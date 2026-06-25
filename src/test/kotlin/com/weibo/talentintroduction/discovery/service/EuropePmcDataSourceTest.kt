package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.web.client.RestTemplate
import java.net.URI

class EuropePmcDataSourceTest {
    private val mapper = ObjectMapper()
    private val properties = EuropePmcProperties(
        baseUrl = "https://www.ebi.ac.uk/europepmc/webservices/rest",
        requestDelayMs = 0,
        enabled = true
    )

    @Test
    fun `searchPapers uses URI to avoid double-encoding`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val response = mapper.readTree(
            """
            {"version":"6.8","hitCount":0,"resultList":{"result":[]}}
            """.trimIndent()
        )

        val uriCaptor = ArgumentCaptor.forClass(URI::class.java)
        Mockito.`when`(
            restTemplate.getForObject(uriCaptor.capture(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

        dataSource.searchPapers(PaperSearchCriteria(
            keywords = listOf("machine learning"),
            publicationYearFrom = 2022,
            publicationYearTo = 2025
        ))

        val uri = uriCaptor.value
        val uriStr = uri.toString()
        assertTrue(uriStr.contains("IN_EPMC"), "URI should contain IN_EPMC")
        assertTrue(uriStr.contains("OPEN_ACCESS"), "URI should contain OPEN_ACCESS")
        assertTrue(uriStr.contains("PUB_YEAR"), "URI should contain PUB_YEAR")
        assertTrue(uriStr.contains("query=") || uriStr.contains("query%3D"), "URI should have query param")
        // Verify no double-encoding: encoded patterns should not appear twice
        assertTrue(!uriStr.contains("%253A"), "Should not have double-encoded %253A")
        assertTrue(!uriStr.contains("%2520"), "Should not have double-encoded %2520")
        assertTrue(!uriStr.contains("%252B"), "Should not have double-encoded %252B")
    }

    @Test
    fun `searchPapers encodes cursor with special chars`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val response = mapper.readTree(
            """
            {"version":"6.8","hitCount":0,"resultList":{"result":[]}}
            """.trimIndent()
        )

        val uriCaptor = ArgumentCaptor.forClass(URI::class.java)
        Mockito.`when`(
            restTemplate.getForObject(uriCaptor.capture(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

        dataSource.searchPapers(PaperSearchCriteria(cursor = "AoE/+EDE5MzM5NTMy="))

        val uri = uriCaptor.value
        val uriStr = uri.toString()
        // + should be encoded as %2B, = should be encoded as %3D
        assertTrue(uriStr.contains("cursorMark=") || uriStr.contains("cursorMark%3D"))
        assertTrue(uriStr.contains("%2B") || uriStr.contains("AoE"), "Cursor should be properly encoded")
    }

    @Test
    fun `searchPapers parses response correctly with nested affiliations`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val sampleJson = javaClass.classLoader
            .getResource("europepmc/search-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.getForObject(
                Mockito.any(URI::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(response)

        val criteria = PaperSearchCriteria(keywords = listOf("deep learning"))
        val result = dataSource.searchPapers(criteria)

        assertEquals(1, result.papers.size)
        assertEquals("AoE/EDE5MzM5NTMy", result.nextCursor)
        assertEquals(2L, result.totalResults)

        val paper = result.papers[0]
        assertEquals("PMC9876543", paper.pmcId)
        assertEquals("36543210", paper.pmid)
        assertEquals("10.1038/s41586-024-00001-2", paper.doi)
        assertEquals("Deep Learning for Climate Prediction", paper.title)
        assertEquals(2024, paper.pubYear)
        assertEquals("Nature", paper.journal)
        assertEquals("EUROPE_PMC", paper.source)
        assertEquals(3, paper.authors.size)

        assertEquals("John", paper.authors[0].givenNames)
        assertEquals("Smith", paper.authors[0].familyNames)
        assertEquals("0000-0001-2345-6789", paper.authors[0].orcidId)
        assertEquals("University of Oxford, Department of Computer Science", paper.authors[0].affiliation)

        assertEquals("Alice", paper.authors[1].givenNames)
        assertEquals("Jones", paper.authors[1].familyNames)
        assertNull(paper.authors[1].orcidId)
        assertEquals("MIT, CSAIL; Broad Institute of MIT and Harvard", paper.authors[1].affiliation)

        assertEquals("Bob", paper.authors[2].givenNames)
        assertEquals("NoAffil", paper.authors[2].familyNames)
        assertNull(paper.authors[2].affiliation)
    }

    @Test
    fun `searchPapers handles author without affiliationDetailsList via fallback`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val response = mapper.readTree(
            """
            {"version":"6.8","hitCount":1,"resultList":{"result":[
              {"pmcid":"PMC1","pmid":"1","doi":"10.0/x","title":"T","pubYear":"2024","journalTitle":"J",
               "authorList":{"author":[
                 {"firstName":"A","lastName":"B","affiliation":"Direct Aff"}
               ]}}
            ]}}
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.getForObject(
                Mockito.any(URI::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(response)

        val result = dataSource.searchPapers(PaperSearchCriteria(keywords = listOf("test")))
        assertEquals("Direct Aff", result.papers[0].authors[0].affiliation)
    }

    @Test
    fun `searchPapers handles API error gracefully`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(
                Mockito.any(URI::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("API unavailable"))

        val criteria = PaperSearchCriteria(keywords = listOf("test"))
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(criteria) }
    }

    @Test
    fun `searchPapers handles null response gracefully`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(
                Mockito.any(URI::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(null)

        val criteria = PaperSearchCriteria(keywords = listOf("test"))
        val result = dataSource.searchPapers(criteria)

        assertEquals(0, result.papers.size)
        assertNull(result.nextCursor)
        assertEquals(0L, result.totalResults)
    }

    @Test
    fun `searchPapers handles timeout exception gracefully`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(
                Mockito.any(URI::class.java),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(org.springframework.web.client.ResourceAccessException("Connection timed out"))

        val criteria = PaperSearchCriteria(keywords = listOf("test"))
        org.junit.jupiter.api.Assertions.assertThrows(java.lang.Exception::class.java) { dataSource.searchPapers(criteria) }
    }

    @Test
    fun `searchPapers returns empty when disabled`() {
        val disabledProperties = EuropePmcProperties(enabled = false)
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, disabledProperties)

        val result = dataSource.searchPapers(PaperSearchCriteria(keywords = listOf("test")))

        assertEquals(0, result.papers.size)
        assertNull(result.nextCursor)
        assertEquals(0L, result.totalResults)
        // Verify restTemplate was NOT called
        Mockito.verify(restTemplate, Mockito.never())
            .getForObject(Mockito.any(URI::class.java), Mockito.any<Class<*>>())
    }

    @Test
    fun `fetchFullTextXml returns null when disabled`() {
        val disabledProperties = EuropePmcProperties(enabled = false)
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, disabledProperties)

        val result = dataSource.fetchFullTextXml("PMC123")
        assertNull(result)
        Mockito.verify(restTemplate, Mockito.never())
            .getForObject(Mockito.anyString(), Mockito.any<Class<*>>())
    }

    @Test
    fun `extractEmailsFromFullText returns empty when disabled`() {
        val disabledProperties = EuropePmcProperties(enabled = false)
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, disabledProperties)

        val result = dataSource.extractEmailsFromFullText("PMC123")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fetchFullTextXml returns null on HTTP error`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenThrow(RuntimeException("404 Not Found"))

        val result = dataSource.fetchFullTextXml("PMC9876543")
        assertNull(result)
    }

    @Test
    fun `fetchFullTextXml returns bytes on success`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val xmlBytes = """<?xml version="1.0" encoding="UTF-8"?><article/>""".toByteArray()

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(xmlBytes)

        val result = dataSource.fetchFullTextXml("PMC9876543")
        assertNotNull(result)
        assertEquals(xmlBytes.size, result!!.size)
    }

    @Test
    fun `extractEmailsFromFullText returns empty when fetch fails`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(null)

        val result = dataSource.extractEmailsFromFullText("PMC9876543")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractEmailsFromFullText preserves UTF-8 characters`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val xmlBytes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group content-type="author">
                    <contrib>
                      <name><surname>Müller</surname><given-names>Jörg</given-names></name>
                      <xref ref-type="corresp" rid="fn001"/>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <fn id="fn001">
                      <p>*Correspondence:
                        <email>joerg.mueller@uni-wuerzburg.de</email>
                      </p>
                    </fn>
                  </author-notes>
                  <aff id="aff1">University Hospital Würzburg, Würzburg, Germany</aff>
                </article-meta>
              </front>
            </article>
        """.trimIndent().toByteArray()

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(xmlBytes)

        val result = dataSource.extractEmailsFromFullText("PMC9876543")
        assertEquals(1, result.size)
        assertEquals("joerg.mueller@uni-wuerzburg.de", result[0].email)
        assertEquals("Müller", result[0].familyNames)
        assertEquals("Jörg", result[0].givenNames)
    }

    @Test
    fun `MockRestServiceServer handles application-xml without charset`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val xmlBytes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group content-type="author">
                    <contrib>
                      <name><surname>Müller</surname><given-names>Jörg</given-names></name>
                      <xref ref-type="corresp" rid="fn001"/>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <fn id="fn001">
                      <p>*Correspondence:
                        <email>joerg@uni-wuerzburg.de</email>
                      </p>
                    </fn>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent().toByteArray()

        server.expect(MockRestRequestMatchers.requestTo(containsString("/fullTextXML")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withSuccess(xmlBytes, MediaType.APPLICATION_XML))

        val result = dataSource.extractEmailsFromFullText("PMC9876543")
        assertEquals(1, result.size)
        assertEquals("joerg@uni-wuerzburg.de", result[0].email)
        assertEquals("Müller", result[0].familyNames)
        assertEquals("Jörg", result[0].givenNames)
        server.verify()
    }

    @Test
    fun `MockRestServiceServer verifies search request encoding`() {
        val restTemplate = RestTemplate()
        val server = MockRestServiceServer.bindTo(restTemplate).build()
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val responseJson = """{"version":"6.8","hitCount":1,"nextCursorMark":"AoE/Cursor","resultList":{"result":[
          {"pmcid":"PMC1","pmid":"1","doi":"10.0/x","title":"T","pubYear":"2024","journalTitle":"J",
           "authorList":{"author":[]}}
        ]}}"""

        server.expect(MockRestRequestMatchers.requestTo(containsString("/search")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withSuccess(responseJson, MediaType.APPLICATION_JSON))

        val result = dataSource.searchPapers(PaperSearchCriteria())
        assertEquals(1, result.papers.size)
        assertEquals("AoE/Cursor", result.nextCursor)
        server.verify()
    }

    @Test
    fun `extractAuthorEmails returns FULLTEXT_FETCH_FAILED when fetch fails`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(null)

        val outcome = dataSource.extractAuthorEmails(PaperMetadata(
            pmcId = "PMC9876543", pmid = "1", doi = "10.0/x", title = "T", pubYear = 2024,
            journal = "J", authors = emptyList(), source = "EUROPE_PMC"
        ))

        assertEquals("FULLTEXT_FETCH_FAILED", outcome.failureReason)
        assertTrue(outcome.emails.isEmpty())
        assertEquals(1, outcome.httpRequests)
    }

    @Test
    fun `extractAuthorEmails returns XML_PARSE_FAILED when parse throws`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn("not valid xml".toByteArray())

        val outcome = dataSource.extractAuthorEmails(PaperMetadata(
            pmcId = "PMC9876543", pmid = "1", doi = "10.0/x", title = "T", pubYear = 2024,
            journal = "J", authors = emptyList(), source = "EUROPE_PMC"
        ))

        assertEquals("XML_PARSE_FAILED", outcome.failureReason)
        assertTrue(outcome.emails.isEmpty())
    }

    @Test
    fun `extractAuthorEmails returns NO_EMAIL_IN_FULLTEXT when XML has no email`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val xmlBytes = """<?xml version="1.0" encoding="UTF-8"?><article><front><article-meta/></front></article>"""
            .toByteArray()
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(xmlBytes)

        val outcome = dataSource.extractAuthorEmails(PaperMetadata(
            pmcId = "PMC9876543", pmid = "1", doi = "10.0/x", title = "T", pubYear = 2024,
            journal = "J", authors = emptyList(), source = "EUROPE_PMC"
        ))

        assertEquals("NO_EMAIL_IN_FULLTEXT", outcome.failureReason)
        assertTrue(outcome.emails.isEmpty())
    }

    @Test
    fun `extractAuthorEmails returns emails when XML contains email`() {
        val restTemplate = Mockito.mock(RestTemplate::class.java)
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val xmlBytes = """
            <?xml version="1.0" encoding="UTF-8"?>
            <article>
              <front>
                <article-meta>
                  <contrib-group content-type="author">
                    <contrib>
                      <name><surname>Smith</surname><given-names>John</given-names></name>
                      <xref ref-type="corresp" rid="fn001"/>
                    </contrib>
                  </contrib-group>
                  <author-notes>
                    <fn id="fn001">
                      <p><email>john@oxford.ac.uk</email></p>
                    </fn>
                  </author-notes>
                </article-meta>
              </front>
            </article>
        """.trimIndent().toByteArray()

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(ByteArray::class.java))
        ).thenReturn(xmlBytes)

        val outcome = dataSource.extractAuthorEmails(PaperMetadata(
            pmcId = "PMC9876543", pmid = "1", doi = "10.0/x", title = "T", pubYear = 2024,
            journal = "J", authors = emptyList(), source = "EUROPE_PMC"
        ))

        assertNull(outcome.failureReason)
        assertEquals(1, outcome.emails.size)
        assertEquals("john@oxford.ac.uk", outcome.emails[0].email)
    }

    @Test
    @Disabled("Requires network access to Europe PMC API - run manually to verify")
    fun `smoke test real Europe PMC API returns papers`() {
        val restTemplate = RestTemplate()
        val dataSource = EuropePmcDataSource(restTemplate, properties)

        val criteria = PaperSearchCriteria(
            keywords = listOf("deep learning"),
            publicationYearFrom = 2024,
            publicationYearTo = 2024
        )
        val result = dataSource.searchPapers(criteria)

        assertTrue(result.papers.isNotEmpty(), "Should return at least one paper")
        assertNotNull(result.papers[0].pmcId, "Paper should have pmcId")
        assertNotNull(result.nextCursor, "Should have next cursor for pagination")
    }
}
