package com.weibo.talentintroduction.qa.service

import com.weibo.talentintroduction.mail.service.MailVariableService

object QaFactBodyPolicy {
    private const val MAX_LENGTH = 4000

    private val HTML_DOCUMENT_TAG = Regex("""<(html|body|head|title|meta|style)\b""", RegexOption.IGNORE_CASE)
    private val OPENING_SALUTATION = Regex("""^(Dear|Hi|Hello)\b""", RegexOption.IGNORE_CASE)
    private val THANK_YOU_LINE = Regex("""Thank you for your email""", RegexOption.IGNORE_CASE)
    private val CLOSING_SIGNATURE = Regex(
        """(Best regards|Kind regards|Sincerely)[,，]?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val FURTHER_QUESTIONS = Regex(
        """Please let us know if you have any further questions""",
        RegexOption.IGNORE_CASE
    )

    fun validate(answerBody: String, mailVariableService: MailVariableService) {
        val trimmed = answerBody.trim()
        require(trimmed.isNotBlank()) { "answerBody is required" }
        require(trimmed.length <= MAX_LENGTH) { "answerBody must be at most $MAX_LENGTH characters" }
        mailVariableService.requireValidPlaceholders(trimmed)
        require(!HTML_DOCUMENT_TAG.containsMatchIn(trimmed)) {
            "answerBody must not contain HTML document tags"
        }
        require(!OPENING_SALUTATION.containsMatchIn(trimmed)) {
            "answerBody must not start with a salutation (Dear/Hi/Hello)"
        }
        require(!THANK_YOU_LINE.containsMatchIn(trimmed)) {
            "answerBody must not contain email acknowledgment phrases"
        }
        require(!CLOSING_SIGNATURE.containsMatchIn(trimmed)) {
            "answerBody must not end with an email signature"
        }
        require(!FURTHER_QUESTIONS.containsMatchIn(trimmed)) {
            "answerBody must not contain call-to-action closing phrases"
        }
    }
}
