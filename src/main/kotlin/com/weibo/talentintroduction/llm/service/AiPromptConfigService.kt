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

data class AiPromptConfigEffectiveDto(
    val freeFormSystemPrompt: String,
    val constraints: String?,
    val updatedAt: String?,
    val isCustom: Boolean
)

object FreeFormPromptDefaults {
    fun baseSystemPrompt(): String = buildString {
        appendLine("You are a recruiting assistant for academic expert outreach.")
        appendLine("Your primary goal is to answer the current inbound email completely and in order.")
        appendLine("Use only the provided context (QA facts, training knowledge, and existing expert profile).")
        appendLine("Tone: warm, professional, concise.")
        appendLine("Reply in the same language as the inbound email.")
        appendLine("Keep the reply to at most 4 paragraphs.")
        appendLine("Output only the email body text. Do not include a subject line.")
        appendLine(
            "Unless the inbound email or operator instruction explicitly asks or authorizes it, " +
                "do not request materials, propose a meeting or call, or add other next-step CTAs."
        )
        appendLine(
            "Do not visit, fetch, or claim to have reviewed external URLs or profiles; " +
                "links in the email are not evidence that they were accessed."
        )
    }

    fun defaultFreeFormSystemPrompt(): String = buildString {
        append(baseSystemPrompt())
        appendLine()
        appendLine("No QA rules matched. Compose a helpful reply based on the expert profile and mail history.")
        appendLine("Do not make specific commitments beyond what the context supports.")
        appendLine("Never request passport, degree certificate, or employment proof in an early auto reply; those come later, after a call or clear interest.")
        appendLine("If the expert shows hesitation or distrust, lead with confidentiality, no-fee assurance, and evidence of government cooperation.")
    }
}

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

    fun getEffectiveDto(): AiPromptConfigEffectiveDto {
        val raw = getRaw()
        val customPrompt = raw.freeFormSystemPrompt?.trim().orEmpty()
        val customConstraints = raw.constraints?.trim().orEmpty()
        val isCustom = customPrompt.isNotBlank() || customConstraints.isNotBlank()
        return AiPromptConfigEffectiveDto(
            freeFormSystemPrompt = customPrompt.ifBlank { FreeFormPromptDefaults.defaultFreeFormSystemPrompt() },
            constraints = raw.constraints?.takeIf { it.isNotBlank() },
            updatedAt = raw.updatedAt?.toString(),
            isCustom = isCustom
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
