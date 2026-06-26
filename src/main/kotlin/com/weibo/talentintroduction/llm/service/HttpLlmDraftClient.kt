package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

interface LlmDraftClient {
    fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String?
}

@Component
@ConditionalOnProperty(prefix = "talent-introduction.llm", name = ["enabled"], havingValue = "true")
class HttpLlmDraftClient(
    private val properties: LlmProperties,
    @Qualifier("llmRestTemplate") private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) : LlmDraftClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? {
        if (properties.apiUrl.isBlank()) {
            return null
        }
        val prompt = buildString {
            appendLine("Merge the following email reply draft into one smooth, deduplicated response.")
            appendLine("Match the expert's language if the inbound question is not English.")
            appendLine("Return only the reply body text, no subject line.")
            appendLine()
            appendLine("Inbound question:")
            appendLine(inboundQuestion.take(4000))
            appendLine()
            appendLine("Selected rule segments:")
            appendLine(ruleSegments.take(8000))
            if (freeText.isNotBlank()) {
                appendLine()
                appendLine("Operator free text:")
                appendLine(freeText.take(4000))
            }
        }
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (properties.apiKey.isNotBlank()) {
                setBearerAuth(properties.apiKey)
            }
        }
        val body = mapOf(
            "model" to properties.model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.3
        )
        return try {
            val response = restTemplate.postForEntity(
                properties.apiUrl,
                HttpEntity(objectMapper.writeValueAsString(body), headers),
                JsonNode::class.java
            ).body ?: return null
            response.path("choices").path(0).path("message").path("content").asText(null)
        } catch (ex: Exception) {
            log.warn("LLM stitch failed, caller should fall back: {}", ex.message)
            null
        }
    }
}
