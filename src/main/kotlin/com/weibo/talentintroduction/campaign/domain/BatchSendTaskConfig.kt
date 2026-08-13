package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("batch_send_task_config")
data class BatchSendTaskConfig(
    @Id
    val id: Long? = null,
    val configName: String,
    val mailType: String,
    val autoEnabled: Boolean = false,
    val cron: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tagsJson: String = "[]",
    val regionsJson: String = "[]",
    val emailDomain: String? = null,
    val discipline: String? = null,
    val operatorStatus: String? = null,
    val templateId: Long? = null,
    val legacyCode: String? = null,
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

data class BatchSendTaskConfigView(
    val id: Long,
    val configName: String,
    val mailType: String,
    val autoEnabled: Boolean,
    val cron: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String?,
    val tags: List<String>,
    val regions: List<String> = emptyList(),
    val emailDomain: String?,
    val discipline: String?,
    val operatorStatus: String? = null,
    val templateId: Long?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    /** Next automatic trigger time; null when autoEnabled = false or the cron is invalid (I-1/I-2/I-3). */
    val nextFireTime: LocalDateTime? = null,
    /** Most recent execution start time (started_at), MANUAL or SCHEDULED; null when never executed (I-5). */
    val lastExecutedAt: LocalDateTime? = null
)

data class BatchSendTaskConfigCreateCommand(
    val configName: String,
    val autoEnabled: Boolean = false,
    val cron: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tags: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val emailDomain: String? = null,
    val discipline: String? = null,
    val operatorStatus: String? = null,
    val templateId: Long? = null
)

data class BatchSendTaskConfigUpdateCommand(
    val configName: String,
    val autoEnabled: Boolean,
    val cron: String,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tags: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val emailDomain: String? = null,
    val discipline: String? = null,
    val operatorStatus: String? = null,
    val templateId: Long? = null
)
