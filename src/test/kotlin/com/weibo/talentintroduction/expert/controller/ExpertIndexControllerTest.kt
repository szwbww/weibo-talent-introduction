package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class ExpertIndexControllerTest {
    private val searchService = Mockito.mock(ExpertSearchService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val writerService = Mockito.mock(ExpertIndexWriterService::class.java)
    private val revalidationService = Mockito.mock(ExpertRevalidationService::class.java)
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val controller = ExpertIndexController(
        searchService, contactRepository, writerService,
        revalidationService, taskExecutionService
    )

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 1`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(1))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertNotNull(result)
        assertEquals(0, result.stats.total)
    }

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 10000`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(10000))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 10000)
        assertNotNull(result)
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 0`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 0)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
    }

    @Test
    fun `promoteEligibleRaw rejects negative maxPromotions`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = -1)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 10001`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 10001)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
    }
}
