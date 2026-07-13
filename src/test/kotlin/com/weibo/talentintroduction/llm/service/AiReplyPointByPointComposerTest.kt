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
    fun `compose emits salutation acknowledgement numbered sections and closing in order`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary facts")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Visa facts")))

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "- What is salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Visa process?", listOf(2L), RequestGroundingStatus.GROUNDED)
            )
        )

        assertTrue(text.startsWith("Dear \${expertName|Professor},"))
        assertTrue(text.contains(QaReplyComposer.GREETING))
        assertTrue(text.contains("1. What is salary"))
        assertTrue(text.contains("2. Visa process"))
        assertTrue(text.indexOf("1. What is salary") < text.indexOf("2. Visa process"))
        assertTrue(text.indexOf(QaReplyComposer.GREETING) < text.indexOf("1. What is salary"))
        assertTrue(text.indexOf("2. Visa process") < text.indexOf(QaReplyComposer.CLOSING))
        assertTrue(text.endsWith(QaReplyComposer.CLOSING) || text.contains(QaReplyComposer.CLOSING))
    }

    @Test
    fun `each section uses only its own facts and unsupported stays in place`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary only")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Visa only")))

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Visa?", listOf(2L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(3, "- Unknown topic?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )

        val s1 = text.substringAfter("1. Salary").substringBefore("2. Visa")
        val s2 = text.substringAfter("2. Visa").substringBefore("3. Unknown topic")
        val s3 = text.substringAfter("3. Unknown topic")
        assertTrue(s1.contains("Salary only"))
        assertFalse(s1.contains("Visa only"))
        assertTrue(s2.contains("Visa only"))
        assertFalse(s2.contains("Salary only"))
        assertTrue(s3.contains(AiReplyPointByPointComposer.UNSUPPORTED_TEXT))
        assertFalse(s3.contains("Salary only"))
        assertFalse(s3.contains("Visa only"))
    }

    @Test
    fun `partial section appends fixed confirmation trailer and dedupes rule ids`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Partial facts")))

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "Deliverables details?", listOf(1L, 1L), RequestGroundingStatus.PARTIAL),
                RequestFactItem(2, "Other?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )

        val section = text.substringAfter("1. Deliverables details").substringBefore("2. Other")
        assertEquals(1, Regex("Partial facts").findAll(section).count())
        assertTrue(section.trimEnd().endsWith(AiReplyPointByPointComposer.PARTIAL_CONFIRMATION))
    }

    @Test
    fun `cleanHeading strips bullets question marks and truncates`() {
        assertEquals("Salary", AiReplyPointByPointComposer.cleanHeading("- Salary?"))
        assertEquals("Visa process", AiReplyPointByPointComposer.cleanHeading("1. Visa process？"))
        assertEquals("Topic", AiReplyPointByPointComposer.cleanHeading("• Topic??"))
        val long = "A".repeat(200)
        assertEquals(160, AiReplyPointByPointComposer.cleanHeading(long).length)
    }

    @Test
    fun `compose does not invent CTA and preserves raw template variables`() {
        stubFrame(salutation = "Dear \${expertName|Professor},")
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Body A")))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule(2, "Body B")))

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "A?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "B?", listOf(2L), RequestGroundingStatus.GROUNDED)
            )
        )

        assertTrue(text.contains("Dear \${expertName|Professor},"))
        assertFalse(text.contains("Please send your CV", ignoreCase = true))
        assertFalse(text.contains("schedule a meeting", ignoreCase = true))
        assertFalse(text.contains("next step", ignoreCase = true))
    }

    @Test
    fun `seven requests produce exactly sections 1 through 7`() {
        stubFrame()
        (1L..7L).forEach { id ->
            Mockito.`when`(qaRuleRepository.findById(id)).thenReturn(
                Optional.of(rule(id, if (id == 7L) "" else "Body $id"))
            )
        }

        val facts = (1..7).map { idx ->
            if (idx == 7) {
                RequestFactItem(idx, "- Q$idx?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            } else {
                RequestFactItem(idx, "- Q$idx?", listOf(idx.toLong()), RequestGroundingStatus.GROUNDED)
            }
        }
        val text = composer.compose(facts)

        (1..7).forEach { n ->
            assertTrue(text.contains("$n. Q$n"))
        }
        assertTrue(text.indexOf("1. Q1") < text.indexOf("2. Q2"))
        assertTrue(text.indexOf("6. Q6") < text.indexOf("7. Q7"))
        assertTrue(text.substringAfter("7. Q7").contains(AiReplyPointByPointComposer.UNSUPPORTED_TEXT))
    }

    @Test
    fun `research GROUNDED empty facts uses profile excerpt when available`() {
        stubFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule(1, "Salary facts")))
        val profile = "Name: Dr. Ada\nResearch fields: ML, NLP\nAffiliation: Example University"

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Research match?", emptyList(), RequestGroundingStatus.GROUNDED)
            ),
            expertProfile = profile
        )

        val researchSection = text.substringAfter("2. Research match")
        assertTrue(researchSection.contains("Name: Dr. Ada"))
        assertTrue(researchSection.contains("Research fields: ML, NLP"))
        assertFalse(researchSection.contains(AiReplyPointByPointComposer.UNSUPPORTED_TEXT))
        assertTrue(researchSection.trim().isNotEmpty())
    }

    @Test
    fun `research GROUNDED empty facts without profile uses UNSUPPORTED_TEXT`() {
        stubFrame()

        val text = composer.compose(
            listOf(
                RequestFactItem(1, "- Research match?", emptyList(), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Other?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            ),
            expertProfile = null
        )

        val researchSection = text.substringAfter("1. Research match").substringBefore("2. Other")
        assertTrue(researchSection.contains(AiReplyPointByPointComposer.UNSUPPORTED_TEXT))
        assertFalse(researchSection.trim().isEmpty())
    }
}
