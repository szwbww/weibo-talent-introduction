package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64

@Service
class ExpertSearchService(
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService
) {
    fun searchExperts(
        size: Int,
        level: ExpertIndexLevel
    ): List<ExpertProfile> {
        require(size in 1..1000) { "size must be between 1 and 1000" }

        val requestBody = mapOf(
            "size" to size,
            "_source" to sourceFields(),
            "query" to mapOf("match_all" to emptyMap<String, Any>()),
            "sort" to sortFields(level)
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        return response.path("hits")
            .path("hits")
            .mapNotNull { hit -> hit.path("_source").takeUnless(JsonNode::isMissingNode) }
            .map(::toExpertProfile)
    }

    fun searchExpertsWithEmail(
        size: Int,
        level: ExpertIndexLevel = ExpertIndexLevel.CANDIDATE
    ): List<ExpertProfile> {
        require(size in 1..1000) { "size must be between 1 and 1000" }

        val requestBody = mapOf(
            "size" to size,
            "_source" to sourceFields(),
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to listOf(
                        mapOf("exists" to mapOf("field" to "email"))
                    )
                )
            ),
            "sort" to sortFields(level)
        )

        val response = restTemplate.exchange(
            "${properties.baseUrl}/${expertIndexService.indexName(level)}/_search",
            HttpMethod.POST,
            HttpEntity(requestBody, headers()),
            JsonNode::class.java
        ).body ?: return emptyList()

        return response.path("hits")
            .path("hits")
            .mapNotNull { hit -> hit.path("_source").takeUnless(JsonNode::isMissingNode) }
            .map(::toExpertProfile)
    }

    private fun toExpertProfile(source: JsonNode): ExpertProfile =
        ExpertProfile(
            orcidId = source.path("orcidId").takeUnless(JsonNode::isMissingNode)?.asText()
                ?: source.path("orcid").takeUnless(JsonNode::isMissingNode)?.asText()
                ?: source.path("id").takeUnless(JsonNode::isMissingNode)?.asText()
                ?: "",
            email = source.path("email").takeUnless(JsonNode::isMissingNode)?.asText(),
            givenNames = source.path("givenNames").takeUnless(JsonNode::isMissingNode)?.asText(),
            familyNames = source.path("familyNames").takeUnless(JsonNode::isMissingNode)?.asText(),
            country = source.path("country").takeUnless(JsonNode::isMissingNode)?.asText(),
            keyword = source.path("keyword").takeUnless(JsonNode::isMissingNode)?.asText(),
            employment = source.path("employment").takeUnless(JsonNode::isMissingNode)?.asText(),
            age = source.path("age").takeUnless(JsonNode::isMissingNode)?.asInt(),
            degree = source.path("degree").takeUnless(JsonNode::isMissingNode)?.asText(),
            nationality = source.path("nationality").takeUnless(JsonNode::isMissingNode)?.asText()
        )

    private fun sourceFields(): List<String> =
        listOf(
            "orcidId",
            "orcid",
            "id",
            "email",
            "givenNames",
            "familyNames",
            "country",
            "keyword",
            "employment",
            "age",
            "degree",
            "nationality"
        )

    private fun sortFields(level: ExpertIndexLevel): List<Map<String, Any>> =
        when (level) {
            ExpertIndexLevel.CANDIDATE ->
                listOf(dateSort("candidateValidatedAt"))

            ExpertIndexLevel.APPLICATION ->
                listOf(dateSort("applicationPromotedAt"), dateSort("lastReplyAt"))

            ExpertIndexLevel.RAW ->
                listOf(mapOf("_doc" to mapOf("order" to "asc")))
        }

    private fun dateSort(field: String): Map<String, Any> =
        mapOf(
            field to mapOf(
                "order" to "desc",
                "missing" to "_last",
                "unmapped_type" to "date"
            )
        )

    private fun headers(): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, basicAuthHeader())
        }

    private fun basicAuthHeader(): String {
        val raw = "${properties.username}:${properties.password}"
        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "Basic $encoded"
    }
}
