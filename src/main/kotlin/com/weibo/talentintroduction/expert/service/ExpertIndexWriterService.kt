package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertApplicationPromotion
import com.weibo.talentintroduction.expert.domain.ExpertClassification
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.repository.ExpertApplicationPromotionRepository
import com.weibo.talentintroduction.mail.domain.TriggeredBy
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
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

    companion object {
        /** 分类 bulk 单批上限（I2-2/Task 1）；实际批次大小由 backfill request.batchSize 控制。 */
        const val CLASSIFICATION_BULK_BATCH_CAP = 1000

        /** 失败样本保留上限：统计全部失败，但样本最多保留 100 条（I2-4）。 */
        const val CLASSIFICATION_FAILURE_SAMPLE_CAP = 100
    }

    fun markApplicationClosed(contact: ExpertContact) {
        if (!contact.applicationIndexed) return
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)
        val orcid = ExpertIdNormalizer.normalize(contact.orcidId)
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

    fun syncOperatorStatus(orcidId: String, operatorStatus: String): SingleSyncResult {
        // IP-3: operatorStatus must reach all three layers; follow the
        // ExpertDiscoveryService.updateExpertAcademicFields pattern — skip layers whose
        // document does not exist (HEAD check), then _update by doc id.
        val normalizedOrcidId = ExpertIdNormalizer.normalize(orcidId)
        val now = LocalDateTime.now().format(dateFormatter)
        var updated = 0L
        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
            if (!documentExistsInIndex(level, normalizedOrcidId)) continue
            try {
                val index = expertIndexService.indexName(level)
                val body: Map<String, Any> = if (operatorStatus == "NOT_CONTACTED") {
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
                        )
                    )
                }
                val updateUrl = "${properties.baseUrl}/$index/_update/$normalizedOrcidId"
                val resp = restTemplate.exchange(
                    updateUrl, HttpMethod.POST,
                    HttpEntity(body, headers()),
                    JsonNode::class.java
                ).body
                val result = resp?.path("result")?.asText("")
                if (result == "updated" || result == "noop") {
                    updated++
                }
            } catch (e: Exception) {
                log.warn("Failed to sync operatorStatus for orcid={} in index {}: {}", normalizedOrcidId, level, e.message, e)
                return SingleSyncResult(matched = updated, ok = false, error = e.message)
            }
        }
        if (updated == 0L) {
            log.warn("syncOperatorStatus matched 0 docs across raw/candidate/application for orcid={}", normalizedOrcidId)
        }
        return SingleSyncResult(matched = updated, ok = true)
    }

    fun syncOperatorStatusBatch(updates: List<Pair<String, String>>): BulkSyncResult {
        // IP-3: sync operatorStatus on all three layers (RAW/CANDIDATE/APPLICATION);
        // per layer resolve orcidId → _id and bulk-update, aggregating results.
        val overallResult = BulkSyncResult()
        if (updates.isEmpty()) return overallResult
        val now = LocalDateTime.now().format(dateFormatter)
        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
            val index = expertIndexService.indexName(level)
            val batches = updates.chunked(500)
            for (batch in batches) {
                try {
                    // Resolve orcidId → _id mapping via terms query
                    val orcidIds = batch.map { ExpertIdNormalizer.normalize(it.first) }.distinct()
                    val idMapping = resolveOrcidToDocIds(index, orcidIds)

                    val bulkBody = batch.joinToString(separator = "\n", postfix = "\n") { (orcidId, operatorStatus) ->
                        val normalizedOrcidId = ExpertIdNormalizer.normalize(orcidId)
                        val docId = idMapping[normalizedOrcidId] ?: return@joinToString ""
                        val meta = mapOf("update" to mapOf("_id" to docId, "_index" to index))
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

                    // Count orcidIds not found in this layer as skipped
                    for ((orcidId, _) in batch) {
                        if (!idMapping.containsKey(ExpertIdNormalizer.normalize(orcidId))) {
                            overallResult.total++
                            overallResult.skipped++
                        }
                    }

                    if (bulkBody.isBlank()) {
                        log.debug("No _id mappings found for batch of {} orcidIds in index {}", batch.size, index)
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
                    log.warn("Failed to batch sync operatorStatus for index {}: {}", index, e.message, e)
                    overallResult.total += batch.size
                    overallResult.failure += batch.size
                    overallResult.errors.add("Bulk request failed: ${e.message}")
                }
            }
        }
        return overallResult
    }

    /**
     * 分类批量局部更新的唯一写入口（I2-2，M-4）。
     *
     * 以调用方传入的 [level] 解析唯一目标 index（不跨三层循环，不做 ORCID→docId 二次查询）；
     * 每个 item 按 [ClassificationBulkItem.esDocId] 更新原索引 `_id`，NDJSON data 逐字结构为
     * `{"doc":{"expertClassification":{...}},"doc_as_upsert":false}` —— 只写分类对象，
     * 不写根级 `updatedAt`、不自动创建缺失文档（`doc_as_upsert=false`）、不使用 `_update_by_query`。
     *
     * 返回每项 updated/noop/failure 状态；统计全部失败但只保留最多 100 条失败样本。
     * 整批请求异常（ES 不可达 / bulk 被拒）时本批全部计 failure 并停止后续批次，
     * 通过 [ClassificationBulkResult.wholesaleError] 暴露，由调用方决定是否中止整个任务。
     */
    fun bulkUpdateExpertClassifications(
        level: ExpertIndexLevel,
        updates: List<ClassificationBulkItem>
    ): ClassificationBulkResult {
        val index = expertIndexService.indexName(level)
        val results = mutableListOf<ClassificationBulkItemResult>()
        val failureSamples = mutableListOf<String>()
        var anyMapperError = false
        var wholesaleError: String? = null

        for (batch in updates.chunked(CLASSIFICATION_BULK_BATCH_CAP)) {
            val bulkBody = batch.joinToString(separator = "\n", postfix = "\n") { item ->
                val meta = mapOf("update" to mapOf("_id" to item.esDocId, "_index" to index))
                val data = mapOf(
                    "doc" to mapOf("expertClassification" to classificationNode(item.classification)),
                    "doc_as_upsert" to false
                )
                "${objectMapper.writeValueAsString(meta)}\n${objectMapper.writeValueAsString(data)}"
            }
            try {
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
                if (responseNode == null) {
                    batch.forEach { item ->
                        recordClassificationFailure(results, failureSamples, item.esDocId, "Empty bulk response from ES")
                    }
                    wholesaleError = "Empty bulk response from ES for index $index"
                    break
                }
                val items = responseNode.path("items")
                if (!items.isArray) {
                    batch.forEach { item ->
                        recordClassificationFailure(results, failureSamples, item.esDocId, "Bulk response items path is not an array")
                    }
                    wholesaleError = "Bulk response items path is not an array for index $index"
                    break
                }
                for ((item, itemNode) in batch.zip(items)) {
                    val updateNode = itemNode.path("update")
                    val status = updateNode.path("status").asInt(200)
                    val resultField = updateNode.path("result").asText("")
                    when {
                        status in 200..299 && resultField == "noop" ->
                            results += ClassificationBulkItemResult(item.esDocId, ClassificationBulkItemStatus.NOOP)

                        status in 200..299 ->
                            results += ClassificationBulkItemResult(item.esDocId, ClassificationBulkItemStatus.UPDATED)

                        else -> {
                            val errorType = updateNode.path("error").path("type").asText("")
                            val errReason = updateNode.path("error").path("reason").asText("Unknown error")
                            if (errorType == "mapper_parsing_exception") {
                                anyMapperError = true
                            }
                            recordClassificationFailure(results, failureSamples, item.esDocId, errReason)
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to bulk update expert classifications for index {}: {}", index, e.message, e)
                batch.forEach { item ->
                    recordClassificationFailure(results, failureSamples, item.esDocId, "Bulk request failed: ${e.message}")
                }
                wholesaleError = "Bulk request failed for index $index: ${e.message}"
                break
            }
        }

        val failures = results.count { it.status == ClassificationBulkItemStatus.FAILED }
        return ClassificationBulkResult(
            items = results,
            failureSamples = failureSamples,
            allFailedWithMapperError = results.isNotEmpty() && failures == results.size && anyMapperError,
            wholesaleError = wholesaleError
        )
    }

    /**
     * 前置 mapping 检查（I2-2 立即 FAILED 前提）：确认 [level] 索引已声明
     * `expertClassification.type`（keyword）。缺失/异常 → false，调用方应在扫描前停止。
     */
    fun checkExpertClassificationMapping(level: ExpertIndexLevel): Boolean {
        val index = expertIndexService.indexName(level)
        val url = "${properties.baseUrl}/$index/_mapping"
        return try {
            val response = restTemplate.exchange(
                url, HttpMethod.GET,
                HttpEntity(null, headers()),
                JsonNode::class.java
            ).body ?: return false
            var found = false
            response.fields().forEachRemaining { (_, indexNode) ->
                val type = indexNode.path("mappings")
                    .path("properties")
                    .path("expertClassification")
                    .path("properties")
                    .path("type")
                    .path("type")
                    .asText()
                if (type == "keyword") {
                    found = true
                }
            }
            found
        } catch (e: Exception) {
            log.warn("Failed to check expertClassification mapping for index {}", index, e)
            false
        }
    }

    private fun classificationNode(classification: ExpertClassification): JsonNode =
        objectMapper.createObjectNode().apply {
            put("type", classification.type.name)
            put("sendable", classification.sendable)
            put("productionScore", classification.productionScore)
            put("researchScore", classification.researchScore)
            putArray("positiveEvidence").apply { classification.positiveEvidence.forEach { add(it) } }
            putArray("negativeEvidence").apply { classification.negativeEvidence.forEach { add(it) } }
            put("version", classification.version)
            put("sourceFingerprint", classification.sourceFingerprint)
            put("classifiedAt", classification.classifiedAt.format(dateFormatter))
        }

    private fun recordClassificationFailure(
        results: MutableList<ClassificationBulkItemResult>,
        failureSamples: MutableList<String>,
        esDocId: String,
        reason: String
    ) {
        results += ClassificationBulkItemResult(esDocId, ClassificationBulkItemStatus.FAILED, reason)
        if (failureSamples.size < CLASSIFICATION_FAILURE_SAMPLE_CAP) {
            failureSamples.add("docId=$esDocId error: $reason")
        }
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
        val normalizedOrcid = ExpertIdNormalizer.normalize(orcid)
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

        val getUrl = "${properties.baseUrl}/$candidateIndex/_doc/$normalizedOrcid"
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

        val putUrl = "${properties.baseUrl}/$applicationIndex/_doc/$normalizedOrcid"
        return try {
            restTemplate.exchange(
                putUrl,
                HttpMethod.PUT,
                HttpEntity(toStringMap(doc), headers()),
                JsonNode::class.java
            )
            val removedFromCandidate = removeFromCandidateIndex(normalizedOrcid)
            if (!removedFromCandidate) {
                markPromotionFailed(audit, "Failed to remove candidate index document after application promotion")
                return false
            }
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
        val orcid = ExpertIdNormalizer.normalize(contact.orcidId)
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
        val normalizedOrcid = ExpertIdNormalizer.normalize(orcid)
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)

        val getUrl = "${properties.baseUrl}/$rawIndex/_doc/$normalizedOrcid"
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

        val putUrl = "${properties.baseUrl}/$candidateIndex/_doc/$normalizedOrcid"
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
        val normalizedOrcid = ExpertIdNormalizer.normalize(orcid)
        val rawIndex = expertIndexService.indexName(ExpertIndexLevel.RAW)
        val putUrl = "${properties.baseUrl}/$rawIndex/_doc/$normalizedOrcid"
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
        val normalizedOrcid = ExpertIdNormalizer.normalize(orcid)
        val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
        val applicationIndex = expertIndexService.indexName(ExpertIndexLevel.APPLICATION)

        val deleteBody = mapOf("query" to mapOf("term" to mapOf("orcidId" to normalizedOrcid)))
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

data class SingleSyncResult(
    val matched: Long,
    val ok: Boolean,
    val error: String? = null
)

data class BulkSyncResult(
    var total: Int = 0,
    var success: Int = 0,
    var failure: Int = 0,
    var skipped: Int = 0,
    val errors: MutableList<String> = mutableListOf()
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = success
    override val taskFailureCount: Int get() = failure
    override val taskFinalStatus: String?
        get() = when {
            failure > 0 && success > 0 -> "PARTIAL_SUCCESS"
            failure > 0 -> "FAILED"
            else -> "SUCCESS"
        }
}

/** 分类 bulk 更新单项（I2-2）：只含 esDocId + 分类对象。 */
data class ClassificationBulkItem(
    val esDocId: String,
    val classification: ExpertClassification
)

enum class ClassificationBulkItemStatus { UPDATED, NOOP, FAILED }

/** 分类 bulk 单项结果（I2-4）：保留每项 updated/noop/failure 状态。 */
data class ClassificationBulkItemResult(
    val esDocId: String,
    val status: ClassificationBulkItemStatus,
    val error: String? = null
)

/**
 * 分类 bulk 汇总结果（I2-4/I2-6）。
 * 统计全部失败（[failure]），样本 [failureSamples] 最多 100 条；
 * [allFailedWithMapperError] 供调用方在首批全失败且均为 mapper_parsing_exception 时立即 FAILED；
 * [wholesaleError] 非空表示整批请求异常（ES 不可达/被拒），调用方应停止整个扫描任务。
 */
data class ClassificationBulkResult(
    val items: List<ClassificationBulkItemResult>,
    val failureSamples: List<String>,
    val allFailedWithMapperError: Boolean,
    val wholesaleError: String? = null
) {
    val updated: Int get() = items.count { it.status == ClassificationBulkItemStatus.UPDATED }
    val noop: Int get() = items.count { it.status == ClassificationBulkItemStatus.NOOP }
    val failure: Int get() = items.count { it.status == ClassificationBulkItemStatus.FAILED }
}
