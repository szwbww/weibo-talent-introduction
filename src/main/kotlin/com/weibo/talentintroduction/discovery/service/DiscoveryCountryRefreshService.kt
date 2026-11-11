package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.expert.service.ExpertRevalidationService
import com.weibo.talentintroduction.expert.service.PromotionOutcome
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock

/** Country-only continuation. Frozen qualification facts/outcomes and provider cursors stay unchanged.
 * COMPLETED + next_attempt_at is a durable country outbox, disjoint from the qualification worker queue.
 */
@Service
class DiscoveryCountryRefreshService(
    private val countries: DiscoveryCountrySupplementService,
    private val writer: ExpertIndexWriterService,
    private val repository: DiscoveryVerificationRepository,
    private val jdbc: JdbcTemplate,
    private val revalidation: ExpertRevalidationService? = null,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val log = LoggerFactory.getLogger(javaClass)

    /** One bounded item per tick; SQL CAS permits one owner across scheduler instances. An abandoned
     * five-minute claim becomes due again after a restart. No Apollo call or qualification replay. */
    @Scheduled(fixedDelay = 30000)
    fun retryPending() {
        val now = clock.instant()
        val id = jdbc.queryForList("""SELECT id FROM discovery_verification_item
            WHERE state='COMPLETED' AND next_attempt_at<=? ORDER BY next_attempt_at,id LIMIT 1""",
            Long::class.java, now.verificationUtc()).firstOrNull() ?: return
        val item = repository.findItem(id) ?: return
        if (item.state != VerificationState.COMPLETED || item.nextAttemptAt == null || item.nextAttemptAt > now) return
        val claimUntil = now.plusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
        if (jdbc.update("""UPDATE discovery_verification_item SET next_attempt_at=?
            WHERE id=? AND state='COMPLETED' AND generation=? AND next_attempt_at=?""",
                claimUntil.verificationUtc(), id, item.generation, item.nextAttemptAt.verificationUtc()) != 1) return
        val claimed = item.copy(nextAttemptAt = claimUntil)
        try {
            refresh(claimed, mapper.readValue(item.evidenceJson ?: "{}", VerificationWorkerEvidence::class.java))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); throw e
        } catch (e: Exception) {
            log.warn("event=DISCOVERY_COUNTRY_RETRY_FAILED itemId={} error={}", id, e.javaClass.simpleName)
            // A malformed saved payload is bounded too. If SQL fails, leave the lease for restart recovery.
            save(claimed, linkedMapOf("error" to e.javaClass.simpleName), "RETRY_FAILED")
        }
    }

    fun refresh(item: VerificationItem, evidence: VerificationWorkerEvidence) {
        if (item.policyVersion != VerificationMode.ENFORCE.policyVersion || item.state != VerificationState.COMPLETED) return
        val report = linkedMapOf<String, Any?>()
        try {
            val latest = repository.findItem(item.id) ?: return
            if (latest.state != VerificationState.COMPLETED || latest.generation != item.generation ||
                latest.nextAttemptAt != item.nextAttemptAt) return
            val run = repository.findRun(item.ownerExecutionId)
            if (run == null || !permitted(run)) {
                report["status"] = "OWNER_STOPPED"; save(item, report); return
            }
            // Country changes invalidate frozen tokens, so finish the initial admission projection first.
            if (latest.projectionState in setOf(VerificationProjection.PENDING, VerificationProjection.ERROR)) {
                save(item, report, "PROJECTION_PENDING"); return
            }
            val recheck = run.recheckJson?.let { repository.recheck(run) }
            val allowHttp = recheck == null || recheck.spec.evidenceMode == RecheckEvidenceMode.REFRESH
            val frozen = mapper.readValue(item.snapshotJson, VerificationSnapshot::class.java).profile
            val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)
            if (raw == null) { report["status"] = "RAW_MISSING"; save(item, report); return }
            val profile = writer.discoveryProfile(item.expertDocId, raw.source)
            if (!DiscoveryIdentity.allowed(profile) || profile.identityVerification != frozen.identityVerification ||
                DiscoveryIdentity.normalizedEmail(profile.email) != DiscoveryIdentity.normalizedEmail(frozen.email) ||
                profile.givenNames != frozen.givenNames || profile.familyNames != frozen.familyNames) {
                report["status"] = "IDENTITY_CHANGED"; save(item, report); return
            }
            val retained = evidence.orcidCompleted.firstOrNull { it.endpoint == "employments" }?.document
                ?.takeIf { it.body.findValues("country").isNotEmpty() }
            val result = countries.supplement(item.ownerExecutionId, item.expertDocId, profile, null, retained, allowHttp)
            if (result.receipt == null) { report["status"] = "DISABLED"; save(item, report); return }
            val country = result.profile.country
            val priorReceipt = mapper.readTree(item.evidenceJson ?: "{}").path("countrySupplement").path("receipt")
            // A later layer retry sees an existing RAW country. Retain the observation that established it.
            report["receipt"] = if (result.receipt.reason == "EXISTING_VALUE" &&
                priorReceipt.path("status").asText() == "FILLED" && priorReceipt.path("afterCountry").asText() == country)
                priorReceipt else result.receipt
            report["lastReason"] = result.receipt.reason
            val layers = linkedMapOf<String, String>()
            report["layers"] = layers
            if (!country.isNullOrBlank()) for (level in ExpertIndexLevel.values().sortedBy { if (it == ExpertIndexLevel.RAW) 0 else 1 }) {
                val document = if (level == ExpertIndexLevel.RAW) raw else writer.readDiscoveryDocument(level, item.expertDocId)
                if (document == null) { layers[level.name] = "ABSENT"; continue }
                val current = writer.discoveryProfile(item.expertDocId, document.source)
                if (current.identityVerification != profile.identityVerification ||
                    current.givenNames != profile.givenNames || current.familyNames != profile.familyNames ||
                    DiscoveryIdentity.normalizedEmail(current.email) != DiscoveryIdentity.normalizedEmail(profile.email) ||
                    current.institution != profile.institution) {
                    layers[level.name] = "IDENTITY_OR_INSTITUTION_CHANGED"; continue
                }
                if (!current.country.isNullOrBlank()) {
                    layers[level.name] = if (current.country == country) "ALREADY_PRESENT" else "COUNTRY_CONFLICT"; continue
                }
                if (!country.matches(Regex("[A-Z]{2}"))) { layers[level.name] = "INVALID_COUNTRY"; continue }
                val token = current.institutionEvidence?.let {
                    if (!DiscoveryIdentity.validInstitutionEvidence(current)) null
                    else DiscoveryIdentity.institutionEvidence(current.copy(country = country), it.substringBefore(':'))
                }
                if (!current.institution.isNullOrBlank() && token == null) {
                    layers[level.name] = "INSTITUTION_EVIDENCE_INVALID"; continue
                }
                if (!permitted(run)) { report["status"] = "OWNER_STOPPED"; save(item, report); return }
                layers[level.name] = if (writer.writeDiscoveryCountryIfVersionMatches(level, item.expertDocId, document, country, token))
                    "UPDATED" else "WRITE_CONFLICT_OR_FAILURE"
                // Do not propagate a stale observation after losing RAW's CAS.
                if (level == ExpertIndexLevel.RAW && layers[level.name] == "WRITE_CONFLICT_OR_FAILURE") break
            }
            var retry = result.receipt.reason.takeIf { allowHttp && it in PROVIDER_RETRY_REASONS }
            if (layers.containsValue("WRITE_CONFLICT_OR_FAILURE")) retry = "WRITE_FAILED"
            if (DiscoveryAdmissionPolicy.excludedLocation(country) && layers["RAW"] in setOf("UPDATED", "ALREADY_PRESENT")) {
                if (!permitted(run)) { report["status"] = "OWNER_STOPPED"; save(item, report); return }
                if (contacted(profile)) report["admission"] = "CONTACT_PROTECTED"
                else {
                    // Shared revalidation retains manual decisions and APPLICATION protection.
                    val outcome = revalidation?.revalidateDiscovery(item.expertDocId)
                    report["admission"] = outcome?.javaClass?.simpleName ?: "UNAVAILABLE"
                    if (outcome == null || outcome == PromotionOutcome.WriteFailed || outcome == PromotionOutcome.ExistenceCheckFailed)
                        retry = "ADMISSION_WRITE_FAILED"
                }
            }
            save(item, report, retry)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); throw e
        } catch (e: Exception) {
            log.warn("event=DISCOVERY_COUNTRY_REFRESH_FAILED itemId={} error={}", item.id, e.javaClass.simpleName)
            report["error"] = e.javaClass.simpleName
            save(item, report, "REFRESH_FAILED")
        }
    }

    private fun permitted(run: VerificationRun): Boolean {
        if (run.mode != VerificationMode.ENFORCE) return false
        if (repository.isPaperReprocessOwned(run.executionId) && !repository.paperRunPermission(run.executionId)) return false
        if (jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM task_execution
                WHERE id=? AND status IN ('CANCELLED','INTERRUPTED','FAILED'))""", Boolean::class.java, run.executionId) == true) return false
        val latest = repository.findRun(run.executionId) ?: return false
        return latest.recheckJson == null || repository.recheck(latest).control.phase !in setOf(
            RecheckPhase.CANCELLED, RecheckPhase.CANCELLING, RecheckPhase.INTERRUPTED, RecheckPhase.FAILED)
    }

    private fun contacted(profile: ExpertProfile): Boolean {
        val email = DiscoveryIdentity.normalizedEmail(profile.email)
        val orcid = profile.externalIds?.let { mapper.readTree(it).path("orcid").asText(null) }?.let(::normalizeOrcid)
        return jdbc.queryForObject("""SELECT (EXISTS(SELECT 1 FROM expert_contact WHERE LOWER(TRIM(expert_email))=? OR orcid_id=?)
            OR EXISTS(SELECT 1 FROM expert_email_alias WHERE normalized_email=?))""", Boolean::class.java, email, orcid, email) == true
    }

    private fun save(item: VerificationItem, report: MutableMap<String, Any?>, reason: String? = null) {
        val saved = runCatching { mapper.readTree(item.evidenceJson ?: "{}") }.getOrNull()
            ?.path("countrySupplement") ?: mapper.createObjectNode()
        for (field in listOf("receipt", "layers", "admission")) {
            if (!report.containsKey(field) && saved.has(field)) report[field] = saved.path(field)
        }
        val previous = saved.path("retry")
        val failures = previous.path("failures").asInt(0) + if (reason != null && reason !in WAIT_REASONS) 1 else 0
        val next = if (reason != null && failures < 3) countries.nextRetryAt(reason).truncatedTo(java.time.temporal.ChronoUnit.MILLIS) else null
        report["retry"] = mapOf("state" to if (next != null) "PENDING" else if (reason != null) "EXHAUSTED" else "DONE",
            "reason" to reason, "attempts" to previous.path("attempts").asInt(0) + 1, "failures" to failures,
            "nextAttemptAt" to next?.toString())
        jdbc.update("""UPDATE discovery_verification_item
            SET evidence_json=JSON_SET(COALESCE(evidence_json,'{}'),'$.countrySupplement',CAST(? AS JSON)),next_attempt_at=?,updated_at=?
            WHERE id=? AND state='COMPLETED' AND generation=? AND next_attempt_at <=> ?""",
            mapper.writeValueAsString(report), next?.verificationUtc(), clock.instant().verificationUtc(),
            item.id, item.generation, item.nextAttemptAt?.verificationUtc())
    }

    companion object {
        private val WAIT_REASONS = setOf("BUDGET_EXHAUSTED", "CHANNEL_BUSY", "RATE_LIMITED", "PROJECTION_PENDING")
        private val PROVIDER_RETRY_REASONS = setOf("BUDGET_EXHAUSTED", "CHANNEL_BUSY", "RATE_LIMITED", "HTTP_ERROR", "INVALID_RESPONSE")
    }
}
