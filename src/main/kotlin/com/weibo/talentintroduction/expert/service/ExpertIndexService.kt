package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.annotation.PostConstruct

@Service
class ExpertIndexService(
    private val properties: ElasticsearchProperties,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun indexName(level: ExpertIndexLevel): String =
        when (level) {
            ExpertIndexLevel.RAW -> properties.rawIndexName
            ExpertIndexLevel.CANDIDATE -> properties.candidateIndexName
            ExpertIndexLevel.APPLICATION -> properties.applicationIndexName
        }

    @PostConstruct
    fun bootstrapMappings() {
        try {
            val applicationIndex = indexName(ExpertIndexLevel.APPLICATION)
            val existsUrl = "${properties.baseUrl}/$applicationIndex"
            try {
                restTemplate.exchange(
                    existsUrl,
                    HttpMethod.GET,
                    HttpEntity(null, headers()),
                    JsonNode::class.java
                )
            } catch (e: Exception) {
                val mappingContent = ClassPathResource("es/orcid_info_application.json").inputStream
                    .readAllBytes()
                    .let { String(it, StandardCharsets.UTF_8) }
                val mappingNode = objectMapper.readTree(mappingContent)

                restTemplate.exchange(
                    existsUrl,
                    HttpMethod.PUT,
                    HttpEntity(mappingNode, headers()),
                    JsonNode::class.java
                )
                log.info("Created application index mapping: $applicationIndex")
            }
        } catch (e: Exception) {
            log.warn("Failed to bootstrap ES application index mapping: ${e.message}")
        }

        try {
            updateMappingIfNeeded(ExpertIndexLevel.RAW, "es/orcid_info_raw.json")
            updateMappingIfNeeded(ExpertIndexLevel.CANDIDATE, "es/orcid_info_candidate.json")
            updateMappingIfNeeded(ExpertIndexLevel.APPLICATION, "es/orcid_info_application.json")
        } catch (e: Exception) {
            log.warn("Failed to update ES index mappings: ${e.message}")
        }
    }

    private fun updateMappingIfNeeded(level: ExpertIndexLevel, mappingResource: String) {
        val index = indexName(level)
        try {
            val existsUrl = "${properties.baseUrl}/$index"
            restTemplate.exchange(
                existsUrl,
                HttpMethod.HEAD,
                HttpEntity(null, headers()),
                Void::class.java
            )

            val props = loadMappingProperties(mappingResource) ?: return
            val fields = (props["properties"] as? Map<*, *>)?.size ?: 0
            val putMappingUrl = "${properties.baseUrl}/$index/_mapping"
            try {
                restTemplate.exchange(
                    putMappingUrl,
                    HttpMethod.PUT,
                    HttpEntity(props, headers()),
                    JsonNode::class.java
                )
                log.info("Updated {} mapping fields for index $index", fields)
            } catch (e: HttpClientErrorException) {
                log.warn("Failed to update mapping for index {} (HTTP {}): {} fields", index, e.statusCode, fields)
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                log.info("Index {} does not exist, skipping mapping update", index)
            } else {
                log.warn("HEAD check failed for index {} (HTTP {}): {}", index, e.statusCode, e.message)
            }
        } catch (e: Exception) {
            log.warn("Failed to update mapping for index {}: {}", index, e.message)
        }
    }

    private val phase5NewFields = setOf(
        "hIndex", "citationCount", "lastPublicationYear",
        "researchFields", "institution", "emailSource", "emailVerifiedLevel",
        "dataSource", "externalIds", "discoveredAt", "filterResult", "filterRejectReason",
        "updatedAt", "worksCount",
        "tags", "operatorStatus"
    )

    private fun loadMappingProperties(resource: String): Map<String, Any>? {
        return try {
            val bytes = ClassPathResource(resource).inputStream.readBytes()
            val root = objectMapper.readTree(bytes)
            val allProps = root.path("mappings").path("properties")
            val newProps = objectMapper.createObjectNode()
            allProps.fields().forEachRemaining { (key, value) ->
                if (key in phase5NewFields) {
                    newProps.replace(key, value.deepCopy())
                }
            }
            val fields = newProps.size()
            if (fields == 0) {
                log.debug("No Phase 5 fields found in mapping resource $resource")
                null
            } else {
                log.info("Prepared {} new mapping fields from $resource", fields)
                mapOf("properties" to objectMapper.convertValue(newProps, Map::class.java)!!)
            }
        } catch (e: Exception) {
            log.warn("Failed to load mapping from {}: {}", resource, e.message)
            null
        }
    }

    fun checkCandidateOperatorStatusMapping(): Boolean {
        val candidateIndex = indexName(ExpertIndexLevel.CANDIDATE)
        val url = "${properties.baseUrl}/$candidateIndex/_mapping/field/operatorStatus"
        return try {
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity(null, headers()),
                JsonNode::class.java
            ).body ?: return false
            var isKeyword = false
            response.fields().forEachRemaining { (_, indexNode) ->
                val type = indexNode.path("mappings")
                    .path("operatorStatus")
                    .path("mapping")
                    .path("operatorStatus")
                    .path("type")
                    .asText()
                if (type == "keyword") {
                    isKeyword = true
                }
            }
            isKeyword
        } catch (e: Exception) {
            log.warn("Failed to check candidate operatorStatus mapping", e)
            false
        }
    }

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
