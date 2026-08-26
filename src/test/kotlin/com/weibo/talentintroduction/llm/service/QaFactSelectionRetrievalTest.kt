package com.weibo.talentintroduction.llm.service

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.config.FactRetrieverProperties
import com.weibo.talentintroduction.mail.service.AutoReplyConfidenceScorer
import com.weibo.talentintroduction.mail.service.GroundedAutoReplyDecisionService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional

/**
 * 计划 01 (阶段 2/3): QaFactSelectionService 接入检索后的 I-1（intents 不变）/
 * I-2（矩阵旁路）/ I-3（并集）/ I-5（sendQaRuleIds 口径一致）/ I-6（status
 * 新口径 + 自动发面不扩大）/ I-8（fail-open 与 retriever 缺席逐字段相等），以及
 * 经 select() 实际发出的 [FACT_RETRIEVAL] 日志行的 rejected/truncated 计数与
 * A-3 的 WORKBENCH DISABLED 行形态。
 */
class QaFactSelectionRetrievalTest {
    private val repository = Mockito.mock(QaRuleRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private fun rule(
        id: Long,
        keywords: String,
        answerBody: String = "Fact body $id",
        replyPolicy: String = QaReplyPolicy.AUTO.name,
        enabled: Boolean = true
    ) = QaRule(
        id = id,
        categoryId = 1,
        keywords = keywords,
        replyBody = answerBody,
        answerBody = answerBody,
        replySubject = null,
        replyPolicy = replyPolicy,
        enabled = enabled
    )

    private fun clientReturning(response: String?): LlmDraftClient = object : LlmDraftClient {
        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = response
    }

    @Suppress("UNCHECKED_CAST")
    private fun providerWith(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    /** Real retriever wired to a stub LLM client (I-4 校验在检索器内部生效)。 */
    private fun realRetriever(response: String?): QaFactRetriever =
        QaFactRetriever(
            providerWith(clientReturning(response)),
            LlmProperties(enabled = true),
            FactRetrieverProperties(enabled = true),
            objectMapper
        )

    private fun serviceWith(
        retriever: QaFactRetriever?,
        enabledForAutoReply: Boolean = true
    ) = QaFactSelectionService(
        repository,
        qaFactRetriever = retriever,
        factRetrieverProperties = FactRetrieverProperties(enabledForAutoReply = enabledForAutoReply)
    )

    private fun stubRetriever(byRequestIndex: Map<Int, List<Long>>): QaFactRetriever {
        val retriever = Mockito.mock(QaFactRetriever::class.java)
        Mockito.`when`(
            retriever.retrieve(Mockito.anyString(), Mockito.anyList<String>(), Mockito.anyList<QaRule>())
        ).thenReturn(FactRetrieval(true, byRequestIndex))
        return retriever
    }

    private val salaryQuestion = "What salary and compensation do researchers receive?"

    private fun matchingRules(): Pair<QaRule, QaRule> {
        val salaryRule = rule(10L, keywords = "salary,compensation")
        val unrelatedRule = rule(11L, keywords = "unrelated")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(salaryRule, unrelatedRule))
        return salaryRule to unrelatedRule
    }

    /** Captures lines emitted by the QaFactSelectionService logger. */
    private fun captureServiceLogs(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(QaFactSelectionService::class.java) as Logger
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

    // ── I-1: 检索结果绝不进 intents ────────────────────────────────────────

    @Test
    fun `retrieval never changes the intents of a request`() {
        matchingRules()
        val text = salaryQuestion
        val withRetrieval = serviceWith(stubRetriever(mapOf(1 to listOf(11L)))).select(
            text, null, true
        )
        val withoutRetrieval = serviceWith(null).select(text, null, true)

        val onIntents = withRetrieval.requestFacts[0].intents.map { it.intentKey }
        val offIntents = withoutRetrieval.requestFacts[0].intents.map { it.intentKey }
        assertTrue(onIntents.isNotEmpty())
        assertEquals(offIntents, onIntents)
        // 检索补入只反映在诊断字段，不进授权/版本。
        assertEquals(listOf(11L), withRetrieval.requestFacts[0].retrievedFactRuleIds)
        assertEquals(emptyList<Long>(), withoutRetrieval.requestFacts[0].retrievedFactRuleIds)
    }

    // ── I-2: 矩阵路径必须旁路检索 ─────────────────────────────────────────

    @Test
    fun `matrix selection ignores retrieval results and never calls the retriever`() {
        val salaryRule = rule(10L, keywords = "salary")
        Mockito.`when`(repository.findById(10L)).thenReturn(Optional.of(salaryRule))
        val retriever = stubRetriever(mapOf(1 to listOf(10L)))

        val result = serviceWith(retriever).selectForWorkbench(
            inboundText = salaryQuestion,
            selectionsByRequest = listOf(listOf(10L)),
            requestedFactIds = null,
            researchProfileSufficient = true
        )

        val item = result.requestFacts.single()
        assertEquals(listOf(10L), item.factRuleIds)
        assertTrue(item.retrievedFactRuleIds.isEmpty())
        Mockito.verify(retriever, Mockito.never())
            .retrieve(Mockito.anyString(), Mockito.anyList<String>(), Mockito.anyList<QaRule>())
    }

    // ── I-3: 并集不是替代，关键词命中的必进且在前 ─────────────────────────

    @Test
    fun `keyword hits and retrieved facts both enter factRuleIds with keyword hits first`() {
        matchingRules()
        val result = serviceWith(stubRetriever(mapOf(1 to listOf(11L)))).select(
            salaryQuestion, null, true
        )

        assertEquals(listOf(10L, 11L), result.requestFacts.single().factRuleIds)
    }

    // ── I-5: 检索结果必须进入 sendQaRuleIds，两条路径口径一致 ─────────────

    @Test
    fun `select sendQaRuleIds includes retrieved facts`() {
        matchingRules()
        val result = serviceWith(stubRetriever(mapOf(1 to listOf(11L)))).select(
            salaryQuestion, null, true
        )

        assertTrue(result.sendQaRuleIds.containsAll(listOf(10L, 11L)))
        assertTrue(result.promptRuleIds.containsAll(listOf(10L, 11L)))
    }

    @Test
    fun `workbench auto path keeps sendQaRuleIds equal to the fact union`() {
        matchingRules()
        val result = serviceWith(realRetriever("""[{"requestIndex": 1, "ruleIds": [11]}]"""))
            .selectForWorkbench(
                inboundText = salaryQuestion,
                selectionsByRequest = null,
                requestedFactIds = null,
                researchProfileSufficient = true
            )

        val expected = result.requestFacts.flatMap { it.factRuleIds }.distinct()
        assertEquals(expected, result.sendQaRuleIds)
        assertTrue(result.sendQaRuleIds.contains(11L))
    }

    // ── I-6: status 新口径 + 自动发面不扩大 ────────────────────────────────

    @Test
    fun `zero facts stays unsupported`() {
        val unrelated = rule(11L, keywords = "unrelated")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(unrelated))
        val result = serviceWith(null).select(salaryQuestion, null, true)

        assertEquals(RequestGroundingStatus.UNSUPPORTED, result.requestFacts.single().status)
    }

    @Test
    fun `retrieved facts demote unsupported to partial but never to grounded`() {
        val unrelated = rule(11L, keywords = "unrelated")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(unrelated))
        val result = serviceWith(stubRetriever(mapOf(1 to listOf(11L)))).select(
            salaryQuestion, null, true
        )

        val item = result.requestFacts.single()
        assertEquals(RequestGroundingStatus.PARTIAL, item.status)
        assertEquals(listOf(11L), item.factRuleIds)
    }

    @Test
    fun `supported intents stay grounded even with retrieved facts`() {
        matchingRules()
        val result = serviceWith(stubRetriever(mapOf(1 to listOf(11L)))).select(
            salaryQuestion, null, true
        )

        assertEquals(RequestGroundingStatus.GROUNDED, result.requestFacts.single().status)
    }

    @Test
    fun `passesSendGate still rejects partial drafts`() {
        val decisionService = GroundedAutoReplyDecisionService(
            LlmProperties(enabled = true, autoReplyEnabled = true),
            Mockito.mock(AiReplyDraftService::class.java),
            repository,
            Mockito.mock(AiReplyContextService::class.java),
            Mockito.mock(AiTrainingQaService::class.java),
            Mockito.mock(MailRecordRepository::class.java),
            AutoReplyConfidenceScorer()
        )
        fun draft(status: RequestGroundingStatus) = AiReplyDraftResult(
            draftText = "Grounded reply",
            usedLlm = true,
            qaRuleIds = listOf(1L),
            mode = AiReplyMode.QA_GROUNDED,
            generationState = AiReplyGenerationState.LLM_USED,
            draftReadiness = AiReplyDraftReadiness.READY,
            requestFacts = listOf(
                RequestFactItem(
                    index = 1,
                    requestText = "Salary?",
                    factRuleIds = listOf(1L),
                    status = status
                )
            )
        )

        // PARTIAL 与 UNSUPPORTED 一样被硬性闸门拒绝——自动发面不扩大（I-6）。
        assertFalse(decisionService.passesSendGate(draft(RequestGroundingStatus.PARTIAL), listOf(1L)))
        assertFalse(decisionService.passesSendGate(draft(RequestGroundingStatus.UNSUPPORTED), listOf(1L)))
        assertTrue(decisionService.passesSendGate(draft(RequestGroundingStatus.GROUNDED), listOf(1L)))
    }

    // ── I-8: 检索不可用时 select() 与 retriever 缺席逐字段相等 ────────────

    @Test
    fun `disabled retrieval returns the exact same result as an absent retriever`() {
        val unrelated = rule(11L, keywords = "unrelated")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(unrelated))

        val disabledRetriever = QaFactRetriever(
            providerWith(clientReturning("""[{"requestIndex": 1, "ruleIds": [11]}]""")),
            LlmProperties(enabled = true),
            FactRetrieverProperties(enabled = false),
            objectMapper
        )
        val withDisabledRetriever = serviceWith(disabledRetriever).select(salaryQuestion, null, true)
        val withAbsentRetriever = serviceWith(null).select(salaryQuestion, null, true)

        assertEquals(withAbsentRetriever, withDisabledRetriever)
        assertTrue(withDisabledRetriever.requestFacts.single().retrievedFactRuleIds.isEmpty())
    }

    // ── I-4: 经 select() 发出的日志行 rejected 计数 ────────────────────────

    @Test
    fun `select logs the rejected count for ids that fail validation`() {
        val valid = rule(10L, keywords = "unrelated")
        val disabled = rule(11L, keywords = "unrelated", enabled = false)
        val neverPolicy = rule(12L, keywords = "unrelated", replyPolicy = QaReplyPolicy.NEVER.name)
        val blankBody = rule(13L, keywords = "unrelated", answerBody = "   ")
        Mockito.`when`(repository.findAllEnabledOrdered())
            .thenReturn(listOf(valid, disabled, neverPolicy, blankBody))

        val logs = captureServiceLogs {
            val result = serviceWith(realRetriever("""[{"requestIndex": 1, "ruleIds": [999, 11, 12, 13]}]"""))
                .select(salaryQuestion, null, true)

            // 全部被拒 → 逐字段与无检索一致。
            assertTrue(result.requestFacts.single().factRuleIds.isEmpty())
            assertEquals(RequestGroundingStatus.UNSUPPORTED, result.requestFacts.single().status)
        }
        val retrievalLine = logs.single { it.startsWith("[FACT_RETRIEVAL]") }
        assertTrue(retrievalLine.contains("source=AUTO"))
        assertTrue(retrievalLine.contains("available=false"))
        assertTrue(retrievalLine.contains("rejected=4"))
        assertTrue(retrievalLine.contains("outcome=ALL_REJECTED"))
    }

    // ── I-9: 经 select() 发出的日志行 truncated 计数与采纳上限 ─────────────

    @Test
    fun `select caps retrieved facts per request and logs the truncated count`() {
        val pool = (10L..14L).map { rule(it, keywords = "unrelated") }
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(pool)

        val logs = captureServiceLogs {
            val result = serviceWith(
                realRetriever("""[{"requestIndex": 1, "ruleIds": [10, 11, 12, 13, 14]}]""")
            ).select(salaryQuestion, null, true)

            assertEquals(listOf(10L, 11L, 12L), result.requestFacts.single().factRuleIds)
            assertEquals(RequestGroundingStatus.PARTIAL, result.requestFacts.single().status)
        }
        val retrievalLine = logs.single { it.startsWith("[FACT_RETRIEVAL]") }
        assertTrue(retrievalLine.contains("accepted=3"))
        assertTrue(retrievalLine.contains("truncated=2"))
        assertTrue(retrievalLine.contains("outcome=OK"))
    }

    // ── A-3: 工作台关闭检索时的 DISABLED 行形态 ───────────────────────────

    @Test
    fun `workbench logs the fixed disabled line when the retriever flag is off`() {
        val unrelated = rule(11L, keywords = "unrelated")
        Mockito.`when`(repository.findAllEnabledOrdered()).thenReturn(listOf(unrelated))
        val disabledRetriever = QaFactRetriever(
            providerWith(clientReturning("""[{"requestIndex": 1, "ruleIds": [11]}]""")),
            LlmProperties(enabled = true),
            FactRetrieverProperties(enabled = false),
            objectMapper
        )

        val logs = captureServiceLogs {
            val result = serviceWith(disabledRetriever).selectForWorkbench(
                inboundText = salaryQuestion,
                selectionsByRequest = null,
                requestedFactIds = null,
                researchProfileSufficient = true
            )
            assertTrue(result.requestFacts.single().retrievedFactRuleIds.isEmpty())
        }
        val retrievalLine = logs.single { it.startsWith("[FACT_RETRIEVAL]") }
        assertEquals(
            "[FACT_RETRIEVAL] source=WORKBENCH available=false requested=0 returned=0 accepted=0 rejected=0 truncated=0 outcome=DISABLED",
            retrievalLine
        )
    }
}
