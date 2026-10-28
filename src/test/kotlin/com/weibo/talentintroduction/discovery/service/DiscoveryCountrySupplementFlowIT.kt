package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.CountryContinentMapping
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.domain.IdentityVerification
import com.weibo.talentintroduction.expert.service.ExpertSearchService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import com.weibo.talentintroduction.discovery.repository.VerificationReportFilter
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Real MySQL registration plus the real worker/Gate/Commit/QueryService, with provider HTTP confined to
 * deterministic mocks. Proves that the frozen snapshot and RAW carry the same final country and token.
 */
class DiscoveryCountrySupplementFlowIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("country_supplement_flow").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1")
        private lateinit var jdbc: JdbcTemplate
        private lateinit var manager: DataSourceTransactionManager
        private const val ORCID = "0000-0002-1825-0097"

        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required; skipping is not PASS" }
            mysql.start()
            val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("161").load().migrate()
            jdbc = JdbcTemplate(ds)
            manager = DataSourceTransactionManager(ds)
        }

        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }

        /** Standalone runtime proof outside JUnit: real MySQL, real registration, snapshot == RAW. */
        @JvmStatic fun main(args: Array<String>) {
            start()
            try {
                val mapper = jacksonObjectMapper()
                Fixture().use { f ->
                    f.task(2001); val context = f.begin()
                    val paper = openAlexPaper(mapper)
                    val base = openAlexProfile(mapper)
                    val registration = f.registration.reserve(context, "EMAIL-runtime-oa", base, "OPENALEX", "DOI:10.example/test", paper)
                    val frozen = requireNotNull(registration.preparedProfile)
                    f.raw("EMAIL-runtime-oa", frozen)
                    val persisted = f.persistedProfile(registration.item!!)
                    val receipt = f.receipt(registration.item!!)
                    check(receipt.path("status").asText() == "FILLED" && receipt.path("afterCountry").asText() == "US")
                    check(persisted.country == "US" && f.raws.getValue("EMAIL-runtime-oa")["country"] == "US")
                    check(persisted.institutionEvidence == f.raws.getValue("EMAIL-runtime-oa")["institutionEvidence"])
                    check(DiscoveryIdentity.validInstitutionEvidence(persisted))
                    check(f.supplementRequests.isEmpty())
                    println("RUNTIME_COUNTRY_SMOKE doc=runtime-oa receipt=FILLED/OPENALEX/US snapshotCountry=${persisted.country} rawCountry=${f.raws.getValue("EMAIL-runtime-oa")["country"]} " +
                        "token=${persisted.institutionEvidence?.take(9)} validInstitutionEvidence=true supplementHttp=${f.supplementRequests.size}")
                }
                Fixture().use { f ->
                    f.task(2002); val context = f.begin(2002)
                    f.expectEmployments("GB")
                    val registration = f.registration.reserve(context, "EMAIL-runtime-orcid", orcidProfile(jacksonObjectMapper()), "ORCID", ORCID)
                    val frozen = requireNotNull(registration.preparedProfile)
                    f.raw("EMAIL-runtime-orcid", frozen)
                    val persisted = f.persistedProfile(registration.item!!)
                    val receipt = f.receipt(registration.item!!)
                    check(receipt.path("selectedSource").asText() == "ORCID" && persisted.country == "GB")
                    check(f.raws.getValue("EMAIL-runtime-orcid")["country"] == "GB")
                    check(DiscoveryIdentity.validInstitutionEvidence(persisted))
                    check(f.supplementRequests.size == 1)
                    println("RUNTIME_COUNTRY_SMOKE doc=runtime-orcid receipt=FILLED/ORCID/GB snapshotCountry=${persisted.country} rawCountry=${f.raws.getValue("EMAIL-runtime-orcid")["country"]} " +
                        "token=${persisted.institutionEvidence?.take(7)} validInstitutionEvidence=true supplementHttp=1")
                }
            } finally { stop() }
        }

        private fun openAlexProfile(mapper: com.fasterxml.jackson.databind.ObjectMapper): ExpertProfile {
            val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
                "JATS_SHA256", "a".repeat(64), ORCID, "A123")
            val base = ExpertProfile(orcidId = "EMAIL-ada", email = "ada@example.invalid", givenNames = "Ada",
                familyNames = "Lee", country = null, keyword = null, employment = null, institution = "Example University",
                emailSource = "PAPER_FULLTEXT", dataSource = "OPENALEX",
                externalIds = mapper.writeValueAsString(mapOf("orcid" to ORCID, "doi" to "10.example/test",
                    "openAlexAuthorId" to "A123")),
                identityVerification = proof)
            return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "OPENALEX"))
        }

        private fun openAlexPaper(mapper: com.fasterxml.jackson.databind.ObjectMapper) =
            com.weibo.talentintroduction.discovery.domain.PaperMetadata(pmcId = null, pmid = null, doi = "10.example/test",
                title = "Synthetic paper", pubYear = 2026, journal = null, source = "OPENALEX",
                authors = listOf(com.weibo.talentintroduction.discovery.domain.PaperAuthor("Ada", "Lee", null, null,
                    openAlexAuthorId = "A123", institutionName = "Example University", institutionCountry = "US",
                    institutionSource = "OPENALEX")))

        private fun orcidProfile(mapper: com.fasterxml.jackson.databind.ObjectMapper): ExpertProfile {
            val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
                "ORCID_RECORD_SHA256", "b".repeat(64), ORCID, null)
            val base = ExpertProfile(orcidId = ORCID, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
                country = null, keyword = null, employment = null, institution = "Example University",
                emailSource = "ORCID_PUBLIC", dataSource = "ORCID", externalIds = mapper.writeValueAsString(mapOf("orcid" to ORCID)),
                identityVerification = proof)
            return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "ORCID"))
        }

        @Suppress("UNCHECKED_CAST")
        private fun rawMap(mapper: com.fasterxml.jackson.databind.ObjectMapper, profile: ExpertProfile): Map<String, Any?> =
            mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
    }

    private class Fixture : AutoCloseable {
        private val mapper = jacksonObjectMapper()
        val props = DiscoveryVerificationProperties(enabled = true, workerEnabled = true, mode = VerificationMode.ENFORCE,
            apolloApiKey = "fixture-key", requestsPerMinute = 1000, countrySupplementEnabled = true, concurrency = 1)
        val flow = VerificationFlowFixture(jdbc, manager, props)
        init { flow.saveApolloSettings(true, true) }
        private val http = countrySupplementRestTemplate()
        private val server = MockRestServiceServer.bindTo(http).build()
        val supplementRequests = mutableListOf<String>()
        private val supplement = DiscoveryCountrySupplementService(props, flow.clock, http)
        val registration = DiscoveryVerificationRegistrationService(props, flow.repository, flow.writer, flow.policy,
            flow.admissions, jdbc, TransactionTemplate(manager), flow.apolloSettings, flow.clock, supplement)
        val raws get() = flow.raws

        init {
            server.expect(ExpectedCount.between(0, Int.MAX_VALUE), anything()).andRespond { request ->
                supplementRequests += request.uri.toString()
                withSuccess(employmentBody("US"), MediaType.APPLICATION_JSON).createResponse(request)
            }
        }

        fun task(id: Long) {
            jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (?,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)",
                id, flow.clock.instant().verificationUtc())
        }

        fun begin(execution: Long = 2001): DiscoveryVerificationExecution = registration.begin(execution,
            registration.resolveOptions(PaperSearchCriteria(sources = listOf("OPENALEX"), apolloAssist = true)))

        fun expectEmployments(country: String, status: Int = 200) {
            server.reset()
            server.expect(ExpectedCount.once(), requestTo("https://pub.orcid.org/v3.0/$ORCID/employments")).andRespond { request ->
                supplementRequests += request.uri.toString()
                withSuccess(employmentBody(country), MediaType.APPLICATION_JSON).createResponse(request)
            }
        }

        fun expectEmploymentsStatus(status: Int) {
            server.reset()
            server.expect(ExpectedCount.once(), requestTo("https://pub.orcid.org/v3.0/$ORCID/employments")).andRespond { request ->
                supplementRequests += request.uri.toString()
                withStatus(HttpStatus.valueOf(status)).headers(org.springframework.http.HttpHeaders().apply {
                    set("Retry-After", "60")
                }).createResponse(request)
            }
        }

        fun expectTimeout() {
            server.reset()
            server.expect(ExpectedCount.once(), anything()).andRespond { request ->
                supplementRequests += request.uri.toString()
                throw SocketTimeoutException("fixture")
            }
        }

        fun expectInvalidJson() {
            server.reset()
            server.expect(ExpectedCount.once(), anything()).andRespond { request ->
                supplementRequests += request.uri.toString()
                withSuccess("{not-json", MediaType.APPLICATION_JSON).createResponse(request)
            }
        }

        fun verifyHttp() = server.verify()

        fun persistedProfile(item: VerificationItem): ExpertProfile {
            val root = mapper.readTree(item.snapshotJson)
            return mapper.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(root.path("profile").traverse(), ExpertProfile::class.java)
        }

        fun receipt(item: VerificationItem): com.fasterxml.jackson.databind.JsonNode =
            mapper.readTree(item.snapshotJson).path("countrySupplement")

        @Suppress("UNCHECKED_CAST")
        fun raw(doc: String, profile: ExpertProfile) {
            val map = mapper.convertValue(profile, Map::class.java) as MutableMap<String, Any?>
            // toIndexMap writes externalIds as a parsed object; the reader re-serializes that member.
            profile.externalIds?.let { map["externalIds"] = mapper.readValue(it, Map::class.java) }
            flow.raws[doc] = map
        }

        fun settle(execution: Long = 2001) {
            registration.closeInput(registration.begin(execution, PaperSearchCriteria()))
            jdbc.update("UPDATE task_execution SET finished_at=? WHERE id=?", flow.clock.instant().verificationUtc(), execution)
        }

        override fun close() = flow.close()

        private fun employmentBody(country: String) = """
            {"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":1,"visibility":"public",
            "role-title":"Professor","organization":{"name":"Example University","address":{"country":{"value":"$country"}}},
            "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}}}}]}]}
        """.trimIndent()
    }

    @BeforeEach fun clean() {
        listOf("discovery_apollo_setting", "expert_email_alias", "expert_contact", "discovery_verification_attempt", "discovery_verification_item",
            "discovery_verification_run", "expert_discovery_admission", "task_execution").forEach {
            jdbc.update("DELETE FROM $it")
        }
    }

    private fun oaProfile(): ExpertProfile {
        val mapper = jacksonObjectMapper()
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "JATS_SHA256", "a".repeat(64), ORCID, "A123")
        val base = ExpertProfile(orcidId = "EMAIL-ada", email = "ada@example.invalid", givenNames = "Ada",
            familyNames = "Lee", country = null, keyword = null, employment = null, institution = "Example University",
            emailSource = "PAPER_FULLTEXT", dataSource = "OPENALEX",
            externalIds = mapper.writeValueAsString(mapOf("orcid" to ORCID, "doi" to "10.example/test",
                "openAlexAuthorId" to "A123")),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "OPENALEX"))
    }

    private fun oaPaper() = com.weibo.talentintroduction.discovery.domain.PaperMetadata(pmcId = null, pmid = null,
        doi = "10.example/test", title = "Synthetic paper", pubYear = 2026, journal = null, source = "OPENALEX",
        authors = listOf(com.weibo.talentintroduction.discovery.domain.PaperAuthor("Ada", "Lee", null, null,
            openAlexAuthorId = "A123", institutionName = "Example University", institutionCountry = "US",
            institutionSource = "OPENALEX")))

    private fun resigned(base: ExpertProfile, country: String?): ExpertProfile {
        val changed = base.copy(country = country)
        return changed.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(changed,
            if (base.institutionEvidence?.startsWith("ORCID:") == true) "ORCID" else "OPENALEX"))
    }

    private fun orcidProfile(): ExpertProfile {
        val mapper = jacksonObjectMapper()
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "ORCID_RECORD_SHA256", "b".repeat(64), ORCID, null)
        val base = ExpertProfile(orcidId = ORCID, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
            country = null, keyword = null, employment = null, institution = "Example University",
            emailSource = "ORCID_PUBLIC", dataSource = "ORCID", externalIds = mapper.writeValueAsString(mapOf("orcid" to ORCID)),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "ORCID"))
    }

    private fun jatsProfile(): ExpertProfile {
        val mapper = jacksonObjectMapper()
        val proof = IdentityVerification("VERIFIED", DiscoveryIdentity.VERSION, "ada@example.invalid", "Ada", "Lee",
            "JATS_SHA256", "a".repeat(64), ORCID, null)
        val base = ExpertProfile(orcidId = ORCID, email = "ada@example.invalid", givenNames = "Ada", familyNames = "Lee",
            country = null, keyword = null, employment = null, institution = "Example University",
            emailSource = "PAPER_FULLTEXT", dataSource = "JATS",
            externalIds = mapper.writeValueAsString(mapOf("pmcId" to "PMC123456", "orcid" to ORCID)),
            identityVerification = proof)
        return base.copy(institutionEvidence = DiscoveryIdentity.institutionEvidence(base, "JATS"))
    }

    @Test fun `JATS original token retains its source in snapshot and RAW after an ORCID fill`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            f.expectEmployments("GB")
            val registration = f.registration.reserve(context, "EMAIL-jats-doc", jatsProfile(), "JATS", "PMC123456")
            f.verifyHttp()
            val frozen = requireNotNull(registration.preparedProfile)
            assertEquals("JATS", frozen.institutionEvidence!!.substringBefore(':'))
            assertEquals("GB", frozen.country)
            assertTrue(DiscoveryIdentity.validInstitutionEvidence(frozen))
            f.raw("EMAIL-jats-doc", frozen)
            assertEquals("GB", f.raws.getValue("EMAIL-jats-doc")["country"])
            assertEquals(frozen.institutionEvidence, f.raws.getValue("EMAIL-jats-doc")["institutionEvidence"])
            assertEquals("JATS", f.persistedProfile(registration.item!!).institutionEvidence!!.substringBefore(':'))
            assertEquals("JATS", f.receipt(registration.item!!).path("originalInstitutionEvidence").asText().substringBefore(':'))
            assertEquals("ORCID", f.receipt(registration.item!!).path("selectedSource").asText())
        }
    }

    @Test fun `OPENALEX match freezes country without a request and projects the same value to candidate`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            val base = oaProfile()
            val registration = f.registration.reserve(context, "EMAIL-oa-doc", base, "OPENALEX", "DOI:10.example/test", oaPaper())
            f.verifyHttp()
            val frozen = requireNotNull(registration.preparedProfile)
            assertEquals("US", frozen.country)
            assertNotEquals(base.institutionEvidence, frozen.institutionEvidence)
            assertTrue(DiscoveryIdentity.validInstitutionEvidence(frozen))
            f.raw("EMAIL-oa-doc", frozen)
            f.registration.activate(registration)
            assertEquals("US", f.raws.getValue("EMAIL-oa-doc")["country"])
            assertEquals(frozen.institutionEvidence, f.raws.getValue("EMAIL-oa-doc")["institutionEvidence"])
            val receipt = f.receipt(registration.item!!)
            assertEquals("COUNTRY_V1", receipt.path("version").asText())
            assertEquals("FILLED", receipt.path("status").asText())
            assertEquals("OPENALEX", receipt.path("selectedSource").asText())
            assertEquals("US", receipt.path("afterCountry").asText())
            f.settle()
            f.flow.worker().tickAndAwait()
            assertEquals(VerificationState.COMPLETED, f.flow.item(registration.item!!.id).state)
            assertEquals("US", f.flow.candidates.getValue("EMAIL-oa-doc")["country"])
            assertEquals(frozen.institutionEvidence, f.flow.candidates.getValue("EMAIL-oa-doc")["institutionEvidence"])
            assertEquals(1L, f.flow.autoCount())
            assertTrue(CountryContinentMapping.toRegion(f.flow.candidates.getValue("EMAIL-oa-doc")["country"] as String) ==
                CountryContinentMapping.REGION_NORTH_AMERICA)
            val filter = jacksonObjectMapper().writeValueAsString(
                ExpertSearchService.regionFilter(CountryContinentMapping.REGION_NORTH_AMERICA))
            assertTrue(filter.contains("US") && filter.contains("country"))
            val row = DiscoveryVerificationQueryService(f.flow.repository, f.flow.admissions, f.props)
                .records(VerificationReportFilter(2001), 0, 10).items.single { it.itemId == registration.item!!.id }
            assertEquals("EMAIL-oa-doc", row.docId)
            assertTrue(f.supplementRequests.isEmpty())
        }
    }

    @Test fun `ORCID match issues exactly one request and the worker reuses the frozen country`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            val base = orcidProfile()
            f.expectEmployments("GB")
            val registration = f.registration.reserve(context, "EMAIL-orcid-doc", base, "ORCID", ORCID)
            f.verifyHttp()
            assertEquals(1, f.supplementRequests.size)
            val frozen = requireNotNull(registration.preparedProfile)
            assertEquals("GB", frozen.country)
            assertTrue(DiscoveryIdentity.validInstitutionEvidence(frozen))
            f.raw("EMAIL-orcid-doc", frozen)
            f.registration.activate(registration)
            assertEquals("GB", f.raws.getValue("EMAIL-orcid-doc")["country"])
            assertEquals("GB", f.persistedProfile(registration.item!!).country)
            f.settle()
            f.flow.worker().tickAndAwait()
            assertEquals("GB", f.flow.candidates.getValue("EMAIL-orcid-doc")["country"])
            assertEquals(1, f.supplementRequests.size)
        }
    }

    @Test fun `existing values literal unknown protection and switch off never call the provider`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            val existing = f.registration.reserve(context, "EMAIL-us-doc", resigned(orcidProfile(), "US"), "ORCID", ORCID)
            val literal = f.registration.reserve(context, "EMAIL-unknown-doc", resigned(orcidProfile(), "未知"), "ORCID", ORCID)
            f.raw("EMAIL-protected-doc", orcidProfile())
            val protected = f.registration.reserve(context, "EMAIL-protected-doc", orcidProfile(), "ORCID", ORCID)
            f.verifyHttp()
            assertEquals("US", f.persistedProfile(existing.item!!).country)
            assertEquals("未知", f.persistedProfile(literal.item!!).country)
            assertEquals("UNCHANGED", f.receipt(existing.item!!).path("status").asText())
            assertTrue(protected.protected)
            assertTrue(f.supplementRequests.isEmpty())
        }
        val off = DiscoveryVerificationProperties(enabled = true, mode = VerificationMode.ENFORCE,
            apolloApiKey = "fixture-key", countrySupplementEnabled = false)
        val flow = VerificationFlowFixture(jdbc, manager, off)
        val http = countrySupplementRestTemplate()
        val server = MockRestServiceServer.bindTo(http).build()
        server.expect(ExpectedCount.never(), anything())
        val registration = DiscoveryVerificationRegistrationService(off, flow.repository, flow.writer, flow.policy,
            flow.admissions, jdbc, TransactionTemplate(manager), flow.apolloSettings, flow.clock,
            DiscoveryCountrySupplementService(off, flow.clock, http))
        flow.use {
            flow.saveApolloSettings(true, true)
            jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (2101,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)",
                flow.clock.instant().verificationUtc())
            val context = registration.begin(2101, registration.resolveOptions(PaperSearchCriteria(apolloAssist = true)))
            val result = registration.reserve(context, "EMAIL-off-doc", orcidProfile(), "ORCID", ORCID)
            server.verify()
            val root = jacksonObjectMapper().readTree(result.item!!.snapshotJson)
            assertFalse(root.has("countrySupplement"))
            assertTrue(root.path("profile").path("country").isNull)
        }
    }

    @Test fun `a retried and an old RESERVED item reuse frozen facts without new requests`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            f.expectEmployments("GB")
            val first = f.registration.reserve(context, "EMAIL-retry-doc", orcidProfile(), "ORCID", ORCID)
            f.verifyHttp()
            val frozenJson = first.item!!.snapshotJson
            val retried = f.registration.reserve(context, "EMAIL-retry-doc", orcidProfile(), "ORCID", ORCID)
            assertEquals(1, f.supplementRequests.size)
            assertEquals(frozenJson, retried.item!!.snapshotJson)
            assertEquals(first.item!!.factFingerprint, retried.item!!.factFingerprint)
            assertEquals("GB", retried.preparedProfile!!.country)
            assertEquals(1, f.flow.repository.findItemsByDocIds(listOf("EMAIL-retry-doc")).size)
        }
        Fixture().use { f ->
            f.task(2002)
            val context = f.begin(2002)
            val base = orcidProfile()
            val persisted = base.copy(esDocId = "EMAIL-legacy-doc", orcidId = "EMAIL-legacy-doc")
            val config = f.flow.policy.evaluate("EMAIL-legacy-doc", persisted).configSnapshot
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot("EMAIL-legacy-doc", persisted, config)
            val json = jacksonObjectMapper().writeValueAsString(linkedMapOf("docId" to "EMAIL-legacy-doc",
                "identityHash" to snapshot.identityHash, "factFingerprint" to snapshot.factFingerprint, "profile" to persisted,
                "sourceName" to "ORCID", "sourceItemKey" to ORCID, "constructedAt" to f.flow.clock.instant().toString()))
            f.flow.repository.reserveItem(VerificationReservation(2002, "EMAIL-legacy-doc", snapshot.identityHash,
                VerificationMode.ENFORCE.policyVersion, "ORCID", ORCID, json, snapshot.factFingerprint), f.flow.clock.instant())
            val resumed = f.registration.reserve(context, "EMAIL-legacy-doc", base, "ORCID", ORCID)
            f.verifyHttp()
            assertNull(resumed.preparedProfile!!.country)
            assertEquals(json, resumed.item!!.snapshotJson)
            assertTrue(f.supplementRequests.isEmpty())
        }
    }

    @Test fun `concurrent reservations return the single winning frozen profile`() {
        Fixture().use { f ->
            f.task(2001)
            val firstContext = f.begin()
            val other = countrySupplementRestTemplate()
            val otherServer = MockRestServiceServer.bindTo(other).build()
            otherServer.expect(ExpectedCount.once(), anything()).andRespond(
                withSuccess("""{"affiliation-group":[{"summaries":[{"employment-summary":{"put-code":1,"visibility":"public",
                "role-title":"Professor","organization":{"name":"Example University","address":{"country":{"value":"GB"}}},
                "source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}}}}]}]}""",
                    MediaType.APPLICATION_JSON))
            val otherRegistration = DiscoveryVerificationRegistrationService(f.props, f.flow.repository, f.flow.writer,
                f.flow.policy, f.flow.admissions, jdbc, TransactionTemplate(manager), f.flow.apolloSettings, f.flow.clock,
                DiscoveryCountrySupplementService(f.props, f.flow.clock, other))
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            val futures = listOf(
                executor.submit<DiscoveryRegistration> { start.await(); f.registration.reserve(firstContext, "EMAIL-race-doc", orcidProfile(), "ORCID", ORCID) },
                executor.submit<DiscoveryRegistration> { start.await(); otherRegistration.reserve(firstContext, "EMAIL-race-doc", orcidProfile(), "ORCID", ORCID) }
            )
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }
            executor.shutdown()
            f.verifyHttp(); otherServer.verify()
            assertEquals(1, f.flow.repository.findItemsByDocIds(listOf("EMAIL-race-doc")).size)
            val countries = results.map { it.preparedProfile!!.country }.distinct()
            assertEquals(1, countries.size, "both callers must consume the winner")
            assertTrue(countries.single() in setOf("GB", "US"))
            val snapshotCountry = f.persistedProfile(results.first().item!!).country
            assertEquals(countries.single(), snapshotCountry)
            results.forEach { f.raw("race-${it.hashCode()}", it.preparedProfile!!) }
            assertTrue(f.raws.values.filter { it["institution"] == "Example University" }
                .all { it["country"] == countries.single() })
        }
    }

    @Test fun `bounded failures keep the original collection flow and still freeze a receipt`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            val cases = listOf<Triple<String, () -> Unit, String>>(
                Triple("rate", { f.expectEmploymentsStatus(429) }, "RATE_LIMITED"),
                Triple("server", { f.expectEmploymentsStatus(500) }, "HTTP_ERROR"),
                Triple("invalid", { f.expectInvalidJson() }, "INVALID_RESPONSE"),
                Triple("timeout", { f.expectTimeout() }, "HTTP_ERROR")
            )
            cases.forEach { (name, setup, reason) ->
                setup()
                val result = f.registration.reserve(context, "EMAIL-fail-$name", orcidProfile(), "ORCID", ORCID)
                val receipt = f.receipt(result.item!!)
                assertEquals(reason, receipt.path("reason").asText(), name)
                assertTrue(receipt.path("status").asText() in setOf("ERROR", "SKIPPED"), name)
                assertTrue(f.persistedProfile(result.item!!).country.isNullOrBlank(), name)
                assertTrue(result.preparedProfile!!.country.isNullOrBlank(), name)
                f.raw("EMAIL-fail-$name", result.preparedProfile!!)
                // Clear the 429 channel pause so the next failure case reaches its own HTTP path.
                f.flow.clock.now = f.flow.clock.now.plusSeconds(61)
            }
            // Budget exhausted: ten permits consumed, the next reservation skips without a request.
            val service = DiscoveryCountrySupplementService(f.props, f.flow.clock, countrySupplementRestTemplate())
            repeat(10) { assertEquals(CountryPermit.ACQUIRED, service.tryAcquirePermit()); service.releasePermit() }
            val budgetRegistration = DiscoveryVerificationRegistrationService(f.props, f.flow.repository, f.flow.writer,
                f.flow.policy, f.flow.admissions, jdbc, TransactionTemplate(manager), f.flow.apolloSettings, f.flow.clock, service)
            val budget = budgetRegistration.reserve(context, "EMAIL-fail-budget", orcidProfile(), "ORCID", ORCID)
            assertEquals(DiscoveryCountrySupplementService.REASON_BUDGET_EXHAUSTED,
                f.receipt(budget.item!!).path("reason").asText())
            assertNull(budget.preparedProfile!!.country)
            assertEquals(5, f.flow.repository.findItemsByDocIds(listOf("EMAIL-fail-rate", "EMAIL-fail-server",
                "EMAIL-fail-invalid", "EMAIL-fail-timeout", "EMAIL-fail-budget")).size)
        }
    }

    /** A registration SQL failure leaves no item and no RAW; it is not disguised as a supplement failure. */
    @Test fun `registration SQL failure leaves no item and no RAW`() {
        Fixture().use { f ->
            f.task(2001)
            val context = f.begin()
            f.expectEmployments("GB")
            jdbc.execute("CREATE TRIGGER fail_country_reserve BEFORE INSERT ON discovery_verification_item FOR EACH ROW " +
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected reserve failure'")
            assertThrows(DiscoveryRegistrationException::class.java) {
                f.registration.reserve(context, "EMAIL-sql-fail", orcidProfile(), "ORCID", ORCID)
            }
            jdbc.execute("DROP TRIGGER fail_country_reserve")
            assertTrue(f.flow.repository.findItemsByDocIds(listOf("EMAIL-sql-fail")).isEmpty())
            assertTrue(f.raws.isEmpty())
        }
    }
}
