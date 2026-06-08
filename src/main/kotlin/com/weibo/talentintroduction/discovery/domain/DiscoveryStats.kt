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
    val filterReasons: MutableMap<String, Int> = mutableMapOf()
)
