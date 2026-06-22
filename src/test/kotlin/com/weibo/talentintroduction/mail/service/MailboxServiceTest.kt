package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class MailboxServiceTest {

    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val mailboxService = MailboxService(mailRecordRepository, senderAccountRepository)

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
            page = 0,
            size = 20
        )

        assertEquals(0L, response.totalCount)
        assertTrue(response.items.isEmpty())
        Mockito.verifyNoInteractions(mailRecordRepository)
    }

    @Test
    fun `returns empty list when filtered account is not active`() {
        val activeAccount = MailSenderAccount(
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
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(listOf(activeAccount))

        val response = mailboxService.listMailbox(
            direction = null,
            accountCode = "inactive_acc", // not in the active accounts list
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
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
        val activeAccount = MailSenderAccount(
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
        Mockito.`when`(senderAccountRepository.findAllByEnabledTrue()).thenReturn(listOf(activeAccount))

        val now = LocalDateTime.of(2026, 6, 22, 10, 0, 0)
        val mockRow = MailboxRow(
            id = 42L,
            expertContactId = 99L,
            direction = "OUTBOUND",
            mailType = "QA_REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = "SYSTEM",
            subject = "Question Answered",
            bodyPreview = "Answer preview text",
            sendStatus = "SENT",
            sentAt = now,
            receivedAt = null,
            createdAt = now,
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = true
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
                endTime = null
            )
        ).thenReturn(1L)

        val response = mailboxService.listMailbox(
            direction = "OUTBOUND",
            accountCode = "active_acc",
            keyword = "Question",
            recipientEmail = "expert",
            startTime = null,
            endTime = null,
            page = 0,
            size = 10
        )

        assertEquals(1L, response.totalCount)
        assertEquals(1, response.items.size)

        val item = response.items[0]
        assertEquals(42L, item.id)
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
    }
}
