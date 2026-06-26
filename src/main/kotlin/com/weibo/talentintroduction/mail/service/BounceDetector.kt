package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

@Service
class BounceDetector {
    fun detect(from: String?, subject: String?, body: String?): BounceSignal? {
        val fromLower = from?.trim()?.lowercase().orEmpty()
        val subjectLower = subject?.trim()?.lowercase().orEmpty()
        val bodyText = body.orEmpty()
        val bodyLower = bodyText.lowercase()

        val fromMatch = fromLower.contains("mailer-daemon") || fromLower.contains("postmaster")
        val subjectMatch = BOUNCE_SUBJECT_KEYWORDS.any { subjectLower.contains(it) }
        val bodyMatch = BOUNCE_BODY_PATTERNS.any { it.containsMatchIn(bodyLower) }
        val dsnInBody = DSN_STATUS_PATTERN.find(bodyLower)?.value

        if (!fromMatch && !subjectMatch && !bodyMatch && dsnInBody == null) {
            return null
        }

        val dsnStatus = dsnInBody ?: extractDsnFromLines(bodyText)
        val failedRecipient = extractFailedRecipient(bodyText)
        val originalMessageId = extractOriginalMessageIdFromBody(bodyText)
        val bounceType = classifyBounceType(dsnStatus, bodyLower, fromMatch || subjectMatch)
        val reason = subject?.take(500) ?: bodyText.lineSequence().firstOrNull()?.take(500)

        return BounceSignal(
            bounceType = bounceType,
            dsnStatus = dsnStatus,
            failedRecipient = failedRecipient,
            reason = reason,
            originalMessageId = originalMessageId
        )
    }

    fun isBounce(from: String?, subject: String?, contentType: String?): Boolean {
        if (contentType?.contains("report-type=delivery-status", ignoreCase = true) == true) return true
        return detect(from, subject, null) != null
    }

    fun parseBounceDetails(message: Message): BounceSignal? {
        val from = message.from
            ?.filterIsInstance<InternetAddress>()
            ?.firstOrNull()
            ?.address
        val subject = message.subject
        val body = extractTextContent(message)
        val mimeDsn = extractDsnStatus(message)
        val mimeOriginalMessageId = extractOriginalMessageId(message)
        val heuristic = detect(from, subject, body)

        if (mimeDsn == null && heuristic == null) {
            return null
        }

        val dsnStatus = mimeDsn ?: heuristic?.dsnStatus
        val failedRecipient = heuristic?.failedRecipient ?: extractFailedRecipient(body)
        val originalMessageId = mimeOriginalMessageId ?: heuristic?.originalMessageId
        val bodyLower = body.lowercase()
        val bounceType = classifyBounceType(
            dsnStatus,
            bodyLower,
            heuristic != null || mimeDsn != null
        )
        val reason = heuristic?.reason ?: subject?.take(500) ?: body.lineSequence().firstOrNull()?.take(500)

        return BounceSignal(
            bounceType = bounceType,
            dsnStatus = dsnStatus,
            failedRecipient = failedRecipient,
            reason = reason,
            originalMessageId = originalMessageId
        )
    }

    private fun classifyBounceType(dsnStatus: String?, bodyLower: String, heuristicBounce: Boolean): String =
        when {
            dsnStatus?.startsWith("5") == true -> "HARD"
            dsnStatus?.startsWith("4") == true -> "SOFT"
            HARD_SMTP_CODE_PATTERN.containsMatchIn(bodyLower) -> "HARD"
            heuristicBounce -> "SOFT"
            else -> "SOFT"
        }

    private fun extractDsnFromLines(body: String): String? =
        body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Status:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.let { STATUS_PATTERN.find(it)?.value }

    private fun extractFailedRecipient(body: String): String? {
        FAILED_RECIPIENT_PATTERNS.forEach { pattern ->
            pattern.find(body)?.groupValues?.getOrNull(1)?.let { candidate ->
                val normalized = normalizeEmailCandidate(candidate)
                if (normalized.contains("@")) return normalized
            }
        }

        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val failureLineIdx = lines.indexOfFirst { line ->
            FAILURE_LINE_MARKERS.any { marker -> line.contains(marker, ignoreCase = true) }
        }
        if (failureLineIdx >= 0) {
            for (i in (failureLineIdx + 1) until minOf(lines.size, failureLineIdx + 5)) {
                STANDALONE_EMAIL.find(lines[i])?.groupValues?.getOrNull(1)?.let { candidate ->
                    return normalizeEmailCandidate(candidate)
                }
            }
        }

        lines.forEach { line ->
            STANDALONE_EMAIL.find(line)?.groupValues?.getOrNull(1)?.let { candidate ->
                val normalized = normalizeEmailCandidate(candidate)
                if (normalized.contains("@") && !normalized.contains("mailer-daemon")) {
                    return normalized
                }
            }
        }
        return null
    }

    private fun extractOriginalMessageIdFromBody(body: String): String? {
        body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Original-Message-ID:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.let { return normalizeMessageId(it) }
        MESSAGE_ID_HEADER_PATTERN.find(body)?.groupValues?.getOrNull(1)?.let {
            return normalizeMessageId(it)
        }
        return null
    }

    private fun normalizeEmailCandidate(raw: String): String =
        raw.trim().removePrefix("<").removeSuffix(">").trim().lowercase()

    private fun extractTextContent(part: Part): String {
        return try {
            when {
                part.isMimeType("text/plain") -> part.content as? String ?: ""
                part.isMimeType("text/html") -> stripHtml(part.content as? String ?: "")
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as Multipart
                    buildString {
                        for (i in 0 until multipart.count) {
                            append(extractTextContent(multipart.getBodyPart(i)))
                            if (isNotEmpty()) append('\n')
                        }
                    }
                }
                else -> part.content?.toString().orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

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

    fun normalizeMessageId(messageId: String): String =
        messageId.trim().removePrefix("<").removeSuffix(">")

    companion object {
        private val STATUS_PATTERN = Regex("""\d\.\d\.\d""")
        private val DSN_STATUS_PATTERN = Regex("""\b5\d\d\s+5\.\d\.\d\b""")
        private val HARD_SMTP_CODE_PATTERN = Regex("""\b5\d\d\s+5\.\d\.\d\b""")
        private val MESSAGE_ID_HEADER_PATTERN = Regex("""Message-ID:\s*<?([^>\s]+@[^>\s]+)>?""", RegexOption.IGNORE_CASE)

        private val BOUNCE_SUBJECT_KEYWORDS = listOf(
            "undelivered",
            "delivery status",
            "returned mail",
            "mail delivery failed",
            "undeliverable",
            "delivery has failed",
            "recipients or groups",
            "failure notice",
            "mail system error",
            "退信",
            "被退回",
            "无法发送",
            "邮件被退回"
        )

        private val BOUNCE_BODY_PATTERNS = listOf(
            Regex("""\b5\d\d\s+5\.\d\.\d\b"""),
            Regex("""bounced address""", RegexOption.IGNORE_CASE),
            Regex("""could not resolve""", RegexOption.IGNORE_CASE),
            Regex("""poor reputation""", RegexOption.IGNORE_CASE),
            Regex("""access to this mail system has been rejected""", RegexOption.IGNORE_CASE)
        )

        private val FAILED_RECIPIENT_PATTERNS = listOf(
            Regex("""无法发送到\s*<?([^\s<>]+@[^\s<>]+)>?"""),
            Regex("""bounced address[:\s]+<?([^\s<>]+@[^\s<>]+)>?""", RegexOption.IGNORE_CASE)
        )

        private val FAILURE_LINE_MARKERS = listOf(
            "delivery has failed",
            "failed to these recipients or groups",
            "failure notice",
            "无法发送",
            "被退回",
            "退信"
        )

        private val STANDALONE_EMAIL = Regex("""^<?([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})>?$""")
    }
}

data class BounceSignal(
    val bounceType: String,
    val dsnStatus: String?,
    val failedRecipient: String?,
    val reason: String?,
    val originalMessageId: String?
)

/** @deprecated use [BounceSignal] via [BounceDetector.parseBounceDetails] */
data class BounceDetails(
    val originalMessageId: String?,
    val dsnStatus: String?,
    val bounceType: String,
    val reason: String?
)
