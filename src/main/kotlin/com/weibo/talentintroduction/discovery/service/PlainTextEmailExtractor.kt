package com.weibo.talentintroduction.discovery.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlainTextEmailExtractor {

    private val log = LoggerFactory.getLogger(PlainTextEmailExtractor::class.java)

    private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val atObfuscation = Regex("\\(at\\)|\\[at\\]|\\{at\\}|\\s+at\\s+")
    private val dotObfuscation = Regex("\\(dot\\)|\\[dot\\]|\\{dot\\}|\\s+dot\\s+")
    private val blacklistDomains = setOf("example.com", "example.org", "domain.com")

    private val operationalPrefixes = setOf(
        "support", "info", "journals", "permissions", "editorial", "office", "help", "admin"
    )

    fun extract(text: String, blacklistPrefixes: List<String> = emptyList()): List<String> {
        if (text.isBlank()) return emptyList()

        val cleaned = expandBraceEmails(cleanObfuscation(text))
        val allPrefixes = operationalPrefixes + blacklistPrefixes

        return emailRegex.findAll(cleaned)
            .map { it.value.lowercase() }
            .filter { email ->
                val localPart = email.substringBefore("@").lowercase()
                val domain = email.substringAfter("@").lowercase()
                domain !in blacklistDomains &&
                    allPrefixes.none { localPart.startsWith(it) }
            }
            .distinct()
            .toList()
    }

    private fun expandBraceEmails(text: String): String {
        val bracePattern = Regex("\\{([a-zA-Z,]+)\\}@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
        return bracePattern.replace(text) { match ->
            val variants = match.groupValues[1].split(",").map { it.trim() }
            val domain = match.groupValues[2]
            variants.joinToString(" ") { "$it@$domain" }
        }
    }

    private fun cleanObfuscation(text: String): String {
        return text
            .replace(atObfuscation, "@")
            .replace(dotObfuscation, ".")
            .replace(Regex("\\b(dot)\\s+(com|org|edu|net|gov|io|uk|de|fr|cn|jp)\\b")) { match ->
                ".${match.groupValues[2]}"
            }
    }
}
