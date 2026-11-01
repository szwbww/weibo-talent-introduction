package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.CancellationException

/** Only orchestration: RAW, identity repair, qualification, admission and projection retain their original writers. */
@Service
class DiscoveryPaperReprocessApplyService(private val repository: DiscoveryPaperReprocessRepository,
    private val verification: DiscoveryVerificationRepository, private val registration: DiscoveryVerificationRegistrationService,
    private val discovery: ExpertDiscoveryService, private val rechecks: DiscoveryRecheckService,
    private val writer: ExpertIndexWriterService, private val admissions: DiscoveryReviewRepository,
    private val archive: DiscoveryPaperArchiveService, private val analyzer: PaperBodyAnalyzer,
    private val mapper: ObjectMapper, private val jdbc: JdbcTemplate,
    private val worker: ObjectProvider<DiscoveryVerificationWorker>) {
    internal var afterEffect: (String) -> Unit = {}

    internal fun freezeSettings(access: FrozenApolloAccess): JsonNode {
        val runningWorker = worker.getIfAvailable() ?: error("VERIFICATION_UNAVAILABLE")
        check(!runningWorker.isAuthBlocked()) { "VERIFICATION_UNAVAILABLE" }
        return registration.freezePaperSettings(access)
    }

    internal fun cancel(parentId: Long) { verification.cancelPaperReprocessChildren(parentId) }

    private fun targets(id: Long): List<ReprocessItem> {
        val result = mutableListOf<ReprocessItem>()
        var page = 0
        do {
            val rows = repository.items(id, ReprocessKind.TARGET, page++, 100).items
            result.addAll(rows)
        } while (rows.size == 100)
        return result
    }

    /** false leaves a persisted RUNNING/REVIEW wait. No page or process-local flag is authority. */
    internal fun apply(parent: ReprocessRun): Boolean {
        repository.requirePermission(parent)
        val context = registration.beginFromPaperReprocess(parent)
        for (saved in targets(parent.executionId)) {
            val target = repository.claimTarget(parent, saved.id) ?: continue
            val result = target.result?.deepCopy<ObjectNode>() ?: result(target)
            try {
                if (!result.path("applied").asBoolean()) {
                    if (result.path("applicationError").asBoolean()) result.remove(listOf("reasonCode", "reason"))
                    applyTarget(parent, target, result, context)
                    result.put("applicationError", false).put("retryable", false)
                    save(parent, target, result)
                }
                observe(parent, target, result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                repository.item(parent.executionId, target.id)?.result?.let { result.setAll<ObjectNode>(it as ObjectNode) }
                result.put("targetOutcome", "ERROR").put("reasonCode", if (e is ArchiveException) e.code else "APPLY_FAILED")
                    .put("reason", "补录或登记失败，已生效收据保留；请查看原因并重试")
                    .put("retryable", true).put("applicationError", true).put("applied", result.path("applied").asBoolean())
                trace(result, 4, "ERROR")
                save(parent, target, result)
                complete(parent, target, result, ReprocessItemState.ERROR)
            }
        }
        repository.requirePermission(parent)
        registration.closeInput(context)
        // A failed RAW write must not strand an unactivated reservation behind its still-running parent.
        for (target in targets(parent.executionId).filter { it.state == ReprocessItemState.ERROR }) {
            val itemId = target.result?.path("verificationItemId")?.takeIf { it.isIntegralNumber }?.asLong() ?: continue
            val item = verification.findItem(itemId) ?: continue
            if (item.ownerExecutionId == parent.executionId && item.state == VerificationState.RESERVED &&
                writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId) == null)
                verification.failReserved(item.id, "[\"REGISTRATION_RAW_MISSING\"]", Instant.now())
        }
        var waitingSlot = false
        // The roster includes completed members too: a restart cannot shift the stable 100-document groups.
        val roster = targets(parent.executionId).filter { it.result?.path("needsRecheck")?.asBoolean() == true }
            .sortedBy { it.result!!.path("docId").asText() }
        for (group in roster.chunked(100)) {
            repository.requirePermission(parent)
            val docIds = group.map { it.result!!.path("docId").asText() }
            val refs = group.associate { it.result!!.path("docId").asText() to sourceRefs(it.input) }
            val child = try { rechecks.prepareFromPaperReprocess(parent, docIds, refs) }
            catch (e: ResponseStatusException) {
                if (e.reason?.startsWith("ACTIVE_RECHECK_EXISTS") == true || e.reason == "PREPARE_BUSY") {
                    waitingSlot = true
                    continue
                }
                throw e
            }
            // Store every ownership receipt before start. A lost response recovers by the deterministic request key.
            for (saved in group) {
                if (saved.state in setOf(ReprocessItemState.COMPLETE, ReprocessItemState.SKIPPED)) continue
                val target = repository.claimTarget(parent, saved.id) ?: continue
                val result = target.result!!.deepCopy<ObjectNode>().put("childRunId", child.runId)
                    .put("verificationRunId", child.runId)
                save(parent, target, result)
                check(repository.releaseTarget(parent, target))
            }
            afterEffect("AFTER_PREPARE")
            try {
                repository.requirePermission(parent)
                rechecks.startFromPaperReprocess(parent, child.runId)
                afterEffect("AFTER_START")
                repository.requirePermission(parent)
            } catch (e: CancellationException) {
                if (repository.find(parent.executionId)?.cancelRequested == true) cancel(parent.executionId)
                throw e
            }
            verification.reconcileRecheck(child.runId, Instant.now())
            for (saved in group) {
                val target = repository.claimTarget(parent, saved.id) ?: continue
                observe(parent, target, target.result!!.deepCopy<ObjectNode>())
            }
        }
        val owned = verification.paperReprocessRuns(parent.executionId)
        val allTerminal = owned.all { run ->
            val count = verification.recheckCounts(run.executionId)
            if (run.recheckJson == null) run.inputClosed && count.queued == 0L else
                verification.recheck(run).control.phase in setOf(RecheckPhase.COMPLETED, RecheckPhase.CANCELLED,
                    RecheckPhase.FAILED, RecheckPhase.INTERRUPTED) && count.queued == 0L
        }
        val unfinished = targets(parent.executionId).any { it.state in setOf(ReprocessItemState.PENDING, ReprocessItemState.RUNNING) }
        if (waitingSlot || unfinished || !allTerminal) {
            repository.waitForReview(parent, if (waitingSlot) "WAITING_RECHECK_SLOT" else "WAITING_VERIFICATION")
            return false
        }
        return true
    }

    private fun applyTarget(parent: ReprocessRun, target: ReprocessItem, result: ObjectNode,
        context: DiscoveryVerificationExecution) {
        repository.requirePermission(parent)
        val candidates = target.input.path("paperRefs").toList()
        fun name(n: JsonNode) = listOf(n.path("givenNames").asText(""), n.path("familyNames").asText(""))
            .joinToString(" ").trim().lowercase().replace(Regex("\\s+"), " ")
        val names = candidates.filter { !it.path("givenNames").asText().isBlank() && !it.path("familyNames").asText().isBlank() }
            .map(::name).distinct()
        val conflict = names.size > 1 || listOf("orcidId", "openAlexAuthorId").any { key ->
            candidates.mapNotNull { it.path(key).asText(null) }.distinct().size > 1
        }
        val bound = candidates.firstOrNull { !it.path("givenNames").asText().isBlank() &&
            !it.path("familyNames").asText().isBlank() && DiscoveryIdentity.validEvidence(it.path("identityEvidence").asText(null)) }
        if (conflict || bound == null) {
            result.put("targetOutcome", if (conflict) "CONFLICT" else "SKIPPED")
                .put("reasonCode", if (conflict) "SOURCE_IDENTITY_CONFLICT" else "IDENTITY_NOT_BOUND").put("applied", true)
            trace(result, 3, "INSUFFICIENT")
            save(parent, target, result)
            return
        }
        val email = target.input.path("email").asText()
        val hits = writer.findDiscoveryHitsByEmail(email)
        if (hits.map { it.docId }.distinct().size > 1 || hits.groupBy { it.level }.any { it.value.size > 1 }) {
            result.put("targetOutcome", "CONFLICT").put("reasonCode", "EMAIL_MULTIPLE_DOCUMENTS").put("applied", true)
            save(parent, target, result)
            return
        }
        val docId = hits.firstOrNull()?.docId
        val raw = docId?.let { writer.readDiscoveryDocument(ExpertIndexLevel.RAW, it) }
        if (!result.has("before")) {
            result.put("beforeAbsent", hits.isEmpty()).put("proposalHash", DiscoveryVerificationRepository.replayHash(target.input.toString()))
            result.put("proposedDocId", com.weibo.talentintroduction.expert.service.ExpertIdGenerator.generate(null, email))
            result.set<JsonNode>("before", raw?.let { mapper.valueToTree<JsonNode>(it) } ?: mapper.nullNode())
            result.put("beforeAdmissionRevision", docId?.let { admissions.findAdmission(it)?.revision })
            result.set<JsonNode>("paperRefs", target.input.path("paperRefs"))
            save(parent, target, result)
        }
        val owned = docId?.let { id -> verification.findItemsByDocIds(listOf(id)).singleOrNull {
            it.ownerExecutionId == parent.executionId && it.reviewEpoch == 0L
        } }
        if (hits.isEmpty() || result.path("beforeAbsent").asBoolean() && owned != null) {
            val ref = sourceRefs(target.input).first { it.archiveRecordId == bound.path("recordId").asLong() &&
                it.bodySha256 == bound.path("bodyHash").asText() }
            val parsed = ParsedArchivedPaper.archiveLease(archive, analyzer, repository, parent, target, ref)
            result.set<JsonNode>("qualificationCoverage", mapper.valueToTree(parsed.analysis.qualification.coverage))
            save(parent, target, result)
            val author = parsed.emails.singleOrNull { DiscoveryIdentity.normalizedEmail(it.email) == email }
                ?: run { result.put("targetOutcome", "SKIPPED").put("reasonCode", "SOURCE_IDENTITY_NOT_UNIQUE").put("applied", true); save(parent, target, result); return }
            val paper = mapper.treeToValue(repository.paper(parent.executionId, ref.archiveRecordId)!!.input.path("metadata"), PaperMetadata::class.java)
            val outcome = discovery.consumeArchivedPaper(paper, listOf(author), ref.sourceName, ref.sourceItemKey,
                context.copy(archivedPaper = ArchivedPaperExecution(ref, Instant.parse(parent.scope.path("evaluationAt").asText()), parsed, parent, target)))
            repository.item(parent.executionId, target.id)?.result?.let { result.setAll<ObjectNode>(it as ObjectNode) }
            result.set<JsonNode>("consumeOutcome", mapper.valueToTree(outcome))
            val after = writer.findDiscoveryHitsByEmail(email)
            val ids = after.map { it.docId }.distinct()
            val registered = if (ids.size == 1) verification.findItemsByDocIds(ids).singleOrNull {
                it.ownerExecutionId == parent.executionId && it.reviewEpoch == 0L && it.sourceName == ref.sourceName && it.sourceItemKey == ref.sourceItemKey
            } else null
            if (registered != null) {
                val document = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, registered.expertDocId) ?: error("RAW_RECEIPT_MISSING")
                val profile = writer.discoveryProfile(registered.expertDocId, document.source)
                require(DiscoveryIdentity.normalizedEmail(profile.email) == email && profile.givenNames == author.givenNames &&
                    profile.familyNames == author.familyNames && DiscoveryIdentity.allowed(profile)) { "RAW_RECEIPT_IDENTITY_CONFLICT" }
                result.put("docId", registered.expertDocId).put("verificationItemId", registered.id)
                    .put("verificationRunId", parent.executionId).put("targetOutcome", "NEW").put("actualNew", 1)
                result.set<JsonNode>("after", mapper.valueToTree(document))
            }
            save(parent, target, result)
            afterEffect("AFTER_CONSUME")
            if (outcome.rawWriteFailed || outcome.enqueueFailed || outcome.dedupFailed || outcome.identityRepairFailed || outcome.registrationFailed)
                error("CONSUME_WRITE_FAILED")
            if (registered == null) {
                result.put("targetOutcome", if (after.isNotEmpty()) "CONFLICT" else "SKIPPED")
                    .put("reasonCode", if (after.isNotEmpty()) "FOREIGN_DOCUMENT" else "NO_ELIGIBLE_EMAIL")
            }
            result.put("applied", true)
            trace(result, 4, "SUCCESS")
            save(parent, target, result)
            return
        }
        if (result.path("beforeAbsent").asBoolean()) {
            result.put("targetOutcome", "CONFLICT").put("reasonCode", "FOREIGN_DOCUMENT").put("applied", true)
            save(parent, target, result)
            return
        }
        requireNotNull(docId)
        result.put("docId", docId)
        val profile = raw?.let { writer.discoveryProfile(docId, it.source) }
        val contact = jdbc.queryForObject("""SELECT (EXISTS(SELECT 1 FROM expert_contact WHERE LOWER(TRIM(expert_email))=? OR orcid_id=?)
            OR EXISTS(SELECT 1 FROM expert_email_alias WHERE normalized_email=?))""", Boolean::class.java, email, docId, email) == true
        val admission = admissions.findAdmission(docId)
        val protected = contact || hits.any { it.level == ExpertIndexLevel.APPLICATION } || admission?.decisionEnum?.manual == true ||
            admission?.decision == "LEGACY_APPROVED" || profile?.dataSource.orEmpty().contains("SBIR", true) ||
            profile?.emailSource.orEmpty().contains("SBIR", true)
        val mismatched = hits.any { hit ->
            listOf("givenNames", "familyNames").any { key -> val current = hit.source[key]?.toString();
                !current.isNullOrBlank() && !current.equals(bound.path(key).asText(), true) }
        }
        if (protected || mismatched || raw == null || profile == null || !DiscoveryIdentity.isDiscoveryMap(raw.source)) {
            result.put("targetOutcome", if (protected) "PROTECTED" else "CONFLICT")
                .put("reasonCode", if (protected) "BUSINESS_PROTECTED" else "EXISTING_IDENTITY_CONFLICT").put("applied", true)
            save(parent, target, result)
            return
        }
        result.put("needsRecheck", true).put("repairExpected", profile.givenNames.isNullOrBlank() ||
            profile.familyNames.isNullOrBlank() || !DiscoveryIdentity.allowed(profile))
            .put("targetOutcome", "UNCHANGED").put("applied", true)
        result.put("alreadyAdmitted", if (admission?.decision == "AUTO_PASSED") 1 else 0)
        trace(result, 4, "SUCCESS")
        save(parent, target, result)
    }

    private fun observe(parent: ReprocessRun, target: ReprocessItem, result: ObjectNode) {
        val childId = result.path("childRunId").takeIf { it.isIntegralNumber }?.asLong()
        val ordinaryId = result.path("verificationItemId").takeIf { it.isIntegralNumber }?.asLong()
        val item = ordinaryId?.let(verification::findItem) ?: childId?.let { id ->
            verification.recheckItems(id, 0, 100).second.singleOrNull { it.expertDocId == result.path("docId").asText() }
        }
        if (item != null) {
            require(verification.paperReprocessRuns(parent.executionId).any { it.executionId == item.ownerExecutionId }) { "FOREIGN_VERIFICATION" }
            result.put("verificationItemId", item.id).put("verificationRunId", item.ownerExecutionId)
                .put("verificationState", item.state.name).put("admissionRevision", item.admissionRevision)
                .put("projection", item.projectionState.name).put("projectionError", item.projectionError)
                .put("actualAdmitted", if (item.admissionRevision != null && result.path("alreadyAdmitted").asInt() == 0) 1 else 0)
                .put("syncFailure", if (item.admissionRevision != null && item.projectionState == VerificationProjection.ERROR) 1 else 0)
            val supplement = RetainedPaperIdentityService.supplement(item)
            if (supplement?.phase == "APPLIED" && supplement.repairReceipt?.reason == "REPAIRED")
                result.put("targetOutcome", "REPAIRED").put("actualUpdated", 1)
            if (supplement?.phase == "CONFLICT") result.put("targetOutcome", "CONFLICT")
            if (supplement?.phase == "PROTECTED") result.put("targetOutcome", "PROTECTED")
            val current = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, item.expertDocId)
            current?.let { result.set<JsonNode>("after", mapper.valueToTree(it)) }
            trace(result, 5, if (item.state in TERMINAL) if (item.state == VerificationState.ERROR) "ERROR" else "SUCCESS" else "RUNNING")
            result.put("reason", if (result.path("syncFailure").asInt() == 1) "已准入，候选同步失败；沿用核验同步重试，不重新资格外呼" else "补录与核验结果分别计数")
            save(parent, target, result)
            afterEffect("AFTER_ADMISSION_OBSERVED")
        }
        val awaiting = item?.let { it.state !in TERMINAL || it.projectionState == VerificationProjection.PENDING }
            ?: (result.path("needsRecheck").asBoolean() || result.path("targetOutcome").asText() == "NEW")
        if (awaiting) {
            save(parent, target, result)
            check(repository.releaseTarget(parent, target))
        } else {
            trace(result, 6, "SUCCESS")
            if (item?.state == VerificationState.ERROR || result.path("applicationError").asBoolean())
                result.put("targetOutcome", "ERROR")
            complete(parent, target, result, if (item?.state == VerificationState.ERROR || result.path("targetOutcome").asText() == "ERROR")
                ReprocessItemState.ERROR else ReprocessItemState.COMPLETE)
        }
    }

    private fun sourceRefs(input: JsonNode): List<PaperSourceRef> = input.path("paperRefs").filter { ref ->
        !ref.path("givenNames").asText().isBlank() && !ref.path("familyNames").asText().isBlank() &&
            DiscoveryIdentity.validEvidence(ref.path("identityEvidence").asText(null))
    }.flatMap { ref ->
        ref.path("attemptIds").map { attempt -> PaperSourceRef(ref.path("recordId").asLong(), attempt.asLong(),
            ref.path("bodyHash").asText(), ref.path("sourceExecutionId").asLong(), ref.path("sourceName").asText(), ref.path("sourceItemKey").asText()) }
    }.distinct().sortedWith(compareBy({ it.archiveRecordId }, { it.attemptId }))

    private fun result(target: ReprocessItem): ObjectNode = mapper.createObjectNode().put("schemaVersion", 1)
        .put("actualNew", 0).put("actualUpdated", 0).put("actualAdmitted", 0).put("alreadyAdmitted", 0).put("syncFailure", 0).also { result ->
            result.set<JsonNode>("paperRefs", target.input.path("paperRefs"))
            val trace = result.putArray("trace")
            (1..6).forEach { trace.addObject().put("stepNo", it).put("state", if (it <= 3) "SUCCESS" else "NOT_STARTED") }
        }
    private fun trace(result: ObjectNode, step: Int, state: String) { (result.path("trace")[step - 1] as ObjectNode).put("state", state) }
    private fun save(parent: ReprocessRun, target: ReprocessItem, result: ObjectNode) {
        check(repository.receipt(parent, target, result)) { "TARGET_LEASE_LOST" }
    }
    private fun complete(parent: ReprocessRun, target: ReprocessItem, result: ObjectNode, state: ReprocessItemState) {
        repository.requirePermission(parent)
        check(repository.completeItem(parent.executionId, parent.leaseToken!!, parent.generation, target.id,
            target.leaseToken!!, target.generation, state, result)) { "TARGET_LEASE_LOST" }
    }
    companion object { private val TERMINAL = setOf(VerificationState.COMPLETED, VerificationState.ERROR, VerificationState.CANCELLED) }
}
