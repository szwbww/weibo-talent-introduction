package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.LlmProperties
import com.weibo.talentintroduction.qa.repository.QaRuleRepository
import com.weibo.talentintroduction.llm.service.LlmDraftClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.web.client.ResourceAccessException
import java.util.Optional

class LlmStitchServiceTest {
    private val qaRuleRepository = Mockito.mock(QaRuleRepository::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun provider(client: LlmDraftClient?): ObjectProvider<LlmDraftClient> {
        val mock = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<LlmDraftClient>
        Mockito.`when`(mock.getIfAvailable()).thenReturn(client)
        return mock
    }

    @Test
    fun `returns deterministic draft when llm disabled`() {
        val properties = LlmProperties(enabled = false)
        val service = LlmStitchService(
            properties,
            provider(null),
            qaRuleRepository
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 1,
            categoryId = 1,
            keywords = "salary",
            replyBody = "Salary info",
            replySubject = "Re",
            enabled = true
        )
        Mockito.`when`(qaRuleRepository.findById(1L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(1), "What is salary?", null)

        assertEquals("Salary info", result.draftText)
        assertFalse(result.usedLlm)
    }

    @Test
    fun `falls back to deterministic when llm client fails`() {
        val properties = LlmProperties(enabled = true, apiUrl = "http://llm")
        val failingClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? = null
        }
        val service = LlmStitchService(
            properties,
            provider(failingClient),
            qaRuleRepository
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 2,
            categoryId = 1,
            keywords = "visa",
            replyBody = "Visa info",
            replySubject = "Re",
            enabled = true
        )
        Mockito.`when`(qaRuleRepository.findById(2L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(2), "Visa question?", "extra")

        assertEquals("Visa info\n\nextra", result.draftText)
        assertFalse(result.usedLlm)
    }

    @Test
    fun `falls back to deterministic when llm client times out`() {
        val properties = LlmProperties(enabled = true, apiUrl = "http://llm")
        val timeoutClient = object : LlmDraftClient {
            override fun stitchDraft(inboundQuestion: String, ruleSegments: String, freeText: String): String? {
                throw ResourceAccessException("Read timed out")
            }
        }
        val service = LlmStitchService(
            properties,
            provider(timeoutClient),
            qaRuleRepository
        )
        val rule = com.weibo.talentintroduction.qa.domain.QaRule(
            id = 3,
            categoryId = 1,
            keywords = "fund",
            replyBody = "Funding info",
            replySubject = "Re",
            enabled = true
        )
        Mockito.`when`(qaRuleRepository.findById(3L)).thenReturn(Optional.of(rule))

        val result = service.polishDraft(listOf(3), "Funding?", null)

        assertEquals("Funding info", result.draftText)
        assertFalse(result.usedLlm)
    }
}
