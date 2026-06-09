package com.weibo.talentintroduction.task.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("task_progress_log")
data class TaskProgressLog(
    @Id
    val id: Long? = null,
    val taskType: String,
    val taskExecutionId: Long? = null,
    val batchNumber: Int,
    val status: String,
    val processedCount: Long = 0,
    val totalCount: Long = 0,
    val batchProcessed: Int = 0,
    val batchPassed: Int = 0,
    val batchRejected: Int = 0,
    val message: String? = null,
    val detailsJson: String? = null,
    val errorsJson: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
