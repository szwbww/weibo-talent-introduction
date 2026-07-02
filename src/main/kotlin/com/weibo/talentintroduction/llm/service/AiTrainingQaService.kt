package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class AiTrainingQaDto(
    val id: Long,
    val topic: String,
    val question: String?,
    val answer: String,
    val keywords: String?,
    val source: String,
    val sourceRef: String,
    val enabled: Boolean,
    val createdAt: String?,
    val updatedAt: String?
)

data class AiTrainingQaPage(
    val items: List<AiTrainingQaDto>,
    val total: Long,
    val page: Int,
    val size: Int
)

@Service
class AiTrainingQaService(
    private val repository: AiTrainingQaRepository
) {
    fun list(source: String?, page: Int, size: Int): AiTrainingQaPage {
        val normalizedPage = page.coerceAtLeast(0)
        val normalizedSize = size.coerceIn(1, 200)
        val filtered = repository.findAllByOrderByCreatedAtDesc()
            .filter { row -> source.isNullOrBlank() || row.source == source }
        val total = filtered.size.toLong()
        val fromIndex = (normalizedPage * normalizedSize).coerceAtMost(filtered.size)
        val toIndex = (fromIndex + normalizedSize).coerceAtMost(filtered.size)
        val items = filtered.subList(fromIndex, toIndex).map { it.toDto() }
        return AiTrainingQaPage(items = items, total = total, page = normalizedPage, size = normalizedSize)
    }

    fun create(topic: String, question: String?, answer: String, keywords: String?): AiTrainingQaDto {
        val now = LocalDateTime.now()
        val saved = repository.save(
            AiTrainingQa(
                topic = topic.trim(),
                question = question?.trim()?.takeIf { it.isNotEmpty() },
                answer = answer.trim(),
                keywords = keywords?.trim()?.takeIf { it.isNotEmpty() },
                source = "MANUAL_IMPORT",
                sourceRef = "MANUAL:${System.currentTimeMillis()}",
                enabled = true,
                createdAt = now,
                updatedAt = now
            )
        )
        return saved.toDto()
    }

    fun update(id: Long, topic: String, question: String?, answer: String, keywords: String?): AiTrainingQaDto {
        val existing = repository.findById(id).orElseThrow {
            IllegalArgumentException("QA entry not found: $id")
        }
        val saved = repository.save(
            existing.copy(
                topic = topic.trim(),
                question = question?.trim()?.takeIf { it.isNotEmpty() },
                answer = answer.trim(),
                keywords = keywords?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = LocalDateTime.now()
            )
        )
        return saved.toDto()
    }

    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw IllegalArgumentException("QA entry not found: $id")
        }
        repository.deleteById(id)
    }

    fun buildKnowledgeContext(): String {
        return repository.findAllByOrderByCreatedAtDesc()
            .asSequence()
            .filter { it.enabled }
            .map { qa ->
                buildString {
                    append("Topic: ").append(qa.topic)
                    qa.question?.takeIf { it.isNotBlank() }?.let { append("\nQuestion: ").append(it) }
                    append("\nAnswer: ").append(qa.answer)
                }
            }
            .joinToString("\n\n")
            .take(12000)
    }

    private fun AiTrainingQa.toDto() = AiTrainingQaDto(
        id = id ?: 0,
        topic = topic,
        question = question,
        answer = answer,
        keywords = keywords,
        source = source,
        sourceRef = sourceRef,
        enabled = enabled,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
}
