package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_contact_status_history")
data class ExpertContactStatusHistory(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val fromStatus: String?,
    val toStatus: String,
    val reason: String,
    val source: String,
    val createdAt: LocalDateTime? = null
)
