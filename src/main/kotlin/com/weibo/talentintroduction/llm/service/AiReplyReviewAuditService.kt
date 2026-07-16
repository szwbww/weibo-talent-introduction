package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.domain.OperatorActionType
import com.weibo.talentintroduction.audit.repository.OperatorActionLogRepository
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

const val TARGET_TYPE_INBOUND_MAIL_PROCESSING = "INBOUND_MAIL_PROCESSING"

private const val MAX_SEND_BLOCKED_ITEMS = 100
private const val MAX_SEND_BLOCKED_KEY_LENGTH = 200

data class AiReplyReviewItem(
    val reviewKey: String,
    val requestIndex: Int,
    val intentKey: String,
    val status: String,
    val missingEvidenceKeys: List<String>
)

data class AiReviewConfirmation(
    val draftIdentity: String? = null,
    val confirmedReviewKeys: List<String> = emptyList(),
    val operatorNote: String = ""
)

data class InitialDraftAuthorityResult(
    val available: Boolean,
    val draftIdentity: String?
)

sealed class AiReplySendAuthorityResult {
    object MANUAL : AiReplySendAuthorityResult()
    object AI_READY : AiReplySendAuthorityResult()
    data class AI_REVIEW_CONFIRMED(
        val draftIdentity: String,
        val confirmedReviewKeys: List<String>,
        val operatorNote: String
    ) : AiReplySendAuthorityResult()
}

@Service
class AiReplyReviewAuditService(
    private val operatorActionLogService: OperatorActionLogService,
    private val operatorActionLogRepository: OperatorActionLogRepository
) {

    companion object {
        private const val MIN_NOTE_LENGTH_BLOCKED = 5
        const val WARNING_AI_REPLY_AUDIT_UNAVAILABLE = "AI_REPLY_AUDIT_UNAVAILABLE"
    }

    private val objectMapper = jacksonObjectMapper()

    fun recordInitialDraft(
        inboundProcessingId: Long,
        contactId: Long,
        result: AiReplyDraftResult,
        operatorName: String?
    ): InitialDraftAuthorityResult {
        return try {
            val actionType = when (result.draftReadiness) {
                AiReplyDraftReadiness.READY -> OperatorActionType.AI_REPLY_DRAFT_READY
                AiReplyDraftReadiness.NEEDS_REVIEW -> OperatorActionType.AI_REPLY_DRAFT_NEEDS_REVIEW
                AiReplyDraftReadiness.BLOCKED -> OperatorActionType.AI_REPLY_DRAFT_BLOCKED
            }

            val draftIdentity = UUID.randomUUID().toString()

            val unresolvedItems = result.requestFacts.flatMap { fact ->
                fact.intents
                    .filter { it.status != "SUPPORTED" }
                    .map { intent ->
                        AiReplyReviewItem(
                            reviewKey = "${fact.index}:${intent.intentKey}",
                            requestIndex = fact.index,
                            intentKey = intent.intentKey,
                            status = intent.status,
                            missingEvidenceKeys = intent.missingEvidenceKeys
                        )
                    }
            }

            val afterMap = mapOf(
                "draftIdentity" to draftIdentity,
                "model" to result.selectedModel,
                "mode" to result.mode.name,
                "requestCount" to result.requestCount,
                "groundedRequestCount" to result.groundedRequestCount,
                "readiness" to result.draftReadiness.name,
                "generationState" to result.generationState.name,
                "unresolvedCount" to unresolvedItems.size,
                "unresolvedSnapshot" to unresolvedItems.map { item ->
                    mapOf(
                        "reviewKey" to item.reviewKey,
                        "requestIndex" to item.requestIndex,
                        "intentKey" to item.intentKey,
                        "status" to item.status,
                        "missingEvidenceKeys" to item.missingEvidenceKeys
                    )
                }
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
            InitialDraftAuthorityResult(available = true, draftIdentity = draftIdentity)
        } catch (ex: Exception) {
            logger.warn("Failed to record AI reply draft audit for inboundProcessingId={}: {}", inboundProcessingId, ex.message)
            InitialDraftAuthorityResult(available = false, draftIdentity = null)
        }
    }

    fun recordSendBlocked(
        inboundProcessingId: Long,
        contactId: Long,
        unresolvedItems: List<AiReplyReviewItem>,
        operatorName: String?
    ) {
        val totalCount = unresolvedItems.size
        val truncated = totalCount > MAX_SEND_BLOCKED_ITEMS
        val limitedItems = if (truncated) {
            unresolvedItems.take(MAX_SEND_BLOCKED_ITEMS)
        } else {
            unresolvedItems
        }
        val sanitizedKeys = limitedItems
            .map { it.reviewKey.take(MAX_SEND_BLOCKED_KEY_LENGTH) }

        val afterMap = mutableMapOf<String, Any?>(
            "unresolvedCount" to totalCount,
            "unresolvedKeys" to sanitizedKeys
        )
        if (truncated) {
            afterMap["truncated"] = true
        }

        operatorActionLogService.record(
            targetType = TARGET_TYPE_INBOUND_MAIL_PROCESSING,
            targetId = inboundProcessingId,
            actionType = OperatorActionType.AI_REPLY_SEND_BLOCKED,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            after = afterMap,
            operatorName = operatorName,
            note = "AI reply send blocked for inbound processing $inboundProcessingId"
        )
    }

    fun validateConfirmationForSend(
        inboundProcessingId: Long,
        replySource: String?,
        confirmation: AiReviewConfirmation?
    ): AiReplySendAuthorityResult {
        val latestDraft = operatorActionLogRepository.findLatestAiDraftByInboundProcessingId(inboundProcessingId)

        if (latestDraft == null) {
            if (replySource.isNullOrBlank() && confirmation == null) {
                return AiReplySendAuthorityResult.MANUAL
            }
            throw IllegalArgumentException(
                "No AI draft record exists for inbound $inboundProcessingId but replySource or confirmation was provided"
            )
        }

        val afterJson = latestDraft.afterValue
            ?: throw IllegalArgumentException("AI draft audit record for inbound $inboundProcessingId has no afterValue")

        val afterMap: Map<String, Any?> = try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(afterJson, Map::class.java) as Map<String, Any?>
        } catch (ex: Exception) {
            throw IllegalArgumentException("AI draft audit record for inbound $inboundProcessingId afterValue is not valid JSON", ex)
        }

        val readiness = afterMap["readiness"] as? String
            ?: throw IllegalArgumentException("AI draft audit record for inbound $inboundProcessingId missing readiness field")

        val expectedReadiness = when (latestDraft.actionType) {
            "AI_REPLY_DRAFT_READY" -> "READY"
            "AI_REPLY_DRAFT_NEEDS_REVIEW" -> "NEEDS_REVIEW"
            "AI_REPLY_DRAFT_BLOCKED" -> "BLOCKED"
            else -> throw IllegalArgumentException(
                "AI draft audit record for inbound $inboundProcessingId has unexpected action_type: ${latestDraft.actionType}"
            )
        }
        require(readiness == expectedReadiness) {
            "AI draft audit record for inbound $inboundProcessingId readiness '$readiness' does not match action_type '${latestDraft.actionType}' (expected '$expectedReadiness')"
        }

        val storedIdentity = afterMap["draftIdentity"] as? String
        if (storedIdentity.isNullOrBlank()) {
            throw IllegalArgumentException("AI draft for inbound $inboundProcessingId has no draftIdentity — corrupt record")
        }

        @Suppress("UNCHECKED_CAST")
        val rawSnapshot = afterMap["unresolvedSnapshot"] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("AI draft audit record for inbound $inboundProcessingId missing unresolvedSnapshot")

        val canonicalKeys = validateSnapshot(inboundProcessingId, rawSnapshot)

        if (readiness != "READY") {
            require(canonicalKeys.isNotEmpty()) {
                "Unresolved snapshot must not be empty for non-READY draft"
            }
        }

        val unresolvedCount = afterMap["unresolvedCount"]
        if (unresolvedCount is Number && unresolvedCount.toInt() != rawSnapshot.size) {
            throw IllegalArgumentException(
                "AI draft audit record for inbound $inboundProcessingId unresolvedCount (${unresolvedCount.toInt()}) does not match snapshot size (${rawSnapshot.size})"
            )
        }

        if (readiness == "READY") {
            val draftIdentity = confirmation?.draftIdentity
            if (!draftIdentity.isNullOrBlank()) {
                require(draftIdentity == storedIdentity) {
                    "draftIdentity does not match current AI draft for inbound $inboundProcessingId"
                }
            }
            return AiReplySendAuthorityResult.AI_READY
        }

        val draftIdentity = confirmation?.draftIdentity
        if (draftIdentity.isNullOrBlank()) {
            throw IllegalArgumentException("AI draft for inbound $inboundProcessingId is $readiness — must provide draftIdentity to confirm")
        }

        require(draftIdentity == storedIdentity) {
            "draftIdentity does not match current AI draft for inbound $inboundProcessingId"
        }

        val confirmedReviewKeys = confirmation.confirmedReviewKeys

        val duplicateConfirmed = confirmedReviewKeys
            .groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        require(duplicateConfirmed.isEmpty()) {
            "Duplicate review keys in confirmation: $duplicateConfirmed"
        }

        val extraKeys = confirmedReviewKeys.toSet() - canonicalKeys.toSet()
        require(extraKeys.isEmpty()) {
            "Unknown review keys in confirmation: $extraKeys"
        }

        val missingKeys = canonicalKeys.toSet() - confirmedReviewKeys.toSet()
        require(missingKeys.isEmpty()) {
            "Missing review keys need confirmation: $missingKeys"
        }

        if (readiness == "BLOCKED") {
            val note = (confirmation.operatorNote).trim()
            require(note.length >= MIN_NOTE_LENGTH_BLOCKED) {
                "Operator note must be at least $MIN_NOTE_LENGTH_BLOCKED characters for BLOCKED draft (got ${note.length})"
            }
        }

        return AiReplySendAuthorityResult.AI_REVIEW_CONFIRMED(
            draftIdentity = draftIdentity,
            confirmedReviewKeys = confirmedReviewKeys,
            operatorNote = confirmation.operatorNote
        )
    }

    private fun validateSnapshot(
        inboundProcessingId: Long,
        rawSnapshot: List<Map<String, Any?>>
    ): List<String> {
        val canonicalKeys = rawSnapshot.map { entry ->
            val key = entry["reviewKey"] as? String
                ?: throw IllegalArgumentException("AI draft audit record for inbound $inboundProcessingId unresolvedSnapshot item missing reviewKey")
            val idx = entry["requestIndex"] as? Int
            val ik = entry["intentKey"] as? String
            val expectedKey = if (idx != null && ik != null) "$idx:$ik" else null
            if (expectedKey != null && key != expectedKey) {
                throw IllegalArgumentException(
                    "AI draft audit record for inbound $inboundProcessingId unresolved reviewKey '$key' does not match index-colon-intentKey format ($idx:$ik)"
                )
            }
            key
        }

        val seen = linkedSetOf<String>()
        val duplicates = canonicalKeys.filter { !seen.add(it) }
        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException(
                "AI draft audit record for inbound $inboundProcessingId unresolvedSnapshot contains duplicate reviewKeys: $duplicates"
            )
        }
        return canonicalKeys
    }

    fun recordConfirmed(
        inboundProcessingId: Long,
        contactId: Long,
        mailRecordId: Long,
        confirmation: AiReviewConfirmation?,
        operatorName: String?
    ) {
        val afterMap = mutableMapOf<String, Any?>(
            "mailRecordId" to mailRecordId,
            "replySource" to "AI_DRAFT"
        )
        if (confirmation != null) {
            afterMap["draftIdentity"] = confirmation.draftIdentity
            afterMap["confirmedReviewKeys"] = confirmation.confirmedReviewKeys
            if (confirmation.operatorNote.isNotBlank()) {
                afterMap["operatorNote"] = confirmation.operatorNote
            }
        }

        operatorActionLogService.record(
            targetType = TARGET_TYPE_INBOUND_MAIL_PROCESSING,
            targetId = inboundProcessingId,
            actionType = OperatorActionType.AI_REPLY_REVIEW_CONFIRMED,
            expertContactId = contactId,
            inboundProcessingId = inboundProcessingId,
            after = afterMap,
            operatorName = operatorName,
            note = "AI reply review confirmed for inbound processing $inboundProcessingId"
        )
    }

    private val logger = LoggerFactory.getLogger(AiReplyReviewAuditService::class.java)
}
