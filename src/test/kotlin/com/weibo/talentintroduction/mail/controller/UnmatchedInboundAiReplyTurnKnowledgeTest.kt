package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.llm.service.AiReplyContext
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiReplyReviewItem
import com.weibo.talentintroduction.llm.service.InitialDraftAuthorityResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyGenerationState
import com.weibo.talentintroduction.llm.service.AiReplyMode
import com.weibo.talentintroduction.llm.service.AiReplyModel
import com.weibo.talentintroduction.llm.service.RequestFactItem
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
import com.weibo.talentintroduction.llm.service.LlmStitchService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.Optional

class UnmatchedInboundAiReplyTurnKnowledgeTest {
    private val unmatchedInboundMailService = Mockito.mock(UnmatchedInboundMailService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val pendingMailOperationService = Mockito.mock(PendingMailOperationService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val llmStitchService = Mockito.mock(LlmStitchService::class.java)
    private val autoReplyPreviewService = Mockito.mock(AutoReplyPreviewService::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyDraftPreviewService = Mockito.mock(AiReplyDraftPreviewService::class.java) { invocation ->
        if (invocation.method.name == "preview") {
            AiReplyDraftPreviewService.PreviewResult(
                renderedText = invocation.getArgument(0),
                warningCodes = emptyList()
            )
        } else {
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val aiReplyContextBuilder = AiReplyContextBuilder()
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val aiReplyReviewAuditService = Mockito.mock(AiReplyReviewAuditService::class.java)

    private val controller = UnmatchedInboundMailController(
        unmatchedInboundMailService,
        expertEmailAliasService,
        expertContactRepository,
        pendingMailOperationService,
        operatorActionLogService,
        llmStitchService,
        autoReplyPreviewService,
        replySnippetService,
        aiReplyDraftService,
        aiReplyDraftPreviewService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService,
        aiReplyReviewAuditService
    )

    private val contact = ExpertContact(
        id = 10L,
        campaignId = 1L,
        orcidId = "0000-0000-0000-0001",
        expertName = "Dr. Test",
        expertEmail = "expert@test.com",
        currentStatus = "WAITING_REPLY"
    )

    init {
        Mockito.lenient().doReturn(InitialDraftAuthorityResult(available = true, draftIdentity = "default-test-id"))
            .`when`(aiReplyReviewAuditService).recordInitialDraft(
                anyV(0L),
                anyV(0L),
                anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)),
                Mockito.any()
            )
    }

    private fun <T> anyV(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    @Test
    fun `aiReplyTurn injects training knowledge into expert profile via context service`() {
        val detail = InboundMailProcessing(
            id = 1L,
            senderAccountCode = "a1",
            imapUid = 1L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Question",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(1L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello"))
            .thenReturn("Topic: office mascot\nAnswer: QINGFEI-PANDA")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)

        val expectedProfile = "Name: Dr. Test\nTraining knowledge base:\nTopic: office mascot\nAnswer: QINGFEI-PANDA"
        val expectedContext = AiReplyContext(
            profileText = expectedProfile,
            mailHistory = "",
            contextWarnings = emptyList()
        )
        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(
                contact,
                emptyList(),
                "Hello",
                "Topic: office mascot\nAnswer: QINGFEI-PANDA"
            )
        ).thenReturn(expectedContext)

        var capturedProfile: String? = null
        var capturedWarnings: List<String>? = null
        Mockito.doAnswer { invocation ->
            capturedProfile = invocation.getArgument(4)
            capturedWarnings = invocation.getArgument(7)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList(),
            Mockito.any()
        )

        controller.aiReplyTurn(1L, AiReplyTurnRequest())

        assertTrue(capturedProfile!!.contains("Training knowledge base:"))
        assertTrue(capturedProfile!!.contains("QINGFEI-PANDA"))
        assertEquals(emptyList<String>(), capturedWarnings)
        Mockito.verify(aiTrainingQaService).buildKnowledgeContext("Hello")
    }

    @Test
    fun `aiReplyTurn response includes new coverage fields`() {
        val detail = InboundMailProcessing(
            id = 2L,
            senderAccountCode = "a1",
            imapUid = 2L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Query",
            body = "Question text",
            cleanedBody = "Question text",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(2L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L))
            .thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Question text")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)

        // Use exact values (not matchers) to avoid Kotlin non-null parameter check issues
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Question text", "")
        ).thenReturn(
            AiReplyContext(
                profileText = "Name: Dr. Test",
                mailHistory = "",
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
            )
        )

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "reply draft",
                usedLlm = false,
                qaRuleIds = listOf(5L),
                mode = AiReplyMode.QA_MATCHED,
                requestCount = 1,
                groundedRequestCount = 0,
                unsupportedRequests = listOf("research scope"),
                contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
                fewShotDialogRefs = listOf("DIALOG_1"),
                requestFacts = listOf(
                    RequestFactItem(
                        index = 1,
                        requestText = "research scope",
                        factRuleIds = emptyList(),
                        status = RequestGroundingStatus.UNSUPPORTED
                    )
                ),
                draftReadiness = AiReplyDraftReadiness.BLOCKED
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(),
            Mockito.anyList(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(),
            Mockito.anyBoolean(),
            Mockito.anyList(),
            Mockito.any()
        )

        val response = controller.aiReplyTurn(2L, AiReplyTurnRequest())

        assertEquals("reply draft", response.draftText)
        assertEquals("reply draft", response.renderedDraftText)
        assertEquals(listOf(5L), response.qaRuleIds)
        assertEquals("QA_MATCHED", response.mode)
        assertEquals(1, response.requestCount)
        assertEquals(0, response.groundedRequestCount)
        assertEquals(listOf("research scope"), response.unsupportedRequests)
        assertEquals(listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"), response.contextWarnings)
        assertEquals(listOf("DIALOG_1"), response.injectedDialogRefs)
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH.name, response.selectedModel)
        assertEquals("BLOCKED", response.draftReadiness)
        Mockito.verify(aiReplyDraftPreviewService).preview("reply draft", contact, "a1")
        assertEquals(
            listOf(
                RequestCoverageItem(
                    index = 1,
                    requestText = "research scope",
                    status = "UNSUPPORTED",
                    factRuleIds = emptyList()
                )
            ),
            response.requestCoverage
        )
    }

    @Test
    fun `aiReplyTurn maps all shared AiReplyDraftResult fields to response identically`() {
        val detail = InboundMailProcessing(
            id = 5L,
            senderAccountCode = "a1",
            imapUid = 5L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Multi-question",
            body = "Multiple questions",
            cleanedBody = "Multiple questions",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(5L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Multiple questions")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(
            aiReplyContextService.build(contact, emptyList(), "Multiple questions", "")
        ).thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        val sourceResult = AiReplyDraftResult(
            draftText = "Grounded reply body",
            usedLlm = true,
            qaRuleIds = listOf(1L, 3L),
            mode = AiReplyMode.QA_GROUNDED,
            requestCount = 2,
            groundedRequestCount = 1,
            unsupportedRequests = listOf("research scope question"),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
            fewShotDialogRefs = listOf("DIALOG_42"),
            selectedModel = AiReplyModel.DEEPSEEK_V4_PRO.name,
            requestFacts = listOf(
                RequestFactItem(1, "salary question", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "research scope question", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            generationState = AiReplyGenerationState.LLM_USED,
            draftReadiness = AiReplyDraftReadiness.BLOCKED
        )
        Mockito.doReturn(sourceResult).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )
        Mockito.`when`(
            aiReplyDraftPreviewService.preview("Grounded reply body", contact, "a1")
        ).thenReturn(
            AiReplyDraftPreviewService.PreviewResult(
                renderedText = "Grounded reply rendered",
                warningCodes = listOf("AI_REPLY_PREVIEW_INVALID_PLACEHOLDER")
            )
        )

        val response = controller.aiReplyTurn(5L, AiReplyTurnRequest())

        assertEquals(sourceResult.draftText, response.draftText)
        assertEquals("Grounded reply rendered", response.renderedDraftText)
        assertEquals(sourceResult.mode.name, response.mode)
        assertEquals(sourceResult.qaRuleIds, response.qaRuleIds)
        assertEquals(sourceResult.requestCount, response.requestCount)
        assertEquals(sourceResult.groundedRequestCount, response.groundedRequestCount)
        assertEquals(sourceResult.unsupportedRequests, response.unsupportedRequests)
        assertEquals(
            listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT", "AI_REPLY_PREVIEW_INVALID_PLACEHOLDER"),
            response.contextWarnings
        )
        assertEquals(sourceResult.fewShotDialogRefs, response.injectedDialogRefs)
        assertEquals(sourceResult.selectedModel, response.selectedModel)
        assertEquals(sourceResult.generationState.name, response.generationState)
        assertEquals("BLOCKED", response.draftReadiness)
        assertEquals(
            sourceResult.requestFacts.map {
                RequestCoverageItem(it.index, it.requestText, it.status.name, it.factRuleIds)
            },
            response.requestCoverage
        )
        // Research flag stays internal on RequestFactItem; response DTO fields: index, requestText, status, factRuleIds, intents
        assertEquals(
            setOf("index", "requestText", "status", "factRuleIds", "intents"),
            RequestCoverageItem::class.java.declaredFields
                .filter { !it.isSynthetic && it.name != "Companion" }
                .map { it.name }
                .toSet()
        )
        Mockito.verify(mailRecordRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `aiReplyTurn throws when expertContactId is null`() {
        val detail = InboundMailProcessing(
            id = 3L,
            senderAccountCode = "a1",
            imapUid = 3L,
            messageId = null,
            fromEmail = "unknown@test.com",
            subject = "?",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = null
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(3L)).thenReturn(detail)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurn(3L, AiReplyTurnRequest())
        }
    }
    @Test
    fun `aiReplyTurn accepts model enum and rejects unknown`() {
        val detail = InboundMailProcessing(
            id = 9L,
            senderAccountCode = "a1",
            imapUid = 9L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Model",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(9L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        var capturedModel: String? = "unset"
        Mockito.doAnswer { invocation ->
            capturedModel = invocation.getArgument(8)
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM,
                selectedModel = AiReplyModel.DEEPSEEK_V4_PRO.name
            )
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )

        val response = controller.aiReplyTurn(9L, AiReplyTurnRequest(model = "DEEPSEEK_V4_PRO"))
        assertEquals("DEEPSEEK_V4_PRO", capturedModel)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, response.selectedModel)

        // unknown model is validated inside DraftService; controller passes through
        capturedModel = "unset"
        Mockito.doAnswer { invocation ->
            capturedModel = invocation.getArgument(8)
            throw IllegalArgumentException("Unknown AI reply model: DEEPSEEK_UNKNOWN")
        }.`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            controller.aiReplyTurn(9L, AiReplyTurnRequest(model = "DEEPSEEK_UNKNOWN"))
        }
        assertEquals("DEEPSEEK_UNKNOWN", capturedModel)
    }

    @Test
    fun `aiReplyTurn records initial draft on first turn`() {
        val detail = InboundMailProcessing(
            id = 6L,
            senderAccountCode = "a1",
            imapUid = 6L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "First turn",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(6L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )

        Mockito.doReturn(InitialDraftAuthorityResult(available = true, draftIdentity = "uuid-test-123"))
            .`when`(aiReplyReviewAuditService).recordInitialDraft(
                anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("op")
            )

        val response = controller.aiReplyTurn(6L, AiReplyTurnRequest(operatorName = "op"))

        assertEquals(true, response.draftAuthorityAvailable)
        assertEquals("uuid-test-123", response.draftIdentity)
        Mockito.verify(aiReplyReviewAuditService).recordInitialDraft(
            anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("op")
        )
    }

    @Test
    fun `aiReplyTurn returns empty draft and draftAuthorityAvailable false when audit fails`() {
        val detail = InboundMailProcessing(
            id = 60L,
            senderAccountCode = "a1",
            imapUid = 60L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "First turn fail",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(60L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft body here",
                usedLlm = true,
                qaRuleIds = listOf(1L),
                mode = AiReplyMode.QA_GROUNDED
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )

        Mockito.doReturn(InitialDraftAuthorityResult(available = false, draftIdentity = null))
            .`when`(aiReplyReviewAuditService).recordInitialDraft(
                anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("op")
            )

        val response = controller.aiReplyTurn(60L, AiReplyTurnRequest(operatorName = "op"))

        assertEquals("", response.draftText)
        assertEquals("", response.renderedDraftText)
        assertNull(response.draftIdentity)
        assertEquals(false, response.draftAuthorityAvailable)
        assertTrue(response.contextWarnings.contains("AI_REPLY_AUDIT_UNAVAILABLE"))
        Mockito.verify(aiReplyDraftPreviewService, Mockito.never()).preview(Mockito.anyString(), anyV(contact), Mockito.anyString())
    }

    @Test
    fun `aiReplyTurn does NOT record initial draft on subsequent turn`() {
        val detail = InboundMailProcessing(
            id = 7L,
            senderAccountCode = "a1",
            imapUid = 7L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Second turn",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(7L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(10L)).thenReturn(emptyList())
        Mockito.`when`(aiTrainingQaService.buildKnowledgeContext("Hello")).thenReturn("")
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.`when`(aiReplyContextService.build(contact, emptyList(), "Hello", ""))
            .thenReturn(AiReplyContext(profileText = "Name: Dr. Test", mailHistory = "", contextWarnings = emptyList()))

        Mockito.doReturn(
            AiReplyDraftResult(
                draftText = "draft v2",
                usedLlm = false,
                qaRuleIds = emptyList(),
                mode = AiReplyMode.FREE_FORM
            )
        ).`when`(aiReplyDraftService).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )

        Mockito.doReturn(InitialDraftAuthorityResult(available = true, draftIdentity = "existing-id"))
            .`when`(aiReplyReviewAuditService).resolveCurrentDraftAuthority(7L)

        val response = controller.aiReplyTurn(7L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals("existing-id", response.draftIdentity)
        assertEquals(true, response.draftAuthorityAvailable)
        assertEquals("draft v2", response.draftText)
        Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordInitialDraft(
            anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("")
        )
    }

    @Test
    fun `aiReplyTurn rejects forged continuation when no current authority`() {
        val detail = InboundMailProcessing(
            id = 71L,
            senderAccountCode = "a1",
            imapUid = 71L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Forged continuation",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(71L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(false)
        Mockito.doReturn(InitialDraftAuthorityResult(available = false, draftIdentity = null))
            .`when`(aiReplyReviewAuditService).resolveCurrentDraftAuthority(71L)

        val response = controller.aiReplyTurn(71L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals("", response.draftText)
        assertEquals("", response.renderedDraftText)
        assertEquals(false, response.draftAuthorityAvailable)
        assertNull(response.draftIdentity)
        assertTrue(response.contextWarnings.contains("AI_REPLY_AUDIT_UNAVAILABLE"))
        Mockito.verify(aiReplyDraftService, Mockito.never()).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )
        Mockito.verify(aiReplyDraftPreviewService, Mockito.never()).preview(Mockito.anyString(), anyV(contact), Mockito.anyString())
        Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordInitialDraft(
            anyV(0L), anyV(0L), anyV(AiReplyDraftResult("", false, emptyList(), AiReplyMode.FREE_FORM)), anyV("")
        )
    }

    @Test
    fun `aiReplyTurn rejects continuation when current authority is corrupt`() {
        val detail = InboundMailProcessing(
            id = 72L,
            senderAccountCode = "a1",
            imapUid = 72L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Corrupt continuation",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(72L)).thenReturn(detail)
        Mockito.`when`(expertContactRepository.findById(10L)).thenReturn(Optional.of(contact))
        Mockito.`when`(llmStitchService.isEnabled()).thenReturn(true)
        Mockito.doReturn(InitialDraftAuthorityResult(available = false, draftIdentity = null))
            .`when`(aiReplyReviewAuditService).resolveCurrentDraftAuthority(72L)

        val response = controller.aiReplyTurn(72L, AiReplyTurnRequest(
            turns = listOf(AiReplyTurnDto(assistantDraft = "v1", operatorInstruction = "fix"))
        ))

        assertEquals(false, response.draftAuthorityAvailable)
        assertEquals("", response.draftText)
        Mockito.verify(aiReplyDraftService, Mockito.never()).generate(
            Mockito.anyString(), Mockito.anyList(), Mockito.any(), Mockito.any(),
            Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.anyList(), Mockito.any()
        )
    }

    @Test
    fun `reviewEvent endpoint records SEND_BLOCKED for valid event type`() {
        val detail = InboundMailProcessing(
            id = 8L,
            senderAccountCode = "a1",
            imapUid = 8L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Block",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(8L)).thenReturn(detail)

        val items = listOf(
            AiReplyReviewItem("1:role.deliverables", 1, "role.deliverables", "MISSING", listOf("deliverables"))
        )
        controller.recordReviewEvent(
            8L,
            ReviewEventRequest(eventType = "SEND_BLOCKED", operatorName = "op", unresolvedItems = items)
        )

        Mockito.verify(aiReplyReviewAuditService).recordSendBlocked(
            anyV(0L), anyV(0L), anyV(emptyList<AiReplyReviewItem>()), anyV("op")
        )
    }

    @Test
    fun `reviewEvent endpoint rejects unknown event types`() {
        val detail = InboundMailProcessing(
            id = 8L,
            senderAccountCode = "a1",
            imapUid = 8L,
            messageId = null,
            fromEmail = "expert@test.com",
            subject = "Block",
            body = "Hello",
            cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "PENDING",
            processReason = "UNMATCHED",
            expertContactId = 10L
        )
        Mockito.`when`(unmatchedInboundMailService.getDetail(8L)).thenReturn(detail)

        assertThrows(IllegalArgumentException::class.java) {
            controller.recordReviewEvent(
                8L,
                ReviewEventRequest(eventType = "UNKNOWN_TYPE", operatorName = "op")
            )
        }
        Mockito.verify(aiReplyReviewAuditService, Mockito.never()).recordSendBlocked(
            anyV(0L), anyV(0L), anyV(emptyList<AiReplyReviewItem>()), anyV("")
        )
    }
}
