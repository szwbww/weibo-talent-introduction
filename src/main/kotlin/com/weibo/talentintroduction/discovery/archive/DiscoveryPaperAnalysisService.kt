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
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class DiscoveryPaperAnalysisService(private val archive: DiscoveryPaperArchiveService,
    private val pdf: PdfEmailExtractor, private val qualification: PaperQualificationEvidenceExtractor,
    private val mapper: ObjectMapper) {
    private val running = ConcurrentHashMap.newKeySet<Long>()
    internal var beforeParse: () -> Unit = {}
    fun analyze(id: Long, force: Boolean = false): JsonNode {
        if (!running.add(id)) throw ArchiveException("ANALYSIS_RUNNING", 409)
        try {
            archive.analysisLease(id).use { lease ->
                val record = lease.record
                val metadata = mapper.readTree(record.metadataJson)
                val first = record.outcomeJson?.let { mapper.readTree(it).path("first") }
                val anchorNode = first?.path("attemptIds")
                val baseline = first?.path("outcome")?.takeIf { it.isObject && it.path("emails").isArray }
                val anchored = anchorNode != null && anchorNode.isArray && !anchorNode.isEmpty &&
                    anchorNode.all { it.isIntegralNumber && it.canConvertToLong() && it.longValue() > 0 } &&
                    anchorNode.map { it.longValue() }.distinct().size == anchorNode.size()
                val anchors = if (anchored) anchorNode!!.map { it.longValue() } else emptyList()
                val available = lease.fileSet.map { it.first }.toSet()
                val missing = anchors.filter { it !in available }
                val baselineCoverage = when {
                    baseline == null || !anchored -> "BASELINE_MISSING"
                    missing.isNotEmpty() -> "PARTIAL"
                    else -> "COMPLETE"
                }
                val hashes = lease.bodies.map { it.hash }.sorted()
                val versions = mapOf("identityExtraction" to DiscoveryIdentity.EXTRACTION_VERSION, "pdfLayout" to PdfAuthorContactLayout.RULE_VERSION,
                    "format" to PaperContentFormat.VERSION, "qualification" to PaperQualificationEvidenceExtractor.VERSION)
                record.analysisJson?.let { cached ->
                    val value = mapper.readTree(cached)
                    if (!force && value.path("schemaVersion").asInt() == 1 && value.path("analysisVersion").asInt() == 2 &&
                        value.path("parserVersion") == mapper.valueToTree<JsonNode>(versions) &&
                        value.path("inputBodyHashes") == mapper.valueToTree<JsonNode>(hashes) &&
                        value.path("baselineCoverage").asText() == baselineCoverage &&
                        value.path("missingBodies") == mapper.valueToTree<JsonNode>(missing)) return value
                }
                beforeParse()
                val authors = metadata.path("authors").mapNotNull { author ->
                    if (!author.isObject) null else mapper.treeToValue(author, PaperAuthor::class.java)
                }
                val year = (metadata.get("pubYear") ?: metadata.get("year"))?.takeIf { it.isIntegralNumber }?.asInt()
                val emails = mutableListOf<AuthorEmail>()
                val facts = mutableListOf<PaperQualificationFact>()
                val coverages = mutableListOf<PaperAnalysisCoverage>()
                val reasons = mutableListOf<String>()
                if (baselineCoverage != "COMPLETE") reasons += if (baselineCoverage == "PARTIAL") "BASELINE_BODIES_MISSING" else "BASELINE_MISSING"
                for (body in lease.bodies) {
                    val bytes = body.input.readBytes()
                    val format = PaperContentFormat.detect(bytes, body.attempt.kind)
                    val result = qualification.extract(bytes, format, authors, year, body.hash)
                    coverages += result.coverage
                    facts += result.facts
                    try {
                        emails += when (format) {
                            "PDF" -> pdf.extractEmailsFromBytes(bytes, authors, null)
                            "XML" -> { qualification.safeText(bytes, "XML"); JatsXmlEmailParser.parse(bytes) }
                            "HTML" -> SourceAuthorEmailResolver.resolveHtml(bytes.toString(Charsets.UTF_8), authors)
                            else -> SourceAuthorEmailResolver.resolveText(bytes.toString(Charsets.UTF_8), authors)
                        }
                    } catch (_: Exception) { reasons += "EMAIL_PARSE_ERROR" }
                }
                fun bindings(values: List<AuthorEmail>): Pair<List<PaperAnalysisBinding>, List<PaperAnalysisBinding>> {
                    val conflicts = mutableListOf<PaperAnalysisBinding>()
                    val merged = values.groupBy { it.email.trim().lowercase() }.map { (email, entries) ->
                        val named = entries.mapNotNull { e ->
                            val name = listOfNotNull(e.givenNames, e.familyNames).joinToString(" ").trim()
                            name.takeIf { !e.givenNames.isNullOrBlank() && !e.familyNames.isNullOrBlank() }
                        }.distinctBy { it.lowercase() }
                        if (named.size > 1) {
                            conflicts += named.map { PaperAnalysisBinding(email, it) }
                            PaperAnalysisBinding(email, null)
                        } else PaperAnalysisBinding(email, named.singleOrNull(), entries.firstOrNull { it.identityEvidence != null }?.identityEvidence)
                    }
                    return merged.sortedWith(compareBy({ it.email }, { it.authorName ?: "" })) to conflicts
                }
                val (after, conflicts) = bindings(emails)
                val before = baseline?.let { b -> bindings(b.path("emails").map { mapper.treeToValue(it, AuthorEmail::class.java) }).first }
                fun key(b: PaperAnalysisBinding) = b.email to b.authorName?.lowercase()
                val added = if (before == null) emptyList() else after.filter { it.authorName != null && before.none { old -> key(old) == key(it) } }
                val removed = if (before == null) emptyList() else before.filter { it.authorName != null && after.none { new -> key(new) == key(it) } }
                reasons += coverages.flatMap { it.reasons }
                if (facts.size > PaperQualificationEvidenceExtractor.MAX_FACTS) reasons += "FACT_LIMIT"
                val incomplete = missing.isNotEmpty() || reasons.any { it !in listOf("BASELINE_MISSING") }
                val status = when {
                    coverages.any { it.status == PaperEvidenceStatus.ERROR } || "EMAIL_PARSE_ERROR" in reasons -> PaperEvidenceStatus.ERROR
                    incomplete -> PaperEvidenceStatus.PARTIAL
                    facts.isEmpty() -> PaperEvidenceStatus.NOT_FOUND
                    else -> PaperEvidenceStatus.FOUND
                }
                val complete = baselineCoverage == "COMPLETE" && status != PaperEvidenceStatus.ERROR && status != PaperEvidenceStatus.PARTIAL
                val delta = if (complete) PaperAnalysisDelta(added.size, removed.size) else null
                val output = PaperAnalysisResult(analysisVersion = 2, parserVersion = versions, analyzedAt = Instant.now().toString(), inputBodyHashes = hashes,
                    baselineCaptureAt = first?.path("capturedAt")?.takeIf { it.isTextual }?.asText(), baselineCoverage = baselineCoverage,
                    missingBodies = missing, coverage = PaperAnalysisCoverage(status, coverages.sumOf { it.scannedPages }, coverages.sumOf { it.totalPages }, reasons.distinct()),
                    before = before, after = after, added = if (delta == null) emptyList() else added, removed = if (delta == null) emptyList() else removed,
                    conflicts = conflicts, qualificationFacts = facts.take(PaperQualificationEvidenceExtractor.MAX_FACTS), delta = delta,
                    deltaAddedBindings = delta?.addedBindings, hasGain = delta?.let { it.addedBindings > 0 }, reasons = reasons.distinct())
                val tree = mapper.valueToTree<JsonNode>(output)
                archive.commitAnalysis(lease, tree)
                return tree
            }
        } finally { running.remove(id) }
    }
}
