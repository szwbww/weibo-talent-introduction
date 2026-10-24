package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.config.OpenAlexProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetDeferredException
import com.weibo.talentintroduction.discovery.domain.RecheckEvidenceMode
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Instant

/** Decision/display data only. No profile writer or admission service is a dependency. */
data class RecheckAcademicEvidence(val identityHash: String, val factFingerprint: String,
    val trustedId: String?, val data: AuthorEnrichment?, val sourceUrls: List<String>,
    val evaluatedAt: Instant, val reasonCodes: List<String>, val retained: Boolean,
    val profileResearchFields: String? = null, val retryAt: Instant? = null, val provider: String = "OPENALEX")

@Service
class DiscoveryRecheckAcademicEvidenceService(private val clients: ObjectProvider<OpenAlexDataSource>,
    private val properties: OpenAlexProperties = OpenAlexProperties()) {
    /** Trusted discovery IDs must be carried by the current, verified receipt, never guessed from names. */
    fun trustedId(snapshot: VerificationSnapshot): String? {
        if (!snapshot.identityValid || !DiscoveryIdentity.allowed(snapshot.profile)) return null
        val proof = snapshot.profile.identityVerification ?: return null
        return normalizeOpenAlexAuthorId(proof.openAlexAuthorId) ?: snapshot.boundOrcid
    }

    fun collect(snapshots: List<VerificationSnapshot>, mode: RecheckEvidenceMode, evaluatedAt: Instant,
        retained: Map<String, RecheckAcademicEvidence> = emptyMap()): Map<String, RecheckAcademicEvidence> {
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
            if (authors.isNotEmpty()) outcomes.putAll(client.batchEnrichByAuthorIds(authors, RequestKind.HISTORY_ENRICHMENT))
            if (orcids.isNotEmpty()) outcomes.putAll(client.batchEnrichByOrcids(orcids, RequestKind.HISTORY_ENRICHMENT))
        } catch (e: OpenAlexBudgetDeferredException) { deferred = e }
        pending.forEach { (s, id) ->
            val urls = if (id!!.startsWith("A")) listOf("${properties.baseUrl}/authors?filter=openalex:${authors.joinToString("|")}&per_page=${authors.size}")
                else listOf("${properties.baseUrl}/authors?filter=orcid:${orcids.joinToString("|")}&per_page=200")
            results[s.identityHash] = when (val outcome = outcomes[id]) {
                is EnrichmentOutcome.Success -> result(s, id, outcome.data,
                    reasons = if (outcome.titlesFailed) listOf("PARTIAL_TITLES") else emptyList(), urls = urls)
                EnrichmentOutcome.AmbiguousIdentity -> result(s, id, reasons = listOf("AMBIGUOUS_IDENTITY"), urls = urls)
                EnrichmentOutcome.NotFound -> result(s, id, reasons = listOf("ACADEMIC_SOURCE_NOT_FOUND"), urls = urls)
                is EnrichmentOutcome.RateLimited -> result(s, id, reasons = listOf("RATE_LIMITED"), urls = urls)
                is EnrichmentOutcome.ApiError -> result(s, id, reasons = listOf("PROVIDER_ERROR"), urls = urls)
                null -> if (deferred != null) result(s, id, reasons = listOf("BUDGET_DEFERRED"), urls = urls).copy(retryAt = deferred.retryAt)
                    else result(s, id, reasons = listOf("PROVIDER_RESULT_MISSING"), urls = urls)
            }
        }
        return results
    }
}
