package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

/**
 * Send-side hard gate for personalized mail (P1).
 *
 * Two independent responsibilities:
 * 1. [evaluate] decides whether the mail may be sent at all: a required variable
 *    that fell back to its default value means the expert's data is incomplete
 *    (I-3). The decision is based on the RAW template text (placeholders still
 *    present), never on rendered text where fallbacks have already been filled in.
 * 2. [requireNoPlaceholderResidue] is the last line of defense right before SMTP:
 *    any `${...}` left in the final subject/body aborts the send (I-2). This is
 *    deliberately NOT `requireValidPlaceholders`, which only checks key
 *    whitelist/defaults and cannot detect unfilled values.
 */
class PersonalizationGateResult(
    val blocked: Boolean,
    val missingKeys: List<String>
)

class PlaceholderResidueException(message: String) : RuntimeException(message)

class PersonalizationGateException(
    val missingKeys: List<String>
) : RuntimeException(
    "Personalization gate blocked send: required variables fell back to defaults: " +
        missingKeys.joinToString(", ")
)

@Service
class PersonalizationGateService(
    private val mailPlaceholderService: MailPlaceholderService = MailPlaceholderService()
) {
    /**
     * Gates a send on the intersection of (keys that actually took their fallback
     * in [rawTexts] given [variables]) and [requiredKeys]. An empty [requiredKeys]
     * disables the gate entirely (I-4).
     */
    fun evaluate(
        rawTexts: List<String>,
        variables: Map<String, String>,
        requiredKeys: List<String>
    ): PersonalizationGateResult {
        if (requiredKeys.isEmpty()) {
            return PersonalizationGateResult(blocked = false, missingKeys = emptyList())
        }
        val fallbackKeys = linkedSetOf<String>()
        rawTexts.forEach { text ->
            fallbackKeys.addAll(mailPlaceholderService.detectFallbackKeys(text, variables))
        }
        val missing = requiredKeys.filter { it in fallbackKeys }
        return PersonalizationGateResult(blocked = missing.isNotEmpty(), missingKeys = missing)
    }

    /**
     * Rejects the mail when any final rendered text still contains a `${...}`
     * token. Throws [PlaceholderResidueException] naming the first residue.
     * Cannot be disabled by configuration.
     */
    fun requireNoPlaceholderResidue(vararg renderedTexts: String?) {
        renderedTexts.forEach { text ->
            if (text == null) {
                return@forEach
            }
            val residue = PLACEHOLDER_RESIDUE_REGEX.find(text)
            if (residue != null) {
                throw PlaceholderResidueException(
                    "Unresolved placeholder residue in outgoing mail: ${residue.value}"
                )
            }
        }
    }

    companion object {
        private val PLACEHOLDER_RESIDUE_REGEX = Regex("""\$\{[^}]*\}""")
    }
}
