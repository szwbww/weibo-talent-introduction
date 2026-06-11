package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate

class OpenAlexDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    private val pdfExtractor = Mockito.mock(PdfEmailExtractor::class.java)
    private val properties = OpenAlexProperties(
        enabled = true,
        politeEmail = "",
        baseUrl = "https://api.openalex.org",
        requestDelayMs = 0
    )
    private val mapper = ObjectMapper()
    private val dataSource = OpenAlexDataSource(restTemplate, properties, europePmc, pdfExtractor)

    @Test
    fun `searchPapers parses OpenAlex works response`() {
        val sampleJson = javaClass.classLoader
            .getResource("openalex/works-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

        val criteria = PaperSearchCriteria(keywords = listOf("deep learning"))
        val result = dataSource.searchPapers(criteria)

        assertEquals(1, result.papers.size)
        assertEquals("AoE/EDE5MzM5NTMy", result.nextCursor)
        assertEquals(2L, result.totalResults)

        val paper = result.papers[0]
        assertEquals("OPENALEX", paper.source)
        assertEquals("PMC9876543", paper.pmcId)
        assertEquals("36543210", paper.pmid)
        assertEquals("10.1038/s41586-024-00001-2", paper.doi)
        assertEquals("Deep Learning for Climate Prediction", paper.title)
        assertEquals(2024, paper.pubYear)
        assertEquals("Nature", paper.journal)
        assertEquals(2, paper.authors.size)

        assertEquals("John", paper.authors[0].givenNames)
        assertEquals("Smith", paper.authors[0].familyNames)
        assertEquals("0000-0001-2345-6789", paper.authors[0].orcidId)
        assertEquals("University of Oxford", paper.authors[0].affiliation)
        assertTrue(paper.authors[0].isCorresponding)
        assertEquals("Alice", paper.authors[1].givenNames)
        assertEquals("Jones", paper.authors[1].familyNames)
        assertNull(paper.authors[1].orcidId)
    }

    @Test
    fun `enrichAuthor returns academic metrics`() {
        val sampleJson = javaClass.classLoader
            .getResource("openalex/author-response-sample.json")!!.readText()
        val response = mapper.readTree(sampleJson)

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenReturn(response)

        val result = dataSource.enrichAuthor("A1234567")
        assertNotNull(result)
        assertEquals(18, result!!.hIndex)
        assertEquals(1200, result.citationCount)
        assertEquals(45, result.worksCount)
    }

    @Test
    fun `buildFilter includes is_oa and publication_year`() {
        val response = mapper.readTree("""{"meta":{"count":0,"next_cursor":null},"results":[]}""")
        val urlCaptor = mutableListOf<String>()

        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenAnswer { invocation ->
            urlCaptor.add(invocation.arguments[0] as String)
            response
        }

        val criteria = PaperSearchCriteria(
            publicationYearFrom = 2022,
            publicationYearTo = 2025,
            openAccessOnly = true,
            excludeCountries = listOf("CN")
        )
        dataSource.searchPapers(criteria)

        val url = urlCaptor.single()
        assertTrue(url.contains("is_oa:true"), "Filter should contain is_oa:true")
        assertTrue(url.contains("publication_year:2022-2025"), "Filter should contain year range")
        assertTrue(url.contains("authorships.institutions.country_code:!CN"), "Filter should exclude CN")
    }

    @Test
    fun `searchPapers handles API error gracefully`() {
        Mockito.`when`(
            restTemplate.getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))
        ).thenThrow(RuntimeException("API unavailable"))

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException::class.java) { dataSource.searchPapers(PaperSearchCriteria()) }
    }
}
