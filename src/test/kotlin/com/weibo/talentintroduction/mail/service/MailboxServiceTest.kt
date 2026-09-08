package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailboxExpertSummaryRow
import com.weibo.talentintroduction.mail.repository.MailboxRow
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import java.time.LocalDateTime
import java.util.Optional

class MailboxServiceTest {

    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val mailAttachmentRepository = Mockito.mock(MailAttachmentRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val inboundMailTagService = Mockito.mock(InboundMailTagService::class.java)
    private val expertMaterialService = Mockito.mock(ExpertMaterialService::class.java)
    private val mailboxService = MailboxService(
        mailRecordRepository,
        senderAccountRepository,
        inboundMailProcessingRepository,
        mailAttachmentRepository,
        expertContactRepository,
        inboundMailTagService,
        expertMaterialService
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
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)).thenReturn(emptyList())

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
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)).thenReturn(listOf(activeAccount))

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
        Mockito.verify(senderAccountRepository).findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        Mockito.verifyNoMoreInteractions(mailRecordRepository)
    }

    @Test
    fun `delegates to repository with active accounts filter and converts row to response`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)).thenReturn(listOf(activeAccount))

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
        assertTrue(item.inboundTags.isEmpty())
    }

    @Test
    fun `listMailbox fills inboundTags for inbound processing rows`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)).thenReturn(listOf(activeAccount))

        val inboundRow = MailboxRow(
            source = "INBOUND_PROCESSING",
            id = 20L,
            expertContactId = 99L,
            direction = "INBOUND",
            mailType = "REPLY",
            senderAccountCode = "active_acc",
            triggeredBy = null,
            matchedQaRuleId = null,
            subject = "Question",
            bodyPreview = "Body",
            sendStatus = null,
            sentAt = null,
            receivedAt = LocalDateTime.of(2026, 6, 22, 10, 0),
            processStatus = "PROCESSED",
            reasonType = null,
            expertEmail = "expert@example.com",
            expertName = "Dr. Expert",
            hasAttachment = 0L,
            inboundProcessingId = 20L
        )
        val tagView = TagView(
            tagId = 1L,
            tagType = "QA",
            qaRuleId = 5L,
            label = "薪资",
            source = "AUTO",
            active = true
        )

        Mockito.`when`(
            mailRecordRepository.listMailbox(
                accountCodes = listOf("active_acc"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 0,
                limit = 20,
                offset = 0L
            )
        ).thenReturn(listOf(inboundRow))
        Mockito.`when`(
            mailRecordRepository.countMailbox(
                accountCodes = listOf("active_acc"),
                direction = null,
                accountCode = null,
                keyword = null,
                recipientEmail = null,
                startTime = null,
                endTime = null,
                onlyPending = 0
            )
        ).thenReturn(1L)
        Mockito.`when`(inboundMailTagService.listTagsBatch(listOf(20L))).thenReturn(mapOf(20L to listOf(tagView)))

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

        assertEquals(1, response.items.size)
        assertEquals(listOf(tagView), response.items[0].inboundTags)
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
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)).thenReturn(listOf(activeAccount))
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
        val contact = ExpertContact(
            id = 9L,
            campaignId = 1L,
            orcidId = "0000-0002-4464-150X",
            expertEmail = "alex@cfs.energy",
            expertName = "A. J. Creely",
            currentIndexLevel = "CANDIDATE"
        )
        Mockito.`when`(mailRecordRepository.findByIdOrNull(5L)).thenReturn(record)
        Mockito.`when`(expertContactRepository.findById(9L)).thenReturn(Optional.of(contact))
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("MAIL_RECORD", 5L)).thenReturn(emptyList())

        val detail = mailboxService.getMailboxDetail("MAIL_RECORD", 5L)

        assertEquals("cleaned body", detail.body)
        assertEquals("MAIL_RECORD", detail.source)
        assertEquals("OUTBOUND", detail.direction)
        assertFalse(detail.hasAttachment)
        assertTrue(detail.inboundTags.isEmpty())
        assertEquals("0000-0002-4464-150X", detail.expertOrcidId)
        assertEquals("CANDIDATE", detail.expertIndexLevel)
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
            expertContactId = 9L,
            retryCount = 0,
            lastError = null,
            createdAt = null,
            updatedAt = null
        )
        val contact = ExpertContact(
            id = 9L,
            campaignId = 1L,
            orcidId = "0000-0002-4464-150X",
            expertEmail = "alex@cfs.energy",
            expertName = "A. J. Creely",
            currentIndexLevel = "APPLICATION"
        )
        Mockito.`when`(inboundMailProcessingRepository.findById(12L)).thenReturn(Optional.of(inbound))
        Mockito.`when`(expertContactRepository.findById(9L)).thenReturn(Optional.of(contact))
        // 附件归属委托 06 精确来源解析；本用例无附件（旧 findFirstByMessageId 回退已停用）。
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", 12L)).thenReturn(emptyList())
        Mockito.`when`(inboundMailTagService.listTags(12L)).thenReturn(
            listOf(
                TagView(
                    tagId = 3L,
                    tagType = "CUSTOM",
                    qaRuleId = null,
                    label = "跟进",
                    source = "MANUAL",
                    active = true
                )
            )
        )

        val detail = mailboxService.getMailboxDetail("INBOUND_PROCESSING", 12L)

        assertEquals("inbound cleaned", detail.body)
        assertEquals("INBOUND_PROCESSING", detail.source)
        assertEquals("INBOUND", detail.direction)
        assertEquals(12L, detail.inboundProcessingId)
        assertEquals("alex@cfs.energy", detail.expertEmail)
        assertEquals("0000-0002-4464-150X", detail.expertOrcidId)
        assertEquals("APPLICATION", detail.expertIndexLevel)
        assertEquals(1, detail.inboundTags.size)
        assertEquals("跟进", detail.inboundTags[0].label)
        assertFalse(detail.hasAttachment)
        // 无账号/专家/方向限定的 findFirstByMessageId 回退必须不再被触达。
        Mockito.verify(mailRecordRepository, never()).findFirstByMessageIdOrderByCreatedAtDesc(Mockito.anyString())
    }

    @Test
    fun `resolveAttachments uses exact bridge result from material resolver and never touches old lookups`() {
        // 06 精确来源：bridge（transfer.inbound_processing_id）/ 直接 owner 附件。
        val bridgedAttachment = com.weibo.talentintroduction.mail.domain.MailAttachment(
            id = 1L,
            mailRecordId = null,
            inboundProcessingId = 12L,
            fileName = "a.pdf",
            contentType = "application/pdf",
            fileSize = 10L,
            storagePath = "/tmp/a.pdf"
        )
        val directAttachment = com.weibo.talentintroduction.mail.domain.MailAttachment(
            id = 2L,
            mailRecordId = null,
            inboundProcessingId = 12L,
            fileName = "b.pdf",
            contentType = "application/pdf",
            fileSize = 20L,
            storagePath = "/tmp/b.pdf"
        )
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", 12L))
            .thenReturn(listOf(bridgedAttachment, directAttachment))

        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertEquals(listOf("a.pdf", "b.pdf"), attachments.map { it.fileName })
        Mockito.verify(expertMaterialService).resolveMessageAttachments("INBOUND_PROCESSING", 12L)
        Mockito.verify(mailRecordRepository, never()).findFirstByMessageIdOrderByCreatedAtDesc(Mockito.anyString())
        Mockito.verify(mailAttachmentRepository, never())
            .findAllByInboundProcessingIdOrderByCreatedAtAsc(Mockito.anyLong())
    }

    @Test
    fun `resolveAttachments keeps strictly unique legacy relation result from the material resolver`() {
        // 06 精确来源：严格唯一旧关系（同账号/同专家/INBOUND/非空 messageId 恰 1 条）
        // 的结果原样透传，MailboxService 不再自行按 messageId 猜最新 record。
        val legacyAttachment = com.weibo.talentintroduction.mail.domain.MailAttachment(
            id = 3L,
            mailRecordId = 9L,
            inboundProcessingId = null,
            fileName = "legacy-cv.pdf",
            contentType = "application/pdf",
            fileSize = 30L,
            storagePath = "/tmp/legacy-cv.pdf"
        )
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", 12L))
            .thenReturn(listOf(legacyAttachment))

        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertEquals(1, attachments.size)
        assertEquals("legacy-cv.pdf", attachments[0].fileName)
        Mockito.verify(expertMaterialService).resolveMessageAttachments("INBOUND_PROCESSING", 12L)
        Mockito.verify(mailRecordRepository, never()).findFirstByMessageIdOrderByCreatedAtDesc(Mockito.anyString())
    }

    @Test
    fun `resolveAttachments refuses cross-account ambiguity as empty without guessing`() {
        // 跨账号/重复投递歧义 → 06 返回空（来源待核对），MailboxService 绝不回退到
        // 不限账号/专家/方向的 findFirstByMessageId。
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", 12L))
            .thenReturn(emptyList())

        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertTrue(attachments.isEmpty())
        Mockito.verify(mailRecordRepository, never()).findFirstByMessageIdOrderByCreatedAtDesc(Mockito.anyString())
        Mockito.verify(inboundMailProcessingRepository, never()).findById(Mockito.anyLong())
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
        Mockito.`when`(expertMaterialService.resolveMessageAttachments("INBOUND_PROCESSING", 12L))
            .thenReturn(listOf(inboundAttachment))

        val detail = mailboxService.getMailboxDetail("INBOUND_PROCESSING", 12L)
        val attachments = mailboxService.resolveAttachments("INBOUND_PROCESSING", 12L)

        assertTrue(detail.hasAttachment)
        assertEquals(1, attachments.size)
    }

    @Test
    fun `listByExpert returns empty when no real accounts`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(emptyList())

        val response = mailboxService.listByExpert(
            direction = null, accountCode = null, keyword = null, recipientEmail = null,
            startTime = null, endTime = null, pending = false, tag = null, page = 0, size = 20
        )

        assertEquals(0L, response.totalCount)
        assertTrue(response.groups.isEmpty())
        Mockito.verifyNoInteractions(mailRecordRepository)
    }

    @Test
    fun `listByExpert skips sub mail query when expert page is empty`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.countMailboxExperts(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null
            )
        ).thenReturn(0L)

        val response = mailboxService.listByExpert(
            direction = null, accountCode = null, keyword = null, recipientEmail = null,
            startTime = null, endTime = null, pending = false, tag = null, page = 0, size = 20
        )

        assertEquals(0L, response.totalCount)
        assertTrue(response.groups.isEmpty())
        Mockito.verify(mailRecordRepository, never()).listMailboxByExpertContactIds(
            Mockito.anyList(), Mockito.anyList(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
            Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.anyInt(), Mockito.isNull()
        )
    }

    @Test
    fun `listByExpert assembles all outbound and inbound mails with pending count`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.countMailboxExperts(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null
            )
        ).thenReturn(1L)

        val summary = MailboxExpertSummaryRow(
            expertContactId = 200L,
            expertName = "Expert A",
            expertEmail = "a@example.com",
            orcidId = "0000-0001",
            operatorStatus = "REPLIED",
            currentIndexLevel = "APPLICATION",
            mailCount = 2L,
            pendingCount = 1L,
            latestEventAt = LocalDateTime.of(2026, 7, 18, 12, 0)
        )
        Mockito.`when`(
            mailRecordRepository.listMailboxExpertSummaries(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null, limit = 20, offset = 0L
            )
        ).thenReturn(listOf(summary))

        val outbound = MailboxRow(
            source = "MAIL_RECORD", id = 40L, expertContactId = 200L, direction = "OUTBOUND",
            mailType = "INTRODUCTION", senderAccountCode = "active_acc", triggeredBy = "OPERATOR",
            matchedQaRuleId = null, subject = "Introduction", bodyPreview = "Hello", sendStatus = "SENT",
            sentAt = LocalDateTime.of(2026, 7, 18, 11, 0), receivedAt = null, processStatus = null,
            reasonType = null, expertEmail = "a@example.com", expertName = "Expert A",
            hasAttachment = 0L, inboundProcessingId = null
        )
        val inbound = MailboxRow(
            source = "INBOUND_PROCESSING", id = 30L, expertContactId = 200L, direction = "INBOUND",
            mailType = "REPLY", senderAccountCode = "active_acc", triggeredBy = null,
            matchedQaRuleId = null, subject = "Reply", bodyPreview = "Body", sendStatus = null,
            sentAt = null, receivedAt = LocalDateTime.of(2026, 7, 18, 12, 0),
            processStatus = "MANUAL_REVIEW", reasonType = null, expertEmail = "a@example.com",
            expertName = "Expert A", hasAttachment = 0L, inboundProcessingId = 30L
        )
        Mockito.`when`(
            mailRecordRepository.listMailboxByExpertContactIds(
                expertContactIds = listOf(200L), accountCodes = listOf("active_acc"), direction = null,
                accountCode = null, keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null
            )
        ).thenReturn(listOf(inbound, outbound))

        val response = mailboxService.listByExpert(
            direction = null, accountCode = null, keyword = null, recipientEmail = null,
            startTime = null, endTime = null, pending = false, tag = null, page = 0, size = 20
        )

        assertEquals(1L, response.totalCount)
        assertEquals(2L, response.groups.single().mailCount)
        assertEquals(1L, response.groups.single().pendingCount)
        assertEquals(listOf("INBOUND_PROCESSING", "MAIL_RECORD"), response.groups.single().mails.map { it.source })
    }
    @Test
    fun `listByExpert forwards expertContactId into count and summary and derives sub mail ids from summary`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.countMailboxExperts(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null, expertContactId = 100L
            )
        ).thenReturn(1L)

        val summary = MailboxExpertSummaryRow(
            expertContactId = 200L,
            expertName = "Expert A",
            expertEmail = "a@example.com",
            orcidId = "0000-0001",
            operatorStatus = "REPLIED",
            currentIndexLevel = "APPLICATION",
            mailCount = 5L,
            pendingCount = 0L,
            receivedCount = 2L,
            sentCount = 2L,
            failedCount = 1L,
            latestEventAt = LocalDateTime.of(2026, 7, 18, 12, 0)
        )
        Mockito.`when`(
            mailRecordRepository.listMailboxExpertSummaries(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null, limit = 20, offset = 0L, expertContactId = 100L
            )
        ).thenReturn(listOf(summary))

        val outbound = MailboxRow(
            source = "MAIL_RECORD", id = 40L, expertContactId = 200L, direction = "OUTBOUND",
            mailType = "INTRODUCTION", senderAccountCode = "active_acc", triggeredBy = "OPERATOR",
            matchedQaRuleId = null, subject = "Introduction", bodyPreview = "Hello", sendStatus = "SENT",
            sentAt = LocalDateTime.of(2026, 7, 18, 11, 0), receivedAt = null, processStatus = null,
            reasonType = null, expertEmail = "a@example.com", expertName = "Expert A",
            hasAttachment = 0L, inboundProcessingId = null
        )
        val inbound = MailboxRow(
            source = "INBOUND_PROCESSING", id = 30L, expertContactId = 200L, direction = "INBOUND",
            mailType = "REPLY", senderAccountCode = "active_acc", triggeredBy = null,
            matchedQaRuleId = null, subject = "Reply", bodyPreview = "Body", sendStatus = null,
            sentAt = null, receivedAt = LocalDateTime.of(2026, 7, 18, 12, 0),
            processStatus = "MANUAL_REVIEW", reasonType = null, expertEmail = "a@example.com",
            expertName = "Expert A", hasAttachment = 0L, inboundProcessingId = 30L
        )
        Mockito.`when`(
            mailRecordRepository.listMailboxByExpertContactIds(
                expertContactIds = listOf(200L), accountCodes = listOf("active_acc"), direction = null,
                accountCode = null, keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 0, tag = null
            )
        ).thenReturn(listOf(inbound, outbound))

        val response = mailboxService.listByExpert(
            direction = null, accountCode = null, keyword = null, recipientEmail = null,
            startTime = null, endTime = null, pending = false, tag = null, page = 0, size = 20,
            expertContactId = 100L
        )

        Mockito.verify(mailRecordRepository).countMailboxExperts(
            accountCodes = listOf("active_acc"), direction = null, accountCode = null,
            keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 0, tag = null, expertContactId = 100L
        )
        Mockito.verify(mailRecordRepository).listMailboxExpertSummaries(
            accountCodes = listOf("active_acc"), direction = null, accountCode = null,
            keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 0, tag = null, limit = 20, offset = 0L, expertContactId = 100L
        )
        Mockito.verify(mailRecordRepository).listMailboxByExpertContactIds(
            expertContactIds = listOf(200L), accountCodes = listOf("active_acc"), direction = null,
            accountCode = null, keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 0, tag = null
        )

        val group = response.groups.single()
        assertEquals(2L, group.receivedCount)
        assertEquals(2L, group.sentCount)
        assertEquals(1L, group.failedCount)
        assertEquals(5L, group.mailCount)
        assertEquals(0L, group.pendingCount)
        assertEquals(2, group.mails.size)
        assertEquals(listOf("INBOUND_PROCESSING", "MAIL_RECORD"), group.mails.map { it.source })
    }

    @Test
    fun `listByExpert forwards pending scope independently of expert view`() {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(activeAccount))
        Mockito.`when`(
            mailRecordRepository.countMailboxExperts(
                accountCodes = listOf("active_acc"), direction = null, accountCode = null,
                keyword = null, recipientEmail = null, startTime = null, endTime = null,
                onlyPending = 1, tag = null
            )
        ).thenReturn(0L)

        mailboxService.listByExpert(
            direction = null, accountCode = null, keyword = null, recipientEmail = null,
            startTime = null, endTime = null, pending = true, tag = null, page = 0, size = 20
        )

        Mockito.verify(mailRecordRepository).countMailboxExperts(
            accountCodes = listOf("active_acc"), direction = null, accountCode = null,
            keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 1, tag = null
        )
    }
}
