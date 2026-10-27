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
    val ruleVersion: Int = 0,
    val diagnostics: WorkerDiagnostics? = null,
    val executionContext: VerificationExecutionContext? = null
) { init { require(schemaVersion == 1) } }

/** One additive payload member. Missing context deliberately retains historical clock semantics. */
data class VerificationExecutionContext(val evaluationAt: Instant, val paperSourceRefs: List<PaperSourceRef> = emptyList(),
    val paper: PaperEvidenceRead? = null, val academic: RecheckAcademicEvidence? = null,
    val cacheReasons: List<String> = emptyList())

/** The preparation/start contract; no cursor or HTTP counters are inherited into a new review epoch. */
data class VerificationReviewInput(val expectedAdmissionRevision: Long?, val beforeDecision: String? = null,
    val paperSourceRefs: List<PaperSourceRef> = emptyList(), val evidence: VerificationWorkerEvidence? = null,
    val evidenceHash: String? = null)

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
    private val clock: Clock = Clock.systemUTC(),
    private val traces: com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository? = null,
    private val papers: PaperVerificationEvidenceService? = null,
    private val academic: DiscoveryRecheckAcademicEvidenceService? = null
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
        val activeSteps = mutableMapOf<Int, VerificationStep>()
        var activeStepNo = 1
        val requestKeys = mutableMapOf<Long, String>()
        fun startStep(number: Int, input: Any = emptyMap<String, Any?>()) {
            activeStepNo = number
            traces?.startStep(lease.id, token, lease.generation, number, mapper.writeValueAsString(input),
                mapper.writeValueAsString(mapOf("ruleVersion" to payload.ruleVersion, "evaluationAt" to payload.executionContext?.evaluationAt,
                    "identityVersion" to lease.snapshotJson.let { mapper.readTree(it).path("profile").path("identityVerification").path("version").asText() })),
                clock.instant())?.let { activeSteps[number] = it }
        }
        fun endStep(number: Int, state: VerificationStepState, output: Any = emptyMap<String, Any?>(),
            codes: List<String> = emptyList(), blocking: Boolean = false) {
            val step = activeSteps.remove(number) ?: return
            check(traces!!.finishStep(lease.id, token, lease.generation, step.id, state, mapper.writeValueAsString(output),
                mapper.writeValueAsString(mapOf("codes" to codes, "blocking" to blocking)), clock.instant())) { "Trace lease lost" }
        }
        fun skipStep(number: Int, reason: String) {
            startStep(number)
            endStep(number, VerificationStepState.SKIPPED, codes = listOf(reason))
        }
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
                    ContextIssue.PARSER_DRIFT -> return rules.evaluate(snapshot, payload.evidence, payload.search,
                        ruleVersion = payload.ruleVersion, evaluatedAt = payload.executionContext?.evaluationAt).copy(
                        qualification = VerificationQualification.UNKNOWN, outcome = VerificationOutcome.PENDING,
                        route = VerificationRoute.NONE, reasonCodes = listOf("EVIDENCE_CONFLICT"), matchedPersonId = null)
                    ContextIssue.NONE -> return rules.evaluate(snapshot, payload.evidence, payload.search,
                        institutionNormalization = normalization, ruleVersion = payload.ruleVersion,
                        evaluatedAt = payload.executionContext?.evaluationAt)
                }
            }
            return rules.evaluate(snapshot, payload.evidence, payload.search, ruleVersion = payload.ruleVersion,
                evaluatedAt = payload.executionContext?.evaluationAt)
        }
        fun ensureOwned() {
            if (lost.get() || lease.reviewEpoch > 0 && !repository.isOwned(lease.id, token, lease.generation, clock.instant())) throw LeaseLost()
        }
        fun save() {
            ensureOwned()
            check(repository.saveProgress(lease.id, token, lease.generation, json(), entered, searched, candidatesAt, clock.instant())) { "Verification lease lost" }
        }
        fun reserve(provider: VerificationProvider, kind: VerificationQueryKind, page: Int, request: String): RequestPermit {
            ensureOwned()
            if (!properties.workerEnabled) throw BudgetDeferred(VerificationBudgetReason.LOCK_BUSY)
            val requestKey = "${lease.id}-${UUID.randomUUID()}"
            val requestNode = mapper.readTree(request) as com.fasterxml.jackson.databind.node.ObjectNode
            requestNode.set<com.fasterxml.jackson.databind.JsonNode>("_dispatch", mapper.valueToTree(mapOf(
                "generation" to lease.generation, "ownerHash" to DiscoveryVerificationRepository.replayHash(token))))
            val reservation = repository.reserveAttemptWithBudget(lease.id, token, lease.generation,
                VerificationAttemptRequest(requestKey, provider, kind, page, requestNumber + 1, requestNode.toString()),
                properties.requestsPerMinute, properties.requestsPerDay, properties.maxRequestsPerItem, clock.instant())
            val id = reservation.attemptId ?: throw BudgetDeferred(requireNotNull(reservation.deferred), reservation.retryAt)
            requestNumber++
            requestKeys[id] = requestKey
            return RequestPermit(id, provider).also { activePermit = it }
        }
        fun completed(permit: RequestPermit, result: VerificationHttpResult<*>, response: Any? = null) {
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
            val responseJson = (if (result is VerificationHttpResult.Success) response else when (result) {
                is VerificationHttpResult.RateLimited -> result.diagnostic
                is VerificationHttpResult.TransientFailure -> result.diagnostic
                is VerificationHttpResult.PermanentFailure -> result.diagnostic
                else -> null
            })?.let { mapper.writeValueAsString(it) }
            if (lease.reviewEpoch > 0 && !repository.isOwned(lease.id, token, lease.generation, clock.instant())) {
                repository.completeDispatchedDiagnostic(lease.id, token, lease.generation, permit.attemptId,
                    requireNotNull(requestKeys[permit.attemptId]), status, code, responseJson,
                    (result as? VerificationHttpResult.Success)?.responseSha256, clock.instant())
                activePermit = null
                throw LeaseLost()
            }
            ensureOwned()
            val persisted = repository.completeAttemptAndProgress(lease.id, token, lease.generation, permit.attemptId, status, code,
                responseJson, (result as? VerificationHttpResult.Success)?.responseSha256,
                json(), entered, searched, candidatesAt, clock.instant())
            if (!persisted && lease.reviewEpoch > 0) {
                repository.completeDispatchedDiagnostic(lease.id, token, lease.generation, permit.attemptId,
                    requireNotNull(requestKeys[permit.attemptId]), status, code, responseJson,
                    (result as? VerificationHttpResult.Success)?.responseSha256, clock.instant())
                activePermit = null
                throw LeaseLost()
            }
            check(persisted) { "Verification completion not persisted" }
            activePermit = null
        }
        fun finish(decision: VerificationDecision) {
            ensureOwned()
            payload = payload.copy(evidence = decision.evidence, progress = payload.progress.copy(stage = "DECIDE"),
                diagnostics = (payload.diagnostics ?: WorkerDiagnostics()).copy(decision = decision.trace).bounded())
            val proposed = VerificationResult(VerificationState.COMPLETED, decision.outcome, decision.qualification, decision.route,
                json(), mapper.writeValueAsString(decision.reasonCodes), entered, searched, candidatesAt,
                if (decision.matchedPersonId != null) clock.instant() else null, decision.matchedPersonId)
            if (activeSteps.containsKey(4)) endStep(4, VerificationStepState.SUCCESS,
                mapOf("evidenceCount" to payload.evidence.size, "evidenceRef" to "item:${lease.id}/evidence_json"))
            if (activeSteps.containsKey(5)) endStep(5, if (decision.qualification == VerificationQualification.MET)
                VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapOf("outcome" to decision.outcome, "qualification" to decision.qualification), decision.reasonCodes)
            if (activeSteps.containsKey(6)) endStep(6, if (decision.route == VerificationRoute.APOLLO)
                VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapOf("searchRef" to "item:${lease.id}/evidence_json/search", "personId" to decision.matchedPersonId), decision.reasonCodes)
            else if (traces?.read(lease.id)?.steps?.lastOrNull { it.stepNo == 6 }?.state == VerificationStepState.NOT_STARTED)
                skipStep(6, if (decision.outcome == VerificationOutcome.DIRECT_CLEAR) "DIRECT_SUFFICIENT" else "PERSONAL_QUERY_NOT_REQUIRED")
            val committed = commits.commit(lease, proposed, commits.prepare(lease))
            if (committed != null) commits.project(committed)
        }
        fun fail(code: String, transientAttempts: Int = lease.transientAttempts) {
            if (code == "CONFIG_AUTH") authBlocked.set(true)
            val blocker = activeStepNo
            if (!activeSteps.containsKey(blocker)) startStep(blocker)
            endStep(blocker, VerificationStepState.ERROR, codes = listOf(code), blocking = true)
            if (traces != null) for (number in 1..7) if (traces.read(lease.id).steps.lastOrNull { it.stepNo == number }?.state == VerificationStepState.NOT_STARTED)
                skipStep(number, "BLOCKED_BY_STEP_$blocker")
            startStep(8, mapOf("error" to code))
            val record = activeSteps.remove(8)
            if (repository.saveFailure(lease.id, token, lease.generation,
                VerificationResult(VerificationState.ERROR, null, VerificationQualification.NOT_EVALUATED, VerificationRoute.NONE,
                    json(), mapper.writeValueAsString(listOf(code)), entered, searched, candidatesAt), transientAttempts, clock.instant())) {
                record?.let { traces!!.finishFailure(lease.id, lease.generation, it.id,
                    mapper.writeValueAsString(mapOf("error" to code, "applied" to false, "projection" to "NOT_REQUIRED")), clock.instant()) }
            }
        }
        fun retry(result: VerificationHttpResult<Nothing>) {
            val waitCode = when (result) {
                is VerificationHttpResult.RateLimited -> "RATE_LIMIT"
                is VerificationHttpResult.TransientFailure -> result.code
                is VerificationHttpResult.PermanentFailure -> result.code
                else -> "PROVIDER_ERROR"
            }
            if (activeSteps.containsKey(activeStepNo)) endStep(activeStepNo,
                if (result is VerificationHttpResult.RateLimited || waitCode == "APOLLO_CREDITS_EXHAUSTED")
                    VerificationStepState.INSUFFICIENT else VerificationStepState.ERROR,
                mapOf("retryAt" to (result as? VerificationHttpResult.RateLimited)?.retryAt,
                    "httpStatus" to when (result) {
                        is VerificationHttpResult.RateLimited -> result.httpStatus
                        is VerificationHttpResult.TransientFailure -> result.httpStatus
                        is VerificationHttpResult.PermanentFailure -> result.httpStatus
                        else -> null
                    }, "providerCode" to waitCode, "attemptsRef" to "item:${lease.id}/attempts"), listOf(waitCode))
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
            require(snapshot.identityHash == lease.identityHash && snapshot.factFingerprint == lease.factFingerprint) { "STALE_INPUT" }
            val run = repository.findRun(lease.ownerExecutionId) ?: error("Verification run missing")
            ensureOwned()
            traces?.initSteps(lease.id, clock.instant())
            startStep(1, mapOf("docId" to lease.expertDocId, "identityHash" to lease.identityHash,
                "factFingerprint" to lease.factFingerprint, "reviewEpoch" to lease.reviewEpoch))
            val baseline = if (traces != null) commits.prepare(lease) else null
            endStep(1, if (baseline?.protection == null) VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapOf("beforeRevision" to baseline?.admission?.revision, "beforeDecision" to baseline?.admission?.decision,
                    "protection" to baseline?.protection), listOfNotNull(baseline?.protection))
            val apolloAccess = try {
                run.frozenApolloAccess(mapper)
            } catch (_: Exception) {
                fail("CONFIG_INVALID")
                return
            }
            val matchingVersion = mapper.readTree(run.settingsJson).path("apolloMatchingVersion").asInt(1)
            require(matchingVersion in 1..2)
            val aliasVersion = mapper.readTree(run.settingsJson).path("institutionAliasVersion").asInt(0)
            require(aliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
            if (lease.evidenceJson == null) {
                val frozenRule = mapper.readTree(run.settingsJson).get("ruleVersion")
                require(frozenRule == null || frozenRule.isIntegralNumber && frozenRule.canConvertToInt()) {
                    "Invalid frozen rule version"
                }
                payload = payload.copy(ruleVersion = frozenRule?.intValue() ?: 1)
            }
            require(payload.ruleVersion in 0..4) { "Unsupported frozen rule version" }
            val recheck = run.recheckJson?.let { repository.recheck(run) }
            val root = mapper.readTree(lease.snapshotJson)
            val reviewNode = root.get("reviewInput")
            val reviewInput = reviewNode?.let {
                require(it.isObject && it.has("expectedAdmissionRevision")) { "Missing frozen admission baseline" }
                val revision = it.get("expectedAdmissionRevision")
                require(revision.isNull || revision.isIntegralNumber && revision.canConvertToLong() && revision.longValue() >= 0)
                mapper.treeToValue(it, VerificationReviewInput::class.java)
            }
            require(lease.reviewEpoch == 0L || recheck != null && reviewInput != null)
            if (lease.evidenceJson == null && reviewInput?.evidence != null) {
                val cached = reviewInput.evidence
                reviewInput.evidenceHash?.let {
                    // MySQL JSON reorders object keys. Restore the DTO's serialization order, but do not
                    // inject newly added default fields into a hash frozen by an older application.
                    fun frozenShape(canonical: com.fasterxml.jackson.databind.JsonNode, frozen: com.fasterxml.jackson.databind.JsonNode) {
                        if (canonical.isObject && frozen.isObject) {
                            val objectNode = canonical as com.fasterxml.jackson.databind.node.ObjectNode
                            objectNode.fieldNames().asSequence().toList().forEach { key ->
                                if (!frozen.has(key)) objectNode.remove(key) else frozenShape(objectNode.path(key), frozen.path(key))
                            }
                        } else if (canonical.isArray && frozen.isArray) {
                            canonical.forEachIndexed { index, value -> frozenShape(value, frozen.path(index)) }
                        }
                    }
                    val serialized = mapper.writeValueAsString(cached)
                    if (it != DiscoveryVerificationRepository.replayHash(serialized)) {
                        val exact = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                            .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true))
                        val canonical = exact.readTree(serialized)
                        frozenShape(canonical, reviewNode!!.path("evidence"))
                        require(it == DiscoveryVerificationRepository.replayHash(canonical.toString())) { "STALE_INPUT" }
                    }
                }
                require(cached.evidence.all { it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint }) {
                    "STALE_INPUT"
                }
                if (recheck?.spec?.evidenceMode == RecheckEvidenceMode.REUSE) payload = payload.copy(
                    evidence = cached.evidence, search = cached.search, orcidCompleted = cached.orcidCompleted,
                    institutionNormalization = cached.institutionNormalization)
            }
            if (payload.executionContext == null && lease.evidenceJson == null) {
                val ordinary = root.get("executionContext")?.let { mapper.treeToValue(it, VerificationExecutionContext::class.java) }
                if (recheck != null) payload = payload.copy(executionContext = VerificationExecutionContext(
                    recheck.spec.evaluatedAt, reviewInput!!.paperSourceRefs,
                    cacheReasons = if (recheck.spec.evidenceMode == RecheckEvidenceMode.REUSE)
                        listOf("CACHE_REUSED") + if (payload.orcidCompleted.none { it.document != null }) listOf("ORCID_NOT_REPARSED") else emptyList()
                    else emptyList()))
                else if (ordinary != null) payload = payload.copy(executionContext = ordinary)
            }
            val mode = recheck?.spec?.evidenceMode ?: RecheckEvidenceMode.REUSE
            val context = payload.executionContext
            startStep(2, mapOf("sources" to context?.paperSourceRefs.orEmpty()))
            if (context != null && context.paper == null) {
                val collected = if (context.paperSourceRefs.isNotEmpty()) papers?.collect(snapshot, context.paperSourceRefs, mode, context.evaluationAt) else null
                if (collected?.sources?.any { it.failure?.retryable == true } == true && lease.transientAttempts < properties.maxTransientAttempts) {
                    payload = payload.copy(executionContext = context.copy(paper = collected))
                    endStep(2, VerificationStepState.INSUFFICIENT, mapOf("failures" to collected.sources.filter { it.failure != null }.map { mapOf("source" to it.source, "failure" to it.failure) }), listOf("ARCHIVE_BUSY"))
                    // Keep the diagnostic in the retry trace; retry the same frozen sources, never replace them.
                    payload = payload.copy(executionContext = context.copy(paper = null))
                    repository.reschedule(lease.id, token, lease.generation, clock.instant().plusSeconds(30),
                        lease.transientAttempts + 1, json(), "[\"ARCHIVE_BUSY\"]", clock.instant())
                    return
                }
                payload = payload.copy(executionContext = context.copy(paper = collected),
                    evidence = (payload.evidence.filterNot { it.provider == "PAPER" && context.paperSourceRefs.any { ref -> ref.bodySha256 == it.responseSha256 } } +
                        collected?.sources.orEmpty().flatMap { it.evidence }).distinct())
            }
            val paperRead = payload.executionContext?.paper
            endStep(2, if (paperRead?.sources?.any { it.coverage != null } == true) VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapOf("sourceRefs" to payload.executionContext?.paperSourceRefs.orEmpty(),
                    "parsedBodies" to paperRead?.parsedBodies, "evidenceRef" to "executionContext.paper"),
                paperRead?.reasonCodes.orEmpty().ifEmpty { if (paperRead == null) listOf("SOURCE_UNAVAILABLE") else emptyList() })
            startStep(3, mapOf("identityHash" to snapshot.identityHash))
            endStep(3, if (snapshot.identityValid) VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapOf("reliableIdentity" to snapshot.identityValid, "paperBindings" to paperRead?.sources.orEmpty().map {
                    mapOf("source" to it.source, "authorIndex" to it.boundAuthorIndex, "method" to it.bindingMethod) }),
                if (snapshot.identityValid) emptyList() else listOf("IDENTITY_EVIDENCE_MISSING"), !snapshot.identityValid)
            if (!snapshot.identityValid) {
                for (number in 4..6) skipStep(number, "IDENTITY_EVIDENCE_MISSING")
                finish(evaluate(snapshot)); return
            }
            if (payload.progress.stage != "LOCAL_EVIDENCE") {
                // These stages predate this observation; do not invent elapsed time for their historical work.
                for (number in 4..5) skipStep(number, "PREVIOUS_STAGE_HISTORY_NOT_RECORDED")
            }
            if (payload.progress.stage == "LOCAL_EVIDENCE") {
                startStep(4, mapOf("mode" to mode, "evidenceRef" to "item:${lease.id}/evidence_json"))
                if (payload.executionContext != null && payload.executionContext!!.academic == null && academic != null) {
                    ensureOwned()
                    val academicMode = if (recheck?.spec?.evidenceMode == RecheckEvidenceMode.REFRESH)
                        RecheckEvidenceMode.REFRESH else RecheckEvidenceMode.REUSE
                    val collected = academic.collect(listOf(snapshot), academicMode, payload.executionContext!!.evaluationAt,
                        beforeDispatch = {
                            if (lease.reviewEpoch > 0 && !repository.isOwned(lease.id, token, lease.generation, clock.instant()))
                                throw java.util.concurrent.CancellationException("Recheck dispatch no longer authorized")
                        }).getValue(snapshot.identityHash)
                    ensureOwned()
                    if (collected.retryAt == null) payload = payload.copy(executionContext = payload.executionContext!!.copy(academic = collected))
                    if (collected.retryAt != null) {
                        endStep(4, VerificationStepState.INSUFFICIENT, mapOf("retryAt" to collected.retryAt), collected.reasonCodes)
                        repository.reschedule(lease.id, token, lease.generation, maxOf(clock.instant(), collected.retryAt),
                            lease.transientAttempts, json(), mapper.writeValueAsString(collected.reasonCodes), clock.instant())
                        return
                    }
                }
                payload = payload.copy(evidence = parser.rebuild(snapshot, payload.orcidCompleted.mapNotNull { it.document }, payload.evidence))
                fun directStep(decision: VerificationDecision) {
                    endStep(4, if (payload.evidence.isEmpty()) VerificationStepState.INSUFFICIENT else VerificationStepState.SUCCESS,
                        mapOf("evidenceCount" to payload.evidence.size, "academicRef" to "executionContext.academic",
                            "cacheReasons" to payload.executionContext?.cacheReasons.orEmpty()),
                        payload.executionContext?.academic?.reasonCodes.orEmpty())
                    startStep(5)
                    endStep(5, if (decision.outcome == VerificationOutcome.DIRECT_CLEAR) VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                        mapOf("outcome" to decision.outcome, "qualification" to decision.qualification,
                            "missingFields" to decision.missingFields), decision.reasonCodes,
                        decision.qualification == VerificationQualification.CONFLICT ||
                            matchingVersion == 1 && decision.qualification == VerificationQualification.NOT_MET)
                }
                if (recheck?.spec?.evidenceMode == RecheckEvidenceMode.REUSE) {
                    val cached = payload
                    payload = payload.copy(search = null, institutionNormalization = null)
                    val direct = evaluate(snapshot)
                    if (direct.outcome == VerificationOutcome.DIRECT_CLEAR) { directStep(direct); finish(direct); return }
                    payload = cached
                    if (payload.ruleVersion >= 4 && (!apolloAccess.detailEnabled || payload.search != null)) {
                        val baselineId = reviewNode?.path("baselineItemId")?.takeIf { it.isIntegralNumber }?.asLong()
                        val prior = baselineId?.let { repository.findItem(it) }
                        val cachedSearch = payload.search ?: ApolloSearchEvidence(matchingVersion = 2, institutionAliasVersion = aliasVersion)
                        val pages = if (cachedSearch.firstPages.isNotEmpty()) cachedSearch.firstPages else
                            if (prior != null && prior.expertDocId == lease.expertDocId && prior.identityHash == lease.identityHash &&
                                prior.factFingerprint == lease.factFingerprint)
                                FreeApolloVerification.retainedPages(repository.findAttempts(prior.id)) else emptyList()
                        val restored = cachedSearch.copy(freeSearchOnly = !apolloAccess.detailEnabled, firstPages = pages,
                            candidates = if (!apolloAccess.detailEnabled) pages.flatMap { it.result.candidates }.distinctBy { it.id } else cachedSearch.candidates)
                        payload = payload.copy(search = restored, institutionNormalization = payload.institutionNormalization?.let {
                            DiscoveryInstitutionNormalizer.withCandidates(it, restored.candidates)
                        })
                    }
                    directStep(direct)
                    startStep(6, mapOf("mode" to "REUSE", "searchRef" to "reviewInput.evidence.search"))
                    finish(evaluate(snapshot)); return
                }
                val existing = evaluate(snapshot)
                if (existing.outcome == VerificationOutcome.DIRECT_CLEAR) { directStep(existing); finish(existing); return }
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
                directStep(local)
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
                    search = ApolloSearchEvidence(matchingVersion = matchingVersion,
                        academicResearchFields = payload.executionContext?.academic?.let {
                            it.profileResearchFields ?: it.data?.topics?.joinToString(", ")
                        } ?: snapshot.profile.researchFields, institutionAliasVersion = aliasVersion,
                        freeSearchOnly = payload.ruleVersion >= 4 && !apolloAccess.detailEnabled),
                    institutionNormalization = normalization, queries = searchQueries(snapshot, payload.evidence, normalization,
                        formalName = matchingVersion == 2, ruleVersion = payload.ruleVersion))
                entered = entered ?: clock.instant()
                save()
            }
            if (payload.progress.stage == "APOLLO_SEARCH") startStep(6,
                mapOf("searchRef" to "item:${lease.id}/evidence_json/search", "requestCount" to requestNumber))
            if (payload.progress.stage == "APOLLO_SEARCH") {
                // Old in-flight payloads retain their original query sequence; new runs persist it before HTTP.
                var queries = payload.queries ?: legacySearchQueries(snapshot, payload.evidence)
                fun queryKind(index: Int) = if (queries[index].institution != null) VerificationQueryKind.NARROW
                    else if (index == 0) VerificationQueryKind.BROAD else VerificationQueryKind.VARIANT
                fun trace(index: Int, state: String, reason: String? = null, attemptId: Long? = null,
                    page: ApolloSearchPage? = null, confirmedPages: Int = 0) {
                    val previous = payload.diagnostics ?: WorkerDiagnostics()
                    val rows = queries.take(32).mapIndexed { i, query ->
                        val saved = previous.queries.firstOrNull { it.queryIndex == i } ?: WorkerQueryDiagnostic(i,
                            DiscoveryInstitutionNormalizer.sha256(mapper.writeValueAsString(query.copy(page = 1))), queryKind(i).name)
                        if (i != index) saved else saved.copy(state = state, stopReason = reason,
                            pagesReceived = if (page != null) maxOf(saved.pagesReceived + 1, page.page)
                                else maxOf(saved.pagesReceived, confirmedPages),
                            totalEntries = page?.totalEntries ?: saved.totalEntries,
                            candidateCount = saved.candidateCount + (page?.candidates?.size ?: 0),
                            lastAttemptId = attemptId ?: saved.lastAttemptId)
                    }
                    payload = payload.copy(diagnostics = previous.copy(queries = rows, totalQueryCount = queries.size).bounded())
                }
                while (payload.progress.queryIndex < queries.size) {
                    val index = payload.progress.queryIndex
                    val query = queries[index].copy(page = payload.progress.page)
                    // Old workers could acknowledge page five, persist page six, then crash before finish.
                    // An unacknowledged page has not advanced this durable cursor and is still retried.
                    if (query.page > properties.maxPagesPerQuery) {
                        payload = payload.copy(search = (payload.search ?: ApolloSearchEvidence()).copy(complete = false),
                            progress = payload.progress.copy(queryIndex = index + 1, page = 1, complete = false),
                            queries = queries)
                        trace(index, "TRUNCATED", "SEARCH_PAGE_LIMIT", confirmedPages = query.page - 1)
                        save()
                        continue
                    }
                    val permit = reserve(VerificationProvider.APOLLO, queryKind(index), query.page, mapper.writeValueAsString(query))
                    val result = apollo.search(query, permit)
                    if (result !is VerificationHttpResult.Success) {
                        val reason = when (result) {
                            is VerificationHttpResult.RateLimited -> "RATE_LIMIT"
                            is VerificationHttpResult.TransientFailure -> result.code
                            is VerificationHttpResult.PermanentFailure -> result.code
                            else -> error("Unexpected search result")
                        }
                        trace(index, "ERROR", reason, permit.attemptId)
                        completed(permit, result); retry(result as VerificationHttpResult<Nothing>); return
                    }
                    val page = result.value
                    val truncated = page.hasNext && query.page >= properties.maxPagesPerQuery
                    val last = index == queries.lastIndex && !page.hasNext
                    val finished = if (!page.hasNext) (payload.progress.finishedQueryKeys + index.toString()).distinct()
                        else payload.progress.finishedQueryKeys
                    val allComplete = queries.indices.all { it.toString() in finished }
                    val search = (payload.search ?: ApolloSearchEvidence()).merge(page, query, index == 0, allComplete, permit.attemptId)
                        .let { if (truncated) it.copy(complete = false) else it }
                    payload = payload.copy(search = search, queries = queries,
                        institutionNormalization = payload.institutionNormalization?.let {
                            DiscoveryInstitutionNormalizer.withCandidates(it, search.candidates)
                        },
                        progress = VerificationProgress("APOLLO_SEARCH",
                            if (page.hasNext && !truncated) index else index + 1,
                            if (page.hasNext && !truncated) query.page + 1 else 1,
                            search.complete, search.candidates.map { it.id }, finished))
                    if (last && payload.ruleVersion >= 1 && search.matchingVersion == 2 &&
                        search.candidates.none { rules.nameMatches(it, rules.allowedNames(snapshot, payload.evidence), search.queryNames, true, payload.ruleVersion) &&
                            rules.matchReason(snapshot, payload.evidence, it, null, search.institutionAliasVersion, payload.ruleVersion) in
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
                    trace(index, if (truncated) "TRUNCATED" else if (page.hasNext) "RUNNING" else "EXHAUSTED",
                        if (truncated) "SEARCH_PAGE_LIMIT" else null, permit.attemptId, page)
                    completed(permit, result, page)
                }
                if (payload.ruleVersion >= 4 && !apolloAccess.detailEnabled) {
                    payload = payload.copy(search = payload.search?.copy(freeSearchOnly = true))
                    finish(evaluate(snapshot)); return
                }
                if (payload.search?.complete != true) {
                    val decision = evaluate(snapshot)
                    finish(decision.copy(outcome = VerificationOutcome.PENDING, route = VerificationRoute.NONE,
                        matchedPersonId = null,
                        reasonCodes = (decision.reasonCodes + "SEARCH_INCOMPLETE" + "SEARCH_PAGE_LIMIT").distinct()))
                    return
                }
                if (payload.search?.matchingVersion == 2 && payload.search?.detailsComplete != true) {
                    // Search pagination is finished and durable before detail calls begin. Recovery never repeats
                    // acknowledged details or changes the saved query sequence.
                    val names = rules.allowedNames(snapshot, payload.evidence)
                    fun mergeDetail(candidate: ApolloCandidate, detail: ApolloCandidate) {
                        require(detail.id == candidate.id)
                        // Search is a projection. Preserve missing identity hints but retain real ORCID conflicts.
                        val updated = detail.copy(orcid = detail.orcid ?: candidate.orcid,
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
                    }
                    val reusable = mutableMapOf<String, VerificationAttempt>()
                    val lookedUp = mutableSetOf<String>()
                    while (true) {
                        val candidates = payload.search!!.candidates.filter {
                            (rules.nameMatches(it, names, payload.search!!.queryNames, masked = true, ruleVersion = payload.ruleVersion) ||
                                payload.ruleVersion >= 1 && rules.emailNameMatches(snapshot, payload.evidence, it, payload.search!!.institutionAliasVersion, masked = true))
                        }
                        val strong = candidates.filter { rules.matchReason(snapshot, payload.evidence, it, null, payload.search?.institutionAliasVersion ?: 0, payload.ruleVersion) in
                            setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
                        // Current institution / professor evidence can distinguish a candidate from unrelated
                        // same-name people. Only fall back to history/specialty when no strong candidate exists.
                        val needingDetails = strong.ifEmpty { candidates }.filter {
                            it.id !in payload.search!!.detailsCompleted &&
                                !(it in strong && (it.fullName != null || it.lastName != null))
                        }
                        val candidate = needingDetails.firstOrNull() ?: break
                        if (!apolloAccess.detailEnabled) {
                            // Permissions apply before both paid evidence reuse and request reservation.
                            val decision = evaluate(snapshot)
                            finish(decision.copy(reasonCodes = (decision.reasonCodes + "PAID_DETAILS_DISABLED").distinct()))
                            return
                        }
                        val ids = needingDetails.map { it.id }.filter { it !in lookedUp }
                        if (ids.isNotEmpty()) {
                            reusable.putAll(repository.findSuccessfulDetails(lease.ownerExecutionId, ids))
                            lookedUp.addAll(ids)
                        }
                        val source = reusable[candidate.id]
                        if (source != null) {
                            val detail = mapper.readValue<ApolloCandidate>(requireNotNull(source.responseJson))
                            mergeDetail(candidate, detail)
                            payload = payload.copy(diagnostics = (payload.diagnostics ?: WorkerDiagnostics()).reuse(
                                WorkerDetailReuse(source.id, source.itemId, candidate.id, requireNotNull(source.responseSha256))))
                            save()
                            continue
                        }
                        val permit = reserve(VerificationProvider.APOLLO, VerificationQueryKind.DETAIL, 1,
                            mapper.writeValueAsString(mapOf("personId" to candidate.id, "matchingVersion" to 2)))
                        val result = apollo.detail(candidate.id, permit)
                        if (result is VerificationHttpResult.Success) {
                            val detail = result.value
                            mergeDetail(candidate, detail)
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
                    if (lease.reviewEpoch == 0L && payload.search!!.candidates.isNotEmpty()) {
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
                    if (activeSteps.containsKey(activeStepNo)) endStep(activeStepNo, VerificationStepState.INSUFFICIENT,
                        mapOf("retryAt" to next), listOf(deferred.reason.name))
                    repository.reschedule(lease.id, token, lease.generation, next, lease.transientAttempts, json(),
                        mapper.writeValueAsString(listOf(deferred.reason.name)), now)
                }
            }
        } catch (_: java.util.concurrent.CancellationException) {
            // A provider guard refused dispatch. Cancellation owns item convergence; no error/lease writes.
        } catch (error: Exception) {
            // Persistence failure must stop the request chain. Leave its lease/cursor for durable recovery.
            // Only preflight failures with no unacknowledged HTTP may be recorded as an interface-safe ERROR.
            if (activePermit == null && !lost.get()) {
                payload = payload.copy(diagnostics = (payload.diagnostics ?: WorkerDiagnostics()).copy(
                    failure = WorkerFailureDiagnostic(activeStepNo, error.javaClass.simpleName.take(80),
                        error.stackTrace.firstOrNull { it.className.startsWith("com.weibo.") }?.toString()?.take(240))))
                fail(if (error.message == "STALE_INPUT") "STALE_INPUT" else "VERIFICATION_FAILED")
            }
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
