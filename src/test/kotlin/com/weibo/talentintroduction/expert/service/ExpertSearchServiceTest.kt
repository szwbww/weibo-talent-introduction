package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
}
