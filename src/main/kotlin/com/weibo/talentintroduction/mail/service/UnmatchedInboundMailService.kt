package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class UnmatchedInboundMailService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val mailRecordRepository: MailRecordRepository,
    private val expertIndexWriterService: ExpertIndexWriterService
) {
    fun listManualReviewQueue(
        reasonType: String? = null,
        email: String? = null,
        subject: String? = null,
        pageSize: Int = 20,
        pageOffset: Int = 0
    ): ManualReviewQueueResult {
        val records = inboundMailProcessingRepository.findManualReviewQueue(
            reasonType = reasonType,
            email = email,
            subject = subject,
            limit = pageSize,
            offset = pageOffset
        )
        val totalCount = inboundMailProcessingRepository.countManualReviewQueue(
            reasonType = reasonType,
            email = email,
            subject = subject
        )
        val counts = inboundMailProcessingRepository.countGroupedByReasonType()
            .associate { it.reasonType to it.count }
        return ManualReviewQueueResult(
            records = records,
            totalCount = totalCount,
            countsByReasonType = counts
        )
    }

    fun getDetail(id: Long): InboundMailProcessing {
        val record = inboundMailProcessingRepository.findById(id)
            .orElseThrow { error("Inbound mail processing not found: $id") }
        return record
    }

    fun suggestCandidates(record: InboundMailProcessing): List<CandidateSuggestion> {
        val candidates = mutableListOf<CandidateSuggestion>()

        val inReplyTo = record.inReplyTo
        if (inReplyTo != null) {
            val outboundMail = mailRecordRepository.findByMessageId(inReplyTo)
            if (outboundMail != null) {
                val contact = expertContactRepository.findById(outboundMail.expertContactId).orElse(null)
                if (contact != null) {
                    candidates.add(
                        CandidateSuggestion(
                            contact = contact,
                            reason = "IN_REPLY_TO",
                            confidence = 90
                        )
                    )
                }
            }
        }

        val fromEmailLower = record.fromEmail.lowercase()
        val fromName = record.subject?.let { extractNameFromSubject(it) }
        if (fromName != null) {
            val nameMatches = expertContactRepository
                .findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(fromName, fromEmailLower)
            nameMatches.forEach { contact ->
                if (candidates.none { it.contact.id == contact.id }) {
                    candidates.add(
                        CandidateSuggestion(
                            contact = contact,
                            reason = "NAME_OR_EMAIL_MATCH",
                            confidence = 60
                        )
                    )
                }
            }
        }

        val emailMatches = expertContactRepository
            .findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(fromEmailLower, fromEmailLower)
        emailMatches.forEach { contact ->
            if (candidates.none { it.contact.id == contact.id }) {
                candidates.add(
                    CandidateSuggestion(
                        contact = contact,
                        reason = "EMAIL_SIMILARITY",
                        confidence = 50
                    )
                )
            }
        }

        return candidates.distinctBy { it.contact.id }.take(5)
    }

    fun searchContacts(query: String): List<ExpertContact> =
        expertContactRepository.findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
            query, query, query
        )

    @Transactional
    fun bindToContact(
        recordId: Long,
        contactId: Long,
        resolvedBy: String,
        promoteToApplication: Boolean = false
    ): InboundMailProcessing {
        val record = inboundMailProcessingRepository.findById(recordId)
            .orElseThrow { error("Inbound mail processing not found: $recordId") }
        require(record.expertContactId == null) { "Already bound to a contact" }

        val contact = expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        expertEmailAliasService.bindAlias(
            expertContactId = contactId,
            email = record.fromEmail,
            source = "MANUAL_BIND"
        )

        var currentContact = contact
        if (promoteToApplication && !contact.applicationIndexed) {
            val now = record.receivedAt
            val firstReplyAt = contact.firstReplyAt ?: now
            val updatedContact = contact.copy(firstReplyAt = firstReplyAt)
            val ok = expertIndexWriterService.promoteToApplication(
                orcid = contact.orcidId,
                contact = updatedContact,
                firstReplyAt = firstReplyAt.toInstant(ZoneId.systemDefault().rules.getOffset(firstReplyAt)),
                triggeredBy = TriggeredBy.OPERATOR,
                operatorName = resolvedBy
            )
            if (ok) {
                currentContact = updatedContact.copy(applicationIndexed = true, currentIndexLevel = "APPLICATION")
            } else {
                error("Failed to promote expert to Application index")
            }
        }

        val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW")
        if (remaining == 0L && currentContact.needsManualAttention) {
            currentContact = currentContact.copy(needsManualAttention = false)
        }
        expertContactRepository.save(currentContact)

        val now = LocalDateTime.now()
        return inboundMailProcessingRepository.save(
            record.copy(
                expertContactId = contactId,
                processStatus = "PROCESSED",
                processReason = "MANUAL_BOUND",
                resolvedBy = resolvedBy,
                resolvedAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional
    fun markResolved(recordId: Long, resolvedBy: String, note: String?): InboundMailProcessing {
        val record = inboundMailProcessingRepository.findById(recordId)
            .orElseThrow { error("Inbound mail processing not found: $recordId") }
        require(record.processStatus == "MANUAL_REVIEW") { "Record $recordId is not in MANUAL_REVIEW" }
        val now = LocalDateTime.now()
        val saved = inboundMailProcessingRepository.save(record.copy(
            processStatus = "PROCESSED",
            processReason = "MANUAL_RESOLVED",
            reasonType = "MANUAL_RESOLVED",
            resolvedBy = resolvedBy,
            resolvedAt = now,
            updatedAt = now
        ))

        val contactId = record.expertContactId
        if (contactId != null) {
            val remaining = inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW")
            if (remaining == 0L) {
                expertContactRepository.findById(contactId).ifPresent { contact ->
                    if (contact.needsManualAttention) {
                        expertContactRepository.save(contact.copy(needsManualAttention = false))
                    }
                }
            }
        }
        return saved
    }

    private fun extractNameFromSubject(subject: String): String? {
        val cleaned = subject
            .replace(Regex("""\bRe:\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bFwd:\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (cleaned.isBlank()) return null
        val words = cleaned.split(Regex("\\s+")).filter { it.length > 2 }
        return words.firstOrNull()
    }
}

data class CandidateSuggestion(
    val contact: ExpertContact,
    val reason: String,
    val confidence: Int
)

data class ManualReviewQueueResult(
    val records: List<InboundMailProcessing>,
    val totalCount: Long,
    val countsByReasonType: Map<String, Long>
)
