package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.mail.domain.BounceRecord
import com.weibo.talentintroduction.mail.domain.MailRecord
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
import java.util.Optional
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
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)

    private val service = BounceCollectionService(
        mailReceiveService = mailReceiveService,
        bounceDetector = bounceDetector,
        bounceRecordRepository = bounceRecordRepository,
        mailRecordRepository = mailRecordRepository,
        expertIndexWriterService = expertIndexWriterService,
        expertContactRepository = expertContactRepository,
        expertEmailAliasService = expertEmailAliasService,
        expertOperatorStatusService = expertOperatorStatusService,
        mailSenderAccountService = mailSenderAccountService
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

    @Test
    fun `ingest resolves prefixed originalMessageId to original contact`() {
        val prefixed = "<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val stored = "<reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val outboundRecord = MailRecord(
            id = 50L, expertContactId = 10L, direction = "OUTBOUND", mailType = "REMINDER",
            messageId = stored, inReplyTo = null, subject = "Reminder", body = "Body",
            matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = LocalDateTime.now()
        )
        val c = ExpertContact(
            id = 10L, campaignId = 10L, orcidId = "orcid-10",
            expertEmail = "expert@example.com", expertName = null
        )

        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(prefixed)).thenReturn(emptyList())
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(stored)).thenReturn(listOf(outboundRecord))
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(c))
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 3L) }

        val result = service.ingest(
            signal = BounceSignal(
                bounceType = "HARD",
                dsnStatus = "5.1.1",
                failedRecipient = null,
                reason = "Undelivered",
                originalMessageId = prefixed
            ),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-prefixed@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        assertEquals(BounceIngestResult.INGESTED, result)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals(10L, captor.value.originalExpertContactId)
    }

    @Test
    fun `ingest with NOID originalMessageId falls back to failedRecipient`() {
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("unknown@example.com")).thenReturn(null)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 4L) }

        val result = service.ingest(
            signal = BounceSignal(
                bounceType = "HARD",
                dsnStatus = "5.1.1",
                failedRecipient = "unknown@example.com",
                reason = "Undelivered",
                originalMessageId = "NOID:deadbeef"
            ),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-noid@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        assertEquals(BounceIngestResult.INGESTED, result)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("NOID:deadbeef", captor.value.originalMessageId)
        assertNull(captor.value.originalExpertContactId)
    }

    @Test
    fun `ingest marks EMAIL_INVALID via ExpertOperatorStatusService for HARD bounce`() {
        val c = ExpertContact(
            id = 10L, campaignId = 10L, orcidId = "orcid-10",
            expertEmail = "expert@example.com", expertName = null
        )
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId("orig-hard@example.com")).thenReturn(
            listOf(
                MailRecord(
                    id = 60L, expertContactId = 10L, direction = "OUTBOUND", mailType = "INTRODUCTION",
                    messageId = "orig-hard@example.com", inReplyTo = null, subject = "Intro", body = "Body",
                    matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = LocalDateTime.now()
                )
            )
        )
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(c))
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 5L) }

        service.ingest(
            signal = BounceSignal(
                bounceType = "HARD",
                dsnStatus = "5.1.1",
                failedRecipient = null,
                reason = "Undelivered",
                originalMessageId = "orig-hard@example.com"
            ),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-hard@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        Mockito.verify(expertOperatorStatusService).markEmailInvalid(c, "HARD_BOUNCE")
    }

    @Test
    fun `ingest does not mark EMAIL_INVALID for SOFT bounce`() {
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = 6L) }

        service.ingest(
            signal = BounceSignal(
                bounceType = "SOFT",
                dsnStatus = "4.2.2",
                failedRecipient = null,
                reason = "Mailbox full",
                originalMessageId = null
            ),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-soft@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        Mockito.verifyNoInteractions(expertOperatorStatusService)
    }

    // ------------------------------------------------------------------
    // I-1/I-2：退信归属只凭唯一 OUTBOUND 原始发信，且只改逻辑账号
    // ------------------------------------------------------------------

    @Test
    fun `ingest attributes the bounce to the unique outbound account of the same group`() {
        sharedGroup()
        val prefixed =
            "<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val stored = "<reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        stubIngestSave(id = 7L)
        // 同一原始发信的多个标准化候选都命中同一条记录 → 按 id 去重后仍是唯一候选，不算歧义
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(listOf(outboundCandidate(70L, "alias1", stored)))

        val result = service.ingest(
            signal = bounceSignal(originalMessageId = prefixed),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-alias@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        assertEquals(BounceIngestResult.INGESTED, result)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("alias1", captor.value.senderAccountCode, "别名自己发出的信必须归到别名")
    }

    @Test
    fun `ingest keeps the owner when the unique outbound record belongs to the owner`() {
        sharedGroup()
        stubIngestSave(id = 8L)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(listOf(outboundCandidate(71L, "acc1", "<owner-outbound@example.com>")))

        service.ingest(
            signal = bounceSignal(originalMessageId = "<owner-outbound@example.com>"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-owner@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("acc1", captor.value.senderAccountCode)
    }

    @Test
    fun `ingest keeps the passed-in account when no outbound record proves the original sender`() {
        sharedGroup()
        stubIngestSave(id = 9L)
        // OUTBOUND-only 查询：只有 INBOUND 行命中同一 Message-ID 时返回空列表
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(emptyList())

        service.ingest(
            signal = bounceSignal(originalMessageId = "<inbound-only@example.com>"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-inbound-only@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("acc1", captor.value.senderAccountCode)
        Mockito.verify(mailRecordRepository, Mockito.never()).findByMessageId(Mockito.anyString())
    }

    @Test
    fun `ingest keeps the passed-in account when the message id has multiple outbound records`() {
        sharedGroup()
        stubIngestSave(id = 10L)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(
                listOf(
                    outboundCandidate(80L, "alias1", "<dup-outbound@example.com>"),
                    outboundCandidate(81L, "alias1", "<dup-outbound@example.com>")
                )
            )

        service.ingest(
            signal = bounceSignal(originalMessageId = "<dup-outbound@example.com>"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-dup-outbound@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("acc1", captor.value.senderAccountCode, "多条候选不得取第一条")
    }

    @Test
    fun `ingest keeps the passed-in account when the outbound record is outside the physical group`() {
        sharedGroup()
        stubIngestSave(id = 11L)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(listOf(outboundCandidate(90L, "independent", "<cross-group@example.com>")))

        service.ingest(
            signal = bounceSignal(originalMessageId = "<cross-group@example.com>"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-cross-group@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("acc1", captor.value.senderAccountCode)
    }

    @Test
    fun `ingest never uses the failed recipient expert address as the sender account`() {
        sharedGroup()
        val contact = ExpertContact(
            id = 10L, campaignId = 10L, orcidId = "orcid-10",
            expertEmail = "expert@example.com", expertName = null
        )
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(emptyList())
        Mockito.`when`(expertEmailAliasService.findContactByEmailOrAlias("expert@example.com")).thenReturn(contact)
        stubIngestSave(id = 12L)

        service.ingest(
            signal = bounceSignal(originalMessageId = null, failedRecipient = "expert@example.com"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-failed-recipient@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("acc1", captor.value.senderAccountCode, "专家邮箱不是发件账号")
        assertEquals(10L, captor.value.originalExpertContactId, "failedRecipient 仍保留专家关联后备")
    }

    @Test
    fun `ingest keeps the known logical account of a backfilled bounce`() {
        sharedGroup()
        stubIngestSave(id = 13L)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(emptyList())

        service.ingest(
            signal = bounceSignal(originalMessageId = null),
            senderAccountCode = "alias1",
            bounceMessageId = "bounce-backfill@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("alias1", captor.value.senderAccountCode, "回填传入的逻辑账号不得被改写成 owner")
    }

    @Test
    fun `ingest deduplicates the same bounce message id before any attribution lookup`() {
        sharedGroup()
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId("bounce-dup@example.com"))
            .thenReturn(true)

        val result = service.ingest(
            signal = bounceSignal(originalMessageId = "<dup@example.com>"),
            senderAccountCode = "acc1",
            bounceMessageId = "bounce-dup@example.com",
            from = "mailer-daemon@example.com",
            subject = "Undelivered",
            receivedAt = LocalDateTime.of(2026, 6, 26, 12, 0)
        )

        assertEquals(BounceIngestResult.DUPLICATE, result)
        Mockito.verify(bounceRecordRepository, Mockito.never()).save(Mockito.any(BounceRecord::class.java))
        Mockito.verifyNoInteractions(mailRecordRepository)
    }

    @Test
    fun `collectBounces logs in as the owner and attributes the alias bounce to the alias`() {
        val (owner, _) = sharedGroup()
        val message = mimeDsnReferencing("<alias-outbound@example.com>", "<bounce-physical@example.com>")
        Mockito.`when`(mailReceiveService.fetchUnseenMessages(owner)).thenReturn(listOf(message))
        stubIngestSave(id = 14L)
        Mockito.`when`(mailRecordRepository.findOutboundCandidatesByMessageId(Mockito.anyString()))
            .thenReturn(listOf(outboundCandidate(95L, "alias1", "<alias-outbound@example.com>")))

        val result = service.collectBounces(owner)

        assertEquals(1, result.collected)
        assertEquals(0, result.skippedDuplicate)
        Mockito.verify(mailReceiveService).fetchUnseenMessages(owner)
        val captor = ArgumentCaptor.forClass(BounceRecord::class.java)
        Mockito.verify(bounceRecordRepository).save(captor.capture())
        assertEquals("alias1", captor.value.senderAccountCode)
        assertEquals("alias-outbound@example.com", captor.value.originalMessageId)
    }

    /** I-1：同物理组夹具——owner `acc1` + 别名 `alias1`（别名归属 acc1）。 */
    private fun sharedGroup(): List<MailSenderAccount> {
        val owner = senderAccount()
        val alias = owner.copy(
            accountCode = "alias1",
            senderEmail = "alias1@example.com",
            inboundMailboxCode = "acc1"
        )
        Mockito.`when`(mailSenderAccountService.listAccounts()).thenReturn(listOf(owner, alias))
        return listOf(owner, alias)
    }

    private fun stubIngestSave(id: Long) {
        Mockito.`when`(bounceRecordRepository.existsByBounceMessageId(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(bounceRecordRepository.save(Mockito.any(BounceRecord::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<BounceRecord>(0).copy(id = id) }
    }

    private fun bounceSignal(
        originalMessageId: String?,
        failedRecipient: String? = null,
        bounceType: String = "HARD",
        dsnStatus: String = "5.1.1"
    ) = BounceSignal(
        bounceType = bounceType,
        dsnStatus = dsnStatus,
        failedRecipient = failedRecipient,
        reason = "Undelivered",
        originalMessageId = originalMessageId
    )

    private fun outboundCandidate(id: Long, accountCode: String?, messageId: String): MailRecord =
        MailRecord(
            id = id,
            expertContactId = 10L,
            direction = "OUTBOUND",
            mailType = "INTRODUCTION",
            senderAccountCode = accountCode,
            messageId = messageId,
            inReplyTo = null,
            subject = "Intro",
            body = "Body",
            matchedQaRuleId = null,
            sendStatus = "SENT",
            receivedAt = null,
            sentAt = LocalDateTime.of(2026, 6, 26, 11, 0)
        )

    /** 物理邮箱里的一封 DSN：带引用原始发信的 Message-ID 与顶层 `Message-ID` 头。 */
    private fun mimeDsnReferencing(originalMessageId: String, bounceMessageId: String): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("mailer-daemon@example.com"))
        message.subject = "Undelivered Mail Returned to Sender"
        message.setHeader("Message-ID", bounceMessageId)

        val multipart = MimeMultipart("report; report-type=delivery-status")
        val textPart = MimeBodyPart()
        textPart.setText("Delivery failed\nOriginal-Message-ID: $originalMessageId")
        multipart.addBodyPart(textPart)

        val dsnPart = MimeBodyPart()
        dsnPart.setContent(
            """
            Reporting-MTA: dns; example.com
            Original-Message-ID: $originalMessageId
            Status: 5.1.1
            """.trimIndent(),
            "message/delivery-status"
        )
        multipart.addBodyPart(dsnPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
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
