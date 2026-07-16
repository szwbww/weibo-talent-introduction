package com.weibo.talentintroduction.audit.repository

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OperatorActionLogRepositoryTest {

    companion object {
        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for operator_action_log repository tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.flyway.enabled") { "true" }
        }
    }

    @Autowired
    lateinit var repository: OperatorActionLogRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbc.execute("DELETE FROM operator_action_log")
    }

    @Test
    fun `findLatest returns row with larger id when same inboundProcessingId and same created_at`() {
        val ts = "2024-06-15 10:00:00"
        jdbc.update(
            """INSERT INTO operator_action_log
               (target_type, target_id, inbound_processing_id, action_type, action_summary, after_value, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "INBOUND_MAIL_PROCESSING", 100L, 100L,
            "AI_REPLY_DRAFT_NEEDS_REVIEW", "草稿-需审核",
            """{"draftIdentity":"first","readiness":"NEEDS_REVIEW"}""", ts
        )
        jdbc.update(
            """INSERT INTO operator_action_log
               (target_type, target_id, inbound_processing_id, action_type, action_summary, after_value, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "INBOUND_MAIL_PROCESSING", 100L, 100L,
            "AI_REPLY_DRAFT_READY", "草稿-可发送",
            """{"draftIdentity":"second","readiness":"READY"}""", ts
        )

        val result = repository.findLatestAiDraftByInboundProcessingId(100L)

        assertNotNull(result)
        val afterJson = result!!.afterValue!!
        assert(afterJson.contains("\"second\"")) {
            "Expected the row with larger id (second insert) but got: $afterJson"
        }
    }

    @Test
    fun `findLatest returns null when no matching inboundProcessingId`() {
        jdbc.update(
            """INSERT INTO operator_action_log
               (target_type, target_id, inbound_processing_id, action_type, action_summary, after_value, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "INBOUND_MAIL_PROCESSING", 200L, 200L,
            "AI_REPLY_DRAFT_READY", "草稿-可发送",
            """{"draftIdentity":"other","readiness":"READY"}""", "2024-06-15 10:00:00"
        )

        val result = repository.findLatestAiDraftByInboundProcessingId(999L)

        assertNull(result)
    }

    @Test
    fun `findLatest does not return rows for different inboundProcessingId`() {
        val ts = "2024-06-15 10:00:00"
        jdbc.update(
            """INSERT INTO operator_action_log
               (target_type, target_id, inbound_processing_id, action_type, action_summary, after_value, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "INBOUND_MAIL_PROCESSING", 300L, 300L,
            "AI_REPLY_DRAFT_BLOCKED", "草稿-受阻",
            """{"draftIdentity":"inbound300","readiness":"BLOCKED"}""", ts
        )
        jdbc.update(
            """INSERT INTO operator_action_log
               (target_type, target_id, inbound_processing_id, action_type, action_summary, after_value, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            "INBOUND_MAIL_PROCESSING", 400L, 400L,
            "AI_REPLY_DRAFT_READY", "草稿-可发送",
            """{"draftIdentity":"inbound400","readiness":"READY"}""", ts
        )

        val result = repository.findLatestAiDraftByInboundProcessingId(300L)

        assertNotNull(result)
        assert(result!!.afterValue!!.contains("inbound300"))
    }
}
