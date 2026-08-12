package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("unsubscribe_token")
data class UnsubscribeToken(
    @Id val id: Long? = null,
    val email: String,
    val token: String,
    val createdAt: LocalDateTime? = null
)
