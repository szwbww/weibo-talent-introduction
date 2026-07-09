package com.weibo.talentintroduction.variant.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("content_variant")
data class ContentVariant(
    @Id
    val id: Long? = null,
    val ownerType: String,
    val ownerId: Long,
    val variantOrder: Int = 100,
    val content: String,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

object ContentVariantOwnerType {
    const val QA_RULE = "QA_RULE"
    const val REPLY_SNIPPET = "REPLY_SNIPPET"

    private val KNOWN = setOf(QA_RULE, REPLY_SNIPPET)

    fun isKnown(ownerType: String): Boolean = ownerType in KNOWN
}
