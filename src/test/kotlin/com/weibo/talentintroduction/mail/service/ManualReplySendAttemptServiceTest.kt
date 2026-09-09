package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnosticFlag
import com.weibo.talentintroduction.llm.service.TrustReplyDiagnostics
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyRequestDiagnostic
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.service.MailComposeTemplateBlockDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateDetail
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class ManualReplySendAttemptServiceTest {
    private val attemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val service = ManualReplySendAttemptService(
        attemptRepository, mailRecordRepository, mailRecordQaRuleRepository, operatorActionLogService
    )

    private val payload = ManualReplySendAttemptService.SendPayload(
        orcidId = "orcid-1",
        contactId = 1L,
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = "expert@test.com",
        subject = "Re: Test",
        finalText = "Hello world",
        finalHtml = "<p>Hello world</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = listOf(10L),
        primaryRuleId = 10L
    )

    private fun createAttempt(status: String, fp: ManualReplySendAttemptService.Fingerprint) = MailSendAttempt(
        id = 1L,
        orcidId = payload.orcidId,
        mailType = fp.shortKey,
        accountCode = payload.accountCode,
        messageId = fp.messageId,
        status = status,
        recipient = payload.normalizedRecipient,
        subject = payload.subject,
        body = fp.fullHex,
        contentType = "application/x-manual-rich-fingerprint-v1"
    )

    private fun stubFindForUpdate(fp: ManualReplySendAttemptService.Fingerprint, status: String) {
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(createAttempt(status, fp))
    }

    // I-2: 无 calendar 时 computeFingerprint 保持 01 冻结的 golden 全量哈希逐字不变
    // （冻结于 child 02 实施前，ManualReplySendAttemptServiceTest payload）。
    @Test
    fun `fingerprint without calendar stays byte identical to pre-02 golden`() {
        val fp = service.computeFingerprint(payload)
        assertEquals("fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda", fp.fullHex)
    }

    // ───────────────────────── fast-p 02：calendar 语义指纹（I-2） ─────────────────────────
    // IP-3/4/5：日历语义/字节均来自 01 真实生成器输出（preview 产物），禁止手写
    // 两个相同假串伪证集成。

    private val meetingTemplateBody = "Dear {{expert_salutation}},\n\n" +
        "Thank you for confirming.\n\n" +
        "We have noted the meeting time as {{meeting_time}}.\n\n" +
        "Please join the meeting using the following link:\n\n" +
        "{{zoom_url}}\n\n" +
        "We look forward to speaking with you.\n\n" +
        "Best regards,\n" +
        "{{sender_signature}}"

    private fun meetingAccount() = MailSenderAccount(
        accountCode = "sender-1",
        senderEmail = "sender@example.com",
        senderName = "LuKai",
        senderTitle = "Customer Care Officer",
        senderDisplayName = "LuKai",
        teamName = "Qingfei Tech Talent Team",
        countryName = "China",
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p"
    )

    private fun meetingInput(zoomUrl: String = "https://zoom.us/j/87102801187", generatedAt: String = "2026-09-09T03:00:40Z") =
        MeetingInput(
            templateId = 100L,
            templateBody = meetingTemplateBody,
            expertSalutation = "Professor Basdogan",
            zoneId = "Europe/Istanbul",
            startLocal = "2026-09-11T10:00",
            endLocal = "2026-09-11T10:30",
            zoomUrl = zoomUrl,
            senderSignature = "LuKai, Customer Care Officer\nQingfei Tech Talent Team China",
            generatedAt = generatedAt
        )

    /** 01 真实生成器产物 → 快照：stub 只读身份/模板，preview 生成真实 ICS/sha256/semanticSha256。 */
    private fun realSnapshot(input: MeetingInput = meetingInput()): CalendarAttachmentSnapshot {
        val inboundRepo = Mockito.mock(InboundMailProcessingRepository::class.java)
        val contactRepo = Mockito.mock(ExpertContactRepository::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val templateService = Mockito.mock(MailComposeTemplateService::class.java)
        val processing = InboundMailProcessing(
            id = 100L, senderAccountCode = "sender-1", imapUid = 1L,
            messageId = "in-1", fromEmail = "expert@test.com",
            subject = "Re: invitation", body = "Hello", cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
            expertContactId = 1L
        )
        val contact = ExpertContact(
            id = 1L, campaignId = 1, orcidId = "0000-0001-2345-6789",
            expertEmail = "expert@test.com", expertName = "Professor Basdogan",
            currentStatus = "WAITING_MEETING_CONFIRMATION"
        )
        Mockito.`when`(inboundRepo.findById(100L)).thenReturn(Optional.of(processing))
        Mockito.`when`(contactRepo.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(accountService.getManualSendAccount("sender-1")).thenReturn(meetingAccount())
        Mockito.`when`(templateService.getById(100L)).thenReturn(
            MailComposeTemplateDetail(
                id = 100L,
                templateCode = "MANUAL_MEETING_CONFIRMATION",
                templateName = "专家会议确认 · 英文",
                subject = "Meeting confirmation",
                description = "仅供收发件箱会议确认。",
                mailType = "MANUAL_MEETING_CONFIRMATION",
                subjectVariants = null,
                enabled = true,
                blocks = listOf(
                    MailComposeTemplateBlockDetail(
                        id = 1, blockOrder = 0, blockType = "CUSTOM_TEXT",
                        refId = null, refDisplayName = null, customText = input.templateBody,
                    )
                ),
                createdAt = null,
                updatedAt = null
            )
        )
        Mockito.`when`(templateService.listEnabled()).thenReturn(
            listOf(
                MailComposeTemplate(
                    id = 100L,
                    templateCode = "MANUAL_MEETING_CONFIRMATION",
                    templateName = "专家会议确认 · 英文",
                    subject = "Meeting confirmation",
                    mailType = "MANUAL_MEETING_CONFIRMATION",
                    enabled = true
                )
            )
        )
        val generator = MeetingConfirmationService(
            inboundRepo, contactRepo, accountService, templateService, MailContentService()
        )
        val preview = generator.preview(100L, MeetingPreviewRequest(contactId = 1L, meeting = input))
        return CalendarAttachmentSnapshot(
            schemaVersion = 1,
            filename = preview.attachment.filename,
            contentType = preview.attachment.contentType,
            icsText = preview.attachment.icsText,
            sha256 = preview.attachment.sha256,
            semanticSha256 = preview.attachment.semanticSha256
        )
    }

    @Test
    fun `fingerprint with same calendar semantics is identical even when ics bytes change`() {
        val snapshotA = realSnapshot(meetingInput(generatedAt = "2026-09-09T03:00:40Z"))
        val snapshotB = realSnapshot(meetingInput(generatedAt = "2026-09-10T09:30:00Z"))
        assertTrue(snapshotA.icsText != snapshotB.icsText, "generatedAt 变化应改变 ICS 字节")
        assertEquals(snapshotA.semanticSha256, snapshotB.semanticSha256)

        val fpA = service.computeFingerprint(payload.copy(calendarAttachment = snapshotA))
        val fpB = service.computeFingerprint(payload.copy(calendarAttachment = snapshotB))
        assertEquals(64, fpA.fullHex.length)
        assertEquals("MANUAL_RICH:" + fpA.fullHex.take(32), fpA.shortKey)
        assertEquals(fpA.fullHex, fpB.fullHex)
        assertEquals(fpA.shortKey, fpB.shortKey)
    }

    @Test
    fun `fingerprint changes when calendar semantics change`() {
        val snapshotA = realSnapshot(meetingInput(zoomUrl = "https://zoom.us/j/87102801187"))
        val snapshotB = realSnapshot(meetingInput(zoomUrl = "https://zoom.us/j/87102801188?pwd=other"))
        assertTrue(snapshotA.semanticSha256 != snapshotB.semanticSha256)

        val fpA = service.computeFingerprint(payload.copy(calendarAttachment = snapshotA))
        val fpB = service.computeFingerprint(payload.copy(calendarAttachment = snapshotB))
        assertTrue(fpA.fullHex != fpB.fullHex)
        assertTrue(fpA.shortKey != fpB.shortKey)
    }

    @Test
    fun `fingerprint differs between no calendar and calendar payload`() {
        val fpNone = service.computeFingerprint(payload)
        val fpCalendar = service.computeFingerprint(payload.copy(calendarAttachment = realSnapshot()))
        assertTrue(fpNone.fullHex != fpCalendar.fullHex)
    }

    @Test
    fun `fingerprint same payload produces consistent short key`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload)
        assertEquals(64, fp1.fullHex.length)
        assertEquals("MANUAL_RICH:" + fp1.fullHex.take(32), fp1.shortKey)
        assertEquals(fp1.fullHex, fp2.fullHex)
        assertEquals(fp1.shortKey, fp2.shortKey)
    }

    @Test
    fun `fingerprint different subjects produce different keys`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(subject = "Different"))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint different contactId produces different keys`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(contactId = 2L))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint same contactId back restores original hash`() {
        val fp1 = service.computeFingerprint(payload)
        val fp2 = service.computeFingerprint(payload.copy(contactId = 2L))
        val fp3 = service.computeFingerprint(payload.copy(contactId = 1L))
        assertEquals(fp1.fullHex, fp3.fullHex)
        assertEquals(fp1.shortKey, fp3.shortKey)
        assertTrue(fp1.fullHex != fp2.fullHex)
    }

    @Test
    fun `fingerprint different QA order produces different keys`() {
        val fp1 = service.computeFingerprint(payload.copy(canonicalQaRuleIds = listOf(10L, 20L)))
        val fp2 = service.computeFingerprint(payload.copy(canonicalQaRuleIds = listOf(20L, 10L)))
        assertTrue(fp1.fullHex != fp2.fullHex)
        assertTrue(fp1.shortKey != fp2.shortKey)
    }

    @Test
    fun `fingerprint messageId is UUID-based`() {
        val fp = service.computeFingerprint(payload)
        assertTrue(fp.messageId.startsWith("<manual-rich-"))
        assertTrue(fp.messageId.endsWith("@weibo.com>"))
    }

    @Test
    fun `prepareAndClaim inserts and claims when status is PREPARED`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.PREPARED)
        Mockito.`when`(
            attemptRepository.claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
        ).thenReturn(1)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.CLAIMED, result.result)
    }

    @Test
    fun `prepareAndClaim returns DEDUP_SENT when status is SENT`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.SENT)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.DEDUP_SENT, result.result)
    }

    @Test
    fun `prepareAndClaim returns SAFE_RETRY_CLAIMED when status is FAILED_SAFE_TO_RETRY`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.FAILED_SAFE_TO_RETRY)
        Mockito.`when`(
            attemptRepository.claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
        ).thenReturn(1)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED, result.result)
    }

    @Test
    fun `prepareAndClaim returns IN_PROGRESS when status is DELIVERY_IN_PROGRESS`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.DELIVERY_IN_PROGRESS)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.IN_PROGRESS, result.result)
    }

    @Test
    fun `prepareAndClaim returns UNKNOWN when status is DELIVERY_UNKNOWN`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.DELIVERY_UNKNOWN)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.UNKNOWN, result.result)
    }

    @Test
    fun `prepareAndClaim returns PERMANENT_FAILED when status is FAILED`() {
        val fp = service.computeFingerprint(payload)
        stubFindForUpdate(fp, MailSendAttemptStatus.FAILED)
        val result = service.prepareAndClaim(payload)
        assertEquals(ManualReplySendAttemptService.ClaimResult.PERMANENT_FAILED, result.result)
    }

    @Test
    fun `prepareAndClaim rejects full hash collision`() {
        val fp = service.computeFingerprint(payload)
        val attempt = createAttempt(MailSendAttemptStatus.PREPARED, fp).copy(body = "other-hash")
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(attempt)
        assertThrows(IllegalArgumentException::class.java) { service.prepareAndClaim(payload) }
    }

    @Test
    fun `prepareAndClaim rejects recipient collision on short key`() {
        val fp = service.computeFingerprint(payload)
        val attempt = createAttempt(MailSendAttemptStatus.PREPARED, fp).copy(recipient = "other@test.com")
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(attempt)
        assertThrows(IllegalArgumentException::class.java) { service.prepareAndClaim(payload) }
    }

    @Test
    fun `finalizeSuccess creates mail record and writes QA associations`() {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(payload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 500L) }
        val mailRecordId = service.finalizeSuccess(payload, 1L, "<manual-rich-abc@weibo.com>")
        assertEquals(500L, mailRecordId)
        // fast-p 02 (I-1): 无 calendar 的普通人工富文本仍为 NULL（唯一 absence 形态）。
        val saved = capturedSavedRecord()
        assertNull(saved.calendarAttachmentJson)
        assertEquals("SENT", saved.sendStatus)
    }

    // 03 (I-6): mail_record_qa_rule 按 SendPayload.canonicalQaRuleIds 的 ordinal
    // 逐元素精确写入 —— 与 verified canonical facts 完全一致，无自动推荐事实、
    // 无漏项、无重复键（canonical union 已在 assembly selection 侧去重）。
    @Test
    fun `finalizeSuccess writes QA associations in exact payload ordinal order`() {
        val orderedPayload = payload.copy(canonicalQaRuleIds = listOf(10L, 20L, 30L))
        val attempt = createAttempt(
            MailSendAttemptStatus.DELIVERY_IN_PROGRESS,
            service.computeFingerprint(orderedPayload)
        )
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 500L) }

        val mailRecordId = service.finalizeSuccess(orderedPayload, 1L, "<manual-rich-abc@weibo.com>")

        assertEquals(500L, mailRecordId)
        val order = Mockito.inOrder(mailRecordQaRuleRepository)
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 10L, ordinal = 0
            )
        )
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 20L, ordinal = 1
            )
        )
        order.verify(mailRecordQaRuleRepository).save(
            com.weibo.talentintroduction.mail.domain.MailRecordQaRule(
                mailRecordId = 500L, qaRuleId = 30L, ordinal = 2
            )
        )
    }

    @Test
    fun `finalizeFailure creates failed mail record with error summary`() {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(payload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = 501L) }
        val mailRecordId = service.finalizeFailure(
            payload, 1L, "<manual-rich-abc@weibo.com>",
            MailSendAttemptStatus.FAILED, "SMTP error: 550 rejected"
        )
        assertEquals(501L, mailRecordId)
        // fast-p 02 (I-1): 无 calendar 的失败记录仍为 NULL；失败 sentAt=null（I-4）。
        val saved = capturedSavedRecord()
        assertNull(saved.calendarAttachmentJson)
        assertNull(saved.sentAt)
        assertEquals("FAILED", saved.sendStatus)
    }

    // ───────────────────────── fast-p 02：四支快照存档（I-1/I-4） ─────────────────────────

    private val savedMailRecordCaptor: org.mockito.ArgumentCaptor<MailRecord> =
        org.mockito.ArgumentCaptor.forClass(MailRecord::class.java)

    private fun capturedSavedRecord(): MailRecord {
        Mockito.verify(mailRecordRepository, Mockito.atLeastOnce()).save(savedMailRecordCaptor.capture())
        return savedMailRecordCaptor.allValues.last()
    }

    private fun stubSaveReturning(id: Long) {
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { it.getArgument<MailRecord>(0).copy(id = id) }
    }

    private fun stubFindByIdFor(p: ManualReplySendAttemptService.SendPayload) {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(p))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
    }

    /** 带真实 01 日历快照的 payload（与 golden 无日历 payload 其余字段一致）。 */
    private fun calendarPayload(snapshot: CalendarAttachmentSnapshot = realSnapshot()) =
        payload.copy(calendarAttachment = snapshot)

    private fun snapshotJson(snapshot: CalendarAttachmentSnapshot): String =
        CalendarAttachmentCodec.serialize(snapshot)

    @Test
    fun `finalizeSuccess new branch persists calendar snapshot json`() {
        val calendar = realSnapshot()
        val withCalendar = calendarPayload(calendar)
        stubFindByIdFor(withCalendar)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(600L)

        val id = service.finalizeSuccess(withCalendar, 1L, "<manual-rich-abc@weibo.com>")

        assertEquals(600L, id)
        val saved = capturedSavedRecord()
        assertEquals(snapshotJson(calendar), saved.calendarAttachmentJson)
        assertEquals("SENT", saved.sendStatus)
        // 快照 JSON 由 01 codec 往返仍为同一实例（I-3 同一快照对象）。
        assertEquals(calendar, CalendarAttachmentCodec.parseOrNull(saved.calendarAttachmentJson))
    }

    @Test
    fun `finalizeFailure new branch persists calendar snapshot json`() {
        val calendar = realSnapshot()
        val withCalendar = calendarPayload(calendar)
        stubFindByIdFor(withCalendar)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(601L)

        val id = service.finalizeFailure(
            withCalendar, 1L, "<manual-rich-abc@weibo.com>",
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, "SMTP timeout"
        )

        assertEquals(601L, id)
        val saved = capturedSavedRecord()
        assertEquals(snapshotJson(calendar), saved.calendarAttachmentJson)
        assertEquals("FAILED", saved.sendStatus)
        assertNull(saved.sentAt)
    }

    @Test
    fun `finalizeSuccess copy branch overwrites old snapshot with retry payload snapshot`() {
        val firstCalendar = realSnapshot()
        val retryCalendar = realSnapshot(meetingInput(zoomUrl = "https://zoom.us/j/87102801188?pwd=other"))
        val retryPayload = calendarPayload(retryCalendar)
        stubFindByIdFor(retryPayload)
        val failedRecord = MailRecord(
            id = 700L,
            expertContactId = retryPayload.contactId,
            direction = "OUTBOUND",
            mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = retryPayload.accountCode,
            messageId = "<manual-rich-first@weibo.com>",
            inReplyTo = retryPayload.inReplyTo,
            subject = retryPayload.subject,
            body = retryPayload.finalText,
            matchedQaRuleId = retryPayload.primaryRuleId,
            sendStatus = "FAILED",
            receivedAt = null,
            sentAt = null,
            errorSummary = "SMTP timeout",
            mailSendAttemptId = 1L,
            createdAt = LocalDateTime.now(),
            calendarAttachmentJson = snapshotJson(firstCalendar)
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(failedRecord)
        stubSaveReturning(700L)

        val id = service.finalizeSuccess(retryPayload, 1L, "<manual-rich-retry@weibo.com>")

        assertEquals(700L, id)
        val saved = capturedSavedRecord()
        // 同一 attempt 只收敛到同一 mail_record（I-4：重试成功不新建第二行）。
        assertEquals(700L, saved.id)
        assertEquals("SENT", saved.sendStatus)
        assertEquals(snapshotJson(retryCalendar), saved.calendarAttachmentJson)
        assertEquals(retryCalendar, CalendarAttachmentCodec.parseOrNull(saved.calendarAttachmentJson))
    }

    @Test
    fun `finalizeSuccess copy branch without calendar clears prior failed record snapshot`() {
        // 安全失败带附件 → 人工重试去掉附件（正文其他字段一致、fingerprint 不同）
        // 的 copy 成功：snapshot 必须显式清空，绝不沿用失败记录的旧附件（I-1）。
        val firstCalendar = realSnapshot()
        stubFindByIdFor(payload)
        val failedRecord = MailRecord(
            id = 701L,
            expertContactId = payload.contactId,
            direction = "OUTBOUND",
            mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = payload.accountCode,
            messageId = "<manual-rich-first@weibo.com>",
            inReplyTo = payload.inReplyTo,
            subject = payload.subject,
            body = payload.finalText,
            matchedQaRuleId = payload.primaryRuleId,
            sendStatus = "FAILED",
            receivedAt = null,
            sentAt = null,
            errorSummary = "SMTP timeout",
            mailSendAttemptId = 1L,
            createdAt = LocalDateTime.now(),
            calendarAttachmentJson = snapshotJson(firstCalendar)
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(failedRecord)
        stubSaveReturning(701L)

        val id = service.finalizeSuccess(payload, 1L, "<manual-rich-retry@weibo.com>")

        assertEquals(701L, id)
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertNull(saved.calendarAttachmentJson)
    }

    @Test
    fun `finalizeFailure copy branch clears snapshot when retry has no calendar`() {
        val firstCalendar = realSnapshot()
        stubFindByIdFor(payload)
        val failedRecord = MailRecord(
            id = 702L,
            expertContactId = payload.contactId,
            direction = "OUTBOUND",
            mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = payload.accountCode,
            messageId = "<manual-rich-first@weibo.com>",
            inReplyTo = payload.inReplyTo,
            subject = payload.subject,
            body = payload.finalText,
            matchedQaRuleId = payload.primaryRuleId,
            sendStatus = "FAILED",
            receivedAt = null,
            sentAt = null,
            errorSummary = "SMTP timeout",
            mailSendAttemptId = 1L,
            createdAt = LocalDateTime.now(),
            calendarAttachmentJson = snapshotJson(firstCalendar)
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(failedRecord)
        stubSaveReturning(702L)

        val id = service.finalizeFailure(
            payload, 1L, "<manual-rich-retry@weibo.com>",
            MailSendAttemptStatus.FAILED, "SMTP error: 550 rejected"
        )

        assertEquals(702L, id)
        val saved = capturedSavedRecord()
        assertEquals("FAILED", saved.sendStatus)
        assertNull(saved.sentAt)
        assertNull(saved.calendarAttachmentJson)
    }

    // fast-p 02 (I-4)：带 calendar 的认领路径 —— 安全失败→成功 copy 收敛、DEDUP_SENT、
    // UNKNOWN fail-closed 不重发/不伪成功。
    @Test
    fun `calendar attempt claims safe retry and finalize success on the same attempt id`() {
        val withCalendar = calendarPayload()
        val fp = service.computeFingerprint(withCalendar)
        Mockito.`when`(
            attemptRepository.findByOrcidIdAndMailTypeForUpdate(anyString(), anyString())
        ).thenReturn(
            createAttempt(MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, fp)
        )
        Mockito.`when`(
            attemptRepository.claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
        ).thenReturn(1)

        val claimed = service.prepareAndClaim(withCalendar)
        assertEquals(ManualReplySendAttemptService.ClaimResult.SAFE_RETRY_CLAIMED, claimed.result)
        assertEquals(1L, claimed.attemptId)

        // 失败时（FAILED_SAFE_TO_RETRY）已按旧 payload 建过 record；重试成功后
        // finalizeSuccess 走 copy 分支更新同一 record 为 SENT（安全失败可认领）。
        val failedRecord = MailRecord(
            id = 703L,
            expertContactId = withCalendar.contactId,
            direction = "OUTBOUND",
            mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = withCalendar.accountCode,
            messageId = claimed.messageId,
            inReplyTo = withCalendar.inReplyTo,
            subject = withCalendar.subject,
            body = withCalendar.finalText,
            matchedQaRuleId = withCalendar.primaryRuleId,
            sendStatus = "FAILED",
            receivedAt = null,
            sentAt = null,
            errorSummary = "SMTP timeout",
            mailSendAttemptId = 1L,
            createdAt = LocalDateTime.now(),
            calendarAttachmentJson = snapshotJson(withCalendar.calendarAttachment!!)
        )
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(
            Optional.of(createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, fp))
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(failedRecord)
        stubSaveReturning(703L)

        val mailRecordId = service.finalizeSuccess(withCalendar, 1L, claimed.messageId)
        assertEquals(703L, mailRecordId)
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertEquals(snapshotJson(withCalendar.calendarAttachment!!), saved.calendarAttachmentJson)
    }

    @Test
    fun `calendar attempt dedup sent returns old record without resend`() {
        val withCalendar = calendarPayload()
        val fp = service.computeFingerprint(withCalendar)
        stubFindForUpdate(fp, MailSendAttemptStatus.SENT)
        val result = service.prepareAndClaim(withCalendar)
        assertEquals(ManualReplySendAttemptService.ClaimResult.DEDUP_SENT, result.result)
        assertEquals(1L, result.attemptId)
    }

    @Test
    fun `calendar attempt unknown fails closed and never fakes success`() {
        val withCalendar = calendarPayload()
        val fp = service.computeFingerprint(withCalendar)
        stubFindForUpdate(fp, MailSendAttemptStatus.DELIVERY_UNKNOWN)
        val result = service.prepareAndClaim(withCalendar)
        assertEquals(ManualReplySendAttemptService.ClaimResult.UNKNOWN, result.result)
        // UNKNOWN 分支不 claim（不 CAS 到 DELIVERY_IN_PROGRESS）—— 不重发、不伪成功。
        Mockito.verify(attemptRepository, Mockito.never())
            .claimStatus(Mockito.anyLong(), anyString(), anyString(), Mockito.any())
    }

    private fun delivered() = DeliveredMail(messageId = "out-1", status = "SENT")

    private fun inboundRecord() = com.weibo.talentintroduction.mail.domain.InboundMailProcessing(
        id = 100L, senderAccountCode = "sender-1", imapUid = 1L,
        messageId = "in-1", fromEmail = "expert@test.com",
        subject = "Test", body = "Hello",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
        expertContactId = 1L
    )

    private fun diagnostics() = TrustReplyDiagnostics(
        schemaVersion = TrustReplyDiagnostics.SCHEMA_VERSION,
        flags = listOf(
            TrustReplyDiagnosticFlag.MANUAL_FACT_SELECTED,
            TrustReplyDiagnosticFlag.INTENT_MISMATCH
        ),
        requestSnapshots = listOf(
            TrustReplyRequestDiagnostic(
                requestKey = "req-1",
                status = RequestGroundingStatus.GROUNDED.name,
                handling = TrustReplyItemHandling.ANSWER_WITH_EVIDENCE.name,
                detectedIntentKeys = listOf("INTENT_A"),
                unrecognizedAskCount = 0,
                manualFactRuleIds = listOf(10L),
                intentMatchedFactRuleIds = listOf(10L),
                intentMismatchFactRuleIds = listOf(20L),
                flags = listOf(
                    TrustReplyDiagnosticFlag.MANUAL_FACT_SELECTED,
                    TrustReplyDiagnosticFlag.INTENT_MISMATCH
                ),
                factIdsTruncated = false,
                intentKeysTruncated = false
            )
        ),
        requestTotal = 1,
        requestTruncated = false
    )

    // 04 (I-1): 有 verified 诊断时，既有 SEND_MANUAL_COMPOSED_REPLY after map 追加
    // trustReplyDiagnostics；既有字段（mailRecordId/canonicalFactIds/subject/...）逐字保留。
    @Test
    fun `recordSendAudit appends trust reply diagnostics to composed reply after map`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note",
            trustReplyDiagnostics = diagnostics()
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_COMPOSED_REPLY, invocation.arguments[2])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals(listOf(10L), after["canonicalFactIds"])
        assertEquals(listOf(10L), after["serverSuggestedFactIds"])
        assertEquals("Re: Test", after["subject"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertEquals(false, after["edited"])
        assertEquals(diagnostics(), after["trustReplyDiagnostics"])
    }

    // 04 (阶段 3): 「工作台无事实但完成发送」仍可把 unrecognized/unsupported 诊断
    // 记到 SEND_MANUAL_RICH_REPLY 分支，rich 分支既有字段不变。
    @Test
    fun `recordSendAudit appends trust reply diagnostics to rich reply after map`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = emptyList(), carriesQa = false,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = emptyList(),
            edited = null, note = "note",
            trustReplyDiagnostics = diagnostics()
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_RICH_REPLY, invocation.arguments[2])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(diagnostics(), after["trustReplyDiagnostics"])
        assertEquals(500L, after["mailRecordId"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertFalse(after.containsKey("canonicalFactIds"))
    }

    // 04 (I-7): 无诊断（纯人工 rich reply / legacy QA 发送）时 after payload
    // 逐字保留，不出现 trustReplyDiagnostics 键。
    @Test
    fun `recordSendAudit leaves after payload verbatim when diagnostics absent`() {
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note"
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals(listOf(10L), after["canonicalFactIds"])
        assertEquals("Re: Test", after["subject"])
        assertFalse(after.containsKey("trustReplyDiagnostics"))
    }

    // 04 (I-6): LIVE 审计保持 after-commit best-effort —— 写失败只 warn，不抛异常、
    // 不反转已发送邮件。
    @Test
    fun `recordSendAudit best effort does not throw on failure`() {
        val actionType = com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_COMPOSED_REPLY
        val before = mapOf("inboundProcessingId" to 100L)
        val after = mapOf(
            "mailRecordId" to 500L,
            "canonicalFactIds" to listOf(10L),
            "serverSuggestedFactIds" to listOf(10L),
            "qaRuleIds" to listOf(10L),
            "suggestedRuleIds" to listOf(10L),
            "draftGenerationState" to null,
            "edited" to false,
            "sendStatus" to "SENT",
            "subject" to "Re: Test",
            "bodyPreviewText" to "Hello"
        )
        Mockito.doThrow(RuntimeException("audit down"))
            .`when`(operatorActionLogService)
            .record("INBOUND_MAIL_PROCESSING", 100L, actionType, 1L, 100L, before, after, "op", "note", null)
        service.recordSendAudit(
            inboundProcessingId = 100L, contactId = 1L, mailRecordId = 500L,
            canonicalFactIds = listOf(10L), carriesQa = true,
            delivered = delivered(), sendSubject = "Re: Test",
            bodyPreviewText = "Hello", operatorName = "op",
            inboundRecord = inboundRecord(), serverSuggestedFactIds = listOf(10L),
            edited = false, note = "note"
        )
        Mockito.verify(operatorActionLogService, Mockito.times(1))
            .record("INBOUND_MAIL_PROCESSING", 100L, actionType, 1L, 100L, before, after, "op", "note", null)
    }
}

