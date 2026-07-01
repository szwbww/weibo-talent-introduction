package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

private const val MANUAL_IMPORT = "MANUAL_IMPORT"

@Component
class AiTrainingQaSeeder(
    private val repository: AiTrainingQaRepository,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(AiTrainingQaSeeder::class.java)

    override fun run(args: ApplicationArguments?) {
        try {
            val seeds = loadSeeds()
            var inserted = 0
            var skipped = 0
            seeds.forEach { seed ->
                if (repository.findBySourceAndSourceRef(MANUAL_IMPORT, seed.sourceRef) != null) {
                    skipped++
                } else {
                    repository.save(
                        AiTrainingQa(
                            topic = seed.topic,
                            question = seed.question,
                            answer = seed.answer,
                            keywords = seed.keywords,
                            source = MANUAL_IMPORT,
                            sourceRef = seed.sourceRef,
                            enabled = true,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    )
                    inserted++
                }
            }
            logger.info("ai_training_qa seed: inserted={} skipped={}", inserted, skipped)
        } catch (ex: Exception) {
            logger.warn("ai_training_qa seed failed: {}", ex.message, ex)
        }
    }

    private fun loadSeeds(): List<QaSeedEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("ai-training/qa-seed.json")
            ?: throw IllegalStateException("Missing classpath resource ai-training/qa-seed.json")
        return objectMapper.readValue(stream)
    }

    private data class QaSeedEntry(
        val topic: String,
        val question: String?,
        val answer: String,
        val keywords: String?,
        val sourceRef: String
    )
}
