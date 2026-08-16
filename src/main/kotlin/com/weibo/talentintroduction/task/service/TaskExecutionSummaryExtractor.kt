package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskTypeCatalog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 单次执行聚合指标的共享取数器（I1-3 / I1-4 / M-2 落地）。
 *
 * 从 [TaskProgressController] 整体迁入 `parseResultSummary` / `fallbackFromLog` /
 * `detectWasCancelled`（原私有方法，`when (taskType)` 的 6 分支键改为
 * [TaskTypeCatalog] 的 `summaryRule`）。取数顺序固定为 I1-3 的三级优先级：
 *
 * ① `resultSummary`（终态权威，block 返回后才写入）→
 * ② 该 executionId 最新一条 `task_progress_log.detailsJson` + 该行 `processedCount` →
 * ③ 存量 `success_count` / `failure_count`。
 *
 * 三级都无有效值时返回全 0（列表页由 catalog 的 `metricLabel = null` 决定渲染
 * 「— 无统计」，见 I1-2）。
 */
@Component
class TaskExecutionSummaryExtractor(
    private val progressLogRepository: TaskProgressLogRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(TaskExecutionSummaryExtractor::class.java)

    /** I1-3 三级取数入口。 */
    fun extract(taskType: String, execution: TaskExecution): ExecutionTotals {
        val fromSummary = parseResultSummary(taskType, execution.resultSummary, execution.id)
        if (fromSummary != null) return fromSummary
        val fromLog = fallbackFromLog(execution.id, taskType)
        if (fromLog != null) return fromLog
        val success = execution.successCount.toLong()
        val failure = execution.failureCount.toLong()
        return ExecutionTotals(
            totalProcessed = success + failure,
            totalPassed = success,
            totalRejected = failure
        )
    }

    /** 读 `root.wasCancelled`，用于把终态改判为 `CANCELLED`。 */
    fun detectWasCancelled(resultSummary: String?): Boolean {
        if (resultSummary.isNullOrBlank()) return false
        return try {
            val root = objectMapper.readTree(resultSummary)
            root.path("wasCancelled").asBoolean(false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 第 ① 级：解析 `resultSummary`。规则由 catalog 的 `summaryRule` 决定（I1-4，
     * 不再分散成多处 `when (taskType)`）。返回 null 仅当 `resultSummary` 为 null/空白
     * （此时走第 ② 级）；解析异常或未知规则时返回全 0（终态权威，不回退到日志）。
     */
    private fun parseResultSummary(taskType: String, resultSummary: String?, executionId: Long?): ExecutionTotals? {
        if (resultSummary.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(resultSummary)
            val stats = root.path("stats")
            when (TaskTypeCatalog.byCode(taskType)?.summaryRule) {
                "EXPERT_REVALIDATION" -> ExecutionTotals(
                    totalProcessed = stats.path("total").asLong(0),
                    totalPassed = stats.path("passed").asLong(0),
                    totalRejected = stats.path("demoted").asLong(0)
                )
                "RAW_PROMOTION_SCAN" -> ExecutionTotals(
                    totalProcessed = stats.path("total").asLong(0),
                    totalPassed = stats.path("promoted").asLong(0),
                    totalRejected = stats.path("filtered").asLong(0) + stats.path("emailRejected").asLong(0)
                )
                "EXPERT_DISCOVERY" -> {
                    val totalPapers = stats.path("totalPapers").asLong(0)
                    val indexed = stats.path("indexed").asLong(0)
                    val summaryText = root.path("summaryText").asText().takeIf { it.isNotBlank() }
                        ?: stats.path("summaryText").asText().takeIf { it.isNotBlank() }
                    ExecutionTotals(
                        totalProcessed = totalPapers,
                        totalPassed = indexed,
                        totalRejected = (totalPapers - indexed).coerceAtLeast(0),
                        summaryText = summaryText
                    )
                }
                "MANUAL_INITIAL_OUTREACH" -> ExecutionTotals(
                    totalProcessed = (root.path("total").asLong(0) - root.path("remaining").asLong(0))
                        .coerceAtLeast(0),
                    totalPassed = root.path("sent").asLong(0),
                    totalRejected = root.path("failed").asLong(0)
                )
                "CHECK_REPLIES" -> ExecutionTotals(
                    totalProcessed = root.path("totalAccountsToPoll").asLong(0),
                    totalPassed = root.path("successAccountCount").asLong(0),
                    totalRejected = root.path("failedAccountCount").asLong(0)
                )
                "EXPERT_ENRICHMENT" -> {
                    val enriched = root.path("enriched").asLong(0)
                    val failed = root.path("failed").asLong(0)
                    ExecutionTotals(
                        totalProcessed = enriched + failed,
                        totalPassed = enriched,
                        totalRejected = failed
                    )
                }
                else -> ExecutionTotals()
            }
        } catch (e: Exception) {
            log.warn("Failed to parse resultSummary for executionId={} taskType={}: {}", executionId, taskType, e.message)
            ExecutionTotals()
        }
    }

    /**
     * 第 ② 级：读该 executionId 最新一条 `task_progress_log` 的 `detailsJson`。
     * 字段名与第 ① 级一致但不带 `stats` 前缀；`totalProcessed` 统一取该行的 `processedCount`。
     * 返回 null 仅当 executionId 为空或没有日志（此时走第 ③ 级）。
     */
    private fun fallbackFromLog(executionId: Long?, taskType: String): ExecutionTotals? {
        if (executionId == null) return null
        val latestLog = progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(executionId)
            ?: return null
        val detailsJson = latestLog.detailsJson
        val passed: Long
        val rejected: Long
        if (!detailsJson.isNullOrBlank()) {
            try {
                val details = objectMapper.readTree(detailsJson)
                when (TaskTypeCatalog.byCode(taskType)?.summaryRule) {
                    "EXPERT_REVALIDATION" -> {
                        passed = details.path("passed").asLong(0)
                        rejected = details.path("demoted").asLong(0)
                    }
                    "RAW_PROMOTION_SCAN" -> {
                        passed = details.path("promoted").asLong(0)
                        rejected = details.path("filtered").asLong(0) + details.path("emailRejected").asLong(0)
                    }
                    "EXPERT_DISCOVERY" -> {
                        passed = details.path("indexed").asLong(0)
                        rejected = 0
                    }
                    "MANUAL_INITIAL_OUTREACH" -> {
                        passed = details.path("sent").asLong(0)
                        rejected = details.path("failed").asLong(0)
                    }
                    "CHECK_REPLIES" -> {
                        passed = details.path("successAccountCount").asLong(0)
                        rejected = details.path("failedAccountCount").asLong(0)
                    }
                    "EXPERT_ENRICHMENT" -> {
                        passed = details.path("enriched").asLong(0)
                        rejected = details.path("failed").asLong(0)
                    }
                    else -> {
                        passed = 0; rejected = 0
                    }
                }
            } catch (e: Exception) {
                return ExecutionTotals(
                    totalProcessed = latestLog.processedCount,
                    totalPassed = 0,
                    totalRejected = 0
                )
            }
        } else {
            passed = 0; rejected = 0
        }
        return ExecutionTotals(
            totalProcessed = latestLog.processedCount,
            totalPassed = passed,
            totalRejected = rejected
        )
    }
}

/**
 * 聚合指标结果。从 [TaskProgressController] 的 private 内部类提升为公开 data class
 * （字段不变：totalProcessed / totalPassed / totalRejected / summaryText），供
 * [TaskExecutionController] 的 `/{id}/detail` 与进度弹窗共用。
 */
data class ExecutionTotals(
    val totalProcessed: Long = 0,
    val totalPassed: Long = 0,
    val totalRejected: Long = 0,
    val summaryText: String? = null
)
