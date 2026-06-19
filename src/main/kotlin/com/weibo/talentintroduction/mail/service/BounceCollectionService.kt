package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val expertContactRepository: ExpertContactRepository
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

            if (!bounceDetector.isBounce(from, subject, contentType)) continue

            val messageId = message.getHeader("Message-ID")?.firstOrNull() ?: continue
            if (bounceRecordRepository.existsByBounceMessageId(messageId)) {
                skippedDuplicate++
                continue
            }

            val details = bounceDetector.parseBounceDetails(message)
            val originalContact = details.originalMessageId?.let { origMsgId ->
                mailRecordRepository.findByMessageId(origMsgId)?.let { mailRecord ->
                    expertContactRepository.findById(mailRecord.expertContactId).orElse(null)
                }
            }

            bounceRecordRepository.save(
                BounceRecord(
                    senderAccountCode = account.accountCode,
                    bounceMessageId = messageId,
                    originalMessageId = details.originalMessageId,
                    originalExpertContactId = originalContact?.id,
                    bounceType = details.bounceType,
                    dsnStatus = details.dsnStatus,
                    bounceReason = details.reason,
                    receivedAt = message.receivedDate
                        ?.toInstant()
                        ?.atZone(ZoneId.systemDefault())
                        ?.toLocalDateTime()
                        ?: LocalDateTime.now()
                )
            )

            if (details.bounceType == "HARD" && originalContact != null) {
                expertIndexWriterService.syncCandidateOperatorStatus(
                    originalContact.orcidId,
                    "EMAIL_INVALID"
                )
            }

            collected++
        }

        return BounceCollectionResult(collected = collected, skippedDuplicate = skippedDuplicate)
    }
}

data class BounceCollectionResult(
    val collected: Int,
    val skippedDuplicate: Int
)
