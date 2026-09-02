package com.weibo.talentintroduction.rag.domain

/**
 * 计划 01 (V112, T2/T3): `rag_phrase_group` 之外的规则表领域模型 + 一次性读出
 * 的聚合结果。行数据由 `RagRetrievalRuleRepository` 在启动期一次读入（表小），
 * 之后全部走 `RagKnowledgeBase` 的不可变快照（I-6），无逐条查库。
 */

/**
 * `rag_phrase_group` 一行：短语组 `group_code` 下的一个短语。
 * 来源为脚本的 `_XXX_PHRASES` 常量 / `_INTENT_COVERAGE` 内联短语组 /
 * `COMPENSATION` 组；02 用它对来信做归一化子串命中（`_contains_any`）。
 */
data class RagPhraseGroup(
    val groupCode: String,
    val phrase: String,
    val sortOrder: Int
)

/**
 * `rag_intent_coverage` 一行：命中 `group_code` 短语组时追加的 `coverage_key`。
 * 行顺序 = 组内 `sort_order`（01 按 group_code、sort_order 读入快照）。
 */
data class RagIntentCoverage(
    val groupCode: String,
    val coverageKey: String,
    val sortOrder: Int
)

/**
 * `rag_mandatory_rule` 一行：硬性事实规则。`match_groups` 为 any-of（逗号分隔，
 * 任一命中即生效，02 T3 的语义），`fact_codes` 为有序追加（逗号分隔）。
 * sort_order 10/15/20/30/40/50；15 是 D-3 新增的 `COMPENSATION -> KB-FUND-033`。
 */
data class RagMandatoryRule(
    val ruleCode: String,
    val matchGroups: List<String>,
    val factCodes: List<String>,
    val sortOrder: Int
)

/**
 * `rag_prefilter_exclusion` 一行：预筛剔除规则（02 I-8 第 ③ 步）。
 * `when_groups` 全部命中且 `unless_groups` 均未命中时，按 [targetType] 剔除
 * 对应 `fact_code`（`FACT_CODE`）或含该覆盖键的事实（`COVERAGE_KEY`）。
 * 共 4 行（T2 小节标题「3 行」为笔误，A-1 与 brief 均为 4）。
 */
data class RagPrefilterExclusion(
    val ruleCode: String,
    val whenGroups: List<String>,
    val unlessGroups: List<String>,
    val targetType: String,
    val targetValue: String
)

/**
 * `rag_kb_meta` 单行：迁移写入的语料指纹与事实数；启动时由
 * `RagKnowledgeBase.verifyAndPublish()` 比对（G-2），`republish()` 在同一事务里更新。
 */
data class RagKbMeta(
    val fingerprint: String,
    val factCount: Int
)

/**
 * 四张规则表的整包读出结果（`RagRetrievalRuleRepository.loadAll()`）。
 * 列表均为不可变快照输入。
 */
data class RagRetrievalRuleData(
    val phraseGroups: List<RagPhraseGroup>,
    val intentCoverage: List<RagIntentCoverage>,
    val mandatoryRules: List<RagMandatoryRule>,
    val exclusions: List<RagPrefilterExclusion>
) {
    init {
        require(mandatoryRules.size == 6) {
            "rag_mandatory_rule must hold exactly 6 rows (D-3 adds sort_order 15), got ${mandatoryRules.size}"
        }
        require(mandatoryRules.any { it.sortOrder == 15 && it.matchGroups == listOf("COMPENSATION") }) {
            "rag_mandatory_rule is missing the D-3 COMPENSATION row at sort_order 15"
        }
    }
}
