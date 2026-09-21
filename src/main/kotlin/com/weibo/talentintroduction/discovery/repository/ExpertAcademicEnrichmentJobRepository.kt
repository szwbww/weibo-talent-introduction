package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

/**
 * expert_academic_enrichment_job 的全部状态写路径（I-1/I-2/I-3）。
 *
 * 写入语义全部落在条件 UPDATE 上，进程崩溃/并发领取都不需要额外锁：
 *
 * - [insertIfAbsent]：唯一键幂等入队。`UNIQUE(expert_doc_id)` 保证同专家永远一行；
 *   已存在时**不改写**任何列（`id = id` 是空操作）——合并/重开由 [reopenUnmatchedOrStaleSuccess] 决定。
 * - [reopenUnmatchedOrStaleSuccess]：只在「UNMATCHED（可靠身份可能已变更）」或
 *   「SUCCEEDED 且已超过 30 天新鲜度」时把行重开为 PENDING；PENDING/RUNNING/RETRY_WAIT/FAILED
 *   一律合并（RUNNING 保持他人租约，FAILED 只允许人工显式重开）。
 * - [findDueCandidates] + [claimById]：先取候选，再逐行 CAS 领取。claim 谓词同时接受
 *   「到点的 PENDING/RETRY_WAIT」与「租约已过期的 RUNNING」（崩溃恢复），并写 `lease_token`；
 *   同一行被两个事务同时领取时只有一个能更新成功，因此不依赖 `SKIP LOCKED`（I-2）。
 * - [renewLeaseById]/[completeWithToken]：必须匹配 `lease_token` 且仍是 RUNNING；
 *   旧 worker 的续租/完成影响行数为 0，绝不覆盖新尝试。
 * - [reopenFailedById]：人工重试显式重开 FAILED（I-3），重置故障尝试计数与租约。
 *
 * 排序 `(next_attempt_at, id)` 与队列索引 `idx_expert_academic_enrichment_job_due` 同形；
 * 多 worker 并发领取时按同序加行锁，不会交叉死锁。
 */
interface ExpertAcademicEnrichmentJobRepository : CrudRepository<ExpertAcademicEnrichmentJob, Long> {

    @Query("SELECT * FROM expert_academic_enrichment_job WHERE expert_doc_id = :docId")
    fun findByExpertDocId(docId: String): ExpertAcademicEnrichmentJob?

    /**
     * 新任务插入为 `PENDING`、`attempts = 0`、`next_attempt_at = :now`（立即可领）。
     * 唯一键冲突时不改任何列，返回 0。
     */
    @Modifying
    @Query(
        """
        INSERT INTO expert_academic_enrichment_job
            (expert_doc_id, source, discovery_execution_id, status, attempts, next_attempt_at, created_at, updated_at)
        VALUES (:docId, :source, :executionId, 'PENDING', 0, :now, :now, :now)
        ON DUPLICATE KEY UPDATE id = id
        """
    )
    fun insertIfAbsent(
        docId: String,
        source: String,
        executionId: Long?,
        now: LocalDateTime
    ): Int

    /**
     * 重开条件（I-1）：`UNMATCHED`，或 `SUCCEEDED` 且 `updated_at <= :freshSince`（30 天新鲜度窗口）。
     * 重开清空租约与上次错误，保留 `result_json`（审计依据）；`FAILED` 不在谓词内。
     */
    @Modifying
    @Query(
        """
        UPDATE expert_academic_enrichment_job
           SET status = 'PENDING',
               attempts = 0,
               next_attempt_at = :now,
               lease_token = NULL,
               lease_until = NULL,
               last_error = NULL,
               updated_at = :now
         WHERE expert_doc_id = :docId
           AND (status = 'UNMATCHED'
                OR (status = 'SUCCEEDED' AND updated_at <= :freshSince))
        """
    )
    fun reopenUnmatchedOrStaleSuccess(
        docId: String,
        now: LocalDateTime,
        freshSince: LocalDateTime
    ): Int

    /** 到期候选：到点的 PENDING/RETRY_WAIT，或租约已过期的 RUNNING（崩溃恢复）。 */
    @Query(
        """
        SELECT * FROM expert_academic_enrichment_job
         WHERE ((status = 'PENDING' OR status = 'RETRY_WAIT') AND next_attempt_at <= :now)
            OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= :now))
         ORDER BY next_attempt_at, id
         LIMIT :limit
        """
    )
    fun findDueCandidates(limit: Int, now: LocalDateTime): List<ExpertAcademicEnrichmentJob>

    /**
     * CAS 领取：谓词必须与 [findDueCandidates] 一致，且条件在更新时重新求值。
     * 行已被其他事务领取/续租时返回 0。
     */
    @Modifying
    @Query(
        """
        UPDATE expert_academic_enrichment_job
           SET status = 'RUNNING',
               lease_token = :leaseToken,
               lease_until = :leaseUntil,
               updated_at = :now
         WHERE id = :id
           AND (((status = 'PENDING' OR status = 'RETRY_WAIT') AND next_attempt_at <= :now)
                OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= :now)))
        """
    )
    fun claimById(
        id: Long,
        leaseToken: String,
        leaseUntil: LocalDateTime,
        now: LocalDateTime
    ): Int

    /** 续租：只有当前 token 的持有者能延长租约；租约已过期或已被重新领取返回 0。 */
    @Modifying
    @Query(
        """
        UPDATE expert_academic_enrichment_job
           SET lease_until = :leaseUntil,
               updated_at = :now
         WHERE id = :id
           AND status = 'RUNNING'
           AND lease_token = :leaseToken
        """
    )
    fun renewLeaseById(
        id: Long,
        leaseToken: String,
        leaseUntil: LocalDateTime,
        now: LocalDateTime
    ): Int

    /**
     * CAS 完成：匹配 `lease_token` 且仍是 RUNNING 才写终态/下一尝试。
     * 写入即清空租约（避免迟到 worker 影响新尝试）；旧 token 返回 0。
     */
    @Modifying
    @Query(
        """
        UPDATE expert_academic_enrichment_job
           SET status = :status,
               attempts = :attempts,
               next_attempt_at = :nextAttemptAt,
               lease_token = NULL,
               lease_until = NULL,
               last_error = :lastError,
               result_json = :resultJson,
               updated_at = :now
         WHERE id = :id
           AND status = 'RUNNING'
           AND lease_token = :leaseToken
        """
    )
    fun completeWithToken(
        id: Long,
        leaseToken: String,
        status: String,
        attempts: Int,
        nextAttemptAt: LocalDateTime,
        lastError: String?,
        resultJson: String?,
        now: LocalDateTime
    ): Int

    /** 人工重试（I-3）：只有 FAILED 可显式重开为 PENDING，故障尝试计数归零。 */
    @Modifying
    @Query(
        """
        UPDATE expert_academic_enrichment_job
           SET status = 'PENDING',
               attempts = 0,
               next_attempt_at = :now,
               lease_token = NULL,
               lease_until = NULL,
               last_error = NULL,
               updated_at = :now
         WHERE id = :id
           AND status = 'FAILED'
        """
    )
    fun reopenFailedById(id: Long, now: LocalDateTime): Int
}
