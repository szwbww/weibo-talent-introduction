package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class BounceCollectionServiceTest {
    private val mailReceiveService = Mockito.mock(ImapMailReceiveService::class.java)
    private val bounceDetector = BounceDetector()
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val expertIndexWriterService =
        Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java)
    private val expertContactRepository =
        Mockito.mock(com.weibo.talentintroduction.campaign.repository.ExpertContactRepository::class.java)
    private val expertEmailAliasService =
        Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService::class.java)

    private val service = BounceCollectionService(
        mailReceiveService,
        bounceDetector,
        bounceRecordRepository,
        mailRecordRepository,
        expertIndexWriterService,
        expertContactRepository,
        expertEmailAliasService
    )

    @Test
    fun `collectBounces ingests neutral MIME DSN without heuristic keywords`() {
        val account = senderAccount()
        val message = neutralMimeDsn()
        Mockito.`when`(mailReceiveService.fetchUnseenMessages(account)).thenReturn(listOf(message))
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId("neutral-bounce@example.com")).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 1L) }

        val result = service.collectBounces(account)

        assertEquals(1, result.collected)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("HARD", captor.value.bounceType)
        assertEquals("5.1.1", captor.value.dsnStatus)
    }

    @Test
    fun `ingest persists failedRecipient even when expert contact is not found`() {
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("unknown@example.com")).thenReturn(null)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 2L) }

        val result = service.ingest(
            signal = BounceSignal(
                bounceType = "HARD",
                dsnStatus = "5.1.1",
                failedRecipient = "unknown@example.com",
                reason = "Undelivered",
                originalMessageId = null
            ),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-1@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        assertEquals(BounceIngestResult.INGESTED, result)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("unknown@example.com", captor.value.failedRecipient)
        assertNull(captor.value.originalExpertContactId)
    }

    private fun senderAccount() = MailSenderAccount(
        accountCode = "acc1",
        senderEmail = "acc1@example.com",
        senderName = "acc1",
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "acc1@example.com",
        smtpPassword = "secret",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "acc1@example.com",
        imapPassword = "secret"
    )

    private fun neutralMimeDsn(): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("system@example.com"))
        message.subject = "notice"
        message.setHeader("Message-ID", "<neutral-bounce@example.com>")

        val multipart = MimeMultipart("report; report-type=delivery-status")
        val textPart = MimeBodyPart()
        textPart.setText("Delivery failed")
        multipart.addBodyPart(textPart)

        val dsnPart = MimeBodyPart()
        dsnPart.setContent(
            """
            Reporting-MTA: dns; example.com
            Status: 5.1.1
            """.trimIndent(),
            "message/delivery-status"
        )
        multipart.addBodyPart(dsnPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
    }
}
