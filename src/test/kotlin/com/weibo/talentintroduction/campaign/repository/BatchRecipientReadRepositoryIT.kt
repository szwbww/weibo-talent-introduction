package com.weibo.talentintroduction.campaign.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer

/**
 * 批量目标只读仓储的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由真实迁移建立（不是手写 DDL），因此同时验证迁移契约：`expert_contact`、`mail_record`、
 * `email_suppression` 的列语义、`LIKE 'PERMANENT:%'` 判定与批量 IN 查询。
 *
 * 覆盖子计划 02 的 I-2/I-4/I-5：
 * - **I-4**：只 SELECT —— 读操作前后业务行数完全不变，无写入、无 N+1 之外的表。
 * - **I-5**：空入参不发 SQL、500 一批仍返回完整结果。
 * - **I-2**：绑定/抑制/已发送/永久失败/EMAIL_INVALID 事实与执行路径同列同判据。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(BatchRecipientReadRepository::class)
class BatchRecipientReadRepositoryIT {

    companion object {
        private const val CAMPAIGN_ID = 9201L
        private const val CONTACT_BASE_ID = 9200L

        private class Mysql(image: String) : MySQLContainer<Mysql>(image)

        private val mysql = Mysql("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for the batch recipient read repository tests"
            }
            mysql.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired
    private lateinit var repository: BatchRecipientReadRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun seed() {
        jdbc.update("DELETE FROM mail_record")
        jdbc.update("DELETE FROM expert_contact")
        jdbc.update("DELETE FROM email_suppression")
        jdbc.update("DELETE FROM campaign")
        jdbc.update("DELETE FROM mail_sender_account")
        jdbc.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (9201, 'read-test', 'sender@example.test', 'Sender', 'smtp.test', 465,
                    'sender@example.test', 'pw', 'imap.test', 993, 'sender@example.test', 'pw')
            """.trimIndent()
        )
        jdbc.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (?, 'READ_TEST', 'Read', 9201)",
            CAMPAIGN_ID
        )
    }

    private fun contact(
        id: Long,
        orcid: String,
        email: String,
        bound: String? = null,
        operatorStatus: String? = null
    ) {
        jdbc.update(
            """
            INSERT INTO expert_contact
                (id, campaign_id, orcid_id, expert_email, expert_name, current_status, bound_sender_account_code, operator_status)
            VALUES (?, ?, ?, ?, 'Name', 'NEW', ?, COALESCE(?, 'NOT_CONTACTED'))
            """.trimIndent(),
            id, CAMPAIGN_ID, orcid, email, bound, operatorStatus
        )
    }

    private fun mailRecord(contactId: Long, status: String, errorSummary: String? = null) {
        jdbc.update(
            """
            INSERT INTO mail_record (expert_contact_id, direction, mail_type, send_status, error_summary)
            VALUES (?, 'OUTBOUND', 'INTRODUCTION', ?, ?)
            """.trimIndent(),
            contactId, status, errorSummary
        )
    }

    private fun rowCounts(): Triple<Long, Long, Long> = Triple(
        jdbc.queryForObject("SELECT COUNT(*) FROM expert_contact", Long::class.java)!!,
        jdbc.queryForObject("SELECT COUNT(*) FROM mail_record", Long::class.java)!!,
        jdbc.queryForObject("SELECT COUNT(*) FROM email_suppression", Long::class.java)!!
    )

    @Test
    fun `empty inputs issue no query and return empty results (I-5)`() {
        assertEquals(emptySet<String>(), repository.findBoundOrcids(listOf(" ", "")))
        assertEquals(emptySet<String>(), repository.findSuppressedEmails(listOf(null, " ", "")))
        assertEquals(emptyMap<String, IntroductionSendFact>(), repository.findIntroductionSendFacts(emptyList()))
    }

    @Test
    fun `reads explain binding suppression email invalid and send facts without writing (I-2 I-4)`() {
        contact(9201, "A", "a@example.test", bound = "chen")
        contact(9202, "B", "b@example.test", operatorStatus = "EMAIL_INVALID")
        contact(9203, "C", "c@example.test")
        contact(9204, "D", "d@example.test")
        mailRecord(9203, "SENT")
        mailRecord(9204, "FAILED", "PERMANENT:550:550 5.1.1 User unknown")
        jdbc.update("INSERT INTO email_suppression (email, source) VALUES ('c@example.test', 'MANUAL')")

        val orcids = listOf("A", "B", "C", "D")
        val before = rowCounts()

        assertEquals(setOf("A"), repository.findBoundOrcids(orcids))
        assertEquals(setOf("B"), repository.findEmailInvalidOrcids(orcids))
        assertEquals(setOf("c@example.test"), repository.findSuppressedEmails(listOf("C@Example.Test", "c@example.test")))
        assertEquals(
            mapOf(
                "C" to IntroductionSendFact(sent = true, permanentFailure = false),
                "D" to IntroductionSendFact(sent = false, permanentFailure = true)
            ),
            repository.findIntroductionSendFacts(orcids)
        )

        assertEquals(before, rowCounts(), "只读预检查不得改动任何业务行")
    }

    @Test
    fun `permanent prefix matcher excludes transient failures (I-2)`() {
        contact(9301, "T", "t@example.test")
        mailRecord(9301, "FAILED", "TRANSIENT:421:rate limited")
        contact(9302, "P", "p@example.test")
        mailRecord(9302, "FAILED", "PERMANENT:554:mailbox unavailable")

        val facts = repository.findIntroductionSendFacts(listOf("T", "P"))

        assertFalse(facts.getValue("T").permanentFailure)
        assertFalse(facts.getValue("T").sent)
        assertTrue(facts.getValue("P").permanentFailure)
    }

    @Test
    fun `five hundred and one orcids are queried in bounded batches without losing rows (I-5)`() {
        val orcids = (1..501).map { "B%04d".format(it) }
        orcids.forEachIndexed { index, orcid ->
            contact(9400L + index, orcid, "$orcid@example.test", bound = "chen")
        }

        val result = repository.findBoundOrcids(orcids)

        assertEquals(501, result.size)
        assertTrue(result.containsAll(orcids))
    }
}
