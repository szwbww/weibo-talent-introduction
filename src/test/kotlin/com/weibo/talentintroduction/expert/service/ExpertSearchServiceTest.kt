package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito

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
}
