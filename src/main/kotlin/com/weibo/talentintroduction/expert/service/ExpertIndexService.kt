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
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
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
