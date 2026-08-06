package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskProgressLog
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface TaskProgressLogRepository : CrudRepository<TaskProgressLog, Long> {
    fun findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId: Long): List<TaskProgressLog>
    fun findAllByTaskTypeOrderByIdDesc(taskType: String): List<TaskProgressLog>
    fun findTopByTaskTypeOrderByIdDesc(taskType: String): TaskProgressLog?
    fun findTopByTaskExecutionIdOrderByIdDesc(taskExecutionId: Long): TaskProgressLog?

    @Modifying
    @Query("UPDATE task_progress_log SET task_execution_id = :executionId WHERE task_execution_id = :pendingToken")
    fun rebindPendingExecutionId(pendingToken: Long, executionId: Long): Int
}
