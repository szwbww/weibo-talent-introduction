package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.*
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.Locale

/** Immutable union of successful pages. A narrower empty query cannot erase a broad result. */
data class ApolloSearchEvidence(val candidates: List<ApolloCandidate> = emptyList(), val broadComplete: Boolean = false,
    val complete: Boolean = false, val queryNames: Set<String> = emptySet(), val conflictingIds: Set<String> = emptySet(),
    val matchingVersion: Int = 1, val detailsCompleted: Set<String> = emptySet(), val detailsComplete: Boolean = false,
    val academicResearchFields: String? = null, val academicCapturedAt: java.time.Instant? = null,
    val institutionAliasVersion: Int = 0, val freeSearchOnly: Boolean = false,
    val firstPages: List<ApolloQueryResult> = emptyList()) {
    fun merge(page: ApolloSearchPage, query: ApolloSearchQuery, broad: Boolean, allQueriesComplete: Boolean = false, attemptId: Long? = null): ApolloSearchEvidence {
        val ids = conflictingIds.toMutableSet()
        val union = candidates.associateByTo(linkedMapOf()) { it.id }
        page.candidates.forEach { candidate ->
            val previous = union[candidate.id]
            if (previous != null && previous != candidate) ids += candidate.id
            if (previous == null) union[candidate.id] = candidate
        }
        return copy(candidates = union.values.toList(), broadComplete = broadComplete || (broad && !page.hasNext),
            complete = complete || (allQueriesComplete && !page.hasNext), queryNames = queryNames + query.name, conflictingIds = ids,
            firstPages = if (freeSearchOnly && page.page == 1) firstPages.filterNot { it.query.copy(page = 1) == query.copy(page = 1) } +
                ApolloQueryResult.retained(query, page, attemptId) else firstPages)
    }
}
data class VerificationDecision(val qualification: VerificationQualification, val route: VerificationRoute,
    val outcome: VerificationOutcome, val reasonCodes: List<String>, val missingFields: List<String>,
    val evidence: List<VerificationEvidence>, val evaluatedOn: LocalDate, val matchedPersonId: String? = null,
    val trace: VerificationDecisionTrace? = null)

@Service
class DiscoveryVerificationRules(private val clock: Clock = Clock.systemUTC()) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .enable(com.fasterxml.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    fun evaluate(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
        search: ApolloSearchEvidence? = null, protected: Boolean = false,
        institutionNormalization: InstitutionNormalization? = null, ruleVersion: Int = 0,
        evaluatedAt: Instant? = null): VerificationDecision {
        require(ruleVersion in 0..4)
        fun sameName(left: String, right: String) = sameName(left, right, ruleVersion)
        val evaluationTime = evaluatedAt ?: clock.instant()
        val today = evaluationTime.atZone(clock.zone).toLocalDate()
        require(search == null || search.matchingVersion in 1..2) { "Unsupported Apollo matching version" }
        require(search == null || search.institutionAliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
        val traces = search?.candidates.orEmpty().mapIndexed { index, candidate ->
            candidate.id to CandidateDecisionTrace(candidate.id, "search.candidates[$index]")
        }.toMap(linkedMapOf())
        fun trace(candidate: ApolloCandidate, update: (CandidateDecisionTrace) -> CandidateDecisionTrace) {
            traces[candidate.id]?.let { traces[candidate.id] = update(it) }
        }
        fun support(candidate: ApolloCandidate): String? = matchReason(snapshot, evidence, candidate,
            search?.academicResearchFields, search?.institutionAliasVersion ?: 0, ruleVersion, today) { update ->
                trace(candidate, update)
            }
        fun decision(q: VerificationQualification, reasons: List<String>, missing: List<String> = emptyList(),
            route: VerificationRoute = VerificationRoute.NONE, outcome: VerificationOutcome = VerificationOutcome.PENDING,
            id: String? = null): VerificationDecision {
            val rows = traces.values.map { row ->
                if (row.personId == id) row.copy(rejectedReason = null)
                else row.copy(rejectedReason = row.rejectedReason ?: reasons.firstOrNull())
            }.sortedBy { if (it.personId == id) 0 else 1 }
            val inputHash = DiscoveryInstitutionNormalizer.sha256(mapper.writeValueAsString(
                listOf(snapshot, evidence, search, institutionNormalization, protected)))
            return VerificationDecision(q, route, outcome, reasons.distinct(), missing.distinct(), evidence, today, id,
                VerificationDecisionTrace(ruleVersion = ruleVersion, aliasVersion = search?.institutionAliasVersion ?: 0,
                    evaluatedAt = evaluationTime, inputHash = inputHash,
                    normalizationRef = institutionNormalization?.let { "institutionNormalization" },
                    evidenceRefs = evidence.indices.map { "evidence[$it]" },
                    total = search?.candidates?.size ?: 0, truncated = rows.size > 200, candidates = rows.take(200)))
        }
        if (protected) return decision(VerificationQualification.NOT_EVALUATED, listOf("PROTECTED"), outcome = VerificationOutcome.PROTECTED)
        if (institutionNormalization != null) {
            // Recompute the expected context from the original payload; a saved subject is never self-certifying.
            DiscoveryInstitutionNormalizer.validate(
                DiscoveryInstitutionNormalizer.build(snapshot.profile.institution,
                    identityEmployments(snapshot, evidence), search?.candidates), institutionNormalization)
        }
        if (evidence.any { it.identityHash != snapshot.identityHash || it.factFingerprint != snapshot.factFingerprint })
            return decision(VerificationQualification.CONFLICT, listOf("FACT_CHANGED"))
        if (!snapshot.identityValid) return decision(VerificationQualification.UNKNOWN, listOf("IDENTITY_EVIDENCE_MISSING"), listOf("identity"))
        if (evidence.any { it.conflicts.isNotEmpty() }) return decision(VerificationQualification.CONFLICT, listOf("EVIDENCE_CONFLICT"))
        val names = evidence.filter { it.field == "name" }
        if (names.any { !sameName(it.value, snapshot.fullName) &&
                !(ruleVersion >= 3 && anchoredNameMatches(snapshot, it)) })
            return decision(VerificationQualification.CONFLICT, listOf("NAME_CONFLICT"))
        val facts = evidence.filter { it.assertionSource == AssertionSource.ORGANIZATION ||
            ruleVersion >= 1 && it.assertionSource == AssertionSource.SELF }
        val jobs = facts.filter { it.field == "employment" && it.temporalScope == TemporalScope.CURRENT }
        val professor = jobs.any { fact -> value(fact, "institution").isNotBlank() && isProfessor(value(fact, "role"), ruleVersion) }
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
        if (ruleVersion >= 4 && search?.freeSearchOnly == true) {
            val result = FreeApolloVerification.evaluate(snapshot, evidence, search, this,
                institutionNormalization?.queryInstitution ?: DiscoveryInstitutionNormalizer.build(
                    snapshot.profile.institution, identityEmployments(snapshot, evidence), emptyList()).queryInstitution)
            result.personId?.let { id -> search.candidates.firstOrNull { it.id == id }?.let { candidate ->
                trace(candidate) { it.copy(nameMatch = "MATCH", currentInstitution = if (result.clear) "MATCH" else "NOT_EVALUATED",
                    chosenReason = result.reason) }
            } }
            return decision(qualification, listOf(result.reason), missing,
                if (result.personId != null) VerificationRoute.APOLLO else VerificationRoute.NONE,
                if (result.personId == null) VerificationOutcome.PENDING else if (result.clear) VerificationOutcome.APOLLO_CLEAR else VerificationOutcome.APOLLO_UNCLEAR,
                result.personId)
        }
        if (search?.matchingVersion == 2) {
            if (!search.complete || !search.broadComplete || !search.detailsComplete)
                return decision(qualification, listOf("SEARCH_INCOMPLETE"), missing)
            if (search.conflictingIds.isNotEmpty())
                return decision(VerificationQualification.CONFLICT, listOf("APOLLO_ID_CONFLICT"), missing)
            if (search.candidates.isEmpty()) return decision(qualification, listOf("APOLLO_NO_MATCH"), missing)
            val namesToMatch = allowedNames(snapshot, evidence)
            val named = search.candidates.filter { candidate ->
                val matchedName = nameMatches(candidate, namesToMatch, search.queryNames, masked = false, ruleVersion = ruleVersion) ||
                    ruleVersion >= 1 && emailNameMatches(snapshot, evidence, candidate, search.institutionAliasVersion)
                trace(candidate) { it.copy(nameMatch = if (matchedName) "MATCH" else "NO_MATCH",
                    rejectedReason = if (matchedName) null else "NAME_NOT_MATCHED") }
                matchedName
            }
            if (named.any { candidate ->
                    val conflict = candidate.orcid != null && snapshot.boundOrcid != null && normalizeOrcid(candidate.orcid) != snapshot.boundOrcid
                    trace(candidate) { it.copy(orcid = when {
                        candidate.orcid == null || snapshot.boundOrcid == null -> "NOT_AVAILABLE"
                        conflict -> "CONFLICT"
                        else -> "MATCH"
                    }) }
                    conflict
                }) return decision(VerificationQualification.CONFLICT, listOf("ORCID_CONFLICT"), missing)
            val supported = named.mapNotNull { candidate ->
                support(candidate)?.let { candidate to it }
            }
            val strong = supported.filter { it.second in setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
            val matched = strong.ifEmpty { supported }
            if (matched.size != 1) return decision(qualification,
                listOf(if (matched.size > 1) "IDENTITY_AMBIGUOUS" else "IDENTITY_NOT_MATCHED"), missing)
            // An unresolved masked candidate could still be a second valid person; do not discard it.
            if (search.candidates.any { candidate ->
                    if (candidate.id == matched.single().first.id || explicitName(candidate) != null) false
                    else {
                        val maskedMatch = nameMatches(candidate, namesToMatch, search.queryNames, masked = true, ruleVersion = ruleVersion) ||
                            ruleVersion >= 1 && emailNameMatches(snapshot, evidence, candidate, search.institutionAliasVersion, masked = true)
                        trace(candidate) { it.copy(nameMatch = if (maskedMatch) "MATCH" else "NO_MATCH",
                            rejectedReason = if (maskedMatch) null else "NAME_NOT_MATCHED") }
                        maskedMatch && support(candidate) != null
                    }
                })
                return decision(qualification, listOf("IDENTITY_AMBIGUOUS"), missing)
            val (candidate, reason) = matched.single()
            val aliasReasons = if (reason in setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH") &&
                matchReason(snapshot, evidence, candidate, search.academicResearchFields, 0, ruleVersion, today) != reason) {
                val originals = listOfNotNull(snapshot.profile.institution?.let(::VerificationInstitution)) +
                    identityEmployments(snapshot, evidence).map { VerificationInstitution(value(it, "institution")) }
                val institutions = if (reason == "APOLLO_CURRENT_INSTITUTION_MATCH") listOfNotNull(candidate.institution)
                    else candidate.employmentHistory.filter { it.current == false && !it.endDate.isNullOrBlank() &&
                        runCatching { LocalDate.parse(it.endDate!!.take(10)) }.getOrNull()?.let { date -> !date.isAfter(today) } == true
                    }.mapNotNull { it.institution }
                originals.flatMap { original -> institutions.mapNotNull {
                    DiscoveryInstitutionNormalizer.aliasReason(original, it, search.institutionAliasVersion)
                } }.distinct()
            } else emptyList()
            return decision(qualification, aliasReasons + (if (ruleVersion >= 1 && !nameMatches(candidate, namesToMatch, search.queryNames, false, ruleVersion))
                listOf("APOLLO_EMAIL_GIVEN_NAME_MATCH") else emptyList()) + listOf("APOLLO_MATCH_POLICY_V2", reason, when (qualification) {
                VerificationQualification.MET -> "QUALIFICATION_MET"
                VerificationQualification.NOT_MET -> "AGE_REQUIREMENT_NOT_MET"
                else -> "QUALIFICATION_EVIDENCE_MISSING"
            }), missing, VerificationRoute.APOLLO,
                if (reason == "APOLLO_SPECIALTY_MATCH") VerificationOutcome.APOLLO_UNCLEAR else VerificationOutcome.APOLLO_CLEAR,
                candidate.id)
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
        val profileSubject = institutionNormalization?.inputs?.firstOrNull { it.source == InstitutionSource.PROFILE.name }
        val employmentSubjects = institutionNormalization?.inputs?.filter { it.source == InstitutionSource.ORCID_EMPLOYMENT.name }.orEmpty()
        val candidateSubjects = institutionNormalization?.inputs
            ?.filter { it.source == InstitutionSource.APOLLO_CANDIDATE.name }?.associateBy { it.sourceRef }.orEmpty()
        if (!originalInstitution.isNullOrBlank()) {
            if (institutionNormalization == null) {
                if (institutions.none { sameInstitution(it, VerificationInstitution(originalInstitution)) })
                    return decision(VerificationQualification.CONFLICT, listOf("INSTITUTION_CONFLICT"), missing)
            } else {
                // An unclear subject is missing evidence, never a fabricated institution conflict.
                if (profileSubject?.status != DiscoveryInstitutionNormalizer.RESOLVED ||
                    employmentSubjects.any { it.status != DiscoveryInstitutionNormalizer.RESOLVED })
                    return decision(VerificationQualification.UNKNOWN, listOf("IDENTITY_EVIDENCE_MISSING"), missing + "institutionSubject")
                if (employmentSubjects.none { DiscoveryInstitutionNormalizer.compare(profileSubject, it) == InstitutionComparison.MATCH })
                    return decision(VerificationQualification.CONFLICT, listOf("INSTITUTION_CONFLICT"), missing)
            }
        }
        val allowedNames = setOf(snapshot.fullName) + evidence.filter { it.field == "otherName" && it.provider == "ORCID" }.map { it.value }
        fun institutionMatches(candidate: ApolloCandidate): Boolean {
            if (institutionNormalization == null)
                return candidate.institution?.let { institution -> institutions.any { sameInstitution(it, institution) } } == true
            val subject = candidateSubjects[candidate.id]
            if (subject == null || subject.status != DiscoveryInstitutionNormalizer.RESOLVED) return false
            val candidateName = subject.subjects.singleOrNull() ?: return false
            return employments.indices.any { index ->
                val employmentSubject = employmentSubjects.getOrNull(index) ?: return@any false
                val employmentName = employmentSubject.subjects.singleOrNull() ?: return@any false
                if (employmentSubject.status != DiscoveryInstitutionNormalizer.RESOLVED) return@any false
                val employmentInstitution = institutions[index]
                // Validated subjects are the compare text; original standard IDs and aliases remain the
                // source-bound metadata. Extraction never manufactures an ID or alias.
                val left = VerificationInstitution(employmentName, employmentInstitution.standardId, employmentInstitution.aliases)
                val right = candidate.institution?.let {
                    VerificationInstitution(candidateName, it.standardId, it.aliases)
                } ?: VerificationInstitution(candidateName)
                DiscoveryInstitutionNormalizer.compare(left, right,
                    InstitutionSource.ORCID_EMPLOYMENT, InstitutionSource.APOLLO_CANDIDATE) == InstitutionComparison.MATCH
            }
        }
        val matches = search.candidates.filter { candidate ->
            val explicit = candidate.fullName ?: listOfNotNull(candidate.firstName, candidate.lastName).takeIf { candidate.lastName != null }?.joinToString(" ")
            val strict = explicit != null && allowedNames.any { known ->
                sameName(explicit, known) && (candidate.lastName == null ||
                    (candidate.firstName != null && sameName("${candidate.firstName} ${candidate.lastName}", known)))
            }
            val masked = explicit == null && candidate.lastName == null && allowedNames.any { name ->
                search.queryNames.any { sameName(it, name) } && maskedCompatible(candidate, name)
            }
            trace(candidate) { it.copy(nameMatch = if (strict || masked) "MATCH" else "NO_MATCH") }
            if (!strict && !masked) return@filter false
            val orcidMatch = candidate.orcid == null || normalizeOrcid(candidate.orcid) == orcid
            trace(candidate) { it.copy(orcid = if (candidate.orcid == null) "NOT_AVAILABLE" else if (orcidMatch) "MATCH" else "CONFLICT") }
            if (!orcidMatch) return@filter false
            val institutionMatch = institutionMatches(candidate)
            trace(candidate) { it.copy(currentInstitution = if (institutionMatch) "MATCH" else
                if (candidate.institution == null) "UNKNOWN" else "DIFFERENT",
                chosenReason = if (institutionMatch) "APOLLO_PERSON_MATCHED" else null) }
            institutionMatch
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

    internal fun allowedNames(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>): Set<String> =
        setOf(snapshot.fullName) + evidence.filter { it.provider == "ORCID" && it.field in setOf("name", "otherName") &&
            it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint }.map { it.value }

    private fun explicitName(candidate: ApolloCandidate): String? = candidate.fullName ?:
        listOfNotNull(candidate.firstName, candidate.lastName).takeIf { candidate.lastName != null }?.joinToString(" ")

    internal fun nameMatches(candidate: ApolloCandidate, names: Set<String>, queries: Set<String>, masked: Boolean, ruleVersion: Int = 1): Boolean {
        fun sameName(left: String, right: String) = sameName(left, right, ruleVersion)
        val explicit = explicitName(candidate)
        return if (explicit != null) names.any { known ->
            sameName(explicit, known) && (candidate.lastName == null ||
                candidate.firstName != null && sameName("${candidate.firstName} ${candidate.lastName}", known))
        } else masked && names.flatMap { if (ruleVersion >= 1) nameVariants(it) else listOf(it) }.any { known ->
            val tokens = normalizeName(known).split(' ')
            val mask = candidate.obfuscatedLastName.orEmpty().lowercase(Locale.ROOT)
            // Apollo masks a hidden span, not a fixed number of letters: Hadfield -> Ha***d.
            val pattern = mask.split(Regex("\\*+")).joinToString(".*") { Regex.escape(it) }
            queries.any { sameName(it, known) } && candidate.firstName?.let(::normalizeName) == tokens.first() &&
                mask.any { it.isLetter() } && tokens.last().matches(Regex(pattern))
        }
    }

    /** V2 is an explicitly approved admission heuristic, not a claim that degrees/age were verified. */
    internal fun matchReason(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>,
        candidate: ApolloCandidate, academicFields: String?, institutionAliasVersion: Int = 0, ruleVersion: Int = 0,
        evaluatedOn: LocalDate = LocalDate.now(clock),
        trace: (((CandidateDecisionTrace) -> CandidateDecisionTrace) -> Unit)? = null): String? {
        require(ruleVersion in 0..4)
        val originals = listOfNotNull(snapshot.profile.institution?.let(::VerificationInstitution)) +
            identityEmployments(snapshot, evidence).map { VerificationInstitution(value(it, "institution")) }
        fun institutionResult(institution: VerificationInstitution?): String {
            if (institution == null || originals.isEmpty()) return "UNKNOWN"
            val comparisons = originals.map { DiscoveryInstitutionNormalizer.compareWithAliases(it, institution, institutionAliasVersion) }
            return when {
                InstitutionComparison.MATCH in comparisons -> "MATCH"
                InstitutionComparison.UNKNOWN in comparisons -> "UNKNOWN"
                else -> "DIFFERENT"
            }
        }
        fun chosen(reason: String): String {
            trace?.invoke { it.copy(chosenReason = reason) }
            return reason
        }
        val current = institutionResult(candidate.institution)
        trace?.invoke { it.copy(currentInstitution = current) }
        if (current == "MATCH") return chosen("APOLLO_CURRENT_INSTITUTION_MATCH")
        val historical = candidate.employmentHistory.filter { job ->
            job.current == false && !job.endDate.isNullOrBlank() &&
                runCatching { LocalDate.parse(job.endDate!!.take(10)) }.getOrNull()?.let { !it.isAfter(evaluatedOn) } == true
        }.map { institutionResult(it.institution) }
        val history = when {
            "MATCH" in historical -> "MATCH"
            historical.isEmpty() || "UNKNOWN" in historical -> "UNKNOWN"
            else -> "DIFFERENT"
        }
        trace?.invoke { it.copy(historicalInstitution = history) }
        if (history == "MATCH") return chosen("APOLLO_HISTORICAL_INSTITUTION_MATCH")
        val professor = apolloProfessor(candidate.title) || ruleVersion >= 2 && candidate.title?.trim() == "Assistenzprofessor" &&
            normalizeName(candidate.headline.orEmpty()).let { headline ->
                Regex("^assistant professor(?: |$)").containsMatchIn(headline) &&
                    !Regex("\\b(?:emeritus|retired|former|student|candidate|assistant to|lab of|research group|not|never|no longer)\\b|退休|学生|博士生|助理人员").containsMatchIn(headline)
            }
        trace?.invoke { it.copy(professor = if (professor) "MATCH" else "NO_MATCH") }
        if (professor) return chosen("APOLLO_PROFESSOR_MATCH")
        // Whole specific phrases only. Generic job/industry labels are not academic specialties.
        val excluded = setOf("science", "engineering", "research", "education", "higher education", "technology",
            "professor", "researcher", "scientist", "科学", "研究", "教育", "工程", "其他")
        val specialties = academicFields.orEmpty().split(Regex("[,;，；\\n]")).map(::normalizeName)
            .filter { it.length >= 3 && it !in excluded }.distinct()
        val currentText = listOfNotNull(candidate.title, candidate.headline) +
            candidate.employmentHistory.filter { it.current == true }.mapNotNull { it.major }
        val specialty = specialties.any { field -> currentText.any { text ->
            (" " + normalizeName(text) + " ").contains(" $field ")
        } }
        trace?.invoke { it.copy(specialty = if (specialty) "MATCH" else "NO_MATCH",
            rejectedReason = if (specialty) null else "IDENTITY_NOT_MATCHED") }
        if (specialty) return chosen("APOLLO_SPECIALTY_MATCH")
        return null
    }

    internal fun apolloProfessor(role: String?): Boolean {
        val title = normalizeName(role.orEmpty())
        if (Regex("emeritus|retired|former|student|candidate|assistant to|lab of|research group|退休|学生|博士生|助理人员").containsMatchIn(title)) return false
        return title.matches(Regex("(?:(?:assistant|associate|teaching|adjunct|visiting|full|research|clinical|honorary) )?(?:professor|prof)(?: (?:of|in|at) .+)?")) ||
            title.matches(Regex("(?:助理|副|特聘|客座|讲席)?教授"))
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
    /** Transliteration is only for person names, never for institution matching. */
    internal fun nameVariants(name: String): List<String> {
        val german = name.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
            .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss")
        fun ascii(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return listOf(name, ascii(german), ascii(name)).distinctBy(::normalizeName)
    }
    internal fun queryNameVariants(name: String): List<String> {
        val parts = name.trim().split(Regex("\\s+"))
        val withoutInitials = if (parts.size > 2 && parts.drop(1).dropLast(1).all {
                it.removeSuffix(".").let { token -> token.length == 1 && token.single().isLetter() }
            }) "${parts.first()} ${parts.last()}" else null
        return (nameVariants(name) + listOfNotNull(withoutInitials).flatMap(::nameVariants)).distinctBy(::normalizeName).take(4)
    }
    fun sameName(left: String, right: String, ruleVersion: Int = 1): Boolean {
        fun matches(a: List<String>, b: List<String>): Boolean {
            if (a == b || a.size == 2 && b.size > 2 && a.first() == b.first() && a.last() == b.last()) return true
            return ruleVersion >= 1 && a.size >= 3 && a.size == b.size && a.first() == b.first() && a.last() == b.last() &&
                a.drop(1).dropLast(1).zip(b.drop(1).dropLast(1)).all { (x, y) ->
                    x == y || x.length == 1 && y.startsWith(x) || y.length == 1 && x.startsWith(y)
                }
        }
        val aa = if (ruleVersion >= 1) nameVariants(left) else listOf(left)
        val bb = if (ruleVersion >= 1) nameVariants(right) else listOf(right)
        return aa.any { a -> bb.any { b -> matches(normalizeName(a).split(' '), normalizeName(b).split(' ')) } }
    }

    /** Only the bound ORCID endpoint can reconcile its own name with the immutable snapshot. */
    private fun anchoredNameMatches(snapshot: VerificationSnapshot, fact: VerificationEvidence): Boolean {
        val orcid = snapshot.boundOrcid ?: return false
        if (fact.provider != "ORCID" || fact.identityHash != snapshot.identityHash ||
            fact.factFingerprint != snapshot.factFingerprint ||
            fact.sourceUrl !in setOf("https://pub.orcid.org/v3.0/$orcid/person", "https://pub.orcid.org/v3.0/$orcid/other-names")) return false
        val left = normalizeName(fact.value).split(' ')
        val right = normalizeName(snapshot.fullName).split(' ')
        if (left.size < 2 || left.size != right.size) return false
        // Rotation preserves every token and its relative order; arbitrary permutations are not aliases.
        if (left.indices.any { shift -> left.drop(shift) + left.take(shift) == right }) return true
        val surname = familyNameHint(snapshot)
        if (surname.length < 3 || surname.contains(' ') ||
            left.count { it == surname } != 1 || right.count { it == surname } != 1) return false
        fun given(tokens: List<String>): List<String> {
            val at = tokens.indexOf(surname)
            return tokens.drop(at + 1) + tokens.take(at)
        }
        return given(left).zip(given(right)).all { (a, b) ->
            a == b || a.length == 1 && b.startsWith(a) || b.length == 1 && a.startsWith(b)
        }
    }

    internal fun familyNameHint(snapshot: VerificationSnapshot): String = normalizeName(
        snapshot.profile.familyNames.orEmpty().trim().split(Regex("\\s+"))
            .dropWhile { it.removeSuffix(".").let { token -> token.length == 1 && token.single().isLetter() } }.joinToString(" "))

    /** Email is a bounded corroborating hint, not a fabricated alias or a provider-revealed address. */
    internal fun emailNameMatches(snapshot: VerificationSnapshot, evidence: List<VerificationEvidence>, candidate: ApolloCandidate,
        aliasVersion: Int, masked: Boolean = false): Boolean {
        val first = candidate.firstName?.let(::normalizeName) ?: return false
        if (first.length < 3 || first.contains(' ')) return false
        val local = snapshot.profile.email?.substringBefore('@')?.lowercase(Locale.ROOT) ?: return false
        if (first !in local.split(Regex("[._+\\-]"))) return false
        val surname = familyNameHint(snapshot)
        if (surname.length < 3) return false
        val full = explicitName(candidate)
        val surnameMatches = if (full != null) {
            val tokens = normalizeName(full).split(' ')
            tokens.size >= 2 && tokens.first() == first && normalizeName(full).endsWith(" $surname") &&
                (candidate.lastName == null || normalizeName(candidate.lastName) == surname)
        } else masked && candidate.obfuscatedLastName?.let { mask ->
            mask.any { it.isLetter() } && surname.matches(Regex(mask.lowercase(Locale.ROOT).split(Regex("\\*+")).joinToString(".*") { Regex.escape(it) }))
        } == true
        return surnameMatches && matchReason(snapshot, evidence, candidate, null, aliasVersion) in
            setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH")
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
    private fun isProfessor(role: String, ruleVersion: Int = 0): Boolean {
        val value = normalizeName(role)
        if (Regex("emeritus|retired|former|student|candidate|visiting student|research group|lab of|assistant to|博士生|退休|课题组").containsMatchIn(value)) return false
        if (ruleVersion >= 4) {
            if (Regex("\\b(?:not|never|no|qualification|postdoctoral|emerito|under|supervised|mentor|advised|aspiring|seeking|becoming)\\b|from lecturer to").containsMatchIn(value)) return false
            if (Regex("\\bprofessor\\b").containsMatchIn(value)) return true
        }
        if (ruleVersion >= 3) {
            if (Regex("\\b(?:not|never|no longer)\\b").containsMatchIn(value)) return false
            return role.split('/').any { part ->
                val title = normalizeName(part)
                title.matches(Regex("(?:(?:assistant|associate|teaching|adjunct|visiting|full|research|clinical|honorary) )?(?:professor|prof)(?: (?:of|in|at) [\\p{L}][\\p{L}\\p{N} ]*)?")) ||
                    title.matches(Regex("(?:助理|副|特聘|客座|讲席)?教授"))
            }
        }
        return value.matches(Regex("(?:(?:assistant|associate|teaching|adjunct|visiting|full|research|clinical|honorary) )?(?:professor|prof)")) ||
            value.matches(Regex("(?:助理|副|特聘|客座|讲席)?教授"))
    }
}
