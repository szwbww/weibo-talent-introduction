package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service

data class ClaimValidationResult(
    val valid: Boolean,
    val warningCodes: List<String> = emptyList()
)

@Service
class AiReplyHighRiskClaimValidator(
    private val qaRuleRepository: QaRuleRepository
) {
    fun validate(
        sections: List<ValidatedSection>,
        requestFacts: List<RequestFactItem>
    ): ClaimValidationResult {
        val warnings = mutableListOf<String>()

        for (section in sections) {
            for (answer in section.answers) {
                if (answer.answer.isBlank()) {
                    continue
                }
                val sourceText = resolveSourceText(answer.sourceRuleIds)
                if (sourceText == null) {
                    warnings += WARNING_CLAIM_SOURCE_UNAVAILABLE
                    continue
                }

                if (containsHallucinatedNumberOrUrl(answer.answer, sourceText)) {
                    warnings += WARNING_CLAIM_HALLUCINATED_FACT
                }
                if (detectsModalityStrengthening(answer.answer, sourceText)) {
                    warnings += WARNING_CLAIM_MODALITY_STRENGTHENED
                }
                if (containsUnbackedHighRiskDeclarations(answer.answer, sourceText)) {
                    warnings += WARNING_CLAIM_HIGH_RISK_UNBACKED
                }
            }
        }
        val valid = warnings.isEmpty()
        return ClaimValidationResult(valid = valid, warningCodes = warnings.distinct())
    }

    /**
     * Returns the combined [replySubject] + [replyBody] of all referenced rules,
     * or null if any referenced rule is missing or has empty text.
     */
    internal fun resolveSourceText(sourceRuleIds: List<Long>): String? {
        val texts = mutableListOf<String>()
        val seen = linkedSetOf<Long>()
        for (ruleId in sourceRuleIds) {
            if (!seen.add(ruleId)) {
                continue
            }
            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: return null
            val subject = rule.replySubject?.trim().orEmpty()
            val body = rule.replyBody.trim()
            if (subject.isBlank() && body.isBlank()) {
                return null
            }
            if (subject.isNotBlank()) {
                texts += subject + "\n" + body
            } else {
                texts += body
            }
        }
        return texts.joinToString("\n")
    }

    internal fun containsHallucinatedNumberOrUrl(answer: String, combinedFacts: String): Boolean {
        val numbers = NUMBER_TOKEN_REGEX.findAll(answer).map { it.value.trim() }.toList()
        val urls = EXTRACT_URL.findAll(answer).map { it.value.trim() }.toList()
        val compoundTokens = extractCompoundTokens(answer)

        val factsCommaStripped = combinedFacts.lowercase().replace(",", "")
        val factsLower = combinedFacts.lowercase()

        for (num in numbers) {
            val numCleaned = num.replace(Regex("[\\s,]+"), "")
            if (!wordBoundaryContains(factsCommaStripped, numCleaned)) {
                return true
            }
        }

        for (url in urls) {
            val urlCleaned = url.lowercase().trimEnd('/')
            if (!factsLower.contains(urlCleaned) &&
                !factsLower.contains(url.lowercase())
            ) {
                return true
            }
        }

        for (token in compoundTokens) {
            val cleaned = token.lowercase().replace(",", "")
            if (!wordBoundaryContains(factsCommaStripped, cleaned) &&
                !factsLower.contains(token.lowercase())
            ) {
                return true
            }
        }
        return false
    }

    private fun extractCompoundTokens(answer: String): List<String> {
        val tokens = mutableListOf<String>()
        for (match in AMOUNT_REGEX.findAll(answer)) {
            tokens += match.value.trim()
        }
        for (match in TIME_UNIT_REGEX.findAll(answer)) {
            tokens += match.value.trim()
        }
        return tokens
    }

    internal fun detectsModalityStrengthening(answer: String, combinedFacts: String): Boolean {
        val sourceConditional = CONDITIONAL_PHRASES.any { phrase ->
            combinedFacts.contains(phrase, ignoreCase = true)
        }
        if (!sourceConditional) {
            return false
        }
        val answerStrengthened = STRENGTHENING_PHRASES.any { phrase ->
            wordBoundaryContains(answer, phrase)
        }
        return answerStrengthened
    }

    internal fun containsUnbackedHighRiskDeclarations(answer: String, combinedFacts: String): Boolean {
        val lowerAnswer = answer.lowercase()
        val lowerFacts = combinedFacts.lowercase()

        for (entry in HIGH_RISK_PHRASE_FAMILIES) {
            val answerHas = wordBoundaryContains(lowerAnswer, entry.key)
            if (!answerHas) {
                continue
            }
            val factsHave = entry.value.any { phrase ->
                lowerFacts.contains(phrase)
            }
            if (!factsHave) {
                return true
            }
        }
        return false
    }

    internal fun wordBoundaryContains(text: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("\\b" + escaped + "\\b").containsMatchIn(text)
    }

    companion object {
        const val WARNING_CLAIM_HALLUCINATED_FACT = "AI_REPLY_CLAIM_HALLUCINATED_FACT"
        const val WARNING_CLAIM_MODALITY_STRENGTHENED = "AI_REPLY_CLAIM_MODALITY_STRENGTHENED"
        const val WARNING_CLAIM_HIGH_RISK_UNBACKED = "AI_REPLY_CLAIM_HIGH_RISK_UNBACKED"
        const val WARNING_CLAIM_SOURCE_UNAVAILABLE = "AI_REPLY_CLAIM_SOURCE_UNAVAILABLE"

        private val NUMBER_TOKEN_REGEX = Regex("\\b\\d[\\d,.]*\\b", RegexOption.IGNORE_CASE)

        private val EXTRACT_URL = Regex("https?://\\S+")

        private val AMOUNT_REGEX = Regex(
            "\\b\\d[\\d,.]*\\s*(RMB|CNY|USD|EUR|GBP|yuan|dollars?|euros?)\\b|" +
                "\\b(RMB|CNY|USD|EUR|GBP)\\s*\\d[\\d,.]*\\b",
            RegexOption.IGNORE_CASE
        )

        private val TIME_UNIT_REGEX = Regex(
            "\\b\\d[\\d,.]*\\s*(per\\s+)?(years?|months?|weeks?|days?|annually|monthly|weekly|yearly|annum)\\b|" +
                "\\b(per\\s+)?(years?|months?|weeks?|days?)\\s+\\d[\\d,.]*\\b",
            RegexOption.IGNORE_CASE
        )

        private val CONDITIONAL_PHRASES = listOf(
            "may ", "might ", "can ", "could ", "depends on", "after selection",
            "typically", "generally", "usually", "in some cases", "subject to"
        )

        private val STRENGTHENING_PHRASES = listOf(
            "guaranteed", "will definitely", "unconditionally", "entitled to",
            "absolutely", "certainly will"
        )

        private val HIGH_RISK_PHRASE_FAMILIES: Map<String, List<String>> = linkedMapOf(
            "government" to listOf("government"),
            "travel expenses" to listOf("travel expenses", "travel costs covered", "all travel"),
            "no fees" to listOf("no fees", "free of charge", "at no cost", "no cost"),
            "labor contract" to listOf("labor contract", "employment contract"),
            "intellectual property" to listOf("intellectual property", "ip ownership", "ip rights"),
            "confidentiality" to listOf("confidentiality", "confidential information", "nda"),
            "all expenses" to listOf("all expenses", "expenses covered")
        )
    }
}
