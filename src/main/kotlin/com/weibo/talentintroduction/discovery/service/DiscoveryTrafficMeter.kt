package com.weibo.talentintroduction.discovery.service

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/** Bytes actually read from HTTP response bodies by one discovery execution. */
data class DiscoveryTrafficSnapshot(
    val totalBytes: Long,
    val metadataBytes: Long,
    val fulltextBytes: Long,
    val bySource: Map<String, Long>,
    val byHost: Map<String, Long>,
    val discardedBytes: Long = 0,
    val oversizedBytes: Long = 0,
    val oversizedDownloads: Long = 0
)

object DiscoveryTrafficMeter {
    class Session {
        private val metadata = LongAdder()
        private val fulltext = LongAdder()
        private val discarded = LongAdder()
        private val oversized = LongAdder()
        private val oversizedCount = LongAdder()
        private val sources = ConcurrentHashMap<String, LongAdder>()
        private val hosts = ConcurrentHashMap<String, LongAdder>()

        internal fun add(source: String, host: String, category: Category, bytes: Long, wasDiscarded: Boolean) {
            if (bytes <= 0) return
            if (category == Category.FULLTEXT) fulltext.add(bytes) else metadata.add(bytes)
            if (wasDiscarded) discarded.add(bytes)
            sources.computeIfAbsent(source) { LongAdder() }.add(bytes)
            hosts.computeIfAbsent(host) { LongAdder() }.add(bytes)
        }

        internal fun markOversized(bytes: Long) {
            oversizedCount.increment()
            oversized.add(bytes.coerceAtLeast(0))
        }

        fun snapshot(): DiscoveryTrafficSnapshot {
            val metadataBytes = metadata.sum()
            val fulltextBytes = fulltext.sum()
            return DiscoveryTrafficSnapshot(
                totalBytes = metadataBytes + fulltextBytes,
                metadataBytes = metadataBytes,
                fulltextBytes = fulltextBytes,
                bySource = sources.mapValues { it.value.sum() }.toSortedMap(),
                byHost = hosts.entries.sortedByDescending { it.value.sum() }.take(10)
                    .associate { it.key to it.value.sum() },
                discardedBytes = discarded.sum(),
                oversizedBytes = oversized.sum(),
                oversizedDownloads = oversizedCount.sum()
            )
        }
    }

    enum class Category { METADATA, FULLTEXT }
    private class Scope(val session: Session, val source: String, val category: Category) {
        private val bytes = LongAdder()

        fun add(host: String, count: Long, discarded: Boolean = false) {
            if (count <= 0) return
            bytes.add(count)
            session.add(source, host, category, count, discarded)
        }

        fun totalBytes(): Long = bytes.sum()
    }
    private val current = ThreadLocal<Scope?>()

    fun newSession() = Session()

    fun currentSession(): Session? = current.get()?.session

    fun currentScopeBytes(): Long = current.get()?.totalBytes() ?: 0

    /** Bytes consumed while closing a partial response or draining an HTTP redirect body. */
    fun recordDiscarded(host: String, bytes: Long) {
        current.get()?.add(host, bytes, discarded = true)
    }

    fun recordOversized(bytes: Long) {
        current.get()?.session?.markOversized(bytes)
    }

    fun <T> measure(session: Session, source: String, category: Category, block: () -> T): T {
        val previous = current.get()
        current.set(Scope(session, source, category))
        return try { block() } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /** Put this after retry interceptors so every returned attempt is counted once. */
    val interceptor = ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray,
                                                  execution: ClientHttpRequestExecution ->
        val scope = current.get()
        val response = execution.execute(request, body)
        if (scope == null) response else MeteredResponse(response, scope, request.uri.host ?: "unknown")
    }

    private class MeteredResponse(
        private val delegate: ClientHttpResponse,
        private val scope: Scope,
        private val host: String
    ) : ClientHttpResponse by delegate {
        private val meteredBody: InputStream by lazy {
            object : FilterInputStream(delegate.body) {
                private var position = 0L
                private var highWatermark = 0L
                private var markedPosition = 0L

                private fun countNewBytes(length: Long) {
                    position += length
                    val newlyRead = (position - highWatermark).coerceAtLeast(0L)
                    highWatermark = maxOf(highWatermark, position)
                    scope.add(host, newlyRead)
                }

                override fun read(): Int {
                    val value = super.read()
                    if (value >= 0) countNewBytes(1)
                    return value
                }

                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    // FilterInputStream.read(byte[],...) delegates straight to the wrapped stream.
                    val count = `in`.read(bytes, offset, length)
                    if (count > 0) countNewBytes(count.toLong())
                    return count
                }

                override fun skip(length: Long): Long {
                    val skipped = `in`.skip(length)
                    if (skipped > 0) countNewBytes(skipped)
                    return skipped
                }

                override fun mark(readlimit: Int) {
                    `in`.mark(readlimit)
                    markedPosition = position
                }

                override fun reset() {
                    `in`.reset()
                    position = markedPosition
                }
            }
        }

        override fun getBody(): InputStream = meteredBody
    }
}
