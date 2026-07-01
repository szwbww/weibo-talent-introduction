package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.LocalDateTime

const val AUTO_EXTRACTED = "AUTO_EXTRACTED"

data class ExtractionSummary(
    val processed: Int,
    val upserted: Int,
    val skipped: Int
)

data class ExtractedQaItem(
    val topic: String,
    val question: String? = null,
    val answer: String,
    val keywords: String? = null
)

@Service
class AiQaExtractionService(
    private val llmProperties: LlmProperties,
    private val schedulingProperties: MailSchedulingProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val mailRecordRepository: MailRecordRepository,
    private val aiReplyContextBuilder: AiReplyContextBuilder,
    private val aiTrainingQaRepository: AiTrainingQaRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(AiQaExtractionService::class.java)

    fun extractBatch(maxContacts: Int? = null): ExtractionSummary {
        if (!llmProperties.enabled) {
            return ExtractionSummary(processed = 0, upserted = 0, skipped = 0)
        }
        val client = llmDraftClientProvider.getIfAvailable()
            ?: return ExtractionSummary(processed = 0, upserted = 0, skipped = 0)

        val limit = (maxContacts ?: schedulingProperties.aiQaExtractionMaxContacts).coerceIn(1, 200)
        val contactIds = mailRecordRepository.findExpertContactIdsWithInboundMail(null, limit)
        var upserted = 0
        var skipped = 0

        contactIds.forEach { contactId ->
            try {
                val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
                if (records.isEmpty()) {
                    skipped++
                    return@forEach
                }
                val conversation = aiReplyContextBuilder.buildMailHistory(records)
                if (conversation.isBlank()) {
                    skipped++
                    return@forEach
                }
                val llmText = try {
                    client.chat(
                        messages = listOf(
                            LlmChatMessage(role = "system", content = EXTRACTION_SYSTEM_PROMPT),
                            LlmChatMessage(
                                role = "user",
                                content = buildString {
                                    appendLine("Expert contact id: $contactId")
                                    appendLine("Mail conversation:")
                                    append(conversation.take(12000))
                                }
                            )
                        ),
                        temperature = llmProperties.temperature
                    )?.takeIf { it.isNotBlank() }
                } catch (ex: Exception) {
                    log.warn("QA extraction LLM failed for contact {}: {}", contactId, ex.message)
                    null
                }
                if (llmText == null) {
                    skipped++
                    return@forEach
                }
                val items = parseExtractedItems(llmText)
                if (items.isEmpty()) {
                    skipped++
                    return@forEach
                }
                upsertAuto(contactId, items)
                upserted++
            } catch (ex: Exception) {
                log.warn("QA extraction failed for contact {}: {}", contactId, ex.message)
                skipped++
            }
        }

        return ExtractionSummary(
            processed = contactIds.size,
            upserted = upserted,
            skipped = skipped
        )
    }

    internal fun upsertAuto(contactId: Long, items: List<ExtractedQaItem>) {
        val sourceRef = contactSourceRef(contactId)
        val existing = aiTrainingQaRepository.findBySourceAndSourceRef(AUTO_EXTRACTED, sourceRef)
        val now = LocalDateTime.now()
        val topic = items.first().topic.ifBlank { "对话提炼" }
        val question = items.joinToString("\n\n") { item ->
            item.question?.takeIf { it.isNotBlank() } ?: item.topic
        }.take(65535)
        val answer = items.joinToString("\n\n") { item ->
            buildString {
                append("Topic: ").append(item.topic)
                item.question?.takeIf { it.isNotBlank() }?.let { append("\nQuestion: ").append(it) }
                append("\nAnswer: ").append(item.answer)
            }
        }.take(65535)
        val keywords = items.mapNotNull { it.keywords?.trim()?.takeIf { keyword -> keyword.isNotEmpty() } }
            .joinToString(",")
            .take(512)

        val row = if (existing != null) {
            existing.copy(
                topic = topic,
                question = question,
                answer = answer,
                keywords = keywords.ifBlank { null },
                enabled = true,
                updatedAt = now
            )
        } else {
            AiTrainingQa(
                topic = topic,
                question = question,
                answer = answer,
                keywords = keywords.ifBlank { null },
                source = AUTO_EXTRACTED,
                sourceRef = sourceRef,
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        }
        aiTrainingQaRepository.save(row)
    }

    internal fun parseExtractedItems(raw: String): List<ExtractedQaItem> {
        val json = extractJsonPayload(raw) ?: return emptyList()
        return try {
            objectMapper.readValue<List<ExtractedQaItem>>(json)
                .filter { it.topic.isNotBlank() && it.answer.isNotBlank() }
        } catch (ex: Exception) {
            log.warn("Failed to parse extracted QA JSON: {}", ex.message)
            emptyList()
        }
    }

    private fun extractJsonPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("```")) {
            val withoutFence = trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .substringBeforeLast("```")
                .trim()
            return withoutFence.takeIf { it.isNotEmpty() }
        }
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }
        return trimmed
    }

    internal fun contactSourceRef(contactId: Long): String = "contact:$contactId"

    companion object {
        internal val EXTRACTION_SYSTEM_PROMPT = """
            You distill expert recruiting email conversations into reusable QA knowledge.
            Return ONLY a JSON array. Each element must have:
            - topic (string, short title)
            - question (string, expert question or concern, optional)
            - answer (string, standard reply points)
            - keywords (string, comma-separated trigger keywords, optional)
            Do not include markdown fences or commentary outside the JSON array.
        """.trimIndent()
    }
}
