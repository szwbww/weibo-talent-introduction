package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.template.service.ComposeTemplateRenderResult
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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
        val expert = ExpertProfile(
            orcidId = "0000-0001",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null
        )
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
                templateCode = eqValue("INTRODUCTION"),
                variables = anyValue(emptyMap()),
                variantSeed = Mockito.anyInt()
            )
        ).thenReturn(
            ComposeTemplateRenderResult(
                subject = "Research Collaboration Opportunity",
                body = "Rendered body",
                mailType = "INTRODUCTION"
            )
        )

        val mail = composer.compose("chenjj", expert)

        assertEquals("expert@example.com", mail.to)
        assertEquals("Research Collaboration Opportunity", mail.subject)
        assertEquals("Rendered body", mail.body)
        assertNotNull(mail.messageId)
        assertTrue(mail.messageId!!.matches(Regex("<intro-0000-0001-[0-9a-f-]+@qftechtalent\\.com>")))

        val variablesCaptor = ArgumentCaptor.forClass(Map::class.java as Class<Map<String, String>>)
        val seedCaptor = ArgumentCaptor.forClass(Int::class.java)
        Mockito.verify(templateService).renderByCode(
            eqValue("INTRODUCTION"),
            captureValue(variablesCaptor, emptyMap<String, String>()),
            captureValue(seedCaptor, 0)
        )
        assertEquals(introductionVariables(expert), variablesCaptor.value)
        assertEquals(expert.orcidId.hashCode(), seedCaptor.value)
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
        Mockito.`when`(templateService.render(eqValue(7L), anyValue(emptyMap()), Mockito.anyInt()))
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

        val variablesCaptor = ArgumentCaptor.forClass(Map::class.java as Class<Map<String, String>>)
        val seedCaptor = ArgumentCaptor.forClass(Int::class.java)
        Mockito.verify(templateService).render(
            eqValue(7L),
            captureValue(variablesCaptor, emptyMap<String, String>()),
            captureValue(seedCaptor, 0)
        )
        assertEquals(variables, variablesCaptor.value)
        assertEquals(expert.orcidId.hashCode(), seedCaptor.value)
        Mockito.verify(templateService, Mockito.never()).renderByCode(
            Mockito.anyString(),
            Mockito.anyMap(),
            Mockito.anyInt()
        )
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
        Mockito.`when`(templateService.renderByCode(eqValue("INTRODUCTION"), anyValue(emptyMap()), Mockito.anyInt()))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Subject",
                    body = "Body",
                    mailType = "INTRODUCTION"
                )
            )

        composer.compose("chenjj", expert)

        val variablesCaptor = ArgumentCaptor.forClass(Map::class.java as Class<Map<String, String>>)
        val seedCaptor = ArgumentCaptor.forClass(Int::class.java)
        Mockito.verify(templateService).renderByCode(
            eqValue("INTRODUCTION"),
            captureValue(variablesCaptor, emptyMap<String, String>()),
            captureValue(seedCaptor, 0)
        )
        assertEquals(expectedVariables, variablesCaptor.value)
        assertEquals(expert.orcidId.hashCode(), seedCaptor.value)
        assertEquals("", expectedVariables["researchFields"])
        assertEquals("", expectedVariables["institution"])
        assertEquals("0000-0002", expectedVariables["expertName"])
    }

    @Test
    fun `compose selects same subject and body for same orcid on retry`() {
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
            orcidId = "0000-0003",
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null
        )
        val seed = expert.orcidId.hashCode()
        Mockito.`when`(templateService.renderByCode(eqValue("INTRODUCTION"), anyValue(emptyMap()), eqValue(seed)))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Variant subject",
                    body = "Variant body",
                    mailType = "INTRODUCTION"
                )
            )

        val first = composer.compose("chenjj", expert)
        val second = composer.compose("chenjj", expert)

        assertEquals(first.subject, second.subject)
        assertEquals(first.body, second.body)
        assertNotEquals(first.messageId, second.messageId)
    }

    @Test
    fun `compose passes raw hashCode seed when orcidId hash is Int MIN_VALUE`() {
        val orcidId = "polygenelubricants"
        assertEquals(Int.MIN_VALUE, orcidId.hashCode())
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
            orcidId = orcidId,
            email = "expert@example.com",
            givenNames = "Ada",
            familyNames = "Lovelace",
            country = null,
            keyword = null,
            employment = null
        )
        Mockito.`when`(templateService.renderByCode(eqValue("INTRODUCTION"), anyValue(emptyMap()), eqValue(Int.MIN_VALUE)))
            .thenReturn(
                ComposeTemplateRenderResult(
                    subject = "Stable subject",
                    body = "Stable body",
                    mailType = "INTRODUCTION"
                )
            )

        val mail = composer.compose("chenjj", expert)

        assertEquals("Stable subject", mail.subject)
        assertEquals("Stable body", mail.body)
        assertNotNull(mail.messageId)
        assertTrue(mail.messageId!!.contains("polygenelubricants"))
    }

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }
}
