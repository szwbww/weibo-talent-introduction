package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskProgressLog
import org.springframework.data.repository.CrudRepository

interface TaskProgressLogRepository : CrudRepository<TaskProgressLog, Long> {
    fun findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId: Long): List<TaskProgressLog>
    fun findAllByTaskTypeOrderByIdDesc(taskType: String): List<TaskProgressLog>
    fun findTopByTaskTypeOrderByIdDesc(taskType: String): TaskProgressLog?
}
