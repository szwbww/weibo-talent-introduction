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
    /**
     * Compose from per-intent structured LLM output.
     * Uses intent catalog fixed titles; includes ALL request indexes (empty section for MISSING/UNSUPPORTED).
     */
    fun composeFromSections(
        requestFacts: List<RequestFactItem>,
        sections: List<ValidatedSection>
    ): String {
        val sectionByIndex = sections.associateBy { it.requestIndex }
        val headingByIndex = requestFacts.associate { item ->
            item.index to resolveSectionHeading(item)
        }
        val indexedBodies = linkedMapOf<Int, String>()
        val firstIndexByNormalizedAnswer = linkedMapOf<String, Int>()

        for (item in requestFacts) {
            val section = sectionByIndex[item.index]
            if (section != null && section.answers.isNotEmpty()) {
                val combined = section.answers.joinToString("\n\n") { it.answer }
                val normalized = normalizeForDedup(combined)
                val prior = firstIndexByNormalizedAnswer[normalized]
                val body = if (prior != null) {
                    crossReference(prior)
                } else {
                    firstIndexByNormalizedAnswer[normalized] = item.index
                    combined
                }
                indexedBodies[item.index] = body
            }
        }
        return assembleByAllRequests(headingByIndex, indexedBodies)
    }

    /**
     * Deterministic fallback with intent evidence. Keeps old composeFallback() API
     * but uses intent catalog titles for section headings.
     */
    fun composeFallback(requestFacts: List<RequestFactItem>): String {
        val headingByIndex = requestFacts.associate { item ->
            item.index to resolveSectionHeading(item)
        }
        val indexedBodies = linkedMapOf<Int, String>()
        val firstIndexByFactKey = linkedMapOf<String, Int>()

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
            val prior = firstIndexByFactKey[key]
            val body = if (prior != null) {
                crossReference(prior)
            } else {
                firstIndexByFactKey[key] = item.index
                facts
            }
            indexedBodies[item.index] = body
        }
        return assembleByAllRequests(headingByIndex, indexedBodies)
    }

    /**
     * Legacy: used by older callers that pass per-request answers. Kept for backward compat
     * but new code should use composeFromSections().
     */
    fun composeFromAnswers(
        requestFacts: List<RequestFactItem>,
        answersByIndex: Map<Int, String>
    ): String {
        val headingByIndex = requestFacts.associate { item ->
            item.index to resolveSectionHeading(item)
        }
        val indexedBodies = linkedMapOf<Int, String>()
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
            indexedBodies[item.index] = body
        }
        return assembleByAllRequests(headingByIndex, indexedBodies)
    }

    private fun resolveSectionHeading(item: RequestFactItem): String {
        if (item.intents.isNotEmpty()) {
            val intentKeys = item.intents.map { it.intentKey }
            return AiReplyIntentCatalog.resolveGroupTitle(intentKeys, item.requestText)
        }
        return cleanHeading(item.requestText)
    }

    /**
     * Assembles email with heading for EVERY request (by index), even if body is empty.
     * This ensures section numbering stays stable and gaps are visible.
     */
    private fun assembleByAllRequests(
        headingByIndex: Map<Int, String>,
        indexedBodies: Map<Int, String>
    ): String {
        val frame = replySnippetService.resolveManualFrame()
        val orderedIndexes = headingByIndex.keys.sorted()
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
            orderedIndexes.forEach { index ->
                appendLine("$index. ${headingByIndex[index].orEmpty()}")
                val body = indexedBodies[index]
                if (body != null) {
                    appendLine(body)
                }
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
