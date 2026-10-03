package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.auth.config.AuthSessionKeys
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.common.controller.GlobalExceptionHandler
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiReplyContextBuilder
import com.weibo.talentintroduction.llm.service.AiReplyContextService
import com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService
import com.weibo.talentintroduction.llm.service.AiReplyDraftService
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import com.weibo.talentintroduction.llm.service.AiTrainingQaService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 01b：mark-resolved 的身份只能来自 Session。
 *
 * 采用 standalone MockMvc + mock 服务走真实 JSON 解析与响应序列化路径，不直接调用控制器方法，
 * 以覆盖「无 Session/空白 Session 拒绝」「body 伪造身份被忽略」「旧 JSON 兼容」「服务失败不伪造成功」。
 */
class UnmatchedInboundMarkResolvedIdentityTest {
    private val unmatchedInboundMailService = Mockito.mock(UnmatchedInboundMailService::class.java)
    private val expertEmailAliasService = Mockito.mock(ExpertEmailAliasService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val pendingMailOperationService = Mockito.mock(PendingMailOperationService::class.java)
    private val operatorActionLogService = Mockito.mock(OperatorActionLogService::class.java)
    private val llmProperties = LlmProperties(enabled = true)
    private val autoReplyPreviewService = Mockito.mock(AutoReplyPreviewService::class.java)
    private val aiReplyDraftService = Mockito.mock(AiReplyDraftService::class.java)
    private val aiReplyDraftPreviewService = Mockito.mock(AiReplyDraftPreviewService::class.java)
    private val aiReplyContextBuilder = AiReplyContextBuilder()
    private val aiTrainingQaService = Mockito.mock(AiTrainingQaService::class.java)
    private val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)
    private val aiReplyReviewAuditService = Mockito.mock(AiReplyReviewAuditService::class.java)

    private val controller = UnmatchedInboundMailController(
        unmatchedInboundMailService,
        expertEmailAliasService,
        expertContactRepository,
        pendingMailOperationService,
        operatorActionLogService,
        llmProperties,
        autoReplyPreviewService,
        aiReplyDraftService,
        aiReplyDraftPreviewService,
        aiReplyContextBuilder,
        aiTrainingQaService,
        mailRecordRepository,
        aiReplyContextService,
        aiReplyReviewAuditService
    )

    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    /** 无 advice：401 由 ResponseStatusExceptionResolver 解析，成功回包走 Jackson。 */
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    /** 带业务异常映射：用于断言服务失败不会返回 PROCESSED。 */
    private val mockMvcWithAdvice: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    private fun session(username: String): MockHttpSession =
        MockHttpSession().apply { setAttribute(AuthSessionKeys.USERNAME, username) }

    private fun markResolvedUrl(id: Long) = "/api/mail/unmatched-inbound/$id/mark-resolved"

    @Test
    fun `missing session returns 401 and never calls the service`() {
        mockMvc.perform(
            post(markResolvedUrl(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"x"}""")
        ).andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    @Test
    fun `blank session username returns 401 and never calls the service`() {
        mockMvc.perform(
            post(markResolvedUrl(1L))
                .session(session("   "))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"x"}""")
        ).andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(pendingMailOperationService)
    }

    @Test
    fun `session identity overrides forged body identity and is returned`() {
        mockMvc.perform(
            post(markResolvedUrl(1L))
                .session(session("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resolvedBy":"other","operatorName":"other","note":"身份验收"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.processStatus").value("PROCESSED"))
            .andExpect(jsonPath("$.resolvedBy").value("admin"))

        val resolvedBy = ArgumentCaptor.forClass(String::class.java)
        val operatorName = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(pendingMailOperationService).markResolved(
            Mockito.eq(1L),
            resolvedBy.capture(),
            operatorName.capture(),
            Mockito.eq("身份验收")
        )
        assertEquals("admin", resolvedBy.value)
        assertEquals("admin", operatorName.value)
        Mockito.verify(pendingMailOperationService, Mockito.times(1)).markResolved(
            Mockito.anyLong(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any()
        )
    }

    @Test
    fun `minimal note null payload is accepted and note stays null`() {
        mockMvc.perform(
            post(markResolvedUrl(7L))
                .session(session("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":null}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.processStatus").value("PROCESSED"))
            .andExpect(jsonPath("$.resolvedBy").value("admin"))

        Mockito.verify(pendingMailOperationService).markResolved(
            Mockito.eq(7L),
            Mockito.eq("admin"),
            Mockito.eq("admin"),
            Mockito.isNull()
        )
    }

    @Test
    fun `legacy full payload is accepted and session identity wins`() {
        mockMvc.perform(
            post(markResolvedUrl(9L))
                .session(session("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"resolvedBy":"legacy","operatorName":"legacyop","note":"legacy note"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.resolvedBy").value("admin"))

        val resolvedBy = ArgumentCaptor.forClass(String::class.java)
        val operatorName = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(pendingMailOperationService).markResolved(
            Mockito.eq(9L),
            resolvedBy.capture(),
            operatorName.capture(),
            Mockito.eq("legacy note")
        )
        assertEquals("admin", resolvedBy.value)
        assertEquals("admin", operatorName.value)
    }

    @Test
    fun `service failure does not return PROCESSED`() {
        Mockito.`when`(
            pendingMailOperationService.markResolved(
                Mockito.anyLong(),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
            )
        ).thenThrow(IllegalStateException("boom"))

        val result = mockMvcWithAdvice.perform(
            post(markResolvedUrl(3L))
                .session(session("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"note":"x"}""")
        ).andReturn()

        assertFalse(
            result.response.contentAsString.contains("PROCESSED"),
            "服务失败时不得返回伪造的成功回包"
        )
        assertFalse(
            result.response.status == 200,
            "服务失败时不得返回 200"
        )

        Mockito.verify(pendingMailOperationService, Mockito.times(1)).markResolved(
            Mockito.eq(3L),
            Mockito.eq("admin"),
            Mockito.eq("admin"),
            Mockito.eq("x")
        )
    }
}
