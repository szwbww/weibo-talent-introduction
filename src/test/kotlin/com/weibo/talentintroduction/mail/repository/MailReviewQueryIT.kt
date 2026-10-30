package com.weibo.talentintroduction.mail.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.controller.ConversationListResponse
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DelegatingDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.LocalDateTime
import java.util.UUID

/** Real MySQL queries and committed production review transitions; no application scan or cached truth. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MailReviewQueryIT {
    private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    private val mysql = Mysql().withDatabaseName("review_query").withUsername("test").withPassword("test")
    private val statements = mutableListOf<String>()
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: MailboxConversationRepository
    private lateinit var reviews: MailReviewDraftService
    private lateinit var conversations: MailboxConversationService
    private lateinit var tx: TransactionTemplate
    private val accounts = listOf("real-a", "real-b", "SIMULATOR_NOOP", "missing-account")
    private val filter get() = MailboxConversationRepository.ConversationFilter(accounts, reviewPendingOnly = true)
    private val mapper = ObjectMapper().findAndRegisterModules()

    @BeforeAll
    fun start() {
        mysql.start()
        val raw = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        Flyway.configure().dataSource(raw).placeholderReplacement(false).load().migrate()
        val counted = object : DelegatingDataSource(raw) {
            override fun getConnection(): Connection {
                val connection = super.getConnection()
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                    if (method.name == "prepareStatement") statements += args!![0] as String
                    try { method.invoke(connection, *(args ?: emptyArray())) }
                    catch (e: InvocationTargetException) { throw e.targetException }
                } as Connection
            }
        }
        jdbc = JdbcTemplate(counted)
        val named = NamedParameterJdbcTemplate(counted)
        repository = MailboxConversationRepository(named)
        tx = TransactionTemplate(DataSourceTransactionManager(counted))
        val senders = Mockito.mock(MailSenderAccountRepository::class.java)
        Mockito.`when`(senders.findAllByAccountCodeNot("SIMULATOR_NOOP")).thenReturn(
            listOf(account("real-a"), account("real-b").copy(enabled = false)))
        reviews = MailReviewDraftService(MailReviewDraftRepository(named, mapper), repository, senders,
            MailContentService(), MailPlaceholderService(), Mockito.mock(MailTranslationService::class.java))
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        Mockito.`when`(contacts.findAllById(Mockito.anyCollection())).thenReturn(emptyList())
        conversations = MailboxConversationService(repository, senders, contacts,
            Mockito.mock(ExpertMaterialService::class.java), Mockito.mock(InboundMailTagService::class.java),
            Mockito.mock(ExpertSearchService::class.java), Mockito.mock(MailRecordRepository::class.java),
            ExpertMailReadAccessService(named))
    }

    @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }

    @BeforeEach
    fun seed() {
        listOf("mailbox_review_draft_event", "mailbox_review_draft", "expert_mail_read_access", "expert_follow",
            "expert_mailbox_suspension", "inbound_mail_tag", "inbound_mail_processing", "mail_record", "expert_contact", "campaign", "mail_sender_account")
            .forEach { jdbc.update("DELETE FROM $it") }
        listOf("real-a", "real-b", "SIMULATOR_NOOP").forEach { code ->
            jdbc.update("""INSERT INTO mail_sender_account(account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
                VALUES(?,?,?,'smtp.fixture',465,?,'pw','imap.fixture',993,?,'pw')""", code, "$code@example.test", code, code, code)
        }
        jdbc.update("UPDATE mail_sender_account SET enabled=false WHERE account_code='real-b'")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'QUERY','Query',(SELECT id FROM mail_sender_account WHERE account_code='real-a'))")
        (1L..3L).forEach { contact(it) }
        jdbc.update("""INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,triggered_by,message_id,subject,body,send_status,sent_at,created_at)
            VALUES(1,'OUTBOUND','INTRODUCTION','real-b','SYSTEM','out-1-b','Original','Original body','SENT','2026-09-01 08:00:00','2026-09-01 08:00:00')""")
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at) VALUES('user1',1,'admin',NOW(3)),('user1',3,'admin',NOW(3))")
        draft(1, "real-a")
        draft(1, "real-b")
        draft(2, "real-a") // No current reader grant, still an admin review.
        draft(3, "real-a", "DRAFT")
        draft(3, "real-a", "RETURNED")
        draft(3, "real-a", "APPROVED")
        draft(3, "SIMULATOR_NOOP")
        draft(3, "missing-account")
        draft(3, "real-a", deleted = true)
    }

    private fun account(code: String) = MailSenderAccount(accountCode = code, senderEmail = "$code@example.test", senderName = code,
        senderTitle = null, senderDisplayName = null, teamName = null, countryName = null,
        smtpHost = "smtp.fixture", smtpPort = 465, smtpUsername = code, smtpPassword = "pw",
        imapHost = "imap.fixture", imapPort = 993, imapUsername = code, imapPassword = "pw")

    private fun contact(id: Long) {
        jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(?,1,?,?,?,'NEW')",
            id, "query-$id", "expert$id@example.test", "Expert $id")
        jdbc.update("""INSERT INTO mail_record(expert_contact_id,direction,mail_type,sender_account_code,triggered_by,message_id,subject,body,send_status,sent_at,created_at)
            VALUES(?,'OUTBOUND','INTRODUCTION','real-a','SYSTEM',?,'Original','Original body','SENT','2026-09-01 09:00:00','2026-09-01 09:00:00')""", id, "out-$id")
    }

    private fun draft(contact: Long, account: String, state: String = "SUBMITTED", deleted: Boolean = false) {
        jdbc.update("""INSERT INTO mailbox_review_draft(owner_username,expert_contact_id,client_request_id,target_kind,target_id,account_code,version,state,subject,html_body,text_body,submitted_at,deleted_at,created_at,updated_at,reviewed_by,reviewed_at,approved_context_token,send_request_id)
            VALUES('user1',?,?,'OUTBOUND',1,?,1,?,'Review','<p>Body</p>','Body',NOW(3),?,NOW(3),NOW(3),?,?,?,?)""",
            contact, UUID.randomUUID().toString(), account, state, if (deleted) LocalDateTime.now() else null,
            if (state == "APPROVED") "admin" else null, if (state == "APPROVED") LocalDateTime.now() else null,
            if (state == "APPROVED") "a".repeat(64) else null, if (state == "APPROVED") UUID.randomUUID().toString() else null)
    }

    private fun list(user: String = "admin", page: Int = 0, size: Int = 20, pending: Boolean = true, followed: Boolean = false): ConversationListResponse =
        conversations.listConversations(user, null, followed, false, false, false, null, null, null, null, null, null, null, null, page, size,
            reviewPendingOnly = pending)

    @Test
    fun `draft totals differ from expert totals and SQL pages do not duplicate contacts`() {
        assertEquals(2L, repository.countConversations("admin", filter))
        assertEquals(3L, repository.reviewPendingTotal("admin", filter))
        val first = list(size = 1)
        val second = list(page = 1, size = 1)
        assertEquals(3L, first.reviewPendingTotal)
        assertEquals(2L, first.total)
        assertEquals(setOf(1L, 2L), (first.items + second.items).map { it.contactId }.toSet())
        assertEquals(mapOf(1L to 2L, 2L to 1L), (first.items + second.items).associate { it.contactId to it.reviewPendingCount })
        assertEquals(3L, list(pending = false).total)
        val outside = list(page = 99)
        assertTrue(outside.items.isEmpty())
        assertEquals(3L, outside.reviewPendingTotal)
        assertEquals(3L, list(followed = true).reviewPendingTotal)
        assertEquals(0L, list(followed = true).total)
        val plan = repository.explainConversationsPage("admin", filter, 100, 0)
        assertTrue(plan.isNotEmpty())
        println("REVIEW_QUERY_EXPLAIN=$plan")
        statements.clear()
        assertTrue(repository.reviewPendingCountsByContacts(emptyList(), accounts, null).isEmpty())
        assertTrue(statements.isEmpty(), "Empty page must not issue IN ()")
    }

    @Test
    fun `non chip scope and account intersection preserve existing membership semantics`() {
        assertEquals(2L, repository.reviewPendingTotal("admin", filter.copy(accountCode = "real-a")))
        assertEquals(1L, repository.reviewPendingTotal("admin", filter.copy(accountCode = "real-b")))
        assertEquals(mapOf(1L to 1L), repository.reviewPendingCountsByContacts(listOf(1), accounts, "real-b"))
        assertEquals(0L, repository.reviewPendingTotal("admin", filter.copy(accountCode = "SIMULATOR_NOOP")))
        assertEquals(0L, repository.reviewPendingTotal("admin", filter.copy(accountCode = "missing-account")))
        assertEquals(2L, repository.reviewPendingTotal("admin", filter.copy(q = "Expert 1")))
        assertEquals(0L, repository.reviewPendingTotal("admin", filter.copy(q = "absent")))
        listOf(filter.copy(followed = true), filter.copy(providedOnly = true), filter.copy(repliedOnly = true),
            filter.copy(pendingOnly = true), filter.copy(waitingReply = true), filter.copy(suspendedOnly = true)).forEach {
            assertEquals(3L, repository.reviewPendingTotal("admin", it))
        }
        listOf(filter.copy(direction = "INBOUND"), filter.copy(subject = "absent"), filter.copy(label = "absent"),
            filter.copy(recipientEmail = "absent"), filter.copy(keyword = "absent"),
            filter.copy(startTime = LocalDateTime.of(2026, 10, 1, 0, 0))).forEach {
            assertEquals(0L, repository.countConversations("admin", it))
            assertTrue(repository.pageConversations("admin", it, 1, 0).isEmpty())
            assertEquals(0L, repository.reviewPendingTotal("admin", it))
            assertTrue(repository.explainConversationsPage("admin", it, 1, 0).isNotEmpty())
        }
        assertEquals(3L, repository.reviewPendingTotal("admin", filter.copy(direction = "OUTBOUND", subject = "Original", keyword = "body",
            endTime = LocalDateTime.of(2026, 10, 1, 0, 0))))
    }

    @Test
    fun `committed submit withdraw delete approve and return immediately change counts`() {
        jdbc.update("DELETE FROM mailbox_review_draft")
        fun created(): MailReviewDraftDetail = tx.execute { reviews.create("user1", 1, UUID.randomUUID().toString(), "Subject", "<p>Body</p>") } as MailReviewDraftDetail
        fun submit(d: MailReviewDraftDetail) = tx.execute { reviews.submit("user1", d.id, d.version) } as MailReviewDraftDetail
        val a = created()
        assertEquals(0L, list().reviewPendingTotal)
        val submitted = submit(a)
        assertEquals(1L, list().reviewPendingTotal)
        val withdrawn = tx.execute { reviews.withdraw("user1", a.id, submitted.version) } as MailReviewDraftDetail
        assertEquals(0L, list().reviewPendingTotal)
        val resubmitted = submit(withdrawn)
        tx.execute { reviews.softDelete("user1", a.id, resubmitted.version) }
        assertEquals(0L, list().reviewPendingTotal)
        val b = submit(created())
        val c = submit(created())
        assertEquals(2L, list().reviewPendingTotal)
        val baseline = list(pending = false).items.associate { it.contactId to listOf(it.receivedCount, it.sentCount, it.pendingCount) }
        tx.execute { reviews.approve("admin", b.id, b.version, reviews.context("admin", b.id).currentToken) }
        assertEquals(1L, list().items.single().reviewPendingCount)
        tx.execute { reviews.returnDraft("admin", c.id, c.version, "Please revise") }
        assertEquals(0L, list().total)
        assertEquals(baseline, list(pending = false).items.associate { it.contactId to listOf(it.receivedCount, it.sentCount, it.pendingCount) })
    }

    @Test
    fun `revocation preserves admin workload while deleted contacts and accounts disappear`() {
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        assertEquals(3L, list().reviewPendingTotal)
        assertFalse(repository.hasReadAccess("user1", 1))
        assertThrows(MailReadAccessDeniedException::class.java) { conversations.listMessages(1, null, null, 20, "user1") }
        jdbc.update("DELETE FROM mail_record WHERE expert_contact_id=1")
        jdbc.update("DELETE FROM expert_contact WHERE id=1")
        assertEquals(1L, list().reviewPendingTotal)
        assertEquals(listOf(2L), list().items.map { it.contactId })
        jdbc.update("UPDATE campaign SET sender_account_id=(SELECT id FROM mail_sender_account WHERE account_code='real-b')")
        jdbc.update("DELETE FROM mail_sender_account WHERE account_code='real-a'")
        assertEquals(0L, list().reviewPendingTotal)
        assertEquals(0L, list().total)
        assertEquals(9L, jdbc.queryForObject("SELECT COUNT(*) FROM mailbox_review_draft", Long::class.java))
    }

    @Test
    fun `size100 uses constant batched queries and readers never query review summaries`() {
        (4L..100L).forEach { contact(it); draft(it, "real-a") }
        statements.clear()
        list(size = 1, pending = false)
        val singleContactQueryCount = statements.size
        statements.clear()
        val result = list(size = 100, pending = false)
        val adminSql = statements.toList()
        assertEquals(100, result.items.size)
        assertEquals(100L, result.reviewPendingTotal)
        assertEquals(singleContactQueryCount, adminSql.size, "Page growth must not introduce per-contact queries")
        assertEquals(2, adminSql.count { it.contains("mailbox_review_draft") })
        assertTrue(adminSql.none { it.contains("d.html_body") || it.contains("d.text_body") })
        println("REVIEW_QUERY_SIZE100 rows=${result.items.size} sqlCount=${adminSql.size} reviewSqlCount=${adminSql.count { it.contains("mailbox_review_draft") }}")
        statements.clear()
        val reader = list(user = "user1", pending = false)
        assertEquals(2L, reader.total)
        assertEquals(0L, reader.reviewPendingTotal)
        assertTrue(reader.items.all { it.reviewPendingCount == 0L })
        assertTrue(statements.none { it.contains("mailbox_review_draft") })
        statements.clear()
        assertThrows(MailReadAccessDeniedException::class.java) { list(user = "user1") }
        assertTrue(statements.isEmpty())
    }
}
