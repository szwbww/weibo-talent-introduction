package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigCreateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigUpdateCommand
import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfigView
import com.weibo.talentintroduction.campaign.domain.ManualBatchExecutionRequest
import com.weibo.talentintroduction.campaign.service.BatchSendConfig
import com.weibo.talentintroduction.campaign.service.BatchSendConfigUpdateRequest
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendStatusView
import com.weibo.talentintroduction.campaign.service.CronPreviewResult
import com.weibo.talentintroduction.campaign.service.ExecutionLiveView
import com.weibo.talentintroduction.campaign.service.BatchSendTaskConfigService
import com.weibo.talentintroduction.campaign.service.BatchSendType
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.campaign.service.PendingOutreachSummary
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/mail/batch-send")
class BatchSendConfigController(
    private val batchSendTaskConfigService: BatchSendTaskConfigService,
    private val templateRepository: MailComposeTemplateRepository,
    private val batchSendControlService: BatchSendControlService,
    private val manualInitialOutreachService: ManualInitialOutreachService,
    private val taskExecutionService: TaskExecutionService,
    private val progressLogRepository: TaskProgressLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(BatchSendConfigController::class.java)
    // ── New multi-config CRUD ──────────────────────────────────────────────────

    @GetMapping("/configs")
    fun listConfigs(@RequestParam(required = false) q: String?): ResponseEntity<List<BatchSendTaskConfigView>> =
        ResponseEntity.ok(batchSendTaskConfigService.list(q))

    @PostMapping("/configs")
    fun createConfig(@RequestBody request: BatchSendTaskConfigCreateCommand): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.status(HttpStatus.CREATED).body(batchSendTaskConfigService.create(request))

    @GetMapping("/configs/{id}")
    fun getConfigById(@PathVariable id: Long): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.get(id))

    @PutMapping("/configs/{id}")
    fun updateConfigById(
        @PathVariable id: Long,
        @RequestBody request: BatchSendTaskConfigUpdateCommand
    ): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.update(id, request))

    @PatchMapping("/configs/{id}/enabled")
    fun setConfigEnabled(
        @PathVariable id: Long,
        @RequestBody request: BatchSendTaskConfigEnabledRequest
    ): ResponseEntity<BatchSendTaskConfigView> =
        ResponseEntity.ok(batchSendTaskConfigService.setEnabled(id, request.enabled))

    @DeleteMapping("/configs/{id}")
    fun deleteConfig(@PathVariable id: Long): ResponseEntity<Void> {
        batchSendTaskConfigService.softDelete(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * POST (not GET) so cron expressions with '?' '*' do not need query-string escaping.
     * Always 200: invalid cron is a normal editor state, not an error (I-3).
     */
    @PostMapping("/cron/preview")
    fun previewCron(@RequestBody request: CronPreviewRequest): ResponseEntity<CronPreviewResult> =
        ResponseEntity.ok(batchSendTaskConfigService.previewCron(request.cron, request.count ?: 5))

    /**
     * Recipient-count preview (P-F / 06): input is the launch snapshot itself (I-2), computed
     * with the exact execution-path target code (I-1) and no side effects (I-3).
     * POST (not GET) — the snapshot carries tags/regions arrays that do not fit a query string.
     */
    @PostMapping("/recipients/preview")
    fun previewRecipients(@RequestBody snapshot: BatchExecutionSnapshot): ResponseEntity<PendingOutreachSummary> =
        ResponseEntity.ok(manualInitialOutreachService.countBySnapshot(snapshot))

    @PostMapping("/configs/{id}/execute")
    fun executeConfig(@PathVariable id: Long): ResponseEntity<Map<String, Any>> =
        batchSendControlService.startManualFromConfig(id)

    @PostMapping("/manual-executions")
    fun executeManual(@RequestBody request: ManualBatchExecutionRequest): ResponseEntity<Map<String, Any>> =
        batchSendControlService.startManual(request)

    @GetMapping("/configs/{id}/executions")
    fun listConfigExecutions(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "50") limit: Int
    ): ResponseEntity<List<BatchConfigExecutionSummary>> {
        val clamped = limit.coerceIn(1, 200)
        val executions = taskExecutionService.listRecentByBatchConfigId(id, clamped)
        return ResponseEntity.ok(executions.map { toSummary(it) })
    }

    @GetMapping("/configs/{id}/executions/{executionId}")
    fun getConfigExecutionDetail(
        @PathVariable id: Long,
        @PathVariable executionId: Long
    ): ResponseEntity<BatchConfigExecutionDetail> {
        val execution = taskExecutionService.getExecution(executionId)
        if (execution.batchConfigId != id) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        return ResponseEntity.ok(
            toDetail(execution, buildProgressRows(executionId), live = batchSendControlService.getLiveExecutionView(executionId))
        )
    }

    // ── Execution-level endpoints (I-3: independent manual executions) ──────────

    @GetMapping("/executions/{executionId}")
    fun getExecutionDetail(
        @PathVariable executionId: Long
    ): ResponseEntity<BatchConfigExecutionDetail> {
        val execution = taskExecutionService.getExecution(executionId)
        if (execution.taskType != BatchSendControlService.TASK_TYPE) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
        return ResponseEntity.ok(
            toDetail(execution, buildProgressRows(executionId), live = batchSendControlService.getLiveExecutionView(executionId))
        )
    }

    @PostMapping("/executions/{executionId}/cancel")
    fun cancelExecution(
        @PathVariable executionId: Long
    ): ResponseEntity<Map<String, Any>> = batchSendControlService.cancelExecution(executionId)

    private fun buildProgressRows(executionId: Long): List<ExecutionProgressRow> {
        val logs = progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(executionId)
        val (zeroRows, roundRows) = logs.partition { it.batchNumber == 0 }
        val initRow = zeroRows.firstOrNull()
        val finalRows = zeroRows.drop(1)
        return buildList {
            initRow?.let { add(it to "INIT") }
            addAll(roundRows.groupBy { it.batchNumber }.map { (_, group) -> group.last() to "ROUND" })
            finalRows.forEach { add(it to "FINAL") }
        }
            .sortedBy { (row, _) -> row.id ?: 0L }
            .map { (row, kind) -> toExecutionProgressRow(row, kind) }
    }

    // ── INTRODUCTION compat config endpoints (legacy → entity adapter) ─────────

    @GetMapping("/config")
    fun getConfig(): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendTaskConfigService.getLegacyConfig(BatchSendType.INTRODUCTION))

    @PutMapping("/config")
    fun updateConfig(@RequestBody request: BatchSendConfigUpdateRequest): ResponseEntity<BatchSendConfig> {
        validateTemplate(request.templateId, BatchSendType.INTRODUCTION)
        return ResponseEntity.ok(
            batchSendTaskConfigService.updateLegacyConfig(BatchSendType.INTRODUCTION, request)
        )
    }

    // ── Typed config endpoints (legacy compat → entity adapter) ────────────────

    @GetMapping("/types/{sendType}/config")
    fun getConfigByType(@PathVariable sendType: BatchSendType): ResponseEntity<BatchSendConfig> =
        ResponseEntity.ok(batchSendTaskConfigService.getLegacyConfig(sendType))

    @PutMapping("/types/{sendType}/config")
    fun updateConfigByType(
        @PathVariable sendType: BatchSendType,
        @RequestBody request: BatchSendConfigUpdateRequest
    ): ResponseEntity<BatchSendConfig> {
        validateTemplate(request.templateId, sendType)
        return ResponseEntity.ok(batchSendTaskConfigService.updateLegacyConfig(sendType, request))
    }

    // ── Typed control endpoints ────────────────────────────────────────────────

    @GetMapping("/types/{sendType}/pending-count")
    fun getPendingCount(@PathVariable sendType: BatchSendType): ResponseEntity<PendingOutreachSummary> =
        ResponseEntity.ok(manualInitialOutreachService.countPending(sendType))

    @PostMapping("/types/{sendType}/start")
    fun startManual(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.startManual(sendType)

    @PostMapping("/types/{sendType}/pause")
    fun pause(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.pause("OPERATOR", sendType)

    @PostMapping("/types/{sendType}/manual")
    fun runManualOnce(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.runManualOnce(sendType)

    @PostMapping("/types/{sendType}/start-auto")
    fun startAuto(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.startAuto(sendType)

    @PostMapping("/types/{sendType}/resume-schedule")
    fun resumeSchedule(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.resumeSchedule(sendType)

    @PostMapping("/types/{sendType}/pause-schedule")
    fun pauseSchedule(@PathVariable sendType: BatchSendType): ResponseEntity<Map<String, String>> =
        batchSendControlService.pauseSchedule(sendType)

    @GetMapping("/types/{sendType}/status")
    fun getStatus(@PathVariable sendType: BatchSendType): ResponseEntity<BatchSendStatusView> =
        ResponseEntity.ok(batchSendControlService.getStatus(sendType))

    // ── I-7: template type gate (legacy typed API) ─────────────────────────────

    private fun toSummary(execution: com.weibo.talentintroduction.task.domain.TaskExecution): BatchConfigExecutionSummary {
        val outcome = parseOutcome(execution.resultSummary)
        val durationMs = if (execution.finishedAt != null) {
            Duration.between(execution.startedAt, execution.finishedAt).toMillis()
        } else null
        return BatchConfigExecutionSummary(
            executionId = execution.id,
            triggerType = execution.triggerType,
            status = execution.status,
            target = outcome?.target ?: execution.successCount + execution.failureCount,
            success = outcome?.success ?: execution.successCount,
            failure = outcome?.failure ?: execution.failureCount,
            skipped = outcome?.skipped ?: 0,
            remaining = outcome?.remaining ?: 0,
            startedAt = execution.startedAt,
            finishedAt = execution.finishedAt,
            durationMs = durationMs
        )
    }

    private fun toDetail(
        execution: com.weibo.talentintroduction.task.domain.TaskExecution,
        progressRows: List<ExecutionProgressRow>,
        live: ExecutionLiveView? = null
    ): BatchConfigExecutionDetail {
        val outcome = parseOutcome(execution.resultSummary)
        val runningFallback = if (outcome == null) {
            execution.id?.let {
                parseProgressLogOutcome(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(it))
            }
        } else null
        val requestSnapshot = execution.requestPayload?.let {
            runCatching { objectMapper.readTree(it) }.getOrNull()
        }
        return BatchConfigExecutionDetail(
            executionId = execution.id,
            triggerType = execution.triggerType,
            status = execution.status,
            target = outcome?.target ?: runningFallback?.target ?: 0,
            success = outcome?.success ?: runningFallback?.success ?: execution.successCount,
            failure = outcome?.failure ?: runningFallback?.failure ?: execution.failureCount,
            skipped = outcome?.skipped ?: runningFallback?.skipped ?: 0,
            remaining = outcome?.remaining ?: runningFallback?.remaining ?: 0,
            startedAt = execution.startedAt,
            finishedAt = execution.finishedAt,
            durationMs = if (execution.finishedAt != null) {
                Duration.between(execution.startedAt, execution.finishedAt).toMillis()
            } else null,
            requestSnapshot = requestSnapshot,
            failureReasons = outcome?.failureReasons ?: runningFallback?.failureReasons ?: emptyMap(),
            skippedReasons = outcome?.skippedReasons ?: runningFallback?.skippedReasons ?: emptyMap(),
            errorSamples = outcome?.errorSamples ?: runningFallback?.errorSamples ?: emptyList(),
            progressRows = progressRows,
            live = live
        )
    }

    private fun toExecutionProgressRow(log: TaskProgressLog, kind: String): ExecutionProgressRow {
        return ExecutionProgressRow(
            kind = kind,
            batchNumber = log.batchNumber,
            status = log.status,
            message = log.message,
            stopReason = parseStopReason(log),
            processedCount = log.processedCount,
            totalCount = log.totalCount,
            batchProcessed = log.batchProcessed,
            batchPassed = log.batchPassed,
            batchRejected = log.batchRejected,
            errors = parseErrors(log.errorsJson),
            createdAt = log.createdAt
        )
    }

    private fun parseStopReason(entry: TaskProgressLog): String? {
        val detailsJson = entry.detailsJson ?: return null
        return try {
            val node = objectMapper.readTree(detailsJson)
            val stop = node.path("stopReason")
            if (stop.isMissingNode || stop.isNull) null else stop.asText()
        } catch (e: Exception) {
            log.warn("Failed to parse stopReason from progress log {}: {}", entry.id, e.message)
            null
        }
    }

    private fun parseErrors(errorsJson: String?): List<String> {
        if (errorsJson.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readTree(errorsJson).map { it.asText() }
        } catch (e: Exception) {
            log.warn("Failed to parse errorsJson from progress log: {}", e.message)
            emptyList()
        }
    }

    private fun parseProgressLogOutcome(entry: TaskProgressLog?): ParsedOutcome? {
        if (entry == null) return null
        val detailsJson = entry.detailsJson ?: return null
        return try {
            val node = objectMapper.readTree(detailsJson)
            ParsedOutcome(
                target = entry.totalCount.toInt(),
                success = node.path("sentTotal").asInt(0),
                failure = node.path("failedTotal").asInt(0),
                skipped = node.path("skippedTotal").asInt(0),
                remaining = node.path("pending").asInt(0),
                failureReasons = parseReasonMap(node.path("failureReasons")),
                skippedReasons = parseReasonMap(node.path("skippedReasons")),
                errorSamples = parseErrors(entry.errorsJson)
            )
        } catch (e: Exception) {
            log.warn("Failed to parse progress log outcome: {}", e.message)
            null
        }
    }

    private fun parseOutcome(resultSummary: String?): ParsedOutcome? {
        if (resultSummary.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(resultSummary)
            val outcomeNode = root.path("outcome")
            if (!outcomeNode.isMissingNode) {
                ParsedOutcome(
                    target = outcomeNode.path("target").asInt(0),
                    success = outcomeNode.path("success").asInt(0),
                    failure = outcomeNode.path("failure").asInt(0),
                    skipped = outcomeNode.path("skipped").asInt(0),
                    remaining = outcomeNode.path("remaining").asInt(0),
                    failureReasons = parseReasonMap(outcomeNode.path("failureReasons")),
                    skippedReasons = parseReasonMap(outcomeNode.path("skippedReasons")),
                    errorSamples = outcomeNode.path("errorSamples").map { it.asText() }
                )
            } else {
                ParsedOutcome(
                    target = root.path("total").asInt(0),
                    success = root.path("sent").asInt(0),
                    failure = root.path("failed").asInt(0),
                    skipped = root.path("skipped").asInt(0),
                    remaining = root.path("remaining").asInt(0),
                    failureReasons = emptyMap(),
                    skippedReasons = emptyMap(),
                    errorSamples = emptyList()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseReasonMap(node: com.fasterxml.jackson.databind.JsonNode): Map<String, com.weibo.talentintroduction.campaign.domain.ReasonCount> {
        if (node.isMissingNode || !node.isObject) return emptyMap()
        return node.fields().asSequence().associate { (code, value) ->
            code to com.weibo.talentintroduction.campaign.domain.ReasonCount(
                label = value.path("label").asText(code),
                count = value.path("count").asInt(0)
            )
        }
    }

    /**
     * I-7: enforces that the template pointed to by templateId is enabled and
     * has the mailType matching sendType.
     * INTRODUCTION: null templateId is allowed (falls back to default INTRODUCTION template).
     * MATERIAL_REMINDER: templateId must not be null (enforced in service validate; checked here too).
     */
    private fun validateTemplate(templateId: Long?, sendType: BatchSendType) {
        if (sendType == BatchSendType.MATERIAL_REMINDER) {
            requireNotNull(templateId) { "MATERIAL_REMINDER config requires a templateId" }
        }
        if (templateId == null) return
        val template = templateRepository.findById(templateId).orElse(null)
            ?: throw IllegalArgumentException("Template $templateId not found")
        require(template.enabled) { "Template $templateId is not enabled" }
        val expectedMailType = sendType.name
        require(template.mailType == expectedMailType) {
            "Template $templateId has mailType=${template.mailType}, expected $expectedMailType for $sendType"
        }
    }
}

data class BatchSendTaskConfigEnabledRequest(
    val enabled: Boolean
)

data class CronPreviewRequest(
    val cron: String,
    val count: Int? = null
)

data class BatchConfigExecutionSummary(
    val executionId: Long?,
    val triggerType: String,
    val status: String,
    val target: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val remaining: Int,
    val startedAt: java.time.LocalDateTime,
    val finishedAt: java.time.LocalDateTime?,
    val durationMs: Long?
)

data class BatchConfigExecutionDetail(
    val executionId: Long?,
    val triggerType: String,
    val status: String,
    val target: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val remaining: Int,
    val startedAt: java.time.LocalDateTime,
    val finishedAt: java.time.LocalDateTime?,
    val durationMs: Long?,
    val requestSnapshot: com.fasterxml.jackson.databind.JsonNode?,
    val failureReasons: Map<String, com.weibo.talentintroduction.campaign.domain.ReasonCount>,
    val skippedReasons: Map<String, com.weibo.talentintroduction.campaign.domain.ReasonCount>,
    val errorSamples: List<String>,
    val progressRows: List<ExecutionProgressRow>,
    val live: ExecutionLiveView? = null
)

data class ExecutionProgressRow(
    val kind: String,            // INIT | ROUND | FINAL
    val batchNumber: Int,
    val status: String,
    val message: String?,
    val stopReason: String?,
    val processedCount: Long,
    val totalCount: Long,
    val batchProcessed: Int,
    val batchPassed: Int,
    val batchRejected: Int,
    val errors: List<String>,
    val createdAt: java.time.LocalDateTime
)

private data class ParsedOutcome(
    val target: Int,
    val success: Int,
    val failure: Int,
    val skipped: Int,
    val remaining: Int,
    val failureReasons: Map<String, com.weibo.talentintroduction.campaign.domain.ReasonCount>,
    val skippedReasons: Map<String, com.weibo.talentintroduction.campaign.domain.ReasonCount>,
    val errorSamples: List<String>
)
