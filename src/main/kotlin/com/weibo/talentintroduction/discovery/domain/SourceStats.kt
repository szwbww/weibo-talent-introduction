package com.weibo.talentintroduction.discovery.domain

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
    var apiRequests: Int = 0
)
