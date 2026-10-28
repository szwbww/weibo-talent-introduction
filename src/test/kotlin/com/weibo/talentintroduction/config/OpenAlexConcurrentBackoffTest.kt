package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenAlexConcurrentBackoffTest {
    @Test fun `concurrent throttled responses retain capped backoff until success resets it`() {
        val now = Instant.parse("2026-10-28T00:00:00Z")
        val delays = CopyOnWriteArrayList<Long>()
        val delegate = InMemoryOpenAlexBudgetStore()
        val store = object : OpenAlexBudgetStore by delegate {
            override fun applyCooldown(accountScope: String, notBefore: Instant, now: Instant) {
                delays += Duration.between(now, notBefore).toMillis()
                delegate.applyCooldown(accountScope, notBefore, now)
            }
        }
        val time = object : PolicyTimeSource {
            override fun now() = now
            override fun sleep(ms: Long) { fail<Unit>("Backoff must not sleep") }
        }
        val policy = OpenAlexRequestPolicy(OpenAlexProperties(rateLimitBackoffMaxMs = 60000), time, store)
        fun throttled() = HttpHeaders().apply { set(OpenAlexRequestPolicy.REMAINING_HEADER, "500") }
        repeat(7) { policy.recordResponse(throttled()) }
        assertEquals(60000L, delays.last())
        delays.clear()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val work = (1..8).map { pool.submit {
                check(start.await(5, TimeUnit.SECONDS))
                repeat(32) { policy.recordResponse(throttled()) }
            } }
            start.countDown()
            work.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(256, delays.size, "Every throttled response must maintain cooldown")
            assertTrue(delays.all { it == 60000L }, "Backoff must stay capped without overflow or lost updates: $delays")
            policy.recordResponse(HttpHeaders().apply { set(OpenAlexRequestPolicy.CREDITS_USED_HEADER, "1") })
            policy.recordResponse(throttled())
            assertEquals(1000L, delays.last(), "Successful response resets the next backoff")
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
