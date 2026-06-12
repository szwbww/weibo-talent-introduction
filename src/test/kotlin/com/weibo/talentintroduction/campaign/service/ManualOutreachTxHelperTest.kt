package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.common.domain.ConversationStatus
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime

class ManualOutreachTxHelperTest {
    private val conversationStateService = Mockito.mock(ConversationStateService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)

    private val helper = ManualOutreachTxHelper(
        conversationStateService,
        mailRecordRepository,
        mailSenderAccountRepository,
        expertContactRepository
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
        val account = MailSenderAccount(
            accountCode = "chen",
            senderEmail = "chen@example.com",
            senderName = "Chen",
            senderTitle = "Title",
            senderDisplayName = "Chen",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "chen@example.com",
            smtpPassword = "pwd",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "chen@example.com",
            imapPassword = "pwd",
            todaySentCount = 5
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
            val mutated = mutator(base.copy(currentStatus = "INTRO_SENT"))
            mutated
        }

        Mockito.`when`(mailSenderAccountRepository.findByAccountCode("chen")).thenReturn(account)
        Mockito.`when`(mailSenderAccountRepository.save(anyValue(account))).thenAnswer { it.getArgument<MailSenderAccount>(0) }
        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }

        helper.recordSuccess(contact, "chen", "msg123", "Subject", "Body")

        // 1. Verify conversation status transitions
        val contactCaptor = ArgumentCaptor.forClass(ExpertContact::class.java)
        Mockito.verify(conversationStateService).transition(
            captureValue(contactCaptor, contact),
            eqValue(ConversationStatus.INTRO_SENT),
            eqValue("MANUAL_BULK_OUTREACH"),
            eqValue("MANUAL"),
            anyValue(LocalDateTime.now()),
            anyValue { contact }
        )

        // 2. Verify account count incremented
        val accountCaptor = ArgumentCaptor.forClass(MailSenderAccount::class.java)
        Mockito.verify(mailSenderAccountRepository).save(captureValue(accountCaptor, account))
        assertEquals(6, accountCaptor.value.todaySentCount)
        assertNotNull(accountCaptor.value.lastSentAt)

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
    }

    @Test
    fun `recordFailure saves failed mail record`() {
        Mockito.`when`(mailRecordRepository.save(anyValue(MailRecord(expertContactId = 0L, direction = "", mailType = "", senderAccountCode = "", triggeredBy = "", subject = "", body = "", sendStatus = "", messageId = null, inReplyTo = null, matchedQaRuleId = null, receivedAt = null, sentAt = null)))).thenAnswer { it.getArgument<MailRecord>(0) }

        helper.recordFailure(100L, "chen", "SMTP connection timeout", "Subject", "Body")

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
        assertEquals("FAILED", record.sendStatus)
        assertEquals("SMTP connection timeout", record.errorSummary)
    }
}
