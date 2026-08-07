package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MeetingInvitationMailComposerTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val templateService = Mockito.mock(MailComposeTemplateService::class.java)
    private val composer = MeetingInvitationMailComposer(accountService, templateService)

    @Test
    fun `composes meeting invitation mail from account and template`() {
        val expert = ExpertProfile(
            orcidId = "0000-0001",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null
        )
        val expectedSeed = MailComposeTemplateService.variantSeedFor(expert.orcidId, expert.email)
        Mockito.`when`(accountService.getEnabledAccount("chenjj"))
            .thenReturn(
                MailSenderAccount(
                    accountCode = "chenjj",
                    senderEmail = "chenjj@qftechtalent.com",
                    senderName = "Chen",
                    senderTitle = "Customer Care Officer",
                    senderDisplayName = "Chen",
                    teamName = "Qingfei Tech Talent Team",
                    countryName = "China",
                    smtpHost = "smtp.example.com",
                    smtpPort = 465,
                    smtpUsername = "chenjj@qftechtalent.com",
                    smtpPassword = "secret",
                    imapHost = "imap.example.com",
                    imapPort = 993,
                    imapUsername = "chenjj@qftechtalent.com",
                    imapPassword = "secret"
                )
            )
        Mockito.`when`(
            templateService.renderByCode(
                templateCode = "MEETING_INVITATION",
                variables = mapOf("senderDisplayName" to "Chen"),
                variantSeed = expectedSeed
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Follow-up on the Talent Program",
                body = "Meeting invitation body",
                mailType = "MEETING_INVITATION"
            )
        )

        val mail = composer.compose(
            "chenjj",
            expert
        )

        assertEquals("expert@example.com", mail.to)
        assertEquals("Follow-up on the Talent Program", mail.subject)
        assertEquals("Meeting invitation body", mail.body)
        assertNotNull(mail.messageId)
        assertTrue(
            mail.messageId!!.matches(
                Regex("^<meeting-invitation-0000-0001-[0-9a-f-]{36}@qftechtalent\\.com>$")
            ),
            "unexpected messageId: ${mail.messageId}"
        )
        Mockito.verify(templateService).renderByCode(
            templateCode = "MEETING_INVITATION",
            variables = mapOf("senderDisplayName" to "Chen"),
            variantSeed = expectedSeed
        )
    }
}
