package com.weibo.talentintroduction.llm.service

/**
 * 计划 01（item-answer-strip-frame-phrases）的确定性剥离策略：在生成侧
 * （answerText 定稿前）把模型偶尔带出的邮件框架语气词（称呼、开场致谢、
 * 收尾客套与落款）从条目答案中静默剥离，避免整合成整封邮件时与 frame 片段重复。
 *
 * 不变量（详见计划 I-2 ~ I-6）：
 *  - I-2：结果空白时返回原始 text 原样并标记 skipped=true —— 剥离永不成为生成失败成因。
 *  - I-3：无删除时逐字返回原文；有删除时只删整块（blank-line 分隔）或首块的开场致谢整句，
 *    其余字符与换行逐字保留，仅规范删除接缝处的多余空行。
 *  - I-4：只按「块索引」与「句尾偏移」切分，不引入 IntRange 端点转换（闭区间不做开区间换算）。
 *  - I-5：称呼/落款按整块匹配（只删开头连续/结尾连续），开场致谢按剩余首块的首句匹配
 *    （句须同时含致谢动词与来信名词）。
 *  - I-6：两条生成链路共用本对象，禁止在各调用点另写正则。
 */
data class FrameStripResult(val text: String, val stripped: Boolean, val skipped: Boolean)

object AiReplyFramePhrasePolicy {
    private val BLOCK_SEPARATOR = Regex("""\n\s*\n""")

    // 句边界判据与 AiReplyActionPolicy.SENTENCE_SPLIT 保持一致（I-5 第三点），
    // 但本策略自带私有常量，不 import 其它策略的 private 成员。
    private val SENTENCE_BOUNDARY = Regex("""(?<=[.!?。！？])\s+|\n+""")

    // I-5 第一条：称呼块（整块匹配，只删开头连续）。
    private val SALUTATION_BLOCK = Regex("""(?i)^(?:Dear|Hi|Hello)\b[^.\n]{0,60}[,，]?$""")

    // I-5 第二条：落款块（整块匹配，只删结尾连续），可带尾逗号。
    private val SIGN_OFF_BLOCK = Regex(
        """(?i)^(?:(?:Best|Kind|Warm)\s+regards|Regards|Sincerely|Yours\s+(?:sincerely|faithfully)|Best\s+wishes|Thanks?|Thank\s+you)[,，]?$"""
    )

    // I-5 第二条：收尾客套块（整块匹配，只删结尾连续）。
    private val CLOSING_COURTESY_BLOCK = Regex(
        """(?i)^Please let us know if you have any (?:further\s+|other\s+)?questions?[.!?。！？]?[,，]?$"""
    )

    // I-5 第三条：开场致谢整句，须同时含致谢动词与来信名词；只作用于剩余首块的首句。
    private val OPENING_THANK_YOU_SENTENCE = Regex(
        """(?i)^(?=.*\b(?:thanks?|thanked|thanking|appreciates?|appreciated|grateful)\b)""" +
            """(?=.*\b(?:emails?|messages?|notes?|repl(?:y|ies)|enquir(?:y|ies)|inquir(?:y|ies)|getting in touch|reaching out|writing)\b)""" +
            """.*[.!?。！？]\s*$"""
    )

    fun strip(text: String): FrameStripResult {
        if (text.isBlank()) {
            return FrameStripResult(text, stripped = false, skipped = false)
        }
        // I-3：按 blank-line 分隔切块，保留每块内部原始字符；空块只可能出现在
        // 首尾接缝（分隔符被整段消费），先剔除以便头部/尾部连续块判定不受干扰。
        val blocks = text.split(BLOCK_SEPARATOR).filter { it.isNotBlank() }
        var head = 0
        while (head < blocks.size && SALUTATION_BLOCK.matches(blocks[head].trim())) {
            head++
        }
        var tail = blocks.size - 1
        while (tail >= head && (
                SIGN_OFF_BLOCK.matches(blocks[tail].trim()) ||
                    CLOSING_COURTESY_BLOCK.matches(blocks[tail].trim())
                )
        ) {
            tail--
        }
        var deleted = head > 0 || tail < blocks.size - 1
        val kept = blocks.subList(head, tail + 1).toMutableList()
        if (kept.isNotEmpty()) {
            val first = kept[0]
            val (newFirst, removedSentence) = stripOpeningThankYou(first)
            if (removedSentence) {
                deleted = true
                if (newFirst.isBlank()) {
                    kept.removeAt(0)
                } else {
                    kept[0] = newFirst
                }
            }
        }
        if (!deleted) {
            // I-3：无删除时逐字返回原文。
            return FrameStripResult(text, stripped = false, skipped = false)
        }
        val rebuilt = kept.joinToString("\n\n").trim()
        if (rebuilt.isBlank()) {
            // I-2：剥离不得清空答案 —— 原样返回并标记 skipped。
            return FrameStripResult(text, stripped = false, skipped = true)
        }
        return FrameStripResult(rebuilt, stripped = true, skipped = false)
    }

    /** 在首块句首至多删一句开场致谢；返回 (剩余文本, 是否发生了删除)。 */
    private fun stripOpeningThankYou(block: String): Pair<String, Boolean> {
        val boundary = SENTENCE_BOUNDARY.find(block)
        // I-4：句尾偏移为闭区间端点语义 —— head 含句号（range.first），
        // rest 从分隔符之后（range.last + 1）开始，不残留孤立句点。
        // 无句内边界时整块即一句（如整块就是 "Thank you for your message."）。
        val head = (if (boundary == null) block else block.substring(0, boundary.range.first + 1)).trimEnd()
        if (!OPENING_THANK_YOU_SENTENCE.matches(head)) {
            return block to false
        }
        if (boundary == null) {
            return "" to true
        }
        val rest = block.substring(boundary.range.last + 1)
        return rest to true
    }
}
