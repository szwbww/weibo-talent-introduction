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
        assertFalse(
            AiReplyActionPolicy.findViolations(
                "Please send your CV when convenient.",
                setOf(AiReplyAction.REQUEST_MATERIALS)
            ).isEmpty()
        )
        assertEquals(
            listOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.findViolations("Could you share your CV?", none).map { it.action }
        )
        assertEquals(
            listOf(AiReplyAction.REQUEST_MATERIALS),
            AiReplyActionPolicy.findViolations("Would you mind forwarding your résumé?", none).map { it.action }
        )
        assertFalse(
            AiReplyActionPolicy.findViolations(
                "Could you share your CV?",
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
            Could you share your CV?
            Would you mind forwarding your résumé?
            Meetings may be arranged after selection.
            Let us schedule a meeting next week.
        """.trimIndent()

        val (cleaned, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertTrue(removed)
        assertTrue(cleaned.contains("Thank you for your questions"))
        assertTrue(cleaned.contains("The process requires applicants to submit materials for review"))
        assertTrue(cleaned.contains("Meetings may be arranged after selection"))
        assertFalse(cleaned.contains("Please send your CV", ignoreCase = true))
        assertFalse(cleaned.contains("Could you share your CV", ignoreCase = true))
        assertFalse(cleaned.contains("résumé", ignoreCase = true))
        assertFalse(cleaned.contains("Let us schedule a meeting", ignoreCase = true))
    }

    @Test
    fun `sanitize preserves safe multi-paragraph layout byte-for-byte (I-1)`() {
        val input = """
            Dear Dr. Smith,

            Thank you for your interest in our program. Here are answers to your questions:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent() + "\n"

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertFalse(removed)
        assertEquals(input, sanitized)
    }

    @Test
    fun `sanitize removes only unauthorized sentence and keeps numbering signature and blank lines (I-2)`() {
        val input = """
            Dear Dr. Smith,

            Thank you for your interest. Answers below:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            Could you share your CV?
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent()

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("Could you share your CV", ignoreCase = true))
        assertTrue(sanitized.contains("Dear Dr. Smith,"))
        assertTrue(sanitized.contains("1. The company is registered in Beijing."))
        assertTrue(sanitized.contains("2. Expected deliverables are defined per project scope."))
        assertTrue(sanitized.contains("3. Researchers are matched by domain expertise."))
        assertTrue(sanitized.contains("6. Early-stage materials are a short CV and research summary."))
        assertTrue(sanitized.contains("Best regards,"))
        assertTrue(sanitized.contains("\n\n"))
        // Numbered items remain on separate lines (layout not collapsed to a single line)
        assertTrue(sanitized.contains("1. The company is registered in Beijing.\n"))
        assertTrue(sanitized.contains("\nBest regards,"))
    }

    @Test
    fun `sanitize preserves CRLF bullets and trailing newline when safe (I-1 I-3)`() {
        val input = "Dear colleague,\r\n\r\n" +
            "- Point one about the program.\r\n" +
            "- Point two about funding.\r\n" +
            "- Point three about next steps.\r\n\r\n" +
            "Best regards\r\n"

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertFalse(removed)
        assertEquals(input, sanitized)
    }

    @Test
    fun `sanitize removes CV request while preserving CRLF bullet layout (I-2 I-3)`() {
        val input = "Dear colleague,\r\n\r\n" +
            "- Point one about the program.\r\n" +
            "Could you share your CV?\r\n" +
            "- Point two about funding.\r\n\r\n" +
            "Best regards\r\n"

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("Could you share your CV", ignoreCase = true))
        assertTrue(sanitized.contains("- Point one about the program."))
        assertTrue(sanitized.contains("- Point two about funding."))
        assertTrue(sanitized.contains("Best regards"))
        assertTrue(sanitized.contains("\r\n"))
        assertTrue(sanitized.contains("- Point one about the program.\r\n"))
    }

    @Test
    fun `sanitize keeps pre-existing triple newlines away from deletion seam (I-2 seam-local)`() {
        val input = "Intro paragraph.\n\n\nContinued intro.\n\n" +
            "1. First answer.\n" +
            "Could you share your CV?\n" +
            "2. Second answer.\n"

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("Could you share your CV", ignoreCase = true))
        // Non-seam pre-existing hole must stay byte-identical
        assertTrue(sanitized.contains("Intro paragraph.\n\n\nContinued intro."))
        // Deletion seam collapses to at most \n\n (not a huge hole)
        assertTrue(sanitized.contains("1. First answer.\n\n2. Second answer."))
        assertFalse(sanitized.contains("1. First answer.\n\n\n2. Second answer."))
    }

    @Test
    fun `sanitize collapses CRLF blank-line hole only at deletion seam (I-2)`() {
        val input = "Dear colleague,\r\n\r\n" +
            "- Point one about the program.\r\n" +
            "Could you share your CV?\r\n\r\n\r\n\r\n" +
            "- Point two about funding.\r\n\r\n" +
            "Best regards\r\n"

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(input, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("Could you share your CV", ignoreCase = true))
        assertTrue(sanitized.contains("- Point one about the program."))
        assertTrue(sanitized.contains("- Point two about funding."))
        // Seam: at most two CRLF blank-line separators (\r\n\r\n), no 3+ blank lines
        assertTrue(sanitized.contains("- Point one about the program.\r\n\r\n- Point two about funding."))
        assertFalse(sanitized.contains("\r\n\r\n\r\n"))
    }

    @Test
    fun `do not need to provide passport is not a violation`() {
        val text = "You do not need to provide a passport at this stage."
        assertTrue(AiReplyActionPolicy.findViolations(text, emptySet()).isEmpty())

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertFalse(removed)
        assertEquals(text, sanitized)
    }

    @Test
    fun `do not request an ID card is not a violation`() {
        val text = "We do not request an identity card for initial contact."
        assertTrue(AiReplyActionPolicy.findViolations(text, emptySet()).isEmpty())

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertFalse(removed)
        assertEquals(text, sanitized)
    }

    @Test
    fun `chinese negative passport mention is not a violation`() {
        val text = "此阶段不需要提供护照。"
        assertTrue(AiReplyActionPolicy.findViolations(text, emptySet()).isEmpty())

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertFalse(removed)
        assertEquals(text, sanitized)
    }

    @Test
    fun `negation with subsequent positive CTA still flags the positive part`() {
        val text = "We do not request an ID card, but please send your passport."
        val violations = AiReplyActionPolicy.findViolations(text, emptySet())
        assertFalse(violations.isEmpty())
        assertEquals(AiReplyAction.REQUEST_MATERIALS, violations.first().action)
        assertEquals(AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL, violations.first().code)

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("passport", ignoreCase = true))
        assertTrue(sanitized.contains("do not request an ID card"))
    }

    @Test
    fun `ID card positive request is blocked and fully removed`() {
        val text = "Please send your ID card."
        val violations = AiReplyActionPolicy.findViolations(text, emptySet())
        assertFalse(violations.isEmpty())
        assertEquals(AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL, violations.first().code)

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertTrue(removed)
        assertEquals("", sanitized)
    }

    @Test
    fun `do not request ID card is not a violation`() {
        val text = "We do not request an ID card."
        assertTrue(AiReplyActionPolicy.findViolations(text, emptySet()).isEmpty())

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertFalse(removed)
        assertEquals(text, sanitized)
    }

    @Test
    fun `positive passport CTA still blocked`() {
        val text = "Could you send your passport?"
        val violations = AiReplyActionPolicy.findViolations(text, emptySet())
        assertFalse(violations.isEmpty())
        assertEquals(AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL, violations.first().code)

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
        assertTrue(removed)
        assertFalse(sanitized.contains("passport", ignoreCase = true))
    }

    @Test
    fun `please send work certificate still blocked`() {
        val text = "请发送您的工作证明。"
        val violations = AiReplyActionPolicy.findViolations(text, emptySet())
        assertFalse(violations.isEmpty())
        assertEquals(AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL, violations.first().code)
    }

    @Test
    fun `sensitive CTA action scope follows the closed 5A final matrix`() {
        val cases = listOf(
            Triple(
                "We do not request an ID card.",
                false,
                "We do not request an ID card."
            ),
            Triple(
                "We do not request an ID card, but please send your passport.",
                true,
                "We do not request an ID card"
            ),
            Triple(
                "Please send your passport and bank statement.",
                true,
                ""
            ),
            Triple(
                "Please send your passport, ID card, and bank statement.",
                true,
                ""
            ),
            Triple(
                "We do not request a passport and bank statement.",
                false,
                "We do not request a passport and bank statement."
            ),
            Triple(
                "We do not request a passport and please send your bank statement.",
                true,
                "We do not request a passport"
            ),
            Triple(
                "此阶段不需要提供护照和银行证明。",
                false,
                "此阶段不需要提供护照和银行证明。"
            ),
            Triple(
                "此阶段不需要提供护照，但请发送银行证明。",
                true,
                "此阶段不需要提供护照"
            )
        )

        cases.forEach { (text, expectedViolation, expectedSanitized) ->
            val violations = AiReplyActionPolicy.findViolations(text, emptySet())
            assertEquals(if (expectedViolation) 1 else 0, violations.size, text)
            if (expectedViolation) {
                assertEquals(
                    AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL,
                    violations.single().code,
                    text
                )
            }

            val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, emptySet())
            assertEquals(expectedViolation, removed, text)
            assertEquals(expectedSanitized, sanitized, text)
        }
    }
}
