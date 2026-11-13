package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.expert.service.ExpertIdNormalizer
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.dao.DuplicateKeyException
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

data class ExpertTemplateOverrides(val version: Long = 0, val values: Map<String, String> = emptyMap())

/** Operational rendering overrides only. Never changes ES identity or verification evidence. */
@Service
class ExpertTemplateOverrideService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val audit: OperatorActionLogService
) {
    fun read(expertId: String): ExpertTemplateOverrides = jdbc.query(
        "SELECT version, values_json FROM expert_template_override WHERE expert_key = ?",
        { rs, _ -> ExpertTemplateOverrides(rs.getLong("version"),
            mapper.readTree(rs.getString("values_json")).fields().asSequence().associate { it.key to it.value.asText() }) },
        ExpertIdNormalizer.normalize(expertId)
    ).firstOrNull() ?: ExpertTemplateOverrides()

    @Transactional
    fun save(expertId: String, expectedVersion: Long, values: Map<String, String>, actor: String): ExpertTemplateOverrides {
        require(expectedVersion >= 0) { "版本无效" }
        require(values.keys.all { it in MailPlaceholderService.EXPERT_KEYS }) { "只能编辑专家模板变量" }
        require(values.values.all { it.length <= 8000 && !it.contains('\u0000') && !it.contains("\${") }) {
            "变量值不能包含模板占位符或超过8000字符"
        }
        require(values.values.sumOf { it.length } <= 32000) { "变量总长度不能超过32000字符" }
        val key = ExpertIdNormalizer.normalize(expertId)
        val json = mapper.writeValueAsString(values)
        val updated = jdbc.update("""UPDATE expert_template_override SET values_json=?, version=version+1,
            updated_by=?, updated_at=CURRENT_TIMESTAMP(3) WHERE expert_key=? AND version=?""",
            json, actor, key, expectedVersion)
        if (updated == 0) {
            if (expectedVersion != 0L) conflict()
            try {
                jdbc.update("INSERT INTO expert_template_override VALUES (?,1,?,?,CURRENT_TIMESTAMP(3))", key, json, actor)
            } catch (e: DuplicateKeyException) { conflict() }
        }
        audit.record("EXPERT_TEMPLATE", 0, OperatorActionType.EDIT_EXPERT_TEMPLATE_VARIABLES,
            after = mapOf("expertKey" to key, "keys" to values.keys.sorted(), "version" to expectedVersion + 1), operatorName = actor)
        return ExpertTemplateOverrides(expectedVersion + 1, values)
    }

    private fun conflict(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "变量已被其他操作更新，请重新加载")
}
