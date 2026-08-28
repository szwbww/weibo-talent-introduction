package com.weibo.talentintroduction.llm.service

import org.slf4j.LoggerFactory

/**
 * 12-letter-closer：assemble 处的确定性整封收口（零新增 LLM 调用，纯函数）。
 *
 * 输入 `(versions, allowedActions)`，输出按主题归并、去重、单 CTA 后的段落列表
 * `List<String>`；`TrustReplyWorkbenchService.verifyAssembly` 在 `:1466-1468` 用本
 * 对象替换 `versions.mapNotNull { answerText }`，`composeLockedItems` 的契约不变。
 *
 * 五步结构（c4 / 13-letter-orchestrator 只替换第 3 步为主题归并的一次编排 LLM 调用，
 * 其余四步不动）：
 *   1. 展开为 canonical order 的 claim 单元（跳过 OMIT；claims 为空的
 *      verbatim / operator / pending 答案作为独立单元保留）——I-1。
 *   2. `sourceRuleIds` 集合相等即同一事实，保留首次出现；空集合不参与去重——I-2。
 *   3. 主题归并：`intentKey.substringBefore('.')` 分组，主题顺序 = 该主题首个存活
 *      claim 的 canonical 位置，组内保持 canonical order；同组 text 单空格连接——I-3。
 *   4. CTA 收口：复用 `AiReplyActionPolicy.detectActions`（不另写动作正则），保留
 *      全信最后一个动作句，其余动作句从文本移除；保留的动作必须在该次 assemble 的
 *      授权集合内，否则整体移除并记 warning——I-4。冻结事实正文（qa_rule id ∈
 *      {1,3,21,24}，G-1）、含 `${...}` 占位符的文本、verbatim 受审正文（I-5 / G-3）
 *      一律不切分、不删改；按 I-4 本应删除时放弃删除并记 warning 交人工。
 *   5. 逃生舱：全部存活条目为非 AI 生成（运营手写 / 安全模板）时直接返回原
 *      orderedAnswers，逐字不变——I-6。
 *
 * warning 经 slf4j 记录（收口不新增任何响应字段；`close` 的输出仍是纯段落列表）。
 */
object AiReplyLetterCloser {

    private val log = LoggerFactory.getLogger(AiReplyLetterCloser::class.java)

    /** G-1 冻结事实：qa_rule id ∈ {1, 3, 21, 24}（需求方 2026-08-28 手调，任何代码不得改其任何列）。 */
    private val FROZEN_RULE_IDS: Set<Long> = setOf(1L, 3L, 21L, 24L)

    private const val PLACEHOLDER_PREFIX = "\${"

    private data class Unit(
        val text: String,
        val intentKey: String,
        val sourceRuleIds: List<Long>,
        val frozen: Boolean
    )

    private data class Sentence(
        val index: Int,
        val text: String,
        val separator: String
    )

    /**
     * 整封收口。`allowedActions` 传 null 表示本次 assemble 取不到授权集合：
     * 按 T-3 降级为「保留最后一处动作、不校验授权」，并记 warning。
     */
    fun close(
        versions: List<TrustReplyItemVersion>,
        allowedActions: Set<AiReplyAction>? = null
    ): List<String> {
        // Step 5 (I-6): 逃生舱 —— 全部存活条目非 AI 生成（运营手写 / 安全模板）时，
        // 跳过收口，逐字返回原 orderedAnswers（composeLockedItems 既有语义：
        // 不 trim、不 dedupe、不 reorder、不调 LLM）。
        val surviving = versions.filter { it.handling != TrustReplyItemHandling.OMIT }
        if (surviving.all { it.generationKind != TrustReplyItemGenerationKind.AI_GENERATED }) {
            return surviving.map { it.answerText }
        }
        // Step 1 (I-1): 展开为 canonical order 的单元列表（claims；空 claims 的答案独立成单元）。
        val units = expandUnits(versions)
        // Step 2 (I-2): 按 sourceRuleIds 集合去重，保留首次出现。
        val deduped = dedupBySourceRuleIds(units)
        // Step 3 (I-3): 主题归并（c4 将只替换本步为一次编排 LLM 调用）。
        val groups = groupByTopic(deduped)
        // Step 4 (I-4 / I-5): CTA 收口。
        val closedGroups = reconcileCta(groups, allowedActions)
        // 输出段落：同主题 text 单空格连接。
        return closedGroups.map { group -> group.joinToString(" ") { it.text } }
    }

    private fun expandUnits(versions: List<TrustReplyItemVersion>): List<Unit> {
        val units = mutableListOf<Unit>()
        versions.forEach { version ->
            if (version.handling == TrustReplyItemHandling.OMIT) {
                return@forEach
            }
            if (version.claims.isEmpty()) {
                // verbatim / operator / pending 答案没有 claim 归因：作为独立单元
                // 保留，sourceRuleIds 为空 → 不参与去重（I-2 例外）。verbatim 正文是
                // QA 库受审内容、可能即冻结事实正文，整体保护（G-1 / G-3）。
                val text = version.answerText.trim()
                if (text.isNotEmpty()) {
                    units += Unit(
                        text = text,
                        intentKey = "__standalone$units.size",
                        sourceRuleIds = emptyList(),
                        frozen = version.handling == TrustReplyItemHandling.ANSWER_FACTS_VERBATIM
                    )
                }
            } else {
                version.claims.forEach { claim ->
                    val text = claim.text.trim()
                    if (text.isNotEmpty()) {
                        units += Unit(
                            text = text,
                            intentKey = claim.intentKey,
                            sourceRuleIds = claim.sourceRuleIds,
                            frozen = claim.text.contains(PLACEHOLDER_PREFIX) ||
                                claim.sourceRuleIds.any { it in FROZEN_RULE_IDS }
                        )
                    }
                }
            }
        }
        return units
    }

    private fun dedupBySourceRuleIds(units: List<Unit>): List<Unit> {
        val seen = linkedSetOf<Set<Long>>()
        return units.filter { unit ->
            if (unit.sourceRuleIds.isEmpty()) {
                // I-2 例外：无依据 / 运营自撰的 claim 不参与去重，一律保留。
                true
            } else {
                seen.add(unit.sourceRuleIds.toSet())
            }
        }
    }

    private fun groupByTopic(units: List<Unit>): List<List<Unit>> {
        val groups = linkedMapOf<String, MutableList<Unit>>()
        units.forEach { unit ->
            groups.getOrPut(unit.intentKey.substringBefore('.')) { mutableListOf() } += unit
        }
        return groups.values.toList()
    }

    /**
     * I-4 / I-5 CTA 收口。逐句复用 `AiReplyActionPolicy.detectActions` 分类；
     * 保留全信最后一个动作句所在处，其余动作句从文本移除。冻结 / 占位符 /
     * verbatim 单元一字不改（本应删除时放弃删除并记 warning 交人工）。
     */
    private fun reconcileCta(
        groups: List<List<Unit>>,
        allowedActions: Set<AiReplyAction>?
    ): List<List<Unit>> {
        val flat = groups.flatten()
        // 逐句分类（全局 canonical 顺序）。
        val classified = flat.map { unit ->
            unit to splitSentences(unit.text).map { sentence -> sentence to detectAction(sentence.text) }
        }
        // I-4: 定位全信最后一个动作句。
        var lastLocation: Pair<Int, Int>? = null
        var keptAction: AiReplyAction? = null
        classified.forEachIndexed { ui, (_, sentences) ->
            sentences.forEachIndexed { si, (_, action) ->
                if (action != null) {
                    lastLocation = ui to si
                    keptAction = action
                }
            }
        }
        val last = lastLocation ?: return groups
        val (lastUnitIndex, lastSentenceIndex) = last
        val kept = keptAction!!

        val authorized = allowedActions == null || kept in allowedActions
        if (allowedActions == null) {
            log.warn(
                "12-letter-closer: allowed actions unavailable for this assemble; " +
                    "keeping the last CTA without an authorization check"
            )
        } else if (!authorized) {
            log.warn(
                "12-letter-closer: kept CTA {} is outside the assemble allowed actions {}; removing it",
                kept, allowedActions
            )
        }

        // 逐组处理，保持组边界（c4 替换第 3 步后本步不需要改动）。
        val result = mutableListOf<List<Unit>>()
        var ui = 0
        groups.forEach { group ->
            val keptUnits = group.mapNotNull { unit ->
                val myIndex = ui++
                val sentences = classified[myIndex].second
                if (unit.frozen) {
                    // I-5 / G-1 / G-3: 冻结事实正文、${...} 占位符文本、verbatim 受审
                    // 正文 —— 一字不改。含动作句且按 I-4 本应删除（非最后一处，或
                    // 保留处未授权）时放弃删除并记 warning，交人工复核。
                    val actionIndexes = sentences.mapIndexedNotNull { si, (_, action) ->
                        if (action != null) si else null
                    }
                    val wouldRemove = when {
                        myIndex != lastUnitIndex -> actionIndexes.isNotEmpty()
                        else -> actionIndexes.any { it != lastSentenceIndex } || !authorized
                    }
                    if (wouldRemove) {
                        log.warn(
                            "12-letter-closer: frozen/verbatim body holds an action sentence that I-4 would " +
                                "remove; removal skipped (G-1/I-5) — hand to human review"
                        )
                    }
                    unit
                } else {
                    val keptSentences = sentences.filterIndexed { si, (_, action) ->
                        when {
                            action == null -> true
                            myIndex == lastUnitIndex && si == lastSentenceIndex -> authorized
                            else -> false
                        }
                    }
                    val rebuilt = rebuildText(keptSentences)
                    if (rebuilt.isEmpty()) null else unit.copy(text = rebuilt)
                }
            }
            if (keptUnits.isNotEmpty()) {
                result += keptUnits
            }
        }
        return result
    }

    private fun detectAction(text: String): AiReplyAction? =
        AiReplyActionPolicy.detectActions(text).firstOrNull()

    /**
     * 重建单元文本：相邻存活句保留原分隔符；被删句两侧的接缝压缩为单个空格
     * （本计划唯一的归一化 = 压缩空白；不触碰 `--`、`–`、`${...}`）。
     */
    private fun rebuildText(sentences: List<Pair<Sentence, AiReplyAction?>>): String {
        if (sentences.isEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        sentences.forEachIndexed { i, (sentence, _) ->
            if (i > 0) {
                val previous = sentences[i - 1].first
                val seam = if (sentence.index == previous.index + 1) previous.separator else " "
                sb.append(seam)
            }
            sb.append(sentence.text)
        }
        return sb.toString().trim()
    }

    private val SENTENCE_ENDINGS = charArrayOf('.', '!', '?', '。', '！', '？')

    /**
     * 与 `AiReplyActionPolicy.SENTENCE_SPLIT`（`(?<=[.!?。！？])\s+|\n+`）同约定的句
     * 切分：句末标点后跟空白、或任意换行处切分。刻意不在这里定义任何 Regex——
     * 动作检测只复用 `detectActions`，本类不携带动作正则。
     */
    private fun splitSentences(text: String): List<Sentence> {
        val sentences = mutableListOf<Sentence>()
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\n' || c == '\r') {
                if (i > start) {
                    sentences += Sentence(sentences.size, text.substring(start, i), "")
                }
                val runStart = i
                while (i < n && (text[i] == '\n' || text[i] == '\r')) i++
                if (sentences.isNotEmpty()) {
                    val last = sentences.size - 1
                    sentences[last] = sentences[last].copy(separator = text.substring(runStart, i))
                }
                start = i
            } else if (c in SENTENCE_ENDINGS && i + 1 < n && text[i + 1].isWhitespace()) {
                val end = i + 1
                if (end > start) {
                    sentences += Sentence(sentences.size, text.substring(start, end), "")
                }
                var runStart = end
                while (runStart < n && text[runStart].isWhitespace()) runStart++
                if (sentences.isNotEmpty()) {
                    val last = sentences.size - 1
                    sentences[last] = sentences[last].copy(separator = text.substring(end, runStart))
                }
                i = runStart
                start = runStart
            } else {
                i++
            }
        }
        if (start < n) {
            sentences += Sentence(sentences.size, text.substring(start), "")
        }
        return sentences
    }
}
