package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.qa.service.QaRuleMatch
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class LlmStitchService(
    private val properties: LlmProperties,
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService
) {
    fun polishDraft(
        qaRuleIds: List<Long>,
        inboundQuestion: String,
        freeText: String?,
        ackSnippetId: Long? = null
    ): PolishDraftResult {
        val deterministic = composeDeterministic(qaRuleIds, freeText, ackSnippetId)
        if (!properties.enabled) {
            return PolishDraftResult(draftText = deterministic, usedLlm = false)
        }
        val ruleSegments = buildRuleSegments(qaRuleIds)
        val llmText = try {
            llmDraftClientProvider.getIfAvailable()
                ?.stitchDraft(inboundQuestion, ruleSegments, freeText.orEmpty())
                ?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            null
        }
        return if (llmText != null) {
            PolishDraftResult(draftText = llmText, usedLlm = true)
        } else {
            PolishDraftResult(draftText = deterministic, usedLlm = false)
        }
    }

    fun isEnabled(): Boolean = properties.enabled

    fun composeDeterministicDraft(
        qaRuleIds: List<Long>,
        freeText: String? = null,
        ackSnippetId: Long? = null
    ): String = composeDeterministic(qaRuleIds, freeText, ackSnippetId)

    private fun composeDeterministic(qaRuleIds: List<Long>, freeText: String?, ackSnippetId: Long?): String {
        if (qaRuleIds.isEmpty()) {
            return freeText.orEmpty()
        }
        val rules = qaRuleIds.map { ruleId ->
            qaRuleRepository.findById(ruleId).orElseThrow { error("QA rule not found: $ruleId") }
        }
        val matches = rules.map { QaRuleMatch(rule = it, matchedKeywordCount = 1) }
        val frame = replySnippetService.resolveManualFrame()
        val ack = replySnippetService.resolveAck(ackSnippetId)
        val composed = QaReplyComposer.composeInOperatorOrder(
            matches = matches,
            salutation = frame.salutation,
            ack = ack,
            greeting = frame.greeting,
            closing = frame.closing
        )
        val free = freeText?.trim().orEmpty()
        return if (free.isBlank()) {
            composed.replyBody
        } else {
            listOf(composed.replyBody, free).joinToString("\n\n")
        }
    }

    private fun buildRuleSegments(qaRuleIds: List<Long>): String =
        qaRuleIds.mapNotNull { ruleId ->
            qaRuleRepository.findById(ruleId).orElse(null)?.replyBody
        }.joinToString("\n\n")
}

data class PolishDraftResult(
    val draftText: String,
    val usedLlm: Boolean
)

data class PolishDraftRequest(
    val qaRuleIds: List<Long>,
    val freeText: String?,
    val ackSnippetId: Long? = null
)

data class PolishDraftResponse(
    val draftText: String,
    val usedLlm: Boolean,
    val llmEnabled: Boolean
)
