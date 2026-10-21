package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.service.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Always runs when selected: unavailable Docker is a failure, never a silent skip. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false", "spring.flyway.target=158"])
@Import(DiscoveryVerificationRepository::class)
class DiscoveryVerificationRepositoryIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("verification_ledger").withUsername("test").withPassword("test")
        private val NOW = Instant.parse("2026-10-18T10:00:00.123Z")
        @JvmStatic @BeforeAll fun start() {
            check(DockerClientFactory.instance().isDockerAvailable) { "Isolated Docker MySQL is required" }
            mysql.start()
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
    @Autowired private lateinit var repository: DiscoveryVerificationRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var manager: PlatformTransactionManager

    @BeforeEach fun clean() {
        // These tables exist only in this test's disposable container.
        jdbc.update("DELETE FROM discovery_verification_attempt")
        jdbc.update("DELETE FROM discovery_verification_item")
        jdbc.update("DELETE FROM discovery_verification_run")
        jdbc.update("DELETE FROM expert_discovery_admission")
        jdbc.update("DELETE FROM task_execution")
    }
    private fun run(id: Long = 1001, mode: VerificationMode = VerificationMode.ENFORCE) =
        repository.createRun(id, mode, true, "{}", NOW)
    private fun reservation(doc: String = "doc-1", owner: Long = 1001, mode: VerificationMode = VerificationMode.ENFORCE) =
        VerificationReservation(owner, doc, "a".repeat(64), mode.policyVersion, "OPENALEX", "source-$doc", "{\"name\":\"Synthetic\",\"email\":\"fixture@example.invalid\"}", "b".repeat(64))
    private fun active(doc: String = "doc-1"): VerificationItem {
        val item = repository.reserveItem(reservation(doc), NOW)
        return repository.activateItem(item.id, 1, NOW)
    }
    private fun claim(now: Instant = NOW) = repository.claimDue(now, 120)!!
    private fun result() = VerificationResult(VerificationState.COMPLETED, VerificationOutcome.PENDING,
        VerificationQualification.UNKNOWN, VerificationRoute.NONE, "{\"schemaVersion\":1}", "[\"IDENTITY_UNRESOLVED\"]")
    private fun <T> parallel(count: Int, action: (Int) -> T): List<T> {
        val pool = Executors.newFixedThreadPool(count)
        val start = CountDownLatch(1)
        try {
            val futures = (0 until count).map { index -> pool.submit(Callable { start.await(); action(index) }) }
            start.countDown()
            return futures.map { it.get(90, TimeUnit.SECONDS) }
        } finally { pool.shutdownNow() }
    }

    /** A-1 runtime fixture: repeat/replay uses the same immutable identity and first owner. */
    private fun seedLedgerFixture(): VerificationItem {
        run(); run(1002)
        val ids = parallel(20) { repository.reserveItem(reservation(), NOW).id }
        assertEquals(1, ids.toSet().size)
        val original = repository.activateItem(ids.first(), 1, NOW)
        val replay = repository.reserveItem(reservation(owner = 1002).copy(sourceItemKey = "new-source"), NOW)
        assertEquals(original.id, replay.id); assertEquals(1001L, replay.ownerExecutionId)
        assertEquals("source-doc-1", replay.sourceItemKey); assertEquals(1, replay.batchNumber)
        return replay
    }

    @Test fun `twenty reserves and cross run replay keep first ownership`() {
        val item = seedLedgerFixture()
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item", Long::class.java))
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_run", Long::class.java))
        repository.activateItem(item.id, 99, NOW)
        assertEquals(1, repository.findItem(item.id)!!.batchNumber)
        val lease = claim()
        assertTrue(repository.saveEvidenceAndOutcome(item.id, lease.leaseToken!!, lease.generation, result(), NOW))
        assertEquals(VerificationState.COMPLETED, repository.reserveItem(reservation(owner = 1002), NOW).state)
        assertEquals(0L, repository.findRun(1002)!!.revision)
    }

    @Test fun `mode and identity keys are independent and malformed parameters fail`() {
        run(); run(1002, VerificationMode.AUDIT)
        val original = repository.reserveItem(reservation(), NOW)
        val audit = repository.reserveItem(reservation(owner = 1002, mode = VerificationMode.AUDIT), NOW)
        val changed = repository.reserveItem(reservation().copy(identityHash = "c".repeat(64)), NOW)
        assertEquals(3, setOf(original.id, audit.id, changed.id).size)
        assertEquals(VerificationContext.MANAGED_ENFORCE, repository.findManagedContexts(listOf("doc-1"))["doc-1"])
        assertEquals(VerificationContext.UNMANAGED, repository.findManagedContexts(listOf("old"))["old"])
        assertThrows(IllegalArgumentException::class.java) { repository.findManagedContexts((0..500).map { "doc-$it" }) }
        assertThrows(IllegalArgumentException::class.java) { repository.createRun(0, VerificationMode.AUDIT, false, "{}", NOW) }
        assertThrows(IllegalArgumentException::class.java) { repository.createRun(1001, VerificationMode.AUDIT, true, "{}", NOW) }
        jdbc.update("UPDATE discovery_verification_item SET state='BAD' WHERE id=?", original.id)
        assertThrows(Exception::class.java) { repository.findItem(original.id) }
    }

    @Test fun `expired lease reclaimed after repository recreation rejects all stale writes`() {
        run(); active()
        val first = claim()
        // Reconstructing a stateless reader demonstrates recovery uses SQL, not process memory.
        assertEquals(first, DiscoveryVerificationRepository(jdbc).findItem(first.id))
        val later = NOW.plusSeconds(121)
        val second = claim(later)
        assertNotEquals(first.leaseToken, second.leaseToken)
        assertEquals(first.generation + 1, second.generation)
        val revision = repository.findRun(1001)!!.revision
        assertFalse(repository.renewLease(first.id, first.leaseToken!!, first.generation, later, 120))
        assertFalse(repository.reschedule(first.id, first.leaseToken!!, first.generation, later, 0, "{}", "[]", later))
        assertFalse(repository.saveEvidenceAndOutcome(first.id, first.leaseToken!!, first.generation, result(), later))
        assertFalse(repository.saveEvidenceAndOutcome(second.id, second.leaseToken!!, second.generation - 1, result(), later))
        assertEquals(revision, repository.findRun(1001)!!.revision)
        assertTrue(repository.saveEvidenceAndOutcome(second.id, second.leaseToken!!, second.generation, result(), later))
        assertEquals(revision + 1, repository.findRun(1001)!!.revision)
        assertFalse(repository.saveEvidenceAndOutcome(second.id, second.leaseToken!!, second.generation, result(), later))
    }

    @Test fun `item and revision roll back together on transactional failure`() {
        run(); active(); val lease = claim()
        val revision = repository.findRun(1001)!!.revision
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(manager).executeWithoutResult {
                assertTrue(repository.saveEvidenceAndOutcome(lease.id, lease.leaseToken!!, lease.generation, result(), NOW))
                error("injected commit transaction failure")
            }
        }
        assertEquals(VerificationState.RUNNING, repository.findItem(lease.id)!!.state)
        assertEquals(revision, repository.findRun(1001)!!.revision)
    }

    @Test fun `parallel Apollo reservations ignore legacy day cap and retain transaction rollback`() {
        val leases = (0 until 20).map { index -> run(1001L + index); activeForOwner(index) }
        val reservations = parallel(20) { index ->
            val lease = leases[index]
            repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                VerificationAttemptRequest("request-$index", VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1, "{}"), 100, 3, 12, NOW)
        }
        assertEquals(20, reservations.count { it.attemptId != null })
        assertEquals(20L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt", Long::class.java))
        assertEquals(20L, jdbc.queryForObject("SELECT SUM(request_count) FROM discovery_verification_item", Long::class.java))
        assertNull(jdbc.queryForObject("SELECT IS_USED_LOCK('discovery_verification_apollo_budget')", Long::class.java))
        val lease = leases.first()
        val before = repository.findRun(lease.ownerExecutionId)!!.revision
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(manager).executeWithoutResult {
                val reserved = repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                    VerificationAttemptRequest("rollback", VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 2, "{}"), 100, 100, 12, NOW)
                assertNotNull(reserved.attemptId)
                error("rollback budget")
            }
        }
        assertEquals(before, repository.findRun(lease.ownerExecutionId)!!.revision)
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt WHERE request_key='rollback'", Long::class.java))
        assertNull(jdbc.queryForObject("SELECT IS_USED_LOCK('discovery_verification_apollo_budget')", Long::class.java))
    }
    private fun activeForOwner(index: Int): VerificationItem {
        val owner = 1001L + index
        val item = repository.reserveItem(reservation("doc-$index", owner), NOW)
        repository.activateItem(item.id, 1, NOW)
        return claim()
    }

    @Test fun `attempt completion retry limits UTC rollover and projection are independent`() {
        run(); active(); var lease = claim()
        fun request(key: String) = repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
            VerificationAttemptRequest(key, VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1, "{}"), 1, 1, 2, NOW)
        val attempt = request("first").attemptId!!
        assertEquals(VerificationBudgetReason.DUPLICATE_REQUEST, request("first").deferred)
        assertNotNull(request("second").attemptId)
        assertTrue(repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation, attempt, 429, "RATE_LIMIT", "{}", "d".repeat(64), NOW))
        assertFalse(repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation, attempt, 200, "OK", "{}", null, NOW))
        assertTrue(repository.reschedule(lease.id, lease.leaseToken!!, lease.generation, NOW.plusSeconds(60), 0, "{\"progress\":{\"stage\":\"APOLLO_SEARCH\"}}", "[]", NOW))
        assertNull(repository.claimDue(NOW.plusSeconds(59), 120))
        lease = claim(NOW.plusSeconds(60))
        assertEquals(0, lease.transientAttempts)
        assertTrue(repository.renewLease(lease.id, lease.leaseToken!!, lease.generation, NOW.plusSeconds(60), 120))
        val clear = VerificationResult(VerificationState.COMPLETED, VerificationOutcome.DIRECT_CLEAR, VerificationQualification.MET,
            VerificationRoute.DIRECT, "{}", "[]", admissionRevision = 7, projectionState = VerificationProjection.PENDING)
        assertTrue(repository.saveEvidenceAndOutcome(lease.id, lease.leaseToken!!, lease.generation, clear, NOW.plusSeconds(60)))
        assertFalse(repository.markProjection(lease.id, 8, VerificationProjection.PROJECTED, null, NOW))
        assertTrue(repository.markProjection(lease.id, 7, VerificationProjection.ERROR, "ES_UNAVAILABLE", NOW))
        assertTrue(repository.markProjection(lease.id, 7, VerificationProjection.PROJECTED, null, NOW))
        assertFalse(repository.markProjection(lease.id, 7, VerificationProjection.ERROR, "late", NOW))
        assertEquals(VerificationOutcome.DIRECT_CLEAR, repository.findItem(lease.id)!!.outcome)
    }

    @Test fun `Apollo ignores all legacy quotas while ORCID retains its evidence budget`() {
        run(); active()
        val lease = claim()
        fun reserve(key: String, provider: VerificationProvider) =
            repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                VerificationAttemptRequest(key, provider, VerificationQueryKind.BROAD, 1, 1, "{}"),
                1, 1, 1, NOW)
        repeat(15) { assertNotNull(reserve("apollo-$it", VerificationProvider.APOLLO).attemptId) }
        assertEquals(15, repository.findItem(lease.id)!!.requestCount)
        assertEquals(VerificationBudgetReason.DUPLICATE_REQUEST, reserve("apollo-0", VerificationProvider.APOLLO).deferred)
        assertEquals(VerificationBudgetReason.ITEM_LIMIT, reserve("orcid", VerificationProvider.ORCID).deferred)
    }

    @Test fun `reserved errors stay outside activated denominator and input close is idempotent`() {
        run(); val item = repository.reserveItem(reservation(), NOW)
        assertEquals(0, item.batchNumber); assertNull(item.activatedAt); assertNull(item.outcome)
        assertEquals(VerificationQualification.NOT_EVALUATED, item.qualification)
        assertEquals(listOf(item), repository.listReserved(10))
        assertFalse(repository.failReserved(item.id, "[]", NOW))
        assertTrue(repository.closeInput(1001, NOW)); assertFalse(repository.closeInput(1001, NOW))
        assertTrue(repository.failReserved(item.id, "[\"RAW_WRITE_FAILED\"]", NOW))
        val failed = repository.findItem(item.id)!!
        assertEquals(VerificationState.ERROR, failed.state); assertNull(failed.activatedAt); assertNull(failed.outcome)
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_item WHERE activated_at IS NOT NULL", Long::class.java))
        assertEquals("158", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE version='158' AND success=1", String::class.java))
    }

    private fun replayPending(doc: String): DiscoveryVerificationRepository.ReplayExpected {
        val original = active(doc)
        val lease = claim()
        assertTrue(repository.saveEvidenceAndOutcome(original.id, lease.leaseToken!!, lease.generation,
            result().copy(evidenceJson = """{"evidence":[],"search":{"complete":true},"progress":{"stage":"DONE","page":4},"orcidCompleted":[]}"""), NOW))
        val item = repository.findItem(original.id)!!
        return DiscoveryVerificationRepository.ReplayExpected(item, repository.replayAdmission(doc),
            DiscoveryVerificationRepository.replayEvidence(item.evidenceJson))
    }
    private fun settle() {
        repository.closeInput(1001, NOW)
        jdbc.update("""INSERT INTO task_execution (id,task_type,trigger_type,status,started_at,finished_at)
            VALUES (1001,'EXPERT_DISCOVERY','MANUAL','SUCCESS',?,?)
            ON DUPLICATE KEY UPDATE finished_at=VALUES(finished_at)""", NOW.verificationUtc(), NOW.verificationUtc())
    }
    private fun replay(items: List<DiscoveryVerificationRepository.ReplayExpected>) =
        repository.requeuePendingForEvidenceReplay(1001, items, NOW.plusSeconds(1))

    @Test fun `replay hundred item batch queues once without self blocking and preserves billing and identity`() {
        run()
        val expected = (1..100).map { replayPending("replay-$it") }; settle()
        val beforeRevision = repository.findRun(1001)!!.revision
        assertThrows(IllegalArgumentException::class.java) { replay(expected + expected.first()) }
        val receipts = replay(expected)
        assertEquals(100, receipts.count { it.status == "QUEUED" })
        expected.forEach { e ->
            val next = repository.findItem(e.item.id)!!
            assertEquals(e.item.copy(state = VerificationState.QUEUED, outcome = null,
                qualification = VerificationQualification.NOT_EVALUATED, route = VerificationRoute.NONE,
                completedAt = null, evidenceJson = e.evidenceJson, reasonsJson = "[]",
                generation = e.item.generation + 1, updatedAt = NOW.plusSeconds(1)), next)
        }
        assertTrue(repository.findRun(1001)!!.revision > beforeRevision)
        assertEquals(setOf("STALE_OR_ALREADY_APPLIED"), replay(expected).map { it.status }.toSet())
        val queued = repository.findItem(expected.first().item.id)!!
        val claimed = repository.claimDue(NOW.plusSeconds(1), 120)!!
        assertEquals(queued.generation + 1, claimed.generation)
        assertFalse(repository.saveEvidenceAndOutcome(claimed.id, "old-token", queued.generation - 1, result(), NOW.plusSeconds(1)))
    }

    @Test fun `replay rejects unsettled run changed admissions wrong hashes and wrong owners`() {
        run(); val first = replayPending("cas-1"); val second = replayPending("cas-2")
        assertEquals(setOf("RUN_NOT_SETTLED"), replay(listOf(first, second)).map { it.status }.toSet())
        val queued = active("in-flight"); settle()
        assertEquals("RUN_NOT_SETTLED", replay(listOf(first)).single().status)
        val lease = claim()
        repository.saveEvidenceAndOutcome(queued.id, lease.leaseToken!!, lease.generation, result(), NOW)
        assertEquals("STALE_OR_ALREADY_APPLIED", replay(listOf(first.copy(snapshotSha256 = "0".repeat(64)))).single().status)
        jdbc.update("""INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,updated_at)
            VALUES (?,?, 'HOLD',1,?)""", first.item.expertDocId, first.item.identityHash, NOW.verificationUtc())
        assertEquals("STALE_OR_ALREADY_APPLIED", replay(listOf(first)).single().status)
        val held = first.copy(admission = repository.replayAdmission(first.item.expertDocId))
        assertEquals("PROTECTED_ADMISSION", replay(listOf(held)).single().status)
        assertThrows(IllegalArgumentException::class.java) { replay(listOf(first.copy(item = first.item.copy(ownerExecutionId = 1002)))) }
        assertEquals("QUEUED", replay(listOf(second)).single().status)
    }

    @Test fun `replay validates entire batch before writes and transaction failures roll back`() {
        run(); val first = replayPending("atomic-1"); val second = replayPending("atomic-2"); settle()
        val revision = repository.findRun(1001)!!.revision
        assertThrows(IllegalArgumentException::class.java) { replay(listOf(first, second.copy(evidenceJson = """{"progress":{"stage":"DECIDE"}}"""))) }
        assertEquals(first.item, repository.findItem(first.item.id))
        assertThrows(IllegalStateException::class.java) {
            TransactionTemplate(manager).executeWithoutResult { replay(listOf(first, second)); error("injected") }
        }
        assertEquals(revision, repository.findRun(1001)!!.revision)
        val results = parallel(2) { replay(listOf(first, second)) }
        assertEquals(2, results.flatten().count { it.status == "QUEUED" })
        assertEquals(2, results.flatten().count { it.status == "STALE_OR_ALREADY_APPLIED" })
    }

    @Test fun `known protection reasons and revision changes remain skipped`() {
        run()
        val protected = replayPending("protected")
        jdbc.update("UPDATE discovery_verification_item SET reasons_json='[\"FACT_CHANGED\"]' WHERE id=?", protected.item.id)
        val changed = repository.findItem(protected.item.id)!!
        val current = protected.copy(item = changed, reasonsSha256 = DiscoveryVerificationRepository.replayHash(changed.reasonsJson))
        settle()
        assertEquals("PROTECTED_REASON", replay(listOf(current)).single().status)
        assertEquals(current.item, repository.findItem(current.item.id))
    }
    private fun detail(lease: VerificationItem, key: String, at: Instant = NOW,
        repo: DiscoveryVerificationRepository = repository) =
        repo.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
            VerificationAttemptRequest(key, VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1, 1,
                """{"personId":"person-1","matchingVersion":2}"""), 1, 1, 1, at)

    @Test fun `persistent DETAIL fault permits only one probe across workers and crashed windows`() {
        val leases = (0..2).map { run(1001L + it); activeForOwner(it) }
        val failed = detail(leases[0], "fault").attemptId!!
        val old = detail(leases[1], "old-in-flight").attemptId!!
        val faultAt = NOW.plusSeconds(1)
        assertTrue(repository.completeAttempt(leases[0].id, leases[0].leaseToken!!, leases[0].generation,
            failed, 422, "APOLLO_CREDITS_EXHAUSTED", "{}", null, faultAt))
        assertTrue(repository.completeAttempt(leases[1].id, leases[1].leaseToken!!, leases[1].generation,
            old, 200, "OK", "{}", null, NOW.plusSeconds(2)))
        val before = repository.findItem(leases[2].id)!!
        val blocked = detail(leases[2], "blocked", NOW.plusSeconds(3))
        assertEquals(VerificationBudgetReason.CREDITS_WAIT, blocked.deferred)
        assertEquals(faultAt.plusSeconds(1800), blocked.retryAt)
        assertEquals(before, repository.findItem(leases[2].id))
        assertEquals(2, leases.sumOf { repository.findAttempts(it.id).size })
        val restarted = DiscoveryVerificationRepository(jdbc)
        val restartResult = TransactionTemplate(manager).execute { detail(leases[2], "restart", NOW.plusSeconds(4), restarted) }!!
        assertEquals(blocked, restartResult)
        val probeAt = faultAt.plusSeconds(1800)
        leases.forEach { assertTrue(repository.renewLease(it.id, it.leaseToken!!, it.generation, NOW.plusSeconds(5), 8000)) }
        val permits = parallel(2) { detail(leases[it], "probe-$it", probeAt) }
        assertEquals(1, permits.count { it.attemptId != null })
        assertEquals(1, permits.count { it.deferred == VerificationBudgetReason.CREDITS_WAIT })
        assertEquals(probeAt.plusSeconds(1800), permits.single { it.deferred != null }.retryAt)
        // An unfinished (crashed) probe consumes the entire window, including after reconstruction.
        assertEquals(VerificationBudgetReason.CREDITS_WAIT, TransactionTemplate(manager).execute {
            detail(leases[2], "crashed-window", probeAt.plusSeconds(1799), restarted)
        }!!.deferred)
        val nextAt = probeAt.plusSeconds(1800)
        val next = detail(leases[2], "next-window", nextAt).attemptId!!
        assertTrue(repository.completeAttempt(leases[2].id, leases[2].leaseToken!!, leases[2].generation,
            next, 200, "OK", "{}", null, nextAt))
        assertNotNull(detail(leases[2], "reopened", nextAt.plusSeconds(1)).attemptId)
        assertEquals(5, leases.sumOf { repository.findAttempts(it.id).size })
    }

    @Test fun `only explicit completed DETAIL credit faults pause and search ORCID remain independent`() {
        run(); active(); val lease = claim()
        val unknown = detail(lease, "unknown").attemptId!!
        repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation, unknown,
            422, "APOLLO_HTTP_422_UNCLASSIFIED", "{}", null, NOW)
        val fault = detail(lease, "explicit").attemptId!!
        repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation, fault,
            422, "APOLLO_CREDITS_EXHAUSTED", "{}", null, NOW)
        assertEquals(VerificationBudgetReason.CREDITS_WAIT, detail(lease, "no-detail").deferred)
        listOf(VerificationProvider.APOLLO to VerificationQueryKind.BROAD, VerificationProvider.ORCID to VerificationQueryKind.ORCID).forEach { (provider, kind) ->
            assertNotNull(repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                VerificationAttemptRequest("continue-$provider", provider, kind, 1, 1, "{}"), 1, 1, 100, NOW).attemptId)
        }
        val later = NOW.plusSeconds(1800)
        repository.renewLease(lease.id, lease.leaseToken!!, lease.generation, NOW, 4000)
        val probe = detail(lease, "not-ok", later).attemptId!!
        repository.completeAttempt(lease.id, lease.leaseToken!!, lease.generation, probe, 200, "RESPONSE_INVALID", "{}", null, later)
        assertEquals(VerificationBudgetReason.CREDITS_WAIT, detail(lease, "still-blocked", later.plusSeconds(1)).deferred)
    }

    private fun recoveryError(doc: String, code: String = "REQUEST_INVALID"): DiscoveryVerificationRepository.ReplayExpected {
        val snapshot = ruleSnapshot().copy(identityHash = "a".repeat(64), factFingerprint = "b".repeat(64))
        val mapper = DiscoveryVerificationReplayTool.mapper
        val item = repository.reserveItem(reservation(doc).copy(snapshotJson = mapper.writeValueAsString(snapshot)), NOW)
        repository.activateItem(item.id, 1, NOW)
        val lease = claim()
        val attempt = detail(lease, "error-$doc").attemptId!!
        repository.completeAttempt(item.id, lease.leaseToken!!, lease.generation, attempt, 422, code, "{}", null, NOW)
        val candidate = ApolloCandidate("person-1", "Ada", null, "L**", null, null, null)
        val evidence = mapper.writeValueAsString(VerificationWorkerEvidence(
            search = ApolloSearchEvidence(candidates = listOf(candidate), complete = true, matchingVersion = 2, queryNames = setOf("Ada Lee")),
            progress = VerificationProgress(stage = "APOLLO_SEARCH", queryIndex = 1, complete = true, candidateIds = listOf("person-1")),
            queries = listOf(ApolloSearchQuery("Ada Lee")), ruleVersion = 1))
        repository.saveFailure(item.id, lease.leaseToken!!, lease.generation,
            VerificationResult(VerificationState.ERROR, null, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE,
                evidence, """["$code"]"""), 0, NOW)
        return repository.findItem(item.id)!!.let { DiscoveryVerificationRepository.ReplayExpected(it, null, it.evidenceJson!!) }
    }
    private fun recover(items: List<DiscoveryVerificationRepository.ReplayExpected>) =
        repository.requeueErrorsForCreditRecovery(1001, items, NOW.plusSeconds(1))

    @Test fun `error recovery CAS preserves original cursors billing attempts and run ownership`() {
        run(); val first = recoveryError("error-1"); val second = recoveryError("error-2", "APOLLO_HTTP_422_UNCLASSIFIED")
        val oldAttempts = listOf(first, second).associate { it.item.id to repository.findAttempts(it.item.id) }
        assertEquals(setOf("RUN_NOT_SETTLED"), recover(listOf(first, second)).map { it.status }.toSet())
        settle()
        assertEquals("INELIGIBLE", replay(listOf(first.copy(evidenceJson = DiscoveryVerificationRepository.replayEvidence(first.evidenceJson)))).single().status)
        assertThrows(IllegalArgumentException::class.java) { recover(List(101) { first }) }
        assertEquals("STALE_OR_ALREADY_APPLIED", recover(listOf(first.copy(evidenceSha256 = "0".repeat(64)))).single().status)
        val receipts = parallel(2) { recover(listOf(first, second)) }.flatten()
        assertEquals(2, receipts.count { it.status == "QUEUED" })
        assertEquals(2, receipts.count { it.status == "STALE_OR_ALREADY_APPLIED" })
        listOf(first, second).forEach { expected ->
            val next = repository.findItem(expected.item.id)!!
            assertEquals(expected.item.copy(state = VerificationState.QUEUED, completedAt = null, reasonsJson = "[]",
                generation = expected.item.generation + 1, updatedAt = NOW.plusSeconds(1)), next)
            assertEquals(oldAttempts[next.id], repository.findAttempts(next.id))
        }
        val lease = claim(NOW.plusSeconds(1))
        assertNotNull(detail(lease, "new-http", NOW.plusSeconds(1)).attemptId)
        assertEquals(first.item.requestCount + 1, repository.findItem(lease.id)!!.requestCount)
    }

    @Test fun `error recovery rejects manual admissions reasons damaged cursors and stale manifests without writes`() {
        run()
        val initial = (1..9).map { recoveryError("reject-$it") }
        jdbc.update("""INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,updated_at)
            VALUES (?,?, 'HOLD',1,?)""", initial[0].item.expertDocId, initial[0].item.identityHash, NOW.verificationUtc())
        jdbc.update("UPDATE discovery_verification_item SET reasons_json='[\"FACT_CHANGED\"]' WHERE id=?", initial[1].item.id)
        jdbc.update("UPDATE discovery_verification_item SET evidence_json='broken' WHERE id=?", initial[2].item.id)
        listOf(3 to "DECIDE", 4 to "LOCAL_EVIDENCE", 5 to "PROJECT").forEach { (index, stage) ->
            jdbc.update("UPDATE discovery_verification_item SET evidence_json=JSON_SET(evidence_json,'$.progress.stage',?) WHERE id=?", stage, initial[index].item.id)
        }
        jdbc.update("UPDATE discovery_verification_item SET evidence_json=JSON_REMOVE(evidence_json,'$.queries') WHERE id=?", initial[6].item.id)
        jdbc.update("UPDATE discovery_verification_item SET evidence_json=JSON_SET(evidence_json,'$.search.detailsCompleted',JSON_ARRAY('person-1')) WHERE id=?", initial[7].item.id)
        jdbc.update("UPDATE discovery_verification_attempt SET result_code='CONFIG_AUTH' WHERE item_id=?", initial[8].item.id)
        settle()
        val current = initial.map { old -> repository.findItem(old.item.id)!!.let {
            DiscoveryVerificationRepository.ReplayExpected(it, repository.replayAdmission(it.expertDocId), it.evidenceJson!!)
        } }
        val beforeRevision = repository.findRun(1001)!!.revision
        assertEquals("STALE_OR_ALREADY_APPLIED", recover(listOf(initial[0])).single().status)
        val receipts = recover(current)
        assertEquals(listOf("PROTECTED_ADMISSION", "PROTECTED_REASON") + List(7) { "INELIGIBLE" }, receipts.map { it.status })
        assertEquals(beforeRevision, repository.findRun(1001)!!.revision)
        current.forEach { assertEquals(it.item, repository.findItem(it.item.id)) }
    }

}
