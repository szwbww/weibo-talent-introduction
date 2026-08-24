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
    fun `mapping update sends all JSON-declared fields`() {
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

        // I-1: JSON is the single declaration source — every JSON-declared field is pushed,
        // no Kotlin-side field-name whitelist exists anymore.
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

        // Legacy fields are pushed too — previously blocked by the whitelist
        assertFieldExists(properties, "country")
        assertFieldExists(properties, "email")
        assertFieldExists(properties, "givenNames")
        assertFieldExists(properties, "familyNames")
        assertFieldExists(properties, "orcidId")
        assertFieldExists(properties, "keyword")
        assertFieldExists(properties, "degree")
        assertFieldExists(properties, "age")
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
    fun `PUT returns 400 degrades to per-field PUT and does not block remaining indices`() {
        // All three indices exist
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        // First batch mapping PUT (RAW) returns 400 → per-field degradation kicks in;
        // CANDIDATE/APPLICATION batch PUTs succeed
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.contains("/_mapping"),
                Mockito.eq(HttpMethod.PUT),
                entityCaptor.capture(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST))
            .thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        service.bootstrapMappings()

        val captured = entityCaptor.allValues
        val batchPuts = captured.count {
            ((it.body as Map<*, *>)["properties"] as Map<*, *>).size > 1
        }
        val singleFieldPuts = captured.count {
            ((it.body as Map<*, *>)["properties"] as Map<*, *>).size == 1
        }
        // I-2: all three indices still get their batch PUT attempt
        org.junit.jupiter.api.Assertions.assertEquals(3, batchPuts, "each index must get one batch PUT attempt")
        // I-2: the failing RAW batch degrades to one PUT per JSON-declared field (33 in orcid_info_raw.json)
        org.junit.jupiter.api.Assertions.assertEquals(33, singleFieldPuts, "RAW batch failure must degrade to per-field PUTs for every declared field")
    }

    @Test
    fun `bootstrapMappings pushes operatorStatus keyword for candidate and application but not raw`() {
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.anyString(),
                Mockito.eq(HttpMethod.HEAD),
                Mockito.any(),
                Mockito.eq(Void::class.java)
            )
        ).thenReturn(ResponseEntity(HttpStatus.OK))

        val urlCaptor = ArgumentCaptor.forClass(String::class.java)
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java)

        Mockito.`when`(
            restTemplate.exchange(
                urlCaptor.capture(),
                Mockito.eq(HttpMethod.PUT),
                entityCaptor.capture(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.createObjectNode(), HttpStatus.OK))

        service.bootstrapMappings()

        val capturedUrls = urlCaptor.allValues
        val capturedEntities = entityCaptor.allValues

        var candidateFound = false
        var rawFound = false
        var appFound = false

        for (i in capturedUrls.indices) {
            val url = capturedUrls[i]
            val entity = capturedEntities[i]
            val body = entity.body as Map<*, *>
            val properties = body["properties"] as Map<*, *>

            if (url.contains("orcid_info_candidate")) {
                candidateFound = true
                assertFieldExists(properties, "operatorStatus")
                val opStatusField = properties["operatorStatus"] as Map<*, *>
                org.junit.jupiter.api.Assertions.assertEquals("keyword", opStatusField["type"])
            } else if (url.contains("orcid_info_application")) {
                appFound = true
                // A-1: APPLICATION declares operatorStatus in its JSON → it is now pushed
                assertFieldExists(properties, "operatorStatus")
                val opStatusField = properties["operatorStatus"] as Map<*, *>
                org.junit.jupiter.api.Assertions.assertEquals("keyword", opStatusField["type"])
            } else if (url.contains("orcid_info") && !url.contains("candidate") && !url.contains("application")) {
                rawFound = true
                // I-1: RAW JSON does not declare operatorStatus → not pushed
                assertFieldMissing(properties, "operatorStatus")
            }
        }

        org.junit.jupiter.api.Assertions.assertTrue(candidateFound, "Candidate index PUT mapping not called")
        org.junit.jupiter.api.Assertions.assertTrue(rawFound, "Raw index PUT mapping not called")
        org.junit.jupiter.api.Assertions.assertTrue(appFound, "Application index PUT mapping not called")
    }

    @Test
    fun `checkOperatorStatusMapping returns true when all three layers have keyword`() {
        val responseJson = """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "operatorStatus": {
                    "mapping": {
                      "operatorStatus": {
                        "type": "keyword"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val node = mapper.readTree(responseJson)

        for (index in listOf("orcid_info", "orcid_info_candidate", "orcid_info_application")) {
            Mockito.`when`(
                restTemplate.exchange(
                    Mockito.eq("https://es.example.com:9200/$index/_mapping/field/operatorStatus"),
                    Mockito.eq(HttpMethod.GET),
                    Mockito.any(),
                    Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
                )
            ).thenReturn(ResponseEntity(node, HttpStatus.OK))
        }

        val result = service.checkOperatorStatusMapping()
        org.junit.jupiter.api.Assertions.assertTrue(result)
    }

    @Test
    fun `checkOperatorStatusMapping returns false when type is not keyword on one layer`() {
        val keywordJson = """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "operatorStatus": {
                    "mapping": {
                      "operatorStatus": {
                        "type": "keyword"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val textJson = """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "operatorStatus": {
                    "mapping": {
                      "operatorStatus": {
                        "type": "text"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_candidate/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(textJson), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_application/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))

        val result = service.checkOperatorStatusMapping()
        org.junit.jupiter.api.Assertions.assertFalse(result)
    }

    @Test
    fun `checkOperatorStatusMapping returns false when field is missing on one layer`() {
        val keywordJson = """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "operatorStatus": {
                    "mapping": {
                      "operatorStatus": {
                        "type": "keyword"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val missingJson = """
            {
              "orcid_info_candidate": {
                "mappings": {}
              }
            }
        """.trimIndent()

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_candidate/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(missingJson), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_application/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))

        val result = service.checkOperatorStatusMapping()
        org.junit.jupiter.api.Assertions.assertFalse(result)
    }

    @Test
    fun `checkOperatorStatusMapping returns false when HTTP error occurs on one layer`() {
        val keywordJson = """
            {
              "orcid_info_candidate": {
                "mappings": {
                  "operatorStatus": {
                    "mapping": {
                      "operatorStatus": {
                        "type": "keyword"
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_candidate/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenThrow(HttpClientErrorException(HttpStatus.NOT_FOUND))
        Mockito.`when`(
            restTemplate.exchange(
                Mockito.eq("https://es.example.com:9200/orcid_info_application/_mapping/field/operatorStatus"),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(),
                Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(mapper.readTree(keywordJson), HttpStatus.OK))

        val result = service.checkOperatorStatusMapping()
        org.junit.jupiter.api.Assertions.assertFalse(result)
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
