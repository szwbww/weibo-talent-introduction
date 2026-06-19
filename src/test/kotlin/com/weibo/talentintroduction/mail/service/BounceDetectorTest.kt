package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        val details = detector.parseBounceDetails(message)
        assertEquals("HARD", details.bounceType)
        assertEquals("5.1.1", details.dsnStatus)
        assertEquals("orig-1@example.com", details.originalMessageId)
    }

    @Test
    fun `parseBounceDetails classifies 4_2_2 as SOFT`() {
        val message = dsnBounce(status = "4.2.2")
        val details = detector.parseBounceDetails(message)
        assertEquals("SOFT", details.bounceType)
        assertEquals("4.2.2", details.dsnStatus)
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
