package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.repository.CrudRepository

interface ExpertContactRepository : CrudRepository<ExpertContact, Long> {
    fun existsByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): Boolean

    fun findFirstByExpertEmailOrderByUpdatedAtDesc(expertEmail: String): ExpertContact?

    fun findFirstByOrcidIdOrderByUpdatedAtDesc(orcidId: String): ExpertContact?

    fun findAllByOrderByUpdatedAtDesc(): List<ExpertContact>

    fun findAllByCurrentStatusOrderByUpdatedAtDesc(currentStatus: String): List<ExpertContact>

    fun findAllByCampaignIdOrderByUpdatedAtDesc(campaignId: Long): List<ExpertContact>

    fun findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(
        campaignId: Long,
        currentStatus: String
    ): List<ExpertContact>
}
