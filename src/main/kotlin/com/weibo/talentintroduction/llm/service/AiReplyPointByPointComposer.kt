package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import com.weibo.talentintroduction.reply.service.ResolvedReplyFrame
import org.springframework.stereotype.Service

@Service
class AiReplyPointByPointComposer(
    private val qaRuleRepository: QaRuleRepository,
    private val replySnippetService: ReplySnippetService
) {
    fun composeLockedItems(orderedAnswers: List<String>): String {
        require(orderedAnswers.all { it.isNotBlank() }) { "locked answers must be non-empty" }
        val frame = replySnippetService.resolveManualFrame()
        val blocks = buildList {
            frame.salutation?.takeIf { it.isNotBlank() }?.let(::add)
            frame.greeting?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(orderedAnswers)
            frame.closing?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return blocks.joinToString("\n\n")
    }

    /**
     * I-5 explicit-frame locked composer (workbench-only): inserts the resolved
     * salutation, greeting, acknowledgement and closing around the canonical
     * answer list in the fixed order SALUTATION → GREETING → ACK → answers →
     * CLOSING, each block separated by a single blank line. Blank frame blocks
     * are filtered; every non-OMIT locked answer must appear verbatim, in
     * original order, exactly once — no trim, dedupe, reorder or LLM call.
     */
    fun composeLockedItems(orderedAnswers: List<String>, resolvedFrame: ResolvedReplyFrame): String {
        require(orderedAnswers.all { it.isNotBlank() }) { "locked answers must be non-empty" }
        val blocks = buildList {
            resolvedFrame.salutation?.takeIf { it.isNotBlank() }?.let(::add)
            resolvedFrame.greeting?.takeIf { it.isNotBlank() }?.let(::add)
            resolvedFrame.acknowledgement?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(orderedAnswers)
            resolvedFrame.closing?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return blocks.joinToString("\n\n")
    }

    fun composeFromPlan(
        plan: GroundedContentPlan,
        claimTexts: Map<String, String>,
        actionText: String? = null
    ): String {
        val allClaimKeys = plan.claims.map { it.claimKey }.toSet()
        if (allClaimKeys != claimTexts.keys) {
            return ""
        }
        val paragraphs = plan.paragraphs.map { para ->
            para.claimKeys.mapNotNull { claimTexts[it]?.trim() }.joinToString(" ")
        }.toMutableList()
        if (actionText != null && actionText.isNotBlank()) {
            if (paragraphs.isNotEmpty()) {
                val lastIdx = paragraphs.size - 1
                paragraphs[lastIdx] = paragraphs[lastIdx] + " " + actionText.trim()
            } else {
                paragraphs += actionText.trim()
            }
        }
        return assembleGroundedEmail(paragraphs)
    }

    fun composeFromSections(
        requestFacts: List<RequestFactItem>,
        sections: List<ValidatedSection>
    ): String {
        val sectionByIndex = sections.associateBy { it.requestIndex }
        val bodies = linkedSetOf<String>()

        for (item in requestFacts) {
            val section = sectionByIndex[item.index] ?: continue
            if (section.answers.isEmpty()) {
                continue
            }
            section.answers.forEach { answer ->
                val text = answer.answer.trim()
                if (text.isNotBlank()) {
                    bodies += text
                }
            }
        }
        return assembleNaturalEmail(bodies.toList())
    }

    fun composeFallbackReference(
        plan: GroundedContentPlan,
        requestFacts: List<RequestFactItem>
    ): String = buildString {
        appendLine("QA 规则参考内容（LLM 未生成，不能直接发送）")
        appendLine()

        val factsById = requestFacts.associateBy { it.index }

        val paraClaimKeys = plan.paragraphs.flatMap { para -> para.claimKeys }.toSet()
        val paraClaims = plan.claims.filter { it.claimKey in paraClaimKeys }

        val claimGroups = linkedMapOf<Int, MutableList<Long>>()
        for (claim in paraClaims) {
            claimGroups.getOrPut(claim.requestIndex) { mutableListOf() }
                .addAll(claim.sourceIds)
        }

        val missingByIndex = linkedMapOf<Int, MutableList<String>>()
        for (mf in plan.missingFacts) {
            val titles = mf.intentKeys.mapNotNull { intentKey ->
                factsById[mf.requestIndex]?.intents
                    ?.firstOrNull { it.intentKey == intentKey }
                    ?.title
            }
            missingByIndex.getOrPut(mf.requestIndex) { mutableListOf() }
                .addAll(titles.ifEmpty { listOf("未命名问题") })
        }

        val paragraphIndices = plan.paragraphs.flatMap { para -> para.claimKeys }
            .mapNotNull { claimKey -> paraClaims.firstOrNull { it.claimKey == claimKey }?.requestIndex }
            .distinct()
        val missingIndices = plan.missingFacts.map { it.requestIndex }.distinct()
        val orderedIndices = (paragraphIndices + missingIndices).distinct()
        val allIndices = if (orderedIndices.isEmpty()) {
            factsById.keys.sorted()
        } else {
            val originalOrder = requestFacts.map { it.index }
            orderedIndices.sortedBy { originalOrder.indexOf(it).let { idx -> if (idx >= 0) idx else Int.MAX_VALUE } }
        }

        val sourceIdsUnion = paraClaims.flatMap { it.sourceIds }.distinct()

        val rulesById = linkedMapOf<Long, QaRule>()
        for (ruleId in sourceIdsUnion) {
            val rule = qaRuleRepository.findById(ruleId).orElse(null)
            if (rule != null && rule.answerBody.trim().isNotBlank()) {
                rulesById[ruleId] = rule
            }
        }

        val globalSeen = linkedSetOf<Long>()
        for (reqIdx in allIndices) {
            val item = factsById[reqIdx] ?: continue
            val heading = cleanHeading(item.requestText)
            appendLine("问题 ${reqIdx}：$heading")

            val sourceIds = claimGroups[reqIdx].orEmpty().distinct()
            for (ruleId in sourceIds) {
                if (!globalSeen.add(ruleId)) continue
                val rule = rulesById[ruleId] ?: continue
                appendLine("可引用事实：${rule.answerBody.trim()}")
                appendLine("来源：${resolveSourceName(rule)}")
            }

            val hasPartialFacts = item.status == RequestGroundingStatus.PARTIAL && sourceIds.isNotEmpty()
            val missing = missingByIndex[reqIdx]
            if (sourceIds.isEmpty()) {
                if (missing != null && missing.isNotEmpty()) {
                    appendLine("缺失：暂无已审核事实，需人工补充。")
                    for (title in missing) {
                        appendLine("  - $title")
                    }
                } else {
                    appendLine("缺失：暂无已审核事实，需人工补充。")
                }
            } else if (hasPartialFacts && missing != null && missing.isNotEmpty()) {
                appendLine("缺失：以下问题暂无已审核事实，需人工补充。")
                for (title in missing) {
                    appendLine("  - $title")
                }
            } else if (hasPartialFacts) {
                appendLine("缺失：暂无已审核事实，需人工补充。")
            }

            appendLine()
        }
    }.trim()

    private fun resolveSourceName(rule: QaRule): String {
        val name = rule.displayName?.trim().takeIf { !it.isNullOrBlank() && it != "未命名事实" }
        if (name != null) return name
        val section = rule.sectionTitle?.trim().takeIf { !it.isNullOrBlank() && it != "未命名事实" }
        if (section != null) return section
        val subject = rule.replySubject?.trim().takeIf { !it.isNullOrBlank() && it != "未命名事实" }
        if (subject != null) return subject
        return "事实名称缺失"
    }

    fun composeFromAnswers(
        requestFacts: List<RequestFactItem>,
        answersByIndex: Map<Int, String>
    ): String {
        val bodies = linkedSetOf<String>()
        for (item in requestFacts) {
            if (item.status != RequestGroundingStatus.GROUNDED &&
                item.status != RequestGroundingStatus.PARTIAL
            ) {
                continue
            }
            val answer = answersByIndex[item.index]?.trim().orEmpty()
            if (answer.isNotBlank()) {
                bodies += answer
            }
        }
        return assembleNaturalEmail(bodies.toList())
    }

    private fun assembleGroundedEmail(paragraphs: List<String>): String {
        val frame = replySnippetService.resolveManualFrame()
        return buildString {
            frame.salutation?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            paragraphs.filter { it.isNotBlank() }.forEach { paragraph ->
                appendLine(paragraph.trim())
                appendLine()
            }
            frame.closing?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
        }.trim()
    }

    private fun assembleNaturalEmail(paragraphs: List<String>): String {
        val frame = replySnippetService.resolveManualFrame()
        val limited = paragraphs.filter { it.isNotBlank() }.take(4)
        return buildString {
            frame.salutation?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            frame.greeting?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            replySnippetService.resolveAck(null)?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            limited.forEach { paragraph ->
                appendLine(paragraph.trim())
                appendLine()
            }
            frame.closing?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
            }
        }.trim()
    }

    companion object {
        private val LEADING_BULLET_REGEX = Regex("""^\s*(?:[-*•]+|\d+[.)])\s*""")
        private val TRAILING_PUNCT_REGEX = Regex("""[?？;；]+\s*$""")
        private val TRAILING_AND_REGEX = Regex("""(?i)\band\s*$""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun cleanHeading(requestText: String): String {
            var cleaned = requestText
                .replace(LEADING_BULLET_REGEX, "")
                .replace(TRAILING_PUNCT_REGEX, "")
                .replace(TRAILING_AND_REGEX, "")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            cleaned = cleaned.replace(TRAILING_PUNCT_REGEX, "").trim()
            cleaned = capitalizeFirstLetter(cleaned)
            return cleaned.take(HEADING_MAX_CHARS)
        }

        private fun capitalizeFirstLetter(text: String): String {
            val idx = text.indexOfFirst { it in 'A'..'Z' || it in 'a'..'z' }
            if (idx < 0) {
                return text
            }
            return text.substring(0, idx) + text[idx].uppercaseChar() + text.substring(idx + 1)
        }

        private const val HEADING_MAX_CHARS = 160
    }
}
