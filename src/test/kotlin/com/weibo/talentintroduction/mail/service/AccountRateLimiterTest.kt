package com.weibo.talentintroduction.mail.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccountRateLimiterTest {
    private val limiter = AccountRateLimiter()
    private val baseIntervalMs = 1000L

    @BeforeEach
    fun setUp() {
        limiter.clear()
    }

    @Test
    fun `recordThrottled doubles interval from base`() {
        limiter.recordThrottled("chen", baseIntervalMs)
        assertEquals(2000L, limiter.getIntervalMs("chen", baseIntervalMs))
    }

    @Test
    fun `recordThrottled five times reaches 32x capped at MAX_INTERVAL_MS`() {
        repeat(5) { limiter.recordThrottled("chen", baseIntervalMs) }
        assertEquals(32_000L, limiter.getIntervalMs("chen", baseIntervalMs))
    }

    @Test
    fun `recordSuccess ten times recovers one backoff level`() {
        limiter.recordThrottled("chen", baseIntervalMs)
        limiter.recordThrottled("chen", baseIntervalMs)
        assertEquals(4000L, limiter.getIntervalMs("chen", baseIntervalMs))
        repeat(AccountRateLimiter.RECOVERY_THRESHOLD) {
            limiter.recordSuccess("chen", baseIntervalMs)
        }
        assertEquals(2000L, limiter.getIntervalMs("chen", baseIntervalMs))
    }

    @Test
    fun `getIntervalMs never below baseIntervalMs`() {
        limiter.recordThrottled("chen", 500L)
        assertEquals(1000L, limiter.getIntervalMs("chen", 1000L))
    }

    @Test
    fun `clear resets to base interval`() {
        limiter.recordThrottled("chen", baseIntervalMs)
        limiter.clear()
        assertEquals(baseIntervalMs, limiter.getIntervalMs("chen", baseIntervalMs))
        assertEquals(emptyMap<String, Long>(), limiter.getSnapshot())
    }
}
