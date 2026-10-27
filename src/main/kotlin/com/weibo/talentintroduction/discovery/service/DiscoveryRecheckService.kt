package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.archive.PaperArchiveLookup
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

// This wire contract is deliberately separate from the side-effectful manual-review prepare request.
enum class RecheckScope { IDS, ALL_MATCHING, ALL_PENDING }
data class RecheckFilter(val q: String? = null, val issue: String? = null, val decision: String? = null)
data class RecheckPrepareRequest(val requestKey: String, val scope: RecheckScope = RecheckScope.IDS,
    val docIds: List<String> = emptyList(), val filter: RecheckFilter = RecheckFilter(),
    val mode: VerificationMode = VerificationMode.AUDIT, val evidenceMode: RecheckEvidenceMode = RecheckEvidenceMode.REUSE)
data class RecheckStartRequest(val rosterHash: String, val revision: Long)
data class RecheckSyncRequest(val itemIds: List<Long>)

/** Only coordinates frozen input and run authority. All qualification/application uses the original worker. */
@Service
class DiscoveryRecheckService(
    private val repository: DiscoveryVerificationRepository,
    private val admissions: DiscoveryReviewRepository,
    private val writer: ExpertIndexWriterService,
    private val scan: DiscoveryReviewScanService,
    private val policy: DiscoveryAdmissionPolicy,
    private val apolloSettings: DiscoveryApolloSettingsService,
    private val properties: DiscoveryVerificationProperties,
    private val tasks: TaskExecutionRepository,
    private val transactions: TransactionTemplate,
    private val queries: DiscoveryVerificationQueryService,
    private val commits: DiscoveryVerificationCommitService,
    @Qualifier("discoveryRecheckPrepareExecutor") private val executor: Executor,
    private val worker: ObjectProvider<DiscoveryVerificationWorker>,
    private val papers: PaperVerificationEvidenceService,
    private val clock: Clock = Clock.systemUTC(),
    private val progressStore: com.weibo.talentintroduction.task.service.TaskProgressStore? = null
) {
    private val mapper = jacksonObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val owner = UUID.randomUUID().toString()
    private val preparingThreads = ConcurrentHashMap<Long, Thread>()

    fun prepare(submitted: RecheckPrepareRequest, actor: String): RecheckRunView {
        val request = normalizedRequest(submitted)
        val canonical = canonicalSpec(request, actor)
        var taskId: Long? = null
        val registration = try {
            repository.registerRecheck(request.requestKey, canonical) {
                // All configuration reads are local SQL; ES/provider work is outside this transaction and lock.
                val access = try { apolloSettings.freeze() } catch (_: DiscoveryApolloSettingsUnavailable) {
                    conflict("APOLLO_SETTINGS_UNAVAILABLE")
                }
                val now = clock.instant()
                val task = tasks.save(TaskExecution(taskType = TASK_TYPE, triggerType = "MANUAL", status = "RUNNING",
                    requestPayload = canonical, resultSummary = null, startedAt = LocalDateTime.now(),
                    createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now(), ownerToken = owner,
                    heartbeatAt = LocalDateTime.now()))
                taskId = requireNotNull(task.id)
                val settings = mapper.writeValueAsString(linkedMapOf(
                    "mode" to request.mode, "policyVersion" to request.mode.policyVersion, "ruleVersion" to 4,
                    "apolloMatchingVersion" to 2, "institutionAliasVersion" to DiscoveryInstitutionNormalizer.ALIAS_VERSION,
                    "apolloLimitPolicy" to "PROVIDER", "apolloAccess" to access.toJson(mapper),
                    "maxRequestsPerItem" to properties.maxRequestsPerItem, "maxPagesPerQuery" to properties.maxPagesPerQuery,
                    "maxTransientAttempts" to properties.maxTransientAttempts, "leaseSeconds" to properties.leaseSeconds,
                    "httpConnectTimeout" to properties.httpConnectTimeout.toString(), "httpReadTimeout" to properties.httpReadTimeout.toString()))
                val specHash = DiscoveryVerificationRepository.replayHash(canonical)
                val scope = mapper.readTree(canonical) as com.fasterxml.jackson.databind.node.ObjectNode
                scope.put("specHash", specHash)
                repository.createRecheckRun(taskId!!, request.mode, access.searchEnabled, settings,
                    RecheckSpec(actor, request.requestKey, scope.toString(), request.evidenceMode, now,
                        CODE_VERSION, "layout-${PdfAuthorContactLayout.RULE_VERSION}"), now)
            }
        } catch (ex: Exception) {
            // Normally task+run both roll back. If a task was persisted by a caller's repository, never strand it.
            taskId?.let { finishTask(it, "FAILED", 0, "RUN_REGISTRATION_FAILED") }
            throw ex
        }
        val run = registration.first
        val frozen = mapper.readTree(repository.recheck(run).spec.scope).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        frozen.remove("specHash")
        if (frozen != mapper.readTree(canonical)) conflict("SPEC_CONFLICT")
        if (registration.second) {
            try { executor.execute {
                preparingThreads[run.executionId] = Thread.currentThread()
                try { freeze(run, request) }
                finally { preparingThreads.remove(run.executionId) }
            } }
            catch (_: RejectedExecutionException) {
                failPreparation(run.executionId, "PREPARE_BUSY")
                conflict("PREPARE_BUSY")
            }
        }
        return queries.recheckRun(run.executionId)
    }

    internal fun canonicalSpec(submitted: RecheckPrepareRequest, actor: String): String {
        val request = normalizedRequest(submitted)
        require(runCatching { UUID.fromString(request.requestKey).toString() == request.requestKey }.getOrDefault(false)) {
            "requestKey must be a canonical UUID"
        }
        verificationText(actor, 128)
        if (request.scope == RecheckScope.IDS) {
            require(request.docIds.size in 1..100) { "IDS requires 1..100 document IDs" }
            request.docIds.forEach { verificationText(it, 128); require(it == it.trim() && !it.contains('/')) { "Invalid docId" } }
            require(request.filter == RecheckFilter()) { "IDS does not accept a filter" }
        } else require(request.docIds.isEmpty()) { "ALL_MATCHING does not accept docIds" }
        require(request.filter.q == null || request.filter.q.length <= 100)
        require(request.filter.issue == null || request.filter.issue.matches(Regex("[A-Z_0-9]{1,100}")))
        require(request.filter.decision == null || request.filter.decision in setOf("ALL", "UNINITIALIZED", "AUTO_PASSED",
            "NEEDS_REVIEW", "MANUAL_APPROVED", "LEGACY_APPROVED", "HOLD", "REJECTED")) { "Invalid decision" }
        return mapper.writeValueAsString(linkedMapOf("actor" to actor, "scope" to request.scope,
            "docIds" to request.docIds.distinct().sorted(), "filter" to RecheckFilter(
                request.filter.q?.trim()?.takeIf { it.isNotEmpty() }, request.filter.issue?.takeUnless { it == "ALL" },
                request.filter.decision?.takeUnless { it == "ALL" }), "mode" to request.mode, "evidenceMode" to request.evidenceMode))
    }

    private fun normalizedRequest(request: RecheckPrepareRequest): RecheckPrepareRequest =
        if (request.scope == RecheckScope.ALL_PENDING) {
            require(request.docIds.isEmpty())
            request.copy(filter = RecheckFilter(decision = "NEEDS_REVIEW"))
        } else request

    fun active(actor: String): List<RecheckRunView> = repository.activeRechecks(actor).map { queries.recheckRun(it.executionId) }

    private data class Input(val docId: String, val profile: ExpertProfile,
        val admissionRevision: Long? = null, val beforeDecision: String? = null)

    private fun freeze(run: VerificationRun, request: RecheckPrepareRequest) {
        val seen = HashSet<String>()
        fun progress() {
            val current = repository.findRun(run.executionId) ?: return
            val control = repository.recheck(current).control
            val prepared = repository.recheckCounts(run.executionId).total
            val message = if (control.preparationStage == "SCANNING") "正在保存名单：已保存 ${control.scanned} 人"
                else "正在关联论文证据：$prepared / ${control.scanned} 人"
            progressStore?.update(TASK_TYPE, com.weibo.talentintroduction.task.service.TaskProgress(TASK_TYPE, "RUNNING", 0,
                if (control.preparationStage == "SCANNING") control.scanned else prepared,
                if (control.preparationStage == "SCANNING") 0 else control.scanned, message), run.executionId)
        }
        try {
            progressStore?.tryStart(TASK_TYPE, com.weibo.talentintroduction.task.service.TaskProgress(TASK_TYPE, "RUNNING", 0, 0, 0,
                "正在保存名单", executionId = run.executionId))
            val admissionConfig = policy.currentConfigSnapshot()
            fun batch(inputs: List<Input>): Boolean {
                ensurePreparing(run.executionId)
                val unique = inputs.filter { seen.add(it.docId) }
                val history = repository.findHistoryByDocIds(unique.map { it.docId }).groupBy { it.expertDocId }
                // Do all potentially slow reads before acquiring any run/item locks.
                fun lookup(input: Input): PaperArchiveLookup? {
                    val snapshot = DiscoveryVerificationAdmissionGate.snapshot(input.docId, input.profile,
                        admissionConfig)
                    val compatible = history[input.docId].orEmpty().filter {
                        it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint
                    }
                    val old = compatible.filter { it.evidenceJson != null }.maxByOrNull { it.id } ?: compatible.maxByOrNull { it.id }
                    val cache = old?.evidenceJson?.let { mapper.readValue<VerificationWorkerEvidence>(it) }
                    if (!cache?.executionContext?.paperSourceRefs.isNullOrEmpty()) return null
                    val external = input.profile.externalIds?.let { mapper.readTree(it) }
                    val source = old?.sourceName ?: input.profile.dataSource?.uppercase()?.takeIf { it.matches(Regex("[A-Z_0-9]{1,32}")) } ?: "UNKNOWN"
                    val key = old?.sourceItemKey ?: listOfNotNull(external?.path("doi")?.asText(null), external?.path("pmid")?.asText(null),
                        external?.path("pmcId")?.asText(null)).firstOrNull()?.take(128) ?: input.docId
                    return PaperArchiveLookup(old?.ownerExecutionId ?: run.executionId, source, key,
                        PaperArchiveLookup.doi(external?.path("doi")?.asText(null)), PaperArchiveLookup.pmid(external?.path("pmid")?.asText(null)),
                        PaperArchiveLookup.pmcid(external?.path("pmcId")?.asText(null)))
                }
                val lookups = unique.associate { it.docId to lookup(it) }
                val located = papers.locate(lookups.values.filterNotNull().distinct())
                ensurePreparing(run.executionId)
                val reservations = unique.map { input ->
                    val profile = input.profile
                    val snapshot = DiscoveryVerificationAdmissionGate.snapshot(input.docId, profile,
                        admissionConfig)
                    val compatible = history[input.docId].orEmpty().filter {
                        it.identityHash == snapshot.identityHash && it.factFingerprint == snapshot.factFingerprint
                    }
                    val old = compatible.filter { it.evidenceJson != null }.maxByOrNull { it.id }
                        ?: compatible.maxByOrNull { it.id }
                    val cache = old?.evidenceJson?.let { json -> mapper.readValue<VerificationWorkerEvidence>(json) }
                    val external = profile.externalIds?.let { mapper.readTree(it) }
                    val sourceName = old?.sourceName ?: profile.dataSource?.uppercase()?.takeIf { it.matches(Regex("[A-Z_0-9]{1,32}")) } ?: "UNKNOWN"
                    val sourceKey = old?.sourceItemKey ?: listOfNotNull(external?.path("doi")?.asText(null),
                        external?.path("pmid")?.asText(null), external?.path("pmcId")?.asText(null)).firstOrNull()?.take(128) ?: input.docId
                    val refs = cache?.executionContext?.paperSourceRefs?.takeIf { it.isNotEmpty() }
                        ?: located[lookups[input.docId]].orEmpty()
                    require(refs.size <= 100) { "SOURCE_LOOKUP_LIMIT" }
                    val review = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(VerificationReviewInput(
                        input.admissionRevision, input.beforeDecision, refs, cache,
                        cache?.let { DiscoveryVerificationRepository.replayHash(mapper.writeValueAsString(it)) }))
                    review.put("baselineItemId", old?.id)
                    review.put("baselineEvidenceAvailable", cache != null)
                    val root = mapper.createObjectNode().put("docId", input.docId).put("identityHash", snapshot.identityHash)
                        .put("factFingerprint", snapshot.factFingerprint)
                    root.set<com.fasterxml.jackson.databind.JsonNode>("profile", mapper.valueToTree(profile))
                    root.set<com.fasterxml.jackson.databind.JsonNode>("reviewInput", review)
                    VerificationReservation(run.executionId, input.docId, snapshot.identityHash, run.policyVersion,
                        sourceName, sourceKey, root.toString(), snapshot.factFingerprint)
                }
                transactions.executeWithoutResult {
                    ensurePreparing(run.executionId)
                    reservations.forEach { repository.reserveRecheckItem(it, clock.instant()) }
                }
                tasks.updateProgressCounts(run.executionId, seen.size, 0, LocalDateTime.now())
                progress()
                return true
            }
            repository.preparationStage(run.executionId, "SCANNING", clock.instant())
            fun spool(inputs: List<Input>) {
                ensurePreparing(run.executionId)
                repository.savePreparationInputs(run.executionId, inputs.map { it.docId to mapper.writeValueAsString(it) }, clock.instant())
                progress()
            }
            if (request.scope == RecheckScope.IDS) {
                val current = admissions.findAdmissions(request.docIds).associateBy { it.expertDocId }
                val inputs = request.docIds.distinct().sorted().map { doc ->
                    val raw = writer.readDiscoveryDocument(ExpertIndexLevel.RAW, doc)
                        ?: throw IllegalStateException("SOURCE_NOT_FOUND:$doc")
                    require(DiscoveryIdentity.isDiscoveryMap(raw.source)) { "SOURCE_NOT_DISCOVERED:$doc" }
                    Input(doc, writer.discoveryProfile(doc, raw.source), current[doc]?.revision, current[doc]?.decision)
                }
                spool(inputs)
            } else {
                // The scroll callback only persists the input; paper lookups never hold an ES cursor open.
                scan.scanAll(DiscoveryReviewScanService.DiscoveryReviewFilter(ExpertIndexLevel.RAW, "discovered",
                    request.filter.q, request.filter.issue, request.filter.decision), 500) { experts ->
                    spool(experts.map { Input(it.docId, it.profile, if (it.row.initialized) it.row.revision else null,
                        if (it.row.initialized) it.row.decision else null) })
                    true
                }
            }
            repository.preparationStage(run.executionId, "EVIDENCE", clock.instant())
            var after = ""
            while (true) {
                ensurePreparing(run.executionId)
                val inputs = repository.preparationInputs(run.executionId, after).map { mapper.readValue<Input>(it) }
                if (inputs.isEmpty()) break
                batch(inputs)
                after = inputs.last().docId
            }
            transactions.executeWithoutResult {
                ensurePreparing(run.executionId)
                val current = repository.findRun(run.executionId)!!
                check(repository.compareAndSetRecheckControl(run.executionId, current.revision,
                    repository.recheck(current).control.copy(phase = RecheckPhase.READY, scopeHash = repository.recheckScopeHash(run.executionId)), clock.instant()))
            }
            finishTask(run.executionId, "SUCCESS", seen.size, null)
        } catch (fatal: Error) {
            // Best effort after stack unwinds; if persistence also fails, no live heartbeat masks lease expiry.
            try { failPreparation(run.executionId, "PREPARE_FATAL:${fatal.javaClass.simpleName}") }
            catch (cleanup: Throwable) { if (cleanup !== fatal) fatal.addSuppressed(cleanup) }
            throw fatal
        } catch (ex: Exception) {
            val phase = repository.findRun(run.executionId)?.let { repository.recheck(it).control.phase }
            when {
                phase == RecheckPhase.CANCELLED -> finishTask(run.executionId, "CANCELLED", seen.size, "RUN_CANCELLED")
                tasks.findById(run.executionId).orElseThrow().status == "INTERRUPTED" ->
                    repository.reconcileRecheck(run.executionId, clock.instant())
                else -> failPreparation(run.executionId, ex.message?.take(1000) ?: "PREPARE_FAILED")
            }
        }
    }

    private fun ensurePreparing(id: Long) {
        val run = repository.findRun(id) ?: error("RUN_NOT_FOUND")
        check(repository.recheck(run).control.phase == RecheckPhase.PREPARING && !run.inputClosed &&
            tasks.findById(id).orElseThrow().status == "RUNNING" && !Thread.currentThread().isInterrupted) { "PREPARE_INTERRUPTED" }
    }

    private fun failPreparation(id: Long, error: String) {
        transactions.executeWithoutResult {
            val current = repository.findRun(id) ?: return@executeWithoutResult
            if (repository.recheck(current).control.phase == RecheckPhase.PREPARING)
                repository.compareAndSetRecheckControl(id, current.revision, repository.recheck(current).control.copy(phase = RecheckPhase.FAILED), clock.instant())
        }
        finishTask(id, "FAILED", repository.recheckCounts(id).total.toInt(), error)
    }

    private fun finishTask(id: Long, status: String, count: Int, error: String?) {
        progressStore?.update(TASK_TYPE, com.weibo.talentintroduction.task.service.TaskProgress(TASK_TYPE, status, 0,
            count.toLong(), count.toLong(), error ?: "名单准备完成，等待确认执行"), id)
        val control = repository.findRun(id)?.let { repository.recheck(it).control }
        tasks.finishOwned(id, owner, status, mapper.writeValueAsString(mapOf("runId" to id, "frozen" to count,
            "scanned" to control?.scanned, "stage" to control?.preparationStage, "phase" to "PREPARATION_ONLY")), count, if (status == "FAILED") 1 else 0, error, LocalDateTime.now())
    }

    fun start(id: Long, request: RecheckStartRequest): RecheckRunView {
        queries.requireRecheck(id)
        verificationHash(request.rosterHash)
        require(request.revision >= 0)
        val run = repository.findRun(id)!!
        if (repository.recheck(run).control.phase == RecheckPhase.READY) {
            if (!properties.enabled) conflict("VERIFICATION_DISABLED")
            if (!properties.workerEnabled || worker.getIfAvailable() == null) conflict("WORKER_DISABLED")
            if (worker.getIfAvailable()?.isAuthBlocked() == true) conflict("CONFIG_AUTH")
            if (repository.recheck(run).spec.evidenceMode == RecheckEvidenceMode.REFRESH && run.apolloRequested && properties.apolloApiKey.isBlank())
                conflict("APOLLO_API_KEY_MISSING")
            try { run.frozenApolloAccess(mapper) } catch (_: Exception) { conflict("CONFIG_INVALID") }
        }
        repository.startRecheck(id, request.rosterHash, request.revision, clock.instant())
        return queries.recheckRun(id)
    }

    fun cancel(id: Long): RecheckRunView {
        queries.requireRecheck(id)
        repository.cancelRecheck(id, clock.instant())
        finishTask(id, "CANCELLED", repository.recheckCounts(id).total.toInt(), "RUN_CANCELLED")
        return queries.recheckRun(id)
    }

    fun syncRetry(id: Long, request: RecheckSyncRequest): List<RecheckItemView> {
        queries.requireRecheck(id)
        require(request.itemIds.size in 1..100 && request.itemIds.all { it > 0 } && request.itemIds.distinct().size == request.itemIds.size)
        val items = request.itemIds.map { queries.requireRecheckItem(id, it) }
        if (items.any { it.admissionRevision == null || it.state != VerificationState.COMPLETED ||
                it.projectionState == VerificationProjection.NOT_REQUIRED }) conflict("ITEM_NOT_ADMITTED")
        items.forEach { item ->
            if (item.projectionState in setOf(VerificationProjection.ERROR, VerificationProjection.PENDING)) commits.project(item)
        }
        return items.map { queries.recheckItem(id, it.id) }
    }

    /** No worker or provider dispatch here. The original worker owns claim/recovery of running items. */
    @Scheduled(fixedDelayString = "\${talent-introduction.discovery-verification.poll-ms:5000}")
    fun reconcile() {
        preparingThreads.forEach { (id, thread) ->
            if (thread.isAlive) tasks.heartbeatExecution(id, owner, LocalDateTime.now())
        }
        repository.unsettledRechecks().forEach { repository.reconcileRecheck(it, clock.instant()) }
    }

    private fun conflict(code: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, code)
    companion object {
        const val TASK_TYPE = "DISCOVERY_RECHECK_PREPARE"
        const val CODE_VERSION = "discovery-recheck-v1-rule4"
    }
}
