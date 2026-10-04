package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 收发件箱三态标记（I-1）：
 *
 * - `FOLLOWING`：跟进中（原“关注”的新语义，唯一持久化为行值 FOLLOWING）。
 * - `PROVIDED`：已提供（人工标记，不表示系统已上传/发送材料）。
 * - `NONE`：未标记，**不是**可存储的行值 —— 只有“无行”才代表 NONE。
 *
 * 该枚举是 API 与持久化共享的唯一状态词汇；`expert_follow.progress_status`
 * 只允许落库 FOLLOWING / PROVIDED 两者之一。
 */
enum class MailboxProgressStatus { NONE, FOLLOWING, PROVIDED }

/** I-3：关注操作结果（PUT=true / DELETE=false，均幂等；从不 toggle）。 */
data class FollowResult(val followed: Boolean)

/**
 * 新状态端点的回包（主计划接口契约逐字）：本次已提交的状态指令与其派生布尔。
 *
 * `followed` 恒等于 `progressStatus == FOLLOWING`，由同一份指令状态派生，
 * 绝不进行第二次独立读取。
 */
data class MailboxProgressResult(
    val contactId: Long,
    val progressStatus: MailboxProgressStatus,
    val followed: Boolean
)

/**
 * expert_follow 的唯一写者（I-3）。
 *
 * 身份严格取自 Session AUTH_USERNAME（由 controller 解析后传入），请求体永不携带
 * username；username 缺失/非法由 controller/拦截器以 401 拒绝，本服务对空值防御性
 * 拒绝。expert_contact 必须真实存在（JDBC 存在性校验；未知 contact → 404）。
 *
 * 全部读写均为参数化 JDBC（不依赖 Spring Data 实体仓库，保证在 controller 测试里可
 * 以真实服务 + 受控 JDBC 直连测试库）：
 * - FOLLOWING / PROVIDED 用单条 upsert（`ON DUPLICATE KEY UPDATE progress_status`），
 *   并发重复只产生一行且不刷新首次 created_at；转态同样不重置时间；
 * - NONE 用单条 DELETE（按 username + contact，可删当前用户任意标记行）；
 * - 旧 `setFollowed(true)` 复用显式 FOLLOWING 写入，旧 `setFollowed(false)` 只删除
 *   FOLLOWING 行（不误删 PROVIDED）。不使用 toggle。
 *
 * 关注/标记不写 ES / expert_contact / 来信处理状态 / 邮件记录。
 */
@Service
class ExpertFollowService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    /** 旧关注接口（PUT true / DELETE false）保持 `{followed:boolean}` 回包。 */
    fun setFollowed(username: String, expertContactId: Long, followed: Boolean): FollowResult {
        require(username.isNotBlank()) { "未登录" }
        requireContactExists(expertContactId)
        if (followed) {
            // I-3：旧 PUT 显式转 FOLLOWING（与显式状态写入共用同一 upsert）。
            writeMark(username, expertContactId, MailboxProgressStatus.FOLLOWING)
        } else {
            // I-3：旧 DELETE 只删除 FOLLOWING 行，绝不误删 PROVIDED。
            jdbcTemplate.update(
                """
                DELETE FROM expert_follow
                 WHERE username = :username
                   AND expert_contact_id = :contactId
                   AND progress_status = 'FOLLOWING'
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", username)
                    .addValue("contactId", expertContactId)
            )
        }
        return FollowResult(followed)
    }

    /**
     * 显式三态写入（I-1/I-2/I-3）：NONE 删除当前用户标记行；FOLLOWING / PROVIDED
     * 单条 upsert（不刷新 created_at）。非法状态不在写入域内，由 DTO 枚举在 400 边界拒绝。
     */
    fun setProgressStatus(
        username: String,
        expertContactId: Long,
        status: MailboxProgressStatus
    ): MailboxProgressResult {
        require(username.isNotBlank()) { "未登录" }
        requireContactExists(expertContactId)
        if (status == MailboxProgressStatus.NONE) {
            jdbcTemplate.update(
                """
                DELETE FROM expert_follow
                 WHERE username = :username AND expert_contact_id = :contactId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("username", username)
                    .addValue("contactId", expertContactId)
            )
        } else {
            writeMark(username, expertContactId, status)
        }
        return MailboxProgressResult(
            expertContactId,
            status,
            status == MailboxProgressStatus.FOLLOWING
        )
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

    /**
     * 单条 upsert：并发重复插入只产生一行（复合主键），相同状态或转态都不刷新
     * created_at（首次标记时间语义）。
     */
    private fun writeMark(username: String, expertContactId: Long, status: MailboxProgressStatus) {
        jdbcTemplate.update(
            """
            INSERT INTO expert_follow (username, expert_contact_id, created_at, progress_status)
            VALUES (:username, :contactId, :createdAt, :progressStatus)
            ON DUPLICATE KEY UPDATE progress_status = VALUES(progress_status)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", username)
                .addValue("contactId", expertContactId)
                .addValue("createdAt", LocalDateTime.now())
                .addValue("progressStatus", status.name)
        )
    }
}
