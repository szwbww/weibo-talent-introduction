package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDateTime

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

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :boundAt
         WHERE id = :id
    """)
    fun updateBindingById(id: Long, accountCode: String?, boundAt: LocalDateTime?): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :accountCode,
               sender_account_bound_at = :changedAt,
               sender_account_changed = true,
               sender_account_changed_at = :changedAt
         WHERE id = :id
    """)
    fun rebindSenderAccountById(id: Long, accountCode: String, changedAt: LocalDateTime): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET bound_sender_account_code = :toAccountCode,
               sender_account_bound_at = :migratedAt
         WHERE bound_sender_account_code = :fromAccountCode
    """)
    fun migrateBindingByAccount(
        fromAccountCode: String,
        toAccountCode: String,
        migratedAt: LocalDateTime
    ): Int

    @Modifying
    @Query("""
        UPDATE expert_contact
           SET sender_account_changed = false,
               sender_account_changed_at = NULL
         WHERE id = :id
    """)
    fun clearSenderChangeMarkById(id: Long): Int

    fun findAllByBoundSenderAccountCode(boundSenderAccountCode: String): List<ExpertContact>

    @Query("""
        SELECT bound_sender_account_code AS account_code, COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code
    """)
    fun countBindingsByAccount(): List<AccountBindingCount>

    @Query("""
        SELECT bound_sender_account_code AS account_code,
               COALESCE(LOWER(TRIM(country)), '') AS distribution_key,
               COUNT(*) AS bound_count
          FROM expert_contact
         WHERE bound_sender_account_code IS NOT NULL
           AND bound_sender_account_code <> ''
           AND bound_sender_account_code <> 'SIMULATOR_NOOP'
         GROUP BY bound_sender_account_code, COALESCE(LOWER(TRIM(country)), '')
    """)
    fun countBindingsByAccountAndCountry(): List<AccountCountryBindingCount>
}

data class AccountBindingCount(val accountCode: String, val boundCount: Long)

data class AccountCountryBindingCount(
    val accountCode: String,
    val distributionKey: String?,
    val boundCount: Long
)
