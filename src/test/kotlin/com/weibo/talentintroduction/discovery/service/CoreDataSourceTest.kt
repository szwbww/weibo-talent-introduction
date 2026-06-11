package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.CoreProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import com.fasterxml.jackson.databind.ObjectMapper

class CoreDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val plainTextExtractor = PlainTextEmailExtractor()
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val mapper = ObjectMapper()
    private val properties = CoreProperties(enabled = true, apiKey = "test-key", requestDelayMs = 0)
    private val dataSource = CoreDataSource(restTemplate, properties, plainTextExtractor, pdfExtractor)

    @Test
    fun `searchPapers parses initial CORE search response`() {
        val response = mapper.readTree("""{
            "totalHits": 100, "scrollId": "scroll-abc",
            "results": [
                {"doi": "10.1234/test.1", "title": "Test Paper 1", "yearPublished": 2024,
                 "authors": [{"name": "John Smith"}, {"name": "Jane Doe"}],
                 "publisher": "Nature", "fullText": "Contact: a@b.com"},
                {"doi": "10.1234/test.2", "title": "Test Paper 2", "yearPublished": 2023,
                 "authors": [{"name": "Bob Wilson"}], "publisher": "Science"}
            ]
        }""")
        Mockito.doReturn(ResponseEntity.ok(response))
            .`when`(restTemplate).exchange(
                Mockito.matches("^(?:(?!scroll).)*$"),
                Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val result = dataSource.searchPapers(PaperSearchCriteria(keywords = listOf("deep learning")))

        assertEquals(2, result.papers.size)
        assertEquals(100L, result.totalResults)
        assertEquals("Test Paper 1", result.papers[0].title)
        assertEquals(2024, result.papers[0].pubYear)
        assertEquals("Contact: a@b.com", result.papers[0].fullText)
        assertEquals("Nature", result.papers[0].journal)
    }

    @Test
    fun `searchPapers scrolls next page when cursor present`() {
        val response = mapper.readTree("""{
            "totalHits": 100, "scrollId": "scroll-xyz",
            "results": [
                {"doi": "10.1234/test.3", "title": "Paper 3", "yearPublished": 2024,
                 "authors": [{"name": "Alice"}], "publisher": "Cell"}
            ]
        }""")
        Mockito.doReturn(ResponseEntity.ok(response))
            .`when`(restTemplate).exchange(
                Mockito.contains("scroll"),
                Mockito.eq(HttpMethod.POST), Mockito.any(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )

        val criteria = PaperSearchCriteria(cursor = "scroll:scroll-abc")
        val result = dataSource.searchPapers(criteria)

        assertEquals(1, result.papers.size)
        assertEquals("Paper 3", result.papers[0].title)
        assertEquals(100L, result.totalResults)
    }

    @Test
    fun `extractAuthorEmails uses fullText field`() {
        val paper = com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, "10.1234/test",
            "Test Title", 2024, null, emptyList(), "CORE",
            fullText = "Contact researcher@univ.edu, support@springer.com"
        )
        val result = dataSource.extractAuthorEmails(paper)

        assertTrue(result.emails.isNotEmpty())
        assertTrue(result.emails.any { it.email == "researcher@univ.edu" })
    }

    @Test
    fun `extractAuthorEmails returns NO_FULLTEXT when no fullText or downloadUrl`() {
        val paper = com.weibo.talentintroduction.discovery.domain.PaperMetadata(
            null, null, null,
            "Test", 2024, null, emptyList(), "CORE"
        )
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_FULLTEXT", result.failureReason)
    }

    @Test
    fun `searchPapers handles error gracefully`() {
        Mockito.doThrow(RuntimeException("timeout"))
            .`when`(restTemplate).exchange(
                Mockito.anyString(), Mockito.eq(HttpMethod.POST), Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }

    @Test
    fun `init throws when apiKey is blank`() {
        val blankProperties = CoreProperties(enabled = true, apiKey = "", requestDelayMs = 0)
        assertThrows(IllegalArgumentException::class.java) {
            CoreDataSource(restTemplate, blankProperties, plainTextExtractor, pdfExtractor)
        }
    }

    @Test
    fun `init succeeds when apiKey is non-blank`() {
        val validProperties = CoreProperties(enabled = true, apiKey = "valid-key", requestDelayMs = 0)
        assertNotNull(CoreDataSource(restTemplate, validProperties, plainTextExtractor, pdfExtractor))
    }
}
