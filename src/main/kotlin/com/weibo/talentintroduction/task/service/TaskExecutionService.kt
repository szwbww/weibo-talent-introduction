package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskInterruptionReasons
import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Service
class TaskExecutionService(
    private val repository: TaskExecutionRepository,
    private val objectMapper: ObjectMapper,
    private val schedulingProperties: MailSchedulingProperties
) {
    private val ownerToken = UUID.randomUUID().toString()

    /** 心跳与恢复共用五分钟租约；历史无 owner 的运行行按开始时间判断。 */
    fun reconcileInterruptedExecutions(): Int {
        val now = LocalDateTime.now()
        repository.heartbeatOwned(ownerToken, now)
        return repository.interruptExpired(now.minusMinutes(5), now)
    }

    fun interruptManually(id: Long, reasonCode: String, detail: String?, handledBy: String): TaskExecution {
        val label = TaskInterruptionReasons.label(reasonCode)
            ?: throw IllegalArgumentException("未知异常处理原因")
        val note = detail?.trim()?.takeIf { it.isNotEmpty() }
        require(note == null || note.length <= 1000) { "补充说明最多1000字" }
        require(reasonCode != "OTHER" || note != null) { "其他原因必须填写说明" }
        require(handledBy.isNotBlank()) { "操作人不能为空" }
        val now = LocalDateTime.now()
        val message = "人工标记已中断：$label" + (note?.let { "；$it" } ?: "")
        val changed = repository.interruptManually(
            id, reasonCode, note, handledBy, message, now.minusMinutes(5), now
        )
        if (changed == 0) {
            val current = repository.findById(id).orElseThrow {
                NoSuchElementException("任务记录不存在：$id")
            }
            throw IllegalStateException(if (current.status in setOf("RUNNING", "CANCELLING")) {
                "任务仍有近期心跳，请先确认执行进程已停止；失联超过5分钟后可人工处理"
            } else {
                "任务已有终态：${current.status}"
            })
        }
        return repository.findById(id).orElseThrow { NoSuchElementException("任务记录不存在：$id") }
    }
    fun listExecutions(taskType: String?, status: String?, page: Int, size: Int): TaskExecutionPage {
        val offset = page.toLong() * size
        return when {
            taskType != null && status != null -> TaskExecutionPage(
                items = repository.findPageByTaskTypeAndStatus(taskType, status, size, offset),
                total = repository.countByTaskTypeAndStatus(taskType, status)
            )

            taskType != null -> TaskExecutionPage(
                items = repository.findPageByTaskType(taskType, size, offset),
                total = repository.countByTaskType(taskType)
            )

            status != null -> TaskExecutionPage(
                items = repository.findPageByStatus(status, size, offset),
                total = repository.countByStatus(status)
            )

            else -> TaskExecutionPage(
                items = repository.findPage(size, offset),
                total = repository.countAll()
            )
        }
    }

    fun getExecution(id: Long): TaskExecution =
        repository.findById(id)
            .orElseThrow { error("Task execution not found: $id") }

    fun listRecentByBatchConfigId(batchConfigId: Long, limit: Int): List<TaskExecution> {
        require(limit in 1..200) { "limit must be between 1 and 200" }
        return repository.findRecentByBatchConfigId(batchConfigId, limit)
    }

    fun listRecentByTaskType(taskType: String, limit: Int): List<TaskExecution> {
        require(limit in 1..200) { "limit must be between 1 and 200" }
        return repository.findRecentByTaskType(taskType, limit)
    }

    /**
     * Batch last-execution start time per config (I-4): a single aggregated query covering
     * MANUAL + SCHEDULED executions. Independent manual runs (batch_config_id = null) are
     * excluded naturally. An empty input is a no-op (IN () is invalid SQL).
     */
    fun lastExecutedAtByBatchConfigIds(batchConfigIds: Collection<Long>): Map<Long, LocalDateTime> {
        if (batchConfigIds.isEmpty()) return emptyMap()
        return repository.findLastStartedAtByBatchConfigIds(batchConfigIds)
            .associate { it.batchConfigId to it.lastStartedAt }
    }

    /**
     * Natural-day success sum for a config (Asia/Shanghai day boundary).
     * Used by dailyCap across auto + config-sourced manual runs (I-5).
     */
    fun sumSuccessCountTodayByBatchConfigId(batchConfigId: Long, now: LocalDateTime = LocalDateTime.now(SHANGHAI)): Int {
        val dayStart = LocalDate.from(now).atStartOfDay()
        val nextDayStart = dayStart.plusDays(1)
        return repository.sumSuccessCountByBatchConfigIdBetween(batchConfigId, dayStart, nextDayStart).toInt()
    }

    /** Persist mid-run success/failure counts so crash/restart still counts toward dailyCap (I-5). */
    fun updateProgressCounts(executionId: Long, successCount: Int, failureCount: Int) {
        repository.updateProgressCounts(executionId, successCount, failureCount, LocalDateTime.now())
    }

    fun listRecentPolls(limit: Int): List<TaskExecution> {
        require(limit in 1..100) { "limit must be between 1 and 100" }
        return repository.findRecentByTaskType("AUTO_REPLY_ALL", limit)
    }

    fun countScheduledSince(taskType: String, since: LocalDateTime): Long =
        repository.countActiveSince(taskType, "SCHEDULED", since)

    fun nextPollTime(): LocalDateTime? {
        val cron = schedulingProperties.autoReplyAllCron
        if (cron.isBlank() || cron == "-") return null
        return try {
            val expr = org.springframework.scheduling.support.CronExpression.parse(cron)
            expr.next(LocalDateTime.now())
        } catch (_: Exception) {
            null
        }
    }

    fun <T : Any?> runAndRecordWithResult(
        taskType: String,
        triggerType: String,
        request: Any,
        onStarted: ((executionId: Long) -> Unit)? = null,
        batchConfigId: Long? = null,
        block: () -> T
    ): Pair<TaskExecution, T> {
        val startedAt = LocalDateTime.now()
        val running = repository.save(
            TaskExecution(
                taskType = taskType,
                triggerType = triggerType,
                status = "RUNNING",
                requestPayload = toJson(request),
                resultSummary = null,
                startedAt = startedAt,
                createdAt = startedAt,
                updatedAt = startedAt,
                batchConfigId = batchConfigId,
                ownerToken = ownerToken,
                heartbeatAt = startedAt
            )
        )

        return try {
            onStarted?.invoke(running.id!!)
            val result = block()
            val resultValue: Any? = result ?: Unit
            val (successCount, failureCount, status) = when (resultValue) {
                is TaskExecutionSummaryProvider -> {
                    val s = resultValue.taskSuccessCount
                    val f = resultValue.taskFailureCount
                    val finalStatus = resultValue.taskFinalStatus
                        ?: when {
                            f > 0 && s > 0 -> "PARTIAL_SUCCESS"
                            f > 0 -> "FAILED"
                            else -> "SUCCESS"
                        }
                    Triple(s, f, finalStatus)
                }
                else -> {
                    val summary = TaskResultSummary.from(resultValue)
                    val status = when {
                        summary.failureCount > 0 && summary.successCount > 0 -> "PARTIAL_SUCCESS"
                        summary.failureCount > 0 -> "FAILED"
                        else -> "SUCCESS"
                    }
                    Triple(summary.successCount, summary.failureCount, status)
                }
            }
            val finishedAt = LocalDateTime.now()
            val saved = finishOwned(
                running.copy(
                    status = status,
                    resultSummary = toJson(resultValue),
                    successCount = successCount,
                    failureCount = failureCount,
                    finishedAt = finishedAt,
                    updatedAt = finishedAt
                )
            )
            @Suppress("UNUSED_EXPRESSION")
            Pair(saved, result)
        } catch (ex: Exception) {
            val finishedAt = LocalDateTime.now()
            finishOwned(
                running.copy(
                    status = "FAILED",
                    failureCount = 1,
                    errorMessage = ex.message?.take(4000),
                    finishedAt = finishedAt,
                    updatedAt = finishedAt
                )
            )
            throw ex
        }
    }

    fun <T : Any?> runAndRecord(
        taskType: String,
        triggerType: String,
        request: Any,
        onStarted: ((executionId: Long) -> Unit)? = null,
        batchConfigId: Long? = null,
        block: () -> T
    ): TaskExecution {
        val startedAt = LocalDateTime.now()
        val running = repository.save(
            TaskExecution(
                taskType = taskType,
                triggerType = triggerType,
                status = "RUNNING",
                requestPayload = toJson(request),
                resultSummary = null,
                startedAt = startedAt,
                createdAt = startedAt,
                updatedAt = startedAt,
                batchConfigId = batchConfigId,
                ownerToken = ownerToken,
                heartbeatAt = startedAt
            )
        )

        return try {
            onStarted?.invoke(running.id!!)
            val result = block()
            val resultValue: Any? = result
            val (successCount, failureCount, status) = when (resultValue) {
                is TaskExecutionSummaryProvider -> {
                    val s = resultValue.taskSuccessCount
                    val f = resultValue.taskFailureCount
                    val finalStatus = resultValue.taskFinalStatus
                        ?: when {
                            f > 0 && s > 0 -> "PARTIAL_SUCCESS"
                            f > 0 -> "FAILED"
                            else -> "SUCCESS"
                        }
                    Triple(s, f, finalStatus)
                }
                else -> {
                    val summary = TaskResultSummary.from(resultValue)
                    val status = when {
                        summary.failureCount > 0 && summary.successCount > 0 -> "PARTIAL_SUCCESS"
                        summary.failureCount > 0 -> "FAILED"
                        else -> "SUCCESS"
                    }
                    Triple(summary.successCount, summary.failureCount, status)
                }
            }
            val finishedAt = LocalDateTime.now()
            finishOwned(
                running.copy(
                    status = status,
                    resultSummary = toJson(resultValue),
                    successCount = successCount,
                    failureCount = failureCount,
                    finishedAt = finishedAt,
                    updatedAt = finishedAt
                )
            )
        } catch (ex: Exception) {
            val finishedAt = LocalDateTime.now()
            finishOwned(
                running.copy(
                    status = "FAILED",
                    failureCount = 1,
                    errorMessage = ex.message?.take(4000),
                    finishedAt = finishedAt,
                    updatedAt = finishedAt
                )
            )
        }
    }

    private fun finishOwned(next: TaskExecution): TaskExecution {
        val id = requireNotNull(next.id)
        val changed = repository.finishOwned(
            id, ownerToken, next.status, next.resultSummary,
            next.successCount, next.failureCount, next.errorMessage,
            requireNotNull(next.finishedAt)
        )
        // 租约恢复或人工中断已抢先写入终态时，不能再用旧快照覆盖它。
        return if (changed == 1) next else repository.findById(id).orElse(next)
    }

    private fun toJson(value: Any?): String =
        objectMapper.writeValueAsString(value)

    companion object {
        val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

data class TaskExecutionPage(
    val items: List<TaskExecutionListItem>,
    val total: Long
)

data class TaskDispatchRequest(
    val campaignId: Long? = null,
    val batchSize: Int? = null,
    val maxMessagesPerAccount: Int? = null,
    val dispatchMode: String
)

private data class TaskResultSummary(
    val successCount: Int,
    val failureCount: Int
) {
    companion object {
        fun from(result: Any?): TaskResultSummary {
            val fields = result?.javaClass?.declaredFields.orEmpty()
                .onEach { it.isAccessible = true }
                .associate { field -> field.name to field.get(result) }

            return TaskResultSummary(
                successCount = firstInt(fields, "sent", "replied", "accepted", "fetched", "dispatched"),
                failureCount = firstInt(fields, "manualReview", "skipped", "failureCount")
            )
        }

        private fun firstInt(fields: Map<String, Any?>, vararg names: String): Int =
            names.firstNotNullOfOrNull { name ->
                when (val value = fields[name]) {
                    is Int -> value
                    is Boolean -> if (value) 1 else 0
                    else -> null
                }
            } ?: 0
    }
}
