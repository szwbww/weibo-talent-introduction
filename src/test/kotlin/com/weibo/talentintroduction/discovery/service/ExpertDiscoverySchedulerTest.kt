package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.MailSchedulingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito

class ExpertDiscoverySchedulerTest {
    private val discoveryService = Mockito.mock(ExpertDiscoveryService::class.java)
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val discoveryProperties = ExpertDiscoveryProperties()
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objectMapper = ObjectMapper()
    private val taskExecutionService = TaskExecutionService(repository, objectMapper, schedulingProperties)
    private val scheduler = ExpertDiscoveryScheduler(
        discoveryService, taskExecutionService, discoveryProperties, progressStore
    )

    private fun anyTaskProgress(): TaskProgress {
        return Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)
    }

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -1L)
    private fun notStartedToken(): Pair<Boolean, Long> = Pair(false, -1L)

    @Test
    fun `scheduleDiscovery does nothing when task already running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        scheduler.scheduleDiscovery()

        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString()
        )
    }

    @Test
    fun `scheduleDiscovery writes FAILED to progressStore when repository save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        scheduler.scheduleDiscovery()

        Mockito.verify(progressStore).update(
            Mockito.anyString(),
            Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0),
            Mockito.any()
        )
    }

    @Test
    fun `scheduleDiscovery runs successfully`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doReturn(DiscoveryResult("SCHEDULED", DiscoveryStats())).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString()
        )

        scheduler.scheduleDiscovery()

        Mockito.verify(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString()
        )
    }

    @Test
    fun `scheduleDiscovery clears execution context on success`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = 99L)
            }
        Mockito.doReturn(DiscoveryResult("SCHEDULED", DiscoveryStats())).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString()
        )

        scheduler.scheduleDiscovery()

        Mockito.verify(progressStore).bindExecutionId("EXPERT_DISCOVERY", -1L, 99L)
        Mockito.verify(progressStore).clearExecutionContext("EXPERT_DISCOVERY", 99L)
    }

    @Test
    fun `scheduleDiscovery clears token context when save fails`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenThrow(RuntimeException("DB connection lost"))

        scheduler.scheduleDiscovery()

        Mockito.verify(progressStore).clearExecutionContext("EXPERT_DISCOVERY", -1L)
    }
}
