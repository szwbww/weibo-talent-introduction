package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * I-2（08）：发现后的自动学术补全 worker —— 唯一的调度入口，逐批消费 07 的任务存储。
 *
 * - **每 30 秒检查一次**（`@Scheduled(fixedDelay = 30000)`），每次领取至多 100 条到期任务；不足 100 也执行
 *   （尾批不等待）。空闲的一次检查不产生任务记录、不写进度日志。
 * - **与人工补全同锁**：经 [TaskProgressStore.tryStartWithToken] 拿 EXPERT_ENRICHMENT 的任务锁，
 *   自动与手动补全互相排斥；发现任务（EXPERT_DISCOVERY）使用另一把锁，完全不受影响。
 * - **不占调度线程**：批次跑在专用单线程 executor（[DiscoveryExecutorConfig] 的 `autoEnrichmentExecutor`）上，
 *   而不是与邮件定时任务共用的 `@Scheduled` 线程；上一批仍在跑时新提交被拒绝，本次 tick 直接跳过。
 * - **开关默认关闭**：`talent-introduction.expert-discovery.auto-enrichment-enabled`（env
 *   `EXPERT_DISCOVERY_AUTO_ENRICHMENT_ENABLED`）默认 false，验收后在服务器开启；关闭时本 bean 仍可注入，
 *   只是每次检查立即返回。bean 本身沿用既有模式，仅当 `talent-introduction.scheduling.enabled=true` 才注册。
 * - **保存未完成状态**：取消或异常时已领取但未处理的任务保持 `RUNNING`，租约（10 分钟）到期后由下一次领取恢复；
 *   自动调用一律 `requestKind = NEW_ENRICHMENT`（人工历史回填是 HISTORY_ENRICHMENT）。
 *
 * 本类不持有队列与计数器：任务生命周期唯一事实是 07 的任务表，重启后只从表里恢复。
 */
@Service
@ConditionalOnProperty(prefix = "talent-introduction.scheduling", name = ["enabled"], havingValue = "true")
class ExpertAcademicEnrichmentWorker(
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    @Qualifier(AUTO_ENRICHMENT_EXECUTOR)
    private val autoEnrichmentExecutor: Executor
) {
    private val log = LoggerFactory.getLogger(ExpertAcademicEnrichmentWorker::class.java)

    /** I-2：worker 的定时检查；开关关闭时什么都不做（不领取、不记录、不写进度）。 */
    @Scheduled(fixedDelay = 30000)
    fun processDueEnrichmentJobs() {
        if (!discoveryProperties.autoEnrichmentEnabled) return

        // R-3（V-3）：先做一次只读到期探针。空转也要拿任务锁的话，tryStartWithToken 会落一条
        // execution_id 为负的孤儿进度行，内存被清理后就表现为一次「中断/失败」的执行。
        // 探针命中才进入原来的取锁 → 领取流程，互斥/尾批/租约恢复语义完全不变。
        if (!discoveryService.hasDueEnrichmentJobs()) {
            log.debug("没有到期的补全任务，本次检查不建任务记录也不写进度")
            return
        }

        val (started, pendingToken) = progressStore.tryStartWithToken(TASK_TYPE, TaskProgress(
            taskType = TASK_TYPE, status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "补全 worker 检查待补任务..."
        ))
        if (!started) {
            // 人工补全正在执行（或上一批尚未释放锁）：本次 tick 不排队、不改任何状态。
            log.debug("补全任务锁被占用（人工入口或上一批），跳过本次检查")
            return
        }

        try {
            autoEnrichmentExecutor.execute { runBatch(pendingToken) }
        } catch (e: RejectedExecutionException) {
            // 批次仍在专用线程上执行：释放本次拿到的锁并跳过，让下一批正常进行。
            progressStore.clear(TASK_TYPE)
            log.warn("补全批次提交被拒绝（上一批仍在执行），本次跳过: {}", e.message)
        }
    }

    /**
     * 一次批次：领取到期任务（空闲即返回，不产生任务记录）→ 在自己的 EXPERT_ENRICHMENT 任务记录下
     * 复用 06/07 的批次核心 → 释放任务锁。异常时记录 FAILED 并把未完成状态留给租约恢复。
     */
    private fun runBatch(pendingToken: Long) {
        var executionId: Long? = null
        var claimedCount = 0
        try {
            if (progressStore.isCancelled(TASK_TYPE)) {
                log.info("补全任务在开始前已被取消，本次不领取任务")
                return
            }
            val claimed = discoveryService.claimDueEnrichmentJobs(discoveryProperties.autoEnrichmentBatchSize)
            if (claimed.isEmpty()) {
                log.debug("没有到期的补全任务，本次检查结束")
                return
            }
            claimedCount = claimed.size
            taskExecutionService.runAndRecordWithResult(
                TASK_TYPE, "SCHEDULED", emptyMap<String, Any>(),
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId(TASK_TYPE, pendingToken, id)
                }
            ) {
                discoveryService.processClaimedEnrichmentJobBatch(claimed, RequestKind.NEW_ENRICHMENT, TASK_TYPE)
            }
        } catch (ex: Exception) {
            // 未写终态的任务保持 RUNNING，租约到期后重领；任务记录与进度都如实记 FAILED。
            log.error("自动补全批次失败（{} 条任务保持租约未完成）: {}", claimedCount, ex.message, ex)
            progressStore.update(TASK_TYPE, TaskProgress(
                taskType = TASK_TYPE, status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = claimedCount.toLong(),
                message = ex.message ?: "自动补全批次失败",
                executionId = executionId
            ), executionId)
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext(TASK_TYPE, execId)
            } else {
                progressStore.clearExecutionContext(TASK_TYPE, pendingToken)
            }
            if (progressStore.get(TASK_TYPE)?.status in RUNNING_STATUSES) {
                progressStore.clear(TASK_TYPE)
            }
        }
    }

    companion object {
        /** 与人工补全入口共用的任务类型：同一把互斥锁，自动与手动互相排斥。 */
        const val TASK_TYPE = EXPERT_ENRICHMENT_TASK_TYPE

        /** 专用批次执行线程的 bean 名（[DiscoveryExecutorConfig.autoEnrichmentExecutor]）。 */
        const val AUTO_ENRICHMENT_EXECUTOR = "autoEnrichmentExecutor"

        private val RUNNING_STATUSES = setOf("RUNNING", "CANCELLING")
    }
}
