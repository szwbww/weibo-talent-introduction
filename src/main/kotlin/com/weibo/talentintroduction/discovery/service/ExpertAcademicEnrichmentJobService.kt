package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 可恢复的补全任务存储的唯一入口（07）：08 的发现入队、worker 领取、人工重试全部经本 service。
 *
 * 关键不变量：
 * - **I-1 持久化唯一事实**：[enqueue] 以 `UNIQUE(expert_doc_id)` 幂等入队；同专家多次入队合并成一行，
 *   已成功且未超过 30 天的行不重入，`UNMATCHED` 在可靠身份可能变更后由再次入队重开；
 *   本 service 不向 ES 写任何状态字段。
 * - **I-2 租约和竞争**：[claimDue] 用「候选查询 + 事务内条件 UPDATE + `lease_token`」领取，租期
 *   [LEASE_MINUTES] 分钟；[renewLease] 供 worker 每批续租；[complete] 必须匹配 token，旧 token 的
 *   完成返回 `false` 且不写任何列。崩溃后过期 `RUNNING` 由 [claimDue] 谓词重新纳入候选。
 *   事务内绝不做外部 HTTP 调用（worker 在事务外调用补全核心）。
 * - **I-3 重试分类**：429 / OpenAlex 日额度延期只推迟 `next_attempt_at`，**不消耗** [ExpertAcademicEnrichmentJob.attempts]；
 *   网络/5xx 与层写失败按 [BACKOFF_MINUTES]（1m/5m/30m/2h）退避，故障尝试达到
 *   [MAX_FAILURE_ATTEMPTS] 次后 `FAILED`；无可靠身份或查无作者是 `UNMATCHED`，绝不伪造 `SUCCEEDED`；
 *   [reopenFailed] 是人工显式重开 `FAILED` 的唯一路径。
 *
 * 本类不持有调度、开关或进程内队列：c8 负责调度与默认关闭的开关，重启后任务只从本表恢复。
 */
@Service
class ExpertAcademicEnrichmentJobService(
    private val repository: ExpertAcademicEnrichmentJobRepository
) {

    /**
     * 幂等入队。已存在时：
     * - `PENDING` / `RUNNING` / `RETRY_WAIT` / `FAILED`：合并（保持原状态，不产生第二行）；
     * - `UNMATCHED`：重开为 `PENDING`（可靠身份可能已变更，重新判定）；
     * - `SUCCEEDED` 且未超过 30 天：不重入；超过 30 天才重开。
     */
    @Transactional
    fun enqueue(docId: String, source: String, executionId: Long?) {
        require(docId.isNotBlank()) { "docId is required" }
        require(source.isNotBlank()) { "source is required" }
        val now = LocalDateTime.now()
        repository.insertIfAbsent(docId, source, executionId, now)
        repository.reopenUnmatchedOrStaleSuccess(docId, now, now.minusDays(SUCCESS_FRESHNESS_DAYS))
    }

    /**
     * 领取至多 [limit] 条到期任务：到点的 `PENDING`/`RETRY_WAIT`，或租约已过期的 `RUNNING`（崩溃恢复）。
     * 每行一次 CAS，失败（已被其他 worker 领取/续租）的行不出现在结果里。
     */
    @Transactional
    fun claimDue(limit: Int, now: LocalDateTime): List<ExpertAcademicEnrichmentJob> {
        require(limit >= 1) { "limit must be at least 1" }
        val candidates = repository.findDueCandidates(limit, now)
        val claimed = ArrayList<ExpertAcademicEnrichmentJob>(candidates.size)
        for (candidate in candidates) {
            val id = candidate.id ?: continue
            val leaseToken = UUID.randomUUID().toString()
            val leaseUntil = now.plusMinutes(LEASE_MINUTES)
            if (repository.claimById(id, leaseToken, leaseUntil, now) == 1) {
                repository.findById(id).orElse(null)?.let(claimed::add)
            }
        }
        return claimed
    }

    /** worker 每批续租。租约已被重新领取或 token 不是当前持有者时返回 `false`。 */
    @Transactional
    fun renewLease(id: Long, leaseToken: String, now: LocalDateTime): Boolean =
        repository.renewLeaseById(id, leaseToken, now.plusMinutes(LEASE_MINUTES), now) == 1

    /**
     * 按 06 的逐人结果写终态/下一尝试，并清空租约。
     * 只有当前 `RUNNING` 且 `lease_token` 匹配时成功；否则返回 `false` 且不写任何列。
     */
    @Transactional
    fun complete(id: Long, leaseToken: String, outcome: ProfileEnrichmentOutcome): Boolean {
        val row = repository.findById(id).orElse(null) ?: return false
        if (row.status != ExpertAcademicEnrichmentJob.STATUS_RUNNING || row.leaseToken != leaseToken) {
            return false
        }
        val now = LocalDateTime.now()
        val completion = classify(row.attempts, outcome, now)
        return repository.completeWithToken(
            id = id,
            leaseToken = leaseToken,
            status = completion.status,
            attempts = completion.attempts,
            nextAttemptAt = completion.nextAttemptAt,
            lastError = completion.lastError,
            resultJson = resultJson(outcome),
            now = now
        ) == 1
    }

    /** 人工重试（I-3）：显式把 `FAILED` 重开为 `PENDING` 并重置故障尝试计数；其他状态返回 `false`。 */
    @Transactional
    fun reopenFailed(id: Long, now: LocalDateTime): Boolean = repository.reopenFailedById(id, now) == 1

    // ------------------------------------------------------------------
    // 结果 -> 状态/下一尝试（I-3）
    // ------------------------------------------------------------------

    private data class Completion(
        val status: String,
        val attempts: Int,
        val nextAttemptAt: LocalDateTime,
        val lastError: String?
    )

    private fun classify(attempts: Int, outcome: ProfileEnrichmentOutcome, now: LocalDateTime): Completion =
        when (outcome) {
            is ProfileEnrichmentOutcome.Success ->
                Completion(ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED, attempts, now, null)

            // 基础事实写入成功但仍有未完成部分：可按层重试，绝不能算成功。
            is ProfileEnrichmentOutcome.Partial -> transientFailure(
                attempts,
                now,
                if (outcome.layers.hasFailedLayer()) REASON_LAYER_UPDATE_FAILED else REASON_RECENT_WORKS_FAILED
            )

            // 日额度延期：未发请求，不消耗故障尝试，按真实重置时刻重排（系统时钟口径写入本库 DATETIME）。
            is ProfileEnrichmentOutcome.Deferred -> Completion(
                ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT,
                attempts,
                LocalDateTime.ofInstant(outcome.resetAt, ZoneId.systemDefault()),
                REASON_BUDGET_DEFERRED
            )

            is ProfileEnrichmentOutcome.RetryableError -> if (outcome.rateLimited) {
                // 供应商限流：按 retry-after 重排，同样不消耗故障尝试。
                Completion(
                    ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT,
                    attempts,
                    now.plus(rateLimitedDelay(outcome)),
                    REASON_RATE_LIMITED
                )
            } else {
                transientFailure(attempts, now, REASON_TRANSIENT_ERROR)
            }

            // 有可靠身份但查无作者 / 无可靠身份：都不是成功，也不是可重试故障。
            ProfileEnrichmentOutcome.NotFound -> unmatched(attempts, now, REASON_AUTHOR_NOT_FOUND)
            ProfileEnrichmentOutcome.NoId -> unmatched(attempts, now, REASON_NO_TRUSTED_IDENTITY)
        }

    private fun unmatched(attempts: Int, now: LocalDateTime, reason: String): Completion =
        Completion(ExpertAcademicEnrichmentJob.STATUS_UNMATCHED, attempts, now, reason)

    private fun transientFailure(attempts: Int, now: LocalDateTime, reason: String): Completion {
        val failedAttempts = attempts + 1
        return if (failedAttempts >= MAX_FAILURE_ATTEMPTS) {
            Completion(ExpertAcademicEnrichmentJob.STATUS_FAILED, failedAttempts, now, reason)
        } else {
            Completion(
                ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT,
                failedAttempts,
                now.plusMinutes(BACKOFF_MINUTES[failedAttempts - 1]),
                reason
            )
        }
    }

    /** 限流没有给 retry-after 时按最小退避（1 分钟）重排，不因此升格为故障尝试。 */
    private fun rateLimitedDelay(outcome: ProfileEnrichmentOutcome.RetryableError): Duration {
        val retryAfterMs = outcome.retryAfterMs ?: 0L
        return if (retryAfterMs > 0L) Duration.ofMillis(retryAfterMs) else Duration.ofMinutes(BACKOFF_MINUTES.first())
    }

    // ------------------------------------------------------------------
    // result_json：基础/最近论文/三层更新结果（重试与审计依据，不含 Key/邮箱/正文）
    // ------------------------------------------------------------------

    private fun resultJson(outcome: ProfileEnrichmentOutcome): String = when (outcome) {
        is ProfileEnrichmentOutcome.Success ->
            """{"outcome":"SUCCESS","layers":${layersJson(outcome.layers)}}"""

        is ProfileEnrichmentOutcome.Partial ->
            """{"outcome":"PARTIAL","recentWorksFailed":${outcome.recentWorksFailed},"layers":${layersJson(outcome.layers)}}"""

        is ProfileEnrichmentOutcome.Deferred ->
            """{"outcome":"DEFERRED","resetAt":"${outcome.resetAt}"}"""

        is ProfileEnrichmentOutcome.RetryableError -> {
            val retryAfterMs = outcome.retryAfterMs?.toString() ?: "null"
            """{"outcome":"RETRYABLE_ERROR","rateLimited":${outcome.rateLimited},"retryAfterMs":$retryAfterMs}"""
        }

        ProfileEnrichmentOutcome.NotFound -> """{"outcome":"NOT_FOUND"}"""
        ProfileEnrichmentOutcome.NoId -> """{"outcome":"NO_ID"}"""
    }

    private fun layersJson(layers: LayerUpdateResult): String =
        """{"raw":"${layers.raw}","candidate":"${layers.candidate}","application":"${layers.application}"}"""

    companion object {
        /** I-2：领取租期（分钟）；worker 每批续租。 */
        const val LEASE_MINUTES = 10L

        /** I-1：已成功任务的 30 天新鲜度窗口，窗口内不重入。 */
        const val SUCCESS_FRESHNESS_DAYS = 30L

        /** I-3：故障尝试上限，达到即 `FAILED`（人工重试可显式重开）。 */
        const val MAX_FAILURE_ATTEMPTS = 5

        /** I-3：网络/5xx 与层写失败的退避分钟序列。 */
        val BACKOFF_MINUTES = listOf(1L, 5L, 30L, 120L)

        /** last_error 只存脱敏原因码。 */
        const val REASON_TRANSIENT_ERROR = "TRANSIENT_ERROR"
        const val REASON_RATE_LIMITED = "RATE_LIMITED"
        const val REASON_BUDGET_DEFERRED = "BUDGET_DEFERRED"
        const val REASON_LAYER_UPDATE_FAILED = "LAYER_UPDATE_FAILED"
        const val REASON_RECENT_WORKS_FAILED = "RECENT_WORKS_FAILED"
        const val REASON_AUTHOR_NOT_FOUND = "AUTHOR_NOT_FOUND"
        const val REASON_NO_TRUSTED_IDENTITY = "NO_TRUSTED_IDENTITY"
    }
}
