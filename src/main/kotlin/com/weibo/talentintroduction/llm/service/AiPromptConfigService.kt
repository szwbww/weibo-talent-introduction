package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.domain.AiPromptConfig
import com.weibo.talentintroduction.llm.repository.AiPromptConfigRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class AiPromptConfigDto(
    val freeFormSystemPrompt: String?,
    val constraints: String?,
    val updatedAt: String?
)

@Service
class AiPromptConfigService(
    private val repository: AiPromptConfigRepository
) {
    fun getRaw(): AiPromptConfig =
        repository.findById(1L).orElse(AiPromptConfig())

    fun getDto(): AiPromptConfigDto {
        val raw = getRaw()
        return AiPromptConfigDto(
            freeFormSystemPrompt = raw.freeFormSystemPrompt,
            constraints = raw.constraints,
            updatedAt = raw.updatedAt?.toString()
        )
    }

    fun getEffectiveFreeFormSystemPrompt(defaultPrompt: String): String {
        val raw = getRaw()
        val custom = raw.freeFormSystemPrompt?.trim().orEmpty()
        if (custom.isBlank()) {
            return defaultPrompt
        }
        val constraintLines = raw.constraints
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()
        return buildString {
            append(custom)
            if (constraintLines.isNotEmpty()) {
                appendLine()
                appendLine("Additional constraints:")
                constraintLines.forEach { appendLine("- $it") }
            }
        }
    }

    fun update(freeFormSystemPrompt: String?, constraints: String?): AiPromptConfigDto {
        val existing = getRaw()
        val saved = repository.save(
            existing.copy(
                freeFormSystemPrompt = freeFormSystemPrompt,
                constraints = constraints,
                updatedAt = LocalDateTime.now()
            )
        )
        return AiPromptConfigDto(
            freeFormSystemPrompt = saved.freeFormSystemPrompt,
            constraints = saved.constraints,
            updatedAt = saved.updatedAt?.toString()
        )
    }
}
