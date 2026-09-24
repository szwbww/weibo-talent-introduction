package com.weibo.talentintroduction.task.domain

/** 只描述中断线索，不推断已执行的业务动作成功或失败。 */
data class TaskInterruptionReason(val code: String, val label: String)

object TaskInterruptionReasons {
    val defaults = listOf(
        TaskInterruptionReason("SERVICE_RESTART", "服务重启或发布中断"),
        TaskInterruptionReason("PROCESS_EXIT", "进程异常退出"),
        TaskInterruptionReason("DEPENDENCY_FAILURE", "数据库、网络或外部服务故障"),
        TaskInterruptionReason("QUEUE_INTERRUPTED", "消息队列或消费中断"),
        TaskInterruptionReason("TIMEOUT_STALLED", "执行超时或长期无进展"),
        TaskInterruptionReason("VERIFIED_STOPPED", "人工确认执行已停止"),
        TaskInterruptionReason("UNKNOWN", "原因待排查"),
        TaskInterruptionReason("OTHER", "其他原因（填写说明）")
    )

    fun label(code: String): String? = defaults.firstOrNull { it.code == code }?.label
}
