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
                    sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                    turnsJson = "[]"
                )
            )

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `seeder inserts six STYLE refs when missing`() {
        Mockito.`when`(repository.findBySourceRef(Mockito.anyString())).thenReturn(null)
        val captor = ArgumentCaptor.forClass(AiTrainingDialogue::class.java)

        seeder.run(DefaultApplicationArguments())

        Mockito.verify(repository, Mockito.times(6)).save(captor.capture())
        assertEquals("STYLE_MULTI_DUE_DILIGENCE", captor.allValues.first().sourceRef)
        assertEquals(
            listOf(
                "STYLE_MULTI_DUE_DILIGENCE",
                "STYLE_PROFILE_CONTEXT_GAP",
                "STYLE_TRUST_VERIFICATION",
                "STYLE_CONTRACT_BOUNDARY",
                "STYLE_PROCESS_NEXT_STEPS",
                "STYLE_MATERIALS_BOUNDARY"
            ),
            captor.allValues.map { it.sourceRef }
        )
    }
}
