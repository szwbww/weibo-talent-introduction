package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.RestTemplateConfig
import com.weibo.talentintroduction.discovery.repository.DiscoverySourceCursorRepository
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import com.weibo.talentintroduction.discovery.service.*
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.LocalDateTime
import java.util.concurrent.Executor

class ClassificationWireFormatTest {
    private val mapper = jacksonObjectMapper()
    // Use the production factory and its unmodified JSON converters, not a serialization-only mock.
    private val restTemplate = RestTemplateConfig().restTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val properties = ElasticsearchProperties("http://classification-wire.invalid", "elastic", "secret",
        "wire_raw", "wire_candidate", "wire_application")
    private val indexService = ExpertIndexService(properties, restTemplate, mapper)
    private val search = ExpertSearchService(restTemplate, properties, indexService)
    private val writer = ExpertIndexWriterService(restTemplate, properties, indexService, mapper,
        mock<ExpertPromotionAuditService>(), mock<ExpertContactRepository>())
    private val classification = ExpertClassification(
        ExpertType.ACADEMIC_RND, 7, 95, listOf("RESEARCH_FIXTURE"), listOf("NEGATIVE_FIXTURE"),
        "wire-fixture-v1", "wire-fixture-fingerprint", LocalDateTime.of(2026, 10, 22, 2, 47, 14, 390627000)
    )
    private val scope = RecipientScope("INTRODUCTION", setOf("CANDIDATE"), emptyList(), emptyList(),
        emptyList(), null, expertTypes = listOf("ACADEMIC_RND"))
    private val profile = ExpertProfile(orcidId = "WIRE-ORCID", email = "wire@example.com",
        givenNames = "Wire", familyNames = "Fixture", country = "US", keyword = null,
        employment = null, esDocId = "WIRE-DOC")

    @Test
    fun `enrichment ordinary doc and discovered params doc serialize string dates and replay through scope`() {
        for (discovered in listOf(false, true)) {
            val original = if (discovered) profile.copy(tags = listOf("discovered")) else profile
            val classifier = mock<ExpertClassificationService>()
            Mockito.doReturn(classification).`when`(classifier).classify(
                Mockito.any(ExpertProfile::class.java) ?: original)
            val discovery = discovery(classifier)
            val wires = mutableListOf<JsonNode>()
            for (level in ExpertIndexLevel.values()) {
                val index = indexService.indexName(level)
                server.expect(requestTo("${properties.baseUrl}/$index/_doc/WIRE-DOC"))
                    .andExpect(method(HttpMethod.HEAD))
                    .andRespond(withStatus(if (level == ExpertIndexLevel.APPLICATION) HttpStatus.NOT_FOUND else HttpStatus.OK))
                if (level == ExpertIndexLevel.APPLICATION) continue
                server.expect(requestTo("${properties.baseUrl}/$index/_update/WIRE-DOC"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect { request ->
                        val body = mapper.readTree((request as MockClientHttpRequest).bodyAsString)
                        val doc = if (discovered) body.path("script").path("params").path("doc") else body.path("doc")
                        assertFalse(body.has("upsert"))
                        assertEquals(writer.classificationNode(classification), doc.path("expertClassification"))
                        assertTrue(doc.path("expertClassification").path("classifiedAt").isTextual)
                        assertEquals("2026-10-22 02:47:14", doc.path("expertClassification").path("classifiedAt").asText())
                        assertEquals(24, doc.path("hIndex").asInt())
                        assertFalse(doc.has("citationCount"), "null enrichment facts must not erase persisted facts")
                        for (key in listOf("email", "givenNames", "familyNames", "operatorStatus", "updatedAt")) {
                            assertFalse(doc.has(key), key)
                        }
                        if (discovered) {
                            val script = body.path("script").path("source").asText()
                            for (field in listOf("identityVerification", "email", "givenNames", "familyNames", "externalIds")) {
                                assertTrue(script.contains("ctx._source.$field !="), field)
                            }
                            assertTrue(script.contains("ctx.op = 'none'"))
                        }
                        wires.add(doc.path("expertClassification").deepCopy<JsonNode>())
                    }.andRespond(withSuccess("{\"result\":\"updated\"}", MediaType.APPLICATION_JSON))
            }
            // Exercise the real enrichment write seam; upstream discovery/provider traffic is deliberately absent.
            val result = ReflectionTestUtils.invokeMethod<LayerUpdateResult>(discovery,
                "updateExpertAcademicFields", original, AuthorEnrichment(24, null, null))!!
            assertEquals(LayerUpdateStatus.UPDATED, result.raw)
            assertEquals(LayerUpdateStatus.UPDATED, result.candidate)
            assertEquals(LayerUpdateStatus.ABSENT, result.application)
            Mockito.verify(classifier).classify(original.copy(hIndex = 24))
            assertEquals(2, wires.size)
            server.verify()
            server.reset()
            for (level in ExpertIndexLevel.values()) {
                val replayed = replay(wires.first(), level)
                assertEquals(classification.copy(classifiedAt = classification.classifiedAt.withNano(0)), replayed.expertClassification)
                assertTrue(scope.matchesExpertType(replayed))
            }
        }
    }

    @Test
    fun `historical array survives layer replay without rewriting source and malformed classifications fail closed`() {
        val historical = writer.classificationNode(classification).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        historical.set<JsonNode>("classifiedAt", mapper.readTree("[2026,10,22,2,47,14,390627000]"))
        val original = historical.deepCopy()
        for (level in ExpertIndexLevel.values()) {
            val restored = replay(historical, level)
            assertEquals(classification, restored.expertClassification)
            assertTrue(scope.matchesExpertType(restored))
            assertEquals(original, historical, "read replay must not normalize or rewrite historical source")
            for (broken in listOf(
                historical.deepCopy().put("type", "INVALID_TYPE"),
                historical.deepCopy().apply { remove("positiveEvidence") },
                historical.deepCopy().apply {
                    set<JsonNode>("negativeEvidence", mapper.readTree("[1]"))
                },
                historical.deepCopy().apply {
                    set<JsonNode>("classifiedAt", mapper.readTree("[2026,13,22,2,47]"))
                }
            )) {
                val rejected = replay(broken, level)
                assertNull(rejected.expertClassification)
                assertFalse(scope.matchesExpertType(rejected))
            }
        }
    }

    private fun replay(node: JsonNode, level: ExpertIndexLevel): ExpertProfile {
        val source = mapper.createObjectNode().put("orcidId", profile.orcidId).put("email", profile.email)
        source.set<JsonNode>("expertClassification", node)
        val response = mapper.createObjectNode()
        response.putObject("hits").apply {
            putObject("total").put("value", 1)
            putArray("hits").addObject().set<JsonNode>("_source", source)
        }
        server.expect(requestTo("${properties.baseUrl}/${indexService.indexName(level)}/_search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON))
        val expert = search.searchExpertsWithEmail(1, level).experts.single()
        server.verify()
        server.reset()
        return expert
    }

    private fun discovery(classifier: ExpertClassificationService) = ExpertDiscoveryService(
        europePmc = mock(), openAlexProvider = mock(), crossrefProvider = mock(), arxivProvider = mock(),
        pmcOaProvider = mock(), orcidProvider = mock(), coreProvider = mock(), emailValidationService = mock(),
        eligibilityService = mock(), expertIndexWriterService = writer, expertIndexService = indexService,
        revalidationService = mock(), expertSearchService = search, expertClassificationService = classifier,
        restTemplate = restTemplate, esProperties = properties, discoveryProperties = ExpertDiscoveryProperties(),
        openAlexProperties = OpenAlexProperties(), objectMapper = mapper, progressStore = mock<TaskProgressStore>(),
        cursorRepository = mock<DiscoverySourceCursorRepository>(), enrichmentJobService = mock(),
        enrichmentJobRepository = mock<ExpertAcademicEnrichmentJobRepository>(),
        discoveryFetchExecutor = Executor { it.run() }, europePmcProperties = EuropePmcProperties()
    )

    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)
}
