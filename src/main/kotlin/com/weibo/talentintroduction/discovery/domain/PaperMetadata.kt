package com.weibo.talentintroduction.discovery.domain

data class PaperMetadata(
    val pmcId: String?,
    val pmid: String?,
    val doi: String?,
    val title: String,
    val pubYear: Int,
    val journal: String?,
    val authors: List<PaperAuthor>,
    val source: String,
    val fullText: String? = null,
    val downloadUrl: String? = null
)
