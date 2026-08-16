package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskTypeCatalog
import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskTypeCount
import com.weibo.talentintroduction.task.service.ExecutionTotals
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/task-executions")
class TaskExecutionController(
    private val service: TaskExecutionService,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val extractor: TaskExecutionSummaryExtractor,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(TaskExecutionController::class.java)

    @GetMapping
    fun listExecutions(
        @RequestParam(required = false) taskType: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): TaskExecutionPageResponse {
        val safeSize = size.coerceIn(1, 200)
        val safePage = page.coerceAtLeast(0)
        val result = service.listExecutions(taskType, status, safePage, safeSize)
        return TaskExecutionPageResponse(
            items = result.items.map { it.toListResponse() },
            total = result.total
        )
    }

    /**
     * I1-7：类型下拉选项来自实际数据（`GROUP BY task_type`），与 catalog 左连接：
     * catalog 有声明取中文名，未声明仍返回并以原始枚举名兜底。按 count 降序。
     */
    @GetMapping("/task-types")
    fun taskTypes(): List<TaskTypeOption> =
        taskExecutionRepository.findTaskTypeCounts()
            .map { count ->
                TaskTypeOption(
                    code = count.taskType,
                    label = TaskTypeCatalog.byCode(count.taskType)?.label ?: count.taskType,
                    count = count.cnt
                )
            }
            .sortedByDescending { it.count }

    /**
     * I1-5：任意 taskType 的通用明细端点，一律返回 200（禁止用 require(...) 对
     * taskType 做前置断言）。有结构化 renderer 的类型由前端按 catalog 分派渲染；
     * 无 renderer 的类型走 `rawRequestPayload` / `rawResultSummary` JSON 兜底。
     * 记录不存在抛 [NoSuchElementException] → GlobalExceptionHandler 映射 404。
     */
    @GetMapping("/{id}/detail")
    fun executionDetail(@PathVariable id: Long): TaskExecutionDetailResponse {
        val exec = taskExecutionRepository.findById(id)
            .orElseThrow { NoSuchElementException("Task execution not found: $id") }
        val totals: ExecutionTotals = extractor.extract(exec.taskType, exec)
        val (requestRaw, requestTruncated) = truncateRaw(exec.requestPayload)
        val (summaryRaw, summaryTruncated) = truncateRaw(exec.resultSummary)
        return TaskExecutionDetailResponse(
            id = exec.id,
            taskType = exec.taskType,
            taskTypeLabel = TaskTypeCatalog.byCode(exec.taskType)?.label ?: exec.taskType,
            status = exec.status,
            startedAt = exec.startedAt.format(DATE_FMT),
            finishedAt = exec.finishedAt?.format(DATE_FMT),
            durationSeconds = exec.finishedAt?.let { finish ->
                java.time.Duration.between(exec.startedAt, finish).seconds
            },
            totals = totals,
            metricLabel = TaskTypeCatalog.byCode(exec.taskType)?.metricLabel,
            rawRequestPayload = requestRaw,
            rawResultSummary = summaryRaw,
            rawTruncated = requestTruncated || summaryTruncated
        )
    }

    @GetMapping("/recent-polls")
    fun recentPolls(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<PollLogResponse> {
        require(limit in 1..100) { "limit must be between 1 and 100" }

        val executions = service.listRecentPolls(limit)
        val nextPoll = service.nextPollTime()
        return executions.map { exec ->
            val resultSummary = exec.resultSummary
                ?.let { tryParseResultSummary(it) }
            PollLogResponse(
                id = exec.id,
                triggerType = exec.triggerType,
                status = exec.status,
                totalAccountsToPoll = firstPositive(
                    resultSummary?.totalAccountsToPoll,
                    resultSummary?.accountCount,
                    resultSummary?.totalExpertsToCheck
                ),
                accountsPolled = firstPositive(
                    resultSummary?.accountsPolled,
                    resultSummary?.accountCount,
                    resultSummary?.expertsChecked
                ),
                totalFetched = resultSummary?.fetched ?: 0,
                totalRecorded = resultSummary?.recorded ?: 0,
                totalReplied = resultSummary?.replied ?: 0,
                totalManualReview = resultSummary?.manualReview ?: 0,
                expertsWithReply = resultSummary?.expertsWithReply.orEmpty(),
                startedAt = exec.startedAt.format(DATE_FMT),
                finishedAt = exec.finishedAt?.format(DATE_FMT),
                durationSeconds = exec.finishedAt?.let { finish ->
                    java.time.Duration.between(exec.startedAt, finish).seconds
                },
                nextPollAt = nextPoll?.format(DATE_FMT),
                isManualTrigger = exec.triggerType.startsWith("MANUAL_")
            )
        }
    }

    @GetMapping("/recent-polls/{id}/detail")
    fun pollDetail(@PathVariable id: Long): PollDetailResponse {
        val exec = service.getExecution(id)
        require(exec.taskType == "AUTO_REPLY_ALL") { "Not a poll execution" }
        val raw = exec.resultSummary
        if (raw == null) {
            return PollDetailResponse(id = exec.id, accounts = emptyList(), error = exec.errorMessage)
        }
        return try {
            val detail = objectMapper.readValue<PollDetailRaw>(raw)
            PollDetailResponse(
                id = exec.id,
                accounts = detail.accounts.map { acct ->
                    PollAccountDetail(
                        accountCode = acct.accountCode,
                        status = acct.status,
                        fetched = acct.fetched,
                        recorded = acct.recorded,
                        replied = acct.replied,
                        manualReview = acct.manualReview,
                        errorMessage = acct.errorMessage,
                        repliedExperts = acct.repliedExperts.map { expert ->
                            PollRepliedExpert(
                                expertContactId = expert.expertContactId,
                                expertEmail = expert.expertEmail,
                                expertName = expert.expertName,
                                outcome = expert.outcome
                            )
                        }
                    )
                },
                error = null
            )
        } catch (e: Exception) {
            log.warn("Failed to parse poll detail for execution {}: {}", id, e.message)
            PollDetailResponse(id = exec.id, accounts = emptyList(), error = "无法解析轮询详情")
        }
    }

    @GetMapping("/{id}")
    fun getExecution(@PathVariable id: Long): TaskExecutionResponse =
        service.getExecution(id).toResponse()

    private fun tryParseResultSummary(json: String): PollResultSummary? =
        try {
            objectMapper.readValue(json)
        } catch (e: Exception) {
            log.warn("Failed to parse resultSummary as PollResultSummary: {}", e.message)
            null
        }

    /**
     * I1-6：原始 JSON 兜底返回前按 32 KB 截断，截断时置 rawTruncated = true
     * （AUTO_REPLY_ALL 的 result_summary 内嵌 accounts[].repliedExperts[]，
     * 无限长兜底会把 P0 刚消除的传输问题在明细路径上重新引入）。
     */
    private fun truncateRaw(raw: String?): Pair<String?, Boolean> {
        if (raw == null) return null to false
        return if (raw.length > MAX_RAW_LENGTH) raw.take(MAX_RAW_LENGTH) to true else raw to false
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /** I1-6：明细兜底原始 JSON 的截断上限（32 KB）。 */
        private const val MAX_RAW_LENGTH = 32 * 1024

        private fun firstPositive(vararg values: Int?): Int =
            values.firstOrNull { it != null && it > 0 } ?: 0
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PollResultSummary(
    val totalAccountsToPoll: Int = 0,
    val accountsPolled: Int = 0,
    val totalExpertsToCheck: Int = 0,
    val expertsChecked: Int = 0,
    val expertsWithReply: List<String> = emptyList(),
    val accountCount: Int = 0,
    val fetched: Int = 0,
    val recorded: Int = 0,
    val replied: Int = 0,
    val manualReview: Int = 0
)

data class PollLogResponse(
    val id: Long?,
    val triggerType: String,
    val status: String,
    val totalAccountsToPoll: Int,
    val accountsPolled: Int,
    val totalFetched: Int,
    val totalRecorded: Int,
    val totalReplied: Int,
    val totalManualReview: Int,
    val expertsWithReply: List<String>,
    val startedAt: String,
    val finishedAt: String?,
    val durationSeconds: Long?,
    val nextPollAt: String?,
    val isManualTrigger: Boolean
)

data class TaskExecutionResponse(
    val id: Long?,
    val taskType: String,
    val triggerType: String,
    val status: String,
    val requestPayload: String?,
    val resultSummary: String?,
    val successCount: Int,
    val failureCount: Int,
    val errorMessage: String?,
    val startedAt: String,
    val finishedAt: String?,
    val createdAt: String?,
    val updatedAt: String?
)

/**
 * 列表响应投影（M-1）：刻意不含 requestPayload / resultSummary —— 两个 TEXT 列
 * 只由单行详情端点 `/{id}`（TaskExecutionResponse）返回。时间格式沿用
 * `startedAt.toString()` 语义以满足 N0-1。
 *
 * B2（T1-4 自我修正）：只加 `taskTypeLabel` + `metricLabel` 两个字段；
 * `summaryText` 刻意不加（列表页不读 TEXT 列，摘要只在展开明细时出现）。
 */
data class TaskExecutionListItemResponse(
    val id: Long?,
    val taskType: String,
    val taskTypeLabel: String,
    val triggerType: String,
    val status: String,
    val successCount: Int,
    val failureCount: Int,
    val metricLabel: String?,
    val errorMessage: String?,
    val startedAt: String,
    val finishedAt: String?
)

/** I1-7：类型下拉选项（label 已做 catalog 左连接兜底）。 */
data class TaskTypeOption(
    val code: String,
    val label: String,
    val count: Long
)

/** I1-3/I1-5/I1-6：通用明细响应。 */
data class TaskExecutionDetailResponse(
    val id: Long?,
    val taskType: String,
    val taskTypeLabel: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String?,
    val durationSeconds: Long?,
    val totals: ExecutionTotals,
    val metricLabel: String?,
    val rawRequestPayload: String?,
    val rawResultSummary: String?,
    val rawTruncated: Boolean
)

data class TaskExecutionPageResponse(
    val items: List<TaskExecutionListItemResponse>,
    val total: Long
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PollDetailRaw(
    val accounts: List<PollDetailAccountRaw> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PollDetailAccountRaw(
    val accountCode: String = "",
    val status: String = "",
    val fetched: Int = 0,
    val recorded: Int = 0,
    val replied: Int = 0,
    val manualReview: Int = 0,
    val errorMessage: String? = null,
    val repliedExperts: List<PollDetailExpertRaw> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class PollDetailExpertRaw(
    val expertContactId: Long? = null,
    val expertEmail: String? = null,
    val expertName: String? = null,
    val outcome: String = ""
)

data class PollDetailResponse(
    val id: Long?,
    val accounts: List<PollAccountDetail>,
    val error: String?
)

data class PollAccountDetail(
    val accountCode: String,
    val status: String,
    val fetched: Int,
    val recorded: Int,
    val replied: Int,
    val manualReview: Int,
    val errorMessage: String?,
    val repliedExperts: List<PollRepliedExpert>
)

data class PollRepliedExpert(
    val expertContactId: Long?,
    val expertEmail: String?,
    val expertName: String?,
    val outcome: String
)

private fun TaskExecutionListItem.toListResponse(): TaskExecutionListItemResponse =
    TaskExecutionListItemResponse(
        id = id,
        taskType = taskType,
        taskTypeLabel = TaskTypeCatalog.byCode(taskType)?.label ?: taskType,
        triggerType = triggerType,
        status = status,
        successCount = successCount,
        failureCount = failureCount,
        metricLabel = TaskTypeCatalog.byCode(taskType)?.metricLabel,
        errorMessage = errorMessage,
        startedAt = startedAt.toString(),
        finishedAt = finishedAt?.toString()
    )

private fun TaskExecution.toResponse(): TaskExecutionResponse =
    TaskExecutionResponse(
        id = id,
        taskType = taskType,
        triggerType = triggerType,
        status = status,
        requestPayload = requestPayload,
        resultSummary = resultSummary,
        successCount = successCount,
        failureCount = failureCount,
        errorMessage = errorMessage,
        startedAt = startedAt.toString(),
        finishedAt = finishedAt?.toString(),
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString()
    )
