package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.service.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.MountableFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** No Docker skip: selecting this class requires a real disposable MySQL server. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false", "spring.flyway.target=161"])
@Import(DiscoveryVerificationRepository::class, DiscoveryReviewRepository::class, DiscoveryVerificationQueryService::class,
    DiscoveryVerificationReportingIT.Config::class)
class DiscoveryVerificationReportingIT {
    @TestConfiguration
    @EnableConfigurationProperties(DiscoveryVerificationProperties::class)
    class Config
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("reporting_verification_fixture").withUsername("test").withPassword("test")
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required" }
            mysql.start()
            mysql.copyFileToContainer(MountableFile.forClasspathResource("discovery/apollo-verification-manual-fixture.sql"), "/tmp/apv-fixture.sql")
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
    @Autowired private lateinit var repository: DiscoveryVerificationRepository
    @Autowired private lateinit var query: DiscoveryVerificationQueryService
    @Autowired private lateinit var manager: PlatformTransactionManager
    @SpyBean private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var jobs: ExpertAcademicEnrichmentJobRepository
    @Autowired private lateinit var executions: com.weibo.talentintroduction.task.repository.TaskExecutionRepository
    @Autowired private lateinit var logs: com.weibo.talentintroduction.task.repository.TaskProgressLogRepository

    private fun importFixture() {
        val result = mysql.execInContainer("mysql", "-utest", "-ptest", mysql.databaseName, "-e", "source /tmp/apv-fixture.sql")
        assertEquals(0, result.exitCode, result.stdout + result.stderr)
    }
    @BeforeEach fun seed() {
        // Nothing in a shared database is touched: these IDs exist only in this disposable container.
        jdbc.update("DELETE FROM discovery_verification_attempt WHERE item_id IN (SELECT id FROM discovery_verification_item WHERE owner_execution_id NOT IN (3001,3003))")
        jdbc.update("DELETE FROM discovery_verification_item WHERE owner_execution_id NOT IN (3001,3003)")
        jdbc.update("DELETE FROM discovery_verification_run WHERE execution_id NOT IN (3001,3003)")
        jdbc.update("DELETE FROM expert_academic_enrichment_job WHERE expert_doc_id LIKE 'APV-FIXTURE-PROMOTION-%'")
        jdbc.update("DELETE FROM task_execution WHERE id=6101")
        importFixture()
        Mockito.clearInvocations(jdbc)
    }
    private fun counts(id: Long = 3001) = query.summary(listOf(id)).single().counts!!
    private fun assertF123(c: VerificationCounts) {
        assertEquals(listOf(123L,55L,15L,40L,36L,19L,45L,5L,18L),
            listOf(c.profiles,c.autoAdmitted,c.directAdmitted,c.apolloAdmitted,c.clear,c.unclear,c.pending,c.errors,c.queued))
        assertEquals(c.profiles, c.autoAdmitted+c.pending+c.errors+c.queued+c.protected)
        assertEquals(c.autoAdmitted, c.directAdmitted+c.apolloAdmitted)
        assertEquals(c.autoAdmitted, c.clear+c.unclear)
        assertEquals(listOf(108L,85L,66L,40L,108L),
            listOf(c.apolloEntered,c.apolloSearched,c.apolloCandidates,c.apolloMatched,c.apolloHttpAttempts))
        assertEquals(0L,c.projectionPending); assertEquals(0L,c.projectionErrors); assertEquals(0L,c.registrationErrors)
    }

    private val promotionTime = java.time.Instant.parse("2026-10-18T12:00:00Z")

    private fun promotionItem(owner: Long, doc: String, source: String = "OPENALEX",
        projection: VerificationProjection = VerificationProjection.PROJECTED, revision: Long? = 1L,
        activated: Boolean = true, mode: VerificationMode = VerificationMode.ENFORCE,
        identity: String = source) {
        repository.createRun(owner, mode, false, "{}", promotionTime)
        val item = repository.reserveItem(VerificationReservation(owner, doc,
            DiscoveryVerificationRepository.replayHash("$owner/$doc/$identity"), mode.policyVersion,
            source, identity, "{}", "b".repeat(64)), promotionTime)
        if (activated) repository.activateItem(item.id, 1, promotionTime)
        jdbc.update("""UPDATE discovery_verification_item SET state='COMPLETED',outcome='DIRECT_CLEAR',
            route='DIRECT',qualification='MET',admission_revision=?,projection_state=?,
            completed_at=?,updated_at=? WHERE id=?""",
            revision, projection.name, promotionTime.verificationUtc(), promotionTime.verificationUtc(), item.id)
        // Filter fixtures deliberately include inconsistent persisted rows; do not materialize them as domain items.
    }

    @Test fun `promotion ownership query filters mode activation revision and deduplicates latest qualifying source`() {
        val doc = "APV-FIXTURE-PROMOTION-SHARED"
        promotionItem(6001, doc)
        promotionItem(6001, doc, "CROSSREF")
        // A still newer nonqualifying item cannot replace the latest actual projection's source.
        promotionItem(6001, doc, "EUROPE_PMC", VerificationProjection.PENDING)
        val excluded = VerificationProjection.values().filter { it != VerificationProjection.PROJECTED }
        excluded.forEach { promotionItem(6001, "APV-FIXTURE-PROMOTION-${it.name}", projection = it) }
        promotionItem(6001, "APV-FIXTURE-PROMOTION-NO-REVISION", revision = null)
        promotionItem(6001, "APV-FIXTURE-PROMOTION-UNACTIVATED", activated = false)
        promotionItem(6002, "APV-FIXTURE-PROMOTION-OTHER-RUN")
        promotionItem(6003, "APV-FIXTURE-PROMOTION-AUDIT", mode = VerificationMode.AUDIT)
        val before = databaseSnapshot()
        Mockito.clearInvocations(jdbc)
        val result = repository.reportingPromotionDocuments(6001)
        assertEquals(setOf(doc, "APV-FIXTURE-PROMOTION-NO-REVISION") +
            excluded.map { "APV-FIXTURE-PROMOTION-${it.name}" }, result.managedDocIds)
        assertEquals(mapOf(doc to "CROSSREF"), result.projectedSources)
        assertEquals(DiscoveryVerificationRepository.PromotionDocuments(emptySet(), emptyMap()),
            repository.reportingPromotionDocuments(6003))
        assertEquals(DiscoveryVerificationRepository.PromotionDocuments(emptySet(), emptyMap()),
            repository.reportingPromotionDocuments(999999))
        assertFalse(Mockito.mockingDetails(jdbc).invocations.any {
            it.method.name in setOf("update", "batchUpdate", "execute")
        })
        assertEquals(before, databaseSnapshot())
    }

    @Test fun `changed path HTTP smoke reads real MySQL ledger plus legacy ES without writes or summary mutation`() {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val start = java.time.LocalDateTime.of(2026, 10, 18, 10, 0)
        val projected = "APV-FIXTURE-PROMOTION-LATE"
        val existing = "APV-FIXTURE-PROMOTION-EXISTING"
        val legacy = "APV-FIXTURE-PROMOTION-LEGACY"
        // Projection events are at 12:00, after the 10:20 collection finish.
        promotionItem(6101, projected)
        promotionItem(6101, existing, projection = VerificationProjection.ALREADY_PRESENT)
        assertEquals(2L, counts(6101).autoAdmitted)
        jobs.insertIfAbsent(projected, "OPENALEX", 6000, start) // unique job belongs to an older run
        jobs.insertIfAbsent(existing, "OPENALEX", 6101, start)
        jobs.insertIfAbsent(legacy, "CROSSREF", 6101, start)
        val details = mapOf("promoted" to 0, "autoAdmitted" to 2,
            "bySource" to mapOf("OPENALEX" to mapOf("promoted" to 0), "CROSSREF" to mapOf("promoted" to 0)))
        val summary = mapper.writeValueAsString(mapOf("stats" to details))
        jdbc.update("""INSERT INTO task_execution
            (id,task_type,trigger_type,status,result_summary,started_at,finished_at,created_at,updated_at)
            VALUES (6101,'EXPERT_DISCOVERY','MANUAL','SUCCESS',?,?,?,?,?)""",
            summary, start, start.plusMinutes(20), start, start)
        val rest = org.springframework.web.client.RestTemplate()
        val server = org.springframework.test.web.client.MockRestServiceServer.createServer(rest)
        val reader = DiscoveryPromotionProgressService(jobs, repository, executions, logs, rest,
            com.weibo.talentintroduction.config.ElasticsearchProperties("http://es", "user", "pass", "raw", "candidate", "application"), mapper)
        val store = Mockito.mock(com.weibo.talentintroduction.task.service.TaskProgressStore::class.java)
        val original = com.weibo.talentintroduction.task.service.TaskProgress(
            "EXPERT_DISCOVERY", "RUNNING", 1, 2, 2, executionId = 6101, details = details)
        Mockito.`when`(store.get("EXPERT_DISCOVERY")).thenReturn(original)
        val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            com.weibo.talentintroduction.task.controller.TaskProgressController(store, logs, executions, mapper,
                promotionProgress = reader),
            com.weibo.talentintroduction.task.controller.TaskExecutionController(
                Mockito.mock(com.weibo.talentintroduction.task.service.TaskExecutionService::class.java), executions,
                com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor(logs, mapper), mapper,
                promotionProgress = reader)).build()
        val body = mapper.writeValueAsString(mapOf("docs" to listOf(
            mapOf("_id" to legacy, "found" to true, "_source" to mapOf(
                "tags" to listOf("discovered"), "discoveredAt" to "2026-10-18 10:05:00")),
            mapOf("_id" to legacy, "found" to false))))
        server.expect(org.springframework.test.web.client.ExpectedCount.times(4),
            org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://es/_mget"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(org.springframework.http.HttpMethod.POST))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.docs.length()").value(2))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.docs[0]._id").value(legacy))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(body,
                org.springframework.http.MediaType.APPLICATION_JSON))
        val before = databaseSnapshot() + ("jobs" to jdbc.queryForList("SELECT * FROM expert_academic_enrichment_job ORDER BY id").toString())
        Mockito.clearInvocations(jdbc)
        repeat(2) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/task-progress/EXPERT_DISCOVERY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.details.promoted").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.details.autoAdmitted").value(2))
            val response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/task-executions/6101/detail"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
                .andReturn().response.contentAsString
            val stats = mapper.readTree(mapper.readTree(response).path("rawResultSummary").asText()).path("stats")
            assertEquals(2, stats.path("promoted").asInt())
            assertEquals(1, stats.path("bySource").path("OPENALEX").path("promoted").asInt())
            assertEquals(1, stats.path("bySource").path("CROSSREF").path("promoted").asInt())
        }
        assertFalse(Mockito.mockingDetails(jdbc).invocations.any {
            it.method.name in setOf("update", "batchUpdate", "execute")
        })
        assertEquals(before, databaseSnapshot() +
            ("jobs" to jdbc.queryForList("SELECT * FROM expert_academic_enrichment_job ORDER BY id").toString()))
        assertEquals(summary, executions.findById(6101).get().resultSummary)
        assertEquals(0, original.details!!["promoted"])
        Mockito.verify(store, Mockito.times(2)).get("EXPERT_DISCOVERY")
        Mockito.verifyNoMoreInteractions(store)
        server.verify()
    }

    @Test fun `actual 401 and 403 worker failures reach HTTP capabilities without more claims or writes`() {
        for (status in listOf(401, 403)) {
            VerificationFlowFixture(jdbc, org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.dataSource!!),
                DiscoveryVerificationProperties(enabled = true, workerEnabled = true, apolloApiKey = "fixture-secret",
                    requestsPerMinute = 1000)).use { flow ->
                flow.apolloStatus = status
                val item = flow.seed("APV-FIXTURE-AUTH-$status")
                // Keep F123's queued records away from this worker's claim loop.
                jdbc.update("UPDATE discovery_verification_item SET state='RETRY_WAIT',next_attempt_at='2099-01-01' WHERE owner_execution_id=3001 AND state='QUEUED'")
                val worker = flow.worker()
                val factory = org.springframework.beans.factory.support.DefaultListableBeanFactory()
                factory.registerSingleton("worker", worker)
                val reader = DiscoveryVerificationQueryService(repository, DiscoveryReviewRepository(jdbc), flow.props,
                    factory.getBeanProvider(DiscoveryVerificationWorker::class.java))
                val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                    com.weibo.talentintroduction.discovery.controller.DiscoveryVerificationController(reader)).build()
                assertTrue(reader.capabilities().ready)
                worker.tick()
                assertTrue(worker.isAuthBlocked())
                assertEquals(VerificationState.ERROR, flow.item(item.id).state)
                assertTrue(flow.item(item.id).reasonsJson!!.contains("CONFIG_AUTH"))
                val attempts = repository.findAttempts(item.id)
                assertEquals(status, attempts.last().httpStatus)
                val requests = flow.apolloRequests.size
                val before = databaseSnapshot()
                Mockito.clearInvocations(jdbc)
                repeat(20) {
                    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/expert-discovery/verification/capabilities"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ready").value(false))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.reasonCodes[0]").value("CONFIG_AUTH"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-secret"))))
                    worker.tick()
                }
                assertEquals(before, databaseSnapshot())
                assertEquals(requests, flow.apolloRequests.size)
                assertEquals(attempts, repository.findAttempts(item.id))
                assertFalse(Mockito.mockingDetails(jdbc).invocations.any {
                    it.method.name in setOf("update", "batchUpdate", "execute")
                })
                // A new process-local worker does not infer its latch from historical failures.
                assertFalse(flow.worker().isAuthBlocked())
            }
        }
    }

    @Test fun `successful worker identity and stage events are visible in typed HTTP detail without replay`() {
        VerificationFlowFixture(jdbc, org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.dataSource!!)).use { flow ->
            val seed = flow.seed("APV-FIXTURE-MATCHED-DETAIL")
            jdbc.update("UPDATE discovery_verification_item SET state='RETRY_WAIT',next_attempt_at='2099-01-01' WHERE owner_execution_id=3001 AND state='QUEUED'")
            val worker = flow.worker()
            worker.process(flow.claim())
            val stored = flow.item(seed.id)
            assertEquals("person-1", stored.apolloPersonId)
            assertNotNull(stored.apolloEnteredAt)
            assertNotNull(stored.searchCompletedAt)
            assertNotNull(stored.candidateFoundAt)
            assertNotNull(stored.personMatchedAt)
            val before = databaseSnapshot()
            val requests = flow.apolloRequests.toList() to flow.orcidRequests.toList()
            val detail = query.item(seed.id)
            assertEquals(stored.apolloPersonId, detail.apolloPersonId)
            assertEquals(VerificationApolloStages(stored.apolloEnteredAt, stored.searchCompletedAt,
                stored.candidateFoundAt, stored.personMatchedAt), detail.apolloStages)
            val mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                com.weibo.talentintroduction.discovery.controller.DiscoveryVerificationController(query)).build()
            val response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/expert-discovery/verification/items/${seed.id}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.apolloPersonId").value("person-1"))
                .andReturn().response.contentAsString
            val json = flow.mapper.readTree(response)
            for (stage in listOf("enteredAt", "searchedAt", "candidateFoundAt", "matchedAt")) {
                assertFalse(json.path("apolloStages").path(stage).isNull, stage)
                assertFalse(json.path("apolloStages").path(stage).isMissingNode, stage)
            }
            assertFalse(response.contains("responseJson"))
            assertFalse(response.contains("requestJson"))
            assertEquals(before, databaseSnapshot())
            assertEquals(requests, flow.apolloRequests.toList() to flow.orcidRequests.toList())
        }
    }

    @Test fun `F123 every aggregate batch route result filter and page reconciles on MySQL`() {
        assertF123(counts())
        val batches = query.batches(3001,0,100)
        assertEquals(3L,batches.totalBatches); assertF123(batches.runCounts!!)
        val expected = listOf(listOf(40L,20L,6L,14L,14L,6L,13L,2L,5L),
            listOf(40L,18L,5L,13L,12L,6L,15L,1L,6L), listOf(43L,17L,4L,13L,10L,7L,17L,2L,7L))
        batches.items.forEachIndexed { i, row ->
            assertEquals(i+1,row.batchNumber)
            val c=row.counts
            assertEquals(expected[i],listOf(c.profiles,c.autoAdmitted,c.directAdmitted,c.apolloAdmitted,c.clear,c.unclear,c.pending,c.errors,c.queued))
        }
        assertEquals(listOf(2),query.batches(3001,1,1).items.map { it.batchNumber })
        assertTrue(query.batches(3001,Int.MAX_VALUE,100).items.isEmpty())
        val all = query.records(VerificationReportFilter(3001),0,100).items + query.records(VerificationReportFilter(3001),1,100).items
        assertEquals(123,all.size); assertEquals(123,all.map { it.itemId }.toSet().size)
        assertEquals(all.map { it.itemId }.sorted(),all.map { it.itemId })
        val enteredIds = repository.reportingRecords(VerificationReportFilter(3001),0,100)
            .plus(repository.reportingRecords(VerificationReportFilter(3001),1,100))
            .filter { it.apolloEnteredAt != null }.map { it.id }.toSet()
        for ((result, total) in mapOf("ALL" to 108L, "PENDING" to 45L, "ERROR" to 5L, "QUEUED" to 18L)) {
            assertEquals(total, query.records(VerificationReportFilter(3001,"APOLLO",result),0,100).total)
        }
        val resultMap = mapOf("CLEAR" to setOf("DIRECT_CLEAR","APOLLO_CLEAR"),"UNCLEAR" to setOf("APOLLO_UNCLEAR"),
            "PENDING" to setOf("PENDING"),"ERROR" to setOf("ERROR"),"QUEUED" to setOf("QUEUED"),"PROTECTED" to setOf("PROTECTED"))
        for (batch in listOf(null,1,2,3)) for (route in listOf("ALL","DIRECT","APOLLO")) for (result in listOf("ALL")+resultMap.keys) {
            val expectedRows = all.filter { (batch==null || it.batchNumber==batch) &&
                (route=="ALL" || if (route=="APOLLO") it.itemId in enteredIds else it.route==VerificationRoute.DIRECT) &&
                (result=="ALL" || it.resultLabel in resultMap.getValue(result)) }
            val actual = query.records(VerificationReportFilter(3001,route,result,batchNumber=batch),0,100)
            assertEquals(expectedRows.size.toLong(),actual.total,"$batch/$route/$result")
            assertEquals(expectedRows.take(100).map { it.itemId },actual.items.map { it.itemId })
            assertEquals(expectedRows.size.toLong(),actual.counts!!.profiles)
            assertEquals("ACTIVE",actual.lifecycle) // filtered empty/clear pages never imply the entire run is finished
            val expectedReasons = expectedRows.flatMap { it.reasonCodes.distinct() }.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
            assertEquals(expectedReasons,actual.reasons.associate { it.code to it.count })
        }
        val unclear = query.records(VerificationReportFilter(3001,result="UNCLEAR"),0,100)
        assertEquals(19L,unclear.total); assertEquals(4,unclear.items.first().reasonCodes.size)
        assertEquals("APV-FIXTURE-REASON-4",unclear.reasons.first { it.code=="APV-FIXTURE-REASON-4" }.label)
    }

    @Test fun `batch page cumulative timestamps and all reason counts come from activated full run`() {
        val id = query.records(VerificationReportFilter(3001, batchNumber = 2), 0, 1).items.single().itemId
        val codes = listOf("IDENTITY_AMBIGUOUS", "APV-FIXTURE-UNKNOWN-" + "X".repeat(1100), "R3", "R4", "R5")
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        jdbc.update("UPDATE discovery_verification_item SET reasons_json=?,updated_at='2026-10-19 12:34:56.789' WHERE id=?",
            mapper.writeValueAsString(codes + codes.first()), id)
        val before = databaseSnapshot()
        val page = query.batches(3001, 1, 1)
        val batch = page.items.single()
        assertEquals(2, batch.batchNumber)
        assertEquals(80L, batch.cumulativeProfiles)
        assertEquals(123L, page.runCounts!!.profiles)
        assertEquals(java.time.Instant.parse("2026-10-19T12:34:56.789Z"), batch.updatedAt)
        val all = query.records(VerificationReportFilter(3001, batchNumber = 2), 0, 100)
        val expected = all.items.flatMap { it.reasonCodes.distinct() }.groupingBy { it }.eachCount().mapValues { it.value.toLong() }
        assertEquals(expected, batch.reasons.associate { it.code to it.count })
        assertEquals(codes[1], batch.reasons.single { it.code == codes[1] }.label)
        assertTrue(batch.reasons.size >= 5)
        assertEquals(listOf(40L, 80L, 123L), query.batches(3001, 0, 20).items.map { it.cumulativeProfiles })
        assertEquals(before, databaseSnapshot())
    }

    @Test fun `actual admission history projection registration and audit remain independent`() {
        val first=query.records(VerificationReportFilter(3001),0,1).items.single()
        assertEquals("HOLD",first.currentAdmissionDecision); assertEquals(2L,first.currentAdmissionRevision)
        assertEquals("DIRECT_CLEAR",first.resultLabel); assertF123(counts())
        jdbc.update("UPDATE discovery_verification_item SET projection_state='ERROR',projection_error='APV-FIXTURE-projection' WHERE id=?",first.itemId)
        assertEquals(55L,counts().autoAdmitted); assertEquals(1L,counts().projectionErrors)
        val now=java.time.Instant.parse("2026-10-18T10:00:00Z")
        repository.createRun(4001,VerificationMode.AUDIT,true,"{}",now)
        val audit=repository.reserveItem(VerificationReservation(4001,"APV-FIXTURE-AUDIT","d".repeat(64),VerificationMode.AUDIT.policyVersion,
            "OPENALEX","audit",jdbc.queryForObject("SELECT snapshot_json FROM discovery_verification_item WHERE id=?",String::class.java,first.itemId)!!,"e".repeat(64)),now)
        repository.activateItem(audit.id,1,now)
        jdbc.update("UPDATE discovery_verification_item SET state='COMPLETED',outcome='DIRECT_CLEAR',route='DIRECT',qualification='MET',completed_at=updated_at WHERE id=?",audit.id)
        val summary=query.summary(listOf(4001L)).single()
        assertEquals(0L,summary.counts!!.autoAdmitted); assertEquals(0L,summary.counts!!.directAdmitted)
        assertEquals(VerificationSuggestions(1,1,0),summary.suggested)
        assertEquals(VerificationSuggestions(1,1,0),query.batches(4001,0,20).items.single().suggested)
        assertEquals(VerificationSuggestions(1,1,0),query.records(VerificationReportFilter(4001),0,20).suggested)
        // An unactivated registration error is never the 124th profile.
        jdbc.update("""INSERT INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,source_name,source_item_key,batch_number,snapshot_json,
             fact_fingerprint,state,completed_at,created_at,updated_at)
            VALUES (3001,'APV-FIXTURE-REGISTRATION',?,'APV_20261018_V1_ENFORCE','OPENALEX','registration',0,?,?,'ERROR',NOW(3),NOW(3),NOW(3))""",
            "f".repeat(64),audit.snapshotJson,"e".repeat(64))
        assertEquals(123L,counts().profiles); assertEquals(1L,counts().registrationErrors)
        assertEquals(123L,query.records(VerificationReportFilter(3001),0,100).total)
    }

    @Test fun `evidence and attempts are typed filtered source linked timestamped and twenty GET cycles never write`() {
        val id=query.records(VerificationReportFilter(3001,result="UNCLEAR"),0,1).items.single().itemId
        jdbc.update("UPDATE discovery_verification_attempt SET request_json='{\"api_key\":\"SECRET-REQUEST\"}',response_json='{\"api_key\":\"SECRET-RESPONSE\"}' WHERE item_id=?",id)
        val before=databaseSnapshot()
        Mockito.clearInvocations(jdbc)
        repeat(20) {
            query.capabilities(); query.summary(listOf(3001,3002,3003)); query.batches(3001,0,20)
            query.records(VerificationReportFilter(3001,result="UNCLEAR"),0,20)
            val detail=query.item(id)
            assertEquals(4,detail.reasons.size)
            assertEquals("https://example.invalid/fixture/evidence",detail.evidence.single().sourceUrl)
            assertEquals("2026-10-18T10:00:00.123Z",detail.evidence.single().fetchedAt.toString())
            assertNotNull(detail.evidence.single().sourceUpdatedAt)
            assertEquals(VerificationProvider.APOLLO,detail.attempts.single().provider)
            val json=com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().findAndRegisterModules().writeValueAsString(detail)
            assertFalse(json.contains("SECRET")); assertFalse(json.contains("requestJson")); assertFalse(json.contains("responseJson"))
        }
        val calls=Mockito.mockingDetails(jdbc).invocations
        calls.forEach { invocation ->
            val sql=invocation.arguments.firstOrNull() as? String
            if(sql!=null) assertFalse(Regex("(?i)^\\s*(INSERT|UPDATE|DELETE|REPLACE|ALTER|CREATE|DROP|TRUNCATE)\\b").containsMatchIn(sql),sql)
        }
        assertEquals(before,databaseSnapshot())
        assertTrue(DiscoveryVerificationQueryService::class.java.declaredConstructors.flatMap { it.parameterTypes.toList() }
            .none { it.simpleName.contains("Client") || it.simpleName.contains("Worker") || it.simpleName.contains("ExpertIndex") })
    }
    private fun databaseSnapshot() = listOf("task_execution","expert_discovery_admission","discovery_verification_run","discovery_verification_item","discovery_verification_attempt")
        .associateWith { table -> jdbc.queryForList("SELECT * FROM $table ORDER BY 1").toString() }

    @Test fun `null legacy real zero expired run and fixture reimport are safe`() {
        val rows=query.summary(listOf(3001,3002,3003))
        assertNull(rows[1].counts); assertEquals("NOT_RECORDED",rows[1].coverage)
        assertEquals(VerificationCounts(),rows[2].counts); assertEquals("RECORDED",rows[2].coverage)
        importFixture(); assertF123(counts())
        jdbc.update("DELETE FROM task_execution WHERE id=3001")
        assertEquals("EXPIRED",query.summary(listOf(3001)).single().collectionStatus)
        assertEquals("EXPIRED",query.batches(3001,0,20).collectionStatus)
        assertEquals("EXPIRED",query.records(VerificationReportFilter(3001),0,20).collectionStatus)
        assertF123(counts())
        importFixture()
        jdbc.update("UPDATE task_execution SET request_payload='non-fixture-do-not-touch' WHERE id=3002")
        val snapshot=databaseSnapshot()
        val rejected=mysql.execInContainer("mysql","-utest","-ptest",mysql.databaseName,"-e","source /tmp/apv-fixture.sql")
        assertTrue(rejected.stdout.contains("occupied") || rejected.stderr.contains("occupied"))
        assertEquals(snapshot,databaseSnapshot())
        // The rejected routine is fixture-owned; remove it so subsequent imports can proceed.
        jdbc.execute("DROP PROCEDURE IF EXISTS APV_FIXTURE_seed_reporting")
        jdbc.update("UPDATE task_execution SET request_payload='{\"fixture\":\"APV-FIXTURE-LEGACY\"}' WHERE id=3002")
    }

    @Test fun `manual fixture refuses wrong database before creating any data tables`() {
        val created=mysql.execInContainer("mysql","-uroot","-ptest","-e","CREATE DATABASE IF NOT EXISTS apv_guard_probe")
        assertEquals(0,created.exitCode,created.stderr)
        val rejected=mysql.execInContainer("mysql","-uroot","-ptest","apv_guard_probe","-e","source /tmp/apv-fixture.sql")
        assertTrue((rejected.stdout+rejected.stderr).contains("requires an independent database"))
        val tables=mysql.execInContainer("mysql","-uroot","-ptest","apv_guard_probe","-N","-e","SHOW TABLES")
        assertEquals("",tables.stdout.trim())
    }

    @Test fun `repeatable read preserves revision count page and admission while concurrent writer commits`() {
        val pool=Executors.newSingleThreadExecutor()
        try {
            val tx=TransactionTemplate(manager).apply { isolationLevel=TransactionDefinition.ISOLATION_REPEATABLE_READ; isReadOnly=true }
            tx.execute {
                val before=query.summary(listOf(3001)).single()
                val first=query.records(VerificationReportFilter(3001),0,1).items.single()
                pool.submit {
                    TransactionTemplate(manager).execute {
                        jdbc.update("UPDATE discovery_verification_run SET revision=revision+1 WHERE execution_id=3001")
                        jdbc.update("UPDATE discovery_verification_item SET outcome='PENDING',qualification='UNKNOWN',route='NONE',admission_revision=NULL,projection_state='NOT_REQUIRED' WHERE id=?",first.itemId)
                        jdbc.update("UPDATE expert_discovery_admission SET decision='REJECTED',revision=revision+1 WHERE expert_doc_id=?",first.docId)
                    }
                }.get(15,TimeUnit.SECONDS)
                val after=query.summary(listOf(3001)).single()
                assertEquals(before,after.copy(asOf=before.asOf))
                val page=query.records(VerificationReportFilter(3001),0,1)
                assertEquals(before.snapshotVersion,page.snapshotVersion); assertEquals("HOLD",page.items.single().currentAdmissionDecision)
                assertEquals("DIRECT_CLEAR",page.items.single().resultLabel)
            }
            assertEquals(124L,query.summary(listOf(3001)).single().snapshotVersion)
            assertEquals(54L,counts().autoAdmitted)
        } finally { pool.shutdownNow() }
    }

    @Test fun `bound LIKE literals and ten thousand row EXPLAIN use owner index with capped stable pages`() {
        val first=query.records(VerificationReportFilter(3001),0,1).items.single()
        jdbc.update("UPDATE discovery_verification_item SET snapshot_json=JSON_SET(snapshot_json,'$.profile.givenNames',?) WHERE id=?","Literal%_\\! Needle",first.itemId)
        for (q in listOf("%","_","\\","!","Needle","Needle APV-FIXTURE-001","' OR 1=1 --")) {
            val response=query.records(VerificationReportFilter(3001,q=q),0,100)
            assertEquals(if(q.startsWith("'"))0L else 1L,response.total,q)
        }
        repository.createRun(5001,VerificationMode.ENFORCE,false,"{}",java.time.Instant.now())
        val snapshot=jdbc.queryForObject("SELECT snapshot_json FROM discovery_verification_item WHERE id=?",String::class.java,first.itemId)!!
        jdbc.batchUpdate("""INSERT INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,source_name,source_item_key,batch_number,snapshot_json,fact_fingerprint,state,activated_at,created_at,updated_at)
            VALUES (5001,?,?,'APV_20261018_V1_ENFORCE','OPENALEX',?,1,?,?,'QUEUED',NOW(3),NOW(3),NOW(3))""",
            (1..10000).map { n -> arrayOf<Any>("APV-FIXTURE-LARGE-$n","a".repeat(64),"large-$n",snapshot,"b".repeat(64)) })
        jdbc.execute("ANALYZE TABLE discovery_verification_item")
        for(owner in listOf(3001L,5001L)) {
            val sql="SELECT i.* FROM discovery_verification_item i FORCE INDEX (idx_dvi_run_batch) WHERE i.owner_execution_id=$owner AND i.activated_at IS NOT NULL ORDER BY i.id LIMIT 100 OFFSET 0"
            val plan=jdbc.queryForList("EXPLAIN $sql")
            println("APV-EXPLAIN owner=$owner rows=${if(owner==3001L)123 else 10000}: $plan")
            assertTrue(plan.any { it["key"]=="idx_dvi_run_batch" },plan.toString())
            Mockito.clearInvocations(jdbc)
            val records=query.records(VerificationReportFilter(owner),0,100)
            assertEquals(if(owner==3001L)123L else 10000L,records.total)
            assertEquals(100,records.items.size)
            val admissionReads=Mockito.mockingDetails(jdbc).invocations.mapNotNull { it.arguments.firstOrNull() as? String }
                .filter { it.startsWith("SELECT * FROM expert_discovery_admission WHERE expert_doc_id IN") }
            assertTrue(admissionReads.isNotEmpty())
            assertTrue(admissionReads.all { sqlText -> sqlText.count { it=='?' }==100 },admissionReads.toString())
            assertTrue(query.records(VerificationReportFilter(owner),Int.MAX_VALUE,100).items.isEmpty())
        }
    }
}
