package com.weibo.talentintroduction.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.repository.AdminUserRepository
import com.weibo.talentintroduction.auth.service.AdminUserBootstrap
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.LocalDateTime

@RestController
class AuthFlowProbeController {
    @GetMapping("/api/auth-flow-probe")
    fun probe() = mapOf("ok" to true)
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
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

    companion object {
        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

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
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var authService: AuthService

    @MockBean
    private lateinit var expertIndexService: ExpertIndexService

    @MockBean
    private lateinit var expertSearchService: ExpertSearchService

    @BeforeEach
    fun setUp() {
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

        // 6. Verify duplicate check: only one user exists
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
}
