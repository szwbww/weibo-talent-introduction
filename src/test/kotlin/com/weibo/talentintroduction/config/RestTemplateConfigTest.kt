package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate

class RestTemplateConfigTest {
    private val config = RestTemplateConfig()

    @Test
    fun `shared restTemplate has no interceptors`() {
        assertTrue(config.restTemplate().interceptors.isEmpty())
    }

    @Test
    fun `europePmcRestTemplate includes retry interceptor`() {
        val restTemplate = config.europePmcRestTemplate(EuropePmcProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is RetryingClientHttpRequestInterceptor)
    }

    @Test
    fun `pdfDownloadRestTemplate includes retry interceptor`() {
        val restTemplate = config.pdfDownloadRestTemplate(PdfExtractionProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is RetryingClientHttpRequestInterceptor)
    }

    @Test
    fun `openAlexRestTemplate carries only the authentication interceptor`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is OpenAlexAuthInterceptor)
    }

    @Test
    fun `openAlexRestTemplate sends bearer to the configured https origin (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(apiKey = "test-key"), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        val urls = listOf(
            "https://api.openalex.org/works?per_page=1",
            "https://api.openalex.org:443/authors/A1"
        )
        for (url in urls) {
            server.expect(anything())
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withStatus(HttpStatus.OK))
        }
        for (url in urls) {
            restTemplate.getForEntity(url, String::class.java)
        }
        server.verify()
    }

    @Test
    fun `openAlexRestTemplate keeps the key off every other origin (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(apiKey = "test-key"), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        val urls = listOf(
            "https://api.openalex.org:8443/works",
            "https://api.openalex.org.evil.com/works",
            "https://www.example.org/paper.pdf",
            "http://api.openalex.org/works"
        )
        for (url in urls) {
            server.expect(anything())
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.OK))
        }
        for (url in urls) {
            restTemplate.getForEntity(url, String::class.java)
        }
        server.verify()
    }

    @Test
    fun `openAlexRestTemplate strips a credential from an untrusted origin (I-1, V-1)`() {
        // A cross-origin redirect target or an external fulltext host must never carry the key.
        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(
            ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.set(HttpHeaders.AUTHORIZATION, "Bearer test-key")
                execution.execute(request, body)
            }
        )
        restTemplate.interceptors.add(OpenAlexAuthInterceptor("test-key", "https://api.openalex.org"))
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(anything())
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.OK))
        restTemplate.getForEntity("https://www.example.org/paper.pdf", String::class.java)
        server.verify()
    }

    @Test
    fun `anonymous configuration never sends a credential (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(anything())
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.OK))
        restTemplate.getForEntity("https://api.openalex.org/works", String::class.java)
        server.verify()
    }

    @Test
    fun `configuration never prints a live key (I-1)`() {
        val properties = OpenAlexProperties(apiKey = "super-secret-key")

        assertTrue(properties.toString().contains("apiKey=\"***\""))
        assertTrue(!properties.toString().contains("super-secret-key"))
    }

    @Test
    fun `openAlexRequestPolicy bean uses the configured budget and key (I-2)`() {
        assertEquals(1_000, config.openAlexRequestPolicy(OpenAlexProperties()).remainingCredits())
        assertEquals(10_000, config.openAlexRequestPolicy(OpenAlexProperties(apiKey = "k")).remainingCredits())
    }
}
