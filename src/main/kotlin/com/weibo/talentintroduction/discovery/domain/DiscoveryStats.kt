package com.weibo.talentintroduction.discovery.domain

data class DiscoveryStats(
    var totalPapers: Int = 0,
    var noEmailPapers: Int = 0,
    var totalAuthors: Int = 0,
    var emailRejected: Int = 0,
    var duplicates: Int = 0,
    var indexed: Int = 0,
    var rawWriteFailed: Int = 0,
    var promoted: Int = 0,
    var promotionFailed: Int = 0,
    var filtered: Int = 0,
    var dedupErrors: Int = 0,
    val filterReasons: MutableMap<String, Int> = mutableMapOf(),
    val errors: MutableList<String> = mutableListOf(),
    val bySource: MutableMap<String, SourceStats> = mutableMapOf(),
    var globalBatchSeq: Int = 0,
    /** I-3: 本次任务真正启动过运行的来源数量（每个 bySource 条目对应一个已尝试来源）。 */
    var attemptedSources: Int = 0,
    /** I-3: 以终止性搜索错误结束的来源数量，用于区分「部分来源成功」与「全源失败」。 */
    var failedSources: Int = 0,
    /** I-4: 终止性源错误次数；计入 failure_count，但不代表某位专家失败。 */
    var sourceFailures: Int = 0,
    /** I-3: 未穷尽、仍有可续跑工作的来源数量（预算/时长/限额导致的提前结束）。 */
    var pendingSources: Int = 0
) {
    fun nextBatchSeq(): Int = ++globalBatchSeq

    fun getOrCreateSourceStats(sourceName: String, extractionMethod: String): SourceStats {
        return bySource.getOrPut(sourceName) { SourceStats(sourceName, extractionMethod) }
    }

    fun refreshGlobalCounts() {
        totalPapers = bySource.values.sumOf { it.papersSearched }
        noEmailPapers = bySource.values.sumOf { it.papersSkippedNoId + it.noEmailInFulltext }
        totalAuthors = bySource.values.sumOf { it.authorsExtracted }
        emailRejected = bySource.values.sumOf { it.emailsRejected }
        duplicates = bySource.values.sumOf { it.duplicates }
        indexed = bySource.values.sumOf { it.indexed }
        rawWriteFailed = bySource.values.sumOf { it.rawWriteFailed }
        promoted = bySource.values.sumOf { it.promoted }
        promotionFailed = bySource.values.sumOf { it.promotionFailed }
        filtered = bySource.values.sumOf { it.filtered }
        dedupErrors = bySource.values.sumOf { it.dedupErrors }
        attemptedSources = bySource.size
        failedSources = bySource.values.count { it.sourceFailureCount > 0 }
        sourceFailures = bySource.values.sumOf { it.sourceFailureCount }
        pendingSources = bySource.values.count { it.pendingWork }
        filterReasons.clear()
        bySource.values.forEach { sourceStats ->
            sourceStats.filterReasons.forEach { (reason, count) ->
                filterReasons.merge(reason, count) { a, b -> a + b }
            }
        }
    }
}
