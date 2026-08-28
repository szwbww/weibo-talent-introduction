package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.service.ArxivDataSource
import com.weibo.talentintroduction.discovery.service.CoreDataSource
import com.weibo.talentintroduction.discovery.service.CrossrefDataSource
import com.weibo.talentintroduction.discovery.service.EnrichmentResult
import com.weibo.talentintroduction.discovery.service.EnrichmentScope
import com.weibo.talentintroduction.discovery.service.EnrichmentStats
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.discovery.service.OpenAlexDataSource
import com.weibo.talentintroduction.discovery.service.OrcidDataSource
import com.weibo.talentintroduction.discovery.service.PmcOaDataSource
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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class ExpertDiscoveryControllerTest {
    private val discoveryService = Mockito.mock(ExpertDiscoveryService::class.java)
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val enrichmentExecutor = Mockito.mock(Executor::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    @Suppress("UNCHECKED_CAST")
    private val openAlexProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OpenAlexDataSource>
    @Suppress("UNCHECKED_CAST")
    private val crossrefProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<CrossrefDataSource>
    @Suppress("UNCHECKED_CAST")
    private val arxivProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<ArxivDataSource>
    @Suppress("UNCHECKED_CAST")
    private val pmcOaProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<PmcOaDataSource>
    @Suppress("UNCHECKED_CAST")
    private val orcidProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<OrcidDataSource>
    @Suppress("UNCHECKED_CAST")
    private val coreProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<CoreDataSource>
    private val europePmcProperties = EuropePmcProperties()
    private val discoveryProperties = ExpertDiscoveryProperties()
    private val controller = ExpertDiscoveryController(
        discoveryService, taskExecutionService, progressStore, enrichmentExecutor, discoveryProperties,
        openAlexProvider, crossrefProvider, arxivProvider,
        pmcOaProvider, orcidProvider, coreProvider, europePmcProperties
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -1L)
    private fun notStartedToken(): Pair<Boolean, Long> = Pair(false, -1L)

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        Mockito.doAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }.`when`(enrichmentExecutor).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `triggerDiscovery returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.triggerDiscovery(null)
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `triggerDiscoveryByKeyword returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.triggerDiscoveryByKeyword(listOf("ai"), 2020, 2026)
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `triggerDiscovery returns 500 when execution status is FAILED`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doThrow(RuntimeException("Europe PMC unavailable")).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        val result = controller.triggerDiscovery(null)
        assertEquals(500, result.statusCodeValue)
        val body = result.body as Map<*, *>
        assertTrue((body["message"] as String).contains("Europe PMC unavailable"))
    }

    @Test
    fun `triggerDiscoveryByKeyword returns 500 when execution status is FAILED`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doThrow(RuntimeException("OpenAlex timeout")).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        val result = controller.triggerDiscoveryByKeyword(listOf("ml"), 2020, 2026)
        assertEquals(500, result.statusCodeValue)
        val body = result.body as Map<*, *>
        assertTrue((body["message"] as String).contains("OpenAlex timeout"))
    }

    @Test
    fun `triggerDiscovery writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        val ex = assertThrows(RuntimeException::class.java) {
            controller.triggerDiscovery(null)
        }
        assertEquals("DB connection lost", ex.message)
        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `triggerDiscovery returns ok on success`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doReturn(DiscoveryResult("MANUAL", DiscoveryStats())).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        val result = controller.triggerDiscovery(null)
        assertEquals(200, result.statusCodeValue)
        val body = result.body as TaskLaunchResponse<*>
        assertEquals(1L, body.executionId)
        val exec = body.result as TaskExecution
        assertEquals("EXPERT_DISCOVERY", exec.taskType)
    }

    @Test
    fun `triggerDiscovery preserves existing errors on FAILED`() {
        val existing = TaskProgress(
            taskType = "EXPERT_DISCOVERY", status = "RUNNING",
            batchNumber = 2, processedCount = 10, totalCount = 100,
            message = "批次2", errors = listOf("paper A failed", "paper B timeout")
        )
        val captured = mutableListOf<TaskProgress>()
        Mockito.doAnswer { invocation ->
            captured.add(invocation.getArgument(1) as TaskProgress)
            null
        }.`when`(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
            .thenReturn(existing)
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doThrow(RuntimeException("Europe PMC down")).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        val result = controller.triggerDiscovery(null)
        assertEquals(500, result.statusCodeValue)

        assertEquals(1, captured.size)
        assertEquals(listOf("paper A failed", "paper B timeout"), captured[0].errors)
        assertEquals(2, captured[0].batchNumber)
        assertEquals(10, captured[0].processedCount)
        assertEquals(100, captured[0].totalCount)
    }

    @Test
    fun `getAvailableSources reflects Europe PMC enabled state`() {
        val disabledProps = EuropePmcProperties(enabled = false)
        val disabledController = ExpertDiscoveryController(
            discoveryService, taskExecutionService, progressStore, enrichmentExecutor, discoveryProperties,
            openAlexProvider, crossrefProvider, arxivProvider,
            pmcOaProvider, orcidProvider, coreProvider, disabledProps
        )
        val sources = disabledController.getAvailableSources()
        val europePmc = sources.find { it["sourceName"] == "EUROPE_PMC" }
        assertEquals(false, europePmc?.get("enabled"))
    }

    @Test
    fun `getAvailableSources marks new sources disabled by default`() {
        val sources = controller.getAvailableSources()
        val newSources = listOf("PMC_OA", "OPENALEX", "CROSSREF", "CORE", "ARXIV", "ORCID")
        for (name in newSources) {
            val source = sources.find { it["sourceName"] == name }
            assertEquals(false, source?.get("enabled"), "$name should be disabled by default")
        }
    }

    @Test
    fun `getEnrichmentStats returns stats from service`() {
        Mockito.doReturn(EnrichmentStats(
            pending = 10, enrichedLast30d = 90, total = 100,
            institutionTypePending = 5, lastPublicationYearPending = 7
        ))
            .`when`(discoveryService).getEnrichmentStats()

        val stats = controller.getEnrichmentStats()

        assertEquals(10, stats.pending)
        assertEquals(90, stats.enrichedLast30d)
        assertEquals(100, stats.total)
        assertEquals(5, stats.institutionTypePending)
        assertEquals(7, stats.lastPublicationYearPending)
    }

    @Test
    fun `enrichExperts returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        val result = controller.enrichExperts()
        assertEquals(409, result.statusCodeValue)
    }

    @Test
    fun `enrichExperts returns 202 when task starts asynchronously`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doReturn(EnrichmentResult(enriched = 3, failed = 1))
            .`when`(discoveryService).enrichExistingExperts()

        val result = controller.enrichExperts()
        assertEquals(HttpStatus.ACCEPTED, result.statusCode)
        val body = result.body as Map<*, *>
        assertEquals("任务已启动", body["message"])
        Mockito.verify(discoveryService).enrichExistingExperts()
    }

    @Test
    fun `enrichExperts forwards INSTITUTION_TYPE_BACKFILL scope to service`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val taskExecution = invocation.arguments[0] as TaskExecution
                taskExecution.copy(id = 1L)
            }
        Mockito.doReturn(EnrichmentResult(enriched = 1, failed = 0))
            .`when`(discoveryService).enrichExistingExperts(EnrichmentScope.INSTITUTION_TYPE_BACKFILL)

        val result = controller.enrichExperts(EnrichmentScope.INSTITUTION_TYPE_BACKFILL)
        assertEquals(HttpStatus.ACCEPTED, result.statusCode)
        Mockito.verify(discoveryService).enrichExistingExperts(EnrichmentScope.INSTITUTION_TYPE_BACKFILL)
    }

    @Test
    fun `enrichExperts forwards LAST_PUBLICATION_YEAR_BACKFILL scope to service`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val taskExecution = invocation.arguments[0] as TaskExecution
                taskExecution.copy(id = 1L)
            }
        Mockito.doReturn(EnrichmentResult(enriched = 1, failed = 0))
            .`when`(discoveryService).enrichExistingExperts(EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL)

        val result = controller.enrichExperts(EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL)
        assertEquals(HttpStatus.ACCEPTED, result.statusCode)
        Mockito.verify(discoveryService).enrichExistingExperts(EnrichmentScope.LAST_PUBLICATION_YEAR_BACKFILL)
    }

    @Test
    fun `enrichExperts returns 409 when executor rejects submission`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.doThrow(RejectedExecutionException("pool saturated"))
            .`when`(enrichmentExecutor).execute(Mockito.any(Runnable::class.java))

        val result = controller.enrichExperts()
        assertEquals(409, result.statusCodeValue)
        Mockito.verify(progressStore).clear("EXPERT_ENRICHMENT")
        Mockito.verify(discoveryService, Mockito.never()).enrichExistingExperts()
    }
}
