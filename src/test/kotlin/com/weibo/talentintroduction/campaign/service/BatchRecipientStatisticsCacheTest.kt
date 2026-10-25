package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import com.weibo.talentintroduction.template.service.TemplateStatisticsFingerprint
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class BatchRecipientStatisticsCacheTest {
    private val outreach = Mockito.mock(ManualInitialOutreachService::class.java)
    private val templates = Mockito.mock(MailComposeTemplateService::class.java)
    private val mapper = jacksonObjectMapper()
    private val clock = MutableClock(Instant.parse("2026-10-22T01:00:00Z"))
    private val cache = newCache()
    private val computations = AtomicInteger()
    private var resolveContext: (BatchExecutionSnapshot) -> BatchRecipientStatisticsContext = {
        BatchRecipientStatisticsContext(it, RecipientScope.fromSnapshot(it))
    }
    private var compute: (BatchRecipientStatisticsContext) -> PendingOutreachSummary = { summary() }
    private var fingerprint: (Long?, String) -> TemplateStatisticsFingerprint = { id, _ ->
        TemplateStatisticsFingerprint(id ?: 7L, "template-v1")
    }

    init {
        Mockito.doAnswer { invocation ->
            resolveContext(invocation.getArgument(0))
        }.`when`(outreach).statisticsContext(anySnapshot())
        Mockito.doAnswer { invocation ->
            computations.incrementAndGet()
            compute(invocation.getArgument(0))
        }.`when`(outreach).countStrictStatistics(anyContext())
        Mockito.doAnswer { invocation ->
            fingerprint(invocation.getArgument(0), invocation.getArgument(1))
        }.`when`(templates).statisticsFingerprint(Mockito.nullable(Long::class.javaObjectType), Mockito.anyString())
    }

    @AfterEach
    fun `cache only calls read-only statistics seams`() {
        assertTrue(Mockito.mockingDetails(outreach).invocations.all {
            it.method.name in setOf("statisticsContext", "countStrictStatistics")
        }, "Cache must not call sending, preview, or other business operations")
        assertTrue(Mockito.mockingDetails(templates).invocations.all {
            it.method.name == "statisticsFingerprint"
        }, "Cache must not render or mutate templates")
    }

    @Test
    fun `cacheOnly miss has no fabricated summary or computation and rejects conflicting flags`() {
        val miss = cache.statistics(snapshot(), cacheOnly = true)

        assertEquals(BatchRecipientStatisticsState.MISS, miss.state)
        assertNotNull(miss.cacheKey)
        assertNull(miss.summary)
        assertNull(miss.cachedAt)
        assertNull(miss.errorCode)
        assertFalse(miss.stale)
        assertEquals(0, computations.get())
        Mockito.clearInvocations(outreach, templates)

        assertThrows(IllegalArgumentException::class.java) {
            cache.statistics(snapshot(), cacheOnly = true, refresh = true)
        }
        Mockito.verifyNoInteractions(outreach, templates)
    }

    @Test
    fun `default miss computes once hits reuse and only explicit refresh replaces value and completion time`() {
        val sampleTime = LocalDateTime.of(2026, 10, 22, 8, 30)
        compute = { summary(100).copy(asOf = sampleTime) }
        val first = cache.statistics(snapshot())

        assertEquals(BatchRecipientStatisticsState.READY, first.state)
        assertEquals(100, first.summary?.totalSendable)
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 0), first.cachedAt)
        assertEquals(sampleTime, first.summary?.asOf)
        clock.advance(Duration.ofMinutes(5))
        assertEquals(first, cache.statistics(snapshot()))
        assertEquals(first, cache.statistics(snapshot(), cacheOnly = true))
        assertEquals(1, computations.get())

        compute = { summary(125).copy(asOf = sampleTime) }
        val refreshed = cache.statistics(snapshot(), refresh = true)
        assertEquals(BatchRecipientStatisticsState.READY, refreshed.state)
        assertEquals(125, refreshed.summary?.totalSendable)
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 5), refreshed.cachedAt)
        assertEquals(sampleTime, refreshed.summary?.asOf)
        assertEquals(first.cacheKey, refreshed.cacheKey)
        assertEquals(2, computations.get())
    }

    @Test
    fun `twenty concurrent refreshes of one key share exactly one computation`() {
        val enteredContexts = CountDownLatch(20)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val threads = CopyOnWriteArrayList<Thread>()
        resolveContext = {
            enteredContexts.countDown()
            BatchRecipientStatisticsContext(it, RecipientScope.fromSnapshot(it))
        }
        compute = {
            started.countDown()
            await(release)
            summary(30001)
        }
        val executor = Executors.newFixedThreadPool(20)
        try {
            val results = (1..20).map {
                executor.submit<BatchRecipientStatisticsEnvelope> {
                    threads += Thread.currentThread()
                    cache.statistics(snapshot(), refresh = true)
                }
            }
            await(started)
            await(enteredContexts)
            awaitParked(threads, 20)
            assertEquals(1, computations.get())
            assertTrue(results.none { it.isDone })
            release.countDown()

            val envelopes = results.map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(envelopes.all { it.state == BatchRecipientStatisticsState.READY })
            assertTrue(envelopes.all { it == envelopes.first() })
            assertEquals(30001, envelopes.first().summary?.totalSendable)
            assertEquals(1, computations.get())
        } finally {
            release.countDown()
            stop(executor)
        }
    }

    @Test
    fun `two distinct keys compute concurrently third returns busy without queueing and slot is reusable`() {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        compute = {
            started.countDown()
            await(release)
            summary()
        }
        val executor = Executors.newFixedThreadPool(3)
        try {
            val first = executor.submit<BatchRecipientStatisticsEnvelope> { cache.statistics(snapshot(1)) }
            val second = executor.submit<BatchRecipientStatisticsEnvelope> { cache.statistics(snapshot(2)) }
            await(started)
            val third = executor.submit<BatchRecipientStatisticsEnvelope> { cache.statistics(snapshot(3)) }
                .get(2, TimeUnit.SECONDS)

            assertEquals(BatchRecipientStatisticsState.ERROR, third.state)
            assertEquals("STATISTICS_BUSY", third.errorCode)
            assertNull(third.summary)
            assertNull(third.cachedAt)
            assertEquals(2, computations.get())
            assertFalse(first.isDone)
            assertFalse(second.isDone)
            release.countDown()
            assertEquals(BatchRecipientStatisticsState.READY, first.get(10, TimeUnit.SECONDS).state)
            assertEquals(BatchRecipientStatisticsState.READY, second.get(10, TimeUnit.SECONDS).state)
            assertEquals(BatchRecipientStatisticsState.READY, cache.statistics(snapshot(3)).state)
            assertEquals(3, computations.get())
        } finally {
            release.countDown()
            stop(executor)
        }
    }

    @Test
    fun `cacheOnly lookup never waits for refreshing scan and exposes old value while missing key has none`() {
        val original = cache.statistics(snapshot(1))
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        compute = {
            started.countDown()
            await(release)
            summary(222)
        }
        val executor = Executors.newFixedThreadPool(3)
        try {
            val refreshing = executor.submit<BatchRecipientStatisticsEnvelope> {
                cache.statistics(snapshot(1), refresh = true)
            }
            val firstLoad = executor.submit<BatchRecipientStatisticsEnvelope> { cache.statistics(snapshot(2)) }
            await(started)
            val lookup = executor.submit<List<BatchRecipientStatisticsEnvelope>> {
                listOf(cache.statistics(snapshot(1), cacheOnly = true), cache.statistics(snapshot(2), cacheOnly = true))
            }.get(2, TimeUnit.SECONDS)

            assertEquals(BatchRecipientStatisticsState.REFRESHING, lookup[0].state)
            assertEquals(original.summary, lookup[0].summary)
            assertEquals(original.cachedAt, lookup[0].cachedAt)
            assertEquals(original.cacheKey, lookup[0].cacheKey)
            assertEquals(BatchRecipientStatisticsState.REFRESHING, lookup[1].state)
            assertNull(lookup[1].summary)
            assertNull(lookup[1].cachedAt)
            assertEquals(3, computations.get())
            assertFalse(refreshing.isDone)
            assertFalse(firstLoad.isDone)
            release.countDown()
            assertEquals(222, refreshing.get(10, TimeUnit.SECONDS).summary?.totalSendable)
            assertEquals(222, firstLoad.get(10, TimeUnit.SECONDS).summary?.totalSendable)
        } finally {
            release.countDown()
            stop(executor)
        }
    }

    @Test
    fun `completed cache retains 64 keys and evicts least recently read rather than oldest inserted`() {
        (1L..64L).forEach { cache.statistics(snapshot(it)) }
        assertEquals(BatchRecipientStatisticsState.READY, cache.statistics(snapshot(1), cacheOnly = true).state)
        cache.statistics(snapshot(65))

        assertEquals(BatchRecipientStatisticsState.MISS, cache.statistics(snapshot(2), cacheOnly = true).state)
        val retained = (1L..65L).count {
            cache.statistics(snapshot(it), cacheOnly = true).state == BatchRecipientStatisticsState.READY
        }
        assertEquals(64, retained)
        assertEquals(BatchRecipientStatisticsState.READY, cache.statistics(snapshot(1), cacheOnly = true).state)
        assertEquals(BatchRecipientStatisticsState.READY, cache.statistics(snapshot(65), cacheOnly = true).state)
        assertEquals(65, computations.get())
    }

    @Test
    fun `twenty four hour age changes only stale flag and neither lookup nor default hit recomputes`() {
        val original = cache.statistics(snapshot())
        Mockito.clearInvocations(outreach)
        clock.advance(Duration.ofHours(24).minusNanos(1))
        assertFalse(cache.statistics(snapshot(), cacheOnly = true).stale)
        clock.advance(Duration.ofNanos(1))

        val stale = cache.statistics(snapshot(), cacheOnly = true)
        assertTrue(stale.stale)
        assertEquals(original.copy(stale = true), stale)
        assertEquals(stale, cache.statistics(snapshot()))
        Mockito.verify(outreach, Mockito.never()).countStrictStatistics(anyContext())
        assertEquals(1, computations.get())
    }

    @Test
    fun `cachedAt records Shanghai completion time rather than computation start or sample time`() {
        val sampleTime = LocalDateTime.of(2026, 10, 20, 12, 0)
        compute = {
            clock.advance(Duration.ofMinutes(17))
            summary().copy(asOf = sampleTime)
        }

        val result = cache.statistics(snapshot())
        assertEquals(LocalDateTime.of(2026, 10, 22, 9, 17), result.cachedAt)
        assertEquals(sampleTime, result.summary?.asOf)
    }

    @Test
    fun `failed refresh preserves old successful summary and exact timestamp and recovery clears error`() {
        val original = cache.statistics(snapshot())
        clock.advance(Duration.ofHours(25))
        compute = { throw IllegalStateException("ES query includes secret-password and private-data") }

        val error = cache.statistics(snapshot(), refresh = true)
        assertEquals(BatchRecipientStatisticsState.ERROR, error.state)
        assertEquals("STATISTICS_UNAVAILABLE", error.errorCode)
        assertEquals(original.summary, error.summary)
        assertEquals(original.cachedAt, error.cachedAt)
        assertEquals(original.cacheKey, error.cacheKey)
        assertTrue(error.stale)
        assertEquals(error, cache.statistics(snapshot(), cacheOnly = true))
        assertFalse(mapper.writeValueAsString(error.copy(cachedAt = null, summary = null)).contains("secret-password"))

        compute = { summary(250) }
        val recovered = cache.statistics(snapshot(), refresh = true)
        assertEquals(BatchRecipientStatisticsState.READY, recovered.state)
        assertEquals(250, recovered.summary?.totalSendable)
        assertNotEquals(original.cachedAt, recovered.cachedAt)
        assertFalse(recovered.stale)
        assertNull(recovered.errorCode)
        assertEquals(3, computations.get())
    }

    @Test
    fun `first failure has no summary or timestamp while successful zero is legitimate ready data`() {
        compute = { throw IllegalStateException("strict scan was incomplete") }
        val failed = cache.statistics(snapshot())
        assertEquals(BatchRecipientStatisticsState.ERROR, failed.state)
        assertEquals("STATISTICS_UNAVAILABLE", failed.errorCode)
        assertNull(failed.summary)
        assertNull(failed.cachedAt)
        assertFalse(failed.stale)
        assertEquals(failed, cache.statistics(snapshot(), cacheOnly = true))

        compute = { summary(0) }
        val zero = cache.statistics(snapshot())
        assertEquals(BatchRecipientStatisticsState.READY, zero.state)
        assertEquals(0, zero.summary?.totalSendable)
        assertNotNull(zero.cachedAt)
        assertNull(zero.errorCode)
        assertEquals(2, computations.get())
    }

    @Test
    fun `template change during refresh rejects mixed result and preserves prior success`() {
        val current = AtomicReference(TemplateStatisticsFingerprint(7L, "template-v1"))
        fingerprint = { _, _ -> current.get() }
        val original = cache.statistics(snapshot())
        clock.advance(Duration.ofHours(1))
        compute = {
            current.set(TemplateStatisticsFingerprint(7L, "template-v2"))
            summary(999)
        }

        val rejected = cache.statistics(snapshot(), refresh = true)
        assertEquals(BatchRecipientStatisticsState.ERROR, rejected.state)
        assertEquals("TEMPLATE_CHANGED_DURING_STATISTICS", rejected.errorCode)
        assertEquals(original.summary, rejected.summary)
        assertEquals(original.cachedAt, rejected.cachedAt)
        assertEquals(original.cacheKey, rejected.cacheKey)
        assertEquals(2, computations.get())
        val changedKey = cache.statistics(snapshot(), cacheOnly = true)
        assertEquals(BatchRecipientStatisticsState.MISS, changedKey.state)
        assertNotEquals(original.cacheKey, changedKey.cacheKey)
        assertNull(changedKey.summary)
        assertNull(changedKey.cachedAt)
        current.set(TemplateStatisticsFingerprint(7L, "template-v1"))
        assertEquals(rejected, cache.statistics(snapshot(), cacheOnly = true))
    }

    @Test
    fun `first computation cannot publish when default template switches during scan`() {
        val current = AtomicReference(TemplateStatisticsFingerprint(7L, "template-v1"))
        fingerprint = { _, _ -> current.get() }
        compute = {
            current.set(TemplateStatisticsFingerprint(8L, "template-v2"))
            summary(999)
        }

        val result = cache.statistics(snapshot().copy(templateId = null))
        assertEquals(BatchRecipientStatisticsState.ERROR, result.state)
        assertEquals("TEMPLATE_CHANGED_DURING_STATISTICS", result.errorCode)
        assertNull(result.summary)
        assertNull(result.cachedAt)
        assertEquals(1, computations.get())
    }

    @Test
    fun `canonical keys deduplicate and sort every effective collection including verification states`() {
        val baseSnapshot = snapshot().copy(emailVerificationEnabled = true,
            emailVerificationAllowedStates = listOf("unknown", "deliverable", "risky", "unknown"))
        val scope = RecipientScope.fromSnapshot(baseSnapshot).copy(
            funnelLevels = linkedSetOf("APPLICATION", "CANDIDATE"),
            tags = listOf("beta", "alpha", "beta"),
            excludeTags = listOf(" B ", "A", "A"),
            regions = listOf("Europe", "East Asia", "Europe"),
            emailDomains = listOf("z.example", "a.example", "z.example"),
            operatorStatuses = listOf("NOT_CONTACTED", "CONTACTED", "NOT_CONTACTED"),
            expertTypes = listOf("ACADEMIC", "PRODUCTION_RND", "ACADEMIC"),
            gateEsFields = listOf("researchFields", "institution", "researchFields")
        )
        val reordered = scope.copy(
            funnelLevels = linkedSetOf("CANDIDATE", "APPLICATION"),
            excludeTags = listOf("A", "B"),
            tags = listOf("alpha", "beta"), regions = listOf("East Asia", "Europe"),
            emailDomains = listOf("a.example", "z.example"),
            operatorStatuses = listOf("CONTACTED", "NOT_CONTACTED"),
            expertTypes = listOf("PRODUCTION_RND", "ACADEMIC"),
            gateEsFields = listOf("institution", "researchFields")
        )
        val first = BatchRecipientStatisticsContext(baseSnapshot, scope)
        val second = BatchRecipientStatisticsContext(baseSnapshot.copy(
            emailVerificationAllowedStates = listOf("risky", "deliverable", "unknown")), reordered)

        assertEquals(cache.cacheKey(first, "fp"), cache.cacheKey(second, "fp"))
        assertEquals(cache.cacheKey(first, "fp"), cache.cacheKey(second.copy(
            snapshot = second.snapshot.copy(emailVerificationAllowedStates = null)), "fp"))
        assertTrue(cache.cacheKey(first, "fp").matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `each effective filter gate verification policy mail type and template changes the key`() {
        val base = context(snapshot())
        val changed = linkedMapOf(
            "funnel levels" to base.copy(scope = base.scope.copy(funnelLevels = setOf("APPLICATION"))),
            "tags" to base.copy(scope = base.scope.copy(tags = listOf("new"))),
            "exclude tags" to base.copy(scope = base.scope.copy(excludeTags = listOf("微软系邮箱"))),
            "regions" to base.copy(scope = base.scope.copy(regions = listOf("Europe"))),
            "email domains" to base.copy(scope = base.scope.copy(emailDomains = listOf("example.edu"))),
            "discipline" to base.copy(scope = base.scope.copy(discipline = "ENGINEERING")),
            "operator statuses" to base.copy(scope = base.scope.copy(operatorStatuses = listOf("NOT_CONTACTED"))),
            "expert types" to base.copy(scope = base.scope.copy(expertTypes = listOf("ACADEMIC"))),
            "hasFields" to base.copy(scope = base.scope.copy(gateEsFields = listOf("institution"))),
            "research direction" to base.copy(scope = base.scope.copy(researchDirectionFilter = "PRESENT")),
            "historical verification" to base.copy(scope = base.scope.copy(excludeVerifiedUnavailableEmails = true)),
            "gate switch" to base.copy(snapshot = base.snapshot.copy(gateFilterEnabled = true)),
            "live verification" to base.copy(snapshot = base.snapshot.copy(emailVerificationEnabled = true)),
            "allowed states" to base.copy(snapshot = base.snapshot.copy(emailVerificationAllowedStates = listOf("deliverable"))),
            "empty allowed states" to base.copy(snapshot = base.snapshot.copy(emailVerificationAllowedStates = emptyList())),
            "mail type" to base.copy(snapshot = base.snapshot.copy(mailType = "MATERIAL_REMINDER"),
                scope = base.scope.copy(mailType = "MATERIAL_REMINDER")),
            "resolved template id" to base.copy(snapshot = base.snapshot.copy(templateId = 8L))
        )
        val key = cache.cacheKey(base, "template-v1")
        changed.forEach { (label, candidate) ->
            assertNotEquals(key, cache.cacheKey(candidate, "template-v1"), label)
        }
        assertNotEquals(key, cache.cacheKey(base, "template-v2"), "template content fingerprint")
    }

    @Test
    fun `key uses resolved effective scope rather than raw request lists or execution pacing`() {
        val base = context(snapshot())
        val rawChanges = base.snapshot.copy(
            funnelLevel = "APPLICATION", tags = listOf("ignored-raw-tag"), regions = listOf("Europe"),
            emailDomains = listOf("ignored.example"), discipline = "IGNORED", operatorStatuses = listOf("CONTACTED"),
            expertTypes = listOf("ACADEMIC"), researchDirectionFilter = "PRESENT",
            excludeVerifiedUnavailableEmails = true,
            roundSize = 900, roundsPerRun = 8, perMailIntervalMs = 9000,
            perRoundIntervalMs = 90000, selfCheckTtlMinutes = 80, oneRoundOnly = true,
            senderAccountCodes = listOf("another-sender")
        )
        assertEquals(cache.cacheKey(base, "fp"),
            cache.cacheKey(BatchRecipientStatisticsContext(rawChanges, base.scope), "fp"))
        assertEquals(0, computations.get())
    }

    @Test
    fun `resolved scope and default template identity are used for actual cache reuse`() {
        val effectiveScope = RecipientScope.fromSnapshot(snapshot()).copy(gateEsFields = listOf("institution"))
        resolveContext = { BatchRecipientStatisticsContext(it, effectiveScope) }
        val first = cache.statistics(snapshot().copy(templateId = null, tags = listOf("raw-a")))
        val reused = cache.statistics(snapshot().copy(tags = listOf("raw-b"), roundSize = 900))
        assertEquals(first, reused)
        assertEquals(1, computations.get())

        val changed = effectiveScope.copy(gateEsFields = listOf("institution", "researchFields"))
        resolveContext = { BatchRecipientStatisticsContext(it, changed) }
        val miss = cache.statistics(snapshot(), cacheOnly = true)
        assertEquals(BatchRecipientStatisticsState.MISS, miss.state)
        assertNotEquals(first.cacheKey, miss.cacheKey)
        assertEquals(1, computations.get())
    }

    @Test
    fun `changed template fingerprint or resolved default id never reuses prior result`() {
        val first = cache.statistics(snapshot().copy(templateId = null))
        fingerprint = { id, _ -> TemplateStatisticsFingerprint(id ?: 7L, "template-v2") }
        val contentChanged = cache.statistics(snapshot().copy(templateId = null), cacheOnly = true)
        assertEquals(BatchRecipientStatisticsState.MISS, contentChanged.state)
        assertNotEquals(first.cacheKey, contentChanged.cacheKey)
        fingerprint = { id, _ -> TemplateStatisticsFingerprint(id ?: 8L, "template-v1") }
        val defaultChanged = cache.statistics(snapshot().copy(templateId = null), cacheOnly = true)
        assertEquals(BatchRecipientStatisticsState.MISS, defaultChanged.state)
        assertNotEquals(first.cacheKey, defaultChanged.cacheKey)
        assertNull(defaultChanged.summary)
        assertEquals(1, computations.get())
    }

    @Test
    fun `process restart creates empty cache instead of ready zero or persistent state`() {
        val original = cache.statistics(snapshot())
        val restarted = newCache().statistics(snapshot(), cacheOnly = true)

        assertEquals(BatchRecipientStatisticsState.MISS, restarted.state)
        assertEquals(original.cacheKey, restarted.cacheKey)
        assertNull(restarted.summary)
        assertNull(restarted.cachedAt)
        assertNull(restarted.errorCode)
        assertEquals(1, computations.get())
    }

    private fun newCache() = BatchRecipientStatisticsCache(outreach, templates, mapper).also { it.clock = clock }

    private fun snapshot(templateId: Long = 7L) = BatchExecutionSnapshot(
        mailType = BatchSendType.INTRODUCTION.name, roundSize = 30,
        perMailIntervalMs = 0, perRoundIntervalMs = 0, selfCheckTtlMinutes = 10,
        funnelLevel = "CANDIDATE", expertTypes = listOf("PRODUCTION_RND"), templateId = templateId
    )

    private fun context(value: BatchExecutionSnapshot) = BatchRecipientStatisticsContext(value, RecipientScope.fromSnapshot(value))
    private fun summary(total: Int = 100) = PendingOutreachSummary(pending = total, retryable = 0, totalSendable = total)
    private fun anySnapshot(): BatchExecutionSnapshot = Mockito.any(BatchExecutionSnapshot::class.java) ?: snapshot()
    private fun anyContext(): BatchRecipientStatisticsContext = Mockito.any(BatchRecipientStatisticsContext::class.java) ?: context(snapshot())

    private fun await(latch: CountDownLatch) {
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for controlled concurrent computation")
    }

    private fun awaitParked(threads: List<Thread>, expected: Int) {
        // All callers passed statisticsContext; one owns the controlled scan and the rest must join its future.
        // Waiting for parked callers avoids releasing the scan while a refresh caller has yet to join.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (threads.size == expected && threads.all {
                    it.state == Thread.State.WAITING || it.state == Thread.State.TIMED_WAITING
                }) return
            Thread.yield()
        }
        throw AssertionError("Concurrent refresh callers did not all join the in-flight computation")
    }

    private fun stop(executor: ExecutorService) {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Cache test executor did not terminate")
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
