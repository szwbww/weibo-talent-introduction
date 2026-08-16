package com.weibo.talentintroduction.task.repository

import com.weibo.talentintroduction.task.domain.TaskProgressLog
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

interface TaskProgressLogRepository : CrudRepository<TaskProgressLog, Long> {
    fun findAllByTaskExecutionIdOrderByIdAsc(taskExecutionId: Long): List<TaskProgressLog>
    fun findAllByTaskTypeOrderByIdDesc(taskType: String): List<TaskProgressLog>
    fun findTopByTaskTypeOrderByIdDesc(taskType: String): TaskProgressLog?
    fun findTopByTaskExecutionIdOrderByIdDesc(taskExecutionId: Long): TaskProgressLog?

    @Modifying
    @Query("UPDATE task_progress_log SET task_execution_id = :executionId WHERE task_execution_id = :pendingToken")
    fun rebindPendingExecutionId(pendingToken: Long, executionId: Long): Int

    /**
     * B5 保留清理（I3-1 / M-6）：按 `created_at` 删（V102 新建 idx_tpl_created_at），
     * **禁止**按 task_execution_id 关联（tryStartWithToken 落的孤儿行 execution_id 为负值，
     * 关联删除会漏掉）。`ORDER BY ... LIMIT` 沿索引顺序分批删除，减少锁范围（I3-2）。
     * 不含任何 task_type 排除（I3-5：清理任务自身的审计行同样受保留策略约束）。
     */
    @Modifying
    @Query("DELETE FROM task_progress_log WHERE created_at < :cutoff ORDER BY created_at LIMIT :batchSize")
    fun deleteOlderThan(cutoff: LocalDateTime, batchSize: Int): Int
}
