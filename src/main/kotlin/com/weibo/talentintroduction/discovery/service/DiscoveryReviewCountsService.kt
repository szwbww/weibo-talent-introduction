package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** Counts are a rolling read of RAW/discovered, not an ES/MySQL transactional snapshot. */
enum class DiscoveryReviewCountsState { PREPARING, RUNNING, SUCCESS, FAILED, INTERRUPTED }

data class DiscoveryReviewCountsAccepted(
    val requestKey: String,
    val executionId: Long?,
    val state: DiscoveryReviewCountsState
)

data class DiscoveryReviewCountsError(val code: String, val causeCategory: String, val message: String)

data class DiscoveryReviewCountsResult(
    val startedAt: String,
    val asOf: String,
    val scanned: Int,
    val uninitialized: Int,
    val decisions: Map<String, Int>,
    val counts: Map<String, Int>
) : TaskExecutionSummaryProvider {
    @get:JsonIgnore
    override val taskSuccessCount: Int get() = scanned
    @get:JsonIgnore
    override val taskFailureCount: Int get() = 0
    @get:JsonIgnore
    override val taskFinalStatus: String get() = "SUCCESS"
}

data class DiscoveryReviewCountsStatus(
    val requestKey: String,
    val executionId: Long?,
    val state: DiscoveryReviewCountsState,
    val scanned: Int,
    val startedAt: String?,
    val result: DiscoveryReviewCountsResult? = null,
    val lastSuccess: DiscoveryReviewCountsResult? = null,
    val error: DiscoveryReviewCountsError? = null,
    val scope: String = "RAW/discovered；仅 initialized=true；滚动统计，非跨 ES/MySQL 事务快照"
)

class DiscoveryReviewCountsBusyException : IllegalStateException("数量统计执行器繁忙，请稍后重试")
class DiscoveryReviewCountsReadException(cause: Exception) : IllegalStateException("数量统计记录读取失败", cause)
class DiscoveryReviewScanIntegrityException(val code: String, message: String) : IllegalStateException(message)

@Service
class DiscoveryReviewCountsService(
    private val scan: DiscoveryReviewScanService,
    private val tasks: TaskExecutionService,
    private val mapper: ObjectMapper,
    @Qualifier("discoveryReviewCountsExecutor") private val executor: Executor
) {
    // Bounded process-local submission bridge only. Completed counts are always read from task_execution.
    private class Submission(val key: String) {
        @Volatile var executionId: Long? = null
        @Volatile var error: DiscoveryReviewCountsError? = null
        @Volatile var finished = false
        @Volatile var terminal: DiscoveryReviewCountsState? = null
        @Volatile var scanned = 0
    }
    @Volatile private var submission: Submission? = null

    @Synchronized
    fun refresh(actor: String): DiscoveryReviewCountsAccepted {
        submission?.takeUnless { it.finished }?.let { return accepted(it) }
        val next = Submission(UUID.randomUUID().toString())
        submission = next
        try {
            executor.execute { run(next, actor) }
        } catch (ex: RejectedExecutionException) {
            submission = null
            throw DiscoveryReviewCountsBusyException()
        }
        return accepted(next)
    }

    private fun accepted(value: Submission) = DiscoveryReviewCountsAccepted(
        value.key, value.executionId,
        when {
            value.error != null -> DiscoveryReviewCountsState.FAILED
            value.finished -> value.terminal ?: DiscoveryReviewCountsState.FAILED
            value.executionId != null -> DiscoveryReviewCountsState.RUNNING
            else -> DiscoveryReviewCountsState.PREPARING
        }
    )

    private fun run(owner: Submission, actor: String) {
        try {
            val completed = tasks.runAndRecordWithResult(
                TASK_TYPE, "MANUAL", mapOf("requestKey" to owner.key, "actor" to actor),
                onStarted = { owner.executionId = it }
            ) {
                try {
                    val id = requireNotNull(owner.executionId)
                    val startedAt = tasks.getExecution(id).startedAt.toString()
                    var scanned = 0
                    var uninitialized = 0
                    val decisions = DECISIONS.associateWith { 0 }.toMutableMap()
                    scan.scanAll(
                        DiscoveryReviewScanService.DiscoveryReviewFilter(ExpertIndexLevel.RAW, "discovered", null, null, null),
                        500
                    ) { batch ->
                        checkOwner(owner, id)
                        for (expert in batch) {
                            if (!expert.row.initialized) uninitialized++
                            else {
                                val decision = expert.row.decision
                                val count = decisions[decision]
                                    ?: throw DiscoveryReviewScanIntegrityException("REVIEW_SCAN_DECISION_UNKNOWN", "未知准入结论：$decision")
                                decisions[decision] = count + 1
                            }
                        }
                        scanned += batch.size
                        owner.scanned = scanned
                        tasks.updateProgressCounts(id, scanned, 0)
                        true
                    }
                    checkOwner(owner, id)
                    DiscoveryReviewCountsResult(
                        startedAt, LocalDateTime.now().toString(), scanned, uninitialized, decisions,
                        mapOf(
                            "autoPassed" to decisions.getValue("AUTO_PASSED"),
                            "needsReview" to decisions.getValue("NEEDS_REVIEW"),
                            "manualApproved" to decisions.getValue("MANUAL_APPROVED") + decisions.getValue("LEGACY_APPROVED"),
                            "held" to decisions.getValue("HOLD") + decisions.getValue("REJECTED")
                        )
                    )
                } catch (ex: Exception) {
                    val error = errorOf(ex)
                    owner.error = error
                    // TaskExecutionService persists this stable code/category without losing the original cause.
                    throw IllegalStateException("${error.code}|${error.causeCategory}|${error.message}", ex)
                }
            }
            owner.terminal = DiscoveryReviewCountsState.valueOf(completed.first.status)
        } catch (ex: Exception) {
            if (owner.error == null) owner.error = errorOf(ex)
            owner.executionId?.let { id ->
                try {
                    tasks.updateProgressCounts(id, owner.scanned, 1)
                } catch (_: DataAccessException) {
                    // Existing task recovery owns a stranded RUNNING row when the database is unavailable.
                }
            }
        } finally {
            owner.finished = true
        }
    }

    private fun checkOwner(owner: Submission, id: Long) {
        if (submission !== owner || Thread.currentThread().isInterrupted || tasks.getExecution(id).status != "RUNNING") {
            throw DiscoveryReviewScanIntegrityException("COUNTS_INTERRUPTED", "数量统计执行已中断")
        }
    }

    /** GET performs no submission or business writes, including after process restart. */
    fun status(requestKey: String): DiscoveryReviewCountsStatus {
        require(runCatching { UUID.fromString(requestKey).toString() == requestKey }.getOrDefault(false)) { "requestKey 必须为 UUID" }
        try {
            val recent = tasks.listRecentByTaskType(TASK_TYPE, 200)
            val local = submission?.takeIf { it.key == requestKey }
            val task = local?.executionId?.let { tasks.getExecution(it) }
                ?: recent.firstOrNull { keyOf(it) == requestKey }
            val previous = recent.firstOrNull { it.status == "SUCCESS" }?.let { resultOf(it) }
            if (task == null && local == null) throw NoSuchElementException("数量统计请求不存在：$requestKey")
            val state = when {
                task?.status == "INTERRUPTED" -> DiscoveryReviewCountsState.INTERRUPTED
                local?.error != null -> DiscoveryReviewCountsState.FAILED
                task != null -> DiscoveryReviewCountsState.valueOf(task.status)
                else -> DiscoveryReviewCountsState.PREPARING
            }
            return DiscoveryReviewCountsStatus(
                requestKey, task?.id, state, task?.successCount ?: 0, task?.startedAt?.toString(),
                result = if (state == DiscoveryReviewCountsState.SUCCESS) task?.let { resultOf(it) } else null,
                lastSuccess = previous,
                error = when (state) {
                    DiscoveryReviewCountsState.INTERRUPTED -> DiscoveryReviewCountsError("COUNTS_INTERRUPTED", "INTERRUPTED", task?.errorMessage ?: "进程中断，需重新刷新")
                    DiscoveryReviewCountsState.FAILED -> local?.error ?: persistedError(task?.errorMessage)
                    else -> null
                }
            )
        } catch (ex: DataAccessException) {
            throw DiscoveryReviewCountsReadException(ex)
        }
    }

    private fun keyOf(task: TaskExecution): String? = task.requestPayload?.let { mapper.readTree(it).path("requestKey").asText() }
    private fun resultOf(task: TaskExecution): DiscoveryReviewCountsResult =
        mapper.readValue(requireNotNull(task.resultSummary), DiscoveryReviewCountsResult::class.java)

    private fun persistedError(message: String?): DiscoveryReviewCountsError {
        val parts = message?.split('|', limit = 3).orEmpty()
        return if (parts.size == 3) DiscoveryReviewCountsError(parts[0], parts[1], parts[2])
        else DiscoveryReviewCountsError("REVIEW_SCAN_FAILED", "UNKNOWN", message ?: "数量统计失败")
    }

    private fun errorOf(ex: Exception): DiscoveryReviewCountsError = DiscoveryReviewCountsError(
        when (ex) {
            is DiscoveryReviewScanIntegrityException -> ex.code
            is DataAccessException -> "COUNTS_DATABASE_FAILED"
            else -> "REVIEW_SCAN_FAILED"
        },
        when (ex) {
            is HttpStatusCodeException -> "HTTP_${ex.statusCode.value()}"
            is DataAccessException -> "DATABASE"
            else -> ex.javaClass.simpleName
        },
        ex.message?.take(1000) ?: "数量统计失败"
    )

    companion object {
        const val TASK_TYPE = "DISCOVERY_REVIEW_COUNTS"
        private val DECISIONS = listOf("AUTO_PASSED", "NEEDS_REVIEW", "MANUAL_APPROVED", "LEGACY_APPROVED", "HOLD", "REJECTED")
    }
}
