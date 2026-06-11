package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ArxivProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate
import javax.xml.parsers.DocumentBuilderFactory

class ArxivDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val properties = ArxivProperties(enabled = true, requestDelayMs = 0)
    private val dataSource = ArxivDataSource(restTemplate, properties, pdfExtractor)

    @Test
    fun `DOM parses Atom entry elements correctly`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val doc = factory.newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(atomXml.toByteArray(Charsets.UTF_8)))
        val entries = doc.getElementsByTagName("entry")
        assertTrue(entries.length > 0, "DOM should find entry elements, found ${entries.length}")

        val entry = entries.item(0) as org.w3c.dom.Element
        val publishedNodes = entry.getElementsByTagName("published")
        assertTrue(publishedNodes.length > 0, "Should find published element, found ${publishedNodes.length}")
        assertEquals("2024-01-15T10:00:00Z", publishedNodes.item(0).textContent)
    }

    @Test
    fun `parseAtomResponse parses Atom XML`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val criteria = PaperSearchCriteria(publicationYearFrom = 2020, publicationYearTo = 2026)
        val result = dataSource.parseAtomResponse(atomXml, criteria)

        assertEquals(2, result.papers.size)
        assertEquals(2L, result.totalResults)
        assertEquals("ARXIV", result.papers[0].source)
        assertEquals("arXiv:2401.00001", result.papers[0].doi)
        assertEquals(2024, result.papers[0].pubYear)
    }

    @Test
    fun `parseAtomResponse filters by publication year`() {
        val atomXml = javaClass.classLoader.getResource("arxiv/atom-response-sample.xml")!!.readText()
        val criteria = PaperSearchCriteria(publicationYearFrom = 2025, publicationYearTo = 2026)
        val result = dataSource.parseAtomResponse(atomXml, criteria)
        assertEquals(0, result.papers.size)
    }

    @Test
    fun `parseAtomResponse returns empty for null xml`() {
        val result = dataSource.parseAtomResponse(null, PaperSearchCriteria())
        assertEquals(0, result.papers.size)
    }

    @Test
    fun `extractAuthorEmails delegates to PDF extractor`() {
        Mockito.doReturn(EmailExtractionOutcome(emptyList(), "PDF_PARSE", "NO_EMAIL_IN_TEXT"))
            .`when`(pdfExtractor).extract(Mockito.anyString(), Mockito.anyList(), Mockito.anyString())

        val paper = PaperMetadata(null, null, "arXiv:2401.00001", "Test", 2024, null, emptyList(), "ARXIV")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_EMAIL_IN_TEXT", result.failureReason)
    }

    @Test
    fun `extractAuthorEmails returns NO_DOI when no arxiv id`() {
        val paper = PaperMetadata(null, null, null, "Test", 2024, null, emptyList(), "ARXIV")
        val result = dataSource.extractAuthorEmails(paper)
        assertEquals("NO_DOI", result.failureReason)
    }
}
