package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * I-1 / I-2 回归护栏：extractBody 对 multipart 按子类型分流，
 * report 拼接（含 DSN 机器段）、alternative 取首个非空（不重复）。
 * 全部夹具经 writeTo + 重解析往返，复现真实 IMAP 收到的 InputStream 分段路径。
 */
class ImapMailReceiveServiceTest {
    private val service = ImapMailReceiveService()

    @Test
    fun `extractBody includes delivery-status segment for multipart report`() {
        val message = roundTrip(reportMimeDsn())
        val body = service.extractBody(message)
        assertTrue(body.contains("Status: 5.1.1"))
        assertTrue(body.contains("Delivery failed"))
    }

    @Test
    fun `extractBody does not duplicate content for multipart alternative`() {
        val message = roundTrip(alternativeMessage())
        val body = service.extractBody(message)
        assertEquals("Hello", body)
        assertEquals(1, Regex("Hello").findAll(body).count())
    }

    private fun roundTrip(message: MimeMessage): MimeMessage {
        val buf = ByteArrayOutputStream()
        message.writeTo(buf)
        return MimeMessage(
            Session.getDefaultInstance(Properties()),
            ByteArrayInputStream(buf.toByteArray())
        )
    }

    private fun reportMimeDsn(): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("system@example.com"))
        message.subject = "notice"
        message.setHeader("Message-ID", "<report-mime@example.com>")

        val multipart = MimeMultipart("report; report-type=delivery-status")
        val textPart = MimeBodyPart()
        textPart.setText("Delivery failed")
        multipart.addBodyPart(textPart)

        val dsnPart = MimeBodyPart()
        dsnPart.setContent(
            """
            Reporting-MTA: dns; example.com
            Status: 5.1.1
            """.trimIndent(),
            "message/delivery-status"
        )
        multipart.addBodyPart(dsnPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
    }

    private fun alternativeMessage(): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.subject = "Re: Introduction"
        message.setHeader("Message-ID", "<alternative@example.com>")

        val multipart = MimeMultipart("alternative")
        val plainPart = MimeBodyPart()
        plainPart.setText("Hello")
        multipart.addBodyPart(plainPart)

        val htmlPart = MimeBodyPart()
        htmlPart.setContent("<p>Hello</p>", "text/html")
        multipart.addBodyPart(htmlPart)

        message.setContent(multipart)
        message.saveChanges()
        return message
    }
}
