package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * 深度发现的定时入口。
 *
 * - **旧模式**（`pipeline-enabled=false`，默认）：与原实现逐字一致 —— 每天一次、先查“当天已存在
 *   SCHEDULED 执行”再同步 `discover`。
 * - **新模式**（`pipeline-enabled=true`，c3/I-4）：cron 与本类的恢复 tick **只调用 02 的 `tick()`**，
 *   不查“今天已跑一次”、不同步 `discover`、不睡眠等待配额/PDF；全局属主与同查询互斥由 02 的数据库状态
 *   保证，因此调度线程永不被后台窗口占用（其他定时任务不受影响）。
 * - `pipeline-enabled=true` 但尚无持久化启用记录时保持 `PAUSED`（02 的 `tick()` 自己判定不派发），
 *   绝不自动导入正在运行的旧进程。
 */
@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery", name = ["enabled"], havingValue = "true")
class ExpertDiscoveryScheduler(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val progressStore: TaskProgressStore,
    /** I-1（c3）：02 协调者；末尾可选参数，既有构造调用逐字兼容（开关关闭时入口不碰它）。 */
    private val pipelineService: DiscoveryPipelineService? = null
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(ExpertDiscoveryScheduler::class.java)

    /**
     * I-4（c3）：恢复 tick，间隔 = 02 的 `pipeline-tick`（默认 30 秒）。
     * 旧模式（开关关闭）**不注册**任何 tick，因此“旧 cron 每天一次”的语义保持不变。
     * 这里用 `SchedulingConfigurer` 而不是 `@Scheduled(fixedDelayString=…)`，因为属性是 Spring 的
     * Duration（`30s`）而 `fixedDelayString` 只接受毫秒数或 ISO-8601 串。
     */
    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        if (!discoveryProperties.pipelineEnabled) return
        taskRegistrar.addFixedDelayTask(
            Runnable { recoverPipelineTick() },
            discoveryProperties.pipelineTick.toMillis()
        )
    }

    @Scheduled(cron = "\${talent-introduction.expert-discovery.cron:-}")
    fun scheduleDiscovery() {
        val pipeline = continuousPipeline()
        if (pipeline != null) {
            // I-1/I-4：新模式只推进已保存的同一份配置；没有“今天已执行一次”的闸门，日内多窗口可续跑。
            dispatchTick(pipeline, "cron")
            return
        }
        val todayStart = LocalDate.now().atStartOfDay()
        if (taskExecutionService.countScheduledSince("EXPERT_DISCOVERY", todayStart) > 0) {
            log.info("今日已存在 SCHEDULED 深度发现执行，跳过本次触发 (since={})", todayStart)
            return
        }
        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
            taskType = "EXPERT_DISCOVERY", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化 EuropePMC 搜索..."
        ))
        if (!started) {
            return
        }
        var executionId: Long? = null
        try {
            val criteria = PaperSearchCriteria(
                excludeCountries = listOf("CN"),
                openAccessOnly = true,
                subjectScope = SubjectScopeCatalog.RND_TARGET
            )
            taskExecutionService.runAndRecord("EXPERT_DISCOVERY", "SCHEDULED", criteria,
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
                }
            ) {
                discoveryService.discover(criteria, "SCHEDULED", discoveryProperties.includeRawScan)
            }
        } catch (ex: Exception) {
            progressStore.update("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "定时发现初始化失败"
            ), executionId)
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext("EXPERT_DISCOVERY", execId)
            } else {
                progressStore.clearExecutionContext("EXPERT_DISCOVERY", token)
            }
        }
    }

    /**
     * I-4（c3）：30 秒恢复 tick 的实体。暂停、无配置、别人持有有效属主、未到 `next_wake_at`
     * 都由 02 的 `tick()` 判定为“本 tick 什么都不做”，这里不做任何本地状态推断。
     */
    fun recoverPipelineTick() {
        val pipeline = continuousPipeline() ?: return
        dispatchTick(pipeline, "recovery-tick")
    }

    private fun dispatchTick(pipeline: DiscoveryPipelineService, trigger: String) {
        try {
            val result = pipeline.tick()
            log.debug(
                "深度发现流水线 tick({}): dispatched={}, state={}, skipReason={}",
                trigger, result.dispatched, result.state, result.skipReason
            )
        } catch (ex: Exception) {
            // 派发失败不得让调度线程带着异常退出：数据库状态保持不变，下一 tick 重新判定。
            log.warn("深度发现流水线 tick({}) 失败: {}", trigger, ex.message)
        }
    }

    /** 与 controller 同一判定：开关关闭 → null（旧语义保留）；开关打开却拿不到协调者 → 明确失败。 */
    private fun continuousPipeline(): DiscoveryPipelineService? {
        val service = pipelineService
        if (service == null) {
            check(!discoveryProperties.pipelineEnabled) {
                "pipeline-enabled=true 但 DiscoveryPipelineService 未装配，拒绝回退同步旧路径"
            }
            return null
        }
        return service.takeIf { it.enabled }
    }
}
