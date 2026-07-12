package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AiTrainingQaServiceTest {
    private val repository = Mockito.mock(AiTrainingQaRepository::class.java)
    private val service = AiTrainingQaService(repository)

    private val materialsLight = AiTrainingQa(
        id = 10,
        topic = "轻问材料",
        question = "What documents should I send at this stage?",
        answer = "CV, patents, and a publication list.",
        keywords = "what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,what should i provide,provide my cv",
        source = "MANUAL_IMPORT",
        sourceRef = "MATERIALS_LIGHT",
        enabled = true
    )

    private val funding = AiTrainingQa(
        id = 20,
        topic = "Funding",
        question = "What funding is available?",
        answer = "3-12 million RMB.",
        keywords = "funding,salary,subsidy",
        source = "MANUAL_IMPORT",
        sourceRef = "SALARY",
        enabled = true
    )

    @Test
    fun `provide further information does not hit MATERIALS_LIGHT`() {
        stubRows(listOf(materialsLight, funding))

        val context = service.buildKnowledgeContext("Could you provide further information about the program?")

        assertFalse(context.contains("轻问材料"))
        assertFalse(context.contains("CV, patents"))
        assertEquals("", context)
    }

    @Test
    fun `what documents should I provide hits MATERIALS_LIGHT`() {
        stubRows(listOf(materialsLight, funding))

        val context = service.buildKnowledgeContext("What documents should I provide at this stage?")

        assertTrue(context.contains("Topic: 轻问材料"))
        assertTrue(context.contains("CV, patents, and a publication list."))
        assertFalse(context.contains("Topic: Funding"))
    }

    @Test
    fun `ranks by matched keyword count desc then id asc and caps at six rows`() {
        val rows = (1L..8L).map { id ->
            AiTrainingQa(
                id = id,
                topic = "T$id",
                answer = "A$id",
                keywords = if (id <= 2) "alpha,beta,gamma" else "alpha",
                source = "MANUAL_IMPORT",
                sourceRef = "REF$id",
                enabled = true
            )
        }
        stubRows(rows)

        val context = service.buildKnowledgeContext("Please cover alpha beta gamma topics")
        val topics = Regex("Topic: T(\\d+)").findAll(context).map { it.groupValues[1].toLong() }.toList()

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L), topics)
        assertFalse(context.contains("Topic: T7"))
        assertFalse(context.contains("Topic: T8"))
    }

    @Test
    fun `disabled rows and zero hits return empty and respects char budget`() {
        val disabled = materialsLight.copy(id = 99, enabled = false)
        val huge = AiTrainingQa(
            id = 1,
            topic = "Huge",
            answer = "X".repeat(7000),
            keywords = "huge topic",
            source = "MANUAL_IMPORT",
            sourceRef = "HUGE",
            enabled = true
        )
        stubRows(listOf(disabled, huge))

        assertEquals("", service.buildKnowledgeContext("provide further information"))
        assertEquals("", service.buildKnowledgeContext("unrelated mail with no keywords"))

        val truncated = service.buildKnowledgeContext("Please discuss the huge topic now")
        assertTrue(truncated.startsWith("Topic: Huge"))
        assertEquals(6000, truncated.length)
    }

    @Test
    fun `keyword normalize trims lowercases and drops blanks`() {
        val row = AiTrainingQa(
            id = 5,
            topic = "Norm",
            answer = "ok",
            keywords = "  Foo Bar , ,BAZ, ",
            source = "MANUAL_IMPORT",
            sourceRef = "NORM",
            enabled = true
        )
        stubRows(listOf(row))

        val hit = service.buildKnowledgeContext("Please send FOO   BAR details")
        assertTrue(hit.contains("Topic: Norm"))

        val miss = service.buildKnowledgeContext("nothing here")
        assertEquals("", miss)
    }

    private fun stubRows(rows: List<AiTrainingQa>) {
        Mockito.`when`(repository.findAllByOrderByCreatedAtDesc()).thenReturn(rows)
    }
}
