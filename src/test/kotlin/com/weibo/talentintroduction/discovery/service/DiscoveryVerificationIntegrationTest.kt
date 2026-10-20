package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.controller.ExpertDiscoveryController
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.domain.*
import com.weibo.talentintroduction.expert.service.*
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real ledger/launch transactions and real discovery consumer, with network confined to mocks. */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
class DiscoveryVerificationIntegrationTest {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("verification_registration").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1")
        lateinit var jdbc: JdbcTemplate
        lateinit var manager: DataSourceTransactionManager
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required; skip is not PASS" }
            mysql.start()
            val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("158").load().migrate()
            jdbc = JdbcTemplate(ds)
            manager = DataSourceTransactionManager(ds)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
    }

    @BeforeEach fun clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_registration_reserve")
        jdbc.execute("DROP TRIGGER IF EXISTS fail_registration_activate")
        listOf("discovery_paper_job", "discovery_collection_stream", "discovery_pipeline", "discovery_source_cursor",
            "expert_email_alias", "expert_contact", "discovery_verification_attempt", "discovery_verification_item",
            "discovery_verification_run", "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
    }

    @Test fun `SELF professor is directly admitted with provenance and without Apollo requests`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val row = f.seed()
            f.orcidBodies["employments"] = f.selfDocuments()[1].document!!.body.toString()
            f.worker().process(f.claim())
            val done = f.item(row.id)
            assertEquals(VerificationOutcome.DIRECT_CLEAR, done.outcome)
            assertEquals(VerificationProjection.PROJECTED, done.projectionState)
            assertTrue(f.apolloRequests.isEmpty())
            val payload = f.mapper.readValue(done.evidenceJson, VerificationWorkerEvidence::class.java)
            assertEquals(1, payload.ruleVersion)
            assertEquals(AssertionSource.SELF, payload.evidence.single().assertionSource)
            assertEquals(1L, f.autoCount())
        }
    }

    @Test fun `surname fallback persists before detail throttling and resumes through real admission gate`() {
        VerificationFlowFixture(jdbc, manager).use { f ->
            val row = f.seed()
            val old = f.mapper.readValue(row.snapshotJson, VerificationSnapshot::class.java)
            val profile = old.profile.copy(givenNames = "Adaline", identityVerification = DiscoveryIdentity.verified(
                "ada@example.invalid", "Adaline", "Lee", "ORCID_RECORD_SHA256:" + "a".repeat(64), "0000-0002-1825-0097", null))
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot(row.expertDocId, profile, f.policy.evaluate(profile).configSnapshot)
            @Suppress("UNCHECKED_CAST")
            val source = f.mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
            f.raws[row.expertDocId] = source
            jdbc.update("UPDATE discovery_verification_item SET snapshot_json=?,identity_hash=?,fact_fingerprint=? WHERE id=?",
                f.mapper.writeValueAsString(snapshot), snapshot.identityHash, snapshot.factFingerprint, row.id)
            jdbc.update("UPDATE discovery_verification_run SET settings_json=? WHERE execution_id=2001", """{"apolloMatchingVersion":2}""")
            f.onApollo = {
                f.apolloBody = if (f.apolloRequests.last().contains("q_person_name=lee&"))
                    """{"people":[{"id":"person-1","first_name":"Ada","last_name_obfuscated":"L**","title":"Researcher","organization":{"name":"Example University"}}],"total_entries":1}"""
                else """{"people":[],"total_entries":0}"""
            }
            f.detailStatus = 429
            f.worker().process(f.claim())
            val waiting = f.item(row.id)
            assertEquals(VerificationState.RETRY_WAIT, waiting.state)
            val saved = f.mapper.readValue(waiting.evidenceJson, VerificationWorkerEvidence::class.java)
            assertEquals(1, saved.ruleVersion)
            assertEquals(listOf("Adaline Lee", "Adaline Lee", "lee"), saved.queries!!.map { it.name })
            assertEquals("Example University", saved.queries!!.last().institution!!.name)
            val searches = f.apolloRequests.count { it.contains("api_search") }
            assertEquals(3, searches)
            f.detailStatus = 200
            f.clock.now = f.clock.now.plusSeconds(61)
            f.worker().process(f.claim())
            val done = f.item(row.id)
            assertEquals(VerificationOutcome.APOLLO_CLEAR, done.outcome)
            assertTrue(done.reasonsJson!!.contains("APOLLO_EMAIL_GIVEN_NAME_MATCH"))
            assertEquals(VerificationProjection.PROJECTED, done.projectionState)
            assertEquals(1L, f.autoCount())
            assertEquals(searches, f.apolloRequests.count { it.contains("api_search") })
            assertEquals(1, f.candidates.size)
        }
    }

    class Fixture(workerEnabled: Boolean = false) : AutoCloseable {
        val properties = DiscoveryVerificationProperties(enabled = true, workerEnabled = workerEnabled, mode = VerificationMode.ENFORCE, apolloApiKey = "offline-fixture")
        val flow = VerificationFlowFixture(jdbc, manager, properties)
        val registration = DiscoveryVerificationRegistrationService(properties, flow.repository, flow.writer, flow.policy,
            flow.admissions, jdbc, TransactionTemplate(manager), flow.clock)
        val beans = DefaultListableBeanFactory()
        inline fun <reified T : Any> provider() = beans.getBeanProvider(T::class.java)
        val source = Mockito.mock(CrossrefDataSource::class.java)
        val orcid = Mockito.mock(OrcidDataSource::class.java)
        val progress = Mockito.mock(TaskProgressStore::class.java)
        val cursors = Mockito.mock(DiscoverySourceCursorRepository::class.java)
        val enrichment = Mockito.mock(ExpertAcademicEnrichmentJobService::class.java)
        val persons = Mockito.mock(DiscoveryPersonRegistrationService::class.java)
        val email = Mockito.mock(EmailValidationService::class.java)
        val taskRepository = Mockito.mock(TaskExecutionRepository::class.java)
        val taskService = TaskExecutionService(taskRepository, flow.mapper, MailSchedulingProperties(autoReplyAllCron = "-"))
        val discoveryProperties = ExpertDiscoveryProperties(enabled = true, includeRawScan = false, maxPapersPerRun = 100, maxAuthorsPerRun = 500)
        var nextExecution = 4000L
        var nextPaper = "manual"
        var sourceCalls = 0
        val savedCursors = mutableMapOf<String, DiscoverySourceCursor>()
        val searchHttp = RestTemplate()
        val service: ExpertDiscoveryService
        val queue: DiscoveryPaperQueueRepository
        init {
            beans.registerSingleton("crossref", source)
            Mockito.doReturn("CROSSREF").`when`(source).sourceName
            Mockito.doReturn("PDF_PARSE").`when`(source).emailExtractionMethod
            Mockito.doReturn(10).`when`(source).maxPapersPerSource
            Mockito.doAnswer {
                sourceCalls++
                PaperSearchResult(listOf(paper(nextPaper)), null, 1)
            }.`when`(source).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
            Mockito.doAnswer { extraction(nextPaper) }.`when`(source).extractAuthorEmails(Mockito.any(PaperMetadata::class.java) ?: paper("unused"))
            Mockito.doReturn(EmailValidationResult(3, true)).`when`(email).validate(Mockito.anyString())
            Mockito.doReturn(DiscoveryPersonRefreshResult(DiscoveryPersonRefreshStatus.CREATED, "fixture-person"))
                .`when`(persons).refresh(Mockito.anyString(), Mockito.anyString())
            Mockito.doReturn(true to -999L).`when`(progress).tryStartWithToken(Mockito.anyString(), Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0))
            // Deliberately stale: verification must never consult this slot for ownership.
            Mockito.doReturn(987654L).`when`(progress).getCurrentExecutionId(Mockito.anyString())
            Mockito.doAnswer { inv ->
                val row = inv.getArgument<TaskExecution>(0)
                val saved = row.copy(id = row.id ?: ++nextExecution)
                jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE status=VALUES(status)""", saved.id, saved.taskType, saved.triggerType, saved.status, saved.startedAt)
                saved
            }.`when`(taskRepository).save(Mockito.any(TaskExecution::class.java))
            Mockito.doAnswer { savedCursors[it.getArgument<String>(0)] }.`when`(cursors).findBySourceName(Mockito.anyString())
            Mockito.doAnswer { inv -> inv.getArgument<DiscoverySourceCursor>(0).also { savedCursors[it.sourceName] = it } }
                .`when`(cursors).save(Mockito.any(DiscoverySourceCursor::class.java))
            val search = MockRestServiceServer.bindTo(searchHttp).build()
            search.expect(ExpectedCount.manyTimes(), anything()).andRespond { request ->
                val body = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                val address = flow.mapper.readTree(body).path("query").path("term").path("email").asText()
                val hit = flow.raws.entries.firstOrNull { it.value["email"] == address }
                val hits = if (hit == null) emptyList() else listOf(mapOf("_id" to hit.key, "_source" to hit.value))
                withSuccess(flow.mapper.writeValueAsString(mapOf("hits" to mapOf("total" to mapOf("value" to hits.size), "hits" to hits))), MediaType.APPLICATION_JSON).createResponse(request)
            }
            service = ExpertDiscoveryService(Mockito.mock(EuropePmcDataSource::class.java), provider(), provider(), provider(), provider(), provider(), provider(),
                email, flow.eligibility, flow.writer, flow.indices, Mockito.mock(ExpertRevalidationService::class.java),
                Mockito.mock(ExpertSearchService::class.java), ExpertClassificationService(), searchHttp, flow.esProperties,
                discoveryProperties, OpenAlexProperties(enabled = false), flow.mapper, progress, cursors, enrichment,
                Mockito.mock(ExpertAcademicEnrichmentJobRepository::class.java), Executor { it.run() }, EuropePmcProperties(enabled = false),
                discoveryReviewService = flow.review, personRegistrationService = persons, verificationRegistration = registration)
            val proxy = ProxyFactory(DiscoveryPaperQueueRepository(jdbc))
            proxy.isProxyTargetClass = true
            proxy.addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
            queue = proxy.proxy as DiscoveryPaperQueueRepository
        }
        fun task(id: Long) { jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at) VALUES (?,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)", id, flow.clock.instant().verificationUtc()) }
        fun context(id: Long, apollo: Boolean = true): DiscoveryVerificationExecution {
            task(id)
            return registration.begin(id, registration.resolveOptions(PaperSearchCriteria(apolloAssist = apollo)))
        }
        fun paper(key: String) = PaperMetadata(pmcId = null, pmid = null, doi = "10.fixture/$key", title = "Synthetic paper $key",
            pubYear = 2026, journal = null, authors = emptyList(), source = "CROSSREF")
        fun extraction(key: String) = EmailExtractionOutcome(
            listOf(AuthorEmail("$key@example.invalid", "Ada", "Lee", false, null, null)),
            "PDF_PARSE", identityRuleVersion = DiscoveryIdentity.EXTRACTION_VERSION)
        fun envelope(key: String) = QueuedItemEnvelope("CROSSREF", "DOI:10.fixture/$key", "DOI", "PAPER", 1,
            flow.mapper.writeValueAsString(paper(key)), 100, true)
        fun consume(key: String, context: DiscoveryVerificationExecution) = service.consumeQueuedItem(envelope(key), flow.mapper.writeValueAsString(extraction(key)), context)
        fun controller(pipeline: DiscoveryPipelineService? = null) = ExpertDiscoveryController(service, taskService, progress,
            Executor { it.run() }, discoveryProperties, provider(), provider(), provider(), provider(), provider(), provider(),
            EuropePmcProperties(enabled = false), pipeline, registration)
        fun pipeline() = DiscoveryPipelineService(queue, service, taskService, progress, discoveryProperties.copy(pipelineEnabled = true),
            flow.mapper, OpenAlexRequestPolicy(OpenAlexProperties(enabled = false)), Executor { it.run() }, Executor { it.run() }, Executor { it.run() },
            object : PipelineTimeSource { override fun now() = flow.clock.instant() }, registration)
        fun itemCount() = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE activated_at IS NOT NULL", Long::class.java)!!
        override fun close() { flow.close() }
    }

    @Test fun `manual keyword scheduled and continuous freeze options and bind actual positive execution IDs`() {
        Fixture().use { f ->
            assertEquals(200, f.controller().triggerDiscovery(null).statusCodeValue)
            f.nextPaper = "keyword"
            assertEquals(200, f.controller().triggerDiscoveryByKeyword(listOf("synthetic"), 2020, 2026, apolloAssist = false).statusCodeValue)
            f.nextPaper = "scheduled"
            ExpertDiscoveryScheduler(f.service, f.taskService, f.discoveryProperties, f.progress, verificationRegistration = f.registration).scheduleDiscovery()
            f.nextPaper = "continuous"
            val pipeline = f.pipeline()
            assertTrue(pipeline.launch(PaperSearchCriteria(sources = listOf("CROSSREF"), keywords = listOf("continuous")), "MANUAL", false).applied)
            assertTrue(pipeline.tick().dispatched)
            val runs = jdbc.queryForList("SELECT execution_id,apollo_requested,input_closed,settings_json FROM discovery_verification_run ORDER BY execution_id")
            assertEquals(4, runs.size)
            assertEquals(listOf(true, false, true, true), runs.map { (it["apollo_requested"] as Boolean) })
            assertEquals(4L, f.itemCount())
            assertTrue(runs.all { (it["execution_id"] as Number).toLong() > 0 && it["input_closed"] == true })
            assertTrue(runs.all { f.flow.mapper.readTree(it["settings_json"] as String).path("apolloLimitPolicy").asText() == "PROVIDER" })
            assertTrue(runs.none { f.flow.mapper.readTree(it["settings_json"] as String).has("requestsPerDay") })
            assertTrue(runs.all { f.flow.mapper.readTree(it["settings_json"] as String).path("institutionAliasVersion").asInt() == 1 })
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=987654", Long::class.java))
            assertEquals(4L, jdbc.queryForObject("SELECT COUNT(DISTINCT owner_execution_id) FROM discovery_verification_item", Long::class.java))
            assertTrue(f.flow.candidates.isEmpty(), "All managed profiles wait for the worker, even direct candidates")
            assertTrue(f.flow.apolloRequests.isEmpty())
            Mockito.verify(f.progress, Mockito.never()).getCurrentExecutionId("EXPERT_DISCOVERY")
        }
    }

    @Test fun `historical JSON is OFF existing snapshots survive config change and missing task or ID cannot create runs`() {
        Fixture().use { f ->
            f.task(11)
            val historical = f.registration.begin(11, f.flow.mapper.readValue("{}", PaperSearchCriteria::class.java), historical = true)
            assertFalse(historical.run!!.apolloRequested)
            assertEquals(historical.run, f.registration.begin(11, PaperSearchCriteria(apolloAssist = true)).run)
            assertThrows(IllegalArgumentException::class.java) { f.registration.begin(null, PaperSearchCriteria()) }
            assertThrows(IllegalArgumentException::class.java) { f.registration.begin(-1, PaperSearchCriteria()) }
            assertThrows(IllegalStateException::class.java) { f.registration.begin(12, PaperSearchCriteria()) }
            val disabled = DiscoveryVerificationRegistrationService(DiscoveryVerificationProperties(), f.flow.repository, f.flow.writer,
                f.flow.policy, f.flow.admissions, jdbc, TransactionTemplate(manager), f.flow.clock)
            assertFalse(disabled.resolveOptions(PaperSearchCriteria()).apolloAssist!!)
            assertThrows(ResponseStatusException::class.java) { disabled.resolveOptions(PaperSearchCriteria(apolloAssist = true)) }
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_run", Long::class.java))
        }
    }

    @Test fun `reserve failure writes zero RAW skips legacy admission and retains entering checkpoint`() {
        Fixture().use { f ->
            f.task(21)
            jdbc.execute("CREATE TRIGGER fail_registration_reserve BEFORE INSERT ON discovery_verification_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected reserve failure'")
            val result = f.service.discover(f.registration.resolveOptions(PaperSearchCriteria(sources = listOf("CROSSREF"))), "TEST", 21, false)
            assertTrue(f.flow.raws.isEmpty())
            assertTrue(f.flow.candidates.isEmpty())
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM expert_discovery_admission", Long::class.java))
            assertTrue(result.stats.bySource.getValue("CROSSREF").failureReasons.containsKey("VERIFICATION_REGISTRATION_FAILED"))
            assertTrue(f.savedCursors.values.none { DiscoveryCheckpointCodec.decode(it.cursorValue)?.exhausted == true })
            assertTrue(f.flow.repository.findRun(21)!!.inputClosed)
        }
    }

    @Test fun `RAW activation crash replays original owner once and leaves historical duplicates outside ledger`() {
        Fixture().use { f ->
            val first = f.context(31)
            jdbc.execute("CREATE TRIGGER fail_registration_activate BEFORE UPDATE ON discovery_verification_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected activation failure'")
            val failed = f.consume("crash", first)
            assertFalse(failed.succeeded)
            assertEquals(1, f.flow.raws.size)
            assertEquals(0L, f.itemCount())
            assertTrue(f.flow.candidates.isEmpty())
            jdbc.execute("DROP TRIGGER fail_registration_activate")
            val next = f.context(32, false)
            assertTrue(f.consume("crash", next).succeeded)
            assertEquals(1L, f.itemCount())
            assertEquals(31L, jdbc.queryForObject("SELECT owner_execution_id FROM discovery_verification_item", Long::class.java))
            assertTrue(f.consume("crash", next).succeeded)
            assertEquals(1L, f.itemCount())
            val doc = f.flow.raws.entries.single()
            f.flow.raws["historical"] = doc.value + ("email" to "historical@example.invalid")
            assertTrue(f.consume("historical", next).succeeded)
            assertEquals(1L, f.itemCount())
            Mockito.verify(f.enrichment, Mockito.atLeast(2)).enqueue(Mockito.eq(doc.key) ?: doc.key, Mockito.eq("CROSSREF") ?: "CROSSREF", Mockito.any())
            Mockito.verify(f.persons, Mockito.atLeastOnce()).refresh(Mockito.eq("CROSSREF") ?: "CROSSREF", Mockito.eq(doc.key) ?: doc.key)
            val item = f.flow.repository.findItemsByDocIds(listOf(doc.key)).single()
            val snapshot = f.flow.mapper.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(item.snapshotJson, VerificationSnapshot::class.java)
            assertEquals(doc.key, snapshot.profile.orcidId)
            assertEquals(doc.key, snapshot.profile.esDocId)
            assertEquals(item.factFingerprint, f.flow.commits.prepare(item).snapshot!!.factFingerprint)
            f.registration.closeInput(first)
            f.registration.closeInput(next)
            f.flow.worker().process(f.flow.claim())
            val completed = f.flow.item(item.id)
            assertEquals(VerificationState.COMPLETED, completed.state)
            assertEquals(VerificationOutcome.PENDING, completed.outcome)
            assertEquals(VerificationQualification.UNKNOWN, completed.qualification)
            assertEquals("[\"IDENTITY_EVIDENCE_MISSING\"]", completed.reasonsJson)
            assertEquals(31L, completed.ownerExecutionId)
            assertEquals(1L, f.itemCount())
            assertTrue(f.flow.apolloRequests.isEmpty())
        }
    }

    @Test fun `recovery uses exact RAW identity and missing RAW waits until source retry is terminal`() {
        Fixture().use { f ->
            val context = f.context(41)
            val profile = ruleSnapshot().profile.copy(esDocId = "recover")
            val reserved = f.registration.reserve(context, "recover", profile, "CROSSREF", "recover-key")
            f.registration.closeInput(context)
            assertEquals(0, f.registration.recoverReserved(100))
            assertEquals(VerificationState.RESERVED, f.flow.repository.findItem(reserved.item!!.id)!!.state)
            @Suppress("UNCHECKED_CAST") val raw = f.flow.mapper.convertValue(profile, Map::class.java) as Map<String, Any?>
            f.flow.raws["recover"] = raw
            assertEquals(1, f.registration.recoverReserved(100))
            assertEquals(0, f.registration.recoverReserved(100))
            assertEquals(1L, f.itemCount())
        }

        Fixture().use { f ->
            val context = f.context(42)
            val missing = f.registration.reserve(context, "missing", ruleSnapshot().profile, "CROSSREF", "missing-key")
            val stream = f.queue.ensureStream(1, "a".repeat(64), "CROSSREF", 1, "{}", f.flow.clock.instant())
            jdbc.update("""INSERT INTO discovery_paper_job
                (stream_id,item_key,identity_quality,unit,payload_version,metadata_json,status,next_attempt_at)
                VALUES (?,'missing-key','DOI','PAPER',1,'{}','FAILED',?)""", stream.id, f.flow.clock.instant().verificationUtc())
            f.registration.closeInput(context)
            assertEquals(0, f.registration.recoverReserved(100))
            val error = f.flow.repository.findItem(missing.item!!.id)!!
            assertEquals(VerificationState.ERROR, error.state)
            assertNull(error.activatedAt)
            assertEquals(0, error.batchNumber)
        }

        Fixture().use { f ->
            val context = f.context(43)
            val missing = f.registration.reserve(context, "sync-missing", ruleSnapshot().profile, "CROSSREF", "sync-missing-key")
            f.registration.closeInput(context)
            jdbc.update("UPDATE task_execution SET status='FAILED',finished_at=? WHERE id=43", f.flow.clock.instant().verificationUtc())
            assertEquals(0, f.registration.recoverReserved(100))
            val error = f.flow.repository.findItem(missing.item!!.id)!!
            assertEquals(VerificationState.ERROR, error.state)
            assertNull(error.activatedAt)
            assertEquals(0, error.batchNumber)
        }
    }

    @Test fun `101 first activations form 100 and 1 across concurrent sources and replay never moves batches`() {
        Fixture().use { f ->
            val context = f.context(51)
            val pool = Executors.newFixedThreadPool(6)
            try {
                val registrations = (1..101).map { n ->
                    f.registration.reserve(context, "batch-$n", ruleSnapshot().profile.copy(email = "batch-$n@example.invalid"),
                        if (n % 2 == 0) "ORCID" else "CROSSREF", "item-$n")
                }
                pool.invokeAll(registrations.map { r -> Callable { f.registration.activate(r) } }).forEach { it.get(30, TimeUnit.SECONDS) }
                val batches = jdbc.queryForList("SELECT batch_number,COUNT(*) n FROM discovery_verification_item GROUP BY batch_number ORDER BY batch_number")
                assertEquals(listOf(100L, 1L), batches.map { (it["n"] as Number).toLong() })
                val before = jdbc.queryForList("SELECT id,batch_number,owner_execution_id FROM discovery_verification_item ORDER BY id")
                registrations.reversed().forEach { f.registration.activate(it) }
                assertEquals(before, jdbc.queryForList("SELECT id,batch_number,owner_execution_id FROM discovery_verification_item ORDER BY id"))
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun `protections and unavailable protection reads make no RAW admission or ledger changes`() {
        Fixture().use { f ->
            val context = f.context(61)
            val profile = ruleSnapshot().profile
            assertTrue(f.registration.reserve(context, "sbir", profile.copy(dataSource = "SBIR"), "CROSSREF", "sbir").protected)
            f.flow.applications["application"] = mapOf("email" to profile.email)
            assertTrue(f.registration.reserve(context, "application", profile, "CROSSREF", "application").protected)
            jdbc.update("""INSERT INTO expert_contact (campaign_id,orcid_id,expert_email)
                SELECT MIN(id),'fixture-other','other@example.invalid' FROM campaign""")
            jdbc.update("""INSERT INTO expert_email_alias (expert_contact_id,email,normalized_email)
                SELECT MAX(id),?,? FROM expert_contact""", profile.email, profile.email)
            assertTrue(f.registration.reserve(context, "contact", profile, "CROSSREF", "contact").protected)
            jdbc.update("DELETE FROM expert_email_alias")
            f.flow.failEs = true
            assertThrows(DiscoveryRegistrationException::class.java) { f.registration.reserve(context, "unavailable", profile, "CROSSREF", "error") }
            f.flow.failEs = false
            assertNotNull(f.registration.reserve(context, "normal", profile, "CROSSREF", "normal").item)
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item", Long::class.java))
            assertTrue(f.flow.raws.isEmpty())
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM expert_discovery_admission", Long::class.java))
        }
    }

    @Test fun `launch option conflict is transactional preserves query hash cursor and resume snapshot`() {
        Fixture().use { f ->
            val pipeline = f.pipeline()
            val criteria = PaperSearchCriteria(keywords = listOf("synthetic"), sources = listOf("CROSSREF"),
                subjectScope = SubjectScopeCatalog.RND_TARGET, apolloAssist = true)
            val first = pipeline.launch(criteria, "MANUAL", false)
            val stream = f.queue.ensureStream(1, f.service.queueQueryHash("CROSSREF", criteria), "CROSSREF", 1, f.flow.mapper.writeValueAsString(criteria), f.flow.clock.instant())
            jdbc.update("UPDATE discovery_collection_stream SET cursor_value='saved-cursor' WHERE id=?", stream.id)
            jdbc.update("UPDATE discovery_pipeline SET active_count=1")
            val before = f.queue.findPipeline()!!
            val conflict = pipeline.launch(criteria.copy(apolloAssist = false), "MANUAL", false)
            assertFalse(conflict.applied)
            assertEquals("VERIFICATION_OPTIONS_CONFLICT", conflict.rejection!!.reason)
            val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(f.controller(pipeline)).build()
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/expert-discovery/run")
                .contentType(MediaType.APPLICATION_JSON).content(f.flow.mapper.writeValueAsString(criteria.copy(apolloAssist = false))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VERIFICATION_OPTIONS_CONFLICT"))
            assertEquals(before, f.queue.findPipeline())
            assertEquals("saved-cursor", f.queue.findStreams(1).single().cursorValue)
            assertEquals(DiscoveryCheckpointCodec.canonicalCriteria(criteria), DiscoveryCheckpointCodec.canonicalCriteria(criteria.copy(apolloAssist = false)))
            pipeline.pause()
            assertTrue(pipeline.resume().applied)
            assertTrue(f.flow.mapper.readTree(f.queue.findPipeline()!!.criteriaJson).path("apolloAssist").asBoolean())
            jdbc.update("UPDATE discovery_pipeline SET active_count=0")
            assertTrue(pipeline.launch(criteria.copy(apolloAssist = false), "MANUAL", false).applied)
            assertEquals(first.queryHash, f.queue.findPipeline()!!.queryHash)
            assertEquals("saved-cursor", f.queue.findStreams(1).single().cursorValue)
            assertFalse(f.flow.mapper.readTree(f.queue.findPipeline()!!.criteriaJson).path("apolloAssist").asBoolean())
        }
    }

    @Test fun `active window without backlog rejects changed options even for a different query`() {
        Fixture().use { f ->
            val pipeline = f.pipeline()
            val criteria = PaperSearchCriteria(keywords = listOf("original"), sources = listOf("CROSSREF"),
                subjectScope = SubjectScopeCatalog.RND_TARGET, apolloAssist = true)
            assertTrue(pipeline.launch(criteria, "MANUAL", false).applied)
            val stream = f.queue.ensureStream(1, f.service.queueQueryHash("CROSSREF", criteria), "CROSSREF", 1,
                f.flow.mapper.writeValueAsString(criteria), f.flow.clock.instant())
            jdbc.update("UPDATE discovery_collection_stream SET cursor_value='active-window-cursor' WHERE id=?", stream.id)
            jdbc.update("UPDATE discovery_pipeline SET active_count=0,owner_token='live-owner',owner_until='2030-01-01 00:00:00'")
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_job", Long::class.java))
            val before = f.queue.findPipeline()!!
            val changed = criteria.copy(keywords = listOf("different"), apolloAssist = false)
            assertNotEquals(DiscoveryCheckpointCodec.canonicalCriteria(criteria), DiscoveryCheckpointCodec.canonicalCriteria(changed))
            val conflict = pipeline.launch(changed, "MANUAL", false)
            assertFalse(conflict.applied)
            assertEquals("VERIFICATION_OPTIONS_CONFLICT", conflict.rejection!!.reason)
            val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(f.controller(pipeline)).build()
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/expert-discovery/run")
                .contentType(MediaType.APPLICATION_JSON).content(f.flow.mapper.writeValueAsString(changed)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VERIFICATION_OPTIONS_CONFLICT"))
            assertEquals(before, f.queue.findPipeline())
            assertEquals("active-window-cursor", f.queue.findStreams(1).single().cursorValue)
            // The existing academic-query conflict keeps its authority when options are unchanged.
            jdbc.update("UPDATE discovery_pipeline SET active_count=1")
            val withBacklog = f.queue.findPipeline()
            val queryConflict = pipeline.launch(changed.copy(apolloAssist = true), "MANUAL", false)
            assertFalse(queryConflict.applied)
            assertEquals("QUERY_CONFLICT", queryConflict.rejection!!.reason)
            assertEquals(withBacklog, f.queue.findPipeline())
            assertEquals("active-window-cursor", f.queue.findStreams(1).single().cursorValue)
        }
    }

    @Test fun `ORCID reserves before RAW and replay preserves enrichment and person registration`() {
        Fixture(workerEnabled = true).use { f ->
            f.beans.registerSingleton("orcid", f.orcid)
            Mockito.doReturn("ORCID").`when`(f.orcid).sourceName
            Mockito.doReturn(10).`when`(f.orcid).maxRecordsPerRun
            val record = OrcidDataSource.OrcidRecord("0000-0002-1825-0097", "Ada", "Lee",
                listOf("orcid@example.invalid"), "Example University", "GB")
            Mockito.doReturn(OrcidDataSource.OrcidSearchPage(listOf(record), null, 1)).`when`(f.orcid)
                .searchOrcidPage(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
            Mockito.doCallRealMethod().`when`(f.orcid).orcidRecordToAuthorEmails(record)
            val criteria = f.registration.resolveOptions(PaperSearchCriteria(sources = listOf("ORCID"), apolloAssist = false))
            f.task(71)
            jdbc.execute("CREATE TRIGGER fail_registration_reserve BEFORE INSERT ON discovery_verification_item FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected reserve failure'")
            f.service.discover(criteria, "TEST", 71, false)
            assertTrue(f.flow.raws.isEmpty())
            assertTrue(f.savedCursors.values.none { DiscoveryCheckpointCodec.decode(it.cursorValue)?.exhausted == true })
            jdbc.execute("DROP TRIGGER fail_registration_reserve")
            f.task(72)
            f.service.discover(criteria, "TEST", 72, false)
            assertEquals(1, f.flow.raws.size)
            assertEquals(1L, f.itemCount())
            assertTrue(f.flow.candidates.isEmpty())
            assertFalse(f.flow.repository.findRun(72)!!.apolloRequested)
            Mockito.verify(f.enrichment).enqueue(Mockito.anyString(), Mockito.eq("ORCID") ?: "ORCID", Mockito.eq(72L))
            Mockito.verify(f.persons).refresh(Mockito.eq("ORCID") ?: "ORCID", Mockito.anyString())
            val doc = f.flow.raws.entries.single()
            assertEquals(doc.key, doc.value["orcidId"], "Preserve the existing RAW identity convention")
            val item = f.flow.repository.findItemsByDocIds(listOf(doc.key)).single()
            val snapshot = f.flow.mapper.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(item.snapshotJson, VerificationSnapshot::class.java)
            assertEquals(doc.key, snapshot.profile.orcidId)
            assertEquals(record.orcidId, f.flow.mapper.readTree(snapshot.profile.externalIds).path("orcid").asText())
            assertTrue(snapshot.identityValid)
            assertEquals(record.orcidId, snapshot.boundOrcid)
            assertEquals(item.factFingerprint, f.flow.commits.prepare(item).snapshot!!.factFingerprint)
            f.flow.worker().process(f.flow.claim())
            val completed = f.flow.item(item.id)
            assertEquals(VerificationState.COMPLETED, completed.state)
            assertEquals(VerificationOutcome.PENDING, completed.outcome)
            assertEquals(VerificationQualification.UNKNOWN, completed.qualification)
            assertEquals("[\"QUALIFICATION_EVIDENCE_MISSING\"]", completed.reasonsJson)
            assertTrue(f.flow.orcidRequests.isNotEmpty(), "Bound ORCID remains available to the worker")
            assertTrue(f.flow.apolloRequests.isEmpty())
        }
    }
}
