package com.weibo.talentintroduction.reply.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("reply_snippet")
data class ReplySnippet(
    @Id
    val id: Long? = null,
    val snippetType: String,
    val content: String,
    val displayOrder: Int = 100,
    val isDefault: Boolean = false,
    val enabled: Boolean = true,
    val variantGroup: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
