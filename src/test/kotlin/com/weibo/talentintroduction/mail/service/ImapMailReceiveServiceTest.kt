package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import javax.mail.Message
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * I-1 / I-2 / I-3 回归护栏：extractBody/extractAttachments 的 multipart 白名单分流、
 * 正文/附件互斥、metadata 描述符完整性、正文有界截断。
 * 全部夹具经 writeTo + 重解析往返，复现真实 IMAP 收到的 InputStream 分段路径。
 */
class ImapMailReceiveServiceTest {
    private val service = ImapMailReceiveService(MailAttachmentStorageProperties(metadataOnly = false))
    private val metadataService = ImapMailReceiveService(
        MailAttachmentStorageProperties(metadataOnly = true)
    )
    private val tinyCapMetadataService = ImapMailReceiveService(
        MailAttachmentStorageProperties(
            metadataOnly = true,
            metadataMaxBodyBytes = 16
        )
    )
    private val account = MailSenderAccount(
        accountCode = "unit-test-account",
        senderEmail = "sender@example.com",
        senderName = "Sender",
        senderTitle = null,
        senderDisplayName = null,
        teamName = null,
        countryName = null,
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "sender",
        smtpPassword = "secret",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "sender",
        imapPassword = "secret"
    )

    private fun convert(message: Message, metadataOnly: Boolean = false, tinyCap: Boolean = false): ReceivedMail {
        val svc = when {
            tinyCap -> tinyCapMetadataService
            metadataOnly -> metadataService
            else -> service
        }
        return svc.convertToReceivedMail(
            message as MimeMessage,
            account = account,
            folder = "INBOX",
            uidValidity = 42L,
            imapUid = 7L
        )
    }

    @Test
    fun `legacy mode returns original attachment bytes and keeps legacy DTO shape`() {
        val message = roundTrip(multipartMessage(parts = listOf(attachmentPdf())))
        val received = convert(message)

        assertEquals(1, received.attachments.size)
        val attachment = received.attachments[0]
        assertNotNull(attachment.content)
        assertEquals("pdf-bytes".toByteArray().toList(), attachment.content!!.toList())
        assertEquals(7L, received.imapUid)
        assertEquals(42L, received.uidValidity)
        assertFalse(received.bodyTruncated)
    }

    @Test
    fun `metadata mode has null content and complete per attachment remote source`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Hello body") },
                    attachmentPdf()
                )
            )
        )
        val received = convert(message, metadataOnly = true)

        assertEquals(1, received.attachments.size)
        val attachment = received.attachments[0]
        assertNull(attachment.content, "metadata mode must never carry content")
        val source = attachment.source
        assertNotNull(source, "metadata mode must carry per-attachment remote source")
        assertEquals("unit-test-account", source!!.accountCode)
        assertEquals("INBOX", source.folder)
        assertEquals(42L, source.uidValidity)
        assertEquals(7L, source.uid)
        assertEquals("2", source.partPath)
        assertEquals("cv.pdf", attachment.fileName)
        assertEquals("application/pdf", attachment.contentType)
        assertEquals("attachment", source.disposition?.lowercase())
        // encodedSize 只是估算（>=0 或 null），不是实际 file_size
        val encodedSize = source.encodedSize
        assertTrue(encodedSize == null || encodedSize >= 0L)
        assertEquals("Hello body", received.body)
        assertFalse(received.bodyTruncated)
    }

    @Test
    fun `legacy constructor shape stays compatible with existing callers`() {
        val attachment = ReceivedMailAttachment(
            fileName = "legacy.pdf",
            contentType = "application/pdf",
            content = "legacy".toByteArray()
        )
        assertEquals("legacy", String(attachment.content!!))
        assertNull(attachment.source)
    }

    @Test
    fun `encoded and folded mime subjects decode at header read without content access`() {
        // 假消息任何正文/附件内容访问都失败并计数（I-4/P3）：证明 subject 解码只发生在
        // 已读取的 header 字符串上，不新增 getContent / 附件流访问；walkContent 原有的
        // 一次 inputStream 正文读取尝试保持不变（被 fixture 拒绝 → 空正文，与改动前一致）。
        val session = Session.getDefaultInstance(Properties())
        val blocked = ContentBlockedMessage(session)
        blocked.setFrom(InternetAddress("expert@university.edu"))
        blocked.setHeader(
            "Subject",
            "=?UTF-8?Q?Re:_Remote_advisory_collaboration?=\r\n =?UTF-8?Q?_request?="
        )
        blocked.setHeader("Message-ID", "<blocked-content@example.com>")

        val received = convert(blocked, metadataOnly = true)

        assertEquals("Re: Remote advisory collaboration request", received.subject,
            "合法 folding 的 Q 两段必须在头读取处解码成可读文本")
        assertEquals(0, blocked.contentAccesses, "subject 解码绝不触发 getContent")
        assertEquals(1, blocked.streamAccesses, "仅 walkContent 既有的一次 inputStream 读取尝试")
        assertEquals("", received.body, "被拒绝的正文保持原行为（空正文，不冒充成功）")
        assertTrue(received.attachments.isEmpty())
    }

    @Test
    fun `non-ascii subject round trips through writeTo and decodes at header read`() {
        val original = "Re: Remote advisory collaboration request 会议邀请"
        val message = subjectMessage(original)
        val received = convert(message, metadataOnly = true)

        assertEquals(original, received.subject,
            "writeTo 重解析后的 RFC2047（含 folding）subject 必须还原为原文本")
        assertTrue(received.body.contains("Hello body"), "正文解析不受 subject 解码影响")
        assertFalse(received.bodyTruncated)
    }

    @Test
    fun `unknown charset subject stays raw and never blocks receiving`() {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.setHeader("Subject", "=?x-unknown-charset?Q?abc?=")
        message.setHeader("Message-ID", "<unknown-charset-subject@example.com>")
        message.setContent("Hello body", "text/plain; charset=utf-8")
        message.saveChanges()

        val received = convert(message, metadataOnly = true)

        assertEquals("=?x-unknown-charset?Q?abc?=", received.subject,
            "未知 charset 回退原串，不抛异常、不阻断收信")
        assertEquals("Hello body", received.body)
    }

    @Test
    fun `encoded Chinese and overlong attachment names survive metadata mode`() {
        val longName = "A".repeat(400) + ".pdf"
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Hello body") },
                    MimeBodyPart().apply {
                        setContent("x".toByteArray(), "application/pdf")
                        setFileName("中文简历.pdf")
                    },
                    MimeBodyPart().apply {
                        setContent("y".toByteArray(), "application/pdf")
                        setFileName(longName)
                    }
                )
            )
        )
        val received = convert(message, metadataOnly = true)
        val names = received.attachments.map { it.fileName }
        assertTrue(names.any { it.contains("中文简历") }, "encoded Chinese name should be decoded, got $names")
        assertTrue(names.any { it.length == 404 && it.endsWith(".pdf") }, "overlong name should survive whole")
        assertEquals(2, received.attachments.size)
        assertNull(received.attachments[0].content)
        assertNotNull(received.attachments[0].source)
    }

    @Test
    fun `text attachment with filename is not body and not listed in metadata mode`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Real body") },
                    MimeBodyPart().apply {
                        setContent("notes-line", "text/plain")
                        setFileName("notes.txt")
                    }
                )
            )
        )
        val received = convert(message, metadataOnly = true)
        assertEquals("Real body", received.body)
        assertEquals(1, received.attachments.size)
        assertEquals("notes.txt", received.attachments[0].fileName)
        assertNull(received.attachments[0].content)
    }

    @Test
    fun `unnamed inline signature is not an expert material`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Body with signature") },
                    MimeBodyPart().apply {
                        setContent(ByteArray(0), "image/png")
                        disposition = Part.INLINE
                    }
                )
            )
        )
        val received = convert(message, metadataOnly = true)
        assertEquals("Body with signature", received.body)
        assertTrue(received.attachments.isEmpty())
    }

    @Test
    fun `unnamed attachment with attachment disposition registers with part path`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Body") },
                    MimeBodyPart().apply {
                        setContent(ByteArray(4), "application/octet-stream")
                        disposition = Part.ATTACHMENT
                    }
                )
            )
        )
        val received = convert(message, metadataOnly = true)
        assertEquals(1, received.attachments.size)
        assertEquals("未命名附件-2", received.attachments[0].fileName)
        assertEquals("2", received.attachments[0].source?.partPath)
        assertNull(received.attachments[0].content)
    }

    @Test
    fun `nested rfc822 with filename is one opaque attachment`() {
        val session = Session.getDefaultInstance(Properties())
        val nested = MimeMessage(session).apply {
            setFrom(InternetAddress("inner@example.com"))
            subject = "nested subject"
            setText("Inner secret body")
            setHeader("Message-ID", "<inner-nested@example.com>")
        }
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("Outer body") },
                    MimeBodyPart().apply {
                        setContent(nested, "message/rfc822")
                        setFileName("original.eml")
                    }
                )
            )
        )
        val received = convert(message, metadataOnly = true)
        assertEquals("Outer body", received.body)
        assertEquals(1, received.attachments.size)
        assertEquals("original.eml", received.attachments[0].fileName)
        assertEquals("message/rfc822", received.attachments[0].contentType)
        assertNull(received.attachments[0].content)
        assertEquals("2", received.attachments[0].source?.partPath)
    }

    @Test
    fun `extractBody includes delivery-status segment for multipart report`() {
        val message = roundTrip(reportMimeDsn())
        val received = convert(message)
        assertTrue(received.body.contains("Status: 5.1.1"))
        assertTrue(received.body.contains("Delivery failed"))
    }

    @Test
    fun `extractBody does not duplicate content for multipart alternative`() {
        val message = roundTrip(alternativeMessage())
        val received = convert(message)
        assertEquals("Hello", received.body)
        assertEquals(1, Regex("Hello").findAll(received.body).count())
    }

    @Test
    fun `body over metadata cap is bounded and flagged while remaining readable`() {
        val longText = "a".repeat(200)
        val message = roundTrip(
            multipartMessage(
                parts = listOf(MimeBodyPart().apply { setText(longText) })
            )
        )
        val received = convert(message, tinyCap = true)
        assertTrue(received.bodyTruncated, "body over metadata cap must be flagged truncated")
        assertTrue(received.body.length <= 16, "body must be bounded by metadata cap")
        assertFalse(received.body.isEmpty())
    }

    @Test
    fun `mime node explosion raises explicit structure limit error`() {
        val limitedService = ImapMailReceiveService(
            MailAttachmentStorageProperties(metadataOnly = true, metadataMaxMimeNodes = 2)
        )
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("a") },
                    MimeBodyPart().apply { setText("b") },
                    MimeBodyPart().apply { setText("c") }
                )
            )
        )
        val failure = runCatching {
            limitedService.convertToReceivedMail(
                message as MimeMessage,
                account = account,
                folder = "INBOX",
                uidValidity = 42L,
                imapUid = 8L
            )
        }.exceptionOrNull()
        assertTrue(
            failure is MetadataStructureLimitException,
            "node overflow must raise an explicit retryable error, got ${failure?.javaClass?.simpleName}"
        )
    }

    @Test
    fun `metadata deadline expiry raises explicit timeout error`() {
        val expiredService = ImapMailReceiveService(
            MailAttachmentStorageProperties(metadataOnly = true, metadataTotalTimeoutSeconds = 0)
        )
        val message = roundTrip(
            multipartMessage(parts = listOf(MimeBodyPart().apply { setText("body") }))
        )
        val failure = runCatching {
            expiredService.convertToReceivedMail(
                message as MimeMessage,
                account = account,
                folder = "INBOX",
                uidValidity = 42L,
                imapUid = 9L
            )
        }.exceptionOrNull()
        assertTrue(
            failure is MetadataTimeoutException,
            "deadline expiry must raise an explicit retryable error, got ${failure?.javaClass?.simpleName}"
        )
    }

    private fun subjectMessage(subject: String): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.subject = subject
        message.setHeader("Message-ID", "<subject-fixture@example.com>")
        val multipart = MimeMultipart("mixed")
        multipart.addBodyPart(MimeBodyPart().apply { setText("Hello body") })
        message.setContent(multipart)
        message.saveChanges()
        return roundTrip(message)
    }

    private fun roundTrip(message: MimeMessage): MimeMessage {
        val buf = ByteArrayOutputStream()
        message.writeTo(buf)
        return MimeMessage(
            Session.getDefaultInstance(Properties()),
            ByteArrayInputStream(buf.toByteArray())
        )
    }

    private fun multipartMessage(parts: List<MimeBodyPart>): MimeMessage {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.subject = "Re: Introduction"
        message.setHeader("Message-ID", "<unit-test@example.com>")
        val multipart = MimeMultipart("mixed")
        parts.forEach { multipart.addBodyPart(it) }
        message.setContent(multipart)
        message.saveChanges()
        return message
    }

    private fun attachmentPdf(): MimeBodyPart =
        MimeBodyPart().apply {
            setContent("pdf-bytes".toByteArray(), "application/pdf")
            setFileName("cv.pdf")
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

/**
 * 仅头可读的假消息（I-4/P3）：任何正文/附件内容访问（getContent / 附件 inputStream）
 * 都抛 IOException 并计数。subject 解码只允许操作已读到的 header 字符串；若实现
 * 顺手触发内容访问，计数断言立即失败。
 */
private class ContentBlockedMessage(session: Session) : MimeMessage(session) {
    var contentAccesses = 0
        private set
    var streamAccesses = 0
        private set

    override fun getContent(): Any {
        contentAccesses++
        throw IOException("content access blocked by fixture")
    }

    override fun getInputStream(): InputStream {
        streamAccesses++
        throw IOException("stream access blocked by fixture")
    }
}
