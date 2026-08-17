package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.config.TaskRetentionProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 任务审计保留清理调度（计划 B5 T3-5）。
 *
 * - N3-5：默认关闭（`talent-introduction.task-retention.enabled=false`），
 *   `scheduleRetention()` 直接 return，零行为变化。
 * - N3-6：不用任何 executor——`@Scheduled` 自带调度线程即可，每日一次低频任务。
 * - 用 `runAndRecordWithResult` 使 [RetentionResult] 落入 result_summary，
 *   终态语义由 TaskExecutionSummaryProvider 分支给出（I3-6）。
 * - 配置类在此经 [EnableConfigurationProperties] 注册（与 MailSchedulingProperties
 *   相同的注册机制，落在本文件内以保持授权文件范围）。
 */
@Component
@Configuration
@EnableConfigurationProperties(TaskRetentionProperties::class)
class TaskAuditRetentionScheduler(
    private val props: TaskRetentionProperties,
    private val taskExecutionService: TaskExecutionService,
    private val retentionService: TaskAuditRetentionService
) {
    @Scheduled(cron = "\${talent-introduction.task-retention.cron:-}")
    fun scheduleRetention() {
        if (!props.enabled) return
        taskExecutionService.runAndRecordWithResult(
            "TASK_AUDIT_RETENTION", "SCHEDULED", "task-audit-retention"
        ) {
            retentionService.purge()
        }
    }
}
