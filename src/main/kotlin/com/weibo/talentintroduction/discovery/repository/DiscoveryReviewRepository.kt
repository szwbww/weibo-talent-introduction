package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDateTime

/** 02（I-4）：单个审核项应用结果。 */
enum class DiscoveryReviewApplyOutcome { APPLIED, ALREADY_APPLIED, STALE, SKIPPED, NOT_FOUND }

/** 02（I-2）：撤销结果。 */
enum class DiscoveryReviewRevokeOutcome { APPLIED, NOT_CURRENT, NOT_FOUND }

/**
 * 02（I-1/I-2/I-4）：`expert_discovery_admission` + `expert_discovery_review_item` 的唯一访问点。
 *
 * 全部参数化（`?` 绑定）；写路径自成事务。并发控制按固定锁序
 * `review_item`（按 id）→ `admission`（按 docId），版本用 `revision` CAS，绝不使用
 * 「最新自增 id」代替。
 */
@Repository
class DiscoveryReviewRepository(private val jdbcTemplate: JdbcTemplate) {

    // ── admission（当前结论/版本） ────────────────────────────────────────────

    /**
     * 幂等初始化当前结论行：已存在则**绝不覆盖**（INSERT IGNORE）。新行 revision=0，
     * 使首个 prepare 看到的版本与已存在行一致。
     */
    fun initializeAdmission(
        expertDocId: String,
        identityHash: String,
        decision: String,
        policyVersion: String?,
        now: LocalDateTime
    ) {
        jdbcTemplate.update(
            """
            INSERT IGNORE INTO expert_discovery_admission
                (expert_doc_id, identity_hash, decision, revision, decision_item_id,
                 policy_version, checked_at, updated_at)
            VALUES (?, ?, ?, 0, NULL, ?, ?, ?)
            """.trimIndent(),
            expertDocId, identityHash, decision, policyVersion, Timestamp.valueOf(now), Timestamp.valueOf(now)
        )
    }

    fun findAdmission(expertDocId: String): ExpertDiscoveryAdmission? =
        jdbcTemplate.query(
            "SELECT * FROM expert_discovery_admission WHERE expert_doc_id = ?",
            ADMISSION_MAPPER, expertDocId
        ).firstOrNull()

    /** 分批准入查询：一次最多 500 个 docId，避免整页 N 次查询。 */
    fun findAdmissions(expertDocIds: Collection<String>): List<ExpertDiscoveryAdmission> {
        if (expertDocIds.isEmpty()) return emptyList()
        val result = mutableListOf<ExpertDiscoveryAdmission>()
        expertDocIds.toList().chunked(BATCH_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(", ") { "?" }
            result += jdbcTemplate.query(
                "SELECT * FROM expert_discovery_admission WHERE expert_doc_id IN ($placeholders)",
                ADMISSION_MAPPER, *chunk.toTypedArray()
            )
        }
        return result
    }

    // ── review_item（快照/决策/历史） ─────────────────────────────────────────

    fun insertItem(
        batchKey: String,
        expertDocId: String,
        sourceLevel: String,
        identityHash: String,
        snapshotHash: String,
        expectedRevision: Long,
        action: String,
        state: String,
        snapshotJson: String,
        reasonSnapshotJson: String?,
        actor: String,
        note: String?,
        previousItemId: Long?,
        executionId: Long?,
        now: LocalDateTime
    ): Long {
        val keyHolder = GeneratedKeyHolder()
        jdbcTemplate.update({ connection ->
            val ps = connection.prepareStatement(
                """
                INSERT INTO expert_discovery_review_item
                    (batch_key, expert_doc_id, source_level, identity_hash, snapshot_hash,
                     expected_revision, action, state, snapshot_json, reason_snapshot_json,
                     actor, note, previous_item_id, execution_id, error_code,
                     created_at, confirmed_at, applied_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL, NULL)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS
            )
            ps.setString(1, batchKey)
            ps.setString(2, expertDocId)
            ps.setString(3, sourceLevel)
            ps.setString(4, identityHash)
            ps.setString(5, snapshotHash)
            ps.setLong(6, expectedRevision)
            ps.setString(7, action)
            ps.setString(8, state)
            ps.setString(9, snapshotJson)
            ps.setString(10, reasonSnapshotJson)
            ps.setString(11, actor)
            ps.setString(12, note)
            if (previousItemId != null) ps.setLong(13, previousItemId) else ps.setNull(13, java.sql.Types.BIGINT)
            if (executionId != null) ps.setLong(14, executionId) else ps.setNull(14, java.sql.Types.BIGINT)
            ps.setTimestamp(15, Timestamp.valueOf(now))
            ps
        }, keyHolder)
        return keyHolder.key!!.toLong()
    }

    fun findItem(id: Long): ExpertDiscoveryReviewItem? =
        jdbcTemplate.query("SELECT * FROM expert_discovery_review_item WHERE id = ?", ITEM_MAPPER, id).firstOrNull()

    /** 分批按 id 取项（列表页一次取回所有当前有效决策项，避免逐行查询）。 */
    fun findItemsByIds(ids: Collection<Long>): List<ExpertDiscoveryReviewItem> {
        if (ids.isEmpty()) return emptyList()
        val result = mutableListOf<ExpertDiscoveryReviewItem>()
        ids.toList().distinct().chunked(BATCH_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(", ") { "?" }
            result += jdbcTemplate.query(
                "SELECT * FROM expert_discovery_review_item WHERE id IN ($placeholders)",
                ITEM_MAPPER, *chunk.toTypedArray()
            )
        }
        return result
    }

    fun findItemsByBatch(batchKey: String): List<ExpertDiscoveryReviewItem> =
        jdbcTemplate.query(
            "SELECT * FROM expert_discovery_review_item WHERE batch_key = ? ORDER BY id ASC",
            ITEM_MAPPER, batchKey
        )

    fun findHistory(expertDocId: String, limit: Int): List<ExpertDiscoveryReviewItem> =
        jdbcTemplate.query(
            "SELECT * FROM expert_discovery_review_item WHERE expert_doc_id = ? ORDER BY id DESC LIMIT ?",
            ITEM_MAPPER, expertDocId, limit
        )

    fun batchStateCounts(batchKey: String): Map<String, Int> =
        jdbcTemplate.query(
            "SELECT state, COUNT(*) AS c FROM expert_discovery_review_item WHERE batch_key = ? GROUP BY state",
            { rs, _ -> rs.getString("state") to rs.getInt("c") }, batchKey
        ).toMap()

    // ── 应用（单事务 + 行锁 + CAS） ───────────────────────────────────────────

    /**
     * 应用一个 STAGED/READY 项：同一事务内推进状态并更新当前结论。
     * 提交前比较 `admission.revision`（CAS）与真实身份：任一不符即记 `STALE`，不覆盖当前结论。
     * 已有 `APPLIED` 项幂等返回 [DiscoveryReviewApplyOutcome.ALREADY_APPLIED]。
     */
    @Transactional
    fun applyItem(
        itemId: Long,
        currentIdentityHash: String,
        decision: String,
        policyVersion: String?,
        now: LocalDateTime
    ): DiscoveryReviewApplyOutcome {
        val item = lockItem(itemId) ?: return DiscoveryReviewApplyOutcome.NOT_FOUND
        if (item.state == STATE_APPLIED) return DiscoveryReviewApplyOutcome.ALREADY_APPLIED
        if (item.state != STATE_STAGED && item.state != STATE_READY) return DiscoveryReviewApplyOutcome.SKIPPED
        updateItemState(itemId, STATE_READY)
        val admission = lockAdmission(item.expertDocId)
        if (admission == null) {
            markStale(itemId, "ADMISSION_MISSING", now)
            return DiscoveryReviewApplyOutcome.STALE
        }
        if (admission.revision != item.expectedRevision || admission.identityHash != currentIdentityHash) {
            markStale(itemId, "REVISION_OR_IDENTITY_CHANGED", now)
            return DiscoveryReviewApplyOutcome.STALE
        }
        updateItemState(itemId, STATE_APPLYING)
        val newRevision = admission.revision + 1
        val affected = jdbcTemplate.update(
            """
            UPDATE expert_discovery_admission
            SET identity_hash = ?, decision = ?, revision = ?, decision_item_id = ?,
                policy_version = ?, checked_at = ?, updated_at = ?
            WHERE expert_doc_id = ? AND revision = ? AND identity_hash = ?
            """.trimIndent(),
            currentIdentityHash, decision, newRevision, itemId,
            policyVersion, Timestamp.valueOf(now), Timestamp.valueOf(now),
            item.expertDocId, admission.revision, currentIdentityHash
        )
        if (affected != 1) {
            markStale(itemId, "CAS_CONFLICT", now)
            return DiscoveryReviewApplyOutcome.STALE
        }
        markItemApplied(itemId, now)
        return DiscoveryReviewApplyOutcome.APPLIED
    }

    /**
     * 撤销当前仍然有效的人工决策：同一事务内锁定原项与当前结论，写入一条 `REVOKE` 项，
     * 并用撤销时重新运行的自动校验结论覆盖当前结论。原项不再是当前有效决策 → `NOT_CURRENT`。
     */
    @Transactional
    fun revokeCurrent(
        currentItemId: Long,
        actor: String,
        note: String?,
        newDecision: String,
        currentIdentityHash: String,
        policyVersion: String?,
        sourceLevel: String,
        snapshotHash: String,
        snapshotJson: String,
        reasonSnapshotJson: String?,
        now: LocalDateTime
    ): Pair<DiscoveryReviewRevokeOutcome, Long?> {
        val item = lockItem(currentItemId) ?: return DiscoveryReviewRevokeOutcome.NOT_FOUND to null
        if (item.state != STATE_APPLIED || item.action == ACTION_REVOKE) {
            return DiscoveryReviewRevokeOutcome.NOT_CURRENT to null
        }
        val admission = lockAdmission(item.expertDocId)
            ?: return DiscoveryReviewRevokeOutcome.NOT_CURRENT to null
        if (admission.decisionItemId != currentItemId) return DiscoveryReviewRevokeOutcome.NOT_CURRENT to null

        val newRevision = admission.revision + 1
        val newItemId = insertItem(
            batchKey = REVOKE_BATCH_PREFIX + currentItemId,
            expertDocId = item.expertDocId,
            sourceLevel = sourceLevel,
            identityHash = currentIdentityHash,
            snapshotHash = snapshotHash,
            expectedRevision = admission.revision,
            action = ACTION_REVOKE,
            state = STATE_APPLIED,
            snapshotJson = snapshotJson,
            reasonSnapshotJson = reasonSnapshotJson,
            actor = actor,
            note = note,
            previousItemId = currentItemId,
            executionId = null,
            now = now
        )
        val affected = jdbcTemplate.update(
            """
            UPDATE expert_discovery_admission
            SET identity_hash = ?, decision = ?, revision = ?, decision_item_id = ?,
                policy_version = ?, checked_at = ?, updated_at = ?
            WHERE expert_doc_id = ? AND revision = ? AND identity_hash = ?
            """.trimIndent(),
            currentIdentityHash, newDecision, newRevision, newItemId,
            policyVersion, Timestamp.valueOf(now), Timestamp.valueOf(now),
            item.expertDocId, admission.revision, admission.identityHash
        )
        if (affected != 1) throw IllegalStateException("撤销时当前结论版本已变化，请重试")
        return DiscoveryReviewRevokeOutcome.APPLIED to newItemId
    }

    fun markItemFailed(itemId: Long, errorCode: String, now: LocalDateTime): Unit =
        markStale(itemId, errorCode, now)

    // ── 内部 ─────────────────────────────────────────────────────────────────

    private fun lockItem(id: Long): ExpertDiscoveryReviewItem? =
        jdbcTemplate.query(
            "SELECT * FROM expert_discovery_review_item WHERE id = ? FOR UPDATE", ITEM_MAPPER, id
        ).firstOrNull()

    private fun lockAdmission(expertDocId: String): ExpertDiscoveryAdmission? =
        jdbcTemplate.query(
            "SELECT * FROM expert_discovery_admission WHERE expert_doc_id = ? FOR UPDATE",
            ADMISSION_MAPPER, expertDocId
        ).firstOrNull()

    private fun updateItemState(itemId: Long, state: String) {
        jdbcTemplate.update(
            "UPDATE expert_discovery_review_item SET state = ? WHERE id = ?", state, itemId
        )
    }

    private fun markItemApplied(itemId: Long, now: LocalDateTime) {
        jdbcTemplate.update(
            """
            UPDATE expert_discovery_review_item
            SET state = ?, confirmed_at = ?, applied_at = ?, error_code = NULL
            WHERE id = ?
            """.trimIndent(),
            STATE_APPLIED, Timestamp.valueOf(now), Timestamp.valueOf(now), itemId
        )
    }

    private fun markStale(itemId: Long, errorCode: String, now: LocalDateTime) {
        jdbcTemplate.update(
            """
            UPDATE expert_discovery_review_item
            SET state = ?, error_code = ?, confirmed_at = ?
            WHERE id = ?
            """.trimIndent(),
            STATE_STALE, errorCode, Timestamp.valueOf(now), itemId
        )
    }

    companion object {
        const val BATCH_SIZE = 500
        const val STATE_STAGED = "STAGED"
        const val STATE_READY = "READY"
        const val STATE_APPLYING = "APPLYING"
        const val STATE_APPLIED = "APPLIED"
        const val STATE_STALE = "STALE"
        const val STATE_FAILED = "FAILED"
        const val STATE_CANCELLED = "CANCELLED"
        const val ACTION_REVOKE = "REVOKE"
        const val REVOKE_BATCH_PREFIX = "revoke-"

        private val ADMISSION_MAPPER = RowMapper<ExpertDiscoveryAdmission> { rs: ResultSet, _: Int ->
            ExpertDiscoveryAdmission(
                expertDocId = rs.getString("expert_doc_id"),
                identityHash = rs.getString("identity_hash"),
                decision = rs.getString("decision"),
                revision = rs.getLong("revision"),
                decisionItemId = rs.getLong("decision_item_id").takeUnless { rs.wasNull() },
                policyVersion = rs.getString("policy_version"),
                checkedAt = rs.getTimestamp("checked_at")?.toLocalDateTime(),
                updatedAt = rs.getTimestamp("updated_at")!!.toLocalDateTime()
            )
        }

        private val ITEM_MAPPER = RowMapper<ExpertDiscoveryReviewItem> { rs: ResultSet, _: Int ->
            ExpertDiscoveryReviewItem(
                id = rs.getLong("id"),
                batchKey = rs.getString("batch_key"),
                expertDocId = rs.getString("expert_doc_id"),
                sourceLevel = rs.getString("source_level"),
                identityHash = rs.getString("identity_hash"),
                snapshotHash = rs.getString("snapshot_hash"),
                expectedRevision = rs.getLong("expected_revision"),
                action = rs.getString("action"),
                state = rs.getString("state"),
                snapshotJson = rs.getString("snapshot_json"),
                reasonSnapshotJson = rs.getString("reason_snapshot_json"),
                actor = rs.getString("actor"),
                note = rs.getString("note"),
                previousItemId = rs.getLong("previous_item_id").takeUnless { rs.wasNull() },
                executionId = rs.getLong("execution_id").takeUnless { rs.wasNull() },
                errorCode = rs.getString("error_code"),
                createdAt = rs.getTimestamp("created_at")!!.toLocalDateTime(),
                confirmedAt = rs.getTimestamp("confirmed_at")?.toLocalDateTime(),
                appliedAt = rs.getTimestamp("applied_at")?.toLocalDateTime()
            )
        }
    }
}
