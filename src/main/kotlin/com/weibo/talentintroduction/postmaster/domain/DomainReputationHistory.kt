package com.weibo.talentintroduction.postmaster.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Table("domain_reputation_history")
data class DomainReputationHistory(
    @Id
    val id: Long? = null,
    val domain: String,
    val reportDate: LocalDate,
    val spamRate: Double? = null,
    val domainReputation: String? = null,
    val spfSuccessRate: Double? = null,
    val dkimSuccessRate: Double? = null,
    val dmarcSuccessRate: Double? = null,
    val rawJson: String? = null,
    val collectedAt: LocalDateTime? = null
)
