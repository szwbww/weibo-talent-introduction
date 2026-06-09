package com.weibo.talentintroduction.expert.domain

import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider

data class RevalidationStats(
    var total: Int = 0,
    var passed: Int = 0,
    var demoted: Int = 0,
    var demotionFailed: Int = 0,
    var tagFailed: Int = 0,
    val demotionReasons: MutableMap<String, Int> = mutableMapOf()
)

data class RevalidationResult(
    val stats: RevalidationStats,
    val wasCancelled: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = stats.passed
    override val taskFailureCount: Int get() = stats.demoted + stats.demotionFailed + stats.tagFailed
    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            stats.total == 0 -> "SUCCESS"
            taskFailureCount == 0 -> "SUCCESS"
            taskSuccessCount == 0 -> "FAILED"
            else -> "PARTIAL_SUCCESS"
        }
}

data class PromotionScanStats(
    var total: Int = 0,
    var promoted: Int = 0,
    var filtered: Int = 0,
    var emailRejected: Int = 0,
    var alreadyPromoted: Int = 0,
    var promotionFailed: Int = 0,
    var existenceCheckFailed: Int = 0
)

data class PromotionScanResult(
    val stats: PromotionScanStats,
    val wasCancelled: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = stats.promoted
    override val taskFailureCount: Int get() = stats.filtered + stats.emailRejected + stats.promotionFailed + stats.existenceCheckFailed
    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            stats.total == 0 -> "SUCCESS"
            taskFailureCount == 0 -> "SUCCESS"
            taskSuccessCount == 0 -> "FAILED"
            else -> "PARTIAL_SUCCESS"
        }
}
