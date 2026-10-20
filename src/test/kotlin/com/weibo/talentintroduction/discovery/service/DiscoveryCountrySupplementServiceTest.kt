package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Synthetic OPENALEX/ORCID selection, bounded request and rate-limit matrix for COUNTRY_V1. */
class DiscoveryCountrySupplementServiceTest {
    private val mapper = jacksonObjectMapper()

    private class MutableClock(var now: Instant = Instant.parse("2026-10-19T00:00:00Z")) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private data class Author(
        val openAlexAuthorId: String? = null,
        val institutionName: String? = null,
        val institutionCountry: String? = null,
        val institutionSource: String? = null,
        val orcidId: String? = null
    )

    private data class Case(
        val id: String,
        val source: String,
        val institution: String? = "Example University",
        val country: String? = null,
        val orcid: String? = null,
        val proofOrcid: String? = null,
        val pmcId: String? = null,
        val openAlexAuthorId: String? = null,
        val paperSource: String? = null,
        val paperDoi: String? = null,
        val authors: List<Author> = emptyList(),
        val orcidStatus: Int = 200,
        val retryAfter: Long? = null,
        val orcidBody: JsonNode? = null,
        val orcidRaw: String? = null,
        val oversize: Boolean = false,
        val expectStatus: String,
        val expectReason: String,
        val expectCountry: String? = null,
        val expectRequests: Int
    )

    private data class Fixture(val version: String, val synthetic: Boolean, val cases: List<Case>)

    private fun fixture(): Fixture = javaClass.getResourceAsStream("/discovery/institution-country-cases.json")!!.use {
        mapper.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .readValue(it, object : TypeReference<Fixture>() {})
    }

    private fun profile(case: Case): ExpertProfile {
        val ids = linkedMapOf<String, String>()
        case.orcid?.let { ids["orcid"] = it }
        case.pmcId?.let { ids["pmcId"] = it }
        case.paperDoi?.let { ids["doi"] = it }
        case.openAlexAuthorId?.let { ids["openAlexAuthorId"] = it }
        val proof = IdentityVerification(status = "VERIFIED", version = DiscoveryIdentity.VERSION,
            email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee", source = "JATS_SHA256",
            evidenceHash = "a".repeat(64), orcid = case.proofOrcid, openAlexAuthorId = case.openAlexAuthorId)
        val base = ExpertProfile(orcidId = case.orcid ?: "EMAIL-ada", email = "ada@example.invalid",
            givenNames = "Ada", familyNames = "Lee", country = case.country, keyword = null, employment = null,
            institution = case.institution, emailSource = "PAPER_FULLTEXT", dataSource = case.source,
            externalIds = ids.takeIf { it.isNotEmpty() }?.let { mapper.writeValueAsString(it) },
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, case.source))
    }

    private fun paper(case: Case): PaperMetadata? {
        if (case.paperSource == null) return null
        return PaperMetadata(pmcId = null, pmid = null, doi = case.paperDoi, title = "Synthetic paper", pubYear = 2026,
            journal = null, source = case.paperSource, authors = case.authors.map {
                PaperAuthor(givenNames = "Ada", familyNames = "Lee", orcidId = it.orcidId, affiliation = null,
                    openAlexAuthorId = it.openAlexAuthorId, institutionName = it.institutionName,
                    institutionCountry = it.institutionCountry, institutionSource = it.institutionSource)
            })
    }

    private fun properties() = DiscoveryVerificationProperties(enabled = true, countrySupplementEnabled = true)

    @Test fun `fixture matrix selects only bound matching institutions and keeps countries verbatim`() {
        val fixture = fixture()
        assertTrue(fixture.synthetic)
        assertEquals("COUNTRY_CASES_V1", fixture.version)
        assertTrue(fixture.cases.map { it.id }.toSet().size == fixture.cases.size)
        fixture.cases.forEach { case ->
            val clock = MutableClock()
            val http = countrySupplementRestTemplate()
            val server = MockRestServiceServer.bindTo(http).build()
            val base = profile(case)
            val originalToken = base.institutionEvidence
            val uri = "https://pub.orcid.org/v3.0/${case.orcid}/employments"
            if (case.expectRequests == 1) {
                val request = server.expect(ExpectedCount.once(), requestTo(uri))
                when {
                    case.orcidStatus != 200 -> request.andRespond(withStatus(HttpStatus.valueOf(case.orcidStatus))
                        .headers(org.springframework.http.HttpHeaders().apply { case.retryAfter?.let { set("Retry-After", it.toString()) } }))
                    case.oversize -> request.andRespond(withSuccess("x".repeat(DiscoveryCountrySupplementService.MAX_BODY_BYTES + 1), MediaType.APPLICATION_JSON))
                    case.orcidRaw != null -> request.andRespond(withSuccess(case.orcidRaw, MediaType.APPLICATION_JSON))
                    else -> request.andRespond(withSuccess(mapper.writeValueAsString(case.orcidBody), MediaType.APPLICATION_JSON))
                }
            } else {
                server.expect(ExpectedCount.never(), anything())
            }
            val service = DiscoveryCountrySupplementService(properties(), clock, http)
            val result = service.supplement(1, "doc-${case.id}", base, paper(case))
            server.verify()
            val receipt = requireNotNull(result.receipt) { case.id }
            assertEquals(case.expectStatus, receipt.status, case.id)
            assertEquals(case.expectReason, receipt.reason, case.id)
            assertEquals(case.expectCountry, result.profile.country, case.id)
            assertEquals(case.expectRequests, result.httpRequests, case.id)
            assertEquals(base.institution, result.profile.institution, case.id)
            assertEquals(base.nationality, result.profile.nationality, case.id)
            when (receipt.status) {
                DiscoveryCountrySupplementService.STATUS_FILLED -> {
                    assertNotEquals(originalToken, result.profile.institutionEvidence, case.id)
                    assertTrue(DiscoveryIdentity.validInstitutionEvidence(result.profile), case.id)
                    assertEquals(receipt.afterCountry, result.profile.country)
                    assertNull(receipt.beforeCountry)
                    // I-2: the original evidence source is retained even when the country came from another source.
                    assertEquals(originalToken!!.substringBefore(':'), result.profile.institutionEvidence!!.substringBefore(':'), case.id)
                }
                else -> {
                    assertEquals(originalToken, result.profile.institutionEvidence, case.id)
                    assertEquals(base.country, result.profile.country, case.id)
                }
            }
        }
    }

    @Test fun `429 pauses the channel from Retry-After and resumes after the clock advances`() {
        val clock = MutableClock()
        val http = countrySupplementRestTemplate()
        val server = MockRestServiceServer.bindTo(http).build()
        val case = fixture().cases.single { it.id == "ORCID_429" }
        val base = profile(case)
        val uri = "https://pub.orcid.org/v3.0/${case.orcid}/employments"
        server.expect(ExpectedCount.once(), requestTo(uri)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
            .headers(org.springframework.http.HttpHeaders().apply { set("Retry-After", "5") }))
        val service = DiscoveryCountrySupplementService(properties(), clock, http)
        assertEquals(DiscoveryCountrySupplementService.REASON_RATE_LIMITED,
            service.supplement(1, "doc-429", base, null).receipt!!.reason)
        clock.now = clock.now.plusSeconds(4)
        assertEquals(DiscoveryCountrySupplementService.STATUS_SKIPPED,
            service.supplement(1, "doc-429", base, null).receipt!!.status)
        clock.now = clock.now.plusSeconds(5)
        server.reset()
        server.expect(ExpectedCount.once(), requestTo(uri)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
        assertNotEquals(DiscoveryCountrySupplementService.REASON_RATE_LIMITED,
            service.supplement(1, "doc-429", base, null).receipt!!.reason)
        server.verify()
    }

    @Test fun `per-minute and per-hour budgets reject without waiting and recover with the clock`() {
        val clock = MutableClock()
        val service = DiscoveryCountrySupplementService(properties(), clock)
        repeat(10) {
            assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
            service.releasePermit()
        }
        assertEquals(CountryPermit.BUDGET, service.tryAcquirePermit())
        clock.now = clock.now.plusSeconds(61)
        assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
        service.releasePermit()

        // Reach the hourly cap with minute windows that never exceed ten.
        var acquired = 11
        while (acquired < 100) {
            if (acquired % 10 == 0) clock.now = clock.now.plusSeconds(60)
            assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
            service.releasePermit()
            acquired++
        }
        assertEquals(CountryPermit.BUDGET, service.tryAcquirePermit())
        clock.now = clock.now.plusSeconds(3601)
        assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
        service.releasePermit()
    }

    @Test fun `the channel allows exactly one concurrent request`() {
        val service = DiscoveryCountrySupplementService(properties(), MutableClock())
        assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
        assertEquals(CountryPermit.BUSY, service.tryAcquirePermit())
        service.releasePermit()
        assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit())
    }

    @Test fun `a timeout becomes HTTP_ERROR and an interrupt propagates`() {
        val timeoutCase = fixture().cases.single { it.id == "ORCID_MATCH" }
        val timeoutHttp = countrySupplementRestTemplate()
        val timeoutServer = MockRestServiceServer.bindTo(timeoutHttp).build()
        timeoutServer.expect(ExpectedCount.once(), anything()).andRespond { throw SocketTimeoutException("fixture") }
        val timedOut = DiscoveryCountrySupplementService(properties(), MutableClock(), timeoutHttp)
            .supplement(1, "doc-timeout", profile(timeoutCase), null)
        assertEquals(DiscoveryCountrySupplementService.STATUS_ERROR, timedOut.receipt!!.status)
        assertEquals(DiscoveryCountrySupplementService.REASON_HTTP_ERROR, timedOut.receipt!!.reason)
        assertEquals(1, timedOut.httpRequests)

        val interruptHttp = countrySupplementRestTemplate()
        val interruptServer = MockRestServiceServer.bindTo(interruptHttp).build()
        interruptServer.expect(ExpectedCount.once(), anything()).andRespond { throw InterruptedException("fixture") }
        val interrupted = DiscoveryCountrySupplementService(properties(), MutableClock(), interruptHttp)
        assertThrows(InterruptedException::class.java) {
            interrupted.supplement(1, "doc-interrupt", profile(timeoutCase), null)
        }
        assertTrue(Thread.currentThread().isInterrupted)
        Thread.interrupted()
    }

    @Test fun `switch off returns the profile untouched with no receipt and no request`() {
        val case = fixture().cases.single { it.id == "OA_MATCH" }
        val service = DiscoveryCountrySupplementService(DiscoveryVerificationProperties(enabled = true),
            MutableClock(), countrySupplementRestTemplate())
        val result = service.supplement(1, "doc-off", profile(case), paper(case))
        assertNull(result.receipt)
        assertEquals(profile(case), result.profile)
        assertEquals(0, result.httpRequests)
    }

    @Test fun `country selection uses strict complete-name equality without punctuation folding`() {
        val divergent = Case(id = "strict-name", source = "OPENALEX", institution = "Example University-A",
            orcid = "0000-0002-1825-0097", proofOrcid = "0000-0002-1825-0097", openAlexAuthorId = "A123",
            paperSource = "OPENALEX", paperDoi = "10.example/strict",
            authors = listOf(Author(openAlexAuthorId = "A123", institutionName = "Example University A",
                institutionCountry = "US", institutionSource = "OPENALEX")),
            expectStatus = DiscoveryCountrySupplementService.STATUS_UNRESOLVED,
            expectReason = DiscoveryCountrySupplementService.REASON_INSTITUTION_MISMATCH, expectRequests = 0)
        val divergentService = DiscoveryCountrySupplementService(properties(), MutableClock(), countrySupplementRestTemplate())
        val divergentResult = divergentService.supplement(1, "doc-strict", profile(divergent), paper(divergent))
        assertNull(divergentResult.profile.country)
        assertEquals(DiscoveryCountrySupplementService.REASON_INSTITUTION_MISMATCH, divergentResult.receipt!!.reason)
        assertEquals(0, divergentResult.httpRequests)

        val equivalent = divergent.copy(institution = "Example University",
            authors = listOf(Author(openAlexAuthorId = "A123", institutionName = "  example   university ",
                institutionCountry = "US", institutionSource = "OPENALEX")),
            expectStatus = DiscoveryCountrySupplementService.STATUS_FILLED,
            expectReason = DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION, expectCountry = "US")
        val equivalentService = DiscoveryCountrySupplementService(properties(), MutableClock(), countrySupplementRestTemplate())
        val equivalentResult = equivalentService.supplement(1, "doc-equivalent", profile(equivalent), paper(equivalent))
        assertEquals("US", equivalentResult.profile.country)
        assertEquals(0, equivalentResult.httpRequests)
    }

    @Test fun `identical valid country duplicates fill while real disagreement stays a conflict`() {
        val duplicate = Case(id = "oa-duplicate", source = "OPENALEX", institution = "Example University",
            openAlexAuthorId = "A123", paperSource = "OPENALEX", paperDoi = "10.example/duplicate",
            authors = listOf(
                Author(openAlexAuthorId = "A123", institutionName = "Example University", institutionCountry = "US",
                    institutionSource = "OPENALEX"),
                Author(openAlexAuthorId = "A123", institutionName = "Example University", institutionCountry = "US",
                    institutionSource = "OPENALEX")),
            expectStatus = DiscoveryCountrySupplementService.STATUS_FILLED,
            expectReason = DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION,
            expectCountry = "US", expectRequests = 0)
        val duplicateResult = DiscoveryCountrySupplementService(properties(), MutableClock(), countrySupplementRestTemplate())
            .supplement(1, "doc-oa-duplicate", profile(duplicate), paper(duplicate))
        assertEquals("US", duplicateResult.profile.country)
        assertEquals(0, duplicateResult.httpRequests)

        val disagreement = duplicate.copy(authors = listOf(
            duplicate.authors.first(),
            duplicate.authors.first().copy(institutionCountry = "GB")),
            expectStatus = DiscoveryCountrySupplementService.STATUS_CONFLICT,
            expectReason = DiscoveryCountrySupplementService.REASON_MULTIPLE_COUNTRIES, expectCountry = null)
        val disagreementResult = DiscoveryCountrySupplementService(properties(), MutableClock(), countrySupplementRestTemplate())
            .supplement(1, "doc-oa-conflict", profile(disagreement), paper(disagreement))
        assertNull(disagreementResult.profile.country)
        assertEquals(DiscoveryCountrySupplementService.REASON_MULTIPLE_COUNTRIES, disagreementResult.receipt!!.reason)

        val twoGb = mapper.readTree("""{"affiliation-group":[{"summaries":[
            {"employment-summary":{"put-code":1,"visibility":"public","role-title":"Professor",
              "organization":{"name":"Example University","address":{"country":{"value":"GB"}}},
              "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}}}},
            {"employment-summary":{"put-code":2,"visibility":"public","role-title":"Professor",
              "organization":{"name":"Example University","address":{"country":{"value":"GB"}}},
              "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2021"}}}}]}]}""")
        val orcidDuplicate = Case(id = "orcid-duplicate", source = "ORCID", institution = "Example University",
            orcid = "0000-0002-1825-0097", proofOrcid = "0000-0002-1825-0097", orcidBody = twoGb,
            expectStatus = DiscoveryCountrySupplementService.STATUS_FILLED,
            expectReason = DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION,
            expectCountry = "GB", expectRequests = 1)
        val orcidHttp = countrySupplementRestTemplate()
        val orcidServer = MockRestServiceServer.bindTo(orcidHttp).build()
        orcidServer.expect(ExpectedCount.once(), anything()).andRespond(withSuccess(twoGb.toString(), MediaType.APPLICATION_JSON))
        val orcidResult = DiscoveryCountrySupplementService(properties(), MutableClock(), orcidHttp)
            .supplement(1, "doc-orcid-duplicate", profile(orcidDuplicate), paper(orcidDuplicate))
        orcidServer.verify()
        assertEquals("GB", orcidResult.profile.country)
        assertEquals(1, orcidResult.httpRequests)
    }

    @Test fun `cancellation propagates instead of becoming an error receipt`() {
        val case = fixture().cases.single { it.id == "ORCID_MATCH" }
        val http = countrySupplementRestTemplate()
        val server = MockRestServiceServer.bindTo(http).build()
        server.expect(ExpectedCount.once(), anything()).andRespond {
            throw java.util.concurrent.CancellationException("fixture")
        }
        val service = DiscoveryCountrySupplementService(properties(), MutableClock(), http)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            service.supplement(1, "doc-cancel", profile(case), paper(case))
        }
    }

    @Test fun `over limit observation text is omitted so the skip remains a valid registration input`() {
        val long = "Example University " + "x".repeat(2003)
        val case = Case(id = "oversized-institution", source = "OPENALEX", institution = long,
            openAlexAuthorId = "A123", paperSource = "OPENALEX", paperDoi = "10.example/long",
            authors = listOf(Author(openAlexAuthorId = "A123", institutionName = long, institutionCountry = "US",
                institutionSource = "OPENALEX")),
            expectStatus = DiscoveryCountrySupplementService.STATUS_SKIPPED,
            expectReason = DiscoveryCountrySupplementService.REASON_EVIDENCE_TOO_LARGE, expectRequests = 0)
        val service = DiscoveryCountrySupplementService(properties(), MutableClock(), countrySupplementRestTemplate())
        val result = service.supplement(1, "doc-oversized", profile(case), paper(case))
        assertEquals(DiscoveryCountrySupplementService.STATUS_SKIPPED, result.receipt!!.status)
        assertEquals(DiscoveryCountrySupplementService.REASON_EVIDENCE_TOO_LARGE, result.receipt!!.reason)
        assertNull(result.profile.country)
        assertEquals(profile(case).institutionEvidence, result.profile.institutionEvidence)
        assertTrue(result.receipt!!.observations.isEmpty())
    }

    @Test fun `observations are bounded and oversized evidence never fills a country`() {
        val case = fixture().cases.single { it.id == "ORCID_MATCH" }
        val html = mapper.createObjectNode()
        val group = mapper.createArrayNode()
        html.set<JsonNode>("affiliation-group", group)
        val summaries = mapper.createArrayNode()
        repeat(DiscoveryCountrySupplementService.MAX_OBSERVATIONS + 1) {
            val summary = mapper.createObjectNode()
            summary.put("employment-summary", mapper.createObjectNode().apply {
                put("put-code", it + 1); put("visibility", "public"); put("role-title", "Professor")
                set<JsonNode>("organization", mapper.createObjectNode().apply {
                    put("name", "Example University")
                    set<JsonNode>("address", mapper.createObjectNode().apply {
                        set<JsonNode>("country", mapper.createObjectNode().put("value", "GB"))
                    })
                })
                set<JsonNode>("source", mapper.createObjectNode().apply {
                    set<JsonNode>("source-client-id", mapper.createObjectNode().put("path", "fixture-org"))
                })
                set<JsonNode>("start-date", mapper.createObjectNode().apply {
                    set<JsonNode>("year", mapper.createObjectNode().put("value", "2020"))
                })
            })
            summaries.add(summary)
        }
        val wrapper = mapper.createObjectNode(); wrapper.set<JsonNode>("summaries", summaries); group.add(wrapper)
        val http = countrySupplementRestTemplate()
        val server = MockRestServiceServer.bindTo(http).build()
        server.expect(ExpectedCount.once(), anything()).andRespond(withSuccess(mapper.writeValueAsString(html), MediaType.APPLICATION_JSON))
        val result = DiscoveryCountrySupplementService(properties(), MutableClock(), http)
            .supplement(1, "doc-bounded", profile(case), null)
        assertEquals(DiscoveryCountrySupplementService.REASON_EVIDENCE_TOO_LARGE, result.receipt!!.reason)
        assertNull(result.profile.country)
        assertTrue(result.receipt!!.observations.size <= DiscoveryCountrySupplementService.MAX_OBSERVATIONS)
    }
}
