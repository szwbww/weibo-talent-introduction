package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class IntroductionMailComposerTest {
    private val accountService = Mockito.mock(MailSenderAccountService::class.java)
    private val templateService = Mockito.mock(MailComposeTemplateService::class.java)
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
            templateService.renderByCode(
                templateCode = "INTRODUCTION",
                variables = mapOf(
                    "senderEmail" to "chenjj@qftechtalent.com",
                    "senderName" to "Chen",
                    "senderTitle" to "Customer Care Officer",
                    "teamName" to "Qingfei Tech Talent Team",
                    "countryName" to "China"
                )
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Research Collaboration Opportunity",
                body = "Rendered body",
                mailType = "INTRODUCTION"
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

    @Test
    fun `composes introduction mail using template id when provided`() {
        Mockito.`when`(accountService.getEnabledAccount("chenjj"))
            .thenReturn(
                MailSenderAccount(
                    accountCode = "chenjj",
                    senderEmail = "chenjj@qftechtalent.com",
                    senderName = "Chen",
                    senderTitle = null,
                    senderDisplayName = "Chen",
                    teamName = null,
                    countryName = null,
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
        val variables = mapOf(
            "senderEmail" to "chenjj@qftechtalent.com",
            "senderName" to "Chen",
            "senderTitle" to "",
            "teamName" to "",
            "countryName" to ""
        )
        Mockito.`when`(templateService.render(7L, variables))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Custom intro",
                    body = "Custom body",
                    mailType = "INTRODUCTION"
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
            ),
            templateId = 7L
        )

        assertEquals("Custom intro", mail.subject)
        assertEquals("Custom body", mail.body)
        Mockito.verify(templateService).render(7L, variables)
        Mockito.verify(templateService, Mockito.never()).renderByCode(Mockito.anyString(), Mockito.anyMap())
    }
}
