package com.weibo.talentintroduction.rag.service

import com.weibo.talentintroduction.rag.domain.RagFact
import com.weibo.talentintroduction.rag.repository.RagFactRepository
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

/**
 * 计划 04 (T1): RAG 知识库管理服务 —— 事实「修改」与「启停」的唯一后端入口。
 *
 * 全部写路径都必须满足：
 * - I-20: `rag_fact` 的写入一律包在 [RagKnowledgeBase.republish] 事务内
 *   （同一事务：写 fact → 重算指纹 → UPDATE rag_kb_meta → 提交后发布快照）。
 *   **绝不调用启动期校验入口**（该只读门禁会把第一次合法编辑拦下，P0-3）。
 * - I-21: 每次字段变更按字段在**同一事务内**逐行写 `rag_fact_audit`
 *   （NamedParameterJdbcTemplate 直写，先例 TrustReplyWorkbenchStateStore.kt）；
 *   `fingerprint_before` = republish 前的快照指纹，`fingerprint_after` = republish
 *   算出的新指纹。审计绝不写入 QA 审计表/服务（G-4）。
 * - I-22: fact_code / area / seq / legacy_rule_id 只读 —— 请求里带这些字段一律忽略，
 *   以库中现值为准（不报错）。
 * - I-23: 不提供新增/删除事实（控制器无 create / delete 端点）。
 *
 * audit 行在 [RagKnowledgeBase.republish] 返回后、本方法事务提交前写入：
 * republish 用 REQUIRED 传播加入本方法开启的事务，因此 fact 写入、meta 更新、
 * audit 行三者要么一起提交、要么一起回滚（I-21 的回滚断言）。快照仍在事务
 * 提交后（afterCommit）才整体替换 —— 回滚时快照保持旧实例（01 I-3b）。
 */
@Service
class RagFactAdminService(
    private val ragKnowledgeBase: RagKnowledgeBase,
    private val factRepository: RagFactRepository,
    private val jdbc: NamedParameterJdbcTemplate
) {

    /** 只读列表：当前已发布快照中的事实 + 库指纹（快照在每次成功后整体替换）。 */
    fun list(): RagFactAdminListResult {
        val snapshot = ragKnowledgeBase.snapshot()
        return RagFactAdminListResult(
            facts = snapshot.facts.map { it.toAdminFact() },
            fingerprint = snapshot.fingerprint,
            factCount = snapshot.facts.size
        )
    }

    /**
     * I-20/I-21/I-22: 部分更新（null = 不改）。返回 republish 算出的新指纹。
     * 实际发生字段变更时才写库 + 写审计；无变更直接返回当前指纹（不写 audit）。
     */
    @Transactional
    fun update(factCode: String, request: RagFactUpdateRequest): String {
        request.validate()
        val existing = requireFact(factCode)
        val operator = request.operator?.trim()?.takeIf { it.isNotEmpty() }

        val edits = mutableListOf<FieldEdit>()
        var updated = existing
        fun applyEdit(
            field: String,
            current: String,
            requested: String?,
            normalize: (String) -> String = { it },
            withValue: (RagFact, String) -> RagFact
        ) {
            if (requested == null) return
            val next = normalize(requested)
            if (next == current) return
            edits += FieldEdit(field, current, next)
            updated = withValue(updated, next)
        }
        applyEdit("title", existing.title, request.title) { _, v -> updated.copy(title = v) }
        applyEdit("answer", existing.answer, request.answer) { _, v -> updated.copy(answer = v) }
        applyEdit("question_variants", existing.questionVariants, request.questionVariants, { it.normalized("|") }) { _, v ->
            updated.copy(questionVariants = v)
        }
        applyEdit("coverage_keys", existing.coverageKeys, request.coverageKeys, { it.normalized(",") }) { _, v ->
            updated.copy(coverageKeys = v)
        }
        applyEdit("render_mode", existing.renderMode, request.renderMode) { _, v -> updated.copy(renderMode = v) }
        applyEdit("risk_level", existing.riskLevel, request.riskLevel) { _, v -> updated.copy(riskLevel = v) }
        applyEdit("status", existing.status, request.status) { _, v -> updated.copy(status = v) }
        applyEdit("reply_policy", existing.replyPolicy, request.replyPolicy) { _, v -> updated.copy(replyPolicy = v) }

        if (edits.isEmpty()) {
            return ragKnowledgeBase.fingerprint()
        }
        updated = updated.copy(updatedAt = LocalDateTime.now(), updatedBy = operator)
        return commitEdit(existing, updated, edits, operator)
    }

    /**
     * I-20/I-21: 启停事实（enabled 列）。与 [update] 同一原子入口与审计语义；
     * 已是目标状态时为无操作，返回当前指纹。
     */
    @Transactional
    fun toggleEnabled(factCode: String, enabled: Boolean, operator: String?): String {
        val existing = requireFact(factCode)
        if (existing.enabled == enabled) {
            return ragKnowledgeBase.fingerprint()
        }
        val cleanOperator = operator?.trim()?.takeIf { it.isNotEmpty() }
        val edits = listOf(
            FieldEdit("enabled", if (existing.enabled) "1" else "0", if (enabled) "1" else "0")
        )
        val updated = existing.copy(
            enabled = enabled,
            updatedAt = LocalDateTime.now(),
            updatedBy = cleanOperator
        )
        return commitEdit(existing, updated, edits, cleanOperator)
    }

    /** I-20/I-21: republish 写 fact → audit 行（同一事务）→ 返回新指纹。 */
    private fun commitEdit(
        existing: RagFact,
        updated: RagFact,
        edits: List<FieldEdit>,
        operator: String?
    ): String {
        val fingerprintBefore = ragKnowledgeBase.fingerprint()
        val fingerprintAfter = ragKnowledgeBase.republish {
            factRepository.save(updated)
        }
        edits.forEach { edit ->
            insertAudit(
                factCode = existing.factCode,
                field = edit.field,
                oldValue = edit.oldValue,
                newValue = edit.newValue,
                fingerprintBefore = fingerprintBefore,
                fingerprintAfter = fingerprintAfter,
                operator = operator
            )
        }
        return fingerprintAfter
    }

    private fun insertAudit(
        factCode: String,
        field: String,
        oldValue: String,
        newValue: String,
        fingerprintBefore: String,
        fingerprintAfter: String,
        operator: String?
    ) {
        jdbc.update(
            """
            INSERT INTO rag_fact_audit
                (fact_code, field, old_value, new_value, fingerprint_before, fingerprint_after, operator)
            VALUES
                (:factCode, :field, :oldValue, :newValue, :fingerprintBefore, :fingerprintAfter, :operator)
            """.trimIndent(),
            MapSqlParameterSource("factCode", factCode)
                .addValue("field", field)
                .addValue("oldValue", oldValue)
                .addValue("newValue", newValue)
                .addValue("fingerprintBefore", fingerprintBefore)
                .addValue("fingerprintAfter", fingerprintAfter)
                .addValue("operator", operator)
        )
    }

    /** G-1: 按业务键取事实；不存在 → 404。 */
    private fun requireFact(factCode: String): RagFact =
        factRepository.findByFactCode(factCode)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "RAG fact not found: $factCode")

    /** 一次字段变更（audit 行的最小单元，I-21）。 */
    private data class FieldEdit(
        val field: String,
        val oldValue: String,
        val newValue: String
    )
}

/** I-22: 请求 DTO 带只读字段名以便忽略；服务端永不读取 factCode/area/seq/legacyRuleId。 */
data class RagFactUpdateRequest(
    val title: String? = null,
    val answer: String? = null,
    val questionVariants: String? = null,
    val coverageKeys: String? = null,
    val renderMode: String? = null,
    val riskLevel: String? = null,
    val status: String? = null,
    val replyPolicy: String? = null,
    val operator: String? = null,
    // 以下四列只读（I-22），入参一律忽略，以库中现值为准。
    val factCode: String? = null,
    val area: String? = null,
    val seq: Int? = null,
    val legacyRuleId: Long? = null
) {
    /** 枚举列只允许既有取值（非法值 400，避免把库写坏）；超长字段 400。 */
    fun validate() {
        require(renderMode == null || renderMode in RENDER_MODES) {
            "renderMode must be one of $RENDER_MODES: $renderMode"
        }
        require(riskLevel == null || riskLevel in RISK_LEVELS) {
            "riskLevel must be one of $RISK_LEVELS: $riskLevel"
        }
        require(status == null || status in STATUSES) {
            "status must be one of $STATUSES: $status"
        }
        require(replyPolicy == null || replyPolicy in REPLY_POLICIES) {
            "replyPolicy must be one of $REPLY_POLICIES: $replyPolicy"
        }
        title?.let { require(it.length <= 128) { "title exceeds 128 characters" } }
        coverageKeys?.let {
            require(it.normalized(",").length <= 512) { "coverage_keys exceeds 512 characters" }
        }
        questionVariants?.let {
            require(it.normalized("|").length <= 60000) { "question_variants is too long" }
        }
    }

    companion object {
        private val RENDER_MODES = setOf("COMPOSE", "VERBATIM")
        private val RISK_LEVELS = setOf("LOW", "MEDIUM", "HIGH")
        private val STATUSES = setOf("APPROVED", "REVIEW", "DISABLED")
        private val REPLY_POLICIES = setOf("AUTO", "REVIEW", "NEVER")
    }
}

/** 列表 / 保存结果 —— 供知识库页渲染（G-1：响应里绝不含自增 id）。 */
data class RagFactAdminListResult(
    val facts: List<RagFactAdminFact>,
    val fingerprint: String,
    val factCount: Int
)

/** 单条事实的只读视图；字段与 V112 列一一对应（camelCase），不含自增 id。 */
data class RagFactAdminFact(
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
    val legacyRuleId: Long?,
    val enabled: Boolean,
    val sortOrder: Int
)

data class RagFactSaveResult(val fingerprint: String)

/** I-4 归一：按分隔符拆 → trim → 丢空 → 重连（与 RagFact.splitList 同构）。 */
private fun String.normalized(separator: String): String =
    split(separator).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(separator)

private fun RagFact.toAdminFact(): RagFactAdminFact =
    RagFactAdminFact(
        factCode = factCode,
        area = area,
        seq = seq,
        title = title,
        category = category,
        questionVariants = questionVariants,
        keywords = keywords,
        answer = answer,
        coverageKeys = coverageKeys,
        replyPolicy = replyPolicy,
        status = status,
        riskLevel = riskLevel,
        renderMode = renderMode,
        sourceRefs = sourceRefs,
        legacyRuleId = legacyRuleId,
        enabled = enabled,
        sortOrder = sortOrder
    )
