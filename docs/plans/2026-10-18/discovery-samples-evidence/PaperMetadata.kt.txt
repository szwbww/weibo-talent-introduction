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
    val downloadUrl: String? = null,
    /**
     * c10（I-1）：除首选 [downloadUrl] 之外的其他**开放**全文地址（其他去重 OA PDF），保持来源返回顺序。
     * 只在首选失效/缺失时按序回退；默认空列表，其他来源的构造保持原样可用。
     */
    val candidateDownloadUrls: List<String> = emptyList()
)
