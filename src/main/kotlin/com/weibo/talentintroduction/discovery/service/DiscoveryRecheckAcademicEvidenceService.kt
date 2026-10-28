package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.discovery.domain.RecheckEvidenceMode
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** Decision/display data only. No profile writer or admission service is a dependency. */
data class RecheckAcademicEvidence(val identityHash: String, val factFingerprint: String,
    val trustedId: String?, val data: AuthorEnrichment?, val sourceUrls: List<String>,
    val evaluatedAt: Instant, val reasonCodes: List<String>, val retained: Boolean,
    val profileResearchFields: String? = null, val retryAt: Instant? = null, val provider: String = "OPENALEX",
    val providerError: String? = null, val httpStatus: Int? = null)

@Service
class DiscoveryRecheckAcademicEvidenceService(private val clients: ObjectProvider<OpenAlexDataSource>,
    private val properties: OpenAlexProperties = OpenAlexProperties(),
    private val clock: java.time.Clock = java.time.Clock.systemUTC()) {
    private data class BatchKey(val executionId: Long, val mode: RecheckEvidenceMode, val evaluatedAt: Instant)
    private class Pending(val snapshot: VerificationSnapshot, val guard: () -> Unit) {
        val result = CompletableFuture<RecheckAcademicEvidence>()
    }
    private val closed = AtomicBoolean()
    private val lock = Any()
    private val pending = linkedMapOf<BatchKey, MutableList<Pending>>()
    private val outstanding = mutableSetOf<Pending>()
    private val dispatcher = Executors.newScheduledThreadPool(8) { runnable ->
        Thread(runnable, "discovery-academic-batch").apply { isDaemon = true }
    }
    private fun checkActive(item: Pending) {
        if (closed.get() || Thread.currentThread().isInterrupted || item.result.isDone)
            throw CancellationException("Academic dispatch no longer authorized")
        item.guard()
    }

    @PreDestroy fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            outstanding.forEach { it.result.completeExceptionally(CancellationException("Academic batch closed")) }
            outstanding.clear()
            pending.clear()
        }
        dispatcher.shutdownNow()
    }

    /** Only the worker's already-authorized academic step enters the short gather window. */
    fun collectCoalesced(executionId: Long, snapshot: VerificationSnapshot, mode: RecheckEvidenceMode,
        evaluatedAt: Instant, beforeDispatch: () -> Unit): RecheckAcademicEvidence {
        if (closed.get()) throw CancellationException("Academic batch closed")
        if (mode == RecheckEvidenceMode.REUSE || trustedId(snapshot) == null) {
            if (Thread.currentThread().isInterrupted) throw CancellationException("Academic request interrupted")
            beforeDispatch()
            return collect(listOf(snapshot), mode, evaluatedAt).getValue(snapshot.identityHash)
        }
        val item = Pending(snapshot, beforeDispatch)
        val key = BatchKey(executionId, mode, evaluatedAt)
        val accepted = synchronized(lock) {
            if (closed.get()) throw CancellationException("Academic batch closed")
            if (outstanding.size >= 8 || pending[key]?.any { it.snapshot.identityHash == snapshot.identityHash } == true) false
            else {
                val group = pending.getOrPut(key) { mutableListOf() }
                group += item
                outstanding += item
                if (group.size == 1) dispatcher.schedule({ flush(key) }, 25, TimeUnit.MILLISECONDS)
                if (group.size == 8) dispatcher.execute { flush(key) }
                true
            }
        }
        if (!accepted) return collect(listOf(snapshot), mode, evaluatedAt, beforeDispatch = {
            if (closed.get() || Thread.currentThread().isInterrupted) throw CancellationException("Academic dispatch no longer authorized")
            beforeDispatch()
        }).getValue(snapshot.identityHash)
        try { return item.result.get() }
        catch (e: ExecutionException) { throw (e.cause ?: e) }
        catch (e: InterruptedException) {
            item.result.completeExceptionally(CancellationException("Academic batch interrupted"))
            synchronized(lock) { pending[key]?.remove(item); outstanding.remove(item) }
            Thread.currentThread().interrupt()
            throw CancellationException("Academic batch interrupted")
        }
    }

    private fun flush(key: BatchKey) {
        val group = synchronized(lock) { pending.remove(key)?.toList().orEmpty() }
        if (group.isEmpty()) return
        fun finish(item: Pending, value: RecheckAcademicEvidence? = null, error: Throwable? = null) {
            if (error == null) item.result.complete(requireNotNull(value)) else item.result.completeExceptionally(error)
            synchronized(lock) { outstanding.remove(item) }
        }
        val live = group.filter { item ->
            try { checkActive(item); true }
            catch (e: Throwable) { finish(item, error = e); false }
        }
        if (live.isEmpty()) return
        try {
            val snapshots = live.map { it.snapshot }
            val combined = collect(snapshots, key.mode, key.evaluatedAt, beforeDispatch = {
                // A changed member invalidates the whole wire request. Survivors retry with fresh guards.
                live.forEach(::checkActive)
            })
            live.forEach { item ->
                try { checkActive(item); finish(item, combined.getValue(item.snapshot.identityHash)) }
                catch (e: Throwable) { finish(item, error = e) }
            }
        } catch (error: Throwable) {
            val survivors = live.filter { item ->
                try { checkActive(item); true }
                catch (e: Throwable) { finish(item, error = e); false }
            }
            if (survivors.size == live.size) survivors.forEach { finish(it, error = error) }
            else survivors.forEach { item ->
                try {
                    val value = collect(listOf(item.snapshot), key.mode, key.evaluatedAt,
                        beforeDispatch = { checkActive(item) }).getValue(item.snapshot.identityHash)
                    checkActive(item)
                    finish(item, value)
                } catch (e: Throwable) { finish(item, error = e) }
            }
        }
    }
    /** Trusted discovery IDs must be carried by the current, verified receipt, never guessed from names. */
    fun trustedId(snapshot: VerificationSnapshot): String? {
        if (!snapshot.identityValid || !DiscoveryIdentity.allowed(snapshot.profile)) return null
        val proof = snapshot.profile.identityVerification ?: return null
        return normalizeOpenAlexAuthorId(proof.openAlexAuthorId) ?: snapshot.boundOrcid
    }

    fun collect(snapshots: List<VerificationSnapshot>, mode: RecheckEvidenceMode, evaluatedAt: Instant,
        retained: Map<String, RecheckAcademicEvidence> = emptyMap(), beforeDispatch: (() -> Unit)? = null): Map<String, RecheckAcademicEvidence> {
        require(snapshots.size <= 100)
        require(snapshots.map { it.identityHash }.distinct().size == snapshots.size)
        val results = linkedMapOf<String, RecheckAcademicEvidence>()
        val trusted = snapshots.associateWith(::trustedId)
        fun result(s: VerificationSnapshot, id: String?, data: AuthorEnrichment? = null,
            reasons: List<String> = emptyList(), cached: Boolean = false, urls: List<String> = emptyList(), fields: String? = null) =
            RecheckAcademicEvidence(s.identityHash, s.factFingerprint, id, data, urls, evaluatedAt, reasons, cached, fields)
        for ((snapshot, id) in trusted) {
            if (id == null) { results[snapshot.identityHash] = result(snapshot, null, reasons = listOf("NO_TRUSTED_ACADEMIC_ID")); continue }
            if (mode == RecheckEvidenceMode.REUSE) {
                val old = retained[snapshot.identityHash]?.takeIf {
                    it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint && it.trustedId == id
                }
                val p = snapshot.profile
                val hasFrozen = listOf(p.hIndex, p.citationCount, p.worksCount, p.lastPublicationYear, p.researchFields,
                    p.researchFieldIds, p.recentWorkTitles, p.patentTitles).any { it != null }
                results[snapshot.identityHash] = when {
                    old != null -> old.copy(evaluatedAt = evaluatedAt, retained = true)
                    hasFrozen -> result(snapshot, id, AuthorEnrichment(p.hIndex, p.citationCount, p.worksCount,
                        recentWorkTitles = p.recentWorkTitles, patentTitles = p.patentTitles,
                        disciplineCategory = p.disciplineCategory, institutionType = p.institutionType,
                        lastPublicationYear = p.lastPublicationYear, researchFieldIds = p.researchFieldIds),
                        cached = true, fields = p.researchFields)
                    else -> result(snapshot, id, reasons = listOf("EVIDENCE_NOT_RETAINED"), cached = true)
                }
            }
        }
        if (mode == RecheckEvidenceMode.REUSE) return results // No bean resolution, no provider access.
        val pending = trusted.filterValues { it != null }
        if (pending.isEmpty()) return results
        val client = clients.getIfAvailable()
        if (client == null) {
            pending.forEach { (s, id) -> results[s.identityHash] = result(s, id, reasons = listOf("PROVIDER_UNAVAILABLE")) }
            return results
        }
        val authors = pending.values.filterNotNull().filter { it.startsWith("A") }.distinct()
        val orcids = pending.values.filterNotNull().filterNot { it.startsWith("A") }.distinct()
        val outcomes = linkedMapOf<String, EnrichmentOutcome>()
        var deferred: OpenAlexBudgetDeferredException? = null
        try {
            if (authors.isNotEmpty()) outcomes.putAll(if (beforeDispatch == null)
                client.batchEnrichByAuthorIds(authors, RequestKind.HISTORY_ENRICHMENT)
                else client.batchEnrichByAuthorIds(authors, RequestKind.HISTORY_ENRICHMENT, beforeDispatch))
            if (orcids.isNotEmpty()) outcomes.putAll(if (beforeDispatch == null)
                client.batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT)
                else client.batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT, beforeDispatch))
        } catch (e: OpenAlexBudgetDeferredException) { deferred = e }
        pending.forEach { (s, id) ->
            val urls = if (id!!.startsWith("A")) listOf("${properties.baseUrl}/authors?filter=openalex:${authors.joinToString("|")}&per_page=${authors.size}")
                else listOf("${properties.baseUrl}/authors?filter=orcid:${orcids.joinToString("|")}&per_page=200")
            results[s.identityHash] = when (val outcome = outcomes[id]) {
                is EnrichmentOutcome.Success -> result(s, id, outcome.data,
                    reasons = if (outcome.titlesFailed) listOf("PARTIAL_TITLES") else emptyList(), urls = urls)
                EnrichmentOutcome.AmbiguousIdentity -> result(s, id, reasons = listOf("AMBIGUOUS_IDENTITY"), urls = urls)
                EnrichmentOutcome.NotFound -> result(s, id, reasons = listOf("ACADEMIC_SOURCE_NOT_FOUND"), urls = urls)
                is EnrichmentOutcome.RateLimited -> result(s, id, reasons = listOf("RATE_LIMITED"), urls = urls).copy(
                    retryAt = clock.instant().plusMillis((outcome.retryAfterMs ?: 60000L).coerceAtLeast(0)))
                is EnrichmentOutcome.ApiError -> result(s, id, reasons = listOf("PROVIDER_ERROR"), urls = urls).copy(
                    providerError = outcome.message.take(2048),
                    httpStatus = Regex("^HTTP ([1-5][0-9]{2})$").matchEntire(outcome.message)?.groupValues?.get(1)?.toInt())
                null -> if (deferred != null) result(s, id, reasons = listOf("BUDGET_DEFERRED"), urls = urls).copy(retryAt = deferred.retryAt)
                    else result(s, id, reasons = listOf("PROVIDER_RESULT_MISSING"), urls = urls)
            }
        }
        return results
    }
}
