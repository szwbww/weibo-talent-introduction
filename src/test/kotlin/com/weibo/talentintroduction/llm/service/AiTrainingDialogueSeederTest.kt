package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.llm.domain.AiTrainingDialogue
import com.weibo.talentintroduction.llm.repository.AiTrainingDialogueRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.boot.DefaultApplicationArguments

class AiTrainingDialogueSeederTest {
    private val repository = Mockito.mock(AiTrainingDialogueRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val seeder = AiTrainingDialogueSeeder(repository, objectMapper)

    @Test
    fun `seeder skips existing sourceRef rows`() {
        Mockito.`when`(repository.findBySourceRef(Mockito.anyString()))
            .thenReturn(
                AiTrainingDialogue(
                    id = 1,
                    title = "existing",
                    sourceRef = "DIALOG_1095",
                    turnsJson = "[]"
                )
            )

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `seeder inserts missing sourceRef rows`() {
        Mockito.`when`(repository.findBySourceRef(Mockito.anyString())).thenReturn(null)
        val captor = ArgumentCaptor.forClass(AiTrainingDialogue::class.java)

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.atLeastOnce()).save(captor.capture())
        assertEquals("DIALOG_1095", captor.allValues.first().sourceRef)
    }
}
