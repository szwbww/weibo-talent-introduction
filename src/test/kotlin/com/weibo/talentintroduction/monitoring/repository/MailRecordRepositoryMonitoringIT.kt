package com.weibo.talentintroduction.monitoring.repository

import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
class MailRecordRepositoryMonitoringIT {
    companion object {
        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        private val shanghaiZone: ZoneId = ZoneId.of("Asia/Shanghai")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for MailRecordRepository monitoring tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired
    private lateinit var mailRecordRepository: MailRecordRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanMailRecords() {
        jdbcTemplate.execute("DELETE FROM inbound_mail_processing")
        jdbcTemplate.execute("DELETE FROM mail_record")
    }

    @Test
    fun `countFailedOutboundBetween counts FAILED by created_at and excludes SENT`() {
        seedBaseContact()
        val todayStart = LocalDate.now(shanghaiZone).atStartOfDay()
        val todayEnd = todayStart.plusDays(1)
        val sentAt = todayStart.plusHours(10)
        val createdAt = todayStart.plusHours(9)

        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'MANUAL', 'msg-sent', 'SENT', ?, ?),
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'MANUAL', 'msg-manual-failed', 'FAILED', NULL, ?),
                (1, 'OUTBOUND', 'QA_REPLY', 'sender', 'SYSTEM', 'msg-auto-failed', 'FAILED', ?, ?)
            """.trimIndent(),
            sentAt, createdAt, createdAt, sentAt, createdAt
        )

        assertEquals(2L, mailRecordRepository.countFailedOutboundBetween(todayStart, todayEnd))
        assertEquals(1L, mailRecordRepository.countOutboundByMailTypeBetween("INTRODUCTION", todayStart, todayEnd))
    }

    @Test
    fun `aggregateSenderAccountStats failed_count only includes FAILED status`() {
        seedBaseContact()
        val todayStart = LocalDate.now(shanghaiZone).atStartOfDay()
        val todayEnd = todayStart.plusDays(1)
        val sentAt = todayStart.plusHours(11)
        val createdAt = todayStart.plusHours(11)

        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'MANUAL', 'msg-sent', 'SENT', ?, ?),
                (1, 'OUTBOUND', 'QA_REPLY', 'sender', 'SYSTEM', 'msg-auto-failed', 'FAILED', ?, ?)
            """.trimIndent(),
            sentAt, createdAt, sentAt, createdAt
        )

        val stats = mailRecordRepository.aggregateSenderAccountStats(todayStart, todayEnd)
        assertEquals(1, stats.size)
        assertEquals("sender", stats[0].senderAccountCode)
        assertEquals(1L, stats[0].introductionCount)
        assertEquals(1L, stats[0].failedCount)
    }

    @Test
    fun `listMailbox works when outbound and inbound text columns have different collations`() {
        seedBaseContact()
        jdbcTemplate.execute("ALTER TABLE mail_record MODIFY subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        jdbcTemplate.execute("ALTER TABLE mail_record MODIFY body LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")
        jdbcTemplate.execute("ALTER TABLE inbound_mail_processing MODIFY body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci")

        val receivedAt = LocalDate.now(shanghaiZone).atStartOfDay().plusHours(8)
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 subject, body, send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-out',
                 'outbound subject', 'outbound body', 'SENT', ?, ?)
            """.trimIndent(),
            receivedAt.minusHours(1), receivedAt.minusHours(1)
        )
        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, body,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES
                ('sender', 1001, 'msg-in', 'reply@example.com', 'inbound subject', 'inbound body',
                 ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1)
            """.trimIndent(),
            receivedAt
        )

        val rows = mailRecordRepository.listMailbox(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 0,
            limit = 10,
            offset = 0
        )

        assertEquals(listOf("INBOUND_PROCESSING", "MAIL_RECORD"), rows.map { it.source })
    }

    @Test
    fun `countPendingExperts excludes unmatched manual review and processed inbound`() {
        seedBaseContact()
        seedSecondExpertContact()
        val receivedAt = LocalDate.now(shanghaiZone).atStartOfDay().plusHours(8)

        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, body,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES
                ('sender', 1001, 'msg-linked-pending', 'linked@example.com', 'pending subject', 'pending body',
                 ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 1002, 'msg-unlinked-pending', 'unknown@example.com', 'unmatched subject', 'unmatched body',
                 ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', NULL),
                ('sender', 1003, 'msg-linked-processed', 'linked@example.com', 'processed subject', 'processed body',
                 ?, 'PROCESSED', 'MANUAL_BOUND', 1)
            """.trimIndent(),
            receivedAt, receivedAt.minusHours(1), receivedAt.minusHours(2)
        )
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 subject, body, send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-out',
                 'outbound subject', 'outbound body', 'SENT', ?, ?)
            """.trimIndent(),
            receivedAt.minusHours(3), receivedAt.minusHours(3)
        )

        assertEquals(1L, mailRecordRepository.countPendingExperts(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = null,
            recipientEmail = null
        ))
    }

    @Test
    fun `pending expert pagination orders by latest received and keeps expert on one page`() {
        seedBaseContact()
        seedSecondExpertContact()
        val base = LocalDate.now(shanghaiZone).atStartOfDay().plusHours(8)

        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, body,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES
                ('sender', 2001, 'a1', 'one@example.com', 'a1', 'a1', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 2002, 'a2', 'one@example.com', 'a2', 'a2', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 2003, 'a3', 'one@example.com', 'a3', 'a3', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 2004, 'b1', 'two@example.com', 'b1', 'b1', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 2)
            """.trimIndent(),
            base.minusHours(3), base.minusHours(2), base.minusHours(1), base
        )

        assertEquals(2L, mailRecordRepository.countPendingExperts(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = null,
            recipientEmail = null
        ))

        val page0 = mailRecordRepository.listPendingExpertSummaries(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            limit = 1,
            offset = 0L
        )
        val page1 = mailRecordRepository.listPendingExpertSummaries(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            limit = 1,
            offset = 1L
        )

        assertEquals(1, page0.size)
        assertEquals(1, page1.size)
        assertEquals(2L, page0[0].expertContactId)
        assertEquals(1L, page1[0].expertContactId)
        assertEquals(3L, page1[0].pendingCount)

        val mails = mailRecordRepository.listPendingMailsByExpertContactIds(
            expertContactIds = listOf(page1[0].expertContactId),
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = null,
            recipientEmail = null
        )
        assertEquals(3, mails.size)
        assertTrue(mails.all { it.expertContactId == 1L })
    }

    @Test
    fun `pending expert filters stay consistent across count summary and mails`() {
        seedBaseContact()
        seedSecondExpertContact()
        val base = LocalDate.now(shanghaiZone).atStartOfDay().plusHours(8)

        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, body,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES
                ('sender', 3001, 'mat-a1', 'one@example.com', '材料 A1', 'body', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 3002, 'mat-a2', 'one@example.com', '材料 A2', 'body', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 1),
                ('sender', 3003, 'meet-b1', 'two@example.com', '会议 B1', 'body', ?, 'MANUAL_REVIEW', 'UNMATCHED_CONTACT', 2)
            """.trimIndent(),
            base, base.minusHours(1), base.minusHours(2)
        )

        val keyword = "材料"
        assertEquals(1L, mailRecordRepository.countPendingExperts(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = keyword,
            recipientEmail = null
        ))

        val summaries = mailRecordRepository.listPendingExpertSummaries(
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = keyword,
            recipientEmail = null,
            limit = 10,
            offset = 0L
        )
        assertEquals(1, summaries.size)
        assertEquals(1L, summaries[0].expertContactId)
        assertEquals(2L, summaries[0].pendingCount)

        val mails = mailRecordRepository.listPendingMailsByExpertContactIds(
            expertContactIds = listOf(1L),
            accountCodes = listOf("sender"),
            accountCode = null,
            keyword = keyword,
            recipientEmail = null
        )
        assertEquals(2, mails.size)
        assertTrue(mails.all { it.subject?.contains("材料") == true })
    }

    private fun seedSecondExpertContact() {
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact
                (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES (2, 1, '0000-0002', 'two@example.com', 'Expert Two', 'NEW')
            """
        )
    }

    private fun seedBaseContact() {
        jdbcTemplate.execute("DELETE FROM expert_contact")
        jdbcTemplate.execute("DELETE FROM campaign")
        jdbcTemplate.execute("DELETE FROM mail_sender_account")
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (id, account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES
                (1, 'sender', 'sender@example.com', 'Sender', 'smtp.example.com', 465,
                 'sender@example.com', 'pwd', 'imap.example.com', 993, 'sender@example.com', 'pwd')
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id)
            VALUES (1, 'MANUAL_OUTREACH', 'Manual Outreach', 1)
            """
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact
                (id, campaign_id, orcid_id, expert_email, current_status)
            VALUES (1, 1, '0000-0001', 'one@example.com', 'NEW')
            """
        )
    }
}
