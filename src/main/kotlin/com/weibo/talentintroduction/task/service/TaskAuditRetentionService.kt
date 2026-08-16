package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.config.TaskRetentionProperties
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 任务审计 90 天保留清理（计划 B5）。
 *
 * - I3-1：`task_progress_log` 按 `created_at` 删，无 JOIN / EXISTS / task_execution_id 关联
 *   （tryStartWithToken 落的孤儿行 execution_id 为负值，关联删除漏得掉它们）。
 * - I3-2：分批删除，单批 `batchSize`（默认 2000），循环直到单批返回 0 或累计达
 *   `maxRowsPerRun`（默认 200000）。
 * - I3-3：`task_execution` 按 `started_at` 删（有 idx_te_started）；两表共用同一个
 *   `retentionDays` 算出的 cutoff，时区 `Asia/Shanghai`（与 TaskExecutionService.SHANGHAI 一致）。
 * - I3-4：先子表 `task_progress_log` 后主表 `task_execution`。
 * - I3-5：删除条件不含 `task_type` 排除——清理任务自身的审计行 90 天后同样被清，
 *   运行中的当前行由 `started_at < cutoff` 天然保护。
 * - I3-6：两表各自 try/catch，失败表数计入 [RetentionResult.failedTables]，
 *   使终态落到 SUCCESS / PARTIAL_SUCCESS / FAILED，异常不穿透出 purge()。
 */
@Service
class TaskAuditRetentionService(
    private val progressLogRepository: TaskProgressLogRepository,
    private val executionRepository: TaskExecutionRepository,
    private val props: TaskRetentionProperties
) {

    private val log = LoggerFactory.getLogger(TaskAuditRetentionService::class.java)

    fun purge(): RetentionResult {
        val cutoff = LocalDateTime.now(TaskExecutionService.SHANGHAI).minusDays(props.retentionDays)
        var progressDeleted = 0
        var executionDeleted = 0
        var failedTables = 0

        // I3-4：先子表后主表
        try {
            progressDeleted = purgeLoop { progressLogRepository.deleteOlderThan(cutoff, props.batchSize) }
        } catch (e: Exception) {
            failedTables++
            log.warn("purge task_progress_log failed: {}", e.message)
        }

        try {
            executionDeleted = purgeLoop { executionRepository.deleteOlderThan(cutoff, props.batchSize) }
        } catch (e: Exception) {
            failedTables++
            log.warn("purge task_execution failed: {}", e.message)
        }

        return RetentionResult(progressDeleted, executionDeleted, failedTables)
    }

    /** I3-2：循环删除直到单批返回 0 或累计达到单次运行上限。 */
    private fun purgeLoop(deleteBatch: () -> Int): Int {
        var deleted = 0
        while (true) {
            val batch = deleteBatch()
            deleted += batch
            if (batch == 0 || deleted >= props.maxRowsPerRun) return deleted
        }
    }
}

/**
 * 清理结果。实现 [TaskExecutionSummaryProvider]（I3-6）：使
 * `runAndRecordWithResult` 落正确的 success_count / failure_count / status，
 * 而不是依赖 `TaskResultSummary.from()` 反射（该反射名单 sent/replied/accepted/fetched/dispatched
 * 对本结果类一个都不命中，会得到恒 0/0）。
 *
 * 属性名同时是 result_summary JSON 的键（TaskExecutionSummaryExtractor 的
 * TASK_AUDIT_RETENTION 分支读取 progressLogDeleted / executionDeleted / failedTables）。
 */
data class RetentionResult(
    val progressLogDeleted: Int,
    val executionDeleted: Int,
    val failedTables: Int
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = progressLogDeleted + executionDeleted
    override val taskFailureCount: Int get() = failedTables
    override val taskFinalStatus: String? get() = when {
        failedTables == 2 -> "FAILED"
        failedTables == 1 -> "PARTIAL_SUCCESS"
        else -> "SUCCESS"
    }
}
