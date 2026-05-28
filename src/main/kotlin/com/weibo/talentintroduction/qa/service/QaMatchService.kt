package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class QaMatchService(
    private val qaRuleRepository: QaRuleRepository
) {
    fun match(messageBody: String): QaMatchResult? {
        val normalizedBody = normalize(messageBody)
        return qaRuleRepository.findAllEnabledOrdered()
            .mapNotNull { rule -> matchRule(rule, normalizedBody) }
            .maxWithOrNull(
                compareBy<QaRuleMatch> { it.matchedKeywordCount }
                    .thenBy { -it.rule.priority }
            )
            ?.toResult()
    }

    private fun matchRule(rule: QaRule, normalizedBody: String): QaRuleMatch? {
        val keywords = rule.keywords
            .split(",")
            .map { normalize(it) }
            .filter { it.isNotBlank() }

        if (keywords.isEmpty()) {
            return null
        }

        val matchedKeywords = keywords.filter(normalizedBody::contains)
        val matched = when (rule.matchMode.uppercase(Locale.ROOT)) {
            "ALL" -> matchedKeywords.size == keywords.size
            else -> matchedKeywords.isNotEmpty()
        }

        return if (matched) {
            QaRuleMatch(
                rule = rule,
                matchedKeywordCount = matchedKeywords.size
            )
        } else {
            null
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
}

data class QaMatchResult(
    val ruleId: Long?,
    val replySubject: String?,
    val replyBody: String,
    val handoffRequired: Boolean,
    val autoReplyEnabled: Boolean
)

private data class QaRuleMatch(
    val rule: QaRule,
    val matchedKeywordCount: Int
) {
    fun toResult(): QaMatchResult =
        QaMatchResult(
            ruleId = rule.id,
            replySubject = rule.replySubject,
            replyBody = rule.replyBody,
            handoffRequired = rule.handoffRequired,
            autoReplyEnabled = rule.autoReplyEnabled
        )
}
