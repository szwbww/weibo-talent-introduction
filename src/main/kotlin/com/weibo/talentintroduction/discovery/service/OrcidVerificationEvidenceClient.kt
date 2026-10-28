package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Clock
import java.time.Instant

/** Constructed from the original profile, not from an Apollo hit or a same-name ORCID search. */
data class VerificationSnapshot(val identityHash: String, val factFingerprint: String, val profile: ExpertProfile) {
    init { verificationHash(identityHash); verificationHash(factFingerprint) }
    val identityValid: Boolean get() = profile.identityVerification?.status == "VERIFIED" && DiscoveryIdentity.allowed(profile)
    val fullName: String get() = listOfNotNull(profile.givenNames, profile.familyNames).joinToString(" ")
    val boundOrcid: String? get() {
        if (!identityValid) return null
        val proofId = normalizeOrcid(profile.identityVerification?.orcid) ?: return null
        val profileId = normalizeOrcid(profile.orcidId)
        val externalNode = profile.externalIds?.let {
            runCatching { com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(it) }.getOrNull() ?: return null
        }
        val external = externalNode?.path("orcid")?.takeIf { !it.isMissingNode && !it.isNull }?.let {
            if (!it.isTextual) return null
            normalizeOrcid(it.asText()) ?: return null
        }
        if ((profileId != null && profileId != proofId) || (external != null && external != proofId)) return null
        return proofId
    }
}

fun normalizeOrcid(value: String?): String? {
    val id = value?.removePrefix("https://orcid.org/")?.removePrefix("http://orcid.org/") ?: return null
    if (!id.matches(Regex("[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X]"))) return null
    val digits = id.replace("-", "")
    var total = 0
    digits.take(15).forEach { total = (total + (it - '0')) * 2 }
    val check = (12 - total % 11) % 11
    return id.takeIf { digits.last() == if (check == 10) 'X' else ('0'.code + check).toChar() }
}

data class OrcidEvidenceDocument(val endpoint: String, val body: JsonNode, val responseSha256: String, val fetchedAt: Instant)
data class OrcidEvidenceRead(val documents: List<OrcidEvidenceDocument>, val complete: Boolean,
    val reasonCodes: List<String>, val failure: VerificationHttpResult<Nothing>? = null)

/** Durable, bounded endpoint result. Missing public records are completed endpoints too. */
data class OrcidEndpointCompletion(val endpoint: String, val document: OrcidEvidenceDocument? = null)

@Service
class OrcidVerificationEvidenceClient(private val properties: DiscoveryVerificationProperties,
    http: RestTemplate, private val clock: Clock = Clock.systemUTC()) {
    @org.springframework.beans.factory.annotation.Autowired
    constructor(properties: DiscoveryVerificationProperties) : this(properties, verificationRestTemplate(properties), Clock.systemUTC())
    private val transport = VerificationHttp(http, clock)

    /** Existing callers retain aggregate semantics; durable callers acknowledge each response before another request. */
    fun read(snapshot: VerificationSnapshot, reserve: (String) -> RequestPermit?): OrcidEvidenceRead =
        read(snapshot, emptyList(), { _, _, _, _ -> }, reserve = reserve)

    fun read(snapshot: VerificationSnapshot, completed: List<OrcidEndpointCompletion>,
        onResponse: (String, RequestPermit, OrcidEvidenceDocument?, VerificationHttpResult<*>) -> Unit,
        beforeDispatch: (() -> Unit)? = null,
        reserve: (String) -> RequestPermit?): OrcidEvidenceRead {
        val orcid = snapshot.boundOrcid ?: return OrcidEvidenceRead(emptyList(), false, listOf("IDENTITY_EVIDENCE_MISSING"))
        val documents = ArrayList<OrcidEvidenceDocument>()
        val endpoints = java.util.ArrayDeque(listOf("person", "employments", "educations"))
        require(completed.map { it.endpoint }.distinct().size == completed.size)
        completed.forEach { require(allowedEndpoint(it.endpoint) && (it.document == null || it.document.endpoint == it.endpoint)) }
        val saved = completed.associateBy { it.endpoint }
        var requests = 0
        while (endpoints.isNotEmpty()) {
            val endpoint = endpoints.removeFirst()
            require(allowedEndpoint(endpoint))
            val cached = saved[endpoint]
            val doc: OrcidEvidenceDocument?
            if (cached != null) {
                doc = cached.document
            } else {
                if (requests + completed.size >= minOf(12, properties.maxRequestsPerItem))
                    return OrcidEvidenceRead(documents, false, listOf("EVIDENCE_INCOMPLETE"))
                val path = "/$orcid/$endpoint"
                val permit = reserve(path) ?: return OrcidEvidenceRead(documents, false, listOf("EVIDENCE_INCOMPLETE"))
                val headers = mutableMapOf("Accept" to "application/json")
                if (properties.orcidAccessToken.isNotBlank()) headers["Authorization"] = "Bearer ${properties.orcidAccessToken}"
                requests++
                beforeDispatch?.invoke()
                val result = transport.request(URI(properties.orcidPublicBaseUrl.trimEnd('/') + path), HttpMethod.GET, headers, permit, VerificationProvider.ORCID)
                doc = (result as? VerificationHttpResult.Success)?.let {
                    OrcidEvidenceDocument(endpoint, selectedFields(it.value), it.responseSha256, clock.instant())
                }
                // A failed acknowledgement throws out of this method: never issue the next HTTP request.
                onResponse(endpoint, permit, doc, result)
                when (result) {
                    is VerificationHttpResult.Success -> Unit
                    is VerificationHttpResult.PermanentFailure -> if (result.code != "EVIDENCE_MISSING")
                        return OrcidEvidenceRead(documents, false, listOf(result.code), result)
                    else -> return OrcidEvidenceRead(documents, false, listOf("INTERFACE_UNAVAILABLE"), result as VerificationHttpResult<Nothing>)
                }
            }
            if (doc != null) {
                documents += doc
                if (endpoint == "employments" || endpoint == "educations") {
                    val kind = if (endpoint == "employments") "employment" else "education"
                    val groups = doc.body.path("affiliation-group")
                    if (!groups.isMissingNode && !groups.isArray) return OrcidEvidenceRead(documents, false, listOf("RESPONSE_INVALID"), VerificationHttpResult.PermanentFailure("RESPONSE_INVALID"))
                    groups.forEach { group -> group.path("summaries").forEach { wrapper ->
                        val summary = wrapper.path("$kind-summary")
                        val putCode = summary.path("put-code")
                        if (putCode.isIntegralNumber && putCode.canConvertToLong() && putCode.longValue() > 0 &&
                            (!summary.has("source") || !summary.has("start-date"))) {
                            val detail = "$kind/${putCode.longValue()}"
                            if (!endpoints.contains(detail) && documents.none { it.endpoint == detail }) endpoints.add(detail)
                        }
                    } }
                }
            }
        }
        return OrcidEvidenceRead(documents, true, if (documents.isEmpty()) listOf("EVIDENCE_MISSING") else emptyList())
    }

    private val endpointPattern = Regex("person|employments|educations|employment/[0-9]+|education/[0-9]+")
    private fun allowedEndpoint(endpoint: String) = endpoint.matches(endpointPattern)
    private val retainedFields = setOf("name", "given-names", "family-name", "value", "other-names", "other-name", "content",
        "affiliation-group", "summaries", "employment-summary", "education-summary", "put-code", "source",
        "source-client-id", "source-orcid", "path", "visibility", "last-modified-date", "start-date", "end-date",
        "year", "month", "day", "organization", "role-title", "disambiguated-organization",
        "disambiguated-organization-identifier", "disambiguation-source")

    /** Retain parser inputs and detail cursor only, not biographies, emails, URLs or raw provider records. */
    private fun selectedFields(node: JsonNode): JsonNode {
        return when {
            node.isObject -> com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().also { result ->
                node.fields().forEachRemaining { (key, value) -> if (key in retainedFields) result.set<JsonNode>(key, selectedFields(value)) }
            }
            node.isArray -> com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode().also { result -> node.forEach { result.add(selectedFields(it)) } }
            node.isTextual -> com.fasterxml.jackson.databind.node.TextNode(node.asText().take(500))
            else -> node.deepCopy()
        }
    }
}
