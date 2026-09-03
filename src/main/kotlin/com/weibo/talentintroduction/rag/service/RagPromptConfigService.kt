package com.weibo.talentintroduction.rag.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.rag.domain.RagMandatoryRule
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * 计划 06 (T2): RAG 提示词约束配置服务 —— 单行表 `rag_prompt_config` 的
 * 直读直写（先例 `TrustReplyWorkbenchStateStore.kt` 的窄用途单行表 + JSON 载荷
 * + 手写 SQL；不建 @Table 领域类）。
 *
 * 关键不变量：
 * - I-30: 两列 NULL 即回落 03 的 [RagPromptConstraints] 常量（逐字相同、
 *   isCustom=false）；`resetToDefault()` = 两列置 NULL，**不写默认快照**。
 * - I-31: 生成调用的第 18/19/21 条（下标 17/18/20）是派生只读 —— 每次
 *   `effective()` 按当前语料快照的 `rag_mandatory_rule` 现算（规则表变了页面
 *   就跟着变，A-4），不入库；`save()` 忽略入参里 derived=true 的条目。
 * - I-32: 存储为纯文本 JSON 数组，条目不含 no/index 字段；页面编号是渲染产物。
 * - I-33: 每次保存/恢复写 `operator_action_log` 审计（改动下标 + 改前/改后值、
 *   新增、删除、操作人、时间），与配置更新同一事务（NamedParameterJdbcTemplate
 *   直写，先例 04 的审计写入）。
 * - I-34: [RagPromptBuilder] 每次构建经本服务 `effective()` 取值。
 *
 * 派生三条的显示/拼接位置与前端渲染共用 [derivedSlotsFor] 的「固定第 18/19/21
 * 位」规则：合并列表长度 ≥ 21 时派生条目恒占 0-based 17/18/20 位，可编辑条目
 * 按序填入其余位置；总长 < 21（运营删掉过多可编辑条）时退化为末尾三位。
 */
@Service
class RagPromptConfigService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val knowledgeBase: RagKnowledgeBase,
    private val objectMapper: ObjectMapper
) {

    /** 派生（只读）条目在合并列表中的 0-based 固定槽位（= 第 18/19/21 条，I-31）。 */
    fun derivedSlotsFor(total: Int): Set<Int> =
        if (total >= DERIVED_COUNT_BOUNDARY) setOf(17, 18, 20)
        else setOf(total - 3, total - 2, total - 1)

    /**
     * I-30/I-31: 当前生效的完整约束视图（页面 GET 与 [RagPromptBuilder] 共用）。
     * 两段均含系统提示词头部 —— 头是静态常量，放在这里转发使 builder 不再直接
     * 触碰 [RagPromptConstraints]（I-34 的取值来源收敛到本服务）。
     */
    fun effective(): RagPromptConfigEffective {
        val row = storedRow()
        val storedRetrieval = row?.retrievalJson?.let { parseStringList(it) }
        val storedGeneration = row?.generationJson?.let { parseStringList(it) }
        return effectiveOf(
            storedRetrieval = storedRetrieval,
            storedGeneration = storedGeneration,
            mandatoryRules = knowledgeBase.snapshot().mandatoryRules,
            isCustom = storedRetrieval != null || storedGeneration != null,
            updatedAt = row?.updatedAt?.toString(),
            updatedBy = row?.updatedBy
        )
    }

    /**
     * I-31/I-32/I-33: 保存。入参里 derived=true 的条目（派生三条）一律忽略
     * （文本按现算值处理，绝不入库）；其余按序整体覆写对应列。实际差异
     * （改动下标 + 改前/改后、新增、删除）连同操作人/时间写一条审计（I-33）。
     */
    @Transactional
    fun save(request: RagPromptConfigSaveRequest): RagPromptConfigEffective {
        val operator = request.operator?.trim()?.takeIf { it.isNotEmpty() }
        val newRetrieval = request.retrieval.filterNot { it.derived }.map { it.text }
        val newGeneration = request.generation.filterNot { it.derived }.map { it.text }
        val row = storedRow()
        val oldRetrieval = row?.retrievalJson?.let { parseStringList(it) }.orEmpty()
        val oldGeneration = row?.generationJson?.let { parseStringList(it) }.orEmpty()

        jdbc.update(
            """
            UPDATE rag_prompt_config
               SET retrieval_constraints = :retrieval,
                   generation_constraints = :generation,
                   updated_by = :operator
             WHERE id = 1
            """.trimIndent(),
            MapSqlParameterSource("retrieval", encodeList(newRetrieval))
                .addValue("generation", encodeList(newGeneration))
                .addValue("operator", operator)
        )
        writeAudit(
            actionType = ACTION_TYPE_SAVE,
            actionSummary = "保存 RAG 提示词约束配置",
            oldRetrieval = oldRetrieval,
            newRetrieval = newRetrieval,
            oldGeneration = oldGeneration,
            newGeneration = newGeneration,
            operator = operator
        )
        return effective()
    }

    /** I-30/I-33: 全部恢复默认 = 两列置 NULL（不是写入一份默认快照），并留审计。 */
    @Transactional
    fun resetToDefault(operator: String?): RagPromptConfigEffective {
        val cleanOperator = operator?.trim()?.takeIf { it.isNotEmpty() }
        val row = storedRow()
        jdbc.update(
            """
            UPDATE rag_prompt_config
               SET retrieval_constraints = NULL,
                   generation_constraints = NULL,
                   updated_by = :operator
             WHERE id = 1
            """.trimIndent(),
            MapSqlParameterSource("operator", cleanOperator)
        )
        writeAudit(
            actionType = ACTION_TYPE_RESET,
            actionSummary = "恢复 RAG 提示词约束默认（置 NULL）",
            oldRetrieval = row?.retrievalJson?.let { parseStringList(it) }.orEmpty(),
            newRetrieval = emptyList(),
            oldGeneration = row?.generationJson?.let { parseStringList(it) }.orEmpty(),
            newGeneration = emptyList(),
            operator = cleanOperator
        )
        return effective()
    }

    companion object {
        private const val DERIVED_COUNT_BOUNDARY = 21
        private const val ACTION_TYPE_SAVE = "SAVE_RAG_PROMPT_CONFIG"
        private const val ACTION_TYPE_RESET = "RESET_RAG_PROMPT_CONFIG"

        /**
         * 无服务实例时的默认视图（03 的 builder 测试/降级路径，I-34）：
         * 存储视为 NULL → 两段回落 [RagPromptConstraints] 常量；派生三条按给定
         * [mandatoryRules] 现算（缺规则行时该条回落常量占位文本，保持 22 条）。
         */
        fun defaultEffective(mandatoryRules: List<RagMandatoryRule> = emptyList()): RagPromptConfigEffective =
            effectiveOf(null, null, mandatoryRules, false, null, null)

        private fun effectiveOf(
            storedRetrieval: List<String>?,
            storedGeneration: List<String>?,
            mandatoryRules: List<RagMandatoryRule>,
            isCustom: Boolean,
            updatedAt: String?,
            updatedBy: String?
        ): RagPromptConfigEffective {
            val retrievalBase = storedRetrieval ?: RagPromptConstraints.RETRIEVAL_RULES
            val generationBase = storedGeneration ?: defaultGenerationBase()
            val derivedTexts = derivedTextsOf(mandatoryRules)
            val generation = mergeGeneration(
                base = generationBase,
                derived = derivedTexts,
                isDefault = storedGeneration == null
            )
            return RagPromptConfigEffective(
                retrievalSystemHead = RagPromptConstraints.RETRIEVAL_SYSTEM_HEAD,
                generationSystemHead = RagPromptConstraints.GENERATION_SYSTEM_HEAD,
                retrieval = retrievalBase.mapIndexed { _, text ->
                    RagConstraintView(text = text)
                },
                generation = generation,
                isCustom = isCustom,
                updatedAt = updatedAt,
                updatedBy = updatedBy
            )
        }

        /** 默认可编辑生成约束 = 03 常量去掉派生三条（22 − 3 = 19 条，I-31）。 */
        private fun defaultGenerationBase(): List<String> =
            RagPromptConstraints.GENERATION_RULES.filterIndexed { index, _ ->
                index !in RagPromptConstraints.DERIVED_GENERATION_RULE_INDICES
            }

        /**
         * 把可编辑约束与派生三条按 [derivedSlotsFor] 的固定槽位合并成展示/拼接
         * 列表（I-31/I-32）。派生条目标 derived=true；可编辑条目按内容回落常量
         * 时携带 provenance 徽章标记（第 12 条「本次改动」= I-18 改写、第 22 条
         * 「新增」= D-6 追加），仅当该条文本与 03 常量逐字相同且位于对应槽位时
         * 才标记 —— 运营改过即自然消失。
         */
        private fun mergeGeneration(
            base: List<String>,
            derived: List<String>,
            isDefault: Boolean
        ): List<RagConstraintView> {
            val views = base.mapIndexed { index, text ->
                RagConstraintView(
                    text = text,
                    changed = isDefault && index == 11 && text == RagPromptConstraints.GENERATION_RULES[11],
                    added = isDefault && index == 18 && base.size >= 19 &&
                        text == RagPromptConstraints.GENERATION_RULES[21]
                )
            }
            val total = views.size + derived.size
            val slots = if (total >= DERIVED_COUNT_BOUNDARY) setOf(17, 18, 20)
            else setOf(total - 3, total - 2, total - 1)
            val result = ArrayList<RagConstraintView>(total)
            var baseIndex = 0
            var derivedIndex = 0
            for (slot in 0 until total) {
                if (slot in slots) {
                    result.add(RagConstraintView(text = derived[derivedIndex], derived = true))
                    derivedIndex += 1
                } else {
                    result.add(views[baseIndex])
                    baseIndex += 1
                }
            }
            return result
        }

        /**
         * 第 18/19/21 条（0-based 17/18/20）的现算文本 —— 逻辑从 03 的
         * [RagPromptBuilder.renderDerivedRules] 平移（按 `rag_mandatory_rule`
         * 行现算成自然语言，行缺失返回空串），空串回落常量占位文本以保持
         * 22 条的稳定形态。返回长度恒为 3。
         */
        private fun derivedTextsOf(mandatoryRules: List<RagMandatoryRule>): List<String> {
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
            val computed = listOf(slot18, slot19, slot21)
            return computed.mapIndexed { index, text ->
                if (text.isBlank()) {
                    RagPromptConstraints.GENERATION_RULES[
                        RagPromptConstraints.DERIVED_GENERATION_RULE_INDICES.elementAt(index)
                    ]
                } else {
                    text
                }
            }
        }

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

    // ------------------------------------------------------------------
    // DB / audit
    // ------------------------------------------------------------------

    private data class StoredRow(
        val retrievalJson: String?,
        val generationJson: String?,
        val updatedAt: LocalDateTime?,
        val updatedBy: String?
    )

    private fun storedRow(): StoredRow? {
        val rows = jdbc.queryForList(
            """
            SELECT retrieval_constraints, generation_constraints, updated_at, updated_by
              FROM rag_prompt_config
             WHERE id = 1
            """.trimIndent(),
            MapSqlParameterSource("id", 1L)
        )
        val row = rows.firstOrNull() ?: return null
        return StoredRow(
            retrievalJson = row["retrieval_constraints"] as? String,
            generationJson = row["generation_constraints"] as? String,
            updatedAt = (row["updated_at"] as? java.sql.Timestamp)?.toLocalDateTime(),
            updatedBy = row["updated_by"] as? String
        )
    }

    private fun encodeList(values: List<String>): String =
        objectMapper.writeValueAsString(values)

    private fun parseStringList(json: String): List<String> =
        try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (e: Exception) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "RAG_PROMPT_CONFIG_INVALID_JSON: constraint column is not a string array",
                e
            )
        }

    /**
     * I-33: 审计写 `operator_action_log`（NamedParameterJdbcTemplate 直写，与配置
     * 更新同一事务）。target_type/target_id 恒为 RAG_PROMPT_CONFIG/1（无联系人/
     * 邮件上下文），before/after 为整份配置 JSON，note 为按段的差异明细
     * （改动下标 + 改前/改后值、新增、删除）。
     */
    private fun writeAudit(
        actionType: String,
        actionSummary: String,
        oldRetrieval: List<String>,
        newRetrieval: List<String>,
        oldGeneration: List<String>,
        newGeneration: List<String>,
        operator: String?
    ) {
        val before = objectMapper.createObjectNode()
        before.put("retrieval", encodeArray(oldRetrieval))
        before.put("generation", encodeArray(oldGeneration))
        val after = objectMapper.createObjectNode()
        after.put("retrieval", encodeArray(newRetrieval))
        after.put("generation", encodeArray(newGeneration))
        val note = objectMapper.createObjectNode()
        note.set<ObjectNode>("retrieval", diffNode("retrieval", oldRetrieval, newRetrieval))
        note.set<ObjectNode>("generation", diffNode("generation", oldGeneration, newGeneration))

        jdbc.update(
            """
            INSERT INTO operator_action_log
                (target_type, target_id, action_type, action_summary,
                 before_value, after_value, operator_name, note)
            VALUES
                (:targetType, :targetId, :actionType, :actionSummary,
                 :before, :after, :operator, :note)
            """.trimIndent(),
            MapSqlParameterSource("targetType", "RAG_PROMPT_CONFIG")
                .addValue("targetId", 1L)
                .addValue("actionType", actionType)
                .addValue("actionSummary", actionSummary)
                .addValue("before", before.toString())
                .addValue("after", after.toString())
                .addValue("operator", operator)
                .addValue("note", note.toString())
        )
    }

    /**
     * 审计明细（I-33）：diff 采用「公共前缀/后缀裁剪 + 中间逐位对齐」——
     * 纯编辑/增/删场景归类干净：中间等长 → 逐位 changed（afterIndex +
     * before/after 文本）；不等长 → 先按 min 长度逐位 changed，剩余尾部
     * 为纯 deleted（before 下标）/ added（after 下标）。对运营单次保存的
     * 改动粒度（几条编辑 + 追加 + 删除）结果确定且可读。
     */
    private fun diffNode(segment: String, before: List<String>, after: List<String>): ObjectNode {
        val node = objectMapper.createObjectNode()
        node.put("segment", segment)
        val changed = node.putArray("changed")
        val added = node.putArray("added")
        val deleted = node.putArray("deleted")

        var prefix = 0
        while (prefix < before.size && prefix < after.size && before[prefix] == after[prefix]) {
            prefix += 1
        }
        var suffix = 0
        while (
            suffix < before.size - prefix &&
            suffix < after.size - prefix &&
            before[before.size - 1 - suffix] == after[after.size - 1 - suffix]
        ) {
            suffix += 1
        }
        val middleBefore = before.subList(prefix, before.size - suffix)
        val middleAfter = after.subList(prefix, after.size - suffix)
        val common = minOf(middleBefore.size, middleAfter.size)
        for (t in 0 until common) {
            if (middleBefore[t] != middleAfter[t]) {
                val entry = changed.addObject()
                entry.put("afterIndex", prefix + t)
                entry.put("before", middleBefore[t])
                entry.put("after", middleAfter[t])
            }
        }
        for (t in common until middleBefore.size) {
            deleted.add(deletedEntry(prefix + t, middleBefore[t]))
        }
        for (t in common until middleAfter.size) {
            added.add(addedEntry(prefix + t, middleAfter[t]))
        }
        return node
    }

    private fun deletedEntry(index: Int, text: String): ObjectNode {
        val entry = objectMapper.createObjectNode()
        entry.put("index", index)
        entry.put("text", text)
        return entry
    }

    private fun addedEntry(index: Int, text: String): ObjectNode {
        val entry = objectMapper.createObjectNode()
        entry.put("index", index)
        entry.put("text", text)
        return entry
    }

    private fun encodeArray(values: List<String>): ArrayNode {
        val array = objectMapper.createArrayNode()
        values.forEach(array::add)
        return array
    }
}

/** 展示用单条约束（I-32：只带文本；编号由渲染时按下标生成）。 */
data class RagConstraintView(
    val text: String,
    val derived: Boolean = false,
    val changed: Boolean = false,
    val added: Boolean = false
)

/** PUT 入参的单条约束；derived=true 的条目由服务端忽略（I-31）。 */
data class RagConstraintInput(
    val text: String,
    val derived: Boolean = false
)

data class RagPromptConfigSaveRequest(
    val retrieval: List<RagConstraintInput> = emptyList(),
    val generation: List<RagConstraintInput> = emptyList(),
    val operator: String? = null
)

data class RagPromptConfigResetRequest(
    val operator: String? = null
)

/** I-30/I-31: 当前生效的完整视图（系统提示词头部 + 两段约束 + 自定义标记）。 */
data class RagPromptConfigEffective(
    val retrievalSystemHead: String,
    val generationSystemHead: String,
    val retrieval: List<RagConstraintView>,
    val generation: List<RagConstraintView>,
    val isCustom: Boolean,
    val updatedAt: String?,
    val updatedBy: String?
)
