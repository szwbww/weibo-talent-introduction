package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenAlexRequestPolicyTest {

    private class FakeTime(
        var current: Instant = Instant.parse("2026-09-21T02:41:17Z")
    ) : PolicyTimeSource {
        var nanos: Long = 0
        val sleeps = CopyOnWriteArrayList<Long>()

        override fun now(): Instant = synchronized(this) { current }

        override fun nanoTime(): Long = synchronized(this) { nanos }

        override fun sleep(ms: Long) {
            synchronized(this) {
                sleeps += ms
                nanos += ms * 1_000_000L
                current = current.plusMillis(ms)
            }
        }
    }

    private val time = FakeTime()

    // 5/s would add a 200ms slot to every assertion; the rate limit itself is asserted in its own test.
    private fun properties(
        apiKey: String = "",
        dailyBudgetUsd: Double = 0.0,
        maxRequestsPerSecond: Double = 100.0,
        reserveRatio: Double = 0.2
    ) = OpenAlexProperties(
        apiKey = apiKey,
        dailyBudgetUsd = dailyBudgetUsd,
        maxRequestsPerSecond = maxRequestsPerSecond,
        newEnrichmentReserveRatio = reserveRatio
    )

    private fun policy(
        apiKey: String = "",
        dailyBudgetUsd: Double = 0.0,
        maxRequestsPerSecond: Double = 100.0,
        reserveRatio: Double = 0.2
    ) = OpenAlexRequestPolicy(
        properties(apiKey, dailyBudgetUsd, maxRequestsPerSecond, reserveRatio),
        time
    )

    private fun quotaHeaders(
        creditsUsed: Long? = 1L,
        remaining: Long? = null,
        limit: Long? = null,
        resetSeconds: Long? = null,
        retryAfterSeconds: Long? = null,
        prepaidRemainingUsd: String? = null
    ): HttpHeaders {
        val headers = HttpHeaders()
        creditsUsed?.let { headers.set(OpenAlexRequestPolicy.CREDITS_USED_HEADER, it.toString()) }
        remaining?.let { headers.set(OpenAlexRequestPolicy.REMAINING_HEADER, it.toString()) }
        limit?.let { headers.set(OpenAlexRequestPolicy.LIMIT_HEADER, it.toString()) }
        resetSeconds?.let { headers.set(OpenAlexRequestPolicy.RESET_HEADER, it.toString()) }
        retryAfterSeconds?.let { headers.set(OpenAlexRequestPolicy.RETRY_AFTER_HEADER, it.toString()) }
        prepaidRemainingUsd?.let { headers.set("X-RateLimit-Prepaid-Remaining-USD", it) }
        return headers
    }

    /** Spends the budget the way a real caller does: reserve a slot, then reconcile with the provider cost header. */
    private fun consume(policy: OpenAlexRequestPolicy, kind: RequestKind, requests: Int) {
        repeat(requests) {
            assertEquals(Permit.Allowed, policy.beforeRequest(kind))
            policy.recordResponse(quotaHeaders())
        }
    }

    @Test
    fun `keyless budget is the 0_10 dollar free budget and is never unlimited (I-2)`() {
        val policy = policy()

        assertEquals(1_000, policy.remainingCredits())
        consume(policy, RequestKind.NEW_ENRICHMENT, 1_000)

        assertEquals(0, policy.remainingCredits())
        assertTrue(policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Deferred)
    }

    @Test
    fun `keyed budget is ten times the keyless free budget (I-2)`() {
        val policy = policy(apiKey = "test-key")

        assertEquals(10_000, policy.remainingCredits())
        consume(policy, RequestKind.NEW_ENRICHMENT, 1_000)
        assertEquals(9_000, policy.remainingCredits())
    }

    @Test
    fun `provider remaining and limit headers cap the configured budget (I-2)`() {
        // Keyed cap is $1 (10,000 credits), but the account is really on the keyless allowance.
        val policy = policy(apiKey = "test-key")
        policy.recordResponse(quotaHeaders(remaining = 900, limit = 1_000))

        assertEquals(900, policy.remainingCredits())
    }

    @Test
    fun `discovery and history backfill stop at the share reserved for new-expert enrichment (I-3, V-3)`() {
        val policy = policy()
        consume(policy, RequestKind.DISCOVERY, 800)

        // 200 of 1,000 credits left = exactly the reserved 20%: only new-expert enrichment may continue.
        assertEquals(200, policy.remainingCredits())
        assertTrue(policy.beforeRequest(RequestKind.DISCOVERY) is Permit.Deferred)
        assertTrue(policy.beforeRequest(RequestKind.HISTORY_ENRICHMENT) is Permit.Deferred)
        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.NEW_ENRICHMENT))
    }

    @Test
    fun `deferred permit exposes the actual UTC reset (V-2)`() {
        val policy = policy()
        consume(policy, RequestKind.DISCOVERY, 800)

        val deferred = policy.beforeRequest(RequestKind.DISCOVERY) as Permit.Deferred
        assertEquals(Instant.parse("2026-09-22T00:00:00Z"), deferred.resetAt)
    }

    @Test
    fun `exhausted day budget defers every consumer without dense retries (I-2, V-2)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 3_600))

        val reset = Instant.parse("2026-09-21T03:41:17Z")
        assertEquals(reset, (policy.beforeRequest(RequestKind.DISCOVERY) as Permit.Deferred).resetAt)
        assertEquals(reset, (policy.beforeRequest(RequestKind.NEW_ENRICHMENT) as Permit.Deferred).resetAt)
        assertEquals(reset, (policy.beforeRequest(RequestKind.HISTORY_ENRICHMENT) as Permit.Deferred).resetAt)
        assertEquals(0, policy.remainingCredits())
        assertTrue(time.sleeps.isEmpty(), "an exhausted budget must not spin or back off, only wait for the reset")
    }

    @Test
    fun `budget is rebuilt after the UTC reset (V-3)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 10))
        assertTrue(policy.beforeRequest(RequestKind.DISCOVERY) is Permit.Deferred)

        time.current = Instant.parse("2026-09-22T00:00:05Z")

        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.DISCOVERY))
        assertEquals(1_000, policy.remainingCredits())
        assertEquals(0, policy.listRequestCount())
    }

    @Test
    fun `paid prepaid balance is never consumed automatically (I-2)`() {
        val policy = policy()
        policy.recordResponse(
            quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 3_600, prepaidRemainingUsd = "5.0")
        )

        assertTrue(policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Deferred)
        assertEquals(0, policy.remainingCredits())
    }

    @Test
    fun `request-rate 429 backs off with a bounded delay and recovers after a success (I-2)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000, retryAfterSeconds = 3))
        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.DISCOVERY))
        assertEquals(listOf(3_000L), time.sleeps.toList())

        // Retry-After absent: exponential from 1s, doubling, never above the configured cap.
        var backoff = 1_000L
        var observedSleeps = time.sleeps.size
        repeat(8) {
            policy.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000))
            backoff = (backoff * 2).coerceAtMost(60_000)
            assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.DISCOVERY))
            assertEquals(backoff, time.sleeps[observedSleeps], "backoff must grow but stay bounded")
            observedSleeps = time.sleeps.size
        }
        assertEquals(60_000L, time.sleeps.last(), "backoff is capped by rate-limit-backoff-max-ms")

        val sleepsBeforeSuccess = time.sleeps.size
        policy.recordResponse(quotaHeaders())
        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.DISCOVERY))
        assertEquals(10L, time.sleeps.last(), "a successful response must clear the rate-limit backoff")
        assertEquals(sleepsBeforeSuccess + 1, time.sleeps.size)
    }

    @Test
    fun `request rate is configurable and clamped to the official 100 per second (I-3)`() {
        assertEquals(5.0, policy(maxRequestsPerSecond = 5.0).effectiveMaxRequestsPerSecond)
        assertEquals(100.0, policy(maxRequestsPerSecond = 500.0).effectiveMaxRequestsPerSecond)
        assertEquals(0.01, policy(maxRequestsPerSecond = 0.0).effectiveMaxRequestsPerSecond)

        assertEquals(listOf(200L), slots(5.0))
        assertEquals(listOf(10L), slots(500.0))
        assertEquals(listOf(100_000L), slots(0.0))
    }

    /** Delay the policy inserts between two back-to-back requests at the given configured rate. */
    private fun slots(maxRequestsPerSecond: Double): List<Long> {
        val recorder = FakeTime()
        val recorded = OpenAlexRequestPolicy(
            OpenAlexProperties(apiKey = "", maxRequestsPerSecond = maxRequestsPerSecond),
            recorder
        )
        recorded.beforeRequest(RequestKind.DISCOVERY)
        recorded.beforeRequest(RequestKind.DISCOVERY)
        return recorder.sleeps.toList()
    }

    @Test
    fun `list calls and fulltext downloads are counted and charged separately (I-3)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 1_000, limit = 1_000))
        policy.recordResponse(quotaHeaders(creditsUsed = 100, remaining = 900, limit = 1_000))

        assertEquals(1, policy.listRequestCount())
        assertEquals(1, policy.fulltextDownloadCount())
        // 1,000 - 1 list credit - 100 content credits.
        assertEquals(899, policy.remainingCredits())
    }

    @Test
    fun `a request without a response releases its reservation and stays conservative (I-2)`() {
        val policy = policy()
        assertEquals(Permit.Allowed, policy.beforeRequest(RequestKind.NEW_ENRICHMENT))
        assertEquals(1_000, policy.remainingCredits())

        policy.recordResponse(HttpHeaders())

        assertEquals(999, policy.remainingCredits())
        assertEquals(0, policy.listRequestCount())
    }

    @Test
    fun `concurrent callers share one budget and only one is admitted for the last credit (V-2)`() {
        val policy = policy(dailyBudgetUsd = 0.0001, reserveRatio = 0.0)
        assertEquals(1, policy.remainingCredits())

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map {
                pool.submit<Boolean> {
                    start.await()
                    policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Allowed
                }
            }
            start.countDown()
            val allowed = futures.count { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, allowed, "only one caller may be admitted from the last credit")
        } finally {
            pool.shutdownNow()
        }
    }
}
