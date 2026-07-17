package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.qa.domain.QaReplyPolicy
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaCategoryRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class QaMatchService(
    private val qaRuleRepository: QaRuleRepository,
    private val qaCategoryRepository: QaCategoryRepository,
    private val contentVariantService: ContentVariantService
) {
    fun suggestComposition(messageBody: String): CompositionSuggestResult {
        val normalizedBody = normalize(messageBody)
        val enabledRules = matchableRules()
        val rawMatches = enabledRules
            .mapNotNull { rule -> matchRule(rule, normalizedBody) }

        val requestItems = QaRequestExtractor.extract(messageBody).map { it.text }
        val gapItems = requestItems.map { item ->
            val normalizedItem = normalize(item)
            val candidateRuleIds = enabledRules
                .filter { rule -> matchRuleAgainstText(rule, normalizedItem) }
                .mapNotNull { it.id }
            GapItem(text = item, candidateRuleIds = candidateRuleIds)
        }

        // Multi-request: return all raw matches so the operator sees every candidate.
        // Single/zero request: apply supersede (same as auto-reply path).
        val matches = if (requestItems.size <= 1) applySupersede(rawMatches) else rawMatches

        val matchedCategoryIds = rawMatches.map { it.rule.categoryId }.distinct()
        val categories = qaCategoryRepository.findAll().filter { it.enabled }
        val rulesByCategory = categories.map { category ->
            val categoryId = requireNotNull(category.id)
            CategoryRulesGroup(
                categoryId = categoryId,
                categoryCode = category.categoryCode,
                categoryName = category.categoryName,
                composeOrder = category.composeOrder,
                rules = matchableRules()
                    .filter { it.categoryId == categoryId }
                    .map { it.toSuggestRule() }
            )
        }.sortedBy { it.composeOrder }

        return CompositionSuggestResult(
            suggestedRuleIds = matches.mapNotNull { it.rule.id },
            suggestedRules = matches.map { it.rule.toSuggestRule() },
            rulesByCategory = rulesByCategory,
            gapItems = gapItems,
            gapDetected = gapItems.any { it.candidateRuleIds.isEmpty() },
            matchedCategoryIds = matchedCategoryIds
        )
    }

    fun matchAllRuleIds(messageBody: String): List<Long> {
        val normalizedBody = normalize(messageBody)
        return matchableRules()
            .mapNotNull { rule -> matchRule(rule, normalizedBody)?.let { rule.id } }
            .distinct()
    }

    fun match(messageBody: String, variantSeed: Int = 0): QaMatchResult? {
        val normalizedBody = normalize(messageBody)
        val rawMatches = matchableRules()
            .mapNotNull { rule -> matchRule(rule, normalizedBody) }

        if (rawMatches.isEmpty()) {
            return null
        }

        val matches = applySupersede(rawMatches).map { resolveMatchVariant(it, variantSeed) }
        val categoryComposeOrder = qaCategoryRepository.findAll()
            .associate { requireNotNull(it.id) to it.composeOrder }

        val primary = QaReplyComposer.selectPrimary(matches)
        val composed = QaReplyComposer.compose(matches, categoryComposeOrder)
        val gapDetected = detectGap(messageBody, rawMatches, matches)

        val aggregatedPolicy = QaReplyPolicy.aggregate(matches.map { it.rule.replyPolicyEnum() })

        return QaMatchResult(
            ruleId = primary.rule.id,
            replySubject = composed.replySubject,
            replyBody = composed.replyBody,
            replyPolicy = aggregatedPolicy.name,
            handoffRequired = aggregatedPolicy.legacyHandoffRequired(),
            autoReplyEnabled = aggregatedPolicy.legacyAutoReplyEnabled(),
            matchedRuleIds = matches.mapNotNull { it.rule.id },
            gapDetected = gapDetected
        )
    }

    private fun matchableRules(): List<QaRule> =
        qaRuleRepository.findAllEnabledOrdered().filter { it.isMatchable() }

    private fun resolveMatchVariant(match: QaRuleMatch, variantSeed: Int): QaRuleMatch {
        val ruleId = match.rule.id ?: return match
        val resolvedBody = contentVariantService.resolveBody(
            ownerType = ContentVariantOwnerType.QA_RULE,
            ownerId = ruleId,
            mainBody = match.rule.replyBody,
            seed = variantSeed,
            useVariants = true
        )
        return match.copy(rule = match.rule.copy(replyBody = resolvedBody))
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
        QaRequestExtractor.extract(messageBody).size

    private fun matchRule(rule: QaRule, normalizedBody: String): QaRuleMatch? {
        val keywords = parseKeywords(rule)
        if (keywords.isEmpty()) {
            return null
        }
        val matchedKeywords = keywords.filter(normalizedBody::contains)
        return if (ruleMatchesKeywords(keywords, matchedKeywords, rule.matchMode)) {
            QaRuleMatch(
                rule = rule,
                matchedKeywordCount = matchedKeywords.size
            )
        } else {
            null
        }
    }

    private fun matchRuleAgainstText(rule: QaRule, normalizedGapText: String): Boolean =
        ruleMatches(rule, normalizedGapText)

    private fun ruleMatches(rule: QaRule, normalizedText: String): Boolean {
        val keywords = parseKeywords(rule)
        if (keywords.isEmpty()) {
            return false
        }
        val matchedKeywords = keywords.filter(normalizedText::contains)
        return ruleMatchesKeywords(keywords, matchedKeywords, rule.matchMode)
    }

    private fun parseKeywords(rule: QaRule): List<String> =
        rule.keywords
            .split(",")
            .map { normalize(it) }
            .filter { it.isNotBlank() }

    private fun ruleMatchesKeywords(
        keywords: List<String>,
        matchedKeywords: List<String>,
        matchMode: String
    ): Boolean = when (matchMode.uppercase(Locale.ROOT)) {
        "ALL" -> matchedKeywords.size == keywords.size
        else -> matchedKeywords.isNotEmpty()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .replace("details", "information")
            .replace("detail", "information")
            .trim()

}

data class QaMatchResult(
    val ruleId: Long?,
    val replySubject: String?,
    val replyBody: String,
    val replyPolicy: String = QaReplyPolicy.AUTO.name,
    val handoffRequired: Boolean,
    val autoReplyEnabled: Boolean,
    val matchedRuleIds: List<Long> = emptyList(),
    val gapDetected: Boolean = false
)

data class GapItem(
    val text: String,
    val candidateRuleIds: List<Long>
)

data class CompositionSuggestResult(
    val suggestedRuleIds: List<Long>,
    val suggestedRules: List<SuggestQaRule>,
    val rulesByCategory: List<CategoryRulesGroup>,
    val gapItems: List<GapItem>,
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
    val keywords: String,
    val replyPolicy: String
)

private fun QaRule.toSuggestRule() = SuggestQaRule(
    id = requireNotNull(id),
    categoryId = categoryId,
    displayName = displayName,
    sectionTitle = sectionTitle,
    replySubject = replySubject,
    replyBody = replyBody,
    keywords = keywords,
    replyPolicy = replyPolicyEnum().name
)
