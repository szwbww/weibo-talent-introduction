package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.config.MailReadAccessDeniedException
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.mail.controller.ExpertMailReadAccessController
import org.flywaydb.core.Flyway
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

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
@WebMvcTest(controllers = [ExpertMailReadAccessController::class])
@Import(
    AuthWebConfig::class,
    ExpertMailReadAccessService::class,
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

    @BeforeEach
    fun setUp() {
        cleanup()
        seedAccount("read-access-acc")
        seedContact(1L, "Alice Expert", "alice@example.org")
        seedContact(2L, "Bob Expert", "bob@example.org")
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
