package com.weibo.talentintroduction.document.repository

import com.weibo.talentintroduction.document.domain.ExpertAnalysisResult
import org.springframework.data.repository.CrudRepository

interface ExpertAnalysisResultRepository : CrudRepository<ExpertAnalysisResult, Long> {
    fun findAllByExpertContactIdOrderByDisplayOrderAsc(expertContactId: Long): List<ExpertAnalysisResult>

    fun deleteAllByExpertContactId(expertContactId: Long)
}
