package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.campaign.domain.ExpertContact
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.*
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.Mockito
import org.springframework.aop.framework.ProxyFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.weibo.talentintroduction.expert.domain.EmailValidationResult
import com.weibo.talentintroduction.task.service.TaskProgressStore

/**
 * Fixed 12-target business fixture. Real MySQL + strict loopback ES + deterministic provider HTTP.
 * The real reprocess service, consumer, recheck, worker, admission and projection seams are used;
 * this fixture only seeds inputs and observes results.
 */
@EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
class DiscoveryPaperReprocessEndToEndTest {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("paper_reprocess_e2e").withUsername("test").withPassword("test")
            .withCommand("--log-bin-trust-function-creators=1")
        lateinit var jdbc: JdbcTemplate
        lateinit var manager: DataSourceTransactionManager
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Docker MySQL is required; missing infrastructure is NOT_RUN" }
            mysql.start()
            val ds = DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password)
            Flyway.configure().dataSource(ds).placeholderReplacement(false).target("173").load().migrate()
            jdbc = JdbcTemplate(ds)
            manager = DataSourceTransactionManager(ds)
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        /** Separate runtime entry: real MySQL and real service seams, never the production Spring app. */
        @JvmStatic fun main(args: Array<String>) {
            require(args.contentEquals(arrayOf("--paper-reprocess-e2e-smoke"))) { "Unsupported fixture arguments" }
            start()
            try {
                DiscoveryPaperReprocessEndToEndTest().apply {
                    clean()
                    `fixed twelve targets separate new repair conflict protection missing and write failure`()
                    clean()
                    `cancel retains committed results and transient write recovery never replays successful targets`()
                    clean()
                    `explicit retry resumes cancelled ordinary qualification without duplicating effects`()
                    clean()
                    `legacy parent retry resumes a cancelled owned child under the same epoch`()
                    clean()
                    `sync retry projects admitted ordinary and owned child items without replaying providers`()
                }
                println("PAPER06_RUNTIME_OK realMySQL=true loopbackEsCAS=true realServices=true paidCalls=0 productionWrites=0")
            } finally { stop() }
        }
    }

    @BeforeEach fun clean() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_e2e_reserve")
        listOf("discovery_paper_reprocess_item", "discovery_paper_reprocess_run",
            "discovery_person_author_key", "discovery_person_member", "discovery_person",
            "expert_academic_enrichment_job").forEach { jdbc.update("DELETE FROM $it") }
        listOf("discovery_paper_archive_attempt", "discovery_paper_archive_record", "discovery_paper_archive_blob",
            "discovery_paper_archive_setting").forEach { jdbc.update("DELETE FROM $it") }
        listOf("discovery_apollo_setting", "discovery_paper_job", "discovery_collection_stream", "discovery_pipeline",
            "discovery_source_cursor", "expert_email_alias", "expert_contact", "discovery_recheck_input",
            "discovery_verification_attempt", "discovery_verification_item", "discovery_verification_run",
            "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
    }

    @Test fun `partial page coverage keeps proven author binding and permits new RAW`() {
        Harness().use { h ->
            val paper = h.seedPartialPdfSource()
            val run = h.create(Harness.RECOVERY_SOURCE)
            h.pump(run.executionId, ReprocessState.COMPLETED)
            val parsed = h.repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items.single().result!!
            assertTrue(parsed.toString().contains("PAGE_NOT_SCANNED"))
            val target = h.target(run.executionId, paper.email!!)
            assertEquals(1, target.result!!.path("actualNew").asInt())
            assertFalse(target.result!!.path("applicationError").asBoolean())
            assertEquals("Alice", h.readRaw(paper.doc)!!["givenNames"])
            assertEquals("Smith", h.readRaw(paper.doc)!!["familyNames"])
        }
    }

    @Test fun `first ready review starts before last target batch is applied and groups survive retries`() {
        Harness().use { h ->
            h.seedBatchedSource()
            val run = h.create(Harness.CHILD_SOURCE)
            var pendingAtFirstStart: Long? = null
            h.apply.afterEffect = { point ->
                if (point == "AFTER_START" && pendingAtFirstStart == null) {
                    pendingAtFirstStart = jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_paper_reprocess_item
                        WHERE execution_id=? AND kind='TARGET' AND result_json IS NULL""", Long::class.java, run.executionId)
                }
            }
            h.pump(run.executionId, ReprocessState.COMPLETED)
            assertTrue((pendingAtFirstStart ?: 0) > 0, "First child must start before remaining target applications")
            val parent = h.repository.find(run.executionId)!!
            assertTrue(parent.scope.path("stagedReviews").asBoolean())
            assertEquals(1, parent.scope.path("reviewGroups").size())
            assertEquals(1L, jdbc.queryForObject("""SELECT COUNT(*) FROM discovery_verification_run
                WHERE recheck_json IS NOT NULL AND JSON_EXTRACT(settings_json,'$.paperReprocessParentId')=?""",
                Long::class.java, run.executionId))
        }
    }

    @Test fun `child observation HTTP failure preserves peers and explicit retry recovers receipts`() {
        Harness().use { h ->
            h.seedBatchedSource()
            val run = h.create(Harness.CHILD_SOURCE)
            h.apply.afterEffect = { point -> if (point == "AFTER_START") h.failObservationReads = true }
            h.pump(run.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            val failed = h.targets(run.executionId).filter { it.result?.path("observationError")?.asBoolean() == true }
            assertTrue(failed.isNotEmpty())
            assertTrue(failed.all { it.result!!.path("applied").asBoolean() })
            assertTrue(failed.all { it.result!!.path("failure").path("causes").any { c -> c.path("httpStatus").asInt() == 503 } })
            assertTrue(h.repository.summary(run.executionId).targetComplete >= 100)
            val writes = h.rawWrites
            h.failObservationReads = false
            h.apply.afterEffect = {}
            val retried = h.service.retry(run.executionId, h.repository.find(run.executionId)!!.revision)
            h.pump(retried.executionId, ReprocessState.COMPLETED)
            assertEquals(writes, h.rawWrites)
            assertFalse(h.targets(retried.executionId).any { it.result?.path("observationError")?.asBoolean() == true })
        }
    }

    @Test fun `partial paper failure admits successful peers and followup adds recovered expert without duplicate RAW`() {
        Harness().use { h ->
            h.seedMainSource()
            h.service.beforeParse = { item ->
                if (item.input.path("paperKey").asText() == h.w.key) error("fixture parse failure")
            }
            val first = h.create(Harness.SOURCE)
            h.pump(first.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            val before = h.terminalReceipts(first.executionId)
            assertEquals(1L, h.repository.summary(first.executionId).paperErrors)
            assertTrue(h.repository.summary(first.executionId).actualNew > 0)
            assertTrue(h.repository.summary(first.executionId).actualAdmitted > 0)
            h.service.beforeParse = {}
            val retried = h.service.retry(first.executionId, h.repository.find(first.executionId)!!.revision)
            assertNotEquals(first.executionId, retried.executionId)
            h.pump(retried.executionId, ReprocessState.COMPLETED)
            assertEquals(1, h.repository.summary(retried.executionId).actualNew)
            assertEquals(h.w.doc, h.target(retried.executionId, h.w.email!!).result!!.path("docId").asText())
            assertEquals(before, h.terminalReceipts(first.executionId))
        }
    }

    @Test fun `fixed twelve targets separate new repair conflict protection missing and write failure`() {
        Harness().use { h ->
            h.seedMainSource()
            h.failCandidateDocs += h.n1.doc
            h.failRawDocs += h.w.doc
            val run = h.create(Harness.SOURCE)
            h.pump(run.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            h.assertFixedResults(run.executionId)
            val summary = h.repository.summary(run.executionId)
            assertEquals(2, summary.actualNew)
            assertEquals(2, summary.actualUpdated)
            assertEquals(2, summary.actualAdmitted)
            assertEquals(2L, summary.conflict)
            assertEquals(2L, summary.protected)
            assertEquals(2L, summary.targetErrors)
            assertEquals(2L, summary.qualification.met)
            assertEquals(1L, summary.syncFailure)

            h.reportRun(run.executionId, "FIRST")
            val n1Item = h.verificationItem(h.n1.doc)
            assertNotNull(n1Item)
            assertEquals(VerificationProjection.ERROR, n1Item!!.projectionState)
            assertNotNull(n1Item.admissionRevision)
            // The existing eight-step seam serves the ordinary epoch0 item and the REPAIRED child alike.
            val ordinary = h.queries.recheckItem(run.executionId, n1Item.id)
            assertEquals(n1Item.id, ordinary.itemId); assertEquals(run.executionId, ordinary.runId)
            assertEquals((1..8).toList(), ordinary.trace.steps.map { it.stepNo }.distinct().sorted())
            val repaired = h.target(run.executionId, requireNotNull(h.e1.email)).result!!
            val childRunId = repaired.path("verificationRunId").asLong()
            val childItemId = repaired.path("verificationItemId").asLong()
            val child = h.queries.recheckItem(childRunId, childItemId)
            assertEquals(childItemId, child.itemId)
            assertEquals((1..8).toList(), child.trace.steps.map { it.stepNo }.distinct().sorted())
            // Wrong parent/item and an unrelated ordinary run stay outside the exception.
            assertThrows(ResponseStatusException::class.java) { h.queries.recheckItem(run.executionId, childItemId) }
            h.f.repository.createRun(Harness.UNRELATED_RUN, VerificationMode.AUDIT, false, "{}", Instant.now())
            assertThrows(ResponseStatusException::class.java) { h.queries.recheckItem(Harness.UNRELATED_RUN, n1Item.id) }
            val providerCalls = h.providerUris.toList()
            h.failCandidateDocs.clear()
            // The public sync-retry seam projects only; admission and provider calls are retained.
            val retriedItem = h.rechecks.syncRetry(run.executionId, RecheckSyncRequest(listOf(n1Item.id))).single()
            assertEquals(VerificationProjection.PROJECTED, retriedItem.projection)
            val projected = h.f.repository.findItem(n1Item.id)!!
            assertEquals(VerificationProjection.PROJECTED, projected.projectionState)
            assertEquals(providerCalls, h.providerUris)
            assertEquals(n1Item.admissionRevision, projected.admissionRevision)
            assertThrows(ResponseStatusException::class.java) {
                h.rechecks.syncRetry(run.executionId, RecheckSyncRequest(listOf(999999)))
            }

            // A same-run retry only resets non-terminal targets. Every committed target receipt must
            // remain byte-for-byte identical and no business effect may be replayed.
            val before = h.terminalReceipts(run.executionId)
            val rawWrites = h.server.rawWrites
            val admissions = h.admissionRows()
            val retried = h.service.retry(run.executionId, h.repository.find(run.executionId)!!.revision)
            h.pump(retried.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            assertEquals(before, h.terminalReceipts(run.executionId))
            assertEquals(rawWrites, h.server.rawWrites)
            assertEquals(admissions, h.admissionRows())
            val retriedSummary = h.repository.summary(run.executionId)
            assertEquals(2, retriedSummary.actualNew)
            assertEquals(2, retriedSummary.actualUpdated)
            assertEquals(2, retriedSummary.actualAdmitted)

            // Second task over the same frozen source: no new binding, no repair, already admitted
            // targets are reported separately rather than counted as new admissions.
            h.restoreMissingSource()
            val second = h.create(Harness.SOURCE)
            h.pump(second.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            val repeat = h.repository.summary(second.executionId)
            assertEquals(0, repeat.actualNew)
            assertEquals(0, repeat.actualUpdated)
            assertEquals(0, repeat.actualAdmitted)
            assertEquals(2, h.targets(second.executionId).count { it.result?.path("alreadyAdmitted")?.asInt() == 1 })
            h.reportRun(second.executionId, "SECOND")
            assertEquals(2L, repeat.conflict)
            assertEquals(2L, repeat.protected)
        }
    }

    @Test fun `cancel retains committed results and transient write recovery never replays successful targets`() {
        Harness().use { h ->
            h.seedCancelSource()
            val cancelled = h.create(Harness.CANCEL_SOURCE)
            h.cancelOnFirstConsume = true
            h.pump(cancelled.executionId, ReprocessState.CANCELLED)
            val cancelSummary = h.repository.summary(cancelled.executionId)
            assertEquals(1, cancelSummary.actualNew)
            val target = h.targets(cancelled.executionId).single()
            assertEquals("NEW", target.result!!.path("targetOutcome").asText())
            assertNotNull(h.readRaw(h.cancelDoc))
            assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM discovery_verification_item WHERE expert_doc_id=?", Long::class.java, h.cancelDoc))
            h.reportRun(cancelled.executionId, "CANCELLED")

            h.cancelOnFirstConsume = false
            h.seedRecoverySource()
            h.failRawDocs += h.recoverySecond.doc
            val first = h.create(Harness.RECOVERY_SOURCE)
            h.pump(first.executionId, ReprocessState.COMPLETED_WITH_ERRORS)
            assertEquals(1, h.repository.summary(first.executionId).actualNew)
            val successful = h.targets(first.executionId).single { it.result?.path("targetOutcome")?.asText() == "NEW" }
            val successfulReceipt = successful.result!!.deepCopy<JsonNode>()
            h.failRawDocs.clear()
            h.service.retry(first.executionId, h.repository.find(first.executionId)!!.revision)
            h.pump(first.executionId, ReprocessState.COMPLETED)
            assertEquals(2, h.repository.summary(first.executionId).actualNew)
            assertEquals(successfulReceipt, h.repository.item(first.executionId, successful.id)!!.result)
            h.reportRun(first.executionId, "RECOVERED")
        }
    }

    @Test fun `explicit retry resumes cancelled ordinary qualification without duplicating effects`() {
        Harness().use { h ->
            h.seedCancelSource()
            val run = h.create(Harness.CANCEL_SOURCE)
            h.cancelOnFirstConsume = true
            h.pump(run.executionId, ReprocessState.CANCELLED)
            val target = h.targets(run.executionId).single()
            assertEquals("NEW", target.result!!.path("targetOutcome").asText())
            assertEquals(1, target.result!!.path("actualNew").asInt())
            assertEquals("NOT_STARTED", target.result!!.path("trace")[4].path("state").asText())
            val item = h.verificationItem(h.cancelDoc)!!
            assertEquals(VerificationState.CANCELLED, item.state)
            assertNull(item.admissionRevision)
            assertEquals(VerificationQualification.NOT_EVALUATED, item.qualification)
            val cancelledSteps = h.f.repository.findSteps(item.id)
            assertTrue(cancelledSteps.any { it.state == VerificationStepState.CANCELLED || it.state == VerificationStepState.SKIPPED })
            val rawWrites = h.server.rawWrites
            val rawBefore = h.readRaw(h.cancelDoc)
            val personRows = jdbc.queryForObject("SELECT COUNT(*) FROM discovery_person_member", Long::class.java)

            h.cancelOnFirstConsume = false
            val retried = h.service.retry(run.executionId, h.repository.find(run.executionId)!!.revision)
            assertEquals("QUEUED", jdbc.queryForObject("SELECT state FROM discovery_verification_item WHERE id=?",
                String::class.java, item.id))
            h.pump(retried.executionId, ReprocessState.COMPLETED)

            val finished = h.verificationItem(h.cancelDoc)!!
            assertEquals(item.id, finished.id)
            assertEquals(VerificationState.COMPLETED, finished.state)
            assertNotEquals(VerificationQualification.NOT_EVALUATED, finished.qualification)
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE expert_doc_id=?",
                Long::class.java, h.cancelDoc))
            assertEquals(rawWrites, h.server.rawWrites)
            assertEquals(rawBefore, h.readRaw(h.cancelDoc))
            assertEquals(personRows, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_person_member", Long::class.java))
            val summary = h.repository.summary(run.executionId)
            assertEquals(1, summary.actualNew)
            assertEquals(0L, summary.qualification.notEvaluated)
            val receipt = h.repository.item(run.executionId, target.id)!!.result!!
            assertEquals("SUCCESS", receipt.path("trace")[4].path("state").asText())
            assertEquals("SUCCESS", receipt.path("trace")[5].path("state").asText())
            assertTrue(h.f.repository.findSteps(item.id).any { step ->
                step.reasonJson?.contains("CANCELLED") == true
            }, "cancellation history stays durable")
            h.reportRun(run.executionId, "RETRIED")
        }
    }

    @Test fun `legacy parent retry resumes a cancelled owned child under the same epoch`() {
        Harness().use { h ->
            h.seedExistingAuthorSource()
            val run = h.create(Harness.CHILD_SOURCE)
            h.cancelOnFirstStart = true
            h.pump(run.executionId, ReprocessState.CANCELLED)
            val target = h.targets(run.executionId).single()
            val childId = target.result!!.path("childRunId").asLong()
            assertTrue(childId > 0)
            assertEquals(RecheckPhase.CANCELLED, h.f.repository.recheck(h.f.repository.findRun(childId)!!).control.phase)
            assertEquals(1, h.f.repository.recheckItems(childId, 0, 100).second.size)
            assertEquals(VerificationState.CANCELLED, h.f.repository.recheckItems(childId, 0, 100).second.single().state)
            assertEquals(1, h.f.repository.paperReprocessRuns(run.executionId).count { it.recheckJson != null })

            h.cancelOnFirstStart = false
            // Simulate a parent created before staged grouping existed, retaining its already-owned child.
            val legacyScope = h.repository.find(run.executionId)!!.scope.deepCopy<ObjectNode>()
                .also { it.remove(listOf("stagedReviews", "reviewGroups")) }
            jdbc.update("UPDATE discovery_paper_reprocess_run SET scope_json=? WHERE execution_id=?",
                legacyScope.toString(), run.executionId)
            val retried = h.service.retry(run.executionId, h.repository.find(run.executionId)!!.revision)
            h.pump(retried.executionId, ReprocessState.COMPLETED)

            assertFalse(h.repository.find(run.executionId)!!.scope.path("stagedReviews").asBoolean())
            val receipt = h.repository.item(run.executionId, target.id)!!.result!!
            assertEquals(childId, receipt.path("childRunId").asLong())
            assertEquals(1, h.f.repository.paperReprocessRuns(run.executionId).count { it.recheckJson != null }, "no second epoch is prepared")
            assertEquals(RecheckPhase.COMPLETED, h.f.repository.recheck(h.f.repository.findRun(childId)!!).control.phase)
            assertEquals(VerificationState.COMPLETED, h.f.repository.recheckItems(childId, 0, 100).second.single().state)
            assertEquals("SUCCESS", receipt.path("trace")[4].path("state").asText())
            h.reportRun(run.executionId, "CHILD_RETRIED")
        }
    }

    @Test fun `sync retry projects admitted ordinary and owned child items without replaying providers`() {
        Harness().use { h ->
            h.seedMainSource()
            // Both the ordinary epoch0 item and the REPAIRED child item fail candidate projection.
            h.failCandidateDocs += h.n1.doc
            h.failCandidateDocs += h.e1.doc
            val run = h.create(Harness.SOURCE)
            h.pump(run.executionId, ReprocessState.COMPLETED_WITH_ERRORS)

            val ordinary = h.verificationItem(h.n1.doc)!!
            assertEquals(VerificationState.COMPLETED, ordinary.state)
            assertNotNull(ordinary.admissionRevision)
            assertEquals(VerificationProjection.ERROR, ordinary.projectionState)
            val repaired = h.target(run.executionId, requireNotNull(h.e1.email)).result!!
            assertEquals("REPAIRED", repaired.path("targetOutcome").asText())
            val childRunId = repaired.path("verificationRunId").asLong()
            val childItemId = repaired.path("verificationItemId").asLong()
            assertNotEquals(run.executionId, childRunId)
            val child = h.f.repository.findItem(childItemId)!!
            assertEquals(childRunId, child.ownerExecutionId)
            assertEquals(childRunId, child.reviewEpoch)
            assertEquals(VerificationState.COMPLETED, child.state)
            assertEquals(0L, child.admissionRevision) // revision 0 is a legitimate admission, never "missing"
            assertEquals(VerificationProjection.ERROR, child.projectionState)
            assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM discovery_verification_run WHERE execution_id=? AND recheck_json IS NOT NULL",
                Long::class.java, childRunId))

            val admissions = h.admissionRows()
            val providerCalls = h.providerUris.toList()
            val rawWrites = h.server.rawWrites
            h.failCandidateDocs.clear()

            // Ordinary epoch0 run: only its server-proven paper-reprocess ownership opens the guard.
            val ordinaryRetried = h.rechecks.syncRetry(run.executionId, RecheckSyncRequest(listOf(ordinary.id))).single()
            assertEquals(ordinary.id, ordinaryRetried.itemId)
            assertEquals(run.executionId, ordinaryRetried.runId)
            assertEquals(VerificationProjection.PROJECTED, ordinaryRetried.projection)
            // The owned child keeps the generic recheck guard and its own owner/epoch identity.
            val childRetried = h.rechecks.syncRetry(childRunId, RecheckSyncRequest(listOf(childItemId))).single()
            assertEquals(childItemId, childRetried.itemId)
            assertEquals(childRunId, childRetried.runId)
            assertEquals(VerificationProjection.PROJECTED, childRetried.projection)

            val ordinaryAfter = h.f.repository.findItem(ordinary.id)!!
            val childAfter = h.f.repository.findItem(childItemId)!!
            assertEquals(VerificationProjection.PROJECTED, ordinaryAfter.projectionState)
            assertEquals(VerificationProjection.PROJECTED, childAfter.projectionState)
            assertEquals(ordinary.admissionRevision, ordinaryAfter.admissionRevision)
            assertEquals(0L, childAfter.admissionRevision)
            assertEquals(admissions, h.admissionRows())
            assertEquals(providerCalls, h.providerUris)
            assertEquals(rawWrites, h.server.rawWrites)
            // A repeat retry stays idempotent: already-projected items are returned unchanged.
            assertEquals(VerificationProjection.PROJECTED,
                h.rechecks.syncRetry(run.executionId, RecheckSyncRequest(listOf(ordinary.id))).single().projection)
            assertEquals(VerificationProjection.PROJECTED,
                h.rechecks.syncRetry(childRunId, RecheckSyncRequest(listOf(childItemId))).single().projection)

            // Wrong/foreign/unowned combinations stay rejected and project nothing.
            assertThrows(ResponseStatusException::class.java) {
                h.rechecks.syncRetry(run.executionId, RecheckSyncRequest(listOf(childItemId)))
            }
            assertThrows(ResponseStatusException::class.java) {
                h.rechecks.syncRetry(childRunId, RecheckSyncRequest(listOf(ordinary.id)))
            }
            h.f.repository.createRun(Harness.UNRELATED_RUN, VerificationMode.AUDIT, false, "{}", Instant.now())
            assertThrows(ResponseStatusException::class.java) {
                h.rechecks.syncRetry(Harness.UNRELATED_RUN, RecheckSyncRequest(listOf(ordinary.id)))
            }
            assertEquals(VerificationProjection.PROJECTED, h.f.repository.findItem(ordinary.id)!!.projectionState)
            assertEquals(VerificationProjection.PROJECTED, h.f.repository.findItem(childItemId)!!.projectionState)
            assertEquals(admissions, h.admissionRows())
            assertEquals(providerCalls, h.providerUris)
            h.reportRun(run.executionId, "SYNC_RETRY")
        }
    }

    private class Paper(val key: String, val email: String?, val given: String, val family: String,
        val orcid: String?, val country: String = "United Kingdom", val facts: String = "") {
        var doc: String = ""
        var recordId: Long = 0
        var attemptId: Long = 0
        var bodySha: String = ""
    }

    private class Harness : AutoCloseable {
        companion object {
            const val SOURCE = 30711L
            const val CANCEL_SOURCE = 30712L
            const val RECOVERY_SOURCE = 30713L
            const val CHILD_SOURCE = 30715L
            const val UNRELATED_RUN = 99001L
            const val PROFESSOR_ORCID = "0000-0002-1825-0097"
            const val OTHER_ORCID = "0000-0002-1562-097X"
        }

        val f = VerificationFlowFixture(jdbc, manager, DiscoveryVerificationProperties(enabled = true,
            workerEnabled = true, apolloApiKey = "fixture-key", requestsPerMinute = 1000, concurrency = 1))
        val mapper = f.mapper
        val root = Files.createTempDirectory("paper-reprocess-e2e-").toRealPath()
        val docs = linkedMapOf("raw" to linkedMapOf<String, MutableMap<String, Any?>>(),
            "candidate" to linkedMapOf<String, MutableMap<String, Any?>>(),
            "application" to linkedMapOf<String, MutableMap<String, Any?>>())
        val versions = mutableMapOf<Pair<String, String>, Long>()
        val failRawDocs = mutableSetOf<String>()
        val failCandidateDocs = mutableSetOf<String>()
        val providerUris = mutableListOf<String>()
        @Volatile var failObservationReads = false
        var rawWrites = 0
        val esExecutor = Executors.newCachedThreadPool()
        val server = ESFixture(this)
        val props: ElasticsearchProperties
        val writer: ExpertIndexWriterService
        val review: DiscoveryReviewService
        val commits: DiscoveryVerificationCommitService
        val traces: DiscoveryVerificationTraceRepository
        val archiveRepo: DiscoveryPaperArchiveRepository
        val archive: DiscoveryPaperArchiveService
        val analyzer: PaperBodyAnalyzer
        val papers: PaperVerificationEvidenceService
        val contacts = Mockito.mock(ExpertContactRepository::class.java)
        val aliases = Mockito.mock(ExpertEmailAliasService::class.java)
        val persons: DiscoveryPersonRegistrationService
        val retained: RetainedPaperIdentityService
        val queries: DiscoveryVerificationQueryService
        val tasks: TaskExecutionRepository
        val context = AnnotationConfigApplicationContext().also {
            it.registerBean("dataSource", javax.sql.DataSource::class.java, java.util.function.Supplier { manager.dataSource!! })
            it.register(Paper04JdbcConfig::class.java)
            it.refresh()
        }
        val repository: DiscoveryPaperReprocessRepository
        val registration: DiscoveryVerificationRegistrationService
        val worker: DiscoveryVerificationWorker
        val rechecks: DiscoveryRecheckService
        val consumer: ExpertDiscoveryService
        val apply: DiscoveryPaperReprocessApplyService
        val service: DiscoveryPaperReprocessService
        val n1 = Paper("n1-new", "n1.new@fixture.edu", "Nora", "One", PROFESSOR_ORCID)
        val n2 = Paper("n2-new", "n2.new@fixture.edu", "Neil", "Two", OTHER_ORCID)
        val e1 = Paper("e1-empty", "e1.empty@fixture.edu", "Elena", "Empty", PROFESSOR_ORCID)
        val e2 = Paper("e2-empty", "e2.empty@fixture.edu", "Evan", "Empty", OTHER_ORCID)
        val c1 = Paper("c1-complete", "c1.complete@fixture.edu", "Cora", "Complete", null)
        val c2 = Paper("c2-complete", "c2.complete@fixture.edu", "Cyrus", "Complete", null)
        val x1a = Paper("x1-conflict-a", "x1.conflict@fixture.edu", "Ada", "Lee", null)
        val x1b = Paper("x1-conflict-b", "x1.conflict@fixture.edu", "Alan", "Lee", null)
        val x2a = Paper("x2-conflict-a", "x2.conflict@fixture.edu", "Xena", "Kai", PROFESSOR_ORCID)
        val x2b = Paper("x2-conflict-b", "x2.conflict@fixture.edu", "Xena", "Kai", OTHER_ORCID)
        val p1 = Paper("p1-contact", "p1.contact@fixture.edu", "Pia", "Contact", null)
        val p2 = Paper("p2-manual", "p2.manual@fixture.edu", "Pablo", "Manual", null)
        val m = Paper("m-missing", "m.missing@fixture.edu", "Mona", "Missing", null)
        val w = Paper("w-write", "w.write@fixture.edu", "Wade", "Write", PROFESSOR_ORCID)
        val trigger = Paper("trigger-no-email", null, "Tess", "Trigger", null)
        val cancelPaper = Paper("cancel-paper", "cancel.new@fixture.edu", "Cara", "Cancel", PROFESSOR_ORCID)
        val existingAuthor = Paper("existing-author", "existing.author@fixture.edu", "Edith", "Existing", null)
        val recoveryFirst = Paper("recovery-first", "recovery.first@fixture.edu", "Rita", "First", PROFESSOR_ORCID)
        val recoverySecond = Paper("recovery-second", "recovery.second@fixture.edu", "Rami", "Second", OTHER_ORCID)
        var cancelOnFirstConsume = false
        var cancelOnFirstStart = false
        private var cancelTriggered = false
        private var cancelRunId: Long? = null

        init {
            server.start()
            props = f.esProperties.copy(baseUrl = "http://127.0.0.1:${server.port}")
            val esHttp = RestTemplate()
            esHttp.interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { request, bytes, execution ->
                if (failObservationReads && Thread.currentThread().name == "paper-reprocess-audit" &&
                    request.method == org.springframework.http.HttpMethod.GET && request.uri.path.startsWith("/raw/_doc/"))
                    throw org.springframework.web.client.HttpServerErrorException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                execution.execute(request, bytes)
            })
            val indices = ExpertIndexService(props, esHttp, mapper)
            writer = ExpertIndexWriterService(esHttp, props, indices, mapper,
                Mockito.mock(ExpertPromotionAuditService::class.java), contacts)
            review = DiscoveryReviewService(f.admissions, f.policy, writer, esHttp, props, indices, mapper, f.clock, jdbcTemplate = jdbc)
            traces = proxy(DiscoveryVerificationTraceRepository(jdbc))
            commits = proxy(DiscoveryVerificationCommitService(f.repository, f.admissions, review, f.policy, writer, jdbc, f.clock, traces))
            archiveRepo = DiscoveryPaperArchiveRepository(jdbc, manager)
            archive = DiscoveryPaperArchiveService(archiveRepo,
                DiscoveryPaperArchiveProperties(root.resolve("archive").toString()),
                DiscoverySampleProperties(directory = root.resolve("samples").toString()), mapper)
            analyzer = DiscoveryHistoricalReplayTool.analyzer()
            papers = PaperVerificationEvidenceService(archiveRepo, archive, analyzer, mapper)
            archive.updateSettings(ArchiveSettingsRequest(archive.settings().revision, true,
                archive.settings().maxBytes, archive.settings().minFreeBytes))
            persons = DiscoveryPersonRegistrationService(writer,
                DiscoveryPersonService(proxy(DiscoveryPersonRepository(jdbc)), writer, contacts, aliases, f.clock),
                proxy(DiscoveryPersonRepository(jdbc)), DiscoveryPersonFeatureGate(true))
            retained = RetainedPaperIdentityService(archive, archiveRepo, analyzer, papers, writer,
                DiscoveryIdentityRepairService(writer, persons), contacts, aliases, f.admissions, f.policy)
            queries = DiscoveryVerificationQueryService(f.repository, f.admissions, f.props)
            tasks = context.getBean(TaskExecutionRepository::class.java)
            repository = DiscoveryPaperReprocessRepository(jdbc, manager, mapper, tasks)
            registration = DiscoveryVerificationRegistrationService(f.props, f.repository, writer, f.policy,
                f.admissions, jdbc, TransactionTemplate(manager), f.apolloSettings, f.clock, traces = traces,
                papers = papers, paperReprocess = repository)
            Mockito.doAnswer { invocation ->
                val email = invocation.getArgument<String>(0)
                if (email == p1.email) ExpertContact(campaignId = 1, orcidId = p1.doc, expertEmail = email,
                    expertName = "Pia Contact") else null
            }.`when`(contacts).findFirstByExpertEmailOrderByUpdatedAtDesc(Mockito.anyString())
            Mockito.doReturn(false).`when`(contacts).existsByOrcidId(Mockito.anyString())
            Mockito.doReturn(null).`when`(aliases).findContactByEmailOrAlias(Mockito.anyString())

            val providerHttp = RestTemplate().apply {
                interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { request, _, _ ->
                    providerUris += request.uri.toString()
                    val body = if (request.uri.host == "api.apollo.io") {
                        check(request.uri.path.endsWith("api_search")) { "Paid detail dispatch forbidden: ${request.uri}" }
                        """{"people":[],"total_entries":0}"""
                    } else {
                        check(request.uri.host == "pub.orcid.org") { "Unexpected provider host: ${request.uri}" }
                        if (request.uri.path.contains(PROFESSOR_ORCID)) {
                            f.selfDocuments().firstOrNull { request.uri.path.endsWith("/${it.endpoint}") }
                                ?.document?.body?.toString() ?: "{}"
                        } else "{}"
                    }
                    org.springframework.mock.http.client.MockClientHttpResponse(
                        body.toByteArray(), org.springframework.http.HttpStatus.OK).apply {
                        headers.contentType = MediaType.APPLICATION_JSON }
                })
            }
            val beans = DefaultListableBeanFactory()
            worker = DiscoveryVerificationWorker(f.props, f.repository,
                OrcidVerificationEvidenceClient(f.props, providerHttp, f.clock),
                ApolloPeopleSearchClient(f.props, providerHttp, f.clock), QualificationEvidenceParser(f.clock),
                f.rules, commits,
                DefaultListableBeanFactory().apply { registerSingleton("paperRecovery", registration) }
                    .getBeanProvider(RegistrationRecovery::class.java), f.clock, traces, papers, retainedIdentity = retained)
            beans.registerSingleton("paperE2eWorker", worker)
            rechecks = DiscoveryRecheckService(f.repository, f.admissions, writer,
                DiscoveryReviewScanService(f.admissions, f.policy, writer, RestTemplate(), props,
                    ExpertIndexService(props, RestTemplate(), mapper), mapper), f.policy, f.apolloSettings, f.props,
                tasks, TransactionTemplate(manager), queries, commits, Executor { it.run() },
                beans.getBeanProvider(DiscoveryVerificationWorker::class.java), papers, f.clock)
            val validation = Mockito.mock(EmailValidationService::class.java)
            Mockito.doReturn(EmailValidationResult(3, true)).`when`(validation).validate(Mockito.anyString())
            consumer = ExpertDiscoveryService(Mockito.mock(EuropePmcDataSource::class.java),
                beans.getBeanProvider(OpenAlexDataSource::class.java), beans.getBeanProvider(CrossrefDataSource::class.java),
                beans.getBeanProvider(ArxivDataSource::class.java), beans.getBeanProvider(PmcOaDataSource::class.java),
                beans.getBeanProvider(OrcidDataSource::class.java), beans.getBeanProvider(CoreDataSource::class.java),
                validation, f.eligibility, writer, indices, Mockito.mock(ExpertRevalidationService::class.java),
                Mockito.mock(ExpertSearchService::class.java), ExpertClassificationService(), RestTemplate(), props,
                ExpertDiscoveryProperties(enabled = true, includeRawScan = false, maxAuthorsPerRun = 500),
                OpenAlexProperties(enabled = false), mapper, Mockito.mock(TaskProgressStore::class.java),
                Mockito.mock(DiscoverySourceCursorRepository::class.java),
                proxy(ExpertAcademicEnrichmentJobService(context.getBean(ExpertAcademicEnrichmentJobRepository::class.java))),
                context.getBean(ExpertAcademicEnrichmentJobRepository::class.java), Executor { it.run() },
                EuropePmcProperties(enabled = false), discoveryReviewService = review,
                personRegistrationService = persons, verificationRegistration = registration)
            apply = DiscoveryPaperReprocessApplyService(repository, f.repository, registration, consumer, rechecks, writer,
                f.admissions, archive, analyzer, mapper, jdbc, beans.getBeanProvider(DiscoveryVerificationWorker::class.java))
            service = DiscoveryPaperReprocessService(repository, archive, analyzer, mapper, f.apolloSettings,
                Mockito.mock(TaskProgressStore::class.java), apply)
            apply.afterEffect = { point ->
                val cancel = (point == "AFTER_CONSUME" && cancelOnFirstConsume) || (point == "AFTER_START" && cancelOnFirstStart)
                if (cancel && !cancelTriggered) {
                    cancelTriggered = true
                    val id = cancelRunId!!
                    service.cancel(id, repository.find(id)!!.revision)
                }
            }
            service.beforeParse = { item ->
                if (item.input.path("paperKey").asText() == trigger.key) {
                    jdbc.update("UPDATE discovery_paper_archive_attempt SET status='DELETED' WHERE id=?", m.attemptId)
                }
            }
            f.saveApolloSettings(true, false)
            jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at)
                VALUES (?, 'EXPERT_DISCOVERY','MANUAL','SUCCESS',NOW(),NOW())""", SOURCE)
            jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at)
                VALUES (?, 'EXPERT_DISCOVERY','MANUAL','SUCCESS',NOW(),NOW())""", CANCEL_SOURCE)
            jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at)
                VALUES (?, 'EXPERT_DISCOVERY','MANUAL','SUCCESS',NOW(),NOW())""", RECOVERY_SOURCE)
            jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at)
                VALUES (?, 'EXPERT_DISCOVERY','MANUAL','SUCCESS',NOW(),NOW())""", CHILD_SOURCE)
        }

        private fun <T : Any> proxy(value: T): T {
            @Suppress("UNCHECKED_CAST")
            return ProxyFactory(value).apply {
                isProxyTargetClass = true
                addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
            }.proxy as T
        }

        fun seedMainSource() {
            seed(SOURCE, n1, n2, e1, e2, c1, c2, p1, p2, x1a, x1b, x2a, x2b, m, trigger, w)
            seedRaw(c1, complete = true, orcid = null)
            seedRaw(c2, complete = true, orcid = null)
            seedRaw(e1, complete = false, orcid = PROFESSOR_ORCID)
            seedRaw(e2, complete = false, orcid = OTHER_ORCID)
            seedRaw(p1, complete = true, orcid = PROFESSOR_ORCID)
            seedRaw(p2, complete = true, orcid = PROFESSOR_ORCID)
            jdbc.update("""INSERT INTO expert_contact (campaign_id,orcid_id,expert_email,expert_name)
                VALUES (1,?,?,?)""", p1.doc, p1.email, "Pia Contact")
            jdbc.update("""INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,policy_version,updated_at)
                VALUES (?,?,'HOLD',1,?,NOW(3))""", p2.doc, "a".repeat(64), DiscoveryVerificationAdmissionGate.POLICY_VERSION)
        }

        fun seedCancelSource() {
            seed(CANCEL_SOURCE, cancelPaper)
            cancelPaper.doc = docId(cancelPaper)
            cancelDoc = cancelPaper.doc
            cancelRunId = null
        }
        var cancelDoc: String = ""

        fun seedRecoverySource() {
            seed(RECOVERY_SOURCE, recoveryFirst, recoverySecond)
        }

        fun seedExistingAuthorSource() {
            seed(CHILD_SOURCE, existingAuthor)
            seedRaw(existingAuthor, complete = true, orcid = null)
        }

        fun seedBatchedSource() {
            seedExistingAuthorSource()
            repeat(100) { index ->
                // No RAW/provider work needed for these unbound contacts; they still are distinct TARGETs.
                addPaper(CHILD_SOURCE, Paper("unbound-$index", "unbound$index@fixture.edu", "", "", null))
            }
        }

        fun seedPartialPdfSource(): Paper {
            val paper = Paper("partial-pdf", "alice@fixture.edu", "Alice", "Smith", null)
            val bytes = QualificationFixtures.pdf(4, listOf("Alice Smith: alice@fixture.edu"))
            addPaper(RECOVERY_SOURCE, paper, bytes)
            return paper
        }

        private fun seed(sourceId: Long, vararg papers: Paper) {
            papers.forEach { addPaper(sourceId, it) }
        }

        private fun addPaper(sourceId: Long, paper: Paper, pdf: ByteArray? = null) {
            val metadata = PaperMetadata(null, null, "10.fixture/${paper.key}", "Isolated paper ${paper.key}", 2026, null,
                listOf(PaperAuthor(paper.given, paper.family, paper.orcid, "Example University", true)), "CROSSREF")
            val orcid = paper.orcid?.let {
                """<contrib-id contrib-id-type="orcid">$it</contrib-id>"""
            } ?: ""
            val email = paper.email?.let { "<email>$it</email>" } ?: ""
            val xml = """<article><front><article-meta><contrib-group><contrib contrib-type="author">
                <name><surname>${paper.family}</surname><given-names>${paper.given}</given-names></name>
                $orcid $email<xref ref-type="aff" rid="a1"/></contrib></contrib-group>
                <aff id="a1"><institution content-type="university">Example University</institution>
                <country>${paper.country}</country></aff></article-meta></front><body>${paper.facts}</body></article>""".toByteArray()
            val record = archive.beginRecord(sourceId, "CROSSREF", paper.key,
                mapper.valueToTree<ObjectNode>(metadata).put("schemaVersion", 1))
            val attempt = archive.beginAttempt(record.id, UUID.randomUUID(), 1,
                if (pdf == null) ArchiveAttemptKind.PMC_XML else ArchiveAttemptKind.PDF_URL, "https://fixture.invalid/${paper.key}")
            archive.saveBody(attempt.id, pdf ?: xml, if (pdf == null) "application/xml" else "application/pdf")
            archive.finishExtraction(record.id, mapper.createObjectNode().put("schemaVersion", 1))
            paper.recordId = record.id; paper.attemptId = attempt.id
            paper.bodySha = DiscoveryPaperArchiveService.sha(pdf ?: xml)
            paper.doc = docId(paper)
        }

        private fun docId(paper: Paper): String = paper.email?.let {
            com.weibo.talentintroduction.expert.service.ExpertIdGenerator.generate(null, it)
        } ?: "no-email-${paper.key}"

        private fun seedRaw(paper: Paper, complete: Boolean, orcid: String?) {
            val email = requireNotNull(paper.email)
            val proof = if (complete) DiscoveryIdentity.verified(email, paper.given, paper.family,
                "SOURCE_SHA256:" + "b".repeat(64), orcid, null) else null
            // The seeded complete profiles deliberately carry no ORCID, so provider qualification
            // remains insufficient and only the two designated targets can be admitted.
            val raw = linkedMapOf<String, Any?>(
                "givenNames" to if (complete) paper.given else "",
                "familyNames" to if (complete) paper.family else "",
                "email" to email,
                "emailSource" to "PAPER_FULLTEXT",
                "dataSource" to "CROSSREF",
                "tags" to listOf("discovered"),
                "institution" to "Example University",
                "country" to "United Kingdom",
                "lastPublicationYear" to 2026,
                "externalIds" to mapOf("doi" to "10.fixture/${paper.key}")
            )
            orcid?.let { raw["orcidId"] = it }
            proof?.let { raw["identityVerification"] = mapper.convertValue(it, Map::class.java) }
            docs.getValue("raw")[paper.doc] = raw
            versions["raw" to paper.doc] = 1
        }

        fun create(sourceId: Long): ReprocessRun = service.create(CreateReprocessRequest(
            UUID.randomUUID().toString(), sourceId, ReprocessMode.ENFORCE, service.preview(sourceId).expectedScopeHash))

        fun pump(id: Long, expected: ReprocessState) {
            if (cancelOnFirstConsume || cancelOnFirstStart) cancelRunId = id
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300)
            while (true) {
                service.poll()
                val lease = f.repository.claimDue(f.clock.instant(), 120)
                if (lease != null) {
                    check(f.repository.isPaperReprocessOwned(lease.ownerExecutionId)) { "Foreign claim in isolated runtime" }
                    worker.process(lease)
                }
                val run = repository.find(id)
                if (run != null && run.state !in setOf(ReprocessState.PREPARING, ReprocessState.RUNNING, ReprocessState.CANCELLING)) break
                check(System.nanoTime() < deadline) { "Reprocess did not finish: $run" }
                Thread.sleep(10)
            }
            check(repository.find(id)!!.state == expected) { repository.find(id).toString() }
        }

        fun targets(id: Long): List<ReprocessItem> = repository.items(id, ReprocessKind.TARGET, 0, 100).items

        fun target(id: Long, email: String): ReprocessItem = targets(id).single { it.input.path("email").asText() == email }

        fun verificationItem(doc: String): VerificationItem? = f.repository.findItemsByDocIds(listOf(doc))
            .filter { it.reviewEpoch == 0L }.maxByOrNull { it.id }

        private fun outcome(id: Long, paper: Paper): JsonNode = target(id, requireNotNull(paper.email)).result!!

        fun assertFixedResults(id: Long) {
            val n1r = outcome(id, n1); val n2r = outcome(id, n2)
            assertEquals("NEW", n1r.path("targetOutcome").asText()); assertEquals(1, n1r.path("actualNew").asInt())
            assertEquals("NEW", n2r.path("targetOutcome").asText()); assertEquals(1, n2r.path("actualNew").asInt())
            assertEquals(1, n1r.path("actualAdmitted").asInt()); assertEquals(0, n2r.path("actualAdmitted").asInt())
            val e1r = outcome(id, e1); val e2r = outcome(id, e2)
            assertEquals("REPAIRED", e1r.path("targetOutcome").asText()); assertEquals(1, e1r.path("actualUpdated").asInt())
            assertEquals("REPAIRED", e2r.path("targetOutcome").asText()); assertEquals(1, e2r.path("actualUpdated").asInt())
            assertEquals(1, e1r.path("actualAdmitted").asInt()); assertEquals(0, e2r.path("actualAdmitted").asInt())
            for (paper in listOf(c1, c2)) {
                val result = outcome(id, paper)
                assertEquals("UNCHANGED", result.path("targetOutcome").asText())
                assertEquals(0, result.path("actualNew").asInt())
                assertEquals(0, result.path("actualUpdated").asInt())
                assertEquals(0, result.path("actualAdmitted").asInt())
            }
            for (paper in listOf(x1a, x2a)) assertEquals("CONFLICT", outcome(id, paper).path("targetOutcome").asText())
            for (paper in listOf(p1, p2)) assertEquals("PROTECTED", outcome(id, paper).path("targetOutcome").asText())
            val missing = outcome(id, m)
            assertEquals("ERROR", missing.path("targetOutcome").asText())
            assertEquals("SOURCE_MISSING", missing.path("reasonCode").asText())
            assertEquals(0, missing.path("actualAdmitted").asInt())
            val write = outcome(id, w)
            assertEquals("ERROR", write.path("targetOutcome").asText())
            assertTrue(write.path("applicationError").asBoolean())
            assertEquals(0, write.path("actualNew").asInt())
            assertEquals(0, write.path("actualAdmitted").asInt())
            assertEquals(12, targets(id).size)
        }

        fun terminalReceipts(id: Long): Map<Long, String> = targets(id).filter {
            it.state in setOf(ReprocessItemState.COMPLETE, ReprocessItemState.SKIPPED)
        }.associate { it.id to it.result.toString() }

        fun admissionRows(): List<Map<String, Any?>> =
            jdbc.queryForList("SELECT expert_doc_id,decision,revision FROM expert_discovery_admission ORDER BY expert_doc_id")

        fun readRaw(doc: String): Map<String, Any?>? = docs.getValue("raw")[doc]

        fun reportRun(id: Long, label: String) {
            val run = repository.find(id)!!
            val summary = repository.summary(id)
            val targets = targets(id).sortedBy { it.input.path("email").asText() }
            targets.forEach { item ->
                val result = item.result
                println("PAPER06_TARGET run=$id label=$label email=${item.input.path("email").asText()} " +
                    "state=${item.state} outcome=${result?.path("targetOutcome")?.asText()} " +
                    "new=${result?.path("actualNew")?.asInt() ?: 0} updated=${result?.path("actualUpdated")?.asInt() ?: 0} " +
                    "admitted=${result?.path("actualAdmitted")?.asInt() ?: 0} already=${result?.path("alreadyAdmitted")?.asInt() ?: 0} " +
                    "reason=${result?.path("reasonCode")?.asText() ?: result?.path("reason")?.asText()}")
            }
            println("PAPER06_SUMMARY run=$id label=$label state=${run.state} papers=${summary.papers} " +
                "targets=${summary.targets} new=${summary.actualNew} updated=${summary.actualUpdated} " +
                "admitted=${summary.actualAdmitted} conflict=${summary.conflict} protected=${summary.protected} " +
                "errors=${summary.targetErrors} sync=${summary.syncFailure} met=${summary.qualification.met} " +
                "unknown=${summary.qualification.unknown} notMet=${summary.qualification.notMet} " +
                "notEvaluated=${summary.qualification.notEvaluated} unavailable=${summary.qualification.unavailable}")
            val root = mapper.createObjectNode().put("schemaVersion", 1)
            root.putObject("run").put("executionId", id).put("sourceExecutionId", run.sourceExecutionId)
                .put("mode", run.mode.name).put("state", run.state.name)
                .put("scopeHash", run.scope.path("scopeHash").asText(""))
            val array = root.putArray("targets")
            targets.forEach { item ->
                val target = array.addObject().put("email", item.input.path("email").asText())
                item.result?.let { result ->
                    target.put("targetOutcome", result.path("targetOutcome").asText(null))
                    target.put("docId", result.path("docId").asText(null))
                    target.put("qualification", result.path("qualification").asText(null))
                    target.put("reasonCode", result.path("reasonCode").asText(null))
                    target.put("actualNew", result.path("actualNew").asInt())
                    target.put("actualUpdated", result.path("actualUpdated").asInt())
                    target.put("actualAdmitted", result.path("actualAdmitted").asInt())
                    target.put("alreadyAdmitted", result.path("alreadyAdmitted").asInt())
                }
                val candidates = target.putArray("candidates")
                item.input.path("paperRefs").forEach { ref ->
                    candidates.addObject().put("recordId", ref.path("recordId").asLong())
                        .put("bodySha256", ref.path("bodyHash").asText())
                        .put("emailHash", sha256(item.input.path("email").asText().trim().lowercase()))
                        .put("nameHash", sha256(ref.path("givenNames").asText() + "|" + ref.path("familyNames").asText()))
                }
            }
            Files.write(Files.createDirectories(java.nio.file.Paths.get("target", "paper-reprocess-e2e"))
                .resolve("run-$id-$label.json"), mapper.writeValueAsBytes(root))
        }

        private fun sha256(value: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun restoreMissingSource() {
            jdbc.update("UPDATE discovery_paper_archive_attempt SET status='SAVED' WHERE id=?", m.attemptId)
        }

        override fun close() {
            service.shutdown(); worker.close(); context.close(); server.stop(); esExecutor.shutdownNow(); f.close()
        }
    }

    /** Strict loopback ES: only the routes the existing writer/repair/projection seams invoke. */
    private class ESFixture(private val h: Harness) {
        private val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port: Int get() = server.address.port
        val rawWrites: Int get() = h.rawWrites
        fun start() {
            server.createContext("/") { exchange ->
                var status = 200
                val path = exchange.requestURI.path.trim('/').split('/')
                val body = exchange.requestBody.use { it.readBytes() }.takeIf { it.isNotEmpty() }?.let { h.mapper.readTree(it) }
                val answer: Any = if (path.last() == "_search") {
                    val email = body!!.path("query").path("term").path("email").asText()
                    val hits = h.docs.flatMap { (index, values) -> values.filterValues { it["email"] == email }.map { (id, source) ->
                        mapOf("_index" to index, "_id" to id, "_source" to source) } }
                    mapOf("timed_out" to false, "_shards" to mapOf("failed" to 0),
                        "hits" to mapOf("total" to mapOf("value" to hits.size), "hits" to hits))
                } else {
                    check(path.size == 3 && path[0] in h.docs && path[1] in listOf("_doc", "_update")) {
                        "Unexpected ES route ${exchange.requestURI}"
                    }
                    val index = path[0]; val id = path[2]; val key = index to id
                    val current = h.docs.getValue(index)[id]
                    when (exchange.requestMethod) {
                        "GET", "HEAD" -> if (current == null) {
                            status = 404; mapOf("found" to false)
                        } else mapOf("_source" to current, "_seq_no" to h.versions.getOrPut(key) { 1L }, "_primary_term" to 1)
                        "POST" -> {
                            check(path[1] == "_update")
                            val expected = exchange.requestURI.rawQuery.orEmpty().split('&')
                                .associate { it.substringBefore('=') to it.substringAfter('=') }
                            if (current == null) { status = 404; emptyMap<String, Any>() }
                            else if (expected["if_seq_no"]?.toLong() != h.versions.getOrPut(key) { 1L } || expected["if_primary_term"] != "1") {
                                status = 409; emptyMap<String, Any>()
                            } else {
                                @Suppress("UNCHECKED_CAST") val patch = h.mapper.convertValue(body!!.path("doc"), Map::class.java) as Map<String, Any?>
                                current.putAll(patch); h.versions[key] = h.versions.getValue(key) + 1
                                mapOf("result" to "updated", "_seq_no" to h.versions[key], "_primary_term" to 1)
                            }
                        }
                        "PUT" -> {
                            if (index == "raw" && id in h.failRawDocs ||
                                index == "candidate" && id in h.failCandidateDocs) { status = 503; emptyMap<String, Any>() }
                            else {
                                @Suppress("UNCHECKED_CAST") val value = h.mapper.convertValue(body, Map::class.java) as Map<String, Any?>
                                h.docs.getValue(index)[id] = value.toMutableMap(); h.versions[key] = 1
                                if (index == "raw") h.rawWrites++
                                mapOf("result" to "created")
                            }
                        }
                        else -> error("Unexpected ES method ${exchange.requestMethod}")
                    }
                }
                val bytes = h.mapper.writeValueAsBytes(answer)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, if (exchange.requestMethod == "HEAD") -1 else bytes.size.toLong())
                exchange.responseBody.use { if (exchange.requestMethod != "HEAD") it.write(bytes) }
            }
            server.executor = h.esExecutor
            server.start()
        }
        fun stop() = server.stop(0)
    }
}
