package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.service.DiscoveryIdentityRecoveryService
import com.weibo.talentintroduction.discovery.service.RecoveryApplyResult
import com.weibo.talentintroduction.discovery.service.RecoveryApplyStatus
import com.weibo.talentintroduction.discovery.service.RecoveryPreview
import com.weibo.talentintroduction.discovery.service.RecoveryStatus
import org.junit.jupiter.api.Test
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 02（I-3）：维护 API 的身份与状态码契约。走真实 [AuthWebConfig]（`/api/` 登录拦截）与全局 advice：
 * 未登录 401、缺参 400、hash 变化 409、额度延期 503；客户端只能提交 docId 与两项 hash。
 */
@WebMvcTest(DiscoveryIdentityRecoveryController::class)
@Import(AuthWebConfig::class, DiscoveryIdentityRecoveryControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class DiscoveryIdentityRecoveryControllerTest {

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
    private lateinit var service: DiscoveryIdentityRecoveryService

    @MockBean
    private lateinit var authService: AuthService

    private fun sessionOf(username: String): MockHttpSession {
        Mockito.`when`(authService.findUser(username)).thenReturn(
            AdminUser(
                id = 1L, username = username, passwordHash = "x", mustChangePassword = false,
                createdAt = LocalDateTime.parse("2026-10-04T02:00:00"),
                updatedAt = LocalDateTime.parse("2026-10-04T02:00:00")
            )
        )
        return MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }
    }

    private fun previewBody(docId: String = "EMAIL-jane@uni.edu") = """{"docId":"$docId","actor":"evil"}"""

    private fun samplePreview(status: RecoveryStatus): RecoveryPreview = RecoveryPreview(
        docId = "EMAIL-jane@uni.edu", status = status, reason = null, identityRuleVersion = 1,
        snapshotHash = "s", proposalHash = "p", layers = emptyList(),
        sourceLocator = null, sourceUrl = null, evidenceSource = null, evidenceHash = null,
        proposedGivenNames = null, proposedFamilyNames = null, blockedReasons = emptyList(),
        observedAt = "2026-10-13T00:00:00Z"
    )

    private fun sampleApply(status: RecoveryApplyStatus): RecoveryApplyResult = RecoveryApplyResult(
        docId = "EMAIL-jane@uni.edu", status = status, reason = null, layers = emptyList(),
        admissionDecision = null, admissionIdentityChanged = null, observedAt = "2026-10-13T00:00:00Z"
    )

    @Test
    fun `every endpoint requires a login session`() {
        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/preview")
                .contentType(MediaType.APPLICATION_JSON).content(previewBody())
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/apply")
                .contentType(MediaType.APPLICATION_JSON).content("""{"docId":"x","snapshotHash":"s","proposalHash":"p"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a blank docId is rejected with 400`() {
        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON).content(previewBody(docId = "  "))
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `apply without both hashes is rejected with 400`() {
        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON).content("""{"docId":"x"}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `preview returns the server-computed hashes and ignores a body actor`() {
        Mockito.`when`(service.preview(anyString())).thenReturn(samplePreview(RecoveryStatus.RECOVERABLE))

        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON).content(previewBody())
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.snapshotHash").value("s"))
            .andExpect(jsonPath("$.proposalHash").value("p"))

        Mockito.verify(service).preview("EMAIL-jane@uni.edu")
    }

    @Test
    fun `a changed snapshot or proposal maps to 409`() {
        Mockito.`when`(service.apply(anyString(), anyString(), anyString()))
            .thenReturn(sampleApply(RecoveryApplyStatus.STALE))

        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"docId":"EMAIL-jane@uni.edu","snapshotHash":"old","proposalHash":"old"}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `a deferred budget maps to 503`() {
        Mockito.`when`(service.preview(anyString())).thenReturn(samplePreview(RecoveryStatus.DEFERRED))

        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON).content(previewBody())
        ).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `an applied recovery is returned with 200`() {
        Mockito.`when`(service.apply(anyString(), anyString(), anyString()))
            .thenReturn(sampleApply(RecoveryApplyStatus.APPLIED))

        mockMvc.perform(
            post("/api/expert-discovery/identity-recovery/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"docId":"EMAIL-jane@uni.edu","snapshotHash":"s","proposalHash":"p"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("APPLIED"))
    }
}
