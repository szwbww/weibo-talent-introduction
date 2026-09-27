package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.sun.mail.smtp.SMTPAddressFailedException
import com.sun.mail.smtp.SMTPSendFailedException
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
import org.springframework.mail.MailSendException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.SendFailedException

internal object SmtpErrorClassifier {
    fun fromSendFailedException(e: SendFailedException, messageId: String?): DeliveredMail {
        val code = extractSmtpCode(e)
        return DeliveredMail(
            messageId = messageId,
            status = "FAILED",
            errorCategory = classifySmtpCode(code),
            smtpResponseCode = code,
            errorDetail = e.message?.take(500)
        )
    }

    fun fromAuthenticationFailedException(e: AuthenticationFailedException, messageId: String?): DeliveredMail =
        DeliveredMail(
            messageId = messageId,
            status = "FAILED",
            errorCategory = SmtpErrorCategory.INFRASTRUCTURE,
            errorDetail = "AUTH_FAILED:${e.message?.take(500)}"
        )

    fun fromMessagingException(e: MessagingException, messageId: String?): DeliveredMail {
        val code = extractSmtpCode(e)
        return DeliveredMail(
            messageId = messageId,
            status = "FAILED",
            errorCategory = classifySmtpCode(code),
            smtpResponseCode = code,
            errorDetail = e.message?.take(500)
        )
    }

    fun fromMailException(e: MailException, messageId: String?): DeliveredMail {
        if (e is MailAuthenticationException) {
            return DeliveredMail(
                messageId = messageId,
                status = "FAILED",
                errorCategory = SmtpErrorCategory.INFRASTRUCTURE,
                errorDetail = "AUTH_FAILED:${e.message?.take(500)}"
            )
        }

        // Spring stores per-message SMTP failures in failedMessages, often without
        // a cause. Read the exception itself instead of parsing its rendered text.
        val failure = if (e is MailSendException) e.failedMessages.values.firstOrNull() ?: e else e
        val nestedMessagingException = generateSequence<Throwable>(failure) { it.cause }
            .filterIsInstance<MessagingException>()
            .firstOrNull()
        if (nestedMessagingException != null) {
            return when (nestedMessagingException) {
                is SendFailedException -> fromSendFailedException(nestedMessagingException, messageId)
                is AuthenticationFailedException -> fromAuthenticationFailedException(nestedMessagingException, messageId)
                else -> fromMessagingException(nestedMessagingException, messageId)
            }
        }

        val code = extractSmtpCode(e.message)
        return DeliveredMail(
            messageId = messageId,
            status = "FAILED",
            errorCategory = classifySmtpCode(code),
            smtpResponseCode = code,
            errorDetail = e.message?.take(500)
        )
    }

    fun extractSmtpCode(e: MessagingException): Int? {
        // Prefer protocol fields; nested exceptions may hold the actual reply.
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = e
        while (current != null && seen.add(current)) {
            val code = when (current) {
                is SMTPSendFailedException -> current.returnCode
                is SMTPAddressFailedException -> current.returnCode
                else -> extractSmtpCode(current.message)
            }
            if (code != null) return code
            current = if (current is MessagingException) current.nextException ?: current.cause else current.cause
        }
        return null
    }

    private fun extractSmtpCode(message: String?): Int? {
        // Only a reply at the start of a line, never a port embedded in prose.
        val match = Regex("""(?m)^[ \t]*([245]\d{2})(?:[ -]|$)""").find(message ?: "")
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun classifySmtpCode(code: Int?): SmtpErrorCategory {
        if (code == null) return SmtpErrorCategory.TRANSIENT
        return when (code) {
            in 200..299 -> SmtpErrorCategory.SUCCESS
            in 400..499 -> SmtpErrorCategory.TRANSIENT
            in 500..599 -> SmtpErrorCategory.PERMANENT
            else -> SmtpErrorCategory.TRANSIENT
        }
    }
}
