package com.weibo.talentintroduction.llm.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("ai_prompt_config")
data class AiPromptConfig(
    @Id
    val id: Long = 1,
    val freeFormSystemPrompt: String? = null,
    val constraints: String? = null,
    val updatedAt: LocalDateTime? = null
)
