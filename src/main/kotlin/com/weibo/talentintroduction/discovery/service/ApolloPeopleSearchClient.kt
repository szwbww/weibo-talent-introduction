package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.VerificationProvider
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.HttpURLConnection
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** Issued only after the caller has persisted DISPATCH_RESERVED. One reservation permits one wire request. */
class RequestPermit(val attemptId: Long, val provider: VerificationProvider) {
    private val consumed = AtomicBoolean()
    init { require(attemptId > 0) }
    internal fun consume(expected: VerificationProvider) {
        require(provider == expected && consumed.compareAndSet(false, true)) { "Invalid or already consumed request permit" }
    }
}

sealed class VerificationHttpResult<out T> {
    data class Success<T>(val value: T, val responseSha256: String, val httpStatus: Int = 200) : VerificationHttpResult<T>()
    data class RateLimited(val retryAt: Instant, val httpStatus: Int = 429, val diagnostic: VerificationDiagnostic? = null) : VerificationHttpResult<Nothing>()
    data class TransientFailure(val code: String, val httpStatus: Int? = null, val diagnostic: VerificationDiagnostic? = null) : VerificationHttpResult<Nothing>()
    data class PermanentFailure(val code: String, val httpStatus: Int? = null, val diagnostic: VerificationDiagnostic? = null) : VerificationHttpResult<Nothing>()
}

data class VerificationInstitution(val name: String, val standardId: String? = null, val aliases: Set<String> = emptySet())
data class ApolloCandidate(val id: String, val firstName: String?, val lastName: String?, val obfuscatedLastName: String?,
    val fullName: String?, val title: String?, val institution: VerificationInstitution?, val orcid: String? = null,
    val headline: String? = null, val employmentHistory: List<ApolloEmployment> = emptyList())
data class ApolloEmployment(val institution: VerificationInstitution?, val title: String?, val current: Boolean?,
    val startDate: String?, val endDate: String?, val major: String? = null)
data class ApolloSearchQuery(val name: String, val page: Int = 1, val institution: VerificationInstitution? = null) {
    init { require(name.isNotBlank() && name.length <= 300 && page in 1..500) }
}
data class ApolloSearchPage(val candidates: List<ApolloCandidate>, val totalEntries: Long, val page: Int) {
    val hasNext: Boolean get() = page.toLong() * 100 < totalEntries
}

internal fun verificationRestTemplate(properties: DiscoveryVerificationProperties): RestTemplate {
    val factory = object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }
    factory.setConnectTimeout(properties.httpConnectTimeout.toMillis().toInt())
    factory.setReadTimeout(properties.httpReadTimeout.toMillis().toInt())
    return RestTemplate(factory).also { template ->
        template.errorHandler = object : org.springframework.web.client.ResponseErrorHandler {
            override fun hasError(response: org.springframework.http.client.ClientHttpResponse) = false
            override fun handleError(response: org.springframework.http.client.ClientHttpResponse) = Unit
        }
    }
}

/** Shared bounded transport. It never retries, follows redirects, logs bodies, or returns provider error text. */
internal class VerificationHttp(private val http: RestTemplate, private val clock: Clock) {
    private val mapper = jacksonObjectMapper()
    fun request(uri: URI, method: HttpMethod, headers: Map<String, String>, permit: RequestPermit,
        provider: VerificationProvider): VerificationHttpResult<JsonNode> {
        permit.consume(provider)
        return try {
            http.execute(uri, method, { request -> headers.forEach { (key, value) -> request.headers.set(key, value) } }, { response ->
                val status = response.rawStatusCode
                if (status !in 200..299) {
                    val bytes = response.body.readNBytes(VerificationDiagnostics.ERROR_LIMIT + 1)
                    val code = VerificationDiagnostics.classify(provider, status, bytes)
                    val diagnostic = VerificationDiagnostics.fromBytes(provider, uri.path, status, bytes,
                        bytes.size > VerificationDiagnostics.ERROR_LIMIT, code, response.headers.getFirst("X-Request-Id"),
                        response.headers.getFirst("Retry-After"), response.headers.getFirst("Content-Type"))
                    when {
                        status == 429 -> {
                            val raw = response.headers.getFirst("Retry-After")
                            val retryAt = raw?.toLongOrNull()?.takeIf { it >= 0 && it <= 31536000 }?.let { clock.instant().plusSeconds(it) }
                                ?: runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
                                ?: clock.instant().plusSeconds(60)
                            VerificationHttpResult.RateLimited(maxOf(clock.instant(), retryAt), status, diagnostic)
                        }
                        status >= 500 -> VerificationHttpResult.TransientFailure(code, status, diagnostic)
                        else -> VerificationHttpResult.PermanentFailure(code, status, diagnostic)
                    }
                } else {
                    val bytes = response.body.readNBytes(2 * 1024 * 1024 + 1)
                    val node = if (bytes.size <= 2 * 1024 * 1024) runCatching { mapper.readTree(bytes) }.getOrNull() else null
                    if (node != null && node.isObject) VerificationHttpResult.Success(node,
                        com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(String(bytes, Charsets.UTF_8)), status)
                    else {
                        val code = if (bytes.size > 2 * 1024 * 1024) "RESPONSE_TOO_LARGE" else "RESPONSE_INVALID"
                        VerificationHttpResult.PermanentFailure(code, status, VerificationDiagnostics.fromBytes(
                            provider, uri.path, status, bytes, bytes.size > 2 * 1024 * 1024, code,
                            contentType = response.headers.getFirst("Content-Type")))
                    }
                }
            }) ?: VerificationHttpResult.PermanentFailure("RESPONSE_INVALID")
        } catch (_: org.springframework.web.client.ResourceAccessException) {
            VerificationHttpResult.TransientFailure("NETWORK_OR_TIMEOUT")
        } catch (_: org.springframework.web.client.RestClientException) {
            VerificationHttpResult.PermanentFailure("RESPONSE_INVALID")
        }
    }
}

@Service
class ApolloPeopleSearchClient(
    private val properties: DiscoveryVerificationProperties,
    http: RestTemplate, clock: Clock = Clock.systemUTC()
) {
    @org.springframework.beans.factory.annotation.Autowired
    constructor(properties: DiscoveryVerificationProperties) : this(properties, verificationRestTemplate(properties), Clock.systemUTC())
    private val transport = VerificationHttp(http, clock)
    /** Read-only person detail. Keep only matching evidence; never retain revealed contact information. */
    fun detail(id: String, permit: RequestPermit): VerificationHttpResult<ApolloCandidate> {
        require(properties.apolloApiKey.isNotBlank() && id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val uri = UriComponentsBuilder.fromHttpUrl("https://api.apollo.io/api/v1/people")
            .pathSegment(id).build().toUri()
        val result = transport.request(uri, HttpMethod.GET,
            mapOf("X-Api-Key" to properties.apolloApiKey, "Accept" to "application/json"), permit, VerificationProvider.APOLLO)
        if (result !is VerificationHttpResult.Success) return result as VerificationHttpResult<Nothing>
        val person = result.value.path("person")
        if (!person.isObject || text(person, "id") != id ||
            listOf("first_name", "last_name", "name", "title", "headline", "orcid").any {
                person.hasNonNull(it) && !person.path(it).isTextual
            }) return invalid(result, uri.path)
        val history = person.path("employment_history")
        if (person.hasNonNull("employment_history") && !history.isArray ||
            history.size() > 200 || history.any { !it.isObject ||
                it.hasNonNull("current") && !it.path("current").isBoolean })
            return invalid(result, uri.path)
        val candidate = ApolloCandidate(id, text(person, "first_name"), text(person, "last_name"),
            text(person, "last_name_obfuscated"), text(person, "name"), text(person, "title"),
            text(person.path("organization"), "name")?.let(::VerificationInstitution), text(person, "orcid"),
            text(person, "headline"), history.map { job ->
                ApolloEmployment(text(job, "organization_name")?.let(::VerificationInstitution),
                    text(job, "title"), job.path("current").takeIf { it.isBoolean }?.booleanValue(),
                    text(job, "start_date"), text(job, "end_date"), text(job, "major"))
            })
        return VerificationHttpResult.Success(candidate, result.responseSha256, result.httpStatus)
    }
    fun search(query: ApolloSearchQuery, permit: RequestPermit): VerificationHttpResult<ApolloSearchPage> {
        require(properties.apolloApiKey.isNotBlank()) { "Apollo configuration unavailable" }
        val builder = UriComponentsBuilder.fromHttpUrl("https://api.apollo.io/api/v1/mixed_people/api_search")
            .queryParam("q_person_name", "{name}").queryParam("page", query.page).queryParam("per_page", 100)
        val variables = mutableMapOf<String, String>("name" to query.name)
        query.institution?.let {
            require(it.name.isNotBlank())
            builder.queryParam("q_organization_name", "{institution}")
            variables["institution"] = it.name
        }
        val result = transport.request(builder.encode().buildAndExpand(variables).toUri(), HttpMethod.POST,
            mapOf("X-Api-Key" to properties.apolloApiKey, "Accept" to "application/json"), permit, VerificationProvider.APOLLO)
        if (result !is VerificationHttpResult.Success) return result as VerificationHttpResult<Nothing>
        val root = result.value
        val people = root.path("people")
        val total = root.path("total_entries")
        if (!people.isArray || !total.isIntegralNumber || !total.canConvertToLong() || total.longValue() < 0 || people.size() > 100)
            return invalid(result, "/api/v1/mixed_people/api_search")
        val candidates = ArrayList<ApolloCandidate>()
        for (person in people) {
            if (!person.isObject || !person.path("id").isTextual || person.path("id").asText().isBlank() || person.path("id").asText().length > 128)
                return invalid(result, "/api/v1/mixed_people/api_search")
            val fields = listOf("first_name", "last_name", "last_name_obfuscated", "name", "title")
            if (fields.any { person.hasNonNull(it) && !person.path(it).isTextual }) return invalid(result, "/api/v1/mixed_people/api_search")
            val org = person.path("organization")
            if (!org.isMissingNode && !org.isNull && !org.isObject) return invalid(result, "/api/v1/mixed_people/api_search")
            candidates += ApolloCandidate(person.path("id").asText(), text(person, "first_name"), text(person, "last_name"),
                text(person, "last_name_obfuscated"), text(person, "name"), text(person, "title"),
                text(org, "name")?.let { VerificationInstitution(it) }, text(person, "orcid"))
        }
        if (total.longValue() < candidates.size) return invalid(result, "/api/v1/mixed_people/api_search")
        return VerificationHttpResult.Success(ApolloSearchPage(candidates, total.longValue(), query.page), result.responseSha256, result.httpStatus)
    }
    private fun invalid(result: VerificationHttpResult.Success<JsonNode>, path: String) =
        VerificationHttpResult.PermanentFailure("RESPONSE_INVALID", result.httpStatus, VerificationDiagnostic(
            provider = VerificationProvider.APOLLO, endpointPath = path, httpStatus = result.httpStatus,
            bodySha256 = result.responseSha256, hashScope = "FULL", classification = "RESPONSE_INVALID"))
    private fun text(node: JsonNode, field: String): String? = node.path(field).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.take(4096)
}
