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
import com.weibo.talentintroduction.campaign.domain.ExpertMaterialStatusRecord
import com.weibo.talentintroduction.campaign.repository.ExpertMaterialStatusRepository
import com.weibo.talentintroduction.campaign.service.ExpertMaterialCode
import com.weibo.talentintroduction.campaign.service.ExpertMaterialService
import org.junit.jupiter.api.Assertions.assertThrows
import org.mockito.ArgumentCaptor
import java.time.LocalDateTime
import java.util.Optional
import java.util.NoSuchElementException

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
    fun `variableMetadata maps twelve filterable es fields and eight null keys`() {
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
            "pendingExpertMaterials",
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

    // ── cold outreach unsubscribe line (plan 01 T-4) ──

    private val unsubscribeLine =
        "\n\n---\nIf you would prefer not to receive further emails from us, you can unsubscribe here: \${unsubscribeUrl}"

    @Test
    fun `cold outreach unsubscribe line renders a real url`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = serviceWithUnsubscribe.renderForContact(unsubscribeLine, account, contact)

        assertTrue(rendered.contains("/u/unsubscribe?token="), "expected a real unsubscribe url")
        assertFalse(rendered.contains("\${unsubscribeUrl}"), "placeholder must be replaced")
    }

    @Test
    fun `cold outreach unsubscribe line renders empty when token service disabled`() {
        val serviceWithDisabledUnsubscribe = MailVariableService(
            expertSearchService,
            mailComposeTemplateService,
            UnsubscribeTokenService(UnsubscribeProperties(baseUrl = "", secret = ""))
        )
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)

        val rendered = serviceWithDisabledUnsubscribe.renderForContact(unsubscribeLine, account, contact)

        assertFalse(rendered.contains("\${unsubscribeUrl}"), "placeholder must not survive rendering")
    }

    // ── ExpertMaterialService semantics (plan 01 I1-1..I1-6) ──

    private val materialStatusRepository = Mockito.mock(ExpertMaterialStatusRepository::class.java)
    private val materialContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val materialService = ExpertMaterialService(materialStatusRepository, materialContactRepository)

    private fun statusRow(id: Long, code: String, status: String) = ExpertMaterialStatusRecord(
        id = id,
        expertContactId = 1L,
        materialCode = code,
        materialStatus = status,
        createdAt = LocalDateTime.of(2026, 8, 31, 10, 0),
        updatedAt = LocalDateTime.of(2026, 8, 31, 10, 0)
    )

    @Test
    fun `listMaterials returns exactly 7 fixed catalog items all PENDING when no rows`() {
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        val items = materialService.listMaterials(1L)

        assertEquals(
            listOf("CV", "PASSPORT", "DEGREE", "EMPLOYMENT", "PUBLICATIONS", "PATENTS", "RESEARCH"),
            items.map { it.code }
        )
        assertEquals(listOf("简历", "护照", "学位", "工作", "出版", "专利", "研究"), items.map { it.label })
        assertEquals(
            listOf("PENDING", "PENDING", "PENDING", "PENDING", "PENDING", "PENDING", "PENDING"),
            items.map { it.status }
        )
    }

    @Test
    fun `listMaterials resolves stored rows and treats missing rows as PENDING`() {
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(statusRow(1, "CV", "PROVIDED"), statusRow(2, "EMPLOYMENT", "DECLINED"))
        )

        val byCode = materialService.listMaterials(1L).associateBy { it.code }

        assertEquals("PROVIDED", byCode["CV"]!!.status)
        assertEquals("DECLINED", byCode["EMPLOYMENT"]!!.status)
        assertEquals("PENDING", byCode["PASSPORT"]!!.status)
    }

    @Test
    fun `updateStatus PROVIDED inserts new row without id`() {
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(materialStatusRepository.findByExpertContactIdAndMaterialCode(1L, "CV")).thenReturn(null)
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(statusRow(9, "CV", "PROVIDED"))
        )

        materialService.updateStatus(1L, "CV", "PROVIDED")

        val captured = ArgumentCaptor.forClass(ExpertMaterialStatusRecord::class.java)
        Mockito.verify(materialStatusRepository).save(captured.capture())
        assertEquals(null, captured.value.id)
        assertEquals("CV", captured.value.materialCode)
        assertEquals("PROVIDED", captured.value.materialStatus)
    }

    @Test
    fun `updateStatus on existing row preserves id and updates status`() {
        val existing = statusRow(7, "CV", "PROVIDED")
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(materialStatusRepository.findByExpertContactIdAndMaterialCode(1L, "CV"))
            .thenReturn(existing)
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(existing.copy(materialStatus = "DECLINED"))
        )

        materialService.updateStatus(1L, "CV", "DECLINED")

        val captured = ArgumentCaptor.forClass(ExpertMaterialStatusRecord::class.java)
        Mockito.verify(materialStatusRepository).save(captured.capture())
        assertEquals(7L, captured.value.id)
        assertEquals("DECLINED", captured.value.materialStatus)
    }

    @Test
    fun `updateStatus PENDING deletes existing row and never saves`() {
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))
        Mockito.`when`(materialStatusRepository.findByExpertContactIdAndMaterialCode(1L, "CV"))
            .thenReturn(statusRow(7, "CV", "PROVIDED"))
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        val items = materialService.updateStatus(1L, "CV", "PENDING")

        Mockito.verify(materialStatusRepository).deleteById(7L)
        Mockito.verify(materialStatusRepository, Mockito.never()).save(Mockito.any())
        assertTrue(items.all { it.status == "PENDING" })
    }

    @Test
    fun `updateStatus rejects unknown code and status before any repository write`() {
        Mockito.`when`(materialContactRepository.findById(1L)).thenReturn(Optional.of(contact))

        assertThrows(IllegalArgumentException::class.java) {
            materialService.updateStatus(1L, "UNKNOWN", "PROVIDED")
        }
        assertThrows(IllegalArgumentException::class.java) {
            materialService.updateStatus(1L, "CV", "DONE")
        }

        Mockito.verifyNoInteractions(materialStatusRepository)
    }

    @Test
    fun `updateStatus rejects missing contact with NoSuchElementException`() {
        Mockito.`when`(materialContactRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows(NoSuchElementException::class.java) {
            materialService.listMaterials(99L)
        }
    }

    private val expectedAllPending = listOf(
        "1. Your latest English CV, including education, employment, publications, patents, projects, awards, and honors.",
        "2. A copy of the personal information page of your valid passport.",
        "3. Your PhD degree certificate. Master’s and bachelor’s degree certificates may also be required.",
        "4. Proof of your current position and recent employment, such as employment letters, contracts, appointment letters, or official institutional documents.",
        "5. A list of your recent publications, patents, projects, awards, and other professional achievements.",
        "6. Supporting certificates for important patents, awards, qualifications, or editorial/reviewer roles, if available.",
        "7. A brief description of your recent research achievements and proposed research topic."
    ).joinToString("\n")

    @Test
    fun `renderPendingMaterials with all pending returns 7 numbered lines verbatim`() {
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(emptyList())

        assertEquals(expectedAllPending, materialService.renderPendingMaterials(1L))
    }

    @Test
    fun `renderPendingMaterials filters PROVIDED and DECLINED and renumbers from 1`() {
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(
            listOf(
                statusRow(1, "CV", "PROVIDED"),
                statusRow(2, "PASSPORT", "PROVIDED"),
                statusRow(3, "EMPLOYMENT", "DECLINED")
            )
        )

        val rendered = materialService.renderPendingMaterials(1L)

        assertEquals(
            "1. Your PhD degree certificate. Master’s and bachelor’s degree certificates may also be required.\n" +
                "2. A list of your recent publications, patents, projects, awards, and other professional achievements.\n" +
                "3. Supporting certificates for important patents, awards, qualifications, or editorial/reviewer roles, if available.\n" +
                "4. A brief description of your recent research achievements and proposed research topic.",
            rendered
        )
    }

    @Test
    fun `renderPendingMaterials returns empty when every item is provided or declined`() {
        Mockito.`when`(materialStatusRepository.findAllByExpertContactId(1L)).thenReturn(
            ExpertMaterialCode.entries.mapIndexed { index, code ->
                statusRow(index + 1L, code.name, if (index % 2 == 0) "PROVIDED" else "DECLINED")
            }
        )

        assertEquals("", materialService.renderPendingMaterials(1L))
    }

    // ── pendingExpertMaterials variable (plan 01 I1-5..I1-8) ──

    private val materialServiceForVars = Mockito.mock(ExpertMaterialService::class.java)
    private val serviceWithMaterials = MailVariableService(
        expertSearchService,
        mailComposeTemplateService,
        null,
        MailPlaceholderService(),
        materialServiceForVars
    )

    @Test
    fun `buildVariables always contains pendingExpertMaterials and is empty without contact or service`() {
        val withoutContact = service.buildVariables(account, expert)
        assertTrue(withoutContact.containsKey("pendingExpertMaterials"))
        assertEquals("", withoutContact["pendingExpertMaterials"])

        val withContactNoService = service.buildVariables(account, expert, contact = contact)
        assertEquals("", withContactNoService["pendingExpertMaterials"])
    }

    @Test
    fun `renderForContact replaces pendingExpertMaterials for real contact without residue`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)
        Mockito.`when`(materialServiceForVars.renderPendingMaterials(1L))
            .thenReturn("1. Your PhD degree certificate. Master’s and bachelor’s degree certificates may also be required.")

        val rendered = serviceWithMaterials.renderForContact(
            "Materials:\n\${pendingExpertMaterials}",
            account,
            contact
        )

        assertTrue(rendered.contains("1. Your PhD degree certificate."))
        assertFalse(rendered.contains("\${pendingExpertMaterials}"))
    }

    @Test
    fun `renderHtmlForContact and renderPreview replace pendingExpertMaterials without residue`() {
        Mockito.`when`(expertSearchService.findByOrcidId("0000-0001", ExpertIndexLevel.CANDIDATE))
            .thenReturn(expert)
        Mockito.`when`(materialServiceForVars.renderPendingMaterials(1L))
            .thenReturn("1. Your PhD degree certificate.\n2. A list of your recent publications.")

        val html = serviceWithMaterials.renderHtmlForContact(
            "Materials:\n\${pendingExpertMaterials}",
            account,
            contact
        )
        val preview = serviceWithMaterials.renderPreview(
            "Materials:\n\${pendingExpertMaterials}",
            account,
            contact
        )

        assertTrue(html.contains("1. Your PhD degree certificate."))
        assertFalse(html.contains("\${pendingExpertMaterials}"))
        assertTrue(preview.rendered.contains("1. Your PhD degree certificate."))
        assertFalse(preview.rendered.contains("\${pendingExpertMaterials}"))
    }

    @Test
    fun `pendingExpertMaterials metadata is non nullable with fixed label and null esField`() {
        val meta = service.variableMetadata().associateBy { it.key }
        val m = meta["pendingExpertMaterials"]!!
        assertEquals("待专家提供材料", m.label)
        assertFalse(m.nullable)
        assertNull(m.esField)
        assertTrue(m.example.isNotBlank())

        assertEquals(emptyList<String>(), service.validatePlaceholders("\${pendingExpertMaterials}"))
    }
}
