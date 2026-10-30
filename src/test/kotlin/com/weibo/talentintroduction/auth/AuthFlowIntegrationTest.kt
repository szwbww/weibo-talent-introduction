package com.weibo.talentintroduction.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.TalentIntroductionApplication
import com.weibo.talentintroduction.mail.service.WorkerLifecycleTestConfig
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import com.weibo.talentintroduction.auth.service.AdminUserBootstrap
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.auth.service.ReadOnlyUserBootstrap
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.service.ExpertContactLocationCatalog
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.Timestamp
import java.time.LocalDateTime
import javax.sql.DataSource

@RestController
class AuthFlowProbeController {
    @GetMapping("/api/auth-flow-probe")
    fun probe() = mapOf("ok" to true)
}

@SpringBootTest(
    classes = [AuthFlowIntegrationTest.AuthFlowApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@Import(AuthFlowIntegrationTest.AuthFlowTestJdbcConfig::class)
@TestPropertySource(
    properties = [
        "talent-introduction.auth.enabled=true",
        "talent-introduction.mail-queue.enabled=false",
        "talent-introduction.scheduling.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.flyway.placeholder-replacement=false"
    ]
)
class AuthFlowIntegrationTest {

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration
    @org.springframework.boot.autoconfigure.AutoConfigurationPackage(basePackages = ["com.weibo.talentintroduction"])
    @ComponentScan(
        basePackages = ["com.weibo.talentintroduction"],
        excludeFilters = [
            ComponentScan.Filter(
                type = FilterType.CUSTOM,
                classes = [org.springframework.boot.context.TypeExcludeFilter::class]
            ),
            ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = [TalentIntroductionApplication::class, WorkerLifecycleTestConfig::class]
            )
        ]
    )
    class AuthFlowApplication

    companion object {
        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        /** 与实现常量一致的内置只读账号初口令；仅用于真实登录门禁。 */
        private const val READER_INITIAL_PASSWORD = "qfuser1"

        private const val ACCOUNT_CODE = "auth-flow-acc"

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for Flyway migration tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var adminUserRepository: AdminUserRepository

    @Autowired
    private lateinit var adminUserBootstrap: AdminUserBootstrap

    @Autowired
    private lateinit var readOnlyUserBootstrap: ReadOnlyUserBootstrap

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    /**
     * A5 接线提供的 `@Primary` 模板（见 [AuthFlowTestJdbcConfig]）；本类只在真实
     * MySQL 上做种子/清理/事实核对，不经过任何产品 SQL 旁路。
     */
    @Autowired
    private lateinit var authFlowNamedJdbc: NamedParameterJdbcTemplate

    @MockBean
    private lateinit var expertIndexService: ExpertIndexService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    /**
     * 环境中立化一个与本门禁无关的产品 bean：`ExpertContactLocationCatalog` 在加载随包
     * 时区快照时校验 `ZoneId.of("America/Coyhaique")`，而本环境 JDK 11 的 tzdb 尚无该
     * 时区（全量 `mvn test` 基线里 19 个 `ExpertContactLocationServiceTest.<init>:38`
     * 错误即同一预存缺陷）。该 bean 只要被实例化就让整个上下文启动失败，与认证/只读
     * 邮件权限无关。这里用 `@MockBean` 替换它，使真实 MySQL 登录门禁可以执行；产品代码
     * 与该缺陷本身不修改（见 05 execution 记录的偏差说明）。
     */
    @MockBean
    private lateinit var expertContactLocationCatalog: ExpertContactLocationCatalog

    private val jdbc: JdbcOperations
        get() = authFlowNamedJdbc.jdbcOperations

    @BeforeEach
    fun setUp() {
        cleanupBusinessRows()
        adminUserRepository.deleteAll()
    }

    @Test
    fun `test admin bootstrap creation and idempotency`() {
        // 1. Run bootstrap on empty table
        adminUserBootstrap.run(null)

        // 2. Verify admin user created with expected traits
        val user = adminUserRepository.findByUsername("admin")
        assertNotNull(user)
        assertEquals("admin", user!!.username)
        assertTrue(user.mustChangePassword)
        assertNotEquals("admin", user.passwordHash)
        assertTrue(passwordEncoder.matches("admin", user.passwordHash))

        // 3. Manually save updated password hash and set mustChangePassword = false
        val updatedUser = user.copy(
            passwordHash = passwordEncoder.encode("new_password"),
            mustChangePassword = false,
            updatedAt = LocalDateTime.now().plusSeconds(10)
        )
        adminUserRepository.save(updatedUser)

        // 4. Run bootstrap again to verify it is idempotent and doesn't overwrite
        adminUserBootstrap.run(null)

        // 5. Verify values are unchanged
        val reloaded = adminUserRepository.findByUsername("admin")
        assertNotNull(reloaded)
        assertEquals(updatedUser.passwordHash, reloaded!!.passwordHash)
        assertFalse(reloaded.mustChangePassword)
        assertTrue(passwordEncoder.matches("new_password", reloaded.passwordHash))

        // 6. Verify duplicate check: only one user exists（仅 admin bootstrap 场景仍是 1 个）
        val allUsers = adminUserRepository.findAll().toList()
        assertEquals(1, allUsers.size)
    }

    @Test
    fun `test full authentication flow and interceptor gate`() {
        // Ensure bootstrap admin user exists
        adminUserBootstrap.run(null)

        // --- Step 1: No session (Anonymous) ---
        // me returns authenticated=false
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.username").isEmpty)

        // protected API probe returns 401
        mockMvc.perform(get("/api/auth-flow-probe"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))

        // anonymous logout returns 401 (logout is NOT excluded from interceptor)
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))

        // --- Step 2: Login with wrong password ---
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrong_password"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"))

        // --- Step 3: Login with correct password ---
        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn()

        val session = loginResult.request.getSession(false) as MockHttpSession
        assertNotNull(session)
        assertEquals("admin", session.getAttribute(AuthSessionKeys.USERNAME))

        // --- Step 4: First login gate check (mustChangePassword = true) ---
        // protected API probe returns 403
        mockMvc.perform(get("/api/auth-flow-probe").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("首次登录请先修改密码"))

        // exempted endpoints should be allowed
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.mustChangePassword").value(true))

        // mustChangePassword=true: logout is allowed -> 204 and session invalidated
        val mustChangePwdLogoutResult = mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
            .andReturn()
        assertTrue((mustChangePwdLogoutResult.request.getSession(false) as? MockHttpSession)?.isInvalid ?: true)

        // --- Step 4b: Re-login for subsequent steps ---
        val reLoginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn()

        val session2 = reLoginResult.request.getSession(false) as MockHttpSession
        assertNotNull(session2)

        // --- Step 5: Change password ---
        // Old password wrong returns 400
        mockMvc.perform(
            post("/api/auth/change-password")
                .session(session2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"wrong_old_password","newPassword":"new_valid_password"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("原密码错误"))

        // Valid change password returns 204
        mockMvc.perform(
            post("/api/auth/change-password")
                .session(session2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"admin","newPassword":"new_valid_password"}""")
        )
            .andExpect(status().isNoContent)

        // Verify database fields updated
        val dbUser = adminUserRepository.findByUsername("admin")
        assertNotNull(dbUser)
        assertFalse(dbUser!!.mustChangePassword)
        assertTrue(passwordEncoder.matches("new_valid_password", dbUser.passwordHash))

        // --- Step 6: Post-change password access (same session) ---
        // probe should now succeed (200)
        mockMvc.perform(get("/api/auth-flow-probe").session(session2))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))

        // me returns mustChangePassword = false
        mockMvc.perform(get("/api/auth/me").session(session2))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.mustChangePassword").value(false))

        // --- Step 7: Logout ---
        mockMvc.perform(post("/api/auth/logout").session(session2))
            .andExpect(status().isNoContent)

        assertTrue(session2.isInvalid)

        // After logout, accessing probe returns 401
        mockMvc.perform(get("/api/auth-flow-probe").session(session2))
            .andExpect(status().isUnauthorized)

        // --- Step 8: Login with old password fails ---
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("用户名或密码错误"))

        // Login with new password succeeds and doesn't require change password
        val newLoginResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"new_valid_password"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andReturn()

        val newSession = newLoginResult.request.getSession(false) as MockHttpSession
        assertNotNull(newSession)

        // Access probe succeeds directly
        mockMvc.perform(get("/api/auth-flow-probe").session(newSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
    }

    // ------------------------------------------------------------------
    // 05 (I-1)：内置只读账号由 bootstrap 首次创建，重复启动不重置
    // ------------------------------------------------------------------

    @Test
    fun `bootstraps create exactly the two built-in accounts`() {
        runBothBootstraps()

        val usernames = adminUserRepository.findAll().map { it.username }.toSet()
        assertEquals(setOf("admin", "user1"), usernames, "只允许 admin 与精确 user1")

        val reader = adminUserRepository.findByUsername("user1")
        assertNotNull(reader)
        assertEquals("user1", reader!!.username)
        assertNotEquals(READER_INITIAL_PASSWORD, reader.passwordHash, "密码不得明文落库")
        assertTrue(passwordEncoder.matches(READER_INITIAL_PASSWORD, reader.passwordHash))
        assertFalse(reader.mustChangePassword, "user1 不应被迫进入不支持的改密流程")
        assertNull(reader.lastLoginAt, "建号时 lastLoginAt 为 null")
        assertNotNull(reader.createdAt)
        assertNotNull(reader.updatedAt)

        // I-3：建号不写授权表
        assertEquals(0L, countRows("expert_mail_read_access"), "bootstrap 不得写入任何授权")
    }

    @Test
    fun `restart does not reset the built-in accounts nor touch grants`() {
        runBothBootstraps()
        seedAccount()
        seedContact(1L, "Alice Expert", "alice@example.org")
        jdbc.update(
            "INSERT INTO expert_mail_read_access (username, expert_contact_id, granted_by, created_at) " +
                "VALUES ('user1', 1, 'admin', ?)",
            Timestamp.valueOf("2026-09-01 09:00:00")
        )

        // MySQL DATETIME 精度为秒：基准值先截断到秒再比对。
        val modifiedAt = LocalDateTime.now().plusHours(2).withNano(0)
        val originalReader = adminUserRepository.findByUsername("user1")!!
        val changedHash = passwordEncoder.encode("operator_changed_reader_password")
        adminUserRepository.save(
            originalReader.copy(
                passwordHash = changedHash,
                mustChangePassword = false,
                updatedAt = modifiedAt
            )
        )

        runBothBootstraps()

        val reader = adminUserRepository.findByUsername("user1")!!
        assertEquals(changedHash, reader.passwordHash, "重启不得重置密码")
        assertEquals(modifiedAt, reader.updatedAt, "重启不得改写时间戳")
        assertEquals(1L, countRows("expert_mail_read_access"), "重启不得增删授权")
        assertEquals(setOf("admin", "user1"), adminUserRepository.findAll().map { it.username }.toSet())
    }

    // ------------------------------------------------------------------
    // 05 (I-2)：真实登录/me/logout 与错误凭据
    // ------------------------------------------------------------------

    @Test
    fun `reader can log in me and log out while wrong credentials are rejected`() {
        runBothBootstraps()

        // 错误密码不创建已认证 session
        val wrongResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"user1","password":"wrong_password"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("用户名或密码错误"))
            .andReturn()
        val wrongSession = wrongResult.request.getSession(false)
        assertTrue(wrongSession == null || wrongSession.getAttribute(AuthSessionKeys.USERNAME) == null)

        // 白名单外用户名（含大小写变体）无法登录
        listOf("op1", "User1", "user2", " admin").forEach { username ->
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"$username","password":"qfuser1"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
        }

        // 正确凭据 + 会话 ID 轮换
        val existingSession = MockHttpSession()
        val oldSessionId = existingSession.id
        val loginResult = mockMvc.perform(
            post("/api/auth/login")
                .session(existingSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"user1","password":"qfuser1"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("user1"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andExpect(jsonPath("$.mailReadOnly").value(true))
            .andReturn()

        val session = loginResult.request.getSession(false) as MockHttpSession
        assertNotNull(session)
        assertEquals("user1", session.getAttribute(AuthSessionKeys.USERNAME))
        assertNotEquals(oldSessionId, session.id, "登录必须轮换 session id")

        // me 反映真实 DB 身份与派生只读标记
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("user1"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
            .andExpect(jsonPath("$.mailReadOnly").value(true))

        // 登录写 lastLoginAt（真实 DB 事实）
        assertNotNull(adminUserRepository.findByUsername("user1")!!.lastLoginAt)

        // logout 后 session 失效，me 回到未认证
        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
        assertTrue(session.isInvalid)
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))

        // 后台服务层确认 qfuser1 只用于首次种子（当前 hash 仍可校验）
        assertTrue(
            passwordEncoder.matches(
                READER_INITIAL_PASSWORD,
                adminUserRepository.findByUsername("user1")!!.passwordHash
            )
        )
    }

    // ------------------------------------------------------------------
    // 05 (I-3)：无授权 = 空列表/全部详情 403；授权 A 只读 A
    // ------------------------------------------------------------------

    @Test
    fun `reader without a grant sees an empty list and every timeline is forbidden`() {
        seedConversationFixtures()
        runBothBootstraps()
        val readerSession = loginAs("user1", READER_INITIAL_PASSWORD)

        mockMvc.perform(get("/api/mail/mailbox/conversations").session(readerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items.length()").value(0))

        listOf(1L, 2L).forEach { contactId ->
            mockMvc.perform(
                get("/api/mail/mailbox/conversations/$contactId/messages").session(readerSession)
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN))
        }
    }

    @Test
    fun `admin grant scopes the reader to exactly one expert and revoke closes it`() {
        seedConversationFixtures()
        runBothBootstraps()

        completeAdminFirstLogin()
        val adminSession = loginAs("admin", "admin")
        mockMvc.perform(
            put("/api/mail/mailbox/conversations/1/read-access/user1").session(adminSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("user1"))
            .andExpect(jsonPath("$.canRead").value(true))
        assertEquals(1L, countRows("expert_mail_read_access"))

        val readerSession = loginAs("user1", READER_INITIAL_PASSWORD)

        // 列表只剩已授权的 A
        mockMvc.perform(get("/api/mail/mailbox/conversations").session(readerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].contactId").value(1))
            .andExpect(jsonPath("$.items[0].email").value("alice@example.org"))

        // A 的正文可读
        mockMvc.perform(get("/api/mail/mailbox/conversations/1/messages").session(readerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].body").value("alice outbound body"))
            // I-3：reader 无附件投影，附件字段固定为空
            .andExpect(jsonPath("$.items[0].attachmentCount").value(0))
            .andExpect(jsonPath("$.items[0].firstAttachmentNames.length()").value(0))
            .andExpect(jsonPath("$.items[0].outboundAttachments.length()").value(0))

        // 未授权的 B 仍 403（不泄露存在性）
        mockMvc.perform(get("/api/mail/mailbox/conversations/2/messages").session(readerSession))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN))

        // reader 不能自行授权/查看授权
        listOf(
            get("/api/mail/mailbox/conversations/2/read-access/user1"),
            put("/api/mail/mailbox/conversations/2/read-access/user1"),
            delete("/api/mail/mailbox/conversations/1/read-access/user1")
        ).forEach { request ->
            mockMvc.perform(request.session(readerSession))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))
        }
        assertEquals(1L, countRows("expert_mail_read_access"), "reader 的授权请求不得落库")

        // admin 撤销后：列表为空、A 详情 403、授权行删除
        mockMvc.perform(
            delete("/api/mail/mailbox/conversations/1/read-access/user1").session(adminSession)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.canRead").value(false))
        assertEquals(0L, countRows("expert_mail_read_access"))

        mockMvc.perform(get("/api/mail/mailbox/conversations").session(readerSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items.length()").value(0))
        mockMvc.perform(get("/api/mail/mailbox/conversations/1/messages").session(readerSession))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.MAIL_READ_FORBIDDEN))
    }

    @Test
    fun `reader has no send attachment follow or change-password ability`() {
        seedConversationFixtures()
        runBothBootstraps()
        completeAdminFirstLogin()
        val adminSession = loginAs("admin", "admin")
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(adminSession))
            .andExpect(status().isOk)

        val readerSession = loginAs("user1", READER_INITIAL_PASSWORD)
        val readerHashBefore = adminUserRepository.findByUsername("user1")!!.passwordHash
        val mailRecordCountBefore = countRows("mail_record")

        // 发送 / 跟进 / 挂起 / 附件 / 改密 一律 403 READ_ONLY_FORBIDDEN
        val forbiddenRequests = listOf(
            post("/api/mail/mailbox/conversations/1/manual-rich-reply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"requestId":"reader-send-1","subject":"s","textBody":"x","htmlBody":"<p>x</p>"}"""),
            put("/api/mail/mailbox/conversations/1/follow"),
            put("/api/mail/mailbox/conversations/1/progress-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"progressStatus":"FOLLOWING"}"""),
            patch("/api/mail/mailbox/conversations/1/suspension/reason")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"reader attempt"}"""),
            get("/api/mail/mailbox/MAIL_RECORD/1/attachments"),
            get("/api/mail/mailbox/attachments/1/download"),
            get("/api/mail/conversations/1/outbound-attachments/1/download"),
            post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"qfuser1","newPassword":"another_password"}""")
        )
        forbiddenRequests.forEach { request ->
            mockMvc.perform(request.session(readerSession))
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.code").value(MailReadOnlyPolicy.READ_ONLY_FORBIDDEN))
        }

        assertEquals(mailRecordCountBefore, countRows("mail_record"), "reader 的发送请求不得落库")
        assertEquals(
            readerHashBefore,
            adminUserRepository.findByUsername("user1")!!.passwordHash,
            "reader 改密请求不得改变密码"
        )
        assertTrue(passwordEncoder.matches(READER_INITIAL_PASSWORD, readerHashBefore))

        // admin 仍可用原密码登录（回归）
        val reloadedAdminSession = loginAs("admin", "admin")
        mockMvc.perform(get("/api/mail/mailbox/conversations").session(reloadedAdminSession))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `review handlers use real login and enforce roles and current grants`() {
        seedConversationFixtures()
        runBothBootstraps()
        completeAdminFirstLogin()
        val admin = loginAs("admin", "admin")
        val reader = loginAs("user1", READER_INITIAL_PASSWORD)
        val mapper = ObjectMapper()
        val base = "/api/mail/mailbox/review-drafts"
        fun call(method: String, suffix: String, session: MockHttpSession?, body: Any? = null, expected: Int = 200): com.fasterxml.jackson.databind.JsonNode {
            val request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .request(org.springframework.http.HttpMethod.valueOf(method), base + suffix)
            if (session != null) request.session(session)
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))
            val response = mockMvc.perform(request).andExpect(status().`is`(expected)).andReturn().response
            return if (response.contentAsString.isBlank()) mapper.createObjectNode() else mapper.readTree(response.contentAsString)
        }
        val content = mapOf("contactId" to 1, "clientRequestId" to java.util.UUID.randomUUID().toString(), "subject" to "s", "html" to "<p>body</p>")
        val handlers = listOf("POST" to "", "GET" to "?contactId=1", "GET" to "/999", "PUT" to "/999",
            "POST" to "/999/submit", "POST" to "/999/withdraw", "DELETE" to "/999?expectedVersion=1",
            "POST" to "/999/approve", "POST" to "/999/return", "POST" to "/999/comments",
            "GET" to "/999/events", "GET" to "/999/context", "POST" to "/999/confirm-context", "POST" to "/999/translate")
        handlers.forEach { (method, suffix) -> call(method, suffix, null, content, 401) }
        listOf("approve", "return", "context", "confirm-context", "translate").forEach {
            call(if (it == "context") "GET" else "POST", "/999/$it", reader, mapOf("expectedVersion" to 1), 403)
        }
        call("POST", "", reader, content, 403)
        mockMvc.perform(put("/api/mail/mailbox/conversations/1/read-access/user1").session(admin)).andExpect(status().isOk)
        val draft = call("POST", "", reader, content)
        val id = draft["id"].asLong()
        var v = draft["version"].asLong()
        assertFalse(draft.has("expertEmail"))
        assertFalse(draft.has("recipientEmail"))
        listOf("/$id", "/$id/events", "/$id/context").forEach { call("GET", it, admin, expected = 404) }
        call("GET", "?contactId=1", reader)
        call("GET", "/$id", reader)
        listOf<Any>(1.2, -1, "1", java.math.BigInteger("9223372036854775808"), true).forEach { bad ->
            call("PUT", "/$id", reader, mapOf("expectedVersion" to bad, "subject" to "s", "html" to "<p>b</p>"), 422)
        }
        call("PUT", "/$id", reader, mapOf("expectedVersion" to v, "subject" to "s", "html" to "<p>b</p>", "ownerUsername" to "admin"), 422)
        call("POST", "", reader, content + ("contactId" to 2) + ("clientRequestId" to java.util.UUID.randomUUID().toString()), 403)
        call("POST", "", admin, content, 403)
        call("PUT", "/$id", admin, mapOf("expectedVersion" to v, "subject" to "s", "html" to "<p>b</p>"), 403)
        v = call("PUT", "/$id", reader, mapOf("expectedVersion" to v, "subject" to "updated", "html" to "<p>Updated</p>"))["version"].asLong()
        v = call("POST", "/$id/submit", reader, mapOf("expectedVersion" to v))["version"].asLong()
        call("GET", "?contactId=1", admin)
        call("GET", "/$id", admin)
        call("GET", "/$id/events", admin)
        call("POST", "/$id/comments", reader, mapOf("expectedVersion" to v, "text" to "reader comment"))
        call("POST", "/$id/comments", admin, mapOf("expectedVersion" to v, "text" to "admin comment"))
        call("GET", "/$id/events", reader)
        call("POST", "/$id/translate", admin, mapOf("expectedVersion" to v))
        v = call("POST", "/$id/withdraw", reader, mapOf("expectedVersion" to v))["version"].asLong()
        call("GET", "/$id", admin)
        v = call("POST", "/$id/submit", reader, mapOf("expectedVersion" to v))["version"].asLong()
        v = call("POST", "/$id/return", admin, mapOf("expectedVersion" to v, "reason" to "revise"))["version"].asLong()
        v = call("POST", "/$id/submit", reader, mapOf("expectedVersion" to v))["version"].asLong()
        val token = call("GET", "/$id/context", admin)["currentToken"].asText()
        v = call("POST", "/$id/approve", admin, mapOf("expectedVersion" to v, "observedContextToken" to token))["version"].asLong()
        call("POST", "/$id/confirm-context", admin, mapOf("expectedVersion" to v, "observedContextToken" to token))
        val other = call("POST", "", reader, content + ("clientRequestId" to java.util.UUID.randomUUID().toString()))
        call("DELETE", "/${other["id"].asLong()}?expectedVersion=1", reader)
        call("GET", "/${other["id"].asLong()}/events", reader)
        mockMvc.perform(delete("/api/mail/mailbox/conversations/1/read-access/user1").session(admin)).andExpect(status().isOk)
        listOf("/$id", "/$id/events", "?contactId=1").forEach {
            val denied = call("GET", it, reader, expected = 403)
            assertFalse(denied.has("html"))
            assertFalse(denied.has("text"))
        }
        call("POST", "/$id/comments", reader, mapOf("expectedVersion" to v, "text" to "revoked"), 403)
        assertEquals(2L, countRows("mail_record"), "Approval must not send mail")
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun runBothBootstraps() {
        adminUserBootstrap.run(null)
        readOnlyUserBootstrap.run(null)
    }

    private fun loginAs(username: String, password: String): MockHttpSession {
        val result: MvcResult = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        val session = result.request.getSession(false) as? MockHttpSession
        assertNotNull(session)
        return session!!
    }

    /**
     * 人工验收 A-1 的前置条件：admin 已完成首改密码。这里只把标志位置为 false，
     * 让业务接口不被首次登录闸门拦截（真实改密流程已由 `test full authentication flow and interceptor gate` 覆盖）。
     */
    private fun completeAdminFirstLogin() {
        val admin = adminUserRepository.findByUsername("admin") ?: return
        if (admin.mustChangePassword) {
            adminUserRepository.save(
                admin.copy(mustChangePassword = false, updatedAt = LocalDateTime.now())
            )
        }
    }

    private fun seedConversationFixtures() {
        seedAccount()
        seedContact(1L, "Alice Expert", "alice@example.org")
        seedContact(2L, "Bob Expert", "bob@example.org")
        insertOutbound(contactId = 1L, body = "alice outbound body")
        insertOutbound(contactId = 2L, body = "bob outbound body")
    }

    private fun seedAccount() {
        jdbc.update(
            """
            INSERT INTO mail_sender_account
                (account_code, sender_email, sender_name, smtp_host, smtp_port,
                 smtp_username, smtp_password, imap_host, imap_port, imap_username, imap_password)
            VALUES (?, ?, ?, 'smtp.fixture', 465, ?, ?, 'imap.fixture', 993, ?, ?)
            """.trimIndent(),
            ACCOUNT_CODE, "$ACCOUNT_CODE@fixture.local", ACCOUNT_CODE, ACCOUNT_CODE, "pw", ACCOUNT_CODE, "pw"
        )
    }

    private fun seedContact(id: Long, name: String, email: String) {
        jdbc.update(
            "INSERT INTO campaign (id, campaign_code, campaign_name, sender_account_id) " +
                "VALUES (?, ?, 'Fixture', (SELECT id FROM mail_sender_account WHERE account_code = ?))",
            id, "AUTH-FLOW-$id", ACCOUNT_CODE
        )
        jdbc.update(
            """
            INSERT INTO expert_contact (id, campaign_id, orcid_id, expert_email, expert_name, current_status)
            VALUES (?, ?, ?, ?, ?, 'NEW')
            """.trimIndent(),
            id, id, "0000-0000-0000-%04d".format(id), email, name
        )
    }

    private fun insertOutbound(contactId: Long, body: String) {
        jdbc.update(
            """
            INSERT INTO mail_record
                (expert_contact_id, direction, mail_type, sender_account_code, triggered_by,
                 message_id, subject, body, send_status, sent_at, created_at)
            VALUES (?, 'OUTBOUND', 'INTRODUCTION', ?, 'SYSTEM', ?, ?, ?, 'SENT', ?, ?)
            """.trimIndent(),
            contactId, ACCOUNT_CODE, "auth-flow-out-$contactId", "subject-$contactId", body,
            Timestamp.valueOf("2026-09-01 09:00:00"), Timestamp.valueOf("2026-09-01 09:00:00")
        )
    }

    private fun countRows(table: String): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java) ?: 0L

    private fun cleanupBusinessRows() {
        jdbc.update("DELETE FROM mailbox_review_draft_event")
        jdbc.update("DELETE FROM mailbox_review_draft")
        jdbc.update("DELETE FROM expert_mail_read_access")
        jdbc.update("DELETE FROM expert_mailbox_suspension")
        jdbc.update("DELETE FROM expert_replied_dismissal")
        jdbc.update("DELETE FROM expert_follow")
        jdbc.update("DELETE FROM inbound_mail_tag")
        jdbc.update("DELETE FROM inbound_mail_processing")
        jdbc.update("DELETE FROM mail_record")
        jdbc.update("DELETE FROM expert_contact")
        jdbc.update("DELETE FROM campaign")
        jdbc.update("DELETE FROM mail_sender_account")
    }

    /**
     * A5（人工批准的受限例外）：测试源树里的顶层 `@Configuration`（如
     * `MailboxConversationRealJdbcConfig`/`ExpertMailReadAccessRealJdbcConfig`）会被
     * 组件扫描进完整 `@SpringBootTest` 上下文，导致 `DataSource`/`JdbcTemplate`/
     * `NamedParameterJdbcTemplate` 各有多个候选而无法注入。
     *
     * A5 只列了「一个 `@Primary NamedParameterJdbcTemplate`」；实测该最小配方仍会在
     * `expertMailReadAccessFlyway` 参数解析处抛
     * `NoUniqueBeanDefinitionException ... found 2: mailboxConversationDataSource,
     * expertMailReadAccessDataSource`（见 05 execution 记录）。因此这里同样把
     * `DataSource`/`JdbcTemplate` 标为 `@Primary`：三者都指向同一个容器库
     * （`spring.datasource.*`），不引入第二个数据源、不注册任何产品 bean。
     *
     * 只影响本测试上下文：产品代码与其它测试不变。
     */
    @TestConfiguration
    class AuthFlowTestJdbcConfig {
        @Bean
        @Primary
        fun authFlowDataSource(
            @Value("\${spring.datasource.url}") url: String,
            @Value("\${spring.datasource.username:test}") username: String,
            @Value("\${spring.datasource.password:test}") password: String
        ): DataSource = DriverManagerDataSource(url, username, password)

        @Bean
        @Primary
        fun authFlowJdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)

        @Bean
        @Primary
        fun authFlowNamedParameterJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate =
            NamedParameterJdbcTemplate(dataSource)

        /**
         * Spring Data JDBC 的仓库事务代理按**固定名字** `transactionManager` 解析事务管理器；
         * 两个泄漏的 `*RealJdbcConfig` 各自用 `@ConditionalOnMissingBean(PlatformTransactionManager)`
         * 注册了带前缀名字的管理器，使 Boot 的 `transactionManager` 自动配置退避，
         * 于是 `BatchSendSettingService` 等仓库调用在启动期抛
         * `No bean named 'transactionManager' available`。这里补上同名 `@Primary` 管理器。
         */
        @Bean
        @Primary
        fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
            DataSourceTransactionManager(dataSource)
    }
}
