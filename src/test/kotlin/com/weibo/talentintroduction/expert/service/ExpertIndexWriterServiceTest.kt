package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
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

    @Test
    fun `addTag sends correct update script and returns true`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val result = service.addTag("0001", "verified", ExpertIndexLevel.CANDIDATE)
        assertTrue(result)
    }

    @Test
    fun `addTag returns false on ES error`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES timeout"))

        val result = service.addTag("0001", "verified", ExpertIndexLevel.CANDIDATE)
        assertFalse(result)
    }

    @Test
    fun `removeTag sends correct update script and returns true`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val result = service.removeTag("0001", "verified", ExpertIndexLevel.CANDIDATE)
        assertTrue(result)
    }

    @Test
    fun `removeTag returns false on ES error`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES timeout"))

        val result = service.removeTag("0001", "verified", ExpertIndexLevel.CANDIDATE)
        assertFalse(result)
    }

    @Test
    fun `readRawDocument returns parsed map with tags`() {
        val body = mapper.readTree(
            """
            {
              "_index": "orcid_info",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "tags": ["discovered", "verified"]
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
        assertEquals("a@b.com", result!!["email"])
        assertEquals(listOf("discovered", "verified"), result["tags"])
    }

    @Test
    fun `syncCandidateOperatorStatus sends correct update post request`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_update_by_query"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree("""{"updated": 1}"""), HttpStatus.OK))

        service.syncCandidateOperatorStatus("0001", "CONTACTED")
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_update_by_query"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `syncCandidateOperatorStatusBatch sends correct bulk request`() {
        // Mock the _search to resolve orcidId → _id mapping
        val searchResponse = mapper.readTree(
            """
            {
              "hits": {
                "hits": [
                  { "_id": "0001", "_source": { "orcidId": "0001" } },
                  { "_id": "0002", "_source": { "orcidId": "0002" } },
                  { "_id": "0003", "_source": { "orcidId": "0003" } }
                ]
              }
            }
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(searchResponse, HttpStatus.OK))

        val responseNode = mapper.readTree(
            """
            {
              "took": 1,
              "errors": true,
              "items": [
                { "update": { "_index": "orcid_info_candidate", "_id": "0001", "status": 200 } },
                { "update": { "_index": "orcid_info_candidate", "_id": "0002", "status": 404 } },
                { "update": { "_index": "orcid_info_candidate", "_id": "0003", "status": 500, "error": { "reason": "conflict" } } }
              ]
            }
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val result = service.syncCandidateOperatorStatusBatch(listOf(
            "0001" to "CONTACTED",
            "0002" to "REPLIED",
            "0003" to "REPLIED"
        ))

        assertEquals(3, result.total)
        assertEquals(1, result.success)
        assertEquals(1, result.skipped)
        assertEquals(1, result.failure)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0].contains("conflict"))

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `removeFromCandidateIndex returns false on 404`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_doc/ORCID-0001"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))

        val result = service.removeFromCandidateIndex("ORCID-0001")
        assertFalse(result)
    }

    @Test
    fun `removeFromCandidateIndex returns false on non-404 HTTP error`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_doc/ORCID-0001"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.CONFLICT))

        val result = service.removeFromCandidateIndex("ORCID-0001")
        assertFalse(result)
    }

    @Test
    fun `demoteToRaw sends delete_by_query and returns true`() {
        val deleteResponse = mapper.readTree("""{"deleted": 1}""")
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_delete_by_query"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(deleteResponse, HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_delete_by_query"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(deleteResponse, HttpStatus.OK))

        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L,
            orcidId = "0001",
            expertEmail = "a@b.com",
            expertName = "Test User",
            currentStatus = "WAITING_REPLY",
            campaignId = 1L,
            autoReplyEnabled = true
        )
        val result = service.demoteToRaw("0001", contact)
        assertTrue(result)
    }

    @Test
    fun `demoteToRaw returns false when no document deleted`() {
        val deleteResponse = mapper.readTree("""{"deleted": 0}""")
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_delete_by_query"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(deleteResponse, HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_delete_by_query"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(deleteResponse, HttpStatus.OK))

        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = 1L,
            orcidId = "0001",
            expertEmail = "a@b.com",
            expertName = "Test User",
            currentStatus = "WAITING_REPLY",
            campaignId = 1L,
            autoReplyEnabled = true
        )
        val result = service.demoteToRaw("0001", contact)
        assertFalse(result)
    }
}
