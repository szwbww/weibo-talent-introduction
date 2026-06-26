package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service
import java.util.Locale
import kotlin.math.max

@Service
class QaMatchService(
    private val qaRuleRepository: QaRuleRepository,
    private val qaCategoryRepository: QaCategoryRepository
) {
    fun match(messageBody: String): QaMatchResult? {
        val normalizedBody = normalize(messageBody)
        val rawMatches = qaRuleRepository.findAllEnabledOrdered()
            .mapNotNull { rule -> matchRule(rule, normalizedBody) }

        if (rawMatches.isEmpty()) {
            return null
        }

        val matches = applySupersede(rawMatches)
        val categoryComposeOrder = qaCategoryRepository.findAll()
            .associate { requireNotNull(it.id) to it.composeOrder }

        val primary = QaReplyComposer.selectPrimary(matches)
        val composed = QaReplyComposer.compose(matches, categoryComposeOrder)
        val gapDetected = detectGap(messageBody, rawMatches, matches)

        return QaMatchResult(
            ruleId = primary.rule.id,
            replySubject = composed.replySubject,
            replyBody = composed.replyBody,
            handoffRequired = matches.any { it.rule.handoffRequired },
            autoReplyEnabled = matches.all { it.rule.autoReplyEnabled },
            matchedRuleIds = matches.mapNotNull { it.rule.id },
            gapDetected = gapDetected
        )
    }

    private fun applySupersede(matches: List<QaRuleMatch>): List<QaRuleMatch> {
        val superseding = matches.filter { it.rule.supersedesChildren }
        return if (superseding.isNotEmpty()) {
            superseding
        } else {
            matches
        }
    }

    private fun detectGap(
        messageBody: String,
        rawMatches: List<QaRuleMatch>,
        composedMatches: List<QaRuleMatch>
    ): Boolean {
        if (composedMatches.any { it.rule.supersedesChildren }) {
            return false
        }
        val questionUnits = countQuestionUnits(messageBody)
        val matchedCategoryCount = rawMatches.map { it.rule.categoryId }.distinct().size
        return questionUnits > matchedCategoryCount
    }

    private fun countQuestionUnits(messageBody: String): Int {
        val questionSentences = QUESTION_SENTENCE_PATTERN.findAll(messageBody).count()
        val bulletLines = messageBody.lineSequence()
            .count { line -> BULLET_LINE_PATTERN.containsMatchIn(line.trim()) }
        return max(questionSentences, bulletLines)
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

    companion object {
        private val QUESTION_SENTENCE_PATTERN = Regex("[^?.!\n]*\\?")
        private val BULLET_LINE_PATTERN = Regex("^(?:[-*•]|\\d+[.)]\\s)")
    }
}

data class QaMatchResult(
    val ruleId: Long?,
    val replySubject: String?,
    val replyBody: String,
    val handoffRequired: Boolean,
    val autoReplyEnabled: Boolean,
    val matchedRuleIds: List<Long> = emptyList(),
    val gapDetected: Boolean = false
)
