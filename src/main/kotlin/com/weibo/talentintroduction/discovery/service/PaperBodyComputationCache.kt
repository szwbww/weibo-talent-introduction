package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.archive.ArchiveAttemptKind
import com.weibo.talentintroduction.discovery.archive.PaperBodyComputation
import com.weibo.talentintroduction.discovery.archive.PaperEvidenceStatus
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

internal data class PaperComputationKey(val verifiedSha: String, val kind: ArchiveAttemptKind,
    val authors: String, val year: Int?, val versions: String)

/** Stores only serialized, complete values. Parsing and waiting run outside the monitor. */
internal class PaperBodyComputationCache(private val mapper: ObjectMapper,
    private val maxEntries: Int = 64, private val maxBytes: Int = 8 * 1024 * 1024,
    private val maxInFlight: Int = 8) {
    private val lock = Any()
    private val entries = LinkedHashMap<PaperComputationKey, ByteArray>(16, .75f, true)
    private val flights = mutableMapOf<PaperComputationKey, CompletableFuture<ByteArray>>()
    private var retainedBytes = 0
    private var participants = 0

    fun getOrCompute(key: PaperComputationKey, compute: () -> PaperBodyComputation): PaperBodyComputation {
        var hit: ByteArray? = null
        var flight: CompletableFuture<ByteArray>? = null
        var owner = false
        synchronized(lock) {
            hit = entries[key]
            if (hit == null && participants < maxInFlight) {
                flight = flights[key]
                if (flight == null) {
                    flight = CompletableFuture()
                    flights[key] = flight!!
                    owner = true
                }
                participants++
            }
        }
        hit?.let { return decode(it) }
        val active = flight ?: return compute() // Saturated: no cache insertion or waiting.
        if (!owner) {
            try {
                return try { decode(active.get()) }
                catch (error: ExecutionException) { throw (error.cause ?: error) }
                catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw CancellationException("Paper computation wait interrupted").apply { initCause(error) }
                }
            }
            finally { synchronized(lock) { participants-- } }
        }
        try {
            val value = compute()
            val serialized = mapper.writeValueAsBytes(value)
            if (cacheable(value) && serialized.size <= maxBytes) synchronized(lock) {
                entries.remove(key)?.let { retainedBytes -= it.size }
                while (entries.size >= maxEntries || retainedBytes + serialized.size > maxBytes) {
                    val oldest = entries.entries.iterator()
                    if (!oldest.hasNext()) break
                    val removed = oldest.next()
                    retainedBytes -= removed.value.size
                    oldest.remove()
                }
                if (maxEntries > 0) {
                    entries[key] = serialized
                    retainedBytes += serialized.size
                }
            }
            active.complete(serialized)
            return decode(serialized)
        } catch (error: Throwable) {
            active.completeExceptionally(error)
            throw error
        } finally {
            synchronized(lock) {
                flights.remove(key, active)
                participants--
            }
        }
    }

    private fun cacheable(value: PaperBodyComputation): Boolean =
        value.reasons.isEmpty() && value.qualification.coverage.reasons.isEmpty() &&
            value.qualification.coverage.status in setOf(PaperEvidenceStatus.FOUND, PaperEvidenceStatus.NOT_FOUND)

    private fun decode(bytes: ByteArray): PaperBodyComputation = mapper.readValue(bytes, PaperBodyComputation::class.java)
}
