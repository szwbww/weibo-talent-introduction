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
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mail_open_tracking WHERE id=?", Int::class.java, id))
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
        mail(9206,"OUTBOUND","SENT",at.minusNanos(1000),null,"Previous day")
        mail(9207,"OUTBOUND","SENT",at.plusDays(1),null,"Next day")
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
}
