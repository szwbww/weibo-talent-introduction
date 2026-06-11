package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.PaperSearchResult

interface AcademicDataSource {
    val sourceName: String
    val emailExtractionMethod: String
    val maxPapersPerSource: Int get() = 500
    fun searchPapers(criteria: PaperSearchCriteria): PaperSearchResult
    fun extractAuthorEmails(paper: PaperMetadata): EmailExtractionOutcome
}
