package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/** Immutable union of successful pages. A narrower empty query cannot erase a broad result. */
data class ApolloSearchEvidence(val candidates: List<ApolloCandidate> = emptyList(), val broadComplete: Boolean = false,
    val complete: Boolean = false, val queryNames: Set<String> = emptySet(), val conflictingIds: Set<String> = emptySet()) {
    fun merge(page: ApolloSearchPage, query: ApolloSearchQuery, broad: Boolean, allQueriesComplete: Boolean = false): ApolloSearchEvidence {
        val ids = conflictingIds.toMutableSet()
        val union = candidates.associateByTo(linkedMapOf()) { it.id }
        page.candidates.forEach { candidate ->
            val previous = union[candidate.id]
            if (previous != null && previous != candidate) ids += candidate.id
            if (previous == null) union[candidate.id] = candidate
        }
        return copy(candidates = union.values.toList(), broadComplete = broadComplete || (broad && !page.hasNext),
            complete = allQueriesComplete && !page.hasNext, queryNames = queryNames + query.name, conflictingIds = ids)
    }
}
data class VerificationDecision(val qualification: VerificationQualification, val route: VerificationRoute,
    val outcome: VerificationOutcome, val reasonCodes: List<String>, val missingFields: List<String>,
    val evidence: List<VerificationEvidence>, val evaluatedOn: LocalDate, val matchedPersonId: String? = null)

@Service
class DiscoveryVerificationRules(private val clock: Clock = Clock.systemUTC()) {
    private val mapper = jacksonObjectMapper()
    fun evaluate(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
        search: ApolloSearchEvidence? = null, protected: Boolean = false): VerificationDecision {
        val today = LocalDate.now(clock)
        fun decision(q: VerificationQualification, reasons: List<String>, missing: List<String> = emptyList(),
            route: VerificationRoute = VerificationRoute.NONE, outcome: VerificationOutcome = VerificationOutcome.PENDING,
            id: String? = null) = VerificationDecision(q, route, outcome, reasons.distinct(), missing.distinct(), evidence, today, id)
        if (protected) return decision(VerificationQualification.NOT_EVALUATED, listOf("PROTECTED"), outcome = VerificationOutcome.PROTECTED)
        if (evidence.any { it.identityHash != snapshot.identityHash || it.factFingerprint != snapshot.factFingerprint })
            return decision(VerificationQualification.CONFLICT, listOf("FACT_CHANGED"))
        if (!snapshot.identityValid) return decision(VerificationQualification.UNKNOWN, listOf("IDENTITY_EVIDENCE_MISSING"), listOf("identity"))
        if (evidence.any { it.conflicts.isNotEmpty() }) return decision(VerificationQualification.CONFLICT, listOf("EVIDENCE_CONFLICT"))
        val names = evidence.filter { it.field == "name" }.map { normalizeName(it.value) }.distinct()
        if (names.any { !sameName(it, snapshot.fullName) })
            return decision(VerificationQualification.CONFLICT, listOf("NAME_CONFLICT"))
        val facts = evidence.filter { it.assertionSource == AssertionSource.ORGANIZATION }
        val jobs = facts.filter { it.field == "employment" && it.temporalScope == TemporalScope.CURRENT }
        val professor = jobs.any { fact -> value(fact, "institution").isNotBlank() && isProfessor(value(fact, "role")) }
        val doctorates = facts.filter { it.field == "education" }.mapNotNull { fact ->
            runCatching { mapper.readTree(fact.value) }.getOrNull()?.takeIf {
                it.path("completed").isBoolean && it.path("completed").booleanValue() &&
                    QualificationEvidenceParser(clock).completedDoctorate(it.path("role").asText())
            }
        }
        val ages = facts.filter { it.field == "birthDate" }.mapNotNull { fact ->
            runCatching { LocalDate.parse(fact.value) }.getOrNull()?.takeIf { !it.isAfter(today) }?.let { Period.between(it, today).years }
        }.distinct()
        if (ages.size > 1) return decision(VerificationQualification.CONFLICT, listOf("AGE_CONFLICT"))
        val age = ages.singleOrNull()
        val qualification = when {
            professor -> VerificationQualification.MET
            doctorates.isNotEmpty() && age != null && age < 40 -> VerificationQualification.MET
            doctorates.isNotEmpty() && age != null && age >= 40 -> VerificationQualification.NOT_MET
            else -> VerificationQualification.UNKNOWN
        }
        val missing = mutableListOf<String>()
        if (!professor) {
            if (doctorates.isEmpty()) missing += "completedDoctorate"
            if (age == null) missing += "reliableAge"
            if (age != null && age >= 40) missing += "currentAssociateProfessor"
        }
        if (qualification == VerificationQualification.NOT_MET) return decision(qualification, listOf("AGE_REQUIREMENT_NOT_MET"), missing)
        if (search == null && qualification == VerificationQualification.MET)
            return decision(qualification, listOf("QUALIFICATION_MET"), route = VerificationRoute.DIRECT, outcome = VerificationOutcome.DIRECT_CLEAR)
        if (search == null) return decision(qualification, listOf("QUALIFICATION_EVIDENCE_MISSING"), missing)
        if (!search.complete || !search.broadComplete) return decision(qualification, listOf("SEARCH_INCOMPLETE"), missing)
        if (search.conflictingIds.isNotEmpty()) return decision(VerificationQualification.CONFLICT, listOf("APOLLO_ID_CONFLICT"), missing)
        if (search.candidates.isEmpty()) return decision(qualification, listOf("APOLLO_NO_MATCH"), missing)
        val orcid = snapshot.boundOrcid ?: return decision(qualification, listOf("IDENTITY_EVIDENCE_MISSING"), missing + "boundOrcid")
        val employments = identityEmployments(snapshot, evidence)
        if (employments.isEmpty()) return decision(qualification, listOf("CURRENT_INSTITUTION_EVIDENCE_MISSING"), missing + "currentInstitution")
        val originalInstitution = snapshot.profile.institution
        val institutions = employments.map { VerificationInstitution(value(it, "institution"), value(it, "standardId").takeIf(String::isNotBlank)) }
        if (!originalInstitution.isNullOrBlank() && institutions.none { sameInstitution(it, VerificationInstitution(originalInstitution)) })
            return decision(VerificationQualification.CONFLICT, listOf("INSTITUTION_CONFLICT"), missing)
        val allowedNames = setOf(snapshot.fullName) + evidence.filter { it.field == "otherName" && it.provider == "ORCID" }.map { it.value }
        val matches = search.candidates.filter { candidate ->
            val explicit = candidate.fullName ?: listOfNotNull(candidate.firstName, candidate.lastName).takeIf { candidate.lastName != null }?.joinToString(" ")
            val strict = explicit != null && allowedNames.any { known ->
                sameName(explicit, known) && (candidate.lastName == null ||
                    (candidate.firstName != null && sameName("${candidate.firstName} ${candidate.lastName}", known)))
            }
            val masked = explicit == null && candidate.lastName == null && allowedNames.any { name ->
                search.queryNames.any { sameName(it, name) } && maskedCompatible(candidate, name)
            }
            (strict || masked) && (candidate.orcid == null || normalizeOrcid(candidate.orcid) == orcid) &&
                candidate.institution?.let { institution -> institutions.any { sameInstitution(it, institution) } } == true
        }
        if (matches.size != 1) return decision(qualification, listOf(if (matches.size > 1) "IDENTITY_AMBIGUOUS" else "IDENTITY_NOT_MATCHED"), missing)
        // An explicit contradictory ORCID on a same-name record must not be discarded as an unrelated candidate.
        if (search.candidates.any { candidate -> candidate.orcid != null && normalizeOrcid(candidate.orcid) != orcid &&
                (candidate.fullName ?: listOfNotNull(candidate.firstName, candidate.lastName).takeIf { candidate.lastName != null }?.joinToString(" "))
                    ?.let { name -> allowedNames.any { sameName(name, it) } } == true })
            return decision(VerificationQualification.CONFLICT, listOf("ORCID_CONFLICT"), missing)
        val match = matches.single()
        return decision(qualification, listOf("APOLLO_PERSON_MATCHED") + if (qualification == VerificationQualification.MET) listOf("QUALIFICATION_MET") else listOf("QUALIFICATION_EVIDENCE_MISSING"),
            missing, VerificationRoute.APOLLO, if (qualification == VerificationQualification.MET) VerificationOutcome.APOLLO_CLEAR else VerificationOutcome.APOLLO_UNCLEAR, match.id)
    }

    /** Identity corroboration is broader than institutional qualification, but never unbound or historical. */
    fun identityEmployments(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): List<VerificationEvidence> {
        val orcid = snapshot.boundOrcid ?: return emptyList()
        return evidence.filter {
            it.provider == "ORCID" && it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint &&
                it.field == "employment" && it.temporalScope == TemporalScope.CURRENT &&
                it.assertionSource in setOf(AssertionSource.ORGANIZATION, AssertionSource.SELF) &&
                value(it, "orcid") == orcid && value(it, "institution").isNotBlank()
        }
    }

    fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[\\p{P}\\p{Z}\\s]+"), " ").trim()
    fun sameName(left: String, right: String): Boolean {
        val a = normalizeName(left).split(' '); val b = normalizeName(right).split(' ')
        // Only omission of already-known middle names, never invented aliases, initials or surname completion.
        return a == b || (a.size == 2 && b.size > 2 && a.first() == b.first() && a.last() == b.last())
    }
    fun sameInstitution(a: VerificationInstitution, b: VerificationInstitution): Boolean {
        if (a.standardId != null && b.standardId != null) return a.standardId == b.standardId
        val left = (a.aliases + a.name).map(::normalizeName).toSet()
        return (b.aliases + b.name).any { normalizeName(it) in left }
    }
    private fun maskedCompatible(candidate: ApolloCandidate, name: String): Boolean {
        val tokens = normalizeName(name).split(' ')
        if (candidate.firstName == null || normalizeName(candidate.firstName) != tokens.first()) return false
        val mask = candidate.obfuscatedLastName ?: return false
        if (!mask.contains('*')) return normalizeName(mask) == tokens.last()
        val regex = mask.lowercase(Locale.ROOT).map { if (it == '*') "." else Regex.escape(it.toString()) }.joinToString("")
        return tokens.last().matches(Regex(regex))
    }
    private fun value(evidence: VerificationEvidence, key: String): String = runCatching { mapper.readTree(evidence.value).path(key).asText("") }.getOrDefault("")
    private fun isProfessor(role: String): Boolean {
        val value = normalizeName(role)
        if (Regex("emeritus|retired|former|student|candidate|visiting student|research group|lab of|assistant to|博士生|退休|课题组").containsMatchIn(value)) return false
        return value.matches(Regex("(?:(?:assistant|associate|teaching|adjunct|visiting|full|research|clinical|honorary) )?(?:professor|prof)")) ||
            value.matches(Regex("(?:助理|副|特聘|客座|讲席)?教授"))
    }
}
