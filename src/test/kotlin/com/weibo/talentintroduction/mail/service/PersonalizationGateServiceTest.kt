package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalizationGateServiceTest {
    private val service = PersonalizationGateService()

    // ── I-3: gate input is raw text; missing keys = intersection(required, fallback) ──

    @Test
    fun `evaluate blocks when a required variable fell back to its default`() {
        val result = service.evaluate(
            rawTexts = listOf(
                "Topic: \${researchFields|Science}",
                "At \${institution|your institution}"
            ),
            variables = mapOf(
                "researchFields" to "",
                "institution" to "Oxford"
            ),
            requiredKeys = listOf("researchFields", "institution", "expertName")
        )

        assertTrue(result.blocked)
        // exact intersection: researchFields took fallback, institution did not, expertName not in text
        assertEquals(listOf("researchFields"), result.missingKeys)
    }

    @Test
    fun `evaluate does not block when required variable has a real value`() {
        val result = service.evaluate(
            rawTexts = listOf("Topic: \${researchFields|Science}"),
            variables = mapOf("researchFields" to "Machine Learning"),
            requiredKeys = listOf("researchFields")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `evaluate does not flag rendered text as blocked`() {
        // Rendered text has the fallback already substituted, so fallback detection
        // sees nothing — proving the gate must be fed pre-render raw text (I-3).
        val result = service.evaluate(
            rawTexts = listOf("Topic: Science"),
            variables = mapOf("researchFields" to ""),
            requiredKeys = listOf("researchFields")
        )

        assertFalse(result.blocked)
        assertTrue(result.missingKeys.isEmpty())
    }

    @Test
    fun `evaluate collects fallback keys across multiple raw texts`() {
        val result = service.evaluate(
            rawTexts = listOf(
                "Subject: \${recentWorkTitle|Untitled}",
                "Body: \${primaryResearchField|N/A}"
            ),
            variables = mapOf("recentWorkTitle" to "", "primaryResearchField" to ""),
            requiredKeys = listOf("recentWorkTitle", "primaryResearchField")
        )

        assertTrue(result.blocked)
        assertEquals(listOf("recentWorkTitle", "primaryResearchField"), result.missingKeys)
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
