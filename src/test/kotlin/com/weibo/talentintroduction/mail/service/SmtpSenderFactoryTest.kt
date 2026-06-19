package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SmtpSenderFactoryTest {
    private val factory = SmtpSenderFactory()

    @Test
    fun `getSender returns same instance for same account`() {
        val account = testAccount()

        val first = factory.getSender(account)
        val second = factory.getSender(account)

        assertSame(first, second)
    }

    @Test
    fun `getSender returns new instance when smtp host changes`() {
        val account = testAccount()
        val first = factory.getSender(account)

        val changed = account.copy(smtpHost = "smtp2.example.com")
        val second = factory.getSender(changed)

        assertNotSame(first, second)
    }

    @Test
    fun `evict forces new instance on next getSender`() {
        val account = testAccount()
        val first = factory.getSender(account)

        factory.evict(account.accountCode)
        val second = factory.getSender(account)

        assertNotSame(first, second)
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
