package com.weibo.talentintroduction.discovery.sample

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import java.net.URI
import java.security.MessageDigest

enum class DiscoverySampleEntry { SYNC, QUEUE }
data class DiscoverySampleContext(val executionId: Long, val source: String, val entry: DiscoverySampleEntry,
    val sourceBatchNumber: Int?, val queueJobId: Long?) {
    fun valid() = executionId > 0 && source == "OPENALEX" && when (entry) {
        DiscoverySampleEntry.SYNC -> (sourceBatchNumber ?: 0) > 0 && queueJobId == null
        DiscoverySampleEntry.QUEUE -> (queueJobId ?: 0) > 0 && sourceBatchNumber == null
    }
}
enum class DiscoverySampleBucket { EXTRACTION_EXCEPTION, IDENTITY_UNRESOLVED, NO_FULLTEXT_ADDRESS, NO_EMAIL,
    BODY_TOO_LARGE, DOWNLOAD_FAILED, PARSE_FAILED, OTHER_FAILURE, SUCCESS_CONTROL }
enum class DiscoverySampleBodyStatus { SAVED, NOT_AVAILABLE, BODY_LIMIT }
enum class DiscoverySampleAttemptKind { PDF_URL, PMC_XML }
enum class DiscoverySampleDecision { SAVED, DISABLED, INVALID_CONTEXT, CONFIG_ERROR, CAPTURE_BUSY, HOST_BUSY,
    DUPLICATE, QUOTA, METADATA_LIMIT, LOCK_BUSY, IO_ERROR, STORE_INVALID }
data class DiscoverySampleAttempt(val sequence: Int, val kind: DiscoverySampleAttemptKind, val url: String,
    val urlSha256: String, val elapsedMs: Long, val httpStatus: Int?, val contentType: String?,
    val exceptionClass: String?, val bodyStatus: DiscoverySampleBodyStatus, val bodyFile: String?,
    val bodySha256: String?, val bodyBytes: Long?, val pdfSettings: PdfExtractionProperties?)
data class DiscoverySampleManifest(val schemaVersion: Int = 1, val stage: String = "EXTRACTION",
    val sampleId: String, val inputSha256: String, val capturedAt: String, val context: DiscoverySampleContext,
    val paper: PaperMetadata, val urlFingerprints: List<String?>, val bucket: DiscoverySampleBucket,
    val unresolvedAuthorEmailCount: Int, val outcome: EmailExtractionOutcome?, val extractionExceptionClass: String?,
    val identityExtractionVersion: Int = DiscoveryIdentity.EXTRACTION_VERSION, val attempts: List<DiscoverySampleAttempt>,
    val attemptsTruncated: Boolean, val bodyBytesRetained: Long)

object DiscoverySampleJson {
    val mapper = jacksonObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    /** Stop serialization before allocating an unbounded diagnostic JSON buffer. */
    fun bounded(value: Any, limit: Int): ByteArray? {
        val bytes = java.io.ByteArrayOutputStream(minOf(limit, 8192))
        var exceeded = false
        val output = object : java.io.OutputStream() {
            override fun write(value: Int) {
                if (bytes.size() >= limit) { exceeded = true; throw java.io.IOException("metadata limit") }
                bytes.write(value)
            }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (length > limit - bytes.size()) { exceeded = true; throw java.io.IOException("metadata limit") }
                bytes.write(buffer, offset, length)
            }
        }
        try { mapper.writeValue(output, value) }
        catch (error: Exception) { if (!exceeded) throw error }
        return if (exceeded) null else bytes.toByteArray()
    }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
    fun hash(text: String) = hash(text.toByteArray(Charsets.UTF_8))
    private fun sorted(node: JsonNode): JsonNode = when {
        node.isObject -> mapper.createObjectNode().also { out -> node.fieldNames().asSequence().sorted().forEach {
            out.set<JsonNode>(it, sorted(node[it])) } }
        node.isArray -> mapper.createArrayNode().also { out -> node.forEach { out.add(sorted(it)) } }
        else -> node
    }
    fun canonical(value: Any): ByteArray = mapper.writeValueAsBytes(sorted(mapper.valueToTree(value)))
    fun scrub(url: String): String = try {
        val uri = URI(url)
        if (uri.isOpaque) uri.scheme + ":" + uri.rawSchemeSpecificPart.substringBefore('?').substringBefore('#')
        else buildString {
            uri.scheme?.let { append(it).append(':') }
            uri.rawAuthority?.let { append("//").append(it.substringAfterLast('@')) }
            append(uri.rawPath.orEmpty())
        }
    } catch (_: Exception) { "" }
    fun paper(paper: PaperMetadata) = paper.copy(downloadUrl = paper.downloadUrl?.let(::scrub),
        candidateDownloadUrls = paper.candidateDownloadUrls.map(::scrub))
    fun bucket(outcome: EmailExtractionOutcome?, error: Throwable?): DiscoverySampleBucket = when {
        error != null -> DiscoverySampleBucket.EXTRACTION_EXCEPTION
        outcome!!.emails.any { it.givenNames.isNullOrBlank() || it.familyNames.isNullOrBlank() } -> DiscoverySampleBucket.IDENTITY_UNRESOLVED
        outcome.failureReason == "NO_PMC_ID" -> DiscoverySampleBucket.NO_FULLTEXT_ADDRESS
        outcome.failureReason?.startsWith("NO_EMAIL_IN_") == true -> DiscoverySampleBucket.NO_EMAIL
        outcome.failureReason == "PDF_TOO_LARGE" -> DiscoverySampleBucket.BODY_TOO_LARGE
        outcome.failureReason in listOf("PDF_DOWNLOAD_FAILED", "FULLTEXT_FETCH_FAILED") -> DiscoverySampleBucket.DOWNLOAD_FAILED
        outcome.failureReason in listOf("PDF_PARSE_FAILED", "XML_PARSE_FAILED") -> DiscoverySampleBucket.PARSE_FAILED
        !outcome.failureReason.isNullOrEmpty() || outcome.emails.isEmpty() -> DiscoverySampleBucket.OTHER_FAILURE
        else -> DiscoverySampleBucket.SUCCESS_CONTROL
    }
}
