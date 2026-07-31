package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

enum class AiReplyMode {
    QA_MATCHED,
    QA_GROUNDED,
    FREE_FORM
}

enum class AiReplyGenerationState {
    LLM_USED,
    FALLBACK_LLM_DISABLED,
    FALLBACK_CLIENT_UNAVAILABLE,
    FALLBACK_NO_RESPONSE
}

enum class AiReplyDraftReadiness {
    READY,
    NEEDS_REVIEW,
    BLOCKED
}

data class AiReplyTimeoutPolicy(
    val attemptTimeoutSeconds: Int,
    val totalTimeoutSeconds: Int
) {
    companion object {
        const val DEFAULT_ATTEMPT_SECONDS = 30
        const val DEFAULT_TOTAL_SECONDS = 300

        fun resolve(attemptSeconds: Int?, totalSeconds: Int?): AiReplyTimeoutPolicy {
            val attempt = attemptSeconds ?: DEFAULT_ATTEMPT_SECONDS
            require(attempt in 10..600) { "llmAttemptTimeoutSeconds must be an integer from 10 to 600" }
            val total = totalSeconds ?: attempt * 10
            require(total in attempt..7200) {
                "llmTotalTimeoutSeconds must be an integer from llmAttemptTimeoutSeconds to 7200"
            }
            return AiReplyTimeoutPolicy(attempt, total)
        }
    }

    fun budget(nowNanos: () -> Long = System::nanoTime): AiReplyGenerationBudget {
        val now = nowNanos()
        return AiReplyGenerationBudget(attemptTimeoutSeconds * 1000L, now + totalTimeoutSeconds * 1_000_000_000L, nowNanos)
    }
}

class AiReplyGenerationBudget(
    val attemptMillis: Long,
    val totalDeadlineNanos: Long,
    private val nowNanos: () -> Long = System::nanoTime
) {
    fun remainingTotalMillis(now: Long = nowNanos()): Long =
        ((totalDeadlineNanos - now).coerceAtLeast(0L)) / 1_000_000L

    fun nextAttemptMillis(now: Long = nowNanos()): Long =
        min(attemptMillis, remainingTotalMillis(now))
}

class AiReplyCancellationToken {
    private val cancelled = AtomicBoolean(false)
    private val listenerIds = AtomicLong()
    private val listeners = ConcurrentHashMap<Long, () -> Unit>()

    fun cancel() {
        if (!cancelled.compareAndSet(false, true)) return
        listeners.values.toList().forEach { listener ->
            runCatching { listener() }
        }
        listeners.clear()
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun onCancel(listener: () -> Unit): AutoCloseable {
        if (isCancelled()) {
            runCatching { listener() }
            return AutoCloseable { }
        }
        val id = listenerIds.incrementAndGet()
        listeners[id] = listener
        if (isCancelled() && listeners.remove(id) != null) {
            runCatching { listener() }
        }
        return AutoCloseable { listeners.remove(id) }
    }

    fun throwIfCancelled() {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw AiReplyGenerationCancelledException()
        }
    }
}

class AiReplyGenerationCancelledException : RuntimeException()

enum class AiReplyProgressPhase { QUEUED, PREPARING, CALLING, VALIDATING, REPAIRING, FINALIZING }

enum class AiReplyProviderActivity { IDLE, WAITING, REASONING, WRITING }

data class AiReplyProgressSnapshot(
    val generationId: String,
    val progressSeq: Long,
    val phase: AiReplyProgressPhase,
    val providerActivity: AiReplyProviderActivity,
    val providerCallIndex: Int,
    val attemptElapsedSeconds: Int,
    val attemptTimeoutSeconds: Int,
    val totalElapsedSeconds: Int,
    val totalTimeoutSeconds: Int,
    val providerEventCount: Int,
    val contentChars: Int,
    val secondsSinceProviderActivity: Int
)

interface AiReplyProgressReporter {
    fun transition(phase: AiReplyProgressPhase)
    fun startBudget(budget: AiReplyGenerationBudget)
    fun beginProviderCall(phase: AiReplyProgressPhase, timeoutMillis: Long): LlmStreamProgressSink
    fun endProviderCall()
    fun snapshotNow(): AiReplyProgressSnapshot?

    companion object {
        val NOOP = object : AiReplyProgressReporter {
            override fun transition(phase: AiReplyProgressPhase) = Unit
            override fun startBudget(budget: AiReplyGenerationBudget) = Unit
            override fun beginProviderCall(phase: AiReplyProgressPhase, timeoutMillis: Long): LlmStreamProgressSink =
                LlmStreamProgressSink.NOOP
            override fun endProviderCall() = Unit
            override fun snapshotNow(): AiReplyProgressSnapshot? = null
        }
    }
}

class AiReplyProgressTracker(
    private val generationId: String,
    private val attemptTimeoutSeconds: Int,
    private val totalTimeoutSeconds: Int,
    private val clock: () -> Long = System::nanoTime,
    private val sink: (AiReplyProgressSnapshot) -> Unit
) : AiReplyProgressReporter {
    private var budget: AiReplyGenerationBudget? = null
    private var phase = AiReplyProgressPhase.QUEUED
    private var activity = AiReplyProviderActivity.IDLE
    private var progressSeq = 0L
    private var providerCallIndex = 0
    private var providerCallStartedAt: Long? = null
    private var providerCallElapsedMillis = 0L
    private var latestProviderActivityAt = clock()
    private var eventCount = 0
    private var contentChars = 0
    private var lastStreamEventCount = 0
    private var lastStreamContentChars = 0
    private var currentAttemptTimeoutMillis = attemptTimeoutSeconds * 1000L
    private var lastPublishedAt = Long.MIN_VALUE
    private val lock = Any()

    override fun startBudget(budget: AiReplyGenerationBudget) {
        synchronized(lock) {
            this.budget = budget
            transitionLocked(AiReplyProgressPhase.PREPARING)
        }
    }

    override fun transition(phase: AiReplyProgressPhase) {
        synchronized(lock) { transitionLocked(phase) }
    }

    private fun transitionLocked(next: AiReplyProgressPhase) {
        phase = next
        publishLocked()
    }

    override fun beginProviderCall(phase: AiReplyProgressPhase, timeoutMillis: Long): LlmStreamProgressSink {
        synchronized(lock) {
            transitionLocked(phase)
            providerCallIndex = if (providerCallIndex == Int.MAX_VALUE) Int.MAX_VALUE else providerCallIndex + 1
            providerCallStartedAt = clock()
            providerCallElapsedMillis = 0L
            currentAttemptTimeoutMillis = timeoutMillis.coerceAtLeast(0L)
            activity = AiReplyProviderActivity.WAITING
            latestProviderActivityAt = clock()
            lastStreamEventCount = 0
            lastStreamContentChars = 0
            publishLocked()
        }
        return LlmStreamProgressSink { streamActivity, events, chars ->
            synchronized(lock) {
                activity = when (streamActivity) {
                    LlmStreamActivity.WAITING -> if (activity == AiReplyProviderActivity.IDLE) {
                        AiReplyProviderActivity.WAITING
                    } else activity
                    LlmStreamActivity.REASONING -> AiReplyProviderActivity.REASONING
                    LlmStreamActivity.WRITING -> AiReplyProviderActivity.WRITING
                }
                val normalizedEvents = events.coerceAtLeast(0)
                val normalizedChars = chars.coerceAtLeast(0)
                val eventDelta = (normalizedEvents - lastStreamEventCount).coerceAtLeast(0)
                val contentDelta = (normalizedChars - lastStreamContentChars).coerceAtLeast(0)
                lastStreamEventCount = maxOf(lastStreamEventCount, normalizedEvents)
                lastStreamContentChars = maxOf(lastStreamContentChars, normalizedChars)
                eventCount = addSaturated(eventCount, eventDelta)
                contentChars = addSaturated(contentChars, contentDelta)
                latestProviderActivityAt = clock()
                publishLocked(clock() - lastPublishedAt >= 1_000_000_000L)
            }
        }
    }

    override fun endProviderCall() {
        synchronized(lock) {
            providerCallStartedAt?.let {
                providerCallElapsedMillis = ((clock() - it) / 1_000_000L).coerceAtLeast(0L)
            }
            providerCallStartedAt = null
            activity = AiReplyProviderActivity.IDLE
            publishLocked()
        }
    }

    override fun snapshotNow(): AiReplyProgressSnapshot? = synchronized(lock) {
        if (budget == null) null else snapshotLocked()
    }

    private fun publishLocked(force: Boolean = true) {
        if (!force || budget == null) return
        lastPublishedAt = clock()
        sink(snapshotLocked())
    }

    private fun snapshotLocked(): AiReplyProgressSnapshot {
        val now = clock()
        val currentBudget = budget
        val totalElapsed = if (currentBudget == null) 0L else {
            ((now - (currentBudget.totalDeadlineNanos - totalTimeoutSeconds * 1_000_000_000L)) / 1_000_000L).coerceAtLeast(0L)
        }
        val attemptElapsed = providerCallStartedAt?.let {
            ((now - it) / 1_000_000L).coerceAtLeast(0L)
        } ?: providerCallElapsedMillis
        return AiReplyProgressSnapshot(
            generationId = generationId,
            progressSeq = ++progressSeq,
            phase = phase,
            providerActivity = activity,
            providerCallIndex = providerCallIndex,
            attemptElapsedSeconds = (attemptElapsed / 1000L).coerceIn(0L, currentAttemptTimeoutMillis / 1000L).toInt(),
            attemptTimeoutSeconds = (currentAttemptTimeoutMillis / 1000L).coerceAtMost(600L).toInt(),
            totalElapsedSeconds = (totalElapsed / 1000L).coerceIn(0L, totalTimeoutSeconds.toLong()).toInt(),
            totalTimeoutSeconds = totalTimeoutSeconds,
            providerEventCount = eventCount,
            contentChars = contentChars,
            secondsSinceProviderActivity = ((now - latestProviderActivityAt) / 1_000_000_000L).coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }

    private fun addSaturated(current: Int, delta: Int): Int =
        (current.toLong() + delta.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

data class AiReplyTurn(
    val assistantDraft: String,
    val operatorInstruction: String
)

data class AiReplyPromptSnapshot(
    val systemPrompt: String,
    val version: String
)

data class AiReplyEvidenceSnapshot(
    val ruleId: Long,
    val displayName: String,
    val updatedAt: String?,
    val answerBodySha256: String,
    val available: Boolean
)

data class AiReplyItemClaim(
    val intentKey: String,
    val text: String,
    val sourceRuleIds: List<Long>
)

data class AiReplyItemAnswer(
    val requestIndex: Int,
    val requestText: String,
    val status: RequestGroundingStatus,
    val answerText: String,
    val claims: List<AiReplyItemClaim>
)

data class AiReplyItemGenerationResult(
    val itemAnswer: AiReplyItemAnswer?,
    val handling: TrustReplyItemHandling,
    val generationKind: TrustReplyItemGenerationKind?,
    val generationState: AiReplyGenerationState,
    val usedLlm: Boolean,
    val lockable: Boolean,
    val warningCodes: List<String> = emptyList()
)

data class AiReplyDraftResult(
    val draftText: String,
    val usedLlm: Boolean,
    val qaRuleIds: List<Long>,
    val mode: AiReplyMode,
    val fewShotDialogRefs: List<String> = emptyList(),
    val requestCount: Int = 0,
    val groundedRequestCount: Int = 0,
    val unsupportedRequests: List<String> = emptyList(),
    val contextWarnings: List<String> = emptyList(),
    val selectedModel: String = AiReplyModel.DEEPSEEK_V4_FLASH.name,
    val requestFacts: List<RequestFactItem> = emptyList(),
    val generationState: AiReplyGenerationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
    val draftReadiness: AiReplyDraftReadiness = AiReplyDraftReadiness.READY,
    val promptVersion: String = "",
    val evidenceSetVersion: String = "",
    val evidenceSources: List<AiReplyEvidenceSnapshot> = emptyList(),
    val validationDiagnostics: AiReplyValidationDiagnostics = AiReplyValidationDiagnostics(),
    val itemAnswers: List<AiReplyItemAnswer> = emptyList()
)

internal data class FreeFormBuildResult(
    val messages: List<LlmChatMessage>,
    val fewShotDialogRefs: List<String>
)

enum class RequestGroundingStatus {
    GROUNDED,
    PARTIAL,
    UNSUPPORTED
}

data class RequestFactItem(
    val index: Int,
    val requestText: String,
    val factRuleIds: List<Long>,
    val status: RequestGroundingStatus,
    val requiresResearchContext: Boolean = false,
    val intents: List<RequestIntentCoverage> = emptyList()
)

data class ResolvedQaRules(
    val sendQaRuleIds: List<Long>,
    val promptRuleIds: List<Long>,
    val requestFacts: List<RequestFactItem> = emptyList(),
    val unsupportedRequests: List<String> = emptyList(),
    val requestCount: Int = 0,
    val groundedRequestCount: Int = 0
)

@Service
class AiReplyDraftService(
    private val properties: LlmProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val qaFactSelectionService: QaFactSelectionService,
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService,
    private val aiPromptConfigService: AiPromptConfigService,
    private val aiTrainingDialogueService: AiTrainingDialogueService,
    private val aiReplyContextService: AiReplyContextService,
    private val aiReplyPointByPointComposer: AiReplyPointByPointComposer,
    private val groundedDraftMaterializer: AiReplyGroundedDraftMaterializer,
    private val claimValidator: AiReplyHighRiskClaimValidator,
    private val contentPlanner: AiReplyGroundedContentPlanner
) {
    private val logger = LoggerFactory.getLogger(AiReplyDraftService::class.java)

    fun generateItem(
        inboundText: String,
        requestFact: RequestFactItem,
        handling: TrustReplyItemHandling,
        requestKey: String? = null,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null,
        contextWarnings: List<String> = emptyList(),
        replyModel: String? = null,
        researchProfileSufficient: Boolean = true,
        llmAttemptTimeoutSeconds: Int? = null,
        llmTotalTimeoutSeconds: Int? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
    ): AiReplyItemGenerationResult {
        val instruction = operatorInstruction?.trim()?.takeIf { it.isNotEmpty() }
        require(instruction == null || instruction.length <= 500) { "operatorInstruction must not exceed 500 characters" }
        validateItemHandling(requestFact.status, handling)
        if (handling == TrustReplyItemHandling.OMIT) {
            return AiReplyItemGenerationResult(
                itemAnswer = null,
                handling = handling,
                generationKind = TrustReplyItemGenerationKind.OMITTED,
                generationState = AiReplyGenerationState.LLM_USED,
                usedLlm = false,
                lockable = true
            )
        }

        if (handling == TrustReplyItemHandling.ACKNOWLEDGE_PENDING) {
            return rejectNonEnglishItemAnswer(generatePendingAcknowledgement(
                inboundText = inboundText,
                requestFact = requestFact,
                requestKey = requestKey,
                operatorInstruction = instruction,
                replyModel = replyModel,
                llmAttemptTimeoutSeconds = llmAttemptTimeoutSeconds,
                llmTotalTimeoutSeconds = llmTotalTimeoutSeconds,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            ))
        }

        if (handling == TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT) {
            return rejectNonEnglishItemAnswer(generateOperatorDirectedAnswer(
                inboundText = inboundText,
                requestFact = requestFact,
                requestKey = requestKey,
                operatorInstruction = instruction,
                expertProfile = expertProfile,
                replyModel = replyModel,
                llmAttemptTimeoutSeconds = llmAttemptTimeoutSeconds,
                llmTotalTimeoutSeconds = llmTotalTimeoutSeconds,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            ))
        }

        val token = cancellationToken ?: AiReplyCancellationToken()
        val policy = AiReplyTimeoutPolicy.resolve(llmAttemptTimeoutSeconds, llmTotalTimeoutSeconds)
        val budget = policy.budget()
        progressReporter.startBudget(budget)
        val selectedModel = AiReplyModel.fromNullable(replyModel)
        val client = llmDraftClientProvider.getIfAvailable()
        if (!properties.enabled || client == null) {
            return AiReplyItemGenerationResult(
                itemAnswer = null,
                handling = handling,
                generationKind = null,
                generationState = if (!properties.enabled) {
                    AiReplyGenerationState.FALLBACK_LLM_DISABLED
                } else {
                    AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
                },
                usedLlm = false,
                lockable = false
            )
        }

        val resolved = ResolvedQaRules(
            sendQaRuleIds = requestFact.factRuleIds.distinct(),
            promptRuleIds = requestFact.factRuleIds.distinct(),
            requestFacts = listOf(requestFact),
            requestCount = 1,
            groundedRequestCount = if (requestFact.status == RequestGroundingStatus.UNSUPPORTED) 0 else 1
        )
        val plan = contentPlanner.buildPlan(
            listOf(requestFact),
            AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
        )
        val build = buildGroundedMessages(
            inboundText = inboundText,
            operatorTurns = emptyList(),
            requestFacts = listOf(requestFact),
            expertProfile = expertProfile,
            mailHistory = mailHistory,
            contextWarnings = contextWarnings,
            operatorInstruction = instruction,
            plan = plan
        )
        val targetMessage = LlmChatMessage(
            role = "user",
            content = "Only process requestKey=${requestKey ?: requestFact.index}. Do not answer any other request."
        )
        val result = try {
            generateGrounded(
                client = client,
                boundedMessages = withActionBoundary(build.messages + targetMessage, plan.allowedActions),
                temperature = properties.temperature,
                providerModel = selectedModel.resolveProviderModel(properties),
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = AiReplyMode.QA_GROUNDED,
                fewShotDialogRefs = build.fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan,
                operatorTurns = emptyList(),
                lastDraft = null,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = instruction,
                promptVersion = resolvePromptSnapshot(AiReplyMode.QA_GROUNDED).version,
                budget = budget,
                cancellationToken = token,
                progressReporter = progressReporter
            )
        } finally {
            progressReporter.endProviderCall()
        }
        val answer = result.itemAnswers.singleOrNull()
        return rejectNonEnglishItemAnswer(if (result.usedLlm && result.generationState == AiReplyGenerationState.LLM_USED && answer != null) {
            AiReplyItemGenerationResult(
                itemAnswer = answer,
                handling = handling,
                generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                generationState = result.generationState,
                usedLlm = true,
                lockable = true,
                warningCodes = result.contextWarnings
            )
        } else {
            AiReplyItemGenerationResult(
                itemAnswer = null,
                handling = handling,
                generationKind = null,
                generationState = result.generationState,
                usedLlm = result.usedLlm,
                lockable = false,
                warningCodes = result.contextWarnings
            )
        })
    }

    private fun generatePendingAcknowledgement(
        inboundText: String,
        requestFact: RequestFactItem,
        requestKey: String?,
        operatorInstruction: String?,
        replyModel: String?,
        llmAttemptTimeoutSeconds: Int?,
        llmTotalTimeoutSeconds: Int?,
        cancellationToken: AiReplyCancellationToken?,
        progressReporter: AiReplyProgressReporter
    ): AiReplyItemGenerationResult {
        val safeText = AiReplyHighRiskClaimValidator.safeAcknowledgementFor(inboundText)
        val selectedModel = AiReplyModel.fromNullable(replyModel)
        val client = llmDraftClientProvider.getIfAvailable()
        if (!properties.enabled || client == null) {
            return safeAcknowledgementResult(requestFact, safeText, AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE)
        }
        val policy = AiReplyTimeoutPolicy.resolve(llmAttemptTimeoutSeconds, llmTotalTimeoutSeconds)
        val budget = policy.budget()
        progressReporter.startBudget(budget)
        val instructionLine = operatorInstruction?.let { "Operator style instruction (expression only): $it" }.orEmpty()
        val messages = listOf(
            LlmChatMessage(
                role = "system",
                content = "Return one short single-paragraph acknowledgement. Confirm only that the point is pending verification. " +
                    "Return English only, regardless of the language used in the inbound email or operator instruction. " +
                    "Do not add facts, numbers, dates, URLs, identities, contracts, funding, actions, lists, internal labels, or promises with a deadline."
            ),
            LlmChatMessage(
                role = "user",
                content = "requestKey=${requestKey ?: requestFact.index}\nInbound request:\n$inboundText\n$instructionLine"
            )
        )
        val observed = try {
            executeWithRetry(
                client = client,
                messages = messages,
                temperature = properties.temperature,
                providerModel = selectedModel.resolveProviderModel(properties),
                budget = budget,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            ).first
        } finally {
            progressReporter.endProviderCall()
        }
        val candidate = observed.content?.trim()
        val validation = candidate?.let { claimValidator.validateNoEvidenceAcknowledgement(it) }
        if (candidate != null && validation?.valid == true) {
            return AiReplyItemGenerationResult(
                itemAnswer = AiReplyItemAnswer(
                    requestIndex = requestFact.index,
                    requestText = requestFact.requestText,
                    status = requestFact.status,
                    answerText = candidate,
                    claims = emptyList()
                ),
                handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
                generationState = AiReplyGenerationState.LLM_USED,
                usedLlm = true,
                lockable = true
            )
        }
        return safeAcknowledgementResult(
            requestFact,
            safeText,
            if (observed.failureType == LlmChatFailureType.CLIENT_UNAVAILABLE) {
                AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
            } else {
                AiReplyGenerationState.FALLBACK_NO_RESPONSE
            },
            validation?.warningCodes.orEmpty()
        )
    }

    private fun generateOperatorDirectedAnswer(
        inboundText: String,
        requestFact: RequestFactItem,
        requestKey: String?,
        operatorInstruction: String?,
        expertProfile: String?,
        replyModel: String?,
        llmAttemptTimeoutSeconds: Int?,
        llmTotalTimeoutSeconds: Int?,
        cancellationToken: AiReplyCancellationToken?,
        progressReporter: AiReplyProgressReporter
    ): AiReplyItemGenerationResult {
        val instruction = operatorInstruction?.trim().orEmpty()
        require(instruction.isNotBlank()) { "operatorInstruction is required" }
        val token = cancellationToken ?: AiReplyCancellationToken()
        val policy = AiReplyTimeoutPolicy.resolve(llmAttemptTimeoutSeconds, llmTotalTimeoutSeconds)
        val budget = policy.budget()
        progressReporter.startBudget(budget)
        val selectedModel = AiReplyModel.fromNullable(replyModel)
        val client = llmDraftClientProvider.getIfAvailable()
        if (!properties.enabled || client == null) {
            return AiReplyItemGenerationResult(
                itemAnswer = null,
                handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
                generationKind = null,
                generationState = if (!properties.enabled) {
                    AiReplyGenerationState.FALLBACK_LLM_DISABLED
                } else {
                    AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
                },
                usedLlm = false,
                lockable = false
            )
        }

        val allowedActions = AiReplyActionPolicy.deriveAllowed(inboundText, null, emptyList())
        val messages = withActionBoundary(
            listOf(
                LlmChatMessage(
                    role = "system",
                    content = "Rewrite one recipient-facing answer from the operator-provided answer basis. " +
                        "Return English only, regardless of the language used in the target question or answer basis; translate the answer basis into natural English without changing its facts. " +
                        "The operator-provided answer basis is authoritative content. Only restate or organize it; " +
                        "do not add any institution, programme, funding, contract, time, identity, number, URL, or other fact " +
                        "not present in that basis. Return plain email prose only, with no JSON, headings, lists, status labels, " +
                        "or internal markers."
                ),
                LlmChatMessage(
                    role = "user",
                    content = buildString {
                        appendLine("Target requestKey: ${requestKey ?: requestFact.index}")
                        appendLine("Target question:")
                        appendLine(requestFact.requestText)
                        if (!expertProfile.isNullOrBlank()) {
                            appendLine("Recipient context:")
                            appendLine(expertProfile)
                        }
                        appendLine("operator-provided answer basis:")
                        appendLine(instruction)
                    }
                )
            ),
            allowedActions
        )

        val observed = try {
            executeWithRetry(
                client = client,
                messages = messages,
                temperature = properties.temperature,
                providerModel = selectedModel.resolveProviderModel(properties),
                jsonOutput = false,
                budget = budget,
                cancellationToken = token,
                progressReporter = progressReporter
            ).first
        } finally {
            progressReporter.endProviderCall()
        }
        val candidate = observed.content?.trim().orEmpty()
        val transportWarning = failureTypeToWarning(observed.failureType)
        val warnings = transportWarning?.let(::listOf).orEmpty()
        val invalid = candidate.isBlank() || INTERNAL_RESPONSE_MARKER.containsMatchIn(candidate) ||
            AiReplyActionPolicy.findViolations(candidate, allowedActions).isNotEmpty()
        if (observed.failureType != LlmChatFailureType.SUCCESS || invalid) {
            return AiReplyItemGenerationResult(
                itemAnswer = null,
                handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
                generationKind = null,
                generationState = if (observed.failureType == LlmChatFailureType.CLIENT_UNAVAILABLE) {
                    AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
                } else {
                    AiReplyGenerationState.FALLBACK_NO_RESPONSE
                },
                usedLlm = false,
                lockable = false,
                warningCodes = warnings
            )
        }
        return AiReplyItemGenerationResult(
            itemAnswer = AiReplyItemAnswer(
                requestIndex = requestFact.index,
                requestText = requestFact.requestText,
                status = requestFact.status,
                answerText = candidate,
                claims = emptyList()
            ),
            handling = TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
            generationKind = TrustReplyItemGenerationKind.AI_GENERATED,
            generationState = AiReplyGenerationState.LLM_USED,
            usedLlm = true,
            lockable = true
        )
    }

    private fun rejectNonEnglishItemAnswer(result: AiReplyItemGenerationResult): AiReplyItemGenerationResult {
        val answer = result.itemAnswer ?: return result
        if (result.generationKind != TrustReplyItemGenerationKind.AI_GENERATED || !containsNonLatinLetter(answer.answerText)) {
            return result
        }
        return result.copy(
            itemAnswer = null,
            generationKind = null,
            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
            usedLlm = false,
            lockable = false,
            warningCodes = (result.warningCodes + WARNING_ENGLISH_REPLY_REQUIRED).distinct()
        )
    }

    private fun containsNonLatinLetter(text: String): Boolean = text.any { char ->
        Character.isLetter(char) && Character.UnicodeScript.of(char.code) != Character.UnicodeScript.LATIN
    }

    private fun safeAcknowledgementResult(
        requestFact: RequestFactItem,
        text: String,
        state: AiReplyGenerationState,
        warnings: List<String> = emptyList()
    ) = AiReplyItemGenerationResult(
        itemAnswer = AiReplyItemAnswer(
            requestIndex = requestFact.index,
            requestText = requestFact.requestText,
            status = requestFact.status,
            answerText = text,
            claims = emptyList()
        ),
        handling = TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
        generationKind = TrustReplyItemGenerationKind.SAFE_TEMPLATE,
        generationState = state,
        usedLlm = false,
        lockable = true,
        warningCodes = warnings
    )

    private fun validateItemHandling(status: RequestGroundingStatus, handling: TrustReplyItemHandling) {
        val allowed = when (status) {
            RequestGroundingStatus.GROUNDED -> setOf(
                TrustReplyItemHandling.ANSWER_WITH_EVIDENCE,
                TrustReplyItemHandling.OMIT
            )
            RequestGroundingStatus.PARTIAL -> setOf(
                TrustReplyItemHandling.ANSWER_SUPPORTED_PART,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            )
            RequestGroundingStatus.UNSUPPORTED -> setOf(
                TrustReplyItemHandling.ANSWER_FROM_OPERATOR_INPUT,
                TrustReplyItemHandling.ACKNOWLEDGE_PENDING,
                TrustReplyItemHandling.OMIT
            )
        }
        require(handling in allowed) { "handling is not allowed for request status" }
    }

    fun generate(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        qaRuleIds: List<Long>? = null,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null,
        simulateOnly: Boolean = false,
        contextWarnings: List<String> = emptyList(),
        replyModel: String? = null,
        researchProfileSufficient: Boolean =
            !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
    ): AiReplyDraftResult = generate(
        inboundText, operatorTurns, qaRuleIds, operatorInstruction, expertProfile, mailHistory,
        simulateOnly, contextWarnings, replyModel, researchProfileSufficient,
        null, null, null, AiReplyProgressReporter.NOOP
    )

    fun generate(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        qaRuleIds: List<Long>? = null,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null,
        simulateOnly: Boolean = false, // deprecated: has no effect; do not read
        contextWarnings: List<String> = emptyList(),
        replyModel: String? = null,
        researchProfileSufficient: Boolean =
            !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"),
        llmAttemptTimeoutSeconds: Int? = null,
        llmTotalTimeoutSeconds: Int? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
    ): AiReplyDraftResult {
        val runtimeEnabled = llmAttemptTimeoutSeconds != null || llmTotalTimeoutSeconds != null ||
            cancellationToken != null || progressReporter !== AiReplyProgressReporter.NOOP
        val token = if (runtimeEnabled) cancellationToken ?: AiReplyCancellationToken() else null
        val timeoutPolicy = if (runtimeEnabled) {
            AiReplyTimeoutPolicy.resolve(llmAttemptTimeoutSeconds, llmTotalTimeoutSeconds)
        } else null
        val budget = timeoutPolicy?.budget()
        if (budget != null) progressReporter.startBudget(budget)
        token?.throwIfCancelled()
        val selectedModel = AiReplyModel.fromNullable(replyModel)
        val providerModel = selectedModel.resolveProviderModel(properties)
        val resolved = resolveQaRules(
            inboundText,
            qaRuleIds,
            contextWarnings,
            researchProfileSufficient
        )
        var mode = when {
            resolved.sendQaRuleIds.isNotEmpty() ||
                resolved.requestFacts.any { it.factRuleIds.isNotEmpty() } ||
                resolved.requestCount >= 2 ||
                resolved.requestFacts.any { it.requiresResearchContext } ->
                AiReplyMode.QA_GROUNDED
            else ->
                AiReplyMode.FREE_FORM
        }

        val lastDraft = operatorTurns.lastOrNull()?.assistantDraft
        val allowedActions = AiReplyActionPolicy.deriveAllowed(
            inboundText = inboundText,
            operatorInstruction = operatorInstruction,
            operatorTurns = operatorTurns
        )
        val plan = contentPlanner.buildPlan(resolved.requestFacts, allowedActions)

        if (AiReplyGroundedContentPlanner.hasTrustSensitiveNoFacts(resolved.requestFacts)) {
            mode = AiReplyMode.QA_GROUNDED
        }

        val promptSnapshot = resolvePromptSnapshot(mode)

        if (!properties.enabled) {
            return groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel.name,
                contextWarnings = contextWarnings,
                plan = plan,
                allowedActions = allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED,
                promptVersion = promptSnapshot.version
            )
        }

        val client = llmDraftClientProvider.getIfAvailable()
        if (client == null) {
            return groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel.name,
                contextWarnings = contextWarnings,
                plan = plan,
                allowedActions = allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE,
                promptVersion = promptSnapshot.version
            )
        }

        val fewShotDialogRefs: List<String>
        val messages = when (mode) {
            AiReplyMode.QA_GROUNDED -> {
                val buildResult = buildGroundedMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    requestFacts = resolved.requestFacts,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    contextWarnings = contextWarnings,
                    operatorInstruction = operatorInstruction,
                    plan = plan
                )
                fewShotDialogRefs = buildResult.fewShotDialogRefs
                buildResult.messages
            }
            AiReplyMode.QA_MATCHED -> {
                fewShotDialogRefs = emptyList()
                buildGroundedMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    requestFacts = resolved.requestFacts,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    contextWarnings = contextWarnings,
                    operatorInstruction = operatorInstruction,
                    plan = plan
                ).messages
            }
            AiReplyMode.FREE_FORM -> {
                val buildResult = buildFreeFormMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    operatorInstruction = operatorInstruction,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    promptRuleIds = resolved.promptRuleIds,
                    promptSnapshot = promptSnapshot
                )
                fewShotDialogRefs = buildResult.fewShotDialogRefs
                buildResult.messages
            }
        }
        val boundedMessages = withActionBoundary(messages, plan.allowedActions)
        val temperature = when (mode) {
            AiReplyMode.QA_GROUNDED, AiReplyMode.QA_MATCHED -> properties.temperature
            AiReplyMode.FREE_FORM -> properties.freeFormTemperature
        }
        val totalTimeoutFallback = {
            groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel.name,
                contextWarnings = (contextWarnings + WARNING_LLM_TOTAL_TIMEOUT).distinct(),
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                promptVersion = promptSnapshot.version
            )
        }

        if (mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED) {
            return finalizeRuntimeResult(
                generateGrounded(
                client = client,
                boundedMessages = boundedMessages,
                temperature = temperature,
                providerModel = providerModel,
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                promptVersion = promptSnapshot.version,
                budget = budget,
                cancellationToken = token,
                progressReporter = progressReporter
                ),
                budget = budget,
                cancellationToken = token,
                fallback = totalTimeoutFallback
            )
        }

        val (freeFormObserved, freeFormCallCount) = executeWithRetry(
            client, boundedMessages, temperature, providerModel,
            budget = budget,
            cancellationToken = token,
            progressReporter = progressReporter
        )
        val llmText = if (freeFormObserved.failureType == LlmChatFailureType.SUCCESS) freeFormObserved.content else null

        return finalizeRuntimeResult(if (llmText != null) {
            progressReporter.transition(AiReplyProgressPhase.VALIDATING)
            enforceActionPolicy(
                draftText = llmText,
                usedLlm = true,
                generationState = AiReplyGenerationState.LLM_USED,
                allowedActions = plan.allowedActions,
                messages = boundedMessages,
                client = client,
                temperature = temperature,
                providerModel = providerModel,
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan,
                promptVersion = promptSnapshot.version,
                budget = budget,
                cancellationToken = token,
                progressReporter = progressReporter
            )
        } else {
            val transportWarning = failureTypeToWarning(freeFormObserved.failureType)
            val finalWarnings = contextWarnings.toMutableList()
            if (transportWarning != null) {
                finalWarnings += transportWarning
            }
            val genState = if (freeFormObserved.failureType == LlmChatFailureType.CLIENT_UNAVAILABLE) {
                AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
            } else {
                AiReplyGenerationState.FALLBACK_NO_RESPONSE
            }
            groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel.name,
                contextWarnings = finalWarnings.distinct(),
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = genState,
                promptVersion = promptSnapshot.version
            )
        }, budget, token, totalTimeoutFallback)
    }

    private fun finalizeRuntimeResult(
        result: AiReplyDraftResult,
        budget: AiReplyGenerationBudget?,
        cancellationToken: AiReplyCancellationToken?,
        fallback: () -> AiReplyDraftResult
    ): AiReplyDraftResult {
        cancellationToken?.throwIfCancelled()
        return if (budget != null && budget.remainingTotalMillis() <= 0L) fallback() else result
    }

    private fun generateGrounded(
        client: LlmDraftClient,
        boundedMessages: List<LlmChatMessage>,
        temperature: Double,
        providerModel: String,
        selectedModel: String,
        resolved: ResolvedQaRules,
        mode: AiReplyMode,
        fewShotDialogRefs: List<String>,
        contextWarnings: List<String>,
        plan: GroundedContentPlan,
        operatorTurns: List<AiReplyTurn>,
        lastDraft: String?,
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?,
        operatorInstruction: String?,
        promptVersion: String,
        budget: AiReplyGenerationBudget? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
    ): AiReplyDraftResult {
        val (observedResult, callCount) = executeWithRetry(
            client,
            boundedMessages,
            temperature,
            providerModel,
            jsonOutput = true,
            budget = budget,
            cancellationToken = cancellationToken,
            progressReporter = progressReporter
        )
        val llmText = if (observedResult.failureType == LlmChatFailureType.SUCCESS) observedResult.content else null

        if (llmText == null) {
            val transportWarning = failureTypeToWarning(observedResult.failureType)
            val warnings = contextWarnings.toMutableList()
            if (transportWarning != null) {
                warnings += transportWarning
            }
            val genState = if (observedResult.failureType == LlmChatFailureType.CLIENT_UNAVAILABLE) {
                AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
            } else {
                AiReplyGenerationState.FALLBACK_NO_RESPONSE
            }
            return groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel,
                contextWarnings = warnings.distinct(),
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = genState,
                promptVersion = promptVersion
            )
        }

        progressReporter.transition(AiReplyProgressPhase.VALIDATING)

        val blockingTrust = contentPlanner.hasBlockingTrustGap(resolved.requestFacts)
        val firstResult = materializeAndValidateGroundedCandidate(
            rawResponse = llmText,
            plan = plan,
            resolved = resolved,
            allowedActions = plan.allowedActions,
            hasBlockingTrustGap = blockingTrust
        )

        if (firstResult.valid) {
            progressReporter.transition(AiReplyProgressPhase.FINALIZING)
            return buildGroundedResult(
                validated = firstResult,
                usedLlm = true,
                generationState = AiReplyGenerationState.LLM_USED,
                selectedModel = selectedModel,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan,
                promptVersion = promptVersion
            )
        }

        val initialDiagnostics = diagnosticsFor(AiReplyValidationAttempt.INITIAL, firstResult.issues)
        logValidationFailure(selectedModel, mode, AiReplyValidationAttempt.INITIAL, initialDiagnostics)
        val correctionMsg = buildTrustCorrectionMessage(
            issues = firstResult.issues,
            plan = plan
        )
        val retryMessages = boundedMessages + LlmChatMessage(role = "user", content = correctionMsg)
        val retryObserved = executeProviderCall(
            client = client,
            messages = retryMessages,
            temperature = GROUNDED_REPAIR_TEMPERATURE,
            providerModel = providerModel,
            jsonOutput = true,
            phase = AiReplyProgressPhase.REPAIRING,
            budget = budget,
            cancellationToken = cancellationToken,
            progressReporter = progressReporter
        )
        val retryText = if (retryObserved.failureType == LlmChatFailureType.SUCCESS) retryObserved.content else null

        if (retryText == null) {
            val mergedWarnings = contextWarnings.toMutableList()
            mergedWarnings += firstResult.allWarnings
            val transportWarning = failureTypeToWarning(retryObserved.failureType)
            if (transportWarning != null) {
                mergedWarnings += transportWarning
            }
            mergedWarnings += TRUST_REPAIR_EXHAUSTED
            return groundedFallbackResult(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                mode = mode,
                selectedModel = selectedModel,
                contextWarnings = mergedWarnings.distinct(),
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                promptVersion = promptVersion,
                validationDiagnostics = AiReplyValidationDiagnostics.from(initialDiagnostics)
            )
        }

        val retryResult = materializeAndValidateGroundedCandidate(
            rawResponse = retryText,
            plan = plan,
            resolved = resolved,
            allowedActions = plan.allowedActions,
            hasBlockingTrustGap = blockingTrust
        )

        val repairDiagnostics = diagnosticsFor(AiReplyValidationAttempt.REPAIR, retryResult.issues)
        if (retryResult.valid) {
            progressReporter.transition(AiReplyProgressPhase.FINALIZING)
            return buildGroundedResult(
                validated = retryResult,
                usedLlm = true,
                generationState = AiReplyGenerationState.LLM_USED,
                selectedModel = selectedModel,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan,
                promptVersion = promptVersion,
                validationDiagnostics = AiReplyValidationDiagnostics.from(initialDiagnostics)
            )
        }

        logValidationFailure(selectedModel, mode, AiReplyValidationAttempt.REPAIR, repairDiagnostics)
        val mergedWarnings = contextWarnings.toMutableList()
        mergedWarnings += firstResult.allWarnings
        mergedWarnings += retryResult.allWarnings
        mergedWarnings += TRUST_REPAIR_EXHAUSTED
        return groundedFallbackResult(
            resolved = resolved,
            operatorTurns = operatorTurns,
            lastDraft = lastDraft,
            inboundText = inboundText,
            expertProfile = expertProfile,
            mailHistory = mailHistory,
            operatorInstruction = operatorInstruction,
            mode = mode,
            selectedModel = selectedModel,
            contextWarnings = mergedWarnings.distinct(),
            plan = plan,
            allowedActions = plan.allowedActions,
            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
            promptVersion = promptVersion,
            validationDiagnostics = AiReplyValidationDiagnostics.from(initialDiagnostics, repairDiagnostics)
        )
    }

    private fun executeWithRetry(
        client: LlmDraftClient,
        messages: List<LlmChatMessage>,
        temperature: Double,
        providerModel: String,
        jsonOutput: Boolean = false,
        budget: AiReplyGenerationBudget? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP,
        phase: AiReplyProgressPhase = AiReplyProgressPhase.CALLING
    ): Pair<LlmChatResult, Int> {
        val firstResult = executeProviderCall(
            client, messages, temperature, providerModel, jsonOutput, phase,
            budget, cancellationToken, progressReporter
        )
        if (firstResult.failureType == LlmChatFailureType.SUCCESS) {
            return Pair(firstResult, 1)
        }
        val retryable = firstResult.failureType in setOf(
            LlmChatFailureType.TIMEOUT,
            LlmChatFailureType.RATE_LIMITED,
            LlmChatFailureType.NETWORK_ERROR,
            LlmChatFailureType.PROVIDER_ERROR,
            LlmChatFailureType.EMPTY_RESPONSE
        )
        if (!retryable) {
            return Pair(firstResult, 1)
        }
        cancellationToken?.throwIfCancelled()
        val retryResult = executeProviderCall(
            client, messages, temperature, providerModel, jsonOutput, phase,
            budget, cancellationToken, progressReporter
        )
        val totalCalls = 2
        if (retryResult.failureType == LlmChatFailureType.SUCCESS) {
            return Pair(retryResult, totalCalls)
        }
        return Pair(retryResult, totalCalls)
    }

    private fun executeProviderCall(
        client: LlmDraftClient,
        messages: List<LlmChatMessage>,
        temperature: Double,
        providerModel: String,
        jsonOutput: Boolean,
        phase: AiReplyProgressPhase,
        budget: AiReplyGenerationBudget?,
        cancellationToken: AiReplyCancellationToken?,
        progressReporter: AiReplyProgressReporter
    ): LlmChatResult {
        cancellationToken?.throwIfCancelled()
        val timeoutMillis = budget?.nextAttemptMillis() ?: 0L
        if (budget != null && timeoutMillis <= 0L) {
            return LlmChatResult(null, LlmChatFailureType.TOTAL_TIMEOUT)
        }
        val streamToken = cancellationToken ?: AiReplyCancellationToken()
        val streamSink = if (budget != null) {
            progressReporter.beginProviderCall(phase, timeoutMillis)
        } else {
            LlmStreamProgressSink.NOOP
        }
        return try {
            val result = if (budget != null) {
                client.chatWithModelObservedStream(
                    messages = messages,
                    temperature = temperature,
                    providerModel = providerModel,
                    timeoutMillis = timeoutMillis,
                    jsonOutput = jsonOutput,
                    cancellationToken = streamToken,
                    progressSink = streamSink
                )
            } else if (jsonOutput) {
                client.chatWithModelObservedJson(messages, temperature, providerModel)
            } else {
                client.chatWithModelObserved(messages, temperature, providerModel)
            }
            if (result.failureType == LlmChatFailureType.CANCELLED) {
                throw AiReplyGenerationCancelledException()
            }
            if (budget != null && budget.remainingTotalMillis() <= 0L && result.failureType != LlmChatFailureType.SUCCESS) {
                LlmChatResult(null, LlmChatFailureType.TOTAL_TIMEOUT)
            } else {
                result
            }
        } finally {
            if (budget != null) progressReporter.endProviderCall()
        }
    }

    private data class GroundedValidationResult(
        val valid: Boolean,
        val text: String,
        val sections: List<ValidatedSection>,
        val allWarnings: List<String>,
        val issues: List<AiReplyValidationIssue> = emptyList()
    )

    private fun materializeAndValidateGroundedCandidate(
        rawResponse: String,
        plan: GroundedContentPlan,
        resolved: ResolvedQaRules,
        allowedActions: Set<AiReplyAction>,
        hasBlockingTrustGap: Boolean
    ): GroundedValidationResult {
        val warnings = mutableListOf<String>()

        val materialized = acceptGroundedMaterialization(
            groundedDraftMaterializer.materialize(rawResponse, resolved.requestFacts, plan)
        )
        if (!materialized.valid) {
            warnings += materialized.warningCodes
            return GroundedValidationResult(
                valid = false,
                text = "",
                sections = materialized.sections,
                allWarnings = warnings.distinct(),
                issues = materialized.issues
            )
        }

        val claimResult = claimValidator.validate(materialized.sections, resolved.requestFacts)
        if (!claimResult.valid) {
            warnings += claimResult.warningCodes
        }

        val trustResult = claimValidator.validateGroundedCandidate(
            GroundedCandidateInput(
                validatedSections = materialized.sections,
                requestFacts = resolved.requestFacts,
                plan = plan,
                finalBody = materialized.text,
                hasBlockingTrustGap = hasBlockingTrustGap,
                sourceTextsByClaim = claimResult.sourceTextsByClaim
            )
        )
        if (!trustResult.valid) {
            warnings += trustResult.warningCodes
        }

        val issues = (claimResult.issues + trustResult.issues).toMutableList()
        val actionIssues = mutableListOf<AiReplyValidationIssue>()
        materialized.sections.forEach { section ->
            section.answers.forEach { answer ->
                val claimViolations = AiReplyActionPolicy.findViolations(answer.answer, allowedActions)
                if (AiReplyActionPolicy.detectActions(answer.answer).isNotEmpty() || claimViolations.isNotEmpty()) {
                    actionIssues += AiReplyValidationIssue(
                        AiReplyValidationStage.ACTION,
                        AiReplyValidationCodes.ACTION_TEXT_INVALID,
                        "r${section.requestIndex}:${answer.intentKey}"
                    )
                }
            }
        }

        val actionText = materialized.actionText
        val declaredActions = actionText?.let { AiReplyActionPolicy.detectActions(it) }.orEmpty()
        if (!materialized.actionTextValid) {
            actionIssues += AiReplyValidationIssue(
                AiReplyValidationStage.ACTION,
                AiReplyValidationCodes.ACTION_TEXT_INVALID
            )
        } else if (actionText != null && declaredActions.size != 1) {
            actionIssues += AiReplyValidationIssue(
                AiReplyValidationStage.ACTION,
                AiReplyValidationCodes.ACTION_TEXT_INVALID
            )
        } else if (declaredActions.singleOrNull() !in allowedActions && declaredActions.isNotEmpty()) {
            actionIssues += AiReplyValidationIssue(
                AiReplyValidationStage.ACTION,
                AiReplyValidationCodes.ACTION_NOT_ALLOWED
            )
        }

        val bodyActions = AiReplyActionPolicy.detectActions(materialized.text)
        if (bodyActions != declaredActions) {
            actionIssues += AiReplyValidationIssue(
                AiReplyValidationStage.ACTION,
                AiReplyValidationCodes.ACTION_BODY_MISMATCH
            )
        }

        val actionViolations = AiReplyActionPolicy.findViolations(materialized.text, allowedActions)
        if (actionViolations.isNotEmpty()) {
            actionViolations.forEach { v ->
                val code = v.code ?: UNAUTHORIZED_ACTION_REMOVED
                if (code !in warnings) {
                    warnings += code
                }
                actionIssues += AiReplyValidationIssue(AiReplyValidationStage.ACTION, code)
            }
        }
        actionIssues.forEach { issue ->
            if (issue.code !in warnings) {
                warnings += issue.code
            }
        }
        issues += actionIssues
        val text = materialized.text

        if (warnings.isNotEmpty()) {
            return GroundedValidationResult(
                valid = false,
                text = "",
                sections = materialized.sections,
                allWarnings = warnings.distinct(),
                issues = issues.distinct()
            )
        }

        return GroundedValidationResult(
            valid = true,
            text = text,
            sections = materialized.sections,
            allWarnings = emptyList()
        )
    }

    private fun buildGroundedResult(
        validated: GroundedValidationResult,
        usedLlm: Boolean,
        generationState: AiReplyGenerationState,
        selectedModel: String,
        resolved: ResolvedQaRules,
        mode: AiReplyMode,
        fewShotDialogRefs: List<String>,
        contextWarnings: List<String>,
        plan: GroundedContentPlan,
        promptVersion: String,
        validationDiagnostics: AiReplyValidationDiagnostics = AiReplyValidationDiagnostics(),
        budget: AiReplyGenerationBudget? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
    ): AiReplyDraftResult {
        val text = validated.text
        val finalWarnings = contextWarnings.toMutableList()
        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, plan.allowedActions)
        val finalText = if (sanitized.isNotBlank()) sanitized else text
        if (removed) {
            if (UNAUTHORIZED_ACTION_REMOVED !in finalWarnings) {
                finalWarnings += UNAUTHORIZED_ACTION_REMOVED
            }
        }

        val finalState = if (usedLlm) AiReplyGenerationState.LLM_USED else generationState
        val readiness = if (removed) {
            AiReplyDraftReadiness.NEEDS_REVIEW
        } else {
            resolveDraftReadiness(resolved.requestFacts, resolved.sendQaRuleIds)
        }

        val (evidenceSetVersion, evidenceSources, evidenceObservedWarnings) = buildEvidenceSnapshotForSelection(resolved.sendQaRuleIds)
        if (evidenceObservedWarnings.isNotEmpty()) {
            evidenceObservedWarnings.forEach { w ->
                if (w !in finalWarnings) finalWarnings += w
            }
        }

        return AiReplyDraftResult(
            draftText = finalText,
            usedLlm = usedLlm,
            qaRuleIds = resolved.sendQaRuleIds,
            mode = mode,
            fewShotDialogRefs = fewShotDialogRefs,
            requestCount = resolved.requestCount,
            groundedRequestCount = resolved.groundedRequestCount,
            unsupportedRequests = resolved.unsupportedRequests,
            contextWarnings = finalWarnings,
            selectedModel = selectedModel,
            requestFacts = resolved.requestFacts,
            generationState = finalState,
            draftReadiness = readiness,
            promptVersion = promptVersion,
            evidenceSetVersion = evidenceSetVersion,
            evidenceSources = evidenceSources,
            validationDiagnostics = validationDiagnostics,
            itemAnswers = validated.sections.map { section ->
                val request = resolved.requestFacts.firstOrNull { it.index == section.requestIndex }
                AiReplyItemAnswer(
                    requestIndex = section.requestIndex,
                    requestText = request?.requestText.orEmpty(),
                    status = request?.status ?: RequestGroundingStatus.GROUNDED,
                    answerText = section.answers.joinToString(" ") { it.answer },
                    claims = section.answers.map { answer ->
                        AiReplyItemClaim(
                            intentKey = answer.intentKey,
                            text = answer.answer,
                            sourceRuleIds = answer.sourceRuleIds
                        )
                    }
                )
            }
        )
    }

    private fun groundedFallbackResult(
        resolved: ResolvedQaRules,
        operatorTurns: List<AiReplyTurn>,
        lastDraft: String?,
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?,
        operatorInstruction: String?,
        mode: AiReplyMode,
        selectedModel: String,
        contextWarnings: List<String>,
        plan: GroundedContentPlan,
        allowedActions: Set<AiReplyAction>,
        generationState: AiReplyGenerationState,
        promptVersion: String,
        validationDiagnostics: AiReplyValidationDiagnostics = AiReplyValidationDiagnostics()
    ): AiReplyDraftResult {
        val fallbackText = if (mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED) {
            aiReplyPointByPointComposer.composeFallbackReference(plan, resolved.requestFacts)
        } else {
            "LLM 未生成，且当前来信没有可用于确定性回复的审核事实。请人工撰写。"
        }

        val finalWarnings = contextWarnings.toMutableList()
        val finalText = fallbackText

        val (evidenceSetVersion, evidenceSources, evidenceObservedWarnings) = buildEvidenceSnapshotForSelection(resolved.sendQaRuleIds)
        if (evidenceObservedWarnings.isNotEmpty()) {
            evidenceObservedWarnings.forEach { w ->
                if (w !in finalWarnings) finalWarnings += w
            }
        }

        return AiReplyDraftResult(
            draftText = finalText,
            usedLlm = false,
            qaRuleIds = resolved.sendQaRuleIds,
            mode = mode,
            fewShotDialogRefs = emptyList(),
            requestCount = resolved.requestCount,
            groundedRequestCount = resolved.groundedRequestCount,
            unsupportedRequests = resolved.unsupportedRequests,
            contextWarnings = finalWarnings,
            selectedModel = selectedModel,
            requestFacts = resolved.requestFacts,
            generationState = generationState,
            draftReadiness = AiReplyDraftReadiness.BLOCKED,
            promptVersion = promptVersion,
            evidenceSetVersion = evidenceSetVersion,
            evidenceSources = evidenceSources,
            validationDiagnostics = validationDiagnostics
        )
    }

    private fun diagnosticsFor(
        attempt: AiReplyValidationAttempt,
        issues: List<AiReplyValidationIssue>
    ): List<AiReplyValidationDiagnostic> = issues.map {
        AiReplyValidationDiagnostic(attempt, it.stage, it.code, it.claimKey)
    }

    private fun logValidationFailure(
        selectedModel: String,
        mode: AiReplyMode,
        attempt: AiReplyValidationAttempt,
        diagnostics: List<AiReplyValidationDiagnostic>
    ) {
        logger.warn(
            "AI grounded validation failed model={} mode={} attempt={} diagnostics={} truncated={}",
            selectedModel,
            mode,
            attempt,
            diagnostics.map { "${it.stage.name}/${it.code}/${it.claimKey?.take(120)}" },
            diagnostics.size > AiReplyValidationDiagnostics.MAX_ITEMS
        )
    }

    private fun buildTrustCorrectionMessage(
        issues: List<AiReplyValidationIssue>,
        plan: GroundedContentPlan
    ): String = buildString {
        appendLine("Correct only the reported validation failures. Do not repeat the previous response.")
        appendLine("Allowed actions: ${AiReplyActionPolicy.formatAllowedLabel(plan.allowedActions)}.")
        appendLine("Diagnostics:")
        issues.forEach { issue ->
            appendLine("- stage=${issue.stage.name}; code=${issue.code}; claimKey=${issue.claimKey ?: "null"}")
            appendLine("  repair: ${repairInstruction(issue)}")
        }
        appendLine()
        appendLine(
            "Return only the exact minimal JSON protocol. Every plan claimKey appears exactly once; " +
                "rewrite only text values. actionText is null unless one allowed action is necessary. " +
                "No Markdown fence, no extra fields, no status labels."
        )
        appendLine()
        appendLine("{\"claims\":[${plan.claims.joinToString(",") { "{\"claimKey\":\"${it.claimKey}\",\"text\":\"\"}" }}],\"actionText\":null}")
    }

    internal fun repairInstruction(issue: AiReplyValidationIssue): String = when (issue.code) {
        AiReplyValidationCodes.JSON_INVALID ->
            "Return one valid JSON object only; remove Markdown fences and all prose outside the object."
        AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID ->
            "Use exactly the top-level fields claims and actionText, with no deterministic envelope fields."
        AiReplyValidationCodes.CLAIMS_INVALID ->
            "Set claims to an array containing one claim object for each current server-plan claimKey."
        AiReplyValidationCodes.CLAIM_FIELDS_INVALID ->
            "Give every claim exactly the fields claimKey and text, with a nonblank text value."
        AiReplyValidationCodes.CLAIM_KEY_DUPLICATE ->
            "Remove duplicate claim entries so each current server-plan claimKey appears once."
        AiReplyValidationCodes.CLAIM_KEY_UNKNOWN ->
            "Use only claimKey values supplied by the current server plan; do not invent identifiers."
        AiReplyValidationCodes.CLAIM_SET_MISMATCH ->
            "Return the complete server-plan claimKey set exactly once, preserving no missing or extra key."
        AiReplyValidationCodes.CLAIM_TEXT_INVALID ->
            "Replace the claim with natural nonblank answer text and remove status labels or internal markers."
        AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE ->
            "Rewrite the answer as natural email prose without numbered sections, fixed headings, or internal labels."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_SOURCE_UNAVAILABLE ->
            "Remove the claim unless its bound QA rule has an available nonblank authoritative fact."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_HALLUCINATED_FACT ->
            "Remove every number, URL, or compound fact that is absent from the bound authoritative rule fact."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_MODALITY_STRENGTHENED ->
            "Preserve the source fact's conditional or uncertain modality; remove stronger commitments."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_HIGH_RISK_UNBACKED ->
            "Remove each high-risk declaration unless the same claim's bound rule fact explicitly supports it."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_TRUST_RHETORIC ->
            "Remove trust rhetoric and replace unsupported reassurance with a direct fact-based answer."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE ->
            "Do not use confidentiality language as a substitute for an answer or an unsupported assurance."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED ->
            "State the required service or agency role using only the bound fact's supported wording."
        AiReplyHighRiskClaimValidator.WARNING_CLAIM_ENTERPRISE_UNGROUNDED ->
            "Keep enterprise identity uncertain when the bound fact says it is not yet determined or matched."
        AiReplyValidationCodes.ACTION_TEXT_INVALID ->
            "Set actionText to null or a nonblank string containing exactly one detected allowed action."
        AiReplyValidationCodes.ACTION_NOT_ALLOWED ->
            "Remove the unauthorized action or use actionText only for one action listed as allowed by the plan."
        AiReplyValidationCodes.ACTION_BODY_MISMATCH ->
            "Make the final composed body contain exactly the action set declared by actionText."
        UNAUTHORIZED_ACTION_REMOVED ->
            "Remove every outbound request not authorized by the current plan and keep claims informational."
        AiReplyActionPolicy.CODE_ACTION_SENSITIVE_MATERIAL ->
            "Do not request passports, identity documents, employment certificates, or bank statements."
        AiReplyActionPolicy.CODE_ACTION_CV_PURPOSE_MISSING ->
            "If requesting a CV, state its qualification or research-match review purpose."
        AiReplyActionPolicy.CODE_ACTION_CV_OPTIONALITY_MISSING ->
            "If requesting a CV, make the request explicitly optional and pressure-free."
        else -> when (issue.stage) {
            AiReplyValidationStage.STRUCTURE ->
                "Return only the exact minimal JSON protocol and preserve every server-plan claimKey exactly once."
            AiReplyValidationStage.CLAIM ->
                "Use only the bound authoritative RULE FACT and remove unsupported claim content."
            AiReplyValidationStage.TRUST ->
                "Answer only from validated facts and remove unsupported trust or certainty language."
            AiReplyValidationStage.ACTION ->
                "Use actionText only for one allowed outbound action and keep claim text free of requests."
        }
    }

    private fun withActionBoundary(
        messages: List<LlmChatMessage>,
        allowedActions: Set<AiReplyAction>
    ): List<LlmChatMessage> {
        val idx = messages.indexOfFirst { it.role == "system" }
        if (idx < 0) {
            return messages
        }
        val updated = messages.toMutableList()
        updated[idx] = updated[idx].copy(
            content = buildString {
                append(updated[idx].content.trimEnd())
                appendLine()
                appendLine()
                appendLine("Allowed outbound actions for this draft: ${AiReplyActionPolicy.formatAllowedLabel(allowedActions)}.")
                appendLine("Do not request materials or propose a meeting/call unless that action is listed above.")
                appendLine("Never ask for passports, ID cards, work certificates, or bank statements.")
            }
        )
        return updated
    }

    private fun enforceActionPolicy(
        draftText: String,
        usedLlm: Boolean,
        generationState: AiReplyGenerationState,
        allowedActions: Set<AiReplyAction>,
        messages: List<LlmChatMessage>?,
        client: LlmDraftClient?,
        temperature: Double?,
        providerModel: String,
        selectedModel: String,
        resolved: ResolvedQaRules,
        mode: AiReplyMode,
        fewShotDialogRefs: List<String>,
        contextWarnings: List<String>,
        plan: GroundedContentPlan,
        promptVersion: String,
        budget: AiReplyGenerationBudget? = null,
        cancellationToken: AiReplyCancellationToken? = null,
        progressReporter: AiReplyProgressReporter = AiReplyProgressReporter.NOOP
    ): AiReplyDraftResult {
        var text = draftText
        var used = usedLlm
        val warnings = contextWarnings.toMutableList()

        var violations = AiReplyActionPolicy.findViolations(text, allowedActions)
        if (violations.isNotEmpty() && client != null && messages != null) {
            val correction = buildActionCorrectionMessage(violations, allowedActions, mode, plan)
            val retryMessages = messages + LlmChatMessage(role = "user", content = correction)
            val retryText = executeProviderCall(
                client = client,
                messages = retryMessages,
                temperature = if (mode == AiReplyMode.QA_GROUNDED) {
                    GROUNDED_REPAIR_TEMPERATURE
                } else {
                    temperature ?: 0.0
                },
                providerModel = providerModel,
                jsonOutput = mode == AiReplyMode.QA_GROUNDED,
                phase = AiReplyProgressPhase.REPAIRING,
                budget = budget,
                cancellationToken = cancellationToken,
                progressReporter = progressReporter
            ).takeIf { it.failureType == LlmChatFailureType.SUCCESS }
                ?.content?.takeIf { it.isNotBlank() }
            if (retryText != null) {
                text = retryText
                used = true
                violations = AiReplyActionPolicy.findViolations(text, allowedActions)
            }
        }

        val (sanitized, removed) = AiReplyActionPolicy.sanitize(text, allowedActions)
        text = sanitized
        if (removed) {
            if (UNAUTHORIZED_ACTION_REMOVED !in warnings) {
                warnings += UNAUTHORIZED_ACTION_REMOVED
            }
        }
        if (text.isBlank()) {
            if (mode != AiReplyMode.QA_GROUNDED) {
                text = INSUFFICIENT_SAFE_REPLY
            }
            if (UNAUTHORIZED_ACTION_REMOVED !in warnings && removed) {
                warnings += UNAUTHORIZED_ACTION_REMOVED
            }
        }
        val (finalText, finalRemoved) = AiReplyActionPolicy.sanitize(text, allowedActions)
        text = if (finalText.isBlank()) {
            if (mode == AiReplyMode.QA_GROUNDED) "" else INSUFFICIENT_SAFE_REPLY
        } else {
            finalText
        }
        if (finalRemoved && UNAUTHORIZED_ACTION_REMOVED !in warnings) {
            warnings += UNAUTHORIZED_ACTION_REMOVED
        }

        val finalState = if (used) AiReplyGenerationState.LLM_USED else generationState

        progressReporter.transition(AiReplyProgressPhase.FINALIZING)

        val readiness = resolveDraftReadiness(resolved.requestFacts, resolved.sendQaRuleIds)

        val (evidenceSetVersion, evidenceSources, evidenceObservedWarnings) = buildEvidenceSnapshotForSelection(resolved.sendQaRuleIds)
        if (evidenceObservedWarnings.isNotEmpty()) {
            evidenceObservedWarnings.forEach { w ->
                if (w !in warnings) warnings += w
            }
        }

        return AiReplyDraftResult(
            draftText = text,
            usedLlm = used,
            qaRuleIds = resolved.sendQaRuleIds,
            mode = mode,
            fewShotDialogRefs = fewShotDialogRefs,
            requestCount = resolved.requestCount,
            groundedRequestCount = resolved.groundedRequestCount,
            unsupportedRequests = resolved.unsupportedRequests,
            contextWarnings = warnings,
            selectedModel = selectedModel,
            requestFacts = resolved.requestFacts,
            generationState = finalState,
            draftReadiness = readiness,
            promptVersion = promptVersion,
            evidenceSetVersion = evidenceSetVersion,
            evidenceSources = evidenceSources
        )
    }

    private fun buildActionCorrectionMessage(
        violations: List<ActionViolation>,
        allowedActions: Set<AiReplyAction>,
        mode: AiReplyMode,
        plan: GroundedContentPlan
    ): String = buildString {
        appendLine("Your previous draft violated the outbound action policy.")
        appendLine("Allowed actions: ${AiReplyActionPolicy.formatAllowedLabel(allowedActions)}.")
        appendLine("Remove these unauthorized direct requests and rewrite:")
        violations.forEach { violation ->
            appendLine("- [${violation.action}] ${violation.sentence}")
        }
        appendLine("Do not add materials requests or meeting proposals unless they are in the allowed set.")
        if (mode == AiReplyMode.QA_GROUNDED) {
            appendLine(
                "Return exactly the minimal JSON protocol {\"claims\":[{\"claimKey\":\"...\",\"text\":\"...\"}],\"actionText\":null}. " +
                    "Use every claimKey from the current server plan exactly once. No extra fields, Markdown fence, or STATUS labels."
            )
        } else {
            appendLine("Rewrite the email body only.")
        }
    }

    internal fun resolveQaRules(
        inboundText: String,
        qaRuleIds: List<Long>?,
        contextWarnings: List<String> = emptyList(),
        researchProfileSufficient: Boolean =
            !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
    ): ResolvedQaRules =
        qaFactSelectionService.select(
            inboundText = inboundText,
            selectedRuleIds = qaRuleIds,
            researchProfileSufficient = researchProfileSufficient
        )

    fun resolveDraftReadinessForSelection(
        requestFacts: List<RequestFactItem>,
        evidenceRuleIds: List<Long>
    ): AiReplyDraftReadiness = resolveDraftReadiness(requestFacts, evidenceRuleIds)

    internal fun resolveDraftReadiness(
        requestFacts: List<RequestFactItem>,
        evidenceRuleIds: List<Long> = requestFacts.flatMap { it.factRuleIds }.distinct()
    ): AiReplyDraftReadiness {
        if (requestFacts.isEmpty()) {
            return AiReplyDraftReadiness.READY
        }

        val hasBlockingUnsupported = requestFacts.any { item ->
            if (item.status == RequestGroundingStatus.UNSUPPORTED) {
                if (item.intents.isEmpty()) {
                    true
                } else {
                    item.intents.any { contentPlanner.isBlockingTrustIntent(it.intentKey) }
                }
            } else {
                false
            }
        }
        if (hasBlockingUnsupported) {
            return AiReplyDraftReadiness.BLOCKED
        }

        val hasNonCriticalUnsupported = requestFacts.any { item ->
            item.status == RequestGroundingStatus.UNSUPPORTED && item.intents.isNotEmpty() &&
                item.intents.none { contentPlanner.isBlockingTrustIntent(it.intentKey) }
        }
        val hasUnknownUnsupported = requestFacts.any { item ->
            item.status == RequestGroundingStatus.UNSUPPORTED && item.intents.isEmpty()
        }
        if (hasUnknownUnsupported) {
            return AiReplyDraftReadiness.BLOCKED
        }

        if (evidenceRuleIds.isEmpty()) {
            return AiReplyDraftReadiness.BLOCKED
        }

        val rules = evidenceRuleIds.mapNotNull { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)
        }
        if (rules.size != evidenceRuleIds.size) {
            return AiReplyDraftReadiness.BLOCKED
        }
        if (rules.any { !it.enabled }) {
            return AiReplyDraftReadiness.BLOCKED
        }
        if (rules.any { it.answerBody.isBlank() }) {
            return AiReplyDraftReadiness.BLOCKED
        }
        val policies = try {
            rules.map { it.replyPolicyEnum() }
        } catch (_: IllegalArgumentException) {
            return AiReplyDraftReadiness.BLOCKED
        }
        if (policies.any { it == QaReplyPolicy.NEVER }) {
            return AiReplyDraftReadiness.BLOCKED
        }

        if (requestFacts.any { it.status == RequestGroundingStatus.PARTIAL }) {
            return AiReplyDraftReadiness.NEEDS_REVIEW
        }

        if (hasNonCriticalUnsupported) {
            return AiReplyDraftReadiness.NEEDS_REVIEW
        }

        if (policies.any { it == QaReplyPolicy.REVIEW }) {
            return AiReplyDraftReadiness.NEEDS_REVIEW
        }

        return AiReplyDraftReadiness.READY
    }

    internal fun buildMatchedMessages(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        promptRuleIds: List<Long>,
        operatorInstruction: String? = null
    ): List<LlmChatMessage> {
        val messages = mutableListOf<LlmChatMessage>()
        messages += LlmChatMessage(role = "system", content = buildMatchedSystemPrompt())
        messages += LlmChatMessage(role = "user", content = buildMatchedUserContent(inboundText, promptRuleIds))
        appendFirstTurnInstruction(messages, operatorInstruction)
        appendOperatorTurns(messages, operatorTurns)
        return messages
    }

    internal fun buildGroundedMessages(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        requestFacts: List<RequestFactItem>,
        expertProfile: String?,
        mailHistory: String?,
        contextWarnings: List<String>,
        operatorInstruction: String? = null,
        plan: GroundedContentPlan
    ): FreeFormBuildResult {
        val fewShots = aiTrainingDialogueService.selectRelevantDialogues(inboundText, max = 1)
        val messages = mutableListOf<LlmChatMessage>()
        val multiRequest = requestFacts.size >= 2
        val systemPrompt = if (fewShots.isEmpty()) {
            buildGroundedSystemPrompt(multiRequest)
        } else {
            buildGroundedSystemPrompt(multiRequest) + buildGroundedFewShotBoundaryNote(fewShots.size)
        }
        messages += LlmChatMessage(role = "system", content = systemPrompt)
        fewShots.forEach { dialogue ->
            messages += dialogue.messages
        }
        messages += LlmChatMessage(
            role = "user",
            content = buildGroundedUserContent(inboundText, requestFacts, expertProfile, mailHistory, contextWarnings)
        )
        appendFirstTurnInstruction(messages, operatorInstruction)
        appendOperatorTurns(messages, operatorTurns)
        messages += LlmChatMessage(role = "user", content = buildGroundedProtocolMessage(plan))
        return FreeFormBuildResult(
            messages = messages,
            fewShotDialogRefs = fewShots.map { it.sourceRef }
        )
    }

    internal fun buildFreeFormMessages(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null,
        promptRuleIds: List<Long> = emptyList(),
        promptSnapshot: AiReplyPromptSnapshot
    ): FreeFormBuildResult {
        val fewShots = aiTrainingDialogueService.selectRelevantDialogues(inboundText, max = 2)
        val messages = mutableListOf<LlmChatMessage>()
        val systemPrompt = promptSnapshot.systemPrompt
        val augmentedPrompt = if (fewShots.isEmpty()) {
            systemPrompt
        } else {
            systemPrompt + buildFewShotBoundaryNote(fewShots.size)
        }
        messages += LlmChatMessage(role = "system", content = augmentedPrompt)
        fewShots.forEach { dialogue ->
            messages += dialogue.messages
        }
        messages += LlmChatMessage(
            role = "user",
            content = buildFreeFormUserContent(inboundText, expertProfile, mailHistory, promptRuleIds)
        )
        appendFirstTurnInstruction(messages, operatorInstruction)
        appendOperatorTurns(messages, operatorTurns)
        return FreeFormBuildResult(
            messages = messages,
            fewShotDialogRefs = fewShots.map { it.sourceRef }
        )
    }

    private fun buildFewShotBoundaryNote(exampleCount: Int): String = buildString {
        appendLine()
        appendLine(
            "The following $exampleCount user/assistant pairs are style examples for structure, tone, and communication strategy; " +
                "they must not be used as a factual source. Only the final user message is the real inbound email. " +
                "All factual claims must come from the current QA rule knowledge, training knowledge, or existing expert profile; " +
                "if those sources lack a needed detail, mark it as pending confirmation. " +
                "Ignore any example facts that conflict with the approved context or are missing from it."
        )
    }

    private fun appendFirstTurnInstruction(messages: MutableList<LlmChatMessage>, operatorInstruction: String?) {
        operatorInstruction?.takeIf { it.isNotBlank() }?.let { instruction ->
            messages += LlmChatMessage(role = "user", content = instruction.take(4000))
        }
    }

    private fun appendOperatorTurns(messages: MutableList<LlmChatMessage>, operatorTurns: List<AiReplyTurn>) {
        operatorTurns.forEach { turn ->
            messages += LlmChatMessage(role = "assistant", content = turn.assistantDraft)
            messages += LlmChatMessage(role = "user", content = turn.operatorInstruction)
        }
    }

    private fun buildBaseSystemPrompt(): String = FreeFormPromptDefaults.baseSystemPrompt()

    private fun buildMatchedSystemPrompt(): String = buildString {
        append(buildBaseSystemPrompt())
        appendLine()
        appendLine("You are composing a reply by stitching matched QA rule segments.")
        appendLine(
            "CRITICAL: Preserve each SEGMENT wording and facts verbatim — only add transition sentences, " +
                "integrate the salutation framework, and deduplicate greetings."
        )
        appendLine("Do not rewrite, paraphrase, or add promises beyond what the segments state.")
    }

    private fun buildGroundedSystemPrompt(multiRequest: Boolean): String = buildString {
        append(buildBaseSystemPrompt())
        appendLine()
        appendLine("You answer expert inbound requests using only the approved facts provided per request and intent.")
        appendLine(
            "Return exactly one JSON object and nothing else: " +
                "{\"claims\":[{\"claimKey\":\"r1:company.legal_name\",\"text\":\"...\"}]," +
                "\"actionText\":null}"
        )
        appendLine("Rules:")
        appendLine("- Top-level fields must be exactly claims and actionText. Each claim must have exactly claimKey and text.")
        appendLine("- Produce exactly one claim for every server-plan claimKey. Claim array order is arbitrary; the server owns final order.")
        appendLine("- Do not output requestIndex, intentKey, sourceIds, paragraphs, missingFacts, requiresReview, proposedAction, status, explanation, or any other field.")
        appendLine("- actionText must be null or one independently authorized action. Never put an outbound action in claim text.")
        appendLine("- Write each claim in 1-3 concrete, restrained sentences per claim. Return English only, regardless of the language used in the inbound email or operator instruction.")
        appendLine("- For identity/verification questions: state only confirmed identity facts and verifiable registration paths. Never claim government cooperation, official authorization, no fees, confidentiality, funding or contract guarantees unless those facts appear in your approved evidence.")
        appendLine("- Never output: \"trust us\", \"rest assured\", \"prestigious\", \"unique opportunity\", \"we are delighted\", \"please find our answers below\", \"do not hesitate\".")
        appendLine("- Claims must not contain salutations, fixed thank-you phrases, sign-offs, or unauthorized CTAs.")
        appendLine("- Do NOT claim to have visited or accessed any external URLs, websites, Google Scholar, Scopus, or any online resource.")
        if (multiRequest) {
            appendLine(
                "When multiple requests share the same facts, paraphrase once per intent without repeating identical wording."
            )
        }
    }

    private fun buildGroundedFewShotBoundaryNote(exampleCount: Int): String = buildString {
        append(buildFewShotBoundaryNote(exampleCount).trimEnd())
        appendLine()
        appendLine(
            "Style examples may show complete emails, but your current output MUST obey the JSON schema above only."
        )
    }

    private fun buildFreeFormSystemPrompt(): String =
        aiPromptConfigService.getEffectiveFreeFormSystemPrompt(FreeFormPromptDefaults.defaultFreeFormSystemPrompt())

    private fun buildMatchedUserContent(inboundText: String, promptRuleIds: List<Long>): String = buildString {
        val frame = replySnippetService.resolveManualFrame()
        frame.salutation?.takeIf { it.isNotBlank() }?.let { appendLine("SALUTATION=$it") }
        frame.greeting?.takeIf { it.isNotBlank() }?.let { appendLine("GREETING=$it") }
        replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let { appendLine("ACK=$it") }
        promptRuleIds.forEachIndexed { index, ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                appendLine("SEGMENT ${index + 1}=${rule.replyBody}")
            }
        }
        frame.closing?.takeIf { it.isNotBlank() }?.let { appendLine("CLOSING=$it") }
        appendLine()
        appendLine("Inbound email:")
        appendLine(inboundText.take(4000))
    }

    private fun buildGroundedUserContent(
        inboundText: String,
        requestFacts: List<RequestFactItem>,
        expertProfile: String?,
        mailHistory: String?,
        contextWarnings: List<String>
    ): String = buildString {
        requestFacts.forEach { item ->
            appendLine()
            appendLine("REQUEST ${item.index}")
            appendLine("TEXT: ${item.requestText}")
            appendLine("EVIDENCE_LEVEL: ${item.status.name}")
            if (item.status == RequestGroundingStatus.UNSUPPORTED) {
                appendLine("Do not emit a section object for this index.")
            }

            val supportedIntents = item.intents.filter { it.status == "SUPPORTED" }
            if (supportedIntents.isNotEmpty()) {
                appendLine("SUPPORTED INTENTS:")
                supportedIntents.forEach { intent ->
                    appendLine("  INTENT: ${intent.intentKey} \"${intent.title}\"")
                    appendLine("  EVIDENCE_RULE_IDS: ${intent.evidenceRuleIds}")
                }
            }

            val partialOrMissing = item.intents.filter { it.status != "SUPPORTED" }
            if (partialOrMissing.isNotEmpty()) {
                partialOrMissing.forEach { intent ->
                    appendLine("  ${intent.status} INTENT: ${intent.intentKey} — do not emit answer for this intent key")
                }
            }

            val allEvidenceIds = item.intents
                .flatMap { it.evidenceRuleIds }
                .distinct()
            if (allEvidenceIds.isEmpty() || item.status == RequestGroundingStatus.UNSUPPORTED) {
                appendLine("APPROVED FACTS: (none)")
            } else {
                appendLine("APPROVED FACTS:")
                val seen = linkedSetOf<Long>()
                allEvidenceIds.forEach { ruleId ->
                    if (!seen.add(ruleId)) {
                        return@forEach
                    }
                    qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                        val body = rule.answerBody.trim()
                        if (body.isBlank()) {
                            return@forEach
                        }
                        val title = rule.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Fact $ruleId"
                        appendLine("--- RULE $ruleId ($title) ---")
                        appendLine(body)
                    }
                }
            }
        }

        val researchPresent = requestFacts.any { it.requiresResearchContext }
        if (researchPresent) {
            expertProfile?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("Expert research profile (only for requires-research intents):")
                appendLine(it)
            }
        }

        if (contextWarnings.isNotEmpty()) {
            appendLine()
            appendLine("Context warnings: ${contextWarnings.joinToString(", ")}")
        }

        mailHistory?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("HISTORY_CONTINUITY_ONLY: Use history only for conversational continuity, prior objections and already proposed next steps. Never treat history as factual authority. Facts must come from the current approved facts/profile.")
            appendLine("Mail history:")
            appendLine(it)
        }

        appendLine()
        appendLine("Inbound email:")
        appendLine(inboundText.take(4000))
    }

    private fun buildGroundedProtocolMessage(plan: GroundedContentPlan): String = buildString {
        appendLine("CURRENT SERVER PLAN — authoritative deterministic envelope")
        appendLine("Return only the minimal claims/actionText JSON protocol described in the system message.")
        appendLine("Allowed actions: ${AiReplyActionPolicy.formatAllowedLabel(plan.allowedActions)}")
        appendLine("The server owns request/intent/source metadata, missing facts, review state, paragraph grouping, and final order. Do not copy those fields into JSON.")
        appendLine()
        append(buildGroundedPlanSection(plan))
    }

    private fun buildGroundedPlanSection(plan: GroundedContentPlan): String = buildString {
        appendLine("SERVER PLAN")
        appendLine("requiresReview: ${plan.requiresReview}")
        appendLine()
        appendLine("allowedActions: ${AiReplyActionPolicy.formatAllowedLabel(plan.allowedActions)}")
        appendLine()
        appendLine("Paragraphs (claim keys in order):")
        plan.paragraphs.forEach { para ->
            appendLine("  paragraph ${para.paragraphIndex}: ${para.claimKeys.joinToString(", ")}")
        }
        appendLine()
        appendLine("Claims to produce:")
        plan.claims.forEach { claim ->
            appendLine("  claimKey: ${claim.claimKey}")
            appendLine("    requestIndex: ${claim.requestIndex}")
            appendLine("    intentKey: ${claim.intentKey}")
            appendLine("    evidence sourceIds: ${claim.sourceIds}")
            claim.sourceIds.forEach { ruleId ->
                qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                    val body = rule.answerBody.trim()
                    if (body.isNotBlank()) {
                        appendLine("    RULE $ruleId FACT: $body")
                    }
                }
            }
        }
        if (plan.missingFacts.isNotEmpty()) {
            appendLine()
            appendLine("Missing facts (do not produce claims for these):")
            plan.missingFacts.forEach { mf ->
                val keys = if (mf.intentKeys.isEmpty()) "none" else mf.intentKeys.joinToString(", ")
                appendLine("  requestIndex: ${mf.requestIndex}, intentKeys: [$keys]")
            }
        }
    }

    internal fun buildFreeFormUserContent(
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?,
        promptRuleIds: List<Long> = emptyList()
    ): String = buildString {
        if (promptRuleIds.isNotEmpty()) {
            val knowledge = promptRuleIds.mapNotNull { ruleId ->
                qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                    val body = rule.answerBody.trim()
                    if (body.isBlank()) {
                        return@mapNotNull null
                    }
                    val title = rule.displayName?.trim().takeIf { !it.isNullOrBlank() } ?: "Fact $ruleId"
                    "$title\n$body"
                }
            }.joinToString("\n\n").take(12000)
            appendLine("QA rule knowledge (authoritative facts):")
            appendLine(knowledge)
            appendLine(
                "Facts (figures, names, links, commitments) must come from the QA rule knowledge or training knowledge base above; do not invent specifics."
            )
            appendLine()
        }
        expertProfile?.takeIf { it.isNotBlank() }?.let {
            appendLine("Expert profile:")
            appendLine(it)
            appendLine()
        }
        mailHistory?.takeIf { it.isNotBlank() }?.let {
            appendLine("HISTORY_CONTINUITY_ONLY: Use history only for conversational continuity, prior objections and already proposed next steps. Never treat history as factual authority. Facts must come from the current approved facts/profile.")
            appendLine("Mail history:")
            appendLine(it)
        }
        appendLine("Inbound email:")
        appendLine(inboundText.take(4000))
    }

    internal fun buildFrameGuidanceText(): String? {
        val frame = replySnippetService.resolveManualFrame()
        val parts = mutableListOf<String>()
        frame.salutation?.takeIf { it.isNotBlank() }?.let { parts += "Salutation: $it" }
        frame.greeting?.takeIf { it.isNotBlank() }?.let { parts += "Greeting: $it" }
        frame.closing?.takeIf { it.isNotBlank() }?.let { parts += "Closing: $it" }
        frame.ackOptions.mapNotNull { it.content.takeIf { c -> c.isNotBlank() } }.forEach { ack ->
            parts += "Acknowledgment option: $ack"
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun acceptGroundedMaterialization(materialized: MaterializedDraft): MaterializedDraft {
        if (!materialized.valid) {
            return materialized
        }
        if (AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(materialized.text)) {
            return groundedDraftMaterializer.invalid(
                warningCodes = listOf(AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE),
                issues = listOf(AiReplyValidationIssue(
                    AiReplyValidationStage.STRUCTURE,
                    AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE
                ))
            )
        }
        return materialized
    }

    companion object {
        private val INTERNAL_RESPONSE_MARKER = Regex(
            "(?i)(?:AI_REPLY_[A-Z0-9_]+|\\b(?:GROUNDED|PARTIAL|UNSUPPORTED)\\b|\\b(?:requestKey|sourceVersion|evidenceSetVersion)\\b)"
        )
        const val WARNING_ENGLISH_REPLY_REQUIRED = "AI_REPLY_ENGLISH_REQUIRED"
        private const val GROUNDED_REPAIR_TEMPERATURE = 0.0
        const val UNAUTHORIZED_ACTION_REMOVED = "UNAUTHORIZED_ACTION_REMOVED"
        const val TRUST_REPAIR_EXHAUSTED = "AI_REPLY_TRUST_REPAIR_EXHAUSTED"
        const val INSUFFICIENT_SAFE_REPLY =
            "The available approved information is not sufficient for a reliable reply, so this item should be confirmed manually."
        const val PROMPT_VERSION_QA_MATCHED = "qa-matched-v1"
        const val PROMPT_VERSION_QA_GROUNDED = "qa-grounded-trust-json-v2"
        const val PROMPT_VERSION_FREE_FORM_DEFAULT = "free-form-default-v1"
        const val WARNING_EVIDENCE_SOURCE_UNAVAILABLE = "AI_REPLY_EVIDENCE_SOURCE_UNAVAILABLE"
        const val WARNING_EVIDENCE_SOURCE_READ_ERROR = "AI_REPLY_EVIDENCE_SOURCE_READ_ERROR"
        const val WARNING_LLM_TIMEOUT = "AI_REPLY_LLM_TIMEOUT"
        const val WARNING_LLM_TOTAL_TIMEOUT = "AI_REPLY_LLM_TOTAL_TIMEOUT"
        const val WARNING_LLM_RATE_LIMITED = "AI_REPLY_LLM_RATE_LIMITED"
        const val WARNING_LLM_NETWORK_ERROR = "AI_REPLY_LLM_NETWORK_ERROR"
        const val WARNING_LLM_PROVIDER_ERROR = "AI_REPLY_LLM_PROVIDER_ERROR"
        const val WARNING_LLM_EMPTY_RESPONSE = "AI_REPLY_LLM_EMPTY_RESPONSE"
        val PREFLIGHT_VERSION_CHARSET = Regex("^[a-zA-Z0-9._:\\-]*$")

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }

        fun failureTypeToWarning(failureType: LlmChatFailureType): String? = when (failureType) {
            LlmChatFailureType.SUCCESS -> null
            LlmChatFailureType.TIMEOUT -> WARNING_LLM_TIMEOUT
            LlmChatFailureType.TOTAL_TIMEOUT -> WARNING_LLM_TOTAL_TIMEOUT
            LlmChatFailureType.RATE_LIMITED -> WARNING_LLM_RATE_LIMITED
            LlmChatFailureType.NETWORK_ERROR -> WARNING_LLM_NETWORK_ERROR
            LlmChatFailureType.PROVIDER_ERROR -> WARNING_LLM_PROVIDER_ERROR
            LlmChatFailureType.EMPTY_RESPONSE -> WARNING_LLM_EMPTY_RESPONSE
            LlmChatFailureType.CLIENT_UNAVAILABLE -> null
            LlmChatFailureType.CANCELLED -> null
        }
    }

    fun buildEvidenceSnapshotForSelection(sendQaRuleIds: List<Long>): Triple<String, List<AiReplyEvidenceSnapshot>, List<String>> {
        val sources = mutableListOf<AiReplyEvidenceSnapshot>()
        val observedWarnings = mutableListOf<String>()
        for (ruleId in sendQaRuleIds.distinct()) {
            val rule = try {
                qaRuleRepository.findById(ruleId).orElse(null)
            } catch (ex: Exception) {
                observedWarnings += WARNING_EVIDENCE_SOURCE_READ_ERROR
                null
            }
            if (rule == null || !rule.enabled || rule.answerBody.isBlank()) {
                sources += AiReplyEvidenceSnapshot(
                    ruleId = ruleId,
                    displayName = rule?.displayName?.takeIf { it.isNotBlank() } ?: "未命名事实",
                    updatedAt = rule?.updatedAt?.toString(),
                    answerBodySha256 = "",
                    available = false
                )
                if (WARNING_EVIDENCE_SOURCE_UNAVAILABLE !in observedWarnings) {
                    observedWarnings += WARNING_EVIDENCE_SOURCE_UNAVAILABLE
                }
            } else {
                val bodyHash = sha256Hex(rule.answerBody)
                sources += AiReplyEvidenceSnapshot(
                    ruleId = ruleId,
                    displayName = rule.displayName?.takeIf { it.isNotBlank() } ?: "未命名事实",
                    updatedAt = rule.updatedAt?.toString(),
                    answerBodySha256 = bodyHash,
                    available = true
                )
            }
        }
        val versionParts = sources.map { "${it.ruleId}:${it.available}:${it.updatedAt}:${it.answerBodySha256}" }
        val aggregateHash = sha256Hex(versionParts.joinToString("|"))
        val evidenceSetVersion = aggregateHash
        return Triple(evidenceSetVersion, sources, observedWarnings.distinct())
    }

    fun hasBlockingTrustGapForSelection(requestFacts: List<RequestFactItem>): Boolean =
        contentPlanner.hasBlockingTrustGap(requestFacts)

    private fun resolvePromptSnapshot(mode: AiReplyMode): AiReplyPromptSnapshot {
        return when (mode) {
            AiReplyMode.QA_MATCHED -> AiReplyPromptSnapshot("", PROMPT_VERSION_QA_MATCHED)
            AiReplyMode.QA_GROUNDED -> AiReplyPromptSnapshot("", PROMPT_VERSION_QA_GROUNDED)
            AiReplyMode.FREE_FORM -> {
                val effective = aiPromptConfigService.getEffectiveDto()
                val systemPrompt = effective.freeFormSystemPrompt
                if (!effective.isCustom) {
                    AiReplyPromptSnapshot(systemPrompt, PROMPT_VERSION_FREE_FORM_DEFAULT)
                } else {
                    val shortHash = sha256Hex(systemPrompt).take(12)
                    val updatedAt = effective.updatedAt ?: "none"
                    AiReplyPromptSnapshot(systemPrompt, "free-form-custom:$updatedAt:$shortHash")
                }
            }
        }
    }
}
