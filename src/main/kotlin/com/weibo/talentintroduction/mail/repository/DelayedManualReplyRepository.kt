package com.weibo.talentintroduction.mail.repository

import com.weibo.talentintroduction.mail.service.DELAYED_MANUAL_REPLY_DELAY_MS
import com.weibo.talentintroduction.mail.service.DelayedDraftRestore
import com.weibo.talentintroduction.mail.service.DelayedLegacyRestore
import com.weibo.talentintroduction.mail.service.DelayedManualReplyInsert
import com.weibo.talentintroduction.mail.service.DelayedManualReplyOriginKind
import com.weibo.talentintroduction.mail.service.DelayedManualReplyRow
import com.weibo.talentintroduction.mail.service.DelayedManualReplySnapshotCodec
import com.weibo.talentintroduction.mail.service.DelayedManualReplyStatus
import com.weibo.talentintroduction.mail.service.DelayedReplyRestore
import com.weibo.talentintroduction.mail.service.DelayedReviewRestore
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.ResultSet

/**
 * fast-p 01（I-1/I-10）：`delayed_manual_reply` 仓储。
 *
 * 沿用项目既有 NamedParameterJdbcTemplate + GeneratedKeyHolder + 显式 RowMapper +
 * MANDATORY 事务形态（见 [MailReplyDraftRepository]），不使用 Spring Data JDBC。
 *
 * 所有 SQL 的列名/表名/where 条件均为固定字面量，绝不接受调用方拼接的表名或条件。
 * 状态时间一律为 epoch 毫秒；数据库时钟由 [currentTimeMs] 读取。
 */
@Repository
class DelayedManualReplyRepository(private val jdbc: NamedParameterJdbcTemplate) {

    companion object {
        /** due 扫描固定上限；扫描只读 id，不领取。 */
        const val DUE_SCAN_LIMIT = 20

        private const val COLUMNS =
            "id,send_attempt_id,expert_contact_id,account_code,actor_username,origin_kind,origin_id," +
                "origin_version,revision,status,due_at_ms,dispatch_token,claimed_at_ms,subject,text_body," +
                "snapshot_json,public_error_code,created_at_ms,updated_at_ms"

        private val COMPLETABLE_STATUSES = setOf(
            DelayedManualReplyStatus.SENT,
            DelayedManualReplyStatus.FAILED,
            DelayedManualReplyStatus.UNKNOWN
        )
    }

    private val mapper = RowMapper { r: ResultSet, _: Int -> r.toRow() }

    /** 数据库权威毫秒时间（`CURRENT_TIMESTAMP(3)` 转 epoch），不读 JVM 时钟。 */
    fun currentTimeMs(): Long = jdbc.query(
        "SELECT CAST(ROUND(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000) AS SIGNED) AS now_ms",
        emptyMap<String, Any?>(),
        RowMapper { r: ResultSet, _: Int -> r.getLong("now_ms") }
    ).single()

    fun findById(id: Long): DelayedManualReplyRow? = jdbc.query(
        "SELECT $COLUMNS FROM delayed_manual_reply WHERE id=:id",
        mapOf("id" to id),
        mapper
    ).singleOrNull()

    fun findByIds(ids: Collection<Long>): List<DelayedManualReplyRow> = if (ids.isEmpty()) {
        emptyList()
    } else {
        jdbc.query(
            "SELECT $COLUMNS FROM delayed_manual_reply WHERE id IN (:ids) ORDER BY created_at_ms,id",
            mapOf("ids" to ids),
            mapper
        )
    }

    fun findByAttemptId(attemptId: Long): DelayedManualReplyRow? = jdbc.query(
        "SELECT $COLUMNS FROM delayed_manual_reply WHERE send_attempt_id=:attempt",
        mapOf("attempt" to attemptId),
        mapper
    ).singleOrNull()

    fun findByOrigin(kind: DelayedManualReplyOriginKind, originId: Long): List<DelayedManualReplyRow> = jdbc.query(
        "SELECT $COLUMNS FROM delayed_manual_reply WHERE origin_kind=:kind AND origin_id=:origin ORDER BY id",
        mapOf("kind" to kind.name, "origin" to originId),
        mapper
    )

    /**
     * 到期扫描：只返回 id，不先领取；固定 LIMIT 20，按 due/id 升序。
     * 领取必须由 [claimDue] 在同一短事务内以 revision CAS 完成。
     */
    fun scanDueIds(nowMs: Long): List<Long> = jdbc.query(
        "SELECT id FROM delayed_manual_reply WHERE status='WAITING' AND due_at_ms<=:now " +
            "ORDER BY due_at_ms,id LIMIT $DUE_SCAN_LIMIT",
        mapOf("now" to nowMs),
        RowMapper { r: ResultSet, _: Int -> r.getLong("id") }
    )

    /** 显式行锁。调用方必须已在事务中，并按既有锁序（源草稿/审核→attempt→job）持有更早的锁。 */
    @Transactional(propagation = Propagation.MANDATORY)
    fun lockById(id: Long): DelayedManualReplyRow? {
        mandatory()
        return jdbc.query(
            "SELECT $COLUMNS FROM delayed_manual_reply WHERE id=:id FOR UPDATE",
            mapOf("id" to id),
            mapper
        ).singleOrNull()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun lockByAttemptId(attemptId: Long): DelayedManualReplyRow? {
        mandatory()
        return jdbc.query(
            "SELECT $COLUMNS FROM delayed_manual_reply WHERE send_attempt_id=:attempt FOR UPDATE",
            mapOf("attempt" to attemptId),
            mapper
        ).singleOrNull()
    }

    /**
     * 首次排队插入；revision 固定从 1 开始，状态固定 WAITING。
     *
     * `subject/text_body` 只在此处从快照派生（I-4），调用方不能单独传入；
     * `due_at_ms` 必须是 `created_at_ms + 60000`（I-1），且来源身份必须与快照逐项一致。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun insert(insert: DelayedManualReplyInsert): Long {
        mandatory()
        val snapshot = insert.snapshot
        DelayedManualReplySnapshotCodec.validate(snapshot)
        val payload = snapshot.payload
        require(insert.expertContactId == payload.contactId) {
            "insert.expertContactId must match snapshot.payload.contactId"
        }
        require(insert.accountCode == payload.accountCode) {
            "insert.accountCode must match snapshot.payload.accountCode"
        }
        require(insert.actorUsername == snapshot.evidence.operator) {
            "insert.actorUsername must match snapshot.evidence.operator"
        }
        require(insert.originKind.matches(snapshot.restore)) {
            "insert.originKind must match snapshot.restore"
        }
        require(insert.dueAtMs == insert.createdAtMs + DELAYED_MANUAL_REPLY_DELAY_MS) {
            "insert.dueAtMs must equal createdAtMs + $DELAYED_MANUAL_REPLY_DELAY_MS"
        }
        val snapshotJson = DelayedManualReplySnapshotCodec.encode(snapshot)
        val params = MapSqlParameterSource()
            .addValue("attempt", insert.sendAttemptId)
            .addValue("contact", insert.expertContactId)
            .addValue("account", insert.accountCode)
            .addValue("actor", insert.actorUsername)
            .addValue("originKind", insert.originKind.name)
            .addValue("originId", insert.originId)
            .addValue("originVersion", insert.originVersion)
            .addValue("dueAt", insert.dueAtMs)
            .addValue("subject", payload.subject)
            .addValue("text", payload.finalText)
            .addValue("snapshot", snapshotJson)
            .addValue("createdAt", insert.createdAtMs)
            .addValue("updatedAt", insert.updatedAtMs)
        val key = GeneratedKeyHolder()
        jdbc.update(
            """INSERT INTO delayed_manual_reply
                (send_attempt_id,expert_contact_id,account_code,actor_username,origin_kind,origin_id,origin_version,
                 revision,status,due_at_ms,subject,text_body,snapshot_json,created_at_ms,updated_at_ms)
                VALUES (:attempt,:contact,:account,:actor,:originKind,:originId,:originVersion,
                        1,'WAITING',:dueAt,:subject,:text,:snapshot,:createdAt,:updatedAt)""",
            params,
            key,
            arrayOf("id")
        )
        return requireNotNull(key.key).toLong()
    }

    private fun DelayedManualReplyOriginKind.matches(restore: DelayedReplyRestore): Boolean = when (this) {
        DelayedManualReplyOriginKind.DRAFT -> restore is DelayedDraftRestore
        DelayedManualReplyOriginKind.REVIEW -> restore is DelayedReviewRestore
        DelayedManualReplyOriginKind.LEGACY -> restore is DelayedLegacyRestore
    }

    /**
     * WAITING→CANCELLED 条件更新：revision 匹配且数据库时间严格早于 dueAt。
     * 与 [claimDue] 竞争时由同一修订号 CAS 决定唯一赢家（I-2）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun cancelWaiting(id: Long, expectedRevision: Long, nowMs: Long): Boolean {
        mandatory()
        return jdbc.update(
            """UPDATE delayed_manual_reply
                  SET status='CANCELLED',revision=revision+1,updated_at_ms=:now
                WHERE id=:id AND status='WAITING' AND revision=:revision AND :now<due_at_ms""",
            mapOf("id" to id, "revision" to expectedRevision, "now" to nowMs)
        ) == 1
    }

    /**
     * WAITING→SENDING 条件更新：revision 匹配且数据库时间达到 dueAt，写入 claim 令牌。
     * SMTP 只能在本方法返回 true 的事务提交后调用。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun claimDue(id: Long, expectedRevision: Long, dispatchToken: String, nowMs: Long): Boolean {
        mandatory()
        return jdbc.update(
            """UPDATE delayed_manual_reply
                  SET status='SENDING',revision=revision+1,dispatch_token=:token,claimed_at_ms=:now,updated_at_ms=:now
                WHERE id=:id AND status='WAITING' AND revision=:revision AND due_at_ms<=:now""",
            mapOf("id" to id, "revision" to expectedRevision, "token" to dispatchToken, "now" to nowMs)
        ) == 1
    }

    /**
     * SENDING→{SENT,FAILED,UNKNOWN} 条件更新，必须携带 claim 赢家令牌。
     * 旧令牌不能改写新代次；迟到结果由调用方按同一令牌收敛。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun completeSending(
        id: Long,
        dispatchToken: String,
        status: DelayedManualReplyStatus,
        publicErrorCode: String?,
        nowMs: Long
    ): Boolean {
        mandatory()
        require(status in COMPLETABLE_STATUSES) { "completeSending accepts SENT/FAILED/UNKNOWN only" }
        return jdbc.update(
            """UPDATE delayed_manual_reply
                  SET status=:status,public_error_code=:error,revision=revision+1,updated_at_ms=:now
                WHERE id=:id AND status='SENDING' AND dispatch_token=:token""",
            mapOf(
                "id" to id,
                "token" to dispatchToken,
                "status" to status.name,
                "error" to publicErrorCode,
                "now" to nowMs
            )
        ) == 1
    }

    private fun mandatory() {
        check(TransactionSynchronizationManager.isActualTransactionActive()) {
            "Delayed manual reply write operations require an existing transaction"
        }
    }

    private fun ResultSet.toRow(): DelayedManualReplyRow = DelayedManualReplyRow(
        id = getLong("id"),
        sendAttemptId = getLong("send_attempt_id"),
        expertContactId = getLong("expert_contact_id"),
        accountCode = getString("account_code"),
        actorUsername = getString("actor_username"),
        originKind = DelayedManualReplyOriginKind.valueOf(getString("origin_kind")),
        originId = getObject("origin_id")?.let { (it as Number).toLong() },
        originVersion = getObject("origin_version")?.let { (it as Number).toLong() },
        revision = getLong("revision"),
        status = DelayedManualReplyStatus.valueOf(getString("status")),
        dueAtMs = getLong("due_at_ms"),
        dispatchToken = getString("dispatch_token"),
        claimedAtMs = getObject("claimed_at_ms")?.let { (it as Number).toLong() },
        subject = getString("subject"),
        textBody = getString("text_body"),
        snapshotJson = getString("snapshot_json"),
        publicErrorCode = getString("public_error_code"),
        createdAtMs = getLong("created_at_ms"),
        updatedAtMs = getLong("updated_at_ms")
    )
}
