package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import java.time.LocalDateTime

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
    fun `promoteToApplication removes promoted document from candidate index`() {
        val candidateBody = mapper.readTree(
            """
            {
              "_index": "orcid_info_candidate",
              "_id": "0001",
              "_source": {
                "orcidId": "0001",
                "email": "a@b.com",
                "givenNames": "Test",
                "familyNames": "User"
              }
            }
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_doc/0001"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(candidateBody, HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_doc/0001"),
                eq(HttpMethod.PUT),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_doc/0001"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val contact = com.weibo.talentintroduction.campaign.domain.ExpertContact(
            id = null,
            orcidId = "0001",
            expertEmail = "a@b.com",
            expertName = "Test User",
            currentStatus = "WAITING_REPLY",
            campaignId = 1L,
            autoReplyEnabled = true
        )
        val result = service.promoteToApplication(
            orcid = "0001",
            contact = contact,
            firstReplyAt = java.time.Instant.parse("2026-01-01T00:00:00Z")
        )

        assertTrue(result)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_doc/0001"),
            eq(HttpMethod.DELETE),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
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
    fun `syncOperatorStatus sends update posts to all three layers`() {
        // IP-3: document exists in all three layers → _update posted to each
        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/$index/_doc/0001"),
                    eq(HttpMethod.HEAD),
                    any(),
                    eq(Void::class.java)
                )
            ).thenReturn(ResponseEntity(HttpStatus.OK))
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/$index/_update/0001"),
                    eq(HttpMethod.POST),
                    any(),
                    eq(com.fasterxml.jackson.databind.JsonNode::class.java)
                )
            ).thenReturn(ResponseEntity(mapper.readTree("""{"result": "updated"}"""), HttpStatus.OK))
        }

        val result = service.syncOperatorStatus("0001", "CONTACTED")
        assertEquals(3L, result.matched)
        assertTrue(result.ok)
        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.verify(restTemplate).exchange(
                eq("https://es.example.com:9200/$index/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        }
    }

    @Test
    fun `syncOperatorStatus returns matched zero when document missing from all layers`() {
        // All three layers HEAD 404 → layers skipped, matched stays 0
        // (doc ids are normalized to uppercase: MISSING-ORCID)
        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/$index/_doc/MISSING-ORCID"),
                    eq(HttpMethod.HEAD),
                    any(),
                    eq(Void::class.java)
                )
            ).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        }

        val result = service.syncOperatorStatus("missing-orcid", "REPLIED")
        assertEquals(0L, result.matched)
        assertTrue(result.ok)
        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.contains("/_update/"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `syncOperatorStatus returns failure when elasticsearch throws`() {
        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/$index/_doc/0001"),
                    eq(HttpMethod.HEAD),
                    any(),
                    eq(Void::class.java)
                )
            ).thenReturn(ResponseEntity(HttpStatus.OK))
        }
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info/_update/0001"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES unavailable"))

        val result = service.syncOperatorStatus("0001", "REPLIED")
        assertEquals(0L, result.matched)
        assertFalse(result.ok)
        assertEquals("ES unavailable", result.error)
    }

    @Test
    fun `syncOperatorStatusBatch sends bulk updates to all three layers`() {
        // Mock the _search to resolve orcidId → _id mapping on every layer
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
        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/$index/_search"),
                    eq(HttpMethod.POST),
                    any(),
                    eq(com.fasterxml.jackson.databind.JsonNode::class.java)
                )
            ).thenReturn(ResponseEntity(searchResponse, HttpStatus.OK))
        }

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

        val result = service.syncOperatorStatusBatch(listOf(
            "0001" to "CONTACTED",
            "0002" to "REPLIED",
            "0003" to "REPLIED"
        ))

        // Three layers × (1 success + 1 skipped 404 + 1 failure 500)
        assertEquals(9, result.total)
        assertEquals(3, result.success)
        assertEquals(3, result.skipped)
        assertEquals(3, result.failure)
        assertEquals(3, result.errors.size)
        assertTrue(result.errors.all { it.contains("conflict") })

        Mockito.verify(restTemplate, Mockito.times(3)).exchange(
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

    private fun classification(type: ExpertType): ExpertClassification =
        ExpertClassification(
            type = type,
            productionScore = 60,
            researchScore = 40,
            positiveEvidence = listOf("RESEARCH_RECENT_PUBLICATION"),
            negativeEvidence = emptyList(),
            version = "rnd-v1-2026",
            sourceFingerprint = "a".repeat(64),
            classifiedAt = LocalDateTime.of(2026, 1, 15, 10, 30)
        )

    private fun bulkResponse(vararg items: String): JsonNode =
        mapper.readTree(
            """{"took": 1, "errors": true, "items": [${items.joinToString(",")}]}"""
        )

    @Test
    fun `bulkUpdateExpertClassifications sends exact NDJSON and aggregates per-item status (I2-2)`() {
        val responseNode = bulkResponse(
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0001", "status": 200, "result": "updated" } }""",
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0002", "status": 200, "result": "noop" } }""",
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0003", "status": 404, "error": { "type": "document_missing_exception", "reason": "document missing" } } }""",
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0004", "status": 500, "error": { "type": "remote_transport_exception", "reason": "boom" } } }"""
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val result = service.bulkUpdateExpertClassifications(ExpertIndexLevel.CANDIDATE, listOf(
            ClassificationBulkItem("0001", classification(ExpertType.PRODUCTION_RND)),
            ClassificationBulkItem("0002", classification(ExpertType.ACADEMIC_RND)),
            ClassificationBulkItem("0003", classification(ExpertType.SERVICE_ONLY)),
            ClassificationBulkItem("0004", classification(ExpertType.UNKNOWN))
        ))

        assertEquals(1, result.updated)
        assertEquals(1, result.noop)
        assertEquals(2, result.failure)
        assertEquals(2, result.failureSamples.size)
        assertFalse(result.allFailedWithMapperError)
        assertTrue(result.failureSamples.all { it.startsWith("docId=") })

        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            captor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val body = captor.value.body as String
        val lines = body.trim().split("\n")
        assertEquals(8, lines.size)

        // meta 行：_index + _id
        assertEquals("""{"update":{"_id":"0001","_index":"orcid_info_candidate"}}""", lines[0])
        assertEquals("""{"update":{"_id":"0002","_index":"orcid_info_candidate"}}""", lines[2])

        // data 行逐字结构：{"doc":{"expertClassification":{...}},"doc_as_upsert":false}
        val dataNode = mapper.readTree(lines[1])
        assertEquals(false, dataNode.path("doc_as_upsert").asBoolean())
        assertEquals(1, dataNode.path("doc").size(), "doc 只允许 expertClassification，禁止根级 updatedAt 等")
        assertTrue(dataNode.path("doc").has("expertClassification"))
        val cls = dataNode.path("doc").path("expertClassification")
        assertEquals("PRODUCTION_RND", cls.path("type").asText())
        assertFalse(cls.has("sendable"), "serialized classification must not contain sendable")
        assertEquals(60, cls.path("productionScore").asInt())
        assertEquals(40, cls.path("researchScore").asInt())
        assertEquals("RESEARCH_RECENT_PUBLICATION", cls.path("positiveEvidence").get(0).asText())
        assertEquals(0, cls.path("negativeEvidence").size())
        assertEquals("rnd-v1-2026", cls.path("version").asText())
        assertEquals("a".repeat(64), cls.path("sourceFingerprint").asText())
        assertEquals("2026-01-15 10:30:00", cls.path("classifiedAt").asText(), "classifiedAt 必须 yyyy-MM-dd HH:mm:ss 以匹配 mapping")
        assertEquals(8, cls.size())
    }

    @Test
    fun `classificationNode output matches backfill node shape (I3-4)`() {
        // 子计划 03：晋升写入路径的序列化必须与回填路径（bulkUpdateExpertClassifications）逐字一致。
        val node = service.classificationNode(classification(ExpertType.PRODUCTION_RND))
        assertEquals(8, node.size())
        assertEquals("PRODUCTION_RND", node.path("type").asText())
        assertFalse(node.has("sendable"), "classificationNode must not contain sendable")
        assertEquals(60, node.path("productionScore").asInt())
        assertEquals(40, node.path("researchScore").asInt())
        assertEquals("RESEARCH_RECENT_PUBLICATION", node.path("positiveEvidence").get(0).asText())
        assertEquals(0, node.path("negativeEvidence").size())
        assertEquals("rnd-v1-2026", node.path("version").asText())
        assertEquals("a".repeat(64), node.path("sourceFingerprint").asText())
        assertEquals("2026-01-15 10:30:00", node.path("classifiedAt").asText(), "classifiedAt 必须 yyyy-MM-dd HH:mm:ss 以匹配 mapping")
    }

    @Test
    fun `bulkUpdateExpertClassifications targets only the caller level index (I2-2 no cross-layer loop)`() {
        val responseNode = bulkResponse("""{ "update": { "_index": "orcid_info_candidate", "_id": "0001", "status": 200, "result": "updated" } }""")
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val result = service.bulkUpdateExpertClassifications(ExpertIndexLevel.APPLICATION, listOf(
            ClassificationBulkItem("0001", classification(ExpertType.PRODUCTION_RND))
        ))

        assertEquals(1, result.updated)
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            captor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val body = captor.value.body as String
        assertTrue(body.contains("""{"update":{"_id":"0001","_index":"orcid_info_application"}}"""))
        assertFalse(body.contains("orcid_info_candidate"))
        assertFalse(body.contains(""""_index":"orcid_info"}"""))
    }

    @Test
    fun `bulkUpdateExpertClassifications chunks batches at 1000`() {
        val responseNode = bulkResponse()
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val updates = (1..2500).map { ClassificationBulkItem("%04d".format(it), classification(ExpertType.PRODUCTION_RND)) }
        service.bulkUpdateExpertClassifications(ExpertIndexLevel.CANDIDATE, updates)

        Mockito.verify(restTemplate, Mockito.times(3)).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val captor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate, Mockito.times(3)).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            captor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val lineCounts = captor.allValues.map { (it.body as String).trim().split("\n").size }
        assertEquals(listOf(2000, 2000, 1000), lineCounts)
    }

    @Test
    fun `bulkUpdateExpertClassifications keeps at most 100 failure samples but counts all failures (I2-4)`() {
        val items = (1..150).map { i ->
            """{ "update": { "_index": "orcid_info_candidate", "_id": "${"%04d".format(i)}", "status": 500, "error": { "type": "cluster_block_exception", "reason": "disk full" } } }""".trimIndent()
        }
        val responseNode = bulkResponse(*items.toTypedArray())
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val updates = (1..150).map { ClassificationBulkItem("%04d".format(it), classification(ExpertType.PRODUCTION_RND)) }
        val result = service.bulkUpdateExpertClassifications(ExpertIndexLevel.CANDIDATE, updates)

        assertEquals(150, result.failure)
        assertEquals(0, result.updated)
        assertEquals(100, result.failureSamples.size)
    }

    @Test
    fun `bulkUpdateExpertClassifications flags first-batch mapper errors (I2-2)`() {
        val items = listOf(
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0001", "status": 400, "error": { "type": "mapper_parsing_exception", "reason": "failed to parse" } } }""",
            """{ "update": { "_index": "orcid_info_candidate", "_id": "0002", "status": 400, "error": { "type": "mapper_parsing_exception", "reason": "failed to parse" } } }"""
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(bulkResponse(*items.toTypedArray()), HttpStatus.OK))

        val result = service.bulkUpdateExpertClassifications(ExpertIndexLevel.CANDIDATE, listOf(
            ClassificationBulkItem("0001", classification(ExpertType.PRODUCTION_RND)),
            ClassificationBulkItem("0002", classification(ExpertType.PRODUCTION_RND))
        ))

        assertEquals(2, result.failure)
        assertTrue(result.allFailedWithMapperError)
    }

    @Test
    fun `bulkUpdateExpertClassifications records wholesaleError and stops on bulk exception`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_bulk"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES unavailable"))

        val result = service.bulkUpdateExpertClassifications(ExpertIndexLevel.CANDIDATE, listOf(
            ClassificationBulkItem("0001", classification(ExpertType.PRODUCTION_RND)),
            ClassificationBulkItem("0002", classification(ExpertType.PRODUCTION_RND))
        ))

        assertEquals(2, result.failure)
        assertEquals(2, result.failureSamples.size)
        assertNotNull(result.wholesaleError)
        assertTrue(result.wholesaleError!!.contains("ES unavailable"))
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            eq("https://es.example.com:9200/_bulk"),
            eq(HttpMethod.POST),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `checkExpertClassificationMapping returns true when keyword present`() {
        val mapping = mapper.readTree(
            """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "dynamic": false,
                  "properties": {
                    "orcidId": { "type": "keyword" },
                    "expertClassification": {
                      "type": "object",
                      "properties": {
                        "type": { "type": "keyword" },
                        "version": { "type": "keyword" }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_mapping"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapping, HttpStatus.OK))

        assertTrue(service.checkExpertClassificationMapping(ExpertIndexLevel.CANDIDATE))
    }

    @Test
    fun `checkExpertClassificationMapping returns false when field missing`() {
        val mapping = mapper.readTree(
            """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "dynamic": false,
                  "properties": {
                    "orcidId": { "type": "keyword" }
                  }
                }
              }
            }
            """.trimIndent()
        )
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_mapping"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapping, HttpStatus.OK))

        assertFalse(service.checkExpertClassificationMapping(ExpertIndexLevel.CANDIDATE))
    }

    @Test
    fun `checkExpertClassificationMapping returns false on ES error`() {
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_mapping"),
                eq(HttpMethod.GET),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("ES timeout"))

        assertFalse(service.checkExpertClassificationMapping(ExpertIndexLevel.CANDIDATE))
    }
}
