package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

data class AiReplyTurn(
    val assistantDraft: String,
    val operatorInstruction: String
)

data class AiReplyDraftResult(
    val draftText: String,
    val usedLlm: Boolean,
    val qaRuleIds: List<Long>
)

internal data class ResolvedQaRules(
    val sendQaRuleIds: List<Long>,
    val promptRuleIds: List<Long>
)

@Service
class AiReplyDraftService(
    private val properties: LlmProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val qaMatchService: QaMatchService,
    private val qaRuleRepository: QaRuleRepository,
    private val llmStitchService: LlmStitchService,
    private val replySnippetService: ReplySnippetService
) {
    fun generate(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        qaRuleIds: List<Long>? = null
    ): AiReplyDraftResult {
        val resolved = resolveQaRules(inboundText, qaRuleIds)
        val lastDraft = operatorTurns.lastOrNull()?.assistantDraft

        if (!properties.enabled) {
            return fallback(resolved, operatorTurns, lastDraft)
        }

        val messages = buildMessages(inboundText, operatorTurns, resolved.promptRuleIds)
        val llmText = try {
            llmDraftClientProvider.getIfAvailable()
                ?.chat(messages)
                ?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }

        return if (llmText != null) {
            AiReplyDraftResult(
                draftText = llmText,
                usedLlm = true,
                qaRuleIds = resolved.sendQaRuleIds
            )
        } else {
            fallback(resolved, operatorTurns, lastDraft)
        }
    }

    internal fun resolveQaRules(inboundText: String, qaRuleIds: List<Long>?): ResolvedQaRules {
        if (qaRuleIds != null) {
            return ResolvedQaRules(sendQaRuleIds = qaRuleIds, promptRuleIds = qaRuleIds)
        }
        val matched = qaMatchService.suggestComposition(inboundText).suggestedRuleIds
        val promptRuleIds = if (matched.isNotEmpty()) {
            matched
        } else {
            qaRuleRepository.findAllEnabledOrdered().mapNotNull { it.id }
        }
        return ResolvedQaRules(sendQaRuleIds = matched, promptRuleIds = promptRuleIds)
    }

    internal fun buildMessages(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        promptRuleIds: List<Long>
    ): List<LlmChatMessage> {
        val messages = mutableListOf<LlmChatMessage>()
        messages += LlmChatMessage(role = "system", content = buildSystemPrompt(promptRuleIds))
        messages += LlmChatMessage(role = "user", content = inboundText.take(4000))
        operatorTurns.forEach { turn ->
            messages += LlmChatMessage(role = "assistant", content = turn.assistantDraft)
            messages += LlmChatMessage(role = "user", content = turn.operatorInstruction)
        }
        return messages
    }

    private fun buildSystemPrompt(promptRuleIds: List<Long>): String = buildString {
        appendLine("You are a recruiting assistant. Write expert reply emails in English using the QA knowledge below.")
        appendLine("Output only the email body text. Do not include a subject line.")
        appendLine("Do not make promises beyond what the QA rules support.")
        appendLine()
        appendLine("QA knowledge:")
        appendLine(buildRuleSegments(promptRuleIds).take(8000))
        buildFrameGuidanceText()?.let { guidance ->
            appendLine()
            appendLine("Style guidance (salutation, greeting, acknowledgment, closing):")
            appendLine(guidance)
        }
    }

    private fun buildFrameGuidanceText(): String? {
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

    private fun buildRuleSegments(qaRuleIds: List<Long>): String =
        qaRuleIds.mapNotNull { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.let { rule ->
                val title = rule.sectionTitle?.trim().orEmpty()
                if (title.isEmpty()) rule.replyBody else "$title\n${rule.replyBody}"
            }
        }.joinToString("\n\n")

    private fun fallback(
        resolved: ResolvedQaRules,
        operatorTurns: List<AiReplyTurn>,
        lastDraft: String?
    ): AiReplyDraftResult {
        val draftText = if (operatorTurns.isEmpty()) {
            if (resolved.promptRuleIds.isEmpty()) {
                ""
            } else {
                llmStitchService.composeDeterministicDraft(resolved.promptRuleIds)
            }
        } else {
            lastDraft.orEmpty()
        }
        return AiReplyDraftResult(
            draftText = draftText,
            usedLlm = false,
            qaRuleIds = resolved.sendQaRuleIds
        )
    }
}
