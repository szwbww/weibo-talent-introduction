package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskExecution
import org.springframework.data.jdbc.repository.query.Modifying
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

    @Query(
        """
        SELECT * FROM task_execution
        WHERE batch_config_id = :batchConfigId
        ORDER BY started_at DESC
        LIMIT :limit
        """
    )
    fun findRecentByBatchConfigId(batchConfigId: Long, limit: Int): List<TaskExecution>

    @Query(
        """
        SELECT COALESCE(SUM(success_count), 0) FROM task_execution
        WHERE batch_config_id = :batchConfigId
          AND started_at >= :dayStart
          AND started_at < :nextDayStart
        """
    )
    fun sumSuccessCountByBatchConfigIdBetween(
        batchConfigId: Long,
        dayStart: LocalDateTime,
        nextDayStart: LocalDateTime
    ): Long

    @Modifying
    @Query(
        """
        UPDATE task_execution
        SET success_count = :successCount,
            failure_count = :failureCount,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    fun updateProgressCounts(id: Long, successCount: Int, failureCount: Int, updatedAt: LocalDateTime): Int
}
