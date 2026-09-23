package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.mail.domain.MailSenderAccount
import com.weibo.talentintroduction.mail.service.MailPlaceholderService
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
import com.weibo.talentintroduction.variant.domain.ContentVariant
import com.weibo.talentintroduction.variant.domain.ContentVariantOwnerType
import com.weibo.talentintroduction.variant.repository.ContentVariantRepository
import com.weibo.talentintroduction.variant.service.ContentVariantService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

class MailComposeTemplateServiceTest {
    private val templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java)
    private val blockRepository = Mockito.mock(MailComposeTemplateBlockRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetRepository = Mockito.mock(ReplySnippetRepository::class.java)
    private val mailVariableService = Mockito.mock(MailVariableService::class.java)
    private val expertContactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val contentVariantRepository = Mockito.mock(ContentVariantRepository::class.java)
    private val contentVariantService = Mockito.spy(ContentVariantService(contentVariantRepository, MailPlaceholderService()))
    private val objectMapper = ObjectMapper()
    private val service = MailComposeTemplateService(
        templateRepository,
        blockRepository,
        qaRuleRepository,
        replySnippetRepository,
        objectMapper,
        mailVariableService,
        expertContactRepository,
        mailSenderAccountService,
        contentVariantService
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
    fun `create ignores subjectVariants from command`() {
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailComposeTemplate>(0).copy(id = 11) }
        Mockito.`when`(templateRepository.findById(11))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 11,
                        templateName = "Intro",
                        subject = "Main",
                        mailType = "INTRODUCTION"
                    )
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11))
            .thenReturn(emptyList())

        service.create(validTemplateCommand().copy(subjectVariants = """["A"]"""))

        Mockito.verify(templateRepository).save(
            Mockito.argThat { saved -> saved.subjectVariants == null }
        )
    }
    @Test
    fun `create stores subject snippet id and source snapshot while detail returns the id`() {
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Current subject")))
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailComposeTemplate>(0).copy(id = 11L) }
        Mockito.`when`(templateRepository.findById(11L))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 11L,
                        templateName = "Intro",
                        subject = "Current subject",
                        subjectSnippetId = 5L
                    )
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11L)).thenReturn(emptyList())

        val detail = service.create(
            validTemplateCommand().copy(subject = "ignored", subjectSnippetId = 5L)
        )

        assertEquals(5L, detail.subjectSnippetId)
        assertEquals("Current subject", detail.subject)
        Mockito.verify(templateRepository).save(
            Mockito.argThat { saved -> saved.subject == "Current subject" && saved.subjectSnippetId == 5L }
        )
    }

    @Test
    fun `referenced subject render reads latest snippet content and records selected raw text`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 11L,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Stale snapshot",
                    subjectSnippetId = 5L
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11L)).thenReturn(emptyList())
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Latest subject")))
        Mockito.doReturn("Latest subject")
            .`when`(contentVariantService)
            .resolveReplySnippetBody(5L, "Latest subject", null)

        val rendered = service.renderByCode("INTRO", variantSeed = 123)

        assertEquals("Latest subject", rendered.subject)
        assertEquals(listOf("Latest subject"), rendered.rawTexts)
    }

    @Test
    fun `previewDraft resolves referenced subject through public selector without stale snapshot`() {
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Latest preview subject")))
        Mockito.doReturn("Sampled preview subject")
            .`when`(contentVariantService)
            .resolveReplySnippetBody(5L, "Latest preview subject", null)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Stale snapshot",
                subjectSnippetId = 5L
            )
        )

        assertEquals("Sampled preview subject", result.subject)
        Mockito.verify(contentVariantService)
            .resolveReplySnippetBody(5L, "Latest preview subject", null)
    }

    @Test
    fun `render rejects a subject made multiline by placeholder replacement`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 11L,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Hello \${senderName}"
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11L)).thenReturn(emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.renderByCode("INTRO", variables = mapOf("senderName" to "Ada\nLovelace"))
        }

        assertEquals("渲染后的邮件主题必须为 1–255 字的单行文本", error.message)
    }

    @Test
    fun `referenced subject render rejects a missing or disabled snippet instead of using its snapshot`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 11L,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Stale snapshot",
                    subjectSnippetId = 5L
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11L)).thenReturn(emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.renderByCode("INTRO")
        }

        assertEquals("主题引用的回复片段不存在或已停用（ID: 5）", error.message)
    }

    @Test
    fun `subject variants contribute candidate union and intersection to gate keys`() {
        stubTemplate(id = 5L, subject = "Snapshot", blocks = emptyList())
        Mockito.`when`(templateRepository.findById(5L))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 5L,
                        templateName = "Intro",
                        subject = "Snapshot",
                        subjectSnippetId = 9L
                    )
                )
            )
        Mockito.`when`(replySnippetRepository.findById(9L))
            .thenReturn(Optional.of(snippet(9L, "\${institution}")))
        stubSnippetVariants(
            9L,
            listOf(snippetVariant(1L, 9L, 1, "\${institution|your institution}"))
        )

        assertTrue("institution" in service.effectiveRequiredKeys(5L))
        assertFalse(MailPlaceholderService.ES_FIELD_BY_KEY.getValue("institution") in service.requiredEsFields(5L))
    }

    @Test
    fun `subject candidate validation rejects blank long and multiline content`() {
        listOf("", "x".repeat(256), "line one\nline two", "line one\rline two").forEach { content ->
            Mockito.`when`(replySnippetRepository.findById(5L))
                .thenReturn(Optional.of(snippet(5L, content)))
            assertThrows(IllegalArgumentException::class.java) {
                service.create(validTemplateCommand().copy(subjectSnippetId = 5L))
            }
        }
    }


    @Test
    fun `update ignores subjectVariants from command and clears stored value`() {
        Mockito.`when`(templateRepository.findById(10))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 10,
                        templateCode = "INTRODUCTION",
                        templateName = "Intro",
                        subject = "Old",
                        subjectVariants = """["Legacy"]""",
                        mailType = "INTRODUCTION"
                    )
                )
            )
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailComposeTemplate>(0) }
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(10))
            .thenReturn(emptyList())

        service.update(10, validTemplateCommand().copy(subjectVariants = """["A"]"""))

        Mockito.verify(templateRepository).save(
            Mockito.argThat { saved -> saved.subjectVariants == null }
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
    fun `renderByCode ignores subjectVariants and always uses main subject`() {
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

        assertEquals("Default subject", service.renderByCode("INTRO", variantSeed = 0).subject)
        assertEquals("Default subject", service.renderByCode("INTRO", variantSeed = 99).subject)
    }

    @Test
    fun `renderByCode uses snippet directly when no content variants exist`() {
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
    fun `formal render samples each snippet through the public selector and ignores variant seed`() {
        stubIntroSnippetTemplate(refId = 5L)
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(Optional.of(ReplySnippet(id = 5, snippetType = "greeting", content = "Hello original")))
        Mockito.doReturn("Hello A", "Hello B")
            .`when`(contentVariantService)
            .resolveReplySnippetBody(5L, "Hello original", null)

        val first = service.renderByCode("INTRO", variantSeed = 0)
        val second = service.renderByCode("INTRO", variantSeed = Int.MIN_VALUE)

        assertEquals("Hello A", first.body)
        assertEquals("Hello B", second.body)
        Mockito.verify(contentVariantService, Mockito.times(2))
            .resolveReplySnippetBody(5L, "Hello original", null)
    }

    @Test
    fun `formal render independently samples subject and each repeated body occurrence`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 11L,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Snapshot",
                    subjectSnippetId = 5L
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11L))
            .thenReturn(
                listOf(
                    MailComposeTemplateBlock(templateId = 11L, blockOrder = 0, blockType = ComposeBlockType.REPLY_SNIPPET, refId = 5L),
                    MailComposeTemplateBlock(templateId = 11L, blockOrder = 1, blockType = ComposeBlockType.REPLY_SNIPPET, refId = 5L)
                )
            )
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Original")))
        Mockito.doReturn("S1", "B2", "B3")
            .`when`(contentVariantService)
            .resolveReplySnippetBody(5L, "Original", null)

        val rendered = service.renderByCode("INTRO", variantSeed = Int.MIN_VALUE)

        assertEquals("S1", rendered.subject)
        assertEquals("B2\n\nB3", rendered.body)
        assertEquals(listOf("S1", "B2", "B3"), rendered.rawTexts)
        Mockito.verify(contentVariantService, Mockito.times(3))
            .resolveReplySnippetBody(5L, "Original", null)
    }

    @Test
    fun `create rejects QA_RULE blocks`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                validTemplateCommand().copy(
                    blocks = listOf(
                        MailComposeTemplateBlockCommand(
                            blockOrder = 0,
                            blockType = ComposeBlockType.QA_RULE,
                            refId = 1L
                        )
                    )
                )
            )
        }
        assertEquals("QA_RULE blocks are read-only and cannot be created", ex.message)
    }

    @Test
    fun `update rejects QA_RULE blocks`() {
        Mockito.`when`(templateRepository.findById(10))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = 10,
                        templateName = "Intro",
                        subject = "Old"
                    )
                )
            )
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.update(
                10,
                validTemplateCommand().copy(
                    blocks = listOf(
                        MailComposeTemplateBlockCommand(
                            blockOrder = 0,
                            blockType = ComposeBlockType.QA_RULE,
                            refId = 1L
                        )
                    )
                )
            )
        }
        assertEquals("QA_RULE blocks are read-only and cannot be created", ex.message)
    }

    @Test
    fun `previewDraft rejects QA_RULE blocks`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.previewDraft(
                ComposeTemplatePreviewDraftRequest(
                    subject = "S",
                    blocks = listOf(ComposeDraftBlock(0, ComposeBlockType.QA_RULE, refId = 3L))
                )
            )
        }
        assertEquals("QA_RULE blocks are read-only and cannot be created", ex.message)
    }

    @Test
    fun `renderByCode legacy QA rule uses main reply body without content variants`() {
        stubIntroQaTemplate(refId = 11L)
        Mockito.`when`(qaRuleRepository.findById(11))
            .thenReturn(
                Optional.of(
                    QaRule(
                        id = 11,
                        categoryId = 1,
                        keywords = "kw",
                        replySubject = "Subj",
                        replyBody = "MAIN body",
                        enabled = true
                    )
                )
            )
        stubQaVariants(
            ownerId = 11L,
            variants = listOf(contentVariant(id = 1L, ownerId = 11L, order = 1, content = "VARIANT-A"))
        )

        val rendered = service.renderByCode("INTRO", variantSeed = 0)

        assertEquals("MAIN body", rendered.body)
        assertEquals(listOf(11L), rendered.qaRuleIds)
    }

    @Test
    fun `renderByCode handles Int MIN_VALUE seed for content variants`() {
        val seed = "polygenelubricants".hashCode()
        assertEquals(Int.MIN_VALUE, seed)
        stubIntroSnippetTemplate(refId = 5L)
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(
                Optional.of(
                    ReplySnippet(id = 5, snippetType = "greeting", content = "Hello original")
                )
            )
        stubSnippetVariants(
            ownerId = 5L,
            variants = listOf(
                contentVariant(id = 1L, ownerId = 5L, order = 1, content = "Hello A"),
                contentVariant(id = 2L, ownerId = 5L, order = 2, content = "Hello B")
            )
        )

        val first = service.renderByCode("INTRO", variantSeed = seed)
        val second = service.renderByCode("INTRO", variantSeed = seed)

        assertTrue(first.body in listOf("Hello original", "Hello A", "Hello B"))
        assertTrue(second.body in listOf("Hello original", "Hello A", "Hello B"))
        assertEquals("Subject", first.subject)
    }

    @Test
    fun `renderByCode without content variants preserves body output`() {
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
                        blockType = ComposeBlockType.QA_RULE,
                        refId = 11
                    ),
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 1,
                        blockType = ComposeBlockType.REPLY_SNIPPET,
                        refId = 5
                    ),
                    MailComposeTemplateBlock(
                        templateId = 1,
                        blockOrder = 2,
                        blockType = ComposeBlockType.CUSTOM_TEXT,
                        customText = "Tail text"
                    )
                )
            )
        Mockito.`when`(qaRuleRepository.findById(11))
            .thenReturn(
                Optional.of(
                    QaRule(
                        id = 11,
                        categoryId = 1,
                        keywords = "kw",
                        replySubject = "Subj",
                        replyBody = "QA paragraph",
                        enabled = true
                    )
                )
            )
        Mockito.`when`(replySnippetRepository.findById(5))
            .thenReturn(
                Optional.of(
                    ReplySnippet(id = 5, snippetType = "greeting", content = "Hello original")
                )
            )
        stubQaVariants(11L, emptyList())
        stubSnippetVariants(5L, emptyList())

        val rendered = service.renderByCode("INTRO", variantSeed = 42)

        assertEquals("QA paragraph\n\nHello original\n\nTail text", rendered.body)
        assertEquals(listOf(11L), rendered.qaRuleIds)
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
    fun `previewDraft variantIndex scrolls reply snippet block and reports variantPoolSize`() {
        val snippetId = 3L
        Mockito.`when`(replySnippetRepository.findById(snippetId))
            .thenReturn(
                Optional.of(
                    ReplySnippet(
                        id = snippetId,
                        snippetType = "greeting",
                        content = "MAIN body",
                        enabled = true
                    )
                )
            )
        stubSnippetVariants(
            ownerId = snippetId,
            variants = listOf(
                contentVariant(id = 1L, ownerId = snippetId, order = 1, content = "VARIANT-A"),
                contentVariant(id = 2L, ownerId = snippetId, order = 2, content = "VARIANT-B")
            )
        )

        val result0 = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                blocks = listOf(ComposeDraftBlock(0, ComposeBlockType.REPLY_SNIPPET, refId = snippetId)),
                variantIndex = 0
            )
        )
        val result1 = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                blocks = listOf(ComposeDraftBlock(0, ComposeBlockType.REPLY_SNIPPET, refId = snippetId)),
                variantIndex = 1
            )
        )
        val result2 = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "S",
                blocks = listOf(ComposeDraftBlock(0, ComposeBlockType.REPLY_SNIPPET, refId = snippetId)),
                variantIndex = 2
            )
        )

        assertEquals("S", result0.subject)
        assertEquals("MAIN body", result0.body)
        assertEquals("VARIANT-A", result1.body)
        assertEquals("VARIANT-B", result2.body)
        assertEquals(3, result0.variantPoolSize)
        assertEquals(3, result1.variantPoolSize)
        assertEquals(3, result2.variantPoolSize)
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
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = "Visible \${senderName}"),
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

    @Test
    fun `previewDraft uses request expert email for orcid preview contact`() {
        val syntheticContact = ExpertContact(
            campaignId = 0,
            orcidId = "0000-0001",
            expertEmail = "ada@mit.edu",
            expertName = "Preview",
            currentIndexLevel = "CANDIDATE"
        )
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Subject",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Subject", null, syntheticContact)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "https://example.com/u/unsubscribe?token=abc",
                fallbackKeys = emptyList(),
                variables = listOf(
                    PreviewVariableItem("unsubscribeUrl", "退订链接", "https://example.com/u/unsubscribe?token=abc", true, false)
                )
            )
        ).`when`(mailVariableService).renderPreview("\${unsubscribeUrl}", null, syntheticContact)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Subject",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = "\${unsubscribeUrl}")
                ),
                orcidId = "0000-0001",
                expertEmail = "ada@mit.edu",
                strictPlaceholders = true
            )
        )

        assertEquals("ada@mit.edu", result.toEmail)
        assertEquals("https://example.com/u/unsubscribe?token=abc", result.body)
        Mockito.verify(mailVariableService).renderPreview("\${unsubscribeUrl}", null, syntheticContact)
    }

    @Test
    fun `previewDraft falls back to the contact bound sender account when no explicit code`() {
        val contact = ExpertContact(
            id = 42,
            campaignId = 1,
            orcidId = "0000-0042",
            expertEmail = "ada42@mit.edu",
            expertName = "Ada42",
            boundSenderAccountCode = "LiLei"
        )
        val account = MailSenderAccount(
            accountCode = "LiLei",
            senderEmail = "lilei@example.com",
            senderName = "LiLei",
            senderTitle = "Director",
            senderDisplayName = "LiLei",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "lilei@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "lilei@example.com",
            imapPassword = "secret"
        )
        Mockito.`when`(expertContactRepository.findById(42)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("LiLei")).thenReturn(account)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Subject",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Subject", account, contact)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Body",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Body", account, contact)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Subject",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = "Body")
                ),
                contactId = 42
            )
        )

        assertEquals("Subject", result.subject)
        assertEquals("Body", result.body)
        Mockito.verify(mailVariableService).renderPreview("Subject", account, contact)
        Mockito.verify(mailVariableService).renderPreview("Body", account, contact)
    }

    @Test
    fun `previewDraft prefers the explicit sender account over the contact binding`() {
        val contact = ExpertContact(
            id = 42,
            campaignId = 1,
            orcidId = "0000-0042",
            expertEmail = "ada42@mit.edu",
            expertName = "Ada42",
            boundSenderAccountCode = "LiLei"
        )
        val boundAccount = MailSenderAccount(
            accountCode = "LiLei",
            senderEmail = "lilei@example.com",
            senderName = "LiLei",
            senderTitle = "Director",
            senderDisplayName = "LiLei",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "lilei@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "lilei@example.com",
            imapPassword = "secret"
        )
        val explicitAccount = MailSenderAccount(
            accountCode = "WangFang",
            senderEmail = "wangfang@example.com",
            senderName = "WangFang",
            senderTitle = "Director",
            senderDisplayName = "WangFang",
            teamName = "Team",
            countryName = "China",
            smtpHost = "smtp.example.com",
            smtpPort = 465,
            smtpUsername = "wangfang@example.com",
            smtpPassword = "secret",
            imapHost = "imap.example.com",
            imapPort = 993,
            imapUsername = "wangfang@example.com",
            imapPassword = "secret"
        )
        Mockito.`when`(expertContactRepository.findById(42)).thenReturn(Optional.of(contact))
        Mockito.`when`(mailSenderAccountService.getAccount("LiLei")).thenReturn(boundAccount)
        Mockito.`when`(mailSenderAccountService.getAccount("WangFang")).thenReturn(explicitAccount)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Subject",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Subject", explicitAccount, contact)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Body",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Body", explicitAccount, contact)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Subject",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = "Body")
                ),
                contactId = 42,
                senderAccountCode = "WangFang"
            )
        )

        assertEquals("Subject", result.subject)
        assertEquals("Body", result.body)
        Mockito.verify(mailVariableService).renderPreview("Subject", explicitAccount, contact)
        Mockito.verify(mailVariableService).renderPreview("Body", explicitAccount, contact)
        Mockito.verify(mailSenderAccountService, Mockito.never()).getAccount("LiLei")
    }

    @Test
    fun `previewDraft resolves a null account when the contact has no binding`() {
        val contact = ExpertContact(
            id = 42,
            campaignId = 1,
            orcidId = "0000-0042",
            expertEmail = "ada42@mit.edu",
            expertName = "Ada42"
        )
        Mockito.`when`(expertContactRepository.findById(42)).thenReturn(Optional.of(contact))
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Subject",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Subject", null, contact)
        Mockito.doReturn(
            RenderPreviewResult(
                rendered = "Body",
                fallbackKeys = emptyList(),
                variables = emptyList()
            )
        ).`when`(mailVariableService).renderPreview("Body", null, contact)

        val result = service.previewDraft(
            ComposeTemplatePreviewDraftRequest(
                subject = "Subject",
                blocks = listOf(
                    ComposeDraftBlock(0, ComposeBlockType.CUSTOM_TEXT, customText = "Body")
                ),
                contactId = 42
            )
        )

        assertEquals("Subject", result.subject)
        assertEquals("Body", result.body)
        Mockito.verify(mailVariableService).renderPreview("Subject", null, contact)
        Mockito.verify(mailVariableService).renderPreview("Body", null, contact)
    }

    private fun stubIntroSnippetTemplate(refId: Long) {
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
                        refId = refId
                    )
                )
            )
    }

    private fun stubIntroQaTemplate(refId: Long) {
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
                        blockType = ComposeBlockType.QA_RULE,
                        refId = refId
                    )
                )
            )
    }

    private fun stubQaVariants(ownerId: Long, variants: List<ContentVariant>) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.QA_RULE,
                ownerId
            )
        ).thenReturn(variants)
    }

    private fun stubSnippetVariants(ownerId: Long, variants: List<ContentVariant>) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.REPLY_SNIPPET,
                ownerId
            )
        ).thenReturn(variants)
    }

    private fun contentVariant(id: Long, ownerId: Long, order: Int, content: String): ContentVariant =
        ContentVariant(
            id = id,
            ownerType = ContentVariantOwnerType.QA_RULE,
            ownerId = ownerId,
            variantOrder = order,
            content = content
        )

    // ── I-1/I-2: gate keys are derived from the live template, never from required_keys ──

    private fun stubTemplate(
        id: Long,
        subject: String,
        mailType: String? = "INTRODUCTION",
        requiredKeys: String? = null,
        blocks: List<MailComposeTemplateBlock> = listOf(customBlock(id, 0, "Body"))
    ) {
        Mockito.`when`(templateRepository.findById(id))
            .thenReturn(
                Optional.of(
                    MailComposeTemplate(
                        id = id,
                        templateName = "Test",
                        subject = subject,
                        mailType = mailType,
                        requiredKeys = requiredKeys
                    )
                )
            )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id)).thenReturn(blocks)
    }

    private fun customBlock(templateId: Long, order: Int, text: String): MailComposeTemplateBlock =
        MailComposeTemplateBlock(
            templateId = templateId,
            blockOrder = order,
            blockType = ComposeBlockType.CUSTOM_TEXT,
            customText = text
        )

    private fun snippetBlock(templateId: Long, order: Int, refId: Long): MailComposeTemplateBlock =
        MailComposeTemplateBlock(
            templateId = templateId,
            blockOrder = order,
            blockType = ComposeBlockType.REPLY_SNIPPET,
            refId = refId
        )

    private fun allSnippetVariants(ownerId: Long, variants: List<ContentVariant>) {
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.REPLY_SNIPPET,
                ownerId
            )
        ).thenReturn(variants)
        Mockito.`when`(
            contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(
                ContentVariantOwnerType.REPLY_SNIPPET,
                ownerId
            )
        ).thenReturn(variants.filter { it.enabled })
    }

    private fun snippetVariant(id: Long, ownerId: Long, order: Int, content: String): ContentVariant =
        ContentVariant(
            id = id,
            ownerType = ContentVariantOwnerType.REPLY_SNIPPET,
            ownerId = ownerId,
            variantOrder = order,
            content = content
        )

    private fun snippet(id: Long, content: String, enabled: Boolean = true): ReplySnippet =
        ReplySnippet(id = id, snippetType = "CUSTOM", content = content, enabled = enabled)

    @Test
    fun `bare token is required while a defaulted token is optional (I-1)`() {
        stubTemplate(
            id = 1,
            subject = "Hello \${institution}",
            blocks = listOf(customBlock(1, 0, "Topic: \${primaryResearchField|your research area}"))
        )

        assertEquals(listOf("institution"), service.effectiveRequiredKeys(1L))
        assertEquals(listOf("institution"), service.requiredEsFields(1L))
    }

    @Test
    fun `removing the default makes the key required and prefiltrable (I-1)`() {
        stubTemplate(
            id = 1,
            subject = "Hello \${institution}",
            blocks = listOf(customBlock(1, 0, "Topic: \${primaryResearchField}"))
        )

        assertEquals(listOf("institution", "primaryResearchField"), service.effectiveRequiredKeys(1L))
        assertEquals(listOf("institution", "researchFields"), service.requiredEsFields(1L))
    }

    @Test
    fun `legacy required_keys column never decides the gate (I-1)`() {
        stubTemplate(
            id = 1,
            subject = "Hello \${institution}",
            requiredKeys = """["recentWorkTitle","expertName"]""",
            blocks = listOf(customBlock(1, 0, "Topic: \${primaryResearchField|your research area}"))
        )

        // neither decides nor stacks: only the live placeholder set is returned
        assertEquals(listOf("institution"), service.effectiveRequiredKeys(1L))
        assertEquals(listOf("institution"), service.requiredEsFields(1L))
    }

    @Test
    fun `gate keys deduplicate in first-occurrence order and drop keys without es fields (I-1)`() {
        stubTemplate(
            id = 1,
            subject = "\${institution} and \${primaryResearchField|Your field}",
            blocks = listOf(
                customBlock(1, 0, "\${institution} / \${senderEmail}"),
                customBlock(1, 1, "\${primaryResearchField}")
            )
        )

        assertEquals(
            // subject first-occurrence order, then blocks in blockOrder:
            // `${primaryResearchField|Your field}` in the subject is optional, so it is
            // only picked up when its bare occurrence inside block #2 appears.
            listOf("institution", "senderEmail", "primaryResearchField"),
            service.effectiveRequiredKeys(1L)
        )
        // senderEmail is required but has no ES field → dropped from the prefilter
        assertEquals(listOf("institution", "researchFields"), service.requiredEsFields(1L))
    }

    @Test
    fun `key required by only some snippet variants gates the send but is never prefiltrable (I-2)`() {
        stubTemplate(
            id = 1,
            subject = "Hello",
            blocks = listOf(snippetBlock(1, 0, 5L))
        )
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Hi \${institution}")))
        allSnippetVariants(5L, listOf(snippetVariant(21L, 5L, 20, "Hi \${institution|your institution}")))

        // the variant that does require it must still be hard-blocked at send time
        assertEquals(listOf("institution"), service.effectiveRequiredKeys(1L))
        // not required by every possible render → the ES prefilter must not exclude experts
        assertEquals(emptyList<String>(), service.requiredEsFields(1L))
    }

    @Test
    fun `key required by every snippet variant stays prefiltrable (I-2)`() {
        stubTemplate(
            id = 1,
            subject = "Hello",
            blocks = listOf(snippetBlock(1, 0, 5L))
        )
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Hi \${institution}")))
        allSnippetVariants(5L, listOf(snippetVariant(21L, 5L, 20, "Dear \${institution}")))

        assertEquals(listOf("institution"), service.effectiveRequiredKeys(1L))
        assertEquals(listOf("institution"), service.requiredEsFields(1L))
    }

    @Test
    fun `disabled snippet block contributes no gate keys (I-2)`() {
        stubTemplate(
            id = 1,
            subject = "Hello",
            blocks = listOf(snippetBlock(1, 0, 5L))
        )
        Mockito.`when`(replySnippetRepository.findById(5L))
            .thenReturn(Optional.of(snippet(5L, "Hi \${institution}", enabled = false)))

        assertEquals(emptyList<String>(), service.effectiveRequiredKeys(1L))
        assertEquals(emptyList<String>(), service.requiredEsFields(1L))
    }

    // ── I-1: template save rejects unknown keys, blank defaults and broken tokens ──

    @Test
    fun `create rejects blank default unknown key and broken token (I-1)`() {
        val subjects = listOf("\${institution|}", "\${bogus}", "Hello \${institution")
        subjects.forEach { subject ->
            val ex = assertThrows(IllegalArgumentException::class.java) {
                service.create(validTemplateCommand().copy(subject = subject))
            }
            assertTrue(
                ex.message!!.contains("Invalid template placeholders"),
                "subject=$subject message=${ex.message}"
            )
        }
    }

    @Test
    fun `create rejects invalid placeholders inside a custom block (I-1)`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.create(
                validTemplateCommand().copy(blocks = listOf(customBlockCommand(0, "Hi \${bogus}")))
            )
        }

        assertTrue(ex.message!!.contains("\${bogus}"))
    }

    @Test
    fun `create accepts bare tokens and non-blank defaults (I-1)`() {
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation -> invocation.getArgument<MailComposeTemplate>(0).copy(id = 11) }
        Mockito.`when`(templateRepository.findById(11)).thenReturn(
            Optional.of(
                MailComposeTemplate(
                    id = 11,
                    templateName = "Intro",
                    subject = "Hello \${institution}",
                    mailType = "INTRODUCTION"
                )
            )
        )
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(11)).thenReturn(emptyList())

        val detail = service.create(
            validTemplateCommand()
                .copy(subject = "Hello \${institution}")
                .copy(blocks = listOf(customBlockCommand(0, "Topic: \${primaryResearchField|your research area}")))
        )

        assertEquals("INTRODUCTION", detail.mailType)
    }

    @Test
    fun `update rejects invalid custom block placeholders without touching the stored row (I-1)`() {
        Mockito.`when`(templateRepository.findById(10)).thenReturn(
            Optional.of(
                MailComposeTemplate(
                    id = 10,
                    templateCode = "INTRODUCTION",
                    templateName = "Intro",
                    subject = "Main",
                    mailType = "INTRODUCTION"
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.update(
                10,
                validTemplateCommand().copy(blocks = listOf(customBlockCommand(0, "Hi \${bogus}")))
            )
        }
        Mockito.verify(templateRepository, Mockito.never())
            .save(Mockito.any(MailComposeTemplate::class.java))
    }

    // ── I-3: CRUD lifecycle ──

    @Test
    fun `create list get update preview enable disable delete round trip (I-3)`() {
        val store = InMemoryTemplateStore()
        stubInMemoryStore(store)

        val created = service.create(validTemplateCommand())
        assertEquals(5L, created.id)
        assertEquals("INTRODUCTION", created.mailType)
        assertEquals(listOf("Body"), created.blocks.map { it.customText })

        assertEquals(listOf(5L), service.listAll().map { it.id })
        assertEquals("Intro", service.getById(5L).templateName)

        val updated = service.update(
            5L,
            MailComposeTemplateCommand(
                templateCode = "CODE",
                templateName = "Intro v2",
                subject = "Main v2",
                // I-3: an edit keeps the stored mail type
                mailType = "MATERIAL_REMINDER",
                blocks = listOf(customBlockCommand(1, "Second"), customBlockCommand(0, "First"))
            )
        )
        assertEquals(5L, updated.id)
        assertEquals("INTRODUCTION", updated.mailType)
        assertEquals(listOf(0, 1), updated.blocks.map { it.blockOrder })
        assertEquals(listOf("First", "Second"), updated.blocks.map { it.customText })

        assertEquals("Main v2", service.preview(5L).subject)
        assertEquals(false, service.setEnabled(5L, false).enabled)
        assertEquals(true, service.setEnabled(5L, true).enabled)

        service.delete(5L)
        Mockito.verify(templateRepository).deleteById(5L)
        assertEquals(0, store.blocks.size)
    }

    @Test
    fun `delete of a template referenced by a batch task reports a clear conflict (I-3)`() {
        Mockito.`when`(templateRepository.findById(7L)).thenReturn(
            Optional.of(
                MailComposeTemplate(
                    id = 7,
                    templateName = "Intro",
                    subject = "Main",
                    mailType = "INTRODUCTION"
                )
            )
        )
        Mockito.`when`(templateRepository.deleteById(7L)).thenThrow(
            DataIntegrityViolationException(
                "Cannot delete or update a parent row: a foreign key constraint fails " +
                    "(`talent`.`batch_send_task_config`, CONSTRAINT `fk_task_template`)"
            )
        )

        val ex = assertThrows(IllegalArgumentException::class.java) { service.delete(7L) }

        assertTrue(ex.message!!.contains("已被批量任务引用"), "message=${ex.message}")
        // the template row delete is the first write: a rejected delete leaves the blocks alone
        Mockito.verify(blockRepository, Mockito.never()).deleteAllByTemplateId(7L)
    }

    @Test
    fun `delete rethrows integrity failures that are not reference conflicts (I-3)`() {
        Mockito.`when`(templateRepository.findById(8L)).thenReturn(
            Optional.of(
                MailComposeTemplate(
                    id = 8,
                    templateName = "Intro",
                    subject = "Main",
                    mailType = "INTRODUCTION"
                )
            )
        )
        Mockito.`when`(templateRepository.deleteById(8L))
            .thenThrow(DataIntegrityViolationException("connection reset by peer"))

        assertThrows(DataIntegrityViolationException::class.java) { service.delete(8L) }
    }

    private fun customBlockCommand(order: Int, text: String): MailComposeTemplateBlockCommand =
        MailComposeTemplateBlockCommand(
            blockOrder = order,
            blockType = ComposeBlockType.CUSTOM_TEXT,
            customText = text
        )

    private class InMemoryTemplateStore {
        var template: MailComposeTemplate? = null
        val blocks = mutableListOf<MailComposeTemplateBlock>()
    }

    private fun stubInMemoryStore(store: InMemoryTemplateStore, id: Long = 5L) {
        Mockito.`when`(templateRepository.save(Mockito.any(MailComposeTemplate::class.java)))
            .thenAnswer { invocation ->
                val incoming = invocation.getArgument<MailComposeTemplate>(0)
                val persisted = if (incoming.id == null) incoming.copy(id = id) else incoming
                store.template = persisted
                persisted
            }
        Mockito.`when`(templateRepository.findById(id))
            .thenAnswer { Optional.ofNullable(store.template) }
        Mockito.`when`(templateRepository.findAllByOrderByIdAsc())
            .thenAnswer { listOfNotNull(store.template) }
        Mockito.`when`(blockRepository.save(Mockito.any(MailComposeTemplateBlock::class.java)))
            .thenAnswer { invocation ->
                val block = invocation.getArgument<MailComposeTemplateBlock>(0)
                store.blocks += block
                block
            }
        Mockito.`when`(blockRepository.findAllByTemplateIdOrderByBlockOrderAsc(id))
            .thenAnswer { store.blocks.sortedBy { it.blockOrder } }
        Mockito.`when`(blockRepository.deleteAllByTemplateId(id))
            .thenAnswer {
                val removed = store.blocks.size
                store.blocks.clear()
                removed
            }
    }

    @Test
    fun `render result exposes raw texts and template id for gate evaluation`() {
        Mockito.`when`(templateRepository.findByTemplateCodeAndEnabledTrue("INTRO"))
            .thenReturn(
                MailComposeTemplate(
                    id = 1,
                    templateCode = "INTRO",
                    templateName = "Intro",
                    subject = "Hello \${senderName}",
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
                        customText = "Topic: \${researchFields|Science}"
                    )
                )
            )

        val rendered = service.renderByCode("INTRO", mapOf("senderName" to "Chen", "researchFields" to ""))

        assertEquals("Hello Chen", rendered.subject)
        assertEquals(1L, rendered.templateId)
        // raw subject + raw block text, placeholders intact (I-3 input contract)
        assertEquals(listOf("Hello \${senderName}", "Topic: \${researchFields|Science}"), rendered.rawTexts)
    }
}
