package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.PromotionScanResult
import com.weibo.talentintroduction.expert.domain.PromotionScanStats
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.CandidateOperatorStatusSyncService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.BulkSyncResult
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.EmailDomainCount
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.expert.domain.ExpertProfile
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
    private val syncService = Mockito.mock(CandidateOperatorStatusSyncService::class.java)
    private val revalidationService = Mockito.mock(ExpertRevalidationService::class.java)
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val filterService = Mockito.mock(EligibilityFilterService::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val controller = ExpertIndexController(
        searchService, contactRepository, writerService, syncService,
        revalidationService, taskExecutionService, progressStore, filterService
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -1L)
    private fun notStartedToken(): Pair<Boolean, Long> = Pair(false, -1L)

    @Test
    fun `promoteEligibleRaw succeeds`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(revalidationService.promoteEligibleRawExperts())
            .thenReturn(PromotionScanResult(PromotionScanStats(total = 0)))

        val result = controller.promoteEligibleRaw()
        assertNotNull(result)
        val body = result.body as TaskLaunchResponse<*>
        val scanResult = body.result as PromotionScanResult
        assertEquals(0, scanResult.stats.total)
        assertEquals(1L, body.executionId)
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

        val result = controller.promoteEligibleRaw()
        assertEquals(409, result.statusCodeValue)
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
            controller.promoteEligibleRaw()
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
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(syncService.reconcileAll())
            .thenThrow(IllegalStateException("CANDIDATE 索引缺少 keyword 类型的 operatorStatus mapping 声明，请先更新 mapping"))

        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertTrue(body["message"].toString().contains("CANDIDATE 索引缺少 keyword"))
    }

    @Test
    fun `backfillOperatorStatus processes latest contact per orcid and returns ok`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.`when`(syncService.reconcileAll())
            .thenReturn(BulkSyncResult(total = 1, success = 1, failure = 0, skipped = 0))

        val response = controller.backfillOperatorStatus()
        assertEquals(org.springframework.http.HttpStatus.OK, response.statusCode)
        val body = response.body as BackfillResult
        assertEquals(1, body.total)
        assertEquals(1, body.success)
        assertEquals(0, body.failure)
        assertEquals(0, body.skipped)
    }

    @Test
    fun `listExperts prefers mysql operatorStatus over elasticsearch`() {
        val expert = ExpertProfile(
            orcidId = "orcid-1",
            email = "test@example.com",
            givenNames = "Expert",
            familyNames = "One",
            country = "US",
            keyword = null,
            employment = null,
            operatorStatus = null
        )
        Mockito.`when`(searchService.searchExperts(50, ExpertIndexLevel.CANDIDATE, null, null, 0, null, null))
            .thenReturn(com.weibo.talentintroduction.expert.service.ExpertSearchResult(listOf(expert), 1L))
        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L,
            orcidId = "orcid-1",
            operatorStatus = "REPLIED",
            campaignId = 1L,
            expertEmail = "test@example.com",
            expertName = "Expert One",
            updatedAt = LocalDateTime.now()
        )
        Mockito.`when`(contactRepository.findByOrcidIdIn(listOf("orcid-1")))
            .thenReturn(listOf(contact))

        val response = controller.listExperts(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = null
        )

        assertEquals(1, response.experts.size)
        assertEquals("REPLIED", response.experts[0].operatorStatus)
    }

    @Test
    fun `listExperts passes emailDomain parameter to searchService`() {
        Mockito.`when`(searchService.searchExperts(50, ExpertIndexLevel.CANDIDATE, null, null, 0, null, "gmail.com"))
            .thenReturn(com.weibo.talentintroduction.expert.service.ExpertSearchResult(emptyList(), 0L))

        val response = controller.listExperts(
            level = ExpertIndexLevel.CANDIDATE,
            size = 50,
            tag = null,
            sortBy = null,
            from = 0,
            operatorStatus = null,
            emailDomain = "gmail.com"
        )
        assertEquals(0L, response.totalHits)
        Mockito.verify(searchService).searchExperts(50, ExpertIndexLevel.CANDIDATE, null, null, 0, null, "gmail.com")
    }

    @Test
    fun `getEmailProviders aggregates email domains`() {
        val expectedAggs = listOf(EmailDomainCount("gmail.com", 100L))
        Mockito.`when`(searchService.aggregateEmailDomains(ExpertIndexLevel.CANDIDATE))
            .thenReturn(expectedAggs)

        val response = controller.getEmailProviders(ExpertIndexLevel.CANDIDATE)
        assertEquals(1, response.size)
        assertEquals("gmail.com", response[0].domain)
        assertEquals(100L, response[0].count)
    }
}
