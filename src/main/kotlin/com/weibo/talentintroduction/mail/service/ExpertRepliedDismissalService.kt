package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class RepliedDismissalResult(val dismissed: Boolean)

/** 记录当前用户已看过并移出的最新来信；新来信的自增 id 会重新激活已回复分组。 */
@Service
class ExpertRepliedDismissalService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun dismiss(username: String, expertContactId: Long): RepliedDismissalResult {
        require(username.isNotBlank()) { "未登录" }
        val params = MapSqlParameterSource()
            .addValue("username", username)
            .addValue("contactId", expertContactId)
        val contactCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            params,
            Long::class.java
        ) ?: 0L
        if (contactCount == 0L) throw NoSuchElementException("Expert contact not found: $expertContactId")
        val latestInboundId = jdbcTemplate.queryForObject(
            "SELECT MAX(id) FROM inbound_mail_processing WHERE expert_contact_id = :contactId",
            params,
            Long::class.java
        ) ?: throw IllegalArgumentException("该专家尚无来信")
        params.addValue("lastInboundId", latestInboundId)
            .addValue("dismissedAt", LocalDateTime.now())
        jdbcTemplate.update(
            """
            INSERT INTO expert_replied_dismissal
                (username, expert_contact_id, last_inbound_id, dismissed_at)
            VALUES (:username, :contactId, :lastInboundId, :dismissedAt)
            ON DUPLICATE KEY UPDATE
                last_inbound_id = GREATEST(last_inbound_id, VALUES(last_inbound_id)),
                dismissed_at = VALUES(dismissed_at)
            """.trimIndent(),
            params
        )
        return RepliedDismissalResult(true)
    }
}
