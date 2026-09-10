package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.domain.InboundMailProcessing
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.repository.InboundMailProcessingRepository
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.Optional
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.SendFailedException

class SmtpMailDeliveryServiceTest {
    private val mailContentService = MailContentService()
    private val emailSuppressionService = Mockito.mock(EmailSuppressionService::class.java)

    init {
        Mockito.`when`(emailSuppressionService.isSuppressed(Mockito.anyString())).thenReturn(false)
    }

    private val enabledTokenService = UnsubscribeTokenService(
        UnsubscribeProperties(
            baseUrl = "https://outreach.example.com",
            secret = "test-secret"
        )
    )
    private val disabledTokenService = UnsubscribeTokenService(
        UnsubscribeProperties(baseUrl = "", secret = "")
    )
    @Test
    fun `SendFailedException with 550 returns PERMANENT`() {
        val delivered = SmtpErrorClassifier.fromSendFailedException(
            SendFailedException("550 5.1.1 User unknown"),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.PERMANENT, delivered.errorCategory)
        assertEquals(550, delivered.smtpResponseCode)
    }

    @Test
    fun `MessagingException with 421 returns TRANSIENT`() {
        val delivered = SmtpErrorClassifier.fromMessagingException(
            MessagingException("421 4.7.0 Try again later"),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.TRANSIENT, delivered.errorCategory)
        assertEquals(421, delivered.smtpResponseCode)
    }

    @Test
    fun `AuthenticationFailedException returns INFRASTRUCTURE`() {
        val delivered = SmtpErrorClassifier.fromAuthenticationFailedException(
            AuthenticationFailedException("Invalid credentials"),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.INFRASTRUCTURE, delivered.errorCategory)
        assertNull(delivered.smtpResponseCode)
        assertEquals("AUTH_FAILED:Invalid credentials", delivered.errorDetail)
    }

    @Test
    fun `unparseable SMTP code defaults to TRANSIENT`() {
        val delivered = SmtpErrorClassifier.fromMessagingException(
            MessagingException("Connection reset by peer"),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.TRANSIENT, delivered.errorCategory)
        assertNull(delivered.smtpResponseCode)
    }

    @Test
    fun `Spring MailSendException unwraps nested 550 as PERMANENT`() {
        val delivered = SmtpErrorClassifier.fromMailException(
            MailSendException("send failed", SendFailedException("550 5.1.1 User unknown")),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.PERMANENT, delivered.errorCategory)
        assertEquals(550, delivered.smtpResponseCode)
    }

    @Test
    fun `Spring MailAuthenticationException returns INFRASTRUCTURE`() {
        val delivered = SmtpErrorClassifier.fromMailException(
            MailAuthenticationException("Invalid credentials"),
            messageId = "msg-1"
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.INFRASTRUCTURE, delivered.errorCategory)
        assertNull(delivered.smtpResponseCode)
    }

    @Test
    fun `send classifies Spring wrapped SMTP failures`() {
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                throw MailSendException("send failed", SendFailedException("550 5.1.1 User unknown"))
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        val delivered = SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("bad@example.com", "Subject", "Body", messageId = "msg-1")
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.PERMANENT, delivered.errorCategory)
        assertEquals(550, delivered.smtpResponseCode)
    }

    @Test
    fun `send adds List-Unsubscribe headers when token service enabled`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)
        val mail = ComposedMail("recipient@example.com", "Subject", "Body", messageId = "msg-1")

        val delivered = SmtpMailDeliveryService(factory, enabledTokenService, mailContentService, emailSuppressionService).send(account, mail)

        assertEquals("SENT", delivered.status)
        val message = captured.single()
        assertEquals("Subject", message.subject)
        assertEquals("Body", message.content.toString())

        val listUnsubscribe = message.getHeader("List-Unsubscribe", null)
        assertTrue(listUnsubscribe.contains("https://outreach.example.com/u/unsubscribe?token="))
        assertTrue(listUnsubscribe.contains("mailto:test@example.com?subject=unsubscribe"))
        assertEquals("List-Unsubscribe=One-Click", message.getHeader("List-Unsubscribe-Post", null))
    }

    @Test
    fun `list unsubscribe post header value is exactly RFC 8058 postarg`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)
        val mail = ComposedMail("recipient@example.com", "Subject", "Body", messageId = "msg-1")

        val delivered = SmtpMailDeliveryService(factory, enabledTokenService, mailContentService, emailSuppressionService).send(account, mail)

        assertEquals("SENT", delivered.status)
        val message = captured.single()
        assertEquals("List-Unsubscribe=One-Click", message.getHeader("List-Unsubscribe-Post").single())
        assertEquals(
            "<${enabledTokenService.unsubscribeUrl(mail.to)}>, <mailto:test@example.com?subject=unsubscribe>",
            message.getHeader("List-Unsubscribe", null)
        )
    }

    @Test
    fun `send omits List-Unsubscribe headers when token service disabled`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("recipient@example.com", "Subject", "Body", messageId = "msg-1")
        )

        val message = captured.single()
        assertNull(message.getHeader("List-Unsubscribe", null))
        assertNull(message.getHeader("List-Unsubscribe-Post", null))
    }

    @Test
    fun `send uses plain string content for non-html mail`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("recipient@example.com", "Subject", "Plain body", html = false)
        )

        val message = captured.single()
        assertEquals("Plain body", message.content.toString())
    }

    @Test
    fun `send uses multipart alternative for html mail`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)
        val htmlBody = "<p>Hello <strong>world</strong></p>"

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("recipient@example.com", "Subject", htmlBody, html = true)
        )

        val multipart = captured.single().content as MimeMultipart
        assertTrue(multipart.contentType.startsWith("multipart/alternative"))
        assertEquals(2, multipart.count)
        assertTrue(multipart.getBodyPart(0).contentType.lowercase().startsWith("text/plain"))
        assertEquals("Hello world", multipart.getBodyPart(0).content.toString().trim())
        assertEquals(htmlBody, multipart.getBodyPart(1).content.toString())
    }

    @Test
    fun `send uses explicit text part when provided for html mail`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail(
                to = "recipient@example.com",
                subject = "Subject",
                body = "<p>HTML</p>",
                html = true,
                text = "Custom plain text"
            )
        )

        val multipart = captured.single().content as MimeMultipart
        assertEquals("Custom plain text", multipart.getBodyPart(0).content.toString().trim())
    }

    // ───────────────────────── fast-p 02：calendar MIME 附件（I-3） ─────────────────────────
    // IP-3/4/5：附件字节来自 01 真实生成器 preview 产物（icsText/sha256/semanticSha256），
    // 通过真实 MIME 构造与 writeTo→reparse 往返验证，禁止手写相同假串。

    private fun meetingAccount() = MailSenderAccount(
        accountCode = "test_acct",
        senderEmail = "test@example.com",
        senderName = "LuKai",
        senderTitle = "Customer Care Officer",
        senderDisplayName = null,
        teamName = "Qingfei Tech Talent Team",
        countryName = "China",
        smtpHost = "smtp.example.com",
        smtpPort = 465,
        smtpUsername = "test@example.com",
        smtpPassword = "secret",
        imapHost = "imap.example.com",
        imapPort = 993,
        imapUsername = "test@example.com",
        imapPassword = "secret"
    )

    /**
     * 01 生成器协作者 stub：正文由通用 `MEETING_INVITATION` 模板链路渲染；
     * 变量取服务端事实，正文只拼装两个会议值，ICS 字节/语义可复算。
     */
    private fun mockMeetingInvitationBody(
        templateService: MailComposeTemplateService,
        variableService: MailVariableService
    ) {
        Mockito.`when`(variableService.resolveExpertProfileFor(anyValue(meetingContact()))).thenReturn(null)
        Mockito.`when`(
            variableService.buildVariables(
                anyValue(meetingAccount()),
                Mockito.any(),
                Mockito.any(),
                Mockito.anyBoolean(),
                Mockito.any()
            )
        ).thenReturn(
            mapOf(
                "senderName" to "LuKai",
                "senderTitle" to "Customer Care Officer",
                "teamName" to "Qingfei Tech Talent Team",
                "countryName" to "China",
                "expertName" to "Professor Basdogan",
                "expertFamilyName" to "Basdogan"
            )
        )
        Mockito.`when`(
            templateService.renderByCode(
                eqValue("MEETING_INVITATION"),
                anyValue(emptyMap()),
                Mockito.anyInt()
            )
        ).thenAnswer { invocation ->
            val variables = invocation.getArgument<Map<String, String>>(1)
            ComposeTemplateRenderResult(
                subject = "Meeting invitation",
                body = "Dear " + variables["expertName"].orEmpty() + ",\n\n" +
                    "We have noted the meeting time as " + variables["meeting_time"].orEmpty() + ".\n\n" +
                    "Please join the meeting using the following link:\n\n" +
                    variables["zoom_url"].orEmpty() + "\n\n" +
                    "Best regards,\n" + variables["senderName"].orEmpty() + ", " +
                    variables["senderTitle"].orEmpty()
            )
        }
    }

    /** Matcher 占位：resolveExpertProfileFor 参数非空。 */
    private fun meetingContact() = ExpertContact(
        id = 1L,
        campaignId = 1,
        orcidId = "0000-0001-2345-6789",
        expertEmail = "expert@test.com",
        expertName = "Professor Basdogan",
        currentStatus = "WAITING_MEETING_CONFIRMATION"
    )

    /** 01 真实生成器产物 → 快照（与 01 样例同配置：2026-09-11 伊斯坦布尔 15:00–15:30 中国时间）。 */
    private fun realMeetingSnapshot(): CalendarAttachmentSnapshot {
        val inboundRepo = Mockito.mock(InboundMailProcessingRepository::class.java)
        val contactRepo = Mockito.mock(ExpertContactRepository::class.java)
        val accountService = Mockito.mock(MailSenderAccountService::class.java)
        val templateService = Mockito.mock(MailComposeTemplateService::class.java)
        val variableService = Mockito.mock(MailVariableService::class.java)
        val processing = InboundMailProcessing(
            id = 7L, senderAccountCode = "test_acct", imapUid = 1L,
            messageId = "in-1", fromEmail = "expert@test.com",
            subject = "Re: invitation", body = "Hello", cleanedBody = "Hello",
            receivedAt = LocalDateTime.now(),
            processStatus = "MANUAL_REVIEW", processReason = "QA_NO_MATCH",
            expertContactId = 1L
        )
        val contact = ExpertContact(
            id = 1L, campaignId = 1, orcidId = "0000-0001-2345-6789",
            expertEmail = "expert@test.com", expertName = "Professor Basdogan",
            currentStatus = "WAITING_MEETING_CONFIRMATION"
        )
        Mockito.`when`(inboundRepo.findById(7L)).thenReturn(Optional.of(processing))
        Mockito.`when`(contactRepo.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(accountService.getManualSendAccount("test_acct")).thenReturn(meetingAccount())
        mockMeetingInvitationBody(templateService, variableService)
        val generator = MeetingConfirmationService(
            inboundRepo, contactRepo, accountService, templateService, MailContentService(),
            variableService
        )
        val input = MeetingInput(
            zoneId = "Europe/Istanbul",
            startLocal = "2026-09-11T10:00",
            endLocal = "2026-09-11T10:30",
            zoomUrl = "https://zoom.us/j/87102801187?pwd=RH3bf4vbH0SoyTq2UW1Dzzuag4kISa.1",
            generatedAt = "2026-09-09T03:00:40Z"
        )
        val preview = generator.preview(7L, MeetingPreviewRequest(contactId = 1L, meeting = input))
        // A-1 fixture 契约：附件名与 01 样例同；时间 15:00–15:30 中国。
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", preview.attachment.filename)
        assertTrue(preview.chinaTime.startsWith("2026/09/11"))
        return CalendarAttachmentSnapshot(
            schemaVersion = 1,
            filename = preview.attachment.filename,
            contentType = preview.attachment.contentType,
            icsText = preview.attachment.icsText,
            sha256 = preview.attachment.sha256,
            semanticSha256 = preview.attachment.semanticSha256
        )
    }

    private fun captureSentMime(
        account: MailSenderAccount,
        mail: ComposedMail,
        tokenService: UnsubscribeTokenService = disabledTokenService
    ): MimeMessage {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)
        val delivered = SmtpMailDeliveryService(factory, tokenService, mailContentService, emailSuppressionService)
            .send(account, mail)
        assertEquals("SENT", delivered.status)
        return captured.single()
    }

    /** Mockito 捕获真实 MimeMessage → writeTo → 以 MimeMessage 重新解析（真实 MIME 往返）。 */
    private fun roundTrip(message: MimeMessage): MimeMessage {
        val out = ByteArrayOutputStream()
        message.writeTo(out)
        return MimeMessage(javax.mail.Session.getInstance(System.getProperties()), ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `send with calendar attachment builds exactly mixed body part and calendar part`() {
        val snapshot = realMeetingSnapshot()
        val mail = ComposedMail(
            to = "recipient@example.com",
            subject = "Meeting confirmation",
            body = "<p>Please join the meeting.</p>",
            html = true,
            text = "Please join the meeting.",
            messageId = "msg-meeting-1",
            inReplyTo = "<in-1@example.com>",
            references = "<in-0@example.com> <in-1@example.com>",
            calendarAttachment = snapshot
        )
        val reparsed = roundTrip(captureSentMime(testAccount(), mail, enabledTokenService))

        // I-3：外层 multipart/mixed 恰好 2 part；part 2 为 text/calendar 附件。
        assertTrue(reparsed.contentType.startsWith("multipart/mixed"), "outer must be mixed: ${reparsed.contentType}")
        val mixed = reparsed.content as MimeMultipart
        assertEquals(2, mixed.count)
        val alternative = mixed.getBodyPart(0).content as MimeMultipart
        assertTrue(alternative.contentType.startsWith("multipart/alternative"))
        assertEquals(2, alternative.count)
        assertTrue(alternative.getBodyPart(0).contentType.lowercase().startsWith("text/plain"))
        assertEquals("Please join the meeting.", alternative.getBodyPart(0).content.toString().trim())
        assertTrue(alternative.getBodyPart(1).contentType.lowercase().startsWith("text/html"))
        assertEquals("<p>Please join the meeting.</p>", alternative.getBodyPart(1).content.toString())

        val calendarPart = mixed.getBodyPart(1)
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)
        assertEquals("attachment", calendarPart.disposition)
        assertTrue(calendarPart.contentType.lowercase().startsWith("text/calendar"))
        assertEquals(snapshot.icsText, calendarPart.inputStream.readBytes().toString(Charsets.UTF_8))
        assertEquals(snapshot.icsText.toByteArray(Charsets.UTF_8).size, calendarPart.inputStream.readBytes().size)

        // I-3：退订/线程/messageId 保持原有位置与值。
        assertEquals("msg-meeting-1", reparsed.messageID)
        assertEquals("<in-1@example.com>", reparsed.getHeader("In-Reply-To", null))
        assertEquals("<in-0@example.com> <in-1@example.com>", reparsed.getHeader("References", null))
        val listUnsubscribe = reparsed.getHeader("List-Unsubscribe", null)
        assertTrue(listUnsubscribe.contains("https://outreach.example.com/u/unsubscribe?token="))
        assertTrue(listUnsubscribe.contains("mailto:test@example.com?subject=unsubscribe"))
    }

    @Test
    fun `send with calendar attachment and plain body keeps single plain part plus ics`() {
        val snapshot = realMeetingSnapshot()
        val mail = ComposedMail(
            to = "recipient@example.com",
            subject = "Meeting confirmation",
            body = "Plain meeting body",
            html = false,
            calendarAttachment = snapshot
        )
        val reparsed = roundTrip(captureSentMime(testAccount(), mail))

        val mixed = reparsed.content as MimeMultipart
        assertTrue(reparsed.contentType.startsWith("multipart/mixed"))
        assertEquals(2, mixed.count)
        assertEquals("Plain meeting body", mixed.getBodyPart(0).content.toString().trim())
        val calendarPart = mixed.getBodyPart(1)
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)
        assertEquals(snapshot.icsText, calendarPart.inputStream.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `calendar attachment bytes survive writeTo round trip byte identical`() {
        val snapshot = realMeetingSnapshot()
        val mail = ComposedMail(
            to = "recipient@example.com",
            subject = "Meeting confirmation",
            body = "<p>Join</p>",
            html = true,
            text = "Join",
            calendarAttachment = snapshot
        )
        val reparsed = roundTrip(captureSentMime(testAccount(), mail))
        val mixed = reparsed.content as MimeMultipart
        val calendarBytes = mixed.getBodyPart(1).inputStream.readBytes()
        assertArrayEquals(snapshot.icsText.toByteArray(Charsets.UTF_8), calendarBytes)
    }

    @Test
    fun `no-calendar html and plain sends keep pre-02 single multipart shapes`() {
        val htmlMessage = roundTrip(captureSentMime(testAccount(), ComposedMail(
            to = "recipient@example.com", subject = "S", body = "<p>B</p>", html = true, text = "B"
        )))
        assertTrue(htmlMessage.contentType.startsWith("multipart/alternative"))
        assertEquals(2, (htmlMessage.content as MimeMultipart).count)
        val textMessage = roundTrip(captureSentMime(testAccount(), ComposedMail(
            to = "recipient@example.com", subject = "S", body = "B", html = false
        )))
        assertEquals("B", textMessage.content.toString())
    }

    @Test
    fun `smtp test saves meeting confirmation eml fixture to target for A-1 comparison`() {
        val snapshot = realMeetingSnapshot()
        val mail = ComposedMail(
            to = "recipient@example.com",
            subject = "Meeting confirmation",
            body = "<p>Please join the meeting.</p>",
            html = true,
            text = "Please join the meeting.",
            messageId = "meeting-confirmation-fixture@example.com",
            calendarAttachment = snapshot
        )
        val reparsed = roundTrip(captureSentMime(testAccount(), mail, enabledTokenService))
        val mixed = reparsed.content as MimeMultipart
        val calendarPart = mixed.getBodyPart(1)
        assertEquals("meeting-2026-09-11-Professor-Basdogan.ics", calendarPart.fileName)
        assertEquals(snapshot.icsText, calendarPart.inputStream.readBytes().toString(Charsets.UTF_8))
        assertArrayEquals(snapshot.icsText.toByteArray(Charsets.UTF_8), calendarPart.inputStream.readBytes())
        assertTrue(snapshot.sha256.length == 64 && snapshot.semanticSha256.length == 64)

        // A-1：把 SMTP 测试真实产出的 .eml 保存到 target/meeting-confirmation.eml
        // （供人工打开邮件客户端下载附件并与 01 样例 SHA256 比对）。
        val out = ByteArrayOutputStream()
        reparsed.writeTo(out)
        val target = Paths.get("target")
        Files.createDirectories(target)
        Files.write(target.resolve("meeting-confirmation.eml"), out.toByteArray())
    }

    @Test
    fun `send writes In-Reply-To and References headers when provided`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail(
                to = "recipient@example.com",
                subject = "Subject",
                body = "Body",
                inReplyTo = "<anchor-1@example.com>",
                references = "<anchor-0@example.com> <anchor-1@example.com>"
            )
        )

        val message = captured.single()
        assertEquals("<anchor-1@example.com>", message.getHeader("In-Reply-To", null))
        assertEquals(
            "<anchor-0@example.com> <anchor-1@example.com>",
            message.getHeader("References", null)
        )
    }

    @Test
    fun `send omits thread headers when inReplyTo and references are null`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("recipient@example.com", "Subject", "Body")
        )

        val message = captured.single()
        assertNull(message.getHeader("In-Reply-To", null))
        assertNull(message.getHeader("References", null))
    }

    @Test
    fun `send omits thread headers when inReplyTo is blank`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail(
                to = "recipient@example.com",
                subject = "Subject",
                body = "Body",
                inReplyTo = "   ",
                references = "   "
            )
        )

        val message = captured.single()
        assertNull(message.getHeader("In-Reply-To", null))
        assertNull(message.getHeader("References", null))
    }

    @Test
    fun `send writes In-Reply-To only once`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail(
                to = "recipient@example.com",
                subject = "Subject",
                body = "Body",
                inReplyTo = "<anchor-1@example.com>"
            )
        )

        val headers = captured.single().getHeader("In-Reply-To")
        assertEquals(1, headers.size)
    }

    @Test
    fun `send uses display name in From when senderDisplayName is present`() {
        val message = captureSent(testAccount(senderDisplayName = "QF Tech Talent"))

        assertEquals("QF Tech Talent <test@example.com>", message.getHeader("From", null))
    }

    @Test
    fun `send falls back to bare address when senderDisplayName is null`() {
        val message = captureSent(testAccount(senderDisplayName = null))

        assertEquals("test@example.com", message.getHeader("From", null))
    }

    @Test
    fun `send falls back to bare address when senderDisplayName is blank`() {
        val message = captureSent(testAccount(senderDisplayName = "   "))

        assertEquals("test@example.com", message.getHeader("From", null))
    }

    @Test
    fun `send encodes non-ASCII display name`() {
        val message = captureSent(testAccount(senderDisplayName = "李雷"))

        val from = message.getHeader("From", null)
        assertTrue(from.startsWith("=?UTF-8?"), "expected RFC 2047 encoded word, got: $from")
        assertTrue(!from.contains("李雷"), "raw non-ASCII bytes must not appear in From: $from")
        assertTrue(from.endsWith("<test@example.com>"), "address part must be preserved: $from")
    }

    @Test
    fun `send throws RecipientSuppressedException before touching smtp when recipient suppressed`() {
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val account = testAccount()
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked@example.com")).thenReturn(true)

        assertThrows(RecipientSuppressedException::class.java) {
            SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
                account,
                ComposedMail("blocked@example.com", "Subject", "Body")
            )
        }

        // I-1: 拦截必须发生在接触任何 SMTP 资源之前 —— getSender 零调用。
        Mockito.verify(factory, Mockito.never()).getSender(anyValue(testAccount()))
    }

    @Test
    fun `send proceeds when recipient suppressed but allowSuppressedRecipient is true`() {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        val account = testAccount()
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)
        Mockito.`when`(emailSuppressionService.isSuppressed("blocked@example.com")).thenReturn(true)

        val delivered = SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail(
                to = "blocked@example.com",
                subject = "Subject",
                body = "Body",
                allowSuppressedRecipient = true
            )
        )

        assertEquals("SENT", delivered.status)
        assertEquals(1, captured.size)
    }

    @Test
    fun `RecipientSuppressedException is an IllegalStateException`() {
        val ex = RecipientSuppressedException("blocked@example.com")

        assertTrue(ex is IllegalStateException)
        assertEquals("收件人已退订，禁止外发：blocked@example.com", ex.message)
    }

    private fun captureSent(account: MailSenderAccount): MimeMessage {
        val captured = mutableListOf<MimeMessage>()
        val factory = Mockito.mock(SmtpSenderFactory::class.java)
        val sender = object : JavaMailSenderImpl() {
            override fun send(mimeMessage: MimeMessage) {
                captured += mimeMessage
            }
        }
        Mockito.`when`(factory.getSender(account)).thenReturn(sender)

        SmtpMailDeliveryService(factory, disabledTokenService, mailContentService, emailSuppressionService).send(
            account,
            ComposedMail("recipient@example.com", "Subject", "Body", messageId = "msg-1")
        )

        return captured.single()
    }

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    /** Mockito.eq() 对 Kotlin 非空参数会返回 null；传真实默认值实例占位。 */
    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value


    private fun testAccount(senderDisplayName: String? = null): MailSenderAccount =
        MailSenderAccount(
            accountCode = "test_acct",
            senderEmail = "test@example.com",
            senderName = "Test",
            senderTitle = null,
            senderDisplayName = senderDisplayName,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "test@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "test@example.com",
            imapPassword = "secret"
        )
}
