package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.discovery.domain.RecheckEvidenceMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class DiscoveryAcademicBatchTest {
    @Test fun `blocked group does not delay another owner and interrupted waiter never dispatches`() {
        val firstEntered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val wire = CopyOnWriteArrayList<List<String>>()
        val (_, service) = mocked { ids, guard ->
            if ("A123" in ids) { firstEntered.countDown(); release.await() }
            guard(); wire += ids; ids.associateWith { EnrichmentOutcome.NotFound }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<RecheckAcademicEvidence> { invoke(service, 0) }
            assertTrue(firstEntered.await(2, TimeUnit.SECONDS))
            val second = pool.submit<RecheckAcademicEvidence> { invoke(service, 1, owner = 2) }
            assertEquals("A124", second.get(1, TimeUnit.SECONDS).trustedId)
            release.countDown()
            first.get(2, TimeUnit.SECONDS)
            assertEquals(2, wire.size)
        } finally { release.countDown(); service.close(); pool.shutdownNow() }

        val cancelledWire = CopyOnWriteArrayList<List<String>>()
        val (_, later) = mocked { ids, guard -> guard(); cancelledWire += ids; ids.associateWith { EnrichmentOutcome.NotFound } }
        val waiter = Thread { runCatching { invoke(later, 2) } }
        try {
            waiter.start(); waiter.interrupt(); waiter.join(1000)
            Thread.sleep(40)
            assertTrue(cancelledWire.isEmpty())
        } finally { later.close(); waiter.join(1000) }
    }

    private fun invoke(service: DiscoveryRecheckAcademicEvidenceService, index: Int, owner: Long = 1,
        time: java.time.Instant = recheckTime, guard: () -> Unit = {}) = service.collectCoalesced(
        owner, evidenceSnapshot(authorId = "A${123 + index}", suffix = "$index"), RecheckEvidenceMode.REFRESH, time, guard)

    private fun mocked(action: (List<String>, () -> Unit) -> Map<String, EnrichmentOutcome>): Pair<OpenAlexDataSource, DiscoveryRecheckAcademicEvidenceService> {
        val client = Mockito.mock(OpenAlexDataSource::class.java) { call ->
            if (call.method.name == "batchEnrichByAuthorIds" && call.arguments.size == 3)
                action(call.getArgument(0), call.getArgument(2))
            else Mockito.RETURNS_DEFAULTS.answer(call)
        }
        return client to academicService(client)
    }

    @Test fun `four compatible callers share one lookup preserve partial and retry results`() {
        val calls = CopyOnWriteArrayList<List<String>>()
        val (_, service) = mocked { ids, guard ->
            guard(); calls += ids
            ids.associateWith { when (it) {
                "A123" -> EnrichmentOutcome.Success(AuthorEnrichment(12, 345, 67), titlesFailed = true)
                "A124" -> EnrichmentOutcome.RateLimited(1000)
                else -> EnrichmentOutcome.NotFound
            } }
        }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val ready = CyclicBarrier(4)
            val rows = (0..3).map { i -> pool.submit<RecheckAcademicEvidence> { ready.await(); invoke(service, i) } }.map { it.get(3, TimeUnit.SECONDS) }
            assertEquals(1, calls.size); assertEquals(setOf("A123", "A124", "A125", "A126"), calls.single().toSet())
            assertEquals(listOf("PARTIAL_TITLES"), rows[0].reasonCodes); assertEquals(12, rows[0].data!!.hIndex)
            assertEquals(listOf("RATE_LIMITED"), rows[1].reasonCodes); assertNotNull(rows[1].retryAt)
            assertNull(rows[2].data); assertEquals(listOf("ACADEMIC_SOURCE_NOT_FOUND"), rows[2].reasonCodes)
            assertEquals(4, rows.map { it.identityHash }.distinct().size)
        } finally { service.close(); pool.shutdownNow() }
    }

    @Test fun `run and frozen time isolate groups while reuse and untrusted identity never dispatch`() {
        val calls = CopyOnWriteArrayList<List<String>>()
        val (client, service) = mocked { ids, guard -> guard(); calls += ids; ids.associateWith { EnrichmentOutcome.NotFound } }
        val pool = Executors.newFixedThreadPool(3)
        try {
            val ready = CyclicBarrier(3)
            val jobs = (0..2).map { i -> pool.submit { ready.await(); invoke(service, i, if (i == 1) 2 else 1,
                if (i == 2) recheckTime.plusSeconds(1) else recheckTime) } }
            jobs.forEach { it.get(3, TimeUnit.SECONDS) }
            assertEquals(3, calls.size); assertTrue(calls.all { it.size == 1 })
            Mockito.clearInvocations(client)
            service.collectCoalesced(1, evidenceSnapshot(), RecheckEvidenceMode.REUSE, recheckTime) {}
            service.collectCoalesced(1, evidenceSnapshot(authorId = null), RecheckEvidenceMode.REFRESH, recheckTime) {}
            Mockito.verifyNoInteractions(client)
        } finally { service.close(); pool.shutdownNow() }
    }

    @Test fun `membership invalidated at actual dispatch aborts whole batch and retries survivors independently`() {
        val cancelled = AtomicBoolean()
        val wire = CopyOnWriteArrayList<List<String>>()
        val (_, service) = mocked { ids, guard ->
            if (ids.size > 1) cancelled.set(true) // Simulates cancellation during provider budget wait.
            guard(); wire += ids
            ids.associateWith { EnrichmentOutcome.Success(AuthorEnrichment(99, 1, 1)) }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ready = CyclicBarrier(2)
            val a = pool.submit<RecheckAcademicEvidence> { ready.await(); invoke(service, 0) { if (cancelled.get()) throw CancellationException() } }
            val b = pool.submit<RecheckAcademicEvidence> { ready.await(); invoke(service, 1) }
            assertThrows(ExecutionException::class.java) { a.get(3, TimeUnit.SECONDS) }
            val result = b.get(3, TimeUnit.SECONDS)
            assertEquals(listOf(listOf("A124")), wire); assertEquals("A124", result.trustedId); assertEquals(99, result.data!!.hIndex)
        } finally { service.close(); pool.shutdownNow() }
    }

    @Test fun `close releases every waiter and rejects later work`() {
        val blocked = CountDownLatch(1); val release = CountDownLatch(1)
        val (_, service) = mocked { ids, guard -> blocked.countDown(); release.await(); guard(); ids.associateWith { EnrichmentOutcome.NotFound } }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val jobs = (0..1).map { i -> pool.submit<RecheckAcademicEvidence> { invoke(service, i) } }
            assertTrue(blocked.await(2, TimeUnit.SECONDS)); service.close()
            jobs.forEach { assertThrows(ExecutionException::class.java) { it.get(1, TimeUnit.SECONDS) } }
            assertThrows(CancellationException::class.java) { invoke(service, 2) }
        } finally { release.countDown(); service.close(); pool.shutdownNow() }
    }
}
