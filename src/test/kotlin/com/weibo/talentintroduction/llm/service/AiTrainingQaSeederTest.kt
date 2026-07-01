package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.llm.domain.AiTrainingQa
import com.weibo.talentintroduction.llm.repository.AiTrainingQaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.boot.DefaultApplicationArguments

class AiTrainingQaSeederTest {
    private val repository = Mockito.mock(AiTrainingQaRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val seeder = AiTrainingQaSeeder(repository, objectMapper)

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
}
