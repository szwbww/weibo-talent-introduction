package com.weibo.talentintroduction.rag.service

import org.springframework.stereotype.Service

/**
 * 计划 02 (T3): 强制事实解析 —— 与脚本 `mandatory_fact_ids()` 逐字等价，另含 D-3
 * 的 `COMPENSATION` 强制行（`rag_mandatory_rule` sort_order 15，V112 种子，I-11）。
 *
 * - 按 `rag_mandatory_rule.sort_order` 升序遍历规则；规则的 `match_groups` 为 any-of
 *   （任一短语组命中即生效），命中则按序追加其 `fact_codes`。
 * - I-9: 有序去重保留首次出现（等价 Python `dict.fromkeys`）—— 033 同时被
 *   `DETAIL_INQUIRY`(10) 与 `COMPENSATION`(15) 命中时停留在 PROG-002 之后，
 *   不会挪到列表末尾改变 VERBATIM 令牌的落段顺序。
 * - I-2: 只输出全量 enabled 事实的 fact_code（永不出现停用的 KB-APP-017）；按
 *   I-8 第 ④ 步的前提，**只从全量语料取，不接触预筛候选**。
 *
 * 数据缺陷补偿（注册于 child execution report §Deviations）：V112 种子（沿自
 * plan 01 T2 与 `export_rag_kb_sql._MANDATORY_ROWS`）把规则 30/40 的 match group
 * 写作 `GOVERNMENT_ORG`，而短语组 / intent coverage 的组代码是
 * `GOVERNMENT_ORGANIZATION` —— `GOVERNMENT_ORG` 在 `rag_phrase_group` 中不存在，
 * 规则 30（`-> KB-GOV-004`）与规则 40 的 org 支路因此永远无法命中。02 实测基线
 * row 8、I-9 与 03 A-1 都要求「问政府机构」的来信强制出 KB-GOV-004，故消费侧把
 * `GOVERNMENT_ORG` 归一为 `GOVERNMENT_ORGANIZATION` 再查命中组（[normalizeGroupCode]）；
 * python 平价生成器同构镜像。该补偿使行为与脚本逐字一致（脚本无组代码概念），
 * D-3 仍是唯一刻意**行为**偏离（I-11）。
 *
 * 数据只来自 [RagKnowledgeBase.snapshot] 的不可变快照，不查库（现状审计）。
 */
@Service
class RagMandatoryResolver(
    private val knowledgeBase: RagKnowledgeBase? = null
) {

    private val matcher = RagPhraseMatcher()

    /**
     * 生产入口：对当前已发布快照解析强制事实。零生产调用方（03 接线）；
     * 等价于 `resolve(knowledgeBase.snapshot(), inbound)`。
     */
    fun resolve(inbound: String): List<String> = resolve(snapshot(), inbound)

    /**
     * 确定性核心：以显式语料快照计算（平价测试注入语料、不依赖数据库）。
     */
    fun resolve(snapshot: RagCorpusSnapshot, inbound: String): List<String> {
        val matched = matcher.matchedGroups(inbound, snapshot.phraseGroups).toSet()
        val enabledFactCodes = snapshot.facts
            .asSequence()
            .filter { it.enabled && it.effectiveStatus() != "DISABLED" }
            .map { it.factCode }
            .toSet()
        val ordered = LinkedHashSet<String>()
        snapshot.mandatoryRules
            .sortedBy { it.sortOrder }
            .forEach { rule ->
                if (rule.matchGroups.any { normalizeGroupCode(it) in matched }) {
                    rule.factCodes.forEach { code -> ordered.add(code) }
                }
            }
        return ordered.filter { it in enabledFactCodes }
    }

    /**
     * V112 种子数据缺陷补偿：`GOVERNMENT_ORG`（种子规则 30/40 的 match group）
     * 不是任何短语组代码，归一为实际短语组 `GOVERNMENT_ORGANIZATION`。
     * 除该代码外恒等；种子若在后续修复中改名，本映射自动退化为恒等。
     */
    private fun normalizeGroupCode(code: String): String =
        if (code == "GOVERNMENT_ORG") "GOVERNMENT_ORGANIZATION" else code

    private fun snapshot(): RagCorpusSnapshot =
        requireNotNull(knowledgeBase) {
            "RagMandatoryResolver.resolve(inbound) requires an injected RagKnowledgeBase"
        }.snapshot()
}
