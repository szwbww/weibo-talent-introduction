package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalizationGateServiceTest {
    private val service = PersonalizationGateService()

    // ── I-1/I-2: the gate is the template's bare `${key}` set ∩ the texts actually sent ──

    @Test
    fun `evaluate blocks a bare required key that has no value`() {
        val result = service.evaluate(
            rawTexts = listOf("Subject: \${institution}"),
            variables = mapOf("institution" to ""),
            requiredKeys = listOf("institution")
        )

        assertTrue(result.blocked)
        assertEquals(listOf("institution"), result.missingKeys)
    }

    @Test
    fun `evaluate treats whitespace-only values as missing`() {
        val result = service.evaluate(
            rawTexts = listOf("Body: \${primaryResearchField}"),
            variables = mapOf("primaryResearchField" to "   \n"),
            requiredKeys = listOf("primaryResearchField")
        )

        assertTrue(result.blocked)
        assertEquals(listOf("primaryResearchField"), result.missingKeys)
    }

    @Test
    fun `evaluate passes when every required key has a real value`() {
        val result = service.evaluate(
            rawTexts = listOf("Subject: \${institution}", "Body: \${primaryResearchField}"),
            variables = mapOf(
                "institution" to "MIT",
                "primaryResearchField" to "Quantum Computing"
            ),
            requiredKeys = listOf("institution", "primaryResearchField")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `evaluate ignores required keys absent from the texts actually sent`() {
        // I-2: exactness — a key made mandatory by another block/variant of the template
        // must not block a send whose selected texts do not use it.
        val result = service.evaluate(
            rawTexts = listOf("Subject: \${institution}"),
            variables = mapOf("institution" to "MIT"),
            requiredKeys = listOf("institution", "primaryResearchField")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `evaluate ignores a template-wide required key that the selected text defaults`() {
        // V-1/M-1: the caller passes the template-wide required union, which lists a key
        // as soon as ANY block or variant writes it bare. A selected text that resolves
        // that same key through a non-blank default must still send.
        val result = service.evaluate(
            rawTexts = listOf(
                "Topic: \${researchFields|Science}",
                "At \${institution|your institution}"
            ),
            variables = mapOf("researchFields" to "", "institution" to "Oxford"),
            requiredKeys = listOf("researchFields", "institution", "expertName")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `evaluate still blocks a bare token of a key defaulted elsewhere in the send`() {
        val result = service.evaluate(
            rawTexts = listOf(
                "Topic: \${researchFields|Science}",
                "Body: \${researchFields}"
            ),
            variables = mapOf("researchFields" to ""),
            requiredKeys = listOf("researchFields")
        )

        assertTrue(result.blocked)
        assertEquals(listOf("researchFields"), result.missingKeys)
    }

    @Test
    fun `evaluate collects missing keys across multiple raw texts in required order`() {
        val result = service.evaluate(
            rawTexts = listOf(
                "Subject: \${recentWorkTitle}",
                "Body: \${primaryResearchField}"
            ),
            variables = mapOf("recentWorkTitle" to "", "primaryResearchField" to ""),
            requiredKeys = listOf("recentWorkTitle", "primaryResearchField")
        )

        assertTrue(result.blocked)
        assertEquals(listOf("recentWorkTitle", "primaryResearchField"), result.missingKeys)
    }

    @Test
    fun `evaluate does not flag rendered text as blocked`() {
        // Rendered text has the default already substituted, so nothing is missing there —
        // proving the gate must be fed pre-render raw text (I-2).
        val result = service.evaluate(
            rawTexts = listOf("Topic: Science"),
            variables = mapOf("researchFields" to ""),
            requiredKeys = listOf("researchFields")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    // ── I-4: empty required set disables the gate ──

    @Test
    fun `evaluate never blocks when requiredKeys is empty`() {
        val result = service.evaluate(
            rawTexts = listOf("Topic: \${researchFields|Science}"),
            variables = mapOf("researchFields" to ""),
            requiredKeys = emptyList()
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    // ── I-2: placeholder residue always rejects ──

    @Test
    fun `requireNoPlaceholderResidue throws with the residue token in the message`() {
        val ex = assertThrows(PlaceholderResidueException::class.java) {
            service.requireNoPlaceholderResidue("a \${x} b")
        }

        assertTrue(ex.message!!.contains("\${x}"))
    }

    @Test
    fun `requireNoPlaceholderResidue checks every rendered text`() {
        assertThrows(PlaceholderResidueException::class.java) {
            service.requireNoPlaceholderResidue("clean subject", "dirty body \${unresolved}")
        }
    }

    @Test
    fun `requireNoPlaceholderResidue accepts fully resolved and null texts`() {
        service.requireNoPlaceholderResidue(
            "Hello resolved",
            null,
            "no tokens"
        )
    }

    @Test
    fun `requireNoPlaceholderResidue accepts empty string`() {
        service.requireNoPlaceholderResidue("", "plain text")
    }
}
