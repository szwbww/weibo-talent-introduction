package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
import com.weibo.talentintroduction.qa.service.GapItem
import com.weibo.talentintroduction.qa.service.QaMatchService
import com.weibo.talentintroduction.qa.service.QaReplyComposer
import com.weibo.talentintroduction.reply.service.ManualReplyFrame
import com.weibo.talentintroduction.reply.service.ReplySnippetService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException
import java.util.Optional

class AiReplyDraftServiceTest {
    private val qaMatchService = Mockito.mock(QaMatchService::class.java)
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)
    private val aiPromptConfigService = Mockito.mock(AiPromptConfigService::class.java)
    private val aiTrainingDialogueService = Mockito.mock(AiTrainingDialogueService::class.java)
    private val aiReplyContextService = Mockito.mock(AiReplyContextService::class.java)

    init {
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(emptyList())
    }

    @Suppress("UNCHECKED_CAST")
    private fun provider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun stubDefaultFrame(salutation: String? = "Dear Professor,", greeting: String? = QaReplyComposer.GREETING) {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = salutation,
                greeting = greeting,
                closing = QaReplyComposer.CLOSING,
                ackOptions = emptyList()
            )
        )
    }

    private fun stubEmptyFrame() {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = null,
                greeting = null,
                closing = null,
                ackOptions = emptyList()
            )
        )
    }

    private fun emptyComposition(suggestedRuleIds: List<Long> = emptyList()) = CompositionSuggestResult(
        suggestedRuleIds = suggestedRuleIds,
        suggestedRules = emptyList(),
        rulesByCategory = emptyList(),
        gapItems = emptyList(),
        gapDetected = false,
        matchedCategoryIds = emptyList()
    )

    private fun stitchService(): LlmStitchService =
        LlmStitchService(
            LlmProperties(enabled = true),
            provider(null),
            qaRuleRepository,
            replySnippetService
        )

    private fun service(
        properties: LlmProperties,
        client: LlmDraftClient?,
        stitch: LlmStitchService = stitchService()
    ): AiReplyDraftService =
        AiReplyDraftService(
            properties,
            provider(client),
            qaMatchService,
            qaRuleRepository,
            stitch,
            replySnippetService,
            aiPromptConfigService,
            aiTrainingDialogueService,
            aiReplyContextService
        )

    private fun sampleRule(id: Long = 1L) = QaRule(
        id = id,
        categoryId = 1,
        keywords = "salary",
        replyBody = "Salary info",
        replySubject = "Re",
        enabled = true
    )

    @Test
    fun `returns deterministic draft when llm disabled`() {
        val rule = sampleRule()
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("What is salary?")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(1))
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Salary info"))
        assertFalse(result.usedLlm)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
    }

    @Test
    fun `falls back when llm client throws`() {
        val rule = sampleRule(2).copy(keywords = "visa", replyBody = "Visa info")
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Visa?")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(2))
        )
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw ResourceAccessException("Read timed out")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), failingClient).generate(
            inboundText = "Visa?",
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Visa info"))
        assertFalse(result.usedLlm)
        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
    }

    @Test
    fun `uses suggestComposition subset when qaRuleIds null`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Funding?")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(5))
        )
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                capturedTemperatures += temperature
                return "LLM draft"
            }
        }
        val rule = sampleRule(5).copy(replyBody = "Funding info", keywords = "fund")
        Mockito.`when`(qaRuleRepository.findById(5L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Funding?",
            operatorTurns = emptyList(),
            qaRuleIds = null
        )

        assertEquals(listOf(5L), result.qaRuleIds)
        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertTrue(result.usedLlm)
        Mockito.verify(qaMatchService).suggestComposition("Funding?")
        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("SEGMENT 1=Funding info"))
        assertTrue(userContent.contains("Dear Professor,"))
        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Preserve each SEGMENT wording"))
        assertEquals(0.3, capturedTemperatures.single())
    }

    @Test
    fun `free form mode when suggestComposition empty`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        val allRules = listOf(sampleRule(10), sampleRule(11).copy(id = 11, replyBody = "Rule 11"))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                capturedTemperatures += temperature
                return "Free form draft"
            }
        }

        val result = service(
            LlmProperties(enabled = true, apiUrl = "http://llm", freeFormTemperature = 0.6),
            client
        ).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "[OUTBOUND] Intro\nWelcome aboard"
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertTrue(result.usedLlm)
        val messages = capturedMessages.single()
        val systemPrompt = messages.first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("No QA rules matched"))
        assertFalse(systemPrompt.contains("Salary info"))
        val userContent = messages.first { it.role == "user" }.content
        assertTrue(userContent.contains("QA rule knowledge (authoritative facts):"))
        assertTrue(userContent.contains("Salary info"))
        assertTrue(userContent.contains("Rule 11"))
        assertTrue(
            userContent.contains(
                "Facts (figures, names, links, commitments) must come from the QA rule knowledge or training knowledge base above; do not invent specifics."
            )
        )
        assertTrue(userContent.contains("Name: Dr. Smith"))
        assertTrue(userContent.contains("Welcome aboard"))
        assertTrue(userContent.contains("Hello"))
        assertEquals(0.6, capturedTemperatures.single())
    }

    @Test
    fun `first turn fallback with no match returns empty send qaRuleIds`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        val allRules = listOf(sampleRule(10), sampleRule(11).copy(id = 11, replyBody = "Rule 11"))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertTrue(result.draftText.contains("Rule 11"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `continuation falls back to previous draft when llm unavailable`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Question")).thenReturn(emptyComposition(suggestedRuleIds = listOf(1)))
        val previousDraft = "Previous assistant draft"
        val turns = listOf(AiReplyTurn(assistantDraft = previousDraft, operatorInstruction = "more formal"))

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Question",
            operatorTurns = turns,
            qaRuleIds = listOf(1)
        )

        assertEquals(previousDraft, result.draftText)
        assertFalse(result.usedLlm)
        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
    }

    @Test
    fun `includes frame elements in matched user content when configured`() {
        stubDefaultFrame(salutation = "Dear Dr.", greeting = "Hope you are well.")
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition(suggestedRuleIds = listOf(1)))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Framed draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("SALUTATION=Dear Dr."))
        assertTrue(userContent.contains("GREETING=Hope you are well."))
    }

    @Test
    fun `skips frame elements in matched user content when frame empty`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition(suggestedRuleIds = listOf(1)))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Plain draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertFalse(userContent.contains("SALUTATION="))
        assertTrue(userContent.contains("SEGMENT 1=Salary info"))
    }

    @Test
    fun `injects first turn operator instruction into messages`() {
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Draft with instruction"
            }
        }
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            operatorInstruction = "Mention our flexible schedule"
        )

        val userMessages = capturedMessages.single().filter { it.role == "user" }
        assertTrue(userMessages.any { it.content.contains("Mention our flexible schedule") })
        assertTrue(userMessages.any { it.content.contains("Inbound email:") })
    }

    @Test
    fun `explicit qaRuleIds yields QA_MATCHED mode`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Unrelated text")).thenReturn(emptyComposition())
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Matched draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(3L)).thenReturn(Optional.of(sampleRule(3)))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Unrelated text",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(3)
        )

        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertEquals(listOf(3L), result.qaRuleIds)
        assertTrue(capturedMessages.single().first { it.role == "system" }.content.contains("Preserve each SEGMENT"))
    }

    @Test
    fun `uses configured free form prompt when present`() {
        stubEmptyFrame()
        val customPrompt = "Custom free-form prompt with extra constraints."
        Mockito.`when`(aiPromptConfigService.getEffectiveFreeFormSystemPrompt(Mockito.anyString()))
            .thenReturn(customPrompt)
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Configured draft"
            }
        }
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Custom free-form prompt"))
    }

    @Test
    fun `continuation locks mode and qaRuleIds via explicit qaRuleIds`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Question")).thenReturn(emptyComposition(suggestedRuleIds = listOf(7)))
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Revised draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(7L)).thenReturn(Optional.of(sampleRule(7).copy(replyBody = "Rule 7 body")))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val turns = listOf(AiReplyTurn(assistantDraft = "First draft", operatorInstruction = "shorter"))
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Question",
            operatorTurns = turns,
            qaRuleIds = listOf(7)
        )

        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertEquals(listOf(7L), result.qaRuleIds)
        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("SEGMENT 1=Rule 7 body"))
    }

    @Test
    fun `simulateOnly returns deterministic draft when llm disabled`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(qaMatchService.suggestComposition("What is the funding?")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is the funding?",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB",
            mailHistory = "[INBOUND] Question",
            simulateOnly = true
        )

        assertFalse(result.usedLlm)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertTrue(result.draftText.contains("12M RMB"))
    }

    @Test
    fun `simulateOnly falls back to generic draft without training knowledge`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertFalse(result.usedLlm)
        assertTrue(result.draftText.contains("Thank you for your email"))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
    }

    @Test
    fun `simulateOnly with matched rules yields QA_MATCHED and SEGMENT prompt`() {
        stubEmptyFrame()
        val rule = sampleRule(9).copy(replyBody = "First, you submit the required materials.")
        Mockito.`when`(qaMatchService.suggestComposition("what is the application process?")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(9))
        )
        Mockito.`when`(qaRuleRepository.findById(9L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Matched simulate draft"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "what is the application process?",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertEquals(listOf(9L), result.qaRuleIds)
        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("SEGMENT 1=First, you submit the required materials."))
    }

    @Test
    fun `simulateOnly without match injects full rule set into FREE_FORM knowledge`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("casual hello")).thenReturn(emptyComposition())
        val allRules = listOf(
            sampleRule(10).copy(replySubject = "Funding", replyBody = "Funding body"),
            sampleRule(11).copy(id = 11, replySubject = "Process", replyBody = "Process body")
        )
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Free form simulate"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "casual hello",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("QA rule knowledge (authoritative facts):"))
        assertTrue(userContent.contains("Funding body"))
        assertTrue(userContent.contains("Process body"))
    }

    @Test
    fun `free form knowledge section truncates to 12000 characters`() {
        stubEmptyFrame()
        val longBody = "X".repeat(8000)
        val rules = listOf(
            sampleRule(1).copy(replySubject = "A", replyBody = longBody),
            sampleRule(2).copy(id = 2, replySubject = "B", replyBody = longBody)
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rules[0]))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rules[1]))

        val content = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)
            .buildFreeFormUserContent(
                inboundText = "hi",
                expertProfile = null,
                mailHistory = null,
                promptRuleIds = listOf(1L, 2L)
            )

        val knowledgeStart = content.indexOf("QA rule knowledge (authoritative facts):")
        val constraintStart = content.indexOf(
            "Facts (figures, names, links, commitments) must come from the QA rule knowledge"
        )
        assertTrue(knowledgeStart >= 0)
        assertTrue(constraintStart > knowledgeStart)
        val knowledgeBlock = content.substring(
            knowledgeStart + "QA rule knowledge (authoritative facts):\n".length,
            constraintStart
        ).trimEnd('\n')
        assertTrue(knowledgeBlock.length <= 12000)
    }

    @Test
    fun `matched messages verbatim contract unchanged for same inputs`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)

        val first = draftService.buildMatchedMessages(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )
        val second = draftService.buildMatchedMessages(
            inboundText = "What is salary?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )

        assertEquals(first, second)
        assertTrue(first.any { it.content.contains("SEGMENT 1=Salary info") })
        assertFalse(first.any { it.content.contains("QA rule knowledge") })
    }

    @Test
    fun `free form injects few-shot without affecting qaRuleIds`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited and official?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(
            aiTrainingDialogueService.selectRelevantDialogues(
                "Are you accredited and official?",
                2
            )
        )
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "DIALOG_2143",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "Are you an officially accredited agency?"),
                            LlmChatMessage(role = "assistant", content = "Our government cooperation is documented.")
                        )
                    )
                )
            )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Few-shot draft"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited and official?",
            operatorTurns = emptyList()
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(listOf("DIALOG_2143"), result.fewShotDialogRefs)
        val messages = capturedMessages.single()
        assertTrue(messages[1].content.contains("officially accredited agency"))
        val system = messages.first { it.role == "system" }.content
        assertTrue(system.contains("structure, tone, and communication strategy"))
        assertTrue(system.contains("must not be used as a factual source"))
    }

    @Test
    fun `qa matched mode keeps fewShotDialogRefs empty`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Unrelated")).thenReturn(emptyComposition(suggestedRuleIds = listOf(3)))
        Mockito.`when`(qaRuleRepository.findById(3L)).thenReturn(Optional.of(sampleRule(3)))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Matched"
        }).generate(
            inboundText = "Unrelated",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(3)
        )

        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    @Test
    fun `free form without keyword match keeps messages unchanged`() {
        stubEmptyFrame()
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null)
        val baseline = draftService.buildFreeFormMessages(
            inboundText = "Hello without keywords",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "History"
        )
        val result = draftService.buildFreeFormMessages(
            inboundText = "Hello without keywords",
            operatorTurns = emptyList(),
            expertProfile = "Name: Dr. Smith",
            mailHistory = "History"
        )

        assertEquals(baseline.messages, result.messages)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
    }

    @Test
    fun `matched messages never include dialogue seed text`() {
        stubEmptyFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val messages = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).buildMatchedMessages(
            inboundText = "Are you accredited through another agency?",
            operatorTurns = emptyList(),
            promptRuleIds = listOf(1)
        )

        val joined = messages.joinToString("\n") { it.content }
        assertFalse(joined.contains("officially accredited agency"))
        assertFalse(joined.contains("reference examples"))
    }

    @Test
    fun `fallback draft excludes dialogue seed fragments when llm disabled`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited through another agency?"))
            .thenReturn(emptyComposition())
        val allRules = listOf(sampleRule(10))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Are you accredited through another agency?",
            operatorTurns = emptyList()
        )

        assertFalse(result.draftText.contains("government cooperation is documented"))
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    // ── New tests for T2 ──────────────────────────────────────────────────────

    @Test
    fun `single normal question with matching rule yields QA_MATCHED`() {
        stubDefaultFrame()
        val rule = sampleRule(1)
        Mockito.`when`(qaMatchService.suggestComposition("What is salary?")).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem("What is salary?", listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext("What is salary?")).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Matched draft"
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.requestCount)
        assertEquals(1, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Preserve each SEGMENT wording"))
    }

    @Test
    fun `single research question with matching rule yields QA_GROUNDED`() {
        stubDefaultFrame()
        val rule = sampleRule(1).copy(replyBody = "Research areas: AI, NLP")
        val inbound = "Does your research profile match our focus?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(inbound, listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(true)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Grounded draft"
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.requestCount)
        assertEquals(1, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
    }

    @Test
    fun `multi-request inbound with rules yields QA_GROUNDED`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is the visa process?"
        val rule1 = sampleRule(1)
        val rule2 = sampleRule(2).copy(replyBody = "Visa info", keywords = "visa")
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary?", listOf(1L)),
                    GapItem("- What is the visa process?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule1))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule2))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Grounded multi draft"
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L, 2L), result.qaRuleIds)
        assertEquals(2, result.requestCount)
        assertEquals(2, result.groundedRequestCount)
        assertTrue(result.unsupportedRequests.isEmpty())
    }

    @Test
    fun `explicit qaRuleIds with multi-request inbound stays QA_GROUNDED`() {
        stubEmptyFrame()
        val inbound = "- Question about funding?\n- Question about visa process?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- Question about funding?", listOf(1L)),
                    GapItem("- Question about visa process?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(sampleRule(2).copy(replyBody = "Visa info")))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Continuation grounded"
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1, 2)
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf(1L, 2L), result.qaRuleIds)
        assertEquals(2, result.requestCount)
    }

    @Test
    fun `QA_GROUNDED prompt contains requests facts and warnings without external URL claims`() {
        stubDefaultFrame()
        val inbound = "- What is salary?\n- Does your research profile match?"
        val rule = sampleRule(1)
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary?", listOf(1L)),
                    GapItem("- Does your research profile match?", listOf(1L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext("- What is salary?")).thenReturn(false)
        Mockito.`when`(aiReplyContextService.requiresResearchContext("- Does your research profile match?")).thenReturn(true)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Grounded reply"
            }
        }
        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            contextWarnings = listOf("EXPERT_PROFILE_PARTIAL"),
            expertProfile = "Expert in ML"
        )

        val messages = capturedMessages.single()
        val systemPrompt = messages.first { it.role == "system" }.content
        val userContent = messages.first { it.role == "user" }.content
        assertTrue(systemPrompt.contains("factual boundary"))
        assertFalse(systemPrompt.contains("SEGMENT"))
        // System prompt prohibits claiming external URL access; user content must not assert "I visited"
        assertTrue(systemPrompt.lowercase().contains("do not claim"))
        assertTrue(systemPrompt.lowercase().contains("google scholar"))

        assertTrue(userContent.contains("Request checklist"))
        assertTrue(userContent.contains("What is salary?"))
        assertTrue(userContent.contains("research profile"))
        assertTrue(userContent.contains("Salary info"))
        assertTrue(userContent.contains("EXPERT_PROFILE_PARTIAL"))
        assertTrue(userContent.contains("Expert in ML"))

        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues(inbound, 1)
    }

    @Test
    fun `simulateOnly flag does not affect FREE_FORM fallback text`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        val inbound = "What is the funding?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val profile = "Name: Dr. Test\nTraining knowledge base:\nTopic: Funding\nAnswer: Up to 12M RMB"
        val resultWithSimulate = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            expertProfile = profile,
            simulateOnly = true
        )
        val resultWithout = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            expertProfile = profile,
            simulateOnly = false
        )

        assertEquals(resultWithSimulate.draftText, resultWithout.draftText)
        assertTrue(resultWithSimulate.draftText.isNotEmpty())
    }

    @Test
    fun `FREE_FORM fallback is non-empty even without simulateOnly`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            simulateOnly = false
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertTrue(result.qaRuleIds.isEmpty())
        assertTrue(result.draftText.isNotEmpty())
    }

    @Test
    fun `research request with insufficient warning goes to unsupportedRequests`() {
        stubEmptyFrame()
        val inbound = "Does your research profile match our focus area?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(inbound, listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(true)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "Draft"
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList(),
            contextWarnings = listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT")
        )

        assertEquals(1, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(listOf(inbound), result.unsupportedRequests)
        assertTrue(result.contextWarnings.contains("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
    }

    @Test
    fun `same generate args produce equal LLM messages for both simulated entry points`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("What is salary?")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val allMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                allMessages += messages
                return "Draft"
            }
        }
        val draftService = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client)

        // Simulate the same call from two different entry points (simulate + aiReplyTurn)
        draftService.generate(inboundText = "What is salary?", operatorTurns = emptyList(), qaRuleIds = listOf(1))
        draftService.generate(inboundText = "What is salary?", operatorTurns = emptyList(), qaRuleIds = listOf(1))

        assertEquals(2, allMessages.size)
        assertEquals(allMessages[0], allMessages[1])
    }

    @Test
    fun `QA_GROUNDED uses freeFormTemperature`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is visa?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary?", listOf(1L)),
                    GapItem("- What is visa?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(sampleRule(2).copy(replyBody = "Visa info")))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val capturedTemperatures = mutableListOf<Double?>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedTemperatures += temperature
                return "Grounded draft"
            }
        }
        service(
            LlmProperties(enabled = true, apiUrl = "http://llm", temperature = 0.3, freeFormTemperature = 0.7),
            client
        ).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(0.7, capturedTemperatures.single())
    }

    @Test
    fun `QA_GROUNDED injects at most one style few-shot and returns its ref`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- What is visa?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary?", listOf(1L)),
                    GapItem("- What is visa?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(sampleRule(2).copy(replyBody = "Visa info")))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(inbound, 1)).thenReturn(
            listOf(
                SelectedDialogueFewShot(
                    sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                    messages = listOf(
                        LlmChatMessage(role = "user", content = "style expert"),
                        LlmChatMessage(role = "assistant", content = "style agent")
                    )
                )
            )
        )

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                capturedMessages += messages
                return "Grounded with style"
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.QA_GROUNDED, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues(inbound, 1)
        val system = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(system.contains("structure, tone, and communication strategy"))
        assertTrue(system.contains("must not be used as a factual source"))
        assertTrue(capturedMessages.single().any { it.content == "style expert" })
    }

    @Test
    fun `FREE_FORM requests max two style few-shots`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited and official?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited and official?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot("STYLE_TRUST_VERIFICATION", listOf(LlmChatMessage("user", "a"), LlmChatMessage("assistant", "b"))),
                    SelectedDialogueFewShot("STYLE_MATERIALS_BOUNDARY", listOf(LlmChatMessage("user", "c"), LlmChatMessage("assistant", "d")))
                )
            )

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "ok"
        }).generate(
            inboundText = "Are you accredited and official?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_TRUST_VERIFICATION", "STYLE_MATERIALS_BOUNDARY"), result.fewShotDialogRefs)
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues("Are you accredited and official?", 2)
    }

    @Test
    fun `fallback does not query dialogue service`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }
}
