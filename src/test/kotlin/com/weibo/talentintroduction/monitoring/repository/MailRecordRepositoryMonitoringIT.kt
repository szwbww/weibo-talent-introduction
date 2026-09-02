package com.weibo.talentintroduction.monitoring.repository

import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.monitoring.service.MailMonitoringService
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
    fun `expert aggregation combines all mail and pending remains an independent scope`() {
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

        assertEquals(1L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 0,
            tag = null
        ))

        val allSummary = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            limit = 10, offset = 0L
        ).single()
        assertEquals(3L, allSummary.mailCount)
        assertEquals(1L, allSummary.pendingCount)

        val pendingMails = mailRecordRepository.listMailboxByExpertContactIds(
            expertContactIds = listOf(1L), accountCodes = listOf("sender"), direction = null,
            accountCode = null, keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 1, tag = null
        )
        assertEquals(1, pendingMails.size)
        assertEquals("INBOUND_PROCESSING", pendingMails.single().source)
        assertEquals("MANUAL_REVIEW", pendingMails.single().processStatus)
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

        assertEquals(2L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null
        ))

        val page0 = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null,
            limit = 1,
            offset = 0L
        )
        val page1 = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null,
            limit = 1,
            offset = 1L
        )

        assertEquals(1, page0.size)
        assertEquals(1, page1.size)
        assertEquals(2L, page0[0].expertContactId)
        assertEquals(1L, page1[0].expertContactId)
        assertEquals(3L, page1[0].pendingCount)

        val mails = mailRecordRepository.listMailboxByExpertContactIds(
            expertContactIds = listOf(page1[0].expertContactId),
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = null,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null
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
        assertEquals(1L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = keyword,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null
        ))

        val summaries = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = keyword,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null,
            limit = 10,
            offset = 0L
        )
        assertEquals(1, summaries.size)
        assertEquals(1L, summaries[0].expertContactId)
        assertEquals(2L, summaries[0].pendingCount)

        val mails = mailRecordRepository.listMailboxByExpertContactIds(
            expertContactIds = listOf(1L),
            accountCodes = listOf("sender"),
            direction = null,
            accountCode = null,
            keyword = keyword,
            recipientEmail = null,
            startTime = null,
            endTime = null,
            onlyPending = 1,
            tag = null
        )
        assertEquals(2, mails.size)
        assertTrue(mails.all { it.subject?.contains("材料") == true })
    }
    @Test
    fun `expert filter counts only authoritative sources and keeps other expert out`() {
        seedBaseContact()
        seedSecondExpertContact()
        val base = LocalDate.now(shanghaiZone).atStartOfDay().plusHours(8)

        jdbcTemplate.update(
            """
            INSERT INTO inbound_mail_processing
                (sender_account_code, imap_uid, message_id, from_email, subject, body,
                 received_at, process_status, process_reason, expert_contact_id)
            VALUES
                ('sender', 4001, 'in-a1', 'one@example.com', 'inbound one', 'body', ?, 'PROCESSED', 'MANUAL_BOUND', 1),
                ('sender', 4002, 'in-a2', 'one@example.com', 'inbound two', 'body', ?, 'PROCESSED', 'MANUAL_BOUND', 1)
            """.trimIndent(),
            base.minusHours(6), base.minusHours(5)
        )
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'MANUAL', 'msg-s1', 'SENT', ?, ?),
                (1, 'OUTBOUND', 'QA_REPLY', 'sender', 'SYSTEM', 'msg-s2', 'SENT', ?, ?),
                (1, 'OUTBOUND', 'QA_REPLY', 'sender', 'SYSTEM', 'msg-f1', 'FAILED', ?, ?),
                (1, 'INBOUND', 'REPLY', 'sender', NULL, 'msg-in-copy', NULL, NULL, ?),
                (2, 'OUTBOUND', 'INTRODUCTION', 'sender', 'MANUAL', 'msg-b1', 'SENT', ?, ?)
            """.trimIndent(),
            base.minusHours(4), base.minusHours(4),
            base.minusHours(3), base.minusHours(3),
            base.minusHours(2), base.minusHours(2),
            base.minusHours(1),
            base, base
        )

        assertEquals(1L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            expertContactId = 1L
        ))

        val expertSummary = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            limit = 10, offset = 0L, expertContactId = 1L
        ).single()
        assertEquals(1L, expertSummary.expertContactId)
        assertEquals(5L, expertSummary.mailCount)
        assertEquals(0L, expertSummary.pendingCount)
        assertEquals(2L, expertSummary.receivedCount)
        assertEquals(2L, expertSummary.sentCount)
        assertEquals(1L, expertSummary.failedCount)

        val mails = mailRecordRepository.listMailboxByExpertContactIds(
            expertContactIds = listOf(1L), accountCodes = listOf("sender"), direction = null,
            accountCode = null, keyword = null, recipientEmail = null, startTime = null, endTime = null,
            onlyPending = 0, tag = null
        )
        assertEquals(5, mails.size)
        assertTrue(mails.all { it.expertContactId == 1L })
        assertEquals(2, mails.count { it.source == "INBOUND_PROCESSING" })
        assertEquals(2, mails.count { it.direction == "OUTBOUND" && it.sendStatus == "SENT" })
        assertEquals(1, mails.count { it.direction == "OUTBOUND" && it.sendStatus == "FAILED" })

        assertEquals(1L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            expertContactId = 2L
        ))
        val otherSummary = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            limit = 10, offset = 0L, expertContactId = 2L
        ).single()
        assertEquals(2L, otherSummary.expertContactId)
        assertEquals(1L, otherSummary.mailCount)
        assertEquals(0L, otherSummary.receivedCount)
        assertEquals(1L, otherSummary.sentCount)
        assertEquals(0L, otherSummary.failedCount)

        assertEquals(2L, mailRecordRepository.countMailboxExperts(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null
        ))
        val allSummaries = mailRecordRepository.listMailboxExpertSummaries(
            accountCodes = listOf("sender"), direction = null, accountCode = null, keyword = null,
            recipientEmail = null, startTime = null, endTime = null, onlyPending = 0, tag = null,
            limit = 10, offset = 0L
        )
        assertEquals(2, allSummaries.size)
        val contactOne = allSummaries.first { it.expertContactId == 1L }
        assertEquals(5L, contactOne.mailCount)
        assertEquals(2L, contactOne.receivedCount)
        assertEquals(2L, contactOne.sentCount)
        assertEquals(1L, contactOne.failedCount)
    }

    @Test
    fun `intro cohort dedupes repeated introductions per expert`() {
        seedBaseContact()
        seedSecondExpertContact()
        jdbcTemplate.update("UPDATE expert_contact SET country = 'Germany' WHERE id = 1")
        jdbcTemplate.update("UPDATE expert_contact SET country = 'US' WHERE id = 2")
        val from = LocalDateTime.now().minusDays(30)
        val to = LocalDateTime.now().plusDays(1)
        val sentAt = LocalDateTime.now().minusDays(10)

        // 专家 1 两条 INTRODUCTION（同 expert_contact_id）→ 队列人数去重为 1；专家 2 一条 → 1。
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-intro-1', 'SENT', ?, ?),
                (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-intro-1b', 'SENT', ?, ?),
                (2, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-intro-2', 'SENT', ?, ?)
            """.trimIndent(),
            sentAt, sentAt,
            sentAt.plusMinutes(5), sentAt.plusMinutes(5),
            sentAt.plusHours(1), sentAt.plusHours(1)
        )

        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
            from, to, LocalDateTime.now().minusDays(MailMonitoringService.MATURITY_DAYS)
        ).associateBy { it.country }

        assertEquals(2, rows.size)
        assertEquals(1L, rows.getValue("Germany").cohortCount)
        assertEquals(1L, rows.getValue("US").cohortCount)
    }

    @Test
    fun `intro cohort counts reply only when inbound is after the introduction`() {
        seedBaseContact()
        seedSecondExpertContact()
        jdbcTemplate.update("UPDATE expert_contact SET country = 'Germany' WHERE id = 1")
        jdbcTemplate.update("UPDATE expert_contact SET country = 'US' WHERE id = 2")
        val from = LocalDateTime.now().minusDays(30)
        val to = LocalDateTime.now().plusDays(1)
        val introAt = LocalDateTime.now().minusDays(10)

        // A（专家 1 / Germany）：首发后 1 天有 INBOUND，first_reply_at 保持 NULL → 计入回复。
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-a-intro', 'SENT', ?, ?)
            """.trimIndent(),
            introAt, introAt
        )
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 received_at, created_at)
            VALUES (1, 'INBOUND', 'REPLY', 'sender', 'msg-a-reply', ?, ?)
            """.trimIndent(),
            introAt.plusDays(1), introAt.plusDays(1)
        )
        // B（专家 2 / US）：无任何 INBOUND，但晋级路径把 expert_contact.first_reply_at 写成了非空 → 不得计入。
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES (2, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-b-intro', 'SENT', ?, ?)
            """.trimIndent(),
            introAt, introAt
        )
        jdbcTemplate.update("UPDATE expert_contact SET first_reply_at = ? WHERE id = 2", introAt.plusDays(2))

        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
            from, to, LocalDateTime.now().minusDays(MailMonitoringService.MATURITY_DAYS)
        ).associateBy { it.country }

        assertEquals(1L, rows.getValue("Germany").repliedCount)
        assertEquals(0L, rows.getValue("US").repliedCount)
    }

    @Test
    fun `mature cohort excludes introductions newer than seven days`() {
        seedBaseContact()
        seedSecondExpertContact()
        jdbcTemplate.update("UPDATE expert_contact SET country = 'Germany' WHERE id = 1")
        jdbcTemplate.update("UPDATE expert_contact SET country = 'US' WHERE id = 2")
        val from = LocalDateTime.now().minusDays(30)
        val to = LocalDateTime.now().plusDays(1)
        val matureBefore = LocalDateTime.now().minusDays(MailMonitoringService.MATURITY_DAYS)

        // 专家 1：首发仅 2 天前 → 未满 7 天，不进成熟子集。
        val freshSentAt = LocalDateTime.now().minusDays(2)
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-fresh-intro', 'SENT', ?, ?)
            """.trimIndent(),
            freshSentAt, freshSentAt
        )
        // 专家 2：首发 10 天前、3 天后首回（7 日窗口内）→ 成熟队列 1 人、成熟回复 1 人。
        val oldSentAt = LocalDateTime.now().minusDays(10)
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES (2, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-old-intro', 'SENT', ?, ?)
            """.trimIndent(),
            oldSentAt, oldSentAt
        )
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 received_at, created_at)
            VALUES (2, 'INBOUND', 'REPLY', 'sender', 'msg-old-reply', ?, ?)
            """.trimIndent(),
            oldSentAt.plusDays(3), oldSentAt.plusDays(3)
        )

        val rows = mailRecordRepository.aggregateIntroCohortByCountry(
            from, to, matureBefore
        ).associateBy { it.country }

        assertEquals(1L, rows.getValue("Germany").cohortCount)
        assertEquals(0L, rows.getValue("Germany").matureCohortCount)
        assertEquals(0L, rows.getValue("Germany").matureRepliedCount)
        assertEquals(1L, rows.getValue("US").cohortCount)
        assertEquals(1L, rows.getValue("US").matureCohortCount)
        assertEquals(1L, rows.getValue("US").matureRepliedCount)
    }

    @Test
    fun `intro cohort counts first inbound at or after introduction even when an earlier inbound exists`() {
        seedBaseContact()
        jdbcTemplate.update("UPDATE expert_contact SET country = 'Germany' WHERE id = 1")
        val from = LocalDateTime.now().minusDays(30)
        val to = LocalDateTime.now().plusDays(1)
        val matureBefore = LocalDateTime.now().minusDays(MailMonitoringService.MATURITY_DAYS)
        val introAt = LocalDateTime.now().minusDays(10)

        // 首发 10 天前；窗口内先有一条早于首发的 INBOUND（now-12d），再有一条晚于首发的 INBOUND（now-9d）。
        // 修复前：MIN(received_at) = 早于首发的那条 → join 谓词 first_reply_at >= first_sent_at 失败 → 该专家被漏计。
        // 修复后：只取 received_at >= 首发 的 INBOUND 求 MIN → 计为已回复，且成熟口径用这条合格首回。
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 send_status, sent_at, created_at)
            VALUES (1, 'OUTBOUND', 'INTRODUCTION', 'sender', 'msg-pre-intro', 'SENT', ?, ?)
            """.trimIndent(),
            introAt, introAt
        )
        jdbcTemplate.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, message_id,
                 received_at, created_at)
            VALUES
                (1, 'INBOUND', 'REPLY', 'sender', 'msg-early-reply', ?, ?),
                (1, 'INBOUND', 'REPLY', 'sender', 'msg-qualifying-reply', ?, ?)
            """.trimIndent(),
            introAt.minusDays(2), introAt.minusDays(2),
            introAt.plusDays(1), introAt.plusDays(1)
        )

        val byCountry = mailRecordRepository.aggregateIntroCohortByCountry(from, to, matureBefore)
            .associateBy { it.country }
        val byDomain = mailRecordRepository.aggregateIntroCohortByDomain(from, to, matureBefore)
            .associateBy { it.domain }

        assertEquals(1L, byCountry.getValue("Germany").cohortCount)
        assertEquals(1L, byCountry.getValue("Germany").repliedCount)
        assertEquals(1L, byCountry.getValue("Germany").matureCohortCount)
        assertEquals(1L, byCountry.getValue("Germany").matureRepliedCount)
        assertEquals(1L, byDomain.getValue("example.com").repliedCount)
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
