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
    val warningCodes: List<String> = emptyList()
)

data class ParsedGroundedResponse(
    val claimTexts: Map<String, String>,
    val validatedSections: List<ValidatedSection>,
    val missingFacts: List<Map<String, Any>>,
    val actionType: String? = null,
    val actionText: String? = null
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
            ?: return invalid(sections = emptyList())
        val text = composer.composeFromPlan(plan, parsed.claimTexts, parsed.actionText)
        if (!validateBodyActions(text, parsed.actionType, parsed.actionText)) {
            return invalid(sections = parsed.validatedSections)
        }
        return MaterializedDraft(text = text, valid = true, sections = parsed.validatedSections)
    }

    private fun validateBodyActions(
        bodyText: String,
        declaredActionType: String?,
        declaredActionText: String?
    ): Boolean {
        if (bodyText.isBlank()) {
            return true
        }
        val bodyActions = AiReplyActionPolicy.detectActions(bodyText)
        val hasDeclaredAction = declaredActionType != null && declaredActionType != "NONE"

        if (!hasDeclaredAction) {
            return bodyActions.isEmpty()
        }

        val expectedActionSet = when (declaredActionType) {
            "REQUEST_MATERIALS" -> setOf(AiReplyAction.REQUEST_MATERIALS)
            "PROPOSE_MEETING" -> setOf(AiReplyAction.PROPOSE_MEETING)
            else -> return false
        }

        if (bodyActions != expectedActionSet) {
            return false
        }

        if (declaredActionText != null) {
            return true
        }
        return false
    }

    internal fun invalid(
        sections: List<ValidatedSection> = emptyList(),
        warningCodes: List<String> = listOf(WARNING_STRUCTURED_RESPONSE_INVALID)
    ): MaterializedDraft =
        MaterializedDraft(text = "", valid = false, sections = sections, warningCodes = warningCodes)

    private fun parseUnifiedJson(
        rawResponse: String,
        plan: GroundedContentPlan
    ): ParsedGroundedResponse? {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank()) {
            return null
        }
        if (trimmed.startsWith("```")) {
            return null
        }
        val root: JsonNode = try {
            objectMapper.readTree(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (!root.isObject) {
            return null
        }
        val fieldNames = root.fieldNames().asSequence().toSet()
        val expectedFields = setOf("paragraphs", "claims", "missingFacts", "proposedAction", "requiresReview")
        if (fieldNames != expectedFields) {
            return null
        }

        val paragraphsNode = root.get("paragraphs")
        if (paragraphsNode == null || !paragraphsNode.isArray) {
            return null
        }
        val claimsNode = root.get("claims")
        if (claimsNode == null || !claimsNode.isArray) {
            return null
        }
        val missingFactsNode = root.get("missingFacts")
        if (missingFactsNode == null || !missingFactsNode.isArray) {
            return null
        }
        val proposedActionNode = root.get("proposedAction")
        if (proposedActionNode == null || !proposedActionNode.isObject) {
            return null
        }
        val requiresReviewNode = root.get("requiresReview")
        if (requiresReviewNode == null || !requiresReviewNode.isBoolean) {
            return null
        }
        val requiresReview = requiresReviewNode.booleanValue()
        if (requiresReview != plan.requiresReview) {
            return null
        }

        val actionEval = evaluateProposedAction(proposedActionNode, plan.allowedActions)
        if (actionEval == null) {
            return null
        }
        val proposedActionText = actionEval.first

        val claimResult = parseClaims(claimsNode, plan)
        if (claimResult == null) {
            return null
        }
        val (claimTexts, validatedSections) = claimResult

        val paragraphsResult = validateParagraphs(paragraphsNode, plan, claimTexts)
        if (paragraphsResult == null) {
            return null
        }

        val missingFactsResult = validateMissingFacts(missingFactsNode, plan)
        if (!missingFactsResult) {
            return null
        }

        if (proposedActionText != null) {
            val detectedActions = AiReplyActionPolicy.detectActions(proposedActionText)
            val expectedAction = when (proposedActionNode.get("type").asText()) {
                "REQUEST_MATERIALS" -> setOf(AiReplyAction.REQUEST_MATERIALS)
                "PROPOSE_MEETING" -> setOf(AiReplyAction.PROPOSE_MEETING)
                else -> null
            }
            if (expectedAction == null || detectedActions != expectedAction) {
                return null
            }
        }

        val actionType = if (proposedActionText != null) proposedActionNode.get("type").asText() else "NONE"

        return ParsedGroundedResponse(
            claimTexts = claimTexts,
            validatedSections = validatedSections,
            missingFacts = missingFactsNode.map { node ->
                val map = linkedMapOf<String, Any>()
                node.fields().forEach { (key, value) ->
                    when {
                        value.isInt -> map[key] = value.intValue()
                        value.isTextual -> map[key] = value.asText()
                        value.isArray -> map[key] = value.map { it.asText() }
                    }
                }
                map
            },
            actionType = actionType,
            actionText = proposedActionText
        )
    }

    private data class ActionParseResult(val type: String, val text: String?)

    private fun evaluateProposedAction(
        node: JsonNode,
        allowedActions: Set<AiReplyAction>
    ): Pair<String?, Boolean>? {
        val actionFields = node.fieldNames().asSequence().toSet()
        if (actionFields != setOf("type", "text")) {
            return null
        }
        if (!node.get("type").isTextual) {
            return null
        }
        val type = node.get("type").asText()
        if (type !in setOf("NONE", "REQUEST_MATERIALS", "PROPOSE_MEETING")) {
            return null
        }

        if (type == "NONE") {
            if (!node.get("text").isNull) {
                return null
            }
            return null to true
        }

        if (node.get("text").isNull) {
            return null
        }
        if (!node.get("text").isTextual) {
            return null
        }
        val text = node.get("text").asText()
        if (text.isBlank()) {
            return null
        }

        val action = when (type) {
            "REQUEST_MATERIALS" -> AiReplyAction.REQUEST_MATERIALS
            "PROPOSE_MEETING" -> AiReplyAction.PROPOSE_MEETING
            else -> return null
        }
        if (action !in allowedActions) {
            return null
        }
        return text to true
    }

    private data class ClaimParseResult(
        val claimTexts: Map<String, String>,
        val validatedSections: List<ValidatedSection>
    )

    private fun parseClaims(
        claimsNode: JsonNode,
        plan: GroundedContentPlan
    ): ClaimParseResult? {
        val planKeys = plan.claims.map { it.claimKey }
        val planClaimMap = plan.claims.associateBy { it.claimKey }
        val parsedKeys = mutableListOf<String>()
        val claimTexts = linkedMapOf<String, String>()
        val sectionAnswers = linkedMapOf<Int, MutableList<IntentAnswer>>()

        for (claimNode in claimsNode) {
            if (!claimNode.isObject) {
                return null
            }
            val claimFields = claimNode.fieldNames().asSequence().toSet()
            if (claimFields != setOf("claimKey", "requestIndex", "intentKey", "text", "sourceIds")) {
                return null
            }

            if (!claimNode.get("claimKey").isTextual) {
                return null
            }
            val claimKey = claimNode.get("claimKey").asText()

            if (claimKey in parsedKeys) {
                return null
            }
            parsedKeys += claimKey

            val planClaim = planClaimMap[claimKey] ?: return null

            val requestIndexNode = claimNode.get("requestIndex") ?: return null
            if (!requestIndexNode.isIntegralNumber || !requestIndexNode.canConvertToInt()) {
                return null
            }
            val requestIndex = requestIndexNode.intValue()
            if (requestIndex != planClaim.requestIndex) {
                return null
            }

            if (!claimNode.get("intentKey").isTextual) {
                return null
            }
            val intentKey = claimNode.get("intentKey").asText()
            if (intentKey != planClaim.intentKey) {
                return null
            }

            if (!claimNode.get("text").isTextual) {
                return null
            }
            val text = claimNode.get("text").asText()
            if (text.isBlank()) {
                return null
            }
            if (containsInternalMarker(text)) {
                return null
            }

            val sourceIdsNode = claimNode.get("sourceIds")
            if (sourceIdsNode == null || !sourceIdsNode.isArray) {
                return null
            }
            if (!sourceIdsNode.asSequence().all {
                    it.isIntegralNumber && it.canConvertToLong()
                }
            ) {
                return null
            }
            val sourceIds = sourceIdsNode.asSequence()
                .map { it.longValue() }
                .toList()
            if (sourceIds.isEmpty()) {
                return null
            }
            val distinctIds = sourceIds.distinct()
            if (distinctIds.size != sourceIds.size) {
                return null
            }
            val planEvidence = planClaim.sourceIds.toSet()
            if (sourceIds.any { it !in planEvidence }) {
                return null
            }

            claimTexts[claimKey] = text
            sectionAnswers.getOrPut(requestIndex) { mutableListOf() } += IntentAnswer(
                intentKey = intentKey,
                answer = text,
                sourceRuleIds = sourceIds
            )
        }

        if (parsedKeys != planKeys) {
            return null
        }

        val validatedSections = sectionAnswers.entries
            .sortedBy { it.key }
            .map { (idx, answers) -> ValidatedSection(requestIndex = idx, answers = answers) }

        return ClaimParseResult(claimTexts = claimTexts, validatedSections = validatedSections)
    }

    private fun validateParagraphs(
        paragraphsNode: JsonNode,
        plan: GroundedContentPlan,
        claimTexts: Map<String, String>
    ): List<Map<String, Any>>? {
        val parsedParagraphs = mutableListOf<Map<String, Any>>()
        val allClaimKeysInOrder = mutableListOf<String>()
        val seenParagraphIndexes = mutableSetOf<Int>()

        for (paraNode in paragraphsNode) {
            if (!paraNode.isObject) {
                return null
            }
            val paraFields = paraNode.fieldNames().asSequence().toSet()
            if (paraFields != setOf("paragraphIndex", "claimKeys")) {
                return null
            }

            val paragraphIndexNode = paraNode.get("paragraphIndex") ?: return null
            if (!paragraphIndexNode.isIntegralNumber || !paragraphIndexNode.canConvertToInt()) {
                return null
            }
            val paragraphIndex = paragraphIndexNode.intValue()
            if (!seenParagraphIndexes.add(paragraphIndex)) {
                return null
            }

            val claimKeysNode = paraNode.get("claimKeys")
            if (claimKeysNode == null || !claimKeysNode.isArray) {
                return null
            }
            val paraKeys = mutableListOf<String>()
            val paraSeenKeys = mutableSetOf<String>()
            for (keyNode in claimKeysNode) {
                if (!keyNode.isTextual) {
                    return null
                }
                val key = keyNode.asText()
                if (key !in claimTexts) {
                    return null
                }
                if (!paraSeenKeys.add(key)) {
                    return null
                }
                paraKeys += key
                allClaimKeysInOrder += key
            }
            parsedParagraphs += mapOf<String, Any>(
                "paragraphIndex" to paragraphIndex,
                "claimKeys" to paraKeys
            )
        }

        val planParagraphs = plan.paragraphs
        if (parsedParagraphs.size != planParagraphs.size) {
            return null
        }

        for (i in parsedParagraphs.indices) {
            val planPara = planParagraphs[i]
            val parsedPara = parsedParagraphs[i]
            if (parsedPara["paragraphIndex"] as Int != planPara.paragraphIndex) {
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val paraKeyList = parsedPara["claimKeys"] as List<String>
            if (paraKeyList != planPara.claimKeys) {
                return null
            }
        }

        return parsedParagraphs
    }

    private fun validateMissingFacts(
        missingFactsNode: JsonNode,
        plan: GroundedContentPlan
    ): Boolean {
        val planMissingMap = plan.missingFacts
            .sortedBy { it.requestIndex }
            .map { mapOf("requestIndex" to it.requestIndex, "intentKeys" to it.intentKeys) }

        val parsedMissing = mutableListOf<Map<String, Any>>()
        for (node in missingFactsNode) {
            if (!node.isObject) return false
            val fields = node.fieldNames().asSequence().toSet()
            if (fields != setOf("requestIndex", "intentKeys")) return false
            val riNode = node.get("requestIndex")
            if (riNode == null || !riNode.isIntegralNumber || !riNode.canConvertToInt()) return false
            val ri = riNode.intValue()
            val ikNode = node.get("intentKeys")
            if (ikNode == null || !ikNode.isArray) return false
            val keys = mutableListOf<String>()
            for (kn in ikNode) {
                if (!kn.isTextual) return false
                keys += kn.asText()
            }
            parsedMissing += mapOf("requestIndex" to ri, "intentKeys" to keys)
        }

        if (parsedMissing.size != planMissingMap.size) {
            return false
        }

        for (i in parsedMissing.indices) {
            @Suppress("UNCHECKED_CAST")
            val pKeys = parsedMissing[i]["intentKeys"] as? List<String> ?: return false
            @Suppress("UNCHECKED_CAST")
            val planKeys = planMissingMap[i]["intentKeys"] as? List<String> ?: return false
            if (pKeys != planKeys) {
                return false
            }
            if (parsedMissing[i]["requestIndex"] != planMissingMap[i]["requestIndex"]) {
                return false
            }
        }

        return true
    }

    private fun containsInternalMarker(answer: String): Boolean {
        val normalized = answer.replace(Regex("\\s+"), " ")
        if (INTERNAL_PHRASES.any { phrase -> normalized.contains(phrase, ignoreCase = true) }) {
            return true
        }
        if (STATUS_MARKER.containsMatchIn(answer)) {
            return true
        }
        return STATUS_TOKEN_REGEX.containsMatchIn(answer)
    }

    companion object {
        const val WARNING_STRUCTURED_RESPONSE_INVALID = "AI_REPLY_STRUCTURED_RESPONSE_INVALID"
        const val WARNING_CLAIM_VALIDATION_FAILED = "AI_REPLY_CLAIM_VALIDATION_FAILED"
        const val WARNING_UNNATURAL_GROUNDED_STRUCTURE = "AI_REPLY_UNNATURAL_GROUNDED_STRUCTURE"

        fun containsNonNaturalGroundedStructure(text: String): Boolean {
            if (NUMBERED_LIST_LINE.containsMatchIn(text)) {
                return true
            }
            if (FIXED_SECTION_HEADING.containsMatchIn(text)) {
                return true
            }
            if (INTERNAL_INTENT_LABEL.containsMatchIn(text)) {
                return true
            }
            if (STATUS_TOKEN_REGEX.containsMatchIn(text)) {
                return true
            }
            if (RULE_ID_LABEL.containsMatchIn(text)) {
                return true
            }
            if (MARKETING_PHRASES.any { phrase -> text.contains(phrase, ignoreCase = true) }) {
                return true
            }
            return false
        }

        private val NUMBERED_LIST_LINE = Regex("""(?m)^\s*\d+\.\s+\S""")
        private val FIXED_SECTION_HEADING = Regex(
            """(?m)^\s*(?:Program(?:me)?\s*&\s*eligibility|Financial\s*arrangements)\s*$""",
            RegexOption.IGNORE_CASE
        )
        private val INTERNAL_INTENT_LABEL = Regex(
            """(?<![A-Za-z0-9_.])[a-z]+\.[a-z_]+(?:\.[a-z_]+)?(?![A-Za-z0-9_.])"""
        )
        private val RULE_ID_LABEL = Regex(
            """(?i)(?:\bRULE\s+\d+\b|EVIDENCE_RULE_IDS|INTENT:\s*\w+\.\w+)"""
        )

        private val MARKETING_PHRASES = listOf(
            "trust us",
            "rest assured",
            "prestigious",
            "unique opportunity",
            "we are delighted",
            "please find our answers below",
            "do not hesitate"
        )

        private val INTERNAL_PHRASES = listOf(
            "This still needs confirmation on remaining details.",
            "This point is not covered by the approved information currently available",
            "INSUFFICIENT_SAFE_REPLY"
        )

        private val STATUS_MARKER = Regex("""STATUS\s*:""", RegexOption.IGNORE_CASE)
        private val STATUS_TOKEN_REGEX = Regex(
            """(?<![A-Za-z])(UNSUPPORTED|PARTIAL|GROUNDED)(?![A-Za-z])""",
            RegexOption.IGNORE_CASE
        )
    }
}
