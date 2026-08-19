package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.llm.config.AskEnumeratorProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/**
 * One enumerated ask from the inbound email: a short neutral [label] and a
 * [quote] that is a VERBATIM substring of the inbound text (server-validated,
 * I-1). [originalRange] indexes the ORIGINAL inbound text (not the folded or
 * canonical form) and is used for span claiming (I-7).
 */
data class EnumeratedAsk(
    val label: String,
    val quote: String,
    val originalRange: IntRange
)

/**
 * Fail-open result of [InboundAskEnumerator.enumerate]. [available] is false
 * whenever the enumerator could not produce a usable signal (LLM disabled,
 * client missing, timeout/HTTP/parse failure, empty response, or every item
 * discarded by the I-1 verbatim check); callers then behave exactly as before
 * the enumerator existed (I-4/N6).
 */
data class AskEnumeration(
    val available: Boolean,
    val asks: List<EnumeratedAsk>
)

/**
 * I-7: an ask is claimed when its original-text range overlaps the original
 * range of any alias hit of any matched intent. Overlap is positional — never
 * a count subtraction — so one ask hit by two intents is claimed exactly once.
 */
internal fun claimed(ask: EnumeratedAsk, intentSpans: List<MatchedIntentSpan>): Boolean =
    intentSpans.any { spanSet ->
        spanSet.originalRanges.any { it.first <= ask.originalRange.last && ask.originalRange.first <= it.last }
    }

/**
 * P2a (plan 02-unrecognized-request-detection, 阶段 B): narrow-purpose LLM
 * enumerator. It only extracts "label + verbatim quote" pairs from the inbound
 * email; the quotes are server-validated as substrings (I-1) and never enter
 * any version hash (I-2) or any outbound text/prompt (I-5).
 */
@Service
class InboundAskEnumerator(
    private val llmDraftClientProvider: ObjectProvider<LlmDraftClient>,
    private val llmProperties: LlmProperties,
    private val askEnumeratorProperties: AskEnumeratorProperties,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(InboundAskEnumerator::class.java)

    /**
     * I-4: every failure path returns [AskEnumeration]`(false, emptyList())`
     * and never throws into the caller (workbench bootstrap / auto decision).
     */
    fun enumerate(inboundText: String): AskEnumeration {
        if (!llmProperties.enabled) {
            return AskEnumeration(false, emptyList())
        }
        val client = llmDraftClientProvider.getIfAvailable()
            ?: return AskEnumeration(false, emptyList())
        val llmText = try {
            client.chat(
                messages = listOf(
                    LlmChatMessage(role = "system", content = ASK_ENUMERATION_SYSTEM_PROMPT),
                    LlmChatMessage(role = "user", content = inboundText)
                )
            )?.takeIf { it.isNotBlank() }
        } catch (ex: Exception) {
            log.warn("Ask enumeration LLM failed: {}", ex.message)
            null
        }
        val asks = parse(inboundText, llmText)
        if (asks.isEmpty()) {
            // Empty model response, unparseable payload, or every item discarded
            // by the I-1 verbatim check: no usable signal.
            return AskEnumeration(false, emptyList())
        }
        if (asks.size > MAX_ENUMERATED_ASKS) {
            // I-1: hard cap with an explicit truncation log — never silent.
            log.warn(
                "[ASK_ENUM] truncated: {} asks exceeded the limit of {}",
                asks.size,
                MAX_ENUMERATED_ASKS
            )
            return AskEnumeration(true, asks.take(MAX_ENUMERATED_ASKS))
        }
        return AskEnumeration(true, asks)
    }

    /**
     * I-1: each item must carry a label and a quote; the quote must be a
     * VERBATIM (whitespace-folded, case-sensitive) substring of
     * [inboundText]; folded quotes shorter than 8 characters are discarded;
     * duplicate quotes are dropped; [EnumeratedAsk.originalRange] maps back to
     * original text coordinates via the folding index map.
     */
    internal fun parse(inboundText: String, raw: String?): List<EnumeratedAsk> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val json = extractJsonPayload(raw) ?: return emptyList()
        val nodes = try {
            objectMapper.readValue<List<JsonNode>>(json)
        } catch (ex: Exception) {
            log.warn("Failed to parse ask enumeration JSON: {}", ex.message)
            return emptyList()
        }
        val (foldedText, indexMap) = foldWhitespace(inboundText)
        val seenQuotes = linkedSetOf<String>()
        val result = mutableListOf<EnumeratedAsk>()
        for (node in nodes) {
            val label = node.get("label")?.asText()?.trim().orEmpty()
            val quote = node.get("quote")?.asText()?.trim().orEmpty()
            if (label.isBlank() || quote.isBlank()) {
                continue
            }
            val foldedQuote = foldWhitespace(quote).first
            if (foldedQuote.length < MIN_QUOTE_LENGTH) {
                continue
            }
            val startFolded = foldedText.indexOf(foldedQuote)
            if (startFolded < 0) {
                continue
            }
            if (!seenQuotes.add(foldedQuote)) {
                continue
            }
            val start = indexMap[startFolded]
            val end = indexMap[startFolded + foldedQuote.length - 1] + 1
            result += EnumeratedAsk(
                label = label,
                quote = quote,
                originalRange = start until end
            )
        }
        return result
    }

    /** Same markdown-fence/JSON-array extraction shape as [AiQaExtractionService]. */
    private fun extractJsonPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("```")) {
            val withoutFence = trimmed
                .removePrefix("```json")
                .removePrefix("```")
                .substringBeforeLast("```")
                .trim()
            return withoutFence.takeIf { it.isNotEmpty() }
        }
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }
        return trimmed
    }

    /**
     * I-1 normalization: collapse every `\s+` run to a single space (no
     * lowercasing). Returns the folded text plus an index map from folded
     * positions back to original text positions.
     */
    private fun foldWhitespace(text: String): Pair<String, List<Int>> {
        val out = StringBuilder()
        val indexMap = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isWhitespace()) {
                out.append(' ')
                indexMap.add(i)
                while (i < text.length && text[i].isWhitespace()) {
                    i++
                }
            } else {
                out.append(ch)
                indexMap.add(i)
                i++
            }
        }
        return out.toString() to indexMap
    }

    companion object {
        internal const val MAX_ENUMERATED_ASKS = 12
        internal const val MIN_QUOTE_LENGTH = 8

        /**
         * Verbatim system prompt from plan 02-unrecognized-request-detection
         * 阶段 B-1 (定稿逐字 — never reword).
         */
        internal val ASK_ENUMERATION_SYSTEM_PROMPT = """
            You segment an inbound email from a researcher into the distinct things they are asking for.
            Return ONLY a JSON array. Each element must have:
            - label (string, a short neutral title for the ask, at most 8 words)
            - quote (string, a VERBATIM contiguous substring of the email that expresses this ask)
            The quote must be copied character-for-character from the email. Do not paraphrase, translate,
            correct spelling, or join non-adjacent text. If you cannot quote it verbatim, omit the element.
            Do not include greetings, thanks, sign-offs, or statements about the sender's own background.
            Do not include markdown fences or commentary outside the JSON array.
        """.trimIndent()
    }
}

/**
 * D-4: the [ASK_ENUM] structured log line is assembled by this pure function
 * so the fixed field names and order can be asserted directly. The exact line
 * shape is `[ASK_ENUM] source={} contactId={} available={} enumerated={}
 * claimed={} unrecognized={} kind={}`.
 */
internal fun buildAskEnumLogLine(
    source: String,
    contactId: Long,
    available: Boolean,
    enumerated: Int,
    claimed: Int,
    unrecognized: Int,
    kind: String
): String = "[ASK_ENUM] source=$source contactId=$contactId available=$available " +
    "enumerated=$enumerated claimed=$claimed unrecognized=$unrecognized kind=$kind"
