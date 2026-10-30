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
    @EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.mail.repository",
        "com.weibo.talentintroduction.campaign.repository", "com.weibo.talentintroduction.qa.repository"])
    @Import(AuthWebConfig::class, GlobalExceptionHandler::class, ExpertMailPermissionGuard::class,
        ExpertMailReadAccessService::class, ExpertIndexController::class, ExpertManualProfileController::class,
        InboundMailSummaryController::class, ExpertManualProfileService::class,
        ExpertProfileIdentityResolver::class, InboundMailTagService::class)
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

    private val mapper = jacksonObjectMapper()
    private fun session(user: String = "user1") = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, user) }
    private fun tags() = jdbc.queryForObject("SELECT COUNT(*) FROM inbound_mail_tag", Long::class.java)!!
    private fun profileRows() = jdbc.queryForObject("SELECT COUNT(*) FROM expert_manual_profile", Long::class.java)!!
    private fun tagBody(key: String = "expert-a", level: String = "CANDIDATE") =
        """{"orcidId":"$key","level":"$level","tag":"new-tag"}"""
    private fun profileBody(key: String = "expert-a", version: Long = 0, level: String = "CANDIDATE") =
        """{"expertId":"$key","level":"$level","group":"identity","expectedVersion":$version,"values":{"talentCode":"0042","chineseName":null,"birthDateText":null,"documentNumber":null,"sourceCountry":null}}"""

    @BeforeEach fun seed() {
        listOf("expert_manual_profile", "inbound_mail_tag", "expert_mail_read_access", "inbound_mail_processing",
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
}
