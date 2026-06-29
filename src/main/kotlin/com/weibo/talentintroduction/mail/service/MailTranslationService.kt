package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.TranslationProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

data class TranslationResult(
    val ok: Boolean,
    val text: String? = null,
    val reason: String? = null
)

@Service
class MailTranslationService(
    private val properties: TranslationProperties,
    @Qualifier("translationRestTemplate")
    private val restTemplate: RestTemplate
) {
    private val log = LoggerFactory.getLogger(MailTranslationService::class.java)

    fun translate(text: String): TranslationResult {
        if (!properties.enabled) {
            return TranslationResult(ok = false, reason = "TRANSLATION_DISABLED")
        }
        if (text.isBlank()) {
            return TranslationResult(ok = false, reason = "EMPTY_TEXT")
        }

        val input = text.take(properties.maxChars)

        return try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val body = linkedMapOf<String, Any>(
                "q" to input,
                "source" to properties.source,
                "target" to properties.target,
                "format" to "text"
            )
            properties.apiKey?.takeIf { it.isNotBlank() }?.let { body["api_key"] = it }

            val url = "${properties.baseUrl.trimEnd('/')}/translate"
            @Suppress("UNCHECKED_CAST")
            val response = restTemplate.postForObject(
                url,
                HttpEntity(body, headers),
                Map::class.java
            ) as Map<String, Any>?
            val translated = response?.get("translatedText") as? String
            if (translated.isNullOrBlank()) {
                TranslationResult(ok = false, reason = "EMPTY_RESPONSE")
            } else {
                TranslationResult(ok = true, text = translated)
            }
        } catch (e: Exception) {
            log.warn("Translation failed: {}", e.message)
            TranslationResult(ok = false, reason = "TRANSLATION_FAILED")
        }
    }
}
