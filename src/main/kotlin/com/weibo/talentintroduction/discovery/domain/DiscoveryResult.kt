package com.weibo.talentintroduction.discovery.domain

import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.discovery.service.DiscoveryTrafficSnapshot

/**
 * I-3: 发现任务终态的唯一决策函数。任务执行记录（[DiscoveryResult.taskFinalStatus]）与任务进度
 * （EXPERT_DISCOVERY 的 TaskProgress）必须共用它，避免「0 产出故障继续显示成功」。
 */
object DiscoveryTerminalStatus {
    const val SUCCESS = "SUCCESS"
    const val PARTIAL_SUCCESS = "PARTIAL_SUCCESS"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"

    /**
     * 取消 > 全源终止失败 > 部分失败/仍有待续跑工作 > 真实结束。
     * 没有任何来源参与（attemptedSources == 0）与真实空结果一样判 SUCCESS。
     */
    fun decide(cancelled: Boolean, attemptedSources: Int, failedSources: Int, pendingWork: Boolean): String = when {
        cancelled -> CANCELLED
        attemptedSources > 0 && failedSources >= attemptedSources -> FAILED
        failedSources > 0 -> PARTIAL_SUCCESS
        pendingWork -> PARTIAL_SUCCESS
        else -> SUCCESS
    }

    /** I-3: 进度存储用 COMPLETED 表示 SUCCESS，其余终态语义一致。 */
    fun toProgressStatus(status: String): String = if (status == SUCCESS) "COMPLETED" else status
}

data class DiscoveryResult(
    val triggeredBy: String,
    val stats: DiscoveryStats,
    val wasCancelled: Boolean = false,
    val summaryText: String? = null,
    val traffic: DiscoveryTrafficSnapshot? = null
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = stats.indexed
    override val taskFailureCount: Int
        get() = stats.emailRejected + stats.filtered + stats.rawWriteFailed + stats.promotionFailed +
            stats.dedupErrors + stats.sourceFailures
    override val taskFinalStatus: String
        get() = DiscoveryTerminalStatus.decide(
            cancelled = wasCancelled,
            attemptedSources = stats.attemptedSources,
            failedSources = stats.failedSources,
            pendingWork = stats.pendingSources > 0
        )
}
