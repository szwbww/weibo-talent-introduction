package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelfCheckProbeDetectorTest {
    private val detector = SelfCheckProbeDetector()

    /** I-3 夹具：同一物理组的 owner + 别名（别名归属 owner），以及一个其它物理组的账号。 */
    private val owner = account("owner", "sender@qftechtalent.com")
    private val alias = account("alias", "alias@qftechtalent.com")
    private val independent = account("independent", "independent@qftechtalent.com")
    private val group = listOf(owner, alias)

    @Test
    fun `owner probe from the owner address with the full generated subject is a probe`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] owner 1234567890",
                groupMembers = group
            )
        )
    }

    @Test
    fun `alias probe fetched through the shared owner mailbox is a probe`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "alias@qftechtalent.com",
                subject = "[self-check] alias 1788498000276",
                groupMembers = group
            )
        )
    }

    @Test
    fun `spaced self-check tag is still the generated subject`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "alias@qftechtalent.com",
                subject = "[ self - check ] alias 123",
                groupMembers = group
            )
        )
    }

    @Test
    fun `sender address match is case insensitive`() {
        assertTrue(
            detector.isSelfCheckProbe(
                from = "  SENDER@QFTechTalent.com ",
                subject = "[self-check] owner 1234567890",
                groupMembers = group
            )
        )
    }

    @Test
    fun `probe of another physical group is not a probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "independent@qftechtalent.com",
                subject = "[self-check] independent 123",
                groupMembers = group
            )
        )
    }

    @Test
    fun `sender address of one member with another member account code is not a probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] alias 123",
                groupMembers = group
            )
        )
    }

    @Test
    fun `Re prefix is not a probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "Re: [self-check] owner 123",
                groupMembers = group
            )
        )
    }

    @Test
    fun `subject with the tag but no complete account code and timestamp is not a probe`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check]",
                groupMembers = group
            )
        )
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] owner",
                groupMembers = group
            )
        )
    }

    @Test
    fun `timestamp must be decimal digits and nothing else`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] owner 123abc",
                groupMembers = group
            )
        )
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] owner 123 456",
                groupMembers = group
            )
        )
    }

    @Test
    fun `missing from or subject is not a probe`() {
        assertFalse(detector.isSelfCheckProbe(from = null, subject = "[self-check] owner 123", groupMembers = group))
        assertFalse(detector.isSelfCheckProbe(from = "  ", subject = "[self-check] owner 123", groupMembers = group))
        assertFalse(detector.isSelfCheckProbe(from = "sender@qftechtalent.com", subject = null, groupMembers = group))
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "Talent Program",
                groupMembers = group
            )
        )
    }

    @Test
    fun `empty group matches nothing`() {
        assertFalse(
            detector.isSelfCheckProbe(
                from = "sender@qftechtalent.com",
                subject = "[self-check] owner 123",
                groupMembers = emptyList()
            )
        )
    }

    private fun account(accountCode: String, senderEmail: String): MailSenderAccount =
        MailSenderAccount(
            accountCode = accountCode,
            senderEmail = senderEmail,
            senderName = accountCode,
            senderTitle = null,
            senderDisplayName = null,
            teamName = null,
            countryName = null,
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = senderEmail,
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = senderEmail,
            imapPassword = "secret"
        )
}
