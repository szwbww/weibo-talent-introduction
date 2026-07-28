package com.weibo.talentintroduction.llm.controller

import com.weibo.talentintroduction.llm.service.AiReplyGenerationCoordinator
import com.weibo.talentintroduction.llm.service.AiReplyGenerationOperation
import com.weibo.talentintroduction.llm.service.AiReplyTimeoutPolicy
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapRequest
import com.weibo.talentintroduction.llm.service.TrustReplyBootstrapResponse
import com.weibo.talentintroduction.llm.service.TrustReplySourceRef
import com.weibo.talentintroduction.llm.service.TrustReplySourceType
import com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.mockito.Mockito
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

class TrustReplyWorkbenchControllerTest {
    private val service = Mockito.mock(TrustReplyWorkbenchService::class.java)
    private val coordinator = Mockito.mock(AiReplyGenerationCoordinator::class.java)
    private val controller = TrustReplyWorkbenchController(service, coordinator)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `bootstrap maps exact source`() {
        val source = TrustReplySourceRef(TrustReplySourceType.TRAINING_MAIL, 123L)
        val bootstrap = TrustReplyBootstrapResponse(
            source = source,
            sourceVersion = "source-v1",
            inboundSubject = "Subject",
            inboundText = "Question",
            expertName = "Test",
            expertEmail = "test@example.com",
            llmEnabled = false,
            availableModels = listOf("DEEPSEEK_V4_FLASH", "DEEPSEEK_V4_PRO"),
            defaultModel = "DEEPSEEK_V4_FLASH",
            suggestedFactIds = listOf(9L),
            canonicalFactIds = listOf(9L),
            rulesByCategory = emptyList(),
            requestCoverage = emptyList(),
            draftReadiness = "READY",
            evidenceSetVersion = "evidence-v1"
        )
        Mockito.`when`(service.bootstrap(TrustReplyBootstrapRequest(source, listOf(9L)))).thenReturn(bootstrap)
        assertEquals(bootstrap, controller.bootstrap(TrustReplyBootstrapHttpRequest(
            source = TrustReplySourceHttpRequest("TRAINING_MAIL", 123L),
            requestedFactIds = listOf(9L)
        )))

    }

    @Test
    fun `stream and cancel use canonical source scope`() {
        val id = "00000000-0000-0000-0000-000000000004"
        val emitter = SseEmitter()
        Mockito.`when`(
            coordinator.start(
                Mockito.eq("LIVE_INBOUND:77") ?: "LIVE_INBOUND:77",
                Mockito.eq(id) ?: id,
                Mockito.any(AiReplyTimeoutPolicy::class.java) ?: AiReplyTimeoutPolicy(10, 30),
                Mockito.any<AiReplyGenerationOperation>() ?: { _, _, _ -> "unused" }
            )
        )
            .thenReturn(emitter)
        val response = controller.generateStream(
            TrustReplyGenerationHttpRequest(
                source = TrustReplySourceHttpRequest("LIVE_INBOUND", 77L),
                expectedSourceVersion = "source-v1",
                generationId = id
            )
        )
        assertEquals(emitter, response.body)
        Mockito.`when`(coordinator.cancel("LIVE_INBOUND:77", id)).thenReturn("CANCEL_REQUESTED")
        assertEquals("CANCEL_REQUESTED", controller.cancel(
            id,
            TrustReplyCancelHttpRequest(TrustReplySourceHttpRequest("LIVE_INBOUND", 77L))
        ).status)
    }

    @Test
    fun `http contract rejects invalid source and generation id with stable codes`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"UNKNOWN","sourceId":1}}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_SOURCE_INVALID"))

        mockMvc.perform(
            post("/api/trust-reply/workbench/generations/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":1},"expectedSourceVersion":"v","generationId":"not-uuid"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("TRUST_REPLY_GENERATION_ID_INVALID"))
    }

    @Test
    fun `synchronous generation endpoint is unavailable`() {
        mockMvc.perform(
            post("/api/trust-reply/workbench/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"source":{"sourceType":"TRAINING_MAIL","sourceId":1},"expectedSourceVersion":"v"}""")
        )
            .andExpect(status().isNotFound)
    }
}
