package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.archive.ArchiveAttemptKind
import com.weibo.talentintroduction.discovery.archive.ArchiveRecord
import com.weibo.talentintroduction.discovery.archive.DiscoveryPaperArchiveRepository
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.sql.DataSource

@TestConfiguration
@EnableJdbcRepositories(basePackages = ["com.weibo.talentintroduction.task.repository"])
internal class ReprocessJdbcTestConfig : AbstractJdbcConfiguration() {
    @Bean fun namedParameterJdbcOperations(source: DataSource): NamedParameterJdbcOperations = NamedParameterJdbcTemplate(source)
    @Bean fun transactionManager(source: DataSource) = DataSourceTransactionManager(source)
}

/** Shared only by disposable tests/runtime launchers; no application/provider configuration is loaded. */
internal object ReprocessMysqlFixture {
    private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
    private val mysql by lazy {
        check(DockerClientFactory.instance().isDockerAvailable) { "Docker MySQL is required; missing infrastructure is not a skipped PASS" }
        Mysql().withDatabaseName("paper_reprocess_child03").withUsername("test").withPassword("test")
            .withCommand("--max-allowed-packet=16777216").also { it.start() }
    }
    val dataSource by lazy { DriverManagerDataSource(mysql.jdbcUrl, mysql.username, mysql.password).also { source ->
        ResourceDatabasePopulator(ClassPathResource("db/migration/V4__create_task_execution.sql"),
            ClassPathResource("db/migration/V137__task_execution_interruption_recovery.sql"),
            ClassPathResource("db/migration/V158__create_discovery_verification.sql"),
            ClassPathResource("db/migration/V159__create_discovery_paper_archive.sql"),
            ClassPathResource("db/migration/V161__discovery_recheck_history.sql"),
            ClassPathResource("db/migration/V163__paper_archive_lookup_indexes.sql"),
            ClassPathResource("db/migration/V173__create_discovery_paper_reprocess.sql")).execute(source)
        // V73's nullable task mapping column; the batch configuration subsystem is not bootstrapped.
        JdbcTemplate(source).execute("ALTER TABLE task_execution ADD COLUMN batch_config_id BIGINT NULL")
    } }
    val jdbc by lazy { JdbcTemplate(dataSource) }
    val manager by lazy { DataSourceTransactionManager(dataSource) }
    val mapper = jacksonObjectMapper().findAndRegisterModules()
    private val context by lazy { AnnotationConfigApplicationContext().also {
        it.registerBean("dataSource", DataSource::class.java, Supplier { dataSource })
        it.register(ReprocessJdbcTestConfig::class.java)
        it.refresh()
    } }
    val tasks: TaskExecutionRepository get() = context.getBean(TaskExecutionRepository::class.java)
    fun repository() = DiscoveryPaperReprocessRepository(jdbc, manager, mapper, tasks)
    fun archive() = DiscoveryPaperArchiveRepository(jdbc, manager)
    fun clean() {
        jdbc.update("DELETE FROM discovery_verification_attempt")
        jdbc.update("DELETE FROM discovery_verification_item")
        jdbc.update("DELETE FROM discovery_verification_run")
        jdbc.update("DELETE FROM discovery_paper_reprocess_item")
        jdbc.update("DELETE FROM discovery_paper_reprocess_run")
        jdbc.update("DELETE FROM discovery_paper_archive_attempt")
        jdbc.update("DELETE FROM discovery_paper_archive_blob")
        jdbc.update("DELETE FROM discovery_paper_archive_record")
        jdbc.update("DELETE FROM discovery_paper_archive_setting")
        jdbc.update("DELETE FROM task_execution")
    }
    fun task(id: Long = 9001, status: String = "SUCCESS"): Long {
        val time = LocalDateTime.ofInstant(Instant.parse("2026-11-01T01:02:03.456Z"), ZoneOffset.UTC)
        jdbc.update("INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at,created_at,updated_at) VALUES (?,'EXPERT_DISCOVERY','MANUAL',?,?,?,?,?)",
            id, status, time, if (status in setOf("RUNNING", "CANCELLING")) null else time, time, time)
        return id
    }
    fun seedPapers(sourceId: Long, count: Int = 121): List<ArchiveRecord> {
        val archive = archive(); val time = Instant.parse("2026-11-01T01:02:03.456Z")
        return (1..count).map { index ->
            val record = archive.beginRecord(sourceId, "OPENALEX", "fixture-$index",
                """{"schemaVersion":1,"title":"Fixture paper $index","authors":["Fixture Author"]}""", time)
            val blobHash = hash(if (index <= 3) "shared-body" else "body-$index")
            val first = archive.beginAttempt(record.id, hash("attempt-$sourceId-$index"), ArchiveAttemptKind.EMBEDDED_TEXT, null, time)
            archive.saveBody(first.id, blobHash, 128, "text/plain", index > 1 && index <= 3, time)
            if (index == 1) {
                val duplicate = archive.beginAttempt(record.id, hash("duplicate-$sourceId"), ArchiveAttemptKind.EMBEDDED_TEXT, null, time)
                archive.saveBody(duplicate.id, blobHash, 128, "text/plain", true, time)
            }
            if (index == 2) {
                val extra = archive.beginAttempt(record.id, hash("extra-$sourceId"), ArchiveAttemptKind.PMC_XML, null, time)
                archive.saveBody(extra.id, hash("xml-extra"), 256, "application/xml", false, time)
            }
            archive.finishExtraction(record.id, """{"schemaVersion":1,"first":{"schemaVersion":1,"attemptIds":[${first.id}],"bindings":[]}}""", time)
            if (index == 3) archive.protect(record.id, archive.record(record.id)!!.revision, true, time)
            archive.record(record.id)!!
        }
    }
    fun scope(sourceId: Long): ObjectNode {
        val mark = repository().watermark(sourceId)
        return mapper.createObjectNode().put("schemaVersion", 1).put("scopeHash", mark.scopeHash)
            .put("upperRecordId", mark.upperRecordId).put("maxAttemptId", mark.maxAttemptId)
            .put("cursor", 0).put("frozenCount", 0).put("frozen", false).put("codeVersion", "fixture-code-v1")
            .put("evaluationAt", "2026-11-01T01:02:03.456Z").also {
                it.putObject("parserVersions").put("binding", "fixture-parser-v1")
                it.putObject("apollo").put("enabled", false)
            }
    }
    fun request(sourceId: Long, scope: JsonNode, key: String = UUID.randomUUID().toString()) =
        CreateReprocessRequest(key, sourceId, ReprocessMode.AUDIT, scope.path("scopeHash").asText())
    fun create(sourceId: Long, repository: DiscoveryPaperReprocessRepository = repository()): ReprocessRun {
        val scope = scope(sourceId); val request = request(sourceId, scope)
        return repository.create(request, hash(mapper.writeValueAsString(request)), scope, "fixture-owner")
    }
    fun result(reason: String? = null): ObjectNode = mapper.createObjectNode().put("schemaVersion", 1).also { result ->
        reason?.let { result.put("reasonCode", it) }
        val trace = result.putArray("trace")
        (1..6).forEach { trace.addObject().put("stepNo", it).put("state", if (reason == null) "SUCCESS" else "ERROR") }
    }
    fun versions(scope: JsonNode): ObjectNode = mapper.createObjectNode().also {
        it.set<JsonNode>("parserVersions", scope.get("parserVersions")); it.set<JsonNode>("codeVersion", scope.get("codeVersion"))
    }
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun stop() { context.close(); if (mysql.isRunning) mysql.stop() }
}

@EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
class DiscoveryPaperReprocessRepositoryIntegrationTest {
    private val f = ReprocessMysqlFixture
    private val repository get() = f.repository()
    @BeforeEach fun clean() = f.clean()

    @Test fun `review groups persist across process recovery and late ready targets do not shift old membership`() {
        val source = f.task(); f.seedPapers(source, 1)
        val run = f.create(source)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET mode='ENFORCE' WHERE execution_id=?", run.executionId)
        val owner = repository.claimRun("worker")!!
        repository.freezePage(run.executionId, owner.leaseToken!!, owner.generation)
        repository.setPhase(run.executionId, owner.leaseToken!!, owner.generation, PaperReprocessPhase.APPLY)
        val parent = repository.find(run.executionId)!!
        assertTrue(repository.stagedReviews(parent))
        val first = repository.freezeReviewGroups(parent, (1..101).map { "doc-$it" })
        assertEquals(listOf(100, 1), first.map { it.size })
        val resumed = f.repository()
        assertTrue(resumed.stagedReviews(parent))
        assertEquals(first, resumed.freezeReviewGroups(parent, (1..101).reversed().map { "doc-$it" }))
        val extra = resumed.freezeReviewGroups(parent, listOf("a-late-document"))
        assertEquals(listOf(listOf("a-late-document")), extra)
        assertEquals(first + extra, resumed.find(run.executionId)!!.scope.path("reviewGroups").map { g -> g.map { it.asText() } })
    }

    @Test fun `V173 actually enforces enums singleton indexes and UTC millisecond storage in MySQL8`() {
        val version = f.jdbc.queryForObject("SELECT VERSION()", String::class.java)!!
        assertTrue(version.startsWith("8."), version)
        val source = f.task(); f.seedPapers(source, 1)
        val run = f.create(source)
        val enforced = f.jdbc.queryForObject("""SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema=DATABASE() AND constraint_type='CHECK' AND enforced='YES'
            AND table_name IN ('discovery_paper_reprocess_run','discovery_paper_reprocess_item')""", Long::class.java)!!
        assertEquals(11L, enforced)
        listOf("mode='UNKNOWN'", "state='UNKNOWN'", "phase='UNKNOWN'", "active_key='OTHER'", "active_key=NULL", "revision=-1", "scope_json=REPEAT('x',4194305)").forEach {
            assertThrows(Exception::class.java) { f.jdbc.update("UPDATE discovery_paper_reprocess_run SET $it WHERE execution_id=?", run.executionId) }
        }
        assertThrows(Exception::class.java) { f.jdbc.update("INSERT INTO discovery_paper_reprocess_run SELECT execution_id+1,source_execution_id,UUID(),request_hash,active_key,mode,state,phase,scope_json,revision,lease_token,lease_until,generation,cancel_requested,created_at,updated_at,finished_at FROM discovery_paper_reprocess_run WHERE execution_id=?", run.executionId) }
        assertThrows(Exception::class.java) { f.jdbc.update("INSERT INTO discovery_paper_reprocess_run SELECT execution_id+1,source_execution_id,request_key,request_hash,NULL,mode,'FAILED',phase,scope_json,revision,lease_token,lease_until,generation,cancel_requested,created_at,updated_at,finished_at FROM discovery_paper_reprocess_run WHERE execution_id=?", run.executionId) }
        val claimed = repository.claimRun("fixture-owner")!!
        assertTrue(repository.freezePage(run.executionId, claimed.leaseToken!!, claimed.generation))
        val item = repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items.single()
        listOf("kind='OTHER'", "state='UNKNOWN'", "generation=-1", "input_json=REPEAT('x',4194305)", "result_json=REPEAT('x',4194305)").forEach {
            assertThrows(Exception::class.java) { f.jdbc.update("UPDATE discovery_paper_reprocess_item SET $it WHERE id=?", item.id) }
        }
        val stamp = LocalDateTime.of(2026, 11, 1, 8, 9, 10, 123_000_000)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET updated_at=? WHERE execution_id=?", stamp, run.executionId)
        assertEquals(Instant.parse("2026-11-01T08:09:10.123Z"), repository.find(run.executionId)!!.updatedAt)
        val indexes = f.jdbc.queryForList("SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='discovery_paper_reprocess_item'", String::class.java)
        assertTrue(indexes.containsAll(listOf("uk_paper_reprocess_item", "idx_paper_reprocess_item_run", "idx_paper_reprocess_item_lease")))
        assertEquals(0L, f.jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name IN ('discovery_paper_reprocess_run','discovery_paper_reprocess_item')", Long::class.java))
    }

    @Test fun `121 records freeze in keyset100 restart and remain seven API sized pages with duplicate refs`() {
        val source = f.task(); val records = f.seedPapers(source)
        val before = f.jdbc.queryForList("SELECT id,revision,metadata_json,outcome_json,protected FROM discovery_paper_archive_record ORDER BY id")
        val run = f.create(source); var claimed = repository.claimRun("worker-a")!!
        assertFalse(repository.freezePage(run.executionId, claimed.leaseToken!!, claimed.generation))
        assertEquals(100L, repository.summary(run.executionId).papers)
        assertEquals(records[99].id, repository.find(run.executionId)!!.scope.path("cursor").asLong())
        assertNull(repository.claimItem(run.executionId, claimed.leaseToken!!, claimed.generation, ReprocessKind.PAPER))
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE execution_id=?", run.executionId)
        val old = claimed
        claimed = f.repository().claimRun("restarted-worker")!!
        assertTrue(claimed.generation > old.generation)
        assertFalse(repository.freezePage(run.executionId, old.leaseToken!!, old.generation))
        assertTrue(f.repository().freezePage(run.executionId, claimed.leaseToken!!, claimed.generation))
        val frozen = repository.find(run.executionId)!!
        assertTrue(frozen.scope.path("frozen").asBoolean())
        assertTrue(frozen.scope.path("manifestHash").asText().matches(Regex("[0-9a-f]{64}")))
        assertEquals(121L, frozen.scope.path("frozenCount").asLong())
        val pages = (0..6).map { repository.items(run.executionId, ReprocessKind.PAPER, it, 20) }
        assertEquals(listOf(20, 20, 20, 20, 20, 20, 1), pages.map { it.items.size })
        assertTrue(pages.all { it.total == 121L })
        val all = pages.flatMap { it.items }
        assertEquals(records.map { it.id }, all.map { it.input.path("recordId").asLong() })
        assertTrue(all.all { it.input.path("sourceExecutionId").asLong() == source && it.input.path("sourceName").asText() == "OPENALEX" && it.input.path("paperKey").asText().startsWith("fixture-") })
        assertEquals(frozen.scope.path("manifestHash"), frozen.scope.path("sourcesHash"))
        assertNotEquals(frozen.scope.path("scopeHash"), frozen.scope.path("manifestHash"))
        val firstBodies = all[0].input.path("bodies")
        assertEquals(2, firstBodies.size())
        assertEquals(1, firstBodies.map { it.path("hash").asText() }.distinct().size)
        assertEquals(2, firstBodies.map { it.path("attemptId").asLong() }.distinct().size)
        assertEquals(2, all[1].input.path("bodies").size())
        assertEquals(2, all[1].input.path("bodies").map { it.path("hash").asText() }.distinct().size)
        assertEquals(3, all.take(3).count { it.input.path("bodies")[0].path("hash") == firstBodies[0].path("hash") })
        assertEquals(1, all[0].input.path("first").path("attemptIds").size())
        assertEquals(before, f.jdbc.queryForList("SELECT id,revision,metadata_json,outcome_json,protected FROM discovery_paper_archive_record ORDER BY id"))
        val summary = repository.summary(run.executionId)
        assertEquals(121L, summary.papers)
        assertEquals(0, summary.actualNew); assertEquals(0, summary.actualUpdated); assertEquals(0, summary.actualAdmitted)
        val late = f.archive().beginAttempt(records[0].id, f.hash("after-freeze"), ArchiveAttemptKind.EMBEDDED_TEXT, null, Instant.now())
        f.archive().saveBody(late.id, f.hash("after-freeze-body"), 100, "text/plain", false, Instant.now())
        assertEquals(firstBodies, repository.item(run.executionId, all[0].id)!!.input.path("bodies"))
    }

    @Test fun `whole source missing descriptors include deleted save error absent and unavailable blobs but not readable errors`() {
        val source = f.task(); val records = f.seedPapers(source, 121)
        // Include cases beyond both the first UI page and the freeze page of 100.
        f.jdbc.update("UPDATE discovery_paper_archive_attempt SET status='DELETED' WHERE record_id=?", records[120].id)
        f.jdbc.update("UPDATE discovery_paper_archive_attempt SET status='SAVE_ERROR' WHERE record_id=?", records[119].id)
        f.jdbc.update("UPDATE discovery_paper_archive_blob SET state='MISSING' WHERE sha256 IN (SELECT blob_sha256 FROM discovery_paper_archive_attempt WHERE record_id=?)", records[118].id)
        val absent = f.archive().beginRecord(source, "OPENALEX", "no-attempt", """{"schemaVersion":1}""", Instant.now())
        val extra = f.archive().beginAttempt(records[117].id, f.hash("save-error-with-readable-body"),
            ArchiveAttemptKind.PMC_XML, null, Instant.now())
        f.jdbc.update("UPDATE discovery_paper_archive_attempt SET status='SAVE_ERROR' WHERE id=?", extra.id)
        val other = f.task(9002)
        f.archive().beginRecord(other, "OPENALEX", "other-source-missing", """{"schemaVersion":1}""", Instant.now())
        assertEquals(4L, repository.missingSourcePapers(source))
        val created = f.create(source); val owner = repository.claimRun("worker")!!
        assertFalse(repository.freezePage(created.executionId, owner.leaseToken!!, owner.generation))
        assertTrue(repository.freezePage(created.executionId, owner.leaseToken!!, owner.generation))
        val frozen = (0..6).flatMap { repository.items(created.executionId, ReprocessKind.PAPER, it, 20).items }
        assertEquals(122, frozen.size)
        assertEquals(setOf(records[118].id, records[119].id, records[120].id, absent.id),
            frozen.filter { it.input.path("bodies").isEmpty }.map { it.input.path("recordId").asLong() }.toSet())
        assertEquals(4L, repository.summary(created.executionId).missingSourcePapers)
    }

    @Test fun `SQL summary spans121 targets preserves overlap and distinguishes unknown from absent qualification and proposals`() {
        val source = f.task(); f.seedPapers(source, 121)
        val created = f.create(source); val id = created.executionId
        assertFalse(repository.summary(id).totalKnown)
        assertFalse(repository.summary(id).targetTotalKnown)
        assertNull(repository.summary(id).wouldCreate)
        val owner = repository.claimRun("worker")!!
        assertFalse(repository.freezePage(id, owner.leaseToken!!, owner.generation))
        assertFalse(repository.summary(id).totalKnown)
        assertTrue(repository.freezePage(id, owner.leaseToken!!, owner.generation))
        assertTrue(repository.summary(id).totalKnown)
        assertFalse(repository.summary(id).targetTotalKnown)
        f.jdbc.update("UPDATE discovery_paper_reprocess_item SET state='COMPLETE',result_json=? WHERE execution_id=?", f.result().toString(), id)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET mode='ENFORCE',phase='REVIEW' WHERE execution_id=?", id)
        fun verification(runId: Long, index: Int, state: String, qualification: String,
            admitted: Boolean = false, projection: String = "NOT_REQUIRED", snapshot: String = "{}"): Long {
            f.jdbc.update("""INSERT IGNORE INTO discovery_verification_run
                (execution_id,mode,policy_version,apollo_requested,settings_json,created_at,updated_at)
                VALUES (?,'ENFORCE',?,0,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))""",
                runId, VerificationMode.ENFORCE.policyVersion, """{"paperReprocessParentId":$id}""")
            f.jdbc.update("""INSERT INTO discovery_verification_item
                (owner_execution_id,expert_doc_id,identity_hash,policy_version,source_name,source_item_key,
                batch_number,snapshot_json,fact_fingerprint,state,qualification,outcome,admission_revision,
                projection_state,created_at,updated_at)
                VALUES (?,?,?,?, 'OPENALEX',?,1,?,?,?,?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))""",
                runId, "doc-$index", f.hash("identity-$runId-$index"), VerificationMode.ENFORCE.policyVersion,
                "paper-$index", snapshot, f.hash("facts-$index"), state, qualification,
                if (admitted) "DIRECT_CLEAR" else if (state == "COMPLETED") "PENDING" else null,
                if (admitted) 1L else null, projection)
            return f.jdbc.queryForObject("SELECT id FROM discovery_verification_item WHERE owner_execution_id=? AND expert_doc_id=?",
                Long::class.java, runId, "doc-$index")!!
        }
        val linked = mapOf(
            0 to verification(id, 0, "COMPLETED", "MET", true, "PROJECTED"),
            1 to verification(id, 1, "QUEUED", "NOT_EVALUATED"),
            4 to verification(id, 4, "COMPLETED", "UNKNOWN"),
            5 to verification(id, 5, "COMPLETED", "NOT_MET"),
            6 to verification(id, 6, "COMPLETED", "CONFLICT"),
            9 to verification(id + 1000, 9, "COMPLETED", "MET", true, "PROJECTED"),
            120 to verification(id + 1000, 120, "COMPLETED", "MET", true, "ERROR",
                """{"identitySupplement":{"phase":"APPLIED","repairReceipt":{"reason":"REPAIRED"}}}"""))
        // Same document in another review cannot duplicate a target or override its exact link.
        verification(id + 2000, 9, "COMPLETED", "UNKNOWN")
        val foreign = verification(id + 3000, 10, "COMPLETED", "MET", true, "ERROR")
        f.jdbc.update("UPDATE discovery_verification_run SET settings_json='{}' WHERE execution_id=?", id + 3000)
        repeat(121) { index ->
            val result = f.result().put("docId", "doc-$index")
                .put("businessExistenceEvaluated", true).put("wouldCreate", index == 0).put("wouldRepair", index == 120)
                .put("actualNew", if (index == 0) 1 else 0)
                .put("targetOutcome", when (index) { 0, 8 -> "NEW"; 2 -> "CONFLICT"; 3 -> "PROTECTED"; 7 -> "ERROR"; else -> "UNCHANGED" })
            linked[index]?.let { itemId ->
                result.put("verificationItemId", itemId).put("verificationRunId", if (index in setOf(9, 120)) id + 1000 else id)
            }
            if (index == 8) result.put("needsRecheck", true)
            if (index == 10) result.put("verificationItemId", foreign).put("verificationRunId", id + 3000)
            val input = f.mapper.createObjectNode().put("schemaVersion", 1).put("email", "target$index@fixture.edu")
            f.jdbc.update("""INSERT INTO discovery_paper_reprocess_item
                (execution_id,kind,item_key,state,input_json,result_json,created_at,updated_at)
                VALUES (?,'TARGET',?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))""",
                id, f.hash("target-$index"), when (index) { 1, 8 -> "RUNNING"; 7 -> "ERROR"; else -> "COMPLETE" },
                input.toString(), result.toString())
        }
        val before = f.jdbc.queryForList("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? ORDER BY id", id)
        val summary = repository.summary(id)
        assertEquals(121L, summary.papers); assertEquals(121L, summary.targets)
        assertTrue(summary.totalKnown); assertTrue(summary.targetTotalKnown)
        assertEquals("PAPER", summary.paperUnit); assertEquals("TARGET", summary.targetUnit); assertTrue(summary.categoriesOverlap)
        assertEquals(1, summary.actualNew); assertEquals(1, summary.actualUpdated); assertEquals(3, summary.actualAdmitted)
        assertEquals(5L, summary.pendingVerification)
        assertEquals(1L, summary.wouldCreate); assertEquals(1L, summary.wouldRepair)
        assertEquals(115L, summary.unchanged); assertEquals(1L, summary.conflict); assertEquals(1L, summary.protected)
        assertEquals(1L, summary.syncFailure)
        assertEquals(ReprocessQualificationSummary(3, 1, 1, 1, 1, 114), summary.qualification)
        val pages = (0..6).map { repository.items(id, ReprocessKind.TARGET, it, 20) }
        assertEquals(listOf(20, 20, 20, 20, 20, 20, 1), pages.map { it.items.size })
        assertTrue(pages.all { it.total == 121L })
        assertEquals(summary, repository.summary(id))
        assertEquals(before, f.jdbc.queryForList("SELECT * FROM discovery_paper_reprocess_item WHERE execution_id=? ORDER BY id", id))
        // Real AUDIT results are identity candidates only: the two true flags do not establish existence,
        // but the candidate proposal counts must still be shown (explicitly overlapping, not a write).
        f.jdbc.update("""UPDATE discovery_paper_reprocess_item SET result_json=?
            WHERE execution_id=? AND kind='TARGET' AND item_key=?""",
            f.result("AUDIT_CANDIDATE").put("businessExistenceEvaluated", false)
                .put("wouldCreate", true).put("wouldRepair", true).toString(), id, f.hash("target-119"))
        assertEquals(2L, repository.summary(id).wouldCreate); assertEquals(2L, repository.summary(id).wouldRepair)
        assertNull(repository.summary(id).unchanged); assertNull(repository.summary(id).protected)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET phase='BIND' WHERE execution_id=?", id)
        assertFalse(repository.summary(id).targetTotalKnown)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET mode='AUDIT' WHERE execution_id=?", id)
        val audit = repository.summary(id)
        assertEquals(0, audit.actualNew); assertEquals(0, audit.actualUpdated); assertEquals(0, audit.actualAdmitted)
        assertEquals(0L, audit.pendingVerification)
    }
    @Test fun `request retries concurrent tabs active uniqueness and task insertion are atomic`() {
        val source = f.task(); f.seedPapers(source, 1)
        val scope = f.scope(source); val request = f.request(source, scope); val hash = f.hash(f.mapper.writeValueAsString(request))
        val gate = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map { pool.submit<ReprocessRun> { gate.await(); f.repository().create(request, hash, scope, "tab-$it") } }
            gate.countDown()
            val ids = calls.map { it.get(30, TimeUnit.SECONDS).executionId }
            assertEquals(1, ids.distinct().size)
            assertEquals(ids[0], repository.create(request, hash, scope, "resend-after-disconnect").executionId)
            assertEquals("REQUEST_KEY_CONFLICT", assertThrows(ReprocessException::class.java) {
                repository.create(request, f.hash("changed-payload"), scope, "operator")
            }.code)
            val another = request.copy(requestKey = UUID.randomUUID().toString())
            assertEquals(ids[0], repository.create(another, f.hash("different-key"), scope, "another-tab").executionId)
            assertEquals(1L, f.jdbc.queryForObject("SELECT COUNT(*) FROM discovery_paper_reprocess_run", Long::class.java))
            assertEquals(1L, f.jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE task_type='DISCOVERY_PAPER_REPROCESS'", Long::class.java))
            assertEquals(ids[0], repository.runs(source, true, request.requestKey, 0, 20).items.single().executionId)
            assertEquals(0L, repository.runs(source + 1, null, null, 0, 20).total)
        } finally { pool.shutdownNow() }
    }

    @Test fun `different request keys racing cannot create duplicate active or orphan task rows`() {
        val source = f.task(); f.seedPapers(source, 1); val scope = f.scope(source)
        val gate = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val calls = (1..2).map { number -> pool.submit<ReprocessRun> {
                val request = f.request(source, scope)
                gate.await(); f.repository().create(request, f.hash(f.mapper.writeValueAsString(request)), scope, "tab-$number")
            } }
            gate.countDown()
            assertEquals(1, calls.map { it.get(30, TimeUnit.SECONDS).executionId }.distinct().size)
            assertEquals(1L, f.jdbc.queryForObject("SELECT COUNT(*) FROM task_execution WHERE task_type='DISCOVERY_PAPER_REPROCESS'", Long::class.java))
        } finally { pool.shutdownNow() }
    }

    @Test fun `scope watermark rejects running missing changed metadata and deleting blobs during freeze`() {
        assertEquals("SOURCE_NOT_FOUND", assertThrows(ReprocessException::class.java) { repository.watermark(404) }.code)
        val source = f.task(status = "RUNNING")
        assertEquals("SOURCE_RUNNING", assertThrows(ReprocessException::class.java) { repository.watermark(source) }.code)
        f.jdbc.update("UPDATE task_execution SET status='SUCCESS' WHERE id=?", source)
        val records = f.seedPapers(source)
        val scope = f.scope(source); val request = f.request(source, scope)
        f.archive().saveAnalysis(records[0].id, f.archive().record(records[0].id)!!.revision, "{}", Instant.now())
        assertEquals("SCOPE_CHANGED", assertThrows(ReprocessException::class.java) {
            repository.create(request, f.hash("scope-request"), scope, "owner")
        }.code)
        assertNull(repository.active())
        val run = f.create(source); val owner = repository.claimRun("owner")!!
        assertFalse(repository.freezePage(run.executionId, owner.leaseToken!!, owner.generation))
        val blob = f.archive().attempts(records[120].id).single().blobSha256!!
        f.jdbc.update("UPDATE discovery_paper_archive_blob SET state='DELETING' WHERE sha256=?", blob)
        assertEquals("SCOPE_CHANGED", assertThrows(ReprocessException::class.java) {
            f.repository().freezePage(run.executionId, owner.leaseToken!!, owner.generation)
        }.code)
        val failed = repository.find(run.executionId)!!
        assertEquals(ReprocessState.FAILED, failed.state)
        assertEquals("SCOPE_CHANGED", failed.scope.path("reasonCode").asText())
        assertFalse(failed.scope.path("frozen").asBoolean())
        assertNull(repository.active())
        assertEquals("FAILED", f.tasks.findById(run.executionId).get().status)
        assertEquals(121L, repository.summary(run.executionId).paperErrors)
        assertEquals("SCOPE_CHANGED", assertThrows(ReprocessException::class.java) {
            repository.retry(run.executionId, failed.revision, f.versions(failed.scope))
        }.code)
    }

    @Test fun `lease fencing heartbeat interruption explicit same version retry and rejection release use SQL`() {
        val source = f.task(); f.seedPapers(source, 3)
        val created = f.create(source); var run = repository.claimRun("first-worker")!!
        assertTrue(repository.releaseRejected(run.executionId, run.leaseToken!!, run.generation))
        assertFalse(repository.heartbeat(run.executionId, run.leaseToken!!, run.generation))
        run = repository.claimRun("accepted-worker")!!
        assertTrue(repository.freezePage(run.executionId, run.leaseToken!!, run.generation))
        assertTrue(repository.setPhase(run.executionId, run.leaseToken!!, run.generation, PaperReprocessPhase.PARSE))
        val first = repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER)!!
        assertNull(repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER))
        assertTrue(repository.heartbeat(run.executionId, run.leaseToken!!, run.generation))
        val task = f.tasks.findById(run.executionId).get()
        assertEquals(run.leaseToken, task.ownerToken); assertNotNull(task.heartbeatAt)
        val scanAt = LocalDateTime.now().plusSeconds(11)
        assertEquals(0, f.tasks.interruptExpired(scanAt.minusMinutes(5), scanAt))
        assertEquals(repository.find(run.executionId)!!.leaseUntil, repository.item(run.executionId, first.id)!!.leaseUntil)
        assertTrue(repository.completeItem(run.executionId, run.leaseToken!!, run.generation, first.id, first.leaseToken!!, first.generation, ReprocessItemState.COMPLETE, f.result()))
        assertEquals(1, f.tasks.findById(run.executionId).get().successCount)
        assertEquals(0, f.tasks.findById(run.executionId).get().failureCount)
        val second = repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER)!!
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET lease_until=UTC_TIMESTAMP(3)-INTERVAL 1 SECOND WHERE execution_id=?", run.executionId)
        assertFalse(repository.completeItem(run.executionId, run.leaseToken!!, run.generation, second.id, second.leaseToken!!, second.generation, ReprocessItemState.COMPLETE, f.result()))
        val expiredAt = scanAt.plusMinutes(5)
        assertEquals(1, f.tasks.interruptExpired(expiredAt.minusMinutes(5), expiredAt))
        assertNull(f.repository().claimRun("restarted-process"))
        val interrupted = repository.find(run.executionId)!!
        assertEquals(ReprocessState.INTERRUPTED, interrupted.state)
        assertEquals(ReprocessItemState.COMPLETE, repository.item(run.executionId, first.id)!!.state)
        assertEquals(2L, repository.summary(run.executionId).paperErrors)
        val recovered = repository.item(run.executionId, second.id)!!
        val persisted = f.mapper.readTree(f.jdbc.queryForObject(
            "SELECT result_json FROM discovery_paper_reprocess_item WHERE id=?", String::class.java, second.id))
        assertEquals(persisted, recovered.result)
        assertEquals("EXECUTION_LOST", persisted.path("reasonCode").asText())
        assertTrue(persisted.path("reason").asText().length in 1..256)
        assertTrue(persisted.path("reason").asText().any { it in '\u4e00'..'\u9fff' })
        assertEquals(1, persisted.path("schemaVersion").asInt())
        assertEquals((1..6).toList(), persisted.path("trace").map { it.path("stepNo").asInt() })
        assertEquals(listOf("ERROR") + List(5) { "NOT_STARTED" }, persisted.path("trace").map { it.path("state").asText() })
        assertEquals(second.input.path("bodies").map { it.path("attemptId") to it.path("hash") },
            persisted.path("attempts").map { it.path("attemptId") to it.path("hash") })
        assertEquals("INTERRUPTED", f.tasks.findById(run.executionId).get().status)
        assertEquals(3, f.tasks.findById(run.executionId).get().successCount)
        assertEquals(2, f.tasks.findById(run.executionId).get().failureCount)
        val versions = f.versions(interrupted.scope)
        assertEquals("VERSION_CHANGED", assertThrows(ReprocessException::class.java) {
            repository.retry(run.executionId, interrupted.revision, versions.deepCopy().put("codeVersion", "new-version"))
        }.code)
        val retried = f.repository().retry(run.executionId, interrupted.revision, versions)
        assertEquals(created.executionId, retried.executionId)
        assertEquals(0, f.tasks.interruptExpired(scanAt.minusMinutes(5), scanAt))
        assertEquals(1L, repository.summary(run.executionId).paperComplete)
        assertEquals(2, repository.items(run.executionId, ReprocessKind.PAPER, 0, 20).items.count { it.state == ReprocessItemState.PENDING })
        assertEquals(1, f.tasks.findById(run.executionId).get().successCount)
        assertEquals(0, f.tasks.findById(run.executionId).get().failureCount)
        val newRun = repository.claimRun("explicit-retry-worker")!!
        assertFalse(repository.completeItem(run.executionId, run.leaseToken!!, run.generation, second.id, second.leaseToken!!, second.generation, ReprocessItemState.COMPLETE, f.result()))
        assertFalse(repository.setPhase(run.executionId, run.leaseToken!!, run.generation, PaperReprocessPhase.SAVE))
        assertTrue(repository.heartbeat(newRun.executionId, newRun.leaseToken!!, newRun.generation))
        assertTrue(newRun.generation > run.generation)
    }

    @Test fun `cancel CAS preserves complete results fences new work and retry rebuilds only unfinished targets`() {
        val source = f.task(); f.seedPapers(source, 5)
        val created = f.create(source); val run = repository.claimRun("worker")!!
        repository.freezePage(run.executionId, run.leaseToken!!, run.generation)
        repository.setPhase(run.executionId, run.leaseToken!!, run.generation, PaperReprocessPhase.PARSE)
        repeat(2) {
            val item = repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER)!!
            assertTrue(repository.completeItem(run.executionId, run.leaseToken!!, run.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.COMPLETE, f.result()))
        }
        val running = repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER)!!
        assertEquals("REVISION_CONFLICT", assertThrows(ReprocessException::class.java) { repository.cancel(run.executionId, created.revision) }.code)
        val current = repository.find(run.executionId)!!
        val cancelling = repository.cancel(run.executionId, current.revision)
        assertEquals(ReprocessState.CANCELLING, cancelling.state)
        assertNull(repository.claimItem(run.executionId, run.leaseToken!!, run.generation, ReprocessKind.PAPER))
        assertEquals(2L, repository.summary(run.executionId).paperCancelled)
        assertEquals(ReprocessItemState.RUNNING, repository.item(run.executionId, running.id)!!.state)
        assertTrue(repository.finish(run.executionId, run.leaseToken!!, run.generation, ReprocessState.CANCELLED, "CANCELLED"))
        val cancelled = repository.find(run.executionId)!!
        assertEquals(2L, repository.summary(run.executionId).paperComplete)
        assertEquals(3L, repository.summary(run.executionId).paperCancelled)
        val cancelledItem = repository.item(run.executionId, running.id)!!
        assertEquals(6, cancelledItem.result!!.path("trace").size())
        assertTrue(cancelledItem.result!!.path("trace").all { it.path("state").asText() == "CANCELLED" })
        assertEquals(cancelledItem.input.path("bodies").map { it.path("attemptId").asLong() }, cancelledItem.result!!.path("attempts").map { it.path("attemptId").asLong() })
        assertEquals("CANCELLED", f.tasks.findById(run.executionId).get().status)
        assertNull(repository.active())
        val retried = repository.retry(run.executionId, cancelled.revision, f.versions(cancelled.scope))
        val newOwner = repository.claimRun("retry")!!
        while (true) {
            val item = repository.claimItem(run.executionId, newOwner.leaseToken!!, newOwner.generation, ReprocessKind.PAPER) ?: break
            repository.completeItem(run.executionId, newOwner.leaseToken!!, newOwner.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.COMPLETE, f.result())
        }
        val target = f.mapper.createObjectNode().put("schemaVersion", 1).put("email", "fixture@example.invalid")
        target.putArray("paperRefs").add(created.executionId)
        val key = f.hash("fixture@example.invalid")
        assertTrue(repository.addTarget(run.executionId, newOwner.leaseToken!!, newOwner.generation, key, target))
        assertTrue(repository.addTarget(run.executionId, newOwner.leaseToken!!, newOwner.generation, key, target))
        val reordered = f.mapper.createObjectNode().put("email", "fixture@example.invalid").put("schemaVersion", 1)
        reordered.putArray("paperRefs").add(created.executionId.toInt())
        assertTrue(repository.addTarget(run.executionId, newOwner.leaseToken!!, newOwner.generation, key, reordered))
        val changedRefs = target.deepCopy()
        changedRefs.putArray("paperRefs").add(created.executionId + 1)
        assertEquals("IMMUTABLE_INPUT_CONFLICT", assertThrows(ReprocessException::class.java) {
            repository.addTarget(run.executionId, newOwner.leaseToken!!, newOwner.generation, key, changedRefs)
        }.code)
        assertEquals(1L, repository.summary(run.executionId).targets)
        assertEquals("IMMUTABLE_INPUT_CONFLICT", assertThrows(ReprocessException::class.java) {
            repository.addTarget(run.executionId, newOwner.leaseToken!!, newOwner.generation, key, target.deepCopy().put("other", "changed"))
        }.code)
        assertEquals("ITEMS_NOT_FINISHED", assertThrows(ReprocessException::class.java) { repository.finish(run.executionId, newOwner.leaseToken!!, newOwner.generation, ReprocessState.COMPLETED) }.code)
        val targetItem = repository.claimItem(run.executionId, newOwner.leaseToken!!, newOwner.generation, ReprocessKind.TARGET)!!
        repository.completeItem(run.executionId, newOwner.leaseToken!!, newOwner.generation, targetItem.id, targetItem.leaseToken!!, targetItem.generation, ReprocessItemState.ERROR, f.result("PARSER_ERROR"))
        repository.finish(run.executionId, newOwner.leaseToken!!, newOwner.generation, ReprocessState.COMPLETED_WITH_ERRORS)
        val failed = repository.find(run.executionId)!!
        repository.retry(run.executionId, failed.revision, f.versions(failed.scope))
        assertEquals(0L, repository.summary(run.executionId).targets)
        assertEquals(5L, repository.summary(run.executionId).paperComplete)
        assertEquals(retried.executionId, repository.active()!!.executionId)
    }

    @Test fun `all reads reject unknown JSON and states without rewriting evidence and large results become durable errors`() {
        val source = f.task(); f.seedPapers(source, 1)
        val run = f.create(source); val owner = repository.claimRun("worker")!!
        repository.freezePage(run.executionId, owner.leaseToken!!, owner.generation)
        repository.setPhase(run.executionId, owner.leaseToken!!, owner.generation, PaperReprocessPhase.PARSE)
        val item = repository.claimItem(run.executionId, owner.leaseToken!!, owner.generation, ReprocessKind.PAPER)!!
        val unknown = f.result().put("schemaVersion", 2)
        assertEquals("INVALID_SCHEMA", assertThrows(ReprocessException::class.java) {
            repository.completeItem(run.executionId, owner.leaseToken!!, owner.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.COMPLETE, unknown)
        }.code)
        val badTrace = f.result(); (badTrace.path("trace")[0] as ObjectNode).put("state", "UNKNOWN")
        assertEquals("INVALID_TRACE", assertThrows(ReprocessException::class.java) {
            repository.completeItem(run.executionId, owner.leaseToken!!, owner.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.COMPLETE, badTrace)
        }.code)
        val huge = f.result().put("evidence", "a".repeat(4 * 1024 * 1024))
        assertTrue(repository.completeItem(run.executionId, owner.leaseToken!!, owner.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.COMPLETE, huge))
        assertEquals(ReprocessItemState.ERROR, repository.item(run.executionId, item.id)!!.state)
        assertEquals("RESULT_TOO_LARGE", repository.item(run.executionId, item.id)!!.result!!.path("reasonCode").asText())
        assertEquals(item.input.path("bodies").map { it.path("hash").asText() }, repository.item(run.executionId, item.id)!!.result!!.path("attempts").map { it.path("hash").asText() })
        val largeInput = f.mapper.createObjectNode().put("schemaVersion", 1).put("evidence", "b".repeat(4 * 1024 * 1024))
        assertEquals("INPUT_TOO_LARGE", assertThrows(ReprocessException::class.java) {
            repository.addTarget(run.executionId, owner.leaseToken!!, owner.generation, f.hash("large-input"), largeInput)
        }.code)
        val original = f.jdbc.queryForObject("SELECT result_json FROM discovery_paper_reprocess_item WHERE id=?", String::class.java, item.id)!!
        val saved = f.mapper.readTree(original)
        assertEquals(repository.item(run.executionId, item.id)!!.result, saved)
        assertEquals(1, saved.path("schemaVersion").asInt())
        assertTrue(saved.path("reason").asText().length in 1..256)
        assertTrue(saved.path("reason").asText().any { it in '\u4e00'..'\u9fff' })
        assertTrue(original.toByteArray(Charsets.UTF_8).size <= 4 * 1024 * 1024)
        assertEquals((1..6).toList(), saved.path("trace").map { it.path("stepNo").asInt() })
        assertEquals(listOf("ERROR") + List(5) { "NOT_STARTED" }, saved.path("trace").map { it.path("state").asText() })
        assertEquals(item.input.path("bodies").map { it.path("attemptId") to it.path("hash") },
            saved.path("attempts").map { it.path("attemptId") to it.path("hash") })
        val raw = f.result().put("schemaVersion", 999).toString()
        f.jdbc.update("UPDATE discovery_paper_reprocess_item SET result_json=? WHERE id=?", raw, item.id)
        assertEquals("INVALID_SCHEMA", assertThrows(ReprocessException::class.java) { repository.item(run.executionId, item.id) }.code)
        assertEquals(raw, f.jdbc.queryForObject("SELECT result_json FROM discovery_paper_reprocess_item WHERE id=?", String::class.java, item.id))
        val revision = repository.find(run.executionId)!!.revision
        assertEquals("INVALID_SCHEMA", assertThrows(ReprocessException::class.java) { repository.cancel(run.executionId, revision) }.code)
        assertEquals(ReprocessState.RUNNING, repository.find(run.executionId)!!.state)
        f.jdbc.update("UPDATE discovery_paper_reprocess_item SET result_json=? WHERE id=?", original, item.id)
        f.jdbc.execute("ALTER TABLE discovery_paper_reprocess_item ALTER CHECK chk_paper_reprocess_item_state NOT ENFORCED")
        try {
            f.jdbc.update("UPDATE discovery_paper_reprocess_item SET state='FUTURE' WHERE id=?", item.id)
            assertEquals("INVALID_STATE", assertThrows(ReprocessException::class.java) { repository.items(run.executionId, null, 0, 20) }.code)
            assertEquals("INVALID_STATE", assertThrows(ReprocessException::class.java) { repository.summary(run.executionId) }.code)
            assertEquals("FUTURE", f.jdbc.queryForObject("SELECT state FROM discovery_paper_reprocess_item WHERE id=?", String::class.java, item.id))
        } finally {
            f.jdbc.update("UPDATE discovery_paper_reprocess_item SET state='ERROR' WHERE id=?", item.id)
            f.jdbc.execute("ALTER TABLE discovery_paper_reprocess_item ALTER CHECK chk_paper_reprocess_item_state ENFORCED")
        }
    }

    @Test fun `records without retained bodies are frozen not silently filtered and retry honors the other active run`() {
        val source = f.task()
        val record = f.archive().beginRecord(source, "OPENALEX", "missing-body", """{"schemaVersion":1,"title":"No retained body"}""", Instant.now())
        val created = f.create(source); val owner = repository.claimRun("worker")!!
        assertTrue(repository.freezePage(owner.executionId, owner.leaseToken!!, owner.generation))
        assertEquals(1L, repository.summary(created.executionId).papers)
        val frozen = repository.items(created.executionId, ReprocessKind.PAPER, 0, 20).items.single()
        assertEquals(record.id, frozen.input.path("recordId").asLong())
        assertTrue(frozen.input.path("bodies").isEmpty)
        assertTrue(frozen.input.path("first").isNull)
        assertNull(repository.item(created.executionId + 1, frozen.id))
        assertThrows(IllegalArgumentException::class.java) { repository.items(created.executionId, null, -1, 20) }
        assertThrows(IllegalArgumentException::class.java) { repository.runs(null, null, null, 0, 101) }
        repository.setPhase(created.executionId, owner.leaseToken!!, owner.generation, PaperReprocessPhase.READ)
        val item = repository.claimItem(created.executionId, owner.leaseToken!!, owner.generation, ReprocessKind.PAPER)!!
        repository.completeItem(created.executionId, owner.leaseToken!!, owner.generation, item.id, item.leaseToken!!, item.generation, ReprocessItemState.ERROR, f.result("SOURCE_MISSING"))
        repository.finish(created.executionId, owner.leaseToken!!, owner.generation, ReprocessState.COMPLETED_WITH_ERRORS)
        val finished = repository.find(created.executionId)!!
        val next = f.create(source)
        assertNotEquals(created.executionId, next.executionId)
        assertEquals("ACTIVE_RUN_EXISTS", assertThrows(ReprocessException::class.java) {
            repository.retry(created.executionId, finished.revision, f.versions(finished.scope))
        }.code)
        assertEquals(next.executionId, repository.active()!!.executionId)
    }

    @Test fun `oversized frozen input rolls back its page instead of truncating or mutating source metadata`() {
        val source = f.task()
        val metadata = f.mapper.createObjectNode().put("schemaVersion", 1).put("title", "x".repeat(4 * 1024 * 1024)).toString()
        val record = f.archive().beginRecord(source, "OPENALEX", "large-metadata", metadata, Instant.now())
        val created = f.create(source); val owner = repository.claimRun("worker")!!
        assertEquals("INPUT_TOO_LARGE", assertThrows(ReprocessException::class.java) {
            repository.freezePage(created.executionId, owner.leaseToken!!, owner.generation)
        }.code)
        assertEquals(0L, repository.summary(created.executionId).papers)
        assertEquals(0L, repository.find(created.executionId)!!.scope.path("cursor").asLong())
        assertEquals(metadata, f.archive().record(record.id)!!.metadataJson)
    }
}
