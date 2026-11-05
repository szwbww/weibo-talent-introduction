package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.TalentIntroductionApplication
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.config.ManualReplyDelayProperties
import com.weibo.talentintroduction.mail.controller.ExpertContactLocationController
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import com.weibo.talentintroduction.llm.service.*
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.rag.service.RagKnowledgeBase
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.*
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Files
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Real HTTP handlers/auth sessions, canonical services, repositories, Spring transactions and worker.
 * Only delivery is controlled. Scheduling is deliberately not enabled: each test advances DB time
 * explicitly and dispatches the real worker. The separate runtime acceptance exercises wall time.
 * Unrelated location catalogue/controller/service are excluded because the required old JDK11 lacks
 * America/Coyhaique; no location behaviour is claimed here and no timezone fallback is installed.
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@SpringBootTest(classes = [ManualReplyDelayEndToEndIT.Config::class], properties = [
    "spring.flyway.placeholder-replacement=false", "spring.main.allow-circular-references=true",
    "talent-introduction.manual-reply-delay.enabled=true", "talent-introduction.auth.enabled=true",
    "talent-introduction.scheduling.enabled=false", "talent-introduction.mail-queue.enabled=false",
    "talent-introduction.expert-discovery.enabled=false", "talent-introduction.expert-discovery.pipeline-enabled=false",
    "talent-introduction.llm.enabled=false", "talent-introduction.translation.enabled=false",
    "talent-introduction.elasticsearch.base-url=http://127.0.0.1:9",
    "logging.level.com.weibo.talentintroduction=WARN"
])
@AutoConfigureMockMvc
class ManualReplyDelayEndToEndIT {
    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages = ["com.weibo.talentintroduction"])
    @ComponentScan(basePackages = ["com.weibo.talentintroduction"], excludeFilters = [
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class, AutoConfigurationExcludeFilter::class, Config::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [TalentIntroductionApplication::class,
            ExpertContactLocationCatalog::class, ExpertContactLocationService::class, ExpertContactLocationController::class])
    ])
    class Config : org.springframework.core.type.filter.TypeFilter {
        // Other suites contain plain @Configuration fixtures without @TestComponent.
        // Exclude the test output root, not selected business beans or bean-name conflicts.
        override fun match(reader: org.springframework.core.type.classreading.MetadataReader,
            factory: org.springframework.core.type.classreading.MetadataReaderFactory): Boolean =
            reader.resource.url.toExternalForm().startsWith(Config::class.java.protectionDomain.codeSource.location.toExternalForm())
        @Bean @Primary fun controlledDelivery() = ControlledDelivery()
    }

    class ControlledDelivery : MailDeliveryService {
        val sent: MutableList<ComposedMail> = Collections.synchronizedList(mutableListOf())
        @Volatile var next: ((ComposedMail) -> DeliveredMail)? = null
        override fun send(account: MailSenderAccount, mail: ComposedMail): DeliveredMail {
            check(mail.to.endsWith("@example.test")) { "Isolated recipient required" }
            sent.add(mail)
            return next?.invoke(mail) ?: DeliveredMail(mail.messageId, "SENT")
        }
        fun reset() { sent.clear(); next = null }
    }

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("activation07").withUsername("test").withPassword("test")
            .withCommand("mysqld", "--max-allowed-packet=16777216", "--log-bin-trust-function-creators=1")
        private val attachmentsRoot = Files.createTempDirectory("activation07-attachments-")
        @JvmStatic @DynamicPropertySource fun database(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("talent-introduction.mail-attachment-storage.base-path") { attachmentsRoot.toString() }
        }
        @JvmStatic @AfterAll fun stop() {
            if (mysql.isRunning) mysql.stop()
            attachmentsRoot.toFile().deleteRecursively()
        }
        private const val INBOUND = "/api/mail/unmatched-inbound/100/manual-rich-reply"
        private const val OUTBOUND = "/api/mail/mailbox/conversations/1/manual-rich-reply"
        private const val DELAYS = "/api/mail/mailbox/conversations/1/delayed-replies"
        private const val REVIEWS = "/api/mail/mailbox/review-drafts"
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var flags: ManualReplyDelayProperties
    @Autowired private lateinit var smtp: ControlledDelivery
    @Autowired private lateinit var worker: DelayedManualReplyWorker
    @Autowired private lateinit var jobs: DelayedManualReplyRepository
    @Autowired private lateinit var lifecycle: DelayedManualReplyService
    @Autowired private lateinit var drafts: MailReplyDraftService
    @Autowired private lateinit var qaRules: QaRuleRepository
    @Autowired private lateinit var rag: RagKnowledgeBase
    @Autowired private lateinit var workbench: TrustReplyWorkbenchService
    @Autowired private lateinit var content: MailContentService
    @Autowired private lateinit var meetings: MeetingConfirmationService
    @Autowired private lateinit var reviews: MailReviewDraftService
    @Autowired private lateinit var attempts: ManualReplySendAttemptService
    @Autowired private lateinit var attachmentService: OutboundAttachmentService

    // Test-only configuration switch; runtime acceptance restarts with the actual environment flag.
    private fun setEnabled(enabled: Boolean) =
        org.springframework.test.util.ReflectionTestUtils.setField(flags, "enabled", enabled)

    @BeforeEach fun seed() {
        setEnabled(true)
        smtp.reset()
        jdbc.execute("DROP TRIGGER IF EXISTS activation_reject_success")
        listOf("operator_action_log", "delayed_manual_reply", "mailbox_review_draft_event", "mailbox_review_draft", "mailbox_reply_draft",
            "expert_mail_read_access", "meeting_calendar_event", "mail_record_rag_fact", "mail_record_qa_rule", "mail_record",
            "mail_send_attempt", "outbound_mail_attachment", "inbound_mail_processing", "expert_contact_status_history",
            "expert_contact", "campaign", "mail_sender_account", "email_suppression").forEach { jdbc.update("DELETE FROM $it") }
        for (actor in listOf("admin", "user1")) {
            jdbc.update("""INSERT INTO admin_user(username,password_hash,must_change_password,created_at,updated_at)
                SELECT ?,'isolated-fixture',0,NOW(),NOW() FROM dual WHERE NOT EXISTS(SELECT 1 FROM admin_user WHERE username=?)""", actor, actor)
            jdbc.update("UPDATE admin_user SET must_change_password=0 WHERE username=?", actor)
        }
        jdbc.update("""INSERT INTO mail_sender_account(id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
            VALUES(1,'activation','sender@example.test','Sender','127.0.0.1',25287,'sender','pw','127.0.0.1',993,'sender','pw'),
            (2,'other','other@example.test','Other','127.0.0.1',25287,'other','pw','127.0.0.1',993,'other','pw')""")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'ACTIVATION','Activation',1)")
        for (id in 1L..2L) jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(?,1,?,?,?,'WAITING_REPLY')",
            id, "activation-$id", "expert$id@example.test", "Expert $id")
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,access_level,granted_by,created_at) VALUES('user1',1,'FULL','admin',NOW(3))")
        inbound(100)
        anchor(10)
    }

    @AfterEach fun cleanupTrigger() { jdbc.execute("DROP TRIGGER IF EXISTS activation_reject_success") }
    private fun inbound(id: Long) {
        jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id)
            VALUES(?,'activation','INBOX',1,?,?,'expert1@example.test','Inbound','Hello','Hello',NOW(3),'MANUAL_REVIEW','QA_NO_MATCH',1)""", id, id, "<in-$id@test>")
    }
    private fun anchor(id: Long, account: String = "activation") {
        jdbc.update("""INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,triggered_by,message_id,subject,body,send_status,sent_at,created_at)
            VALUES(?,1,'OUTBOUND','INTRODUCTION',?,'SYSTEM',?,'Introduction','Introduction body','SENT',NOW(3),NOW(3))""", id, account, "<anchor-$id@test>")
    }
    private fun session(actor: String): MockHttpSession = MockHttpSession().also { it.setAttribute(AuthSessionKeys.USERNAME, actor) }
    private fun request(path: String, body: Any, actor: String = "admin", status: Int = 200): JsonNode {
        val response = mvc.perform(post(path).session(session(actor)).contentType(MediaType.APPLICATION_JSON)
            .content(if (body is String) body else mapper.writeValueAsString(body))).andReturn().response
        assertEquals(status, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }
    private fun read(path: String, actor: String = "admin", status: Int = 200): JsonNode {
        val response = mvc.perform(get(path).session(session(actor))).andReturn().response
        assertEquals(status, response.status, response.contentAsString)
        return mapper.readTree(response.contentAsString)
    }
    private fun body(text: String = "Hello"): MutableMap<String, Any?> = linkedMapOf(
        "subject" to "Re: Test", "htmlBody" to "<p>$text</p>", "textBody" to text,
        "operatorName" to "forged-admin", "safetyWarningConfirmed" to true)
    private fun outboundBody(anchor: Long? = null): MutableMap<String, Any?> = body().also {
        it["requestId"] = UUID.randomUUID().toString(); it["accountScope"] = null; it["anchorMailRecordId"] = anchor
    }
    private fun queued(response: JsonNode): JsonNode {
        assertEquals("WAITING", response["sendStatus"].asText())
        assertTrue(response["messageId"].asText().isNotBlank())
        assertEquals("NOT_APPLICABLE", response["unsupportedAnswerArchiveStatus"].asText())
        assertEquals(0, response["unsupportedAnswerArchivedCount"].asInt())
        val view = response["delayedReply"]
        assertEquals("WAITING", view["status"].asText())
        assertEquals(60_000L, view["dueAtMs"].asLong() - view["createdAtMs"].asLong())
        assertTrue(view["sentMailRecordId"].isNull)
        assertEquals(response["messageId"].asText(), jdbc.queryForObject(
            "SELECT a.message_id FROM mail_send_attempt a JOIN delayed_manual_reply d ON d.send_attempt_id=a.id WHERE d.id=?", String::class.java, view["id"].asLong()))
        return view
    }
    private fun count(table: String, where: String = "1=1") = jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $where", Int::class.java)!!
    private fun row(view: JsonNode) = jobs.findById(view["id"].asLong())!!
    private fun due(view: JsonNode) { jdbc.update("UPDATE delayed_manual_reply SET due_at_ms=? WHERE id=?", jobs.currentTimeMs() - 1, view["id"].asLong()) }
    private fun dispatch(view: JsonNode): DelayedManualReplyRow { due(view); worker.dispatchDue(); return row(view) }
    private fun cancel(view: JsonNode): JsonNode = request("$DELAYS/${view["id"].asLong()}/cancel", mapOf("expectedRevision" to view["revision"].asLong()))
    private fun approved(): MailReviewDraftDetail {
        val created = reviews.create("user1", 1, UUID.randomUUID().toString(), "Re: Test", "<p>Hello</p>") as MailReviewDraftDetail
        val submitted = reviews.submit("user1", created.id, created.version) as MailReviewDraftDetail
        return reviews.approve("admin", submitted.id, submitted.version, reviews.context("admin", submitted.id).currentToken) as MailReviewDraftDetail
    }
    private fun reviewBody(review: MailReviewDraftDetail) =
        linkedMapOf<String, Any?>("expectedVersion" to review.version, "safetyWarningConfirmed" to true)

    @Test fun `enabled legacy inbound freezes actual actor and dispatches once with atomic facts`() {
        val response = request(INBOUND, body(), "user1")
        val view = queued(response)
        assertEquals("user1", row(view).actorUsername)
        assertEquals(0, smtp.sent.size)
        assertEquals(0, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
        assertEquals(0, count("mail_record_qa_rule"))
        worker.dispatchDue()
        assertEquals(0, smtp.sent.size, "Worker must respect the database deadline")
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals(1, smtp.sent.size)
        assertEquals(response["messageId"].asText(), smtp.sent.single().messageId)
        assertEquals(1, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
        assertEquals(1, count("mail_send_attempt", "status='SENT'"))
        assertEquals(0, jdbc.queryForObject("SELECT today_sent_count FROM mail_sender_account WHERE id=1", Int::class.java))
        assertEquals("SENT", request(INBOUND, body(), "user1")["sendStatus"].asText())
        worker.dispatchDue()
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `disabled without existing job stays synchronous while old SENT remains deduplicated when enabled`() {
        setEnabled(false)
        val sent = request(INBOUND, body())
        assertEquals("SENT", sent["sendStatus"].asText())
        assertTrue(sent["delayedReply"].isNull)
        assertEquals(0, count("delayed_manual_reply"))
        setEnabled(true)
        assertEquals(sent["messageId"], request(INBOUND, body())["messageId"])
        assertEquals(0, count("delayed_manual_reply"))
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `ordinary duplicate and disabled switch retain authoritative waiting deadline`() {
        val first = queued(request(INBOUND, body()))
        for (enabled in listOf(true, false)) {
            setEnabled(enabled)
            val again = request(INBOUND, body())["delayedReply"]
            assertEquals(first["id"], again["id"])
            assertEquals(first["dueAtMs"], again["dueAtMs"])
            assertEquals(first["revision"], again["revision"])
        }
        assertEquals(0, smtp.sent.size)
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(first).status)
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `cancelled legacy requires current positive revision and requeues even while flag disabled`() {
        val first = queued(request(INBOUND, body()))
        val cancelled = cancel(first)
        setEnabled(false)
        assertEquals("DELAYED_REPLY_REVISION_CONFLICT", request(INBOUND, body(), status = 409)["code"].asText())
        request(INBOUND, body().also { it["delayExpectedRevision"] = first["revision"].asLong() }, status = 409)
        for (bad in listOf(0L, -1L)) request(INBOUND, body().also { it["delayExpectedRevision"] = bad }, status = 400)
        val resumed = request(INBOUND, body().also { it["delayExpectedRevision"] = cancelled["revision"].asLong() })["delayedReply"]
        assertEquals(first["id"], resumed["id"])
        assertTrue(resumed["revision"].asLong() > cancelled["revision"].asLong())
        assertTrue(resumed["dueAtMs"].asLong() >= jobs.currentTimeMs() + 59_000)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `draftless outbound optional and explicit anchors preserve restore source and reserved identity`() {
        for (anchorId in listOf<Long?>(null, 10)) {
            val input = outboundBody(anchorId)
            val first = queued(request(OUTBOUND, input))
            val frozen = DelayedManualReplySnapshotCodec.decodeOrNull(row(first).snapshotJson)!!
            assertEquals(10L, frozen.source.anchorMailRecordId)
            assertEquals(anchorId, (frozen.restore as DelayedLegacyRestore).originalConversationInput!!.anchorMailRecordId)
            cancel(first)
            val restore = read("$DELAYS/${first["id"].asLong()}/restore")
            assertEquals("OUTBOUND", restore["sourceKind"].asText())
            assertEquals(10L, restore["anchorMailRecordId"].asLong())
            assertEquals(input["requestId"], restore["originalConversationInput"]["requestId"].asText())
            input["delayExpectedRevision"] = row(first).revision
            val resumed = request(OUTBOUND, input)["delayedReply"]
            assertEquals(first["id"], resumed["id"])
            assertEquals(DelayedManualReplyStatus.SENT, dispatch(resumed).status)
        }
        assertEquals(2, smtp.sent.size)
        assertTrue(smtp.sent.all { it.inReplyTo == "<anchor-10@test>" })
    }

    @Test fun `draft source remains frozen until successful dispatch closes it atomically`() {
        val saved = drafts.save("user1", MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, "activation"),
            MailReplyDraftSaveRequest(0, MailReplyDraftContent("Re: Test", "<p>Hello</p>", "Hello")))
        val input = body().also { it["draftRef"] = MailReplyDraftRef(saved.id!!, saved.version) }
        val view = queued(request(INBOUND, input, "user1"))
        assertEquals("DRAFT", view["originKind"].asText())
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT state FROM mailbox_reply_draft WHERE id=?", String::class.java, saved.id))
        assertEquals(0, smtp.sent.size)
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals("SENT", jdbc.queryForObject("SELECT state FROM mailbox_reply_draft WHERE id=?", String::class.java, saved.id))
        assertEquals(1, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test fun `review server approved body and actor survive duplicate true false routing without changing due`() {
        val approved = approved()
        val input = reviewBody(approved)
        val first = queued(request("$REVIEWS/${approved.id}/send", input))
        assertEquals("REVIEW", first["originKind"].asText())
        assertEquals("admin", row(first).actorUsername)
        for (enabled in listOf(true, false)) {
            setEnabled(enabled)
            val again = request("$REVIEWS/${approved.id}/send", input)["delayedReply"]
            assertEquals(first["id"], again["id"])
            assertEquals(first["dueAtMs"], again["dueAtMs"])
        }
        assertEquals(0, smtp.sent.size)
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(first).status)
        assertEquals(approved.html, smtp.sent.single().body)
        assertEquals(approved.text, smtp.sent.single().text)
        assertEquals("APPROVED", jdbc.queryForObject("SELECT state FROM mailbox_review_draft WHERE id=?", String::class.java, approved.id))
        assertEquals(1, count("mailbox_review_draft_event", "event_type='SEND_SUCCEEDED'"))
    }

    @Test fun `review queued read reference is not synchronous dispatch authority`() {
        val approved = approved()
        val first = queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved)))
        val ref = reviews.prepareSend("admin", approved.id, MailReviewSendRequest(approved.version), allowQueuedResult = true)
        val payload = worker.preflight(row(first)).first.payload.also { it.reviewSendRef = ref }
        val failure = assertThrows(MailReviewDraftException::class.java) { attempts.prepareAndClaim(payload) }
        assertEquals(409, failure.status)
        assertEquals("REVIEW_SEND_QUEUED", failure.code)
        assertThrows(MailReviewDraftException::class.java) {
            reviews.prepareSend("admin", approved.id, MailReviewSendRequest(approved.version))
        }
        assertEquals(first["dueAtMs"].asLong(), row(first).dueAtMs)
        assertEquals(0, smtp.sent.size)
        assertEquals("PREPARED", jdbc.queryForObject("SELECT status FROM mail_send_attempt WHERE id=?", String::class.java, row(first).sendAttemptId))
    }

    @Test fun `review HTTP revision type range positivity and payload allowlist remain strict`() {
        val approved = approved()
        for (invalid in listOf("0", "-1", "1.5", "9223372036854775808", "\"1\"", "true", "{}", "[]")) {
            request("$REVIEWS/${approved.id}/send", "{\"expectedVersion\":${approved.version},\"delayExpectedRevision\":$invalid}", status = 400)
        }
        for (field in listOf("htmlBody", "actor", "allowQueuedResult", "reviewSendRef", "snapshot")) {
            request("$REVIEWS/${approved.id}/send", reviewBody(approved).also { it[field] = "forged" }, status = 422)
        }
        assertEquals(0, count("delayed_manual_reply"))
        assertEquals(0, smtp.sent.size)
        queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved).also { it["delayExpectedRevision"] = null }))
    }

    @Test fun `review cancellation current revision and confirmation remain authoritative`() {
        val approved = approved()
        val first = queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved)))
        val cancelled = cancel(first)
        request("$REVIEWS/${approved.id}/send", reviewBody(approved), status = 409)
        inbound(101)
        request("$REVIEWS/${approved.id}/send", reviewBody(approved).also { it["delayExpectedRevision"] = cancelled["revision"].asLong() }, status = 409)
        val confirm = reviews.confirmContext("admin", approved.id, approved.version, reviews.context("admin", approved.id).currentToken)
        val resumed = request("$REVIEWS/${approved.id}/send", reviewBody(approved).also {
            it["delayExpectedRevision"] = cancelled["revision"].asLong(); it["confirmationEventId"] = confirm.confirmationEventId
        })["delayedReply"]
        assertEquals(first["id"], resumed["id"])
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(resumed).status)
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `queued review still rejects author grant withdrawal and unknown read preparation`() {
        val approved = approved()
        val first = queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved)))
        jdbc.update("DELETE FROM expert_mail_read_access WHERE username='user1'")
        request("$REVIEWS/${approved.id}/send", reviewBody(approved), status = 403)
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,access_level,granted_by,created_at) VALUES('user1',1,'FULL','admin',NOW(3))")
        due(first)
        val claimed = lifecycle.claim(first["id"].asLong())!!
        lifecycle.recoverStale(claimedBeforeMs = jobs.currentTimeMs())
        assertEquals(DelayedManualReplyStatus.UNKNOWN, row(first).status)
        request("$REVIEWS/${approved.id}/send", reviewBody(approved), status = 409)
        assertThrows(MailReviewDraftException::class.java) {
            reviews.prepareSend("admin", approved.id, MailReviewSendRequest(approved.version), allowQueuedResult = true)
        }
        assertNotNull(claimed)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `session permissions and queued actor identity cannot be replaced by operator name`() {
        val view = queued(request(INBOUND, body(), "user1"))
        request(INBOUND, body(), "admin", 409)
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE username='user1'")
        request(INBOUND, body(), "user1", 403)
        read("$DELAYS/${view["id"].asLong()}", "user1")
        request("$DELAYS/${view["id"].asLong()}/cancel", mapOf("expectedRevision" to 1), "user1", 403)
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `known 451 failure only explicit retry creates another full delay and one eventual record`() {
        val view = queued(request(INBOUND, body()))
        smtp.next = { DeliveredMail(it.messageId, "FAILED", SmtpErrorCategory.TRANSIENT, 451, "controlled 451") }
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(1, smtp.sent.size)
        setEnabled(false)
        request(INBOUND, body(), status = 409)
        assertEquals(1, smtp.sent.size)
        val failed = row(view)
        val retry = request("$DELAYS/${failed.id}/retry", mapOf("expectedRevision" to failed.revision))
        assertEquals("WAITING", retry["status"].asText())
        assertTrue(retry["dueAtMs"].asLong() >= jobs.currentTimeMs() + 59_000)
        smtp.next = null
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(retry).status)
        assertEquals(2, smtp.sent.size)
        assertEquals(1, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
    }

    @Test fun `permanent 550 failure forbids both retry routes`() {
        val view = queued(request(INBOUND, body()))
        smtp.next = { DeliveredMail(it.messageId, "FAILED", SmtpErrorCategory.PERMANENT, 550, "controlled 550") }
        val failed = dispatch(view)
        assertEquals(DelayedManualReplyStatus.FAILED, failed.status)
        request("$DELAYS/${failed.id}/retry", mapOf("expectedRevision" to failed.revision), status = 409)
        request(INBOUND, body(), status = 409)
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `ambiguous DATA disconnect becomes unknown and never retries even disabled`() {
        val view = queued(request(INBOUND, body()))
        smtp.next = { throw java.net.SocketException("controlled disconnect after DATA acceptance") }
        assertEquals(DelayedManualReplyStatus.UNKNOWN, dispatch(view).status)
        setEnabled(false)
        request(INBOUND, body(), status = 409)
        val unknown = row(view)
        request("$DELAYS/${unknown.id}/retry", mapOf("expectedRevision" to unknown.revision), status = 409)
        worker.dispatchDue()
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `success finalizer rollback preserves unknown without partial sent facts`() {
        val approved = approved()
        val view = queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved)))
        jdbc.execute("""CREATE TRIGGER activation_reject_success BEFORE INSERT ON mailbox_review_draft_event FOR EACH ROW
            BEGIN IF NEW.event_type='SEND_SUCCEEDED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled finalizer rollback'; END IF; END""")
        assertEquals(DelayedManualReplyStatus.UNKNOWN, dispatch(view).status)
        assertEquals(1, smtp.sent.size)
        assertEquals(0, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
        assertEquals(0, count("mail_record_qa_rule"))
        assertEquals(0, count("mail_record_rag_fact"))
        assertEquals(0, count("meeting_calendar_event"))
        request("$REVIEWS/${approved.id}/send", reviewBody(approved), status = 409)
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `lost HTTP response replays original job rather than accepting another attempt`() {
        // The first real controller response is intentionally discarded by this caller.
        request(INBOUND, body())
        val first = jdbc.queryForMap("SELECT id,due_at_ms,revision FROM delayed_manual_reply")
        val again = request(INBOUND, body())["delayedReply"]
        assertEquals((first["id"] as Number).toLong(), again["id"].asLong())
        assertEquals((first["due_at_ms"] as Number).toLong(), again["dueAtMs"].asLong())
        assertEquals(1, count("mail_send_attempt"))
        assertEquals(1, count("delayed_manual_reply"))
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `actual competing cancellation and claim use database deadline and permit only claim after due`() {
        val view = queued(request(INBOUND, body()))
        due(view)
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val cancel = pool.submit<JsonNode> { start.await(); request("$DELAYS/${view["id"].asLong()}/cancel", mapOf("expectedRevision" to 1), status = 409) }
            val send = pool.submit { start.await(); worker.dispatchDue() }
            start.countDown()
            assertEquals("DELAYED_REPLY_TOO_LATE", cancel.get(20, TimeUnit.SECONDS)["code"].asText())
            send.get(20, TimeUnit.SECONDS)
            assertEquals(DelayedManualReplyStatus.SENT, row(view).status)
            assertEquals(1, smtp.sent.size)
        } finally { pool.shutdownNow() }
    }

    @Test fun `cancel before deadline prevents worker claim and leaves no successful effects`() {
        val view = queued(request(INBOUND, body()))
        cancel(view)
        due(view)
        worker.dispatchDue()
        assertEquals(DelayedManualReplyStatus.CANCELLED, row(view).status)
        assertEquals(0, smtp.sent.size)
        assertEquals(0, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test fun `worker revalidates changed source before SMTP`() {
        val view = queued(request(INBOUND, body()))
        jdbc.update("UPDATE inbound_mail_processing SET message_id='<changed@test>' WHERE id=100")
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `new inbound invalidates approved review at worker preflight`() {
        val approved = approved()
        val view = queued(request("$REVIEWS/${approved.id}/send", reviewBody(approved)))
        inbound(101)
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `new suppression blocks queued mail before SMTP`() {
        val view = queued(request(INBOUND, body()))
        jdbc.update("INSERT INTO email_suppression(email,reason,source) VALUES('expert1@example.test','UNSUBSCRIBED','activation-fixture')")
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `uploaded attachment bytes are frozen and corruption fails closed before SMTP`() {
        val upload = attachmentService.upload(1, "admin", "note.txt", "text/plain", "real attachment".byteInputStream())
        val input = body().also { it["attachmentIds"] = listOf(upload.id) }
        val view = queued(request(INBOUND, input))
        val frozen = DelayedManualReplySnapshotCodec.decodeOrNull(row(view).snapshotJson)!!
        assertEquals(upload.id, frozen.payload.outboundAttachments.single().id)
        Files.write(attachmentsRoot.resolve("outbound").resolve(upload.id), "changed bytes".toByteArray())
        assertEquals(DelayedManualReplyStatus.FAILED, dispatch(view).status)
        assertEquals(0, smtp.sent.size)
    }

    @Test fun `uploaded attachment dispatch retains content and query projects the single sent mail`() {
        val bytes = "real attachment".toByteArray()
        val upload = attachmentService.upload(1, "admin", "note.txt", "text/plain", bytes.inputStream())
        val view = queued(request(INBOUND, body().also { it["attachmentIds"] = listOf(upload.id) }))
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertArrayEquals(bytes, smtp.sent.single().outboundAttachments.single().bytes)
        val queried = read("$DELAYS/${view["id"].asLong()}")
        assertEquals("SENT", queried["status"].asText())
        assertTrue(queried["sentMailRecordId"].asLong() > 0)
        assertEquals(1, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
    }

    private fun factIds(): List<Long> {
        val category = jdbc.queryForObject("SELECT MIN(id) FROM qa_category", Long::class.java)!!
        return listOf("Hello", "Thank you").mapIndexed { index, text ->
            qaRules.save(QaRule(categoryId = category, keywords = "what is the program", replySubject = null, priority = 2 - index,
                replyBody = text, answerBody = text, displayName = "Activation fixture", coverageKeys = "programme.purpose",
                createdAt = java.time.LocalDateTime.now(), updatedAt = java.time.LocalDateTime.now())).id!!
        }.reversed()
    }

    @Test fun `legacy QA audit preserves canonical chosen order only after atomic success`() {
        jdbc.update("UPDATE inbound_mail_processing SET body='What is the program?',cleaned_body='What is the program?' WHERE id=100")
        val ids = factIds()
        val view = queued(request(INBOUND, body().also { it["qaRuleIds"] = ids }))
        assertEquals(0, count("mail_record_qa_rule"))
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals(ids, jdbc.queryForList("SELECT qa_rule_id FROM mail_record_qa_rule ORDER BY ordinal", Long::class.java))
        assertEquals(0, count("mail_record_rag_fact"))
    }

    @Test fun `RAG frozen corpus and fact order survive queue with no QA facts mixed into audit`() {
        val facts = rag.snapshot().facts.filter { it.enabled }.take(2).map { it.factCode }.reversed()
        assertEquals(2, facts.size, "Real versioned corpus must provide the fixture facts")
        val input = body().also { it["ragFactCodes"] = facts; it["ragCorpusFingerprint"] = rag.fingerprint() }
        val view = queued(request(INBOUND, input))
        assertEquals(0, count("mail_record_rag_fact"))
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals(facts, jdbc.queryForList("SELECT fact_code FROM mail_record_rag_fact ORDER BY ordinal", String::class.java))
        assertEquals(0, count("mail_record_qa_rule"))
    }

    @Test fun `real workbench assembly is regenerated validated queued and audited without client verified authority`() {
        jdbc.update("UPDATE inbound_mail_processing SET body='What is the program?',cleaned_body='What is the program?' WHERE id=100")
        val ids = factIds()
        val source = TrustReplySourceRef(TrustReplySourceType.LIVE_INBOUND, 100)
        val bootstrap = workbench.bootstrap(TrustReplyBootstrapRequest(source, requestedFactIds = ids))
        assertTrue(bootstrap.requestCoverage.isNotEmpty())
        val locked = bootstrap.requestCoverage.map { item ->
            val version = workbench.adjustItem(TrustReplyItemAdjustmentRequest(source, bootstrap.sourceVersion,
                item.evidenceSetVersion, item.requestKey, TrustReplyItemHandling.ANSWER_FACTS_VERBATIM,
                requestFactSelections = bootstrap.requestFactSelections)).version
            TrustReplyLockedItemRequest(version.requestKey, version.versionId, version.handling, version.answerText,
                version.claims, version.model, version.generationKind, version.evidenceSetVersion, version.sourceVersion,
                version.operatorInstructionHash, version.operatorInstruction, version.contextVersion)
        }
        val assembly = TrustReplyAssembleRequest(source, bootstrap.sourceVersion, bootstrap.evidenceSetVersion,
            locked, requestFactSelections = bootstrap.requestFactSelections,
            frameSnapshot = bootstrap.frameSnapshot)
        val assembled = workbench.assemble(assembly)
        val input = body(assembled.rawDraftText).also {
            it["htmlBody"] = content.plainTextToHtml(assembled.rawDraftText)
            it["trustReplyAssembly"] = assembly
            it["qaRuleIds"] = assembled.canonicalFactIds
            it["safetyWarningConfirmed"] = true
        }
        val view = queued(request(INBOUND, input))
        assertEquals(0, count("mail_record_qa_rule"))
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals(assembled.canonicalFactIds, jdbc.queryForList("SELECT qa_rule_id FROM mail_record_qa_rule ORDER BY ordinal", Long::class.java))
        assertEquals(1, smtp.sent.size)
    }

    @Test fun `real meeting preview queues immutable ICS and commits calendar only after successful delivery`() {
        jdbc.update("DELETE b FROM mail_compose_template_block b JOIN mail_compose_template t ON t.id=b.template_id WHERE t.template_code='MEETING_INVITATION'")
        jdbc.update("INSERT INTO mail_compose_template_block(template_id,block_order,block_type,custom_text) SELECT id,0,'CUSTOM_TEXT',? FROM mail_compose_template WHERE template_code='MEETING_INVITATION'",
            "Hello,\n\nMeeting: \${meeting_time}\n\${zoom_url}\n\nBest regards,\n\${senderName}")
        val meeting = MeetingInput(zoneId = "Asia/Shanghai", startLocal = "2030-11-10T10:00", endLocal = "2030-11-10T10:30",
            zoomUrl = "https://zoom.us/j/123456789", generatedAt = "2030-11-01T00:00:00Z")
        val preview = meetings.preview(100, MeetingPreviewRequest(contactId = 1, meeting = meeting))
        val input = body(preview.textBody).also {
            it["htmlBody"] = preview.htmlBody; it["meeting"] = meeting
            it["previewAttachmentSha256"] = preview.attachment.sha256; it["safetyWarningConfirmed"] = true
        }
        val view = queued(request(INBOUND, input))
        assertEquals(0, count("meeting_calendar_event"))
        assertEquals(DelayedManualReplyStatus.SENT, dispatch(view).status)
        assertEquals(preview.attachment.sha256, smtp.sent.single().calendarAttachment!!.sha256)
        assertEquals(preview.attachment.icsText, smtp.sent.single().calendarAttachment!!.icsText)
        assertEquals(1, count("meeting_calendar_event"))
    }

    @Test fun `final safety confirmation cancellation and stale RAG create neither job nor attempt`() {
        request(INBOUND, body("请放心，一切顺利").also { it["safetyWarningConfirmed"] = false }, status = 422)
        // Existing non-delayed ResponseStatusException transport is unchanged by activation.
        val stale = request(INBOUND, body().also { it["ragFactCodes"] = emptyList<String>(); it["ragCorpusFingerprint"] = "stale" }, status = 500)
        assertTrue(stale["message"].asText().contains("RAG_CORPUS_STALE"))
        assertEquals(0, count("delayed_manual_reply"))
        assertEquals(0, count("mail_send_attempt"))
        assertEquals(0, smtp.sent.size)
    }
}
