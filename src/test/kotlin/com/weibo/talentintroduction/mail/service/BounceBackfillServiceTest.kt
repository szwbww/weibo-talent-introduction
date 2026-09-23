package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.BounceRecordRepository
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.ArgumentCaptor
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class BounceBackfillServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val bounceRecordRepository = Mockito.mock(BounceRecordRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val bounceDetector = BounceDetector()
    private val bounceCollectionService = BounceCollectionService(
        mailReceiveService = Mockito.mock(ImapMailReceiveService::class.java),
        bounceDetector = bounceDetector,
        bounceRecordRepository = bounceRecordRepository,
        mailRecordRepository = mailRecordRepository,
        expertIndexWriterService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java),
        expertContactRepository = Mockito.mock(com.weibo.talentintroduction.campaign.repository.ExpertContactRepository::class.java),
        expertEmailAliasService = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService::class.java),
        mailSenderAccountService = mailSenderAccountService
    )
    private val service = BounceBackfillService(
        inboundMailProcessingRepository,
        bounceDetector,
        bounceCollectionService
    )

    @Test
    fun `run is idempotent for historical bounces`() {
        val rows = listOf(
            inboundRow(
                id = 1L,
                from = "expert@university.edu",
                subject = "Re: Program",
                body = "Thanks"
            ),
            inboundRow(
                id = 2L,
                from = "postmaster@mail.example.com",
                subject = "邮件被退回",
                body = "554 5.4.4 无法发送到 bounced@example.com"
            ),
            inboundRow(
                id = 3L,
                from = "mailer-daemon@example.com",
                subject = "Delivery has failed to these recipients or groups",
                body = "554 5.4.4 Access to this mail system has been rejected due to poor reputation\nbounced address: fail@example.com"
            ),
            inboundRow(id = 4L, from = "noreply@service.com", subject = "Newsletter", body = "Hello"),
            inboundRow(id = 5L, from = "bot@service.com", subject = "Alert", body = "All good")
        )

        Mockito.`when`(inboundMailProcessingRepository.countAll()).thenReturn(5L)
        Mockito.`when`(inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(200, 0)).thenReturn(rows)
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 1L) }

        val first = service.run()
        assertEquals(5, first.scanned)
        assertEquals(2, first.ingested)

        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(true)
        val second = service.run()
        assertEquals(5, second.scanned)
        assertEquals(0, second.ingested)
        assertEquals(2, second.duplicates)
    }

    @Test
    fun `run does not mutate inbound rows`() {
        val row = inboundRow(
            id = 10L,
            from = "postmaster@mail.example.com",
            subject = "退信通知",
            body = "554 5.1.1 无法发送到 expert@example.com"
        )
        Mockito.`when`(inboundMailProcessingRepository.countAll()).thenReturn(1L)
        Mockito.`when`(inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(200, 0))
            .thenReturn(listOf(row))
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 99L) }

        service.run()

        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .save(Mockito.any(InboundMailProcessing::class.java))
    }

    @Test
    fun `run preserves the known account when a same-group outbound candidate disagrees`() {
        val owner = senderAccount("owner")
        val alias = owner.copy(accountCode = "alias", inboundMailboxCode = "owner")
        val row = inboundRow(
            id = 11L,
            senderAccountCode = "alias",
            from = "postmaster@mail.example.com",
            subject = "Delivery failed",
            body = "Status: 5.1.1\nOriginal-Message-ID: <owner-outbound@example.com>"
        )
        val conflictingOutbound = MailRecord(
            id = 100L,
            expertContactId = 1L,
            direction = "OUTBOUND",
            mailType = "AUTO_REPLY",
            senderAccountCode = "owner",
            messageId = "<owner-outbound@example.com>",
            inReplyTo = null,
            subject = null,
            body = null,
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = LocalDateTime.of(2026, 6, 1, 10, 0)
        )

        Mockito.`when`(inboundMailProcessingRepository.countAll()).thenReturn(1L)
        Mockito.`when`(inboundMailProcessingRepository.findAllPagedOrderByReceivedAtAsc(200, 0))
            .thenReturn(listOf(row))
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(owner, alias))
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(listOf(conflictingOutbound))
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 101L) }

        service.run()

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("alias", captor.value.senderAccountCode)
        assertEquals("owner-outbound@example.com", captor.value.originalMessageId)
    }

    private fun inboundRow(
        id: Long,
        from: String,
        subject: String,
        body: String,
        senderAccountCode: String = "acc1",
        status: String = "MANUAL_REVIEW"
    ) = InboundMailProcessing(
        id = id,
        senderAccountCode = senderAccountCode,
        imapUid = id,
        messageId = "msg-$id",
        fromEmail = from,
        subject = subject,
        body = body,
        receivedAt = LocalDateTime.of(2026, 6, 1, 10, 0).plusHours(id),
        processStatus = status,
        processReason = "CONTACT_NOT_FOUND",
        reasonType = "UNMATCHED_CONTACT",
        expertContactId = null
    )

    private fun senderAccount(accountCode: String) = MailSenderAccount(
        accountCode = accountCode,
        senderEmail = "$accountCode@example.com",
        senderName = accountCode,
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "$accountCode@example.com",
        smtpPassword = "secret",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "$accountCode@example.com",
        imapPassword = "secret"
    )
}
