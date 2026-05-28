package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.MailTemplateService
import com.weibo.talentintroduction.template.service.RenderedMailTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class IntroductionMailComposerTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val templateService = Mockito.mock(MailTemplateService::class.java)
    private val composer = IntroductionMailComposer(accountService, templateService)

    @Test
    fun `composes introduction mail from account and template`() {
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
            templateService.render(
                "INTRODUCTION",
                mapOf(
                    "senderEmail" to "chenjj@qftechtalent.com",
                    "senderName" to "Chen",
                    "senderTitle" to "Customer Care Officer",
                    "teamName" to "Qingfei Tech Talent Team",
                    "countryName" to "China"
                )
            )
        ).thenReturn(
            RenderedMailTemplate(
                subject = "Research Collaboration Opportunity",
                body = "Rendered body"
            )
        )

        val mail = composer.compose(
            "chenjj",
            ExpertProfile(
                orcidId = "0000-0001",
                email = "expert@example.com",
                givenNames = "Ada",
                familyNames = "Lovelace",
                country = null,
                keyword = null,
                employment = null
            )
        )

        assertEquals("expert@example.com", mail.to)
        assertEquals("Research Collaboration Opportunity", mail.subject)
        assertEquals("Rendered body", mail.body)
    }
}
