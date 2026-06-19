package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
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
        val code = extractSmtpCode(e.message)
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

        val nestedMessagingException = generateSequence(e.cause) { it.cause }
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
        return extractSmtpCode(e.message)
    }

    private fun extractSmtpCode(message: String?): Int? {
        val match = Regex("""(?:^|\s)(\d{3})\b""").find(message ?: "")
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
