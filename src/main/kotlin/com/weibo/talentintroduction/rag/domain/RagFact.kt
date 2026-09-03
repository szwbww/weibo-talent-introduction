package com.weibo.talentintroduction.rag.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * 计划 01 (V112, T3): 一条 RAG 事实 —— 对应 `rag_fact` 表的整行数据。
 *
 * 字段与 V112 的 `rag_fact` 列一一对应；列表型列在库中以分隔文本存放，
 * 解析统一走 [variants] / [keywords] / [coverageKeys] / [sourceRefs]，
 * 语义与 `scripts/spike_deepseek_reply.py` 的 `_fact()` 完全同构（I-4）：
 * `|` 分隔的是 question_variants / keywords（脚本 `variants.split("|")`），
 * `,` 分隔的是 coverage_keys / source_refs（脚本 `coverage.split(",")`）；
 * 每段 `trim()` 后丢弃空段（`if item.strip()`）。
 *
 * 不变量：
 * - I-1: `fact_code` 恒为 `KB-<area>-<seq 补零三位>`，全链路唯一业务键（G-1）；
 *   自增 `id` 绝不进入提示词/响应/审计。
 * - I-2: `enabled=false` 归一为 `DISABLED`（[effectiveStatus]），永不进候选。
 * - I-5: [retrievalText] 的拼接顺序与脚本 `RagFact.retrieval_text` 逐字一致，
 *   variants 与 keywords 同源导致短语出现两次 —— 照抄不去重。
 * - G-3: `title` 只到检索为止，绝不进对外正文；[answer] 是对外正文唯一来源。
 * - G-4: `legacy_rule_id` 只读，只用于人工对账，任何运行期判断不得读它。
 */
@Table("rag_fact")
data class RagFact(
    @Id
    val id: Long? = null,
    val factCode: String,
    val area: String,
    val seq: Int,
    val title: String,
    val category: String,
    val questionVariants: String,
    val keywords: String,
    val answer: String,
    val coverageKeys: String,
    val replyPolicy: String,
    val status: String,
    val riskLevel: String,
    val renderMode: String,
    val sourceRefs: String,
    val legacyRuleId: Long? = null,
    val enabled: Boolean,
    val sortOrder: Int,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    val updatedBy: String? = null
) {

    /** I-4: `|` 分隔 → trim → 丢弃空段。 */
    fun variants(): List<String> = splitList(questionVariants, '|')

    /** I-4: 与 [variants] 同源（脚本 `keywords = phrases`），同样 `|` 分隔。 */
    fun keywords(): List<String> = splitList(keywords, '|')

    /** I-4: `,` 分隔的覆盖键。 */
    fun coverageKeys(): List<String> = splitList(coverageKeys, ',')

    /** I-4: `,` 分隔的来源引用。 */
    fun sourceRefs(): List<String> = splitList(sourceRefs, ',')

    /** I-2: `enabled=false` 归一为 `DISABLED`，永不进入检索候选。 */
    fun effectiveStatus(): String = if (enabled) status else "DISABLED"

    /**
     * I-5: `title | question_variants… | keywords… | coverage_keys… | answer`，
     * 各段以 `" | "` 连接 —— 与脚本 `RagFact.retrieval_text` 逐字一致。
     */
    val retrievalText: String
        get() = buildList {
            add(title)
            addAll(variants())
            addAll(keywords())
            addAll(coverageKeys())
            add(answer)
        }.joinToString(" | ")

    private fun splitList(raw: String, separator: Char): List<String> =
        raw.split(separator)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
