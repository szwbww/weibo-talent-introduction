package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.BulkSyncResult
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskLaunchResponse
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
    private val indexService = Mockito.mock(com.weibo.talentintroduction.expert.service.ExpertIndexService::class.java)
    private val filterService = Mockito.mock(EligibilityFilterService::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val controller = ExpertIndexController(
        searchService, contactRepository, writerService,
        revalidationService, taskExecutionService, progressStore, indexService, filterService
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -1L)
    private fun notStartedToken(): Pair<Boolean, Long> = Pair(false, -1L)

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 1`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(1))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertNotNull(result)
        val body = result.body as TaskLaunchResponse<*>
        val scanResult = body.result as PromotionScanResult
        assertEquals(0, scanResult.stats.total)
        assertEquals(1L, body.executionId)
    }

    @Test
    fun `promoteEligibleRaw accepts maxPromotions 10000`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(10000))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 10000)
        assertNotNull(result)
    }

    @Test
    fun `revalidateCandidates returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.revalidateCandidates()
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `promoteEligibleRaw returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 0 and does not call tryStartWithToken`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 0)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `promoteEligibleRaw rejects negative maxPromotions and does not call tryStartWithToken`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = -1)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `promoteEligibleRaw rejects maxPromotions 10001 and does not call tryStartWithToken`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 10001)
        }
        assertTrue(ex.message!!.contains("between 1 and 10000"))
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
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
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(revalidationService.promoteEligibleRawExperts(1))
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw(maxPromotions = 1)
        assertNotNull(result)
        val body = result.body as TaskLaunchResponse<*>
        val scanResult = body.result as PromotionScanResult
        assertEquals(0, scanResult.stats.total)
        assertEquals(1L, body.executionId)
    }

    @Test
    fun `revalidateCandidates writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.revalidateCandidates()
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `promoteEligibleRaw writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.promoteEligibleRaw(maxPromotions = 1)
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `backfillOperatorStatus returns 400 when mapping check fails`() {
        Mockito.`when`(indexService.checkCandidateOperatorStatusMapping()).thenReturn(false)
        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["message"].toString().contains("CANDIDATE 索引缺少 keyword"))
    }

    @Test
    fun `backfillOperatorStatus processes latest contact per orcid and returns ok`() {
        Mockito.`when`(indexService.checkCandidateOperatorStatusMapping()).thenReturn(true)
        val contact1 = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L, orcidId = "orcid-1", operatorStatus = "CONTACTED",
            campaignId = 1L, expertEmail = "test1@example.com", expertName = "Test 1"
        )
        val contact2 = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 2L, orcidId = "orcid-1", operatorStatus = "REPLIED",
            campaignId = 1L, expertEmail = "test2@example.com", expertName = "Test 2"
        )
        Mockito.`when`(contactRepository.findAllByOrderByUpdatedAtDesc())
            .thenReturn(listOf(contact2, contact1)) // contact2 is newer

        Mockito.`when`(writerService.syncCandidateOperatorStatusBatch(listOf("orcid-1" to "REPLIED")))
            .thenReturn(BulkSyncResult(total = 1, success = 1, failure = 0, skipped = 0))

        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.OK, response.statusCode)
        val body = response.body as BackfillResult
        assertEquals(1, body.total)
        assertEquals(1, body.success)
        assertEquals(0, body.failure)
        assertEquals(0, body.skipped)
    }
}
