package com.weibo.talentintroduction.qa.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("qa_category")
data class QaCategory(
    @Id
    val id: Long? = null,
    val categoryCode: String,
    val categoryName: String,
    val description: String?,
    val composeOrder: Int = 100,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
