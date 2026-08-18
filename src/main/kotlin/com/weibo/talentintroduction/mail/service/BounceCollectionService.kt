package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertReachabilitySyncService
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
    private val expertEmailAliasService: ExpertEmailAliasService,
    // 尾部可空默认参数：既有 BounceCollectionServiceTest / BounceBackfillServiceTest 以位置/具名参数
    // 直接构造（未授权文件），加默认值后无需改动。生产由 Spring 注入（I-3-5 挂载点）。
    private val reachabilitySyncService: ExpertReachabilitySyncService? = null,
    // I-3：EMAIL_INVALID 双写出口。可空默认参数沿用上方 reachabilitySyncService 先例，
    // 使既有测试构造无需改签名；生产由 Spring 注入。
    private val expertOperatorStatusService: ExpertOperatorStatusService? = null
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
            // I-3：先落 MySQL + ES（唯一写入口 markEmailInvalid），再增量写 reachability
            try {
                expertOperatorStatusService?.markEmailInvalid(originalContact, "HARD_BOUNCE")
            } catch (e: Exception) {
                log.warn("Failed to mark EMAIL_INVALID for orcid={}", originalContact.orcidId, e)
            }
            // I-3-5/IP-4: 硬退落库后立即增量写 reachability=BLOCKED_BOUNCED；
            // ES 写失败不得回传为退信处理失败，只记 warn，下一轮全量扫描会自愈。
            try {
                reachabilitySyncService?.markBlockedByContact(originalContact)
            } catch (e: Exception) {
                log.warn("Failed to mark reachability BLOCKED_BOUNCED for orcid={}", originalContact.orcidId, e)
            }
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
