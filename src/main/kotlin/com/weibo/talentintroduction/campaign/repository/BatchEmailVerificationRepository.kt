package com.weibo.talentintroduction.campaign.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * `batch_email_verification`（V138）的**唯一**业务写方与只读查询方（子计划 01 T1 / I-6 / I-9）。
 *
 * 设计边界：
 * - 只服务发送前验证链路：一次执行一个专家一行；状态/结果汇总一律来自本表，不塞 task_progress_log。
 * - 结果一旦固定（decision != PENDING），本类只允许 [recordSend] / [recordTag] 更新发送与标签列，
 *   绝不覆盖先前验证结论（provider_* / error_code / request_count / checked_at）。
 * - 发送前用 [markSending] 条件更新（PASS + NOT_SENT → SENDING），必须影响 1 行才允许 SMTP；
 *   0 行表示重复/状态冲突，调用方必须停止而不是重发。
 * - 审计分页严格按 (task_execution_id, id) 游标；验证复用另按规范化邮箱读取一年内原始结果。
 * - 身份键超长（email/orcid/docId）明确抛异常拒绝，绝不截断；只有展示用的 expert_name 允许截断。
 *
 * MySQL 语句兼容性：`SUM(boolean)` 与 `LIMIT ?` 在 MySQL 5.7/8.0 均可解析；本表仅用于 MySQL。
 */
@Repository
class BatchEmailVerificationRepository(private val jdbcTemplate: JdbcTemplate) {

    /** I-6：先插 PENDING 行，返回其自增 id；唯一键冲突（同执行同 ORCID 同邮箱）由 DB 拒绝。 */
    fun insertPending(
        taskExecutionId: Long,
        expertDocId: String?,
        orcidId: String,
        expertName: String?,
        email: String,
        now: LocalDateTime
    ): Long {
        require(taskExecutionId > 0) { "taskExecutionId must be positive" }
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            val statement = connection.prepareStatement(INSERT_PENDING_SQL, arrayOf("id"))
            statement.setLong(1, taskExecutionId)
            statement.setString(2, boundIdentity(expertDocId, COLUMN_EXPERT_DOC_ID, MAX_EXPERT_DOC_ID))
            statement.setString(3, requireIdentity(orcidId, COLUMN_ORCID_ID, MAX_ORCID_ID))
            statement.setString(4, truncate(expertName, MAX_EXPERT_NAME))
            statement.setString(5, requireIdentity(email, COLUMN_EMAIL, MAX_EMAIL))
            statement.setObject(6, now)
            statement.setObject(7, now)
            statement
        }, keyHolder)
        return requireNotNull(keyHolder.key) { "batch_email_verification insert returned no generated id" }.toLong()
    }

    /**
     * 写入验证结论（PENDING → PASS/SKIP/ERROR）。只此一次：`decision = 'PENDING'` 为前置条件，
     * 返回受影响行数；调用方必须要求 1 行，否则说明重复目标或状态被外力改动。
     */
    fun recordDecision(
        id: Long,
        decision: String,
        providerState: String?,
        providerReason: String?,
        errorCode: String?,
        requestCount: Int,
        checkedAt: LocalDateTime?,
        now: LocalDateTime
    ): Int = jdbcTemplate.update(
        RECORD_DECISION_SQL,
        decision,
        truncate(providerState, MAX_PROVIDER_STATE),
        truncate(providerReason, MAX_PROVIDER_REASON),
        truncate(errorCode, MAX_ERROR_CODE),
        requestCount,
        checkedAt,
        now,
        id
    )

    /** 最近一年内的原始已完成结果；按邮箱跨执行复用，不拿复用行延长有效期。 */
    fun findReusable(email: String, now: LocalDateTime): BatchEmailVerificationRow? =
        jdbcTemplate.query(FIND_REUSABLE_SQL, ROW_MAPPER, email, now.minusYears(1), now).firstOrNull()

    /** 复制验证结论及原始时间，不复制其它专家的发送/标签结果。 */
    fun recordReusedDecision(
        id: Long, sourceId: Long, decision: String, providerState: String?,
        providerReason: String?, checkedAt: LocalDateTime, now: LocalDateTime
    ): Int = jdbcTemplate.update(
        RECORD_REUSED_DECISION_SQL, decision, providerState, providerReason,
        checkedAt, sourceId, now, id
    )

    /** 标签处理结果；只写 tag_status / tag_error，不触碰验证结论。 */
    fun recordTag(id: Long, tagStatus: String, tagError: String?, now: LocalDateTime): Int =
        jdbcTemplate.update(RECORD_TAG_SQL, tagStatus, truncate(tagError, MAX_TAG_ERROR), now, id)

    /** 发送结果；只写 send_status / send_reason，不触碰验证结论。 */
    fun recordSend(id: Long, sendStatus: String, sendReason: String?, now: LocalDateTime): Int =
        jdbcTemplate.update(RECORD_SEND_SQL, sendStatus, truncate(sendReason, MAX_SEND_REASON), now, id)

    /**
     * I-6：SMTP 前的条件预占。只有 `decision='PASS' AND send_status='NOT_SENT'` 的行能变成 SENDING，
     * 返回是否恰好影响 1 行。其它结果（重复目标、已 SENT/FAILED/SENDING/SKIPPED、非 PASS）返回 false，
     * 调用方必须停止该次执行并报告状态冲突，不得继续发信。
     */
    fun markSending(id: Long, now: LocalDateTime): Boolean =
        jdbcTemplate.update(MARK_SENDING_SQL, now, id) == 1

    /** 分页游标查询（不外带 +1；`hasMore` 由调用方传 limit+1 判定，见 [readPage]）。 */
    fun listAfter(executionId: Long, afterId: Long, limit: Int): List<BatchEmailVerificationRow> {
        require(limit > 0) { "limit must be positive" }
        return jdbcTemplate.query(LIST_AFTER_SQL, ROW_MAPPER, executionId, afterId, limit)
    }

    /** 单个执行的结果汇总（状态/结果计数一律来自本表，而非 errorSamples）。 */
    fun aggregate(executionId: Long): BatchEmailVerificationAggregate =
        jdbcTemplate.queryForObject(AGGREGATE_SQL, AGGREGATE_MAPPER, executionId)
            ?: BatchEmailVerificationAggregate.EMPTY

    /**
     * 只读事务内组合「一页明细 + 全量汇总」：分页与汇总必须来自同一快照，否则页与汇总会互相矛盾。
     * limit 缺省 50、上限 100；`hasMore` 由 limit+1 判定（多取一行即说明后面还有）。
     */
    @Transactional(readOnly = true)
    fun readPage(
        executionId: Long,
        afterId: Long = 0L,
        limit: Int = DEFAULT_PAGE_SIZE
    ): BatchEmailVerificationPage {
        val effective = limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = listAfter(executionId, afterId, effective + 1)
        return BatchEmailVerificationPage(
            rows = rows.take(effective),
            hasMore = rows.size > effective,
            aggregate = aggregate(executionId)
        )
    }

    private fun requireIdentity(value: String, column: String, max: Int): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("$column 不能为空")
        if (trimmed.length > max) {
            throw IllegalArgumentException("$column 超长（${trimmed.length} > $max），拒绝截断写入")
        }
        return trimmed
    }

    private fun boundIdentity(value: String?, column: String, max: Int): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > max) {
            throw IllegalArgumentException("$column 超长（${trimmed.length} > $max），拒绝截断写入")
        }
        return trimmed
    }

    private fun truncate(value: String?, max: Int): String? =
        value?.takeIf { it.isNotEmpty() }?.let { if (it.length <= max) it else it.substring(0, max) }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100

        const val COLUMN_EMAIL = "email"
        const val COLUMN_ORCID_ID = "orcid_id"
        const val COLUMN_EXPERT_DOC_ID = "expert_doc_id"

        /** 列宽权威值：与 V138 一一对应（身份键超长拒绝，展示名允许截断）。 */
        const val MAX_EMAIL = 320
        const val MAX_ORCID_ID = 128
        const val MAX_EXPERT_DOC_ID = 256
        const val MAX_EXPERT_NAME = 256
        const val MAX_PROVIDER_STATE = 32
        const val MAX_PROVIDER_REASON = 128
        const val MAX_ERROR_CODE = 64
        const val MAX_SEND_REASON = 64
        const val MAX_TAG_ERROR = 256

        private const val INSERT_PENDING_SQL = """
            INSERT INTO batch_email_verification
                (task_execution_id, expert_doc_id, orcid_id, expert_name, email,
                 decision, request_count, send_status, tag_status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 'NOT_SENT', 'NOT_REQUIRED', ?, ?)
        """

        private const val RECORD_DECISION_SQL = """
            UPDATE batch_email_verification
               SET decision = ?, provider_state = ?, provider_reason = ?, error_code = ?,
                   request_count = ?, checked_at = ?, updated_at = ?
             WHERE id = ? AND decision = 'PENDING'
        """

        private const val FIND_REUSABLE_SQL = """
            SELECT * FROM batch_email_verification
             WHERE email = ? AND checked_at > ? AND checked_at <= ?
               AND request_count > 0 AND reused_from_id IS NULL AND error_code IS NULL
               AND ((decision = 'PASS' AND provider_state IN ('deliverable', 'risky', 'unknown'))
                 OR (decision = 'SKIP' AND provider_state IN ('undeliverable', 'risky', 'unknown')))
             ORDER BY checked_at DESC, id DESC LIMIT 1
        """

        private const val RECORD_REUSED_DECISION_SQL = """
            UPDATE batch_email_verification
               SET decision = ?, provider_state = ?, provider_reason = ?, error_code = NULL,
                   request_count = 0, checked_at = ?, reused_from_id = ?, updated_at = ?
             WHERE id = ? AND decision = 'PENDING'
        """

        private const val RECORD_TAG_SQL = """
            UPDATE batch_email_verification
               SET tag_status = ?, tag_error = ?, updated_at = ?
             WHERE id = ?
        """

        private const val RECORD_SEND_SQL = """
            UPDATE batch_email_verification
               SET send_status = ?, send_reason = ?, updated_at = ?
             WHERE id = ?
        """

        private const val MARK_SENDING_SQL = """
            UPDATE batch_email_verification
               SET send_status = 'SENDING', send_reason = NULL, updated_at = ?
             WHERE id = ? AND decision = 'PASS' AND send_status = 'NOT_SENT'
        """

        private const val LIST_AFTER_SQL = """
            SELECT id, task_execution_id, expert_doc_id, orcid_id, expert_name, email, decision,
                   provider_state, provider_reason, error_code, request_count, checked_at,
                   send_status, send_reason, tag_status, tag_error, created_at, updated_at, reused_from_id
              FROM batch_email_verification
             WHERE task_execution_id = ? AND id > ?
             ORDER BY id ASC
             LIMIT ?
        """

        private const val AGGREGATE_SQL = """
            SELECT COUNT(*)                                      AS total_rows,
                   COALESCE(SUM(decision = 'PENDING'), 0)        AS pending_rows,
                   COALESCE(SUM(decision = 'PASS'), 0)           AS pass_rows,
                   COALESCE(SUM(decision = 'SKIP'), 0)           AS skip_rows,
                   COALESCE(SUM(decision = 'ERROR'), 0)          AS error_rows,
                   COALESCE(SUM(send_status = 'NOT_SENT'), 0)    AS not_sent_rows,
                   COALESCE(SUM(send_status = 'SENDING'), 0)     AS sending_rows,
                   COALESCE(SUM(send_status = 'SENT'), 0)        AS sent_rows,
                   COALESCE(SUM(send_status = 'FAILED'), 0)      AS send_failed_rows,
                   COALESCE(SUM(send_status = 'SKIPPED'), 0)     AS send_skipped_rows,
                   COALESCE(SUM(tag_status = 'NOT_REQUIRED'), 0) AS tag_not_required_rows,
                   COALESCE(SUM(tag_status = 'PENDING'), 0)      AS tag_pending_rows,
                   COALESCE(SUM(tag_status = 'APPLIED'), 0)      AS tag_applied_rows,
                   COALESCE(SUM(tag_status = 'FAILED'), 0)       AS tag_failed_rows
              FROM batch_email_verification
             WHERE task_execution_id = ?
        """

        private val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int -> rs.toRow() }

        private val AGGREGATE_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            BatchEmailVerificationAggregate(
                total = rs.getInt("total_rows"),
                pending = rs.getInt("pending_rows"),
                passed = rs.getInt("pass_rows"),
                rejected = rs.getInt("skip_rows"),
                serviceError = rs.getInt("error_rows"),
                notSent = rs.getInt("not_sent_rows"),
                sending = rs.getInt("sending_rows"),
                sent = rs.getInt("sent_rows"),
                sendFailed = rs.getInt("send_failed_rows"),
                sendSkipped = rs.getInt("send_skipped_rows"),
                tagNotRequired = rs.getInt("tag_not_required_rows"),
                tagPending = rs.getInt("tag_pending_rows"),
                tagApplied = rs.getInt("tag_applied_rows"),
                tagFailed = rs.getInt("tag_failed_rows")
            )
        }

        private fun ResultSet.toRow(): BatchEmailVerificationRow = BatchEmailVerificationRow(
            id = getLong("id"),
            taskExecutionId = getLong("task_execution_id"),
            expertDocId = getString("expert_doc_id"),
            orcidId = getString("orcid_id"),
            expertName = getString("expert_name"),
            email = getString("email"),
            decision = getString("decision"),
            providerState = getString("provider_state"),
            providerReason = getString("provider_reason"),
            errorCode = getString("error_code"),
            requestCount = getInt("request_count"),
            checkedAt = getObject("checked_at", LocalDateTime::class.java),
            sendStatus = getString("send_status"),
            sendReason = getString("send_reason"),
            tagStatus = getString("tag_status"),
            tagError = getString("tag_error"),
            createdAt = getObject("created_at", LocalDateTime::class.java),
            updatedAt = getObject("updated_at", LocalDateTime::class.java),
            reusedFromId = getLong("reused_from_id").let { if (wasNull()) null else it }
        )
    }
}

/** 一次执行里一个专家的验证明细行（列语义见 V138 注释）。 */
data class BatchEmailVerificationRow(
    val id: Long,
    val taskExecutionId: Long,
    val expertDocId: String?,
    val orcidId: String,
    val expertName: String?,
    val email: String,
    val decision: String,
    val providerState: String?,
    val providerReason: String?,
    val errorCode: String?,
    val requestCount: Int,
    val checkedAt: LocalDateTime?,
    val sendStatus: String,
    val sendReason: String?,
    val tagStatus: String,
    val tagError: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val reusedFromId: Long? = null
)

/** 单个执行的明细汇总；每个计数都直接来自本表，不从 errorSamples 或进度日志推断。 */
data class BatchEmailVerificationAggregate(
    val total: Int,
    val pending: Int,
    val passed: Int,
    val rejected: Int,
    val serviceError: Int,
    val notSent: Int,
    val sending: Int,
    val sent: Int,
    val sendFailed: Int,
    val sendSkipped: Int,
    val tagNotRequired: Int,
    val tagPending: Int,
    val tagApplied: Int,
    val tagFailed: Int
) {
    companion object {
        val EMPTY = BatchEmailVerificationAggregate(
            total = 0, pending = 0, passed = 0, rejected = 0, serviceError = 0,
            notSent = 0, sending = 0, sent = 0, sendFailed = 0, sendSkipped = 0,
            tagNotRequired = 0, tagPending = 0, tagApplied = 0, tagFailed = 0
        )
    }
}

/** 一页明细 + 同一只读事务内的全量汇总（下游只读接口直接复用）。 */
data class BatchEmailVerificationPage(
    val rows: List<BatchEmailVerificationRow>,
    val hasMore: Boolean,
    val aggregate: BatchEmailVerificationAggregate
)

/** decision 取值集合（VARCHAR(16)）。 */
object BatchEmailVerificationDecision {
    const val PENDING = "PENDING"
    const val PASS = "PASS"
    const val SKIP = "SKIP"
    const val ERROR = "ERROR"
}

/** send_status 取值集合（VARCHAR(16)）。 */
object BatchEmailVerificationSendStatus {
    const val NOT_SENT = "NOT_SENT"
    const val SENDING = "SENDING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
    const val SKIPPED = "SKIPPED"
}

/** tag_status 取值集合（VARCHAR(16)）。 */
object BatchEmailVerificationTagStatus {
    const val NOT_REQUIRED = "NOT_REQUIRED"
    const val PENDING = "PENDING"
    const val APPLIED = "APPLIED"
    const val FAILED = "FAILED"
}

/**
 * 受控服务错误码（VARCHAR(64)）。对外只暴露这些码，不泄露供应商响应正文；
 * 上游按码决定停止原因，下游控制台按码展示文案。
 */
object BatchEmailVerificationErrorCodes {
    const val AUTH_ERROR = "EMAIL_VERIFY_AUTH_ERROR"
    const val NO_CREDITS = "EMAIL_VERIFY_NO_CREDITS"
    const val RATE_LIMITED = "EMAIL_VERIFY_RATE_LIMITED"
    const val TIMEOUT = "EMAIL_VERIFY_TIMEOUT"
    const val INCOMPLETE = "EMAIL_VERIFY_INCOMPLETE"
    const val BAD_RESPONSE = "EMAIL_VERIFY_BAD_RESPONSE"
    const val SERVICE_ERROR = "EMAIL_VERIFY_SERVICE_ERROR"
}
