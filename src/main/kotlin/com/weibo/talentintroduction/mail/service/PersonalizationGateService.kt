package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service

/**
 * Send-side hard gate for personalized mail (P1).
 *
 * Two independent responsibilities:
 * 1. [evaluate] decides whether the mail may be sent at all: a variable that the
 *    template makes required (I-1: `${key}` without a default) has no value for
 *    this recipient — `null`, `""` or whitespace-only all count as missing. The
 *    decision is based on the RAW template text (placeholders still present),
 *    never on rendered text where defaults have already been filled in.
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
     * Gates a send on [requiredKeys] restricted to the keys that actually occur in
     * the raw texts being sent, given [variables]. A key is missing when its value
     * is null, empty or whitespace-only (I-2: the gate is exact for the blocks and
     * variants actually selected — keys resolved through their default value never
     * reach this call because the template gate does not list them as required).
     * An empty [requiredKeys] disables the gate entirely (I-4).
     */
    fun evaluate(
        rawTexts: List<String>,
        variables: Map<String, String>,
        requiredKeys: List<String>
    ): PersonalizationGateResult {
        if (requiredKeys.isEmpty()) {
            return PersonalizationGateResult(blocked = false, missingKeys = emptyList())
        }
        val keysInText = linkedSetOf<String>()
        rawTexts.forEach { text ->
            keysInText.addAll(mailPlaceholderService.placeholderKeysIn(text))
        }
        val missing = requiredKeys.filter { key ->
            key in keysInText && variables[key].isNullOrBlank()
        }
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
