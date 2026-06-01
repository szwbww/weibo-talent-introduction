package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UnmatchedInboundMailService(
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val mailRecordRepository: MailRecordRepository
) {
    fun listUnmatched(): List<InboundMailProcessing> =
        inboundMailProcessingRepository.findAllByProcessStatusAndExpertContactIdIsNullOrderByReceivedAtDesc("MANUAL_REVIEW")

    fun countUnmatched(): Long =
        inboundMailProcessingRepository.countByProcessStatus("MANUAL_REVIEW")

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
        resolvedBy: String
    ): InboundMailProcessing {
        val record = inboundMailProcessingRepository.findById(recordId)
            .orElseThrow { error("Inbound mail processing not found: $recordId") }
        require(record.expertContactId == null) { "Already bound to a contact" }

        expertContactRepository.findById(contactId)
            .orElseThrow { error("Expert contact not found: $contactId") }

        expertEmailAliasService.bindAlias(
            expertContactId = contactId,
            email = record.fromEmail,
            source = "MANUAL_BIND"
        )

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
