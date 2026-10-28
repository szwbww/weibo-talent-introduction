package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.auth.config.MailReadOnlyPolicy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * `expert_mail_read_access` 的唯一写者与读者（I-2/I-3）。
 *
 * 关系即状态：行存在 = 允许内置 `user1` 查看该专家邮件；不存在 = 禁止。`username`
 * 恒为 [MailReadOnlyPolicy.READER_USERNAME]，绝不来自请求体/查询参数；`granted_by`
 * 只取调用方传入的 admin session 用户名。
 *
 * 全部读写为参数化 JDBC；本服务只写 `expert_mail_read_access`，不触碰关注、挂起、
 * 邮件状态、联系人或 ES，也不引入权限缓存。PUT 用单条 `INSERT ... ON DUPLICATE KEY
 * UPDATE`（并发重复仅一行且不刷新 `created_at`）；DELETE 幂等；联系人在存在性校验
 * 后被并发删除时，FK 拒绝写入并统一转成 404（不泄露 SQL）。
 */
@Service
class ExpertMailReadAccessService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    /** GET：无授权行返回 false；专家不存在 404。 */
    fun getAccess(actorUsername: String?, expertContactId: Long): Boolean {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        requireContactExists(expertContactId)
        return grantCount(expertContactId) > 0
    }

    /** PUT：幂等授予，只保留首次 granted_by/created_at；返回 canRead=true。 */
    @Transactional
    fun grant(actorUsername: String?, expertContactId: Long): Boolean {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        requireContactExists(expertContactId)
        try {
            jdbcTemplate.update(
                """
                INSERT INTO expert_mail_read_access (username, expert_contact_id, granted_by, created_at)
                VALUES (:username, :contactId, :grantedBy, :createdAt)
                ON DUPLICATE KEY UPDATE expert_contact_id = expert_contact_id
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                    .addValue("contactId", expertContactId)
                    .addValue("grantedBy", actorUsername)
                    .addValue("createdAt", LocalDateTime.now())
            )
        } catch (ex: DataIntegrityViolationException) {
            // 存在性校验后联系人被并发删除：FK 拒绝，统一按专家不存在处理。
            throw NoSuchElementException("Expert contact not found: $expertContactId")
        }
        return true
    }

    /** DELETE：幂等撤销（无行也成功）；返回 canRead=false。 */
    @Transactional
    fun revoke(actorUsername: String?, expertContactId: Long): Boolean {
        MailReadOnlyPolicy.requireAdmin(actorUsername)
        requireValidContactId(expertContactId)
        requireContactExists(expertContactId)
        jdbcTemplate.update(
            """
            DELETE FROM expert_mail_read_access
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                .addValue("contactId", expertContactId)
        )
        return false
    }

    private fun requireValidContactId(expertContactId: Long) {
        require(expertContactId > 0) { "expertContactId 必须为正数" }
    }

    private fun requireContactExists(expertContactId: Long) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            MapSqlParameterSource("contactId", expertContactId),
            Long::class.java
        ) ?: 0L
        if (exists == 0L) {
            throw NoSuchElementException("Expert contact not found: $expertContactId")
        }
    }

    private fun grantCount(expertContactId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM expert_mail_read_access
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", MailReadOnlyPolicy.READER_USERNAME)
                .addValue("contactId", expertContactId),
            Long::class.java
        ) ?: 0L
}
