package com.weibo.talentintroduction.rag

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.HttpLlmDraftClient
import com.weibo.talentintroduction.llm.service.LlmChatFailureType
import com.weibo.talentintroduction.llm.service.LlmChatMessage
import com.weibo.talentintroduction.llm.service.LlmChatResult
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.llm.service.LlmTokenUsage
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagIntentCoverage
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import com.weibo.talentintroduction.rag.domain.RagPhraseGroup
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import com.weibo.talentintroduction.rag.service.RagComposeException
import com.weibo.talentintroduction.rag.service.RagCorpusSnapshot
import com.weibo.talentintroduction.rag.service.RagLetterComposer
import com.weibo.talentintroduction.rag.service.RagMandatoryResolver
import com.weibo.talentintroduction.rag.service.RagPrefilterService
import com.weibo.talentintroduction.rag.service.RagProcessContext
import com.weibo.talentintroduction.rag.service.RagPromptBuilder
import com.weibo.talentintroduction.rag.config.RagProperties
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * 计划 03 (T7): 整封编排的机器验收（stub LlmDraftClient，零网络、零 docker）。
 * 语料与 c2 平价测试同源：fixtures.json 的 corpus 段在内存重建
 * [RagCorpusSnapshot]（与 V112 种子同序同义，指纹 e62421a42c432cf3）。
 *
 * 覆盖: I-13 / I-14 / I-15（编排级）/ I-16 / I-17 / I-18 / I-45 / I-46 / G-1 /
 * G-3；What must NOT change（不持久化、不接发送路径、不读 qa_rule —— 本类
 * 不触碰任何发送/持久化 API）。
 *
 * I-46 已登记偏离（只登记，不实现）：脚本 `call_deepseek_json()` 的请求体含
 * `"thinking": {"type": "disabled"}` 与 `"stream": false`；生产
 * [HttpLlmDraftClient] 恒流式（请求体 `"stream" to true`，见
 * `chatWithModelObservedStream`）且不发 `thinking` 字段，RAG 两条调用都走
 * 非流式四参重载（executeChatObserved，无 `thinking`）。两者都不改变逐字出信
 * 的正确性；如要补齐需另开计划。见 [registeredDeviationsThinkingAndStreamAreNotImplemented]。
 */
class RagLetterComposerTest {

    // ------------------------------------------------------------------
    // fixture 语料（与 RagPrefilterParityTest 同构装载）
    // ------------------------------------------------------------------

    companion object {
        private const val FIXTURE_PATH = "src/test/resources/rag-parity/fixtures.json"
        private const val EXPECTED_FINGERPRINT = "e62421a42c432cf3"
        private val CJK = Pattern.compile("[\\u4e00-\\u9fff]")

        private val document: JsonNode = ObjectMapper().readTree(
            Files.readString(Path.of(FIXTURE_PATH))
        )
        private val snapshot: RagCorpusSnapshot = parseSnapshot(document.get("corpus"))
        private val japaneseCase: JsonNode = document.path("cases").first {
            it.path("id").asText() == "real-mockup-japanese-professor-full"
        }
        private val japaneseEmail: String = japaneseCase.path("email").asText()
        private val japaneseMandatory: List<String> =
            japaneseCase.path("expected").path("mandatory").map { it.asText() }
        private val japanesePrefilter: List<String> =
            japaneseCase.path("expected").path("prefilter").map { it.asText() }

        private val objectMapper = ObjectMapper()

        /** 与语料同义的流程上下文（回信 1 封 / CV UNKNOWN，不触发 I-12）。 */
        private val context = RagProcessContext(expertReplyCount = 1, cvStatus = "UNKNOWN")

        private fun parseSnapshot(corpus: JsonNode): RagCorpusSnapshot {
            val facts = corpus.get("facts").map { fact ->
                RagFact(
                    factCode = fact.get("factCode").asText(),
                    area = fact.get("area").asText(),
                    seq = fact.get("seq").asInt(),
                    title = fact.get("title").asText(),
                    category = fact.get("category").asText(),
                    questionVariants = fact.get("questionVariants").asText(),
                    keywords = fact.get("keywords").asText(),
                    answer = fact.get("answer").asText(),
                    coverageKeys = fact.get("coverageKeys").asText(),
                    replyPolicy = fact.get("replyPolicy").asText(),
                    status = fact.get("status").asText(),
                    riskLevel = fact.get("riskLevel").asText(),
                    renderMode = fact.get("renderMode").asText(),
                    sourceRefs = fact.get("sourceRefs").asText(),
                    legacyRuleId = if (fact.get("legacyRuleId").isNull) null else fact.get("legacyRuleId").asLong(),
                    enabled = fact.get("enabled").asBoolean(),
                    sortOrder = fact.get("sortOrder").asInt()
                )
            }
            return RagCorpusSnapshot(
                facts = facts,
                phraseGroups = corpus.get("phraseGroups").map { group ->
                    RagPhraseGroup(
                        groupCode = group.get("groupCode").asText(),
                        phrase = group.get("phrase").asText(),
                        sortOrder = group.get("sortOrder").asInt()
                    )
                },
                intentCoverage = corpus.get("intentCoverage").map { row ->
                    RagIntentCoverage(
                        groupCode = row.get("groupCode").asText(),
                        coverageKey = row.get("coverageKey").asText(),
                        sortOrder = row.get("sortOrder").asInt()
                    )
                },
                mandatoryRules = corpus.get("mandatoryRules").map { rule ->
                    RagMandatoryRule(
                        ruleCode = rule.get("ruleCode").asText(),
                        matchGroups = splitCsv(rule.get("matchGroups").asText()),
                        factCodes = splitCsv(rule.get("factCodes").asText()),
                        sortOrder = rule.get("sortOrder").asInt()
                    )
                },
                exclusions = corpus.get("exclusions").map { row ->
                    RagPrefilterExclusion(
                        ruleCode = row.get("ruleCode").asText(),
                        whenGroups = splitCsv(row.get("whenGroups").asText()),
                        unlessGroups = splitCsv(row.get("unlessGroups").asText()),
                        targetType = row.get("targetType").asText(),
                        targetValue = row.get("targetValue").asText()
                    )
                },
                fingerprint = corpus.get("fingerprint").asText()
            )
        }

        private fun splitCsv(raw: String): List<String> =
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        private fun fact(code: String): RagFact = snapshot.facts.first { it.factCode == code }
    }

    // ------------------------------------------------------------------
    // 桩与装配
    // ------------------------------------------------------------------

    private val snippetService = Mockito.mock(ReplySnippetService::class.java)

    private val cannedFrame = ResolvedReplyFrame(
        selection = ReplyFrameSelection(),
        version = "canned-v1",
        salutation = "Dear Professor,",
        greeting = "Thank you for your email.",
        acknowledgement = null,
        closing = "Please let us know if you have any further questions.\n\n" +
            "Best regards,\nWu Wei, Customer Care Officer"
    )

    /** 可编程的 LlmDraftClient 桩：检索/生成各一脚本，并记录温度与 maxTokens。 */
    private open class StubLlmClient : LlmDraftClient {
        var retrievalJson: String = """{"fact_ids": []}"""
        var generationJson: String = """{"draft": "", "coverage": [], "warnings": [], "unaddressed": []}"""

        var retrievalTemperature: Double? = null
        var generationTemperature: Double? = null
        var retrievalMaxTokens: Int? = null
        var generationMaxTokens: Int? = null
        val retrievalSystemPrompts = mutableListOf<String>()
        val retrievalUserPrompts = mutableListOf<String>()
        val generationSystemPrompts = mutableListOf<String>()
        val generationUserPrompts = mutableListOf<String>()

        override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null

        override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null

        override fun chatWithModelObservedJson(
            messages: List<LlmChatMessage>,
            temperature: Double?,
            providerModel: String,
            maxTokens: Int?
        ): LlmChatResult {
            val system = messages.first { it.role == "system" }.content
            val user = messages.first { it.role == "user" }.content
            return if ("semantic retrieval stage" in system) {
                retrievalTemperature = temperature
                retrievalMaxTokens = maxTokens
                retrievalSystemPrompts += system
                retrievalUserPrompts += user
                LlmChatResult(
                    retrievalJson, LlmChatFailureType.SUCCESS,
                    LlmTokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)
                )
            } else {
                generationTemperature = temperature
                generationMaxTokens = maxTokens
                generationSystemPrompts += system
                generationUserPrompts += user
                LlmChatResult(
                    generationJson, LlmChatFailureType.SUCCESS,
                    LlmTokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
                )
            }
        }
    }

    private fun prefilterService(): RagPrefilterService =
        // minLexicalScore=0 保证「无意图命中的来信」也有候选（I-16 回落路径可测）。
        RagPrefilterService(null, RagProperties(minLexicalScore = 0))

    private fun composer(client: StubLlmClient): RagLetterComposer {
        // 全部用例都走 frameSelection=null → 只桩默认框架；resolveSelectableFrame
        // 不桩（Kotlin 的 Mockito.any() 对非空参数会触发 checkNotNull NPE）。
        Mockito.`when`(snippetService.resolveDefaultSelectableFrame()).thenReturn(cannedFrame)
        @Suppress("UNCHECKED_CAST")
        val provider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(provider.getIfAvailable()).thenReturn(client)
        return RagLetterComposer(
            knowledgeBase = null,
            contextResolver = null,
            prefilterService = prefilterService(),
            mandatoryResolver = RagMandatoryResolver(null),
            promptBuilder = RagPromptBuilder(objectMapper),
            replySnippetService = snippetService,
            llmDraftClientProvider = provider,
            properties = RagProperties(),
            objectMapper = objectMapper
        )
    }

    private fun compose(
        client: StubLlmClient,
        inbound: String = japaneseEmail,
        forced: List<String> = emptyList(),
        excluded: List<String> = emptyList()
    ): com.weibo.talentintroduction.rag.service.RagComposeResult =
        composer(client).compose(
            snapshot = snapshot,
            context = context,
            inboundText = inbound,
            providerModel = "stub-provider-model",
            forcedFactCodes = forced,
            excludedFactCodes = excluded,
            frameSelection = null
        )

    private fun retrievalJson(ids: List<String>): String {
        val root = objectMapper.createObjectNode()
        val array = root.putArray("fact_ids")
        ids.forEach(array::add)
        return objectMapper.writeValueAsString(root)
    }

    private fun generationJson(
        draft: String,
        coverage: List<Triple<String, String, String>> = emptyList(),
        warnings: List<String> = emptyList(),
        unaddressed: List<Pair<String, String>> = emptyList()
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("draft", draft)
        val coverageNode = root.putArray("coverage")
        coverage.forEach { (topic, status, evidence) ->
            coverageNode.add(
                objectMapper.createObjectNode()
                    .put("topic", topic)
                    .put("status", status)
                    .put("evidence", evidence)
            )
        }
        val warningsNode = root.putArray("warnings")
        warnings.forEach(warningsNode::add)
        val unaddressedNode = root.putArray("unaddressed")
        unaddressed.forEach { (quote, reason) ->
            unaddressedNode.add(
                objectMapper.createObjectNode()
                    .put("quote", quote)
                    .put("reason", reason)
            )
        }
        return objectMapper.writeValueAsString(root)
    }

    /** 全部 7 条 VERBATIM 令牌各自成段（japanese 来信的强制事实）。 */
    private fun draftWithAllVerbatimTokens(extraParagraphs: List<String> = emptyList()): String =
        (japaneseMandatory.map { "{{FACT:$it}}" } + extraParagraphs).joinToString("\n\n")

    private fun bodyText(result: com.weibo.talentintroduction.rag.service.RagComposeResult): String =
        result.bodyParagraphs.joinToString("\n\n") { it.text }

    // ------------------------------------------------------------------
    // I-16 服务端权威
    // ------------------------------------------------------------------

    @Test
    fun `retrieval invalid fact codes are dropped and mandatory facts stay front merged`() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(listOf("KB-NOPE-999", "KB-CONT-037"))
            generationJson = generationJson(draftWithAllVerbatimTokens())
        }
        val result = compose(client)

        // 检索 [] 无效码 + 一个合法非强制码 → 强制前置 + 覆盖回补 = 语料预筛 14 条逐字。
        assertEquals(japanesePrefilter, result.usedFacts.map { it.factCode })
        assertEquals(japaneseMandatory, result.usedFacts.take(7).map { it.factCode })
        assertTrue(result.usedFacts.take(7).all { it.origin == "MANDATORY" })
        assertTrue(result.usedFacts.drop(7).all { it.origin == "MODEL" })
        assertTrue(result.usedFacts.none { it.factCode == "KB-NOPE-999" })
        // 非法码也不得进入生成调用（服务端丢弃后不会出现在 retrieved_chunks）。
        assertFalse(client.generationUserPrompts.single().contains("KB-NOPE-999"))
        assertEquals(EXPECTED_FINGERPRINT, result.corpusFingerprint)
        // 两条调用都走 T0 四参重载：maxTokens 与温度来自 RagProperties。
        assertEquals(900, client.retrievalMaxTokens)
        assertEquals(2600, client.generationMaxTokens)
        assertEquals(0.0, client.retrievalTemperature)
        assertEquals(0.2, client.generationTemperature)
        assertEquals(15, result.retrievalUsage?.totalTokens)
        assertEquals(150, result.generationUsage?.totalTokens)
    }

    @Test
    fun `empty retrieval falls back to the first twelve candidates`() {
        // 无任何意图/强制命中的来信 → requested 为空 → 覆盖回补不触发；
        // 模型返回空数组 → 服务端回落为候选前 12 条（I-16）。
        val gibberish = "qzx qzw qzy qzq qzv qzt qzs qzr"
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson("Fine.")
        }
        val expected = prefilterService().prefilter(snapshot, gibberish, context).take(12)
        assertTrue(expected.size >= 12)

        val result = compose(client, inbound = gibberish)

        assertEquals(expected.map { it.factCode }, result.usedFacts.map { it.factCode })
        assertTrue(result.usedFacts.all { it.origin == "MODEL" })
    }

    @Test
    fun `forced and excluded fact codes shape the selection`() {
        // excluded 从候选中剔除（用覆盖键只命到它的低危事实）；forced 前置合并。
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(draftWithAllVerbatimTokens())
        }
        // 排除一个非强制候选、强制一个不在强制列表里的 enabled 事实。
        val excludedCode = japanesePrefilter.drop(7).first()
        val forcedCode = snapshot.facts.first { it.enabled && it.factCode !in japaneseMandatory && it.factCode != excludedCode }.factCode
        val result = compose(client, forced = listOf(forcedCode), excluded = listOf(excludedCode))

        val codes = result.usedFacts.map { it.factCode }
        assertFalse(codes.contains(excludedCode))
        assertTrue(codes.contains(forcedCode))
        assertEquals(japaneseMandatory, codes.take(7))
    }

    // ------------------------------------------------------------------
    // I-14 / I-15（编排级）
    // ------------------------------------------------------------------

    @Test
    fun `missing single token is inserted and the final answer appears`() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            // 只写 6/7 个令牌（漏掉 KB-COMP-007），其余为中性正文 —— I-15 插入救回。
            val present = japaneseMandatory.filter { it != "KB-COMP-007" }
            generationJson = generationJson(
                (present.map { "{{FACT:$it}}" } + listOf("Please let me know if you need anything else."))
                    .joinToString("\n\n")
            )
        }
        val result = compose(client)
        val body = bodyText(result)

        val govAnswer = fact("KB-GOV-004").answer
        val compAnswer = fact("KB-COMP-007").answer
        assertTrue(body.contains(compAnswer), "missing verbatim answer must appear after render")
        // KB-COMP-007 前面最近的令牌是 KB-GOV-004 → 插到 GOV 段之后（两侧 \n\n）。
        assertTrue(body.contains("$govAnswer\n\n$compAnswer"))
    }

    @Test
    fun verbatimMissingFailsWholeCompose() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            // 模型既没写任何令牌、又把审定原文改写成了自己的话 → 整次失败 422。
            generationJson = generationJson(
                "Applicants may receive government research funding of 3-12 million RMB " +
                    "after a successful application, with enterprises providing salary support."
            )
        }
        val ex = assertThrows(RagComposeException::class.java) { compose(client) }
        assertEquals(422, ex.status)
        assertEquals("RAG_VERBATIM_MISSING", ex.code)
        assertTrue(ex.message!!.contains("KB-PROG-002"))
        assertTrue(ex.message!!.contains("KB-FUND-033"))
    }

    // ------------------------------------------------------------------
    // I-13 / G-3 泄漏检查
    // ------------------------------------------------------------------

    @Test
    fun generationPromptHidesVerbatimAnswers() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(draftWithAllVerbatimTokens())
        }
        compose(client)

        val userPrompt = client.generationUserPrompts.single()
        val systemPrompt = client.generationSystemPrompts.single()
        val fullPrompt = systemPrompt + "\n" + userPrompt

        // I-13: KB-FUND-033 的 answer 前 30 字符不得出现；令牌必须出现。
        assertFalse(userPrompt.contains("After a successful application,"))
        assertFalse(userPrompt.contains(fact("KB-FUND-033").answer))
        assertTrue(userPrompt.contains("{{FACT:KB-FUND-033}}"))
        // 7 条 VERBATIM answer 全部不可见（只有令牌与指令）。
        japaneseMandatory.filter { fact(it).renderMode == "VERBATIM" }.forEach { code ->
            assertFalse(userPrompt.contains(fact(code).answer), "VERBATIM answer of $code leaked")
        }
        // G-3: 生成侧（system + user）不含任何中文（title 一律不出现）。
        assertFalse(CJK.matcher(fullPrompt).find(), "generation prompt must not contain Chinese titles")
        // I-18: 第 12 条已改写、无 Sign as；派生第 18 条含当前规则行的令牌。
        assertFalse(systemPrompt.contains("Sign as"))
        assertTrue(systemPrompt.contains("12. Do not write a salutation, greeting, thank-you, or signature"))
        assertTrue(systemPrompt.contains("{{FACT:KB-PROG-002}} and {{FACT:KB-FUND-033}}"))
    }

    @Test
    fun `generation rules index eleven has no Sign as`() {
        val rules = com.weibo.talentintroduction.rag.service.RagPromptConstraints.GENERATION_RULES
        assertEquals(22, rules.size)
        assertFalse(rules[11].contains("Sign as"))
        assertTrue(rules[11].contains("salutation"))
    }

    // ------------------------------------------------------------------
    // I-17 unaddressed 校验
    // ------------------------------------------------------------------

    @Test
    fun `unaddressed quotes are validated as verbatim substrings of the inbound`() {
        val realQuote = "The government organization responsible for the programme"
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(
                draft = draftWithAllVerbatimTokens(),
                unaddressed = listOf(
                    "The government organization responsible for the programme" to "real ask",
                    // 编造（不在来信里）→ 丢弃
                    "How is tax handled for overseas awardees" to "invented",
                    // 折叠后 < 8 字符 → 丢弃
                    "Taxes." to "too short",
                    // 重复 quote → 只留一条
                    realQuote to "duplicate"
                )
            )
        }
        val result = compose(client)

        assertEquals(1, result.unaddressed.size)
        assertEquals(realQuote, result.unaddressed.single().quote)
        assertEquals("real ask", result.unaddressed.single().reason)
    }

    @Test
    fun `whitespace folded quotes still validate and duplicates collapse`() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(
                draft = draftWithAllVerbatimTokens(),
                unaddressed = listOf(
                    "Typical responsibilities,\n duration, and compensation for technical\n advisors"
                        to "folded whitespace is a valid substring",
                    "Typical responsibilities, duration, and compensation for technical advisors"
                        to "same quote after folding"
                )
            )
        }
        val result = compose(client)

        assertEquals(1, result.unaddressed.size)
    }

    // ------------------------------------------------------------------
    // I-18 / I-10 / 透传字段
    // ------------------------------------------------------------------

    @Test
    fun `model body never contains Wu Wei while the frame closing does`() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(
                draftWithAllVerbatimTokens(listOf("I would be glad to discuss the next steps."))
            )
        }
        val result = compose(client)

        val body = bodyText(result)
        assertFalse(body.contains("Wu Wei"))
        assertFalse(body.contains("Dear Professor"))
        assertEquals(cannedFrame, result.frame)
        assertNotNull(result.frame.closing)
        assertTrue(result.frame.closing!!.contains("Wu Wei"))
        assertTrue(result.bodyParagraphs.any { it.renderMode == "VERBATIM" })
    }

    @Test
    fun `model coverage and warnings pass through without driving decisions`() {
        val client = StubLlmClient().apply {
            retrievalJson = retrievalJson(emptyList())
            generationJson = generationJson(
                draft = draftWithAllVerbatimTokens(),
                coverage = listOf(
                    Triple("project details", "ANSWERED", "KB-PROG-002"),
                    Triple("funding", "PENDING_CONFIRMATION", "needs enterprise matching")
                ),
                warnings = listOf("Model side note")
            )
        }
        val result = compose(client)

        assertEquals(2, result.modelCoverage.size)
        assertEquals("project details", result.modelCoverage[0].topic)
        assertEquals("PENDING_CONFIRMATION", result.modelCoverage[1].status)
        assertTrue(result.warnings.contains("Model side note"))
    }

    @Test
    fun `llm failure surfaces as 502 RAG_LLM_UNAVAILABLE instead of a degraded draft`() {
        val failing = object : StubLlmClient() {
            override fun chatWithModelObservedJson(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String,
                maxTokens: Int?
            ): LlmChatResult = LlmChatResult(null, LlmChatFailureType.NETWORK_ERROR)
        }
        val ex = assertThrows(RagComposeException::class.java) { compose(failing) }
        assertEquals(502, ex.status)
        assertEquals("RAG_LLM_UNAVAILABLE", ex.code)
    }

    // ------------------------------------------------------------------
    // I-45: max_tokens 只在四参重载下进入请求体
    // ------------------------------------------------------------------

    @Test
    fun `four arg overload appends max_tokens only when provided`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val mapper = ObjectMapper()
        val root = mapper.createObjectNode()
        val choice = root.putArray("choices").addObject()
        choice.putObject("message").put("content", "{}")
        Mockito.`when`(
            restTemplate.postForEntity(
                Mockito.anyString(),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(root))

        val client = HttpLlmDraftClient(
            LlmProperties(
                enabled = true,
                apiUrl = "http://llm.local/v1/chat/completions",
                apiKey = "secret",
                model = "gpt-legacy",
                replyFlashModel = "provider-flash-id",
                replyProModel = "provider-pro-id"
            ),
            restTemplate,
            mapper
        )
        val messages = listOf(LlmChatMessage("user", "json"))
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)

        client.chatWithModelObservedJson(messages, 0.0, "provider-flash-id", maxTokens = 900)
        client.chatWithModelObservedJson(messages, 0.0, "provider-flash-id", maxTokens = null)
        client.chatWithModelObservedJson(messages, 0.0, "provider-flash-id")

        Mockito.verify(restTemplate, Mockito.times(3)).postForEntity(
            Mockito.anyString(),
            captor.capture(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val bodies = captor.allValues.map { mapper.readTree(it.body as String) }
        assertEquals(900, bodies[0].path("max_tokens").asInt())
        assertFalse(bodies[1].has("max_tokens"), "null maxTokens must not add the key")
        assertFalse(bodies[2].has("max_tokens"), "3-arg path must stay byte-identical (no max_tokens)")
    }

    // ------------------------------------------------------------------
    // I-46: 登记（不实现）
    // ------------------------------------------------------------------

    /**
     * I-46 登记（计划 03）：脚本 `call_deepseek_json()` 请求体含
     * `"thinking": {"type": "disabled"}` 与 `"stream": false`；生产
     * [HttpLlmDraftClient] 恒流式（请求体 `"stream" to true`）且不发
     * `thinking` 字段。本轮**不补**这两项 —— 影响评估见类注释，登记在此，
     * 不作为失败项。
     */
    @Disabled(
        "I-46 已登记偏离（不实现）：thinking:{type:disabled} 与 stream:false 与脚本不一致，" +
            "本轮只登记。见类注释与上方注释。"
    )
    @Test
    fun registeredDeviationsThinkingAndStreamAreNotImplemented() {
        // 登记占位：无断言。偏离登记于类注释；若后续计划要补齐，另开计划。
    }
}
