package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery")
data class ExpertDiscoveryProperties(
    val enabled: Boolean = true,
    val cron: String = "-",
    val maxPapersPerRun: Int = 500,
    val maxAuthorsPerRun: Int = 2000,
    val includeRawScan: Boolean = true,
    val fetchConcurrency: Int = 4,
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
