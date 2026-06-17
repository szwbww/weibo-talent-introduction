package com.weibo.talentintroduction.auth.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(AuthController::class)
class AuthControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var authService: AuthService

    @Test
    fun `login creates session and changes session ID`() {
        val adminUser = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.login("admin", "admin")).thenReturn(adminUser)

        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn()

        val session = result.request.getSession(false)
        assertNotNull(session)
        assertEquals("admin", session?.getAttribute(AuthSessionKeys.USERNAME))
    }

    @Test
    fun `login changes session ID when session already exists and preserves attributes`() {
        val adminUser = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.login("admin", "admin")).thenReturn(adminUser)

        val existingSession = MockHttpSession()
        existingSession.setAttribute("temp", "value")
        val oldSessionId = existingSession.id

        val result = mockMvc.perform(
            post("/api/auth/login")
                .session(existingSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"admin"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        val session = result.request.getSession(false)
        assertNotNull(session)
        assertEquals("admin", session?.getAttribute(AuthSessionKeys.USERNAME))
        assertNotEquals(oldSessionId, session?.id)
        assertEquals("value", session?.getAttribute("temp"))
    }

    @Test
    fun `login failure returns BAD_REQUEST with ApiErrorResponse`() {
        Mockito.`when`(authService.login("admin", "wrong_pwd"))
            .thenThrow(IllegalArgumentException("用户名或密码错误"))

        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"admin","password":"wrong_pwd"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("用户名或密码错误"))
            .andExpect(jsonPath("$.detail").value("Bad Request"))
            .andReturn()

        val session = result.request.getSession(false)
        assertTrue(session == null || session.getAttribute(AuthSessionKeys.USERNAME) == null)
    }

    @Test
    fun `me returns authenticated false when no session`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.username").isEmpty)
    }

    @Test
    fun `me returns real-time user info when session valid`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val adminUser = AdminUser(
            id = 1L,
            username = "admin",
            passwordHash = "hash",
            mustChangePassword = false,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(authService.findUser("admin")).thenReturn(adminUser)

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.mustChangePassword").value(false))
    }

    @Test
    fun `me invalidates session and returns authenticated false when user not in DB`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        Mockito.`when`(authService.findUser("admin")).thenReturn(null)

        val result = mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.authenticated").value(false))
            .andReturn()

        assertTrue(result.request.getSession(false) == null || (result.request.getSession(false) as MockHttpSession).isInvalid)
    }

    @Test
    fun `logout invalidates session and returns 204`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        val result = mockMvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
            .andReturn()

        assertTrue(result.request.getSession(false) == null || (result.request.getSession(false) as MockHttpSession).isInvalid)
    }

    /**
     * 该测试仅验证 Controller 在 interceptor 未启用的 slice 中可容忍空 session，
     * 不代表生产 HTTP 端点对匿名用户公开。
     * 生产环境下匿名 logout 请求会被 AuthInterceptor 拦截返回 401。
     * @see com.weibo.talentintroduction.auth.config.AuthInterceptorTest
     */
    @Test
    fun `logout is idempotent when no session`() {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `changePassword success returns 204`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        mockMvc.perform(
            post("/api/auth/change-password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"old","newPassword":"new_password"}""")
        )
            .andExpect(status().isNoContent)

        Mockito.verify(authService).changePassword("admin", "old", "new_password")
    }

    @Test
    fun `changePassword returns 400 when old or new password blank`() {
        val session = MockHttpSession()
        session.setAttribute(AuthSessionKeys.USERNAME, "admin")

        mockMvc.perform(
            post("/api/auth/change-password")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"oldPassword":"","newPassword":"new_password"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
