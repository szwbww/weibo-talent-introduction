package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

@Service
class MailBodyCleaner {
    fun clean(body: String?): String {
        if (body.isNullOrBlank()) {
            return ""
        }

        val lines = body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()

        val latestLines = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isQuotedHistoryStart()) {
                break
            }
            if (trimmed.startsWith(">")) {
                continue
            }
            latestLines += line
        }

        return latestLines
            .joinToString("\n")
            .stripDisclaimer()
            .stripSignature()
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.isQuotedHistoryStart(): Boolean =
        quotedHistoryPatterns.any { it.containsMatchIn(this) }

    private fun String.stripDisclaimer(): String {
        val match = disclaimerPatterns
            .mapNotNull { it.find(this)?.range?.first }
            .minOrNull()
        return if (match != null && match > 0) {
            substring(0, match)
        } else {
            this
        }
    }

    private fun String.stripSignature(): String {
        val lines = lines()
        if (lines.size <= 2) {
            return this
        }
        val signatureIndex = lines.indexOfFirst { line ->
            signaturePatterns.any { it.matches(line.trim()) }
        }
        return if (signatureIndex > 0) {
            lines.take(signatureIndex).joinToString("\n")
        } else {
            this
        }
    }

    companion object {
        private val quotedHistoryPatterns = listOf(
            Regex("^from\\s*:", RegexOption.IGNORE_CASE),
            Regex("^sent\\s*:", RegexOption.IGNORE_CASE),
            Regex("^to\\s*:", RegexOption.IGNORE_CASE),
            Regex("^subject\\s*:", RegexOption.IGNORE_CASE),
            Regex("^on\\s+.+wrote\\s*:", RegexOption.IGNORE_CASE),
            Regex("^-{2,}\\s*original message\\s*-{2,}$", RegexOption.IGNORE_CASE),
            Regex("^begin forwarded message\\s*:", RegexOption.IGNORE_CASE)
        )

        private val disclaimerPatterns = listOf(
            Regex("\\n\\s*this message \\(including any attachments\\)", RegexOption.IGNORE_CASE),
            Regex("\\n\\s*the information contained in this", RegexOption.IGNORE_CASE),
            Regex("\\n\\s*disclaimer\\s*:", RegexOption.IGNORE_CASE),
            Regex("\\n\\s*confidentiality notice\\s*:", RegexOption.IGNORE_CASE)
        )

        private val signaturePatterns = listOf(
            Regex("^(best\\s+regards|kind\\s+regards|regards|sincerely|thanks|thank\\s+you),?$", RegexOption.IGNORE_CASE)
        )
    }
}
