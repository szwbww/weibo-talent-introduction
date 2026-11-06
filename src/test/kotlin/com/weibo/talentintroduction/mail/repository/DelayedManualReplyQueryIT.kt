package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.service.*
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

/** Real MySQL/query/service/API boundary. No production datasource or SMTP is used. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@SpringBootTest(classes = [DelayedManualReplyQueryIT.Config::class], properties = [
    "spring.flyway.placeholder-replacement=false", "talent-introduction.scheduling.enabled=false",
    "talent-introduction.mail-queue.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"
])
class DelayedManualReplyQueryIT {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.test.context.TestComponent
    @Import(DelayedManualReplyLifecycleIT.Config::class, MailboxConversationService::class)
    class Config

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("delayed_query").withUsername("test").withPassword("test")
            .withCommand("mysqld", "--max-allowed-packet=16777216", "--log-bin-trust-function-creators=1")
        @JvmStatic @DynamicPropertySource fun database(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            println("QUERY_ISOLATED_DB ${mysql.jdbcUrl} container=${mysql.containerId}")
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        private const val EPOCH = 1793836800000L
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var repository: MailboxConversationRepository
    @Autowired lateinit var delayed: DelayedManualReplyRepository
    @Autowired lateinit var lifecycle: DelayedManualReplyService
    @Autowired lateinit var attempts: ManualReplySendAttemptService
    @Autowired lateinit var service: MailboxConversationService
    @Autowired lateinit var draftRepository: MailReplyDraftRepository
    @Autowired lateinit var reviewRepository: MailReviewDraftRepository
    @SpyBean lateinit var named: NamedParameterJdbcTemplate
    @SpyBean lateinit var attachmentService: OutboundAttachmentService
    @Autowired lateinit var drafts: MailReplyDraftService
    @Autowired lateinit var context: org.springframework.context.ApplicationContext
    @Autowired lateinit var transactions: org.springframework.transaction.PlatformTransactionManager
    @MockBean lateinit var materials: ExpertMaterialService
    @MockBean lateinit var tags: InboundMailTagService
    @MockBean lateinit var profiles: ExpertSearchService
    @MockBean lateinit var translation: MailTranslationService
    private var inboundSequence = 100L
    private val accounts = listOf("sender", "second")
    private fun filter() = MailboxConversationRepository.ConversationFilter(accounts)

    @BeforeEach fun seed() {
        listOf("delayed_manual_reply", "mailbox_review_draft_event", "mailbox_review_draft", "mailbox_reply_draft",
            "expert_mail_read_access", "expert_mailbox_suspension", "expert_follow", "expert_replied_dismissal",
            "meeting_calendar_event", "mail_record_rag_fact", "mail_record_qa_rule", "mail_record", "mail_send_attempt",
            "outbound_mail_attachment", "inbound_mail_tag", "inbound_mail_processing", "expert_contact", "campaign",
            "mail_sender_account", "email_suppression").forEach { jdbc.update("DELETE FROM $it") }
        for (actor in listOf("admin", "user1")) jdbc.update("""INSERT INTO admin_user
            (username,password_hash,must_change_password,created_at,updated_at)
            SELECT ?,'isolated-fixture',0,NOW(),NOW() FROM dual
            WHERE NOT EXISTS (SELECT 1 FROM admin_user WHERE username=?)""", actor, actor)
        accounts.forEach { code -> jdbc.update("""INSERT INTO mail_sender_account(account_code,sender_email,sender_name,
            smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
            VALUES(?,?,'Fixture','localhost',465,'unused','unused','localhost',993,'unused','unused')""", code, "$code@example.test") }
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'QUERY','Query',(SELECT id FROM mail_sender_account WHERE account_code='sender'))")
        (1L..2L).forEach { jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(?,1,?,?,?,'WAITING_REPLY')",
            it, if (it == 1L) "orcid" else "other", "expert$it@example.test", "Expert $it") }
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,access_level,granted_by,created_at) VALUES('user1',1,'FULL','admin',NOW(3))")
        inboundSequence = 100
        record(900000, null, "SENT")
    }

    private fun record(id: Long, attempt: Long?, state: String) {
        jdbc.update("""INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,
            triggered_by,message_id,subject,body,send_status,sent_at,created_at,mail_send_attempt_id)
            VALUES(?,1,'OUTBOUND','MANUAL_RICH_REPLY','sender','MANUAL',?,'History','History body',?,
            IF(?='SENT','2026-11-05 00:00:00',NULL),'2026-11-05 00:00:00',?)""",
            id, "<record-$id@test>", state, state, attempt)
    }

    private fun snapshot(text: String, account: String = "sender", attachments: List<OutboundAttachmentSnapshot> = emptyList()): DelayedManualReplySnapshot {
        val inbound = inboundSequence++
        jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,message_id,
            from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id)
            VALUES(?,?,'INBOX',1,?,?,'expert1@example.test','Inbound','Question','Question','2026-11-05 00:00:00','MANUAL_REVIEW','QA_NO_MATCH',1)""",
            inbound, account, inbound, "<in-$inbound@test>")
        val base = DelayedWorkerFixture.snapshot(null)
        return base.copy(payload = base.payload.copy(normalizedRecipient = "expert1@example.test", inboundProcessingId = inbound,
            accountCode = account, subject = text, finalText = text, finalHtml = "<p>$text</p>",
            inReplyTo = "<in-$inbound@test>", outboundAttachments = attachments),
            source = base.source.copy(inboundProcessingId = inbound, accountCode = account,
                recipient = "expert1@example.test", sourceMessageId = "<in-$inbound@test>"),
            restore = (base.restore as DelayedLegacyRestore).copy(inboundProcessingId = inbound,
                subject = text, html = "<p>$text</p>", text = text, outboundAttachments = attachments))
    }

    private fun job(text: String, state: String = "WAITING", account: String = "sender",
                    attachments: List<OutboundAttachmentSnapshot> = emptyList(), actor: String = "admin"): DelayedManualReplyRow {
        val view = (lifecycle.enqueue(snapshot(text, account, attachments), actor) as DelayedEnqueueOutcome.Queued).job
        val row = delayed.findById(view.id)!!
        jdbc.update("""UPDATE delayed_manual_reply SET status=?,created_at_ms=?,dispatch_token=?,claimed_at_ms=?,
            public_error_code=? WHERE id=?""", state, EPOCH, if (state == "SENDING") UUID.randomUUID().toString() else null,
            if (state == "SENDING") EPOCH else null,
            if (state == "FAILED") "TEMPORARY_DELIVERY_FAILURE" else if (state == "UNKNOWN") "DELIVERY_UNKNOWN" else null, row.id)
        jdbc.update("UPDATE mail_send_attempt SET status=? WHERE id=?", when (state) {
            "FAILED" -> "FAILED_SAFE_TO_RETRY"; "UNKNOWN" -> "DELIVERY_UNKNOWN"; "SENDING" -> "DELIVERY_IN_PROGRESS"
            "SENT" -> "SENT"; else -> "PREPARED"
        }, row.sendAttemptId)
        return delayed.findById(row.id)!!
    }

    private fun messages(actor: String = "admin", limit: Int = 50, before: String? = null) =
        service.listMessages(1, null, before, limit, actor)

    @Test fun sixStatesMembershipAndStablePagination() {
        val rows = listOf("WAITING", "SENDING", "CANCELLED", "SENT", "FAILED", "UNKNOWN").map {
            job("needle-$it", it, if (it == "UNKNOWN") "second" else "sender")
        }
        val sent = rows.single { it.status == DelayedManualReplyStatus.SENT }
        record(sent.id, sent.sendAttemptId, "SENT")
        val collision = rows.first().id
        record(collision, null, "SENT")
        jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,
            message_id,from_email,subject,body,received_at,process_status,process_reason,expert_contact_id)
            VALUES(?,'sender','INBOX',1,?,'<collision@test>','expert1@example.test','Collision','Collision',
            '2026-11-05 00:00:00','AUTO_REPLIED','QA_MATCH',1)""", collision, 1000000 + collision)
        record(900001, null, "FAILED")
        val summary = repository.pageConversations("admin", filter(), 50, 0).single()
        assertEquals(3L, summary.sentCount)
        assertEquals(3L, summary.failedCount)
        assertEquals(2L, summary.delayedPendingCount)
        val timeline = repository.timelineMessages(1, filter(), null, 50).rows
        assertEquals(6, timeline.count { it.source == "DELAYED_MANUAL_REPLY" })
        assertEquals(setOf("MAIL_RECORD", "INBOUND_PROCESSING", "DELAYED_MANUAL_REPLY"),
            timeline.filter { it.id == collision }.map { it.source }.toSet())
        assertFalse(timeline.any { it.source == "MAIL_RECORD" && it.id == sent.id })
        assertEquals(rows.maxBy { it.id }.id, repository.latestMessageByContacts(listOf(1), accounts, null)[1]?.id)
        assertEquals(105L, repository.latestInboundByContacts(listOf(1), accounts, null)[1]?.processingId)
        assertEquals(1L, repository.countConversations("admin", filter().copy(direction = "OUTBOUND", keyword = "needle-UNKNOWN", accountCode = "second")))
        assertEquals(0L, repository.countConversations("admin", filter().copy(direction = "OUTBOUND", keyword = "needle-UNKNOWN", accountCode = "sender")))
        assertEquals(0L, repository.countConversations("admin", filter().copy(direction = "OUTBOUND", keyword = "needle-UNKNOWN", subject = "needle-FAILED")))
        assertEquals(0L, repository.countConversations("admin", filter().copy(direction = "OUTBOUND", keyword = "needle-UNKNOWN", recipientEmail = "wrong")))
        assertEquals(summary.sentCount, repository.pageConversations("admin", filter().copy(subject = "needle-SENT", startTime = LocalDateTime.parse("2026-11-05T00:00:00")), 50, 0).single().sentCount)
        val seen = mutableListOf<Pair<String, Long>>()
        var cursor: String? = null
        do {
            val page = messages(limit = 1, before = cursor)
            seen += page.items.map { it.source to it.id }
            cursor = page.nextBefore
        } while (cursor != null)
        assertEquals(timeline.map { it.source to it.id }.toSet(), seen.toSet())
        assertEquals(seen.size, seen.toSet().size)
        val old = timeline.first { it.source == "MAIL_RECORD" }
        val legacyCursor = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            listOf("1", "", old.eventAt.toString(), old.source, old.id.toString(), "1").joinToString("\u001f").toByteArray())
        assertDoesNotThrow { messages(before = legacyCursor) }
    }

    @Test fun rawContextIgnoresQueueCancelRetryButObservesRealMail() {
        val frozen = snapshot("context")
        val before = repository.reviewContext(1, accounts)
        // Golden v1 length-prefixed SHA for the original real-mail fixture, before any job exists.
        assertEquals("0ae759d97d23a9c5ea0ceb64ef5ca4d44951ffe56bc2170751150420689ded26", before)
        val queued = (lifecycle.enqueue(frozen, "admin") as DelayedEnqueueOutcome.Queued).job
        assertEquals(before, repository.reviewContext(1, accounts))
        lifecycle.cancel(queued.id, "admin", queued.revision)
        assertEquals(before, repository.reviewContext(1, accounts))
        val cancelled = delayed.findById(queued.id)!!
        lifecycle.enqueue(frozen, "admin", cancelled.revision)
        assertEquals(before, repository.reviewContext(1, accounts))
        record(900001, cancelled.sendAttemptId, "SENT")
        assertNotEquals(before, repository.reviewContext(1, accounts))
        val after = repository.reviewContext(1, accounts)
        snapshot("real inbound")
        assertNotEquals(after, repository.reviewContext(1, accounts))
    }

    private fun upload(owner: String = "admin", contact: Long = 1, text: String = "original"): OutboundAttachmentSnapshot {
        val response = attachmentService.upload(contact, owner, "file.txt", "text/plain", text.byteInputStream())
        return attachmentService.resolveForSend(contact, listOf(response.id), owner).snapshots.single()
    }
    private fun attachmentPath(id: String): Path {
        val field = OutboundAttachmentService::class.java.getDeclaredField("properties").apply { isAccessible = true }
        val target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject<OutboundAttachmentService>(attachmentService)
        val properties = field.get(target) as com.weibo.talentintroduction.config.MailAttachmentStorageProperties
        return Path.of(properties.basePath, "outbound", id)
    }
    private fun queries(): List<String> = Mockito.mockingDetails(named).invocations.mapNotNull { it.arguments.firstOrNull() as? String }
    private fun retry(id: Long) = messages().items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == id }.delayedReply!!.canRetry

    @Test fun fullIntegrityOwnershipAndReadIsolation() {
        val file = upload()
        val valid = job("valid", "FAILED", attachments = listOf(file))
        assertTrue(retry(valid.id))
        val path = attachmentPath(file.id)
        Files.writeString(path, "changed!")
        assertFalse(retry(valid.id), "equal-size changed bytes must fail SHA validation")
        Files.writeString(path, "larger-content")
        assertFalse(retry(valid.id))
        Files.delete(path)
        assertFalse(retry(valid.id))
        val other = upload("user1")
        val wrongOwner = job("wrong-owner", "FAILED", attachments = listOf(other))
        assertFalse(retry(wrongOwner.id))
        val foreign = upload(contact = 2)
        val wrongContact = job("wrong-contact", "FAILED", attachments = listOf(foreign))
        assertFalse(retry(wrongContact.id))
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE username='user1'")
        Mockito.clearInvocations(named, attachmentService)
        val read = messages("user1")
        assertTrue(read.items.all { it.outboundAttachments.isEmpty() && it.calendarAttachment == null && it.attachmentCount == 0 })
        assertTrue(read.items.mapNotNull { it.delayedReply }.all { !it.canRetry && !it.canCancel })
        assertTrue(queries().none { it.contains("j.snapshot_json") || it.contains("outbound_mail_attachment") || it.contains("mailbox_review_draft_event") })
        assertTrue(Mockito.mockingDetails(attachmentService).invocations.none { it.method.name.startsWith("readVerifiedFile") })
        jdbc.update("DELETE FROM expert_mail_read_access")
        assertThrows(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException::class.java) { messages("user1") }
    }

    @Test fun unicodeContactAnd0900SuppressionKeepMessagesReadable() =
        mixedCollationMessages("utf8mb4_unicode_ci", "utf8mb4_0900_ai_ci")

    @Test fun contact0900AndUnicodeSuppressionKeepMessagesReadable() =
        mixedCollationMessages("utf8mb4_0900_ai_ci", "utf8mb4_unicode_ci")

    private fun mixedCollationMessages(contactCollation: String, suppressionCollation: String) {
        // V1 inherits the database default; V30 explicitly selects utf8mb4 and can inherit
        // a different server default. Exercise both directions, never homogenize the tables.
        fun collation(table: String, column: String) = jdbc.queryForObject(
            """SELECT COLLATION_NAME FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?""",
            String::class.java, table, column)!!
        fun contactColumn(value: String) = jdbc.execute(
            "ALTER TABLE expert_contact MODIFY expert_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE $value NOT NULL")
        fun suppressionColumn(value: String) = jdbc.execute(
            "ALTER TABLE email_suppression MODIFY email VARCHAR(320) CHARACTER SET utf8mb4 COLLATE $value NOT NULL COMMENT '归一化邮箱(小写trim)'")
        val originalContact = collation("expert_contact", "expert_email")
        val originalSuppression = collation("email_suppression", "email")
        val file = upload("user1")
        val failed = job("mixed-collation-retry", "FAILED", attachments = listOf(file), actor = "user1")
        val waiting = job("mixed-collation-cancel", actor = "user1")
        // Keep cancellation eligibility independent of wall-clock time spent in ALTER TABLE.
        jdbc.update("UPDATE delayed_manual_reply SET due_at_ms=? WHERE id=?", delayed.currentTimeMs() + 600000, waiting.id)
        jdbc.update("UPDATE expert_contact SET expert_email='  ExPeRt1@Example.Test  ' WHERE id=1")
        jdbc.update("INSERT INTO email_suppression(email,source) VALUES('other@example.test','MANUAL')")
        val controller = MailboxConversationController(service, Mockito.mock(ExpertFollowService::class.java),
            Mockito.mock(ExpertRepliedDismissalService::class.java), Mockito.mock(PendingMailOperationService::class.java),
            Mockito.mock(MailboxSuspensionService::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(controller).build()
        try {
            contactColumn(contactCollation)
            suppressionColumn(suppressionCollation)
            assertEquals(contactCollation, collation("expert_contact", "expert_email"))
            assertEquals(suppressionCollation, collation("email_suppression", "email"))
            assertNotEquals(contactCollation, suppressionCollation)
            // The complete production message path must succeed, not a standalone SQL probe.
            mvc.perform(get("/api/mail/mailbox/conversations/1/messages").sessionAttr(AuthSessionKeys.USERNAME, "admin"))
                .andExpect(status().isOk)
            Mockito.clearInvocations(named)
            val allowed = messages()
            val queryCount = queries().size
            assertTrue(allowed.items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == failed.id }.delayedReply!!.canRetry)
            assertTrue(allowed.items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == waiting.id }.delayedReply!!.canCancel)
            assertEquals(file.id, allowed.items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == failed.id }.outboundAttachments.single().id)
            assertTrue(retryFromFullUser(failed.id))

            // Suppression is normalized like EmailSuppressionService: lowercase and trim.
            jdbc.update("INSERT INTO email_suppression(email,source) VALUES('expert1@example.test','MANUAL')")
            mvc.perform(get("/api/mail/mailbox/conversations/1/messages").sessionAttr(AuthSessionKeys.USERNAME, "admin"))
                .andExpect(status().isOk)
            Mockito.clearInvocations(named)
            val blocked = messages()
            assertEquals(queryCount, queries().size)
            assertFalse(blocked.items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == failed.id }.delayedReply!!.canRetry)
            assertFalse(blocked.items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == waiting.id }.delayedReply!!.canCancel)
            assertFalse(retryFromFullUser(failed.id))

            jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE username='user1'")
            Mockito.clearInvocations(named, attachmentService)
            val read = messages("user1")
            assertEquals(setOf(failed.id, waiting.id), read.items.filter { it.source == "DELAYED_MANUAL_REPLY" }.map { it.id }.toSet())
            assertTrue(read.items.all { it.outboundAttachments.isEmpty() && it.calendarAttachment == null && it.attachmentCount == 0 })
            assertTrue(read.items.mapNotNull { it.delayedReply }.all { !it.canRetry && !it.canCancel })
            assertTrue(queries().none { it.contains("j.snapshot_json") || it.contains("email_suppression") ||
                it.contains("outbound_mail_attachment") || it.contains("mailbox_review_draft_event") })
            assertTrue(Mockito.mockingDetails(attachmentService).invocations.none { it.method.name.startsWith("readVerifiedFile") })
            jdbc.update("DELETE FROM expert_mail_read_access")
            assertThrows(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException::class.java) { messages("user1") }
        } finally {
            // DDL commits independently of transactions. Restore both columns even on the
            // expected pre-repair 1267 failure so other QueryIT methods retain their schema.
            try { suppressionColumn(originalSuppression) } finally { contactColumn(originalContact) }
        }
    }

    private fun retryFromFullUser(id: Long) =
        messages("user1").items.single { it.source == "DELAYED_MANUAL_REPLY" && it.id == id }.delayedReply!!.canRetry

    @Test fun pageQueriesDoNotGrowAndLimitsRemainPerJob() {
        val files = (1..MAX_FILES).map { upload(text = it.toString().repeat(1024 * 1024)) }
        val first = job("batch-first", "FAILED", attachments = files)
        Mockito.clearInvocations(named)
        assertTrue(retry(first.id))
        val initial = queries().size
        repeat(5) { job("batch-$it", "FAILED", attachments = files) }
        Mockito.clearInvocations(named)
        val page = messages()
        assertTrue(page.items.mapNotNull { it.delayedReply }.all { it.canRetry })
        assertEquals(initial, queries().size)
        assertTrue(delayed.pageRows(2, accounts, listOf(first.id), true).isEmpty())
        assertTrue(delayed.pageRows(1, listOf("second"), listOf(first.id), true).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { delayed.pageRows(1, accounts, (1L..101L).toList(), true) }
        Mockito.clearInvocations(named)
        assertTrue(delayed.pageRows(1, accounts, emptyList(), true).isEmpty())
        assertTrue(draftRepository.findForDelayedPage(1, accounts, emptyList()).isEmpty())
        assertTrue(reviewRepository.findForDelayedPage(1, accounts, emptyList()).isEmpty())
        assertTrue(queries().isEmpty())
    }

    @Test fun canonicalMixedSourcesStayBoundedAndRevalidateCurrentContext() {
        val draftBase = snapshot("draft-source")
        val reviewBase = snapshot("review-source")
        val saved = drafts.save("user1", MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND,
            draftBase.payload.inboundProcessingId!!, "sender"),
            MailReplyDraftSaveRequest(0, MailReplyDraftContent("draft-source", "<p>draft-source</p>", "draft-source")))
        val draftFrozen = draftBase.copy(restore = DelayedDraftRestore("user1", saved.id!!, saved.version, 1,
            "INBOUND", requireNotNull(draftBase.payload.inboundProcessingId), "sender", "draft-source", "<p>draft-source</p>",
            "draft-source", MailReplyDraftContextCodec.serialize(saved.content!!.context), null, null))
        val token = repository.reviewContext(1, accounts)
        val request = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO mailbox_review_draft(id,owner_username,expert_contact_id,client_request_id,
            target_kind,target_id,account_code,version,state,subject,html_body,text_body,submitted_at,
            reviewed_by,reviewed_at,approved_context_token,send_request_id,created_at,updated_at)
            VALUES(990,'user1',1,?,'INBOUND',?,'sender',1,'APPROVED','review-source','<p>review-source</p>',
            'review-source',NOW(),'admin',NOW(),?,?,NOW(),NOW())""",
            UUID.randomUUID().toString(), reviewBase.payload.inboundProcessingId, token, request)
        val reviewFrozen = reviewBase.copy(restore = DelayedReviewRestore(990, 1, "admin", null,
            "review-source", "<p>review-source</p>", "review-source", "sender", 1, "INBOUND",
            reviewBase.payload.inboundProcessingId!!, request, token))
        val d = (lifecycle.enqueue(draftFrozen, "user1") as DelayedEnqueueOutcome.Queued).job
        val r = (lifecycle.enqueue(reviewFrozen, "admin") as DelayedEnqueueOutcome.Queued).job
        for (id in listOf(d.id, r.id)) {
            jdbc.update("UPDATE delayed_manual_reply SET status='FAILED',public_error_code='TEMPORARY_DELIVERY_FAILURE' WHERE id=?", id)
            jdbc.update("UPDATE mail_send_attempt SET status='FAILED_SAFE_TO_RETRY' WHERE id=(SELECT send_attempt_id FROM delayed_manual_reply WHERE id=?)", id)
        }
        assertTrue(retry(d.id))
        assertTrue(retry(r.id))
        assertEquals(listOf(saved.id), draftRepository.findForDelayedPage(1, accounts, listOf(d.id, r.id)).map { it.id })
        assertEquals(listOf(990L), reviewRepository.findForDelayedPage(1, accounts, listOf(d.id, r.id)).map { it.id })
        assertTrue(draftRepository.findForDelayedPage(2, accounts, listOf(d.id)).isEmpty())
        assertTrue(reviewRepository.findForDelayedPage(1, listOf("second"), listOf(r.id)).isEmpty())
        assertTrue(draftRepository.findForDelayedPage(1, accounts, listOf(r.id)).isEmpty())
        assertTrue(reviewRepository.findForDelayedPage(1, accounts, listOf(d.id)).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { reviewRepository.findForDelayedPage(1, accounts, (1L..101L).toList()) }
        snapshot("new-real-inbound")
        assertFalse(retry(r.id))
        assertTrue(retry(d.id))
        jdbc.update("UPDATE mailbox_reply_draft SET text_body='changed' WHERE id=?", saved.id)
        assertFalse(retry(d.id))
    }

    @Test fun sentAttachmentsUseRealRecordAndHistoricalFailureHasNoJob() {
        val file = upload()
        val row = job("sent-file", "SENT", attachments = listOf(file))
        record(700000, row.sendAttemptId, "SENT")
        jdbc.update("UPDATE mail_record SET outbound_attachments_json=? WHERE id=700000", OutboundAttachmentSnapshotCodec.serialize(listOf(file)))
        record(row.id, null, "FAILED")
        val page = messages()
        val card = page.items.single { it.source == "DELAYED_MANUAL_REPLY" }
        assertEquals(700000L, card.delayedReply!!.sentMailRecordId)
        assertTrue(card.outboundAttachments.single().downloadUrl.contains("/messages/700000/"))
        assertNull(page.items.single { it.source == "MAIL_RECORD" && it.id == row.id }.delayedReply)
    }

    @Test fun utcAndShanghaiSessionsPreserveEpochAndRank() {
        val row = job("timezone")
        for ((zone, hour) in listOf("+00:00" to 0, "Asia/Shanghai" to 8)) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { it.execute("SET time_zone='$zone'") }
                val scoped = MailboxConversationRepository(NamedParameterJdbcTemplate(SingleConnectionDataSource(connection, true)))
                val event = scoped.timelineMessages(1, filter(), null, 50).rows.single { it.source == "DELAYED_MANUAL_REPLY" }
                assertEquals(hour, event.eventAt.hour)
                assertEquals(row.id, event.id)
                assertEquals(EPOCH, delayed.findById(row.id)!!.createdAtMs)
                connection.createStatement().use { it.execute("SET time_zone='+00:00'") }
            }
        }
    }

    /** Explicit HTTP query smoke; not browser evidence and not production SMTP evidence. */
    @Test fun runtimeApiSmoke() {
        val file = upload()
        val failed = job("api-retry", "FAILED", attachments = listOf(file))
        job("api-wait")
        job("api-cancel", "CANCELLED")
        job("api-unknown", "UNKNOWN", "second")
        job("api-sending", "SENDING")
        val sent = job("api-sent", "SENT", attachments = listOf(file))
        record(700000, sent.sendAttemptId, "SENT")
        jdbc.update("UPDATE mail_record SET outbound_attachments_json=? WHERE id=700000", OutboundAttachmentSnapshotCodec.serialize(listOf(file)))
        val controller = MailboxConversationController(service, Mockito.mock(ExpertFollowService::class.java),
            Mockito.mock(ExpertRepliedDismissalService::class.java), Mockito.mock(PendingMailOperationService::class.java),
            Mockito.mock(MailboxSuspensionService::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(controller).build()
        val full = mvc.perform(get("/api/mail/mailbox/conversations/1/messages").sessionAttr(AuthSessionKeys.USERNAME, "admin"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(full.contains("DELAYED_MANUAL_REPLY") && full.contains("/messages/700000/"))
        assertTrue(retry(failed.id))
        lifecycle.retry(failed.id, "admin", failed.revision)
        jdbc.update("UPDATE delayed_manual_reply SET due_at_ms=? WHERE id=?", delayed.currentTimeMs() - 1, failed.id)
        val sender = Mockito.mock(MailSenderAccountService::class.java)
        val senderRows = context.getBean(MailSenderAccountRepository::class.java)
        for (code in accounts) {
            val account = senderRows.findByAccountCode(code)
            Mockito.`when`(sender.getManualSendAccount(code)).thenReturn(account)
        }
        val delivery = Mockito.mock(MailDeliveryService::class.java) { invocation ->
            if (invocation.method.name == "send")
                DeliveredMail((invocation.arguments[1] as ComposedMail).messageId, "SENT")
            else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val constructor = PendingMailOperationService::class.java.declaredConstructors.single {
            it.parameterTypes.last().name != "kotlin.jvm.internal.DefaultConstructorMarker"
        }
        val overrides = mapOf<Class<*>, Any>(MailSenderAccountService::class.java to sender,
            MailDeliveryService::class.java to delivery)
        val args = constructor.parameterTypes.map { type ->
            overrides[type] ?: context.getBeansOfType(type).values.firstOrNull() ?: Mockito.mock(type)
        }
        val pending = constructor.newInstance(*args.toTypedArray()) as PendingMailOperationService
        val worker = DelayedManualReplyWorker(delayed, lifecycle, pending, attempts, transactions)
        try { worker.dispatchDue() } finally { worker.close() }
        assertEquals(DelayedManualReplyStatus.SENT, delayed.findById(failed.id)!!.status)
        val after = messages().items.filter { it.source == "DELAYED_MANUAL_REPLY" && it.id == failed.id }
        assertEquals(1, after.size)
        assertEquals(failed.createdAtMs, after.single().delayedReply!!.createdAtMs)
        val integrity = job("api-integrity", "FAILED", attachments = listOf(file))
        Files.delete(attachmentPath(file.id))
        assertFalse(retry(integrity.id))
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE username='user1'")
        val read = mvc.perform(get("/api/mail/mailbox/conversations/1/messages").sessionAttr(AuthSessionKeys.USERNAME, "user1"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertFalse(read.contains("/outbound-attachments/"))
        assertFalse(read.contains("\"canRetry\":true") || read.contains("\"canCancel\":true"))
        println("QUERY_API_SMOKE isolated=true HTTP_FULL=200 HTTP_READ=200 states=6 workerRetry=SENT stableJobId=${failed.id} actualRecordId=700000 fileDeletionClosesRetry=true browser=false smtpBoundary=stub")
    }
}
