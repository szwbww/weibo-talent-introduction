package com.weibo.talentintroduction.campaign.service

import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
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
 * - runManualOnce: PAUSED → one round → PAUSED (manual button, I-9/L3-2)
 * - getStatus: merges persisted runtime state with latest TaskProgress (I-5 banner, I-8 stats)
 *
 * Mutual exclusion (I-1) is enforced by [TaskProgressStore.tryStartWithToken] plus the
 * single-thread [manualOutreachExecutor] (core=max=1, queue=0).
 */
@Service
class BatchSendControlService(
    private val progressStore: TaskProgressStore,
    private val taskExecutionService: TaskExecutionService,
    private val manualInitialOutreachService: ManualInitialOutreachService,
    private val batchSendSettingService: BatchSendSettingService,
    @Qualifier("manualOutreachExecutor") private val manualOutreachExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(BatchSendControlService::class.java)

    /**
     * L3-3: on startup, if the persisted runtime status is RUNNING (left over from a crash
     * mid-run), normalize to PAUSED + INTERRUPTED so the status endpoint never reports a
     * phantom RUNNING state with no active execution.
     */
    @PostConstruct
    fun restartRecovery() {
        val state = batchSendSettingService.getRuntimeStatus()
        if (state.status == "RUNNING") {
            log.warn("Found RUNNING batch send state on startup with no active execution; normalizing to PAUSED+INTERRUPTED")
            batchSendSettingService.setRuntimeStatus("PAUSED", state.mode, "INTERRUPTED")
        }
    }

    /**
     * AUTO run triggered by the scheduler (I-2: triggerType=SCHEDULED). Only allowed when
     * runtime status is IDLE and autoEnabled is true.
     */
    fun startAuto(): ResponseEntity<Map<String, String>> {
        val state = batchSendSettingService.getRuntimeStatus()
        if (state.status != "IDLE") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法开始自动运行（需 IDLE）"))
        }
        val config = batchSendSettingService.getConfig()
        if (!config.autoEnabled) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "自动定时发送未启用"))
        }
        return launchExecution(ExecutionMode.AUTO, "SCHEDULED", oneRoundOnly = false)
    }

    /**
     * MANUAL full run triggered by the operator "开始执行" button (I-2: triggerType=MANUAL).
     * Only allowed when runtime status is IDLE.
     */
    fun startManual(): ResponseEntity<Map<String, String>> {
        val state = batchSendSettingService.getRuntimeStatus()
        if (state.status != "IDLE") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法开始（需 IDLE）"))
        }
        return launchExecution(ExecutionMode.MANUAL, "MANUAL", oneRoundOnly = false)
    }

    /**
     * Operator "暂停" button: RUNNING → PAUSED. Requests cancellation of the active execution
     * (I-1) and persists PAUSED + reason. Also called internally by the orchestrator path for
     * I-5 (no available account) — the orchestrator signals via result.stopReason instead, so
     * this method is only for operator-initiated pauses.
     */
    fun pause(reason: String): ResponseEntity<Map<String, String>> {
        val state = batchSendSettingService.getRuntimeStatus()
        if (state.status != "RUNNING") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，无法暂停（需 RUNNING）"))
        }
        progressStore.requestCancel(TASK_TYPE)
        batchSendSettingService.setRuntimeStatus("PAUSED", state.mode, reason)
        log.info("Batch send paused by operator: reason={}", reason)
        return ResponseEntity.ok(mapOf("message" to "已暂停: $reason"))
    }

    /**
     * Operator "手动" button (I-9): PAUSED → one round → PAUSED. Only allowed when PAUSED.
     * Runs a single round (oneRoundOnly=true) then returns to PAUSED (L3-2).
     */
    fun runManualOnce(): ResponseEntity<Map<String, String>> {
        val state = batchSendSettingService.getRuntimeStatus()
        if (state.status != "PAUSED") {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "流程当前状态为 ${state.status}，手动执行仅在 PAUSED 时可用"))
        }
        return launchExecution(ExecutionMode.MANUAL, "MANUAL", oneRoundOnly = true)
    }

    /**
     * Status query (I-5): returns the persisted runtime state (survives refresh) merged with
     * the latest TaskProgress details (I-8 per-account stats) if an execution is active or recent.
     */
    fun getStatus(): BatchSendStatusView {
        val state = batchSendSettingService.getRuntimeStatus()
        val progress = progressStore.get(TASK_TYPE)
        val details = progress?.details
        return BatchSendStatusView(
            status = state.status,
            mode = state.mode,
            pauseReason = state.pauseReason,
            roundNumber = details?.asInt("roundNumber") ?: 0,
            dailyCap = details?.asInt("dailyCap") ?: 0,
            dailySentTotal = details?.asInt("dailySentTotal") ?: 0,
            sentTotal = details?.asInt("sentTotal") ?: 0,
            failedTotal = details?.asInt("failedTotal") ?: 0,
            accounts = extractAccountStats(details),
            executionId = progress?.executionId,
            message = progress?.message
        )
    }

    /**
     * Launches an async execution on the single-thread executor (I-1 mutual exclusion).
     * Persists RUNNING + mode, then post-processes the result to transition runtime status.
     */
    private fun launchExecution(
        mode: ExecutionMode,
        triggerType: String,
        oneRoundOnly: Boolean
    ): ResponseEntity<Map<String, String>> {
        val initialProgress = TaskProgress(
            taskType = TASK_TYPE,
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "正在初始化发送队列...",
            details = mapOf(
                "executionMode" to mode.name,
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

        batchSendSettingService.setRuntimeStatus("RUNNING", mode.name, "")

        try {
            manualOutreachExecutor.execute {
                var executionId: Long? = null
                try {
                    val (_, result) = taskExecutionService.runAndRecordWithResult<ManualOutreachResult>(
                        TASK_TYPE, triggerType, "batch-send-${mode.name}${if (oneRoundOnly) "-one-round" else ""}",
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
                        }
                    ) {
                        manualInitialOutreachService.runScheduledBatch(executionId!!, mode, oneRoundOnly)
                    }
                    applyResultToRuntimeStatus(mode, result)
                } catch (ex: Exception) {
                    log.error("Batch send execution failed", ex)
                    batchSendSettingService.setRuntimeStatus("PAUSED", mode.name, "EXECUTION_ERROR:${ex.message?.take(200)}")
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
            log.warn("Batch send launch rejected: {}", reEx.message)
            batchSendSettingService.setRuntimeStatus("PAUSED", mode.name, "EXECUTOR_REJECTED")
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
     * if the current status is still RUNNING — does not overwrite an operator-initiated PAUSED
     * (which may have been set mid-run via [pause]).
     *
     * - PAUSED (NO_AVAILABLE_ACCOUNT / ONE_ROUND_DONE / DAILY_CAP_REACHED for oneRoundOnly) → PAUSED
     * - CANCELLED (operator pause) → PAUSED (if not already)
     * - FAILED → PAUSED + reason
     * - COMPLETED (snapshot exhausted or dailyCap hit on full run, L3-2) → IDLE
     */
    private fun applyResultToRuntimeStatus(mode: ExecutionMode, result: ManualOutreachResult) {
        val finalStatus = result.finalStatus ?: if (result.wasCancelled) "CANCELLED" else "COMPLETED"
        val current = batchSendSettingService.getRuntimeStatus()
        if (current.status != "RUNNING") {
            log.info("Runtime status is {} (not RUNNING) after execution; skipping transition (finalStatus={})", current.status, finalStatus)
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
                batchSendSettingService.setRuntimeStatus("PAUSED", mode.name, reason)
                log.info("Batch send transitioned to PAUSED after execution: reason={}", reason)
            }
            else -> {
                // COMPLETED → IDLE (L3-2: auto/manual full run done or dailyCap hit → IDLE, wait next day)
                batchSendSettingService.setRuntimeStatus("IDLE", mode.name, "")
                log.info("Batch send transitioned to IDLE after execution (finalStatus={})", finalStatus)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractAccountStats(details: Map<String, Any>?): List<AccountStatRow> {
        if (details == null) return emptyList()
        val raw = details["accounts"] ?: return emptyList()
        // In-memory: List<AccountStatRow>; after log restore: List<Map<String, Any>>
        return when (raw) {
            is List<*> -> raw.mapNotNull { item ->
                when (item) {
                    is AccountStatRow -> item
                    is Map<*, *> -> try {
                        AccountStatRow(
                            accountCode = item["accountCode"] as? String ?: "",
                            todaySent = (item["todaySent"] as? Number)?.toInt() ?: 0,
                            dailyLimit = (item["dailyLimit"] as? Number)?.toInt() ?: 0,
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
    }
}

/** Status view returned by GET /batch-send/status (I-5 banner + I-8 per-account stats). */
data class BatchSendStatusView(
    val status: String,
    val mode: String,
    val pauseReason: String,
    val roundNumber: Int,
    val dailyCap: Int,
    val dailySentTotal: Int,
    val sentTotal: Int,
    val failedTotal: Int,
    val accounts: List<AccountStatRow>,
    val executionId: Long?,
    val message: String?
)
