package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskExecution
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

/**
 * One aggregated row per batch config: the most recent execution start time (I-4/I-5).
 * Covers MANUAL + SCHEDULED executions; rows with batch_config_id = null (independent
 * manual runs) are excluded naturally by the WHERE clause.
 */
data class BatchConfigLastExecution(
    val batchConfigId: Long,
    val lastStartedAt: LocalDateTime
)

/**
 * 列表投影：刻意不含 request_payload / result_summary（两者均为 TEXT，
 * 单条 AUTO_REPLY_ALL 的 result_summary 内嵌 accounts[].repliedExperts[]，
 * 可达数十 KB，而列表页一个字段都不用）。见主计划 Invariant M-1。
 */
data class TaskExecutionListItem(
    val id: Long,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val successCount: Int,
    val failureCount: Int,
    val errorMessage: String?,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?
)

/**
 * 任务类型下拉选项投影（I1-7）：`SELECT DISTINCT task_type` 的聚合行数与类型枚举。
 * 照 `BatchConfigLastExecution` 范式（列别名与 DTO 属性名对齐）。不含 TEXT 列，满足 M-1。
 */
data class TaskTypeCount(
    val taskType: String,
    val cnt: Long
)

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

    /**
     * I-4/I-5: single aggregated query for the last execution start time per batch config.
     * Callers MUST guard against an empty [batchConfigIds] (IN () is invalid SQL).
     */
    @Query(
        """
        SELECT batch_config_id AS batch_config_id, MAX(started_at) AS last_started_at
        FROM task_execution
        WHERE batch_config_id IN (:batchConfigIds)
        GROUP BY batch_config_id
        """
    )
    fun findLastStartedAtByBatchConfigIds(batchConfigIds: Collection<Long>): List<BatchConfigLastExecution>

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

    // ---- Paged list queries (M-1): SELECT list deliberately omits request_payload / result_summary ----

    @Query(
        """
        SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
               status AS status, success_count AS success_count, failure_count AS failure_count,
               error_message AS error_message, started_at AS started_at, finished_at AS finished_at
        FROM task_execution
        ORDER BY started_at DESC
        LIMIT :size OFFSET :offset
        """
    )
    fun findPage(size: Int, offset: Long): List<TaskExecutionListItem>

    @Query(
        """
        SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
               status AS status, success_count AS success_count, failure_count AS failure_count,
               error_message AS error_message, started_at AS started_at, finished_at AS finished_at
        FROM task_execution
        WHERE task_type = :taskType
        ORDER BY started_at DESC
        LIMIT :size OFFSET :offset
        """
    )
    fun findPageByTaskType(taskType: String, size: Int, offset: Long): List<TaskExecutionListItem>

    @Query(
        """
        SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
               status AS status, success_count AS success_count, failure_count AS failure_count,
               error_message AS error_message, started_at AS started_at, finished_at AS finished_at
        FROM task_execution
        WHERE status = :status
        ORDER BY started_at DESC
        LIMIT :size OFFSET :offset
        """
    )
    fun findPageByStatus(status: String, size: Int, offset: Long): List<TaskExecutionListItem>

    @Query(
        """
        SELECT id AS id, task_type AS task_type, trigger_type AS trigger_type,
               status AS status, success_count AS success_count, failure_count AS failure_count,
               error_message AS error_message, started_at AS started_at, finished_at AS finished_at
        FROM task_execution
        WHERE task_type = :taskType AND status = :status
        ORDER BY started_at DESC
        LIMIT :size OFFSET :offset
        """
    )
    fun findPageByTaskTypeAndStatus(
        taskType: String,
        status: String,
        size: Int,
        offset: Long
    ): List<TaskExecutionListItem>

    @Query("SELECT COUNT(*) FROM task_execution")
    fun countAll(): Long

    /**
     * I1-7：下拉选项来自实际数据（`GROUP BY task_type`），与 catalog 左连接后由
     * controller 做 label 兜底——catalog 未声明的类型仍然返回（I1-7 禁止只返回
     * catalog 全集）。不含 WHERE、不含 TEXT 列（M-1）。
     */
    @Query(
        """
        SELECT task_type AS task_type, COUNT(*) AS cnt
        FROM task_execution
        GROUP BY task_type
        """
    )
    fun findTaskTypeCounts(): List<TaskTypeCount>

    @Query("SELECT COUNT(*) FROM task_execution WHERE task_type = :taskType")
    fun countByTaskType(taskType: String): Long

    @Query("SELECT COUNT(*) FROM task_execution WHERE status = :status")
    fun countByStatus(status: String): Long

    @Query("SELECT COUNT(*) FROM task_execution WHERE task_type = :taskType AND status = :status")
    fun countByTaskTypeAndStatus(taskType: String, status: String): Long
}
