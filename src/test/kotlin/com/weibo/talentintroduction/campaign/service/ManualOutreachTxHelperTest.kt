package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.OperatorStatus
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ManualOutreachTxHelperTest {
    private val conversationStateService = Mockito.mock(ConversationStateService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailSendAttemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)

    private val helper = ManualOutreachTxHelper(
        conversationStateService = conversationStateService,
        mailRecordRepository = mailRecordRepository,
        mailSenderAccountRepository = mailSenderAccountRepository,
        mailSendAttemptRepository = mailSendAttemptRepository,
        expertOperatorStatusService = expertOperatorStatusService
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }

    @Test
    fun `recordSuccess transitions status and saves mail record and updates account count`() {
        val contact = ExpertContact(
            id = 100L,
            campaignId = 10L,
            orcidId = "0001",
            expertEmail = "a@b.com",
            expertName = "Name",
            currentStatus = "NEW",
            operatorStatus = "NOT_CONTACTED"
        )

        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_BULK_OUTREACH"),
            eqValue("MANUAL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )).thenAnswer { invocation ->
            val base = invocation.getArgument<ExpertContact>(0)
            val mutator = invocation.getArgument<(ExpertContact) -> ExpertContact>(5)
            mutator(base.copy(currentStatus = "INTRO_SENT"))
        }

        Mockito.`when`(mailSendAttemptRepository.findById(77L)).thenReturn(
            Optional.of(MailSendAttempt(
                id = 77L, orcidId = "0001", mailType = "INTRODUCTION",
                accountCode = "chen", messageId = "msg123",
                status = MailSendAttemptStatus.PREPARED
            ))
        )
        Mockito.`when`(mailSenderAccountRepository.incrementTodaySentCount(eqValue("chen"), anyValue(LocalDateTime.now()))).thenReturn(1)
        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }
        Mockito.`when`(mailSendAttemptRepository.save(anyValue(MailSendAttempt(orcidId = "", mailType = "", accountCode = "", messageId = "", status = "")))).thenAnswer { it.getArgument<MailSendAttempt>(0) }

        helper.recordSuccess(contact, "chen", "msg123", "Subject", "Body", 77L)

        // 1. Verify conversation status transitions
        Mockito.verify(conversationStateService).transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_BULK_OUTREACH"),
            eqValue("MANUAL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )

        // 2. Verify account count incremented
        Mockito.verify(mailSenderAccountRepository).incrementTodaySentCount(eqValue("chen"), anyValue(LocalDateTime.now()))

        // 3. Verify mail record saved
        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
        val record = recordCaptor.value
        assertEquals("OUTBOUND", record.direction)
        assertEquals("INTRODUCTION", record.mailType)
        assertEquals("chen", record.senderAccountCode)
        assertEquals("MANUAL", record.triggeredBy)
        assertEquals("msg123", record.messageId)
        assertEquals("Subject", record.subject)
        assertEquals("Body", record.body)
        assertEquals("SENT", record.sendStatus)
        assertEquals(77L, record.mailSendAttemptId)

        // 4. Verify attempt marked SENT
        val attemptCaptor = ArgumentCaptor.forClass(MailSendAttempt::class.java)
        Mockito.verify(mailSendAttemptRepository).save(captureValue(attemptCaptor, MailSendAttempt(orcidId = "", mailType = "", accountCode = "", messageId = "", status = "")))
        assertEquals(MailSendAttemptStatus.SENT, attemptCaptor.value.status)

        // 5. Verify operator status advanced through the single automatic writer
        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact),
            eqValue(OperatorStatus.CONTACTED),
            eqValue("MANUAL_BULK_OUTREACH")
        )
    }

    @Test
    fun `recordSuccess converges operator status via updateAutomatically without direct ES sync`() {
        val contact = ExpertContact(
            id = 200L,
            campaignId = 10L,
            orcidId = "0002",
            expertEmail = "b@c.com",
            expertName = "Name",
            currentStatus = "NEW",
            operatorStatus = "NOT_CONTACTED"
        )

        Mockito.`when`(conversationStateService.transition(
            anyValue(contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_BULK_OUTREACH"),
            eqValue("MANUAL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )).thenAnswer { invocation ->
            val base = invocation.getArgument<ExpertContact>(0)
            val mutator = invocation.getArgument<(ExpertContact) -> ExpertContact>(5)
            mutator(base.copy(currentStatus = "INTRO_SENT"))
        }

        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }
        Mockito.`when`(mailSenderAccountRepository.incrementTodaySentCount(eqValue("chen"), anyValue(LocalDateTime.now()))).thenReturn(1)
        Mockito.`when`(mailSendAttemptRepository.findById(88L)).thenReturn(
            Optional.of(MailSendAttempt(
                id = 88L, orcidId = "0002", mailType = "INTRODUCTION",
                accountCode = "chen", messageId = "msg456",
                status = MailSendAttemptStatus.PREPARED
            ))
        )
        Mockito.`when`(mailSendAttemptRepository.save(anyValue(MailSendAttempt(orcidId = "", mailType = "", accountCode = "", messageId = "", status = "")))).thenAnswer { it.getArgument<MailSendAttempt>(0) }

        helper.recordSuccess(contact, "chen", "msg456", "Subject", "Body", 88L)

        Mockito.verify(expertOperatorStatusService).updateAutomatically(
            anyValue(contact),
            eqValue(OperatorStatus.CONTACTED),
            eqValue("MANUAL_BULK_OUTREACH")
        )
    }

    @Test
    fun `recordFailure saves failed mail record and marks attempt failed`() {
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }
        Mockito.`when`(mailSendAttemptRepository.findById(77L)).thenReturn(
            Optional.of(MailSendAttempt(
                id = 77L, orcidId = "0001", mailType = "INTRODUCTION",
                accountCode = "chen", messageId = "msg-fail",
                status = MailSendAttemptStatus.PREPARED
            ))
        )
        Mockito.`when`(mailSendAttemptRepository.save(Mockito.any(MailSendAttempt::class.java))).thenAnswer { it.getArgument<MailSendAttempt>(0) }

        helper.recordFailure(100L, "chen", "msg-fail", "SMTP connection timeout", "Subject", "Body", 77L)

        val recordCaptor = ArgumentCaptor.forClass(MailRecord::class.java)
        Mockito.verify(mailRecordRepository).save(captureValue(recordCaptor, MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))
        val record = recordCaptor.value
        assertEquals(100L, record.expertContactId)
        assertEquals("OUTBOUND", record.direction)
        assertEquals("INTRODUCTION", record.mailType)
        assertEquals("chen", record.senderAccountCode)
        assertEquals("MANUAL", record.triggeredBy)
        assertEquals("Subject", record.subject)
        assertEquals("Body", record.body)
        assertEquals("msg-fail", record.messageId)
        assertEquals("FAILED", record.sendStatus)
        assertEquals("SMTP connection timeout", record.errorSummary)

        // Verify attempt marked FAILED
        val attemptCaptor = ArgumentCaptor.forClass(MailSendAttempt::class.java)
        Mockito.verify(mailSendAttemptRepository).save(captureValue(attemptCaptor, MailSendAttempt(orcidId = "", mailType = "", accountCode = "", messageId = "", status = "")))
        assertEquals(MailSendAttemptStatus.FAILED, attemptCaptor.value.status)
        assertEquals("SMTP connection timeout", attemptCaptor.value.errorSummary)
    }

    @Test
    fun `recordFailure without attemptId only saves mail record`() {
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java))).thenAnswer { it.getArgument<MailRecord>(0) }

        helper.recordFailure(100L, "chen", "msg-fail", "SMTP connection timeout", "Subject", "Body", attemptId = null)

        Mockito.verify(mailRecordRepository).save(Mockito.any(MailRecord::class.java))
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).findById(Mockito.anyLong())
        Mockito.verify(mailSendAttemptRepository, Mockito.never()).save(Mockito.any(MailSendAttempt::class.java))
    }
}
