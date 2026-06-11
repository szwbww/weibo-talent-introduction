package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.CrossrefProperties
import com.weibo.talentintroduction.config.UnpaywallProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate

class CrossrefDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = CrossrefProperties(
        enabled = true, politeEmail = "test@example.com", requestDelayMs = 0
    )
    private val unpaywallProperties = UnpaywallProperties(
        email = "test@example.com", requestDelayMs = 0
    )
    private val unpaywallClient = UnpaywallClient(restTemplate, unpaywallProperties)
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val mapper = ObjectMapper()
    private val dataSource = CrossrefDataSource(restTemplate, properties, unpaywallClient, pdfExtractor)

    @Test
    fun `searchPapers parses Crossref works response`() {
        val response = mapOf(
            "message" to mapOf(
                "next-cursor" to "cursor-abc",
                "total-results" to 150,
                "items" to listOf(
                    mapOf(
                        "DOI" to "10.1234/test.1",
                        "title" to listOf("Machine Learning Advances"),
                        "published-print" to mapOf("date-parts" to listOf(listOf(2024))),
                        "container-title" to listOf("Nature"),
                        "author" to listOf(
                            mapOf(
                                "given" to "Alice",
                                "family" to "Wang",
                                "ORCID" to "https://orcid.org/0000-0001-0000-0001",
                                "affiliation" to listOf(mapOf("name" to "Tsinghua University"))
                            )
                        )
                    )
                )
            )
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = dataSource.searchPapers(PaperSearchCriteria(keywords = listOf("machine learning")))

        assertEquals(1, result.papers.size)
        assertEquals("cursor-abc", result.nextCursor)
        assertEquals(150L, result.totalResults)

        val paper = result.papers[0]
        assertEquals("CROSSREF", paper.source)
        assertEquals("10.1234/test.1", paper.doi)
        assertEquals("Machine Learning Advances", paper.title)
        assertEquals(2024, paper.pubYear)
        assertEquals("Nature", paper.journal)
        assertEquals(1, paper.authors.size)
        assertEquals("Alice", paper.authors[0].givenNames)
        assertEquals("Wang", paper.authors[0].familyNames)
        assertEquals("0000-0001-0000-0001", paper.authors[0].orcidId)
        assertEquals("Tsinghua University", paper.authors[0].affiliation)
    }

    @Test
    fun `extractAuthorEmails returns NO_DOI when doi is null`() {
        val paper = PaperMetadata(null, null, null, "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_DOI", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails returns NO_OA_LOCATION when Unpaywall finds no PDF`() {
        Mockito.doReturn(null)
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val paper = PaperMetadata(null, null, "10.1234/test", "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_OA_LOCATION", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails delegates to PDF extractor when PDF url found`() {
        val unpaywallResponse = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "http://example.com/paper.pdf")
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(unpaywallResponse)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        Mockito.doReturn(EmailExtractionOutcome(emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT"))
            .`when`(pdfExtractor).extract(Mockito.anyString(), Mockito.anyList(), Mockito.anyString())

        val paper = PaperMetadata(null, null, "10.1234/test", "Test", 2024, null, emptyList(), "CROSSREF")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_EMAIL_IN_TEXT", result.failureReason)
    }

    @Test
    fun `init throws when unpaywall email is blank`() {
        val unpaywallProps = UnpaywallProperties(email = "", requestDelayMs = 0)
        val unpaywall = UnpaywallClient(restTemplate, unpaywallProps)
        val crossrefProps = CrossrefProperties(enabled = true, politeEmail = "test@example.com", requestDelayMs = 0)
        assertThrows(IllegalArgumentException::class.java) {
            CrossrefDataSource(restTemplate, crossrefProps, unpaywall, pdfExtractor)
        }
    }

    @Test
    fun `init succeeds when unpaywall email is configured`() {
        val unpaywallProps = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val unpaywall = UnpaywallClient(restTemplate, unpaywallProps)
        val crossrefProps = CrossrefProperties(enabled = true, politeEmail = "test@example.com", requestDelayMs = 0)
        assertNotNull(CrossrefDataSource(restTemplate, crossrefProps, unpaywall, pdfExtractor))
    }
}
