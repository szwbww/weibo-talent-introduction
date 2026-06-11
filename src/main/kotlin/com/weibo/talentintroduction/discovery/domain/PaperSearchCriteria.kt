package com.weibo.talentintroduction.discovery.domain

data class PaperSearchCriteria(
    val keywords: List<String> = emptyList(),
    val affiliationKeywords: List<String> = emptyList(),
    val excludeCountries: List<String> = listOf("CN"),
    val publicationYearFrom: Int = 2020,
    val publicationYearTo: Int = 2026,
    val openAccessOnly: Boolean = true,
    val pageSize: Int = 100,
    val cursor: String? = null,
    val sources: List<String> = emptyList()
)
