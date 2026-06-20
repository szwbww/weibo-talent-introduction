package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/task-progress")
class TaskProgressController(
    private val progressStore: TaskProgressStore,
    private val progressLogRepository: TaskProgressLogRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(TaskProgressController::class.java)
    private val allowedTaskTypes = setOf("EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY", "MANUAL_INITIAL_OUTREACH", "CHECK_REPLIES")

    @GetMapping("/{taskType}")
    fun getProgress(@PathVariable taskType: String): ResponseEntity<TaskProgress> {
        val progress = progressStore.get(taskType) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(progress)
    }

    @PostMapping("/{taskType}/cancel")
    fun cancelTask(@PathVariable taskType: String): ResponseEntity<Map<String, String>> {
        if (!progressStore.requestCancel(taskType)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "没有正在运行的任务或取消请求已处理"))
        }
        return ResponseEntity.ok(mapOf("message" to "已发送取消请求，任务将在当前批次结束后停止"))
    }

    @GetMapping("/{taskType}/logs")
    fun getProgressLogs(
        @PathVariable taskType: String,
        @RequestParam(required = false) executionId: Long?,
        @RequestParam(defaultValue = "false") batchOnly: Boolean
    ): List<TaskProgressLog> {
        val targetExecutionId = executionId
            ?: progressStore.getCurrentExecutionId(taskType)
            ?: run {
                val latestLog = progressLogRepository.findTopByTaskTypeOrderByIdDesc(taskType)
                latestLog?.taskExecutionId
            }
            ?: return emptyList()
        val logs = progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(targetExecutionId)
        if (!batchOnly) return logs
        // 同一批次号(batchNumber)可能因多次进度更新落多条日志，批次明细去重：
        // 每个 batchNumber 仅保留最新一条(id 最大)，按批次号升序返回，避免明细表出现重复行。
        return logs.filter { it.batchNumber > 0 }
            .groupBy { it.batchNumber }
            .map { (_, group) -> group.last() }
            .sortedBy { it.batchNumber }
    }

    @GetMapping("/{taskType}/executions")
    fun getExecutions(
        @PathVariable taskType: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): ResponseEntity<List<TaskRunSummaryResponse>> {
        if (taskType !in allowedTaskTypes) {
            return ResponseEntity.badRequest().build()
        }
        val clampedLimit = limit.coerceIn(1, 50)
        val executions = taskExecutionRepository.findRecentByTaskType(taskType, clampedLimit)
        val responses = executions.map { exec ->
            val totals = parseResultSummary(taskType, exec.resultSummary, exec.id)
            val wasCancelled = exec.resultSummary?.let { detectWasCancelled(it) } ?: false
            val status = when {
                exec.status == "RUNNING" || exec.status == "CANCELLING" -> exec.status
                wasCancelled -> "CANCELLED"
                else -> exec.status
            }
            val durationSeconds = if (exec.finishedAt != null) {
                Duration.between(exec.startedAt, exec.finishedAt).seconds
            } else null
            TaskRunSummaryResponse(
                executionId = exec.id,
                taskType = exec.taskType,
                triggerType = exec.triggerType,
                status = status,
                startedAt = exec.startedAt.format(DATE_FMT),
                finishedAt = exec.finishedAt?.format(DATE_FMT),
                durationSeconds = durationSeconds,
                totalProcessed = totals.totalProcessed,
                totalPassed = totals.totalPassed,
                totalRejected = totals.totalRejected,
                summaryText = totals.summaryText,
                errorMessage = exec.errorMessage
            )
        }
        return ResponseEntity.ok(responses)
    }

    private data class ExecutionTotals(
        val totalProcessed: Long = 0,
        val totalPassed: Long = 0,
        val totalRejected: Long = 0,
        val summaryText: String? = null
    )

    private fun parseResultSummary(taskType: String, resultSummary: String?, executionId: Long?): ExecutionTotals {
        if (!resultSummary.isNullOrBlank()) {
            try {
                val root = objectMapper.readTree(resultSummary)
                val stats = root.path("stats")
                return when (taskType) {
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
                        totalProcessed = stats.path("total").asLong(0),
                        totalPassed = stats.path("sent").asLong(0),
                        totalRejected = stats.path("failed").asLong(0)
                    )
                    "CHECK_REPLIES" -> ExecutionTotals(
                        totalProcessed = root.path("totalAccountsToPoll").asLong(0),
                        totalPassed = root.path("successAccountCount").asLong(0),
                        totalRejected = root.path("failedAccountCount").asLong(0)
                    )
                    else -> ExecutionTotals()
                }
            } catch (e: Exception) {
                log.warn("Failed to parse resultSummary for executionId={} taskType={}: {}", executionId, taskType, e.message)
                return ExecutionTotals()
            }
        }
        return fallbackFromLog(executionId, taskType)
    }

    private fun fallbackFromLog(executionId: Long?, taskType: String): ExecutionTotals {
        if (executionId == null) return ExecutionTotals()
        val latestLog = progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(executionId)
            ?: return ExecutionTotals()
        val detailsJson = latestLog.detailsJson
        val passed: Long
        val rejected: Long
        if (!detailsJson.isNullOrBlank()) {
            try {
                val details = objectMapper.readTree(detailsJson)
                when (taskType) {
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

    private fun detectWasCancelled(resultSummary: String?): Boolean {
        if (resultSummary.isNullOrBlank()) return false
        return try {
            val root = objectMapper.readTree(resultSummary)
            root.path("wasCancelled").asBoolean(false)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

data class TaskRunSummaryResponse(
    val executionId: Long?,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String?,
    val durationSeconds: Long?,
    val totalProcessed: Long,
    val totalPassed: Long,
    val totalRejected: Long,
    val summaryText: String?,
    val errorMessage: String?
)
