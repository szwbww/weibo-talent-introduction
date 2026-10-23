package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.sample.DiscoverySampleContext
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

class PaperArchiveBlockedException(val code: String, cause: Throwable? = null) :
    IllegalStateException("论文原文留存受阻，本页尚未消费 [$code]", cause)

@Component
class DiscoveryPaperArchiveCapture(private val service: DiscoveryPaperArchiveService, private val mapper: ObjectMapper) {
    private class Attempt(val id: Long) {
        var status: Int? = null
        var mediaType: String = "text/plain"
        var finished = false
    }
    private class Scope(val owner: DiscoveryPaperArchiveCapture, val permit: DiscoveryPaperArchiveService.ScopePermit) {
        val extractionId: UUID = UUID.randomUUID()
        val attempts = mutableListOf<Attempt>()
        var firstFatal: PaperArchiveBlockedException? = null
        fun <T> storage(action: () -> T): T {
            firstFatal?.let { throw it }
            try { return action() } catch (e: Exception) {
                val blocked = if (e is PaperArchiveBlockedException) e else PaperArchiveBlockedException(
                    (e as? ArchiveException)?.code ?: "STORAGE_UNAVAILABLE", e)
                firstFatal = blocked
                throw blocked
            }
        }
    }
    companion object {
        private val current = ThreadLocal<Scope>()
        fun hasScope(): Boolean = current.get() != null
        fun beginAttempt(kind: ArchiveAttemptKind, url: String?): Any? {
            val scope = current.get() ?: return null
            return scope.storage {
                val a = scope.owner.service.beginScopeAttempt(scope.permit, scope.extractionId, scope.attempts.size + 1, kind, url)
                Attempt(a.id).also { scope.attempts.add(it) }
            }
        }
        private fun observe(token: Any?, action: (Scope, Attempt) -> Unit) {
            val scope = current.get() ?: return
            val attempt = token as? Attempt ?: return
            if (scope.attempts.none { it === attempt }) return
            scope.storage { action(scope, attempt) }
        }
        fun headers(token: Any?, status: Int?, mediaType: String?) = observe(token) { _, a ->
            a.status = status
            if (mediaType != null) a.mediaType = mediaType
        }
        fun fullBody(token: Any?, bytes: ByteArray) = observe(token) { scope, a ->
            if (!a.finished) {
                scope.owner.service.saveScopeBody(scope.permit, a.id, bytes, a.mediaType)
                a.finished = true
            }
        }
        fun endAttempt(token: Any?, error: Throwable? = null, tooLarge: Boolean = false) {
            val scope = current.get() ?: return
            val a = token as? Attempt ?: return
            if (scope.attempts.none { it === a } || a.finished) return
            // Cleanup must still run after firstFatal; do not erase the original infrastructure error.
            try {
                val status = when { scope.firstFatal != null -> ArchiveAttemptStatus.SAVE_ERROR
                    tooLarge -> ArchiveAttemptStatus.TOO_LARGE
                    else -> ArchiveAttemptStatus.DOWNLOAD_FAILED }
                scope.owner.service.finishAttempt(a.id, status,
                    scope.firstFatal?.code ?: if (tooLarge) "TOO_LARGE" else "DOWNLOAD_FAILED", a.status)
                a.finished = true
            } catch (e: Exception) {
                if (scope.firstFatal == null) scope.firstFatal = PaperArchiveBlockedException((e as? ArchiveException)?.code ?: "STORAGE_UNAVAILABLE", e)
            }
        }
    }
    fun capture(context: DiscoverySampleContext?, paperKey: String, paper: PaperMetadata, delegate: () -> EmailExtractionOutcome): EmailExtractionOutcome {
        if (context == null || context.executionId <= 0 || context.source !in SOURCES) return delegate()
        val metadata = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(paper.copy(fullText = null))
        metadata.put("schemaVersion", 1)
        metadata.put("entry", context.entry.name)
        context.sourceBatchNumber?.let { metadata.put("batchNumber", it) }
        context.queueJobId?.let { metadata.put("queueJobId", it) }
        val permit = try { service.beginScope(context.executionId, context.source, paperKey, metadata) }
            catch (e: Exception) { throw PaperArchiveBlockedException((e as? ArchiveException)?.code ?: "STORAGE_UNAVAILABLE", e) }
            ?: return delegate()
        val previous = current.get()
        val scope = Scope(this, permit)
        current.set(scope)
        var failure: Throwable? = null
        try {
            if (context.source == "CORE" && !paper.fullText.isNullOrBlank()) {
                val a = beginAttempt(ArchiveAttemptKind.EMBEDDED_TEXT, null)
                fullBody(a, paper.fullText.toByteArray(Charsets.UTF_8))
                endAttempt(a)
            }
            val outcome = delegate()
            scope.firstFatal?.let { throw it }
            scope.storage {
                val snapshot = mapper.createObjectNode().put("capturedAt", Instant.now().toString())
                snapshot.set<JsonNode>("parserVersion", mapper.createObjectNode().put("identityExtraction", DiscoveryIdentity.EXTRACTION_VERSION).put("pdfLayout", 0))
                snapshot.set<JsonNode>("attemptIds", mapper.valueToTree(scope.attempts.map { it.id }))
                snapshot.set<JsonNode>("outcome", mapper.valueToTree(outcome))
                val envelope = mapper.createObjectNode().put("schemaVersion", 1)
                envelope.set<JsonNode>("first", snapshot)
                envelope.set<JsonNode>("latest", snapshot)
                service.finishExtraction(permit.recordId, envelope)
            }
            return outcome
        } catch (e: Throwable) {
            failure = e
            throw (scope.firstFatal ?: e)
        } finally {
            try {
                scope.attempts.forEach { endAttempt(it, failure) }
            } finally {
                service.endScope(permit)
                if (previous == null) current.remove() else current.set(previous)
            }
            if (failure == null) scope.firstFatal?.let { throw it }
        }
    }
    private val SOURCES = setOf("OPENALEX", "EUROPE_PMC", "PMC_OA", "CROSSREF", "CORE", "ARXIV")
}
