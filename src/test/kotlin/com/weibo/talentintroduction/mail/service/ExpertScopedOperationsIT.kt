package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.service.OperatorStatusReconcileService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonQueryService
import com.weibo.talentintroduction.expert.controller.*
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.*
import com.weibo.talentintroduction.mail.controller.InboundMailSummaryController
import com.weibo.talentintroduction.campaign.controller.MeetingCalendarController
import com.weibo.talentintroduction.campaign.controller.MeetingCalendarExceptionHandler
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.campaign.repository.MeetingCalendarEventRepository
import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
import com.weibo.talentintroduction.mail.controller.MeetingConfirmationController
import com.weibo.talentintroduction.mail.repository.MailReplyDraftRepository
import com.weibo.talentintroduction.mail.repository.MailReviewDraftRepository
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime

/** Real current grants, ownership rows, tag writes and profile CAS through production MVC admission. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@SpringBootTest(classes = [ExpertScopedOperationsIT.Config::class], properties = [
    "spring.flyway.placeholder-replacement=false", "talent-introduction.auth.enabled=true",
    "talent-introduction.scheduling.enabled=false", "talent-introduction.mail-queue.enabled=false"
])
@AutoConfigureMockMvc
class ExpertScopedOperationsIT {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(com.weibo.talentintroduction.config.LlmProperties::class)
    @EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.mail.repository",
        "com.weibo.talentintroduction.campaign.repository", "com.weibo.talentintroduction.qa.repository"])
    @Import(AuthWebConfig::class, GlobalExceptionHandler::class, ExpertMailPermissionGuard::class,
        ExpertMailReadAccessService::class, ExpertIndexController::class, ExpertManualProfileController::class,
        InboundMailSummaryController::class, ExpertManualProfileService::class,
        ExpertProfileIdentityResolver::class, InboundMailTagService::class,
        MeetingCalendarController::class, MeetingCalendarExceptionHandler::class, MeetingCalendarService::class,
        MeetingCalendarEventRepository::class, MailReplyDraftController::class, MailReplyDraftService::class,
        MailReplyDraftRepository::class, MailReviewDraftService::class, MailReviewDraftRepository::class,
        MailboxConversationRepository::class, MailContentService::class, MailPlaceholderService::class,
        MeetingConfirmationController::class, MeetingConfirmationService::class,
        com.weibo.talentintroduction.mail.controller.UnmatchedInboundMailController::class)
    class Config

    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("scoped_operations").withUsername("test").withPassword("test")
        @JvmStatic @DynamicPropertySource fun database(registry: DynamicPropertyRegistry) {
            mysql.start()
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var tagService: InboundMailTagService
    @MockBean private lateinit var auth: AuthService
    @MockBean private lateinit var search: ExpertSearchService
    @MockBean private lateinit var writer: ExpertIndexWriterService
    @MockBean private lateinit var statusSync: CandidateOperatorStatusSyncService
    @MockBean private lateinit var revalidation: ExpertRevalidationService
    @MockBean private lateinit var tasks: TaskExecutionService
    @MockBean private lateinit var progress: TaskProgressStore
    @MockBean private lateinit var filters: EligibilityFilterService
    @MockBean private lateinit var introduction: IntroductionMailComposer
    @MockBean private lateinit var reconcile: OperatorStatusReconcileService
    @MockBean private lateinit var persons: DiscoveryPersonQueryService
    @MockBean private lateinit var qa: QaMatchService
    @MockBean private lateinit var delivery: MailDeliveryService
    @MockBean private lateinit var attachments: OutboundAttachmentService
    @MockBean private lateinit var translation: MailTranslationService
    @MockBean private lateinit var senderAccounts: MailSenderAccountService
    @MockBean private lateinit var templates: com.weibo.talentintroduction.template.service.MailComposeTemplateService
    @MockBean private lateinit var variables: MailVariableService
    @MockBean private lateinit var unmatched: UnmatchedInboundMailService
    @MockBean private lateinit var aliases: com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
    @MockBean private lateinit var pending: PendingMailOperationService
    @MockBean private lateinit var logs: com.weibo.talentintroduction.audit.service.OperatorActionLogService
    @MockBean private lateinit var autoPreview: AutoReplyPreviewService
    @MockBean private lateinit var aiDraft: com.weibo.talentintroduction.llm.service.AiReplyDraftService
    @MockBean private lateinit var aiPreview: com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
    @MockBean private lateinit var aiBuilder: com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
    @MockBean private lateinit var aiTraining: com.weibo.talentintroduction.llm.service.AiTrainingQaService
    @MockBean private lateinit var aiContext: com.weibo.talentintroduction.llm.service.AiReplyContextService
    @MockBean private lateinit var aiAudit: com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
    @MockBean(name = "aiReplyStreamExecutor") private lateinit var streamExecutor: java.util.concurrent.ExecutorService
    @MockBean(name = "aiReplyStreamScheduler") private lateinit var streamScheduler: java.util.concurrent.ScheduledExecutorService
    @Autowired private lateinit var inboundRepository: com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
    @Autowired private lateinit var drafts: MailReplyDraftService
    @Autowired private lateinit var draftRepository: MailReplyDraftRepository
    @Autowired private lateinit var reviews: MailReviewDraftService

    private val mapper = jacksonObjectMapper()
    private fun session(user: String = "user1") = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, user) }
    private fun tags() = jdbc.queryForObject("SELECT COUNT(*) FROM inbound_mail_tag", Long::class.java)!!
    private fun profileRows() = jdbc.queryForObject("SELECT COUNT(*) FROM expert_manual_profile", Long::class.java)!!
    private fun tagBody(key: String = "expert-a", level: String = "CANDIDATE") =
        """{"orcidId":"$key","level":"$level","tag":"new-tag"}"""
    private fun profileBody(key: String = "expert-a", version: Long = 0, level: String = "CANDIDATE") =
        """{"expertId":"$key","level":"$level","group":"identity","expectedVersion":$version,"values":{"talentCode":"0042","chineseName":null,"birthDateText":null,"documentNumber":null,"sourceCountry":null}}"""

    @BeforeEach fun seed() {
        listOf("mailbox_review_draft_event", "mailbox_review_draft", "mailbox_reply_draft", "meeting_calendar_event",
            "expert_manual_profile", "inbound_mail_tag", "expert_mail_read_access", "inbound_mail_processing",
            "mail_record", "expert_contact", "campaign", "mail_sender_account").forEach { jdbc.update("DELETE FROM $it") }
        jdbc.update("""INSERT INTO mail_sender_account(account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password)
            VALUES('scoped','sender@example.test','Fixture','127.0.0.1',1,'fixture','unused','127.0.0.1',1,'fixture','unused')""")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(1,'SCOPED','Scoped',(SELECT id FROM mail_sender_account WHERE account_code='scoped'))")
        jdbc.update("INSERT INTO campaign(id,campaign_code,campaign_name,sender_account_id) VALUES(2,'SCOPED-SIBLING','Sibling',(SELECT id FROM mail_sender_account WHERE account_code='scoped'))")
        for (id in 1L..4L) {
            val key = if (id == 4L) "EXPERT-A" else "expert-${('a'.code + id.toInt() - 1).toChar()}"
            jdbc.update("""INSERT INTO expert_contact(id,campaign_id,orcid_id,expert_email,expert_name,current_status,current_index_level)
                VALUES(?,?,?,?,?,'NEW','CANDIDATE')""", id, if (id == 4L) 2L else 1L, key, "expert$id@example.test", "Expert $id")
            jdbc.update("""INSERT INTO inbound_mail_processing(id,sender_account_code,folder,uid_validity,imap_uid,message_id,from_email,subject,body,cleaned_body,received_at,process_status,process_reason,expert_contact_id)
                VALUES(?,'scoped','INBOX',1,?,?,?,'Subject','Body','Body',NOW(3),'MANUAL_REVIEW','Scoped fixture',?)""", id, id, "scoped-$id", "expert$id@example.test", id)
        }
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at,access_level) VALUES('user1',1,'admin',NOW(3),'FULL'),('user1',2,'admin',NOW(3),'READ')")
        listOf("admin", "user1").forEach { user ->
            Mockito.`when`(auth.findUser(user)).thenReturn(AdminUser(username = user, passwordHash = "unused",
                mustChangePassword = false, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
        }
        Mockito.`when`(search.findByOrcidId("expert-a", ExpertIndexLevel.CANDIDATE)).thenReturn(
            ExpertProfile(esDocId = "doc-a", orcidId = "expert-a", email = "expert1@example.test", givenNames = "Expert",
                familyNames = "A", country = "US", keyword = null, employment = null, tags = listOf("new-tag")))
        Mockito.`when`(writer.addTag("doc-a", "new-tag", ExpertIndexLevel.CANDIDATE)).thenReturn(true)
        Mockito.`when`(writer.removeTag("doc-a", "new-tag", ExpertIndexLevel.CANDIDATE)).thenReturn(true)
        Mockito.`when`(qa.matchAllRuleIds("Body")).thenReturn(emptyList())
    }

    @Test fun `expert handlers require current FULL identity and current layer before ES`() {
        for (key in listOf("expert-b", "expert-c", "unknown")) {
            for (path in listOf("add", "remove")) mvc.perform(post("/api/experts/tags/$path").session(session())
                .contentType(MediaType.APPLICATION_JSON).content(tagBody(key))).andExpect(status().isForbidden)
            mvc.perform(get("/api/experts/profile").session(session()).param("orcidId", key)).andExpect(status().isForbidden)
        }
        for (level in listOf("RAW", "APPLICATION")) {
            mvc.perform(post("/api/experts/tags/add").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content(tagBody(level = level))).andExpect(status().isForbidden)
        }
        Mockito.verifyNoInteractions(search, writer)
        mvc.perform(post("/api/experts/tags/add").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(tagBody())).andExpect(status().isOk).andExpect(jsonPath("$.success").value(true))
        mvc.perform(post("/api/experts/tags/remove").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(tagBody())).andExpect(status().isOk)
        mvc.perform(get("/api/experts/profile").session(session()).param("orcidId", "expert-a"))
            .andExpect(status().isOk).andExpect(jsonPath("$.tags[0]").value("new-tag"))
        jdbc.update("UPDATE expert_contact SET current_index_level='APPLICATION' WHERE id=1")
        Mockito.clearInvocations(search, writer)
        mvc.perform(get("/api/experts/profile").session(session()).param("orcidId", "expert-a"))
            .andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(search, writer)
    }

    @Test fun `profile FULL normalized key preserves CAS and session audit without granting sibling mailbox`() {
        mvc.perform(get("/api/experts/manual-profile").session(session()).param("expertId", " EXPERT-A ")
            .param("level", "CANDIDATE")).andExpect(status().isOk).andExpect(jsonPath("$.version").value(0))
        mvc.perform(patch("/api/experts/manual-profile").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(profileBody())).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        assertEquals("user1", jdbc.queryForObject("SELECT updated_by FROM expert_manual_profile WHERE expert_key='EXPERT-A'", String::class.java))
        mvc.perform(patch("/api/experts/manual-profile").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(profileBody())).andExpect(status().isConflict)
        mvc.perform(get("/api/experts/manual-profile").session(session("admin")).param("expertId", "expert-a")
            .param("level", "RAW")).andExpect(status().isOk).andExpect(jsonPath("$.version").value(1))
        for (key in listOf("expert-b", "expert-c")) {
            mvc.perform(get("/api/experts/manual-profile").session(session()).param("expertId", key).param("level", "CANDIDATE"))
                .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("MAIL_WRITE_FORBIDDEN"))
            mvc.perform(patch("/api/experts/manual-profile").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content(profileBody(key))).andExpect(status().isForbidden)
        }
        mvc.perform(patch("/api/experts/manual-profile").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(profileBody(version = 1, level = "RAW"))).andExpect(status().isForbidden)
        assertEquals(1, profileRows())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM expert_mail_read_access WHERE expert_contact_id=4", Int::class.java))
        mvc.perform(post("/api/inbound-summary/mails/4/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"label":"sibling"}""")).andExpect(status().isForbidden)
        assertEquals(0, tags())
    }

    @Test fun `mail tag operations resolve actual processing and tag ownership and ignore forged operator`() {
        for (id in listOf(2, 3, 4)) {
            mvc.perform(post("/api/inbound-summary/mails/$id/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"label":"forbidden","operatorName":"admin"}""")).andExpect(status().isForbidden)
            mvc.perform(post("/api/inbound-summary/mails/$id/tags/auto").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"operatorName":"admin"}""")).andExpect(status().isForbidden)
        }
        assertEquals(0, tags())
        Mockito.verifyNoInteractions(qa)
        mvc.perform(post("/api/inbound-summary/mails/1/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"label":"<img src=x onerror=alert(1)>","operatorName":"admin"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.tags[0].label").value("<img src=x onerror=alert(1)>"))
        assertEquals("user1", jdbc.queryForObject("SELECT created_by FROM inbound_mail_tag", String::class.java))
        val own = jdbc.queryForObject("SELECT id FROM inbound_mail_tag", Long::class.java)!!
        val foreign = tagService.addCustomTag(2, "foreign", "admin").id!!
        mvc.perform(delete("/api/inbound-summary/tags/$foreign").session(session())).andExpect(status().isForbidden)
        assertEquals(2, tags())
        mvc.perform(delete("/api/inbound-summary/tags/$own").session(session())).andExpect(status().isOk)
        mvc.perform(post("/api/inbound-summary/mails/1/tags/auto").session(session())).andExpect(status().isOk)
        Mockito.verify(qa).matchAllRuleIds("Body")
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        mvc.perform(post("/api/inbound-summary/mails/1/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"label":"revoked"}""")).andExpect(status().isForbidden)
        assertEquals(1, tags())
    }

    @Test fun `automatic and manual QA tags preserve session audit and unbound inbound is forbidden`() {
        val ruleId = jdbc.queryForObject("SELECT MIN(id) FROM qa_rule WHERE enabled=true", Long::class.java)!!
        Mockito.`when`(qa.matchAllRuleIds("Body")).thenReturn(listOf(ruleId))
        mvc.perform(post("/api/inbound-summary/mails/1/tags/auto").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"operatorName":"admin"}""")).andExpect(status().isOk)
            .andExpect(jsonPath("$.addedCount").value(1)).andExpect(jsonPath("$.tags[0].tagType").value("QA"))
        assertEquals("user1", jdbc.queryForObject("SELECT created_by FROM inbound_mail_tag", String::class.java))
        val tagId = jdbc.queryForObject("SELECT id FROM inbound_mail_tag", Long::class.java)!!
        mvc.perform(delete("/api/inbound-summary/tags/$tagId").session(session())).andExpect(status().isOk)
        mvc.perform(post("/api/inbound-summary/mails/1/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"qaRuleId":$ruleId,"operatorName":"admin"}""")).andExpect(status().isOk)
        jdbc.update("UPDATE inbound_mail_processing SET expert_contact_id=NULL WHERE id=3")
        mvc.perform(post("/api/inbound-summary/mails/3/tags").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"label":"unbound"}""")).andExpect(status().isForbidden)
        mvc.perform(post("/api/inbound-summary/mails/3/tags/auto").session(session())).andExpect(status().isForbidden)
        assertEquals(1, tags())
    }

    @Test fun `admin and background tags retain access while global endpoints stay closed`() {
        mvc.perform(post("/api/inbound-summary/mails/3/tags").session(session("admin")).contentType(MediaType.APPLICATION_JSON)
            .content("""{"label":"admin-tag"}""")).andExpect(status().isOk)
        tagService.addCustomTag(3, "background", "SYSTEM")
        assertEquals(2, tags())
        mvc.perform(get("/api/inbound-summary/tags/options").session(session())).andExpect(status().isOk)
        mvc.perform(get("/api/experts").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/inbound-summary/mails").session(session())).andExpect(status().isForbidden)
        mvc.perform(post("/api/experts/reindex-applications").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/experts/profile").param("orcidId", "expert-a")).andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(delivery, tasks, persons)
    }

    private fun calendarBody(contact: Long = 1, version: String? = null): String = mapper.writeValueAsString(
        linkedMapOf<String, Any?>("contactId" to contact, "startBeijing" to "2026-11-02T10:00",
            "endBeijing" to "2026-11-02T10:30", "note" to "Scoped calendar").apply {
            if (version != null) put("expectedUpdatedAt", version)
        })

    @Test fun `calendar current FULL body query and actual event ownership preserve CAS with no send`() {
        for (contact in listOf(2L, 3L, 4L)) {
            mvc.perform(post("/api/meeting-calendar/events").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content(calendarBody(contact))).andExpect(status().isForbidden)
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_calendar_event", Int::class.java))
        mvc.perform(get("/api/meeting-calendar/events").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/summaries").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/summaries?contactIds=1,2").session(session())).andExpect(status().isForbidden)
        val created = mapper.readTree(mvc.perform(post("/api/meeting-calendar/events").session(session())
            .contentType(MediaType.APPLICATION_JSON).content(calendarBody())).andExpect(status().isCreated)
            .andExpect(jsonPath("$.startUtc").value("2026-11-02T02:00:00Z")).andReturn().response.contentAsString)
        val id = created["id"].asLong()
        val version = created["updatedAt"].asText()
        mvc.perform(get("/api/meeting-calendar/events/$id").session(session("admin"))).andExpect(status().isOk)
        mvc.perform(get("/api/meeting-calendar/events?contactId=1").session(session())).andExpect(status().isOk)
        mvc.perform(get("/api/meeting-calendar/summaries?contactIds=1").session(session()))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].activeCount").value(1))
        val updated = mapper.readTree(mvc.perform(put("/api/meeting-calendar/events/$id").session(session())
            .contentType(MediaType.APPLICATION_JSON).content(calendarBody(version = version).replace("Scoped calendar", "Changed calendar"))).andExpect(status().isOk)
            .andReturn().response.contentAsString)
        mvc.perform(put("/api/meeting-calendar/events/$id").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(calendarBody(version = version))).andExpect(status().isConflict)
        mvc.perform(post("/api/meeting-calendar/events/$id/cancel").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(mapOf("expectedUpdatedAt" to updated["updatedAt"].asText()))))
            .andExpect(status().isOk).andExpect(jsonPath("$.status").value("CANCELLED"))
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        mvc.perform(get("/api/meeting-calendar/events/$id").session(session())).andExpect(status().isForbidden)
        mvc.perform(put("/api/meeting-calendar/events/$id").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content(calendarBody(version = version))).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(delivery, attachments)
    }

    @Test fun `personal drafts filter FULL and real target in SQL before page count and summaries then revoke all entry points`() {
        val content = MailReplyDraftContent(subject = "Scoped", html = "<p>Body</p>", text = "Body")
        val now = LocalDateTime.now()
        for (contact in listOf(2L, 3L, 4L, 1L)) draftRepository.insert("user1",
            MailReplyDraftTarget(contact, MailReplyDraftKind.OUTBOUND), content,
            MailReplyDraftContextCodec.serialize(content.context), now.plusSeconds(contact))
        draftRepository.insert("admin", MailReplyDraftTarget(3, MailReplyDraftKind.OUTBOUND), content,
            MailReplyDraftContextCodec.serialize(content.context), now)
        val own = drafts.list("user1", null, null, 0, 1)
        assertEquals(1L, own.total)
        assertEquals(1L, own.items.single().target.contactId)
        assertTrue(drafts.list("user1", null, null, 1, 1).items.isEmpty())
        val summaries = drafts.summaries("user1", listOf(1, 2, 3, 4), null)
        assertEquals(1L, summaries.total)
        assertEquals(listOf(1L, 0L, 0L, 0L), summaries.items.map { it.count })
        assertEquals(1L, drafts.list("admin", null, null, 0, 20).total)
        val t = MailReplyDraftTarget(1, MailReplyDraftKind.INBOUND, 1, "scoped")
        val saved = drafts.save("user1", t, MailReplyDraftSaveRequest(0, content))
        assertEquals(2L, drafts.list("user1", null, null, 0, 20).total)
        jdbc.update("UPDATE inbound_mail_processing SET expert_contact_id=2 WHERE id=1")
        assertEquals(1L, drafts.list("user1", null, null, 0, 20).total)
        assertThrows(MailReplyDraftException::class.java) { drafts.get("user1", saved.id!!) }
        jdbc.update("UPDATE inbound_mail_processing SET expert_contact_id=1 WHERE id=1")
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        assertEquals(0L, drafts.list("user1", null, null, 0, 20).total)
        assertEquals(0L, drafts.summaries("user1", listOf(1, 2), null).total)
        val ref = MailReplyDraftRef(saved.id!!, saved.version)
        for (action in listOf<() -> Unit>(
            { drafts.get("user1", saved.id!!) }, { drafts.getTarget("user1", t) },
            { drafts.save("user1", t, MailReplyDraftSaveRequest(saved.version, content)) },
            { drafts.discard("user1", saved.id!!, saved.version) },
            { drafts.completedBinding("user1", ref, 1, 1, "scoped") },
            { drafts.validateConversationSend("user1", ref, 1, "scoped", "request", 1, "Scoped", "<p>Body</p>", "Body", emptyList()) }
        )) assertThrows(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException::class.java, action)
        mvc.perform(get("/api/mail/mailbox/drafts/${saved.id}").session(session())).andExpect(status().isForbidden)
        assertEquals(1L, draftRepository.findOwned("user1", saved.id!!)!!.version)
        Mockito.verifyNoInteractions(attachments, delivery)
    }

    @Test fun `READ review four states remain independently locked after FULL upgrade without automatic delivery`() {
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        fun draft() = reviews.create("user1", 1, java.util.UUID.randomUUID().toString(), "Subject", "<p>Body</p>") as MailReviewDraftDetail
        val unsubmitted = draft()
        val submitted = draft().let { reviews.submit("user1", it.id, it.version) as MailReviewDraftDetail }
        val returned = draft().let { reviews.submit("user1", it.id, it.version) as MailReviewDraftDetail }
            .let { reviews.returnDraft("admin", it.id, it.version, "Please revise") as MailReviewDraftDetail }
        val approved = draft().let { reviews.submit("user1", it.id, it.version) as MailReviewDraftDetail }
            .let { reviews.approve("admin", it.id, it.version, reviews.context("admin", it.id).currentToken) as MailReviewDraftDetail }
        jdbc.update("UPDATE expert_mail_read_access SET access_level='FULL' WHERE expert_contact_id=1")
        for (d in listOf(unsubmitted, submitted, returned, approved)) {
            assertEquals(d.state, (reviews.detail("user1", d.id) as MailReviewDraftDetail).state)
            assertTrue(reviews.events("user1", d.id).isNotEmpty())
        }
        for (action in listOf<() -> Unit>(
            { reviews.save("user1", approved.id, approved.version, "Changed", "<p>Changed</p>") },
            { reviews.softDelete("user1", approved.id, approved.version) },
            { reviews.comment("user1", approved.id, approved.version, "Changed") }
        )) assertEquals(409, assertThrows(MailReviewDraftException::class.java, action).status)
        assertEquals(MailReviewDraftState.SUBMITTED, (reviews.detail("user1", submitted.id) as MailReviewDraftDetail).state)
        reviews.save("user1", unsubmitted.id, unsubmitted.version, "Editable", "<p>Editable</p>")
        reviews.save("user1", returned.id, returned.version, "Revised", "<p>Revised</p>")
        reviews.confirmContext("admin", approved.id, approved.version, reviews.context("admin", approved.id).currentToken)
        assertNotNull(drafts.save("user1", MailReplyDraftTarget(1, MailReplyDraftKind.OUTBOUND),
            MailReplyDraftSaveRequest(0, MailReplyDraftContent(text = "New personal draft"))).id)
        Mockito.verifyNoInteractions(delivery)
    }

    @Test fun `meeting confirmation precise routes reject wrong owner and body equality before account or template access`() {
        val prefix = "/api/mail/unmatched-inbound"
        for (processing in listOf(2, 3, 4)) {
            mvc.perform(get("$prefix/$processing/meeting-confirmation/options?contactId=$processing").session(session()))
                .andExpect(status().isForbidden)
            mvc.perform(post("$prefix/$processing/meeting-confirmation/preview").session(session())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
        }
        mvc.perform(get("$prefix/1/meeting-confirmation/options?contactId=2").session(session())).andExpect(status().isForbidden)
        mvc.perform(post("$prefix/1/meeting-confirmation/preview").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"contactId":2,"meeting":{"zoneId":"Asia/Shanghai","startLocal":"2026-11-02T10:00","endLocal":"2026-11-02T10:30","zoomUrl":"https://zoom.test","generatedAt":"2026-10-30T08:00:00Z"}}"""))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("会议确认对象与来信处理不匹配")))
        Mockito.verifyNoInteractions(senderAccounts, templates, variables, delivery)
        mvc.perform(get("/api/mail/meeting-confirmation/time-zones?date=2026-11-02").session(session())).andExpect(status().isOk)
    }

    @Test fun `unmatched FULL detail never suggests candidates and resolve actions use authenticated session`() {
        for (id in listOf(2, 3, 4)) {
            mvc.perform(get("/api/mail/unmatched-inbound/$id").session(session())).andExpect(status().isForbidden)
        }
        jdbc.update("UPDATE inbound_mail_processing SET expert_contact_id=NULL WHERE id=4")
        mvc.perform(get("/api/mail/unmatched-inbound/4").session(session())).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(unmatched, logs, pending)
        Mockito.`when`(unmatched.getDetail(1)).thenReturn(inboundRepository.findById(1).get())
        Mockito.`when`(logs.search(null, 1, null, null, null, null, 50, 0)).thenReturn(emptyList<com.weibo.talentintroduction.audit.domain.OperatorActionLog>() to 0L)
        mvc.perform(get("/api/mail/unmatched-inbound/1").session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.record.id").value(1)).andExpect(jsonPath("$.contact.contactId").value(1))
            .andExpect(jsonPath("$.candidates").isEmpty)
        Mockito.verify(unmatched, Mockito.never()).suggestCandidates(Mockito.any(com.weibo.talentintroduction.mail.domain.InboundMailProcessing::class.java)
            ?: inboundRepository.findById(1).get())
        mvc.perform(post("/api/mail/unmatched-inbound/1/mark-resolved").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"resolvedBy":"admin","operatorName":"admin","note":"test"}""")).andExpect(status().isOk)
        Mockito.verify(pending).markResolved(1, "user1", "user1", "test")
        mvc.perform(post("/api/mail/unmatched-inbound/1/cancel-resolved").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"operatorName":"admin","note":"undo"}""")).andExpect(status().isOk)
        Mockito.verify(pending).cancelResolved(1, "user1", "undo")
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        Mockito.clearInvocations(unmatched, logs, pending)
        mvc.perform(get("/api/mail/unmatched-inbound/1").session(session())).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(unmatched, logs, pending)
    }

    @Test fun `personal draft exact HTTP routes retain owner CAS current FULL and admin access`() {
        val prefix = "/api/mail/mailbox/drafts"
        val target = "$prefix/target?contactId=1&kind=OUTBOUND"
        for (id in listOf(2, 3, 4)) {
            mvc.perform(get("$prefix/target?contactId=$id&kind=OUTBOUND").session(session())).andExpect(status().isForbidden)
            mvc.perform(put("$prefix/target?contactId=$id&kind=OUTBOUND").session(session()).contentType(MediaType.APPLICATION_JSON)
                .content("""{"expectedVersion":0,"content":{"text":"Denied"}}""")).andExpect(status().isForbidden)
        }
        mvc.perform(get(target).session(session())).andExpect(status().isOk).andExpect(jsonPath("$.version").value(0))
        val saved = mapper.readTree(mvc.perform(put(target).session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":0,"content":{"subject":"Subject","html":"<p>Body</p>","text":"Body"}}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString)
        val id = saved["id"].asLong()
        mvc.perform(get("$prefix/$id").session(session())).andExpect(status().isOk).andExpect(jsonPath("$.content.text").value("Body"))
        mvc.perform(get(prefix).session(session())).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
        mvc.perform(get("$prefix/summaries?contactIds=1,2").session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[1].count").value(0))
        mvc.perform(put(target).session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":0,"content":{"text":"Stale"}}""")).andExpect(status().isConflict)
        mvc.perform(get("$prefix/$id").session(session("admin"))).andExpect(status().isNotFound)
        mvc.perform(delete("$prefix/$id?expectedVersion=1").session(session())).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DISCARDED"))
        mvc.perform(put(target).session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"expectedVersion":2,"reopen":true,"content":{"text":"Reopened"}}""")).andExpect(status().isOk)
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        mvc.perform(delete("$prefix/$id?expectedVersion=3").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("$prefix/$id").session(session())).andExpect(status().isForbidden)
        mvc.perform(get(prefix).session(session())).andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
        Mockito.verifyNoInteractions(delivery, attachments)
    }
}
