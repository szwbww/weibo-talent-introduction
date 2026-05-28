package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/task-executions")
class TaskExecutionController(
    private val service: TaskExecutionService
) {
    @GetMapping
    fun listExecutions(
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) status: String?
    ): List<TaskExecutionResponse> =
        service.listExecutions(taskType, status).map { it.toResponse() }

    @GetMapping("/{id}")
    fun getExecution(@PathVariable id: Long): TaskExecutionResponse =
        service.getExecution(id).toResponse()
}

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
