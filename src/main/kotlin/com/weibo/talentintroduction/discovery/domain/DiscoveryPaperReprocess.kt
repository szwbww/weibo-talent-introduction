package com.weibo.talentintroduction.discovery.domain

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** This audit store is independent of archive first results and of expert/admission storage. */
enum class ReprocessMode { AUDIT, ENFORCE }
enum class ReprocessState { PREPARING, RUNNING, CANCELLING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, INTERRUPTED, CANCELLED }
enum class PaperReprocessPhase { FREEZE, READ, PARSE, BIND, APPLY, REVIEW, SAVE }
enum class ReprocessKind { PAPER, TARGET }
enum class ReprocessItemState { PENDING, RUNNING, COMPLETE, ERROR, SKIPPED, CANCELLED }
enum class ReprocessTraceState { NOT_STARTED, RUNNING, SUCCESS, INSUFFICIENT, SKIPPED, ERROR, CANCELLED }

class ReprocessException(val code: String, val status: Int, message: String = when (code) {
    "SOURCE_NOT_FOUND" -> "来源采集任务不存在"
    "SOURCE_RUNNING" -> "来源采集任务尚未结束，暂不能重新解析"
    "RUN_NOT_FOUND" -> "重新解析任务不存在"
    "REVISION_CONFLICT" -> "任务已更新，请刷新后重试"
    "RUN_NOT_ACTIVE" -> "任务已结束，不能取消"
    "RETRY_NOT_ALLOWED" -> "当前任务状态不允许重试"
    "VERSION_CHANGED" -> "解析版本已变化，请新建重新解析任务"
    "SCOPE_CHANGED" -> "来源范围已变化，请重新预览并创建任务"
    "REQUEST_KEY_CONFLICT" -> "请求标识已用于不同参数"
    "ACTIVE_RUN_EXISTS" -> "已有重新解析任务正在运行"
    "LEASE_LOST", "TASK_OWNERSHIP_LOST" -> "任务执行权已失效，请刷新查看最新状态"
    "PAPERS_NOT_PARSED" -> "论文尚未全部解析完成"
    "IMMUTABLE_INPUT_CONFLICT" -> "工作项来源与已固定的输入不一致"
    "SCOPE_NOT_FROZEN" -> "来源范围尚未固定"
    "ITEMS_NOT_FINISHED" -> "工作项尚未全部处理完成"
    "CANCEL_REQUESTED" -> "任务已请求取消"
    "ITEM_ERRORS" -> "工作项存在错误，请查看处理详情"
    "INPUT_TOO_LARGE" -> "工作项输入超过存储上限"
    "RESULT_TOO_LARGE" -> "解析结果超过存储上限"
    "INVALID_SCHEMA", "INVALID_TRACE", "INVALID_STATE" -> "任务记录格式或状态不受支持，原始记录已保留"
    else -> "重新解析请求无法完成，请查看错误代码"
}) : IllegalStateException(message)
data class CreateReprocessRequest(val requestKey: String, val sourceExecutionId: Long, val mode: ReprocessMode,
    val expectedScopeHash: String)
data class RevisionReprocessRequest(val revision: Long)
data class ScopeWatermark(val sourceExecutionId: Long, val sourceState: String, val upperRecordId: Long,
    val maxAttemptId: Long, val recordCount: Long, val revisionSum: Long, val attemptCount: Long,
    val attemptRevisionSum: String, val scopeHash: String)
data class ReprocessRun(val executionId: Long, val sourceExecutionId: Long, val requestKey: String,
    val requestHash: String, val mode: ReprocessMode, val state: ReprocessState, val phase: PaperReprocessPhase,
    val scope: JsonNode, val revision: Long, val leaseToken: String?, val leaseUntil: Instant?, val generation: Long,
    val cancelRequested: Boolean, val createdAt: Instant, val updatedAt: Instant, val finishedAt: Instant?)
data class ReprocessItem(val id: Long, val executionId: Long, val kind: ReprocessKind, val itemKey: String,
    val state: ReprocessItemState, val input: JsonNode, val result: JsonNode?, val leaseToken: String?,
    val leaseUntil: Instant?, val generation: Long, val revision: Long)
data class ReprocessPage<T>(val items: List<T>, val total: Long, val page: Int, val size: Int)
/** Qualification counts use TARGET units, not papers or unique people. Unavailable is not UNKNOWN. */
data class ReprocessQualificationSummary(val met: Long = 0, val unknown: Long = 0, val notMet: Long = 0,
    val conflict: Long = 0, val notEvaluated: Long = 0, val unavailable: Long = 0)
/**
 * Counts cover all persisted items, never the requested detail page. Categories may overlap (for
 * example a repaired target can be admitted with projection failure). totalKnown means the PAPER
 * manifest is frozen; targetTotalKnown means TARGET enumeration is also complete. Otherwise counts
 * are known-so-far, not a final total. Null proposal/unchanged/protected counts mean not evaluated.
 */
data class ReprocessSummary(val papers: Long, val paperComplete: Long, val paperErrors: Long,
    val paperCancelled: Long, val targets: Long, val targetComplete: Long, val targetErrors: Long,
    val actualNew: Int = 0, val actualUpdated: Int = 0, val actualAdmitted: Int = 0,
    val pendingVerification: Long = 0, val wouldCreate: Long? = null, val wouldRepair: Long? = null,
    val unchanged: Long? = null, val conflict: Long = 0, val protected: Long? = null, val syncFailure: Long = 0,
    val paperSkipped: Long = 0, val missingSourcePapers: Long = 0, val qualification: ReprocessQualificationSummary = ReprocessQualificationSummary(),
    val totalKnown: Boolean = false, val targetTotalKnown: Boolean = false,
    val paperUnit: String = "PAPER", val targetUnit: String = "TARGET", val categoriesOverlap: Boolean = true)
data class ReprocessPreview(val sourceExecutionId: Long, val recordCount: Long, val expectedScopeHash: String,
    val activeRun: ReprocessRun?, val missingSourcePapers: Long = 0)
data class ReprocessDetail(val run: ReprocessRun, val summary: ReprocessSummary,
    val progressText: String, val totalKnown: Boolean)
