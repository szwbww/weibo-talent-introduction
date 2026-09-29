package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryStats
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSetting
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSettingRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.discovery.service.PipelineTickSkipReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.SimpleTriggerContext
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Delayed
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 深度发现调度的定向回归：保留的旧模式/连续模式语义 + 03 新增的「唯一动态 future / 小时 Trigger」。
 *
 * I-2 的小时周期一律用**固定时钟**直接问 Trigger（不用 `sleep` 等小时）；I-3 的代次/取消语义
 * 用捕获到的真实 Runnable/Trigger 与 fake future 断言。
 */
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

    private val scheduleRepository = Mockito.mock(DiscoveryScheduleSettingRepository::class.java)
    private val taskScheduler = RecordingTaskScheduler()

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
    // I-2（03）：唯一动态 future —— 固定时钟直接问 Trigger，不用 sleep 等小时
    // ------------------------------------------------------------------

    /** 北京时间 2026-09-29 10:00（= 02:00Z）。 */
    private val at10 = Instant.parse("2026-09-29T02:00:00Z")
    private val at11 = Instant.parse("2026-09-29T03:00:00Z")
    private val at12 = Instant.parse("2026-09-29T04:00:00Z")
    private val at13 = Instant.parse("2026-09-29T05:00:00Z")
    private val at16 = Instant.parse("2026-09-29T08:00:00Z")
    private val at17 = Instant.parse("2026-09-29T09:00:00Z")
    private val at19 = Instant.parse("2026-09-29T11:00:00Z")

    private fun withStoredSetting(intervalHours: Int, anchor: Instant) {
        Mockito.doReturn(DiscoveryScheduleSetting(id = 1, intervalHours = intervalHours, updatedAt = anchor))
            .`when`(scheduleRepository).find()
    }

    /** 用 doThrow 重设存根：`Mockito.\`when\``（调用式）会在旧存根已经抛异常时再次抛出。 */
    private fun withSettingReadFailure(message: String) {
        Mockito.doThrow(DataAccessResourceFailureException(message)).`when`(scheduleRepository).find()
    }

    /** CONFIG 模式走 Spring 的 CronTrigger：必须显式给出上下文，否则它会用真实系统时间。 */
    private fun at10Context(): SimpleTriggerContext =
        SimpleTriggerContext(Date.from(at10), Date.from(at10), Date.from(at10))

    private fun dynamicScheduler(
        properties: ExpertDiscoveryProperties = ExpertDiscoveryProperties(cron = "0 0 */2 * * ?"),
        clock: Clock = Clock.fixed(at10, ZoneOffset.UTC)
    ): ExpertDiscoveryScheduler = ExpertDiscoveryScheduler(
        discoveryService, taskExecutionService, properties, progressStore,
        pipelineService = null, scheduleSettingRepository = scheduleRepository,
        taskScheduler = taskScheduler, clock = clock
    )

    private fun nextAfter(task: RecordedTask, completedAt: Instant): Date? =
        task.trigger.nextExecutionTime(
            SimpleTriggerContext(Date.from(completedAt), Date.from(completedAt), Date.from(completedAt))
        )

    @Test
    fun `sync mode registers exactly one dynamic trigger instead of the removed Scheduled annotation (I-4)`() {
        // 唯一动态 future：类上不得再有任何 @Scheduled 注册点。
        val scheduledMethods = ExpertDiscoveryScheduler::class.java.methods
            .filter { it.getAnnotation(Scheduled::class.java) != null }
        assertTrue(scheduledMethods.isEmpty(), "同步模式必须只由唯一的动态 future 触发")

        Mockito.doReturn(null).`when`(scheduleRepository).find()
        val scheduler = dynamicScheduler()

        assertTrue(scheduler.reload())

        assertEquals(1, taskScheduler.tasks.size, "同一时刻只能有一个有效注册")
        assertEquals(
            Date.from(at12),
            taskScheduler.latest.trigger.nextExecutionTime(at10Context()),
            "无设置时沿用部署 cron（每偶数小时整点，北京时间）"
        )
        assertNull(scheduler.snapshot().anchorAt)
        assertEquals(DiscoveryScheduleSpec.SOURCE_CONFIG, scheduler.snapshot().source)
        assertEquals(2, scheduler.snapshot().intervalHours, "默认 cron 被识别为 2 小时")
    }

    @Test
    fun `a saved interval drives the candidates from the anchor with a fixed clock (I-2)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()

        assertTrue(scheduler.reload())

        val task = taskScheduler.latest
        assertEquals(Date.from(at13), task.trigger.nextExecutionTime(at10Context()), "保存即刻的下一候选 = 锚点 + 3 小时")
        // 13:00、16:00、19:00 —— 只按「锚点 + k×N」推进，不按日内 cron 取模。
        assertEquals(Date.from(at16), nextAfter(task, at13))
        assertEquals(Date.from(at19), nextAfter(task, at16))
        // 保存动作本身绝不启动发现。
        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        val snapshot = scheduler.snapshot()
        assertEquals(DiscoveryScheduleSpec.SOURCE_OVERRIDE, snapshot.source)
        assertEquals(3, snapshot.intervalHours)
        assertEquals(at10, snapshot.anchorAt)
        assertEquals(at13, snapshot.nextTriggerAt)
        assertTrue(snapshot.registered)
        assertNull(snapshot.reason)
    }

    @Test
    fun `a five hour interval crosses midnight without changing its length (I-2)`() {
        withStoredSetting(5, at10)
        val scheduler = dynamicScheduler()

        assertTrue(scheduler.reload())

        val task = taskScheduler.latest
        val at15 = Instant.parse("2026-09-29T07:00:00Z")
        val at20 = Instant.parse("2026-09-29T12:00:00Z")
        val nextDay01 = Instant.parse("2026-09-29T17:00:00Z")
        assertEquals(Date.from(at15), task.trigger.nextExecutionTime(at10Context()))
        assertEquals(Date.from(at20), nextAfter(task, at15))
        assertEquals(Date.from(nextDay01), nextAfter(task, at20), "跨午夜仍是 5 小时间隔")
    }

    @Test
    fun `a restart reuses the stored anchor and never catches up missed slots (I-2)`() {
        // 停机到 17:00 后重启：锚点仍是 10:00，只注册下一个**未来**槽（19:00），不补 13/16 点。
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler(clock = Clock.fixed(at17, ZoneOffset.UTC))

        assertTrue(scheduler.reload())

        val next = taskScheduler.latest.trigger.nextExecutionTime(at10Context())
        assertEquals(Date.from(at19), next)
        assertTrue(next.toInstant().isAfter(Instant.parse("2026-09-29T09:00:00Z")))
        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `a long run that crosses several slots resumes at the next future slot only (I-2)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()

        assertTrue(scheduler.reload())

        // 上一轮 13:20 才结束：13:00 的槽已过去，下一候选必须是 16:00（不是立刻补跑 13 点）。
        val completedLate = Instant.parse("2026-09-29T05:20:00Z")
        assertEquals(Date.from(at16), nextAfter(taskScheduler.latest, completedLate))
    }

    @Test
    fun `a trigger never schedules at or before the anchor even with a skewed clock (I-2)`() {
        withStoredSetting(3, at13)
        // 时钟比锚点早 3 小时（时钟回拨/机器漂移）：k 至少为 1，绝不给锚点或更早的点。
        val scheduler = dynamicScheduler(clock = Clock.fixed(at10, ZoneOffset.UTC))

        assertTrue(scheduler.reload())

        assertEquals(Date.from(at16), taskScheduler.latest.trigger.nextExecutionTime(at10Context()))
    }

    @Test
    fun `the same value saved again reuses the stored anchor (I-1, I-3)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler(clock = Clock.fixed(at11, ZoneOffset.UTC))

        assertTrue(scheduler.reload())

        // 11:00 重存 3 小时：下一候选仍是 13:00（锚点没被重置成 11:00 → 14:00）。
        assertEquals(Date.from(at13), taskScheduler.latest.trigger.nextExecutionTime(at10Context()))
        assertEquals(at10, scheduler.snapshot().anchorAt)
    }

    @Test
    fun `a reschedule cancels the previous future with cancel(false) and keeps one active generation (I-3)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()

        assertTrue(scheduler.reload())
        val first = taskScheduler.latest

        assertTrue(scheduler.reload())

        assertEquals(listOf(false), first.future.cancelCalls, "取消旧 future 必须用 cancel(false)")
        assertEquals(2, taskScheduler.tasks.size)
    }

    @Test
    fun `a cancelled generation's late callback never starts a task (I-3)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
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

        assertTrue(scheduler.reload())
        val stale = taskScheduler.latest.task
        assertTrue(scheduler.reload())

        stale.run()

        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        Mockito.verify(progressStore, Mockito.never()).tryStartWithToken(Mockito.anyString(), anyTaskProgress())

        // 新代次的回调仍然正常启动。
        taskScheduler.latest.task.run()
        Mockito.verify(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.eq("SCHEDULED") ?: "SCHEDULED",
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `a scheduled callback skips without queueing a make-up run when the slot is busy (I-2)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        assertTrue(scheduler.reload())
        taskScheduler.latest.task.run()

        Mockito.verify(discoveryService, Mockito.never()).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `a scheduled callback runs the same RND_TARGET criteria as the legacy cron (I-4)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
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

        assertTrue(scheduler.reload())
        taskScheduler.latest.task.run()

        val captor = ArgumentCaptor.forClass(PaperSearchCriteria::class.java)
        val scanCaptor = ArgumentCaptor.forClass(Boolean::class.java)
        Mockito.verify(discoveryService).discover(
            captor.capture() ?: PaperSearchCriteria(),
            Mockito.eq("SCHEDULED") ?: "SCHEDULED",
            scanCaptor.capture() ?: false
        )
        val criteria = captor.value
        assertEquals(listOf("CN"), criteria.excludeCountries, "定时发现仍排除中国")
        assertEquals(true, criteria.openAccessOnly)
        assertEquals(
            com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog.RND_TARGET,
            criteria.subjectScope
        )
        assertEquals(discoveryProperties.includeRawScan, scanCaptor.value, "includeRawScan 仍沿用配置")
        assertTrue(criteria.keywords.isNullOrEmpty(), "小时设置不得把手动关键词偷偷变成定时条件")
    }

    @Test
    fun `rescheduling during a running discovery returns promptly and never interrupts it (I-3, P-4)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }
        Mockito.doAnswer {
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            DiscoveryResult("SCHEDULED", DiscoveryStats())
        }.`when`(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )

        assertTrue(scheduler.reload())
        val first = taskScheduler.latest

        val runner = Thread { scheduler.scheduleDiscovery() }
        runner.start()
        assertTrue(entered.await(10, TimeUnit.SECONDS), "同步发现必须已在运行")

        val startedAt = System.nanoTime()
        assertTrue(scheduler.reload(), "运行中保存同样必须立刻返回并完成重排")
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(elapsedMs < 5_000, "重排不得等待运行中的发现结束（${elapsedMs}ms）")
        assertEquals(listOf(false), first.future.cancelCalls)

        release.countDown()
        runner.join(10_000)
        assertFalse(runner.isAlive, "运行中的发现必须能跑完（cancel(false) 不打断）")
        Mockito.verify(discoveryService).discover(
            Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
            Mockito.anyString(),
            Mockito.anyBoolean()
        )
    }

    @Test
    fun `a failed registration reports applied=false and does not lie about the old schedule (I-3)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
        taskScheduler.failNextSchedule = true

        assertFalse(scheduler.reload())

        val snapshot = scheduler.snapshot()
        assertFalse(snapshot.registered)
        assertNull(snapshot.nextTriggerAt)
        assertEquals(DiscoveryScheduleSpec.REASON_APPLY_FAILED, snapshot.reason)
    }

    @Test
    fun `a setting read failure never registers a sync schedule and keeps the old one (I-1, I-4)`() {
        val scheduler = dynamicScheduler()
        withSettingReadFailure("db down")

        scheduler.onApplicationReady()

        assertTrue(taskScheduler.tasks.isEmpty(), "读取失败时不得按旧默认频率注册同步发现")
        assertFalse(scheduler.snapshot().registered)
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, scheduler.snapshot().reason)

        // 已有有效 future 时，读取失败**不取消**原调度。
        withStoredSetting(3, at10)
        assertTrue(scheduler.reload())
        val registered = taskScheduler.latest
        withSettingReadFailure("db down again")

        assertFalse(scheduler.reload())

        assertTrue(registered.future.cancelCalls.isEmpty(), "读取失败不得取消原调度")
        assertEquals(DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE, scheduler.snapshot().reason)
    }

    @Test
    fun `cron disabled keeps the sync schedule unregistered and the recovery tick intact (I-4)`() {
        Mockito.doReturn(null).`when`(scheduleRepository).find()
        val scheduler = dynamicScheduler(properties = ExpertDiscoveryProperties(cron = "-"))

        assertFalse(scheduler.reload())

        assertTrue(taskScheduler.tasks.isEmpty(), "cron=- 时不注册同步触发")
        assertFalse(scheduler.snapshot().registered)
        assertEquals(DiscoveryScheduleSpec.REASON_CRON_DISABLED, scheduler.snapshot().reason)
        assertNull(scheduler.snapshot().nextTriggerAt)

        // 即使库里留有历史小时设置，cron=- 的禁用语义优先：不注册、不复活。
        withStoredSetting(3, at10)
        val withStaleSetting = dynamicScheduler(properties = ExpertDiscoveryProperties(cron = "-"))
        assertFalse(withStaleSetting.reload())
        assertTrue(taskScheduler.tasks.isEmpty(), "cron=- 时遗留的小时设置不得复活同步触发")
        assertEquals(DiscoveryScheduleSpec.REASON_CRON_DISABLED, withStaleSetting.snapshot().reason)
        assertNull(withStaleSetting.snapshot().nextTriggerAt)

        // cron=- 只停 cron：连续模式的恢复 tick 不受影响。
        usePipeline()
        val registrar = ScheduledTaskRegistrar()
        continuousScheduler(ExpertDiscoveryProperties(cron = "-", pipelineEnabled = true)).configureTasks(registrar)
        assertEquals(1, registrar.fixedDelayTaskList.size)
        registrar.fixedDelayTaskList[0].runnable.run()
        Mockito.verify(pipeline).tick()
    }

    @Test
    fun `continuous mode keeps its cron tick and never applies the hourly setting (I-4)`() {
        // 库里即使已有小时设置，连续模式也不读它、不应用它（否则页面限速会被 30 秒 tick 绕过）。
        usePipeline()
        withStoredSetting(5, at10)
        val scheduler = dynamicScheduler(
            properties = ExpertDiscoveryProperties(cron = "0 0 */2 * * ?", pipelineEnabled = true)
        )

        assertTrue(scheduler.reload())

        Mockito.verify(scheduleRepository, Mockito.never()).find()
        assertEquals(Date.from(at12), taskScheduler.latest.trigger.nextExecutionTime(at10Context()))
        val snapshot = scheduler.snapshot()
        assertEquals(DiscoveryScheduleSpec.MODE_CONTINUOUS, snapshot.mode)
        assertEquals(DiscoveryScheduleSpec.SOURCE_CONFIG, snapshot.source)
        assertNull(snapshot.nextTriggerAt, "连续模式不返回一个仿佛已应用的小时计划")
        assertNull(snapshot.anchorAt)
    }

    // ------------------------------------------------------------------
    // I-6/03：仓内默认 cron 与初始化文案一起断言
    // ------------------------------------------------------------------

    @Test
    fun `only the exact two hour cron is recognized as two hours and the repository default matches (I-1)`() {
        val yaml = Files.readString(Paths.get("src/main/resources/application.yml"))
        assertTrue(
            yaml.contains("cron: \${EXPERT_DISCOVERY_CRON:0 0 */2 * * ?}"),
            "application.yml 的默认 cron 必须仍是两小时一次（北京时间偶数整点）"
        )
        assertEquals(2, DiscoveryScheduleSpec.configuredIntervalHours("0 0 */2 * * ?"))
        assertEquals(2, DiscoveryScheduleSpec.configuredIntervalHours(" 0 0 */2 * * ? "), "仅忽略首尾空白")
        assertNull(DiscoveryScheduleSpec.configuredIntervalHours("0 0 2 * * ?"))
        assertNull(DiscoveryScheduleSpec.configuredIntervalHours("0 0 */3 * * ?"))

        // 同一 cron 在同一固定时刻的下三个触发点（北京时间）。
        val cron = CronExpression.parse("0 0 */2 * * ?")
        var cursor = ZonedDateTime.of(2026, 9, 28, 0, 1, 0, 0, ZoneId.of("Asia/Shanghai"))
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

    @Test
    fun `the initialization message states the applied hour interval when one is saved (I-1)`() {
        withStoredSetting(3, at10)
        val scheduler = dynamicScheduler()
        val initial = ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(notStartedToken())

        assertTrue(scheduler.reload())
        taskScheduler.latest.task.run()

        Mockito.verify(progressStore).tryStartWithToken(
            Mockito.anyString(),
            initial.capture() ?: TaskProgress("", "", 0, 0, 0)
        )
        val message = initial.value.message!!
        assertTrue(message.contains("间隔=3小时"), "初始化文案必须显示已保存的小时周期：$message")
        assertTrue(message.contains("30s/120s/300s"), "初始化文案同时给出搜索恢复间隔：$message")
    }

    // ------------------------------------------------------------------
    // 测试替身：捕获真实注册（Runnable + Trigger）与 fake future
    // ------------------------------------------------------------------

    private class RecordedTask(
        val task: Runnable,
        val trigger: Trigger,
        val future: FakeScheduledFuture
    )

    private class FakeScheduledFuture : ScheduledFuture<Any> {
        val cancelCalls = mutableListOf<Boolean>()
        private var cancelled = false
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            cancelCalls += mayInterruptIfRunning
            cancelled = true
            return true
        }
        override fun isCancelled(): Boolean = cancelled
        override fun isDone(): Boolean = cancelled
        override fun get(): Any = throw UnsupportedOperationException("fake future")
        override fun get(timeout: Long, unit: TimeUnit): Any = throw UnsupportedOperationException("fake future")
        override fun getDelay(unit: TimeUnit): Long = 0L
        override fun compareTo(other: Delayed): Int = 0
    }

    private class RecordingTaskScheduler : TaskScheduler {
        val tasks = mutableListOf<RecordedTask>()
        var failNextSchedule = false
        val latest: RecordedTask get() = tasks.last()

        override fun schedule(task: Runnable, trigger: Trigger): ScheduledFuture<*> {
            if (failNextSchedule) {
                failNextSchedule = false
                throw IllegalStateException("cannot schedule")
            }
            val recorded = RecordedTask(task, trigger, FakeScheduledFuture())
            tasks += recorded
            return recorded.future
        }

        override fun schedule(task: Runnable, startTime: Date): ScheduledFuture<*> =
            throw UnsupportedOperationException("固定时刻注册不得使用")

        override fun scheduleAtFixedRate(task: Runnable, startTime: Date, period: Long): ScheduledFuture<*> =
            throw UnsupportedOperationException("I-2 禁止会追补过期周期的 scheduleAtFixedRate")

        override fun scheduleAtFixedRate(task: Runnable, period: Long): ScheduledFuture<*> =
            throw UnsupportedOperationException("I-2 禁止会追补过期周期的 scheduleAtFixedRate")

        override fun scheduleWithFixedDelay(task: Runnable, startTime: Date, delay: Long): ScheduledFuture<*> =
            throw UnsupportedOperationException("同步发现不得使用固定延迟注册")

        override fun scheduleWithFixedDelay(task: Runnable, delay: Long): ScheduledFuture<*> =
            throw UnsupportedOperationException("同步发现不得使用固定延迟注册")
    }
}
