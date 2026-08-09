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
        ContentVariantService(Mockito.mock(ContentVariantRepository::class.java), MailPlaceholderService())
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
    fun `renderForContact uses contact email for unsubscribe url when profile email is empty`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert.copy(email = null))

        val rendered = serviceWithUnsubscribe.renderForContact(
            "Unsubscribe: \${unsubscribeUrl}",
            account,
            contact
        )

        assertTrue(rendered.startsWith("Unsubscribe: https://example.com/u/unsubscribe?token="))
        assertFalse(rendered.contains("\${unsubscribeUrl}"))
    }

    @Test
    fun `renderPreview shows preview unsubscribe url when token service is not configured`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val result = service.renderPreview(
            "Unsubscribe: \${unsubscribeUrl}",
            account,
            contact
        )

        assertEquals("Unsubscribe: https://example.com/u/unsubscribe?token=preview", result.rendered)
        assertTrue(
            result.variables.any {
                it.key == "unsubscribeUrl" &&
                    it.value == "https://example.com/u/unsubscribe?token=preview" &&
                    it.filled &&
                    !it.usedFallback
            }
        )
        assertFalse(result.fallbackKeys.contains("unsubscribeUrl"))
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

    @Test
    fun `renderHtmlForContact escapes special chars while renderForContact keeps raw`() {
        val specialAccount = account.copy(teamName = "A&B <Team>")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val text = service.renderForContact("From \${teamName}", specialAccount, contact)
        val html = service.renderHtmlForContact("From \${teamName}", specialAccount, contact)

        assertEquals("From A&B <Team>", text)
        assertEquals("From A&amp;B &lt;Team&gt;", html)
    }

    @Test
    fun `renderHtmlForContact preserves editor tags and escapes substituted values`() {
        val specialExpert = expert.copy(givenNames = "Ada &", familyNames = "Lovelace <PhD>")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(specialExpert)

        val rendered = service.renderHtmlForContact(
            "<p>Hello \${expertName}</p>",
            account,
            contact
        )

        assertEquals("<p>Hello Ada &amp; Lovelace &lt;PhD&gt;</p>", rendered)
        assertTrue(rendered.startsWith("<p>"))
        assertTrue(rendered.endsWith("</p>"))
    }

    @Test
    fun `renderHtmlForContact fallback and empty known keys match renderForContact semantics`() {
        val sparseExpert = expert.copy(familyNames = null, researchFields = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(sparseExpert)

        val template = "Dear \${expertFamilyName|there}, fields=\${researchFields}"
        val text = service.renderForContact(template, account, contact)
        val html = service.renderHtmlForContact(template, account, contact)

        assertEquals("Dear there, fields=", text)
        assertEquals(text, html)
    }

    @Test
    fun `renderHtmlForContact preserves unknown tokens like renderForContact`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val template = "Value: \${unknownKey}"
        val text = service.renderForContact(template, account, contact)
        val html = service.renderHtmlForContact(template, account, contact)

        assertEquals("Value: \${unknownKey}", text)
        assertEquals("Value: \${unknownKey}", html)
    }

    // ── ExpertRecipientNamePolicy tests (Phase 10 I-7) ──

    @Test
    fun `expertName uses given and family names`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "a@b.com",
            givenNames = "Ada", familyNames = "Lovelace",
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("Ada Lovelace", vars["expertName"])
        assertEquals("Lovelace", vars["expertFamilyName"])
    }

    @Test
    fun `expertName is empty when given and family are null`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "a@b.com",
            givenNames = null, familyNames = null,
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertName"])
    }

    @Test
    fun `expertName is empty for EMAIL dash prefix`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "a@b.com",
            givenNames = "EMAIL-6b9d5416e939bbe8ea0", familyNames = null,
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertName"])
    }

    @Test
    fun `expertName is empty when equal to ORCID`() {
        val expert = ExpertProfile(orcidId = "0000-0001-2345-6789-x", email = "a@b.com",
            givenNames = "0000-0001-2345-6789-x", familyNames = null,
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertName"])
    }

    @Test
    fun `expertName is empty when equal to email`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "test@example.com",
            givenNames = null, familyNames = "test@example.com",
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertName"])
    }

    @Test
    fun `expertFamilyName is empty for technical id`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "a@b.com",
            givenNames = "Ada", familyNames = "EMAIL-abc123",
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertFamilyName"])
    }

    @Test
    fun `expertName empty for name containing at sign`() {
        val expert = ExpertProfile(orcidId = "0000-0001", email = "a@b.com",
            givenNames = "test@example", familyNames = null,
            country = null, keyword = null, employment = null)
        val vars = service.buildVariables(null, expert)
        assertEquals("", vars["expertName"])
    }

    // ── preview/plain/HTML rendering consistency (Phase 10 I-8) ──

    @Test
    fun `renderPreview usedFallback true when expertName is empty`() {
        val noNameExpert = expert.copy(givenNames = null, familyNames = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(noNameExpert)

        val preview = service.renderPreview(
            "Dear \${expertName|Professor},",
            account, contact
        )
        assertEquals("Dear Professor,", preview.rendered)
        assertTrue(preview.fallbackKeys.contains("expertName"))
        val en = preview.variables.single { it.key == "expertName" }
        assertTrue(en.usedFallback)
        assertFalse(en.filled)
    }

    @Test
    fun `renderPreview usedFallback true when expertName is EMAIL prefix`() {
        val emailExpert = expert.copy(givenNames = "EMAIL-abc123", familyNames = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(emailExpert)

        val preview = service.renderPreview(
            "Dear \${expertName|Professor},",
            account, contact
        )
        assertEquals("Dear Professor,", preview.rendered)
        val en = preview.variables.single { it.key == "expertName" }
        assertTrue(en.usedFallback)
    }

    @Test
    fun `plain HTML and preview render same expertName for normal profile`() {
        val normal = expert.copy(givenNames = "Ada", familyNames = "Lovelace")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(normal)

        val template = "Hi \${expertName|there}, from \${teamName}."
        val plain = service.renderForContact(template, account, contact)
        val html = service.renderHtmlForContact(template, account, contact)
        val preview = service.renderPreview(template, account, contact)

        assertTrue(plain.contains("Ada Lovelace"))
        assertTrue(html.contains("Ada Lovelace"))
        assertTrue(preview.rendered.contains("Ada Lovelace"))
        assertFalse(plain.contains("there"))
        assertFalse(html.contains("there"))
        assertFalse(preview.rendered.contains("there"))
    }

    @Test
    fun `plain HTML and preview render Professor fallback when name is technical`() {
        val techExpert = expert.copy(givenNames = "EMAIL-6b9d5416", familyNames = null)
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(techExpert)

        val template = "Dear \${expertName|Professor}, welcome."
        val plain = service.renderForContact(template, account, contact)
        val html = service.renderHtmlForContact(template, account, contact)
        val preview = service.renderPreview(template, account, contact)

        assertTrue(plain.startsWith("Dear Professor,"))
        assertTrue(html.startsWith("Dear Professor,"))
        assertTrue(preview.rendered.startsWith("Dear Professor,"))
    }

    // ── primaryResearchField (P1 I-7, I-8) ──

    @Test
    fun `buildVariables derives primaryResearchField as first research field segment`() {
        val multiField = expert.copy(researchFields = "Machine Learning, Data Mining, NLP")
        val variables = service.buildVariables(account, multiField)

        assertEquals("Machine Learning", variables["primaryResearchField"])
        assertEquals("Machine Learning, Data Mining, NLP", variables["researchFields"])
    }

    @Test
    fun `buildVariables primaryResearchField is empty when researchFields is null or blank`() {
        assertEquals("", service.buildVariables(account, expert.copy(researchFields = null))["primaryResearchField"])
        assertEquals("", service.buildVariables(account, expert.copy(researchFields = "  "))["primaryResearchField"])
    }

    @Test
    fun `buildVariables primaryResearchField trims the first segment`() {
        val variables = service.buildVariables(account, expert.copy(researchFields = "  Machine Learning , Data Mining"))
        assertEquals("Machine Learning", variables["primaryResearchField"])
    }

    @Test
    fun `variableMetadata exposes primaryResearchField with researchFields es field and example`() {
        val metadata = service.variableMetadata().associateBy { it.key }
        assertTrue(metadata.containsKey("primaryResearchField"))

        val meta = metadata["primaryResearchField"]!!
        assertEquals("researchFields", meta.esField)
        assertTrue(meta.nullable)
        assertTrue(meta.example.isNotBlank())
        assertEquals("主要研究方向", meta.label)
    }

    @Test
    fun `renderForContact replaces primaryResearchField with derived value`() {
        val multiField = expert.copy(researchFields = "Machine Learning, Data Mining")
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(multiField)

        val rendered = service.renderForContact("Focus: \${primaryResearchField}", account, contact)

        assertEquals("Focus: Machine Learning", rendered)
        assertFalse(rendered.contains("\${"))
    }
}
