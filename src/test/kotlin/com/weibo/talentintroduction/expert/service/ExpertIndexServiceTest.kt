package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

class ExpertIndexServiceTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val mapper = ObjectMapper()
    private val properties = ElasticsearchProperties(
        baseUrl = "https://es.example.com:9200",
        username = "elastic",
        password = "secret",
        rawIndexName = "orcid_info",
        candidateIndexName = "orcid_info_candidate",
        applicationIndexName = "orcid_info_application"
    )
    private val service = ExpertIndexService(properties, restTemplate, mapper)

    @Test
    fun `mapping update sends only Phase5 new fields not legacy fields`() {
        // HEAD returns OK for all indices → PUT _mapping called
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("orcid_info_candidate"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("/_mapping"),
                Mockito.eq(HttpMethod.PUT),
                entityCaptor.capture(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        service.bootstrapMappings()

        val captured = entityCaptor.value
        val body = captured.body as Map<*, *>
        val properties = body["properties"] as Map<*, *>

        // Phase 5 fields present
        assertFieldExists(properties, "hIndex")
        assertFieldExists(properties, "citationCount")
        assertFieldExists(properties, "lastPublicationYear")
        assertFieldExists(properties, "researchFields")
        assertFieldExists(properties, "institution")
        assertFieldExists(properties, "emailSource")
        assertFieldExists(properties, "emailVerifiedLevel")
        assertFieldExists(properties, "dataSource")
        assertFieldExists(properties, "externalIds")
        assertFieldExists(properties, "discoveredAt")
        assertFieldExists(properties, "filterResult")
        assertFieldExists(properties, "filterRejectReason")

        // Legacy fields NOT present
        assertFieldMissing(properties, "country")
        assertFieldMissing(properties, "email")
        assertFieldMissing(properties, "givenNames")
        assertFieldMissing(properties, "familyNames")
        assertFieldMissing(properties, "orcidId")
        assertFieldMissing(properties, "keyword")
        assertFieldMissing(properties, "degree")
        assertFieldMissing(properties, "age")
    }

    @Test
    fun `404 on one index does not block other indices`() {
        // RAW: exists, CANDIDATE: 404, APPLICATION: exists
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("orcid_info"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("orcid_info_candidate"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("orcid_info_application"),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("/_mapping"),
                Mockito.eq(HttpMethod.PUT),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        service.bootstrapMappings()

        // application always created/checked above. Verify mapping PUTs were called at least twice (RAW + APPLICATION)
        Mockito.verify(restTemplate, Mockito.atLeast(2)).exchange(
            Mockito.contains("/_mapping"),
            Mockito.eq(HttpMethod.PUT),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `PUT returns 400 does not block remaining indices`() {
        // All three indices exist
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        // First mapping PUT returns 400
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("/_mapping"),
                Mockito.eq(HttpMethod.PUT),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
            .thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        service.bootstrapMappings()

        // Verify all three PUTs were attempted (1st fails, 2nd+3rd succeed)
        Mockito.verify(restTemplate, Mockito.times(3)).exchange(
            Mockito.contains("/_mapping"),
            Mockito.eq(HttpMethod.PUT),
            Mockito.any(),
            Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    private fun assertFieldExists(properties: Map<*, *>, field: String) {
        org.junit.jupiter.api.Assertions.assertTrue(
            properties.containsKey(field),
            "Mapping should contain field: $field"
        )
    }

    private fun assertFieldMissing(properties: Map<*, *>, field: String) {
        org.junit.jupiter.api.Assertions.assertFalse(
            properties.containsKey(field),
            "Mapping should NOT contain legacy field: $field"
        )
    }
}
