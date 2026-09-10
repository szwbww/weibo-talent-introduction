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
        jdbc.execute("DELETE FROM expert_contact")
        jdbc.execute("DELETE FROM campaign")
        jdbc.execute("DELETE FROM mail_sender_account")
    }

    private fun insertRow(
        imapUid: Long,
        accountCode: String = "sender-1",
        processStatus: String = "MANUAL_REVIEW",
        processReason: String = "CONTACT_NOT_FOUND",
        reasonType: String? = "UNMATCHED_CONTACT",
        receivedAt: LocalDateTime = LocalDateTime.of(2026, 8, 1, 9, 0, 0),
        expertContactId: Long? = null,
        fromEmail: String = "expert@test.com",
        subject: String = "Subject"
    ): Long {
        jdbc.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, received_at,
                 process_status, process_reason, reason_type, expert_contact_id, retry_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            accountCode, imapUid, "msg-$imapUid", fromEmail, subject, receivedAt,
            processStatus, processReason, reasonType, expertContactId
        )
        val id = jdbc.queryForObject(
            "SELECT id FROM inbound_mail_processing WHERE sender_account_code = ? AND imap_uid = ?",
            Long::class.java,
            accountCode, imapUid
        )
        return requireNotNull(id) { "inserted row must have an id" }
    }

    /** 已绑定来信需要真实 expert_contact 行（V5 外键）；返回其 id。 */
    private fun seedBoundContact(): Long {
        jdbc.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES
                (9001, 'sender-seed', 'seed@example.com', 'Seed', 'smtp.example.com', 465,
                 'seed@example.com', 'pwd', 'imap.example.com', 993, 'seed@example.com', 'pwd')
            """
        )
        jdbc.update(
            """
            INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id)
            VALUES (9001, 'IT_UNMATCHED', 'IT Unmatched', 9001)
            """
        )
        jdbc.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, current_status)
            VALUES (9001, 9001, '0000-0000-0000-9001', 'bound@example.com', 'NEW')
            """
        )
        return 9001L
    }

    @Test
    fun `findUnmatchedManualReviewQueue keeps only unbound manual review rows of the requested accounts`() {
        val contactId = seedBoundContact()
        val unboundId = insertRow(100L, reasonType = null)
        insertRow(101L, expertContactId = contactId)
        insertRow(102L, processStatus = "PROCESSED", processReason = "MANUAL_RESOLVED", reasonType = "MANUAL_RESOLVED")
        insertRow(103L, accountCode = "sender-2")

        val ids = repository.findUnmatchedManualReviewQueue(listOf("sender-1"), null, 20, 0).map { it.id }

        // I-1：reason_type 为 NULL 的未绑定 MANUAL_REVIEW 仍入选；已绑定/已处理/账号外行排除。
        assertEquals(listOf(unboundId), ids)
        assertEquals(1L, repository.countUnmatchedManualReviewQueue(listOf("sender-1"), null))
    }

    @Test
    fun `findUnmatchedManualReviewQueue searches both from_email and subject and pages stably`() {
        val sameTime = LocalDateTime.of(2026, 8, 3, 8, 0, 0)
        val first = insertRow(200L, receivedAt = sameTime, fromEmail = "plain@test.com", subject = "Hello")
        val second = insertRow(201L, receivedAt = sameTime, fromEmail = "acceptance-key@test.com", subject = "Other")
        val third = insertRow(202L, receivedAt = sameTime, fromEmail = "third@test.com", subject = "acceptance-key subject")
        val older = insertRow(203L, receivedAt = sameTime.minusDays(1), fromEmail = "older@test.com", subject = "Older")

        val accounts = listOf("sender-1")
        val ordered = repository.findUnmatchedManualReviewQueue(accounts, null, 20, 0).map { it.id }
        // I-8：received_at DESC, id DESC —— 同一时间按 id 降序。
        assertEquals(listOf(third, second, first, older), ordered)

        val page1 = repository.findUnmatchedManualReviewQueue(accounts, null, 2, 0).map { it.id }
        val page2 = repository.findUnmatchedManualReviewQueue(accounts, null, 2, 2).map { it.id }
        assertEquals(listOf(third, second), page1)
        assertEquals(listOf(first, older), page2)
        assertEquals(4L, repository.countUnmatchedManualReviewQueue(accounts, null))

        // OR 模糊匹配：from_email 命中 second，subject 命中 third；count 与 list 同 WHERE。
        val matched = repository.findUnmatchedManualReviewQueue(accounts, "acceptance-key", 20, 0).map { it.id }
        assertEquals(listOf(third, second), matched)
        assertEquals(2L, repository.countUnmatchedManualReviewQueue(accounts, "acceptance-key"))
    }

    private fun insertResolvedRow(
        imapUid: Long,
        processStatus: String,
        processReason: String,
        reasonType: String?
    ): Long = insertRow(
        imapUid = imapUid,
        processStatus = processStatus,
        processReason = processReason,
        reasonType = reasonType
    )

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
