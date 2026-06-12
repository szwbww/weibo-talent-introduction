package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ManualOutreachTxHelper(
    private val conversationStateService: ConversationStateService,
    private val mailRecordRepository: MailRecordRepository,
    private val mailSenderAccountRepository: MailSenderAccountRepository,
    private val expertContactRepository: ExpertContactRepository
) {
    @Transactional
    fun recordSuccess(
        contact: ExpertContact,
        accountCode: String,
        deliveredMessageId: String?,
        subject: String,
        body: String
    ) {
        val now = LocalDateTime.now()
        val updatedContact = conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.INTRO_SENT,
            reason = "MANUAL_BULK_OUTREACH",
            source = "MANUAL",
            now = now
        ) {
            it.copy(operatorStatus = "CONTACTED", lastMailAt = now)
        }

        mailRecordRepository.save(
            MailRecord(
                expertContactId = updatedContact.id ?: error("Saved expert contact id is null"),
                direction = "OUTBOUND",
                mailType = "INTRODUCTION",
                senderAccountCode = accountCode,
                triggeredBy = "MANUAL",
                sourceInboundId = null,
                messageId = deliveredMessageId,
                inReplyTo = null,
                subject = subject,
                body = body,
                matchedQaRuleId = null,
                sendStatus = "SENT",
                receivedAt = null,
                sentAt = now,
                createdAt = now
            )
        )

        val account = mailSenderAccountRepository.findByAccountCode(accountCode)
            ?: error("Account not found: $accountCode")
        mailSenderAccountRepository.save(
            account.copy(
                todaySentCount = account.todaySentCount + 1,
                lastSentAt = now
            )
        )
    }

    @Transactional
    fun recordFailure(
        contactId: Long,
        accountCode: String,
        errorSummary: String?,
        subject: String,
        body: String
    ) {
        val now = LocalDateTime.now()
        mailRecordRepository.save(
            MailRecord(
                expertContactId = contactId,
                direction = "OUTBOUND",
                mailType = "INTRODUCTION",
                senderAccountCode = accountCode,
                triggeredBy = "MANUAL",
                sourceInboundId = null,
                messageId = null,
                inReplyTo = null,
                subject = subject,
                body = body,
                matchedQaRuleId = null,
                sendStatus = "FAILED",
                errorSummary = errorSummary?.take(1000),
                receivedAt = null,
                sentAt = null,
                createdAt = now
            )
        )
    }
}
