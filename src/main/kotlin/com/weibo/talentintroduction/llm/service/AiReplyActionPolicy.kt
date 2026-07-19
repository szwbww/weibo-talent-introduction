package com.weibo.talentintroduction.llm.service

enum class AiReplyAction {
    REQUEST_MATERIALS,
    PROPOSE_MEETING
}

data class ActionViolation(
    val action: AiReplyAction,
    val sentence: String,
    val code: String? = null
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

    private val SENSITIVE_MATERIAL = listOf(
        Regex("""(?i)\b(passport|national\s+id|id\s+card|identity\s+card|employment\s+certificate|work\s+certificate|bank\s+statement)\b"""),
        Regex("""旅客|护照|身份证|在职证明|工作证明|银行证明|银行对账单""")
    )

    private val CV_PURPOSE = listOf(
        Regex("""(?i)\b(eligibility\s+(check|review|assessment|screening)|qualification\s+(review|check|screening|assessment)|research\s+match|application\s+(review|assessment|evaluation))"""),
        Regex("""(?i)\b(to\s+)?(assess|evaluate|review|check)\s+(your\s+)?(qualifications?|eligibility|research\s+fit|suitability)\b"""),
        Regex("""资格(初核|审核|审查|匹配|评估)|研究(方向)?匹配|申请(审核|评估)""")
    )

    private val CV_OPTIONALITY = listOf(
        Regex("""(?i)\b(optional|if\s+you\s+wish|if\s+comfortable|if\s+you\s+are\s+comfortable|no\s+pressure|at\s+your\s+(convenience|discretion|earliest\s+convenience)|when\s+convenient|when\s+ready|whenever\s+you'?re\s+ready|if\s+you\s+would\s+like|feel\s+free\s+to|you\s+are\s+welcome\s+to)\b"""),
        Regex("""自愿|方便时|如您愿意|非强制|不作要求|不强求|方便的时候""")
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

    private val CV_ONLY_PATTERN = listOf(
        Regex("""(?i)\b(cv|curriculum vitae|r[eé]sum[eé]s?|resume)\b"""),
        Regex("""简历|履历""")
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

    fun restrictForTrustState(
        allowedActions: Set<AiReplyAction>,
        blockingTrustGap: Boolean
    ): Set<AiReplyAction> {
        if (!blockingTrustGap) {
            return allowedActions
        }
        return allowedActions.filter { it != AiReplyAction.REQUEST_MATERIALS }.toSet()
    }

    fun findViolations(text: String, allowed: Set<AiReplyAction>): List<ActionViolation> {
        if (text.isBlank()) {
            return emptyList()
        }
        return tokenizeUnits(text).mapNotNull { unit ->
            val sensitiveCode = detectSensitiveMaterial(unit.text)
            if (sensitiveCode != null) {
                return@mapNotNull ActionViolation(
                    action = AiReplyAction.REQUEST_MATERIALS,
                    sentence = unit.text.trim(),
                    code = sensitiveCode
                )
            }
            val action = detectDirectRequest(unit.text) ?: return@mapNotNull null
            val violationCode = when (action) {
                AiReplyAction.REQUEST_MATERIALS -> detectCvConditionViolation(unit.text, allowed)
                else -> null
            }
            if (action in allowed && violationCode == null) {
                null
            } else if (action in allowed && violationCode != null) {
                ActionViolation(action = action, sentence = unit.text.trim(), code = violationCode)
            } else {
                ActionViolation(action = action, sentence = unit.text.trim(), code = violationCode)
            }
        }
    }

    fun sanitize(text: String, allowed: Set<AiReplyAction>): Pair<String, Boolean> {
        if (text.isBlank()) {
            return text to false
        }
        val removeRanges = mutableListOf<IntRange>()
        tokenizeUnits(text).forEach { unit ->
            val sensitiveSpans = findPositiveSensitiveCtaSpans(unit.text)
            if (sensitiveSpans.isNotEmpty()) {
                for (span in sensitiveSpans) {
                    removeRanges += (unit.start + span.first) until (unit.start + span.second)
                }
                return@forEach
            }
            val action = detectDirectRequest(unit.text)
            if (action != null && action !in allowed) {
                removeRanges += unit.start until unit.end
            } else if (action == AiReplyAction.REQUEST_MATERIALS && action in allowed) {
                val cvCode = detectCvConditionViolation(unit.text, allowed)
                if (cvCode != null) {
                    removeRanges += unit.start until unit.end
                }
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

    fun detectActions(text: String): Set<AiReplyAction> {
        if (text.isBlank()) {
            return emptySet()
        }
        return tokenizeUnits(text).mapNotNull { unit ->
            val action = detectDirectRequest(unit.text)
            if (action == null && sensitiveMaterialMatch(unit.text)) {
                null
            } else {
                action
            }
        }.toSet()
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

    private val SENSITIVE_MATERIAL_CTA_PREFIX = listOf(
        Regex("""(?i)\b(send|share|provide|forward|submit|attach|upload|deliver|email|mail|fax|scan|copy|supply|hand\s+in|turn\s+in)\b"""),
        Regex("""(?i)\b(could|would|can|will|shall)\s+you\b"""),
        Regex("""(?i)\bplease\b"""),
        Regex("""(?i)\b(?:(?:we|I)\s+)?(?:need|require|request|ask\s+for|want)\b"""),
        Regex("""(?i)\bwould\s+you\s+mind\b"""),
        Regex("""(?i)\b(if|could)\s+you\s+would\s+like\b"""),
        Regex("""请(发送|提供|附上|上传|提交|传)""")
    )

    private val SENSITIVE_NEGATION = Regex(
        """(?i)\b(?:do\s+not|don'?t|does\s+not|doesn'?t|cannot|can'?t|will\s+not|won'?t|never|not)\s+"""
    )

    private val CN_SENSITIVE_NEGATION = listOf(
        Regex("""不(需要|会|能|要|应|可|可以|用|要求|索要)"""),
        Regex("""请勿""")
    )

    private val SENSITIVE_ACTION_BOUNDARY = Regex(
        """(?i)\s*,?\s*\bbut\b\s+|\s*[，,]?\s*(?:但是|而是|但)\s*|[,;；，]\s*|\s+\band\b\s+|以及|并且|和|并"""
    )

    private val SENSITIVE_CONTRAST_BOUNDARY = Regex("""(?i)\bbut\b|但是|而是|但""")

    private val SENSITIVE_NEGATIVE_ACTION_START = listOf(
        Regex(
            """(?i)^(?:(?:we|you|i)\s+)?(?:do\s+not|don'?t|does\s+not|doesn'?t|cannot|can'?t|will\s+not|won'?t|never)\b"""
        ),
        Regex("""^(?:此阶段)?(?:我们|您|你)?(?:不需要|无需|不用|不要|不索要|请勿)""")
    )

    private fun detectSensitiveMaterial(unit: String): String? {
        if (findPositiveSensitiveCtaSpans(unit).isNotEmpty()) {
            return CODE_ACTION_SENSITIVE_MATERIAL
        }
        return null
    }

    private fun isPositiveSensitiveCta(text: String): Boolean {
        return SENSITIVE_MATERIAL.any { it.containsMatchIn(text) } &&
            SENSITIVE_MATERIAL_CTA_PREFIX.any { it.containsMatchIn(text) } &&
            !SENSITIVE_NEGATION.containsMatchIn(text) &&
            CN_SENSITIVE_NEGATION.none { it.containsMatchIn(text) }
    }

    /** Each pair is [start, endExclusive). */
    private fun findPositiveSensitiveCtaSpans(text: String): List<Pair<Int, Int>> {
        if (SENSITIVE_MATERIAL.none { it.containsMatchIn(text) }) {
            return emptyList()
        }

        val boundaries = SENSITIVE_ACTION_BOUNDARY.findAll(text).filter { boundary ->
            SENSITIVE_CONTRAST_BOUNDARY.containsMatchIn(boundary.value) ||
                startsIndependentSensitiveAction(text.substring(boundary.range.last + 1))
        }.toList()
        val spans = mutableListOf<Pair<Int, Int>>()
        var contentStart = 0
        var removalStart = 0
        for (boundary in boundaries) {
            val contentEnd = boundary.range.first
            val content = text.substring(contentStart, contentEnd)
            if (content.isNotBlank() && isPositiveSensitiveCta(content)) {
                spans += removalStart to contentEnd
            }
            removalStart = boundary.range.first
            contentStart = boundary.range.last + 1
        }

        val finalContent = text.substring(contentStart)
        if (finalContent.isNotBlank() && isPositiveSensitiveCta(finalContent)) {
            spans += removalStart to text.length
        }
        return spans
    }

    private fun startsIndependentSensitiveAction(text: String): Boolean {
        val candidate = text.trimStart()
        return SENSITIVE_MATERIAL_CTA_PREFIX.any { pattern ->
            pattern.find(candidate)?.range?.first == 0
        } || SENSITIVE_NEGATIVE_ACTION_START.any { it.containsMatchIn(candidate) }
    }

    private fun sensitiveMaterialMatch(unit: String): Boolean =
        SENSITIVE_MATERIAL.any { it.containsMatchIn(unit) }

    private fun detectCvConditionViolation(unit: String, allowed: Set<AiReplyAction>): String? {
        if (AiReplyAction.REQUEST_MATERIALS !in allowed) {
            return null
        }
        if (!isCvOnlyRequest(unit)) {
            return null
        }
        val hasPurpose = CV_PURPOSE.any { it.containsMatchIn(unit) }
        val hasOptionality = CV_OPTIONALITY.any { it.containsMatchIn(unit) }
        if (!hasPurpose) {
            return CODE_ACTION_CV_PURPOSE_MISSING
        }
        if (!hasOptionality) {
            return CODE_ACTION_CV_OPTIONALITY_MISSING
        }
        return null
    }

    private fun isCvOnlyRequest(unit: String): Boolean {
        if (CV_ONLY_PATTERN.any { it.containsMatchIn(unit) }) {
            return true
        }
        return false
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

    const val CODE_ACTION_SENSITIVE_MATERIAL = "AI_REPLY_ACTION_SENSITIVE_MATERIAL"
    const val CODE_ACTION_CV_PURPOSE_MISSING = "AI_REPLY_ACTION_CV_PURPOSE_MISSING"
    const val CODE_ACTION_CV_OPTIONALITY_MISSING = "AI_REPLY_ACTION_CV_OPTIONALITY_MISSING"
}
