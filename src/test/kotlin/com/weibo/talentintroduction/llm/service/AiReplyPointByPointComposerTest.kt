package com.weibo.talentintroduction.llm.service

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

class AiReplyPointByPointComposerTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val composer = AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)

    private fun stubFrame(
        salutation: String? = "Dear \${expertName|Professor},",
        greeting: String? = QaReplyComposer.GREETING,
        closing: String? = QaReplyComposer.CLOSING
    ) {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = salutation,
                greeting = greeting,
                closing = closing,
                ackOptions = emptyList()
            )
        )
        Mockito.`when`(replySnippetService.resolveAck(null)).thenReturn(null)
    }

    private fun rule(id: Long, body: String) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "k$id",
        replyBody = body,
        replySubject = "Re $id",
        enabled = true
    )

    @Test
    fun `composeFallback emits frame numbered grounded sections and closing`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary facts")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Visa facts")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "- What is salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Visa process?", listOf(2L), RequestGroundingStatus.GROUNDED)
            )
        )

        assertTrue(text.startsWith("Dear \${expertName|Professor},"))
        assertTrue(text.contains(QaReplyComposer.GREETING))
        assertTrue(text.contains("1. What is salary"))
        assertTrue(text.contains("2. Visa process"))
        assertTrue(text.contains("Salary facts"))
        assertTrue(text.contains("Visa facts"))
        assertTrue(text.contains(QaReplyComposer.CLOSING))
    }

    @Test
    fun `unsupported and research items are omitted from fallback`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary only")))
        Mockito.`when`(qaRuleRepository.findById(7L)).thenReturn(Optional.of(rule(7, "Scope facts")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(
                    2,
                    "- Research match?",
                    listOf(7L),
                    RequestGroundingStatus.GROUNDED,
                    requiresResearchContext = true
                ),
                RequestFactItem(3, "- Unknown?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )

        assertTrue(text.contains("1. Salary"))
        assertTrue(text.contains("Salary only"))
        assertFalse(text.contains("Research match"))
        assertFalse(text.contains("Unknown"))
        assertFalse(text.contains("Scope facts"))
        assertFalse(text.contains("UNSUPPORTED", ignoreCase = true))
        assertFalse(text.contains("This still needs confirmation"))
        assertFalse(text.contains("not covered by the approved information"))
    }

    @Test
    fun `partial has facts without confirmation trailer`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Partial facts")))

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(1, "Deliverables details?", listOf(1L, 1L), RequestGroundingStatus.PARTIAL)
            )
        )

        assertTrue(text.contains("Partial facts"))
        assertFalse(text.contains("This still needs confirmation"))
    }

    @Test
    fun `identical fact bodies cross-reference later points`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(rule(1, "Shared enterprise matching facts"))
        )

        val text = composer.composeFallback(
            listOf(
                RequestFactItem(3, "- Matching?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(7, "- Enterprise projects?", listOf(1L), RequestGroundingStatus.GROUNDED)
            )
        )

        assertTrue(text.contains("Shared enterprise matching facts"))
        assertTrue(text.contains("Please see point 3 above."))
        assertEquals(1, Regex("Shared enterprise matching facts").findAll(text).count())
    }

    @Test
    fun `composeFromAnswers cross-references identical answers`() {
        stubFrame()
        val text = composer.composeFromAnswers(
            requestFacts = listOf(
                RequestFactItem(1, "- A?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- B?", listOf(2L), RequestGroundingStatus.GROUNDED)
            ),
            answersByIndex = mapOf(
                1 to "Same answer body",
                2 to "Same answer body"
            )
        )
        assertTrue(text.contains("Same answer body"))
        assertTrue(text.contains("Please see point 1 above."))
        assertEquals(1, Regex("Same answer body").findAll(text).count())
    }

    @Test
    fun `cleanHeading strips bullets punctuation trailing and and capitalizes`() {
        assertEquals("Salary", AiReplyPointByPointComposer.cleanHeading("- Salary?"))
        assertEquals("Visa process", AiReplyPointByPointComposer.cleanHeading("1. Visa process？"))
        assertEquals(
            "The full name and registered location of your company",
            AiReplyPointByPointComposer.cleanHeading("- the full name and registered location of your company;")
        )
        assertEquals(
            "Contractual, financial and IP arrangements",
            AiReplyPointByPointComposer.cleanHeading("- contractual, financial and IP arrangements; and")
        )
        val long = "A".repeat(200)
        assertEquals(160, AiReplyPointByPointComposer.cleanHeading(long).length)
    }

    @Test
    fun `all omitted items return frame only`() {
        stubFrame()
        val text = composer.composeFallback(
            listOf(
                RequestFactItem(
                    1,
                    "- Research?",
                    emptyList(),
                    RequestGroundingStatus.UNSUPPORTED,
                    requiresResearchContext = true
                )
            )
        )
        assertTrue(text.contains("Dear \${expertName|Professor},"))
        assertTrue(text.contains(QaReplyComposer.CLOSING))
        assertFalse(text.contains("1."))
        assertFalse(text.contains("confirmation", ignoreCase = true))
        assertFalse(text.contains("insufficient", ignoreCase = true))
    }

    @Test
    fun `composeFromAnswers preserves raw template variables and omits unsupported`() {
        stubFrame(salutation = "Dear \${expertName|Professor},")
        val text = composer.composeFromAnswers(
            listOf(
                RequestFactItem(1, "A?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "B?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            mapOf(1 to "Body A")
        )
        assertTrue(text.contains("Dear \${expertName|Professor},"))
        assertTrue(text.contains("1. A"))
        assertTrue(text.contains("Body A"))
        assertFalse(text.contains("2. B"))
        assertFalse(text.contains("Please send your CV", ignoreCase = true))
    }
}
