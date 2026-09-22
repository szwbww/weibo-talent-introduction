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
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.discovery.service.PipelineTickSkipReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

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

    private val todayStart: LocalDateTime = LocalDate.now().atStartOfDay()

    private fun stubNoScheduledRunToday() {
        Mockito.`when`(
            repository.countActiveSince("EXPERT_DISCOVERY", "SCHEDULED", todayStart)
        ).thenReturn(0L)
    }

    @BeforeEach
    fun setUp() {
        stubNoScheduledRunToday()
    }

    @Test
    fun `scheduleDiscovery does nothing when task already running`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        scheduler.scheduleDiscovery()

        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `scheduleDiscovery skips when scheduled discovery already ran today`() {
        Mockito.`when`(
            repository.countActiveSince("EXPERT_DISCOVERY", "SCHEDULED", todayStart)
        ).thenReturn(1L)

        scheduler.scheduleDiscovery()

        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(
            Mockito.anyString(),
            anyTaskProgress()
        )
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `scheduleDiscovery proceeds when only FAILED scheduled run exists today`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doReturn(DiscoveryResult("SCHEDULED", DiscoveryStats())).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        scheduler.scheduleDiscovery()

        Mockito.verify(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
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
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        scheduler.scheduleDiscovery()

        Mockito.verify(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
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
            Mockito.anyString(),
            Mockito.anyBoolean()
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

    @Test
    fun `scheduleDiscovery criteria enables RND_TARGET subject scope`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doReturn(DiscoveryResult("SCHEDULED", DiscoveryStats())).`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        scheduler.scheduleDiscovery()

        val captor = ArgumentCaptor.forClass(PaperSearchCriteria::class.java)
        Mockito.verify(discoveryService).discover(
            captor.capture() ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        assertEquals(com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog.RND_TARGET, captor.value.subjectScope)
    }

    // ------------------------------------------------------------------
    // c3（I-1/I-3/I-4）：新模式入口只派发 02 的 tick，旧模式语义逐字保留
    // ------------------------------------------------------------------

    private val pipeline: DiscoveryPipelineService = Mockito.mock(DiscoveryPipelineService::class.java)

    private fun usePipeline(
        enabled: Boolean = true,
        tickResult: PipelineTickResult = PipelineTickResult(
            dispatched = true, state = PipelinePhase.RUNNING, phase = PipelinePhase.RUNNING,
            skipReason = null, waitReason = null, nextWakeAt = null, ownerToken = "owner"
        )
    ) {
        Mockito.`when`(pipeline.enabled).thenReturn(enabled)
        Mockito.`when`(pipeline.tick()).thenReturn(tickResult)
    }

    private fun continuousScheduler(
        properties: ExpertDiscoveryProperties,
        service: DiscoveryPipelineService? = pipeline
    ): ExpertDiscoveryScheduler =
        ExpertDiscoveryScheduler(discoveryService, taskExecutionService, properties, progressStore, service)

    @Test
    fun `continuous cron only dispatches the tick and ignores the once-per-day gate (I-1, I-4)`() {
        // 库里已经有「今天跑过一次」的 SCHEDULED 执行：旧模式会就此跳过，新模式必须照常续跑。
        Mockito.`when`(
            repository.countActiveSince("EXPERT_DISCOVERY", "SCHEDULED", todayStart)
        ).thenReturn(1L)
        usePipeline()
        val scheduler = continuousScheduler(ExpertDiscoveryProperties(pipelineEnabled = true))

        scheduler.scheduleDiscovery()
        scheduler.scheduleDiscovery()

        // 日内多窗口：两次触发都派发，没有「今天已跑一次」闸门。
        Mockito.verify(pipeline, Mockito.times(2)).tick()
        // 新入口绝不同步 discover、也绝不占用进度槽。
        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
    }

    @Test
    fun `a manually paused pipeline is never resumed by cron or the recovery tick (I-1, I-3)`() {
        usePipeline(
            tickResult = PipelineTickResult(
                dispatched = false, state = PipelineDesiredState.PAUSED, phase = PipelinePhase.WAITING,
                skipReason = PipelineTickSkipReason.PAUSED, waitReason = PipelineWaitReason.MANUAL_PAUSE,
                nextWakeAt = null, ownerToken = null
            )
        )
        val scheduler = continuousScheduler(ExpertDiscoveryProperties(pipelineEnabled = true))

        scheduler.scheduleDiscovery()
        scheduler.recoverPipelineTick()

        Mockito.verify(pipeline, Mockito.times(2)).tick()
        Mockito.verify(pipeline, Mockito.never()).launch(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        Mockito.verify(pipeline, Mockito.never()).resume()
        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `the recovery tick is registered with the configured interval exactly when the pipeline is enabled (I-4)`() {
        val enabled = ExpertDiscoveryProperties(pipelineEnabled = true, pipelineTick = Duration.ofSeconds(20))
        usePipeline()
        val registrar = ScheduledTaskRegistrar()

        continuousScheduler(enabled).configureTasks(registrar)

        val tasks = registrar.fixedDelayTaskList
        assertEquals(1, tasks.size)
        assertEquals(20_000L, tasks[0].interval)
        // 派发体只调用 02 的 tick —— 外部 API 的耗时永远不在调度线程上。
        tasks[0].runnable.run()
        Mockito.verify(pipeline).tick()

        val legacyRegistrar = ScheduledTaskRegistrar()
        continuousScheduler(ExpertDiscoveryProperties(pipelineEnabled = false)).configureTasks(legacyRegistrar)
        org.junit.jupiter.api.Assertions.assertTrue(legacyRegistrar.fixedDelayTaskList.isNullOrEmpty())
    }

    @Test
    fun `an enabled switch without the coordinator fails loudly (I-1)`() {
        val scheduler = continuousScheduler(ExpertDiscoveryProperties(pipelineEnabled = true), service = null)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            scheduler.scheduleDiscovery()
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            scheduler.recoverPipelineTick()
        }
    }

    @Test
    fun `a tick failure never kills the scheduler thread and never falls back to legacy (I-4)`() {
        usePipeline()
        Mockito.`when`(pipeline.tick()).thenThrow(RuntimeException("db down"))
        val scheduler = continuousScheduler(ExpertDiscoveryProperties(pipelineEnabled = true))

        scheduler.recoverPipelineTick()

        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
    }
}
