package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.FulltextRequestGate
import com.weibo.talentintroduction.config.OpenAlexBudgetSnapshot
import com.weibo.talentintroduction.config.OpenAlexRequestPolicy
import com.weibo.talentintroduction.config.PIPELINE_RESERVED_RESULT_BYTES
import com.weibo.talentintroduction.discovery.domain.DiscoveryTerminalStatus
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.repository.DiscoveryPaperQueueStore
import com.weibo.talentintroduction.discovery.repository.EnqueuePageStatus
import com.weibo.talentintroduction.discovery.repository.JobRow
import com.weibo.talentintroduction.discovery.repository.LaunchOutcome
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineRow
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.discovery.repository.QueueCapacityLimits
import com.weibo.talentintroduction.discovery.repository.QueueItemUnit
import com.weibo.talentintroduction.discovery.repository.QueueJobInsert
import com.weibo.talentintroduction.discovery.repository.QueueJobStatus
import com.weibo.talentintroduction.discovery.repository.StreamCursorState
import com.weibo.talentintroduction.discovery.repository.StreamRow
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** I-7（c2）：流水线窗口的 `task_execution.task_type`（与既有深度发现任务历史同一类型）。 */
const val DISCOVERY_PIPELINE_TASK_TYPE: String = "EXPERT_DISCOVERY"

/** I-7（c2）：`launch` 的 `criteria_version`；未知版本不得消费同一份 criteria。 */
const val PIPELINE_CRITERIA_VERSION: Int = 1

/**
 * I-7/I-6（c2）：时间源 —— 窗口截止、租约、退避、日切在测试里必须可控（与 01 的 `PolicyTimeSource` 同风格）。
 */
interface PipelineTimeSource {
    fun now(): Instant

    companion object {
        val SYSTEM: PipelineTimeSource = object : PipelineTimeSource {
            override fun now(): Instant = Instant.now()
        }
    }
}

/**
 * I-7（c2）：`launch`/`resume` 的拒绝原因。**控制层必须把这两者映射为 409**：
 * - [QUERY_CONFLICT]：不同查询但仍有活跃工作/积压 —— 绝不静默丢弃在手工作；
 * - [NOT_CONFIGURED]：`resume()` 时没有任何已保存的查询。
 */
object PipelineRejectionReason {
    const val QUERY_CONFLICT = "QUERY_CONFLICT"
    const val NOT_CONFIGURED = "NOT_CONFIGURED"
}

/** I-7（c2）：`tick()` 未派发窗口的原因（不是错误，是「本 tick 什么都不该做」）。 */
object PipelineTickSkipReason {
    const val PAUSED = "PAUSED"
    const val OWNED = "OWNED"
    const val NOT_DUE = "NOT_DUE"
    const val DRAINED = "DRAINED"
    const val NO_WORK = "NO_WORK"
    const val RECOVERY = "RECOVERY"
    const val REJECTED = "REJECTED"
}

/** I-7（c2）：窗口终止原因（固定六个字面量；任务结果、进度、状态三处共用）。 */
object PipelineTerminationReason {
    const val WINDOW_END = "WINDOW_END"
    const val DAILY_BUDGET = "DAILY_BUDGET"
    const val QUEUE_FULL = "QUEUE_FULL"
    const val MANUAL_PAUSE = "MANUAL_PAUSE"
    const val SOURCE_EXHAUSTED = "SOURCE_EXHAUSTED"
    const val SOURCE_ERROR = "SOURCE_ERROR"
}

/** I-7（c2）：`launch`/`resume` 的结果；[rejection] 非空时 `applied=false`，控制层应回 409。 */
data class PipelineLaunchResult(
    val applied: Boolean,
    val pipelineId: Long,
    val state: String,
    val phase: String,
    val desiredState: String,
    val queryHash: String?,
    val executionId: Long?,
    val resumed: Boolean,
    val rejection: PipelineRejection? = null
)

data class PipelineRejection(val reason: String, val message: String)

/** I-7（c2）：`pause()` 的结果 —— 幂等持久化 `PAUSED` 之后的对外状态与在途工作数。 */
data class PipelinePauseResult(
    val pipelineId: Long,
    val state: String,
    val phase: String,
    val desiredState: String,
    val inFlightJobs: Long,
    val inFlightCollections: Long,
    val activeJobs: Long
)

/** I-7（c2）：`tick()` 的结果 —— 是否已派发窗口，以及本 tick 的判定依据。 */
data class PipelineTickResult(
    val dispatched: Boolean,
    val state: String,
    val phase: String,
    val skipReason: String?,
    val waitReason: String?,
    val nextWakeAt: Instant?,
    val ownerToken: String?
)

/** I-8（c2）：单个来源在 `status()` 里的可见状态（游标、下次尝试、逐源计数）。 */
data class DiscoverySourceStatus(
    val source: String,
    val cursorState: String,
    val cursorValue: String?,
    val nextAttemptAt: Instant?,
    val sourceError: String?,
    val activeJobs: Long,
    val failedJobs: Long,
    val queuedItems: Long,
    val processedItems: Long,
    val indexedExperts: Long,
    val duplicateExperts: Long
)

/**
 * I-8（c2）：`status()` 的完整快照。`state` 是**派生值**：`desired_state=PAUSED` 时恒为 `PAUSED`，
 * 否则等于 `phase`（暂停期间可能仍有在途收尾态）。
 */
data class DiscoveryPipelineStatus(
    val pipelineId: Long,
    val state: String,
    val phase: String,
    val desiredState: String,
    val currentExecutionId: Long?,
    val queryHash: String?,
    val criteriaVersion: Int,
    val sources: List<DiscoverySourceStatus>,
    val queuedPapers: Long,
    val queuedRecords: Long,
    val processedPapers: Long,
    val processedRecords: Long,
    val indexedExperts: Long,
    val duplicateExperts: Long,
    val failedItems: Long,
    val queueDepth: Long,
    val runningJobs: Long,
    val payloadBytes: Long,
    val reservedResultBytes: Long,
    val oldestActiveAgeSeconds: Long?,
    val waitReasons: List<String>,
    val waitReason: String?,
    val nextWakeAt: Instant?,
    val windowUntil: Instant?,
    val ownerActive: Boolean,
    val capacityPaused: Boolean,
    val rawScanDone: Boolean,
    val budget: OpenAlexBudgetSnapshot
) {
    /** I-5：当前占用 = 实际负载 + 在途结果预留。 */
    val occupiedBytes: Long get() = payloadBytes + reservedResultBytes
}

/**
 * I-7（c2）：一个窗口的最终结果。`taskSuccessCount` 沿用既有口径 = **本窗口新增专家数**；
 * `taskFinalStatus` 是六种终止原因映射到的既有任务状态。
 */
data class PipelineWindowResult(
    val pipelineId: Long,
    val queryHash: String?,
    val terminationReason: String,
    val windowStartedAt: Instant,
    val windowEndedAt: Instant,
    val processedItems: Int,
    val indexedExperts: Int,
    val duplicateExperts: Int,
    val failedItems: Int,
    val collectionPages: Int,
    val extractionDownloads: Int,
    val collectedPapers: Int,
    val collectedRecords: Int,
    val capacityBlocked: Boolean,
    val waitReasons: List<String>,
    val queueDepth: Long,
    val nextWakeAt: Instant?,
    val pendingWork: Boolean
) : TaskExecutionSummaryProvider {

    override val taskSuccessCount: Int get() = indexedExperts

    override val taskFailureCount: Int get() = failedItems

    /**
     * 窗口终止原因的**唯一**状态映射：
     * - 人工暂停 → CANCELLED；
     * - 不可恢复的全失败（没有任何处理、也没有待续跑工作）→ FAILED；
     * - 已排空且无失败 → SUCCESS；
     * - 其余（窗口结束/额度/队列满/来源错误）都还有待续跑工作 → PARTIAL_SUCCESS。
     */
    override val taskFinalStatus: String?
        get() = when (terminationReason) {
            PipelineTerminationReason.MANUAL_PAUSE -> DiscoveryTerminalStatus.CANCELLED
            PipelineTerminationReason.SOURCE_EXHAUSTED ->
                if (failedItems == 0) DiscoveryTerminalStatus.SUCCESS else DiscoveryTerminalStatus.PARTIAL_SUCCESS
            PipelineTerminationReason.SOURCE_ERROR ->
                if (processedItems == 0 && !pendingWork) {
                    DiscoveryTerminalStatus.FAILED
                } else {
                    DiscoveryTerminalStatus.PARTIAL_SUCCESS
                }
            else -> DiscoveryTerminalStatus.PARTIAL_SUCCESS
        }

    /** I-8：任务 `result_summary` / 进度的稳定 details（不写正文、不写 API Key；空值一律用空串）。 */
    fun toDetails(): Map<String, Any> = linkedMapOf(
        "pipelineId" to pipelineId,
        "queryHash" to (queryHash ?: ""),
        "terminationReason" to terminationReason,
        "processedItems" to processedItems,
        "indexedExperts" to indexedExperts,
        "duplicateExperts" to duplicateExperts,
        "failedItems" to failedItems,
        "collectionPages" to collectionPages,
        "extractionDownloads" to extractionDownloads,
        "collectedPapers" to collectedPapers,
        "collectedRecords" to collectedRecords,
        "capacityBlocked" to capacityBlocked,
        "waitReasons" to waitReasons,
        "queueDepth" to queueDepth,
        "nextWakeAt" to (nextWakeAt?.toString() ?: ""),
        "pendingWork" to pendingWork
    )
}

/** I-8（c2）：一次清理的结果。 */
data class PipelineCleanupResult(val payloadsCleared: Int, val jobsDeleted: Int)

/**
 * I-5 至 I-8（c2）：持久化采集流水线的协调者 —— 窗口生命周期、持久化控制、公平调度与状态快照。
 *
 * 职责边界（与另外两个文件严格分开）：
 * - SQL 与状态机 CAS 只在 [DiscoveryPaperQueueRepository]；
 * - 查询规范化 / 页取数 / 抽取 / 消费门禁只在 [ExpertDiscoveryService]；
 * - 本类只做**协调与持久化控制**：`launch`/`pause`/`resume`/`tick`/`status` + 窗口循环。
 *
 * 关键不变量落点：
 * - I-6：全局最多 8 个提取任务由 `pipelineFetchExecutor` 线程数保证；每目标域最多 2 个在飞请求由
 *   [FulltextRequestGate] 在提取作用域内逐跳保证；采集按来源轮转、每源每轮一页；采集/提取都在独立
 *   executor，窗口循环**绝不 join 整页**，因此慢来源不会阻塞其他来源。
 * - I-7：`tick()` 是短事务，只判定 `PAUSED`/`nextWake`/owner 后派发并立即返回；线程池拒绝保留 `QUEUED`；
 *   `pause()` 提交 `PAUSED` 并递增 `generation` 停止新领取；窗口只结束**领取**，收尾 ≤ 90 秒。
 * - I-8：窗口计数只累加「自己派发并观察到完成」的结果（不冒充全库总数）；逐源状态与 `queueDepth`
 *   来自持久化行；清理只动终态行。
 */
@Service
class DiscoveryPipelineService(
    private val repository: DiscoveryPaperQueueStore,
    private val expertDiscoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    private val properties: ExpertDiscoveryProperties,
    private val objectMapper: ObjectMapper,
    private val openAlexRequestPolicy: OpenAlexRequestPolicy,
    @Qualifier("pipelineCoordinatorExecutor") private val coordinatorExecutor: Executor,
    @Qualifier("pipelineCollectionExecutor") private val collectionExecutor: Executor,
    @Qualifier("pipelineFetchExecutor") private val fetchExecutor: Executor,
    private val time: PipelineTimeSource = PipelineTimeSource.SYSTEM
) {
    private val log = LoggerFactory.getLogger(DiscoveryPipelineService::class.java)

    /** I-7：生产入口是否启用由控制层判定（本类不据此拒绝直接调用；本期验收走显式 tick）。 */
    val enabled: Boolean get() = properties.pipelineEnabled

    // ------------------------------------------------------------------
    // I-7：持久化控制
    // ------------------------------------------------------------------

    /**
     * I-7：原子保存 `RUNNING` 与规范化查询。同查询幂等（`PAUSED` 视为显式恢复）；
     * 不同查询而仍有活跃工作/积压 → [PipelineRejectionReason.QUERY_CONFLICT]（控制层 409）。
     */
    fun launch(
        criteria: PaperSearchCriteria,
        triggeredBy: String,
        includeRawScan: Boolean
    ): PipelineLaunchResult {
        repository.ensurePipeline(now())
        val normalized = criteria.copy(cursor = null)
        val criteriaJson = objectMapper.writeValueAsString(normalized)
        val outcome = repository.launch(
            criteriaJson = criteriaJson,
            criteriaVersion = PIPELINE_CRITERIA_VERSION,
            queryHash = queryHashOf(normalized),
            // I-7：只有本次显式要求扫描才把标记置回「未扫描」；否则沿用已有标记（完成后不再重扫）。
            rawScanDone = !includeRawScan,
            now = now()
        )
        return when (outcome) {
            is LaunchOutcome.Conflict -> PipelineLaunchResult(
                applied = false,
                pipelineId = PIPELINE_ID,
                state = PipelineDesiredState.PAUSED,
                phase = PipelinePhase.QUEUED,
                desiredState = PipelineDesiredState.PAUSED,
                queryHash = null,
                executionId = null,
                resumed = false,
                rejection = PipelineRejection(PipelineRejectionReason.QUERY_CONFLICT, outcome.message)
            )
            is LaunchOutcome.Applied -> {
                val row = outcome.pipeline
                log.info(
                    "深度发现流水线{}: queryHash={}, includeRawScan={}, triggeredBy={}",
                    if (outcome.resumed) "恢复（同查询）" else "启动", row.queryHash, includeRawScan, triggeredBy
                )
                PipelineLaunchResult(
                    applied = true,
                    pipelineId = PIPELINE_ID,
                    state = derivedState(row),
                    phase = row.phase,
                    desiredState = row.desiredState,
                    queryHash = row.queryHash,
                    executionId = row.executionId,
                    resumed = outcome.resumed
                )
            }
        }
    }

    /**
     * I-7：幂等持久化 `PAUSED`（无需存在 RUNNING 的 task_execution），返回 `phase` 与在途工作数。
     * 真正从 `RUNNING` 转入时报废 `generation`，因此不会再有新的 job 领取；已领取的工作仍可保存
     * 可靠抽取结果，但不消费专家（见 [processJob]）。
     */
    fun pause(): PipelinePauseResult {
        val row = repository.pause(now())
        val stats = repository.activeJobStats()
        val at = now()
        val inFlightCollections = repository.findStreams(PIPELINE_ID)
            .count { it.leaseToken != null && it.leaseUntil != null && it.leaseUntil.isAfter(at) }
            .toLong()
        log.info(
            "深度发现流水线暂停: generation={}, 活跃 job={}, 在飞采集页={}",
            row.generation, stats.active, inFlightCollections
        )
        return PipelinePauseResult(
            pipelineId = PIPELINE_ID,
            state = derivedState(row),
            phase = row.phase,
            desiredState = row.desiredState,
            inFlightJobs = stats.running,
            inFlightCollections = inFlightCollections,
            activeJobs = stats.active
        )
    }

    /** I-7：只恢复已保存的查询；没有任何配置 → [PipelineRejectionReason.NOT_CONFIGURED]（控制层 409）。 */
    fun resume(): PipelineLaunchResult {
        val row = repository.resume(now())
            ?: return PipelineLaunchResult(
                applied = false,
                pipelineId = PIPELINE_ID,
                state = PipelineDesiredState.PAUSED,
                phase = PipelinePhase.QUEUED,
                desiredState = PipelineDesiredState.PAUSED,
                queryHash = null,
                executionId = null,
                resumed = false,
                rejection = PipelineRejection(
                    PipelineRejectionReason.NOT_CONFIGURED,
                    "尚未配置任何深度发现查询，无法恢复"
                )
            )
        return PipelineLaunchResult(
            applied = true,
            pipelineId = PIPELINE_ID,
            state = derivedState(row),
            phase = row.phase,
            desiredState = row.desiredState,
            queryHash = row.queryHash,
            executionId = row.executionId,
            resumed = true
        )
    }

    /**
     * I-7：短事务判定后把窗口循环派发到专用执行器并立即返回。
     *
     * - `desired_state=PAUSED` → 不派发（暂停跨重启/日切保持）；
     * - 别人仍持有有效的窗口属主 → 不派发（同一时刻只有一个有效 owner）；
     * - 前一个 owner 失效 → 先进入 `OWNER_RECOVERY`，不立刻重叠新窗口的外呼；
     * - `next_wake_at` 未到 → 不派发；
     * - 线程池拒绝 → 释放属主、保留 `QUEUED`，下一次 tick 重试。
     */
    fun tick(): PipelineTickResult {
        val now = now()
        repository.ensurePipeline(now)
        val pipeline = repository.findPipeline()
            ?: return PipelineTickResult(
                false, PipelinePhase.QUEUED, PipelinePhase.QUEUED,
                PipelineTickSkipReason.NO_WORK, null, null, null
            )

        if (pipeline.desiredState == PipelineDesiredState.PAUSED) {
            return PipelineTickResult(
                dispatched = false, state = derivedState(pipeline), phase = pipeline.phase,
                skipReason = PipelineTickSkipReason.PAUSED, waitReason = pipeline.waitReason,
                nextWakeAt = pipeline.nextWakeAt, ownerToken = pipeline.ownerToken
            )
        }

        val ownerAlive = pipeline.ownerToken != null &&
            pipeline.ownerUntil != null && pipeline.ownerUntil.isAfter(now)
        if (ownerAlive) {
            return PipelineTickResult(
                dispatched = false, state = derivedState(pipeline), phase = pipeline.phase,
                skipReason = PipelineTickSkipReason.OWNED, waitReason = pipeline.waitReason,
                nextWakeAt = pipeline.nextWakeAt, ownerToken = pipeline.ownerToken
            )
        }

        // I-7/OWNER_RECOVERY：旧 owner 已失效，但它的外部调用（单篇 90 秒）与 job 租约可能仍在飞，
        // 绝不立刻重叠新窗口的外呼；先等待收尾窗口结束。
        val staleOwnerUntil = pipeline.ownerUntil
        if (pipeline.ownerToken != null && staleOwnerUntil != null && !staleOwnerUntil.isAfter(now)) {
            val recoveryUntil = staleOwnerUntil.plus(OWNER_RECOVERY_DELAY)
            if (recoveryUntil.isAfter(now)) {
                repository.releaseStaleOwner(recoveryUntil, now)
                return PipelineTickResult(
                    dispatched = false, state = derivedState(pipeline), phase = PipelinePhase.WAITING,
                    skipReason = PipelineTickSkipReason.RECOVERY,
                    waitReason = PipelineWaitReason.OWNER_RECOVERY,
                    nextWakeAt = recoveryUntil, ownerToken = null
                )
            }
        }

        if (pipeline.nextWakeAt != null && pipeline.nextWakeAt.isAfter(now)) {
            return PipelineTickResult(
                dispatched = false, state = derivedState(pipeline), phase = pipeline.phase,
                skipReason = PipelineTickSkipReason.NOT_DUE, waitReason = pipeline.waitReason,
                nextWakeAt = pipeline.nextWakeAt, ownerToken = null
            )
        }

        if (!hasProgressableWork(pipeline)) {
            repository.markDrained(now)
            val drained = repository.findPipeline() ?: pipeline
            return PipelineTickResult(
                dispatched = false, state = derivedState(drained), phase = drained.phase,
                skipReason = PipelineTickSkipReason.DRAINED,
                waitReason = PipelineWaitReason.SOURCE_EXHAUSTED, nextWakeAt = null, ownerToken = null
            )
        }

        val ownerToken = UUID.randomUUID().toString()
        val claimed = repository.claimOwner(
            ownerToken = ownerToken,
            ownerUntil = now.plus(OWNER_LEASE),
            windowUntil = now.plus(properties.timeBudget),
            now = now
        )
        if (!claimed) {
            return PipelineTickResult(
                dispatched = false, state = derivedState(pipeline), phase = pipeline.phase,
                skipReason = PipelineTickSkipReason.OWNED, waitReason = pipeline.waitReason,
                nextWakeAt = null, ownerToken = null
            )
        }
        return try {
            coordinatorExecutor.execute { runWindowSafely(ownerToken, now) }
            PipelineTickResult(
                dispatched = true, state = PipelinePhase.RUNNING, phase = PipelinePhase.RUNNING,
                skipReason = null, waitReason = null, nextWakeAt = null, ownerToken = ownerToken
            )
        } catch (e: RejectedExecutionException) {
            // I-7：被拒绝即保留 QUEUED（释放属主），绝不并发开第二个窗口。
            repository.releaseOwner(
                ownerToken = ownerToken, phase = PipelinePhase.QUEUED,
                waitReason = null, nextWakeAt = now.plus(properties.pipelineTick), now = now()
            )
            PipelineTickResult(
                dispatched = false, state = derivedState(pipeline), phase = PipelinePhase.QUEUED,
                skipReason = PipelineTickSkipReason.REJECTED, waitReason = null,
                nextWakeAt = now.plus(properties.pipelineTick), ownerToken = null
            )
        }
    }

    /** I-7/I-8：`state` 是派生值 —— `PAUSED` 优先于 `phase`。 */
    private fun derivedState(row: PipelineRow): String =
        if (row.desiredState == PipelineDesiredState.PAUSED) PipelineDesiredState.PAUSED else row.phase

    /**
     * I-8：完整状态快照（下一阶段的控制层直接透出）：逐来源状态、七个计数、队列深度/字节/最老活跃时间、
     * 等待原因、下次唤醒时间，以及 01 的预算快照。
     */
    fun status(): DiscoveryPipelineStatus {
        val now = now()
        repository.ensurePipeline(now)
        val row = requireNotNull(repository.findPipeline()) { "discovery_pipeline 行缺失" }
        val streams = repository.findStreams(PIPELINE_ID)
        val counts = repository.jobStatusCountsByStream(streams.map { it.id })
        val active = repository.activeJobStats()
        val sourceStatuses = streams.map { stream ->
            val byStatus = counts[stream.id].orEmpty()
            DiscoverySourceStatus(
                source = stream.source,
                cursorState = stream.cursorState,
                cursorValue = stream.cursorValue,
                nextAttemptAt = stream.nextAttemptAt,
                sourceError = stream.sourceError,
                activeJobs = byStatus.entries.filter { it.key in QueueJobStatus.ACTIVE }.sumOf { it.value },
                failedJobs = byStatus[QueueJobStatus.FAILED] ?: 0,
                queuedItems = stream.queuedPapers + stream.queuedRecords,
                processedItems = stream.processedPapers + stream.processedRecords,
                indexedExperts = stream.indexedExperts,
                duplicateExperts = stream.duplicateExperts
            )
        }
        val waitReasons = linkedSetOf<String>()
        row.waitReason?.let { waitReasons += it }
        if (row.capacityPaused) waitReasons += PipelineWaitReason.QUEUE_FULL
        streams.mapNotNull { it.sourceError }.forEach { waitReasons += it }
        val budget = try {
            openAlexRequestPolicy.snapshot()
        } catch (e: Exception) {
            log.warn("读取 OpenAlex 预算快照失败: {}", e.message)
            emptyBudget()
        }
        return DiscoveryPipelineStatus(
            pipelineId = PIPELINE_ID,
            state = derivedState(row),
            phase = row.phase,
            desiredState = row.desiredState,
            currentExecutionId = row.executionId,
            queryHash = row.queryHash,
            criteriaVersion = row.criteriaVersion,
            sources = sourceStatuses,
            queuedPapers = row.queuedPapers,
            queuedRecords = row.queuedRecords,
            processedPapers = row.processedPapers,
            processedRecords = row.processedRecords,
            indexedExperts = row.indexedExperts,
            duplicateExperts = row.duplicateExperts,
            failedItems = row.failedItems,
            queueDepth = active.active,
            runningJobs = active.running,
            payloadBytes = row.payloadBytes,
            reservedResultBytes = row.reservedResultBytes,
            oldestActiveAgeSeconds = active.oldestCreatedAt?.let { Duration.between(it, now).seconds },
            waitReasons = waitReasons.toList(),
            waitReason = row.waitReason,
            nextWakeAt = row.nextWakeAt,
            windowUntil = row.windowUntil,
            ownerActive = row.ownerToken != null && row.ownerUntil != null && row.ownerUntil.isAfter(now),
            capacityPaused = row.capacityPaused,
            rawScanDone = row.rawScanDone,
            budget = budget
        )
    }

    /**
     * I-8：清理 —— 终态负载 7 天后可清空、去重键/状态 90 天后可删，每批 1000 条。
     * 绝不触碰非终态 job 或未消费抽取结果，也绝不重置 stream 游标或从负载重算累计指标。
     */
    fun cleanup(at: Instant = now()): PipelineCleanupResult {
        val payloads = repository.clearTerminalPayloads(
            completedBefore = at.minus(TERMINAL_PAYLOAD_RETENTION),
            batchSize = CLEANUP_BATCH_SIZE,
            now = at
        )
        val deleted = repository.deleteTerminalJobs(
            completedBefore = at.minus(TERMINAL_KEY_RETENTION),
            batchSize = CLEANUP_BATCH_SIZE,
            now = at
        )
        return PipelineCleanupResult(payloadsCleared = payloads, jobsDeleted = deleted)
    }

    // ------------------------------------------------------------------
    // I-6/I-7/I-8：窗口循环
    // ------------------------------------------------------------------

    private fun runWindowSafely(ownerToken: String, startedAt: Instant) {
        val initial = repository.findPipeline() ?: return
        val state = WindowState(
            criteria = criteriaOf(initial),
            ownerToken = ownerToken,
            generation = initial.generation,
            windowUntil = initial.windowUntil ?: startedAt.plus(properties.timeBudget),
            startedAt = startedAt
        )
        try {
            val result = taskExecutionService.runAndRecordWithResult(
                taskType = DISCOVERY_PIPELINE_TASK_TYPE,
                triggerType = "PIPELINE",
                request = mapOf("pipelineId" to PIPELINE_ID, "windowStartedAt" to startedAt.toString()),
                onStarted = { executionId ->
                    state.executionId.set(executionId)
                    repository.bindExecutionId(ownerToken, executionId, now())
                },
                block = { windowLoop(state, initial) }
            ).second
            log.info(
                "深度发现窗口结束: 原因={}, 处理={}, 新增专家={}, 重复={}, 失败={}, 队列深度={}",
                result.terminationReason, result.processedItems, result.indexedExperts,
                result.duplicateExperts, result.failedItems, result.queueDepth
            )
        } catch (e: Exception) {
            log.error("深度发现窗口异常终止: {}", e.message, e)
            runCatching { repository.markFaulted(ownerToken, PipelineWaitReason.SOURCE_ERROR, now()) }
        }
    }

    private fun windowLoop(state: WindowState, initial: PipelineRow): PipelineWindowResult {
        val criteria = state.criteria
        val queryHash = initial.queryHash
        if (criteria == null || queryHash == null) {
            log.error("流水线查询条件缺失或版本不支持（criteriaVersion={}），窗口只做故障标记", initial.criteriaVersion)
            releaseWindow(state, PipelineWaitReason.SOURCE_ERROR, now().plus(properties.pipelineTick))
            return state.snapshot(initial, PipelineTerminationReason.SOURCE_ERROR, now())
        }

        // I-1：来源流按「本源条件」建流；旧 v2 检查点只在条件完全匹配时用于首次种子。
        expertDiscoveryService.queueSourceNames(criteria).forEach { source ->
            val stream = repository.ensureStream(
                pipelineId = PIPELINE_ID,
                queryHash = queryHash,
                source = source,
                epoch = STREAM_EPOCH,
                criteriaJson = initial.criteriaJson,
                now = now()
            )
            seedLegacyCursor(stream, criteria)
        }

        // I-7：RAW 扫描只在本查询显式开启且尚未完成时运行一次。
        if (!initial.rawScanDone) {
            runRawScan(state)
        }

        var terminationReason: String? = null
        var claimRounds = 0
        while (terminationReason == null) {
            claimRounds++
            val now = now()
            val pipeline = repository.findPipeline() ?: break
            if (pipeline.desiredState == PipelineDesiredState.PAUSED || pipeline.generation != state.generation) {
                terminationReason = PipelineTerminationReason.MANUAL_PAUSE
                break
            }
            if (!now.isBefore(state.windowUntil)) {
                terminationReason = PipelineTerminationReason.WINDOW_END
                break
            }
            if (progressStore.isCancelled(DISCOVERY_PIPELINE_TASK_TYPE)) {
                terminationReason = PipelineTerminationReason.MANUAL_PAUSE
                break
            }
            val streams = repository.findStreams(PIPELINE_ID)
            state.streamsById.clear()
            streams.forEach { state.streamsById[it.id] = it }

            producePages(state, streams, pipeline)
            consumeJobs(state, streams)

            val after = repository.findPipeline() ?: break
            repository.renewOwner(state.ownerToken, now().plus(OWNER_LEASE), now())

            val streamsExhausted = streams.isNotEmpty() && streams.all { it.cursorState == StreamCursorState.EXHAUSTED }
            if (streamsExhausted && after.activeCount == 0L && state.inFlightCount() == 0) {
                terminationReason = PipelineTerminationReason.SOURCE_EXHAUSTED
                break
            }
            val budgetWait = after.waitReason?.takeIf { it in DAILY_BUDGET_REASONS }
            if (budgetWait != null && state.inFlightCount() == 0) {
                terminationReason = PipelineTerminationReason.DAILY_BUDGET
                break
            }
            if (state.capacityBlocked && state.inFlightCount() == 0) {
                terminationReason = PipelineTerminationReason.QUEUE_FULL
                break
            }
            if (!hasReadyWork(state, streams) && state.inFlightCount() == 0) {
                terminationReason = state.waitReasons.firstOrNull { it in TERMINAL_WAIT_REASONS }
                    ?: PipelineTerminationReason.WINDOW_END
                break
            }
            if (claimRounds >= MAX_LOOP_ROUNDS_WITHOUT_PROGRESS && state.inFlightCount() == 0) {
                terminationReason = PipelineTerminationReason.WINDOW_END
                break
            }
            state.completions.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
        }

        // I-7：窗口截止只停止**领取**；已派发工作允许最多 90 秒收尾。
        awaitInFlight(state, now().plus(WRAP_UP_LIMIT))

        val reason = terminationReason ?: PipelineTerminationReason.WINDOW_END
        val finalPipeline = repository.findPipeline() ?: initial
        val streamsNow = repository.findStreams(PIPELINE_ID)
        val drained = streamsNow.isNotEmpty() &&
            streamsNow.all { it.cursorState == StreamCursorState.EXHAUSTED } &&
            finalPipeline.activeCount == 0L &&
            state.inFlightCount() == 0
        val waitReason = when {
            reason == PipelineTerminationReason.SOURCE_EXHAUSTED -> PipelineWaitReason.SOURCE_EXHAUSTED
            else -> state.waitReasons.firstOrNull() ?: reason
        }
        releaseWindow(state, waitReason, if (drained) null else now().plus(properties.pipelineTick))
        if (drained) repository.markDrained(now())

        val snapshot = state.snapshot(finalPipeline, reason, now())
        publishProgress(state, reason, finalPipeline, snapshot)
        log.info(
            "深度发现窗口收尾: 等待原因={}, 待续跑={}, 队列深度={}, 采集页={}",
            waitReason, snapshot.pendingWork, snapshot.queueDepth, snapshot.collectionPages
        )
        return snapshot
    }

    /**
     * I-1/I-6：来源若反复返回**同一个** nextCursor（既不前进也不穷尽），必须变成可观测的来源错误 + 退避，
     * 而不是让窗口在同一页上空转 —— 空转会持续消耗来源的限速额度并反复重放同一页。
     * 正常重放（崩溃后重取同一页）只会发生一次，因此连续 [MAX_NO_PROGRESS_ROUNDS] 次才判定异常。
     */
    private fun guardAgainstNonAdvancingSource(state: WindowState, stream: StreamRow, page: QueuedSourcePage) {
        val entering = stream.cursorValue
        val next = page.nextCursor
        if (page.exhausted || next == null || next != entering) {
            state.noProgressRounds.remove(stream.id)
            return
        }
        val rounds = state.noProgressRounds.merge(stream.id, 1) { a, b -> a + b } ?: 1
        if (rounds >= MAX_NO_PROGRESS_ROUNDS) {
            val at = now()
            repository.recordStreamError(stream.id, NO_PROGRESS_REASON, at.plus(SOURCE_ERROR_BACKOFF), at)
            state.waitReasons.add(PipelineWaitReason.SOURCE_ERROR)
            state.noProgressRounds.remove(stream.id)
            log.warn("[{}] 连续 {} 次返回同一游标 {}，按来源错误退避（绝不原地空转）", stream.source, rounds, next)
        }
    }

    /** I-6：生产者 —— 每个可采集来源每轮**至多一页**；慢来源绝不阻塞其他来源。 */
    private fun producePages(state: WindowState, streams: List<StreamRow>, pipeline: PipelineRow) {
        if (!capacityGateOpen(pipeline)) {
            state.capacityBlocked = true
            state.waitReasons.add(PipelineWaitReason.QUEUE_FULL)
            return
        }
        val at = now()
        val available = COLLECTION_MAX_IN_FLIGHT - state.collectionInFlight.size
        if (available <= 0) return
        val ordered = rotate(streams, state.producerRotation)
        if (ordered.isNotEmpty()) state.producerRotation = (state.producerRotation + 1) % ordered.size
        var dispatched = 0
        for (stream in ordered) {
            if (dispatched >= available) break
            if (stream.cursorState != StreamCursorState.ACTIVE) continue
            if (state.collectionInFlight.containsKey(stream.id)) continue
            if (stream.nextAttemptAt != null && stream.nextAttemptAt.isAfter(at)) {
                state.waitReasons.add(stream.sourceError ?: PipelineWaitReason.SOURCE_ERROR)
                continue
            }
            val leaseToken = UUID.randomUUID().toString()
            if (!repository.claimStreamLease(stream.id, leaseToken, at.plus(STREAM_LEASE), at)) continue
            state.collectionInFlight[stream.id] = true
            try {
                collectionExecutor.execute { collectPage(state, stream, leaseToken) }
                dispatched++
            } catch (e: RejectedExecutionException) {
                state.collectionInFlight.remove(stream.id)
                repository.releaseStreamLease(stream.id, leaseToken, now())
                state.waitReasons.add(PipelineWaitReason.QUEUE_FULL)
                return
            }
        }
    }

    /** I-1：单页采集 + 整页入队（同一事务）；容量不足/租约被接管都不推进游标。 */
    private fun collectPage(state: WindowState, stream: StreamRow, leaseToken: String) {
        val criteria = state.criteria
        try {
            if (criteria == null) return
            val page = expertDiscoveryService.collectQueuePage(
                sourceName = stream.source,
                criteria = criteria,
                cursor = stream.cursorValue,
                metadataMaxBytes = properties.metadataMaxBytes
            )
            val at = now()
            if (page.deferredUntil != null) {
                // I-6：OpenAlex 额度延期只推迟本源，其他来源照常。
                val reason = page.deferredReason ?: PipelineWaitReason.DAILY_BUDGET
                repository.deferStream(stream.id, leaseToken, page.deferredUntil, reason, at)
                state.waitReasons.add(reason)
                return
            }
            if (page.errorReason != null) {
                repository.recordStreamError(stream.id, page.errorReason, at.plus(SOURCE_ERROR_BACKOFF), at)
                state.waitReasons.add(PipelineWaitReason.SOURCE_ERROR)
                return
            }
            val limits = capacityLimits()
            val inserts = ArrayList<QueueJobInsert>(page.items.size)
            var rejected = 0
            for (item in page.items) {
                if (item.rejectReason != null) {
                    // I-2/I-5：无可靠标识或过大的条目形成轻量 FAILED 诊断，不阻塞整页。
                    if (repository.insertFailedItem(stream.id, toInsert(item), item.rejectReason, limits, at)) {
                        rejected++
                    }
                } else {
                    inserts += toInsert(item)
                }
            }
            val result = repository.enqueuePage(
                streamId = stream.id,
                leaseToken = leaseToken,
                unit = page.unit,
                items = inserts,
                cursorValue = page.nextCursor,
                exhausted = page.exhausted,
                limits = limits,
                now = at
            )
            synchronized(state) {
                state.collectionPages++
                state.failedItems += rejected
                // I-8：只统计**真正新入队**的条目（重放/重复身份不冒充采集量）。
                if (page.unit == QueueItemUnit.RECORD) {
                    state.collectedRecords += result.insertedJobs
                } else {
                    state.collectedPapers += result.insertedJobs
                }
                when (result.status) {
                    EnqueuePageStatus.COMMITTED -> state.capacityBlocked = false
                    EnqueuePageStatus.CAPACITY_BLOCKED -> {
                        state.capacityBlocked = true
                        state.waitReasons.add(PipelineWaitReason.QUEUE_FULL)
                    }
                    EnqueuePageStatus.LEASE_LOST ->
                        log.warn("[{}] 采集页租约被接管，整页回滚（cursor 不推进）", stream.source)
                }
            }
            if (result.status == EnqueuePageStatus.COMMITTED) {
                guardAgainstNonAdvancingSource(state, stream, page)
            }
            when (result.status) {
                EnqueuePageStatus.CAPACITY_BLOCKED -> {
                    // I-5：容量不足 → 维持原 cursor、保存 next_attempt_at 与 QUEUE_FULL 原因，
                    // 回落到低水位且字节足够后才恢复。
                    repository.setCapacityPaused(true, at)
                    repository.deferStream(stream.id, leaseToken, at.plus(QUEUE_FULL_BACKOFF), PipelineWaitReason.QUEUE_FULL, at)
                }
                EnqueuePageStatus.LEASE_LOST -> repository.releaseStreamLease(stream.id, leaseToken, at)
                EnqueuePageStatus.COMMITTED -> Unit
            }
        } catch (e: Exception) {
            log.warn("[{}] 队列采集页失败: {}", stream.source, e.message)
            runCatching { repository.releaseStreamLease(stream.id, leaseToken, now()) }
            state.waitReasons.add(PipelineWaitReason.SOURCE_ERROR)
        } finally {
            state.collectionInFlight.remove(stream.id)
            state.notifyCompletion()
        }
    }

    /** I-3/I-6：消费者 —— 在有空闲提取槽位时领取并按来源轮转处理。 */
    private fun consumeJobs(state: WindowState, streams: List<StreamRow>) {
        var rounds = 0
        while (rounds++ < MAX_CLAIM_ATTEMPTS_PER_ROUND) {
            if (!now().isBefore(state.windowUntil)) return
            val slots = properties.pipelineFetchConcurrency - state.extractionInFlight.size
            if (slots <= 0) return
            val job = claimNextJob(state, streams) ?: return
            val at = now()
            val leaseToken = UUID.randomUUID().toString()
            if (!repository.claimJob(job.id, leaseToken, at.plus(JOB_LEASE), state.generation, at)) {
                // 被其他连接抢走或 generation 已变动：本轮不再尝试同一行。
                continue
            }
            state.extractionInFlight[job.id] = true
            try {
                fetchExecutor.execute { processJob(state, job, leaseToken) }
            } catch (e: RejectedExecutionException) {
                state.extractionInFlight.remove(job.id)
                repository.returnToPending(
                    job.id, leaseToken, job.generation, at.plus(CAPACITY_BACKOFF),
                    PipelineWaitReason.QUEUE_FULL, at
                )
                state.waitReasons.add(PipelineWaitReason.QUEUE_FULL)
                return
            }
        }
    }

    /**
     * I-6：来源轮转 + 「每 10 个高优先任务至少取 1 个最老普通任务」。
     * 来源内部优先可公开下载的工作；轮转保证前来源不会独占窗口。
     */
    private fun claimNextJob(state: WindowState, streams: List<StreamRow>): JobRow? {
        val ids = streams.map { it.id }
        if (ids.isEmpty()) return null
        val at = now()
        if (state.claimsSinceOrdinary >= ORDINARY_CLAIM_INTERVAL) {
            val ordinary = repository.nextDueOrdinaryJob(ids, at)
            if (ordinary != null) {
                state.claimsSinceOrdinary = 0
                return ordinary
            }
        }
        val ordered = rotate(streams, state.consumerRotation)
        for (stream in ordered) {
            val job = repository.nextDueJob(listOf(stream.id), priorityFirst = true, now = at)
            if (job != null) {
                state.claimsSinceOrdinary = if (job.priority == 0) 0 else state.claimsSinceOrdinary + 1
                state.consumerRotation = (state.consumerRotation + 1) % ordered.size
                return job
            }
        }
        return null
    }

    /**
     * I-3/I-4：单条工作的抽取与消费。
     *
     * 已保存的抽取结果（`extraction_json` 非空）**直接消费、不再下载**；消费前重新读取 pipeline 的
     * `generation` —— 人工暂停之后不再消费专家，只把可靠抽取结果退回 `PENDING`。
     */
    private fun processJob(state: WindowState, job: JobRow, leaseToken: String) {
        try {
            val stream = state.streamsById[job.streamId] ?: repository.findStreamById(job.streamId)
            if (stream == null) {
                repository.completeJob(
                    job.id, leaseToken, job.generation, QueueJobStatus.FAILED,
                    job.attempts, now(), QUEUE_PAYLOAD_UNREADABLE, now()
                )
                synchronized(state) { state.failedItems++ }
                return
            }
            val envelope = QueuedItemEnvelope(
                sourceName = stream.source,
                itemKey = job.itemKey,
                identityQuality = job.identityQuality,
                unit = job.unit,
                payloadVersion = job.payloadVersion,
                payloadJson = job.metadataJson,
                payloadBytes = job.payloadBytes,
                publiclyDownloadable = job.priority > 0
            )
            var extractionJson = job.extractionJson
            if (extractionJson.isNullOrEmpty()) {
                val criteria = state.criteria
                if (criteria == null) {
                    releaseWindow(state, PipelineWaitReason.SOURCE_ERROR, null)
                    return
                }
                when (val extraction = expertDiscoveryService.extractQueuedItem(
                    envelope = envelope,
                    criteria = criteria,
                    perHostConcurrency = properties.perHostConcurrency,
                    extractionMaxBytes = properties.extractionMaxBytes
                )) {
                    is QueuedItemExtraction.Extracted -> {
                        val bytes = extraction.extractionJson.toByteArray(Charsets.UTF_8).size.toLong()
                        val saved = repository.saveExtraction(
                            jobId = job.id, leaseToken = leaseToken, generation = job.generation,
                            extractionJson = extraction.extractionJson, extractionBytes = bytes, now = now()
                        )
                        if (!saved) {
                            // 租约/generation 已失效（已被重新领取）：绝不消费。
                            log.info("job {} 抽取结果未保存（租约/generation 失效），留给下一次尝试", job.id)
                            return
                        }
                        extractionJson = extraction.extractionJson
                        synchronized(state) { state.extractionDownloads += extraction.httpRequests }
                    }
                    QueuedItemExtraction.HostBusy -> {
                        // I-6：域名许可不可得 —— 延期，不消耗 attempts、不记永久失败。
                        val at = now()
                        repository.returnToPending(
                            job.id, leaseToken, job.generation, at.plus(HOST_BUSY_BACKOFF),
                            FulltextRequestGate.HOST_BUSY, at
                        )
                        state.waitReasons.add(FulltextRequestGate.HOST_BUSY)
                        return
                    }
                    QueuedItemExtraction.TooLarge -> {
                        val at = now()
                        repository.completeJob(
                            job.id, leaseToken, job.generation, QueueJobStatus.FAILED,
                            job.attempts, at, QUEUE_EXTRACTION_TOO_LARGE, at
                        )
                        synchronized(state) { state.failedItems++ }
                        return
                    }
                    is QueuedItemExtraction.Failed -> {
                        handleRetryableFailure(state, job, leaseToken, extraction.reason, extraction.retryable)
                        return
                    }
                }
            }

            // I-3 唯一例外：人工暂停（generation 递增）后**不再消费专家**，但上面刚保存的可靠抽取结果保留，
            // job 退回 PENDING —— 不 complete、不消耗 attempts，恢复后由新的 generation 重新领取。
            val current = repository.findPipeline()
            if (current == null || current.generation != job.generation) {
                val at = now()
                repository.returnToPending(
                    job.id, leaseToken, job.generation, at.plus(PAUSE_BACKOFF),
                    PipelineWaitReason.MANUAL_PAUSE, at
                )
                state.waitReasons.add(PipelineWaitReason.MANUAL_PAUSE)
                return
            }

            val executionId = state.executionId.get().takeIf { it > 0L }
            val consumption = expertDiscoveryService.consumeQueuedItem(envelope, extractionJson!!, executionId)
            val at = now()
            if (consumption.unrecoverableReason != null) {
                repository.completeJob(
                    job.id, leaseToken, job.generation, QueueJobStatus.FAILED,
                    job.attempts, at, consumption.unrecoverableReason, at
                )
                synchronized(state) { state.failedItems++ }
                return
            }
            if (consumption.succeeded) {
                val outcome = repository.completeJobWithExperts(
                    jobId = job.id, leaseToken = leaseToken, generation = job.generation,
                    indexedExperts = consumption.indexedExperts,
                    duplicateExperts = consumption.duplicateExperts,
                    attempts = job.attempts, nextAttemptAt = at, now = at
                )
                if (outcome.applied) {
                    synchronized(state) {
                        state.processedItems++
                        state.indexedExperts += consumption.indexedExperts
                        state.duplicateExperts += consumption.duplicateExperts
                        consumption.failureReasons.forEach { (reason, count) ->
                            state.failureReasons.merge(reason, count) { a, b -> a + b }
                        }
                    }
                }
                return
            }
            val reason = when {
                consumption.rawWriteFailed -> "RAW_WRITE_FAILED"
                consumption.enqueueFailed -> "ENRICHMENT_ENQUEUE_FAILED"
                consumption.dedupFailed -> "DEDUP_LOOKUP_FAILED"
                else -> "CONSUME_INCOMPLETE"
            }
            handleRetryableFailure(state, job, leaseToken, reason, retryable = true)
        } catch (e: Exception) {
            log.warn("job {} 处理异常: {}", job.id, e.message)
            runCatching { handleRetryableFailure(state, job, leaseToken, "PROCESSING_EXCEPTION", retryable = true) }
        } finally {
            state.extractionInFlight.remove(job.id)
            state.notifyCompletion()
        }
    }

    /** I-3：可重试失败最多 5 次、30s/2m/10m/30m 退避；不可重试或超限直接 FAILED 并给出原因。 */
    private fun handleRetryableFailure(
        state: WindowState,
        job: JobRow,
        leaseToken: String,
        reason: String,
        retryable: Boolean
    ) {
        val attempts = job.attempts + 1
        val at = now()
        if (!retryable || attempts >= MAX_PROCESSING_ATTEMPTS) {
            repository.completeJob(
                job.id, leaseToken, job.generation, QueueJobStatus.FAILED, attempts, at, reason, at
            )
            synchronized(state) {
                state.failedItems++
                state.failureReasons.merge(reason, 1) { a, b -> a + b }
            }
            return
        }
        val backoff = RETRY_BACKOFFS[minOf(attempts - 1, RETRY_BACKOFFS.size - 1)]
        repository.scheduleRetry(
            jobId = job.id, leaseToken = leaseToken, generation = job.generation,
            attempts = attempts, nextAttemptAt = at.plus(backoff), lastError = reason, now = at
        )
        synchronized(state) { state.failureReasons.merge(reason, 1) { a, b -> a + b } }
    }

    /** I-7：RAW 扫描只在本查询显式开启且尚未完成时运行一次，完成后持久化标记。 */
    private fun runRawScan(state: WindowState) {
        log.info("深度发现窗口执行 RAW 晋升扫描与邮箱补全（本查询首次）")
        progressStore.update(
            DISCOVERY_PIPELINE_TASK_TYPE,
            TaskProgress(
                taskType = DISCOVERY_PIPELINE_TASK_TYPE, status = "RUNNING", batchNumber = 0,
                processedCount = 0, totalCount = 0, message = "正在扫描 RAW 索引并晋升..."
            ),
            state.executionId.get().takeIf { it > 0L }
        )
        try {
            expertDiscoveryService.runRawScan()
            repository.markRawScanDone(state.ownerToken, now())
        } catch (e: Exception) {
            log.warn("RAW 扫描失败，本次不标记完成（允许安全重放）: {}", e.message)
        }
    }

    /** I-1：旧 v2 游标只作为**首次种子**，且只在完整条件可确认匹配时使用。 */
    private fun seedLegacyCursor(stream: StreamRow, criteria: PaperSearchCriteria) {
        if (stream.cursorValue != null || stream.cursorState != StreamCursorState.ACTIVE) return
        val seeded = expertDiscoveryService.queueLegacySeedCursor(stream.source, criteria) ?: return
        if (repository.seedStreamCursorIfPristine(stream.id, seeded, now())) {
            log.info("[{}] 旧 v2 检查点条件完全匹配，用于首次种子（旧行不改）", stream.source)
        }
    }

    private fun hasProgressableWork(pipeline: PipelineRow): Boolean {
        if (pipeline.activeCount > 0) return true
        val streams = repository.findStreams(PIPELINE_ID)
        if (streams.isEmpty()) return true
        return streams.any { it.cursorState == StreamCursorState.ACTIVE }
    }

    private fun hasReadyWork(state: WindowState, streams: List<StreamRow>): Boolean {
        val at = now()
        val collectable = streams.any { stream ->
            stream.cursorState == StreamCursorState.ACTIVE &&
                !state.collectionInFlight.containsKey(stream.id) &&
                (stream.nextAttemptAt == null || !stream.nextAttemptAt.isAfter(at))
        }
        if (collectable) return true
        return repository.nextDueJob(streams.map { it.id }, priorityFirst = true, now = at) != null
    }

    private fun capacityGateOpen(pipeline: PipelineRow): Boolean =
        !pipeline.capacityPaused || pipeline.activeCount <= properties.queueLowWater

    private fun capacityLimits() = QueueCapacityLimits(
        highWater = properties.queueHighWater.toLong(),
        maxBytes = properties.queueMaxBytes,
        reservedResultBytes = PIPELINE_RESERVED_RESULT_BYTES
    )

    private fun toInsert(item: QueuedItemEnvelope) = QueueJobInsert(
        itemKey = item.itemKey,
        identityQuality = item.identityQuality,
        unit = item.unit,
        payloadVersion = item.payloadVersion,
        publiclyDownloadable = item.publiclyDownloadable,
        metadataJson = item.payloadJson,
        payloadBytes = item.payloadBytes
    )

    private fun criteriaOf(row: PipelineRow): PaperSearchCriteria? {
        val json = row.criteriaJson ?: return null
        if (row.criteriaVersion != PIPELINE_CRITERIA_VERSION) return null
        return try {
            objectMapper.readValue(json, PaperSearchCriteria::class.java)
        } catch (e: Exception) {
            log.warn("流水线 criteria_json 无法解析（版本 {}）: {}", row.criteriaVersion, e.message)
            null
        }
    }

    private fun queryHashOf(criteria: PaperSearchCriteria): String {
        val canonical = DiscoveryCheckpointCodec.canonicalCriteria(criteria)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            builder.append(HEX[(byte.toInt() shr 4) and 0x0F])
            builder.append(HEX[byte.toInt() and 0x0F])
        }
        return builder.toString()
    }

    private fun publishProgress(
        state: WindowState,
        reason: String,
        pipeline: PipelineRow,
        snapshot: PipelineWindowResult
    ) {
        val message = when (reason) {
            PipelineTerminationReason.WINDOW_END -> "窗口结束，等待续跑"
            PipelineTerminationReason.MANUAL_PAUSE -> "人工暂停，已保存在手工作，等待恢复"
            PipelineTerminationReason.QUEUE_FULL -> "队列达到容量上限，窗口结束，等待容量回落后续跑"
            PipelineTerminationReason.DAILY_BUDGET -> "OpenAlex 日额度等待，窗口结束，等待额度恢复"
            PipelineTerminationReason.SOURCE_ERROR -> "来源错误，窗口结束，等待下次重试"
            else -> "所有来源已穷尽"
        }
        progressStore.update(
            DISCOVERY_PIPELINE_TASK_TYPE,
            TaskProgress(
                taskType = DISCOVERY_PIPELINE_TASK_TYPE,
                status = DiscoveryTerminalStatus.toProgressStatus(snapshot.taskFinalStatus ?: DiscoveryTerminalStatus.PARTIAL_SUCCESS),
                batchNumber = state.collectionPages,
                processedCount = state.processedItems.toLong(),
                totalCount = pipeline.queuedPapers + pipeline.queuedRecords,
                message = message,
                details = snapshot.toDetails()
            ),
            state.executionId.get().takeIf { it > 0L }
        )
    }

    private fun releaseWindow(state: WindowState, waitReason: String, nextWakeAt: Instant?) {
        val at = now()
        val pipeline = repository.findPipeline()
        val phase = when {
            pipeline?.desiredState == PipelineDesiredState.PAUSED -> PipelinePhase.WAITING
            waitReason == PipelineWaitReason.SOURCE_EXHAUSTED && pipeline?.activeCount == 0L -> PipelinePhase.DRAINED
            else -> PipelinePhase.WAITING
        }
        repository.releaseOwner(state.ownerToken, phase, waitReason, nextWakeAt, at)
    }

    private fun awaitInFlight(state: WindowState, limit: Instant) {
        while (state.inFlightCount() > 0 && time.now().isBefore(limit)) {
            state.completions.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS)
            repository.renewOwner(state.ownerToken, time.now().plus(OWNER_LEASE), time.now())
        }
    }

    private fun <T> rotate(items: List<T>, offset: Int): List<T> {
        if (items.isEmpty()) return items
        val start = ((offset % items.size) + items.size) % items.size
        return items.drop(start) + items.take(start)
    }

    private fun now(): Instant = time.now()

    private fun emptyBudget() = OpenAlexBudgetSnapshot(
        accountScope = "unknown", resetAt = null, officialLimitCredits = null, officialRemainingCredits = null,
        confirmedSpentCredits = 0, reservedCredits = 0, effectiveRemainingCredits = 0,
        enrichmentReserveCredits = 0, lastSyncedAt = null, deferredReason = null, retryAt = null
    )

    /** I-6/I-7/I-8：窗口循环的可变状态（协调线程与 worker 完成回调共享，计数一律在 `synchronized(this)` 内）。 */
    private class WindowState(
        val criteria: PaperSearchCriteria?,
        val ownerToken: String,
        val generation: Long,
        val windowUntil: Instant,
        val startedAt: Instant
    ) {
        val streamsById = ConcurrentHashMap<Long, StreamRow>()
        val extractionInFlight = ConcurrentHashMap<Long, Boolean>()
        val collectionInFlight = ConcurrentHashMap<Long, Boolean>()
        val completions = ArrayBlockingQueue<Int>(COMPLETION_QUEUE_CAPACITY)
        val executionId = AtomicLong(0L)
        val waitReasons = ConcurrentLinkedQueue<String>()
        val failureReasons = ConcurrentHashMap<String, Int>()
        val noProgressRounds = ConcurrentHashMap<Long, Int>()

        @Volatile
        var capacityBlocked: Boolean = false

        @Volatile
        var producerRotation: Int = 0

        @Volatile
        var consumerRotation: Int = 0

        @Volatile
        var claimsSinceOrdinary: Int = 0

        var collectionPages: Int = 0
        var collectedPapers: Int = 0
        var collectedRecords: Int = 0
        var processedItems: Int = 0
        var indexedExperts: Int = 0
        var duplicateExperts: Int = 0
        var failedItems: Int = 0
        var extractionDownloads: Int = 0

        fun inFlightCount(): Int = extractionInFlight.size + collectionInFlight.size

        fun notifyCompletion() {
            completions.offer(1)
        }

        fun snapshot(pipeline: PipelineRow, reason: String, endedAt: Instant): PipelineWindowResult = PipelineWindowResult(
            pipelineId = PIPELINE_ID,
            queryHash = pipeline.queryHash,
            terminationReason = reason,
            windowStartedAt = startedAt,
            windowEndedAt = endedAt,
            processedItems = processedItems,
            indexedExperts = indexedExperts,
            duplicateExperts = duplicateExperts,
            failedItems = failedItems,
            collectionPages = collectionPages,
            extractionDownloads = extractionDownloads,
            collectedPapers = collectedPapers,
            collectedRecords = collectedRecords,
            capacityBlocked = capacityBlocked,
            waitReasons = waitReasons.toList().distinct(),
            queueDepth = pipeline.activeCount,
            nextWakeAt = pipeline.nextWakeAt,
            pendingWork = pipeline.activeCount > 0 || capacityBlocked
        )

        companion object {
            const val COMPLETION_QUEUE_CAPACITY = 256
        }
    }

    companion object {
        private const val PIPELINE_ID = 1L

        /** I-1：本期 epoch 恒为 1 —— `EXHAUSTED` 不因重启/次日/新窗口/重新 launch 重置。 */
        private const val STREAM_EPOCH = 1L

        private val HEX = "0123456789abcdef".toCharArray()

        /** I-7：窗口属主租约 30 秒，窗口循环每轮心跳续租。 */
        private val OWNER_LEASE: Duration = Duration.ofSeconds(30)

        /** I-6：来源采集页租约 120 秒（覆盖一次外部取数）。 */
        private val STREAM_LEASE: Duration = Duration.ofSeconds(120)

        /** I-3：job 租约 120 秒。 */
        private val JOB_LEASE: Duration = Duration.ofSeconds(120)

        /** I-7：旧 owner 失效后先等待收尾窗口（覆盖 90 秒外呼与在飞租约）再开新窗口。 */
        private val OWNER_RECOVERY_DELAY: Duration = Duration.ofSeconds(90)

        /** I-7：窗口截止后最多 90 秒收尾已派发工作。 */
        private val WRAP_UP_LIMIT: Duration = Duration.ofSeconds(90)

        /** I-6：采集池同时最多压在途的页数（线程 4 + 队列 8）。 */
        private const val COLLECTION_MAX_IN_FLIGHT = 12

        /** I-6：消费轮转 —— 每 10 个高优先任务至少取 1 个最老普通任务。 */
        private const val ORDINARY_CLAIM_INTERVAL = 10

        /** I-3：可重试失败最多 5 次；30s / 2m / 10m / 30m 退避。 */
        private const val MAX_PROCESSING_ATTEMPTS = 5
        private val RETRY_BACKOFFS: List<Duration> = listOf(
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30)
        )

        /** I-1：来源级错误的下一次尝试退避（不冒充穷尽）。 */
        private val SOURCE_ERROR_BACKOFF: Duration = Duration.ofMinutes(5)

        /** I-6：域名许可不可得时的延期退避（不算失败、不消耗 attempts）。 */
        private val HOST_BUSY_BACKOFF: Duration = Duration.ofSeconds(30)

        /** I-5：容量不足时同一页的重放退避。 */
        private val QUEUE_FULL_BACKOFF: Duration = Duration.ofSeconds(15)

        /** I-3：容量等待的退避（不消耗 attempts）。 */
        private val CAPACITY_BACKOFF: Duration = Duration.ofSeconds(10)

        /** I-3：人工暂停期间退回 PENDING 的等待（恢复后立即可领）。 */
        private val PAUSE_BACKOFF: Duration = Duration.ofSeconds(1)

        /** I-8：终态负载保留 7 天、去重键/状态保留 90 天，每批 1000 条。 */
        private val TERMINAL_PAYLOAD_RETENTION: Duration = Duration.ofDays(7)
        private val TERMINAL_KEY_RETENTION: Duration = Duration.ofDays(90)
        private const val CLEANUP_BATCH_SIZE = 1000

        /** 窗口循环的轮询上限；有完成事件时提前唤醒，因此不会空转。 */
        private val POLL_INTERVAL: Duration = Duration.ofMillis(200)

        /** 单轮消费的领取尝试上限（同一行被抢走时不会无限重试）。 */
        private const val MAX_CLAIM_ATTEMPTS_PER_ROUND = 64

        /** 窗口迭代上限的安全阀（正常终止条件都先命中；避免任何异常状态下空转）。 */
        private const val MAX_LOOP_ROUNDS_WITHOUT_PROGRESS = 10_000

        /** I-1/I-6：来源连续多次返回同一游标即视为来源异常（NO_PROGRESS + 退避）。 */
        private const val MAX_NO_PROGRESS_ROUNDS = 3
        private const val NO_PROGRESS_REASON = "NO_PROGRESS"

        /** I-4/I-6：以「等待原因」结束窗口的那批原因。 */
        private val DAILY_BUDGET_REASONS = setOf(
            PipelineWaitReason.DAILY_BUDGET,
            PipelineWaitReason.BUDGET_SYNC,
            PipelineWaitReason.RATE_LIMIT,
            PipelineWaitReason.ENRICHMENT_RESERVE
        )
        private val TERMINAL_WAIT_REASONS = setOf(
            PipelineWaitReason.DAILY_BUDGET,
            PipelineWaitReason.BUDGET_SYNC,
            PipelineWaitReason.RATE_LIMIT,
            PipelineWaitReason.ENRICHMENT_RESERVE,
            PipelineWaitReason.QUEUE_FULL,
            PipelineWaitReason.SOURCE_ERROR,
            // 人工暂停同样是窗口的终止原因（I-7）：暂停后本窗口不再领取，只保存可靠抽取结果。
            PipelineWaitReason.MANUAL_PAUSE
        )
    }
}
