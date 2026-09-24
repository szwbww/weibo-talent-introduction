package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.util.concurrent.Executor
import java.time.LocalDateTime
import java.util.concurrent.RejectedExecutionException

class BatchSendControlServiceTest {
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
    private val batchSendSettingService = Mockito.mock(BatchSendSettingService::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailComposeTemplateService = Mockito.mock(com.weibo.talentintroduction.template.service.MailComposeTemplateService::class.java)
    private val batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val manualOutreachExecutor = Mockito.mock(Executor::class.java)

    private val control = BatchSendControlService(
        progressStore = progressStore,
        taskExecutionService = taskExecutionService,
        manualInitialOutreachService = manualInitialOutreachService,
        batchSendSettingService = batchSendSettingService,
        batchSendTaskConfigRepository = batchSendTaskConfigRepository,
        mailSenderAccountService = mailSenderAccountService,
        mailComposeTemplateService = mailComposeTemplateService,
        objectMapper = objectMapper,
        manualOutreachExecutor = manualOutreachExecutor
    )

    private fun anySnapshot(): com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot =
        anyValue(com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
            mailType = "INTRODUCTION", roundSize = 10,
            perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
        ))

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value
    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T = captor.capture() ?: defaultValue

    /** I-1: 存量介绍邮件任务的配置实体形态（研发类型非空 → 过快照校验）。 */
    private fun verificationConfig(id: Long, enabled: Boolean) =
        com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig(
            id = id,
            configName = "INTRODUCTION",
            mailType = "INTRODUCTION",
            autoEnabled = true,
            cron = "0 0 0 * * ?",
            roundSize = 10,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            expertTypesJson = """["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]""",
            emailVerificationEnabled = enabled,
            legacyCode = null
        )

    /** I-1/I-4: 读出本次启动固定进 `task_execution.request_payload` 的那份请求载荷。 */
    private fun capturedLaunchRequest(): com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest {
        val payloadCaptor = ArgumentCaptor.forClass(Any::class.java)
        Mockito.verify(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            anyValue(""),
            captureValue(payloadCaptor, Any()),
            anyValue { },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
        return payloadCaptor.value as com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest
    }

    @BeforeEach
    fun setUp() {
        // Synchronous executor: runs the Runnable inline so tests are deterministic
        Mockito.doAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }.`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))

        // Default: tryStartWithToken succeeds
        Mockito.doAnswer { Pair(true, -12345L) }
            .`when`(progressStore).tryStartWithToken(
                Mockito.anyString(),
                Mockito.any(TaskProgress::class.java) ?: TaskProgress(BatchSendControlService.TASK_TYPE, "RUNNING", 0, 0, 0)
            )

        // Default config: autoEnabled=true so startAuto passes the gate
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(
            BatchSendConfig(autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 100, roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30)
        )
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(Mockito.anyBoolean())).thenReturn(10)
        Mockito.`when`(mailSenderAccountService.warmupActiveCount()).thenReturn(0)
        Mockito.`when`(mailSenderAccountService.todayTotalCapacity()).thenReturn(100)

        val legacyIntroConfig = com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig(
            id = 1L,
            configName = "INTRODUCTION",
            mailType = "INTRODUCTION",
            autoEnabled = true,
            cron = "0 0 0 * * ?",
            roundSize = 10,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            // I3-2: V109 之后存量配置全部非空 —— 快照校验要求 INTRODUCTION 研发类型必填。
            expertTypesJson = """["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]""",
            legacyCode = "INTRODUCTION"
        )
        Mockito.`when`(batchSendTaskConfigRepository.findByLegacyCode("INTRODUCTION")).thenReturn(legacyIntroConfig)
        Mockito.`when`(batchSendTaskConfigRepository.findByLegacyCode("MATERIAL_REMINDER")).thenReturn(null)
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(legacyIntroConfig)

        // Default runAndRecordWithResult: invoke onStarted(99L) and run the block
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(99L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> ManualOutreachResult>(5)
            val result = block()
            val exec = com.weibo.talentintroduction.task.domain.TaskExecution(
                id = 99L, taskType = invocation.getArgument(0),
                triggerType = invocation.getArgument(1), status = "SUCCESS",
                requestPayload = "", resultSummary = null,
                startedAt = java.time.LocalDateTime.now(), finishedAt = java.time.LocalDateTime.now()
            )
            Pair(exec, result)
        }.`when`(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            anyValue(""),
            anyValue(""),
            anyValue(Any()),
            anyValue { _: Long -> },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )

        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
                anyValue(com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )),
                anyValue(0L),
                anyValue(ExecutionMode.MANUAL),
                anyValue(false)
            )
    }

    // ──── I-9: state machine transitions ────

    @Test
    fun `startManual from IDLE returns 202 and sets RUNNING then IDLE after COMPLETED result`() {
        // First getRuntimeStatus: IDLE (precondition); second: RUNNING (applyResult check)
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))

        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            anySnapshot(),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(false)
        )

        val response = control.startManual()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        Mockito.verify(mailSenderAccountService).remainingDailyCapacity(eqValue(true))
        // I-9: RUNNING set on start
        Mockito.verify(batchSendSettingService).setRuntimeStatus("RUNNING", "MANUAL", "")
        // L3-2: COMPLETED → IDLE
        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "MANUAL", "")
    }

    @Test
    fun `startManual from RUNNING returns 409 and does not launch`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))

        val response = control.startManual()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("IDLE"))
        // I-1: no execution launched
        Mockito.verifyNoInteractions(manualInitialOutreachService)
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `startAuto from IDLE with autoEnabled=true returns 202 and sets RUNNING then IDLE`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(
            BatchSendConfig(autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 100, roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30)
        )
        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            anySnapshot(),
            eqValue(99L),
            eqValue(ExecutionMode.AUTO),
            eqValue(false)
        )

        val response = control.startAuto()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        Mockito.verify(batchSendSettingService).setRuntimeStatus("RUNNING", "AUTO", "")
        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "AUTO", "")
    }

    @Test
    fun `startAuto with autoEnabled=false returns 409`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        val disabledLegacy = com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig(
            id = 1L,
            configName = "INTRODUCTION",
            mailType = "INTRODUCTION",
            autoEnabled = false,
            cron = "0 0 0 * * ?",
            roundSize = 10,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            legacyCode = "INTRODUCTION"
        )
        Mockito.`when`(batchSendTaskConfigRepository.findByLegacyCode("INTRODUCTION"))
            .thenReturn(disabledLegacy)
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(disabledLegacy)

        val response = control.startAuto()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("未启用"))
        Mockito.verifyNoInteractions(manualInitialOutreachService)
    }

    @Test
    fun `pause from RUNNING returns 200, requests cancel and sets PAUSED with reason`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))
        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(true)

        val response = control.pause("OPERATOR")

        assertEquals(HttpStatus.OK, response.statusCode)
        // I-9: RUNNING → PAUSED with reason
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "AUTO", "OPERATOR")
        Mockito.verify(progressStore).requestCancel(BatchSendControlService.TASK_TYPE)
    }

    @Test
    fun `pause from IDLE returns 409`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))

        val response = control.pause("OPERATOR")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        Mockito.verify(progressStore, Mockito.never()).requestCancel(Mockito.anyString())
    }

    @Test
    fun `resumeSchedule from PAUSED clears pause and does not launch execution`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("PAUSED", "AUTO", "OPERATOR"))

        val response = control.resumeSchedule()

        assertEquals(HttpStatus.OK, response.statusCode)
        Mockito.verify(batchSendSettingService).setAutoEnabled(true)
        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "AUTO", "")
        Mockito.verifyNoInteractions(manualInitialOutreachService)
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `resumeSchedule from RUNNING returns 409 and keeps execution untouched`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))

        val response = control.resumeSchedule()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        Mockito.verify(batchSendSettingService, Mockito.never()).setRuntimeStatus(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
        Mockito.verifyNoInteractions(manualInitialOutreachService)
    }

    @Test
    fun `pauseSchedule from IDLE disables timer and marks PAUSED without launching execution`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "AUTO", ""))

        val response = control.pauseSchedule()

        assertEquals(HttpStatus.OK, response.statusCode)
        Mockito.verify(batchSendSettingService).setAutoEnabled(false)
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "AUTO", "OPERATOR")
        Mockito.verifyNoInteractions(manualInitialOutreachService)
    }

    @Test
    fun `getStatus includes autoEnabled for active timer UI`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(
            BatchSendConfig(autoEnabled = true, cron = "0 0 9 * * ?", dailyCap = 100, roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30)
        )

        val status = control.getStatus()

        assertEquals(true, status.autoEnabled)
        assertEquals("AUTO", status.mode)
    }

    @Test
    fun `runManualOnce from PAUSED returns 202 and sets PAUSED after one round`() {
        // First getRuntimeStatus: PAUSED (precondition); second: RUNNING (applyResult check)
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT"))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))

        Mockito.doReturn(ManualOutreachResult(total = 5, sent = 2, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "PAUSED", stopReason = "ONE_ROUND_DONE"))
            .`when`(manualInitialOutreachService).run(
            anySnapshot(),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(true)
        )

        val response = control.runManualOnce()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        // I-9: RUNNING set on start
        Mockito.verify(batchSendSettingService).setRuntimeStatus("RUNNING", "MANUAL", "")
        // L3-2: oneRoundOnly → back to PAUSED
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "MANUAL", "ONE_ROUND_DONE")
    }

    @Test
    fun `runManualOnce from IDLE returns 202 and restores IDLE after one round`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "AUTO", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))

        Mockito.doReturn(ManualOutreachResult(total = 5, sent = 2, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "PAUSED", stopReason = "ONE_ROUND_DONE"))
            .`when`(manualInitialOutreachService).run(
            anySnapshot(),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(true)
        )

        val response = control.runManualOnce()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        Mockito.verify(batchSendSettingService).setRuntimeStatus("RUNNING", "MANUAL", "")
        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "MANUAL", "")
    }

    @Test
    fun `runManualOnce from RUNNING returns 409`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))

        val response = control.runManualOnce()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("PAUSED"))
        Mockito.verifyNoInteractions(manualInitialOutreachService)
    }

    @Test
    fun `runManualOnce from IDLE keeps PAUSED when no available account`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "AUTO", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))

        Mockito.doReturn(ManualOutreachResult(total = 5, sent = 0, failed = 0, skippedNoAccount = 5, wasCancelled = false, finalStatus = "PAUSED", stopReason = "NO_AVAILABLE_ACCOUNT"))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.MANUAL), eqValue(true))

        val response = control.runManualOnce()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "MANUAL", "NO_AVAILABLE_ACCOUNT")
    }

    // ──── I-5: no available account → PAUSED ────

    @Test
    fun `execution with NO_AVAILABLE_ACCOUNT result transitions to PAUSED with reason`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))

        Mockito.doReturn(ManualOutreachResult(total = 5, sent = 0, failed = 0, skippedNoAccount = 5, wasCancelled = false, finalStatus = "PAUSED", stopReason = "NO_AVAILABLE_ACCOUNT"))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.AUTO), eqValue(false))

        control.startAuto()

        // I-5: PAUSED + NO_AVAILABLE_ACCOUNT
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT")
    }

    // ──── I-1: mutual exclusion via tryStartWithToken ────

    @Test
    fun `launch returns 409 when tryStartWithToken fails`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        Mockito.doReturn(Pair(false, 0L))
            .`when`(progressStore).tryStartWithToken(
                eqValue(BatchSendControlService.TASK_TYPE),
                anyValue(TaskProgress(BatchSendControlService.TASK_TYPE, "RUNNING", 0, 0, 0))
            )

        val response = control.startManual()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("执行中"))
        // No execution launched, no state change
        Mockito.verify(batchSendSettingService, Mockito.never()).setRuntimeStatus(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `executor rejection returns 500 and sets PAUSED`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        Mockito.doThrow(RejectedExecutionException("Queue full"))
            .`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))

        val response = control.startManual()

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("启动失败"))
        // Robustness: PAUSED on executor rejection
        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "MANUAL", "EXECUTOR_REJECTED")
    }

    // ──── L3-3: restart recovery ────

    @Test
    fun `restartRecovery normalizes RUNNING state to PAUSED with INTERRUPTED reason`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))

        control.restartRecovery()

        Mockito.verify(batchSendSettingService).setRuntimeStatus("PAUSED", "AUTO", "INTERRUPTED")
    }

    @Test
    fun `restartRecovery does not modify IDLE state`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))

        control.restartRecovery()

        Mockito.verify(batchSendSettingService, Mockito.never()).setRuntimeStatus(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `restartRecovery does not modify PAUSED state`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT"))

        control.restartRecovery()

        Mockito.verify(batchSendSettingService, Mockito.never()).setRuntimeStatus(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())
    }

    // ──── I-5/I-8: getStatus ────

    @Test
    fun `getStatus returns persisted runtime state merged with progress details`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT"))
        val progress = TaskProgress(
            taskType = BatchSendControlService.TASK_TYPE,
            status = "PAUSED",
            batchNumber = 5,
            processedCount = 5,
            totalCount = 10,
            message = "流程已暂停: NO_AVAILABLE_ACCOUNT",
            details = mapOf(
                "executionMode" to "AUTO",
                "roundNumber" to 3,
                "dailySentTotal" to 42,
                "sentTotal" to 42,
                "failedTotal" to 2,
                "accounts" to listOf(
                    AccountStatRow(
                        accountCode = "chen",
                        todaySent = 42,
                        dailyLimit = 100,
                        success = 40,
                        failed = 2,
                        paused = false,
                        pauseReason = null
                    )
                )
            ),
            executionId = 99L
        )
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(progress)

        val status = control.getStatus()

        // I-5: persisted state survives refresh
        assertEquals("PAUSED", status.status)
        assertEquals("AUTO", status.mode)
        assertEquals("NO_AVAILABLE_ACCOUNT", status.pauseReason)
        // I-8: per-account stats from progress
        assertEquals(3, status.roundNumber)
        assertEquals(42, status.dailySentTotal)
        assertEquals(1, status.accounts.size)
        assertEquals("chen", status.accounts[0].accountCode)
        assertEquals(40, status.accounts[0].success)
        assertEquals(99L, status.executionId)
    }

    @Test
    fun `getStatus returns IDLE with empty accounts when no progress exists`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(null)

        val status = control.getStatus()

        assertEquals("IDLE", status.status)
        assertEquals("AUTO", status.mode)
        assertEquals(true, status.autoEnabled)
        assertTrue(status.accounts.isEmpty())
        assertEquals(0, status.roundNumber)
    }

    // ──── I-2: triggerType distinguishes AUTO vs MANUAL ────

    @Test
    fun `startAuto records SCHEDULED triggerType`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(
            BatchSendConfig(autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 100, roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30)
        )
        Mockito.doReturn(ManualOutreachResult(0, 0, 0, 0, false, "COMPLETED"))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.AUTO), eqValue(false))

        control.startAuto()

        // I-2: AUTO uses SCHEDULED triggerType
        Mockito.verify(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            eqValue("SCHEDULED"),
            anyValue(Any()),
            anyValue { },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
    }

    @Test
    fun `startManual records MANUAL triggerType`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))
        Mockito.doReturn(ManualOutreachResult(0, 0, 0, 0, false, "COMPLETED"))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.MANUAL), eqValue(false))

        control.startManual()

        // I-2: MANUAL uses MANUAL triggerType
        Mockito.verify(taskExecutionService).runAndRecordWithResult<ManualOutreachResult>(
            eqValue(BatchSendControlService.TASK_TYPE),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue { },
            anyValue(null as Long?),
            anyValue { ManualOutreachResult(0, 0, 0, 0, false) }
        )
    }

    @Test
    fun `startManual returns 409 when remaining daily capacity is zero`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(0)

        val response = control.startManual()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("额度已用尽"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `runManualOnce returns 409 when remaining daily capacity is zero`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("PAUSED", "AUTO", "NO_AVAILABLE_ACCOUNT"))
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(0)

        val response = control.runManualOnce()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("额度已用尽"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `startScheduled still accepts when today sum far exceeds dailyCap (I-2)`() {
        val config = com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig(
            id = 5L,
            configName = "INTRODUCTION",
            mailType = "INTRODUCTION",
            autoEnabled = true,
            cron = "0 0 0 * * ?",
            roundSize = 10,
            perMailIntervalMs = 0,
            perRoundIntervalMs = 0,
            selfCheckTtlMinutes = 30,
            // I3-2: V109 之后存量配置全部非空 —— startScheduled 走快照校验。
            expertTypesJson = """["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]""",
            legacyCode = null
        )
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(config)
        // dailyCap gate is gone: a today-sum far above dailyCap must not reject the launch
        Mockito.doReturn(999999).`when`(taskExecutionService).sumSuccessCountTodayByBatchConfigId(
            eqValue(5L), anyValue(LocalDateTime.now())
        )

        val response = control.startScheduled(5L)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        // I-2: the 5 launch entry points must never query the today-sum
        Mockito.verify(taskExecutionService, Mockito.never()).sumSuccessCountTodayByBatchConfigId(
            Mockito.anyLong(), anyValue(LocalDateTime.now())
        )
    }

    @Test
    fun `startManual(request) returns 409 with account-capacity message when remaining daily capacity is zero`() {
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "INTRODUCTION", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND")
            )
        )
        Mockito.`when`(mailSenderAccountService.remainingDailyCapacity(eqValue(true))).thenReturn(0)

        val response = control.startManual(request)

        // must-NOT-change: account-capacity precheck still rejects manual launch with the exact message
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("今日发送额度已用尽（含预热限制），暂不可手动发送", response.body?.get("message"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `execution with WARMUP_LIMIT_REACHED result transitions to IDLE`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "AUTO", ""))
        Mockito.doReturn(ManualOutreachResult(
                total = 5, sent = 2, failed = 0, skippedNoAccount = 0, wasCancelled = false,
                finalStatus = "COMPLETED", stopReason = "WARMUP_LIMIT_REACHED"
            ))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.AUTO), eqValue(false))

        control.startAuto()

        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "AUTO", "")
    }

    @Test
    fun `execution with DAILY_LIMIT_REACHED result transitions to IDLE`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))
        Mockito.doReturn(ManualOutreachResult(
                total = 5, sent = 5, failed = 0, skippedNoAccount = 0, wasCancelled = false,
                finalStatus = "COMPLETED", stopReason = "DAILY_LIMIT_REACHED"
            ))
            .`when`(manualInitialOutreachService).run(anySnapshot(), eqValue(99L), eqValue(ExecutionMode.MANUAL), eqValue(false))

        control.startManual()

        Mockito.verify(batchSendSettingService).setRuntimeStatus("IDLE", "MANUAL", "")
    }

    @Test
    fun `startManual rejects snapshot with roundsPerRun below 1 with 422`() {
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "INTRODUCTION", roundSize = 10, roundsPerRun = 0,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
            )
        )

        val response = control.startManual(request)

        // I-5: roundsPerRun = 0 must fail validation before any launch
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertTrue((response.body?.get("message") as String).contains("roundsPerRun must be >= 1"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `legacy KV fallback derives roundsPerRun from dailyCap and roundSize`() {
        Mockito.`when`(batchSendSettingService.getRuntimeStatus())
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("IDLE", "NONE", ""))
            .thenReturn(BatchSendRuntimeState("RUNNING", "MANUAL", ""))
        Mockito.`when`(batchSendTaskConfigRepository.findByLegacyCode("INTRODUCTION")).thenReturn(null)
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(
            BatchSendConfig(autoEnabled = true, cron = "0 0 0 * * ?", dailyCap = 100, roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30)
        )

        val captor = ArgumentCaptor.forClass(com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot::class.java)
        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            captureValue(
                captor,
                com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )
            ),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(false)
        )

        val response = control.startManual()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        // I-6/X-1: legacy KV fallback derives roundsPerRun = ceil(dailyCap / roundSize)
        assertEquals(10, captor.value.roundsPerRun)
        // I-1/I-2: 纯 KV 兼容快照没有该字段 → 一律 false，缺字段绝不等于开启验证。
        assertFalse(captor.value.emailVerificationEnabled)
    }

    @Test
    fun `startManual(request) rejects INTRODUCTION snapshot with empty expertTypes with 422 (I3-2)`() {
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "INTRODUCTION", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
            )
        )

        val response = control.startManual(request)

        // I3-2: 手动路径的快照直接来自请求体，不经配置服务，必须在此独立校验。
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
        assertTrue((response.body?.get("message") as String).contains("研发类型至少选择一个"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `startManual(request) launches MATERIAL_REMINDER snapshot with empty expertTypes (I3-1)`() {
        Mockito.`when`(mailComposeTemplateService.getById(42L)).thenReturn(
            com.weibo.talentintroduction.template.service.MailComposeTemplateDetail(
                id = 42L, templateCode = "T42", templateName = "材料提醒", subject = "材料",
                description = null, mailType = "MATERIAL_REMINDER", subjectVariants = null,
                enabled = true, blocks = emptyList(), createdAt = null, updatedAt = null
            )
        )
        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            anySnapshot(),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(false)
        )

        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "MATERIAL_REMINDER", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                templateId = 42L
            )
        )

        val response = control.startManual(request)

        // I3-1: 必填只对 INTRODUCTION 生效 —— MATERIAL_REMINDER 空 expertTypes 正常启动。
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        Mockito.verify(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))
    }

    // ──── I-1/I-3/I-4: 发送前邮箱验证开关（email_verification_enabled） ────

    @Test
    fun `startScheduled copies the switch into snapshot and execution request payload (I-1 I-4)`() {
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(6L))
            .thenReturn(verificationConfig(6L, enabled = true))

        val response = control.startScheduled(6L)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        val payload = capturedLaunchRequest()
        assertTrue(payload.snapshot.emailVerificationEnabled)
        // I-4: 快照在启动时固定；配置写入面不得被启动流程触碰。
        Mockito.verify(batchSendTaskConfigRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `startManualFromConfig derives the switch from the persisted config entity (I-1)`() {
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(6L))
            .thenReturn(verificationConfig(6L, enabled = true))
        val captor = ArgumentCaptor.forClass(
            com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot::class.java
        )
        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            captureValue(
                captor,
                com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )
            ),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(false)
        )

        val response = control.startManualFromConfig(6L)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(captor.value.emailVerificationEnabled)
    }

    @Test
    fun `startManual(request) temporary override never rewrites the source config (I-4)`() {
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(6L))
            .thenReturn(verificationConfig(6L, enabled = true))
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = 6L,
            sourceUpdatedAt = LocalDateTime.of(2026, 9, 24, 9, 0),
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "INTRODUCTION", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"),
                emailVerificationEnabled = false
            )
        )

        val response = control.startManual(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        // 临时关闭只影响本次：来源配置不被回写。
        Mockito.verify(batchSendTaskConfigRepository, Mockito.never()).save(Mockito.any())
        val payload = capturedLaunchRequest()
        assertFalse(payload.snapshot.emailVerificationEnabled)
        // 审计身份原样保留，便于按执行 ID 回溯来源配置。
        assertEquals(6L, payload.sourceConfigId)
    }

    @Test
    fun `startManual(request) rejects a MATERIAL_REMINDER snapshot with the switch on with 400 (I-3)`() {
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "MATERIAL_REMINDER", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                templateId = 42L, emailVerificationEnabled = true
            )
        )

        val response = control.startManual(request)

        // I-3: 直接快照绕过配置服务，类型守卫必须在此独立成立且为 400（与配置保存同码）。
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue((response.body?.get("message") as String).contains("发送前邮箱验证只支持介绍邮件"))
        Mockito.verify(manualOutreachExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
    }

    @Test
    fun `startManual(request) accepts an INTRODUCTION snapshot with the switch on (I-3)`() {
        val captor = ArgumentCaptor.forClass(
            com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot::class.java
        )
        Mockito.doReturn(ManualOutreachResult(total = 1, sent = 1, failed = 0, skippedNoAccount = 0, wasCancelled = false, finalStatus = "COMPLETED"))
            .`when`(manualInitialOutreachService).run(
            captureValue(
                captor,
                com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                    mailType = "INTRODUCTION", roundSize = 10,
                    perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30
                )
            ),
            eqValue(99L),
            eqValue(ExecutionMode.MANUAL),
            eqValue(false)
        )
        val request = com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest(
            sourceConfigId = null,
            sourceUpdatedAt = null,
            snapshot = com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot(
                mailType = "INTRODUCTION", roundSize = 10,
                perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 30,
                expertTypes = listOf("PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"),
                emailVerificationEnabled = true
            )
        )

        val response = control.startManual(request)

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertTrue(captor.value.emailVerificationEnabled)
    }

    @Test
    fun `legacy request payload without the flag still reads as false (I-1)`() {
        // 存量 task_execution.request_payload（V139 之前写入）没有该字段 —— 反序列化必须仍为 false，
        // 不得因缺字段抛异常或把历史执行读成开启。
        val legacyJson = """
            {"sourceConfigId":6,"snapshot":{"mailType":"INTRODUCTION","roundSize":10,
            "perMailIntervalMs":0,"perRoundIntervalMs":0,"selfCheckTtlMinutes":30,
            "expertTypes":["PRODUCTION_RND"]}}
        """.trimIndent()

        val request = objectMapper.readValue(
            legacyJson,
            com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest::class.java
        )

        assertFalse(request.snapshot.emailVerificationEnabled)
    }
}
