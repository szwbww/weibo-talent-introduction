package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttempt
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
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
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class ManualReplySendAttemptServiceTest {
    private val attemptRepository = Mockito.mock(MailSendAttemptRepository::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val meetingCalendarService = Mockito.mock(MeetingCalendarService::class.java)
    private val service = ManualReplySendAttemptService(
        attemptRepository, mailRecordRepository, mailRecordQaRuleRepository, operatorActionLogService,
        meetingCalendarService
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

    /**
     * 01 生成器协作者 stub：正文由通用 `MEETING_INVITATION` 模板链路渲染。
     * 变量取服务端事实、正文只拼装两个会议值，保证 ICS 字节/语义可复算。
     */
    private fun mockMeetingInvitationBody(
        templateService: MailComposeTemplateService,
        variableService: MailVariableService
    ) {
        Mockito.`when`(variableService.resolveExpertProfileFor(anyValue(meetingContact()))).thenReturn(null)
        Mockito.`when`(
            variableService.buildVariables(
                anyValue(meetingAccount()),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyBoolean(),
                Mockito.any()
            )
        ).thenReturn(MEETING_VARIABLES)
        Mockito.`when`(
            templateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap()),
                Mockito.anyInt()
            )
        ).thenAnswer { invocation ->
            val variables = invocation.getArgument<Map<String, String>>(1)
            com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult(
                subject = "Meeting invitation",
                body = "Dear " + variables["expertName"].orEmpty() + ",\n\n" +
                    "We have noted the meeting time as " + variables["meeting_time"].orEmpty() + ".\n\n" +
                    "Please join the meeting using the following link:\n\n" +
                    variables["zoom_url"].orEmpty() + "\n\n" +
                    "Best regards,\n" + variables["senderName"].orEmpty() + ", " +
                    variables["senderTitle"].orEmpty()
            )
        }
    }

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    /** Mockito.eq() 对 Kotlin 非空参数会返回 null；传真实默认值实例占位。 */
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value


    /** Matcher 占位：resolveExpertProfileFor 参数非空。 */
    private fun meetingContact() = ExpertContact(
        id = 1L,
        campaignId = 1,
        orcidId = "0000-0001-2345-6789",
        expertEmail = "expert@test.com",
        expertName = "Professor Basdogan",
        currentStatus = "WAITING_MEETING_CONFIRMATION"
    )

    companion object {
        private val MEETING_VARIABLES = mapOf(
            "senderName" to "LuKai",
            "senderTitle" to "Customer Care Officer",
            "teamName" to "Qingfei Tech Talent Team",
            "countryName" to "China",
            "expertName" to "Professor Basdogan",
            "expertFamilyName" to "Basdogan"
        )
    }

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
            zoneId = "Europe/Istanbul",
            startLocal = "2026-09-11T10:00",
            endLocal = "2026-09-11T10:30",
            zoomUrl = zoomUrl,
            generatedAt = generatedAt
        )

    /** 01 真实生成器产物 → 快照：stub 只读身份/通用模板，preview 生成真实 ICS/sha256/semanticSha256。 */
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
        val variableService = Mockito.mock(MailVariableService::class.java)
        mockMeetingInvitationBody(templateService, variableService)
        val generator = MeetingConfirmationService(
            inboundRepo, contactRepo, accountService, templateService, MailContentService(),
            variableService
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

    // ─────────────────────── fast-p 05：通用附件发送身份（I-2） ───────────────────────

    /** 04 快照形态（真实 sha256/字节数口径）；id 是上传 UUID，不参与发送身份。 */
    private fun attachmentSnapshot(
        filename: String,
        contentType: String,
        bytes: ByteArray,
        id: String = UUID.randomUUID().toString()
    ) = OutboundAttachmentSnapshot(
        schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
        id = id,
        filename = filename,
        contentType = contentType,
        byteLength = bytes.size.toLong(),
        sha256 = outboundSha256Hex(bytes)
    )

    private fun txtBytes() = "附件一：中文说明\n第二行".toByteArray(Charsets.UTF_8)

    private fun zipBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("材料/说明.txt"))
            zip.write("压缩包内正文".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("report.bin"))
            zip.write(ByteArray(512) { (it % 251).toByte() })
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `attachment fingerprint ignores upload id and keeps original no-attachment golden`() {
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val zip = attachmentSnapshot("材料.zip", "application/zip", zipBytes())
        val fp = service.computeFingerprint(payload.copy(outboundAttachments = listOf(txt, zip)))

        // 同字节同文件名重传（新上传 UUID）得到同一发送身份。
        val reuploaded = service.computeFingerprint(
            payload.copy(outboundAttachments = listOf(txt.copy(id = UUID.randomUUID().toString()), zip))
        )
        assertEquals(fp.fullHex, reuploaded.fullHex)
        assertEquals(fp.shortKey, reuploaded.shortKey)
        assertEquals("MANUAL_RICH:" + fp.fullHex.take(32), fp.shortKey)

        // 无附件（含显式空列表）仍是 01 冻结 golden：空列表不追加任何字节。
        assertEquals(
            "fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda",
            service.computeFingerprint(payload.copy(outboundAttachments = emptyList())).fullHex
        )
        assertTrue(fp.fullHex != service.computeFingerprint(payload).fullHex)
    }

    @Test
    fun `attachment fingerprint changes with bytes filename mime and order`() {
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val zip = attachmentSnapshot("材料.zip", "application/zip", zipBytes())
        val base = service.computeFingerprint(payload.copy(outboundAttachments = listOf(txt, zip)))

        val otherBytes = txtBytes() + "改一个字节".toByteArray(Charsets.UTF_8)
        val changedBytes = txt.copy(byteLength = otherBytes.size.toLong(), sha256 = outboundSha256Hex(otherBytes))
        assertTrue(base.fullHex != service.computeFingerprint(
            payload.copy(outboundAttachments = listOf(changedBytes, zip))
        ).fullHex, "字节变化必须改变发送身份")

        assertTrue(base.fullHex != service.computeFingerprint(
            payload.copy(outboundAttachments = listOf(txt.copy(filename = "说明-改名.txt"), zip))
        ).fullHex, "文件名变化必须改变发送身份")

        assertTrue(base.fullHex != service.computeFingerprint(
            payload.copy(outboundAttachments = listOf(txt.copy(contentType = "text/markdown"), zip))
        ).fullHex, "MIME 变化必须改变发送身份")

        assertTrue(base.fullHex != service.computeFingerprint(
            payload.copy(outboundAttachments = listOf(zip, txt))
        ).fullHex, "选取顺序变化必须改变发送身份")
    }

    @Test
    fun `attachment segment is independent of the calendar segment`() {
        val calendar = realSnapshot()
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val fpNone = service.computeFingerprint(payload)
        val fpCalendar = service.computeFingerprint(payload.copy(calendarAttachment = calendar))
        val fpAttachments = service.computeFingerprint(payload.copy(outboundAttachments = listOf(txt)))
        val fpBoth = service.computeFingerprint(
            payload.copy(calendarAttachment = calendar, outboundAttachments = listOf(txt))
        )

        assertEquals(4, listOf(fpNone.fullHex, fpCalendar.fullHex, fpAttachments.fullHex, fpBoth.fullHex).toSet().size)
        // calendar 段仍按 02 的顺序由 semanticSha256 决定：仅换日历语义即换身份（附件不变）。
        val otherCalendar = realSnapshot(meetingInput(zoomUrl = "https://zoom.us/j/87102801188?pwd=other"))
        assertTrue(fpBoth.fullHex != service.computeFingerprint(
            payload.copy(calendarAttachment = otherCalendar, outboundAttachments = listOf(txt))
        ).fullHex)
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

    // ──────────────── fast-p 05：四分支通用附件快照存档（I-1/I-4） ────────────────

    private fun attachmentPayload(vararg files: OutboundAttachmentSnapshot) =
        payload.copy(outboundAttachments = files.toList())

    private fun attachmentsJson(vararg files: OutboundAttachmentSnapshot) =
        OutboundAttachmentSnapshotCodec.serialize(files.toList())

    /** 上一次尝试（成功或失败）留下的附件存档记录。 */
    private fun priorFailedRecord(
        id: Long,
        snapshotJsonValue: String?,
        calendarJsonValue: String? = null
    ) = MailRecord(
        id = id,
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
        calendarAttachmentJson = calendarJsonValue,
        outboundAttachmentsJson = snapshotJsonValue
    )

    @Test
    fun `finalizeSuccess new branch persists ordered attachment snapshot json`() {
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val zip = attachmentSnapshot("材料.zip", "application/zip", zipBytes())
        val withAttachments = attachmentPayload(txt, zip)
        stubFindByIdFor(withAttachments)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(600L)

        val id = service.finalizeSuccess(withAttachments, 1L, "<manual-rich-att@weibo.com>")

        assertEquals(600L, id)
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertEquals(attachmentsJson(txt, zip), saved.outboundAttachmentsJson)
        assertEquals(listOf(txt, zip), OutboundAttachmentSnapshotCodec.parseOrThrow(saved.outboundAttachmentsJson))
        assertNull(saved.calendarAttachmentJson, "无会议日历仍是 NULL，不被通用附件影响")
    }

    @Test
    fun `finalizeSuccess copy branch overwrites previous attachment snapshot with this attempt`() {
        val previous = attachmentSnapshot("旧.txt", "text/plain", "上一次的附件".toByteArray(Charsets.UTF_8))
        val zip = attachmentSnapshot("材料.zip", "application/zip", zipBytes())
        val retryPayload = attachmentPayload(zip)
        stubFindByIdFor(retryPayload)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L))
            .thenReturn(priorFailedRecord(700L, attachmentsJson(previous)))
        stubSaveReturning(700L)

        val id = service.finalizeSuccess(retryPayload, 1L, "<manual-rich-retry@weibo.com>")

        assertEquals(700L, id)
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertEquals(attachmentsJson(zip), saved.outboundAttachmentsJson)
        assertEquals(listOf(zip), OutboundAttachmentSnapshotCodec.parseOrThrow(saved.outboundAttachmentsJson))
    }

    @Test
    fun `finalizeSuccess copy branch without attachments clears the failed record snapshot`() {
        // 安全失败带附件 → 人工重试去掉附件（正文其余字段一致）的 copy 成功：必须显式
        // 清空为 NULL，绝不沿用失败记录的旧附件（I-1）。
        val previous = attachmentSnapshot("旧.txt", "text/plain", "上一次的附件".toByteArray(Charsets.UTF_8))
        stubFindByIdFor(payload)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L))
            .thenReturn(priorFailedRecord(701L, attachmentsJson(previous)))
        stubSaveReturning(701L)

        val id = service.finalizeSuccess(payload, 1L, "<manual-rich-retry@weibo.com>")

        assertEquals(701L, id)
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertNull(saved.outboundAttachmentsJson)
    }

    @Test
    fun `finalizeFailure new branch persists ordered attachment snapshot json`() {
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val zip = attachmentSnapshot("材料.zip", "application/zip", zipBytes())
        val withAttachments = attachmentPayload(txt, zip)
        stubFindByIdFor(withAttachments)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(602L)

        val id = service.finalizeFailure(
            withAttachments, 1L, "<manual-rich-safe-fail@weibo.com>",
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, "SMTP error: 421 try later"
        )

        assertEquals(602L, id)
        val saved = capturedSavedRecord()
        assertEquals("FAILED", saved.sendStatus)
        assertNull(saved.sentAt)
        assertEquals(attachmentsJson(txt, zip), saved.outboundAttachmentsJson)
        assertEquals(listOf(txt, zip), OutboundAttachmentSnapshotCodec.parseOrThrow(saved.outboundAttachmentsJson))
    }

    @Test
    fun `finalizeFailure copy branch without attachments clears the previous snapshot`() {
        val previous = attachmentSnapshot("旧.txt", "text/plain", "上一次的附件".toByteArray(Charsets.UTF_8))
        stubFindByIdFor(payload)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L))
            .thenReturn(priorFailedRecord(702L, attachmentsJson(previous)))
        stubSaveReturning(702L)

        val id = service.finalizeFailure(
            payload, 1L, "<manual-rich-retry@weibo.com>",
            MailSendAttemptStatus.FAILED, "SMTP error: 550 rejected"
        )

        assertEquals(702L, id)
        val saved = capturedSavedRecord()
        assertEquals("FAILED", saved.sendStatus)
        assertNull(saved.outboundAttachmentsJson)
    }

    @Test
    fun `plain send persists explicit null and never an empty array or blank string`() {
        stubFindByIdFor(payload)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(603L)

        service.finalizeSuccess(payload, 1L, "<manual-rich-plain@weibo.com>")

        val saved = capturedSavedRecord()
        assertNull(saved.outboundAttachmentsJson)
        assertFalse(saved.outboundAttachmentsJson == "[]")
        assertFalse(saved.outboundAttachmentsJson == "")
    }

    @Test
    fun `attachment only send never touches the scheduling service`() {
        // I-4：只附普通文件（无 calendar/meetingEvent）不触发排期，也不产生会议。
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val withAttachments = attachmentPayload(txt)
        stubFindByIdFor(withAttachments)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(604L)

        val id = service.finalizeSuccess(withAttachments, 1L, "<manual-rich-att-only@weibo.com>")

        assertEquals(604L, id)
        Mockito.verify(meetingCalendarService, Mockito.never())
            .createFromSentMail(anyValue(placeholderRecord()), anyValue(meetingEventInput()))
        val saved = capturedSavedRecord()
        assertEquals("SENT", saved.sendStatus)
        assertNull(saved.calendarAttachmentJson)
        assertEquals(attachmentsJson(txt), saved.outboundAttachmentsJson)
    }

    @Test
    fun `retry with attachments after a plain failed attempt keeps both calendar and attachments`() {
        val calendar = realSnapshot()
        val txt = attachmentSnapshot("说明.txt", "text/plain", txtBytes())
        val retryPayload = payload.copy(calendarAttachment = calendar, outboundAttachments = listOf(txt))
        stubFindByIdFor(retryPayload)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L))
            .thenReturn(priorFailedRecord(704L, null))
        stubSaveReturning(704L)

        val id = service.finalizeSuccess(retryPayload, 1L, "<manual-rich-both@weibo.com>")

        assertEquals(704L, id)
        val saved = capturedSavedRecord()
        assertEquals(snapshotJson(calendar), saved.calendarAttachmentJson)
        assertEquals(attachmentsJson(txt), saved.outboundAttachmentsJson)
    }

    // ───────────────────────── fast-p 02：成功事务内创建排期（I-1/I-2） ─────────────────────────

    /** 同一 validateAndBuild 产物派生的结构化排期输入（生产由 PendingMailOperationService 装配）。 */
    private fun meetingEventInput() = MeetingCalendarInput(
        startUtc = Instant.parse("2026-09-18T02:00:00Z"),
        endUtc = Instant.parse("2026-09-18T02:30:00Z"),
        meetingLink = "https://zoom.us/j/87102801187"
    )

    private fun calendarPayloadWithEvent(
        snapshot: CalendarAttachmentSnapshot = realSnapshot(),
        event: MeetingCalendarInput = meetingEventInput()
    ) = payload.copy(calendarAttachment = snapshot, meetingEvent = event)

    /** Matcher 占位：Kotlin 非空参数不接受 null，`any()` 之前先给一个真实实例。 */
    private fun placeholderRecord() = MailRecord(
        id = 1L,
        expertContactId = 1L,
        direction = "OUTBOUND",
        mailType = "MANUAL_RICH_REPLY",
        messageId = "<placeholder@weibo.com>",
        inReplyTo = null,
        subject = "Meeting",
        body = "body",
        matchedQaRuleId = null,
        sendStatus = "SENT",
        receivedAt = null,
        sentAt = LocalDateTime.now()
    )

    @Test
    fun `finalizeSuccess creates the schedule from the persisted sent record before marking the attempt sent`() {
        val event = meetingEventInput()
        val withEvent = calendarPayloadWithEvent(event = event)
        stubFindByIdFor(withEvent)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(600L)

        val id = service.finalizeSuccess(withEvent, 1L, "<manual-rich-abc@weibo.com>")
        assertEquals(600L, id)

        // Kotlin 非空参数不得传 null matcher：先从调用记录取真实参数，再用真实值做顺序校验。
        val calendarCall = Mockito.mockingDetails(meetingCalendarService).invocations
            .single { it.method.name == "createFromSentMail" }
        val persisted = calendarCall.arguments[0] as MailRecord
        val passedEvent = calendarCall.arguments[1] as MeetingCalendarInput
        assertEquals(600L, persisted.id, "排期来源必须是真实落库取得 id 的 SENT 记录")
        assertEquals("SENT", persisted.sendStatus)
        assertEquals("OUTBOUND", persisted.direction)
        assertEquals("MANUAL_RICH_REPLY", persisted.mailType)
        assertNotNull(persisted.calendarAttachmentJson)
        assertEquals(event, passedEvent, "排期输入逐字段取自 payload（不重新取时间/链接）")

        val order = Mockito.inOrder(mailRecordRepository, meetingCalendarService, attemptRepository)
        // save 的实参是 mock 保存前的记录（返回的是补 id 后的副本）→ 用占位 matcher 匹配，
        // 顺序断言仍绑定真实的 meetingCalendarService 调用参数。
        order.verify(mailRecordRepository).save(anyValue(placeholderRecord()))
        order.verify(meetingCalendarService).createFromSentMail(persisted, event)
        order.verify(attemptRepository).updateStatusAndError(
            Mockito.anyLong(), anyString(), Mockito.any(), anyValue(LocalDateTime.now())
        )
    }

    @Test
    fun `finalizeSuccess without meeting event never touches the calendar`() {
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(payload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(600L)

        service.finalizeSuccess(payload, 1L, "<manual-rich-abc@weibo.com>")

        Mockito.verifyNoInteractions(meetingCalendarService)
    }

    @Test
    fun `finalizeFailure never creates a schedule even when the payload carries a meeting`() {
        val withEvent = calendarPayloadWithEvent()
        stubFindByIdFor(withEvent)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(601L)

        service.finalizeFailure(
            withEvent, 1L, "<manual-rich-abc@weibo.com>",
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, "SMTP timeout"
        )

        Mockito.verifyNoInteractions(meetingCalendarService)
    }

    @Test
    fun `finalizeSuccess propagates a schedule write failure instead of marking the attempt sent`() {
        val withEvent = calendarPayloadWithEvent()
        stubFindByIdFor(withEvent)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        stubSaveReturning(600L)
        Mockito.doThrow(IllegalStateException("calendar down"))
            .`when`(meetingCalendarService)
            .createFromSentMail(anyValue(placeholderRecord()), anyValue(meetingEventInput()))

        val ex = assertThrows(IllegalStateException::class.java) {
            service.finalizeSuccess(withEvent, 1L, "<manual-rich-abc@weibo.com>")
        }

        assertEquals("calendar down", ex.message)
        Mockito.verify(attemptRepository, Mockito.never())
            .updateStatusAndError(Mockito.anyLong(), anyString(), Mockito.any(), anyValue(LocalDateTime.now()))
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

    // =====================================================================
    // 会话（无来信）回信：requestId 幂等键 / 锚点指纹 / 收敛读 / 审计（T1，I-3~I-7）
    // =====================================================================

    // I-5: 固定 inbound payload 的 fullHex/shortKey 回归值（部署前后同一来信/正文的
    // 幂等键不得漂移 —— 字段顺序/首段数字 ID/算法不变）。
    @Test
    fun `inbound fingerprint stays byte identical to the pinned regression values`() {
        val fp = service.computeFingerprint(payload)
        assertEquals(64, fp.fullHex.length)
        assertEquals("fa838dbceec46871ec1eae26e9ba4666d6f1ac0fda1bd36f872110efd38becda", fp.fullHex)
        assertEquals("MANUAL_RICH:fa838dbceec46871ec1eae26e9ba4666", fp.shortKey)
        assertTrue(fp.shortKey.length <= 50, "mail_type VARCHAR(50) 上限")
    }

    @Test
    fun `conversation request id canonicalizes to lowercase uuid and fixed 50 char short key`() {
        val raw = "B5C98F60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val canonical = service.canonicalConversationRequestId(raw)
        assertEquals("b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f", canonical)
        val key1 = service.conversationRequestShortKey(raw)
        val key2 = service.conversationRequestShortKey(canonical)
        assertEquals(50, key1.length, "MANUAL_RICH_REQUEST:20 字符 + 30 hex = 50（VARCHAR(50)）")
        assertTrue(key1.startsWith("MANUAL_RICH_REQUEST:"))
        assertEquals(key1, key2, "大小写/空白规范化后同一短键")
        assertFalse(key1.startsWith("MANUAL_RICH:"), "与来信前缀隔离")
    }

    @Test
    fun `different request ids produce different short keys and invalid ids fail immediately`() {
        val keyA = service.conversationRequestShortKey("00000000-0000-4000-8000-000000000001")
        val keyB = service.conversationRequestShortKey("00000000-0000-4000-8000-000000000002")
        assertTrue(keyA != keyB)
        assertThrows(IllegalArgumentException::class.java) {
            service.canonicalConversationRequestId("not-a-uuid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.conversationRequestShortKey("not-a-uuid")
        }
    }

    private fun outboundPayload(requestId: String, anchorRecordId: Long = 11L) = payload.copy(
        inboundProcessingId = null,
        sourceAnchor = "${ManualReplySendAttemptService.SOURCE_ANCHOR_PREFIX}$anchorRecordId",
        idempotencyRequestId = requestId
    )

    @Test
    fun `outbound fingerprint requires exactly one of inbound id or anchor plus request id`() {
        val withoutRequest = payload.copy(inboundProcessingId = null, sourceAnchor = "MAIL_RECORD:11")
        assertThrows(IllegalArgumentException::class.java) { service.computeFingerprint(withoutRequest) }
        val withoutAnchor = payload.copy(inboundProcessingId = null, idempotencyRequestId = "00000000-0000-4000-8000-000000000001")
        assertThrows(IllegalArgumentException::class.java) { service.computeFingerprint(withoutAnchor) }
        assertThrows(IllegalArgumentException::class.java) {
            service.computeFingerprint(payload.copy(sourceAnchor = "MAIL_RECORD:11"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.computeFingerprint(payload.copy(idempotencyRequestId = "00000000-0000-4000-8000-000000000001"))
        }
    }

    @Test
    fun `outbound short key is request derived while anchor enters the full hash`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val fp1 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 11L))
        val fp2 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 12L))
        assertEquals(service.conversationRequestShortKey(requestId), fp1.shortKey, "同一 requestId 短键固定")
        assertEquals(fp1.shortKey, fp2.shortKey)
        assertTrue(fp1.fullHex != fp2.fullHex, "锚点不同进入完整 hash")
        val fp3 = service.computeFingerprint(outboundPayload(requestId, anchorRecordId = 11L))
        assertEquals(fp1.fullHex, fp3.fullHex, "同锚点/同正文/同 requestId → 同 fullHex")
        assertTrue(fp1.messageId.startsWith("<manual-rich-"))
        assertEquals(64, fp1.fullHex.length)
    }

    @Test
    fun `findCompletedByRequestId returns completed reply only when attempt SENT and record exists`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val fp = service.computeFingerprint(outboundPayload(requestId))
        val attempt = createAttempt(MailSendAttemptStatus.SENT, fp)
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(attempt)
        val completedRecord = MailRecord(
            expertContactId = 1L, direction = "OUTBOUND", mailType = "MANUAL_RICH_REPLY",
            senderAccountCode = "sender-1", messageId = "<manual-rich-x@weibo.com>",
            inReplyTo = "anchor-mid", subject = "Re: Test", body = "sent",
            matchedQaRuleId = null,
            sendStatus = "SENT", receivedAt = null, sentAt = LocalDateTime.now()
        )
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(completedRecord)

        val result = service.findCompletedByRequestId(payload.orcidId, requestId)
        org.junit.jupiter.api.Assertions.assertNotNull(result)
        assertEquals(1L, result!!.attemptId)
        assertEquals("sender-1", result.attemptAccountCode)
        assertEquals("anchor-mid", result.mailRecord.inReplyTo)
        // 非 SENT 状态一律返回空（交给 claim/fail-closed 逻辑），即使结果行存在
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(createAttempt(MailSendAttemptStatus.DELIVERY_UNKNOWN, fp))
        assertTrue(service.findCompletedByRequestId(payload.orcidId, requestId) == null)
        // attempt 存在但结果行缺失 → 空
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(payload.orcidId, fp.shortKey))
            .thenReturn(attempt)
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        assertTrue(service.findCompletedByRequestId(payload.orcidId, requestId) == null)
        // 无 attempt → 空
        Mockito.`when`(attemptRepository.findByOrcidIdAndMailType(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(null)
        assertTrue(service.findCompletedByRequestId(payload.orcidId, "00000000-0000-4000-8000-000000000001") == null)
    }

    @Test
    fun `finalizeSuccess persists conversation reply with anchor inReplyTo and no inbound source`() {
        val requestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"
        val outPayload = payload.copy(
            inboundProcessingId = null,
            inReplyTo = "anchor-mid",
            canonicalQaRuleIds = emptyList(),
            sourceAnchor = "MAIL_RECORD:11",
            idempotencyRequestId = requestId
        )
        val attempt = createAttempt(MailSendAttemptStatus.DELIVERY_IN_PROGRESS, service.computeFingerprint(outPayload))
        Mockito.`when`(attemptRepository.findById(1L)).thenReturn(Optional.of(attempt))
        Mockito.`when`(mailRecordRepository.findByMailSendAttemptId(1L)).thenReturn(null)
        var saved: MailRecord? = null
        Mockito.`when`(mailRecordRepository.save(Mockito.any(MailRecord::class.java)))
            .thenAnswer { invocation ->
                saved = invocation.getArgument<MailRecord>(0)
                saved!!.copy(id = 500L)
            }
        val mailRecordId = service.finalizeSuccess(outPayload, 1L, "<manual-rich-abc@weibo.com>")
        assertEquals(500L, mailRecordId)
        val record = requireNotNull(saved)
        assertEquals("OUTBOUND", record.direction)
        assertEquals("MANUAL_RICH_REPLY", record.mailType)
        assertEquals("sender-1", record.senderAccountCode)
        assertEquals("<manual-rich-abc@weibo.com>", record.messageId)
        assertEquals("anchor-mid", record.inReplyTo)
        assertEquals("SENT", record.sendStatus)
        assertTrue(record.sourceInboundId == null)
        assertEquals(com.weibo.talentintroduction.mail.domain.TriggeredBy.OPERATOR, record.triggeredBy)
        assertEquals(1L, record.expertContactId)
    }

    @Test
    fun `recordConversationSendAudit writes expert contact target with anchor before map`() {
        service.recordConversationSendAudit(
            contactId = 1L,
            anchorMailRecordId = 77L,
            mailRecordId = 500L,
            delivered = delivered(),
            sendSubject = "Re: Intro",
            bodyPreviewText = "Hello",
            operatorName = "op",
            note = "conversation send"
        )
        val invocation = Mockito.mockingDetails(operatorActionLogService).invocations
            .single { it.method.name == "record" }
        assertEquals("EXPERT_CONTACT", invocation.arguments[0])
        assertEquals(1L, invocation.arguments[1])
        assertEquals(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_RICH_REPLY, invocation.arguments[2])
        assertEquals(1L, invocation.arguments[3])
        assertTrue(invocation.arguments[4] == null, "inbound_processing_id 恒 null")
        val before = invocation.arguments[5] as Map<*, *>
        assertEquals(77L, before["anchorMailRecordId"])
        val after = invocation.arguments[6] as Map<*, *>
        assertEquals(500L, after["mailRecordId"])
        assertEquals("SENT", after["sendStatus"])
        assertEquals("Re: Intro", after["subject"])
        assertEquals("Hello", after["bodyPreviewText"])
        assertFalse(after.containsKey("canonicalFactIds"))
    }
}

