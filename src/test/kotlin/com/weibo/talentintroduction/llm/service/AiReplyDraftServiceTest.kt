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

    private fun pointByPointComposer(): AiReplyPointByPointComposer =
        AiReplyPointByPointComposer(qaRuleRepository, replySnippetService)

    private fun service(
        properties: LlmProperties,
        client: LlmDraftClient?,
        stitch: LlmStitchService = stitchService(),
        pointByPoint: AiReplyPointByPointComposer = pointByPointComposer()
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
            aiReplyContextService,
            pointByPoint
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
        assertEquals(AiReplyGenerationState.FALLBACK_LLM_DISABLED, result.generationState)
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
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, result.generationState)
        assertEquals(AiReplyMode.QA_MATCHED, result.mode)
    }

    @Test
    fun `generationState truth table matches usedLlm for all four branches`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("What is salary?")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(1))
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val disabled = service(LlmProperties(enabled = false), null).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )
        assertFalse(disabled.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_LLM_DISABLED, disabled.generationState)

        val nullClient = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )
        assertFalse(nullClient.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE, nullClient.generationState)

        val emptyClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "   "
        }
        val noResponse = service(LlmProperties(enabled = true, apiUrl = "http://llm"), emptyClient).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )
        assertFalse(noResponse.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_NO_RESPONSE, noResponse.generationState)

        val okClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "LLM polished draft"
        }
        val used = service(LlmProperties(enabled = true, apiUrl = "http://llm"), okClient).generate(
            inboundText = "What is salary?",
            operatorTurns = emptyList()
        )
        assertTrue(used.usedLlm)
        assertEquals(AiReplyGenerationState.LLM_USED, used.generationState)
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

        assertTrue(userContent.contains("REQUEST 1"))
        assertTrue(userContent.contains("REQUEST 2"))
        assertTrue(userContent.contains("TEXT: - What is salary?"))
        assertTrue(userContent.contains("TEXT: - Does your research profile match?"))
        assertTrue(userContent.contains("STATUS: GROUNDED"))
        assertTrue(userContent.contains("APPROVED FACTS FOR REQUEST 1:"))
        assertTrue(userContent.contains("APPROVED FACTS FOR REQUEST 2:"))
        assertTrue(userContent.indexOf("TEXT: - What is salary?") < userContent.indexOf("STATUS: GROUNDED"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("TEXT: - What is salary?"))
        assertTrue(userContent.contains("What is salary?"))
        assertTrue(userContent.contains("research profile"))
        assertTrue(userContent.contains("Salary info"))
        assertTrue(userContent.contains("EXPERT_PROFILE_PARTIAL"))
        assertTrue(userContent.contains("Expert in ML"))
        assertFalse(userContent.contains("Request checklist"))
        assertFalse(userContent.contains("Matched QA answers"))

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

    @Test
    fun `enabled with null client skips few-shot selection and returns empty refs`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(AiReplyGenerationState.FALLBACK_CLIENT_UNAVAILABLE, result.generationState)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        Mockito.verifyNoInteractions(aiTrainingDialogueService)
    }

    @Test
    fun `chat exception clears fewShotDialogRefs on fallback`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_TRUST_VERIFICATION",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "style expert verify"),
                            LlmChatMessage(role = "assistant", content = "UNIQUE_STYLE_AGENT_SNIPPET_XYZ")
                        )
                    )
                )
            )

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw RuntimeException("LLM down")
            }
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        assertFalse(result.draftText.contains("UNIQUE_STYLE_AGENT_SNIPPET_XYZ"))
        Mockito.verify(aiTrainingDialogueService).selectRelevantDialogues("Are you accredited?", 2)
    }

    @Test
    fun `blank chat response clears fewShotDialogRefs on fallback`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited?"))
            .thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues("Are you accredited?", 2))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MATERIALS_BOUNDARY",
                        messages = listOf(
                            LlmChatMessage(role = "user", content = "style expert materials"),
                            LlmChatMessage(role = "assistant", content = "BLANK_FALLBACK_STYLE_SNIPPET")
                        )
                    )
                )
            )

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "   "
        }
        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Are you accredited?",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertEquals(emptyList<String>(), result.fewShotDialogRefs)
        assertFalse(result.draftText.contains("BLANK_FALLBACK_STYLE_SNIPPET"))
    }

    private val expertDiligenceMail = """
        Thank you for your message. Here are my research profiles:
        https://scholar.google.com/citations?user=OT-O6joAAAAJ&hl=en
        https://www.scopus.com/authid/detail.uri?authorId=57201234567

        Could you please confirm whether my research background fits the enterprise projects you manage?

        Specifically:
        - What is the registered location of your company?
        - What are the expected responsibilities and deliverables?
        - How are researchers selected and matched within the scope of enterprise projects?
        - What are the intellectual property arrangements?
        - What are the next stages of the application?
        - What materials should I send?

        Best regards
    """.trimIndent()

    @Test
    fun `retries once then accepts cleaned draft without warning`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition(expertDiligenceMail)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return if (chatCount == 1) {
                    "Thank you for writing. Please send your CV when convenient."
                } else {
                    "Thank you for writing. I will answer your programme questions from approved information."
                }
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = expertDiligenceMail,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
    }

    @Test
    fun `two violating drafts sanitize and keep metadata with warning`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition(expertDiligenceMail)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Thank you. Please send your CV. Let us schedule a meeting next week. Applicants submit materials for review after matching."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = expertDiligenceMail,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("Let us schedule a meeting", ignoreCase = true))
        assertTrue(result.draftText.contains("Applicants submit materials for review after matching"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        assertEquals(0, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(emptyList<String>(), result.unsupportedRequests)
        Mockito.verify(aiTrainingDialogueService, Mockito.times(1))
            .selectRelevantDialogues(expertDiligenceMail, 2)
    }

    @Test
    fun `disabled fallback strips unauthorized CTA without calling client`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(
            emptyComposition(suggestedRuleIds = listOf(1))
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule().copy(replyBody = "Please send your CV for matching. Applicants submit materials for review."))
        )
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)

        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? =
                error("should not call")
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? =
                error("should not call")
        }

        val result = service(LlmProperties(enabled = false), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        assertFalse(result.usedLlm)
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertTrue(result.draftText.contains("Applicants submit materials for review"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
    }

    @Test
    fun `explicit meeting request allows meeting action without retry`() {
        stubEmptyFrame()
        val inbound = "Can we arrange a meeting next week?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Happy to arrange a meeting. Please share a convenient time."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(1, chatCount)
        assertTrue(result.usedLlm)
        assertTrue(result.draftText.contains("convenient time"))
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
    }

    @Test
    fun `replyModel null defaults to flash and pro maps provider id through chatWithModel`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val capturedModels = mutableListOf<String>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = "unused"
            override fun chatWithModel(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): String? {
                capturedModels += providerModel
                return "Draft for $providerModel"
            }
        }
        val props = LlmProperties(
            enabled = true,
            apiUrl = "http://llm",
            replyFlashModel = "flash-id",
            replyProModel = "pro-id"
        )

        val flash = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = null
        )
        assertEquals(AiReplyModel.DEEPSEEK_V4_FLASH.name, flash.selectedModel)
        assertEquals(listOf("flash-id"), capturedModels)

        capturedModels.clear()
        val pro = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, pro.selectedModel)
        assertEquals(listOf("pro-id"), capturedModels)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service(props, client).generate(
                inboundText = "Hello",
                operatorTurns = emptyList(),
                replyModel = "DEEPSEEK_UNKNOWN"
            )
        }
    }

    @Test
    fun `action retry reuses the same provider model and fallback echoes selectedModel`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())

        val capturedModels = mutableListOf<String>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
            override fun chatWithModel(
                messages: List<LlmChatMessage>,
                temperature: Double?,
                providerModel: String
            ): String? {
                capturedModels += providerModel
                return if (capturedModels.size == 1) {
                    "Please send your CV when convenient."
                } else {
                    "Thank you. I will follow up with approved information."
                }
            }
        }
        val props = LlmProperties(
            enabled = true,
            apiUrl = "http://llm",
            replyFlashModel = "flash-id",
            replyProModel = "pro-id"
        )
        val result = service(props, client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertEquals(listOf("pro-id", "pro-id"), capturedModels)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, result.selectedModel)
        assertTrue(result.usedLlm)

        val fallback = service(LlmProperties(enabled = false, replyProModel = "pro-id"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            replyModel = "DEEPSEEK_V4_PRO"
        )
        assertFalse(fallback.usedLlm)
        assertEquals(AiReplyModel.DEEPSEEK_V4_PRO.name, fallback.selectedModel)
        assertEquals(2, capturedModels.size) // no additional client calls on disabled path
    }

    @Test
    fun `interrogative CV CTA is sanitized with metadata preserved`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition(expertDiligenceMail)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return "Thank you for writing. Could you share your CV? Would you mind forwarding your résumé? " +
                    "Applicants submit materials for review after matching."
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = expertDiligenceMail,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Could you share your CV", ignoreCase = true))
        assertFalse(result.draftText.contains("résumé", ignoreCase = true))
        assertTrue(result.draftText.contains("Applicants submit materials for review after matching"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(AiReplyGenerationState.LLM_USED, result.generationState)
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        assertEquals(0, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(emptyList<String>(), result.unsupportedRequests)
    }

    @Test
    fun `safe multi-paragraph draft preserved byte-for-byte through final gate`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition(expertDiligenceMail)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        val safeDraft = """
            Dear Dr. Smith,

            Thank you for your interest in our program. Here are answers to your questions:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent() + "\n"

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return safeDraft
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = expertDiligenceMail,
            operatorTurns = emptyList()
        )

        assertEquals(1, chatCount)
        assertTrue(result.usedLlm)
        assertEquals(safeDraft, result.draftText)
        assertFalse(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        assertEquals(0, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(emptyList<String>(), result.unsupportedRequests)
    }

    @Test
    fun `retry still violating multi-paragraph draft keeps structure with warning`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition(expertDiligenceMail)).thenReturn(emptyComposition())
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(emptyList())
        Mockito.`when`(aiTrainingDialogueService.selectRelevantDialogues(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(
                listOf(
                    SelectedDialogueFewShot(
                        sourceRef = "STYLE_MULTI_DUE_DILIGENCE",
                        messages = listOf(
                            LlmChatMessage("user", "example expert"),
                            LlmChatMessage("assistant", "example agent")
                        )
                    )
                )
            )

        val violatingDraft = """
            Dear Dr. Smith,

            Thank you for your interest. Answers below:

            1. The company is registered in Beijing.
            2. Expected deliverables are defined per project scope.
            Could you share your CV?
            3. Researchers are matched by domain expertise.
            4. Intellectual property follows the signed agreement.
            5. Next stages include document review and interview.
            6. Early-stage materials are a short CV and research summary.

            Best regards,
            Talent Introduction Team
        """.trimIndent()

        var chatCount = 0
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                chatCount++
                return violatingDraft
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = expertDiligenceMail,
            operatorTurns = emptyList()
        )

        assertEquals(2, chatCount)
        assertTrue(result.usedLlm)
        assertFalse(result.draftText.contains("Could you share your CV", ignoreCase = true))
        assertTrue(result.draftText.contains("Dear Dr. Smith,"))
        assertTrue(result.draftText.contains("1. The company is registered in Beijing."))
        assertTrue(result.draftText.contains("2. Expected deliverables are defined per project scope."))
        assertTrue(result.draftText.contains("3. Researchers are matched by domain expertise."))
        assertTrue(result.draftText.contains("6. Early-stage materials are a short CV and research summary."))
        assertTrue(result.draftText.contains("Best regards,"))
        assertTrue(result.draftText.contains("\n\n"))
        assertTrue(result.draftText.contains("1. The company is registered in Beijing.\n"))
        assertTrue(result.draftText.contains("\nBest regards,"))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertEquals(AiReplyMode.FREE_FORM, result.mode)
        assertEquals(listOf("STYLE_MULTI_DUE_DILIGENCE"), result.fewShotDialogRefs)
        assertEquals(0, result.requestCount)
        assertEquals(0, result.groundedRequestCount)
        assertEquals(emptyList<String>(), result.unsupportedRequests)
        Mockito.verify(aiTrainingDialogueService, Mockito.times(1))
            .selectRelevantDialogues(expertDiligenceMail, 2)
    }

    // ── Phase 2: request fact matrix ──────────────────────────────────────────

    @Test
    fun `resolveQaRules maps gapItems 1-to-1 with stable index order`() {
        val inbound = "- Salary?\n- Visa?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- Salary?", listOf(1L)),
                    GapItem("- Visa?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(sampleRule(2).copy(replyBody = "Visa")))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(2, resolved.requestFacts.size)
        assertEquals(1, resolved.requestFacts[0].index)
        assertEquals("- Salary?", resolved.requestFacts[0].requestText)
        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts[0].status)
        assertEquals(2, resolved.requestFacts[1].index)
        assertEquals("- Visa?", resolved.requestFacts[1].requestText)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertEquals(listOf("- Salary?", "- Visa?"), resolved.requestFacts.map { it.requestText })
    }

    @Test
    fun `resolveQaRules intersects candidates with promptRuleIds per item only`() {
        val inbound = "- A?\n- B?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2, 3),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- A?", listOf(1L, 9L)),
                    GapItem("- B?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(sampleRule(2)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(2L), resolved.requestFacts[1].factRuleIds)
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(2L))
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(3L))
        assertFalse(resolved.requestFacts[0].factRuleIds.contains(9L))
    }

    @Test
    fun `resolveQaRules marks PARTIAL when detail phrase missing from all fact rules`() {
        val request = "What are the expected deliverables?"
        Mockito.`when`(qaMatchService.suggestComposition(request)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(request, listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replySubject = "Scope", replyBody = "High-level project overview."))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.PARTIAL, resolved.requestFacts.single().status)
        assertEquals(1, resolved.groundedRequestCount)
        assertTrue(resolved.unsupportedRequests.isEmpty())
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("deliverables"))
    }

    @Test
    fun `resolveQaRules marks GROUNDED not PARTIAL when fact rule missing from repository`() {
        val request = "What are the expected deliverables?"
        Mockito.`when`(qaMatchService.suggestComposition(request)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(99),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(request, listOf(99L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(99L)).thenReturn(Optional.empty())

        val draftService = service(LlmProperties(enabled = false), null)
        val resolved = draftService.resolveQaRules(request, null)

        assertEquals(listOf(99L), resolved.requestFacts.single().factRuleIds)
        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts.single().status)
        assertFalse(draftService.isPartialCoverage(request, listOf(99L)))
    }

    @Test
    fun `explicit qaRuleIds intersects gap candidates into factRuleIds and sendQaRuleIds`() {
        val inbound = "What about salary and benefits?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(inbound, listOf(1L, 2L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(inbound)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, listOf(1L))

        assertEquals(listOf(1L), resolved.requestFacts.single().factRuleIds)
        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertEquals(listOf(1L), resolved.promptRuleIds)
    }

    @Test
    fun `resolveQaRules marks GROUNDED when detail phrase covered by rule body`() {
        val request = "What are the expected Deliverables for this role?"
        Mockito.`when`(qaMatchService.suggestComposition(request)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(request, listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replySubject = "Role", replyBody = "Expected deliverables are defined per project."))
        )

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.GROUNDED, resolved.requestFacts.single().status)
    }

    @Test
    fun `resolveQaRules marks UNSUPPORTED when factRuleIds empty for non-research`() {
        val request = "What is the coffee policy?"
        Mockito.`when`(qaMatchService.suggestComposition(request)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(request, emptyList())),
                gapDetected = true,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(listOf(sampleRule(10).copy(id = 10)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(request, null)

        assertEquals(RequestGroundingStatus.UNSUPPORTED, resolved.requestFacts.single().status)
        assertEquals(emptyList<Long>(), resolved.requestFacts.single().factRuleIds)
        assertEquals(listOf(request), resolved.unsupportedRequests)
        assertEquals(0, resolved.groundedRequestCount)
        assertEquals(emptyList<Long>(), resolved.sendQaRuleIds)
        assertEquals(listOf(10L), resolved.promptRuleIds)
    }

    @Test
    fun `research request uses warning only and never invents factRuleIds`() {
        val request = "Does my research profile match?"
        Mockito.`when`(qaMatchService.suggestComposition(request)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem(request, listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(request)).thenReturn(true)

        val sufficient = service(LlmProperties(enabled = false), null)
            .resolveQaRules(request, null, emptyList())
        assertEquals(RequestGroundingStatus.GROUNDED, sufficient.requestFacts.single().status)
        assertEquals(emptyList<Long>(), sufficient.requestFacts.single().factRuleIds)
        assertEquals(1, sufficient.groundedRequestCount)

        val insufficient = service(LlmProperties(enabled = false), null)
            .resolveQaRules(request, null, listOf("EXPERT_RESEARCH_CONTEXT_INSUFFICIENT"))
        assertEquals(RequestGroundingStatus.UNSUPPORTED, insufficient.requestFacts.single().status)
        assertEquals(emptyList<Long>(), insufficient.requestFacts.single().factRuleIds)
        assertEquals(listOf(request), insufficient.unsupportedRequests)
    }

    @Test
    fun `resolveQaRules does not invent URL-only request facts beyond gapItems`() {
        val inbound = "See https://scholar.google.com/citations?user=abc\n- What is salary?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(GapItem("- What is salary?", listOf(1L))),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(1, resolved.requestFacts.size)
        assertFalse(resolved.requestFacts.any { it.requestText.contains("scholar.google") })
        assertEquals("- What is salary?", resolved.requestFacts.single().requestText)
    }

    @Test
    fun `shared rule across two items does not inflate sendQaRuleIds`() {
        val inbound = "- What is salary band?\n- What is salary currency?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary band?", listOf(1L)),
                    GapItem("- What is salary currency?", listOf(1L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))

        val resolved = service(LlmProperties(enabled = false), null).resolveQaRules(inbound, null)

        assertEquals(listOf(1L), resolved.sendQaRuleIds)
        assertEquals(listOf(1L), resolved.requestFacts[0].factRuleIds)
        assertEquals(listOf(1L), resolved.requestFacts[1].factRuleIds)
        assertEquals(2, resolved.requestCount)
        assertEquals(2, resolved.groundedRequestCount)
    }

    @Test
    fun `normalizeCoverageText collapses case and whitespace`() {
        val draftService = service(LlmProperties(enabled = false), null)
        assertEquals(
            "registered location",
            draftService.normalizeCoverageText("  Registered   Location  ")
        )
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("registered location"))
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("full name"))
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("exact"))
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("full terms"))
        assertTrue(AiReplyDraftService.PARTIAL_DETAIL_PHRASES.contains("financial arrangements"))
    }

    @Test
    fun `generate exposes requestFacts on AiReplyDraftResult`() {
        stubEmptyFrame()
        val inbound = "- What is salary?\n- Unknown topic?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- What is salary?", listOf(1L)),
                    GapItem("- Unknown topic?", emptyList())
                ),
                gapDetected = true,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule(1)))
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertEquals(2, result.requestFacts.size)
        assertEquals(RequestGroundingStatus.GROUNDED, result.requestFacts[0].status)
        assertEquals(RequestGroundingStatus.UNSUPPORTED, result.requestFacts[1].status)
        assertEquals(listOf(1L), result.qaRuleIds)
        assertEquals(1, result.groundedRequestCount)
        assertEquals(listOf("- Unknown topic?"), result.unsupportedRequests)
    }

    @Test
    fun `multi-request grounded prompt emits per-request fact blocks in order`() {
        stubDefaultFrame(salutation = "Dear \${expertName|Professor},")
        val inbound = (1..7).joinToString("\n") { "- Question $it?" }
        val gapItems = (1..7).map { idx ->
            GapItem(
                "- Question $idx?",
                if (idx == 7) emptyList() else listOf(idx.toLong())
            )
        }
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = (1L..6L).toList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = gapItems,
                gapDetected = true,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        (1L..6L).forEach { id ->
            Mockito.`when`(qaRuleRepository.findById(id)).thenReturn(
                Optional.of(
                    sampleRule(id).copy(
                        keywords = "q$id",
                        replyBody = "Unique body for rule $id",
                        replySubject = "Subject $id"
                    )
                )
            )
        }
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val captured = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                captured += messages
                return "LLM multi draft"
            }
        }
        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        val systemPrompt = captured.single().first { it.role == "system" }.content
        val userContent = captured.single().first { it.role == "user" }.content
        assertTrue(systemPrompt.contains("one numbered section per request"))
        assertTrue(systemPrompt.contains("Do not crush the reply into at most 4 paragraphs"))
        assertTrue(systemPrompt.contains("plain-text email"))
        assertFalse(systemPrompt.contains("Keep the reply to at most 4 paragraphs."))

        (1..7).forEach { n ->
            assertTrue(userContent.contains("REQUEST $n"))
            assertTrue(userContent.contains("APPROVED FACTS FOR REQUEST $n:"))
            assertTrue(userContent.contains("TEXT: - Question $n?"))
        }
        assertTrue(userContent.contains("STATUS: UNSUPPORTED"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("TEXT: - Question 1?"))
        assertTrue(userContent.indexOf("TEXT: - Question 1?") < userContent.indexOf("APPROVED FACTS FOR REQUEST 1:"))
        assertTrue(userContent.indexOf("REQUEST 1") < userContent.indexOf("REQUEST 2"))
        assertTrue(userContent.indexOf("REQUEST 6") < userContent.indexOf("REQUEST 7"))
        assertTrue(userContent.contains("Unique body for rule 1"))
        assertTrue(userContent.contains("Unique body for rule 6"))
        assertFalse(userContent.contains("Unique body for rule 1\nUnique body for rule 2"))
        // Request 1 facts must not include rule 2 body in its block
        val req1Block = userContent.substringAfter("APPROVED FACTS FOR REQUEST 1:")
            .substringBefore("REQUEST 2")
        assertTrue(req1Block.contains("Unique body for rule 1"))
        assertFalse(req1Block.contains("Unique body for rule 2"))
        val req7Block = userContent.substringAfter("APPROVED FACTS FOR REQUEST 7:")
            .substringBefore("Expert profile")
            .ifBlank {
                userContent.substringAfter("APPROVED FACTS FOR REQUEST 7:")
                    .substringBefore("Context warnings")
                    .ifBlank { userContent.substringAfter("APPROVED FACTS FOR REQUEST 7:").substringBefore("CLOSING=") }
            }
        assertTrue(req7Block.contains("(none)"))
        assertTrue(userContent.contains("Dear \${expertName|Professor},"))
    }

    @Test
    fun `multi-request fallback is isomorphic across llm disabled null client and empty response`() {
        stubDefaultFrame(salutation = "Dear \${expertName|Professor},")
        val inbound = "- Salary?\n- Visa?\n- Unknown?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- Salary?", listOf(1L)),
                    GapItem("- Visa?", listOf(2L)),
                    GapItem("- Unknown?", emptyList())
                ),
                gapDetected = true,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "Salary only body"))
        )
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(
            Optional.of(sampleRule(2).copy(keywords = "visa", replyBody = "Visa only body"))
        )
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val stitch = Mockito.spy(stitchService())
        val composer = pointByPointComposer()
        val expected = composer.compose(
            listOf(
                RequestFactItem(1, "- Salary?", listOf(1L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(2, "- Visa?", listOf(2L), RequestGroundingStatus.GROUNDED),
                RequestFactItem(3, "- Unknown?", emptyList(), RequestGroundingStatus.UNSUPPORTED)
            )
        )

        val disabled = service(LlmProperties(enabled = false), null, stitch, composer).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        val nullClient = service(LlmProperties(enabled = true, apiUrl = "http://llm"), null, stitch, composer).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )
        val emptyClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
        }
        val noResponse = service(
            LlmProperties(enabled = true, apiUrl = "http://llm"),
            emptyClient,
            stitch,
            composer
        ).generate(inboundText = inbound, operatorTurns = emptyList())

        assertEquals(expected, disabled.draftText)
        assertEquals(expected, nullClient.draftText)
        assertEquals(expected, noResponse.draftText)
        assertTrue(disabled.draftText.contains("1. Salary"))
        assertTrue(disabled.draftText.contains("2. Visa"))
        assertTrue(disabled.draftText.contains("3. Unknown"))
        assertTrue(disabled.draftText.indexOf("1. Salary") < disabled.draftText.indexOf("2. Visa"))
        assertTrue(disabled.draftText.indexOf("2. Visa") < disabled.draftText.indexOf("3. Unknown"))
        val section1 = disabled.draftText.substringAfter("1. Salary").substringBefore("2. Visa")
        val section2 = disabled.draftText.substringAfter("2. Visa").substringBefore("3. Unknown")
        val section3 = disabled.draftText.substringAfter("3. Unknown")
        assertTrue(section1.contains("Salary only body"))
        assertFalse(section1.contains("Visa only body"))
        assertTrue(section2.contains("Visa only body"))
        assertFalse(section2.contains("Salary only body"))
        assertTrue(section3.contains(AiReplyPointByPointComposer.UNSUPPORTED_TEXT))
        assertTrue(disabled.draftText.contains("Dear \${expertName|Professor},"))
        assertFalse(disabled.usedLlm)
        Mockito.verify(stitch, Mockito.never()).composeDeterministicDraft(
            Mockito.anyList(),
            Mockito.nullable(String::class.java),
            Mockito.nullable(Long::class.java)
        )
    }

    @Test
    fun `multi-request structured fallback still strips unauthorized CTA via action policy`() {
        stubDefaultFrame()
        val inbound = "- Salary?\n- Visa?"
        Mockito.`when`(qaMatchService.suggestComposition(inbound)).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1, 2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = listOf(
                    GapItem("- Salary?", listOf(1L)),
                    GapItem("- Visa?", listOf(2L))
                ),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        Mockito.`when`(aiReplyContextService.requiresResearchContext(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(
            Optional.of(sampleRule(1).copy(replyBody = "Salary info. Please send your CV."))
        )
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(
            Optional.of(sampleRule(2).copy(keywords = "visa", replyBody = "Visa info"))
        )
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = inbound,
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("1. Salary"))
        assertTrue(result.draftText.contains("2. Visa"))
        assertFalse(result.draftText.contains("Please send your CV", ignoreCase = true))
        assertTrue(result.contextWarnings.contains(AiReplyDraftService.UNAUTHORIZED_ACTION_REMOVED))
    }
}
