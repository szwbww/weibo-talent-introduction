package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.PmcOaProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate

class PmcOaDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = PmcOaProperties(enabled = true, requestDelayMs = 0)
    private val mapper = ObjectMapper()
    private val dataSource = PmcOaDataSource(restTemplate, properties)

    @Test
    fun `searchPapers parses esearch response`() {
        val response = mapper.readTree("""
            {"esearchresult":{"count":"50","retmax":"100","retstart":"0","idlist":["PMC123","PMC456"]}}
        """.trimIndent())
        Mockito.doReturn(response)
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val criteria = PaperSearchCriteria(keywords = listOf("cancer"), publicationYearFrom = 2020, publicationYearTo = 2026)
        val result = dataSource.searchPapers(criteria)

        assertEquals(2, result.papers.size)
        assertEquals(50L, result.totalResults)
        assertEquals("PMC123", result.papers[0].pmcId)
        assertEquals("PMC456", result.papers[1].pmcId)
        assertEquals("PMC_OA", result.papers[0].source)
    }

    @Test
    fun `extractAuthorEmails returns NO_PMC_ID when pmcId null`() {
        val paper = com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, null, "Test", 2024, null, emptyList(), "PMC_OA"
        )
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_PMC_ID", result.failureReason)
    }

    @Test
    fun `searchPapers handles API error gracefully`() {
        Mockito.doThrow(RuntimeException("timeout"))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }
}
