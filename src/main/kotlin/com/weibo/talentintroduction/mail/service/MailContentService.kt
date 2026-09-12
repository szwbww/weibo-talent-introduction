package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class MailContentService {
    fun plainTextToHtml(plain: String, linkedUrls: Collection<String>): String {
        if (plain.isBlank()) return ""
        val targets = linkedUrls.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        return plain.split(Regex("\\n\\s*\\n"))
            .map { paragraph ->
                var inner = escapeHtml(paragraph.trim()).replace("\n", "<br>")
                targets.forEach { url ->
                    inner = inner.replace(url, "<a href=\"$url\">$UNSUBSCRIBE_ANCHOR_TEXT</a>")
                }
                "<p>$inner</p>"
            }
            .joinToString("")
    }

    fun plainTextToHtml(plain: String): String = plainTextToHtml(plain, emptyList())

    fun htmlToPlainText(html: String): String =
        html.replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .let { unescapeHtmlEntities(it) }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    /**
     * 人工富文本专用（I-1）：CRLF/CR 统一为 LF，并丢弃所有空白行（空行与仅由空格/Tab
     * 组成的行）——非空行之间恰好一个 `\n`，结果不含 `\r`、不含两个相邻换行。非空行内容
     * 逐字保留（不 trim、不合并行内空白）。只服务人工富文本发送（`executeManualRichSend`），
     * 不改变 `plainTextToHtml` 与非人工邮件既有的 `\n\n` 段落约定（I-5）。
     */
    fun normalizeManualTextLineBreaks(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        // 空白行（仅空格/Tab，含空行）整行丢弃：非空行之间只剩一个 \n，结果既无 \r 也无相邻换行。
        return normalized.split("\n")
            .filterNot { line -> line.all { it == ' ' || it == '\t' } }
            .joinToString("\n")
    }

    /**
     * 人工富文本专用（I-2）：连续 `<br>`（标签间允许空白）最多保留一个；仅由空白、
     * `&nbsp;`、`<br>` 组成的空 `<p>`/`<div>` 块整体移除；其他标签、属性与文本顺序逐字保留。
     * 移除空块后新暴露的连续 `<br>`/空块在函数内反复折叠到不动点，输出与调用历史无关。
     */
    fun normalizeManualRichHtmlLineBreaks(html: String): String {
        var current = html
        var passes = 0
        while (passes++ < MAX_MANUAL_HTML_PASSES) {
            val next = EMPTY_BLOCK.replace(CONSECUTIVE_BR.replace(current, "<br>"), "")
            if (next == current) return current
            current = next
        }
        return current
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    companion object {
        const val UNSUBSCRIBE_ANCHOR_TEXT = "Unsubscribe"

        /** `<br>` 后跟一个以上（空白 + `<br>`）——折叠为单个 `<br>`。 */
        private val CONSECUTIVE_BR = Regex("(?i)<br\\s*/?>(?:\\s*<br\\s*/?>)+")

        /** 仅由空白/`&nbsp;`/`<br>` 组成的空 `<p>`/`<div>` 块。 */
        private val EMPTY_BLOCK = Regex("(?i)<(p|div)(\\s[^>]*)?>(?:\\s|&nbsp;|&amp;nbsp;|<br\\s*/?>)*</\\1>")

        /** 折叠不动点上限：每轮都严格缩短字符串，正常 2 轮内收敛，上限只作防御。 */
        private const val MAX_MANUAL_HTML_PASSES = 8
    }

    private fun unescapeHtmlEntities(text: String): String {
        val namedEntities = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " "
        )
        return text.replace(Regex("&#(\\d+);|&#x([0-9a-fA-F]+);|&([a-zA-Z]+);")) { match ->
            val decimal = match.groupValues[1]
            val hex = match.groupValues[2]
            val named = match.groupValues[3]
            when {
                decimal.isNotEmpty() -> decimal.toIntOrNull()?.toChar()?.toString() ?: match.value
                hex.isNotEmpty() -> hex.toIntOrNull(16)?.toChar()?.toString() ?: match.value
                else -> namedEntities[named] ?: match.value
            }
        }
    }
}
