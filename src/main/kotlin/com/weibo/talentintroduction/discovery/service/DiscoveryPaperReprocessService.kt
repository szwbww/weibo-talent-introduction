package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import java.util.concurrent.CancellationException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryPaperReprocessRepository
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** AUDIT never invokes the apply adapter, consumer or provider. Both modes share the owned lifecycle. */
@Service
class DiscoveryPaperReprocessService(private val repository: DiscoveryPaperReprocessRepository,
    private val archive: DiscoveryPaperArchiveService, private val analyzer: PaperBodyAnalyzer,
    private val mapper: ObjectMapper, private val apolloSettings: DiscoveryApolloSettingsService,
    private val progress: TaskProgressStore, private val apply: DiscoveryPaperReprocessApplyService? = null) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val owner = UUID.randomUUID().toString()
    private val stopping = AtomicBoolean(false)
    private val submitted = AtomicBoolean(false)
    private val pool = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "paper-reprocess-audit").apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
    @Volatile private var live: ReprocessRun? = null
    @Volatile private var lastHeartbeat = 0L
    private var lastPublishedId = -1L
    private var lastPublishedAt = 0L
    internal var parseBudgetMillis: Long = 15 * 60 * 1000L
    internal var beforeParse: (ReprocessItem) -> Unit = {}

    fun preview(sourceExecutionId: Long): ReprocessPreview {
        positive(sourceExecutionId)
        val w = repository.watermark(sourceExecutionId)
        return ReprocessPreview(sourceExecutionId, w.recordCount, w.scopeHash, repository.active(),
            repository.missingSourcePapers(sourceExecutionId))
    }
    fun create(request: CreateReprocessRequest): ReprocessRun {
        positive(request.sourceExecutionId)
        val canonicalKey = try { UUID.fromString(request.requestKey).toString() } catch (_: IllegalArgumentException) { null }
        if (canonicalKey != request.requestKey)
            throw ReprocessException("INVALID_REQUEST_KEY", 400, "请求标识无效")
        if (!request.expectedScopeHash.matches(Regex("[a-f0-9]{64}")))
            throw ReprocessException("INVALID_SCOPE_HASH", 400, "范围标识无效")
        val requestHash = hash("${request.sourceExecutionId}|${request.mode}|${request.expectedScopeHash}")
        repository.byRequestKey(request.requestKey)?.let {
            if (it.requestHash != requestHash) throw ReprocessException("REQUEST_KEY_CONFLICT", 409, "相同请求标识对应不同参数")
            return it
        }
        if (request.mode == ReprocessMode.ENFORCE && apply == null) throw ReprocessException("APPLY_NOT_AVAILABLE", 503, "正式补录尚未开放，请使用仅解析对照")
        val w = repository.watermark(request.sourceExecutionId)
        if (w.scopeHash != request.expectedScopeHash) throw ReprocessException("SCOPE_CHANGED", 409, "原文范围已变化，请重新预览")
        val scope = mapper.createObjectNode().put("schemaVersion", 1).put("upperRecordId", w.upperRecordId)
            .put("cursor", 0).put("frozen", false).put("frozenCount", 0).put("expectedScopeHash", w.scopeHash)
            .put("maxAttemptId", w.maxAttemptId).put("scopeHash", w.scopeHash).put("codeVersion", CODE_VERSION).put("evaluationAt", Instant.now().toString())
        scope.set<JsonNode>("parserVersions", mapper.valueToTree(analyzer.versions().toSortedMap()))
        val access = apolloSettings.freeze()
        scope.set<JsonNode>("apolloAccess", access.toJson(mapper))
        if (request.mode == ReprocessMode.ENFORCE) {
            val settings = try { requireNotNull(apply).freezeSettings(access) }
                catch (_: IllegalStateException) { throw ReprocessException("VERIFICATION_UNAVAILABLE", 503, "核验服务或配置不可用，未创建正式任务") }
            scope.set<JsonNode>("verificationSettings", settings)
            scope.put("policyVersion", VerificationMode.ENFORCE.policyVersion)
        }
        return repository.create(request, requestHash, scope, owner)
    }
    fun detail(id: Long): ReprocessDetail {
        val run = requireRun(id)
        val summary = repository.summary(id)
        val frozen = summary.totalKnown
        val papersDone = summary.paperComplete + summary.paperErrors + summary.paperCancelled + summary.paperSkipped
        val message = if (!frozen) "已固定${run.scope.path("frozenCount").asLong()}篇，统计中"
            else "论文 ${papersDone}/${summary.papers}篇（失败${summary.paperErrors}、跳过${summary.paperSkipped}）；" +
                "专家处理 ${summary.targetComplete + summary.targetErrors}/${summary.targets}个；" +
                "待核验 ${summary.pendingVerification}，实际准入 ${summary.actualAdmitted}"
        return ReprocessDetail(run, summary, message, frozen)
    }
    fun runs(sourceId: Long?, active: Boolean?, requestKey: String?, page: Int, size: Int): ReprocessPage<ReprocessRun> {
        paging(page, size); sourceId?.let(::positive)
        return repository.runs(sourceId, active, requestKey, page, size)
    }
    fun items(id: Long, kind: ReprocessKind?, page: Int, size: Int): ReprocessPage<ReprocessItem> {
        requireRun(id); paging(page, size); return repository.items(id, kind, page, size)
    }
    fun item(id: Long, itemId: Long): ReprocessItem {
        requireRun(id); positive(itemId)
        return repository.item(id, itemId) ?: throw ReprocessException("NOT_FOUND", 404, "记录不存在或不属于本任务")
    }
    fun cancel(id: Long, revision: Long): ReprocessRun {
        val run = requireRun(id); revision(revision)
        val cancelled = repository.cancel(id, revision)
        if (run.mode == ReprocessMode.ENFORCE) requireNotNull(apply).cancel(id)
        return cancelled
    }
    fun retry(id: Long, revision: Long): ReprocessRun {
        requireRun(id); revision(revision)
        return repository.retry(id, revision, versions())
    }
    private fun versions(): JsonNode = mapper.createObjectNode().put("codeVersion", CODE_VERSION).also {
        it.set<JsonNode>("parserVersions", mapper.valueToTree(analyzer.versions().toSortedMap()))
    }
    private fun requireRun(id: Long): ReprocessRun { positive(id); return repository.find(id) ?: throw ReprocessException("NOT_FOUND", 404, "任务不存在") }
    private fun positive(id: Long) { if (id <= 0) throw ReprocessException("INVALID_ID", 400, "标识必须为正整数") }
    private fun revision(value: Long) { if (value < 0) throw ReprocessException("INVALID_REVISION", 400, "版本号无效") }
    private fun paging(page: Int, size: Int) { if (page < 0 || size !in 1..100) throw ReprocessException("INVALID_PAGE", 400, "页码或每页数量无效") }

    /** The scheduler never parses; only this feature's bounded dedicated pool does. */
    @Scheduled(fixedDelay = 5000)
    fun poll() {
        if (stopping.get()) return
        live?.let { run ->
            if (System.nanoTime() - lastHeartbeat >= TimeUnit.SECONDS.toNanos(10)) {
                if (!repository.heartbeat(run.executionId, run.leaseToken!!, run.generation)) live = null
                lastHeartbeat = System.nanoTime()
            }
        }
        if (!submitted.compareAndSet(false, true)) return
        val run = try { repository.claimRun(owner) } catch (e: Exception) { submitted.set(false); throw e }
        if (run == null) { submitted.set(false); return }
        try {
            pool.execute {
                live = run; lastHeartbeat = 0L
                try { execute(run) }
                finally { live = null; submitted.set(false) }
            }
        } catch (_: RejectedExecutionException) {
            repository.releaseRejected(run.executionId, run.leaseToken!!, run.generation)
            submitted.set(false)
        }
    }

    /** Also used by the isolated real-SQL/API fixture, without an HTTP thread doing the work. */
    internal fun execute(run: ReprocessRun) {
        val token = run.leaseToken ?: throw ReprocessException("LEASE_LOST", 409)
        val id = run.executionId
        fun checkpoint(deadline: Long = Long.MAX_VALUE) {
            if (stopping.get() || Thread.currentThread().isInterrupted) throw PaperParseCheckpointException("EXECUTION_INTERRUPTED")
            val current = repository.find(id) ?: throw PaperParseCheckpointException("LEASE_LOST")
            if (current.leaseToken != token || current.generation != run.generation || current.leaseUntil?.isAfter(Instant.now()) != true)
                throw PaperParseCheckpointException("LEASE_LOST")
            if (current.cancelRequested || current.state == ReprocessState.CANCELLING) throw PaperParseCheckpointException("CANCELLED")
            if (System.nanoTime() >= deadline) throw PaperParseCheckpointException("PARSE_BUDGET_EXCEEDED")
        }
        fun phase(value: PaperReprocessPhase) {
            if (!repository.setPhase(id, token, run.generation, value)) throw PaperParseCheckpointException("LEASE_LOST")
        }
        fun persist(item: ReprocessItem, state: ReprocessItemState, result: JsonNode) {
            if (!repository.completeItem(id, token, run.generation, item.id, item.leaseToken!!, item.generation, state, result))
                throw PaperParseCheckpointException("LEASE_LOST")
        }
        try {
            if (run.scope.path("parserVersions") != versions().path("parserVersions") || run.scope.path("codeVersion").asText() != CODE_VERSION)
                throw PaperParseCheckpointException("VERSION_CHANGED")
            progress.tryStart(TASK_TYPE, TaskProgress(taskType = TASK_TYPE, status = "RUNNING", batchNumber = 0,
                processedCount = 0, totalCount = 0, executionId = id))
            progress.setCurrentExecutionId(TASK_TYPE, id)
            while (!requireRun(id).scope.path("frozen").asBoolean()) {
                checkpoint()
                repository.freezePage(id, token, run.generation)
                publish(id)
            }
            phase(PaperReprocessPhase.READ)
            // Cache is invocation-scoped and bounded. Metadata and kind are part of interpretation identity.
            val parsedCache = object : LinkedHashMap<String, PaperBodyComputation>(16, .75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PaperBodyComputation>?) = size > 64
            }
            while (true) {
                checkpoint()
                val item = repository.claimItem(id, token, run.generation, ReprocessKind.PAPER) ?: break
                try {
                    phase(PaperReprocessPhase.READ)
                    val input = item.input
                    val recordId = input.path("recordId").asLong()
                    val refs = input.path("bodies").map { it.path("attemptId").asLong() to it.path("hash").asText() }
                    if (refs.isEmpty()) {
                        persist(item, ReprocessItemState.SKIPPED, resultNode("SOURCE_MISSING",
                            listOf(ReprocessTraceState.SKIPPED, ReprocessTraceState.SKIPPED,
                                ReprocessTraceState.NOT_STARTED, ReprocessTraceState.SKIPPED,
                                ReprocessTraceState.SKIPPED, ReprocessTraceState.SUCCESS)))
                        publish(id)
                        continue
                    }
                    beforeParse(item)
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(parseBudgetMillis)
                    val metadata = input.path("metadata")
                    val interpretation = hash(metadata.path("authors").toString() +
                        "|" + (metadata.get("pubYear") ?: metadata.get("year")).toString())
                    val parsed = linkedMapOf<String, PaperBodyComputation>()
                    val bodyFailures = linkedMapOf<String, String>()
                    for ((bodyHash, bodyRefs) in refs.groupBy { it.second }) {
                        checkpoint(deadline)
                        try {
                            phase(PaperReprocessPhase.READ)
                            var cached: PaperBodyComputation? = null
                            var cacheKey = ""
                            val body = archive.analysisLease(recordId, bodyRefs).use { lease ->
                                if (hash(lease.record.metadataJson) != input.path("metadataHash").asText())
                                    throw ReprocessException("SOURCE_CHANGED", 409)
                                val source = lease.bodies.single()
                                cacheKey = bodyHash + "|" + source.attempt.kind + "|" + interpretation
                                cached = parsedCache[cacheKey]
                                if (cached == null) PaperBodyInput(bodyHash, source.attempt.kind, source.input.readBytes()) else null
                            }
                            // At most one body's bytes are retained, and its archive lease is already released.
                            checkpoint(deadline)
                            phase(PaperReprocessPhase.PARSE)
                            parsed[bodyHash] = cached ?: analyzer.parse(body!!, metadata) { checkpoint(deadline) }
                                .also { parsedCache[cacheKey] = it }
                        } catch (e: PaperParseCheckpointException) { throw e }
                        catch (e: CancellationException) { throw PaperParseCheckpointException("EXECUTION_INTERRUPTED") }
                        catch (e: Exception) {
                            bodyFailures[bodyHash] = when (e) { is ReprocessException -> e.code; is ArchiveException -> e.code; else -> "PARSE_FAILED" }
                        }
                    }
                    checkpoint(deadline)
                    val current = requireRun(id)
                    val assembled = analyzer.assemble(parsed, input.get("first")?.takeUnless { it.isNull },
                        refs.filter { it.second in parsed }.map { it.first }.toSet(),
                        Instant.parse(current.scope.path("evaluationAt").asText()), analyzer.versions())
                    val result = if (bodyFailures.isEmpty()) assembled else assembled.copy(
                        coverage = assembled.coverage.copy(status = PaperEvidenceStatus.ERROR,
                            reasons = (assembled.coverage.reasons + bodyFailures.values).distinct()),
                        added = emptyList(), removed = emptyList(), delta = null, deltaAddedBindings = null, hasGain = null,
                        reasons = (assembled.reasons + bodyFailures.values).distinct())
                    val candidates = parsed.flatMap { (bodyHash, computation) -> computation.emails.map { email ->
                        mapper.valueToTree<ObjectNode>(email).put("bodyHash", bodyHash).put("recordId", recordId)
                            .put("sourceExecutionId", run.sourceExecutionId).put("sourceName", input.path("sourceName").asText())
                            .put("sourceItemKey", input.path("paperKey").asText()).also { candidate ->
                                candidate.set<JsonNode>("attemptIds", mapper.valueToTree(refs.filter { it.second == bodyHash }.map { it.first }))
                            }
                    } }
                    val parseFailed = bodyFailures.isNotEmpty() || result.coverage.status == PaperEvidenceStatus.ERROR
                    val payload = resultNode(bodyFailures.values.firstOrNull() ?: if (parseFailed) "PARSE_FAILED" else "PARSED",
                        listOf(if (bodyFailures.isEmpty()) ReprocessTraceState.SUCCESS else ReprocessTraceState.ERROR,
                        if (parseFailed) ReprocessTraceState.ERROR else ReprocessTraceState.SUCCESS,
                        ReprocessTraceState.NOT_STARTED, ReprocessTraceState.SKIPPED, ReprocessTraceState.SKIPPED, ReprocessTraceState.SUCCESS))
                    payload.set<JsonNode>("analysis", mapper.valueToTree(result))
                    payload.set<JsonNode>("attempts", mapper.valueToTree(input.path("bodies").map { reference ->
                        reference.deepCopy<ObjectNode>().also {
                            val bodyHash = reference.path("hash").asText()
                            parsed[bodyHash]?.let { computation -> it.set<JsonNode>("parsed", mapper.valueToTree(computation)) }
                            bodyFailures[bodyHash]?.let { code -> it.put("reasonCode", code) }
                        }
                    }))
                    payload.set<JsonNode>("candidates", mapper.valueToTree(candidates))
                    payload.put("baselineStatus", if (result.baselineCoverage == "COMPLETE") "AVAILABLE" else "BASELINE_UNAVAILABLE")
                    persist(item, if (parseFailed) ReprocessItemState.ERROR else ReprocessItemState.COMPLETE, payload)
                } catch (e: PaperParseCheckpointException) { throw e }
                catch (e: CancellationException) { throw PaperParseCheckpointException("EXECUTION_INTERRUPTED") }
                catch (e: Exception) {
                    val code = when (e) { is ReprocessException -> e.code; is ArchiveException -> e.code; else -> "PARSE_FAILED" }
                    val failure = resultNode(code, listOf(ReprocessTraceState.ERROR, ReprocessTraceState.ERROR,
                        ReprocessTraceState.NOT_STARTED, ReprocessTraceState.SKIPPED, ReprocessTraceState.SKIPPED, ReprocessTraceState.SUCCESS))
                    PaperReprocessFailure.record(failure, e, id, item.id)
                    failure.set<JsonNode>("attempts", mapper.valueToTree(item.input.path("bodies").map {
                        it.deepCopy<ObjectNode>().put("reasonCode", code)
                    }))
                    persist(item, ReprocessItemState.ERROR, failure)
                }
                publish(id)
            }
            checkpoint()
            phase(PaperReprocessPhase.BIND)
            // Merge only complete papers after every parse attempt has reached a terminal state.
            val candidates = linkedMapOf<String, MutableList<JsonNode>>()
            var page = 0
            do {
                val papers = repository.items(id, ReprocessKind.PAPER, page++, 100)
                for (paper in papers.items.filter { it.state == ReprocessItemState.COMPLETE }) for (candidate in paper.result?.path("candidates") ?: mapper.createArrayNode()) {
                    val email = candidate.path("email").asText().trim().lowercase()
                    if (email.isNotEmpty()) candidates.getOrPut(email) { mutableListOf() }.add(candidate)
                }
            } while (papers.items.size == 100)
            for ((email, refs) in candidates) {
                checkpoint()
                val input = mapper.createObjectNode().put("schemaVersion", 1).put("email", email)
                input.set<JsonNode>("paperRefs", mapper.valueToTree(refs))
                if (!repository.addTarget(id, token, run.generation, hash(email), input)) throw PaperParseCheckpointException("LEASE_LOST")
            }
            if (run.mode == ReprocessMode.ENFORCE) {
                phase(PaperReprocessPhase.APPLY)
                while (!requireNotNull(apply).apply(requireRun(id))) {
                    publish(id)
                    if (repository.find(id)?.leaseToken == null) return
                    checkpoint()
                    try { Thread.sleep(250) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw CancellationException("EXECUTION_INTERRUPTED")
                    }
                }
                checkpoint(); phase(PaperReprocessPhase.SAVE)
                val summary = repository.summary(id)
                repository.finish(id, token, run.generation, if (summary.paperErrors + summary.targetErrors > 0)
                    ReprocessState.COMPLETED_WITH_ERRORS else ReprocessState.COMPLETED)
                publish(id)
                return
            }
            while (true) {
                checkpoint()
                val target = repository.claimItem(id, token, run.generation, ReprocessKind.TARGET) ?: break
                val refs = target.input.path("paperRefs").toList()
                fun name(n: JsonNode) = listOf(n.path("givenNames").asText(""), n.path("familyNames").asText("")).joinToString(" ").trim().lowercase().replace(Regex("\\s+"), " ")
                val names = refs.map(::name).filter { it.isNotBlank() }.distinct()
                val authorIds = refs.mapNotNull { it.path("openAlexAuthorId").asText(null) }.distinct()
                val orcids = refs.mapNotNull { it.path("orcidId").asText(null) }.distinct()
                val conflict = names.size > 1 || authorIds.size > 1 || orcids.size > 1
                val bound = names.size == 1 && refs.any { !it.path("givenNames").asText().isBlank() &&
                    !it.path("familyNames").asText().isBlank() && DiscoveryIdentity.validEvidence(it.path("identityEvidence").asText(null)) }
                val reason = if (conflict) "CONFLICT" else if (!bound) "IDENTITY_NOT_BOUND" else "AUDIT_CANDIDATE"
                val result = resultNode(reason, listOf(ReprocessTraceState.SUCCESS, ReprocessTraceState.SUCCESS,
                    if (conflict || !bound) ReprocessTraceState.INSUFFICIENT else ReprocessTraceState.SUCCESS,
                    ReprocessTraceState.SKIPPED, ReprocessTraceState.SKIPPED, ReprocessTraceState.SUCCESS))
                result.put("wouldCreate", bound && !conflict).put("wouldRepair", bound && !conflict)
                    .put("candidateOnly", true).put("businessExistenceEvaluated", false)
                for (field in listOf("before", "after", "receipt", "verificationItemId", "verificationRunId")) result.putNull(field)
                result.set<JsonNode>("paperRefs", target.input.path("paperRefs"))
                persist(target, ReprocessItemState.COMPLETE, result)
            }
            checkpoint(); phase(PaperReprocessPhase.SAVE)
            val summary = repository.summary(id)
            repository.finish(id, token, run.generation, if (summary.paperErrors + summary.targetErrors > 0) ReprocessState.COMPLETED_WITH_ERRORS else ReprocessState.COMPLETED)
            publish(id)
        } catch (e: PaperParseCheckpointException) {
            val state = if (e.code == "CANCELLED") ReprocessState.CANCELLED else ReprocessState.INTERRUPTED
            repository.finish(id, token, run.generation, state, e.code)
            publish(id)
        } catch (e: CancellationException) {
            val cancelled = repository.find(id)?.cancelRequested == true
            if (cancelled && run.mode == ReprocessMode.ENFORCE) requireNotNull(apply).cancel(id)
            repository.finish(id, token, run.generation, if (cancelled) ReprocessState.CANCELLED else ReprocessState.INTERRUPTED,
                if (cancelled) "CANCELLED" else "LEASE_LOST")
            publish(id)
        } catch (e: Exception) {
            log.warn("Paper reprocess execution {} stopped ({})", id, e.javaClass.simpleName)
            repository.finish(id, token, run.generation, ReprocessState.FAILED,
                if (e is ReprocessException) e.code else "EXECUTION_FAILED")
            publish(id)
        }
    }
    private fun resultNode(code: String, states: List<ReprocessTraceState>): ObjectNode = mapper.createObjectNode()
        .put("schemaVersion", 1).put("reasonCode", code).put("reason", when (code) {
            "PARSED" -> "留存原文解析结束；不表示补录或准入"
            "CONFLICT" -> "同一邮箱的作者身份冲突，未选择覆盖任何来源"
            "AUDIT_CANDIDATE" -> "仅拟补档候选；未查询业务档案或外部资格"
            "IDENTITY_NOT_BOUND" -> "邮箱尚未明确绑定完整作者身份"
            "SOURCE_CHANGED" -> "固定来源已变化，未替换其他原文"
            "SOURCE_MISSING" -> "固定原文不可读取"
            else -> "处理未完成，请查看原因代码"
        }).put("actualNew", 0).put("actualUpdated", 0).put("actualAdmitted", 0).also { node ->
            node.set<JsonNode>("trace", mapper.valueToTree(states.mapIndexed { index, state -> mapOf("stepNo" to index + 1,
                "state" to state.name, "step" to listOf("READ", "PARSE", "BIND", "APPLY", "REVIEW", "SAVE")[index]) }))
        }
    private fun publish(id: Long) {
        val run = requireRun(id)
        val now = System.nanoTime()
        if (run.state in setOf(ReprocessState.PREPARING, ReprocessState.RUNNING) &&
            lastPublishedId == id && now - lastPublishedAt < TimeUnit.SECONDS.toNanos(5)) return
        lastPublishedId = id
        lastPublishedAt = now
        val detail = detail(id)
        val summary = detail.summary
        val status = when (detail.run.state) {
            ReprocessState.PREPARING, ReprocessState.RUNNING -> "RUNNING"
            ReprocessState.COMPLETED -> "SUCCESS"
            ReprocessState.COMPLETED_WITH_ERRORS, ReprocessState.FAILED -> "FAILED"
            else -> detail.run.state.name
        }
        val targetPhase = detail.run.phase in setOf(PaperReprocessPhase.APPLY, PaperReprocessPhase.REVIEW, PaperReprocessPhase.SAVE)
        progress.update(TASK_TYPE, TaskProgress(taskType = TASK_TYPE, status = status, batchNumber = 0,
            processedCount = if (targetPhase)
                summary.targetComplete + summary.targetErrors else
                summary.paperComplete + summary.paperErrors + summary.paperCancelled + summary.paperSkipped,
            totalCount = if (targetPhase) { if (summary.targetTotalKnown) summary.targets else 0 }
                else if (detail.totalKnown) summary.papers else 0, message = detail.progressText, executionId = id), id)
    }
    @PreDestroy fun shutdown() { stopping.set(true); pool.shutdownNow() }
    companion object {
        const val TASK_TYPE = "DISCOVERY_PAPER_REPROCESS"
        const val CODE_VERSION = "paper-reprocess-audit-1"
        private fun hash(value: String) = DiscoveryPaperArchiveService.sha(value.toByteArray(Charsets.UTF_8))
    }
}
