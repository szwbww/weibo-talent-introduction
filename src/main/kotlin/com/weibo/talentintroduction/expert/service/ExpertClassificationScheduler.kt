package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.ExpertClassificationProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * 专家分类增量调度（子计划 04，I4-1 ~ I4-5，M-2/M-6）。
 *
 * - 默认关闭：`talent-introduction.expert-classification.incremental-enabled` 为 false/未配置时
 *   bean 不创建，发布/启动不产生任务记录或 ES 写入（I4-1）。
 * - 固定请求：只处理 CANDIDATE 中 pending（缺失分类版本或版本不符）的文档
 *   （CANDIDATE/EXECUTE/rnd-v2-2026/onlyPending=true，确认串 `EXECUTE_CANDIDATE:rnd-v2-2026`），
 *   不自动扫描 RAW/APPLICATION、不 force（I4-2）。分类语义仍只在 `ExpertClassificationService`（M-2）。
 * - 与人工回填共享 `EXPERT_CLASSIFICATION_BACKFILL` taskType、同一 [ExpertClassificationBackfillService]
 *   与同一 `expertClassificationExecutor`：抢锁失败只记 skip，不排队第二个任务（I4-3）。
 * - 有界增量：batchSize/delayMs/maxDocsPerRun 取自配置；达到 maxDocsPerRun 由 backfill 服务以
 *   SUCCESS + remaining 收尾，次日继续，不视为失败（I4-4）。
 * - 生命周期模式与子计划 02 controller 逐字复用：
 *   tryStartWithToken → executor → runAndRecordWithResult → bindExecutionId → finally clear。
 */
@Service
@ConditionalOnProperty(
    prefix = "talent-introduction.expert-classification",
    name = ["incremental-enabled"],
    havingValue = "true"
)
class ExpertClassificationScheduler(
    private val properties: ExpertClassificationProperties,
    private val backfillService: ExpertClassificationBackfillService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    @Qualifier("expertClassificationExecutor")
    private val expertClassificationExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(ExpertClassificationScheduler::class.java)

    @Scheduled(cron = "\${talent-introduction.expert-classification.incremental-cron:0 0 4 * * ?}")
    fun scheduleIncremental() {
        val request = ExpertClassificationBackfillRequest(
            level = ExpertIndexLevel.CANDIDATE,
            mode = BackfillMode.EXECUTE,
            version = ExpertClassificationService.VERSION,
            batchSize = properties.batchSize,
            delayMs = properties.delayMs,
            maxDocs = properties.maxDocsPerRun,
            onlyPending = true,
            confirmation = "EXECUTE_CANDIDATE:${ExpertClassificationService.VERSION}"
        )

        val initialProgress = TaskProgress(
            taskType = ExpertClassificationBackfillService.TASK_TYPE,
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = request.maxDocs ?: 0,
            message = "初始化中...",
            details = mapOf(
                "level" to request.level!!.name,
                "mode" to request.mode!!.name,
                "policyVersion" to ExpertClassificationService.VERSION,
                "batchSize" to request.batchSize,
                "delayMs" to request.delayMs,
                "maxDocs" to (request.maxDocs ?: ""),
                "onlyPending" to request.onlyPending
            )
        )
        val (started, pendingToken) = progressStore.tryStartWithToken(
            ExpertClassificationBackfillService.TASK_TYPE,
            initialProgress
        )
        if (!started) {
            log.warn("incremental classification skipped: task already running")
            return
        }

        var executionId: Long? = null
        try {
            expertClassificationExecutor.execute {
                try {
                    taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                        ExpertClassificationBackfillService.TASK_TYPE,
                        "SCHEDULED",
                        request,
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId(ExpertClassificationBackfillService.TASK_TYPE, pendingToken, id)
                        }
                    ) {
                        backfillService.run(request, executionId!!)
                    }
                } catch (ex: Exception) {
                    log.error("Expert classification incremental execution failed", ex)
                    progressStore.update(
                        ExpertClassificationBackfillService.TASK_TYPE,
                        TaskProgress(
                            taskType = ExpertClassificationBackfillService.TASK_TYPE,
                            status = "FAILED",
                            batchNumber = 0,
                            processedCount = 0,
                            totalCount = request.maxDocs ?: 0,
                            message = ex.message ?: "任务执行失败"
                        ),
                        executionId
                    )
                } finally {
                    val execId = executionId
                    if (execId != null) {
                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, execId)
                    } else {
                        progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
                    }
                }
            }
        } catch (reEx: RejectedExecutionException) {
            log.warn("Expert classification incremental launch rejected: {}", reEx.message)
            progressStore.update(
                ExpertClassificationBackfillService.TASK_TYPE,
                TaskProgress(
                    taskType = ExpertClassificationBackfillService.TASK_TYPE,
                    status = "FAILED",
                    batchNumber = 0,
                    processedCount = 0,
                    totalCount = 0,
                    message = "启动失败: ${reEx.message}"
                ),
                pendingToken
            )
            progressStore.clearExecutionContext(ExpertClassificationBackfillService.TASK_TYPE, pendingToken)
        }
    }
}
