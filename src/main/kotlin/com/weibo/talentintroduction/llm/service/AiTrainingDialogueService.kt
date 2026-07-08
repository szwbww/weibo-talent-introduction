package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.llm.domain.AiTrainingDialogue
import com.weibo.talentintroduction.llm.repository.AiTrainingDialogueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private const val MAX_DIALOGUE_CHARS = 2500
private const val MAX_FEW_SHOT_TOTAL_CHARS = 6000

data class SelectedDialogueFewShot(
    val sourceRef: String,
    val messages: List<LlmChatMessage>
)

data class AiTrainingDialogueView(
    val sourceRef: String,
    val title: String,
    val keywords: String?,
    val turnCount: Int,
    val enabled: Boolean
)

@Service
class AiTrainingDialogueService(
    private val repository: AiTrainingDialogueRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AiTrainingDialogueService::class.java)

    fun selectRelevantDialogues(inboundText: String, max: Int = 2): List<SelectedDialogueFewShot> {
        val inboundLower = inboundText.lowercase()
        val ranked = repository.findAllByEnabledTrue()
            .map { dialogue ->
                val score = scoreKeywords(dialogue.keywords, inboundLower)
                dialogue to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<AiTrainingDialogue, Int>> { it.second }
                    .thenBy { it.first.id ?: Long.MAX_VALUE }
            )

        var totalChars = 0
        val selected = mutableListOf<SelectedDialogueFewShot>()
        for ((dialogue, _) in ranked) {
            if (selected.size >= max) {
                break
            }
            val messages = renderDialogueTurns(dialogue.turnsJson, dialogue.sourceRef) ?: continue
            val charCount = messages.sumOf { it.content.length }
            if (charCount == 0) {
                continue
            }
            if (totalChars + charCount > MAX_FEW_SHOT_TOTAL_CHARS) {
                break
            }
            selected += SelectedDialogueFewShot(sourceRef = dialogue.sourceRef, messages = messages)
            totalChars += charCount
        }
        return selected
    }

    fun listViews(): List<AiTrainingDialogueView> =
        repository.findAllByOrderByIdAsc().map { dialogue ->
            AiTrainingDialogueView(
                sourceRef = dialogue.sourceRef,
                title = dialogue.title,
                keywords = dialogue.keywords,
                turnCount = countTurns(dialogue.turnsJson, dialogue.sourceRef),
                enabled = dialogue.enabled
            )
        }

    internal fun countTurns(turnsJson: String, sourceRef: String): Int {
        return try {
            val turns: List<DialogueTurn> = objectMapper.readValue(turnsJson)
            turns.size
        } catch (ex: Exception) {
            logger.warn("Failed to count dialogue turns for {}: {}", sourceRef, ex.message)
            0
        }
    }

    internal fun scoreKeywords(keywords: String?, inboundLower: String): Int {
        if (keywords.isNullOrBlank()) {
            return 0
        }
        return keywords.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && inboundLower.contains(it) }
            .size
    }

    internal fun renderDialogueTurns(turnsJson: String, sourceRef: String): List<LlmChatMessage>? {
        return try {
            val turns: List<DialogueTurn> = objectMapper.readValue(turnsJson)
            val messages = turns.mapNotNull { turn ->
                when (turn.role.uppercase()) {
                    "EXPERT" -> LlmChatMessage(role = "user", content = turn.text)
                    "AGENT" -> LlmChatMessage(role = "assistant", content = turn.text)
                    else -> null
                }
            }
            truncateMessages(messages, MAX_DIALOGUE_CHARS)
        } catch (ex: Exception) {
            logger.warn("Failed to parse dialogue turns for {}: {}", sourceRef, ex.message)
            null
        }
    }

    internal fun truncateMessages(messages: List<LlmChatMessage>, maxChars: Int): List<LlmChatMessage> {
        var used = 0
        val result = mutableListOf<LlmChatMessage>()
        for (message in messages) {
            val remaining = maxChars - used
            if (remaining <= 0) {
                break
            }
            val content = if (message.content.length <= remaining) {
                message.content
            } else {
                message.content.take(remaining)
            }
            if (content.isEmpty()) {
                break
            }
            result += message.copy(content = content)
            used += content.length
        }
        return result
    }

    private data class DialogueTurn(
        val role: String,
        val text: String
    )
}
