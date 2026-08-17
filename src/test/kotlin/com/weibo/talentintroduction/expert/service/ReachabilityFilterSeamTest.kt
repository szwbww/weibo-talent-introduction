package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.eq
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

/**
 * I-5-3: ES 侧（[ExpertSearchService.reachabilityFilter]）与内存侧（[RecipientScope.matchesExpert]）
 * 可达性口径逐档等价测试。四档 × 五个筛选选项 = 20 组全组合（I-5-3），
 * 另覆盖 I-5-2（UNKNOWN = must_not exists / isNullOrBlank）、I-5-4（空/未指定不追加）。
 */
class ReachabilityFilterSeamTest {

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

    /**
     * 四档专家可达性（null = UNKNOWN，I-2-3 写入侧以字段缺失表达）× 五个筛选选项。
     * 期望值按 [ExpertSearchService.reachabilityFilter] 的档位语义手写（I-5-3）：
     * - 空档位（不筛选，I-5-4）：全部命中
     * - HIGH_ONLY：仅 HIGH
     * - EXCLUDE_BLOCKED：存在 reachability 且非 BLOCKED 两值（= HIGH/LOW）
     * - UNKNOWN_ONLY：字段缺失/空白（isNullOrBlank，I-5-2）
     * - BLOCKED_ONLY：BLOCKED_UNSUBSCRIBED / BLOCKED_BOUNCED 两值之一
     */
    @ParameterizedTest
    @CsvSource(
        // reachability, mode, expected
        "NULL,,true",
        "NULL,HIGH_ONLY,false",
        "NULL,EXCLUDE_BLOCKED,false",
        "NULL,UNKNOWN_ONLY,true",
        "NULL,BLOCKED_ONLY,false",
        "HIGH,,true",
        "HIGH,HIGH_ONLY,true",
        "HIGH,EXCLUDE_BLOCKED,true",
        "HIGH,UNKNOWN_ONLY,false",
        "HIGH,BLOCKED_ONLY,false",
        "LOW,,true",
        "LOW,HIGH_ONLY,false",
        "LOW,EXCLUDE_BLOCKED,true",
        "LOW,UNKNOWN_ONLY,false",
        "LOW,BLOCKED_ONLY,false",
        "BLOCKED_UNSUBSCRIBED,,true",
        "BLOCKED_UNSUBSCRIBED,HIGH_ONLY,false",
        "BLOCKED_UNSUBSCRIBED,EXCLUDE_BLOCKED,false",
        "BLOCKED_UNSUBSCRIBED,UNKNOWN_ONLY,false",
        "BLOCKED_UNSUBSCRIBED,BLOCKED_ONLY,true"
    )
    fun `matchesExpert matches reachabilityFilter semantics for all 4 tiers and 5 modes`(
        reachability: String,
        mode: String?,
        expected: Boolean
    ) {
        val tier = if (reachability == "NULL") null else reachability
        val profile = ExpertProfile(
            orcidId = "0001",
            email = "a@b.com",
            givenNames = null,
            familyNames = null,
            country = null,
            keyword = null,
            employment = null,
            reachability = tier
        )
        val scope = RecipientScope(
            mailType = "INTRODUCTION",
            funnelLevels = setOf("CANDIDATE"),
            tags = emptyList(),
            regions = emptyList(),
            emailDomains = emptyList(),
            discipline = null,
            reachabilityFilter = mode
        )
        assertEquals(expected, scope.matchesExpert(profile), "tier=$tier mode=[$mode]")
    }

    @Test
    fun `reachabilityFilter null or blank returns null`() {
        assertNull(ExpertSearchService.reachabilityFilter(null))
        assertNull(ExpertSearchService.reachabilityFilter(""))
        assertNull(ExpertSearchService.reachabilityFilter("  "))
    }

    @Test
    fun `reachabilityFilter illegal mode fails fast`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            ExpertSearchService.reachabilityFilter("BOGUS")
        }
        assertTrue(ex.message!!.contains("Invalid reachability mode"))
    }

    @Test
    fun `reachabilityFilter builds the documented expressions`() {
        val highOnly = ExpertSearchService.reachabilityFilter("HIGH_ONLY")!!
        assertTrue(highOnly.toString().contains("term"))
        assertTrue(highOnly.toString().contains("HIGH"))
        assertTrue(!highOnly.toString().contains("exists"))

        val excludeBlocked = ExpertSearchService.reachabilityFilter("EXCLUDE_BLOCKED")!!
        assertTrue(excludeBlocked.toString().contains("exists"))
        assertTrue(excludeBlocked.toString().contains("must_not"))
        assertTrue(excludeBlocked.toString().contains("BLOCKED_UNSUBSCRIBED"))
        assertTrue(excludeBlocked.toString().contains("BLOCKED_BOUNCED"))

        val unknownOnly = ExpertSearchService.reachabilityFilter("UNKNOWN_ONLY")!!
        assertTrue(unknownOnly.toString().contains("must_not"))
        assertTrue(unknownOnly.toString().contains("exists"))
        assertTrue(!unknownOnly.toString().contains("HIGH"))

        val blockedOnly = ExpertSearchService.reachabilityFilter("BLOCKED_ONLY")!!
        assertTrue(blockedOnly.toString().contains("terms"))
        assertTrue(blockedOnly.toString().contains("BLOCKED_UNSUBSCRIBED"))
        assertTrue(blockedOnly.toString().contains("BLOCKED_BOUNCED"))
    }

    @Test
    fun `searchExperts without reachability appends no extra filter`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com"}}
                ]
              }
            }
            """.trimIndent()
        )
        val capture = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.searchExperts(
            10, ExpertIndexLevel.CANDIDATE,
            tag = "verified", operatorStatus = "CONTACTED", region = "Europe",
            hasField = listOf("employment"), discipline = "STEM"
        )

        assertEquals(1, result.experts.size)
        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        // tag + operatorStatus + region + hasField + discipline = 5；未传 reachability 不得追加（I-5-4）
        assertEquals(5, filter.size)
        assertTrue(filter.none { it.toString().contains("reachability") })
    }

    @Test
    fun `searchExperts with reachability appends the filter`() {
        val body = mapper.readTree(
            """
            {
              "hits": {
                "total": {"value": 1},
                "hits": [
                  {"_source": {"orcidId": "0001", "email": "a@b.com"}}
                ]
              }
            }
            """.trimIndent()
        )
        val capture = ArgumentCaptor.forClass(HttpEntity::class.java)
        Mockito.`when`(
            restTemplate.exchange(
                eq("https://es.example.com:9200/orcid_info_candidate/_search"),
                eq(HttpMethod.POST),
                capture.capture(),
                eq(com.fasterxml.jackson.databind.JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        service.searchExperts(10, ExpertIndexLevel.CANDIDATE, reachability = "UNKNOWN_ONLY")

        val requestPayload = capture.value.body as Map<*, *>
        val query = requestPayload["query"] as Map<*, *>
        val bool = query["bool"] as Map<*, *>
        val filter = bool["filter"] as List<*>
        assertEquals(1, filter.size)
        assertTrue(filter.single().toString().contains("reachability"))
        assertTrue(filter.single().toString().contains("must_not"))
    }
}
