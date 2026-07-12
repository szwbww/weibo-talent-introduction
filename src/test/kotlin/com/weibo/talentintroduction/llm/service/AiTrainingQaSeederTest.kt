package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.boot.DefaultApplicationArguments
import java.nio.file.Files
import java.nio.file.Paths

class AiTrainingQaSeederTest {
    private val repository = Mockito.mock(AiTrainingQaRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val seeder = AiTrainingQaSeeder(repository, objectMapper)

    data class SeedEntry(
        val topic: String,
        val question: String?,
        val answer: String,
        val keywords: String?,
        val sourceRef: String
    )

    @Test
    fun `seeder skips existing sourceRef rows`() {
        Mockito.`when`(repository.findBySourceAndSourceRef(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(
                AiTrainingQa(
                    id = 1,
                    topic = "existing",
                    answer = "existing",
                    source = "MANUAL_IMPORT",
                    sourceRef = "PROJECT_CONTENT"
                )
            )

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `seeder inserts missing sourceRef rows`() {
        Mockito.`when`(repository.findBySourceAndSourceRef(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(null)
        val captor = ArgumentCaptor.forClass(AiTrainingQa::class.java)

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.atLeastOnce()).save(captor.capture())
        assertEquals("MANUAL_IMPORT", captor.allValues.first().source)
    }

    @Test
    fun `MATERIALS_LIGHT seed and V70 drop bare provide keep explicit phrases`() {
        val materials = loadSeeds().single { it.sourceRef == "MATERIALS_LIGHT" }
        val tokens = materials.keywords!!.split(",").map { it.trim() }
        assertFalse(tokens.any { it.equals("provide", ignoreCase = true) }, "bare provide must not be a keyword token")
        assertTrue(tokens.contains("what should i provide"))
        assertTrue(tokens.contains("provide my cv"))
        assertTrue(tokens.contains("what documents"))

        val v70 = Files.readString(
            Paths.get("src/main/resources/db/migration/V70__tighten_ai_reply_action_boundaries.sql")
        )
        assertTrue(v70.all { it.code < 128 }, "V70 must be ASCII-only")
        assertTrue(v70.contains("source_ref = 'MATERIALS_LIGHT'"))
        assertTrue(v70.contains(materials.keywords!!), "V70 keywords must match seed")

        assertTrue(v70.contains("WHERE id = 24"))
        assertTrue(v70.contains("WHERE id = 23"))
        assertTrue(v70.contains("all materials are kept strictly confidential."))
        assertFalse(v70.contains("reply with your CV"), "id=24 must drop CV CTA")
        assertFalse(v70.contains("could you confirm your current research focus"), "id=23 must drop research-focus ask")

        val v65 = Files.readString(
            Paths.get("src/main/resources/db/migration/V65__qa_rule_dedup_keyword_fix_and_overview.sql")
        )
        assertTrue(v65.contains("reply with your CV"), "applied V65 must remain unchanged")
        assertTrue(v65.contains("could you confirm your current research focus"), "applied V65 must remain unchanged")
    }

    private fun loadSeeds(): List<SeedEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("ai-training/qa-seed.json")
            ?: error("Missing qa-seed.json")
        return objectMapper.readValue(stream)
    }
}
