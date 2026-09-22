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
    val autoEnrichmentBatchSize: Int = 100,
    /**
     * I-7（c2）：持久化采集流水线开关。默认 **false**：生产入口（controller/scheduler）在 c3 才切换，
     * 本阶段只由测试/受控服务直接调用 launch/pause/resume/tick 验收。关闭时本类不参与任何生产装配。
     */
    val pipelineEnabled: Boolean = false,
    /**
     * I-5（c2）：活跃 job（PENDING/RUNNING/RETRY_WAIT）数量的高/低水位 —— 触达高水位整页不入队、
     * 游标不推进；必须**回落到低水位且字节足够容纳一页**才恢复生产。启动校验 `0 < low < high`。
     */
    val queueHighWater: Int = 20000,
    val queueLowWater: Int = 10000,
    /**
     * I-5（c2）：队列总字节上限（实际 UTF-8 负载 + 在途结果预留共同占用），默认 1 GiB。
     * 启动校验必须至少能容纳一页（[PIPELINE_PAGE_SIZE] 条 × (metadata 上限 + 结果预留)）。
     */
    val queueMaxBytes: Long = PIPELINE_QUEUE_MAX_BYTES_DEFAULT,
    /** I-5（c2）：单条元数据负载上限；超限条目形成轻量 FAILED 诊断，绝不截断身份字段。 */
    val metadataMaxBytes: Int = 65_536,
    /** I-5（c2）：单条抽取结果上限；超限形成 FAILED/EXTRACTION_TOO_LARGE 诊断。 */
    val extractionMaxBytes: Int = 32_768,
    /** I-6（c2）：同一目标域名的最大并发 HTTP 请求数（含重定向的实际落点域名）。 */
    val perHostConcurrency: Int = 2,
    /** I-7（c2）：流水线 tick 的最小间隔；不是「睡满才醒」，到点即重新判定 owner/nextWakeAt。 */
    val pipelineTick: Duration = Duration.ofSeconds(30),
    /**
     * I-6（c2）：新队列全文提取的专用并发上限（`pipelineFetchExecutor`）。旧 `fetchConcurrency`
     * 与 `discoveryFetchExecutor` 语义逐字不变。
     */
    val pipelineFetchConcurrency: Int = 8
) {
    init {
        require(queueHighWater > 0) {
            "深度发现队列配置错误：queue-high-water 必须为正数，当前为 $queueHighWater"
        }
        require(queueLowWater > 0 && queueLowWater < queueHighWater) {
            "深度发现队列配置错误：必须 0 < queue-low-water < queue-high-water，" +
                "当前 low=$queueLowWater high=$queueHighWater"
        }
        require(queueMaxBytes > 0) {
            "深度发现队列配置错误：queue-max-bytes 必须为正数，当前为 $queueMaxBytes"
        }
        require(metadataMaxBytes > 0 && extractionMaxBytes > 0) {
            "深度发现队列配置错误：metadata-max-bytes/extraction-max-bytes 必须为正数，" +
                "当前 metadata=$metadataMaxBytes extraction=$extractionMaxBytes"
        }
        require(perHostConcurrency > 0) {
            "深度发现队列配置错误：per-host-concurrency 必须为正数，当前为 $perHostConcurrency"
        }
        require(pipelineFetchConcurrency > 0) {
            "深度发现队列配置错误：pipeline-fetch-concurrency 必须为正数，当前为 $pipelineFetchConcurrency"
        }
        require(!pipelineTick.isZero && !pipelineTick.isNegative) {
            "深度发现队列配置错误：pipeline-tick 必须为正数，当前为 $pipelineTick"
        }
        // I-5：字节上限必须至少容纳一整页（否则任何一页都永远入不了队 = 静默死锁）。
        val onePageBytes = PIPELINE_PAGE_SIZE.toLong() *
            (metadataMaxBytes.toLong() + PIPELINE_RESERVED_RESULT_BYTES)
        require(queueMaxBytes >= onePageBytes) {
            "深度发现队列配置错误：queue-max-bytes=$queueMaxBytes 小于一页所需 $onePageBytes" +
                "（$PIPELINE_PAGE_SIZE 条 × (metadata $metadataMaxBytes + 结果预留 $PIPELINE_RESERVED_RESULT_BYTES)）"
        }
        require(extractionMaxBytes.toLong() < queueMaxBytes) {
            "深度发现队列配置错误：extraction-max-bytes 必须小于 queue-max-bytes"
        }
    }
}

/** I-6（c2）：来源生产者每轮每源一页的固定页大小（100 篇 / 100 条记录）。 */
const val PIPELINE_PAGE_SIZE: Int = 100

/** I-5（c2）：队列总字节上限的默认值（1 GiB）。 */
const val PIPELINE_QUEUE_MAX_BYTES_DEFAULT: Long = 1_073_741_824L

/**
 * I-5（c2）：每条**尚未抽取**的活跃 job 在入队时预留的结果空间（32 KiB）；保存抽取结果时
 * 预占转成实际字节，因此「队列满」不会连在途结果也写不进去。
 */
const val PIPELINE_RESERVED_RESULT_BYTES: Long = 32_768L
