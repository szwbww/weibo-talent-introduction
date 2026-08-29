package com.weibo.talentintroduction.llm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.annotation.PostConstruct

enum class UnsupportedAnswerIndexStatus { CANDIDATE, ACTIVE }
enum class UnsupportedAnswerIndexSourceMode { TRAINING, LIVE }
enum class UnsupportedAnswerIndexSourceType { TRAINING_MAIL, LIVE_INBOUND }
enum class UnsupportedAnswerIndexQualificationType { TRAINING_EVALUATION, LIVE_SEND }
enum class UnsupportedAnswerIndexCreateOutcome { CREATED, ALREADY_EXISTS, REJECTED, FAILED }

data class UnsupportedAnswerIndexDocument(
    val schemaVersion: String = UnsupportedAnswerIndexService.SCHEMA_VERSION,
    val status: UnsupportedAnswerIndexStatus,
    val sourceMode: UnsupportedAnswerIndexSourceMode,
    val sourceType: UnsupportedAnswerIndexSourceType,
    val sourceId: Long,
    val sourceVersion: String,
    val expertContactId: Long,
    val campaignId: Long,
    val requestKey: String,
    val requestIndex: Int,
    val requestText: String,
    val handling: String,
    val operatorInstruction: String,
    val operatorInstructionHash: String,
    val versionId: String,
    val answerText: String,
    val answerHash: String,
    val model: String,
    val generationKind: String,
    val qualificationType: UnsupportedAnswerIndexQualificationType,
    val qualificationId: String,
    val approvedBy: String,
    val createdAt: Instant,
    // c6 (16-unsupported-index T-2): 归档接缝字段（I-2/IP-4）。
    // - topic：keyword 精确过滤键（I-3），写入侧从版本 claims 的 intentKey 主段派生；
    // - finalParagraphText：步骤 03 权威最终段落文本（Repair R-1：来自 assemble 响应
    //   finalParagraphByRequestKey 的逐条确定性映射），与 item answerText 分开存放
    //   （plan 12 IP-4），绝不回退为 answerText；映射缺失/歧义时该条 fail closed；
    // - editedByOperator：线上侧「照常归档并置 editedByOperator = true」（I-4 / T-2）。
    val topic: String = "",
    val finalParagraphText: String = "",
    val editedByOperator: Boolean = false
)

data class UnsupportedAnswerIndexCreateResult(
    val outcome: UnsupportedAnswerIndexCreateOutcome,
    val errorCode: String? = null
)

enum class UnsupportedAnswerArchiveStatus { NOT_APPLICABLE, SAVED, PARTIAL, FAILED }

data class UnsupportedAnswerIndexArchiveResult(
    val status: UnsupportedAnswerArchiveStatus = UnsupportedAnswerArchiveStatus.NOT_APPLICABLE,
    val archivedCount: Int = 0,
    val failedCount: Int = 0
)

data class UnsupportedAnswerIndexListItem(
    val status: String,
    val sourceMode: String,
    val requestText: String,
    val operatorInstruction: String,
    val answerText: String,
    val model: String,
    val createdAt: String,
    // c6 (T-3): 新增字段默认值保持既有构造点兼容（存量调用不改）。
    val topic: String = "",
    val editedByOperator: Boolean = false
)

data class UnsupportedAnswerIndexPage(
    val items: List<UnsupportedAnswerIndexListItem>,
    val total: Long,
    val page: Int,
    val size: Int
)

/**
 * c6 (T-5 通道 B)：「待转事实」队列条目 —— 按 topic 聚合 status = CANDIDATE 的条目，
 * 命中次数 ≥ 阈值；draftBody 取该主题最近一条 CANDIDATE 正文（answerText 为
 * `index: false` 不可做 terms 聚合，故用 top_hits 取最近一条作 QA 事实草稿预填）。
 */
data class UnsupportedAnswerPendingTopic(
    val topic: String,
    val count: Long,
    val draftBody: String
)

class UnsupportedAnswerIndexUnavailableException : RuntimeException()

@Service
class UnsupportedAnswerIndexService(
    private val properties: ElasticsearchProperties,
    private val objectMapper: ObjectMapper,
    restTemplateBuilder: RestTemplateBuilder
) {
    private val log = LoggerFactory.getLogger(javaClass)

    internal val restTemplate: RestTemplate = restTemplateBuilder
        .setConnectTimeout(Duration.ofSeconds(2))
        .setReadTimeout(Duration.ofSeconds(5))
        .build()

    @PostConstruct
    fun bootstrapIndex() {
        try {
            val response = restTemplate.exchange(indexUrl(), HttpMethod.HEAD, HttpEntity<Void>(headers()), Void::class.java)
            if (!response.statusCode.is2xxSuccessful) {
                log.warn("Unsupported answer index HEAD returned HTTP {}", response.statusCode.value())
            } else {
                // c6 (T-1 / I-2，方案 A)：对已存在的索引追加 mapping 补丁——
                // `dynamic: strict` 下新增字段必须先改 mapping，否则写入被 ES 拒绝。
                // HEAD 命中（索引已存在）时额外 PUT <index>/_mapping 提交三个新
                // properties（向 strict mapping 追加 properties 是允许的）；失败只记
                // warn，绝不阻断启动。选方案 A 而非方案 B（v2 + reindex）的理由：
                // 存量索引就地演进、无需切索引名/重灌数据，代价（追加 properties）由
                // ES 保证安全。
                patchIndexMapping()
            }
        } catch (error: HttpStatusCodeException) {
            if (error.statusCode == HttpStatus.NOT_FOUND) {
                createIndexMapping()
                // c6 (T-1 / I-2，方案 A)：新环境由映射资源首建后同样补丁一次——保证
                // `dynamic: strict` 下三个新字段一定在 mapping 里（资源文件与存量索引
                // 两条路径一致）；失败只记 warn。
                patchIndexMapping()
            } else {
                log.warn("Unsupported answer index HEAD failed with HTTP {}", error.statusCode.value())
            }
        } catch (error: ResourceAccessException) {
            log.warn("Unsupported answer index HEAD failed: {}", error.javaClass.simpleName)
        } catch (error: RestClientException) {
            log.warn("Unsupported answer index HEAD failed: {}", error.javaClass.simpleName)
        } catch (error: Exception) {
            log.warn("Unsupported answer index bootstrap failed: {}", error.javaClass.simpleName)
        }
    }

    /**
     * Repair R-2 (V-2)：唯一权威入库资格判定——与 document validate() 的允许集合
     * 完全一致（四种 handling × 两种 generationKind，operatorInstruction 可选）。
     * 训练/线上两个触发点在各自审核/发送闸门之后调用本函数，取代各自的窄化旧过滤器。
     */
    fun isArchiveEligible(version: TrustReplyItemVersion): Boolean =
        version.handling.name in ALLOWED_HANDLINGS &&
            version.generationKind.name in ALLOWED_GENERATION_KINDS &&
            version.requestText.isNotBlank() &&
            version.answerText.isNotBlank()

    fun create(document: UnsupportedAnswerIndexDocument): UnsupportedAnswerIndexCreateResult {
        val validation = validate(document)
        if (validation != null) {
            return UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.REJECTED, validation)
        }
        val id = documentId(document)
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_create/$id",
                HttpMethod.PUT,
                HttpEntity(documentNode(document), headers()),
                JsonNode::class.java
            )
            if (response.statusCode == HttpStatus.CREATED) {
                UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.CREATED)
            } else {
                UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.FAILED, "UNSUPPORTED_ANSWER_INDEX_WRITE_FAILED")
            }
        } catch (error: HttpStatusCodeException) {
            if (error.statusCode == HttpStatus.CONFLICT) {
                UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.ALREADY_EXISTS)
            } else {
                log.warn("Unsupported answer index create failed with HTTP {}", error.statusCode.value())
                UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.FAILED, "UNSUPPORTED_ANSWER_INDEX_WRITE_FAILED")
            }
        } catch (error: RestClientException) {
            log.warn("Unsupported answer index create failed: {}", error.javaClass.simpleName)
            UnsupportedAnswerIndexCreateResult(UnsupportedAnswerIndexCreateOutcome.FAILED, "UNSUPPORTED_ANSWER_INDEX_WRITE_FAILED")
        }
    }

    fun archiveCanonicalVersions(
        source: ResolvedTrustReplySource,
        versions: List<TrustReplyItemVersion>,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant,
        // Repair R-1 (V-3): requestKey -> 最终段落文本（assemble 响应的
        // finalParagraphByRequestKey）。映射缺失/歧义时该条 fail closed。
        finalParagraphs: Map<String, String> = emptyMap()
    ): UnsupportedAnswerIndexArchiveResult = archiveVersions(
        versions = versions,
        documentFactory = { version ->
            trainingDocument(source, version, qualificationId, approvedBy, createdAt, finalParagraphs[version.requestKey])
        }
    )

    fun archiveLiveCanonicalVersions(
        source: ResolvedTrustReplySource,
        versions: List<TrustReplyItemVersion>,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant,
        finalParagraphs: Map<String, String> = emptyMap()
    ): UnsupportedAnswerIndexArchiveResult = archiveVersions(
        versions = versions,
        documentFactory = { version ->
            liveDocument(source, version, qualificationId, approvedBy, createdAt, finalParagraphs[version.requestKey])
        }
    )

    private fun archiveVersions(
        versions: List<TrustReplyItemVersion>,
        documentFactory: (TrustReplyItemVersion) -> UnsupportedAnswerIndexDocument
    ): UnsupportedAnswerIndexArchiveResult {
        if (versions.isEmpty()) return UnsupportedAnswerIndexArchiveResult()

        var archivedCount = 0
        var failedCount = 0
        versions.forEach { version ->
            var idForLog = version.versionId
            try {
                val document = documentFactory(version)
                idForLog = documentId(document)
                val result = create(document)
                when (result.outcome) {
                    UnsupportedAnswerIndexCreateOutcome.CREATED,
                    UnsupportedAnswerIndexCreateOutcome.ALREADY_EXISTS -> archivedCount++
                    else -> {
                        failedCount++
                        log.warn(
                            "Unsupported answer archive rejected for document {}: {}",
                            idForLog,
                            result.errorCode ?: "UNSUPPORTED_ANSWER_INDEX_WRITE_FAILED"
                        )
                    }
                }
            } catch (error: Exception) {
                failedCount++
                log.warn("Unsupported answer archive failed for document {}: {}", idForLog, error.javaClass.simpleName)
            }
        }
        val status = when {
            failedCount == 0 -> UnsupportedAnswerArchiveStatus.SAVED
            archivedCount == 0 -> UnsupportedAnswerArchiveStatus.FAILED
            else -> UnsupportedAnswerArchiveStatus.PARTIAL
        }
        return UnsupportedAnswerIndexArchiveResult(status, archivedCount, failedCount)
    }

    fun list(
        page: Int,
        size: Int,
        sourceMode: UnsupportedAnswerIndexSourceMode?,
        // c6 (T-3 / I-3)：可选 topic keyword 精确过滤。默认 null 保持既有调用兼容。
        topic: String? = null
    ): UnsupportedAnswerIndexPage {
        require(page >= 0) { "page must be non-negative" }
        require(size in 1..100) { "size must be between 1 and 100" }
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_search",
                HttpMethod.POST,
                HttpEntity(listQuery(page, size, sourceMode, topic), headers()),
                JsonNode::class.java
            ).body ?: throw UnsupportedAnswerIndexUnavailableException()
            val hits = response.path("hits")
            if (hits.isMissingNode) throw UnsupportedAnswerIndexUnavailableException()
            val totalNode = hits.path("total")
            val total = if (totalNode.isNumber) totalNode.asLong() else totalNode.path("value").asLong()
            val items = hits.path("hits").mapNotNull { hit -> parseListItem(hit) }
            UnsupportedAnswerIndexPage(items, total, page, size)
        } catch (error: UnsupportedAnswerIndexUnavailableException) {
            throw error
        } catch (error: RestClientException) {
            log.warn("Unsupported answer index list failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        } catch (error: Exception) {
            log.warn("Unsupported answer index list failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        }
    }

    /**
     * c6 (T-5 通道 B)：按 topic 聚合 `status = CANDIDATE` 条目，命中次数 ≥ 阈值
     * （默认 3）的主题置顶返回；draftBody 取该主题最近一条 CANDIDATE 正文（answerText
     * 为 `index: false` 不可 terms 聚合，故用 top_hits 取最近一条作 QA 事实草稿预填）。
     * 索引不可用时抛 [UnsupportedAnswerIndexUnavailableException]（与 list 同降级）。
     */
    fun pendingTopics(threshold: Int): List<UnsupportedAnswerPendingTopic> {
        require(threshold >= 1) { "threshold must be positive" }
        val query = objectMapper.createObjectNode().apply {
            put("size", 0)
            put("track_total_hits", false)
            set<ObjectNode>(
                "query",
                objectMapper.createObjectNode().set<ObjectNode>(
                    "bool",
                    objectMapper.createObjectNode().set<ArrayNode>(
                        "filter",
                        objectMapper.createArrayNode().apply {
                            add(termNode("status", UnsupportedAnswerIndexStatus.CANDIDATE.name))
                        }
                    )
                )
            )
            set<ObjectNode>(
                "aggs",
                objectMapper.createObjectNode().set<ObjectNode>(
                    "topics",
                    objectMapper.createObjectNode().apply {
                        set<ObjectNode>(
                            "terms",
                            objectMapper.createObjectNode().apply {
                                put("field", "topic")
                                put("size", 200)
                                put("min_doc_count", threshold)
                            }
                        )
                        set<ObjectNode>(
                            "aggs",
                            objectMapper.createObjectNode().set<ObjectNode>(
                                "latest",
                                objectMapper.createObjectNode().set<ObjectNode>(
                                    "top_hits",
                                    objectMapper.createObjectNode().apply {
                                        put("size", 1)
                                        set<ArrayNode>(
                                            "sort",
                                            objectMapper.createArrayNode().add(
                                                objectMapper.createObjectNode().set<ObjectNode>(
                                                    "createdAt",
                                                    objectMapper.createObjectNode().put("order", "desc")
                                                )
                                            )
                                        )
                                    }
                                )
                            )
                        )
                    }
                )
            )
        }
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_search",
                HttpMethod.POST,
                HttpEntity(query, headers()),
                JsonNode::class.java
            ).body ?: throw UnsupportedAnswerIndexUnavailableException()
            val buckets = response.path("aggregations").path("topics").path("buckets")
            if (buckets.isMissingNode) throw UnsupportedAnswerIndexUnavailableException()
            buckets.mapNotNull { bucket ->
                val topic = bucket.path("key").asText("").trim()
                if (topic.isBlank()) {
                    log.warn("Skipping pending topic with blank key")
                    null
                } else {
                    val latest = bucket.path("latest").path("hits").path("hits").firstOrNull()
                    UnsupportedAnswerPendingTopic(
                        topic = topic,
                        count = bucket.path("doc_count").asLong(),
                        draftBody = latest?.path("_source")?.path("answerText")?.asText("")?.trim().orEmpty()
                    )
                }
            }
        } catch (error: UnsupportedAnswerIndexUnavailableException) {
            throw error
        } catch (error: RestClientException) {
            log.warn("Unsupported answer index pending topics failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        } catch (error: Exception) {
            log.warn("Unsupported answer index pending topics failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        }
    }

    /**
     * c6 (T-5 通道 B)：运营保存 QA 规则后，把该主题全部 `CANDIDATE` 条目置为
     * `ACTIVE`（I-5：ACTIVE = 已由运营转化为 QA 事实）。返回被更新的文档数。
     * **不自动创建规则**——必须经运营保存（Out of scope 已声明）。
     */
    fun activatePendingTopic(topic: String): Long {
        val query = objectMapper.createObjectNode().apply {
            put("conflicts", "proceed")
            put("refresh", true)
            set<ObjectNode>(
                "query",
                objectMapper.createObjectNode().set<ObjectNode>(
                    "bool",
                    objectMapper.createObjectNode().set<ArrayNode>(
                        "filter",
                        objectMapper.createArrayNode().apply {
                            add(termNode("status", UnsupportedAnswerIndexStatus.CANDIDATE.name))
                            add(termNode("topic", topic))
                        }
                    )
                )
            )
            set<ObjectNode>(
                "script",
                objectMapper.createObjectNode().apply {
                    put("source", "ctx._source.status = params.status")
                    set<ObjectNode>("params", objectMapper.createObjectNode().put("status", UnsupportedAnswerIndexStatus.ACTIVE.name))
                }
            )
        }
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_update_by_query",
                HttpMethod.POST,
                HttpEntity(query, headers()),
                JsonNode::class.java
            ).body ?: throw UnsupportedAnswerIndexUnavailableException()
            response.path("updated").asLong()
        } catch (error: UnsupportedAnswerIndexUnavailableException) {
            throw error
        } catch (error: RestClientException) {
            log.warn("Unsupported answer index activate failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        } catch (error: Exception) {
            log.warn("Unsupported answer index activate failed: {}", error.javaClass.simpleName)
            throw UnsupportedAnswerIndexUnavailableException()
        }
    }

    /**
     * c6 (T-4 通道 A / I-1 / IP-3)：按 topicOrder 的主题各取最近 N 条
     * `status = CANDIDATE` 的 `finalParagraphText` 作为**措辞样例**——仅供句式、语气与
     * 过渡方式参考，索引内容永远不作为事实来源进入生成链路（I-1）。
     *
     * 尽力而为：任何失败（ES 不可用、超时、解析异常）都返回空 map 并只记 warn——
     * 样例获取不得阻断编排主流程（与「归档失败不阻断主流程」同一原则，What must
     * NOT change 第 2 项）。
     */
    fun recentCandidateSamples(topics: List<String>, limitPerTopic: Int): Map<String, List<String>> {
        if (topics.isEmpty() || limitPerTopic <= 0) return emptyMap()
        val query = objectMapper.createObjectNode().apply {
            put("size", Math.min(limitPerTopic * topics.size, MAX_STYLE_SAMPLE_FETCH))
            put("track_total_hits", false)
            set<ArrayNode>("_source", objectMapper.createArrayNode().apply {
                add("topic")
                add("finalParagraphText")
            })
            set<ArrayNode>("sort", objectMapper.createArrayNode().apply {
                add(objectMapper.createObjectNode().set<ObjectNode>(
                    "createdAt",
                    objectMapper.createObjectNode().put("order", "desc")
                ))
            })
            set<ObjectNode>(
                "query",
                objectMapper.createObjectNode().set<ObjectNode>(
                    "bool",
                    objectMapper.createObjectNode().set<ArrayNode>(
                        "filter",
                        objectMapper.createArrayNode().apply {
                            add(termNode("status", UnsupportedAnswerIndexStatus.CANDIDATE.name))
                            add(termsNode("topic", topics))
                        }
                    )
                )
            )
        }
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_search",
                HttpMethod.POST,
                HttpEntity(query, headers()),
                JsonNode::class.java
            ).body ?: return emptyMap()
            val hits = response.path("hits").path("hits")
            if (hits.isMissingNode) return emptyMap()
            val samples = linkedMapOf<String, MutableList<String>>()
            hits.forEach { hit ->
                val source = hit.path("_source")
                val topic = source.path("topic").asText("").trim()
                val text = source.path("finalParagraphText").asText("").trim()
                if (topic.isNotEmpty() && text.isNotEmpty()) {
                    val bucket = samples.getOrPut(topic) { mutableListOf() }
                    if (bucket.size < limitPerTopic) bucket += text
                }
            }
            samples.mapValues { it.value.toList() }
        } catch (error: Exception) {
            log.warn("Unsupported answer index style sample fetch failed: {}", error.javaClass.simpleName)
            emptyMap()
        }
    }

    fun documentId(document: UnsupportedAnswerIndexDocument): String =
        sha256("${document.sourceType}|${document.sourceId}|${document.requestKey}|${document.versionId}")

    private fun createIndexMapping() {
        try {
            val mapping = ClassPathResource(MAPPING_RESOURCE).inputStream.use { objectMapper.readTree(it) }
            val response = restTemplate.exchange(
                indexUrl(),
                HttpMethod.PUT,
                HttpEntity(mapping, headers()),
                JsonNode::class.java
            )
            if (response.statusCode.is2xxSuccessful) {
                log.info("Created unsupported answer index mapping")
            } else {
                log.warn("Unsupported answer index mapping create returned HTTP {}", response.statusCode.value())
            }
        } catch (error: HttpStatusCodeException) {
            log.warn("Unsupported answer index mapping create failed with HTTP {}", error.statusCode.value())
        } catch (error: Exception) {
            log.warn("Unsupported answer index mapping create failed: {}", error.javaClass.simpleName)
        }
    }

    /**
     * c6 (T-1 / I-2，方案 A)：`PUT <index>/_mapping` 提交三个新 properties。任何失败
     * 只记 warn（含测试桩的断言错误）——bootstrap 在任何情况下都不得阻断应用启动
     * （「失败只记 warn」），故此处捕获 Throwable 而非 Exception。
     */
    private fun patchIndexMapping() {
        try {
            val patch = objectMapper.createObjectNode().set<ObjectNode>(
                "properties",
                objectMapper.createObjectNode()
                    .set<ObjectNode>("topic", objectMapper.createObjectNode().put("type", "keyword"))
                    .set<ObjectNode>(
                        "finalParagraphText",
                        objectMapper.createObjectNode().put("type", "text").put("index", false)
                    )
                    .set<ObjectNode>("editedByOperator", objectMapper.createObjectNode().put("type", "boolean"))
            )
            val response = restTemplate.exchange(
                "${indexUrl()}/_mapping",
                HttpMethod.PUT,
                HttpEntity(patch, headers()),
                JsonNode::class.java
            )
            if (response.statusCode.is2xxSuccessful) {
                log.info("Patched unsupported answer index mapping with topic/finalParagraphText/editedByOperator")
            } else {
                log.warn("Unsupported answer index mapping patch returned HTTP {}", response.statusCode.value())
            }
        } catch (error: Throwable) {
            log.warn("Unsupported answer index mapping patch failed: {}", error.javaClass.simpleName)
        }
    }

    private fun listQuery(page: Int, size: Int, sourceMode: UnsupportedAnswerIndexSourceMode?, topic: String?): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("track_total_hits", true)
            put("from", Math.multiplyExact(page, size))
            put("size", size)
            set<ArrayNode>("_source", objectMapper.createArrayNode().apply { LIST_SOURCE_FIELDS.forEach(::add) })
            set<ArrayNode>("sort", objectMapper.createArrayNode().apply {
                add(objectMapper.createObjectNode().set<ObjectNode>("createdAt", objectMapper.createObjectNode().put("order", "desc")))
                add(objectMapper.createObjectNode().set<ObjectNode>("versionId", objectMapper.createObjectNode().put("order", "asc")))
            })
            when {
                // c6 (T-3 / I-3)：topic 为 keyword 精确过滤（v1 mapping 的 requestText /
                // answerText 都是 index:false，不可检索）；与 sourceMode 组合为
                // bool.filter。topic 为空时保持既有 match_all / term 结构不变。
                topic != null -> set<ObjectNode>(
                    "query",
                    objectMapper.createObjectNode().set<ObjectNode>(
                        "bool",
                        objectMapper.createObjectNode().set<ArrayNode>(
                            "filter",
                            objectMapper.createArrayNode().apply {
                                add(termNode("topic", topic))
                                if (sourceMode != null) add(termNode("sourceMode", sourceMode.name))
                            }
                        )
                    )
                )
                sourceMode == null -> set<ObjectNode>("query", objectMapper.createObjectNode().set<ObjectNode>("match_all", objectMapper.createObjectNode()))
                else -> set<ObjectNode>("query", objectMapper.createObjectNode().set<ObjectNode>("term", objectMapper.createObjectNode().put("sourceMode", sourceMode.name)))
            }
        }

    private fun termNode(field: String, value: String): ObjectNode =
        objectMapper.createObjectNode().set<ObjectNode>("term", objectMapper.createObjectNode().put(field, value))

    /** c6 (T-4)：多值 keyword 精确过滤（I-3：只对 topic / sourceMode / status 做 term）。 */
    private fun termsNode(field: String, values: List<String>): ObjectNode =
        objectMapper.createObjectNode().set<ObjectNode>(
            "terms",
            objectMapper.createObjectNode().set<ArrayNode>(
                field,
                objectMapper.createArrayNode().apply { values.forEach(::add) }
            )
        )

    private fun parseListItem(hit: JsonNode): UnsupportedAnswerIndexListItem? {
        val source = hit.path("_source")
        val values = LIST_SOURCE_FIELDS.associateWith { field -> source.path(field).asText("").trim() }
        // c6 (I-6 / T-3)：operatorInstruction 已降为可选，从空值丢弃判定中移除
        // （空时渲染为 —）；topic / editedByOperator 为新增字段，存量文档可能缺失，
        // 同样不参与空值丢弃。其余必填字段任一为空仍视为脏文档整条丢弃（I-6 排查
        // 顺序：先看 total 是否为 0，total > 0 且 items 为空才是本条）。
        val requiredFields = values.filterKeys { it !in OPTIONAL_LIST_FIELDS }
        if (requiredFields.values.any(String::isBlank)
            || values.getValue("status") !in UnsupportedAnswerIndexStatus.entries.map { it.name }
            || values.getValue("sourceMode") !in UnsupportedAnswerIndexSourceMode.entries.map { it.name }) {
            log.warn("Skipping malformed unsupported answer index hit {}", hit.path("_id").asText("unknown"))
            return null
        }
        return UnsupportedAnswerIndexListItem(
            status = values.getValue("status"),
            sourceMode = values.getValue("sourceMode"),
            requestText = values.getValue("requestText"),
            operatorInstruction = values.getValue("operatorInstruction").ifBlank { "—" },
            answerText = values.getValue("answerText"),
            model = values.getValue("model"),
            createdAt = values.getValue("createdAt"),
            topic = values.getValue("topic"),
            editedByOperator = source.path("editedByOperator").asBoolean(false)
        )
    }

    private fun documentNode(document: UnsupportedAnswerIndexDocument): ObjectNode = objectMapper.createObjectNode().apply {
        put("schemaVersion", document.schemaVersion)
        put("status", document.status.name)
        put("sourceMode", document.sourceMode.name)
        put("sourceType", document.sourceType.name)
        put("sourceId", document.sourceId)
        put("sourceVersion", document.sourceVersion)
        put("expertContactId", document.expertContactId)
        put("campaignId", document.campaignId)
        put("requestKey", document.requestKey)
        put("requestIndex", document.requestIndex)
        put("requestText", document.requestText)
        put("handling", document.handling)
        put("operatorInstruction", document.operatorInstruction)
        put("operatorInstructionHash", document.operatorInstructionHash)
        put("versionId", document.versionId)
        put("answerText", document.answerText)
        put("answerHash", document.answerHash)
        put("model", document.model)
        put("generationKind", document.generationKind)
        put("qualificationType", document.qualificationType.name)
        put("qualificationId", document.qualificationId)
        put("approvedBy", document.approvedBy)
        put("createdAt", document.createdAt.toString())
        // c6 (T-2)：归档接缝三字段（mapping 已按方案 A 补丁）。
        put("topic", document.topic)
        put("finalParagraphText", document.finalParagraphText)
        put("editedByOperator", document.editedByOperator)
    }

    private fun trainingDocument(
        source: ResolvedTrustReplySource,
        version: TrustReplyItemVersion,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant,
        finalParagraph: String?
    ): UnsupportedAnswerIndexDocument = baseDocument(
        source = source,
        version = version,
        status = UnsupportedAnswerIndexStatus.CANDIDATE,
        sourceMode = UnsupportedAnswerIndexSourceMode.TRAINING,
        sourceType = UnsupportedAnswerIndexSourceType.TRAINING_MAIL,
        qualificationType = UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION,
        qualificationId = qualificationId,
        approvedBy = approvedBy,
        createdAt = createdAt,
        editedByOperator = false,
        finalParagraph = finalParagraph
    )

    private fun liveDocument(
        source: ResolvedTrustReplySource,
        version: TrustReplyItemVersion,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant,
        finalParagraph: String?
    ): UnsupportedAnswerIndexDocument = baseDocument(
        source = source,
        version = version,
        // F-1 (I-5)：ACTIVE = 已由运营转化为 QA 事实（通道 B 完成）。新建的线上条目
        // 尚未转化，必须以 CANDIDATE 入库——唯一 ACTIVE 写入方是通道 B 的
        // activatePendingTopic()。否则线上历史回答会从通道 A 样例与通道 B 待转队列
        // （均 CANDIDATE-only）中消失，且 UI 将其误标为「已转化」。
        status = UnsupportedAnswerIndexStatus.CANDIDATE,
        sourceMode = UnsupportedAnswerIndexSourceMode.LIVE,
        sourceType = UnsupportedAnswerIndexSourceType.LIVE_INBOUND,
        qualificationType = UnsupportedAnswerIndexQualificationType.LIVE_SEND,
        qualificationId = qualificationId,
        approvedBy = approvedBy,
        createdAt = createdAt,
        // c6 (T-2 / I-4)：线上侧照常归档并置 editedByOperator = true（A-2「运营已编辑」）。
        editedByOperator = true,
        finalParagraph = finalParagraph
    )

    private fun baseDocument(
        source: ResolvedTrustReplySource,
        version: TrustReplyItemVersion,
        status: UnsupportedAnswerIndexStatus,
        sourceMode: UnsupportedAnswerIndexSourceMode,
        sourceType: UnsupportedAnswerIndexSourceType,
        qualificationType: UnsupportedAnswerIndexQualificationType,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant,
        editedByOperator: Boolean,
        // Repair R-1 (V-3)：该条所属的最终闭合段落文本（步骤 03 权威段落，来自
        // assemble 响应的 finalParagraphByRequestKey）；绝不回退为 item answerText。
        finalParagraph: String?
    ): UnsupportedAnswerIndexDocument = UnsupportedAnswerIndexDocument(
        status = status,
        sourceMode = sourceMode,
        sourceType = sourceType,
        sourceId = source.source.sourceId,
        sourceVersion = source.sourceVersion,
        expertContactId = requireNotNull(source.contact.id) { "Source contact id is required" },
        campaignId = source.contact.campaignId,
        requestKey = version.requestKey,
        requestIndex = version.requestIndex,
        requestText = version.requestText,
        handling = version.handling.name,
        operatorInstruction = version.operatorInstruction,
        operatorInstructionHash = version.operatorInstructionHash,
        versionId = version.versionId,
        answerText = version.answerText,
        answerHash = sha256(version.answerText),
        model = version.model,
        generationKind = version.generationKind.name,
        qualificationType = qualificationType,
        qualificationId = qualificationId,
        approvedBy = approvedBy,
        createdAt = createdAt,
        // c6 (T-2)：topic 取版本 claims 的 intentKey 主段（与 12/13 的 topicOrder 同源）；
        // 无 claims 时沿用收口器的 gap 主题约定。finalParagraphText 见数据类注释。
        topic = versionTopic(version),
        finalParagraphText = finalParagraph.orEmpty(),
        editedByOperator = editedByOperator
    )

    /** c6 (T-2)：归档条目的 topic —— claims 的 intentKey 主段；无 claims 时用 gap 约定。 */
    private fun versionTopic(version: TrustReplyItemVersion): String =
        version.claims.firstNotNullOfOrNull { claim ->
            claim.intentKey.substringBefore('.').takeIf(String::isNotBlank)
        } ?: "unanswered.request.${version.requestIndex}"

    private fun validate(document: UnsupportedAnswerIndexDocument): String? {
        if (document.schemaVersion != SCHEMA_VERSION || document.sourceId <= 0 || document.expertContactId <= 0
            || document.campaignId <= 0 || document.requestIndex < 0) return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        // c6 (T-2 / I-4)：handling / generationKind 放宽为允许集合——入库门槛只放宽
        // 样本形态，「已被人认可」前提不变（训练侧 rating == MEETS_EXPECTATION、
        // 线上侧真实发送成功仍在调用方把关）。
        if (document.handling !in ALLOWED_HANDLINGS || document.generationKind !in ALLOWED_GENERATION_KINDS) {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        // Repair R-1 (V-3): 资格内文档必须携带非空 finalParagraphText——步骤 03 最终
        // 段落（assemble 的确定性映射）；映射缺失/歧义即 fail closed（「reject missing
        // or ambiguous mappings」），绝不回退为 item answerText。
        if (document.finalParagraphText.isBlank()) {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        if (document.operatorInstructionHash != sha256(document.operatorInstruction)
            || document.answerHash != sha256(document.answerText)) return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        // c6 (T-2 / I-4)：operatorInstruction 从必填降为可选——非空时校验长度，空时放行。
        if (document.operatorInstruction.isNotBlank() && document.operatorInstruction.length > 4_000) {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        if (!bounded(document.sourceVersion, 512) || !bounded(document.requestKey, 512) || !bounded(document.requestText, 10_000)
            || !bounded(document.versionId, 512) || !bounded(document.answerText, 20_000)
            || !bounded(document.model, 256) || !bounded(document.qualificationId, 512) || !bounded(document.approvedBy, 128)) {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        // c6 (T-2 / I-5)：CANDIDATE / ACTIVE 改为按「是否已转化」区分，来源由
        // sourceMode 表达——status 与 sourceMode 不再绑死；仅保留
        // sourceMode ↔ sourceType ↔ qualificationType 的组合一致性。
        val training = document.sourceMode == UnsupportedAnswerIndexSourceMode.TRAINING
            && document.sourceType == UnsupportedAnswerIndexSourceType.TRAINING_MAIL
            && document.qualificationType == UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION
        val live = document.sourceMode == UnsupportedAnswerIndexSourceMode.LIVE
            && document.sourceType == UnsupportedAnswerIndexSourceType.LIVE_INBOUND
            && document.qualificationType == UnsupportedAnswerIndexQualificationType.LIVE_SEND
        return if (training || live) null else "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
    }

    private fun bounded(value: String, maxLength: Int): Boolean = value.isNotBlank() && value.length <= maxLength

    private fun indexUrl(): String = "${properties.baseUrl.trimEnd('/')}/${properties.unsupportedAnswerIndexName}"

    private fun headers(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        val raw = "${properties.username}:${properties.password}"
        set(HttpHeaders.AUTHORIZATION, "Basic ${Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))}")
    }

    companion object {
        const val SCHEMA_VERSION = "trust-reply-unsupported-answer-v1"
        private const val MAPPING_RESOURCE = "es/trust_reply_unsupported_answer_v1.json"

        // c6 (T-2 / I-4)：入库允许集合——四种 handling × 两种 generationKind。
        private val ALLOWED_HANDLINGS = setOf(
            "ANSWER_FROM_OPERATOR_INPUT",
            "ANSWER_EVIDENCE_WITH_OPERATOR_INPUT",
            "ANSWER_SUPPORTED_PART",
            "ACKNOWLEDGE_PENDING"
        )
        private val ALLOWED_GENERATION_KINDS = setOf("AI_GENERATED", "SAFE_TEMPLATE")

        // c6 (T-3)：列表投影字段——新增 topic / editedByOperator（I-3 / A-2 标记）。
        private val LIST_SOURCE_FIELDS = listOf(
            "status", "sourceMode", "requestText", "operatorInstruction", "answerText",
            "model", "createdAt", "topic", "editedByOperator"
        )

        // c6 (I-6 / T-3)：不参与空值丢弃的字段——operatorInstruction 已降为可选，
        // topic / editedByOperator 为新增字段、存量文档可能缺失。
        private val OPTIONAL_LIST_FIELDS = setOf("operatorInstruction", "topic", "editedByOperator")

        // c6 (T-4)：单次样例拉取上限（N=2 × 主题数，封顶防大请求）。
        private const val MAX_STYLE_SAMPLE_FETCH = 200

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
