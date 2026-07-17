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

@Service
class AiReplyGroundedDraftMaterializer(
    private val objectMapper: ObjectMapper,
    private val composer: AiReplyPointByPointComposer
) {
    fun materialize(rawResponse: String, requestFacts: List<RequestFactItem>): MaterializedDraft {
        val sections = parseStrict(rawResponse, requestFacts)
            ?: return invalid(sections = emptyList())
        val text = composer.composeFromSections(requestFacts, sections)
        return MaterializedDraft(text = text, valid = true, sections = sections)
    }

    internal fun invalid(
        sections: List<ValidatedSection> = emptyList(),
        warningCodes: List<String> = listOf(WARNING_STRUCTURED_RESPONSE_INVALID)
    ): MaterializedDraft =
        MaterializedDraft(text = "", valid = false, sections = sections, warningCodes = warningCodes)

    private fun parseStrict(
        rawResponse: String,
        requestFacts: List<RequestFactItem>
    ): List<ValidatedSection>? {
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
        if (fieldNames != setOf("sections")) {
            return null
        }
        val sectionsNode = root.get("sections")
        if (sectionsNode == null || !sectionsNode.isArray) {
            return null
        }

        val factByIndex = requestFacts.associateBy { it.index }
        val sections = mutableListOf<ValidatedSection>()
        val seenRequestIndexes = mutableSetOf<Int>()

        for (sectionNode in sectionsNode) {
            if (!sectionNode.isObject) {
                return null
            }
            val sectionFields = sectionNode.fieldNames().asSequence().toSet()
            if (sectionFields != setOf("requestIndex", "answers")) {
                return null
            }
            val requestIndexNode = sectionNode.get("requestIndex") ?: return null
            if (!requestIndexNode.isIntegralNumber || !requestIndexNode.canConvertToInt()) {
                return null
            }
            val requestIndex = requestIndexNode.intValue()
            val answersNode = sectionNode.get("answers")
            if (answersNode == null || !answersNode.isArray) {
                return null
            }

            if (!seenRequestIndexes.add(requestIndex)) {
                return null
            }

            val fact = factByIndex[requestIndex] ?: return null

            val allowedStatuses = setOf(
                RequestGroundingStatus.GROUNDED,
                RequestGroundingStatus.PARTIAL
            )
            if (fact.status !in allowedStatuses) {
                return null
            }

            val allowedIntentKeys = fact.intents
                .filter { it.status == "SUPPORTED" }
                .map { it.intentKey }
                .toSet()
            if (allowedIntentKeys.isEmpty()) {
                return null
            }

            val answers = mutableListOf<IntentAnswer>()
            val seenIntentKeys = mutableSetOf<String>()

            for (answerNode in answersNode) {
                if (!answerNode.isObject) {
                    return null
                }
                val answerFields = answerNode.fieldNames().asSequence().toSet()
                if (answerFields != setOf("intentKey", "answer", "sourceRuleIds")) {
                    return null
                }
                if (!answerNode.get("intentKey").isTextual) {
                    return null
                }
                val intentKey = answerNode.get("intentKey").asText()
                if (!answerNode.get("answer").isTextual) {
                    return null
                }
                val answer = answerNode.get("answer").asText()
                if (answer.isBlank()) {
                    return null
                }
                if (containsInternalMarker(answer)) {
                    return null
                }
                if (intentKey !in allowedIntentKeys) {
                    return null
                }
                if (!seenIntentKeys.add(intentKey)) {
                    return null
                }

                val sourceIdsNode = answerNode.get("sourceRuleIds")
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

                val intent = fact.intents.firstOrNull { it.intentKey == intentKey } ?: return null
                val evidenceIds = intent.evidenceRuleIds.toSet()
                if (sourceIds.any { it !in evidenceIds }) {
                    return null
                }

                answers += IntentAnswer(
                    intentKey = intentKey,
                    answer = answer,
                    sourceRuleIds = sourceIds
                )
            }

            if (seenIntentKeys != allowedIntentKeys) {
                return null
            }

            if (answers.isEmpty()) {
                return null
            }

            sections += ValidatedSection(
                requestIndex = requestIndex,
                answers = answers
            )
        }

        val expectedRequestIndexes = requestFacts
            .filter { fact ->
                fact.intents.any { it.status == "SUPPORTED" } &&
                    (fact.status == RequestGroundingStatus.GROUNDED ||
                        fact.status == RequestGroundingStatus.PARTIAL)
            }
            .map { it.index }
            .toSet()

        if (expectedRequestIndexes.isNotEmpty() && seenRequestIndexes != expectedRequestIndexes) {
            return null
        }

        return sections.sortedBy { it.requestIndex }
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
