package com.weibo.talentintroduction.document.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("expert_analysis_result")
data class ExpertAnalysisResult(
    @Id
    val id: Long? = null,
    val expertContactId: Long,
    val fieldKey: String,
    val fieldLabel: String,
    val value: String,
    val sourceAttachmentId: Long? = null,
    val sourceExcerpt: String? = null,
    val excerptVerified: Boolean = false,
    val displayOrder: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
