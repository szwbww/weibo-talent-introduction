package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.campaign.service.ExpertIndexLevelOperationService
import com.weibo.talentintroduction.campaign.service.ExpertOperatorStatusService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.llm.service.*
import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.repository.*
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.apache.catalina.startup.Tomcat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/** SMTP and external/read-only collaborators may be doubles; draft/attempt/record/meeting SQL and transactions are real. */
@EnabledIfSystemProperty(named="mysqlIt",matches="true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@TestPropertySource(properties=["spring.flyway.placeholder-replacement=false"])
@Import(MailReplyDraftRepository::class,MailReplyDraftService::class,ManualReplySendAttemptService::class,
    MeetingCalendarEventRepository::class,MeetingCalendarSendIntegrationTestSupport::class)
class MailReplyDraftSendIntegrationTest {
    companion object {
        private val mysql = DraftSendMysql()
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) = mysql.properties(registry)
        @JvmStatic @AfterAll fun stop() { mysql.stop() }
    }
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var drafts: MailReplyDraftService
    @Autowired private lateinit var attempts: ManualReplySendAttemptService
    @Autowired private lateinit var contacts: ExpertContactRepository
    @Autowired private lateinit var inbound: InboundMailProcessingRepository
    @Autowired private lateinit var records: MailRecordRepository
    @Autowired private lateinit var attachments: OutboundAttachmentService
    @Autowired private lateinit var audit: OperatorActionLogService
    private val delivery = mock(MailDeliveryService::class.java)
    private val accounts = mock(MailSenderAccountService::class.java)
    private val rules = mock(QaRuleRepository::class.java)
    private val categories = mock(QaCategoryRepository::class.java)
    private val factSelection = mock(QaFactSelectionService::class.java)
    private val aiContext = mock(AiReplyContextService::class.java)
    private val suppression = mock(EmailSuppressionService::class.java)
    private val meetingTemplates = mock(MailComposeTemplateService::class.java)
    private val ragKnowledge = mock(com.weibo.talentintroduction.rag.service.RagKnowledgeBase::class.java)
    private lateinit var pending: PendingMailOperationService
    private lateinit var meetings: MeetingConfirmationService
    private val target = MailReplyDraftTarget(1,MailReplyDraftKind.INBOUND,100,"draft-send")
    private val original = MailReplyDraftContent("Re: Test","<p>Hello</p>","Hello")
    private fun <T> anyValue(value: T): T = any<T>() ?: value
    private fun account() = MailSenderAccount(accountCode="draft-send",senderEmail="sender@fixture.test",senderName="Sender",
        senderTitle="Officer",senderDisplayName="Sender",teamName="Team",countryName="China",
        smtpHost="smtp.fixture.test",smtpPort=25,smtpUsername="sender",smtpPassword="pw",imapHost="imap.fixture.test",
        imapPort=993,imapUsername="sender",imapPassword="pw")
    private fun contact() = ExpertContact(id=1,campaignId=1,orcidId="draft-send-orcid",expertEmail="expert@fixture.test",expertName="Expert",currentStatus="WAITING_REPLY")
    private fun composed() = ComposedMail("expert@fixture.test","Re: Test","<p>Hello</p>",html=true,text="Hello")
    private fun successful() = DeliveredMail("<fixture@weibo.com>","SENT",errorCategory=SmtpErrorCategory.SUCCESS)
    private fun variableService(): MailVariableService {
        val template = MailComposeTemplateService(
            mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository::class.java),
            mock(com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository::class.java),rules,
            mock(com.weibo.talentintroduction.reply.repository.ReplySnippetRepository::class.java),ObjectMapper(),
            mock(MailVariableService::class.java),contacts,accounts,
            ContentVariantService(mock(com.weibo.talentintroduction.variant.repository.ContentVariantRepository::class.java),MailPlaceholderService()))
        return MailVariableService(mock(ExpertSearchService::class.java),template)
    }
    @BeforeEach fun setup() {
        reset(audit); CalendarSendToggle.failOnCreate=false
        cleanup()
        jdbc.update("INSERT INTO mail_sender_account (id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password) VALUES (1,'draft-send','sender@fixture.test','Sender','smtp.fixture',25,'s','pw','imap.fixture',993,'s','pw')")
        jdbc.update("INSERT INTO campaign (id,campaign_code,campaign_name,sender_account_id) VALUES (1,'DRAFT_SEND','Draft sending',1)")
        jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email,expert_name,current_status) VALUES (1,1,'draft-send-orcid','expert@fixture.test','Expert','WAITING_REPLY'),(2,1,'other-orcid','other@fixture.test','Other','WAITING_REPLY')")
        jdbc.update("INSERT INTO inbound_mail_processing (id,sender_account_code,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id,created_at,updated_at) VALUES (100,'draft-send',1,'<inbound@test>','expert@fixture.test','Test','Hello','Hello',NOW(),'MANUAL_REVIEW','QA_NO_MATCH',1,NOW(),NOW()),(101,'draft-send',2,'<other-inbound@test>','expert@fixture.test','Test','Hello','Hello',NOW(),'MANUAL_REVIEW','QA_NO_MATCH',1,NOW(),NOW())")
        jdbc.update("INSERT INTO mail_record (id,expert_contact_id,direction,mail_type,sender_account_code,message_id,subject,body,send_status,sent_at,created_at) VALUES (10,1,'OUTBOUND','INTRODUCTION','draft-send','<anchor@test>','Introduction','Introduction','SENT',NOW(),NOW())")
        `when`(accounts.getManualSendAccount("draft-send")).thenReturn(account())
        `when`(suppression.isSuppressed(anyString())).thenReturn(false)
        `when`(categories.findAll()).thenReturn(emptyList())
        `when`(factSelection.select(anyValue(""),anyValue(emptyList<Long>()),anyBoolean())).thenReturn(ResolvedQaRules(emptyList(),emptyList(),emptyList()))
        `when`(aiContext.build(anyValue(contact()),anyList(),anyValue(""),anyValue(""),any())).thenReturn(AiReplyContext("","",contextWarnings=emptyList()))
        `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenReturn(successful())
        `when`(meetingTemplates.renderByCode(anyValue("MEETING_INVITATION"),anyMap(),anyInt())).thenAnswer { invocation ->
            val vars = invocation.getArgument<Map<String,String>>(1)
            ComposeTemplateRenderResult("Meeting","Dear Expert,\n\nWe have noted the meeting time as ${vars["meeting_time"]}.\n\nPlease join the meeting using the following link:\n\n${vars["zoom_url"]}\n\nBest regards, Sender")
        }
        val variables = variableService()
        meetings = MeetingConfirmationService(inbound,contacts,accounts,meetingTemplates,MailContentService(),variables)
        pending = PendingMailOperationService(inbound,contacts,mock(ExpertOperatorStatusService::class.java),
            mock(ExpertIndexLevelOperationService::class.java),accounts,delivery,records,mock(MailRecordQaRuleRepository::class.java),audit,
            rules,categories,factSelection,mock(AiReplyDraftService::class.java),aiContext,AiReplyHighRiskClaimValidator(rules),
            MailBodyCleaner(),MailContentService(),variables,attempts,mock(TrustReplyWorkbenchService::class.java),
            mock(UnsupportedAnswerIndexService::class.java),suppression,meetings,ragKnowledgeBase=ragKnowledge,
            outboundAttachmentService=attachments,mailReplyDraftService=drafts)
    }
    @AfterEach fun teardown() { jdbc.execute("DROP TRIGGER IF EXISTS reject_draft_sent"); cleanup() }
    private fun cleanup() {
        for (table in listOf("mailbox_reply_draft","meeting_calendar_event","mail_record_rag_fact","mail_record_qa_rule",
            "mail_record","mail_send_attempt","outbound_mail_attachment","inbound_mail_processing","expert_contact","campaign","mail_sender_account")) jdbc.update("DELETE FROM $table")
    }
    private fun save(content: MailReplyDraftContent = original, t: MailReplyDraftTarget = target, owner: String = "op", version: Long = 0) =
        drafts.save(owner,t,MailReplyDraftSaveRequest(version,content))
    private fun ref(draft: MailReplyDraftDetail) = MailReplyDraftRef(requireNotNull(draft.id),draft.version)
    private fun send(draft: MailReplyDraftDetail, content: MailReplyDraftContent = requireNotNull(draft.content), owner: String = "op",
        version: Long = draft.version, processing: Long = 100, meeting: MeetingInput? = null, sha: String? = null, ids: List<String> = emptyList()) =
        pending.sendManualRichReply(processing,null,content.subject,content.html,content.text,"forged",
            authenticatedUsername=owner,draftRef=MailReplyDraftRef(requireNotNull(draft.id),version),meeting=meeting,
            previewAttachmentSha256=sha,attachmentIds=ids,safetyWarningConfirmed=true)
    private fun conversation(draft: MailReplyDraftDetail, content: MailReplyDraftContent = requireNotNull(draft.content),
        requestId: String = requireNotNull(content.context.requestId), anchor: Long? = content.context.followUpAnchorMailRecordId) =
        pending.sendConversationManualRichReply(1,requestId,draft.target?.accountScope?.takeIf { it.isNotEmpty() },anchor,
            content.subject,content.html,content.text,"op",safetyWarningConfirmed=true,authenticatedUsername="op",draftRef=ref(draft))
    private fun smtpCount() = mockingDetails(delivery).invocations.count { it.method.name == "send" }
    private fun count(table: String, condition: String = "1=1") = jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $condition",Long::class.java)!!
    private fun assertActive(draft: MailReplyDraftDetail, version: Long = draft.version) {
        val row = drafts.get("op",requireNotNull(draft.id)); assertEquals(MailReplyDraftState.ACTIVE,row.state); assertEquals(version,row.version); assertNotNull(row.content)
    }

    @Test fun `inbound success closes exactly its version and terminal replay makes no SMTP`() {
        val draft=save(); assertEquals("SENT",send(draft).sendStatus)
        val closed=drafts.get("op",draft.id!!); assertEquals(MailReplyDraftState.SENT,closed.state); assertEquals(2L,closed.version); assertNull(closed.content)
        assertEquals("SENT",send(draft).sendStatus); assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt")); assertEquals(0L,drafts.list("op",null,null,0,10).total)
    }
    @Test fun `outbound and inbound followup use real conversation anchors and trusted early replay`() {
        for (t in listOf(MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND),target)) {
            val content=original.copy(context=MailReplyDraftContext(requestId=UUID.randomUUID().toString(),followUpAnchorMailRecordId=10))
            val draft=save(content,t); assertEquals("SENT",conversation(draft).sendStatus); assertEquals("SENT",conversation(draft).sendStatus)
            assertEquals(MailReplyDraftState.SENT,drafts.get("op",draft.id!!).state)
        }
        assertEquals(2,smtpCount()); assertEquals(2L,count("mail_record","mail_type='MANUAL_RICH_REPLY' AND in_reply_to='<anchor@test>'"))
    }
    @Test fun `wrong owner version contact processing account body attachment and anchor all fail before SMTP`() {
        val draft=save()
        assertEquals("NOT_FOUND",assertThrows(MailReplyDraftException::class.java) { send(draft,owner="other") }.code)
        assertEquals("DRAFT_VERSION_CONFLICT",assertThrows(MailReplyDraftException::class.java) { send(draft,version=2) }.code)
        for (action in listOf<() -> Unit>({ send(draft,content=original.copy(html="<p>Tampered</p>")) },
            { send(draft,processing=101) },{ send(draft,ids=listOf("foreign")) })) {
            assertEquals("DRAFT_CONTENT_MISMATCH",assertThrows(MailReplyDraftException::class.java) { action() }.code)
        }
        val other=save(original,MailReplyDraftTarget(2,MailReplyDraftKind.OUTBOUND))
        assertEquals("DRAFT_CONTENT_MISMATCH",assertThrows(MailReplyDraftException::class.java) { send(other) }.code)
        val wrongScope=save(original,MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND))
        assertEquals("DRAFT_CONTENT_MISMATCH",assertThrows(MailReplyDraftException::class.java) { pending.sendConversationManualRichReply(1,UUID.randomUUID().toString(),"draft-send",subject=original.subject,htmlBody=original.html,textBody=original.text,operatorName="op",authenticatedUsername="op",draftRef=ref(wrongScope)) }.code)
        val followed=save(original.copy(context=MailReplyDraftContext(requestId=UUID.randomUUID().toString(),followUpAnchorMailRecordId=10)),target,version=1)
        assertEquals("DRAFT_CONTENT_MISMATCH",assertThrows(MailReplyDraftException::class.java) { conversation(followed,anchor=null) }.code)
        assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt")); assertActive(followed,2)
    }
    @Test fun `safe failure retains content and retry updates the same failed record before closing`() {
        val draft=save(); `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenReturn(DeliveredMail("fixture","FAILED",smtpResponseCode=451,errorCategory=SmtpErrorCategory.TRANSIENT))
        assertEquals(503,assertThrows(ResponseStatusException::class.java) { send(draft) }.status.value()); assertActive(draft)
        val attempt=drafts.get("op",draft.id!!).sendAttemptId; val record=jdbc.queryForObject("SELECT id FROM mail_record WHERE mail_send_attempt_id=?",Long::class.java,attempt)
        `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenReturn(successful())
        assertEquals("SENT",send(draft).sendStatus); assertEquals(attempt,drafts.get("op",draft.id!!).sendAttemptId)
        assertEquals(record,jdbc.queryForObject("SELECT id FROM mail_record WHERE mail_send_attempt_id=? AND send_status='SENT'",Long::class.java,attempt)); assertEquals(1L,count("mail_send_attempt")); assertEquals(2,smtpCount())
    }
    @Test fun `safe and permanent failures permit edited versions to bind a new attempt`() {
        for ((code,category) in listOf(451 to SmtpErrorCategory.TRANSIENT,550 to SmtpErrorCategory.PERMANENT)) {
            val t=target.copy(processingId=if (code==451) 100 else 101)
            val draft=save(t=t); `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenReturn(DeliveredMail("fixture","FAILED",smtpResponseCode=code,errorCategory=category))
            assertThrows(ResponseStatusException::class.java) { send(draft,processing=t.processingId) }
            val old= drafts.get("op",draft.id!!).sendAttemptId
            val edited=save(original.copy(text="Edited",html="<p>Edited</p>"),t,version=1)
            `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenReturn(successful())
            assertEquals("SENT",send(edited,processing=t.processingId).sendStatus); assertNotEquals(old,drafts.get("op",draft.id!!).sendAttemptId)
        }
        assertEquals(4,smtpCount()); assertEquals(4L,count("mail_send_attempt"))
    }
    @Test fun `unknown binding blocks changed content and new requestId but explicit discard is allowed`() {
        val content=original.copy(context=MailReplyDraftContext(requestId=UUID.randomUUID().toString(),followUpAnchorMailRecordId=10))
        val draft=save(content,MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND))
        `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenThrow(IllegalStateException("simulated timeout"))
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { conversation(draft) }.status.value()); assertActive(draft)
        val newer=save(content.copy(html="<p>New</p>",text="New",context=content.context.copy(requestId=UUID.randomUUID().toString())),draft.target!!,version=1)
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { conversation(newer) }.status.value())
        assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt")); assertEquals("DELIVERY_UNKNOWN",drafts.get("op",draft.id!!).sendAttemptStatus)
        assertEquals(MailReplyDraftState.DISCARDED,drafts.discard("op",draft.id!!,2).state)
        assertEquals("DRAFT_CLOSED",assertThrows(MailReplyDraftException::class.java) { save(content,draft.target!!,version=3) }.code)
    }
    @Test fun `concurrent SMTP edit save and discard retain the newer version with draft first locks`() {
        val content=original.copy(context=MailReplyDraftContext(requestId=UUID.randomUUID().toString(),followUpAnchorMailRecordId=10))
        val draft=save(content,MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND)); val entered=CountDownLatch(1); val release=CountDownLatch(1)
        `when`(delivery.send(anyValue(account()),anyValue(composed()))).thenAnswer { entered.countDown(); check(release.await(15,TimeUnit.SECONDS)); successful() }
        val pool=Executors.newFixedThreadPool(2)
        try {
            val sending=pool.submit<PendingMailSendResult> { conversation(draft) }; assertTrue(entered.await(15,TimeUnit.SECONDS))
            assertEquals("DELIVERY_IN_PROGRESS",drafts.get("op",draft.id!!).sendAttemptStatus)
            assertEquals("DRAFT_SEND_IN_PROGRESS",assertThrows(MailReplyDraftException::class.java) { drafts.discard("op",draft.id!!,1) }.code)
            val newer=pool.submit<MailReplyDraftDetail> { save(content.copy(html="<p>New during SMTP</p>",text="New during SMTP",context=content.context.copy(requestId=UUID.randomUUID().toString())),draft.target!!,version=1) }.get(15,TimeUnit.SECONDS)
            assertEquals(409,assertThrows(ResponseStatusException::class.java) { conversation(newer) }.status.value())
            release.countDown(); assertEquals("SENT",sending.get(15,TimeUnit.SECONDS).sendStatus)
            val retained=drafts.get("op",draft.id!!); assertActive(retained,2); assertEquals("New during SMTP",retained.content!!.text); assertNull(retained.sendAttemptId)
            assertEquals("Hello",jdbc.queryForObject("SELECT body FROM mail_record WHERE mail_type='MANUAL_RICH_REPLY'",String::class.java))
            assertEquals("DRAFT_VERSION_CONFLICT",assertThrows(MailReplyDraftException::class.java) { conversation(draft) }.code)
            assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt"))
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun `unbound matching dedup closes only the submitted version without SMTP`() {
        assertEquals("SENT",pending.sendManualRichReply(100,null,original.subject,original.html,original.text,"op",safetyWarningConfirmed=true).sendStatus)
        val draft=save(); assertEquals("SENT",send(draft).sendStatus); assertEquals(1,smtpCount()); assertEquals(MailReplyDraftState.SENT,drafts.get("op",draft.id!!).state)
        val reopened=drafts.save("op",target,MailReplyDraftSaveRequest(2,original.copy(html="<p>New</p>",text="New"),reopen=true))
        assertEquals("DRAFT_VERSION_CONFLICT",assertThrows(MailReplyDraftException::class.java) { send(draft) }.code); assertActive(reopened,3)
    }
    @Test fun `completed request with an unbound different snapshot still hits original fingerprint collision`() {
        val request=UUID.randomUUID().toString()
        pending.sendConversationManualRichReply(1,request,null,10,original.subject,original.html,original.text,"op",safetyWarningConfirmed=true)
        val changed=original.copy(html="<p>Different</p>",text="Different",context=MailReplyDraftContext(requestId=request,followUpAnchorMailRecordId=10))
        val draft=save(changed,MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND))
        assertTrue(assertThrows(IllegalArgumentException::class.java) { conversation(draft) }.message!!.contains("full hash mismatch"))
        assertActive(draft); assertEquals(1,smtpCount()); assertNull(drafts.get("op",draft.id!!).sendAttemptId)
    }
    @Test fun `template validation safety cancellation and unavailable attachments retain unclaimed draft`() {
        val bad=save(original.copy(text="Hello ${'$'}{unknown}",html="<p>Hello ${'$'}{unknown}</p>"))
        assertThrows(IllegalArgumentException::class.java) { send(bad) }; assertActive(bad)
        val risky=save(original.copy(text="Please send your CV",html="<p>Please send your CV</p>"),target,version=1)
        assertThrows(ManualSendSafetyBlockedException::class.java) { pending.sendManualRichReply(100,null,risky.content!!.subject,risky.content!!.html,risky.content!!.text,"op",authenticatedUsername="op",draftRef=ref(risky)) }
        assertActive(risky,2); assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
    }
    @Test fun `draft change between entry validation and claim is rejected atomically`() {
        val draft=save()
        `when`(suppression.isSuppressed(anyString())).thenAnswer {
            save(original.copy(text="Other window",html="<p>Other window</p>"),version=1); false
        }
        assertEquals("DRAFT_VERSION_CONFLICT",assertThrows(MailReplyDraftException::class.java) { send(draft) }.code)
        assertActive(draft,2); assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
    }
    @Test fun `draft binding SQL failure rolls back the claim before SMTP`() {
        val draft=save()
        jdbc.execute("CREATE TRIGGER reject_draft_sent BEFORE UPDATE ON mailbox_reply_draft FOR EACH ROW BEGIN IF NEW.send_attempt_id IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected binding failure'; END IF; END")
        assertThrows(org.springframework.dao.DataAccessException::class.java) { send(draft) }
        assertActive(draft); assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt")); assertNull(drafts.get("op",draft.id!!).sendAttemptId)
    }
    @Test fun `RAG stale corpus validation is not bypassed by a matching saved snapshot`() {
        val content=original.copy(context=MailReplyDraftContext(qa=MailReplyDraftQa(listOf("fact-one"),"stale","Hello")))
        val draft=save(content); `when`(ragKnowledge.fingerprint()).thenReturn("current")
        val ex=assertThrows(ResponseStatusException::class.java) {
            pending.sendManualRichReply(100,null,content.subject,content.html,content.text,"op",ragFactCodes=listOf("fact-one"),
                ragCorpusFingerprint="stale",edited=false,authenticatedUsername="op",draftRef=ref(draft))
        }
        assertEquals("RAG_CORPUS_STALE",ex.reason); assertActive(draft); assertEquals(0,smtpCount()); assertEquals(0L,count("mail_send_attempt"))
    }
    @Test fun `fully matching conversation dedup closes an unbound draft without another SMTP`() {
        val request=UUID.randomUUID().toString()
        pending.sendConversationManualRichReply(1,request,null,10,original.subject,original.html,original.text,"op",safetyWarningConfirmed=true)
        val content=original.copy(context=MailReplyDraftContext(requestId=request,followUpAnchorMailRecordId=10))
        val draft=save(content,MailReplyDraftTarget(1,MailReplyDraftKind.OUTBOUND))
        assertEquals("SENT",conversation(draft).sendStatus); assertEquals(1,smtpCount()); assertEquals(MailReplyDraftState.SENT,drafts.get("op",draft.id!!).state)
    }
    @Test fun `raw template draft is rendered with final sender before SMTP`() {
        val template=save(original.copy(text="Hello ${'$'}{senderName}",html="<p>Hello ${'$'}{senderName}</p>"))
        assertEquals("SENT",send(template).sendStatus)
        val sent=mockingDetails(delivery).invocations.single { it.method.name=="send" }.arguments[1] as ComposedMail
        assertEquals("Hello Sender",sent.text); assertFalse(sent.body.contains("${'$'}{")); assertEquals(MailReplyDraftState.SENT,drafts.get("op",template.id!!).state)
    }
    @Test fun `real owned attachments are ordered and missing originals do not claim or close`() {
        val upload=attachments.upload(1,"op","proof.txt","text/plain","proof".byteInputStream())
        val field=MailReplyDraftAttachments(items=listOf(MailReplyDraftAttachmentItem("one","ready",upload.id)))
        val draft=save(original.copy(context=MailReplyDraftContext(outboundAttachmentDraft=field)))
        assertEquals("SENT",send(draft,ids=listOf(upload.id)).sendStatus); assertEquals(1,smtpCount())
        val foreign=attachments.upload(1,"other","foreign.txt","text/plain","foreign".byteInputStream())
        assertThrows(MailReplyDraftException::class.java) { save(original.copy(context=MailReplyDraftContext(outboundAttachmentDraft=MailReplyDraftAttachments(items=listOf(MailReplyDraftAttachmentItem("foreign","ready",foreign.id))))),target,version=2) }
        val second=save(original.copy(context=MailReplyDraftContext(outboundAttachmentDraft=field)),target.copy(processingId=101))
        jdbc.update("DELETE FROM outbound_mail_attachment WHERE id=?",upload.id)
        assertThrows(OutboundAttachmentException::class.java) { send(second,processing=101,ids=listOf(upload.id)) }; assertActive(second); assertEquals(1,smtpCount())
    }
    private fun meetingDraft(): Triple<MailReplyDraftDetail,MeetingInput,String> {
        val input=MeetingInput(zoneId="Asia/Shanghai",startLocal="2026-10-12T10:00",endLocal="2026-10-12T10:30",
            zoomUrl="https://zoom.us/j/123456789",generatedAt="2026-10-08T01:00:00Z")
        val preview=meetings.preview(100,MeetingPreviewRequest(contactId=1,meeting=input))
        val content=MailReplyDraftContent("Meeting",preview.htmlBody,preview.textBody,MailReplyDraftContext(meetingAccountCode="draft-send",
            meeting=MailReplyDraftMeeting(input=MailReplyDraftMeetingInput(input.zoneId,input.startLocal,input.endLocal,input.zoomUrl,input.generatedAt),
                preview=MailReplyDraftMeetingPreview(attachment=MailReplyDraftCalendarAttachment(sha256=preview.attachment.sha256)),state="ready")))
        return Triple(save(content),input,preview.attachment.sha256)
    }
    @Test fun `meeting and draft success commit together while stale preview retains the draft`() {
        val (draft,input,sha)=meetingDraft()
        assertEquals("DRAFT_CONTENT_MISMATCH",assertThrows(MailReplyDraftException::class.java) { send(draft,meeting=input,sha="bad") }.code); assertActive(draft); assertEquals(0,smtpCount())
        assertEquals("SENT",send(draft,meeting=input,sha=sha).sendStatus); assertEquals(1L,count("meeting_calendar_event")); assertEquals(MailReplyDraftState.SENT,drafts.get("op",draft.id!!).state)
    }
    @Test fun `draft close SQL error rolls back SENT attempt record and meeting then records unknown without retry`() {
        val (draft,input,sha)=meetingDraft()
        jdbc.execute("CREATE TRIGGER reject_draft_sent BEFORE UPDATE ON mailbox_reply_draft FOR EACH ROW BEGIN IF NEW.state='SENT' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected draft close SQL failure'; END IF; END")
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(draft,meeting=input,sha=sha) }.status.value())
        assertActive(draft); assertEquals(0L,count("mail_record","mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'")); assertEquals(0L,count("meeting_calendar_event")); assertEquals("DELIVERY_UNKNOWN",drafts.get("op",draft.id!!).sendAttemptStatus)
        jdbc.execute("DROP TRIGGER reject_draft_sent")
        assertEquals(409,assertThrows(ResponseStatusException::class.java) { send(draft,meeting=input,sha=sha) }.status.value()); assertEquals(1,smtpCount())
    }
    @Test fun `audit exception after durable success never revives closed content or redelivers`() {
        doThrow(IllegalStateException("injected audit failure")).`when`(audit).record(anyString(),anyLong(),
            anyValue(com.weibo.talentintroduction.audit.domain.OperatorActionType.SEND_MANUAL_RICH_REPLY),any(),any(),any(),any(),any(),any(),any())
        val draft=save(); assertEquals("SENT",send(draft).sendStatus); assertEquals("SENT",send(draft).sendStatus)
        assertEquals(MailReplyDraftState.SENT,drafts.get("op",draft.id!!).state); assertEquals(1,smtpCount()); assertEquals(1L,count("mail_record","mail_type='MANUAL_RICH_REPLY' AND send_status='SENT'"))
    }
    @Test fun `actual HTTP sending smoke closes persists and replays across independent sessions`() {
        val root=Files.createTempDirectory("draft-send-http")
        val tomcat=Tomcat(); tomcat.setBaseDir(root.toString()); tomcat.setPort(0); tomcat.connector
        val context=tomcat.addContext("",root.toString()); val app=AnnotationConfigWebApplicationContext()
        app.servletContext=context.servletContext; app.register(DraftSendMvc::class.java)
        app.addBeanFactoryPostProcessor {
            it.registerSingleton("draftController",MailReplyDraftController(drafts))
            it.registerSingleton("conversationController",MailboxConversationController(mock(MailboxConversationService::class.java),
                mock(ExpertFollowService::class.java),mock(ExpertRepliedDismissalService::class.java),pending,mock(MailboxSuspensionService::class.java)))
        }
        val dispatcher=Tomcat.addServlet(context,"dispatcher",DispatcherServlet(app)); dispatcher.loadOnStartup=1; context.addServletMappingDecoded("/","dispatcher")
        val login=object: HttpServlet() { override fun doGet(req: HttpServletRequest,res: HttpServletResponse) { req.getSession(true).setAttribute(AuthSessionKeys.USERNAME,req.getParameter("user")); res.writer.write("ok") } }
        Tomcat.addServlet(context,"session",login); context.addServletMappingDecoded("/test-session","session")
        val mapper=ObjectMapper().registerModule(KotlinModule.Builder().build())
        try {
            tomcat.start(); val base="http://127.0.0.1:${tomcat.connector.localPort}"
            fun session(user: String): String { val c=URL("$base/test-session?user=$user").openConnection() as HttpURLConnection; c.inputStream.close(); val cookie=c.getHeaderField("Set-Cookie").substringBefore(';'); c.disconnect(); return cookie }
            fun request(method: String,path: String,cookie: String,body: String?=null): Pair<Int,com.fasterxml.jackson.databind.JsonNode> {
                val c=URL(base+path).openConnection() as HttpURLConnection; c.requestMethod=method; c.connectTimeout=5000; c.readTimeout=5000; c.setRequestProperty("Cookie",cookie)
                if (body!=null) { c.doOutput=true; c.setRequestProperty("Content-Type","application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
                val status=c.responseCode; val result=mapper.readTree((if (status<400) c.inputStream else c.errorStream).use { it.readBytes() }); c.disconnect()
                println("DRAFT_SEND_RUNTIME_SMOKE $method $path -> $status ${result["state"] ?: result["sendStatus"] ?: result["code"]}"); return status to result
            }
            val a=session("op"); val b=session("other"); val requestId=UUID.randomUUID().toString()
            val content=original.copy(context=MailReplyDraftContext(requestId=requestId,followUpAnchorMailRecordId=10))
            val saved=request("PUT","/api/mail/mailbox/drafts/target?contactId=1&kind=OUTBOUND",a,mapper.writeValueAsString(MailReplyDraftSaveRequest(0,content)))
            assertEquals(200,saved.first); val id=saved.second["id"].asLong()
            val payload=mapper.writeValueAsString(mapOf("requestId" to requestId,"anchorMailRecordId" to 10,"subject" to content.subject,"htmlBody" to content.html,"textBody" to content.text,"draftRef" to mapOf("id" to id,"version" to 1)))
            assertEquals(404,request("POST","/api/mail/mailbox/conversations/1/manual-rich-reply",b,payload).first)
            assertEquals(200,request("POST","/api/mail/mailbox/conversations/1/manual-rich-reply",a,payload).first)
            val restored=request("GET","/api/mail/mailbox/drafts/$id",session("op")); assertEquals("SENT",restored.second["state"].asText()); assertEquals(2L,restored.second["version"].asLong()); assertTrue(restored.second["content"].isNull)
            assertEquals(200,request("POST","/api/mail/mailbox/conversations/1/manual-rich-reply",session("op"),payload).first)
            assertEquals(0L,request("GET","/api/mail/mailbox/drafts",a).second["total"].asLong()); assertEquals(1,smtpCount()); assertEquals(1L,count("mail_send_attempt","status='SENT'"))
            println("DRAFT_SEND_RUNTIME_SMOKE complete; actual HTTP controller, Session identity, MySQL claim/CAS/transaction, SMTP calls=1, SENT/version=2/null content")
        } finally { tomcat.stop(); tomcat.destroy(); app.close(); Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
}

@Configuration
@EnableWebMvc
class DraftSendMvc

/** Isolated MySQL for each integration class; no Docker skip and no use of a developer's database. */
internal class DraftSendMysql {
    private class Mysql: MySQLContainer<Mysql>("mysql:8.0.36")
    private val mysql=Mysql().withDatabaseName("talent_introduction").withUsername("test").withPassword("test")
        .withCommand("--log-bin-trust-function-creators=1")
    fun properties(registry: DynamicPropertyRegistry) {
        check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for draft send MySQL integration" }
        mysql.start()
        registry.add("spring.datasource.url",mysql::getJdbcUrl); registry.add("spring.datasource.username",mysql::getUsername); registry.add("spring.datasource.password",mysql::getPassword)
    }
    fun stop() { mysql.stop() }
}
