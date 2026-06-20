package com.weibo.talentintroduction.mail.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("email_suppression")
data class EmailSuppression(
    @Id val id: Long? = null,
    val email: String,
    val source: String,
    val reason: String?,
    val createdAt: LocalDateTime? = null
)
