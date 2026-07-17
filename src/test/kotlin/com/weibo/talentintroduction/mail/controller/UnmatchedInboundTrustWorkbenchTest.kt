package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.PendingManualRichReplyRequest
import com.weibo.talentintroduction.mail.service.PendingQaReplyRequest
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.TrustWorkbenchEvaluateResult
import com.weibo.talentintroduction.mail.service.TrustWorkbenchSuggestResult
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.GapItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class UnmatchedInboundTrustWorkbenchTest {
    private val unmatchedInboundMailService = Mockito.mock(UnmatchedInboundMailService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val pendingMailOperationService = Mockito.mock(PendingMailOperationService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val llmProperties = LlmProperties(enabled = true)
    private val autoReplyPreviewService = Mockito.mock(AutoReplyPreviewService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyDraftPreviewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
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
        llmProperties,
        autoReplyPreviewService,
        aiReplyDraftService,
        aiReplyDraftPreviewService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService,
        aiReplyReviewAuditService
    )

    private val suggestResult = TrustWorkbenchSuggestResult(
        suggestedRuleIds = listOf(10L),
        suggestedRules = emptyList(),
        rulesByCategory = listOf(
            CategoryRulesGroup(
                categoryId = 1L,
                categoryCode = "FUNDING",
                categoryName = "Funding",
                composeOrder = 10,
                rules = emptyList()
            )
        ),
        gapItems = emptyList(),
        gapDetected = false,
        matchedCategoryIds = listOf(1L),
        draftReadiness = "READY",
        requestCoverage = listOf(
            RequestCoverageItem(1, "Can I work remotely?", "GROUNDED", listOf(10L))
        ),
        inboundText = "Can I work remotely?"
    )

    @Test
    fun `deprecated qa-reply returns 410 with migration message`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            controller.sendQaReply(1L, PendingQaReplyRequest(qaRuleId = 10L, senderAccountCode = null, operatorName = "op"))
        }
        assertEquals(HttpStatus.GONE, ex.status)
        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    @Test
    fun `deprecated composed-reply returns 410 with migration message`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            controller.sendComposedReply(
                1L,
                com.weibo.talentintroduction.mail.service.ComposedReplyRequest(
                    qaRuleIds = listOf(10L),
                    overrideTextBody = null,
                    senderAccountCode = null,
                    operatorName = "op"
                )
            )
        }
        assertEquals(HttpStatus.GONE, ex.status)
        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    @Test
    fun `deprecated composed-reply polish returns 410 with migration message`() {
        val ex = assertThrows(ResponseStatusException::class.java) {
            controller.polishComposedReply(
                1L,
                DeprecatedPolishDraftRequest(
                    qaRuleIds = listOf(10L),
                    operatorInstruction = "shorter"
                )
            )
        }
        assertEquals(HttpStatus.GONE, ex.status)
        assertTrue(ex.reason!!.contains("Use trust workbench and manual-rich-reply"))
        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    @Test
    fun `suggest returns trust workbench payload with llmEnabled`() {
        Mockito.`when`(pendingMailOperationService.suggestComposedReply(5L)).thenReturn(suggestResult)

        val response = controller.suggestComposedReply(5L)

        assertEquals(listOf(10L), response.suggestedRuleIds)
        assertEquals("READY", response.draftReadiness)
        assertEquals(true, response.llmEnabled)
        assertEquals(1, response.requestCoverage.size)
    }

    @Test
    fun `evaluate returns server canonical fact ids and readiness`() {
        Mockito.`when`(pendingMailOperationService.evaluateComposedReply(5L, listOf(10L)))
            .thenReturn(
                TrustWorkbenchEvaluateResult(
                    canonicalFactIds = listOf(10L),
                    suggestedFactIds = listOf(10L, 20L),
                    draftReadiness = "NEEDS_REVIEW",
                    requestCoverage = suggestResult.requestCoverage,
                    gapDetected = false
                )
            )

        val response = controller.evaluateComposedReply(
            5L,
            com.weibo.talentintroduction.mail.service.ComposedReplyEvaluateRequest(factRuleIds = listOf(10L))
        )

        assertEquals(listOf(10L), response.canonicalFactIds)
        assertEquals(listOf(10L, 20L), response.suggestedFactIds)
        assertEquals("NEEDS_REVIEW", response.draftReadiness)
    }

    @Test
    fun `manual rich reply still delegates to service`() {
        Mockito.`when`(
            pendingMailOperationService.sendManualRichReply(
                inboundProcessingId = 5L,
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Hello</p>",
                textBody = "Hello",
                operatorName = "op",
                qaRuleIds = listOf(10L),
                suggestedRuleIds = listOf(99L),
                ackSnippetId = null,
                edited = false,
                freeTextPreview = null,
                useVariants = false,
                templateTextBody = null,
                templateHtmlBody = null
            )
        ).thenReturn(
            PendingMailSendResult(
                contactId = 1L,
                senderAccountCode = "sender-1",
                mailType = "MANUAL_RICH_REPLY",
                subject = "Re: Test",
                sendStatus = "SUCCESS",
                messageId = "out-1"
            )
        )

        val result = controller.sendManualRichReply(
            5L,
            PendingManualRichReplyRequest(
                senderAccountCode = null,
                subject = "Re: Test",
                htmlBody = "<p>Hello</p>",
                textBody = "Hello",
                operatorName = "op",
                qaRuleIds = listOf(10L),
                suggestedRuleIds = listOf(99L),
                edited = false
            )
        )

        assertEquals("SUCCESS", result.sendStatus)
    }
}
