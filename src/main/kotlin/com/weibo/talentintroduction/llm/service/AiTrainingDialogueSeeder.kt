package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.llm.domain.AiTrainingDialogue
import com.weibo.talentintroduction.llm.repository.AiTrainingDialogueRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class AiTrainingDialogueSeeder(
    private val repository: AiTrainingDialogueRepository,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(AiTrainingDialogueSeeder::class.java)

    override fun run(args: ApplicationArguments?) {
        try {
            val seeds = loadSeeds()
            var inserted = 0
            var skipped = 0
            seeds.forEach { seed ->
                if (repository.findBySourceRef(seed.sourceRef) != null) {
                    skipped++
                } else {
                    repository.save(
                        AiTrainingDialogue(
                            title = seed.title,
                            sourceRef = seed.sourceRef,
                            keywords = seed.keywords,
                            turnsJson = objectMapper.writeValueAsString(seed.turns),
                            enabled = true,
                            createdAt = LocalDateTime.now(),
                            updatedAt = LocalDateTime.now()
                        )
                    )
                    inserted++
                }
            }
            logger.info("ai_training_dialogue seed: inserted={} skipped={}", inserted, skipped)
        } catch (ex: Exception) {
            logger.warn("ai_training_dialogue seed failed: {}", ex.message, ex)
        }
    }

    private fun loadSeeds(): List<DialogueSeedEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("ai-training/dialogue-seed.json")
            ?: throw IllegalStateException("Missing classpath resource ai-training/dialogue-seed.json")
        return objectMapper.readValue(stream)
    }

    internal data class DialogueSeedEntry(
        val title: String,
        val sourceRef: String,
        val keywords: String?,
        val turns: List<DialogueTurnSeed>
    )

    internal data class DialogueTurnSeed(
        val role: String,
        val text: String
    )
}
