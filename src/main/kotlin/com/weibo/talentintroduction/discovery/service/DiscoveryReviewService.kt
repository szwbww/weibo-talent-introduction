package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.domain.AdmissionReason
import com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewAction
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmItemView
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewConfirmResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewDecision
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewExpertPage
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewItemState
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareItemView
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewReasonSnapshot
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewRevokeResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewSnapshot
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewApplyOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRevokeOutcome
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import com.weibo.talentintroduction.expert.domain.EligibilityResult
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/** 02（I-3）：提交的固定快照已变化 / 撤销对象已不是当前有效决策 —— 映射为 409。 */
class DiscoveryReviewConflictException(message: String) : IllegalStateException(message)

/** 02（I-3）：查询超出服务端预算 —— 映射为 503，绝不返回不完整总数。 */
class DiscoveryReviewTimeoutException(message: String) : IllegalStateException(message)

/**
 * 03：批次阶段。没有新列，全部由 `task_execution` 行与 `expert_discovery_review_item` 持久状态派生
 * （计划 §实现方案 2/4；TaskProgressStore 只用于实时进度，不是审核权威）。
 */
object DiscoveryReviewBatchPhase {
    const val PREPARING = "PREPARING"
    const val READY = "READY"
    const val PREPARE_FAILED = "PREPARE_FAILED"
    const val APPLYING = "APPLYING"
    const val INTERRUPTED = "INTERRUPTED"
    const val APPLIED = "APPLIED"
    const val CANCELLED = "CANCELLED"
}

/**
 * 03（I-1～I-4）：批次状态快照。`total = applied + stale + failed + cancelled + pending`（恒等式，I-3）。
 * `expiresAt` 由本批最早一项 `created_at + 24h` 派生（无新列，I-4）。
 */
data class DiscoveryReviewBatchStatus(
    val batchKey: String,
    val phase: String,
    val batchHash: String?,
    val total: Int,
    val applied: Int,
    val stale: Int,
    val failed: Int,
    val cancelled: Int,
    val pending: Int,
    val prepareExecutionId: Long?,
    val applyExecutionId: Long?,
    val expiresAt: LocalDateTime?,
    val items: List<ExpertDiscoveryReviewItem>,
    val nextCursor: Long?
)

/** 03（I-1）：名单固定任务的持久终态（写进 `task_execution.result_summary`，读回判 READY/PREPARE_FAILED）。 */
data class DiscoveryReviewPrepareOutcome(
    val batchKey: String,
    val phase: String,
    val batchHash: String?,
    val total: Int,
    override val taskSuccessCount: Int,
    override val taskFailureCount: Int,
    override val taskFinalStatus: String?
) : TaskExecutionSummaryProvider

/** 03（I-3）：应用任务的持久终态（计数来自持久明细，不是 202 受理时的估计）。 */
data class DiscoveryReviewApplyOutcomeSummary(
    val batchKey: String,
    val phase: String,
    val total: Int,
    val applied: Int,
    val stale: Int,
    val failed: Int,
    val cancelled: Int,
    val pending: Int,
    override val taskSuccessCount: Int,
    override val taskFailureCount: Int,
    override val taskFinalStatus: String?
) : TaskExecutionSummaryProvider

/**
 * 04（I-1）：一次自动准入写入的结果。`admitted` 由结论派生（AUTO_PASSED/LEGACY_APPROVED/MANUAL_APPROVED）；
 * `manual` 表示结论来自人工点击；`identityChanged` 表示旧结论的身份与当前档案不一致（已生成新自动结论）。
 */
data class DiscoveryReviewAdmissionOutcome(
    val docId: String,
    val decision: String,
    val admitted: Boolean,
    val manual: Boolean,
    val identityChanged: Boolean,
    val revision: Long,
    val reasons: List<AdmissionReason>
)

/** 04（I-1）：批量解析准入结论时的身份键（真实 docId + 当前身份哈希）。 */
data class DiscoveryReviewAdmissionKey(val docId: String, val identityHash: String)

/**
 * 04（I-1/I-2）：一个 docId 的当前准入结论解析结果。`decision == null` 表示「尚未初始化」或
 * 「身份已变化、原结论不适用」——两者都必须重新自动判定或人工审核，绝不当成已批准。
 */
data class DiscoveryReviewResolvedAdmission(
    val docId: String,
    val decision: String?,
    val admitted: Boolean,
    val manual: Boolean,
    val initialized: Boolean,
    val identityChanged: Boolean,
    val revision: Long,
    val syncErrorCode: String?
)

/** 04（I-2）：一次候选投影结果。`ok` 为 true 表示候选已存在/已进有效层/已创建/无需投影。 */
data class DiscoveryReviewCandidateSync(
    val result: String,
    val ok: Boolean,
    val errorCode: String?
)

/** 04（I-2）：只补投影的重试结果。 */
data class DiscoveryReviewSyncRetryResult(
    val batchKey: String,
    val retried: Int,
    val synced: Int,
    val failed: Int
)

/** 04（I-4）：存量初始化请求。`requestKey` 幂等；`level` 省略时固定扫 RAW→CANDIDATE→APPLICATION。 */
data class DiscoveryReviewInitializeRequest(
    val requestKey: String? = null,
    val level: String? = null
)

/** 04（I-4）：存量初始化的可见状态（由 task_execution 行与结果摘要派生，无新表）。 */
data class DiscoveryReviewInitializeStatus(
    val key: String,
    val phase: String,
    val total: Int,
    val recorded: Int,
    val unchanged: Int,
    val skipped: Int,
    val byLevel: Map<String, Int>,
    val executionId: Long?,
    val taskStatus: String
)

/** 04（I-4）：初始化工作者的持久终态。 */
data class DiscoveryReviewInitializeOutcome(
    val key: String,
    val phase: String,
    val total: Int,
    val recorded: Int,
    val unchanged: Int,
    val skipped: Int,
    val byLevel: Map<String, Int>,
    override val taskSuccessCount: Int,
    override val taskFailureCount: Int,
    override val taskFinalStatus: String?
) : TaskExecutionSummaryProvider

/**
 * 02 + 03：深度发现审核的查询 / 准备 / 确认 / 撤销；03 追加「审核所有页」的异步名单固定与批量应用。
 *
 * 边界：读取真实 ES source（[ExpertIndexWriterService.readDiscoveryDocument]）用于展示事实与身份
 * 绑定；持久化只写 [DiscoveryReviewRepository] 的两张表；**不做任何 ES 晋升**（04 接投影），
 * **不发送邮件**，actor 只由调用方从登录会话取得。资格判定复用 01 的
 * [DiscoveryAdmissionPolicy]，本片不另写规则。
 *
 * 03 的异步工作者（`DISCOVERY_REVIEW_PREPARE` / `DISCOVERY_REVIEW_APPLY`）复用现有
 * [TaskExecutionService] 与 `enrichmentExecutor` 单任务 token 语义；两个 task type 不在本文件硬写前端字符串，
 * 中文名/进度白名单只在 [com.weibo.talentintroduction.task.domain.TaskTypeCatalog] 声明。
 */
@Service
class DiscoveryReviewService(
    private val repository: DiscoveryReviewRepository,
    private val policy: DiscoveryAdmissionPolicy,
    private val writer: ExpertIndexWriterService,
    private val restTemplate: org.springframework.web.client.RestTemplate,
    private val properties: ElasticsearchProperties,
    private val expertIndexService: ExpertIndexService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
    private val taskExecutions: TaskExecutionService? = null,
    @Qualifier("enrichmentExecutor") private val executor: Executor? = null,
    private val scanService: DiscoveryReviewScanService = DiscoveryReviewScanService(
        repository, policy, writer, restTemplate, properties, expertIndexService, objectMapper
    ),
    /**
     * 04（I-1/I-2）：admission 表写入口收敛到本服务的 `recordAutomatic`/`applyManual`。
     * 02/03 的 repository 未授权新增方法，故本服务用窄语句完成两处它没有暴露的写：
     * 自动结论的 CAS 更新、`APPLIED` 项上的候选同步失败标记。其余读写仍全部经 repository。
     */
    private val jdbcTemplate: JdbcTemplate? = null
) {

    // ── 查询（I-2/I-3） ───────────────────────────────────────────────────────

    /**
     * `GET /experts`：服务端过滤，不只过滤当前页。读取/筛选与「审核所有页」共用
     * [DiscoveryReviewScanService] 的同一份条件（计划 §实现方案 1）。
     */
    fun listExperts(
        levelRaw: String?,
        tag: String?,
        from: Int,
        size: Int,
        q: String?,
        issue: String?,
        decision: String?
    ): DiscoveryReviewExpertPage =
        scanService.listPage(parseLevel(levelRaw), tag, from, size, q, issue, decision)

    fun history(docId: String, limit: Int = HISTORY_LIMIT): List<ExpertDiscoveryReviewItem> {
        require(docId.isNotBlank()) { "docId 必填" }
        return repository.findHistory(docId, limit.coerceIn(1, HISTORY_LIMIT))
    }

    // ── 准备（IDS，I-3/I-4） ──────────────────────────────────────────────────

    fun prepare(request: DiscoveryReviewPrepareRequest, actor: String): DiscoveryReviewPrepareResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val scope = request.scope?.trim().orEmpty().ifEmpty { SCOPE_IDS }
        require(scope == SCOPE_IDS) { "仅支持 scope=$SCOPE_IDS" }
        val action = parseAction(request.action)
        require(action != DiscoveryReviewAction.REVOKE) { "REVOKE 请使用 /items/{id}/revoke" }
        val docIds = request.docIds.map { it.trim() }.filter { it.isNotEmpty() }
        require(docIds.isNotEmpty()) { "docIds 不能为空" }
        require(docIds.size <= MAX_BATCH_SIZE) { "单批最多 $MAX_BATCH_SIZE 人" }
        require(docIds.distinct().size == docIds.size) { "docIds 不得重复" }
        val note = normalizedNote(request.note, action)

        val level = parseLevel(request.level)
        val now = LocalDateTime.now(clock)
        val batchKey = UUID.randomUUID().toString().replace("-", "")
        val inserted = mutableListOf<ExpertDiscoveryReviewItem>()

        for (docId in docIds) {
            val snapshot = writer.readDiscoveryDocument(level, docId)
                ?: throw IllegalArgumentException("ES 文档不存在：$docId")
            val profile = writer.discoveryProfile(docId, snapshot.source)
            val automatic = policy.evaluate(profile)
            val identityHash = identityHashOf(docId, profile)
            repository.initializeAdmission(docId, identityHash, automatic.status.name, automatic.policyVersion, now)
            val currentRevision = repository.findAdmission(docId)?.revision ?: 0L
            val expectedRevision = request.expectedRevisions[docId] ?: currentRevision

            val snapshotJson = snapshotJson(level, docId, profile, snapshot.seqNo, snapshot.primaryTerm, now)
            val reasonJson = objectMapper.writeValueAsString(
                DiscoveryReviewReasonSnapshot(
                    automaticStatus = automatic.status.name,
                    blockingReasons = automatic.blockingReasons,
                    hints = automatic.hints,
                    policyVersion = automatic.policyVersion
                )
            )
            val itemId = repository.insertItem(
                batchKey = batchKey,
                expertDocId = docId,
                sourceLevel = level.name,
                identityHash = identityHash,
                snapshotHash = DiscoveryIdentity.hash(snapshotJson),
                expectedRevision = expectedRevision,
                action = action.name,
                state = DiscoveryReviewItemState.STAGED.name,
                snapshotJson = snapshotJson,
                reasonSnapshotJson = reasonJson,
                actor = actor,
                note = note,
                previousItemId = null,
                executionId = request.executionId,
                now = now
            )
            inserted += stagedItem(
                itemId, batchKey, docId, level.name, identityHash,
                DiscoveryIdentity.hash(snapshotJson), expectedRevision, action.name,
                snapshotJson, reasonJson, actor, note, request.executionId, now
            )
        }

        return DiscoveryReviewPrepareResult(
            batchKey = batchKey,
            batchHash = computeBatchHash(inserted),
            itemCount = inserted.size,
            items = inserted.map {
                DiscoveryReviewPrepareItemView(
                    it.expertDocId, it.identityHash, it.expectedRevision, it.snapshotHash, it.state
                )
            }
        )
    }

    // ── 全页：准备（I-1/I-2/I-4） ─────────────────────────────────────────────

    /**
     * `POST /batches/prepare`（`scope=ALL_MATCHING`）：固定规范筛选 + action + 准备人 + 时间，
     * 服务端 ES scroll **每批 500 完整扫描**，落地 `review_item`，完成后才 READY。
     *
     * - request 只存 `batchKey/action/筛选/actor`（**绝不**存 docId 名单）；
     * - `requestKey` 相同 + payload 相同幂等返回，payload 不同 409；
     * - 扫描失败 → PREPARE_FAILED，不可 confirm。
     */
    fun prepareAllMatching(
        request: DiscoveryReviewPrepareRequest,
        tag: String?,
        q: String?,
        issue: String?,
        requestKey: String?,
        actor: String
    ): DiscoveryReviewBatchStatus {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        require(request.scope?.trim().equals(SCOPE_ALL_MATCHING, ignoreCase = true)) {
            "全页准备必须 scope=$SCOPE_ALL_MATCHING"
        }
        val action = parseAction(request.action)
        require(action != DiscoveryReviewAction.REVOKE) { "REVOKE 请使用 /items/{id}/revoke" }
        val note = normalizedNote(request.note, action)

        val level = parseLevel(request.level)
        val batchKey = resolveBatchKey(requestKey)
        // I-2：全页只收 NEEDS_REVIEW，筛选条件由服务端固定，客户端无法扩张到 HOLD/REJECTED/已批准。
        val filter = DiscoveryReviewScanService.DiscoveryReviewFilter(
            level = level,
            tag = tag?.trim()?.takeIf { it.isNotEmpty() },
            q = q?.trim()?.takeIf { it.isNotEmpty() },
            issue = issue?.trim()?.takeIf { it.isNotEmpty() },
            decision = DiscoveryReviewDecision.NEEDS_REVIEW.name
        )
        val payload = preparePayload(batchKey, action, filter, actor)

        val existing = findPrepareTask(batchKey)
        if (existing != null) {
            if (!samePayload(existing.requestPayload, payload)) {
                throw DiscoveryReviewConflictException("相同 requestKey 的名单筛选不同，请更换 requestKey")
            }
            return batchStatus(batchKey)
        }

        val tasks = requireTaskExecutions()
        val worker = requireExecutor()
        try {
            worker.execute {
                try {
                    var prepareExecutionId: Long? = null
                    tasks.runAndRecordWithResult(
                        TASK_PREPARE, "MANUAL", payload,
                        onStarted = { prepareExecutionId = it }
                    ) {
                        runPrepareWorker(batchKey, action, filter, note, actor, prepareExecutionId)
                    }
                } catch (_: Exception) {
                    // 名单固定失败：task_execution 行已置 FAILED（= PREPARE_FAILED），不做任何成功计数。
                }
            }
        } catch (reEx: RejectedExecutionException) {
            throw DiscoveryReviewConflictException("名单固定任务启动失败，请稍后重试")
        }
        return statusAfterSubmit(batchKey)
    }

    private fun runPrepareWorker(
        batchKey: String,
        action: DiscoveryReviewAction,
        filter: DiscoveryReviewScanService.DiscoveryReviewFilter,
        note: String?,
        actor: String,
        prepareExecutionId: Long?
    ): DiscoveryReviewPrepareOutcome {
        val now = LocalDateTime.now(clock)
        val inserted = mutableListOf<ExpertDiscoveryReviewItem>()
        val seen = mutableSetOf<String>()
        scanService.scanAll(filter) { experts ->
            for (expert in experts) {
                // I-2 双保险：即使筛选语义变化，也绝不把非 NEEDS_REVIEW 拉进默认全页批准。
                if (expert.row.decision != DiscoveryReviewDecision.NEEDS_REVIEW.name) continue
                if (!seen.add(expert.docId)) continue
                val identityHash = identityHashOf(expert.docId, expert.profile)
                repository.initializeAdmission(
                    expert.docId, identityHash, expert.row.automaticStatus,
                    DiscoveryAdmissionPolicy.POLICY_VERSION, now
                )
                // 每批只读一次 admission：expectedRevision 直接取扫描批次里已读到的当前版本。
                val expectedRevision = expert.row.revision
                val snapshotJson = snapshotJson(
                    expert.level, expert.docId, expert.profile, expert.seqNo, expert.primaryTerm, now
                )
                val reasonJson = objectMapper.writeValueAsString(
                    DiscoveryReviewReasonSnapshot(
                        automaticStatus = expert.row.automaticStatus,
                        blockingReasons = expert.row.automaticReasons,
                        hints = expert.row.automaticHints,
                        policyVersion = DiscoveryAdmissionPolicy.POLICY_VERSION
                    )
                )
                val snapshotHash = DiscoveryIdentity.hash(snapshotJson)
                val itemId = repository.insertItem(
                    batchKey = batchKey,
                    expertDocId = expert.docId,
                    sourceLevel = expert.level.name,
                    identityHash = identityHash,
                    snapshotHash = snapshotHash,
                    expectedRevision = expectedRevision,
                    action = action.name,
                    state = DiscoveryReviewItemState.STAGED.name,
                    snapshotJson = snapshotJson,
                    reasonSnapshotJson = reasonJson,
                    actor = actor,
                    note = note,
                    previousItemId = null,
                    executionId = prepareExecutionId,
                    now = now
                )
                inserted += stagedItem(
                    itemId, batchKey, expert.docId, expert.level.name, identityHash, snapshotHash,
                    expectedRevision, action.name, snapshotJson, reasonJson, actor, note, prepareExecutionId, now
                )
            }
            true
        }
        // 完整扫描落地后才把整批推进 READY；中途失败则全部保持 STAGED，confirm 拒绝。
        repository.markBatchReady(batchKey)
        return DiscoveryReviewPrepareOutcome(
            batchKey = batchKey,
            phase = DiscoveryReviewBatchPhase.READY,
            batchHash = computeBatchHash(inserted),
            total = inserted.size,
            taskSuccessCount = inserted.size,
            taskFailureCount = 0,
            taskFinalStatus = "SUCCESS"
        )
    }

    // ── 确认（I-3/I-4） ──────────────────────────────────────────────────────

    /**
     * `POST /batches/{batchKey}/confirm`。
     *
     * - IDS 批次（无 PREPARE 任务）：02 语义逐字保留，逐项独立事务应用。
     * - 全页批次（有 PREPARE 任务）：只消费已存 `batchKey+hash`，24h 过期拒绝，重复确认返回同一结果，
     *   应用走 `DISCOVERY_REVIEW_APPLY` 任务 + 持久明细 CAS。
     */
    fun confirm(batchKey: String, batchHash: String?, actor: String): DiscoveryReviewConfirmResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        require(!batchHash.isNullOrBlank()) { "batchHash 必填" }
        val prepareTask = findPrepareTask(batchKey) ?: return confirmItems(batchKey, batchHash, actor)
        return confirmAllMatching(batchKey, batchHash, prepareTask, actor)
    }

    private fun confirmItems(batchKey: String, batchHash: String, actor: String): DiscoveryReviewConfirmResult {
        val items = repository.findItemsByBatch(batchKey)
        require(items.isNotEmpty()) { "批次不存在：$batchKey" }
        if (computeBatchHash(items) != batchHash) {
            throw DiscoveryReviewConflictException("批次快照已变化，请重新准备")
        }
        var applied = 0
        var stale = 0
        var failed = 0
        var skipped = 0
        val views = mutableListOf<DiscoveryReviewConfirmItemView>()
        for (item in items) {
            val state = item.state
            if (state != DiscoveryReviewItemState.STAGED.name && state != DiscoveryReviewItemState.READY.name) {
                // 终态项在重复确认时返回同一分类（幂等），不再重复应用。
                when (state) {
                    DiscoveryReviewItemState.APPLIED.name -> applied++
                    DiscoveryReviewItemState.STALE.name -> stale++
                    DiscoveryReviewItemState.FAILED.name -> failed++
                    else -> skipped++
                }
                views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, state, item.errorCode)
                continue
            }
            val level = parseLevel(item.sourceLevel)
            val snapshot = writer.readDiscoveryDocument(level, item.expertDocId)
            if (snapshot == null) {
                repository.markItemFailed(item.id, "SOURCE_MISSING", LocalDateTime.now(clock))
                failed++
                views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "SOURCE_MISSING")
                continue
            }
            val profile = writer.discoveryProfile(item.expertDocId, snapshot.source)
            val currentIdentityHash = identityHashOf(item.expertDocId, profile)
            val decision = DiscoveryReviewDecision.forAction(parseAction(item.action)).name
            when (repository.applyItem(
                itemId = item.id,
                currentIdentityHash = currentIdentityHash,
                decision = decision,
                policyVersion = DiscoveryAdmissionPolicy.POLICY_VERSION,
                now = LocalDateTime.now(clock)
            )) {
                DiscoveryReviewApplyOutcome.APPLIED, DiscoveryReviewApplyOutcome.ALREADY_APPLIED -> {
                    applied++
                    syncCandidateAfterApproval(item.id, item.expertDocId, snapshot.source, decision)
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.APPLIED.name)
                }
                DiscoveryReviewApplyOutcome.STALE -> {
                    stale++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.STALE.name, "STALE")
                }
                DiscoveryReviewApplyOutcome.SKIPPED -> {
                    skipped++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, item.state)
                }
                DiscoveryReviewApplyOutcome.NOT_FOUND -> {
                    failed++
                    views += DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "NOT_FOUND")
                }
            }
        }
        return DiscoveryReviewConfirmResult(
            batchKey = batchKey,
            total = items.size,
            applied = applied,
            stale = stale,
            failed = failed,
            skipped = skipped,
            items = views
        )
    }

    private fun confirmAllMatching(
        batchKey: String,
        batchHash: String,
        prepareTask: TaskExecution,
        actor: String
    ): DiscoveryReviewConfirmResult {
        if (prepareTask.status in ACTIVE_STATUSES) {
            throw DiscoveryReviewConflictException("名单仍在固定中，完成前不能确认")
        }
        if (prepareTask.status !in SUCCESS_STATUSES) {
            throw DiscoveryReviewConflictException("名单固定失败（PREPARE_FAILED），不能确认，请重新准备")
        }
        val storedHash = prepareResultBatchHash(prepareTask)
            ?: throw DiscoveryReviewConflictException("名单尚未固定完成，不能确认")
        val items = repository.findItemsByBatch(batchKey)
        require(items.isNotEmpty()) { "批次不存在：$batchKey" }
        if (storedHash != batchHash || computeBatchHash(items) != batchHash) {
            throw DiscoveryReviewConflictException("批次快照已变化，请重新准备")
        }
        // I-4：24h 未确认即过期（快照最早一项 created_at + 24h，无新列）。
        val createdAt = repository.findBatchCreatedAt(batchKey)
        if (createdAt != null && createdAt.plusHours(SNAPSHOT_TTL_HOURS).isBefore(LocalDateTime.now(clock))) {
            throw DiscoveryReviewConflictException("名单快照已超过 $SNAPSHOT_TTL_HOURS 小时，请重新准备")
        }
        // I-4：既有确认重复调用返回同一任务/结果，不新建执行。
        if (findApplyTask(batchKey) != null) {
            return confirmResultFromCounts(batchKey, items.size)
        }
        startApply(batchKey, actor, includeFailed = false)
        return confirmResultFromCounts(batchKey, items.size)
    }

    // ── 重试 / 取消（I-3/I-4） ────────────────────────────────────────────────

    /** `POST /batches/{key}/retry`：只在用户明确点击后重领 FAILED/未处理项；STALE 需重新 prepare。 */
    fun retryBatch(batchKey: String, actor: String): DiscoveryReviewBatchStatus {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val prepareTask = findPrepareTask(batchKey)
            ?: throw DiscoveryReviewConflictException("该批次不是全页快照，无法重试")
        if (prepareTask.status !in SUCCESS_STATUSES) {
            throw DiscoveryReviewConflictException("名单固定未完成，无法重试")
        }
        val applied = findApplyTask(batchKey)
        if (applied != null && applied.status in ACTIVE_STATUSES) {
            throw DiscoveryReviewConflictException("审核应用仍在执行中，请等待完成")
        }
        startApply(batchKey, actor, includeFailed = true)
        return batchStatus(batchKey)
    }

    /** `POST /batches/{key}/cancel`：只影响未应用项；已应用结果不倒退（I-4）。 */
    fun cancelBatch(batchKey: String, actor: String): DiscoveryReviewBatchStatus {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val counts = repository.batchStateCounts(batchKey)
        if (counts.isEmpty() && findPrepareTask(batchKey) == null && findApplyTask(batchKey) == null) {
            throw NoSuchElementException("批次不存在：$batchKey")
        }
        repository.cancelUnappliedItems(batchKey)
        return batchStatus(batchKey)
    }

    /** `GET /batches/{key}`：持久明细驱动的状态 + id 游标分页（绝不反复 offset 跳过）。 */
    fun batchStatus(batchKey: String, afterId: Long = 0L, limit: Int = STATUS_PAGE_SIZE): DiscoveryReviewBatchStatus {
        require(batchKey.isNotBlank()) { "batchKey 必填" }
        require(afterId >= 0) { "afterId 必须 >= 0" }
        val pageSize = limit.coerceIn(1, MAX_STATUS_PAGE_SIZE)
        val prepareTask = findPrepareTask(batchKey)
        val applyTask = findApplyTask(batchKey)
        val counts = repository.batchStateCounts(batchKey)
        if (counts.isEmpty() && prepareTask == null && applyTask == null) {
            throw NoSuchElementException("批次不存在：$batchKey")
        }
        val items = repository.findItemsByBatchPage(batchKey, afterId, pageSize)
        return buildBatchStatus(batchKey, counts, prepareTask, applyTask, items, afterId, pageSize)
    }

    /** 提交异步任务后立刻读状态：任务行可能尚未落库，此时明确回 `PREPARING`，绝不 404/500。 */
    private fun statusAfterSubmit(batchKey: String): DiscoveryReviewBatchStatus =
        try {
            batchStatus(batchKey)
        } catch (_: NoSuchElementException) {
            DiscoveryReviewBatchStatus(
                batchKey = batchKey,
                phase = DiscoveryReviewBatchPhase.PREPARING,
                batchHash = null,
                total = 0, applied = 0, stale = 0, failed = 0, cancelled = 0, pending = 0,
                prepareExecutionId = null, applyExecutionId = null, expiresAt = null,
                items = emptyList(), nextCursor = null
            )
        }

    // ── 应用工作者（I-3） ────────────────────────────────────────────────────

    private fun startApply(batchKey: String, actor: String, includeFailed: Boolean) {
        val tasks = requireTaskExecutions()
        val worker = requireExecutor()
        val payload = mapOf(
            "batchKey" to batchKey,
            "actor" to actor,
            "retry" to includeFailed
        )
        try {
            worker.execute {
                try {
                    var applyExecutionId: Long? = null
                    tasks.runAndRecordWithResult(
                        TASK_APPLY, "MANUAL", payload,
                        onStarted = { applyExecutionId = it }
                    ) {
                        runApplyWorker(batchKey, includeFailed, applyExecutionId)
                    }
                } catch (_: Exception) {
                    // 应用失败：逐项失败原因已按人保存，task_execution 行已置 FAILED，绝不凭受理伪造成功总数。
                }
            }
        } catch (reEx: RejectedExecutionException) {
            throw DiscoveryReviewConflictException("审核应用任务启动失败，请稍后重试")
        }
    }

    private fun runApplyWorker(
        batchKey: String,
        includeFailed: Boolean,
        applyExecutionId: Long?
    ): DiscoveryReviewApplyOutcomeSummary {
        val executionId = applyExecutionId ?: return emptyApplyOutcome(batchKey)
        while (true) {
            val claimed = repository.claimBatchItems(batchKey, executionId, APPLY_BATCH_SIZE, includeFailed)
            if (claimed.isEmpty()) break
            for (item in claimed) {
                try {
                    applyItemAndView(item)
                } catch (ex: Exception) {
                    // I-3：失败原因逐人保存，剩余项继续；只有明确重试才重新领取该项。
                    repository.markItemFailed(item.id, "APPLY_ERROR", LocalDateTime.now(clock))
                }
            }
            if (claimed.size < APPLY_BATCH_SIZE) break
        }
        return applyOutcomeFromCounts(batchKey)
    }

    /**
     * 应用单项：同一份应用逻辑服务 IDS confirm 与全页 worker（绝不另写绕过版本检查的 bulk 批准）。
     * 返回给 IDS confirm 的回显视图。
     */
    private fun applyItemAndView(item: ExpertDiscoveryReviewItem): DiscoveryReviewConfirmItemView {
        val level = parseLevel(item.sourceLevel)
        val snapshot = writer.readDiscoveryDocument(level, item.expertDocId)
        if (snapshot == null) {
            repository.markItemFailed(item.id, "SOURCE_MISSING", LocalDateTime.now(clock))
            return DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "SOURCE_MISSING")
        }
        val profile = writer.discoveryProfile(item.expertDocId, snapshot.source)
        val currentIdentityHash = identityHashOf(item.expertDocId, profile)
        val decision = DiscoveryReviewDecision.forAction(parseAction(item.action)).name
        return when (repository.applyItem(
            itemId = item.id,
            currentIdentityHash = currentIdentityHash,
            decision = decision,
            policyVersion = DiscoveryAdmissionPolicy.POLICY_VERSION,
            now = LocalDateTime.now(clock)
        )) {
            DiscoveryReviewApplyOutcome.APPLIED, DiscoveryReviewApplyOutcome.ALREADY_APPLIED -> {
                syncCandidateAfterApproval(item.id, item.expertDocId, snapshot.source, decision)
                DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.APPLIED.name)
            }
            DiscoveryReviewApplyOutcome.STALE ->
                DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.STALE.name, "STALE")
            DiscoveryReviewApplyOutcome.SKIPPED ->
                DiscoveryReviewConfirmItemView(item.id, item.expertDocId, item.state)
            DiscoveryReviewApplyOutcome.NOT_FOUND ->
                DiscoveryReviewConfirmItemView(item.id, item.expertDocId, DiscoveryReviewItemState.FAILED.name, "NOT_FOUND")
        }
    }

    // ── 撤销（I-2） ──────────────────────────────────────────────────────────

    /**
     * `POST /items/{id}/revoke`：只撤销当前仍然有效的人工决策（否则 409），撤销时**重新运行**
     * 自动校验并按结果回写当前结论（未通过回 `NEEDS_REVIEW`）。
     */
    fun revoke(itemId: Long, actor: String, note: String?): DiscoveryReviewRevokeResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val item = repository.findItem(itemId) ?: throw NoSuchElementException("审核项不存在：$itemId")
        if (item.action == DiscoveryReviewAction.REVOKE.name) {
            throw DiscoveryReviewConflictException("撤销动作不能再次撤销")
        }
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedNote != null) require(trimmedNote.length <= NOTE_MAX) { "备注最多 $NOTE_MAX 字符" }

        val level = parseLevel(item.sourceLevel)
        val snapshot = writer.readDiscoveryDocument(level, item.expertDocId)
            ?: throw IllegalStateException("ES 文档不存在，无法重新运行自动校验：${item.expertDocId}")
        val profile = writer.discoveryProfile(item.expertDocId, snapshot.source)
        val automatic = policy.evaluate(profile)
        val identityHash = identityHashOf(item.expertDocId, profile)
        val now = LocalDateTime.now(clock)
        val snapshotJson = snapshotJson(level, item.expertDocId, profile, snapshot.seqNo, snapshot.primaryTerm, now)
        val reasonJson = objectMapper.writeValueAsString(
            DiscoveryReviewReasonSnapshot(
                automaticStatus = automatic.status.name,
                blockingReasons = automatic.blockingReasons,
                hints = automatic.hints,
                policyVersion = automatic.policyVersion
            )
        )
        val (outcome, newItemId) = repository.revokeCurrent(
            currentItemId = itemId,
            actor = actor,
            note = trimmedNote,
            newDecision = automatic.status.name,
            currentIdentityHash = identityHash,
            policyVersion = automatic.policyVersion,
            sourceLevel = level.name,
            snapshotHash = DiscoveryIdentity.hash(snapshotJson),
            snapshotJson = snapshotJson,
            reasonSnapshotJson = reasonJson,
            now = now
        )
        if (outcome != DiscoveryReviewRevokeOutcome.APPLIED || newItemId == null) {
            throw DiscoveryReviewConflictException("该决策已不是当前有效决策，无法撤销")
        }
        val revision = repository.findAdmission(item.expertDocId)?.revision ?: 0L
        return DiscoveryReviewRevokeResult(newItemId, item.expertDocId, automatic.status.name, revision)
    }

    // ── 04：自动准入写入（I-1/I-4） ───────────────────────────────────────────

    /**
     * 04（I-1）：RAW 落库成功（或补全重验）后的**唯一**自动准入写入口。
     *
     * - 无结论：按 01 的自动判定插入 `AUTO_PASSED`/`NEEDS_REVIEW`/`LEGACY_APPROVED`；
     * - 同身份人工结论（`MANUAL_APPROVED`/`HOLD`/`REJECTED`）与有效历史认可（`LEGACY_APPROVED`）**不被覆盖**；
     * - 同身份自动结论：资格变化可更新为新的自动结论（不抹人工决定）；
     * - 身份变化：生成新自动结论（CAS 递增版本），旧人工历史仍留在 `review_item`。
     */
    fun recordAutomatic(
        docId: String,
        profile: ExpertProfile,
        eligibility: EligibilityResult
    ): DiscoveryReviewAdmissionOutcome {
        require(docId.isNotBlank()) { "docId 必填" }
        val automatic = policy.evaluate(profile, eligibility)
        val identityHash = identityHashOf(docId, profile)
        val now = LocalDateTime.now(clock)
        val existing = repository.findAdmission(docId)
        if (existing == null) {
            repository.initializeAdmission(docId, identityHash, automatic.status.name, automatic.policyVersion, now)
            val revision = repository.findAdmission(docId)?.revision ?: 0L
            return DiscoveryReviewAdmissionOutcome(
                docId, automatic.status.name, admittedDecision(automatic.status.name), false, false,
                revision, automatic.blockingReasons
            )
        }
        val identityChanged = existing.identityHash != identityHash
        val legacy = DiscoveryReviewDecision.LEGACY_APPROVED.name
        if (!identityChanged && (existing.decisionEnum.manual || existing.decision == legacy)) {
            return DiscoveryReviewAdmissionOutcome(
                docId, existing.decision, admittedDecision(existing.decision), existing.decisionEnum.manual,
                false, existing.revision, emptyList()
            )
        }
        if (!identityChanged && existing.decision == automatic.status.name &&
            existing.policyVersion == automatic.policyVersion
        ) {
            return DiscoveryReviewAdmissionOutcome(
                docId, existing.decision, admittedDecision(existing.decision), false, false,
                existing.revision, automatic.blockingReasons
            )
        }
        val newRevision = updateAutomaticAdmission(existing, identityHash, automatic, now)
            ?: return DiscoveryReviewAdmissionOutcome(
                docId, existing.decision, admittedDecision(existing.decision), false, identityChanged,
                existing.revision, emptyList()
            )
        return DiscoveryReviewAdmissionOutcome(
            docId, automatic.status.name, admittedDecision(automatic.status.name), false, identityChanged,
            newRevision, automatic.blockingReasons
        )
    }

    /** 04（I-1）：单文档准入解析（重验等内部读取共用同一身份语义）。 */
    fun resolveAdmission(docId: String, profile: ExpertProfile): DiscoveryReviewResolvedAdmission {
        require(docId.isNotBlank()) { "docId 必填" }
        val key = DiscoveryReviewAdmissionKey(docId, identityHashOf(docId, profile))
        return resolveAdmissionBatch(listOf(key))[docId]
            ?: DiscoveryReviewResolvedAdmission(docId, null, false, false, false, false, 0L, null)
    }

    /**
     * 04（I-1）：批量解析当前准入结论（05 消费）。一次最多 500 个 docId 的批量读取，
     * 身份不一致 → `decision == null`（原结论不适用），绝不把旧批准当成新身份的批准。
     */
    fun resolveAdmissionBatch(
        keys: List<DiscoveryReviewAdmissionKey>
    ): Map<String, DiscoveryReviewResolvedAdmission> {
        if (keys.isEmpty()) return emptyMap()
        val admissions = repository.findAdmissions(keys.map { it.docId }).associateBy { it.expertDocId }
        val items = repository.findItemsByIds(admissions.values.mapNotNull { it.decisionItemId })
            .associateBy { it.id }
        return keys.associate { key ->
            val stored = admissions[key.docId]
            if (stored == null) {
                key.docId to DiscoveryReviewResolvedAdmission(
                    key.docId, null, false, false, false, false, 0L, null
                )
            } else {
                val identityChanged = stored.identityHash != key.identityHash
                val decision = stored.decision.takeUnless { identityChanged }
                val manual = !identityChanged &&
                    (stored.decisionEnum.manual || stored.decision == DiscoveryReviewDecision.LEGACY_APPROVED.name)
                val syncError = stored.decisionItemId?.let { items[it] }
                    ?.takeIf { it.state == DiscoveryReviewItemState.APPLIED.name }
                    ?.errorCode
                key.docId to DiscoveryReviewResolvedAdmission(
                    key.docId, decision, decision != null && admittedDecision(decision), manual,
                    true, identityChanged, stored.revision, syncError
                )
            }
        }
    }

    // ── 04：候选投影（I-2） ───────────────────────────────────────────────────

    /**
     * 04（I-2）：人工批准与自动晋升共用的候选投影。真实 `_id` create；已存在候选/有效不覆盖；
     * 失败时返回独立错误码 `CANDIDATE_SYNC_FAILED`（审核已保存、投影可重试）。
     */
    fun projectApprovedCandidate(
        docId: String,
        source: Map<String, Any?>,
        decision: String
    ): DiscoveryReviewCandidateSync {
        val result = writer.projectDiscoveryCandidate(docId, source, decision)
        return when (result) {
            ExpertIndexWriterService.DiscoveryCandidateProjection.PROJECTED,
            ExpertIndexWriterService.DiscoveryCandidateProjection.ALREADY_PRESENT,
            ExpertIndexWriterService.DiscoveryCandidateProjection.APPLICATION_PRESENT,
            ExpertIndexWriterService.DiscoveryCandidateProjection.NOT_ADMITTED ->
                DiscoveryReviewCandidateSync(result.name, true, null)
            else -> DiscoveryReviewCandidateSync(result.name, false, CANDIDATE_SYNC_FAILED)
        }
    }

    /** 04（I-2）：只补投影的重试；不重签审核、不改结论，只重跑 `CANDIDATE_SYNC_FAILED` 项。 */
    fun retryBatchCandidateSync(batchKey: String, actor: String): DiscoveryReviewSyncRetryResult {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val items = repository.findItemsByBatch(batchKey)
        require(items.isNotEmpty()) { "批次不存在：$batchKey" }
        var retried = 0
        var synced = 0
        var failed = 0
        for (item in items) {
            if (item.state != DiscoveryReviewItemState.APPLIED.name || item.errorCode != CANDIDATE_SYNC_FAILED) continue
            retried++
            val snapshot = try {
                writer.readDiscoveryDocument(parseLevel(item.sourceLevel), item.expertDocId)
            } catch (_: Exception) {
                null
            }
            if (snapshot == null) {
                failed++
                continue
            }
            val decision = DiscoveryReviewDecision.forAction(parseAction(item.action)).name
            val sync = projectApprovedCandidate(item.expertDocId, snapshot.source, decision)
            if (sync.ok) {
                synced++
                clearCandidateSyncFailure(item.id)
            } else {
                failed++
            }
        }
        return DiscoveryReviewSyncRetryResult(batchKey, retried, synced, failed)
    }

    // ── 04：存量初始化（I-4） ─────────────────────────────────────────────────

    /**
     * 04（I-4）：由已登录运营的**明确初始化操作**触发，固定批次扫描 RAW 与候选/有效层的发现/待确认数据，
     * 按真实 `_id` 去重。只写 `AUTO_PASSED`/`NEEDS_REVIEW`/`LEGACY_APPROVED`（有效历史回执），
     * `INSERT IGNORE` 幂等：已有结论绝不覆盖（不扩张授权）。不删文档、不发邮件、不取消人工降级。
     */
    fun initializeExistingAdmissions(
        request: DiscoveryReviewInitializeRequest,
        actor: String
    ): DiscoveryReviewInitializeStatus {
        require(actor.isNotBlank()) { "缺少操作者身份" }
        val key = resolveBatchKey(request.requestKey)
        val levels = request.level?.let { listOf(parseLevel(it)) }
            ?: listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)
        val payload = mapOf(
            "batchKey" to key,
            "actor" to actor,
            "levels" to levels.map { it.name }
        )
        val existing = findTask(TASK_INITIALIZE, key)
        if (existing != null) {
            if (!samePayload(existing.requestPayload, payload)) {
                throw DiscoveryReviewConflictException("相同 requestKey 的初始化范围不同，请更换 requestKey")
            }
            return initializeStatus(key)
        }
        val tasks = requireTaskExecutions()
        val worker = requireExecutor()
        try {
            worker.execute {
                try {
                    var executionId: Long? = null
                    tasks.runAndRecordWithResult(TASK_INITIALIZE, "MANUAL", payload, onStarted = { executionId = it }) {
                        runInitializeWorker(key, levels)
                    }
                } catch (_: Exception) {
                    // 初始化失败：task_execution 行已置 FAILED，绝不凭受理伪造成功计数。
                }
            }
        } catch (reEx: RejectedExecutionException) {
            throw DiscoveryReviewConflictException("存量初始化任务启动失败，请稍后重试")
        }
        return statusAfterInitializeSubmit(key)
    }

    /** 04（I-4）：读取初始化状态（无新表；由任务行与结果摘要派生）。 */
    fun initializeStatus(key: String): DiscoveryReviewInitializeStatus {
        require(key.isNotBlank()) { "key 必填" }
        val task = findTask(TASK_INITIALIZE, key) ?: throw NoSuchElementException("初始化任务不存在：$key")
        val outcome = parseInitializeOutcome(task)
        return DiscoveryReviewInitializeStatus(
            key = key,
            phase = initializePhase(task.status),
            total = outcome?.total ?: 0,
            recorded = outcome?.recorded ?: 0,
            unchanged = outcome?.unchanged ?: 0,
            skipped = outcome?.skipped ?: 0,
            byLevel = outcome?.byLevel ?: emptyMap(),
            executionId = task.id,
            taskStatus = task.status
        )
    }

    /** 04（I-4）：初始化工作者（可被单测直接调用）。 */
    internal fun runInitializeWorker(
        key: String,
        levels: List<ExpertIndexLevel>
    ): DiscoveryReviewInitializeOutcome {
        val now = LocalDateTime.now(clock)
        val seen = mutableSetOf<String>()
        val byLevel = linkedMapOf<String, Int>()
        var recorded = 0
        var unchanged = 0
        var skipped = 0
        for (level in levels) {
            var levelRecorded = 0
            val filter = DiscoveryReviewScanService.DiscoveryReviewFilter(level, null, null, null, null)
            scanService.scanAll(filter) { experts ->
                val fresh = mutableListOf<DiscoveryReviewScanService.ScanExpert>()
                for (expert in experts) {
                    if (expert.docId.isBlank()) {
                        skipped++
                        continue
                    }
                    if (!seen.add(expert.docId)) {
                        skipped++
                        continue
                    }
                    fresh += expert
                }
                if (fresh.isEmpty()) return@scanAll true
                val known = repository.findAdmissions(fresh.map { it.docId }).map { it.expertDocId }.toSet()
                for (expert in fresh) {
                    if (known.contains(expert.docId)) {
                        unchanged++
                        continue
                    }
                    val automatic = policy.evaluate(expert.profile)
                    repository.initializeAdmission(
                        expert.docId, identityHashOf(expert.docId, expert.profile),
                        automatic.status.name, automatic.policyVersion, now
                    )
                    recorded++
                    levelRecorded++
                }
                true
            }
            byLevel[level.name] = levelRecorded
        }
        return DiscoveryReviewInitializeOutcome(
            key = key,
            phase = DiscoveryReviewBatchPhase.APPLIED,
            total = recorded + unchanged,
            recorded = recorded,
            unchanged = unchanged,
            skipped = skipped,
            byLevel = byLevel,
            taskSuccessCount = recorded,
            taskFailureCount = 0,
            taskFinalStatus = "SUCCESS"
        )
    }

    private fun updateAutomaticAdmission(
        existing: ExpertDiscoveryAdmission,
        identityHash: String,
        automatic: AutomaticAdmissionResult,
        now: LocalDateTime
    ): Long? {
        val jdbc = jdbcTemplate ?: return null
        val newRevision = existing.revision + 1
        val affected = jdbc.update(
            """
            UPDATE expert_discovery_admission
            SET identity_hash = ?, decision = ?, revision = ?, decision_item_id = NULL,
                policy_version = ?, checked_at = ?, updated_at = ?
            WHERE expert_doc_id = ? AND revision = ? AND identity_hash = ?
            """.trimIndent(),
            identityHash, automatic.status.name, newRevision, automatic.policyVersion,
            Timestamp.valueOf(now), Timestamp.valueOf(now),
            existing.expertDocId, existing.revision, existing.identityHash
        )
        return if (affected == 1) newRevision else null
    }

    private fun markCandidateSyncFailed(itemId: Long, errorCode: String) {
        jdbcTemplate?.update(
            "UPDATE expert_discovery_review_item SET error_code = ? WHERE id = ? AND state = ?",
            errorCode, itemId, DiscoveryReviewItemState.APPLIED.name
        )
    }

    private fun clearCandidateSyncFailure(itemId: Long) {
        jdbcTemplate?.update(
            "UPDATE expert_discovery_review_item SET error_code = NULL WHERE id = ? AND state = ? AND error_code = ?",
            itemId, DiscoveryReviewItemState.APPLIED.name, CANDIDATE_SYNC_FAILED
        )
    }

    /**
     * 04（I-2）：审核保存（APPLIED）之后单独投影候选；失败只在 `error_code` 记
     * `CANDIDATE_SYNC_FAILED`，绝不把审核本身标失败、也不重签批准。
     */
    private fun syncCandidateAfterApproval(
        itemId: Long,
        docId: String,
        source: Map<String, Any?>,
        decision: String
    ) {
        val sync = try {
            projectApprovedCandidate(docId, source, decision)
        } catch (ex: Exception) {
            // 04（I-2）回归修复：投影抛错绝不能把已 APPLIED 的审核项翻成 FAILED
            // （runApplyWorker 的 catch 会调用 markItemFailed 覆盖 APPLIED）。投影失败只以
            // 独立错误码记录，供 retryBatchCandidateSync 补投影 —— 与本节 KDoc 的承诺一致。
            markCandidateSyncFailed(itemId, CANDIDATE_SYNC_FAILED)
            return
        }
        if (sync.errorCode != null) markCandidateSyncFailed(itemId, sync.errorCode) else clearCandidateSyncFailure(itemId)
    }

    private fun admittedDecision(decision: String): Boolean =
        decision in ExpertIndexWriterService.ADMITTED_DECISIONS

    private fun initializePhase(taskStatus: String): String = when (taskStatus) {
        "RUNNING", "CANCELLING" -> DiscoveryReviewBatchPhase.PREPARING
        "SUCCESS", "PARTIAL_SUCCESS" -> DiscoveryReviewBatchPhase.APPLIED
        "CANCELLED" -> DiscoveryReviewBatchPhase.CANCELLED
        else -> DiscoveryReviewBatchPhase.PREPARE_FAILED
    }

    private fun parseInitializeOutcome(task: TaskExecution): DiscoveryReviewInitializeOutcome? {
        val summary = task.resultSummary ?: return null
        return try {
            val node = objectMapper.readTree(summary)
            val byLevel = linkedMapOf<String, Int>()
            node.path("byLevel").fields().forEachRemaining { (name, value) -> byLevel[name] = value.asInt(0) }
            DiscoveryReviewInitializeOutcome(
                key = node.path("key").asText(""),
                phase = node.path("phase").asText(DiscoveryReviewBatchPhase.APPLIED),
                total = node.path("total").asInt(0),
                recorded = node.path("recorded").asInt(0),
                unchanged = node.path("unchanged").asInt(0),
                skipped = node.path("skipped").asInt(0),
                byLevel = byLevel,
                taskSuccessCount = node.path("recorded").asInt(0),
                taskFailureCount = 0,
                taskFinalStatus = task.status
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun statusAfterInitializeSubmit(key: String): DiscoveryReviewInitializeStatus =
        try {
            initializeStatus(key)
        } catch (_: NoSuchElementException) {
            DiscoveryReviewInitializeStatus(
                key = key, phase = DiscoveryReviewBatchPhase.PREPARING,
                total = 0, recorded = 0, unchanged = 0, skipped = 0, byLevel = emptyMap(),
                executionId = null, taskStatus = "RUNNING"
            )
        }

    // ── task_execution 读取（只读；phase/hash 无新列，从此派生） ───────────────

    private fun findPrepareTask(batchKey: String): TaskExecution? =
        findTask(TASK_PREPARE, batchKey)

    private fun findApplyTask(batchKey: String): TaskExecution? =
        findTask(TASK_APPLY, batchKey)

    private fun findTask(taskType: String, batchKey: String): TaskExecution? {
        val tasks = taskExecutions ?: return null
        return tasks.listRecentByTaskType(taskType, TASK_LOOKUP_LIMIT)
            .firstOrNull { payloadBatchKey(it.requestPayload) == batchKey }
    }

    private fun payloadBatchKey(requestPayload: String?): String? {
        if (requestPayload.isNullOrBlank()) return null
        return try {
            objectMapper.readTree(requestPayload).path("batchKey").asText("").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun prepareResultBatchHash(task: TaskExecution): String? {
        val summary = task.resultSummary ?: return null
        return try {
            val node = objectMapper.readTree(summary)
            if (node.path("phase").asText("") != DiscoveryReviewBatchPhase.READY) return null
            node.path("batchHash").asText("").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun applyResultPhase(task: TaskExecution): String? {
        val summary = task.resultSummary ?: return null
        return try {
            objectMapper.readTree(summary).path("phase").asText("").takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBatchStatus(
        batchKey: String,
        counts: Map<String, Int>,
        prepareTask: TaskExecution?,
        applyTask: TaskExecution?,
        items: List<ExpertDiscoveryReviewItem>,
        afterId: Long,
        pageSize: Int
    ): DiscoveryReviewBatchStatus {
        val applied = counts[DiscoveryReviewItemState.APPLIED.name] ?: 0
        val stale = counts[DiscoveryReviewItemState.STALE.name] ?: 0
        val failed = counts[DiscoveryReviewItemState.FAILED.name] ?: 0
        val cancelled = counts[DiscoveryReviewItemState.CANCELLED.name] ?: 0
        val pending = (counts[DiscoveryReviewItemState.STAGED.name] ?: 0) +
            (counts[DiscoveryReviewItemState.READY.name] ?: 0) +
            (counts[DiscoveryReviewItemState.APPLYING.name] ?: 0)
        val phase = derivePhase(prepareTask, applyTask, cancelled, pending)
        return DiscoveryReviewBatchStatus(
            batchKey = batchKey,
            phase = phase,
            batchHash = prepareTask?.let { prepareResultBatchHash(it) },
            total = applied + stale + failed + cancelled + pending,
            applied = applied,
            stale = stale,
            failed = failed,
            cancelled = cancelled,
            pending = pending,
            prepareExecutionId = prepareTask?.id,
            applyExecutionId = applyTask?.id,
            expiresAt = repository.findBatchCreatedAt(batchKey)?.plusHours(SNAPSHOT_TTL_HOURS),
            items = items,
            nextCursor = if (items.size == pageSize) items.last().id else null
        )
    }

    private fun derivePhase(prepareTask: TaskExecution?, applyTask: TaskExecution?, cancelled: Int, pending: Int): String {
        if (prepareTask == null) {
            // IDS 快照：准备是同步的，直接可确认。
            return if (cancelled > 0) DiscoveryReviewBatchPhase.CANCELLED else DiscoveryReviewBatchPhase.READY
        }
        if (prepareTask.status in ACTIVE_STATUSES) return DiscoveryReviewBatchPhase.PREPARING
        if (prepareTask.status !in SUCCESS_STATUSES || prepareResultBatchHash(prepareTask) == null) {
            return DiscoveryReviewBatchPhase.PREPARE_FAILED
        }
        if (applyTask != null) {
            if (applyTask.status in ACTIVE_STATUSES) return DiscoveryReviewBatchPhase.APPLYING
            if (applyTask.status == "INTERRUPTED" && pending > 0) return DiscoveryReviewBatchPhase.INTERRUPTED
            return applyResultPhase(applyTask)
                ?: if (cancelled > 0) DiscoveryReviewBatchPhase.CANCELLED else DiscoveryReviewBatchPhase.APPLIED
        }
        return if (cancelled > 0) DiscoveryReviewBatchPhase.CANCELLED else DiscoveryReviewBatchPhase.READY
    }

    private fun confirmResultFromCounts(batchKey: String, total: Int): DiscoveryReviewConfirmResult {
        val counts = repository.batchStateCounts(batchKey)
        val applied = counts[DiscoveryReviewItemState.APPLIED.name] ?: 0
        val stale = counts[DiscoveryReviewItemState.STALE.name] ?: 0
        val failed = counts[DiscoveryReviewItemState.FAILED.name] ?: 0
        val cancelled = counts[DiscoveryReviewItemState.CANCELLED.name] ?: 0
        val skipped = (counts[DiscoveryReviewItemState.STAGED.name] ?: 0) +
            (counts[DiscoveryReviewItemState.READY.name] ?: 0) +
            (counts[DiscoveryReviewItemState.APPLYING.name] ?: 0)
        return DiscoveryReviewConfirmResult(
            batchKey = batchKey,
            total = maxOf(total, applied + stale + failed + cancelled + skipped),
            applied = applied,
            stale = stale,
            failed = failed,
            skipped = skipped + cancelled,
            items = emptyList()
        )
    }

    private fun emptyApplyOutcome(batchKey: String): DiscoveryReviewApplyOutcomeSummary =
        DiscoveryReviewApplyOutcomeSummary(batchKey, DiscoveryReviewBatchPhase.APPLIED, 0, 0, 0, 0, 0, 0, 0, 0, "SUCCESS")

    private fun applyOutcomeFromCounts(batchKey: String): DiscoveryReviewApplyOutcomeSummary {
        val counts = repository.batchStateCounts(batchKey)
        val applied = counts[DiscoveryReviewItemState.APPLIED.name] ?: 0
        val stale = counts[DiscoveryReviewItemState.STALE.name] ?: 0
        val failed = counts[DiscoveryReviewItemState.FAILED.name] ?: 0
        val cancelled = counts[DiscoveryReviewItemState.CANCELLED.name] ?: 0
        val pending = (counts[DiscoveryReviewItemState.STAGED.name] ?: 0) +
            (counts[DiscoveryReviewItemState.READY.name] ?: 0) +
            (counts[DiscoveryReviewItemState.APPLYING.name] ?: 0)
        val phase = if (cancelled > 0) DiscoveryReviewBatchPhase.CANCELLED else DiscoveryReviewBatchPhase.APPLIED
        val status = when {
            failed > 0 && applied > 0 -> "PARTIAL_SUCCESS"
            failed > 0 -> "FAILED"
            else -> "SUCCESS"
        }
        return DiscoveryReviewApplyOutcomeSummary(
            batchKey = batchKey,
            phase = phase,
            total = applied + stale + failed + cancelled + pending,
            applied = applied,
            stale = stale,
            failed = failed,
            cancelled = cancelled,
            pending = pending,
            taskSuccessCount = applied,
            taskFailureCount = failed + stale,
            taskFinalStatus = status
        )
    }

    // ── 纯函数辅助 ───────────────────────────────────────────────────────────

    private fun snapshotJson(
        level: ExpertIndexLevel,
        docId: String,
        profile: ExpertProfile,
        seqNo: Long,
        primaryTerm: Long,
        preparedAt: LocalDateTime
    ): String = objectMapper.writeValueAsString(
        DiscoveryReviewSnapshot(
            docId = docId,
            level = level.name,
            orcidId = profile.orcidId,
            email = profile.email,
            givenNames = profile.givenNames,
            familyNames = profile.familyNames,
            institution = profile.institution,
            country = profile.country,
            researchFields = profile.researchFields,
            disciplineCategory = profile.disciplineCategory,
            institutionEvidence = profile.institutionEvidence,
            filterResult = profile.filterResult,
            esSeqNo = seqNo,
            esPrimaryTerm = primaryTerm,
            preparedAt = preparedAt.toString()
        )
    )

    private fun stagedItem(
        id: Long, batchKey: String, docId: String, level: String, identityHash: String,
        snapshotHash: String, expectedRevision: Long, action: String, snapshotJson: String,
        reasonJson: String?, actor: String, note: String?, executionId: Long?, now: LocalDateTime
    ): ExpertDiscoveryReviewItem = ExpertDiscoveryReviewItem(
        id = id, batchKey = batchKey, expertDocId = docId, sourceLevel = level,
        identityHash = identityHash, snapshotHash = snapshotHash, expectedRevision = expectedRevision,
        action = action, state = DiscoveryReviewItemState.STAGED.name, snapshotJson = snapshotJson,
        reasonSnapshotJson = reasonJson, actor = actor, note = note, previousItemId = null,
        executionId = executionId, errorCode = null, createdAt = now, confirmedAt = null, appliedAt = null
    )

    private fun normalizedNote(note: String?, action: DiscoveryReviewAction): String? {
        val trimmed = note?.trim()?.takeIf { it.isNotEmpty() }
        if (action == DiscoveryReviewAction.REJECT) {
            require(!trimmed.isNullOrEmpty()) { "拒绝必须填写备注" }
        }
        if (trimmed != null) require(trimmed.length <= NOTE_MAX) { "备注最多 $NOTE_MAX 字符" }
        return trimmed
    }

    private fun resolveBatchKey(requestKey: String?): String {
        val trimmed = requestKey?.trim()
        if (trimmed.isNullOrEmpty()) return UUID.randomUUID().toString().replace("-", "")
        require(trimmed.length <= BATCH_KEY_MAX) { "requestKey 最长 $BATCH_KEY_MAX 字符" }
        require(BATCH_KEY_REGEX.matches(trimmed)) { "requestKey 只允许字母、数字、下划线与横线" }
        return trimmed
    }

    private fun preparePayload(
        batchKey: String,
        action: DiscoveryReviewAction,
        filter: DiscoveryReviewScanService.DiscoveryReviewFilter,
        actor: String
    ): Map<String, Any?> = mapOf(
        "batchKey" to batchKey,
        "action" to action.name,
        "filter" to mapOf(
            "level" to filter.level.name,
            "tag" to filter.tag,
            "q" to filter.q,
            "issue" to filter.issue,
            "decision" to filter.decision
        ),
        "actor" to actor
    )

    private fun samePayload(existingJson: String?, payload: Map<String, Any?>): Boolean {
        if (existingJson.isNullOrBlank()) return false
        return try {
            objectMapper.readTree(existingJson) == objectMapper.valueToTree(payload)
        } catch (_: Exception) {
            false
        }
    }

    private fun requireTaskExecutions(): TaskExecutionService =
        taskExecutions ?: error("TaskExecutionService 未装配，无法执行全页审核任务")

    private fun requireExecutor(): Executor =
        executor ?: error("审核执行线程池未装配，无法执行全页审核任务")

    private fun identityHashOf(docId: String, profile: ExpertProfile): String =
        DiscoveryReviewIdentity.hash(docId, profile.email, profile.givenNames, profile.familyNames)

    private fun computeBatchHash(items: List<ExpertDiscoveryReviewItem>): String =
        DiscoveryIdentity.hash(
            items.sortedBy { it.expertDocId }
                .joinToString("\n") { "${it.expertDocId}|${it.expectedRevision}|${it.identityHash}|${it.snapshotHash}|${it.action}" }
        )

    private fun parseLevel(raw: String?): ExpertIndexLevel {
        val value = raw?.trim()?.uppercase().orEmpty().ifEmpty { ExpertIndexLevel.RAW.name }
        return try {
            ExpertIndexLevel.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("不支持的 level：$raw")
        }
    }

    private fun parseAction(raw: String?): DiscoveryReviewAction {
        val value = raw?.trim()?.uppercase().orEmpty()
        return try {
            DiscoveryReviewAction.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("不支持的 action：$raw")
        }
    }

    companion object {
        const val MAX_BATCH_SIZE = 1000
        const val NOTE_MAX = 1000
        const val HISTORY_LIMIT = 200
        const val SCOPE_IDS = "IDS"
        const val SCOPE_ALL_MATCHING = "ALL_MATCHING"
        const val TASK_PREPARE = "DISCOVERY_REVIEW_PREPARE"
        const val TASK_APPLY = "DISCOVERY_REVIEW_APPLY"
        const val TASK_INITIALIZE = "DISCOVERY_REVIEW_INITIALIZE"
        /** 04（I-2）：审核已保存但候选投影失败 —— 独立可见、可只补投影重试。 */
        const val CANDIDATE_SYNC_FAILED = "CANDIDATE_SYNC_FAILED"
        const val SNAPSHOT_TTL_HOURS = 24L
        const val STATUS_PAGE_SIZE = 200
        const val MAX_STATUS_PAGE_SIZE = 500
        const val APPLY_BATCH_SIZE = 500
        private const val BATCH_KEY_MAX = 64
        private const val TASK_LOOKUP_LIMIT = 200
        private val ACTIVE_STATUSES = setOf("RUNNING", "CANCELLING")
        private val SUCCESS_STATUSES = setOf("SUCCESS", "PARTIAL_SUCCESS")
        private val BATCH_KEY_REGEX = Regex("^[A-Za-z0-9_-]+$")
    }
}
