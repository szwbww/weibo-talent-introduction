package com.weibo.talentintroduction.qa.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Text-level assertions on V105 (plan 01-fact-and-catalog, C-3). Chosen over
 * FlywayMigrationIntegrationTest because that suite needs local Docker and is
 * skipped by default (@EnabledIfSystemProperty migrationIt=true), so it cannot
 * gate the full `mvn test` run.
 */
class ProgrammeIdentityFactsMigrationTest {

    private val sql: String = Files.readString(
        Path.of("src/main/resources/db/migration/V105__add_programme_identity_facts.sql")
    )

    @Test
    fun `both new inserts are guarded by where not exists`() {
        // I-5: the two new INSERTs must be idempotent against runtime-created rules.
        val occurrences = Regex(
            "WHERE NOT EXISTS \\(SELECT 1 FROM qa_rule WHERE reply_subject ="
        ).findAll(sql).count()
        assertTrue(
            occurrences == 2,
            "expected exactly 2 WHERE NOT EXISTS guards, found $occurrences"
        )
    }

    @Test
    fun `existing rule updates preserve updated_at`() {
        // I-5: appending keywords must not mark the record as operator-updated.
        assertTrue(
            sql.contains("updated_at = updated_at"),
            "V105 must preserve updated_at on existing-rule updates"
        )
    }

    @Test
    fun `id 6 collaboration keywords each carry a not like guard`() {
        // I-5: dedupe guards for the three appended collaboration-form keywords.
        listOf(
            "%form of collaboration%",
            "%forms of collaboration%",
            "%how the collaboration works%"
        ).forEach { guard ->
            assertTrue(
                sql.contains("NOT LIKE '$guard'"),
                "missing NOT LIKE guard $guard for id=6 keywords"
            )
        }
    }

    @Test
    fun `id 18 sponsor keywords each carry a not like guard`() {
        // I-5 (statement 4, IP-6 fix): dedupe guards for the appended sponsor keywords.
        listOf(
            "%government body%",
            "%institution supporting%"
        ).forEach { guard ->
            assertTrue(
                sql.contains("NOT LIKE '$guard'"),
                "missing NOT LIKE guard $guard for id=18 keywords"
            )
        }
    }

    @Test
    fun `migration contains no flyway placeholder tokens`() {
        assertFalse(
            sql.contains("\${"),
            "V105 must not contain Flyway placeholder tokens"
        )
    }

    @Test
    fun `new facts carry their coverage keys inline`() {
        // I-4: coverage_keys must be written by the migration itself, not left to
        // the backend UI.
        assertTrue(sql.contains("'programme.name'"), "missing coverage literal 'programme.name'")
        assertTrue(
            sql.contains("'governance.sponsor_level'"),
            "missing coverage literal 'governance.sponsor_level'"
        )
    }
}
