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

    private fun senderVariables(
        senderEmail: String = "chenjj@qftechtalent.com",
        senderName: String = "Chen",
        senderTitle: String = "Customer Care Officer",
        teamName: String = "Qingfei Tech Talent Team",
        countryName: String = "China"
    ) = mapOf(
        "senderEmail" to senderEmail,
        "senderName" to senderName,
        "senderTitle" to senderTitle,
        "teamName" to teamName,
        "countryName" to countryName
    )

    private fun expertVariables(
        expert: ExpertProfile
    ) = mapOf(
        "expertName" to expert.displayName,
        "expertFamilyName" to expert.familyNames.orEmpty(),
        "researchFields" to expert.researchFields.orEmpty(),
        "institution" to expert.institution.orEmpty(),
        "keyword" to expert.keyword.orEmpty(),
        "expertCountry" to expert.country.orEmpty()
    )

    private fun introductionVariables(
        expert: ExpertProfile,
        senderEmail: String = "chenjj@qftechtalent.com",
        senderName: String = "Chen",
        senderTitle: String = "Customer Care Officer",
        teamName: String = "Qingfei Tech Talent Team",
        countryName: String = "China"
    ) = senderVariables(senderEmail, senderName, senderTitle, teamName, countryName) + expertVariables(expert)

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
                variables = introductionVariables(
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
        val expert = ExpertProfile(
            orcidId = "0000-0001",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null
        )
        val variables = introductionVariables(expert).toMutableMap().apply {
            put("senderTitle", "")
            put("teamName", "")
            put("countryName", "")
        }
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
            expert,
            templateId = 7L
        )

        assertEquals("Custom intro", mail.subject)
        assertEquals("Custom body", mail.body)
        Mockito.verify(templateService).render(7L, variables)
        Mockito.verify(templateService, Mockito.never()).renderByCode(Mockito.anyString(), Mockito.anyMap())
    }

    @Test
    fun `expert variables use empty string when profile fields are null`() {
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
        val expert = ExpertProfile(
            orcidId = "0000-0002",
            email = "expert@example.com",
            givenNames = null,
            familyNames = null,
            country = null,
            keyword = null,
            employment = null,
            researchFields = null,
            institution = null
        )
        val expectedVariables = introductionVariables(expert).toMutableMap().apply {
            put("senderTitle", "")
            put("teamName", "")
            put("countryName", "")
        }
        Mockito.`when`(templateService.renderByCode("INTRODUCTION", expectedVariables))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Subject",
                    body = "Body",
                    mailType = "INTRODUCTION"
                )
            )

        composer.compose("chenjj", expert)

        Mockito.verify(templateService).renderByCode("INTRODUCTION", expectedVariables)
        assertEquals("", expectedVariables["researchFields"])
        assertEquals("", expectedVariables["institution"])
        assertEquals("0000-0002", expectedVariables["expertName"])
    }
}
