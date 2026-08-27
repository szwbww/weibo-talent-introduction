package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.audit.domain.OperatorActionLog
import com.weibo.talentintroduction.audit.service.OperatorActionLogService
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.domain.ExpertEmailAlias
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.llm.controller.RequestCoverageItem
import com.weibo.talentintroduction.llm.controller.IntentCoverageResponse
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewResult
import com.weibo.talentintroduction.mail.service.AutoReplyPreviewService
import com.weibo.talentintroduction.mail.service.CandidateSuggestion
import com.weibo.talentintroduction.mail.service.PendingMailOperationService
import com.weibo.talentintroduction.mail.service.ComposedReplyRequest
import com.weibo.talentintroduction.mail.service.PendingManualRichReplyRequest
import com.weibo.talentintroduction.mail.service.PendingMailSendResult
import com.weibo.talentintroduction.mail.service.PendingQaReplyRequest
import com.weibo.talentintroduction.mail.service.UnmatchedInboundMailService
import com.weibo.talentintroduction.qa.service.CategoryRulesGroup
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.SuggestQaRule
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.service.AiReplyDraftReadiness
import com.weibo.talentintroduction.mail.service.ComposedReplyEvaluateRequest
import com.weibo.talentintroduction.mail.service.TrustWorkbenchEvaluateResult
import com.weibo.talentintroduction.mail.service.TrustWorkbenchSuggestResult
import com.weibo.talentintroduction.mail.service.AiReplyPreflightRequest
import com.weibo.talentintroduction.mail.service.AiReplyPreflightResult
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.server.ResponseStatusException
import com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import com.weibo.talentintroduction.llm.service.AiReplyCancellationToken
import com.weibo.talentintroduction.llm.service.AiReplyGenerationCancelledException
import com.weibo.talentintroduction.llm.service.AiReplyProgressPhase
import com.weibo.talentintroduction.llm.service.AiReplyProgressReporter
import com.weibo.talentintroduction.llm.service.AiReplyProgressSnapshot
import com.weibo.talentintroduction.llm.service.AiReplyProgressTracker
import com.weibo.talentintroduction.llm.service.AiReplyTimeoutPolicy
@RestController
@RequestMapping("/api/mail")
class UnmatchedInboundMailController(
    private val unmatchedInboundMailService: UnmatchedInboundMailService,
    private val expertEmailAliasService: ExpertEmailAliasService,
    private val expertContactRepository: com.weibo.talentintroduction.campaign.repository.ExpertContactRepository,
    private val pendingMailOperationService: PendingMailOperationService,
    private val operatorActionLogService: OperatorActionLogService,
    private val llmProperties: LlmProperties,
    private val autoReplyPreviewService: AutoReplyPreviewService,
    private val aiReplyDraftService: com.weibo.talentintroduction.llm.service.AiReplyDraftService,
    private val aiReplyDraftPreviewService: com.weibo.talentintroduction.llm.service.AiReplyDraftPreviewService,
    private val aiReplyContextBuilder: com.weibo.talentintroduction.llm.service.AiReplyContextBuilder,
    private val aiTrainingQaService: com.weibo.talentintroduction.llm.service.AiTrainingQaService,
    private val mailRecordRepository: MailRecordRepository,
    private val aiReplyContextService: com.weibo.talentintroduction.llm.service.AiReplyContextService,
    private val aiReplyReviewAuditService: com.weibo.talentintroduction.llm.service.AiReplyReviewAuditService,
    @Qualifier("aiReplyStreamExecutor") private val aiReplyStreamExecutor: ExecutorService =
        Executors.newFixedThreadPool(2),
    @Qualifier("aiReplyStreamScheduler") private val aiReplyStreamScheduler: ScheduledExecutorService =
        Executors.newScheduledThreadPool(2)
) {
    private val generations = ConcurrentHashMap<String, GenerationControl>()
    private val logger = org.slf4j.LoggerFactory.getLogger(UnmatchedInboundMailController::class.java)
    @GetMapping("/unmatched-inbound")
    fun list(
        @RequestParam(required = false) reasonType: String?,
        @RequestParam(required = false) email: String?,
        @RequestParam(required = false) subject: String?,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
        @RequestParam(required = false, defaultValue = "0") pageOffset: Int
    ): InboundMailProcessingListResponse {
        val result = unmatchedInboundMailService.listManualReviewQueue(
            reasonType = reasonType,
            email = email,
            subject = subject,
            pageSize = pageSize,
            pageOffset = pageOffset
        )
        val contactIds = result.records.mapNotNull { it.expertContactId }.distinct()
        val contactsMap = if (contactIds.isNotEmpty()) {
            expertContactRepository.findAllById(contactIds).associateBy { it.id }
        } else {
            emptyMap()
        }
        return InboundMailProcessingListResponse(
            records = result.records.map { record ->
                val contact = record.expertContactId?.let { contactsMap[it] }
                record.toResponse(
                    expertName = contact?.expertName,
                    expertCurrentStatus = contact?.currentStatus,
                    expertOperatorStatus = contact?.operatorStatus,
                    expertIndexLevel = contact?.currentIndexLevel
                )
            },
            totalCount = result.totalCount,
            manualReviewTotal = result.manualReviewTotal,
            countsByReasonType = result.countsByReasonType
        )
    }

    @GetMapping("/unmatched-inbound/{id}")
    fun getUnmatchedDetail(@PathVariable id: Long): UnmatchedDetailResponse {
        val record = unmatchedInboundMailService.getDetail(id)
        val candidates = unmatchedInboundMailService.suggestCandidates(record)
        val contact = record.expertContactId?.let { expertContactRepository.findById(it).orElse(null) }
        val logs = operatorActionLogService.search(
            expertContactId = null,
            inboundProcessingId = id,
            actionType = null,
            operatorName = null,
            start = null,
            end = null,
            pageSize = 50,
            pageOffset = 0
        ).first
        return UnmatchedDetailResponse(
            record = record.toResponse(
                expertName = contact?.expertName,
                expertCurrentStatus = contact?.currentStatus
            ),
            candidates = candidates.map { it.toResponse() },
            contact = contact?.toPendingMailContactResponse(),
            logs = logs.map { it.toResponse() }
        )
    }

    @PostMapping("/unmatched-inbound/{id}/bind")
    fun bindUnmatched(
        @PathVariable id: Long,
        @RequestBody request: BindUnmatchedRequest
    ): InboundMailProcessingResponse {
        val result = unmatchedInboundMailService.bindToContact(
            recordId = id,
            contactId = request.contactId,
            resolvedBy = request.resolvedBy,
            promoteToApplication = request.promoteToApplication ?: false
        )
        return result.toResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/mark-resolved")
    fun markResolved(
        @PathVariable id: Long,
        @RequestBody request: MarkResolvedRequest
    ) {
        val actualOperator = request.operatorName?.takeIf { it.isNotBlank() }
            ?: request.resolvedBy
            ?: "UNKNOWN"
        pendingMailOperationService.markResolved(
            inboundProcessingId = id,
            resolvedBy = actualOperator,
            operatorName = actualOperator,
            note = request.note
        )
    }

    @PostMapping("/unmatched-inbound/{id}/cancel-resolved")
    fun cancelResolved(
        @PathVariable id: Long,
        @RequestBody request: CancelResolvedRequest
    ) {
        pendingMailOperationService.cancelResolved(
            inboundProcessingId = id,
            operatorName = request.operatorName,
            note = request.note
        )
    }

    @GetMapping("/unmatched-inbound/search-contacts")
    fun searchContacts(@RequestParam query: String): List<CandidateResponse> {
        val contacts = unmatchedInboundMailService.searchContacts(query)
        return contacts.map {
            CandidateResponse(
                contactId = it.id ?: 0,
                orcidId = it.orcidId,
                expertName = it.expertName,
                expertEmail = it.expertEmail,
                reason = "SEARCH",
                confidence = 100
            )
        }
    }

    @PostMapping("/unmatched-inbound/{id}/operator-status")
    fun changeOperatorStatus(
        @PathVariable id: Long,
        @RequestBody request: OperatorStatusChangeRequest
    ): PendingMailContactResponse {
        val contact = pendingMailOperationService.changeOperatorStatus(
            inboundProcessingId = id,
            operatorStatus = request.operatorStatus,
            operatorName = request.operatorName,
            note = request.note
        )
        return contact.toPendingMailContactResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/index-level")
    fun changeIndexLevel(
        @PathVariable id: Long,
        @RequestBody request: IndexLevelChangeRequest
    ): PendingMailContactResponse {
        val contact = pendingMailOperationService.changeIndexLevel(
            inboundProcessingId = id,
            targetLevel = request.targetLevel,
            operatorName = request.operatorName,
            note = request.note
        )
        return contact.toPendingMailContactResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/qa-reply")
    fun sendQaReply(
        @PathVariable id: Long,
        @RequestBody request: PendingQaReplyRequest
    ): PendingMailSendResult =
        throw ResponseStatusException(
            HttpStatus.GONE,
            "Use trust workbench and manual-rich-reply"
        )

    @PostMapping("/unmatched-inbound/{id}/manual-rich-reply")
    fun sendManualRichReply(
        @PathVariable id: Long,
        @RequestBody request: PendingManualRichReplyRequest
    ): PendingMailSendResult =
        pendingMailOperationService.sendManualRichReply(
            inboundProcessingId = id,
            senderAccountCode = request.senderAccountCode,
            subject = request.subject,
            htmlBody = request.htmlBody,
            textBody = request.textBody,
            operatorName = request.operatorName,
            qaRuleIds = request.qaRuleIds,
            suggestedRuleIds = request.suggestedRuleIds,
            ackSnippetId = request.ackSnippetId,
            edited = request.edited,
            freeTextPreview = request.freeTextPreview,
            useVariants = request.useVariants,
            templateTextBody = request.templateTextBody,
            templateHtmlBody = request.templateHtmlBody,
            trustReplyAssembly = request.trustReplyAssembly,
            safetyWarningConfirmed = request.safetyWarningConfirmed,
            strongConfirmationText = request.strongConfirmationText
        )

    @GetMapping("/unmatched-inbound/{id}/auto-reply-preview")
    fun previewAutoReply(@PathVariable id: Long): AutoReplyPreviewResponse =
        autoReplyPreviewService.preview(id).toResponse()

    @GetMapping("/unmatched-inbound/{id}/composed-reply/suggest")
    fun suggestComposedReply(
        @PathVariable id: Long
    ): ComposedReplySuggestResponse {
        val suggest = pendingMailOperationService.suggestComposedReply(id)
        return suggest.toResponse(llmEnabled = llmProperties.enabled)
    }

    @PostMapping("/unmatched-inbound/{id}/composed-reply/evaluate")
    fun evaluateComposedReply(
        @PathVariable id: Long,
        @RequestBody request: ComposedReplyEvaluateRequest
    ): ComposedReplyEvaluateResponse {
        val result = pendingMailOperationService.evaluateComposedReply(id, request.factRuleIds)
        return result.toResponse()
    }

    @PostMapping("/unmatched-inbound/{id}/composed-reply/preflight")
    fun preflightEditedAiReply(
        @PathVariable id: Long,
        @RequestBody request: AiReplyPreflightRequest
    ): AiReplyPreflightResponse {
        val result = pendingMailOperationService.preflightEditedAiReply(
            inboundProcessingId = id,
            factRuleIds = request.factRuleIds,
            expectedEvidenceSetVersion = request.expectedEvidenceSetVersion,
            textBody = request.textBody
        )
        return AiReplyPreflightResponse(
            status = result.status,
            warningCodes = result.warningCodes,
            canonicalFactIds = result.canonicalFactIds,
            evidenceReadiness = result.evidenceReadiness,
            currentEvidenceSetVersion = result.currentEvidenceSetVersion,
            checkedTextHash = result.checkedTextHash
        )
    }

    @PostMapping("/unmatched-inbound/{id}/composed-reply/polish")
    fun polishComposedReply(
        @PathVariable id: Long,
        @RequestBody @Suppress("UNUSED_PARAMETER") request: DeprecatedPolishDraftRequest
    ): Nothing =
        throw ResponseStatusException(
            HttpStatus.GONE,
            "Use trust workbench and manual-rich-reply"
        )

    @PostMapping("/unmatched-inbound/{id}/composed-reply")
    fun sendComposedReply(
        @PathVariable id: Long,
        @RequestBody request: ComposedReplyRequest
    ): PendingMailSendResult =
        throw ResponseStatusException(
            HttpStatus.GONE,
            "Use trust workbench and manual-rich-reply"
        )

    @PostMapping("/unmatched-inbound/{id}/ai-reply/turn")
    fun aiReplyTurn(
        @PathVariable id: Long,
        @RequestBody request: AiReplyTurnRequest
    ): AiReplyTurnResponse = executeAiReplyTurn(id, request)

    private fun executeAiReplyTurn(
        id: Long,
        request: AiReplyTurnRequest,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP,
        beforeCommit: (() -> Boolean)? = null
    ): AiReplyTurnResponse {
        val detail = unmatchedInboundMailService.getDetail(id)
        val inboundText = detail.cleanedBody?.takeIf { it.isNotBlank() } ?: detail.body.orEmpty()
        val turns = request.turns.map {
            com.weibo.talentintroduction.llm.service.AiReplyTurn(
                assistantDraft = it.assistantDraft,
                operatorInstruction = it.operatorInstruction
            )
        }
        val contactId = detail.expertContactId
            ?: throw IllegalArgumentException("Inbound processing $id has no expertContactId")
        val contact = expertContactRepository.findById(contactId).orElse(null)
            ?: throw IllegalArgumentException("Expert contact not found: $contactId")
        val isContinuation = request.turns.isNotEmpty()
        val records = mailRecordRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)
        val knowledge = aiTrainingQaService.buildKnowledgeContext(inboundText)
        val context = aiReplyContextService.build(contact, records, inboundText, knowledge, detail.messageId)

        val result = if (request.llmAttemptTimeoutSeconds == null && request.llmTotalTimeoutSeconds == null &&
            cancellationToken == null && progressReporter === AiReplyProgressReporter.NOOP
        ) {
            aiReplyDraftService.generate(
                inboundText = inboundText,
                operatorTurns = turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = context.profileText,
                mailHistory = context.mailHistory,
                contextWarnings = context.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = context.researchProfileSufficient
            )
        } else {
            aiReplyDraftService.generate(
                inboundText = inboundText,
                operatorTurns = turns,
                qaRuleIds = request.qaRuleIds,
                operatorInstruction = request.operatorInstruction,
                expertProfile = context.profileText,
                mailHistory = context.mailHistory,
                contextWarnings = context.contextWarnings,
                replyModel = request.model,
                researchProfileSufficient = context.researchProfileSufficient,
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

        val snapshot = if (!isContinuation) {
            aiReplyReviewAuditService.recordInitialDraft(
                inboundProcessingId = id,
                contactId = contactId,
                result = result,
                operatorName = request.operatorName
            )
        } else {
            aiReplyReviewAuditService.buildSnapshot(result)
        }

        val preview = aiReplyDraftPreviewService.preview(
            raw = result.draftText,
            contact = contact,
            senderAccountCode = detail.senderAccountCode
        )
        return AiReplyTurnResponse(
            draftText = result.draftText,
            renderedDraftText = preview.renderedText,
            usedLlm = result.usedLlm,
            llmEnabled = llmProperties.enabled,
            qaRuleIds = result.qaRuleIds,
            mode = result.mode.name,
            requestCount = result.requestCount,
            groundedRequestCount = result.groundedRequestCount,
            unsupportedRequests = result.unsupportedRequests,
            contextWarnings = mergeWarningsPreserveOrder(result.contextWarnings, preview.warningCodes),
            injectedDialogRefs = result.fewShotDialogRefs,
            selectedModel = result.selectedModel,
            requestCoverage = result.requestFacts.map {
                RequestCoverageItem(
                    index = it.index,
                    requestText = it.requestText,
                    status = it.status.name,
                    factRuleIds = it.factRuleIds,
                    intents = it.intents.map { intent ->
                        IntentCoverageResponse(
                            intentKey = intent.intentKey,
                            title = intent.title,
                            status = intent.status,
                            evidenceRuleIds = intent.evidenceRuleIds,
                            missingEvidenceKeys = intent.missingEvidenceKeys,
                            requiresResearchContext = intent.requiresResearchContext
                        )
                    }
                )
            },
            generationState = result.generationState.name,
            draftReadiness = result.draftReadiness.name,
            promptVersion = result.promptVersion,
            draftHash = snapshot.draftHash,
            evidenceSetVersion = result.evidenceSetVersion,
            appliedLlmAttemptTimeoutSeconds = AiReplyTimeoutPolicy.resolve(
                request.llmAttemptTimeoutSeconds,
                request.llmTotalTimeoutSeconds
            ).attemptTimeoutSeconds,
            appliedLlmTotalTimeoutSeconds = AiReplyTimeoutPolicy.resolve(
                request.llmAttemptTimeoutSeconds,
                request.llmTotalTimeoutSeconds
            ).totalTimeoutSeconds,
            evidenceSources = result.evidenceSources.map {
                AiReplyEvidenceResponseItem(
                    ruleId = it.ruleId,
                    displayName = it.displayName,
                    updatedAt = it.updatedAt,
                    answerBodyHash = it.answerBodySha256,
                    available = it.available
                )
            }
        )
    }

    private fun mergeWarningsPreserveOrder(existing: List<String>, extra: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        return (existing + extra).filter { seen.add(it) }
    }

    @PostMapping(
        "/unmatched-inbound/{id}/ai-reply/turn-stream",
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE]
    )
    fun aiReplyTurnStream(
        @PathVariable id: Long,
        @RequestBody request: AiReplyTurnRequest
    ): ResponseEntity<SseEmitter> {
        val generationId = request.generationId?.trim()
            ?: throw IllegalArgumentException("generationId is required for streaming AI reply")
        val parsedGenerationId = runCatching { UUID.fromString(generationId) }.getOrNull()
        require(parsedGenerationId != null && parsedGenerationId.toString() == generationId) {
            "generationId must be a canonical UUID"
        }
        val policy = AiReplyTimeoutPolicy.resolve(
            request.llmAttemptTimeoutSeconds,
            request.llmTotalTimeoutSeconds
        )
        val emitter = SseEmitter(policy.totalTimeoutSeconds * 1000L + 30_000L)
        val token = AiReplyCancellationToken()
        val control = GenerationControl(id, generationId, token, emitter, aiReplyStreamScheduler)
        synchronized(generations) {
            if (generations.size >= MAX_ACTIVE_GENERATIONS) {
                throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI generation queue is full")
            }
            if (generations.putIfAbsent(generationId, control) != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "generationId is already active")
            }
        }
        val tracker = AiReplyProgressTracker(
            generationId = generationId,
            attemptTimeoutSeconds = policy.attemptTimeoutSeconds,
            totalTimeoutSeconds = policy.totalTimeoutSeconds,
            sink = { snapshot -> control.publishProgress(snapshot) }
        )
        control.onCleanup = { generations.remove(generationId, control) }
        emitter.onTimeout { control.disconnect() }
        emitter.onCompletion { control.disconnect() }
        emitter.onError { control.disconnect() }
        control.sendEvent(
            "ready",
            mapOf(
                "generationId" to generationId,
                "appliedLlmAttemptTimeoutSeconds" to policy.attemptTimeoutSeconds,
                "appliedLlmTotalTimeoutSeconds" to policy.totalTimeoutSeconds,
                "progress" to queuedProgress(generationId, policy)
            )
        )
        control.heartbeatFuture = aiReplyStreamScheduler.scheduleAtFixedRate({
            control.sendHeartbeat(tracker.snapshotNow())
        }, 10L, 10L, TimeUnit.SECONDS)
        try {
            control.workerFuture = aiReplyStreamExecutor.submit {
                control.markRunning()
                try {
                    val response = executeAiReplyTurn(
                        id = id,
                        request = request,
                        cancellationToken = token,
                        progressReporter = tracker,
                        beforeCommit = { control.tryBeginCommit() }
                    )
                    control.sendTerminal("result", response)
                } catch (_: AiReplyGenerationCancelledException) {
                    control.sendTerminal("cancelled", mapOf("generationId" to generationId))
                } catch (ex: Exception) {
                    // I-1: 只对已知业务异常透出真实 code；其余固定码，不泄露异常原文。
                    val code = (ex as? com.weibo.talentintroduction.llm.service.TrustReplyWorkbenchException)?.code ?: com.weibo.talentintroduction.llm.service.AiReplyGenerationCoordinator.CODE_GENERATION_FAILED
                    logger.warn("AI reply generation failed: generationId={}, code={}", generationId, code, ex)
                    control.sendTerminal("error", mapOf("generationId" to generationId, "code" to code, "message" to "AI generation failed"))
                } finally {
                    control.cleanup()
                }
            }
        } catch (_: RejectedExecutionException) {
            control.sendTerminal(
                "error",
                mapOf("generationId" to generationId, "message" to "AI generation queue is full")
            )
            control.cleanup()
        }
        val headers = org.springframework.http.HttpHeaders().apply {
            cacheControl = "no-cache, no-transform"
            set("X-Accel-Buffering", "no")
        }
        return ResponseEntity.ok().headers(headers).body(emitter)
    }

    @PostMapping("/unmatched-inbound/{id}/ai-reply/generations/{generationId}/cancel")
    fun cancelAiReplyGeneration(
        @PathVariable id: Long,
        @PathVariable generationId: String
    ): Map<String, String> {
        val control = generations[generationId]
        val status = if (control == null || control.inboundProcessingId != id) {
            "NOT_ACTIVE"
        } else {
            control.requestCancel()
        }
        return mapOf("generationId" to generationId, "status" to status)
    }

    private fun queuedProgress(
        generationId: String,
        policy: AiReplyTimeoutPolicy
    ): Map<String, Any> = mapOf(
        "generationId" to generationId,
        "progressSeq" to 0,
        "phase" to AiReplyProgressPhase.QUEUED.name,
        "providerActivity" to "IDLE",
        "providerCallIndex" to 0,
        "attemptElapsedSeconds" to 0,
        "attemptTimeoutSeconds" to policy.attemptTimeoutSeconds,
        "totalElapsedSeconds" to 0,
        "totalTimeoutSeconds" to policy.totalTimeoutSeconds,
        "providerEventCount" to 0,
        "contentChars" to 0,
        "secondsSinceProviderActivity" to 0
    )

    companion object {
        private const val MAX_ACTIVE_GENERATIONS = 40
    }
}

private enum class GenerationStatus { REGISTERED, RUNNING, COMMITTING, CANCEL_REQUESTED, FINISHED }

private class GenerationControl(
    val inboundProcessingId: Long,
    val generationId: String,
    val token: AiReplyCancellationToken,
    private val emitter: SseEmitter,
    private val progressScheduler: ScheduledExecutorService
) {
    @Volatile
    var workerFuture: Future<*>? = null
    @Volatile
    var heartbeatFuture: ScheduledFuture<*>? = null
    @Volatile
    var onCleanup: (() -> Unit)? = null
    @Volatile
    private var status: GenerationStatus = GenerationStatus.REGISTERED
    private val terminalSent = AtomicBoolean(false)
    private val cleanupDone = AtomicBoolean(false)
    private val sendLock = Any()
    private val progressStateLock = Any()
    private var pendingProgress: AiReplyProgressSnapshot? = null
    private var progressFlushFuture: ScheduledFuture<*>? = null
    private var progressSendInFlight = false
    private var lastProgressSentAt = Long.MIN_VALUE
    private var lastProgressPhase: AiReplyProgressPhase? = null

    fun markRunning() {
        synchronized(sendLock) {
            if (status == GenerationStatus.REGISTERED) status = GenerationStatus.RUNNING
        }
    }

    fun tryBeginCommit(): Boolean = synchronized(sendLock) {
        if (status != GenerationStatus.RUNNING) return@synchronized false
        status = GenerationStatus.COMMITTING
        true
    }

    fun publishProgress(snapshot: AiReplyProgressSnapshot) {
        synchronized(progressStateLock) {
            if (!isProgressActive()) return
            pendingProgress = snapshot
            if (progressSendInFlight) return
            val delay = progressDelayNanos(snapshot)
            if (progressFlushFuture != null && progressFlushFuture?.isDone != true) {
                if (delay != 0L) return
                progressFlushFuture?.cancel(false)
            }
            scheduleProgressFlushLocked(delay)
        }
    }

    private fun flushProgress() {
        val snapshot = synchronized(progressStateLock) {
            progressFlushFuture = null
            if (!isProgressActive()) {
                pendingProgress = null
                null
            } else {
                pendingProgress.also {
                    pendingProgress = null
                    progressSendInFlight = it != null
                }
            }
        } ?: return
        val sent = synchronized(sendLock) {
            isProgressActive() && sendLocked("progress", progressMap(snapshot))
        }
        synchronized(progressStateLock) {
            progressSendInFlight = false
            if (sent) {
                lastProgressSentAt = System.nanoTime()
                lastProgressPhase = snapshot.phase
            }
            pendingProgress?.takeIf { sent && isProgressActive() }?.let {
                scheduleProgressFlushLocked(progressDelayNanos(it))
            }
        }
    }

    private fun progressDelayNanos(snapshot: AiReplyProgressSnapshot): Long {
        if (lastProgressPhase != snapshot.phase || lastProgressSentAt == Long.MIN_VALUE) return 0L
        return (1_000_000_000L - (System.nanoTime() - lastProgressSentAt)).coerceAtLeast(0L)
    }

    private fun scheduleProgressFlushLocked(delayNanos: Long) {
        progressFlushFuture = progressScheduler.schedule(
            { flushProgress() }, delayNanos, TimeUnit.NANOSECONDS
        )
    }

    private fun isProgressActive(): Boolean =
        status == GenerationStatus.RUNNING || status == GenerationStatus.REGISTERED

    fun sendHeartbeat(snapshot: AiReplyProgressSnapshot?) {
        if (snapshot == null) return
        synchronized(sendLock) {
            if (status != GenerationStatus.RUNNING) return
            sendLocked("heartbeat", mapOf("generationId" to generationId, "progress" to progressMap(snapshot)))
        }
    }

    fun sendEvent(name: String, data: Any) {
        synchronized(sendLock) { sendLocked(name, data) }
    }

    fun sendTerminal(name: String, data: Any) {
        if (!terminalSent.compareAndSet(false, true)) return
        synchronized(sendLock) {
            status = GenerationStatus.FINISHED
            sendLocked(name, data)
            runCatching { emitter.complete() }
        }
    }

    fun requestCancel(): String {
        val active = synchronized(sendLock) {
            when (status) {
                GenerationStatus.REGISTERED, GenerationStatus.RUNNING -> {
                    status = GenerationStatus.CANCEL_REQUESTED
                    true
                }
                GenerationStatus.COMMITTING -> return "TOO_LATE"
                GenerationStatus.CANCEL_REQUESTED, GenerationStatus.FINISHED -> return "NOT_ACTIVE"
            }
        }
        if (active) {
            token.cancel()
            workerFuture?.cancel(true)
            heartbeatFuture?.cancel(false)
            sendTerminal("cancelled", mapOf("generationId" to generationId))
            cleanup()
            return "CANCEL_REQUESTED"
        }
        return "NOT_ACTIVE"
    }

    fun disconnect() {
        synchronized(sendLock) {
            status = GenerationStatus.FINISHED
            terminalSent.set(true)
        }
        token.cancel()
        workerFuture?.cancel(true)
        heartbeatFuture?.cancel(false)
        cleanup()
    }

    fun cleanup() {
        if (!cleanupDone.compareAndSet(false, true)) return
        heartbeatFuture?.cancel(false)
        synchronized(progressStateLock) {
            progressFlushFuture?.cancel(false)
            progressFlushFuture = null
            pendingProgress = null
            progressSendInFlight = false
        }
        onCleanup?.invoke()
    }

    private fun sendLocked(name: String, data: Any): Boolean {
        if (terminalSent.get() && name != "result" && name != "cancelled" && name != "error") return false
        return try {
            emitter.send(SseEmitter.event().name(name).data(data))
            true
        } catch (_: Exception) {
            status = GenerationStatus.FINISHED
            terminalSent.set(true)
            token.cancel()
            workerFuture?.cancel(true)
            heartbeatFuture?.cancel(false)
            cleanup()
            false
        }
    }

    private fun progressMap(snapshot: AiReplyProgressSnapshot): Map<String, Any> = mapOf(
        "generationId" to snapshot.generationId,
        "progressSeq" to snapshot.progressSeq,
        "phase" to snapshot.phase.name,
        "providerActivity" to snapshot.providerActivity.name,
        "providerCallIndex" to snapshot.providerCallIndex,
        "attemptElapsedSeconds" to snapshot.attemptElapsedSeconds,
        "attemptTimeoutSeconds" to snapshot.attemptTimeoutSeconds,
        "totalElapsedSeconds" to snapshot.totalElapsedSeconds,
        "totalTimeoutSeconds" to snapshot.totalTimeoutSeconds,
        "providerEventCount" to snapshot.providerEventCount,
        "contentChars" to snapshot.contentChars,
        "secondsSinceProviderActivity" to snapshot.secondsSinceProviderActivity
    )
}

@RestController
@RequestMapping("/api/expert-contacts")
class ExpertEmailAliasController(
    private val expertEmailAliasService: ExpertEmailAliasService
) {
    @GetMapping("/{contactId}/email-aliases")
    fun listAliases(@PathVariable contactId: Long): List<ExpertEmailAliasResponse> =
        expertEmailAliasService.listAliases(contactId).map { it.toResponse() }

    @PostMapping("/{contactId}/email-aliases")
    fun addAlias(
        @PathVariable contactId: Long,
        @RequestBody request: AddAliasRequest
    ): ExpertEmailAliasResponse =
        expertEmailAliasService.addAlias(
            expertContactId = contactId,
            email = request.email,
            source = request.source ?: "MANUAL_ADD"
        ).toResponse()

    @DeleteMapping("/{contactId}/email-aliases/{aliasId}")
    fun deleteAlias(
        @PathVariable contactId: Long,
        @PathVariable aliasId: Long
    ) {
        expertEmailAliasService.deleteAlias(aliasId)
    }

}

data class InboundMailProcessingListResponse(
    val records: List<InboundMailProcessingResponse>,
    val totalCount: Long,
    val manualReviewTotal: Long,
    val countsByReasonType: Map<String, Long>
)

data class UnmatchedDetailResponse(
    val record: InboundMailProcessingResponse,
    val candidates: List<CandidateResponse>,
    val contact: PendingMailContactResponse? = null,
    val logs: List<OperatorActionLogResponse> = emptyList()
)

data class InboundMailProcessingResponse(
    val id: Long?,
    val senderAccountCode: String,
    val imapUid: Long,
    val messageId: String?,
    val inReplyTo: String?,
    val fromEmail: String,
    val subject: String?,
    val body: String?,
    val cleanedBody: String?,
    val receivedAt: String?,
    val processStatus: String,
    val processReason: String,
    val reasonType: String?,
    val resolvedAt: String?,
    val resolvedBy: String?,
    val expertContactId: Long?,
    val expertName: String? = null,
    val expertCurrentStatus: String? = null,
    val expertOperatorStatus: String? = null,
    val expertIndexLevel: String? = null
)

data class PendingMailContactResponse(
    val contactId: Long,
    val expertName: String?,
    val expertEmail: String,
    val orcidId: String,
    val currentIndexLevel: String,
    val operatorStatus: String,
    val currentStatus: String,
    val autoReplyEnabled: Boolean
)

data class MarkResolvedRequest(
    val resolvedBy: String?,
    val operatorName: String? = null,
    val note: String?
)

data class CancelResolvedRequest(
    val operatorName: String? = null,
    val note: String? = null
)

data class OperatorStatusChangeRequest(
    val operatorStatus: String,
    val operatorName: String?,
    val note: String?
)

data class IndexLevelChangeRequest(
    val targetLevel: String,
    val operatorName: String?,
    val note: String?
)

data class CandidateResponse(
    val contactId: Long,
    val orcidId: String,
    val expertName: String?,
    val expertEmail: String,
    val reason: String,
    val confidence: Int
)

data class BindUnmatchedRequest(
    val contactId: Long,
    val resolvedBy: String,
    val promoteToApplication: Boolean? = null
)

data class AddAliasRequest(
    val email: String,
    val source: String?
)

data class ExpertEmailAliasResponse(
    val id: Long?,
    val expertContactId: Long,
    val email: String,
    val normalizedEmail: String,
    val source: String,
    val verified: Boolean,
    val createdAt: String?
)

data class OperatorActionLogResponse(
    val id: Long?,
    val actionType: String,
    val actionSummary: String,
    val beforeValue: String?,
    val afterValue: String?,
    val operatorName: String?,
    val note: String?,
    val createdAt: String?
)

data class GapItemResponse(
    val text: String,
    val candidateRuleIds: List<Long>
)

data class AutoReplyPreviewResponse(
    val previewKind: String,
    val intentCode: String,
    val autoAction: String,
    val confidence: Int,
    val matchedKeywords: List<String>,
    val replySubject: String?,
    val replyBody: String?,
    val reason: String?,
    val matchedRuleIds: List<Long>,
    val wouldBeBlockedBy: List<String>,
    val attachmentIntentIgnored: Boolean
)

data class ComposedReplySuggestResponse(
    val suggestedRuleIds: List<Long>,
    val suggestedRules: List<SuggestQaRuleResponse>,
    val rulesByCategory: List<CategoryRulesGroupResponse>,
    val gapItems: List<GapItemResponse>,
    val gapDetected: Boolean,
    val matchedCategoryIds: List<Long>,
    val llmEnabled: Boolean,
    val inboundText: String,
    val draftReadiness: String,
    val requestCoverage: List<RequestCoverageItem>
)

data class ComposedReplyEvaluateResponse(
    val canonicalFactIds: List<Long>,
    val suggestedFactIds: List<Long>,
    val draftReadiness: String,
    val requestCoverage: List<RequestCoverageItem>,
    val gapDetected: Boolean
)

data class DeprecatedPolishDraftRequest(
    val qaRuleIds: List<Long> = emptyList(),
    val operatorInstruction: String? = null
)

data class AckOptionResponse(
    val id: Long,
    val content: String
)

data class SuggestQaRuleResponse(
    val id: Long,
    val categoryId: Long,
    val displayName: String?,
    val sectionTitle: String?,
    val replySubject: String?,
    val replyBody: String,
    val keywords: String
)

data class CategoryRulesGroupResponse(
    val categoryId: Long,
    val categoryCode: String,
    val categoryName: String,
    val composeOrder: Int,
    val rules: List<SuggestQaRuleResponse>
)

data class AiReplyTurnDto(
    val assistantDraft: String,
    val operatorInstruction: String
)

data class AiReplyTurnRequest(
    val turns: List<AiReplyTurnDto> = emptyList(),
    val qaRuleIds: List<Long>? = null,
    val sessionId: String? = null,
    val operatorInstruction: String? = null,
    val operatorName: String? = null,
    val model: String? = null,
    val generationId: String? = null,
    val llmAttemptTimeoutSeconds: Int? = null,
    val llmTotalTimeoutSeconds: Int? = null
)

data class AiReplyTurnResponse(
    val draftText: String,
    val renderedDraftText: String = "",
    val usedLlm: Boolean,
    val llmEnabled: Boolean,
    val qaRuleIds: List<Long>,
    val mode: String,
    val requestCount: Int = 0,
    val groundedRequestCount: Int = 0,
    val unsupportedRequests: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val injectedDialogRefs: List<String> = emptyList(),
    val selectedModel: String = com.weibo.talentintroduction.llm.service.AiReplyModel.DEEPSEEK_V4_FLASH.name,
    val requestCoverage: List<RequestCoverageItem> = emptyList(),
    val generationState: String = "FALLBACK_NO_RESPONSE",
    val draftReadiness: String = "READY",
    val promptVersion: String = "",
    val draftHash: String = "",
    val evidenceSetVersion: String = "",
    val appliedLlmAttemptTimeoutSeconds: Int = 30,
    val appliedLlmTotalTimeoutSeconds: Int = 300,
    val evidenceSources: List<AiReplyEvidenceResponseItem> = emptyList()
)

data class AiReplyEvidenceResponseItem(
    val ruleId: Long,
    val displayName: String,
    val updatedAt: String?,
    val answerBodyHash: String,
    val available: Boolean
)

data class AiReplyPreflightResponse(
    val status: String,
    val warningCodes: List<String>,
    val canonicalFactIds: List<Long>,
    val evidenceReadiness: String,
    val currentEvidenceSetVersion: String,
    val checkedTextHash: String
)

private fun InboundMailProcessing.toResponse(
    expertName: String? = null,
    expertCurrentStatus: String? = null,
    expertOperatorStatus: String? = null,
    expertIndexLevel: String? = null
) = InboundMailProcessingResponse(
    id = id,
    senderAccountCode = senderAccountCode,
    imapUid = imapUid,
    messageId = messageId,
    inReplyTo = inReplyTo,
    fromEmail = fromEmail,
    subject = subject,
    body = body,
    cleanedBody = cleanedBody,
    receivedAt = receivedAt.toString(),
    processStatus = processStatus,
    processReason = processReason,
    reasonType = reasonType,
    resolvedAt = resolvedAt?.toString(),
    resolvedBy = resolvedBy,
    expertContactId = expertContactId,
    expertName = expertName,
    expertCurrentStatus = expertCurrentStatus,
    expertOperatorStatus = expertOperatorStatus,
    expertIndexLevel = expertIndexLevel
)

private fun CandidateSuggestion.toResponse() = CandidateResponse(
    contactId = contact.id ?: 0,
    orcidId = contact.orcidId,
    expertName = contact.expertName,
    expertEmail = contact.expertEmail,
    reason = reason,
    confidence = confidence
)

private fun ExpertEmailAlias.toResponse() = ExpertEmailAliasResponse(
    id = id,
    expertContactId = expertContactId,
    email = email,
    normalizedEmail = normalizedEmail,
    source = source,
    verified = verified,
    createdAt = createdAt?.toString()
)

private fun ExpertContact.toPendingMailContactResponse() = PendingMailContactResponse(
    contactId = id ?: 0,
    expertName = expertName,
    expertEmail = expertEmail,
    orcidId = orcidId,
    currentIndexLevel = currentIndexLevel,
    operatorStatus = operatorStatus,
    currentStatus = currentStatus,
    autoReplyEnabled = autoReplyEnabled
)

private fun OperatorActionLog.toResponse() = OperatorActionLogResponse(
    id = id,
    actionType = actionType,
    actionSummary = actionSummary,
    beforeValue = beforeValue,
    afterValue = afterValue,
    operatorName = operatorName,
    note = note,
    createdAt = createdAt?.toString()
)

private fun AutoReplyPreviewResult.toResponse() = AutoReplyPreviewResponse(
    previewKind = previewKind.name,
    intentCode = intentCode.name,
    autoAction = autoAction.name,
    confidence = confidence,
    matchedKeywords = matchedKeywords,
    replySubject = replySubject,
    replyBody = replyBody,
    reason = reason,
    matchedRuleIds = matchedRuleIds,
    wouldBeBlockedBy = wouldBeBlockedBy,
    attachmentIntentIgnored = attachmentIntentIgnored
)

private fun TrustWorkbenchSuggestResult.toResponse(llmEnabled: Boolean) = ComposedReplySuggestResponse(
    suggestedRuleIds = suggestedRuleIds,
    suggestedRules = suggestedRules.map { it.toResponse() },
    rulesByCategory = rulesByCategory.map { it.toResponse() },
    gapItems = gapItems.map { GapItemResponse(it.text, it.candidateRuleIds) },
    gapDetected = gapDetected,
    matchedCategoryIds = matchedCategoryIds,
    llmEnabled = llmEnabled,
    inboundText = inboundText,
    draftReadiness = draftReadiness,
    requestCoverage = requestCoverage
)

private fun TrustWorkbenchEvaluateResult.toResponse() = ComposedReplyEvaluateResponse(
    canonicalFactIds = canonicalFactIds,
    suggestedFactIds = suggestedFactIds,
    draftReadiness = draftReadiness,
    requestCoverage = requestCoverage,
    gapDetected = gapDetected
)

private fun CompositionSuggestResult.toResponse(
    llmEnabled: Boolean,
    inboundText: String
) = ComposedReplySuggestResponse(
    suggestedRuleIds = suggestedRuleIds,
    suggestedRules = suggestedRules.map { it.toResponse() },
    rulesByCategory = rulesByCategory.map { it.toResponse() },
    gapItems = gapItems.map { GapItemResponse(it.text, it.candidateRuleIds) },
    gapDetected = gapDetected,
    matchedCategoryIds = matchedCategoryIds,
    llmEnabled = llmEnabled,
    inboundText = inboundText,
    draftReadiness = AiReplyDraftReadiness.READY.name,
    requestCoverage = emptyList()
)

private fun SuggestQaRule.toResponse() = SuggestQaRuleResponse(
    id = id,
    categoryId = categoryId,
    displayName = displayName,
    sectionTitle = sectionTitle,
    replySubject = replySubject,
    replyBody = replyBody,
    keywords = keywords
)

private fun CategoryRulesGroup.toResponse() = CategoryRulesGroupResponse(
    categoryId = categoryId,
    categoryCode = categoryCode,
    categoryName = categoryName,
    composeOrder = composeOrder,
    rules = rules.map { it.toResponse() }
)
