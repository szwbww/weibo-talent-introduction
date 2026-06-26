package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class QaMatchService(
    private val qaRuleRepository: QaRuleRepository,
    private val qaCategoryRepository: QaCategoryRepository
) {
    fun suggestComposition(messageBody: String): CompositionSuggestResult {
        val normalizedBody = normalize(messageBody)
        val rawMatches = qaRuleRepository.findAllEnabledOrdered()
            .mapNotNull { rule -> matchRule(rule, normalizedBody) }
        val matches = if (rawMatches.isEmpty()) emptyList() else applySupersede(rawMatches)
        val gapItems = extractGapItems(messageBody)
        val matchedCategoryIds = rawMatches.map { it.rule.categoryId }.distinct()
        val categories = qaCategoryRepository.findAll().filter { it.enabled }
        val rulesByCategory = categories.map { category ->
            val categoryId = requireNotNull(category.id)
            CategoryRulesGroup(
                categoryId = categoryId,
                categoryCode = category.categoryCode,
                categoryName = category.categoryName,
                composeOrder = category.composeOrder,
                rules = qaRuleRepository.findAllEnabledOrdered()
                    .filter { it.categoryId == categoryId }
                    .map { it.toSuggestRule() }
            )
        }.sortedBy { it.composeOrder }

        return CompositionSuggestResult(
            suggestedRuleIds = matches.mapNotNull { it.rule.id },
            suggestedRules = matches.map { it.rule.toSuggestRule() },
            rulesByCategory = rulesByCategory,
            gapItems = gapItems,
            gapDetected = detectGap(messageBody, rawMatches, matches),
            matchedCategoryIds = matchedCategoryIds
        )
    }

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

    private fun countQuestionUnits(messageBody: String): Int =
        extractGapItems(messageBody).size

    private fun extractGapItems(messageBody: String): List<String> {
        val questions = QUESTION_SENTENCE_PATTERN.findAll(messageBody)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val bullets = messageBody.lineSequence()
            .map { it.trim() }
            .filter { BULLET_LINE_PATTERN.containsMatchIn(it) }
            .toList()
        return if (questions.size >= bullets.size) questions else bullets
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

data class CompositionSuggestResult(
    val suggestedRuleIds: List<Long>,
    val suggestedRules: List<SuggestQaRule>,
    val rulesByCategory: List<CategoryRulesGroup>,
    val gapItems: List<String>,
    val gapDetected: Boolean,
    val matchedCategoryIds: List<Long>
)

data class CategoryRulesGroup(
    val categoryId: Long,
    val categoryCode: String,
    val categoryName: String,
    val composeOrder: Int,
    val rules: List<SuggestQaRule>
)

data class SuggestQaRule(
    val id: Long,
    val categoryId: Long,
    val displayName: String?,
    val sectionTitle: String?,
    val replySubject: String?,
    val replyBody: String,
    val keywords: String
)

private fun QaRule.toSuggestRule() = SuggestQaRule(
    id = requireNotNull(id),
    categoryId = categoryId,
    displayName = displayName,
    sectionTitle = sectionTitle,
    replySubject = replySubject,
    replyBody = replyBody,
    keywords = keywords
)
