package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import javax.mail.internet.InternetAddress

@Service
class BounceCollectionService(
    private val mailReceiveService: ImapMailReceiveService,
    private val bounceDetector: BounceDetector,
    private val bounceRecordRepository: BounceRecordRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val expertIndexWriterService: ExpertIndexWriterService,
    private val expertContactRepository: ExpertContactRepository,
    private val expertEmailAliasService: ExpertEmailAliasService
) {
    private val log = LoggerFactory.getLogger(BounceCollectionService::class.java)

    fun collectBounces(account: MailSenderAccount): BounceCollectionResult {
        var collected = 0
        var skippedDuplicate = 0

        val messages = mailReceiveService.fetchUnseenMessages(account)
        for (message in messages) {
            val from = message.from
                ?.filterIsInstance<InternetAddress>()
                ?.firstOrNull()
                ?.address
            val subject = message.subject
            val contentType = message.contentType

            val signal = if (contentType?.contains("report-type=delivery-status", ignoreCase = true) == true) {
                bounceDetector.parseBounceDetails(message)
                    ?: bounceDetector.detect(from, subject, message.content?.toString())
            } else {
                bounceDetector.parseBounceDetails(message)
            } ?: continue

            val messageIdHeader = message.getHeader("Message-ID")?.firstOrNull()
            val receivedAt = message.receivedDate
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDateTime()
                ?: LocalDateTime.now()

            when (
                ingest(
                    signal = signal,
                    senderAccountCode = account.accountCode,
                    bounceMessageId = messageIdHeader,
                    from = from,
                    subject = subject,
                    receivedAt = receivedAt
                )
            ) {
                BounceIngestResult.INGESTED -> collected++
                BounceIngestResult.DUPLICATE -> skippedDuplicate++
            }
        }

        return BounceCollectionResult(collected = collected, skippedDuplicate = skippedDuplicate)
    }

    fun ingest(
        signal: BounceSignal,
        senderAccountCode: String,
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime
    ): BounceIngestResult {
        val dedupeKey = resolveBounceMessageId(bounceMessageId, from, subject, receivedAt)
        if (bounceRecordRepository.existsByBounceMessageId(dedupeKey)) {
            return BounceIngestResult.DUPLICATE
        }

        val originalContact = resolveOriginalContact(signal)

        bounceRecordRepository.save(
            BounceRecord(
                senderAccountCode = senderAccountCode,
                bounceMessageId = dedupeKey,
                originalMessageId = signal.originalMessageId,
                originalExpertContactId = originalContact?.id,
                failedRecipient = signal.failedRecipient,
                bounceType = signal.bounceType,
                dsnStatus = signal.dsnStatus,
                bounceReason = signal.reason,
                receivedAt = receivedAt,
                createdAt = LocalDateTime.now()
            )
        )

        if (signal.bounceType == "HARD" && originalContact != null) {
            expertIndexWriterService.syncCandidateOperatorStatus(
                originalContact.orcidId,
                "EMAIL_INVALID"
            )
        }

        log.debug(
            "Ingested bounce {} for account {} type={}",
            dedupeKey,
            senderAccountCode,
            signal.bounceType
        )
        return BounceIngestResult.INGESTED
    }

    fun resolveBounceMessageId(
        bounceMessageId: String?,
        from: String?,
        subject: String?,
        receivedAt: LocalDateTime
    ): String {
        if (!bounceMessageId.isNullOrBlank()) {
            return MailMessageIdNormalizer.normalize(bounceMessageId)
        }
        val input = "${from.orEmpty()}|${subject.orEmpty()}|$receivedAt"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "NOID:$hex"
    }

    private fun resolveOriginalContact(signal: BounceSignal) =
        signal.originalMessageId?.let { origMsgId ->
            MessageIdNormalizer.candidatesFor(origMsgId)
                .firstNotNullOfOrNull { mailRecordRepository.findByMessageId(it) }
                ?.let { mailRecord ->
                    expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
                }
        } ?: signal.failedRecipient?.let { recipient ->
            expertEmailAliasService.findContactByEmailOrAlias(recipient)
        }
}

enum class BounceIngestResult {
    INGESTED,
    DUPLICATE
}

data class BounceCollectionResult(
    val collected: Int,
    val skippedDuplicate: Int
)
