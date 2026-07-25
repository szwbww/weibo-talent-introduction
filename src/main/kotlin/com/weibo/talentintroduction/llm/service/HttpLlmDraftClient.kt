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
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import org.springframework.beans.factory.annotation.Qualifier
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    CLIENT_UNAVAILABLE,
    CANCELLED,
    TOTAL_TIMEOUT
}

data class LlmChatResult(
    val content: String?,
    val failureType: LlmChatFailureType = LlmChatFailureType.SUCCESS
)

enum class LlmStreamActivity { WAITING, REASONING, WRITING }

fun interface LlmStreamProgressSink {
    fun onActivity(activity: LlmStreamActivity, eventCount: Int, contentChars: Int)

    companion object {
        val NOOP = LlmStreamProgressSink { _, _, _ -> }
    }
}

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

    fun chatWithModelObservedJson(
        messages: List<LlmChatMessage>,
        temperature: Double? = null,
        providerModel: String
    ): LlmChatResult = chatWithModelObserved(messages, temperature, providerModel)

    fun chatWithModelObservedStream(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        providerModel: String,
        timeoutMillis: Long,
        jsonOutput: Boolean,
        cancellationToken: AiReplyCancellationToken,
        progressSink: LlmStreamProgressSink = LlmStreamProgressSink.NOOP
    ): LlmChatResult {
        cancellationToken.throwIfCancelled()
        val result = if (jsonOutput) {
            chatWithModelObservedJson(messages, temperature, providerModel)
        } else {
            chatWithModelObserved(messages, temperature, providerModel)
        }
        cancellationToken.throwIfCancelled()
        return result
    }
}

@Component
@ConditionalOnProperty(prefix = "talent-introduction.llm", name = ["enabled"], havingValue = "true")
class HttpLlmDraftClient(
    private val properties: LlmProperties,
    @Qualifier("llmRestTemplate") private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    @Qualifier("aiReplySseHttpClient") private val streamingHttpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build(),
    @Qualifier("aiReplyStreamScheduler") private val streamScheduler: ScheduledExecutorService =
        java.util.concurrent.Executors.newScheduledThreadPool(1)
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

    override fun chatWithModelObservedJson(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        providerModel: String
    ): LlmChatResult = executeChatObserved(messages, temperature, providerModel, jsonOutput = true)

    override fun chatWithModelObservedStream(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        providerModel: String,
        timeoutMillis: Long,
        jsonOutput: Boolean,
        cancellationToken: AiReplyCancellationToken,
        progressSink: LlmStreamProgressSink
    ): LlmChatResult {
        if (properties.apiUrl.isBlank()) {
            return LlmChatResult(null, LlmChatFailureType.CLIENT_UNAVAILABLE)
        }
        if (timeoutMillis <= 0L) {
            return LlmChatResult(null, LlmChatFailureType.TOTAL_TIMEOUT)
        }
        cancellationToken.throwIfCancelled()
        val body = linkedMapOf<String, Any>(
            "model" to providerModel,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to (temperature ?: properties.temperature),
            "stream" to true,
            "stream_options" to mapOf("include_usage" to true)
        )
        if (jsonOutput) {
            body["response_format"] = mapOf("type" to "json_object")
        }
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(properties.apiUrl))
            .timeout(Duration.ofMillis(timeoutMillis))
            .headers("Accept", "text/event-stream", "Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        if (properties.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${properties.apiKey}")
        }
        val started = System.nanoTime()
        val streamRef = AtomicReference<InputStream?>()
        val responseFuture = streamingHttpClient.sendAsync(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofInputStream()
        )
        val cancellationRegistration = cancellationToken.onCancel {
            responseFuture.cancel(true)
            streamRef.get()?.closeQuietly()
        }
        val deadlineReached = AtomicBoolean(false)
        val deadlineTask = streamScheduler.schedule({
            deadlineReached.set(true)
            responseFuture.cancel(true)
            streamRef.get()?.closeQuietly()
        }, timeoutMillis, TimeUnit.MILLISECONDS)
        var input: InputStream? = null
        return try {
            val response = try {
                responseFuture.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                responseFuture.cancel(true)
                return LlmChatResult(null, LlmChatFailureType.TIMEOUT)
            }
            if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted) {
                return LlmChatResult(null, LlmChatFailureType.CANCELLED)
            }
            val contentType = response.headers().firstValue("Content-Type").orElse("")
            if (response.statusCode() !in 200..299) {
                return LlmChatResult(null, if (response.statusCode() == 429) {
                    LlmChatFailureType.RATE_LIMITED
                } else {
                    LlmChatFailureType.PROVIDER_ERROR
                })
            }
            if (!contentType.substringBefore(';').trim().equals("text/event-stream", ignoreCase = true)) {
                return LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
            }
            input = response.body()
            streamRef.set(input)
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var eventCount = 0
            var contentChars = 0
            var activity = LlmStreamActivity.WAITING
            var sawDone = false
            var sawContent = false
            var finishReason: String? = null
            val content = StringBuilder()
            reportStreamActivity(progressSink, activity, ++eventCount, contentChars, cancellationToken)
            while (true) {
                cancellationToken.throwIfCancelled()
                if (Thread.currentThread().isInterrupted) {
                    return LlmChatResult(null, LlmChatFailureType.CANCELLED)
                }
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                if (line.startsWith(":")) {
                    eventCount = saturatingIncrement(eventCount)
                    reportStreamActivity(progressSink, activity, eventCount, contentChars, cancellationToken)
                    continue
                }
                if (!line.startsWith("data:")) {
                    return LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
                }
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") {
                    sawDone = true
                    break
                }
                val node = try {
                    objectMapper.readTree(data)
                } catch (_: Exception) {
                    return LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
                }
                eventCount = saturatingIncrement(eventCount)
                val choice = node.path("choices").firstOrNull()
                if (choice == null || choice.isMissingNode || choice.isNull) {
                    reportStreamActivity(progressSink, activity, eventCount, contentChars, cancellationToken)
                    continue
                }
                val reason = choice.path("finish_reason").asText(null)
                if (reason != null) finishReason = reason
                val delta = choice.path("delta")
                if (delta.isMissingNode || delta.isNull) {
                    reportStreamActivity(progressSink, activity, eventCount, contentChars, cancellationToken)
                    continue
                }
                if (delta.has("reasoning_content")) {
                    activity = LlmStreamActivity.REASONING
                }
                val fragment = delta.path("content").asText(null)
                if (!fragment.isNullOrEmpty()) {
                    if (contentChars > 65536 - fragment.length) {
                        return LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
                    }
                    content.append(fragment)
                    contentChars += fragment.length
                    sawContent = true
                    activity = LlmStreamActivity.WRITING
                }
                reportStreamActivity(progressSink, activity, eventCount, contentChars, cancellationToken)
            }
            if (!sawDone || !sawContent || finishReason != "stop") {
                return LlmChatResult(null, LlmChatFailureType.PROVIDER_ERROR)
            }
            val text = content.toString()
            if (text.isBlank()) return LlmChatResult(null, LlmChatFailureType.EMPTY_RESPONSE)
            log.info("LLM stream success model={} eventCount={} contentChars={} finishReason={} elapsedMs={}",
                providerModel, eventCount, contentChars, finishReason,
                (System.nanoTime() - started) / 1_000_000)
            LlmChatResult(text)
        } catch (_: AiReplyGenerationCancelledException) {
            LlmChatResult(null, LlmChatFailureType.CANCELLED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            LlmChatResult(null, LlmChatFailureType.CANCELLED)
        } catch (_: java.util.concurrent.CancellationException) {
            LlmChatResult(null, if (cancellationToken.isCancelled()) LlmChatFailureType.CANCELLED else LlmChatFailureType.TIMEOUT)
        } catch (_: Exception) {
            LlmChatResult(null, if (cancellationToken.isCancelled() || Thread.currentThread().isInterrupted) {
                LlmChatFailureType.CANCELLED
            } else if (deadlineReached.get()) {
                LlmChatFailureType.TIMEOUT
            } else {
                LlmChatFailureType.NETWORK_ERROR
            })
        } finally {
            cancellationRegistration.close()
            deadlineTask.cancel(false)
            streamRef.get()?.closeQuietly()
            input?.closeQuietly()
        }
    }

    private fun reportStreamActivity(
        sink: LlmStreamProgressSink,
        activity: LlmStreamActivity,
        eventCount: Int,
        contentChars: Int,
        token: AiReplyCancellationToken
    ) {
        try {
            sink.onActivity(activity, eventCount, contentChars)
        } catch (_: Exception) {
            log.debug("Progress callback ignored")
            if (token.isCancelled()) throw AiReplyGenerationCancelledException()
        }
    }

    private fun saturatingIncrement(value: Int): Int = if (value == Int.MAX_VALUE) value else value + 1

    private fun InputStream?.closeQuietly() {
        try {
            this?.close()
        } catch (_: Exception) {
            // Best effort close during cancellation/deadline.
        }
    }

    private fun executeChatObserved(
        messages: List<LlmChatMessage>,
        temperature: Double?,
        model: String,
        jsonOutput: Boolean = false
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
        val body = linkedMapOf<String, Any>(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "temperature" to (temperature ?: properties.temperature)
        )
        if (jsonOutput) {
            body["response_format"] = mapOf("type" to "json_object")
        }
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
