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
     * 人工富文本专用：CRLF/CR 统一为 LF；普通换行保留，连续空白行最多保留一行。
     * 非空行内容逐字保留（不 trim、不合并行内空白）。只服务人工富文本发送（`executeManualRichSend`），
     * 不改变 `plainTextToHtml` 与非人工邮件既有的 `\n\n` 段落约定（I-5）。
     */
    fun normalizeManualTextLineBreaks(text: String): String {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val result = StringBuilder()
        var pendingBlankLine = false
        normalized.split("\n").forEach { line ->
            if (line.all { it == ' ' || it == '\t' }) {
                if (result.isNotEmpty()) pendingBlankLine = true
            } else {
                if (result.isNotEmpty()) result.append(if (pendingBlankLine) "\n\n" else "\n")
                result.append(line)
                pendingBlankLine = false
            }
        }
        return result.toString()
    }

    /**
     * 人工富文本专用：连续 `<br>` 最多保留两个，连续空 `<p>`/`<div>` 块最多保留一个，
     * 从而在邮件中保留一行空白。其他标签、属性与文本顺序逐字保留。
     */
    fun normalizeManualRichHtmlLineBreaks(html: String): String {
        var current = html
        var passes = 0
        while (passes++ < MAX_MANUAL_HTML_PASSES) {
            val next = EMPTY_BLOCK_RUN.replace(CONSECUTIVE_BR.replace(current, "<br><br>"), "$1")
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

        /** 两个及以上 `<br>`（标签间允许空白）——规范为两个 `<br>`。 */
        private val CONSECUTIVE_BR = Regex("(?i)<br\\s*/?>(?:\\s*<br\\s*/?>)+")

        /** 连续空 `<p>`/`<div>` 块折叠为首个块，以保留一个空行。 */
        private val EMPTY_BLOCK_RUN = Regex(
            "(?is)(<(?:p|div)(?:\\s[^>]*)?>(?:\\s|&nbsp;|&amp;nbsp;|<br\\s*/?>)*</(?:p|div)>)(?:\\s*<(?:p|div)(?:\\s[^>]*)?>(?:\\s|&nbsp;|&amp;nbsp;|<br\\s*/?>)*</(?:p|div)>)+"
        )

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
