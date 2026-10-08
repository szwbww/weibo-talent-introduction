package com.weibo.talentintroduction.mail.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(ExpertInboundNotificationRepository::class)
class ExpertInboundNotificationRepositoryIT {
    companion object {
        private class Mysql(image: String) : MySQLContainer<Mysql>(image)
        private val mysql = Mysql("mysql:8.0.36").withDatabaseName("talent_introduction").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1")
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for notification MySQL IT" }
            mysql.start()
        }
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var repository: ExpertInboundNotificationRepository
    @Autowired lateinit var transactions: PlatformTransactionManager
    @Autowired lateinit var inbound: InboundMailProcessingRepository
    @Autowired lateinit var contacts: ExpertContactRepository
    private fun service() = ExpertInboundNotificationService(repository, inbound, contacts, MailBodyCleaner(), ObjectMapper(),
        "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=isolated-test-not-a-credential")

    @BeforeEach fun setup() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_notification_enqueue")
        jdbc.update("DELETE FROM expert_inbound_notification_outbox")
        jdbc.update("DELETE FROM inbound_mail_processing WHERE mailbox_owner_code='notification-test'")
        jdbc.update("DELETE FROM expert_inbound_notification_setting")
        jdbc.update("INSERT INTO expert_inbound_notification_setting (id,enabled,generation) VALUES (1,FALSE,0)")
        jdbc.update("INSERT INTO mail_sender_account (id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password) VALUES (9101,'notification-test','sender@example.test','Sender','smtp.test',465,'sender','pw','imap.test',993,'sender','pw') ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO campaign (id,campaign_code,campaign_name,sender_account_id) VALUES (9101,'NOTIFICATION_TEST','Notification',9101) ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email,expert_name) VALUES (9101,9101,'notification-test','expert@example.test','Expert 😀') ON DUPLICATE KEY UPDATE id=id")
    }
    private fun source(uid: Long = 101) = InboundMailProcessing(senderAccountCode = "notification-test", mailboxOwnerCode = "notification-test",
        uidValidity = 7, imapUid = uid, messageId = "reply-$uid", fromEmail = "expert@example.test", subject = "Test subject",
        body = "Hello 😀\n> old message", receivedAt = LocalDateTime.of(2026,10,8,12,0), processStatus = "MANUAL_REVIEW", processReason = "GLOBAL_AUTO_REPLY_DISABLED", expertContactId = 9101,
        createdAt = LocalDateTime.of(2026,10,8,12,0), updatedAt = LocalDateTime.of(2026,10,8,12,0))
    private fun enqueue(uid: Long = 101): Long {
        var id = 0L
        TransactionTemplate(transactions).execute {
            val saved = inbound.findByMailboxOwnerCodeAndUidValidityAndImapUid("notification-test",7,uid) ?: inbound.save(source(uid))
            repository.inReceiptSavepoint { id = repository.enqueue(saved) { "专家来信\n通知编号：$it" } ?: 0 }
        }
        return id
    }
    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM expert_inbound_notification_outbox", Int::class.java)!!
    private fun status(id: Long) = jdbc.queryForObject("SELECT status FROM expert_inbound_notification_outbox WHERE id=?", String::class.java, id)
    private fun ready() {
        jdbc.update("UPDATE expert_inbound_notification_setting SET next_send_at=NULL")
        jdbc.update("UPDATE expert_inbound_notification_outbox SET next_attempt_at=UTC_TIMESTAMP(3) WHERE status='PENDING'")
    }

    @Test fun `default idempotence persistence missing row and generations are authoritative`() {
        assertEquals(ExpertInboundNotificationSetting(), repository.settings())
        val before = jdbc.queryForMap("SELECT * FROM expert_inbound_notification_setting WHERE id=1")
        repository.setEnabled(false,"operator")
        assertEquals(before, jdbc.queryForMap("SELECT * FROM expert_inbound_notification_setting WHERE id=1"))
        val enabled = repository.setEnabled(true,"operator")
        assertEquals(1L,enabled.generation)
        assertEquals(enabled,repository.setEnabled(true,"other"))
        assertEquals("operator", jdbc.queryForObject("SELECT updated_by FROM expert_inbound_notification_setting", String::class.java))
        assertEquals(enabled.generation,service().settings().generation)
        assertEquals(enabled,ExpertInboundNotificationRepository(jdbc,transactions).settings())
        jdbc.update("DELETE FROM expert_inbound_notification_setting")
        assertFalse(repository.settings().enabled)
        assertEquals(0L,repository.setEnabled(false,"operator").generation)
    }

    @Test fun `savepoint follows receipt rollback and is invisible on another connection before commit`() {
        repository.setEnabled(true,"operator")
        TransactionTemplate(transactions).execute { outer ->
            val saved = inbound.save(source())
            repository.inReceiptSavepoint { repository.enqueue(saved) { "notification $it" } }
            assertEquals(1,count())
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT COUNT(*) FROM expert_inbound_notification_outbox").use { rows ->
                        assertTrue(rows.next()); assertEquals(0,rows.getInt(1))
                    }
                }
            }
            outer.setRollbackOnly()
        }
        assertEquals(0,count())
        assertNull(inbound.findByMailboxOwnerCodeAndUidValidityAndImapUid("notification-test",7,101))
    }

    @Test fun `notification SQL failure rolls back only savepoint and receipt still commits and marks seen`() {
        repository.setEnabled(true,"operator")
        jdbc.execute("CREATE TRIGGER fail_notification_enqueue BEFORE INSERT ON expert_inbound_notification_outbox FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated notification failure'")
        val service = service()
        val (pipeline, receive) = pipeline(service)
        try {
            val result = pipeline.processSingle(account(), mail(101), notifyGroups = true)
            assertEquals(SinglePipelineOutcome.BODY_TRUNCATED,result.outcome)
            assertNotNull(inbound.findByMailboxOwnerCodeAndUidValidityAndImapUid("notification-test",7,101))
            assertEquals(0,count())
            Mockito.verify(receive).markSeen(account(),101)
        } finally { jdbc.execute("DROP TRIGGER fail_notification_enqueue") }
    }

    @Test fun `registration requires real outer transaction and unmatched historical identities never enqueue`() {
        repository.setEnabled(true,"operator")
        assertThrows(IllegalStateException::class.java) { repository.inReceiptSavepoint {} }
        val service = service()
        service.enqueueBestEffort("notification-test",7,101)
        TransactionTemplate(transactions).execute {
            inbound.save(source().copy(expertContactId = null))
            service.enqueueBestEffort("notification-test",7,101)
            inbound.save(source(102).copy(senderAccountCode = "SIMULATOR_NOOP"))
            service.enqueueBestEffort("notification-test",7,102)
            service.enqueueBestEffort("notification-test",0,102)
            service.enqueueBestEffort("",7,103)
        }
        assertEquals(0,count())
    }

    @Test fun `concurrent duplicate enqueue and settings changes serialize without duplicate rows`() {
        repository.setEnabled(true,"operator")
        TransactionTemplate(transactions).execute { inbound.save(source()) }
        val pool = Executors.newFixedThreadPool(6)
        try {
            (0 until 12).map { pool.submit { enqueue() } }.forEach { it.get(30,TimeUnit.SECONDS) }
            assertEquals(1,count())
            val latch = CountDownLatch(1)
            val enqueue = pool.submit { latch.await(); enqueue(102) }
            val disable = pool.submit { latch.await(); repository.setEnabled(false,"operator") }
            latch.countDown()
            enqueue.get(30,TimeUnit.SECONDS); disable.get(30,TimeUnit.SECONDS)
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM expert_inbound_notification_outbox WHERE status='PENDING'",Int::class.java))
            assertFalse(repository.settings().enabled)
        } finally { pool.shutdownNow() }
    }

    @Test fun `disabled period is not queued and disable enable cannot revive pending or claimed generation`() {
        repository.setEnabled(true,"operator")
        val pending = enqueue()
        val claimedId = enqueue(102)
        val claim = repository.claim("worker")!!
        assertEquals(pending,claim.id)
        repository.setEnabled(false,"operator")
        assertEquals("CANCELLED",status(claimedId))
        assertEquals(0L,enqueue(103))
        repository.setEnabled(true,"operator")
        assertFalse(repository.authorizeSend("worker",claim))
        assertEquals("CANCELLED",status(pending))
        assertNull(repository.claim("worker"))
        val fresh = enqueue(104)
        assertEquals(fresh,repository.claim("worker")!!.id)
    }

    @Test fun `global worker lease and five second authorization interval hold across instances`() {
        repository.setEnabled(true,"operator")
        enqueue(); enqueue(102)
        val second = ExpertInboundNotificationRepository(jdbc,transactions)
        val first = repository.claim("one")!!
        assertNull(second.claim("two"))
        assertTrue(repository.authorizeSend("one",first))
        repository.finish(first,true,false,null)
        assertNull(repository.claim("one"))
        assertNull(second.claim("two"))
        repository.releaseWorker("one")
        assertNull(second.claim("two")) // releasing leadership must not erase rate limit
        Thread.sleep(5100)
        val next = second.claim("two")!!
        assertTrue(second.authorizeSend("two",next))
        repository.finish(next,true,false,null)
        assertEquals("SENT",status(next.id))
    }

    @Test fun `expired sending recovery fences old token and persists bounded backoff`() {
        repository.setEnabled(true,"operator")
        val id = enqueue()
        val first = repository.claim("one")!!
        assertTrue(repository.authorizeSend("one",first))
        jdbc.update("UPDATE expert_inbound_notification_setting SET worker_lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND,next_send_at=NULL")
        jdbc.update("UPDATE expert_inbound_notification_outbox SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",id)
        assertNull(repository.claim("two"))
        assertEquals("PENDING",status(id))
        assertTrue(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(3),next_attempt_at) FROM expert_inbound_notification_outbox WHERE id=?",Int::class.java,id)!! in 28..30)
        ready()
        val replacement = repository.claim("two")!!
        assertNotEquals(first.token,replacement.token)
        assertEquals(2,replacement.attempts)
        repository.finish(first,true,false,null)
        assertEquals("SENDING",status(id))
        assertFalse(repository.authorizeSend("one",first))
        repository.finish(replacement,false,true,"HTTP_TIMEOUT")
        assertTrue(jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(3),next_attempt_at) FROM expert_inbound_notification_outbox WHERE id=?",Int::class.java,id)!! in 118..120)
        ready()
        val third = repository.claim("two")!!
        assertEquals(3,third.attempts)
        repository.finish(third,false,true,"HTTP_TIMEOUT")
        assertEquals("FAILED",status(id))
        ready()
        assertNull(repository.claim("two"))
    }

    @Test fun `expired final attempt fails and old generation recovery is cancelled`() {
        repository.setEnabled(true,"operator")
        val id = enqueue()
        val claim = repository.claim("one")!!
        jdbc.update("UPDATE expert_inbound_notification_outbox SET attempts=3,lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",id)
        jdbc.update("UPDATE expert_inbound_notification_setting SET worker_lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND")
        assertNull(repository.claim("two"))
        assertEquals("FAILED",status(id))
        repository.finish(claim,true,false,null)
        assertEquals("FAILED",status(id))
        enqueue(102)
        val old = repository.claim("two")!!
        repository.setEnabled(false,"operator"); repository.setEnabled(true,"operator")
        jdbc.update("UPDATE expert_inbound_notification_outbox SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE id=?",old.id)
        assertNull(repository.claim("two"))
        assertEquals("CANCELLED",status(old.id))
    }

    @Test fun `permanent errors are terminal and invalid diagnostics cannot leak secrets into storage`() {
        repository.setEnabled(true,"operator")
        val id = enqueue()
        val claim = repository.claim("worker")!!
        repository.finish(claim,false,false,"https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=not-stored")
        assertEquals("FAILED",status(id))
        assertEquals("SEND_FAILED",jdbc.queryForObject("SELECT last_error_code FROM expert_inbound_notification_outbox WHERE id=?",String::class.java,id))
        assertNull(repository.claim("worker"))
    }

    @Test fun `terminal cleanup is thirty days bounded and does not touch receipt identity`() {
        repository.setEnabled(true,"operator")
        val id = enqueue()
        val claim = repository.claim("worker")!!
        repository.finish(claim,true,false,null)
        jdbc.update("UPDATE expert_inbound_notification_outbox SET updated_at=UTC_TIMESTAMP(3)-INTERVAL 31 DAY WHERE id=?",id)
        // Populate only this auxiliary table with terminal fixtures.
        repeat(104) { index ->
            jdbc.update("INSERT INTO expert_inbound_notification_outbox (inbound_processing_id,expert_contact_id,mailbox_owner_code,uid_validity,imap_uid,generation,payload,status,next_attempt_at,created_at,updated_at) VALUES (1,9101,'cleanup',7,?,1,'bounded','CANCELLED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)-INTERVAL 31 DAY)",index+1)
        }
        enqueue(102)
        assertEquals(100,repository.cleanTerminal())
        assertEquals(6,count())
        assertEquals(5,repository.cleanTerminal())
        assertEquals(1,count())
        assertNotNull(inbound.findByMailboxOwnerCodeAndUidValidityAndImapUid("notification-test",7,101))
        val (pipeline, _) = pipeline(service())
        assertEquals(SinglePipelineOutcome.DUPLICATE_IMAP_UID,pipeline.processSingle(account(),mail(101),notifyGroups = true).outcome)
        assertEquals(1,count())
    }

    @Test fun `SMOKE real receipt commit dedicated two workers and local HTTP stub never send to a real group`() {
        val requests = Collections.synchronizedList(mutableListOf<Pair<Long,String>>())
        val delivered = CountDownLatch(2)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/send") { exchange ->
            val request = String(exchange.requestBody.readBytes(),Charsets.UTF_8)
            requests.add(System.nanoTime() to request)
            val reply = "{\"errcode\":0,\"errmsg\":\"ok\"}".toByteArray()
            exchange.sendResponseHeaders(200,reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
            delivered.countDown()
        }
        server.start()
        val first = service()
        val second = service()
        listOf(first,second).forEach { notification ->
            notification.httpClient.requestFactory = object : SimpleClientHttpRequestFactory() {
                override fun createRequest(uri: URI, method: HttpMethod): ClientHttpRequest =
                    super.createRequest(URI("http://127.0.0.1:${server.address.port}/send"),method)
                override fun prepareConnection(connection: HttpURLConnection, method: String) {
                    super.prepareConnection(connection,method)
                    connection.instanceFollowRedirects = false
                }
            }.apply { setConnectTimeout(3000); setReadTimeout(5000) }
        }
        try {
            first.setEnabled(true,"operator")
            val (pipeline, receive) = pipeline(first)
            val before = contacts.findById(9101).get()
            pipeline.processSingle(account(),mail(101),notifyGroups = true)
            pipeline.processSingle(account(),mail(102),notifyGroups = true)
            assertEquals(2,count())
            assertEquals(0,requests.size)
            assertEquals(before,contacts.findById(9101).get())
            Mockito.verify(receive).markSeen(account(),101)
            Mockito.verify(receive).markSeen(account(),102)
            first.start(); second.start()
            assertTrue(delivered.await(12,TimeUnit.SECONDS))
            first.stop(); second.stop()
            assertEquals(2,requests.size)
            assertTrue(requests[1].first-requests[0].first >= TimeUnit.SECONDS.toNanos(5))
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM expert_inbound_notification_outbox WHERE status='SENT'",Int::class.java))
            val json = ObjectMapper().readTree(requests[0].second)
            assertEquals("text",json["msgtype"].asText())
            assertTrue(json["text"]["content"].asText().contains("通知编号："))
            assertTrue(json["text"]["content"].asText().contains("Expert 😀"))
            assertFalse(requests[0].second.contains("mentioned_list"))
            assertFalse(requests[0].second.contains("key="))
            first.setEnabled(false,"operator")
            pipeline.processSingle(account(),mail(103),notifyGroups = true)
            first.setEnabled(true,"operator")
            assertEquals(2,count()) // closed-period receipt never backfills
            pipeline.processSingle(account(),mail(101),notifyGroups = true)
            assertEquals(2,count())
            println("SMOKE: real receipt/outbox committed, 2 local text HTTP requests, global interval >=5s, SENT=2, disabled-period and duplicate increments=0")
        } finally { first.stop(); second.stop(); server.stop(0) }
    }

    @Test fun `slow HTTP holds no receipt or settings lock and disable allows only the in-flight request`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/send") { exchange ->
            exchange.requestBody.readBytes()
            entered.countDown()
            release.await(4,TimeUnit.SECONDS)
            val reply = "{\"errcode\":0}".toByteArray()
            exchange.sendResponseHeaders(200,reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
            completed.countDown()
        }
        server.start()
        val notification = service()
        notification.httpClient.requestFactory = object : SimpleClientHttpRequestFactory() {
            override fun createRequest(uri: URI, method: HttpMethod): ClientHttpRequest =
                super.createRequest(URI("http://127.0.0.1:${server.address.port}/send"),method)
        }.apply { setConnectTimeout(3000); setReadTimeout(5000) }
        val caller = Executors.newSingleThreadExecutor()
        try {
            notification.setEnabled(true,"operator")
            val (pipeline,receive) = pipeline(notification)
            pipeline.processSingle(account(),mail(101),notifyGroups = true)
            notification.start()
            assertTrue(entered.await(4,TimeUnit.SECONDS))
            caller.submit {
                notification.setEnabled(false,"operator")
                pipeline.processSingle(account(),mail(102),notifyGroups = true)
            }.get(1,TimeUnit.SECONDS)
            Mockito.verify(receive).markSeen(account(),102)
            assertEquals(1,count())
            release.countDown()
            assertTrue(completed.await(2,TimeUnit.SECONDS))
            notification.stop()
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM expert_inbound_notification_outbox WHERE status='SENT'",Int::class.java))
            assertFalse(repository.settings().enabled)
        } finally {
            release.countDown()
            notification.stop()
            caller.shutdownNow()
            server.stop(0)
        }
    }

    private fun account() = MailSenderAccount(accountCode = "notification-test",senderEmail = "sender@example.test",senderName = "Sender",
        senderTitle = null,senderDisplayName = null,teamName = null,countryName = null,smtpHost = "smtp.test",smtpPort = 465,smtpUsername = "sender",smtpPassword = "pw",
        imapHost = "imap.test",imapPort = 993,imapUsername = "sender",imapPassword = "pw")
    private fun mail(uid: Long) = ReceivedMail(imapUid = uid,uidValidity = 7,from = "expert@example.test",subject = "Test subject",
        body = "Hello 😀\n> old message",messageId = "reply-$uid",inReplyTo = null,receivedAt = LocalDateTime.of(2026,10,8,12,0),attachments = emptyList(),bodyTruncated = true)

    /** Existing receipt core and real JDBC sinks; other existing business collaborators stay inert. */
    private fun pipeline(notification: ExpertInboundNotificationService): Pair<AutoMailReplyService,MailReceiveService> {
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        Mockito.`when`(accountService.resolveInboundOwner(Mockito.any(MailSenderAccount::class.java) ?: account())).thenReturn(account())
        Mockito.`when`(accountService.listAccounts()).thenReturn(listOf(account()))
        val aliases = Mockito.mock(ExpertEmailAliasService::class.java)
        Mockito.`when`(aliases.findContactByEmailOrAlias("expert@example.test")).thenReturn(contacts.findById(9101).get())
        val receive = Mockito.mock(MailReceiveService::class.java)
        val constructor = AutoMailReplyService::class.java.declaredConstructors.single()
        val real = mapOf<Class<*>,Any>(MailSenderAccountService::class.java to accountService,
            MailReceiveService::class.java to receive,ExpertContactRepository::class.java to contacts,
            InboundMailProcessingRepository::class.java to inbound,ExpertEmailAliasService::class.java to aliases,
            MailBodyCleaner::class.java to MailBodyCleaner(),SelfCheckProbeDetector::class.java to SelfCheckProbeDetector(),
            TransactionTemplate::class.java to TransactionTemplate(transactions))
        val arguments = constructor.parameterTypes.map { real[it] ?: Mockito.mock(it) }.toTypedArray()
        val pipeline = constructor.newInstance(*arguments) as AutoMailReplyService
        pipeline.setInboundNotificationService(notification)
        pipeline.requireInboundNotificationService()
        return pipeline to receive
    }
}
