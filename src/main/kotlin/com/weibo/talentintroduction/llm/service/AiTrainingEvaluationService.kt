package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

enum class AiTrainingEvaluationRating {
    MEETS_EXPECTATION,
    NEEDS_IMPROVEMENT,
    UNUSABLE
}

data class AiTrainingEvaluationRequest(
    val assembly: TrustReplyAssembleRequest,
    val rating: String?,
    val note: String? = null,
    val operatorName: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AiTrainingEvaluationResponse(
    val evaluationId: Long,
    val rating: String,
    val createdAt: String?,
    val unsupportedAnswerArchiveStatus: UnsupportedAnswerArchiveStatus? = null,
    val unsupportedAnswerArchivedCount: Int? = null,
    val unsupportedAnswerArchiveFailedCount: Int? = null
)

@Service
class AiTrainingEvaluationService(
    private val workbenchService: TrustReplyWorkbenchService,
    private val operatorActionLogService: OperatorActionLogService,
    private val unsupportedAnswerIndexService: UnsupportedAnswerIndexService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val SNAPSHOT_SCHEMA_VERSION = "ai-training-reply-evaluation-v1"
        private const val MAX_NOTE_LENGTH = 1000
        private const val MAX_OPERATOR_NAME_LENGTH = 128
        private const val MAX_ITEM_SNAPSHOTS = 50
        private const val MAX_SNAPSHOT_STRING_LENGTH = 200
        private const val MAX_MODEL_LENGTH = 64
        private const val MAX_MODELS = 5
    }

    fun save(request: AiTrainingEvaluationRequest): AiTrainingEvaluationResponse {
        val source = request.assembly.source
        if (source.sourceType != TrustReplySourceType.TRAINING_MAIL) {
            throw invalid("TRUST_REPLY_TRAINING_SOURCE_REQUIRED")
        }

        val rating = parseRating(request.rating)
        val note = normalizeNote(request.note)
        val operatorName = normalizeOperatorName(request.operatorName)

        // Resolve only after input validation. assemble performs the authoritative
        // source, evidence, request, version, and handling revalidation.
        val assembled = workbenchService.assemble(request.assembly)
        val resolved = workbenchService.resolveSource(source)
        if (assembled.source != source || assembled.sourceVersion != resolved.sourceVersion) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE")
        }
        val contactId = requireNotNull(resolved.contact.id) {
            "Training source contact id is required"
        }

        val snapshot = buildSnapshot(assembled, rating)
        val evaluationLog = operatorActionLogService.record(
            targetType = "MAIL_RECORD",
            targetId = source.sourceId,
            actionType = OperatorActionType.AI_TRAINING_REPLY_EVALUATED,
            expertContactId = contactId,
            inboundProcessingId = null,
            after = snapshot,
            operatorName = operatorName,
            note = note
        )

        val evaluationId = requireNotNull(evaluationLog.id) { "Persisted evaluation must have an id" }
        val eligibleVersions = if (rating == AiTrainingEvaluationRating.MEETS_EXPECTATION) {
            assembled.itemVersions.filter { item ->
                item.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT &&
                    item.generationKind == TrustReplyItemGenerationKind.AI_GENERATED &&
                    item.requestText.isNotBlank() &&
                    item.operatorInstruction.isNotBlank() &&
                    item.answerText.isNotBlank()
            }
        } else {
            emptyList()
        }
        val archive = if (eligibleVersions.isEmpty()) {
            UnsupportedAnswerIndexArchiveResult()
        } else {
            try {
                unsupportedAnswerIndexService.archiveCanonicalVersions(
                    source = resolved,
                    versions = eligibleVersions,
                    qualificationId = evaluationId.toString(),
                    approvedBy = operatorName,
                    createdAt = evaluationLog.createdAt
                        ?.atZone(ZoneId.systemDefault())
                        ?.toInstant()
                        ?: Instant.now()
                )
            } catch (error: Exception) {
                log.warn("Unsupported answer archive failed after evaluation {}: {}", evaluationId, error.javaClass.simpleName)
                UnsupportedAnswerIndexArchiveResult(
                    status = UnsupportedAnswerArchiveStatus.FAILED,
                    failedCount = eligibleVersions.size
                )
            }
        }

        return AiTrainingEvaluationResponse(
            evaluationId = evaluationId,
            rating = rating.name,
            createdAt = evaluationLog.createdAt?.toString(),
            unsupportedAnswerArchiveStatus = archive.status,
            unsupportedAnswerArchivedCount = archive.archivedCount,
            unsupportedAnswerArchiveFailedCount = archive.failedCount
        )
    }

    fun buildSnapshot(
        assembled: TrustReplyAssembleResponse,
        rating: AiTrainingEvaluationRating
    ): Map<String, Any> {
        val allItems = assembled.itemVersions
        val itemSnapshots = allItems.take(MAX_ITEM_SNAPSHOTS).map { item ->
            mapOf(
                "requestKey" to item.requestKey.take(MAX_SNAPSHOT_STRING_LENGTH),
                "handling" to item.handling.name.take(MAX_SNAPSHOT_STRING_LENGTH),
                "versionId" to item.versionId.take(MAX_SNAPSHOT_STRING_LENGTH),
                "answerHash" to AiReplyDraftService.sha256Hex(item.answerText),
                "model" to item.model.take(MAX_SNAPSHOT_STRING_LENGTH),
                "generationKind" to item.generationKind.name.take(MAX_SNAPSHOT_STRING_LENGTH)
            )
        }
        val handlingCounts = TrustReplyItemHandling.values().associate { handling ->
            handling.name to allItems.count { it.handling == handling }
        }
        val models = allItems.asSequence()
            .map { it.model.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_MODELS)
            .map { it.take(MAX_MODEL_LENGTH) }
            .toList()

        return linkedMapOf(
            "schemaVersion" to SNAPSHOT_SCHEMA_VERSION,
            "sourceVersion" to assembled.sourceVersion,
            "draftHash" to AiReplyDraftService.sha256Hex(assembled.rawDraftText),
            "evidenceSetVersion" to assembled.evidenceSetVersion,
            "rating" to rating.name,
            "requestCount" to allItems.size,
            "handlingCounts" to handlingCounts,
            "models" to models,
            "itemSnapshots" to itemSnapshots,
            "itemTotal" to allItems.size,
            "itemTruncated" to (allItems.size > MAX_ITEM_SNAPSHOTS)
        )
    }

    private fun parseRating(value: String?): AiTrainingEvaluationRating =
        runCatching { AiTrainingEvaluationRating.valueOf(value?.trim().orEmpty()) }
            .getOrElse { throw invalid("TRUST_REPLY_EVALUATION_RATING_INVALID") }

    private fun normalizeNote(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.length > MAX_NOTE_LENGTH) {
            throw invalid("TRUST_REPLY_EVALUATION_NOTE_INVALID")
        }
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun normalizeOperatorName(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.length > MAX_OPERATOR_NAME_LENGTH) {
            throw invalid("TRUST_REPLY_EVALUATION_OPERATOR_INVALID")
        }
        return normalized.ifEmpty { "UNKNOWN" }
    }

    private fun invalid(code: String): TrustReplyWorkbenchException =
        TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, code)
}
