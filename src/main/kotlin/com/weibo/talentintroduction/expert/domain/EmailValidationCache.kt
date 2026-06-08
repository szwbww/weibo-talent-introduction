package com.weibo.talentintroduction.expert.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("email_validation_cache")
data class EmailValidationCache(
    @Id
    val id: Long? = null,
    val email: String,
    val domain: String,
    val formatValid: Boolean = false,
    val disposable: Boolean = false,
    val mxValid: Boolean? = null,
    val verifiedLevel: Int = 0,
    val rejectReason: String? = null,
    val verifiedAt: LocalDateTime,
    val expiresAt: LocalDateTime
) {
    fun isExpired(): Boolean = LocalDateTime.now().isAfter(expiresAt)
}
