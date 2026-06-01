package com.weibo.talentintroduction.campaign.repository

import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import org.springframework.data.repository.CrudRepository

interface ExpertEmailAliasRepository : CrudRepository<ExpertEmailAlias, Long> {
    fun findByNormalizedEmail(normalizedEmail: String): ExpertEmailAlias?

    fun findAllByExpertContactIdOrderByCreatedAtAsc(expertContactId: Long): List<ExpertEmailAlias>

    fun existsByNormalizedEmail(normalizedEmail: String): Boolean

    fun existsByExpertContactIdAndNormalizedEmail(expertContactId: Long, normalizedEmail: String): Boolean
}
