package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.rag.config.RagProperties
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagPrefilterExclusion
import org.springframework.stereotype.Service

/**
 * 计划 02 (T2/T4): 覆盖键请求、CV 判定、词法打分与预筛 —— 与
 * `scripts/spike_deepseek_reply.py` 的 `requested_coverage_keys()` /
 * `should_request_cv()` / `_lexical_score()` / `prefilter_facts()` 逐字等价。
 *
 * 关键不变量:
 * - I-10: requested coverage keys **只**由 `rag_intent_coverage` + 命中的短语组算出；
 *   任何模型输出的 coverage 数组都不得进入判定 —— 本文件无任何 LLM 依赖
 *   （I-10 验收 grep 门禁：`coverage\[` 在本目录无输出）。
 * - I-12: `shouldRequestCv` 四条件恒为「与」；命中时向 requested **末尾**追加
 *   `application.required_materials`。
 * - I-8: 预筛五步顺序不可交换（见 [prefilter] 的注释与平价测试的白盒用例）。
 * - I-9: 强制事实与 requested keys 的有序去重都保留首次出现。
 * - I-7: 全部打分与匹配走 [RagTextNormalizer]（ASCII 词元、首尾空格归一）。
 *
 * 数据只来自 [RagKnowledgeBase.snapshot] 的不可变快照，不查库（现状审计）。
 */

/**
 * I-12/D-7 的流程上下文：字段与脚本 `ProcessContext` 完全同构。
 * 03 的 `RagProcessContextResolver` 按 D-7 从 `expert_material_status` +
 * `mail_record` 映射本类型。
 */
data class RagProcessContext(
    val expertReplyCount: Int = 1,
    val expertTags: List<String> = emptyList(),
    val cvStatus: String = "UNKNOWN"
)

@Service
class RagPrefilterService(
    private val knowledgeBase: RagKnowledgeBase? = null,
    private val properties: RagProperties = RagProperties()
) {

    private val matcher = RagPhraseMatcher()
    private val normalizer = RagTextNormalizer()
    private val resolver = RagMandatoryResolver()

    // ------------------------------------------------------------------
    // I-12 CV 判定与 requested coverage keys
    // ------------------------------------------------------------------

    /** 生产入口（03 接线）；等价于带快照的确定性核心。 */
    fun requestedCoverageKeys(
        inbound: String,
        context: RagProcessContext? = null
    ): List<String> = requestedCoverageKeys(snapshot(), inbound, context)

    /**
     * 确定性核心：I-10 的覆盖键分母。遍历快照 `intentCoverage`（01 按
     * `group_code, sort_order` 读入），命中组的 coverage key 按序追加、去重保留
     * 首次出现（I-9）；末尾按 I-12 追加 `application.required_materials`。
     */
    fun requestedCoverageKeys(
        snapshot: RagCorpusSnapshot,
        inbound: String,
        context: RagProcessContext? = null
    ): List<String> {
        val matched = matchedGroupCodes(snapshot, inbound)
        val ordered = LinkedHashSet<String>()
        snapshot.intentCoverage.forEach { row ->
            if (row.groupCode in matched) {
                ordered.add(row.coverageKey)
            }
        }
        if (shouldRequestCv(snapshot, inbound, context)) {
            ordered.add("application.required_materials")
        }
        return ordered.toList()
    }

    /** 生产入口（03 接线）；等价于带快照的确定性核心。 */
    fun shouldRequestCv(
        inbound: String,
        context: RagProcessContext? = null
    ): Boolean = shouldRequestCv(snapshot(), inbound, context)

    /**
     * 确定性核心：I-12 四条件「与」——
     * `expertReplyCount >= 2 && cvStatus == MISSING && positiveIntent && asksNextStep`；
     * `positiveIntent` = expertTags 含 `WILLING_TO_CONTINUE` 或命中 `POSITIVE_INTENT`
     * 短语组；`asksNextStep` = 命中 `NEXT_STEP` 短语组。四条件缺一即 false。
     */
    fun shouldRequestCv(
        snapshot: RagCorpusSnapshot,
        inbound: String,
        context: RagProcessContext? = null
    ): Boolean {
        val ctx = context ?: RagProcessContext()
        val matched = matchedGroupCodes(snapshot, inbound)
        val positiveIntent =
            ctx.expertTags.any { it.trim().uppercase() == "WILLING_TO_CONTINUE" } ||
                "POSITIVE_INTENT" in matched
        val asksNextStep = "NEXT_STEP" in matched
        return ctx.expertReplyCount >= 2 &&
            ctx.cvStatus.trim().uppercase() == "MISSING" &&
            positiveIntent &&
            asksNextStep
    }

    // ------------------------------------------------------------------
    // I-7 词法打分
    // ------------------------------------------------------------------

    /**
     * `coverage 命中 * coverageWeight + 短语命中 * phraseWeight + 词重叠 * overlapWeight`，
     * 权重取自 [RagProperties]（默认 100 / 12 / 1，与脚本 `_lexical_score` 一致）。
     * `query` 即来信全文；短语命中 = `normalize(query).contains(normalize(variant))`。
     */
    fun lexicalScore(query: String, fact: RagFact, requested: Set<String>): Double {
        val queryNormalized = normalizer.normalize(query)
        val queryTokens = normalizer.tokens(query)
        val factTokens = normalizer.tokens(fact.retrievalText)
        val overlap = (queryTokens intersect factTokens).size
        val phraseHits = fact.variants().count { normalizer.normalize(it) in queryNormalized }
        val coverageHits = requested.intersect(fact.coverageKeys().toSet()).size
        return coverageHits * properties.coverageWeight +
            phraseHits * properties.phraseWeight +
            overlap * properties.overlapWeight
    }

    // ------------------------------------------------------------------
    // I-8 预筛（五步顺序不可交换）
    // ------------------------------------------------------------------

    /** 生产入口（03 接线）；等价于带快照的确定性核心。 */
    fun prefilter(
        inbound: String,
        context: RagProcessContext? = null
    ): List<RagFact> = prefilter(snapshot(), inbound, context)

    /**
     * 确定性核心：I-8 五步，顺序恒为
     *   ① 全量 enabled 事实按 `-score, fact_code` 排序
     *   ② requested 非空 → 只留覆盖键相交的事实；否则只留 `score >= minLexicalScore`
     *   ③ 应用反向剔除（`rag_prefilter_exclusion`：when_groups 全部命中且
     *      unless_groups 均未命中时，按 target_type 剔除对应 fact_code 或含该
     *      coverage_key 的事实）
     *   ④ 强制事实**前置合并**：`mandatory + selected.filter { it !in mandatoryIds }`
     *      —— 强制项取自全量 enabled 语料，**绕过第 ③ 步剔除**
     *   ⑤ 截断到 `prefilterLimit`（默认 18）条。
     *
     * 为什么 ④ 必须在 ③ 之后：KB-FUND-033 同时携带
     * `finance.government_funding` / `finance.additional_support` 覆盖键，在
     * 「只问报酬、未提 government funding」的来信上会被第 ③ 步剔出候选；它必须
     * 作为强制项（D-3 / DETAIL_INQUIRY 规则）在剔除**之后**加回。若把 ④ 挪到 ③
     * 之前，033 被剔后无人加回 —— 薪资段落消失（实测脚本靠这个顺序让 033 在该
     * 场景下仍然出现；平价测试 `prefilterOrderIsNotCommutable` 白盒钉死）。
     */
    fun prefilter(
        snapshot: RagCorpusSnapshot,
        inbound: String,
        context: RagProcessContext? = null
    ): List<RagFact> {
        val enabled = snapshot.facts.filter { it.enabled && it.effectiveStatus() != "DISABLED" }
        val requested = requestedCoverageKeys(snapshot, inbound, context).toSet()

        // ① 排序：-score 降序，score 相同按 fact_code 升序（脚本 sort key 同构）。
        val ranked = enabled
            .map { fact -> fact to lexicalScore(inbound, fact, requested) }
            .sortedWith(
                compareByDescending<Pair<RagFact, Double>> { it.second }
                    .thenBy { it.first.factCode }
            )

        // ② 覆盖过滤 / 最低词法分。
        val selected = ranked
            .filter { (fact, score) ->
                if (requested.isNotEmpty()) {
                    requested.intersect(fact.coverageKeys().toSet()).isNotEmpty()
                } else {
                    score >= properties.minLexicalScore.toDouble()
                }
            }
            .map { it.first }

        // ③ 反向剔除（数据驱动 `rag_prefilter_exclusion`）。
        val matched = matchedGroupCodes(snapshot, inbound)
        val surviving = selected.filter { fact ->
            snapshot.exclusions.none { exclusion ->
                exclusionFires(exclusion, fact, matched)
            }
        }

        // ④ 强制事实前置合并 —— 从全量 enabled 语料取，绕过 ③。
        val enabledById = enabled.associateBy { it.factCode }
        val mandatory = resolver.resolve(snapshot, inbound).mapNotNull { enabledById[it] }
        val mandatoryIds = mandatory.map { it.factCode }.toSet()
        return (mandatory + surviving.filterNot { it.factCode in mandatoryIds })
            .take(properties.prefilterLimit)
    }

    private fun matchedGroupCodes(snapshot: RagCorpusSnapshot, text: String): Set<String> =
        matcher.matchedGroups(text, snapshot.phraseGroups).toSet()

    /** I-8 第 ③ 步: when_groups 全部命中 && unless_groups 均未命中时命中目标。 */
    private fun exclusionFires(
        exclusion: RagPrefilterExclusion,
        fact: RagFact,
        matched: Set<String>
    ): Boolean {
        if (exclusion.whenGroups.isEmpty()) return false
        if (!exclusion.whenGroups.all { it in matched }) return false
        if (exclusion.unlessGroups.any { it in matched }) return false
        return when (exclusion.targetType) {
            "FACT_CODE" -> fact.factCode == exclusion.targetValue
            "COVERAGE_KEY" -> exclusion.targetValue in fact.coverageKeys()
            else -> false
        }
    }

    private fun snapshot(): RagCorpusSnapshot =
        requireNotNull(knowledgeBase) {
            "RagPrefilterService production entries require an injected RagKnowledgeBase"
        }.snapshot()
}
