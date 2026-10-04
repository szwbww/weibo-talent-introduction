package com.weibo.talentintroduction.mail.service

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 挂起状态对象（GET/PUT/DELETE 统一返回形状；child 02 消费，字段名/类型逐字固定）：
 * `{ contactId, suspended, suspendReason, suspensionPendingCount, followed, progressStatus }`。
 *
 * - suspended：真实新表 `expert_mailbox_suspension` 是否存在 (username, contactId) 行（I-1）。
 * - suspendReason：行内 reason；null = 用户未填写原因，绝不表示未挂起。
 * - suspensionPendingCount：跨该专家**所有真实账号**的 MANUAL_REVIEW 待处理数（I-3），
 *   排除 SIMULATOR_NOOP 与不存在账号，包含 enabled=false 的真实账号；不受日期/主题/
 *   分页/时间线窗口影响。
 * - followed：既有 expert_follow 归属（只读，不因挂起改变 I-6）；恒等于
 *   `progressStatus == FOLLOWING`。
 */
data class MailboxSuspensionState(
    val contactId: Long,
    val suspended: Boolean,
    val suspendReason: String?,
    val suspensionPendingCount: Long,
    val followed: Boolean,
    /**
     * 01 (T4/I-4/I-6)：当前用户对该专家的真实三态标记（DB 真值，无行 = NONE）。
     * `followed` 恒等于 `progressStatus == FOLLOWING`（已提供绝不误报为跟进中）。
     * 字段放尾部并默认从 `followed` 派生，仅为旧构造兼容；stateOf 显式传入同一份读取。
     */
    val progressStatus: MailboxProgressStatus =
        if (followed) MailboxProgressStatus.FOLLOWING else MailboxProgressStatus.NONE
)

data class MailboxPendingBadge(
    val manualReviewTotal: Long,
    val countsByReasonType: Map<String, Long>
)

/** PUT 请求体：`{ "reason": null }` 或字符串；缺省按 null。username 永不出现在请求体。 */
data class MailboxSuspensionRequest(
    val reason: String? = null
)

/**
 * 首次挂起时该专家全球待处理为 0（I-5，409）。
 *
 * 继承 [IllegalStateException]（面向运营的业务异常；未被 controller 显式捕获时仍映射为 400，
 * 不会落到全局 `Exception` handler 的 500）。controller 在 PUT 边界显式映射为 409。
 */
class MailboxSuspensionConflictException(message: String) : IllegalStateException(message)

/**
 * 收发件箱会话挂起/取消的唯一写者（I-1/I-2/I-5）。
 *
 * 身份严格取自 Session AUTH_USERNAME（controller 解析后传入），请求体/查询参数永不携带
 * username。全部读写为参数化 JDBC（禁止拼接用户输入）。
 *
 * - PUT：[suspend] 先校验/trim 原因长度，再回读已有行（已有行原样返回，不覆盖原因），
 *   无行才校验全球 pending（0 → 409）并 INSERT IGNORE（并发重复只产生一行）。
 * - DELETE：[resume] 只按 Session+contact 删除并回读实时状态；可重复，第二次仍
 *   suspended=false。
 * - 只写新表；不写 expert_contact/expert_follow/expert_replied_dismissal/mail_record/
 *   inbound_mail_processing/ES（I-6）。
 */
@Service
class MailboxSuspensionService(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {
    companion object {
        /** 与前端 textarea maxlength 一致的 UTF-16 code units 上限（I-5，超长 400）。 */
        const val MAX_REASON_LENGTH = 500

        /** 待处理权威谓词（复用 inbound_mail_processing 现有 MANUAL_REVIEW 口径 I-3）。 */
        const val PROCESS_STATUS_MANUAL_REVIEW = "MANUAL_REVIEW"
    }

    /** PUT：显式挂起（幂等；不覆盖已有原因）。 */
    @Transactional
    fun suspend(username: String, contactId: Long, rawReason: String?): MailboxSuspensionState {
        require(username.isNotBlank()) { "未登录" }
        // 顺序（I-5）：先校验原因 → 已有行原样返回 → 无行再校验 pending。
        val reason = normalizeReason(rawReason)
        lockContact(contactId)
        val existing = suspensionRow(username, contactId)
        if (existing.first) {
            return stateOf(contactId, username, suspended = true, reason = existing.second)
        }
        if (pendingCount(contactId) == 0L) {
            throw MailboxSuspensionConflictException("该专家当前没有待处理来信，无法挂起")
        }
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO expert_mailbox_suspension (username, expert_contact_id, reason)
            VALUES (:username, :contactId, :reason)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", username)
                .addValue("contactId", contactId)
                .addValue("reason", reason)
        )
        return stateOf(contactId, username, suspended = true, reason = reason)
    }

    /** 仅编辑当前用户已存在的挂起；与取消使用相同contact锁，迟到保存不能重建挂起。 */
    @Transactional
    fun updateReason(username: String, contactId: Long, rawReason: String?): MailboxSuspensionState {
        require(username.isNotBlank()) { "未登录" }
        val reason = normalizeReason(rawReason)
        lockContact(contactId)
        if (!suspensionRow(username, contactId).first) {
            throw MailboxSuspensionConflictException("该会话已不在挂起状态，请刷新后重试")
        }
        jdbcTemplate.update(
            "UPDATE expert_mailbox_suspension SET reason = :reason WHERE username = :username AND expert_contact_id = :contactId",
            MapSqlParameterSource("username", username).addValue("contactId", contactId).addValue("reason", reason)
        )
        return readState(username, contactId)
    }

    /** 导航角标统计邮件数，未关联邮件保留；不改变原待匹配队列的数据或分页。 */
    fun pendingBadge(username: String): MailboxPendingBadge {
        require(username.isNotBlank()) { "未登录" }
        val grouped = jdbcTemplate.query(
            """
            SELECT COALESCE(imp.reason_type, 'UNKNOWN') AS reason_type, COUNT(*) AS mail_count
              FROM inbound_mail_processing imp
              JOIN mail_sender_account msa ON msa.account_code = imp.sender_account_code
             WHERE imp.process_status = :status
               AND msa.account_code <> :simulatorCode
               AND NOT EXISTS (
                   SELECT 1 FROM expert_mailbox_suspension ems
                    WHERE ems.username = :username AND ems.expert_contact_id = imp.expert_contact_id
               )
             GROUP BY COALESCE(imp.reason_type, 'UNKNOWN')
            """.trimIndent(),
            MapSqlParameterSource("username", username)
                .addValue("status", PROCESS_STATUS_MANUAL_REVIEW)
                .addValue("simulatorCode", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE)
        ) { rs, _ -> rs.getString("reason_type") to rs.getLong("mail_count") }.toMap()
        return MailboxPendingBadge(grouped.values.sum(), grouped)
    }

    /** DELETE：显式取消（幂等；不删除/更新其它任何表 I-2/I-6）。 */
    @Transactional
    fun resume(username: String, contactId: Long): MailboxSuspensionState {
        require(username.isNotBlank()) { "未登录" }
        lockContact(contactId)
        jdbcTemplate.update(
            """
            DELETE FROM expert_mailbox_suspension
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", username)
                .addValue("contactId", contactId)
        )
        return readState(username, contactId)
    }

    /** GET：读取实时状态（只读，不加锁，不访问 IMAP/SMTP I-6）。 */
    fun get(username: String, contactId: Long): MailboxSuspensionState {
        require(username.isNotBlank()) { "未登录" }
        requireContactExists(contactId)
        return readState(username, contactId)
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private fun readState(username: String, contactId: Long): MailboxSuspensionState {
        val existing = suspensionRow(username, contactId)
        return stateOf(contactId, username, existing.first, existing.second)
    }

    private fun stateOf(
        contactId: Long,
        username: String,
        suspended: Boolean,
        reason: String?
    ): MailboxSuspensionState {
        // 01 (T4/I-4)：一次读取 DB 真值三态，followed 由同一份结果派生（已提供不再误报跟进中）。
        val progressStatus = progressStatusOf(username, contactId)
        return MailboxSuspensionState(
            contactId = contactId,
            suspended = suspended,
            suspendReason = if (suspended) reason else null,
            suspensionPendingCount = pendingCount(contactId),
            followed = progressStatus == MailboxProgressStatus.FOLLOWING,
            progressStatus = progressStatus
        )
    }

    /** 行存在即挂起：返回 (存在, reason)。空 reason 仍为存在（I-1）。 */
    private fun suspensionRow(username: String, contactId: Long): Pair<Boolean, String?> {
        val rows = jdbcTemplate.query(
            """
            SELECT reason FROM expert_mailbox_suspension
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", username)
                .addValue("contactId", contactId)
        ) { rs, _ -> rs.getString("reason") }
        return if (rows.isEmpty()) false to null else true to rows[0]
    }

    /**
     * 01 (T4/I-4/I-6)：一次读取当前用户对该专家的真实三态；无行 = NONE。
     * 不再只判断行存在 —— 否则已提供会被误说成跟进中。
     */
    private fun progressStatusOf(username: String, contactId: Long): MailboxProgressStatus {
        val stored = jdbcTemplate.query(
            """
            SELECT progress_status FROM expert_follow
             WHERE username = :username AND expert_contact_id = :contactId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("username", username)
                .addValue("contactId", contactId)
        ) { rs, _ -> rs.getString("progress_status") }.firstOrNull()
        return if (stored == null) MailboxProgressStatus.NONE else MailboxProgressStatus.valueOf(stored)
    }

    /**
     * 跨账号真实待处理计数（I-3）：JOIN mail_sender_account 排除不存在账号，显式排除
     * SIMULATOR_NOOP；不带 enabled / 当前账号 / date / q。
     */
    private fun pendingCount(contactId: Long): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
              FROM inbound_mail_processing imp
              JOIN mail_sender_account msa ON msa.account_code = imp.sender_account_code
             WHERE imp.expert_contact_id = :contactId
               AND imp.process_status = :status
               AND msa.account_code <> :simulatorCode
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("contactId", contactId)
                .addValue("status", PROCESS_STATUS_MANUAL_REVIEW)
                .addValue("simulatorCode", MailSenderAccountService.SIMULATOR_ACCOUNT_CODE),
            Long::class.java
        ) ?: 0L

    /** SELECT ... FOR UPDATE 串行化同专家的反复挂起/取消；不存在专家 404。 */
    private fun lockContact(contactId: Long) {
        val locked = jdbcTemplate.queryForList(
            "SELECT id FROM expert_contact WHERE id = :contactId FOR UPDATE",
            MapSqlParameterSource("contactId", contactId),
            Long::class.java
        )
        if (locked.isEmpty()) throw NoSuchElementException("Expert contact not found: $contactId")
    }

    private fun requireContactExists(contactId: Long) {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_contact WHERE id = :contactId",
            MapSqlParameterSource("contactId", contactId),
            Long::class.java
        ) ?: 0L
        if (count == 0L) throw NoSuchElementException("Expert contact not found: $contactId")
    }

    /** trim 后空串 → null；长度在 trim 之后按 UTF-16 code units 校验，超长 400。 */
    private fun normalizeReason(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.length > MAX_REASON_LENGTH) {
            throw IllegalArgumentException("挂起原因最多 $MAX_REASON_LENGTH 个字符")
        }
        return trimmed.ifEmpty { null }
    }
}
