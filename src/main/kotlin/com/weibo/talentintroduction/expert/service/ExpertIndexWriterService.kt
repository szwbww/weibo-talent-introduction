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

    fun syncCandidateOperatorStatus(orcidId: String, operatorStatus: String) {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val now = LocalDateTime.now().format(dateFormatter)
        try {
            val body: Map<String, Any>
            if (operatorStatus == "NOT_CONTACTED") {
                body = mapOf(
                    "query" to mapOf("term" to mapOf("orcidId" to orcidId)),
                    "script" to mapOf(
                        "source" to "if (ctx._source.containsKey('operatorStatus')) { ctx._source.remove('operatorStatus'); ctx._source.updatedAt = params.updatedAt; }",
                        "params" to mapOf("updatedAt" to now)
                    )
                )
            } else {
                body = mapOf(
                    "query" to mapOf("term" to mapOf("orcidId" to orcidId)),
                    "script" to mapOf(
                        "source" to "ctx._source.operatorStatus = params.status; ctx._source.updatedAt = params.now",
                        "params" to mapOf("status" to operatorStatus, "now" to now)
                    )
                )
            }
            val updateUrl = "${properties.baseUrl}/$candidateIndex/_update_by_query"
            val resp = restTemplate.exchange(
                updateUrl, HttpMethod.POST,
                HttpEntity(body, headers()),
                JsonNode::class.java
            ).body
            val updated = resp?.path("updated")?.asLong(0) ?: 0
            if (updated == 0L) {
                log.debug("_update_by_query matched 0 docs for orcid={}", orcidId)
            }
        } catch (e: Exception) {
            log.warn("Failed to sync operatorStatus for orcid={}", orcidId, e)
        }
    }

    fun syncCandidateOperatorStatusBatch(updates: List<Pair<String, String>>): BulkSyncResult {
        val overallResult = BulkSyncResult()
        if (updates.isEmpty()) return overallResult
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val now = LocalDateTime.now().format(dateFormatter)
        val batches = updates.chunked(500)
        for (batch in batches) {
            try {
                // Resolve orcidId → _id mapping via terms query
                val orcidIds = batch.map { it.first }.distinct()
                val idMapping = resolveOrcidToDocIds(candidateIndex, orcidIds)

                val bulkBody = batch.joinToString(separator = "\n", postfix = "\n") { (orcidId, operatorStatus) ->
                    val docId = idMapping[orcidId] ?: return@joinToString ""
                    val meta = mapOf("update" to mapOf("_id" to docId, "_index" to candidateIndex))
                    val data = if (operatorStatus == "NOT_CONTACTED") {
                        mapOf(
                            "script" to mapOf(
                                "source" to "if (ctx._source.containsKey('operatorStatus')) { ctx._source.remove('operatorStatus'); ctx._source.updatedAt = params.updatedAt; }",
                                "params" to mapOf("updatedAt" to now)
                            )
                        )
                    } else {
                        mapOf(
                            "doc" to mapOf(
                                "operatorStatus" to operatorStatus,
                                "updatedAt" to now
                            ),
                            "doc_as_upsert" to false
                        )
                    }
                    "${objectMapper.writeValueAsString(meta)}\n${objectMapper.writeValueAsString(data)}"
                }

                // Count orcidIds not found in ES as skipped
                for ((orcidId, _) in batch) {
                    if (!idMapping.containsKey(orcidId)) {
                        overallResult.total++
                        overallResult.skipped++
                    }
                }

                if (bulkBody.isBlank()) {
                    log.debug("No _id mappings found for batch of {} orcidIds", batch.size)
                    continue
                }

                val bulkUrl = "${properties.baseUrl}/_bulk"
                val bulkHeaders = HttpHeaders().apply {
                    contentType = MediaType.valueOf("application/x-ndjson")
                    set(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                }
                val responseNode = restTemplate.exchange(
                    bulkUrl, HttpMethod.POST,
                    HttpEntity(bulkBody, bulkHeaders),
                    JsonNode::class.java
                ).body
                if (responseNode != null) {
                    val items = responseNode.path("items")
                    if (items.isArray) {
                        for (item in items) {
                            val updateNode = item.path("update")
                            val status = updateNode.path("status").asInt(200)
                            val docId = updateNode.path("_id").asText("")
                            overallResult.total++
                            if (status in 200..299) {
                                overallResult.success++
                            } else if (status == 404) {
                                overallResult.skipped++
                            } else {
                                overallResult.failure++
                                val errReason = updateNode.path("error").path("reason").asText("Unknown error")
                                overallResult.errors.add("docId=$docId error: $errReason")
                            }
                        }
                    } else {
                        overallResult.total += batch.size
                        overallResult.failure += batch.size
                        overallResult.errors.add("Bulk response items path is not an array")
                    }
                } else {
                    overallResult.total += batch.size
                    overallResult.failure += batch.size
                    overallResult.errors.add("Empty bulk response from ES")
                }
            } catch (e: Exception) {
                log.warn("Failed to batch sync operatorStatus", e)
                overallResult.total += batch.size
                overallResult.failure += batch.size
                overallResult.errors.add("Bulk request failed: ${e.message}")
            }
        }
        return overallResult
    }

    private fun resolveOrcidToDocIds(index: String, orcidIds: List<String>): Map<String, String> {
        if (orcidIds.isEmpty()) return emptyMap()
        val searchBody = mapOf(
            "size" to orcidIds.size,
            "_source" to listOf("orcidId"),
            "query" to mapOf("terms" to mapOf("orcidId" to orcidIds))
        )
        return try {
            val resp = restTemplate.exchange(
                "${properties.baseUrl}/$index/_search",
                HttpMethod.POST,
                HttpEntity(searchBody, headers()),
                JsonNode::class.java
            ).body
            val hits = resp?.path("hits")?.path("hits") ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            for (hit in hits) {
                val hitOrcid = hit.path("_source").path("orcidId").asText(null) ?: continue
                val docId = hit.path("_id").asText(null) ?: continue
                result[hitOrcid] = docId
            }
            result
        } catch (e: Exception) {
            log.warn("Failed to resolve orcidId → _id mapping: {}", e.message)
            emptyMap()
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

    fun removeFromCandidateIndex(esDocId: String): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val deleteUrl = "${properties.baseUrl}/$candidateIndex/_doc/$esDocId"
        return try {
            restTemplate.exchange(
                deleteUrl,
                HttpMethod.DELETE,
                HttpEntity(null, headers()),
                JsonNode::class.java
            )
            true
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                log.warn("Candidate doc not found for esDocId={}, DELETE returned 404", esDocId)
                false
            } else {
                log.warn("Failed to remove esDocId={} from candidate index (HTTP {}): {}", esDocId, e.statusCode, e.message)
                false
            }
        } catch (e: Exception) {
            log.warn("Failed to remove esDocId={} from candidate index", esDocId, e)
            false
        }
    }

    fun indexToRaw(orcid: String, profile: Map<String, Any?>): Boolean {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val putUrl = "${properties.baseUrl}/$rawIndex/_doc/$orcid"
        return try {
            restTemplate.exchange(
                putUrl,
                HttpMethod.PUT,
                HttpEntity(profile, headers()),
                JsonNode::class.java
            )
            true
        } catch (e: Exception) {
            log.warn("Failed to index orcid={} to raw index", orcid, e)
            false
        }
    }

    fun documentExistsInIndex(indexLevel: ExpertIndexLevel, esDocId: String): Boolean {
        val index = expertIndexService.indexName(indexLevel)
        val headUrl = "${properties.baseUrl}/$index/_doc/$esDocId"
        return try {
            restTemplate.exchange(
                headUrl,
                HttpMethod.HEAD,
                HttpEntity(null, headers()),
                Void::class.java
            )
            true
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) false else throw e
        }
    }

    fun addTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean {
        val index = expertIndexService.indexName(level)
        val script = mapOf(
            "script" to mapOf(
                "source" to "if (ctx._source.tags == null) ctx._source.tags = []; if (!ctx._source.tags.contains(params.tag)) ctx._source.tags.add(params.tag)",
                "params" to mapOf("tag" to tag)
            )
        )
        val url = "${properties.baseUrl}/$index/_update/$docId"
        return try {
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity(script, headers()), JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to add tag '{}' to {} in {}: {}", tag, docId, level, e.message)
            false
        }
    }

    fun removeTag(docId: String, tag: String, level: ExpertIndexLevel): Boolean {
        val index = expertIndexService.indexName(level)
        val script = mapOf(
            "script" to mapOf(
                "source" to "if (ctx._source.tags != null) ctx._source.tags.removeIf(t -> t == params.tag)",
                "params" to mapOf("tag" to tag)
            )
        )
        val url = "${properties.baseUrl}/$index/_update/$docId"
        return try {
            restTemplate.exchange(url, HttpMethod.POST, HttpEntity(script, headers()), JsonNode::class.java)
            true
        } catch (e: Exception) {
            log.warn("Failed to remove tag '{}' from {} in {}: {}", tag, docId, level, e.message)
            false
        }
    }

    fun readRawDocument(docId: String): Map<String, Any?>? {
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val getUrl = "${properties.baseUrl}/$rawIndex/_doc/$docId"
        return try {
            val response = restTemplate.exchange(
                getUrl,
                HttpMethod.GET,
                HttpEntity(null, headers()),
                JsonNode::class.java
            ).body
            val source = response?.path("_source")
            if (source == null || source.isMissingNode) null
            else toStringMap(source)
        } catch (e: Exception) {
            log.warn("Failed to read raw document for esDocId={}", docId, e)
            null
        }
    }

    fun writeCandidateDocument(docId: String, doc: Map<String, Any?>): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val putUrl = "${properties.baseUrl}/$candidateIndex/_doc/$docId"
        return try {
            restTemplate.exchange(
                putUrl,
                HttpMethod.PUT,
                HttpEntity(doc, headers()),
                JsonNode::class.java
            )
            true
        } catch (e: Exception) {
            log.warn("Failed to write candidate document for esDocId={}", docId, e)
            false
        }
    }

    fun demoteToRaw(orcid: String, contact: ExpertContact): Boolean {
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

        val deleteBody = mapOf("query" to mapOf("term" to mapOf("orcidId" to orcid)))
        val deleteCandidateUrl = "${properties.baseUrl}/$candidateIndex/_delete_by_query"
        val deleteApplicationUrl = "${properties.baseUrl}/$applicationIndex/_delete_by_query"

        var deleted = 0L
        try {
            val resp = restTemplate.exchange(
                deleteCandidateUrl, HttpMethod.POST,
                HttpEntity(deleteBody, headers()),
                JsonNode::class.java
            ).body
            val candidateDeleted = resp?.path("deleted")?.asLong(0) ?: 0
            deleted += candidateDeleted
            if (candidateDeleted > 0) {
                log.info("Demoted orcid={}: {} docs deleted from candidate index", orcid, candidateDeleted)
            }
        } catch (e: Exception) {
            log.warn("Failed to delete orcid={} from candidate index via _delete_by_query", orcid, e)
        }

        try {
            val resp = restTemplate.exchange(
                deleteApplicationUrl, HttpMethod.POST,
                HttpEntity(deleteBody, headers()),
                JsonNode::class.java
            ).body
            val appDeleted = resp?.path("deleted")?.asLong(0) ?: 0
            deleted += appDeleted
            if (appDeleted > 0) {
                log.info("Demoted orcid={}: {} docs deleted from application index", orcid, appDeleted)
            }
        } catch (e: Exception) {
            log.warn("Failed to delete orcid={} from application index via _delete_by_query", orcid, e)
        }

        return deleted >= 1
    }

    private fun toStringMap(node: JsonNode): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        node.fields().forEachRemaining { (key, value) ->
            result[key] = toAny(value)
        }
        return result
    }

    private fun toAny(node: JsonNode): Any? = when {
        node.isNull -> null
        node.isBoolean -> node.asBoolean()
        node.isInt -> node.asInt()
        node.isLong -> node.asLong()
        node.isDouble -> node.asDouble()
        node.isTextual -> node.asText()
        node.isArray -> node.map { toAny(it) }
        node.isObject -> toStringMap(node)
        else -> node.asText()
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

data class BulkSyncResult(
    var total: Int = 0,
    var success: Int = 0,
    var failure: Int = 0,
    var skipped: Int = 0,
    val errors: MutableList<String> = mutableListOf()
)
