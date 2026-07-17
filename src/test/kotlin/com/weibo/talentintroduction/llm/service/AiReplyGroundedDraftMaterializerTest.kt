package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaReplyComposer
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

    @Test
    fun `valid per-intent json materializes natural paragraphs without fixed section titles`() {
        val raw = """
            {"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"Salary is competitive.","sourceRuleIds":[1]}]},{"requestIndex":2,"answers":[{"intentKey":"role.deliverables","answer":"Deliverables depend on project.","sourceRuleIds":[2]}]}]}
        """.trimIndent()

        val result = materializer.materialize(raw, facts)
        assertTrue(result.valid)
        assertTrue(result.text.contains("Salary is competitive."))
        assertTrue(result.text.contains("Deliverables depend on project."))
        assertTrue(result.text.indexOf("Salary is competitive.") < result.text.indexOf("Deliverables depend on project."))
        assertFalse(result.text.contains("1. Financial arrangements"))
        assertFalse(result.text.contains("2. Deliverables"))
        assertFalse(result.text.contains("finance.arrangements"))
        assertFalse(result.text.contains("role.deliverables"))
        assertFalse(result.text.contains("sourceRuleIds"))
        assertFalse(result.text.contains("RULE 1"))
        assertFalse(result.text.contains("\"sections\""))
        assertFalse(result.text.contains("STATUS:"))
    }

    @Test
    fun `markdown fence is invalid`() {
        val raw = """
            ```json
            {"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}]}
            ```
        """.trimIndent()
        val result = materializer.materialize(raw, facts)
        assertFalse(result.valid)
        assertEquals(listOf(AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID), result.warningCodes)
    }

    @Test
    fun `extra top-level field is invalid`() {
        val raw = """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}],"note":"x"}"""
        assertFalse(materializer.materialize(raw, facts).valid)
    }

    @Test
    fun `duplicate request index is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]},{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"B","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `unsupported request index in section is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]},{"requestIndex":3,"answers":[{"intentKey":"general.answer","answer":"X","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `missing expected request index is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `unknown unknown request index is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]},{"requestIndex":99,"answers":[{"intentKey":"general.answer","answer":"X","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `blank answer or internal marker is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"  ","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"STATUS: GROUNDED ok","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `empty sourceRuleIds is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `sourceRuleIds outside evidence set is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[999]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `unsupported intent key is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"ip.arrangements","answer":"A","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `request with two supported intents must output both`() {
        val twoIntentFacts = listOf(
            RequestFactItem(1, "- Compound?", listOf(1L, 2L), RequestGroundingStatus.GROUNDED,
                intents = listOf(supportedIntent, supportedIntent2))
        )
        // Model outputs only one of two supported intents → invalid
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}]}""",
                twoIntentFacts
            ).valid
        )
    }

    @Test
    fun `request with two supported intents passes when both output`() {
        val twoIntentFacts = listOf(
            RequestFactItem(1, "- Compound?", listOf(1L, 2L), RequestGroundingStatus.GROUNDED,
                intents = listOf(supportedIntent, supportedIntent2))
        )
        assertTrue(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]},{"intentKey":"role.deliverables","answer":"B","sourceRuleIds":[2]}]}]}""",
                twoIntentFacts
            ).valid
        )
    }

    @Test
    fun `mixed type sourceRuleIds is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1,"2",3]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `float sourceRuleIds is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1.5]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `float requestIndex is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1.5,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `out of range requestIndex is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":4294967297,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[1]}]}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `out of range sourceRuleId is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"sections":[{"requestIndex":1,"answers":[{"intentKey":"finance.arrangements","answer":"A","sourceRuleIds":[18446744073709551617]}]}]}""",
                facts
            ).valid
        )
    }
}
