package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.domain.QaRule
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.qa.service.CompositionSuggestResult
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
            aiTrainingDialogueService
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
            CompositionSuggestResult(
                suggestedRuleIds = listOf(1),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
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
            CompositionSuggestResult(
                suggestedRuleIds = listOf(2),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
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
            CompositionSuggestResult(
                suggestedRuleIds = listOf(5),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
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
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
        val allRules = listOf(sampleRule(10), sampleRule(11).copy(id = 11, replyBody = "Rule 11"))
        Mockito.`when`(qaRuleRepository.findAllEnabledOrdered()).thenReturn(allRules)

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
        assertTrue(userContent.contains("Name: Dr. Smith"))
        assertTrue(userContent.contains("Welcome aboard"))
        assertTrue(userContent.contains("Hello"))
        assertEquals(0.6, capturedTemperatures.single())
    }

    @Test
    fun `first turn fallback with no match returns empty send qaRuleIds`() {
        stubDefaultFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
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
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
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
        Mockito.verifyNoInteractions(qaMatchService)
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
        Mockito.`when`(qaMatchService.suggestComposition("Hello")).thenReturn(
            CompositionSuggestResult(
                suggestedRuleIds = emptyList(),
                suggestedRules = emptyList(),
                rulesByCategory = emptyList(),
                gapItems = emptyList(),
                gapDetected = false,
                matchedCategoryIds = emptyList()
            )
        )
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
        Mockito.verifyNoInteractions(qaMatchService)
        val userContent = capturedMessages.single().first { it.role == "user" }.content
        assertTrue(userContent.contains("SEGMENT 1=Rule 7 body"))
    }

    @Test
    fun `simulateOnly returns deterministic draft when llm disabled`() {
        stubDefaultFrame()
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)

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
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `simulateOnly falls back to generic draft without training knowledge`() {
        stubEmptyFrame()

        val result = service(LlmProperties(enabled = false), null).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            simulateOnly = true
        )

        assertFalse(result.usedLlm)
        assertTrue(result.draftText.contains("Thank you for your email"))
        assertEquals(emptyList<Long>(), result.qaRuleIds)
        Mockito.verifyNoInteractions(qaMatchService)
    }

    @Test
    fun `free form injects few-shot without affecting qaRuleIds`() {
        stubEmptyFrame()
        Mockito.`when`(qaMatchService.suggestComposition("Are you accredited and official?"))
            .thenReturn(
                CompositionSuggestResult(
                    suggestedRuleIds = emptyList(),
                    suggestedRules = emptyList(),
                    rulesByCategory = emptyList(),
                    gapItems = emptyList(),
                    gapDetected = false,
                    matchedCategoryIds = emptyList()
                )
            )
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
        assertTrue(messages.first { it.role == "system" }.content.contains("reference examples"))
    }

    @Test
    fun `qa matched mode keeps fewShotDialogRefs empty`() {
        stubEmptyFrame()
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
            .thenReturn(
                CompositionSuggestResult(
                    suggestedRuleIds = emptyList(),
                    suggestedRules = emptyList(),
                    rulesByCategory = emptyList(),
                    gapItems = emptyList(),
                    gapDetected = false,
                    matchedCategoryIds = emptyList()
                )
            )
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
}
