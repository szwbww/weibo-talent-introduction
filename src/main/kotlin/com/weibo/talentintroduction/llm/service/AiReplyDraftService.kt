package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

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

data class AiReplyTurn(
    val assistantDraft: String,
    val operatorInstruction: String
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
    val draftReadiness: AiReplyDraftReadiness = AiReplyDraftReadiness.READY
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
    private val claimValidator: AiReplyHighRiskClaimValidator
) {
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
            !contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
    ): AiReplyDraftResult {
        val selectedModel = AiReplyModel.fromNullable(replyModel)
        val providerModel = selectedModel.resolveProviderModel(properties)
        val resolved = resolveQaRules(
            inboundText,
            qaRuleIds,
            contextWarnings,
            researchProfileSufficient
        )
        val mode = when {
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

        if (!properties.enabled) {
            return enforceActionPolicy(
                draftText = fallbackDraftText(
                    resolved = resolved,
                    operatorTurns = operatorTurns,
                    lastDraft = lastDraft,
                    inboundText = inboundText,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    operatorInstruction = operatorInstruction,
                    mode = mode
                ),
                usedLlm = false,
                generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED,
                allowedActions = allowedActions,
                messages = null,
                client = null,
                temperature = null,
                providerModel = providerModel,
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = emptyList(),
                contextWarnings = contextWarnings
            )
        }

        val client = llmDraftClientProvider.getIfAvailable()
        if (client == null) {
            return enforceActionPolicy(
                draftText = fallbackDraftText(
                    resolved = resolved,
                    operatorTurns = operatorTurns,
                    lastDraft = lastDraft,
                    inboundText = inboundText,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    operatorInstruction = operatorInstruction,
                    mode = mode
                ),
                usedLlm = false,
                generationState = AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE,
                allowedActions = allowedActions,
                messages = null,
                client = null,
                temperature = null,
                providerModel = providerModel,
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = emptyList(),
                contextWarnings = contextWarnings
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
                    operatorInstruction = operatorInstruction
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
                    operatorInstruction = operatorInstruction
                ).messages
            }
            AiReplyMode.FREE_FORM -> {
                val buildResult = buildFreeFormMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    operatorInstruction = operatorInstruction,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    promptRuleIds = resolved.promptRuleIds
                )
                fewShotDialogRefs = buildResult.fewShotDialogRefs
                buildResult.messages
            }
        }
        val boundedMessages = withActionBoundary(messages, allowedActions)
        val temperature = when (mode) {
            AiReplyMode.QA_GROUNDED, AiReplyMode.QA_MATCHED -> properties.freeFormTemperature
            AiReplyMode.FREE_FORM -> properties.freeFormTemperature
        }
        val llmText = try {
            client.chatWithModel(boundedMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        return if (llmText != null) {
            if (mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED) {
                val materialized = acceptGroundedMaterialization(
                    groundedDraftMaterializer.materialize(llmText, resolved.requestFacts)
                )
                if (materialized.valid) {
                    val claimResult = claimValidator.validate(materialized.sections, resolved.requestFacts)
                    if (claimResult.valid) {
                        enforceActionPolicy(
                            draftText = materialized.text,
                            usedLlm = true,
                            generationState = AiReplyGenerationState.LLM_USED,
                            allowedActions = allowedActions,
                            messages = boundedMessages,
                            client = client,
                            temperature = temperature,
                            providerModel = providerModel,
                            selectedModel = selectedModel.name,
                            resolved = resolved,
                            mode = mode,
                            fewShotDialogRefs = fewShotDialogRefs,
                            contextWarnings = contextWarnings
                        )
                    } else {
                        enforceActionPolicy(
                            draftText = fallbackDraftText(
                                resolved = resolved,
                                operatorTurns = operatorTurns,
                                lastDraft = lastDraft,
                                inboundText = inboundText,
                                expertProfile = expertProfile,
                                mailHistory = mailHistory,
                                operatorInstruction = operatorInstruction,
                                mode = mode
                            ),
                            usedLlm = false,
                            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                            allowedActions = allowedActions,
                            messages = null,
                            client = null,
                            temperature = null,
                            providerModel = providerModel,
                            selectedModel = selectedModel.name,
                            resolved = resolved,
                            mode = mode,
                            fewShotDialogRefs = emptyList(),
                            contextWarnings = contextWarnings + claimResult.warningCodes
                        )
                    }
                } else {
                    enforceActionPolicy(
                        draftText = fallbackDraftText(
                            resolved = resolved,
                            operatorTurns = operatorTurns,
                            lastDraft = lastDraft,
                            inboundText = inboundText,
                            expertProfile = expertProfile,
                            mailHistory = mailHistory,
                            operatorInstruction = operatorInstruction,
                            mode = mode
                        ),
                        usedLlm = false,
                        generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                        allowedActions = allowedActions,
                        messages = null,
                        client = null,
                        temperature = null,
                        providerModel = providerModel,
                        selectedModel = selectedModel.name,
                        resolved = resolved,
                        mode = mode,
                        fewShotDialogRefs = emptyList(),
                        contextWarnings = contextWarnings + materialized.warningCodes
                    )
                }
            } else {
                enforceActionPolicy(
                    draftText = llmText,
                    usedLlm = true,
                    generationState = AiReplyGenerationState.LLM_USED,
                    allowedActions = allowedActions,
                    messages = boundedMessages,
                    client = client,
                    temperature = temperature,
                    providerModel = providerModel,
                    selectedModel = selectedModel.name,
                    resolved = resolved,
                    mode = mode,
                    fewShotDialogRefs = fewShotDialogRefs,
                    contextWarnings = contextWarnings
                )
            }
        } else {
            enforceActionPolicy(
                draftText = fallbackDraftText(
                    resolved = resolved,
                    operatorTurns = operatorTurns,
                    lastDraft = lastDraft,
                    inboundText = inboundText,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    operatorInstruction = operatorInstruction,
                    mode = mode
                ),
                usedLlm = false,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                allowedActions = allowedActions,
                messages = null,
                client = null,
                temperature = null,
                providerModel = providerModel,
                selectedModel = selectedModel.name,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = emptyList(),
                contextWarnings = contextWarnings
            )
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
        contextWarnings: List<String>
    ): AiReplyDraftResult {
        var text = draftText
        var used = usedLlm
        val warnings = contextWarnings.toMutableList()

        var violations = AiReplyActionPolicy.findViolations(text, allowedActions)
        if (violations.isNotEmpty() && client != null && messages != null) {
            val correction = buildActionCorrectionMessage(violations, allowedActions, mode)
            val retryMessages = messages + LlmChatMessage(role = "user", content = correction)
            val retryText = try {
                client.chatWithModel(retryMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
            if (retryText != null) {
                if (mode == AiReplyMode.QA_GROUNDED) {
                    val materialized = acceptGroundedMaterialization(
                        groundedDraftMaterializer.materialize(retryText, resolved.requestFacts)
                    )
                    if (materialized.valid) {
                        val claimResult = claimValidator.validate(materialized.sections, resolved.requestFacts)
                        if (claimResult.valid) {
                            text = materialized.text
                            used = true
                            violations = AiReplyActionPolicy.findViolations(text, allowedActions)
                        } else {
                            if (AiReplyGroundedDraftMaterializer.WARNING_CLAIM_VALIDATION_FAILED !in warnings) {
                                warnings += AiReplyGroundedDraftMaterializer.WARNING_CLAIM_VALIDATION_FAILED
                            }
                            warnings += claimResult.warningCodes
                            val fallbackText = aiReplyPointByPointComposer.composeFallback(resolved.requestFacts)
                            return AiReplyDraftResult(
                                draftText = fallbackText,
                                usedLlm = false,
                                qaRuleIds = resolved.sendQaRuleIds,
                                mode = mode,
                                fewShotDialogRefs = fewShotDialogRefs,
                                requestCount = resolved.requestCount,
                                groundedRequestCount = resolved.groundedRequestCount,
                                unsupportedRequests = resolved.unsupportedRequests,
                                contextWarnings = warnings,
                                selectedModel = selectedModel,
                                requestFacts = resolved.requestFacts,
                                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                                draftReadiness = resolveDraftReadiness(resolved.requestFacts, resolved.sendQaRuleIds)
                            )
                        }
                    } else {
                        warnings += materialized.warningCodes
                        val fallbackText = aiReplyPointByPointComposer.composeFallback(resolved.requestFacts)
                        return AiReplyDraftResult(
                            draftText = fallbackText,
                            usedLlm = false,
                            qaRuleIds = resolved.sendQaRuleIds,
                            mode = mode,
                            fewShotDialogRefs = fewShotDialogRefs,
                            requestCount = resolved.requestCount,
                            groundedRequestCount = resolved.groundedRequestCount,
                            unsupportedRequests = resolved.unsupportedRequests,
                            contextWarnings = warnings,
                            selectedModel = selectedModel,
                            requestFacts = resolved.requestFacts,
                            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE,
                            draftReadiness = resolveDraftReadiness(resolved.requestFacts)
                        )
                    }
                } else {
                    text = retryText
                    used = true
                    violations = AiReplyActionPolicy.findViolations(text, allowedActions)
                }
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
            // QA_GROUNDED empty fallback must stay blank — never inject internal marker copy (I-2/I-8).
            if (mode != AiReplyMode.QA_GROUNDED) {
                text = INSUFFICIENT_SAFE_REPLY
            }
            if (UNAUTHORIZED_ACTION_REMOVED !in warnings && removed) {
                warnings += UNAUTHORIZED_ACTION_REMOVED
            }
        }
        // Final hard gate: never return unauthorized CTAs.
        val (finalText, finalRemoved) = AiReplyActionPolicy.sanitize(text, allowedActions)
        text = if (finalText.isBlank()) {
            if (mode == AiReplyMode.QA_GROUNDED) "" else INSUFFICIENT_SAFE_REPLY
        } else {
            finalText
        }
        if (finalRemoved && UNAUTHORIZED_ACTION_REMOVED !in warnings) {
            warnings += UNAUTHORIZED_ACTION_REMOVED
        }

        // Retry success stays LLM_USED; sanitize never invents fallback when used=true.
        val finalState = if (used) AiReplyGenerationState.LLM_USED else generationState

        val readiness = resolveDraftReadiness(resolved.requestFacts, resolved.sendQaRuleIds)

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
            draftReadiness = readiness
        )
    }

    private fun buildActionCorrectionMessage(
        violations: List<ActionViolation>,
        allowedActions: Set<AiReplyAction>,
        mode: AiReplyMode
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
                "Return the corrected reply as the same JSON object " +
                    "{\"sections\":[{\"requestIndex\":1,\"answers\":[" +
                    "{\"intentKey\":\"expertise.programme_fit\",\"answer\":\"...\",\"sourceRuleIds\":[24]}" +
                    "]}]} only. " +
                    "No Markdown fence, no salutation, no closing, no STATUS labels."
            )
        } else {
            appendLine("Rewrite the email body only.")
        }
    }

    private fun fallbackDraftText(
        resolved: ResolvedQaRules,
        operatorTurns: List<AiReplyTurn>,
        lastDraft: String?,
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?,
        operatorInstruction: String?,
        mode: AiReplyMode
    ): String {
        return if (operatorTurns.isEmpty()) {
            when {
                mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED ->
                    aiReplyPointByPointComposer.composeFallback(resolved.requestFacts)
                else ->
                    composeFreeFormDeterministicDraft(
                        inboundText = inboundText,
                        expertProfile = expertProfile,
                        mailHistory = mailHistory,
                        operatorInstruction = operatorInstruction
                    )
            }
        } else {
            lastDraft.orEmpty()
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
        if (requestFacts.any { it.status == RequestGroundingStatus.UNSUPPORTED }) {
            return AiReplyDraftReadiness.BLOCKED
        }
        if (evidenceRuleIds.isEmpty()) {
            return AiReplyDraftReadiness.BLOCKED
        }
        if (requestFacts.any { it.status == RequestGroundingStatus.PARTIAL }) {
            return AiReplyDraftReadiness.NEEDS_REVIEW
        }
        val policies = evidenceRuleIds.mapNotNull { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.replyPolicyEnum()
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
        operatorInstruction: String? = null
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
        promptRuleIds: List<Long> = emptyList()
    ): FreeFormBuildResult {
        val fewShots = aiTrainingDialogueService.selectRelevantDialogues(inboundText, max = 2)
        val messages = mutableListOf<LlmChatMessage>()
        val systemPrompt = if (fewShots.isEmpty()) {
            buildFreeFormSystemPrompt()
        } else {
            buildFreeFormSystemPrompt() + buildFewShotBoundaryNote(fewShots.size)
        }
        messages += LlmChatMessage(role = "system", content = systemPrompt)
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
                "{\"sections\":[" +
                "{\"requestIndex\":1,\"answers\":[" +
                "{\"intentKey\":\"expertise.programme_fit\",\"answer\":\"...\"," +
                "\"sourceRuleIds\":[24]}" +
                "]}" +
                "]}"
        )
        appendLine("Rules:")
        appendLine("- Include exactly one section for every request index with supported intents.")
        appendLine("- For each request section, include exactly one answer object per supported intent, with:" +
            " intentKey matching the allowed intent keys, answer text, and sourceRuleIds referencing" +
            " only the evidence rule IDs listed for that intent.")
        appendLine("- Do not include requests with no supported intents.")
        appendLine("- Write natural prose in 2-4 short paragraphs total across all answers; do not use numbered lists, section headings, or internal labels.")
        appendLine("- Use one brief acknowledgment only; do not repeat salutation, thanks, or closing in JSON answers.")
        appendLine("- Do not expose intent keys, rule IDs, GROUNDED/PARTIAL/UNSUPPORTED, or coverage labels in answer text.")
        appendLine("- Each answer uses only that intent's approved facts; do not invent or borrow across intents.")
        appendLine("- sourceRuleIds must be a non-empty subset of the evidence rule IDs listed for that intent.")
        appendLine(
            "Do NOT claim to have visited or accessed any external URLs, websites, Google Scholar, Scopus, " +
                "or any online resource."
        )
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
            appendLine("Mail history:")
            appendLine(it)
        }

        appendLine()
        appendLine("Inbound email:")
        appendLine(inboundText.take(4000))
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
            appendLine("Mail history:")
            appendLine(it)
            appendLine()
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

    internal fun composeFreeFormDeterministicDraft(
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?,
        operatorInstruction: String?
    ): String {
        val frame = replySnippetService.resolveManualFrame()
        return buildString {
            frame.salutation?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            frame.greeting?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            extractTrainingKnowledgeSummary(expertProfile)?.let {
                appendLine(it)
            } ?: appendLine(
                "Thank you for your email. We appreciate your interest and will follow up with more information soon."
            )
            operatorInstruction?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("(Simulation note: ${it.take(500)})")
            }
            frame.closing?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(it)
            }
        }.trim()
    }

    internal fun extractTrainingKnowledgeSummary(expertProfile: String?): String? {
        if (expertProfile.isNullOrBlank()) {
            return null
        }
        val marker = "Training knowledge base:"
        val start = expertProfile.indexOf(marker)
        if (start < 0) {
            return null
        }
        return expertProfile.substring(start + marker.length)
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Answer:") }
            ?.removePrefix("Answer:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun acceptGroundedMaterialization(materialized: MaterializedDraft): MaterializedDraft {
        if (!materialized.valid) {
            return materialized
        }
        if (AiReplyGroundedDraftMaterializer.containsNonNaturalGroundedStructure(materialized.text)) {
            return groundedDraftMaterializer.invalid(
                warningCodes = listOf(AiReplyGroundedDraftMaterializer.WARNING_UNNATURAL_GROUNDED_STRUCTURE)
            )
        }
        return materialized
    }

    companion object {
        const val UNAUTHORIZED_ACTION_REMOVED = "UNAUTHORIZED_ACTION_REMOVED"
        const val INSUFFICIENT_SAFE_REPLY =
            "The available approved information is not sufficient for a reliable reply, so this item should be confirmed manually."
    }
}
