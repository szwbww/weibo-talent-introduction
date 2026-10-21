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
    val orcidCompleted: List<OrcidEndpointCompletion> = emptyList(), val schemaVersion: Int = 1,
    val queries: List<ApolloSearchQuery>? = null,
    val institutionNormalization: InstitutionNormalization? = null,
    val ruleVersion: Int = 0
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
    private class BudgetDeferred(val reason: VerificationBudgetReason, val retryAt: Instant? = null) : IllegalStateException()
    private enum class ContextIssue { NONE, PARSER_DRIFT, INVALID_PROTOCOL }

    /** Also used by the isolated runtime fixture. SQL reservations always precede provider calls. */
    internal fun process(lease: VerificationItem) {
        val token = requireNotNull(lease.leaseToken)
        val lost = AtomicBoolean()
        val renewal = heartbeat.scheduleAtFixedRate({
            try {
                if (!repository.renewLease(lease.id, token, lease.generation, clock.instant(), properties.leaseSeconds)) lost.set(true)
            } catch (_: Exception) { lost.set(true) }
        }, 30, 30, TimeUnit.SECONDS)
        var payload = VerificationWorkerEvidence(ruleVersion = 1)
        var rawPayload: String? = null
        var activePermit: RequestPermit? = null
        var requestNumber = lease.requestCount
        var entered = lease.apolloEnteredAt
        var searched = lease.searchCompletedAt
        var candidatesAt = lease.candidateFoundAt
        fun json() = rawPayload ?: mapper.writeValueAsString(payload)
        fun contextIssue(snapshot: VerificationSnapshot, saved: InstitutionNormalization): ContextIssue {
            if (saved.version != DiscoveryInstitutionNormalizer.VERSION) return ContextIssue.INVALID_PROTOCOL
            val expected = runCatching {
                DiscoveryInstitutionNormalizer.build(snapshot.profile.institution,
                    rules.identityEmployments(snapshot, payload.evidence), payload.search?.candidates)
            }.getOrElse { return ContextIssue.INVALID_PROTOCOL }
            // Structure and input identity are protocol, not parser output: a mismatch is corruption.
            if (saved.inputs.map { it.source to it.sourceRef } != expected.inputs.map { it.source to it.sourceRef })
                return ContextIssue.INVALID_PROTOCOL
            var drift = false
            saved.inputs.zip(expected.inputs).forEach { (stored, rebuilt) ->
                if (stored.raw != rebuilt.raw) {
                    // A lawful parser rebuild may change the raw value; the stored hash must still describe it.
                    if (stored.rawSha256 != DiscoveryInstitutionNormalizer.sha256(stored.raw ?: ""))
                        return ContextIssue.INVALID_PROTOCOL
                    drift = true
                } else if (stored != rebuilt) {
                    return ContextIssue.INVALID_PROTOCOL
                }
            }
            if (saved.queryInstitution != expected.queryInstitution && !drift) return ContextIssue.INVALID_PROTOCOL
            return if (drift) ContextIssue.PARSER_DRIFT else ContextIssue.NONE
        }
        fun evaluate(snapshot: VerificationSnapshot): VerificationDecision {
            val normalization = payload.institutionNormalization
            if (normalization != null) {
                when (contextIssue(snapshot, normalization)) {
                    // An unrecognized or corrupted saved protocol is an interface error, not a no-match.
                    ContextIssue.INVALID_PROTOCOL -> throw IllegalArgumentException("Invalid institution normalization context")
                    ContextIssue.PARSER_DRIFT -> return rules.evaluate(snapshot, payload.evidence, payload.search, ruleVersion = payload.ruleVersion).copy(
                        qualification = VerificationQualification.UNKNOWN, outcome = VerificationOutcome.PENDING,
                        route = VerificationRoute.NONE, reasonCodes = listOf("EVIDENCE_CONFLICT"), matchedPersonId = null)
                    ContextIssue.NONE -> return rules.evaluate(snapshot, payload.evidence, payload.search,
                        institutionNormalization = normalization, ruleVersion = payload.ruleVersion)
                }
            }
            return rules.evaluate(snapshot, payload.evidence, payload.search, ruleVersion = payload.ruleVersion)
        }
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
            val id = reservation.attemptId ?: throw BudgetDeferred(requireNotNull(reservation.deferred), reservation.retryAt)
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
                (if (result is VerificationHttpResult.Success) response else when (result) {
                    is VerificationHttpResult.RateLimited -> result.diagnostic
                    is VerificationHttpResult.TransientFailure -> result.diagnostic
                    is VerificationHttpResult.PermanentFailure -> result.diagnostic
                    else -> null
                })?.let { mapper.writeValueAsString(it) }, (result as? VerificationHttpResult.Success)?.responseSha256,
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
                is VerificationHttpResult.PermanentFailure -> if (result.code == "APOLLO_CREDITS_EXHAUSTED") {
                    repository.reschedule(lease.id, token, lease.generation, now.plusSeconds(1800),
                        lease.transientAttempts, json(), "[\"APOLLO_CREDITS_EXHAUSTED\"]", now)
                } else fail(result.code)
                else -> error("Unexpected retry result")
            }
        }
        try {
            rawPayload = lease.evidenceJson
            payload = lease.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) } ?: payload
            rawPayload = null
            val snapshot = mapper.readValue<VerificationSnapshot>(lease.snapshotJson)
            require(snapshot.identityHash == lease.identityHash && snapshot.factFingerprint == lease.factFingerprint)
            val run = repository.findRun(lease.ownerExecutionId) ?: error("Verification run missing")
            val matchingVersion = mapper.readTree(run.settingsJson).path("apolloMatchingVersion").asInt(1)
            require(matchingVersion in 1..2)
            val aliasVersion = mapper.readTree(run.settingsJson).path("institutionAliasVersion").asInt(0)
            require(aliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
            if (payload.progress.stage == "LOCAL_EVIDENCE") {
                payload = payload.copy(evidence = parser.rebuild(snapshot, payload.orcidCompleted.mapNotNull { it.document }, payload.evidence))
                val existing = evaluate(snapshot)
                if (existing.outcome == VerificationOutcome.DIRECT_CLEAR) { finish(existing); return }
                val read = orcid.read(snapshot, payload.orcidCompleted, { endpoint, permit, document, result ->
                    if (result is VerificationHttpResult.Success || result is VerificationHttpResult.PermanentFailure && result.code == "EVIDENCE_MISSING") {
                        val completion = OrcidEndpointCompletion(endpoint, document)
                        val saved = payload.orcidCompleted + completion
                        payload = payload.copy(orcidCompleted = saved,
                            evidence = parser.rebuild(snapshot, saved.mapNotNull { it.document }, payload.evidence))
                        completed(permit, result, completion)
                    } else completed(permit, result)
                }) { path -> reserve(VerificationProvider.ORCID, VerificationQueryKind.ORCID, 1, mapper.writeValueAsString(mapOf("path" to path))) }
                if (read.failure != null) { retry(read.failure); return }
                payload = payload.copy(evidence = parser.rebuild(snapshot,
                    (payload.orcidCompleted.mapNotNull { it.document } + read.documents).distinct(), payload.evidence))
                val local = evaluate(snapshot)
                if (!read.complete && snapshot.boundOrcid != null) {
                    val reasons = listOf("EVIDENCE_INCOMPLETE") +
                        if (requestNumber >= properties.maxRequestsPerItem) listOf("ITEM_REQUEST_LIMIT") else emptyList()
                    finish(local.copy(outcome = VerificationOutcome.PENDING,
                        route = VerificationRoute.NONE, reasonCodes = reasons)); return
                }
                if (local.outcome == VerificationOutcome.DIRECT_CLEAR || !run.apolloRequested || !snapshot.identityValid ||
                    local.qualification == VerificationQualification.CONFLICT ||
                    matchingVersion == 1 && local.qualification == VerificationQualification.NOT_MET) {
                    finish(local); return
                }
                // Only the first Apollo entry creates the derived context, and it is persisted before any HTTP.
                val normalization = if (entered == null && payload.search == null && payload.institutionNormalization == null)
                    DiscoveryInstitutionNormalizer.build(snapshot.profile.institution,
                        rules.identityEmployments(snapshot, payload.evidence), emptyList())
                else payload.institutionNormalization
                payload = payload.copy(progress = VerificationProgress(stage = "APOLLO_SEARCH"),
                    search = ApolloSearchEvidence(matchingVersion = matchingVersion, academicResearchFields = snapshot.profile.researchFields, institutionAliasVersion = aliasVersion),
                    institutionNormalization = normalization, queries = searchQueries(snapshot, payload.evidence, normalization,
                        formalName = matchingVersion == 2, ruleVersion = payload.ruleVersion))
                entered = entered ?: clock.instant()
                save()
            }
            if (payload.progress.stage == "APOLLO_SEARCH") {
                // Old in-flight payloads retain their original query sequence; new runs persist it before HTTP.
                var queries = payload.queries ?: legacySearchQueries(snapshot, payload.evidence)
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
                    payload = payload.copy(search = search,
                        institutionNormalization = payload.institutionNormalization?.let {
                            DiscoveryInstitutionNormalizer.withCandidates(it, search.candidates)
                        },
                        progress = VerificationProgress("APOLLO_SEARCH",
                        if (page.hasNext) index else index + 1, if (page.hasNext) query.page + 1 else 1,
                        last, search.candidates.map { it.id }, finished))
                    if (last && payload.ruleVersion >= 1 && search.matchingVersion == 2 &&
                        search.candidates.none { rules.nameMatches(it, rules.allowedNames(snapshot, payload.evidence), search.queryNames, true, payload.ruleVersion) &&
                            rules.matchReason(snapshot, payload.evidence, it, null, search.institutionAliasVersion) in
                            setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }) {
                        val fallback = surnameFallback(snapshot, payload.institutionNormalization)
                        if (fallback != null && queries.none { it.name == fallback.name && it.institution == fallback.institution }) {
                            queries = queries + fallback
                            payload = payload.copy(queries = queries, search = search.copy(complete = false),
                                progress = payload.progress.copy(complete = false))
                        }
                    }
                    if (search.candidates.isNotEmpty()) candidatesAt = candidatesAt ?: clock.instant()
                    if (payload.search?.complete == true) searched = searched ?: clock.instant()
                    completed(permit, result, page)
                    if (page.hasNext && query.page >= properties.maxPagesPerQuery) {
                        val decision = evaluate(snapshot)
                        finish(decision.copy(reasonCodes = (decision.reasonCodes + "SEARCH_INCOMPLETE" + "SEARCH_PAGE_LIMIT").distinct()))
                        return
                    }
                }
                if (payload.search?.matchingVersion == 2 && payload.search?.detailsComplete != true) {
                    // Search pagination is finished and durable before detail calls begin. Recovery never repeats
                    // acknowledged details or changes the saved query sequence.
                    val names = rules.allowedNames(snapshot, payload.evidence)
                    while (true) {
                        val candidates = payload.search!!.candidates.filter {
                            (rules.nameMatches(it, names, payload.search!!.queryNames, masked = true, ruleVersion = payload.ruleVersion) ||
                                payload.ruleVersion >= 1 && rules.emailNameMatches(snapshot, payload.evidence, it, payload.search!!.institutionAliasVersion, masked = true))
                        }
                        val strong = candidates.filter { rules.matchReason(snapshot, payload.evidence, it, null, payload.search?.institutionAliasVersion ?: 0) in
                            setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
                        // Current institution / professor evidence can distinguish a candidate from unrelated
                        // same-name people. Only fall back to history/specialty when no strong candidate exists.
                        val candidate = strong.ifEmpty { candidates }.firstOrNull {
                            it.id !in payload.search!!.detailsCompleted &&
                                !(it in strong && (it.fullName != null || it.lastName != null))
                        } ?: break
                        val permit = reserve(VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1,
                            mapper.writeValueAsString(mapOf("personId" to candidate.id, "matchingVersion" to 2)))
                        val result = apollo.detail(candidate.id, permit)
                        if (result is VerificationHttpResult.Success) {
                            // Search is a partial projection; richer values replace it, rather than being treated as
                            // contradictory copies from independent search pages.
                            val detail = result.value
                            val updated = detail.copy(
                                orcid = detail.orcid ?: candidate.orcid,
                                obfuscatedLastName = detail.obfuscatedLastName ?: candidate.obfuscatedLastName)
                            val conflicting = detail.orcid != null && candidate.orcid != null &&
                                normalizeOrcid(detail.orcid) != normalizeOrcid(candidate.orcid)
                            val search = payload.search!!.copy(
                                candidates = payload.search!!.candidates.map { if (it.id == candidate.id) updated else it },
                                conflictingIds = payload.search!!.conflictingIds + if (conflicting) setOf(candidate.id) else emptySet(),
                                detailsCompleted = payload.search!!.detailsCompleted + candidate.id)
                            payload = payload.copy(search = search, institutionNormalization = payload.institutionNormalization?.let {
                                DiscoveryInstitutionNormalizer.withCandidates(it, search.candidates)
                            })
                            completed(permit, result, detail)
                        } else if (result is VerificationHttpResult.PermanentFailure && result.code == "EVIDENCE_MISSING") {
                            payload = payload.copy(search = payload.search!!.copy(
                                detailsCompleted = payload.search!!.detailsCompleted + candidate.id))
                            completed(permit, result)
                        } else {
                            completed(permit, result)
                            retry(result as VerificationHttpResult<Nothing>); return
                        }
                    }
                    // Academic enrichment runs independently. Freeze the fields already available at decision
                    // time; replay and the gate consume these same fields, never a later mutable profile.
                    if (payload.search!!.candidates.isNotEmpty()) {
                        val latest = commits.prepare(lease).snapshot
                        if (latest != null && latest.identityHash == snapshot.identityHash &&
                            latest.factFingerprint == snapshot.factFingerprint) {
                            payload = payload.copy(search = payload.search!!.copy(
                                academicResearchFields = latest.profile.researchFields,
                                academicCapturedAt = clock.instant()))
                        }
                    }
                    payload = payload.copy(search = payload.search!!.copy(detailsComplete = true))
                    save()
                }
                finish(evaluate(snapshot))
            } else if (payload.progress.stage == "DECIDE") {
                payload = payload.copy(evidence = parser.rebuild(snapshot, payload.orcidCompleted.mapNotNull { it.document }, payload.evidence))
                finish(evaluate(snapshot))
            }
        } catch (_: LeaseLost) {
            // A replacement token owns recovery; no stale writes.
        } catch (deferred: BudgetDeferred) {
            when (deferred.reason) {
                VerificationBudgetReason.LEASE_LOST -> Unit
                VerificationBudgetReason.ITEM_LIMIT -> {
                    val snapshot = mapper.readValue<VerificationSnapshot>(lease.snapshotJson)
                    val decision = evaluate(snapshot)
                    finish(decision.copy(outcome = VerificationOutcome.PENDING, route = VerificationRoute.NONE,
                        reasonCodes = (decision.reasonCodes +
                            (if (payload.progress.stage == "LOCAL_EVIDENCE") "EVIDENCE_INCOMPLETE" else "SEARCH_INCOMPLETE") +
                            "ITEM_REQUEST_LIMIT").distinct(), matchedPersonId = null))
                }
                else -> {
                    val now = clock.instant()
                    val next = deferred.retryAt?.let { maxOf(now, it) } ?: if (deferred.reason == VerificationBudgetReason.DAY_LIMIT)
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

    internal fun searchQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
        normalization: InstitutionNormalization? = null, formalName: Boolean = true, ruleVersion: Int = 0): List<ApolloSearchQuery> {
        if (normalization != null) {
            // The narrow hint is exactly the frozen subject; ambiguity produces no institution query at all.
            return nameQueries(snapshot, evidence, formalName, ruleVersion) + listOfNotNull(normalization.queryInstitution?.let {
                ApolloSearchQuery(snapshot.fullName, institution = VerificationInstitution(it))
            })
        }
        val institutions = ArrayList<VerificationInstitution>()
        rules.identityEmployments(snapshot, evidence).forEach { fact ->
            val value = mapper.readTree(fact.value)
            val institution = VerificationInstitution(value.path("institution").asText(),
                value.path("standardId").asText("").takeIf(String::isNotBlank))
            if (institutions.none { rules.sameInstitution(it, institution) }) institutions += institution
        }
        return nameQueries(snapshot, evidence, formalName, ruleVersion) + listOfNotNull(institutions.singleOrNull()?.let {
            ApolloSearchQuery(snapshot.fullName, institution = it)
        })
    }

    internal fun surnameFallback(snapshot: VerificationSnapshot, normalization: InstitutionNormalization?): ApolloSearchQuery? {
        val subject = normalization?.queryInstitution ?: return null
        val surname = rules.familyNameHint(snapshot)
        if (surname.length < 3 || surname.any { !it.isLetter() && it != ' ' }) return null
        return ApolloSearchQuery(surname, institution = VerificationInstitution(subject))
    }

    private fun nameQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>, formalName: Boolean = true, ruleVersion: Int = 0) =
        (listOf(snapshot.fullName) + evidence.filter { it.provider == "ORCID" &&
            (it.field == "otherName" || formalName && it.field == "name") &&
            it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint }.map { it.value })
            .let { names -> if (ruleVersion >= 1) names.flatMap(rules::queryNameVariants) else names }
            .distinctBy(rules::normalizeName).map { ApolloSearchQuery(it) }

    private fun legacySearchQueries(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): List<ApolloSearchQuery> {
        val institution = evidence.firstOrNull { it.field == "employment" && it.assertionSource == AssertionSource.ORGANIZATION &&
            it.temporalScope == TemporalScope.CURRENT }?.let {
            mapper.readTree(it.value).path("institution").asText().takeIf(String::isNotBlank)
        }?.let(::VerificationInstitution)
        return nameQueries(snapshot, evidence, formalName = false) + listOfNotNull(institution?.let { ApolloSearchQuery(snapshot.fullName, institution = it) })
    }
}
