package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.OrcidProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate

class OrcidDataSourceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val properties = OrcidProperties(enabled = true, requestDelayMs = 0)
    private val mapper = ObjectMapper()
    private val dataSource = OrcidDataSource(restTemplate, properties)

    @Test
    fun `searchOrcidRecords parses expanded-search response`() {
        val response = mapper.readTree("""
            {"expanded-result": [
                {"orcid-id": "0000-0001-0000-0001", "given-names": "John", "family-names": "Smith",
                 "email": ["john@oxford.ac.uk"], "institution-name": ["Oxford University"]},
                {"orcid-id": "0000-0002-0000-0002", "given-names": "Jane", "family-names": "Doe",
                 "email": ["jane@cam.ac.uk"], "institution-name": ["Cambridge University"]}
            ]}
        """.trimIndent())
        Mockito.doReturn(response)
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val criteria = PaperSearchCriteria(keywords = listOf("machine learning"))
        val records = dataSource.searchOrcidRecords(criteria)

        assertEquals(2, records.size)
        assertEquals("0000-0001-0000-0001", records[0].orcidId)
        assertEquals("John", records[0].givenNames)
        assertEquals("Smith", records[0].familyNames)
        assertEquals(listOf("john@oxford.ac.uk"), records[0].emails)
        assertEquals("Oxford University", records[0].institutionName)
    }

    @Test
    fun `searchOrcidRecords skips records without emails`() {
        val response = mapper.readTree("""
            {"expanded-result": [
                {"orcid-id": "0000-0001-0000-0001", "email": [], "institution-name": ["Oxford"]},
                {"orcid-id": "0000-0002-0000-0002", "email": ["jane@cam.ac.uk"], "institution-name": ["Cambridge"]}
            ]}
        """.trimIndent())
        Mockito.doReturn(response)
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val records = dataSource.searchOrcidRecords(PaperSearchCriteria(keywords = listOf("test")))
        assertEquals(1, records.size, "Should skip record without email")
        assertEquals("jane@cam.ac.uk", records[0].emails[0])
    }

    @Test
    fun `orcidRecordToAuthorEmails converts record correctly`() {
        val record = OrcidDataSource.OrcidRecord(
            orcidId = "0000-0001-0000-0001",
            givenNames = "John",
            familyNames = "Smith",
            emails = listOf("john@oxford.ac.uk", "jsmith@oxford.ac.uk"),
            institutionName = "Oxford University",
            country = null
        )
        val emails = dataSource.orcidRecordToAuthorEmails(record)
        assertEquals(2, emails.size)
        assertEquals("john@oxford.ac.uk", emails[0].email)
        assertEquals("John", emails[0].givenNames)
        assertEquals("Smith", emails[0].familyNames)
        assertEquals("0000-0001-0000-0001", emails[0].orcidId)
        assertEquals("Oxford University", emails[0].affiliation)
    }

    @Test
    fun `searchOrcidRecords throws on API error`() {
        Mockito.doThrow(RuntimeException("API unavailable"))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        assertThrows(RuntimeException::class.java) {
            dataSource.searchOrcidRecords(PaperSearchCriteria(keywords = listOf("test")))
        }
    }

    @Test
    fun `searchOrcidRecords returns empty for blank query`() {
        val records = dataSource.searchOrcidRecords(PaperSearchCriteria(keywords = emptyList()))
        assertTrue(records.isEmpty())
    }
}
