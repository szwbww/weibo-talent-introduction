package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ArxivProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private fun capturedSearchUrl(criteria: PaperSearchCriteria): String {
        val urlCaptor = mutableListOf<String>()
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(String::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            ""
        }
        dataSource.searchPapers(criteria)
        return urlCaptor.single()
    }

    @Test
    fun `searchPapers uses all-star query when no keywords and scope null`() {
        // I4-2 锚点断言：无关键词 + subjectScope == null 时保持改动前的 all:* 兜底。
        val url = capturedSearchUrl(PaperSearchCriteria())
        assertTrue(url.contains("search_query=all:*"), "null scope must keep pre-change all-star query")
    }

    @Test
    fun `searchPapers builds OR-joined category query for RND_TARGET scope`() {
        val url = capturedSearchUrl(PaperSearchCriteria(subjectScope = SubjectScopeCatalog.RND_TARGET))
        assertTrue(
            url.contains("search_query=cat:cs*+OR+cat:eess*+OR+cat:cond-mat*+OR+cat:physics*"),
            "RND_TARGET must map to arXiv category prefixes, got: $url"
        )
    }

    @Test
    fun `searchPapers prefers keywords over subjectScope`() {
        val url = capturedSearchUrl(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = SubjectScopeCatalog.RND_TARGET)
        )
        assertTrue(url.contains("search_query=all:\"deep+learning\""), "keywords branch must stay verbatim")
        assertFalse(url.contains("cat:"), "subjectScope must not leak into the keywords branch")
    }

    @Test
    fun `searchPapers keywords branch is unaffected by scope`() {
        val withScope = capturedSearchUrl(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = SubjectScopeCatalog.RND_TARGET)
        )
        val withoutScope = capturedSearchUrl(
            PaperSearchCriteria(keywords = listOf("deep learning"), subjectScope = null)
        )
        assertEquals(withScope, withoutScope, "keywords present must produce identical query regardless of scope")
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
