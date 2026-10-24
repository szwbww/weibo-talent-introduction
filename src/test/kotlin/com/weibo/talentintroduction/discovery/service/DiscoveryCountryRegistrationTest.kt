package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.CandidateEligibilityService
import com.weibo.talentintroduction.expert.service.EligibilityFilterService
import com.weibo.talentintroduction.expert.service.EmailValidationService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Registration-level I-3/I-4 contracts: protection first, frozen-input recovery, concurrent winner and
 * receipt validation. No provider call may happen for an existing reservation.
 */
class DiscoveryCountryRegistrationTest {
    private val mapper = jacksonObjectMapper()
    private val orcid = "0000-0002-1825-0097"
    private val now = Instant.parse("2026-10-19T00:00:00Z")

    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private lateinit var repository: DiscoveryVerificationRepository
    private lateinit var writer: ExpertIndexWriterService
    private lateinit var admissions: DiscoveryReviewRepository
    private lateinit var jdbc: JdbcTemplate
    private lateinit var policy: DiscoveryAdmissionPolicy
    private lateinit var clock: MutableClock
    private lateinit var http: org.springframework.web.client.RestTemplate
    private lateinit var server: MockRestServiceServer
    private lateinit var apolloSettings: DiscoveryApolloSettingsService

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @BeforeEach fun setUp() {
        repository = Mockito.mock(DiscoveryVerificationRepository::class.java)
        writer = Mockito.mock(ExpertIndexWriterService::class.java)
        admissions = Mockito.mock(DiscoveryReviewRepository::class.java)
        jdbc = Mockito.mock(JdbcTemplate::class.java)
        apolloSettings = Mockito.mock(DiscoveryApolloSettingsService::class.java)
        val settings = DiscoveryApolloSettings(1, ApolloSettingSource.SAVED, true, true, true, true,
            true, true, true, now)
        Mockito.`when`(apolloSettings.resolve()).thenReturn(settings)
        Mockito.`when`(apolloSettings.freeze()).thenReturn(FrozenApolloAccess.fromSettings(settings))
        clock = MutableClock(now)
        val filters = Mockito.mock(EligibilityFilterService::class.java)
        Mockito.`when`(filters.getCandidateFilter()).thenReturn(CandidateFilterProperties(requireValidEmail = false,
            requireDoctoralDegree = true, enableAgeFilter = true))
        Mockito.`when`(filters.getAcademicFilter()).thenReturn(AcademicFilterProperties())
        policy = DiscoveryAdmissionPolicy(CandidateEligibilityService(filters, Mockito.mock(EmailValidationService::class.java)),
            filters, clock, null)
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(emptyList())
        http = countrySupplementRestTemplate()
        server = MockRestServiceServer.bindTo(http).build()
    }

    private fun properties(enabled: Boolean = true) =
        DiscoveryVerificationProperties(enabled = true, mode = VerificationMode.ENFORCE,
            apolloApiKey = "fixture-key", countrySupplementEnabled = enabled)

    private fun service(props: DiscoveryVerificationProperties = properties(), supplement: Boolean = true): DiscoveryVerificationRegistrationService {
        val supplementService = if (supplement) DiscoveryCountrySupplementService(props, clock, http) else null
        return DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions, jdbc,
            Mockito.mock(TransactionTemplate::class.java), apolloSettings, clock, supplementService)
    }

    @Test fun `new run reads settings once at freeze and preserves null options until begin`() {
        val registration = service()
        val criteria = registration.resolveOptions(PaperSearchCriteria())
        assertNull(criteria.apolloAssist)
        Mockito.verifyNoInteractions(apolloSettings)
        Mockito.`when`(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, 71L))
            .thenReturn(1L)
        Mockito.doAnswer { invocation ->
            VerificationRun(invocation.getArgument(0), invocation.getArgument(1),
                VerificationMode.ENFORCE.policyVersion, invocation.getArgument(2), invocation.getArgument(3),
                createdAt = now, updatedAt = now)
        }.`when`(repository).createRun(Mockito.eq(71L), Mockito.eq(VerificationMode.ENFORCE) ?: VerificationMode.ENFORCE, Mockito.eq(true),
            anyValue("{}"), anyValue(now))
        val run = registration.begin(71, criteria).run!!
        Mockito.verify(apolloSettings, Mockito.times(1)).freeze()
        Mockito.verify(apolloSettings, Mockito.never()).resolve()
        assertTrue(run.apolloRequested)
        val frozen = run.frozenApolloAccess(mapper)
        assertEquals(1L, frozen.settingRevision)
        assertTrue(frozen.searchEnabled)
        assertTrue(frozen.detailEnabled)
    }

    @Test fun `existing run wins before live settings and unavailable service validation`() {
        val existing = context().run!!
        Mockito.`when`(repository.findRun(7)).thenReturn(existing)
        val unavailable = DiscoveryVerificationProperties(enabled = false, apolloApiKey = "")
        val resumed = service(unavailable).begin(7, PaperSearchCriteria(apolloAssist = true))
        assertEquals(existing, resumed.run)
        Mockito.verifyNoInteractions(apolloSettings)
        Mockito.verifyNoInteractions(jdbc)
    }

    @Test fun `explicit Apollo requires service and key but null and false stay unresolved`() {
        listOf(DiscoveryVerificationProperties(enabled = false, apolloApiKey = "fixture-key"),
            DiscoveryVerificationProperties(enabled = true, apolloApiKey = "")).forEach { props ->
            val registration = service(props)
            assertNull(registration.resolveOptions(PaperSearchCriteria()).apolloAssist)
            assertFalse(registration.resolveOptions(PaperSearchCriteria(apolloAssist = false)).apolloAssist!!)
            val error = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
                registration.resolveOptions(PaperSearchCriteria(apolloAssist = true))
            }
            assertEquals(503, error.rawStatusCode)
        }
        Mockito.verifyNoInteractions(apolloSettings)
    }

    @Test fun `explicit true validates saved search before launch without resolving null or false`() {
        val settings = apolloSettings.resolve().copy(revision = 2, searchEnabled = false, detailEnabled = true,
            effectiveSearch = false, effectiveDetails = false)
        Mockito.`when`(apolloSettings.resolve()).thenReturn(settings)
        Mockito.clearInvocations(apolloSettings)
        val registration = service()
        assertNull(registration.resolveOptions(PaperSearchCriteria()).apolloAssist)
        assertEquals(false, registration.resolveOptions(PaperSearchCriteria(apolloAssist = false)).apolloAssist)
        Mockito.verifyNoInteractions(apolloSettings)
        val error = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            registration.resolveOptions(PaperSearchCriteria(apolloAssist = true))
        }
        assertEquals(409, error.rawStatusCode)
        assertEquals("APOLLO_SEARCH_DISABLED", error.reason)
        Mockito.verify(apolloSettings, Mockito.times(1)).resolve()
        Mockito.verify(apolloSettings, Mockito.never()).freeze()
        Mockito.verifyNoInteractions(repository, jdbc)
    }

    @Test fun `begin freezes once and rechecks settings changed after explicit prelaunch validation`() {
        val registration = service()
        val criteria = registration.resolveOptions(PaperSearchCriteria(apolloAssist = true))
        val changed = DiscoveryApolloSettings(2, ApolloSettingSource.SAVED, false, true, false, false,
            true, true, true, now)
        Mockito.`when`(apolloSettings.freeze()).thenReturn(FrozenApolloAccess.fromSettings(changed))
        Mockito.`when`(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, 71L))
            .thenReturn(1L)
        val error = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            registration.begin(71, criteria)
        }
        assertEquals(409, error.rawStatusCode)
        Mockito.verify(apolloSettings, Mockito.times(1)).resolve()
        Mockito.verify(apolloSettings, Mockito.times(1)).freeze()
        assertFalse(Mockito.mockingDetails(repository).invocations.any { it.method.name == "createRun" })
    }

    @Test fun `saved settings read failures stay 503 at prelaunch and new run freeze`() {
        Mockito.`when`(apolloSettings.resolve()).thenThrow(DiscoveryApolloSettingsUnavailable())
        val registration = service()
        val validation = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            registration.resolveOptions(PaperSearchCriteria(apolloAssist = true))
        }
        assertEquals(503, validation.rawStatusCode)
        assertEquals("APOLLO_SETTINGS_UNAVAILABLE", validation.reason)
        Mockito.verifyNoInteractions(repository, jdbc)
        Mockito.`when`(apolloSettings.freeze()).thenThrow(DiscoveryApolloSettingsUnavailable())
        Mockito.`when`(jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, 71L))
            .thenReturn(1L)
        val freeze = assertThrows(org.springframework.web.server.ResponseStatusException::class.java) {
            registration.begin(71, PaperSearchCriteria())
        }
        assertEquals(503, freeze.rawStatusCode)
        assertEquals("APOLLO_SETTINGS_UNAVAILABLE", freeze.reason)
        Mockito.verify(apolloSettings, Mockito.times(1)).freeze()
        assertFalse(Mockito.mockingDetails(repository).invocations.any { it.method.name == "createRun" })
    }

    private fun profile(institution: String = "Example University", country: String? = null, paperDoi: String? = "10.example/test"): ExpertProfile {
        val ids = linkedMapOf("orcid" to orcid)
        paperDoi?.let { ids["doi"] = it }
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "ORCID_RECORD_SHA256", "a".repeat(64), orcid, null)
        val base = ExpertProfile(orcidId = orcid, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
            country = country, keyword = null, employment = null, institution = institution,
            emailSource = "ORCID_PUBLIC", dataSource = "ORCID", externalIds = mapper.writeValueAsString(ids),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "ORCID"))
    }

    private fun openAlexProfile(institution: String): ExpertProfile {
        val ids = linkedMapOf("orcid" to orcid, "openAlexAuthorId" to "A123", "doi" to "10.example/long")
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "SOURCE_SHA256", "a".repeat(64), orcid, "A123")
        val base = ExpertProfile(orcidId = orcid, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
            country = null, keyword = null, employment = null, institution = institution,
            emailSource = "PAPER_FULLTEXT", dataSource = "OPENALEX", externalIds = mapper.writeValueAsString(ids),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "OPENALEX"))
    }

    private fun openAlexPaper(institution: String): PaperMetadata = PaperMetadata(
        pmcId = null, pmid = null, doi = "10.example/long", title = "Synthetic paper", pubYear = 2026,
        journal = null, source = "OPENALEX", authors = listOf(
            PaperAuthor(givenNames = "Ada", familyNames = "Lee", orcidId = orcid, affiliation = null,
                openAlexAuthorId = "A123", institutionName = institution, institutionCountry = "US",
                institutionSource = "OPENALEX")))

    private fun jatsProfile(): ExpertProfile {
        val ids = linkedMapOf("pmcId" to "PMC123456", "orcid" to orcid)
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "JATS_SHA256", "a".repeat(64), orcid, null)
        val base = ExpertProfile(orcidId = orcid, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
            country = null, keyword = null, employment = null, institution = "Example University",
            emailSource = "PAPER_FULLTEXT", dataSource = "JATS", externalIds = mapper.writeValueAsString(ids),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "JATS"))
    }

    private fun orcidGbBody() = """{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":1,
        |"visibility":"public","role-title":"Professor","organization":{"name":"Example University",
        |"address":{"country":{"value":"GB"}}},"source":{"source-client-id":{"path":"fixture-org"}},
        |"start-date":{"year":{"value":"2020"}}}}]}]}""".trimMargin().replace("\n", "")

    private fun profileWithCountry(country: String?): ExpertProfile {
        val changed = profile(country = country)
        return changed.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(changed, "ORCID"))
    }

    private fun context() = DiscoveryVerificationExecution(7, VerificationRun(7, VerificationMode.ENFORCE,
        VerificationMode.ENFORCE.policyVersion, true, "{}", createdAt = now, updatedAt = now))

    private fun item(input: VerificationReservation, state: VerificationState = VerificationState.RESERVED): VerificationItem =
        VerificationItem(1, input.ownerExecutionId, input.expertDocId, input.identityHash, input.policyVersion,
            input.sourceName, input.sourceItemKey, if (state == VerificationState.RESERVED) 0 else 1, input.snapshotJson,
            input.factFingerprint, state, null, VerificationQualification.UNKNOWN, VerificationRoute.NONE,
            null, null, null, null, null, null, null, 0, 0, 0, null, null, null, null,
            VerificationProjection.NOT_REQUIRED, null, if (state == VerificationState.RESERVED) null else now,
            null, now, now)

    private fun snapshot(profile: ExpertProfile, receipt: CountrySupplementReceipt? = null): String {
        // Registration always freezes the RAW identity convention (docId for both esDocId and orcidId).
        val persisted = profile.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc")
        val config = policy.evaluate("EMAIL-doc", persisted).configSnapshot
        val frozen = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", persisted, config)
        val map = linkedMapOf<String, Any?>("docId" to "EMAIL-doc", "identityHash" to frozen.identityHash,
            "factFingerprint" to frozen.factFingerprint, "profile" to persisted, "admissionRevision" to null,
            "sourceName" to "ORCID", "sourceItemKey" to orcid, "constructedAt" to now.toString())
        receipt?.let { map["countrySupplement"] = it }
        return mapper.writeValueAsString(map)
    }

    @Test fun `existing RESERVED is resumed from frozen facts without any HTTP and without upgrading`() {
        val base = profile()
        val frozen = profile()
        val frozenJson = snapshot(frozen)
        val identity = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", base,
            policy.evaluate("EMAIL-doc", base).configSnapshot).identityHash
        val fingerprint = mapper.readTree(frozenJson).path("factFingerprint").asText()
        val reservedItem = item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion,
            "ORCID", orcid, frozenJson, fingerprint))
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(listOf(reservedItem))
        server.expect(ExpectedCount.never(), anything())
        val registration = service().reserve(context(), "EMAIL-doc", base, "ORCID", orcid)
        server.verify()
        assertFalse(registration.protected)
        assertNull(registration.preparedProfile!!.country)
        assertEquals(frozen.institution, registration.preparedProfile!!.institution)
        assertEquals(frozen.institutionEvidence, registration.preparedProfile!!.institutionEvidence)
        Mockito.verify(repository, Mockito.never()).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "ORCID", orcid, "{}", "0".repeat(64))), anyValue(now))
    }

    @Test fun `existing reservation from another source or with changed facts fails registration`() {
        val base = profile()
        val identity = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc"),
            policy.evaluate("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc")).configSnapshot).identityHash
        val frozenJson = snapshot(profile())
        val fingerprint = snapshot(profile()).let { mapper.readTree(it).path("factFingerprint").asText() }
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(listOf(
            item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion,
                "CROSSREF", "DOI:1", frozenJson, fingerprint))))
        assertThrows(DiscoveryRegistrationException::class.java) {
            service().reserve(context(), "EMAIL-doc", base, "ORCID", orcid)
        }
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(listOf(
            item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion,
                "ORCID", orcid, frozenJson, fingerprint))))
        assertThrows(DiscoveryRegistrationException::class.java) {
            service().reserve(context(), "EMAIL-doc", profile(institution = "Changed University"), "ORCID", orcid)
        }
    }

    @Test fun `already activated reservation is protected and never rewrites RAW`() {
        val base = profile()
        val identity = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc"),
            policy.evaluate("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc")).configSnapshot).identityHash
        val frozenJson = snapshot(profile())
        val fingerprint = snapshot(profile()).let { mapper.readTree(it).path("factFingerprint").asText() }
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(listOf(
            item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion,
                "ORCID", orcid, frozenJson, fingerprint), VerificationState.QUEUED)))
        server.expect(ExpectedCount.never(), anything())
        val registration = service().reserve(context(), "EMAIL-doc", base, "ORCID", orcid)
        assertTrue(registration.protected)
        assertEquals(frozenJson, registration.item!!.snapshotJson)
    }

    @Test fun `a concurrent winner country is returned instead of the locally supplemented value`() {
        val base = profile()
        server.expect(ExpectedCount.once(), anything()).andRespond(withSuccess(
            """{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":1,"visibility":"public",
            "role-title":"Professor","organization":{"name":"Example University","address":{"country":{"value":"GB"}}},
            "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}}}}]}]}""",
            MediaType.APPLICATION_JSON))
        // Simulate another process winning with US: reserveItem returns that frozen snapshot.
        val winnerProfile = profileWithCountry("US")
        val winnerJson = snapshot(winnerProfile, CountrySupplementReceipt(
            status = DiscoveryCountrySupplementService.STATUS_FILLED,
            reason = DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION,
            beforeCountry = null, afterCountry = "US", originalInstitutionEvidence = base.institutionEvidence,
            institution = base.institution, selectedSource = "ORCID", constructedAt = now.toString()))
        val winnerFingerprint = mapper.readTree(winnerJson).path("factFingerprint").asText()
        val identity = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc"),
            policy.evaluate("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc")).configSnapshot).identityHash
        Mockito.doReturn(item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion,
            "ORCID", orcid, winnerJson, winnerFingerprint))).`when`(repository).reserveItem(
            anyValue(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion, "ORCID", orcid,
                winnerJson, winnerFingerprint)), anyValue(now))
        val registration = service().reserve(context(), "EMAIL-doc", base, "ORCID", orcid)
        server.verify()
        assertEquals("US", registration.preparedProfile!!.country)
        assertNotEquals("GB", registration.preparedProfile!!.country)
        assertEquals(winnerJson, registration.item!!.snapshotJson)
    }

    @Test fun `a new registration freezes the receipt and the final token in the same snapshot`() {
        var capturedValue: VerificationReservation? = null
        Mockito.doAnswer { invocation ->
            capturedValue = invocation.getArgument(0)
            item(invocation.getArgument(0))
        }.`when`(repository).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "ORCID", orcid, "{}", "0".repeat(64))), anyValue(now))
        server.expect(ExpectedCount.once(), anything()).andRespond(withSuccess(
            """{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":1,"visibility":"public",
            "role-title":"Professor","organization":{"name":"Example University","address":{"country":{"value":"GB"}}},
            "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}}}}]}]}""",
            MediaType.APPLICATION_JSON))
        val registration = service().reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        val input = requireNotNull(capturedValue)
        val root = mapper.readTree(input.snapshotJson)
        assertEquals("COUNTRY_V1", root.path("countrySupplement").path("version").asText())
        assertEquals("FILLED", root.path("countrySupplement").path("status").asText())
        assertEquals("GB", root.path("profile").path("country").asText())
        assertEquals("GB", registration.preparedProfile!!.country)
        assertTrue(DiscoveryIdentity.validInstitutionEvidence(registration.preparedProfile!!))
        assertNotEquals(profile().institutionEvidence, registration.preparedProfile!!.institutionEvidence)
        assertEquals(input.factFingerprint, registration.item!!.factFingerprint)
    }

    @Test fun `switch off and a missing supplement service freeze the legacy snapshot`() {
        var capturedValue: VerificationReservation? = null
        Mockito.doAnswer { invocation ->
            capturedValue = invocation.getArgument(0)
            item(invocation.getArgument(0))
        }.`when`(repository).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "ORCID", orcid, "{}", "0".repeat(64))), anyValue(now))
        server.expect(ExpectedCount.never(), anything())
        service(properties(enabled = false)).reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        assertFalse(mapper.readTree(requireNotNull(capturedValue).snapshotJson).has("countrySupplement"))
        assertTrue(mapper.readTree(requireNotNull(capturedValue).snapshotJson).path("profile").path("country").isNull)
        service(properties(enabled = true), supplement = false).reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        assertFalse(mapper.readTree(requireNotNull(capturedValue).snapshotJson).has("countrySupplement"))
        server.verify()
    }

    @Test fun `existing protections run before any supplement request`() {
        Mockito.doReturn(ExpertIndexWriterService.DiscoverySnapshot(mapOf("email" to "ada@example.invalid"), 1, 1))
            .`when`(writer).readDiscoveryDocument(anyValue(ExpertIndexLevel.RAW), anyValue("EMAIL-doc"))
        server.expect(ExpectedCount.never(), anything())
        val registration = service().reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        server.verify()
        assertTrue(registration.protected)
        Mockito.verify(repository, Mockito.never()).findItemsByDocIds(anyValue(listOf("EMAIL-doc")))
        Mockito.verify(repository, Mockito.never()).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "ORCID", orcid, "{}", "0".repeat(64))), anyValue(now))
    }

    @Test fun `old snapshots without the member stay readable and an invalid receipt fails closed`() {
        val legacyJson = snapshot(profile())
        assertFalse(mapper.readTree(legacyJson).has("countrySupplement"))
        val invalid = snapshot(profile(), CountrySupplementReceipt(
            status = DiscoveryCountrySupplementService.STATUS_FILLED,
            reason = DiscoveryCountrySupplementService.REASON_MATCHED_BOUND_INSTITUTION,
            afterCountry = "GB", constructedAt = now.toString()))
        val base = profile()
        val identity = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc"),
            policy.evaluate("EMAIL-doc", base.copy(esDocId = "EMAIL-doc", orcidId = "EMAIL-doc")).configSnapshot).identityHash
        val fingerprint = snapshot(profile()).let { mapper.readTree(it).path("factFingerprint").asText() }
        Mockito.`when`(repository.findItemsByDocIds(anyValue(listOf("EMAIL-doc")))).thenReturn(listOf(
            item(VerificationReservation(7, "EMAIL-doc", identity, VerificationMode.ENFORCE.policyVersion, "ORCID", orcid,
                invalid, fingerprint))))
        assertThrows(DiscoveryRegistrationException::class.java) { service().reserve(context(), "EMAIL-doc", base, "ORCID", orcid) }
    }

    @Test fun `JATS original token keeps its source after an ORCID fill and registration succeeds`() {
        var capturedValue: VerificationReservation? = null
        Mockito.doAnswer { invocation ->
            capturedValue = invocation.getArgument(0)
            item(invocation.getArgument(0))
        }.`when`(repository).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "JATS", "PMC123456", "{}", "0".repeat(64))), anyValue(now))
        server.expect(ExpectedCount.once(), anything()).andRespond(withSuccess(orcidGbBody(), MediaType.APPLICATION_JSON))
        val base = jatsProfile()
        val registration = service().reserve(context(), "EMAIL-doc", base, "JATS", "PMC123456")
        val input = requireNotNull(capturedValue)
        val root = mapper.readTree(input.snapshotJson)
        assertEquals("JATS", root.path("profile").path("institutionEvidence").asText().substringBefore(':'))
        assertEquals("ORCID", root.path("countrySupplement").path("selectedSource").asText())
        assertEquals("GB", root.path("profile").path("country").asText())
        assertEquals("GB", registration.preparedProfile!!.country)
        assertTrue(DiscoveryIdentity.validInstitutionEvidence(registration.preparedProfile!!))
    }

    @Test fun `the supplement service is a container bean injected into registration`() {
        AnnotationConfigApplicationContext().use { ctx ->
            ctx.beanFactory.registerSingleton("properties", properties())
            ctx.beanFactory.registerSingleton("repository", repository)
            ctx.beanFactory.registerSingleton("writer", writer)
            ctx.beanFactory.registerSingleton("policy", policy)
            ctx.beanFactory.registerSingleton("admissions", admissions)
            ctx.beanFactory.registerSingleton("jdbc", jdbc)
            ctx.beanFactory.registerSingleton("transactions", Mockito.mock(TransactionTemplate::class.java))
            ctx.beanFactory.registerSingleton("apolloSettings", apolloSettings)
            ctx.beanFactory.registerSingleton("clock", clock)
            ctx.register(DiscoveryCountrySupplementService::class.java, DiscoveryVerificationRegistrationService::class.java)
            ctx.refresh()
            assertNotNull(ctx.getBean(DiscoveryCountrySupplementService::class.java))
            val registration = ctx.getBean(DiscoveryVerificationRegistrationService::class.java)
            assertNotNull(ReflectionTestUtils.getField(registration, "countrySupplement"))
        }
    }

    @Test fun `interrupt during the supplement request stops registration instead of becoming a registration failure`() {
        val interruptHttp = countrySupplementRestTemplate()
        val interruptServer = MockRestServiceServer.bindTo(interruptHttp).build()
        interruptServer.expect(ExpectedCount.once(), anything()).andRespond { throw InterruptedException("fixture") }
        val props = properties()
        val interruptRegistration = DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions,
            jdbc, Mockito.mock(TransactionTemplate::class.java), apolloSettings, clock,
            DiscoveryCountrySupplementService(props, clock, interruptHttp))
        assertThrows(InterruptedException::class.java) {
            interruptRegistration.reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        }
        assertTrue(Thread.currentThread().isInterrupted)
        Thread.interrupted()
    }

    @Test fun `cancellation during the supplement request stops registration without inserting an item`() {
        val cancelHttp = countrySupplementRestTemplate()
        val cancelServer = MockRestServiceServer.bindTo(cancelHttp).build()
        cancelServer.expect(ExpectedCount.once(), anything()).andRespond {
            throw java.util.concurrent.CancellationException("fixture")
        }
        val props = properties()
        val cancelRegistration = DiscoveryVerificationRegistrationService(props, repository, writer, policy, admissions,
            jdbc, Mockito.mock(TransactionTemplate::class.java), apolloSettings, clock,
            DiscoveryCountrySupplementService(props, clock, cancelHttp))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            cancelRegistration.reserve(context(), "EMAIL-doc", profile(), "ORCID", orcid)
        }
        Mockito.verify(repository, Mockito.never()).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc",
            "0".repeat(64), VerificationMode.ENFORCE.policyVersion, "ORCID", orcid, "{}", "0".repeat(64))), anyValue(now))
        cancelServer.verify()
    }

    @Test fun `oversized skipped evidence freezes registration with the original token`() {
        var capturedValue: VerificationReservation? = null
        Mockito.doAnswer { invocation ->
            capturedValue = invocation.getArgument(0)
            item(invocation.getArgument(0))
        }.`when`(repository).reserveItem(anyValue(VerificationReservation(7, "EMAIL-doc", "0".repeat(64),
            VerificationMode.ENFORCE.policyVersion, "OPENALEX", "A123", "{}", "0".repeat(64))), anyValue(now))
        server.expect(ExpectedCount.never(), anything())
        val long = "Example University " + "x".repeat(2003)
        val base = openAlexProfile(long)
        val registration = service().reserve(context(), "EMAIL-doc", base, "OPENALEX", "A123", openAlexPaper(long))
        server.verify()
        val input = requireNotNull(capturedValue)
        val root = mapper.readTree(input.snapshotJson)
        assertEquals(DiscoveryCountrySupplementService.STATUS_SKIPPED, root.path("countrySupplement").path("status").asText())
        assertEquals(DiscoveryCountrySupplementService.REASON_EVIDENCE_TOO_LARGE,
            root.path("countrySupplement").path("reason").asText())
        assertTrue(root.path("profile").path("country").isNull)
        assertEquals(base.institutionEvidence, root.path("profile").path("institutionEvidence").asText())
        assertTrue(root.path("countrySupplement").path("observations").size() <=
            DiscoveryCountrySupplementService.MAX_OBSERVATIONS)
        assertNull(registration.preparedProfile!!.country)
    }
}
