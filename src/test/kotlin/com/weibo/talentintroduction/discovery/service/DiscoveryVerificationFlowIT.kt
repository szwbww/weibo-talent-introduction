package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.web.client.RestTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

class VerificationFlowClock : Clock() {
    var now: Instant = Instant.parse("2026-10-18T00:00:00Z")
    override fun instant() = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}

/** Disposable MySQL plus the real worker, clients, policy/gate, CAS writer and transaction proxies. */
class VerificationFlowFixture(val jdbc: JdbcTemplate, private val manager: DataSourceTransactionManager,
    val props: DiscoveryVerificationProperties = DiscoveryVerificationProperties(workerEnabled = true, apolloApiKey = "fixture-key", requestsPerMinute = 1000)) : AutoCloseable {
    val mapper = jacksonObjectMapper().findAndRegisterModules()
    val clock = VerificationFlowClock()
    private fun <T : Any> transactional(target: T): T {
        val factory = ProxyFactory(target)
        factory.isProxyTargetClass = true
        factory.addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
        @Suppress("UNCHECKED_CAST") return factory.proxy as T
    }
    val repository = transactional(DiscoveryVerificationRepository(jdbc))
    val admissions = DiscoveryReviewRepository(jdbc)
    val filters = Mockito.mock(EligibilityFilterService::class.java).also {
        Mockito.`when`(it.getCandidateFilter()).thenReturn(CandidateFilterProperties(requireValidEmail = false, requireDoctoralDegree = true, enableAgeFilter = true))
        Mockito.`when`(it.getAcademicFilter()).thenReturn(AcademicFilterProperties())
    }
    val eligibility = CandidateEligibilityService(filters, Mockito.mock(EmailValidationService::class.java))
    val rules = DiscoveryVerificationRules(clock)
    val policy = DiscoveryAdmissionPolicy(eligibility, filters, clock, DiscoveryVerificationAdmissionGate(repository, rules))
    val esHttp = RestTemplate()
    val esProperties = ElasticsearchProperties("http://fixture.invalid", "u", "p", "raw", "candidate", "application")
    val indices = ExpertIndexService(esProperties, esHttp, mapper)
    val writer = ExpertIndexWriterService(esHttp, esProperties, indices, mapper,
        Mockito.mock(ExpertPromotionAuditService::class.java), Mockito.mock(ExpertContactRepository::class.java))
    val review = DiscoveryReviewService(admissions, policy, writer, esHttp, esProperties, indices, mapper, clock, jdbcTemplate = jdbc)
    val commits = transactional(DiscoveryVerificationCommitService(repository, admissions, review, policy, writer, jdbc, clock))
    val raws = mutableMapOf<String, Map<String, Any?>>()
    val candidates = mutableMapOf<String, Map<String, Any?>>()
    val applications = mutableMapOf<String, Map<String, Any?>>()
    var failEs = false
    var failProjection = false
    var jobRole: String? = "Researcher"
    var apolloStatus = 200
    var apolloBody = """{"people":[{"id":"person-1","name":"Ada Lee","first_name":"Ada","last_name":"Lee","organization":{"name":"Example University"}}],"total_entries":1}"""
    var orcidFailEndpoint: String? = null
    var onApollo: (() -> Unit)? = null
    val orcidRequests = mutableListOf<String>()
    val apolloRequests = mutableListOf<String>()
    private val es = MockRestServiceServer.bindTo(esHttp).build()
    private val providerHttp = verificationRestTemplate(props)
    private val provider = MockRestServiceServer.bindTo(providerHttp).build()
    private val workers = mutableListOf<DiscoveryVerificationWorker>()
    init {
        es.expect(ExpectedCount.manyTimes(), anything()).andRespond { request ->
            val segments = request.uri.path.trim('/').split('/')
            val index = segments[0]
            val doc = segments.last()
            val documents = when (index) { "raw" -> raws; "candidate" -> candidates; else -> applications }
            if (failEs || request.method == HttpMethod.PUT && failProjection) withServerError().createResponse(request)
            else if (request.method == HttpMethod.PUT) {
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                documents[doc] = mapper.readValue(body, Map::class.java) as Map<String, Any?>
                withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request)
            } else if (!documents.containsKey(doc)) withStatus(HttpStatus.NOT_FOUND).createResponse(request)
            else if (request.method == HttpMethod.HEAD) withSuccess().createResponse(request)
            else withSuccess(mapper.writeValueAsString(mapOf("_source" to documents[doc], "_seq_no" to 1, "_primary_term" to 1)), MediaType.APPLICATION_JSON).createResponse(request)
        }
        provider.expect(ExpectedCount.manyTimes(), anything()).andRespond { request ->
            if (request.uri.host == "api.apollo.io") {
                assertEquals("/api/v1/mixed_people/api_search", request.uri.path)
                assertFalse(request.uri.toString().contains("example.invalid"))
                apolloRequests += request.uri.toString()
                onApollo?.invoke()
                if (apolloStatus == 200) withSuccess(apolloBody, MediaType.APPLICATION_JSON).createResponse(request)
                else withStatus(HttpStatus.valueOf(apolloStatus)).headers(org.springframework.http.HttpHeaders().apply { set("Retry-After", "60") }).createResponse(request)
            } else {
                val endpoint = request.uri.path.substringAfter("0000-0002-1825-0097/")
                orcidRequests += endpoint
                if (endpoint == orcidFailEndpoint) withServerError().createResponse(request)
                else {
                    val body = if (endpoint == "employments" && jobRole != null) """{"affiliation-group":[{"summaries":[{"employment-summary":{"role-title":"$jobRole","organization":{"name":"Example University"},"source":{"source-client-id":{"path":"fixture-org"}},"start-date":{"year":{"value":"2020"}},"end-date":null}}]}]}""" else "{}"
                    withSuccess(body, MediaType.APPLICATION_JSON).createResponse(request)
                }
            }
        }
    }
    fun worker() = DiscoveryVerificationWorker(props, repository, OrcidVerificationEvidenceClient(props, providerHttp, clock),
        ApolloPeopleSearchClient(props, providerHttp, clock), QualificationEvidenceParser(clock), rules, commits,
        DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), clock).also { workers += it }
    fun seed(doc: String = "synthetic-1", mode: VerificationMode = VerificationMode.ENFORCE, apollo: Boolean = true): VerificationItem {
        val profile = ruleSnapshot().profile.copy(esDocId = doc, researchFieldIds = listOf("22"), lastPublicationYear = 2026)
        val snapshot = DiscoveryVerificationAdmissionGate.snapshot(doc, profile, policy.evaluate(profile).configSnapshot)
        @Suppress("UNCHECKED_CAST") val source = mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
        raws[doc] = source
        repository.createRun(2001, mode, apollo, "{}", clock.instant())
        val item = repository.reserveItem(VerificationReservation(2001, doc, snapshot.identityHash, mode.policyVersion,
            "ORCID", doc, mapper.writeValueAsString(snapshot), snapshot.factFingerprint), clock.instant())
        return repository.activateItem(item.id, 1, clock.instant())
    }
    fun claim() = repository.claimDue(clock.instant(), 120)!!
    fun item(id: Long) = repository.findItem(id)!!
    fun autoCount() = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE admission_revision IS NOT NULL", Long::class.java)
    fun manual(item: VerificationItem, decision: String = "HOLD") {
        jdbc.update("""INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,updated_at)
            VALUES (?,?,?,1,?) ON DUPLICATE KEY UPDATE decision=VALUES(decision),revision=revision+1""",
            item.expertDocId, item.identityHash, decision, clock.instant().verificationUtc())
    }
    override fun close() { workers.forEach { it.close() } }
}

class DiscoveryVerificationFlowIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("verification_flow").withUsername("test").withPassword("test").withCommand("--log-bin-trust-function-creators=1")
        private lateinit var jdbc: JdbcTemplate
        private lateinit var manager: DataSourceTransactionManager
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required; skipping is not PASS" }
            mysql.start()
            val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
            jdbc = JdbcTemplate(ds)
            manager = DataSourceTransactionManager(ds)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        /** Standalone runtime entry, deliberately outside JUnit: real MySQL + worker restart + ES failure recovery. */
        @JvmStatic fun main(args: Array<String>) {
            start()
            try {
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val item = f.seed(); f.repository.closeInput(2001, f.clock.instant()); f.failProjection = true
                    f.worker().tick()
                    check(f.item(item.id).projectionState == VerificationProjection.ERROR)
                    val requests = f.apolloRequests.size
                    f.failProjection = false
                    f.worker().tick()
                    check(f.autoCount() == 1L && f.item(item.id).projectionState == VerificationProjection.PROJECTED)
                    check(f.apolloRequests.size == requests)
                    println("RUNTIME_SMOKE auto=1 projection=PROJECTED apolloRequests=$requests no-provider-replay=true candidates=${f.candidates.size}")
                }
            } finally { stop() }
        }
    }
    @BeforeEach fun clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_verification_attach")
        jdbc.execute("DROP TRIGGER IF EXISTS fail_attempt_complete")
        listOf("expert_email_alias", "expert_contact", "discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run", "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
    }
    @Test fun `collection SUCCESS survives 429 and process reconstruction with two actual Apollo attempts`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            f.jobRole = null; f.apolloBody = """{"people":[],"total_entries":0}"""; f.apolloStatus = 429
            val item = f.seed(); f.repository.closeInput(2001, f.clock.instant())
            jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (2001,'EXPERT_DISCOVERY','MANUAL','SUCCESS',?)", f.clock.instant().verificationUtc())
            f.worker().tick()
            assertEquals(VerificationState.RETRY_WAIT, f.item(item.id).state)
            assertEquals(0, f.item(item.id).transientAttempts)
            f.apolloStatus = 200; f.clock.now = f.clock.now.plusSeconds(60); f.worker().tick()
            assertEquals(VerificationOutcome.PENDING, f.item(item.id).outcome)
            assertNotNull(f.item(item.id).searchCompletedAt)
            assertEquals(2, f.apolloRequests.size); assertEquals(3, f.orcidRequests.size)
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM task_execution", Long::class.java))
            assertEquals("SUCCESS", jdbc.queryForObject("SELECT status FROM task_execution WHERE id=2001", String::class.java))
        }
    }
    @Test fun `ES failure recovers projection only and existing candidates are not counted as new`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); f.failProjection = true; f.worker().tick()
            assertEquals(VerificationOutcome.APOLLO_UNCLEAR, f.item(item.id).outcome)
            assertEquals(VerificationProjection.ERROR, f.item(item.id).projectionState); assertEquals(1L, f.autoCount())
            val count = f.apolloRequests.size
            f.failProjection = false; f.worker().tick()
            assertEquals(VerificationProjection.PROJECTED, f.item(item.id).projectionState)
            assertEquals(count, f.apolloRequests.size); assertEquals(1L, f.autoCount()); assertEquals(1, f.candidates.size)
        }
        clean()
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); f.candidates[item.expertDocId] = f.raws.getValue(item.expertDocId)
            f.worker().tick()
            assertEquals(VerificationProjection.ALREADY_PRESENT, f.item(item.id).projectionState)
            assertEquals(1, f.candidates.size)
        }
    }
    @Test fun `HOLD before response wins and HOLD after commit preserves history while stopping projection`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); f.onApollo = { f.manual(item) }; f.worker().tick()
            assertEquals("HOLD", f.admissions.findAdmission(item.expertDocId)!!.decision)
            assertEquals(VerificationOutcome.PROTECTED, f.item(item.id).outcome); assertEquals(0L, f.autoCount()); assertTrue(f.candidates.isEmpty())
        }
        clean()
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); f.failProjection = true; f.worker().tick(); f.manual(item)
            f.failProjection = false; val requests = f.apolloRequests.size; f.worker().tick()
            assertEquals("SUPERSEDED_BY_REVIEW", f.item(item.id).projectionError)
            assertEquals(1L, f.autoCount()); assertEquals(requests, f.apolloRequests.size); assertTrue(f.candidates.isEmpty())
        }
    }
    @Test fun `AUDIT stores suggestions but never writes admission or ES`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(mode = VerificationMode.AUDIT); val raw = f.raws.toMap(); f.worker().tick()
            assertEquals(VerificationOutcome.APOLLO_UNCLEAR, f.item(item.id).outcome)
            assertNull(f.admissions.findAdmission(item.expertDocId)); assertEquals(0L, f.autoCount())
            assertEquals(raw, f.raws); assertTrue(f.candidates.isEmpty())
        }
    }
    @Test fun `ORCID completion and cursor survive transient failure and worker reconstruction`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); f.orcidFailEndpoint = "employments"; f.worker().tick()
            assertEquals(listOf("person", "employments"), f.orcidRequests)
            assertEquals(VerificationState.RETRY_WAIT, f.item(item.id).state)
            val saved = f.repository.findAttempts(item.id).first()
            assertEquals("OK", saved.resultCode); assertNotNull(saved.responseJson)
            f.orcidFailEndpoint = null; f.clock.now = f.clock.now.plusSeconds(30); f.worker().tick()
            assertEquals(listOf("person", "employments", "employments", "educations"), f.orcidRequests)
            assertEquals(VerificationOutcome.APOLLO_UNCLEAR, f.item(item.id).outcome)
        }
    }
    @Test fun `attempt persistence failure stops next HTTP and failed admission transaction rolls back evidence too`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed()
            jdbc.execute("CREATE TRIGGER fail_attempt_complete BEFORE UPDATE ON discovery_verification_attempt FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected attempt persistence failure'")
            f.worker().tick()
            assertEquals(listOf("person"), f.orcidRequests); assertTrue(f.apolloRequests.isEmpty())
            assertEquals(VerificationState.RUNNING, f.item(item.id).state)
            assertNull(f.item(item.id).evidenceJson)
            assertEquals("DISPATCH_RESERVED", f.repository.findAttempts(item.id).single().resultCode)
        }
        clean()
        VerificationFlowFixture(jdbc, manager).use { f ->
            val item = f.seed(); val lease = f.claim()
            val evidence = listOf(ruleJob("Professor").copy(identityHash = lease.identityHash, factFingerprint = lease.factFingerprint))
            val result = VerificationResult(VerificationState.COMPLETED, VerificationOutcome.DIRECT_CLEAR, VerificationQualification.MET,
                VerificationRoute.DIRECT, f.mapper.writeValueAsString(VerificationWorkerEvidence(evidence)), "[]")
            val input = f.commits.prepare(lease)
            jdbc.execute("""CREATE TRIGGER fail_verification_attach BEFORE UPDATE ON discovery_verification_item FOR EACH ROW
                BEGIN IF NEW.admission_revision IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected atomic commit failure'; END IF; END""")
            assertThrows(Exception::class.java) { f.commits.commit(lease, result, input) }
            assertNull(f.admissions.findAdmission(item.expertDocId)); assertNull(f.item(item.id).evidenceJson)
            assertEquals(VerificationState.RUNNING, f.item(item.id).state)
        }
    }
    @Test fun `identity fact and protection read races fail closed and stale token cannot commit`() {
        listOf("identity", "fact", "read", "lease", "application").forEach { race ->
            clean()
            VerificationFlowFixture(jdbc, manager).use { f ->
                val item = f.seed(); val once = AtomicReference(true)
                f.onApollo = {
                    if (once.getAndSet(false)) when (race) {
                        "identity" -> f.raws[item.expertDocId] = f.raws.getValue(item.expertDocId) + ("email" to "changed@example.invalid")
                        "fact" -> f.raws[item.expertDocId] = f.raws.getValue(item.expertDocId) + ("institution" to "Different University")
                        "read" -> f.failEs = true
                        "application" -> f.applications[item.expertDocId] = f.raws.getValue(item.expertDocId)
                        "lease" -> { f.clock.now = f.clock.now.plusSeconds(121); f.repository.claimDue(f.clock.instant(), 120) }
                    }
                }
                val lease = f.claim(); f.worker().process(lease)
                assertEquals(0L, f.autoCount(), race); assertTrue(f.candidates.isEmpty(), race)
                when (race) {
                    "identity", "fact" -> assertEquals(VerificationOutcome.PENDING, f.item(item.id).outcome, race)
                    "read" -> assertEquals(VerificationState.ERROR, f.item(item.id).state)
                    "application" -> assertEquals(VerificationOutcome.PROTECTED, f.item(item.id).outcome)
                    "lease" -> { assertEquals(VerificationState.RUNNING, f.item(item.id).state); assertNotEquals(lease.leaseToken, f.item(item.id).leaseToken) }
                }
            }
        }
    }
    @Test fun `403 invalid response page caps item budget and transient exhaustion never pretend no match`() {
        listOf("403", "invalid", "pages", "budget", "transient").forEach { scenario ->
            clean()
            val props = DiscoveryVerificationProperties(workerEnabled = true, apolloApiKey = "fixture", requestsPerMinute = 1000,
                maxPagesPerQuery = 1, maxRequestsPerItem = if (scenario == "budget") 3 else 12)
            VerificationFlowFixture(jdbc, manager, props).use { f ->
                val item = f.seed()
                when (scenario) {
                    "403" -> f.apolloStatus = 403
                    "invalid" -> f.apolloBody = "{}"
                    "pages" -> f.apolloBody = """{"people":[],"total_entries":101}"""
                    "transient" -> f.apolloStatus = 500
                }
                val worker = f.worker(); worker.tick()
                if (scenario == "transient") {
                    f.clock.now = f.clock.now.plusSeconds(30); worker.tick()
                    f.clock.now = f.clock.now.plusSeconds(120); worker.tick()
                }
                val done = f.item(item.id)
                assertEquals(0L, f.autoCount())
                if (scenario in listOf("pages", "budget")) {
                    assertEquals(VerificationOutcome.PENDING, done.outcome)
                    assertTrue(done.reasonsJson!!.contains("SEARCH_INCOMPLETE"))
                } else assertEquals(VerificationState.ERROR, done.state)
                assertNull(done.searchCompletedAt)
                if (scenario == "transient") {
                    assertEquals(3, f.apolloRequests.size)
                    assertEquals(3, done.transientAttempts)
                }
                if (scenario == "403") {
                    f.seed("synthetic-2"); worker.tick()
                    assertEquals(1, f.apolloRequests.size)
                }
            }
        }
    }

    @Test fun `successful broad page union resumes at the failed page not page one`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            f.jobRole = null
            val item = f.seed()
            var secondPageFailed = false
            f.onApollo = {
                val secondPage = f.apolloRequests.last().contains("page=2")
                f.apolloStatus = if (secondPage && !secondPageFailed) 429 else 200
                if (secondPage) secondPageFailed = true
                f.apolloBody = if (secondPage) """{"people":[{"id":"second"}],"total_entries":101}"""
                    else """{"people":[{"id":"first"}],"total_entries":101}"""
            }
            f.worker().tick()
            assertEquals(VerificationState.RETRY_WAIT, f.item(item.id).state)
            assertNull(f.item(item.id).searchCompletedAt)
            f.clock.now = f.clock.now.plusSeconds(60); f.worker().tick()
            val saved = f.mapper.readTree(f.item(item.id).evidenceJson)
            assertEquals(setOf("first", "second"), saved.path("search").path("candidates").map { it.path("id").asText() }.toSet())
            assertNotNull(f.item(item.id).searchCompletedAt)
            assertEquals(3, f.apolloRequests.size)
            assertEquals(1, f.apolloRequests.count { java.net.URI(it).query.split('&').contains("page=1") })
        }
    }

    @Test fun `reliable persisted local evidence bypasses all HTTP and direct ORCID professor bypasses Apollo`() {
        listOf(true, false).forEach { alreadyPersisted ->
            clean()
            VerificationFlowFixture(jdbc, manager).use { f ->
                val item = f.seed()
                if (alreadyPersisted) {
                    val lease = f.claim()
                    val fact = ruleJob("Professor").copy(identityHash = lease.identityHash, factFingerprint = lease.factFingerprint)
                    f.repository.reschedule(lease.id, lease.leaseToken!!, lease.generation, f.clock.instant(), 0,
                        f.mapper.writeValueAsString(VerificationWorkerEvidence(listOf(fact))), "[]", f.clock.instant())
                } else f.jobRole = "Professor"
                f.worker().tick()
                assertEquals(VerificationOutcome.DIRECT_CLEAR, f.item(item.id).outcome)
                assertEquals(1L, f.autoCount()); assertTrue(f.apolloRequests.isEmpty())
                if (alreadyPersisted) assertTrue(f.orcidRequests.isEmpty())
                assertNull(f.item(item.id).apolloEnteredAt)
            }
        }
    }

    @Test fun `late external ORCID contact blocks persisted RAW before prepare and during commit`() {
        listOf(false, true).forEach { afterPrepare ->
            listOf("0000-0002-1825-0097", "https://orcid.org/0000-0002-1825-0097").forEach { externalOrcid ->
                clean()
                VerificationFlowFixture(jdbc, manager).use { f ->
                    val doc = "persisted-orcid-contact"
                    @Suppress("UNCHECKED_CAST")
                    val source = f.mapper.convertValue(ruleSnapshot().profile.copy(
                        esDocId = doc, orcidId = doc, researchFieldIds = listOf("22"), lastPublicationYear = 2026),
                        Map::class.java) as Map<String, Any?>
                    f.raws[doc] = source + ("externalIds" to mapOf("orcid" to externalOrcid))
                    val profile = f.writer.discoveryProfile(doc, f.raws.getValue(doc))
                    val snapshot = DiscoveryVerificationAdmissionGate.snapshot(doc, profile, f.policy.evaluate(doc, profile).configSnapshot)
                    f.repository.createRun(2001, VerificationMode.ENFORCE, true, "{}", f.clock.instant())
                    val reserved = f.repository.reserveItem(VerificationReservation(2001, doc, snapshot.identityHash,
                        VerificationMode.ENFORCE.policyVersion, "ORCID", doc, f.mapper.writeValueAsString(snapshot),
                        snapshot.factFingerprint), f.clock.instant())
                    val item = f.repository.activateItem(reserved.id, 1, f.clock.instant())
                    val lease = f.claim()
                    val facts = listOf(ruleJob("Professor").copy(identityHash = lease.identityHash, factFingerprint = lease.factFingerprint))
                    val decision = f.rules.evaluate(snapshot, facts)
                    assertEquals(VerificationOutcome.DIRECT_CLEAR, decision.outcome)
                    val proposed = VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification,
                        decision.route, f.mapper.writeValueAsString(VerificationWorkerEvidence(facts)),
                        f.mapper.writeValueAsString(decision.reasonCodes))
                    val prepared = if (afterPrepare) f.commits.prepare(lease).also { assertNull(it.protection) } else null
                    jdbc.update("""INSERT INTO expert_contact (campaign_id,orcid_id,expert_email)
                        SELECT MIN(id),'0000-0002-1825-0097','unrelated@example.invalid' FROM campaign""")
                    val contacts = jdbc.queryForList("SELECT * FROM expert_contact")
                    val admission = f.admissions.findAdmission(doc)
                    val input = prepared ?: f.commits.prepare(lease)
                    val committed = f.commits.commit(lease, proposed, input)!!
                    f.commits.project(committed)
                    assertEquals(VerificationOutcome.PROTECTED, committed.outcome, "$afterPrepare/$externalOrcid")
                    assertEquals(listOf("CONTACT_PROTECTED"), f.mapper.readValue(committed.reasonsJson, List::class.java))
                    assertEquals(VerificationProjection.NOT_REQUIRED, committed.projectionState)
                    assertNull(committed.admissionRevision)
                    assertEquals(admission, f.admissions.findAdmission(doc))
                    assertEquals(contacts, jdbc.queryForList("SELECT * FROM expert_contact"))
                    assertEquals(0L, f.autoCount())
                    assertTrue(f.candidates.isEmpty())
                    assertEquals(snapshot.factFingerprint, input.snapshot!!.factFingerprint)
                    assertEquals(snapshot.factFingerprint, f.item(item.id).factFingerprint)
                    assertEquals(doc, f.raws.getValue(doc)["orcidId"])
                    assertEquals(source + ("externalIds" to mapOf("orcid" to externalOrcid)), f.raws.getValue(doc))
                    assertTrue(f.orcidRequests.isEmpty()); assertTrue(f.apolloRequests.isEmpty())
                }
            }
        }
    }

    @Test fun `all protected families retain their admission and never create a candidate`() {
        listOf("HOLD", "REJECTED", "MANUAL_APPROVED", "LEGACY_APPROVED", "SBIR", "CONTACT_ALIAS", "APPLICATION").forEach { protection ->
            clean()
            VerificationFlowFixture(jdbc, manager).use { f ->
                val item = f.seed()
                when (protection) {
                    "SBIR" -> f.raws[item.expertDocId] = f.raws.getValue(item.expertDocId) + ("dataSource" to "SBIR")
                    "APPLICATION" -> f.applications[item.expertDocId] = f.raws.getValue(item.expertDocId)
                    "CONTACT_ALIAS" -> {
                        jdbc.update("""INSERT INTO expert_contact (campaign_id,orcid_id,expert_email) SELECT MIN(id),'fixture-other','other@example.invalid' FROM campaign""")
                        jdbc.update("""INSERT INTO expert_email_alias (expert_contact_id,email,normalized_email)
                            SELECT MAX(id),'ada@example.invalid','ada@example.invalid' FROM expert_contact""")
                    }
                    else -> f.manual(item, protection)
                }
                val before = f.admissions.findAdmission(item.expertDocId)
                f.worker().tick()
                assertEquals(VerificationOutcome.PROTECTED, f.item(item.id).outcome, protection)
                assertEquals(before, f.admissions.findAdmission(item.expertDocId), protection)
                assertEquals(0L, f.autoCount()); assertTrue(f.candidates.isEmpty())
            }
        }
    }
}
