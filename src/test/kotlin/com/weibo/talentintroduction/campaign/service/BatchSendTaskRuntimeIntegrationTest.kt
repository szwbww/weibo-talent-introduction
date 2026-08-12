package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchOutcomeReasonCodes
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.domain.Campaign
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest
import com.weibo.talentintroduction.campaign.domain.OutcomeAccumulator
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.repository.CampaignRepository
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.repository.MailSendAttemptRepository
import com.weibo.talentintroduction.config.ManualOutreachProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.config.WarmupProperties
import com.weibo.talentintroduction.config.WarmupStep
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.repository.MailSenderAccountRepository
import com.weibo.talentintroduction.mail.service.AccountRateLimiter
import com.weibo.talentintroduction.mail.service.AutoReplySettingService
import com.weibo.talentintroduction.mail.service.EmailSuppressionService
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.ProviderResolver
import com.weibo.talentintroduction.mail.service.SenderAccountBindingService
import com.weibo.talentintroduction.mail.service.SenderAccountSelfCheckService
import com.weibo.talentintroduction.mail.service.SenderWarmupService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.BatchSendScheduler
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledFuture

/**
 * Task 4.1 — batch send runtime + config-scoped execution logs.
 */
class BatchSendTaskRuntimeIntegrationTest {

    // ─── I-2: scheduler per configId ─────────────────────────────────────────

    @Test
    fun `scheduleInitial registers one future per enabled config`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val controlService = Mockito.mock(BatchSendControlService::class.java)
        val taskScheduler = Mockito.mock(TaskScheduler::class.java)
        val future = Mockito.mock(ScheduledFuture::class.java)
        Mockito.`when`(configRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig(1L), enabledConfig(2L, cron = "0 0 8 * * ?")))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(future)

        BatchSendScheduler(configRepository, controlService, taskScheduler).scheduleInitial()

        Mockito.verify(taskScheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `cron change cancels old future with cancel false and reschedules`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val controlService = Mockito.mock(BatchSendControlService::class.java)
        val taskScheduler = Mockito.mock(TaskScheduler::class.java)
        val future = Mockito.mock(ScheduledFuture::class.java)
        Mockito.`when`(configRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig(1L)))
            .thenReturn(listOf(enabledConfig(1L, cron = "0 0 9 * * ?")))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(future)

        val scheduler = BatchSendScheduler(configRepository, controlService, taskScheduler)
        scheduler.scheduleInitial()
        scheduler.onCronChanged(BatchSendCronChangedEvent("0 0 0 * * ?", "0 0 9 * * ?"))

        Mockito.verify(future).cancel(false)
        Mockito.verify(taskScheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `disabled config removed from enabled list cancels its future`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val controlService = Mockito.mock(BatchSendControlService::class.java)
        val taskScheduler = Mockito.mock(TaskScheduler::class.java)
        val future1 = Mockito.mock(ScheduledFuture::class.java)
        val future2 = Mockito.mock(ScheduledFuture::class.java)
        var call = 0
        Mockito.`when`(configRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig(1L), enabledConfig(2L, cron = "0 0 8 * * ?")))
            .thenReturn(listOf(enabledConfig(1L)))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenAnswer {
                if (call++ == 0) future1 else future2
            }

        val scheduler = BatchSendScheduler(configRepository, controlService, taskScheduler)
        scheduler.scheduleInitial()
        scheduler.onCronChanged(BatchSendCronChangedEvent("0 0 8 * * ?", "0 0 8 * * ?"))

        Mockito.verify(future2).cancel(false)
        Mockito.verify(taskScheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `deleted config absent from reload cancels future without interrupt`() {
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val controlService = Mockito.mock(BatchSendControlService::class.java)
        val taskScheduler = Mockito.mock(TaskScheduler::class.java)
        val future = Mockito.mock(ScheduledFuture::class.java)
        Mockito.`when`(configRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig(1L)))
            .thenReturn(emptyList())
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(future)

        val scheduler = BatchSendScheduler(configRepository, controlService, taskScheduler)
        scheduler.scheduleInitial()
        scheduler.onCronChanged(BatchSendCronChangedEvent("0 0 0 * * ?", "0 0 0 * * ?"))

        Mockito.verify(future).cancel(false)
    }

    // ─── I-1/I-2: payloads + batch_config_id ─────────────────────────────────

    @Test
    fun `startScheduled writes SCHEDULED trigger sourceConfigId and batch_config_id`() {
        val ctx = controlContext()
        val config = enabledConfig(1L, dailyCap = 80)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(config)

        val response = ctx.control.startScheduled(1L)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("SCHEDULED", ctx.capturedTriggers.single())
        assertEquals(1L, ctx.capturedBatchConfigIds.single())
        val request = ctx.objectMapper.convertValue(ctx.capturedRequests.single(), ManualBatchExecutionRequest::class.java)
        assertEquals(1L, request.sourceConfigId)
        assertEquals(80, request.snapshot.dailyCap)
        assertEquals(config.updatedAt, request.sourceUpdatedAt)
    }

    @Test
    fun `startManualFromConfig writes MANUAL trigger and config batch_config_id`() {
        val ctx = controlContext()
        val config = enabledConfig(2L, dailyCap = 60)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(config)

        val response = ctx.control.startManualFromConfig(2L)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("MANUAL", ctx.capturedTriggers.single())
        assertEquals(2L, ctx.capturedBatchConfigIds.single())
        val request = ctx.objectMapper.convertValue(ctx.capturedRequests.single(), ManualBatchExecutionRequest::class.java)
        assertEquals(2L, request.sourceConfigId)
        assertEquals(60, request.snapshot.dailyCap)
    }

    @Test
    fun `independent manual has null batch_config_id and null sourceConfigId`() {
        val ctx = controlContext()
        val request = ManualBatchExecutionRequest(null, null, baseSnapshot(dailyCap = 25))

        val response = ctx.control.startManual(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertNull(ctx.capturedBatchConfigIds.single())
        val stored = ctx.objectMapper.convertValue(ctx.capturedRequests.single(), ManualBatchExecutionRequest::class.java)
        assertNull(stored.sourceConfigId)
        assertEquals(25, stored.snapshot.dailyCap)
    }

    @Test
    fun `mid-run config edit does not change snapshot passed to run`() {
        val ctx = controlContext()
        val original = enabledConfig(1L, dailyCap = 50, discipline = "STEM", tagsJson = """["alpha"]""")
        val configHolder = mutableListOf(original)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(1L)).thenAnswer { configHolder.last() }
        Mockito.doAnswer { invocation ->
            configHolder[0] = original.copy(dailyCap = 999, discipline = "HUMANITIES", tagsJson = """["beta"]""")
            ctx.capturedSnapshots.add(invocation.getArgument(0))
            ManualOutreachResult(0, 0, 0, 0, false, "COMPLETED")
        }.`when`(ctx.manualInitialOutreachService).run(
            anyValue(baseSnapshot()), eqValue(101L), eqValue(ExecutionMode.AUTO), anyValue(false)
        )

        ctx.control.startScheduled(1L)

        val captured = ctx.capturedSnapshots.single()
        assertEquals(50, captured.dailyCap)
        assertEquals("STEM", captured.discipline)
        assertEquals(listOf("alpha"), captured.tags)
    }

    // ─── I-3: RecipientScope parity ──────────────────────────────────────────

    @Test
    fun `null funnel expands to CANDIDATE and APPLICATION excluding RAW`() {
        val scope = RecipientScope.fromSnapshot(baseSnapshot(funnelLevel = null))
        assertEquals(setOf("CANDIDATE", "APPLICATION"), scope.funnelLevels)
        assertFalse(scope.funnelLevels.contains("RAW"))
    }

    @Test
    fun `tags use OR within field discipline and provider use AND`() {
        val scope = RecipientScope.fromSnapshot(
            baseSnapshot(tags = listOf("t1", "t2"), emailDomain = "edu.cn", discipline = "STEM")
        )
        assertTrue(scope.matchesExpert(expert("0001", "a@edu.cn", tags = listOf("t2"), discipline = "STEM")))
        assertFalse(scope.matchesExpert(expert("0002", "b@edu.cn", tags = listOf("other"), discipline = "STEM")))
        assertFalse(scope.matchesExpert(expert("0003", "c@gmail.com", tags = listOf("t1"), discipline = "STEM")))
        assertFalse(scope.matchesExpert(expert("0004", "d@edu.cn", tags = listOf("t1"), discipline = "HUMANITIES")))
    }

    @Test
    fun `retry path applies same scope filters as ES matcher`() {
        val outreach = buildManualOutreachService()
        val scope = RecipientScope.fromSnapshot(
            baseSnapshot(funnelLevel = "APPLICATION", tags = listOf("hot"), emailDomain = "mit.edu", discipline = "STEM")
        )
        stubRetryContacts(
            outreach,
            campaignId = 10L,
            contacts = listOf(contact(1L, "0001", 10L), contact(2L, "0002", 10L), contact(3L, "0003", 10L)),
            profilesByLevel = mapOf(
                "APPLICATION" to listOf(
                    expert("0001", "a@mit.edu", tags = listOf("hot"), discipline = "STEM"),
                    expert("0002", "b@mit.edu", tags = listOf("cold"), discipline = "STEM"),
                    expert("0003", "c@gmail.com", tags = listOf("hot"), discipline = "STEM")
                )
            )
        )
        val (targets, _) = invokeBuildRetryableTargets(outreach.service, 10L, scope)
        assertEquals(1, targets.size)
        assertEquals("0001", targets[0].second.orcidId)
    }

    @Test
    fun `ES count path queries every funnel level in scope`() {
        val outreach = buildManualOutreachService()
        val scope = RecipientScope.fromSnapshot(baseSnapshot(funnelLevel = null, emailDomain = "edu.cn"))
        Mockito.`when`(outreach.expertSearchService.countExperts(eqValue(ExpertIndexLevel.CANDIDATE), anyValue(emptyList())))
            .thenReturn(3L)
        Mockito.`when`(outreach.expertSearchService.countExperts(eqValue(ExpertIndexLevel.APPLICATION), anyValue(emptyList())))
            .thenReturn(2L)

        assertEquals(5, invokeCountEsTargets(outreach.service, scope))
    }

    // ─── I-4: template gate ────────────────────────────────────────────────────

    @Test
    fun `disabled template at launch returns 422 and never calls outreach`() {
        val ctx = controlContext()
        val config = enabledConfig(1L, templateId = 42L)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(config)
        Mockito.`when`(ctx.mailComposeTemplateService.getById(42L)).thenReturn(
            com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                id = 42L, templateCode = null, templateName = "intro", subject = "s",
                description = null, mailType = "INTRODUCTION", subjectVariants = null,
                enabled = false, blocks = emptyList(), createdAt = null, updatedAt = null
            )
        )

        val response = ctx.control.startScheduled(1L)

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertTrue(response.body?.get("message").toString().contains("禁用"))
        Mockito.verifyNoInteractions(ctx.manualInitialOutreachService)
        Mockito.verify(ctx.manualOutreachExecutor, Mockito.never()).execute(Mockito.any())
        Mockito.verifyNoInteractions(ctx.taskExecutionService)
    }

    // ─── I-5: daily cap ────────────────────────────────────────────────────────

    @Test
    fun `same config second run no longer queries today sum (I-2)`() {
        val ctx = dailyCapContextWithTodaySum(5L, 7)
        val config = enabledConfig(5L, dailyCap = 10)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(config)

        val response = ctx.control.startManualFromConfig(5L)

        // I-2: launch succeeds even though today-sum (7) is close to dailyCap (10)
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(
            Mockito.mockingDetails(ctx.taskExecutionService).invocations
                .none { it.method.name == "sumSuccessCountTodayByBatchConfigId" }
        )
    }

    @Test
    fun `config daily cap reached no longer blocks launch via sumSuccessCountToday (I-2)`() {
        val ctx = dailyCapContextWithTodaySum(5L, 10)
        val config = enabledConfig(5L, dailyCap = 10)
        Mockito.`when`(ctx.configRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(config)

        val response = ctx.control.startManualFromConfig(5L)

        // I-2: sum(10) >= dailyCap(10) no longer rejects the launch
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(
            Mockito.mockingDetails(ctx.taskExecutionService).invocations
                .none { it.method.name == "sumSuccessCountTodayByBatchConfigId" }
        )
    }

    @Test
    fun `independent manual does not query config daily sum`() {
        val ctx = dailyCapContext()
        ctx.control.startManual(ManualBatchExecutionRequest(null, null, baseSnapshot(dailyCap = 5)))
        assertTrue(
            Mockito.mockingDetails(ctx.taskExecutionService).invocations
                .none { it.method.name == "sumSuccessCountTodayByBatchConfigId" }
        )
    }

    // ─── I-6: outcome conservation ───────────────────────────────────────────

    @Test
    fun `target equals success plus failure plus skipped plus remaining`() {
        val acc = OutcomeAccumulator(target = 10)
        repeat(3) { acc.recordSuccess() }
        repeat(2) { acc.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION) }
        acc.recordSkipped(BatchOutcomeReasonCodes.DEDUP)
        val breakdown = acc.toBreakdown()
        assertEquals(10, breakdown.success + breakdown.failure + breakdown.skipped + breakdown.remaining)
    }

    @Test
    fun `cancel annotates remaining as CANCELLED skipped`() {
        val acc = OutcomeAccumulator(target = 5)
        repeat(2) { acc.recordSuccess() }
        acc.annotateTerminalRemaining("CANCELLED")
        val breakdown = acc.toBreakdown()
        assertEquals(0, breakdown.remaining)
        assertEquals(3, breakdown.skipped)
        assertEquals(3, breakdown.skippedReasons[BatchOutcomeReasonCodes.CANCELLED]?.count)
    }

    @Test
    fun `failure and skipped reason sums match totals`() {
        val acc = OutcomeAccumulator(target = 8)
        repeat(2) { acc.recordFailure(BatchOutcomeReasonCodes.SEND_EXCEPTION) }
        acc.recordFailure(BatchOutcomeReasonCodes.TEMPLATE_RENDER_FAILED)
        repeat(2) { acc.recordSkipped(BatchOutcomeReasonCodes.SUPPRESSED) }
        val breakdown = acc.toBreakdown()
        assertEquals(breakdown.failure, breakdown.failureReasons.values.sumOf { it.count })
        assertEquals(breakdown.skipped, breakdown.skippedReasons.values.sumOf { it.count })
    }

    // ─── I-7: config-scoped logs ───────────────────────────────────────────────

    @Test
    fun `listRecentByBatchConfigId returns only matching config records`() {
        val repository = Mockito.mock(TaskExecutionRepository::class.java)
        val service = TaskExecutionService(repository, objectMapper, MailSchedulingProperties())
        val execA1 = execution(11L, 1L, "SCHEDULED")
        val execA2 = execution(12L, 1L, "MANUAL")
        Mockito.`when`(repository.findRecentByBatchConfigId(1L, 50)).thenReturn(listOf(execA1, execA2))

        val rows = service.listRecentByBatchConfigId(1L, 50)

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.batchConfigId == 1L })
    }

    @Test
    fun `soft-deleted config logs remain queryable by batch_config_id`() {
        val repository = Mockito.mock(TaskExecutionRepository::class.java)
        val service = TaskExecutionService(repository, objectMapper, MailSchedulingProperties())
        val historical = execution(200L, 99L, "SCHEDULED")
        Mockito.`when`(repository.findRecentByBatchConfigId(99L, 10)).thenReturn(listOf(historical))

        val rows = service.listRecentByBatchConfigId(99L, 10)

        assertEquals(1, rows.size)
        assertEquals(99L, rows[0].batchConfigId)
        assertNotNull(rows[0].id)
    }

    // ─── fixtures ──────────────────────────────────────────────────────────────

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    private data class ControlContext(
        val progressStore: TaskProgressStore,
        val taskExecutionService: TaskExecutionService,
        val manualInitialOutreachService: ManualInitialOutreachService,
        val configRepository: BatchSendTaskConfigRepository,
        val mailComposeTemplateService: MailComposeTemplateService,
        val manualOutreachExecutor: Executor,
        val control: BatchSendControlService,
        val objectMapper: ObjectMapper,
        val capturedRequests: MutableList<Any>,
        val capturedBatchConfigIds: MutableList<Long?>,
        val capturedTriggers: MutableList<String>,
        val capturedSnapshots: MutableList<BatchExecutionSnapshot>
    )

    private data class DailyCapContext(
        val taskExecutionService: TaskExecutionService,
        val manualInitialOutreachService: ManualInitialOutreachService,
        val configRepository: BatchSendTaskConfigRepository,
        val control: BatchSendControlService
    )

    private data class ManualOutreachHarness(
        val service: ManualInitialOutreachService,
        val expertSearchService: ExpertSearchService,
        val expertContactRepository: ExpertContactRepository,
        val mailRecordRepository: MailRecordRepository
    )

    private fun dailyCapContextWithTodaySum(configId: Long, sum: Int): DailyCapContext {
        val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
        // Kotlin default-arg call site becomes (configId, LocalDateTime).
        Mockito.doReturn(sum).`when`(taskExecutionService).sumSuccessCountTodayByBatchConfigId(
            eqValue(configId),
            anyValue(LocalDateTime.now())
        )
        val progressStore = Mockito.mock(TaskProgressStore::class.java)
        val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
        val manualOutreachExecutor = Mockito.mock(Executor::class.java)

        Mockito.doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))
        Mockito.doAnswer { Pair(true, -1L) }
            .`when`(progressStore).tryStartWithToken(
                eqValue(BatchSendControlService.TASK_TYPE),
                anyValue(TaskProgress(BatchSendControlService.TASK_TYPE, "RUNNING", 0, 0, 0))
            )
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(100)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(50L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> ManualOutreachResult>(5)
            block()
            Pair(
                TaskExecution(
                    id = 50L, taskType = BatchSendControlService.TASK_TYPE, triggerType = "MANUAL",
                    status = "SUCCESS", requestPayload = null, resultSummary = null, startedAt = LocalDateTime.now()
                ),
                ManualOutreachResult(1, 1, 0, 0, false)
            )
        }.`when`(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            anyValue(""),
            anyValue(Any()),
            anyValue { _: Long -> },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
        Mockito.doReturn(ManualOutreachResult(1, 1, 0, 0, false))
            .`when`(manualInitialOutreachService).run(
                anyValue(baseSnapshot()), eqValue(50L), anyValue(ExecutionMode.MANUAL), anyValue(false)
            )

        val control = BatchSendControlService(
            progressStore, taskExecutionService, manualInitialOutreachService,
            Mockito.mock(BatchSendSettingService::class.java), configRepository,
            mailSenderAccountService, Mockito.mock(MailComposeTemplateService::class.java),
            objectMapper, manualOutreachExecutor
        )
        return DailyCapContext(taskExecutionService, manualInitialOutreachService, configRepository, control)
    }

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun controlContext(): ControlContext {
        val progressStore = Mockito.mock(TaskProgressStore::class.java)
        val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
        val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
        val mailComposeTemplateService = Mockito.mock(MailComposeTemplateService::class.java)
        val manualOutreachExecutor = Mockito.mock(Executor::class.java)
        val capturedRequests = mutableListOf<Any>()
        val capturedBatchConfigIds = mutableListOf<Long?>()
        val capturedTriggers = mutableListOf<String>()
        val capturedSnapshots = mutableListOf<BatchExecutionSnapshot>()

        Mockito.doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))
        Mockito.doAnswer { Pair(true, -99L) }
            .`when`(progressStore).tryStartWithToken(
                eqValue(BatchSendControlService.TASK_TYPE),
                anyValue(TaskProgress(BatchSendControlService.TASK_TYPE, "RUNNING", 0, 0, 0))
            )
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(50)
        Mockito.doAnswer { invocation ->
            capturedTriggers.add(invocation.getArgument(1))
            capturedRequests.add(invocation.getArgument(2))
            capturedBatchConfigIds.add(invocation.getArgument(4))
            @Suppress("UNCHECKED_CAST")
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(101L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> ManualOutreachResult>(5)
            block()
            Pair(
                TaskExecution(
                    id = 101L, taskType = BatchSendControlService.TASK_TYPE,
                    triggerType = invocation.getArgument(1), status = "SUCCESS",
                    requestPayload = null, resultSummary = null, startedAt = LocalDateTime.now(),
                    batchConfigId = invocation.getArgument(4)
                ),
                ManualOutreachResult(1, 1, 0, 0, false)
            )
        }.`when`(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            anyValue(""),
            anyValue(Any()),
            anyValue { _: Long -> },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
        Mockito.doReturn(ManualOutreachResult(1, 1, 0, 0, false))
            .`when`(manualInitialOutreachService).run(
                anyValue(baseSnapshot()), eqValue(101L), anyValue(ExecutionMode.AUTO), anyValue(false)
            )

        val control = BatchSendControlService(
            progressStore, taskExecutionService, manualInitialOutreachService,
            Mockito.mock(BatchSendSettingService::class.java), configRepository,
            mailSenderAccountService, mailComposeTemplateService, objectMapper, manualOutreachExecutor
        )
        return ControlContext(
            progressStore, taskExecutionService, manualInitialOutreachService, configRepository,
            mailComposeTemplateService, manualOutreachExecutor, control, objectMapper,
            capturedRequests, capturedBatchConfigIds, capturedTriggers, capturedSnapshots
        )
    }

    private fun dailyCapContext(): DailyCapContext {
        val progressStore = Mockito.mock(TaskProgressStore::class.java)
        val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
        val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
        val configRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
        val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
        val manualOutreachExecutor = Mockito.mock(Executor::class.java)

        Mockito.doAnswer { invocation ->
            invocation.getArgument<Runnable>(0).run()
            null
        }.`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))
        Mockito.doAnswer { Pair(true, -1L) }
            .`when`(progressStore).tryStartWithToken(
                eqValue(BatchSendControlService.TASK_TYPE),
                anyValue(TaskProgress(BatchSendControlService.TASK_TYPE, "RUNNING", 0, 0, 0))
            )
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(100)
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(50L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> ManualOutreachResult>(5)
            block()
            Pair(
                TaskExecution(
                    id = 50L, taskType = BatchSendControlService.TASK_TYPE, triggerType = "MANUAL",
                    status = "SUCCESS", requestPayload = null, resultSummary = null, startedAt = LocalDateTime.now()
                ),
                ManualOutreachResult(1, 1, 0, 0, false)
            )
        }.`when`(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            anyValue(""),
            anyValue(Any()),
            anyValue { _: Long -> },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
        Mockito.doReturn(ManualOutreachResult(1, 1, 0, 0, false))
            .`when`(manualInitialOutreachService).run(
                anyValue(baseSnapshot()), eqValue(50L), anyValue(ExecutionMode.MANUAL), anyValue(false)
            )

        val control = BatchSendControlService(
            progressStore, taskExecutionService, manualInitialOutreachService,
            Mockito.mock(BatchSendSettingService::class.java), configRepository,
            mailSenderAccountService, Mockito.mock(MailComposeTemplateService::class.java),
            objectMapper, manualOutreachExecutor
        )
        return DailyCapContext(taskExecutionService, manualInitialOutreachService, configRepository, control)
    }

    private fun enabledConfig(
        id: Long = 1L,
        cron: String = "0 0 0 * * ?",
        dailyCap: Int = 100,
        templateId: Long? = null,
        funnelLevel: String? = null,
        tagsJson: String = "[]",
        emailDomain: String? = null,
        discipline: String? = null
    ) = BatchSendTaskConfig(
        id = id, configName = "cfg-$id", mailType = "INTRODUCTION", autoEnabled = true, cron = cron,
        dailyCap = dailyCap, roundSize = 10, perMailIntervalMs = 0, perRoundIntervalMs = 0,
        selfCheckTtlMinutes = 30, funnelLevel = funnelLevel, tagsJson = tagsJson,
        emailDomain = emailDomain, discipline = discipline, templateId = templateId,
        updatedAt = LocalDateTime.of(2026, 7, 14, 10, 0)
    )

    private fun baseSnapshot(
        dailyCap: Int = 100,
        funnelLevel: String? = null,
        tags: List<String> = emptyList(),
        emailDomain: String? = null,
        discipline: String? = null,
        templateId: Long? = null
    ) = BatchExecutionSnapshot(
        mailType = "INTRODUCTION", dailyCap = dailyCap, roundSize = 10,
        perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
        funnelLevel = funnelLevel, tags = tags, emailDomain = emailDomain,
        discipline = discipline, templateId = templateId
    )

    private fun buildManualOutreachService(): ManualOutreachHarness {
        val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
        val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
        val mailRecordRepository = Mockito.mock(MailRecordRepository::class.java)
        val service = ManualInitialOutreachService(
            expertSearchService, Mockito.mock(com.weibo.talentintroduction.mail.service.SenderAccountAssignmentService::class.java),
            Mockito.mock(com.weibo.talentintroduction.mail.service.IntroductionMailComposer::class.java),
            Mockito.mock(com.weibo.talentintroduction.mail.service.MailDeliveryService::class.java),
            expertContactRepository, Mockito.mock(CampaignRepository::class.java), mailRecordRepository,
            Mockito.mock(MailSenderAccountRepository::class.java), Mockito.mock(MailSendAttemptRepository::class.java),
            Mockito.mock(TaskProgressStore::class.java), ManualOutreachProperties(0),
            Mockito.mock(ManualOutreachTxHelper::class.java), Mockito.mock(BatchSendSettingService::class.java),
            Mockito.mock(MailSenderAccountService::class.java), Mockito.mock(SenderAccountSelfCheckService::class.java),
            Mockito.mock(ExpertIndexWriterService::class.java), AccountRateLimiter(),
            Mockito.mock(EmailSuppressionService::class.java), ProviderResolver(),
            SenderWarmupService(WarmupProperties(false, listOf(WarmupStep(1, 20))), objectMapper),
            Mockito.mock(AutoReplySettingService::class.java),
            Mockito.mock(com.weibo.talentintroduction.mail.service.ManualExpertMailService::class.java),
            Mockito.mock(TaskExecutionService::class.java),
            Mockito.mock(SenderAccountBindingService::class.java)
        )
        return ManualOutreachHarness(service, expertSearchService, expertContactRepository, mailRecordRepository)
    }

    private fun stubRetryContacts(
        outreach: ManualOutreachHarness,
        campaignId: Long,
        contacts: List<ExpertContact>,
        profilesByLevel: Map<String, List<ExpertProfile>>
    ) {
        Mockito.`when`(outreach.expertContactRepository.findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaignId, "NEW"))
            .thenReturn(contacts)
        contacts.forEach { c ->
            Mockito.`when`(outreach.mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(c.id!!)).thenReturn(emptyList())
        }
        profilesByLevel.forEach { (level, profiles) ->
            val orcidIds = contacts.map { it.orcidId }
            Mockito.`when`(outreach.expertSearchService.searchByOrcidIds(orcidIds, ExpertIndexLevel.valueOf(level)))
                .thenReturn(profiles)
        }
    }

    private fun invokeBuildRetryableTargets(
        service: ManualInitialOutreachService,
        campaignId: Long,
        scope: RecipientScope
    ): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
        val method = ManualInitialOutreachService::class.java.getDeclaredMethod(
            "buildRetryableTargets", Long::class.javaPrimitiveType, RecipientScope::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(service, campaignId, scope) as Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>>
    }

    private fun invokeCountEsTargets(service: ManualInitialOutreachService, scope: RecipientScope): Int {
        val method = ManualInitialOutreachService::class.java.getDeclaredMethod("countEsTargets", RecipientScope::class.java)
        method.isAccessible = true
        return method.invoke(service, scope) as Int
    }

    private fun expert(orcidId: String, email: String, tags: List<String> = emptyList(), discipline: String? = null) =
        ExpertProfile(
            orcidId = orcidId, email = email, givenNames = null, familyNames = null,
            country = null, keyword = null, employment = null, disciplineCategory = discipline, tags = tags
        )

    private fun contact(id: Long, orcidId: String, campaignId: Long) =
        ExpertContact(
            id = id, campaignId = campaignId, orcidId = orcidId,
            expertEmail = "$orcidId@test.com", expertName = null, currentStatus = "NEW"
        )

    private fun execution(id: Long, batchConfigId: Long?, triggerType: String, status: String = "SUCCESS") =
        TaskExecution(
            id = id, taskType = BatchSendControlService.TASK_TYPE, triggerType = triggerType,
            status = status, requestPayload = "{}", resultSummary = null,
            startedAt = LocalDateTime.now(), batchConfigId = batchConfigId
        )
}
