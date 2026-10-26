package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.domain.AuthorEmail
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.service.JatsXmlEmailParser
import com.weibo.talentintroduction.discovery.service.PdfAuthorContactLayout
import com.weibo.talentintroduction.discovery.service.PdfEmailExtractor
import com.weibo.talentintroduction.discovery.service.SourceAuthorEmailResolver
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.springframework.stereotype.Component
import java.time.Instant

/** Value inputs only: neither computation entry owns storage, leases, clocks or provider clients. */
data class PaperBodyInput(val hash: String, val kind: ArchiveAttemptKind, val bytes: ByteArray)
data class PaperBodyComputation(val format: String, val emails: List<AuthorEmail>,
    val qualification: PaperQualificationResult, val reasons: List<String>)

@Component
class PaperBodyAnalyzer(private val pdf: PdfEmailExtractor,
    private val qualification: PaperQualificationEvidenceExtractor, private val mapper: ObjectMapper) {
    fun versions(): Map<String, Int> = mapOf("identityExtraction" to DiscoveryIdentity.EXTRACTION_VERSION,
        "pdfLayout" to PdfAuthorContactLayout.RULE_VERSION, "format" to PaperContentFormat.VERSION,
        "qualification" to PaperQualificationEvidenceExtractor.RULE_VERSION)

    fun parse(body: PaperBodyInput, metadata: JsonNode): PaperBodyComputation {
        require(DiscoveryPaperArchiveService.sha(body.bytes) == body.hash) { "BODY_HASH_MISMATCH" }
        val authors = metadata.path("authors").mapNotNull {
            if (it.isObject) mapper.treeToValue(it, PaperAuthor::class.java) else null
        }
        val year = (metadata.get("pubYear") ?: metadata.get("year"))?.takeIf { it.isIntegralNumber }?.asInt()
        val format = PaperContentFormat.detect(body.bytes, body.kind)
        val facts = qualification.extract(body.bytes, format, authors, year, body.hash)
        val reasons = mutableListOf<String>()
        val emails = try {
            when (format) {
                "PDF" -> pdf.extractEmailsFromBytes(body.bytes, authors, null)
                "XML" -> { qualification.safeText(body.bytes, "XML"); JatsXmlEmailParser.parse(body.bytes) }
                "HTML" -> SourceAuthorEmailResolver.resolveHtml(body.bytes.toString(Charsets.UTF_8), authors)
                else -> SourceAuthorEmailResolver.resolveText(body.bytes.toString(Charsets.UTF_8), authors)
            }
        } catch (_: Exception) { reasons += "EMAIL_PARSE_ERROR"; emptyList<AuthorEmail>() }
        return PaperBodyComputation(format, emails, facts, reasons)
    }

    fun baselineCoverage(first: JsonNode?, availableAttemptIds: Set<Long>): Pair<String, List<Long>> {
        val anchor = first?.path("attemptIds")
        val baseline = first?.path("outcome")?.takeIf { it.isObject && it.path("emails").isArray }
        val anchored = anchor != null && anchor.isArray && !anchor.isEmpty &&
            anchor.all { it.isIntegralNumber && it.canConvertToLong() && it.longValue() > 0 } &&
            anchor.map { it.longValue() }.distinct().size == anchor.size()
        val missing = if (anchored) anchor!!.map { it.longValue() }.filter { it !in availableAttemptIds } else emptyList()
        return (when { baseline == null || !anchored -> "BASELINE_MISSING"; missing.isNotEmpty() -> "PARTIAL"; else -> "COMPLETE" }) to missing
    }

    fun compute(bodies: List<PaperBodyInput>, metadata: JsonNode, first: JsonNode?,
        availableAttemptIds: Set<Long>, analyzedAt: Instant, parserVersions: Map<String, Int> = versions()): PaperAnalysisResult =
        assemble(bodies.distinctBy { it.hash }.associate { it.hash to parse(it, metadata) }, first, availableAttemptIds, analyzedAt, parserVersions)

    /** Allows one invocation-scoped parse per identical source body across archive records. No retained cache. */
    fun assemble(parsed: Map<String, PaperBodyComputation>, first: JsonNode?, availableAttemptIds: Set<Long>,
        analyzedAt: Instant, parserVersions: Map<String, Int> = versions()): PaperAnalysisResult {
        val (baselineCoverage, missing) = baselineCoverage(first, availableAttemptIds)
        val baseline = first?.path("outcome")?.takeIf { it.isObject && it.path("emails").isArray }
        val reasons = mutableListOf<String>()
        if (baselineCoverage != "COMPLETE") reasons += if (baselineCoverage == "PARTIAL") "BASELINE_BODIES_MISSING" else "BASELINE_MISSING"
        val facts = parsed.values.flatMap { it.qualification.facts }
        val coverages = parsed.values.map { it.qualification.coverage }
        reasons += parsed.values.flatMap { it.reasons }
        reasons += coverages.flatMap { it.reasons }
        if (facts.size > PaperQualificationEvidenceExtractor.MAX_FACTS) reasons += "FACT_LIMIT"
        fun bindings(values: List<AuthorEmail>): Pair<List<PaperAnalysisBinding>, List<PaperAnalysisBinding>> {
            val conflicts = mutableListOf<PaperAnalysisBinding>()
            val merged = values.groupBy { it.email.trim().lowercase() }.map { (email, entries) ->
                val named = entries.mapNotNull { e ->
                    listOfNotNull(e.givenNames, e.familyNames).joinToString(" ").trim()
                        .takeIf { !e.givenNames.isNullOrBlank() && !e.familyNames.isNullOrBlank() }
                }.distinctBy { it.lowercase() }
                if (named.size > 1) {
                    conflicts += named.map { PaperAnalysisBinding(email, it) }; PaperAnalysisBinding(email, null)
                } else PaperAnalysisBinding(email, named.singleOrNull(), entries.firstOrNull { it.identityEvidence != null }?.identityEvidence)
            }
            return merged.sortedWith(compareBy({ it.email }, { it.authorName ?: "" })) to conflicts
        }
        val (after, conflicts) = bindings(parsed.values.flatMap { it.emails })
        val before = baseline?.let { b -> bindings(b.path("emails").map { mapper.treeToValue(it, AuthorEmail::class.java) }).first }
        fun key(b: PaperAnalysisBinding) = b.email to b.authorName?.lowercase()
        val added = if (before == null) emptyList() else after.filter { it.authorName != null && before.none { old -> key(old) == key(it) } }
        val removed = if (before == null) emptyList() else before.filter { it.authorName != null && after.none { new -> key(new) == key(it) } }
        val incomplete = missing.isNotEmpty() || reasons.any { it != "BASELINE_MISSING" }
        val status = when {
            coverages.any { it.status == PaperEvidenceStatus.ERROR } || "EMAIL_PARSE_ERROR" in reasons -> PaperEvidenceStatus.ERROR
            incomplete -> PaperEvidenceStatus.PARTIAL
            facts.isEmpty() -> PaperEvidenceStatus.NOT_FOUND
            else -> PaperEvidenceStatus.FOUND
        }
        val delta = if (baselineCoverage == "COMPLETE" && status !in setOf(PaperEvidenceStatus.ERROR, PaperEvidenceStatus.PARTIAL)) PaperAnalysisDelta(added.size, removed.size) else null
        return PaperAnalysisResult(analysisVersion = 2, parserVersion = parserVersions, analyzedAt = analyzedAt.toString(),
            inputBodyHashes = parsed.keys.sorted(), baselineCaptureAt = first?.path("capturedAt")?.takeIf { it.isTextual }?.asText(),
            baselineCoverage = baselineCoverage, missingBodies = missing,
            coverage = PaperAnalysisCoverage(status, coverages.sumOf { it.scannedPages }, coverages.sumOf { it.totalPages }, reasons.distinct()),
            before = before, after = after, added = if (delta == null) emptyList() else added, removed = if (delta == null) emptyList() else removed,
            conflicts = conflicts, qualificationFacts = facts.take(PaperQualificationEvidenceExtractor.MAX_FACTS), delta = delta,
            deltaAddedBindings = delta?.addedBindings, hasGain = delta?.let { it.addedBindings > 0 }, reasons = reasons.distinct())
    }
}
