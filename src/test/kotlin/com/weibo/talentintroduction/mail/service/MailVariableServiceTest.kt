package com.weibo.talentintroduction.mail.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.config.UnsubscribeProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class MailVariableServiceTest {
    private val expertSearchService = Mockito.mock(ExpertSearchService::class.java)
    private val mailComposeTemplateService = MailComposeTemplateService(
        Mockito.mock(MailComposeTemplateRepository::class.java),
        Mockito.mock(MailComposeTemplateBlockRepository::class.java),
        Mockito.mock(QaRuleRepository::class.java),
        Mockito.mock(ReplySnippetRepository::class.java),
        ObjectMapper(),
        Mockito.mock(MailVariableService::class.java),
        Mockito.mock(ExpertContactRepository::class.java),
        Mockito.mock(MailSenderAccountService::class.java),
        ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), Mockito.mock(MailVariableService::class.java))
    )
    private val service = MailVariableService(expertSearchService, mailComposeTemplateService)
    private val serviceWithUnsubscribe = MailVariableService(
        expertSearchService,
        mailComposeTemplateService,
        UnsubscribeTokenService(UnsubscribeProperties(baseUrl = "https://example.com", secret = "secret"))
    )

    private val account = MailSenderAccount(
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

    private val expert = ExpertProfile(
        orcidId = "0000-0001",
        email = "expert@example.com",
        givenNames = "Ada",
        familyNames = "Lovelace",
        country = "UK",
        keyword = "computing",
        employment = "Professor",
        researchFields = "Mathematics",
        institution = "Oxford"
    )

    private val contact = ExpertContact(
        id = 1,
        campaignId = 1,
        orcidId = "0000-0001",
        expertEmail = "expert@example.com",
        expertName = "Ada Lovelace",
        currentStatus = "INTRO_SENT",
        currentIndexLevel = "CANDIDATE"
    )

    @Test
    fun `buildVariables returns empty expert values when profile is null`() {
        val variables = service.buildVariables(account, null)

        assertEquals(MailVariableService.VARIABLE_LABELS.keys, variables.keys)
        MailVariableService.EXPERT_KEYS.forEach { key ->
            assertEquals("", variables[key], "expected empty value for $key")
        }
        assertEquals("Chen", variables["senderName"])
    }

    @Test
    fun `renderForContact replaces placeholder with expert value`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = service.renderForContact(
            "Dear \${expertFamilyName}, welcome from \${senderName}",
            account,
            contact
        )

        assertEquals("Dear Lovelace, welcome from Chen", rendered)
        assertFalse(rendered.contains("\${"))
    }

    @Test
    fun `renderForContact replaces unsubscribe url with signed link`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = serviceWithUnsubscribe.renderForContact(
            "Unsubscribe: \${unsubscribeUrl}",
            account,
            contact
        )

        assertTrue(rendered.startsWith("Unsubscribe: https://example.com/u/unsubscribe?token="))
        assertFalse(rendered.contains("\${unsubscribeUrl}"))
    }

    @Test
    fun `renderForContact uses fallback when expert field is empty`() {
        val sparseExpert = expert.copy(familyNames = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(sparseExpert)

        val rendered = service.renderForContact(
            "Dear \${expertFamilyName|there}",
            account,
            contact
        )

        assertEquals("Dear there", rendered)
    }

    @Test
    fun `renderForContact preserves unknown placeholder`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = service.renderForContact("Value: \${unknownKey}", account, contact)

        assertEquals("Value: \${unknownKey}", rendered)
    }

    @Test
    fun `renderForContact falls back from APPLICATION to CANDIDATE index`() {
        val applicationContact = contact.copy(currentIndexLevel = "APPLICATION")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.APPLICATION))
            .thenReturn(null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = service.renderForContact("Institution: \${institution}", account, applicationContact)

        assertEquals("Institution: Oxford", rendered)
    }

    @Test
    fun `renderForContact degrades to empty values when ES lookup fails`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenThrow(RuntimeException("ES unavailable"))

        val rendered = service.renderForContact(
            "Dear \${expertFamilyName|there}, from \${senderName}",
            account,
            contact
        )

        assertEquals("Dear there, from Chen", rendered)
        assertFalse(rendered.contains("\${expertFamilyName"))
    }

    @Test
    fun `renderForContact is identity when text has no placeholders`() {
        val plain = "Static QA body without variables."
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = service.renderForContact(plain, account, contact)

        assertEquals(plain, rendered)
    }

    @Test
    fun `validatePlaceholders rejects whitespace-only fallback`() {
        val violations = service.validatePlaceholders("Dear \${expertFamilyName|   },")

        assertEquals(listOf("\${expertFamilyName|   }"), violations)
    }

    @Test
    fun `validatePlaceholders flags unknown and nullable without fallback`() {
        val violations = service.validatePlaceholders(
            "Hi \${senderName} and \${expertFamilyName} plus \${bogus}"
        )
        assertTrue(violations.contains("\${expertFamilyName}"))
        assertTrue(violations.contains("\${bogus}"))
        assertFalse(violations.contains("\${senderName}"))
    }

    @Test
    fun `validatePlaceholders accepts unsubscribe url without fallback`() {
        val violations = service.validatePlaceholders("Stop: \${unsubscribeUrl}")

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `renderPreview matches renderForContact output`() {
        val sparseExpert = expert.copy(familyNames = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(sparseExpert)

        val text = "Dear \${expertFamilyName|there}, from \${senderName}"
        val preview = service.renderPreview(text, account, contact)
        val direct = service.renderForContact(text, account, contact)

        assertEquals(direct, preview.rendered)
        assertEquals(listOf("expertFamilyName"), preview.fallbackKeys)
    }

    @Test
    fun `variableMetadata exposes labels for all keys`() {
        val metadata = service.variableMetadata()

        assertEquals(MailVariableService.VARIABLE_LABELS.size, metadata.size)
        assertTrue(metadata.any { it.key == "expertFamilyName" && it.label == "专家姓氏" })
        assertTrue(metadata.any { it.key == "unsubscribeUrl" && it.label == "退订链接" && !it.nullable })
    }

    @Test
    fun `variableMetadata maps twelve filterable es fields and seven null keys`() {
        val metadata = service.variableMetadata().associateBy { it.key }
        val filterableKeys = listOf(
            "expertFamilyName",
            "researchFields",
            "institution",
            "keyword",
            "expertCountry",
            "employment",
            "hIndex",
            "worksCount",
            "lastPublicationYear",
            "degree",
            "recentWorkTitle",
            "patentTitle"
        )
        filterableKeys.forEach { key ->
            assertTrue(metadata.containsKey(key), "missing key $key")
            assertTrue(!metadata[key]!!.esField.isNullOrBlank(), "expected esField for $key")
        }
        listOf(
            "senderEmail",
            "senderName",
            "senderTitle",
            "teamName",
            "countryName",
            "expertName",
            "unsubscribeUrl"
        ).forEach { key ->
            assertNull(metadata[key]?.esField, "expected null esField for $key")
        }
    }

    @Test
    fun `filterableEsFields extracts deduplicated es fields in stable order`() {
        val fields = service.filterableEsFields(
            "Hi \${institution} at \${institution} in \${expertCountry} from \${senderName}"
        )

        assertEquals(listOf("institution", "country"), fields)
    }

    @Test
    fun `renderPreview exposes variable states and unknown tokens only`() {
        val sparseExpert = expert.copy(familyNames = null, institution = "Oxford")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(sparseExpert)

        val preview = service.renderPreview(
            "Dear \${expertFamilyName|there}, at \${institution}, unknown \${bogus}",
            account,
            contact
        )

        assertEquals("Dear there, at Oxford, unknown \${bogus}", preview.rendered)
        assertEquals(listOf("expertFamilyName"), preview.fallbackKeys)
        assertEquals(listOf("\${bogus}"), preview.invalidTokens)
        assertFalse(preview.invalidTokens.contains("\${expertFamilyName|there}"))

        val familyName = preview.variables.single { it.key == "expertFamilyName" }
        assertTrue(familyName.usedFallback)
        assertFalse(familyName.filled)

        val institution = preview.variables.single { it.key == "institution" }
        assertTrue(institution.filled)
        assertFalse(institution.usedFallback)
    }
}
