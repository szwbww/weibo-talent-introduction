package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class MailContentService {
    fun htmlToPlainText(html: String): String =
        html.replace(Regex("(?is)<(script|style).*?>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .let { unescapeHtmlEntities(it) }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

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
