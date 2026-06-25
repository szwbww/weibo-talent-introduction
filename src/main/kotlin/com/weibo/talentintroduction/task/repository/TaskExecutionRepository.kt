package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskExecution
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface TaskExecutionRepository : CrudRepository<TaskExecution, Long> {
    fun findAllByOrderByStartedAtDesc(): List<TaskExecution>

    fun findAllByTaskTypeOrderByStartedAtDesc(taskType: String): List<TaskExecution>

    fun findAllByStatusOrderByStartedAtDesc(status: String): List<TaskExecution>

    fun findAllByTaskTypeAndStatusOrderByStartedAtDesc(
        taskType: String,
        status: String
    ): List<TaskExecution>

    @Query("SELECT * FROM task_execution WHERE task_type = :taskType ORDER BY started_at DESC LIMIT :limit")
    fun findRecentByTaskType(taskType: String, limit: Int): List<TaskExecution>

    @Query(
        """
        SELECT COUNT(*) FROM task_execution
        WHERE task_type = :taskType AND trigger_type = :triggerType
          AND status IN ('RUNNING','SUCCESS','PARTIAL_SUCCESS')
          AND started_at >= :since
        """
    )
    fun countActiveSince(taskType: String, triggerType: String, since: LocalDateTime): Long
}
