package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

internal data class GroundedAnswer(
    val index: Int,
    val answer: String
)

internal data class GroundedAnswerEnvelope(
    val answers: List<GroundedAnswer>
)

data class MaterializedDraft(
    val text: String,
    val valid: Boolean,
    val warningCodes: List<String> = emptyList()
)

@Service
class AiReplyGroundedDraftMaterializer(
    private val objectMapper: ObjectMapper,
    private val composer: AiReplyPointByPointComposer
) {
    fun materialize(rawResponse: String, requestFacts: List<RequestFactItem>): MaterializedDraft {
        val parsed = parseStrict(rawResponse, requestFacts)
            ?: return invalid()
        val text = composer.composeFromAnswers(
            requestFacts = requestFacts,
            answersByIndex = parsed.answers.associate { it.index to it.answer }
        )
        return MaterializedDraft(text = text, valid = true)
    }

    private fun invalid(): MaterializedDraft =
        MaterializedDraft(
            text = "",
            valid = false,
            warningCodes = listOf(WARNING_STRUCTURED_RESPONSE_INVALID)
        )

    private fun parseStrict(rawResponse: String, requestFacts: List<RequestFactItem>): GroundedAnswerEnvelope? {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank()) {
            return null
        }
        // Markdown fences are rejected (I-1); do not strip.
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
        if (fieldNames != setOf("answers")) {
            return null
        }
        val answersNode = root.get("answers")
        if (answersNode == null || !answersNode.isArray) {
            return null
        }

        val answerableIndexes = requestFacts
            .filter {
                it.status == RequestGroundingStatus.GROUNDED || it.status == RequestGroundingStatus.PARTIAL
            }
            .map { it.index }
            .toSet()
        val unsupportedIndexes = requestFacts
            .filter { it.status == RequestGroundingStatus.UNSUPPORTED }
            .map { it.index }
            .toSet()

        val answers = mutableListOf<GroundedAnswer>()
        val seenIndexes = mutableSetOf<Int>()

        for (node in answersNode) {
            if (!node.isObject) {
                return null
            }
            val answerFields = node.fieldNames().asSequence().toSet()
            if (answerFields != setOf("index", "answer")) {
                return null
            }
            if (!node.get("index").canConvertToInt()) {
                return null
            }
            val index = node.get("index").asInt()
            if (!node.get("answer").isTextual) {
                return null
            }
            val answer = node.get("answer").asText()
            if (answer.isBlank()) {
                return null
            }
            if (containsInternalMarker(answer)) {
                return null
            }
            if (index in unsupportedIndexes) {
                return null
            }
            if (index !in answerableIndexes) {
                return null
            }
            if (!seenIndexes.add(index)) {
                return null
            }
            answers += GroundedAnswer(index = index, answer = answer)
        }

        if (seenIndexes != answerableIndexes) {
            return null
        }
        return GroundedAnswerEnvelope(answers = answers.sortedBy { it.index })
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
