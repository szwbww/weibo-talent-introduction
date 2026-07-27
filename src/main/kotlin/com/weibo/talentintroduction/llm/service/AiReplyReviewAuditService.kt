package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

const val TARGET_TYPE_INBOUND_MAIL_PROCESSING = "INBOUND_MAIL_PROCESSING"

data class AiReplyAuditSnapshot(
    val schemaVersion: String,
    val observedAt: String,
    val draftHash: String,
    val model: String,
    val mode: String,
    val promptVersion: String,
    val evidenceSetVersion: String,
    val evidenceSources: List<Map<String, Any?>>,
    val sourceTotal: Int,
    val sourceTruncated: Boolean,
    val requestCount: Int,
    val groundedRequestCount: Int,
    val requestCoverage: List<Map<String, Any?>>,
    val coverageTotal: Int,
    val coverageTruncated: Boolean,
    val readiness: String,
    val generationState: String,
    val usedLlm: Boolean,
    val warningCodes: List<String>,
    val warningTotal: Int,
    val warningTruncated: Boolean,
    val fewShotRefs: List<String>,
    val fewShotTotal: Int,
    val fewShotTruncated: Boolean,
    val validationDiagnostics: Map<String, Any?>
)

@Service
class AiReplyReviewAuditService(
    private val operatorActionLogService: OperatorActionLogService
) {

    fun buildSnapshot(result: AiReplyDraftResult): AiReplyAuditSnapshot {
        val draftHash = AiReplyDraftService.sha256Hex(result.draftText)
        val observedAt = Instant.now().toString()

        val maxSources = 50
        val allSources = result.evidenceSources
        val sourceTruncated = allSources.size > maxSources
        val evidenceSources = allSources.take(maxSources).map {
            mapOf(
                "ruleId" to it.ruleId,
                "displayName" to it.displayName.take(200),
                "updatedAt" to (it.updatedAt ?: ""),
                "answerBodySha256" to it.answerBodySha256,
                "available" to it.available
            )
        }

        val maxCoverage = 50
        val allCoverage = result.requestFacts
        val coverageTruncated = allCoverage.size > maxCoverage
        val requestCoverage = allCoverage.take(maxCoverage).map { item ->
            mapOf(
                "index" to item.index,
                "status" to item.status.name,
                "evidenceIds" to item.factRuleIds.take(30)
            )
        }

        val maxWarnings = 30
        val allWarnings = result.contextWarnings
        val warningTruncated = allWarnings.size > maxWarnings
        val warningCodes = allWarnings.take(maxWarnings).map { it.take(200) }

        val maxFewShot = 10
        val allFewShot = result.fewShotDialogRefs
        val fewShotTruncated = allFewShot.size > maxFewShot
        val fewShotRefs = allFewShot.take(maxFewShot).map { it.take(200) }
        val validationDiagnostics = mapOf(
            "items" to result.validationDiagnostics.items.take(AiReplyValidationDiagnostics.MAX_ITEMS).map {
                mapOf(
                    "attempt" to it.attempt.name,
                    "stage" to it.stage.name,
                    "code" to it.code.take(200),
                    "claimKey" to it.claimKey?.take(AiReplyValidationDiagnostics.MAX_CLAIM_KEY_LENGTH)
                )
            },
            "total" to result.validationDiagnostics.total,
            "truncated" to result.validationDiagnostics.truncated
        )

        return AiReplyAuditSnapshot(
            schemaVersion = AI_REPLY_DRAFT_AUDIT_SCHEMA_VERSION,
            observedAt = observedAt,
            draftHash = draftHash,
            model = result.selectedModel,
            mode = result.mode.name,
            promptVersion = result.promptVersion,
            evidenceSetVersion = result.evidenceSetVersion,
            evidenceSources = evidenceSources,
            sourceTotal = allSources.size,
            sourceTruncated = sourceTruncated,
            requestCount = result.requestCount,
            groundedRequestCount = result.groundedRequestCount,
            requestCoverage = requestCoverage,
            coverageTotal = allCoverage.size,
            coverageTruncated = coverageTruncated,
            readiness = result.draftReadiness.name,
            generationState = result.generationState.name,
            usedLlm = result.usedLlm,
            warningCodes = warningCodes,
            warningTotal = allWarnings.size,
            warningTruncated = warningTruncated,
            fewShotRefs = fewShotRefs,
            fewShotTotal = allFewShot.size,
            fewShotTruncated = fewShotTruncated,
            validationDiagnostics = validationDiagnostics
        )
    }

    fun recordInitialDraft(
        inboundProcessingId: Long,
        contactId: Long,
        result: AiReplyDraftResult,
        operatorName: String?
    ): AiReplyAuditSnapshot {
        val snapshot = buildSnapshot(result)
        try {
            val actionType = when (result.draftReadiness) {
                AiReplyDraftReadiness.READY -> OperatorActionType.AI_REPLY_DRAFT_READY
                AiReplyDraftReadiness.NEEDS_REVIEW -> OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW
                AiReplyDraftReadiness.BLOCKED -> OperatorActionType.AI_REPLY_DRAFT_BLOCKED
            }

            val afterMap = mapOf(
                "schemaVersion" to snapshot.schemaVersion,
                "observedAt" to snapshot.observedAt,
                "draftHash" to snapshot.draftHash,
                "model" to snapshot.model,
                "mode" to snapshot.mode,
                "promptVersion" to snapshot.promptVersion,
                "evidenceSetVersion" to snapshot.evidenceSetVersion,
                "evidenceSources" to snapshot.evidenceSources,
                "sourceTotal" to snapshot.sourceTotal,
                "sourceTruncated" to snapshot.sourceTruncated,
                "requestCount" to snapshot.requestCount,
                "groundedRequestCount" to snapshot.groundedRequestCount,
                "requestCoverage" to snapshot.requestCoverage,
                "coverageTotal" to snapshot.coverageTotal,
                "coverageTruncated" to snapshot.coverageTruncated,
                "readiness" to snapshot.readiness,
                "generationState" to snapshot.generationState,
                "usedLlm" to snapshot.usedLlm,
                "warningCodes" to snapshot.warningCodes,
                "warningTotal" to snapshot.warningTotal,
                "warningTruncated" to snapshot.warningTruncated,
                "fewShotRefs" to snapshot.fewShotRefs,
                "fewShotTotal" to snapshot.fewShotTotal,
                "fewShotTruncated" to snapshot.fewShotTruncated,
                "validationDiagnostics" to snapshot.validationDiagnostics
            )

            operatorActionLogService.record(
                targetType = TARGET_TYPE_INBOUND_MAIL_PROCESSING,
                targetId = inboundProcessingId,
                actionType = actionType,
                expertContactId = contactId,
                inboundProcessingId = inboundProcessingId,
                after = afterMap,
                operatorName = operatorName,
                note = "AI reply draft generated for inbound processing $inboundProcessingId"
            )
        } catch (ex: Exception) {
            logger.warn("Failed to record AI reply draft audit for inboundProcessingId={}: {}", inboundProcessingId, ex.message)
        }
        return snapshot
    }

    private val logger = LoggerFactory.getLogger(AiReplyReviewAuditService::class.java)

    companion object {
        const val AI_REPLY_DRAFT_AUDIT_SCHEMA_VERSION = "ai-reply-draft-audit-v2"
    }
}
