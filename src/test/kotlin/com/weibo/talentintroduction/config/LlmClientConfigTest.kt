package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.client.SimpleClientHttpRequestFactory

class LlmClientConfigTest {
    @Test
    fun `llmRestTemplate uses configured timeout`() {
        val properties = LlmProperties(enabled = true, timeoutMs = 12_345)
        val config = LlmClientConfig(properties)
        val restTemplate = config.llmRestTemplate(RestTemplateBuilder())

        val factory = restTemplate.requestFactory as SimpleClientHttpRequestFactory
        assertEquals(12_345, factory.readTimeoutProperty())
        assertEquals(12_345, factory.connectTimeoutProperty())
    }

    private fun SimpleClientHttpRequestFactory.readTimeoutProperty(): Int {
        val field = SimpleClientHttpRequestFactory::class.java.getDeclaredField("readTimeout")
        field.isAccessible = true
        return field.getInt(this)
    }

    private fun SimpleClientHttpRequestFactory.connectTimeoutProperty(): Int {
        val field = SimpleClientHttpRequestFactory::class.java.getDeclaredField("connectTimeout")
        field.isAccessible = true
        return field.getInt(this)
    }
}
