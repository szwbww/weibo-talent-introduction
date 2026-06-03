package com.weibo.talentintroduction.expert.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_application_promotion")
data class ExpertApplicationPromotion(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val orcidId: String,
    val sourceInboundId: Long? = null,
    val triggeredBy: String,
    val promotionStatus: String,
    val fromLevel: String = "CANDIDATE",
    val toLevel: String = "APPLICATION",
    val errorMessage: String? = null,
    val operatorName: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
