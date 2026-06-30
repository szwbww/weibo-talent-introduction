package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface ExpertContactRepository : CrudRepository<ExpertContact, Long> {
    fun existsByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): Boolean
    fun existsByOrcidId(orcidId: String): Boolean
    fun findAllByCurrentStatus(currentStatus: String): List<ExpertContact>
    fun findByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): ExpertContact?

    fun findFirstByExpertEmailOrderByUpdatedAtDesc(expertEmail: String): ExpertContact?

    fun findFirstByOrcidIdOrderByUpdatedAtDesc(orcidId: String): ExpertContact?

    fun findByOrcidIdIn(orcidIds: List<String>): List<ExpertContact>

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
          AND (:replyMode IS NULL
               OR (:replyMode = 'MANUAL' AND (auto_reply_enabled = false OR current_status = 'MANUAL_HANDOFF'))
               OR (:replyMode = 'AUTO'   AND auto_reply_enabled = true AND current_status <> 'MANUAL_HANDOFF'))
          AND (:followUpMarked IS NULL OR follow_up_marked = :followUpMarked)
        ORDER BY
          CASE WHEN :followUpMarked = true THEN follow_up_marked_at END DESC,
          updated_at DESC
    """)
    fun findFilteredContacts(
        campaignId: Long?,
        status: String?,
        operatorStatus: String?,
        needsAttention: Boolean?,
        replyMode: String? = null,
        followUpMarked: Boolean? = null
    ): List<ExpertContact>

    @Modifying
    @Query("UPDATE expert_contact SET country = :country WHERE id = :id")
    fun updateCountryById(id: Long, country: String?): Int
}
