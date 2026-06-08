package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito

class ExpertIndexWriterServiceTest {
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
    private val expertIndexService = ExpertIndexService(properties, restTemplate, mapper)
    private val promotionRepository = Mockito.mock(ExpertApplicationPromotionRepository::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val service = ExpertIndexWriterService(
        restTemplate, properties, expertIndexService, mapper,
        promotionRepository, contactRepository
    )

    @Test
    fun `readRawDocument preserves number array`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User",
                "country": "GB",
                "subjectAreas": [101, 202, 303]
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.readRawDocument("0001")
        assertNotNull(result)
        assertEquals("0001", result!!["orcidId"])
        val subjectAreas = result["subjectAreas"] as List<*>
        assertEquals(3, subjectAreas.size)
        assertEquals(101, subjectAreas[0])
        assertEquals(202, subjectAreas[1])
        assertEquals(303, subjectAreas[2])
    }

    @Test
    fun `readRawDocument preserves nested object array`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User",
                "country": "GB",
                "employments": [
                  {"institution": "Oxford", "position": "Professor"},
                  {"institution": "MIT", "position": "Researcher"}
                ]
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.readRawDocument("0001")
        assertNotNull(result)
        val employments = result!!["employments"] as List<*>
        assertEquals(2, employments.size)
        val first = employments[0] as Map<*, *>
        assertEquals("Oxford", first["institution"])
        assertEquals("Professor", first["position"])
    }

    @Test
    fun `readRawDocument preserves nested object`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User",
                "country": "GB",
                "address": {
                  "city": "London",
                  "postcode": "WC1A 1AA",
                  "coordinates": {"lat": 51.5, "lon": -0.12}
                }
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.readRawDocument("0001")
        assertNotNull(result)
        val address = result!!["address"] as Map<*, *>
        assertEquals("London", address["city"])
        assertEquals("WC1A 1AA", address["postcode"])
        val coords = address["coordinates"] as Map<*, *>
        assertEquals(51.5, coords["lat"])
        assertEquals(-0.12, coords["lon"])
    }

    @Test
    fun `readRawDocument preserves null fields`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": null,
                "givenNames": "Test",
                "familyNames": "User",
                "country": null,
                "keyword": null
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.readRawDocument("0001")
        assertNotNull(result)
        assertEquals(null, result!!["email"])
        assertEquals(null, result["country"])
        assertEquals(null, result["keyword"])
    }

    @Test
    fun `readRawDocument preserves boolean fields`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User",
                "country": "GB",
                "isVerified": true,
                "hasPublications": false
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.readRawDocument("0001")
        assertNotNull(result)
        assertEquals(true, result!!["isVerified"])
        assertEquals(false, result["hasPublications"])
    }

    @Test
    fun `promoteToCandidate deep copies complex source fields`() {
        val rawBody = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User",
                "country": "GB",
                "hIndex": 42,
                "citationCount": 1500,
                "subjectAreas": [101, 202],
                "tags": ["AI", "ML", "NLP"],
                "metadata": {"source": "orcid", "score": 95.5}
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(rawBody, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_doc/0001"),
                eq(HttpMethod.PUT),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L,
            orcidId = "0001",
            expertEmail = "a@b.com",
            expertName = "Test User",
            currentStatus = "WAITING_REPLY",
            campaignId = 1L,
            autoReplyEnabled = true
        )
        val result = service.promoteToCandidate("0001", contact)
        assertTrue(result)
    }
}
