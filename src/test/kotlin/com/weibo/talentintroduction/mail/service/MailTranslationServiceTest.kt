package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.TranslationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

class MailTranslationServiceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private lateinit var service: MailTranslationService

    @BeforeEach
    fun setUp() {
        Mockito.reset(restTemplate)
        service = MailTranslationService(
            TranslationProperties(enabled = true, baseUrl = "http://127.0.0.1:5000", maxChars = 5000),
            restTemplate
        )
    }

    @Test
    fun `translate returns translated text on success`() {
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.eq("http://127.0.0.1:5000/translate"),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(Map::class.java)
            )
        ).thenReturn(mapOf("translatedText" to "你好"))

        val result = service.translate("Hello")

        assertTrue(result.ok)
        assertEquals("你好", result.text)
    }

    @Test
    fun `translate does not throw when rest client fails`() {
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.anyString(),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(Map::class.java)
            )
        ).thenThrow(RestClientException("timeout"))

        val result = service.translate("Hello")

        assertFalse(result.ok)
        assertEquals("TRANSLATION_FAILED", result.reason)
    }

    @Test
    fun `translate does not throw on empty response`() {
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.anyString(),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(Map::class.java)
            )
        ).thenReturn(mapOf("translatedText" to ""))

        val result = service.translate("Hello")

        assertFalse(result.ok)
        assertEquals("EMPTY_RESPONSE", result.reason)
    }

    @Test
    fun `translate returns disabled when not enabled`() {
        service = MailTranslationService(
            TranslationProperties(enabled = false),
            restTemplate
        )

        val result = service.translate("Hello")

        assertFalse(result.ok)
        assertEquals("TRANSLATION_DISABLED", result.reason)
        Mockito.verifyNoInteractions(restTemplate)
    }

    @Test
    fun `translate returns empty for blank text`() {
        val result = service.translate("   ")

        assertFalse(result.ok)
        assertEquals("EMPTY_TEXT", result.reason)
        Mockito.verifyNoInteractions(restTemplate)
    }

    @Test
    fun `translate truncates input to maxChars`() {
        service = MailTranslationService(
            TranslationProperties(enabled = true, baseUrl = "http://127.0.0.1:5000", maxChars = 10),
            restTemplate
        )
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.anyString(),
                captor.capture(),
                Mockito.eq(Map::class.java)
            )
        ).thenReturn(mapOf("translatedText" to "译文"))

        val result = service.translate("a".repeat(100))

        assertTrue(result.ok)
        @Suppress("UNCHECKED_CAST")
        val body = captor.value.body as Map<String, Any>
        assertEquals(10, (body["q"] as String).length)
    }

    @Test
    fun `translate uses configured base url only`() {
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.eq("http://127.0.0.1:5000/translate"),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(Map::class.java)
            )
        ).thenReturn(mapOf("translatedText" to "ok"))

        service.translate("test")

        Mockito.verify(restTemplate).postForObject(
            Mockito.eq("http://127.0.0.1:5000/translate"),
            Mockito.any(HttpEntity::class.java),
            Mockito.eq(Map::class.java)
        )
    }

    @Test
    fun `translate includes api key when configured`() {
        service = MailTranslationService(
            TranslationProperties(enabled = true, apiKey = "secret-key"),
            restTemplate
        )
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.postForObject(
                Mockito.anyString(),
                captor.capture(),
                Mockito.eq(Map::class.java)
            )
        ).thenReturn(mapOf("translatedText" to "ok"))

        service.translate("Hello")

        @Suppress("UNCHECKED_CAST")
        val body = captor.value.body as Map<String, Any>
        assertEquals("secret-key", body["api_key"])
    }
}
