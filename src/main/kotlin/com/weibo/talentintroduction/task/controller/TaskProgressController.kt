package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/task-progress")
class TaskProgressController(
    private val progressStore: TaskProgressStore
) {
    @GetMapping("/{taskType}")
    fun getProgress(@PathVariable taskType: String): ResponseEntity<TaskProgress> {
        val progress = progressStore.get(taskType) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(progress)
    }
}
