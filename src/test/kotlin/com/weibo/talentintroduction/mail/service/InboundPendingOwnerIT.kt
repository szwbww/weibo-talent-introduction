package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

/** Uses the explicitly supplied disposable MySQL, real JDBC repositories and real consumer methods. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false"])
@Import(ExpertMailReadAccessService::class)
class InboundPendingOwnerIT {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var dataSource: DataSource
    @Autowired lateinit var transactions: PlatformTransactionManager
    @Autowired lateinit var inbound: InboundMailProcessingRepository
    @Autowired lateinit var contacts: ExpertContactRepository
    @Autowired lateinit var access: ExpertMailReadAccessService
    private val now = LocalDateTime.of(2026, 10, 30, 12, 0)
    private val contactId = 9201L
    private fun tx() = TransactionTemplate(transactions)

    @BeforeEach fun setup() {
        jdbc.execute("DROP TRIGGER IF EXISTS owner_reclaim_failure")
        jdbc.update("DELETE FROM inbound_mail_tag")
        jdbc.update("DELETE FROM inbound_mail_processing")
        jdbc.update("DELETE FROM expert_mail_read_access")
        jdbc.update("INSERT INTO mail_sender_account (id,account_code,sender_email,sender_name,smtp_host,smtp_port,smtp_username,smtp_password,imap_host,imap_port,imap_username,imap_password) VALUES (9201,'owner-test','sender@example.test','Sender','127.0.0.1',1,'sender','pw','127.0.0.1',1,'sender','pw') ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO campaign (id,campaign_code,campaign_name,sender_account_id) VALUES (9201,'OWNER_TEST','Owner',9201) ON DUPLICATE KEY UPDATE id=id")
        jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email,expert_name,needs_manual_attention) VALUES (9201,9201,'owner-test','expert@example.test','Expert',TRUE) ON DUPLICATE KEY UPDATE needs_manual_attention=TRUE")
    }

    private fun account() = MailSenderAccount(accountCode = "owner-test", senderEmail = "sender@example.test", senderName = "Sender",
        senderTitle = null, senderDisplayName = null, teamName = null, countryName = null,
        smtpHost = "127.0.0.1", smtpPort = 1, smtpUsername = "sender", smtpPassword = "pw",
        imapHost = "127.0.0.1", imapPort = 1, imapUsername = "sender", imapPassword = "pw")
    private fun mail(uid: Long, withBody: Boolean = true, matched: Boolean = true) = ReceivedMail(
        imapUid = uid, uidValidity = 7, from = if (matched) "expert@example.test" else "unknown@example.test",
        subject = "Ownership $uid", body = "Original body $uid", messageId = "owner-$uid", inReplyTo = null,
        receivedAt = now, attachments = emptyList(), bodyTruncated = withBody)
    private fun row(uid: Long, owner: String = "user1", linked: Boolean = true, resolved: Boolean = false) = inbound.save(
        InboundMailProcessing(senderAccountCode = "owner-test", mailboxOwnerCode = "owner-test", uidValidity = 7,
            imapUid = uid, messageId = "row-$uid", fromEmail = "expert@example.test", subject = "Subject", body = "Body",
            receivedAt = now, processStatus = if (resolved) "PROCESSED" else "MANUAL_REVIEW",
            processReason = if (resolved) "MANUAL_RESOLVED" else "TEST", reasonType = if (resolved) "MANUAL_RESOLVED" else "QA_NO_MATCH",
            resolvedBy = if (resolved) "admin" else null, resolvedAt = if (resolved) now else null,
            expertContactId = if (linked) contactId else null, pendingOwnerUsername = owner, createdAt = now, updatedAt = now))
    private fun persisted(id: Long) = inbound.findById(id).get()
    private fun received(uid: Long) = inbound.findByMailboxOwnerCodeAndUidValidityAndImapUid("owner-test", 7, uid)!!

    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    class OwnerMvcConfig

    @Test fun `real AuthWebConfig owner routes enforce FULL and legacy queues match personal counts`() {
        jdbc.update("INSERT INTO expert_contact (id,campaign_id,orcid_id,expert_email,expert_name) VALUES (9202,9201,'owner-other','other@example.test','Other') ON DUPLICATE KEY UPDATE id=id")
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        val adminMail = row(600, "admin")
        val readerMail = row(601)
        val secondReaderMail = row(602)
        val unmatched = row(603, "admin", linked = false)
        val accountRepository = Mockito.mock(com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository::class.java)
        Mockito.`when`(accountRepository.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(account().copy(enabled = false)))
        val queue = consumer(UnmatchedInboundMailService::class.java, extra = mapOf(
            com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository::class.java to accountRepository))
        assertEquals(setOf(adminMail.id, unmatched.id), queue.listManualReviewQueue(actor = "admin").records.map { it.id }.toSet())
        assertEquals(2L, queue.listManualReviewQueue(actor = "admin").manualReviewTotal)
        assertEquals(2L, queue.listManualReviewQueue(actor = "user1").totalCount)
        assertEquals(1L, queue.listManualReviewQueue(actor = "admin", unmatchedOnly = true).totalCount)
        assertEquals(0L, queue.listManualReviewQueue(actor = "user1", unmatchedOnly = true).totalCount)
        val named = NamedParameterJdbcTemplate(dataSource)
        val repository = com.weibo.talentintroduction.mail.repository.MailboxConversationRepository(named)
        val suspension = MailboxSuspensionService(named)
        val search = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertSearchService::class.java)
        val materials = Mockito.mock(ExpertMaterialService::class.java)
        val delivery = Mockito.mock(MailDeliveryService::class.java)
        val conversations = consumer(MailboxConversationService::class.java, extra = mapOf(
            com.weibo.talentintroduction.mail.repository.MailboxConversationRepository::class.java to repository,
            com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository::class.java to accountRepository,
            ExpertMailReadAccessService::class.java to access,
            com.weibo.talentintroduction.expert.service.ExpertSearchService::class.java to search,
            ExpertMaterialService::class.java to materials))
        val controller = consumer(com.weibo.talentintroduction.mail.controller.MailboxConversationController::class.java,
            extra = mapOf(MailboxConversationService::class.java to conversations,
                MailboxSuspensionService::class.java to suspension))
        val auth = Mockito.mock(com.weibo.talentintroduction.auth.service.AuthService::class.java)
        for (username in listOf("admin", "user1")) {
            Mockito.`when`(auth.findUser(username)).thenReturn(com.weibo.talentintroduction.auth.domain.AdminUser(
                username = username, passwordHash = "unused", mustChangePassword = false, createdAt = now, updatedAt = now))
        }
        val context = org.springframework.web.context.support.GenericWebApplicationContext()
        context.servletContext = org.springframework.mock.web.MockServletContext()
        context.beanFactory.registerSingleton("auth", auth)
        context.beanFactory.registerSingleton("mapper", com.fasterxml.jackson.module.kotlin.jacksonObjectMapper())
        context.beanFactory.registerSingleton("guard", ExpertMailPermissionGuard(access, named))
        context.beanFactory.registerSingleton("controller", controller)
        context.beanFactory.registerSingleton("errors", com.weibo.talentintroduction.common.controller.GlobalExceptionHandler())
        org.springframework.context.annotation.AnnotatedBeanDefinitionReader(context).register(
            OwnerMvcConfig::class.java, com.weibo.talentintroduction.auth.config.AuthWebConfig::class.java)
        context.refresh()
        try {
            val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build()
            fun session(username: String) = org.springframework.mock.web.MockHttpSession().apply {
                setAttribute(com.weibo.talentintroduction.auth.config.AuthSessionKeys.USERNAME, username)
            }
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val base = "/api/mail/mailbox/conversations"
            val methods = listOf("GET", "PUT", "PATCH", "DELETE")
            for (level in listOf(ExpertMailAccessLevel.NONE, ExpertMailAccessLevel.READ, ExpertMailAccessLevel.FULL)) {
                if (level == ExpertMailAccessLevel.NONE) access.revoke("admin", contactId)
                else access.setAccessLevel("admin", contactId, level)
                if (level == ExpertMailAccessLevel.FULL) {
                    jdbc.update("UPDATE inbound_mail_processing SET pending_owner_username='user1' WHERE id IN (?,?)",
                        readerMail.id, secondReaderMail.id)
                }
                for (target in listOf(contactId, 9202L)) {
                    for (method in methods) {
                        val url = "$base/$target/suspension" + if (method == "PATCH") "/reason" else ""
                        val request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .request(org.springframework.http.HttpMethod.valueOf(method), url).session(session("user1"))
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""{"reason":"Waiting"}""")
                        val response = mvc.perform(request).andReturn().response
                        if (level != ExpertMailAccessLevel.FULL || target != contactId) assertEquals(403, response.status, "$level $method $target")
                        else assertEquals(200, response.status, "$method ${response.contentAsString}")
                    }
                }
            }
            Mockito.verifyNoInteractions(search, materials, delivery)
            // Explicit mixed-owner fixture for admin suspension routes; FULL grant now transfers old pending items.
            jdbc.update("UPDATE inbound_mail_processing SET pending_owner_username='admin' WHERE id=?", adminMail.id)
            for (method in methods) {
                val url = "$base/$contactId/suspension" + if (method == "PATCH") "/reason" else ""
                assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(org.springframework.http.HttpMethod.valueOf(method), url).session(session("admin"))
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("""{"reason":"Admin waiting"}"""))
                    .andReturn().response.status, "admin $method")
            }
            // Restore the two personal items after the downgrade test reclaimed them.
            jdbc.update("UPDATE inbound_mail_processing SET pending_owner_username='user1' WHERE id<>? AND expert_contact_id=?", adminMail.id, contactId)
            fun badge(user: String) = mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("$base/pending-badge").session(session(user)).param("username", "admin")).andReturn().response.contentAsString)["manualReviewTotal"].asLong()
            assertEquals(2L, badge("user1"))
            assertEquals(2L, badge("admin"))
            assertEquals(2L, suspension.get("user1", contactId).suspensionPendingCount)
            suspension.suspend("user1", contactId, "Wait")
            assertEquals(0L, badge("user1"))
            assertEquals(2L, badge("admin"))
            suspension.resume("user1", contactId)
            val personal = repository.pageConversations("user1",
                com.weibo.talentintroduction.mail.repository.MailboxConversationRepository.ConversationFilter(listOf("owner-test"), pendingOnly = true), 20, 0)
            assertEquals(1, personal.size)
            assertEquals(2L, personal.single().pendingCount)
            assertEquals(3L, personal.single().receivedCount)
            access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.READ)
            assertEquals(0L, badge("user1"))
            assertEquals(4L, badge("admin"))
            assertEquals(0L, queue.listManualReviewQueue(actor = "user1").totalCount)
            assertEquals("admin", persisted(readerMail.id!!).pendingOwnerUsername)
            access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
            assertEquals(3L, badge("user1"))
            assertEquals(1L, badge("admin"))
            assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("$base/$contactId/suspension").session(session("admin"))).andReturn().response.status)
        } finally { context.close() }
    }

    /** Same constructor wiring convention as ExpertInboundNotificationRepositoryIT, no production shim. */
    private fun <T> consumer(type: Class<T>, repository: InboundMailProcessingRepository = inbound,
                             extra: Map<Class<*>, Any> = emptyMap()): T {
        val bindings = mapOf<Class<*>, Any>(InboundMailProcessingRepository::class.java to repository,
            ExpertContactRepository::class.java to contacts) + extra
        val constructor = type.declaredConstructors.single { !it.isSynthetic }
        return type.cast(constructor.newInstance(*constructor.parameterTypes.map { bindings[it] ?: Mockito.mock(it) }.toTypedArray()))
    }
    private fun pipeline(detector: InboundAutoResponseDetector = InboundAutoResponseDetector(NamedParameterJdbcTemplate(dataSource))): AutoMailReplyService {
        val accounts = Mockito.mock(MailSenderAccountService::class.java)
        Mockito.`when`(accounts.resolveInboundOwner(Mockito.any(MailSenderAccount::class.java) ?: account())).thenReturn(account())
        Mockito.`when`(accounts.listAccounts()).thenReturn(listOf(account()))
        val aliases = Mockito.mock(ExpertEmailAliasService::class.java)
        Mockito.`when`(aliases.findContactByEmailOrAlias("expert@example.test")).thenAnswer { contacts.findById(contactId).get() }
        return consumer(AutoMailReplyService::class.java, extra = mapOf(
            MailSenderAccountService::class.java to accounts, ExpertEmailAliasService::class.java to aliases,
            MailBodyCleaner::class.java to MailBodyCleaner(), SelfCheckProbeDetector::class.java to SelfCheckProbeDetector(),
            TransactionTemplate::class.java to tx(), InboundAutoResponseDetector::class.java to detector))
    }

    @Test fun `both actual sinks allocate once from current grant and physical duplicates preserve owner`() {
        val pipeline = pipeline()
        var uid = 1L
        for (withBody in listOf(true, false)) {
            for (level in listOf(ExpertMailAccessLevel.NONE, ExpertMailAccessLevel.READ, ExpertMailAccessLevel.FULL)) {
                if (level == ExpertMailAccessLevel.NONE) access.revoke("admin", contactId)
                else access.setAccessLevel("admin", contactId, level)
                val message = mail(uid++, withBody)
                pipeline.processSingle(account(), message, skipImapAck = true)
                val original = received(message.imapUid)
                assertEquals(if (level == ExpertMailAccessLevel.FULL) "user1" else "admin", original.pendingOwnerUsername)
                assertEquals("owner-test", original.mailboxOwnerCode)
                assertEquals(message.body, original.body)
                access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
                val transferred = received(message.imapUid)
                assertEquals("user1", transferred.pendingOwnerUsername)
                assertEquals(original.copy(pendingOwnerUsername = "user1", updatedAt = transferred.updatedAt), transferred)
                assertEquals(SinglePipelineOutcome.DUPLICATE_IMAP_UID,
                    pipeline.processSingle(account(), message, skipImapAck = true).outcome)
                assertEquals(transferred, received(message.imapUid))
            }
            val unknown = mail(uid++, withBody, matched = false)
            pipeline.processSingle(account(), unknown, skipImapAck = true)
            assertNull(received(unknown.imapUid).expertContactId)
            assertEquals("admin", received(unknown.imapUid).pendingOwnerUsername)
        }
    }

    @Test fun `grant downgrade and revoke serialize before both actual creation sinks`() {
        var uid = 100L
        for (withBody in listOf(true, false)) {
            for (level in listOf(ExpertMailAccessLevel.FULL, ExpertMailAccessLevel.READ, ExpertMailAccessLevel.NONE)) {
                if (level == ExpertMailAccessLevel.FULL) access.revoke("admin", contactId)
                else access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
                val message = mail(uid++, withBody)
                val pipeline = pipeline()
                race({
                    if (level == ExpertMailAccessLevel.NONE) access.revoke("admin", contactId)
                    else access.setAccessLevel("admin", contactId, level)
                }, { pipeline.processSingle(account(), message, skipImapAck = true) })
                assertEquals(if (level == ExpertMailAccessLevel.FULL) "user1" else "admin", received(message.imapUid).pendingOwnerUsername)
            }
        }
    }

    @Test fun `receipt winning contact lock is transferred on grant and reclaimed on downgrade or revoke`() {
        var uid = 200L
        for (withBody in listOf(true, false)) {
            for (level in listOf(ExpertMailAccessLevel.FULL, ExpertMailAccessLevel.READ, ExpertMailAccessLevel.NONE)) {
                if (level == ExpertMailAccessLevel.FULL) access.revoke("admin", contactId)
                else access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val once = AtomicBoolean()
                val detector = Mockito.spy(InboundAutoResponseDetector(NamedParameterJdbcTemplate(dataSource)))
                Mockito.doAnswer { call ->
                    call.callRealMethod()
                    if (once.compareAndSet(false, true)) {
                        entered.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                    }
                    null
                }.`when`(detector).lockContact(contactId)
                val pipeline = pipeline(detector)
                val message = mail(uid++, withBody)
                val pool = Executors.newFixedThreadPool(2)
                try {
                    val receipt = pool.submit { pipeline.processSingle(account(), message, skipImapAck = true) }
                    assertTrue(entered.await(15, TimeUnit.SECONDS))
                    val started = CountDownLatch(1)
                    val change = pool.submit {
                        started.countDown()
                        if (level == ExpertMailAccessLevel.NONE) access.revoke("admin", contactId)
                        else access.setAccessLevel("admin", contactId, level)
                    }
                    assertTrue(started.await(15, TimeUnit.SECONDS))
                    release.countDown()
                    receipt.get(20, TimeUnit.SECONDS)
                    change.get(20, TimeUnit.SECONDS)
                    assertEquals(if (level == ExpertMailAccessLevel.FULL) "user1" else "admin", received(message.imapUid).pendingOwnerUsername)
                } finally { release.countDown(); pool.shutdownNow() }
            }
        }
    }

    /** Hold the same contact lock while the second real operation starts on another connection. */
    private fun race(first: () -> Unit, second: () -> Unit) {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val holder = pool.submit { tx().execute {
                assertEquals(contactId, inbound.lockPendingContact(contactId))
                first()
                locked.countDown()
                check(release.await(15, TimeUnit.SECONDS))
            } }
            assertTrue(locked.await(15, TimeUnit.SECONDS))
            val waiter = pool.submit { started.countDown(); second() }
            assertTrue(started.await(15, TimeUnit.SECONDS))
            release.countDown()
            holder.get(20, TimeUnit.SECONDS)
            waiter.get(20, TimeUnit.SECONDS)
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun `stale completion consumers preserve reclaimed owner and expert wide attention`() {
        var uid = 300L
        for (pendingConsumer in listOf(true, false)) {
            access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
            val target = row(uid++)
            val other = row(uid++, owner = "admin")
            val read = CountDownLatch(1)
            val release = CountDownLatch(1)
            val repository = Mockito.mock(InboundMailProcessingRepository::class.java,
                org.mockito.AdditionalAnswers.delegatesTo<InboundMailProcessingRepository>(inbound))
            val once = AtomicBoolean()
            Mockito.doAnswer {
                val result = inbound.findById(target.id!!)
                if (once.compareAndSet(false, true)) { read.countDown(); check(release.await(15, TimeUnit.SECONDS)) }
                result
            }.`when`(repository).findById(target.id!!)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val update = pool.submit { tx().execute {
                    if (pendingConsumer) consumer(PendingMailOperationService::class.java, repository)
                        .markResolved(target.id!!, "ignored", "admin", "done")
                    else consumer(UnmatchedInboundMailService::class.java, repository).markResolved(target.id!!, "admin", "done")
                } }
                assertTrue(read.await(15, TimeUnit.SECONDS))
                access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.READ)
                release.countDown()
                update.get(20, TimeUnit.SECONDS)
                val completed = persisted(target.id!!)
                assertEquals("admin", completed.pendingOwnerUsername)
                assertEquals("PROCESSED", completed.processStatus)
                assertEquals("MANUAL_RESOLVED", completed.reasonType)
                assertEquals("admin", completed.resolvedBy)
                assertNotNull(completed.resolvedAt)
                assertEquals(target.body, completed.body)
                assertEquals("MANUAL_REVIEW", persisted(other.id!!).processStatus)
                assertTrue(contacts.findById(contactId).get().needsManualAttention)
            } finally { release.countDown(); pool.shutdownNow() }
        }
    }

    @Test fun `binding remains admin reclassification preserves fields and completed reopen falls back after revocation`() {
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        val unbound = row(400, "admin", linked = false)
        race({ access.revoke("admin", contactId) }, {
            tx().execute { consumer(UnmatchedInboundMailService::class.java).bindToContact(unbound.id!!, contactId, "admin") }
        })
        val bound = persisted(unbound.id!!)
        assertEquals(contactId, bound.expertContactId)
        assertEquals("admin", bound.pendingOwnerUsername)
        assertEquals("MANUAL_BOUND", bound.processReason)
        assertEquals(unbound.reasonType, bound.reasonType)
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        val auto = row(401)
        race({ access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.READ) }, {
            tx().execute {
                assertEquals(1, inbound.reclassifyAutoResponse(auto.id!!, "AUTO_RESPONSE_HEADER", now.plusMinutes(1)))
            }
        })
        assertEquals("admin", persisted(auto.id!!).pendingOwnerUsername)
        assertEquals("AUTO_RESPONSE", persisted(auto.id!!).reasonType)
        assertEquals(auto.body, persisted(auto.id!!).body)
        assertNull(persisted(auto.id!!).resolvedAt)
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        val completed = row(402, resolved = true)
        race({ access.revoke("admin", contactId) }, {
            tx().execute { consumer(PendingMailOperationService::class.java).cancelResolved(completed.id!!, "admin", null) }
        })
        val reopened = persisted(completed.id!!)
        assertEquals("admin", reopened.pendingOwnerUsername)
        assertEquals("MANUAL_REVIEW", reopened.processStatus)
        assertEquals("MANUAL_REOPENED", reopened.processReason)
        assertNull(reopened.reasonType)
        assertNull(reopened.resolvedAt)
        assertNull(reopened.resolvedBy)
        assertEquals(0, inbound.reopenManualResolved(auto.id!!, now))
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        assertEquals("user1", persisted(completed.id!!).pendingOwnerUsername)
        assertEquals(2L, inbound.countByExpertContactIdAndProcessStatus(contactId, "MANUAL_REVIEW"))
    }

    @Test fun `grant and queue rollback together when reclaim fails`() {
        access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.FULL)
        val target = row(500)
        jdbc.execute("CREATE TRIGGER owner_reclaim_failure BEFORE UPDATE ON inbound_mail_processing FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected owner update failure'")
        try {
            assertThrows(Exception::class.java) { access.setAccessLevel("admin", contactId, ExpertMailAccessLevel.READ) }
            assertEquals(ExpertMailAccessLevel.FULL, access.accessLevel(contactId))
            assertEquals("user1", persisted(target.id!!).pendingOwnerUsername)
            assertThrows(Exception::class.java) { access.revoke("admin", contactId) }
            assertEquals(ExpertMailAccessLevel.FULL, access.accessLevel(contactId))
            assertEquals("user1", persisted(target.id!!).pendingOwnerUsername)
        } finally { jdbc.execute("DROP TRIGGER owner_reclaim_failure") }
    }

    @Test fun `V170 legacy rows upgrade through V172 with current FULL ownership`() {
        val previous = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).cleanDisabled(false).target("170").load()
        previous.clean()
        previous.migrate()
        setup()
        jdbc.update("INSERT INTO expert_mail_read_access(username,expert_contact_id,granted_by,created_at,access_level) VALUES('user1',?,'admin',NOW(),'FULL')", contactId)
        jdbc.update("INSERT INTO inbound_mail_processing (sender_account_code,imap_uid,from_email,received_at,process_status,process_reason,expert_contact_id) VALUES ('owner-test',900,'expert@example.test','2099-01-01','MANUAL_REVIEW','LEGACY',9201),('owner-test',901,'expert@example.test','2000-01-01','PROCESSED','LEGACY',9201)")
        val result = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
        assertEquals("172", result.targetSchemaVersion)
        assertEquals(2, result.migrationsExecuted)
        assertEquals(listOf("user1", "admin"), jdbc.queryForList("SELECT pending_owner_username FROM inbound_mail_processing ORDER BY imap_uid", String::class.java))
        assertEquals("NO", jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND column_name='pending_owner_username'", String::class.java))
        assertEquals("admin", jdbc.queryForObject("SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND column_name='pending_owner_username'", String::class.java))
        assertEquals(listOf("pending_owner_username", "process_status", "expert_contact_id", "sender_account_code"), jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='inbound_mail_processing' AND index_name='idx_inbound_pending_owner' ORDER BY seq_in_index", String::class.java))
    }
}
