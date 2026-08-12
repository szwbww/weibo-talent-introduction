package com.weibo.talentintroduction.campaign.domain

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDateTime

/** Immutable launch snapshot consumed once per execution (I-1). */
data class BatchExecutionSnapshot(
    val mailType: String,
    val dailyCap: Int,
    val roundSize: Int,
    val roundsPerRun: Int = 1,
    val perMailIntervalMs: Long,
    val perRoundIntervalMs: Long,
    val selfCheckTtlMinutes: Int,
    val funnelLevel: String? = null,
    val tags: List<String> = emptyList(),
    val emailDomain: String? = null,
    val discipline: String? = null,
    val templateId: Long? = null,
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
    val emailDomain: String?,
    val discipline: String?
) {
    fun matchesExpert(profile: com.weibo.talentintroduction.expert.domain.ExpertProfile): Boolean {
        if (!discipline.isNullOrBlank() && profile.disciplineCategory != discipline) return false
        if (!emailDomain.isNullOrBlank()) {
            val email = profile.email
            if (email.isNullOrBlank() || !email.endsWith("@$emailDomain")) return false
        }
        if (tags.isNotEmpty()) {
            val expertTags = profile.tags.orEmpty()
            if (tags.none { it in expertTags }) return false
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
                emailDomain = snapshot.emailDomain?.trim()?.takeIf { it.isNotEmpty() },
                discipline = snapshot.discipline?.trim()?.takeIf { it.isNotEmpty() }
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
    return BatchExecutionSnapshot(
        mailType = mailType,
        dailyCap = dailyCap,
        roundSize = roundSize,
        roundsPerRun = roundsPerRun,
        perMailIntervalMs = perMailIntervalMs,
        perRoundIntervalMs = perRoundIntervalMs,
        selfCheckTtlMinutes = selfCheckTtlMinutes,
        funnelLevel = funnelLevel,
        tags = tags,
        emailDomain = emailDomain,
        discipline = discipline,
        templateId = templateId,
        oneRoundOnly = oneRoundOnly
    )
}
