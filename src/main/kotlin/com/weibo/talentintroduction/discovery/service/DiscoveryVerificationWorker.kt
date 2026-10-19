package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** Confirmed field evidence and provider cursors are deliberately separate JSON members. */
data class VerificationWorkerEvidence(
    val evidence: List<VerificationEvidence> = emptyList(), val search: ApolloSearchEvidence? = null,
    val progress: VerificationProgress = VerificationProgress(),
    val orcidCompleted: List<OrcidEndpointCompletion> = emptyList(), val schemaVersion: Int = 1
) { init { require(schemaVersion == 1) } }

@Service
class DiscoveryVerificationWorker(
    private val properties: DiscoveryVerificationProperties,
    private val repository: DiscoveryVerificationRepository,
    private val orcid: OrcidVerificationEvidenceClient,
    private val apollo: ApolloPeopleSearchClient,
    private val parser: QualificationEvidenceParser,
    private val rules: DiscoveryVerificationRules,
    private val commits: DiscoveryVerificationCommitService,
    private val recovery: ObjectProvider<RegistrationRecovery>,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val running = AtomicBoolean()
    private val authBlocked = AtomicBoolean()
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "discovery-verification-lease").apply { isDaemon = true } }
    @PreDestroy fun close() { heartbeat.shutdownNow() }

    /** Process-local current latch; observing it never resumes or claims work. */
    fun isAuthBlocked(): Boolean = authBlocked.get()

    @Scheduled(fixedDelayString = "\${talent-introduction.discovery-verification.poll-ms:5000}")
    fun tick() {
        if (!properties.workerEnabled || authBlocked.get() || !running.compareAndSet(false, true)) return
        try {
            recovery.getIfAvailable()?.recoverReserved(10)
            repository.listProjectionDue(10).forEach { commits.project(it) }
            repeat(10) {
                if (!properties.workerEnabled || authBlocked.get()) return
                val lease = repository.claimDue(clock.instant(), properties.leaseSeconds) ?: return
                process(lease)
            }
        } finally { running.set(false) }
    }

    private class LeaseLost : IllegalStateException()
    private class BudgetDeferred(val reason: VerificationBudgetReason) : IllegalStateException()

    /** Also used by the isolated runtime fixture. SQL reservations always precede provider calls. */
    internal fun process(lease: VerificationItem) {
        val token = requireNotNull(lease.leaseToken)
        val lost = AtomicBoolean()
        val renewal = heartbeat.scheduleAtFixedRate({
            try {
                if (!repository.renewLease(lease.id, token, lease.generation, clock.instant(), properties.leaseSeconds)) lost.set(true)
            } catch (_: Exception) { lost.set(true) }
        }, 30, 30, TimeUnit.SECONDS)
        var payload = VerificationWorkerEvidence()
        var activePermit: RequestPermit? = null
        var requestNumber = lease.requestCount
        var entered = lease.apolloEnteredAt
        var searched = lease.searchCompletedAt
        var candidatesAt = lease.candidateFoundAt
        fun json() = mapper.writeValueAsString(payload)
        fun ensureOwned() { if (lost.get()) throw LeaseLost() }
        fun save() {
            ensureOwned()
            check(repository.saveProgress(lease.id, token, lease.generation, json(), entered, searched, candidatesAt, clock.instant())) { "Verification lease lost" }
        }
        fun reserve(provider: VerificationProvider, kind: VerificationQueryKind, page: Int, request: String): RequestPermit {
            ensureOwned()
            if (!properties.workerEnabled) throw BudgetDeferred(VerificationBudgetReason.LOCK_BUSY)
            val reservation = repository.reserveAttemptWithBudget(lease.id, token, lease.generation,
                VerificationAttemptRequest("${lease.id}-${UUID.randomUUID()}", provider, kind, page, requestNumber + 1, request),
                properties.requestsPerMinute, properties.requestsPerDay, properties.maxRequestsPerItem, clock.instant())
            val id = reservation.attemptId ?: throw BudgetDeferred(requireNotNull(reservation.deferred))
            requestNumber++
            return RequestPermit(id, provider).also { activePermit = it }
        }
        fun completed(permit: RequestPermit, result: VerificationHttpResult<*>, response: Any? = null) {
            ensureOwned()
            val status = when (result) {
                is VerificationHttpResult.Success -> result.httpStatus
                is VerificationHttpResult.RateLimited -> result.httpStatus
                is VerificationHttpResult.TransientFailure -> result.httpStatus
                is VerificationHttpResult.PermanentFailure -> result.httpStatus
            }
            val code = when (result) {
                is VerificationHttpResult.Success -> "OK"
                is VerificationHttpResult.RateLimited -> "RATE_LIMIT"
                is VerificationHttpResult.TransientFailure -> result.code
                is VerificationHttpResult.PermanentFailure -> result.code
            }
            check(repository.completeAttemptAndProgress(lease.id, token, lease.generation, permit.attemptId, status, code,
                response?.let { mapper.writeValueAsString(it) }, (result as? VerificationHttpResult.Success)?.responseSha256,
                json(), entered, searched, candidatesAt, clock.instant())) { "Verification completion not persisted" }
            activePermit = null
        }
        fun finish(decision: VerificationDecision) {
            ensureOwned()
            payload = payload.copy(evidence = decision.evidence, progress = payload.progress.copy(stage = "DECIDE"))
            val proposed = VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification, decision.route,
                json(), mapper.writeValueAsString(decision.reasonCodes), entered, searched, candidatesAt,
                if (decision.matchedPersonId != null) clock.instant() else null, decision.matchedPersonId)
            val committed = commits.commit(lease, proposed, commits.prepare(lease))
            if (committed != null) commits.project(committed)
        }
        fun fail(code: String, transientAttempts: Int = lease.transientAttempts) {
            if (code == "CONFIG_AUTH") authBlocked.set(true)
            repository.saveFailure(lease.id, token, lease.generation,
                VerificationResult(VerificationState.ERROR, null, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE,
                    json(), mapper.writeValueAsString(listOf(code)), entered, searched, candidatesAt), transientAttempts, clock.instant())
        }
        fun retry(result: VerificationHttpResult<Nothing>) {
            val now = clock.instant()
            when (result) {
                is VerificationHttpResult.RateLimited -> repository.reschedule(lease.id, token, lease.generation, maxOf(now, result.retryAt),
                    lease.transientAttempts, json(), "[\"RATE_LIMIT\"]", now)
                is VerificationHttpResult.TransientFailure -> {
                    val count = lease.transientAttempts + 1
                    if (count >= properties.maxTransientAttempts) fail(result.code, count)
                    else repository.reschedule(lease.id, token, lease.generation, now.plusSeconds(longArrayOf(30, 120, 600)[count - 1]),
                        count, json(), mapper.writeValueAsString(listOf(result.code)), now)
                }
                is VerificationHttpResult.PermanentFailure -> fail(result.code)
                else -> error("Unexpected retry result")
            }
        }
        try {
            payload = lease.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) } ?: payload
            val snapshot = mapper.readValue<VerificationSnapshot>(lease.snapshotJson)
            require(snapshot.identityHash == lease.identityHash && snapshot.factFingerprint == lease.factFingerprint)
            val run = repository.findRun(lease.ownerExecutionId) ?: error("Verification run missing")
            if (payload.progress.stage == "LOCAL_EVIDENCE") {
                val existing = rules.evaluate(snapshot, payload.evidence)
                if (existing.outcome == VerificationOutcome.DIRECT_CLEAR) { finish(existing); return }
                val read = orcid.read(snapshot, payload.orcidCompleted, { endpoint, permit, document, result ->
                    if (result is VerificationHttpResult.Success || result is VerificationHttpResult.PermanentFailure && result.code == "EVIDENCE_MISSING") {
                        val completion = OrcidEndpointCompletion(endpoint, document)
                        val saved = payload.orcidCompleted + completion
                        payload = payload.copy(orcidCompleted = saved,
                            evidence = (payload.evidence + parser.parse(snapshot, OrcidEvidenceRead(listOfNotNull(document), false, emptyList()))).distinct())
                        completed(permit, result, completion)
                    } else completed(permit, result)
                }) { path -> reserve(VerificationProvider.ORCID, VerificationQueryKind.ORCID, 1, mapper.writeValueAsString(mapOf("path" to path))) }
                if (read.failure != null) { retry(read.failure); return }
                val local = rules.evaluate(snapshot, payload.evidence)
                if (!read.complete && snapshot.boundOrcid != null) {
                    finish(local.copy(outcome = VerificationOutcome.PENDING, qualification = VerificationQualification.UNKNOWN,
                        route = VerificationRoute.NONE, reasonCodes = listOf("EVIDENCE_INCOMPLETE"))); return
                }
                if (local.outcome == VerificationOutcome.DIRECT_CLEAR || !run.apolloRequested || !snapshot.identityValid ||
                    local.qualification in setOf(VerificationQualification.NOT_MET, VerificationQualification.CONFLICT)) {
                    finish(local); return
                }
                payload = payload.copy(progress = VerificationProgress(stage = "APOLLO_SEARCH"), search = ApolloSearchEvidence())
                entered = entered ?: clock.instant()
                save()
            }
            if (payload.progress.stage == "APOLLO_SEARCH") {
                val names = (listOf(snapshot.fullName) + payload.evidence.filter { it.provider == "ORCID" && it.field == "otherName" }.map { it.value })
                    .distinctBy(rules::normalizeName)
                val institution = payload.evidence.firstOrNull { it.field == "employment" && it.assertionSource == AssertionSource.ORGANIZATION && it.temporalScope == TemporalScope.CURRENT }
                    ?.let { mapper.readTree(it.value).path("institution").asText().takeIf(String::isNotBlank) }?.let(::VerificationInstitution)
                val queries = names.map { ApolloSearchQuery(it) } + listOfNotNull(institution?.let { ApolloSearchQuery(snapshot.fullName, institution = it) })
                while (payload.progress.queryIndex < queries.size) {
                    val index = payload.progress.queryIndex
                    val query = queries[index].copy(page = payload.progress.page)
                    val kind = if (query.institution != null) VerificationQueryKind.NARROW else if (index == 0) VerificationQueryKind.BROAD else VerificationQueryKind.VARIANT
                    val permit = reserve(VerificationProvider.APOLLO, kind, query.page, mapper.writeValueAsString(query))
                    val result = apollo.search(query, permit)
                    if (result !is VerificationHttpResult.Success) { completed(permit, result); retry(result as VerificationHttpResult<Nothing>); return }
                    val page = result.value
                    val last = index == queries.lastIndex && !page.hasNext
                    val search = (payload.search ?: ApolloSearchEvidence()).merge(page, query, index == 0, last)
                    val finished = if (!page.hasNext) payload.progress.finishedQueryKeys + index.toString() else payload.progress.finishedQueryKeys
                    payload = payload.copy(search = search, progress = VerificationProgress("APOLLO_SEARCH",
                        if (page.hasNext) index else index + 1, if (page.hasNext) query.page + 1 else 1,
                        last, search.candidates.map { it.id }, finished))
                    if (search.candidates.isNotEmpty()) candidatesAt = candidatesAt ?: clock.instant()
                    if (last) searched = searched ?: clock.instant()
                    completed(permit, result, page)
                    if (page.hasNext && query.page >= properties.maxPagesPerQuery) break
                }
                finish(rules.evaluate(snapshot, payload.evidence, payload.search))
            } else if (payload.progress.stage == "DECIDE") finish(rules.evaluate(snapshot, payload.evidence, payload.search))
        } catch (_: LeaseLost) {
            // A replacement token owns recovery; no stale writes.
        } catch (deferred: BudgetDeferred) {
            when (deferred.reason) {
                VerificationBudgetReason.LEASE_LOST -> Unit
                VerificationBudgetReason.ITEM_LIMIT -> {
                    val snapshot = mapper.readValue<VerificationSnapshot>(lease.snapshotJson)
                    val decision = rules.evaluate(snapshot, payload.evidence, payload.search)
                    finish(decision.copy(outcome = VerificationOutcome.PENDING, route = VerificationRoute.NONE,
                        reasonCodes = listOf(if (payload.progress.stage == "LOCAL_EVIDENCE") "EVIDENCE_INCOMPLETE" else "SEARCH_INCOMPLETE"), matchedPersonId = null))
                }
                else -> {
                    val now = clock.instant()
                    val next = if (deferred.reason == VerificationBudgetReason.DAY_LIMIT)
                        LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) else now.plusSeconds(60)
                    repository.reschedule(lease.id, token, lease.generation, next, lease.transientAttempts, json(),
                        mapper.writeValueAsString(listOf(deferred.reason.name)), now)
                }
            }
        } catch (_: Exception) {
            // Persistence failure must stop the request chain. Leave its lease/cursor for durable recovery.
            // Only preflight failures with no unacknowledged HTTP may be recorded as an interface-safe ERROR.
            if (activePermit == null && !lost.get()) fail("VERIFICATION_FAILED")
        } finally { renewal.cancel(false) }
    }
}
