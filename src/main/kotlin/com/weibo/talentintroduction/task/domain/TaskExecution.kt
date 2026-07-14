package com.weibo.talentintroduction.task.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("task_execution")
data class TaskExecution(
    @Id
    val id: Long? = null,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val requestPayload: String?,
    val resultSummary: String?,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val errorMessage: String? = null,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
    /** Source batch_send_task_config id at launch; null for independent manual runs. Soft-delete safe. */
    val batchConfigId: Long? = null
)
