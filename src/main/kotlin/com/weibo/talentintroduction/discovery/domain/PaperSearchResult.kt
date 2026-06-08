package com.weibo.talentintroduction.discovery.domain

data class PaperSearchResult(
    val papers: List<PaperMetadata>,
    val nextCursor: String?,
    val totalResults: Long
)
