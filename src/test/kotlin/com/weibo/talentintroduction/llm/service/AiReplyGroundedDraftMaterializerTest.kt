package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class AiReplyGroundedDraftMaterializerTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val composer = AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)
    private val planner = AiReplyGroundedContentPlanner()
    private val materializer = AiReplyGroundedDraftMaterializer(ObjectMapper(), composer)

    private val supportedIntent = RequestIntentCoverage(
        intentKey = "finance.arrangements",
        title = "Financial arrangements",
        requiredCoverageKeys = emptyList(),
        evidenceRuleIds = listOf(1L),
        status = "SUPPORTED",
        missingEvidenceKeys = emptyList()
    )

    private val supportedIntent2 = RequestIntentCoverage(
        intentKey = "role.deliverables",
        title = "Deliverables",
        requiredCoverageKeys = emptyList(),
        evidenceRuleIds = listOf(2L),
        status = "SUPPORTED",
        missingEvidenceKeys = emptyList()
    )

    private val facts = listOf(
        RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED,
            intents = listOf(supportedIntent)),
        RequestFactItem(2, "- Deliverables?", listOf(2L), RequestGroundingStatus.PARTIAL,
            intents = listOf(supportedIntent2)),
        RequestFactItem(3, "- Unknown?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
    )

    init {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = "Dear Colleague,",
                greeting = QaReplyComposer.GREETING,
                closing = QaReplyComposer.CLOSING,
                ackOptions = emptyList()
            )
        )
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
    }

    private fun planFor(facts: List<RequestFactItem>): GroundedContentPlan =
        planner.buildPlan(facts, emptySet())

    private fun unifiedJson(
        claims: String,
        paragraphs: String = "[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:finance.arrangements\"]},{\"paragraphIndex\":2,\"claimKeys\":[\"r2:role.deliverables\"]}]",
        missingFacts: String = "[{\"requestIndex\":3,\"intentKeys\":[]}]",
        proposedAction: String = "{\"type\":\"NONE\",\"text\":null}",
        requiresReview: Boolean = true
    ): String {
        return "{\"paragraphs\":$paragraphs,\"claims\":$claims,\"missingFacts\":$missingFacts,\"proposedAction\":$proposedAction,\"requiresReview\":$requiresReview}"
    }

    @Test
    fun `valid per-claim unified json materializes natural paragraphs`() {
        val plan = planFor(facts)
        assertEquals(2, plan.claims.size)
        assertEquals("r1:finance.arrangements", plan.claims[0].claimKey)
        assertEquals("r2:role.deliverables", plan.claims[1].claimKey)
        assertEquals(2, plan.paragraphs.size)
        assertEquals(1, plan.missingFacts.size)
        assertEquals(3, plan.missingFacts[0].requestIndex)
        assertTrue(plan.requiresReview)

        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"Salary is competitive.\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"Deliverables depend on project.\",\"sourceIds\":[2]}]"
        val raw = unifiedJson(claims = claims)

        // Manual reproduction to debug
        val om = com.fasterxml.jackson.databind.ObjectMapper()
        val trimmed = raw.trim()
        assertFalse(trimmed.isBlank())
        assertFalse(trimmed.startsWith("```"))
        val root = om.readTree(trimmed)
        assertTrue(root.isObject)
        val fieldNames = root.fieldNames().asSequence().toSet()
        assertEquals(setOf("paragraphs", "claims", "missingFacts", "proposedAction", "requiresReview"), fieldNames)
        assertTrue(root.get("paragraphs").isArray)
        assertTrue(root.get("claims").isArray)
        assertTrue(root.get("missingFacts").isArray)
        assertTrue(root.get("proposedAction").isObject)
        assertTrue(root.get("requiresReview").isBoolean)
        assertEquals(plan.requiresReview, root.get("requiresReview").booleanValue())

        val result = materializer.materialize(raw, facts, plan)
        assertTrue(result.valid, "Expected valid but got invalid with warnings: ${result.warningCodes}")
        assertTrue(result.text.contains("Salary is competitive."))
        assertTrue(result.text.contains("Deliverables depend on project."))
        assertFalse(result.text.contains("finance.arrangements"))
        assertFalse(result.text.contains("role.deliverables"))
        assertFalse(result.text.contains("sourceRuleIds"))
        assertFalse(result.text.contains("\"sections\""))
        assertFalse(result.text.contains("STATUS:"))
    }

    @Test
    fun `markdown fence is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        val raw = "```json\n${unifiedJson(claims = claims)}\n```"
        val result = materializer.materialize(raw, facts, plan)
        assertFalse(result.valid)
        assertEquals(listOf(AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID), result.warningCodes)
    }

    @Test
    fun `extra top-level field is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        val raw = "{\"paragraphs\":[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:finance.arrangements\"]},{\"paragraphIndex\":2,\"claimKeys\":[\"r2:role.deliverables\"]}],\"claims\":$claims,\"missingFacts\":[],\"proposedAction\":{\"type\":\"NONE\",\"text\":null},\"requiresReview\":true,\"note\":\"x\"}"
        assertFalse(materializer.materialize(raw, facts, plan).valid)
    }

    @Test
    fun `duplicate claim key is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"B\",\"sourceIds\":[1]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `unknown claim key is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r99:ip.arrangements\",\"requestIndex\":99,\"intentKey\":\"ip.arrangements\",\"text\":\"X\",\"sourceIds\":[1]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims, paragraphs = "[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:finance.arrangements\",\"r99:ip.arrangements\"]}]"), facts, plan).valid
        )
    }

    @Test
    fun `missing expected claim is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `blank claim text or internal marker is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"  \",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
        val claimsMarker = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"STATUS: GROUNDED ok\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claimsMarker), facts, plan).valid
        )
    }

    @Test
    fun `empty sourceIds is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `sourceIds outside evidence set is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[999]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `unsupported intent key is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:ip.arrangements\",\"requestIndex\":1,\"intentKey\":\"ip.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `request with two supported intents must output both claims`() {
        val twoIntentFacts = listOf(
            RequestFactItem(1, "- Compound?", listOf(1L, 2L), RequestGroundingStatus.GROUNDED,
                intents = listOf(supportedIntent, supportedIntent2))
        )
        val plan = planFor(twoIntentFacts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]}]"
        assertFalse(
            materializer.materialize(
                unifiedJson(claims = claims, paragraphs = "[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:finance.arrangements\"]}]", requiresReview = false),
                twoIntentFacts, plan
            ).valid
        )
    }

    @Test
    fun `request with two supported intents passes when both output`() {
        val twoIntentFacts = listOf(
            RequestFactItem(1, "- Compound?", listOf(1L, 2L), RequestGroundingStatus.GROUNDED,
                intents = listOf(supportedIntent, supportedIntent2))
        )
        val plan = planFor(twoIntentFacts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r1:role.deliverables\",\"requestIndex\":1,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertTrue(
            materializer.materialize(
                unifiedJson(claims = claims, paragraphs = "[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:finance.arrangements\",\"r1:role.deliverables\"]}]", missingFacts = "[]", requiresReview = false),
                twoIntentFacts, plan
            ).valid
        )
    }

    @Test
    fun `mixed type sourceIds is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1,\"2\",3]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `float sourceIds is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1.5]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `float requestIndex is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1.5,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims), facts, plan).valid
        )
    }

    @Test
    fun `wrong requiresReview is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims, requiresReview = false), facts, plan).valid
        )
    }

    @Test
    fun `wrong missingFacts is invalid`() {
        val plan = planFor(facts)
        val claims = "[{\"claimKey\":\"r1:finance.arrangements\",\"requestIndex\":1,\"intentKey\":\"finance.arrangements\",\"text\":\"A\",\"sourceIds\":[1]},{\"claimKey\":\"r2:role.deliverables\",\"requestIndex\":2,\"intentKey\":\"role.deliverables\",\"text\":\"B\",\"sourceIds\":[2]}]"
        val planMissing = plan.missingFacts
        val wrongMissing = if (planMissing.isNotEmpty()) "[{\"requestIndex\":99,\"intentKeys\":[\"wrong\"]}]" else "[{\"requestIndex\":1,\"intentKeys\":[\"fake\"]}]"
        assertFalse(
            materializer.materialize(unifiedJson(claims = claims, missingFacts = wrongMissing), facts, plan).valid
        )
    }

    @Test
    fun `natural structure gate catches marketing phrases`() {
        assertTrue(
            AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(
                "Thank you. We are delighted to have you. trust us and rest assured this is a unique opportunity."
            )
        )
    }

    @Test
    fun `natural structure gate allows website domains in configured closing`() {
        val closing = """
            Website: http://www.qingfeitalent.com/
            LinkedIn: http://www.linkedin.com/in/yuyun-chou-48899a392
        """.trimIndent()

        assertFalse(AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(closing))
    }

    @Test
    fun `natural structure gate still catches known internal intent labels`() {
        assertTrue(
            AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(
                "Internal routing: finance.arrangements"
            )
        )
        assertTrue(
            AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(
                "Internal routing: general.answer"
            )
        )
    }
}
