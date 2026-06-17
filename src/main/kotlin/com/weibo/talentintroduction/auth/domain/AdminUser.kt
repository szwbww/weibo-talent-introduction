package com.weibo.talentintroduction.auth.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("admin_user")
data class AdminUser(
    @Id
    val id: Long? = null,
    val username: String,
    val passwordHash: String,
    val mustChangePassword: Boolean = true,
    val lastLoginAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
