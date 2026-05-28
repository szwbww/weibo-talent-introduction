package com.weibo.talentintroduction.document.repository

import com.weibo.talentintroduction.document.domain.ExpertDocument
import org.springframework.data.repository.CrudRepository

interface ExpertDocumentRepository : CrudRepository<ExpertDocument, Long> {
    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<ExpertDocument>
}
