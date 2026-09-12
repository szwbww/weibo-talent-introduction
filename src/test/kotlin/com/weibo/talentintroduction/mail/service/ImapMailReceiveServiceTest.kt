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

    private companion object {
        /** 2026-09-12 生产真实样本（只读验证过的分享链接）。 */
        const val DRIVE_SAMPLE_FILE_ID = "1eUOvutQu2yinCWYHiWIbVAVt2icbSwW9"
        const val DRIVE_SAMPLE_URL =
            "https://drive.google.com/file/d/$DRIVE_SAMPLE_FILE_ID/view?usp=drive_web"
    }

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

    // ------------------------------------------------------------------
    // 受控 Google Drive 外链材料识别（I-1/I-2/I-5/I-6/I-7）
    // ------------------------------------------------------------------

    @Test
    fun `plain text drive link registers one metadata material and leaves body untouched`() {
        val body = "请查收材料：\n\nChina_Collaborator.zip <$DRIVE_SAMPLE_URL>"
        val message = roundTrip(
            multipartMessage(parts = listOf(MimeBodyPart().apply { setText(body) }))
        )

        val received = convert(message, metadataOnly = true)

        assertEquals(
            convert(message, metadataOnly = false).body,
            received.body,
            "正文不得因材料识别而改写（与 legacy 解析逐字一致）"
        )
        assertTrue(received.body.contains("China_Collaborator.zip <$DRIVE_SAMPLE_URL>"))
        assertFalse(received.bodyTruncated)
        assertTrue(received.attachments.isEmpty(), "外链与真实 MIME 附件必须分离")
        assertEquals(1, received.linkedMaterials.size)
        val material = received.linkedMaterials.single()
        assertEquals("China_Collaborator.zip", material.fileName)
        assertNull(material.content, "识别阶段不得携带内容")
        assertNull(material.contentType, "外链登记时 contentType 为空，由后续按文件名推断")
        val source = material.source
        assertNotNull(source)
        assertEquals("unit-test-account", source!!.accountCode)
        assertEquals("INBOX", source.folder)
        assertEquals(42L, source.uidValidity)
        assertEquals(7L, source.uid)
        assertEquals("gdrive:$DRIVE_SAMPLE_FILE_ID", source.partPath)
        assertEquals(received.messageId, source.messageId)
        assertNull(source.encodedSize)
        assertEquals("external-link", source.disposition)
    }

    @Test
    fun `html anchor and bare url for the same file id register exactly one material`() {
        val html = """
            <p>Here is the archive:</p>
            <p><a href="$DRIVE_SAMPLE_URL">China_Collaborator.zip</a></p>
            <p>Mirror: $DRIVE_SAMPLE_URL</p>
        """.trimIndent()
        val message = roundTrip(
            multipartMessage(parts = listOf(MimeBodyPart().apply { setContent(html, "text/html") }))
        )

        val received = convert(message, metadataOnly = true)

        assertEquals(1, received.linkedMaterials.size, "同一 fileId 在同一叶内至多一份")
        val material = received.linkedMaterials.single()
        assertEquals("China_Collaborator.zip", material.fileName, "锚文本优先于裸 URL 前缀")
        assertEquals("gdrive:$DRIVE_SAMPLE_FILE_ID", material.source?.partPath)
        assertFalse(received.body.contains("<a href"), "正文仍走既有 stripHtml，不保留标签")
    }

    @Test
    fun `multipart alternative plain and html forms of the same link register one material`() {
        val session = Session.getDefaultInstance(Properties())
        val message = MimeMessage(session)
        message.setFrom(InternetAddress("expert@university.edu"))
        message.subject = "Re: Introduction"
        message.setHeader("Message-ID", "<gdrive-alternative@example.com>")
        val multipart = MimeMultipart("alternative")
        multipart.addBodyPart(MimeBodyPart().apply { setText("China_Collaborator.zip <$DRIVE_SAMPLE_URL>") })
        multipart.addBodyPart(
            MimeBodyPart().apply {
                setContent("<p><a href=\"$DRIVE_SAMPLE_URL\">China_Collaborator.zip</a></p>", "text/html")
            }
        )
        message.setContent(multipart)
        message.saveChanges()

        val received = convert(roundTrip(message), metadataOnly = true)

        assertEquals(1, received.linkedMaterials.size, "plain + HTML 同 fileId 只登记一份")
        assertEquals("gdrive:$DRIVE_SAMPLE_FILE_ID", received.linkedMaterials.single().source?.partPath)
        assertEquals(
            "China_Collaborator.zip <$DRIVE_SAMPLE_URL>",
            received.body,
            "alternative 正文仍取首个非空分段"
        )
    }

    @Test
    fun `non file url shapes and quoted history never register materials`() {
        val fileId = DRIVE_SAMPLE_FILE_ID
        val rejected = listOf(
            "http://drive.google.com/file/d/$fileId/view",
            "https://drive.google.com.evil/file/d/$fileId/view",
            "https://drive.google.com:8443/file/d/$fileId/view",
            "https://user@drive.google.com/file/d/$fileId/view",
            "https://drive.google.com/open?id=$fileId",
            "https://drive.google.com/file/d/$fileId/edit",
            "https://drive.google.com/drive/folders/$fileId",
            "https://drive.google.com/file/d//view",
            "https://drive.google.com/file/d/${"A".repeat(249)}/view",
            "https://docs.google.com/document/d/$fileId/edit"
        )
        for (url in rejected) {
            val message = roundTrip(
                multipartMessage(parts = listOf(MimeBodyPart().apply { setText("See $url") }))
            )
            val received = convert(message, metadataOnly = true)
            assertTrue(
                received.linkedMaterials.isEmpty(),
                "must not register $url (got ${received.linkedMaterials.map { it.fileName }})"
            )
        }

        val quoted = """
            Thanks, here is the new reply.

            On 2026-09-10 10:00, Sam <sam@example.com> wrote:
            China_Collaborator.zip <$DRIVE_SAMPLE_URL>
        """.trimIndent()
        val quotedMessage = roundTrip(
            multipartMessage(parts = listOf(MimeBodyPart().apply { setText(quoted) }))
        )
        val quotedReceived = convert(quotedMessage, metadataOnly = true)
        assertTrue(
            quotedReceived.linkedMaterials.isEmpty(),
            "引用历史中的旧链接不得重复导入 (got ${quotedReceived.linkedMaterials.map { it.fileName }})"
        )
    }

    @Test
    fun `invalid anchor text falls back to the generated drive file name`() {
        val html = "<p><a href=\"$DRIVE_SAMPLE_URL\">click here</a></p>"
        val message = roundTrip(
            multipartMessage(parts = listOf(MimeBodyPart().apply { setContent(html, "text/html") }))
        )

        val received = convert(message, metadataOnly = true)

        assertEquals(
            "GoogleDrive-$DRIVE_SAMPLE_FILE_ID",
            received.linkedMaterials.single().fileName
        )
    }

    @Test
    fun `legacy attachment mode never registers linked materials`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("China_Collaborator.zip <$DRIVE_SAMPLE_URL>") },
                    attachmentPdf()
                )
            )
        )

        val received = convert(message, metadataOnly = false)

        assertTrue(received.linkedMaterials.isEmpty(), "legacy 附件模式不引入外链语义")
        assertEquals(1, received.attachments.size)
        assertNotNull(received.attachments[0].content)
    }

    @Test
    fun `metadata mode keeps mime attachments separate from drive materials`() {
        val message = roundTrip(
            multipartMessage(
                parts = listOf(
                    MimeBodyPart().apply { setText("China_Collaborator.zip <$DRIVE_SAMPLE_URL>") },
                    attachmentPdf()
                )
            )
        )

        val received = convert(message, metadataOnly = true)

        assertEquals(listOf("2"), received.attachments.map { it.source?.partPath })
        assertEquals(listOf("gdrive:$DRIVE_SAMPLE_FILE_ID"), received.linkedMaterials.map { it.source?.partPath })
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
