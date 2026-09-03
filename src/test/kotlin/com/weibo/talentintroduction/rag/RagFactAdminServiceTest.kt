package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.rag.service.RagFactAdminService
import com.weibo.talentintroduction.rag.service.RagFactUpdateRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.io.File

/**
 * 计划 04 (T6 + 验收标准): 对真实 MySQL 断言管理服务全部不变量 ——
 * I-20（republish 原子入口 + 连续两次编辑）、I-21（审计行同事务 + 回滚 +
 * 全文 old/new + fingerprint_before/after 闭环）、I-22（只读字段忽略）、
 * I-23（控制器无 create/delete）。覆盖: I-20 / I-21 / I-22 / I-23 / G-1 / G-4。
 *
 * 数据源模式与 RagKnowledgeBaseTest 完全一致（类级 `-DmigrationIt=true` 门控；
 * 设 `RAG_KB_TEST_DB_URL`（+ 可选 USERNAME/PASSWORD，默认 root/root）时不启
 * 容器直连已就绪库 —— 控制器授权的 scratch 补丁链替代验证路径；普通
 * `mvn test` 保持 docker-free 全绿（类被跳过）。
 *
 * 每个用例结束都把 DB 恢复为 V112 种子态（rag_fact 原值 + meta 指纹 +
 * 清空 rag_fact_audit），保证用例之间无顺序耦合。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@TestPropertySource(
    properties = [
        "talent-introduction.mail-queue.enabled=false",
        "talent-introduction.scheduling.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.flyway.placeholder-replacement=false"
    ]
)
class RagFactAdminServiceTest {

    companion object {
        /** G-2 常量（A1 修订后）。 */
        private const val EXPECTED_FINGERPRINT = "e62421a42c432cf3"
        /** A-2 验收用事实：KB-ENT-012 合作企业类型（COMPOSE，非逐字）。 */
        private const val EDIT_CODE = "KB-ENT-012"
        private const val VERBATIM_CODE = "KB-FUND-033"

        private val externalUrl: String? = System.getenv("RAG_KB_TEST_DB_URL")
        private val externalUsername: String =
            System.getenv("RAG_KB_TEST_DB_USERNAME") ?: "root"
        private val externalPassword: String =
            System.getenv("RAG_KB_TEST_DB_PASSWORD") ?: "root"

        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            if (externalUrl == null) {
                check(DockerClientFactory.instance().isDockerAvailable) {
                    "Docker is required when RAG_KB_TEST_DB_URL is not set"
                }
                mysql.start()
            }
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (externalUrl == null && mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            if (externalUrl == null) {
                registry.add("spring.datasource.url", mysql::getJdbcUrl)
                registry.add("spring.datasource.username", mysql::getUsername)
                registry.add("spring.datasource.password", mysql::getPassword)
            } else {
                registry.add("spring.datasource.url") { externalUrl }
                registry.add("spring.datasource.username") { externalUsername }
                registry.add("spring.datasource.password") { externalPassword }
            }
        }
    }

    @Autowired
    private lateinit var service: RagFactAdminService

    @Autowired
    private lateinit var ragKnowledgeBase: com.weibo.talentintroduction.rag.service.RagKnowledgeBase

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var transactionManager: DataSourceTransactionManager

    // 与 RagKnowledgeBaseTest 相同的上下文配方：这两个 bean 启动期会触达 ES。
    @MockBean
    private lateinit var expertIndexService: ExpertIndexService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    // ---------------------------------------------------------------- I-20

    @Test
    fun `update goes through republish and two consecutive edits both succeed - I-20`() {
        val before = ragKnowledgeBase.snapshot()
        val original = answerOf(EDIT_CODE)
        try {
            val first = service.update(
                EDIT_CODE,
                RagFactUpdateRequest(answer = original + " (r1)", operator = "c5-tester")
            )
            // ① 不抛异常（P0-3 直接钉死点）② 返回新指纹 == rag_kb_meta.fingerprint
            // ③ 快照整体替换为新实例，且已含新值（I-6/I-20 Interaction point 1）。
            assertNotEquals(EXPECTED_FINGERPRINT, first)
            assertEquals(first, metaFingerprint())
            assertNotSame(before, ragKnowledgeBase.snapshot())
            assertEquals(original + " (r1)", answerOf(EDIT_CODE))
            assertEquals(first, ragKnowledgeBase.fingerprint())
            // 启动期校验路径此时必须放行（meta 与库一致）。
            ragKnowledgeBase.verifyAndPublish()

            // 第二次编辑在第一次结果之上继续 —— 也必须成功（防只修好第一次）。
            val second = service.update(
                EDIT_CODE,
                RagFactUpdateRequest(answer = original + " (r1) (r2)", operator = "c5-tester")
            )
            assertNotEquals(first, second)
            assertEquals(second, metaFingerprint())
            assertEquals(original + " (r1) (r2)", answerOf(EDIT_CODE))
            assertEquals(second, ragKnowledgeBase.fingerprint())
            ragKnowledgeBase.verifyAndPublish()
        } finally {
            restoreAnswer(EDIT_CODE, original)
            assertSeedState()
        }
    }

    @Test
    fun `update with no actual field change is a no-op without audit rows - I-20`() {
        val original = answerOf(EDIT_CODE)
        try {
            val fingerprint = service.update(
                EDIT_CODE,
                RagFactUpdateRequest(answer = original, title = titleOf(EDIT_CODE))
            )
            assertEquals(EXPECTED_FINGERPRINT, fingerprint)
            assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
            assertEquals(0L, auditCount())
        } finally {
            assertSeedState()
        }
    }

    // ---------------------------------------------------------------- I-21

    @Test
    fun `answer edit writes one audit row with full text and fingerprint closure - I-21`() {
        val original = answerOf(EDIT_CODE)
        val fingerprintBefore = ragKnowledgeBase.fingerprint()
        assertEquals(EXPECTED_FINGERPRINT, fingerprintBefore)
        try {
            val edited = original + " \u2014 audited revision"
            val fingerprintAfter = service.update(
                EDIT_CODE,
                RagFactUpdateRequest(answer = edited, operator = "audit-operator")
            )
            val rows = auditRows()
            assertEquals(1, rows.size)
            val row = rows.single()
            assertEquals(EDIT_CODE, row["fact_code"])
            assertEquals("answer", row["field"])
            // answer 的 old/new 必须是全文（I-21 MEDIUMTEXT 语义）。
            assertEquals(original, row["old_value"])
            assertEquals(edited, row["new_value"])
            // fingerprint_before = 改动前快照指纹；fingerprint_after = republish 新指纹。
            assertEquals(fingerprintBefore, row["fingerprint_before"])
            assertEquals(fingerprintAfter, row["fingerprint_after"])
            assertEquals("audit-operator", row["operator"])
            assertNotEquals(fingerprintBefore, fingerprintAfter)
        } finally {
            restoreAnswer(EDIT_CODE, original)
            assertSeedState()
        }
    }

    @Test
    fun `multi-field edit writes one audit row per changed field - I-21`() {
        val originals = captureRow(EDIT_CODE)
        try {
            service.update(
                EDIT_CODE,
                RagFactUpdateRequest(
                    title = originals["title"] as String + " 改",
                    answer = originals["answer"] as String + " X",
                    renderMode = "VERBATIM",
                    riskLevel = "HIGH",
                    status = "REVIEW",
                    replyPolicy = "NEVER"
                )
            )
            val fields = auditRows().map { it["field"] }
            assertEquals(listOf("title", "answer", "render_mode", "risk_level", "status", "reply_policy"), fields)
            // 同一事务内所有 audit 行的指纹对一致。
            val fingerprints = auditRows().map { it["fingerprint_before"] to it["fingerprint_after"] }.toSet()
            assertEquals(1, fingerprints.size)
        } finally {
            restoreRow(EDIT_CODE, originals)
            assertSeedState()
        }
    }

    @Test
    fun `audit rows roll back together with the fact write - I-21 rollback`() {
        val original = answerOf(EDIT_CODE)
        val before = ragKnowledgeBase.snapshot()
        try {
            val template = TransactionTemplate(transactionManager)
            try {
                template.execute {
                    service.update(
                        EDIT_CODE,
                        RagFactUpdateRequest(answer = original + " (never committed)", operator = "c5-tester")
                    )
                    throw RuntimeException("force rollback of the whole republish transaction")
                }
            } catch (expected: RuntimeException) {
                assertEquals("force rollback of the whole republish transaction", expected.message)
            }
            // 事务回滚：fact 未改、audit 行不落库、meta 指纹不变、快照仍是旧实例（01 I-3b）。
            assertEquals(original, answerOf(EDIT_CODE))
            assertEquals(0L, auditCount())
            assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
            assertEquals(EXPECTED_FINGERPRINT, ragKnowledgeBase.fingerprint())
            assertSame(before, ragKnowledgeBase.snapshot())
            ragKnowledgeBase.verifyAndPublish()
        } finally {
            assertSeedState()
        }
    }

    @Test
    fun `toggleEnabled writes enabled audit row and restores seed on re-enable - I-21`() {
        val before = ragKnowledgeBase.snapshot()
        try {
            val disabledFingerprint = service.toggleEnabled(EDIT_CODE, false, "toggle-operator")
            assertNotEquals(EXPECTED_FINGERPRINT, disabledFingerprint)
            assertFalse(enabledOf(EDIT_CODE))
            assertNotSame(before, ragKnowledgeBase.snapshot())
            assertTrue(
                ragKnowledgeBase.enabledFacts().none { it.factCode == EDIT_CODE },
                "disabled fact must not enter candidates (I-2)"
            )
            val disabledAudit = auditRows().single()
            assertEquals("enabled", disabledAudit["field"])
            assertEquals("1", disabledAudit["old_value"])
            assertEquals("0", disabledAudit["new_value"])
            assertEquals("toggle-operator", disabledAudit["operator"])

            // 重新启用：指纹必须回到种子值（A-2 第 5 步语义）。
            val reEnabledFingerprint = service.toggleEnabled(EDIT_CODE, true, "toggle-operator")
            assertEquals(EXPECTED_FINGERPRINT, reEnabledFingerprint)
            assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
            assertTrue(enabledOf(EDIT_CODE))
            assertEquals(2, auditRows().size)
            val reEnabledAudit = auditRows()[1]
            assertEquals("enabled", reEnabledAudit["field"])
            assertEquals("0", reEnabledAudit["old_value"])
            assertEquals("1", reEnabledAudit["new_value"])
            ragKnowledgeBase.verifyAndPublish()
        } finally {
            // 无论如何回到启用态（restore 幂等）。
            if (!enabledOf(EDIT_CODE)) {
                service.toggleEnabled(EDIT_CODE, true, null)
            }
            assertSeedState()
        }
    }

    // ---------------------------------------------------------------- I-22

    @Test
    fun `read-only fields in the request are ignored and DB values win - I-22`() {
        val beforeCode = codeOf(EDIT_CODE)
        val beforeArea = areaOf(EDIT_CODE)
        val beforeSeq = seqOf(EDIT_CODE)
        val beforeLegacy = legacyRuleIdOf(EDIT_CODE)
        val originals = captureRow(EDIT_CODE)
        val newTitle = "改个名"
        try {
            // A-5 场景：body 带 factCode/area/seq/legacyRuleId（外加 title）—— 不报错，
            // 四列只读字段不变，title 生效。
            service.update(
                EDIT_CODE,
                RagFactUpdateRequest(
                    factCode = "KB-XXX-999",
                    area = "ZZZ",
                    seq = 999,
                    legacyRuleId = 999L,
                    title = newTitle
                )
            )
            assertEquals(beforeCode, codeOf(EDIT_CODE))
            assertEquals(beforeArea, areaOf(EDIT_CODE))
            assertEquals(beforeSeq, seqOf(EDIT_CODE))
            assertEquals(beforeLegacy, legacyRuleIdOf(EDIT_CODE))
            assertEquals(newTitle, titleOf(EDIT_CODE))
            val audit = auditRows().single()
            assertEquals("title", audit["field"])
            assertEquals(EDIT_CODE, audit["fact_code"])
        } finally {
            restoreRow(EDIT_CODE, originals)
            assertSeedState()
        }
    }

    @Test
    fun `service never calls verifyAndPublish and never reuses QaRuleAuditService - I-20-I-21 grep`() {
        val source = File(
            "src/main/kotlin/com/weibo/talentintroduction/rag/service/RagFactAdminService.kt"
        ).readText()
        assertFalse(
            source.contains("verifyAndPublish"),
            "RagFactAdminService must not reference verifyAndPublish (P0-3)"
        )
        assertFalse(
            source.contains("QaRuleAuditService"),
            "RagFactAdminService must not reuse QaRuleAuditService (G-4)"
        )
        assertFalse(source.contains("qa_rule"), "no qa_rule coupling (G-4)")
    }

    // ---------------------------------------------------------------- I-23

    @Test
    fun `controller exposes no create or delete endpoints - I-23 grep`() {
        val source = File(
            "src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagFactAdminController.kt"
        ).readText()
        assertFalse(source.contains("DeleteMapping"), "no @DeleteMapping allowed (I-23)")
        assertFalse(
            source.contains("""@PostMapping("")""") || source.contains("""@PostMapping("/")"""),
            "no empty-path POST create allowed (I-23)"
        )
        assertTrue(source.contains("""@RequestMapping("/api/rag/facts")"""))
        assertTrue(source.contains("""@PutMapping("/{factCode}")"""))
        assertTrue(source.contains("""@PostMapping("/{factCode}/enable")"""))
        assertTrue(source.contains("""@PostMapping("/{factCode}/disable")"""))
    }

    @Test
    fun `list exposes 45 facts with seed fingerprint and no id field - G-1`() {
        val result = service.list()
        assertEquals(45, result.factCount)
        assertEquals(45, result.facts.size)
        assertEquals(EXPECTED_FINGERPRINT, result.fingerprint)
        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result)
        assertFalse(json.contains("\"id\""), "auto-increment id must never surface (G-1)")
        assertTrue(result.facts.any { it.factCode == VERBATIM_CODE })
    }

    // ---------------------------------------------------------------- helpers

    private fun codeOf(code: String): String =
        queryColumn(code, "fact_code") { it.getString("fact_code") }!!
    private fun areaOf(code: String): String =
        queryColumn(code, "area") { it.getString("area") }!!
    private fun seqOf(code: String): Int =
        queryColumn(code, "seq") { it.getInt("seq") }!!
    private fun legacyRuleIdOf(code: String): Long? =
        queryColumn(code, "legacy_rule_id") { (it.getObject("legacy_rule_id") as? Number)?.toLong() }
    private fun enabledOf(code: String): Boolean =
        queryColumn(code, "CAST(enabled AS UNSIGNED)") { it.getLong(1) } == 1L
    private fun answerOf(code: String): String =
        queryColumn(code, "answer") { it.getString("answer") }!!
    private fun titleOf(code: String): String =
        queryColumn(code, "title") { it.getString("title") }!!
    private fun <T> queryColumn(code: String, column: String, extract: (java.sql.ResultSet) -> T): T? =
        jdbc.query(
            "SELECT $column FROM rag_fact WHERE fact_code = :code",
            mapOf("code" to code)
        ) { rs, _ -> extract(rs) }.firstOrNull()

    private fun metaFingerprint(): String =
        jdbc.queryForObject(
            "SELECT fingerprint FROM rag_kb_meta WHERE id = 1",
            mapOf<String, Any>(),
            String::class.java
        )!!

    private fun auditCount(): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM rag_fact_audit",
            mapOf<String, Any>(),
            Long::class.java
        )!!

    private fun auditRows(): List<Map<String, String?>> =
        jdbc.query(
            """
            SELECT fact_code, field, old_value, new_value, fingerprint_before, fingerprint_after, operator
              FROM rag_fact_audit
             ORDER BY id ASC
            """.trimIndent(),
            mapOf<String, Any>()
        ) { rs, _ ->
            mapOf(
                "fact_code" to rs.getString("fact_code"),
                "field" to rs.getString("field"),
                "old_value" to rs.getString("old_value"),
                "new_value" to rs.getString("new_value"),
                "fingerprint_before" to rs.getString("fingerprint_before"),
                "fingerprint_after" to rs.getString("fingerprint_after"),
                "operator" to rs.getString("operator")
            )
        }

    /** 捕获一条事实的全部可编辑列（种子态基准），供用例 finally 整体复位。 */
    private fun captureRow(code: String): Map<String, Any?> =
        jdbc.query(
            """
            SELECT title, answer, question_variants, coverage_keys,
                   render_mode, risk_level, status, reply_policy,
                   CAST(enabled AS UNSIGNED) AS enabled
              FROM rag_fact
             WHERE fact_code = :code
            """.trimIndent(),
            mapOf("code" to code)
        ) { rs, _ ->
            mapOf(
                "title" to rs.getString("title"),
                "answer" to rs.getString("answer"),
                "question_variants" to rs.getString("question_variants"),
                "coverage_keys" to rs.getString("coverage_keys"),
                "render_mode" to rs.getString("render_mode"),
                "risk_level" to rs.getString("risk_level"),
                "status" to rs.getString("status"),
                "reply_policy" to rs.getString("reply_policy"),
                "enabled" to (rs.getLong("enabled") == 1L)
            )
        }.firstOrNull()!!

    /** 把整行可编辑列复位为捕获值（enabled 走 CAST 语义，兼容 TINYINT(1) 映射）。 */
    private fun restoreRow(code: String, original: Map<String, Any?>) {
        jdbc.update(
            """
            UPDATE rag_fact
               SET title = :title, answer = :answer, question_variants = :question_variants,
                   coverage_keys = :coverage_keys, render_mode = :render_mode,
                   risk_level = :risk_level, status = :status, reply_policy = :reply_policy,
                   enabled = :enabled
             WHERE fact_code = :code
            """.trimIndent(),
            mapOf(
                "code" to code,
                "title" to original["title"],
                "answer" to original["answer"],
                "question_variants" to original["question_variants"],
                "coverage_keys" to original["coverage_keys"],
                "render_mode" to original["render_mode"],
                "risk_level" to original["risk_level"],
                "status" to original["status"],
                "reply_policy" to original["reply_policy"],
                "enabled" to (if (original["enabled"] == true) 1 else 0)
            )
        )
    }

    private fun restoreAnswer(code: String, original: String) {
        jdbc.update(
            "UPDATE rag_fact SET answer = :answer WHERE fact_code = :code",
            mapOf("answer" to original, "code" to code)
        )
    }

    /** 每个用例的收尾：库回到 V112 种子态（meta 指纹复位 + 审计清空）。 */
    private fun assertSeedState() {
        jdbc.update("DELETE FROM rag_fact_audit", mapOf<String, Any>())
        ragKnowledgeBase.republish {}
        assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
        assertEquals(45L, jdbc.queryForObject(
            "SELECT COUNT(*) FROM rag_fact",
            mapOf<String, Any>(),
            Long::class.java
        )!!)
        assertEquals(0L, auditCount())
        assertDoesNotThrow { ragKnowledgeBase.verifyAndPublish() }
    }
}

private fun assertDoesNotThrow(block: () -> Unit) {
    block()
}
