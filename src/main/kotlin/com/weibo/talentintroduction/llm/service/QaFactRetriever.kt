package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.config.FactRetrieverProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 计划 01 (I-4/I-7/I-8/I-9): fail-open 的 LLM 全库事实检索结果。
 *
 * [available] 为 false 时 [byRequestIndex] 恒空；[outcome] 把失败类型带给调用方
 * （DISABLED / CLIENT_ABSENT / TRANSPORT_ERROR / EMPTY_RESPONSE / PARSE_ERROR /
 * ALL_REJECTED，成功为 OK），stats 字段支撑调用方写固定的 [FACT_RETRIEVAL] 日志行。
 */
data class FactRetrieval(
    val available: Boolean,
    val byRequestIndex: Map<Int, List<Long>>,
    val outcome: String = "OK",
    val requested: Int = 0,
    val returned: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val truncated: Int = 0
)

/**
 * 计划 01 (T1.2): 服务端是 ruleId 的唯一权威（I-4）——模型只提供候选，每个 id 逐项
 * 通过「在 promptPool 内 / enabled / policy != NEVER / answerBody 非空」四道校验才可
 * 采纳；结果按 `(inboundText, 规则集指纹)` 缓存（I-7），LLM 调用显式
 * temperature = 0.0，每条 request 截断到 [FactRetrieverProperties.maxFactsPerRequest]
 * （I-9）。模板与守卫同构自 [InboundAskEnumerator]。
 */
@Service
class QaFactRetriever(
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val llmProperties: LlmProperties,
    private val factRetrieverProperties: FactRetrieverProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(QaFactRetriever::class.java)
    private val cache = ConcurrentHashMap<String, FactRetrieval>()

    /**
     * I-8: 每一条失败路径都返回 `available = false` 且绝不抛进调用方；失败类型经
     * [FactRetrieval.outcome] 带给调用方记录（DISABLED / CLIENT_ABSENT /
     * TRANSPORT_ERROR / EMPTY_RESPONSE / PARSE_ERROR / ALL_REJECTED）。
     */
    fun retrieve(inboundText: String, requests: List<String>, pool: List<QaRule>): FactRetrieval {
        if (!factRetrieverProperties.enabled || !llmProperties.enabled) {
            return FactRetrieval(false, emptyMap(), outcome = "DISABLED")
        }
        val client = llmDraftClientProvider.getIfAvailable()
            ?: return FactRetrieval(false, emptyMap(), outcome = "CLIENT_ABSENT")
        if (pool.isEmpty() || requests.isEmpty()) {
            // 直接空结果（T1.2）——调用点已保证非空才调用，这里只是防御。
            return FactRetrieval(false, emptyMap())
        }

        // I-7: 同一 (inboundText, 规则集指纹) 在同一进程生命周期内只调用一次 LLM。
        val cacheKey = sha256(inboundText) + ":" + poolFingerprint(pool)
        cache[cacheKey]?.let { return it }

        val promptPool = pool.take(factRetrieverProperties.maxRulesInPrompt)
        if (promptPool.size < pool.size) {
            // T1.2: prompt 截断必须可见（与 I-9 同类要求）。
            log.warn(
                "[FACT_RETRIEVAL] prompt truncated: {} rules exceeded the limit of {}",
                pool.size,
                factRetrieverProperties.maxRulesInPrompt
            )
        }
        val userContent = buildUserContent(requests, promptPool)
        val llmResult = try {
            client.chatWithModelObservedJson(
                messages = listOf(
                    LlmChatMessage(role = "system", content = FACT_RETRIEVAL_SYSTEM_PROMPT),
                    LlmChatMessage(role = "user", content = userContent)
                ),
                // I-7: 必须显式 0.0，不得走 LlmProperties.temperature（默认 0.3）。
                temperature = 0.0,
                providerModel = llmProperties.model
            )
        } catch (ex: Exception) {
            log.warn("Fact retrieval LLM failed: {}", ex.message)
            return FactRetrieval(false, emptyMap(), outcome = "TRANSPORT_ERROR")
        }
        if (llmResult.failureType == LlmChatFailureType.EMPTY_RESPONSE) {
            return FactRetrieval(false, emptyMap(), outcome = "EMPTY_RESPONSE")
        }
        if (llmResult.failureType != LlmChatFailureType.SUCCESS) {
            log.warn("Fact retrieval LLM failed: {}", llmResult.failureType)
            return FactRetrieval(false, emptyMap(), outcome = "TRANSPORT_ERROR")
        }
        val raw = llmResult.content
        if (raw.isNullOrBlank()) {
            return FactRetrieval(false, emptyMap(), outcome = "EMPTY_RESPONSE")
        }
        val extracted = extractJsonPayload(raw)
        if (extracted == null) {
            return FactRetrieval(false, emptyMap(), outcome = "PARSE_ERROR")
        }
        val elements = parseElements(extracted)
        if (elements == null) {
            return FactRetrieval(false, emptyMap(), outcome = "PARSE_ERROR")
        }
        if (elements.isEmpty()) {
            // 模型明确说没有事实：这是「模型说没有」而非「模型没跑」（I-8）。
            return cachePut(cacheKey, FactRetrieval(true, emptyMap(), requested = requests.size))
        }

        // Repair V-1 (fix/00-execution-order R-1): 校验表必须与 prompt 实际看到的规则
        // 完全一致——prompt 用的是 pool.take(maxRulesInPrompt) 截断后的 promptPool，
        // 若用未截断的 pool 建表，截断范围外的 id 会被误判为「在池内」。
        // 模型只应选择 prompt 里给出的 id（01 I-4：在 promptPool 内）。
        val poolById = promptPool.filter { it.id != null }.associateBy { it.id!! }
        var returned = 0
        var rejected = 0
        val acceptedByRequest = linkedMapOf<Int, LinkedHashSet<Long>>()
        for (node in elements) {
            val requestIndexNode = node.get("requestIndex")
            val requestIndex = requestIndexNode?.takeIf { it.isInt }?.asInt()
            if (requestIndex == null || requestIndex !in 1..requests.size) {
                log.warn(
                    "[FACT_RETRIEVAL] rejected element: requestIndex {} out of range",
                    requestIndex
                )
                continue
            }
            val ruleIdsNode = node.get("ruleIds")
            if (ruleIdsNode == null || !ruleIdsNode.isArray) {
                log.warn(
                    "[FACT_RETRIEVAL] rejected element: missing ruleIds for requestIndex {}",
                    requestIndex
                )
                continue
            }
            for (ruleNode in ruleIdsNode) {
                if (!ruleNode.isIntegralNumber) {
                    continue
                }
                val ruleId = ruleNode.asLong()
                returned++
                val reason = rejectionReason(ruleId, poolById)
                if (reason != null) {
                    rejected++
                    // I-4: 按条记录被丢弃的 id 与原因——不得静默丢弃。
                    log.warn(
                        "[FACT_RETRIEVAL] rejected ruleId={} requestIndex={} reason={}",
                        ruleId,
                        requestIndex,
                        reason
                    )
                    continue
                }
                acceptedByRequest.getOrPut(requestIndex) { LinkedHashSet() }.add(ruleId)
            }
        }

        // I-9: 每条 request 采纳上限 maxFactsPerRequest，按模型返回顺序截断，
        // 截断必须可见（log.warn 记录被丢弃的数量与全部 id）。
        var accepted = 0
        var truncated = 0
        val byRequestIndex = linkedMapOf<Int, List<Long>>()
        for ((requestIndex, ids) in acceptedByRequest) {
            val cap = factRetrieverProperties.maxFactsPerRequest
            val kept = ids.take(cap).toList()
            accepted += kept.size
            if (ids.size > cap) {
                truncated += ids.size - cap
                log.warn(
                    "[FACT_RETRIEVAL] truncated requestIndex={} count={} ids={}",
                    requestIndex,
                    ids.size - cap,
                    ids
                )
            }
            byRequestIndex[requestIndex] = kept
        }

        if (returned > 0 && accepted == 0) {
            // I-8: 模型给了候选但全部未通过校验。
            return FactRetrieval(
                false,
                emptyMap(),
                outcome = "ALL_REJECTED",
                requested = requests.size,
                returned = returned,
                rejected = rejected,
                truncated = truncated
            )
        }
        return cachePut(
            cacheKey,
            FactRetrieval(
                available = true,
                byRequestIndex = byRequestIndex,
                requested = requests.size,
                returned = returned,
                accepted = accepted,
                rejected = rejected,
                truncated = truncated
            )
        )
    }

    /** I-4: 四项校验，返回拒绝原因；通过返回 null。 */
    private fun rejectionReason(ruleId: Long, poolById: Map<Long, QaRule>): String? {
        val rule = poolById[ruleId]
            ?: return "not_in_pool"
        if (!rule.enabled) {
            return "disabled"
        }
        if (rule.replyPolicyEnum() == QaReplyPolicy.NEVER) {
            return "policy_never"
        }
        if (rule.answerBody.trim().isBlank()) {
            return "blank_answer_body"
        }
        return null
    }

    private fun cachePut(cacheKey: String, result: FactRetrieval): FactRetrieval {
        // 容量超限整表 clear()（简单可预期，避免引入淘汰算法）。
        if (cache.size >= factRetrieverProperties.cacheEntries) {
            cache.clear()
        }
        cache[cacheKey] = result
        return result
    }

    /** T1.2: 按序号编号的诉求列表 + 规则清单（每条一行 `id | 名称 | answerBody`）。 */
    private fun buildUserContent(requests: List<String>, rules: List<QaRule>): String {
        val requestLines = requests.mapIndexed { index, text -> "${index + 1}. $text" }
        val ruleLines = rules.map { rule ->
            val name = rule.displayName?.takeIf { it.isNotBlank() }
                ?: rule.replySubject?.takeIf { it.isNotBlank() }
                ?: "Rule ${rule.id}"
            "${rule.id} | $name | ${rule.answerBody}"
        }
        return buildString {
            append("Requests:\n")
            requestLines.forEach { append(it).append('\n') }
            append("\nRules:\n")
            ruleLines.forEach { append(it).append('\n') }
        }.trimEnd()
    }

    /** Same markdown-fence/JSON-array extraction shape as [InboundAskEnumerator]. */
    private fun extractJsonPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("```")) {
            val withoutFence = trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .substringBeforeLast("```")
                .trim()
            return withoutFence.takeIf { it.isNotEmpty() }
        }
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }
        return trimmed
    }

    private fun parseElements(json: String): List<JsonNode>? = try {
        objectMapper.readValue<List<JsonNode>>(json)
    } catch (ex: Exception) {
        log.warn("Failed to parse fact retrieval JSON: {}", ex.message)
        null
    }

    /**
     * I-7: 规则集指纹 = 按 id 升序拼 `id|updatedAt|sha256(answerBody)` 后取
     * SHA-256（与 AiReplyDraftService 既有证据快照口径同源）。运营一改
     * keywords/enabled/正文即缓存失效（IP-5）。
     */
    private fun poolFingerprint(pool: List<QaRule>): String {
        val canonical = pool.filter { it.id != null }
            .sortedBy { it.id }
            .map { rule ->
                val id = rule.id!!
                "$id|${rule.updatedAt}|${sha256(rule.answerBody)}"
            }
            .joinToString("\n")
        return sha256(canonical)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        /**
         * 计划 01 阶段 1 定稿 system prompt（逐字 — never reword）。
         */
        internal val FACT_RETRIEVAL_SYSTEM_PROMPT = """
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
    }
}

/**
 * 计划 01 (T1.2): 固定的 [FACT_RETRIEVAL] 结构化日志行由这个纯函数组装，字段名与
 * 顺序可直接逐字断言。行形态为 `[FACT_RETRIEVAL] source={} available={} requested={}
 * returned={} accepted={} rejected={} truncated={} outcome={}`。
 */
internal fun buildFactRetrievalLogLine(
    source: String,
    available: Boolean,
    requested: Int,
    returned: Int,
    accepted: Int,
    rejected: Int,
    truncated: Int,
    outcome: String
): String = "[FACT_RETRIEVAL] source=$source available=$available requested=$requested " +
    "returned=$returned accepted=$accepted rejected=$rejected truncated=$truncated outcome=$outcome"
