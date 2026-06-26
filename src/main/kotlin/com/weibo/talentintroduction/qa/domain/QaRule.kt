package com.weibo.talentintroduction.qa.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("qa_rule")
data class QaRule(
    @Id
    val id: Long? = null,
    val categoryId: Long,
    val keywords: String,
    val matchMode: String = "ANY",
    val priority: Int = 100,
    val replySubject: String?,
    val replyBody: String,
    val displayName: String? = null,
    val sectionTitle: String? = null,
    val autoReplyEnabled: Boolean = true,
    val handoffRequired: Boolean = false,
    val supersedesChildren: Boolean = false,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
