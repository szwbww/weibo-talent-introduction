package com.weibo.talentintroduction.discovery.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class PlainTextEmailExtractor {

    private val log = LoggerFactory.getLogger(PlainTextEmailExtractor::class.java)

    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val bracePattern = Regex("\\{([^{}\\r\\n]+)}\\s*@\\s*([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
    private val validLocalPart = Regex("[A-Za-z0-9._%+-]+")
    private val wrappedAddress = Regex("([A-Za-z0-9._%+-]+@)\\r?\\n([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
    // PDF text extraction can split a local part before '@'. The remaining suffix is syntactically
    // valid, but it is not the address printed in the source (e.g. "Email: yin-\nqiu001@...").
    private val splitLocalPartBeforeMailbox = Regex(
        "(?i)(?:e-?mail|email address)\\s*:\\s*[A-Za-z0-9._%+-]+(?:\\s+|[-_]\\s*)$"
    )
    private val atObfuscation = Regex("\\(at\\)|\\[at\\]|\\{at\\}|\\s+at\\s+")
    private val dotObfuscation = Regex("\\(dot\\)|\\[dot\\]|\\{dot\\}|\\s+dot\\s+")
    private val blacklistDomains = setOf("example.com", "example.org", "domain.com")

    private val operationalPrefixes = setOf(
        "support", "info", "journals", "permissions", "editorial", "office", "help", "admin"
    )

    fun extract(text: String, blacklistPrefixes: List<String> = emptyList()): List<String> {
        if (text.isBlank()) return emptyList()

        val cleaned = expandBraceEmails(normalizeContactText(text))
        val allPrefixes = operationalPrefixes + blacklistPrefixes

        return emailRegex.findAll(cleaned)
            .filterNot { match ->
                val before = cleaned.substring(maxOf(0, match.range.first - 120), match.range.first)
                splitLocalPartBeforeMailbox.containsMatchIn(before)
            }
            .map { it.value.lowercase(Locale.ROOT) }
            .filter { email ->
                val localPart = email.substringBefore("@").lowercase(Locale.ROOT)
                val domain = email.substringAfter("@").lowercase(Locale.ROOT)
                domain !in blacklistDomains && allPrefixes.none { localPart.startsWith(it) }
            }
            .distinct()
            .toList()
    }

    internal fun normalizeContactText(text: String): String {
        val unobfuscated = cleanObfuscation(text)
        return wrappedAddress.replace(unobfuscated) { "${it.groupValues[1]}${it.groupValues[2]}" }
    }

    private fun expandBraceEmails(text: String): String = bracePattern.replace(text) { match ->
        val members = match.groupValues[1].split(',', ';').map(String::trim)
        if (members.isEmpty() || members.any { !validLocalPart.matches(it) }) match.value
        else members.joinToString(" ") { "$it@${match.groupValues[2]}" }
    }

    private fun cleanObfuscation(text: String): String = text
        .replace(atObfuscation, "@")
        .replace(dotObfuscation, ".")
        .replace(Regex("\\b(dot)\\s+(com|org|edu|net|gov|io|uk|de|fr|cn|jp)\\b")) { match ->
            ".${match.groupValues[2]}"
        }
}
