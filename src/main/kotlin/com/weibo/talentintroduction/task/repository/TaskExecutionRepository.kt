package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskExecution
import org.springframework.data.repository.CrudRepository

interface TaskExecutionRepository : CrudRepository<TaskExecution, Long> {
    fun findAllByOrderByStartedAtDesc(): List<TaskExecution>

    fun findAllByTaskTypeOrderByStartedAtDesc(taskType: String): List<TaskExecution>

    fun findAllByStatusOrderByStartedAtDesc(status: String): List<TaskExecution>

    fun findAllByTaskTypeAndStatusOrderByStartedAtDesc(
        taskType: String,
        status: String
    ): List<TaskExecution>
}
