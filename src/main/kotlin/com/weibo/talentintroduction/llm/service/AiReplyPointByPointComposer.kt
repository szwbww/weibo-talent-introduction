package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.springframework.stereotype.Service

/**
 * AI-only structured fallback for multi-request grounded drafts.
 * Does not replace [LlmStitchService.composeDeterministicDraft] (manual polish).
 */
@Service
class AiReplyPointByPointComposer(
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService
) {
    fun compose(requestFacts: List<RequestFactItem>, expertProfile: String? = null): String {
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
            requestFacts.forEach { item ->
                appendLine("${item.index}. ${cleanHeading(item.requestText)}")
                when (item.status) {
                    RequestGroundingStatus.UNSUPPORTED -> {
                        appendLine(UNSUPPORTED_TEXT)
                    }
                    RequestGroundingStatus.GROUNDED,
                    RequestGroundingStatus.PARTIAL -> {
                        val facts = joinFacts(item.factRuleIds)
                        if (facts.isNotBlank()) {
                            appendLine(facts)
                        } else {
                            val profileExcerpt = expertProfile
                                ?.takeIf { it.isNotBlank() }
                                ?.trim()
                                ?.take(PROFILE_EXCERPT_MAX_CHARS)
                            if (!profileExcerpt.isNullOrBlank()) {
                                appendLine(profileExcerpt)
                            } else {
                                appendLine(UNSUPPORTED_TEXT)
                            }
                        }
                        if (item.status == RequestGroundingStatus.PARTIAL) {
                            appendLine(PARTIAL_CONFIRMATION)
                        }
                    }
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

    companion object {
        const val UNSUPPORTED_TEXT =
            "This point is not covered by the approved information currently available, " +
                "so it requires confirmation before we provide a definitive answer."

        const val PARTIAL_CONFIRMATION =
            "This still needs confirmation on remaining details."

        private val LEADING_BULLET_REGEX = Regex("""^\s*(?:[-*•]+|\d+[.)])\s*""")
        private val TRAILING_QUESTION_REGEX = Regex("""[?？]+\s*$""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun cleanHeading(requestText: String): String {
            val cleaned = requestText
                .replace(LEADING_BULLET_REGEX, "")
                .replace(TRAILING_QUESTION_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            return cleaned.take(HEADING_MAX_CHARS)
        }

        private const val HEADING_MAX_CHARS = 160
        private const val PROFILE_EXCERPT_MAX_CHARS = 500
    }
}
