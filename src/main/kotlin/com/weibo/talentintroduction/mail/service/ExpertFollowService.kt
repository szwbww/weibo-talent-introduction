package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** I-3：关注操作结果（PUT=true / DELETE=false，均幂等；从不 toggle）。 */
data class FollowResult(val followed: Boolean)

/**
 * expert_follow 的唯一写者（I-3）。
 *
 * 身份严格取自 Session AUTH_USERNAME（由 controller 解析后传入），请求体永不携带
 * username；username 缺失/非法由 controller/拦截器以 401 拒绝，本服务对空值防御性
 * 拒绝。expert_contact 必须真实存在（JDBC 存在性校验；未知 contact → 404）。
 *
 * 全部读写均为参数化 JDBC（不依赖 Spring Data 实体仓库，保证在 controller 测试里可
 * 以真实服务 + 受控 JDBC 直连测试库）：PUT 置 true 用 INSERT IGNORE（并发重复只产生
 * 一行，且不刷新首次 created_at），DELETE 置 false 用 DELETE（重复删除幂等成功）。
 * 不使用 toggle。关注不写 ES / expert_contact / 来信处理状态。
 */
@Service
class ExpertFollowService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    fun setFollowed(username: String, expertContactId: Long, followed: Boolean): FollowResult {
        require(username.isNotBlank()) { "未登录" }
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            MapSqlParameterSource("contactId", expertContactId),
            Long::class.java
        ) ?: 0L
        if (exists == 0L) {
            throw NoSuchElementException("Expert contact not found: $expertContactId")
        }
        if (followed) {
            jdbcTemplate.update(
                """
                INSERT IGNORE INTO expert_follow (username, expert_contact_id, created_at)
                VALUES (:username, :contactId, :createdAt)
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", username)
                    .addValue("contactId", expertContactId)
                    .addValue("createdAt", LocalDateTime.now())
            )
        } else {
            jdbcTemplate.update(
                """
                DELETE FROM expert_follow
                 WHERE username = :username AND expert_contact_id = :contactId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", username)
                    .addValue("contactId", expertContactId)
            )
        }
        return FollowResult(followed)
    }
}
