package com.weibo.talentintroduction.monitoring.repository

import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
