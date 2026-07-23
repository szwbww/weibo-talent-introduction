package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.config.LlmProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.net.ConnectException
import java.net.SocketTimeoutException

class HttpLlmDraftClientTest {
    private val objectMapper = ObjectMapper()
    private val restTemplate = Mockito.mock(RestTemplate::class.java)

    private fun properties() = LlmProperties(
        enabled = true,
        apiUrl = "http://llm.local/v1/chat/completions",
        apiKey = "secret",
        model = "gpt-legacy",
        replyFlashModel = "provider-flash-id",
        replyProModel = "provider-pro-id"
    )

    private fun stubResponse(content: String?) {
        val root = objectMapper.createObjectNode()
        val choices = objectMapper.createArrayNode()
        val choice = objectMapper.createObjectNode()
        val message = objectMapper.createObjectNode()
        if (content != null) {
            message.put("content", content)
        }
        choice.set<ObjectNode>("message", message)
        choices.add(choice)
        root.set<ObjectNode>("choices", choices)
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(root))
    }

    @Test
    fun `chat uses legacy properties model while chatWithModel uses mapped provider ids`() {
        stubResponse("ok")
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val messages = listOf(LlmChatMessage("user", "hello"))
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)

        client.chat(messages, 0.2)
        client.chatWithModel(messages, 0.2, properties().replyFlashModel)
        client.chatWithModel(messages, 0.2, properties().replyProModel)

        Mockito.verify(restTemplate, Mockito.times(3)).postForEntity(
            Mockito.eq("http://llm.local/v1/chat/completions"),
            captor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val models = captor.allValues.map { entity ->
            val body = entity.body as String
            objectMapper.readTree(body).path("model").asText()
        }
        assertEquals(listOf("gpt-legacy", "provider-flash-id", "provider-pro-id"), models)
        assertTrue(models.none { it.contains("DEEPSEEK_V4") })
    }

    @Test
    fun `json observed chat requests provider JSON output without changing plain chat`() {
        stubResponse("{}")
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val messages = listOf(LlmChatMessage("user", "return json"))
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)

        client.chatWithModelObserved(messages, 0.2, properties().replyFlashModel)
        client.chatWithModelObservedJson(messages, 0.2, properties().replyFlashModel)

        Mockito.verify(restTemplate, Mockito.times(2)).postForEntity(
            Mockito.eq("http://llm.local/v1/chat/completions"),
            captor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val requests = captor.allValues.map { entity ->
            objectMapper.readTree(entity.body as String)
        }
        assertFalse(requests[0].has("response_format"))
        assertEquals("json_object", requests[1].path("response_format").path("type").asText())
    }

    @Test
    fun `AiReplyModel maps null to flash and rejects unknown`() {
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH, AiReplyModel.fromNullable(null))
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH, AiReplyModel.fromNullable("  "))
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO, AiReplyModel.fromNullable("DEEPSEEK_V4_PRO"))
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            AiReplyModel.fromNullable("DEEPSEEK_UNKNOWN")
        }
        val props = properties()
        assertEquals("provider-flash-id", AiReplyModel.DEEPSEEK_V4_FLASH.resolveProviderModel(props))
        assertEquals("provider-pro-id", AiReplyModel.DEEPSEEK_V4_PRO.resolveProviderModel(props))
    }

    // ── Transport classification (Phase 08 I-1) ──

    @Test
    fun `observed seam returns SUCCESS for valid content`() {
        stubResponse("Hello world")
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
        assertEquals("Hello world", result.content)
    }

    @Test
    fun `observed seam returns EMPTY_RESPONSE for blank content`() {
        stubResponse("   ")
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.EMPTY_RESPONSE, result.failureType)
        assertNull(result.content)
    }

    @Test
    fun `observed seam returns EMPTY_RESPONSE for null content`() {
        stubResponse(null)
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.EMPTY_RESPONSE, result.failureType)
    }

    @Test
    fun `observed seam returns TIMEOUT for read timeout`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(ResourceAccessException("Read timed out", SocketTimeoutException("Read timed out")))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.TIMEOUT, result.failureType)
    }

    @Test
    fun `observed seam returns NETWORK_ERROR for generic ConnectException regardless of message`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(ResourceAccessException("Connection timed out", ConnectException("Connection timed out")))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.NETWORK_ERROR, result.failureType)
    }

    @Test
    fun `observed seam returns NETWORK_ERROR for connection refused`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(ResourceAccessException("Connection refused"))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.NETWORK_ERROR, result.failureType)
    }

    @Test
    fun `observed seam returns RATE_LIMITED for HTTP 429`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests"))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.RATE_LIMITED, result.failureType)
    }

    @Test
    fun `observed seam returns PROVIDER_ERROR for HTTP 5xx`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error"))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.PROVIDER_ERROR, result.failureType)
    }

    @Test
    fun `observed seam returns PROVIDER_ERROR for HTTP 400`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.PROVIDER_ERROR, result.failureType)
    }

    @Test
    fun `observed seam returns NETWORK_ERROR for generic exception`() {
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(), Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("unknown"))
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.NETWORK_ERROR, result.failureType)
    }

    @Test
    fun `observed seam returns CLIENT_UNAVAILABLE for blank apiUrl`() {
        val props = properties().copy(apiUrl = "")
        val client = HttpLlmDraftClient(props, restTemplate, objectMapper)
        val result = client.chatWithModelObserved(
            listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id"
        )
        assertEquals(LlmChatFailureType.CLIENT_UNAVAILABLE, result.failureType)
    }

    @Test
    fun `old chat delegates to observed seam`() {
        stubResponse("delegated")
        val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper)
        val result = client.chat(listOf(LlmChatMessage("user", "hello")), null)
        assertEquals("delegated", result)
    }
}
