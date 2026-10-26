package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Instant
import java.time.LocalDate

/** Prepared by the caller once. Collection never searches for a replacement for this identity. */
data class PaperSourceRef(val archiveRecordId: Long, val attemptId: Long, val bodySha256: String,
    val sourceExecutionId: Long, val sourceName: String, val sourceItemKey: String) {
    init { require(archiveRecordId > 0 && attemptId > 0 && sourceExecutionId > 0); verificationHash(bodySha256) }
}
data class PaperSourceEvidence(val source: PaperSourceRef, val coverage: PaperAnalysisCoverage?,
    val binding: PaperAnalysisBinding?, val evidence: List<VerificationEvidence>, val reasonCodes: List<String>,
    val analysis: PaperAnalysisResult? = null, val boundAuthorIndex: Int? = null, val bindingMethod: String? = null)
data class PaperEvidenceRead(val sources: List<PaperSourceEvidence>, val reasonCodes: List<String>, val parsedBodies: Int)

@Service
class PaperVerificationEvidenceService(private val repository: DiscoveryPaperArchiveRepository,
    private val archive: DiscoveryPaperArchiveService, private val analyzer: PaperBodyAnalyzer, private val mapper: ObjectMapper) {
    /** Bounded preparation helper. Historical reuse is declared in every returned ref, not hidden at execution. */
    fun locate(requests: List<PaperArchiveLookup>): Map<PaperArchiveLookup, List<PaperSourceRef>> {
        val originals = repository.lookupOriginal(requests)
        return requests.distinct().associateWith { request ->
            val candidates = originals[request]?.let { listOf(it) } ?: repository.lookupHistorical(request).let {
                if (it.nextAfterId != null) throw ArchiveException("SOURCE_LOOKUP_LIMIT",409)
                it.records
            }
            val records = candidates.filter { matchesIds(it, request) }.associateBy { it.id }
            repository.lookupSourceAttempts(records.keys.toList()).map { attempt ->
                val r = records.getValue(attempt.recordId)
                PaperSourceRef(r.id, attempt.id, attempt.blobSha256!!, r.executionId, r.sourceName, r.paperKey)
            }
        }
    }
    private fun matchesIds(record: ArchiveRecord, request: PaperArchiveLookup): Boolean {
        val m = mapper.readTree(record.metadataJson)
        return listOf(
            PaperArchiveLookup.doi(request.doi) to PaperArchiveLookup.doi(m.path("doi").asText(null)),
            PaperArchiveLookup.pmid(request.pmid) to PaperArchiveLookup.pmid(m.path("pmid").asText(null)),
            PaperArchiveLookup.pmcid(request.pmcid) to PaperArchiveLookup.pmcid(m.path("pmcId").asText(null))
        ).all { (expected, actual) -> expected == null || expected == actual }
    }
    private data class Read(val ref: PaperSourceRef, val record: ArchiveRecord, val metadata: JsonNode,
        val first: JsonNode?, val available: Set<Long>, val body: PaperBodyInput, val sourceUrl: String?)

    fun collect(snapshot: VerificationSnapshot, refs: List<PaperSourceRef>, mode: RecheckEvidenceMode,
        evaluatedAt: Instant, applicationOrigin: String? = null): PaperEvidenceRead {
        require(refs.size <= 100)
        // Both modes read the fixed retained source. REFRESH is not permission to download a different body.
        require(mode in RecheckEvidenceMode.values())
        val results = mutableMapOf<PaperSourceRef, PaperSourceEvidence>()
        val bodyBytes = mutableMapOf<String, ByteArray>()
        val reads = refs.distinct().mapNotNull { ref ->
            try {
                archive.analysisLease(ref.archiveRecordId).use { lease ->
                    val r = lease.record
                    require(r.executionId == ref.sourceExecutionId && r.sourceName == ref.sourceName && r.paperKey == ref.sourceItemKey)
                    val attempt = repository.attempt(ref.attemptId) ?: error("SOURCE_UNAVAILABLE")
                    require(attempt.recordId == r.id && attempt.status == ArchiveAttemptStatus.SAVED && attempt.blobSha256 == ref.bodySha256)
                    require(ref.attemptId to ref.bodySha256 in lease.fileSet)
                    val bytes = bodyBytes.getOrPut(ref.bodySha256) {
                        val body = lease.bodies.singleOrNull { it.hash == ref.bodySha256 }
                        if (body != null) body.input.readBytes()
                        else archive.withContent(r.id, ref.attemptId) { _, input -> input.readBytes() }
                    }
                    require(DiscoveryPaperArchiveService.sha(bytes) == ref.bodySha256)
                    val metadata = mapper.readTree(r.metadataJson)
                    val external = snapshot.profile.externalIds?.let { mapper.readTree(it) }
                    if (external != null) {
                        require(external.isObject)
                        require(matchesIds(r, PaperArchiveLookup(r.executionId, r.sourceName, r.paperKey,
                            external.path("doi").asText(null), external.path("pmid").asText(null),
                            (external.get("pmcId") ?: external.get("pmcid"))?.asText())))
                    }
                    Read(ref, r, metadata, r.outcomeJson?.let { mapper.readTree(it).path("first") },
                        lease.fileSet.filter { it.second == ref.bodySha256 }.map { it.first }.toSet(), PaperBodyInput(ref.bodySha256, attempt.kind, bytes),
                        url(metadata.path("doi").asText(null)?.let { PaperArchiveLookup.doi(it)?.let { doi -> "https://doi.org/$doi" } })
                            ?: url(attempt.sourceUrl) ?: url(metadata.path("sourceUrl").asText(null))
                            ?: origin(applicationOrigin)?.let { "$it/api/expert-discovery/papers/${r.id}" })
                }
            } catch (_: ArchiveException) {
                results[ref] = PaperSourceEvidence(ref, null, null, emptyList(), listOf("SOURCE_UNAVAILABLE")); null
            } catch (_: IllegalArgumentException) {
                results[ref] = PaperSourceEvidence(ref, null, null, emptyList(), listOf("SOURCE_UNAVAILABLE")); null
            } catch (_: IllegalStateException) {
                results[ref] = PaperSourceEvidence(ref, null, null, emptyList(), listOf("SOURCE_UNAVAILABLE")); null
            } catch (_: java.io.IOException) {
                results[ref] = PaperSourceEvidence(ref, null, null, emptyList(), listOf("SOURCE_UNAVAILABLE")); null
            }
        }
        var parsedBodies = 0
        for ((_, group) in reads.groupBy { it.ref.bodySha256 }) {
            // The same bytes cannot be reinterpreted using conflicting frozen authorship metadata.
            if (group.map { it.metadata.path("authors") to (it.metadata.get("pubYear") ?: it.metadata.get("year")) }.distinct().size != 1) {
                group.forEach { results[it.ref] = PaperSourceEvidence(it.ref, null, null, emptyList(), listOf("SOURCE_METADATA_CONFLICT")) }
                continue
            }
            val parsed = analyzer.parse(group.first().body, group.first().metadata); parsedBodies++
            for (read in group) {
                val analysis = analyzer.assemble(mapOf(read.ref.bodySha256 to parsed), read.first, read.available, evaluatedAt)
                val reasons = mutableListOf<String>()
                val authors = read.metadata.path("authors")
                fun name(n: JsonNode) = listOf(n.path("givenNames").asText(""), n.path("familyNames").asText("")).joinToString(" ").trim()
                fun normalized(n: String) = n.lowercase().trim().replace(Regex("\\s+"), " ")
                val email = DiscoveryIdentity.normalizedEmail(snapshot.profile.email)
                val currentProof = parsed.emails.any {
                    DiscoveryIdentity.normalizedEmail(it.email) == email &&
                        normalized(listOfNotNull(it.givenNames,it.familyNames).joinToString(" ")) == normalized(snapshot.fullName) &&
                        DiscoveryIdentity.validEvidence(it.identityEvidence)
                }
                val binding = analysis.after.singleOrNull { currentProof && it.email == email &&
                    normalized(it.authorName.orEmpty()) == normalized(snapshot.fullName) }
                val emailConflict = analysis.conflicts.any { it.email == email } ||
                    authors.count { DiscoveryIdentity.normalizedEmail(it.path("email").asText(null)) == email && email.isNotEmpty() } > 1 ||
                    (analysis.after.any { it.email == email && it.authorName == null } && authors.size() > 1)
                val proof = snapshot.profile.identityVerification
                val authorId = if (snapshot.identityValid) normalizeOpenAlexAuthorId(proof?.openAlexAuthorId) else null
                val orcid = snapshot.boundOrcid
                val idOwners = (0 until authors.size()).filter { index ->
                    val a = authors[index]
                    (authorId != null && normalizeOpenAlexAuthorId(a.path("openAlexAuthorId").asText(null)) == authorId) ||
                        (orcid != null && normalizeOrcid(a.path("orcidId").asText(null)) == orcid)
                }
                val nameOwners = (0 until authors.size()).filter { normalized(name(authors[it])) == normalized(snapshot.fullName) }
                val owners = when {
                    emailConflict -> { reasons += "AMBIGUOUS_IDENTITY"; emptyList() }
                    idOwners.size > 1 -> { reasons += "AMBIGUOUS_IDENTITY"; emptyList() }
                    idOwners.size == 1 -> idOwners
                    nameOwners.size > 1 -> { reasons += "AMBIGUOUS_IDENTITY"; emptyList() }
                    binding != null && nameOwners.size == 1 -> nameOwners
                    else -> { reasons += "IDENTITY_NOT_BOUND"; emptyList() }
                }
                if (read.sourceUrl == null) reasons += "SOURCE_URL_UNAVAILABLE"
                val evidence = if (owners.size == 1 && read.sourceUrl != null) analysis.qualificationFacts.filter { it.authorIndex == owners.single() }.mapNotNull { fact ->
                    val (field, value) = when (fact.kind) {
                        PaperQualificationKind.COMPLETED_DOCTORATE -> "education" to mapper.writeValueAsString(mapOf("role" to "PhD completed", "completed" to true))
                        PaperQualificationKind.DOCTORAL_STUDENT -> "education" to mapper.writeValueAsString(mapOf("role" to "PhD student", "completed" to false))
                        PaperQualificationKind.ASSISTANT_PROFESSOR, PaperQualificationKind.ASSOCIATE_PROFESSOR, PaperQualificationKind.PROFESSOR -> "employment" to mapper.writeValueAsString(mapOf("role" to fact.originalTerm))
                        PaperQualificationKind.BIRTH_DATE -> {
                            val date = runCatching { LocalDate.parse(fact.originalTerm) }.getOrNull()
                            if (date == null || date.isAfter(evaluatedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate())) return@mapNotNull null
                            "birthDate" to date.toString()
                        }
                        PaperQualificationKind.CONFLICT -> { reasons += "PAPER_FACT_CONFLICT"; return@mapNotNull null }
                    }
                    VerificationEvidence(snapshot.identityHash, snapshot.factFingerprint, "PAPER", read.sourceUrl, evaluatedAt,
                        fact.bodySha256, field, value, AssertionSource.SELF, TemporalScope.HISTORICAL,
                        jsonPointer = "/archive/${read.ref.archiveRecordId}/attempt/${read.ref.attemptId}/page/${fact.page ?: 0}/paragraph/${fact.paragraph}/year/${fact.paperYear ?: 0}",
                        excerpt = fact.quote.take(2048))
                } else emptyList()
                results[read.ref] = PaperSourceEvidence(read.ref, analysis.coverage, binding, evidence, reasons.distinct(), analysis,
                    owners.singleOrNull(), if (owners.isEmpty()) null else if (idOwners.size == 1) "VERIFIED_AUTHOR_ID" else "CURRENT_SOURCE_EMAIL")
            }
        }
        val ordered = refs.distinct().map { results.getValue(it) }
        return PaperEvidenceRead(ordered, ordered.flatMap { it.reasonCodes }.distinct(), parsedBodies)
    }
    private fun url(raw: String?): String? = raw?.takeIf { it.length <= 2048 }?.let {
        runCatching { URI(it).takeIf { u -> u.scheme in setOf("https", "http") && u.host != null && u.userInfo == null }?.toASCIIString() }.getOrNull()
    }
    private fun origin(raw: String?): String? = url(raw)?.let { URI(it) }?.takeIf { it.path.orEmpty() in setOf("", "/") && it.query == null && it.fragment == null }?.toString()?.trimEnd('/')
}
