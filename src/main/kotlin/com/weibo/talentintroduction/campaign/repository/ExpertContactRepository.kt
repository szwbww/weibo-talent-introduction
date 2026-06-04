package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.jdbc.repository.query.Query
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

    fun findAllByExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
        expertName: String,
        expertEmail: String
    ): List<ExpertContact>

    fun findAllByOrcidIdContainingIgnoreCaseOrExpertNameContainingIgnoreCaseOrExpertEmailContainingIgnoreCaseOrderByUpdatedAtDesc(
        orcidId: String,
        expertName: String,
        expertEmail: String
    ): List<ExpertContact>

    @Query("""
        SELECT * FROM expert_contact
        WHERE (:campaignId IS NULL OR campaign_id = :campaignId)
          AND (:status IS NULL OR current_status = :status)
          AND (:operatorStatus IS NULL OR operator_status = :operatorStatus)
          AND (:needsAttention IS NULL OR needs_manual_attention = :needsAttention)
        ORDER BY updated_at DESC
    """)
    fun findFilteredContacts(
        campaignId: Long?,
        status: String?,
        operatorStatus: String?,
        needsAttention: Boolean?
    ): List<ExpertContact>
}
