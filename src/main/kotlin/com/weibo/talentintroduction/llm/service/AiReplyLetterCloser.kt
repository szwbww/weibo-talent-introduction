package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.service.QaCoverageKeyCatalog
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
 *   3. 主题归并（13 / c4）：先构造 `paragraphPlan` / 事实清单（T-1，id 用 `f<ruleId>`
 *      或 `x<n>`、受控组经 `QaCoverageKeyCatalog.groupIdOf` 求值、frozen = {1,3,21,24}
 *      或含占位符 / verbatim 正文、单主题事实数 > 4 拆 `<topic>` / `<topic>.2`、缺口挂
 *      `gapCondition`），再试一次编排 LLM 调用（`AiReplyLetterOrchestrator`，六道校验）；
 *      编排返回 null（LLM 不可用 / 超时 / 校验重试穷尽）时退回原确定性主题归并
 *      （I-8），assemble 不失败。
 *   4. CTA 收口：复用 `AiReplyActionPolicy.detectActions`（不另写动作正则），保留
 *      全信最后一个动作句，其余动作句从文本移除；保留的动作必须在该次 assemble 的
 *      授权集合内，否则整体移除并记 warning——I-4。冻结事实正文（qa_rule id ∈
 *      {1,3,21,24}，G-1）、含 `${...}` 占位符的文本、verbatim 受审正文（I-5 / G-3）
 *      一律不切分、不删改；按 I-4 本应删除时放弃删除并记 warning 交人工。编排路径
 *      下本步同样执行，作为最后一道网。
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

    /** T-1：单主题事实数超过该值时拆为 `<topic>` / `<topic>.2` 两个 plan 条目。 */
    private const val MAX_FACTS_PER_PLAN_TOPIC = 4

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
     *
     * `orchestration` 为编排尝试的可测接缝（T-4 / I-8）：null 时走
     * `AiReplyLetterOrchestrator.instance`（Spring 装配的生产编排器）；编排返回 null
     * 则退回 12 的确定性主题归并并记 warning，assemble 永不因编排失败而失败。
     */
    fun close(
        versions: List<TrustReplyItemVersion>,
        allowedActions: Set<AiReplyAction>? = null,
        orchestration: OrchestrationAttempt? = null
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
        // Step 3 (13 / c4): 构造 paragraphPlan → 一次编排 LLM 调用；失败退回确定性归并（I-8）。
        val groups = orchestratedGroupsOrFallback(deduped, versions, allowedActions, orchestration)
        // Step 4 (I-4 / I-5): CTA 收口（对确定性 / 编排两条路径同样执行）。
        val closedGroups = reconcileCta(groups, allowedActions)
        // 输出段落：同主题 text 单空格连接。
        return closedGroups.map { group -> group.joinToString(" ") { it.text } }
    }

    /**
     * T-1 / T-4：从去重后的存活单元构造 `paragraphPlan` 并尝试一次编排；编排返回 null
     * 或不可用时退回 12 的确定性主题归并（I-8）。
     */
    private fun orchestratedGroupsOrFallback(
        deduped: List<Unit>,
        versions: List<TrustReplyItemVersion>,
        allowedActions: Set<AiReplyAction>?,
        orchestration: OrchestrationAttempt?
    ): List<List<Unit>> {
        val (facts, plan, topicOrder) = buildPlan(deduped, versions)
        if (plan.isEmpty()) {
            return groupByTopic(deduped)
        }
        val attempt = orchestration ?: AiReplyLetterOrchestrator.instance?.let { instance ->
            OrchestrationAttempt { f, p, t, a -> instance.orchestrate(f, p, t, a) }
        }
        if (attempt == null) {
            // 无编排器可用（如无 Spring 上下文的纯单元测试）：纯确定性路径，不记 warning。
            return groupByTopic(deduped)
        }
        val letter = attempt.attempt(facts, plan, topicOrder, allowedActions)
        if (letter == null) {
            log.warn(
                "13-letter-orchestrator: orchestration returned null; using the deterministic closure (I-8)"
            )
            return groupByTopic(deduped)
        }
        return orchestratedGroups(letter, facts)
    }

    /**
     * T-1：构造 PlanFact / ParagraphPlanEntry / topicOrder。
     * - `id`：`f<ruleId>`（多规则按升序用 `+` 连接，编码整个去重身份集合）；无依据 /
     *   运营自撰（sourceRuleIds 为空）用 `x<n>`（n = 存活单元序号）。
     * - `controlled`：`QaCoverageKeyCatalog.groupIdOf(intentKey)` 求值（G1..G4 / null）。
     * - `frozen`：沿用展开时的冻结判定（id ∈ {1,3,21,24} / 占位符 / verbatim）。
     * - `required`：12 去重后存活的每条事实都必须恰好出现一次（I-3），恒为 true。
     * - 单主题事实数 > 4 时按二级分类（intentKey 的 '.' 后段）拆为 `<topic>` / `<topic>.2`。
     * - 缺口（非 OMIT 且展开后无任何存活单元的 request）以 `gapCondition` 挂在其主题
     *   条目上，不独立成段（I-6）。当前锁定链保证非 OMIT 恒有正文，此处为协议完备性。
     */
    private fun buildPlan(
        deduped: List<Unit>,
        versions: List<TrustReplyItemVersion>
    ): Triple<List<PlanFact>, List<ParagraphPlanEntry>, List<String>> {
        val facts = deduped.mapIndexed { index, unit ->
            PlanFact(
                id = factIdOf(index, unit),
                topic = unit.intentKey.substringBefore('.'),
                body = unit.text,
                controlled = QaCoverageKeyCatalog.groupIdOf(unit.intentKey),
                frozen = unit.frozen,
                required = true
            )
        }
        val byTopic = linkedMapOf<String, MutableList<Pair<Int, Unit>>>()
        deduped.forEachIndexed { index, unit ->
            byTopic.getOrPut(unit.intentKey.substringBefore('.')) { mutableListOf() } += index to unit
        }
        val plan = mutableListOf<ParagraphPlanEntry>()
        val topicOrder = mutableListOf<String>()
        byTopic.forEach { (topic, entries) ->
            val splits = if (entries.size > MAX_FACTS_PER_PLAN_TOPIC) {
                splitTopic(topic, entries)
            } else {
                listOf(topic to entries)
            }
            splits.forEach { (planTopic, group) ->
                topicOrder += planTopic
                plan += ParagraphPlanEntry(
                    topic = planTopic,
                    factIds = group.map { (index, _) -> factIdOf(index, deduped[index]) }
                )
            }
        }
        versions.forEach { version ->
            if (version.handling != TrustReplyItemHandling.OMIT &&
                version.claims.isEmpty() &&
                version.answerText.isBlank() &&
                version.requestText.isNotBlank()
            ) {
                val gapTopic = "unanswered.request.${version.requestIndex}"
                topicOrder += gapTopic
                plan += ParagraphPlanEntry(gapTopic, emptyList(), version.requestText.trim())
            }
        }
        return Triple(facts, plan, topicOrder)
    }

    private fun factIdOf(index: Int, unit: Unit): String =
        if (unit.sourceRuleIds.isEmpty()) "x$index"
        else "f" + unit.sourceRuleIds.sorted().joinToString("+")

    /** T-1：单主题事实数 > 4 时按二级分类切分，同子主题的事实不跨段。 */
    private fun splitTopic(
        topic: String,
        entries: List<Pair<Int, Unit>>
    ): List<Pair<String, List<Pair<Int, Unit>>>> {
        val cut = (MAX_FACTS_PER_PLAN_TOPIC until entries.size).firstOrNull { i ->
            secondaryKey(entries[i].second) != secondaryKey(entries[i - 1].second)
        } ?: MAX_FACTS_PER_PLAN_TOPIC
        return listOf(topic to entries.take(cut), "$topic.2" to entries.drop(cut))
    }

    private fun secondaryKey(unit: Unit): String = unit.intentKey.substringAfter('.', "")

    /**
     * 把编排结果转回收口器第 4 步的单元组形态：每段一个单元；actionText（非空时）
     * 追加到末段（同 `composeFromPlan` 约定），随后仍过一遍 CTA 收口作为最后一道网。
     */
    private fun orchestratedGroups(
        letter: OrchestratedLetter,
        facts: List<PlanFact>
    ): List<List<Unit>> {
        val factsById = facts.associateBy { it.id }
        val paragraphs = letter.paragraphs.toMutableList()
        if (!letter.actionText.isNullOrBlank() && paragraphs.isNotEmpty()) {
            val last = paragraphs.size - 1
            paragraphs[last] = paragraphs[last].copy(text = paragraphs[last].text + " " + letter.actionText.trim())
        }
        return paragraphs.map { paragraph ->
            listOf(
                Unit(
                    text = paragraph.text,
                    intentKey = paragraph.topic,
                    sourceRuleIds = paragraph.factIds.flatMap { ruleIdsOf(it) }.distinct(),
                    frozen = paragraph.factIds.any { factsById[it]?.frozen == true }
                )
            )
        }
    }

    private fun ruleIdsOf(factId: String): List<Long> {
        if (factId.startsWith("f")) {
            return factId.removePrefix("f").split("+").mapNotNull { it.toLongOrNull() }
        }
        return emptyList()
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
