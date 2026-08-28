package com.weibo.talentintroduction.llm.service

import org.springframework.stereotype.Service

data class GroundedClaimPlan(
    val claimKey: String,
    val requestIndex: Int,
    val intentKey: String,
    val sourceIds: List<Long>
)

data class GroundedParagraphPlan(
    val paragraphIndex: Int,
    val claimKeys: List<String>
)

data class GroundedMissingFactPlan(
    val requestIndex: Int,
    val intentKeys: List<String>
)

data class GroundedContentPlan(
    val claims: List<GroundedClaimPlan>,
    val paragraphs: List<GroundedParagraphPlan>,
    val missingFacts: List<GroundedMissingFactPlan>,
    val allowedActions: Set<AiReplyAction>,
    val requiresReview: Boolean
)

/**
 * c5 / 15-workbench-three-step（T-5）：重排端点的编排输入——运营编辑后的
 * `paragraphPlanDraft` + `op*` 运营事实构造出的服务端规范化 facts / plan / topicOrder。
 * facts 的 id 即下游协议 id（`f<ruleId>` / `op<n>`），plan 与 topicOrder 保持草稿顺序。
 */
data class RearrangedPlanInput(
    val facts: List<PlanFact>,
    val plan: List<ParagraphPlanEntry>,
    val topicOrder: List<String>
)

@Service
class AiReplyGroundedContentPlanner {

    fun buildPlan(
        requestFacts: List<RequestFactItem>,
        allowedActions: Set<AiReplyAction>
    ): GroundedContentPlan {
        val claimKeys = linkedSetOf<String>()
        val claims = mutableListOf<GroundedClaimPlan>()
        val missingFacts = mutableListOf<GroundedMissingFactPlan>()
        val requestClaimKeys = linkedMapOf<Int, MutableList<String>>()

        for (item in requestFacts.sortedBy { it.index }) {
            val itemClaimKeys = mutableListOf<String>()
            val supportedIntents = item.intents.filter { it.status == "SUPPORTED" }

            // 计划 02 (I-5): UNSUPPORTED 且有人工事实时不进入 missing-only early
            // return——落到下方 residual 通道生成 general.answer claim；无事实才
            // 保持 missing。
            if (item.status == RequestGroundingStatus.UNSUPPORTED && item.factRuleIds.isEmpty()) {
                val missingIntentKeys = if (item.intents.isNotEmpty()) {
                    item.intents.map { it.intentKey }
                } else {
                    emptyList()
                }
                missingFacts += GroundedMissingFactPlan(
                    requestIndex = item.index,
                    intentKeys = missingIntentKeys
                )
                continue
            }

            if (supportedIntents.isNotEmpty()) {
                for (intent in supportedIntents) {
                    val claimKey = "r${item.index}:${intent.intentKey}"
                    if (claimKeys.add(claimKey)) {
                        val sourceIds = intent.evidenceRuleIds.distinct()
                        claims += GroundedClaimPlan(
                            claimKey = claimKey,
                            requestIndex = item.index,
                            intentKey = intent.intentKey,
                            sourceIds = sourceIds
                        )
                        itemClaimKeys += claimKey
                    }
                }
            }

            // 计划 02 (I-5): residual = 人工最终事实集 - 已被 supported intent claims
            // 使用的 source ids；非空时汇入同 request 的 general.answer claim——
            // 已存在（supported general.answer）则并入其 sourceIds，否则追加新
            // claim。claimKey 保持唯一。
            val usedSourceIds = supportedIntents.flatMap { it.evidenceRuleIds }.toSet()
            val residual = item.factRuleIds.filter { it !in usedSourceIds }
            if (residual.isNotEmpty()) {
                val existingGeneral = claims.indexOfFirst {
                    it.requestIndex == item.index && it.intentKey == "general.answer"
                }
                if (existingGeneral >= 0) {
                    val merged = claims[existingGeneral].copy(
                        sourceIds = (claims[existingGeneral].sourceIds + residual).distinct()
                    )
                    claims[existingGeneral] = merged
                    itemClaimKeys += merged.claimKey
                } else {
                    val claimKey = "r${item.index}:general.answer"
                    if (claimKeys.add(claimKey)) {
                        claims += GroundedClaimPlan(
                            claimKey = claimKey,
                            requestIndex = item.index,
                            intentKey = "general.answer",
                            sourceIds = residual
                        )
                        itemClaimKeys += claimKey
                    }
                }
            }

            // 计划 02 (I-5): 已由 residual general.answer claim 承载的 intent 不再
            // 记为 missing，避免提示词同时要求「生成 rX:general.answer」与「不要为
            // general.answer 生成 claim」的矛盾。
            var missingIntents = item.intents.filter { it.status != "SUPPORTED" }
            if (residual.isNotEmpty()) {
                missingIntents = missingIntents.filter { it.intentKey != "general.answer" }
            }
            if (missingIntents.isNotEmpty()) {
                missingFacts += GroundedMissingFactPlan(
                    requestIndex = item.index,
                    intentKeys = missingIntents.map { it.intentKey }
                )
            }

            if (itemClaimKeys.isNotEmpty()) {
                requestClaimKeys[item.index] = itemClaimKeys
            }
        }

        val paragraphs = buildParagraphs(requestClaimKeys)

        val requiresReview = missingFacts.isNotEmpty() ||
            requestFacts.any { it.status == RequestGroundingStatus.PARTIAL } ||
            requestFacts.any { it.status == RequestGroundingStatus.UNSUPPORTED }

        val trustGap = hasBlockingTrustGap(requestFacts)
        val effectiveActions = AiReplyActionPolicy.restrictForTrustState(allowedActions, trustGap)

        return GroundedContentPlan(
            claims = claims,
            paragraphs = paragraphs,
            missingFacts = missingFacts,
            allowedActions = effectiveActions,
            requiresReview = requiresReview
        )
    }

    fun isBlockingTrustIntent(intentKey: String): Boolean {
        return BLOCKING_TRUST_PREFIXES.any { prefix ->
            intentKey.startsWith(prefix)
        }
    }

    fun hasBlockingTrustGap(requestFacts: List<RequestFactItem>): Boolean {
        return requestFacts.any { item ->
            item.intents.any { intent ->
                intent.status != "SUPPORTED" && isBlockingTrustIntent(intent.intentKey)
            }
        }
    }

    // ── c5 / 15-workbench-three-step（T-5）：重排计划构造与六道校验再验证 ────────

    /**
     * T-5：从运营编辑后的 `paragraphPlanDraft` 与 `op*` 运营事实构造编排输入
     * （facts / plan / topicOrder）。
     * - `f<ruleId>` 事实的 body / controlled / frozen 由 [resolveQaFact] 解析（服务端
     *   权威 QA 库），id 归一为草稿中的引用形式，topic 取首次引用它的草稿条目主题。
     * - `op<n>` 事实来自 [operatorFacts]（I-1/I-2：运营逐字插槽，原样携带
     *   body / controlled / frozen / required），绝不进入任何哈希（I-1 / G-7）。
     * - 无法解析的 id（既非已知 QA 规则也非 op 事实）不出现在 facts 中，由编排器的
     *   G1 来源封闭校验拦截。
     */
    fun buildRearrangePlan(
        draft: List<ParagraphPlanEntry>,
        operatorFacts: List<PlanFact>,
        resolveQaFact: (ruleId: Long) -> PlanFact?
    ): RearrangedPlanInput {
        val factsById = linkedMapOf<String, PlanFact>()
        val operatorById = operatorFacts.associateBy { it.id }
        draft.forEach { entry ->
            entry.factIds.forEach { factId ->
                if (factsById.containsKey(factId)) return@forEach
                val resolved = when {
                    factId.startsWith("f") -> {
                        val ruleId = factId.removePrefix("f").split("+").firstOrNull()?.toLongOrNull()
                        ruleId?.let(resolveQaFact)?.copy(id = factId, topic = entry.topic)
                    }
                    else -> operatorById[factId]
                }
                if (resolved != null) {
                    factsById[factId] = resolved
                }
            }
        }
        return RearrangedPlanInput(
            facts = factsById.values.toList(),
            plan = draft,
            topicOrder = draft.map { it.topic }
        )
    }

    /**
     * T-5：对重排输出段落重跑 13 的六道校验（语义与 `AiReplyLetterOrchestrator` 的
     * G1..G6 一致；逐字期望串取自 `PlanFact.body`，不另抄任何字面量——IP-2）。
     * 返回命中的校验码（`AiReplyValidationCodes` 的 ORCH_* / ACTION_*），空列表 = 全过。
     * 供重排响应暴露「六道校验结果」，也用于 `op*` 逐字插槽的回归断言（T-6.5 / I-2）。
     */
    fun validateRearrangement(
        paragraphs: List<OrchestratedParagraph>,
        facts: List<PlanFact>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        allowedActions: Set<AiReplyAction>?
    ): List<String> {
        val issues = mutableListOf<String>()
        if (paragraphs.size != plan.size) {
            issues += AiReplyValidationCodes.ORCH_PLAN_MISMATCH
        } else {
            paragraphs.forEachIndexed { i, paragraph ->
                if (paragraph.topic != topicOrder.getOrNull(i) ||
                    paragraph.factIds.toSet() != plan[i].factIds.toSet()
                ) {
                    issues += AiReplyValidationCodes.ORCH_PLAN_MISMATCH
                }
            }
        }
        val factsById = facts.associateBy { it.id }
        val allowedIds = plan.flatMap { it.factIds }.toSet()
        val knownIds = facts.map { it.id }.toSet()
        paragraphs.forEach { paragraph ->
            paragraph.factIds.forEach { id ->
                if (id !in allowedIds || id !in knownIds) {
                    issues += AiReplyValidationCodes.ORCH_FACT_ID_UNKNOWN
                }
            }
        }
        val counts = linkedMapOf<String, Int>()
        paragraphs.forEach { paragraph ->
            paragraph.factIds.forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
        }
        facts.filter { it.required }.forEach { fact ->
            if (counts[fact.id] != 1) {
                issues += AiReplyValidationCodes.ORCH_REQUIRED_FACT_COUNT_INVALID
            }
        }
        paragraphs.forEach { paragraph ->
            val normalizedText = normalizeWhitespace(paragraph.text)
            paragraph.factIds.distinct().forEach { id ->
                val fact = factsById[id] ?: return@forEach
                if (fact.controlled != null || fact.frozen) {
                    if (!normalizedText.contains(normalizeWhitespace(fact.body))) {
                        issues += AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING
                    }
                }
            }
        }
        paragraphs.forEach { paragraph ->
            val actions = AiReplyActionPolicy.detectActions(paragraph.text)
            if (actions.isEmpty()) return@forEach
            val frozenBuiltIn = paragraph.factIds.mapNotNull { factsById[it] }
                .filter { it.frozen }
                .flatMap { AiReplyActionPolicy.detectActions(it.body) }
                .toSet()
            if ((actions - frozenBuiltIn).isNotEmpty()) {
                issues += AiReplyValidationCodes.ORCH_ACTION_IN_PARAGRAPH
            }
        }
        val exemptActions = paragraphs.flatMap { paragraph ->
            paragraph.factIds.mapNotNull { factsById[it] }
                .filter { it.frozen }
                .flatMap { AiReplyActionPolicy.detectActions(it.body) }
        }.toSet()
        val finalBody = paragraphs.joinToString("\n\n") { it.text }
        val bodyActions = if (finalBody.isBlank()) emptySet() else AiReplyActionPolicy.detectActions(finalBody)
        if (bodyActions != exemptActions) {
            issues += AiReplyValidationCodes.ACTION_BODY_MISMATCH
        }
        return issues.distinct()
    }

    private fun normalizeWhitespace(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

    private val WHITESPACE_RUN = Regex("\\s+")

    private fun buildParagraphs(
        requestClaimKeys: LinkedHashMap<Int, MutableList<String>>
    ): List<GroundedParagraphPlan> {
        val entries = requestClaimKeys.entries.toList()
        if (entries.isEmpty()) {
            return emptyList()
        }

        if (entries.size <= 4) {
            return entries.mapIndexed { idx, (_, claimKeys) ->
                GroundedParagraphPlan(
                    paragraphIndex = idx + 1,
                    claimKeys = claimKeys.toList()
                )
            }
        }

        val result = mutableListOf<GroundedParagraphPlan>()
        var paraIdx = 1
        var i = 0
        while (i < entries.size) {
            val remaining = entries.size - i
            val maxForThis = 4 - result.size

            if (remaining <= maxForThis) {
                for (j in i until entries.size) {
                    result += GroundedParagraphPlan(
                        paragraphIndex = paraIdx++,
                        claimKeys = entries[j].value.toList()
                    )
                }
                break
            }

            val mergeCount = if (remaining - maxForThis == 0) maxForThis else 2
            val mergedKeys = mutableListOf<String>()
            for (j in i until (i + mergeCount).coerceAtMost(entries.size)) {
                mergedKeys.addAll(entries[j].value)
            }
            result += GroundedParagraphPlan(
                paragraphIndex = paraIdx++,
                claimKeys = mergedKeys.toList()
            )
            i += mergeCount

            if (result.size == 4 && i < entries.size) {
                for (j in i until entries.size) {
                    result.last().let { last ->
                        val updated = GroundedParagraphPlan(
                            paragraphIndex = last.paragraphIndex,
                            claimKeys = last.claimKeys + entries[j].value
                        )
                        result[result.size - 1] = updated
                    }
                }
                break
            }
        }

        return result
    }

    companion object {
        private val TRUST_SENSITIVE_PREFIXES = setOf(
            "company.",
            "agency.",
            "finance.",
            "confidentiality.",
            "contract.",
            "ip.",
            "publication.",
            "fees."
        )

        fun isTrustSensitive(intentKey: String): Boolean {
            return TRUST_SENSITIVE_PREFIXES.any { prefix ->
                intentKey.startsWith(prefix)
            }
        }

        fun hasTrustSensitiveNoFacts(requestFacts: List<RequestFactItem>): Boolean {
            return requestFacts.any { item ->
                item.intents.any { intent ->
                    intent.status != "SUPPORTED" && isTrustSensitive(intent.intentKey)
                }
            }
        }

        private val BLOCKING_TRUST_PREFIXES = setOf(
            "company.",
            "agency.",
            "finance.",
            "fees."
        )
    }
}
