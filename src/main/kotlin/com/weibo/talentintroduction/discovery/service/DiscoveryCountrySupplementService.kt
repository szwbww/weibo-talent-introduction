package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.INSTITUTION_SOURCE_OPENALEX
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.HttpURLConnection
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.ArrayDeque
import java.util.Locale

/**
 * One auditable country observation. Only the bounded fields needed to re-verify an institution/country
 * decision are retained; personal addresses, e-mails, tokens and whole provider responses never are.
 */
data class CountrySupplementObservation(
    val source: String,
    val sourceRef: String,
    val institution: String,
    val country: String? = null,
    val inputSha256: String,
    val fetchedAt: String? = null,
    val publicationYear: Int? = null,
    val endpoint: String? = null,
    val putCode: Long? = null,
    val jsonPointer: String? = null,
    val sourceCategory: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val responseSha256: String? = null
)

/**
 * `COUNTRY_V1` audit receipt persisted as the optional top-level `snapshot_json.countrySupplement`.
 * It is audit metadata only: it is never handed to the qualification rules and never creates an
 * Apollo/ORCID verification attempt.
 */
data class CountrySupplementReceipt(
    val version: String = DiscoveryCountrySupplementService.VERSION,
    val status: String,
    val reason: String,
    val beforeCountry: String? = null,
    val afterCountry: String? = null,
    val originalInstitutionEvidence: String? = null,
    val institution: String? = null,
    val selectedSource: String? = null,
    val constructedAt: String,
    val observations: List<CountrySupplementObservation> = emptyList()
)

data class CountrySupplementResult(
    val profile: ExpertProfile,
    val receipt: CountrySupplementReceipt?,
    val httpRequests: Int
)

/** Request permit outcome. The caller skips immediately instead of waiting for budget. */
internal enum class CountryPermit { ACQUIRED, BUSY, PAUSED, BUDGET }

/** Dedicated transport for the single bounded public ORCID read; never redirects and never retries. */
internal fun countrySupplementRestTemplate(): RestTemplate {
    val factory = object : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }
    }
    factory.setConnectTimeout(DiscoveryCountrySupplementService.CONNECT_TIMEOUT_MS)
    factory.setReadTimeout(DiscoveryCountrySupplementService.READ_TIMEOUT_MS)
    return RestTemplate(factory).also { template ->
        template.errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse) = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }
}

/**
 * Selects an institution country for a brand-new discovery registration (I-1/I-4/I-5).
 *
 * Pure selection consumes the existing [DiscoveryInstitutionNormalizer] and
 * [QualificationEvidenceParser] read-only. The only network call is one bounded
 * `GET {orcidPublicBaseUrl}/{boundOrcid}/employments`; the OpenAlex path never issues HTTP.
 */
class DiscoveryCountrySupplementService private constructor(
    private val properties: DiscoveryVerificationProperties,
    private val clock: Clock,
    private val http: RestTemplate?,
    private val parser: QualificationEvidenceParser,
    @Suppress("UNUSED_PARAMETER") marker: Unit
) {
    @Autowired
    constructor(properties: DiscoveryVerificationProperties) :
        this(properties, Clock.systemUTC(), null, QualificationEvidenceParser(), Unit)

    constructor(properties: DiscoveryVerificationProperties, clock: Clock,
                http: RestTemplate? = null, parser: QualificationEvidenceParser? = null) :
        this(properties, clock, http, parser ?: QualificationEvidenceParser(clock), Unit)

    private val log = LoggerFactory.getLogger(DiscoveryCountrySupplementService::class.java)
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val client: RestTemplate by lazy { http ?: countrySupplementRestTemplate() }

    private val permitLock = Any()
    private var inFlight = false
    private val minuteWindow = ArrayDeque<Instant>()
    private val hourWindow = ArrayDeque<Instant>()
    private var pausedUntil: Instant? = null

    /**
     * Returns the profile that registration must freeze. The profile differs from [profile] only in
     * `country` and the re-signed `institutionEvidence`, or is returned unchanged with an audit receipt.
     */
    fun supplement(executionId: Long?, docId: String, profile: ExpertProfile, paper: PaperMetadata?): CountrySupplementResult {
        if (!properties.countrySupplementEnabled) return CountrySupplementResult(profile, null, 0)
        val started = System.nanoTime()
        var requests = 0
        val outcome = try {
            select(profile, paper).also { requests = it.requests }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } catch (e: Exception) {
            if (isCancellation(e)) {
                Thread.currentThread().interrupt()
                throw InterruptedException("Country supplement cancelled")
            }
            Outcome(null, null, REASON_HTTP_ERROR, emptyList())
        }
        var effective = outcome
        if (effective.observations.size > MAX_OBSERVATIONS || effective.observations.any { it.institution.length > MAX_INSTITUTION_LENGTH }) {
            effective = Outcome(null, null, REASON_EVIDENCE_TOO_LARGE, effective.observations.take(MAX_OBSERVATIONS))
        }
        var finalProfile = profile
        val selectedSource = effective.source
        if (effective.country != null && selectedSource != null) {
            val signed = DiscoveryIdentity.institutionEvidence(profile.copy(country = effective.country), selectedSource)
            if (signed == null) {
                effective = Outcome(null, null, REASON_IDENTITY_UNRESOLVED, effective.observations)
            } else {
                finalProfile = profile.copy(country = effective.country, institutionEvidence = signed)
            }
        }
        check(finalProfile.copy(country = profile.country, institutionEvidence = profile.institutionEvidence) == profile) {
            "Country supplement may change only country and institutionEvidence"
        }
        val receipt = CountrySupplementReceipt(
            status = statusOf(effective.reason),
            reason = effective.reason,
            beforeCountry = profile.country?.takeIf { it.isNotBlank() },
            afterCountry = finalProfile.country,
            originalInstitutionEvidence = profile.institutionEvidence,
            institution = profile.institution,
            selectedSource = if (finalProfile.country != profile.country) effective.source else null,
            constructedAt = clock.instant().toString(),
            observations = effective.observations.take(MAX_OBSERVATIONS)
        )
        log.info(
            "event=DISCOVERY_COUNTRY_SUPPLEMENT executionId={} docId={} source={} status={} reason={} httpRequests={} elapsedMs={}",
            executionId, docId, effective.source ?: "NONE", receipt.status, receipt.reason, requests,
            (System.nanoTime() - started) / 1_000_000
        )
        return CountrySupplementResult(finalProfile, receipt, requests)
    }

    // ------------------------------------------------------------------
    // Pure selection
    // ------------------------------------------------------------------

    private data class Outcome(
        val country: String?,
        val source: String?,
        val reason: String,
        val observations: List<CountrySupplementObservation>,
        val requests: Int = 0
    )

    private fun select(profile: ExpertProfile, paper: PaperMetadata?): Outcome {
        if (profile.country?.isNotBlank() == true) return Outcome(null, null, REASON_EXISTING_VALUE, emptyList())
        if (profile.institution.isNullOrBlank()) return Outcome(null, null, REASON_NO_EVIDENCE, emptyList())
        val source = profile.institutionEvidence?.substringBefore(':')
            ?.takeIf { it in setOf(DiscoveryIdentity.EVIDENCE_SOURCE_JATS, DiscoveryIdentity.EVIDENCE_SOURCE_OPENALEX, DiscoveryIdentity.EVIDENCE_SOURCE_ORCID) }
            ?: return Outcome(null, null, REASON_IDENTITY_UNRESOLVED, emptyList())
        if (!DiscoveryIdentity.validInstitutionEvidence(profile)) {
            return Outcome(null, null, REASON_IDENTITY_UNRESOLVED, emptyList())
        }
        val openAlex = openAlex(profile, paper)
        if (openAlex != null && (openAlex.country != null || openAlex.reason != REASON_NO_EVIDENCE)) return openAlex
        val fallback = orcid(profile)
        return if (openAlex == null) fallback
        else fallback.copy(observations = openAlex.observations + fallback.observations)
    }

    /**
     * OpenAlex authorships already carry the structured institution and its country. The bound author ID
     * must be verified and unique; a different complete institution name or conflicting ORCID terminates.
     */
    private fun openAlex(profile: ExpertProfile, paper: PaperMetadata?): Outcome? {
        if (paper?.source != "OPENALEX") return null
        val bound = normalizedAuthorId(profile.externalIds?.let { runCatching { mapper.readTree(it) }.getOrNull() }?.path("openAlexAuthorId")?.asText(null))
        if (bound == null || !bound.equals(normalizedAuthorId(profile.identityVerification?.openAlexAuthorId), ignoreCase = true)) return null
        val matches = paper.authors.filter { normalizedAuthorId(it.openAlexAuthorId)?.equals(bound, ignoreCase = true) == true }
        if (matches.isEmpty()) return Outcome(null, null, REASON_NO_EVIDENCE, emptyList())
        val id = paper.doi?.let { "doi:$it" } ?: paper.pmcId?.let { "pmc:$it" } ?: paper.pmid?.let { "pmid:$it" } ?: "paper:${paper.title}"
        val observations = matches.map { author ->
            val institution = author.institutionName.orEmpty().trim()
            val country = author.institutionCountry?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            CountrySupplementObservation(
                source = SOURCE_OPENALEX,
                sourceRef = "$id#author=$bound",
                institution = institution,
                country = country,
                inputSha256 = sha256(listOf(SOURCE_OPENALEX, "$id#author=$bound", institution, country.orEmpty()).joinToString("\u0000")),
                fetchedAt = null,
                publicationYear = paper.pubYear
            )
        }.distinctBy { listOf(it.sourceRef, it.institution, it.country) }
        val profileOrcids = profileOrcidIds(profile)
        for (author in matches) {
            val authorOrcid = normalizeOrcid(author.orcidId) ?: continue
            if (profileOrcids.isNotEmpty() && authorOrcid !in profileOrcids) {
                return Outcome(null, null, REASON_IDENTITY_UNRESOLVED, observations)
            }
        }
        val institutions = matches.mapNotNull { it.institutionName?.trim()?.takeIf(String::isNotEmpty) }.distinctBy(::canonical)
        if (institutions.size > 1) return Outcome(null, null, REASON_INSTITUTION_MISMATCH, observations)
        val structured = matches.filter { it.institutionSource == INSTITUTION_SOURCE_OPENALEX }
        if (structured.isEmpty() || institutions.isEmpty()) return Outcome(null, null, REASON_NO_EVIDENCE, observations)
        val institution = institutions.single()
        if (canonical(institution) != canonical(profile.institution!!) ||
            !resolvedInstitution(profile.institution!!, InstitutionSource.PROFILE) ||
            !resolvedInstitution(institution, InstitutionSource.PROFILE)
        ) {
            return Outcome(null, null, REASON_INSTITUTION_MISMATCH, observations)
        }
        val rawCountries = structured.map { it.institutionCountry?.trim()?.takeIf(String::isNotEmpty) }
        val countries = rawCountries.mapNotNull(::validCountry).distinct()
        if (rawCountries.any { it == null }) return Outcome(null, null, REASON_NO_EVIDENCE, observations)
        if (countries.size != rawCountries.size) return Outcome(null, null, REASON_INVALID_COUNTRY, observations)
        if (countries.size > 1) return Outcome(null, null, REASON_MULTIPLE_COUNTRIES, observations)
        if (countries.isEmpty()) return Outcome(null, null, REASON_NO_EVIDENCE, observations)
        return Outcome(countries.single(), SOURCE_OPENALEX, REASON_MATCHED_BOUND_INSTITUTION, observations)
    }

    /** At most one bounded public employments read; no person/education/detail and no retry. */
    private fun orcid(profile: ExpertProfile): Outcome {
        val orcid = boundOrcid(profile) ?: return Outcome(null, null, REASON_NO_EVIDENCE, emptyList())
        val permit = tryAcquirePermit()
        if (permit != CountryPermit.ACQUIRED) {
            return when (permit) {
                CountryPermit.PAUSED -> Outcome(null, null, REASON_RATE_LIMITED, emptyList())
                else -> Outcome(null, null, REASON_BUDGET_EXHAUSTED, emptyList())
            }
        }
        try {
            val uri = URI("${properties.orcidPublicBaseUrl.trimEnd('/')}/$orcid/employments")
            val headers = mutableMapOf("Accept" to "application/json")
            if (properties.orcidAccessToken.isNotBlank()) headers["Authorization"] = "Bearer ${properties.orcidAccessToken}"
            val response = client.execute(uri, HttpMethod.GET, { request ->
                headers.forEach { (key, value) -> request.headers.set(key, value) }
            }, { result -> readBounded(result) })
            val status = response.status
            if (status == 429) {
                pauseFor(response.retryAfterSeconds)
                return Outcome(null, null, REASON_RATE_LIMITED, emptyList(), 1)
            }
            if (status !in 200..299) return Outcome(null, null, REASON_HTTP_ERROR, emptyList(), 1)
            if (response.oversize) return Outcome(null, null, REASON_EVIDENCE_TOO_LARGE, emptyList(), 1)
            val bytes = response.body ?: return Outcome(null, null, REASON_INVALID_RESPONSE, emptyList(), 1)
            val body = try { mapper.readTree(bytes) } catch (_: Exception) {
                return Outcome(null, null, REASON_INVALID_RESPONSE, emptyList(), 1)
            }
            if (!body.isObject) return Outcome(null, null, REASON_INVALID_RESPONSE, emptyList(), 1)
            val document = OrcidEvidenceDocument("employments", retainCountryFields(body), sha256(bytes), clock.instant())
            return selectOrcidEmployment(profile, document, orcid)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); throw e
        } catch (e: Exception) {
            if (isCancellation(e)) { Thread.currentThread().interrupt(); throw InterruptedException("Country supplement cancelled") }
            return Outcome(null, null, REASON_HTTP_ERROR, emptyList(), 1)
        } finally {
            releasePermit()
        }
    }

    private fun selectOrcidEmployment(profile: ExpertProfile, document: OrcidEvidenceDocument, orcid: String): Outcome {
        val provisional = VerificationSnapshot("0".repeat(64), "0".repeat(64), profile)
        val facts = parser.parse(provisional, OrcidEvidenceRead(listOf(document), true, emptyList()))
        val matched = facts.filter { fact ->
            fact.field == "employment" && fact.temporalScope == com.weibo.talentintroduction.discovery.domain.TemporalScope.CURRENT &&
                fact.assertionSource in setOf(com.weibo.talentintroduction.discovery.domain.AssertionSource.ORGANIZATION,
                    com.weibo.talentintroduction.discovery.domain.AssertionSource.SELF) &&
                fact.conflicts.isEmpty() && institutionOf(fact.value)?.let {
                    canonical(it) == canonical(profile.institution!!) && resolvedInstitution(it, InstitutionSource.ORCID_EMPLOYMENT) &&
                        resolvedInstitution(profile.institution!!, InstitutionSource.PROFILE)
                } == true
        }
        if (matched.isEmpty()) return Outcome(null, null, REASON_NO_EVIDENCE, emptyList(), 1)
        val observations = matched.map { fact ->
            val pointer = fact.jsonPointer.orEmpty()
            val summary = if (pointer.isBlank()) null else runCatching { document.body.at(pointer) }.getOrNull()
            val country = summary?.path("organization")?.path("address")?.path("country")?.path("value")?.asText(null)
                ?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            val putCode = summary?.path("put-code")?.takeIf { it.isIntegralNumber && it.canConvertToLong() }?.longValue()
            val start = summary?.path("start-date")?.takeIf { !it.isMissingNode && !it.isNull }?.let { dateText(it) }
            val end = summary?.path("end-date")?.takeIf { !it.isMissingNode && !it.isNull }?.let { dateText(it) }
            CountrySupplementObservation(
                source = SOURCE_ORCID,
                sourceRef = "https://pub.orcid.org/v3.0/$orcid/employments#put-code=${putCode ?: "-"}",
                institution = institutionOf(fact.value).orEmpty(),
                country = country,
                inputSha256 = sha256(listOf(SOURCE_ORCID, "employments", pointer, institutionOf(fact.value).orEmpty(), country.orEmpty()).joinToString("\u0000")),
                fetchedAt = document.fetchedAt.toString(),
                endpoint = "employments",
                putCode = putCode,
                jsonPointer = pointer,
                sourceCategory = fact.assertionSource.name,
                startDate = start,
                endDate = end,
                responseSha256 = document.responseSha256
            )
        }
        if (observations.any { it.country != null && validCountry(it.country) == null }) {
            return Outcome(null, null, REASON_INVALID_COUNTRY, observations, 1)
        }
        if (observations.any { it.country == null }) return Outcome(null, null, REASON_NO_EVIDENCE, observations, 1)
        val countries = observations.mapNotNull { it.country }.distinct()
        if (countries.size != observations.size) return Outcome(null, null, REASON_INVALID_COUNTRY, observations, 1)
        if (countries.size > 1) return Outcome(null, null, REASON_MULTIPLE_COUNTRIES, observations, 1)
        return Outcome(countries.single(), SOURCE_ORCID, REASON_MATCHED_BOUND_INSTITUTION, observations, 1)
    }

    // ------------------------------------------------------------------
    // Bounded request machinery
    // ------------------------------------------------------------------

    private data class BoundedResponse(val status: Int, val retryAfterSeconds: Long?, val body: ByteArray?, val oversize: Boolean)

    private fun readBounded(response: ClientHttpResponse): BoundedResponse {
        val status = response.rawStatusCode
        val retryAfter = response.headers.getFirst("Retry-After")?.toLongOrNull()?.takeIf { it >= 0 && it <= 31536000 }
        val body = response.body.use { stream ->
            val buffer = ByteArray(MAX_BODY_BYTES + 1)
            var read = 0
            while (read < buffer.size) {
                val next = stream.read(buffer, read, buffer.size - read)
                if (next < 0) break
                read += next
            }
            if (read > MAX_BODY_BYTES) null else buffer.copyOf(read)
        }
        return BoundedResponse(status, retryAfter, body, body == null && status in 200..299)
    }

    internal fun tryAcquirePermit(): CountryPermit = synchronized(permitLock) {
        val now = clock.instant()
        pausedUntil?.let {
            if (now.isBefore(it)) return CountryPermit.PAUSED else pausedUntil = null
        }
        if (inFlight) return CountryPermit.BUSY
        while (minuteWindow.isNotEmpty() && !Duration.between(minuteWindow.first(), now).minusSeconds(60).isNegative) {
            minuteWindow.removeFirst()
        }
        while (hourWindow.isNotEmpty() && !Duration.between(hourWindow.first(), now).minusSeconds(3600).isNegative) {
            hourWindow.removeFirst()
        }
        if (minuteWindow.size >= PER_MINUTE || hourWindow.size >= PER_HOUR) return CountryPermit.BUDGET
        inFlight = true
        minuteWindow.addLast(now)
        hourWindow.addLast(now)
        CountryPermit.ACQUIRED
    }

    internal fun releasePermit() = synchronized(permitLock) { inFlight = false }

    private fun pauseFor(seconds: Long?) {
        val pause = (seconds ?: DEFAULT_PAUSE_SECONDS).coerceIn(0, 31536000)
        synchronized(permitLock) { pausedUntil = clock.instant().plusSeconds(pause) }
    }

    // ------------------------------------------------------------------
    // Small pure helpers
    // ------------------------------------------------------------------

    private fun statusOf(reason: String): String = when (reason) {
        REASON_MATCHED_BOUND_INSTITUTION -> STATUS_FILLED
        REASON_EXISTING_VALUE -> STATUS_UNCHANGED
        REASON_MULTIPLE_COUNTRIES, REASON_INVALID_COUNTRY, REASON_INSTITUTION_MISMATCH -> STATUS_CONFLICT
        REASON_HTTP_ERROR, REASON_INVALID_RESPONSE -> STATUS_ERROR
        REASON_RATE_LIMITED, REASON_BUDGET_EXHAUSTED, REASON_EVIDENCE_TOO_LARGE -> STATUS_SKIPPED
        REASON_IDENTITY_UNRESOLVED, REASON_NO_EVIDENCE -> STATUS_UNRESOLVED
        else -> STATUS_UNRESOLVED
    }

    private fun boundOrcid(profile: ExpertProfile): String? {
        if (!DiscoveryIdentity.allowed(profile)) return null
        val proofId = normalizeOrcid(profile.identityVerification?.orcid) ?: return null
        val profileId = normalizeOrcid(profile.orcidId)
        val externalNode = profile.externalIds?.let {
            runCatching { mapper.readTree(it) }.getOrNull() ?: return null
        }
        val external = externalNode?.path("orcid")?.takeIf { !it.isMissingNode && !it.isNull }?.let {
            if (!it.isTextual) return null
            normalizeOrcid(it.asText()) ?: return null
        }
        if ((profileId != null && profileId != proofId) || (external != null && external != proofId)) return null
        return proofId
    }

    private fun profileOrcidIds(profile: ExpertProfile): Set<String> = buildSet {
        normalizeOrcid(profile.identityVerification?.orcid)?.let(::add)
        normalizeOrcid(profile.orcidId)?.let(::add)
        profile.externalIds?.let { runCatching { mapper.readTree(it) }.getOrNull() }
            ?.path("orcid")?.asText(null)?.let(::normalizeOrcid)?.let(::add)
    }

    private fun resolvedInstitution(value: String, source: InstitutionSource): Boolean =
        DiscoveryInstitutionNormalizer.resolve(value, source, "country-supplement").let {
            it.status == DiscoveryInstitutionNormalizer.RESOLVED && it.subjects.size == 1
        }

    private fun canonical(value: String) = DiscoveryInstitutionNormalizer.canonical(value)

    private fun normalizedAuthorId(value: String?): String? = value?.trim()
        ?.removePrefix("https://openalex.org/")?.removePrefix("http://openalex.org/")
        ?.trim()?.takeIf { it.matches(Regex("[Aa][0-9]+")) }

    private fun validCountry(value: String?): String? {
        val code = value?.trim()?.uppercase(Locale.ROOT) ?: return null
        if (!code.matches(Regex("[A-Z]{2}"))) return null
        if (CountryContinentMapping.toRegion(code) == CountryContinentMapping.REGION_OTHER) return null
        return code
    }

    private fun institutionOf(value: String): String? =
        runCatching { mapper.readTree(value).path("institution").asText("") }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun dateText(node: JsonNode): String? = listOf("year", "month", "day").mapNotNull { key ->
        node.path(key).path("value")?.takeIf { !it.isMissingNode && !it.isNull }?.asText(null)
    }.takeIf { it.isNotEmpty() }?.joinToString("-")

    private fun isCancellation(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is InterruptedException) return true
            current = current.cause
        }
        // A SocketTimeoutException is a bounded read failure, not cancellation; only a set interrupt flag is.
        return Thread.currentThread().isInterrupted
    }

    private fun sha256(value: String): String = DiscoveryInstitutionNormalizer.sha256(value)
    private fun sha256(value: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    /** Keeps only parser inputs plus `organization.address.country`; strings stay bounded. */
    private fun retainCountryFields(node: JsonNode): JsonNode = when {
        node.isObject -> com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().also { result ->
            node.fields().forEachRemaining { (key, value) -> result.set<JsonNode>(key, retainCountryFields(value)) }
        }
        node.isArray -> com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode().also { result ->
            node.forEach { result.add(retainCountryFields(it)) }
        }
        node.isTextual -> com.fasterxml.jackson.databind.node.TextNode(node.asText().take(500))
        else -> node.deepCopy()
    }

    companion object {
        const val VERSION = "COUNTRY_V1"
        const val MAX_OBSERVATIONS = 20
        const val MAX_INSTITUTION_LENGTH = 2000
        const val MAX_BODY_BYTES = 2 * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 2000
        const val READ_TIMEOUT_MS = 3000
        const val PER_MINUTE = 10
        const val PER_HOUR = 100
        const val DEFAULT_PAUSE_SECONDS = 60L

        const val STATUS_FILLED = "FILLED"
        const val STATUS_UNCHANGED = "UNCHANGED"
        const val STATUS_UNRESOLVED = "UNRESOLVED"
        const val STATUS_CONFLICT = "CONFLICT"
        const val STATUS_ERROR = "ERROR"
        const val STATUS_SKIPPED = "SKIPPED"

        const val REASON_MATCHED_BOUND_INSTITUTION = "MATCHED_BOUND_INSTITUTION"
        const val REASON_EXISTING_VALUE = "EXISTING_VALUE"
        const val REASON_NO_EVIDENCE = "NO_EVIDENCE"
        const val REASON_IDENTITY_UNRESOLVED = "IDENTITY_UNRESOLVED"
        const val REASON_INSTITUTION_MISMATCH = "INSTITUTION_MISMATCH"
        const val REASON_MULTIPLE_COUNTRIES = "MULTIPLE_COUNTRIES"
        const val REASON_INVALID_COUNTRY = "INVALID_COUNTRY"
        const val REASON_HTTP_ERROR = "HTTP_ERROR"
        const val REASON_INVALID_RESPONSE = "INVALID_RESPONSE"
        const val REASON_RATE_LIMITED = "RATE_LIMITED"
        const val REASON_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED"
        const val REASON_EVIDENCE_TOO_LARGE = "EVIDENCE_TOO_LARGE"

        const val SOURCE_OPENALEX = "OPENALEX"
        const val SOURCE_ORCID = "ORCID"

        val STATUSES = setOf(STATUS_FILLED, STATUS_UNCHANGED, STATUS_UNRESOLVED, STATUS_CONFLICT, STATUS_ERROR, STATUS_SKIPPED)
        val REASONS = setOf(
            REASON_MATCHED_BOUND_INSTITUTION, REASON_EXISTING_VALUE, REASON_NO_EVIDENCE, REASON_IDENTITY_UNRESOLVED,
            REASON_INSTITUTION_MISMATCH, REASON_MULTIPLE_COUNTRIES, REASON_INVALID_COUNTRY, REASON_HTTP_ERROR,
            REASON_INVALID_RESPONSE, REASON_RATE_LIMITED, REASON_BUDGET_EXHAUSTED, REASON_EVIDENCE_TOO_LARGE
        )
    }
}
