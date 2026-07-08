package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.mail.service.MailVariableService
import com.weibo.talentintroduction.mail.service.PreviewVariableItem
import com.weibo.talentintroduction.mail.service.RenderPreviewResult
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.domain.ReplySnippet
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.domain.ComposeBlockType
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class MailComposeTemplateServiceTest {
    private val templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java)
    private val blockRepository = Mockito.mock(MailComposeTemplateBlockRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetRepository = Mockito.mock(ReplySnippetRepository::class.java)
    private val mailVariableService = Mockito.mock(MailVariableService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val objectMapper = ObjectMapper()
    private val service = MailComposeTemplateService(
        templateRepository,
        blockRepository,
        qaRuleRepository,
        replySnippetRepository,
        objectMapper,
        mailVariableService,
        expertContactRepository,
        mailSenderAccountService
    )

    @Test
    fun `renderByCode renders custom text variables and returns mail type`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRODUCTION"))
            .thenReturn(
                MailComposeTemplate(
                    id = 10,
                    templateCode = "INTRODUCTION",
                    templateName = "Intro",
                    subject = "Hello ${'$'}{senderName}",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(10))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 10,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Dear Professor,\n\n${'$'}{senderName} from ${'$'}{teamName}"
                    )
                )
            )

        val rendered = service.renderByCode(
            templateCode = "INTRODUCTION",
            variables = mapOf("senderName" to "Chen", "teamName" to "Team")
        )

        assertEquals("Hello Chen", rendered.subject)
        assertEquals("Dear Professor,\n\nChen from Team", rendered.body)
        assertEquals("INTRODUCTION", rendered.mailType)
    }

    @Test
    fun `renderWithVariables delegates to renderText`() {
        assertEquals(
            "Hello Chen",
            service.renderWithVariables("Hello \${senderName}", mapOf("senderName" to "Chen"))
        )
    }

    @Test
    fun `renderText replaces placeholder when variable has value`() {
        assertEquals("Hello Chen", renderSubject("Hello ${'$'}{senderName}", mapOf("senderName" to "Chen")))
    }

    @Test
    fun `renderText preserves placeholder when variable is missing`() {
        assertEquals("Hello ${'$'}{unknown}", renderSubject("Hello ${'$'}{unknown}", emptyMap()))
    }

    @Test
    fun `renderText uses value for fallback placeholder when variable is non-empty`() {
        assertEquals(
            "Topic: AI",
            renderSubject("Topic: ${'$'}{researchFields|Science}", mapOf("researchFields" to "AI"))
        )
    }

    @Test
    fun `renderText uses fallback when variable is empty string`() {
        assertEquals(
            "Topic: Science",
            renderSubject("Topic: ${'$'}{researchFields|Science}", mapOf("researchFields" to ""))
        )
    }

    @Test
    fun `renderText uses fallback when variable key is missing`() {
        assertEquals(
            "Topic: Science",
            renderSubject("Topic: ${'$'}{researchFields|Science}", emptyMap())
        )
    }

    @Test
    fun `renderText treats only first pipe as fallback separator`() {
        assertEquals(
            "Value: 含|管道符",
            renderSubject("Value: ${'$'}{key|含|管道符}", emptyMap())
        )
    }

    @Test
    fun `renderText handles mixed plain and fallback placeholders`() {
        assertEquals(
            "Hi Chen, topic: Default",
            renderSubject(
                "Hi ${'$'}{senderName}, topic: ${'$'}{researchFields|Default}",
                mapOf("senderName" to "Chen", "researchFields" to "")
            )
        )
    }

    private fun renderSubject(subject: String, variables: Map<String, String>): String {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("TEST"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "TEST",
                    templateName = "Test",
                    subject = subject,
                    mailType = "TEST"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )
        return service.renderByCode("TEST", variables).subject
    }

    @Test
    fun `update preserves template code and mail type when request omits them`() {
        Mockito.`when`(templateRepository.findById(10))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 10,
                        templateCode = "INTRODUCTION",
                        templateName = "Intro",
                        subject = "Old",
                        mailType = "INTRODUCTION"
                    )
                )
            )
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailComposeTemplate>(0) }
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(10))
            .thenReturn(emptyList())

        service.update(
            10,
            MailComposeTemplateCommand(
                templateName = "Intro edited",
                subject = "New",
                blocks = listOf(
                    MailComposeTemplateBlockCommand(
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )
        )

        Mockito.verify(templateRepository).save(
            Mockito.argThat { saved ->
                saved.templateCode == "INTRODUCTION" && saved.mailType == "INTRODUCTION"
            }
        )
    }

    @Test
    fun `renderByCode falls back to subject when subjectVariants is null`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Default subject",
                    subjectVariants = null,
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )

        val rendered = service.renderByCode("INTRO")

        assertEquals("Default subject", rendered.subject)
    }

    @Test
    fun `renderByCode selects subject from variants deterministically`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Default subject",
                    subjectVariants = """["A","B","C"]""",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )

        val seed = "0000-0001".hashCode()
        val rendered = service.renderByCode("INTRO", variantSeed = seed)

        assertTrue(rendered.subject in listOf("Default subject", "A", "B", "C"))
        assertEquals(rendered.subject, service.renderByCode("INTRO", variantSeed = seed).subject)
    }

    @Test
    fun `renderByCode includes main subject in variant pool`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "S",
                    subjectVariants = """["A","B"]""",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )

        assertEquals("S", service.renderByCode("INTRO", variantSeed = 0).subject)
        assertEquals("A", service.renderByCode("INTRO", variantSeed = 1).subject)
        assertEquals("B", service.renderByCode("INTRO", variantSeed = 2).subject)
    }

    @Test
    fun `renderByCode uses snippet directly when variantGroup is null`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Subject",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(
                Optional.of(
                    ReplySnippet(
                        id = 5,
                        snippetType = "greeting",
                        content = "Hello original",
                        variantGroup = null
                    )
                )
            )

        val rendered = service.renderByCode("INTRO")

        assertEquals("Hello original", rendered.body)
    }

    @Test
    fun `renderByCode selects snippet variant from group deterministically`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Subject",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        val groupSnippets = listOf(
            ReplySnippet(id = 5, snippetType = "greeting", content = "Hello A", variantGroup = "greeting", displayOrder = 1),
            ReplySnippet(id = 6, snippetType = "greeting", content = "Hello B", variantGroup = "greeting", displayOrder = 2),
            ReplySnippet(id = 7, snippetType = "greeting", content = "Hello C", variantGroup = "greeting", displayOrder = 3)
        )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(groupSnippets[0]))
        Mockito.`when`(replySnippetRepository.findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc("greeting", "greeting"))
            .thenReturn(groupSnippets)

        val seed = "0000-0002".hashCode()
        val rendered = service.renderByCode("INTRO", variantSeed = seed)

        assertTrue(rendered.body in listOf("Hello A", "Hello B", "Hello C"))
        assertEquals(rendered.body, service.renderByCode("INTRO", variantSeed = seed).body)
    }

    @Test
    fun `renderByCode does not cross snippet types in variant group`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Subject",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        val greetingSnippet = ReplySnippet(id = 5, snippetType = "greeting", content = "Hello original", variantGroup = "shared")
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(greetingSnippet))
        Mockito.`when`(
            replySnippetRepository.findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc("shared", "greeting")
        ).thenReturn(emptyList())

        val rendered = service.renderByCode("INTRO")

        assertEquals("Hello original", rendered.body)
    }

    @Test
    fun `renderByCode decouples subject and snippet variant indices`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "S",
                    subjectVariants = """["A","B"]""",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        val groupSnippets = listOf(
            ReplySnippet(id = 5, snippetType = "greeting", content = "Hello A", variantGroup = "greeting", displayOrder = 1),
            ReplySnippet(id = 6, snippetType = "greeting", content = "Hello B", variantGroup = "greeting", displayOrder = 2),
            ReplySnippet(id = 7, snippetType = "greeting", content = "Hello C", variantGroup = "greeting", displayOrder = 3)
        )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(groupSnippets[0]))
        Mockito.`when`(
            replySnippetRepository.findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc("greeting", "greeting")
        ).thenReturn(groupSnippets)

        val seed = 0
        val subjectIndex = Math.floorMod(seed, 3)
        val snippetIndex = Math.floorMod(seed + "greeting".hashCode(), 3)
        assertTrue(subjectIndex != snippetIndex)

        val rendered = service.renderByCode("INTRO", variantSeed = seed)
        assertEquals(listOf("S", "A", "B")[subjectIndex], rendered.subject)
        assertEquals(listOf("Hello A", "Hello B", "Hello C")[snippetIndex], rendered.body)
    }

    @Test
    fun `renderByCode handles Int MIN_VALUE seed for subject and snippet variants`() {
        val seed = "polygenelubricants".hashCode()
        assertEquals(Int.MIN_VALUE, seed)
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Default subject",
                    subjectVariants = """["A","B","C"]""",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        val groupSnippets = listOf(
            ReplySnippet(id = 5, snippetType = "greeting", content = "Hello A", variantGroup = "greeting", displayOrder = 1),
            ReplySnippet(id = 6, snippetType = "greeting", content = "Hello B", variantGroup = "greeting", displayOrder = 2),
            ReplySnippet(id = 7, snippetType = "greeting", content = "Hello C", variantGroup = "greeting", displayOrder = 3)
        )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(groupSnippets[0]))
        Mockito.`when`(replySnippetRepository.findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc("greeting", "greeting"))
            .thenReturn(groupSnippets)

        val first = service.renderByCode("INTRO", variantSeed = seed)
        val second = service.renderByCode("INTRO", variantSeed = seed)

        assertTrue(first.subject in listOf("Default subject", "A", "B", "C"))
        assertTrue(first.body in listOf("Hello A", "Hello B", "Hello C"))
        assertEquals(first.subject, second.subject)
        assertEquals(first.body, second.body)
    }

    @Test
    fun `renderByCode tolerates invalid subjectVariants json on read path`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Default subject",
                    subjectVariants = "not-json",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Body"
                    )
                )
            )

        val rendered = service.renderByCode("INTRO", variantSeed = 99)

        assertEquals("Default subject", rendered.subject)
    }

    @Test
    fun `variantSeedFor prefers orcid over email`() {
        val orcidSeed = MailComposeTemplateService.variantSeedFor("0000-0001", "a@b.c")
        assertEquals("0000-0001".hashCode(), orcidSeed)
    }

    @Test
    fun `variantSeedFor normalizes email when orcid is blank`() {
        val emailSeed = MailComposeTemplateService.variantSeedFor(null, " A@B.C ")
        assertEquals("a@b.c".hashCode(), emailSeed)
    }

    @Test
    fun `variantSeedFor returns zero when both identifiers are blank`() {
        assertEquals(0, MailComposeTemplateService.variantSeedFor(null, null))
        assertEquals(0, MailComposeTemplateService.variantSeedFor("  ", " "))
    }

    @Test
    fun `variantSeedFor is deterministic`() {
        val first = MailComposeTemplateService.variantSeedFor("0000-0002", null)
        val second = MailComposeTemplateService.variantSeedFor("0000-0002", null)
        assertEquals(first, second)
    }

    @Test
    fun `create rejects invalid subjectVariants`() {
        val base = validTemplateCommand()

        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subjectVariants = "not-json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subjectVariants = """[1,true]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subjectVariants = """[" "]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subjectVariants = """["A","A"]"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subject = "Main", subjectVariants = """["Main"]"""))
        }
        Mockito.`when`(mailVariableService.validatePlaceholders("""Hello ${'$'}{unknown}"""))
            .thenReturn(listOf("""${'$'}{unknown}"""))
        assertThrows(IllegalArgumentException::class.java) {
            service.create(base.copy(subjectVariants = """["Hello ${'$'}{unknown}"]"""))
        }

        Mockito.verify(templateRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `update rejects invalid subjectVariants`() {
        Mockito.`when`(templateRepository.findById(10))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 10,
                        templateCode = "INTRODUCTION",
                        templateName = "Intro",
                        subject = "Old",
                        mailType = "INTRODUCTION"
                    )
                )
            )
        val base = validTemplateCommand()

        assertThrows(IllegalArgumentException::class.java) {
            service.update(10, base.copy(subjectVariants = "not-json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.update(10, base.copy(subjectVariants = """[1,true]"""))
        }

        Mockito.verify(templateRepository, Mockito.never()).save(Mockito.any())
    }

    @Test
    fun `create accepts valid subjectVariants`() {
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation ->
                invocation.getArgument<MailComposeTemplate>(0).copy(id = 11)
            }
        Mockito.`when`(templateRepository.findById(11))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 11,
                        templateName = "Intro",
                        subject = "Main",
                        subjectVariants = """["Alt A","Alt B"]""",
                        mailType = "INTRODUCTION"
                    )
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11))
            .thenReturn(emptyList())

        service.create(
            validTemplateCommand().copy(
                subject = "Main",
                subjectVariants = """["Alt A","Alt B"]"""
            )
        )

        Mockito.verify(templateRepository).save(Mockito.any())
    }

    @Test
    fun `previewDraft variantIndex selects subject pool members`() {
        val resultDefault = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                subjectVariants = listOf("A", "B")
            )
        )
        assertEquals("S", resultDefault.subject)

        val resultFirstVariant = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                subjectVariants = listOf("A", "B"),
                variantIndex = 1
            )
        )
        assertEquals("A", resultFirstVariant.subject)

        val resultWrapped = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                subjectVariants = listOf("A", "B"),
                variantIndex = 5
            )
        )
        assertEquals("B", resultWrapped.subject)
    }

    @Test
    fun `renderByCode end to end uses variantSeedFor for subject pool and same-type snippet group`() {
        val seed = MailComposeTemplateService.variantSeedFor("0000-0003", "ignored@example.com")
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "S",
                    subjectVariants = """["A","B"]""",
                    mailType = "INTRODUCTION"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(1))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 0,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    )
                )
            )
        val groupSnippets = listOf(
            ReplySnippet(id = 5, snippetType = "greeting", content = "Hello A", variantGroup = "greeting", displayOrder = 1),
            ReplySnippet(id = 6, snippetType = "greeting", content = "Hello B", variantGroup = "greeting", displayOrder = 2),
            ReplySnippet(id = 7, snippetType = "greeting", content = "Hello C", variantGroup = "greeting", displayOrder = 3)
        )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(groupSnippets[0]))
        Mockito.`when`(
            replySnippetRepository.findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc("greeting", "greeting")
        ).thenReturn(groupSnippets)

        val rendered = service.renderByCode("INTRO", variantSeed = seed)

        assertEquals(listOf("S", "A", "B")[Math.floorMod(seed, 3)], rendered.subject)
        assertEquals(
            listOf("Hello A", "Hello B", "Hello C")[Math.floorMod(seed + "greeting".hashCode(), 3)],
            rendered.body
        )
    }

    private fun validTemplateCommand(): MailComposeTemplateCommand =
        MailComposeTemplateCommand(
            templateName = "Intro",
            subject = "Main",
            blocks = listOf(
                MailComposeTemplateBlockCommand(
                    blockOrder = 0,
                    blockType = ComposeBlockType.CUSTOM_TEXT,
                    customText = "Body"
                )
            )
        )

    @Test
    fun `preview returns parsed blocks without variable rendering`() {
        Mockito.`when`(templateRepository.findById(20))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 20,
                        templateName = "Intro",
                        subject = "Hello \${senderName}"
                    )
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(20))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(
                        templateId = 20,
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Dear \${expertName}"
                    )
                )
            )

        val preview = service.preview(20)

        assertEquals("Hello \${senderName}", preview.subject)
        assertEquals("Dear \${expertName}", preview.body)
        assertEquals(1, preview.blocks.size)
        assertTrue(preview.blocks[0].included)
    }

    @Test
    fun `previewDraft without expert context returns raw text and placeholder keys`() {
        Mockito.doReturn(listOf("expertName", "senderName"))
            .`when`(mailVariableService)
            .placeholderKeysIn("Hello \${senderName}", "Dear \${expertName}")

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Hello \${senderName}",
                blocks = listOf(
                    ComposeDraftBlock(
                        blockOrder = 0,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Dear \${expertName}"
                    )
                )
            )
        )

        assertEquals("Hello \${senderName}", result.subject)
        assertEquals("Dear \${expertName}", result.body)
        assertEquals(listOf("expertName", "senderName"), result.fallbackKeys)
        assertEquals(null, result.toEmail)
        Mockito.verifyNoInteractions(expertContactRepository)
    }

    @Test
    fun `previewDraft renders variables and skips blocks under strict placeholders`() {
        val contact = ExpertContact(
            id = 7,
            campaignId = 1,
            orcidId = "0000-0001",
            expertEmail = "ada@mit.edu",
            expertName = "Ada"
        )
        val account = MailSenderAccount(
            accountCode = "ops",
            senderEmail = "ops@example.com",
            senderName = "Ops",
            senderTitle = "Director",
            senderDisplayName = "Ops",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "ops@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "ops@example.com",
            imapPassword = "secret"
        )
        Mockito.`when`(expertContactRepository.findById(7)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("ops")).thenReturn(account)
        Mockito.`when`(qaRuleRepository.findById(11))
            .thenReturn(
                Optional.of(
                    QaRule(
                        id = 11,
                        categoryId = 1,
                        keywords = "kw",
                        replySubject = "Subj",
                        replyBody = "Visible \${senderName}",
                        enabled = true
                    )
                )
            )
        Mockito.`when`(mailVariableService.renderPreview("Hello \${senderName}", account, contact))
            .thenReturn(
                RenderPreviewResult(
                    rendered = "Hello Ops",
                    fallbackKeys = emptyList(),
                    variables = listOf(
                        PreviewVariableItem("senderName", "发件人姓名", "Ops", true, false)
                    )
                )
            )
        Mockito.`when`(mailVariableService.renderPreview("Visible \${senderName}", account, contact))
            .thenReturn(
                RenderPreviewResult(
                    rendered = "Visible Ops",
                    fallbackKeys = emptyList(),
                    variables = listOf(
                        PreviewVariableItem("senderName", "发件人姓名", "Ops", true, false)
                    )
                )
            )
        Mockito.`when`(mailVariableService.renderPreview("Hidden \${researchFields}", account, contact))
            .thenReturn(
                RenderPreviewResult(
                    rendered = "Hidden ",
                    fallbackKeys = listOf("researchFields"),
                    variables = listOf(
                        PreviewVariableItem("researchFields", "研究方向", "", false, true)
                    )
                )
            )

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Hello \${senderName}",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.QA_RULE, refId = 11),
                    ComposeDraftBlock(1, ComposeBlockType.CUSTOM_TEXT, customText = "Hidden \${researchFields}")
                ),
                contactId = 7,
                senderAccountCode = "ops",
                strictPlaceholders = true
            )
        )

        assertEquals("Hello Ops", result.subject)
        assertEquals("Visible Ops", result.body)
        assertEquals("ada@mit.edu", result.toEmail)
        assertTrue(result.fallbackKeys.contains("researchFields"))
        assertEquals(2, result.blocks.size)
        assertTrue(result.blocks[0].included)
        assertFalse(result.blocks[1].included)
        assertEquals("存在未满足占位符", result.blocks[1].skipReason)
    }

    @Test
    fun `previewDraft passes raw fallback placeholder tokens to renderPreview`() {
        val contact = ExpertContact(
            id = 7,
            campaignId = 1,
            orcidId = "0000-0001",
            expertEmail = "ada@mit.edu",
            expertName = "Ada"
        )
        val account = MailSenderAccount(
            accountCode = "ops",
            senderEmail = "ops@example.com",
            senderName = "Ops",
            senderTitle = "Director",
            senderDisplayName = "Ops",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "ops@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "ops@example.com",
            imapPassword = "secret"
        )
        val rawFallbackBody = "Topic: \${researchFields|Science}"
        Mockito.`when`(expertContactRepository.findById(7)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("ops")).thenReturn(account)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Subject",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Subject", account, contact)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Topic: Science",
                fallbackKeys = listOf("researchFields"),
                variables = listOf(
                    PreviewVariableItem("researchFields", "研究方向", "", false, true)
                )
            )
        ).`when`(mailVariableService).renderPreview(rawFallbackBody, account, contact)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Subject",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = rawFallbackBody)
                ),
                contactId = 7,
                senderAccountCode = "ops",
                strictPlaceholders = true
            )
        )

        assertEquals("", result.body)
        assertEquals(1, result.blocks.size)
        assertFalse(result.blocks[0].included)
        assertEquals("存在未满足占位符", result.blocks[0].skipReason)
        assertTrue(result.fallbackKeys.contains("researchFields"))
        Mockito.verify(mailVariableService).renderPreview(rawFallbackBody, account, contact)
    }
}
