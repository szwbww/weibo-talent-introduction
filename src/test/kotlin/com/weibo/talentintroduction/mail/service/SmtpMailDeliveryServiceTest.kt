package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.SmtpErrorCategory
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.internet.MimeMessage
import javax.mail.SendFailedException

class SmtpMailDeliveryServiceTest {
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

        val delivered = SmtpMailDeliveryService(factory).send(
            account,
            ComposedMail("bad@example.com", "Subject", "Body", messageId = "msg-1")
        )

        assertEquals("FAILED", delivered.status)
        assertEquals(SmtpErrorCategory.PERMANENT, delivered.errorCategory)
        assertEquals(550, delivered.smtpResponseCode)
    }

    private fun testAccount(): MailSenderAccount =
        MailSenderAccount(
            accountCode = "test_acct",
            senderEmail = "test@example.com",
            senderName = "Test",
            senderTitle = null,
            senderDisplayName = null,
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
