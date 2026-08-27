package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.LocalDateTime

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InboundMailProcessingRepositoryTest {

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
                "Docker is required for inbound_mail_processing repository tests"
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
    lateinit var repository: InboundMailProcessingRepository

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbc.execute("DELETE FROM inbound_mail_processing")
    }

    private fun insertResolvedRow(
        imapUid: Long,
        processStatus: String,
        processReason: String,
        reasonType: String?
    ): Long {
        jdbc.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, received_at,
                 process_status, process_reason, reason_type, expert_contact_id, retry_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 0)
            """,
            "sender-1", imapUid, "msg-$imapUid", "expert@test.com", "Subject",
            LocalDateTime.of(2026, 8, 1, 9, 0, 0),
            processStatus, processReason, reasonType
        )
        val id = jdbc.queryForObject(
            "SELECT id FROM inbound_mail_processing WHERE sender_account_code = ? AND imap_uid = ?",
            Long::class.java,
            "sender-1", imapUid
        )
        return requireNotNull(id) { "inserted row must have an id" }
    }

    @Test
    fun `reopenManualResolved hits only exact resolved triplet and clears resolution fields`() {
        val id = insertResolvedRow(100L, "PROCESSED", "MANUAL_RESOLVED", "MANUAL_RESOLVED")
        val now = LocalDateTime.of(2026, 8, 2, 10, 30, 0)

        val updated = repository.reopenManualResolved(id, now)

        assertEquals(1, updated)
        val reopened: InboundMailProcessing = repository.findById(id)
            .orElseThrow { AssertionError("reopened row must still exist") }
        assertEquals("MANUAL_REVIEW", reopened.processStatus)
        assertEquals("MANUAL_REOPENED", reopened.processReason)
        assertNull(reopened.reasonType)
        assertNull(reopened.resolvedBy)
        assertNull(reopened.resolvedAt)
        assertEquals(now, reopened.updatedAt)
        // I-5: identity, sender account, uid and binding survive untouched.
        assertEquals("sender-1", reopened.senderAccountCode)
        assertEquals(100L, reopened.imapUid)
        assertNull(reopened.expertContactId)
        assertEquals("msg-100", reopened.messageId)
        assertEquals("expert@test.com", reopened.fromEmail)
    }

    @Test
    fun `reopenManualResolved misses when process_status is not PROCESSED`() {
        val id = insertResolvedRow(200L, "MANUAL_REVIEW", "MANUAL_RESOLVED", "MANUAL_RESOLVED")

        val updated = repository.reopenManualResolved(id, LocalDateTime.of(2026, 8, 2, 10, 30, 0))

        assertEquals(0, updated)
        val row = requireNotNull(repository.findById(id).orElse(null)) { "row must be unchanged" }
        assertEquals("MANUAL_REVIEW", row.processStatus)
        assertEquals("MANUAL_RESOLVED", row.processReason)
        assertEquals("MANUAL_RESOLVED", row.reasonType)
    }

    @Test
    fun `reopenManualResolved misses when process_reason is not MANUAL_RESOLVED`() {
        val id = insertResolvedRow(300L, "PROCESSED", "MANUAL_BOUND", "MANUAL_RESOLVED")

        val updated = repository.reopenManualResolved(id, LocalDateTime.of(2026, 8, 2, 10, 30, 0))

        assertEquals(0, updated)
        val row = requireNotNull(repository.findById(id).orElse(null)) { "row must be unchanged" }
        assertEquals("PROCESSED", row.processStatus)
        assertEquals("MANUAL_BOUND", row.processReason)
        assertEquals("MANUAL_RESOLVED", row.reasonType)
    }

    @Test
    fun `reopenManualResolved misses when reason_type is not MANUAL_RESOLVED`() {
        val id = insertResolvedRow(400L, "PROCESSED", "MANUAL_RESOLVED", "AUTO_NOOP")

        val updated = repository.reopenManualResolved(id, LocalDateTime.of(2026, 8, 2, 10, 30, 0))

        assertEquals(0, updated)
        val row = requireNotNull(repository.findById(id).orElse(null)) { "row must be unchanged" }
        assertEquals("PROCESSED", row.processStatus)
        assertEquals("MANUAL_RESOLVED", row.processReason)
        assertEquals("AUTO_NOOP", row.reasonType)
    }
}
