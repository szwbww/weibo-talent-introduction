package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.domain.TaskTypeCatalog
import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * T-2：任务活动观察端点（I-2/I-3/I-4/I-8）。
 *
 * 与 [TaskExecutionController] 分离：后者被大量直构测试依赖，且其 `/{id}` 端点契约
 * （SELECT * 含 TEXT）不适合高频轮询。本控制器只做两件事——有界地读任务记录、
 * 只读地读内存进度——并合成卡片所需的展示字段。
 *
 * 硬约束：
 * - 每次请求最多 1 次 COUNT + 1 次分页 SELECT + 内存读取（I-4）；
 * - 不返回 request_payload / result_summary / error_message 三个 TEXT 列，
 *   也不返回完整 [TaskProgress]（details/errors/batchRejectReasons 不上卡片）；
 * - 只 GET，不触碰任何执行/调度写路径（I-4）；
 * - 遵循既有 auth interceptor，不新增匿名路径（I-8）。
 */
@RestController
@RequestMapping("/api/task-executions")
class TaskActivityController(
    private val taskExecutionRepository: TaskExecutionRepository,
    private val taskProgressStore: TaskProgressStore
) {

    /**
     * 进行中执行集合。集合口径是**数据库记录状态**（`status IN ('RUNNING','CANCELLING')`），
     * 不是进程存活探针——进程崩溃后遗留的 RUNNING 行仍会被返回，UI 必须标注该口径（I-2/I-3）。
     *
     * 分页与 total 是两条独立 SQL，不做事务快照：并发的任务开始/结束允许产生一轮暂态不一致，
     * 下一轮自愈（I-2 交互点 X-2）。
     */
    @GetMapping("/active")
    fun activeExecutions(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "6") size: Int
    ): TaskActivityPageResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 50)
        val offset = safePage.toLong() * safeSize
        val capturedNow = LocalDateTime.now()
        val items = taskExecutionRepository.findActivePage(safeSize, offset)
            .map { it.toActivityItem(capturedNow) }
        val total = taskExecutionRepository.countActive()
        return TaskActivityPageResponse(items = items, total = total, page = safePage, size = safeSize)
    }

    /**
     * 进度匹配（I-3）：只接受"同一次执行"的内存进度，否则一律 null。
     *
     * peek 只读 store，不触发日志恢复：负 pendingToken（未绑定执行 id）、已清理的旧 id、
     * 终态残留、以及同类型的另一次执行都必须判为无实时数据，而不是把历史快照当成实时状态。
     */
    private fun matchProgress(taskType: String, executionId: Long): TaskActivityProgress? {
        if (executionId <= 0L) return null
        val progress = taskProgressStore.peek(taskType) ?: return null
        if (progress.taskType != taskType) return null
        if (progress.executionId != executionId) return null
        if (progress.status != "RUNNING" && progress.status != "CANCELLING") return null
        return TaskActivityProgress(
            status = progress.status,
            processedCount = progress.processedCount,
            totalCount = progress.totalCount,
            // totalCount <= 0 时百分比无意义：返回 null，前端不渲染 0% 或空进度条（I-3）。
            percentage = if (progress.totalCount > 0) progress.percentage else null,
            message = progress.message?.take(MAX_MESSAGE_LENGTH)
        )
    }

    private fun TaskExecutionListItem.toActivityItem(now: LocalDateTime): TaskActivityItemResponse {
        val meta = TaskTypeCatalog.byCode(taskType)
        return TaskActivityItemResponse(
            id = id,
            taskType = taskType,
            // catalog 未声明的类型仍然返回，label 回落 code；triggerType 原样，绝不用 group 替代（I-2）。
            taskTypeLabel = meta?.label ?: taskType,
            triggerType = triggerType,
            status = status,
            startedAt = startedAt.format(DATE_FMT),
            elapsedSeconds = Duration.between(startedAt, now).seconds.coerceAtLeast(0),
            metricLabel = meta?.metricLabel,
            successCount = successCount,
            failureCount = failureCount,
            // hasProgressUi 只表示 catalog 声明了进度 UI；是否已有启动/控制按钮由前端
            // taskButtonMapping 决定，二者刻意分离（I-6）。
            hasProgressUi = meta?.hasProgressUi ?: false,
            progress = matchProgress(taskType, id)
        )
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** I-3：卡片消息长度上限，避免把长文本搬进 5 秒一次的轮询响应。 */
        private const val MAX_MESSAGE_LENGTH = 500
    }
}

data class TaskActivityProgress(
    val status: String,
    val processedCount: Long,
    val totalCount: Long,
    /** totalCount <= 0 时为 null。 */
    val percentage: Int?,
    val message: String?
)

data class TaskActivityItemResponse(
    val id: Long,
    val taskType: String,
    val taskTypeLabel: String,
    val triggerType: String,
    /** DB 行状态原样；前端只在匹配的 progress.status=CANCELLING 时把标签改"取消中"。 */
    val status: String,
    val startedAt: String,
    val elapsedSeconds: Long,
    val metricLabel: String?,
    val successCount: Int,
    val failureCount: Int,
    val hasProgressUi: Boolean,
    val progress: TaskActivityProgress?
)

data class TaskActivityPageResponse(
    val items: List<TaskActivityItemResponse>,
    val total: Long,
    val page: Int,
    val size: Int
)
