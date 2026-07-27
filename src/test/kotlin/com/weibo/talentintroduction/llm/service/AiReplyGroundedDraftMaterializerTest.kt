package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AiReplyGroundedDraftMaterializerTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val composer = AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)
    private val planner = AiReplyGroundedContentPlanner()
    private val materializer = AiReplyGroundedDraftMaterializer(ObjectMapper(), composer)

    private val intent1 = RequestIntentCoverage("finance.arrangements", "Financial", emptyList(), listOf(1L), "SUPPORTED", emptyList())
    private val intent2 = RequestIntentCoverage("role.deliverables", "Deliverables", emptyList(), listOf(2L), "SUPPORTED", emptyList())
    private val facts = listOf(
        RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED, intents = listOf(intent1)),
        RequestFactItem(2, "- Deliverables?", listOf(2L), RequestGroundingStatus.GROUNDED, intents = listOf(intent2))
    )

    init {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame("Dear Colleague,", QaReplyComposer.GREETING, QaReplyComposer.CLOSING, emptyList())
        )
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
    }

    private fun plan(actions: Set<AiReplyAction> = emptySet()) = planner.buildPlan(facts, actions)

    private fun minimal(
        claims: String = "[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary is competitive.\"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"Deliverables depend on the project.\"}]",
        actionText: String = "null"
    ) = "{\"claims\":$claims,\"actionText\":$actionText}"

    @Test
    fun `exact minimal protocol binds by plan order`() {
        val raw = minimal(
            claims = "[{\"claimKey\":\"r2:role.deliverables\",\"text\":\"Deliverables depend on the project.\"},{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary is competitive.\"}]"
        )
        val result = materializer.materialize(raw, facts, plan())
        assertTrue(result.valid, result.issues.toString())
        assertTrue(result.text.indexOf("Salary is competitive.") < result.text.indexOf("Deliverables depend"))
        assertEquals(emptyList<AiReplyValidationIssue>(), result.issues)
    }

    @Test
    fun `legacy deterministic envelope is rejected`() {
        val old = "{\"paragraphs\":[],\"claims\":[],\"missingFacts\":[],\"proposedAction\":{\"type\":\"NONE\",\"text\":null},\"requiresReview\":false}"
        val result = materializer.materialize(old, facts, plan())
        assertFalse(result.valid)
        assertEquals(AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID, result.issues.single().code)
    }

    @Test
    fun `missing duplicate unknown and extra claim fields fail with precise codes`() {
        val p = plan()
        assertEquals(AiReplyValidationCodes.CLAIM_SET_MISMATCH, materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"A\"}]"), facts, p
        ).issues.single().code)
        assertEquals(AiReplyValidationCodes.CLAIM_KEY_DUPLICATE, materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"A\"},{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"B\"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"C\"}]"), facts, p
        ).issues.first().code)
        assertEquals(AiReplyValidationCodes.CLAIM_KEY_UNKNOWN, materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"A\"},{\"claimKey\":\"r9:unknown\",\"text\":\"B\"}]"), facts, p
        ).issues.first().code)
        assertEquals(AiReplyValidationCodes.CLAIM_FIELDS_INVALID, materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"A\",\"note\":\"x\"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"B\"}]"), facts, p
        ).issues.first().code)
    }

    @Test
    fun `blank internal and markdown fenced claim text fail`() {
        val blank = materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\" \"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"B\"}]"), facts, plan()
        )
        assertEquals(AiReplyValidationCodes.CLAIM_TEXT_INVALID, blank.issues.single().code)
        val marker = materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"STATUS: GROUNDED\"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"B\"}]"), facts, plan()
        )
        assertEquals(AiReplyValidationCodes.CLAIM_TEXT_INVALID, marker.issues.single().code)
        val fence = materializer.materialize("```json\n${minimal()}\n```", facts, plan())
        assertEquals(AiReplyValidationCodes.JSON_INVALID, fence.issues.single().code)
    }

    @Test
    fun `action text is independent and must be allowed`() {
        val noAction = materializer.materialize(
            minimal(actionText = "\"Please send your CV.\""), facts, plan()
        )
        assertTrue(noAction.valid, noAction.issues.toString())
        val allowed = materializer.materialize(
            minimal(actionText = "\"Please send your CV.\""), facts, plan(setOf(AiReplyAction.REQUEST_MATERIALS))
        )
        assertTrue(allowed.valid, allowed.issues.toString())
        val hidden = materializer.materialize(
            minimal("[{\"claimKey\":\"r1:finance.arrangements\",\"text\":\"Salary info. Please send your CV.\"},{\"claimKey\":\"r2:role.deliverables\",\"text\":\"B\"}]"), facts, plan(setOf(AiReplyAction.REQUEST_MATERIALS))
        )
        assertTrue(hidden.valid, hidden.issues.toString())
    }

    @Test
    fun `actionText requires null or nonblank textual protocol value`() {
        listOf("1", "{}", "\"\"", "\"   \"").forEach { actionText ->
            val result = materializer.materialize(minimal(actionText = actionText), facts, plan())
            assertTrue(result.valid, actionText)
            assertTrue(result.issues.isEmpty(), actionText)
            assertFalse(result.actionTextValid, actionText)
        }
        val noAction = materializer.materialize(minimal(actionText = "null"), facts, plan())
        assertTrue(noAction.valid, noAction.issues.toString())
        assertTrue(noAction.actionTextValid)
    }

    @Test
    fun `diagnostic envelope is stable bounded and deduplicated`() {
        val distinct = List(25) { index ->
            AiReplyValidationDiagnostic(
                AiReplyValidationAttempt.INITIAL,
                AiReplyValidationStage.CLAIM,
                "CODE_$index",
                "r$index:key"
            )
        }
        val diagnostics = AiReplyValidationDiagnostics.from(distinct + distinct.take(3))
        assertEquals(25, diagnostics.total)
        assertTrue(diagnostics.truncated)
        assertEquals(20, diagnostics.items.size)
    }

    @Test
    fun `natural structure gate keeps safety rules`() {
        assertTrue(AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure("1. Program & eligibility\ntrust us"))
        assertFalse(AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure("Website: https://example.com"))
    }
}
