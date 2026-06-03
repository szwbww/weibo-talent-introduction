package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_contact")
data class ExpertContact(
    @Id
    val id: Long? = null,
    val campaignId: Long,
    val orcidId: String,
    val expertEmail: String,
    val expertName: String?,
    val currentStatus: String = "NEW",
    val lastMailAt: LocalDateTime? = null,
    val lastReplyAt: LocalDateTime? = null,
    val manualHandoffRequired: Boolean = false,
    val closedReason: String? = null,
    val autoReplyEnabled: Boolean = true,
    val firstReplyAt: LocalDateTime? = null,
    val applicationIndexed: Boolean = false,
    val currentIndexLevel: String = "CANDIDATE",
    val needsManualAttention: Boolean = false,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
