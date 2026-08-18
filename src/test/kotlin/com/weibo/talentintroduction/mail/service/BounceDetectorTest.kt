package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class BounceDetectorTest {
    private val detector = BounceDetector()

    @Test
    fun `mailer-daemon from is detected as bounce`() {
        assertTrue(detector.isBounce("mailer-daemon@example.com", "hello", null))
    }

    @Test
    fun `normal expert reply is not detected as bounce`() {
        assertFalse(detector.isBounce("expert@university.edu", "Re: Introduction", "text/plain"))
    }

    @Test
    fun `DSN content type is detected as bounce`() {
        assertTrue(
            detector.isBounce(
                "system@example.com",
                "notice",
                "multipart/report; report-type=delivery-status"
            )
        )
    }

    @Test
    fun `parseBounceDetails classifies 5_1_1 as HARD`() {
        val message = dsnBounce(status = "5.1.1", originalMessageId = "orig-1@example.com")
        val signal = detector.parseBounceDetails(message)
        assertNotNull(signal)
        assertEquals("HARD", signal!!.bounceType)
        assertEquals("5.1.1", signal.dsnStatus)
        assertEquals("orig-1@example.com", signal.originalMessageId)
    }

    @Test
    fun `parseBounceDetails classifies 4_2_2 as SOFT`() {
        val message = dsnBounce(status = "4.2.2")
        val signal = detector.parseBounceDetails(message)
        assertNotNull(signal)
        assertEquals("SOFT", signal!!.bounceType)
        assertEquals("4.2.2", signal.dsnStatus)
    }

    @Test
    fun `parseBounceDetails classifies 5_1_1 as HARD after MIME round trip`() {
        val message = roundTrip(dsnBounce(status = "5.1.1", originalMessageId = "orig-rt@example.com"))
        val signal = detector.parseBounceDetails(message)
        assertNotNull(signal)
        assertEquals("HARD", signal!!.bounceType)
        assertEquals("5.1.1", signal.dsnStatus)
        assertEquals("orig-rt@example.com", signal.originalMessageId)
    }

    @Test
    fun `parseBounceDetails classifies 4_2_2 as SOFT after MIME round trip`() {
        val message = roundTrip(dsnBounce(status = "4.2.2"))
        val signal = detector.parseBounceDetails(message)
        assertNotNull(signal)
        assertEquals("SOFT", signal!!.bounceType)
        assertEquals("4.2.2", signal.dsnStatus)
    }

    @Test
    fun `detect recognizes Chinese bounce subject`() {
        val signal = detector.detect(
            from = "postmaster@mail.example.com",
            subject = "邮件被退回：无法发送到 expert@university.edu",
            body = "554 5.4.4 无法发送到 expert@university.edu"
        )
        assertNotNull(signal)
        assertEquals("HARD", signal!!.bounceType)
        assertEquals("expert@university.edu", signal.failedRecipient)
    }

    @Test
    fun `detect recognizes Exchange Amazon NDR subject and body`() {
        val body = """
            Delivery has failed to these recipients or groups:
            expert@company.com
            554 5.4.4 Access to this mail system has been rejected due to poor reputation
        """.trimIndent()
        val signal = detector.detect(
            from = "MicrosoftExchange123@example.com",
            subject = "Delivery has failed to these recipients or groups",
            body = body
        )
        assertNotNull(signal)
        assertEquals("HARD", signal!!.bounceType)
        assertEquals("expert@company.com", signal.failedRecipient)
    }

    @Test
    fun `detect returns null for normal reply`() {
        assertNull(
            detector.detect(
                from = "expert@university.edu",
                subject = "Re: Introduction",
                body = "Thanks for reaching out."
            )
        )
    }

    @Test
    fun `parseBounceDetails classifies neutral MIME DSN as HARD without heuristic keywords`() {
        val message = neutralMimeDsn(status = "5.1.1", originalMessageId = "orig-neutral@example.com")
        val signal = detector.parseBounceDetails(message)
        assertNotNull(signal)
        assertEquals("HARD", signal!!.bounceType)
        assertEquals("5.1.1", signal.dsnStatus)
        assertEquals("orig-neutral@example.com", signal.originalMessageId)
        assertNull(detector.detect("system@example.com", "notice", "Delivery failed"))
    }

    private fun roundTrip(message: MimeMessage): MimeMessage {
        val buf = java.io.ByteArrayOutputStream()
        message.writeTo(buf)
        return MimeMessage(
            Session.getDefaultInstance(Properties()),
            java.io.ByteArrayInputStream(buf.toByteArray())
        )
    }

    private fun neutralMimeDsn(status: String, originalMessageId: String? = null): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("system@example.com"))
        message.subject = "notice"
        message.setHeader("Message-ID", "<neutral-bounce@example.com>")

        val multipart = MimeMultipart("report; report-type=delivery-status")
        val textPart = MimeBodyPart()
        textPart.setText("Delivery failed")
        multipart.addBodyPart(textPart)

        val dsnPart = MimeBodyPart()
        val originalLine = originalMessageId?.let { "Original-Message-ID: <$it>\n" }.orEmpty()
        dsnPart.setContent(
            """
            Reporting-MTA: dns; example.com
            Status: $status
            $originalLine
            """.trimIndent(),
            "message/delivery-status"
        )
        multipart.addBodyPart(dsnPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
    }

    private fun dsnBounce(status: String, originalMessageId: String? = null): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("mailer-daemon@example.com"))
        message.subject = "Undelivered Mail Returned to Sender"
        message.setHeader("Message-ID", "<bounce-test@example.com>")

        val multipart = MimeMultipart("report; report-type=delivery-status")
        val textPart = MimeBodyPart()
        textPart.setText("Delivery failed")
        multipart.addBodyPart(textPart)

        val dsnPart = MimeBodyPart()
        val originalLine = originalMessageId?.let { "Original-Message-ID: <$it>\n" }.orEmpty()
        dsnPart.setContent(
            """
            Reporting-MTA: dns; example.com
            Status: $status
            $originalLine
            """.trimIndent(),
            "message/delivery-status"
        )
        multipart.addBodyPart(dsnPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
    }
}
