package com.weibo.talentintroduction.campaign.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_email_alias")
data class ExpertEmailAlias(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val email: String,
    val normalizedEmail: String,
    val source: String = "MANUAL_BIND",
    val verified: Boolean = true,
    val createdAt: LocalDateTime? = null
)
