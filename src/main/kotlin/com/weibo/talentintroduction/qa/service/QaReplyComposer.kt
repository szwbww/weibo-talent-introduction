package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule

object QaReplyComposer {
    const val GREETING = "Thank you for your email. Please find our answers below."
    const val CLOSING =
        "Please let us know if you have any further questions.\n\nBest regards,\nTalent Introduction Team"

    fun compose(
        matches: List<QaRuleMatch>,
        categoryComposeOrder: Map<Long, Int>
    ): ComposedReply {
        require(matches.isNotEmpty())

        if (matches.size == 1) {
            val rule = matches.first().rule
            return ComposedReply(
                replySubject = rule.replySubject,
                replyBody = rule.replyBody
            )
        }

        val primary = selectPrimary(matches)
        val ordered = matches
            .sortedWith(
                compareBy<QaRuleMatch> { categoryComposeOrder[it.rule.categoryId] ?: error("Missing compose_order for category ${it.rule.categoryId}") }
                    .thenBy { it.rule.priority }
                    .thenBy { it.rule.id ?: Long.MAX_VALUE }
            )

        val sections = ordered.joinToString("\n\n") { formatSection(it.rule) }
        val replyBody = listOf(GREETING, sections, CLOSING).joinToString("\n\n")

        return ComposedReply(
            replySubject = primary.rule.replySubject,
            replyBody = replyBody
        )
    }

    /** Preserves [matches] list order (operator-selected sequence). */
    fun composeInOperatorOrder(
        matches: List<QaRuleMatch>,
        salutation: String? = null,
        ack: String? = null,
        greeting: String? = null,
        closing: String? = null
    ): ComposedReply {
        require(matches.isNotEmpty())

        val primary = selectPrimary(matches)
        val sections = matches.joinToString("\n\n") { formatSection(it.rule) }
        val replyBody = listOf(salutation, ack, greeting, sections, closing)
            .mapNotNull { it?.trim()?.takeIf { text -> text.isNotEmpty() } }
            .joinToString("\n\n")

        return ComposedReply(
            replySubject = primary.rule.replySubject,
            replyBody = replyBody
        )
    }

    private fun formatSection(rule: QaRule): String = rule.replyBody

    fun selectPrimary(matches: List<QaRuleMatch>): QaRuleMatch =
        matches.minWith(
            compareBy<QaRuleMatch> { -it.matchedKeywordCount }
                .thenBy { it.rule.priority }
                .thenBy { it.rule.id ?: Long.MAX_VALUE }
        )
}

data class QaRuleMatch(
    val rule: QaRule,
    val matchedKeywordCount: Int
)

data class ComposedReply(
    val replySubject: String?,
    val replyBody: String
)
