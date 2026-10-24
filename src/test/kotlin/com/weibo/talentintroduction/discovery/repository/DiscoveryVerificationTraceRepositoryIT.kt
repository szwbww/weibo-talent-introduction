package com.weibo.talentintroduction.discovery.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = ["spring.flyway.placeholder-replacement=false", "spring.flyway.target=161"])
@Import(DiscoveryVerificationRepository::class, DiscoveryVerificationTraceRepository::class)
class DiscoveryVerificationTraceRepositoryIT {
    companion object {
        private class Mysql : MySQLContainer<Mysql>("mysql:8.0.36")
        private val mysql = Mysql().withDatabaseName("trace_07").withUsername("test").withPassword("test")
        private val now = Instant.parse("2026-10-24T12:00:00Z")
        @JvmStatic @BeforeAll fun start() {
            check(org.testcontainers.DockerClientFactory.instance().isDockerAvailable) { "Real disposable MySQL is mandatory" }
            mysql.start()
        }
        @JvmStatic @AfterAll fun stop() { if (mysql.isRunning) mysql.stop() }
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url", mysql::getJdbcUrl); r.add("spring.datasource.username", mysql::getUsername)
            r.add("spring.datasource.password", mysql::getPassword)
        }
    }
    @Autowired lateinit var repository: DiscoveryVerificationRepository
    @Autowired lateinit var trace: DiscoveryVerificationTraceRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    @BeforeEach fun clean() {
        listOf("discovery_verification_step", "discovery_verification_attempt", "discovery_verification_item",
            "discovery_verification_run", "expert_discovery_admission", "task_execution").forEach { jdbc.update("DELETE FROM $it") }
    }
    private fun reserved(recheck: Boolean = false): VerificationItem {
        if (recheck) {
            jdbc.update("INSERT INTO task_execution(id,task_type,trigger_type,status,started_at) VALUES (7001,'EXPERT_DISCOVERY','MANUAL','RUNNING',?)", now.verificationUtc())
            repository.createRecheckRun(7001, VerificationMode.AUDIT, false, "{}",
                RecheckSpec("fixture", "trace", "{}", RecheckEvidenceMode.REUSE, now, "fixture", "3"), now)
        } else repository.createRun(7001, VerificationMode.AUDIT, false, "{}", now)
        val input = VerificationReservation(7001, "trace-doc", "a".repeat(64), VerificationMode.AUDIT.policyVersion,
            "OPENALEX", "W1", "{}", "b".repeat(64))
        return if (recheck) repository.reserveRecheckItem(input, now) else repository.reserveItem(input, now)
    }
    private fun startRecheck(item: VerificationItem) {
        val run = repository.findRun(item.ownerExecutionId)!!
        assertTrue(repository.compareAndSetRecheckControl(run.executionId, run.revision,
            RecheckControl(RecheckPhase.READY, repository.recheckScopeHash(run.executionId)), now))
        val ready = repository.findRun(run.executionId)!!
        assertTrue(repository.compareAndSetRecheckControl(run.executionId, ready.revision,
            repository.recheck(ready).control.copy(phase = RecheckPhase.RUNNING), now))
        repository.activateItem(item.id, 1, now)
    }
    @Test fun `read absence READY reservations and old completed history never manufacture rows`() {
        val item = reserved(true)
        assertFalse(trace.read(item.id).recorded)
        assertFalse(trace.initSteps(item.id, now))
        assertEquals(0, repository.findSteps(item.id).size)
        startRecheck(item)
        assertTrue(trace.initSteps(item.id, now)); assertTrue(trace.initSteps(item.id, now))
        assertEquals((1..8).toList(), trace.read(item.id).steps.map { it.stepNo })
        assertTrue(trace.read(item.id).steps.all { it.state == VerificationStepState.NOT_STARTED && it.startedAt == null })
        jdbc.update("DELETE FROM discovery_verification_step WHERE item_id=?", item.id)
        val lease = repository.claimDue(now, 120)!!
        assertTrue(repository.saveEvidenceAndOutcome(item.id, lease.leaseToken!!, lease.generation,
            VerificationResult(VerificationState.COMPLETED, VerificationOutcome.PENDING, VerificationQualification.UNKNOWN,
                VerificationRoute.NONE, "{}", "[]"), now))
        assertFalse(trace.read(item.id).recorded); assertFalse(trace.initSteps(item.id, now))
        assertFalse(trace.read(item.id).recorded)
    }
    @Test fun `terminal attempts immutable stale lease forbidden and takeover appends observed interruption`() {
        val item = reserved(); repository.activateItem(item.id, 1, now)
        val lease = repository.claimDue(now, 120)!!
        val first = trace.startStep(item.id, lease.leaseToken!!, lease.generation, 1, "{}", "{}", now)!!
        assertTrue(trace.finishStep(item.id, lease.leaseToken!!, lease.generation, first.id, VerificationStepState.SUCCESS, "{}", "{}", now))
        assertFalse(trace.finishStep(item.id, lease.leaseToken!!, lease.generation, first.id, VerificationStepState.ERROR, "{}", "{}", now))
        val second = trace.startStep(item.id, lease.leaseToken!!, lease.generation, 1, "{}", "{}", now)!!
        assertEquals(2, second.attemptNo)
        assertNull(trace.startStep(item.id, "wrong", lease.generation, 2, "{}", "{}", now))
        val later = now.plusSeconds(121)
        assertNull(trace.startStep(item.id, lease.leaseToken!!, lease.generation, 2, "{}", "{}", later))
        val replacement = repository.claimDue(later, 120)!!
        val third = trace.startStep(item.id, replacement.leaseToken!!, replacement.generation, 1, "{}", "{}", later)!!
        assertEquals(3, third.attemptNo)
        assertFalse(trace.finishStep(item.id, lease.leaseToken!!, lease.generation, third.id, VerificationStepState.SUCCESS, "{}", "{}", later))
        assertEquals(first.copy(state = VerificationStepState.SUCCESS, outputJson = trace.read(item.id).steps.first().outputJson,
            reasonJson = trace.read(item.id).steps.first().reasonJson, finishedAt = now), trace.read(item.id).steps.first())
        assertTrue(trace.read(item.id).steps.first { it.id == second.id }.reasonJson!!.contains("LEASE_REPLACED"))
    }
    @Test fun `concurrent completion wins once and bounded excerpts retain truthful count and references`() {
        val item = reserved(); repository.activateItem(item.id, 1, now)
        val lease = repository.claimDue(now, 120)!!
        val large = mapper.writeValueAsString((1..500).map { mapOf("excerpt" to "字".repeat(3000), "apiKey" to "secret") })
        val step = trace.startStep(item.id, lease.leaseToken!!, lease.generation, 2, large, "{}", now)!!
        assertTrue(step.inputJson!!.toByteArray().size <= 65536)
        val summary = mapper.readTree(step.inputJson)
        assertTrue(summary.path("truncated").asBoolean()); assertEquals(500, summary.path("count").asInt())
        assertTrue(summary.path("fullRef").asText().contains("evidence_json")); assertFalse(step.inputJson!!.contains("secret"))
        val pool = Executors.newFixedThreadPool(2); val go = CountDownLatch(1)
        try {
            val futures = (1..2).map { pool.submit(Callable { go.await(); trace.finishStep(item.id, lease.leaseToken!!,
                lease.generation, step.id, VerificationStepState.SUCCESS, large, "{}", now) }) }
            go.countDown(); assertEquals(1, futures.count { it.get(30, TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
        assertTrue(trace.read(item.id).steps.single { it.id == step.id }.outputJson!!.toByteArray().size <= 65536)
    }
    @Test fun `cancelled dispatched response acknowledges once with exact ownership and no item run writes`() {
        val item = reserved(true); startRecheck(item)
        val lease = repository.claimDue(now, 120)!!
        val request = """{"_dispatch":{"generation":${lease.generation},"ownerHash":"${DiscoveryVerificationRepository.replayHash(lease.leaseToken)}"}}"""
        val id = repository.reserveAttemptWithBudget(item.id, lease.leaseToken!!, lease.generation,
            VerificationAttemptRequest("owned-request", VerificationProvider.APOLLO, VerificationQueryKind.BROAD, 1, 1, request),
            100, 100, 10, now).attemptId!!
        val run = repository.findRun(7001)!!
        assertTrue(repository.compareAndSetRecheckControl(7001, run.revision,
            repository.recheck(run).control.copy(phase = RecheckPhase.CANCELLING), now))
        assertTrue(repository.cancelRecheckItem(item.id, now))
        val before = repository.findItem(item.id); val runBefore = repository.findRun(7001)
        val pending = repository.findAttempts(item.id).single()
        assertEquals("UNKNOWN_AFTER_CANCEL", pending.resultCode); assertNull(pending.httpStatus); assertNull(pending.completedAt)
        assertFalse(repository.completeDispatchedDiagnostic(item.id, "wrong", lease.generation, id, "owned-request", 422, "BAD_INPUT", "{}", null, now))
        assertFalse(repository.completeDispatchedDiagnostic(item.id, lease.leaseToken!!, lease.generation, id, "wrong", 422, "BAD_INPUT", "{}", null, now))
        assertTrue(repository.completeDispatchedDiagnostic(item.id, lease.leaseToken!!, lease.generation, id, "owned-request", 422, "BAD_INPUT", "{}", null, now))
        assertFalse(repository.completeDispatchedDiagnostic(item.id, lease.leaseToken!!, lease.generation, id, "owned-request", 200, "OK", "{}", null, now))
        assertEquals(before, repository.findItem(item.id)); assertEquals(runBefore, repository.findRun(7001))
        assertEquals(422, repository.findAttempts(item.id).single().httpStatus)
        assertNull(trace.startStep(item.id, lease.leaseToken!!, lease.generation, 1, "{}", "{}", now))
    }
}
