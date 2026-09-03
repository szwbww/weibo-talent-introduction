package com.weibo.talentintroduction.rag

import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer

/**
 * 计划 01 (T6 + 验收标准): 对真实 MySQL 断言数据层全部不变量。
 *
 * 覆盖: I-1 / I-2 / I-3 (+P0-3) / I-3b / I-4 / I-5 / I-6 / D-3 / G-2。
 *
 * 两种数据源模式（均需 `-DmigrationIt=true`，与仓内所有 Docker/Testcontainers
 * 测试同一门控，保证普通 `mvn test` 全绿）：
 * 1. 默认：Testcontainers 起 fresh mysql:8.0.36，Flyway V1..V112 全链迁移后种子
 *    就位（本环境 fresh 链被既有 V82 基线门禁挡住 —— 环境阻塞，见 execution.md）；
 * 2. 外部已就绪库：设环境变量 `RAG_KB_TEST_DB_URL`（+ 可选 `RAG_KB_TEST_DB_USERNAME` /
 *    `RAG_KB_TEST_DB_PASSWORD`，默认 root/root）时不启容器，直接连该库 —— 控制器
 *    授权的 scratch 补丁链替代验证路径。
 *
 * 每个用例结束都把 DB 恢复为 V112 种子态，保证用例之间无顺序耦合。
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
class RagKnowledgeBaseTest {

    companion object {
        /** G-2 常量（A1 修订后；与 export_rag_kb_sql.py / V112 一致）。 */
        private const val EXPECTED_FINGERPRINT = "e62421a42c432cf3"
        private const val DRIFT_CODE = "KB-PROG-001"
        private val HEX16 = Regex("[0-9a-f]{16}")

        /** 外部已就绪库（scratch 补丁链）模式；设置后不再启动 Testcontainers。 */
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
    private lateinit var ragKnowledgeBase: RagKnowledgeBase

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    // 与 AuthFlowIntegrationTest 相同的上下文配方：这两个 bean 在启动期会触达 ES。
    @MockBean
    private lateinit var expertIndexService: ExpertIndexService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    // ---------------------------------------------------------------- I-3 / G-2

    @Test
    fun `seeded corpus exposes 45 facts, 44 enabled and the G-2 fingerprint`() {
        val snapshot = ragKnowledgeBase.snapshot()
        assertEquals(45, snapshot.facts.size)
        assertEquals(EXPECTED_FINGERPRINT, snapshot.fingerprint)
        assertEquals(EXPECTED_FINGERPRINT, ragKnowledgeBase.fingerprint())
        assertEquals(44, ragKnowledgeBase.enabledFacts().size)
        assertFalse(ragKnowledgeBase.enabledFacts().any { it.factCode == "KB-APP-017" })
        assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
        assertEquals(45L, metaFactCount())

        // VERBATIM 恰好 7 条（现状审计的语料实测）。
        assertEquals(7, snapshot.facts.count { it.renderMode == "VERBATIM" })

        // 启动期校验路径再次执行应通过（库里事实 == 迁移写入常量）。
        ragKnowledgeBase.verifyAndPublish()
    }

    @Test
    fun `verifyAndPublish rejects a drifted corpus and reports both fingerprints`() {
        val original = originalAnswer(DRIFT_CODE)
        try {
            updateAnswer(DRIFT_CODE, original + " X")
            val error = assertThrows(IllegalStateException::class.java) {
                ragKnowledgeBase.verifyAndPublish()
            }
            // 异常消息必须同时含期望值与实际值（验收 I-3 / A-2 第 3 步）。
            val hexTokens = HEX16.findAll(error.message ?: "").map { it.value }.toList()
            assertTrue(hexTokens.contains(EXPECTED_FINGERPRINT), error.message)
            assertTrue(hexTokens.any { it != EXPECTED_FINGERPRINT }, error.message)
            assertTrue(error.message!!.contains("expected $EXPECTED_FINGERPRINT"), error.message)
            val actual = hexTokens.first { it != EXPECTED_FINGERPRINT }
            assertNotEquals(EXPECTED_FINGERPRINT, actual)
            assertTrue(error.message!!.contains("computed $actual"), error.message)
        } finally {
            updateAnswer(DRIFT_CODE, original)
            // 恢复后 meta 与库一致，门禁重新放行 —— 用例结束回到种子态。
            ragKnowledgeBase.republish {}
            assertDoesNotThrow { ragKnowledgeBase.verifyAndPublish() }
        }
    }

    @Test
    fun `republish changes a fact then verifyAndPublish passes - P0-3`() {
        val before = ragKnowledgeBase.snapshot()
        val original = originalAnswer(DRIFT_CODE)
        try {
            val newFingerprint = ragKnowledgeBase.republish {
                updateAnswer(DRIFT_CODE, original + " (revised)")
            }
            // ① 不抛异常 ② meta 指纹已更新为新值 ③ verifyAndPublish 通过。
            assertEquals(newFingerprint, metaFingerprint())
            assertNotEquals(EXPECTED_FINGERPRINT, newFingerprint)
            ragKnowledgeBase.verifyAndPublish()
            // I-6: 成功提交后快照整体替换为新实例。
            assertNotSame(before, ragKnowledgeBase.snapshot())
            assertEquals(newFingerprint, ragKnowledgeBase.fingerprint())
        } finally {
            ragKnowledgeBase.republish { updateAnswer(DRIFT_CODE, original) }
            assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
            assertDoesNotThrow { ragKnowledgeBase.verifyAndPublish() }
        }
    }

    @Test
    fun `republish rollback keeps old snapshot and unchanged meta - I-3b`() {
        val before = ragKnowledgeBase.snapshot()
        assertThrows(RuntimeException::class.java) {
            ragKnowledgeBase.republish {
                updateAnswer(DRIFT_CODE, originalAnswer(DRIFT_CODE) + " (never committed)")
                throw RuntimeException("force rollback")
            }
        }
        // 事务回滚：meta 未变、快照仍是旧实例（I-3b）。
        assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
        assertEquals(45L, metaFactCount())
        assertSame(before, ragKnowledgeBase.snapshot())
        assertDoesNotThrow { ragKnowledgeBase.verifyAndPublish() }
    }

    // ---------------------------------------------------------------- I-6

    @Test
    fun `snapshot is stable until a successful republish - I-6`() {
        val first = ragKnowledgeBase.snapshot()
        val second = ragKnowledgeBase.snapshot()
        assertSame(first, second)

        val original = originalAnswer(DRIFT_CODE)
        try {
            ragKnowledgeBase.republish { updateAnswer(DRIFT_CODE, original + " (v2)") }
            assertNotSame(second, ragKnowledgeBase.snapshot())
        } finally {
            ragKnowledgeBase.republish { updateAnswer(DRIFT_CODE, original) }
            assertEquals(EXPECTED_FINGERPRINT, metaFingerprint())
            assertDoesNotThrow { ragKnowledgeBase.verifyAndPublish() }
        }
    }

    // ---------------------------------------------------------------- I-1

    @Test
    fun `45 fact codes are unique and self-consistent with area and seq - I-1`() {
        val facts = ragKnowledgeBase.snapshot().facts
        assertEquals(45, facts.map { it.factCode }.toSet().size)
        facts.forEach { fact ->
            val expected = "KB-${fact.area}-${fact.seq.toString().padStart(3, '0')}"
            assertEquals(expected, fact.factCode)
        }
    }

    // ---------------------------------------------------------------- I-2

    @Test
    fun `disabled fact normalizes to DISABLED and never enters candidates - I-2`() {
        val facts = ragKnowledgeBase.snapshot().facts
        val disabled = facts.filter { !it.enabled }
        assertEquals(1, disabled.size)
        assertEquals("KB-APP-017", disabled.single().factCode)
        assertEquals("DISABLED", disabled.single().effectiveStatus())
        assertTrue(ragKnowledgeBase.enabledFacts().none { it.factCode == "KB-APP-017" })
    }

    // ---------------------------------------------------------------- I-4 / I-5

    @Test
    fun `KB-GOV-004 parses separators per I-4`() {
        val fact = factByCode("KB-GOV-004")
        val variants = fact.variants()
        assertEquals(6, variants.size)
        assertEquals("responsible government organization", variants.first())
        val coverage = fact.coverageKeys()
        assertEquals(4, coverage.size)
        assertEquals(
            listOf(
                "governance.sponsor_level",
                "governance.responsible_organization",
                "governance.national_lead",
                "governance.local_implementation"
            ),
            coverage
        )
        assertEquals(3, fact.sourceRefs().size)
        assertEquals("QA_FACT_PROPOSAL:fact-04", fact.sourceRefs().first())
    }

    @Test
    fun `KB-GOV-004 retrieval text layout matches the script - I-5`() {
        val fact = factByCode("KB-GOV-004")
        assertTrue(fact.retrievalText.startsWith("项目组织层级 | responsible government organization | "))
        assertTrue(fact.retrievalText.endsWith(fact.answer))
        // variants 与 keywords 同源导致该短语出现两次 —— 照抄不去重。
        val occurrences = Regex(Regex.escape("responsible government organization"))
            .findAll(fact.retrievalText).count()
        assertEquals(2, occurrences)
    }

    // ---------------------------------------------------------------- D-3

    @Test
    fun `mandatory rules hold 6 rows with D-3 COMPENSATION at sort order 15`() {
        val rules = ragKnowledgeBase.snapshot().mandatoryRules
        assertEquals(6, rules.size)
        assertEquals(listOf(10, 15, 20, 30, 40, 50), rules.map { it.sortOrder })
        val compensation = rules.single { it.sortOrder == 15 }
        assertEquals(listOf("COMPENSATION"), compensation.matchGroups)
        assertEquals(listOf("KB-FUND-033"), compensation.factCodes)
        // I-2: 强制规则引用的 fact_code 必须都存在于全量语料。
        rules.flatMap { it.factCodes }.forEach { code ->
            assertNotNull(ragKnowledgeBase.snapshot().facts.firstOrNull { it.factCode == code })
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun factByCode(code: String): RagFact =
        ragKnowledgeBase.snapshot().facts.single { it.factCode == code }

    private fun originalAnswer(code: String): String =
        ragKnowledgeBase.snapshot().facts.single { it.factCode == code }.answer

    private fun updateAnswer(code: String, answer: String) {
        jdbc.update(
            "UPDATE rag_fact SET answer = :answer WHERE fact_code = :code",
            mapOf("answer" to answer, "code" to code)
        )
    }

    private fun metaFingerprint(): String =
        jdbc.queryForObject(
            "SELECT fingerprint FROM rag_kb_meta WHERE id = 1",
            mapOf<String, Any>(),
            String::class.java
        )!!

    private fun metaFactCount(): Long =
        jdbc.queryForObject(
            "SELECT fact_count FROM rag_kb_meta WHERE id = 1",
            mapOf<String, Any>(),
            Long::class.java
        )!!
}

private fun assertDoesNotThrow(block: () -> Unit) {
    block()
}
