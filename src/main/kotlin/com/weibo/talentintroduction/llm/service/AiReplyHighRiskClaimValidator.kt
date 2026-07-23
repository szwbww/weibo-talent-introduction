package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import org.springframework.stereotype.Service

data class ClaimValidationResult(
    val valid: Boolean,
    val warningCodes: List<String> = emptyList()
)

data class GroundedCandidateInput(
    val validatedSections: List<ValidatedSection>,
    val requestFacts: List<RequestFactItem>,
    val plan: GroundedContentPlan,
    val finalBody: String,
    val hasBlockingTrustGap: Boolean
)

@Service
class AiReplyHighRiskClaimValidator(
    private val qaRuleRepository: QaRuleRepository
) {
    fun validatePlainText(finalRawText: String, factRuleIds: List<Long>): ClaimValidationResult {
        if (factRuleIds.isEmpty()) {
            return ClaimValidationResult(valid = true)
        }
        val sourceText = resolveSourceText(factRuleIds)
        if (sourceText == null) {
            return ClaimValidationResult(
                valid = false,
                warningCodes = listOf(WARNING_CLAIM_SOURCE_UNAVAILABLE)
            )
        }
        val warnings = mutableListOf<String>()
        if (containsHallucinatedNumberOrUrl(finalRawText, sourceText)) {
            warnings += WARNING_CLAIM_HALLUCINATED_FACT
        }
        if (detectsModalityStrengthening(finalRawText, sourceText)) {
            warnings += WARNING_CLAIM_MODALITY_STRENGTHENED
        }
        if (containsUnbackedHighRiskDeclarations(finalRawText, sourceText)) {
            warnings += WARNING_CLAIM_HIGH_RISK_UNBACKED
        }
        return ClaimValidationResult(valid = warnings.isEmpty(), warningCodes = warnings.distinct())
    }

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

    fun validateGroundedCandidate(input: GroundedCandidateInput): ClaimValidationResult {
        val warnings = mutableListOf<String>()
        warnings += validate(input.validatedSections, input.requestFacts).warningCodes

        val body = input.finalBody
        if (body.isNotBlank()) {
            if (containsTrustRhetoric(body)) {
                warnings += WARNING_CLAIM_TRUST_RHETORIC
            }
            if (input.hasBlockingTrustGap && containsConfidentialitySubstitute(body)) {
                warnings += WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE
            }
            for (section in input.validatedSections) {
                for (answer in section.answers) {
                    if (answer.answer.isBlank()) {
                        continue
                    }
                    val sourceText = resolveSourceText(answer.sourceRuleIds)
                    if (sourceText == null) {
                        continue
                    }
                    val claimPlan = input.plan.claims.find {
                        val key = "r${section.requestIndex}:${answer.intentKey}"
                        it.claimKey == key
                    }
                    if (claimPlan != null) {
                        val isAgencyOrCompany = claimPlan.intentKey.startsWith("agency.") ||
                            claimPlan.intentKey.startsWith("company.")
                        if (isAgencyOrCompany) {
                            if (isRoleDisclosureRequired(sourceText) && !containsRoleDisclosure(answer.answer)) {
                                warnings += WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED
                            }
                        }
                        if (claimPlan.intentKey.startsWith("enterprise.")) {
                            if (isEnterpriseUncertaintyRequired(sourceText) && containsEnterpriseCertainty(answer.answer)) {
                                warnings += WARNING_CLAIM_ENTERPRISE_UNGROUNDED
                            }
                            if (isEnterpriseUncertaintyRequired(sourceText) && !containsEnterpriseUncertainty(answer.answer)) {
                                warnings += WARNING_CLAIM_ENTERPRISE_UNGROUNDED
                            }
                        }
                    }
                }
            }
        }

        return ClaimValidationResult(valid = warnings.isEmpty(), warningCodes = warnings.distinct())
    }

    internal fun containsTrustRhetoric(text: String): Boolean {
        return TRUST_RHETORIC_PHRASES.any { phrase ->
            text.contains(phrase, ignoreCase = true)
        }
    }

    internal fun containsConfidentialitySubstitute(text: String): Boolean {
        return CONFIDENTIALITY_SUBSTITUTE_PHRASES.any { phrase ->
            text.contains(phrase, ignoreCase = true)
        }
    }

    internal fun isRoleDisclosureRequired(sourceText: String): Boolean {
        return SERVICE_ROLE_FAMILY.any { role -> sourceText.contains(role, ignoreCase = true) }
    }

    internal fun containsRoleDisclosure(answer: String): Boolean {
        return SERVICE_ROLE_FAMILY.any { role -> answer.contains(role, ignoreCase = true) }
    }

    internal fun isEnterpriseUncertaintyRequired(sourceText: String): Boolean {
        return ENTERPRISE_UNCERTAINTY_FAMILY.any { phrase -> sourceText.contains(phrase, ignoreCase = true) }
    }

    internal fun containsEnterpriseCertainty(answer: String): Boolean {
        return ENTERPRISE_CERTAINTY_FAMILY.any { phrase -> answer.contains(phrase, ignoreCase = true) }
    }

    internal fun containsEnterpriseUncertainty(answer: String): Boolean {
        return ENTERPRISE_UNCERTAINTY_FAMILY.any { phrase -> answer.contains(phrase, ignoreCase = true) }
    }

    internal fun resolveSourceText(sourceRuleIds: List<Long>): String? {
        val texts = mutableListOf<String>()
        val seen = linkedSetOf<Long>()
        for (ruleId in sourceRuleIds) {
            if (!seen.add(ruleId)) {
                continue
            }
            val rule = qaRuleRepository.findById(ruleId).orElse(null) ?: return null
            if (!rule.enabled) {
                return null
            }
            val body = rule.answerBody.trim()
            if (body.isBlank()) {
                return null
            }
            texts += body
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

        if (STRENGTHENING_PHRASES.any { phrase -> wordBoundaryContains(answer, phrase) }) {
            return true
        }

        val hitFamilies = DEFINITIVE_FAMILIES.filter { family ->
            family.patterns.any { it.containsMatchIn(answer) }
        }
        if (hitFamilies.isEmpty()) {
            return false
        }
        return hitFamilies.any { family ->
            family.patterns.none { it.containsMatchIn(combinedFacts) }
        }
    }

    internal fun containsUnbackedHighRiskDeclarations(answer: String, combinedFacts: String): Boolean {
        val normalizedAnswer = normalizeHighRiskText(answer)
        val normalizedFacts = normalizeHighRiskText(combinedFacts)
        for (entry in HIGH_RISK_PHRASE_FAMILIES) {
            val answerHas = entry.value.any { phrase ->
                wordBoundaryContains(normalizedAnswer, normalizeHighRiskText(phrase))
            }
            if (!answerHas) {
                continue
            }
            val factsHave = entry.value.any { phrase ->
                wordBoundaryContains(normalizedFacts, normalizeHighRiskText(phrase))
            }
            if (!factsHave) {
                return true
            }
        }
        return false
    }

    private fun normalizeHighRiskText(text: String): String =
        text
            .replace(HIGH_RISK_SEPARATOR_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    internal fun wordBoundaryContains(text: String, phrase: String): Boolean {
        val escaped = Regex.escape(phrase)
        return Regex("\\b" + escaped + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    companion object {
        const val WARNING_CLAIM_HALLUCINATED_FACT = "AI_REPLY_CLAIM_HALLUCINATED_FACT"
        const val WARNING_CLAIM_MODALITY_STRENGTHENED = "AI_REPLY_CLAIM_MODALITY_STRENGTHENED"
        const val WARNING_CLAIM_HIGH_RISK_UNBACKED = "AI_REPLY_CLAIM_HIGH_RISK_UNBACKED"
        const val WARNING_CLAIM_SOURCE_UNAVAILABLE = "AI_REPLY_CLAIM_SOURCE_UNAVAILABLE"
        const val WARNING_CLAIM_TRUST_RHETORIC = "AI_REPLY_CLAIM_TRUST_RHETORIC"
        const val WARNING_CLAIM_CONFIDENTIALITY_SUBSTITUTE = "AI_REPLY_CLAIM_CONFIDENTIALITY_SUBSTITUTE"
        const val WARNING_CLAIM_ROLE_DISCLOSURE_OMITTED = "AI_REPLY_CLAIM_ROLE_DISCLOSURE_OMITTED"
        const val WARNING_CLAIM_ENTERPRISE_UNGROUNDED = "AI_REPLY_CLAIM_ENTERPRISE_UNGROUNDED"

        private val HIGH_RISK_SEPARATOR_REGEX = Regex("[\\p{Pd}\\u2212]+")
        private val WHITESPACE_REGEX = Regex("\\s+")

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

        private data class DefinitiveFamily(val name: String, val patterns: List<Regex>)

        private val DEFINITIVE_FAMILIES = listOf(
            DefinitiveFamily(
                "receive_pay_provide",
                listOf(
                    Regex("""\b(will|shall)\s+receive\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+pay\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+provide\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+be\s+paid\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+be\s+provided\b""", setOf(RegexOption.IGNORE_CASE))
                )
            ),
            DefinitiveFamily(
                "cover_reimburse",
                listOf(
                    Regex("""\b(will|shall)\s+cover\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+reimburse\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+be\s+covered\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""\b(will|shall)\s+be\s+reimbursed\b""", setOf(RegexOption.IGNORE_CASE))
                )
            ),
            DefinitiveFamily(
                "entitlement_ownership",
                listOf(
                    Regex("""\b(is|are)\s+entitled\s+to\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""(will|shall)\s+own\b""", setOf(RegexOption.IGNORE_CASE))
                )
            ),
            DefinitiveFamily(
                "contract",
                listOf(
                    Regex("""(will|shall)\s+sign\b""", setOf(RegexOption.IGNORE_CASE)),
                    Regex("""(will|shall)\s+be\s+signed\b""", setOf(RegexOption.IGNORE_CASE))
                )
            )
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

        private val TRUST_RHETORIC_PHRASES = listOf(
            "trust us",
            "you can trust us",
            "rest assured",
            "我们值得信赖",
            "请相信我们",
            "请放心",
            "敬请放心"
        )

        private val CONFIDENTIALITY_SUBSTITUTE_PHRASES = listOf(
            "project sensitive",
            "project sensitivities",
            "项目敏感",
            "项目保密",
            "sensitive so",
            "confidential so",
            "sensitive and cannot",
            "confidential and cannot",
            "保密所以无法",
            "敏感所以无法",
            "敏感无法",
            "保密无法"
        )

        private val SERVICE_ROLE_FAMILY = listOf(
            "service provider",
            "service agency",
            "agency",
            "intermediary",
            "adviser",
            "advisor",
            "consultancy",
            "中介",
            "服务机构"
        )

        private val ENTERPRISE_UNCERTAINTY_FAMILY = listOf(
            "not yet",
            "no specific",
            "currently matching",
            "to be matched",
            "to be confirmed",
            "not yet determined",
            "尚未确定",
            "尚未匹配",
            "后续匹配",
            "待匹配",
            "匹配后",
            "匹配中"
        )

        private val ENTERPRISE_CERTAINTY_FAMILY = listOf(
            "we are",
            "our partner company",
            "the enterprise you will work with",
            "the company is",
            "your matched enterprise",
            "your partnering company",
            "合作企业是",
            "您的合作企业",
            "匹配的企业是",
            "将会合作的企业是",
            "具体企业是"
        )
    }
}
