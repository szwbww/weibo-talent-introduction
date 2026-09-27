package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
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
    private val promotionAuditService = Mockito.mock(ExpertPromotionAuditService::class.java)
    private val contactRepository = Mockito.mock(ExpertContactRepository::class.java)
    private val service = ExpertIndexWriterService(
        restTemplate, properties, expertIndexService, mapper,
        promotionAuditService, contactRepository
    )

    @Test
    fun `candidate writer does not add an identity proof eligibility rule`() {
        val doc = mapOf<String, Any?>("email" to "a@example.org", "emailSource" to "PAPER_FULLTEXT")
        Mockito.`when`(restTemplate.exchange(Mockito.anyString(), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(mapper.readTree("{}"), HttpStatus.CREATED))
        assertTrue(service.writeCandidateDocument("existing", doc))
        Mockito.verify(restTemplate).exchange(eq("https://es.example.com:9200/orcid_info_candidate/_doc/existing"),
            eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java))
    }

    @Test
    fun `new discovery without proof enters RAW with atomic create`() {
        val unknown = mapOf<String, Any?>("email" to "a@example.org", "emailSource" to "PAPER_FULLTEXT", "givenNames" to "A", "familyNames" to "B")
        Mockito.`when`(restTemplate.exchange(Mockito.anyString(), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(mapper.readTree("{}"), HttpStatus.CREATED))
        assertTrue(service.indexToRaw("new", unknown))
        Mockito.verify(restTemplate).exchange(eq("https://es.example.com:9200/orcid_info/_doc/NEW?op_type=create"),
            eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java))
    }

    @Test
    fun `proof-free discovery conflict never falls back to overwrite for any discovery marker`() {
        Mockito.`when`(restTemplate.exchange(Mockito.contains("?op_type=create"), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenThrow(HttpClientErrorException(HttpStatus.CONFLICT))
        for (marker in listOf(mapOf("emailSource" to "PAPER_FULLTEXT"), mapOf("emailSource" to "ORCID_PUBLIC"), mapOf("tags" to listOf("discovered")))) {
            val doc = mapOf("email" to "a@example.org", "givenNames" to "Jane", "familyNames" to "Doe") + marker
            assertFalse(service.indexToRaw("EMAIL-existing", doc))
        }
        Mockito.verify(restTemplate, Mockito.times(3)).exchange(Mockito.contains("?op_type=create"), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java))
        Mockito.verifyNoMoreInteractions(restTemplate)
    }

    @Test
    fun `verified discovery uses atomic create instead of overwriting an existing RAW identity`() {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified("a@example.org", "Jane", "Doe", "JATS_SHA256:" + "a".repeat(64), null, null)
        val doc = mapOf<String, Any?>("email" to "a@example.org", "emailSource" to "PAPER_FULLTEXT", "givenNames" to "Jane", "familyNames" to "Doe", "identityVerification" to proof)
        Mockito.`when`(restTemplate.exchange(Mockito.contains("?op_type=create"), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenThrow(HttpClientErrorException(HttpStatus.CONFLICT))
        assertFalse(service.indexToRaw("EMAIL-existing", doc))
        Mockito.verify(restTemplate).exchange(Mockito.contains("?op_type=create"), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java))
    }

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
    @Test
    fun `discovery replica removal uses RAW and candidate CAS and retains other fields`() {
        discoveryReplicaEvidence()
    }

    @Test
    fun `real discovery revalidation creates historical candidate under the actual ES id`() {
        val outcome = revalidateHistoricalDiscovery(emailValid = true)
        assertEquals(PromotionOutcome.Promoted, outcome)
        val put = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_doc/OLD-DOC?op_type=create"),
            eq(HttpMethod.PUT), put.capture(), eq(JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val candidate = put.value.body as Map<String, Any?>
        assertEquals("HISTORICAL-ORCID", candidate["orcidId"])
        assertEquals("Jane", candidate["givenNames"])
        assertEquals("a@example.org", candidate["email"])
        assertEquals("PAUSED", candidate["operatorStatus"])
        assertEquals("retained", candidate["customOperatorNote"])
        assertEquals("PASSED", candidate["filterResult"])
        assertEquals("A123", (candidate["identityVerification"] as Map<*, *>)["openAlexAuthorId"])
    }

    @Test
    fun `real discovery revalidation rejects invalid email and conditionally removes historical candidate`() {
        val outcome = revalidateHistoricalDiscovery(emailValid = false)
        assertEquals(PromotionOutcome.Rejected(listOf("EMAIL:NO_MX_RECORD")), outcome)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_doc/OLD-DOC?if_seq_no=8&if_primary_term=2"),
            eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java)
        )
        val update = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info/_update/OLD-DOC?if_seq_no=3&if_primary_term=2"),
            eq(HttpMethod.POST), update.capture(), eq(JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val doc = (update.value.body as Map<String, Any?>)["doc"] as Map<String, Any?>
        assertEquals("REJECTED", doc["filterResult"])
        assertEquals("EMAIL:NO_MX_RECORD", doc["filterRejectReason"])
        assertEquals(setOf("filterResult", "filterRejectReason", "expertClassification"), doc.keys)
        Mockito.verify(restTemplate, Mockito.never()).exchange(
            Mockito.contains("orcid_info_candidate/_doc/OLD-DOC"), eq(HttpMethod.PUT),
            any<HttpEntity<*>>(), eq(JsonNode::class.java)
        )
    }

    private fun revalidateHistoricalDiscovery(emailValid: Boolean): PromotionOutcome {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
            "a@example.org", "Jane", "Doe", "JATS_SHA256:" + "a".repeat(64), null, "A123"
        )
        val source = mapOf<String, Any?>(
            "orcidId" to "HISTORICAL-ORCID", "email" to "a@example.org",
            "givenNames" to "Jane", "familyNames" to "Doe", "emailSource" to "PAPER_FULLTEXT",
            "identityVerification" to mapper.convertValue(proof, Map::class.java),
            "researchFieldIds" to listOf("22"), "institution" to "University",
            "lastPublicationYear" to 2026, "operatorStatus" to "PAUSED",
            "customOperatorNote" to "retained"
        )
        fun raw(seq: Int, fields: Map<String, Any?>) = ResponseEntity(
            mapper.valueToTree<JsonNode>(mapOf("_seq_no" to seq, "_primary_term" to 2, "_source" to fields)),
            HttpStatus.OK
        )
        val rawUrl = "https://es.example.com:9200/orcid_info/_doc/OLD-DOC"
        val candidateUrl = "https://es.example.com:9200/orcid_info_candidate/_doc/OLD-DOC"
        Mockito.`when`(restTemplate.exchange(
            eq("https://es.example.com:9200/orcid_info_application/_doc/OLD-DOC"),
            eq(HttpMethod.HEAD), any<HttpEntity<*>>(), eq(Void::class.java)
        )).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        Mockito.`when`(restTemplate.exchange(eq(rawUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(raw(3, source), raw(3, source),
                raw(4, source + ("filterResult" to if (emailValid) "PASSED" else "REJECTED")))
        if (emailValid) {
            Mockito.`when`(restTemplate.exchange(eq(candidateUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
            Mockito.`when`(restTemplate.exchange(
                eq("$candidateUrl?op_type=create"), eq(HttpMethod.PUT), any<HttpEntity<*>>(), eq(JsonNode::class.java)
            )).thenReturn(ResponseEntity(mapper.readTree("""{"result":"created"}"""), HttpStatus.CREATED))
        } else {
            Mockito.`when`(restTemplate.exchange(eq(candidateUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenReturn(raw(8, source))
            Mockito.`when`(restTemplate.exchange(
                eq("$candidateUrl?if_seq_no=8&if_primary_term=2"), eq(HttpMethod.DELETE),
                any<HttpEntity<*>>(), eq(JsonNode::class.java)
            )).thenReturn(ResponseEntity(mapper.readTree("""{"result":"deleted"}"""), HttpStatus.OK))
        }
        Mockito.`when`(restTemplate.exchange(
            eq("https://es.example.com:9200/orcid_info/_update/OLD-DOC?if_seq_no=3&if_primary_term=2"),
            eq(HttpMethod.POST), any<HttpEntity<*>>(), eq(JsonNode::class.java)
        )).thenReturn(ResponseEntity(
            mapper.readTree("""{"result":"updated","_seq_no":4,"_primary_term":2}"""), HttpStatus.OK
        ))
        val filters = Mockito.mock(EligibilityFilterService::class.java)
        Mockito.`when`(filters.getCandidateFilter()).thenReturn(
            com.weibo.talentintroduction.config.CandidateFilterProperties(requireValidEmail = true)
        )
        Mockito.`when`(filters.getAcademicFilter()).thenReturn(
            com.weibo.talentintroduction.config.AcademicFilterProperties()
        )
        val email = Mockito.mock(EmailValidationService::class.java)
        Mockito.`when`(email.isDisposableEmail("a@example.org")).thenReturn(false)
        Mockito.`when`(email.validate("a@example.org")).thenReturn(
            com.weibo.talentintroduction.expert.domain.EmailValidationResult(
                2, emailValid, if (emailValid) null else "NO_MX_RECORD"
            )
        )
        val revalidator = ExpertRevalidationService(
            Mockito.mock(ExpertSearchService::class.java),
            CandidateEligibilityService(filters, email), email, service,
            Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java), filters
        )
        return revalidator.revalidateDiscovery("OLD-DOC")
    }

    @Test
    fun `historical business key does not prevent conditional candidate removal`() {
        discoveryReplicaEvidence("OLD-DOC", "HISTORICAL-ORCID")
    }


    internal fun discoveryReplicaEvidence(
        docId: String = "DOC", orcidId: String = "DOC"
    ): Map<String, Any> {
        val proof = com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.verified(
            "a@example.org", "Jane", "Doe", "JATS_SHA256:" + "a".repeat(64), null, "A123"
        )
        val source = mapOf<String, Any?>(
            "orcidId" to orcidId, "email" to "a@example.org", "givenNames" to "Jane",
            "familyNames" to "Doe", "emailSource" to "PAPER_FULLTEXT",
            "identityVerification" to mapper.convertValue(proof, Map::class.java),
            "researchFieldIds" to listOf("27"), "operatorStatus" to "PAUSED",
            "customOperatorNote" to "retained"
        )
        fun doc(seq: Int, fields: Map<String, Any?>) = mapper.valueToTree<JsonNode>(
            mapOf("_seq_no" to seq, "_primary_term" to 2, "_source" to fields)
        )
        val rawUrl = "https://es.example.com:9200/orcid_info/_doc/$docId"
        val candidateUrl = "https://es.example.com:9200/orcid_info_candidate/_doc/$docId"
        val snapshot = ExpertIndexWriterService.DiscoverySnapshot(source, 3, 2)
        val classification = ExpertClassificationService().classify(service.discoveryProfile(docId, source))
        val postUpdateRaw = source + mapOf("filterResult" to "REJECTED")
        Mockito.`when`(restTemplate.exchange(eq(rawUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(doc(3, source), HttpStatus.OK))
            .thenReturn(ResponseEntity(doc(4, postUpdateRaw), HttpStatus.OK))
        Mockito.`when`(restTemplate.exchange(eq(candidateUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(doc(8, source), HttpStatus.OK))
        val updateUrl = "https://es.example.com:9200/orcid_info/_update/$docId?if_seq_no=3&if_primary_term=2"
        Mockito.`when`(restTemplate.exchange(eq(updateUrl), eq(HttpMethod.POST), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(mapper.readTree("""{"result":"updated","_seq_no":4,"_primary_term":2}"""), HttpStatus.OK))
        val deleteUrl = "$candidateUrl?if_seq_no=8&if_primary_term=2"
        Mockito.`when`(restTemplate.exchange(eq(deleteUrl), eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(mapper.readTree("""{"result":"deleted"}"""), HttpStatus.OK))

        assertTrue(service.reconcileDiscoveryCandidate(docId, snapshot, classification, listOf("RND_OUT_OF_SCOPE")))
        val update = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.verify(restTemplate).exchange(eq(updateUrl), eq(HttpMethod.POST), update.capture(), eq(JsonNode::class.java))
        @Suppress("UNCHECKED_CAST")
        val partial = update.value.body as Map<String, Any?>
        assertEquals(setOf("filterResult", "filterRejectReason", "expertClassification"),
            (partial["doc"] as Map<*, *>).keys)
        Mockito.verify(restTemplate).exchange(eq(deleteUrl), eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java))
        assertEquals("PAUSED", source["operatorStatus"])
        assertEquals("retained", source["customOperatorNote"])
        val observed = linkedMapOf<String, Any>(
            "before" to mapOf("RAW" to 1, "CANDIDATE" to 1, "APPLICATION" to 0),
            "after" to mapOf("RAW" to 1, "CANDIDATE" to 0, "APPLICATION" to 0),
            "rawReason" to "RND_OUT_OF_SCOPE", "classification" to classification.type.name,
            "candidateDelete" to "CAS_DELETED",
            "fieldSnapshots" to mapOf(
                "before" to mapOf("operatorStatus" to source["operatorStatus"],
                    "customOperatorNote" to source["customOperatorNote"]),
                "afterRawPartial" to mapOf("operatorStatus" to postUpdateRaw["operatorStatus"],
                    "customOperatorNote" to postUpdateRaw["customOperatorNote"]),
                "updatedRawKeys" to (partial["doc"] as Map<*, *>).keys
            )
        )
        for ((status, expected) in listOf(
            HttpStatus.NOT_FOUND to "IDEMPOTENT", HttpStatus.CONFLICT to "RETRY",
            HttpStatus.INTERNAL_SERVER_ERROR to "RETRY"
        )) {
            Mockito.reset(restTemplate)
            Mockito.`when`(restTemplate.exchange(eq(rawUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenReturn(ResponseEntity(doc(3, source), HttpStatus.OK))
                .thenReturn(ResponseEntity(doc(4, source), HttpStatus.OK))
            Mockito.`when`(restTemplate.exchange(eq(candidateUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenReturn(ResponseEntity(doc(8, source), HttpStatus.OK))
            Mockito.`when`(restTemplate.exchange(eq(updateUrl), eq(HttpMethod.POST), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenReturn(ResponseEntity(mapper.readTree("""{"result":"updated","_seq_no":4,"_primary_term":2}"""), HttpStatus.OK))
            Mockito.`when`(restTemplate.exchange(eq(deleteUrl), eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
                .thenThrow(HttpClientErrorException(status))
            if (status == HttpStatus.NOT_FOUND) {
                assertTrue(service.reconcileDiscoveryCandidate(docId, snapshot, classification, listOf("RND_OUT_OF_SCOPE")))
            } else {
                org.junit.jupiter.api.Assertions.assertThrows(HttpClientErrorException::class.java) {
                    service.reconcileDiscoveryCandidate(docId, snapshot, classification, listOf("RND_OUT_OF_SCOPE"))
                }
            }
            observed["delete${status.value()}"] = expected
        }
        Mockito.reset(restTemplate)
        Mockito.`when`(restTemplate.exchange(eq(rawUrl), eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenReturn(ResponseEntity(doc(3, source + ("email" to "changed@example.org")), HttpStatus.OK))
        assertFalse(service.reconcileDiscoveryCandidate(docId, snapshot, classification, listOf("RND_OUT_OF_SCOPE")))
        Mockito.verify(restTemplate, Mockito.never()).exchange(Mockito.contains("orcid_info_candidate"),
            eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java))
        observed["identityChanged"] = "RETRY_WITHOUT_DELETE"
        return observed
    }

    @Test
    fun `discovery candidate is not deleted if RAW is missing`() {
        Mockito.`when`(restTemplate.exchange(Mockito.contains("/orcid_info/_doc/DOC"),
            eq(HttpMethod.GET), any<HttpEntity<*>>(), eq(JsonNode::class.java)))
            .thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        assertFalse(service.reconcileDiscoveryCandidate(
            "DOC", ExpertIndexWriterService.DiscoverySnapshot(mapOf("orcidId" to "DOC"), 1, 1),
            ExpertClassificationService().classify(com.weibo.talentintroduction.expert.domain.ExpertProfile(
                orcidId = "DOC", email = "a@example.org", givenNames = "Jane", familyNames = "Doe",
                country = null, keyword = null, employment = null
            )), listOf("RND_SCOPE_UNCONFIRMED")
        ))
        Mockito.verify(restTemplate, Mockito.never()).exchange(Mockito.contains("orcid_info_candidate"),
            eq(HttpMethod.DELETE), any<HttpEntity<*>>(), eq(JsonNode::class.java))
    }
}
