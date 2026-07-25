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
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.springframework.web.client.RestTemplate
import java.net.ConnectException
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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

    @Test
    fun `stream seam requires DONE stop and reports activity`() {
        val body = """
            data: {"choices":[{"delta":{"content":"hello"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent().toByteArray()
        val input = body.inputStream()
        val response = Mockito.mock(HttpResponse::class.java) as HttpResponse<InputStream>
        Mockito.`when`(response.statusCode()).thenReturn(200)
        Mockito.`when`(response.headers()).thenReturn(HttpHeaders.of(mapOf("Content-Type" to listOf("text/event-stream"))) { _, _ -> true })
        Mockito.`when`(response.body()).thenReturn(input)
        val httpClient = Mockito.mock(HttpClient::class.java)
        val future = CompletableFuture.completedFuture(response)
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(future)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val activities = mutableListOf<Pair<LlmStreamActivity, Int>>()
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val result = client.chatWithModelObservedStream(
                listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 2_000,
                false, AiReplyCancellationToken(), LlmStreamProgressSink { activity, events, _ ->
                    activities += activity to events
                }
            )
            assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
            assertEquals("hello", result.content)
            assertTrue(activities.any { it.first == LlmStreamActivity.WRITING })
            assertTrue(activities.any { it.second >= 3 })
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `stream request keeps json response format and usage streaming options`() {
        val body = """
            data: {"choices":[{"delta":{"content":"ok"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent().toByteArray()
        val response = streamResponse(body)
        val httpClient = Mockito.mock(HttpClient::class.java)
        val captured = AtomicReference<HttpRequest>()
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenAnswer { invocation ->
            captured.set(invocation.getArgument(0))
            CompletableFuture.completedFuture(response)
        }
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val result = client.chatWithModelObservedStream(
                listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 2_000,
                true, AiReplyCancellationToken()
            )
            assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
            val request = objectMapper.readTree(readBody(captured.get()))
            assertTrue(request.path("stream").asBoolean())
            assertTrue(request.path("stream_options").path("include_usage").asBoolean())
            assertEquals("json_object", request.path("response_format").path("type").asText())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `stream ignores keepalive reasoning and usage-only chunks`() {
        val body = """
            : keepalive

            data: {"choices":[{"delta":{"reasoning_content":"internal"}}]}

            data: {"id":"usage-only","usage":{"prompt_tokens":2,"completion_tokens":1}}

            data: {"choices":[{"delta":{"content":"answer"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        val activities = mutableListOf<Pair<LlmStreamActivity, Int>>()
        val result = runStream(body, sink = LlmStreamProgressSink { activity, events, _ -> activities += activity to events })
        assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
        assertEquals("answer", result.content)
        assertTrue(activities.any { it.first == LlmStreamActivity.REASONING })
        assertTrue(activities.any { it.first == LlmStreamActivity.WRITING })
        assertTrue(activities.last().second >= 6)
    }

    @Test
    fun `stream rejects negative finish eof and malformed json without partial content`() {
        val cases = listOf(
            "negative finish" to """
                data: {"choices":[{"delta":{"content":"partial"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"length"}]}

                data: [DONE]
            """.trimIndent(),
            "eof without done" to """
                data: {"choices":[{"delta":{"content":"partial"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}
            """.trimIndent(),
            "malformed json" to """
                data: {"choices":[{"delta":{"content":"partial"}}]}

                data: {not-json}
            """.trimIndent()
        )
        cases.forEach { (label, body) ->
            val result = runStream(body)
            assertEquals(LlmChatFailureType.PROVIDER_ERROR, result.failureType, label)
            assertNull(result.content, label)
        }
    }

    @Test
    fun `stream rejects provider content filter and resource exhaustion finishes`() {
        listOf("content_filter", "insufficient_system_resource").forEach { finishReason ->
            val result = runStream("""
                data: {"choices":[{"delta":{"content":"partial"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"$finishReason"}]}

                data: [DONE]
            """.trimIndent())
            assertEquals(LlmChatFailureType.PROVIDER_ERROR, result.failureType, finishReason)
            assertNull(result.content, finishReason)
        }
    }

    @Test
    fun `stream enforces 64 kib content boundary`() {
        val atLimit = runStream(streamBody("x".repeat(65_536)))
        assertEquals(LlmChatFailureType.SUCCESS, atLimit.failureType)
        assertEquals(65_536, atLimit.content?.length)

        val overLimit = runStream(streamBody("x".repeat(65_537)))
        assertEquals(LlmChatFailureType.PROVIDER_ERROR, overLimit.failureType)
        assertNull(overLimit.content)
    }

    @Test
    fun `http stream keeps max content counter bounded at the accepted boundary`() {
        val callbacks = mutableListOf<Pair<Int, Int>>()
        val result = runStream(
            streamBody("x".repeat(65_536)),
            sink = LlmStreamProgressSink { _, events, chars -> callbacks += events to chars }
        )
        assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
        assertEquals(65_536, callbacks.maxOf { it.second })
        assertTrue(callbacks.zipWithNext().all { (previous, current) ->
            current.first >= previous.first && current.second >= previous.second
        })
    }

    @Test
    fun `stream deadline aborts blocked response body`() {
        val body = BlockingInputStream()
        val response = streamResponse(body)
        val httpClient = Mockito.mock(HttpClient::class.java)
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(CompletableFuture.completedFuture(response))
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val result = client.chatWithModelObservedStream(
                listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 50,
                false, AiReplyCancellationToken()
            )
            assertEquals(LlmChatFailureType.TIMEOUT, result.failureType)
            assertTrue(body.closed.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `stream deadline cancels the request future`() {
        val body = BlockingInputStream()
        val response = streamResponse(body)
        val httpClient = Mockito.mock(HttpClient::class.java)
        val responseFuture = TrackingFuture<HttpResponse<InputStream>>().apply { complete(response) }
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(responseFuture)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val result = client.chatWithModelObservedStream(
                listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 50,
                false, AiReplyCancellationToken()
            )
            assertEquals(LlmChatFailureType.TIMEOUT, result.failureType)
            assertTrue(responseFuture.cancelCalled.get())
        } finally {
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `cancellation after partial content returns cancelled without partial body`() {
        val prefix = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".toByteArray()
        val body = PartialBlockingInputStream(prefix)
        val response = streamResponse(body)
        val httpClient = Mockito.mock(HttpClient::class.java)
        val responseFuture = TrackingFuture<HttpResponse<InputStream>>().apply { complete(response) }
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(responseFuture)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val token = AiReplyCancellationToken()
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val resultFuture = worker.submit<LlmChatResult> {
                client.chatWithModelObservedStream(
                    listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 2_000,
                    false, token
                )
            }
            assertTrue(body.entered.await(1, TimeUnit.SECONDS))
            token.cancel()
            val result = resultFuture.get(2, TimeUnit.SECONDS)
            assertEquals(LlmChatFailureType.CANCELLED, result.failureType)
            assertNull(result.content)
            assertTrue(responseFuture.cancelCalled.get())
        } finally {
            worker.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun `progress sink failure does not break successful stream`() {
        val result = runStream(
            streamBody("ok"),
            sink = LlmStreamProgressSink { _, _, _ -> throw IllegalStateException("sink") }
        )
        assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
        assertEquals("ok", result.content)
    }

    @Test
    fun `stream activity callback maps waiting reasoning and writing without chunk text`() {
        val callbacks = mutableListOf<Triple<LlmStreamActivity, Int, Int>>()
        val result = runStream(
            """
                data: {"choices":[{"delta":{"reasoning_content":"hidden reasoning"}}]}

                data: {"choices":[{"delta":{"content":"visible answer"}}]}

                data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                data: [DONE]
            """.trimIndent(),
            sink = LlmStreamProgressSink { activity, events, chars -> callbacks += Triple(activity, events, chars) }
        )
        assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
        assertEquals("visible answer", result.content)
        assertEquals(LlmStreamActivity.WAITING, callbacks.first().first)
        assertTrue(callbacks.any { it.first == LlmStreamActivity.REASONING })
        assertTrue(callbacks.any { it.first == LlmStreamActivity.WRITING && it.third == 14 })
        assertTrue(callbacks.toString().contains("WRITING"))
        assertTrue(!callbacks.toString().contains("visible answer"))
        assertTrue(!callbacks.toString().contains("hidden reasoning"))
    }

    @Test
    fun `stream rejects lookalike event stream content type`() {
        val result = runStream(streamBody("ok"), contentType = "text/event-streaming")
        assertEquals(LlmChatFailureType.PROVIDER_ERROR, result.failureType)
        assertNull(result.content)
    }

    @Test
    fun `stream log contains only the approved metadata allowlist`() {
        val logger = LoggerFactory.getLogger(HttpLlmDraftClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.INFO
        try {
            val result = runStream(
                """
                    data: {"choices":[{"delta":{"reasoning_content":"secret reasoning"}}]}

                    data: {"choices":[{"delta":{"content":"secret body"}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    data: [DONE]
                """.trimIndent()
            )
            assertEquals(LlmChatFailureType.SUCCESS, result.failureType)
            val streamLogs = appender.list.map { it.formattedMessage }
                .filter { it.contains("LLM stream") }
            assertTrue(streamLogs.isNotEmpty())
            streamLogs.forEach { message ->
                assertTrue(message.contains("model="))
                assertTrue(message.contains("eventCount="))
                assertTrue(message.contains("contentChars="))
                assertTrue(message.contains("finishReason="))
                assertTrue(message.contains("elapsedMs="))
                assertTrue(!message.contains("messageCount="))
                assertTrue(!message.contains("type="))
                assertTrue(!message.contains("secret body"))
                assertTrue(!message.contains("secret reasoning"))
            }
        } finally {
            logger.level = previousLevel
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `stream cancellation closes blocked body and cancels future`() {
        val body = BlockingInputStream()
        val response = Mockito.mock(HttpResponse::class.java) as HttpResponse<InputStream>
        Mockito.`when`(response.statusCode()).thenReturn(200)
        Mockito.`when`(response.headers()).thenReturn(HttpHeaders.of(mapOf("Content-Type" to listOf("text/event-stream"))) { _, _ -> true })
        Mockito.`when`(response.body()).thenReturn(body)
        val httpClient = Mockito.mock(HttpClient::class.java)
        val responseFuture = TrackingFuture<HttpResponse<InputStream>>().apply { complete(response) }
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(responseFuture)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val token = AiReplyCancellationToken()
            val client = HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
            val resultFuture = worker.submit<LlmChatResult> {
                client.chatWithModelObservedStream(
                    listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", 2_000,
                    false, token
                )
            }
            assertTrue(body.entered.await(1, TimeUnit.SECONDS))
            token.cancel()
            val result = resultFuture.get(2, TimeUnit.SECONDS)
            assertEquals(LlmChatFailureType.CANCELLED, result.failureType)
            assertTrue(body.closed.get())
            assertTrue(responseFuture.cancelCalled.get())
        } finally {
            worker.shutdownNow()
            scheduler.shutdownNow()
        }
    }

    private class BlockingInputStream : InputStream() {
        val entered = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun read(): Int {
            entered.countDown()
            while (!closed.get()) {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("interrupted")
                }
            }
            throw IOException("closed")
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class PartialBlockingInputStream(private val prefix: ByteArray) : InputStream() {
        private var index = 0
        val entered = CountDownLatch(1)
        val closed = AtomicBoolean(false)

        override fun read(): Int {
            if (index < prefix.size) return prefix[index++].toInt()
            entered.countDown()
            while (!closed.get()) {
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("interrupted")
                }
            }
            throw IOException("closed")
        }

        override fun close() {
            closed.set(true)
        }
    }

    private class TrackingFuture<T> : CompletableFuture<T>() {
        val cancelCalled = AtomicBoolean(false)

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCalled.set(true)
            return true
        }
    }

    private fun streamBody(content: String): String = """
        data: {"choices":[{"delta":{"content":${objectMapper.writeValueAsString(content)}}}]}

        data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

        data: [DONE]
    """.trimIndent()

    private fun runStream(
        body: String,
        timeoutMillis: Long = 2_000,
        sink: LlmStreamProgressSink = LlmStreamProgressSink.NOOP,
        contentType: String = "text/event-stream"
    ): LlmChatResult {
        val response = streamResponse(body.toByteArray(), contentType)
        val httpClient = Mockito.mock(HttpClient::class.java)
        Mockito.`when`(
            httpClient.sendAsync(
                Mockito.any(HttpRequest::class.java),
                Mockito.any(HttpResponse.BodyHandler::class.java)
            )
        ).thenReturn(CompletableFuture.completedFuture(response))
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        return try {
            HttpLlmDraftClient(properties(), restTemplate, objectMapper, httpClient, scheduler)
                .chatWithModelObservedStream(
                    listOf(LlmChatMessage("user", "hello")), null, "provider-flash-id", timeoutMillis,
                    false, AiReplyCancellationToken(), sink
                )
        } finally {
            scheduler.shutdownNow()
        }
    }

    private fun streamResponse(body: ByteArray, contentType: String = "text/event-stream"): HttpResponse<InputStream> {
        val response = Mockito.mock(HttpResponse::class.java) as HttpResponse<InputStream>
        Mockito.`when`(response.statusCode()).thenReturn(200)
        Mockito.`when`(response.headers()).thenReturn(
            HttpHeaders.of(mapOf("Content-Type" to listOf(contentType))) { _, _ -> true }
        )
        Mockito.`when`(response.body()).thenReturn(body.inputStream())
        return response
    }

    private fun streamResponse(body: InputStream, contentType: String = "text/event-stream"): HttpResponse<InputStream> {
        val response = Mockito.mock(HttpResponse::class.java) as HttpResponse<InputStream>
        Mockito.`when`(response.statusCode()).thenReturn(200)
        Mockito.`when`(response.headers()).thenReturn(
            HttpHeaders.of(mapOf("Content-Type" to listOf(contentType))) { _, _ -> true }
        )
        Mockito.`when`(response.body()).thenReturn(body)
        return response
    }

    private fun readBody(request: HttpRequest): String {
        val output = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        request.bodyPublisher().orElseThrow().subscribe(object : Flow.Subscriber<ByteBuffer> {
            override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)
            override fun onNext(item: ByteBuffer) {
                while (item.hasRemaining()) output.write(item.get().toInt())
            }
            override fun onError(throwable: Throwable) = done.countDown()
            override fun onComplete() = done.countDown()
        })
        assertTrue(done.await(1, TimeUnit.SECONDS))
        return output.toString(Charsets.UTF_8)
    }
}
