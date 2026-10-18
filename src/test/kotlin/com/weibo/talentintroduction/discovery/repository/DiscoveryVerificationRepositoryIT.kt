package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.*
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

    @Test fun `parallel budget reservation never exceeds shared day cap and releases lock`() {
        val leases = (0 until 20).map { index -> run(1001L + index); activeForOwner(index) }
        val reservations = parallel(20) { index ->
            val lease = leases[index]
            repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                VerificationAttemptRequest("request-$index", VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1, "{}"), 100, 3, 12, NOW)
        }
        assertEquals(3, reservations.count { it.attemptId != null })
        assertEquals(3L, jdbc.queryForObject("SELECT COUNT(*) FROM discovery_verification_attempt", Long::class.java))
        assertEquals(3L, jdbc.queryForObject("SELECT SUM(request_count) FROM discovery_verification_item", Long::class.java))
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
        assertEquals(VerificationBudgetReason.DAY_LIMIT, request("second").deferred)
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

    @Test fun `minute item and UTC day budgets count dispatch reservations before responses`() {
        run(); active()
        var lease = claim()
        fun reserve(key: String, now: Instant, minute: Int, day: Int, item: Int) =
            repository.reserveAttemptWithBudget(lease.id, lease.leaseToken!!, lease.generation,
                VerificationAttemptRequest(key, VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1, "{}"),
                minute, day, item, now)
        assertNotNull(reserve("one", NOW, 1, 10, 3).attemptId)
        assertEquals(VerificationBudgetReason.MINUTE_LIMIT, reserve("two", NOW, 1, 10, 3).deferred)
        assertNotNull(reserve("two", NOW.plusSeconds(60), 1, 10, 3).attemptId)
        assertEquals(VerificationBudgetReason.ITEM_LIMIT, reserve("three", NOW.plusSeconds(60), 10, 10, 2).deferred)
        // Reclaim after midnight; yesterday's crashed DISPATCH_RESERVED remains in yesterday's budget.
        val tomorrow = Instant.parse("2026-10-19T00:00:00Z")
        lease = claim(tomorrow)
        assertNotNull(reserve("three", tomorrow, 1, 1, 3).attemptId)
        assertEquals(VerificationBudgetReason.ITEM_LIMIT, reserve("four", tomorrow, 1, 1, 3).deferred)
        assertEquals(3, repository.findItem(lease.id)!!.requestCount)
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
}
