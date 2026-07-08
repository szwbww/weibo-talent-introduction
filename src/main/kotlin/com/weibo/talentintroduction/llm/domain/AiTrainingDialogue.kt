package com.weibo.talentintroduction.llm.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("ai_training_dialogue")
data class AiTrainingDialogue(
    @Id
    val id: Long? = null,
    val title: String,
    val sourceRef: String,
    val keywords: String? = null,
    val turnsJson: String,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
