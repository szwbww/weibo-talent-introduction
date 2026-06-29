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
            replySnippetService
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
            override fun chat(messages: List<LlmChatMessage>): String? {
                throw ResourceAccessException("Read timed out")
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), failingClient).generate(
            inboundText = "Visa?",
            operatorTurns = emptyList()
        )

        assertTrue(result.draftText.contains("Visa info"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `uses suggestComposition subset when qaRuleIds null`() {
        stubEmptyFrame()
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
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>): String? {
                capturedMessages += messages
                return "LLM draft"
            }
        }
        val rule = sampleRule(5).copy(replyBody = "Funding info", keywords = "fund")
        Mockito.`when`(qaRuleRepository.findById(5L)).thenReturn(Optional.of(rule))

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Funding?",
            operatorTurns = emptyList(),
            qaRuleIds = null
        )

        assertEquals(listOf(5L), result.qaRuleIds)
        assertTrue(result.usedLlm)
        Mockito.verify(qaMatchService).suggestComposition("Funding?")
        assertTrue(capturedMessages.single().any { it.role == "system" && it.content.contains("Funding info") })
    }

    @Test
    fun `uses all enabled rules in prompt but empty send qaRuleIds when suggestComposition empty`() {
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
        Mockito.`when`(qaRuleRepository.findById(10L)).thenReturn(Optional.of(allRules[0]))
        Mockito.`when`(qaRuleRepository.findById(11L)).thenReturn(Optional.of(allRules[1]))

        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>): String? {
                capturedMessages += messages
                return "All rules draft"
            }
        }

        val result = service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList()
        )

        assertEquals(emptyList<Long>(), result.qaRuleIds)
        assertTrue(result.usedLlm)
        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Salary info"))
        assertTrue(systemPrompt.contains("Rule 11"))
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
    }

    @Test
    fun `includes frame guidance in system prompt when configured`() {
        stubDefaultFrame(salutation = "Dear Dr.", greeting = "Hope you are well.")
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>): String? {
                capturedMessages += messages
                return "Framed draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertTrue(systemPrompt.contains("Dear Dr."))
        assertTrue(systemPrompt.contains("Hope you are well."))
    }

    @Test
    fun `skips frame guidance when frame empty`() {
        stubEmptyFrame()
        val capturedMessages = mutableListOf<List<LlmChatMessage>>()
        val client = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>): String? {
                capturedMessages += messages
                return "Plain draft"
            }
        }
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(sampleRule()))

        service(LlmProperties(enabled = true, apiUrl = "http://llm"), client).generate(
            inboundText = "Hello",
            operatorTurns = emptyList(),
            qaRuleIds = listOf(1)
        )

        val systemPrompt = capturedMessages.single().first { it.role == "system" }.content
        assertFalse(systemPrompt.contains("Style guidance"))
    }
}
