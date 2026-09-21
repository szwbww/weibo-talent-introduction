package com.weibo.talentintroduction.discovery.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * expert_academic_enrichment_job：一行 = 一个专家文档（真实 ES `_id`）的学术补全生命周期。
 * 这是补全任务的唯一生命周期存储（I-1），也是 08 worker 跨进程的唯一状态来源。
 *
 * 状态机（I-1/I-3）：
 * `PENDING -> RUNNING -> {SUCCEEDED | RETRY_WAIT | UNMATCHED | FAILED}`；
 * `RETRY_WAIT`（额度延期或可重试故障）到点后重新 `PENDING -> RUNNING`；
 * 崩溃留下的 `RUNNING` 在租约到期后重新可领；人工重试可显式把 `FAILED` 重开为 `PENDING`。
 *
 * 字段名是持久化契约（08 消费同一份字段名），辅助状态值只在本文件定义。
 *
 * - [attempts] 只计**故障尝试**：429 / OpenAlex 日额度延期不消耗（I-3）。
 * - [nextAttemptAt] 只对 `PENDING`/`RETRY_WAIT` 有意义（claim 谓词按它判定到期）；
 *   `SUCCEEDED`/`UNMATCHED`/`FAILED` 写入完成时刻，不构成未来调度。
 * - [leaseToken]/[leaseUntil] 只在 `RUNNING` 有值；claim/续租/完成都以 token CAS。
 * - [resultJson] 存基础/最近论文/三层更新结果，作为重试与审计依据（不是专家真实字段）。
 * - [lastError] 只存脱敏原因码，绝不写 API Key 或明文邮箱。
 * - [discoveryExecutionId] 可空，只作审计归属，不是外键。
 */
@Table("expert_academic_enrichment_job")
data class ExpertAcademicEnrichmentJob(
    @Id
    val id: Long? = null,
    /** 真实 ES `_id`（ORCID 或 `EMAIL-*` 主键），绝不是姓名/邮箱派生的键。 */
    val expertDocId: String,
    /** 入队来源（发现来源名或人工入口标记），仅审计用途。 */
    val source: String,
    /** 触发本次入队的 task_execution id；未知为 NULL。 */
    val discoveryExecutionId: Long? = null,
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val nextAttemptAt: LocalDateTime,
    val leaseToken: String? = null,
    val leaseUntil: LocalDateTime? = null,
    val lastError: String? = null,
    val resultJson: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
) {
    companion object {
        /** 待领取。 */
        const val STATUS_PENDING = "PENDING"

        /** 已被某个 worker 领取（持有 [leaseToken]）。 */
        const val STATUS_RUNNING = "RUNNING"

        /** 等待重试：额度延期或可重试故障，[nextAttemptAt] 到点才可再领。 */
        const val STATUS_RETRY_WAIT = "RETRY_WAIT"

        /** 学术事实已按真实 `_id` 写入现存层。 */
        const val STATUS_SUCCEEDED = "SUCCEEDED"

        /** 无可靠身份或查无作者：不是成功，也不是可重试故障。 */
        const val STATUS_UNMATCHED = "UNMATCHED"

        /** 故障尝试用尽：只有人工重试能显式重开。 */
        const val STATUS_FAILED = "FAILED"
    }
}
