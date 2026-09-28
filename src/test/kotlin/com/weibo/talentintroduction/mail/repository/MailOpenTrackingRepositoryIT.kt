package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.service.MailOpenTrackingService
import com.weibo.talentintroduction.monitoring.service.MonitoringDateRangeResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false", "talent-introduction.mail-open-tracking.base-url=https://example.test/talent"])
@Import(MailOpenTrackingRepository::class, MailOpenTrackingService::class, MonitoringDateRangeResolver::class)
class MailOpenTrackingRepositoryIT {
    companion object {
        private class Mysql(image: String) : MySQLContainer<Mysql>(image)
        private val mysql = Mysql("mysql:8.0.36").withDatabaseName("talent_introduction")
            .withUsername("test").withPassword("test")

        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for tracking MySQL IT" }
            mysql.start()
        }

        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var repository: MailOpenTrackingRepository
    @Autowired lateinit var service: MailOpenTrackingService
    @Autowired lateinit var transactions: PlatformTransactionManager

    @BeforeEach fun setup() {
        jdbc.update("DELETE FROM mail_record")
        jdbc.update("DELETE FROM mail_open_tracking")
        jdbc.update("DELETE FROM batch_send_setting WHERE setting_key='mailOpenTracking.enabled'")
        jdbc.update("DELETE FROM expert_contact")
        jdbc.update("DELETE FROM campaign")
        jdbc.update("DELETE FROM mail_sender_account")
        jdbc.update("INSERT INTO mail_sender_account (id, account_code, sender_email, sender_name, smtp_host, smtp_port, smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password) VALUES (9101,'tracking-test','sender@example.test','Sender','smtp.test',465,'sender@example.test','pw','imap.test',993,'sender@example.test','pw')")
        jdbc.update("INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) VALUES (9101,'TRACKING_TEST','Tracking',9101)")
        jdbc.update("INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name) VALUES (9101,9101,'tracking-1','now@example.test','Jane'),(9102,9101,'tracking-2','other@example.test','John')")
    }

    @Test fun `setting upsert is idempotent and reservation commits independently of outer rollback`() {
        assertFalse(repository.isEnabled())
        jdbc.update("INSERT INTO batch_send_setting (setting_key, setting_value) VALUES ('mailOpenTracking.enabled','invalid')")
        assertFalse(repository.isEnabled())
        service.setEnabled(true)
        service.setEnabled(true)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM batch_send_setting WHERE setting_key='mailOpenTracking.enabled'", Int::class.java))
        var id = 0L
        TransactionTemplate(transactions).execute { outer ->
            jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email) VALUES (9103,9101,'uncommitted','uncommitted@example.test')")
            id = service.reserve("valid@example.test")!!.id
            dataSource.connection.use { connection ->
                connection.prepareStatement("SELECT COUNT(*) FROM mail_open_tracking WHERE id=?").use { statement ->
                    statement.setLong(1, id)
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(1, rows.getInt(1))
                    }
                }
                connection.prepareStatement("SELECT COUNT(*) FROM expert_contact WHERE id=9103").use { statement ->
                    statement.executeQuery().use { rows ->
                        assertTrue(rows.next())
                        assertEquals(0, rows.getInt(1))
                    }
                }
            }
            outer.setRollbackOnly()
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_open_tracking WHERE id=?", Int::class.java, id))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM expert_contact WHERE id=9103", Int::class.java))
    }

    @Test fun `concurrent first saves create one key without changing old settings`() {
        val before = jdbc.queryForList("SELECT setting_key, setting_value FROM batch_send_setting ORDER BY setting_key")
        val executor = Executors.newFixedThreadPool(8)
        try {
            (0 until 16).map { executor.submit { service.setEnabled(true) } }
                .forEach { it.get(30, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM batch_send_setting WHERE setting_key='mailOpenTracking.enabled'", Int::class.java))
        assertEquals(before, jdbc.queryForList("SELECT setting_key, setting_value FROM batch_send_setting WHERE setting_key<>'mailOpenTracking.enabled' ORDER BY setting_key"))
    }

    @Test fun `failed independent reservation does not mark caller transaction rollback only`() {
        service.setEnabled(true)
        TransactionTemplate(transactions).execute {
            assertThrows(Exception::class.java) {
                repository.reserve("A".repeat(43), "x".repeat(256), LocalDateTime.of(2026,9,25,10,0))
            }
            jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email) VALUES (9103,9101,'committed','committed@example.test')")
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM expert_contact WHERE id=9103", Int::class.java))
    }

    @Test fun `signals atomically preserve minimum and maximum with concurrent requests and stop when disabled`() {
        service.setEnabled(true)
        val reservation = service.reserve("one@example.test")!!
        val early = LocalDateTime.of(2026, 9, 25, 0, 0)
        val late = early.plusHours(8)
        jdbc.update("UPDATE batch_send_setting SET setting_value='TRUE' WHERE setting_key='mailOpenTracking.enabled'")
        assertFalse(repository.isEnabled())
        assertEquals(0, repository.recordSignal(reservation.token, late))
        assertNull(jdbc.queryForObject("SELECT first_open_at FROM mail_open_tracking WHERE id=?", LocalDateTime::class.java, reservation.id))
        service.setEnabled(true)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val work = (0 until 100).map { index -> executor.submit<Int> {
                repository.recordSignal(reservation.token, if (index % 2 == 0) late else early)
            } }
            work.forEach { assertEquals(1, it.get(30, TimeUnit.SECONDS)) }
        } finally { executor.shutdownNow() }
        assertEquals(early, jdbc.queryForObject("SELECT first_open_at FROM mail_open_tracking WHERE id=?", LocalDateTime::class.java, reservation.id))
        assertEquals(late, jdbc.queryForObject("SELECT last_open_at FROM mail_open_tracking WHERE id=?", LocalDateTime::class.java, reservation.id))
        service.setEnabled(false)
        assertEquals(0, repository.recordSignal(reservation.token, late.plusDays(1)))
        assertEquals(0, repository.recordSignal("Z".repeat(43), late.plusDays(1)))
        assertEquals(late, jdbc.queryForObject("SELECT last_open_at FROM mail_open_tracking WHERE id=?", LocalDateTime::class.java, reservation.id))
    }

    @Test fun `only successful associated outbound mail counts with stable filtering and Shanghai date boundaries`() {
        service.setEnabled(true)
        val first = service.reserve("first@example.test")!!
        val second = service.reserve("second@example.test")!!
        val orphan = service.reserve("orphan@example.test")!!
        val failed = service.reserve("failed@example.test")!!
        val inbound = service.reserve("inbound@example.test")!!
        repository.recordSignal(first.token, LocalDateTime.of(2026,9,25,9,0))
        val at = LocalDateTime.of(2026,9,25,0,0)
        fun mail(id: Long, direction: String, sendStatus: String, sentAt: LocalDateTime?, tracking: Long?, subject: String) {
            jdbc.update("INSERT INTO mail_record (id,expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at,open_tracking_id) VALUES (?,9101,?,'INTRODUCTION','tracking-test',?,?,?,?)",
                id, direction, subject, sendStatus, sentAt, tracking)
        }
        mail(9201,"OUTBOUND","SENT",at,first.id,"100% welcome")
        mail(9202,"OUTBOUND","SENT",at,second.id,"Other")
        mail(9203,"OUTBOUND","SENT",at,null,"Untracked")
        mail(9204,"OUTBOUND","FAILED",null,failed.id,"Failed")
        mail(9205,"INBOUND","SENT",at,inbound.id,"Incoming")
        mail(9206,"OUTBOUND","SENT",at.minusSeconds(1),null,"Previous day")
        mail(9207,"OUTBOUND","SENT",at.plusDays(1),null,"Next day")
        val persistedPreviousDay = jdbc.queryForObject(
            "SELECT sent_at FROM mail_record WHERE id=?", LocalDateTime::class.java, 9206L
        )
        assertEquals(at.toLocalDate().minusDays(1), persistedPreviousDay?.toLocalDate())
        val all = service.readPage(LocalDate.of(2026,9,25), LocalDate.of(2026,9,25),null,"ALL",null,20,0)
        assertEquals(listOf(9203L,9202L,9201L), all.records.map { it.mailRecordId })
        assertEquals(3L, all.totalCount)
        assertEquals(OpenTrackingSummary(2,1,0.5), all.summary)
        assertEquals(null, repository.detail(9204))
        assertEquals(null, repository.detail(9205))
        assertEquals("first@example.test", service.detail(9201).recipient)
        val opened = service.readPage(LocalDate.of(2026,9,25),LocalDate.of(2026,9,25),null,"OPENED",null,1,0)
        assertEquals(listOf(9201L), opened.records.map { it.mailRecordId })
        assertEquals(1L, opened.totalCount)
        assertEquals(all.summary, opened.summary)
        assertEquals(1L, service.readPage(LocalDate.of(2026,9,25),LocalDate.of(2026,9,25),null,"ALL","100%",20,0).totalCount)
        assertEquals(OpenTrackingSummary(0,0,null), service.readPage(LocalDate.of(2026,9,26),LocalDate.of(2026,9,26),"other-account","ALL",null,20,0).summary)
        val plan = jdbc.queryForList("EXPLAIN SELECT m.id FROM mail_record m LEFT JOIN mail_open_tracking t ON t.id=m.open_tracking_id LEFT JOIN expert_contact ec ON ec.id=m.expert_contact_id WHERE m.direction='OUTBOUND' AND m.send_status='SENT' AND m.sent_at >= ? AND m.sent_at < ? ORDER BY m.sent_at DESC,m.id DESC LIMIT 20", at, at.plusDays(1))
        assertTrue(plan.isNotEmpty())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mail_record WHERE open_tracking_id=?", Int::class.java, orphan.id))
    }

    @Test fun `one hundred twenty second cutoff decides status list filter summary and detail identically`() {
        service.setEnabled(true)
        val at = LocalDateTime.of(2026, 9, 25, 0, 0)
        val under = at.plusNanos(119_999_999_000L)
        val exact = at.plusSeconds(120)
        val over = at.plusSeconds(120).plusNanos(1_000L)
        val underCutoff = service.reserve("under@example.test")!!
        val exactlyCutoff = service.reserve("exact@example.test")!!
        val overCutoff = service.reserve("over@example.test")!!
        val laterRequest = service.reserve("later@example.test")!!
        val noRequest = service.reserve("none@example.test")!!
        val beforeSent = service.reserve("before@example.test")!!
        val orphan = service.reserve("orphan@example.test")!!
        val failedMail = service.reserve("failed@example.test")!!
        val inboundMail = service.reserve("inbound@example.test")!!
        fun mail(id: Long, direction: String, sendStatus: String, sentAt: LocalDateTime?, tracking: Long?, subject: String) {
            jdbc.update("INSERT INTO mail_record (id,expert_contact_id,direction,mail_type,sender_account_code,subject,send_status,sent_at,open_tracking_id) VALUES (?,9101,?,'INTRODUCTION','tracking-test',?,?,?,?)",
                id, direction, subject, sendStatus, sentAt, tracking)
        }
        mail(9301, "OUTBOUND", "SENT", at, underCutoff.id, "TRACK120-UNDER")
        mail(9302, "OUTBOUND", "SENT", at, exactlyCutoff.id, "TRACK120-EXACT")
        mail(9303, "OUTBOUND", "SENT", at, overCutoff.id, "TRACK120-OVER")
        mail(9304, "OUTBOUND", "SENT", at, laterRequest.id, "TRACK120-LATER")
        mail(9305, "OUTBOUND", "SENT", at, noRequest.id, "TRACK120-NONE")
        mail(9306, "OUTBOUND", "SENT", at, null, "TRACK120-UNTRACKED")
        mail(9307, "OUTBOUND", "FAILED", null, failedMail.id, "TRACK120-FAILED")
        mail(9308, "INBOUND", "SENT", at, inboundMail.id, "TRACK120-INBOUND")
        mail(9309, "OUTBOUND", "SENT", at, beforeSent.id, "TRACK120-BEFORE-SENT")
        assertEquals(1, repository.recordSignal(underCutoff.token, under))
        assertEquals(1, repository.recordSignal(exactlyCutoff.token, exact))
        assertEquals(1, repository.recordSignal(overCutoff.token, over))
        assertEquals(1, repository.recordSignal(laterRequest.token, at.plusSeconds(10)))
        assertEquals(1, repository.recordSignal(beforeSent.token, at.minusHours(1)))
        assertEquals(1, repository.recordSignal(orphan.token, at.plusSeconds(121)))
        assertEquals(1, repository.recordSignal(failedMail.token, at.plusSeconds(121)))
        assertEquals(1, repository.recordSignal(inboundMail.token, at.plusSeconds(121)))
        val day = LocalDate.of(2026, 9, 25)

        // I-2: only the 10s request exists on 9304 while real wall-clock time is far past sent_at+120s.
        val earlyOnly = service.readPage(day, day, null, "ALL", "TRACK120-LATER", 20, 0)
        assertEquals(listOf(9304L), earlyOnly.records.map { it.mailRecordId })
        assertEquals("NO_SIGNAL", earlyOnly.records.single().trackingStatus)
        assertEquals(1L, earlyOnly.totalCount)
        assertEquals(OpenTrackingSummary(6, 1, 1.0 / 6.0), earlyOnly.summary)
        assertEquals("NO_SIGNAL", repository.detail(9304L)!!.trackingStatus)

        // I-2: a second, later pixel request is the only thing that flips the row to OPENED.
        assertEquals(1, repository.recordSignal(laterRequest.token, at.plusSeconds(121)))

        val all = service.readPage(day, day, null, "ALL", null, 20, 0)
        assertEquals(listOf(9309L, 9306L, 9305L, 9304L, 9303L, 9302L, 9301L), all.records.map { it.mailRecordId })
        assertEquals(7L, all.totalCount)
        assertEquals(
            listOf("NO_SIGNAL", "NOT_TRACKED", "NO_SIGNAL", "OPENED", "OPENED", "NO_SIGNAL", "NO_SIGNAL"),
            all.records.map { it.trackingStatus }
        )
        assertEquals(OpenTrackingSummary(6, 2, 2.0 / 6.0), all.summary)

        // I-4: status and keyword only move the list and totalCount, never the summary.
        val opened = service.readPage(day, day, null, "OPENED", null, 20, 0)
        assertEquals(listOf(9304L, 9303L), opened.records.map { it.mailRecordId })
        assertEquals(2L, opened.totalCount)
        assertEquals(all.summary, opened.summary)
        assertEquals(listOf("OPENED", "OPENED"), opened.records.map { it.trackingStatus })
        val noSignal = service.readPage(day, day, null, "NO_SIGNAL", null, 20, 0)
        assertEquals(listOf(9309L, 9305L, 9302L, 9301L), noSignal.records.map { it.mailRecordId })
        assertEquals(4L, noSignal.totalCount)
        assertEquals(all.summary, noSignal.summary)
        assertEquals(listOf("NO_SIGNAL", "NO_SIGNAL", "NO_SIGNAL", "NO_SIGNAL"), noSignal.records.map { it.trackingStatus })
        val notTracked = service.readPage(day, day, null, "NOT_TRACKED", null, 20, 0)
        assertEquals(listOf(9306L), notTracked.records.map { it.mailRecordId })
        assertEquals(1L, notTracked.totalCount)
        assertEquals(all.summary, notTracked.summary)
        val keyworded = service.readPage(day, day, null, "ALL", "TRACK120", 20, 0)
        assertEquals(7L, keyworded.totalCount)
        assertEquals(all.summary, keyworded.summary)

        // I-1/I-4: the filtered list, the list status, the detail row and the summary numerator agree exactly.
        val openedFromList = all.records.filter { it.trackingStatus == "OPENED" }.map { it.mailRecordId }
        assertEquals(openedFromList, opened.records.map { it.mailRecordId })
        assertEquals(all.summary.opened, openedFromList.size.toLong())
        assertEquals(all.summary.opened, openedFromList.count { repository.detail(it)!!.trackingStatus == "OPENED" }.toLong())

        // I-1: the exact 120s boundary and anything before it stay NO_SIGNAL; one microsecond past it is OPENED.
        val underRow = repository.detail(9301L)!!
        assertEquals("NO_SIGNAL", underRow.trackingStatus)
        assertEquals(under, underRow.firstOpenAt)
        assertEquals(under, underRow.lastOpenAt)
        val exactRow = repository.detail(9302L)!!
        assertEquals("NO_SIGNAL", exactRow.trackingStatus)
        assertEquals(exact, exactRow.firstOpenAt)
        assertEquals(exact, exactRow.lastOpenAt)
        val overRow = repository.detail(9303L)!!
        assertEquals("OPENED", overRow.trackingStatus)
        assertEquals(over, overRow.firstOpenAt)
        assertEquals(over, overRow.lastOpenAt)

        // I-3: a later request flips the status but keeps both raw request times untouched.
        val laterRow = repository.detail(9304L)!!
        assertEquals("OPENED", laterRow.trackingStatus)
        assertEquals(at.plusSeconds(10), laterRow.firstOpenAt)
        assertEquals(at.plusSeconds(121), laterRow.lastOpenAt)

        // I-1/I-3: no request, and a request predating sent_at, stay NO_SIGNAL and still show their raw times.
        val noneRow = repository.detail(9305L)!!
        assertEquals("NO_SIGNAL", noneRow.trackingStatus)
        assertNull(noneRow.firstOpenAt)
        assertNull(noneRow.lastOpenAt)
        val beforeRow = repository.detail(9309L)!!
        assertEquals("NO_SIGNAL", beforeRow.trackingStatus)
        assertEquals(at.minusHours(1), beforeRow.firstOpenAt)
        assertEquals(at.minusHours(1), beforeRow.lastOpenAt)
        assertEquals("NOT_TRACKED", repository.detail(9306L)!!.trackingStatus)
        assertNull(repository.detail(9307L))
        assertNull(repository.detail(9308L))

        // I-4: an orphan tracking row with a qualified signal never enters the records, the denominator or the numerator.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_open_tracking WHERE id=?", Int::class.java, orphan.id))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mail_record WHERE open_tracking_id=?", Int::class.java, orphan.id))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_open_tracking WHERE id=? AND last_open_at>?", Int::class.java, orphan.id, at.plusSeconds(120)))

        // I-4: account, date and pagination filters keep working on top of the new predicate.
        assertEquals(7L, service.readPage(day, day, "tracking-test", "ALL", null, 20, 0).totalCount)
        assertEquals(OpenTrackingSummary(0, 0, null), service.readPage(day, day, "other-account", "ALL", null, 20, 0).summary)
        assertEquals(listOf(9304L, 9303L, 9302L), service.readPage(day, day, null, "ALL", null, 3, 3).records.map { it.mailRecordId })
    }
}
