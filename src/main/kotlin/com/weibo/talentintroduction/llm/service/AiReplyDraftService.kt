package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

enum class AiReplyMode {
    QA_MATCHED,
    QA_GROUNDED,
    FREE_FORM
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
    val contextWarnings: List<String> = emptyList()
)

internal data class FreeFormBuildResult(
    val messages: List<LlmChatMessage>,
    val fewShotDialogRefs: List<String>
)

internal data class ResolvedQaRules(
    val sendQaRuleIds: List<Long>,
    val promptRuleIds: List<Long>,
    val requestItems: List<String> = emptyList(),
    val unsupportedRequests: List<String> = emptyList(),
    val requestCount: Int = 0,
    val groundedRequestCount: Int = 0
)

@Service
class AiReplyDraftService(
    private val properties: LlmProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val qaMatchService: QaMatchService,
    private val qaRuleRepository: QaRuleRepository,
    private val llmStitchService: LlmStitchService,
    private val replySnippetService: ReplySnippetService,
    private val aiPromptConfigService: AiPromptConfigService,
    private val aiTrainingDialogueService: AiTrainingDialogueService,
    private val aiReplyContextService: AiReplyContextService
) {
    fun generate(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        qaRuleIds: List<Long>? = null,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null,
        simulateOnly: Boolean = false, // deprecated: has no effect; do not read
        contextWarnings: List<String> = emptyList()
    ): AiReplyDraftResult {
        val resolved = resolveQaRules(inboundText, qaRuleIds, contextWarnings)
        val mode = when {
            resolved.sendQaRuleIds.isEmpty() -> AiReplyMode.FREE_FORM
            resolved.requestCount <= 1 &&
                resolved.unsupportedRequests.isEmpty() &&
                resolved.requestItems.none { aiReplyContextService.requiresResearchContext(it) } ->
                AiReplyMode.QA_MATCHED
            else -> AiReplyMode.QA_GROUNDED
        }
        val lastDraft = operatorTurns.lastOrNull()?.assistantDraft

        if (!properties.enabled) {
            return fallback(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                mode = mode,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                contextWarnings = contextWarnings
            )
        }

        val fewShotDialogRefs: List<String>
        val messages = when (mode) {
            AiReplyMode.QA_MATCHED -> {
                fewShotDialogRefs = emptyList()
                buildMatchedMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    promptRuleIds = resolved.promptRuleIds,
                    operatorInstruction = operatorInstruction
                )
            }
            AiReplyMode.QA_GROUNDED -> {
                val buildResult = buildGroundedMessages(
                    inboundText = inboundText,
                    operatorTurns = operatorTurns,
                    promptRuleIds = resolved.promptRuleIds,
                    requestItems = resolved.requestItems,
                    expertProfile = expertProfile,
                    mailHistory = mailHistory,
                    contextWarnings = contextWarnings,
                    operatorInstruction = operatorInstruction
                )
                fewShotDialogRefs = buildResult.fewShotDialogRefs
                buildResult.messages
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
        val temperature = when (mode) {
            AiReplyMode.QA_MATCHED -> properties.temperature
            AiReplyMode.QA_GROUNDED, AiReplyMode.FREE_FORM -> properties.freeFormTemperature
        }
        val llmText = try {
            llmDraftClientProvider.getIfAvailable()
                ?.chat(messages, temperature)
                ?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        return if (llmText != null) {
            AiReplyDraftResult(
                draftText = llmText,
                usedLlm = true,
                qaRuleIds = resolved.sendQaRuleIds,
                mode = mode,
                fewShotDialogRefs = fewShotDialogRefs,
                requestCount = resolved.requestCount,
                groundedRequestCount = resolved.groundedRequestCount,
                unsupportedRequests = resolved.unsupportedRequests,
                contextWarnings = contextWarnings
            )
        } else {
            fallback(
                resolved = resolved,
                operatorTurns = operatorTurns,
                lastDraft = lastDraft,
                mode = mode,
                inboundText = inboundText,
                expertProfile = expertProfile,
                mailHistory = mailHistory,
                operatorInstruction = operatorInstruction,
                fewShotDialogRefs = fewShotDialogRefs,
                contextWarnings = contextWarnings
            )
        }
    }

    internal fun resolveQaRules(
        inboundText: String,
        qaRuleIds: List<Long>?,
        contextWarnings: List<String> = emptyList()
    ): ResolvedQaRules {
        val composition = qaMatchService.suggestComposition(inboundText)
        val gapItems = composition.gapItems
        val requestItems = gapItems.map { it.text }

        val sendQaRuleIds: List<Long>
        val promptRuleIds: List<Long>
        if (qaRuleIds != null) {
            sendQaRuleIds = qaRuleIds
            promptRuleIds = qaRuleIds
        } else {
            val matched = composition.suggestedRuleIds
            sendQaRuleIds = matched
            promptRuleIds = if (matched.isNotEmpty()) matched else qaRuleRepository.findAllEnabledOrdered().mapNotNull { it.id }
        }

        val insufficient = contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        val unsupportedRequests = mutableListOf<String>()
        var groundedCount = 0
        for (item in gapItems) {
            val isResearch = aiReplyContextService.requiresResearchContext(item.text)
            if (isResearch) {
                if (insufficient) {
                    unsupportedRequests += item.text
                } else {
                    groundedCount++
                }
            } else {
                if (item.candidateRuleIds.isNotEmpty()) {
                    groundedCount++
                } else {
                    unsupportedRequests += item.text
                }
            }
        }

        return ResolvedQaRules(
            sendQaRuleIds = sendQaRuleIds,
            promptRuleIds = promptRuleIds,
            requestItems = requestItems,
            unsupportedRequests = unsupportedRequests,
            requestCount = gapItems.size,
            groundedRequestCount = groundedCount
        )
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
        promptRuleIds: List<Long>,
        requestItems: List<String>,
        expertProfile: String?,
        mailHistory: String?,
        contextWarnings: List<String>,
        operatorInstruction: String? = null
    ): FreeFormBuildResult {
        val fewShots = aiTrainingDialogueService.selectRelevantDialogues(inboundText, max = 1)
        val messages = mutableListOf<LlmChatMessage>()
        val systemPrompt = if (fewShots.isEmpty()) {
            buildGroundedSystemPrompt()
        } else {
            buildGroundedSystemPrompt() + buildFewShotBoundaryNote(fewShots.size)
        }
        messages += LlmChatMessage(role = "system", content = systemPrompt)
        fewShots.forEach { dialogue ->
            messages += dialogue.messages
        }
        messages += LlmChatMessage(
            role = "user",
            content = buildGroundedUserContent(inboundText, promptRuleIds, requestItems, expertProfile, mailHistory, contextWarnings)
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

    private fun buildGroundedSystemPrompt(): String = buildString {
        append(buildBaseSystemPrompt())
        appendLine()
        appendLine("The following QA answers, expert training data, and expert profile are the factual boundary for your reply.")
        appendLine("Answer each stated request in order, based only on the provided facts.")
        appendLine(
            "If the information needed for a specific request is insufficient, explicitly state that you will " +
                "follow up later (pending) — do not speculate or invent facts."
        )
        appendLine(
            "Do NOT claim to have visited or accessed any external URLs, websites, Google Scholar, Scopus, " +
                "or any online resource."
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
        promptRuleIds: List<Long>,
        requestItems: List<String>,
        expertProfile: String?,
        mailHistory: String?,
        contextWarnings: List<String>
    ): String = buildString {
        val frame = replySnippetService.resolveManualFrame()
        frame.salutation?.takeIf { it.isNotBlank() }?.let { appendLine("SALUTATION=$it") }
        frame.greeting?.takeIf { it.isNotBlank() }?.let { appendLine("GREETING=$it") }
        replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let { appendLine("ACK=$it") }

        if (promptRuleIds.isNotEmpty()) {
            val facts = promptRuleIds.mapNotNull { ruleId ->
                qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                    "${rule.replySubject.orEmpty()}\n${rule.replyBody}"
                }
            }.joinToString("\n\n").take(12000)
            appendLine()
            appendLine("Matched QA answers (authoritative facts):")
            appendLine(facts)
        }

        expertProfile?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Expert profile:")
            appendLine(it)
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

        if (requestItems.isNotEmpty()) {
            appendLine()
            appendLine("Request checklist (answer each in order):")
            requestItems.forEachIndexed { idx, item ->
                appendLine("${idx + 1}. $item")
            }
        }

        frame.closing?.takeIf { it.isNotBlank() }?.let { appendLine("CLOSING=$it") }

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
                    "${rule.replySubject.orEmpty()}\n${rule.replyBody}"
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

    private fun fallback(
        resolved: ResolvedQaRules,
        operatorTurns: List<AiReplyTurn>,
        lastDraft: String?,
        mode: AiReplyMode,
        inboundText: String = "",
        expertProfile: String? = null,
        mailHistory: String? = null,
        operatorInstruction: String? = null,
        fewShotDialogRefs: List<String> = emptyList(),
        contextWarnings: List<String> = emptyList()
    ): AiReplyDraftResult {
        val draftText = if (operatorTurns.isEmpty()) {
            when {
                resolved.promptRuleIds.isNotEmpty() ->
                    llmStitchService.composeDeterministicDraft(resolved.promptRuleIds)
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
        return AiReplyDraftResult(
            draftText = draftText,
            usedLlm = false,
            qaRuleIds = resolved.sendQaRuleIds,
            mode = mode,
            fewShotDialogRefs = fewShotDialogRefs,
            requestCount = resolved.requestCount,
            groundedRequestCount = resolved.groundedRequestCount,
            unsupportedRequests = resolved.unsupportedRequests,
            contextWarnings = contextWarnings
        )
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
}
