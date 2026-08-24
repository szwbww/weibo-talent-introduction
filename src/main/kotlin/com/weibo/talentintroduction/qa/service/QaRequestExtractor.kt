package com.weibo.talentintroduction.qa.service

import java.util.Locale

/**
 * Offset-aware request unit extractor shared by suggestion and automatic gap counting.
 * Soft newlines inside a paragraph fold to spaces; blank paragraphs are hard boundaries.
 * All returned offsets are coordinates into the original [messageBody].
 */
object QaRequestExtractor {

    enum class Kind { BULLET, QUESTION, FALLBACK }

    data class ExtractedRequest(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val kind: Kind
    )

    fun extract(messageBody: String): List<ExtractedRequest> {
        if (messageBody.isBlank()) {
            return emptyList()
        }

        val bullets = extractBullets(messageBody)
        val questions = extractQuestions(messageBody)
        val nonOverlappingQuestions = questions.filter { question ->
            bullets.none { bullet ->
                rangesOverlap(bullet.startOffset, bullet.endOffset, question.startOffset, question.endOffset)
            }
        }

        val combined = (bullets + nonOverlappingQuestions)
            .sortedBy { it.startOffset }

        val seen = mutableSetOf<String>()
        val deduped = combined.filter { seen.add(normalizeForDedup(it.text)) }

        if (deduped.isNotEmpty()) {
            return deduped
        }

        val trimmed = messageBody.trim()
        return when {
            trimmed.isBlank() -> emptyList()
            isUrlOnlyRequestFragment(trimmed) -> emptyList()
            else -> {
                val start = messageBody.indexOf(trimmed)
                listOf(
                    ExtractedRequest(
                        text = trimmed,
                        startOffset = start,
                        endOffset = start + trimmed.length,
                        kind = Kind.FALLBACK
                    )
                )
            }
        }
    }

    private fun extractBullets(body: String): List<ExtractedRequest> {
        val lines = splitLinesWithOffsets(body)
        val results = mutableListOf<ExtractedRequest>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.text.trim()
            if (trimmed.isBlank() || !BULLET_LINE_PATTERN.containsMatchIn(trimmed)) {
                i++
                continue
            }
            if (isUrlOnlyRequestFragment(trimmed)) {
                i++
                continue
            }

            var endExclusive = line.endExclusive
            var j = i + 1
            while (j < lines.size) {
                val next = lines[j]
                val nextTrimmed = next.text.trim()
                if (nextTrimmed.isBlank()) {
                    break
                }
                if (BULLET_LINE_PATTERN.containsMatchIn(nextTrimmed)) {
                    break
                }
                // Continuation only for indented soft-wrap lines (plan T1)
                if (!next.text.startsWith(" ") && !next.text.startsWith("\t")) {
                    break
                }
                endExclusive = next.endExclusive
                j++
            }

            val rangeStart = trimStart(body, line.startInclusive, endExclusive)
            val rangeEnd = trimEnd(body, rangeStart, endExclusive)
            if (rangeStart < rangeEnd) {
                val raw = body.substring(rangeStart, rangeEnd)
                if (!isUrlOnlyRequestFragment(raw)) {
                    results += ExtractedRequest(
                        text = foldSoftNewlines(raw),
                        startOffset = rangeStart,
                        endOffset = rangeEnd,
                        kind = Kind.BULLET
                    )
                }
            }
            i = j
        }
        return results
    }

    private fun extractQuestions(body: String): List<ExtractedRequest> {
        val masked = maskUrls(body)
        val paragraphs = splitParagraphs(body)
        val results = mutableListOf<ExtractedRequest>()

        for (paragraph in paragraphs) {
            if (paragraph.startInclusive >= paragraph.endExclusive) {
                continue
            }
            val paraMasked = masked.substring(paragraph.startInclusive, paragraph.endExclusive)

            // searchable view: soft newlines (CRLF/CR/LF) → space; map back to original-in-paragraph
            val searchable = StringBuilder()
            val indexMap = mutableListOf<Int>()
            var idx = 0
            while (idx < paraMasked.length) {
                val newlineLen = newlineLengthAt(paraMasked, idx)
                if (newlineLen > 0) {
                    searchable.append(' ')
                    indexMap.add(idx)
                    idx += newlineLen
                } else {
                    searchable.append(paraMasked[idx])
                    indexMap.add(idx)
                    idx++
                }
            }
            indexMap.add(paraMasked.length)

            val searchableText = searchable.toString()
            var cursor = 0
            while (cursor < searchableText.length) {
                val qPos = indexOfQuestionMark(searchableText, cursor)
                if (qPos < 0) {
                    break
                }

                val startSearchable = findQuestionStart(searchableText, qPos)
                val endSearchable = qPos + 1

                val startInPara = indexMap[startSearchable]
                val endInPara = indexMap[endSearchable]

                var absStart = paragraph.startInclusive + startInPara
                val absEnd = paragraph.startInclusive + endInPara
                absStart = trimStart(body, absStart, absEnd)
                val trimmedEnd = trimEnd(body, absStart, absEnd)

                if (absStart < trimmedEnd) {
                    val raw = body.substring(absStart, trimmedEnd)
                    if (raw.isNotBlank() && !isUrlOnlyRequestFragment(raw)) {
                        results += ExtractedRequest(
                            text = foldSoftNewlines(raw),
                            startOffset = absStart,
                            endOffset = trimmedEnd,
                            kind = Kind.QUESTION
                        )
                    }
                }
                cursor = endSearchable
            }
        }
        return results
    }

    private fun findQuestionStart(searchable: String, questionPos: Int): Int {
        var i = questionPos - 1
        while (i >= 0) {
            val ch = searchable[i]
            if (ch == '?' || ch == '？' || ch == '.' || ch == '!' || ch == '。' || ch == '！') {
                break
            }
            i--
        }
        var start = i + 1
        while (start < questionPos && searchable[start].isWhitespace()) {
            start++
        }
        return start
    }

    private fun indexOfQuestionMark(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            val ch = text[i]
            if (ch == '?' || ch == '？') {
                return i
            }
            i++
        }
        return -1
    }

    private data class LineSpan(val text: String, val startInclusive: Int, val endExclusive: Int)

    private data class ParagraphSpan(val startInclusive: Int, val endExclusive: Int)

    private fun splitLinesWithOffsets(body: String): List<LineSpan> {
        val lines = mutableListOf<LineSpan>()
        var start = 0
        var i = 0
        while (i <= body.length) {
            if (i == body.length) {
                lines += LineSpan(body.substring(start, i), start, i)
                break
            }
            val nl = newlineLengthAt(body, i)
            if (nl > 0) {
                lines += LineSpan(body.substring(start, i), start, i)
                i += nl
                start = i
            } else {
                i++
            }
        }
        return lines
    }

    private fun splitParagraphs(body: String): List<ParagraphSpan> {
        val paragraphs = mutableListOf<ParagraphSpan>()
        var start = 0
        var i = 0
        while (i <= body.length) {
            if (i == body.length) {
                var paraEnd = i
                while (paraEnd > start && isNewlineChar(body[paraEnd - 1])) {
                    paraEnd--
                }
                if (paraEnd > start) {
                    paragraphs += ParagraphSpan(start, paraEnd)
                }
                break
            }

            val firstNl = newlineLengthAt(body, i)
            if (firstNl > 0) {
                val afterFirst = i + firstNl
                val secondNl = if (afterFirst <= body.length) newlineLengthAt(body, afterFirst) else 0
                if (secondNl > 0) {
                    // blank paragraph boundary
                    var paraEnd = i
                    while (paraEnd > start && isNewlineChar(body[paraEnd - 1])) {
                        paraEnd--
                    }
                    if (paraEnd > start) {
                        paragraphs += ParagraphSpan(start, paraEnd)
                    }
                    var skip = afterFirst + secondNl
                    while (skip < body.length) {
                        val more = newlineLengthAt(body, skip)
                        if (more == 0) break
                        skip += more
                    }
                    start = skip
                    i = skip
                    continue
                }
            }
            i++
        }
        return paragraphs
    }

    /** Length of newline sequence at [index]: 2 for CRLF, 1 for CR or LF, else 0. */
    private fun newlineLengthAt(text: String, index: Int): Int {
        if (index >= text.length) return 0
        return when {
            text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n' -> 2
            text[index] == '\r' || text[index] == '\n' -> 1
            else -> 0
        }
    }

    private fun isNewlineChar(ch: Char): Boolean = ch == '\n' || ch == '\r'

    private fun trimStart(body: String, start: Int, end: Int): Int {
        var s = start
        while (s < end && body[s].isWhitespace()) {
            s++
        }
        return s
    }

    private fun trimEnd(body: String, start: Int, end: Int): Int {
        var e = end
        while (e > start && body[e - 1].isWhitespace()) {
            e--
        }
        return e
    }

    private fun maskUrls(body: String): String {
        val masked = StringBuilder(body)
        for (match in URL_PATTERN.findAll(body)) {
            for (idx in match.range) {
                masked[idx] = ' '
            }
        }
        return masked.toString()
    }

    private fun foldSoftNewlines(text: String): String =
        text.replace(Regex("[ \\t]*\\r\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), " ")
            .replace(Regex("[ \\t]*\\r[ \\t]*"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()

    private fun rangesOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
        aStart < bEnd && bStart < aEnd

    private fun isUrlOnlyRequestFragment(text: String): Boolean {
        val withoutUrls = URL_PATTERN.replace(text, " ")
        return withoutUrls.none { it.isLetterOrDigit() }
    }

    private fun normalizeForDedup(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    private val URL_PATTERN = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    // 计划 01 (I-2): bullet markers are explicit list markers only — a symbol or
    // numeric marker MUST be followed by whitespace. `*Name*`, `*Title*`,
    // `-not a list` are Markdown emphasis / hyphenated text, never bullets.
    private val BULLET_LINE_PATTERN = Regex("^(?:[-*•]\\s+|\\d+[.)]\\s+)")
}
