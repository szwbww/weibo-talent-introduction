package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.campaign.service.ExpertEmailAliasService
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.*
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.stereotype.Service
import java.time.Instant

/** Persisted server proposal. The original root occurs exactly once, including its original caches. */
data class RetainedIdentityProposal(val incoming: AuthorEmail?, val sourceRefs: List<PaperSourceRef>,
    val reason: String, val phase: String, val sourceEvidence: List<JsonNode> = emptyList())
data class RetainedIdentityReceipt(val phase: String, val reason: String,
    val layers: List<IdentityRepairLayer> = emptyList(), val effective: VerificationSnapshot? = null)
data class IdentitySupplement(val itemId: Long, val reviewEpoch: Long, val generation: Long, val ownerHash: String,
    val beforeSnapshot: JsonNode, val beforeIdentityHash: String, val beforeFactFingerprint: String,
    val beforeAdmissionRevision: Long?, val beforeAdmissionIdentity: String?, val proposal: RetainedIdentityProposal,
    val proposalHash: String, val mode: VerificationMode, val phase: String,
    val effectiveProfile: ExpertProfile? = null, val effectiveHash: String? = null,
    val repairReceipt: RetainedIdentityReceipt? = null)

/** No provider clients: proposals only inspect retained, hash-verified original bytes. */
@Service
class RetainedPaperIdentityService(private val archive: DiscoveryPaperArchiveService,
    private val archiveRepository: DiscoveryPaperArchiveRepository, private val analyzer: PaperBodyAnalyzer,
    private val papers: PaperVerificationEvidenceService, private val writer: ExpertIndexWriterService,
    private val repair: DiscoveryIdentityRepairService, private val contacts: ExpertContactRepository,
    private val aliases: ExpertEmailAliasService, private val admissions: DiscoveryReviewRepository,
    private val policy: DiscoveryAdmissionPolicy) {
    private val mapper = codec
    private val levels = listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)

    fun propose(docId: String, snapshot: VerificationSnapshot, frozen: List<PaperSourceRef>, at: Instant,
        sourceExecutionId: Long? = null, sourceName: String? = null, sourceItemKey: String? = null): RetainedIdentityProposal {
        fun result(phase: String, reason: String, refs: List<PaperSourceRef> = frozen) = RetainedIdentityProposal(null, refs, reason, phase)
        val protection = inspect(docId, null)
        if (protection != null) return result(protection.first, protection.second)
        if (snapshot.identityValid && !snapshot.profile.givenNames.isNullOrBlank() && !snapshot.profile.familyNames.isNullOrBlank())
            return result("UNCHANGED", "IDENTITY_ALREADY_COMPLETE")
        val refs = try {
            if (frozen.isNotEmpty()) frozen else {
                val ids = snapshot.profile.externalIds?.let(mapper::readTree)
                val doi = ids?.path("doi")?.asText(null)
                val pmid = ids?.path("pmid")?.asText(null)
                val pmcid = (ids?.get("pmcId") ?: ids?.get("pmcid"))?.asText()
                if (listOf(doi, pmid, pmcid).all { it.isNullOrBlank() }) emptyList() else {
                    val lookup = PaperArchiveLookup(requireNotNull(sourceExecutionId), requireNotNull(sourceName),
                        requireNotNull(sourceItemKey), doi, pmid, pmcid)
                    papers.locate(listOf(lookup))[lookup].orEmpty()
                }
            }
        } catch (_: Exception) { return result("UNAVAILABLE", "SOURCE_LOOKUP_FAILED") }
        if (refs.isEmpty()) return result("UNAVAILABLE", "SOURCE_UNAVAILABLE", refs)
        val email = DiscoveryIdentity.normalizedEmail(snapshot.profile.email)
        val matches = mutableListOf<AuthorEmail>()
        val evidence = mutableListOf<JsonNode>()
        try {
            require(refs.size <= 100)
            for (ref in refs.distinct()) archive.analysisLease(ref.archiveRecordId).use { lease ->
                val record = lease.record
                require(record.executionId == ref.sourceExecutionId && record.sourceName == ref.sourceName && record.paperKey == ref.sourceItemKey)
                val attempt = requireNotNull(archiveRepository.attempt(ref.attemptId))
                require(attempt.recordId == record.id && attempt.status == ArchiveAttemptStatus.SAVED && attempt.blobSha256 == ref.bodySha256)
                require(ref.attemptId to ref.bodySha256 in lease.fileSet)
                val metadata = mapper.readTree(record.metadataJson)
                val ids = snapshot.profile.externalIds?.let(mapper::readTree)
                if (ids != null) {
                    require(PaperArchiveLookup.doi(ids.path("doi").asText(null))?.let { it == PaperArchiveLookup.doi(metadata.path("doi").asText(null)) } != false)
                    require(PaperArchiveLookup.pmid(ids.path("pmid").asText(null))?.let { it == PaperArchiveLookup.pmid(metadata.path("pmid").asText(null)) } != false)
                    require(PaperArchiveLookup.pmcid((ids.get("pmcId") ?: ids.get("pmcid"))?.asText())?.let { it == PaperArchiveLookup.pmcid(metadata.path("pmcId").asText(null)) } != false)
                }
                val bytes = lease.bodies.singleOrNull { it.hash == ref.bodySha256 }?.input?.readBytes()
                    ?: archive.withContent(record.id, ref.attemptId) { _, input -> input.readBytes() }
                require(DiscoveryPaperArchiveService.sha(bytes) == ref.bodySha256)
                val parsed = analyzer.parse(PaperBodyInput(ref.bodySha256, attempt.kind, bytes), metadata)
                val owners = parsed.emails.filter { DiscoveryIdentity.normalizedEmail(it.email) == email }
                if (owners.isEmpty()) return@use
                if (owners.any { it.givenNames.isNullOrBlank() || it.familyNames.isNullOrBlank() || !DiscoveryIdentity.validEvidence(it.identityEvidence) })
                    return result("CONFLICT", "AMBIGUOUS_SOURCE_OWNER", refs)
                matches += owners
                val sentences = if (parsed.format == "PDF") org.apache.pdfbox.pdmodel.PDDocument.load(bytes).use { document ->
                    val authors = metadata.path("authors").map { mapper.treeToValue(it, PaperAuthor::class.java) }
                    PdfAuthorContactLayout.collect(document, document.numberOfPages, authors)
                        .filter { DiscoveryIdentity.normalizedEmail(it.email) == email }
                } else emptyList()
                evidence.add(mapper.valueToTree<JsonNode>(mapOf("source" to ref, "authors" to owners,
                    "parserVersions" to analyzer.versions(), "qualificationFacts" to parsed.qualification.facts,
                    "sentences" to sentences, "evaluatedAt" to at)))
            }
        } catch (_: Exception) { return result("UNAVAILABLE", "SOURCE_VALIDATION_FAILED", refs) }
        if (matches.isEmpty()) return result("UNAVAILABLE", "SOURCE_NO_TARGET_EMAIL", refs)
        if (matches.map { listOf(it.givenNames, it.familyNames, it.orcidId, it.openAlexAuthorId) }.distinct().size != 1)
            return result("CONFLICT", "SOURCE_OWNER_CONFLICT", refs)
        val incoming = matches.first()
        val blocked = inspect(docId, incoming)
        if (blocked != null) return result(blocked.first, blocked.second, refs)
        val effective = virtual(docId, snapshot, incoming)
        return RetainedIdentityProposal(incoming, refs, "RETAINED_SOURCE_EMAIL", if (effective.identityHash == snapshot.identityHash &&
            effective.factFingerprint == snapshot.factFingerprint) "UNCHANGED" else "PROPOSED", evidence)
    }

    fun virtual(docId: String, before: VerificationSnapshot, incoming: AuthorEmail): VerificationSnapshot {
        val profile = before.profile.copy(givenNames = incoming.givenNames, familyNames = incoming.familyNames,
            identityVerification = DiscoveryIdentity.verified(incoming.email, incoming.givenNames, incoming.familyNames,
                requireNotNull(incoming.identityEvidence), incoming.orcidId, incoming.openAlexAuthorId))
        return DiscoveryVerificationAdmissionGate.snapshot(docId, profile, policy.evaluate(docId, profile).configSnapshot)
    }

    /** Protection is reread immediately before the existing per-layer ES CAS. Never registers people or admissions. */
    fun apply(docId: String, proposal: RetainedIdentityProposal): RetainedIdentityReceipt {
        val incoming = proposal.incoming ?: return RetainedIdentityReceipt(proposal.phase, proposal.reason)
        val blocked = inspect(docId, incoming)
        if (blocked != null) return RetainedIdentityReceipt(blocked.first, blocked.second)
        val repaired = repair.repairEmptyNameProof(docId, incoming)
        if (repaired.outcome !in setOf(IdentityRepairOutcome.REPAIRED, IdentityRepairOutcome.ALREADY_VERIFIED))
            return RetainedIdentityReceipt(if (repaired.outcome == IdentityRepairOutcome.CONFLICT) "CONFLICT" else "PARTIAL", repaired.outcome.name, repaired.layers)
        val afterProtection = inspect(docId, incoming)
        if (afterProtection != null) return RetainedIdentityReceipt(afterProtection.first, afterProtection.second, repaired.layers)
        val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, docId) ?: return RetainedIdentityReceipt("PARTIAL", "RAW_MISSING", repaired.layers)
        val profile = writer.discoveryProfile(docId, raw.source)
        val effective = DiscoveryVerificationAdmissionGate.snapshot(docId, profile, policy.evaluate(docId, profile).configSnapshot)
        return RetainedIdentityReceipt("APPLIED", repaired.outcome.name, repaired.layers, effective)
    }

    private fun inspect(docId: String, incoming: AuthorEmail?): Pair<String, String>? = try {
        val layers = levels.associateWith { writer.readDiscoveryDocument(it, docId) }
        val raw = layers[ExpertIndexLevel.RAW]?.source
        when {
            raw == null -> "UNAVAILABLE" to "RAW_MISSING"
            !DiscoveryPersonScope.isLiveDiscovered(raw) -> "PROTECTED" to "NOT_DISCOVERY"
            layers[ExpertIndexLevel.APPLICATION] != null -> "PROTECTED" to "APPLICATION_PRESENT"
            else -> {
                val sources = layers.values.mapNotNull { it?.source }
                val email = DiscoveryIdentity.normalizedEmail(raw["email"] as? String)
                val orcids = sources.mapNotNull { source -> external(source).path("orcid").asText(null) }
                val admission = admissions.findAdmission(docId)
                when {
                    admission != null && (admission.decisionEnum.manual || admission.decision == "LEGACY_APPROVED" ||
                        admission.decision == "AUTO_PASSED" && admission.policyVersion != DiscoveryVerificationAdmissionGate.POLICY_VERSION) -> "PROTECTED" to "REVIEW_PROTECTED"
                    (listOf(docId) + orcids).any(contacts::existsByOrcidId) || contacts.findFirstByExpertEmailOrderByUpdatedAtDesc(email) != null ||
                        aliases.findContactByEmailOrAlias(email) != null -> "PROTECTED" to "CONTACT_PRESENT"
                    writer.findDiscoveryHitsByEmail(email).map { it.docId }.distinct() != listOf(docId) -> "CONFLICT" to "EMAIL_NOT_UNIQUE"
                    sources.any { source -> listOf("hIndex", "citationCount", "worksCount", "researchFields", "researchFieldIds", "recentWorkTitles", "patentTitles", "disciplineCategory", "enrichedAt").any {
                        when (val value = source[it]) { null -> false; is Collection<*> -> value.isNotEmpty(); is String -> value.isNotBlank(); else -> true }
                    } } -> "PROTECTED" to "LEGACY_ACADEMIC_FIELDS"
                    listOf("orcid", "openAlexAuthorId").any { key -> (sources.flatMap { source ->
                        val proof = source["identityVerification"]?.let { mapper.convertValue(it, IdentityVerification::class.java) }
                        listOfNotNull(external(source).path(key).asText(null), if (key == "orcid") proof?.orcid else proof?.openAlexAuthorId)
                    }.filter(String::isNotBlank).map { it.lowercase() } +
                        listOfNotNull(if (key == "orcid") incoming?.orcidId?.lowercase() else incoming?.openAlexAuthorId?.lowercase())).distinct().size > 1 } -> "CONFLICT" to "AUTHOR_ID_CONFLICT"
                    listOf("doi", "pmid", "pmcId").any { key -> sources.mapNotNull { source ->
                        val ids = external(source)
                        val value = (ids.get(key) ?: if (key == "pmcId") ids.get("pmcid") else null)?.asText()
                        when (key) { "doi" -> PaperArchiveLookup.doi(value); "pmid" -> PaperArchiveLookup.pmid(value); else -> PaperArchiveLookup.pmcid(value) }
                    }.distinct().size > 1 } -> "CONFLICT" to "SOURCE_IDENTITY_CONFLICT"
                    incoming != null && sources.any { source ->
                        DiscoveryIdentity.normalizedEmail(source["email"] as? String) != email ||
                            listOf("givenNames" to incoming.givenNames, "familyNames" to incoming.familyNames).any { (key, value) ->
                                (source[key] as? String)?.takeIf(String::isNotBlank)?.let { it != value } == true } ||
                            source["identityVerification"]?.let { mapper.convertValue(it, IdentityVerification::class.java) }?.let {
                                it != DiscoveryIdentity.verified(incoming.email, incoming.givenNames, incoming.familyNames,
                                    incoming.identityEvidence!!, incoming.orcidId, incoming.openAlexAuthorId) } == true
                    } -> "CONFLICT" to "NONEMPTY_IDENTITY_CONFLICT"
                    else -> null
                }
            }
        }
    } catch (_: Exception) { "UNAVAILABLE" to "PROTECTION_READ_FAILED" }

    private fun external(source: Map<String, Any?>): JsonNode = source["externalIds"]?.let {
        if (it is String) mapper.readTree(it) else mapper.valueToTree(it)
    } ?: mapper.createObjectNode()

    companion object {
        internal val codec = jacksonObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        fun hash(value: Any): String {
            fun canonical(node: JsonNode): JsonNode = when {
                node.isObject -> codec.createObjectNode().also { target -> node.fieldNames().asSequence().sorted().forEach { target.set<JsonNode>(it, canonical(node[it])) } }
                node.isArray -> codec.createArrayNode().also { target -> node.forEach { target.add(canonical(it)) } }
                node.isNumber -> com.fasterxml.jackson.databind.node.DecimalNode.valueOf(node.decimalValue().stripTrailingZeros())
                else -> node
            }
            return DiscoveryVerificationRepository.replayHash(canonical(codec.valueToTree(value)).toString())
        }
        fun supplement(item: VerificationItem): IdentitySupplement? = codec.readTree(item.snapshotJson).get("identitySupplement")?.let {
            codec.treeToValue(it, IdentitySupplement::class.java)
        }
        /** Used at both SQL rebind and commit boundaries. A completed receipt never authorizes another epoch. */
        fun valid(item: VerificationItem, s: IdentitySupplement): Boolean = item.reviewEpoch > 0 && s.itemId == item.id &&
            s.reviewEpoch == item.reviewEpoch && item.ownerExecutionId == s.reviewEpoch && s.generation == item.generation &&
            s.proposalHash == hash(s.proposal) && !s.beforeSnapshot.has("identitySupplement") &&
            s.beforeSnapshot.path("identityHash").asText() == s.beforeIdentityHash &&
            s.beforeSnapshot.path("factFingerprint").asText() == s.beforeFactFingerprint &&
            s.mode.policyVersion == item.policyVersion && s.proposal.incoming != null &&
            s.proposal.sourceRefs.isNotEmpty() && s.proposal.sourceEvidence.isNotEmpty() &&
            s.effectiveHash == item.identityHash && s.effectiveProfile != null &&
            codec.readTree(item.snapshotJson).path("profile") == codec.valueToTree<JsonNode>(s.effectiveProfile) &&
            (s.mode == VerificationMode.AUDIT && s.phase == "PROPOSED" || s.mode == VerificationMode.ENFORCE &&
                s.phase == "APPLIED" && s.repairReceipt?.phase == "APPLIED" && s.repairReceipt.effective?.identityHash == item.identityHash &&
                s.repairReceipt.effective.factFingerprint == item.factFingerprint &&
                s.repairReceipt.reason in setOf("REPAIRED", "ALREADY_VERIFIED") &&
                s.repairReceipt.layers.map { it.level }.toSet() == ExpertIndexLevel.values().toSet() &&
                s.repairReceipt.layers.all { it.outcome in setOf(IdentityRepairLayerOutcome.REPAIRED,
                    IdentityRepairLayerOutcome.ALREADY_VERIFIED, IdentityRepairLayerOutcome.MISSING) })
    }
}
