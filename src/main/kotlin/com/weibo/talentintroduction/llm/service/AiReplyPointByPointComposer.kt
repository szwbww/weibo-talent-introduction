package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.stereotype.Service

/**
 * Backend-owned assembler for QA_GROUNDED drafts (LLM answers + deterministic fallback).
 * Frame and natural paragraph flow are never delegated to the model.
 */
@Service
class AiReplyPointByPointComposer(
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService
) {
    fun composeFromSections(
        requestFacts: List<RequestFactItem>,
        sections: List<ValidatedSection>
    ): String {
        val sectionByIndex = sections.associateBy { it.requestIndex }
        val bodies = linkedSetOf<String>()

        for (item in requestFacts) {
            val section = sectionByIndex[item.index] ?: continue
            if (section.answers.isEmpty()) {
                continue
            }
            section.answers.forEach { answer ->
                val text = answer.answer.trim()
                if (text.isNotBlank()) {
                    bodies += text
                }
            }
        }
        return assembleNaturalEmail(bodies.toList())
    }

    fun composeFallback(requestFacts: List<RequestFactItem>): String {
        val bodies = linkedSetOf<String>()
        val seenFactKeys = linkedSetOf<String>()

        for (item in requestFacts) {
            if (item.status == RequestGroundingStatus.UNSUPPORTED) {
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
            if (seenFactKeys.add(key)) {
                bodies += facts
            }
        }
        return assembleNaturalEmail(bodies.toList())
    }

    fun composeFromAnswers(
        requestFacts: List<RequestFactItem>,
        answersByIndex: Map<Int, String>
    ): String {
        val bodies = linkedSetOf<String>()
        for (item in requestFacts) {
            if (item.status != RequestGroundingStatus.GROUNDED &&
                item.status != RequestGroundingStatus.PARTIAL
            ) {
                continue
            }
            val answer = answersByIndex[item.index]?.trim().orEmpty()
            if (answer.isNotBlank()) {
                bodies += answer
            }
        }
        return assembleNaturalEmail(bodies.toList())
    }

    private fun assembleNaturalEmail(paragraphs: List<String>): String {
        val frame = replySnippetService.resolveManualFrame()
        val limited = paragraphs.filter { it.isNotBlank() }.take(4)
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
            limited.forEach { paragraph ->
                appendLine(paragraph.trim())
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
                    ?.answerBody
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            .joinToString("\n\n")
    }

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
