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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronExpression
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        // I-6：抢不到运行槽时没有新 task_execution、不发请求、也不排队补跑。
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `scheduleDiscovery starts a new run when a scheduled run already succeeded today (I-6)`() {
        // 旧实现（同步分支的本日已执行闸门）在这里直接 return：库里今天已有的 SUCCESS/PARTIAL_SUCCESS
        // 会封锁当天第二轮。I-6 要求两小时触发只受「当前是否有运行槽」约束。
        Mockito.`when`(
            repository.countActiveSince("EXPERT_DISCOVERY", "SCHEDULED", LocalDateTime.of(2026, 9, 28, 0, 0))
        ).thenReturn(1L)
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

        Mockito.verify(progressStore).tryStartWithToken(Mockito.anyString(), anyTaskProgress())
        Mockito.verify(repository).save(Mockito.any(TaskExecution::class.java))
        Mockito.verify(discoveryService).discover(
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
        // I-6：同步分支的「本日已执行」闸门已删除，历史执行记录不再封锁新轮；新模式更不同步 discover。
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

    // ------------------------------------------------------------------
    // I-6：两小时触发 —— 注解、时区、仓内默认值与初始化文案一起断言
    // ------------------------------------------------------------------

    @Test
    fun `the cron annotation pins the two hour schedule in Asia-Shanghai and the repository default matches (I-6)`() {
        val scheduledMethods = ExpertDiscoveryScheduler::class.java.methods
            .filter { it.getAnnotation(Scheduled::class.java) != null }
        assertEquals(1, scheduledMethods.size, "深度发现只能有一个 @Scheduled 触发点，不新增第二个定时任务")

        val annotation = scheduledMethods.first().getAnnotation(Scheduled::class.java)
        assertEquals("\${talent-introduction.expert-discovery.cron:-}", annotation.cron)
        assertEquals("Asia/Shanghai", annotation.zone, "触发时区必须显式固定，与部署机默认时区无关")

        // 仓内默认值（不是测试里手写的第二份字符串）：两小时一次。
        val yaml = Files.readString(Paths.get("src/main/resources/application.yml"))
        assertTrue(
            yaml.contains("cron: \${EXPERT_DISCOVERY_CRON:0 0 */2 * * ?}"),
            "application.yml 的默认 cron 必须是两小时一次（北京时间偶数整点）"
        )

        // 北京时间 2026-09-28 00:01 起算的下三个触发点。
        val cron = CronExpression.parse("0 0 */2 * * ?")
        val zone = ZoneId.of(annotation.zone)
        var cursor = ZonedDateTime.of(2026, 9, 28, 0, 1, 0, 0, zone)
        val next = (1..3).map {
            cursor = cron.next(cursor)!!
            cursor.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        }
        assertEquals(listOf("09-28 02:00", "09-28 04:00", "09-28 06:00"), next)
    }

    @Test
    fun `the initialization message states the scheduled trigger its cron zone and recovery delays (I-6)`() {
        val properties = ExpertDiscoveryProperties(cron = "0 0 */2 * * ?")
        val scheduler = ExpertDiscoveryScheduler(discoveryService, taskExecutionService, properties, progressStore)
        val initial = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        scheduler.scheduleDiscovery()

        Mockito.verify(progressStore).tryStartWithToken(
            Mockito.anyString(),
            initial.capture() ?: TaskProgress("", "", 0, 0, 0)
        )
        val message = initial.value.message!!
        assertTrue(message.contains("0 0 */2 * * ?"), "初始化文案必须显示实际生效的 cron：$message")
        assertTrue(message.contains("Asia/Shanghai"), "初始化文案必须显示触发时区：$message")
        assertTrue(message.contains("30s/120s/300s"), "初始化文案同时给出搜索恢复间隔：$message")
        assertFalse(message.contains("EuropePMC"), "不得再写误导性的初始化文案：$message")
    }
}
