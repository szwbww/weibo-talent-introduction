package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.service.*
import com.weibo.talentintroduction.expert.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

class EmailRoutingPersistenceTest {
    private val mapper = jacksonObjectMapper()
    private val http = RestTemplate()
    private val properties = ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app")
    private val indices = ExpertIndexService(properties, http, mapper)
    private val writer = ExpertIndexWriterService(http, properties, indices, mapper,
        mock(ExpertPromotionAuditService::class.java), mock(ExpertContactRepository::class.java))
    private val routing = EmailRouting("uni.edu", "MICROSOFT", "MICROSOFT_MX",
        listOf("0 uni.mail.protection.outlook.com"), "2026-11-06T01:00:00Z")

    @Test fun `CAS writes only routing without upsert identity or admission changes`() {
        val server = MockRestServiceServer.createServer(http)
        server.expect(requestTo("http://es/raw/_update/EMAIL-test?if_seq_no=9&if_primary_term=2&refresh=wait_for"))
            .andExpect(method(HttpMethod.POST)).andExpect { req ->
                val body = mapper.readTree((req as MockClientHttpRequest).bodyAsString)
                assertEquals(mapper.valueToTree<JsonNode>(mapOf("doc" to mapOf("emailRouting" to routing))), body)
            }.andRespond(withSuccess("{\"result\":\"updated\"}", MediaType.APPLICATION_JSON))
        assertTrue(writer.writeEmailRoutingIfVersionMatches(ExpertIndexLevel.RAW, "EMAIL-test",
            ExpertIndexWriterService.DiscoverySnapshot(mapOf("email" to "p@uni.edu"), 9, 2), routing))
        server.verify()
    }

    @Test fun `review profile reader keeps evidence only for current email domain`() {
        val source = mapOf("email" to "p@uni.edu", "emailRouting" to routing)
        assertEquals(routing, writer.discoveryProfile("x", source).emailRouting)
        assertNull(writer.discoveryProfile("x", source + ("email" to "p@elsewhere.edu")).emailRouting)
    }

    @Test fun `all three mapping declarations support domain and status filtering and UTC dates`() {
        for (level in listOf("raw", "candidate", "application")) {
            val node = mapper.readTree(ClassPathResource("es/orcid_info_$level.json").inputStream)
                .path("mappings").path("properties").path("emailRouting").path("properties")
            assertEquals("keyword", node.path("status").path("type").asText())
            assertEquals("keyword", node.path("domain").path("type").asText())
            assertEquals("strict_date_optional_time||epoch_millis", node.path("checkedAt").path("format").asText())
        }
    }

    @Test fun `review filtering sends bound-domain query to ES before paging`() {
        val server = MockRestServiceServer.createServer(http)
        server.expect(requestTo("http://es/raw/_search")).andExpect { req ->
            val body = mapper.readTree((req as MockClientHttpRequest).bodyAsString)
            val filters = body.path("query").path("bool").path("filter")
            assertEquals(mapper.valueToTree<JsonNode>(EmailRouting.filterQuery("MICROSOFT")), filters[0])
            assertTrue(body.path("_source").any { it.asText() == "emailRouting" })
        }.andRespond(withSuccess("{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}", MediaType.APPLICATION_JSON))
        val scan = DiscoveryReviewScanService(mock(DiscoveryReviewRepository::class.java),
            mock(DiscoveryAdmissionPolicy::class.java), writer, http, properties, indices, mapper)
        assertEquals(0L, scan.listPage(ExpertIndexLevel.RAW, "discovered", 0, 20, null, null, null, "MICROSOFT").total)
        server.verify()
    }

    @Test fun `routing evidence is not an admission reservation fact`() {
        val registration = mock(DiscoveryVerificationRegistrationService::class.java, CALLS_REAL_METHODS)
        val profile = ExpertProfile(orcidId = "doc", email = "p@uni.edu", givenNames = "Person", familyNames = "One",
            country = null, keyword = null, employment = null)
        val plain = ReflectionTestUtils.invokeMethod<ExpertProfile>(registration, "factsWithoutCountry", profile)
        val annotated = ReflectionTestUtils.invokeMethod<ExpertProfile>(registration, "factsWithoutCountry", profile.copy(emailRouting = routing))
        assertEquals(plain, annotated)
        assertNotEquals(plain, ReflectionTestUtils.invokeMethod<ExpertProfile>(registration, "factsWithoutCountry", profile.copy(givenNames = "Another")))
    }
}
