package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
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

class LlmStitchServiceTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)
    private val replySnippetService = Mockito.mock(ReplySnippetService::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun provider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    private fun stubDefaultFrame() {
        Mockito.`when`(replySnippetService.resolveManualFrame()).thenReturn(
            ManualReplyFrame(
                salutation = "Dear Professor,",
                greeting = QaReplyComposer.GREETING,
                closing = QaReplyComposer.CLOSING,
                ackOptions = emptyList()
            )
        )
        Mockito.`when`(replySnippetService.resolveAck(Mockito.isNull())).thenReturn(null)
        Mockito.`when`(replySnippetService.resolveAck(Mockito.anyLong())).thenReturn(null)
    }

    @Test
    fun `returns deterministic draft when llm disabled`() {
        val properties = LlmProperties(enabled = false)
        val service = LlmStitchService(
            properties,
            provider(null),
            qaRuleRepository,
            replySnippetService
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 1,
            categoryId = 1,
            keywords = "salary",
            replyBody = "Salary info",
            replySubject = "Re",
            enabled = true
        )
        stubDefaultFrame()
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(1), "What is salary?", null)

        assertTrue(result.draftText.contains("Dear Professor,"))
        assertTrue(result.draftText.contains(QaReplyComposer.GREETING))
        assertTrue(result.draftText.contains("Salary info"))
        assertTrue(result.draftText.contains(QaReplyComposer.CLOSING))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `falls back to deterministic when llm client fails`() {
        val properties = LlmProperties(enabled = true, apiUrl = "http://llm")
        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? = null
        }
        val service = LlmStitchService(
            properties,
            provider(failingClient),
            qaRuleRepository,
            replySnippetService
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 2,
            categoryId = 1,
            keywords = "visa",
            replyBody = "Visa info",
            replySubject = "Re",
            enabled = true
        )
        stubDefaultFrame()
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(2), "Visa question?", "extra")

        assertTrue(result.draftText.contains("Visa info"))
        assertTrue(result.draftText.endsWith("extra"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `falls back to deterministic when llm client times out`() {
        val properties = LlmProperties(enabled = true, apiUrl = "http://llm")
        val timeoutClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? {
                throw ResourceAccessException("Read timed out")
            }
            override fun chat(messages: List<LlmChatMessage>, temperature: Double?): String? {
                throw ResourceAccessException("Read timed out")
            }
        }
        val service = LlmStitchService(
            properties,
            provider(timeoutClient),
            qaRuleRepository,
            replySnippetService
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 3,
            categoryId = 1,
            keywords = "fund",
            replyBody = "Funding info",
            replySubject = "Re",
            enabled = true
        )
        stubDefaultFrame()
        Mockito.`when`(qaRuleRepository.findById(3L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(3), "Funding?", null)

        assertTrue(result.draftText.contains("Funding info"))
        assertFalse(result.usedLlm)
    }

    @Test
    fun `deterministic draft omits section titles from rule segments`() {
        val properties = LlmProperties(enabled = false)
        val service = LlmStitchService(
            properties,
            provider(null),
            qaRuleRepository,
            replySnippetService
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 4,
            categoryId = 1,
            keywords = "role",
            replyBody = "Role details here",
            replySubject = "Re",
            sectionTitle = "Role & work style",
            enabled = true
        )
        stubDefaultFrame()
        Mockito.`when`(qaRuleRepository.findById(4L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(4), "What is the role?", null)

        assertFalse(result.draftText.contains("Role & work style"))
        assertTrue(result.draftText.contains("Role details here"))
        assertFalse(result.usedLlm)
    }
}
