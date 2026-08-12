package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class UnsubscribeTokenMigrationTest {
    private val sql = Files.readString(
        Path.of("src/main/resources/db/migration/V89__create_unsubscribe_token.sql")
    )

    @Test
    fun `V89 creates unsubscribe token table with unique keys`() {
        assertTrue(sql.contains("CREATE TABLE unsubscribe_token"), "missing CREATE TABLE unsubscribe_token")
        assertTrue(sql.contains("UNIQUE KEY uk_email"), "missing uk_email unique key")
        assertTrue(sql.contains("UNIQUE KEY uk_token"), "missing uk_token unique key")
        assertTrue(sql.contains("VARCHAR(320)"), "email must be VARCHAR(320)")
        assertTrue(sql.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"), "missing engine/charset clause")
    }

    @Test
    fun `V89 does not backfill tokens`() {
        val codeOnly = sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        assertFalse(codeOnly.contains("INSERT"), "V89 must not backfill tokens")
    }

    @Test
    fun `V89 contains no flyway placeholder expressions`() {
        val codeOnly = sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        assertFalse(codeOnly.contains("\${"), "V89 must not contain \${...} placeholders")
    }
}
