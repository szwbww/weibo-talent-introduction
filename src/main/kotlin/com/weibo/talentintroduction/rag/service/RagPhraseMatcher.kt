package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.rag.domain.RagPhraseGroup

/**
 * 计划 02 (T1): 短语组命中判定 —— 与脚本 `_contains_any()` 逐字等价（I-7）。
 *
 * - [containsAny] = 任一短语归一化后是文本归一化结果的子串
 *   （`any(_normalized(phrase) in _normalized(text))`）。
 * - [matchedGroups] = 返回命中了至少一个短语的 `group_code`，按 `group_code` 升序
 *   （供 UI 与日志稳定展示；内部是去重后的有序列表）。
 *
 * 语义只依赖 [RagTextNormalizer]，无状态、无副作用。
 */
class RagPhraseMatcher(
    private val normalizer: RagTextNormalizer = RagTextNormalizer()
) {

    /** I-7: `_contains_any`。短语集合为空时恒 false（与脚本 `any(())` 一致）。 */
    fun containsAny(text: String, phrases: List<String>): Boolean {
        if (phrases.isEmpty()) return false
        val normalized = normalizer.normalize(text)
        return phrases.any { normalizer.normalize(it) in normalized }
    }

    /**
     * 命中组列表：`groups` 中任一短语命中即视为该组命中；返回命中的 `group_code`，
     * 按 `group_code` 升序、去重。
     */
    fun matchedGroups(text: String, groups: List<RagPhraseGroup>): List<String> =
        groups.asSequence()
            .filter { group -> containsAny(text, listOf(group.phrase)) }
            .map { it.groupCode }
            .distinct()
            .sorted()
            .toList()
}
