package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.config.LlmProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

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

    private fun stubResponse(content: String) {
        val root = objectMapper.createObjectNode()
        val choices = objectMapper.createArrayNode()
        val choice = objectMapper.createObjectNode()
        val message = objectMapper.createObjectNode()
        message.put("content", content)
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
}
