package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.llm.domain.AiPromptConfig
import com.weibo.talentintroduction.llm.repository.AiPromptConfigRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.Optional

class AiPromptConfigServiceTest {

    private val repository = Mockito.mock(AiPromptConfigRepository::class.java)
    private val service = AiPromptConfigService(repository)

    @Test
    fun `getEffectiveDto returns default prompt when no custom config`() {
        Mockito.`when`(repository.findById(1L)).thenReturn(Optional.of(AiPromptConfig()))

        val dto = service.getEffectiveDto()

        assertEquals(FreeFormPromptDefaults.defaultFreeFormSystemPrompt(), dto.freeFormSystemPrompt)
        assertFalse(dto.isCustom)
    }

    @Test
    fun `getEffectiveDto returns custom prompt and marks isCustom`() {
        Mockito.`when`(repository.findById(1L)).thenReturn(
            Optional.of(
                AiPromptConfig(
                    freeFormSystemPrompt = "Custom prompt body",
                    constraints = "Do not promise visa"
                )
            )
        )

        val dto = service.getEffectiveDto()

        assertEquals("Custom prompt body", dto.freeFormSystemPrompt)
        assertEquals("Do not promise visa", dto.constraints)
        assertTrue(dto.isCustom)
    }

    @Test
    fun `getEffectiveDto treats blank custom prompt as default`() {
        Mockito.`when`(repository.findById(1L)).thenReturn(
            Optional.of(AiPromptConfig(freeFormSystemPrompt = "   "))
        )

        val dto = service.getEffectiveDto()

        assertEquals(FreeFormPromptDefaults.defaultFreeFormSystemPrompt(), dto.freeFormSystemPrompt)
        assertFalse(dto.isCustom)
    }

    @Test
    fun `default prompt answers inbound first without unsolicited CV or meeting push`() {
        val prompt = FreeFormPromptDefaults.defaultFreeFormSystemPrompt()
        assertFalse(prompt.contains("advance the conversation toward scheduling a meeting"))
        assertFalse(prompt.contains("at an early stage ask only for CV"))
        assertTrue(prompt.contains("answer the current inbound email completely and in order"))
        assertTrue(prompt.contains("do not request materials, propose a meeting or call, or add other next-step CTAs"))
        assertTrue(prompt.contains("links in the email are not evidence that they were accessed"))
    }

    @Test
    fun `base prompt has conditional single and multi paragraph rules without global four-para crush`() {
        val prompt = FreeFormPromptDefaults.baseSystemPrompt()
        assertTrue(prompt.contains("For a single-request email, keep the reply to at most 4 paragraphs."))
        assertTrue(prompt.contains("For a multi-request email (2 or more requests)"))
        assertTrue(prompt.contains("Do not crush the reply into at most 4 paragraphs."))
        assertFalse(prompt.lines().any { it.trim() == "Keep the reply to at most 4 paragraphs." })
    }

    @Test
    fun `effective custom prompt still wins and appends constraints`() {
        Mockito.`when`(repository.findById(1L)).thenReturn(
            Optional.of(
                AiPromptConfig(
                    freeFormSystemPrompt = "Custom only prompt",
                    constraints = "No visa promises\nKeep tone calm"
                )
            )
        )

        val effective = service.getEffectiveFreeFormSystemPrompt(FreeFormPromptDefaults.defaultFreeFormSystemPrompt())

        assertTrue(effective.startsWith("Custom only prompt"))
        assertTrue(effective.contains("Additional constraints:"))
        assertTrue(effective.contains("- No visa promises"))
        assertTrue(effective.contains("- Keep tone calm"))
        assertFalse(effective.contains("For a single-request email"))
    }
}
