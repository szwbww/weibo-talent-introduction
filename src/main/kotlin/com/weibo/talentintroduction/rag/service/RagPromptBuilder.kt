package com.weibo.talentintroduction.rag.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import org.springframework.stereotype.Service

/**
 * 计划 03 (T2): 两次 LLM 调用的 user 提示词构建 —— 与
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
 * - I-34（计划 06）: 系统提示词（头部 + 编号规则列表）每次构建都从
 *   [RagPromptConfigService.effective()] 取当前值（保存后立即生效，无需重启）；
 *   两段约束的默认值与第 18/19/21 条派生规则（下标 17/18/20）的现算逻辑
 *   收敛在服务端 [RagPromptConfigService]；无服务实例的测试/降级路径回落
 *   [RagPromptConfigService.defaultEffective] 的默认视图。
 */
@Service
class RagPromptBuilder(
    private val objectMapper: ObjectMapper,
    private val configService: RagPromptConfigService? = null
) {

    /** I-34: 每次构建取当前生效视图；无服务实例时回落默认视图。 */
    private fun current(): RagPromptConfigEffective =
        configService?.effective() ?: RagPromptConfigService.defaultEffective()

    /** 检索系统提示词 = 头部 + 当前生效的检索约束（带序号）。 */
    fun retrievalSystemPrompt(): String {
        val effective = current()
        return effective.retrievalSystemHead + "\n" +
            numbered(effective.retrieval.map { it.text }.filter { it.isNotBlank() })
    }

    /** 生成系统提示词 = 头部 + 当前生效的生成约束（含派生三条的现算文本）。 */
    fun generationSystemPrompt(): String {
        val effective = current()
        return effective.generationSystemHead + "\n" +
            numbered(effective.generation.map { it.text }.filter { it.isNotBlank() })
    }

    /**
     * 生成系统提示词（生产路径）。[mandatoryRules] 参数仅为兼容 03 的调用签名
     * （RagLetterComposer 传入语料快照的规则行）；派生三条的现算已在服务端
     * `effective()` 内按同一语料快照完成（I-31/I-34），本方法不再重复计算。
     */
    fun generationSystemPrompt(mandatoryRules: List<RagMandatoryRule>): String {
        val effective = configService?.effective()
            ?: RagPromptConfigService.defaultEffective(mandatoryRules)
        return effective.generationSystemHead + "\n" +
            numbered(effective.generation.map { it.text }.filter { it.isNotBlank() })
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

    private fun token(code: String): String = "{{FACT:$code}}"
}
