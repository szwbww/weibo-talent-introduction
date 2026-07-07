package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder

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
    fun `openAlexRestTemplate has no retry interceptor`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(), RestTemplateBuilder())

        assertTrue(restTemplate.interceptors.isEmpty())
    }
}
