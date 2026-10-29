package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.*
import com.weibo.talentintroduction.mail.controller.MailReviewDraftController
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** Real MySQL review/attempt/mail/event transactions; only SMTP and unrelated collaborators are doubles. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(MailReviewDraftRepository::class, MailReviewDraftService::class, MailboxConversationRepository::class,
    MailContentService::class, MailPlaceholderService::class, ManualReplySendAttemptService::class,
    MailReplyDraftRepository::class, MailReplyDraftService::class, MeetingCalendarEventRepository::class,
    MeetingCalendarSendIntegrationTestSupport::class, MailReviewSendIT.Support::class)
class MailReviewSendIT {
    @TestConfiguration class Support {
        @Bean fun objectMapper() = ObjectMapper().findAndRegisterModules()
    }
    companion object {
        private val mysql = DraftSendMysql()
        @JvmStatic @DynamicPropertySource fun database(registry: DynamicPropertyRegistry) = mysql.properties(registry)
        @JvmStatic @AfterAll fun stop() = mysql.stop()
    }
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var drafts: MailReviewDraftService
    @Autowired private lateinit var attempts: ManualReplySendAttemptService
    @SpyBean private lateinit var repository: MailReviewDraftRepository
    @Autowired private lateinit var contacts: ExpertContactRepository
    @Autowired private lateinit var inbound: InboundMailProcessingRepository
    @Autowired private lateinit var records: MailRecordRepository
    @Autowired private lateinit var audit: OperatorActionLogService
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var transactions: PlatformTransactionManager
    @MockBean private lateinit var translation: MailTranslationService
    private val delivery = mock(MailDeliveryService::class.java)
    private val accounts = mock(MailSenderAccountService::class.java)
    private val rules = mock(QaRuleRepository::class.java)
    private val categories = mock(QaCategoryRepository::class.java)
    private val selection = mock(QaFactSelectionService::class.java)
    private val context = mock(AiReplyContextService::class.java)
    private val suppression = mock(EmailSuppressionService::class.java)
    private val identity = mock(ReplyIdentityService::class.java)
    private lateinit var pending: PendingMailOperationService
    private lateinit var sends: MailReviewSendService
    private lateinit var mvc: MockMvc
    private val content = MailContentService()
    private fun <T> anyValue(value: T): T = any<T>() ?: value
    private fun account() = MailSenderAccount(accountCode="review-send",senderEmail="sender@fixture.test",senderName="Sender",
        senderTitle="Officer",senderDisplayName="Sender",teamName="Team",countryName="China",smtpHost="127.0.0.1",smtpPort=25,smtpUsername="sender",
        smtpPassword="pw",imapHost="127.0.0.1",imapPort=993,imapUsername="sender",imapPassword="pw")
    private fun contact() = ExpertContact(id=1,campaignId=1,orcidId="review-send-orcid",expertEmail="expert@fixture.test",expertName="Expert",currentStatus="WAITING_REPLY")
    private fun mail() = ComposedMail("expert@fixture.test","Subject","<p>Hello</p>",html=true,text="Hello")
    private fun delivered() = DeliveredMail("<fixture@test>","SENT",errorCategory=SmtpErrorCategory.SUCCESS)
    private fun variables(): MailVariableService {
        val template = MailComposeTemplateService(mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
            mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),rules,
            mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),mapper,
            mock(MailVariableService::class.java),contacts,accounts,
            ContentVariantService(mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),MailPlaceholderService()))
        return MailVariableService(mock(ExpertSearchService::class.java),template)
    }
    @BeforeEach fun setup() {
        cleanup()
        reset(delivery,accounts,rules,categories,selection,context,suppression,identity,audit)
        jdbc.update("INSERT INTO mail_sender_account(id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password) VALUES(1,'review-send','sender@fixture.test','Sender','127.0.0.1',25,'s','pw','127.0.0.1',993,'s','pw')")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'REVIEW_SEND','Review sending',1)")
        jdbc.update("INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES(1,1,'review-send-orcid','expert@fixture.test','Expert','WAITING_REPLY'),(2,1,'other-review','other@fixture.test','Other','WAITING_REPLY')")
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at) VALUES('user1',1,'admin',NOW(3))")
        jdbc.update("INSERT INTO mail_record(id,expert_contact_id,direction,mail_type,sender_account_code,message_id,subject,body,send_status,sent_at,created_at) VALUES(10,1,'OUTBOUND','INTRODUCTION','review-send','<anchor@test>','Original','Original','SENT',NOW(3),NOW(3))")
        addInbound(100)
        `when`(accounts.getManualSendAccount("review-send")).thenReturn(account())
        `when`(categories.findAll()).thenReturn(emptyList())
        `when`(selection.select(anyValue(""),anyValue(emptyList<Long>()),anyBoolean())).thenReturn(ResolvedQaRules(emptyList(),emptyList(),emptyList()))
        `when`(context.build(anyValue(contact()),anyList(),anyValue(""),anyValue(""),any())).thenReturn(AiReplyContext("","",contextWarnings=emptyList()))
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenReturn(delivered())
        val variables = variables()
        val meetings = MeetingConfirmationService(inbound,contacts,accounts,mock(MailComposeTemplateService::class.java),content,variables)
        pending = PendingMailOperationService(inbound,contacts,mock(ExpertOperatorStatusService::class.java),
            mock(ExpertIndexLevelOperationService::class.java),accounts,delivery,records,mock(MailRecordQaRuleRepository::class.java),audit,
            rules,categories,selection,mock(AiReplyDraftService::class.java),context,AiReplyHighRiskClaimValidator(rules),
            MailBodyCleaner(),content,variables,attempts,mock(TrustReplyWorkbenchService::class.java),
            mock(UnsupportedAnswerIndexService::class.java),suppression,meetings,identity)
        sends = MailReviewSendService(drafts,pending)
        mvc = MockMvcBuilders.standaloneSetup(MailReviewDraftController(drafts,sends)).setControllerAdvice(GlobalExceptionHandler()).build()
    }
    @AfterEach fun teardown() { jdbc.execute("DROP TRIGGER IF EXISTS reject_review_event"); cleanup() }
    private fun cleanup() {
        listOf("mailbox_review_draft_event","mailbox_review_draft","mailbox_reply_draft","expert_mail_read_access",
            "meeting_calendar_event","mail_record_rag_fact","mail_record_qa_rule","mail_record","mail_send_attempt",
            "outbound_mail_attachment","inbound_mail_processing","expert_contact","campaign","mail_sender_account").forEach { jdbc.update("DELETE FROM $it") }
    }
    private fun addInbound(id: Long) = jdbc.update("INSERT INTO inbound_mail_processing(id,sender_account_code,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id,created_at,updated_at) VALUES(?,'review-send',?,?,'expert@fixture.test','Inbound','Hello','Hello',NOW(3),'MANUAL_REVIEW','QA_NO_MATCH',1,NOW(3),NOW(3))",id,id,"<in-$id@test>")
    private fun create(subject: String = "Subject", html: String = "<p>Hello</p>") = drafts.create("user1",1,UUID.randomUUID().toString(),subject,html) as MailReviewDraftDetail
    private fun submitted() = create().let { drafts.submit("user1",it.id,it.version) as MailReviewDraftDetail }
    private fun approved(): MailReviewDraftDetail = submitted().let { drafts.approve("admin",it.id,it.version,drafts.context("admin",it.id).currentToken) as MailReviewDraftDetail }
    private fun send(d: MailReviewDraftDetail, confirmation: Long? = null, confirmed: Boolean = false) = sends.send("admin",d.id,MailReviewSendRequest(d.version,confirmation,confirmed))
    private fun detail(d: MailReviewDraftDetail) = drafts.detail("admin",d.id) as MailReviewDraftDetail
    private fun smtpCount() = mockingDetails(delivery).invocations.count { it.method.name == "send" }
    private fun count(table: String, where: String = "1=1") = jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $where",Long::class.java)!!
    private fun failure(status: Int, code: String? = null, block: () -> Unit) {
        val ex = assertThrows(MailReviewDraftException::class.java,block)
        assertEquals(status,ex.status); if (code != null) assertEquals(code,ex.code)
    }
    private fun http(d: MailReviewDraftDetail, body: String, actor: String = "admin", target: MockMvc = mvc): org.springframework.mock.web.MockHttpServletResponse {
        val session = MockHttpSession(); session.setAttribute(AuthSessionKeys.USERNAME,actor)
        return target.perform(post("/api/mail/mailbox/review-drafts/${d.id}/send").session(session).contentType("application/json").content(body)).andReturn().response
    }
    private fun request(d: MailReviewDraftDetail, extra: String = "") = "{\"expectedVersion\":${d.version}$extra}"
    private fun payload(d: MailReviewDraftDetail): ManualReplySendAttemptService.SendPayload {
        val ref = drafts.prepareSend("admin",d.id,MailReviewSendRequest(d.version))
        val row = ref.draft
        return ManualReplySendAttemptService.SendPayload("review-send-orcid",1,if (row.targetKind == "INBOUND") row.targetId else null,
            row.accountCode,"expert@fixture.test",row.subject,row.text,row.html,
            if (row.targetKind == "INBOUND") "<in-${row.targetId}@test>" else "<anchor@test>",emptyList(),null,
            sourceAnchor=if (row.targetKind == "OUTBOUND") "MAIL_RECORD:${row.targetId}" else null,
            idempotencyRequestId=if (row.targetKind == "OUTBOUND") row.sendRequestId else null).also { it.reviewSendRef = ref }
    }
    private fun warning() {
        `when`(identity.analyzeFinalBody(anyValue(ReplyIdentitySource(contact(),account(),ReplyIdentityContext(1,100,null,"review-send",contact().expertEmail,"Sender"),null,null)),anyString(),anyString()))
            .thenReturn(listOf(SafetyFinding("REPLY_SIGNATURE_MISMATCH",SafetySeverity.NORMAL,"Signature changed")))
    }
    private fun rejectEvent(type: String) = jdbc.execute("CREATE TRIGGER reject_review_event BEFORE INSERT ON mailbox_review_draft_event FOR EACH ROW BEGIN IF NEW.event_type='$type' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected review event failure'; END IF; END")

    @Test fun `approval and confirmation do not deliver and every non approved state rejects`() {
        val draft = create()
        failure(409,"REVIEW_STATE_CONFLICT") { send(draft) }
        val submitted = drafts.submit("user1",draft.id,draft.version) as MailReviewDraftDetail
        failure(409,"REVIEW_STATE_CONFLICT") { send(submitted) }
        val returned = drafts.returnDraft("admin",submitted.id,submitted.version,"Revise") as MailReviewDraftDetail
        failure(409) { send(returned) }
        val withdrawn = drafts.withdraw("user1",submitted().id,2) as MailReviewDraftDetail
        failure(409) { send(withdrawn) }
        val approved = approved()
        drafts.confirmContext("admin",approved.id,approved.version,drafts.context("admin",approved.id).currentToken)
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
        assertEquals("NOT_SENT",detail(approved).sendStatus)
    }

    @Test fun `strict admin endpoint rejects forged payload and preserves target error status`() {
        val d = approved()
        assertEquals(403,http(d,request(d),"user1").status)
        assertEquals(403,http(d,request(d),"other").status)
        for (extra in listOf(",\"html\":\"changed\"",",\"recipient\":\"bad@test\"",",\"attachmentIds\":[]",",\"reviewSendRef\":{}",",\"safetyWarningConfirmed\":\"true\"",",\"confirmationEventId\":1.5"))
            assertEquals(422,http(d,request(d,extra)).status)
        assertEquals(422,http(d,"{\"expectedVersion\":9223372036854775808}").status)
        jdbc.update("UPDATE inbound_mail_processing SET expert_contact_id=2 WHERE id=100")
        val invalid = http(d,request(d)); assertEquals(422,invalid.status)
        assertTrue(invalid.contentAsString.contains("REVIEW_TARGET_NOT_FOUND"))
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
    }

    @Test fun `revoked owner missing account simulator and deleted target never fall back`() {
        val d = approved()
        jdbc.update("DELETE FROM expert_mail_read_access")
        failure(403) { send(d) }
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at) VALUES('user1',1,'admin',NOW(3))")
        jdbc.update("UPDATE mailbox_review_draft SET account_code='SIMULATOR_NOOP' WHERE id=?",d.id)
        failure(422) { send(d) }
        jdbc.update("UPDATE mailbox_review_draft SET account_code='review-send' WHERE id=?",d.id)
        jdbc.update("DELETE FROM inbound_mail_processing WHERE id=100")
        failure(422) { send(d) }
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
    }

    @Test fun `stale forged wrong draft wrong version and wrong actor confirmations cannot authorize claim`() {
        val a = approved(); val b = approved()
        addInbound(101)
        failure(409,"REVIEW_CONTEXT_CHANGED") { send(a) }
        failure(409,"REVIEW_CONTEXT_CHANGED") { send(a,999999) }
        val token = drafts.context("admin",a.id).currentToken
        val other = drafts.confirmContext("admin",b.id,b.version,token)
        failure(409,"REVIEW_CONTEXT_CHANGED") { send(a,other.confirmationEventId) }
        val confirmation = drafts.confirmContext("admin",a.id,a.version,token)
        jdbc.update("UPDATE mailbox_review_draft_event SET draft_version=draft_version-1 WHERE id=?",confirmation.confirmationEventId)
        failure(409) { send(a,confirmation.confirmationEventId) }
        jdbc.update("UPDATE mailbox_review_draft_event SET draft_version=?,actor_username='user1' WHERE id=?",a.version,confirmation.confirmationEventId)
        failure(409) { send(a,confirmation.confirmationEventId) }
        jdbc.update("UPDATE mailbox_review_draft_event SET actor_username='admin' WHERE id=?",confirmation.confirmationEventId)
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
        addInbound(102)
        failure(409) { send(a,confirmation.confirmationEventId) }
        val current = drafts.confirmContext("admin",a.id,a.version,drafts.context("admin",a.id).currentToken)
        assertEquals(0,smtpCount())
        assertEquals("SENT",send(a,current.confirmationEventId).sendStatus)
        assertEquals("SENT",send(a).sendStatus) // Own sent mail changes context, not the completed result.
        assertEquals(1,smtpCount())
    }

    @Test fun `approved content is final canonical MIME and separate reviews never bind ordinary send`() {
        val first = approved(); val second = approved()
        val p1 = payload(first); val p2 = payload(second)
        val ordinary = p1.copy()
        assertNull(ordinary.reviewSendRef)
        assertNotEquals(attempts.computeFingerprint(p1).fullHex,attempts.computeFingerprint(p2).fullHex)
        assertNotEquals(attempts.computeFingerprint(p1).fullHex,attempts.computeFingerprint(ordinary).fullHex)
        pending.sendManualRichReply(100,null,first.subject,first.html,first.text,"admin",safetyWarningConfirmed=true)
        val c1 = drafts.confirmContext("admin",first.id,first.version,drafts.context("admin",first.id).currentToken)
        send(first,c1.confirmationEventId)
        val c2 = drafts.confirmContext("admin",second.id,second.version,drafts.context("admin",second.id).currentToken)
        send(second,c2.confirmationEventId)
        assertEquals(3,smtpCount()); assertEquals(3L,count("mail_send_attempt")); assertEquals(3L,count("mail_record","mail_type='MANUAL_RICH_REPLY'"))
        val sent = mockingDetails(delivery).invocations.filter { it.method.name == "send" }.map { it.getArgument<ComposedMail>(1) }
        sent.forEach { assertEquals(first.subject,it.subject); assertEquals(first.html,it.body); assertEquals(first.text,it.text); assertTrue(it.outboundAttachments.isEmpty()); assertNull(it.calendarAttachment) }
        assertNotEquals(detail(first).sentMailRecordId,detail(second).sentMailRecordId)
        assertEquals(MailReviewDraftState.APPROVED,detail(first).state)
        assertTrue(drafts.events("admin",first.id).any { it.eventType == "APPROVE" && it.payload["html"] == first.html })
    }

    @Test fun `concurrent same review claims once and successful replays create no extra event`() {
        val d = approved(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenAnswer { entered.countDown(); check(release.await(20,TimeUnit.SECONDS)); delivered() }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<PendingMailSendResult> { send(d) }
            assertTrue(entered.await(20,TimeUnit.SECONDS))
            assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
            release.countDown(); assertEquals("SENT",first.get(20,TimeUnit.SECONDS).sendStatus)
            val events = count("mailbox_review_draft_event")
            repeat(2) { assertEquals("SENT",send(d).sendStatus) }
            assertEquals(events,count("mailbox_review_draft_event")); assertEquals(1,smtpCount())
            assertEquals(1L,count("mail_send_attempt")); assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_SUCCEEDED'"))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `bound SENT uses DEDUP before context and unbound completed lookup cannot mix drafts`() {
        val d = approved(); val p = payload(d)
        send(d)
        assertNotNull(attempts.findCompletedByPayload(p))
        assertEquals(ManualReplySendAttemptService.ClaimResult.DEDUP_SENT,attempts.prepareAndClaim(p).result)
        val other = approved(); val forged = payload(other).also { it.reviewSendRef = p.reviewSendRef }
        // The exact content/domain can find an attempt only through its own persisted binding.
        jdbc.update("UPDATE mailbox_review_draft SET send_attempt_id=NULL WHERE id=?",d.id)
        assertThrows(IllegalStateException::class.java) { attempts.findCompletedByPayload(forged) }
        assertEquals(1,smtpCount())
    }

    @Test fun `outbound review fixed request and fixed original anchor converge after newer mail`() {
        jdbc.update("DELETE FROM inbound_mail_processing")
        val d = approved(); assertEquals("OUTBOUND",d.targetKind)
        assertEquals("SENT",send(d).sendStatus)
        assertEquals("SENT",send(d).sendStatus)
        assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt"))
        val p = payload(d)
        val requestResult = attempts.findCompletedByRequestId(p.orcidId,requireNotNull(p.idempotencyRequestId))
        assertEquals(detail(d).sentMailRecordId,requestResult?.mailRecord?.id)
        assertEquals(ManualReplySendAttemptService.ClaimResult.DEDUP_SENT,attempts.prepareAndClaim(p).result)
    }

    @Test fun `unknown result never retries or replaces request attempt and remains visible`() {
        val d = approved()
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenThrow(IllegalStateException("connection lost after DATA"))
        repeat(2) { assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value()) }
        assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt"))
        assertEquals("DELIVERY_UNKNOWN",detail(d).sendStatus); assertNull(detail(d).sentMailRecordId)
        assertEquals(d.html,detail(d).html)
        assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_RESULT'"))
    }

    @Test fun `safe retry keeps same binding and updates record with transactional result events`() {
        val d = approved()
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenReturn(DeliveredMail("failure","FAILED",SmtpErrorCategory.TRANSIENT,451,"busy"))
        assertEquals(503,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
        val attempt = repository.find(d.id)!!.sendAttemptId
        assertEquals("FAILED_SAFE_TO_RETRY",detail(d).sendStatus); assertNull(detail(d).sentMailRecordId)
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenReturn(delivered())
        val confirmation = drafts.confirmContext("admin",d.id,d.version,drafts.context("admin",d.id).currentToken)
        send(d,confirmation.confirmationEventId)
        assertEquals(attempt,repository.find(d.id)!!.sendAttemptId)
        assertEquals(1L,count("mail_send_attempt")); assertEquals(1L,count("mail_record","mail_type='MANUAL_RICH_REPLY'"))
        assertEquals(2L,count("mailbox_review_draft_event","event_type='SEND_CLAIMED'"))
        assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_RESULT'"))
        assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_SUCCEEDED'"))
    }

    @Test fun `safety error retains original global response and confirmed resubmission sends frozen body`() {
        val d = approved(); warning()
        val blocked = http(d,request(d)); assertEquals(422,blocked.status)
        assertTrue(blocked.contentAsString.contains("MANUAL_SEND_SAFETY_BLOCKED")); assertTrue(blocked.contentAsString.contains("REPLY_SIGNATURE_MISMATCH"))
        assertEquals(0L,count("mail_send_attempt")); assertEquals(0,smtpCount())
        assertEquals(200,http(d,request(d,",\"safetyWarningConfirmed\":true")).status)
        assertEquals(200,http(d,request(d)).status)
        assertEquals(1,smtpCount()); assertEquals(d.html,detail(d).html)
    }

    @Test fun `final rendered content difference is rejected before claim without changing approval`() {
        val d = approved(); val ref = drafts.prepareSend("admin",d.id,MailReviewSendRequest(d.version))
        failure(409,"REVIEW_CONTENT_CHANGED") {
            pending.sendManualRichReply(100,"review-send",d.subject,d.html,"Changed text","admin",authenticatedUsername="admin",reviewSendRef=ref)
        }
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt")); assertEquals(d.text,detail(d).text)
        failure(409) { attempts.prepareAndClaim(payload(d).copy(finalHtml="<p>changed</p>").also { it.reviewSendRef = ref }) }
    }

    @Test fun `submission normalizes subject and rejects variables and final expanded validation length`() {
        val d = create("  Subject  ","<div>Hello<br>World</div>")
        assertEquals("Subject",d.subject)
        assertEquals(content.normalizeManualRichHtmlLineBreaks(d.html),d.html)
        val variable = create(html="<p>Dear \${senderName}</p>")
        failure(422,"REVIEW_INVALID_CONTENT") { drafts.submit("user1",variable.id,variable.version) }
        val long = create(html="<p>${"x".repeat(11000)}</p>")
        failure(422) { drafts.submit("user1",long.id,long.version) }
        assertEquals(0,smtpCount())
    }

    @Test fun `claim event failure rolls back attempt and binding before SMTP`() {
        val d = approved(); rejectEvent("SEND_CLAIMED")
        assertThrows(Exception::class.java) { send(d) }
        assertEquals(0L,count("mail_send_attempt")); assertNull(repository.find(d.id)!!.sendAttemptId)
        assertEquals(0,smtpCount()); assertEquals("NOT_SENT",detail(d).sendStatus)
    }

    @Test fun `success event failure rolls back SENT transaction and preserves unknown without resend`() {
        val d = approved(); rejectEvent("SEND_SUCCEEDED")
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
        assertEquals("DELIVERY_UNKNOWN",detail(d).sendStatus); assertNull(detail(d).sentMailRecordId)
        assertEquals(0L,count("mail_record","mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
        assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_RESULT'"))
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
        assertEquals(1,smtpCount()); assertEquals(d.html,detail(d).html)
    }

    @Test fun `failure event failure rolls back result record and attempt then blocks repeat`() {
        val d = approved(); rejectEvent("SEND_RESULT")
        `when`(delivery.send(anyValue(account()),anyValue(mail()))).thenThrow(IllegalStateException("lost"))
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
        assertEquals("DELIVERY_IN_PROGRESS",detail(d).sendStatus)
        assertEquals(0L,count("mail_record","mail_type='MANUAL_RICH_REPLY'"))
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(d) }.status.value())
        assertEquals(1,smtpCount())
    }

    @Test fun `HTTP failure after success commit still projects SENT and retry does not deliver twice`() {
        val d = approved()
        var postSendFailureReached = false
        val brokenResponse = MockMvcBuilders.standaloneSetup(MailReviewDraftController(drafts,sends))
            .setControllerAdvice(GlobalExceptionHandler()).addInterceptors(object : HandlerInterceptor {
                override fun postHandle(request: HttpServletRequest,response: HttpServletResponse,handler: Any,modelAndView: ModelAndView?) {
                    postSendFailureReached = true
                    throw IllegalStateException("injected post send response failure")
                }
            }).build()
        // @ResponseBody may already be committed at postHandle; the thrown failure is still exercised.
        runCatching { http(d,request(d),target=brokenResponse) }
        assertTrue(postSendFailureReached, "The response failure must actually run after durable send")
        assertEquals("SENT",detail(d).sendStatus); assertNotNull(detail(d).sentMailRecordId)
        assertEquals(200,http(d,request(d)).status)
        assertEquals(1,smtpCount()); assertEquals(1L,count("mailbox_review_draft_event","event_type='SEND_SUCCEEDED'"))
    }

    @Test fun `context is read fresh after a contended review lock rather than an earlier transaction snapshot`() {
        val d = approved(); val p = payload(d)
        val lockRequested = CountDownLatch(1)
        doAnswer { invocation -> lockRequested.countDown(); invocation.callRealMethod() }
            .`when`(repository).find(d.id,true)
        val executor = Executors.newSingleThreadExecutor()
        try {
            lateinit var sendFuture: java.util.concurrent.Future<ManualReplySendAttemptService.ClaimedAttempt>
            TransactionTemplate(transactions).execute {
                jdbc.queryForObject("SELECT id FROM mailbox_review_draft WHERE id=? FOR UPDATE",Long::class.java,d.id)
                sendFuture = executor.submit<ManualReplySendAttemptService.ClaimedAttempt> { attempts.prepareAndClaim(p) }
                assertTrue(lockRequested.await(20,TimeUnit.SECONDS))
                // Commit a new conversation row after prepare's first plain read, before its lock succeeds.
                val independent = TransactionTemplate(transactions).apply { propagationBehavior = Propagation.REQUIRES_NEW.value() }
                independent.execute { addInbound(101) }
            }
            val failure = assertThrows(java.util.concurrent.ExecutionException::class.java) { sendFuture.get(20,TimeUnit.SECONDS) }
            assertEquals("REVIEW_CONTEXT_CHANGED",(failure.cause as MailReviewDraftException).code)
            assertEquals(0L,count("mail_send_attempt")); assertEquals(0,smtpCount())
        } finally { executor.shutdownNow() }
    }
}
