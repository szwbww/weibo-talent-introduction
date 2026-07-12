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
        return splitUnits(text).mapNotNull { unit ->
            val action = detectDirectRequest(unit) ?: return@mapNotNull null
            if (action in allowed) {
                null
            } else {
                ActionViolation(action = action, sentence = unit)
            }
        }
    }

    fun sanitize(text: String, allowed: Set<AiReplyAction>): Pair<String, Boolean> {
        if (text.isBlank()) {
            return text to false
        }
        val kept = mutableListOf<String>()
        var removed = false
        splitUnits(text).forEach { unit ->
            val action = detectDirectRequest(unit)
            if (action != null && action !in allowed) {
                removed = true
            } else {
                kept += unit
            }
        }
        val cleaned = kept.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
        return cleaned to removed
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

    private fun splitUnits(text: String): List<String> =
        text.split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
