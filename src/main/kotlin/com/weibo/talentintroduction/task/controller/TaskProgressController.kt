package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.domain.TaskTypeCatalog
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.ExecutionTotals
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor
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

    /**
     * M-3 / I1-1：白名单从 TaskTypeCatalog 派生（成员集合保持既有 6 项不变，N1-2），
     * 不再手工维护第二份字符串。
     */
    private val allowedTaskTypes: Set<String> =
        TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys

    /** I1-3/I1-4：取数统一委托共享 extractor（本控制器不再保留 when(taskType) 分支）。 */
    private val extractor = TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)

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
            val totals: ExecutionTotals = extractor.extract(taskType, exec)
            val wasCancelled = extractor.detectWasCancelled(exec.resultSummary)
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
