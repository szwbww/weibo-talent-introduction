package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyHighRiskClaimValidator
import com.weibo.talentintroduction.llm.service.QaFactSelectionService
import com.weibo.talentintroduction.llm.service.ResolvedQaRules
import com.weibo.talentintroduction.llm.service.ResolvedTrustReplySource
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleRequest
import com.weibo.talentintroduction.llm.service.TrustReplyAssembleResponse
import com.weibo.talentintroduction.llm.service.TrustReplyItemGenerationKind
import com.weibo.talentintroduction.llm.service.TrustReplyItemHandling
import com.weibo.talentintroduction.llm.service.TrustReplyItemVersion
import com.weibo.talentintroduction.llm.service.TrustReplyLockedItemRequest
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerArchiveStatus
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexArchiveResult
import com.weibo.talentintroduction.llm.service.UnsupportedAnswerIndexService
import com.weibo.talentintroduction.llm.service.VerifiedTrustReplyAssembly
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordQaRuleRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional

/**
 * c6 (T-6.5)：线上侧「正文被编辑时仍归档」——运营改过正文（templateTextBody 与
 * assembly 产物逐字不等）的样本照常归档（不再 failedArchive）；editedByOperator
 * 标记由 service 写入（见 UnsupportedAnswerIndexServiceTest 的文档级断言）。
 */
class PendingMailOperationServiceTest {
    private val inboundMailProcessingRepository = Mockito.mock(InboundMailProcessingRepository::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val expertOperatorStatusService = Mockito.mock(ExpertOperatorStatusService::class.java)
    private val expertIndexLevelOperationService = Mockito.mock(ExpertIndexLevelOperationService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailDeliveryService = Mockito.mock(MailDeliveryService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val mailRecordQaRuleRepository = Mockito.mock(MailRecordQaRuleRepository::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val qaCategoryRepository = Mockito.mock(QaCategoryRepository::class.java)
    private val qaFactSelectionService = Mockito.mock(QaFactSelectionService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val renderTemplateService = MailComposeTemplateService(
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
        Mockito.mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),
        qaRuleRepository,
        Mockito.mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        expertContactRepository,
        mailSenderAccountService,
        ContentVariantService(
            Mockito.mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),
            MailPlaceholderService()
        )
    )
    private val mailVariableService = MailVariableService(expertSearchService, renderTemplateService)
    private val manualReplySendAttemptService = Mockito.mock(ManualReplySendAttemptService::class.java)
    private val trustReplyWorkbenchService = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val unsupportedAnswerIndexService = Mockito.mock(UnsupportedAnswerIndexService::class.java)
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)
    // 03 (T3/I-1): 真实 01 生成器（validateAndBuild + ICS 字节/语义 hash 全真实），只 mock
    // 模板目录的 listEnabled（启用门禁）—— 不以 mock 返回一模一样硬编码字串替代真实生成。
    private val meetingTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val meetingConfirmationService = MeetingConfirmationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        mailSenderAccountService,
        meetingTemplateService,
        MailContentService()
    )
    private val service = PendingMailOperationService(
        inboundMailProcessingRepository,
        expertContactRepository,
        expertOperatorStatusService,
        expertIndexLevelOperationService,
        mailSenderAccountService,
        mailDeliveryService,
        mailRecordRepository,
        mailRecordQaRuleRepository,
        operatorActionLogService,
        qaRuleRepository,
        qaCategoryRepository,
        qaFactSelectionService,
        aiReplyDraftService,
        aiReplyContextService,
        AiReplyHighRiskClaimValidator(qaRuleRepository),
        MailBodyCleaner(),
        MailContentService(),
        mailVariableService,
        manualReplySendAttemptService,
        trustReplyWorkbenchService,
        unsupportedAnswerIndexService,
        emailSuppressionService,
        meetingConfirmationService
    )

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "orcid-1",
        expertEmail = "expert@test.com",
        expertName = "Expert",
        currentStatus = "INTRO_SENT",
        operatorStatus = "CONTACTED",
        currentIndexLevel = "CANDIDATE"
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(inbound()))
        // Repair R-2 (V-2): 权威资格判定默认放行（用例用非资格版本显式断言的另设 stub）。
        Mockito.lenient().`when`(unsupportedAnswerIndexService.isArchiveEligible(Mockito.any(TrustReplyItemVersion::class.java) ?: operatorDirectedVersion()))
            .thenReturn(true)
        Mockito.`when`(expertContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(1L)).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Can I work remotely?", ""))
            .thenReturn(
                AiReplyContext(
                    profileText = "",
                    mailHistory = "",
                    contextWarnings = emptyList(),
                    researchProfileSufficient = true
                )
            )
        Mockito.`when`(qaCategoryRepository.findAll()).thenReturn(emptyList())
        Mockito.`when`(expertSearchService.findByOrcidId(contact.orcidId, ExpertIndexLevel.CANDIDATE))
            .thenReturn(
                ExpertProfile(
                    orcidId = contact.orcidId,
                    email = contact.expertEmail,
                    givenNames = null,
                    familyNames = "Expert",
                    country = null,
                    keyword = null,
                    employment = null
                )
            )
        Mockito.`when`(qaFactSelectionService.select("Can I work remotely?", null, true))
            .thenReturn(
                ResolvedQaRules(
                    sendQaRuleIds = emptyList(),
                    promptRuleIds = emptyList(),
                    requestFacts = emptyList()
                )
            )
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-1")).thenReturn(senderAccount())
        // 03: 专用会议模板默认启用；禁用用例在测试体内另行重 stub（最后 stub 生效）。
        Mockito.`when`(meetingTemplateService.listEnabled()).thenReturn(listOf(meetingTemplate()))
        val claim = ManualReplySendAttemptService.ClaimedAttempt(
            attemptId = 1L, messageId = "<manual-rich-abc@weibo.com>",
            result = ManualReplySendAttemptService.ClaimResult.CLAIMED
        )
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(claim)
        Mockito.`when`(
            mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail()))
        ).thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        Mockito.`when`(
            manualReplySendAttemptService.finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        ).thenReturn(500L)
    }

    // T-6.5: 正文被运营编辑（templateTextBody 与 assembly 产物逐字不等）时仍照常归档，
    // 归档状态为 SAVED 而非 FAILED（「去掉正文一字未改才归档」）。
    @Test
    fun `sendManualRichReply archives edited body instead of failing the archive`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH,
                Mockito.anyMap()
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = " operator-a ",
            // 运营改过正文：与 assembly 的 rawDraftText 逐字不等。
            templateTextBody = "Operator-edited final body, no longer verbatim.",
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        assertEquals(1, result.unsupportedAnswerArchivedCount)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("operator-a"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH,
            Mockito.anyMap()
        )
    }

    // 回归：未编辑（逐字一致）的正文仍照常归档。
    @Test
    fun `sendManualRichReply archives verbatim body as before`() {
        val assembly = liveAssembly()
        val eligible = operatorDirectedVersion()
        val assembled = assembledResponse(eligible)
        Mockito.`when`(trustReplyWorkbenchService.verifyAssembly(assembly)).thenReturn(verified(assembled))
        Mockito.`when`(trustReplyWorkbenchService.resolveSource(assembly.source)).thenReturn(liveResolvedSource())
        Mockito.`when`(
            unsupportedAnswerIndexService.archiveLiveCanonicalVersions(
                Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
                Mockito.anyList<TrustReplyItemVersion>() ?: emptyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.any(Instant::class.java) ?: Instant.EPOCH,
                Mockito.anyMap()
            )
        ).thenReturn(UnsupportedAnswerIndexArchiveResult(UnsupportedAnswerArchiveStatus.SAVED, 1, 0))

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>${assembled.renderedDraftText}</p>",
            textBody = assembled.renderedDraftText,
            operatorName = "op",
            templateTextBody = assembled.rawDraftText,
            trustReplyAssembly = assembly
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(UnsupportedAnswerArchiveStatus.SAVED, result.unsupportedAnswerArchiveStatus)
        Mockito.verify(unsupportedAnswerIndexService).archiveLiveCanonicalVersions(
            Mockito.any(ResolvedTrustReplySource::class.java) ?: liveResolvedSource(),
            eqValue(listOf(eligible)),
            eqValue("500"),
            eqValue("op"),
            Mockito.any(Instant::class.java) ?: Instant.EPOCH,
            Mockito.anyMap()
        )
    }

    private fun liveAssembly(): TrustReplyAssembleRequest {
        val source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L)
        val version = operatorDirectedVersion()
        return TrustReplyAssembleRequest(
            source = source,
            expectedSourceVersion = "live-v1",
            expectedEvidenceSetVersion = "evidence-v1",
            lockedItems = listOf(
                TrustReplyLockedItemRequest(
                    requestKey = version.requestKey,
                    versionId = version.versionId,
                    handling = version.handling,
                    answerText = version.answerText,
                    claims = version.claims,
                    model = version.model,
                    generationKind = version.generationKind,
                    evidenceSetVersion = version.evidenceSetVersion,
                    sourceVersion = version.sourceVersion,
                    operatorInstructionHash = version.operatorInstructionHash,
                    operatorInstruction = version.operatorInstruction
                )
            )
        )
    }

    private fun operatorDirectedVersion() = TrustReplyItemVersion(
        versionId = "live-version-1",
        requestKey = "live-request-1",
        handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
        answerText = "We will follow up next week.",
        claims = emptyList(),
        model = "DEEPSEEK_V4_FLASH",
        generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
        evidenceSetVersion = "evidence-v1",
        sourceVersion = "live-v1",
        operatorInstructionHash = "hash-1",
        requestIndex = 0,
        requestText = "When will you follow up?",
        operatorInstruction = "Please say we will follow up next week."
    )

    private fun verified(assembled: TrustReplyAssembleResponse): VerifiedTrustReplyAssembly =
        VerifiedTrustReplyAssembly(
            response = assembled,
            selection = ResolvedQaRules(
                sendQaRuleIds = assembled.canonicalFactIds,
                promptRuleIds = assembled.canonicalFactIds,
                requestFacts = emptyList()
            )
        )

    private fun assembledResponse(version: TrustReplyItemVersion): TrustReplyAssembleResponse {
        val body = "We will follow up next week."
        return TrustReplyAssembleResponse(
            source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
            sourceVersion = "live-v1",
            evidenceSetVersion = "evidence-v1",
            rawDraftText = body,
            renderedDraftText = body,
            draftHash = "draft-hash",
            canonicalFactIds = emptyList(),
            itemVersions = listOf(version)
        )
    }

    private fun liveResolvedSource() = ResolvedTrustReplySource(
        source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100L),
        contact = contact,
        inboundText = "Can I work remotely?",
        subject = "Question",
        messageId = "in-1",
        senderAccountCode = "sender-1",
        profileText = "",
        mailHistory = "",
        contextWarnings = emptyList(),
        researchProfileSufficient = true,
        sourceVersion = "live-v1"
    )

    private fun inbound() = InboundMailProcessing(
        id = 100L,
        senderAccountCode = "sender-1",
        imapUid = 1L,
        messageId = "in-1",
        fromEmail = "expert@test.com",
        subject = "Question",
        body = "Can I work remotely?",
        cleanedBody = "Can I work remotely?",
        receivedAt = LocalDateTime.now(),
        processStatus = "MANUAL_REVIEW",
        processReason = "QA_NO_MATCH",
        expertContactId = 1L
    )

    private fun senderAccount() = MailSenderAccount(
        accountCode = "sender-1",
        senderEmail = "sender@test.com",
        senderName = "Sender",
        senderTitle = "Title",
        senderDisplayName = "Sender",
        teamName = "Team",
        countryName = "CN",
        smtpHost = "smtp.test.com",
        smtpPort = 465,
        smtpUsername = "u",
        smtpPassword = "p",
        imapHost = "imap.test.com",
        imapPort = 993,
        imapUsername = "u",
        imapPassword = "p",
        enabled = true
    )

    private fun sendPayload() = ManualReplySendAttemptService.SendPayload(
        orcidId = contact.orcidId,
        contactId = requireNotNull(contact.id),
        inboundProcessingId = 100L,
        accountCode = "sender-1",
        normalizedRecipient = contact.expertEmail,
        subject = "Re: Test",
        finalText = "Test",
        finalHtml = "<p>Test</p>",
        inReplyTo = "in-1",
        canonicalQaRuleIds = emptyList(),
        primaryRuleId = null
    )

    private fun composedMail() = ComposedMail(
        to = contact.expertEmail,
        subject = "Re: Test",
        body = "<p>Test</p>",
        html = true,
        text = "Test",
        messageId = "<manual-rich-abc@weibo.com>"
    )

    // ------------------------------------------------------------------
    // 03 (T3/I-1/I-2): 会议日历发送场景 —— 真实 01 生成器 + 控制器同形传参 +
    // 实际 ComposedMail/SendPayload。meeting 与 previewAttachmentSha256 走
    // PendingManualRichReplyRequest → sendManualRichReply 的控制器转发形态。
    // ------------------------------------------------------------------

    @Test
    fun `meeting send carries the one real snapshot into payload and composed mail with thread headers`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        // 会议正文含日期/会议链接会命中既有纯文本安全检查（NORMAL finding）——
        // 按 A-1 第 2 步「如果 422 按原安全确认流程提交相同配置」确认后发送。
        val result = calendarRichSend(preview = preview, input = input, safetyWarningConfirmed = true)

        assertEquals("SENT", result.sendStatus)
        val mail = capturedMails.single()
        val payload = capturedPayloads.single()
        val snapshot = requireNotNull(payload.calendarAttachment) { "payload 必须携带 01 快照" }
        assertEquals(preview.attachment.sha256, snapshot.sha256)
        assertEquals(preview.attachment.semanticSha256, snapshot.semanticSha256)
        assertEquals(preview.attachment.icsText, snapshot.icsText)
        assertEquals(preview.attachment.filename, snapshot.filename)
        // 同一快照实例同时进入 SendPayload 与 ComposedMail（不生成第二份）。
        assertSame(snapshot, mail.calendarAttachment)
        // I-2: 带日历新分支的线程头 = 真实来信 messageId（in-1）；inReplyTo/references 同源。
        assertEquals("in-1", mail.inReplyTo)
        assertEquals("in-1", mail.references)
        assertEquals(contact.expertEmail, mail.to)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `meeting without preview digest or digest alone is rejected 400 before any claim`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val missingDigest = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = input, digest = null)
        }
        assertEquals(HttpStatus.BAD_REQUEST, missingDigest.status)
        assertEquals("会议附件配置不完整，请重新预览", missingDigest.reason)
        val digestOnly = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = null, digest = preview.attachment.sha256)
        }
        assertEquals(HttpStatus.BAD_REQUEST, digestOnly.status)
        assertEquals("会议附件配置不完整，请重新预览", digestOnly.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `changed meeting config after preview is rejected 400 config changed`() {
        val input = meetingInput()
        val preview = previewFor(input)
        // 配置已变（改会议时长/链接），仍带旧 digest → 服务端重算 sha 不一致。
        val changed = input.copy(zoomUrl = "https://zoom.us/j/98765432100?pwd=changedDigest")
        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = changed)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("会议配置已变化，请重新预览", ex.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
    }

    @Test
    fun `edited meeting time inside body is rejected 400 body mismatch`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val editedText = preview.textBody.replace("10:00 AM", "11:00 AM").replace("10:30 AM", "11:30 AM")
        val editedHtml = preview.htmlBody.replace("10:00 AM", "11:00 AM").replace("10:30 AM", "11:30 AM")
        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = input, textOverride = editedText, htmlOverride = editedHtml)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件", ex.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
    }

    @Test
    fun `editing only outside the meeting text still sends`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val note = "\n\nThis note outside the meeting block is an operator addition."
        val result = calendarRichSend(
            preview = preview, input = input,
            textOverride = preview.textBody + note, htmlOverride = preview.htmlBody + "<p>Extra note</p>",
            safetyWarningConfirmed = true
        )
        // 附加段落不改动会议块（完整会议正文仍连续存在）→ 正文核对通过并成功发送。
        assertEquals("SENT", result.sendStatus)
        Mockito.verify(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
    }

    @Test
    fun `disabled meeting template rejects with the 01 template message`() {
        val input = meetingInput()
        val preview = previewFor(input)
        Mockito.`when`(meetingTemplateService.listEnabled()).thenReturn(emptyList())
        val ex = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = input)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("会议模板不可用，请重新选择或检查模板变量", ex.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never()).prepareAndClaim(anyValue(sendPayload()))
    }

    @Test
    fun `safety confirmation keeps meeting and digest on the confirmed retry`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val hallucinated = preview.textBody + "\n\nWe confirm the programme provides EUR 1,200,000 in funding."
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        // Zoom/会议来自表单绝不自动确认：未确认首次仍按原 422 语义（safety findings）。
        val blocked = assertThrows(ManualSendSafetyBlockedException::class.java) {
            calendarRichSend(
                preview = preview, input = input,
                textOverride = hallucinated, htmlOverride = hallucinated,
                safetyWarningConfirmed = false
            )
        }
        assertTrue(
            blocked.findings.any { it.code == AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT },
            "纯文本虚构数字仍触发幻觉事实确认，实际 codes=${blocked.findings.map { it.code }}"
        )
        Mockito.verify(mailDeliveryService, Mockito.never()).send(anyValue(senderAccount()), anyValue(composedMail()))

        // 原确认流程后用同一 meeting/digest 重试成功，快照仍为同一预览内容。
        val result = calendarRichSend(
            preview = preview, input = input,
            textOverride = hallucinated, htmlOverride = hallucinated,
            safetyWarningConfirmed = true, strongConfirmationText = "确认发送"
        )
        assertEquals("SENT", result.sendStatus)
        val payload = capturedPayloads.single()
        assertEquals(preview.attachment.sha256, requireNotNull(payload.calendarAttachment).sha256)
        assertSame(payload.calendarAttachment, capturedMails.single().calendarAttachment)
    }

    @Test
    fun `no meeting old path leaves calendar and thread headers untouched`() {
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Remote work is possible.</p>",
            textBody = "Remote work is possible.",
            operatorName = "op"
        )
        assertEquals("SENT", result.sendStatus)
        val mail = capturedMails.single()
        val payload = capturedPayloads.single()
        assertNull(payload.calendarAttachment, "无 meeting 旧路径不携带快照")
        assertNull(mail.calendarAttachment)
        assertNull(mail.inReplyTo, "旧调用形态线程头保持默认 null")
        assertNull(mail.references)
    }

    // 03: 发送侧 controller 透传 —— 以 UnmatchedInboundMailController 同形请求调用
    // PendingManualRichReplyRequest（@RequestBody 绑定面：meeting 嵌套 + digest 默认 null）。
    @Test
    fun `controller request binding parses meeting and keeps digest nullable defaults`() {
        val input = meetingInput()
        val request = PendingManualRichReplyRequest(
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>meeting</p>",
            textBody = "meeting",
            operatorName = "op",
            meeting = input,
            previewAttachmentSha256 = "a".repeat(64)
        )
        // 与 Spring Boot 默认 mapper 一致（KotlinModule 走主构造反序列化）。
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val json = mapper.writeValueAsString(request)
        val parsed = mapper.readValue(json, PendingManualRichReplyRequest::class.java)
        assertEquals(input, parsed.meeting)
        assertEquals("a".repeat(64), parsed.previewAttachmentSha256)
        val legacy = mapper.readValue(
            """{"senderAccountCode":null,"subject":"Re","htmlBody":"<p>h</p>","textBody":"t","operatorName":"op"}""",
            PendingManualRichReplyRequest::class.java
        )
        assertNull(legacy.meeting)
        assertNull(legacy.previewAttachmentSha256)
    }

    // ── 03 calendar helpers ──

    private fun meetingTemplate(): MailComposeTemplate = MailComposeTemplate(
        id = MEETING_TEMPLATE_ID,
        templateCode = "MANUAL_MEETING_CONFIRMATION",
        templateName = "专家会议确认 · 英文",
        subject = "Meeting confirmation",
        mailType = "MANUAL_MEETING_CONFIRMATION",
        enabled = true
    )

    private fun meetingInput() = MeetingInput(
        templateId = MEETING_TEMPLATE_ID,
        templateBody = MEETING_TEMPLATE_BODY,
        expertSalutation = "Professor Basdogan",
        zoneId = "Europe/Istanbul",
        startLocal = "2026-09-11T10:00",
        endLocal = "2026-09-11T10:30",
        zoomUrl = "https://zoom.us/j/92123456789?pwd=abcDEF123",
        senderSignature = "LuKai, Customer Care Officer\nQingfei Tech Talent Team China",
        generatedAt = "2026-09-09T02:00:00Z"
    )

    private fun previewFor(input: MeetingInput): MeetingPreviewResponse =
        meetingConfirmationService.validateAndBuild(100L, contact, senderAccount(), input)

    /**
     * 以 UnmatchedInboundMailController.sendManualRichReply 的转发形态（同形 named args）
     * 调用服务：预览正文作为人工编辑器 text/html（变量渲染后仍是同一正文），携带
     * meeting + previewAttachmentSha256。
     */
    private fun calendarRichSend(
        preview: MeetingPreviewResponse,
        input: MeetingInput?,
        digest: String? = preview.attachment.sha256,
        textOverride: String = preview.textBody,
        htmlOverride: String = preview.htmlBody,
        safetyWarningConfirmed: Boolean = false,
        strongConfirmationText: String? = null
    ): PendingMailSendResult = service.sendManualRichReply(
        inboundProcessingId = 100L,
        senderAccountCode = null,
        subject = "Re: Test",
        htmlBody = htmlOverride,
        textBody = textOverride,
        operatorName = "op",
        templateTextBody = textOverride,
        templateHtmlBody = htmlOverride,
        safetyWarningConfirmed = safetyWarningConfirmed,
        strongConfirmationText = strongConfirmationText,
        meeting = input,
        previewAttachmentSha256 = digest
    )

    /** 捕获实际外发的 ComposedMail 与 finalize 的 SendPayload（重 stub 捕获版）。 */
    private fun captureCalendarSend(capturedMails: MutableList<ComposedMail>): MutableList<ManualReplySendAttemptService.SendPayload> {
        val capturedPayloads = mutableListOf<ManualReplySendAttemptService.SendPayload>()
        Mockito.doAnswer { inv ->
            capturedPayloads += inv.getArgument<ManualReplySendAttemptService.SendPayload>(0)
            500L
        }.`when`(manualReplySendAttemptService)
            .finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        Mockito.doAnswer { inv ->
            capturedMails += inv.getArgument<ComposedMail>(1)
            DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT")
        }.`when`(mailDeliveryService).send(anyValue(senderAccount()), anyValue(composedMail()))
        return capturedPayloads
    }

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    companion object {
        private const val MEETING_TEMPLATE_ID = 9001L
        private const val MEETING_TEMPLATE_BODY =
            "Dear {{expert_salutation}},\n\n" +
                "Thank you for confirming.\n\n" +
                "We have noted the meeting time as {{meeting_time}}.\n\n" +
                "Please join the meeting using the following link:\n\n" +
                "{{zoom_url}}\n\n" +
                "We look forward to speaking with you.\n\n" +
                "Best regards,\n" +
                "{{sender_signature}}"
    }
}
