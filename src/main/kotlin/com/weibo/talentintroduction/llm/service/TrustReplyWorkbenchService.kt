package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.MailRecord
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaRequestExtractor
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Locale

enum class TrustReplySourceType {
    TRAINING_MAIL,
    LIVE_INBOUND
}

enum class TrustReplyItemHandling {
    ANSWER_WITH_EVIDENCE,
    ANSWER_SUPPORTED_PART,
    ANSWER_FROM_OPERATOR_INPUT,
    ACKNOWLEDGE_PENDING,
    OMIT
}

enum class TrustReplyItemGenerationKind {
    AI_GENERATED,
    SAFE_TEMPLATE,
    OMITTED
}

data class TrustReplySourceRef(
    val sourceType: TrustReplySourceType,
    val sourceId: Long
)

data class ResolvedTrustReplySource(
    val source: TrustReplySourceRef,
    val contact: ExpertContact,
    val inboundText: String,
    val subject: String?,
    val messageId: String?,
    val senderAccountCode: String?,
    val profileText: String,
    val mailHistory: String,
    val contextWarnings: List<String>,
    val researchProfileSufficient: Boolean,
    val sourceVersion: String
)

/**
 * Canonical summary-to-fact matrix entry (I-1). [requestKey] is the canonical
 * request identity computed by [TrustReplyWorkbenchService.requestKey];
 * [factRuleIds] is the ordered list of fact rule ids assigned to that request.
 * A rule id may appear at most once across the whole matrix (I-2).
 */
data class TrustReplyRequestFactSelection(
    val requestKey: String,
    val factRuleIds: List<Long>
)

data class TrustReplyBootstrapRequest(
    val source: TrustReplySourceRef,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelection>? = null
)

data class TrustReplyGenerationRequest(
    val source: TrustReplySourceRef,
    val expectedSourceVersion: String?,
    val turns: List<AiReplyTurn> = emptyList(),
    val qaRuleIds: List<Long>? = null,
    val operatorInstruction: String? = null,
    val operatorName: String? = null,
    val model: String? = null,
    val llmAttemptTimeoutSeconds: Int? = null,
    val llmTotalTimeoutSeconds: Int? = null,
    val operation: String = "FULL_DRAFT",
    val expectedEvidenceSetVersion: String? = null,
    val requestKey: String? = null,
    val handling: TrustReplyItemHandling? = null,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelection>? = null
)

data class TrustReplyRequestCoverage(
    val index: Int,
    val requestText: String,
    val status: String,
    val factRuleIds: List<Long>,
    val intents: List<TrustReplyIntentCoverage> = emptyList(),
    val requestKey: String = "",
    val allowedHandlings: List<String> = emptyList(),
    val recommendedHandling: String = ""
)

data class TrustReplyIntentCoverage(
    val intentKey: String,
    val title: String,
    val status: String,
    val evidenceRuleIds: List<Long>,
    val missingEvidenceKeys: List<String>,
    val requiresResearchContext: Boolean
)

data class TrustReplyBootstrapResponse(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val inboundSubject: String?,
    val inboundText: String,
    val expertName: String?,
    val expertEmail: String,
    val llmEnabled: Boolean,
    val availableModels: List<String>,
    val defaultModel: String,
    val suggestedFactIds: List<Long>,
    val canonicalFactIds: List<Long>,
    val rulesByCategory: List<TrustReplyRuleMetadata> = emptyList(),
    val requestCoverage: List<TrustReplyRequestCoverage>,
    val draftReadiness: String,
    val contextWarnings: List<String> = emptyList(),
    val evidenceSetVersion: String,
    val savedState: TrustReplySavedState? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelection> = emptyList()
)

data class TrustReplySavedStatePayload(
    val schemaVersion: String,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val requestedFactIds: List<Long>,
    val selectedModel: String,
    val lockedItems: List<TrustReplyLockedItemRequest>,
    val requestFactSelections: List<TrustReplyRequestFactSelection> = emptyList()
)

data class TrustReplySavedState(
    val status: String,
    val stateVersion: Long = 0,
    val selectedModel: String = "",
    val requestedFactIds: List<Long> = emptyList(),
    val lockedItems: List<TrustReplyLockedItemRequest> = emptyList(),
    val requestFactSelections: List<TrustReplyRequestFactSelection> = emptyList()
)

data class TrustReplySaveStateRequest(
    val source: TrustReplySourceRef,
    val expectedStateVersion: Long,
    val schemaVersion: String? = null,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val requestedFactIds: List<Long>? = null,
    val selectedModel: String? = null,
    val lockedItems: List<TrustReplyLockedItemRequest>,
    val requestFactSelections: List<TrustReplyRequestFactSelection>? = null
)

data class TrustReplyRuleMetadata(
    val ruleId: Long,
    val displayName: String,
    val categoryId: Long? = null,
    val answerBody: String? = null
)

data class TrustReplyGenerationResult(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val draftText: String,
    val renderedDraftText: String,
    val draftHash: String,
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val qaRuleIds: List<Long>,
    val mode: String,
    val requestCoverage: List<TrustReplyRequestCoverage>,
    val generationState: String,
    val draftReadiness: String,
    val evidenceSetVersion: String,
    val groundedRequestCount: Int = 0,
    val requestCount: Int = requestCoverage.size,
    val unsupportedRequests: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val injectedDialogRefs: List<String> = emptyList(),
    val selectedModel: String = AiReplyModel.DEEPSEEK_V4_FLASH.name,
    val promptVersion: String = "",
    val appliedLlmAttemptTimeoutSeconds: Int = AiReplyTimeoutPolicy.DEFAULT_ATTEMPT_SECONDS,
    val appliedLlmTotalTimeoutSeconds: Int = AiReplyTimeoutPolicy.DEFAULT_TOTAL_SECONDS,
    val evidenceSources: List<AiReplyEvidenceSnapshot> = emptyList(),
    val itemVersions: List<TrustReplyItemVersion> = emptyList()
)

data class TrustReplyItemVersion(
    val versionId: String,
    val requestKey: String,
    val handling: TrustReplyItemHandling,
    val answerText: String,
    val claims: List<AiReplyItemClaim>,
    val model: String,
    val generationKind: TrustReplyItemGenerationKind,
    val evidenceSetVersion: String,
    val sourceVersion: String,
    val operatorInstructionHash: String = "",
    val requestIndex: Int = -1,
    val requestText: String = "",
    val operatorInstruction: String = ""
)

data class TrustReplyItemAdjustmentRequest(
    val source: TrustReplySourceRef,
    val expectedSourceVersion: String,
    val expectedEvidenceSetVersion: String,
    val requestKey: String,
    val handling: TrustReplyItemHandling,
    val operatorInstruction: String? = null,
    val model: String? = null,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelection>? = null,
    val llmAttemptTimeoutSeconds: Int? = null,
    val llmTotalTimeoutSeconds: Int? = null
)

data class TrustReplyItemAdjustmentResponse(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val version: TrustReplyItemVersion
)

data class TrustReplyLockedItemRequest(
    val requestKey: String,
    val versionId: String,
    val handling: TrustReplyItemHandling,
    val answerText: String,
    val claims: List<AiReplyItemClaim> = emptyList(),
    val model: String,
    val generationKind: TrustReplyItemGenerationKind,
    val evidenceSetVersion: String,
    val sourceVersion: String,
    val operatorInstructionHash: String = "",
    val operatorInstruction: String = ""
)

data class TrustReplyAssembleRequest(
    val source: TrustReplySourceRef,
    val expectedSourceVersion: String,
    val expectedEvidenceSetVersion: String,
    val lockedItems: List<TrustReplyLockedItemRequest>,
    val requestedFactIds: List<Long>? = null,
    val requestFactSelections: List<TrustReplyRequestFactSelection>? = null
)

data class TrustReplyAssembleResponse(
    val source: TrustReplySourceRef,
    val sourceVersion: String,
    val evidenceSetVersion: String,
    val rawDraftText: String,
    val renderedDraftText: String,
    val draftHash: String,
    val canonicalFactIds: List<Long>,
    val itemVersions: List<TrustReplyItemVersion>,
    val requestedFactIds: List<Long> = emptyList(),
    val requestFactSelections: List<TrustReplyRequestFactSelection> = emptyList()
)

open class TrustReplyWorkbenchException(
    val status: HttpStatus,
    val code: String
) : RuntimeException(code)

@Service
class TrustReplyWorkbenchService(
    private val mailRecordRepository: MailRecordRepository,
    private val inboundMailProcessingRepository: InboundMailProcessingRepository,
    private val expertContactRepository: ExpertContactRepository,
    private val aiTrainingQaService: AiTrainingQaService,
    private val aiReplyContextService: AiReplyContextService,
    private val qaFactSelectionService: QaFactSelectionService,
    private val qaRuleRepository: QaRuleRepository,
    private val aiReplyDraftService: AiReplyDraftService,
    private val aiReplyDraftPreviewService: AiReplyDraftPreviewService,
    private val aiReplyReviewAuditService: AiReplyReviewAuditService,
    private val llmProperties: LlmProperties,
    private val aiReplyPointByPointComposer: AiReplyPointByPointComposer,
    private val claimValidator: AiReplyHighRiskClaimValidator,
    private val stateStore: TrustReplyWorkbenchStateStore
) {
    fun resolveSource(source: TrustReplySourceRef): ResolvedTrustReplySource {
        require(source.sourceId > 0) { "sourceId must be positive" }
        return when (source.sourceType) {
            TrustReplySourceType.TRAINING_MAIL -> resolveTrainingMail(source)
            TrustReplySourceType.LIVE_INBOUND -> resolveLiveInbound(source)
        }
    }

    fun bootstrap(request: TrustReplyBootstrapRequest): TrustReplyBootstrapResponse {
        val resolved = resolveSource(request.source)
        val stored = stateStore.load(resolved.source.sourceType.name, resolved.source.sourceId)
        val now = LocalDateTime.now()
        val callerSelections = request.requestFactSelections
        val callerFactIds = request.requestedFactIds
        val storedPayload = if (callerSelections == null && callerFactIds == null) {
            stored?.payloadJson?.let { stateStore.decodePayload(it) }
        } else {
            null
        }
        // v2 durable state carries the canonical matrix; v1 carries the legacy flat union (I-6).
        val candidateSelections = callerSelections
            ?: storedPayload
                ?.takeIf { it.schemaVersion == TrustReplyWorkbenchStateStore.SCHEMA_VERSION }
                ?.requestFactSelections
        val candidateFactIds = callerFactIds
            ?: storedPayload
                ?.takeIf { it.schemaVersion != TrustReplyWorkbenchStateStore.SCHEMA_VERSION }
                ?.requestedFactIds
        var implicitSelectionUnusable = false
        val resolvedSelection = try {
            resolveCanonicalSelection(resolved, candidateSelections, candidateFactIds, useLocalEvidence = false)
        } catch (ex: TrustReplyWorkbenchException) {
            if (callerSelections == null && callerFactIds == null && storedPayload != null &&
                (candidateSelections != null || candidateFactIds != null)
            ) {
                implicitSelectionUnusable = true
                resolveCanonicalSelection(resolved, null, null, useLocalEvidence = false)
            } else {
                throw ex
            }
        }
        val selection = resolvedSelection.selection
        val savedState = if (implicitSelectionUnusable && stored != null && stored.expiresAt.isAfter(now)) {
            TrustReplySavedState(status = "STALE", stateVersion = stored.stateVersion)
        } else {
            restoreSavedState(
                resolved = resolved,
                stored = stored,
                selection = selection,
                matrix = resolvedSelection.requestFactSelections,
                evidenceSetVersion = resolvedSelection.evidenceSetVersion,
                now = now
            )
        }
        return TrustReplyBootstrapResponse(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            inboundSubject = resolved.subject,
            inboundText = resolved.inboundText,
            expertName = resolved.contact.expertName,
            expertEmail = resolved.contact.expertEmail,
            llmEnabled = llmProperties.enabled,
            availableModels = AiReplyModel.values().map { it.name },
            defaultModel = AiReplyModel.DEEPSEEK_V4_FLASH.name,
            suggestedFactIds = selection.sendQaRuleIds,
            canonicalFactIds = selection.sendQaRuleIds,
            rulesByCategory = availableFactMetadata(),
            requestCoverage = selection.requestFacts.toCoverage(resolved.sourceVersion),
            draftReadiness = bootstrapReadiness(selection),
            contextWarnings = resolved.contextWarnings,
            evidenceSetVersion = resolvedSelection.evidenceSetVersion,
            savedState = savedState,
            requestFactSelections = resolvedSelection.requestFactSelections
        )
    }

    fun saveState(request: TrustReplySaveStateRequest): TrustReplySavedState {
        val resolved = resolveSource(request.source)
        requireCurrentSourceVersion(request.sourceVersion, resolved.sourceVersion)
        // PUT accepts request schema v1 or v2 during the transition (I-6); writes are always v2.
        if (request.schemaVersion != null && request.schemaVersion !in setOf(
                TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
                TrustReplyWorkbenchStateStore.LEGACY_SCHEMA_VERSION
            )
        ) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_STATE_INVALID")
        }
        val resolvedSelection = resolveCanonicalSelection(
            resolved,
            request.requestFactSelections,
            request.requestedFactIds,
            useLocalEvidence = false
        )
        requireCurrentEvidenceVersion(request.evidenceSetVersion, resolvedSelection.evidenceSetVersion)
        val now = LocalDateTime.now()
        if (request.lockedItems.isEmpty()) {
            stateStore.delete(resolved.source.sourceType.name, resolved.source.sourceId, request.expectedStateVersion)
            stateStore.pruneExpired(now)
            return TrustReplySavedState(status = "DELETED", stateVersion = 0)
        }
        val orderedLocked = validateLockedSubset(
            resolved = resolved,
            selection = resolvedSelection.selection,
            evidenceSetVersion = resolvedSelection.evidenceSetVersion,
            lockedItems = request.lockedItems
        )
        val payload = TrustReplySavedStatePayload(
            schemaVersion = TrustReplyWorkbenchStateStore.SCHEMA_VERSION,
            sourceVersion = resolved.sourceVersion,
            evidenceSetVersion = resolvedSelection.evidenceSetVersion,
            requestedFactIds = resolvedSelection.selection.sendQaRuleIds,
            requestFactSelections = resolvedSelection.requestFactSelections,
            selectedModel = request.selectedModel?.trim()?.takeIf { it.isNotBlank() }
                ?: AiReplyModel.DEEPSEEK_V4_FLASH.name,
            lockedItems = orderedLocked
        )
        val json = stateStore.encodePayload(payload)
        val newVersion = stateStore.save(
            resolved.source.sourceType.name,
            resolved.source.sourceId,
            request.expectedStateVersion,
            json,
            now
        )
        stateStore.pruneExpired(now)
        return TrustReplySavedState(
            status = "SAVED",
            stateVersion = newVersion,
            selectedModel = payload.selectedModel,
            requestedFactIds = payload.requestedFactIds,
            lockedItems = payload.lockedItems,
            requestFactSelections = payload.requestFactSelections
        )
    }

    private fun restoreSavedState(
        resolved: ResolvedTrustReplySource,
        stored: TrustReplyWorkbenchStateStore.TrustReplyStoredState?,
        selection: ResolvedQaRules,
        matrix: List<TrustReplyRequestFactSelection>,
        evidenceSetVersion: String,
        now: LocalDateTime
    ): TrustReplySavedState? {
        if (stored == null) return null
        if (!stored.expiresAt.isAfter(now)) {
            stateStore.pruneExpired(now)
            return TrustReplySavedState(status = "EXPIRED", stateVersion = 0)
        }
        val payload = stateStore.decodePayload(stored.payloadJson) ?: return TrustReplySavedState(
            status = "INVALID",
            stateVersion = stored.stateVersion
        )
        if (payload.sourceVersion != resolved.sourceVersion || payload.evidenceSetVersion != evidenceSetVersion) {
            return TrustReplySavedState(status = "STALE", stateVersion = stored.stateVersion)
        }
        // v2 restores must carry the identical canonical mapping; v1 is re-normalized
        // from its flat union by the resolver and must still match the union (I-4/I-6).
        if (payload.schemaVersion == TrustReplyWorkbenchStateStore.SCHEMA_VERSION) {
            if (payload.requestFactSelections != matrix) {
                return TrustReplySavedState(status = "STALE", stateVersion = stored.stateVersion)
            }
        } else if (payload.requestedFactIds != selection.sendQaRuleIds) {
            return TrustReplySavedState(status = "STALE", stateVersion = stored.stateVersion)
        }
        val ordered = try {
            validateLockedSubset(
                resolved = resolved,
                selection = selection,
                evidenceSetVersion = evidenceSetVersion,
                lockedItems = payload.lockedItems
            )
        } catch (ex: TrustReplyWorkbenchException) {
            return TrustReplySavedState(status = "STALE", stateVersion = stored.stateVersion)
        }
        return TrustReplySavedState(
            status = "RESTORED",
            stateVersion = stored.stateVersion,
            selectedModel = payload.selectedModel,
            requestedFactIds = payload.requestedFactIds,
            lockedItems = ordered,
            requestFactSelections = matrix
        )
    }

    private fun validateLockedSubset(
        resolved: ResolvedTrustReplySource,
        selection: ResolvedQaRules,
        evidenceSetVersion: String,
        lockedItems: List<TrustReplyLockedItemRequest>
    ): List<TrustReplyLockedItemRequest> {
        val canonicalItems = selection.requestFacts.sortedBy { it.index }
        val canonicalKeys = canonicalItems.map { requestKey(resolved.sourceVersion, it) }
        val actualKeys = lockedItems.map { it.requestKey }
        if (actualKeys.size != actualKeys.toSet().size) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEMS_INCOMPLETE")
        }
        val unknown = actualKeys.filter { it !in canonicalKeys }
        if (unknown.isNotEmpty()) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_REQUEST_KEY_INVALID")
        }
        val byKey = lockedItems.associateBy { it.requestKey }
        val groundedSections = mutableListOf<ValidatedSection>()
        val versions = canonicalItems.mapNotNull { item ->
            val key = requestKey(resolved.sourceVersion, item)
            val locked = byKey[key] ?: return@mapNotNull null
            validateLockedItem(
                item = item,
                locked = locked,
                sourceVersion = resolved.sourceVersion,
                evidenceSetVersion = evidenceSetVersion,
                inboundText = resolved.inboundText
            )
            materializeVersion(
                item = item,
                requestKey = locked.requestKey,
                handling = locked.handling,
                answerText = locked.answerText,
                claims = locked.claims,
                model = locked.model,
                generationKind = locked.generationKind,
                evidenceSetVersion = evidenceSetVersion,
                sourceVersion = resolved.sourceVersion,
                operatorInstruction = locked.operatorInstruction,
                operatorInstructionHash = locked.operatorInstructionHash
            ).also { version ->
                if (locked.handling == TrustReplyItemHandling.ANSWER_WITH_EVIDENCE ||
                    locked.handling == TrustReplyItemHandling.ANSWER_SUPPORTED_PART
                ) {
                    groundedSections += ValidatedSection(
                        item.index,
                        version.claims.map { claim ->
                            IntentAnswer(claim.intentKey, claim.text, claim.sourceRuleIds)
                        }
                    )
                }
                if (version.versionId != locked.versionId) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ITEM_VERSION_INVALID")
                }
            }
        }
        validateGroundedTrustBoundary(selection.requestFacts, groundedSections)
        validateNoDuplicateClaims(versions)
        return byKey.let { map ->
            canonicalItems.mapNotNull { item -> map[requestKey(resolved.sourceVersion, item)] }
        }
    }

    fun generate(
        request: TrustReplyGenerationRequest,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP,
        beforeCommit: (() -> Boolean)? = null
    ): TrustReplyGenerationResult {
        val resolved = resolveSource(request.source)
        val operation = request.operation.trim().uppercase()
        // I-7: matrix clients generate per-item only; a FULL_DRAFT carrying the matrix
        // would flatten the assignment back into a reusable flat pool and must fail closed.
        if (operation != "ADJUST_ITEM" && request.requestFactSelections != null) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_OPERATION_INVALID")
        }
        val expectedVersion = request.expectedSourceVersion?.trim()
        if (expectedVersion.isNullOrEmpty()) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_VERSION_REQUIRED")
        }
        if (expectedVersion != resolved.sourceVersion) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE")
        }

        if (operation == "ADJUST_ITEM") {
            val key = request.requestKey?.trim()
                ?: throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_REQUEST_KEY_REQUIRED")
            val handling = request.handling
                ?: throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_HANDLING_REQUIRED")
            val adjustment = adjustItem(
                TrustReplyItemAdjustmentRequest(
                    source = request.source,
                    expectedSourceVersion = expectedVersion,
                    expectedEvidenceSetVersion = request.expectedEvidenceSetVersion.orEmpty(),
                    requestKey = key,
                    handling = handling,
                    operatorInstruction = request.operatorInstruction,
                    model = request.model,
                    requestedFactIds = request.requestedFactIds,
                    requestFactSelections = request.requestFactSelections,
                    llmAttemptTimeoutSeconds = request.llmAttemptTimeoutSeconds,
                    llmTotalTimeoutSeconds = request.llmTotalTimeoutSeconds
                ),
                cancellationToken = cancellationToken,
                progressReporter = progressReporter,
                beforeCommit = beforeCommit
            )
            val version = adjustment.version
            return TrustReplyGenerationResult(
                source = adjustment.source,
                sourceVersion = adjustment.sourceVersion,
                draftText = version.answerText,
                renderedDraftText = version.answerText,
                draftHash = AiReplyDraftService.sha256Hex(version.answerText),
                usedLlm = version.generationKind == TrustReplyItemGenerationKind.AI_GENERATED,
                llmEnabled = llmProperties.enabled,
                qaRuleIds = version.claims.flatMap { it.sourceRuleIds }.distinct(),
                mode = "ITEM",
                requestCoverage = emptyList(),
                generationState = if (version.generationKind == TrustReplyItemGenerationKind.AI_GENERATED) {
                    AiReplyGenerationState.LLM_USED.name
                } else {
                    AiReplyGenerationState.FALLBACK_NO_RESPONSE.name
                },
                draftReadiness = "READY",
                evidenceSetVersion = adjustment.evidenceSetVersion,
                itemVersions = listOf(version),
                selectedModel = version.model
            )
        }

        val result = if (
            request.llmAttemptTimeoutSeconds == null &&
            request.llmTotalTimeoutSeconds == null &&
            cancellationToken == null &&
            progressReporter === AiReplyProgressReporter.NOOP
        ) {
            aiReplyDraftService.generate(
                inboundText = resolved.inboundText,
                operatorTurns = request.turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = resolved.profileText,
                mailHistory = resolved.mailHistory,
                contextWarnings = resolved.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = resolved.researchProfileSufficient
            )
        } else {
            aiReplyDraftService.generate(
                inboundText = resolved.inboundText,
                operatorTurns = request.turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = resolved.profileText,
                mailHistory = resolved.mailHistory,
                contextWarnings = resolved.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = resolved.researchProfileSufficient,
                llmAttemptTimeoutSeconds = request.llmAttemptTimeoutSeconds,
                llmTotalTimeoutSeconds = request.llmTotalTimeoutSeconds,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            )
        }

        cancellationToken?.throwIfCancelled()
        if (beforeCommit != null && !beforeCommit()) {
            throw AiReplyGenerationCancelledException()
        }
        val auditSnapshot = if (resolved.source.sourceType == TrustReplySourceType.LIVE_INBOUND) {
            if (request.turns.isEmpty()) {
                aiReplyReviewAuditService.recordInitialDraft(
                    inboundProcessingId = resolved.source.sourceId,
                    contactId = requireNotNull(resolved.contact.id),
                    result = result,
                    operatorName = request.operatorName
                )
            } else {
                aiReplyReviewAuditService.buildSnapshot(result)
            }
        } else {
            null
        }

        val preview = aiReplyDraftPreviewService.preview(
            raw = result.draftText,
            contact = resolved.contact,
            senderAccountCode = resolved.senderAccountCode
        )
        val warnings = mergeWarnings(result.contextWarnings, preview.warningCodes)
        val policy = AiReplyTimeoutPolicy.resolve(
            request.llmAttemptTimeoutSeconds,
            request.llmTotalTimeoutSeconds
        )
        return TrustReplyGenerationResult(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            draftText = result.draftText,
            renderedDraftText = preview.renderedText,
            draftHash = auditSnapshot?.draftHash ?: AiReplyDraftService.sha256Hex(result.draftText),
            usedLlm = result.usedLlm,
            llmEnabled = llmProperties.enabled,
            qaRuleIds = result.qaRuleIds,
            mode = result.mode.name,
            requestCoverage = result.requestFacts.toCoverage(resolved.sourceVersion),
            generationState = result.generationState.name,
            draftReadiness = result.draftReadiness.name,
            evidenceSetVersion = result.evidenceSetVersion,
            groundedRequestCount = result.groundedRequestCount,
            requestCount = result.requestCount,
            unsupportedRequests = result.unsupportedRequests,
            contextWarnings = warnings,
            injectedDialogRefs = result.fewShotDialogRefs,
            selectedModel = result.selectedModel,
            promptVersion = result.promptVersion,
            appliedLlmAttemptTimeoutSeconds = policy.attemptTimeoutSeconds,
            appliedLlmTotalTimeoutSeconds = policy.totalTimeoutSeconds,
            evidenceSources = result.evidenceSources,
            itemVersions = buildInitialItemVersions(
                requestFacts = result.requestFacts,
                sourceVersion = resolved.sourceVersion,
                evidenceSetVersion = result.evidenceSetVersion,
                selectedModel = result.selectedModel,
                itemAnswers = result.itemAnswers
            )
        )
    }

    fun adjustItem(
        request: TrustReplyItemAdjustmentRequest,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP,
        beforeCommit: (() -> Boolean)? = null
    ): TrustReplyItemAdjustmentResponse {
        cancellationToken?.throwIfCancelled()
        val resolved = resolveSource(request.source)
        requireCurrentSourceVersion(request.expectedSourceVersion, resolved.sourceVersion)
        val resolvedSelection = resolveCanonicalSelection(
            resolved,
            request.requestFactSelections,
            request.requestedFactIds,
            useLocalEvidence = request.handling == TrustReplyItemHandling.OMIT
        )
        val selection = resolvedSelection.selection
        val evidenceSetVersion = resolvedSelection.evidenceSetVersion
        requireCurrentEvidenceVersion(request.expectedEvidenceSetVersion, evidenceSetVersion)
        val item = selection.requestFacts.firstOrNull { item ->
            requestKey(resolved.sourceVersion, item) == request.requestKey
        } ?: throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_REQUEST_KEY_INVALID")
        requireAllowedHandlingForApi(item.status, request.handling)
        if (request.handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT) {
            if (request.operatorInstruction?.trim()?.length ?: 0 > 500) {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID")
            }
            if (request.operatorInstruction?.trim().isNullOrEmpty()) {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_OPERATOR_INSTRUCTION_INVALID")
            }
        }
        if (request.handling == TrustReplyItemHandling.OMIT) {
            cancellationToken?.throwIfCancelled()
            if (beforeCommit != null && !beforeCommit()) {
                throw AiReplyGenerationCancelledException()
            }
            val version = materializeVersion(
                item = item,
                requestKey = request.requestKey,
                handling = request.handling,
                answerText = "",
                claims = emptyList(),
                model = AiReplyModel.fromNullable(request.model).name,
                generationKind = TrustReplyItemGenerationKind.OMITTED,
                evidenceSetVersion = evidenceSetVersion,
                sourceVersion = resolved.sourceVersion,
                operatorInstruction = request.operatorInstruction
            )
            return TrustReplyItemAdjustmentResponse(
                source = resolved.source,
                sourceVersion = resolved.sourceVersion,
                evidenceSetVersion = evidenceSetVersion,
                version = version
            )
        }
        val generated = aiReplyDraftService.generateItem(
            inboundText = resolved.inboundText,
            requestFact = item,
            handling = request.handling,
            requestKey = request.requestKey,
            operatorInstruction = request.operatorInstruction,
            expertProfile = resolved.profileText,
            mailHistory = resolved.mailHistory,
            contextWarnings = resolved.contextWarnings,
            replyModel = request.model,
            researchProfileSufficient = resolved.researchProfileSufficient,
            llmAttemptTimeoutSeconds = request.llmAttemptTimeoutSeconds,
            llmTotalTimeoutSeconds = request.llmTotalTimeoutSeconds,
            cancellationToken = cancellationToken,
            progressReporter = progressReporter
        )
        cancellationToken?.throwIfCancelled()
        if (beforeCommit != null && !beforeCommit()) {
            throw AiReplyGenerationCancelledException()
        }
        if (!generated.lockable || generated.itemAnswer == null || generated.generationKind == null) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ITEM_GENERATION_FAILED")
        }
        val model = AiReplyModel.fromNullable(request.model).name
        val version = materializeVersion(
            item = item,
            requestKey = request.requestKey,
            handling = request.handling,
            answerText = generated.itemAnswer.answerText,
            claims = generated.itemAnswer.claims,
            model = model,
            generationKind = generated.generationKind,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = resolved.sourceVersion,
            operatorInstruction = request.operatorInstruction
        )
        return TrustReplyItemAdjustmentResponse(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            evidenceSetVersion = evidenceSetVersion,
            version = version
        )
    }

    fun assemble(request: TrustReplyAssembleRequest): TrustReplyAssembleResponse {
        val resolved = resolveSource(request.source)
        requireCurrentSourceVersion(request.expectedSourceVersion, resolved.sourceVersion)
        val resolvedSelection = resolveCanonicalSelection(
            resolved,
            request.requestFactSelections,
            request.requestedFactIds,
            useLocalEvidence = false
        )
        val selection = resolvedSelection.selection
        val evidenceSetVersion = resolvedSelection.evidenceSetVersion
        requireCurrentEvidenceVersion(request.expectedEvidenceSetVersion, evidenceSetVersion)
        val expectedKeys = selection.requestFacts.map { requestKey(resolved.sourceVersion, it) }
        val actualKeys = request.lockedItems.map { it.requestKey }
        if (actualKeys.size != actualKeys.toSet().size || actualKeys.toSet() != expectedKeys.toSet()) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEMS_INCOMPLETE")
        }

        val orderedItems = selection.requestFacts.sortedBy { it.index }.map { item ->
            val key = requestKey(resolved.sourceVersion, item)
            request.lockedItems.first { it.requestKey == key }.also { locked ->
                validateLockedItem(
                    item = item,
                    locked = locked,
                    sourceVersion = resolved.sourceVersion,
                    evidenceSetVersion = evidenceSetVersion,
                    inboundText = resolved.inboundText
                )
            }
        }
        val groundedSections = mutableListOf<ValidatedSection>()
        val versions = orderedItems.mapIndexed { index, locked ->
            val item = selection.requestFacts.sortedBy { it.index }[index]
            if (locked.handling == TrustReplyItemHandling.ANSWER_WITH_EVIDENCE ||
                locked.handling == TrustReplyItemHandling.ANSWER_SUPPORTED_PART
            ) {
                groundedSections += ValidatedSection(
                    item.index,
                    canonicalizeClaims(item, locked.claims, locked.answerText).map { claim ->
                        IntentAnswer(claim.intentKey, claim.text, claim.sourceRuleIds)
                    }
                )
            }
            materializeVersion(
                item = item,
                requestKey = locked.requestKey,
                handling = locked.handling,
                answerText = locked.answerText,
                claims = locked.claims,
                model = locked.model,
                generationKind = locked.generationKind,
                evidenceSetVersion = evidenceSetVersion,
                sourceVersion = resolved.sourceVersion,
                operatorInstruction = locked.operatorInstruction,
                operatorInstructionHash = locked.operatorInstructionHash
            ).also { version ->
                if (version.versionId != locked.versionId) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ITEM_VERSION_INVALID")
                }
            }
        }

        validateGroundedTrustBoundary(selection.requestFacts, groundedSections)

        validateNoDuplicateClaims(versions)

        val orderedAnswers = versions.mapNotNull { version ->
            version.answerText.takeIf { version.handling != TrustReplyItemHandling.OMIT }
        }
        val raw = aiReplyPointByPointComposer.composeLockedItems(orderedAnswers)
        val preview = aiReplyDraftPreviewService.preview(
            raw = raw,
            contact = resolved.contact,
            senderAccountCode = resolved.senderAccountCode
        )
        val factIds = linkedSetOf<Long>()
        groundedSections.flatMap { it.answers }.flatMap { it.sourceRuleIds }.forEach(factIds::add)
        return TrustReplyAssembleResponse(
            source = resolved.source,
            sourceVersion = resolved.sourceVersion,
            evidenceSetVersion = evidenceSetVersion,
            rawDraftText = raw,
            renderedDraftText = preview.renderedText,
            draftHash = AiReplyDraftService.sha256Hex(raw),
            canonicalFactIds = factIds.toList(),
            itemVersions = versions,
            requestedFactIds = selection.sendQaRuleIds,
            requestFactSelections = resolvedSelection.requestFactSelections
        )
    }

    private fun validateGroundedTrustBoundary(
        requestFacts: List<RequestFactItem>,
        groundedSections: List<ValidatedSection>
    ) {
        if (groundedSections.isEmpty()) return
        val plan = AiReplyGroundedContentPlanner().buildPlan(requestFacts, emptySet())
        val claimResult = claimValidator.validate(groundedSections, requestFacts)
        if (!claimResult.valid) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
        }
        val trustResult = claimValidator.validateGroundedCandidate(
            GroundedCandidateInput(
                validatedSections = groundedSections,
                requestFacts = requestFacts,
                plan = plan,
                finalBody = groundedSections.flatMap { it.answers }.joinToString(" ") { it.answer },
                hasBlockingTrustGap = AiReplyGroundedContentPlanner().hasBlockingTrustGap(requestFacts),
                sourceTextsByClaim = claimResult.sourceTextsByClaim
            )
        )
        if (!trustResult.valid) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
        }
    }

    private fun validateNoDuplicateClaims(versions: List<TrustReplyItemVersion>) {
        val seenClaimKeys = mutableSetOf<Pair<String, Long>>()
        val seenNormalizedAnswers = mutableMapOf<String, Int>()
        versions.forEach { version ->
            if (version.handling == TrustReplyItemHandling.OMIT) {
                return@forEach
            }
            version.claims.forEach { claim ->
                claim.sourceRuleIds.forEach { sourceRuleId ->
                    if (!seenClaimKeys.add(claim.intentKey to sourceRuleId)) {
                        throw TrustReplyWorkbenchException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "TRUST_REPLY_DUPLICATE_CLAIM"
                        )
                    }
                }
            }
            if (version.answerText.isNotBlank()) {
                val normalized = version.answerText.trim()
                    .lowercase(Locale.ROOT)
                    .replace(Regex("\\s+"), " ")
                val previous = seenNormalizedAnswers.putIfAbsent(normalized, version.requestIndex)
                if (previous != null) {
                    throw TrustReplyWorkbenchException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TRUST_REPLY_DUPLICATE_CLAIM"
                    )
                }
            }
        }
    }

    private fun validateLockedItem(
        item: RequestFactItem,
        locked: TrustReplyLockedItemRequest,
        sourceVersion: String,
        evidenceSetVersion: String,
        inboundText: String
    ) {
        if (locked.sourceVersion != sourceVersion) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE")
        }
        if (locked.evidenceSetVersion != evidenceSetVersion) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_EVIDENCE_STALE")
        }
        requireAllowedHandlingForApi(item.status, locked.handling)
        when (locked.handling) {
            TrustReplyItemHandling.OMIT -> {
                if (locked.answerText.isNotEmpty() || locked.claims.isNotEmpty() ||
                    locked.generationKind != TrustReplyItemGenerationKind.OMITTED
                ) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
                }
            }
            TrustReplyItemHandling.ACKNOWLEDGE_PENDING -> {
                if (locked.answerText.isBlank() || locked.claims.isNotEmpty() ||
                    locked.generationKind !in setOf(
                        TrustReplyItemGenerationKind.AI_GENERATED,
                        TrustReplyItemGenerationKind.SAFE_TEMPLATE
                    ) || !claimValidator.validateNoEvidenceAcknowledgement(locked.answerText).valid
                ) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ACKNOWLEDGEMENT_INVALID")
                }
            }
            TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT -> {
                val instruction = locked.operatorInstruction.trim()
                if (item.status != RequestGroundingStatus.UNSUPPORTED || instruction.isBlank() ||
                    instruction.length > 500 || locked.operatorInstructionHash != sha256Hex(instruction) ||
                    locked.answerText.isBlank() || locked.claims.isNotEmpty() ||
                    locked.generationKind != TrustReplyItemGenerationKind.AI_GENERATED
                ) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
                }
                val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
                if (AiReplyActionPolicy.detectActions(locked.answerText).isNotEmpty() ||
                    AiReplyActionPolicy.findViolations(locked.answerText, allowedActions).isNotEmpty()
                ) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
                }
            }
            TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
            TrustReplyItemHandling.ANSWER_SUPPORTED_PART -> {
                if (locked.answerText.isBlank() || locked.generationKind != TrustReplyItemGenerationKind.AI_GENERATED) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
                }
                val canonicalClaims = canonicalizeClaims(item, locked.claims, locked.answerText)
                val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
                if (canonicalClaims.any { claim ->
                        AiReplyActionPolicy.detectActions(claim.text).isNotEmpty() ||
                            AiReplyActionPolicy.findViolations(claim.text, allowedActions).isNotEmpty()
                    }
                ) {
                    throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIM_INVALID")
                }
            }
        }
    }

    private fun canonicalizeClaims(
        item: RequestFactItem,
        claims: List<AiReplyItemClaim>,
        answerText: String
    ): List<AiReplyItemClaim> {
        val supported = item.intents.filter { it.status == "SUPPORTED" }
        val expected = if (supported.isNotEmpty()) {
            supported.map { it.intentKey to it.evidenceRuleIds.distinct() }
        } else if (item.factRuleIds.isNotEmpty()) {
            listOf("general.answer" to item.factRuleIds.distinct())
        } else {
            emptyList()
        }
        val byKey = claims.groupBy { it.intentKey }
        if (byKey.size != claims.size || byKey.keys != expected.map { it.first }.toSet()) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIMS_INVALID")
        }
        val canonical = expected.map { (intentKey, sourceIds) ->
            val claim = byKey[intentKey]?.singleOrNull()
                ?: throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIMS_INVALID")
            if (claim.sourceRuleIds.distinct() != sourceIds || claim.text.trim().isBlank()) {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_CLAIMS_INVALID")
            }
            AiReplyItemClaim(intentKey, claim.text.trim(), sourceIds)
        }
        if (answerText != canonical.joinToString(" ") { it.text }) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_ANSWER_CLAIMS_MISMATCH")
        }
        return canonical
    }

    private fun requireAllowedHandlingForApi(status: RequestGroundingStatus, handling: TrustReplyItemHandling) {
        try {
            requireAllowedHandling(status, handling)
        } catch (_: IllegalArgumentException) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_HANDLING_INVALID")
        }
    }

    private fun materializeVersion(
        item: RequestFactItem,
        requestKey: String,
        handling: TrustReplyItemHandling,
        answerText: String,
        claims: List<AiReplyItemClaim>,
        model: String,
        generationKind: TrustReplyItemGenerationKind,
        evidenceSetVersion: String,
        sourceVersion: String,
        operatorInstruction: String? = null,
        operatorInstructionHash: String = ""
    ): TrustReplyItemVersion {
        val normalizedInstruction = if (handling == TrustReplyItemHandling.OMIT) {
            ""
        } else {
            operatorInstruction?.trim().orEmpty()
        }
        val normalizedClaims = if (
            handling == TrustReplyItemHandling.OMIT ||
            handling == TrustReplyItemHandling.ACKNOWLEDGE_PENDING ||
            handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
        ) {
            emptyList()
        } else {
            canonicalizeClaims(item, claims, answerText)
        }
        val normalizedAnswer = when (handling) {
            TrustReplyItemHandling.OMIT -> ""
            TrustReplyItemHandling.ACKNOWLEDGE_PENDING -> answerText.trim()
            TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT -> answerText.trim()
            else -> normalizedClaims.joinToString(" ") { it.text }
        }
        val instructionHash = sha256Hex(normalizedInstruction).also { calculated ->
            if (handling != TrustReplyItemHandling.OMIT &&
                operatorInstructionHash.isNotBlank() && operatorInstructionHash != calculated
            ) {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_LOCKED_ITEM_INVALID")
            }
        }
        return TrustReplyItemVersion(
            versionId = versionId(
                requestKey, handling, normalizedAnswer, normalizedClaims, model, generationKind,
                evidenceSetVersion, sourceVersion, instructionHash
            ),
            requestKey = requestKey,
            handling = handling,
            answerText = normalizedAnswer,
            claims = normalizedClaims,
            model = model,
            generationKind = generationKind,
            evidenceSetVersion = evidenceSetVersion,
            sourceVersion = sourceVersion,
            operatorInstructionHash = instructionHash,
            requestIndex = item.index,
            requestText = item.requestText,
            operatorInstruction = normalizedInstruction
        )
    }

    private fun resolveTrainingMail(source: TrustReplySourceRef): ResolvedTrustReplySource {
        val mail = mailRecordRepository.findById(source.sourceId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.NOT_FOUND, "TRUST_REPLY_SOURCE_NOT_FOUND")
        }
        if (!mail.direction.equals("INBOUND", ignoreCase = true)) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_SOURCE_NOT_INBOUND")
        }
        return resolveWithContact(
            source = source,
            contactId = mail.expertContactId,
            inboundText = mail.inboundText(),
            subject = mail.subject,
            messageId = mail.messageId,
            senderAccountCode = mail.senderAccountCode
        )
    }

    private fun resolveLiveInbound(source: TrustReplySourceRef): ResolvedTrustReplySource {
        val inbound = inboundMailProcessingRepository.findById(source.sourceId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.NOT_FOUND, "TRUST_REPLY_SOURCE_NOT_FOUND")
        }
        val contactId = inbound.expertContactId ?: throw TrustReplyWorkbenchException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRUST_REPLY_SOURCE_CONTACT_REQUIRED"
        )
        return resolveWithContact(
            source = source,
            contactId = contactId,
            inboundText = inbound.inboundText(),
            subject = inbound.subject,
            messageId = inbound.messageId,
            senderAccountCode = inbound.senderAccountCode
        )
    }

    private fun resolveWithContact(
        source: TrustReplySourceRef,
        contactId: Long,
        inboundText: String,
        subject: String?,
        messageId: String?,
        senderAccountCode: String?
    ): ResolvedTrustReplySource {
        val contact = expertContactRepository.findById(contactId).orElseThrow {
            TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_SOURCE_CONTACT_NOT_FOUND")
        }
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
        val context = aiReplyContextService.build(
            contact = contact,
            records = records,
            inboundText = inboundText,
            trainingKnowledge = knowledge,
            currentInboundMessageId = messageId
        )
        val sourceVersion = sourceVersion(
            source = source,
            contactId = contactId,
            messageId = messageId,
            subject = subject,
            senderAccountCode = senderAccountCode,
            inboundText = inboundText,
            mailHistory = context.mailHistory,
            profileText = context.profileText,
            researchProfileSufficient = context.researchProfileSufficient
        )
        return ResolvedTrustReplySource(
            source = source,
            contact = contact,
            inboundText = inboundText,
            subject = subject,
            messageId = messageId,
            senderAccountCode = senderAccountCode,
            profileText = context.profileText,
            mailHistory = context.mailHistory,
            contextWarnings = context.contextWarnings,
            researchProfileSufficient = context.researchProfileSufficient,
            sourceVersion = sourceVersion
        )
    }

    private fun sourceVersion(
        source: TrustReplySourceRef,
        contactId: Long,
        messageId: String?,
        subject: String?,
        senderAccountCode: String?,
        inboundText: String,
        mailHistory: String,
        profileText: String,
        researchProfileSufficient: Boolean
    ): String {
        val canonical = listOf(
            source.sourceType.name,
            source.sourceId.toString(),
            contactId.toString(),
            messageId.orEmpty(),
            subject.orEmpty(),
            senderAccountCode.orEmpty(),
            sha256Hex(inboundText),
            sha256Hex(mailHistory),
            sha256Hex(profileText),
            researchProfileSufficient.toString()
        ).joinToString("\u0000")
        return sha256Hex(canonical)
    }

    private data class CanonicalRequestRef(
        val index: Int,
        val requestKey: String,
        val requestText: String
    )

    private data class ResolvedCanonicalSelection(
        val selection: ResolvedQaRules,
        val requestFactSelections: List<TrustReplyRequestFactSelection>,
        val evidenceSetVersion: String
    )

    /**
     * Unified workbench selection resolver (Task 3): resolves canonical
     * requestKeys, then matrix/legacy/auto input, and returns the resolved
     * rules, the canonical matrix, and the mapping-sensitive evidence version.
     * [useLocalEvidence] keeps the OMIT fast path free of the draft-service
     * evidence read while producing an identical base version (I-5).
     */
    private fun resolveCanonicalSelection(
        resolved: ResolvedTrustReplySource,
        requestFactSelections: List<TrustReplyRequestFactSelection>?,
        requestedFactIds: List<Long>?,
        useLocalEvidence: Boolean
    ): ResolvedCanonicalSelection {
        if (requestFactSelections != null && requestedFactIds != null) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_AMBIGUOUS")
        }
        val selectionsByRequest = requestFactSelections?.let { selections ->
            val canonical = canonicalRequests(resolved)
            validateMatrixKeys(canonical, selections)
            canonical.sortedBy { it.index }.map { ref ->
                selections.first { it.requestKey == ref.requestKey }.factRuleIds
            }
        }
        val selection = qaFactSelectionService.selectForWorkbench(
            inboundText = resolved.inboundText,
            selectionsByRequest = selectionsByRequest,
            requestedFactIds = requestedFactIds,
            researchProfileSufficient = resolved.researchProfileSufficient
        )
        val matrix = canonicalMatrix(resolved.sourceVersion, selection)
        val matrixIds = matrix.flatMap { it.factRuleIds }
        if (matrixIds.size != matrixIds.toSet().size) {
            // Duplicates that survive canonicalization (I-2) must never reach the store/composer.
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_ALREADY_ASSIGNED")
        }
        val baseEvidence = if (useLocalEvidence) {
            localEvidenceSetVersion(selection.sendQaRuleIds)
        } else {
            aiReplyDraftService.buildEvidenceSnapshotForSelection(selection.sendQaRuleIds).first
        }
        return ResolvedCanonicalSelection(
            selection = selection,
            requestFactSelections = matrix,
            evidenceSetVersion = evidenceSetVersionWithMapping(baseEvidence, matrix)
        )
    }

    private fun canonicalRequests(resolved: ResolvedTrustReplySource): List<CanonicalRequestRef> =
        QaRequestExtractor.extract(resolved.inboundText).mapIndexed { idx, request ->
            val intentKeys = AiReplyIntentCatalog.matchIntents(request.text).map { it.key }
            CanonicalRequestRef(
                index = idx + 1,
                requestKey = requestKey(resolved.sourceVersion, idx + 1, request.text, intentKeys),
                requestText = request.text
            )
        }

    private fun validateMatrixKeys(
        canonical: List<CanonicalRequestRef>,
        selections: List<TrustReplyRequestFactSelection>
    ) {
        val keys = selections.map { it.requestKey }
        if (keys.any { it.isBlank() } || keys.size != keys.toSet().size) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_REQUEST_KEY_INVALID")
        }
        val knownKeys = canonical.map { it.requestKey }.toSet()
        if (keys.any { it !in knownKeys }) {
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_REQUEST_KEY_INVALID")
        }
        if (knownKeys.any { it !in keys.toSet() }) {
            // I-1: the matrix must fully express every canonical request.
            throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID")
        }
        selections.forEach { selection ->
            if (selection.factRuleIds.any { it <= 0 }) {
                throw TrustReplyWorkbenchException(HttpStatus.UNPROCESSABLE_ENTITY, "TRUST_REPLY_FACT_SELECTION_INVALID")
            }
        }
    }

    private fun canonicalMatrix(
        sourceVersion: String,
        selection: ResolvedQaRules
    ): List<TrustReplyRequestFactSelection> =
        selection.requestFacts.sortedBy { it.index }.map { item ->
            TrustReplyRequestFactSelection(
                requestKey = requestKey(sourceVersion, item),
                factRuleIds = item.factRuleIds
            )
        }

    /**
     * I-5: the workbench evidence version binds the per-request fact assignment
     * into the identity — the same fact union re-assigned to a different request
     * produces a different version. Inputs are rule ids, availability, updatedAt,
     * answerBody SHA-256 and the canonical mapping only; no observed time.
     */
    private fun evidenceSetVersionWithMapping(
        baseEvidenceVersion: String,
        matrix: List<TrustReplyRequestFactSelection>
    ): String {
        val mappingCanonical = matrix.joinToString("\u0001") { selection ->
            "${selection.requestKey}\u0000${selection.factRuleIds.joinToString(",")}"
        }
        return sha256Hex("$baseEvidenceVersion\u0000$mappingCanonical")
    }

    /**
     * OMIT fast path: mirrors [AiReplyDraftService.buildEvidenceSnapshotForSelection]
     * without the draft-service read so an OMIT adjustment never needs evidence
     * assembly, while producing the identical base version for the same input.
     */
    private fun localEvidenceSetVersion(sendQaRuleIds: List<Long>): String {
        val versionParts = sendQaRuleIds.distinct().map { ruleId ->
            val rule = try {
                qaRuleRepository.findById(ruleId).orElse(null)
            } catch (_: Exception) {
                null
            }
            val available = rule != null && rule.enabled && rule.answerBody.isNotBlank()
            val updatedAt = rule?.updatedAt?.toString()
            val answerBodyHash = if (available) sha256Hex(rule?.answerBody.orEmpty()) else ""
            "$ruleId:$available:$updatedAt:$answerBodyHash"
        }
        return sha256Hex(versionParts.joinToString("|"))
    }

    private fun requireCurrentSourceVersion(expected: String?, actual: String) {
        if (expected.isNullOrBlank()) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_SOURCE_VERSION_REQUIRED")
        }
        if (expected != actual) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_SOURCE_STALE")
        }
    }

    private fun requireCurrentEvidenceVersion(expected: String?, actual: String) {
        if (expected.isNullOrBlank()) {
            throw TrustReplyWorkbenchException(HttpStatus.BAD_REQUEST, "TRUST_REPLY_EVIDENCE_VERSION_REQUIRED")
        }
        if (expected != actual) {
            throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_EVIDENCE_STALE")
        }
    }

    private fun buildInitialItemVersions(
        requestFacts: List<RequestFactItem>,
        sourceVersion: String,
        evidenceSetVersion: String,
        selectedModel: String,
        itemAnswers: List<AiReplyItemAnswer>
    ): List<TrustReplyItemVersion> {
        val answersByIndex = itemAnswers.associateBy { it.requestIndex }
        return requestFacts.sortedBy { it.index }.mapNotNull { item ->
            val key = requestKey(sourceVersion, item)
            val answer = answersByIndex[item.index]
            if (answer != null && answer.answerText.isNotBlank()) {
                val handling = when (item.status) {
                    RequestGroundingStatus.GROUNDED -> TrustReplyItemHandling.ANSWER_WITH_EVIDENCE
                    RequestGroundingStatus.PARTIAL -> TrustReplyItemHandling.ANSWER_SUPPORTED_PART
                    RequestGroundingStatus.UNSUPPORTED -> return@mapNotNull null
                }
                materializeVersion(
                    item = item,
                    requestKey = key,
                    handling = handling,
                    answerText = answer.answerText,
                    claims = answer.claims,
                    model = selectedModel,
                    generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                    evidenceSetVersion = evidenceSetVersion,
                    sourceVersion = sourceVersion
                )
            } else {
                null
            }
        }
    }

    private fun requestKey(sourceVersion: String, item: RequestFactItem): String = requestKey(
        sourceVersion = sourceVersion,
        index = item.index,
        requestText = item.requestText,
        intentKeys = item.intents.map { it.intentKey }
    )

    private fun bootstrapReadiness(selection: ResolvedQaRules): String =
        if (selection.requestFacts.any { it.status == RequestGroundingStatus.UNSUPPORTED }) "BLOCKED" else "READY"

    private fun availableFactMetadata(): List<TrustReplyRuleMetadata> =
        qaRuleRepository.findAllEnabledOrdered()
            .asSequence()
            .filter { it.isMatchable() && it.answerBody.trim().isNotBlank() }
            .mapNotNull { rule ->
                rule.id?.let { id ->
                    TrustReplyRuleMetadata(
                        ruleId = id,
                        displayName = rule.displayName?.takeIf { it.isNotBlank() } ?: "未命名事实",
                        categoryId = rule.categoryId,
                        answerBody = rule.answerBody
                    )
                }
            }
            .toList()

    private fun mergeWarnings(first: List<String>, second: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        return (first + second).filter { seen.add(it) }
    }

    private fun List<RequestFactItem>.toCoverage(sourceVersion: String): List<TrustReplyRequestCoverage> = map { item ->
        TrustReplyRequestCoverage(
            index = item.index,
            requestText = item.requestText,
            status = item.status.name,
            factRuleIds = item.factRuleIds,
            intents = item.intents.map { intent ->
                TrustReplyIntentCoverage(
                    intentKey = intent.intentKey,
                    title = intent.title,
                    status = intent.status,
                    evidenceRuleIds = intent.evidenceRuleIds,
                    missingEvidenceKeys = intent.missingEvidenceKeys,
                    requiresResearchContext = intent.requiresResearchContext
                )
            },
            requestKey = requestKey(sourceVersion, item),
            allowedHandlings = allowedHandlings(item.status).map { it.name },
            recommendedHandling = recommendedHandling(item.status).name
        )
    }

    private fun MailRecord.inboundText(): String = cleanedBody?.takeIf { it.isNotBlank() } ?: body.orEmpty()

    private fun InboundMailProcessing.inboundText(): String = cleanedBody?.takeIf { it.isNotBlank() } ?: body.orEmpty()

    companion object {
        fun allowedHandlings(status: RequestGroundingStatus): List<TrustReplyItemHandling> = when (status) {
            RequestGroundingStatus.GROUNDED -> listOf(
                TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                TrustReplyItemHandling.OMIT
            )
            RequestGroundingStatus.PARTIAL -> listOf(
                TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            )
            RequestGroundingStatus.UNSUPPORTED -> listOf(
                TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            )
        }

        fun recommendedHandling(status: RequestGroundingStatus): TrustReplyItemHandling = when (status) {
            RequestGroundingStatus.GROUNDED -> TrustReplyItemHandling.ANSWER_WITH_EVIDENCE
            RequestGroundingStatus.PARTIAL -> TrustReplyItemHandling.ANSWER_SUPPORTED_PART
            RequestGroundingStatus.UNSUPPORTED -> TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT
        }

        fun requireAllowedHandling(status: RequestGroundingStatus, handling: TrustReplyItemHandling) {
            require(handling in allowedHandlings(status)) { "handling is not allowed for request status" }
        }

        fun requestKey(
            sourceVersion: String,
            index: Int,
            requestText: String,
            intentKeys: List<String>
        ): String {
            val canonical = listOf(
                sourceVersion,
                index.toString(),
                requestText.replace(Regex("\\s+"), " ").trim(),
                intentKeys.joinToString("\\u0001")
            ).joinToString("\\u0000")
            return sha256Hex(canonical).take(32)
        }

        fun versionId(
            requestKey: String,
            handling: TrustReplyItemHandling,
            answerText: String,
            claims: List<AiReplyItemClaim>,
            model: String,
            generationKind: TrustReplyItemGenerationKind,
            evidenceSetVersion: String,
            sourceVersion: String,
            operatorInstructionHash: String
        ): String {
            val canonicalClaims = claims.joinToString("\\u0001") { claim ->
                listOf(claim.intentKey, claim.text, claim.sourceRuleIds.joinToString(",")).joinToString("\\u0002")
            }
            val canonical = listOf(
                requestKey, handling.name, answerText, canonicalClaims, model, generationKind.name,
                evidenceSetVersion, sourceVersion, operatorInstructionHash
            ).joinToString("\\u0000")
            return sha256Hex(canonical)
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
