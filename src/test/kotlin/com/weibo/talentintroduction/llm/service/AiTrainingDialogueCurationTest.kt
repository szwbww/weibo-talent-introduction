package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class AiTrainingDialogueCurationTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val expectedRefs = listOf(
        "STYLE_MULTI_DUE_DILIGENCE",
        "STYLE_PROFILE_CONTEXT_GAP",
        "STYLE_TRUST_VERIFICATION",
        "STYLE_CONTRACT_BOUNDARY",
        "STYLE_PROCESS_NEXT_STEPS",
        "STYLE_MATERIALS_BOUNDARY"
    )

    private val forbiddenPatterns = listOf(
        Regex("""\b20\d{2}\b"""),
        Regex("""(?i)\b(rmb|usd|cny|million)\b"""),
        Regex("""(?i)\b(government|national-level|success rate)\b"""),
        Regex("""(?i)\b(passport|degree certificate)\b"""),
        Regex("""(?i)\b(salary|travel expenses|no fees|strictly confidential)\b"""),
        Regex("""\d+%"""),
        Regex("""\$\d|\d+\s*(million|billion)""", RegexOption.IGNORE_CASE)
    )

    data class SeedTurn(val role: String, val text: String)
    data class SeedEntry(
        val title: String,
        val sourceRef: String,
        val keywords: String?,
        val turns: List<SeedTurn>
    )

    @Test
    fun `dialogue-seed json has exactly six STYLE refs with EXPERT-AGENT pairs`() {
        val seeds = loadSeeds()
        assertEquals(6, seeds.size)
        assertEquals(expectedRefs, seeds.map { it.sourceRef })
        seeds.forEach { seed ->
            assertEquals(2, seed.turns.size, seed.sourceRef)
            assertEquals("EXPERT", seed.turns[0].role)
            assertEquals("AGENT", seed.turns[1].role)
            assertTrue(seed.turns[1].text.length <= 700, seed.sourceRef)
            val joined = seed.turns.joinToString("\n") { it.text }
            forbiddenPatterns.forEach { pattern ->
                assertFalse(pattern.containsMatchIn(joined), "${seed.sourceRef} matched forbidden $pattern")
            }
            assertFalse(joined.contains("["), seed.sourceRef)
            assertFalse(joined.contains("]"), seed.sourceRef)
        }
    }

    @Test
    fun `V69 disables legacy DIALOG rows and upserts STYLE turns matching JSON`() {
        val sql = Files.readString(
            Paths.get("src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql")
        )
        assertTrue(sql.contains("UPDATE ai_training_dialogue SET enabled = 0 WHERE source_ref LIKE 'DIALOG_%'"))
        assertTrue(sql.all { it.code < 128 }, "V69 must be ASCII-only")

        val seeds = loadSeeds()
        seeds.forEach { seed ->
            assertTrue(sql.contains(seed.sourceRef), seed.sourceRef)
            assertTrue(sql.contains(seed.title), seed.title)
            assertTrue(sql.contains(seed.keywords!!), seed.sourceRef)
            seed.turns.forEach { turn ->
                assertTrue(sql.contains(turn.text), "${seed.sourceRef}:${turn.role}")
            }
            assertTrue(
                sql.contains("ON DUPLICATE KEY UPDATE") &&
                    sql.contains("enabled = 1"),
                "upsert enabled for ${seed.sourceRef}"
            )
        }
    }

    private fun loadSeeds(): List<SeedEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("ai-training/dialogue-seed.json")
            ?: error("Missing dialogue-seed.json")
        return objectMapper.readValue(stream)
    }
}
