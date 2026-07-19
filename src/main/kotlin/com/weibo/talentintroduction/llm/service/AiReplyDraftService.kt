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
    private val claimValidator: AiReplyHighRiskClaimValidator,
    private val contentPlanner: AiReplyGroundedContentPlanner
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
                generationState = AiReplyGenerationState.FALLBACK_LLM_DISABLED
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
                generationState = AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE
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
                    promptRuleIds = resolved.promptRuleIds
                )
                fewShotDialogRefs = buildResult.fewShotDialogRefs
                buildResult.messages
            }
        }
        val boundedMessages = withActionBoundary(messages, plan.allowedActions)
        val temperature = when (mode) {
            AiReplyMode.QA_GROUNDED, AiReplyMode.QA_MATCHED -> properties.freeFormTemperature
            AiReplyMode.FREE_FORM -> properties.freeFormTemperature
        }

        if (mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED) {
            return generateGrounded(
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
                operatorInstruction = operatorInstruction
            )
        }

        val llmText = try {
            client.chatWithModel(boundedMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        return if (llmText != null) {
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
                plan = plan
            )
        } else {
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
                contextWarnings = contextWarnings,
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE
            )
        }
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
        operatorInstruction: String?
    ): AiReplyDraftResult {
        val llmText = try {
            client.chatWithModel(boundedMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        if (llmText == null) {
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
                contextWarnings = contextWarnings,
                plan = plan,
                allowedActions = plan.allowedActions,
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE
            )
        }

        val blockingTrust = contentPlanner.hasBlockingTrustGap(resolved.requestFacts)
        val firstResult = materializeAndValidateGroundedCandidate(
            rawResponse = llmText,
            plan = plan,
            resolved = resolved,
            allowedActions = plan.allowedActions,
            hasBlockingTrustGap = blockingTrust
        )

        if (firstResult.valid) {
            return buildGroundedResult(
                validated = firstResult,
                usedLlm = true,
                generationState = AiReplyGenerationState.LLM_USED,
                selectedModel = selectedModel,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan
            )
        }

        val correctionMsg = buildTrustCorrectionMessage(
            warningCodes = firstResult.allWarnings,
            allowedActions = plan.allowedActions
        )
        val retryMessages = boundedMessages + LlmChatMessage(role = "user", content = correctionMsg)
        val retryText = try {
            client.chatWithModel(retryMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        if (retryText == null) {
            val mergedWarnings = contextWarnings.toMutableList()
            mergedWarnings += firstResult.allWarnings
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
                generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE
            )
        }

        val retryResult = materializeAndValidateGroundedCandidate(
            rawResponse = retryText,
            plan = plan,
            resolved = resolved,
            allowedActions = plan.allowedActions,
            hasBlockingTrustGap = blockingTrust
        )

        if (retryResult.valid) {
            return buildGroundedResult(
                validated = retryResult,
                usedLlm = true,
                generationState = AiReplyGenerationState.LLM_USED,
                selectedModel = selectedModel,
                resolved = resolved,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings,
                plan = plan
            )
        }

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
            generationState = AiReplyGenerationState.FALLBACK_NO_RESPONSE
        )
    }

    private data class GroundedValidationResult(
        val valid: Boolean,
        val text: String,
        val sections: List<ValidatedSection>,
        val allWarnings: List<String>
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
                allWarnings = warnings.distinct()
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
                hasBlockingTrustGap = hasBlockingTrustGap
            )
        )
        if (!trustResult.valid) {
            warnings += trustResult.warningCodes
        }

        var text = materialized.text
        val actionViolations = AiReplyActionPolicy.findViolations(text, allowedActions)
        if (actionViolations.isNotEmpty()) {
            actionViolations.forEach { v ->
                val code = v.code ?: UNAUTHORIZED_ACTION_REMOVED
                if (code !in warnings) {
                    warnings += code
                }
            }
        }

        if (warnings.isNotEmpty()) {
            return GroundedValidationResult(
                valid = false,
                text = text,
                sections = materialized.sections,
                allWarnings = warnings.distinct()
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
        plan: GroundedContentPlan
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
            draftReadiness = readiness
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
        generationState: AiReplyGenerationState
    ): AiReplyDraftResult {
        val fallbackText = if (operatorTurns.isEmpty()) {
            if (mode == AiReplyMode.QA_GROUNDED || mode == AiReplyMode.QA_MATCHED) {
                aiReplyPointByPointComposer.composeFallback(resolved.requestFacts)
            } else {
                composeFreeFormDeterministicDraft(inboundText, expertProfile, mailHistory, operatorInstruction)
            }
        } else {
            lastDraft.orEmpty()
        }

        val finalWarnings = contextWarnings.toMutableList()
        val (sanitized, removed) = AiReplyActionPolicy.sanitize(fallbackText, allowedActions)
        val finalText = if (sanitized.isNotBlank()) sanitized else fallbackText
        if (removed && UNAUTHORIZED_ACTION_REMOVED !in finalWarnings) {
            finalWarnings += UNAUTHORIZED_ACTION_REMOVED
        }

        val readiness = if (TRUST_REPAIR_EXHAUSTED in contextWarnings) {
            AiReplyDraftReadiness.BLOCKED
        } else {
            resolveDraftReadiness(resolved.requestFacts, resolved.sendQaRuleIds)
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
            draftReadiness = readiness
        )
    }

    private fun buildTrustCorrectionMessage(
        warningCodes: List<String>,
        allowedActions: Set<AiReplyAction>
    ): String = buildString {
        appendLine("Your previous draft violated the trust boundary and action policy rules.")
        appendLine("Allowed actions: ${AiReplyActionPolicy.formatAllowedLabel(allowedActions)}.")
        appendLine("Violations:")
        warningCodes.forEach { code ->
            appendLine("- $code")
        }
        appendLine()
        appendLine(
            "Return the corrected reply as the same JSON object " +
                "{\"paragraphs\":[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:company.legal_name\"]}]," +
                "\"claims\":[{\"claimKey\":\"r1:company.legal_name\",\"requestIndex\":1," +
                "\"intentKey\":\"company.legal_name\",\"text\":\"Our registered name is ...\",\"sourceIds\":[24]}]," +
                "\"missingFacts\":[],\"proposedAction\":{\"type\":\"NONE\",\"text\":null},\"requiresReview\":false} only. " +
                "No Markdown fence, no salutation, no closing, no STATUS labels."
        )
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
        plan: GroundedContentPlan
    ): AiReplyDraftResult {
        var text = draftText
        var used = usedLlm
        val warnings = contextWarnings.toMutableList()

        var violations = AiReplyActionPolicy.findViolations(text, allowedActions)
        if (violations.isNotEmpty() && client != null && messages != null) {
            val correction = buildActionCorrectionMessage(violations, allowedActions, mode, plan)
            val retryMessages = messages + LlmChatMessage(role = "user", content = correction)
            val retryText = try {
                client.chatWithModel(retryMessages, temperature, providerModel)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
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
                "Return the corrected reply as the same JSON object " +
                    "{\"paragraphs\":[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:company.legal_name\"]}]," +
                    "\"claims\":[{\"claimKey\":\"r1:company.legal_name\",\"requestIndex\":1," +
                    "\"intentKey\":\"company.legal_name\",\"text\":\"Our registered name is ...\",\"sourceIds\":[24]}]," +
                    "\"missingFacts\":[],\"proposedAction\":{\"type\":\"NONE\",\"text\":null},\"requiresReview\":false} only. " +
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

        val policies = evidenceRuleIds.mapNotNull { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.replyPolicyEnum()
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
            content = buildGroundedUserContent(inboundText, requestFacts, expertProfile, mailHistory, contextWarnings, plan)
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
                "{\"paragraphs\":[{\"paragraphIndex\":1,\"claimKeys\":[\"r1:company.legal_name\"]}]," +
                "\"claims\":[{\"claimKey\":\"r1:company.legal_name\",\"requestIndex\":1," +
                "\"intentKey\":\"company.legal_name\",\"text\":\"Our registered company name is ...\",\"sourceIds\":[24]}]," +
                "\"missingFacts\":[]," +
                "\"proposedAction\":{\"type\":\"NONE\",\"text\":null}," +
                "\"requiresReview\":false}"
        )
        appendLine("Rules:")
        appendLine("- The SERVER PLAN section specifies exactly what claims to produce, their ordering and paragraph grouping, evidence source IDs, missing facts, allowed actions, and whether review is required.")
        appendLine("- claims: produce exactly one claim per claimKey in the server plan. No extra claims; no missing claims.")
        appendLine("- Each claim.text must use only the approved facts listed after that claim's RULE IDs in the SERVER PLAN. Do not invent, combine across intents, or borrow facts from another claim.")
        appendLine("- claim.sourceIds must be a non-empty subset of the evidence rule IDs listed for that claim in the SERVER PLAN.")
        appendLine("- paragraphs: group claims exactly as specified by claimKeys. Do NOT output section headings, numbered lists, bullet points, intent keys, rule IDs, GROUNDED/PARTIAL/UNSUPPORTED or coverage labels in prose.")
        appendLine("- missingFacts: copy exactly from the server plan. Do not add or remove entries.")
        appendLine("- proposedAction: one of {\"type\":\"NONE\",\"text\":null}, {\"type\":\"REQUEST_MATERIALS\",\"text\":\"...\"}, {\"type\":\"PROPOSE_MEETING\",\"text\":\"...\"}. Only pick a type listed in allowed outbound actions. text must be null for NONE; non-null for others. The action text in the draft body must match this declaration exactly.")
        appendLine("- requiresReview: copy the boolean from the server plan.")
        appendLine("- Write each claim in 1-3 concrete, restrained sentences per claim. Use the same language as the inbound email.")
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
        contextWarnings: List<String>,
        plan: GroundedContentPlan
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
        appendLine(buildGroundedPlanSection(plan))

        appendLine()
        appendLine("Inbound email:")
        appendLine(inboundText.take(4000))
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
        const val TRUST_REPAIR_EXHAUSTED = "AI_REPLY_TRUST_REPAIR_EXHAUSTED"
        const val INSUFFICIENT_SAFE_REPLY =
            "The available approved information is not sufficient for a reliable reply, so this item should be confirmed manually."
    }
}
