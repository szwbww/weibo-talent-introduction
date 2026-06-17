package com.weibo.talentintroduction.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
class AuthProbeController {
    @GetMapping("/api/auth-probe")
    fun probe() = mapOf("ok" to true)
}

@WebMvcTest(controllers = [AuthProbeController::class])
@Import(AuthWebConfig::class, ObjectMapper::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class AuthInterceptorTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authService: AuthService

    @Test
    fun `OPTIONS request is allowed without authentication`() {
        mockMvc.perform(options("/api/auth-probe"))
            .andExpect(status().isOk)
    }

    @Test
    fun `request without session returns 401 with standard JSON format`() {
        mockMvc.perform(get("/api/auth-probe"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    @Test
    fun `request to change-password without session returns 401`() {
        mockMvc.perform(post("/api/auth/change-password"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
    }

    @Test
    fun `request with session but user not in DB invalidates session and returns 401`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        Mockito.`when`(authService.findUser("admin")).thenReturn(null)

        val result = mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andReturn()

        assertTrue(result.request.getSession(false) == null || (result.request.getSession(false) as MockHttpSession).isInvalid)
    }

    @Test
    fun `request with session when password change is required returns 403`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
            .andExpect(jsonPath("$.message").value("首次登录请先修改密码"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }

    @Test
    fun `request with session when password change is required allows exempted endpoints`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        // /api/auth/me is excluded in AuthWebConfig interceptor mapping, so it passes interceptor
        // Since we are mocking the controllers using WebMvcTest, we only register AuthProbeController.
        // Thus, any other endpoint (like /api/auth/me) returns 404 NOT FOUND rather than 403 FORBIDDEN.
        // A 404 response proves the request successfully bypassed the interceptor.
        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isNotFound)

        // /api/auth/change-password matches /api/** but is explicitly exempted in the interceptor logic itself
        mockMvc.perform(post("/api/auth/change-password").session(session))
            .andExpect(status().isNotFound)

        // /api/auth/logout is NOT excluded from interceptor (goes through preHandle),
        // but the interceptor internally allows it when mustChangePassword=true.
        // Since no AuthController is registered in this slice, it returns 404, proving the interceptor let it pass.
        mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `request with session after password change has succeeded returns 200`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val user = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(user)

        mockMvc.perform(get("/api/auth-probe").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
    }

    @Test
    fun `POST logout without session returns 401 UNAUTHORIZED`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("未登录"))
            .andExpect(jsonPath("$.detail").isEmpty)
    }
}
