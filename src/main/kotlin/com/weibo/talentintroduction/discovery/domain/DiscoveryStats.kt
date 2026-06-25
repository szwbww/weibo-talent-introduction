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
    var globalBatchSeq: Int = 0
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
        filterReasons.clear()
        bySource.values.forEach { sourceStats ->
            sourceStats.filterReasons.forEach { (reason, count) ->
                filterReasons.merge(reason, count) { a, b -> a + b }
            }
        }
    }
}
