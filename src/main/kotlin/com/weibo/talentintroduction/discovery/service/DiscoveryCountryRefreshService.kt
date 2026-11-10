package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/** Post-verification country enrichment. Never changes admission, frozen facts or historical evidence.
 * Reuses the worker's public employment response; REFRESH may retry a registration-time skipped read.
 */
@Service
class DiscoveryCountryRefreshService(
    private val countries: DiscoveryCountrySupplementService,
    private val writer: ExpertIndexWriterService,
    private val repository: DiscoveryVerificationRepository,
    private val jdbc: JdbcTemplate
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val log = LoggerFactory.getLogger(javaClass)

    fun refresh(item: VerificationItem, evidence: VerificationWorkerEvidence) {
        if (item.policyVersion != VerificationMode.ENFORCE.policyVersion || item.state != VerificationState.COMPLETED) return
        try {
            val latest = repository.findItem(item.id) ?: return
            // Projection consumes the original snapshot before a country/token patch changes it.
            if (latest.projectionState in setOf(VerificationProjection.PENDING, VerificationProjection.ERROR)) return
            val run = repository.findRun(item.ownerExecutionId) ?: return
            if (run.mode != VerificationMode.ENFORCE) return
            if (repository.isPaperReprocessOwned(run.executionId) && !repository.paperRunPermission(run.executionId)) return
            val recheck = run.recheckJson?.let { repository.recheck(run) }
            if (recheck?.control?.phase in setOf(RecheckPhase.CANCELLED, RecheckPhase.CANCELLING)) return
            val allowHttp = recheck == null || recheck.spec.evidenceMode == RecheckEvidenceMode.REFRESH
            val frozen = mapper.readValue(item.snapshotJson, VerificationSnapshot::class.java).profile
            val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId) ?: return
            val profile = writer.discoveryProfile(item.expertDocId, raw.source)
            if (!DiscoveryIdentity.allowed(profile) || profile.identityVerification != frozen.identityVerification ||
                DiscoveryIdentity.normalizedEmail(profile.email) != DiscoveryIdentity.normalizedEmail(frozen.email) ||
                profile.givenNames != frozen.givenNames || profile.familyNames != frozen.familyNames) return
            val retained = evidence.orcidCompleted.firstOrNull { it.endpoint == "employments" }?.document
                ?.takeIf { it.body.findValues("country").isNotEmpty() }
            val result = countries.supplement(item.ownerExecutionId, item.expertDocId, profile, null, retained, allowHttp)
            if (result.receipt == null) return
            val country = result.profile.country
            if (country != null && !country.matches(Regex("[A-Z]{2}"))) return
            val layers = linkedMapOf<String, String>()
            if (!country.isNullOrBlank()) for (level in ExpertIndexLevel.values()) {
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
                val token = current.institutionEvidence?.let {
                    if (!DiscoveryIdentity.validInstitutionEvidence(current)) null
                    else DiscoveryIdentity.institutionEvidence(current.copy(country = country), it.substringBefore(':'))
                }
                if (!current.institution.isNullOrBlank() && token == null) {
                    layers[level.name] = "INSTITUTION_EVIDENCE_INVALID"; continue
                }
                if (repository.isPaperReprocessOwned(run.executionId) && !repository.paperRunPermission(run.executionId)) return
                layers[level.name] = if (writer.writeDiscoveryCountryIfVersionMatches(level, item.expertDocId, document, country, token))
                    "UPDATED" else "WRITE_CONFLICT_OR_FAILURE"
            }
            // Append an explicit receipt, preserving the original frozen snapshot and provider evidence.
            jdbc.update("""UPDATE discovery_verification_item
                SET evidence_json=JSON_SET(COALESCE(evidence_json,'{}'),'$.countrySupplement',CAST(? AS JSON))
                WHERE id=? AND state='COMPLETED' AND generation=?""",
                mapper.writeValueAsString(mapOf("receipt" to result.receipt, "layers" to layers)), item.id, item.generation)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); throw e
        } catch (e: Exception) {
            log.warn("event=DISCOVERY_COUNTRY_REFRESH_FAILED itemId={} error={}", item.id, e.javaClass.simpleName)
        }
    }
}
