package com.weibo.talentintroduction.discovery.sample

import com.weibo.talentintroduction.config.FulltextRequestGate
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.Semaphore

@Component
class DiscoverySampleCapture(private val store: DiscoverySampleStore, private val clock: Clock = Clock.systemUTC()) {
    private val permits = Semaphore(store.properties.maxInFlightCaptures.coerceIn(1, 2))
    private class Attempt(val sequence: Int, val kind: DiscoverySampleAttemptKind, val url: String,
        val settings: PdfExtractionProperties?, val started: Long = System.nanoTime()) {
        var status: Int? = null
        var contentType: String? = null
        var exceptionClass: String? = null
        var elapsed: Long = 0
        var bodyStatus = DiscoverySampleBodyStatus.NOT_AVAILABLE
        var bytes: ByteArray? = null
        var length: Long? = null
    }
    private class Scope(val owner: DiscoverySampleCapture) {
        val attempts = mutableListOf<Attempt>()
        var truncated = false
        var bodies = 0
    }
    companion object {
        private val current = ThreadLocal<Scope>()
        /** An opaque token is valid only on the thread and scope which created it. */
        fun beginAttempt(kind: DiscoverySampleAttemptKind, url: String, pdfSettings: PdfExtractionProperties? = null): Any? = try {
            current.get()?.let { scope ->
                if (scope.attempts.size >= scope.owner.store.properties.maxAttemptsPerSample) { scope.truncated = true; null }
                else Attempt(scope.attempts.size + 1, kind, url, pdfSettings).also { scope.attempts.add(it) }
            }
        } catch (_: Exception) { null }
        private fun attempt(token: Any?, block: (Scope, Attempt) -> Unit) {
            try { val scope = current.get() ?: return
                val value = token as? Attempt ?: return
                if (scope.attempts.any { it === value }) block(scope, value)
            } catch (_: Exception) { /* Diagnostic hooks never change business behavior. */ }
        }
        fun headers(token: Any?, httpStatus: Int?, contentType: String?) = attempt(token) { _, value ->
            value.status = httpStatus; value.contentType = contentType
        }
        fun fullBody(token: Any?, bytes: ByteArray) = attempt(token) { scope, value ->
            if (value.length != null || bytes.isEmpty()) return@attempt
            value.length = bytes.size.toLong()
            val properties = scope.owner.store.properties
            if (bytes.size > properties.maxBodyBytes || scope.bodies >= properties.maxBodiesPerSample)
                value.bodyStatus = DiscoverySampleBodyStatus.BODY_LIMIT
            else { value.bytes = bytes.copyOf(); scope.bodies++; value.bodyStatus = DiscoverySampleBodyStatus.SAVED }
        }
        fun endAttempt(token: Any?, error: Throwable? = null) = attempt(token) { _, value ->
            value.elapsed = ((System.nanoTime() - value.started) / 1000000).coerceAtLeast(0)
            value.exceptionClass = error?.javaClass?.name
        }
    }
    fun capture(context: DiscoverySampleContext?, paper: PaperMetadata, delegate: () -> EmailExtractionOutcome): EmailExtractionOutcome {
        if (!store.active()) return delegate()
        if (context == null || !context.valid()) { store.decision(DiscoverySampleDecision.INVALID_CONTEXT); return delegate() }
        if (!permits.tryAcquire()) { store.decision(DiscoverySampleDecision.CAPTURE_BUSY); return delegate() }
        val previous = current.get()
        val scope = Scope(this)
        current.set(scope)
        var outcome: EmailExtractionOutcome? = null
        var error: Throwable? = null
        try { return delegate().also { outcome = it } }
        catch (failure: Throwable) { error = failure; throw failure }
        finally {
            try {
                if (error is FulltextRequestGate.HostBusyException || FulltextRequestGate.hostBusyInScope()) store.decision(DiscoverySampleDecision.HOST_BUSY)
                else if (error == null || error is Exception) save(context, paper, scope, outcome, error)
            } catch (_: Exception) { store.decision(DiscoverySampleDecision.IO_ERROR) }
            finally { if (previous == null) current.remove() else current.set(previous); permits.release() }
        }
    }
    private fun save(context: DiscoverySampleContext, paper: PaperMetadata, scope: Scope, outcome: EmailExtractionOutcome?, error: Throwable?) {
        if (DiscoverySampleJson.bounded(paper, store.properties.maxMetadataBytes) == null) {
            store.decision(DiscoverySampleDecision.METADATA_LIMIT)
            return
        }
        val scrubbed = DiscoverySampleJson.paper(paper)
        val input = DiscoverySampleJson.hash(DiscoverySampleJson.canonical(scrubbed))
        val bodies = linkedMapOf<String, ByteArray>()
        val attempts = scope.attempts.map { value ->
            val file = value.bytes?.let { bytes -> "body-0${bodies.size + 1}.bin".also { bodies[it] = bytes } }
            DiscoverySampleAttempt(value.sequence, value.kind, DiscoverySampleJson.scrub(value.url), DiscoverySampleJson.hash(value.url),
                value.elapsed, value.status, value.contentType, value.exceptionClass, value.bodyStatus, file,
                value.bytes?.let(DiscoverySampleJson::hash), value.length, value.settings)
        }
        store.commit(DiscoverySampleManifest(sampleId = DiscoverySampleJson.hash("${context.executionId}\n${context.source}\n$input"),
            inputSha256 = input, capturedAt = clock.instant().toString(), context = context, paper = scrubbed,
            urlFingerprints = listOf(paper.downloadUrl?.let(DiscoverySampleJson::hash)) + paper.candidateDownloadUrls.map(DiscoverySampleJson::hash),
            bucket = DiscoverySampleJson.bucket(outcome, error), unresolvedAuthorEmailCount = outcome?.emails?.count {
                it.givenNames.isNullOrBlank() || it.familyNames.isNullOrBlank() } ?: 0,
            outcome = outcome, extractionExceptionClass = error?.javaClass?.name, attempts = attempts,
            attemptsTruncated = scope.truncated, bodyBytesRetained = bodies.values.sumOf { it.size.toLong() }), bodies)
    }
}
