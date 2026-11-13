package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.ExpertEmailAliasRepository
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus

@Service
class ExpertPrimaryEmailService(
    private val jdbc: JdbcTemplate,
    private val contacts: ExpertContactRepository,
    private val aliases: ExpertEmailAliasRepository,
    private val aliasService: ExpertEmailAliasService,
    private val audit: OperatorActionLogService
) {
    @Transactional
    fun change(contactId: Long, email: String, expectedEmail: String, actor: String): String {
        val target = email.trim().lowercase()
        require(target.length <= 254 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(target)) { "邮箱格式无效" }
        val old = jdbc.query("SELECT expert_email FROM expert_contact WHERE id=? FOR UPDATE",
            { rs, _ -> rs.getString(1) }, contactId).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "专家不存在")
        if (!old.equals(expectedEmail.trim(), true)) conflict("主邮箱已变更，请刷新后重试")
        if (old.equals(target, true)) return old
        val alias = aliases.findByNormalizedEmail(target)
        require(alias != null && alias.expertContactId == contactId && alias.verified) { "只能选择该专家已确认的关联邮箱" }
        val occupied = jdbc.queryForObject("SELECT COUNT(*) FROM expert_contact WHERE LOWER(TRIM(expert_email))=? AND id<>?",
            Long::class.java, target, contactId) ?: 0
        require(occupied == 0L) { "邮箱已被其他联系人使用，不能直接切换" }
        // Review drafts have their own approval context. Do not change their recipient implicitly.
        val reviews = jdbc.queryForObject("""SELECT COUNT(*) FROM mailbox_review_draft d WHERE d.expert_contact_id=? AND d.deleted_at IS NULL
            AND NOT EXISTS (SELECT 1 FROM mail_send_attempt a WHERE a.id=d.send_attempt_id AND a.status='SENT')""",
            Long::class.java, contactId) ?: 0
        if (reviews > 0) conflict("该专家仍有审核草稿，请先处理或删除后切换主邮箱")
        aliasService.bindAlias(contactId, old, "PREVIOUS_PRIMARY")
        // Pin the address on existing editable drafts. CAS version invalidates already-open tabs.
        // Queued drafts retain their immutable snapshot and fail the existing dispatch identity check.
        jdbc.update("""UPDATE mailbox_reply_draft SET context_json=JSON_SET(COALESCE(context_json,'{}'),
            '$.recipientEmail', CASE WHEN JSON_TYPE(JSON_EXTRACT(context_json,'$.recipientEmail'))='STRING' THEN JSON_UNQUOTE(JSON_EXTRACT(context_json,'$.recipientEmail')) ELSE ? END),
            version=version+1, updated_at=CURRENT_TIMESTAMP(3)
            WHERE expert_contact_id=? AND state='ACTIVE' AND send_attempt_id IS NULL""", old, contactId)
        check(jdbc.update("UPDATE expert_contact SET expert_email=?, updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND expert_email=?",
            target, contactId, old) == 1)
        audit.record("EXPERT_CONTACT", contactId, OperatorActionType.CHANGE_PRIMARY_EMAIL, expertContactId = contactId,
            before = mapOf("email" to old), after = mapOf("email" to target), operatorName = actor)
        return target
    }
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
}
