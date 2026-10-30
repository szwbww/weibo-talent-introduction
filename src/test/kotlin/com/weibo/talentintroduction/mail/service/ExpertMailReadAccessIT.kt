package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.controller.ExpertMailReadAccessController
import org.flywaydb.core.Flyway
import com.weibo.talentintroduction.mail.controller.MailboxConversationController
import com.weibo.talentintroduction.mail.repository.MailboxConversationRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import java.util.Optional

/**
 * 授权关系真实 MySQL + MVC 闭环（01；mysqlIt 门禁）。
 *
 * 与 [MailboxConversationControllerTest] 同款切片：`@WebMvcTest` + 真实
 * [ExpertMailReadAccessService] + 真实 MySQL DataSource/Flyway（迁移链含 V166），
 * 断言的是真实迁移/复合主键/FK/幂等/并发/撤销，而不是 mock 成功。
 *
 * - I-2：无 grant=false；PUT 两次只一行且 created_at 不刷新；并发 PUT 仅一行；
 *   DELETE 两次 0 行；删除 contact 级联清理；grant 读写不触碰其它业务表。
 * - I-3：匿名 401（真实 AuthInterceptor）、reader 403（READ_ONLY_FORBIDDEN）、
 *   其它已认证非 admin 403（ADMIN_REQUIRED，经 GlobalExceptionHandler 而非 500）、
 *   非法 contactId 400、不存在专家 404；body 伪造 admin/grantedBy 无效。
 * - 身份：granted_by 只取 admin session；username 固定 user1。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@WebMvcTest(controllers = [ExpertMailReadAccessController::class, MailboxConversationController::class])
@Import(
    AuthWebConfig::class,
    ExpertMailReadAccessService::class,
    ExpertMailPermissionGuard::class,
    MailboxConversationService::class,
    MailboxConversationRepository::class,
    ExpertMailReadAccessRealJdbcConfig::class
)
@TestPropertySource(
    properties = [
        "talent-introduction.auth.enabled=true",
        "spring.flyway.placeholder-replacement=false"
    ]
)
class ExpertMailReadAccessIT {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var service: ExpertMailReadAccessService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var authService: AuthService

    @Autowired private lateinit var dataSource: DataSource
    @MockBean private lateinit var accounts: MailSenderAccountRepository
    @MockBean private lateinit var contacts: ExpertContactRepository
    @MockBean private lateinit var materials: ExpertMaterialService
    @MockBean private lateinit var tags: InboundMailTagService
    @MockBean private lateinit var search: ExpertSearchService
    @MockBean private lateinit var records: MailRecordRepository
    @MockBean private lateinit var follows: ExpertFollowService
    @MockBean private lateinit var dismissals: ExpertRepliedDismissalService
    @MockBean private lateinit var pending: PendingMailOperationService
    @MockBean private lateinit var suspensions: MailboxSuspensionService

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount("read-access-acc")
        seedContact(1L, "Alice Expert", "alice@example.org")
        seedContact(2L, "Bob Expert", "bob@example.org")
        Mockito.`when`(accounts.findAllByAccountCodeNot(MailSenderAccountService.SIMULATOR_ACCOUNT_CODE))
            .thenReturn(listOf(MailSenderAccount(
                accountCode = "read-access-acc", senderEmail = "fixture@example.test", senderName = "Fixture",
                senderTitle = null, senderDisplayName = null, teamName = null, countryName = null,
                smtpHost = "127.0.0.1", smtpPort = 1, smtpUsername = "fixture", smtpPassword = "fixture",
                imapHost = "127.0.0.1", imapPort = 1, imapUsername = "fixture", imapPassword = "fixture",
                enabled = false
            )))
        for (id in listOf(1L, 2L)) {
            Mockito.`when`(contacts.findById(id)).thenReturn(Optional.of(
                ExpertContact(id = id, campaignId = id, orcidId = "fixture-$id",
                    expertEmail = "expert$id@example.test", expertName = "Expert $id")
            ))
        }
        listOf(MailReadOnlyPolicy.ADMIN_USERNAME, MailReadOnlyPolicy.READER_USERNAME, "op1").forEach { name ->
            Mockito.`when`(authService.findUser(name)).thenReturn(
                AdminUser(
                    id = 1L,
                    username = name,
                    passwordHash = "hash",
                    mustChangePassword = false,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                )
            )
        }
    }

    @AfterEach
    fun tearDown() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // I-3：身份边界
    // ------------------------------------------------------------------

    @Test
    fun `anonymous requests are rejected with 401`() {
        listOf(
            get("/api/mail/mailbox/conversations/1/read-access/user1"),
            put("/api/mail/mailbox/conversations/1/read-access/user1"),
            delete("/api/mail/mailbox/conversations/1/read-access/user1")
        ).forEach { request ->
            mockMvc.perform(request)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        }
        assertEquals(0L, grantRows(1L))
    }

    @Test
    fun `reader cannot read or change grants`() {
        val session = sessionOf(MailReadOnlyPolicy.READER_USERNAME)

        mockMvc.perform(get("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))

        assertEquals(0L, grantRows(1L), "reader 的授权写请求不得落库")
    }

    @Test
    fun `non admin sessions get 403 ADMIN_REQUIRED and cannot forge identity`() {
        val session = sessionOf("op1")

        mockMvc.perform(get("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.ADMIN_REQUIRED))
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/1/read-access/user1")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","grantedBy":"admin","canRead":true}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.ADMIN_REQUIRED))
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.ADMIN_REQUIRED))

        assertEquals(0L, grantRows(1L), "body 伪造 admin 不产生授权")
    }

    @Test
    fun `invalid and missing contacts return 400 and 404`() {
        val session = sessionOf(MailReadOnlyPolicy.ADMIN_USERNAME)

        mockMvc.perform(get("/api/mail/mailbox/conversations/0/read-access/user1").session(session))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        mockMvc.perform(get("/api/mail/mailbox/conversations/999/read-access/user1").session(session))
            .andExpect(status().isNotFound)
        mockMvc.perform(put("/api/mail/mailbox/conversations/999/read-access/user1").session(session))
            .andExpect(status().isNotFound)
        mockMvc.perform(delete("/api/mail/mailbox/conversations/999/read-access/user1").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `service rejects non admin actors and invalid ids even when called directly`() {
        assertThrows(MailReadAccessDeniedException::class.java) { service.getAccess(null, 1L) }
        assertThrows(MailReadAccessDeniedException::class.java) { service.grant("user1", 1L) }
        assertThrows(MailReadAccessDeniedException::class.java) { service.revoke("op1", 1L) }
        assertEquals(0L, grantRows(1L))

        assertThrows(IllegalArgumentException::class.java) { service.getAccess("admin", 0L) }
        assertThrows(IllegalArgumentException::class.java) { service.grant("admin", -1L) }
        assertThrows(NoSuchElementException::class.java) { service.revoke("admin", 999L) }
    }

    // ------------------------------------------------------------------
    // I-2：授权关系语义
    // ------------------------------------------------------------------

    @Test
    fun `grant lifecycle is idempotent and reflects through GET`() {
        val session = sessionOf(MailReadOnlyPolicy.ADMIN_USERNAME)

        mockMvc.perform(get("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("user1"))
            .andExpect(jsonPath("$.contactId").value(1))
            .andExpect(jsonPath("$.canRead").value(false))

        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(true))
        assertEquals(1L, grantRows(1L))

        // 把首次授予时间拨回过去，重复 PUT 不得刷新 created_at / granted_by。
        jdbcTemplate.update(
            "UPDATE expert_mail_read_access SET created_at = '2020-01-01 00:00:00', granted_by = 'admin' " +
                "WHERE username = 'user1' AND expert_contact_id = 1"
        )
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(true))

        assertEquals(1L, grantRows(1L), "重复 PUT 只保留一行")
        assertEquals("2020-01-01 00:00:00", createdAt(1L), "重复 PUT 不刷新首次授予时间")
        assertEquals("admin", jdbcTemplate.queryForObject(
            "SELECT granted_by FROM expert_mail_read_access WHERE username = 'user1' AND expert_contact_id = 1",
            String::class.java
        ))

        mockMvc.perform(get("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(true))

        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(false))
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(false))
        assertEquals(0L, grantRows(1L), "DELETE 幂等")

        // 撤销后重新授予 = 新时间（不继承旧行）。
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(true))
        assertEquals(1L, grantRows(1L))
        assertNotEquals("2020-01-01 00:00:00", createdAt(1L), "撤销后再授予产生新时间")
    }

    @Test
    fun `concurrent grants keep exactly one row`() {
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            repeat(8) {
                pool.submit {
                    latch.await()
                    runCatching { service.grant(MailReadOnlyPolicy.ADMIN_USERNAME, 1L) }
                        .onFailure { error -> errors.add(error) }
                }
            }
            latch.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "并发授予应在时限内完成")
        } finally {
            pool.shutdownNow()
        }

        assertTrue(errors.isEmpty(), "并发授予不应失败：$errors")
        assertEquals(1L, grantRows(1L), "复合主键 + upsert 保证并发仅一行")
        assertTrue(service.getAccess(MailReadOnlyPolicy.ADMIN_USERNAME, 1L))
    }

    @Test
    fun `deleting expert contact cascades grant rows`() {
        val session = sessionOf(MailReadOnlyPolicy.ADMIN_USERNAME)
        mockMvc.perform(put("/api/mail/mailbox/conversations/2/read-access/user1").session(session))
            .andExpect(status().isOk)
        assertEquals(1L, grantRows(2L))

        jdbcTemplate.update("DELETE FROM expert_contact WHERE id = 2")

        assertEquals(0L, grantRows(2L), "FK ON DELETE CASCADE 清理授权行")
    }

    @Test
    fun `grant operations only write the new access table`() {
        val tables = listOf(
            "expert_contact",
            "campaign",
            "expert_follow",
            "expert_mailbox_suspension",
            "mail_record",
            "inbound_mail_processing",
            "admin_user"
        )
        val before = countTables(tables)

        service.grant(MailReadOnlyPolicy.ADMIN_USERNAME, 1L)
        service.getAccess(MailReadOnlyPolicy.ADMIN_USERNAME, 1L)
        service.revoke(MailReadOnlyPolicy.ADMIN_USERNAME, 1L)

        assertEquals(before, countTables(tables), "授权读写不得改变其它业务表")
    }

    @Test
    fun `explicit levels switch while legacy PUT never downgrades FULL`() {
        val session = sessionOf("admin")
        val url = "/api/mail/mailbox/conversations/1/read-access/user1"
        mockMvc.perform(get(url).session(session)).andExpect(jsonPath("$.accessLevel").value("NONE"))
        for (level in listOf("FULL", "READ", "FULL")) {
            mockMvc.perform(put(url).session(session).contentType(MediaType.APPLICATION_JSON)
                .content("""{"accessLevel":"$level","username":"op1","grantedBy":"op1"}"""))
                .andExpect(status().isOk).andExpect(jsonPath("$.accessLevel").value(level))
                .andExpect(jsonPath("$.canRead").value(true))
        }
        jdbcTemplate.update("UPDATE expert_mail_read_access SET created_at='2020-01-01' WHERE expert_contact_id=1")
        mockMvc.perform(put(url).session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessLevel").value("FULL"))
        assertEquals("2020-01-01 00:00:00", createdAt(1))
        assertEquals("admin", jdbcTemplate.queryForObject(
            "SELECT granted_by FROM expert_mail_read_access WHERE expert_contact_id=1", String::class.java))
        assertEquals(ExpertMailAccessLevel.FULL, service.requireFull(1))
        assertEquals(ExpertMailAccessLevel.FULL, service.requireRead(1))
        service.setAccessLevel("admin", 1, ExpertMailAccessLevel.READ)
        assertThrows(MailReadAccessDeniedException::class.java) { service.requireFull(1) }
        service.revoke("admin", 1)
        assertThrows(MailReadAccessDeniedException::class.java) { service.requireRead(1) }
    }

    @Test
    fun `invalid explicit levels fail with 400 without inserting or modifying grants`() {
        val url = "/api/mail/mailbox/conversations/1/read-access/user1"
        for (existing in listOf(false, true)) {
            if (existing) service.setAccessLevel("admin", 1, ExpertMailAccessLevel.FULL)
            for (body in listOf("""{"accessLevel":"NONE"}""", """{"accessLevel":"ADMIN"}""",
                """{"accessLevel":"read"}""", """{"accessLevel":1}""",
                """{"accessLevel":null}""", """{}""")) {
                mockMvc.perform(put(url).session(sessionOf("admin"))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest)
                assertEquals(if (existing) ExpertMailAccessLevel.FULL else ExpertMailAccessLevel.NONE,
                    service.accessLevel(1))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.setAccessLevel("admin", 1, ExpertMailAccessLevel.NONE)
        }
        jdbcTemplate.update("UPDATE expert_mail_read_access SET access_level='UNKNOWN' WHERE expert_contact_id=1")
        assertThrows(IllegalStateException::class.java) { service.accessLevel(1) }
    }

    @Test
    fun `concurrent legacy PUTs cannot downgrade explicit FULL`() {
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(1)
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            repeat(16) { index ->
                pool.submit {
                    latch.await()
                    runCatching {
                        if (index % 2 == 0) service.setAccessLevel("admin", 1, ExpertMailAccessLevel.FULL)
                        else service.grant("admin", 1)
                    }.onFailure { errors.add(it) }
                }
            }
            latch.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        } finally { pool.shutdownNow() }
        assertTrue(errors.isEmpty(), "$errors")
        assertEquals(1L, grantRows(1))
        assertEquals(ExpertMailAccessLevel.FULL, service.accessLevel(1))
        assertEquals(mapOf(1L to ExpertMailAccessLevel.FULL, 2L to ExpertMailAccessLevel.NONE),
            service.accessLevels(listOf(1L, 2L, 1L)))
        assertTrue(service.accessLevels(emptyList()).isEmpty())
    }

    @Test
    fun `real auth projects live NONE READ FULL without opening later write handlers`() {
        for (id in listOf(1L, 2L)) {
            jdbcTemplate.update("""
                INSERT INTO mail_record (expert_contact_id, direction, mail_type, sender_account_code,
                    message_id, subject, body, send_status, sent_at)
                VALUES (?, 'OUTBOUND', 'INTRODUCTION', 'read-access-acc', ?, 'Fixture', 'Fixture body', 'SENT', NOW())
            """.trimIndent(), id, "fixture-$id")
        }
        val user = sessionOf("user1")
        val admin = sessionOf("admin")
        val messages = "/api/mail/mailbox/conversations/1/messages"
        mockMvc.perform(get(messages).session(user)).andExpect(status().isForbidden)
        mockMvc.perform(get(messages).session(admin)).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessLevel").value("NONE"))
        for (level in listOf(ExpertMailAccessLevel.READ, ExpertMailAccessLevel.FULL)) {
            service.setAccessLevel("admin", 1, level)
            mockMvc.perform(get(messages).session(user)).andExpect(status().isOk)
                .andExpect(jsonPath("$.accessLevel").value(level.name))
            mockMvc.perform(get("/api/mail/mailbox/conversations").session(user))
                .andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].contactId").value(1))
                .andExpect(jsonPath("$.items[0].accessLevel").value(level.name))
            mockMvc.perform(get("/api/mail/mailbox/conversations/2/messages").session(user))
                .andExpect(status().isForbidden)
            Mockito.clearInvocations(materials, search, records, pending, follows)
            if (level == ExpertMailAccessLevel.READ) {
                mockMvc.perform(put("/api/mail/mailbox/conversations/1/follow").session(user))
                    .andExpect(status().isForbidden)
                mockMvc.perform(post("/api/mail/mailbox/conversations/1/manual-rich-reply").session(user)
                    .contentType(MediaType.APPLICATION_JSON).content("""{"operatorName":"admin"}"""))
                    .andExpect(status().isForbidden)
            }
            mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(user)
                .contentType(MediaType.APPLICATION_JSON).content("""{"accessLevel":"FULL"}"""))
                .andExpect(status().isForbidden)
            Mockito.verifyNoInteractions(materials, search, records, pending, follows)
        }
        service.revoke("admin", 1)
        mockMvc.perform(get(messages).session(user)).andExpect(status().isForbidden)
        mockMvc.perform(get("/api/mail/mailbox/conversations").session(user))
            .andExpect(status().isOk).andExpect(jsonPath("$.total").value(0))
    }

    @Test
    fun `V169 grants upgrade to READ and new grants default to READ`() {
        val previous = Flyway.configure().dataSource(dataSource).placeholderReplacement(false)
            .cleanDisabled(false).target("169").load()
        previous.clean()
        assertEquals("169", previous.migrate().targetSchemaVersion)
        cleanup() // V16 seeds the simulator campaign with id=1.
        seedAccount("read-access-acc")
        seedContact(1, "Old Expert", "old@example.test")
        seedContact(2, "Second Expert", "second@example.test")
        jdbcTemplate.update("""
            INSERT INTO expert_mail_read_access (username, expert_contact_id, granted_by, created_at)
            VALUES ('user1', 1, 'admin', '2020-01-01'), ('user1', 2, 'admin', '2020-01-02')
        """.trimIndent())
        val result = Flyway.configure().dataSource(dataSource).placeholderReplacement(false).load().migrate()
        assertEquals("171", result.targetSchemaVersion)
        assertEquals(2, result.migrationsExecuted)
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_mail_read_access WHERE access_level='READ'", Long::class.java))
        assertEquals("2020-01-01 00:00:00", createdAt(1))
        assertEquals("NO", jdbcTemplate.queryForObject("""
            SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE()
              AND table_name='expert_mail_read_access' AND column_name='access_level'
        """.trimIndent(), String::class.java))
        assertEquals("READ", jdbcTemplate.queryForObject("""
            SELECT COLUMN_DEFAULT FROM information_schema.columns WHERE table_schema=DATABASE()
              AND table_name='expert_mail_read_access' AND column_name='access_level'
        """.trimIndent(), String::class.java))
    }

    @Test
    fun `fresh database applies the complete prefix through V171`() {
        val flyway = Flyway.configure().dataSource(dataSource).placeholderReplacement(false)
            .cleanDisabled(false).load()
        flyway.clean()
        assertEquals("171", flyway.migrate().targetSchemaVersion)
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM expert_mail_read_access", Long::class.java))
    }

    @Test
    fun `downgrade revoke and repeated grants reclaim only unfinished delegated rows`() {
        service.setAccessLevel("admin", 1, ExpertMailAccessLevel.FULL)
        jdbcTemplate.update("""INSERT INTO inbound_mail_processing
            (sender_account_code,imap_uid,from_email,received_at,process_status,process_reason,
             expert_contact_id,pending_owner_username)
            VALUES ('read-access-acc',1,'a@example.test',NOW(),'MANUAL_REVIEW','TEST',1,'user1'),
                   ('read-access-acc',2,'a@example.test',NOW(),'PROCESSED','MANUAL_RESOLVED',1,'user1'),
                   ('read-access-acc',3,'a@example.test',NOW(),'MANUAL_REVIEW','TEST',1,'admin')""")
        service.setAccessLevel("admin", 1, ExpertMailAccessLevel.READ)
        assertEquals(listOf("admin", "user1", "admin"), jdbcTemplate.queryForList(
            "SELECT pending_owner_username FROM inbound_mail_processing ORDER BY imap_uid", String::class.java))
        service.setAccessLevel("admin", 1, ExpertMailAccessLevel.FULL)
        service.grant("admin", 1)
        assertEquals("admin", jdbcTemplate.queryForObject(
            "SELECT pending_owner_username FROM inbound_mail_processing WHERE imap_uid=1", String::class.java))
        jdbcTemplate.update("UPDATE inbound_mail_processing SET pending_owner_username='user1' WHERE imap_uid=1")
        val tx = org.springframework.transaction.support.TransactionTemplate(DataSourceTransactionManager(dataSource))
        tx.execute { status ->
            service.revoke("admin", 1)
            assertEquals("admin", jdbcTemplate.queryForObject(
                "SELECT pending_owner_username FROM inbound_mail_processing WHERE imap_uid=1", String::class.java))
            status.setRollbackOnly()
        }
        assertEquals(ExpertMailAccessLevel.FULL, service.accessLevel(1))
        assertEquals("user1", jdbcTemplate.queryForObject(
            "SELECT pending_owner_username FROM inbound_mail_processing WHERE imap_uid=1", String::class.java))
        service.revoke("admin", 1)
        service.revoke("admin", 1)
        assertEquals("admin", jdbcTemplate.queryForObject(
            "SELECT pending_owner_username FROM inbound_mail_processing WHERE imap_uid=1", String::class.java))
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun sessionOf(username: String): MockHttpSession {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, username)
        return session
    }

    private fun grantRows(contactId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_mail_read_access WHERE username = 'user1' AND expert_contact_id = ?",
            Long::class.java,
            contactId
        ) ?: 0L

    private fun createdAt(contactId: Long): String =
        jdbcTemplate.queryForObject(
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') FROM expert_mail_read_access " +
                "WHERE username = 'user1' AND expert_contact_id = ?",
            String::class.java,
            contactId
        ) ?: ""

    private fun countTables(tables: List<String>): Map<String, Long> =
        tables.associateWith { table ->
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java) ?: 0L
        }

    private fun cleanup() {
        jdbcTemplate.update("DELETE FROM expert_mail_read_access")
        jdbcTemplate.update("DELETE FROM expert_mailbox_suspension")
        jdbcTemplate.update("DELETE FROM expert_replied_dismissal")
        jdbcTemplate.update("DELETE FROM expert_follow")
        jdbcTemplate.update("DELETE FROM inbound_mail_tag")
        jdbcTemplate.update("DELETE FROM inbound_mail_processing")
        jdbcTemplate.update("DELETE FROM mail_record")
        jdbcTemplate.update("DELETE FROM expert_contact")
        jdbcTemplate.update("DELETE FROM campaign")
        jdbcTemplate.update("DELETE FROM mail_sender_account")
    }

    private fun seedAccount(code: String) {
        jdbcTemplate.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (?, ?, ?, 'smtp.fixture', 465, ?, ?, 'imap.fixture', 993, ?, ?)
            """.trimIndent(),
            code, "$code@fixture.local", code, code, "pw", code, "pw"
        )
    }

    private fun seedContact(id: Long, name: String, email: String) {
        jdbcTemplate.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                "VALUES (?, ?, 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = 'read-access-acc'))",
            id, "READ-ACCESS-$id"
        )
        jdbcTemplate.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES (?, ?, ?, ?, ?, 'NEW')
            """.trimIndent(),
            id, id, "0000-0000-0000-%04d".format(id), email, name
        )
    }
}

/** 真实 MySQL + Flyway（test application.yml 数据源；迁移到最新含 V166）。 */
@Configuration
class ExpertMailReadAccessRealJdbcConfig {
    @Bean
    fun expertMailReadAccessDataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username:root}") username: String,
        @Value("\${spring.datasource.password:root}") password: String
    ): DataSource = DriverManagerDataSource(url, username, password)

    @Bean(initMethod = "migrate")
    fun expertMailReadAccessFlyway(dataSource: DataSource): Flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .placeholderReplacement(false)
            .load()

    @Bean
    fun expertMailReadAccessJdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

    @Bean
    fun expertMailReadAccessNamedJdbc(dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager::class)
    fun expertMailReadAccessTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)
}
