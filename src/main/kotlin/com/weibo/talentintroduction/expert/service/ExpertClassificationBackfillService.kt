package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 回填模式（I2-1/I2-3）。 */
enum class BackfillMode { DRY_RUN, EXECUTE }

/**
 * 分类回填请求模型（子计划 02 固定字段，I2-3）。
 *
 * level/mode/version 必须显式提供，此处以 nullable 声明让校验层能区分「未提供」与非法值
 * （请求验证在 controller 返回 400，服务内部再 require 兜底，供子计划 04 调度复用）。
 */
data class ExpertClassificationBackfillRequest(
    val level: ExpertIndexLevel? = null,
    val mode: BackfillMode? = null,
    val version: String? = null,
    val batchSize: Int = 500,
    val delayMs: Int = 250,
    val maxDocs: Long? = null,
    val onlyPending: Boolean = true,
    val confirmation: String? = null
)

/**
 * 回填结果（I2-6 统计口径）。实现 [TaskExecutionSummaryProvider]，
 * 使 runAndRecordWithResult 落正确的 success_count / failure_count / 终态：
 * successCount=writeSuccess（DRY_RUN 时为 scanned），failureCount=writeFailure，
 * 取消 → CANCELLED，部分失败 → PARTIAL_SUCCESS，全失败 → FAILED。
 */
data class ExpertClassificationBackfillResult(
    val level: ExpertIndexLevel,
    val mode: BackfillMode,
    val policyVersion: String,
    val scanned: Long,
    val classifiedByType: Map<String, Long>,
    val sendable: Long,
    val notSendable: Long,
    val writeSuccess: Long,
    val writeNoop: Long,
    val writeFailure: Long,
    val skippedMissingDocId: Long,
    val reasonCounts: Map<String, Long>,
    val wasCancelled: Boolean,
    val terminalMessage: String? = null,
    val immediateFailed: Boolean = false
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int
        get() = if (mode == BackfillMode.DRY_RUN) scanned.toInt() else writeSuccess.toInt()

    override val taskFailureCount: Int
        get() = writeFailure.toInt()

    override val taskFinalStatus: String?
        get() = when {
            wasCancelled -> "CANCELLED"
            immediateFailed -> "FAILED"
            writeFailure > 0 && writeSuccess > 0 -> "PARTIAL_SUCCESS"
            writeFailure > 0 -> "FAILED"
            else -> "SUCCESS"
        }
}

/**
 * 专家分类模拟与线上回填任务（I2-1 ~ I2-6，M-2/M-4/M-6）。
 *
 * - 只扫描、分类、聚合（DRY_RUN）；EXECUTE 时调用 [ExpertIndexWriterService.bulkUpdateExpertClassifications]
 *   做局部更新，绝不复制分类规则或 bulk 协议（M-2）。
 * - onlyPending 过滤 = `must_not exists expertClassification.version OR must_not term version=rnd-v1-2026`
 *   （should/minimum_should_match=1），同版本文档重跑自动跳过（I2-4）。
 * - 每批前检查取消；限速 delay 分段等待（单次 sleep <= 1s）并重复检查取消（I2-4）。
 * - mapping 缺失或首批全部 mapper 错误 → 立即 FAILED，不继续刷失败请求（I2-2）。
 */
@Service
class ExpertClassificationBackfillService(
    private val classificationService: ExpertClassificationService,
    private val indexWriter: ExpertIndexWriterService,
    private val searchService: ExpertSearchService,
    private val progressStore: TaskProgressStore
) {
    private val log = LoggerFactory.getLogger(ExpertClassificationBackfillService::class.java)

    fun run(request: ExpertClassificationBackfillRequest, executionId: Long?): ExpertClassificationBackfillResult {
        validate(request)
        val level = requireNotNull(request.level)
        val mode = requireNotNull(request.mode)

        if (!indexWriter.checkExpertClassificationMapping(level)) {
            val result = failImmediately(request, executionId, "分类 mapping 缺失：索引缺少 expertClassification 字段（type/sendable/version），已停止")
            log.warn("Expert classification backfill aborted before scan: {}", result.terminalMessage)
            return result
        }

        val counters = BackfillCounters()
        var batchNumber = 0
        var cancelled = false
        var stopMessage: String? = null

        val filters = if (request.onlyPending) listOf(pendingOnlyFilter()) else emptyList()

        searchService.searchAfterExpertsFiltered(level, filters, request.batchSize) { batch ->
            batchNumber++
            if (isCancelled(executionId)) {
                cancelled = true
                return@searchAfterExpertsFiltered false
            }
            if (request.maxDocs != null && counters.scanned >= request.maxDocs) {
                return@searchAfterExpertsFiltered false
            }

            val writeItems = mutableListOf<ClassificationBulkItem>()
            for (profile in batch) {
                if (request.maxDocs != null && counters.scanned >= request.maxDocs) break
                val classification = classificationService.classify(profile)
                counters.record(classification)
                val docId = profile.esDocId
                if (docId.isNullOrBlank()) {
                    counters.skippedMissingDocId++
                    continue
                }
                if (mode == BackfillMode.EXECUTE) {
                    writeItems += ClassificationBulkItem(docId, classification)
                }
            }

            if (mode == BackfillMode.EXECUTE && writeItems.isNotEmpty()) {
                val bulkResult = indexWriter.bulkUpdateExpertClassifications(level, writeItems)
                counters.recordWrite(bulkResult)
                if (batchNumber == 1 && bulkResult.allFailedWithMapperError) {
                    stopMessage = "首批全部 mapper 错误：分类 mapping 缺失或字段类型冲突，已停止"
                    log.warn("{} (batch=1, failed={})", stopMessage, bulkResult.failure)
                    return@searchAfterExpertsFiltered false
                }
                if (bulkResult.wholesaleError != null) {
                    stopMessage = bulkResult.wholesaleError
                    log.warn("Expert classification backfill stopped after wholesale bulk failure: {}", stopMessage)
                    return@searchAfterExpertsFiltered false
                }
            }

            publishProgress(request, counters, batchNumber, executionId, "RUNNING")

            if (!cancellableDelay(request.delayMs, isCancelled = { isCancelled(executionId) })) {
                cancelled = true
                return@searchAfterExpertsFiltered false
            }
            true
        }

        val result = counters.toResult(request, cancelled = cancelled, terminalMessage = stopMessage)
        publishProgress(request, counters, -1, executionId, result.taskFinalStatus ?: "SUCCESS", terminalMessage = result.terminalMessage)
        return result
    }

    private fun validate(request: ExpertClassificationBackfillRequest) {
        val level = requireNotNull(request.level) { "level 必填: RAW | CANDIDATE | APPLICATION" }
        val mode = requireNotNull(request.mode) { "mode 必填: DRY_RUN | EXECUTE" }
        require(request.version == ExpertClassificationService.VERSION) {
            "version 只允许 ${ExpertClassificationService.VERSION}"
        }
        require(request.batchSize in 100..1000) { "batchSize 必须在 100..1000" }
        require(request.delayMs in 0..5000) { "delayMs 必须在 0..5000" }
        require(request.maxDocs == null || request.maxDocs > 0) { "maxDocs 必须是正整数" }
        if (mode == BackfillMode.EXECUTE) {
            require(request.confirmation == "EXECUTE_${level.name}:${ExpertClassificationService.VERSION}") {
                "EXECUTE 需要 confirmation = EXECUTE_${level.name}:${ExpertClassificationService.VERSION}"
            }
        }
    }

    /** I2-4：onlyPending 过滤 —— 缺失分类版本或版本 != rnd-v1-2026 视为待处理。 */
    private fun pendingOnlyFilter(): Map<String, Any> =
        mapOf(
            "bool" to mapOf(
                "should" to listOf(
                    mapOf(
                        "bool" to mapOf(
                            "must_not" to listOf(mapOf("exists" to mapOf("field" to "expertClassification.version")))
                        )
                    ),
                    mapOf(
                        "bool" to mapOf(
                            "must_not" to listOf(
                                mapOf("term" to mapOf("expertClassification.version" to ExpertClassificationService.VERSION))
                            )
                        )
                    )
                ),
                "minimum_should_match" to 1
            )
        )

    private fun isCancelled(executionId: Long?): Boolean =
        executionId != null && progressStore.isCancelled(ExpertClassificationBackfillService.TASK_TYPE, executionId)

    /**
     * I2-4：可取消的分段限速等待。单次 sleep 不超过 [SEGMENTED_SLEEP_MS]（1s），
     * 每次醒来都重新检查取消。返回 false 表示已取消。
     */
    internal fun cancellableDelay(delayMs: Int, isCancelled: () -> Boolean, sleep: (Long) -> Unit = { Thread.sleep(it) }): Boolean {
        var remaining = delayMs
        while (remaining > 0) {
            if (isCancelled()) return false
            val step = minOf(remaining, SEGMENTED_SLEEP_MS)
            sleep(step.toLong())
            remaining -= step
        }
        return !isCancelled()
    }

    private fun publishProgress(
        request: ExpertClassificationBackfillRequest,
        counters: BackfillCounters,
        batchNumber: Int,
        executionId: Long?,
        status: String,
        terminalMessage: String? = null
    ) {
        progressStore.update(
            ExpertClassificationBackfillService.TASK_TYPE,
            TaskProgress(
                taskType = ExpertClassificationBackfillService.TASK_TYPE,
                status = status,
                batchNumber = batchNumber,
                processedCount = counters.scanned,
                totalCount = request.maxDocs ?: 0,
                message = terminalMessage ?: progressMessage(request, counters),
                details = counters.statsMap(request)
            ),
            executionId
        )
    }

    private fun failImmediately(
        request: ExpertClassificationBackfillRequest,
        executionId: Long?,
        message: String
    ): ExpertClassificationBackfillResult {
        val counters = BackfillCounters()
        val result = counters.toResult(request, cancelled = false, terminalMessage = message, immediateFailed = true)
        publishProgress(request, counters, 0, executionId, "FAILED", terminalMessage = message)
        return result
    }

    private fun progressMessage(request: ExpertClassificationBackfillRequest, counters: BackfillCounters): String =
        if (request.mode == BackfillMode.DRY_RUN) {
            "已扫描 ${counters.scanned}，可发信 ${counters.sendable}，不可发信 ${counters.notSendable}"
        } else {
            "已扫描 ${counters.scanned}，成功 ${counters.writeSuccess}，失败 ${counters.writeFailure}"
        }

    private class BackfillCounters {
        var scanned: Long = 0
        var sendable: Long = 0
        var notSendable: Long = 0
        var writeSuccess: Long = 0
        var writeNoop: Long = 0
        var writeFailure: Long = 0
        var skippedMissingDocId: Long = 0
        val classifiedByType: MutableMap<String, Long> =
            ExpertType.values().associateTo(mutableMapOf()) { it.name to 0L }
        val reasonCounts: MutableMap<String, Long> = mutableMapOf()

        fun record(classification: ExpertClassification) {
            scanned++
            classifiedByType[classification.type.name] = (classifiedByType[classification.type.name] ?: 0L) + 1
            if (classification.sendable) sendable++ else notSendable++
            classification.negativeEvidence.forEach { code ->
                reasonCounts[code] = (reasonCounts[code] ?: 0L) + 1
            }
        }

        fun recordWrite(result: ClassificationBulkResult) {
            writeSuccess += result.updated
            writeNoop += result.noop
            writeFailure += result.failure
        }

        fun statsMap(request: ExpertClassificationBackfillRequest): Map<String, Any> = mapOf(
            "level" to requireNotNull(request.level).name,
            "mode" to requireNotNull(request.mode).name,
            "policyVersion" to ExpertClassificationService.VERSION,
            "scanned" to scanned,
            "sendable" to sendable,
            "notSendable" to notSendable,
            "writeSuccess" to writeSuccess,
            "writeNoop" to writeNoop,
            "writeFailure" to writeFailure,
            "skippedMissingDocId" to skippedMissingDocId,
            "classifiedByType" to classifiedByType,
            "reasonCounts" to reasonCounts
        )

        fun toResult(
            request: ExpertClassificationBackfillRequest,
            cancelled: Boolean,
            terminalMessage: String?,
            immediateFailed: Boolean = false
        ): ExpertClassificationBackfillResult =
            ExpertClassificationBackfillResult(
                level = requireNotNull(request.level),
                mode = requireNotNull(request.mode),
                policyVersion = ExpertClassificationService.VERSION,
                scanned = scanned,
                classifiedByType = classifiedByType,
                sendable = sendable,
                notSendable = notSendable,
                writeSuccess = writeSuccess,
                writeNoop = writeNoop,
                writeFailure = writeFailure,
                skippedMissingDocId = skippedMissingDocId,
                reasonCounts = reasonCounts,
                wasCancelled = cancelled,
                terminalMessage = terminalMessage,
                immediateFailed = immediateFailed
            )
    }

    companion object {
        const val TASK_TYPE = "EXPERT_CLASSIFICATION_BACKFILL"

        /** 限速分段等待的单次 sleep 上限（I2-4：单次 sleep 不超过 1 秒）。 */
        internal const val SEGMENTED_SLEEP_MS = 1000
    }
}
