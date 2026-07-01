package com.weibo.talentintroduction.llm.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("ai_training_qa")
data class AiTrainingQa(
    @Id
    val id: Long? = null,
    val topic: String,
    val question: String? = null,
    val answer: String,
    val keywords: String? = null,
    val source: String,
    val sourceRef: String,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
