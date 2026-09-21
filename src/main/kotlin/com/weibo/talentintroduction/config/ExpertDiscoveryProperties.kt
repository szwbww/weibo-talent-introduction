package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery")
data class ExpertDiscoveryProperties(
    val enabled: Boolean = true,
    val cron: String = "-",
    /**
     * I-1（09）：**全部论文源**的单次运行上限（全局 cap），与论文页大小、作者补全批量相互独立。
     * 启动时校验：该值必须能覆盖各启用来源的基础份额（每源至少一页，见页大小），
     * 否则直接拒绝启动而不是静默饿死后来源。`0` 不是无限量，是配置错误。
     */
    val maxPapersPerRun: Int = 15000,
    /** I-1（09）：全部来源的作者总数防护上限；ORCID 记录同样受它约束。 */
    val maxAuthorsPerRun: Int = 20000,
    val includeRawScan: Boolean = true,
    val fetchConcurrency: Int = 4,
    /**
     * I-3（09）：单次发现的运行级时间预算。到点即按 `TIME_BUDGET` 停止并保存进入当前页的检查点，
     * 不推进未消费的半页。4 小时是主方案的上线目标值，不是「无限运行」；必须为正数。
     */
    val timeBudget: Duration = Duration.ofHours(4),
    /**
     * I-2（08）：发现后自动补全 worker 的开关。默认 **false**，验收后在服务器开启；
     * 关闭时 worker 仍可注入但什么都不做，且不影响发现与既有三个手动 scope。
     */
    val autoEnrichmentEnabled: Boolean = false,
    /**
     * I-2（08）：worker 单次领取的到期任务上限。语义上界是 100（100 只是一批，不是每日总量），
     * 实际生效值由 worker 侧再钳制到 1..100；c9 的额度旋钮读同一个 properties 对象。
     */
    val autoEnrichmentBatchSize: Int = 100
)
