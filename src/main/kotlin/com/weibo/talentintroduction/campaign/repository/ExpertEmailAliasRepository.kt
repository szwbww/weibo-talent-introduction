package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import org.springframework.data.repository.CrudRepository
import org.springframework.data.jdbc.repository.query.Query

interface ExpertEmailAliasRepository : CrudRepository<ExpertEmailAlias, Long> {
    @Query("SELECT * FROM expert_email_alias WHERE expert_contact_id IN (:contactIds) ORDER BY created_at ASC, id ASC")
    fun findAllByContactIds(contactIds: Collection<Long>): List<ExpertEmailAlias>

    fun findByNormalizedEmail(normalizedEmail: String): ExpertEmailAlias?

    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<ExpertEmailAlias>

    fun existsByNormalizedEmail(normalizedEmail: String): Boolean

    fun existsByExpertContactIdAndNormalizedEmail(expertContactId: Long, normalizedEmail: String): Boolean
}
