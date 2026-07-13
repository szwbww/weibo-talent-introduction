package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.stereotype.Service

/**
 * Backend-owned assembler for QA_GROUNDED drafts (LLM answers + deterministic fallback).
 * Frame, headings, numbering, and cross-references are never delegated to the model.
 */
@Service
class AiReplyPointByPointComposer(
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService
) {
    fun composeFromAnswers(
        requestFacts: List<RequestFactItem>,
        answersByIndex: Map<Int, String>
    ): String {
        val headingByIndex = requestFacts.associate { it.index to cleanHeading(it.requestText) }
        val sections = mutableListOf<Pair<Int, String>>()
        val firstIndexByNormalizedAnswer = linkedMapOf<String, Int>()

        for (item in requestFacts) {
            if (item.status != RequestGroundingStatus.GROUNDED &&
                item.status != RequestGroundingStatus.PARTIAL
            ) {
                continue
            }
            val answer = answersByIndex[item.index]?.trim().orEmpty()
            if (answer.isBlank()) {
                continue
            }
            val normalized = normalizeForDedup(answer)
            val prior = firstIndexByNormalizedAnswer[normalized]
            val body = if (prior != null) {
                crossReference(prior)
            } else {
                firstIndexByNormalizedAnswer[normalized] = item.index
                answer
            }
            sections += item.index to body
        }
        return assemble(headingByIndex, sections)
    }

    fun composeFallback(requestFacts: List<RequestFactItem>): String {
        val headingByIndex = requestFacts.associate { it.index to cleanHeading(it.requestText) }
        val sections = mutableListOf<Pair<Int, String>>()
        val firstIndexByFactKey = linkedMapOf<String, Int>()

        for (item in requestFacts) {
            if (item.status == RequestGroundingStatus.UNSUPPORTED) {
                continue
            }
            // Research synthesis needs the model; never dump profile as a match answer.
            if (item.requiresResearchContext) {
                continue
            }
            if (item.status != RequestGroundingStatus.GROUNDED &&
                item.status != RequestGroundingStatus.PARTIAL
            ) {
                continue
            }
            val facts = joinFacts(item.factRuleIds)
            if (facts.isBlank()) {
                continue
            }
            val key = normalizeForDedup(facts)
            val prior = firstIndexByFactKey[key]
            val body = if (prior != null) {
                crossReference(prior)
            } else {
                firstIndexByFactKey[key] = item.index
                facts
            }
            sections += item.index to body
        }
        return assemble(headingByIndex, sections)
    }

    private fun assemble(
        headingByIndex: Map<Int, String>,
        sections: List<Pair<Int, String>>
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
            sections.forEach { (index, body) ->
                appendLine("$index. ${headingByIndex[index].orEmpty()}")
                appendLine(body)
                appendLine()
            }
            frame.closing?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
        }.trim()
    }

    private fun joinFacts(factRuleIds: List<Long>): String {
        val seen = linkedSetOf<Long>()
        return factRuleIds
            .asSequence()
            .filter { seen.add(it) }
            .mapNotNull { ruleId ->
                qaRuleRepository.findById(ruleId).orElse(null)
                    ?.replyBody
                    ?.takeIf { it.isNotBlank() }
            }
            .joinToString("\n\n")
    }

    private fun crossReference(firstIndex: Int): String =
        "Please see point $firstIndex above."

    private fun normalizeForDedup(text: String): String =
        text.lowercase().replace(WHITESPACE_REGEX, " ").trim()

    companion object {
        private val LEADING_BULLET_REGEX = Regex("""^\s*(?:[-*•]+|\d+[.)])\s*""")
        private val TRAILING_PUNCT_REGEX = Regex("""[?？;；]+\s*$""")
        private val TRAILING_AND_REGEX = Regex("""(?i)\band\s*$""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun cleanHeading(requestText: String): String {
            var cleaned = requestText
                .replace(LEADING_BULLET_REGEX, "")
                .replace(TRAILING_PUNCT_REGEX, "")
                .replace(TRAILING_AND_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            // Trailing "and" may leave punctuation; strip once more.
            cleaned = cleaned.replace(TRAILING_PUNCT_REGEX, "").trim()
            cleaned = capitalizeFirstLetter(cleaned)
            return cleaned.take(HEADING_MAX_CHARS)
        }

        private fun capitalizeFirstLetter(text: String): String {
            val idx = text.indexOfFirst { it in 'A'..'Z' || it in 'a'..'z' }
            if (idx < 0) {
                return text
            }
            return text.substring(0, idx) + text[idx].uppercaseChar() + text.substring(idx + 1)
        }

        private const val HEADING_MAX_CHARS = 160
    }
}
