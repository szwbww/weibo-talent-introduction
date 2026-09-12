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
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
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
        MailContentService(),
        mailVariableService
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
        // 03: 会议正文由通用 MEETING_INVITATION 模板渲染；缺失/禁用用例在测试体内另行重 stub。
        Mockito.`when`(
            meetingTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap()),
                Mockito.anyInt()
            )
        ).thenAnswer { invocation ->
            val variables = invocation.getArgument<Map<String, String>>(1)
            ComposeTemplateRenderResult(
                subject = "Meeting confirmation",
                body = "Dear " + variables["expertFamilyName"].orEmpty().ifBlank { "Colleague" } + ",\n\n" +
                    "Thank you for confirming.\n\n" +
                    "We have noted the meeting time as " + variables["meeting_time"].orEmpty() + ".\n\n" +
                    "Please join the meeting using the following link:\n\n" +
                    variables["zoom_url"].orEmpty() + "\n\n" +
                    "We look forward to speaking with you.\n\n" +
                    "Best regards,\n" +
                    variables["senderName"].orEmpty() + ", " + variables["senderTitle"].orEmpty()
            )
        }
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

    // =====================================================================
    // 会话（无来信）自由回信（T2）：真实 SENT 锚点/账号/线程头/幂等收敛/422 边界
    // =====================================================================

    private fun sentAnchor(
        id: Long = 77L,
        accountCode: String = "sender-1",
        messageId: String? = "<anchor-1@test.com>",
        inReplyTo: String? = null,
        sentAt: LocalDateTime = LocalDateTime.now()
    ) = com.weibo.talentintroduction.mail.domain.MailRecord(
        id = id,
        expertContactId = 1L,
        direction = "OUTBOUND",
        mailType = "INTRODUCTION",
        senderAccountCode = accountCode,
        messageId = messageId,
        inReplyTo = inReplyTo,
        subject = "Introduction",
        body = "intro",
        matchedQaRuleId = null,
        sendStatus = "SENT",
        receivedAt = null,
        sentAt = sentAt
    )

    private val conversationRequestId = "b5c98f60-7b46-4f1e-9c11-1a2b3c4d5e6f"

    // manualReplySendAttemptService 是 mock：canonicalConversationRequestId 默认返回 null，
    // 各会话用例须显式回灌 canonical（与真实 UUID.toString() 小写形态一致）。
    private fun stubConversationRequestCanonical(requestId: String = conversationRequestId) {
        Mockito.`when`(manualReplySendAttemptService.canonicalConversationRequestId(requestId)).thenReturn(requestId)
    }

    private fun invocationOf(mock: Any, methodName: String) =
        Mockito.mockingDetails(mock).invocations.single { it.method.name == methodName }

    private fun hasInvocation(mock: Any, methodName: String) =
        Mockito.mockingDetails(mock).invocations.any { it.method.name == methodName }

    private fun stubConversationAnchor(anchor: com.weibo.talentintroduction.mail.domain.MailRecord?) {
        Mockito.`when`(
            mailRecordRepository.findLatestSentOutboundAnchor(
                Mockito.eq(1L),
                Mockito.any(),
                Mockito.eq(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
            )
        ).thenAnswer {
            val scope = it.getArgument<String>(1)
            if (scope.isNullOrBlank() || anchor?.senderAccountCode == scope) anchor else null
        }
    }

    // I-1/I-6/I-7/I-11：最近 SENT 锚点决定账号/落库 inReplyTo/SMTP 线程头；会话审计
    // target 为联系人、携带真实 anchorMailRecordId；无 QA/RAG 副作用。
    @Test
    fun `conversation reply uses latest SENT anchor account with thread headers and contact audit`() {
        stubConversationAnchor(sentAnchor(messageId = "<anchor-1@test.com>", inReplyTo = "<old-0@test.com>"))
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)

        val result = service.sendConversationManualRichReply(
            contactId = 1L,
            requestId = conversationRequestId,
            accountScope = null,
            subject = "Re: Test",
            htmlBody = "<p>Test</p>",
            textBody = "Test",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("sender-1", result.senderAccountCode)
        assertEquals("MANUAL_RICH_REPLY", result.mailType)
        assertEquals("<manual-rich-abc@weibo.com>", result.messageId)
        assertEquals(UnsupportedAnswerArchiveStatus.NOT_APPLICABLE, result.unsupportedAnswerArchiveStatus)
        // 锚点查询带 scope（空串视为无过滤）与模拟器排除
        Mockito.verify(mailRecordRepository).findLatestSentOutboundAnchor(1L, null, MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)

        val payload = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertTrue(payload.inboundProcessingId == null, "不伪造 inbound id")
        assertEquals("MAIL_RECORD:77", payload.sourceAnchor)
        assertEquals(conversationRequestId, payload.idempotencyRequestId)
        assertEquals("<anchor-1@test.com>", payload.inReplyTo)
        assertTrue(payload.canonicalQaRuleIds.isEmpty())

        val sendInvocation = invocationOf(mailDeliveryService, "send")
        assertEquals("sender-1", (sendInvocation.arguments[0] as MailSenderAccount).accountCode)
        val mail = sendInvocation.arguments[1] as ComposedMail
        assertEquals("<anchor-1@test.com>", mail.inReplyTo)
        assertEquals("<old-0@test.com> <anchor-1@test.com>", mail.references)
        assertEquals("<manual-rich-abc@weibo.com>", mail.messageId)

        val auditInvocation = invocationOf(manualReplySendAttemptService, "recordConversationSendAudit")
        assertEquals(1L, auditInvocation.arguments[0])
        assertEquals(77L, auditInvocation.arguments[1])
        assertEquals(500L, auditInvocation.arguments[2])
        assertEquals("SENT", (auditInvocation.arguments[3] as DeliveredMail).status)
        assertEquals("Re: Test", auditInvocation.arguments[4])
        assertEquals("op", auditInvocation.arguments[6])
        assertTrue(
            !hasInvocation(manualReplySendAttemptService, "recordSendAudit"),
            "outbound 路径绝不调用来信审计"
        )
    }

    // I-6/I-1：accountScope 只约束锚点查询；scope 内无 SENT 锚点 → 422 且不回退其他账号、
    // 不 claim/不 SMTP。
    @Test
    fun `conversation reply without SENT anchor in scope returns 422 before any send side effect`() {
        stubConversationAnchor(sentAnchor(accountCode = "sender-1"))
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
        // scope=other-acc：查询命中 null（不同账号）
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = conversationRequestId, accountScope = "other-acc",
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
            )
        }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        assertEquals("CONVERSATION_SENT_ANCHOR_NOT_FOUND", ex.reason)
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "无锚点不得 claim")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "无锚点不得 SMTP")
        assertTrue(!hasInvocation(manualReplySendAttemptService, "finalizeSuccess"), "无锚点不得 finalize")
        // scope 参与查询（excludedAccountCode 恒为模拟器）
        Mockito.verify(mailRecordRepository).findLatestSentOutboundAnchor(1L, "other-acc", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
    }

    // I-7：锚点 Message-ID 缺失时仍发送，线程头为空、不伪造引用。
    @Test
    fun `conversation reply without anchor message id still sends with empty thread headers`() {
        stubConversationAnchor(sentAnchor(messageId = null))
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )
        assertEquals("SENT", result.sendStatus)
        val payload = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertTrue(payload.inReplyTo == null)
        val mail = invocationOf(mailDeliveryService, "send").arguments[1] as ComposedMail
        assertTrue(mail.inReplyTo == null)
        assertTrue(mail.references == null)
    }

    // I-7：超长 Message-ID 不进线程头但仍发送。
    @Test
    fun `conversation reply with overlong anchor message id sends with empty thread headers`() {
        val overlong = "<" + "x".repeat(300) + "@test.com>"
        stubConversationAnchor(sentAnchor(messageId = overlong))
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )
        assertEquals("SENT", result.sendStatus)
        val payload = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertTrue(payload.inReplyTo == null)
    }

    // I-4/I-10：已完成 requestId 在锚点重查前返回原结果 —— 不再查锚点、不 claim、不 SMTP。
    @Test
    fun `completed request id returns original result before anchor requery and never sends again`() {
        val completed = ManualReplySendAttemptService.CompletedOutboundReply(
            attemptId = 1L,
            attemptMessageId = "<manual-rich-orig@weibo.com>",
            attemptAccountCode = "sender-1",
            mailRecord = com.weibo.talentintroduction.mail.domain.MailRecord(
                id = 500L,
                expertContactId = 1L,
                direction = "OUTBOUND",
                mailType = "MANUAL_RICH_REPLY",
                senderAccountCode = "sender-1",
                messageId = "<manual-rich-orig@weibo.com>",
                inReplyTo = "<anchor-1@test.com>",
                subject = "Re: original",
                body = "sent",
                matchedQaRuleId = null,
                sendStatus = "SENT",
                receivedAt = null,
                sentAt = LocalDateTime.now()
            )
        )
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(completed)
        stubConversationRequestCanonical()

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("<manual-rich-orig@weibo.com>", result.messageId)
        assertEquals("sender-1", result.senderAccountCode)
        assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "收敛命中不重查锚点")
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "收敛命中不 claim")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "收敛命中不再次 SMTP")
    }

    // I-2/I-8：已处理（PROCESSED）来信继续可回 —— 回信入口不读 processStatus。
    @Test
    fun `processed inbound remains replyable without manual review status`() {
        val processed = inbound().copy(processStatus = "PROCESSED")
        Mockito.`when`(inboundMailProcessingRepository.findById(100L)).thenReturn(Optional.of(processed))
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Test</p>",
            textBody = "Test",
            operatorName = "op"
        )
        assertEquals("SENT", result.sendStatus)
        val auditInvocation = invocationOf(manualReplySendAttemptService, "recordSendAudit")
        assertEquals(100L, auditInvocation.arguments[0])
        assertEquals(1L, auditInvocation.arguments[1])
        assertEquals(500L, auditInvocation.arguments[2])
        assertEquals(emptyList<Long>(), auditInvocation.arguments[3])
        assertEquals(false, auditInvocation.arguments[4])
        assertEquals("Re: Test", auditInvocation.arguments[6])
        assertEquals("op", auditInvocation.arguments[8])
        assertEquals(processed, auditInvocation.arguments[9])
        assertEquals(emptyList<Long>(), auditInvocation.arguments[10])
        assertTrue(auditInvocation.arguments[11] == null, "无 assembly 时 edited 恒 null")
    }

    // T2.7/I-9：outbound 路径不运行来信语义（不调 QA selection）；通用安全检查仍是门禁。
    @Test
    fun `conversation reply skips inbound QA selection and respects generic safety confirmation`() {
        stubConversationAnchor(sentAnchor())
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
        // 「请放心」命中信任话术通用检查（NORMAL），未确认即 422 阻断（claim 前）
        val ex = assertThrows(ManualSendSafetyBlockedException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = conversationRequestId, accountScope = null,
                subject = "Re: Test", htmlBody = "<p>请放心，一切顺利</p>", textBody = "请放心，一切顺利",
                operatorName = "op", safetyWarningConfirmed = false
            )
        }
        assertTrue(ex.findings.any { it.code == AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC })
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "阻断发生在 claim 前")
        assertTrue(!hasInvocation(qaFactSelectionService, "select"), "outbound 不调 QA selection")
        // 确认后继续发送成功
        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>请放心，一切顺利</p>", textBody = "请放心，一切顺利",
            operatorName = "op", safetyWarningConfirmed = true
        )
        assertEquals("SENT", result.sendStatus)
    }

    // I-9：suppression 在 claim 前阻断且不烧 attempt。
    @Test
    fun `conversation reply honors suppression before claim`() {
        stubConversationAnchor(sentAnchor())
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
        Mockito.`when`(emailSuppressionService.isSuppressed(contact.expertEmail)).thenReturn(true)
        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = conversationRequestId, accountScope = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "suppression 在 claim 前")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "suppression 不 SMTP")
    }

    // I-4/I-10：非法/缺失 requestId 立即失败；suppression 之外无副作用。
    @Test
    fun `conversation reply rejects malformed or missing request id before any side effect`() {
        stubConversationAnchor(sentAnchor())
        Mockito.`when`(manualReplySendAttemptService.canonicalConversationRequestId("not-a-uuid"))
            .thenThrow(IllegalArgumentException("invalid uuid"))
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = "not-a-uuid", accountScope = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
            )
        }
        assertTrue(ex.message!!.contains("UUID"))
        assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "非法 requestId 不查锚点")
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "非法 requestId 不 claim")
    }

    // repair R-1（V-1）回归守卫：findLatestSentOutboundAnchor 原生查询必须排除空串/纯空白
    // 账号 —— IS NOT NULL 不拒绝 ''，空白账号的 SENT 行不得遮蔽更早的合法锚点；谓词顺序
    // 与排序契约（I-1/T1.1）逐字保持。
    @Test
    fun `anchor query excludes blank and whitespace-only sender accounts`() {
        val repoSource = java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt")
        )
        val funIndex = repoSource.indexOf("fun findLatestSentOutboundAnchor(")
        assertTrue(funIndex >= 0, "仓库必须存在 findLatestSentOutboundAnchor")
        val blockStart = repoSource.lastIndexOf("@Query", funIndex)
        assertTrue(blockStart >= 0, "锚点查询必须携带 @Query 原生 SQL")
        val anchorSql = repoSource.substring(blockStart, funIndex)

        assertTrue(anchorSql.contains("direction = 'OUTBOUND'"), "只做出站")
        assertTrue(anchorSql.contains("send_status = 'SENT'"), "只认真实成功发件")
        assertTrue(anchorSql.contains("sender_account_code IS NOT NULL"), "保留非空判定")
        assertTrue(
            anchorSql.contains("TRIM(sender_account_code) <> ''"),
            "空串/纯空白 sender_account_code 不能成为锚点"
        )
        // 谓词顺序契约：非空 → trim 非空 → 模拟器排除 → 账号范围
        val notNullIdx = anchorSql.indexOf("sender_account_code IS NOT NULL")
        val trimIdx = anchorSql.indexOf("TRIM(sender_account_code) <> ''")
        val excludedIdx = anchorSql.indexOf(":excludedAccountCode")
        val scopeIdx = anchorSql.indexOf(":accountScope")
        assertTrue(notNullIdx >= 0 && trimIdx > notNullIdx && excludedIdx > trimIdx && scopeIdx > excludedIdx)
        assertTrue(
            anchorSql.contains("ORDER BY COALESCE(sent_at, created_at) DESC, id DESC") &&
                anchorSql.contains("LIMIT 1"),
            "锚点排序/单行契约不变"
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
    fun `missing meeting invitation template rejects with the 01 template message`() {
        val input = meetingInput()
        val preview = previewFor(input)
        Mockito.`when`(
            meetingTemplateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap()),
                Mockito.anyInt()
            )
        ).thenThrow(IllegalStateException("Enabled compose template not found: MEETING_INVITATION"))
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

    // I-1/I-2/I-4：来信路径的 final body 在变量渲染后、claim 前规范化一次；claim 载荷、
    // SMTP 两个 MIME alternative、落库 payload 逐字引用同一份 canonical text/html。
    @Test
    fun `sendManualRichReply normalizes final bodies before claim and reuses them verbatim`() {
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<b>A</b><br><br><br>B",
            textBody = "A\r\n\r\n\r\n\r\nB",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        val claimed = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertEquals("A\nB", claimed.finalText, "claim 前 payload 已是 canonical 纯文本")
        assertEquals("<b>A</b><br>B", claimed.finalHtml, "claim 前 payload 已是 canonical HTML")
        assertFalse(claimed.finalText.contains('\r'))
        assertFalse(claimed.finalText.contains("\n\n"))

        val mail = capturedMails.single()
        assertEquals(claimed.finalText, mail.text, "text/plain alternative 与 payload 逐字相同")
        assertEquals(claimed.finalHtml, mail.body, "HTML alternative 与 payload 逐字相同")
        val persisted = capturedPayloads.single()
        assertEquals(claimed.finalText, persisted.finalText, "落库 payload 引用同一 canonical 正文")
        assertEquals(claimed.finalHtml, persisted.finalHtml)
    }

    // I-1/I-4：规范化发生在最终变量渲染之后；已是单换行/无连续 <br> 的正文逐字保留。
    @Test
    fun `sendManualRichReply renders variables before normalizing and keeps single lf verbatim`() {
        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>A</p><p>B</p>",
            textBody = "A\n\n\n\${senderName}\n\n\nB",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        val claimed = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertEquals("A\nSender\nB", claimed.finalText, "占位符先渲染，再收敛换行")
        assertEquals("<p>A</p><p>B</p>", claimed.finalHtml, "无连续 <br> 的人工 HTML 逐字保留")
    }

    // I-1/I-2/I-3：会话回信入口（无来信上下文、不经过浏览器）同样落规范化门禁。
    @Test
    fun `sendConversationManualRichReply normalizes final bodies without any client cooperation`() {
        stubConversationAnchor(sentAnchor())
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)

        val result = service.sendConversationManualRichReply(
            contactId = 1L,
            requestId = conversationRequestId,
            accountScope = null,
            subject = "Re: Test",
            htmlBody = "<div>A</div><div><br></div><div><b>B</b></div>",
            textBody = "A\n \t\n\nB\n\n\n",
            operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        val claimed = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertEquals("A\nB", claimed.finalText)
        assertEquals("<div>A</div><div><b>B</b></div>", claimed.finalHtml)
        val mail = invocationOf(mailDeliveryService, "send").arguments[1] as ComposedMail
        assertEquals(claimed.finalText, mail.text)
        assertEquals(claimed.finalHtml, mail.body)
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

    private fun meetingInput() = MeetingInput(
        zoneId = "Europe/Istanbul",
        startLocal = "2026-09-11T10:00",
        endLocal = "2026-09-11T10:30",
        zoomUrl = "https://zoom.us/j/92123456789?pwd=abcDEF123",
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
}
