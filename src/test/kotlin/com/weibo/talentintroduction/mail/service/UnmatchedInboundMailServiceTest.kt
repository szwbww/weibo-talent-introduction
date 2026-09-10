package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.ReasonTypeCount
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class UnmatchedInboundMailServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val senderAccountRepository = Mockito.mock(MailSenderAccountRepository::class.java)
    private val expertIndexWriterService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexWriterService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val mailAttachmentService = Mockito.mock(MailAttachmentService::class.java)

    private val service = UnmatchedInboundMailService(
        inboundMailProcessingRepository = inboundMailProcessingRepository,
        expertContactRepository = expertContactRepository,
        expertEmailAliasService = expertEmailAliasService,
        mailRecordRepository = mailRecordRepository,
        senderAccountRepository = senderAccountRepository,
        expertIndexWriterService = expertIndexWriterService,
        operatorActionLogService = operatorActionLogService,
        mailAttachmentService = mailAttachmentService
    )

    private fun contact(id: Long, email: String) = ExpertContact(
        id = id, campaignId = 10L, orcidId = "orcid-$id",
        expertEmail = email, expertName = null
    )

    private fun processing(
        id: Long, email: String, status: String = "MANUAL_REVIEW",
        reason: String = "CONTACT_NOT_FOUND", contactId: Long? = null,
        messageId: String? = null, inReplyTo: String? = null
    ) = InboundMailProcessing(
        id = id, senderAccountCode = "acc1", imapUid = 100L,
        fromEmail = email, messageId = messageId, inReplyTo = inReplyTo,
        subject = "Test", receivedAt = LocalDateTime.now(),
        processStatus = status, processReason = reason,
        expertContactId = contactId
    )

    private fun account(code: String, enabled: Boolean) = MailSenderAccount(
        accountCode = code, senderEmail = "$code@b.com", senderName = code, senderTitle = null,
        senderDisplayName = null, teamName = null, countryName = null,
        smtpHost = "h", smtpPort = 587, smtpUsername = "u", smtpPassword = "p",
        imapHost = "h", imapPort = 993, imapUsername = "u", imapPassword = "p",
        enabled = enabled
    )

    private fun stubAccounts(accounts: List<MailSenderAccount>) {
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(accounts)
    }

    @Test
    fun `listManualReviewQueue default mode still uses the legacy queue queries`() {
        stubAccounts(listOf(account("acc1", true)))
        Mockito.`when`(
            inboundMailProcessingRepository.findManualReviewQueue("UNMATCHED_CONTACT", null, null, 20, 40)
        ).thenReturn(listOf(processing(id = 3L, email = "legacy@b.com")))
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewQueue("UNMATCHED_CONTACT", null, null)
        ).thenReturn(4L)
        Mockito.`when`(inboundMailProcessingRepository.countManualReviewByAccounts(listOf("acc1"))).thenReturn(4L)
        Mockito.`when`(inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(listOf("acc1")))
            .thenReturn(emptyList())

        // I-7：未传 unmatchedOnly 时（邮件监控等既有读路径）行为与响应字段保持不变。
        val result = service.listManualReviewQueue("UNMATCHED_CONTACT", null, null, 20, 40)

        assertEquals(3L, result.records[0].id)
        assertEquals(4L, result.totalCount)
        assertEquals(4L, result.manualReviewTotal)
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .findUnmatchedManualReviewQueue(Mockito.anyList(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())
    }

    @Test
    fun `listManualReviewQueue unmatchedOnly passes non-simulator accounts and keeps the global badge total`() {
        val codes = listOf("acc1", "acc-disabled")
        stubAccounts(listOf(account("acc1", true), account("acc-disabled", false)))
        Mockito.`when`(
            inboundMailProcessingRepository.findUnmatchedManualReviewQueue(codes, null, 20, 0)
        ).thenReturn(listOf(processing(id = 5L, email = "unmatched@b.com")))
        Mockito.`when`(
            inboundMailProcessingRepository.countUnmatchedManualReviewQueue(codes, null)
        ).thenReturn(1L)
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewByAccounts(codes)
        ).thenReturn(7L)
        Mockito.`when`(
            inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(codes)
        ).thenReturn(emptyList())

        // I-2：disabled 真实账号仍属于收发范围（不追加 enabled 判定）；I-8：空搜索串归一为 null。
        val result = service.listManualReviewQueue(
            pageSize = 20, pageOffset = 0, unmatchedOnly = true, query = "   "
        )

        assertEquals(1, result.records.size)
        assertEquals(1L, result.totalCount)
        assertEquals(7L, result.manualReviewTotal)
        // I-2：账号范围只排除模拟器（用同一 finder），不追加 enabled 判定。
        Mockito.verify(senderAccountRepository)
            .findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .findManualReviewQueue(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())
    }

    @Test
    fun `listManualReviewQueue unmatchedOnly forwards a trimmed query`() {
        val codes = listOf("acc1")
        stubAccounts(listOf(account("acc1", true)))
        Mockito.`when`(
            inboundMailProcessingRepository.findUnmatchedManualReviewQueue(codes, "acceptance-key", 20, 40)
        ).thenReturn(listOf(processing(id = 9L, email = "key@b.com")))
        Mockito.`when`(
            inboundMailProcessingRepository.countUnmatchedManualReviewQueue(codes, "acceptance-key")
        ).thenReturn(1L)
        Mockito.`when`(inboundMailProcessingRepository.countManualReviewByAccounts(codes)).thenReturn(3L)
        Mockito.`when`(inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(codes))
            .thenReturn(listOf(ReasonTypeCount("UNMATCHED_CONTACT", 3L)))

        val result = service.listManualReviewQueue(
            pageSize = 20, pageOffset = 40, unmatchedOnly = true, query = "  acceptance-key  "
        )

        assertEquals(9L, result.records[0].id)
        assertEquals("UNMATCHED_CONTACT", result.countsByReasonType.keys.first())
    }

    @Test
    fun `listManualReviewQueue unmatchedOnly with no accounts never hits the IN query`() {
        stubAccounts(emptyList())

        val result = service.listManualReviewQueue(unmatchedOnly = true, query = "key")

        assertEquals(0, result.records.size)
        assertEquals(0L, result.totalCount)
        assertEquals(0L, result.manualReviewTotal)
        assertTrue(result.countsByReasonType.isEmpty())
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .findUnmatchedManualReviewQueue(Mockito.anyList(), Mockito.any(), Mockito.anyInt(), Mockito.anyInt())
        Mockito.verify(inboundMailProcessingRepository, Mockito.never())
            .countUnmatchedManualReviewQueue(Mockito.anyList(), Mockito.any())
    }

    @Test
    fun `listManualReviewQueue returns manual review records and counts`() {
        val records = listOf(processing(id = 1L, email = "a@b.com"))
        Mockito.`when`(
            inboundMailProcessingRepository.findManualReviewQueue(null, null, null, 20, 0)
        ).thenReturn(records)
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewQueue(null, null, null)
        ).thenReturn(1L)
        Mockito.`when`(senderAccountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(MailSenderAccount(accountCode = "acc1", senderEmail = "a@b.com", senderName = "A", senderTitle = null, senderDisplayName = null, teamName = null, countryName = null, smtpHost = "h", smtpPort = 587, smtpUsername = "u", smtpPassword = "p", imapHost = "h", imapPort = 993, imapUsername = "u", imapPassword = "p")))
        Mockito.`when`(
            inboundMailProcessingRepository.countManualReviewByAccounts(listOf("acc1"))
        ).thenReturn(1L)
        Mockito.`when`(
            inboundMailProcessingRepository.countGroupedByReasonTypeForAccounts(listOf("acc1"))
        ).thenReturn(listOf(ReasonTypeCount("QA_NO_MATCH", 1L)))

        val result = service.listManualReviewQueue(null, null, null, 20, 0)
        assertEquals(1, result.records.size)
        assertEquals("a@b.com", result.records[0].fromEmail)
        assertEquals(1L, result.totalCount)
        assertEquals(1L, result.manualReviewTotal)
        assertEquals(1L, result.countsByReasonType["QA_NO_MATCH"])
    }

    @Test
    fun `bindToContact updates record and adds alias`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "alias@example.com")
        val c = contact(contactId, "main@example.com").copy(needsManualAttention = true)

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "alias@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "alias@example.com", normalizedEmail = "alias@example.com"))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")
        assertEquals(contactId, result.expertContactId)
        assertEquals("MANUAL_REVIEW", result.processStatus)
        assertEquals("MANUAL_BOUND", result.processReason)
        assertEquals(null, result.resolvedBy)
        assertEquals(null, result.resolvedAt)
        Mockito.verify(expertContactRepository).save(Mockito.argThat<ExpertContact> { it.needsManualAttention })
    }

    @Test
    fun `bindToContact rejects already bound record`() {
        val recordId = 1L
        val record = processing(id = recordId, email = "a@b.com", status = "PROCESSED", reason = "MANUAL_BOUND", contactId = 10L)
        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))

        assertThrows(IllegalArgumentException::class.java) {
            service.bindToContact(recordId, 20L, "operator1")
        }
    }

    @Test
    fun `suggestCandidates uses in_reply_to when available`() {
        val record = processing(id = 1L, email = "a@b.com", messageId = "msg-1", inReplyTo = "out-msg-1")
        val outboundRecord = MailRecord(
            id = 50L, expertContactId = 10L, direction = "OUTBOUND", mailType = "INTRODUCTION",
            messageId = "out-msg-1", inReplyTo = null, subject = "Hello", body = "Body",
            matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = LocalDateTime.now()
        )
        val c = contact(10L, "expert@example.com")

        Mockito.`when`(mailRecordRepository.findByMessageId("out-msg-1")).thenReturn(outboundRecord)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(c))

        val candidates = service.suggestCandidates(record)
        assertEquals(1, candidates.size)
        assertEquals("IN_REPLY_TO", candidates[0].reason)
        assertEquals(90, candidates[0].confidence)
    }

    @Test
    fun `suggestCandidates matches prefixed in_reply_to to unprefixed stored message id`() {
        val prefixed = "<6136051B41AACA62+reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val stored = "<reminder-2088-710aba50-77fa-4936-a8d3-72ecffaba836@talents.szwebotech.cn>"
        val record = processing(id = 1L, email = "a@b.com", inReplyTo = prefixed)
        val outboundRecord = MailRecord(
            id = 50L, expertContactId = 10L, direction = "OUTBOUND", mailType = "INTRODUCTION",
            messageId = stored, inReplyTo = null, subject = "Hello", body = "Body",
            matchedQaRuleId = null, sendStatus = null, receivedAt = null, sentAt = LocalDateTime.now()
        )
        val c = contact(10L, "expert@example.com")

        Mockito.`when`(mailRecordRepository.findByMessageId(prefixed)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.findByMessageId(stored)).thenReturn(outboundRecord)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(c))

        val candidates = service.suggestCandidates(record)
        assertEquals(1, candidates.size)
        assertEquals("IN_REPLY_TO", candidates[0].reason)
        assertEquals(90, candidates[0].confidence)
        assertEquals(10L, candidates[0].contact.id)
        // I-4: 原值候选先查（落空），剥离前缀候选后查（命中）
        Mockito.verify(mailRecordRepository).findByMessageId(prefixed)
        Mockito.verify(mailRecordRepository).findByMessageId(stored)
    }

    @Test
    fun `suggestCandidates with null in_reply_to does not query and has no IN_REPLY_TO candidate`() {
        val record = processing(id = 1L, email = "a@b.com")

        val candidates = service.suggestCandidates(record)

        Mockito.verify(mailRecordRepository, Mockito.never()).findByMessageId(Mockito.anyString())
        assertTrue(candidates.none { it.reason == "IN_REPLY_TO" })
    }

    @Test
    fun `suggestCandidates falls back to NAME_OR_EMAIL_MATCH when prefixed in_reply_to has no record`() {
        val prefixed = "<0123456789ABCDEF+nonexistent-id@example.com>"
        val record = processing(id = 1L, email = "expert@example.com", inReplyTo = prefixed)
        val c = contact(10L, "expert@example.com")

        Mockito.`when`(mailRecordRepository.findByMessageId(Mockito.anyString())).thenReturn(null)
        Mockito.`when`(
            expertContactRepository.findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
                Mockito.anyString(), Mockito.anyString()
            )
        ).thenReturn(listOf(c))

        val candidates = service.suggestCandidates(record)
        assertTrue(candidates.none { it.reason == "IN_REPLY_TO" })
        assertEquals("NAME_OR_EMAIL_MATCH", candidates[0].reason)
        assertEquals(60, candidates[0].confidence)
    }

    @Test
    fun `bindToContact with unmatched mail does not trigger auto reply`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "old@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "old@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "old@example.com", normalizedEmail = "old@example.com"))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")
        assertEquals("MANUAL_REVIEW", result.processStatus)
        assertEquals("MANUAL_BOUND", result.processReason)

        Mockito.verify(expertEmailAliasService).bindAlias(contactId, "old@example.com", "MANUAL_BIND")
        Mockito.verify(inboundMailProcessingRepository).save(Mockito.any(InboundMailProcessing::class.java))
        Mockito.verify(mailAttachmentService).ensureDocumentsForProcessingAttachments(recordId, contactId)
    }

    @Test
    fun `bindToContact after metadata registration links the existing attachments without file io`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "expert@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "expert@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "expert@example.com", normalizedEmail = "expert@example.com"))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }

        val result = service.bindToContact(recordId, contactId, "operator1")

        assertEquals(contactId, result.expertContactId)
        // 绑定只触发幂等补文档（同 attachmentId、零文件 I/O）；不下载、不搬迁 owner
        Mockito.verify(mailAttachmentService).ensureDocumentsForProcessingAttachments(recordId, contactId)
        Mockito.verify(mailAttachmentService, Mockito.never()).saveUnmatchedAttachments(
            Mockito.anyLong(), Mockito.anyList(), Mockito.any()
        )
        Mockito.verify(mailAttachmentService, Mockito.never()).saveInboundAttachments(
            Mockito.anyLong(), Mockito.anyLong(), Mockito.anyList()
        )
    }

    @Test
    fun `markResolved resolves record and clears attention if last`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "expert@example.com", contactId = contactId)
        val c = contact(contactId, "expert@example.com").copy(needsManualAttention = true)

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(inboundMailProcessingRepository.save(Mockito.any(InboundMailProcessing::class.java)))
            .thenAnswer { it.getArgument<InboundMailProcessing>(0) }
        Mockito.`when`(inboundMailProcessingRepository.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW"))
            .thenReturn(0L)
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertContactRepository.save(Mockito.any(ExpertContact::class.java)))
            .thenAnswer { it.getArgument<ExpertContact>(0) }

        val result = service.markResolved(recordId, "operator1", "done")
        assertEquals("PROCESSED", result.processStatus)
        assertEquals("MANUAL_RESOLVED", result.processReason)
        Mockito.verify(expertContactRepository).save(Mockito.argThat { !it.needsManualAttention })
    }

    @Test
    fun `bindToContact with promoteToApplication throws when ES promotion fails`() {
        val recordId = 1L
        val contactId = 10L
        val record = processing(id = recordId, email = "alias@example.com")
        val c = contact(contactId, "main@example.com")

        Mockito.`when`(inboundMailProcessingRepository.findById(recordId)).thenReturn(Optional.of(record))
        Mockito.`when`(expertContactRepository.findById(contactId)).thenReturn(Optional.of(c))
        Mockito.`when`(expertEmailAliasService.bindAlias(contactId, "alias@example.com", "MANUAL_BIND"))
            .thenReturn(ExpertEmailAlias(id = 100L, expertContactId = contactId, email = "alias@example.com", normalizedEmail = "alias@example.com"))
        Mockito.`when`(expertIndexWriterService.promoteToApplication(
            orcid = Mockito.anyString() ?: "",
            contact = Mockito.any(ExpertContact::class.java) ?: contact(10L, ""),
            firstReplyAt = Mockito.any() ?: java.time.Instant.now(),
            sourceInboundId = Mockito.any(),
            triggeredBy = Mockito.anyString() ?: "",
            operatorName = Mockito.anyString() ?: ""
        )).thenReturn(false)

        assertThrows(IllegalStateException::class.java) {
            service.bindToContact(recordId, contactId, "operator1", promoteToApplication = true)
        }
    }
}
