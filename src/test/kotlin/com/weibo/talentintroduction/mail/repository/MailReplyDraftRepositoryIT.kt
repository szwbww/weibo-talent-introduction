package com.weibo.talentintroduction.mail.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
import com.weibo.talentintroduction.mail.controller.MailReplyDraftController
import com.weibo.talentintroduction.mail.service.*
import org.apache.catalina.startup.Tomcat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito.*
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.context.annotation.Configuration
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@EnabledIfSystemProperty(named="mysqlIt",matches="true")
class MailReplyDraftRepositoryIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("talent_introduction").withUsername("test").withPassword("test")
        private lateinit var dataSource: DriverManagerDataSource
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for draft MySQL IT" }
            mysql.start()
            dataSource = DriverManagerDataSource(mysql.jdbcUrl,mysql.username,mysql.password)
            Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
        }
        @JvmStatic @AfterAll fun stop() { mysql.stop() }
    }
    private val jdbc by lazy { JdbcTemplate(dataSource) }
    private val repository by lazy { MailReplyDraftRepository(NamedParameterJdbcTemplate(dataSource)) }
    private val tx by lazy { TransactionTemplate(DataSourceTransactionManager(dataSource)) }
    private val now = LocalDateTime.of(2026,10,8,12,0)
    private val target = MailReplyDraftTarget(9101,MailReplyDraftKind.OUTBOUND)
    private fun insert(owner: String = "A", t: MailReplyDraftTarget = target, content: MailReplyDraftContent = MailReplyDraftContent(text="original")) = repository.insert(owner,t,content,MailReplyDraftContextCodec.serialize(content.context),now)
    private fun save(id: Long, version: Long, text: String = "new", state: MailReplyDraftState = MailReplyDraftState.ACTIVE) = repository.save("A",id,version,state,MailReplyDraftContent(text=text),MailReplyDraftContextCodec.serialize(MailReplyDraftContext()),now.plusSeconds(version))
    @BeforeEach fun seed() {
        jdbc.update("DELETE FROM mailbox_reply_draft")
        jdbc.update("DELETE FROM outbound_mail_attachment WHERE expert_contact_id=9101")
        jdbc.update("INSERT INTO mail_sender_account (id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password) VALUES (9101,'draft-test','sender@example.test','Sender','smtp.test',465,'sender','pw','imap.test',993,'sender','pw') ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO campaign (id,campaign_code,campaign_name,sender_account_id) VALUES (9101,'DRAFT_TEST','Draft',9101) ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email,expert_name) VALUES (9101,9101,'draft-test','expert@example.test','Expert 😀') ON DUPLICATE KEY UPDATE id=id")
    }
    @Test fun `unique target distinguishes owner inbound account and outbound scope case sensitively`() {
        insert()
        assertThrows(DuplicateKeyException::class.java) { insert() }
        insert("B"); insert(t=target.copy(accountScope="draft-test")); insert(t=target.copy(accountScope="DRAFT-test"))
        insert(t=MailReplyDraftTarget(9101,MailReplyDraftKind.INBOUND,1,"draft-test"))
        insert(t=MailReplyDraftTarget(9101,MailReplyDraftKind.INBOUND,2,"draft-test"))
        assertEquals(6L,repository.summaries("A",null,listOf(9101)).total + repository.summaries("B",null,listOf(9101)).total)
        assertNull(repository.findOwned("B",repository.findTarget("A",target)!!.id))
    }
    @Test fun `real attachment metadata ownership empty save missing restoration and business state isolation`() {
        val contacts = mock(ExpertContactRepository::class.java)
        `when`(contacts.existsById(9101)).thenReturn(true)
        val metadata = OutboundMailAttachmentRepository(NamedParameterJdbcTemplate(dataSource))
        val attachmentService = OutboundAttachmentService(MailAttachmentStorageProperties(basePath="/nonexistent/draft-test-no-file-access"),metadata,contacts)
        val service = MailReplyDraftService(repository,contacts,mock(InboundMailProcessingRepository::class.java),mock(MailSenderAccountRepository::class.java),attachmentService)
        val ownId = java.util.UUID.randomUUID().toString()
        val foreignId = java.util.UUID.randomUUID().toString()
        listOf(ownId to "A",foreignId to "B").forEach { (id,owner) ->
            metadata.insert(OutboundMailAttachment(id,9101,owner,"actual.txt","text/plain",3,"a".repeat(64),now))
        }
        val before = businessCounts()
        val contactBefore = jdbc.queryForMap("SELECT * FROM expert_contact WHERE id=9101")
        fun content(id: String, text: String = "body") = MailReplyDraftContent(text=text,context=MailReplyDraftContext(
            outboundAttachmentDraft=MailReplyDraftAttachments(1,listOf(MailReplyDraftAttachmentItem("file-1","ready",id,"forged-name","fake/type",999,"fake-hash")))))
        val saved = service.save("A",target,MailReplyDraftSaveRequest(0,content(ownId)))
        assertEquals("actual.txt",saved.content!!.context.outboundAttachmentDraft!!.items.single().filename)
        assertTrue(saved.attachmentDownloads[ownId]!!.startsWith("/api/mail/conversations/9101/"))
        val empty = service.save("A",target,MailReplyDraftSaveRequest(1,content(ownId,"")))
        assertEquals(MailReplyDraftState.ACTIVE,empty.state); assertEquals("",service.get("A",saved.id!!).content!!.text)
        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,assertThrows(MailReplyDraftException::class.java) {
            service.save("A",target,MailReplyDraftSaveRequest(2,content(foreignId)))
        }.status)
        assertEquals(2L,repository.findOwned("A",saved.id!!)!!.version)
        assertEquals(before,businessCounts()); assertEquals(contactBefore,jdbc.queryForMap("SELECT * FROM expert_contact WHERE id=9101"))
        jdbc.update("DELETE FROM outbound_mail_attachment WHERE id=?",ownId)
        val restored = service.get("A",saved.id!!)
        assertEquals("",restored.content!!.text); assertFalse(restored.attachmentsSendable)
        assertEquals("failed",restored.content!!.context.outboundAttachmentDraft!!.items.single().state)
        service.save("A",target,MailReplyDraftSaveRequest(2,restored.content!!))
        assertEquals(3L,service.get("A",saved.id!!).version)
    }
    private fun <T> race(action: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(2); val ready = CountDownLatch(2); val go = CountDownLatch(1)
        return try {
            val jobs = (1..2).map { executor.submit<T> { ready.countDown(); check(go.await(10,TimeUnit.SECONDS)); action() } }
            check(ready.await(10,TimeUnit.SECONDS)); go.countDown(); jobs.map { it.get(20,TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
    }
    @Test fun `concurrent first create and transactional CAS each have one winner`() {
        val creates = race { try { insert(); true } catch (e: DuplicateKeyException) { false } }
        assertEquals(1,creates.count { it })
        val id = repository.findTarget("A",target)!!.id
        assertEquals(1,race { tx.execute { save(id,1) }!! }.count { it })
        assertEquals(2L,repository.findOwned("A",id)!!.version)
    }
    @Test fun `discard late update explicit reopen empty content and immutable creation`() {
        val id = insert(); assertTrue(save(id,1)); assertTrue(repository.discard("A",id,2,now.plusSeconds(3)))
        val closed = repository.findOwned("A",id)!!
        assertEquals(3L,closed.version); assertEquals(MailReplyDraftState.DISCARDED,closed.state)
        assertNull(closed.subject); assertNull(closed.html); assertNull(closed.text); assertNull(closed.contextJson)
        assertFalse(save(id,2)); assertFalse(save(id,3)); assertTrue(save(id,3,"",MailReplyDraftState.DISCARDED))
        val reopened = repository.findOwned("A",id)!!
        assertEquals(4L,reopened.version); assertEquals("",reopened.text); assertEquals(now,reopened.createdAt)
        assertEquals(MailReplyDraftState.ACTIVE,reopened.state)
    }
    @Test fun `binding requires transaction preserves newer content and participates in rollback`() {
        val id = insert()
        assertThrows(IllegalStateException::class.java) { repository.lockOwned("A",id) }
        assertThrows(IllegalStateException::class.java) { repository.bindAttempt("A",id,1,77) }
        assertThrows(IllegalStateException::class.java) { repository.closeSent("A",id,1,77,now) }
        assertThrows(IllegalStateException::class.java) { repository.releaseCompletedBinding("A",id,1,77) }
        tx.execute { status -> assertNotNull(repository.lockOwned("A",id)); assertTrue(repository.bindAttempt("A",id,1,77)); status.setRollbackOnly() }
        assertNull(repository.findOwned("A",id)!!.sendAttemptId)
        tx.execute { assertTrue(repository.bindAttempt("A",id,1,77)) }
        assertTrue(save(id,1,"edited during send"))
        assertEquals(1L,repository.findOwned("A",id)!!.sendVersion)
        tx.execute { assertFalse(repository.closeSent("A",id,1,77,now)); assertTrue(repository.releaseCompletedBinding("A",id,1,77)) }
        assertEquals("edited during send",repository.findOwned("A",id)!!.text)
        tx.execute { assertTrue(repository.bindAttempt("A",id,2,78)); assertTrue(repository.closeSent("A",id,2,78,now)) }
        assertEquals(MailReplyDraftState.SENT,repository.findOwned("A",id)!!.state)
        assertFalse(save(id,2))
        assertTrue(save(id,3,"reopened",MailReplyDraftState.SENT))
        assertNull(repository.findOwned("A",id)!!.sendAttemptId); assertNull(repository.findOwned("A",id)!!.sendVersion)
    }
    @Test fun `left joined deleted old target remains listed searched and discardable`() {
        val id = insert(content=MailReplyDraftContent(subject="literal %_!",text="😀".repeat(130)))
        jdbc.update("UPDATE mailbox_reply_draft SET updated_at=? WHERE id=?",now.minusYears(2),id)
        jdbc.update("DELETE FROM expert_contact WHERE id=9101")
        val list = repository.list("A",null,"%_!",0,20)
        assertEquals(1L,list.total); assertFalse(list.items.single().contactExists)
        assertEquals(120,list.items.single().preview.codePointCount(0,list.items.single().preview.length))
        assertFalse(repository.findOwned("A",id)!!.contactExists)
        assertTrue(repository.discard("A",id,1,now)); assertEquals(0L,repository.list("A",null,null,0,20).total)
    }
    @Test fun `scope totals are independent of page and contact subset`() {
        insert(); insert(t=target.copy(accountScope="draft-test")); insert(t=target.copy(kind=MailReplyDraftKind.INBOUND,processingId=6,accountScope="draft-test"))
        assertEquals(3L,repository.list("A",null,null,0,1).total)
        assertEquals(2L,repository.list("A","draft-test",null,0,1).total)
        assertEquals(3L,repository.summaries("A",null,listOf(999)).total)
        assertEquals(0L,repository.summaries("A",null,listOf(999)).items.single().count)
    }

    @Configuration @EnableWebMvc class MvcConfig
    /** Actual HTTP/session/Spring MVC smoke; fixtures live only in this temporary server. */
    @Test fun `runtime HTTP sessions restore conflict discard reopen and business isolation`() {
        val contacts = mock(ExpertContactRepository::class.java)
        `when`(contacts.existsById(9101)).thenAnswer { jdbc.queryForObject("SELECT COUNT(*) FROM expert_contact WHERE id=9101",Long::class.java)!! > 0 }
        val service = MailReplyDraftService(repository,contacts,mock(InboundMailProcessingRepository::class.java),mock(MailSenderAccountRepository::class.java),mock(OutboundAttachmentService::class.java))
        val countsBefore = businessCounts()
        val dir = Files.createTempDirectory("draft-runtime-smoke")
        val tomcat = Tomcat(); tomcat.setBaseDir(dir.toString()); tomcat.setPort(0); tomcat.connector
        val context = tomcat.addContext("",dir.toString())
        val app = AnnotationConfigWebApplicationContext(); app.register(MvcConfig::class.java); app.servletContext=context.servletContext
        app.addBeanFactoryPostProcessor { it.registerSingleton("draftController",MailReplyDraftController(service)) }
        val fixture = object : HttpServlet() {
            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) { req.getSession(true).setAttribute(AuthSessionKeys.USERNAME,req.getParameter("owner")); resp.writer.write("session fixture") }
        }
        Tomcat.addServlet(context,"fixture",fixture); context.addServletMappingDecoded("/fixture-session","fixture")
        Tomcat.addServlet(context,"mvc",DispatcherServlet(app)); context.addServletMappingDecoded("/","mvc")
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        try {
            tomcat.start(); val base = "http://127.0.0.1:${tomcat.connector.localPort}"
            fun login(owner: String): String { val c = URL("$base/fixture-session?owner=$owner").openConnection() as HttpURLConnection; c.inputStream.use { it.readBytes() }; val cookie=c.getHeaderField("Set-Cookie").substringBefore(';'); c.disconnect(); return cookie }
            fun request(method: String, path: String, cookie: String?, body: String? = null): Pair<Int,com.fasterxml.jackson.databind.JsonNode> {
                val c = URL(base+path).openConnection() as HttpURLConnection; c.requestMethod=method; c.connectTimeout=5000; c.readTimeout=5000
                if (cookie != null) c.setRequestProperty("Cookie",cookie)
                if (body != null) { c.doOutput=true; c.setRequestProperty("Content-Type","application/json"); c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
                val status=c.responseCode; val text=(if(status>=400)c.errorStream else c.inputStream).use { it.readBytes().toString(Charsets.UTF_8) }; c.disconnect()
                println("DRAFT_RUNTIME_HTTP $method $path -> $status $text")
                return status to mapper.readTree(text)
            }
            val a = login("A"); val b = login("B"); val path="/api/mail/mailbox/drafts/target?contactId=9101&kind=OUTBOUND"
            assertEquals(401,request("GET",path,null).first)
            val created = request("PUT",path,a,"{\"expectedVersion\":0,\"content\":{\"subject\":\"草稿01\",\"html\":\"<b>跨设备保留</b>\",\"text\":\"跨设备保留\"}}")
            assertEquals(200,created.first); val id=created.second["id"].asLong()
            assertEquals("跨设备保留",request("GET","/api/mail/mailbox/drafts/$id",login("A")).second["content"]["text"].asText())
            assertEquals(404,request("GET","/api/mail/mailbox/drafts/$id",b).first)
            assertEquals(200,request("PUT",path,a,"{\"expectedVersion\":1,\"content\":{\"text\":\"版本二\"}}").first)
            assertEquals(409,request("PUT",path,a,"{\"expectedVersion\":1,\"content\":{\"text\":\"旧窗口\"}}").first)
            assertEquals("DISCARDED",request("DELETE","/api/mail/mailbox/drafts/$id?expectedVersion=2",a).second["state"].asText())
            assertEquals(409,request("PUT",path,a,"{\"expectedVersion\":2,\"content\":{\"text\":\"late\"}}").first)
            assertEquals(4L,request("PUT",path,a,"{\"expectedVersion\":3,\"reopen\":true,\"content\":{}}").second["version"].asLong())
            jdbc.update("UPDATE mailbox_reply_draft SET updated_at=? WHERE id=?",now.minusYears(2),id)
            assertEquals(200,request("GET","/api/mail/mailbox/drafts/$id",a).first)
            assertEquals(countsBefore,businessCounts())
            jdbc.update("DELETE FROM expert_contact WHERE id=9101")
            assertFalse(request("GET","/api/mail/mailbox/drafts/$id",a).second["contactExists"].asBoolean())
            assertFalse(request("GET","/api/mail/mailbox/drafts",a).second["items"][0]["contactExists"].asBoolean())
            assertEquals(200,request("DELETE","/api/mail/mailbox/drafts/$id?expectedVersion=4",a).first)
            println("DRAFT_RUNTIME_SMOKE complete; isolated HTTP sessions, durable MySQL, business counts unchanged before explicit fixture deletion")
        } finally {
            tomcat.stop(); tomcat.destroy(); app.close()
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
    private fun businessCounts() = listOf("mail_record","mail_send_attempt","inbound_mail_processing","expert_contact","mail_attachment","outbound_mail_attachment").associateWith { jdbc.queryForObject("SELECT COUNT(*) FROM $it",Long::class.java) }
}
