package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult

interface AcademicDataSource {
    val sourceName: String
    fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult
}
