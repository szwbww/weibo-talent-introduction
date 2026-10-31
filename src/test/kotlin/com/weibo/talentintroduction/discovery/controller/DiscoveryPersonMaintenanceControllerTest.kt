package com.weibo.talentintroduction.discovery.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.auth.config.AuthWebConfig
import com.weibo.talentintroduction.auth.domain.AdminUser
import com.weibo.talentintroduction.auth.service.AuthService
import com.weibo.talentintroduction.discovery.service.ApplyStatus
import com.weibo.talentintroduction.discovery.service.DiscoveryPersonMaintenanceService
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyGroup
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyOutcome
import com.weibo.talentintroduction.discovery.service.MaintenanceApplyRequest
import com.weibo.talentintroduction.discovery.service.MaintenanceDisposition
import com.weibo.talentintroduction.discovery.service.MaintenanceGroupApplyResult
import com.weibo.talentintroduction.discovery.service.MaintenancePreview
import com.weibo.talentintroduction.discovery.service.MaintenancePreviewOutcome
import com.weibo.talentintroduction.discovery.service.MaintenancePreviewRequest
import com.weibo.talentintroduction.discovery.service.MaintenanceReasonCodes
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackGroup
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackOutcome
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackProposal
import com.weibo.talentintroduction.discovery.service.MaintenanceRollbackRequest
import org.junit.jupiter.api.Test
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
 * 10（I-1/I-2/I-3）：维护 API 的身份与状态码契约。走真实 [AuthWebConfig]（`/api/` 登录拦截）与全局
 * advice：未登录 401、参数/规则版本非法 400、预览摘要不一致 409、其余 200。客户端只能提交冻结 ID、
 * 规则版本、预览摘要与逐组指纹，绝不携带证据/actor。
 */
@WebMvcTest(DiscoveryPersonMaintenanceController::class)
@Import(AuthWebConfig::class, DiscoveryPersonMaintenanceControllerTest.KotlinObjectMapperConfig::class)
@TestPropertySource(properties = ["talent-introduction.auth.enabled=true"])
class DiscoveryPersonMaintenanceControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private lateinit var resourceGuard: com.weibo.talentintroduction.mail.service.ExpertMailPermissionGuard

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
    private lateinit var service: DiscoveryPersonMaintenanceService

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

    private fun samplePreview(): MaintenancePreview = MaintenancePreview(
        ruleVersion = 1,
        generatedAt = "2026-10-13T00:00:00Z",
        groups = emptyList(),
        classCounts = mapOf(MaintenanceDisposition.KEEP_SINGLE.name to 2),
        totalDocuments = 2,
        previewDigest = "digest"
    )

    private fun sampleApply(): MaintenanceGroupApplyResult =
        MaintenanceGroupApplyResult("k", ApplyStatus.APPLIED, "p", 2, true, null)

    private fun sampleProposal(): MaintenanceRollbackProposal =
        MaintenanceRollbackProposal("k", listOf("doc-a"), "p", true, 1, null)

    @Test
    fun `every endpoint requires a login session`() {
        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/preview")
                .contentType(MediaType.APPLICATION_JSON).content("""{"frozenDocIds":["doc-a"]}""")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/apply")
                .contentType(MediaType.APPLICATION_JSON).content("""{"ruleVersion":1,"previewDigest":"d","groups":[]}""")
        ).andExpect(status().isUnauthorized)
        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/rollback-preview")
                .contentType(MediaType.APPLICATION_JSON).content("""{"groups":[["doc-a"]]}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `preview returns the server-computed classes`() {
        Mockito.`when`(
            service.preview(MaintenancePreviewRequest(1, listOf("doc-a"), listOf(listOf("doc-a"))))
        ).thenReturn(MaintenancePreviewOutcome.Ok(samplePreview()))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":1,"frozenDocIds":["doc-a"],"candidateGroups":[["doc-a"]]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalDocuments").value(2))
            .andExpect(jsonPath("$.previewDigest").value("digest"))
    }

    @Test
    fun `a rejected preview is a 400`() {
        Mockito.`when`(
            service.preview(MaintenancePreviewRequest(1, emptyList(), emptyList()))
        ).thenReturn(MaintenancePreviewOutcome.Rejected(MaintenanceReasonCodes.EMPTY_FROZEN_LIST))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON).content("""{"ruleVersion":1}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `apply returns per-group results with 200`() {
        Mockito.`when`(
            service.apply(
                MaintenanceApplyRequest(
                    1, "d", listOf("doc-a", "doc-b"),
                    listOf(MaintenanceApplyGroup(listOf("doc-a", "doc-b"), "f", "r"))
                )
            )
        ).thenReturn(MaintenanceApplyOutcome.Ok(listOf(sampleApply())))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":1,"previewDigest":"d","frozenDocIds":["doc-a","doc-b"],"groups":[{"memberDocIds":["doc-a","doc-b"],"expectedFingerprint":"f","expectedRelationFingerprint":"r"}]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("APPLIED"))
    }

    @Test
    fun `a mismatched preview digest maps to 409`() {
        Mockito.`when`(
            service.apply(
                MaintenanceApplyRequest(1, "stale", listOf("doc-a"), listOf(MaintenanceApplyGroup(listOf("doc-a"), "f", "r")))
            )
        ).thenReturn(MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.PREVIEW_DIGEST_MISMATCH))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":1,"previewDigest":"stale","frozenDocIds":["doc-a"],"groups":[{"memberDocIds":["doc-a"],"expectedFingerprint":"f","expectedRelationFingerprint":"r"}]}""")
        ).andExpect(status().isConflict)
    }

    @Test
    fun `an illegal rule version is a 400`() {
        Mockito.`when`(
            service.apply(MaintenanceApplyRequest(999, "d", emptyList(), emptyList()))
        ).thenReturn(MaintenanceApplyOutcome.Rejected(MaintenanceReasonCodes.RULE_VERSION_MISMATCH))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/apply")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":999,"previewDigest":"d","groups":[]}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `rollback preview returns proposals only and carries the expected revision`() {
        Mockito.`when`(
            service.rollbackPreview(
                MaintenanceRollbackRequest(1, listOf(MaintenanceRollbackGroup(listOf("doc-a"), 7L)))
            )
        ).thenReturn(MaintenanceRollbackOutcome.Ok(listOf(sampleProposal())))

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/rollback-preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":1,"groups":[{"memberDocIds":["doc-a"],"expectedRevision":7}]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].safeToRollback").value(true))
    }

    @Test
    fun `rollback preview without the expected revision reaches the service as unproven`() {
        Mockito.`when`(
            service.rollbackPreview(
                MaintenanceRollbackRequest(1, listOf(MaintenanceRollbackGroup(listOf("doc-a"), null)))
            )
        ).thenReturn(
            MaintenanceRollbackOutcome.Ok(
                listOf(MaintenanceRollbackProposal("k", listOf("doc-a"), "p", false, 1, MaintenanceReasonCodes.ROLLBACK_UNSAFE))
            )
        )

        mockMvc.perform(
            post("/api/expert-discovery/person-maintenance/rollback-preview")
                .session(sessionOf("op1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ruleVersion":1,"groups":[{"memberDocIds":["doc-a"]}]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].safeToRollback").value(false))
            .andExpect(jsonPath("$[0].reason").value(MaintenanceReasonCodes.ROLLBACK_UNSAFE))
    }
}
