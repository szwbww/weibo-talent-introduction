package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import javax.annotation.PostConstruct

/**
 * Controls the batch send flow state machine (I-9: IDLE/RUNNING/PAUSED) and provides the
 * operator-facing control surface (start/pause/manual/status). All state transitions are
 * persisted via [BatchSendSettingService.setRuntimeStatus] (L3-3) so they survive refresh/restart.
 *
 * - start: IDLE → RUNNING (full run, AUTO or MANUAL mode, I-2)
 * - pause: RUNNING → PAUSED (operator button or I-5 no-account; cancels active execution)
 * - runManualOnce: IDLE/PAUSED → one round → previous resting state (manual button, I-9/L3-2)
 * - getStatus: merges persisted runtime state with latest TaskProgress (I-5 banner, I-8 stats)
 *
 * Mutual exclusion (I-1/I-8) is enforced by [TaskProgressStore.tryStartWithToken] plus the
 * single-thread [manualOutreachExecutor] (core=max=1, queue=0). INTRODUCTION and
 * MATERIAL_REMINDER share the same executor and TASK_TYPE mutex key — any running type
 * blocks the other (I-8 two-timer independent schedule, shared execution lock).
 *
 * Each [BatchSendType] gets its own persisted runtime state key namespace (I-8). The `sendType`
 * parameter defaults to INTRODUCTION on every public method for backward compat with existing
 * callers (controller, scheduler, tests).
 */
@Service
class BatchSendControlService(
    private val progressStore: TaskProgressStore,
    private val taskExecutionService: TaskExecutionService,
    private val manualInitialOutreachService: ManualInitialOutreachService,
    private val batchSendSettingService: BatchSendSettingService,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    @Qualifier("manualOutreachExecutor") private val manualOutreachExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(BatchSendControlService::class.java)

    /**
     * L3-3: on startup, if the persisted runtime status is RUNNING (left over from a crash
     * mid-run), normalize to PAUSED + INTERRUPTED so the status endpoint never reports a
     * phantom RUNNING state with no active execution.
     */
    @PostConstruct
    fun restartRecoveryOnStartup() {
        restartRecovery()
        restartRecovery(BatchSendType.MATERIAL_REMINDER)
    }

    fun restartRecovery(sendType: BatchSendType = BatchSendType.INTRODUCTION) {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status == "RUNNING") {
            log.warn("Found RUNNING batch send state on startup for {}, normalizing to PAUSED+INTERRUPTED", sendType)
            setRuntimeStatusInternal("PAUSED", state.mode, "INTERRUPTED", sendType)
        }
    }

    /**
     * AUTO run triggered by the scheduler (I-2: triggerType=SCHEDULED). Only allowed when
     * runtime status is IDLE and autoEnabled is true.
     */
    fun startAuto(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法开始自动运行（需 IDLE）"))
        }
        val config = getConfigInternal(sendType)
        if (!config.autoEnabled) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "自动定时发送未启用"))
        }
        return launchExecution(ExecutionMode.AUTO, "SCHEDULED", oneRoundOnly = false, sendType = sendType)
    }

    /**
     * MANUAL full run triggered by the operator "开始执行" button (I-2: triggerType=MANUAL).
     * Only allowed when runtime status is IDLE.
     */
    fun startManual(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法开始（需 IDLE）"))
        }
        val capacityError = checkRemainingDailyCapacity()
        if (capacityError != null) {
            return capacityError
        }
        return launchExecution(ExecutionMode.MANUAL, "MANUAL", oneRoundOnly = false, sendType = sendType)
    }

    /**
     * Operator "暂停" button: RUNNING → PAUSED. Requests cancellation of the active execution
     * (I-1) and persists PAUSED + reason.
     */
    fun pause(reason: String, sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "RUNNING") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法暂停（需 RUNNING）"))
        }
        progressStore.requestCancel(TASK_TYPE)
        setRuntimeStatusInternal("PAUSED", state.mode, reason, sendType)
        log.info("Batch send paused by operator: sendType={}, reason={}", sendType, reason)
        return ResponseEntity.ok(mapOf("message" to "已暂停: $reason"))
    }

    /**
     * Operator resume for scheduled sending. Clears the persisted pause state so the
     * dynamic cron scheduler can run the next due AUTO execution; does not launch a send now.
     */
    fun resumeSchedule(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status == "RUNNING") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 RUNNING，无法恢复定时（需非 RUNNING）"))
        }
        setAutoEnabledInternal(true, sendType)
        setRuntimeStatusInternal("IDLE", state.mode, "", sendType)
        log.info("Batch send schedule resumed: sendType={}, from status={}, mode={}", sendType, state.status, state.mode)
        return ResponseEntity.ok(mapOf("message" to "已恢复定时发送"))
    }

    /**
     * Pauses the cron-driven schedule when no execution is currently running.
     */
    fun pauseSchedule(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status == "RUNNING") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 RUNNING，请使用执行暂停"))
        }
        setAutoEnabledInternal(false, sendType)
        setRuntimeStatusInternal("PAUSED", "AUTO", "OPERATOR", sendType)
        log.info("Batch send schedule paused: sendType={}, from status={}, mode={}", sendType, state.status, state.mode)
        return ResponseEntity.ok(mapOf("message" to "已暂停定时发送"))
    }

    /**
     * Operator "手动" button (I-9): IDLE/PAUSED → one round. Normal IDLE starts return to
     * IDLE so scheduled sending remains armed; PAUSED starts return to PAUSED.
     */
    fun runManualOnce(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status !in setOf("IDLE", "PAUSED")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，手动执行仅在 IDLE 或 PAUSED 时可用"))
        }
        val capacityError = checkRemainingDailyCapacity()
        if (capacityError != null) {
            return capacityError
        }
        return launchExecution(
            mode = ExecutionMode.MANUAL,
            triggerType = "MANUAL",
            oneRoundOnly = true,
            returnToPausedAfterOneRound = state.status == "PAUSED",
            sendType = sendType
        )
    }

    /**
     * Status query (I-5): returns the persisted runtime state (survives refresh) merged with
     * the latest TaskProgress details (I-8 per-account stats) if an execution is active or recent.
     * Only merges progress whose sendType matches the requested type (I-8 independent state).
     * Always returns activeSendType so the frontend can lock the type selector when one is running.
     */
    fun getStatus(sendType: BatchSendType = BatchSendType.INTRODUCTION): BatchSendStatusView {
        val state = getRuntimeStatusInternal(sendType)
        val config = getConfigInternal(sendType)
        val progress = progressStore.get(TASK_TYPE)
        val activeSendTypeInProgress = progress?.details?.get("sendType") as? String
        // Only merge progress data if it belongs to this sendType (null → compat, treat as INTRODUCTION)
        val progressForType = if (activeSendTypeInProgress == null || activeSendTypeInProgress == sendType.name) progress else null
        val details = progressForType?.details
        val mode = if (state.status == "IDLE" && config.autoEnabled) "AUTO" else state.mode
        val templateName = config.templateId?.let { templateId ->
            runCatching { mailComposeTemplateService.getById(templateId).templateName }.getOrNull()
        }
        return BatchSendStatusView(
            status = state.status,
            mode = mode,
            autoEnabled = config.autoEnabled,
            pauseReason = state.pauseReason,
            roundNumber = details?.asInt("roundNumber") ?: 0,
            dailyCap = details?.asInt("dailyCap") ?: 0,
            dailySentTotal = details?.asInt("dailySentTotal") ?: 0,
            sentTotal = details?.asInt("sentTotal") ?: 0,
            failedTotal = details?.asInt("failedTotal") ?: 0,
            accounts = extractAccountStats(details),
            executionId = progressForType?.executionId,
            message = progressForType?.message,
            warmupAccountCount = mailSenderAccountService.warmupActiveCount(),
            todayTotalCapacity = mailSenderAccountService.todayTotalCapacity(),
            todayRemainingCapacity = mailSenderAccountService.remainingDailyCapacity(),
            templateName = templateName,
            activeSendType = activeSendTypeInProgress
        )
    }

    private fun checkRemainingDailyCapacity(): ResponseEntity<Map<String, String>>? {
        if (mailSenderAccountService.remainingDailyCapacity(ignoreWarmup = true) <= 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "今日发送额度已用尽（含预热限制），暂不可手动发送"))
        }
        return null
    }

    /**
     * I-7: re-validate template gate before launch to catch template disabled/type-changed after
     * config was saved. INTRODUCTION: null templateId OK. MATERIAL_REMINDER: must have enabled
     * MATERIAL_REMINDER-type template.
     */
    private fun validateTemplateGate(sendType: BatchSendType, config: BatchSendConfig): ResponseEntity<Map<String, String>>? {
        val templateId = config.templateId
        // INTRODUCTION may use the hardcoded default composer when templateId is null (I-7).
        if (templateId == null) {
            return if (sendType == BatchSendType.MATERIAL_REMINDER) {
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("message" to "MATERIAL_REMINDER 必须配置模板才能发送"))
            } else {
                null
            }
        }
        return try {
            val template = mailComposeTemplateService.getById(templateId)
            when {
                !template.enabled -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("message" to "模板 $templateId 已禁用，无法发送"))
                template.mailType != sendType.name -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("message" to "模板 $templateId 类型为 ${template.mailType}，与 $sendType 不匹配"))
                else -> null
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "模板校验失败: ${e.message}"))
        }
    }

    /**
     * Launches an async execution on the single-thread executor (I-1 mutual exclusion).
     * Persists RUNNING + mode, then post-processes the result to transition runtime status.
     * Dispatches INTRODUCTION to [ManualInitialOutreachService.runScheduledBatch] and
     * MATERIAL_REMINDER to [ManualInitialOutreachService.runMaterialReminderBatch] (I-9).
     */
    private fun launchExecution(
        mode: ExecutionMode,
        triggerType: String,
        oneRoundOnly: Boolean,
        returnToPausedAfterOneRound: Boolean = true,
        sendType: BatchSendType = BatchSendType.INTRODUCTION
    ): ResponseEntity<Map<String, String>> {
        val config = getConfigInternal(sendType)
        val templateError = validateTemplateGate(sendType, config)
        if (templateError != null) return templateError

        val initialProgress = TaskProgress(
            taskType = TASK_TYPE,
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "正在初始化发送队列...",
            details = mapOf(
                "executionMode" to mode.name,
                "sendType" to sendType.name,
                "sent" to 0,
                "failed" to 0,
                "accounts" to emptyList<AccountStatRow>()
            )
        )
        val (started, pendingToken) = progressStore.tryStartWithToken(TASK_TYPE, initialProgress)
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中"))
        }

        setRuntimeStatusInternal("RUNNING", mode.name, "", sendType)

        val taskDescription = "batch-send-${sendType.name}-${mode.name}${if (oneRoundOnly) "-one-round" else ""}"

        try {
            manualOutreachExecutor.execute {
                var executionId: Long? = null
                try {
                    val (_, result) = taskExecutionService.runAndRecordWithResult<ManualOutreachResult>(
                        TASK_TYPE, triggerType, taskDescription,
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
                        }
                    ) {
                        when (sendType) {
                            BatchSendType.INTRODUCTION ->
                                manualInitialOutreachService.runScheduledBatch(executionId!!, mode, oneRoundOnly)
                            BatchSendType.MATERIAL_REMINDER ->
                                manualInitialOutreachService.runMaterialReminderBatch(executionId!!, mode, oneRoundOnly)
                        }
                    }
                    applyResultToRuntimeStatus(mode, result, returnToPausedAfterOneRound, sendType)
                } catch (ex: Exception) {
                    log.error("Batch send execution failed for {}", sendType, ex)
                    setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTION_ERROR:${ex.message?.take(200)}", sendType)
                    progressStore.update(TASK_TYPE, TaskProgress(
                        taskType = TASK_TYPE, status = "FAILED",
                        batchNumber = 0, processedCount = 0, totalCount = 0,
                        message = ex.message ?: "初始化失败"
                    ), executionId)
                } finally {
                    val execId = executionId
                    if (execId != null) {
                        progressStore.clearExecutionContext(TASK_TYPE, execId)
                    } else {
                        progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
                    }
                }
            }
        } catch (reEx: RejectedExecutionException) {
            log.warn("Batch send launch rejected for {}: {}", sendType, reEx.message)
            setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTOR_REJECTED", sendType)
            progressStore.update(TASK_TYPE, TaskProgress(
                taskType = TASK_TYPE, status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = "启动失败: ${reEx.message}"
            ), pendingToken)
            progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "启动失败: 线程池满或已关闭"))
        }

        return ResponseEntity.accepted().body(mapOf("message" to "已启动"))
    }

    /**
     * Maps the orchestrator result to a runtime status transition (L3-3). Only transitions
     * if the current status is still RUNNING — does not overwrite an operator-initiated PAUSED.
     */
    private fun applyResultToRuntimeStatus(
        mode: ExecutionMode,
        result: ManualOutreachResult,
        returnToPausedAfterOneRound: Boolean,
        sendType: BatchSendType
    ) {
        val finalStatus = result.finalStatus ?: if (result.wasCancelled) "CANCELLED" else "COMPLETED"
        val current = getRuntimeStatusInternal(sendType)
        if (current.status != "RUNNING") {
            log.info("Runtime status is {} (not RUNNING) after execution for {}; skipping transition (finalStatus={})", current.status, sendType, finalStatus)
            return
        }
        if (!returnToPausedAfterOneRound && finalStatus == "PAUSED" && result.stopReason in idleSafeOneRoundStopReasons) {
            setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
            log.info("Batch send {} transitioned to IDLE after one-round manual execution: reason={}", sendType, result.stopReason)
            return
        }
        when (finalStatus) {
            "PAUSED", "CANCELLED", "FAILED" -> {
                val reason = when (finalStatus) {
                    "PAUSED" -> result.stopReason ?: "PAUSED"
                    "CANCELLED" -> result.stopReason ?: "CANCELLED"
                    "FAILED" -> result.stopReason ?: "FAILED"
                    else -> ""
                }
                setRuntimeStatusInternal("PAUSED", mode.name, reason, sendType)
                log.info("Batch send {} transitioned to PAUSED after execution: reason={}", sendType, reason)
            }
            else -> {
                setRuntimeStatusInternal("IDLE", mode.name, "", sendType)
                log.info("Batch send {} transitioned to IDLE after execution (finalStatus={})", sendType, finalStatus)
            }
        }
    }

    // ── Internal helpers — dispatch to compat (INTRODUCTION) or typed (MATERIAL_REMINDER) ─────

    /**
     * For INTRODUCTION, calls the no-arg compat overload so Mockito stubs in existing tests
     * (which stub `getRuntimeStatus()`) are matched. For other types, calls the typed overload.
     */
    private fun getRuntimeStatusInternal(sendType: BatchSendType): BatchSendRuntimeState =
        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.getRuntimeStatus()
        else batchSendSettingService.getRuntimeStatus(sendType)

    private fun setRuntimeStatusInternal(status: String, mode: String, reason: String, sendType: BatchSendType) {
        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.setRuntimeStatus(status, mode, reason)
        else batchSendSettingService.setRuntimeStatus(status, mode, reason, sendType)
    }

    private fun getConfigInternal(sendType: BatchSendType): BatchSendConfig =
        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.getConfig()
        else batchSendSettingService.getConfig(sendType)

    private fun setAutoEnabledInternal(enabled: Boolean, sendType: BatchSendType) {
        if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.setAutoEnabled(enabled)
        else batchSendSettingService.setAutoEnabled(enabled, sendType)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAccountStats(details: Map<String, Any>?): List<AccountStatRow> {
        if (details == null) return emptyList()
        val raw = details["accounts"] ?: return emptyList()
        return when (raw) {
            is List<*> -> raw.mapNotNull { item ->
                when (item) {
                    is AccountStatRow -> item
                    is Map<*, *> -> try {
                        AccountStatRow(
                            accountCode = item["accountCode"] as? String ?: "",
                            todaySent = (item["todaySent"] as? Number)?.toInt() ?: 0,
                            dailyLimit = (item["dailyLimit"] as? Number)?.toInt() ?: 0,
                            effectiveDailyLimit = (item["effectiveDailyLimit"] as? Number)?.toInt()
                                ?: (item["dailyLimit"] as? Number)?.toInt() ?: 0,
                            warmupActive = item["warmupActive"] as? Boolean ?: false,
                            limitReason = item["limitReason"] as? String,
                            success = (item["success"] as? Number)?.toInt() ?: 0,
                            failed = (item["failed"] as? Number)?.toInt() ?: 0,
                            paused = item["paused"] as? Boolean ?: false,
                            pauseReason = item["pauseReason"] as? String,
                            currentIntervalMs = (item["currentIntervalMs"] as? Number)?.toLong()
                        )
                    } catch (e: Exception) {
                        null
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    private fun Map<String, Any>.asInt(key: String): Int? =
        (this[key] as? Number)?.toInt()

    companion object {
        const val TASK_TYPE = "MANUAL_INITIAL_OUTREACH"
        private val idleSafeOneRoundStopReasons = setOf(
            "ONE_ROUND_DONE",
            "EMPTY_SNAPSHOT",
            "DAILY_CAP_REACHED",
            "DAILY_LIMIT_REACHED",
            "WARMUP_LIMIT_REACHED"
        )
    }
}

/** Status view returned by GET /batch-send/status (I-5 banner + I-8 per-account stats). */
data class BatchSendStatusView(
    val status: String,
    val mode: String,
    val autoEnabled: Boolean,
    val pauseReason: String,
    val roundNumber: Int,
    val dailyCap: Int,
    val dailySentTotal: Int,
    val sentTotal: Int,
    val failedTotal: Int,
    val accounts: List<AccountStatRow>,
    val executionId: Long?,
    val message: String?,
    val warmupAccountCount: Int = 0,
    val todayTotalCapacity: Int = 0,
    val todayRemainingCapacity: Int = 0,
    val templateName: String? = null,
    /** The sendType currently running, null when idle. Used by frontend to lock type selector. */
    val activeSendType: String? = null
)
