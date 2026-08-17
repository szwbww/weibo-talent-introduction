package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

/**
 * 任务审计保留策略配置（计划 B5 / N3-5）。独立于 [MailSchedulingProperties]：
 * 保留策略与邮件调度是不同关注点，独立前缀便于运维单独开关。
 *
 * 默认关闭（enabled=false）：调度未启用时本计划零行为变化。
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.task-retention")
data class TaskRetentionProperties(
    val enabled: Boolean = false,
    val cron: String = "0 30 4 * * *",
    val retentionDays: Long = 90,
    val batchSize: Int = 2000,
    val maxRowsPerRun: Int = 200000
)
