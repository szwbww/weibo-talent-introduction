package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/task-executions")
class TaskExecutionController(
    private val service: TaskExecutionService,
    private val objectMapper: ObjectMapper
) {
    @GetMapping
    fun listExecutions(
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) status: String?
    ): List<TaskExecutionResponse> =
        service.listExecutions(taskType, status).map { it.toResponse() }

    @GetMapping("/recent-polls")
    fun recentPolls(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<PollLogResponse> {
        require(limit in 1..100) { "limit must be between 1 and 100" }

        val executions = service.listRecentPolls(limit)
        val nextPoll = service.nextPollTime()
        return executions.map { exec ->
            val resultSummary = exec.resultSummary
                ?.let { tryParseResultSummary(it) }
            PollLogResponse(
                id = exec.id,
                triggerType = exec.triggerType,
                status = exec.status,
                totalAccountsToPoll = resultSummary?.totalAccountsToPoll
                    ?: resultSummary?.totalExpertsToCheck ?: 0,
                accountsPolled = resultSummary?.accountsPolled
                    ?: resultSummary?.expertsChecked ?: 0,
                expertsWithReply = resultSummary?.expertsWithReply.orEmpty(),
                startedAt = exec.startedAt.toString(),
                finishedAt = exec.finishedAt?.toString(),
                durationSeconds = exec.finishedAt?.let { finish ->
                    java.time.Duration.between(exec.startedAt, finish).seconds
                },
                nextPollAt = nextPoll?.toString(),
                isManualTrigger = exec.triggerType.startsWith("MANUAL_")
            )
        }
    }

    @GetMapping("/{id}")
    fun getExecution(@PathVariable id: Long): TaskExecutionResponse =
        service.getExecution(id).toResponse()

    private fun tryParseResultSummary(json: String): PollResultSummary? =
        try {
            objectMapper.readValue(json)
        } catch (_: Exception) {
            null
        }
}

data class PollResultSummary(
    val totalAccountsToPoll: Int = 0,
    val accountsPolled: Int = 0,
    val totalExpertsToCheck: Int = 0,
    val expertsChecked: Int = 0,
    val expertsWithReply: List<String> = emptyList()
)

data class PollLogResponse(
    val id: Long?,
    val triggerType: String,
    val status: String,
    val totalAccountsToPoll: Int,
    val accountsPolled: Int,
    val expertsWithReply: List<String>,
    val startedAt: String,
    val finishedAt: String?,
    val durationSeconds: Long?,
    val nextPollAt: String?,
    val isManualTrigger: Boolean
)

data class TaskExecutionResponse(
    val id: Long?,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val requestPayload: String?,
    val resultSummary: String?,
    val successCount: Int,
    val failureCount: Int,
    val errorMessage: String?,
    val startedAt: String,
    val finishedAt: String?,
    val createdAt: String?,
    val updatedAt: String?
)

private fun TaskExecution.toResponse(): TaskExecutionResponse =
    TaskExecutionResponse(
        id = id,
        taskType = taskType,
        triggerType = triggerType,
        status = status,
        requestPayload = requestPayload,
        resultSummary = resultSummary,
        successCount = successCount,
        failureCount = failureCount,
        errorMessage = errorMessage,
        startedAt = startedAt.toString(),
        finishedAt = finishedAt?.toString(),
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
