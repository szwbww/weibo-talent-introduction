package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.campaign.domain.MailSendAttemptStatus
import com.weibo.talentintroduction.campaign.domain.MeetingCalendarInput
import com.weibo.talentintroduction.mail.repository.DelayedManualReplyRepository
import com.weibo.talentintroduction.mail.repository.MailReplyDraftRepository
import com.weibo.talentintroduction.mail.repository.MailReviewDraftRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * fast-p 03 验收：真实 MySQL 事务上的排队/撤回/重排/重试/领取/完成/恢复状态机。
 *
 * 全部使用生产 service 与仓储（只有 SMTP/翻译等外部协作者是替身）；不使用
 * `@Transactional` 测试包装，也不 sleep 模拟 60 秒 —— 到期由数据库时间列直接满足。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@SpringBootTest(classes = [DelayedManualReplyLifecycleIT.Config::class], properties = [
    "spring.flyway.placeholder-replacement=false", "talent-introduction.scheduling.enabled=false",
    "talent-introduction.mail-queue.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"
])
class DelayedManualReplyLifecycleIT {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration
    @EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.mail.repository",
        "com.weibo.talentintroduction.campaign.repository"])
    @Import(com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository::class,
        MailReplyDraftService::class, MailReplyDraftRepository::class, MailReviewDraftService::class,
        MailReviewDraftRepository::class, MailboxConversationRepository::class, MailContentService::class,
        MailPlaceholderService::class, ManualReplySendAttemptService::class,
        DelayedManualReplyRepository::class, DelayedManualReplyService::class,
        com.weibo.talentintroduction.config.ManualReplyDelayConfiguration::class,
        MeetingCalendarSendIntegrationTestSupport::class)
    class Config

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("delayed_reply").withUsername("test").withPassword("test")
            .withCommand("mysqld", "--max-allowed-packet=16777216", "--log-bin-trust-function-creators=1")
        @JvmStatic @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }

        private const val OWNER = "user1"
        private const val ADMIN = "admin"
        private const val ACCOUNT = "delayed"
    }

    @Autowired private lateinit var delays: DelayedManualReplyService
    @Autowired private lateinit var attempts: ManualReplySendAttemptService
    @Autowired private lateinit var drafts: MailReplyDraftService
    @Autowired private lateinit var reviews: MailReviewDraftService
    @Autowired private lateinit var jdbc: JdbcTemplate
    @MockBean private lateinit var translation: MailTranslationService

    private val threadPool = Executors.newFixedThreadPool(4)

    @BeforeEach
    fun seed() {
        listOf("delayed_manual_reply", "mailbox_review_draft_event", "mailbox_review_draft", "mailbox_reply_draft",
            "expert_mail_read_access", "meeting_calendar_event", "mail_record_rag_fact", "mail_record_qa_rule",
            "mail_record", "mail_send_attempt", "outbound_mail_attachment", "inbound_mail_processing",
            "expert_contact", "campaign", "mail_sender_account").forEach { jdbc.update("DELETE FROM $it") }
        jdbc.update("""INSERT INTO mail_sender_account(account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
            VALUES(?,'delayed@example.test','Delayed','smtp.fixture',465,'delayed','pw','imap.fixture',993,'delayed','pw')""", ACCOUNT)
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'DELAYED','Delayed',(SELECT id FROM mail_sender_account WHERE account_code='delayed'))")
        (1L..2L).forEach {
            jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(?,1,?,?,?,'WAITING_REPLY')",
                it, "delayed-$it", "expert$it@example.test", "Expert $it")
        }
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,access_level,granted_by,created_at) VALUES('user1',1,'FULL','admin',NOW(3))")
        inbound(100)
        jdbc.update("""INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,triggered_by,message_id,subject,body,send_status,sent_at,created_at)
            VALUES(10,1,'OUTBOUND','INTRODUCTION','delayed','SYSTEM','<anchor@test>','Introduction','Introduction body','SENT',NOW(3),NOW(3))""")
    }

    private fun inbound(id: Long) {
        jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id)
            VALUES(?,?,'INBOX',1,?,?, 'expert1@example.test','Inbound','Inbound body','Inbound clean',NOW(3),'MANUAL_REVIEW','QA_NO_MATCH',1)""",
            id, ACCOUNT, id, "in-$id")
    }

    // ------------------------------------------------------------------ fixtures

    private fun saveDraft(text: String = "Hello", version: Long = 0) =
        drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
            MailReplyDraftSaveRequest(version, MailReplyDraftContent("Re: Test", "<p>$text</p>", text)))

    private fun draftSnapshot(draft: MailReplyDraftDetail, text: String = "Hello"): DelayedManualReplySnapshot =
        snapshot("Re: Test", "<p>$text</p>", text, DelayedDraftRestore(
            owner = OWNER, draftId = requireNotNull(draft.id), draftVersion = draft.version, contactId = 1,
            targetKind = MailReplyDraftKind.INBOUND.name, processingId = requireNotNull(draft.target).processingId,
            accountScope = ACCOUNT, subject = "Re: Test", html = "<p>$text</p>", text = text,
            contextJson = MailReplyDraftContextCodec.serialize(requireNotNull(draft.content).context),
            sendAttemptId = null, sendVersion = null
        ))

    private fun legacySnapshot(text: String): DelayedManualReplySnapshot =
        snapshot("Re: Test", "<p>$text</p>", text, DelayedLegacyRestore(
            inboundProcessingId = 100, subject = "Re: Test", html = "<p>$text</p>", text = text,
            qaRuleIds = emptyList(), ragFactCodes = emptyList(), assembly = null, templateTextBody = null,
            calendarAttachment = null, meetingEvent = null, outboundAttachments = emptyList()
        ))

    private fun snapshot(
        subject: String,
        html: String,
        text: String,
        restore: DelayedReplyRestore,
        operator: String = OWNER,
        payloadOverrides: DelayedSendPayloadSnapshot? = null
    ): DelayedManualReplySnapshot = DelayedManualReplySnapshot(
        schemaVersion = DELAYED_MANUAL_REPLY_SCHEMA_VERSION,
        payload = payloadOverrides ?: DelayedSendPayloadSnapshot(
            orcidId = "delayed-1", contactId = 1, inboundProcessingId = 100, accountCode = ACCOUNT,
            normalizedRecipient = "expert1@example.test", subject = subject, finalText = text, finalHtml = html,
            inReplyTo = null, canonicalQaRuleIds = emptyList(), primaryRuleId = null, sourceAnchor = null,
            idempotencyRequestId = null, calendarAttachment = null, meetingEvent = null, outboundAttachments = emptyList()
        ),
        source = DelayedReplySourceSnapshot(
            smtpInReplyTo = null, smtpReferences = null, anchorMailRecordId = null, sourceMessageId = "<in-100>",
            inboundProcessingId = 100, accountCode = ACCOUNT, recipient = "expert1@example.test"
        ),
        restore = restore,
        evidence = DelayedReplyEvidenceSnapshot(
            carriesQa = false, canonicalFactIds = emptyList(), serverSuggestedFactIds = emptyList(), edited = false,
            ragFactCodes = emptyList(), ragCorpusFingerprint = "", assembly = null, templateTextBody = null,
            auditNote = null, bodyPreviewText = null, operator = operator
        ),
        confirmation = DelayedReplyConfirmationSnapshot(
            safetyWarningConfirmed = true, strongConfirmationRequired = false, strongConfirmationText = null,
            verifiedContentSha256 = "a".repeat(64)
        )
    )

    private fun payloadOf(snapshot: DelayedManualReplySnapshot): ManualReplySendAttemptService.SendPayload =
        ManualReplySendAttemptService.SendPayload(
            orcidId = snapshot.payload.orcidId, contactId = snapshot.payload.contactId,
            inboundProcessingId = snapshot.payload.inboundProcessingId, accountCode = snapshot.payload.accountCode,
            normalizedRecipient = snapshot.payload.normalizedRecipient, subject = snapshot.payload.subject,
            finalText = snapshot.payload.finalText, finalHtml = snapshot.payload.finalHtml,
            inReplyTo = snapshot.payload.inReplyTo, canonicalQaRuleIds = snapshot.payload.canonicalQaRuleIds,
            primaryRuleId = snapshot.payload.primaryRuleId, sourceAnchor = snapshot.payload.sourceAnchor,
            idempotencyRequestId = snapshot.payload.idempotencyRequestId,
            calendarAttachment = snapshot.payload.calendarAttachment,
            meetingEvent = snapshot.payload.meetingEvent?.let {
                MeetingCalendarInput(Instant.ofEpochMilli(it.startUtcMs), Instant.ofEpochMilli(it.endUtcMs), it.meetingLink)
            },
            outboundAttachments = snapshot.payload.outboundAttachments
        )

    private fun queued(outcome: DelayedEnqueueOutcome): DelayedManualReplyView =
        (outcome as? DelayedEnqueueOutcome.Queued)?.job ?: throw AssertionError("Expected queued job, got $outcome")

    private fun job(id: Long): Map<String, Any?> = jdbc.queryForMap("SELECT * FROM delayed_manual_reply WHERE id=?", id)

    private fun messageIdOf(attemptId: Long): String =
        jdbc.queryForObject("SELECT message_id FROM mail_send_attempt WHERE id=?", String::class.java, attemptId)!!

    private fun number(row: Map<String, Any?>, column: String) = (row[column] as Number).toLong()

    private fun count(table: String, condition: String = "1=1") =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $condition", Long::class.java)!!

    private fun failure(status: Int, code: String? = null, block: () -> Unit): DelayedManualReplyException {
        val ex = assertThrows(DelayedManualReplyException::class.java, block)
        assertEquals(status, ex.status)
        if (code != null) assertEquals(code, ex.code)
        return ex
    }

    private fun <T> race(first: Callable<T>, second: Callable<T>): Pair<Result<T>, Result<T>> {
        val barrier = CountDownLatch(2)
        val a = threadPool.submit<T> { barrier.countDown(); barrier.await(15, TimeUnit.SECONDS); first.call() }
        val b = threadPool.submit<T> { barrier.countDown(); barrier.await(15, TimeUnit.SECONDS); second.call() }
        return await(a) to await(b)
    }

    /** future.get 的 ExecutionException 必须解包成真实业务异常，断言才能看到真实失败原因。 */
    private fun <T> await(future: java.util.concurrent.Future<T>): Result<T> = try {
        Result.success(future.get(30, TimeUnit.SECONDS))
    } catch (ex: java.util.concurrent.ExecutionException) {
        Result.failure(ex.cause ?: ex)
    } catch (ex: Exception) {
        Result.failure(ex)
    }

    /** 数据库时钟下的到期：due 取允许的最小值 created_at，领取无需任何 sleep。 */
    private fun makeDuePast(id: Long) {
        jdbc.update("UPDATE delayed_manual_reply SET due_at_ms=created_at_ms WHERE id=?", id)
    }

    /** 撤回/领取竞争：due 紧贴 created_at+150ms，二者真正竞争同一 revision CAS。 */
    private fun armDueBoundary(id: Long) {
        jdbc.update("UPDATE delayed_manual_reply SET due_at_ms=created_at_ms+150 WHERE id=?", id)
    }

    private fun approvedReview(): MailReviewDraftRow {
        val created = reviews.create(OWNER, 1, UUID.randomUUID().toString(), "Subject", "<p>Body</p>") as MailReviewDraftDetail
        val submitted = reviews.submit(OWNER, created.id, created.version) as MailReviewDraftDetail
        val token = reviews.context(ADMIN, created.id).currentToken
        reviews.approve(ADMIN, created.id, submitted.version, token)
        return reviewRow(created.id)
    }

    /** 直接读行（不经过需要事务的 service 锁），测试断言用。 */
    private fun reviewRow(id: Long): MailReviewDraftRow {
        val row = jdbc.queryForMap("SELECT * FROM mailbox_review_draft WHERE id=?", id)
        fun time(column: String) = when (val value = row[column]) {
            is java.sql.Timestamp -> value.toLocalDateTime()
            is java.time.LocalDateTime -> value
            else -> null
        }
        return MailReviewDraftRow(
            id = id,
            ownerUsername = row["owner_username"] as String,
            contactId = number(row, "expert_contact_id"),
            clientRequestId = row["client_request_id"] as String,
            targetKind = row["target_kind"] as String,
            targetId = number(row, "target_id"),
            accountCode = row["account_code"] as String,
            version = number(row, "version"),
            state = MailReviewDraftState.valueOf(row["state"] as String),
            subject = row["subject"] as String,
            html = row["html_body"] as String,
            text = row["text_body"] as String,
            submittedAt = time("submitted_at"),
            returnReason = row["return_reason"] as String?,
            reviewedBy = row["reviewed_by"] as String?,
            reviewedAt = time("reviewed_at"),
            approvedContextToken = row["approved_context_token"] as String?,
            sendRequestId = row["send_request_id"] as String?,
            sendAttemptId = (row["send_attempt_id"] as? Number)?.toLong(),
            deletedAt = time("deleted_at"),
            createdAt = requireNotNull(time("created_at")),
            updatedAt = requireNotNull(time("updated_at"))
        )
    }

    private fun reviewSnapshot(row: MailReviewDraftRow): DelayedManualReplySnapshot {
        val inboundId = if (row.targetKind == "INBOUND") row.targetId else null
        val anchor = if (row.targetKind == "OUTBOUND") "MAIL_RECORD:${row.targetId}" else null
        val payload = DelayedSendPayloadSnapshot(
            orcidId = "delayed-1", contactId = row.contactId, inboundProcessingId = inboundId,
            accountCode = row.accountCode, normalizedRecipient = "expert1@example.test",
            subject = row.subject, finalText = row.text, finalHtml = row.html, inReplyTo = null,
            canonicalQaRuleIds = emptyList(), primaryRuleId = null, sourceAnchor = anchor,
            idempotencyRequestId = if (inboundId == null) row.sendRequestId else null,
            calendarAttachment = null, meetingEvent = null, outboundAttachments = emptyList()
        )
        return DelayedManualReplySnapshot(
            schemaVersion = DELAYED_MANUAL_REPLY_SCHEMA_VERSION,
            payload = payload,
            source = DelayedReplySourceSnapshot(
                smtpInReplyTo = null, smtpReferences = null,
                anchorMailRecordId = if (inboundId == null) row.targetId else null,
                sourceMessageId = "<in-$inboundId>", inboundProcessingId = inboundId,
                accountCode = row.accountCode, recipient = "expert1@example.test"
            ),
            restore = DelayedReviewRestore(
                reviewId = row.id, version = row.version, actor = ADMIN, confirmationEventId = null,
                subject = row.subject, html = row.html, text = row.text, accountCode = row.accountCode,
                contactId = row.contactId, targetKind = row.targetKind, targetId = row.targetId,
                sendRequestId = requireNotNull(row.sendRequestId),
                approvedContextToken = requireNotNull(row.approvedContextToken)
            ),
            evidence = DelayedReplyEvidenceSnapshot(
                carriesQa = false, canonicalFactIds = emptyList(), serverSuggestedFactIds = emptyList(), edited = false,
                ragFactCodes = emptyList(), ragCorpusFingerprint = "", assembly = null, templateTextBody = null,
                auditNote = null, bodyPreviewText = null, operator = ADMIN
            ),
            confirmation = DelayedReplyConfirmationSnapshot(
                safetyWarningConfirmed = true, strongConfirmationRequired = false, strongConfirmationText = null,
                verifiedContentSha256 = "b".repeat(64)
            )
        )
    }

    // ------------------------------------------------------------------ I-1 期限

    @Test
    fun `repeated enqueue keeps the original due and never claims the attempt`() {
        val draft = saveDraft()
        val snapshot = draftSnapshot(draft)
        val first = queued(delays.enqueue(snapshot, OWNER))
        val row = job(first.id)
        assertEquals("WAITING", row["status"])
        assertEquals(1L, number(row, "revision"))
        assertEquals(number(row, "created_at_ms") + DELAYED_MANUAL_REPLY_DELAY_MS, number(row, "due_at_ms"))
        assertEquals(1L, number(row, "origin_version"))
        assertEquals(OWNER, row["actor_username"])

        Thread.sleep(20)
        val second = queued(delays.enqueue(snapshot, OWNER))
        assertEquals(first.id, second.id)
        assertEquals(first.dueAtMs, second.dueAtMs)
        assertEquals(1L, second.revision)
        assertEquals(number(row, "due_at_ms"), number(job(first.id), "due_at_ms"))
        assertEquals(1L, count("delayed_manual_reply"))
        assertEquals(1L, count("mail_send_attempt", "status='PREPARED'"))
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    // ------------------------------------------------------------------ I-6 冻结

    @Test
    fun `waiting job freezes every draft write and cancel keeps body context and binding rules`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        val before = jdbc.queryForMap("SELECT * FROM mailbox_reply_draft WHERE id=?", draft.id)

        val saveError = assertThrows(MailReplyDraftException::class.java) {
            drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
                MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Other</p>", "Other")))
        }
        assertEquals("DRAFT_SEND_QUEUED", saveError.code)
        assertEquals("DRAFT_SEND_QUEUED", assertThrows(MailReplyDraftException::class.java) {
            drafts.discard(OWNER, requireNotNull(draft.id), 1)
        }.code)
        assertEquals(MailReplyDraftState.ACTIVE, drafts.get(OWNER, requireNotNull(draft.id)).state)

        val cancelled = delays.cancel(view.id, OWNER, view.revision)
        assertEquals(DelayedManualReplyStatus.CANCELLED, cancelled.status)
        val after = jdbc.queryForMap("SELECT * FROM mailbox_reply_draft WHERE id=?", draft.id)
        assertEquals(before["subject"], after["subject"])
        assertEquals(before["html_body"], after["html_body"])
        assertEquals(before["text_body"], after["text_body"])
        assertEquals(before["context_json"], after["context_json"])
        assertNull(after["send_attempt_id"])
        assertNull(after["send_version"])
        assertEquals(MailReplyDraftState.ACTIVE, drafts.get(OWNER, requireNotNull(draft.id)).state)
        assertEquals("PREPARED", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))

        // Idempotent repeat reads CANCELLED back without a new revision.
        assertEquals(cancelled.revision, delays.cancel(view.id, OWNER, view.revision).revision)
        // The released draft is editable again.
        assertEquals(2L, drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
            MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Hello</p>", "Hello"))).version)
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    // ------------------------------------------------------------------ I-1/I-3 重排

    @Test
    fun `cancelled requeue needs the current revision and gains a full new delay`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        val cancelled = delays.cancel(view.id, OWNER, view.revision)
        val firstDue = cancelled.dueAtMs

        failure(409, "DELAYED_REPLY_REVISION_CONFLICT") { delays.enqueue(draftSnapshot(draft), OWNER, null) }
        failure(409, "DELAYED_REPLY_REVISION_CONFLICT") { delays.enqueue(draftSnapshot(draft), OWNER, view.revision) }

        val sameContent = drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
            MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Hello</p>", "Hello")))
        val requeued = queued(delays.enqueue(draftSnapshot(sameContent), OWNER, cancelled.revision))
        assertEquals(DelayedManualReplyStatus.WAITING, requeued.status)
        assertEquals(cancelled.revision + 1, requeued.revision)
        assertTrue(requeued.dueAtMs > firstDue, "requeue must gain a fresh delay")
        assertEquals(sameContent.version, number(job(requeued.id), "origin_version"))
        assertEquals(1L, count("delayed_manual_reply"))
        assertEquals(1L, count("mail_send_attempt"))
        val rebound = drafts.get(OWNER, requireNotNull(sameContent.id))
        assertEquals(sameContent.version, rebound.sendVersion)
        assertEquals(number(job(requeued.id), "send_attempt_id"), rebound.sendAttemptId)
    }

    @Test
    fun `changed draft after cancel starts a new attempt and leaves the old job cancelled`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        delays.cancel(view.id, OWNER, view.revision)
        val edited = drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
            MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Changed</p>", "Changed")))
        val fresh = queued(delays.enqueue(draftSnapshot(edited, "Changed"), OWNER))
        assertNotEquals(view.id, fresh.id)
        assertEquals(DelayedManualReplyStatus.WAITING, fresh.status)
        assertEquals("CANCELLED", job(view.id)["status"])
        assertEquals(2L, count("delayed_manual_reply"))
        assertEquals(2L, count("mail_send_attempt"))
    }

    @Test
    fun `stale revision cannot cancel a later waiting round`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        val cancelled = delays.cancel(view.id, OWNER, view.revision)
        val requeued = queued(delays.enqueue(draftSnapshot(draft), OWNER, cancelled.revision))
        failure(409, "DELAYED_REPLY_REVISION_CONFLICT") { delays.cancel(requeued.id, OWNER, view.revision) }
        assertEquals("WAITING", job(requeued.id)["status"])
    }

    // ------------------------------------------------------------------ I-2/I-7 权限

    @Test
    fun `cancel and retry are limited to the original actor and admin`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        failure(403) { delays.cancel(view.id, "other", view.revision) }
        assertEquals("WAITING", job(view.id)["status"])
        assertEquals(DelayedManualReplyStatus.CANCELLED, delays.cancel(view.id, ADMIN, view.revision).status)
    }

    @Test
    fun `user1 without FULL cannot enqueue or cancel a normal reply`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        assertThrows(MailReadAccessDeniedException::class.java) { delays.cancel(view.id, OWNER, view.revision) }
        assertThrows(MailReadAccessDeniedException::class.java) { delays.enqueue(draftSnapshot(draft), OWNER) }
        assertEquals("WAITING", job(view.id)["status"])
    }

    // ------------------------------------------------------------------ I-3 同步绕行

    @Test
    fun `legacy synchronous claim never steals a queued attempt`() {
        val snapshot = legacySnapshot("Hello")
        val view = queued(delays.enqueue(snapshot, OWNER))
        val attemptId = number(job(view.id), "send_attempt_id")
        val claim = attempts.prepareAndClaim(payloadOf(snapshot))
        assertEquals(attemptId, claim.attemptId)
        assertEquals(ManualReplySendAttemptService.ClaimResult.IN_PROGRESS, claim.result)
        assertEquals("PREPARED", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
        assertEquals("WAITING", job(view.id)["status"])
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test
    fun `same legacy source cannot be re-queued with a different fingerprint`() {
        queued(delays.enqueue(legacySnapshot("Hello"), OWNER))
        failure(409, "DELAYED_REPLY_SOURCE_CONFLICT") { delays.enqueue(legacySnapshot("Changed"), OWNER) }
        assertEquals(1L, count("delayed_manual_reply"))
        assertEquals(1L, count("mail_send_attempt"))
    }

    // ------------------------------------------------------------------ I-2 并发

    @Test
    fun `concurrent cancel and claim have exactly one winner`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        armDueBoundary(view.id)
        val (cancelResult, claimResult) = race(
            Callable { delays.cancel(view.id, OWNER, view.revision) },
            Callable { delays.claim(view.id) }
        )
        val winners = listOf(cancelResult, claimResult).count { it.isSuccess && it.getOrNull() != null }
        assertEquals(1, winners, "exactly one CAS must win: $cancelResult / $claimResult")
        val row = job(view.id)
        val status = row["status"]
        assertTrue(status == "CANCELLED" || status == "SENDING", "unexpected status $status")
        val attempt = jdbc.queryForMap("SELECT * FROM mail_send_attempt")
        val binding = jdbc.queryForMap("SELECT send_attempt_id FROM mailbox_reply_draft WHERE id=?", draft.id)
        if (status == "CANCELLED") {
            assertEquals("PREPARED", attempt["status"])
            assertNull(binding["send_attempt_id"])
        } else {
            assertEquals("DELIVERY_IN_PROGRESS", attempt["status"])
            assertEquals(attempt["id"], binding["send_attempt_id"])
            assertNotNull(row["dispatch_token"])
        }
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test
    fun `concurrent autosave never overwrites a waiting reply or a claimed round`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        makeDuePast(view.id)
        val (saveResult, claimResult) = race(
            Callable {
                drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
                    MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Autosave</p>", "Autosave")))
            },
            Callable { delays.claim(view.id) }
        )
        assertTrue(saveResult.isFailure, "autosave must be rejected while the reply is queued: $saveResult")
        assertTrue((saveResult.exceptionOrNull() as MailReplyDraftException).code in setOf("DRAFT_SEND_QUEUED", "DRAFT_SEND_IN_PROGRESS"))
        assertNotNull(claimResult.getOrNull())
        assertEquals("SENDING", job(view.id)["status"])
        assertEquals("Hello", jdbc.queryForObject("SELECT text_body FROM mailbox_reply_draft WHERE id=?", String::class.java, draft.id))
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM mailbox_reply_draft WHERE id=?", Long::class.java, draft.id))
    }

    @Test
    fun `concurrent discard and enqueue never leave a half binding`() {
        val draft = saveDraft()
        val snapshot = draftSnapshot(draft)
        val (discardResult, enqueueResult) = race(
            Callable { drafts.discard(OWNER, requireNotNull(draft.id), 1) },
            Callable { delays.enqueue(snapshot, OWNER) }
        )
        val jobs = count("delayed_manual_reply")
        val state = jdbc.queryForObject("SELECT state FROM mailbox_reply_draft WHERE id=?", String::class.java, draft.id)
        if (enqueueResult.isSuccess) {
            assertEquals(1L, jobs)
            assertEquals("ACTIVE", state)
            assertTrue(discardResult.isFailure)
            assertEquals("DRAFT_SEND_QUEUED", (discardResult.exceptionOrNull() as MailReplyDraftException).code)
        } else {
            assertEquals(0L, jobs)
            assertEquals("DISCARDED", state)
            assertEquals(409, (enqueueResult.exceptionOrNull() as DelayedManualReplyException).status)
        }
        assertEquals(1, listOf(discardResult, enqueueResult).count { it.isSuccess })
    }

    @Test
    fun `late finalize failure and stale recovery converge to unknown exactly once`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        makeDuePast(view.id)
        val claim = requireNotNull(delays.claim(view.id))
        val claimedAt = requireNotNull(claim.job.claimedAtMs)
        val (finalizeResult, recoveryResult) = race(
            Callable {
                attempts.finalizeDelayedFailure(
                    payloadOf(draftSnapshot(draft)), claim.job.sendAttemptId, messageIdOf(claim.job.sendAttemptId),
                    MailSendAttemptStatus.DELIVERY_UNKNOWN, "simulated timeout", claim.dispatchRef
                )
            },
            Callable { delays.recoverStale(claimedAt, claimedAt) }
        )
        assertTrue(finalizeResult.isSuccess || recoveryResult.isSuccess)
        assertEquals("UNKNOWN", job(view.id)["status"])
        assertEquals("DELIVERY_UNKNOWN", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
        assertEquals(DelayedReplyPublicError.DELIVERY_UNKNOWN.code, job(view.id)["public_error_code"])
        assertTrue(count("mail_record", "send_status='FAILED' AND sent_at IS NULL") <= 1L)
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
        failure(409) { delays.retry(view.id, OWNER, number(job(view.id), "revision")) }
        assertEquals("UNKNOWN", job(view.id)["status"])
    }

    // ------------------------------------------------------------------ I-5 恢复与收敛

    @Test
    fun `unknown converges only through the same dispatch token and never re-sends`() {
        val snapshot = legacySnapshot("Hello")
        val view = queued(delays.enqueue(snapshot, OWNER))
        makeDuePast(view.id)
        val claim = requireNotNull(delays.claim(view.id))
        val attemptId = claim.job.sendAttemptId
        assertEquals("SENDING", job(view.id)["status"])

        val claimedAt = requireNotNull(claim.job.claimedAtMs)
        assertEquals(listOf(view.id), delays.recoverStale(claimedAt, claimedAt))
        assertEquals("UNKNOWN", job(view.id)["status"])
        assertEquals("DELIVERY_UNKNOWN", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
        // Recovery never re-claims and never calls SMTP.
        assertNull(delays.claim(view.id))
        assertEquals("UNKNOWN", job(view.id)["status"])

        // Late definite success with the SAME token converges UNKNOWN→SENT once.
        val mailRecordId = attempts.finalizeDelayedSuccess(payloadOf(snapshot), attemptId, messageIdOf(attemptId), claim.dispatchRef)
        assertEquals("SENT", job(view.id)["status"])
        assertEquals("SENT", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
        assertEquals("SENT", jdbc.queryForObject("SELECT send_status FROM mail_record WHERE id=?", String::class.java, mailRecordId))
        assertNotNull(jdbc.queryForObject("SELECT sent_at FROM mail_record WHERE id=?", java.sql.Timestamp::class.java, mailRecordId))

        // A second late result cannot create another record or downgrade SENT.
        assertThrows(IllegalStateException::class.java) {
            attempts.finalizeDelayedSuccess(payloadOf(snapshot), attemptId, messageIdOf(attemptId), claim.dispatchRef)
        }
        assertThrows(IllegalStateException::class.java) {
            attempts.finalizeDelayedFailure(payloadOf(snapshot), attemptId, messageIdOf(attemptId),
                MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, "late", claim.dispatchRef)
        }
        assertEquals("SENT", job(view.id)["status"])
        assertEquals(1L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='FAILED'"))
    }

    @Test
    fun `old dispatch token cannot complete a later round`() {
        val snapshot = legacySnapshot("Hello")
        val view = queued(delays.enqueue(snapshot, OWNER))
        makeDuePast(view.id)
        val claim = requireNotNull(delays.claim(view.id))
        attempts.finalizeDelayedFailure(payloadOf(snapshot), claim.job.sendAttemptId, messageIdOf(claim.job.sendAttemptId),
            MailSendAttemptStatus.FAILED_SAFE_TO_RETRY, "451", claim.dispatchRef)
        assertEquals("FAILED", job(view.id)["status"])
        assertEquals(DelayedReplyPublicError.TEMPORARY_DELIVERY_FAILURE.code, job(view.id)["public_error_code"])

        val retried = delays.retry(view.id, OWNER, number(job(view.id), "revision"))
        assertEquals(DelayedManualReplyStatus.WAITING, retried.status)
        assertThrows(IllegalStateException::class.java) {
            attempts.finalizeDelayedSuccess(payloadOf(snapshot), claim.job.sendAttemptId, messageIdOf(claim.job.sendAttemptId), claim.dispatchRef)
        }
        assertEquals("WAITING", job(view.id)["status"])
        assertEquals(1L, count("delayed_manual_reply"))
        assertEquals(1L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test
    fun `preflight failure is retryable from the waiting round with a full new delay`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        val failed = delays.preflightFailure(view.id, view.revision, DelayedReplyPublicError.ACCOUNT_UNAVAILABLE)
        assertEquals(DelayedManualReplyStatus.FAILED, failed.status)
        assertEquals(DelayedReplyPublicError.ACCOUNT_UNAVAILABLE.code, failed.publicErrorCode)
        assertEquals(DelayedReplyPublicError.ACCOUNT_UNAVAILABLE.message, failed.publicErrorMessage)
        assertEquals("FAILED_SAFE_TO_RETRY", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))

        val retried = delays.retry(view.id, OWNER, failed.revision)
        assertEquals(DelayedManualReplyStatus.WAITING, retried.status)
        assertTrue(retried.dueAtMs >= failed.dueAtMs)
        assertTrue(retried.dueAtMs >= number(job(view.id), "created_at_ms") + DELAYED_MANUAL_REPLY_DELAY_MS)
        assertNull(retried.publicErrorCode)
        assertEquals(failed.revision + 1, retried.revision)
    }

    @Test
    fun `retry requires an identical source and revalidates after edits`() {
        val draft = saveDraft()
        val view = queued(delays.enqueue(draftSnapshot(draft), OWNER))
        val failed = delays.preflightFailure(view.id, view.revision, DelayedReplyPublicError.SOURCE_CHANGED)
        val edited = drafts.save(OWNER, MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 100, ACCOUNT),
            MailReplyDraftSaveRequest(1, MailReplyDraftContent("Re: Test", "<p>Edited</p>", "Edited")))
        assertEquals(2L, edited.version)
        failure(409, DelayedReplyPublicError.SOURCE_CHANGED.code) { delays.retry(view.id, OWNER, failed.revision) }
        assertEquals("FAILED", job(view.id)["status"])
        assertEquals("FAILED_SAFE_TO_RETRY", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
    }

    @Test
    fun `permanent failure refuses retry while temporary failure allows it`() {
        val snapshot = legacySnapshot("Hello")
        val view = queued(delays.enqueue(snapshot, OWNER))
        makeDuePast(view.id)
        val claim = requireNotNull(delays.claim(view.id))
        attempts.finalizeDelayedFailure(payloadOf(snapshot), claim.job.sendAttemptId, messageIdOf(claim.job.sendAttemptId),
            MailSendAttemptStatus.FAILED, "550", claim.dispatchRef)
        assertEquals(DelayedReplyPublicError.PERMANENT_DELIVERY_FAILURE.code, job(view.id)["public_error_code"])
        val failedView = requireNotNull(delays.findVisible(view.id))
        assertFalse(failedView.canRetry)
        failure(409) { delays.retry(view.id, OWNER, failedView.revision) }
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM mail_send_attempt", String::class.java))
    }

    @Test
    fun `stale sending rows are recovered as unknown without touching fresh claims`() {
        val snapshot = legacySnapshot("Hello")
        val view = queued(delays.enqueue(snapshot, OWNER))
        makeDuePast(view.id)
        val claim = requireNotNull(delays.claim(view.id))
        val claimedAt = requireNotNull(claim.job.claimedAtMs)

        assertEquals(emptyList<Long>(), delays.recoverStale(claimedAt, claimedAt - 1))
        assertEquals("SENDING", job(view.id)["status"])

        assertEquals(listOf(view.id), delays.recoverStale(claimedAt + 1, claimedAt))
        assertEquals("UNKNOWN", job(view.id)["status"])
        // Nothing left to recover: the row is no longer SENDING.
        assertEquals(emptyList<Long>(), delays.recoverStale(claimedAt + 1, claimedAt))
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    // ------------------------------------------------------------------ 审核任务

    @Test
    fun `review queue keeps approved content and records one deduplicated event per round`() {
        val row = approvedReview()
        val snapshot = reviewSnapshot(row)
        val view = queued(delays.enqueue(snapshot, ADMIN))
        assertEquals(DelayedManualReplyOriginKind.REVIEW, view.originKind)
        assertEquals(1L, count("mailbox_review_draft_event", "event_type='SEND_QUEUED'"))
        queued(delays.enqueue(snapshot, ADMIN))
        assertEquals(1L, count("mailbox_review_draft_event", "event_type='SEND_QUEUED'"))
        assertEquals("WAITING", (reviews.detail(ADMIN, row.id) as MailReviewDraftDetail).sendStatus)

        failure(403) { delays.cancel(view.id, OWNER, view.revision) }
        val cancelled = delays.cancel(view.id, ADMIN, view.revision)
        assertEquals(DelayedManualReplyStatus.CANCELLED, cancelled.status)
        assertEquals(1L, count("mailbox_review_draft_event", "event_type='SEND_CANCELLED'"))
        val after = reviewRow(row.id)
        assertEquals(MailReviewDraftState.APPROVED, after.state)
        assertEquals(row.version, after.version)
        assertEquals(row.html, after.html)
        assertEquals(row.text, after.text)

        val requeued = queued(delays.enqueue(snapshot, ADMIN, cancelled.revision))
        assertEquals("WAITING", job(requeued.id)["status"])
        assertEquals(1L, count("mailbox_review_draft_event", "event_type='SEND_REQUEUED'"))
        assertEquals(row.version, reviewRow(row.id).version)
        assertEquals(0L, count("mail_record", "mail_type='MANUAL_RICH_REPLY'"))
    }

    @Test
    fun `review send entry is blocked while queued and confirm context is frozen`() {
        val row = approvedReview()
        val view = queued(delays.enqueue(reviewSnapshot(row), ADMIN))
        assertEquals(409, assertThrows(MailReviewDraftException::class.java) {
            reviews.prepareSend(ADMIN, row.id, MailReviewSendRequest(row.version, null))
        }.status)
        assertEquals(409, assertThrows(MailReviewDraftException::class.java) {
            reviews.confirmContext(ADMIN, row.id, row.version, reviews.context(ADMIN, row.id).currentToken)
        }.status)
        assertEquals("WAITING", job(view.id)["status"])
        delays.cancel(view.id, ADMIN, view.revision)
        // After cancel the approved draft can be prepared again (queue revision is carried by 04/06).
        assertEquals(row.version, reviews.prepareSend(ADMIN, row.id, MailReviewSendRequest(row.version, null)).draft.version)
        assertEquals(MailReviewDraftState.APPROVED, reviewRow(row.id).state)
    }

    @Test
    fun `review queue does not change its own review context`() {
        val row = approvedReview()
        val before = reviews.context(ADMIN, row.id)
        queued(delays.enqueue(reviewSnapshot(row), ADMIN))
        val after = reviews.context(ADMIN, row.id)
        assertEquals(before.currentToken, after.currentToken)
        assertFalse(after.changed)
    }
}
