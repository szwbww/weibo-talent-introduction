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

    private val boundaryRefs = listOf(
        "STYLE_MULTI_DUE_DILIGENCE",
        "STYLE_TRUST_VERIFICATION"
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
    fun `V69 disables legacy DIALOG rows and upserts STYLE refs without editing applied file`() {
        val sql = Files.readString(
            Paths.get("src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql")
        )
        assertTrue(sql.contains("UPDATE ai_training_dialogue SET enabled = 0 WHERE source_ref LIKE 'DIALOG_%'"))
        assertTrue(sql.all { it.code < 128 }, "V69 must be ASCII-only")
        expectedRefs.forEach { ref ->
            assertTrue(sql.contains(ref), ref)
        }
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"))
        assertTrue(sql.contains("enabled = 1"))
        // Applied migration keeps historical CV/meeting anchors; V70 rewrites them.
        assertTrue(sql.contains("request for your CV"))
        assertTrue(sql.contains("documents or a meeting"))
    }

    @Test
    fun `V70 rewrites boundary style turns to match seed without CV or meeting literals`() {
        val v70 = Files.readString(
            Paths.get("src/main/resources/db/migration/V70__tighten_ai_reply_action_boundaries.sql")
        )
        assertTrue(v70.all { it.code < 128 }, "V70 must be ASCII-only")
        assertFalse(Files.readString(
            Paths.get("src/main/resources/db/migration/V69__curate_ai_training_dialogue_styles.sql")
        ).contains("requesting unrelated materials"), "V69 must stay applied-as-is")

        val seeds = loadSeeds().filter { it.sourceRef in boundaryRefs }
        assertEquals(2, seeds.size)
        seeds.forEach { seed ->
            val joined = seed.turns.joinToString("\n") { it.text }
            assertFalse(joined.contains("CV", ignoreCase = false), seed.sourceRef)
            assertFalse(joined.contains("meeting", ignoreCase = true), seed.sourceRef)
            seed.turns.forEach { turn ->
                assertTrue(v70.contains(turn.text), "${seed.sourceRef}:${turn.role}")
            }
            assertTrue(v70.contains("source_ref = '${seed.sourceRef}'"), seed.sourceRef)
        }
        assertTrue(v70.contains("requesting unrelated materials"))
        assertTrue(v70.contains("unrelated next action"))
        assertFalse(v70.contains("request for your CV"))
        assertFalse(v70.contains("documents or a meeting"))
    }

    private fun loadSeeds(): List<SeedEntry> {
        val stream = javaClass.classLoader.getResourceAsStream("ai-training/dialogue-seed.json")
            ?: error("Missing dialogue-seed.json")
        return objectMapper.readValue(stream)
    }
}
