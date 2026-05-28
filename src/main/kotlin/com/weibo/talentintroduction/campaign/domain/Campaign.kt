package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("campaign")
data class Campaign(
    @Id
    val id: Long? = null,
    val campaignCode: String,
    val campaignName: String,
    val description: String?,
    val status: String = "DRAFT",
    val senderAccountId: Long,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
