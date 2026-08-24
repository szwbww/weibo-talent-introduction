package com.weibo.talentintroduction.expert.controller

import com.weibo.talentintroduction.expert.service.BackfillMode
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillRequest
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillResult
import com.weibo.talentintroduction.expert.service.ExpertClassificationBackfillService
import com.weibo.talentintroduction.expert.service.ExpertClassificationService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * 专家分类回填管理 API（I2-3/I2-5，M-6）。
 *
 * 无前端按钮：仅管理员 session 可调（AuthInterceptor 覆盖全部 /api 路径，未登录 401）。
 * `POST /api/expert-classification/backfill` 校验请求 → tryStartWithToken 抢互斥 →
 * 提交单线程 `expertClassificationExecutor` 异步执行 → 立即返回 202；任务运行中 409；
 * executor 拒绝 409 并清理 pending context。进度/日志/取消复用
 * `/api/task-progress/EXPERT_CLASSIFICATION_BACKFILL` 家族，不另造端点。
 */
@RestController
@RequestMapping("/api/expert-classification")
class ExpertClassificationAdminController(
    private val backfillService: ExpertClassificationBackfillService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    @Qualifier("expertClassificationExecutor")
    private val expertClassificationExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(ExpertClassificationAdminController::class.java)

    @PostMapping("/backfill")
    fun startBackfill(@RequestBody request: ExpertClassificationBackfillRequest): ResponseEntity<Any> {
        val validationError = validationError(request)
        if (validationError != null) {
            return ResponseEntity.badRequest().body(mapOf("message" to validationError))
        }
        val level = request.level!!
        val mode = request.mode!!

        val initialProgress = TaskProgress(
            taskType = ExpertClassificationBackfillService.TASK_TYPE,
            status = "RUNNING",
            batchNumber = 0,
            processedCount = 0,
            totalCount = request.maxDocs ?: 0,
            message = "初始化中...",
            details = mapOf(
                "level" to level.name,
                "mode" to mode.name,
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
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf(
                    "message" to "已有分类回填任务运行中",
                    "taskType" to ExpertClassificationBackfillService.TASK_TYPE
                ))
        }

        var executionId: Long? = null
        try {
            expertClassificationExecutor.execute {
                try {
                    taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                        ExpertClassificationBackfillService.TASK_TYPE,
                        "MANUAL",
                        request,
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId(ExpertClassificationBackfillService.TASK_TYPE, pendingToken, id)
                        }
                    ) {
                        backfillService.run(request, executionId!!)
                    }
                } catch (ex: Exception) {
                    log.error("Expert classification backfill execution failed", ex)
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
            log.warn("Expert classification backfill launch rejected: {}", reEx.message)
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
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf(
                    "message" to "任务启动失败：线程池已满或已关闭",
                    "taskType" to ExpertClassificationBackfillService.TASK_TYPE
                ))
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(mapOf(
                "message" to "任务已启动",
                "taskType" to ExpertClassificationBackfillService.TASK_TYPE
            ))
    }

    /** I2-3：level/mode/version 显式必填、version 只允许 rnd-v1-2026、EXECUTE 精确确认串。 */
    private fun validationError(request: ExpertClassificationBackfillRequest): String? {
        val level = request.level ?: return "level 必填: RAW | CANDIDATE | APPLICATION"
        val mode = request.mode ?: return "mode 必填: DRY_RUN | EXECUTE"
        if (request.version != ExpertClassificationService.VERSION) {
            return "version 只允许 ${ExpertClassificationService.VERSION}"
        }
        if (request.batchSize !in 100..1000) return "batchSize 必须在 100..1000"
        if (request.delayMs !in 0..5000) return "delayMs 必须在 0..5000"
        if (request.maxDocs != null && request.maxDocs <= 0) return "maxDocs 必须是正整数"
        if (mode == BackfillMode.EXECUTE &&
            request.confirmation != "EXECUTE_${level.name}:${ExpertClassificationService.VERSION}"
        ) {
            return "EXECUTE 需要 confirmation = EXECUTE_${level.name}:${ExpertClassificationService.VERSION}"
        }
        return null
    }
}
