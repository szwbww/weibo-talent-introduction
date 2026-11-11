package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** All network reads finish in prepare/project, outside commit's SQL transaction. */
data class VerificationCommitInput(val snapshot: VerificationSnapshot?, val admission: ExpertDiscoveryAdmission?, val protection: String?)

/** Not a bypass flag: minted only from a complete server receipt under the current locked lease. */
class RetainedIdentityAdmissionAuthority private constructor(internal val item: VerificationItem,
    internal val supplement: IdentitySupplement) {
    companion object {
        internal fun from(item: VerificationItem): RetainedIdentityAdmissionAuthority {
            val s = requireNotNull(RetainedPaperIdentityService.supplement(item))
            require(RetainedPaperIdentityService.valid(item, s) && s.mode == VerificationMode.ENFORCE &&
                s.ownerHash == DiscoveryVerificationRepository.replayHash(requireNotNull(item.leaseToken)))
            return RetainedIdentityAdmissionAuthority(item, s)
        }
    }
}

@Service
class DiscoveryVerificationCommitService(
    private val repository: DiscoveryVerificationRepository,
    private val admissions: DiscoveryReviewRepository,
    private val review: DiscoveryReviewService,
    private val policy: DiscoveryAdmissionPolicy,
    private val writer: ExpertIndexWriterService,
    private val jdbc: JdbcTemplate,
    private val clock: Clock = Clock.systemUTC(),
    private val traces: com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository =
        com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationTraceRepository(jdbc)
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun prepare(item: VerificationItem): VerificationCommitInput {
        val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)
        // A failed layer read must propagate, not become absence of protection.
        writer.readDiscoveryDocument(ExpertIndexLevel.CANDIDATE, item.expertDocId)
        val application = writer.readDiscoveryDocument(ExpertIndexLevel.APPLICATION, item.expertDocId)
        val profile = raw?.let { writer.discoveryProfile(item.expertDocId, it.source) }
        val snapshot = profile?.let { DiscoveryVerificationAdmissionGate.snapshot(item.expertDocId, it, policy.evaluate(item.expertDocId, it).configSnapshot) }
        val protection = when {
            application != null -> "APPLICATION_PRESENT"
            profile == null -> null
            profile.dataSource.orEmpty().contains("SBIR", true) || profile.emailSource.orEmpty().contains("SBIR", true) -> "SBIR_PROTECTED"
            contacted(snapshot!!) -> "CONTACT_PROTECTED"
            else -> null
        }
        return VerificationCommitInput(snapshot, admissions.findAdmission(item.expertDocId), protection)
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    fun commit(lease: VerificationItem, proposed: VerificationResult, input: VerificationCommitInput): VerificationItem? {
        if (lease.reviewEpoch > 0 && !repository.lockRunAccepting(lease.ownerExecutionId)) return null
        val item = repository.lockForCommit(lease.id)
        val now = clock.instant()
        if (item.state != VerificationState.RUNNING || item.leaseToken != lease.leaseToken || item.generation != lease.generation ||
            item.leaseUntil?.let { it > now } != true) return null
        val current = admissions.findAdmission(item.expertDocId)
        val reviewInput = if (item.reviewEpoch > 0) mapper.readTree(item.snapshotJson).path("reviewInput") else null
        val expectedRevision = if (reviewInput != null) {
            require(reviewInput.isObject && reviewInput.has("expectedAdmissionRevision")) { "Missing frozen admission revision" }
            val revision = reviewInput.get("expectedAdmissionRevision")
            require(revision.isNull || revision.isIntegralNumber && revision.canConvertToLong() && revision.longValue() >= 0)
            if (revision.isNull) null else revision.longValue()
        } else input.admission?.revision
        val traceRecorded = traces.read(item.id).recorded
        val supplement = RetainedPaperIdentityService.supplement(item)
        val validSupplement = supplement?.let { RetainedPaperIdentityService.valid(item, it) &&
            it.ownerHash == DiscoveryVerificationRepository.replayHash(lease.leaseToken) } == true
        val beforeSnapshot = if (validSupplement) mapper.treeToValue(supplement!!.beforeSnapshot, VerificationSnapshot::class.java) else null
        val comparison: VerificationSnapshot? = if (validSupplement && supplement!!.mode == VerificationMode.AUDIT) beforeSnapshot else
            mapper.readValue(item.snapshotJson, VerificationSnapshot::class.java)
        val effective = if (validSupplement) mapper.readValue(item.snapshotJson, VerificationSnapshot::class.java) else input.snapshot
        val controlledOldAdmission = validSupplement &&
            current?.identityHash == supplement!!.beforeAdmissionIdentity && current?.revision == supplement.beforeAdmissionRevision &&
            current?.identityHash == supplement.beforeIdentityHash
        val combined = if (traceRecorded) traces.startStep(item.id, lease.leaseToken!!, lease.generation, 7,
            mapper.writeValueAsString(mapOf("beforeDecision" to (reviewInput?.path("beforeDecision")?.asText(null) ?: input.admission?.decision),
                "expectedAdmissionRevision" to expectedRevision, "proposedOutcome" to proposed.outcome,
                "proposedQualification" to proposed.qualification, "proposedRoute" to proposed.route)),
            "{}", now) else null
        val changed = when {
            input.snapshot == null -> "SOURCE_MISSING"
            supplement != null && !validSupplement && !(supplement.proposal.incoming == null &&
                supplement.phase in setOf("UNCHANGED", "UNAVAILABLE", "CONFLICT", "PROTECTED")) -> "INVALID_IDENTITY_SUPPLEMENT"
            input.snapshot.identityHash != comparison!!.identityHash -> "IDENTITY_CHANGED"
            input.snapshot.factFingerprint != comparison.factFingerprint -> "FACT_CHANGED"
            else -> null
        }
        val protection = input.protection ?: when {
            supplement?.phase in setOf("PROTECTED", "CONFLICT") -> supplement!!.proposal.reason
            (current?.identityHash == item.identityHash || controlledOldAdmission) && (current?.decisionEnum?.manual == true || current?.decision == "LEGACY_APPROVED") -> "REVIEW_PROTECTED"
            (current?.identityHash == item.identityHash || controlledOldAdmission) && current?.decision == "AUTO_PASSED" && current.policyVersion != DiscoveryVerificationAdmissionGate.POLICY_VERSION -> "LEGACY_AUTO_PROTECTED"
            input.snapshot != null && contacted(input.snapshot) -> "CONTACT_PROTECTED"
            else -> null
        }
        fun withheld(code: String, protected: Boolean = false) = proposed.copy(
            outcome = if (protected) VerificationOutcome.PROTECTED else VerificationOutcome.PENDING,
            qualification = if (protected) VerificationQualification.NOT_EVALUATED else VerificationQualification.UNKNOWN,
            route = VerificationRoute.NONE, reasonsJson = mapper.writeValueAsString(listOf(code)),
            admissionRevision = null, projectionState = VerificationProjection.NOT_REQUIRED)
        var result = when {
            changed != null -> withheld(if (item.reviewEpoch > 0) "STALE_INPUT" else changed)
            protection != null -> withheld(protection, true)
            current != null && current.identityHash != item.identityHash && !controlledOldAdmission -> withheld("IDENTITY_CHANGED")
            current?.revision != expectedRevision || item.reviewEpoch == 0L && current?.identityHash != input.admission?.identityHash -> withheld("ADMISSION_CHANGED")
            else -> proposed
        }
        var automatic: AutomaticAdmissionResult? = null
        val admitted = result.outcome in ADMITTED
        if (admitted) {
            // Give the existing policy/gate the exact proposed completed row, never a second qualification implementation.
            val staged = item.copy(state = result.state, outcome = result.outcome, qualification = result.qualification,
                route = result.route, evidenceJson = result.evidenceJson, reasonsJson = result.reasonsJson,
                apolloPersonId = result.apolloPersonId, personMatchedAt = result.personMatchedAt,
                completedAt = now, leaseToken = null, leaseUntil = null, policyVersion = VerificationMode.ENFORCE.policyVersion)
            automatic = policy.evaluate(item.expertDocId, effective!!.profile,
                VerificationAdmissionContext(item.expertDocId, effective.profile, listOf(staged)))
            if (automatic.status != DiscoveryAdmissionStatus.AUTO_PASSED) {
                result = withheld("HARD_BLOCKED").copy(reasonsJson = mapper.writeValueAsString(automatic.blockingReasons.map { it.code }))
            }
        }
        combined?.let {
            check(traces.finishStep(item.id, lease.leaseToken!!, lease.generation, it.id,
                if (result.outcome in ADMITTED) VerificationStepState.SUCCESS else VerificationStepState.INSUFFICIENT,
                mapper.writeValueAsString(mapOf("before" to mapOf("decision" to current?.decision, "revision" to current?.revision),
                    "proposed" to mapOf("outcome" to proposed.outcome, "qualification" to proposed.qualification, "route" to proposed.route),
                    "effective" to mapOf("outcome" to result.outcome, "qualification" to result.qualification, "route" to result.route),
                    "policyBlockers" to automatic?.blockingReasons?.map { reason -> reason.code })),
                mapper.writeValueAsString(mapOf("codes" to mapper.readTree(result.reasonsJson),
                    "blocking" to (result.outcome !in ADMITTED))), now))
        }
        val record = if (traceRecorded) traces.startStep(item.id, lease.leaseToken!!, lease.generation, 8,
            mapper.writeValueAsString(mapOf("mode" to item.policyVersion, "beforeRevision" to current?.revision)), "{}", now) else null
        check(repository.saveEvidenceAndOutcome(item.id, lease.leaseToken!!, lease.generation, result, now, queueCountryRefresh = true))
        if (item.policyVersion == VerificationMode.ENFORCE.policyVersion && result.outcome in ADMITTED) {
            // Evidence is already visible in this transaction. The shared CAS path must confirm a real row.
            val persisted = if (controlledOldAdmission && current?.identityHash != item.identityHash)
                review.recordAutomaticPersisted(item.expertDocId, effective!!.profile, requireNotNull(automatic),
                    RetainedIdentityAdmissionAuthority.from(item))
            else review.recordAutomaticPersisted(item.expertDocId, effective!!.profile, requireNotNull(automatic))
            repository.attachAdmission(item.id, persisted.revision, now)
        }
        val persisted = repository.findItem(item.id) ?: return null
        record?.let {
            check(traces.finishCommittedStep(item.id, lease.generation, it.id, VerificationStepState.SUCCESS,
                mapper.writeValueAsString(mapOf("applied" to (persisted.admissionRevision != null),
                    "after" to admissions.findAdmission(item.expertDocId)?.let { admission ->
                        mapOf("decision" to admission.decision, "revision" to admission.revision) },
                    "persistedRevision" to persisted.admissionRevision, "projection" to persisted.projectionState,
                    "message" to if (item.policyVersion == VerificationMode.AUDIT.policyVersion) "AUDIT_ONLY_NOT_APPLIED"
                        else if (persisted.admissionRevision == null) "SUGGESTION_NOT_APPLIED_CURRENT_ADMISSION_UNCHANGED" else "ADMISSION_COMMITTED")),
                null, now))
        }
        return persisted
    }

    /** Baseline refusal is terminal only while the original run/lease still owns the item. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    fun finishStaleIdentity(lease: VerificationItem, result: VerificationResult, stepId: Long?): Boolean {
        require(lease.reviewEpoch > 0 && result.state == VerificationState.COMPLETED &&
            result.outcome == VerificationOutcome.PENDING && result.admissionRevision == null &&
            result.reasonsJson == "[\"STALE_INPUT\"]")
        if (!repository.lockRunAccepting(lease.ownerExecutionId)) return false
        val now = clock.instant()
        if (!repository.saveEvidenceAndOutcome(lease.id, requireNotNull(lease.leaseToken), lease.generation, result, now)) return false
        stepId?.let {
            check(traces.finishCommittedStep(lease.id, lease.generation, it, VerificationStepState.SUCCESS,
                mapper.writeValueAsString(mapOf("applied" to false, "projection" to "NOT_REQUIRED",
                    "reason" to "STALE_INPUT")), null, now))
        }
        return true
    }

    /** Projection retries never redo qualification or call providers. Historical admission events stay intact. */
    fun project(item: VerificationItem) {
        val revision = item.admissionRevision ?: return
        if (item.projectionState !in setOf(VerificationProjection.PENDING, VerificationProjection.ERROR) || item.projectionError == "SUPERSEDED_BY_REVIEW") return
        val traceRecorded = traces.read(item.id).recorded
        val attempt = if (traceRecorded) traces.startProjection(item.id, revision, clock.instant()) ?: return else null
        fun current(): Boolean = admissions.findAdmission(item.expertDocId)?.let {
            it.revision == revision && it.identityHash == item.identityHash && it.decision == "AUTO_PASSED"
        } == true
        fun mark(state: VerificationProjection, code: String? = null) {
            if (repository.markProjection(item.id, revision, state, code, clock.instant())) attempt?.let {
                traces.finishProjection(item.id, revision, it.id,
                    mapper.writeValueAsString(mapOf("applied" to true, "persistedRevision" to revision,
                        "after" to admissions.findAdmission(item.expertDocId)?.let { admission ->
                            mapOf("decision" to admission.decision, "revision" to admission.revision) },
                        "projection" to state, "error" to code, "providerCalls" to 0)), code, clock.instant())
            }
        }
        try {
            if (!current()) { mark(VerificationProjection.ERROR, "SUPERSEDED_BY_REVIEW"); return }
            val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)
            if (raw == null) { mark(VerificationProjection.ERROR, "SOURCE_MISSING"); return }
            val profile = writer.discoveryProfile(item.expertDocId, raw.source)
            val snapshot = DiscoveryVerificationAdmissionGate.snapshot(item.expertDocId, profile, policy.evaluate(item.expertDocId, profile).configSnapshot)
            if (snapshot.identityHash != item.identityHash ||
                (snapshot.factFingerprint != item.factFingerprint && DiscoveryVerificationAdmissionGate.countryCompatibleSnapshot(
                    item, profile, policy.evaluate(item.expertDocId, profile).configSnapshot) == null) || !current()) {
                mark(VerificationProjection.ERROR, "SUPERSEDED_BY_REVIEW"); return
            }
            val sync = review.projectApprovedCandidate(item.expertDocId, raw.source, "AUTO_PASSED")
            if (!current()) { mark(VerificationProjection.ERROR, "SUPERSEDED_BY_REVIEW"); return }
            val state = when (sync.result) {
                "PROJECTED" -> VerificationProjection.PROJECTED
                "ALREADY_PRESENT" -> VerificationProjection.ALREADY_PRESENT
                "APPLICATION_PRESENT" -> VerificationProjection.APPLICATION_PRESENT
                else -> VerificationProjection.ERROR
            }
            mark(state, if (state == VerificationProjection.ERROR) sync.errorCode ?: sync.result else null)
        } catch (_: Exception) { mark(VerificationProjection.ERROR, "CANDIDATE_SYNC_FAILED") }
    }

    private fun contacted(snapshot: VerificationSnapshot): Boolean {
        val profile = snapshot.profile
        val email = DiscoveryIdentity.normalizedEmail(profile.email)
        // RAW's primary orcidId is the ES document key, not the bound external identity.
        val orcid = profile.externalIds?.let { runCatching { mapper.readTree(it) }.getOrNull() }
            ?.path("orcid")?.takeIf { it.isTextual }?.asText()?.let(::normalizeOrcid)
        return jdbc.queryForObject("""SELECT (EXISTS(SELECT 1 FROM expert_contact WHERE LOWER(TRIM(expert_email))=? OR orcid_id=?)
            OR EXISTS(SELECT 1 FROM expert_email_alias WHERE normalized_email=?))""", Boolean::class.java,
            email, orcid, email) == true
    }

    companion object {
        private val ADMITTED = setOf(VerificationOutcome.DIRECT_CLEAR, VerificationOutcome.APOLLO_CLEAR, VerificationOutcome.APOLLO_UNCLEAR)
    }
}
