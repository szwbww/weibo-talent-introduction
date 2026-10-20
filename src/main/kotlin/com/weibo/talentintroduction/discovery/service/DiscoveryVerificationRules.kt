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
    val complete: Boolean = false, val queryNames: Set<String> = emptySet(), val conflictingIds: Set<String> = emptySet(),
    val matchingVersion: Int = 1, val detailsCompleted: Set<String> = emptySet(), val detailsComplete: Boolean = false,
    val academicResearchFields: String? = null, val academicCapturedAt: java.time.Instant? = null,
    val institutionAliasVersion: Int = 0) {
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
        search: ApolloSearchEvidence? = null, protected: Boolean = false,
        institutionNormalization: InstitutionNormalization? = null, ruleVersion: Int = 0): VerificationDecision {
        require(ruleVersion in 0..1)
        fun sameName(left: String, right: String) = sameName(left, right, ruleVersion)
        val today = LocalDate.now(clock)
        require(search == null || search.matchingVersion in 1..2) { "Unsupported Apollo matching version" }
        require(search == null || search.institutionAliasVersion in 0..DiscoveryInstitutionNormalizer.ALIAS_VERSION)
        fun decision(q: VerificationQualification, reasons: List<String>, missing: List<String> = emptyList(),
            route: VerificationRoute = VerificationRoute.NONE, outcome: VerificationOutcome = VerificationOutcome.PENDING,
            id: String? = null) = VerificationDecision(q, route, outcome, reasons.distinct(), missing.distinct(), evidence, today, id)
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
        val names = evidence.filter { it.field == "name" }.map { normalizeName(it.value) }.distinct()
        if (names.any { !sameName(it, snapshot.fullName) })
            return decision(VerificationQualification.CONFLICT, listOf("NAME_CONFLICT"))
        val facts = evidence.filter { it.assertionSource == AssertionSource.ORGANIZATION ||
            ruleVersion >= 1 && it.assertionSource == AssertionSource.SELF }
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
        if (search?.matchingVersion == 2) {
            if (!search.complete || !search.broadComplete || !search.detailsComplete)
                return decision(qualification, listOf("SEARCH_INCOMPLETE"), missing)
            if (search.conflictingIds.isNotEmpty())
                return decision(VerificationQualification.CONFLICT, listOf("APOLLO_ID_CONFLICT"), missing)
            if (search.candidates.isEmpty()) return decision(qualification, listOf("APOLLO_NO_MATCH"), missing)
            val namesToMatch = allowedNames(snapshot, evidence)
            val named = search.candidates.filter { (nameMatches(it, namesToMatch, search.queryNames, masked = false, ruleVersion = ruleVersion) ||
                ruleVersion >= 1 && emailNameMatches(snapshot, evidence, it, search.institutionAliasVersion)) }
            if (named.any { it.orcid != null && snapshot.boundOrcid != null && normalizeOrcid(it.orcid) != snapshot.boundOrcid })
                return decision(VerificationQualification.CONFLICT, listOf("ORCID_CONFLICT"), missing)
            val supported = named.mapNotNull { candidate ->
                matchReason(snapshot, evidence, candidate, search.academicResearchFields, search.institutionAliasVersion)?.let { candidate to it }
            }
            val strong = supported.filter { it.second in setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH", "APOLLO_PROFESSOR_MATCH") }
            val matched = strong.ifEmpty { supported }
            if (matched.size != 1) return decision(qualification,
                listOf(if (matched.size > 1) "IDENTITY_AMBIGUOUS" else "IDENTITY_NOT_MATCHED"), missing)
            // An unresolved masked candidate could still be a second valid person; do not discard it.
            if (search.candidates.any { it.id != matched.single().first.id && explicitName(it) == null &&
                    (nameMatches(it, namesToMatch, search.queryNames, masked = true, ruleVersion = ruleVersion) ||
                        ruleVersion >= 1 && emailNameMatches(snapshot, evidence, it, search.institutionAliasVersion, masked = true)) &&
                    matchReason(snapshot, evidence, it, search.academicResearchFields, search.institutionAliasVersion) != null })
                return decision(qualification, listOf("IDENTITY_AMBIGUOUS"), missing)
            val (candidate, reason) = matched.single()
            val aliasReasons = if (reason in setOf("APOLLO_CURRENT_INSTITUTION_MATCH", "APOLLO_HISTORICAL_INSTITUTION_MATCH") &&
                matchReason(snapshot, evidence, candidate, search.academicResearchFields, 0) != reason)
                listOf(DiscoveryInstitutionNormalizer.TUM_ALIAS_REASON) else emptyList()
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
            (strict || masked) && (candidate.orcid == null || normalizeOrcid(candidate.orcid) == orcid) &&
                institutionMatches(candidate)
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
        candidate: ApolloCandidate, academicFields: String?, institutionAliasVersion: Int = 0): String? {
        val originals = listOfNotNull(snapshot.profile.institution?.let(::VerificationInstitution)) +
            identityEmployments(snapshot, evidence).map { VerificationInstitution(value(it, "institution")) }
        fun institutionMatch(institution: VerificationInstitution?): Boolean = institution != null && originals.any {
            DiscoveryInstitutionNormalizer.compareWithAliases(it, institution, institutionAliasVersion) == InstitutionComparison.MATCH
        }
        if (institutionMatch(candidate.institution)) return "APOLLO_CURRENT_INSTITUTION_MATCH"
        if (candidate.employmentHistory.any { job ->
                job.current == false && !job.endDate.isNullOrBlank() &&
                    runCatching { LocalDate.parse(job.endDate!!.take(10)) }.getOrNull()?.let { !it.isAfter(LocalDate.now(clock)) } == true &&
                    institutionMatch(job.institution)
            }) return "APOLLO_HISTORICAL_INSTITUTION_MATCH"
        if (apolloProfessor(candidate.title)) return "APOLLO_PROFESSOR_MATCH"
        // Whole specific phrases only. Generic job/industry labels are not academic specialties.
        val excluded = setOf("science", "engineering", "research", "education", "higher education", "technology",
            "professor", "researcher", "scientist", "科学", "研究", "教育", "工程", "其他")
        val specialties = academicFields.orEmpty().split(Regex("[,;，；\\n]")).map(::normalizeName)
            .filter { it.length >= 3 && it !in excluded }.distinct()
        val currentText = listOfNotNull(candidate.title, candidate.headline) +
            candidate.employmentHistory.filter { it.current == true }.mapNotNull { it.major }
        if (specialties.any { field -> currentText.any { text ->
                (" " + normalizeName(text) + " ").contains(" $field ")
            } }) return "APOLLO_SPECIALTY_MATCH"
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
    private fun isProfessor(role: String): Boolean {
        val value = normalizeName(role)
        if (Regex("emeritus|retired|former|student|candidate|visiting student|research group|lab of|assistant to|博士生|退休|课题组").containsMatchIn(value)) return false
        return value.matches(Regex("(?:(?:assistant|associate|teaching|adjunct|visiting|full|research|clinical|honorary) )?(?:professor|prof)")) ||
            value.matches(Regex("(?:助理|副|特聘|客座|讲席)?教授"))
    }
}
