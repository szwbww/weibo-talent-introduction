package com.weibo.talentintroduction.mail.repository

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.MySQLContainer
import java.util.function.Supplier
import javax.sql.DataSource

@TestConfiguration
@EnableJdbcRepositories(basePackageClasses = [MailRecordRepository::class])
internal class MailboxProjectionJdbcConfig : AbstractJdbcConfiguration() {
    @Bean fun namedParameterJdbcOperations(source: DataSource): NamedParameterJdbcOperations = NamedParameterJdbcTemplate(source)
    @Bean fun transactionManager(source: DataSource) = DataSourceTransactionManager(source)
}

/** Runs the actual Spring Data JDBC proxy/@Query methods, never application.yml's datasource.
 * The existing processing branch matches record-owned attachments by Message-ID; this fixture
 * deliberately tests that visible set, not a new processing-owner/bridge coverage contract.
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
class MailboxSignatureProjectionIT {
    private class Mysql(image: String) : MySQLContainer<Mysql>(image)

    @TestFactory
    fun `production mailbox projections on MySQL 5_7_44 and 8_0_36`() =
        listOf("5.7.44", "8.0.36").map { version ->
            DynamicTest.dynamicTest("MySQL $version production repository proxy") {
                val mysql = Mysql("mysql:$version").withDatabaseName("mailbox_projection")
                    .withUsername("fixture").withPassword("fixture")
                    .withCreateContainerCmdModifier { it.withPlatform("linux/amd64") }
                mysql.start()
                try {
                    val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                    val jdbc = JdbcTemplate(source)
                    assertEquals(version, jdbc.queryForObject("SELECT VERSION()", String::class.java))
                    println("MAILBOX_SQL_SERVER=$version")
                    createSchema(jdbc)
                    seed(jdbc)
                    AnnotationConfigApplicationContext().use { context ->
                        context.registerBean("dataSource", DataSource::class.java, Supplier { source })
                        context.register(MailboxProjectionJdbcConfig::class.java)
                        context.refresh()
                        exercise(context.getBean(MailRecordRepository::class.java), jdbc, version)
                    }
                } finally { mysql.stop() }
            }
        }

    private fun exercise(repository: MailRecordRepository, jdbc: JdbcTemplate, version: String) {
        val before = snapshot(jdbc)
        fun list(accounts: List<String> = listOf("acc-a"), account: String? = null,
                 direction: String? = null, pending: Int = 0, limit: Int = 100, offset: Long = 0) =
            repository.listMailbox(accounts, direction, account, null, null, null, null, pending, limit, offset)
        fun byExpert(ids: List<Long> = listOf(1), accounts: List<String> = listOf("acc-a"),
                     account: String? = null, direction: String? = null, pending: Int = 0, tag: String? = null) =
            repository.listMailboxByExpertContactIds(ids, accounts, direction, account, null, null, null, null, pending, tag)
        val expected = listOf(5L to 0L, 4L to 1L, 3L to 0L, 2L to 1L, 1L to 0L)
        val rows = list()
        assertEquals(10, rows.size)
        assertEquals(expected.map { "INBOUND_PROCESSING" to it.first } + expected.map { "MAIL_RECORD" to it.first },
            rows.map { it.source to it.id })
        for (source in listOf("MAIL_RECORD", "INBOUND_PROCESSING")) {
            assertEquals(expected, rows.filter { it.source == source }.map { it.id to it.hasAttachment })
            assertEquals(expected, byExpert().filter { it.source == source }.map { it.id to it.hasAttachment })
        }
        assertEquals(rows, byExpert(), "same truth values, ordering, and projection on both queries for contact A")
        assertEquals(10L, repository.countMailbox(listOf("acc-a"), null, null, null, null, null, null, 0))
        assertEquals(rows.drop(3).take(4), list(limit = 4, offset = 3))
        assertEquals(5, list(direction = "OUTBOUND").size)
        assertEquals(5, byExpert(direction = "INBOUND", tag = "收件").size)
        assertEquals(5, list(pending = 1).size)
        assertEquals(5, byExpert(pending = 1, tag = "待处理").size)
        assertTrue(list(account = "acc-b").isEmpty())
        assertTrue(byExpert(account = "acc-b").isEmpty())
        assertTrue(byExpert(ids = listOf(2)).isEmpty())
        val both = list(accounts = listOf("acc-a", "acc-b"))
        assertEquals(12, both.size)
        // Global mailbox order is timestamp-first, not contact-first: all inbound precede outbound.
        assertEquals((6L downTo 1L).map { "INBOUND_PROCESSING" to it } +
            (6L downTo 1L).map { "MAIL_RECORD" to it }, both.map { it.source to it.id })
        val isolated = byExpert(ids = listOf(2), accounts = listOf("acc-a", "acc-b"))
        assertEquals(listOf("INBOUND_PROCESSING", "MAIL_RECORD"), isolated.map { it.source })
        assertTrue(isolated.all { it.expertContactId == 2L && it.senderAccountCode == "acc-b" && it.hasAttachment == 1L })
        assertEquals(12L, repository.countMailbox(listOf("acc-a", "acc-b"), null, null, null, null, null, null, 0))
        assertEquals(before, snapshot(jdbc), "projection reads leave historical attachments and states intact")
        println("MAILBOX_PROJECTION_CONTRACT $version: proxy=true branches=4 signature=false pdf=true image=true empty=false account/contact isolation=true pagination=4 total=10/12 readOnly=true")
    }

    private fun snapshot(jdbc: JdbcTemplate) = listOf("mail_attachment", "mail_record", "inbound_mail_processing", "expert_contact")
        .associateWith { jdbc.queryForList("SELECT * FROM $it ORDER BY id") }

    private fun seed(jdbc: JdbcTemplate) {
        jdbc.update("INSERT INTO expert_contact VALUES(1,'a@example.invalid','Expert A'),(2,'b@example.invalid','Expert B')")
        val cases = listOf(
            " SMIME.P7S " to null,
            "cv.pdf" to "application/pdf",
            "opaque" to " APPLICATION/X-PKCS7-SIGNATURE ; name=x",
            "image001.jpg" to "image/jpeg",
            null to null,
            "private.pdf" to "application/pdf"
        )
        cases.forEachIndexed { index, (name, mime) ->
            val id = index + 1
            val contact = if (id == 6) 2 else 1
            val account = if (id == 6) "acc-b" else "acc-a"
            // Distinct timestamps make unchanged ordering deterministic without adding a tie-breaker.
            jdbc.update("INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,message_id,subject,body,send_status,sent_at,created_at) VALUES(?,?,'OUTBOUND','INTRODUCTION',?,?,?,'body','SENT',?,?)",
                id, contact, account, "out-$id", "out-$id", "2026-11-01 10:00:0$id", "2026-11-01 10:00:0$id")
            jdbc.update("INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,message_id,subject,body,created_at) VALUES(?,?,'INBOUND','REPLY',?,?,?,'body',?)",
                100 + id, contact, account, "in-$id", "in-$id", "2026-11-01 11:00:0$id")
            jdbc.update("INSERT INTO inbound_mail_processing(id,expert_contact_id,sender_account_code,message_id,from_email,subject,body,received_at,process_status) VALUES(?,?,?,?,?,?,'body',?,'MANUAL_REVIEW')",
                id, contact, account, "in-$id", if (contact == 1) "a@example.invalid" else "b@example.invalid", "in-$id", "2026-11-01 11:00:0$id")
            if (name != null) {
                listOf(id, 100 + id).forEach { recordId ->
                    jdbc.update("INSERT INTO mail_attachment(mail_record_id,file_name,content_type,file_size) VALUES(?,?,?,0)", recordId, name, mime)
                    if (id == 2) jdbc.update("INSERT INTO mail_attachment(mail_record_id,file_name,content_type,file_size) VALUES(?,'smime.p7s',NULL,0)", recordId)
                }
            }
        }
    }

    private fun createSchema(jdbc: JdbcTemplate) {
        // Only columns used by these unmodified projection scopes, with production names/types.
        listOf(
            "CREATE TABLE expert_contact(id BIGINT PRIMARY KEY,expert_email VARCHAR(320),expert_name VARCHAR(255))",
            "CREATE TABLE mail_record(id BIGINT PRIMARY KEY,expert_contact_id BIGINT NOT NULL,direction VARCHAR(32),mail_type VARCHAR(64),sender_account_code VARCHAR(64),triggered_by VARCHAR(32),matched_qa_rule_id BIGINT,send_status VARCHAR(32),message_id VARCHAR(255),subject VARCHAR(255),body TEXT,cleaned_body TEXT,sent_at DATETIME,created_at DATETIME,FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id))",
            "CREATE TABLE inbound_mail_processing(id BIGINT PRIMARY KEY,expert_contact_id BIGINT,sender_account_code VARCHAR(64),message_id VARCHAR(255),from_email VARCHAR(320),subject VARCHAR(255),body TEXT,cleaned_body TEXT,received_at DATETIME,process_status VARCHAR(32),reason_type VARCHAR(64),FOREIGN KEY(expert_contact_id) REFERENCES expert_contact(id))",
            "CREATE TABLE mail_attachment(id BIGINT PRIMARY KEY AUTO_INCREMENT,mail_record_id BIGINT,file_name VARCHAR(255) NOT NULL,content_type VARCHAR(255),file_size BIGINT,FOREIGN KEY(mail_record_id) REFERENCES mail_record(id))"
        ).forEach { jdbc.execute(it + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci") }
    }
}
