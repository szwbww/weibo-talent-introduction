package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.discovery.domain.VerificationEvidence
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Which persisted value an institution subject was derived from. */
enum class InstitutionSource { PROFILE, ORCID_EMPLOYMENT, APOLLO_CANDIDATE }

/** Derived extraction status. This is audit metadata only; it is not a verification state. */
enum class InstitutionSubjectStatus { RESOLVED, AMBIGUOUS, UNRESOLVED }

/** Two explicit subjects only compare MATCH/DIFFERENT when both were extracted reliably. */
enum class InstitutionComparison { MATCH, DIFFERENT, UNKNOWN }

data class InstitutionRemovedPart(val text: String, val rule: String)

/**
 * One persisted input of the derived context. `version`/`source`/`status` are deliberately strings so an
 * unknown or tampered value is rejected by [DiscoveryInstitutionNormalizer.validate] instead of being lost
 * during deserialization.
 */
data class InstitutionInputNormalization(
    val source: String = "",
    val sourceRef: String = "",
    val raw: String? = null,
    val rawSha256: String = "",
    val status: String = "",
    val subjects: List<String> = emptyList(),
    val reason: String = "",
    val removedParts: List<InstitutionRemovedPart> = emptyList()
)

/** Optional evidence member. Absent/null keeps the pre-institution-subject comparison semantics. */
data class InstitutionNormalization(
    val version: String = "",
    val queryInstitution: String? = null,
    val inputs: List<InstitutionInputNormalization> = emptyList()
)

data class InstitutionEmploymentInput(val sourceRef: String, val raw: String?)
data class InstitutionCandidateInput(val sourceRef: String, val raw: String?)

/**
 * Pure institution subject extraction and comparison. No Spring, HTTP, clock or database access.
 *
 * The subject is a derived comparison value; every caller keeps the original profile, ORCID evidence and
 * Apollo candidate strings untouched. Versioned, source-backed aliases are opt-in; no translation,
 * abbreviation expansion, fuzzy matching, LLM call or inferred parent organisation.
 */
object DiscoveryInstitutionNormalizer {
    const val VERSION = "INST_SUBJECT_V1"
    const val MAX_RAW_LENGTH = 4096
    const val MAX_SEGMENTS = 32
    /** `OrcidVerificationEvidenceClient` and `QualificationEvidenceParser` project institution text at this bound. */
    const val ORCID_PROJECTION_LENGTH = 500

    const val RESOLVED = "RESOLVED"
    const val AMBIGUOUS = "AMBIGUOUS"
    const val UNRESOLVED = "UNRESOLVED"

    const val REASON_UNCHANGED_STRUCTURED = "UNCHANGED_STRUCTURED"
    const val REASON_SUBJECT_EXTRACTED = "SUBJECT_EXTRACTED"
    const val REASON_MULTIPLE_SUBJECTS = "MULTIPLE_SUBJECTS"
    const val REASON_MISSING = "MISSING"
    const val REASON_UNSUPPORTED_LAYOUT = "UNSUPPORTED_LAYOUT"
    const val REASON_INPUT_MAY_BE_TRUNCATED = "INPUT_MAY_BE_TRUNCATED"
    const val REASON_INPUT_LIMIT = "INPUT_LIMIT"

    const val REMOVED_FOOTNOTE = "LEADING_FOOTNOTE"
    const val REMOVED_DEPARTMENT = "DEPARTMENT_PREFIX"
    const val REMOVED_UNIT = "UNIT_SEGMENT"
    const val REMOVED_ADDRESS = "ADDRESS"
    const val REMOVED_ADDRESS_TAIL = "ADDRESS_TAIL"
    const val REMOVED_CONTACT = "EMAIL_OR_URL_FRAGMENT"
    const val REMOVED_UNRESOLVED = "UNRESOLVED_SEGMENT"

    private val mapper = jacksonObjectMapper().findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    private val institutionWord = "(?:Department|Dept\\.?|Institute|School|Faculty|Laborator(?:y|ies)|College|Cent(?:er|re)|Division|Program(?:me)?|University)"
    private val leadingFootnote = Regex("^\\s*(?:\\d{1,3}|[†‡*]+|[A-Za-z])\\s*(?=$institutionWord\\b)", RegexOption.IGNORE_CASE)
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val url = Regex("(?i)(?:https?://|www\\.)[^\\s,;]+")
    private val orgKeyword = Regex("(?i)\\b(?:university|universities|universit[ée]|universidad|universit[àa]|universit[äa]t|college|institute|institut|laborator(?:y|ies)|polytechnic|hospital|clinic|school)\\b")
    private val startsUnit = Regex("(?i)^(?:department|dept\\.?|division|faculty|school|program(?:me)?|graduate|chair|unit|team|group)\\b")
    private val postal = Regex("\\b\\d{4,6}\\b")
    private val street = Regex("(?i)\\b(?:street|st|road|rd|avenue|ave|boulevard|blvd|lane|drive|highway|route|way|place|plaza|court|suite|floor|building|bldg|daero|rue|strasse|straße|via|calle|rua|piazza)\\b|[a-z]-(?:gu|gun|si|do|ro|gil|dong|eup|myeon)\\b")
    private val streetNumber = Regex("^\\d+\\s")
    private val systemName = Regex("(?i)^(?:the\\s+)?(?:university|université|universidad|università|universität)\\s+of\\b")
    private val campusQualifier = Regex("(?i)\\b(?:campus|college|school|faculty|institute|laborator(?:y|ies)|cent(?:er|re))\\b")
    private val punctuation = Regex("[\\p{P}\\p{Z}\\s]+")

    private val countries = setOf(
        "usa", "us", "unitedstates", "unitedstatesofamerica", "america", "uk", "unitedkingdom", "england",
        "scotland", "wales", "northernireland", "ireland", "china", "peoplesrepublicofchina", "prc",
        "southkorea", "republicofkorea", "korea", "northkorea", "japan", "germany", "france", "italy", "spain",
        "portugal", "netherlands", "belgium", "switzerland", "austria", "sweden", "norway", "denmark", "finland",
        "poland", "czechrepublic", "slovakia", "hungary", "romania", "bulgaria", "greece", "croatia", "serbia",
        "slovenia", "ukraine", "russia", "canada", "mexico", "brazil", "argentina", "chile", "colombia", "peru",
        "venezuela", "ecuador", "uruguay", "costarica", "cuba", "jamaica", "australia", "newzealand", "india",
        "pakistan", "bangladesh", "singapore", "malaysia", "indonesia", "thailand", "vietnam", "philippines",
        "taiwan", "hongkong", "macau", "israel", "turkey", "egypt", "southafrica", "saudiarabia",
        "unitedarabemirates", "uae", "qatar", "iran", "iraq", "morocco", "tunisia", "algeria", "nigeria", "kenya",
        "ethiopia", "ghana", "iceland", "luxembourg", "cyprus", "malta", "estonia", "latvia", "lithuania"
    )

    private sealed class Parsed {
        data class Subjects(val subjects: List<String>, val removed: List<InstitutionRemovedPart>) : Parsed()
        data class Multiple(val subjects: List<String>, val removed: List<InstitutionRemovedPart>) : Parsed()
        data class Unresolved(val removed: List<InstitutionRemovedPart>) : Parsed()
        object Glue : Parsed()
        object Empty : Parsed()
    }

    /** Deterministic extraction of one saved input value. Never mutates or returns a rewritten original. */
    fun resolve(raw: String?, source: InstitutionSource, sourceRef: String): InstitutionInputNormalization {
        val hash = sha256(raw ?: "")
        fun record(status: String, reason: String, subjects: List<String> = emptyList(),
            rawValue: String? = raw, removed: List<InstitutionRemovedPart> = emptyList()) =
            InstitutionInputNormalization(source.name, sourceRef, rawValue, hash, status, subjects, reason, removed)
        if (raw.isNullOrBlank()) return record(UNRESOLVED, REASON_MISSING, rawValue = null)
        if (raw.length > MAX_RAW_LENGTH) return record(UNRESOLVED, REASON_INPUT_LIMIT, rawValue = null)
        if (source == InstitutionSource.ORCID_EMPLOYMENT && raw.length >= ORCID_PROJECTION_LENGTH)
            return record(UNRESOLVED, REASON_INPUT_MAY_BE_TRUNCATED)
        val normalized = collapse(raw)
        val removed = mutableListOf<InstitutionRemovedPart>()
        val footnote = leadingFootnote.find(normalized)
        val body = if (footnote != null && footnote.value.isNotBlank()) {
            removed += InstitutionRemovedPart(footnote.value.trim(), REMOVED_FOOTNOTE)
            normalized.removeRange(footnote.range).trim()
        } else normalized
        val segments = body.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.size > MAX_SEGMENTS) return record(UNRESOLVED, REASON_INPUT_LIMIT)
        val subjects = LinkedHashMap<String, String>()
        val fragments = mutableListOf<String>()
        var joint = false
        var multiple = false
        var unresolved = false
        for (segment in segments) {
            val contact = scanContact(segment)
            if (contact.glue) return record(UNRESOLVED, REASON_UNSUPPORTED_LAYOUT)
            contact.removed.forEach { removed += InstitutionRemovedPart(it, REMOVED_CONTACT) }
            if (contact.contactOnly) continue
            val pieces = jointSplit(contact.text)
            if (pieces.size > 1) joint = true
            fragments += pieces
            for (piece in pieces) {
                when (val parsed = parseSegment(piece, source)) {
                    is Parsed.Subjects -> {
                        parsed.subjects.forEach { subjects.putIfAbsent(canonical(it), it) }
                        removed += parsed.removed
                    }
                    is Parsed.Multiple -> {
                        multiple = true
                        parsed.subjects.forEach { subjects.putIfAbsent(canonical(it), it) }
                        removed += parsed.removed
                    }
                    is Parsed.Unresolved -> { unresolved = true; removed += parsed.removed }
                    Parsed.Glue -> return record(UNRESOLVED, REASON_UNSUPPORTED_LAYOUT)
                    Parsed.Empty -> Unit
                }
            }
        }
        val values = subjects.values.toList()
        return when {
            multiple || values.size > 1 || (values.isNotEmpty() && unresolved) ||
                (joint && values.isEmpty() && unresolved) -> {
                val candidates = if (values.isEmpty() && joint) fragments.distinctBy(::canonical) else values
                record(AMBIGUOUS, REASON_MULTIPLE_SUBJECTS, candidates, removed = removed.distinct())
            }
            values.size == 1 -> {
                val subject = values.single()
                val unchanged = source == InstitutionSource.ORCID_EMPLOYMENT && canonical(normalized) == canonical(subject)
                record(RESOLVED, if (unchanged) REASON_UNCHANGED_STRUCTURED else REASON_SUBJECT_EXTRACTED,
                    listOf(subject), removed = removed.distinct())
            }
            else -> record(UNRESOLVED, REASON_UNSUPPORTED_LAYOUT, removed = removed.distinct())
        }
    }

    /** Builds the full derived context in the fixed PROFILE, employment, candidate order. */
    fun build(profileInstitution: String?, employments: List<VerificationEvidence>,
        candidates: List<ApolloCandidate>?): InstitutionNormalization =
        buildInputs(profileInstitution, employmentInputs(employments), candidateInputs(candidates.orEmpty()))

    fun buildInputs(profileInstitution: String?, employments: List<InstitutionEmploymentInput>,
        candidates: List<InstitutionCandidateInput>): InstitutionNormalization {
        val inputs = mutableListOf<InstitutionInputNormalization>()
        inputs += resolve(profileInstitution, InstitutionSource.PROFILE, "profile.institution")
        employments.forEach {
            inputs += resolve(it.raw, InstitutionSource.ORCID_EMPLOYMENT, it.sourceRef)
        }
        candidates.sortedBy { it.sourceRef }.forEach {
            inputs += resolve(it.raw, InstitutionSource.APOLLO_CANDIDATE, it.sourceRef)
        }
        return InstitutionNormalization(VERSION, queryInstitution(inputs), inputs)
    }

    /** Adds or refreshes only the Apollo-derived member; the frozen members and query are retained. */
    fun withCandidates(base: InstitutionNormalization, candidates: List<ApolloCandidate>): InstitutionNormalization {
        val kept = base.inputs.filter { it.source != InstitutionSource.APOLLO_CANDIDATE.name }
        val next = candidateInputs(candidates).map {
            resolve(it.raw, InstitutionSource.APOLLO_CANDIDATE, it.sourceRef)
        }
        return base.copy(inputs = kept + next)
    }

    /** Rebuilds the expected context from original inputs and rejects unknown or altered persisted values. */
    fun validate(expected: InstitutionNormalization, saved: InstitutionNormalization, includeCandidates: Boolean = true) {
        require(saved.version == VERSION) { "Unknown institution normalization version" }
        val actual = if (includeCandidates) saved.inputs
            else saved.inputs.filter { it.source != InstitutionSource.APOLLO_CANDIDATE.name }
        val wanted = if (includeCandidates) expected.inputs
            else expected.inputs.filter { it.source != InstitutionSource.APOLLO_CANDIDATE.name }
        require(actual.size == wanted.size) { "Institution normalization input count mismatch" }
        actual.zip(wanted).forEach { (left, right) ->
            require(left == right) { "Institution normalization input mismatch" }
        }
        require(saved.queryInstitution == expected.queryInstitution) { "Institution normalization query mismatch" }
    }

    fun queryInstitution(inputs: List<InstitutionInputNormalization>): String? {
        val employments = inputs.filter { it.source == InstitutionSource.ORCID_EMPLOYMENT.name }
        if (employments.isEmpty()) return null
        if (employments.any { it.status != RESOLVED || it.subjects.size != 1 }) return null
        val distinct = employments.map { canonical(it.subjects.single()) }.toSet()
        return if (distinct.size == 1) employments.first().subjects.single() else null
    }

    fun employmentInputs(employments: List<VerificationEvidence>): List<InstitutionEmploymentInput> {
        val seen = HashMap<String, Int>()
        return employments.map { evidence ->
            val base = "evidence:${evidence.provider}:${evidence.sourceUrl}#${evidence.jsonPointer ?: ""}"
            val occurrence = seen.merge(base, 1, Int::plus)!! - 1
            InstitutionEmploymentInput(if (occurrence == 0) base else "$base#$occurrence", institutionOf(evidence))
        }
    }

    fun candidateInputs(candidates: List<ApolloCandidate>): List<InstitutionCandidateInput> =
        candidates.sortedBy { it.id }.map { InstitutionCandidateInput(it.id, it.institution?.name) }

    /** MATCH/DIFFERENT only for two reliably extracted subjects; anything else is UNKNOWN. */
    fun compare(left: InstitutionInputNormalization, right: InstitutionInputNormalization): InstitutionComparison {
        if (left.status != RESOLVED || right.status != RESOLVED) return InstitutionComparison.UNKNOWN
        val leftSubjects = left.subjects.map(::canonical).toSet()
        val rightSubjects = right.subjects.map(::canonical).toSet()
        return if (leftSubjects.any { it.isNotEmpty() && it in rightSubjects }) InstitutionComparison.MATCH
        else InstitutionComparison.DIFFERENT
    }

    /**
     * Comparison entry point for callers that still hold `VerificationInstitution` values. Explicit standard
     * identifiers and aliases keep their previous meaning; extracted text never creates an identifier or alias.
     */
    fun compare(left: VerificationInstitution, right: VerificationInstitution,
        leftSource: InstitutionSource = InstitutionSource.PROFILE,
        rightSource: InstitutionSource = InstitutionSource.APOLLO_CANDIDATE): InstitutionComparison {
        if (left.standardId != null && right.standardId != null) {
            if (left.standardId == right.standardId) return InstitutionComparison.MATCH
            val leftNamespace = left.standardId.substringBefore(':')
            val rightNamespace = right.standardId.substringBefore(':')
            return if (leftNamespace.equals(rightNamespace, ignoreCase = true)) InstitutionComparison.DIFFERENT
            else InstitutionComparison.UNKNOWN
        }
        val leftRecord = resolve(left.name, leftSource, "compare:left")
        val rightRecord = resolve(right.name, rightSource, "compare:right")
        val leftNames = leftRecord.subjects.map(::canonical).toSet() + left.aliases.map(::canonical)
        val rightNames = rightRecord.subjects.map(::canonical).toSet() + right.aliases.map(::canonical)
        return when {
            leftNames.any { it.isNotEmpty() && it in rightNames } -> InstitutionComparison.MATCH
            leftRecord.status == RESOLVED && rightRecord.status == RESOLVED -> InstitutionComparison.DIFFERENT
            else -> InstitutionComparison.UNKNOWN
        }
    }

    // Immutable V1 allowlist. Official bilingual contact pages identify the same university/address.
    // https://www.tum.de/en/about-tum/contact-directions
    // https://www.tum.de/ueber-die-tum/kontakt-und-anfahrt
    // Additions require a new version so frozen decisions retain their original semantics.
    const val ALIAS_VERSION = 1
    const val TUM_ALIAS_REASON = "APOLLO_INSTITUTION_ALIAS_TUM_V1"
    private val tumNamesV1 = setOf("Technical University of Munich", "Technische Universität München").map(::canonical).toSet()

    fun compareWithAliases(left: VerificationInstitution, right: VerificationInstitution, aliasVersion: Int): InstitutionComparison {
        require(aliasVersion in 0..ALIAS_VERSION) { "Unsupported institution alias version" }
        val original = compare(left, right)
        // Never override explicit identifiers, or change legacy callers/results.
        if (aliasVersion == 0 || original == InstitutionComparison.MATCH ||
            left.standardId != null && right.standardId != null) return original
        fun knownSubject(institution: VerificationInstitution): Boolean {
            // Full exact names also work for languages unsupported by the subject extractor.
            if (canonical(institution.name) in tumNamesV1) return true
            val record = resolve(institution.name, InstitutionSource.APOLLO_CANDIDATE, "alias")
            return record.status == RESOLVED && record.subjects.singleOrNull()?.let(::canonical) in tumNamesV1
        }
        return if (knownSubject(left) && knownSubject(right)) InstitutionComparison.MATCH else original
    }

    fun canonical(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(punctuation, " ").trim()

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private data class ContactScan(val text: String, val removed: List<String>, val contactOnly: Boolean, val glue: Boolean)

    private fun scanContact(segment: String): ContactScan {
        var text = segment
        val removed = mutableListOf<String>()
        for (regex in listOf(url, email)) {
            val match = regex.find(text) ?: continue
            val before = text.getOrNull(match.range.first - 1)
            val after = text.getOrNull(match.range.last + 1)
            val local = match.value.substringBefore('@')
            if ((before != null && before.isLetterOrDigit()) || (after != null && after.isLetterOrDigit()) ||
                (match.value.contains('@') && orgKeyword.containsMatchIn(local)))
                return ContactScan(text, removed, false, true)
            removed += match.value
            text = text.removeRange(match.range)
        }
        if (removed.isEmpty()) return ContactScan(text, removed, false, false)
        text = Regex("\\([^)]*\\)").replace(text) { part ->
            if (orgKeyword.containsMatchIn(part.value)) part.value else ""
        }.trim().trim(',', ';', '.')
        val contactOnly = text.none { it.isLetterOrDigit() }
        return ContactScan(text, removed, contactOnly, false)
    }

    private fun jointSplit(text: String): List<String> {
        val first = mutableListOf<String>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '-' && index > 0 && (text[index - 1].isUpperCase() || text[index - 1].isDigit())) {
                var next = index + 1
                while (next < text.length && text[next].isWhitespace()) next++
                if (next < text.length && text[next].isUpperCase()) {
                    first += text.substring(start, index).trim()
                    start = next
                    index = next
                    continue
                }
            }
            index++
        }
        first += text.substring(start).trim()
        return first.filter { it.isNotEmpty() }.flatMap { fragment ->
            val pieces = mutableListOf<String>()
            var from = 0
            var split = fragment.indexOf(" - ")
            while (split >= 0) {
                val before = fragment.substring(from, split).trim().split(' ').lastOrNull().orEmpty()
                val after = fragment.substring(split + 3).trim().split(' ').firstOrNull().orEmpty()
                if (before.length >= 2 && before.all { it.isUpperCase() } &&
                    after.length >= 2 && after.all { it.isUpperCase() }) {
                    pieces += fragment.substring(from, split).trim()
                    from = split + 3
                }
                split = fragment.indexOf(" - ", split + 1)
            }
            pieces += fragment.substring(from).trim()
            pieces.filter { it.isNotEmpty() }
        }
    }

    private fun parseSegment(text: String, source: InstitutionSource): Parsed {
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Parsed.Empty
        // A unit and an organisation in one comma block cannot be separated without inventing a boundary.
        if (parts.any(::gluedUnitAndOrganisation)) return Parsed.Glue
        val orgIndexes = parts.indices.filter { isOrganisation(parts[it]) }
        val removed = mutableListOf<InstitutionRemovedPart>()
        if (orgIndexes.isEmpty()) {
            val whole = parts.joinToString(", ")
            return if (source == InstitutionSource.ORCID_EMPLOYMENT && cleanStructured(whole, parts))
                Parsed.Subjects(listOf(whole), removed) else Parsed.Unresolved(removed)
        }
        val unitIndexes = parts.indices.filter { startsUnit.containsMatchIn(parts[it]) && !isOrganisation(parts[it]) }
        val addressIndexes = parts.indices.filter { isAddressPart(parts[it]) }
        if (orgIndexes.size == 1 && unitIndexes.isEmpty() && addressIndexes.isEmpty()) {
            // Complete independent name: keep every comma-separated campus qualifier verbatim.
            return Parsed.Subjects(listOf(parts.joinToString(", ")), removed)
        }
        val firstOrg = orgIndexes.first()
        val lastOrg = orgIndexes.last()
        val leadingNames = mutableListOf<String>()
        for (index in 0 until firstOrg) {
            when {
                startsUnit.containsMatchIn(parts[index]) -> removed += InstitutionRemovedPart(parts[index], REMOVED_DEPARTMENT)
                isAddressPart(parts[index]) -> removed += InstitutionRemovedPart(parts[index], REMOVED_ADDRESS)
                else -> leadingNames += parts[index]
            }
        }
        val trailing = (lastOrg + 1) until parts.size
        val trailingParts = trailing.map { parts[it] }
        val hadLeadingUnit = (0 until firstOrg).any { startsUnit.containsMatchIn(parts[it]) && !isOrganisation(parts[it]) }
        val tailRemovable = trailingParts.isNotEmpty() &&
            isAddressTail(trailingParts, parts[lastOrg], hadLeadingUnit)
        val retainQualifier = trailingParts.isNotEmpty() && !tailRemovable &&
            (isSystemName(parts[lastOrg]) || trailingParts.any(::isCampusQualifier))
        val qualifiers = mutableListOf<String>()
        val residue = mutableListOf<String>()
        for (index in trailing) {
            val part = parts[index]
            when {
                tailRemovable -> removed += InstitutionRemovedPart(part, REMOVED_ADDRESS_TAIL)
                isCountry(part) -> removed += InstitutionRemovedPart(part, REMOVED_ADDRESS)
                startsUnit.containsMatchIn(part) -> removed += InstitutionRemovedPart(part, REMOVED_UNIT)
                isAddressPart(part) -> removed += InstitutionRemovedPart(part, REMOVED_ADDRESS)
                retainQualifier -> qualifiers += part
                else -> residue += part
            }
        }
        for (index in firstOrg..lastOrg) {
            if (index in orgIndexes) continue
            if (startsUnit.containsMatchIn(parts[index])) removed += InstitutionRemovedPart(parts[index], REMOVED_UNIT)
            else residue += parts[index]
        }
        val subjects = orgIndexes.map { parts[it] }.distinctBy(::canonical)
        if (orgIndexes.size > 1) return Parsed.Multiple(subjects, removed.distinct())
        if (residue.isNotEmpty()) return Parsed.Unresolved((removed + residue.map {
            InstitutionRemovedPart(it, REMOVED_UNRESOLVED)
        }).distinct())
        val subject = (leadingNames + parts[orgIndexes.single()] + qualifiers).joinToString(", ")
        return Parsed.Subjects(listOf(subject), removed.distinct())
    }

    private fun cleanStructured(whole: String, parts: List<String>): Boolean =
        parts.size == 1 && whole.length in 2..200 && whole.any { it.isLetter() } &&
            whole.none { it.isDigit() } && !isAddressPart(whole) && !startsUnit.containsMatchIn(whole) &&
            !orgKeyword.containsMatchIn(whole)

    private fun isOrganisation(part: String): Boolean =
        orgKeyword.containsMatchIn(part) && !startsUnit.containsMatchIn(part)

    /** A leading department word is not itself evidence of a second organisation. */
    private fun gluedUnitAndOrganisation(part: String): Boolean {
        val unit = startsUnit.find(part) ?: return false
        return orgKeyword.containsMatchIn(part.removeRange(unit.range))
    }

    private fun isAddressTail(parts: List<String>, organisation: String, hadLeadingUnit: Boolean): Boolean {
        if (parts.isEmpty()) return false
        if (!isCountry(parts.last())) return parts.all { isAddressPart(it) }
        val qualifiers = parts.dropLast(1)
        if (qualifiers.any(::isStrongAddressPart)) return qualifiers.all { isLocationToken(it) || isAddressPart(it) }
        // A campus qualifier or a system name such as "University of California" is part of the
        // institution, never proven address data merely because a country follows it.
        if (qualifiers.any(::isCampusQualifier) || isSystemName(organisation)) return false
        // Keep the approved city+country minimum cases only when a unit prefix establishes the
        // segment as an address-bearing affiliation; otherwise return UNRESOLVED rather than collapse.
        return hadLeadingUnit && qualifiers.all { isLocationToken(it) }
    }

    private fun isStrongAddressPart(part: String): Boolean =
        postal.containsMatchIn(part) || streetNumber.containsMatchIn(part) || street.containsMatchIn(part)

    private fun isSystemName(organisation: String): Boolean =
        systemName.containsMatchIn(organisation)

    private fun isCampusQualifier(part: String): Boolean =
        campusQualifier.containsMatchIn(part)

    private fun isLocationToken(part: String): Boolean {
        if (part.isEmpty()) return false
        if (orgKeyword.containsMatchIn(part) || startsUnit.containsMatchIn(part)) return false
        if (part.any { it == '@' }) return false
        return part.any { it.isLetter() } || part.all { it.isDigit() }
    }

    private fun isCountry(part: String): Boolean =
        part.lowercase(Locale.ROOT).replace(".", "").replace(Regex("[\\s,]+"), "") in countries

    private fun isAddressPart(part: String): Boolean =
        isCountry(part) || postal.containsMatchIn(part) || streetNumber.containsMatchIn(part) ||
            street.containsMatchIn(part)

    private fun institutionOf(evidence: VerificationEvidence): String? =
        runCatching { mapper.readTree(evidence.value).path("institution").asText("") }
            .getOrNull()?.takeIf { it.isNotBlank() }

    private fun collapse(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim()
}
