package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertPage
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewRevokeResult
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewBatchPhase
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewBatchStatus
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewConflictException
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewService
import com.weibo.talentintroduction.discovery.service.DiscoveryReviewTimeoutException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 02（I-3）：审核 API 的身份与错误码契约。
 *
 * 走真实 [AuthWebConfig]（`/api/` 前缀登录拦截）与真实全局 advice：未登录 401、
 * actor 只来自会话（请求体伪造的 actor/username 被忽略且不改变 owner）、
 * 校验失败 400、状态冲突 409、扫描超时 503。服务本身 mock，不落库、不发信。
 */
@WebMvcTest(DiscoveryReviewController::class)
@Import(AuthWebConfig::class, DiscoveryReviewControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class DiscoveryReviewControllerTest {

    @TestConfiguration
    class KotlinObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper =
            ObjectMapper()
                .registerModule(KotlinModule())
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var service: DiscoveryReviewService

    @MockBean
    private lateinit var authService: AuthService

    private fun sessionOf(username: String): MockHttpSession {
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(
                id = 1L, username = username, passwordHash = "x", mustChangePassword = false,
                createdAt = LocalDateTime.parse("2026-10-04T02:00:00"), updatedAt = LocalDateTime.parse("2026-10-04T02:00:00")
            )
        )
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }
    }

    private fun anyRequest(): DiscoveryReviewPrepareRequest =
        Mockito.any(DiscoveryReviewPrepareRequest::class.java) ?: DiscoveryReviewPrepareRequest()

    @Test
    fun `unauthenticated access to every endpoint is rejected with 401`() {
        mockMvc.perform(get("/api/discovery/review/experts")).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/discovery/review/batches/prepare").contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/discovery/review/batches/b1/confirm").contentType(MediaType.APPLICATION_JSON).content("""{"batchHash":"h"}""")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/discovery/review/batches/b1")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/discovery/review/batches/b1/retry")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/discovery/review/batches/b1/cancel")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/discovery/review/history?docId=doc1")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/api/discovery/review/items/1/revoke")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an unknown session user is rejected with 401`() {
        Mockito.`when`(authService.findUser("ghost")).thenReturn(null)
        mockMvc.perform(
            get("/api/discovery/review/experts")
                .session(MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, "ghost") })
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `filters are forwarded to the service and the page is returned`() {
        Mockito.`when`(service.listExperts(any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
            .thenReturn(DiscoveryReviewExpertPage(0, 0, 20, emptyList()))

        mockMvc.perform(
            get("/api/discovery/review/experts?level=RAW&issue=ANY").session(sessionOf("op1"))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(0))

        Mockito.verify(service).listExperts("RAW", "discovered", 0, 20, null, "ANY", null)
    }

    @Test
    fun `prepare uses the session actor and ignores a body-supplied actor`() {
        Mockito.`when`(service.prepare(anyRequest(), anyString()))
            .thenReturn(DiscoveryReviewPrepareResult("b1", "h", 0, emptyList()))

        mockMvc.perform(
            post("/api/discovery/review/batches/prepare")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"IDS","action":"APPROVE","docIds":["doc1"],"actor":"evil","username":"evil"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.batchKey").value("b1"))

        val actor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(service).prepare(anyRequest(), actor.capture() ?: "")
        assertEquals("op1", actor.value)
    }

    @Test
    fun `a blank reject note is rejected with 400 by the domain rule`() {
        Mockito.`when`(service.prepare(anyRequest(), anyString()))
            .thenThrow(IllegalArgumentException("拒绝必须填写备注"))

        mockMvc.perform(
            post("/api/discovery/review/batches/prepare")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"IDS","action":"REJECT","docIds":["doc1"]}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `a stale batch hash maps to 409`() {
        Mockito.`when`(service.confirm(anyString(), anyString(), anyString()))
            .thenThrow(DiscoveryReviewConflictException("批次快照已变化，请重新准备"))

        mockMvc.perform(
            post("/api/discovery/review/batches/b1/confirm")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"batchHash":"deadbeef"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `a scan timeout maps to 503`() {
        Mockito.`when`(service.listExperts(any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
            .thenThrow(DiscoveryReviewTimeoutException("审核查询超出服务端时间预算"))

        mockMvc.perform(get("/api/discovery/review/experts").session(sessionOf("op1")))
            .andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `confirm and revoke carry the batch hash and the session actor`() {
        Mockito.`when`(service.confirm(anyString(), anyString(), anyString()))
            .thenReturn(DiscoveryReviewConfirmResult("b1", 0, 0, 0, 0, 0, emptyList()))
        Mockito.`when`(service.revoke(anyLong(), anyString(), anyString()))
            .thenReturn(DiscoveryReviewRevokeResult(7L, "doc1", "AUTO_PASSED", 2L))

        mockMvc.perform(
            post("/api/discovery/review/batches/b1/confirm")
                .session(sessionOf("op1")).contentType(MediaType.APPLICATION_JSON).content("""{"batchHash":"h"}""")
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/api/discovery/review/items/7/revoke")
                .session(sessionOf("op1")).contentType(MediaType.APPLICATION_JSON).content("""{"note":"复核"}""")
        ).andExpect(status().isOk)

        Mockito.verify(service).confirm("b1", "h", "op1")
        Mockito.verify(service).revoke(7L, "op1", "复核")
    }

    private fun batchStatus(phase: String): DiscoveryReviewBatchStatus =
        DiscoveryReviewBatchStatus(
            batchKey = "b-window", phase = phase, batchHash = "h", total = 0,
            applied = 0, stale = 0, failed = 0, cancelled = 0, pending = 0,
            prepareExecutionId = 5L, applyExecutionId = null, expiresAt = null,
            items = emptyList(), nextCursor = null
        )

    @Test
    fun `all-page prepare returns 202 and forwards the server-side filter`() {
        Mockito.`when`(service.prepareAllMatching(anyRequest(), any(), any(), any(), any(), anyString()))
            .thenReturn(batchStatus(DiscoveryReviewBatchPhase.PREPARING))

        mockMvc.perform(
            post("/api/discovery/review/batches/prepare?scope=ALL_MATCHING&tag=discovered&q=ada&issue=EMAIL_MISSING&requestKey=win-1")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"scope":"ALL_MATCHING","action":"APPROVE"}""")
        ).andExpect(status().isAccepted)
            .andExpect(jsonPath("$.phase").value("PREPARING"))

        val captured = ArgumentCaptor.forClass(DiscoveryReviewPrepareRequest::class.java)
        Mockito.verify(service).prepareAllMatching(
            captured.capture() ?: DiscoveryReviewPrepareRequest(),
            Mockito.eq("discovered") ?: "discovered",
            Mockito.eq("ada") ?: "ada",
            Mockito.eq("EMAIL_MISSING") ?: "EMAIL_MISSING",
            Mockito.eq("win-1") ?: "win-1",
            Mockito.eq("op1") ?: "op1"
        )
        assertEquals("ALL_MATCHING", captured.value.scope)
    }

    @Test
    fun `retry and cancel forward the batch key and the session actor`() {
        Mockito.`when`(service.retryBatch(anyString(), anyString()))
            .thenReturn(batchStatus(DiscoveryReviewBatchPhase.APPLYING))
        Mockito.`when`(service.cancelBatch(anyString(), anyString()))
            .thenReturn(batchStatus(DiscoveryReviewBatchPhase.CANCELLED))

        mockMvc.perform(post("/api/discovery/review/batches/b-window/retry").session(sessionOf("op1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phase").value("APPLYING"))
        mockMvc.perform(post("/api/discovery/review/batches/b-window/cancel").session(sessionOf("op1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.phase").value("CANCELLED"))

        Mockito.verify(service).retryBatch("b-window", "op1")
        Mockito.verify(service).cancelBatch("b-window", "op1")
    }

    @Test
    fun `batch status uses the id cursor and exposes the persistent phase`() {
        Mockito.`when`(service.batchStatus(anyString(), anyLong(), anyInt()))
            .thenReturn(batchStatus(DiscoveryReviewBatchPhase.READY))

        mockMvc.perform(
            get("/api/discovery/review/batches/b-window?afterId=120&limit=50").session(sessionOf("op1"))
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.phase").value("READY"))

        Mockito.verify(service).batchStatus("b-window", 120L, 50)
    }

    @Test
    fun `a missing batch status maps to 404 through the global advice`() {
        Mockito.`when`(service.batchStatus(anyString(), anyLong(), anyInt()))
            .thenThrow(NoSuchElementException("批次不存在：ghost"))

        mockMvc.perform(get("/api/discovery/review/batches/ghost").session(sessionOf("op1")))
            .andExpect(status().isNotFound)
    }
}
