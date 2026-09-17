package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
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
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    // 06 (T1/I-1/I-2): 通用附件协作件 —— 本文件断言两条入口的 id/身份透传与文件集合流动，
    // 真实原件读取/归属/容量边界由 OutboundAttachmentFlowTest 用真实 04 服务覆盖。
    private val outboundAttachmentService = Mockito.mock(OutboundAttachmentService::class.java)
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
        meetingConfirmationService,
        outboundAttachmentService = outboundAttachmentService
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

    // 跟进（I-2/I-3）：显式锚点重读权威行 —— 选择较旧 #2893 时不得回退到最新 #4007；
    // 账号、落库 inReplyTo、SMTP In-Reply-To/References 与 sourceAnchor 全部取所选记录。
    @Test
    fun `explicit anchor drives account thread headers and source anchor without the latest query`() {
        val chosen = sentAnchor(
            id = 2893L,
            accountCode = "sender-old",
            messageId = "<m2893@test.com>",
            inReplyTo = "<m2800@test.com>"
        )
        val oldAccount = senderAccount().copy(accountCode = "sender-old", senderEmail = "old@test.com")
        Mockito.`when`(mailRecordRepository.findById(2893L)).thenReturn(Optional.of(chosen))
        Mockito.`when`(mailSenderAccountService.getManualSendAccount("sender-old")).thenReturn(oldAccount)
        Mockito.`when`(mailDeliveryService.send(anyValue(oldAccount), anyValue(composedMail())))
            .thenReturn(DeliveredMail(messageId = "<manual-rich-abc@weibo.com>", status = "SENT"))
        // 最新成功发件桩：一旦被查询就会返回 #4007（本用例断言绝不发生）
        Mockito.`when`(
            mailRecordRepository.findLatestSentOutboundAnchor(1L, null, MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ).thenReturn(
            sentAnchor(id = 4007L, accountCode = "sender-new", messageId = "<m4007@test.com>")
        )
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            anchorMailRecordId = 2893L,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("sender-old", result.senderAccountCode)
        assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "显式锚点绝不回退最近发件")

        val payload = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertEquals("MAIL_RECORD:2893", payload.sourceAnchor)
        assertEquals("<m2893@test.com>", payload.inReplyTo)

        val sendInvocation = invocationOf(mailDeliveryService, "send")
        assertEquals("sender-old", (sendInvocation.arguments[0] as MailSenderAccount).accountCode)
        val mail = sendInvocation.arguments[1] as ComposedMail
        assertEquals("<m2893@test.com>", mail.inReplyTo)
        assertEquals("<m2800@test.com> <m2893@test.com>", mail.references)

        val auditInvocation = invocationOf(manualReplySendAttemptService, "recordConversationSendAudit")
        assertEquals(2893L, auditInvocation.arguments[1])
    }

    // 跟进（I-2）：显式锚点每种非法形态统一 422 CONVERSATION_SENT_ANCHOR_NOT_FOUND，
    // 且在任何 claim/SMTP/finalize 之前短路；不存在的 id 同样 422。
    @Test
    fun `explicit anchor rejects wrong contact direction status account simulator scope and missing row`() {
        val cases = listOf(
            "跨联系人" to (sentAnchor(id = 2893L).copy(expertContactId = 2L) to null),
            "非 OUTBOUND" to (sentAnchor(id = 2893L).copy(direction = "INBOUND") to null),
            "非 SENT" to (sentAnchor(id = 2893L).copy(sendStatus = "FAILED") to null),
            "空账号" to (sentAnchor(id = 2893L).copy(senderAccountCode = "   ") to null),
            "模拟器账号" to (sentAnchor(id = 2893L).copy(senderAccountCode = MailSenderAccountService.SIMULATOR_ACCOUNT_CODE) to null),
            "scope 不同" to (sentAnchor(id = 2893L, accountCode = "sender-1") to "other-acc"),
            "行不存在" to (null to null)
        )
        cases.forEach { (label, pair) ->
            val (record, scope) = pair
            Mockito.clearInvocations(manualReplySendAttemptService, mailDeliveryService, mailRecordRepository)
            Mockito.`when`(mailRecordRepository.findById(2893L)).thenReturn(Optional.ofNullable(record))
            stubConversationRequestCanonical()
            Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
                .thenReturn(null)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.sendConversationManualRichReply(
                    contactId = 1L, requestId = conversationRequestId, accountScope = scope,
                    anchorMailRecordId = 2893L,
                    subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
                )
            }
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status, label)
            assertEquals("CONVERSATION_SENT_ANCHOR_NOT_FOUND", ex.reason, label)
            assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "$label 不得 claim")
            assertTrue(!hasInvocation(mailDeliveryService, "send"), "$label 不得 SMTP")
            assertTrue(!hasInvocation(manualReplySendAttemptService, "finalizeSuccess"), "$label 不得 finalize")
            assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "$label 不得回退最近发件")
        }
    }

    // I-4/I-10：已完成 requestId 优先于任何锚点读取 —— 携带显式 id 也不读 mail_record、不重投。
    @Test
    fun `completed request id with an explicit anchor never reads the anchor row`() {
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(completedConversationReply())
        stubConversationRequestCanonical()

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            anchorMailRecordId = 2893L,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("<manual-rich-orig@weibo.com>", result.messageId)
        assertTrue(!hasInvocation(mailRecordRepository, "findById"), "收敛命中不读锚点行")
        assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "收敛命中不查最近发件")
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "收敛命中不 claim")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "收敛命中不再次 SMTP")
    }

    private fun completedConversationReply() = ManualReplySendAttemptService.CompletedOutboundReply(
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
        val completed = completedConversationReply()
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
        // fast-p 02 (I-1)：结构化排期输入来自同一次 validateAndBuild —— 秒级 UTC 起止与
        // 已校验链接与预览逐字段一致，绝不重新取当前时间/重新生成 ICS。
        val event = requireNotNull(payload.meetingEvent) { "payload 必须携带结构化排期输入" }
        assertEquals(Instant.parse(preview.startUtc), event.startUtc)
        assertEquals(Instant.parse(preview.endUtc), event.endUtc)
        assertEquals(input.zoomUrl, event.meetingLink)
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
        assertNull(payload.meetingEvent, "无 meeting 旧路径不携带排期输入")
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
        assertEquals("A\n\nB", claimed.finalText, "claim 前 payload 保留一个空行")
        assertEquals("<b>A</b><br><br>B", claimed.finalHtml, "claim 前 payload 保留一个 HTML 空行")
        assertFalse(claimed.finalText.contains('\r'))
        assertFalse(claimed.finalText.contains("\n\n\n"))

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
        assertEquals("A\n\nSender\n\nB", claimed.finalText, "占位符先渲染，再收敛换行")
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
        assertEquals("A\n\nB", claimed.finalText)
        assertEquals("<div>A</div><div><br></div><div><b>B</b></div>", claimed.finalHtml)
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

    // I-2：SMTP 安全失败只走 finalizeFailure（成功事务从未被调用 → 0 新增排期），
    // 发送语义仍是既有「可安全重试」。
    @Test
    fun `meeting smtp safe failure never creates a schedule and stays a safe retry`() {
        val input = meetingInput()
        val preview = previewFor(input)
        captureCalendarSend(mutableListOf())
        stubFinalizeFailure(501L)
        Mockito.`when`(mailDeliveryService.send(anyValue(senderAccount()), anyValue(composedMail())))
            .thenReturn(
                DeliveredMail(
                    messageId = "<manual-rich-abc@weibo.com>",
                    status = "FAILED",
                    errorCategory = SmtpErrorCategory.TRANSIENT,
                    smtpResponseCode = 421,
                    errorDetail = "SMTP timeout"
                )
            )

        val ex = assertThrows(ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = input, safetyWarningConfirmed = true)
        }

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status)
        assertEquals("发送暂时失败，可安全重试", ex.reason)
        Mockito.verify(manualReplySendAttemptService, Mockito.never())
            .finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))
        val failure = invocationOf(manualReplySendAttemptService, "finalizeFailure")
        val failingPayload = failure.arguments[0] as ManualReplySendAttemptService.SendPayload
        assertNotNull(
            failingPayload.meetingEvent,
            "失败记录仍带本次校验产物，但失败事务绝不创建排期"
        )
        assertEquals(MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, failure.arguments[3])
    }

    // I-3：重提 SENT 请求与 UNKNOWN 重试都不产生第二次 SMTP，也不重新创建/刷新排期。
    @Test
    fun `meeting dedup sent and unknown claims never resend smtp or recreate the schedule`() {
        val input = meetingInput()
        val preview = previewFor(input)
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        val first = calendarRichSend(preview = preview, input = input, safetyWarningConfirmed = true)
        assertEquals("SENT", first.sendStatus)
        assertEquals(1, capturedMails.size, "首次发送恰好一次 SMTP")
        assertEquals(1, capturedPayloads.size)

        // 重提同一请求（真实收敛为 DEDUP_SENT 由 MeetingCalendarSendIntegrationTest 用真实库证明）
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(
                ManualReplySendAttemptService.ClaimedAttempt(
                    attemptId = 1L,
                    messageId = "<manual-rich-abc@weibo.com>",
                    result = ManualReplySendAttemptService.ClaimResult.DEDUP_SENT
                )
            )
        val dedup = calendarRichSend(preview = preview, input = input, safetyWarningConfirmed = true)
        assertEquals("SENT", dedup.sendStatus)
        assertEquals(1, capturedMails.size, "重提 SENT 请求不再 SMTP")
        assertEquals(1, capturedPayloads.size, "重提 SENT 请求不重新创建/刷新排期")
        Mockito.verify(manualReplySendAttemptService, Mockito.times(1))
            .finalizeSuccess(anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"))

        // UNKNOWN：保留既有「发送状态未知，请勿重复发送」语义，不 SMTP、不落成功记录。
        Mockito.`when`(manualReplySendAttemptService.prepareAndClaim(anyValue(sendPayload())))
            .thenReturn(
                ManualReplySendAttemptService.ClaimedAttempt(
                    attemptId = 1L,
                    messageId = "<manual-rich-abc@weibo.com>",
                    result = ManualReplySendAttemptService.ClaimResult.UNKNOWN
                )
            )
        val unknown = assertThrows(ResponseStatusException::class.java) {
            calendarRichSend(preview = preview, input = input, safetyWarningConfirmed = true)
        }
        assertEquals(HttpStatus.CONFLICT, unknown.status)
        assertTrue(requireNotNull(unknown.reason).startsWith("发送状态未知，请勿重复发送"))
        assertEquals(1, capturedMails.size, "UNKNOWN 重试不再次 SMTP")
        assertEquals(1, capturedPayloads.size, "UNKNOWN 重试不伪造成功排期")
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

    /** 失败路径 stub：finalizeFailure 返回失败记录 id（与生产 mock 一致，避免 null 返回值）。 */
    private fun stubFinalizeFailure(recordId: Long) {
        Mockito.`when`(
            manualReplySendAttemptService.finalizeFailure(
                anyValue(sendPayload()), Mockito.eq(1L), eqValue("<manual-rich-abc@weibo.com>"),
                anyValue(MailSendAttemptStatus.DELIVERY_UNKNOWN), anyValue(null)
            )
        ).thenReturn(recordId)
    }

    // ------------------------------------------------------------------
    // 06 (I-1/I-2/I-5): 两条人工发送入口的通用附件透传与收敛
    // ------------------------------------------------------------------

    private val attachmentId = "3f7c1a52-90de-4a1b-8b0e-6c2f5d4a1e77"
    private val otherUploaderSnapshotId = "0b6e2d18-4c77-4e21-9d5a-1f8a2b3c4d5e"

    /** 04 已解析产物：真实字节 + 快照（本文件只验证流动，原件校验在 FlowTest 用真实服务）。 */
    private fun attachmentFileSet(bytes: ByteArray = "会议资料".toByteArray(Charsets.UTF_8)): OutboundAttachmentFileSet {
        val snapshot = OutboundAttachmentSnapshot(
            schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
            id = attachmentId,
            filename = "会议资料.txt",
            contentType = "text/plain",
            byteLength = bytes.size.toLong(),
            sha256 = outboundSha256Hex(bytes)
        )
        return OutboundAttachmentFileSet(listOf(OutboundMailFile(snapshot, bytes)), listOf(snapshot))
    }

    private fun stubConversationNoCompleted() {
        stubConversationRequestCanonical()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(null)
    }

    // I-1：来信入口按会话身份解析附件，同一文件集合同时喂 payload 快照与 ComposedMail 载荷。
    @Test
    fun `inbound rich reply feeds the resolved attachment file set into payload and mail`() {
        val fileSet = attachmentFileSet()
        Mockito.`when`(outboundAttachmentService.resolveForSend(1L, listOf(attachmentId), "session-op"))
            .thenReturn(fileSet)
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        val result = service.sendManualRichReply(
            inboundProcessingId = 100L,
            senderAccountCode = null,
            subject = "Re: Test",
            htmlBody = "<p>Test</p>",
            textBody = "Test",
            // 请求体里的 operatorName 是旧审计显示值，绝不是附件归属身份。
            operatorName = "body-op",
            attachmentIds = listOf(attachmentId),
            authenticatedUsername = "session-op"
        )

        assertEquals("SENT", result.sendStatus)
        val payload = capturedPayloads.single()
        assertEquals(fileSet.snapshots, payload.outboundAttachments, "payload 快照 = 04 有序快照")
        val mail = capturedMails.single()
        assertEquals(1, mail.outboundAttachments.size)
        assertEquals(fileSet.snapshots.single(), mail.outboundAttachments.single().snapshot)
        assertArrayEquals(fileSet.files.single().bytes, mail.outboundAttachments.single().bytes)
    }

    // I-1：无来信入口同样按会话身份解析，且解析发生在幂等 claim 之前。
    @Test
    fun `conversation rich reply resolves attachments with the session identity before the claim`() {
        stubConversationAnchor(sentAnchor())
        stubConversationNoCompleted()
        val fileSet = attachmentFileSet()
        Mockito.`when`(outboundAttachmentService.resolveForSend(1L, listOf(attachmentId), "op"))
            .thenReturn(fileSet)
        val capturedMails = mutableListOf<ComposedMail>()
        val capturedPayloads = captureCalendarSend(capturedMails)

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op",
            attachmentIds = listOf(attachmentId), authenticatedUsername = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals(fileSet.snapshots, capturedPayloads.single().outboundAttachments)
        assertEquals(1, capturedMails.single().outboundAttachments.size)
        val order = Mockito.inOrder(outboundAttachmentService, manualReplySendAttemptService)
        order.verify(outboundAttachmentService).resolveForSend(1L, listOf(attachmentId), "op")
        order.verify(manualReplySendAttemptService).prepareAndClaim(anyValue(sendPayload()))
    }

    // I-1/I-5：无附件时完全不触碰 04 服务（既有短路路径逐字不变）。
    @Test
    fun `rich replies without attachment ids never touch the attachment service`() {
        stubConversationAnchor(sentAnchor())
        stubConversationNoCompleted()

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        Mockito.verifyNoInteractions(outboundAttachmentService)
        val payload = invocationOf(manualReplySendAttemptService, "prepareAndClaim")
            .arguments[0] as ManualReplySendAttemptService.SendPayload
        assertTrue(payload.outboundAttachments.isEmpty())
    }

    // I-1：04 的每一种附件失败（越权/缺失 404、损坏 409、非法 400、超量 413）都在 claim
    // 之前原样抛出 —— 0 attempt、0 SMTP，且绝不归类成投递失败/UNKNOWN。
    @Test
    fun `attachment resolution failures fail before claim for both entries without any delivery classification`() {
        val failures = listOf(
            "越权/缺失" to OutboundAttachmentException.notFound("附件不存在或不属于当前会话"),
            "原件损坏" to OutboundAttachmentException.conflict("附件原件摘要与元数据不一致（id=x）"),
            "非法 id" to OutboundAttachmentException.badRequest("通用附件 id 不能重复"),
            "超总量" to OutboundAttachmentException.payloadTooLarge("通用附件总计不能超过上限")
        )
        // 会话入口需先有真实 SENT 锚点才会走到附件解析（解析仍在 claim 之前）。
        stubConversationAnchor(sentAnchor())
        stubConversationNoCompleted()
        failures.forEach { (label, failure) ->
            Mockito.reset(outboundAttachmentService)
            Mockito.`when`(outboundAttachmentService.resolveForSend(1L, listOf(attachmentId), "op"))
                .thenThrow(failure)
            val inbound = assertThrows(OutboundAttachmentException::class.java) {
                service.sendManualRichReply(
                    inboundProcessingId = 100L, senderAccountCode = null,
                    subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                    operatorName = "op",
                    attachmentIds = listOf(attachmentId), authenticatedUsername = "op"
                )
            }
            assertEquals(failure.status, inbound.status, label)
            val conversation = assertThrows(OutboundAttachmentException::class.java) {
                service.sendConversationManualRichReply(
                    contactId = 1L, requestId = conversationRequestId, accountScope = null,
                    subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test",
                    operatorName = "op",
                    attachmentIds = listOf(attachmentId), authenticatedUsername = "op"
                )
            }
            assertEquals(failure.status, conversation.status, label)
            assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "$label 不得 claim")
            assertTrue(!hasInvocation(mailDeliveryService, "send"), "$label 不得 SMTP")
            assertTrue(!hasInvocation(manualReplySendAttemptService, "finalizeFailure"), "$label 不得写投递失败")
        }
    }

    // I-2：已 SENT 的 requestId 只读原记录与 04 元数据 —— 语义相同（含同内容重新上传的新 id）
    // 返回原 SENT；语义不同固定 409；两侧都无附件时保持既有短路。
    @Test
    fun `completed conversation request compares attachment semantics before returning the original sent`() {
        val bytes = "会议资料".toByteArray(Charsets.UTF_8)
        val original = attachmentFileSet(bytes).snapshots.single()
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(
                completedConversationReply().let {
                    it.copy(
                        mailRecord = it.mailRecord.copy(
                            outboundAttachmentsJson = OutboundAttachmentSnapshotCodec.serialize(listOf(original))
                        )
                    )
                }
            )
        stubConversationRequestCanonical()

        // 同一份内容重新上传得到的新 id：filename/type/size/hash 有序语义相同 → 原 SENT。
        Mockito.`when`(outboundAttachmentService.loadSnapshots(1L, listOf(otherUploaderSnapshotId), "op"))
            .thenReturn(listOf(original.copy(id = otherUploaderSnapshotId)))
        val same = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op",
            attachmentIds = listOf(otherUploaderSnapshotId), authenticatedUsername = "op"
        )
        assertEquals("SENT", same.sendStatus)
        assertEquals("<manual-rich-orig@weibo.com>", same.messageId)
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "相同附件不得 claim")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "相同附件不得再次 SMTP")
        assertTrue(!hasInvocation(mailRecordRepository, "findById"), "收敛命中不重读锚点")
        assertTrue(!hasInvocation(mailRecordRepository, "findLatestSentOutboundAnchor"), "收敛命中不重选锚点")

        // 内容不同（hash 不同）→ 04 固定 409 文案，不谎报新附件已发送。
        val changed = original.copy(
            id = otherUploaderSnapshotId,
            byteLength = original.byteLength + 1,
            sha256 = "b".repeat(64)
        )
        Mockito.`when`(outboundAttachmentService.loadSnapshots(1L, listOf(otherUploaderSnapshotId), "op"))
            .thenReturn(listOf(changed))
        val conflict = assertThrows(OutboundAttachmentException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = conversationRequestId, accountScope = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op",
                attachmentIds = listOf(otherUploaderSnapshotId), authenticatedUsername = "op"
            )
        }
        assertEquals(HttpStatus.CONFLICT, conflict.status)
        assertEquals("该请求已发送，附件与原请求不同，请发起新回复", conflict.message)

        // 原记录有附件、本次一个都没有 → 同样不同。
        val removed = assertThrows(OutboundAttachmentException::class.java) {
            service.sendConversationManualRichReply(
                contactId = 1L, requestId = conversationRequestId, accountScope = null,
                subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
            )
        }
        assertEquals(HttpStatus.CONFLICT, removed.status)
        assertTrue(!hasInvocation(manualReplySendAttemptService, "prepareAndClaim"), "附件不同不得 claim")
        assertTrue(!hasInvocation(mailDeliveryService, "send"), "附件不同不得 SMTP")
    }

    // I-2：原记录与本次都没有附件 → 既有短路路径逐字不变（不读 04 元数据）。
    @Test
    fun `completed conversation request without any attachments keeps the untouched short circuit`() {
        Mockito.`when`(manualReplySendAttemptService.findCompletedByRequestId(contact.orcidId, conversationRequestId))
            .thenReturn(completedConversationReply())
        stubConversationRequestCanonical()

        val result = service.sendConversationManualRichReply(
            contactId = 1L, requestId = conversationRequestId, accountScope = null,
            subject = "Re: Test", htmlBody = "<p>Test</p>", textBody = "Test", operatorName = "op"
        )

        assertEquals("SENT", result.sendStatus)
        assertEquals("<manual-rich-orig@weibo.com>", result.messageId)
        Mockito.verifyNoInteractions(outboundAttachmentService)
        assertTrue(!hasInvocation(mailDeliveryService, "send"))
    }
}
