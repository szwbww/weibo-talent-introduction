package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

enum class AiReplyMode {
    QA_MATCHED,
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
    val mode: AiReplyMode
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
        qaRuleIds: List<Long>? = null,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null
    ): AiReplyDraftResult {
        val resolved = resolveQaRules(inboundText, qaRuleIds)
        val mode = if (resolved.sendQaRuleIds.isNotEmpty()) AiReplyMode.QA_MATCHED else AiReplyMode.FREE_FORM
        val lastDraft = operatorTurns.lastOrNull()?.assistantDraft

        if (!properties.enabled) {
            return fallback(resolved, operatorTurns, lastDraft, mode)
        }

        val messages = when (mode) {
            AiReplyMode.QA_MATCHED -> buildMatchedMessages(
                inboundText = inboundText,
                operatorTurns = operatorTurns,
                promptRuleIds = resolved.promptRuleIds,
                operatorInstruction = operatorInstruction
            )
            AiReplyMode.FREE_FORM -> buildFreeFormMessages(
                inboundText = inboundText,
                operatorTurns = operatorTurns,
                operatorInstruction = operatorInstruction,
                expertProfile = expertProfile,
                mailHistory = mailHistory
            )
        }
        val temperature = when (mode) {
            AiReplyMode.QA_MATCHED -> properties.temperature
            AiReplyMode.FREE_FORM -> properties.freeFormTemperature
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
                mode = mode
            )
        } else {
            fallback(resolved, operatorTurns, lastDraft, mode)
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

    internal fun buildFreeFormMessages(
        inboundText: String,
        operatorTurns: List<AiReplyTurn>,
        operatorInstruction: String? = null,
        expertProfile: String? = null,
        mailHistory: String? = null
    ): List<LlmChatMessage> {
        val messages = mutableListOf<LlmChatMessage>()
        messages += LlmChatMessage(role = "system", content = buildFreeFormSystemPrompt())
        messages += LlmChatMessage(
            role = "user",
            content = buildFreeFormUserContent(inboundText, expertProfile, mailHistory)
        )
        appendFirstTurnInstruction(messages, operatorInstruction)
        appendOperatorTurns(messages, operatorTurns)
        return messages
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

    private fun buildBaseSystemPrompt(): String = buildString {
        appendLine("You are a recruiting assistant for academic expert outreach.")
        appendLine("Your goal is to encourage a reply and advance the conversation toward scheduling a meeting.")
        appendLine("Tone: warm, professional, concise.")
        appendLine("Reply in the same language as the inbound email.")
        appendLine("Keep the reply to at most 4 paragraphs.")
        appendLine("Output only the email body text. Do not include a subject line.")
    }

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

    private fun buildFreeFormSystemPrompt(): String = buildString {
        append(buildBaseSystemPrompt())
        appendLine()
        appendLine("No QA rules matched. Compose a helpful reply based on the expert profile and mail history.")
        appendLine("Do not make specific commitments beyond what the context supports.")
    }

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

    private fun buildFreeFormUserContent(
        inboundText: String,
        expertProfile: String?,
        mailHistory: String?
    ): String = buildString {
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
        mode: AiReplyMode
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
            qaRuleIds = resolved.sendQaRuleIds,
            mode = mode
        )
    }
}
