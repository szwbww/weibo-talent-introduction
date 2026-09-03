package com.weibo.talentintroduction.rag

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class RagSeedEncodingRepairMigrationTest {
    @Test
    fun `v116 repairs rag_fact mojibake with guarded latin1 round-trip conversion`() {
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V116__repair_rag_fact_encoding.sql")
        )

        // All six free-text columns of rag_fact are repaired.
        val columns = listOf(
            "title", "category", "question_variants", "keywords", "answer", "source_refs"
        )
        columns.forEach { column ->
            assertTrue(
                sql.contains("SET $column = CONVERT(CAST(CONVERT($column USING latin1) AS BINARY) USING utf8mb4)"),
                "Missing latin1 round-trip repair for rag_fact.$column"
            )
            assertTrue(
                sql.contains("WHERE HEX($column) REGEXP '^(..)*C[23]'"),
                "Missing mojibake guard for rag_fact.$column"
            )
        }

        // Guard targets U+0080-U+00FF chars (C2/C3 lead bytes), which never
        // legitimately occur in this ASCII + CJK corpus, so clean rows survive.
        assertTrue(sql.contains("C[23]"), "Guard must cover the latin-1 supplement range")
    }

    @Test
    fun `v116 stays ascii-only and touches no other table`() {
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V116__repair_rag_fact_encoding.sql")
        )
        assertTrue(sql.all { it.code < 128 }, "V116 must be ASCII-only")
        val stmts = sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        assertFalse(stmts.contains("INSERT"), "V116 must not insert rows")
        assertFalse(stmts.contains("rag_phrase_group"), "V116 must not touch rag_phrase_group")
        assertFalse(stmts.contains("rag_intent_coverage"), "V116 must not touch rag_intent_coverage")
        assertFalse(stmts.contains("rag_mandatory_rule"), "V116 must not touch rag_mandatory_rule")
        assertFalse(stmts.contains("rag_prefilter_exclusion"), "V116 must not touch rag_prefilter_exclusion")
        assertFalse(stmts.contains("rag_kb_meta"), "V116 must not touch rag_kb_meta")
    }
}
