package com.weibo.talentintroduction.discovery.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * I-7：pipeline 单例（id=1）的 `desired_state` 取值。只有它表达「人是否希望它跑」。
 */
object PipelineDesiredState {
    const val PAUSED = "PAUSED"
    const val RUNNING = "RUNNING"
}

/** I-7：pipeline 的 `phase` 取值（`state` 对外派生：`PAUSED` 优先于 `phase`）。 */
object PipelinePhase {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val WAITING = "WAITING"
    const val DRAINED = "DRAINED"
    const val FAULTED = "FAULTED"
}

/** I-7：统一终止/等待原因（下游 status/任务结果共用同一批字面量）。 */
object PipelineWaitReason {
    const val DAILY_BUDGET = "DAILY_BUDGET"
    const val QUEUE_FULL = "QUEUE_FULL"
    const val RATE_LIMIT = "RATE_LIMIT"
    const val ENRICHMENT_RESERVE = "ENRICHMENT_RESERVE"
    const val BUDGET_SYNC = "BUDGET_SYNC"
    const val OWNER_RECOVERY = "OWNER_RECOVERY"
    const val SOURCE_ERROR = "SOURCE_ERROR"
    const val MANUAL_PAUSE = "MANUAL_PAUSE"
    const val WINDOW_END = "WINDOW_END"
    const val SOURCE_EXHAUSTED = "SOURCE_EXHAUSTED"
}

/** I-1：stream 的采集位置状态；`EXHAUSTED` 不因重启/次日/新窗口重置。 */
object StreamCursorState {
    const val ACTIVE = "ACTIVE"
    const val EXHAUSTED = "EXHAUSTED"
}

/** I-3：job 状态机取值。 */
object QueueJobStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val SUCCEEDED = "SUCCEEDED"
    const val RETRY_WAIT = "RETRY_WAIT"
    const val FAILED = "FAILED"

    /** I-5：活跃 = 仍占用容量且尚未有终态的三种状态。 */
    val ACTIVE = setOf(PENDING, RUNNING, RETRY_WAIT)
}

/** I-2：`unit` 只有两个合法值，绝不把 ORCID 记录冒充论文。 */
object QueueItemUnit {
    const val PAPER = "PAPER"
    const val RECORD = "RECORD"
}

/** I-2：身份来源；`PAYLOAD_HASH` 表示「没有可靠学科标识，按规范负载哈希去重」。 */
object QueueIdentityQuality {
    const val DOI = "DOI"
    const val PMCID = "PMCID"
    const val PMID = "PMID"
    const val ORCID = "ORCID"
    const val PAYLOAD_HASH = "PAYLOAD_HASH"
}

/** I-5：入队事务需要知道的容量上限（由调用方从配置传入，repository 不读配置）。 */
data class QueueCapacityLimits(
    /** 活跃 job 数上限（高水位）。 */
    val highWater: Long,
    /** 总字节上限（实际负载 + 在途结果预留）。 */
    val maxBytes: Long,
    /** 每条尚未抽取的活跃 job 预留的结果空间。 */
    val reservedResultBytes: Long
)

/** I-7：pipeline 单例的只读快照（字段名即下游 status 契约）。 */
data class PipelineRow(
    val desiredState: String,
    val phase: String,
    val criteriaJson: String?,
    val criteriaVersion: Int,
    val queryHash: String?,
    val generation: Long,
    val ownerToken: String?,
    val ownerUntil: Instant?,
    val executionId: Long?,
    val windowUntil: Instant?,
    val nextWakeAt: Instant?,
    val waitReason: String?,
    val rawScanDone: Boolean,
    val queuedPapers: Long,
    val queuedRecords: Long,
    val processedPapers: Long,
    val processedRecords: Long,
    val indexedExperts: Long,
    val duplicateExperts: Long,
    val failedItems: Long,
    val activeCount: Long,
    val payloadBytes: Long,
    val reservedResultBytes: Long,
    val capacityPaused: Boolean
) {
    /** I-5：当前占用 = 实际负载 + 在途结果预留。 */
    val occupiedBytes: Long get() = payloadBytes + reservedResultBytes
}

/** I-1/I-6：单个来源的采集位置与逐源计数。 */
data class StreamRow(
    val id: Long,
    val pipelineId: Long,
    val queryHash: String,
    val source: String,
    val epoch: Long,
    val criteriaJson: String?,
    val cursorValue: String?,
    val cursorState: String,
    val nextAttemptAt: Instant?,
    val leaseToken: String?,
    val leaseUntil: Instant?,
    val sourceError: String?,
    val queuedPapers: Long,
    val queuedRecords: Long,
    val processedPapers: Long,
    val processedRecords: Long,
    val indexedExperts: Long,
    val duplicateExperts: Long,
    val failedItems: Long
)

/** I-2：入队一条工作的完整负载（身份 + 版本 + 序列化元数据 + 字节数）。 */
data class QueueJobInsert(
    val itemKey: String,
    val identityQuality: String,
    val unit: String,
    val payloadVersion: Int,
    /** I-6：可公开下载 = 消费者在来源内优先处理。 */
    val publiclyDownloadable: Boolean,
    val metadataJson: String,
    val payloadBytes: Long
)

/** I-3：一条已领取/待处理的工作行。 */
data class JobRow(
    val id: Long,
    val streamId: Long,
    val itemKey: String,
    val identityQuality: String,
    val unit: String,
    val payloadVersion: Int,
    val priority: Int,
    val metadataJson: String,
    val extractionJson: String?,
    val payloadBytes: Long,
    val reservedResultBytes: Long,
    val status: String,
    val attempts: Int,
    val nextAttemptAt: Instant,
    val leaseToken: String?,
    val leaseUntil: Instant?,
    val generation: Long,
    val lastError: String?
)

/** I-1/I-5：整页入队的结果；`cursorAdvanced=false` 表示游标未推进（下轮重放同一页）。 */
data class EnqueuePageResult(
    val status: EnqueuePageStatus,
    val insertedJobs: Int = 0,
    val duplicateJobs: Int = 0,
    val activeCount: Long = 0,
    val occupiedBytes: Long = 0
)

/** I-1/I-5：整页入队的三种结局。 */
enum class EnqueuePageStatus {
    /** 整页已入队且 cursor 已推进（同一事务）。 */
    COMMITTED,

    /** 容量（数量或字节任一）不足：整页不入队、cursor 不推进。 */
    CAPACITY_BLOCKED,

    /** stream 租约已被他人接管：本页绝不写入、cursor 不推进。 */
    LEASE_LOST
}

/** I-8：活跃 job 统计（`queueDepth` / 最老活跃时间）。 */
data class ActiveJobStats(val active: Long, val running: Long, val oldestCreatedAt: Instant?)

/** I-3/I-8：一次终态 CAS 的结果；三个计数各自由对应 CAS 保证最多累加一次。 */
data class CompletionOutcome(val applied: Boolean, val failedItems: Int, val indexedExperts: Int)

/** I-7：`launch` 的两种结局（冲突由控制层映射为 409）。 */
sealed class LaunchOutcome {
    data class Applied(val pipeline: PipelineRow, val resumed: Boolean) : LaunchOutcome()
    data class Conflict(val message: String) : LaunchOutcome()
}

/**
 * I-1 至 I-8：持久化采集队列的**存储契约** —— 三张 V133 表的全部读写与状态机。
 *
 * 生产唯一实现是 [DiscoveryPaperQueueRepository]（JdbcTemplate + 行锁 + 条件 CAS）；
 * 独立出接口的理由与 01 的 `OpenAlexBudgetStore` 相同：窗口协调者（[com.weibo.talentintroduction.discovery.service.DiscoveryPipelineService]）
 * 的公平调度 / 窗口续跑 / 暂停语义必须能在**不依赖真实 MySQL** 的情况下被确定性地验证，
 * 而事务、唯一约束与并发 CAS 本身由真实 MySQL 集成测试（`DiscoveryPaperQueueRepositoryIT`）证明。
 *
 * 语义要求对所有实现一致：条件更新（CAS）失败一律返回「未生效」而不是抛异常；
 * 读取方法必须容忍行缺失（返回 null / 0 / 空列表）而不是崩溃。
 */
interface DiscoveryPaperQueueStore {

    // I-7：pipeline 单例控制
    fun ensurePipeline(now: Instant)
    fun findPipeline(): PipelineRow?
    fun launch(
        criteriaJson: String,
        criteriaVersion: Int,
        queryHash: String,
        rawScanDone: Boolean,
        now: Instant
    ): LaunchOutcome

    fun pause(now: Instant): PipelineRow
    fun resume(now: Instant): PipelineRow?
    fun markDrained(now: Instant): Boolean
    fun claimOwner(ownerToken: String, ownerUntil: Instant, windowUntil: Instant, now: Instant): Boolean
    fun renewOwner(ownerToken: String, ownerUntil: Instant, now: Instant): Int
    fun releaseOwner(ownerToken: String, phase: String, waitReason: String?, nextWakeAt: Instant?, now: Instant): Int
    fun bindExecutionId(ownerToken: String, executionId: Long, now: Instant): Int
    fun releaseStaleOwner(recoveryUntil: Instant, now: Instant): Int
    fun markFaulted(ownerToken: String, reason: String, now: Instant): Int
    fun markRawScanDone(ownerToken: String, now: Instant): Int
    fun updatePhase(ownerToken: String, phase: String, waitReason: String?, nextWakeAt: Instant?, now: Instant): Int
    fun setCapacityPaused(paused: Boolean, now: Instant): Int
    fun countActiveJobs(): Long
    fun countUnfinishedJobsInStream(streamId: Long): Long

    // I-1/I-2：stream 身份与采集位置
    fun ensureStream(
        pipelineId: Long,
        queryHash: String,
        source: String,
        epoch: Long,
        criteriaJson: String?,
        now: Instant
    ): StreamRow

    fun findStream(queryHash: String, source: String, epoch: Long): StreamRow?
    fun findStreamById(streamId: Long): StreamRow?
    fun findStreams(pipelineId: Long): List<StreamRow>
    fun seedStreamCursorIfPristine(streamId: Long, cursorValue: String?, now: Instant): Boolean
    fun claimStreamLease(streamId: Long, leaseToken: String, leaseUntil: Instant, now: Instant): Boolean
    fun releaseStreamLease(streamId: Long, leaseToken: String, now: Instant): Int
    fun deferStream(streamId: Long, leaseToken: String, nextAttemptAt: Instant, reason: String, now: Instant): Int
    fun recordStreamError(streamId: Long, reason: String, nextAttemptAt: Instant, now: Instant): Int

    // I-1/I-5：整页入队 + cursor 推进（同一事务）
    fun enqueuePage(
        streamId: Long,
        leaseToken: String,
        unit: String,
        items: List<QueueJobInsert>,
        cursorValue: String?,
        exhausted: Boolean,
        limits: QueueCapacityLimits,
        now: Instant
    ): EnqueuePageResult

    // I-3/I-6：领取、心跳与公平选择
    fun nextDueJob(streamIds: List<Long>, priorityFirst: Boolean, now: Instant): JobRow?
    fun nextDueOrdinaryJob(streamIds: List<Long>, now: Instant): JobRow?
    fun claimJob(jobId: Long, leaseToken: String, leaseUntil: Instant, pipelineGeneration: Long, now: Instant): Boolean
    fun renewJobLease(jobId: Long, leaseToken: String, generation: Long, leaseUntil: Instant, now: Instant): Int
    fun saveExtraction(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        extractionJson: String,
        extractionBytes: Long,
        now: Instant
    ): Boolean

    fun completeJob(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        status: String,
        attempts: Int,
        nextAttemptAt: Instant,
        lastError: String?,
        now: Instant
    ): CompletionOutcome

    fun completeJobWithExperts(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        indexedExperts: Int,
        duplicateExperts: Int,
        attempts: Int,
        nextAttemptAt: Instant,
        now: Instant
    ): CompletionOutcome

    fun scheduleRetry(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        attempts: Int,
        nextAttemptAt: Instant,
        lastError: String,
        now: Instant
    ): Boolean

    fun returnToPending(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        nextAttemptAt: Instant,
        lastError: String?,
        now: Instant
    ): Boolean

    fun forceFailForLeaseLoss(jobId: Long, leaseToken: String, generation: Long, reason: String, now: Instant): Boolean
    fun insertFailedItem(
        streamId: Long,
        item: QueueJobInsert,
        reason: String,
        limits: QueueCapacityLimits,
        now: Instant
    ): Boolean

    // I-8：状态快照与清理
    fun activeJobStats(): ActiveJobStats
    fun jobStatusCountsByStream(streamIds: List<Long>): Map<Long, Map<String, Long>>
    fun clearTerminalPayloads(completedBefore: Instant, batchSize: Int, now: Instant): Int
    fun deleteTerminalJobs(completedBefore: Instant, batchSize: Int, now: Instant): Int
}

/**
 * I-1 至 I-8：持久化采集队列的全部 SQL（三张 V133 表）—— [DiscoveryPaperQueueStore] 的唯一生产实现。
 *
 * 职责边界：本类只做**存储与状态机**（事务、行锁、条件 CAS、容量账）；查询规范化、身份哈希、
 * 抽取与专家消费策略都在 `ExpertDiscoveryService`，调度/窗口/公平策略在 `DiscoveryPipelineService`。
 * 因此容量上限以 [QueueCapacityLimits] 参数传入，本类**不读任何配置**。
 *
 * 并发与锁序（I-3/I-5）：
 * - 每个公开方法自成一个事务；同时触及多张表时一律按固定锁序 `discovery_pipeline` →
 *   `discovery_collection_stream` → `discovery_paper_job` 加行锁，因此多连接并发不交叉死锁；
 * - 所有终态/续租/保存写入都是「status + lease_token (+ generation)」条件 CAS，旧 token 影响 0 行；
 * - HTTP/ES 调用永远在事务之外（本类只碰数据库）。
 *
 * 时间列一律以 UTC 墙钟写入 `DATETIME(3)`（与 JVM 默认时区无关）。
 */
@Repository
class DiscoveryPaperQueueRepository(private val jdbcTemplate: JdbcTemplate) : DiscoveryPaperQueueStore {

    // ------------------------------------------------------------------
    // I-7：pipeline 单例控制
    // ------------------------------------------------------------------

    /** 初始 `PAUSED`。重复调用是空操作，绝不把已有状态改回 `PAUSED`（restart/reset 不得解除暂停）。 */
    override fun ensurePipeline(now: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO discovery_pipeline (id, desired_state, phase, generation, created_at, updated_at)
            VALUES (? , ?, ?, 0, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """,
            PIPELINE_ID, PipelineDesiredState.PAUSED, PipelinePhase.QUEUED, toDb(now), toDb(now)
        )
    }

    override fun findPipeline(): PipelineRow? = jdbcTemplate.query(
        "$PIPELINE_COLUMNS WHERE id = ?",
        { rs, _ -> pipelineRow(rs) },
        PIPELINE_ID
    ).firstOrNull()

    @Transactional
    override fun launch(
        criteriaJson: String,
        criteriaVersion: Int,
        queryHash: String,
        rawScanDone: Boolean,
        now: Instant
    ): LaunchOutcome {
        ensurePipeline(now)
        val current = lockPipeline() ?: return LaunchOutcome.Conflict("pipeline 行缺失（应在 ensurePipeline 后存在）")
        val sameQuery = current.queryHash != null && current.queryHash == queryHash
        if (sameQuery) {
            // I-7：同查询幂等 —— 只把 PAUSED 解释为显式恢复；绝不新建 epoch、绝不清空队列。
            jdbcTemplate.update(
                """
                UPDATE discovery_pipeline
                   SET desired_state = ?, wait_reason = NULL, next_wake_at = ?, updated_at = ?
                 WHERE id = ?
                """,
                PipelineDesiredState.RUNNING, toDb(now), toDb(now), PIPELINE_ID
            )
            return LaunchOutcome.Applied(requireNotNull(findPipeline()), resumed = true)
        }
        // I-7：不同查询且仍有活跃工作/积压 → 409，绝不静默丢弃在手工作。
        val backlog = current.activeCount > 0 || countBacklogJobs() > 0
        if (current.queryHash != null && backlog) {
            return LaunchOutcome.Conflict(
                "已有查询（queryHash=${current.queryHash}）仍有 $backlog 条未完成工作，不能切换查询；" +
                    "请先暂停并排空，或使用相同查询恢复"
            )
        }
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET desired_state = ?, phase = ?, criteria_json = ?, criteria_version = ?,
                   query_hash = ?, execution_id = NULL, window_until = NULL, next_wake_at = ?,
                   wait_reason = NULL, raw_scan_done = ?, capacity_paused = 0, updated_at = ?
             WHERE id = ?
            """,
            PipelineDesiredState.RUNNING, PipelinePhase.QUEUED, criteriaJson, criteriaVersion,
            queryHash, toDb(now), rawScanDone, toDb(now), PIPELINE_ID
        )
        return LaunchOutcome.Applied(requireNotNull(findPipeline()), resumed = false)
    }

    /**
     * I-7：幂等持久化 `PAUSED`，并在**真正从 RUNNING 转入**时报废 generation 以停止新领取。
     * 不要求存在 RUNNING 的 task_execution；`phase` 保留在途收尾态（对外 state 优先 PAUSED）。
     */
    @Transactional
    override fun pause(now: Instant): PipelineRow {
        ensurePipeline(now)
        lockPipeline()
        // MySQL 的 SET 子句**从左到右**求值，后面的表达式读到的是前面刚写入的新值：
        // 因此 generation 必须先算（依据旧的 desired_state），再写 desired_state。
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET generation = generation + CASE WHEN desired_state = ? THEN 1 ELSE 0 END,
                   desired_state = ?,
                   wait_reason = ?, next_wake_at = NULL, updated_at = ?
             WHERE id = ?
            """,
            PipelineDesiredState.RUNNING, PipelineDesiredState.PAUSED,
            PipelineWaitReason.MANUAL_PAUSE, toDb(now), PIPELINE_ID
        )
        return requireNotNull(findPipeline())
    }

    /** I-7：恢复只对**已保存的查询**生效；无配置返回 null（调用方 409）。绝不新建 epoch。 */
    @Transactional
    override fun resume(now: Instant): PipelineRow? {
        ensurePipeline(now)
        val current = lockPipeline() ?: return null
        if (current.criteriaJson.isNullOrBlank() || current.queryHash.isNullOrBlank()) return null
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET desired_state = ?, wait_reason = NULL, next_wake_at = ?, updated_at = ?
             WHERE id = ?
            """,
            PipelineDesiredState.RUNNING, toDb(now), toDb(now), PIPELINE_ID
        )
        return requireNotNull(findPipeline())
    }

    /** I-7：所有来源已穷尽且无活跃条目 → `DRAINED`（不改 `desired_state`）。 */
    @Transactional
    override fun markDrained(now: Instant): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET phase = ?, wait_reason = NULL, next_wake_at = NULL, capacity_paused = 0, updated_at = ?
             WHERE id = ? AND phase <> ?
            """,
            PipelinePhase.DRAINED, toDb(now), PIPELINE_ID, PipelinePhase.DRAINED
        )
        return updated > 0
    }

    /**
     * I-7：30 秒属主租约 CAS 领取。只有「无人持有 / 租约已过期」且 `desired_state=RUNNING` 时成功；
     * 窗口截止同时刷新为 [windowUntil]（仅在旧窗口已结束或为空时），因此**新窗口不会复用过期截止**。
     */
    @Transactional
    override fun claimOwner(ownerToken: String, ownerUntil: Instant, windowUntil: Instant, now: Instant): Boolean {
        ensurePipeline(now)
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET owner_token = ?, owner_until = ?,
                   window_until = CASE WHEN window_until IS NULL OR window_until <= ? THEN ? ELSE window_until END,
                   phase = ?, execution_id = NULL, wait_reason = NULL, next_wake_at = NULL, updated_at = ?
             WHERE id = ?
               AND desired_state = ?
               AND (owner_token IS NULL OR owner_until IS NULL OR owner_until <= ?)
            """,
            ownerToken, toDb(ownerUntil),
            toDb(now), toDb(windowUntil),
            PipelinePhase.RUNNING, toDb(now), PIPELINE_ID,
            PipelineDesiredState.RUNNING, toDb(now)
        )
        return updated > 0
    }

    /** 心跳：只有当前 token 的持有者能延长属主租约。 */
    override fun renewOwner(ownerToken: String, ownerUntil: Instant, now: Instant): Int = jdbcTemplate.update(
        """
        UPDATE discovery_pipeline
           SET owner_until = ?, updated_at = ?
         WHERE id = ? AND owner_token = ?
        """,
        toDb(ownerUntil), toDb(now), PIPELINE_ID, ownerToken
    )

    /**
     * 窗口收尾：只允许原 token 释放自身租约（旧 owner 不可能清除新 owner，也不可能恢复 RUNNING）。
     */
    override fun releaseOwner(
        ownerToken: String,
        phase: String,
        waitReason: String?,
        nextWakeAt: Instant?,
        now: Instant
    ): Int = jdbcTemplate.update(
        """
        UPDATE discovery_pipeline
           SET owner_token = NULL, owner_until = NULL, phase = ?, wait_reason = ?, next_wake_at = ?, updated_at = ?
         WHERE id = ? AND owner_token = ?
        """,
        phase, waitReason, nextWakeAt?.let { toDb(it) }, toDb(now), PIPELINE_ID, ownerToken
    )

    /** I-7：任务历史归属；只允许当前属主绑定。 */
    override fun bindExecutionId(ownerToken: String, executionId: Long, now: Instant): Int = jdbcTemplate.update(
        "UPDATE discovery_pipeline SET execution_id = ?, updated_at = ? WHERE id = ? AND owner_token = ?",
        executionId, toDb(now), PIPELINE_ID, ownerToken
    )

    /**
     * I-7/OWNER_RECOVERY：清理**已失效**的属主租约并进入 `WAITING` + `OWNER_RECOVERY`。
     * 谓词要求 `owner_until <= now`，因此它不可能清除一个仍然有效的 owner（新旧 owner 不会重叠）。
     */
    @Transactional
    override fun releaseStaleOwner(recoveryUntil: Instant, now: Instant): Int {
        lockPipeline()
        return jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET owner_token = NULL, owner_until = NULL, phase = ?, wait_reason = ?, next_wake_at = ?, updated_at = ?
             WHERE id = ?
               AND owner_token IS NOT NULL
               AND owner_until IS NOT NULL
               AND owner_until <= ?
            """,
            PipelinePhase.WAITING, PipelineWaitReason.OWNER_RECOVERY, toDb(recoveryUntil), toDb(now),
            PIPELINE_ID, toDb(now)
        )
    }

    /**
     * 窗口循环不可恢复异常：只允许原 token 标记 `FAULTED` 并释放自身租约（旧 owner 不能影响新 owner）。
     */
    @Transactional
    override fun markFaulted(ownerToken: String, reason: String, now: Instant): Int {
        lockPipeline()
        return jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET phase = ?, wait_reason = ?, owner_token = NULL, owner_until = NULL, next_wake_at = ?, updated_at = ?
             WHERE id = ? AND owner_token = ?
            """,
            PipelinePhase.FAULTED, reason, toDb(now.plusSeconds(30)), toDb(now), PIPELINE_ID, ownerToken
        )
    }

    /** I-7：RAW 扫描只在本查询显式开启且尚未完成时运行；完成后持久化标记，后续窗口不重扫。 */
    override fun markRawScanDone(ownerToken: String, now: Instant): Int = jdbcTemplate.update(
        "UPDATE discovery_pipeline SET raw_scan_done = 1, updated_at = ? WHERE id = ? AND owner_token = ?",
        toDb(now), PIPELINE_ID, ownerToken
    )

    /** I-7：窗口内相位/等待原因/下次可进展时间；只有当前属主可写。 */
    override fun updatePhase(
        ownerToken: String,
        phase: String,
        waitReason: String?,
        nextWakeAt: Instant?,
        now: Instant
    ): Int = jdbcTemplate.update(
        """
        UPDATE discovery_pipeline
           SET phase = ?, wait_reason = ?, next_wake_at = ?, updated_at = ?
         WHERE id = ? AND owner_token = ?
        """,
        phase, waitReason, nextWakeAt?.let { toDb(it) }, toDb(now), PIPELINE_ID, ownerToken
    )

    /** I-5：容量暂停标记（只是诊断位：容量恢复后由入队事务或恢复判定清除）。 */
    override fun setCapacityPaused(paused: Boolean, now: Instant): Int = jdbcTemplate.update(
        "UPDATE discovery_pipeline SET capacity_paused = ?, updated_at = ? WHERE id = ?",
        if (paused) 1 else 0, toDb(now), PIPELINE_ID
    )

    /** I-5：活跃 job 数（= `queueDepth`，只计 PENDING/RUNNING/RETRY_WAIT）。 */
    override fun countActiveJobs(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')",
            Long::class.java
        ) ?: 0L

    private fun countBacklogJobs(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE status IN ('PENDING','RUNNING','RETRY_WAIT','FAILED')",
            Long::class.java
        ) ?: 0L

    /** I-1：某来源是否还有未终结的工作（提交已入队的页仍未被消费）。 */
    override fun countUnfinishedJobsInStream(streamId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE stream_id = ? AND status IN ('PENDING','RUNNING','RETRY_WAIT')",
            Long::class.java, streamId
        ) ?: 0L

    // ------------------------------------------------------------------
    // I-1/I-2：stream 身份与采集位置
    // ------------------------------------------------------------------

    /** 幂等建流：`UNIQUE(query_hash, source, epoch)` 保证同条件同源只有一行。 */
    @Transactional
    override fun ensureStream(
        pipelineId: Long,
        queryHash: String,
        source: String,
        epoch: Long,
        criteriaJson: String?,
        now: Instant
    ): StreamRow {
        val existing = findStream(queryHash, source, epoch)
        if (existing != null) return existing
        jdbcTemplate.update(
            """
            INSERT INTO discovery_collection_stream
                (pipeline_id, query_hash, source, epoch, criteria_json, cursor_state, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """,
            pipelineId, queryHash, source, epoch, criteriaJson, StreamCursorState.ACTIVE, toDb(now), toDb(now)
        )
        return requireNotNull(findStream(queryHash, source, epoch)) {
            "discovery_collection_stream 行在 ensureStream 后消失：$queryHash/$source/$epoch"
        }
    }

    override fun findStream(queryHash: String, source: String, epoch: Long): StreamRow? = jdbcTemplate.query(
        "$STREAM_COLUMNS WHERE query_hash = ? AND source = ? AND epoch = ?",
        { rs, _ -> streamRow(rs) },
        queryHash, source, epoch
    ).firstOrNull()

    override fun findStreamById(streamId: Long): StreamRow? = jdbcTemplate.query(
        "$STREAM_COLUMNS WHERE id = ?",
        { rs, _ -> streamRow(rs) },
        streamId
    ).firstOrNull()

    override fun findStreams(pipelineId: Long): List<StreamRow> = jdbcTemplate.query(
        "$STREAM_COLUMNS WHERE pipeline_id = ? ORDER BY id",
        { rs, _ -> streamRow(rs) },
        pipelineId
    )

    /**
     * I-1：旧 v2 已处理检查点只用于**首次种子**，且只在「该流从未写入过游标、且没有已入队工作」时生效；
     * 冲突/无法确认的条件由调用方决定（保守重放），本方法不覆盖任何已推进过的游标。
     */
    @Transactional
    override fun seedStreamCursorIfPristine(streamId: Long, cursorValue: String?, now: Instant): Boolean {
        if (cursorValue == null) return false
        lockPipeline()
        val stream = lockStream(streamId) ?: return false
        if (stream.cursorValue != null || stream.cursorState != StreamCursorState.ACTIVE) return false
        if (countJobsInStream(streamId) > 0) return false
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET cursor_value = ?, updated_at = ?
             WHERE id = ? AND cursor_value IS NULL AND cursor_state = ?
            """,
            cursorValue, toDb(now), streamId, StreamCursorState.ACTIVE
        )
        return updated > 0
    }

    private fun countJobsInStream(streamId: Long): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE stream_id = ?", Long::class.java, streamId
        ) ?: 0L

    /**
     * I-1/I-6：采集页租约 —— 同一来源同一时刻只有一个采集者能取下一页。
     * 已被他人持有且未过期时返回 false（调用方跳过本源，绝不并发取同一页）。
     */
    @Transactional
    override fun claimStreamLease(streamId: Long, leaseToken: String, leaseUntil: Instant, now: Instant): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET lease_token = ?, lease_until = ?, updated_at = ?
             WHERE id = ?
               AND cursor_state = ?
               AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
               AND (lease_token IS NULL OR lease_until IS NULL OR lease_until <= ?)
            """,
            leaseToken, toDb(leaseUntil), toDb(now), streamId,
            StreamCursorState.ACTIVE, toDb(now), toDb(now)
        )
        return updated > 0
    }

    /** 采集完成后释放页租约；只有当前 token 持有者能释放。 */
    override fun releaseStreamLease(streamId: Long, leaseToken: String, now: Instant): Int = jdbcTemplate.update(
        """
        UPDATE discovery_collection_stream
           SET lease_token = NULL, lease_until = NULL, updated_at = ?
         WHERE id = ? AND lease_token = ?
        """,
        toDb(now), streamId, leaseToken
    )

    /** I-6：OpenAlex 额度延期/限流只推迟本源的下一次采集，其他来源照常。 */
    override fun deferStream(streamId: Long, leaseToken: String, nextAttemptAt: Instant, reason: String, now: Instant): Int =
        jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET next_attempt_at = ?, source_error = ?, lease_token = NULL, lease_until = NULL, updated_at = ?
             WHERE id = ? AND lease_token = ?
            """,
            toDb(nextAttemptAt), reason.take(1000), toDb(now), streamId, leaseToken
        )

    /** I-1：来源级终止失败写入可观测原因，并安排在 [nextAttemptAt] 之后重试（不冒充穷尽）。 */
    override fun recordStreamError(streamId: Long, reason: String, nextAttemptAt: Instant, now: Instant): Int =
        jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET source_error = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL, updated_at = ?
             WHERE id = ?
            """,
            reason.take(1000), toDb(nextAttemptAt), toDb(now), streamId
        )

    // ------------------------------------------------------------------
    // I-1/I-5：整页入队 + cursor 推进（同一事务）
    // ------------------------------------------------------------------

    /**
     * I-1/I-5/I-8：把一整页写进队列并推进 cursor，**同一事务**。
     *
     * - 页内重复键（规范化后同一身份）先按 item_key 去重；已存在的行不占新增容量（重放不额外占额）；
     * - 数量或字节任一超限 → [EnqueuePageStatus.CAPACITY_BLOCKED]：整页不提交、cursor 不推进、
     *   容量位被标记，等低水位 + 字节足够再重放同一页；
     * - 租约在入队期间被他人接管 → [EnqueuePageStatus.LEASE_LOST]：整页回滚。
     */
    @Transactional
    override fun enqueuePage(
        streamId: Long,
        leaseToken: String,
        unit: String,
        items: List<QueueJobInsert>,
        cursorValue: String?,
        exhausted: Boolean,
        limits: QueueCapacityLimits,
        now: Instant
    ): EnqueuePageResult {
        ensurePipeline(now)
        val pipeline = lockPipeline() ?: error("discovery_pipeline 行缺失")
        val stream = lockStream(streamId) ?: return EnqueuePageResult(EnqueuePageStatus.LEASE_LOST)
        if (stream.leaseToken != leaseToken) return EnqueuePageResult(EnqueuePageStatus.LEASE_LOST)

        // 页内去重：同一 item_key 只保留第一条（顺序即来源返回顺序）。
        val deduped = LinkedHashMap<String, QueueJobInsert>()
        for (item in items) deduped.putIfAbsent(item.itemKey, item)
        val existingKeys = existingItemKeys(streamId, deduped.keys.toList())
        val fresh = deduped.values.filterNot { it.itemKey in existingKeys }
        val duplicateCount = deduped.size - fresh.size
        val freshMetadataBytes = fresh.sumOf { it.payloadBytes }
        val freshReservedBytes = fresh.size.toLong() * limits.reservedResultBytes

        if (fresh.isNotEmpty()) {
            val projectedActive = pipeline.activeCount + fresh.size
            val projectedBytes = pipeline.payloadBytes + pipeline.reservedResultBytes +
                freshMetadataBytes + freshReservedBytes
            if (projectedActive > limits.highWater || projectedBytes > limits.maxBytes) {
                return EnqueuePageResult(
                    EnqueuePageStatus.CAPACITY_BLOCKED,
                    duplicateJobs = duplicateCount,
                    activeCount = pipeline.activeCount,
                    occupiedBytes = pipeline.occupiedBytes
                )
            }
        }

        for (item in fresh) {
            jdbcTemplate.update(
                """
                INSERT INTO discovery_paper_job
                    (stream_id, item_key, identity_quality, unit, payload_version, priority,
                     metadata_json, payload_bytes, reserved_result_bytes, status, attempts,
                     next_attempt_at, generation, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """,
                streamId, item.itemKey, item.identityQuality, item.unit, item.payloadVersion,
                1.takeIf { item.publiclyDownloadable } ?: 0,
                item.metadataJson, item.payloadBytes, limits.reservedResultBytes,
                QueueJobStatus.PENDING, toDb(now), pipeline.generation, toDb(now), toDb(now)
            )
        }

        // `ON DUPLICATE KEY UPDATE id = id` 的 affected-rows 在「同值更新」上的计数不可依赖，
        // 因此新增条数一律用事务内的**预查结果** `fresh.size`（同一事务持 pipeline 行锁 + stream 租约，
        // 不存在并发插入同一 identity 的窗口）。
        val inserted = fresh.size
        if (inserted > 0) {
            val addedMetadata = fresh.sumOf { it.payloadBytes }
            val addedReserved = inserted.toLong() * limits.reservedResultBytes
            applyPipelineInsertCounters(
                inserted = inserted, unit = unit, metadataBytes = addedMetadata,
                reservedBytes = addedReserved, now = now
            )
            applyStreamInsertCounters(
                streamId = streamId, inserted = inserted, unit = unit, now = now
            )
        }

        val advanced = jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET cursor_value = ?, cursor_state = ?, next_attempt_at = NULL, source_error = NULL,
                   lease_token = NULL, lease_until = NULL, updated_at = ?
             WHERE id = ? AND lease_token = ?
            """,
            cursorValue, if (exhausted) StreamCursorState.EXHAUSTED else StreamCursorState.ACTIVE,
            toDb(now), streamId, leaseToken
        )
        check(advanced == 1) {
            "采集页租约在入队事务内被接管：stream=$streamId（整页回滚，cursor 不推进）"
        }

        val after = requireNotNull(findPipeline())
        return EnqueuePageResult(
            status = EnqueuePageStatus.COMMITTED,
            insertedJobs = inserted,
            duplicateJobs = duplicateCount,
            activeCount = after.activeCount,
            occupiedBytes = after.occupiedBytes
        )
    }

    private fun existingItemKeys(streamId: Long, keys: List<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        val placeholders = keys.joinToString(", ") { "?" }
        val found = jdbcTemplate.query(
            "SELECT item_key FROM discovery_paper_job WHERE stream_id = ? AND item_key IN ($placeholders)",
            { rs, _ -> rs.getString("item_key") },
            *(listOf(streamId) + keys).toTypedArray()
        )
        return found.toSet()
    }

    /** I-8：pipeline 的入队计数（论文/ORCID 分列）与容量账，与入队同事务。 */
    private fun applyPipelineInsertCounters(
        inserted: Int,
        unit: String,
        metadataBytes: Long,
        reservedBytes: Long,
        now: Instant
    ) {
        val paperColumn = if (unit == QueueItemUnit.RECORD) "queued_records" else "queued_papers"
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET $paperColumn = $paperColumn + ?, active_count = active_count + ?,
                   payload_bytes = payload_bytes + ?, reserved_result_bytes = reserved_result_bytes + ?,
                   capacity_paused = 0, updated_at = ?
             WHERE id = ?
            """,
            inserted, inserted, metadataBytes, reservedBytes, toDb(now), PIPELINE_ID
        )
    }

    private fun applyStreamInsertCounters(
        streamId: Long,
        inserted: Int,
        unit: String,
        now: Instant
    ) {
        val paperColumn = if (unit == QueueItemUnit.RECORD) "queued_records" else "queued_papers"
        jdbcTemplate.update(
            "UPDATE discovery_collection_stream SET $paperColumn = $paperColumn + ?, updated_at = ? WHERE id = ?",
            inserted, toDb(now), streamId
        )
    }

    // ------------------------------------------------------------------
    // I-3/I-6：领取、心跳与公平选择
    // ------------------------------------------------------------------

    /** I-6：来源内优先「可公开下载」，同时允许消费方另取「最老的普通任务」。 */
    override fun nextDueJob(streamIds: List<Long>, priorityFirst: Boolean, now: Instant): JobRow? {
        if (streamIds.isEmpty()) return null
        val placeholders = streamIds.joinToString(", ") { "?" }
        val priorityClause = if (priorityFirst) "priority DESC, " else ""
        return jdbcTemplate.query(
            """
            $JOB_COLUMNS
             WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
               AND stream_id IN ($placeholders)
               AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= ?)
                    OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= ?)))
             ORDER BY $priorityClause next_attempt_at, id
             LIMIT 1
            """,
            { rs, _ -> jobRow(rs) },
            *(streamIds + listOf<Any>(toDb(now), toDb(now))).toTypedArray()
        ).firstOrNull()
    }

    /** 只取「普通（不可公开下载）」的最老到期任务，供每 10 个高优先任务之后的公平补位。 */
    override fun nextDueOrdinaryJob(streamIds: List<Long>, now: Instant): JobRow? {
        if (streamIds.isEmpty()) return null
        val placeholders = streamIds.joinToString(", ") { "?" }
        return jdbcTemplate.query(
            """
            $JOB_COLUMNS
             WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
               AND stream_id IN ($placeholders)
               AND priority = 0
               AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= ?)
                    OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= ?)))
             ORDER BY next_attempt_at, id
             LIMIT 1
            """,
            { rs, _ -> jobRow(rs) },
            *(streamIds + listOf<Any>(toDb(now), toDb(now))).toTypedArray()
        ).firstOrNull()
    }

    /**
     * I-3/I-7：CAS 领取。谓词同时接受「到点的 PENDING/RETRY_WAIT」与「租约过期的 RUNNING」（崩溃恢复），
     * 并且**只在 pipeline 仍为 RUNNING 且 generation 与调用方观察到的一致时**才会成功 ——
     * 人工暂停（generation 递增）后不可能再产生新领取。
     */
    @Transactional
    override fun claimJob(
        jobId: Long,
        leaseToken: String,
        leaseUntil: Instant,
        pipelineGeneration: Long,
        now: Instant
    ): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, lease_token = ?, lease_until = ?, generation = ?, updated_at = ?
             WHERE id = ?
               AND ((status IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= ?)
                    OR (status = 'RUNNING' AND (lease_until IS NULL OR lease_until <= ?)))
               AND EXISTS (
                     SELECT 1 FROM discovery_pipeline p
                      WHERE p.id = ? AND p.desired_state = 'RUNNING' AND p.generation = ?
                   )
            """,
            QueueJobStatus.RUNNING, leaseToken, toDb(leaseUntil), pipelineGeneration, toDb(now),
            jobId, toDb(now), toDb(now), PIPELINE_ID, pipelineGeneration
        )
        return updated > 0
    }

    /** I-3：心跳续租必须匹配 `lease_token` 与领取时 generation；旧 worker 影响 0 行。 */
    override fun renewJobLease(jobId: Long, leaseToken: String, generation: Long, leaseUntil: Instant, now: Instant): Int =
        jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET lease_until = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            toDb(leaseUntil), toDb(now), jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )

    /**
     * I-3/I-4：保存抽取结果。校验 `lease_token` 与**领取时** generation —— 因此人工暂停递增
     * pipeline.generation 之后，未被重新领取且租约仍有效的旧 job **仍能**保存结果（唯一例外），
     * 但它的 complete 会被 [completeJob] 的当前 generation 条件拒绝，只能退回 PENDING。
     * 预占结果空间在此转成实际字节（I-5）。
     */
    @Transactional
    override fun saveExtraction(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        extractionJson: String,
        extractionBytes: Long,
        now: Instant
    ): Boolean {
        lockPipeline() ?: return false
        val job = lockJob(jobId) ?: return false
        if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken || job.generation != generation) {
            return false
        }
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET extraction_json = ?, payload_bytes = payload_bytes + ?,
                   reserved_result_bytes = 0, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            extractionJson, extractionBytes, toDb(now), jobId,
            QueueJobStatus.RUNNING, leaseToken, generation
        )
        if (updated != 1) return false
        val released = job.reservedResultBytes
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET payload_bytes = payload_bytes + ?, reserved_result_bytes = GREATEST(reserved_result_bytes - ?, 0),
                   updated_at = ?
             WHERE id = ?
            """,
            extractionBytes, released, toDb(now), PIPELINE_ID
        )
        return true
    }

    /**
     * I-3/I-8：终态 CAS。要求 `lease_token`、领取时 generation 与**当前** pipeline generation 三者一致，
     * 因此旧 worker / 暂停后的旧 generation 都不可能 complete（影响 0 行）。
     * 计数在同一次 CAS 成功后最多累加一次。
     */
    @Transactional
    override fun completeJob(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        status: String,
        attempts: Int,
        nextAttemptAt: Instant,
        lastError: String?,
        now: Instant
    ): CompletionOutcome {
        require(status == QueueJobStatus.SUCCEEDED || status == QueueJobStatus.FAILED) {
            "completeJob 只接受终态 SUCCEEDED/FAILED，收到 $status"
        }
        val pipeline = lockPipeline() ?: return CompletionOutcome(false, 0, 0)
        val job = lockJob(jobId) ?: return CompletionOutcome(false, 0, 0)
        if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken) {
            return CompletionOutcome(false, 0, 0)
        }
        // I-7：generation 不一致 = 期间发生过人工暂停（或已被他人重新领取）→ 不允许 complete。
        if (job.generation != generation || pipeline.generation != generation) {
            return CompletionOutcome(false, 0, 0)
        }
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, attempts = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL,
                   last_error = ?, completed_at = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            status, attempts, toDb(nextAttemptAt), lastError?.take(1000), toDb(now), toDb(now),
            jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )
        if (updated != 1) return CompletionOutcome(false, 0, 0)

        val releasedReserved = if (job.extractionJson.isNullOrEmpty()) job.reservedResultBytes else 0L
        val unit = job.unit
        val failedIncrement = if (status == QueueJobStatus.FAILED) 1 else 0
        val paperColumn = if (unit == QueueItemUnit.RECORD) "processed_records" else "processed_papers"
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET active_count = GREATEST(active_count - 1, 0),
                   $paperColumn = $paperColumn + 1,
                   failed_items = failed_items + ?,
                   reserved_result_bytes = GREATEST(reserved_result_bytes - ?, 0),
                   updated_at = ?
             WHERE id = ?
            """,
            failedIncrement, releasedReserved, toDb(now), PIPELINE_ID
        )
        if (releasedReserved > 0) {
            jdbcTemplate.update(
                "UPDATE discovery_paper_job SET reserved_result_bytes = 0 WHERE id = ?", jobId
            )
        }
        jdbcTemplate.update(
            "UPDATE discovery_collection_stream SET $paperColumn = $paperColumn + 1, failed_items = failed_items + ?, updated_at = ? WHERE id = ?",
            failedIncrement, toDb(now), job.streamId
        )
        return CompletionOutcome(true, if (status == QueueJobStatus.FAILED) 1 else 0, 0)
    }

    /**
     * I-3/I-8：专家消费成功后的累计（终态为 SUCCEEDED 的**同一事务**内追加专家计数，
     * 一次成功最多各累加一次）。`indexed/duplicates` 由消费结果给出，绝不从负载重算。
     */
    @Transactional
    override fun completeJobWithExperts(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        indexedExperts: Int,
        duplicateExperts: Int,
        attempts: Int,
        nextAttemptAt: Instant,
        now: Instant
    ): CompletionOutcome {
        val pipeline = lockPipeline() ?: return CompletionOutcome(false, 0, 0)
        val job = lockJob(jobId) ?: return CompletionOutcome(false, 0, 0)
        if (job.status != QueueJobStatus.RUNNING || job.leaseToken != leaseToken) {
            return CompletionOutcome(false, 0, 0)
        }
        if (job.generation != generation || pipeline.generation != generation) {
            return CompletionOutcome(false, 0, 0)
        }
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, attempts = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL,
                   last_error = NULL, completed_at = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            QueueJobStatus.SUCCEEDED, attempts, toDb(nextAttemptAt), toDb(now), toDb(now),
            jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )
        if (updated != 1) return CompletionOutcome(false, 0, 0)

        val releasedReserved = if (job.extractionJson.isNullOrEmpty()) job.reservedResultBytes else 0L
        val paperColumn = if (job.unit == QueueItemUnit.RECORD) "processed_records" else "processed_papers"
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET active_count = GREATEST(active_count - 1, 0),
                   $paperColumn = $paperColumn + 1,
                   indexed_experts = indexed_experts + ?,
                   duplicate_experts = duplicate_experts + ?,
                   reserved_result_bytes = GREATEST(reserved_result_bytes - ?, 0),
                   updated_at = ?
             WHERE id = ?
            """,
            indexedExperts, duplicateExperts, releasedReserved, toDb(now), PIPELINE_ID
        )
        if (releasedReserved > 0) {
            jdbcTemplate.update("UPDATE discovery_paper_job SET reserved_result_bytes = 0 WHERE id = ?", jobId)
        }
        jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET $paperColumn = $paperColumn + 1, indexed_experts = indexed_experts + ?,
                   duplicate_experts = duplicate_experts + ?, updated_at = ?
             WHERE id = ?
            """,
            indexedExperts, duplicateExperts, toDb(now), job.streamId
        )
        return CompletionOutcome(true, 0, indexedExperts)
    }

    /**
     * I-3：可重试失败 → `RETRY_WAIT`（`attempts` 在此**唯一**递增，额度/暂停/容量等待都不走这里）。
     */
    @Transactional
    override fun scheduleRetry(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        attempts: Int,
        nextAttemptAt: Instant,
        lastError: String,
        now: Instant
    ): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, attempts = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL,
                   last_error = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            QueueJobStatus.RETRY_WAIT, attempts, toDb(nextAttemptAt), lastError.take(1000), toDb(now),
            jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )
        return updated == 1
    }

    /**
     * I-3：**不消耗尝试数**的延期（额度等待、人工暂停、容量等待）—— 释放租约并回到 `PENDING`，
     * `attempts` 保持不变。
     */
    @Transactional
    override fun returnToPending(
        jobId: Long,
        leaseToken: String,
        generation: Long,
        nextAttemptAt: Instant,
        lastError: String?,
        now: Instant
    ): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL,
                   last_error = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            QueueJobStatus.PENDING, toDb(nextAttemptAt), lastError?.take(1000), toDb(now),
            jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )
        return updated == 1
    }

    /** I-3：终态失败（鉴权/载荷错误）在**不满足 complete 的 generation 条件**时也要能落地诊断。 */
    @Transactional
    override fun forceFailForLeaseLoss(jobId: Long, leaseToken: String, generation: Long, reason: String, now: Instant): Boolean {
        lockPipeline()
        val updated = jdbcTemplate.update(
            """
            UPDATE discovery_paper_job
               SET status = ?, lease_token = NULL, lease_until = NULL, last_error = ?,
                   completed_at = ?, updated_at = ?
             WHERE id = ? AND status = ? AND lease_token = ? AND generation = ?
            """,
            QueueJobStatus.FAILED, reason.take(1000), toDb(now), toDb(now),
            jobId, QueueJobStatus.RUNNING, leaseToken, generation
        )
        return updated == 1
    }

    /** I-2/I-5：单条超限/无可靠标识形成**轻量可观测** FAILED 条目（保留身份字段，绝不截断后继续造专家）。 */
    @Transactional
    override fun insertFailedItem(
        streamId: Long,
        item: QueueJobInsert,
        reason: String,
        limits: QueueCapacityLimits,
        now: Instant
    ): Boolean {
        ensurePipeline(now)
        val pipeline = lockPipeline() ?: return false
        if (pipeline.activeCount + 1 > limits.highWater) return false
        val projected = pipeline.occupiedBytes + item.payloadBytes + limits.reservedResultBytes
        if (projected > limits.maxBytes) return false
        // 同一身份只允许一条诊断条目：`ON DUPLICATE KEY UPDATE` 的 affected-rows 不可依赖，
        // 用事务内预查（此时已持有 pipeline 行锁，诊断插入之间互斥）。
        val alreadyPresent = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE stream_id = ? AND item_key = ?",
            Long::class.java, streamId, item.itemKey
        ) ?: 0L
        if (alreadyPresent > 0L) return false
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO discovery_paper_job
                (stream_id, item_key, identity_quality, unit, payload_version, priority,
                 metadata_json, payload_bytes, reserved_result_bytes, status, attempts,
                 next_attempt_at, generation, last_error, created_at, updated_at, completed_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?, 0, ?, 0, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """,
            streamId, item.itemKey, item.identityQuality, item.unit, item.payloadVersion,
            item.metadataJson, item.payloadBytes, QueueJobStatus.FAILED, toDb(now), pipeline.generation,
            reason.take(1000), toDb(now), toDb(now), toDb(now)
        )
        if (inserted != 1) return false
        val unitColumn = if (item.unit == QueueItemUnit.RECORD) "records" else "papers"
        // I-8：轻量诊断条目同样是「入队一次、终态一次」，因此 queued 与 processed 各加一，
        // 另计 failed_items；active_count 不变（它从未成为活跃工作）。
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET queued_$unitColumn = queued_$unitColumn + 1, processed_$unitColumn = processed_$unitColumn + 1,
                   failed_items = failed_items + 1, payload_bytes = payload_bytes + ?, updated_at = ?
             WHERE id = ?
            """,
            item.payloadBytes, toDb(now), PIPELINE_ID
        )
        jdbcTemplate.update(
            """
            UPDATE discovery_collection_stream
               SET queued_$unitColumn = queued_$unitColumn + 1, processed_$unitColumn = processed_$unitColumn + 1,
                   failed_items = failed_items + 1, updated_at = ?
             WHERE id = ?
            """,
            toDb(now), streamId
        )
        return true
    }

    // ------------------------------------------------------------------
    // I-8：状态快照与清理
    // ------------------------------------------------------------------

    /** I-8：`queueDepth` 只计活跃 job；oldestAge 是活跃 job 中最早的 `created_at`。 */
    override fun activeJobStats(): ActiveJobStats {
        val row = jdbcTemplate.query(
            """
            SELECT COUNT(*) AS active,
                   COALESCE(SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END), 0) AS running,
                   MIN(created_at) AS oldest
              FROM discovery_paper_job
             WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')
            """
        ) { rs, _ ->
            ActiveJobStats(
                active = rs.getLong("active"),
                running = rs.getLong("running"),
                oldestCreatedAt = toInstant(rs.getObject("oldest", LocalDateTime::class.java))
            )
        }.firstOrNull()
        return row ?: ActiveJobStats(0, 0, null)
    }

    /** I-8：逐来源的活跃 job 与失败条目（`status` 接口的逐源明细）。 */
    override fun jobStatusCountsByStream(streamIds: List<Long>): Map<Long, Map<String, Long>> {
        if (streamIds.isEmpty()) return emptyMap()
        val placeholders = streamIds.joinToString(", ") { "?" }
        val rows = jdbcTemplate.query(
            "SELECT stream_id, status, COUNT(*) AS cnt FROM discovery_paper_job WHERE stream_id IN ($placeholders) GROUP BY stream_id, status",
            { rs, _ -> Triple(rs.getLong("stream_id"), rs.getString("status"), rs.getLong("cnt")) },
            *streamIds.toTypedArray()
        )
        return rows.groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, pairs) -> pairs.toMap() }
    }

    /**
     * I-8：终态负载 7 天后可清空（释放字节），但**不删除**去重键/状态行，也不重算任何累计指标。
     * 每批限量，返回本批释放的 job 数。
     */
    @Transactional
    override fun clearTerminalPayloads(completedBefore: Instant, batchSize: Int, now: Instant): Int {
        lockPipeline() ?: return 0
        val batch = jdbcTemplate.query(
            """
            SELECT id, payload_bytes, reserved_result_bytes
              FROM discovery_paper_job
             WHERE status IN ('SUCCEEDED','FAILED')
               AND completed_at IS NOT NULL AND completed_at <= ?
               AND (payload_bytes > 0 OR reserved_result_bytes > 0)
             ORDER BY completed_at, id
             LIMIT ?
             FOR UPDATE
            """,
            { rs, _ -> Triple(rs.getLong("id"), rs.getLong("payload_bytes"), rs.getLong("reserved_result_bytes")) },
            toDb(completedBefore), batchSize
        )
        if (batch.isEmpty()) return 0
        val ids = batch.map { it.first }
        val placeholders = ids.joinToString(", ") { "?" }
        val releasedBytes = batch.sumOf { it.second }
        val releasedReserved = batch.sumOf { it.third }
        jdbcTemplate.update(
            "UPDATE discovery_paper_job SET metadata_json = '', extraction_json = NULL, payload_bytes = 0, reserved_result_bytes = 0, updated_at = ? WHERE id IN ($placeholders)",
            *(listOf<Any>(toDb(now)) + ids).toTypedArray()
        )
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET payload_bytes = GREATEST(payload_bytes - ?, 0),
                   reserved_result_bytes = GREATEST(reserved_result_bytes - ?, 0),
                   updated_at = ?
             WHERE id = ?
            """,
            releasedBytes, releasedReserved, toDb(now), PIPELINE_ID
        )
        return batch.size
    }

    /** I-8：去重键/状态 90 天后可清理；只动终态行，绝不触碰非终态或未消费抽取结果。 */
    @Transactional
    override fun deleteTerminalJobs(completedBefore: Instant, batchSize: Int, now: Instant): Int {
        lockPipeline() ?: return 0
        val batch = jdbcTemplate.query(
            """
            SELECT id, payload_bytes, reserved_result_bytes
              FROM discovery_paper_job
             WHERE status IN ('SUCCEEDED','FAILED')
               AND completed_at IS NOT NULL AND completed_at <= ?
             ORDER BY completed_at, id
             LIMIT ?
             FOR UPDATE
            """,
            { rs, _ -> Triple(rs.getLong("id"), rs.getLong("payload_bytes"), rs.getLong("reserved_result_bytes")) },
            toDb(completedBefore), batchSize
        )
        if (batch.isEmpty()) return 0
        val ids = batch.map { it.first }
        val placeholders = ids.joinToString(", ") { "?" }
        jdbcTemplate.update("DELETE FROM discovery_paper_job WHERE id IN ($placeholders)", *ids.toTypedArray())
        jdbcTemplate.update(
            """
            UPDATE discovery_pipeline
               SET payload_bytes = GREATEST(payload_bytes - ?, 0),
                   reserved_result_bytes = GREATEST(reserved_result_bytes - ?, 0),
                   updated_at = ?
             WHERE id = ?
            """,
            batch.sumOf { it.second }, batch.sumOf { it.third }, toDb(now), PIPELINE_ID
        )
        return batch.size
    }

    // ------------------------------------------------------------------
    // 行映射与锁
    // ------------------------------------------------------------------

    /** 固定锁序第一级：pipeline 行。 */
    private fun lockPipeline(): PipelineRow? = jdbcTemplate.query(
        "$PIPELINE_COLUMNS WHERE id = ? FOR UPDATE",
        { rs, _ -> pipelineRow(rs) },
        PIPELINE_ID
    ).firstOrNull()

    /** 固定锁序第二级：stream 行。 */
    private fun lockStream(streamId: Long): StreamRow? = jdbcTemplate.query(
        "$STREAM_COLUMNS WHERE id = ? FOR UPDATE",
        { rs, _ -> streamRow(rs) },
        streamId
    ).firstOrNull()

    /** 固定锁序第三级：job 行。 */
    private fun lockJob(jobId: Long): JobRow? = jdbcTemplate.query(
        "$JOB_COLUMNS WHERE id = ? FOR UPDATE",
        { rs, _ -> jobRow(rs) },
        jobId
    ).firstOrNull()

    private fun pipelineRow(rs: ResultSet) = PipelineRow(
        desiredState = rs.getString("desired_state"),
        phase = rs.getString("phase"),
        criteriaJson = rs.getString("criteria_json"),
        criteriaVersion = rs.getInt("criteria_version"),
        queryHash = rs.getString("query_hash"),
        generation = rs.getLong("generation"),
        ownerToken = rs.getString("owner_token"),
        ownerUntil = toInstant(rs.getObject("owner_until", LocalDateTime::class.java)),
        executionId = rs.getObject("execution_id", Long::class.java),
        windowUntil = toInstant(rs.getObject("window_until", LocalDateTime::class.java)),
        nextWakeAt = toInstant(rs.getObject("next_wake_at", LocalDateTime::class.java)),
        waitReason = rs.getString("wait_reason"),
        rawScanDone = rs.getInt("raw_scan_done") == 1,
        queuedPapers = rs.getLong("queued_papers"),
        queuedRecords = rs.getLong("queued_records"),
        processedPapers = rs.getLong("processed_papers"),
        processedRecords = rs.getLong("processed_records"),
        indexedExperts = rs.getLong("indexed_experts"),
        duplicateExperts = rs.getLong("duplicate_experts"),
        failedItems = rs.getLong("failed_items"),
        activeCount = rs.getLong("active_count"),
        payloadBytes = rs.getLong("payload_bytes"),
        reservedResultBytes = rs.getLong("reserved_result_bytes"),
        capacityPaused = rs.getInt("capacity_paused") == 1
    )

    private fun streamRow(rs: ResultSet) = StreamRow(
        id = rs.getLong("id"),
        pipelineId = rs.getLong("pipeline_id"),
        queryHash = rs.getString("query_hash"),
        source = rs.getString("source"),
        epoch = rs.getLong("epoch"),
        criteriaJson = rs.getString("criteria_json"),
        cursorValue = rs.getString("cursor_value"),
        cursorState = rs.getString("cursor_state"),
        nextAttemptAt = toInstant(rs.getObject("next_attempt_at", LocalDateTime::class.java)),
        leaseToken = rs.getString("lease_token"),
        leaseUntil = toInstant(rs.getObject("lease_until", LocalDateTime::class.java)),
        sourceError = rs.getString("source_error"),
        queuedPapers = rs.getLong("queued_papers"),
        queuedRecords = rs.getLong("queued_records"),
        processedPapers = rs.getLong("processed_papers"),
        processedRecords = rs.getLong("processed_records"),
        indexedExperts = rs.getLong("indexed_experts"),
        duplicateExperts = rs.getLong("duplicate_experts"),
        failedItems = rs.getLong("failed_items")
    )

    private fun jobRow(rs: ResultSet) = JobRow(
        id = rs.getLong("id"),
        streamId = rs.getLong("stream_id"),
        itemKey = rs.getString("item_key"),
        identityQuality = rs.getString("identity_quality"),
        unit = rs.getString("unit"),
        payloadVersion = rs.getInt("payload_version"),
        priority = rs.getInt("priority"),
        metadataJson = rs.getString("metadata_json"),
        extractionJson = rs.getString("extraction_json"),
        payloadBytes = rs.getLong("payload_bytes"),
        reservedResultBytes = rs.getLong("reserved_result_bytes"),
        status = rs.getString("status"),
        attempts = rs.getInt("attempts"),
        nextAttemptAt = toInstant(rs.getObject("next_attempt_at", LocalDateTime::class.java)) ?: Instant.EPOCH,
        leaseToken = rs.getString("lease_token"),
        leaseUntil = toInstant(rs.getObject("lease_until", LocalDateTime::class.java)),
        generation = rs.getLong("generation"),
        lastError = rs.getString("last_error")
    )

    private companion object {
        const val PIPELINE_ID = 1L

        const val PIPELINE_COLUMNS =
            "SELECT desired_state, phase, criteria_json, criteria_version, query_hash, generation, " +
                "owner_token, owner_until, execution_id, window_until, next_wake_at, wait_reason, raw_scan_done, " +
                "queued_papers, queued_records, processed_papers, processed_records, indexed_experts, " +
                "duplicate_experts, failed_items, active_count, payload_bytes, reserved_result_bytes, capacity_paused " +
                "FROM discovery_pipeline"

        const val STREAM_COLUMNS =
            "SELECT id, pipeline_id, query_hash, source, epoch, criteria_json, cursor_value, cursor_state, " +
                "next_attempt_at, lease_token, lease_until, source_error, queued_papers, queued_records, " +
                "processed_papers, processed_records, indexed_experts, duplicate_experts, failed_items " +
                "FROM discovery_collection_stream"

        const val JOB_COLUMNS =
            "SELECT id, stream_id, item_key, identity_quality, unit, payload_version, priority, metadata_json, " +
                "extraction_json, payload_bytes, reserved_result_bytes, status, attempts, next_attempt_at, " +
                "lease_token, lease_until, generation, last_error FROM discovery_paper_job"

        fun toDb(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

        fun toInstant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)
    }
}
