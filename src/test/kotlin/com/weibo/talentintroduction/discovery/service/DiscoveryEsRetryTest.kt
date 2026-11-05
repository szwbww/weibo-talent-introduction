package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.PolicyTimeSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import java.time.Instant

class DiscoveryEsRetryTest {
    private class Time : PolicyTimeSource {
        var elapsed = 0L
        var onSleep: () -> Unit = {}
        override fun now(): Instant = Instant.EPOCH.plusMillis(elapsed)
        override fun sleep(ms: Long) { elapsed += ms; onSleep() }
    }

    @Test fun `three retries wait two five ten seconds then return success`() {
        val time = Time()
        val attempts = mutableListOf<Long>()
        val value = DiscoveryEsRetry.withContext(time, { false }) {
            DiscoveryEsRetry.execute("TEST") {
                attempts += time.elapsed
                if (attempts.size < 4) throw HttpClientErrorException(HttpStatus.FORBIDDEN)
                "ok"
            }
        }
        assertEquals("ok", value)
        assertEquals(listOf(0L, 2_000L, 7_000L, 17_000L), attempts)
    }

    @Test fun `exhaustion rethrows original error after exactly four requests`() {
        val time = Time(); var attempts = 0
        val forbidden = HttpClientErrorException(HttpStatus.FORBIDDEN)
        val thrown = assertThrows(HttpClientErrorException::class.java) {
            DiscoveryEsRetry.withContext(time, { false }) {
                DiscoveryEsRetry.execute("TEST") { attempts++; throw forbidden }
            }
        }
        assertSame(forbidden, thrown)
        assertEquals(4, attempts); assertEquals(17_000L, time.elapsed)
    }

    @Test fun `non forbidden failures never retry or mask conflicts`() {
        for (status in listOf(HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT,
            HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR)) {
            val time = Time(); var calls = 0
            val error = HttpClientErrorException(status)
            assertSame(error, assertThrows(HttpClientErrorException::class.java) {
                DiscoveryEsRetry.withContext(time, { false }) {
                    DiscoveryEsRetry.execute("TEST") { calls++; throw error }
                }
            })
            assertEquals(1, calls); assertEquals(0L, time.elapsed)
        }
    }

    @Test fun `cancel during wait starts no new request and removes context`() {
        val time = Time(); var calls = 0
        assertThrows(DiscoveryEsRetryStoppedException::class.java) {
            DiscoveryEsRetry.withContext(time, { time.elapsed >= 500L }) {
                DiscoveryEsRetry.execute("TEST") { calls++; throw HttpClientErrorException(HttpStatus.FORBIDDEN) }
            }
        }
        assertEquals(1, calls); assertEquals(500L, time.elapsed)
        assertEquals("next task", DiscoveryEsRetry.execute("NEXT") { "next task" })
    }

    @Test fun `interrupt during wait preserves interrupt flag and prevents retry`() {
        val time = Time().apply { onSleep = { throw InterruptedException() } }; var calls = 0
        try {
            assertThrows(DiscoveryEsRetryStoppedException::class.java) {
                DiscoveryEsRetry.withContext(time, { false }) {
                    DiscoveryEsRetry.execute("TEST") { calls++; throw HttpClientErrorException(HttpStatus.FORBIDDEN) }
                }
            }
            assertTrue(Thread.currentThread().isInterrupted); assertEquals(1, calls)
        } finally { Thread.interrupted() }
    }

    @Test fun `nested context restores parent cancellation guard`() {
        val time = Time()
        DiscoveryEsRetry.withContext(time, { true }) {
            assertEquals(1, DiscoveryEsRetry.withContext(time, { false }) { DiscoveryEsRetry.execute("INNER") { 1 } })
            assertThrows(DiscoveryEsRetryStoppedException::class.java) { DiscoveryEsRetry.execute("OUTER") { 2 } }
        }
    }
}
