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
                // I-2: PUT _mapping is atomic per batch — one conflicting field rejects the whole batch.
                // Degrade to per-field PUT so conflict-free fields still land; log per-field result.
                log.warn(
                    "Batch mapping PUT failed for index {} (HTTP {}), degrading to per-field PUT: {}",
                    index, e.statusCode, e.message
                )
                pushFieldsIndividually(index, props["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>())
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

    private fun pushFieldsIndividually(index: String, declaredFields: Map<*, *>) {
        val successful = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        for ((key, value) in declaredFields) {
            val fieldName = key.toString()
            try {
                val singleFieldBody = mapOf("properties" to mapOf(fieldName to value))
                val putMappingUrl = "${properties.baseUrl}/$index/_mapping"
                restTemplate.exchange(
                    putMappingUrl,
                    HttpMethod.PUT,
                    HttpEntity(singleFieldBody, headers()),
                    JsonNode::class.java
                )
                successful.add(fieldName)
            } catch (e: HttpClientErrorException) {
                conflicts.add(fieldName)
                log.warn(
                    "Field mapping conflict for index {} field {} (HTTP {}): {}",
                    index, fieldName, e.statusCode, e.message
                )
            } catch (e: Exception) {
                conflicts.add(fieldName)
                log.warn("Failed to push field mapping for index {} field {}: {}", index, fieldName, e.message)
            }
        }
        log.info(
            "index={} 推送 {} 字段：成功 {}，冲突 {}（{}）",
            index, declaredFields.size, successful.size, conflicts.size, conflicts.joinToString(", ")
        )
    }

    private fun loadMappingProperties(resource: String): Map<String, Any>? {
        // I-1: the es/*.json file is the single declaration source for index mappings.
        // No field-name whitelist lives in Kotlin; every property declared in JSON is pushed.
        return try {
            val bytes = ClassPathResource(resource).inputStream.readBytes()
            val root = objectMapper.readTree(bytes)
            val allProps = root.path("mappings").path("properties")
            val fields = allProps.size()
            if (fields == 0) {
                log.debug("No properties found in mapping resource $resource")
                null
            } else {
                log.info("Prepared {} mapping fields from $resource", fields)
                mapOf("properties" to objectMapper.convertValue(allProps, Map::class.java)!!)
            }
        } catch (e: Exception) {
            log.warn("Failed to load mapping from {}: {}", resource, e.message)
            null
        }
    }

    fun checkOperatorStatusMapping(): Boolean {
        // IP-3: operatorStatus is written to all three layers (RAW/CANDIDATE/APPLICATION),
        // so the mapping precondition must hold on all three.
        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
            val index = indexName(level)
            val url = "${properties.baseUrl}/$index/_mapping/field/operatorStatus"
            try {
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
                if (!isKeyword) return false
            } catch (e: Exception) {
                log.warn("Failed to check operatorStatus mapping for index {}", index, e)
                return false
            }
        }
        return true
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
