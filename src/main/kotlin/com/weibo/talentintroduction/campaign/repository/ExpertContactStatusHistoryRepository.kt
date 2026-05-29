package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContactStatusHistory
import org.springframework.data.repository.CrudRepository

interface ExpertContactStatusHistoryRepository : CrudRepository<ExpertContactStatusHistory, Long> {
    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<ExpertContactStatusHistory>
}
