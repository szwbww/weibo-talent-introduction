package com.weibo.talentintroduction.rag.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.llm.service.LlmChatFailureType
import com.weibo.talentintroduction.llm.service.LlmChatMessage
import com.weibo.talentintroduction.llm.service.LlmChatResult
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import com.weibo.talentintroduction.llm.service.LlmTokenUsage
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import com.weibo.talentintroduction.rag.config.RagProperties
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.reply.service.ReplyFrameSelection
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 计划 03 (T5): 整封生成编排 —— 两次 LLM 调用（确定性预筛 → 检索 → 生成 →
 * 令牌逐字渲染）+ unaddressed 校验 + 回复框架拼接。行为与
 * `scripts/spike_deepseek_reply.py` 的 `main()` 逐字等价（D-2），
 * 差异全部为已登记的计划改动（I-13 ~ I-19 / D-5 / D-6）。
 *
 * 失败口径（plan T5）：不 fail-open —— 任何 LLM/解析失败都抛
 * [RagComposeException]（502 RAG_LLM_UNAVAILABLE / 422 RAG_VERBATIM_MISSING），
 * 运营必须看到失败并重试，绝不悄悄给降级稿。
 *
 * I-10: [RagComposeResult.modelCoverage] 与 unaddressed 只是输出字段 ——
 * 服务端任何判定分支（422、强制回补、令牌渲染）都不读它们；
 * `modelCoverage` 在本文件只被赋值、不被 if 读取（验收 grep 门禁）。
 */

/**
 * 业务异常：端点把它映射成 HTTP 状态 + code。本链路唯一异常通道
 * （RAG_VERBATIM_MISSING 422 / RAG_LLM_UNAVAILABLE 502 / RAG_FACT_CODE_INVALID 400）。
 */
class RagComposeException(
    val status: Int,
    val code: String,
    message: String
) : RuntimeException(message)

/** 端点响应里的一段模型正文（05 的 I-26 依赖 [renderMode] 渲染 verbatim 段）。 */
data class RagBodyParagraph(
    val text: String,
    val renderMode: String
)

/** usedFacts 条目（G-1: 业务键是 fact_code；origin ∈ MANDATORY|MODEL）。 */
data class RagUsedFact(
    val factCode: String,
    val title: String,
    val renderMode: String,
    val riskLevel: String,
    val status: String,
    val origin: String
)

/** unaddressed 条目：quote 是来信的逐字子串（I-17 服务端校验后保留）。 */
data class RagUnaddressedItem(
    val quote: String,
    val reason: String
)

/** 模型 coverage 数组条目（只透传展示，不参与任何服务端判定，I-10）。 */
data class RagCoverageItem(
    val topic: String,
    val status: String,
    val evidence: String
)

/** 端点响应（05/03b 下游契约，字段名固定）。 */
data class RagComposeResult(
    val frame: ResolvedReplyFrame,
    val bodyParagraphs: List<RagBodyParagraph>,
    val usedFacts: List<RagUsedFact>,
    val unaddressed: List<RagUnaddressedItem>,
    val modelCoverage: List<RagCoverageItem>,
    val warnings: List<String>,
    val corpusFingerprint: String,
    val retrievalUsage: LlmTokenUsage?,
    val generationUsage: LlmTokenUsage?
)

@Service
class RagLetterComposer(
    private val knowledgeBase: RagKnowledgeBase?,
    private val contextResolver: RagProcessContextResolver?,
    private val prefilterService: RagPrefilterService,
    private val mandatoryResolver: RagMandatoryResolver,
    private val promptBuilder: RagPromptBuilder,
    private val replySnippetService: ReplySnippetService,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val properties: RagProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * I-16 检索缓存：同一 (sha256(inbound), corpusFingerprint) 在本进程生命周期内
     * 只调一次检索 LLM；缓存的是模型**原始**返回的 fact_ids（未经服务端校验），
     * 每次命中后仍走完整的 I-16 校验与回补，因此上下文差异不会泄漏跨来信结果。
     */
    private val retrievalCache = ConcurrentHashMap<String, CachedRetrieval>()

    private data class CachedRetrieval(
        val modelIds: List<String>,
        val usage: LlmTokenUsage?
    )

    /** 生产入口：解析联系人流程上下文并取当前已发布语料快照。 */
    fun compose(
        contactId: Long,
        inboundText: String,
        providerModel: String,
        forcedFactCodes: List<String> = emptyList(),
        excludedFactCodes: List<String> = emptyList(),
        frameSelection: ReplyFrameSelection? = null
    ): RagComposeResult {
        val snapshot = requireNotNull(knowledgeBase) {
            "RagLetterComposer production entry requires an injected RagKnowledgeBase"
        }.snapshot()
        val context = requireNotNull(contextResolver) {
            "RagLetterComposer production entry requires an injected RagProcessContextResolver"
        }.resolve(contactId)
        return compose(
            snapshot, context, inboundText, providerModel,
            forcedFactCodes, excludedFactCodes, frameSelection
        )
    }

    /**
     * 确定性核心（测试注入语料快照；生产入口委托本方法）。
     * 流程：预筛（02，I-8）→ 请求剔除/强制合并 → 检索 LLM（I-16 服务端权威）→
     * 生成 LLM → [RagVerbatimRenderer]（I-14/I-15）→ unaddressed 过滤（I-17）→
     * 回复框架拼接与分段返回（I-18）。
     */
    fun compose(
        snapshot: RagCorpusSnapshot,
        context: RagProcessContext,
        inboundText: String,
        providerModel: String,
        forcedFactCodes: List<String>,
        excludedFactCodes: List<String>,
        frameSelection: ReplyFrameSelection?
    ): RagComposeResult {
        // ---- 候选装配：预筛（≤18，含 resolver 强制前置）→ 请求剔除 → 强制项加回。
        val enabledFacts = snapshot.facts.filter { it.enabled && it.effectiveStatus() != "DISABLED" }
        val enabledByCode = enabledFacts.associateBy { it.factCode }
        // I-9: 强制列表 = resolver（表驱动）+ 请求强制项，有序去重保留首次。
        val resolverMandatory = mandatoryResolver.resolve(snapshot, inboundText)
        val mandatoryEffective = LinkedHashSet(resolverMandatory + forcedFactCodes)
            .filter { it in enabledByCode }

        val prefiltered = prefilterService.prefilter(snapshot, inboundText, context)
        val excludedSet = excludedFactCodes.toSet()
        val candidateFacts = buildList {
            // 强制行放最前（I-16 ① 的前置合并与检索提示词都要看到它们），
            // 随后是预筛序中去掉请求剔除项与重复强制项的事实。
            mandatoryEffective.forEach { code -> enabledByCode[code]?.let { add(it) } }
            prefiltered.forEach { fact ->
                if (fact.factCode !in excludedSet && fact.factCode !in mandatoryEffective) {
                    add(fact)
                }
            }
        }
        val candidateByCode = candidateFacts.associateBy { it.factCode }

        // ---- 检索调用（T0 四参重载；temperature=retrievalTemperature=0.0）。
        val cacheKey = sha256(inboundText) + ":" + snapshot.fingerprint
        val cached = retrievalCache[cacheKey]
        val retrievalUsage: LlmTokenUsage?
        val modelIds: List<String>
        if (cached != null) {
            modelIds = cached.modelIds
            retrievalUsage = cached.usage
        } else {
            val retrievalResult = callLlm(
                stage = "retrieval",
                systemPrompt = promptBuilder.retrievalSystemPrompt(),
                userPrompt = promptBuilder.buildRetrievalPrompt(inboundText, candidateFacts, context),
                temperature = properties.retrievalTemperature,
                maxTokens = properties.retrievalMaxTokens,
                providerModel = providerModel
            )
            modelIds = parseRetrievalIds(retrievalResult.content.orEmpty())
            retrievalCache[cacheKey] = CachedRetrieval(modelIds, retrievalResult.usage)
            retrievalUsage = retrievalResult.usage
        }

        // ---- I-16: 服务端是 fact_code 的唯一权威。
        val invalidIds = modelIds.filter { it !in candidateByCode }
        invalidIds.forEach { log.warn("RAG retrieval returned invalid fact_code={}; dropped", it) }
        val selection = LinkedHashSet<String>()
        // ① 强制 fact_code 前置合并（只在候选内；候选装配已保证强制项都在候选内）。
        mandatoryEffective.filter { it in candidateByCode }.forEach(selection::add)
        // 模型选中的合法项（排除与强制重复的）。
        modelIds.filter { it in candidateByCode && it !in selection }.forEach(selection::add)
        // ② 覆盖键与 requested 相交但未被选中的候选追加到尾部。
        val requestedKeys = prefilterService.requestedCoverageKeys(snapshot, inboundText, context).toSet()
        candidateFacts.forEach { fact ->
            if (requestedKeys.intersect(fact.coverageKeys().toSet()).isNotEmpty() &&
                fact.factCode !in selection
            ) {
                selection.add(fact.factCode)
            }
        }
        val retrievedCodes: List<String>
        if (selection.isEmpty()) {
            // 模型空/全非法且无强制无覆盖命中 → 回落为候选前 12 条（I-16）。
            retrievedCodes = candidateFacts.take(12).map { it.factCode }
        } else {
            // I-16: 截断到 retrievalLimit（默认 14）。
            retrievedCodes = selection.toList().take(properties.retrievalLimit)
        }
        val retrieved = retrievedCodes.mapNotNull { candidateByCode[it] }

        // ---- 生成调用（temperature=generationTemperature=0.2）。
        val generationResult = callLlm(
            stage = "generation",
            systemPrompt = promptBuilder.generationSystemPrompt(snapshot.mandatoryRules),
            userPrompt = promptBuilder.buildGenerationPrompt(
                retrieved, inboundText, mandatoryEffective, context
            ),
            temperature = properties.generationTemperature,
            maxTokens = properties.generationMaxTokens,
            providerModel = providerModel
        )
        val generationJson = parseObjectPayload(generationResult.content.orEmpty())
            ?: throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "generation LLM response is not parseable JSON: PARSE_ERROR"
            )
        val draftNode = generationJson.path("draft")
        val draft = draftNode.takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim().orEmpty()
        if (draft.isEmpty()) {
            throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "generation JSON does not contain a non-empty string field named 'draft': PARSE_ERROR"
            )
        }

        // ---- 令牌逐字渲染（I-15）与整次失败校验（I-14）。
        // I-14 前置判定：原稿一个 VERBATIM 令牌都没有 = 模型把审定原文整体改写
        // 掉了 —— 整次失败，不降级（计划验收 verbatimMissingFailsWholeCompose；
        // I-15 的自动插入只救回「漏写个别令牌」，不救回「全改写」）。
        val verbatimFacts = retrieved.filter { it.renderMode == "VERBATIM" }
        val missingInDraft = RagVerbatimRenderer.missingTokens(draft, retrieved)
        if (verbatimFacts.isNotEmpty() && missingInDraft.size == verbatimFacts.size) {
            throw RagComposeException(
                422, "RAG_VERBATIM_MISSING",
                "model draft omits every VERBATIM render_token; answers would be rewritten: " +
                    missingInDraft.joinToString(", ")
            )
        }
        val rendered = RagVerbatimRenderer.render(draft, retrieved)
        val violations = RagVerbatimRenderer.violations(rendered, retrieved)
        if (violations.isNotEmpty()) {
            throw RagComposeException(
                422, "RAG_VERBATIM_MISSING",
                "VERBATIM answer(s) missing from the final body: ${violations.joinToString(", ")}"
            )
        }

        // ---- 输出装配：unaddressed 过滤（I-17）、warning 合并、框架与分段。
        val unaddressed = parseUnaddressed(generationJson, inboundText)
        val modelWarnings = parseWarnings(generationJson)
        val warnings = LinkedHashSet<String>()
        modelWarnings.forEach(warnings::add)
        localWarnings(rendered).forEach(warnings::add)

        val frame = if (frameSelection == null) {
            replySnippetService.resolveDefaultSelectableFrame()
        } else {
            replySnippetService.resolveSelectableFrame(frameSelection)
        }
        val usedFacts = retrieved.map { fact ->
            RagUsedFact(
                factCode = fact.factCode,
                title = fact.title,
                renderMode = fact.renderMode,
                riskLevel = fact.riskLevel,
                status = fact.status,
                origin = if (fact.factCode in mandatoryEffective) "MANDATORY" else "MODEL"
            )
        }
        return RagComposeResult(
            frame = frame,
            bodyParagraphs = toBodyParagraphs(rendered, retrieved),
            usedFacts = usedFacts,
            unaddressed = unaddressed,
            modelCoverage = parseCoverage(generationJson),
            warnings = warnings.toList(),
            corpusFingerprint = snapshot.fingerprint,
            retrievalUsage = retrievalUsage,
            generationUsage = generationResult.usage
        )
    }

    /** 两次 LLM 调用的公共通道：客户端缺席/失败/空响应一律 502，不 fail-open。 */
    private fun callLlm(
        stage: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double,
        maxTokens: Int,
        providerModel: String
    ): LlmChatResult {
        val client = llmDraftClientProvider.getIfAvailable()
            ?: throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "$stage call impossible: LlmDraftClient absent (talent-introduction.llm.enabled=false?): CLIENT_ABSENT"
            )
        val result = try {
            client.chatWithModelObservedJson(
                messages = listOf(
                    LlmChatMessage(role = "system", content = systemPrompt),
                    LlmChatMessage(role = "user", content = userPrompt)
                ),
                temperature = temperature,
                providerModel = providerModel,
                maxTokens = maxTokens
            )
        } catch (ex: Exception) {
            log.warn("RAG $stage LLM call failed: {}", ex.message)
            throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "$stage LLM call raised ${ex.javaClass.simpleName}: TRANSPORT_ERROR"
            )
        }
        if (result.failureType != LlmChatFailureType.SUCCESS) {
            log.warn("RAG $stage LLM call failed: {}", result.failureType)
            throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "$stage LLM call failed: ${result.failureType}"
            )
        }
        if (result.content.isNullOrBlank()) {
            throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "$stage LLM returned empty content: EMPTY_RESPONSE"
            )
        }
        return result
    }

    /** 解析检索 JSON 的 `fact_ids` 字符串数组；字段缺失/非数组等价模型说「没有」→ []。 */
    private fun parseRetrievalIds(raw: String): List<String> {
        val payload = parseObjectPayload(raw)
            ?: throw RagComposeException(
                502, "RAG_LLM_UNAVAILABLE",
                "retrieval LLM response is not parseable JSON: PARSE_ERROR"
            )
        val idsNode = payload.path("fact_ids")
        if (idsNode.isMissingNode || idsNode.isNull || !idsNode.isArray) {
            return emptyList()
        }
        return idsNode.mapNotNull { node -> node.takeIf { it.isTextual }?.asText() }
    }

    /** I-17: 只保留 quote 是来信逐字子串（折叠空白后）的条目；静默丢弃，不报错。 */
    private fun parseUnaddressed(generationJson: JsonNode, inboundText: String): List<RagUnaddressedItem> {
        val node = generationJson.path("unaddressed")
        if (node.isMissingNode || node.isNull || !node.isArray) {
            return emptyList()
        }
        val foldedInbound = foldWhitespace(inboundText)
        val seenQuotes = linkedSetOf<String>()
        val result = mutableListOf<RagUnaddressedItem>()
        for (entry in node) {
            if (entry == null || !entry.isObject) {
                continue
            }
            val quote = entry.path("quote").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
            if (quote.isEmpty()) {
                continue
            }
            val foldedQuote = foldWhitespace(quote)
            if (foldedQuote.length < MIN_QUOTE_LENGTH) {
                continue
            }
            if (foldedInbound.indexOf(foldedQuote) < 0) {
                continue
            }
            if (!seenQuotes.add(foldedQuote)) {
                continue
            }
            val reason = entry.path("reason").takeIf { it.isTextual }?.asText()?.trim().orEmpty()
            result += RagUnaddressedItem(quote = quote, reason = reason)
        }
        return result
    }

    private fun parseCoverage(generationJson: JsonNode): List<RagCoverageItem> {
        val node = generationJson.path("coverage")
        if (node.isMissingNode || node.isNull || !node.isArray) {
            return emptyList()
        }
        return node.mapNotNull { entry ->
            if (entry == null || !entry.isObject) {
                null
            } else {
                RagCoverageItem(
                    topic = entry.path("topic").asText(""),
                    status = entry.path("status").asText(""),
                    evidence = entry.path("evidence").asText("")
                )
            }
        }
    }

    private fun parseWarnings(generationJson: JsonNode): List<String> {
        val node = generationJson.path("warnings")
        if (node.isMissingNode || node.isNull || !node.isArray) {
            return emptyList()
        }
        return node.mapNotNull { entry ->
            entry.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    /** I-18: 把渲染后的模型正文按空行分段；含 VERBATIM answer 的段标 VERBATIM。 */
    private fun toBodyParagraphs(rendered: String, retrieved: List<RagFact>): List<RagBodyParagraph> {
        val verbatimAnswers = retrieved.filter { it.renderMode == "VERBATIM" }.map { it.answer }
        return rendered.split(Regex("\\n\\s*\\n"))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { text ->
                RagBodyParagraph(
                    text = text,
                    renderMode = if (verbatimAnswers.any { text.contains(it) }) "VERBATIM" else "COMPOSE"
                )
            }
            .toList()
    }

    /** 脚本 `local_warnings()`：模型正文的本地质量告警（与模型 warnings 合并展示）。 */
    private fun localWarnings(draft: String): List<String> {
        val checks = linkedMapOf(
            "start-up capital" to "contains unsolicited startup support",
            "startup capital" to "contains unsolicited startup support",
            "[" to "contains a possible unresolved placeholder",
            "]" to "contains a possible unresolved placeholder",
            "i understand you" to "restates or paraphrases the inbound email",
            "you mentioned" to "restates or paraphrases the inbound email",
            "particularly in relation to your research" to "restates or paraphrases the inbound email"
        )
        val lowered = draft.lowercase()
        return checks.filter { (needle, _) -> needle in lowered }.values.toSortedSet().toList()
    }

    /** 剥 markdown fence + 取首个 `{` 到末个 `}`（复用 QaFactRetriever/InboundAskEnumerator 形状）。 */
    private fun parseObjectPayload(raw: String): JsonNode? {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("```")) {
            trimmed = trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .substringBeforeLast("```")
                .trim()
            if (trimmed.isEmpty()) {
                return null
            }
        }
        val objectStart = trimmed.indexOf('{')
        val objectEnd = trimmed.lastIndexOf('}')
        val candidate = if (objectStart >= 0 && objectEnd > objectStart) {
            trimmed.substring(objectStart, objectEnd + 1)
        } else {
            trimmed
        }
        return try {
            objectMapper.readTree(candidate)
        } catch (ex: Exception) {
            log.warn("RAG LLM JSON payload unparseable: {}", ex.message)
            null
        }
    }

    /** I-17 折叠：任意空白串折叠为单个空格（不做小写化），与 InboundAskEnumerator 同款。 */
    private fun foldWhitespace(text: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch.isWhitespace()) {
                out.append(' ')
                while (index < text.length && text[index].isWhitespace()) {
                    index++
                }
            } else {
                out.append(ch)
                index++
            }
        }
        return out.toString()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** I-17: 折叠后长度小于 8 的 quote 丢弃（与 InboundAskEnumerator.MIN_QUOTE_LENGTH 同值）。 */
        const val MIN_QUOTE_LENGTH = 8
    }
}
