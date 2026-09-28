package com.weibo.talentintroduction.discovery.domain

/**
 * c9（I-1）：单个来源的计量单位。论文源按「篇」，ORCID 按「记录」。
 * `papersSearched` 对 ORCID 的历史语义本来就是记录数，本轮保持兼容，只把两者在汇总里分列，
 * 不追溯重算历史。
 */
enum class SourceUnit { PAPER, RECORD }

/**
 * I-5：来源的**重试观察对象**。它只作观察：随 `details_json`/`result_summary` 序列化给运营端看，
 * 不用于启动恢复、不写专家 ES、也不是第二份恢复依据（恢复只用本轮内存中的上下文）。
 *
 * - [round] 是已安排的延迟恢复组序号（1..[maxRounds]）；
 * - [nextRetryAt] 是 ISO-8601 UTC 的计划重试时刻，**非空表示仍待执行**；真正开始、取消、超时或
 *   结束都会把它清空（轮次与原因保留用于审计）；
 * - [reason] 是脱敏错误码（`REMOTE_TLS_HANDSHAKE`/`TIMEOUT`/`NETWORK_IO`/`HTTP_5xx`），不含 URL 或密钥。
 */
data class SourceRetryState(
    val round: Int,
    val maxRounds: Int,
    val nextRetryAt: String?,
    val reason: String
)

data class SourceStats(
    val sourceName: String,
    val extractionMethod: String,
    var papersSearched: Int = 0,
    var papersSkippedNoId: Int = 0,
    var fulltextAttempted: Int = 0,
    var fulltextObtained: Int = 0,
    var pdfDownloadFailed: Int = 0,
    var pdfParseFailed: Int = 0,
    var noEmailInFulltext: Int = 0,
    var authorsExtracted: Int = 0,
    var emailsValid: Int = 0,
    var emailsRejected: Int = 0,
    var duplicates: Int = 0,
    var dedupErrors: Int = 0,
    var indexed: Int = 0,
    var rawWriteFailed: Int = 0,
    var promoted: Int = 0,
    var promotionFailed: Int = 0,
    var filtered: Int = 0,
    val filterReasons: MutableMap<String, Int> = mutableMapOf(),
    val failureReasons: MutableMap<String, Int> = mutableMapOf(),
    var elapsedMs: Long = 0,
    var apiRequests: Int = 0,
    /** I-1: 本次运行是否仍有未消费的页；false 表示已穷尽，true 表示存在可续跑工作。 */
    var pendingWork: Boolean = false,
    /** I-4: 本次运行的终止性搜索层错误次数；重试不计入，一次运行最多累计一次。 */
    var sourceFailureCount: Int = 0,
    /**
     * c9（I-2）：本源本次运行分配到的额度（论文数或 ORCID 记录数）。
     * 0 表示本次没有分到额度（该来源不发请求），绝不代表无限量。
     */
    var runBudget: Int = 0,
    /** c9（I-1）：本源本次运行的计量单位，汇总时据此把论文数与 ORCID 记录数分列。 */
    var unit: SourceUnit = SourceUnit.PAPER,
    /** I-1: 本次运行的停止原因，取值见 DiscoveryStopReason。 */
    var stopReason: String? = null,
    /** I-5: 唯一的重试观察对象（等待时的轮次/计划时间/原因）；null 表示本源本轮没有待恢复项。 */
    var retry: SourceRetryState? = null
)
