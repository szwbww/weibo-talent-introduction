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

class ReprocessException(val code: String, val status: Int, message: String = code) : IllegalStateException(message)
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
data class ReprocessSummary(val papers: Long, val paperComplete: Long, val paperErrors: Long,
    val paperCancelled: Long, val targets: Long, val targetComplete: Long, val targetErrors: Long,
    val actualNew: Int = 0, val actualUpdated: Int = 0, val actualAdmitted: Int = 0)
data class ReprocessPreview(val sourceExecutionId: Long, val recordCount: Long, val expectedScopeHash: String,
    val activeRun: ReprocessRun?)
data class ReprocessDetail(val run: ReprocessRun, val summary: ReprocessSummary,
    val progressText: String, val totalKnown: Boolean)
