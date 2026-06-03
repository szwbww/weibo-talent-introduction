package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.http.HttpStatus
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
    private val objectMapper: ObjectMapper,
    private val expertApplicationPromotionRepository: ExpertApplicationPromotionRepository,
    private val expertContactRepository: ExpertContactRepository
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

    fun promoteToApplication(
        orcid: String,
        contact: ExpertContact,
        firstReplyAt: Instant,
        sourceInboundId: Long? = null,
        triggeredBy: String = TriggeredBy.SYSTEM,
        operatorName: String? = null
    ): Boolean {
        val audit = createPromotionAudit(contact, orcid, sourceInboundId, triggeredBy, operatorName)
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
            markPromotionFailed(audit, e.message ?: "Failed to read candidate index")
            return false
        }

        val source = candidateResponse?.path("_source") ?: run {
            markPromotionFailed(audit, "Candidate index document has no _source")
            return false
        }

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
            put("campaignId", contact.campaignId)
            put("promotionSource", "INBOUND_REPLY")
            put("applicationPromotedAt", now)
            put("updatedAt", now)
        }

        val putUrl = "${properties.baseUrl}/$applicationIndex/_doc/$orcid"
        return try {
            restTemplate.exchange(
                putUrl,
                HttpMethod.PUT,
                HttpEntity(toStringMap(doc), headers()),
                JsonNode::class.java
            )
            markPromotionSuccess(audit)
            true
        } catch (e: Exception) {
            markPromotionFailed(audit, e.message ?: "Failed to write application index")
            throw e
        }
    }

    fun retryFailedPromotion(promotionId: Long): ExpertApplicationPromotion {
        val promotion = expertApplicationPromotionRepository.findById(promotionId)
            .orElseThrow { error("Promotion audit not found: $promotionId") }
        require(promotion.promotionStatus == "FAILED") { "Only FAILED promotions can be retried" }
        val contact = expertContactRepository.findById(promotion.expertContactId)
            .orElseThrow { error("Expert contact not found: ${promotion.expertContactId}") }
        val firstReplyAt = contact.firstReplyAt ?: LocalDateTime.now()
        promoteToApplication(
            orcid = promotion.orcidId,
            contact = contact,
            firstReplyAt = firstReplyAt.toInstant(ZoneId.systemDefault().rules.getOffset(firstReplyAt)),
            sourceInboundId = promotion.sourceInboundId,
            triggeredBy = promotion.triggeredBy,
            operatorName = promotion.operatorName
        )
        return expertApplicationPromotionRepository
            .findFirstByExpertContactIdAndPromotionStatusOrderByCreatedAtDesc(
                promotion.expertContactId,
                "SUCCESS"
            )
            ?: expertApplicationPromotionRepository
                .findFirstByExpertContactIdAndPromotionStatusOrderByCreatedAtDesc(
                    promotion.expertContactId,
                    "FAILED"
                )
            ?: promotion
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

    fun promoteToCandidate(orcid: String, contact: ExpertContact): Boolean {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)

        val getUrl = "${properties.baseUrl}/$rawIndex/_doc/$orcid"
        val rawResponse = try {
            restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                HttpEntity(null, headers()),
                JsonNode::class.java
            ).body
        } catch (e: Exception) {
            log.warn("Failed to get raw expert profile from {} for orcid={}", rawIndex, orcid, e)
            return false
        }

        val source = rawResponse?.path("_source") ?: return false
        val now = LocalDateTime.now().format(dateFormatter)

        val doc = objectMapper.createObjectNode().apply {
            source.fields().forEachRemaining { (key, value) ->
                val copy: JsonNode = value.deepCopy()
                replace(key, copy)
            }
            put("candidateValidatedAt", now)
            put("updatedAt", now)
        }

        val putUrl = "${properties.baseUrl}/$candidateIndex/_doc/$orcid"
        try {
            restTemplate.exchange(
                putUrl,
                HttpMethod.PUT,
                HttpEntity(toStringMap(doc), headers()),
                JsonNode::class.java
            )
            return true
        } catch (e: Exception) {
            log.warn("Failed to PUT candidate profile to {} for orcid={}", candidateIndex, orcid, e)
            return false
        }
    }

    fun demoteToRaw(orcid: String, contact: ExpertContact): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

        val deleteCandidateUrl = "${properties.baseUrl}/$candidateIndex/_doc/$orcid"
        val deleteApplicationUrl = "${properties.baseUrl}/$applicationIndex/_doc/$orcid"

        try {
            try {
                restTemplate.exchange(
                    deleteCandidateUrl,
                    HttpMethod.DELETE,
                    HttpEntity(null, headers()),
                    JsonNode::class.java
                )
            } catch (e: HttpClientErrorException) {
                if (e.statusCode != HttpStatus.NOT_FOUND) throw e
            }

            try {
                restTemplate.exchange(
                    deleteApplicationUrl,
                    HttpMethod.DELETE,
                    HttpEntity(null, headers()),
                    JsonNode::class.java
                )
            } catch (e: HttpClientErrorException) {
                if (e.statusCode != HttpStatus.NOT_FOUND) throw e
            }

            return true
        } catch (e: Exception) {
            log.warn("Failed to demote orcid={} from ES indices", orcid, e)
            throw e
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

    private fun createPromotionAudit(
        contact: ExpertContact,
        orcid: String,
        sourceInboundId: Long?,
        triggeredBy: String,
        operatorName: String?
    ): ExpertApplicationPromotion? {
        val contactId = contact.id ?: return null
        val now = LocalDateTime.now()
        return expertApplicationPromotionRepository.save(
            ExpertApplicationPromotion(
                expertContactId = contactId,
                orcidId = orcid,
                sourceInboundId = sourceInboundId,
                triggeredBy = triggeredBy,
                promotionStatus = "PENDING",
                operatorName = operatorName,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun markPromotionSuccess(audit: ExpertApplicationPromotion?) {
        if (audit == null) return
        expertApplicationPromotionRepository.save(
            audit.copy(promotionStatus = "SUCCESS", updatedAt = LocalDateTime.now())
        )
    }

    private fun markPromotionFailed(audit: ExpertApplicationPromotion?, message: String) {
        if (audit == null) return
        expertApplicationPromotionRepository.save(
            audit.copy(
                promotionStatus = "FAILED",
                errorMessage = message.take(2000),
                updatedAt = LocalDateTime.now()
            )
        )
    }
}
