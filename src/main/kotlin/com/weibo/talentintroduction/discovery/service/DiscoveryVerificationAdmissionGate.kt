package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.stereotype.Service

/** Loaded absence is a value; null at the policy boundary means not loaded. No network or writes. */
data class VerificationAdmissionContext(
    val docId: String,
    val profile: ExpertProfile,
    val items: List<VerificationItem>
) {
    val managed: Boolean get() = items.any { it.policyVersion == VerificationMode.ENFORCE.policyVersion }
}

/** Persisted input to the pure rules, separate from worker pagination progress. */
data class VerificationAdmissionEvidence(
    val evidence: List<VerificationEvidence>,
    val search: ApolloSearchEvidence? = null,
    val schemaVersion: Int = 1,
    val institutionNormalization: InstitutionNormalization? = null
) {
    init { require(schemaVersion == 1) }
}

@Service
class DiscoveryVerificationAdmissionGate(
    private val repository: DiscoveryVerificationRepository,
    private val rules: DiscoveryVerificationRules
) {
    fun findContext(docId: String, profile: ExpertProfile): VerificationAdmissionContext =
        findContexts(mapOf(docId to profile)).getValue(docId)

    fun findContexts(keys: Map<String, ExpertProfile>): Map<String, VerificationAdmissionContext> {
        require(keys.size <= 500)
        val items = repository.findItemsByDocIds(keys.keys).groupBy { it.expertDocId }
        return keys.mapValues { (docId, profile) -> VerificationAdmissionContext(docId, profile, items[docId].orEmpty()) }
    }

    fun evaluateManaged(baseAutomatic: AutomaticAdmissionResult, context: VerificationAdmissionContext): AutomaticAdmissionResult {
        if (!context.managed || baseAutomatic.status == DiscoveryAdmissionStatus.LEGACY_APPROVED) return baseAutomatic
        val blockers = baseAutomatic.blockingReasons.filterNot { it.code in REPLACED_CODES }.toMutableList()
        fun result(codes: List<String>): AutomaticAdmissionResult {
            blockers += codes.map { AdmissionReason(it, it, "verification", null, "有效身份与资格证据", "discovery_verification_item") }
            return baseAutomatic.copy(
                status = if (blockers.isEmpty()) DiscoveryAdmissionStatus.AUTO_PASSED else DiscoveryAdmissionStatus.NEEDS_REVIEW,
                blockingReasons = blockers.distinctBy { it.code }, policyVersion = POLICY_VERSION
            )
        }
        val snapshot = snapshot(context.docId, context.profile, baseAutomatic.configSnapshot)
        val item = context.items.filter { it.policyVersion == VerificationMode.ENFORCE.policyVersion && it.identityHash == snapshot.identityHash }
            .maxByOrNull { it.id } ?: return result(listOf("IDENTITY_CHANGED"))
        if (item.factFingerprint != snapshot.factFingerprint) return result(listOf("FACT_CHANGED"))
        if (item.state != VerificationState.COMPLETED) return result(listOf("VERIFICATION_PENDING"))
        val decision = try {
            val frozen = mapper.readValue<VerificationSnapshot>(item.snapshotJson)
            require(frozen.identityHash == item.identityHash && frozen.factFingerprint == item.factFingerprint)
            require(snapshot(context.docId, frozen.profile, baseAutomatic.configSnapshot).factFingerprint == item.factFingerprint)
            val payload = mapper.readValue<VerificationAdmissionEvidence>(requireNotNull(item.evidenceJson))
            rules.evaluate(snapshot, payload.evidence, payload.search,
                institutionNormalization = payload.institutionNormalization).also {
                require(it.outcome == item.outcome && it.qualification == item.qualification && it.route == item.route)
                require(it.matchedPersonId == item.apolloPersonId)
            }
        } catch (_: Exception) {
            return result(listOf("VERIFICATION_EVIDENCE_INVALID"))
        }
        return result(if (decision.outcome in ADMITTED_OUTCOMES) emptyList() else decision.reasonCodes.ifEmpty { listOf("VERIFICATION_PENDING") })
    }

    companion object {
        const val POLICY_VERSION = "APV_20261018_V1"
        val REPLACED_CODES = setOf("NO_DOCTORAL_DEGREE", "AGE_EXCEEDED")
        private val ADMITTED_OUTCOMES = setOf(VerificationOutcome.DIRECT_CLEAR, VerificationOutcome.APOLLO_CLEAR, VerificationOutcome.APOLLO_UNCLEAR)
        private val mapper = jacksonObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

        /** Shared with registration/commit: acquired provider facts never enter this RAW fingerprint. */
        fun snapshot(docId: String, profile: ExpertProfile, config: AdmissionConfigSnapshot): VerificationSnapshot {
            val identity = DiscoveryReviewIdentity.hash(docId, profile.email, profile.givenNames, profile.familyNames)
            val external = profile.externalIds?.let { mapper.readTree(it) }
            val facts = sortedMapOf<String, Any?>(
                "identityHash" to identity,
                "externalIds" to sortedMapOf("orcid" to external?.get("orcid"), "openAlexAuthorId" to external?.get("openAlexAuthorId")),
                "orcidId" to profile.orcidId, "institution" to profile.institution, "employment" to profile.employment,
                "degree" to profile.degree, "age" to profile.age,
                "identityVerification" to profile.identityVerification, "institutionEvidence" to profile.institutionEvidence,
                "config" to config
            )
            return VerificationSnapshot(identity, com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.hash(mapper.writeValueAsString(facts)), profile)
        }
    }
}
