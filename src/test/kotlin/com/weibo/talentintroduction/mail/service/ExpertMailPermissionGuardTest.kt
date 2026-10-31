package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.campaign.controller.MeetingCalendarController
import com.weibo.talentintroduction.campaign.service.MeetingCalendarService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.document.controller.ExpertDocumentBrowseController
import com.weibo.talentintroduction.document.controller.ExpertMaterialController
import com.weibo.talentintroduction.document.controller.ExpertMaterialHttpExceptionAdvice
import com.weibo.talentintroduction.document.service.ExpertDocumentBrowseService
import com.weibo.talentintroduction.document.service.MaterialArchiveFixture
import com.weibo.talentintroduction.mail.controller.MailboxAttachmentController
import com.weibo.talentintroduction.mail.controller.MeetingConfirmationController
import com.weibo.talentintroduction.mail.controller.OutboundAttachmentController
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.testcontainers.containers.MySQLContainer
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

/** Real metadata SQL, real AuthWebConfig and real file/ZIP consumer paths; no canned authorization result. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpertMailPermissionGuardTest {
    private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    private val mysql = Mysql().withDatabaseName("resource_guard").withUsername("test").withPassword("test")
    private lateinit var jdbc: JdbcTemplate
    private lateinit var guard: ExpertMailPermissionGuard
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var mvc: MockMvc
    private lateinit var material: MaterialArchiveFixture
    private lateinit var attachmentService: MailboxAttachmentService
    private lateinit var calendar: MeetingCalendarService
    private lateinit var meetings: MeetingConfirmationService
    private lateinit var outbound: OutboundAttachmentService
    private lateinit var logs: com.weibo.talentintroduction.audit.service.OperatorActionLogService
    private lateinit var snippets: com.weibo.talentintroduction.reply.service.ReplySnippetService
    @TempDir lateinit var root: Path

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    class MvcConfig

    @BeforeAll fun database() {
        mysql.start()
        val source = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
        jdbc = JdbcTemplate(source)
        val named = NamedParameterJdbcTemplate(source)
        guard = ExpertMailPermissionGuard(ExpertMailReadAccessService(named), named)
        // Only existing metadata columns consumed by the guard; no application schema mutation.
        listOf(
            "CREATE TABLE expert_contact(id BIGINT PRIMARY KEY)",
            "CREATE TABLE expert_mail_read_access(username VARCHAR(64),expert_contact_id BIGINT,access_level VARCHAR(8))",
            "CREATE TABLE mail_record(id BIGINT PRIMARY KEY,expert_contact_id BIGINT)",
            "CREATE TABLE inbound_mail_processing(id BIGINT PRIMARY KEY,expert_contact_id BIGINT NULL)",
            "CREATE TABLE manual_expert_material_upload(id BIGINT PRIMARY KEY,expert_contact_id BIGINT)",
            "CREATE TABLE mail_attachment(id BIGINT PRIMARY KEY,mail_record_id BIGINT NULL,inbound_processing_id BIGINT NULL,manual_upload_id BIGINT NULL)",
            "CREATE TABLE outbound_mail_attachment(id VARCHAR(36) PRIMARY KEY,expert_contact_id BIGINT)",
            "CREATE TABLE meeting_calendar_event(id BIGINT PRIMARY KEY,expert_contact_id BIGINT)"
        ).forEach(jdbc::execute)
    }

    @AfterAll fun stop() { mysql.stop() }
    @BeforeEach fun fixture() {
        listOf("expert_mail_read_access", "expert_contact", "mail_record", "inbound_mail_processing",
            "manual_expert_material_upload", "mail_attachment", "outbound_mail_attachment", "meeting_calendar_event")
            .forEach { jdbc.update("DELETE FROM $it") }
        jdbc.update("INSERT INTO expert_contact VALUES(1),(2),(3)")
        jdbc.update("INSERT INTO expert_mail_read_access VALUES('user1',1,'FULL'),('user1',2,'READ')")
        jdbc.update("INSERT INTO mail_record VALUES(201,1),(202,2),(203,3)")
        jdbc.update("INSERT INTO inbound_mail_processing VALUES(101,1),(102,2),(103,3),(104,NULL)")
        jdbc.update("INSERT INTO manual_expert_material_upload VALUES(401,1),(402,2)")
        jdbc.update("INSERT INTO mail_attachment VALUES(301,201,NULL,NULL),(302,NULL,101,NULL),(303,NULL,NULL,401),(304,202,NULL,NULL)")
        jdbc.update("INSERT INTO meeting_calendar_event VALUES(501,1),(502,2),(503,3)")
        jdbc.update("INSERT INTO outbound_mail_attachment VALUES('00000000-0000-0000-0000-000000000001',1),('00000000-0000-0000-0000-000000000002',2)")
        material = MaterialArchiveFixture(root)
        material.file(301, "record.pdf", "record-bytes".toByteArray(), "record", 1)
        material.file(302, "processing.pdf", "processing-bytes".toByteArray(), "processing", 1)
        material.file(303, "manual.pdf", "manual-bytes".toByteArray(), "manual", 1)
        material.file(304, "foreign.pdf", "secret".toByteArray(), "record", 2)
        attachmentService = Mockito.spy(MailboxAttachmentService(Mockito.mock(MailboxService::class.java), material.material))
        calendar = Mockito.mock(MeetingCalendarService::class.java)
        meetings = Mockito.mock(MeetingConfirmationService::class.java)
        outbound = Mockito.mock(OutboundAttachmentService::class.java)
        val auth = Mockito.mock(AuthService::class.java)
        for (user in listOf("admin", "user1")) {
            Mockito.`when`(auth.findUser(user)).thenReturn(AdminUser(username = user, passwordHash = "unused",
                mustChangePassword = false, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
        }
        context = AnnotationConfigWebApplicationContext()
        context.servletContext = MockServletContext()
        context.addBeanFactoryPostProcessor { beans ->
            beans.registerSingleton("authService", auth)
            beans.registerSingleton("objectMapper", jacksonObjectMapper().findAndRegisterModules())
            beans.registerSingleton("expertMailPermissionGuard", guard)
            beans.registerSingleton("mailboxAttachments", MailboxAttachmentController(attachmentService))
            beans.registerSingleton("materials", ExpertMaterialController(material.material, archiveService = material.archive))
            beans.registerSingleton("documents", ExpertDocumentBrowseController(ExpertDocumentBrowseService(
                material.documents, material.attachments, material.records, material.material)))
            beans.registerSingleton("calendar", MeetingCalendarController(calendar, guard))
            beans.registerSingleton("meetings", MeetingConfirmationController(meetings))
            beans.registerSingleton("outbound", OutboundAttachmentController(outbound))
            logs = Mockito.mock(com.weibo.talentintroduction.audit.service.OperatorActionLogService::class.java)
            snippets = Mockito.mock(com.weibo.talentintroduction.reply.service.ReplySnippetService::class.java)
            Mockito.`when`(logs.search(1L, null, null, null, null, null, 20, 0)).thenReturn(emptyList<com.weibo.talentintroduction.audit.domain.OperatorActionLog>() to 0L)
            beans.registerSingleton("logs", com.weibo.talentintroduction.audit.controller.OperatorActionLogController(logs))
            beans.registerSingleton("snippets", com.weibo.talentintroduction.reply.controller.ReplySnippetController(snippets))
        }
        context.register(MvcConfig::class.java, AuthWebConfig::class.java, GlobalExceptionHandler::class.java,
            ExpertMaterialHttpExceptionAdvice::class.java)
        context.refresh()
        mvc = MockMvcBuilders.webAppContextSetup(context).build()
    }
    @AfterEach fun close() { context.close() }
    private fun session(user: String = "user1") = MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, user) }

    @Test fun `logs and workbench options require an explicit currently writable expert`() {
        for ((path, parameter) in listOf("/api/operator-action-logs" to "expertContactId", "/api/reply-snippets" to "contactId")) {
            mvc.perform(get(path).session(session()).param(parameter, "1")).andExpect(status().isOk)
            for (id in listOf("2", "3")) {
                mvc.perform(get(path).session(session()).param(parameter, id)).andExpect(status().isForbidden)
            }
            mvc.perform(get(path).session(session())).andExpect(status().isBadRequest)
        }
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        mvc.perform(get("/api/operator-action-logs").session(session()).param("expertContactId", "1")).andExpect(status().isForbidden)
        mvc.perform(get("/api/reply-snippets").session(session()).param("contactId", "1")).andExpect(status().isForbidden)
        mvc.perform(post("/api/reply-snippets").session(session()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden)
    }

    @Test fun `three actual attachment owners download exact bytes and downgrade revokes before file service`() {
        for ((id, text) in listOf(301 to "record-bytes", 302 to "processing-bytes", 303 to "manual-bytes")) {
            mvc.perform(get("/api/mail/mailbox/attachments/$id/download").session(session()))
                .andExpect(status().isOk).andExpect(content().bytes(text.toByteArray()))
        }
        Mockito.clearInvocations(attachmentService)
        jdbc.update("UPDATE expert_mail_read_access SET access_level='READ' WHERE expert_contact_id=1")
        mvc.perform(get("/api/mail/mailbox/attachments/301/download").session(session()))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value(ExpertMailPermissionGuard.MAIL_WRITE_FORBIDDEN))
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        mvc.perform(get("/api/mail/mailbox/attachments/302/download").session(session())).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(attachmentService)
        mvc.perform(get("/api/mail/mailbox/attachments/301/download").session(session("admin")))
            .andExpect(status().isOk).andExpect(content().bytes("record-bytes".toByteArray()))
    }

    @Test fun `foreign true owner and forged contact path never read file bytes`() {
        mvc.perform(get("/api/mail/mailbox/attachments/304/download").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/expert-contacts/1/attachments/304/download").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/expert-contacts/2/attachments/301/preview").session(session())).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(attachmentService)
        material.assertNoWritesOrTransfers()
    }

    @Test fun `source rows and missing malformed unbound resources retain distinct outcomes`() {
        mvc.perform(get("/api/mail/mailbox/INBOUND_PROCESSING/102/attachments").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/mail/mailbox/INBOUND_PROCESSING/104/attachments").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/mail/mailbox/MAIL_RECORD/203/attachments").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/mail/mailbox/attachments/999/download").session(session())).andExpect(status().isNotFound)
        mvc.perform(get("/api/mail/mailbox/attachments/0/download").session(session())).andExpect(status().isBadRequest)
        mvc.perform(get("/api/mail/mailbox/OTHER/201/attachments").session(session())).andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(attachmentService)
    }

    @Test fun `calendar query arrays and event IDs are resolved before service execution`() {
        mvc.perform(get("/api/meeting-calendar/events").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/events?contactId=2").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/summaries?contactIds=1,2").session(session())).andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/summaries").param("contactIds", "1", "2").session(session())).andExpect(status().isForbidden)
        mvc.perform(post("/api/meeting-calendar/events").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("""{"contactId":2,"startBeijing":"2026-11-02T10:00","endBeijing":"2026-11-02T10:30"}"""))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/meeting-calendar/events/503").session(session())).andExpect(status().isForbidden)
        mvc.perform(put("/api/meeting-calendar/events/502").session(session()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(calendar)
    }

    @Test fun `processing query and outbound record attachment mismatch stay denied even with a legal contact path`() {
        mvc.perform(get("/api/mail/unmatched-inbound/101/meeting-confirmation/options?contactId=2&senderAccountCode=fixture").session(session()))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/mail/conversations/1/outbound-attachments/00000000-0000-0000-0000-000000000002/download").session(session()))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/mail/conversations/1/messages/202/outbound-attachments/00000000-0000-0000-0000-000000000001/download").session(session()))
            .andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(meetings, outbound)
    }

    @Test fun `FULL material batches retain complete member validation before queue or ZIP output`() {
        val prefix = "/api/expert-contacts/1/materials"
        mvc.perform(post("$prefix/download-zip").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"attachmentIds\":[301,304]}"))
            .andExpect(status().isBadRequest).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        mvc.perform(post("$prefix/transfers").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"attachmentIds\":[301,304]}"))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(material.transferService)
        val response = mvc.perform(post("$prefix/download-zip").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"attachmentIds\":[301,302,303]}"))
            .andExpect(status().isOk).andReturn().response.contentAsByteArray
        val members = linkedMapOf<String, String>()
        ZipInputStream(response.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                members[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(mapOf("record.pdf" to "record-bytes", "processing.pdf" to "processing-bytes", "manual.pdf" to "manual-bytes"), members)
        Mockito.`when`(material.transferService.enqueueMaterial(1, listOf(301L,302L), "user1"))
            .thenReturn(AttachmentTransferService.EnqueueBatchResult(listOf(
                AttachmentTransferService.EnqueueItemResult(301, alreadyReady = true),
                AttachmentTransferService.EnqueueItemResult(302, alreadyReady = true)), 0, 2, false))
        mvc.perform(post("$prefix/transfers").session(session()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"attachmentIds\":[301,302]}"))
            .andExpect(status().isAccepted).andExpect(jsonPath("$.alreadyReadyCount").value(2))
    }

    @Test fun `service guard preserves admin and internal contracts but never treats operatorName as authority`() {
        assertDoesNotThrow { guard.requireFull(null, 2) }
        assertDoesNotThrow { guard.requireFull("admin", 2) }
        assertThrows(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException::class.java) { guard.requireFull("user1", 2) }
        assertDoesNotThrow { guard.requireFull("user1", 1) }
        jdbc.update("DELETE FROM expert_mail_read_access WHERE expert_contact_id=1")
        assertThrows(com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException::class.java) { guard.requireFull("user1", 1) }
    }
}
