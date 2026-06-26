package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class MailboxServiceTest {

    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailboxService = MailboxService(
        mailRecordRepository,
        senderAccountRepository,
        inboundMailProcessingRepository,
        mailAttachmentRepository,
        expertContactRepository
    )

    private val activeAccount = MailSenderAccount(
        id = 1L,
        accountCode = "active_acc",
        senderEmail = "active@example.com",
        senderName = "Active",
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com", smtpPort = 465, smtpUsername = "active", smtpPassword = "pwd",
        imapHost = "imap.example.com", imapPort = 993, imapUsername = "active", imapPassword = "pwd",
        enabled = true
    )

    @Test
    fun `returns empty list when no active accounts exist`() {
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(emptyList())

        val response = mailboxService.listMailbox(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 20
        )

        assertEquals(0L, response.totalCount)
        assertTrue(response.items.isEmpty())
        Mockito.verifyNoInteractions(mailRecordRepository)
    }

    @Test
    fun `returns empty list when filtered account is not active`() {
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(listOf(activeAccount))

        val response = mailboxService.listMailbox(
            direction = null,
            accountCode = "inactive_acc",
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 20
        )

        assertEquals(0L, response.totalCount)
        assertTrue(response.items.isEmpty())
        Mockito.verify(senderAccountRepository).findAllByEnabledTrue()
        Mockito.verifyNoMoreInteractions(mailRecordRepository)
    }

    @Test
    fun `delegates to repository with active accounts filter and converts row to response`() {
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(listOf(activeAccount))

        val now = LocalDateTime.of(2026, 6, 22, 10, 0, 0)
        val mockRow = MailboxRow(
            source = "MAIL_RECORD",
            id = 42L,
            expertContactId = 99L,
            direction = "OUTBOUND",
            mailType = "QA_REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = "SYSTEM",
            matchedQaRuleId = 7L,
            subject = "Question Answered",
            bodyPreview = "Answer preview text",
            sendStatus = "SENT",
            sentAt = now,
            receivedAt = null,
            processStatus = null,
            reasonType = null,
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = 1L,
            inboundProcessingId = null
        )

        Mockito.`when`(
            mailRecordRepository.listMailbox(
                accountCodes = listOf("active_acc"),
                direction = "OUTBOUND",
                accountCode = "active_acc",
                keyword = "Question",
                recipientEmail = "expert",
                startTime = null,
                endTime = null,
                onlyPending = 0,
                limit = 10,
                offset = 0L
            )
        ).thenReturn(listOf(mockRow))

        Mockito.`when`(
            mailRecordRepository.countMailbox(
                accountCodes = listOf("active_acc"),
                direction = "OUTBOUND",
                accountCode = "active_acc",
                keyword = "Question",
                recipientEmail = "expert",
                startTime = null,
                endTime = null,
                onlyPending = 0
            )
        ).thenReturn(1L)

        val response = mailboxService.listMailbox(
            direction = "OUTBOUND",
            accountCode = "active_acc",
            keyword = "Question",
            recipientEmail = "expert",
            startTime = null,
            endTime = null,
            pending = false,
            page = 0,
            size = 10
        )

        assertEquals(1L, response.totalCount)
        assertEquals(1, response.items.size)

        val item = response.items[0]
        assertEquals(42L, item.id)
        assertEquals("MAIL_RECORD", item.source)
        assertEquals(99L, item.expertContactId)
        assertEquals("OUTBOUND", item.direction)
        assertEquals("QA_REPLY", item.mailType)
        assertEquals("active_acc", item.senderAccountCode)
        assertEquals("SYSTEM", item.triggeredBy)
        assertTrue(item.isSystemSent)
        assertEquals("expert@example.com", item.expertEmail)
        assertEquals("Dr. Expert", item.expertName)
        assertEquals("Question Answered", item.subject)
        assertEquals("Answer preview text", item.bodyPreview)
        assertTrue(item.hasAttachment)
        assertEquals("SENT", item.sendStatus)
        assertEquals("2026-06-22T10:00:00", item.timestamp)
        assertTrue(item.tags.containsAll(listOf("专家", "发件", "自动回复")))
    }

    @Test
    fun `unmatched inbound processing row gets pending and unmatched tags`() {
        val row = MailboxRow(
            source = "INBOUND_PROCESSING",
            id = 10L,
            expertContactId = null,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = null,
            matchedQaRuleId = null,
            subject = "Hello",
            bodyPreview = "Body",
            sendStatus = null,
            sentAt = null,
            receivedAt = LocalDateTime.of(2026, 6, 22, 9, 0),
            processStatus = "MANUAL_REVIEW",
            reasonType = "UNMATCHED_CONTACT",
            expertEmail = "unknown@example.com",
            expertName = null,
            hasAttachment = 0L,
            inboundProcessingId = 10L
        )

        val tags = mailboxService.computeTags(row)

        assertTrue(tags.containsAll(listOf("待匹配", "收件", "待处理")))
        assertFalse(tags.contains("专家"))
    }

    @Test
    fun `bound inbound processing row gets expert tag without pending`() {
        val row = MailboxRow(
            source = "INBOUND_PROCESSING",
            id = 11L,
            expertContactId = 55L,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = null,
            matchedQaRuleId = null,
            subject = "Follow up",
            bodyPreview = "Body",
            sendStatus = null,
            sentAt = null,
            receivedAt = LocalDateTime.of(2026, 6, 22, 8, 0),
            processStatus = "PROCESSED",
            reasonType = "MANUAL_BOUND",
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = 0L,
            inboundProcessingId = 11L
        )

        val tags = mailboxService.computeTags(row)

        assertTrue(tags.containsAll(listOf("专家", "收件")))
        assertFalse(tags.contains("待处理"))
    }

    @Test
    fun `introduction outbound gets intro and outbound tags`() {
        val row = MailboxRow(
            source = "MAIL_RECORD",
            id = 1L,
            expertContactId = 2L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            senderAccountCode = "active_acc",
            triggeredBy = "SYSTEM",
            matchedQaRuleId = null,
            subject = "Intro",
            bodyPreview = "Intro body",
            sendStatus = "SENT",
            sentAt = LocalDateTime.of(2026, 6, 22, 7, 0),
            receivedAt = null,
            processStatus = null,
            reasonType = null,
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = 0L,
            inboundProcessingId = null
        )

        val tags = mailboxService.computeTags(row)

        assertTrue(tags.containsAll(listOf("专家", "发件", "首发", "自动回复")))
    }

    @Test
    fun `manual outbound reply gets manual reply tag`() {
        val row = MailboxRow(
            source = "MAIL_RECORD",
            id = 3L,
            expertContactId = 2L,
            direction = "OUTBOUND",
            mailType = "MANUAL_QA_REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = "OPERATOR",
            matchedQaRuleId = null,
            subject = "Manual",
            bodyPreview = "Manual body",
            sendStatus = "SENT",
            sentAt = LocalDateTime.of(2026, 6, 22, 6, 0),
            receivedAt = null,
            processStatus = null,
            reasonType = null,
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = 0L,
            inboundProcessingId = null
        )

        val tags = mailboxService.computeTags(row)

        assertTrue(tags.contains("手动回复"))
    }

    @Test
    fun `pending filter passes onlyPending flag to repository`() {
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.listMailbox(
                accountCodes = listOf("active_acc"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 1,
                limit = 20,
                offset = 0L
            )
        ).thenReturn(emptyList())
        Mockito.`when`(
            mailRecordRepository.countMailbox(
                accountCodes = listOf("active_acc"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 1
            )
        ).thenReturn(0L)

        mailboxService.listMailbox(
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            pending = true,
            page = 0,
            size = 20
        )

        Mockito.verify(mailRecordRepository).listMailbox(
            accountCodes = listOf("active_acc"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            limit = 20,
            offset = 0L
        )
    }

    @Test
    fun `getMailboxDetail returns mail record body from cleaned body`() {
        val record = MailRecord(
            id = 5L,
            expertContactId = 9L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            senderAccountCode = "active_acc",
            triggeredBy = "SYSTEM",
            sourceInboundId = null,
            messageId = "msg-1",
            inReplyTo = null,
            subject = "Hello",
            body = "raw body",
            cleanedBody = "cleaned body",
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = LocalDateTime.of(2026, 6, 22, 10, 0),
            errorSummary = null,
            mailSendAttemptId = null,
            createdAt = null
        )
        Mockito.`when`(mailRecordRepository.findByIdOrNull(5L)).thenReturn(record)
        Mockito.`when`(expertContactRepository.findById(9L)).thenReturn(Optional.empty())
        Mockito.`when`(mailAttachmentRepository.findAllByMailRecordIdOrderByCreatedAtAsc(5L)).thenReturn(emptyList())

        val detail = mailboxService.getMailboxDetail("MAIL_RECORD", 5L)

        assertEquals("cleaned body", detail.body)
        assertEquals("MAIL_RECORD", detail.source)
        assertEquals("OUTBOUND", detail.direction)
        assertFalse(detail.hasAttachment)
    }

    @Test
    fun `getMailboxDetail returns inbound processing body`() {
        val inbound = InboundMailProcessing(
            id = 12L,
            senderAccountCode = "active_acc",
            imapUid = 100L,
            messageId = "in-msg",
            inReplyTo = null,
            fromEmail = "expert@example.com",
            subject = "Re: Hello",
            body = "inbound raw",
            cleanedBody = "inbound cleaned",
            receivedAt = LocalDateTime.of(2026, 6, 22, 11, 0),
            processStatus = "MANUAL_REVIEW",
            processReason = "UNMATCHED",
            reasonType = "UNMATCHED_CONTACT",
            resolvedAt = null,
            resolvedBy = null,
            expertContactId = null,
            retryCount = 0,
            lastError = null,
            createdAt = null,
            updatedAt = null
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(12L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(mailRecordRepository.findFirstByMessageIdOrderByCreatedAtDesc("in-msg")).thenReturn(null)

        val detail = mailboxService.getMailboxDetail("INBOUND_PROCESSING", 12L)

        assertEquals("inbound cleaned", detail.body)
        assertEquals("INBOUND_PROCESSING", detail.source)
        assertEquals("INBOUND", detail.direction)
        assertEquals(12L, detail.inboundProcessingId)
        assertEquals("expert@example.com", detail.expertEmail)
    }

    @Test
    fun `resolveAttachments prefers inbound processing attachments`() {
        val inboundAttachment = com.weibo.talentintroduction.mail.domain.MailAttachment(
            id = 1L,
            mailRecordId = null,
            inboundProcessingId = 12L,
            fileName = "a.pdf",
            contentType = "application/pdf",
            fileSize = 10L,
            storagePath = "/tmp/a.pdf"
        )
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(12L))
            .thenReturn(listOf(inboundAttachment))

        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertEquals(1, attachments.size)
        assertEquals("a.pdf", attachments[0].fileName)
    }

    @Test
    fun `hasAttachment matches resolveAttachments for inbound processing`() {
        val inbound = InboundMailProcessing(
            id = 12L,
            senderAccountCode = "active_acc",
            imapUid = 100L,
            messageId = "in-msg",
            inReplyTo = null,
            fromEmail = "expert@example.com",
            subject = "Re: Hello",
            body = "inbound raw",
            cleanedBody = "inbound cleaned",
            receivedAt = LocalDateTime.of(2026, 6, 22, 11, 0),
            processStatus = "MANUAL_REVIEW",
            processReason = "UNMATCHED",
            reasonType = "UNMATCHED_CONTACT",
            resolvedAt = null,
            resolvedBy = null,
            expertContactId = null,
            retryCount = 0,
            lastError = null,
            createdAt = null,
            updatedAt = null
        )
        val inboundAttachment = com.weibo.talentintroduction.mail.domain.MailAttachment(
            id = 2L,
            mailRecordId = null,
            inboundProcessingId = 12L,
            fileName = "cv.pdf",
            contentType = "application/pdf",
            fileSize = 20L,
            storagePath = "/tmp/cv.pdf"
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(12L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(12L))
            .thenReturn(listOf(inboundAttachment))

        val detail = mailboxService.getMailboxDetail("INBOUND_PROCESSING", 12L)
        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertTrue(detail.hasAttachment)
        assertEquals(1, attachments.size)
    }
}
