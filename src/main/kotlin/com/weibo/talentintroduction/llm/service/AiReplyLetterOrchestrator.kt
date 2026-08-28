package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/**
 * 13-letter-orchestrator：一次整封编排 LLM 调用 + 六道服务端纯函数校验。
 *
 * `AiReplyLetterCloser`（12 的确定性收口）第 3 步在 c4 中被本编排器替换：服务端从
 * 去重后的存活 claim 构造 `paragraphPlan` / `topicOrder` / 事实清单（含逐字 body），
 * 编排器内联这些输入调用 LLM，解析
 * `{"paragraphs":[{"topic","factIds","text"}],"actionText"}`，通过六道校验后返回
 * `OrchestratedLetter`；**任何失败路径返回 null**（I-8），由收口器退回确定性结果。
 *
 * 六道校验（I-7：全部为解析器内的纯函数，绝不以提示词约束替代）：
 * - G1 来源封闭（I-2）→ `ORCH_FACT_ID_UNKNOWN`
 * - G2 required 事实恰好一次（I-3）→ `ORCH_REQUIRED_FACT_COUNT_INVALID`
 * - G3 受控与冻结事实逐字插槽（I-4）→ `ORCH_VERBATIM_BODY_MISSING`
 * - G4 段落零动作（I-5，冻结事实自带 CTA 豁免）→ `ORCH_ACTION_IN_PARAGRAPH`
 * - G5 动作对账（I-5，最终正文 = actionText 声明集 ∪ 冻结豁免集）→ 复用既有
 *   `AiReplyValidationCodes.ACTION_TEXT_INVALID / ACTION_NOT_ALLOWED / ACTION_BODY_MISMATCH`
 * - G6 编排一致（I-1 / I-6：段数、topic 序列、逐组 factIds 集合）→ `ORCH_PLAN_MISMATCH`
 *
 * 逐字比对的期望串一律取自 `PlanFact.body`（由收口器从 claim 原文 / 受控组常量构建），
 * 校验侧不另抄任何 canonical 字面量（IP-2）。归一化只压缩连续空白为单个空格并 trim，
 * 绝不触碰 `--`、`–`（U+2013）与 `${...}` 占位符（I-4 / G-1）。
 *
 * 下游协议（c5 / 15-workbench-three-step 消费）：`paragraphPlan: List<ParagraphPlanEntry>`
 * （topic + factIds + 可选 gapCondition）、facts 的 id（`f<ruleId>` / `x<n>`）、
 * `topicOrder`、编排响应 `{"paragraphs":[...],"actionText"}`。
 */
data class PlanFact(
    val id: String,
    val topic: String,
    val body: String,
    /** 受控组 id（`QaCoverageKeyCatalog.groupIdOf` 求值，取值 G1..G4），非受控为 null。 */
    val controlled: String?,
    /** qa_rule id ∈ {1, 3, 21, 24}（G-1）或含 `${...}` 占位符 / verbatim 受审正文。 */
    val frozen: Boolean,
    /** I-3：为 true 时必须在全信 `paragraphs[].factIds` 并集里恰好出现一次。 */
    val required: Boolean
)

data class ParagraphPlanEntry(
    val topic: String,
    val factIds: List<String>,
    /** 缺口（对应 request 无存活 claim）挂在主题条目上，不独立成段（I-6）。 */
    val gapCondition: String? = null
)

data class OrchestratedParagraph(
    val topic: String,
    val factIds: List<String>,
    val text: String
)

data class OrchestratedLetter(
    /** 按 `topicOrder` 排列的段落；`paragraphs.size == paragraphPlan.size`（I-6）。 */
    val paragraphs: List<OrchestratedParagraph>,
    /** 恰好一个被授权动作的句子；冻结事实自带 CTA 且为唯一动作来源时为 null（I-5）。 */
    val actionText: String?
)

/**
 * 收口器注入编排尝试的可测接缝：返回 null 表示编排不可用 / 超时 / 校验重试穷尽，
 * 收口器退回 12 的确定性归并（I-8）。生产默认走 `AiReplyLetterOrchestrator.instance`。
 */
fun interface OrchestrationAttempt {
    fun attempt(
        facts: List<PlanFact>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        allowedActions: Set<AiReplyAction>?
    ): OrchestratedLetter?
}

private sealed class OrchestrationParseResult {
    data class Valid(val letter: OrchestratedLetter) : OrchestrationParseResult()
    data class Invalid(val issues: List<AiReplyValidationIssue>) : OrchestrationParseResult()
}

@Service
class AiReplyLetterOrchestrator(
    private val properties: LlmProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AiReplyLetterOrchestrator::class.java)

    init {
        instance = this
    }

    /**
     * 编排一次整封回复。任何失败（客户端不可用 / LLM 关闭 / 超时 / 空响应 / JSON 非法 /
     * 六道校验初始+修复两轮均未通过）都返回 null，由收口器退回确定性结果（I-8）。
     */
    fun orchestrate(
        facts: List<PlanFact>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        allowedActions: Set<AiReplyAction>?
    ): OrchestratedLetter? {
        if (facts.isEmpty() || plan.isEmpty() || topicOrder.size != plan.size) {
            log.warn("13-letter-orchestrator: empty paragraph plan; orchestration skipped (I-8)")
            return null
        }
        val client = llmDraftClientProvider.getIfAvailable()
        if (!properties.enabled || client == null) {
            log.warn("13-letter-orchestrator: LLM client unavailable or disabled; orchestration skipped (I-8)")
            return null
        }
        val messages = buildPrompt(facts, plan, topicOrder, allowedActions)
        val first = executeCall(client, messages)
        if (first.failureType != LlmChatFailureType.SUCCESS || first.content == null) {
            log.warn(
                "13-letter-orchestrator: initial orchestration call failed ({}); falling back (I-8)",
                first.failureType
            )
            return null
        }
        val firstResult = parseAndValidate(first.content, facts, plan, topicOrder, allowedActions)
        val firstIssues = when (firstResult) {
            is OrchestrationParseResult.Valid -> return firstResult.letter
            is OrchestrationParseResult.Invalid -> firstResult.issues
        }
        logValidationFailure(AiReplyValidationAttempt.INITIAL, firstIssues)
        val repairMessages = messages + LlmChatMessage(role = "user", content = correctionMessage(firstIssues))
        val repair = executeCall(client, repairMessages)
        if (repair.failureType != LlmChatFailureType.SUCCESS || repair.content == null) {
            log.warn(
                "13-letter-orchestrator: repair orchestration call failed ({}); falling back (I-8)",
                repair.failureType
            )
            return null
        }
        val repairResult = parseAndValidate(repair.content, facts, plan, topicOrder, allowedActions)
        val repairIssues = when (repairResult) {
            is OrchestrationParseResult.Valid -> return repairResult.letter
            is OrchestrationParseResult.Invalid -> repairResult.issues
        }
        logValidationFailure(AiReplyValidationAttempt.REPAIR, repairIssues)
        return null
    }

    /**
     * 六道校验全部在解析器内以纯函数实现（I-7）。返回所有命中问题（供修复提示词与
     * 日志诊断），任一命中即整份响应无效、走重试。
     */
    private fun parseAndValidate(
        rawResponse: String,
        facts: List<PlanFact>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        allowedActions: Set<AiReplyAction>?
    ): OrchestrationParseResult {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank() || trimmed.startsWith("```")) {
            return invalid(AiReplyValidationCodes.JSON_INVALID)
        }
        val root = try {
            objectMapper.readTree(trimmed)
        } catch (_: Exception) {
            return invalid(AiReplyValidationCodes.JSON_INVALID)
        }
        if (!root.isObject) {
            return invalid(AiReplyValidationCodes.JSON_INVALID)
        }
        if (root.fieldNames().asSequence().toSet() != setOf("paragraphs", "actionText")) {
            return invalid(AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID)
        }
        val paragraphsNode = root.get("paragraphs")
        if (paragraphsNode == null || !paragraphsNode.isArray) {
            return invalid(AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID)
        }
        val issues = mutableListOf<AiReplyValidationIssue>()
        val paragraphs = mutableListOf<OrchestratedParagraph>()
        for (node in paragraphsNode) {
            val parsed = parseParagraph(node)
            if (parsed == null) {
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.STRUCTURE,
                    AiReplyValidationCodes.ORCH_PLAN_MISMATCH
                )
            } else {
                paragraphs += parsed
            }
        }
        if (issues.isNotEmpty()) {
            return OrchestrationParseResult.Invalid(issues.distinct())
        }
        val actionText = parseActionText(root.get("actionText"), issues)
        // G6 (I-1 / I-6)：编排一致 —— 段数、topic 序列、逐组 factIds 集合。
        validatePlanConsistency(paragraphs, plan, topicOrder, issues)
        // G1 (I-2)：来源封闭 —— 返回的 factIds 必须落在 paragraphPlan 并集内。
        validateSourceClosure(paragraphs, plan, facts, issues)
        // G2 (I-3)：required 事实在全信并集里恰好出现一次。
        validateRequiredFactCount(paragraphs, facts, issues)
        // G3 (I-4)：受控与冻结事实逐字插槽。
        validateVerbatimBodies(paragraphs, facts, issues)
        // G4 (I-5)：段落零动作（冻结事实自带 CTA 豁免）。
        validateParagraphActions(paragraphs, facts, actionText, issues)
        // G5 (I-5)：动作对账 —— 最终正文 = actionText 声明集 ∪ 冻结豁免集。
        validateActionReconciliation(paragraphs, actionText, facts, allowedActions, issues)
        if (issues.isNotEmpty()) {
            return OrchestrationParseResult.Invalid(issues.distinct())
        }
        return OrchestrationParseResult.Valid(OrchestratedLetter(paragraphs, actionText))
    }

    private fun parseParagraph(node: JsonNode): OrchestratedParagraph? {
        if (!node.isObject || node.fieldNames().asSequence().toSet() != setOf("topic", "factIds", "text")) {
            return null
        }
        val topicNode = node.get("topic")
        val topic = topicNode?.takeIf { it.isTextual }?.asText()
        if (topic.isNullOrBlank()) {
            return null
        }
        val factIdsNode = node.get("factIds")
        if (factIdsNode == null || !factIdsNode.isArray) {
            return null
        }
        val factIds = mutableListOf<String>()
        for (idNode in factIdsNode) {
            val id = idNode?.takeIf { it.isTextual }?.asText()
            if (id.isNullOrBlank()) {
                return null
            }
            factIds += id
        }
        val textNode = node.get("text")
        val text = textNode?.takeIf { it.isTextual }?.asText()
        if (text.isNullOrBlank()) {
            return null
        }
        return OrchestratedParagraph(topic, factIds, text)
    }

    /** null/JSON-null → null；非文本或空白 → 记 ACTION_TEXT_INVALID 并返回 null。 */
    private fun parseActionText(node: JsonNode?, issues: MutableList<AiReplyValidationIssue>): String? {
        if (node == null || node.isNull) {
            return null
        }
        if (!node.isTextual || node.asText().isBlank()) {
            issues += AiReplyValidationIssue(AiReplyValidationStage.ACTION, AiReplyValidationCodes.ACTION_TEXT_INVALID)
            return null
        }
        return node.asText()
    }

    // ── G6 (I-1 / I-6)：编排一致 ────────────────────────────────────────────────

    private fun validatePlanConsistency(
        paragraphs: List<OrchestratedParagraph>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        if (paragraphs.size != plan.size) {
            issues += AiReplyValidationIssue(
                AiReplyValidationStage.STRUCTURE,
                AiReplyValidationCodes.ORCH_PLAN_MISMATCH
            )
            return
        }
        paragraphs.forEachIndexed { i, paragraph ->
            if (paragraph.topic != topicOrder[i]) {
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.STRUCTURE,
                    AiReplyValidationCodes.ORCH_PLAN_MISMATCH,
                    paragraph.topic
                )
            }
            if (paragraph.factIds.toSet() != plan[i].factIds.toSet()) {
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.STRUCTURE,
                    AiReplyValidationCodes.ORCH_PLAN_MISMATCH,
                    paragraph.topic
                )
            }
        }
    }

    // ── G1 (I-2)：来源封闭 ──────────────────────────────────────────────────────

    private fun validateSourceClosure(
        paragraphs: List<OrchestratedParagraph>,
        plan: List<ParagraphPlanEntry>,
        facts: List<PlanFact>,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        val allowedIds = plan.flatMap { it.factIds }.toSet()
        val knownIds = facts.map { it.id }.toSet()
        paragraphs.forEach { paragraph ->
            paragraph.factIds.forEach { id ->
                if (id !in allowedIds || id !in knownIds) {
                    issues += AiReplyValidationIssue(
                        AiReplyValidationStage.STRUCTURE,
                        AiReplyValidationCodes.ORCH_FACT_ID_UNKNOWN,
                        id
                    )
                }
            }
        }
    }

    // ── G2 (I-3)：required 事实恰好一次 ─────────────────────────────────────────

    private fun validateRequiredFactCount(
        paragraphs: List<OrchestratedParagraph>,
        facts: List<PlanFact>,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        val counts = linkedMapOf<String, Int>()
        paragraphs.forEach { paragraph ->
            paragraph.factIds.forEach { id ->
                counts[id] = (counts[id] ?: 0) + 1
            }
        }
        facts.filter { it.required }.forEach { fact ->
            if (counts[fact.id] != 1) {
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.STRUCTURE,
                    AiReplyValidationCodes.ORCH_REQUIRED_FACT_COUNT_INVALID,
                    fact.id
                )
            }
        }
    }

    // ── G3 (I-4)：受控与冻结事实逐字插槽 ─────────────────────────────────────────

    private fun validateVerbatimBodies(
        paragraphs: List<OrchestratedParagraph>,
        facts: List<PlanFact>,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        val factsById = facts.associateBy { it.id }
        paragraphs.forEach { paragraph ->
            val normalizedText = normalizeWhitespace(paragraph.text)
            paragraph.factIds.distinct().forEach { id ->
                val fact = factsById[id] ?: return@forEach
                if (fact.controlled != null || fact.frozen) {
                    if (!normalizedText.contains(normalizeWhitespace(fact.body))) {
                        issues += AiReplyValidationIssue(
                            AiReplyValidationStage.STRUCTURE,
                            AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING,
                            id
                        )
                    }
                }
            }
        }
    }

    // ── G4 (I-5)：段落零动作（冻结事实自带 CTA 豁免） ─────────────────────────────

    private fun validateParagraphActions(
        paragraphs: List<OrchestratedParagraph>,
        facts: List<PlanFact>,
        actionText: String?,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        val factsById = facts.associateBy { it.id }
        paragraphs.forEach { paragraph ->
            val actions = AiReplyActionPolicy.detectActions(paragraph.text)
            if (actions.isEmpty()) {
                return@forEach
            }
            if (!actionText.isNullOrBlank()) {
                // I-5：actionText 非空时豁免不适用（冻结 CTA 必须是非空 actionText 之外
                // 的唯一动作来源）；段落内出现任何动作句即无效。
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.ACTION,
                    AiReplyValidationCodes.ORCH_ACTION_IN_PARAGRAPH,
                    paragraph.topic
                )
                return@forEach
            }
            val frozenBuiltIn = paragraph.factIds.mapNotNull { factsById[it] }
                .filter { it.frozen }
                .flatMap { AiReplyActionPolicy.detectActions(it.body) }
                .toSet()
            if ((actions - frozenBuiltIn).isNotEmpty()) {
                issues += AiReplyValidationIssue(
                    AiReplyValidationStage.ACTION,
                    AiReplyValidationCodes.ORCH_ACTION_IN_PARAGRAPH,
                    paragraph.topic
                )
            }
        }
    }

    // ── G5 (I-5)：动作对账（复用既有 ACTION_* 码） ───────────────────────────────

    private fun validateActionReconciliation(
        paragraphs: List<OrchestratedParagraph>,
        actionText: String?,
        facts: List<PlanFact>,
        allowedActions: Set<AiReplyAction>?,
        issues: MutableList<AiReplyValidationIssue>
    ) {
        val declaredActions = if (actionText.isNullOrBlank()) {
            emptySet()
        } else {
            AiReplyActionPolicy.detectActions(actionText)
        }
        if (!actionText.isNullOrBlank() && declaredActions.size != 1) {
            issues += AiReplyValidationIssue(AiReplyValidationStage.ACTION, AiReplyValidationCodes.ACTION_TEXT_INVALID)
        }
        if (allowedActions != null && declaredActions.size == 1 && declaredActions.first() !in allowedActions) {
            issues += AiReplyValidationIssue(AiReplyValidationStage.ACTION, AiReplyValidationCodes.ACTION_NOT_ALLOWED)
        }
        val factsById = facts.associateBy { it.id }
        val exemptActions = paragraphs.flatMap { paragraph ->
            paragraph.factIds.mapNotNull { factsById[it] }
                .filter { it.frozen }
                .flatMap { AiReplyActionPolicy.detectActions(it.body) }
        }.toSet()
        val finalBody = buildFinalBody(paragraphs, actionText)
        val bodyActions = if (finalBody.isBlank()) {
            emptySet()
        } else {
            AiReplyActionPolicy.detectActions(finalBody)
        }
        if (bodyActions != declaredActions + exemptActions) {
            issues += AiReplyValidationIssue(AiReplyValidationStage.ACTION, AiReplyValidationCodes.ACTION_BODY_MISMATCH)
        }
    }

    /**
     * 与收口器最终组成一致的正文：段落按序 `\n\n` 连接，actionText（非空时）追加到
     * 末段（同 `AiReplyPointByPointComposer.composeFromPlan` 的约定）。
     */
    private fun buildFinalBody(paragraphs: List<OrchestratedParagraph>, actionText: String?): String {
        val texts = paragraphs.map { it.text }.toMutableList()
        if (!actionText.isNullOrBlank() && texts.isNotEmpty()) {
            val last = texts.size - 1
            texts[last] = texts[last] + " " + actionText.trim()
        }
        return texts.joinToString("\n\n")
    }

    /**
     * 唯一的正文归一化：压缩连续空白为单个空格并 trim。绝不触碰 `--`、`–`（U+2013）
     * 或 `${...}` 占位符（I-4 / G-1）——它们的任何改写都会让逐字子串比对失败。
     */
    private fun normalizeWhitespace(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

    // ── 提示词与修复消息 ────────────────────────────────────────────────────────

    private fun buildPrompt(
        facts: List<PlanFact>,
        plan: List<ParagraphPlanEntry>,
        topicOrder: List<String>,
        allowedActions: Set<AiReplyAction>?
    ): List<LlmChatMessage> {
        val content = buildString {
            appendLine("You are composing the body of a reply letter to an academic expert from server-approved facts.")
            appendLine("The server has already decided the paragraph plan and the topic order; you MUST follow them exactly.")
            appendLine("Return ONLY a JSON object with exactly this shape:")
            appendLine("""{"paragraphs":[{"topic":"...","factIds":["..."],"text":"..."}],"actionText":"..."}""")
            appendLine()
            appendLine("## Paragraph plan (one paragraph per entry, in this exact order)")
            plan.forEachIndexed { index, entry ->
                appendLine("${index + 1}. topic=\"${entry.topic}\" factIds=[${entry.factIds.joinToString(",")}]")
                entry.gapCondition?.takeIf { it.isNotBlank() }?.let { gap ->
                    appendLine("   gap condition: $gap")
                }
            }
            appendLine()
            appendLine("## Topic order")
            appendLine(topicOrder.joinToString(", "))
            appendLine()
            appendLine("## Facts (bodies are authoritative; [CONTROLLED Gx] and [FROZEN] bodies must appear VERBATIM)")
            facts.forEach { fact ->
                val tags = buildList {
                    fact.controlled?.let { add("[CONTROLLED $it]") }
                    if (fact.frozen) add("[FROZEN]")
                }.joinToString(" ")
                appendLine("- id=\"${fact.id}\" topic=\"${fact.topic}\" $tags: ${fact.body}")
            }
            appendLine()
            appendLine("## Authorized actions")
            appendLine(if (allowedActions.isNullOrEmpty()) "NONE" else allowedActions.joinToString(", ") { it.name })
            appendLine()
            appendLine("## Rules")
            appendLine("- \"paragraphs\" must have exactly one entry per plan entry, in the exact plan order, with identical topic strings and identical factIds sets.")
            appendLine("- Every [CONTROLLED] or [FROZEN] fact body must appear verbatim (character-for-character, ignoring only whitespace runs) inside the text of the paragraph that references it. Never reword, truncate, or alter \"--\", \"–\" (U+2013), or $PLACEHOLDER_SAMPLE placeholders.")
            appendLine("- Write each paragraph as one flowing passage with natural transitions and causal connections (for example \"therefore\", \"however\", \"once ... we will\"); do not just concatenate sentences with spaces.")
            appendLine("- Do not put action sentences (requests for materials, meeting proposals) inside paragraph text. If exactly one authorized action is needed, put its sentence in \"actionText\"; otherwise set \"actionText\" to null.")
            appendLine("- If an entry carries a gap condition, its paragraph must still cover the topic and phrase the dependence conditionally (\"this depends on ...\"), never as a standalone apology paragraph.")
            appendLine("- Keep the letter in English.")
        }
        return listOf(LlmChatMessage(role = "user", content = content))
    }

    private fun correctionMessage(issues: List<AiReplyValidationIssue>): String = buildString {
        appendLine("Correct only the reported validation failures in your previous response. Do not repeat the previous response.")
        appendLine("Return ONLY the corrected JSON object with the exact shape {\"paragraphs\":[{\"topic\":\"...\",\"factIds\":[\"...\"],\"text\":\"...\"}],\"actionText\":\"...\"}.")
        issues.forEach { issue ->
            appendLine("- stage=${issue.stage.name}; code=${issue.code}; claimKey=${issue.claimKey ?: "null"}")
            appendLine("  repair: ${repairInstruction(issue)}")
        }
    }

    private fun repairInstruction(issue: AiReplyValidationIssue): String = when (issue.code) {
        AiReplyValidationCodes.JSON_INVALID ->
            "Return one valid JSON object only; remove Markdown fences and all prose outside the object."
        AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID ->
            "The object must contain exactly the fields \"paragraphs\" and \"actionText\"."
        AiReplyValidationCodes.ORCH_PLAN_MISMATCH ->
            "Match the paragraph plan exactly: same number of paragraphs, same topic strings in the same order, identical factIds sets per paragraph."
        AiReplyValidationCodes.ORCH_FACT_ID_UNKNOWN ->
            "Every factId must come from the fact list of the plan; do not invent ids."
        AiReplyValidationCodes.ORCH_REQUIRED_FACT_COUNT_INVALID ->
            "Every required fact must be referenced exactly once across all paragraphs."
        AiReplyValidationCodes.ORCH_VERBATIM_BODY_MISSING ->
            "Controlled and frozen fact bodies must appear verbatim inside the text of the paragraph referencing them; keep \"--\", \"–\" (U+2013) and $PLACEHOLDER_SAMPLE placeholders untouched."
        AiReplyValidationCodes.ORCH_ACTION_IN_PARAGRAPH ->
            "Remove action sentences from paragraph text; put the single authorized action in \"actionText\" or leave it null."
        AiReplyValidationCodes.ACTION_TEXT_INVALID ->
            "\"actionText\" must be null or exactly one action sentence."
        AiReplyValidationCodes.ACTION_NOT_ALLOWED ->
            "The action in \"actionText\" must be one of the authorized actions; otherwise set \"actionText\" to null."
        AiReplyValidationCodes.ACTION_BODY_MISMATCH ->
            "The final body's detected actions must equal the actionText actions plus frozen-fact built-in actions."
        else -> "Match the paragraph plan exactly."
    }

    private fun executeCall(client: LlmDraftClient, messages: List<LlmChatMessage>): LlmChatResult {
        // 复用 AiReplyTimeoutPolicy（默认 30s/300s）与取消语义（同 12 的逐条生成）。
        val budget = AiReplyTimeoutPolicy.resolve(null, null).budget()
        val cancellationToken = AiReplyCancellationToken()
        return client.chatWithModelObservedStream(
            messages = messages,
            temperature = properties.temperature,
            providerModel = AiReplyModel.DEEPSEEK_V4_FLASH.resolveProviderModel(properties),
            timeoutMillis = budget.nextAttemptMillis(),
            jsonOutput = true,
            cancellationToken = cancellationToken
        )
    }

    private fun logValidationFailure(
        attempt: AiReplyValidationAttempt,
        issues: List<AiReplyValidationIssue>
    ) {
        log.warn(
            "13-letter-orchestrator: orchestration validation failed attempt={} issues={}",
            attempt,
            issues.map { "${it.stage.name}/${it.code}/${it.claimKey?.take(120)}" }
        )
    }

    private fun invalid(code: String): OrchestrationParseResult =
        OrchestrationParseResult.Invalid(
            listOf(AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, code))
        )

    companion object {
        private const val PLACEHOLDER_SAMPLE = "\${...}"
        private val WHITESPACE_RUN = Regex("\\s+")

        /** Spring 装配实例；`AiReplyLetterCloser`（object）经此拿到生产编排器（I-8）。 */
        @Volatile
        var instance: AiReplyLetterOrchestrator? = null
    }
}
