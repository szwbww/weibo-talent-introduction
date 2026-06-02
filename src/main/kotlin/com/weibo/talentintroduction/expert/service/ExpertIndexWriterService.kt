package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

@Service
class ExpertIndexWriterService(
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(ExpertIndexWriterService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun markApplicationClosed(contact: ExpertContact) {
        if (!contact.applicationIndexed) return
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)
        val orcid = contact.orcidId
        val now = LocalDateTime.now().format(dateFormatter)
        try {
            val updateDoc = mapOf(
                "doc" to mapOf(
                    "applicationStatus" to "CLOSED",
                    "currentConversationStatus" to contact.currentStatus,
                    "updatedAt" to now
                )
            )
            val updateUrl = "${properties.baseUrl}/$applicationIndex/_update/$orcid"
            restTemplate.exchange(
                updateUrl,
                HttpMethod.POST,
                HttpEntity(updateDoc, headers()),
                JsonNode::class.java
            )
        } catch (e: Exception) {
            log.warn("Failed to mark application closed for contact {} (orcid={})", contact.id, orcid, e)
        }
    }

    fun promoteToApplication(orcid: String, contact: ExpertContact, firstReplyAt: Instant): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

        val getUrl = "${properties.baseUrl}/$candidateIndex/_doc/$orcid"
        val candidateResponse = try {
            restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                HttpEntity(null, headers()),
                JsonNode::class.java
            ).body
        } catch (e: Exception) {
            return false
        }

        val source = candidateResponse?.path("_source") ?: return false

        val now = LocalDateTime.now().format(dateFormatter)
        val firstReplyStr = firstReplyAt
            .let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()).format(dateFormatter) }

        val doc = objectMapper.createObjectNode().apply {
            source.fields().forEachRemaining { (key, value) ->
                val copy: JsonNode = value.deepCopy()
                replace(key, copy)
            }
            put("currentConversationStatus", contact.currentStatus)
            put("applicationStatus", "ACTIVE")
            put("autoReplyEnabled", contact.autoReplyEnabled)
            put("firstReplyAt", firstReplyStr)
            put("lastReplyAt", firstReplyStr)
            put("expertContactId", contact.id ?: -1)
            put("campaignId", contact.campaignId ?: -1)
            put("promotionSource", "INBOUND_REPLY")
            put("applicationPromotedAt", now)
            put("updatedAt", now)
        }

        val putUrl = "${properties.baseUrl}/$applicationIndex/_doc/$orcid"
        restTemplate.exchange(
            putUrl,
            HttpMethod.PUT,
            HttpEntity(toStringMap(doc), headers()),
            JsonNode::class.java
        )
        return true
    }

    fun syncApplicationStatus(contact: ExpertContact, intent: String? = null) {
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)
        val orcid = contact.orcidId
        val now = LocalDateTime.now().format(dateFormatter)

        try {
            val updates = mutableMapOf(
                "currentConversationStatus" to contact.currentStatus,
                "autoReplyEnabled" to contact.autoReplyEnabled,
                "updatedAt" to now
            )
            if (intent != null) {
                updates["latestInboundIntent"] = intent
            }
            if (contact.lastReplyAt != null) {
                updates["lastReplyAt"] = contact.lastReplyAt.format(dateFormatter)
            }

            val updateDoc = mapOf("doc" to updates)

            val updateUrl = "${properties.baseUrl}/$applicationIndex/_update/$orcid"
            restTemplate.exchange(
                updateUrl,
                HttpMethod.POST,
                HttpEntity(updateDoc, headers()),
                JsonNode::class.java
            )
        } catch (e: Exception) {
            log.warn("Failed to sync application status for contact {} (orcid={})", contact.id, orcid, e)
        }
    }

    private fun toStringMap(node: JsonNode): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        node.fields().forEachRemaining { (key, value) ->
            result[key] = when {
                value.isTextual -> value.asText()
                value.isBoolean -> value.asBoolean()
                value.isInt -> value.asInt()
                value.isLong -> value.asLong()
                value.isDouble -> value.asDouble()
                value.isNull -> null
                value.isArray -> value.map { it.asText() }
                value.isObject -> toStringMap(value)
                else -> value.asText()
            }
        }
        return result
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
