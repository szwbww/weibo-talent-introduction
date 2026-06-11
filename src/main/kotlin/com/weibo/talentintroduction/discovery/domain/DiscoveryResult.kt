package com.weibo.talentintroduction.discovery.domain

import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider

data class DiscoveryResult(
    val triggeredBy: String,
    val stats: DiscoveryStats,
    val wasCancelled: Boolean = false,
    val summaryText: String? = null
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = stats.indexed
    override val taskFailureCount: Int
        get() = stats.emailRejected + stats.filtered + stats.rawWriteFailed + stats.promotionFailed + stats.dedupErrors
    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            else -> null
        }
}
