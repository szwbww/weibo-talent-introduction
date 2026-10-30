package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.service.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(DiscoveryReviewCountsController::class)
@Import(AuthWebConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class DiscoveryReviewCountsControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard
    @Autowired private lateinit var mvc: MockMvc
    @MockBean private lateinit var service: DiscoveryReviewCountsService
    @MockBean private lateinit var authService: AuthService
    private val key = UUID.randomUUID().toString()

    private fun session(): MockHttpSession {
        val now = LocalDateTime.now()
        Mockito.`when`(authService.findUser("operator")).thenReturn(AdminUser(
            id = 1L, username = "operator", passwordHash = "hash", mustChangePassword = false, createdAt = now, updatedAt = now
        ))
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "operator") }
    }

    @Test
    fun `both endpoints require login and do not call the counts service`() {
        mvc.perform(post("/api/discovery/review/counts/refresh")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/discovery/review/counts?requestKey=$key")).andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `unknown session identity is rejected`() {
        mvc.perform(post("/api/discovery/review/counts/refresh").session(MockHttpSession().apply {
            setAttribute(AuthSessionKeys.USERNAME, "ghost")
        })).andExpect(status().isUnauthorized)
        Mockito.verifyNoInteractions(service)
    }

    @Test
    fun `refresh returns 202 and does not invent an execution id before onStarted`() {
        Mockito.`when`(service.refresh("operator")).thenReturn(DiscoveryReviewCountsAccepted(key, null, DiscoveryReviewCountsState.PREPARING))
        mvc.perform(post("/api/discovery/review/counts/refresh?actor=forged").session(session()))
            .andExpect(status().isAccepted).andExpect(jsonPath("$.requestKey").value(key))
            .andExpect(jsonPath("$.state").value("PREPARING")).andExpect(jsonPath("$.executionId").isEmpty)
        Mockito.verify(service).refresh("operator")
    }

    @Test
    fun `GET returns persisted status with the real execution id and never refreshes`() {
        Mockito.`when`(service.status(key)).thenReturn(DiscoveryReviewCountsStatus(key, 42L, DiscoveryReviewCountsState.RUNNING, 500, "2026-10-24T10:00:00"))
        mvc.perform(get("/api/discovery/review/counts?requestKey=$key").session(session()))
            .andExpect(status().isOk).andExpect(jsonPath("$.executionId").value(42))
            .andExpect(jsonPath("$.scanned").value(500)).andExpect(jsonPath("$.result").isEmpty)
        Mockito.verify(service).status(key)
        Mockito.verifyNoMoreInteractions(service)
    }

    @Test
    fun `executor rejection and database read failure retain stable codes`() {
        Mockito.`when`(service.refresh("operator")).thenThrow(DiscoveryReviewCountsBusyException())
        mvc.perform(post("/api/discovery/review/counts/refresh").session(session()))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("COUNTS_BUSY"))
        Mockito.`when`(service.status(key)).thenThrow(DiscoveryReviewCountsReadException(DataAccessResourceFailureException("offline")))
        mvc.perform(get("/api/discovery/review/counts?requestKey=$key").session(session()))
            .andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.code").value("COUNTS_DATABASE_FAILED"))
    }
}
