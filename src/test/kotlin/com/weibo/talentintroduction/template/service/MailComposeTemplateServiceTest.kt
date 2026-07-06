package com.weibo.talentintroduction.template.service

import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.reply.repository.ReplySnippetRepository
import com.weibo.talentintroduction.template.domain.ComposeBlockType
import com.weibo.talentintroduction.template.domain.MailComposeTemplate
import com.weibo.talentintroduction.template.domain.MailComposeTemplateBlock
import com.weibo.talentintroduction.template.repository.MailComposeTemplateBlockRepository
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class MailComposeTemplateServiceTest {
    private val templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java)
    private val blockRepository = Mockito.mock(MailComposeTemplateBlockRepository::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetRepository = Mockito.mock(ReplySnippetRepository::class.java)
    private val service = MailComposeTemplateService(
        templateRepository,
        blockRepository,
        qaRuleRepository,
        replySnippetRepository
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
}
