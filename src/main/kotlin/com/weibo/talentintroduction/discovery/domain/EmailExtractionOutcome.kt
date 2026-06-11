package com.weibo.talentintroduction.discovery.domain

data class EmailExtractionOutcome @JvmOverloads constructor(
    val emails: List<AuthorEmail>,
    val methodUsed: String?,
    val failureReason: String? = null,
    val httpRequests: Int = 0
)
