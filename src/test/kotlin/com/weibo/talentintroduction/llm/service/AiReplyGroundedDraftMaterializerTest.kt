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

    private val facts = listOf(
        RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
        RequestFactItem(2, "- Deliverables?", listOf(2L), RequestGroundingStatus.PARTIAL),
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
    fun `valid json materializes numbered draft without raw json`() {
        val raw = """
            {"answers":[{"index":1,"answer":"Salary is competitive."},{"index":2,"answer":"Deliverables depend on project."}]}
        """.trimIndent()

        val result = materializer.materialize(raw, facts)
        assertTrue(result.valid)
        assertTrue(result.text.contains("1. Salary"))
        assertTrue(result.text.contains("Salary is competitive."))
        assertTrue(result.text.contains("2. Deliverables"))
        assertFalse(result.text.contains("\"answers\""))
        assertFalse(result.text.contains("3. Unknown"))
        assertFalse(result.text.contains("STATUS:"))
    }

    @Test
    fun `markdown fence is invalid`() {
        val raw = """
            ```json
            {"answers":[{"index":1,"answer":"A"},{"index":2,"answer":"B"}]}
            ```
        """.trimIndent()
        val result = materializer.materialize(raw, facts)
        assertFalse(result.valid)
        assertEquals(listOf(AiReplyGroundedDraftMaterializer.WARNING_STRUCTURED_RESPONSE_INVALID), result.warningCodes)
    }

    @Test
    fun `extra top-level field is invalid`() {
        val raw = """{"answers":[{"index":1,"answer":"A"},{"index":2,"answer":"B"}],"note":"x"}"""
        assertFalse(materializer.materialize(raw, facts).valid)
    }

    @Test
    fun `duplicate missing unknown and unsupported indexes are invalid`() {
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"A"},{"index":1,"answer":"B"},{"index":2,"answer":"C"}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"A"}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"A"},{"index":2,"answer":"B"},{"index":9,"answer":"C"}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"A"},{"index":2,"answer":"B"},{"index":3,"answer":"C"}]}""",
                facts
            ).valid
        )
    }

    @Test
    fun `blank answer or internal marker is invalid`() {
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"  "},{"index":2,"answer":"B"}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"STATUS: GROUNDED ok"},{"index":2,"answer":"B"}]}""",
                facts
            ).valid
        )
        assertFalse(
            materializer.materialize(
                """{"answers":[{"index":1,"answer":"This still needs confirmation on remaining details."},{"index":2,"answer":"B"}]}""",
                facts
            ).valid
        )
    }
}
