package com.weibo.talentintroduction.mail.controller

import com.weibo.talentintroduction.mail.service.MailTranslationService
import com.weibo.talentintroduction.mail.service.TranslationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TranslationControllerTest {
    private val mailTranslationService = Mockito.mock(MailTranslationService::class.java)
    private val controller = TranslationController(mailTranslationService)

    @Test
    fun `translate returns translated text`() {
        Mockito.`when`(mailTranslationService.translate("Hello"))
            .thenReturn(TranslationResult(ok = true, text = "你好"))

        val response = controller.translate(TranslateRequest(text = "Hello"))

        assertTrue(response.ok)
        assertEquals("你好", response.translatedText)
    }

    @Test
    fun `translate returns ok false when service fails`() {
        Mockito.`when`(mailTranslationService.translate("Hello"))
            .thenReturn(TranslationResult(ok = false, reason = "TRANSLATION_FAILED"))

        val response = controller.translate(TranslateRequest(text = "Hello"))

        assertFalse(response.ok)
        assertEquals("TRANSLATION_FAILED", response.reason)
    }

    @Test
    fun `translate passes long text to service for truncation`() {
        val longText = "x".repeat(6000)
        Mockito.`when`(mailTranslationService.translate(longText))
            .thenReturn(TranslationResult(ok = true, text = "译文"))

        val response = controller.translate(TranslateRequest(text = longText))

        assertTrue(response.ok)
        Mockito.verify(mailTranslationService).translate(longText)
    }

    @Test
    fun `translate treats null text as empty`() {
        Mockito.`when`(mailTranslationService.translate(""))
            .thenReturn(TranslationResult(ok = false, reason = "EMPTY_TEXT"))

        val response = controller.translate(TranslateRequest(text = null))

        assertFalse(response.ok)
        assertEquals("EMPTY_TEXT", response.reason)
    }
}
