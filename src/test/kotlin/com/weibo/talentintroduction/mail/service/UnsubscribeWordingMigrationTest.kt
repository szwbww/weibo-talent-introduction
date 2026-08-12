package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UnsubscribeWordingMigrationTest {
    private val v87 = Files.readString(
        Path.of("src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql")
    )
    private val v88 = Files.readString(
        Path.of("src/main/resources/db/migration/V88__rewrite_unsubscribe_line_wording.sql")
    )

    private val replaceSource = "you can unsubscribe here: \${unsubscribeUrl}"

    @Test
    fun `V88 replace source is a substring of the V87 appended sentence`() {
        assertTrue(
            v87.contains(replaceSource),
            "V88's REPLACE source must appear verbatim in V87 or the migration silently rewrites 0 rows"
        )
    }

    @Test
    fun `V88 uses point REPLACE and never whole-body overwrite`() {
        assertTrue(v88.contains("REPLACE("), "V88 must use point REPLACE")
        assertFalse(
            Regex("SET\\s+b\\.custom_text\\s*=\\s*'").containsMatchIn(v88),
            "V88 must not overwrite the whole custom_text body"
        )
    }

    @Test
    fun `V88 keeps the LIKE idempotency guard`() {
        assertTrue(
            v88.contains("LIKE '%you can unsubscribe here: \${unsubscribeUrl}%'"),
            "missing LIKE guard for idempotency"
        )
    }

    @Test
    fun `V88 does not write the dead mail_template table`() {
        val codeOnly = v88.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        assertFalse(codeOnly.contains("mail_template"), "V88 must not touch mail_template")
    }

    @Test
    fun `V88 targets only cold outreach templates and CUSTOM_TEXT blocks`() {
        assertTrue(v88.contains("'INTRODUCTION'"), "missing INTRODUCTION template code")
        assertTrue(v88.contains("'MATERIAL_REMINDER'"), "missing MATERIAL_REMINDER template code")
        assertTrue(v88.contains("b.block_type = 'CUSTOM_TEXT'"), "missing CUSTOM_TEXT block guard")
    }
}
