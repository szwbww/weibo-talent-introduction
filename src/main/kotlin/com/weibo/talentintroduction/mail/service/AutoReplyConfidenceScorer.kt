package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.llm.service.AiReplyDraftResult
import com.weibo.talentintroduction.llm.service.RequestGroundingStatus
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

/**
 * Confidence score for a grounded auto-reply draft. CRS is a bypass observation
 * only: it never participates in send decisions in this phase.
 */
data class AutoReplyConfidenceScore(
    val crs: Double,
    val coverageScore: Double,
    val evidenceScore: Double,
    val consistencyScore: Double,
    val historyScore: Double,
    val requestCount: Int,
    val unsupportedCount: Int,
    val partialCount: Int,
    val verifiedRuleCount: Int,
    val warningCount: Int
)

/**
 * Pure component scorer: derives every component exclusively from the
 * [AiReplyDraftResult] and the verified evidence rule ids produced by
 * [GroundedAutoReplyDecisionService.verifyAutoEvidenceRuleIds]. No repository,
 * draft service, or LLM client access — the QA match / grounding / readiness
 * verdicts are reused as-is (invariant I-5).
 *
 * Weights are cold-start constants, centralized here for phase-1 calibration.
 */
@Service
class AutoReplyConfidenceScorer {

    fun score(draft: AiReplyDraftResult, verifiedRuleIds: List<Long>): AutoReplyConfidenceScore {
        val facts = draft.requestFacts
        val requestCount = facts.size
        val unsupportedCount = facts.count { it.status == RequestGroundingStatus.UNSUPPORTED }
        val partialCount = facts.count { it.status == RequestGroundingStatus.PARTIAL }
        val warningCount = draft.contextWarnings.size

        val coverageScore = if (requestCount == 0) {
            ZERO
        } else {
            COVERAGE_MAX * facts.sumOf { weightFor(it.status) } / requestCount
        }

        val evidenceScore = if (verifiedRuleIds.isEmpty() || requestCount == 0) {
            ZERO
        } else {
            EVIDENCE_MAX * facts.count { it.factRuleIds.isNotEmpty() } / requestCount
        }

        val readinessFactor = when (draft.draftReadiness) {
            AiReplyDraftReadiness.READY -> READY_FACTOR
            AiReplyDraftReadiness.NEEDS_REVIEW -> NEEDS_REVIEW_FACTOR
            AiReplyDraftReadiness.BLOCKED -> BLOCKED_FACTOR
        }
        val consistencyScore = (CONSISTENCY_MAX - warningCount * WARNING_PENALTY)
            .coerceAtLeast(ZERO) * readinessFactor

        val historyScore = HISTORY_COLD_START

        val crs = (coverageScore + evidenceScore + consistencyScore + historyScore)
            .coerceIn(MIN_CRS, MAX_CRS)
            .let { oneDecimal(it) }

        return AutoReplyConfidenceScore(
            crs = crs,
            coverageScore = oneDecimal(coverageScore),
            evidenceScore = oneDecimal(evidenceScore),
            consistencyScore = oneDecimal(consistencyScore),
            historyScore = oneDecimal(historyScore),
            requestCount = requestCount,
            unsupportedCount = unsupportedCount,
            partialCount = partialCount,
            verifiedRuleCount = verifiedRuleIds.size,
            warningCount = warningCount
        )
    }

    private fun weightFor(status: RequestGroundingStatus): Double = when (status) {
        RequestGroundingStatus.GROUNDED -> GROUNDED_WEIGHT
        RequestGroundingStatus.PARTIAL -> PARTIAL_WEIGHT
        RequestGroundingStatus.UNSUPPORTED -> UNSUPPORTED_WEIGHT
    }

    private fun oneDecimal(value: Double): Double = (value * 10).roundToInt() / 10.0

    companion object {
        const val COVERAGE_MAX = 40.0
        const val EVIDENCE_MAX = 25.0
        const val CONSISTENCY_MAX = 20.0
        const val HISTORY_MAX = 15.0
        const val HISTORY_COLD_START = 7.0
        const val WARNING_PENALTY = 5.0
        const val GROUNDED_WEIGHT = 1.0
        const val PARTIAL_WEIGHT = 0.6
        const val UNSUPPORTED_WEIGHT = 0.35
        const val READY_FACTOR = 1.0
        const val NEEDS_REVIEW_FACTOR = 0.75
        const val BLOCKED_FACTOR = 0.0
        const val MIN_CRS = 0.0
        const val MAX_CRS = 100.0
        private const val ZERO = 0.0
    }
}
