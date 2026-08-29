package com.weibo.talentintroduction.qa.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Text-level assertions on V109 (plan 11-fact-supply, T-2). Chosen over
 * FlywayMigrationIntegrationTest because that suite needs local Docker and is
 * skipped by default (@EnabledIfSystemProperty migrationIt=true), so it cannot
 * gate the full `mvn test` run.
 */
class QaFactSupplyMigrationTest {

    private val sql: String = Files.readString(
        Path.of("src/main/resources/db/migration/V109__qa_fact_supply_and_controlled_key_repair.sql")
    )

    private val frozenIdGuard = "AND id NOT IN (1, 3, 21, 24)"
    private val whereNotExistsGuard =
        "WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject ="

    // 断言 1 (I-1): 恰好 2 条 UPDATE，每条语句体都含冻结 id 守卫，
    // 且守卫字面量在全文恰好出现 2 次（多于 2 说明注释里混入了字面量）。
    @Test
    fun `every update carries the frozen id guard`() {
        val updates = sql.split("UPDATE qa_rule").drop(1)
        assertEquals(2, updates.size, "expected exactly 2 UPDATE qa_rule statements")
        updates.forEach { update ->
            assertTrue(
                update.substringBefore(";").contains(frozenIdGuard),
                "UPDATE statement body missing frozen-id guard"
            )
        }
        val occurrences = Regex(Regex.escape(frozenIdGuard)).findAll(sql).count()
        assertEquals(
            2,
            occurrences,
            "guard literal must appear exactly once per UPDATE and nowhere else"
        )
    }

    // 断言 2 (G-3): 5 条 INSERT 全部带 WHERE NOT EXISTS 守卫。
    @Test
    fun `all five inserts are guarded by where not exists`() {
        val occurrences = Regex(Regex.escape(whereNotExistsGuard)).findAll(sql).count()
        assertEquals(5, occurrences, "expected exactly 5 WHERE NOT EXISTS guards, found $occurrences")
    }

    // 断言 3 (K-qa-migration-preserve-auto-updated-timestamp): 每条 UPDATE 保留 updated_at。
    @Test
    fun `both updates preserve updated_at`() {
        val occurrences = Regex("updated_at = updated_at").findAll(sql).count()
        assertEquals(2, occurrences, "expected updated_at = updated_at exactly once per UPDATE")
    }

    // 断言 4 (I-4): 死锁修复语句携带精确守卫，防止误伤已被运营改过的行。
    @Test
    fun `deadlock repair carries the exact coverage guard`() {
        assertTrue(
            sql.contains("AND coverage_keys = 'confidentiality.materials'"),
            "deadlock repair must be guarded on coverage_keys = 'confidentiality.materials'"
        )
    }

    // 断言 5 (G-3): 四个新增 IP 关键词各自出现 2 次 NOT LIKE 守卫
    // （一次在 CASE WHEN、一次在 WHERE 短路条件），合计 8 处。
    @Test
    fun `each ip keyword appears in two not like guards`() {
        val keywords = listOf("ip arising", "advisory input", "ownership of ip", "ip ownership")
        keywords.forEach { kw ->
            val occurrences =
                Regex("NOT LIKE '%" + Regex.escape(kw) + "%'").findAll(sql).count()
            assertEquals(2, occurrences, "expected 2 NOT LIKE guards for '$kw'")
        }
        val total = keywords.sumOf { kw ->
            Regex("NOT LIKE '%" + Regex.escape(kw) + "%'").findAll(sql).count()
        }
        assertEquals(8, total, "expected 8 NOT LIKE guards in total")
    }

    // 断言 6 (I-5 / Out of scope): 全文不含 reply_body = / answer_body = 赋值
    // （INSERT 的列名列表不算赋值），即无任何正文改写。
    @Test
    fun `migration contains no body rewrites`() {
        assertFalse(sql.contains("reply_body ="), "V109 must not rewrite reply_body")
        assertFalse(sql.contains("answer_body ="), "V109 must not rewrite answer_body")
    }

    // 断言 7 (I-3): 五条 INSERT 的 coverage_keys 字面量逐一断言，
    // 且没有任何一个等于任一受控组。
    @Test
    fun `five new rules carry the planned coverage literals and none is a controlled group`() {
        val coverageLiterals = Regex("INSERT INTO qa_rule[\\s\\S]*?(?=INSERT INTO qa_rule|\\z)")
            .findAll(sql)
            .map { m ->
                val body = m.value.substringBefore("WHERE NOT EXISTS")
                Regex("'([^']*)'").findAll(body).last().groupValues[1]
            }
            .toList()
        assertEquals(
            listOf(
                "enterprise.project_types",
                "finance.compensation_structure",
                "role.deliverables",
                "confidentiality.research",
                "work.time_commitment,work.advisory_duration"
            ),
            coverageLiterals,
            "INSERT coverage literals must match the planned five rules"
        )
        val controlledGroups = setOf(
            "confidentiality.materials",
            "fees.policy",
            "contract.party,contract.terms",
            "ip.arrangements"
        )
        assertTrue(
            coverageLiterals.none { it in controlledGroups },
            "no INSERT coverage literal may equal a controlled group: $coverageLiterals"
        )
    }
}
