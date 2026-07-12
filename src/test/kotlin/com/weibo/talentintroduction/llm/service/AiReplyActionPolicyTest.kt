package com.weibo.talentintroduction.llm.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiReplyActionPolicyTest {

    private val expertMail = """
        Thank you for your message. Here are my research profiles:
        https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
        https://www.scopus.com/authid/detail.uri?authorId=57201234567

        Could you please confirm whether my research background fits the enterprise projects you manage?

        Specifically:
        - What is the registered location of your company?
        - What are the expected responsibilities and deliverables?
        - How are researchers selected and matched within the scope of enterprise projects?
        - What are the intellectual property arrangements?
        - What are the next stages of the application?
        - What materials should I send?

        Best regards
    """.trimIndent()

    @Test
    fun `expert diligence mail allows no actions by default`() {
        val allowed = AiReplyActionPolicy.deriveAllowed(expertMail, null, emptyList())
        assertTrue(allowed.isEmpty())
        assertEquals("NONE", AiReplyActionPolicy.formatAllowedLabel(allowed))
    }

    @Test
    fun `explicit materials or meeting intent authorizes matching action only`() {
        assertEquals(
            setOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.deriveAllowed("I attached my CV for your review.", null, emptyList())
        )
        assertEquals(
            setOf(AiReplyAction.PROPOSE_MEETING),
            AiReplyActionPolicy.deriveAllowed("Can we arrange a meeting next week?", null, emptyList())
        )
        assertEquals(
            setOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.deriveAllowed(
                "Hello",
                "please ask for their CV",
                emptyList()
            )
        )
        assertEquals(
            setOf(AiReplyAction.PROPOSE_MEETING),
            AiReplyActionPolicy.deriveAllowed(
                "Hello",
                null,
                listOf(AiReplyTurn(assistantDraft = "draft", operatorInstruction = "propose a Zoom call"))
            )
        )
        assertTrue(
            AiReplyActionPolicy.deriveAllowed(
                "Hello",
                null,
                listOf(
                    AiReplyTurn(
                        assistantDraft = "please send your CV so we can proceed",
                        operatorInstruction = "tone warmer"
                    )
                )
            ).isEmpty()
        )
    }

    @Test
    fun `findViolations distinguishes direct requests from process descriptions`() {
        val none = emptySet<AiReplyAction>()
        assertEquals(
            listOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.findViolations("Please send your CV when convenient.", none).map { it.action }
        )
        assertEquals(
            listOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.findViolations("请发送您的简历。", none).map { it.action }
        )
        assertTrue(
            AiReplyActionPolicy.findViolations(
                "The process requires applicants to submit materials for review.",
                none
            ).isEmpty()
        )
        assertEquals(
            listOf(AiReplyAction.PROPOSE_MEETING),
            AiReplyActionPolicy.findViolations("Let us schedule a meeting next week.", none).map { it.action }
        )
        assertEquals(
            listOf(AiReplyAction.PROPOSE_MEETING),
            AiReplyActionPolicy.findViolations("Please share a convenient time for a call.", none).map { it.action }
        )
        assertTrue(
            AiReplyActionPolicy.findViolations("Meetings may be arranged after selection.", none).isEmpty()
        )
        assertTrue(
            AiReplyActionPolicy.findViolations(
                "Please send your CV when convenient.",
                setOf(AiReplyAction.REQUEST_MATERIALS)
            ).isEmpty()
        )
    }

    @Test
    fun `sanitize removes only unauthorized direct request sentences`() {
        val text = """
            Thank you for your questions.
            The process requires applicants to submit materials for review.
            Please send your CV when convenient.
            Meetings may be arranged after selection.
            Let us schedule a meeting next week.
        """.trimIndent()

        val (cleaned, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertTrue(removed)
        assertTrue(cleaned.contains("Thank you for your questions"))
        assertTrue(cleaned.contains("The process requires applicants to submit materials for review"))
        assertTrue(cleaned.contains("Meetings may be arranged after selection"))
        assertFalse(cleaned.contains("Please send your CV", ignoreCase = true))
        assertFalse(cleaned.contains("Let us schedule a meeting", ignoreCase = true))
    }
}
