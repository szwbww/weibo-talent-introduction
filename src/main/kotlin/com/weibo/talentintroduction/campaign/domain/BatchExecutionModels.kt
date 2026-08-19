package com.weibo.talentintroduction.campaign.domain

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime

/** Immutable launch snapshot consumed once per execution (I-1). */
data class BatchExecutionSnapshot(
    val mailType: String,
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
    val oneRoundOnly: Boolean = false
)

data class ManualBatchExecutionRequest(
    val sourceConfigId: Long? = null,
    val sourceUpdatedAt: LocalDateTime? = null,
    val snapshot: BatchExecutionSnapshot
)

data class ReasonCount(
    val label: String,
    val count: Int
)

data class OutcomeBreakdown(
    val target: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val remaining: Int,
    val failureReasons: Map<String, ReasonCount> = emptyMap(),
    val skippedReasons: Map<String, ReasonCount> = emptyMap(),
    val errorSamples: List<String> = emptyList()
)

/** Unified recipient filter applied to ES and MySQL retry paths (I-3). */
data class RecipientScope(
    val mailType: String,
    val funnelLevels: Set<String>,
    val tags: List<String>,
    val regions: List<String>,
    val emailDomains: List<String>,
    val discipline: String?,
    val operatorStatuses: List<String> = emptyList(),
    /** I4a-4: 已解析的门禁 ES 字段（ALLOWED_HAS_FIELDS 交集）；解析只发生在 resolveScope。 */
    val gateEsFields: List<String> = emptyList()
) {
    fun matchesExpert(profile: com.weibo.talentintroduction.expert.domain.ExpertProfile): Boolean {
        // I3a-5：与 ES 的 operatorStatusesFilter 同口径 —— 多状态取 OR；
        // NOT_CONTACTED = ES 文档无该字段（I3a-1）；空集合不判定（I3a-3）。
        if (operatorStatuses.isNotEmpty()) {
            val matched = operatorStatuses.any {
                if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
                else profile.operatorStatus == it
            }
            if (!matched) return false
        }
        if (!discipline.isNullOrBlank()) {
            val matched = if (discipline == "UNCLASSIFIED") {
                profile.disciplineCategory.isNullOrBlank()
            } else {
                profile.disciplineCategory == discipline
            }
            if (!matched) return false
        }
        // I2a-4: 与 ES 的 emailDomainsFilter 同口径 —— 多域取 OR；空集合不判定（I2a-2）。
        if (emailDomains.isNotEmpty()) {
            val email = profile.email
            if (email.isNullOrBlank()) return false
            if (emailDomains.none { email.endsWith("@$it") }) return false
        }
        if (tags.isNotEmpty()) {
            val expertTags = profile.tags.orEmpty()
            if (tags.none { it in expertTags }) return false
        }
        if (regions.isNotEmpty()) {
            val expertRegion = com.weibo.talentintroduction.expert.domain
                .CountryContinentMapping.toRegion(profile.country)
            if (expertRegion !in regions) return false
        }
        // I4a-5: 与 ES 的 fieldPresenceFilter 同口径。BLANK_EXCLUDABLE_FIELDS
        // （researchFields / recentWorkTitles / patentTitles / degree）在 ES 侧是
        // `exists AND NOT term ""`，故空串不算有值；employment / institution 只有
        // `exists`，空串在 ES 里算有值，内存侧对应 `!= null`。
        if (gateEsFields.isNotEmpty()) {
            val allPresent = gateEsFields.all { field ->
                when (field) {
                    "employment" -> profile.employment != null
                    "institution" -> profile.institution != null
                    "degree" -> !profile.degree.isNullOrBlank()
                    "researchFields" -> !profile.researchFields.isNullOrBlank()
                    "recentWorkTitles" -> profile.recentWorkTitles?.any { it.isNotBlank() } == true
                    "patentTitles" -> profile.patentTitles?.any { it.isNotBlank() } == true
                    else -> true   // I4a-3 已裁剪，理论不可达；保守放行，不静默排除
                }
            }
            if (!allPresent) return false
        }
        return true
    }

    companion object {
        fun fromSnapshot(snapshot: BatchExecutionSnapshot): RecipientScope {
            val levels = when (snapshot.funnelLevel?.trim()?.takeIf { it.isNotEmpty() }) {
                null -> setOf("CANDIDATE", "APPLICATION")
                "CANDIDATE" -> setOf("CANDIDATE")
                "APPLICATION" -> setOf("APPLICATION")
                else -> setOf(snapshot.funnelLevel)
            }
            return RecipientScope(
                mailType = snapshot.mailType,
                funnelLevels = levels,
                tags = snapshot.tags,
                regions = snapshot.regions,
                // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。
                emailDomains = snapshot.emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                discipline = snapshot.discipline?.trim()?.takeIf { it.isNotEmpty() },
                // I3a-3：trim、丢空、去重保序；空集合 = 不限。
                operatorStatuses = snapshot.operatorStatuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
        }
    }
}

object BatchOutcomeReasonCodes {
    const val SEND_EXCEPTION = "SEND_EXCEPTION"
    const val TEMPLATE_RENDER_FAILED = "TEMPLATE_RENDER_FAILED"
    const val ACCOUNT_UNAVAILABLE = "ACCOUNT_UNAVAILABLE"
    const val SUPPRESSED = "SUPPRESSED"
    const val NO_CONTACT = "NO_CONTACT"
    const val DEDUP = "DEDUP"
    const val DAILY_CAP_EXCEEDED = "DAILY_CAP_EXCEEDED"
    const val CANCELLED = "CANCELLED"
    const val PERSONALIZATION_INCOMPLETE = "PERSONALIZATION_INCOMPLETE"

    val LABELS = mapOf(
        SEND_EXCEPTION to "发送异常",
        TEMPLATE_RENDER_FAILED to "模板渲染失败",
        ACCOUNT_UNAVAILABLE to "邮箱账号不可用",
        SUPPRESSED to "退订/抑制",
        NO_CONTACT to "无联系人账号",
        DEDUP to "去重跳过",
        DAILY_CAP_EXCEEDED to "超日限额",
        CANCELLED to "被取消",
        PERSONALIZATION_INCOMPLETE to "个性化字段缺失"
    )

    fun label(code: String): String = LABELS[code] ?: code
}

class OutcomeAccumulator(private val target: Int) {
    private val failureReasons = mutableMapOf<String, Int>()
    private val skippedReasons = mutableMapOf<String, Int>()
    private val errorSamples = mutableListOf<String>()

    var success: Int = 0
        private set
    var failure: Int = 0
        private set
    var skipped: Int = 0
        private set

    fun recordSuccess() {
        success++
    }

    fun recordFailure(code: String, sample: String? = null) {
        failure++
        failureReasons.merge(code, 1) { a, b -> a + b }
        addSample(sample)
    }

    fun recordSkipped(code: String, sample: String? = null) {
        skipped++
        skippedReasons.merge(code, 1) { a, b -> a + b }
        addSample(sample)
    }

    fun remaining(): Int = (target - success - failure - skipped).coerceAtLeast(0)

    /**
     * Move unprocessed slots into skipped reasons for terminal stop codes (I-6 coverage).
     * Leaves remaining untouched for soft stops like ONE_ROUND_DONE.
     */
    fun annotateTerminalRemaining(stopReason: String?) {
        val left = remaining()
        if (left <= 0) return
        val code = when (stopReason) {
            "CANCELLED" -> BatchOutcomeReasonCodes.CANCELLED
            "DAILY_CAP_REACHED" -> BatchOutcomeReasonCodes.DAILY_CAP_EXCEEDED
            "NO_AVAILABLE_ACCOUNT", "NO_SENDABLE_ACCOUNT",
            "WARMUP_LIMIT_REACHED", "DAILY_LIMIT_REACHED" -> BatchOutcomeReasonCodes.ACCOUNT_UNAVAILABLE
            else -> return
        }
        repeat(left) { recordSkipped(code) }
    }

    fun toBreakdown(): OutcomeBreakdown = OutcomeBreakdown(
        target = target,
        success = success,
        failure = failure,
        skipped = skipped,
        remaining = remaining(),
        failureReasons = failureReasons.mapValues { (code, count) ->
            ReasonCount(BatchOutcomeReasonCodes.label(code), count)
        },
        skippedReasons = skippedReasons.mapValues { (code, count) ->
            ReasonCount(BatchOutcomeReasonCodes.label(code), count)
        },
        errorSamples = errorSamples.toList()
    )

    fun failureReasonsMap(): Map<String, Int> = failureReasons.toMap()
    fun skippedReasonsMap(): Map<String, Int> = skippedReasons.toMap()

    private fun addSample(sample: String?) {
        if (sample.isNullOrBlank()) return
        if (errorSamples.size >= 20) errorSamples.removeAt(0)
        errorSamples.add(sample)
    }
}

fun BatchSendTaskConfig.toExecutionSnapshot(
    objectMapper: ObjectMapper,
    oneRoundOnly: Boolean = false
): BatchExecutionSnapshot {
    val tags = try {
        objectMapper.readValue(tagsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    val regions = try {
        objectMapper.readValue(regionsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I2a-1/I2a-2: email_domains_json 是唯一事实源；解析失败按不限（空集合）处理。
    val emailDomains = try {
        objectMapper.readValue(emailDomainsJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    // I3a-1/I3a-3: operator_statuses_json 是唯一事实源；解析失败按不限（空集合）处理。
    val operatorStatuses = try {
        objectMapper.readValue(operatorStatusesJson, object : TypeReference<List<String>>() {})
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    } catch (_: Exception) {
        emptyList()
    }
    return BatchExecutionSnapshot(
        mailType = mailType,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        regions = regions,
        emailDomains = emailDomains,
        discipline = discipline,
        operatorStatuses = operatorStatuses,
        templateId = templateId,
        gateFilterEnabled = gateFilterEnabled,
        oneRoundOnly = oneRoundOnly
    )
}
