package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service
import java.net.URI

@Service
class MailContentService {
    fun plainTextToHtml(plain: String, linkedUrls: Collection<String>): String {
        if (plain.isBlank()) return ""
        val targets = linkedUrls.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        return plain.split(Regex("\\n\\s*\\n"))
            .map { paragraph ->
                var inner = escapeHtml(paragraph.trim()).replace("\n", "<br>")
                targets.forEach { url ->
                    val escapedUrl = escapeHtml(url)
                    inner = inner.replace(escapedUrl, "<a href=\"$escapedUrl\">$escapedUrl</a>")
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

    /**
     * Remove only this application's open-tracking img tags from quoted reply HTML.
     * Locate the original tag spans instead of reserializing HTML so unrelated content stays byte-for-byte intact.
     */
    fun stripOpenTrackingImages(html: String): String {
        var searchFrom = 0
        var keptThrough = 0
        var cleaned: StringBuilder? = null
        while (true) {
            val start = html.indexOf('<', searchFrom)
            if (start < 0) break
            if (html.startsWith("<!--", start)) {
                val commentEnd = html.indexOf("-->", start + 4)
                if (commentEnd < 0) break
                searchFrom = commentEnd + 3
                continue
            }
            val image = html.regionMatches(start, "<img", 0, 4, ignoreCase = true) &&
                start + 4 < html.length &&
                (html[start + 4].isWhitespace() || html[start + 4] == '/' || html[start + 4] == '>')
            if (!image && (start + 1 >= html.length ||
                    !(html[start + 1].isLetter() ||
                        (html[start + 1] == '/' && start + 2 < html.length && html[start + 2].isLetter())))) {
                searchFrom = start + 1
                continue
            }
            var quote = '\u0000'
            var end = if (image) start + 4 else start + 1
            while (end < html.length) {
                val ch = html[end]
                if (quote != '\u0000') {
                    if (ch == quote) quote = '\u0000'
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (ch == '>') {
                    break
                }
                end++
            }
            if (end == html.length) break
            searchFrom = end + 1
            if (!image || !isOpenTrackingImage(html, start + 4, end)) continue
            val result = cleaned ?: StringBuilder(html.length).also { cleaned = it }
            result.append(html, keptThrough, start)
            keptThrough = end + 1
        }
        return cleaned?.append(html, keptThrough, html.length)?.toString() ?: html
    }

    private fun isOpenTrackingImage(html: String, attributesStart: Int, tagEnd: Int): Boolean {
        var pos = attributesStart
        while (pos < tagEnd) {
            while (pos < tagEnd && (html[pos].isWhitespace() || html[pos] == '/')) pos++
            val nameStart = pos
            while (pos < tagEnd && !html[pos].isWhitespace() && html[pos] != '=' && html[pos] != '/') pos++
            if (pos == nameStart) {
                pos++
                continue
            }
            val nameEnd = pos
            while (pos < tagEnd && html[pos].isWhitespace()) pos++
            if (pos == tagEnd || html[pos] != '=') continue
            pos++
            while (pos < tagEnd && html[pos].isWhitespace()) pos++
            val valueStart: Int
            val valueEnd: Int
            if (pos < tagEnd && (html[pos] == '"' || html[pos] == '\'')) {
                val quote = html[pos++]
                valueStart = pos
                while (pos < tagEnd && html[pos] != quote) pos++
                valueEnd = pos
                if (pos < tagEnd) pos++
            } else {
                valueStart = pos
                while (pos < tagEnd && !html[pos].isWhitespace()) pos++
                valueEnd = if (pos == tagEnd && pos > valueStart && html[pos - 1] == '/') pos - 1 else pos
            }
            if (nameEnd - nameStart == 23 &&
                html.regionMatches(nameStart, "data-mail-open-tracking", 0, 23, ignoreCase = true) &&
                valueEnd - valueStart == 1 && html[valueStart] == '1') return true
            if (nameEnd - nameStart == 3 && html.regionMatches(nameStart, "src", 0, 3, ignoreCase = true)) {
                val src = unescapeHtmlEntities(html.substring(valueStart, valueEnd))
                val path = try { URI(src).path } catch (_: IllegalArgumentException) { null }
                if (path != null && OPEN_TRACKING_PATH.containsMatchIn(path)) return true
            }
        }
        return false
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    companion object {
        /** 两个及以上 `<br>`（标签间允许空白）——规范为两个 `<br>`。 */
        private val CONSECUTIVE_BR = Regex("(?i)<br\\s*/?>(?:\\s*<br\\s*/?>)+")

        /** 连续空 `<p>`/`<div>` 块折叠为首个块，以保留一个空行。 */
        private val EMPTY_BLOCK_RUN = Regex(
            "(?is)(<(?:p|div)(?:\\s[^>]*)?>(?:\\s|&nbsp;|&amp;nbsp;|<br\\s*/?>)*</(?:p|div)>)(?:\\s*<(?:p|div)(?:\\s[^>]*)?>(?:\\s|&nbsp;|&amp;nbsp;|<br\\s*/?>)*</(?:p|div)>)+"
        )

        /** 折叠不动点上限：每轮都严格缩短字符串，正常 2 轮内收敛，上限只作防御。 */
        private val OPEN_TRACKING_PATH = Regex("(?:^|/)t/mail-open/[A-Za-z0-9_-]{43}\\.gif$")
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
