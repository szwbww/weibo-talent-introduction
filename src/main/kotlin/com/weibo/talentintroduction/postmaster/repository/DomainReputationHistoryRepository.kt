package com.weibo.talentintroduction.postmaster.repository

import com.weibo.talentintroduction.postmaster.domain.DomainReputationHistory
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate

interface DomainReputationHistoryRepository : CrudRepository<DomainReputationHistory, Long> {
    fun findByDomainAndReportDate(domain: String, reportDate: LocalDate): DomainReputationHistory?

    fun findFirstByDomainOrderByReportDateDesc(domain: String): DomainReputationHistory?

    @Query(
        """
        SELECT * FROM domain_reputation_history
         WHERE domain = :domain
         ORDER BY report_date DESC
         LIMIT :limit
        """
    )
    fun findByDomainOrderByReportDateDesc(domain: String, limit: Int): List<DomainReputationHistory>

    @Query(
        """
        SELECT * FROM domain_reputation_history
         WHERE domain = :domain
           AND report_date >= :since
         ORDER BY report_date DESC
        """
    )
    fun findByDomainSince(domain: String, since: LocalDate): List<DomainReputationHistory>

    @Query("SELECT DISTINCT domain FROM domain_reputation_history ORDER BY domain")
    fun findDistinctDomains(): List<String>
}
