package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.archive.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PaperBodyComputationCacheTest {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    private fun key(sha: String = "a", kind: ArchiveAttemptKind = ArchiveAttemptKind.EMBEDDED_TEXT,
        authors: String = "[]", year: Int? = 2023, version: String = "v1") =
        PaperComputationKey(sha,kind,authors,year,version)
    private fun value(status: PaperEvidenceStatus = PaperEvidenceStatus.FOUND) =
        PaperBodyComputation("TEXT",emptyList(),PaperQualificationResult(emptyList(),PaperAnalysisCoverage(status)),emptyList())

    @Test fun `key dimensions miss and returned value is independent`() {
        val cache = PaperBodyComputationCache(mapper)
        val count = AtomicInteger()
        val keys = listOf(key(),key(kind=ArchiveAttemptKind.PMC_XML),key(authors="[1]"),
            key(year=2024),key(version="v2"),key(sha="b"))
        keys.forEach { cache.getOrCompute(it) { count.incrementAndGet(); value() } }
        assertEquals(6,count.get())
        val first = cache.getOrCompute(key()) { count.incrementAndGet(); value() }
        (first.reasons as MutableList<String>).add("caller mutation")
        assertTrue(cache.getOrCompute(key()) { count.incrementAndGet(); value() }.reasons.isEmpty())
        assertEquals(6,count.get())
    }

    @Test fun `same key concurrent callers coalesce and retain complete computation`() {
        val cache = PaperBodyComputationCache(mapper)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val one = pool.submit<PaperBodyComputation> { cache.getOrCompute(key()) {
                count.incrementAndGet(); entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); value()
            } }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val two = pool.submit<PaperBodyComputation> { cache.getOrCompute(key()) { count.incrementAndGet(); value() } }
            assertThrows(TimeoutException::class.java) { two.get(100,TimeUnit.MILLISECONDS) }
            release.countDown()
            assertEquals(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS))
            assertEquals(1,count.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun `failed and partial results are retried and LRU and byte limits evict`() {
        val cache = PaperBodyComputationCache(mapper,maxEntries=2)
        val count = AtomicInteger()
        assertThrows(IllegalStateException::class.java) { cache.getOrCompute(key()) { count.incrementAndGet(); error("parse failed") } }
        cache.getOrCompute(key()) { count.incrementAndGet(); value(PaperEvidenceStatus.PARTIAL) }
        cache.getOrCompute(key()) { count.incrementAndGet(); value() }
        cache.getOrCompute(key("b")) { count.incrementAndGet(); value() }
        cache.getOrCompute(key()) { count.incrementAndGet(); value() } // Refresh a.
        cache.getOrCompute(key("c")) { count.incrementAndGet(); value() } // Evict b.
        cache.getOrCompute(key("b")) { count.incrementAndGet(); value() }
        assertEquals(6,count.get())
        val tiny = PaperBodyComputationCache(mapper,maxBytes=1)
        tiny.getOrCompute(key()) { count.incrementAndGet(); value() }
        tiny.getOrCompute(key()) { count.incrementAndGet(); value() }
        assertEquals(8,count.get())
        val serializedSize = mapper.writeValueAsBytes(value()).size
        val byteBounded = PaperBodyComputationCache(mapper,maxBytes=serializedSize * 2 - 1)
        byteBounded.getOrCompute(key()) { count.incrementAndGet(); value() }
        byteBounded.getOrCompute(key("b")) { count.incrementAndGet(); value() }
        byteBounded.getOrCompute(key()) { count.incrementAndGet(); value() }
        assertEquals(11,count.get())
    }

    @Test fun `saturated in flight capacity computes overflow without retaining it`() {
        val cache = PaperBodyComputationCache(mapper,maxInFlight=2)
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val busy = listOf("a","b").map { sha -> pool.submit<PaperBodyComputation> {
                cache.getOrCompute(key(sha)) { count.incrementAndGet(); entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); value() }
            } }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            cache.getOrCompute(key("overflow")) { count.incrementAndGet(); value() }
            cache.getOrCompute(key("overflow")) { count.incrementAndGet(); value() }
            assertEquals(4,count.get())
            release.countDown()
            busy.forEach { it.get(5,TimeUnit.SECONDS) }
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun `interrupted waiter exits without cancelling owner computation`() {
        val cache = PaperBodyComputationCache(mapper)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val waiterInterrupted = java.util.concurrent.atomic.AtomicBoolean()
        val pool = Executors.newSingleThreadExecutor()
        try {
            val owner = pool.submit<PaperBodyComputation> { cache.getOrCompute(key()) {
                count.incrementAndGet(); entered.countDown(); assertTrue(release.await(5,TimeUnit.SECONDS)); value()
            } }
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            val failure = AtomicReference<Throwable?>()
            val waiter = Thread {
                try { cache.getOrCompute(key()) { count.incrementAndGet(); value() } }
                catch (error: Throwable) { failure.set(error); waiterInterrupted.set(Thread.currentThread().isInterrupted) }
            }
            waiter.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (waiter.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.yield()
            assertEquals(Thread.State.WAITING,waiter.state)
            waiter.interrupt(); waiter.join(5000)
            assertFalse(waiter.isAlive)
            assertTrue(failure.get() is CancellationException)
            assertTrue(failure.get()?.cause is InterruptedException)
            assertTrue(waiterInterrupted.get())
            release.countDown(); owner.get(5,TimeUnit.SECONDS)
            cache.getOrCompute(key()) { count.incrementAndGet(); value() }
            assertEquals(1,count.get())
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
