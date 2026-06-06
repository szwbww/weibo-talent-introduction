package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class TaskExecutionService(
    private val repository: TaskExecutionRepository,
    private val objectMapper: ObjectMapper
) {
    fun listExecutions(taskType: String?, status: String?): List<TaskExecution> =
        when {
            taskType != null && status != null ->
                repository.findAllByTaskTypeAndStatusOrderByStartedAtDesc(taskType, status)

            taskType != null ->
                repository.findAllByTaskTypeOrderByStartedAtDesc(taskType)

            status != null ->
                repository.findAllByStatusOrderByStartedAtDesc(status)

            else ->
                repository.findAllByOrderByStartedAtDesc()
        }

    fun getExecution(id: Long): TaskExecution =
        repository.findById(id)
            .orElseThrow { error("Task execution not found: $id") }

    fun <T : Any?> runAndRecord(
        taskType: String,
        triggerType: String,
        request: Any,
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
                updatedAt = startedAt
            )
        )

        return try {
            val result = block()
            val resultValue: Any? = result
            val (successCount, failureCount, status) = when (resultValue) {
                is TaskExecutionSummaryProvider -> {
                    val s = resultValue.taskSuccessCount
                    val f = resultValue.taskFailureCount
                    val finalStatus = resultValue.taskFinalStatus
                        ?: if (f > 0 && s == 0) "FAILED" else "SUCCESS"
                    Triple(s, f, finalStatus)
                }
                else -> {
                    val summary = TaskResultSummary.from(resultValue)
                    Triple(summary.successCount, summary.failureCount, "SUCCESS")
                }
            }
            val finishedAt = LocalDateTime.now()
            repository.save(
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
            repository.save(
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

    private fun toJson(value: Any?): String =
        objectMapper.writeValueAsString(value)
}

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
