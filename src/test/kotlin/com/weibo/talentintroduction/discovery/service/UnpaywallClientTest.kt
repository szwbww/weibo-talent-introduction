package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.UnpaywallProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate

class UnpaywallClientTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val mapper = ObjectMapper()

    @Test
    fun `findPdfUrl returns null when email not configured`() {
        val properties = UnpaywallProperties(email = "")
        val client = UnpaywallClient(restTemplate, properties)
        assertNull(client.findPdfUrl("10.1234/test"))
    }

    @Test
    fun `findPdfUrl returns best_oa_location url_for_pdf`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "http://example.com/paper.pdf")
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertEquals("http://example.com/paper.pdf", result)
    }

    @Test
    fun `findPdfUrl falls back to oa_locations`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to null,
            "oa_locations" to listOf(
                mapOf("url_for_pdf" to "http://example.com/fallback.pdf")
            )
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertEquals("http://example.com/fallback.pdf", result)
    }

    @Test
    fun `findPdfUrl returns null on API error`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        Mockito.doThrow(RuntimeException("API error"))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertNull(result)
    }
}
