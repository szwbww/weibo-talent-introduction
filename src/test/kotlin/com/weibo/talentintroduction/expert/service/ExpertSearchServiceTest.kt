package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.times
import java.time.LocalDateTime

class ExpertSearchServiceTest {
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
    private val service = ExpertSearchService(
        restTemplate = restTemplate,
        properties = properties,
        expertIndexService = ExpertIndexService(properties, restTemplate, mapper)
    )

    @Test
    fun `maps elasticsearch hits to expert profiles including academic fields`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 123},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "country": "United Kingdom",
                      "keyword": "mathematics",
                      "employment": "University",
                      "hIndex": 42,
                      "citationCount": 1500,
                      "lastPublicationYear": 2025
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertEquals(1, result.experts.size)
        assertEquals("Ada Lovelace", result.experts.single().displayName)
        assertEquals(123L, result.totalHits)
        assertEquals(42, result.experts.single().hIndex)
        assertEquals(1500, result.experts.single().citationCount)
        assertEquals(2025, result.experts.single().lastPublicationYear)
    }

    @Test
    fun `academic fields default to null when absent`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "country": "United Kingdom"
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertEquals(1, result.experts.size)
        assertNull(result.experts.single().hIndex)
        assertNull(result.experts.single().citationCount)
        assertNull(result.experts.single().lastPublicationYear)
    }

    @Test
    fun `explicit null fields map to Kotlin null not zero`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "country": "United Kingdom",
                      "hIndex": null,
                      "citationCount": null,
                      "lastPublicationYear": null
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertEquals(1, result.experts.size)
        assertNull(result.experts.single().hIndex)
        assertNull(result.experts.single().citationCount)
        assertNull(result.experts.single().lastPublicationYear)
    }

    @Test
    fun `maps elasticsearch hits to expert profiles with totalHits`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 123},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "country": "United Kingdom",
                      "keyword": "mathematics",
                      "employment": "University"
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertEquals(1, result.experts.size)
        assertEquals("Ada Lovelace", result.experts.single().displayName)
        assertEquals(123L, result.totalHits)
    }

    @Test
    fun `empty response returns zero totalHits`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 0},
                "hits": []
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertEquals(0, result.experts.size)
        assertEquals(0L, result.totalHits)
    }

    @Test
    fun `scroll multi-page processes all batches and cleans up`() {
        val page1 = mapper.readTree(
            """
            {
              "_scroll_id": "scroll-abc",
              "hits": {
                "total": {"value": 5},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "A", "familyNames": "B", "country": "GB"}},
                  {"_source": {"orcidId": "0002", "email": "c@d.com", "givenNames": "C", "familyNames": "D", "country": "US"}}
                ]
              }
            }
            """.trimIndent()
        )
        val page2 = mapper.readTree(
            """
            {
              "_scroll_id": "scroll-def",
              "hits": {
                "total": {"value": 5},
                "hits": [
                  {"_source": {"orcidId": "0003", "email": "e@f.com", "givenNames": "E", "familyNames": "F", "country": "FR"}}
                ]
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search?scroll=5m"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page1, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_search/scroll"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page2, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_search/scroll"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val allExperts = mutableListOf<com.weibo.talentintroduction.expert.domain.ExpertProfile>()
        val batchNumbers = mutableListOf<Int>()
        var capturedTotalHits = -1L
        service.scrollExperts(ExpertIndexLevel.CANDIDATE, batchSize = 2) { batch, batchNumber, totalHits ->
            allExperts.addAll(batch)
            batchNumbers.add(batchNumber)
            capturedTotalHits = totalHits
            true
        }

        assertEquals(3, allExperts.size)
        assertEquals("0001", allExperts[0].orcidId)
        assertEquals("0002", allExperts[1].orcidId)
        assertEquals("0003", allExperts[2].orcidId)
        assertEquals(listOf(1, 2), batchNumbers)
        assertEquals(5L, capturedTotalHits)

        Mockito.verify(restTemplate, times(1)).exchange(
            eq("https://es.example.com:9200/_search/scroll"),
            eq(HttpMethod.DELETE),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `scroll handler early stop still cleans up scroll`() {
        val page1 = mapper.readTree(
            """
            {
              "_scroll_id": "scroll-xyz",
              "hits": {
                "total": {"value": 10},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "A", "familyNames": "B", "country": "GB"}},
                  {"_source": {"orcidId": "0002", "email": "c@d.com", "givenNames": "C", "familyNames": "D", "country": "US"}}
                ]
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search?scroll=5m"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page1, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_search/scroll"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        val processed = mutableListOf<String>()
        service.scrollExperts(ExpertIndexLevel.CANDIDATE, batchSize = 2) { batch, _, _ ->
            processed.addAll(batch.map { it.orcidId })
            false
        }

        assertEquals(2, processed.size)
        assertEquals("0001", processed[0])
        assertEquals("0002", processed[1])

        Mockito.verify(restTemplate, times(1)).exchange(
            eq("https://es.example.com:9200/_search/scroll"),
            eq(HttpMethod.DELETE),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `scroll exception mid-processing still cleans up scroll`() {
        val page1 = mapper.readTree(
            """
            {
              "_scroll_id": "scroll-err",
              "hits": {
                "total": {"value": 2},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "A", "familyNames": "B", "country": "GB"}}
                ]
              }
            }
            """.trimIndent()
        )

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search?scroll=5m"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page1, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_search/scroll"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        try {
            service.scrollExperts(ExpertIndexLevel.CANDIDATE, batchSize = 2) { _, _, _ ->
                error("processing failure")
            }
        } catch (_: Exception) {
        }

        Mockito.verify(restTemplate, times(1)).exchange(
            eq("https://es.example.com:9200/_search/scroll"),
            eq(HttpMethod.DELETE),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `maps new academic fields from elasticsearch source`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001-2345-6789",
                      "email": "test@example.com",
                      "givenNames": "John",
                      "familyNames": "Smith",
                      "country": "GB",
                      "hIndex": 15,
                      "citationCount": 500,
                      "lastPublicationYear": 2024,
                      "researchFields": "machine learning",
                      "institution": "University of Oxford",
                      "emailSource": "PAPER_FULLTEXT",
                      "emailVerifiedLevel": 3,
                      "dataSource": "EUROPE_PMC",
                      "externalIds": {"pmcId": "PMC123"}
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)
        val profile = result.experts.single()

        assertEquals(15, profile.hIndex)
        assertEquals(500, profile.citationCount)
        assertEquals(2024, profile.lastPublicationYear)
        assertEquals("machine learning", profile.researchFields)
        assertEquals("University of Oxford", profile.institution)
        assertEquals("PAPER_FULLTEXT", profile.emailSource)
        assertEquals(3, profile.emailVerifiedLevel)
        assertEquals("EUROPE_PMC", profile.dataSource)
        assertNotNull(profile.externalIds)
        assertTrue(profile.externalIds!!.contains("PMC123"))
    }

    @Test
    fun `explicit null fields map to Kotlin null not string null`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-99",
                      "email": null,
                      "givenNames": "Jane",
                      "familyNames": "Doe",
                      "country": null,
                      "keyword": null,
                      "employment": null,
                      "researchFields": null,
                      "institution": null,
                      "emailSource": null,
                      "dataSource": null
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)
        val profile = result.experts.single()

        // All explicit nulls should map to Kotlin null, not literal "null"
        assertNull(profile.email)
        assertNull(profile.country)
        assertNull(profile.keyword)
        assertNull(profile.employment)
        assertNull(profile.researchFields)
        assertNull(profile.institution)
        assertNull(profile.emailSource)
        assertNull(profile.dataSource)
    }

    @Test
    fun `orcidId null falls back to orcid then id`() {
        val bodyIdOnly = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": null, "orcid": null, "id": "id-only", "givenNames": "A", "familyNames": "B"}}
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
        ).thenReturn(ResponseEntity(bodyIdOnly, HttpStatus.OK))

        val result1 = service.searchExpertsWithEmail(1)
        assertEquals("id-only", result1.experts.single().orcidId)

        val bodyOrcidOnly = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": null, "orcid": "0000-orcid", "givenNames": "A", "familyNames": "B"}}
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
        ).thenReturn(ResponseEntity(bodyOrcidOnly, HttpStatus.OK))

        val result2 = service.searchExpertsWithEmail(1)
        assertEquals("0000-orcid", result2.experts.single().orcidId)
    }

    @Test
    fun `searchExperts with NOT_CONTACTED operatorStatus sets correct query body`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 5},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "operatorStatus": null}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExperts(10, ExpertIndexLevel.CANDIDATE, operatorStatus = "NOT_CONTACTED")

        assertEquals(1, result.experts.size)
        assertNull(result.experts.first().operatorStatus)

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        assertTrue(filter.any { it.toString().contains("exists") && it.toString().contains("email") })
        assertTrue(filter.any { it.toString().contains("must_not") && it.toString().contains("exists") && it.toString().contains("operatorStatus") })
    }

    @Test
    fun `searchExperts with CONTACTED operatorStatus and tag sets correct query body`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "operatorStatus": "CONTACTED", "tags": ["verified"]}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExperts(10, ExpertIndexLevel.CANDIDATE, tag = "verified", operatorStatus = "CONTACTED")

        assertEquals(1, result.experts.size)
        assertEquals("CONTACTED", result.experts.first().operatorStatus)

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        assertTrue(filter.any { it.toString().contains("tags") && it.toString().contains("verified") })
        assertTrue(filter.any { it.toString().contains("operatorStatus") && it.toString().contains("CONTACTED") })
    }

    @Test
    fun `countExperts sends count request with filters`() {
        val responseNode = mapper.readTree("""{"count": 42}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_count"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val count = service.countExperts(
            level = ExpertIndexLevel.CANDIDATE,
            filters = listOf(mapOf("term" to mapOf("operatorStatus" to "REPLIED")))
        )

        assertEquals(42L, count)
        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertTrue(filter.toString().contains("operatorStatus") && filter.toString().contains("REPLIED"))
    }

    @Test
    fun `searchAfterExpertsFiltered paginates with search_after and stops early`() {
        val page1 = mapper.readTree(
            """
            {
              "hits": {
                "hits": [
                  {
                    "_source": {"orcidId": "0001", "email": "a@b.com"},
                    "sort": ["0001"]
                  },
                  {
                    "_source": {"orcidId": "0002", "email": "b@b.com"},
                    "sort": ["0002"]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page1, HttpStatus.OK))

        var processedCount = 0
        service.searchAfterExpertsFiltered(
            level = ExpertIndexLevel.CANDIDATE,
            filters = listOf(mapOf("term" to mapOf("operatorStatus" to "CONTACTED"))),
            batchSize = 2
        ) { batch ->
            processedCount += batch.size
            false
        }

        assertEquals(2, processedCount)
        val firstRequest = capture.value.body as Map<*, *>
        val sort = firstRequest["sort"] as List<*>
        assertTrue(sort.toString().contains("orcidId"))
        assertEquals(null, firstRequest["search_after"])
    }

    @Test
    fun `scrollExpertsFiltered sends search request with filters and cleans up`() {
        val page1 = mapper.readTree(
            """
            {
              "_scroll_id": "scroll-123",
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "operatorStatus": "CONTACTED"}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search?scroll=5m"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(page1, HttpStatus.OK))

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/_search/scroll"),
                eq(HttpMethod.DELETE),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        var processedCount = 0
        service.scrollExpertsFiltered(
            level = ExpertIndexLevel.CANDIDATE,
            filters = listOf(mapOf("term" to mapOf("operatorStatus" to "CONTACTED")))
        ) { batch ->
            processedCount += batch.size
            false
        }

        assertEquals(1, processedCount)
        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertTrue(filter.toString().contains("operatorStatus") && filter.toString().contains("CONTACTED"))

        Mockito.verify(restTemplate, times(1)).exchange(
            eq("https://es.example.com:9200/_search/scroll"),
            eq(HttpMethod.DELETE),
            any(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
    }

    @Test
    fun `maps esDocId from _id field in ES hit`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_id": "ORCID-0000-0001-0002-0003",
                    "_source": {
                      "orcidId": "0000-0001-0002-0003",
                      "email": "expert@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace"
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)
        assertEquals(1, result.experts.size)
        assertEquals("ORCID-0000-0001-0002-0003", result.experts.single().esDocId)
    }

    @Test
    fun `notContactedWithEmailFilters appends wildcard when emailDomain is provided`() {
        val defaultFilters = ExpertSearchService.notContactedWithEmailFilters()
        assertEquals(2, defaultFilters.size)
        
        val filtered = ExpertSearchService.notContactedWithEmailFilters("gmail.com")
        assertEquals(3, filtered.size)
        val wildcardFilter = filtered[2]
        val wildcard = wildcardFilter["wildcard"] as Map<*, *>
        val email = wildcard["email"] as Map<*, *>
        assertEquals("*@gmail.com", email["value"])
    }

    @Test
    fun `searchExperts passes emailDomain wildcard query to ES`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@gmail.com"}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExperts(10, ExpertIndexLevel.CANDIDATE, emailDomain = "gmail.com")

        assertEquals(1, result.experts.size)
        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        assertTrue(filter.any { it.toString().contains("wildcard") && it.toString().contains("email") && it.toString().contains("*@gmail.com") })
    }

    @Test
    fun `aggregateEmailDomains runs terms aggregation and returns domain counts`() {
        val body = mapper.readTree(
            """
            {
              "aggregations": {
                "email_domains": {
                  "buckets": [
                    {"key": "gmail.com", "doc_count": 10},
                    {"key": "outlook.com", "doc_count": 5}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.aggregateEmailDomains(ExpertIndexLevel.CANDIDATE)

        assertEquals(2, result.size)
        assertEquals("gmail.com", result[0].domain)
        assertEquals(10L, result[0].count)
        assertEquals("outlook.com", result[1].domain)
        assertEquals(5L, result[1].count)

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertTrue(filter.any { it.toString().contains("exists") && it.toString().contains("email") })
        val aggs = requestPayload["aggs"] as Map<*, *>
        assertTrue(aggs.containsKey("email_domains"))
    }

    @Test
    fun `aggregateEmailDomains applies region filter but ignores emailDomain`() {
        val body = mapper.readTree(
            """
            {
              "aggregations": {
                "email_domains": {
                  "buckets": [
                    {"key": "gmail.com", "doc_count": 3}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.aggregateEmailDomains(
            ExpertIndexLevel.CANDIDATE,
            tag = null,
            operatorStatus = null,
            region = "Europe"
        )

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertTrue(filter.any { it.toString().contains("terms") && it.toString().contains("country") })
        assertFalse(filter.any { it.toString().contains("wildcard") && it.toString().contains("email") })
    }

    @Test
    fun `aggregateRegions applies emailDomain filter but ignores region`() {
        val body = mapper.readTree(
            """
            {
              "aggregations": {
                "countries": {
                  "buckets": []
                }
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.aggregateRegions(
            ExpertIndexLevel.CANDIDATE,
            tag = null,
            operatorStatus = null,
            emailDomain = "gmail.com"
        )

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertTrue(filter.any { it.toString().contains("wildcard") && it.toString().contains("*@gmail.com") })
    }

    @Test
    fun `aggregateRegions merges country buckets into region counts`() {
        val body = mapper.readTree(
            """
            {
              "aggregations": {
                "countries": {
                  "buckets": [
                    {"key": "China", "doc_count": 10},
                    {"key": "Japan", "doc_count": 5},
                    {"key": "India", "doc_count": 3},
                    {"key": "Germany", "doc_count": 7},
                    {"key": "US", "doc_count": 12},
                    {"key": "Brazil", "doc_count": 4},
                    {"key": "xyzabc", "doc_count": 2}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.aggregateRegions(ExpertIndexLevel.CANDIDATE)

        val resultMap = result.associate { it.region to it.count }
        assertEquals(10L, resultMap["China"])
        assertEquals(5L, resultMap["Asia (Japan & Korea)"])
        assertEquals(3L, resultMap["Asia (Other)"])
        assertEquals(7L, resultMap["Europe"])
        assertEquals(12L, resultMap["North America"])
        assertEquals(4L, resultMap["South America"])
        assertEquals(2L, resultMap["Other"])

        val requestPayload = capture.value.body as Map<*, *>
        assertEquals(0, requestPayload["size"])
        val aggs = requestPayload["aggs"] as Map<*, *>
        val countriesAgg = aggs["countries"] as Map<*, *>
        val terms = countriesAgg["terms"] as Map<*, *>
        assertEquals("country", terms["field"])
        assertFalse(terms.containsKey("script"))
    }

    @Test
    fun `searchExperts with region adds terms filter on country and nationality`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@example.com", "country": "GB"}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(10, ExpertIndexLevel.CANDIDATE, region = "Europe")

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        val regionFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val innerBool = map["bool"] as? Map<*, *> ?: return@firstOrNull false
            val should = innerBool["should"] as? List<*> ?: return@firstOrNull false
            should.size == 2 &&
                should.any { (it as Map<*, *>)["terms"]?.toString()?.contains("country") == true } &&
                should.any { (it as Map<*, *>)["terms"]?.toString()?.contains("nationality") == true }
        }
        assertNotNull(regionFilter)
        val innerBool = (regionFilter as Map<*, *>)["bool"] as Map<*, *>
        assertEquals(1, innerBool["minimum_should_match"])
    }

    @Test
    fun `countByFieldPresence uses exists filters for SATISFY_ALL`() {
        val responseNode = mapper.readTree("""{"count": 7}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_count"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val count = service.countByFieldPresence(
            ExpertIndexLevel.CANDIDATE,
            listOf("institution", "country"),
            FieldPresenceMode.SATISFY_ALL
        )

        assertEquals(7L, count)
        val requestPayload = capture.value.body as Map<*, *>
        val filter = ((requestPayload["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>
        assertTrue(filter.toString().contains("exists") && filter.toString().contains("institution"))
        assertTrue(filter.toString().contains("exists") && filter.toString().contains("country"))
    }

    @Test
    fun `countByFieldPresence uses should must_not exists for MISSING_ANY`() {
        val responseNode = mapper.readTree("""{"count": 3}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_count"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val count = service.countByFieldPresence(
            ExpertIndexLevel.APPLICATION,
            listOf("institution"),
            FieldPresenceMode.MISSING_ANY
        )

        assertEquals(3L, count)
        val requestPayload = capture.value.body as Map<*, *>
        val bool = ((requestPayload["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>
        val innerBool = ((bool.first() as Map<*, *>)["bool"] as Map<*, *>)
        assertEquals(1, innerBool["minimum_should_match"])
        assertTrue(innerBool["should"].toString().contains("must_not"))
    }

    @Test
    fun `findRandomByFieldPresence wraps query with function_score random_score and size 20`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "Ada", "familyNames": "Lovelace", "institution": "MIT"}}
                ]
              }
            }
            """.trimIndent()
        )
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val expert = service.findRandomByFieldPresence(
            ExpertIndexLevel.CANDIDATE,
            listOf("institution"),
            FieldPresenceMode.SATISFY_ALL
        )

        assertNotNull(expert)
        assertEquals("0001", expert!!.orcidId)
        val requestPayload = capture.value.body as Map<*, *>
        assertEquals(20, requestPayload["size"])
        val functionScore = (requestPayload["query"] as Map<*, *>)["function_score"] as Map<*, *>
        assertTrue(functionScore.containsKey("random_score"))
    }

    @Test
    fun `findRandomByFieldPresence filters blank institution for SATISFY_ALL batch`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "A", "familyNames": "One", "institution": "   "}},
                  {"_source": {"orcidId": "0002", "email": "b@b.com", "givenNames": "B", "familyNames": "Two", "institution": "MIT"}}
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val expert = service.findRandomByFieldPresence(
            ExpertIndexLevel.CANDIDATE,
            listOf("institution"),
            FieldPresenceMode.SATISFY_ALL
        )

        assertNotNull(expert)
        assertEquals("0002", expert!!.orcidId)
    }

    @Test
    fun `findRandomByFieldPresence returns null when SATISFY_ALL batch has only blank text values`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com", "givenNames": "A", "familyNames": "One", "institution": ""}}
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val expert = service.findRandomByFieldPresence(
            ExpertIndexLevel.CANDIDATE,
            listOf("institution"),
            FieldPresenceMode.SATISFY_ALL
        )

        assertNull(expert)
    }

    @Test
    fun `notContactedWithEmailFilters appends term for STEM discipline`() {
        val filters = ExpertSearchService.notContactedWithEmailFilters(discipline = "STEM")
        assertEquals(3, filters.size)
        val term = filters[2]["term"] as Map<*, *>
        assertEquals("STEM", term["disciplineCategory"])
    }

    @Test
    fun `notContactedWithEmailFilters appends must_not exists for UNCLASSIFIED`() {
        val filters = ExpertSearchService.notContactedWithEmailFilters(discipline = "UNCLASSIFIED")
        assertEquals(3, filters.size)
        val bool = filters[2]["bool"] as Map<*, *>
        val mustNot = bool["must_not"] as List<*>
        val exists = (mustNot[0] as Map<*, *>)["exists"] as Map<*, *>
        assertEquals("disciplineCategory", exists["field"])
    }

    @Test
    fun `notContactedWithEmailFilters rejects illegal discipline`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpertSearchService.notContactedWithEmailFilters(discipline = "UNKNOWN")
        }
    }

    @Test
    fun `searchExperts passes discipline term filter to ES`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@x.com", "disciplineCategory": "HUMANITIES"}}
                ]
              }
            }
            """.trimIndent()
        )
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(size = 10, level = ExpertIndexLevel.CANDIDATE, discipline = "HUMANITIES")

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val request = entityCaptor.value.body as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val query = request["query"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val bool = query["bool"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val filter = bool["filter"] as List<Map<String, Any>>
        val termFilter = filter.first { it.containsKey("term") && (it["term"] as Map<*, *>).containsKey("disciplineCategory") }
        assertEquals("HUMANITIES", (termFilter["term"] as Map<*, *>)["disciplineCategory"])
    }

    @Test
    fun `ALLOWED_EXPERT_TYPES derives exactly from ExpertType enum plus UNCLASSIFIED (I1-1)`() {
        assertEquals(
            ExpertType.values().map { it.name }.toSet() + "UNCLASSIFIED",
            ExpertSearchService.ALLOWED_EXPERT_TYPES
        )
        assertEquals(ExpertType.values().size + 1, ExpertSearchService.ALLOWED_EXPERT_TYPES.size)
        assertTrue("UNCLASSIFIED" in ExpertSearchService.ALLOWED_EXPERT_TYPES)
    }

    @Test
    fun `expertTypesFilter empty list returns null meaning unrestricted (I1-2)`() {
        assertNull(ExpertSearchService.expertTypesFilter(emptyList()))
        assertNull(ExpertSearchService.expertTypesFilter(listOf("  ", "")))
    }

    @Test
    fun `expertTypesFilter single value produces one term predicate (I1-1 I1-3)`() {
        val filter = ExpertSearchService.expertTypesFilter(listOf("ACADEMIC_RND"))
        assertNotNull(filter)
        val bool = filter!!["bool"] as Map<*, *>
        assertEquals(1, bool["minimum_should_match"])
        val should = bool["should"] as List<*>
        assertEquals(1, should.size)
        val term = (should[0] as Map<*, *>)["term"] as Map<*, *>
        assertEquals("ACADEMIC_RND", term["expertClassification.type"])
    }

    @Test
    fun `expertTypesFilter multiple values produces single should with minimum_should_match 1 (I1-3)`() {
        val filter = ExpertSearchService.expertTypesFilter(listOf("PRODUCTION_RND", "ACADEMIC_RND"))
        assertNotNull(filter)
        // I1-3：顶层不得出现平铺的 filter 键（那是 AND，恒零命中），只能是单个 bool.should
        assertEquals(setOf("bool"), filter!!.keys)
        val bool = filter["bool"] as Map<*, *>
        assertEquals(1, bool["minimum_should_match"])
        val should = bool["should"] as List<*>
        assertEquals(2, should.size)
        val types = should.map { ((it as Map<*, *>)["term"] as Map<*, *>)["expertClassification.type"] }
        assertEquals(listOf("PRODUCTION_RND", "ACADEMIC_RND"), types)
    }

    @Test
    fun `expertTypesFilter trims and dedups values like operatorStatusesFilter`() {
        val filter = ExpertSearchService.expertTypesFilter(listOf(" ACADEMIC_RND ", "ACADEMIC_RND", "out_of_scope".uppercase()))
        assertNotNull(filter)
        val bool = filter!!["bool"] as Map<*, *>
        val should = bool["should"] as List<*>
        assertEquals(2, should.size)
    }

    @Test
    fun `expertTypesFilter UNCLASSIFIED produces must_not exists on expertClassification dot type (I1-1)`() {
        val filter = ExpertSearchService.expertTypesFilter(listOf("UNCLASSIFIED"))
        assertNotNull(filter)
        val bool = filter!!["bool"] as Map<*, *>
        val should = bool["should"] as List<*>
        assertEquals(1, should.size)
        val innerBool = (should[0] as Map<*, *>)["bool"] as Map<*, *>
        val mustNot = innerBool["must_not"] as List<*>
        val exists = (mustNot[0] as Map<*, *>)["exists"] as Map<*, *>
        assertEquals("expertClassification.type", exists["field"])
    }

    @Test
    fun `expertTypesFilter rejects unknown value with IllegalArgumentException (I1-1)`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpertSearchService.expertTypesFilter(listOf("NOT_A_TYPE"))
        }
    }

    @Test
    fun `searchExperts with empty expertTypes keeps filter array identical to default (I1-2 I1-4)`() {
        fun captureQueryBody(expertTypes: List<String>): com.fasterxml.jackson.databind.JsonNode {
            val body = mapper.readTree(
                """
                {
                  "hits": {
                    "total": {"value": 1},
                    "hits": [
                      {"_source": {"orcidId": "0001", "email": "a@x.com"}}
                    ]
                  }
                }
                """.trimIndent()
            )
            val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
            Mockito.`when`(
                restTemplate.exchange(
                    eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                    eq(HttpMethod.POST),
                    any(),
                    eq(com.fasterxml.jackson.databind.JsonNode::class.java)
                )
            ).thenReturn(ResponseEntity(body, HttpStatus.OK))
            service.searchExperts(
                size = 10,
                level = ExpertIndexLevel.CANDIDATE,
                tag = "verified",
                discipline = "STEM",
                expertTypes = expertTypes
            )
            Mockito.verify(restTemplate).exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
            @Suppress("UNCHECKED_CAST")
            return mapper.valueToTree((entityCaptor.value.body as Map<String, Any>)["query"])
        }

        val withDefault = captureQueryBody(emptyList())
        Mockito.clearInvocations(restTemplate)
        val withExplicitEmpty = captureQueryBody(listOf())
        assertEquals(withDefault, withExplicitEmpty)
        // 空集合不追加任何 filter：只有 tag + discipline 两项，无 expertType 结构
        assertEquals(2, withExplicitEmpty["bool"]["filter"].size())
    }

    @Test
    fun `searchExperts passes expertType multi-term should filter to ES (I1-1 I1-3)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@x.com"}}
                ]
              }
            }
            """.trimIndent()
        )
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(
            size = 10,
            level = ExpertIndexLevel.CANDIDATE,
            expertTypes = listOf("ACADEMIC_RND", "OUT_OF_SCOPE")
        )

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        @Suppress("UNCHECKED_CAST")
        val request = entityCaptor.value.body as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val query = request["query"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val bool = query["bool"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val filter = bool["filter"] as List<Map<String, Any>>
        val expertTypeFilter = filter.first { it.containsKey("bool") && (it["bool"] as Map<*, *>).containsKey("should") }
        @Suppress("UNCHECKED_CAST")
        val should = (expertTypeFilter["bool"] as Map<String, Any>)["should"] as List<Map<String, Any>>
        assertEquals(2, should.size)
        assertEquals(
            mapOf("bool" to mapOf("should" to listOf(
                mapOf("term" to mapOf("expertClassification.type" to "ACADEMIC_RND")),
                mapOf("term" to mapOf("expertClassification.type" to "OUT_OF_SCOPE"))
            ), "minimum_should_match" to 1)),
            expertTypeFilter
        )
    }

    @Test
    fun `ALLOWED_HAS_FIELDS includes recentWorkTitles so gate fields never 500`() {
        assertTrue("recentWorkTitles" in ExpertSearchService.ALLOWED_HAS_FIELDS)
        assertTrue(
            ExpertSearchService.ALLOWED_HAS_FIELDS.containsAll(
                listOf("employment", "degree", "institution", "researchFields", "patentTitles")
            )
        )
    }

    @Test
    fun `BLANK_EXCLUDABLE_FIELDS equals the keyword-type field set exactly`() {
        assertEquals(
            setOf("researchFields", "recentWorkTitles", "patentTitles", "degree", "country"),
            ExpertSearchService.BLANK_EXCLUDABLE_FIELDS
        )
    }

    @Test
    fun `searchExperts blank-excludes keyword researchFields but uses bare exists for text institution`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 2},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@x.com", "researchFields": "AI", "institution": "USTC"}}
                ]
              }
            }
            """.trimIndent()
        )
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(
            size = 10,
            level = ExpertIndexLevel.CANDIDATE,
            hasField = listOf("researchFields", "institution")
        )

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val query = request["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        // keyword field: exists + must_not term "" (blank values excluded, I-9/I-12)
        val researchFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val inner = map["bool"] as? Map<*, *>
            inner != null && (inner["must"] as List<*>).toString().contains("researchFields")
        }
        assertNotNull(researchFilter, "researchFields filter missing from: $filter")
        val researchBool = (researchFilter as Map<*, *>)["bool"] as Map<*, *>
        val researchMust = researchBool["must"] as List<*>
        val researchMustNot = researchBool["must_not"] as List<*>
        assertEquals(1, researchMust.size)
        assertEquals(1, researchMustNot.size)
        assertTrue(researchMust.toString().contains("exists") && researchMust.toString().contains("researchFields"))
        val term = (researchMustNot[0] as Map<*, *>)["term"] as Map<*, *>
        assertEquals("", term["researchFields"])

        // text field: bare exists only, no bool wrapper
        val institutionFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val exists = map["exists"] as? Map<*, *>
            exists != null && exists["field"] == "institution"
        }
        assertNotNull(institutionFilter, "institution filter missing from: $filter")
        assertFalse((institutionFilter as Map<*, *>).containsKey("bool"))
        assertTrue((institutionFilter["exists"] as Map<*, *>)["field"].toString().contains("institution"))
    }

    @Test
    fun `searchExperts rejects unknown hasField with IllegalArgumentException`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            service.searchExperts(
                size = 10,
                level = ExpertIndexLevel.CANDIDATE,
                hasField = listOf("notAnEsField")
            )
        }
    }

    @Test
    fun `countByFieldPresence SATISFY_ALL blank-excludes keyword fields but keeps bare exists for text`() {
        val responseNode = mapper.readTree("""{"count": 7}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_count"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(responseNode, HttpStatus.OK))

        val count = service.countByFieldPresence(
            ExpertIndexLevel.CANDIDATE,
            listOf("researchFields", "institution"),
            FieldPresenceMode.SATISFY_ALL
        )

        assertEquals(7L, count)
        val requestPayload = capture.value.body as Map<*, *>
        val filter = ((requestPayload["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>

        val researchFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val inner = map["bool"] as? Map<*, *>
            inner != null && (inner["must"] as List<*>).toString().contains("researchFields")
        }
        assertNotNull(researchFilter, "researchFields filter missing from: $filter")
        val researchBool = (researchFilter as Map<*, *>)["bool"] as Map<*, *>
        val researchMustNot = researchBool["must_not"] as List<*>
        val term = (researchMustNot[0] as Map<*, *>)["term"] as Map<*, *>
        assertEquals("", term["researchFields"])

        val institutionFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val exists = map["exists"] as? Map<*, *>
            exists != null && exists["field"] == "institution"
        }
        assertNotNull(institutionFilter, "institution filter missing from: $filter")
        assertFalse((institutionFilter as Map<*, *>).containsKey("bool"))
    }

    @Test
    fun `regionsFilter empty list returns null meaning unrestricted`() {
        assertNull(ExpertSearchService.regionsFilter(emptyList()))
    }

    @Test
    fun `regionsFilter single region equals regionFilter verbatim`() {
        assertEquals(
            ExpertSearchService.regionFilter("China"),
            ExpertSearchService.regionsFilter(listOf("China"))
        )
    }

    @Test
    fun `regionsFilter multiple regions builds one should clause with minimum_should_match 1`() {
        val filter = ExpertSearchService.regionsFilter(listOf("China", "Europe"))
        assertNotNull(filter)
        val bool = (filter as Map<*, *>)["bool"] as Map<*, *>
        val should = bool["should"] as List<*>
        assertEquals(2, should.size)
        assertEquals(1, bool["minimum_should_match"])
    }

    @Test
    fun `regionsFilter rejects unknown region with IllegalArgumentException`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            ExpertSearchService.regionsFilter(listOf("Mars"))
        }
    }

    @Test
    fun `searchExperts with Other region keeps double-branch should structure (regression I-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@example.com", "country": "GB"}}
                ]
              }
            }
            """.trimIndent()
        )

        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(10, ExpertIndexLevel.CANDIDATE, region = "Other")

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>

        // REGION_OTHER special case: bool.should of two bool branches
        // (must exists country / nationality + must_not terms known values) + minimum_should_match 1.
        val regionFilter = filter.firstOrNull { item ->
            val map = item as Map<*, *>
            val innerBool = map["bool"] as? Map<*, *> ?: return@firstOrNull false
            val should = innerBool["should"] as? List<*> ?: return@firstOrNull false
            should.size == 2 &&
                should.all { sub ->
                    val subBool = (sub as Map<*, *>)["bool"] as? Map<*, *> ?: return@all false
                    val must = subBool["must"] as? List<*> ?: return@all false
                    val mustNot = subBool["must_not"] as? List<*> ?: return@all false
                    val existsField = ((must[0] as Map<*, *>)["exists"] as? Map<*, *>)?.get("field")
                    (existsField == "country" || existsField == "nationality") &&
                        mustNot.any { (it as Map<*, *>)["terms"] != null }
                }
        }
        assertNotNull(regionFilter, "Other region filter missing from: $filter")
        val innerBool = (regionFilter as Map<*, *>)["bool"] as Map<*, *>
        assertEquals(1, innerBool["minimum_should_match"])
    }

    // ---- I1-5: expertClassification 读取与 mapping ----

    @Test
    fun `search request _source includes expertClassification (I1-5)`() {
        val body = mapper.readTree("""{"hits":{"total":{"value":0},"hits":[]}}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(10, ExpertIndexLevel.CANDIDATE)

        val requestPayload = capture.value.body as Map<*, *>
        val source = requestPayload["_source"] as List<*>
        assertTrue(source.contains("expertClassification"), "sourceFields must include expertClassification: $source")
    }

    @Test
    fun `search request _source includes institutionType (I5a-4)`() {
        val body = mapper.readTree("""{"hits":{"total":{"value":0},"hits":[]}}""")
        val capture = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(10, ExpertIndexLevel.CANDIDATE)

        val requestPayload = capture.value.body as Map<*, *>
        val source = requestPayload["_source"] as List<*>
        assertTrue(source.contains("institutionType"), "sourceFields must include institutionType: $source")
    }

    @Test
    fun `toExpertProfile parses institutionType from source (I5a-4)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001-2345-6789",
                      "email": "test@example.com",
                      "givenNames": "John",
                      "familyNames": "Smith",
                      "country": "GB",
                      "institution": "OpenAlex",
                      "institutionType": "nonprofit"
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val profile = service.searchExpertsWithEmail(1).experts.single()
        assertEquals("nonprofit", profile.institutionType)
        assertEquals("OpenAlex", profile.institution)
    }

    @Test
    fun `toExpertProfile institutionType null when key missing or blank (I5a-3)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 2},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001-2345-6789",
                      "email": "missing@example.com",
                      "givenNames": "John",
                      "familyNames": "Smith"
                    }
                  },
                  {
                    "_source": {
                      "orcidId": "0000-0002-2345-6789",
                      "email": "blank@example.com",
                      "givenNames": "Jane",
                      "familyNames": "Doe",
                      "institutionType": ""
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val experts = service.searchExpertsWithEmail(2).experts
        assertNull(experts[0].institutionType)
        assertNull(experts[1].institutionType)
    }

    @Test
    fun `parses expertClassification with type-derived sendable ignoring untrusted ES sendable (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "expertClassification": {
                        "type": "PRODUCTION_RND",
                        "sendable": false,
                        "productionScore": 95,
                        "researchScore": 15,
                        "positiveEvidence": ["PROD_PATENTS", "PROD_ROLE"],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234",
                        "classifiedAt": "2026-01-15 10:30:00"
                      }
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        val c = result.experts.single().expertClassification!!
        assertNotNull(c)
        assertEquals(ExpertType.PRODUCTION_RND, c.type)
        assertTrue(c.sendable, "domain sendable must derive from type, not from untrusted ES sendable=false")
        assertEquals(95, c.productionScore)
        assertEquals(15, c.researchScore)
        assertEquals(listOf("PROD_PATENTS", "PROD_ROLE"), c.positiveEvidence)
        assertEquals(emptyList<String>(), c.negativeEvidence)
        assertEquals("rnd-v1-2026", c.version)
        assertEquals("abcd1234", c.sourceFingerprint)
        assertEquals(LocalDateTime.of(2026, 1, 15, 10, 30), c.classifiedAt)
    }

    @Test
    fun `ES sendable=true cannot override OUT_OF_SCOPE derived sendable (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "expertClassification": {
                        "type": "OUT_OF_SCOPE",
                        "sendable": true,
                        "productionScore": 0,
                        "researchScore": 95,
                        "positiveEvidence": [],
                        "negativeEvidence": ["MEDICAL_DOMAIN_NO_WHITELIST"],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234",
                        "classifiedAt": "2026-01-15 10:30:00"
                      }
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        val c = result.experts.single().expertClassification!!
        assertNotNull(c)
        assertEquals(ExpertType.OUT_OF_SCOPE, c.type)
        assertFalse(c.sendable, "ES sendable=true must not override type-derived sendable")
    }

    @Test
    fun `missing expertClassification maps to null (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0000-0001", "email": "expert@example.com"}}
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertNull(result.experts.single().expertClassification)
    }

    @Test
    fun `explicit null expertClassification maps to null (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0000-0001", "email": "expert@example.com", "expertClassification": null}}
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertNull(result.experts.single().expertClassification)
    }

    @Test
    fun `unknown expertClassification type maps whole object to null (fail closed, I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "expertClassification": {
                        "type": "MYSTERY_TYPE",
                        "sendable": true,
                        "productionScore": 95,
                        "researchScore": 15,
                        "positiveEvidence": [],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234",
                        "classifiedAt": "2026-01-15 10:30:00"
                      }
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertNull(
            result.experts.single().expertClassification,
            "unknown type must fail closed to null, never silently map to UNKNOWN"
        )
    }

    @Test
    fun `partial expertClassification with missing subfields maps to null (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "expert@example.com",
                      "expertClassification": {
                        "type": "PRODUCTION_RND",
                        "productionScore": 95,
                        "researchScore": 15,
                        "positiveEvidence": ["PROD_PATENTS"],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234"
                      }
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(1)

        assertNull(
            result.experts.single().expertClassification,
            "missing classifiedAt must fail closed to null"
        )
    }

    @Test
    fun `classifiedAt supports date-only and epoch_millis formats (I1-5)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 2},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0000-0001",
                      "email": "a@example.com",
                      "expertClassification": {
                        "type": "ACADEMIC_RND",
                        "sendable": true,
                        "productionScore": 0,
                        "researchScore": 95,
                        "positiveEvidence": [],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234",
                        "classifiedAt": "2026-01-15"
                      }
                    }
                  },
                  {
                    "_source": {
                      "orcidId": "0000-0002",
                      "email": "b@example.com",
                      "expertClassification": {
                        "type": "ACADEMIC_RND",
                        "sendable": true,
                        "productionScore": 0,
                        "researchScore": 95,
                        "positiveEvidence": [],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "abcd1234",
                        "classifiedAt": 1768444200000
                      }
                    }
                  }
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
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsWithEmail(2)

        val experts = result.experts
        assertEquals(2, experts.size)
        assertEquals(LocalDateTime.of(2026, 1, 15, 0, 0), experts[0].expertClassification?.classifiedAt)
        assertNotNull(experts[1].expertClassification?.classifiedAt, "epoch_millis classifiedAt must parse")
    }

    @Test
    fun `all three ES mappings declare identical expertClassification object (I1-5)`() {
        fun readProperties(name: String) =
            mapper.readTree(
                ExpertSearchServiceTest::class.java.getResourceAsStream("/es/$name.json")!!
            ).path("mappings").path("properties")

        val raw = readProperties("orcid_info_raw")
        val candidate = readProperties("orcid_info_candidate")
        val application = readProperties("orcid_info_application")

        val rawEc = raw.path("expertClassification")
        assertFalse(rawEc.isMissingNode, "RAW mapping must declare expertClassification")
        assertEquals(rawEc, candidate.path("expertClassification"), "CANDIDATE mapping must be identical")
        assertEquals(rawEc, application.path("expertClassification"), "APPLICATION mapping must be identical")

        val props = rawEc.path("properties")
        assertEquals("keyword", props.path("type").path("type").asText())
        assertEquals("boolean", props.path("sendable").path("type").asText())
        assertEquals("integer", props.path("productionScore").path("type").asText())
        assertEquals("integer", props.path("researchScore").path("type").asText())
        assertEquals("keyword", props.path("positiveEvidence").path("type").asText())
        assertEquals("keyword", props.path("negativeEvidence").path("type").asText())
        assertEquals("keyword", props.path("version").path("type").asText())
        assertEquals("keyword", props.path("sourceFingerprint").path("type").asText())
        assertEquals("date", props.path("classifiedAt").path("type").asText())
        assertEquals(
            "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis",
            props.path("classifiedAt").path("format").asText()
        )

        // 不使用 enabled:false；无根级第二事实源（I1-5、M-3）
        assertFalse(rawEc.toString().contains("enabled"))
        assertFalse(raw.has("sendable"))
        assertFalse(raw.has("expertType"))
        assertTrue(raw.has("expertClassification"))
    }

    // ──── I3: INTRODUCTION sendable gate (child 03) ──────────────────────────

    @Test
    fun `expertSendableFilter requires sendable and accepted policy version (I3-2 I5a2-9)`() {
        assertEquals(
            mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("term" to mapOf("expertClassification.sendable" to true)),
                        mapOf("terms" to mapOf(
                            "expertClassification.version" to ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS
                        ))
                    )
                )
            ),
            ExpertSearchService.expertSendableFilter()
        )
    }

    @Test
    fun `searchSendableExpertsWithEmail sends exists email AND sendable term on CANDIDATE (I3-1 I3-2)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0001",
                      "email": "a@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "expertClassification": {
                        "type": "PRODUCTION_RND",
                        "sendable": true,
                        "productionScore": 80,
                        "researchScore": 20,
                        "positiveEvidence": ["RND_PRODUCTION"],
                        "negativeEvidence": [],
                        "version": "rnd-v1-2026",
                        "sourceFingerprint": "fp",
                        "classifiedAt": "2026-08-01 12:00:00"
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchSendableExpertsWithEmail(1)

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val query = request["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertEquals(2, filter.size, "sendable query must carry exactly exists email + current-policy sendable gate")
        assertTrue(filter.toString().contains("exists") && filter.toString().contains("email"))
        assertEquals(
            ExpertSearchService.expertSendableFilter(),
            filter.firstOrNull { item -> item == ExpertSearchService.expertSendableFilter() }
        )
        // I3-2: 查询仍按现有层级排序 —— CANDIDATE 使用 candidateValidatedAt。
        val sort = request["sort"] as List<*>
        assertTrue(sort.toString().contains("candidateValidatedAt"), "CANDIDATE sort must be candidateValidatedAt: $sort")

        // 结果映射仍完整（含分类对象）。
        assertEquals(1, result.experts.size)
        assertEquals("0001", result.experts.single().orcidId)
        assertEquals(ExpertType.PRODUCTION_RND, result.experts.single().expertClassification?.type)
        assertTrue(result.experts.single().expertClassification?.sendable == true)
        assertEquals(1L, result.totalHits)
    }

    @Test
    fun `searchSendableExpertsWithEmail honors explicit level and its sort (I3-2)`() {
        val body = mapper.readTree("""{"hits":{"total":{"value":0},"hits":[]}}""")
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchSendableExpertsWithEmail(5, ExpertIndexLevel.APPLICATION)

        assertEquals(0, result.experts.size)
        assertEquals(0L, result.totalHits)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_application/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val filter = ((request["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>
        assertEquals(
            ExpertSearchService.expertSendableFilter(),
            filter.firstOrNull { item -> item == ExpertSearchService.expertSendableFilter() }
        )
        val sort = request["sort"] as List<*>
        assertTrue(sort.toString().contains("applicationPromotedAt"), "APPLICATION sort must be applicationPromotedAt: $sort")
    }

    @Test
    fun `searchExpertsWithEmail stays a generic query without the sendable term (I3-2)`() {
        val body = mapper.readTree("""{"hits":{"total":{"value":0},"hits":[]}}""")
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExpertsWithEmail(1)

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val filter = ((request["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>
        assertEquals(1, filter.size, "generic searchExpertsWithEmail must keep only exists email")
        assertFalse(filter.toString().contains("expertClassification"), "generic query must not carry the sendable term")
    }

    // ──── I2: 旧首发链路显式类型集合（child 02） ─────────────────────────────

    @Test
    fun `searchExpertsByTypesWithEmail filter is exactly exists email plus types filter (I2-4)`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {
                    "_source": {
                      "orcidId": "0001",
                      "email": "a@example.com",
                      "givenNames": "Ada",
                      "familyNames": "Lovelace",
                      "expertClassification": {
                        "type": "PRODUCTION_RND",
                        "productionScore": 80,
                        "researchScore": 20,
                        "positiveEvidence": ["RND_PRODUCTION"],
                        "negativeEvidence": [],
                        "version": "rnd-v2-2026",
                        "sourceFingerprint": "fp",
                        "classifiedAt": "2026-08-01 12:00:00"
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val types = listOf("PRODUCTION_RND", "OUT_OF_SCOPE")
        val result = service.searchExpertsByTypesWithEmail(1, expertTypes = types)

        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_candidate/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val query = request["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertEquals(2, filter.size, "I2-4: types query must carry exactly exists email + types filter")
        assertTrue(filter.toString().contains("exists") && filter.toString().contains("email"))
        assertEquals(
            ExpertSearchService.expertTypesFilter(types),
            filter.firstOrNull { item -> item == ExpertSearchService.expertTypesFilter(types) }
        )
        // I2-4: 不得混入 sendable/version 等任何其他条件（M-1）。
        assertFalse(filter.toString().contains("sendable"), "I2-4: types query must not carry the sendable gate")
        assertFalse(filter.toString().contains("version"), "I2-4: types query must not carry the version gate")
        // 排序与旧方法一致 —— CANDIDATE 使用 candidateValidatedAt。
        val sort = request["sort"] as List<*>
        assertTrue(sort.toString().contains("candidateValidatedAt"), "CANDIDATE sort must be candidateValidatedAt: $sort")

        // 结果映射仍完整（含分类对象）。
        assertEquals(1, result.experts.size)
        assertEquals("0001", result.experts.single().orcidId)
        assertEquals(ExpertType.PRODUCTION_RND, result.experts.single().expertClassification?.type)
        assertEquals(1L, result.totalHits)
    }

    @Test
    fun `searchExpertsByTypesWithEmail rejects empty expertTypes (I2-2)`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.searchExpertsByTypesWithEmail(1, expertTypes = emptyList())
        }
        assertTrue(ex.message!!.contains("expertTypes must not be empty"))
    }

    @Test
    fun `searchExpertsByTypesWithEmail honors explicit level and its sort (I2-4)`() {
        val body = mapper.readTree("""{"hits":{"total":{"value":0},"hits":[]}}""")
        val entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_application/_search"),
                eq(HttpMethod.POST),
                any(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExpertsByTypesWithEmail(5, ExpertIndexLevel.APPLICATION, listOf("ACADEMIC_RND"))

        assertEquals(0, result.experts.size)
        assertEquals(0L, result.totalHits)
        Mockito.verify(restTemplate).exchange(
            eq("https://es.example.com:9200/orcid_info_application/_search"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(com.fasterxml.jackson.databind.JsonNode::class.java)
        )
        val request = entityCaptor.value.body as Map<*, *>
        val filter = ((request["query"] as Map<*, *>)["bool"] as Map<*, *>)["filter"] as List<*>
        assertEquals(
            ExpertSearchService.expertTypesFilter(listOf("ACADEMIC_RND")),
            filter.firstOrNull { item -> item == ExpertSearchService.expertTypesFilter(listOf("ACADEMIC_RND")) }
        )
        val sort = request["sort"] as List<*>
        assertTrue(sort.toString().contains("applicationPromotedAt"), "APPLICATION sort must be applicationPromotedAt: $sort")
    }
}
