package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/task-progress")
class TaskProgressController(
    private val progressStore: TaskProgressStore,
    private val progressLogRepository: TaskProgressLogRepository
) {
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
        @RequestParam(required = false) executionId: Long?
    ): List<TaskProgressLog> {
        val targetExecutionId = executionId
            ?: progressStore.getCurrentExecutionId(taskType)
            ?: run {
                val latestLog = progressLogRepository.findTopByTaskTypeOrderByIdDesc(taskType)
                latestLog?.taskExecutionId
            }
            ?: return emptyList()
        return progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(targetExecutionId)
    }
}
