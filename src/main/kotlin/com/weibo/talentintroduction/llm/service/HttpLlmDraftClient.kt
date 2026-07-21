package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate

data class LlmChatMessage(
    val role: String,
    val content: String
)

enum class AiReplyModel {
    DEEPSEEK_V4_FLASH,
    DEEPSEEK_V4_PRO;

    fun resolveProviderModel(properties: LlmProperties): String = when (this) {
        DEEPSEEK_V4_FLASH -> properties.replyFlashModel
        DEEPSEEK_V4_PRO -> properties.replyProModel
    }

    companion object {
        fun fromNullable(value: String?): AiReplyModel {
            if (value.isNullOrBlank()) {
                return DEEPSEEK_V4_FLASH
            }
            return try {
                valueOf(value.trim())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown AI reply model: $value")
            }
        }
    }
}

enum class LlmChatFailureType {
    SUCCESS,
    TIMEOUT,
    RATE_LIMITED,
    NETWORK_ERROR,
    PROVIDER_ERROR,
    EMPTY_RESPONSE,
    CLIENT_UNAVAILABLE
}

data class LlmChatResult(
    val content: String?,
    val failureType: LlmChatFailureType = LlmChatFailureType.SUCCESS
)

interface LlmDraftClient {
    fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String?

    fun chat(messages: List<LlmChatMessage>, temperature: Double? = null): String?

    fun chatWithModel(
        messages: List<LlmChatMessage>,
        temperature: Double? = null,
        providerModel: String
    ): String? = chat(messages, temperature)

    fun chatWithModelObserved(
        messages: List<LlmChatMessage>,
        temperature: Double? = null,
        providerModel: String
    ): LlmChatResult {
        return try {
            val content = chatWithModel(messages, temperature, providerModel)
            if (!content.isNullOrBlank()) {
                LlmChatResult(content)
            } else {
                LlmChatResult(null, LlmChatFailureType.EMPTY_RESPONSE)
            }
        } catch (_: Exception) {
            LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
        }
    }
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
        return chat(listOf(LlmChatMessage(role = "user", content = prompt)))
    }

    override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
        executeChat(messages, temperature, properties.model)

    override fun chatWithModel(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        providerModel: String
    ): String? = executeChat(messages, temperature, providerModel)

    override fun chatWithModelObserved(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        providerModel: String
    ): LlmChatResult = executeChatObserved(messages, temperature, providerModel)

    private fun executeChatObserved(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        model: String
    ): LlmChatResult {
        if (properties.apiUrl.isBlank()) {
            return LlmChatResult(null, LlmChatFailureType.CLIENT_UNAVAILABLE)
        }
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            if (properties.apiKey.isNotBlank()) {
                setBearerAuth(properties.apiKey)
            }
        }
        val body = mapOf(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to (temperature ?: properties.temperature)
        )
        val startMs = System.currentTimeMillis()
        return try {
            val response = restTemplate.postForEntity(
                properties.apiUrl,
                HttpEntity(objectMapper.writeValueAsString(body), headers),
                JsonNode::class.java
            )
            val elapsedMs = System.currentTimeMillis() - startMs
            val responseBody = response.body
            val content = responseBody?.path("choices")?.path(0)?.path("message")?.path("content")?.asText(null)
            if (content.isNullOrBlank()) {
                log.warn("LLM chat returned empty content model={} messageCount={} elapsedMs={}",
                    model, messages.size, elapsedMs)
                LlmChatResult(null, LlmChatFailureType.EMPTY_RESPONSE)
            } else {
                log.info("LLM chat success model={} messageCount={} contentChars={} elapsedMs={}",
                    model, messages.size, content.length, elapsedMs)
                LlmChatResult(content)
            }
        } catch (ex: ResourceAccessException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            val cause = ex.cause
            val isTimeout = cause is java.net.SocketTimeoutException
            val failureType = if (isTimeout) LlmChatFailureType.TIMEOUT else LlmChatFailureType.NETWORK_ERROR
            log.warn("LLM chat failed model={} messageCount={} failureType={} elapsedMs={}",
                model, messages.size, failureType, elapsedMs)
            LlmChatResult(null, failureType)
        } catch (ex: HttpClientErrorException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            val failureType = if (ex.statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                LlmChatFailureType.RATE_LIMITED
            } else {
                LlmChatFailureType.PROVIDER_ERROR
            }
            log.warn("LLM chat failed model={} messageCount={} failureType={} elapsedMs={}",
                model, messages.size, failureType, elapsedMs)
            LlmChatResult(null, failureType)
        } catch (ex: HttpServerErrorException) {
            val elapsedMs = System.currentTimeMillis() - startMs
            log.warn("LLM chat failed model={} messageCount={} failureType=PROVIDER_ERROR elapsedMs={}",
                model, messages.size, elapsedMs)
            LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
        } catch (ex: Exception) {
            val elapsedMs = System.currentTimeMillis() - startMs
            log.warn("LLM chat failed model={} messageCount={} failureType=NETWORK_ERROR elapsedMs={}",
                model, messages.size, elapsedMs)
            LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
        }
    }

    private fun executeChat(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        model: String
    ): String? = executeChatObserved(messages, temperature, model).content
}
