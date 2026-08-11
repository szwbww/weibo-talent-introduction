package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
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
