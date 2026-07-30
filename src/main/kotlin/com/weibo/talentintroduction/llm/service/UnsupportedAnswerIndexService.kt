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
    val createdAt: Instant
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
    val createdAt: String
)

data class UnsupportedAnswerIndexPage(
    val items: List<UnsupportedAnswerIndexListItem>,
    val total: Long,
    val page: Int,
    val size: Int
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
            }
        } catch (error: HttpStatusCodeException) {
            if (error.statusCode == HttpStatus.NOT_FOUND) {
                createIndexMapping()
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
        createdAt: Instant
    ): UnsupportedAnswerIndexArchiveResult = archiveVersions(
        versions = versions,
        documentFactory = { version -> trainingDocument(source, version, qualificationId, approvedBy, createdAt) }
    )

    fun archiveLiveCanonicalVersions(
        source: ResolvedTrustReplySource,
        versions: List<TrustReplyItemVersion>,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant
    ): UnsupportedAnswerIndexArchiveResult = archiveVersions(
        versions = versions,
        documentFactory = { version -> liveDocument(source, version, qualificationId, approvedBy, createdAt) }
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
        sourceMode: UnsupportedAnswerIndexSourceMode?
    ): UnsupportedAnswerIndexPage {
        require(page >= 0) { "page must be non-negative" }
        require(size in 1..100) { "size must be between 1 and 100" }
        return try {
            val response = restTemplate.exchange(
                "${indexUrl()}/_search",
                HttpMethod.POST,
                HttpEntity(listQuery(page, size, sourceMode), headers()),
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

    private fun listQuery(page: Int, size: Int, sourceMode: UnsupportedAnswerIndexSourceMode?): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("track_total_hits", true)
            put("from", Math.multiplyExact(page, size))
            put("size", size)
            set<ArrayNode>("_source", objectMapper.createArrayNode().apply { LIST_SOURCE_FIELDS.forEach(::add) })
            set<ArrayNode>("sort", objectMapper.createArrayNode().apply {
                add(objectMapper.createObjectNode().set<ObjectNode>("createdAt", objectMapper.createObjectNode().put("order", "desc")))
                add(objectMapper.createObjectNode().set<ObjectNode>("_id", objectMapper.createObjectNode().put("order", "asc")))
            })
            if (sourceMode == null) {
                set<ObjectNode>("query", objectMapper.createObjectNode().set<ObjectNode>("match_all", objectMapper.createObjectNode()))
            } else {
                set<ObjectNode>("query", objectMapper.createObjectNode().set<ObjectNode>("term", objectMapper.createObjectNode().put("sourceMode", sourceMode.name)))
            }
        }

    private fun parseListItem(hit: JsonNode): UnsupportedAnswerIndexListItem? {
        val source = hit.path("_source")
        val values = LIST_SOURCE_FIELDS.associateWith { field -> source.path(field).asText("").trim() }
        if (values.values.any(String::isBlank)
            || values.getValue("status") !in UnsupportedAnswerIndexStatus.entries.map { it.name }
            || values.getValue("sourceMode") !in UnsupportedAnswerIndexSourceMode.entries.map { it.name }) {
            log.warn("Skipping malformed unsupported answer index hit {}", hit.path("_id").asText("unknown"))
            return null
        }
        return UnsupportedAnswerIndexListItem(
            status = values.getValue("status"),
            sourceMode = values.getValue("sourceMode"),
            requestText = values.getValue("requestText"),
            operatorInstruction = values.getValue("operatorInstruction"),
            answerText = values.getValue("answerText"),
            model = values.getValue("model"),
            createdAt = values.getValue("createdAt")
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
    }

    private fun trainingDocument(
        source: ResolvedTrustReplySource,
        version: TrustReplyItemVersion,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant
    ): UnsupportedAnswerIndexDocument = baseDocument(
        source = source,
        version = version,
        status = UnsupportedAnswerIndexStatus.CANDIDATE,
        sourceMode = UnsupportedAnswerIndexSourceMode.TRAINING,
        sourceType = UnsupportedAnswerIndexSourceType.TRAINING_MAIL,
        qualificationType = UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION,
        qualificationId = qualificationId,
        approvedBy = approvedBy,
        createdAt = createdAt
    )

    private fun liveDocument(
        source: ResolvedTrustReplySource,
        version: TrustReplyItemVersion,
        qualificationId: String,
        approvedBy: String,
        createdAt: Instant
    ): UnsupportedAnswerIndexDocument = baseDocument(
        source = source,
        version = version,
        status = UnsupportedAnswerIndexStatus.ACTIVE,
        sourceMode = UnsupportedAnswerIndexSourceMode.LIVE,
        sourceType = UnsupportedAnswerIndexSourceType.LIVE_INBOUND,
        qualificationType = UnsupportedAnswerIndexQualificationType.LIVE_SEND,
        qualificationId = qualificationId,
        approvedBy = approvedBy,
        createdAt = createdAt
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
        createdAt: Instant
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
        createdAt = createdAt
    )

    private fun validate(document: UnsupportedAnswerIndexDocument): String? {
        if (document.schemaVersion != SCHEMA_VERSION || document.sourceId <= 0 || document.expertContactId <= 0
            || document.campaignId <= 0 || document.requestIndex < 0) return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        if (document.handling != "ANSWER_FROM_OPERATOR_INPUT" || document.generationKind != "AI_GENERATED") {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        if (document.operatorInstructionHash != sha256(document.operatorInstruction)
            || document.answerHash != sha256(document.answerText)) return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        if (!bounded(document.sourceVersion, 512) || !bounded(document.requestKey, 512) || !bounded(document.requestText, 10_000)
            || !bounded(document.operatorInstruction, 4_000) || !bounded(document.versionId, 512) || !bounded(document.answerText, 20_000)
            || !bounded(document.model, 256) || !bounded(document.qualificationId, 512) || !bounded(document.approvedBy, 128)) {
            return "UNSUPPORTED_ANSWER_INDEX_DOCUMENT_INVALID"
        }
        val training = document.sourceMode == UnsupportedAnswerIndexSourceMode.TRAINING
            && document.sourceType == UnsupportedAnswerIndexSourceType.TRAINING_MAIL
            && document.status == UnsupportedAnswerIndexStatus.CANDIDATE
            && document.qualificationType == UnsupportedAnswerIndexQualificationType.TRAINING_EVALUATION
        val live = document.sourceMode == UnsupportedAnswerIndexSourceMode.LIVE
            && document.sourceType == UnsupportedAnswerIndexSourceType.LIVE_INBOUND
            && document.status == UnsupportedAnswerIndexStatus.ACTIVE
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
        private val LIST_SOURCE_FIELDS = listOf(
            "status", "sourceMode", "requestText", "operatorInstruction", "answerText", "model", "createdAt"
        )

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
