package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.MimeMessage

@Service
class BounceDetector {
    fun isBounce(from: String?, subject: String?, contentType: String?): Boolean {
        val fromLower = from?.lowercase() ?: return false
        if (fromLower.contains("mailer-daemon") || fromLower.contains("postmaster")) return true
        val subjectLower = subject?.lowercase() ?: ""
        if (subjectLower.contains("undelivered") ||
            subjectLower.contains("delivery status") ||
            subjectLower.contains("returned mail") ||
            subjectLower.contains("mail delivery failed") ||
            subjectLower.contains("undeliverable")
        ) {
            return true
        }
        if (contentType?.contains("report-type=delivery-status", ignoreCase = true) == true) return true
        return false
    }

    fun parseBounceDetails(message: Message): BounceDetails {
        val dsnStatus = extractDsnStatus(message)
        val originalMessageId = extractOriginalMessageId(message)
        val bounceType = when {
            dsnStatus?.startsWith("5") == true -> "HARD"
            dsnStatus?.startsWith("4") == true -> "SOFT"
            else -> inferBounceTypeFromSubject(message.subject)
        }
        return BounceDetails(
            originalMessageId = originalMessageId,
            dsnStatus = dsnStatus,
            bounceType = bounceType,
            reason = message.subject?.take(500)
        )
    }

    private fun extractDsnStatus(message: Message): String? {
        val dsnBody = findDeliveryStatusBody(message) ?: return null
        val statusLine = dsnBody.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Status:", ignoreCase = true) }
            ?: return null
        val statusValue = statusLine.substringAfter(":").trim()
        return STATUS_PATTERN.find(statusValue)?.value
    }

    private fun extractOriginalMessageId(message: Message): String? {
        findEmbeddedRfc822Message(message)?.getHeader("Message-ID")?.firstOrNull()?.let { return normalizeMessageId(it) }
        findDeliveryStatusBody(message)?.let { body ->
            body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("Original-Message-ID:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.let { return normalizeMessageId(it) }
        }
        message.getHeader("In-Reply-To")?.firstOrNull()?.let { return normalizeMessageId(it) }
        return null
    }

    private fun findDeliveryStatusBody(part: Part): String? {
        if (part.isMimeType("message/delivery-status")) {
            return part.content as? String
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            for (i in 0 until multipart.count) {
                findDeliveryStatusBody(multipart.getBodyPart(i))?.let { return it }
            }
        }
        return null
    }

    private fun findEmbeddedRfc822Message(part: Part): MimeMessage? {
        if (part.isMimeType("message/rfc822")) {
            val content = part.content
            return when (content) {
                is MimeMessage -> content
                is ByteArrayInputStream -> MimeMessage(null, content)
                else -> null
            }
        }
        if (part.isMimeType("multipart/*")) {
            val multipart = part.content as Multipart
            for (i in 0 until multipart.count) {
                findEmbeddedRfc822Message(multipart.getBodyPart(i))?.let { return it }
            }
        }
        return null
    }

    private fun normalizeMessageId(messageId: String): String =
        messageId.trim().removePrefix("<").removeSuffix(">")

    private fun inferBounceTypeFromSubject(subject: String?): String = "SOFT"

    companion object {
        private val STATUS_PATTERN = Regex("""\d\.\d\.\d""")
    }
}

data class BounceDetails(
    val originalMessageId: String?,
    val dsnStatus: String?,
    val bounceType: String,
    val reason: String?
)
