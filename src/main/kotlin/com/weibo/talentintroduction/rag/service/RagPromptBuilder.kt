package com.weibo.talentintroduction.rag.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import org.springframework.stereotype.Service

/**
 * 计划 03 (T2): 两次 LLM 调用的 user 提示词构建 + 派生规则现算 —— 与
 * `scripts/spike_deepseek_reply.py` 的 `build_retrieval_prompt()` /
 * `build_generation_prompt()` / `retrieval_record()` / `generation_record()` /
 * `fact_render_token()` 逐字等价（D-2）。
 *
 * - I-13: 生成调用里 `render_mode == VERBATIM` 的记录删掉 `answer`，
 *   改为 `render_token = {{FACT:<fact_code>}}` + `render_instruction`；
 *   COMPOSE 记录保留 `answer`。
 * - G-3: `title` 只出现在检索记录（脚本 `retrieval_record()` 同款），
 *   生成记录**不含** `title` 也不含 `retrieval_text`。
 * - G-1: 记录里的 `fact_id` 的值就是 `fact_code`（自增 id 绝不进提示词）。
 * - 第 18/19/21 条生成规则（下标 17/18/20）按当前 `rag_mandatory_rule` 行
 *   现算成自然语言（[renderDerivedRules]），插入规则列表对应位置；
 *   缺失对应规则行时该条留空并从拼好的系统提示词里剔除。
 */
@Service
class RagPromptBuilder(
    private val objectMapper: ObjectMapper
) {

    /** 检索系统提示词 = 头部 + 5 条带序号规则（重现脚本原文）。 */
    fun retrievalSystemPrompt(): String =
        RagPromptConstraints.RETRIEVAL_SYSTEM_HEAD + "\n" +
            numbered(RagPromptConstraints.RETRIEVAL_RULES)

    /** 生成系统提示词（派生三条用常量占位文本 —— 仅测试/降级路径使用）。 */
    fun generationSystemPrompt(): String =
        RagPromptConstraints.GENERATION_SYSTEM_HEAD + "\n" +
            numbered(RagPromptConstraints.GENERATION_RULES)

    /**
     * 生成系统提示词（生产路径）：第 18/19/21 条（下标 17/18/20）被
     * [renderDerivedRules] 的现算文本替换；派生结果为空串的条目被剔除
     * （对应强制规则行已被删除时，不再给出悬空指令）。
     */
    fun generationSystemPrompt(mandatoryRules: List<RagMandatoryRule>): String {
        val derived = renderDerivedRules(mandatoryRules)
        val lines = RagPromptConstraints.GENERATION_RULES.mapIndexed { index, text ->
            when (index) {
                17 -> derived[0]
                18 -> derived[1]
                20 -> derived[2]
                else -> text
            }
        }
        return RagPromptConstraints.GENERATION_SYSTEM_HEAD + "\n" +
            numbered(lines.filter { it.isNotBlank() })
    }

    /**
     * I-13 + G-3 检索 user 提示词：三段 XML 包裹，候选记录字段与脚本
     * `retrieval_record()` 一致 —— `fact_id(=fact_code) / title / category /
     * coverage_keys / reply_policy / status / risk_level / render_mode /
     * retrieval_text`。title 只允许出现在这一侧（G-3 检索层）。
     */
    fun buildRetrievalPrompt(
        inbound: String,
        candidates: List<RagFact>,
        context: RagProcessContext
    ): String {
        val records = objectMapper.createArrayNode()
        candidates.forEach { records.add(retrievalRecord(it)) }
        return buildString {
            appendLine("PROCESS CONTEXT")
            appendLine("<process_context>")
            append(pretty(processContextRecord(context)))
            appendLine()
            appendLine("</process_context>")
            appendLine()
            appendLine("INBOUND EMAIL")
            appendLine("<inbound_email>")
            append(inbound.trim())
            appendLine()
            appendLine("</inbound_email>")
            appendLine()
            appendLine("CANDIDATE FACT CHUNKS")
            appendLine("<candidate_chunks>")
            append(pretty(records))
            appendLine()
            appendLine("</candidate_chunks>")
            appendLine()
            append("Select the minimum sufficient fact IDs now.")
        }
    }

    /**
     * I-13 + G-3 生成 user 提示词（plan 03 的四参签名）：四段 XML 包裹；
     * [mandatory] 为当前生效的强制 fact_code 列表（I-9 有序去重；含请求强制项）。
     */
    fun buildGenerationPrompt(
        retrieved: List<RagFact>,
        inbound: String,
        mandatory: List<String>,
        context: RagProcessContext
    ): String {
        val records = objectMapper.createArrayNode()
        retrieved.forEach { records.add(generationRecord(it)) }
        val mandatoryIds = objectMapper.createArrayNode()
        mandatory.forEach { mandatoryIds.add(it) }
        return buildString {
            appendLine("PROCESS CONTEXT")
            appendLine("<process_context>")
            append(pretty(processContextRecord(context)))
            appendLine()
            appendLine("</process_context>")
            appendLine()
            appendLine("RETRIEVED FACT CHUNKS")
            appendLine("<retrieved_chunks>")
            append(pretty(records))
            appendLine()
            appendLine("</retrieved_chunks>")
            appendLine()
            appendLine("MANDATORY FACT IDS")
            appendLine("<mandatory_fact_ids>")
            append(pretty(mandatoryIds))
            appendLine()
            appendLine("</mandatory_fact_ids>")
            appendLine()
            appendLine("INBOUND EMAIL")
            appendLine("<inbound_email>")
            append(inbound.trim())
            appendLine()
            appendLine("</inbound_email>")
            appendLine()
            append("Generate the JSON result now. Before finalizing, verify that every requested")
            appendLine()
            append("topic appears in coverage, coverage evidence names fact IDs, and the draft")
            appendLine()
            append("contains no unsupported fact or unrelated promotional detail.")
        }
    }

    /**
     * 把第 18/19/21 条（下标 17/18/20）按当前 `rag_mandatory_rule` 行现算成
     * 自然语言（数据驱动，06 的「派生 · 只读」与 A-3 的规则行联动依赖它）。
     *
     * 行选择按命中短语组的**全集**精确匹配（`GOVERNMENT_ORG` 归一为
     * `GOVERNMENT_ORGANIZATION`，与 [RagMandatoryResolver] 同款补偿），行内
     * fact_codes 保持表内顺序：
     * - 下标 17（第 18 条）：`DETAIL_INQUIRY` 行 —— 总览 + 薪资/政府经费令牌；
     * - 下标 18（第 19 条）：`PROGRAMME_NAME` 行 + `GOVERNMENT_ORGANIZATION` 行 +
     *   `{PROGRAMME_NAME, GOVERNMENT_ORGANIZATION}`（合作证明证据）行，按名 → 机构 →
     *   证据的顺序；
     * - 下标 20（第 21 条）：`IP` 行 —— IP 边界令牌 + 材料保密令牌。
     *
     * 对应行缺失时返回空串（调用方从提示词剔除该条）。返回列表长度恒为 3，
     * 顺序与 [RagPromptConstraints.DERIVED_GENERATION_RULE_INDICES] 对齐。
     */
    fun renderDerivedRules(mandatoryRules: List<RagMandatoryRule>): List<String> {
        val rows = mandatoryRules.sortedBy { it.sortOrder }
        fun codesOf(vararg matchGroups: String): List<String> {
            val signature = matchGroups.map { normalizeGroupCode(it) }.toSet()
            return rows
                .filter { row -> row.matchGroups.map { normalizeGroupCode(it) }.toSet() == signature }
                .flatMap { it.factCodes }
                .distinct()
        }

        val detailCodes = codesOf("DETAIL_INQUIRY")
        val programmeCodes = codesOf("PROGRAMME_NAME")
        val organisationCodes = codesOf("GOVERNMENT_ORGANIZATION")
        val evidenceCodes = codesOf("PROGRAMME_NAME", "GOVERNMENT_ORGANIZATION")
        val ipCodes = codesOf("IP")

        val slot18 = if (detailCodes.isEmpty()) {
            ""
        } else {
            "For a request about programme details, a specific plan, further information, " +
                "or the nature of the offer, include the VERBATIM tokens " +
                "${tokenList(detailCodes)} in the order listed, each as its own separate " +
                "paragraph."
        }

        val slot19Tokens = programmeCodes + organisationCodes + evidenceCodes
        val slot19 = when {
            slot19Tokens.isEmpty() -> ""
            evidenceCodes.isEmpty() -> {
                "If the expert asks for the programme name or the responsible government " +
                    "organization, include the VERBATIM tokens ${tokenList(slot19Tokens)} in " +
                    "the order listed, each as its own separate paragraph."
            }
            else -> {
                "If the expert asks for the programme name or the responsible government " +
                    "organization, include the VERBATIM tokens ${tokenList(slot19Tokens)} in " +
                    "the order listed and as separate paragraphs, so that the supporting " +
                    "evidence directly follows the name and organization it evidences."
            }
        }

        val slot21 = if (ipCodes.isEmpty()) {
            ""
        } else {
            "For any intellectual-property question, include the VERBATIM tokens " +
                "${tokenList(ipCodes)} in the order listed as separate paragraphs, and do not " +
                "add any other IP or confidentiality claim."
        }
        return listOf(slot18, slot19, slot21)
    }

    /** 脚本 `retrieval_record()`：字段与顺序一致；`fact_id` 的值即 fact_code（G-1）。 */
    private fun retrievalRecord(fact: RagFact): ObjectNode = objectMapper.createObjectNode().apply {
        put("fact_id", fact.factCode)
        put("title", fact.title)
        put("category", fact.category)
        val coverageKeys = putArray("coverage_keys")
        fact.coverageKeys().forEach(coverageKeys::add)
        put("reply_policy", fact.replyPolicy)
        put("status", fact.status)
        put("risk_level", fact.riskLevel)
        put("render_mode", fact.renderMode)
        put("retrieval_text", fact.retrievalText)
    }

    /**
     * 脚本 `generation_record()`（G-3: 不含 title / retrieval_text）：
     * COMPOSE 保留 answer；VERBATIM 删 answer（I-13），在记录末尾追加
     * `render_token` 与 `render_instruction`（等价脚本 pop + 尾部插入）。
     */
    private fun generationRecord(fact: RagFact): ObjectNode = objectMapper.createObjectNode().apply {
        put("fact_id", fact.factCode)
        if (fact.renderMode == "VERBATIM") {
            // I-13: answer 绝不进入生成提示词 —— 模型只能看到令牌与指令。
        } else {
            put("answer", fact.answer)
        }
        val coverageKeys = putArray("coverage_keys")
        fact.coverageKeys().forEach(coverageKeys::add)
        put("reply_policy", fact.replyPolicy)
        put("status", fact.status)
        put("risk_level", fact.riskLevel)
        put("render_mode", fact.renderMode)
        val sourceRefs = putArray("source_refs")
        fact.sourceRefs().forEach(sourceRefs::add)
        if (fact.renderMode == "VERBATIM") {
            put("render_token", token(fact.factCode))
            put(
                "render_instruction",
                "Place render_token exactly once as its own paragraph; " +
                    "do not restate or paraphrase this fact."
            )
        }
    }

    /** 脚本 `ProcessContext.prompt_record()`：字段名与顺序一致。 */
    private fun processContextRecord(context: RagProcessContext): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("expert_reply_count", context.expertReplyCount)
            val expertTags = putArray("expert_tags")
            context.expertTags.forEach(expertTags::add)
            put("cv_status", context.cvStatus)
        }

    private fun pretty(node: com.fasterxml.jackson.databind.JsonNode): String =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)

    private fun numbered(rules: List<String>): String =
        rules.mapIndexed { index, rule -> "${index + 1}. $rule" }.joinToString("\n")

    private fun tokenList(codes: List<String>): String = when (codes.size) {
        0 -> ""
        1 -> token(codes[0])
        2 -> "${token(codes[0])} and ${token(codes[1])}"
        else -> codes.dropLast(1).joinToString(", ") { token(it) } + ", and " + token(codes.last())
    }

    private fun token(code: String): String = "{{FACT:$code}}"

    private fun normalizeGroupCode(code: String): String =
        if (code == "GOVERNMENT_ORG") "GOVERNMENT_ORGANIZATION" else code
}
