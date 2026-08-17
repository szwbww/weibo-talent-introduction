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
    val emailDomainsJson: String = "[]",
    val discipline: String? = null,
    val operatorStatusesJson: String = "[]",
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /** I-6-5: 可达性过滤模式（EXCLUDE_BLOCKED / HIGH_ONLY 等，见 ExpertSearchService.ALLOWED_REACHABILITY_MODES）；null/空 = 不过滤。 */
    val reachabilityFilter: String? = null,
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
    val emailDomains: List<String> = emptyList(),
    val discipline: String?,
    val operatorStatuses: List<String> = emptyList(),
    val templateId: Long?,
    val gateFilterEnabled: Boolean = false,
    /** I-6-2: 可达性过滤模式；null = 不过滤（I-6-5）。 */
    val reachabilityFilter: String? = null,
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
    val emailDomains: List<String> = emptyList(),
    val discipline: String? = null,
    val operatorStatuses: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    val reachabilityFilter: String? = null
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
    val emailDomains: List<String> = emptyList(),
    val discipline: String? = null,
    val operatorStatuses: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /** I-6-5: 带默认值，旧 typed API 请求（BatchSendConfigUpdateRequest）不含该字段时不命中默认值重置。 */
    val reachabilityFilter: String? = null
)
