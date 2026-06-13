package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
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
    private val mailSendAttemptRepository: MailSendAttemptRepository
) {
    /**
     * Atomically records a successful send: transition contact NEW→INTRO_SENT,
     * create SENT mail record, increment account counter, mark attempt SENT.
     */
    @Transactional
    fun recordSuccess(
        contact: ExpertContact,
        accountCode: String,
        deliveredMessageId: String?,
        subject: String?,
        body: String?,
        attemptId: Long
    ) {
        val now = LocalDateTime.now()

        // 1. Transition contact state: NEW → INTRO_SENT
        conversationStateService.transition(
            contact = contact,
            toStatus = ConversationStatus.INTRO_SENT,
            reason = "MANUAL_BULK_OUTREACH",
            source = "MANUAL",
            now = now
        ) {
            it.copy(operatorStatus = "CONTACTED", lastMailAt = now)
        }

        // 2. Create mail record
        mailRecordRepository.save(
            MailRecord(
                expertContactId = contact.id ?: error("Contact ID is null"),
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
                mailSendAttemptId = attemptId,
                createdAt = now
            )
        )

        // 3. Increment account daily counter
        mailSenderAccountRepository.incrementTodaySentCount(accountCode, now)

        // 4. Mark attempt as SENT
        val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
        if (attempt != null) {
            mailSendAttemptRepository.save(attempt.copy(
                status = MailSendAttemptStatus.SENT,
                updatedAt = now
            ))
        }
    }

    /**
     * Records a failed send: create FAILED mail record, mark attempt FAILED.
     */
    @Transactional
    fun recordFailure(
        contactId: Long,
        accountCode: String,
        messageId: String?,
        errorSummary: String?,
        subject: String?,
        body: String?,
        attemptId: Long?
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
                messageId = messageId,
                inReplyTo = null,
                subject = subject,
                body = body,
                matchedQaRuleId = null,
                sendStatus = "FAILED",
                errorSummary = errorSummary?.take(1000),
                receivedAt = null,
                sentAt = null,
                mailSendAttemptId = attemptId,
                createdAt = now
            )
        )

        if (attemptId != null) {
            val attempt = mailSendAttemptRepository.findById(attemptId).orElse(null)
            if (attempt != null) {
                mailSendAttemptRepository.save(attempt.copy(
                    status = MailSendAttemptStatus.FAILED,
                    errorSummary = errorSummary?.take(1000),
                    updatedAt = now
                ))
            }
        }
    }
}
