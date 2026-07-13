package com.weibo.talentintroduction.llm.service

enum class AiReplyAction {
    REQUEST_MATERIALS,
    PROPOSE_MEETING
}

data class ActionViolation(
    val action: AiReplyAction,
    val sentence: String
)

object AiReplyActionPolicy {
    private val MATERIAL_INTENT = listOf(
        Regex("""(?i)\b(my|our)\s+(cv|curriculum vitae|resume)\b"""),
        Regex("""(?i)\b(attached|attach|enclosed)\b.{0,40}\b(cv|resume|documents?|materials?)\b"""),
        Regex("""(?i)\b(send|sending|sent)\s+my\s+(cv|documents?|materials?|resume)\b"""),
        Regex("""(?i)\bprovide\s+my\s+cv\b"""),
        Regex("""(?i)\b(ask|request)\b.{0,40}\b(cv|resume|documents?|materials?)\b"""),
        Regex("""(?i)\bplease\s+(ask|request)\b.{0,40}\b(cv|materials?|documents?)\b"""),
        Regex("""附件.{0,10}(简历|履历|材料)|我的简历|索要(简历|材料)|请对方提供(简历|材料)""")
    )

    private val MEETING_INTENT = listOf(
        Regex("""(?i)\b(arrange|schedule|book|set\s+up)\s+(a\s+)?(meeting|call|zoom|teams|webex)\b"""),
        Regex("""(?i)\b(can|could|shall)\s+we\s+(arrange|schedule|meet|talk)\b"""),
        Regex("""(?i)\bconvenient\s+time\b"""),
        Regex("""(?i)\b(zoom|teams|webex)\b"""),
        Regex("""安排会议|预约通话|方便的时间|约个时间|视频会议""")
    )

    private val MATERIAL_REQUEST = listOf(
        Regex("""(?i)\bplease\s+(send|reply\s+with|share|provide|forward)\b.{0,60}\b(cv|curriculum vitae|r[eé]sum[eé]s?|resume|documents?|materials?)\b"""),
        Regex("""(?i)\b(send|share|provide|forward)\s+(me\s+)?(your\s+)?(cv|curriculum vitae|r[eé]sum[eé]s?|resume|documents?|materials?)\b"""),
        Regex("""(?i)\breply\s+with\s+(your\s+)?(cv|r[eé]sum[eé]|resume)\b"""),
        Regex(
            """(?i)\b(could|would|can)\s+you\s+(please\s+)?(share|send|provide|forward)\b.{0,60}\b(cv|curriculum vitae|r[eé]sum[eé]s?|resume|documents?|materials?)\b"""
        ),
        Regex(
            """(?i)\bwould\s+you\s+mind\s+(please\s+)?(sharing|sending|providing|forwarding)\b.{0,60}\b(cv|curriculum vitae|r[eé]sum[eé]s?|resume|documents?|materials?)\b"""
        ),
        Regex("""请(发送|提供|回复).{0,30}(简历|履历|CV|材料|文件)""")
    )

    private val MATERIAL_PROCESS_DESCRIPTION = listOf(
        Regex("""(?i)\b(process|applicants?|application)\b.{0,80}\b(submit|require|requires)\b.{0,40}\b(materials?|documents?|cv)\b"""),
        Regex("""(?i)\b(materials?|documents?)\s+(are|is)\s+(usually\s+)?(submitted|required|needed)\b"""),
        Regex("""申请人.{0,20}(提交|准备).{0,20}(材料|文件)""")
    )

    private val MEETING_REQUEST = listOf(
        Regex("""(?i)\b(let'?s|let\s+us|shall\s+we|can\s+we|could\s+we)\s+(schedule|arrange|set\s+up|book|meet)\b"""),
        Regex("""(?i)\bplease\s+(share|let\s+me\s+know|send)\b.{0,40}\b(convenient\s+time|availability|calendar)\b"""),
        Regex("""(?i)\b(schedule|arrange|book)\s+(a\s+)?(meeting|call|zoom|teams|webex)\b"""),
        Regex("""(?i)\b(zoom|teams|webex)\s+(meeting|call|invite|link)\b"""),
        Regex("""请(安排|预约).{0,20}(会议|通话|时间)|方便(的)?时间""")
    )

    private val MEETING_PROCESS_DESCRIPTION = listOf(
        Regex("""(?i)\bmeetings?\s+(may|can|might)\s+be\s+(arranged|scheduled)\b"""),
        Regex("""(?i)\ba\s+meeting\s+(may|can|might)\s+be\s+(arranged|scheduled)\b"""),
        Regex("""会议(可以|可能).{0,10}(安排|召开)""")
    )

    private val SENTENCE_SPLIT = Regex("""(?<=[.!?。！？])\s+|\n+""")

    fun deriveAllowed(
        inboundText: String,
        operatorInstruction: String?,
        operatorTurns: List<AiReplyTurn>
    ): Set<AiReplyAction> {
        val sources = buildList {
            add(inboundText)
            operatorInstruction?.takeIf { it.isNotBlank() }?.let { add(it) }
            operatorTurns.forEach { turn ->
                turn.operatorInstruction.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
        val allowed = linkedSetOf<AiReplyAction>()
        sources.forEach { source ->
            if (MATERIAL_INTENT.any { it.containsMatchIn(source) }) {
                allowed += AiReplyAction.REQUEST_MATERIALS
            }
            if (MEETING_INTENT.any { it.containsMatchIn(source) }) {
                allowed += AiReplyAction.PROPOSE_MEETING
            }
        }
        return allowed
    }

    fun findViolations(text: String, allowed: Set<AiReplyAction>): List<ActionViolation> {
        if (text.isBlank()) {
            return emptyList()
        }
        return tokenizeUnits(text).mapNotNull { unit ->
            val action = detectDirectRequest(unit.text) ?: return@mapNotNull null
            if (action in allowed) {
                null
            } else {
                ActionViolation(action = action, sentence = unit.text.trim())
            }
        }
    }

    fun sanitize(text: String, allowed: Set<AiReplyAction>): Pair<String, Boolean> {
        if (text.isBlank()) {
            return text to false
        }
        val removeRanges = mutableListOf<IntRange>()
        tokenizeUnits(text).forEach { unit ->
            val action = detectDirectRequest(unit.text)
            if (action != null && action !in allowed) {
                removeRanges += unit.start until unit.end
            }
        }
        if (removeRanges.isEmpty()) {
            return text to false
        }
        val sb = StringBuilder(text.length)
        var cursor = 0
        var afterDeletion = false
        for (range in removeRanges) {
            if (cursor < range.first) {
                if (afterDeletion) {
                    appendCollapsedSeam(sb, text, cursor, range.first)
                } else {
                    sb.append(text, cursor, range.first)
                }
            }
            cursor = range.last + 1
            afterDeletion = true
        }
        if (cursor < text.length) {
            if (afterDeletion) {
                appendCollapsedSeam(sb, text, cursor, text.length)
            } else {
                sb.append(text, cursor, text.length)
            }
        }
        return sb.toString() to true
    }

    fun formatAllowedLabel(allowed: Set<AiReplyAction>): String =
        if (allowed.isEmpty()) "NONE" else allowed.joinToString(",") { it.name }

    private fun detectDirectRequest(unit: String): AiReplyAction? {
        val trimmed = unit.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (MATERIAL_REQUEST.any { it.containsMatchIn(trimmed) } &&
            MATERIAL_PROCESS_DESCRIPTION.none { it.containsMatchIn(trimmed) }
        ) {
            return AiReplyAction.REQUEST_MATERIALS
        }
        if (MEETING_REQUEST.any { it.containsMatchIn(trimmed) } &&
            MEETING_PROCESS_DESCRIPTION.none { it.containsMatchIn(trimmed) }
        ) {
            return AiReplyAction.PROPOSE_MEETING
        }
        return null
    }

    private data class TextUnit(val text: String, val start: Int, val end: Int)

    /** Units with original offsets; delimiters stay as gaps between spans. */
    private fun tokenizeUnits(text: String): List<TextUnit> {
        val units = mutableListOf<TextUnit>()
        var lastEnd = 0
        for (match in SENTENCE_SPLIT.findAll(text)) {
            if (match.range.first > lastEnd) {
                val start = lastEnd
                val end = match.range.first
                val unitText = text.substring(start, end)
                if (unitText.isNotBlank()) {
                    units += TextUnit(text = unitText, start = start, end = end)
                }
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < text.length) {
            val unitText = text.substring(lastEnd)
            if (unitText.isNotBlank()) {
                units += TextUnit(text = unitText, start = lastEnd, end = text.length)
            }
        }
        return units
    }

    /**
     * Append [from, to) after a deletion. Collapse only the newline run that meets at the seam;
     * interior of the kept span stays byte-identical.
     */
    private fun appendCollapsedSeam(sb: StringBuilder, text: String, from: Int, to: Int) {
        var trailStart = sb.length
        while (trailStart > 0 && isNewlineChar(sb[trailStart - 1])) {
            trailStart--
        }
        val trailing = sb.substring(trailStart)
        sb.setLength(trailStart)

        var leadEnd = from
        while (leadEnd < to && isNewlineChar(text[leadEnd])) {
            leadEnd++
        }
        val leading = text.substring(from, leadEnd)
        sb.append(collapseNewlineRun(trailing + leading))
        sb.append(text, leadEnd, to)
    }

    private fun isNewlineChar(c: Char): Boolean = c == '\n' || c == '\r'

    /** Cap seam blank lines: 3+ `\n` → two line breaks (LF or CRLF style). */
    private fun collapseNewlineRun(run: String): String {
        if (run.isEmpty()) {
            return run
        }
        val nCount = run.count { it == '\n' }
        if (nCount <= 2) {
            return run
        }
        val useCrlf = run.contains('\r')
        return if (useCrlf) "\r\n\r\n" else "\n\n"
    }
}
