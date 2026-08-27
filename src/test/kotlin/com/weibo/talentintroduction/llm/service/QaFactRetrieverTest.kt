package com.weibo.talentintroduction.llm.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.config.FactRetrieverProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * 计划 01 (阶段 1): QaFactRetriever 的 I-4（四项校验）/ I-7（确定性与缓存）/
 * I-8（fail-open 六条失败路径）/ I-9（每条上限与可见截断），以及定稿 system
 * prompt 逐字、prompt 规则上限与固定 [FACT_RETRIEVAL] 日志行格式。
 */
class QaFactRetrieverTest {
    private val llmProperties = LlmProperties(enabled = true)
    private val factProperties = FactRetrieverProperties(enabled = true)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private fun rule(
        id: Long,
        answerBody: String = "Fact body $id",
        replyPolicy: String = QaReplyPolicy.AUTO.name,
        enabled: Boolean = true
    ) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "keyword$id",
        replyBody = answerBody,
        answerBody = answerBody,
        replySubject = null,
        replyPolicy = replyPolicy,
        enabled = enabled
    )

    private class RecordingClient(var response: String?) : LlmDraftClient {
        var chatCount = 0
        var lastTemperature: Double? = null
        var lastMessages: List<LlmChatMessage> = emptyList()

        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
            chatCount++
            lastTemperature = temperature
            lastMessages = messages
            return response
        }
    }

    private class ThrowingClient : LlmDraftClient {
        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
            throw RuntimeException("llm down")
    }

    @Suppress("UNCHECKED_CAST")
    private fun providerWith(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun retriever(
        client: LlmDraftClient?,
        properties: FactRetrieverProperties = factProperties,
        llm: LlmProperties = llmProperties
    ): QaFactRetriever = QaFactRetriever(providerWith(client), llm, properties, objectMapper)

    /** Captures warn/info lines emitted by the QaFactRetriever logger. */
    private fun captureRetrieverLogs(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(QaFactRetriever::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.INFO
        try {
            block()
        } finally {
            logger.level = previousLevel
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.map { it.formattedMessage }
    }

    // ── 定稿 system prompt 逐字 ─────────────────────────────────────────────

    @Test
    fun `system prompt is verbatim from the plan`() {
        val expected = """
            You select which approved facts answer each numbered request from an inbound email.
            You are given a numbered list of requests and a numbered catalogue of approved facts.
            Return ONLY a JSON array. Each element must have:
            - requestIndex (integer, one of the request numbers given)
            - ruleIds (array of integers, each one of the fact ids given)
            Select a fact only when it directly answers that request. Prefer fewer, more precise facts.
            Never invent an id that is not in the catalogue. Never write prose, explanations, or answer text.
            If no fact answers a request, omit that request from the array.
            Do not include markdown fences or commentary outside the JSON array.
        """.trimIndent()
        assertEquals(expected, QaFactRetriever.FACT_RETRIEVAL_SYSTEM_PROMPT)
    }

    // ── 正常路径：多 request、模型顺序 ──────────────────────────────────────

    @Test
    fun `accepts valid ids per request preserving model order`() {
        val pool = listOf(rule(10), rule(11), rule(12))
        val client = RecordingClient(
            """[{"requestIndex": 1, "ruleIds": [10, 11]}, {"requestIndex": 2, "ruleIds": [12]}]"""
        )
        val result = retriever(client).retrieve("mail text", listOf("q1", "q2"), pool)

        assertTrue(result.available)
        assertEquals(mapOf(1 to listOf(10L, 11L), 2 to listOf(12L)), result.byRequestIndex)
        assertEquals(2, result.requested)
        assertEquals(3, result.returned)
        assertEquals(3, result.accepted)
        assertEquals(0, result.rejected)
        assertEquals(0, result.truncated)
        // 请求以 system/user 两段发出，system 用定稿 prompt，temperature 显式 0.0。
        assertEquals(2, client.lastMessages.size)
        assertEquals("system", client.lastMessages[0].role)
        assertEquals(QaFactRetriever.FACT_RETRIEVAL_SYSTEM_PROMPT, client.lastMessages[0].content)
        assertEquals("user", client.lastMessages[1].role)
        assertTrue(client.lastMessages[1].content.contains("1. q1"))
        assertTrue(client.lastMessages[1].content.contains("2. q2"))
        assertTrue(client.lastMessages[1].content.contains("10 | Rule 10 | Fact body 10"))
        assertTrue(client.lastMessages[1].content.contains("12 | Rule 12 | Fact body 12"))
    }

    // ── I-4: 四项校验，按条拒绝且计数 ──────────────────────────────────────

    @Test
    fun `rejects ids failing any of the four checks and counts them`() {
        val pool = listOf(
            rule(10),                                        // valid
            rule(11, enabled = false),                       // disabled
            rule(12, replyPolicy = QaReplyPolicy.NEVER.name), // NEVER
            rule(13, answerBody = "   ")                      // blank body
        )
        val client = RecordingClient("""[{"requestIndex": 1, "ruleIds": [999, 11, 12, 13]}]""")
        val logs = captureRetrieverLogs {
            val result = retriever(client).retrieve("mail text", listOf("q1"), pool)

            assertFalse(result.available)
            assertEquals("ALL_REJECTED", result.outcome)
            assertTrue(result.byRequestIndex.isEmpty())
            assertEquals(4, result.returned)
            assertEquals(4, result.rejected)
            assertEquals(0, result.accepted)
        }
        val warnLines = logs.filter { it.contains("rejected ruleId=") }
        assertEquals(4, warnLines.size)
        assertTrue(warnLines.any { it.contains("reason=not_in_pool") })
        assertTrue(warnLines.any { it.contains("reason=disabled") })
        assertTrue(warnLines.any { it.contains("reason=policy_never") })
        assertTrue(warnLines.any { it.contains("reason=blank_answer_body") })
    }

    @Test
    fun `drops elements whose requestIndex is out of range with an explicit warn`() {
        val pool = listOf(rule(10))
        val logs = captureRetrieverLogs {
            val result = retriever(RecordingClient("""[{"requestIndex": 5, "ruleIds": [10]}]"""))
                .retrieve("mail text", listOf("q1"), pool)

            // 越界元素整条丢弃（fail-open，不抛），不影响其余元素的可用性。
            assertTrue(result.available)
            assertTrue(result.byRequestIndex.isEmpty())
        }
        assertTrue(logs.any { it.contains("rejected element: requestIndex 5 out of range") })
    }

    @Test
    fun `deduplicates repeated ids within one request`() {
        val pool = listOf(rule(10))
        val result = retriever(RecordingClient("""[{"requestIndex": 1, "ruleIds": [10, 10]}]"""))
            .retrieve("mail text", listOf("q1"), pool)

        assertTrue(result.available)
        assertEquals(mapOf(1 to listOf(10L)), result.byRequestIndex)
        assertEquals(2, result.returned)
        assertEquals(1, result.accepted)
    }

    // ── I-9: 每条 request 上限，截断可见 ───────────────────────────────────

    @Test
    fun `truncates a request to maxFactsPerRequest with an explicit warn`() {
        val pool = (10L..14L).map { rule(it) }
        val client = RecordingClient(
            """[{"requestIndex": 1, "ruleIds": [10, 11, 12, 13, 14]}]"""
        )
        val logs = captureRetrieverLogs {
            val result = retriever(client).retrieve("mail text", listOf("q1"), pool)

            assertTrue(result.available)
            assertEquals(mapOf(1 to listOf(10L, 11L, 12L)), result.byRequestIndex)
            assertEquals(5, result.returned)
            assertEquals(3, result.accepted)
            assertEquals(2, result.truncated)
        }
        assertTrue(logs.any { it.contains("truncated requestIndex=1 count=2") && it.contains("[10, 11, 12, 13, 14]") })
    }

    // ── Repair V-1 (fix/00-execution-order R-1): prompt-pool authority ─────

    @Test
    fun `rejects an id that is absent from the truncated prompt pool`() {
        // maxRulesInPrompt = 1 → prompt 只含 rule(10)；rule(11) 在完整 pool 里但
        // 不在 prompt 里。模型返回两个 id：10 应被采纳，11 必须按 not_in_pool 拒绝。
        val pool = listOf(rule(10), rule(11))
        val client = RecordingClient("""[{"requestIndex": 1, "ruleIds": [10, 11]}]""")
        val logs = captureRetrieverLogs {
            val result = retriever(
                client,
                properties = FactRetrieverProperties(enabled = true, maxRulesInPrompt = 1)
            ).retrieve("mail text", listOf("q1"), pool)

            assertTrue(result.available)
            assertEquals(mapOf(1 to listOf(10L)), result.byRequestIndex)
            assertEquals(2, result.returned)
            assertEquals(1, result.accepted)
            assertEquals(1, result.rejected)
            assertEquals(0, result.truncated)
        }
        assertTrue(logs.any { it.contains("rejected ruleId=11 requestIndex=1 reason=not_in_pool") })
        // prompt 截断可见且模型实际只看到 rule 10。
        assertTrue(client.lastMessages[1].content.contains("10 | Rule 10 | Fact body 10"))
        assertTrue(!client.lastMessages[1].content.contains("11 | Rule 11"))
    }

    // ── I-7: 确定性 — 同输入同输出、一次 LLM、temperature 0.0 ──────────────

    @Test
    fun `same input and pool calls the llm once with temperature 0 and caches`() {
        val client = RecordingClient("""[{"requestIndex": 1, "ruleIds": [10]}]""")
        val service = retriever(client)
        val pool = listOf(rule(10))

        val first = service.retrieve("mail text", listOf("q1"), pool)
        val second = service.retrieve("mail text", listOf("q1"), pool)

        assertEquals(first, second)
        assertEquals(1, client.chatCount)
        assertEquals(0.0, client.lastTemperature)

        // 改动任一 answerBody → 规则集指纹变化 → 再次调用 LLM。
        val modifiedPool = listOf(rule(10, answerBody = "Changed body 10"))
        val third = service.retrieve("mail text", listOf("q1"), modifiedPool)
        assertTrue(third.available)
        assertEquals(2, client.chatCount)
    }

    // ── I-8: 六条失败路径 fail-open ────────────────────────────────────────

    @Test
    fun `fail open when the retriever is disabled`() {
        val result = retriever(
            RecordingClient("""[{"requestIndex": 1, "ruleIds": [10]}]"""),
            properties = FactRetrieverProperties(enabled = false)
        ).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("DISABLED", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open when the llm is disabled`() {
        val result = retriever(
            RecordingClient("""[{"requestIndex": 1, "ruleIds": [10]}]"""),
            llm = LlmProperties(enabled = false)
        ).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("DISABLED", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open when the client is absent`() {
        val result = retriever(null).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("CLIENT_ABSENT", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open when chat throws`() {
        val result = retriever(ThrowingClient()).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("TRANSPORT_ERROR", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open on an empty response`() {
        val result = retriever(RecordingClient(null)).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("EMPTY_RESPONSE", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open on a non json response`() {
        val result = retriever(RecordingClient("this is not json"))
            .retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertFalse(result.available)
        assertEquals("PARSE_ERROR", result.outcome)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    @Test
    fun `fail open when the pool or requests are empty`() {
        val emptyPool = retriever(RecordingClient("""[{"requestIndex": 1, "ruleIds": [10]}]"""))
            .retrieve("mail text", listOf("q1"), emptyList())
        assertFalse(emptyPool.available)
        assertTrue(emptyPool.byRequestIndex.isEmpty())

        val emptyRequests = retriever(RecordingClient("""[{"requestIndex": 1, "ruleIds": [10]}]"""))
            .retrieve("mail text", emptyList(), listOf(rule(10)))
        assertFalse(emptyRequests.available)
        assertTrue(emptyRequests.byRequestIndex.isEmpty())
    }

    @Test
    fun `empty json array means the model says no facts and is available`() {
        val result = retriever(RecordingClient("[]")).retrieve("mail text", listOf("q1"), listOf(rule(10)))

        assertTrue(result.available)
        assertTrue(result.byRequestIndex.isEmpty())
    }

    // ── T1.2: prompt 规则上限，截断可见 ────────────────────────────────────

    @Test
    fun `prompt caps the rule list at maxRulesInPrompt and warns`() {
        val pool = (1L..70L).map { rule(it) }
        val client = RecordingClient("[]")
        val logs = captureRetrieverLogs {
            val result = retriever(
                client,
                properties = FactRetrieverProperties(enabled = true, maxRulesInPrompt = 60)
            ).retrieve("mail text", listOf("q1"), pool)

            assertTrue(result.available)
        }
        val userContent = client.lastMessages[1].content
        assertTrue(userContent.contains("60 | Rule 60 | Fact body 60"))
        assertTrue(!userContent.contains("61 | Rule 61 | Fact body 61"))
        assertTrue(logs.any { it.contains("prompt truncated: 70 rules exceeded the limit of 60") })
    }

    // ── T1.2: 固定 [FACT_RETRIEVAL] 日志行格式 ──────────────────────────────

    @Test
    fun `fact retrieval log line has the fixed field names and order`() {
        assertEquals(
            "[FACT_RETRIEVAL] source=WORKBENCH available=false requested=2 returned=0 accepted=0 rejected=0 truncated=0 outcome=DISABLED",
            buildFactRetrievalLogLine("WORKBENCH", false, 2, 0, 0, 0, 0, "DISABLED")
        )
        assertEquals(
            "[FACT_RETRIEVAL] source=AUTO available=true requested=1 returned=5 accepted=3 rejected=0 truncated=2 outcome=OK",
            buildFactRetrievalLogLine("AUTO", true, 1, 5, 3, 0, 2, "OK")
        )
    }
}
