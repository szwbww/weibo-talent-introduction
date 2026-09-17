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
    val expertTypesJson: String = "[]",
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /** I-1: 研究方向三态（[ResearchDirectionFilters]）；旧行与默认 = ANY（不限）。 */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
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
    val expertTypes: List<String> = emptyList(),
    val templateId: Long?,
    val gateFilterEnabled: Boolean = false,
    /** I-1: 研究方向三态，永远回显权威值（旧任务 = ANY）。 */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    /** Next planned trigger time; null when the cron is invalid (I-1/I-2/I-3). */
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
    val expertTypes: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /** I-1: 未传值 = ANY（不限）；非法值由配置服务拒绝。 */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY
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
    val expertTypes: List<String> = emptyList(),
    val templateId: Long? = null,
    val gateFilterEnabled: Boolean = false,
    /** I-1: 未传值 = ANY（不限）；非法值由配置服务拒绝。 */
    val researchDirectionFilter: String = ResearchDirectionFilters.ANY
)

/**
 * I-1: 研究方向三态的唯一权威定义（迁移 V128 的 `DEFAULT 'ANY'` 与之对齐）。
 * - `ANY`（不限）：不追加任何方向查询，旧任务与未传值的默认值；
 * - `PRESENT`（有）：命中 ES `fieldPresenceFilter("researchFields")`；
 * - `ABSENT`（无）：命中该 filter 的 `bool.must_not`。
 *
 * 非法值由写入与启动两侧的 [requireAllowed] 拒绝，不静默降级。
 */
object ResearchDirectionFilters {
    const val ANY = "ANY"
    const val PRESENT = "PRESENT"
    const val ABSENT = "ABSENT"

    /** I-2: 专家 ES 文档中的研究方向字段（keyword；见 `ExpertSearchService.BLANK_EXCLUDABLE_FIELDS`）。 */
    const val ES_FIELD = "researchFields"

    val ALLOWED: Set<String> = setOf(ANY, PRESENT, ABSENT)

    /** 空白/未传值归一为 [ANY]；其余原样返回（大小写敏感，非法值交给 [requireAllowed]）。 */
    fun normalize(raw: String?): String = raw?.trim().takeIf { !it.isNullOrEmpty() } ?: ANY

    /** I-1: 三态白名单校验；非法值抛 [IllegalArgumentException]（保存 → 4xx，启动 → 422）。 */
    fun requireAllowed(raw: String?): String {
        val value = normalize(raw)
        require(value in ALLOWED) { "researchDirectionFilter must be one of $ALLOWED: $raw" }
        return value
    }
}
