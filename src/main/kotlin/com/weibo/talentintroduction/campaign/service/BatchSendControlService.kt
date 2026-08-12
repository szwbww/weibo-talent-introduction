package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest
import com.weibo.talentintroduction.campaign.domain.toExecutionSnapshot
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
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
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct
import kotlin.math.ceil

@Service
class BatchSendControlService(
    private val progressStore: TaskProgressStore,
    private val taskExecutionService: TaskExecutionService,
    private val manualInitialOutreachService: ManualInitialOutreachService,
    private val batchSendSettingService: BatchSendSettingService,
    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
    private val mailSenderAccountService: MailSenderAccountService,
    private val mailComposeTemplateService: MailComposeTemplateService,
    private val objectMapper: ObjectMapper,
    @Qualifier("manualOutreachExecutor") private val manualOutreachExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(BatchSendControlService::class.java)

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

    /** Scheduled auto run for one config entity (I-2). */
    fun startScheduled(configId: Long): ResponseEntity<Map<String, Any>> {
        val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(configId)
            ?: return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("message" to "配置不存在或已删除: $configId"))
        if (!config.autoEnabled) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "自动定时发送未启用"))
        }
        val snapshot = config.toExecutionSnapshot(objectMapper)
        validateSnapshotFields(snapshot)?.let { return it }
        validateTemplateAtLaunch(snapshot.mailType, snapshot.templateId)?.let { return it }
        val request = ManualBatchExecutionRequest(
            sourceConfigId = configId,
            sourceUpdatedAt = config.updatedAt,
            snapshot = snapshot
        )
        return launchFromSnapshot(
            snapshot = snapshot,
            batchConfigId = configId,
            triggerType = "SCHEDULED",
            mode = ExecutionMode.AUTO,
            requestPayload = request
        )
    }

    /** Manual run from a full snapshot request (I-1). */
    fun startManual(request: ManualBatchExecutionRequest): ResponseEntity<Map<String, Any>> {
        validateSnapshotFields(request.snapshot)?.let { return it }
        validateTemplateAtLaunch(request.snapshot.mailType, request.snapshot.templateId)?.let { return it }
        val batchConfigId = request.sourceConfigId
        val capacityError = checkRemainingAccountCapacity()
        if (capacityError != null) return capacityError
        return launchFromSnapshot(
            snapshot = request.snapshot,
            batchConfigId = batchConfigId,
            triggerType = "MANUAL",
            mode = ExecutionMode.MANUAL,
            requestPayload = request,
            oneRoundOnly = request.snapshot.oneRoundOnly
        )
    }

    /** Manual run using the current persisted config row as snapshot (config list "手动"). */
    fun startManualFromConfig(configId: Long): ResponseEntity<Map<String, Any>> {
        val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(configId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Batch send task config not found: $configId")
        val request = ManualBatchExecutionRequest(
            sourceConfigId = configId,
            sourceUpdatedAt = config.updatedAt,
            snapshot = config.toExecutionSnapshot(objectMapper)
        )
        return startManual(request)
    }

    fun startAuto(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") {
            return conflict("流程当前状态为 ${state.status}，无法开始自动运行（需 IDLE）")
        }
        val legacy = batchSendTaskConfigRepository.findByLegacyCode(sendType.name)
        if (legacy != null) {
            val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(legacy.id!!)
                ?: return conflict("配置不存在或已删除")
            if (!config.autoEnabled) {
                return conflict("自动定时发送未启用")
            }
            val snapshot = config.toExecutionSnapshot(objectMapper)
            validateSnapshotFields(snapshot)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            validateTemplateAtLaunch(snapshot.mailType, snapshot.templateId)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            val request = ManualBatchExecutionRequest(legacy.id, config.updatedAt, snapshot)
            val response = launchFromSnapshot(
                snapshot = snapshot,
                batchConfigId = legacy.id,
                triggerType = "SCHEDULED",
                mode = ExecutionMode.AUTO,
                requestPayload = request,
                legacySendType = sendType,
                manageRuntimeStatus = true
            )
            return ResponseEntity.status(response.statusCode)
                .body(response.body?.mapValues { it.value.toString() } ?: emptyMap())
        }
        return legacyKvStartAuto(sendType)
    }

    fun startManual(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") {
            return conflict("流程当前状态为 ${state.status}，无法开始（需 IDLE）")
        }
        val capacityError = checkRemainingDailyCapacity()
        if (capacityError != null) return capacityError
        val legacy = batchSendTaskConfigRepository.findByLegacyCode(sendType.name)
        if (legacy != null) {
            val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(legacy.id!!)
                ?: return conflict("配置不存在或已删除")
            val snapshot = config.toExecutionSnapshot(objectMapper)
            validateSnapshotFields(snapshot)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            validateTemplateAtLaunch(snapshot.mailType, snapshot.templateId)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            val request = ManualBatchExecutionRequest(legacy.id, config.updatedAt, snapshot)
            val response = launchFromSnapshot(
                snapshot = snapshot,
                batchConfigId = legacy.id,
                triggerType = "MANUAL",
                mode = ExecutionMode.MANUAL,
                requestPayload = request,
                legacySendType = sendType,
                manageRuntimeStatus = true
            )
            return ResponseEntity.status(response.statusCode)
                .body(response.body?.mapValues { it.value.toString() } ?: emptyMap())
        }
        return legacyKvStartManual(sendType)
    }

    /**
     * I-1: live block exists iff the in-memory single slot for TASK_TYPE is
     * currently bound to the queried executionId. Only reads the memory slot
     * (never restoreFromLog), so a RUNNING row left behind by a restart does
     * not produce a live block.
     */
    fun getLiveExecutionView(executionId: Long): ExecutionLiveView? {
        if (progressStore.getCurrentExecutionId(TASK_TYPE) != executionId) return null
        val progress = progressStore.get(TASK_TYPE) ?: return null
        val details = progress.details
        return ExecutionLiveView(
            status = progress.status,
            message = progress.message,
            roundNumber = details?.asInt("roundNumber") ?: 0,
            processedCount = progress.processedCount,
            totalCount = progress.totalCount,
            percentage = progress.percentage,
            accounts = extractAccountStats(details),
            cancellable = progress.status == "RUNNING"
        )
    }

    /**
     * I-2: cancellation target is decided by executionId, not by taskType.
     * A mismatch returns 409 without touching requestCancel; requestCancel
     * itself stays single-slot (only accepts RUNNING + bound executionId).
     */
    fun cancelExecution(executionId: Long): ResponseEntity<Map<String, Any>> {
        if (progressStore.getCurrentExecutionId(TASK_TYPE) != executionId) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "该执行已结束或不是当前正在运行的执行"))
        }
        val accepted = progressStore.requestCancel(TASK_TYPE)
        return if (accepted) {
            ResponseEntity.ok(mapOf("message" to "已发送取消请求，将在当前批次结束后停止"))
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "取消请求已在处理中"))
        }
    }

    fun pause(reason: String, sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "RUNNING") {
            return conflict("流程当前状态为 ${state.status}，无法暂停（需 RUNNING）")
        }
        progressStore.requestCancel(TASK_TYPE)
        setRuntimeStatusInternal("PAUSED", state.mode, reason, sendType)
        log.info("Batch send paused by operator: sendType={}, reason={}", sendType, reason)
        return ResponseEntity.ok(mapOf("message" to "已暂停: $reason"))
    }

    fun resumeSchedule(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status == "RUNNING") {
            return conflict("流程当前状态为 RUNNING，无法恢复定时（需非 RUNNING）")
        }
        setAutoEnabledInternal(true, sendType)
        setRuntimeStatusInternal("IDLE", state.mode, "", sendType)
        log.info("Batch send schedule resumed: sendType={}, from status={}, mode={}", sendType, state.status, state.mode)
        return ResponseEntity.ok(mapOf("message" to "已恢复定时发送"))
    }

    fun pauseSchedule(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status == "RUNNING") {
            return conflict("流程当前状态为 RUNNING，请使用执行暂停")
        }
        setAutoEnabledInternal(false, sendType)
        setRuntimeStatusInternal("PAUSED", "AUTO", "OPERATOR", sendType)
        log.info("Batch send schedule paused: sendType={}, from status={}, mode={}", sendType, state.status, state.mode)
        return ResponseEntity.ok(mapOf("message" to "已暂停定时发送"))
    }

    fun runManualOnce(sendType: BatchSendType = BatchSendType.INTRODUCTION): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status !in setOf("IDLE", "PAUSED")) {
            return conflict("流程当前状态为 ${state.status}，手动执行仅在 IDLE 或 PAUSED 时可用")
        }
        val capacityError = checkRemainingDailyCapacity()
        if (capacityError != null) return capacityError
        val legacy = batchSendTaskConfigRepository.findByLegacyCode(sendType.name)
        if (legacy != null) {
            val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(legacy.id!!)
                ?: return conflict("配置不存在或已删除")
            val snapshot = config.toExecutionSnapshot(objectMapper, oneRoundOnly = true)
            validateSnapshotFields(snapshot)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            validateTemplateAtLaunch(snapshot.mailType, snapshot.templateId)?.let { return ResponseEntity.status(it.statusCode).body(it.body?.mapValues { e -> e.value.toString() }) }
            val request = ManualBatchExecutionRequest(legacy.id, config.updatedAt, snapshot)
            val response = launchFromSnapshot(
                snapshot = snapshot,
                batchConfigId = legacy.id,
                triggerType = "MANUAL",
                mode = ExecutionMode.MANUAL,
                requestPayload = request,
                oneRoundOnly = true,
                legacySendType = sendType,
                manageRuntimeStatus = true,
                returnToPausedAfterOneRound = state.status == "PAUSED"
            )
            return ResponseEntity.status(response.statusCode)
                .body(response.body?.mapValues { it.value.toString() } ?: emptyMap())
        }
        return legacyKvRunManualOnce(sendType)
    }

    fun getStatus(sendType: BatchSendType = BatchSendType.INTRODUCTION): BatchSendStatusView {
        val state = getRuntimeStatusInternal(sendType)
        val config = getConfigInternal(sendType)
        val progress = progressStore.get(TASK_TYPE)
        val activeSendTypeInProgress = progress?.details?.get("sendType") as? String
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
            roundsPerRun = details?.asInt("roundsPerRun") ?: 0,
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

    private fun launchFromSnapshot(
        snapshot: BatchExecutionSnapshot,
        batchConfigId: Long?,
        triggerType: String,
        mode: ExecutionMode,
        requestPayload: Any,
        oneRoundOnly: Boolean = snapshot.oneRoundOnly,
        legacySendType: BatchSendType? = null,
        manageRuntimeStatus: Boolean = false,
        returnToPausedAfterOneRound: Boolean = false
    ): ResponseEntity<Map<String, Any>> {
        val initialProgress = TaskProgress(
            taskType = TASK_TYPE,
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "正在初始化发送队列...",
            details = mapOf(
                "executionMode" to mode.name,
                "sendType" to snapshot.mailType,
                "batchConfigId" to (batchConfigId ?: ""),
                "sent" to 0,
                "failed" to 0,
                "accounts" to emptyList<AccountStatRow>()
            )
        )
        val (started, pendingToken) = progressStore.tryStartWithToken(TASK_TYPE, initialProgress)
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "已有批量任务执行中"))
        }

        if (manageRuntimeStatus && legacySendType != null) {
            setRuntimeStatusInternal("RUNNING", mode.name, "", legacySendType)
        }

        val taskDescription = "batch-send-${snapshot.mailType}-${mode.name}${if (oneRoundOnly) "-one-round" else ""}"
        val executionIdFuture = CompletableFuture<Long>()

        try {
            manualOutreachExecutor.execute {
                var executionId: Long? = null
                try {
                    val (_, result) = taskExecutionService.runAndRecordWithResult<ManualOutreachResult>(
                        TASK_TYPE, triggerType, requestPayload,
                        onStarted = { id ->
                            executionId = id
                            executionIdFuture.complete(id)
                            progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
                        },
                        batchConfigId = batchConfigId
                    ) {
                        manualInitialOutreachService.run(
                            snapshot = snapshot,
                            executionId = executionId!!,
                            mode = mode,
                            oneRoundOnly = oneRoundOnly
                        )
                    }
                    if (manageRuntimeStatus && legacySendType != null) {
                        applyResultToRuntimeStatus(mode, result, returnToPausedAfterOneRound, legacySendType)
                    }
                } catch (ex: Exception) {
                    executionIdFuture.completeExceptionally(ex)
                    log.error("Batch send execution failed for mailType={}", snapshot.mailType, ex)
                    if (manageRuntimeStatus && legacySendType != null) {
                        setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTION_ERROR:${ex.message?.take(200)}", legacySendType)
                    }
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
            log.warn("Batch send launch rejected for {}: {}", snapshot.mailType, reEx.message)
            if (manageRuntimeStatus && legacySendType != null) {
                setRuntimeStatusInternal("PAUSED", mode.name, "EXECUTOR_REJECTED", legacySendType)
            }
            progressStore.update(TASK_TYPE, TaskProgress(
                taskType = TASK_TYPE, status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = "启动失败: ${reEx.message}"
            ), pendingToken)
            progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("message" to "启动失败: 线程池满或已关闭"))
        }

        val body = mutableMapOf<String, Any>("message" to "已启动")
        try {
            val executionId = executionIdFuture.get(5, TimeUnit.SECONDS)
            body["executionId"] = executionId
        } catch (e: Exception) {
            log.warn("Timed out waiting for executionId after launch: {}", e.message)
        }
        return ResponseEntity.accepted().body(body)
    }

    private fun validateSnapshotFields(snapshot: BatchExecutionSnapshot): ResponseEntity<Map<String, Any>>? {
        return try {
            require(snapshot.roundSize > 0) { "roundSize must be > 0" }
            require(snapshot.roundsPerRun >= 1) { "roundsPerRun must be >= 1" }
            require(snapshot.perMailIntervalMs >= 0) { "perMailIntervalMs must be >= 0" }
            require(snapshot.perRoundIntervalMs >= 0) { "perRoundIntervalMs must be >= 0" }
            require(snapshot.selfCheckTtlMinutes >= 1) { "selfCheckTtlMinutes must be >= 1" }
            snapshot.funnelLevel?.let { level ->
                require(level in setOf("CANDIDATE", "APPLICATION")) {
                    "funnelLevel must be CANDIDATE, APPLICATION, or empty"
                }
            }
            snapshot.regions.forEach { region ->
                require(region in CountryContinentMapping.allRegions()) { "Invalid region: $region" }
            }
            null
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("message" to (e.message ?: "参数校验失败")))
        }
    }

    private fun validateTemplateAtLaunch(mailType: String, templateId: Long?): ResponseEntity<Map<String, Any>>? {
        if (templateId == null) {
            return if (mailType == BatchSendType.MATERIAL_REMINDER.name) {
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("message" to "MATERIAL_REMINDER 必须配置模板才能发送"))
            } else {
                null
            }
        }
        return try {
            val template = mailComposeTemplateService.getById(templateId)
            when {
                !template.enabled -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("message" to "模板 $templateId 已禁用，无法发送"))
                template.mailType != mailType -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("message" to "模板 $templateId 类型为 ${template.mailType}，与 $mailType 不匹配"))
                else -> null
            }
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(mapOf("message" to "模板校验失败: ${e.message}"))
        }
    }

    private fun checkRemainingAccountCapacity(): ResponseEntity<Map<String, Any>>? {
        if (mailSenderAccountService.remainingDailyCapacity(ignoreWarmup = true) <= 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "今日发送额度已用尽（含预热限制），暂不可手动发送"))
        }
        return null
    }

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

    // ── Legacy KV fallback when legacy_code row missing ─────────────────────────

    private fun legacyKvStartAuto(sendType: BatchSendType): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") return conflict("流程当前状态为 ${state.status}，无法开始自动运行（需 IDLE）")
        val config = getConfigInternal(sendType)
        if (!config.autoEnabled) return conflict("自动定时发送未启用")
        return launchLegacyKv(ExecutionMode.AUTO, "SCHEDULED", oneRoundOnly = false, sendType = sendType)
    }

    private fun legacyKvStartManual(sendType: BatchSendType): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status != "IDLE") return conflict("流程当前状态为 ${state.status}，无法开始（需 IDLE）")
        checkRemainingDailyCapacity()?.let { return it }
        return launchLegacyKv(ExecutionMode.MANUAL, "MANUAL", oneRoundOnly = false, sendType = sendType)
    }

    private fun legacyKvRunManualOnce(sendType: BatchSendType): ResponseEntity<Map<String, String>> {
        val state = getRuntimeStatusInternal(sendType)
        if (state.status !in setOf("IDLE", "PAUSED")) {
            return conflict("流程当前状态为 ${state.status}，手动执行仅在 IDLE 或 PAUSED 时可用")
        }
        checkRemainingDailyCapacity()?.let { return it }
        return launchLegacyKv(
            mode = ExecutionMode.MANUAL,
            triggerType = "MANUAL",
            oneRoundOnly = true,
            returnToPausedAfterOneRound = state.status == "PAUSED",
            sendType = sendType
        )
    }

    private fun launchLegacyKv(
        mode: ExecutionMode,
        triggerType: String,
        oneRoundOnly: Boolean,
        returnToPausedAfterOneRound: Boolean = true,
        sendType: BatchSendType = BatchSendType.INTRODUCTION
    ): ResponseEntity<Map<String, String>> {
        val config = getConfigInternal(sendType)
        val templateError = validateTemplateGate(sendType, config)
        if (templateError != null) return templateError
        val snapshot = config.toLegacySnapshot(oneRoundOnly)
        val response = launchFromSnapshot(
            snapshot = snapshot,
            batchConfigId = null,
            triggerType = triggerType,
            mode = mode,
            requestPayload = mapOf("legacySendType" to sendType.name, "snapshot" to snapshot),
            oneRoundOnly = oneRoundOnly,
            legacySendType = sendType,
            manageRuntimeStatus = true,
            returnToPausedAfterOneRound = returnToPausedAfterOneRound
        )
        return ResponseEntity.status(response.statusCode)
            .body(response.body?.mapValues { it.value.toString() } ?: emptyMap())
    }

    private fun BatchSendConfig.toLegacySnapshot(oneRoundOnly: Boolean): BatchExecutionSnapshot =
        BatchExecutionSnapshot(
            mailType = sendType.name,
            roundSize = roundSize,
            roundsPerRun = maxOf(1, ceil(dailyCap.toDouble() / roundSize).toInt()),
            perMailIntervalMs = perMailIntervalMs,
            perRoundIntervalMs = perRoundIntervalMs,
            selfCheckTtlMinutes = selfCheckTtlMinutes,
            funnelLevel = if (sendType == BatchSendType.INTRODUCTION) "CANDIDATE" else "APPLICATION",
            tags = if (sendType == BatchSendType.MATERIAL_REMINDER) listOf("承诺回复材料") else emptyList(),
            regions = emptyList(),
            emailDomain = emailDomain.ifBlank { null },
            discipline = discipline.ifBlank { null },
            templateId = templateId,
            oneRoundOnly = oneRoundOnly
        )

    private fun validateTemplateGate(sendType: BatchSendType, config: BatchSendConfig): ResponseEntity<Map<String, String>>? {
        val err = validateTemplateAtLaunch(sendType.name, config.templateId) ?: return null
        return ResponseEntity.status(err.statusCode)
            .body(err.body?.mapValues { it.value.toString() } ?: emptyMap())
    }

    private fun checkRemainingDailyCapacity(): ResponseEntity<Map<String, String>>? {
        val err = checkRemainingAccountCapacity() ?: return null
        return ResponseEntity.status(err.statusCode)
            .body(err.body?.mapValues { it.value.toString() } ?: emptyMap())
    }

    private fun conflict(message: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to message))

    private fun mapAnyResponse(
        response: ResponseEntity<Map<String, Any>>,
        manageRuntime: Boolean,
        sendType: BatchSendType,
        mode: ExecutionMode,
        returnToPausedAfterOneRound: Boolean = false
    ): ResponseEntity<Map<String, String>> {
        if (manageRuntime && response.statusCode == HttpStatus.ACCEPTED) {
            // Runtime transitions happen in async block for legacy paths
        }
        return ResponseEntity.status(response.statusCode)
            .body(response.body?.mapValues { it.value.toString() } ?: emptyMap())
    }

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
            "DAILY_LIMIT_REACHED",
            "WARMUP_LIMIT_REACHED"
        )
    }
}

data class BatchSendStatusView(
    val status: String,
    val mode: String,
    val autoEnabled: Boolean,
    val pauseReason: String,
    val roundNumber: Int,
    val roundsPerRun: Int = 0,
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
    val activeSendType: String? = null
)

data class ExecutionLiveView(
    val status: String,          // RUNNING | CANCELLING
    val message: String?,
    val roundNumber: Int,
    val processedCount: Long,
    val totalCount: Long,        // ES 估算值，前端须标注"约"
    val percentage: Int,
    val accounts: List<AccountStatRow>,
    val cancellable: Boolean     // status == "RUNNING"
)
