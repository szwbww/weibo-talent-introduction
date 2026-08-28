package com.weibo.talentintroduction.llm.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.service.QaCoverageKeyCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider

/**
 * 13-letter-orchestrator 单测（T-5.1 ~ T-5.3、T-5.5）。
 *
 * - 受控逐字用例的期望串一律取自 `QaCoverageKeyCatalog.controlledGroups()`（IP-2），
 *   本文件不硬编码任何 canonical 字面量（I-4：grep 断言成立）。
 * - 冻结事实 fixture 取自需求方 2026-08-28 提供的逐字基线（id 1 占位符 / id 3 `--` /
 *   id 21 en dash U+2013 + 动作句），不得自造替身。
 * - 六道校验各含失败 + 通过用例；失败经编排日志（`logValidationFailure` 的
 *   `stage/code/claimKey`）断言命中的校验码。
 */
class AiReplyLetterOrchestratorTest {

    private val controlled = QaCoverageKeyCatalog.controlledGroups()

    /** 受控组 canonical 正文（运行时取自常量表，校验侧与测试侧共用同一份）。 */
    private fun controlledBody(groupId: String): String =
        controlled.first { it.id == groupId }.canonicalAnswerBody

    /** id 21 `Meeting arrangement` 正文逐字（`15–20` 为 en dash U+2013；自带 CTA）。 */
    private val meetingArrangementBody: String =
        "We would like to arrange a brief Zoom meeting to learn more about your professional background " +
            "and research interests, and to introduce ourselves briefly.\n\n" +
            "The meeting will take approximately 15–20 minutes. Could you please let us know when you " +
            "would be available? We will arrange the meeting according to your time zone."

    /** id 1 `About the talent program` 正文要素逐字（两个 ${...} 占位符 + 末段动作句）。 */
    private val aboutTalentProgramBody: String =
        "Thank you for your interest in our talent program. " +
            "The program covers \${researchFields|your field} and values \${recentWorkTitle|your recent research}. " +
            "Would you be open to learning more about the program and the possible cooperation format?"

    /** id 3 `Application criteria` 第二段逐字（含 `--` 双连字符）。 */
    private val applicationCriteriaBody: String =
        "We can discuss fit first -- no documents needed at this stage."

    // ── 基础设施 ────────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun provider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun orchestrator(
        client: LlmDraftClient?,
        enabled: Boolean = true
    ) = AiReplyLetterOrchestrator(
        properties = LlmProperties(enabled = enabled, apiUrl = "http://llm.test", temperature = 0.3),
        llmDraftClientProvider = provider(client),
        objectMapper = ObjectMapper()
    )

    /** 每次调用返回队列中的下一条；耗尽后重复最后一条（失败用例的初始 + 修复两轮同响应）。 */
    private fun clientResponding(vararg responses: String): LlmDraftClient = object : LlmDraftClient {
        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
        private val queue = ArrayDeque(responses.toList())

        override fun chatWithModelObservedStream(
            messages: List<LlmChatMessage>,
            temperature: Double?,
            providerModel: String,
            timeoutMillis: Long,
            jsonOutput: Boolean,
            cancellationToken: AiReplyCancellationToken,
            progressSink: LlmStreamProgressSink
        ): LlmChatResult = LlmChatResult(if (queue.size > 1) queue.removeFirst() else queue.first())
    }

    private fun paragraphJson(topic: String, factIds: List<String>, text: String): Map<String, Any> =
        mapOf("topic" to topic, "factIds" to factIds, "text" to text)

    private fun responseJson(
        paragraphs: List<Map<String, Any>>,
        actionText: String?
    ): String = ObjectMapper().writeValueAsString(
        mapOf("paragraphs" to paragraphs, "actionText" to actionText)
    )

    private fun captureOrchestratorLogs(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(AiReplyLetterOrchestrator::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply {
            context = logger.loggerContext
            start()
        }
        logger.addAppender(appender)
        val previousLevel = logger.level
        logger.level = Level.WARN
        try {
            block()
        } finally {
            logger.level = previousLevel
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.map { it.formattedMessage }
    }

    // ── 公共 fixture：两条事实（G2 受控 + 普通），两主题 ─────────────────────────

    private fun baseFacts(): List<PlanFact> = listOf(
        PlanFact(id = "f7", topic = "finance", body = controlledBody("G2"), controlled = "G2", frozen = false, required = true),
        PlanFact(
            id = "f8",
            topic = "meeting",
            body = "We will arrange the meeting according to your time zone.",
            controlled = null,
            frozen = false,
            required = true
        )
    )

    private fun basePlan(): List<ParagraphPlanEntry> = listOf(
        ParagraphPlanEntry("finance", listOf("f7")),
        ParagraphPlanEntry("meeting", listOf("f8"))
    )

    private fun baseTopicOrder(): List<String> = listOf("finance", "meeting")

    private fun validResponse(): String = responseJson(
        listOf(
            paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")} There are no hidden costs."),
            paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
        ),
        null
    )

    // ── T-5.1 / T-5.5：六道校验失败 + 通过 ─────────────────────────────────────

    @Test
    fun `valid response passes all six validations`() {
        val result = orchestrator(clientResponding(validResponse()))
            .orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet())

        // I-6：paragraphs.size == paragraphPlan.size 的断言。
        assertNotNull(result)
        assertEquals(2, result!!.paragraphs.size)
        assertEquals(listOf("finance", "meeting"), result.paragraphs.map { it.topic })
        assertNull(result.actionText)
    }

    @Test
    fun `unknown fact id fails source closure`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7", "f99"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_FACT_ID_UNKNOWN) }, logs.toString())
    }

    @Test
    fun `required fact appearing zero times fails exactly-once`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", emptyList(), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_REQUIRED_FACT_COUNT_INVALID) }, logs.toString())
    }

    @Test
    fun `required fact appearing twice fails exactly-once`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7", "f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_REQUIRED_FACT_COUNT_INVALID) }, logs.toString())
    }

    @Test
    fun `rewritten controlled body fails verbatim`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "We never charge any fees."),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING) }, logs.toString())
    }

    // ── T-5.3：冻结事实逐字（id 1 占位符 / id 3 `--` / id 21 en dash）────────────

    @Test
    fun `frozen placeholder body rewritten fails verbatim`() {
        val facts = listOf(
            PlanFact("f1", "program", aboutTalentProgramBody, null, frozen = true, required = true)
        )
        val plan = listOf(ParagraphPlanEntry("program", listOf("f1")))
        val rewritten = aboutTalentProgramBody.replace("\${researchFields|your field}", "\${researchFields|your area}")
        val raw = responseJson(listOf(paragraphJson("program", listOf("f1"), rewritten)), null)
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(facts, plan, listOf("program"), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING) }, logs.toString())
    }

    @Test
    fun `frozen double hyphen rewritten fails verbatim`() {
        val facts = listOf(
            PlanFact("f3", "criteria", applicationCriteriaBody, null, frozen = true, required = true)
        )
        val plan = listOf(ParagraphPlanEntry("criteria", listOf("f3")))
        val rewritten = applicationCriteriaBody.replace("--", "—")
        val raw = responseJson(listOf(paragraphJson("criteria", listOf("f3"), rewritten)), null)
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(facts, plan, listOf("criteria"), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING) }, logs.toString())
    }

    @Test
    fun `frozen en dash rewritten fails verbatim`() {
        val facts = listOf(
            PlanFact("f21", "meeting", meetingArrangementBody, null, frozen = true, required = true)
        )
        val plan = listOf(ParagraphPlanEntry("meeting", listOf("f21")))
        val rewritten = meetingArrangementBody.replace("15–20", "15-20")
        val raw = responseJson(listOf(paragraphJson("meeting", listOf("f21"), rewritten)), null)
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(facts, plan, listOf("meeting"), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING) }, logs.toString())
    }

    @Test
    fun `frozen meeting body verbatim passes with frozen CTA exemption`() {
        val facts = listOf(
            PlanFact("f21", "meeting", meetingArrangementBody, null, frozen = true, required = true)
        )
        val plan = listOf(ParagraphPlanEntry("meeting", listOf("f21")))
        val raw = responseJson(listOf(paragraphJson("meeting", listOf("f21"), meetingArrangementBody)), null)

        val result = orchestrator(clientResponding(raw)).orchestrate(
            facts, plan, listOf("meeting"), setOf(AiReplyAction.PROPOSE_MEETING)
        )

        // I-5：冻结事实自带 CTA 留在段落内、actionText 为 null —— 不触发
        // ORCH_ACTION_IN_PARAGRAPH（豁免）且逐字通过。
        assertNotNull(result)
        val letter = result!!
        assertNull(letter.actionText)
        assertEquals(listOf(meetingArrangementBody), letter.paragraphs.map { it.text })
    }

    // ── G4 / G5：动作 ───────────────────────────────────────────────────────────

    @Test
    fun `action sentence in paragraph fails`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "Could we schedule a call?")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_ACTION_IN_PARAGRAPH) }, logs.toString())
    }

    @Test
    fun `actionText with multiple actions fails`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            "Please send your CV. Could we schedule a call?"
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ACTION_TEXT_INVALID) }, logs.toString())
    }

    @Test
    fun `unauthorized actionText fails`() {
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            "Could we schedule a call?"
        )
        val logs = captureOrchestratorLogs {
            assertNull(
                orchestrator(clientResponding(raw)).orchestrate(
                    baseFacts(), basePlan(), baseTopicOrder(), setOf(AiReplyAction.REQUEST_MATERIALS)
                )
            )
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ACTION_NOT_ALLOWED) }, logs.toString())
    }

    @Test
    fun `body action mismatch fails reconciliation`() {
        val facts = listOf(
            PlanFact("f21", "meeting", meetingArrangementBody, null, frozen = true, required = true),
            PlanFact("f7", "finance", controlledBody("G2"), "G2", frozen = false, required = true)
        )
        val plan = listOf(
            ParagraphPlanEntry("meeting", listOf("f21")),
            ParagraphPlanEntry("finance", listOf("f7"))
        )
        val order = listOf("meeting", "finance")
        val raw = responseJson(
            listOf(
                paragraphJson("meeting", listOf("f21"), meetingArrangementBody),
                paragraphJson("finance", listOf("f7"), "Please send your CV. ${controlledBody("G2")}")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(facts, plan, order, emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ACTION_BODY_MISMATCH) }, logs.toString())
    }

    // ── T-5.5：G6 编排一致 ──────────────────────────────────────────────────────

    @Test
    fun `paragraph topics not matching topic order fails plan consistency`() {
        val raw = responseJson(
            listOf(
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone."),
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}")
            ),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_PLAN_MISMATCH) }, logs.toString())
    }

    @Test
    fun `paragraph count not matching plan fails plan consistency`() {
        val raw = responseJson(
            listOf(paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}")),
            null
        )
        val logs = captureOrchestratorLogs {
            assertNull(orchestrator(clientResponding(raw)).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet()))
        }
        assertTrue(logs.any { it.contains(AiReplyValidationCodes.ORCH_PLAN_MISMATCH) }, logs.toString())
    }

    // ── I-6：缺口挂主题、不独立成段 ─────────────────────────────────────────────

    @Test
    fun `gap condition entry is honored with matching paragraph count`() {
        val facts = listOf(
            PlanFact("f7", "finance", controlledBody("G2"), "G2", frozen = false, required = true)
        )
        val plan = listOf(
            ParagraphPlanEntry("finance", listOf("f7")),
            ParagraphPlanEntry("unanswered.request.0", emptyList(), gapCondition = "What is the salary range?")
        )
        val order = listOf("finance", "unanswered.request.0")
        val raw = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("unanswered.request.0", emptyList(), "This depends on the salary range agreed with the enterprise.")
            ),
            null
        )
        val result = orchestrator(clientResponding(raw)).orchestrate(facts, plan, order, emptySet())
        assertNotNull(result)
        assertEquals(2, result!!.paragraphs.size)
    }

    // ── 修复路径与降级 ──────────────────────────────────────────────────────────

    @Test
    fun `repair attempt after validation failure can succeed`() {
        val invalid = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8", "f8"), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val valid = responseJson(
            listOf(
                paragraphJson("finance", listOf("f7"), "Regarding fees, ${controlledBody("G2")}"),
                paragraphJson("meeting", listOf("f8"), "We will arrange the meeting according to your time zone.")
            ),
            null
        )
        val result = orchestrator(clientResponding(invalid, valid))
            .orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet())
        assertNotNull(result)
    }

    @Test
    fun `llm disabled returns null`() {
        assertNull(
            orchestrator(clientResponding(validResponse()), enabled = false)
                .orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet())
        )
    }

    @Test
    fun `client unavailable returns null`() {
        assertNull(
            orchestrator(null).orchestrate(baseFacts(), basePlan(), baseTopicOrder(), emptySet())
        )
    }
}
