package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UnsubscribeBodyLinkMigrationTest {
    private val sql = Files.readString(
        Path.of("src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql")
    )

    @Test
    fun `V87 appends unsubscribe placeholder to both cold outreach templates`() {
        assertTrue(sql.contains("'INTRODUCTION'"), "missing INTRODUCTION template code")
        assertTrue(sql.contains("'MATERIAL_REMINDER'"), "missing MATERIAL_REMINDER template code")
        assertTrue(sql.contains("\${unsubscribeUrl}"), "missing unsubscribe placeholder")
    }

    @Test
    fun `V87 uses CONCAT append and never whole-body overwrite`() {
        assertTrue(sql.contains("CONCAT("), "must append with CONCAT")
        assertFalse(
            Regex("SET\\s+b\\.custom_text\\s*=\\s*'").containsMatchIn(sql),
            "V87 must not overwrite the whole custom_text body"
        )
    }

    @Test
    fun `V87 guards against duplicate application`() {
        assertTrue(sql.contains("NOT LIKE '%unsubscribeUrl%'"), "missing NOT LIKE idempotency guard")
    }

    @Test
    fun `V87 does not write the dead mail_template table`() {
        val codeOnly = sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        assertFalse(codeOnly.contains("mail_template"), "V87 must not touch mail_template")
    }

    @Test
    fun `production flyway config disables placeholder replacement`() {
        val yml = Files.readString(Path.of("src/main/resources/application.yml"))
        assertTrue(yml.contains("placeholder-replacement: false"), "flyway placeholder replacement must be disabled")
    }

    @Test
    fun `no other template code is touched by V87`() {
        assertFalse(sql.contains("MEETING_INVITATION"), "MEETING_INVITATION must not be touched")
        assertFalse(sql.contains("MEETING_CONFIRMATION"), "MEETING_CONFIRMATION must not be touched")
    }
}
