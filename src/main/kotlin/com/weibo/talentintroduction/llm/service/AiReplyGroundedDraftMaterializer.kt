package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

data class IntentAnswer(
    val intentKey: String,
    val answer: String,
    val sourceRuleIds: List<Long>
)

data class ValidatedSection(
    val requestIndex: Int,
    val answers: List<IntentAnswer>
)

data class MaterializedDraft(
    val text: String,
    val valid: Boolean,
    val sections: List<ValidatedSection> = emptyList(),
    val warningCodes: List<String> = emptyList(),
    val issues: List<AiReplyValidationIssue> = emptyList(),
    internal val actionText: String? = null,
    internal val actionTextValid: Boolean = true
)

private data class ParsedGroundedResponse(
    val claimTexts: Map<String, String>,
    val validatedSections: List<ValidatedSection>,
    val actionText: String?,
    val actionTextValid: Boolean = true,
    val issues: List<AiReplyValidationIssue> = emptyList()
)

private data class ParsedActionText(
    val text: String?,
    val valid: Boolean
)

@Service
class AiReplyGroundedDraftMaterializer(
    private val objectMapper: ObjectMapper,
    private val composer: AiReplyPointByPointComposer
) {
    fun materialize(
        rawResponse: String,
        requestFacts: List<RequestFactItem>,
        plan: GroundedContentPlan
    ): MaterializedDraft {
        val parsed = parseUnifiedJson(rawResponse, plan)
        if (parsed.issues.isNotEmpty()) {
            return invalid(issues = parsed.issues)
        }

        val text = composer.composeFromPlan(plan, parsed.claimTexts, parsed.actionText)
        return MaterializedDraft(
            text = text,
            valid = true,
            sections = parsed.validatedSections,
            actionText = parsed.actionText,
            actionTextValid = parsed.actionTextValid
        )
    }

    internal fun invalid(
        sections: List<ValidatedSection> = emptyList(),
        warningCodes: List<String> = listOf(WARNING_STRUCTURED_RESPONSE_INVALID),
        issues: List<AiReplyValidationIssue> = emptyList()
    ): MaterializedDraft = MaterializedDraft(
        text = "",
        valid = false,
        sections = sections,
        warningCodes = warningCodes,
        issues = issues
    )

    private fun parseUnifiedJson(rawResponse: String, plan: GroundedContentPlan): ParsedGroundedResponse {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank() || trimmed.startsWith("```")) {
            return failure(AiReplyValidationCodes.JSON_INVALID)
        }
        val root = try {
            objectMapper.readTree(trimmed)
        } catch (_: Exception) {
            return failure(AiReplyValidationCodes.JSON_INVALID)
        }
        if (!root.isObject) {
            return failure(AiReplyValidationCodes.JSON_INVALID)
        }
        if (root.fieldNames().asSequence().toSet() != setOf("claims", "actionText")) {
            return failure(AiReplyValidationCodes.TOP_LEVEL_FIELDS_INVALID)
        }
        val claimsNode = root.get("claims")
        if (claimsNode == null || !claimsNode.isArray) {
            return failure(AiReplyValidationCodes.CLAIMS_INVALID)
        }

        val parsedActionText = parseActionText(root.get("actionText"))

        val parsed = linkedMapOf<String, String>()
        val issues = mutableListOf<AiReplyValidationIssue>()
        val planKeys = plan.claims.map { it.claimKey }.toSet()
        for (claimNode in claimsNode) {
            if (!claimNode.isObject || claimNode.fieldNames().asSequence().toSet() != setOf("claimKey", "text")) {
                issues += AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, AiReplyValidationCodes.CLAIM_FIELDS_INVALID)
                continue
            }
            val keyNode = claimNode.get("claimKey")
            val claimKey = keyNode?.takeIf { it.isTextual }?.asText()
            if (claimKey == null || claimKey.isBlank()) {
                issues += AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, AiReplyValidationCodes.CLAIM_FIELDS_INVALID)
                continue
            }
            if (parsed.containsKey(claimKey)) {
                issues += AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, AiReplyValidationCodes.CLAIM_KEY_DUPLICATE, claimKey)
                continue
            }
            if (claimKey !in planKeys) {
                issues += AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, AiReplyValidationCodes.CLAIM_KEY_UNKNOWN, claimKey)
                continue
            }
            val textNode = claimNode.get("text")
            val text = textNode?.takeIf { it.isTextual }?.asText()
            if (text == null || text.isBlank() || containsInternalMarker(text)) {
                issues += AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, AiReplyValidationCodes.CLAIM_TEXT_INVALID, claimKey)
                continue
            }
            parsed[claimKey] = text
        }

        val missingKey = plan.claims.firstOrNull { it.claimKey !in parsed }?.claimKey
        if (issues.isNotEmpty()) {
            return ParsedGroundedResponse(emptyMap(), emptyList(), null, parsedActionText.valid, issues.distinct())
        }
        if (parsed.keys != plan.claims.map { it.claimKey }.toSet()) {
            return failure(AiReplyValidationCodes.CLAIM_SET_MISMATCH, missingKey)
        }

        val sectionAnswers = linkedMapOf<Int, MutableList<IntentAnswer>>()
        plan.claims.forEach { claim ->
            sectionAnswers.getOrPut(claim.requestIndex) { mutableListOf() } += IntentAnswer(
                intentKey = claim.intentKey,
                answer = parsed.getValue(claim.claimKey),
                sourceRuleIds = claim.sourceIds
            )
        }
        val sections = sectionAnswers.entries.sortedBy { it.key }.map { (index, answers) ->
            ValidatedSection(index, answers)
        }
        return ParsedGroundedResponse(parsed, sections, parsedActionText.text, parsedActionText.valid)
    }

    private fun parseActionText(node: JsonNode?): ParsedActionText {
        if (node == null || node.isNull) return ParsedActionText(null, true)
        if (!node.isTextual || node.asText().isBlank()) return ParsedActionText(null, false)
        return ParsedActionText(node.asText(), true)
    }

    private fun failure(code: String, claimKey: String? = null): ParsedGroundedResponse = ParsedGroundedResponse(
        emptyMap(), emptyList(), null,
        actionTextValid = true,
        issues = listOf(AiReplyValidationIssue(AiReplyValidationStage.STRUCTURE, code, claimKey))
    )

    private fun containsInternalMarker(answer: String): Boolean {
        val normalized = answer.replace(Regex("\\s+"), " ")
        return INTERNAL_PHRASES.any { normalized.contains(it, ignoreCase = true) } ||
            STATUS_MARKER.containsMatchIn(answer) || STATUS_TOKEN_REGEX.containsMatchIn(answer)
    }

    companion object {
        const val WARNING_STRUCTURED_RESPONSE_INVALID = "AI_REPLY_STRUCTURED_RESPONSE_INVALID"
        const val WARNING_CLAIM_VALIDATION_FAILED = "AI_REPLY_CLAIM_VALIDATION_FAILED"
        const val WARNING_UNNATURAL_GROUNDED_STRUCTURE = "AI_REPLY_UNNATURAL_GROUNDED_STRUCTURE"

        fun containsNonNaturalGroundedStructure(text: String): Boolean {
            if (NUMBERED_LIST_LINE.containsMatchIn(text) || FIXED_SECTION_HEADING.containsMatchIn(text) ||
                INTERNAL_INTENT_LABEL.containsMatchIn(text) || STATUS_TOKEN_REGEX.containsMatchIn(text) ||
                MARKETING_PHRASES.any { phrase -> text.contains(phrase, ignoreCase = true) }) return true
            return false
        }

        private val NUMBERED_LIST_LINE = Regex("(?m)^\\s*\\d+\\.\\s+\\S")
        private val FIXED_SECTION_HEADING = Regex(
            "(?m)^\\s*(?:Program(?:me)?\\s*&\\s*eligibility|Financial\\s*arrangements)\\s*$",
            RegexOption.IGNORE_CASE
        )
        private val INTERNAL_INTENT_LABEL = Regex(
            "(?<![A-Za-z0-9_.])(?:" +
                (AiReplyIntentCatalog.definitions.map { it.key } + "general.answer").joinToString("|") { Regex.escape(it) } +
                ")(?![A-Za-z0-9_.])"
        )
        private val MARKETING_PHRASES = listOf(
            "trust us", "rest assured", "prestigious", "unique opportunity", "we are delighted",
            "please find our answers below", "do not hesitate"
        )
        private val INTERNAL_PHRASES = listOf(
            "This still needs confirmation on remaining details.",
            "This point is not covered by the approved information currently available",
            "INSUFFICIENT_SAFE_REPLY"
        )
        private val STATUS_MARKER = Regex("STATUS\\s*:", RegexOption.IGNORE_CASE)
        private val STATUS_TOKEN_REGEX = Regex("(?<![A-Za-z])(UNSUPPORTED|PARTIAL|GROUNDED)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    }
}
