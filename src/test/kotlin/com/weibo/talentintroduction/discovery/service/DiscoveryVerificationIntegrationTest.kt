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
            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("164").load().migrate()
            jdbc = JdbcTemplate(ds)
            manager = DataSourceTransactionManager(ds)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        /** Separate runtime entry; never starts the production Spring application. */
        @JvmStatic fun main(args: Array<String>) {
            require(args.contentEquals(arrayOf("--queue-access-smoke")) || args.contentEquals(arrayOf("--retained-identity-smoke")))
            start()
            try {
                if (args[0] == "--retained-identity-smoke") {
                    DiscoveryVerificationIntegrationTest().clean()
                    RetainedRuntime().use { it.originalButtonScenario(VerificationMode.AUDIT) }
                    DiscoveryVerificationIntegrationTest().clean()
                    RetainedRuntime().use { it.originalButtonScenario(VerificationMode.ENFORCE) }
                } else DiscoveryVerificationIntegrationTest().queueVersionAccessScenario()
            } finally { stop() }
        }
    }

    @BeforeEach fun clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_registration_reserve")
        jdbc.execute("DROP TRIGGER IF EXISTS fail_registration_activate")
        jdbc.execute("DROP TRIGGER IF EXISTS fail_controlled_attach")
        listOf("discovery_paper_archive_attempt", "discovery_paper_archive_record", "discovery_paper_archive_blob",
            "discovery_paper_archive_setting").forEach { jdbc.update("DELETE FROM $it") }
        listOf("discovery_apollo_setting", "discovery_paper_job", "discovery_collection_stream", "discovery_pipeline", "discovery_source_cursor",
            "expert_email_alias", "expert_contact", "discovery_recheck_input", "discovery_verification_attempt", "discovery_verification_item",
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
        init { flow.saveApolloSettings(true, true) }
        val registration = DiscoveryVerificationRegistrationService(properties, flow.repository, flow.writer, flow.policy,
            flow.admissions, jdbc, TransactionTemplate(manager), flow.apolloSettings, flow.clock)
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
            assertTrue(runs.all { f.flow.mapper.readTree(it["settings_json"] as String).path("institutionAliasVersion").asInt() == 2 })
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE owner_execution_id=987654", Long::class.java))
            assertEquals(4L, jdbc.queryForObject("SELECT COUNT(DISTINCT owner_execution_id) FROM discovery_verification_item", Long::class.java))
            assertTrue(f.flow.candidates.isEmpty(), "All managed profiles wait for the worker, even direct candidates")
            assertTrue(f.flow.apolloRequests.isEmpty())
            Mockito.verify(f.progress, Mockito.never()).getCurrentExecutionId("EXPERT_DISCOVERY")
        }
    }
    @Test fun queueVersionAccessScenario() {
        for (version in listOf(1, 2)) {
            clean()
            Fixture().use { f ->
                f.flow.saveApolloSettings(true, false)
                val pipeline = f.pipeline()
                assertTrue(pipeline.launch(PaperSearchCriteria(sources = listOf("CROSSREF"),
                    keywords = listOf("queue-access-$version")), "MANUAL", false).applied)
                val launched = f.queue.findPipeline()!!
                assertEquals(2, launched.criteriaVersion)
                val savedCriteria = f.flow.mapper.readTree(launched.criteriaJson)
                assertTrue(savedCriteria.has("apolloAssist"))
                assertTrue(savedCriteria.path("apolloAssist").isNull)
                if (version == 1) {
                    // Simulate an already persisted old launch, not a new launch guessed from its timestamp.
                    jdbc.update("UPDATE discovery_pipeline SET criteria_version=1")
                }
                val before = f.queue.findPipeline()!!
                assertTrue(pipeline.tick().dispatched)
                val executionId = jdbc.queryForObject("SELECT execution_id FROM discovery_verification_run",
                    Long::class.java)!!
                assertTrue(executionId > 0)
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE id=?", Long::class.java, executionId))
                val run = f.flow.repository.findRun(executionId)!!
                assertEquals(version == 2, run.apolloRequested)
                assertTrue(run.frozenApolloAccess(f.flow.mapper).searchEnabled)
                assertFalse(run.frozenApolloAccess(f.flow.mapper).detailEnabled)
                assertEquals(before.criteriaJson, f.queue.findPipeline()!!.criteriaJson)
                assertEquals(version, f.queue.findPipeline()!!.criteriaVersion)
                f.flow.saveApolloSettings(false, true)
                assertEquals(run, f.registration.begin(executionId, PaperSearchCriteria(apolloAssist = true),
                    historical = version == 1).run)
                assertEquals(run.settingsJson, f.flow.repository.findRun(executionId)!!.settingsJson)
                assertTrue(f.flow.apolloRequests.isEmpty())
                println("APOLLO_QUEUE_ACCESS_SMOKE version=$version nullPersisted=true executionId=$executionId requested=${run.apolloRequested} frozen=true/false reentry=unchanged providerHttp=0")
            }
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
                f.flow.policy, f.flow.admissions, jdbc, TransactionTemplate(manager), f.flow.apolloSettings, f.flow.clock)
            assertNull(disabled.resolveOptions(PaperSearchCriteria()).apolloAssist)
            assertThrows(ResponseStatusException::class.java) { disabled.resolveOptions(PaperSearchCriteria(apolloAssist = true)) }
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_run", Long::class.java))
        }
    }

    @Test fun `new runs freeze saved permissions while resumed and legacy runs keep their own permissions`() {
        Fixture().use { f ->
            f.task(13)
            val options = f.registration.resolveOptions(PaperSearchCriteria())
            assertNull(options.apolloAssist)
            val a = f.registration.begin(13, options).run!!
            assertTrue(a.apolloRequested)
            assertTrue(a.frozenApolloAccess(f.flow.mapper).detailEnabled)
            val saved = f.flow.saveApolloSettings(true, false)
            assertEquals(a, f.registration.begin(13, options).run)
            f.task(14)
            val b = f.registration.begin(14, options).run!!
            assertTrue(b.apolloRequested)
            val access = b.frozenApolloAccess(f.flow.mapper)
            assertTrue(access.searchEnabled)
            assertFalse(access.detailEnabled)
            assertEquals(saved.revision, access.settingRevision)
            assertEquals(ApolloSettingSource.SAVED, access.source)
            f.task(15)
            val legacy = f.flow.repository.createRun(15, VerificationMode.ENFORCE, true, "{}", f.flow.clock.instant())
            f.flow.saveApolloSettings(false, true)
            assertEquals(legacy, f.registration.begin(15, PaperSearchCriteria(apolloAssist = true)).run)
            assertEquals(ApolloSettingSource.LEGACY, legacy.frozenApolloAccess(f.flow.mapper).source)
            assertTrue(legacy.frozenApolloAccess(f.flow.mapper).detailEnabled)
            f.task(16)
            val off = f.registration.begin(16, options).run!!
            assertFalse(off.apolloRequested)
            assertFalse(off.frozenApolloAccess(f.flow.mapper).searchEnabled)
            assertTrue(off.frozenApolloAccess(f.flow.mapper).detailEnabled)
            f.task(17)
            val error = assertThrows(ResponseStatusException::class.java) {
                f.registration.begin(17, PaperSearchCriteria(apolloAssist = true))
            }
            assertEquals(409, error.rawStatusCode)
            assertEquals("APOLLO_SEARCH_DISABLED", error.reason)
            assertNull(f.flow.repository.findRun(17))
            assertTrue(f.flow.apolloRequests.isEmpty())
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

    /** Source-backed, isolated runtime: original controller prepare/start -> real SQL -> worker -> query.
     * The loopback ES implements version CAS and rejects any unexpected operation. Providers fail closed.
     */
    private class RetainedRuntime : AutoCloseable {
        val f = VerificationFlowFixture(jdbc, manager, DiscoveryVerificationProperties(enabled = true,
            workerEnabled = true, apolloApiKey = "fixture-key", requestsPerMinute = 1000, concurrency = 1))
        val root = java.nio.file.Files.createTempDirectory("retained-identity-runtime-").toRealPath()
        val mapper = f.mapper
        val doc = "EMAIL-retained-empty-31372"
        val docs = linkedMapOf("raw" to linkedMapOf<String, MutableMap<String, Any?>>(),
            "candidate" to linkedMapOf<String, MutableMap<String, Any?>>(), "application" to linkedMapOf<String, MutableMap<String, Any?>>())
        val versions = mutableMapOf<Pair<String, String>, Long>()
        var identityWrites = 0
        var providerCalls = 0
        var failCandidate = false
        var beforeUpdate: (() -> Unit)? = null
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val props: ElasticsearchProperties
        val writer: ExpertIndexWriterService
        val review: DiscoveryReviewService
        val commits: DiscoveryVerificationCommitService
        val archiveRepo = com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveRepository(jdbc, manager)
        val archive = com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveService(archiveRepo,
            DiscoveryPaperArchiveProperties(root.resolve("archive").toString()), DiscoverySampleProperties(directory = root.resolve("samples").toString()), mapper)
        val analyzer = DiscoveryHistoricalReplayTool.analyzer()
        val papers = PaperVerificationEvidenceService(archiveRepo, archive, analyzer, mapper)
        val contacts = Mockito.mock(com.weibo.talentintroduction.campaign.repository.ExpertContactRepository::class.java)
        val aliases = Mockito.mock(com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService::class.java)
        val retained: RetainedPaperIdentityService
        val queries = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
        val tasks = Mockito.mock(TaskExecutionRepository::class.java)
        val taskRows = mutableMapOf<Long, TaskExecution>()
        var nextTask = 7000L
        val service: DiscoveryRecheckService
        val controller: com.weibo.talentintroduction.discovery.controller.DiscoveryRecheckController
        val request = org.springframework.mock.web.MockHttpServletRequest().apply {
            getSession(true).setAttribute(com.weibo.talentintroduction.auth.config.AuthSessionKeys.USERNAME, "isolated-operator")
        }
        val source: PaperSourceRef
        val original: VerificationSnapshot
        val historyId: Long
        val historyBefore: Map<String, Any?>
        val workers = mutableListOf<DiscoveryVerificationWorker>()
        init {
            server.createContext("/") { exchange ->
                var status = 200
                val path = exchange.requestURI.path.trim('/').split('/')
                val body = exchange.requestBody.use { it.readBytes() }.takeIf { it.isNotEmpty() }?.let { mapper.readTree(it) }
                val answer: Any
                if (path.last() == "_search") {
                    val email = body!!.path("query").path("term").path("email").asText()
                    val hits = docs.flatMap { (index, values) -> values.filterValues { it["email"] == email }.map { (id, source) ->
                        mapOf("_index" to index, "_id" to id, "_source" to source) } }
                    answer = mapOf("timed_out" to false, "_shards" to mapOf("failed" to 0), "hits" to mapOf("hits" to hits))
                } else {
                    check(path.size == 3 && path[0] in docs && path[1] in listOf("_doc", "_update")) { "Unexpected ES route ${exchange.requestURI}" }
                    val index = path[0]; val id = path[2]; val key = index to id
                    val current = docs.getValue(index)[id]
                    when (exchange.requestMethod) {
                        "GET", "HEAD" -> {
                            if (current == null) { status = 404; answer = mapOf("found" to false) }
                            else answer = mapOf("_source" to current, "_seq_no" to versions.getOrPut(key) { 1L }, "_primary_term" to 1)
                        }
                        "POST" -> {
                            check(path[1] == "_update")
                            beforeUpdate?.also { beforeUpdate = null; it() }
                            val expected = exchange.requestURI.rawQuery.orEmpty().split('&').associate { it.substringBefore('=') to it.substringAfter('=') }
                            if (current == null) { status = 404; answer = emptyMap<String, Any>() }
                            else if (expected["if_seq_no"]?.toLong() != versions.getOrPut(key) { 1L } || expected["if_primary_term"] != "1") {
                                status = 409; answer = emptyMap<String, Any>()
                            } else if (index == "candidate" && failCandidate) { status = 503; answer = emptyMap<String, Any>() }
                            else {
                                @Suppress("UNCHECKED_CAST") val patch = mapper.convertValue(body!!.path("doc"), Map::class.java) as Map<String, Any?>
                                check(patch.keys.all { it in setOf("givenNames", "familyNames", "identityVerification") })
                                current.putAll(patch); versions[key] = versions.getValue(key) + 1; identityWrites++
                                answer = mapOf("result" to "updated", "_seq_no" to versions[key], "_primary_term" to 1)
                            }
                        }
                        "PUT" -> {
                            check(index == "candidate" && current == null && exchange.requestURI.rawQuery == "op_type=create")
                            @Suppress("UNCHECKED_CAST") val value = mapper.convertValue(body, Map::class.java) as Map<String, Any?>
                            docs.getValue(index)[id] = value.toMutableMap(); versions[key] = 1
                            answer = mapOf("result" to "created")
                        }
                        else -> error("Unexpected ES method ${exchange.requestMethod}")
                    }
                }
                val bytes = mapper.writeValueAsBytes(answer)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
                exchange.responseBody.use { if (exchange.requestMethod != "HEAD") it.write(bytes) }
            }
            server.start()
            props = f.esProperties.copy(baseUrl = "http://127.0.0.1:${server.address.port}")
            val http = RestTemplate()
            val indices = ExpertIndexService(props, http, mapper)
            writer = ExpertIndexWriterService(http, props, indices, mapper, Mockito.mock(ExpertPromotionAuditService::class.java), contacts)
            review = DiscoveryReviewService(f.admissions, f.policy, writer, http, props, indices, mapper, f.clock, jdbcTemplate = jdbc)
            val proxy = ProxyFactory(DiscoveryVerificationCommitService(f.repository, f.admissions, review, f.policy, writer, jdbc, f.clock))
            proxy.isProxyTargetClass = true
            proxy.addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
            commits = proxy.proxy as DiscoveryVerificationCommitService
            retained = RetainedPaperIdentityService(archive, archiveRepo, analyzer, papers, writer,
                DiscoveryIdentityRepairService(writer), contacts, aliases, f.admissions, f.policy)
            val sourceDir = java.nio.file.Paths.get(System.getProperty("retainedOriginalDirectory",
                "/Users/lukai/.local/share/paper-reprocess-30711"))
            val record = mapper.readTree(java.nio.file.Files.readAllBytes(sourceDir.resolve("31372.json")))
            val metadata = mapper.readTree(record.path("metadataRaw").asText())
            val hash = record.path("bodySha256").asText()
            check(hash == "488b16702960051e2613220ff837bfb0abd229bc738b4983ace6dc5f15dc42c7")
            val bytes = java.nio.file.Files.readAllBytes(sourceDir.resolve("$hash.bin"))
            check(com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveService.sha(bytes) == hash)
            val setting = archive.settings()
            archive.updateSettings(com.weibo.talentintroduction.discovery.archive.ArchiveSettingsRequest(setting.revision, true, setting.maxBytes, setting.minFreeBytes))
            val archived = archive.beginRecord(30711, "CROSSREF", "retained-31372", metadata)
            val attempt = archive.beginAttempt(archived.id, java.util.UUID.randomUUID(), 1,
                com.weibo.talentintroduction.discovery.archive.ArchiveAttemptKind.PDF_URL, "https://fixture.invalid/original.pdf")
            archive.saveBody(attempt.id, bytes, "application/pdf")
            archive.finishExtraction(archived.id, mapper.readTree(record.path("outcomeRaw").asText()))
            source = PaperSourceRef(archived.id, attempt.id, hash, 30711, "CROSSREF", "retained-31372")
            val raw = linkedMapOf<String, Any?>("orcidId" to doc, "givenNames" to "", "familyNames" to "",
                "email" to "e.martinez-paneda@imperial.ac.uk", "emailSource" to "PAPER_FULLTEXT", "dataSource" to "CROSSREF",
                "tags" to listOf("discovered"), "lastPublicationYear" to 2026, "institution" to "Example University",
                "externalIds" to mapOf("doi" to metadata.path("doi").asText()))
            docs.getValue("raw")[doc] = raw
            original = DiscoveryVerificationAdmissionGate.snapshot(doc, writer.discoveryProfile(doc, raw), f.policy.currentConfigSnapshot())
            jdbc.update("""INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,policy_version,updated_at)
                VALUES (?,?,'NEEDS_REVIEW',7,?,?)""", doc, original.identityHash, DiscoveryVerificationAdmissionGate.POLICY_VERSION, f.clock.instant().verificationUtc())
            f.repository.createRun(6000, VerificationMode.ENFORCE, false, "{}", f.clock.instant())
            val historical = f.repository.reserveItem(VerificationReservation(6000, doc, original.identityHash,
                VerificationMode.ENFORCE.policyVersion, "CROSSREF", "retained-31372", mapper.writeValueAsString(original),
                original.factFingerprint), f.clock.instant())
            val oldEvidence = VerificationWorkerEvidence(evidence = listOf(ruleJob("Professor", AssertionSource.SELF).copy(
                identityHash = original.identityHash, factFingerprint = original.factFingerprint)), search = ruleSearch(),
                ruleVersion = 4, executionContext = VerificationExecutionContext(f.clock.instant(), listOf(source)))
            jdbc.update("""UPDATE discovery_verification_item SET state='COMPLETED',outcome='PENDING',qualification='UNKNOWN',
                evidence_json=?,completed_at=? WHERE id=?""", mapper.writeValueAsString(oldEvidence), f.clock.instant().verificationUtc(), historical.id)
            historyId = historical.id
            historyBefore = jdbc.queryForMap("SELECT * FROM discovery_verification_item WHERE id=?", historyId)
            Mockito.doAnswer { call ->
                val task = call.getArgument<TaskExecution>(0).let { it.copy(id = it.id ?: ++nextTask) }
                taskRows[task.id!!] = task
                jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,owner_token)
                    VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE status=VALUES(status)""",
                    task.id, task.taskType, task.triggerType, task.status, task.startedAt, task.ownerToken)
                task
            }.`when`(tasks).save(Mockito.any(TaskExecution::class.java))
            Mockito.doAnswer { java.util.Optional.ofNullable(taskRows[it.getArgument<Long>(0)]) }.`when`(tasks).findById(Mockito.anyLong())
            Mockito.doAnswer { call ->
                val id = call.getArgument<Long>(0); val status = call.getArgument<String>(2)
                taskRows[id] = taskRows.getValue(id).copy(status = status)
                jdbc.update("UPDATE task_execution SET status=?,error_message=? WHERE id=?", status, call.getArgument<String?>(6), id)
            }.`when`(tasks).finishOwned(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyInt(), Mockito.nullable(String::class.java),
                Mockito.any(java.time.LocalDateTime::class.java) ?: java.time.LocalDateTime.now())
            f.saveApolloSettings(false, false)
            service = DiscoveryRecheckService(f.repository, f.admissions, writer,
                DiscoveryReviewScanService(f.admissions, f.policy, writer, http, props, indices, mapper),
                f.policy, f.apolloSettings, f.props, tasks, TransactionTemplate(manager), queries, commits, Executor { it.run() },
                DefaultListableBeanFactory().apply { registerSingleton("retainedRuntimeWorker", worker()) }
                    .getBeanProvider(DiscoveryVerificationWorker::class.java), papers, f.clock)
            controller = com.weibo.talentintroduction.discovery.controller.DiscoveryRecheckController(service, queries)
        }
        fun worker(refreshFixture: Boolean = false): DiscoveryVerificationWorker {
            val denied = RestTemplate().apply { interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { request, _, _ ->
                providerCalls++
                check(refreshFixture && request.uri.host != "api.apollo.io" &&
                    request.uri.path.contains("0000-0002-1562-097X")) { "Unexpected provider dispatch ${request.uri}" }
                org.springframework.mock.http.client.MockClientHttpResponse("{}".toByteArray(), org.springframework.http.HttpStatus.OK)
                    .apply { headers.contentType = MediaType.APPLICATION_JSON }
            }) }
            return DiscoveryVerificationWorker(f.props, f.repository, OrcidVerificationEvidenceClient(f.props, denied, f.clock),
                ApolloPeopleSearchClient(f.props, denied, f.clock), QualificationEvidenceParser(f.clock), f.rules, commits,
                DefaultListableBeanFactory().getBeanProvider(RegistrationRecovery::class.java), f.clock,
                DiscoveryVerificationTraceRepository(jdbc), papers, retainedIdentity = retained).also { workers += it }
        }
        fun launch(mode: VerificationMode = VerificationMode.ENFORCE, evidenceMode: RecheckEvidenceMode = RecheckEvidenceMode.REUSE): VerificationItem {
            val prepared = controller.prepare(request, RecheckPrepareRequest(java.util.UUID.randomUUID().toString(),
                docIds = listOf(doc), mode = mode, evidenceMode = evidenceMode)).body as RecheckRunView
            check(prepared.phase == RecheckPhase.READY) { prepared.toString() }
            check(identityWrites == 0 || docs.getValue("raw").getValue(doc)["givenNames"] != "")
            val run = f.repository.findRun(prepared.runId)!!
            controller.start(request, run.executionId, RecheckStartRequest(f.repository.recheck(run).control.scopeHash!!, run.revision))
            return f.claim()
        }
        fun rebind(lease: VerificationItem, audit: Boolean = false): VerificationItem {
            val proposal = retained.propose(doc, original, listOf(source), f.clock.instant())
            check(proposal.incoming != null) { proposal.toString() }
            val staged = f.repository.stageIdentity(lease, proposal, f.clock.instant())!!
            val receipt = if (audit) RetainedIdentityReceipt("PROPOSED", proposal.reason,
                effective = retained.virtual(doc, original, proposal.incoming!!)) else retained.apply(doc, proposal)
            check(receipt.phase == if (audit) "PROPOSED" else "APPLIED") { receipt.toString() }
            return f.repository.completeIdentity(staged, receipt, f.clock.instant())!!
        }
        fun qualified(item: VerificationItem): VerificationResult {
            val snapshot = mapper.readValue(item.snapshotJson, VerificationSnapshot::class.java)
            val facts = listOf(ruleJob("Professor", AssertionSource.SELF)).map {
                it.copy(identityHash = snapshot.identityHash, factFingerprint = snapshot.factFingerprint) }
            val payload = VerificationWorkerEvidence(evidence = facts, ruleVersion = 4,
                executionContext = VerificationExecutionContext(f.clock.instant(), listOf(source)))
            val decision = f.rules.evaluate(snapshot, facts, ruleVersion = 4, evaluatedAt = f.clock.instant())
            check(decision.outcome == VerificationOutcome.DIRECT_CLEAR)
            return VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification, decision.route,
                mapper.writeValueAsString(payload), mapper.writeValueAsString(decision.reasonCodes))
        }
        fun originalButtonScenario(mode: VerificationMode) {
            val beforeArchive = archiveRepo.record(source.archiveRecordId)
            val lease = launch(mode)
            val rosterHash = f.repository.recheck(f.repository.findRun(lease.ownerExecutionId)!!).control.scopeHash
            val originalJson = lease.snapshotJson
            check(docs.getValue("raw").getValue(doc)["givenNames"] == "" && identityWrites == 0)
            worker().process(lease)
            val done = f.item(lease.id)
            check(done.state == VerificationState.COMPLETED) { done.toString() }
            val s = RetainedPaperIdentityService.supplement(done)!!
            check(s.effectiveProfile!!.givenNames == "Emilio")
            check(s.beforeSnapshot == RetainedPaperIdentityService.codec.readTree(originalJson))
            check(s.proposal.sourceEvidence.any { it.path("sentences").any { sentence -> sentence.path("page").asInt() == 1 &&
                sentence.path("contactText").asText().contains("e.martinez-paneda@imperial.ac.uk") } })
            check(s.phase == if (mode == VerificationMode.AUDIT) "PROPOSED" else "APPLIED")
            check(identityWrites == if (mode == VerificationMode.AUDIT) 0 else 1)
            check(f.admissions.findAdmission(doc)!!.revision == 7L && done.admissionRevision == null)
            check(f.repository.recheck(f.repository.findRun(lease.ownerExecutionId)!!).control.scopeHash == rosterHash)
            check(archiveRepo.record(source.archiveRecordId) == beforeArchive)
            check(jdbc.queryForMap("SELECT * FROM discovery_verification_item WHERE id=?", historyId) == historyBefore)
            val payload = mapper.readValue(done.evidenceJson, VerificationWorkerEvidence::class.java)
            check(payload.executionContext!!.cacheReasons.contains("IDENTITY_CHANGED_CACHE_NOT_REUSED"))
            check(payload.search?.candidates.orEmpty().none { it.id == "person-1" })
            check(payload.evidence.all { it.identityHash == done.identityHash && it.factFingerprint == done.factFingerprint })
            check(providerCalls == 0)
            val view = queries.recheckItem(done.ownerExecutionId, done.id)
            check(mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(view).toString().contains("identitySupplement"))
            check(queries.recheckManifest(done.ownerExecutionId, 0, 20).items.isNotEmpty())
            println("RETAINED_IDENTITY_RUNTIME mode=$mode originalControllerPrepareStart=true isolatedMySQL=true loopbackEsCAS=true sourceSha=${source.bodySha256} evidencePage=1 beforeNameEmpty=true effectiveName=Emilio phase=${s.phase} identityWrites=$identityWrites outcome=${done.outcome} applied=false admissionRevision=7 providers=$providerCalls rosterHashUnchanged=true archiveUnchanged=true queryTrace=true")
        }
        override fun close() {
            workers.forEach { it.close() }; server.stop(0); f.close()
            check(root.toFile().deleteRecursively())
        }
    }

    @Test fun `original retained recheck uses virtual audit identity and enforce repairs without inventing qualification`() {
        RetainedRuntime().use { it.originalButtonScenario(VerificationMode.AUDIT) }
        clean()
        RetainedRuntime().use { it.originalButtonScenario(VerificationMode.ENFORCE) }
    }

    @Test fun `controlled old automatic admission changes identity through real persisted seam and projects`() {
        RetainedRuntime().use { r ->
            jdbc.update("UPDATE expert_discovery_admission SET decision='AUTO_PASSED' WHERE expert_doc_id=?", r.doc)
            val lease = r.launch()
            val originalHash = DiscoveryVerificationRepository.replayHash(lease.snapshotJson)
            val rebound = r.rebind(lease)
            assertNotEquals(lease.identityHash, rebound.identityHash)
            assertEquals(lease.identityHash, RetainedPaperIdentityService.supplement(rebound)!!.beforeIdentityHash)
            val done = r.commits.commit(rebound, r.qualified(rebound), r.commits.prepare(rebound))!!
            assertEquals(8L, done.admissionRevision)
            assertEquals(rebound.identityHash, r.f.admissions.findAdmission(r.doc)!!.identityHash)
            assertEquals(originalHash, DiscoveryVerificationRepository.replayHash(
                RetainedPaperIdentityService.supplement(done)!!.beforeSnapshot.toString()))
            r.commits.project(done)
            assertEquals(VerificationProjection.PROJECTED, r.f.item(done.id).projectionState)
        }
    }

    @Test fun `SQL rebind rejects ordinary epoch stale token cancellation and resumes same frozen proposal after external success`() {
        RetainedRuntime().use { r ->
            val lease = r.launch()
            val p = r.retained.propose(r.doc, r.original, listOf(r.source), r.f.clock.instant())
            assertThrows(IllegalArgumentException::class.java) { r.f.repository.stageIdentity(lease.copy(reviewEpoch = 0), p, r.f.clock.instant()) }
            assertNull(r.f.repository.stageIdentity(lease.copy(leaseToken = "obsolete"), p, r.f.clock.instant()))
            val staged = r.f.repository.stageIdentity(lease, p, r.f.clock.instant())!!
            val receipt = r.retained.apply(r.doc, p)
            assertEquals("APPLIED", receipt.phase)
            assertEquals(1, r.identityWrites)
            r.f.clock.now = r.f.clock.now.plusSeconds(121)
            val recovered = r.f.claim()
            assertNull(r.f.repository.completeIdentity(staged, receipt, r.f.clock.instant()))
            val resumed = r.f.repository.stageIdentity(recovered, p, r.f.clock.instant())!!
            val replayed = r.retained.apply(r.doc, p)
            assertEquals("ALREADY_VERIFIED", replayed.reason)
            val rebound = r.f.repository.completeIdentity(resumed, replayed, r.f.clock.instant())!!
            assertEquals(1, r.identityWrites)
            assertFalse(RetainedPaperIdentityService.supplement(rebound)!!.beforeSnapshot.has("identitySupplement"))
            r.service.cancel(rebound.ownerExecutionId)
            assertNull(r.commits.commit(rebound, r.qualified(rebound), r.commits.prepare(rebound)))
            assertEquals(7L, r.f.admissions.findAdmission(r.doc)!!.revision)
        }
    }

    @Test fun `missing receipt manual history revision contact and changed ES block admission and ordinary guard rolls back`() {
        for (change in listOf("missing", "manual", "history", "revision", "contact", "es", "rollback")) {
            clean()
            RetainedRuntime().use { r ->
                val rebound = r.rebind(r.launch())
                val proposed = r.qualified(rebound)
                when (change) {
                    "missing" -> jdbc.update("UPDATE discovery_verification_item SET snapshot_json=JSON_REMOVE(snapshot_json,'$.identitySupplement') WHERE id=?", rebound.id)
                    "manual" -> jdbc.update("UPDATE expert_discovery_admission SET decision='HOLD',revision=8 WHERE expert_doc_id=?", r.doc)
                    "history" -> jdbc.update("UPDATE expert_discovery_admission SET decision='LEGACY_APPROVED' WHERE expert_doc_id=?", r.doc)
                    "revision" -> jdbc.update("UPDATE expert_discovery_admission SET revision=8 WHERE expert_doc_id=?", r.doc)
                    "contact" -> jdbc.update("""INSERT INTO expert_contact (campaign_id,orcid_id,expert_email)
                        SELECT MIN(id),?,? FROM campaign""", r.doc, r.original.profile.email)
                    "es" -> r.docs.getValue("raw").getValue(r.doc)["givenNames"] = "Concurrent"
                    "rollback" -> {
                        val before = r.f.admissions.findAdmission(r.doc)
                        assertThrows(IllegalStateException::class.java) {
                            TransactionTemplate(manager).executeWithoutResult {
                                r.review.recordAutomaticPersisted(r.doc, r.mapper.readValue(rebound.snapshotJson, VerificationSnapshot::class.java).profile,
                                    r.f.policy.evaluate(r.doc, r.mapper.readValue(rebound.snapshotJson, VerificationSnapshot::class.java).profile))
                            }
                        }
                        assertEquals(before, r.f.admissions.findAdmission(r.doc))
                        assertEquals(VerificationState.RUNNING, r.f.item(rebound.id).state)
                        return@use
                    }
                }
                val done = r.commits.commit(rebound, proposed, r.commits.prepare(rebound))!!
                assertNull(done.admissionRevision, change)
                assertTrue(done.outcome in setOf(VerificationOutcome.PROTECTED, VerificationOutcome.PENDING), change)
                assertEquals(r.original.identityHash, r.f.admissions.findAdmission(r.doc)!!.identityHash, change)
            }
        }
    }

    @Test fun `controlled identity admission and item attachment roll back together on SQL failure`() {
        RetainedRuntime().use { r ->
            jdbc.update("UPDATE expert_discovery_admission SET decision='AUTO_PASSED' WHERE expert_doc_id=?", r.doc)
            val rebound = r.rebind(r.launch())
            val before = r.f.admissions.findAdmission(r.doc)
            jdbc.execute("""CREATE TRIGGER fail_controlled_attach BEFORE UPDATE ON discovery_verification_item
                FOR EACH ROW BEGIN IF NEW.admission_revision IS NOT NULL THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CONTROLLED_ATTACH_FAILURE'; END IF; END""")
            assertThrows(org.springframework.dao.DataAccessException::class.java) {
                r.commits.commit(rebound, r.qualified(rebound), r.commits.prepare(rebound))
            }
            assertEquals(before, r.f.admissions.findAdmission(r.doc))
            assertEquals(VerificationState.RUNNING, r.f.item(rebound.id).state)
            assertNull(r.f.item(rebound.id).admissionRevision)
        }
    }

    @Test fun `partial ES repair retries only missing layer and SQL effective snapshot changes atomically`() {
        RetainedRuntime().use { r ->
            r.docs.getValue("candidate")[r.doc] = r.docs.getValue("raw").getValue(r.doc).toMutableMap()
            val lease = r.launch()
            val p = r.retained.propose(r.doc, r.original, listOf(r.source), r.f.clock.instant())
            val staged = r.f.repository.stageIdentity(lease, p, r.f.clock.instant())!!
            r.failCandidate = true
            val partial = r.retained.apply(r.doc, p)
            assertEquals("PARTIAL", partial.phase)
            val saved = r.f.repository.completeIdentity(staged, partial, r.f.clock.instant())!!
            assertEquals(lease.identityHash, saved.identityHash)
            assertEquals(lease.factFingerprint, saved.factFingerprint)
            assertNull(RetainedPaperIdentityService.supplement(saved)!!.effectiveHash)
            r.failCandidate = false
            val restaged = r.f.repository.stageIdentity(saved, p, r.f.clock.instant())!!
            val complete = r.retained.apply(r.doc, p)
            val rebound = r.f.repository.completeIdentity(restaged, complete, r.f.clock.instant())!!
            assertEquals(2, r.identityWrites)
            assertEquals(rebound.identityHash, r.mapper.readTree(rebound.snapshotJson).path("identityHash").asText())
            assertEquals(rebound.factFingerprint, r.mapper.readTree(rebound.snapshotJson).path("factFingerprint").asText())
            assertEquals(7L, r.f.admissions.findAdmission(r.doc)!!.revision)
        }
    }

    @Test fun `refresh after retained identity change fetches only effective ORCID and preserves frozen Apollo switches`() {
        RetainedRuntime().use { r ->
            val lease = r.launch(evidenceMode = RecheckEvidenceMode.REFRESH)
            val settings = r.f.repository.findRun(lease.ownerExecutionId)!!.settingsJson
            r.f.saveApolloSettings(true, true)
            r.worker(refreshFixture = true).process(lease)
            val done = r.f.item(lease.id)
            assertEquals(VerificationState.COMPLETED, done.state)
            assertEquals("APPLIED", RetainedPaperIdentityService.supplement(done)!!.phase)
            assertTrue(r.providerCalls > 0)
            assertEquals(settings, r.f.repository.findRun(lease.ownerExecutionId)!!.settingsJson)
            assertNull(done.admissionRevision)
            val payload = r.mapper.readValue(done.evidenceJson, VerificationWorkerEvidence::class.java)
            assertTrue(payload.executionContext!!.cacheReasons.contains("IDENTITY_CHANGED_CACHE_NOT_REUSED"))
            assertTrue(payload.evidence.none { it.identityHash == r.original.identityHash })
        }
    }
}
