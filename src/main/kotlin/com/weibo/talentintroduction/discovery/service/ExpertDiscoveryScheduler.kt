package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSetting
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSettingRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryScheduleSpec
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.TriggerContext
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.concurrent.ScheduledFuture

/**
 * I-3（03）：调度器的**已应用快照**。service 的 GET/PUT 只据此判定 `applied`/`nextTriggerAt`，
 * 不自己推算调度状态；04 不读数据库。
 */
data class DiscoveryScheduleSnapshot(
    val mode: String,
    /** 当前是否存在**有效代次**的定时 future。 */
    val registered: Boolean,
    val source: String,
    /** 已应用的小时周期；CONFIG 且部署 cron 是可识别的两小时时为 2，否则 null。 */
    val intervalHours: Int?,
    /** 已应用锚点（UTC）。 */
    val anchorAt: Instant?,
    /** 当前已应用计划的下一个候选触发时刻；无法计算/未注册/连续模式为 null。 */
    val nextTriggerAt: Instant?,
    /** 未按当前设置生效时的原因码；正常生效为 null。 */
    val reason: String?
)

/**
 * 深度发现的定时入口。
 *
 * - **旧模式**（`pipeline-enabled=false`，默认）：**唯一**动态 future 触发 —— 无已保存设置时按部署
 *   `cron`（默认「每偶数小时整点」，北京时间），有已保存设置时按「锚点 + N 整数小时」推进；
 *   每次触发最多跑一次同步 `discover`。**没有「本日已执行」闸门** —— 日内多轮读取原查询检查点续跑，
 *   被跳过的触发不补排；上一轮没结束时由统一运行槽跳过，不并发。
 * - **新模式**（`pipeline-enabled=true`，c3/I-4）：cron 与本类的恢复 tick **只调用 02 的 `tick()`**，
 *   不同步 `discover`、不睡眠等待配额/PDF；全局属主与同查询互斥由 02 的数据库状态
 *   保证，因此调度线程永不被后台窗口占用（其他定时任务不受影响）。小时设置对此模式**不生效**。
 * - `pipeline-enabled=true` 但尚无持久化启用记录时保持 `PAUSED`（02 的 `tick()` 自己判定不派发），
 *   绝不自动导入正在运行的旧进程。
 * - 永久停定时用既有 `cron=-` 配置；「取消任务」只打断本轮。
 *
 * I-2/I-3（03）：动态注册全部走 `SchedulingConfig.taskScheduler`（不新建线程池）；
 * 重排 = 递增代次 → `cancel(false)` 旧 future → 注册新 future，旧代次的晚到回调不得启动任务，
 * 已在运行的任务不被打断。
 */
@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery", name = ["enabled"], havingValue = "true")
class ExpertDiscoveryScheduler(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val progressStore: TaskProgressStore,
    /** I-1（c3）：02 协调者；末尾可选参数，既有构造调用逐字兼容（开关关闭时入口不碰它）。 */
    private val pipelineService: DiscoveryPipelineService? = null,
    /**
     * I-1（03）：小时设置的唯一读口（调度器只依赖 repository，不依赖 service，避免循环依赖）。
     * 缺失时按「无行」处理（只有人工构造的测试会缺省它）。
     */
    private val scheduleSettingRepository: DiscoveryScheduleSettingRepository? = null,
    /** I-2（03）：复用 `SchedulingConfig.taskScheduler`；不新建线程池，也不改发信调度。 */
    @Qualifier("taskScheduler") private val taskScheduler: TaskScheduler? = null,
    /** I-1（03）：周期锚点运算的可控时钟（测试用固定时钟，不用 sleep 等小时）。 */
    private val clock: Clock = Clock.systemUTC()
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(ExpertDiscoveryScheduler::class.java)

    /** I-3：代次与已应用状态的互斥 —— 只在「校验代次 + 占用运行槽」这类短操作里持有。 */
    private val lifecycleLock = Any()
    private var generation: Long = 0L
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var appliedSetting: DiscoveryScheduleSetting? = null
    private var appliedRegistered: Boolean = false
    private var appliedNextTriggerAt: Instant? = null
    private var appliedReason: String? = null

    /** I-2：最近一次同步发现**完成**时刻，参与下一未来槽计算（跨线程读写）。 */
    @Volatile
    private var lastCompletionAt: Instant? = null

    /**
     * I-6：初始化文案里的搜索恢复间隔（运营端据此知道等待序列）；空列表显示为「关闭」，
     * 表示回退既有「3 次短尝试后终止」。
     */
    private fun recoveryDelaysText(): String =
        discoveryProperties.openAlexSearchRecoveryDelays
            .joinToString("/") { "${it.seconds}s" }
            .ifEmpty { "关闭" }

    /**
     * I-4（c3）：恢复 tick，间隔 = 02 的 `pipeline-tick`（默认 30 秒）。
     * 旧模式（开关关闭）**不注册**任何 tick：它只由动态 future 触发同步 `discover`。
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

    // ------------------------------------------------------------------
    // I-1/I-3（03）：注册、重排与已应用快照
    // ------------------------------------------------------------------

    /**
     * I-3：保存提交后的**同步重排**入口（service 经 `ObjectProvider` 调用）。
     * 返回 true 仅当当前设置已由唯一有效代次的 future 应用；否则调用方报 `applied=false`，不得谎报成功。
     */
    fun reload(): Boolean = applySchedule("save")

    /**
     * I-4：启动时读取设置并注册**唯一** future。读取失败时不启动同步发现并显式告警
     * （绝不默默按旧默认频率继续跑）；重试保存即可恢复。
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        applySchedule("startup")
    }

    /** I-3：已应用快照（GET/PUT 的 `applied`/`source`/`nextTriggerAt` 判据）。 */
    fun snapshot(): DiscoveryScheduleSnapshot = synchronized(lifecycleLock) {
        DiscoveryScheduleSnapshot(
            mode = if (discoveryProperties.pipelineEnabled) {
                DiscoveryScheduleSpec.MODE_CONTINUOUS
            } else {
                DiscoveryScheduleSpec.MODE_LEGACY
            },
            registered = appliedRegistered,
            source = if (appliedSetting != null) {
                DiscoveryScheduleSpec.SOURCE_OVERRIDE
            } else {
                DiscoveryScheduleSpec.SOURCE_CONFIG
            },
            intervalHours = appliedSetting?.intervalHours
                ?: DiscoveryScheduleSpec.configuredIntervalHours(discoveryProperties.cron),
            anchorAt = appliedSetting?.updatedAt,
            // 连续模式的“下一次”不是小时周期计划，不返回一个仿佛已应用的小时计划。
            nextTriggerAt = if (discoveryProperties.pipelineEnabled) null else appliedNextTriggerAt,
            reason = when {
                appliedReason != null -> appliedReason
                appliedRegistered -> null
                else -> DiscoveryScheduleSpec.REASON_NOT_STARTED
            }
        )
    }

    private fun applySchedule(triggerSource: String): Boolean {
        if (discoveryProperties.pipelineEnabled) return applyContinuousCron(triggerSource)
        return applyLegacySchedule(triggerSource)
    }

    /**
     * I-4：连续模式保留**原 cron 触发**（该 future 只调用 02 的 `tick()`）+ `configureTasks` 的恢复 tick。
     * 小时设置在这里**不生效**（source=CONFIG、anchor=null）；`cron=-` 仍只停 cron，不影响恢复 tick。
     */
    private fun applyContinuousCron(triggerSource: String): Boolean {
        val cron = discoveryProperties.cron
        if (cron.trim() == DiscoveryScheduleSpec.DISABLED_CRON) {
            synchronized(lifecycleLock) {
                generation += 1
                cancelFutureLocked()
                appliedSetting = null
                appliedRegistered = false
                appliedNextTriggerAt = null
                appliedReason = DiscoveryScheduleSpec.REASON_CRON_DISABLED
            }
            log.info("深度发现定时[{}]：连续模式且 cron=-，只保留恢复 tick", triggerSource)
            return false
        }
        return installFuture(CronTrigger(cron, BEIJING_ZONE), setting = null, nextTriggerAt = nextCronTriggerAt(cron))
    }

    /**
     * I-4：同步模式 —— 无行用部署 cron（`CronTrigger(cron, Asia/Shanghai)`），有行用小时 Trigger。
     * 读取失败（连接/超时/存量值越界）时**不注册同步发现**并显式告警，保留现有定时不动。
     */
    private fun applyLegacySchedule(triggerSource: String): Boolean {
        val cron = discoveryProperties.cron
        if (cron.trim() == DiscoveryScheduleSpec.DISABLED_CRON) {
            // I-4：`cron=-` 的既有禁用语义优先 —— 即使库里留有历史小时设置，也不注册同步触发；
            // 页面 GET 因此必须返回 nextTriggerAt=null / editable=false（reason=CRON_DISABLED）。
            synchronized(lifecycleLock) {
                generation += 1
                cancelFutureLocked()
                appliedSetting = null
                appliedRegistered = false
                appliedNextTriggerAt = null
                appliedReason = DiscoveryScheduleSpec.REASON_CRON_DISABLED
            }
            log.info(
                "深度发现定时[{}]：cron=- 已停用同步定时（已保存的小时设置不复活它）",
                triggerSource
            )
            return false
        }
        val setting = try {
            scheduleSettingRepository?.find()
        } catch (ex: Exception) {
            // I-1/I-3：库不可用 ≠ 无行；不取消原调度，只标记未应用，等重试保存恢复。
            synchronized(lifecycleLock) {
                appliedReason = DiscoveryScheduleSpec.REASON_DB_UNAVAILABLE
            }
            log.error(
                "深度发现定时[{}]：读取小时设置失败，本次不重排（保留现有定时，重试保存可恢复）：{}",
                triggerSource, ex.message
            )
            return false
        }
        val intervalHours = setting?.intervalHours
        if (intervalHours == null) {
            return installFuture(CronTrigger(cron, BEIJING_ZONE), setting = null, nextTriggerAt = nextCronTriggerAt(cron))
        }
        val anchor = requireNotNull(setting).updatedAt
        return installFuture(
            DiscoveryHourIntervalTrigger(anchor, intervalHours, clock) { lastCompletionAt },
            setting = setting,
            nextTriggerAt = nextHourlyTriggerAt(anchor, intervalHours, currentBase())
        )
    }

    /**
     * I-2/I-3：安装新的唯一 future —— 递增代次 → `cancel(false)` 旧 future → 注册新 future。
     * 注册失败（或拿不到 `TaskScheduler`）时当前代次标记为未应用；绝不谎报旧调度仍有效。
     */
    private fun installFuture(
        trigger: Trigger,
        setting: DiscoveryScheduleSetting?,
        nextTriggerAt: Instant?
    ): Boolean {
        val scheduler = taskScheduler
        if (scheduler == null) {
            synchronized(lifecycleLock) {
                generation += 1
                cancelFutureLocked()
                appliedSetting = setting
                appliedRegistered = false
                appliedNextTriggerAt = null
                appliedReason = DiscoveryScheduleSpec.REASON_NOT_STARTED
            }
            log.error("深度发现定时：没有可用的 TaskScheduler，未注册定时（applied=false）")
            return false
        }
        val scheduledGeneration = synchronized(lifecycleLock) {
            generation += 1
            cancelFutureLocked()
            appliedSetting = setting
            appliedRegistered = false
            appliedNextTriggerAt = null
            appliedReason = DiscoveryScheduleSpec.REASON_NOT_STARTED
            generation
        }
        val future = try {
            scheduler.schedule({ onScheduledTrigger(scheduledGeneration) }, trigger)
        } catch (ex: Exception) {
            synchronized(lifecycleLock) {
                if (generation == scheduledGeneration) {
                    appliedRegistered = false
                    appliedNextTriggerAt = null
                    appliedReason = DiscoveryScheduleSpec.REASON_APPLY_FAILED
                }
            }
            log.error(
                "深度发现定时：注册失败（已保存值不会被应用，请重试保存）：{}",
                ex.message
            )
            return false
        }
        val registered = synchronized(lifecycleLock) {
            if (generation != scheduledGeneration) {
                // 重排期间又被更晚的代次取代：本次注册立即作废。
                future?.cancel(false)
                false
            } else {
                scheduledFuture = future
                appliedRegistered = future != null
                appliedNextTriggerAt = if (future != null) nextTriggerAt else null
                appliedReason = if (future != null) null else DiscoveryScheduleSpec.REASON_NOT_STARTED
                appliedRegistered
            }
        }
        if (registered) {
            log.info(
                "深度发现定时：已应用（source={}, intervalHours={}, anchor={}, next={}）",
                if (setting != null) DiscoveryScheduleSpec.SOURCE_OVERRIDE else DiscoveryScheduleSpec.SOURCE_CONFIG,
                setting?.intervalHours, setting?.updatedAt, nextTriggerAt
            )
        } else {
            log.error("深度发现定时：注册未生效（applied=false）")
        }
        return registered
    }

    /** I-3：取消旧 future 用 `cancel(false)` —— 已在运行的同步发现继续跑完，不被打断。 */
    private fun cancelFutureLocked() {
        scheduledFuture?.cancel(false)
        scheduledFuture = null
    }

    /** I-2：`base = max(now, 上次完成)`，锚点与间隔都用 `Instant` 做 Duration 运算。 */
    private fun currentBase(): Instant = maxOf(clock.instant(), lastCompletionAt ?: Instant.MIN)

    private fun nextHourlyTriggerAt(anchor: Instant, intervalHours: Int, base: Instant): Instant {
        val interval = Duration.ofHours(intervalHours.toLong()).toMillis()
        val elapsed = Duration.between(anchor, base).toMillis()
        // k = floor((base − T)/N) + 1，且至少 1：只给严格晚于 base、且不早于/不重复锚点的点。
        val steps = Math.max(1L, Math.floorDiv(elapsed, interval) + 1L)
        return anchor.plusMillis(steps * interval)
    }

    /** CONFIG 模式：下一 cron 触发点（Asia/Shanghai）；cron 非法时无法计算。 */
    private fun nextCronTriggerAt(cron: String): Instant? =
        try {
            CronExpression.parse(cron)
                .next(ZonedDateTime.ofInstant(clock.instant(), BEIJING_ZONE))
                ?.toInstant()
        } catch (ex: Exception) {
            log.warn("深度发现定时：无法解析 cron '{}'：{}", cron, ex.message)
            null
        }

    // ------------------------------------------------------------------
    // I-1/I-2/I-3/I-4：同步发现的触发实体
    // ------------------------------------------------------------------

    /**
     * 同步模式的定时入口（人工/直接调用无代次闸门；动态 future 用 [onScheduledTrigger]）。
     * 连续模式下只派发 02 的 `tick()`，不同步 `discover`。
     */
    fun scheduleDiscovery() {
        dispatchOrRunSync(generationAt = null)
    }

    /** I-3：动态 future 的实体 —— 旧代次（已重排或已取消）的晚到回调不得启动任务。 */
    private fun onScheduledTrigger(scheduledGeneration: Long) {
        dispatchOrRunSync(generationAt = scheduledGeneration)
    }

    private fun dispatchOrRunSync(generationAt: Long?) {
        val pipeline = continuousPipeline()
        if (pipeline != null) {
            // I-1/I-4：新模式只推进已保存的同一份配置；没有“今天已执行一次”的闸门，日内多窗口可续跑。
            dispatchTick(pipeline, "cron")
            return
        }
        startScheduledDiscovery(generationAt)
    }

    /**
     * I-2/I-3/P-4：小锁内只做「代次校验 + 占用统一运行槽」，**释放锁再**执行长时 `discover` ——
     * 保存不会被运行中的任务阻塞，运行中的任务也不会被 `cancel(false)` 打断。
     */
    private fun startScheduledDiscovery(generationAt: Long?) {
        val pendingToken: Long = synchronized(lifecycleLock) {
            if (generationAt != null && generationAt != generation) {
                log.debug("深度发现定时：忽略过期代次的回调（scheduled={}, current={}）", generationAt, generation)
                return
            }
            val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = initialProgressMessageLocked()
            ))
            if (!started) return
            token
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
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", pendingToken, id)
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
                progressStore.clearExecutionContext("EXPERT_DISCOVERY", pendingToken)
            }
            // I-2：本轮完成时刻参与下一未来槽计算；轮内跨过的槽不补排（下一槽由 Trigger 在执行后再算）。
            lastCompletionAt = clock.instant()
        }
    }

    /**
     * I-6/03：初始化文案说明**实际生效**的触发（cron 或已保存的小时周期），
     * 运营端据此知道这一轮为什么在这个时刻启动。
     */
    private fun initialProgressMessageLocked(): String {
        val setting = appliedSetting
        val trigger = if (setting != null) {
            "间隔=${setting.intervalHours}小时, 锚点(UTC)=${setting.updatedAt}"
        } else {
            "cron=${discoveryProperties.cron}, 时区 ${BEIJING_ZONE.id}"
        }
        return "初始化定时深度发现: $trigger, 搜索恢复间隔=${recoveryDelaysText()}"
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

    /**
     * I-2：锚点 T、间隔 N 小时的 Trigger —— `next = T + (floor((base − T)/N) + 1) × N`，`base` 取
     * 「当前时间」与「上次完成时间」的较晚者。
     *
     * - 只返回**严格晚于 base** 的点，因此不会立即执行、不会补排漏掉的槽、不会因跨午夜改周期；
     * - 由 `TaskScheduler` 在上一轮**执行结束后**再问下一次时间（等价于 fixed-delay 语义），
     *   绝不使用会追补过期周期的 `scheduleAtFixedRate`。
     */
    private class DiscoveryHourIntervalTrigger(
        private val anchor: Instant,
        private val intervalHours: Int,
        private val clock: Clock,
        private val lastCompletion: () -> Instant?
    ) : Trigger {

        override fun nextExecutionTime(context: TriggerContext): Date {
            val interval = Duration.ofHours(intervalHours.toLong()).toMillis()
            val base = maxOf(
                clock.instant(),
                context.lastCompletionTime()?.toInstant() ?: Instant.MIN,
                lastCompletion() ?: Instant.MIN
            )
            val elapsed = Duration.between(anchor, base).toMillis()
            val steps = Math.max(1L, Math.floorDiv(elapsed, interval) + 1L)
            return Date.from(anchor.plusMillis(steps * interval))
        }
    }

    private companion object {
        val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
