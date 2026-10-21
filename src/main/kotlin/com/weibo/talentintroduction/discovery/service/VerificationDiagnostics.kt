package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.VerificationProvider
import java.security.MessageDigest

/** Only this bounded projection, never the provider response, may be retained for failures. */
data class VerificationDiagnostic(
    val schemaVersion: Int = 1,
    val kind: String = "PROVIDER_ERROR",
    val provider: VerificationProvider,
    val endpointPath: String,
    val httpStatus: Int?,
    val providerCode: String? = null,
    val message: String? = null,
    val requestId: String? = null,
    val retryAfter: String? = null,
    val contentType: String? = null,
    val bodyBytesRead: Int? = null,
    val bodyTruncated: Boolean = false,
    val bodySha256: String?,
    val hashScope: String,
    val classification: String
)

/** Optional bounded trace. Business completeness is held in search/progress, never in this projection. */
data class WorkerQueryDiagnostic(
    val queryIndex: Int, val queryHash: String, val kind: String,
    val pagesReceived: Int = 0, val totalEntries: Long? = null, val candidateCount: Int = 0,
    val state: String = "PENDING", val stopReason: String? = null, val lastAttemptId: Long? = null
)

data class WorkerDetailReuse(
    val sourceAttemptId: Long, val sourceItemId: Long, val personId: String,
    val providerResponseSha256: String
)

/** References point into the frozen search/evidence; no duplicate personal profile is stored here. */
data class CandidateDecisionTrace(
    val personId: String,
    val candidateRef: String,
    val nameMatch: String = "NOT_EVALUATED",
    val orcid: String = "NOT_EVALUATED",
    val currentInstitution: String = "NOT_EVALUATED",
    val historicalInstitution: String = "NOT_EVALUATED",
    val professor: String = "NOT_EVALUATED",
    val specialty: String = "NOT_EVALUATED",
    val chosenReason: String? = null,
    val rejectedReason: String? = null
)

data class VerificationDecisionTrace(
    val schemaVersion: Int = 1,
    val traceOrigin: String = "RECORDED",
    val ruleVersion: Int,
    val aliasVersion: Int,
    val evaluatedAt: java.time.Instant,
    val inputHash: String,
    val normalizationRef: String? = null,
    val evidenceRefs: List<String> = emptyList(),
    val total: Int,
    val truncated: Boolean,
    val candidates: List<CandidateDecisionTrace>
) {
    fun bounded(): VerificationDecisionTrace = copy(candidates = candidates.take(200),
        truncated = truncated || candidates.size > 200 || total > 200)
}

data class WorkerDiagnostics(
    val schemaVersion: Int = 1,
    val queries: List<WorkerQueryDiagnostic> = emptyList(),
    val detailReuses: List<WorkerDetailReuse> = emptyList(),
    val totalQueryCount: Int = 0, val totalReused: Int = 0, val truncated: Boolean = false,
    val decision: VerificationDecisionTrace? = null
) {
    fun bounded(): WorkerDiagnostics = copy(queries = queries.take(32), detailReuses = detailReuses.take(200),
        totalQueryCount = maxOf(totalQueryCount, queries.size), totalReused = maxOf(totalReused, detailReuses.size),
        truncated = truncated || queries.size > 32 || detailReuses.size > 200 || totalQueryCount > 32 || totalReused > 200,
        decision = decision?.bounded())

    fun reuse(value: WorkerDetailReuse): WorkerDiagnostics = copy(
        detailReuses = if (detailReuses.size < 200) detailReuses + value else detailReuses,
        totalReused = totalReused + 1, truncated = truncated || totalReused >= 200).bounded()
}

internal object VerificationDiagnostics {
    const val ERROR_LIMIT = 16 * 1024
    private val mapper = jacksonObjectMapper()
    private val email = Regex("[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9.-]+", RegexOption.IGNORE_CASE)
    private val phone = Regex("(?<![A-Za-z0-9])\\+?\\d[\\d ()-]{6,}\\d(?![A-Za-z0-9])")
    private val bearer = Regex("\\bBearer\\s+[^\\s,;\\\"']+", RegexOption.IGNORE_CASE)
    private val secret = Regex("""\b([A-Za-z0-9_-]*(?:key|token|password)[A-Za-z0-9_-]*["']?\s*[:=]\s*)("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^\s,;"']+)""", RegexOption.IGNORE_CASE)
    private val query = Regex("([?&][^\\s=&#]+)=([^\\s&#]*)")
    fun sanitize(value: String?, limit: Int): String? = value?.let {
        val redacted = secret.replace(bearer.replace(phone.replace(email.replace(it, "[REDACTED]"), "[REDACTED]"), "Bearer [REDACTED]")) { match ->
            val secretValue = match.groupValues[2]
            val replacement = if (secretValue.first() == '"' || secretValue.first() == '\'')
                "${secretValue.first()}[REDACTED]${secretValue.first()}" else "[REDACTED]"
            match.groupValues[1] + replacement
        }
        query.replace(redacted, "$1=[REDACTED]")
            .filter { c -> !c.isISOControl() || c == '\n' || c == '\t' }.take(limit)
    }
    private fun scalar(node: JsonNode?, name: String): String? = node?.get(name)?.takeIf {
        it.isTextual || it.isNumber || it.isBoolean
    }?.asText()
    private fun errorNode(bytes: ByteArray): JsonNode? = runCatching { mapper.readTree(bytes) }
        .getOrNull()?.takeIf { it.isObject }
    private fun errorScalars(node: JsonNode?): List<String> = if (node == null) emptyList() else
        listOfNotNull(scalar(node, "error"), scalar(node, "message"), scalar(node, "error_code"), scalar(node, "code")) +
            node.get("error")?.takeIf { it.isObject }?.let {
                listOfNotNull(scalar(it, "message"), scalar(it, "error_code"), scalar(it, "code"))
            }.orEmpty()
    fun classify(provider: VerificationProvider, status: Int, bytes: ByteArray): String = when {
        status == 401 || status == 403 -> "CONFIG_AUTH"
        status == 429 -> "RATE_LIMIT"
        status >= 500 -> "PROVIDER_UNAVAILABLE"
        status == 422 && provider == VerificationProvider.APOLLO -> {
            val text = errorScalars(errorNode(bytes)).joinToString(" ")
            when {
                Regex("\\b(insufficient[ _-]+credits|not[ _-]+enough[ _-]+credits|credits[ _-]+exhausted)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "APOLLO_CREDITS_EXHAUSTED"
                Regex("\\b(invalid[ _-]+parameters?|parameter[ _-]+validation[ _-]+(?:failed|error)|validation[ _-]+error)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "REQUEST_INVALID"
                else -> "APOLLO_HTTP_422_UNCLASSIFIED"
            }
        }
        status == 400 || status == 422 -> "REQUEST_INVALID"
        status == 404 -> "EVIDENCE_MISSING"
        else -> "HTTP_ERROR"
    }
    fun fromBytes(provider: VerificationProvider, path: String, status: Int, bytes: ByteArray,
        truncated: Boolean, classification: String, requestId: String? = null,
        retryAfter: String? = null, contentType: String? = null): VerificationDiagnostic {
        val node = if (!truncated) errorNode(bytes) else null
        val nested = node?.get("error")?.takeIf { it.isObject }
        return VerificationDiagnostic(provider = provider, endpointPath = path.substringBefore('?'), httpStatus = status,
            providerCode = sanitize(scalar(node, "error_code") ?: scalar(node, "code") ?: scalar(nested, "code") ?: scalar(nested, "error_code"), 128),
            message = sanitize(scalar(node, "message") ?: scalar(node, "error") ?: scalar(nested, "message"), 1024),
            requestId = sanitize(requestId ?: scalar(node, "request_id"), 128), retryAfter = sanitize(retryAfter, 128),
            contentType = sanitize(contentType, 128), bodyBytesRead = bytes.size, bodyTruncated = truncated,
            bodySha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            hashScope = if (truncated) "PREFIX" else "FULL", classification = classification)
    }
    fun read(json: String?): VerificationDiagnostic? = runCatching {
        if (json == null || json.length > 16384) return@runCatching null
        val node = mapper.readTree(json)
        if (!node.isObject || node.path("kind").asText() != "PROVIDER_ERROR" ||
            !node.path("schemaVersion").isIntegralNumber || node.path("schemaVersion").intValue() != 1) return@runCatching null
        fun text(key: String, max: Int): String? {
            val value = node.get(key) ?: return null
            if (value.isNull) return null
            require(value.isTextual && value.textValue().length <= max)
            return sanitize(value.textValue(), max)
        }
        val provider = VerificationProvider.valueOf(requireNotNull(text("provider", 32)))
        val classification = requireNotNull(text("classification", 64))
        require(classification in setOf("CONFIG_AUTH", "RATE_LIMIT", "PROVIDER_UNAVAILABLE", "APOLLO_CREDITS_EXHAUSTED",
            "REQUEST_INVALID", "APOLLO_HTTP_422_UNCLASSIFIED", "EVIDENCE_MISSING", "HTTP_ERROR",
            "RESPONSE_TOO_LARGE", "RESPONSE_INVALID"))
        fun integer(key: String): Int? = node.get(key)?.takeUnless { it.isNull }?.let {
            require(it.isIntegralNumber && it.canConvertToInt()); it.intValue()
        }
        val hash = requireNotNull(text("bodySha256", 64))
        require(hash.matches(Regex("[a-f0-9]{64}")))
        val scope = requireNotNull(text("hashScope", 6))
        require(scope == "FULL" || scope == "PREFIX")
        val truncated = node.path("bodyTruncated")
        require(truncated.isBoolean && truncated.booleanValue() == (scope == "PREFIX"))
        val bytes = integer("bodyBytesRead")
        require(bytes == null || bytes >= 0)
        val path = requireNotNull(text("endpointPath", 512))
        require(path.startsWith("/") && '?' !in path && '#' !in path)
        val status = integer("httpStatus")
        require(status == null || status in 100..599)
        VerificationDiagnostic(provider = provider, endpointPath = path, httpStatus = status,
            providerCode = text("providerCode", 128), message = text("message", 1024),
            requestId = text("requestId", 128), retryAfter = text("retryAfter", 128), contentType = text("contentType", 128),
            bodyBytesRead = bytes, bodyTruncated = truncated.booleanValue(), bodySha256 = hash,
            hashScope = scope, classification = classification)
    }.getOrNull()
}
