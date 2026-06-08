package com.weibo.talentintroduction.expert.domain

data class EmailValidationResult(
    val level: Int,
    val valid: Boolean,
    val rejectReason: String? = null
)
