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
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
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
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val controller = ExpertIndexController(
        searchService, contactRepository, writerService,
        revalidationService, taskExecutionService, progressStore
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 1`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(true)
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(1))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertNotNull(result)
        assertEquals(0, (result.body as PromotionScanResult).stats.total)
    }

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 10000`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(true)
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(10000))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 10000)
        assertNotNull(result)
    }

    @Test
    fun `revalidateCandidates returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(false)

        val result = controller.revalidateCandidates()
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `promoteEligibleRaw returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(false)

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 0 and does not call tryStart`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 0)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStart(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `promoteEligibleRaw rejects negative maxPromotions and does not call tryStart`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = -1)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStart(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 10001 and does not call tryStart`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 10001)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStart(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `promoteEligibleRaw valid after invalid does not leak lock`() {
        assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 0)
        }

        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(true)
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(1))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertNotNull(result)
        assertEquals(0, (result.body as PromotionScanResult).stats.total)
    }

    @Test
    fun `revalidateCandidates writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(true)
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.revalidateCandidates()
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
        )
    }

    @Test
    fun `promoteEligibleRaw writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStart(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(true)
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 1)
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
        )
    }
}
