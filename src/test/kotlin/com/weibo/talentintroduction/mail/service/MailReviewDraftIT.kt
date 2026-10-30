package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.mail.repository.MailReviewDraftRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real MySQL transactions and production service/repositories; never a transactional test wrapper. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@SpringBootTest(classes = [MailReviewDraftIT.Config::class], properties = [
    "spring.flyway.placeholder-replacement=false", "talent-introduction.scheduling.enabled=false",
    "talent-introduction.mail-queue.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"
])
class MailReviewDraftIT {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration
    @EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.mail.repository"])
    @Import(MailReviewDraftService::class, MailReviewDraftRepository::class,
        MailboxConversationRepository::class, MailContentService::class, MailPlaceholderService::class)
    class Config

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("review_draft").withUsername("test").withPassword("test")
            .withCommand("mysqld", "--max-allowed-packet=16777216", "--log-bin-trust-function-creators=1")
        @JvmStatic @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
    }

    @Autowired private lateinit var service: MailReviewDraftService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @MockBean private lateinit var translation: MailTranslationService
    @MockBean private lateinit var delivery: MailDeliveryService
    private val mapper = ObjectMapper().findAndRegisterModules()
    private fun node(value: Any): JsonNode = mapper.valueToTree(value)
    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java)!!
    private fun row(id: Long) = jdbc.queryForMap("SELECT * FROM mailbox_review_draft WHERE id=?", id)
    private fun id(draft: JsonNode) = draft["id"].asLong()
    private fun version(draft: JsonNode) = draft["version"].asLong()
    private fun create(subject: String = "Subject", html: String = "<p>Body</p>", request: String = UUID.randomUUID().toString(), contact: Long = 1) =
        node(service.create("user1", contact, request, subject, html))
    private fun submitted(): JsonNode { val d = create(); return node(service.submit("user1", id(d), version(d))) }
    private fun token(d: JsonNode) = node(service.context("admin", id(d)))["currentToken"].asText()
    private fun failure(status: Int, code: String? = null, block: () -> Unit) {
        val ex = assertThrows(MailReviewDraftException::class.java, block)
        assertEquals(status, ex.status)
        if (code != null) assertEquals(code, ex.code)
    }

    @BeforeEach
    fun seed() {
        listOf("mailbox_review_draft_event", "mailbox_review_draft", "mailbox_reply_draft", "expert_mail_read_access",
            "expert_contact_note", "inbound_mail_tag", "inbound_mail_processing", "mail_record", "expert_contact", "campaign", "mail_sender_account")
            .forEach { jdbc.update("DELETE FROM $it") }
        account("real-a")
        account("real-b")
        account("SIMULATOR_NOOP")
        jdbc.update("UPDATE mail_sender_account SET enabled=false WHERE account_code='real-b'")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'REVIEW-IT','Review',(SELECT id FROM mail_sender_account WHERE account_code='real-a'))")
        (1L..3L).forEach {
            jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(?,1,?,?,?,'NEW')",
                it, "review-$it", "expert$it@example.test", "Expert $it")
        }
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at) VALUES('user1',1,'admin',NOW(3)),('user1',3,'admin',NOW(3))")
        outbound(1, "real-a", 10)
    }

    private fun account(code: String) {
        jdbc.update("""INSERT INTO mail_sender_account(account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
            VALUES(?,?,?,'smtp.fixture',465,?,'pw','imap.fixture',993,?,'pw')""", code, "$code@example.test", code, code, code)
    }
    private fun outbound(contact: Long = 1, account: String = "real-a", mailId: Long, status: String = "SENT") {
        jdbc.update("""INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,triggered_by,message_id,subject,body,send_status,sent_at,created_at)
            VALUES(?,?,'OUTBOUND','INTRODUCTION',?,'SYSTEM',?,'Original','Original body',?,?,'2026-09-01 09:00:00')""",
            mailId, contact, account, "out-$mailId", status, if (status == "SENT") "2026-09-01 09:00:00" else null)
    }
    private fun inbound(processingId: Long, account: String = "real-a") {
        jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id)
            VALUES(?,?,'INBOX',1,?,?, 'expert1@example.test','Inbound','Inbound body','Inbound clean','2026-09-01 09:00:00','MANUAL_REVIEW','Review fixture',1)""",
            processingId, account, processingId, "in-$processingId")
    }

    @Test
    fun `fresh migration and V168 upgrade preserve old rows and independent administrator draft`() {
        fun flyway(target: String? = null): Flyway {
            val config = Flyway.configure().dataSource(mysql.jdbcUrl, mysql.username, mysql.password)
                .locations("classpath:db/migration").placeholderReplacement(false).cleanDisabled(false)
            if (target != null) config.target(target)
            return config.load()
        }
        flyway().clean()
        flyway().migrate()
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('mailbox_review_draft','mailbox_review_draft_event')", Long::class.java))
        flyway().clean()
        flyway("168").migrate()
        account("old-account")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(71,'OLD','Old',(SELECT id FROM mail_sender_account WHERE account_code='old-account'))")
        jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(71,71,'old-orcid','old@example.test','Old Expert','NEW')")
        jdbc.update("""INSERT INTO mailbox_reply_draft(username,expert_contact_id,target_kind,inbound_processing_id,account_scope,version,state,subject,html_body,text_body,created_at,updated_at)
            VALUES('admin',71,'OUTBOUND',0,'old-account',9,'ACTIVE','Old subject','<p>Old body</p>','Old body',NOW(3),NOW(3))""")
        val before = jdbc.queryForMap("SELECT * FROM mailbox_reply_draft")
        val contacts = jdbc.queryForList("SELECT * FROM expert_contact")
        flyway().migrate()
        assertEquals(before, jdbc.queryForMap("SELECT * FROM mailbox_reply_draft"))
        assertEquals(contacts, jdbc.queryForList("SELECT * FROM expert_contact"))
        assertEquals(0, count("mailbox_review_draft").toInt())
        assertEquals(0, count("mailbox_review_draft_event").toInt())
    }

    @Test
    fun `complete state action matrix rejects every unlisted transition without events`() {
        val allowed = mapOf("DRAFT" to setOf("save", "submit", "delete", "comment"),
            "SUBMITTED" to setOf("withdraw", "return", "approve", "delete", "comment"),
            "RETURNED" to setOf("save", "submit", "delete", "comment"), "APPROVED" to emptySet())
        for (state in allowed.keys) for (action in listOf("save", "submit", "withdraw", "return", "approve", "delete", "comment")) {
            var d = create()
            if (state != "DRAFT") d = node(service.submit("user1", id(d), version(d)))
            if (state == "RETURNED") d = node(service.returnDraft("admin", id(d), version(d), "Please revise"))
            if (state == "APPROVED") d = node(service.approve("admin", id(d), version(d), token(d)))
            val draft = d
            val events = count("mailbox_review_draft_event")
            val mutate = {
                when (action) {
                    "save" -> service.save("user1", id(draft), version(draft), "Changed", "<p>Changed</p>")
                    "submit" -> service.submit("user1", id(draft), version(draft))
                    "withdraw" -> service.withdraw("user1", id(draft), version(draft))
                    "return" -> service.returnDraft("admin", id(draft), version(draft), "Revise")
                    "approve" -> service.approve("admin", id(draft), version(draft), if (state == "DRAFT") "0".repeat(64) else token(draft))
                    "delete" -> service.softDelete("user1", id(draft), version(draft))
                    else -> service.comment("user1", id(draft), version(draft), "A comment")
                }
                Unit
            }
            if (action in allowed.getValue(state)) {
                mutate()
                assertEquals(events + 1, count("mailbox_review_draft_event"), "$state/$action")
                assertEquals(version(draft) + if (action == "comment") 0 else 1, (row(id(draft))["version"] as Number).toLong())
            } else {
                val ex = assertThrows(MailReviewDraftException::class.java, mutate)
                assertTrue(ex.status == 409 || (state == "APPROVED" && ex.status == 403) ||
                    (state == "DRAFT" && action in setOf("approve", "return") && ex.status == 404), "$state/$action: ${ex.code}")
                assertEquals(events, count("mailbox_review_draft_event"))
            }
        }
        assertEquals(1L, count("mail_record"), "No workflow action may deliver a message")
        org.mockito.Mockito.verifyNoInteractions(delivery)
    }

    @Test
    fun `canonical snapshots idempotent saves resubmission and soft deletion preserve full history`() {
        val request = UUID.randomUUID().toString()
        val first = create(request = request)
        assertEquals(first, create(request = request))
        failure(409) { create("Different", request = request) }
        val second = create()
        assertNotEquals(id(first), id(second))
        val unchanged = node(service.save("user1", id(first), 1, "Subject", "<p>Body</p>"))
        assertEquals(1L, version(unchanged))
        val saved = node(service.save("user1", id(first), 1, "Second", "<p><b>Second</b></p>"))
        failure(409, "REVIEW_VERSION_CONFLICT") { service.save("user1", id(first), 1, "Third", "<p>Third</p>") }
        val submitted = node(service.submit("user1", id(first), version(saved)))
        val firstSubmittedAt = row(id(first))["submitted_at"]
        val returned = node(service.returnDraft("admin", id(first), version(submitted), "More detail"))
        val resubmitted = node(service.submit("user1", id(first), version(returned)))
        assertEquals(firstSubmittedAt, row(id(first))["submitted_at"])
        assertNull(row(id(first))["return_reason"])
        val beforeComment = row(id(first))
        service.comment("user1", id(first), version(resubmitted), "Question")
        assertEquals(beforeComment, row(id(first)))
        service.softDelete("user1", id(first), version(resubmitted))
        service.softDelete("user1", id(first), version(resubmitted))
        assertTrue(node(service.detail("user1", id(first)))["deleted"].asBoolean())
        val events = node(service.events("user1", id(first), null, 100))
        assertEquals(7, events.size())
        val payloads = jdbc.queryForList("SELECT payload_json FROM mailbox_review_draft_event WHERE draft_id=? ORDER BY id", String::class.java, id(first)).map(mapper::readTree)
        assertEquals("Subject", payloads[0]["subject"].asText())
        assertEquals(saved["html"].asText(), payloads[1]["html"].asText())
        val page = node(service.list("user1", 1, 0, 20, false))
        assertEquals(1L, page["total"].asLong())
        assertEquals(2L, node(service.list("user1", 1, 0, 20, true))["total"].asLong())
        jdbc.update("DELETE FROM mail_record WHERE expert_contact_id=1")
        jdbc.update("DELETE FROM expert_contact WHERE id=1")
        assertEquals(7, jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=?", Int::class.java, id(first)))
    }

    @Test
    fun `authorization owner admin visibility and target ownership never leak content`() {
        failure(401) { service.create(null, 1, UUID.randomUUID().toString(), "S", "<p>B</p>") }
        failure(403) { service.create("admin", 1, UUID.randomUUID().toString(), "S", "<p>B</p>") }
        failure(403) { create(contact = 2) }
        failure(422, "REVIEW_TARGET_NOT_FOUND") { create(contact = 3) }
        outbound(contact = 3, mailId = 301, status = "FAILED")
        outbound(contact = 3, account = "SIMULATOR_NOOP", mailId = 302)
        failure(422, "REVIEW_TARGET_NOT_FOUND") { create(contact = 3) }
        val d = create()
        val did = id(d)
        failure(404) { service.detail("admin", did) }
        failure(404) { service.events("admin", did) }
        jdbc.update("UPDATE mailbox_review_draft SET owner_username='someone-else' WHERE id=?", did)
        failure(404) { service.detail("user1", did) }
        jdbc.update("UPDATE mailbox_review_draft SET owner_username='user1' WHERE id=?", did)
        inbound(10, "real-b")
        val incoming = create()
        assertEquals("INBOUND", incoming["targetKind"].asText())
        assertEquals(10L, incoming["targetId"].asLong())
        assertEquals("real-b", incoming["accountCode"].asText())
        val out = row(did)
        assertEquals("OUTBOUND", out["target_kind"])
        listOf("reviewed_by", "reviewed_at", "approved_context_token", "send_request_id").forEach { assertNull(out[it]) }
        val submitted = node(service.submit("user1", did, 1))
        val withdrawn = node(service.withdraw("user1", did, version(submitted)))
        assertEquals("DRAFT", node(service.detail("admin", did))["state"].asText())
        failure(409) { service.approve("admin", did, version(withdrawn), token(withdrawn)) }
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        listOf<() -> Unit>({ service.detail("user1", did) }, { service.events("user1", did) },
            { service.save("user1", did, version(withdrawn), "No", "<p>No</p>") },
            { service.comment("user1", did, version(withdrawn), "No") },
            { service.submit("user1", did, version(withdrawn)) }, { service.softDelete("user1", did, version(withdrawn)) })
            .forEach { failure(403, "MAIL_READ_FORBIDDEN", it) }
    }

    @Test
    fun `sanitizer preserves safe structure rejects bytes placeholders blank and final validation overflow`() {
        val d = create(html = "<script>alert(1)</script><p style='color:red' onclick='evil()'><b>Bold</b><img src=x onerror='evil()'></p><ul><li>One</li></ul><a href='javascript:evil()'>bad</a><a href='https://example.test'>safe</a><a href='mailto:a@example.test'>mail</a>")
        val html = d["html"].asText()
        listOf("script", "onclick", "onerror", "style=", "<img", "javascript:").forEach { assertFalse(html.contains(it), it) }
        listOf("<b>Bold</b>", "<ul>", "<li>One</li>", "https://example.test", "mailto:a@example.test").forEach { assertTrue(html.contains(it), it) }
        assertEquals(MailContentService().normalizeManualTextLineBreaks(MailContentService().htmlToPlainText(html)), d["text"].asText())
        val snapshot = mapper.readTree(jdbc.queryForObject("SELECT payload_json FROM mailbox_review_draft_event WHERE draft_id=?", String::class.java, id(d)))
        assertEquals(html, snapshot["html"].asText())
        failure(413) { create(html = "<p>" + "界".repeat(350000) + "</p>") }
        // Escaping bare ampersands expands canonical HTML beyond the limit despite a small raw payload.
        failure(413) { create(html = "<p>" + "&".repeat(220000) + "</p>") }
        failure(422) { create(subject = "x".repeat(256)) }
        val byteLimit = 1024 * 1024
        val atLimit = "<p>" + "x".repeat(byteLimit - 7) + "</p>"
        create(html = atLimit)
        failure(413) { create(html = atLimit + "x") }
        val boundary = create(subject = "S", html = "<p>" + "x".repeat(9998) + "</p>")
        service.submit("user1", id(boundary), 1)
        val linked = create(subject = "S", html = "<p>" + "x".repeat(9998) + "</p><a href='https://example.test'></a>")
        failure(422) { service.submit("user1", id(linked), 1) }
        for (htmlBody in listOf("<p></p>", "<p>\${expertName}</p>", "<p>\${unknown}</p>", "<p>\${broken</p>", "<p>" + "x".repeat(10000) + "</p>")) {
            val invalid = create(html = htmlBody)
            failure(422) { service.submit("user1", id(invalid), version(invalid)) }
        }
        val blank = create(subject = "")
        failure(422) { service.submit("user1", id(blank), 1) }
        listOf("", " ", "x".repeat(2001)).forEach { text ->
            failure(422) { service.comment("user1", id(d), 1, text) }
            val s = submitted()
            failure(422) { service.returnDraft("admin", id(s), version(s), text) }
        }
    }

    @Test
    fun `context hashes full real history including old revisions failures and disabled account but not processing state`() {
        val d = submitted()
        var previous = token(d)
        fun changed(sql: () -> Unit) { sql(); val next = token(d); assertNotEquals(previous, next); previous = next }
        changed { inbound(20) }
        changed { outbound(mailId = 21) }
        changed { jdbc.update("UPDATE mail_record SET body='Old revised body' WHERE id=10") }
        changed { outbound(mailId = 22, status = "FAILED") }
        changed { jdbc.update("UPDATE mail_record SET send_status='SENT',sent_at='2026-09-01 09:00:00' WHERE id=22") }
        changed { outbound(account = "real-b", mailId = 23) }
        changed { jdbc.update("UPDATE mail_record SET sender_account_code='real-b' WHERE id=10") }
        changed { jdbc.update("UPDATE inbound_mail_processing SET cleaned_body='Revised clean' WHERE id=20") }
        jdbc.update("UPDATE inbound_mail_processing SET process_status='PROCESSED' WHERE id=20")
        assertEquals(previous, token(d))
        jdbc.update("INSERT INTO inbound_mail_tag(inbound_processing_id,tag_type,label,source,created_at) VALUES(20,'MANUAL','Review note','MANUAL',NOW(3))")
        jdbc.update("INSERT INTO expert_contact_note(expert_contact_id,note,updated_by,updated_at) VALUES(1,'Operator note','admin',NOW(3))")
        assertEquals(previous, token(d), "Tags and operator notes are not conversation content")
        val pageToken = token(d)
        service.list("admin", 1, 0, 1, false)
        assertEquals(pageToken, token(d), "UI pagination cannot narrow context")
        outbound(account = "SIMULATOR_NOOP", mailId = 24)
        assertEquals(previous, token(d))
        val stale = previous
        (30L..90L).forEach { outbound(mailId = it) }
        assertNotEquals(stale, token(d), "No 50-message cap or timestamp-only comparison")
        failure(409, "REVIEW_CONTEXT_CHANGED") { service.approve("admin", id(d), version(d), stale) }
        val a = node(service.approve("admin", id(d), version(d), token(d)))
        val frozen = row(id(a))
        listOf("reviewed_by", "reviewed_at", "approved_context_token", "send_request_id").forEach { assertNotNull(frozen[it]) }
        changed { jdbc.update("UPDATE mail_record SET body='Changed after review' WHERE id=10") }
        failure(409) { service.confirmContext("admin", id(a), version(a), frozen["approved_context_token"].toString()) }
        service.confirmContext("admin", id(a), version(a), token(a))
        assertEquals(frozen, row(id(a)), "Context confirmation appends evidence only")
    }

    @Test
    fun approvalRejectsContextCommittedDuringDraftLockWait() {
        rejectContextCommittedDuringDraftLockWait(submitted(), false) { inbound(20) }
    }

    @Test
    fun confirmationRejectsContextCommittedDuringDraftLockWait() {
        val draft = submitted()
        val approved = node(service.approve("admin", id(draft), version(draft), token(draft)))
        rejectContextCommittedDuringDraftLockWait(approved, true) {
            jdbc.update("UPDATE mail_record SET body='Committed during confirmation lock wait' WHERE id=10")
        }
    }

    private fun rejectContextCommittedDuringDraftLockWait(draft: JsonNode, confirmation: Boolean, change: () -> Unit) {
        val observed = token(draft)
        val before = row(id(draft))
        val events = jdbc.queryForList("SELECT * FROM mailbox_review_draft_event WHERE draft_id=? ORDER BY id", id(draft))
        val pool = Executors.newFixedThreadPool(2)
        try {
            lateinit var pending: java.util.concurrent.Future<MailReviewDraftException?>
            lateinit var fresh: String
            // Only the observer needs MySQL diagnostics privileges; production operations use the normal pool.
            DriverManager.getConnection(mysql.jdbcUrl, "root", mysql.password).use { observer ->
                TransactionTemplate(transactions).executeWithoutResult {
                    val blocker = jdbc.queryForObject("SELECT CONNECTION_ID()", Long::class.java)!!
                    jdbc.queryForList("SELECT id FROM mailbox_review_draft WHERE id=? FOR UPDATE", id(draft))
                    pending = pool.submit(Callable {
                        try {
                            if (confirmation) service.confirmContext("admin", id(draft), version(draft), observed)
                            else service.approve("admin", id(draft), version(draft), observed)
                            null
                        } catch (e: MailReviewDraftException) { e }
                    })
                    observer.prepareStatement("""SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                        JOIN performance_schema.data_locks requested ON requested.ENGINE=w.ENGINE
                          AND requested.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                        JOIN information_schema.innodb_trx blocking ON blocking.trx_id=w.BLOCKING_ENGINE_TRANSACTION_ID
                        WHERE blocking.trx_mysql_thread_id=? AND requested.OBJECT_SCHEMA=DATABASE()
                          AND requested.OBJECT_NAME='mailbox_review_draft'""").use { statement ->
                        statement.setLong(1, blocker)
                        statement.queryTimeout = 5
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                        var waiting = false
                        while (!waiting && System.nanoTime() < deadline) {
                            waiting = statement.executeQuery().use { it.next(); it.getLong(1) > 0 }
                            if (!waiting) Thread.sleep(10)
                        }
                        assertTrue(waiting, "The production request must actually wait after its initial consistent read")
                    }
                    fresh = pool.submit(Callable { change(); token(draft) }).get(10, TimeUnit.SECONDS)
                    assertNotEquals(observed, fresh, "The independent commit must be visible before releasing the draft lock")
                }
            }
            val rejected = pending.get(20, TimeUnit.SECONDS)
            assertNotNull(rejected, "A stale context must not be approved or confirmed after the lock wait")
            assertEquals(409, rejected!!.status)
            assertEquals("REVIEW_CONTEXT_CHANGED", rejected.code)
            assertEquals(before, row(id(draft)), "Rejection must not change version, state or review fields")
            assertEquals(events, jdbc.queryForList("SELECT * FROM mailbox_review_draft_event WHERE draft_id=? ORDER BY id", id(draft)))
            assertEquals(fresh, token(draft))
            if (confirmation) {
                val confirmed = service.confirmContext("admin", id(draft), version(draft), fresh)
                assertEquals(fresh, confirmed.currentToken)
                assertEquals(before, row(id(draft)), "Successful confirmation appends evidence without changing the draft")
            } else {
                val approved = node(service.approve("admin", id(draft), version(draft), fresh))
                assertEquals("APPROVED", approved["state"].asText())
                assertEquals(version(draft) + 1, version(approved))
                assertEquals(fresh, approved["approvedContextToken"].asText())
            }
            assertEquals(events.size + 1L, jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=?", Long::class.java, id(draft)))
            org.mockito.Mockito.verifyNoInteractions(delivery)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "Concurrent operations must terminate")
        }
    }

    @Test
    fun `two real transactions saving same version produce one winner and one event`() {
        val d = create()
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { n -> pool.submit(Callable {
                barrier.await(10, TimeUnit.SECONDS)
                try { service.save("user1", id(d), 1, "Version $n", "<p>Body $n</p>"); "OK" }
                catch (e: MailReviewDraftException) { e.code }
            }) }.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(listOf("OK", "REVIEW_VERSION_CONFLICT"), results.sorted())
            assertEquals(2L, (row(id(d))["version"] as Number).toLong())
            assertEquals(2L, count("mailbox_review_draft_event"))
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `event insertion failure rolls back business CAS and emits no partial history`() {
        val d = create()
        val before = row(id(d))
        jdbc.execute("CREATE TRIGGER reject_review_event BEFORE INSERT ON mailbox_review_draft_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected event failure'")
        try { assertThrows(Exception::class.java) { service.save("user1", id(d), 1, "Must rollback", "<p>Changed</p>") } }
        finally { jdbc.execute("DROP TRIGGER reject_review_event") }
        assertEquals(before, row(id(d)))
        assertEquals(1L, count("mailbox_review_draft_event"))
    }

    @Test
    fun `grant revocation and user write serialize on grant before draft`() {
        val d = create()
        val pool = Executors.newSingleThreadExecutor()
        try {
            lateinit var pending: java.util.concurrent.Future<String>
            TransactionTemplate(transactions).executeWithoutResult {
                jdbc.queryForList("SELECT username FROM expert_mail_read_access WHERE username='user1' AND expert_contact_id=1 FOR UPDATE")
                val started = CountDownLatch(1)
                pending = pool.submit(Callable {
                    started.countDown()
                    try { service.save("user1", id(d), 1, "Denied", "<p>Denied</p>"); "OK" }
                    catch (e: MailReviewDraftException) { e.code }
                })
                assertTrue(started.await(10, TimeUnit.SECONDS))
                jdbc.update("DELETE FROM expert_mail_read_access WHERE username='user1' AND expert_contact_id=1")
            }
            assertEquals("MAIL_READ_FORBIDDEN", pending.get(20, TimeUnit.SECONDS))
            assertEquals(1L, (row(id(d))["version"] as Number).toLong())
            assertEquals(1L, count("mailbox_review_draft_event"))
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `approval wins row lock before comment and rejects late user comment without version changes`() {
        val d = submitted()
        val observed = token(d)
        val pool = Executors.newSingleThreadExecutor()
        try {
            lateinit var pending: java.util.concurrent.Future<String>
            TransactionTemplate(transactions).executeWithoutResult {
                jdbc.queryForList("SELECT id FROM mailbox_review_draft WHERE id=? FOR UPDATE", id(d))
                val started = CountDownLatch(1)
                pending = pool.submit(Callable {
                    started.countDown()
                    try { service.comment("user1", id(d), version(d), "Late comment"); "OK" }
                    catch (e: MailReviewDraftException) { e.code }
                })
                assertTrue(started.await(10, TimeUnit.SECONDS))
                service.approve("admin", id(d), version(d), observed)
            }
            assertNotEquals("OK", pending.get(20, TimeUnit.SECONDS))
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=? AND event_type='COMMENT'", Long::class.java, id(d)))
            val a = node(service.detail("admin", id(d)))
            service.comment("admin", id(d), version(a), "Admin after approval")
            assertEquals(version(a), (row(id(d))["version"] as Number).toLong())
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `comment wins draft lock before approval and approval retains comment without changing frozen text`() {
        val d = submitted()
        val observed = token(d)
        val pool = Executors.newSingleThreadExecutor()
        try {
            lateinit var pending: java.util.concurrent.Future<Any>
            TransactionTemplate(transactions).executeWithoutResult {
                jdbc.queryForList("SELECT id FROM mailbox_review_draft WHERE id=? FOR UPDATE", id(d))
                service.comment("user1", id(d), version(d), "Before approval")
                val started = CountDownLatch(1)
                pending = pool.submit(Callable {
                    started.countDown()
                    service.approve("admin", id(d), version(d), observed)
                })
                assertTrue(started.await(10, TimeUnit.SECONDS))
            }
            val result = node(pending.get(20, TimeUnit.SECONDS))
            assertEquals("APPROVED", result["state"].asText())
            assertEquals(d["html"].asText(), result["html"].asText())
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft_event WHERE draft_id=? AND event_type='COMMENT'", Long::class.java, id(d)))
            val firstPage = service.events("admin", id(d), null, 2)
            val secondPage = service.events("admin", id(d), firstPage.last().id, 2)
            assertEquals(4, (firstPage + secondPage).map { it.id }.distinct().size)
            assertTrue((firstPage + secondPage).zipWithNext().all { (a, b) -> a.id > b.id })
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `successful writer holds grant until commit before revoke may finish`() {
        val d = create()
        val pool = Executors.newSingleThreadExecutor()
        try {
            lateinit var revoke: java.util.concurrent.Future<Int>
            TransactionTemplate(transactions).executeWithoutResult {
                service.save("user1", id(d), 1, "Committed first", "<p>Committed first</p>")
                val started = CountDownLatch(1)
                revoke = pool.submit(Callable {
                    started.countDown()
                    jdbc.update("DELETE FROM expert_mail_read_access WHERE username='user1' AND expert_contact_id=1")
                })
                assertTrue(started.await(10, TimeUnit.SECONDS))
            }
            assertEquals(1, revoke.get(20, TimeUnit.SECONDS))
            assertEquals("Committed first", row(id(d))["subject"])
            assertEquals(2L, count("mailbox_review_draft_event"))
            failure(403) { service.detail("user1", id(d)) }
        } finally { pool.shutdownNow() }
    }
}
