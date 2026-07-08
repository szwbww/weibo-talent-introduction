package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.llm.domain.AiTrainingDialogue
import com.weibo.talentintroduction.llm.repository.AiTrainingDialogueRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AiTrainingDialogueServiceTest {
    private val repository = Mockito.mock(AiTrainingDialogueRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val service = AiTrainingDialogueService(repository, objectMapper)

    @Test
    fun `listViews maps dialogue rows with turn counts`() {
        val dialogues = listOf(
            dialogue(id = 1L, sourceRef = "DIALOG_A", keywords = "trust", turnsJson = turnsJson(3))
        )
        Mockito.`when`(repository.findAllByOrderByIdAsc()).thenReturn(dialogues)

        val views = service.listViews()

        assertEquals(1, views.size)
        assertEquals("DIALOG_A", views[0].sourceRef)
        assertEquals(3, views[0].turnCount)
        assertTrue(views[0].enabled)
    }

    @Test
    fun `countTurns returns zero when turns json invalid`() {
        assertEquals(0, service.countTurns("{invalid", "DIALOG_BAD"))
    }

    @Test
    fun `selects top dialogues by keyword score then id ascending`() {
        val dialogues = listOf(
            dialogue(id = 3L, sourceRef = "DIALOG_C", keywords = "trust,official"),
            dialogue(id = 1L, sourceRef = "DIALOG_A", keywords = "trust,accredited"),
            dialogue(id = 2L, sourceRef = "DIALOG_B", keywords = "trust")
        )
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(dialogues)

        val selected = service.selectRelevantDialogues(
            inboundText = "Are you official and accredited? I need trust.",
            max = 2
        )

        assertEquals(listOf("DIALOG_A", "DIALOG_C"), selected.map { it.sourceRef })
    }

    @Test
    fun `returns empty when no keyword matches`() {
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(
            listOf(dialogue(id = 1L, sourceRef = "DIALOG_A", keywords = "funding,video"))
        )

        val selected = service.selectRelevantDialogues("Hello there")

        assertTrue(selected.isEmpty())
    }

    @Test
    fun `truncates each dialogue and total few-shot budget`() {
        val longText = "x".repeat(2500)
        val dialogues = listOf(
            dialogue(
                id = 1L,
                sourceRef = "DIALOG_LONG_A",
                keywords = "budget",
                turnsJson = turnsJson("EXPERT" to longText, "AGENT" to "reply-a")
            ),
            dialogue(
                id = 2L,
                sourceRef = "DIALOG_LONG_B",
                keywords = "budget",
                turnsJson = turnsJson("EXPERT" to longText, "AGENT" to "reply-b")
            ),
            dialogue(
                id = 3L,
                sourceRef = "DIALOG_LONG_C",
                keywords = "budget",
                turnsJson = turnsJson("EXPERT" to longText, "AGENT" to "reply-c")
            )
        )
        Mockito.`when`(repository.findAllByEnabledTrue()).thenReturn(dialogues)

        val selected = service.selectRelevantDialogues("budget question", max = 3)

        assertEquals(2, selected.size)
        assertEquals(listOf("DIALOG_LONG_A", "DIALOG_LONG_B"), selected.map { it.sourceRef })
        selected.forEach { dialogue ->
            assertTrue(dialogue.messages.sumOf { it.content.length } <= 2500)
        }
        assertTrue(selected.sumOf { shot -> shot.messages.sumOf { it.content.length } } <= 6000)
    }

    @Test
    fun `renders expert as user and agent as assistant`() {
        val messages = service.renderDialogueTurns(
            turnsJson = turnsJson("EXPERT" to "Question?", "AGENT" to "Answer."),
            sourceRef = "DIALOG_TEST"
        )

        assertEquals(listOf("user", "assistant"), messages?.map { it.role })
        assertEquals(listOf("Question?", "Answer."), messages?.map { it.content })
    }

    @Test
    fun `scoreKeywords counts lowercase substring matches`() {
        val score = service.scoreKeywords(
            keywords = "Other Agency,ACCredited",
            inboundLower = "are you accredited through another agency?"
        )

        assertEquals(2, score)
    }

    private fun dialogue(
        id: Long,
        sourceRef: String,
        keywords: String,
        turnsJson: String = turnsJson("EXPERT" to "Hello", "AGENT" to "Hi")
    ) = AiTrainingDialogue(
        id = id,
        title = sourceRef,
        sourceRef = sourceRef,
        keywords = keywords,
        turnsJson = turnsJson
    )

    private fun turnsJson(vararg turns: Pair<String, String>): String =
        objectMapper.writeValueAsString(
            turns.map { (role, text) ->
                mapOf("role" to role, "text" to text)
            }
        )

    private fun turnsJson(turnCount: Int): String =
        objectMapper.writeValueAsString(
            (1..turnCount).map { index ->
                mapOf("role" to if (index % 2 == 1) "EXPERT" else "AGENT", "text" to "turn-$index")
            }
        )
}
