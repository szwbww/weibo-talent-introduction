package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.discovery.service.ArxivDataSource
import com.weibo.talentintroduction.discovery.service.CoreDataSource
import com.weibo.talentintroduction.discovery.service.CrossrefDataSource
import com.weibo.talentintroduction.discovery.service.DiscoveryPipelineService
import com.weibo.talentintroduction.discovery.service.DiscoveryPipelineStatus
import com.weibo.talentintroduction.discovery.service.EnrichmentScope
import com.weibo.talentintroduction.discovery.service.EnrichmentStats
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.discovery.service.OpenAlexDataSource
import com.weibo.talentintroduction.discovery.service.OrcidDataSource
import com.weibo.talentintroduction.discovery.service.PipelineLaunchResult
import com.weibo.talentintroduction.discovery.service.PipelineRejectionReason
import com.weibo.talentintroduction.discovery.service.PmcOaDataSource
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskLaunchResponse
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * I-1（c3）：`pipeline-enabled=true` 却拿不到 c2 协调者时的唯一出口 —— fail loudly。
 * 由本控制器自己的 `@ExceptionHandler` 映射为 503，绝不静默回退旧同步发现。
 */
class DiscoveryPipelineUnavailableException(message: String) : IllegalStateException(message)

@RestController
@RequestMapping("/api/expert-discovery")
class ExpertDiscoveryController(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore,
    @Qualifier("enrichmentExecutor")
    private val enrichmentExecutor: Executor,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val openAlexProvider: ObjectProvider<OpenAlexDataSource>,
    private val crossrefProvider: ObjectProvider<CrossrefDataSource>,
    private val arxivProvider: ObjectProvider<ArxivDataSource>,
    private val pmcOaProvider: ObjectProvider<PmcOaDataSource>,
    private val orcidProvider: ObjectProvider<OrcidDataSource>,
    private val coreProvider: ObjectProvider<CoreDataSource>,
    private val europePmcProperties: EuropePmcProperties,
    /**
     * I-1（c3）：02 协调者。末尾可选参数 —— 既有构造测试调用逐字兼容；生产装配（`@Service`）永远存在，
     * `pipeline-enabled=false` 时入口仍走原同步逻辑。
     */
    private val pipelineService: DiscoveryPipelineService? = null
) {
    private val log = LoggerFactory.getLogger(ExpertDiscoveryController::class.java)

    @GetMapping("/sources")
    fun getAvailableSources(): List<Map<String, Any>> {
        val excludedSources = SubjectScopeCatalog.excludedSources(SubjectScopeCatalog.RND_TARGET)
        val all = listOf(
            Triple("EUROPE_PMC", europePmcProperties.enabled && "EUROPE_PMC" !in excludedSources, "FULLTEXT_XML"),
            Triple("PMC_OA", pmcOaProvider.getIfAvailable() != null && "PMC_OA" !in excludedSources, "FULLTEXT_XML"),
            Triple("OPENALEX", openAlexProvider.getIfAvailable() != null, "FULLTEXT_XML"),
            Triple("CROSSREF", crossrefProvider.getIfAvailable() != null, "PDF_PARSE"),
            Triple("CORE", coreProvider.getIfAvailable() != null, "FULLTEXT_TEXT"),
            Triple("ARXIV", arxivProvider.getIfAvailable() != null, "PDF_PARSE"),
            Triple("ORCID", orcidProvider.getIfAvailable() != null, "API_FIELD")
        )
        return all.map { (name, enabled, method) ->
            mapOf(
                "sourceName" to name,
                // I-1（c3）：与队列同一套来源规则 —— RND_TARGET 下生物医学两源本次不参与，
                // 前端不得再把它们当作可提交来源（提交了也会被同一规则排除）。
                "enabled" to (enabled && name !in SubjectScopeCatalog.excludedSources(SubjectScopeCatalog.RND_TARGET)),
                "extractionMethod" to method
            )
        }
    }

    @PostMapping("/run")
    fun triggerDiscovery(
        @RequestBody(required = false) criteria: PaperSearchCriteria?,
        @RequestParam(required = false) includeRawScan: Boolean? = null
    ): ResponseEntity<Any> {
        // I-1（c3）：开关打开时两个手动入口都只调用 02.launch —— 同一份已保存配置、同一份规范化查询。
        val pipeline = continuousPipeline()
        if (pipeline != null) {
            return launchContinuousPipeline(
                pipeline,
                normalizeDiscoveryCriteria(criteria),
                includeRawScan ?: discoveryProperties.includeRawScan
            )
        }
        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
            taskType = "EXPERT_DISCOVERY", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        var execution: TaskExecution? = null
        var executionId: Long? = null
        try {
            val effectiveCriteria = (criteria ?: PaperSearchCriteria(
                excludeCountries = listOf("CN"),
                openAccessOnly = true
            )).copy(subjectScope = SubjectScopeCatalog.RND_TARGET)
            execution = taskExecutionService.runAndRecord(
                "EXPERT_DISCOVERY", "MANUAL", effectiveCriteria,
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
                }
            ) {
                discoveryService.discover(
                    effectiveCriteria,
                    "MANUAL",
                    includeRawScan = includeRawScan ?: discoveryProperties.includeRawScan
                )
            }
            if (execution.status == "FAILED") {
                val existing = progressStore.get("EXPERT_DISCOVERY")
                progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                    status = "FAILED",
                    message = execution.errorMessage ?: "任务执行失败"
                ) ?: TaskProgress(
                    taskType = "EXPERT_DISCOVERY", status = "FAILED",
                    batchNumber = 0, processedCount = 0, totalCount = 0,
                    message = execution.errorMessage ?: "任务执行失败"
                ), executionId)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("message" to (execution.errorMessage ?: "任务执行失败")))
            }
            return ResponseEntity.ok(TaskLaunchResponse(execution.id!!, execution))
        } catch (ex: Exception) {
            val existing = progressStore.get("EXPERT_DISCOVERY")
            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                status = "FAILED",
                message = ex.message ?: "初始化失败"
            ) ?: TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            throw ex
        } finally {
            val execId = executionId
            if (execId != null) {
                progressStore.clearExecutionContext("EXPERT_DISCOVERY", execId)
            } else {
                progressStore.clearExecutionContext("EXPERT_DISCOVERY", token)
            }
        }
    }

    @PostMapping("/run/by-keyword")
    fun triggerDiscoveryByKeyword(
        @RequestParam keywords: List<String>,
        @RequestParam(defaultValue = "2020") yearFrom: Int,
        @RequestParam(defaultValue = "2026") yearTo: Int,
        @RequestParam(required = false) sources: List<String>? = null,
        @RequestParam(required = false) includeRawScan: Boolean? = null
    ): ResponseEntity<Any> {
        // I-1（c3）：与 /run 完全相同的规范化与同一份已保存配置（关键词只是条件的一部分）。
        val pipeline = continuousPipeline()
        if (pipeline != null) {
            return launchContinuousPipeline(
                pipeline,
                normalizeDiscoveryCriteria(
                    PaperSearchCriteria(
                        keywords = keywords,
                        publicationYearFrom = yearFrom,
                        publicationYearTo = yearTo,
                        excludeCountries = listOf("CN"),
                        openAccessOnly = true,
                        sources = sources ?: emptyList()
                    )
                ),
                includeRawScan ?: discoveryProperties.includeRawScan
            )
        }
        val (started, token) = progressStore.tryStartWithToken("EXPERT_DISCOVERY", TaskProgress(
            taskType = "EXPERT_DISCOVERY", status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        var execution: TaskExecution? = null
        var executionId: Long? = null
        try {
            val criteria = PaperSearchCriteria(
                keywords = keywords,
                publicationYearFrom = yearFrom,
                publicationYearTo = yearTo,
                excludeCountries = listOf("CN"),
                openAccessOnly = true,
                sources = sources ?: emptyList(),
                subjectScope = SubjectScopeCatalog.RND_TARGET
            )
            execution = taskExecutionService.runAndRecord(
                "EXPERT_DISCOVERY", "MANUAL", criteria,
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
                }
            ) {
                discoveryService.discover(
                    criteria,
                    "MANUAL",
                    includeRawScan = includeRawScan ?: discoveryProperties.includeRawScan
                )
            }
            if (execution.status == "FAILED") {
                val existing = progressStore.get("EXPERT_DISCOVERY")
                progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                    status = "FAILED",
                    message = execution.errorMessage ?: "任务执行失败"
                ) ?: TaskProgress(
                    taskType = "EXPERT_DISCOVERY", status = "FAILED",
                    batchNumber = 0, processedCount = 0, totalCount = 0,
                    message = execution.errorMessage ?: "任务执行失败"
                ), executionId)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("message" to (execution.errorMessage ?: "任务执行失败")))
            }
            return ResponseEntity.ok(TaskLaunchResponse(execution.id!!, execution))
        } catch (ex: Exception) {
            val existing = progressStore.get("EXPERT_DISCOVERY")
            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                status = "FAILED",
                message = ex.message ?: "初始化失败"
            ) ?: TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ), executionId)
            throw ex
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
     * I-2/I-5/I-7（c3）：新模式进度与配置的**唯一**读口 —— 只透出 02 的 `status()`
     * （`state`/`phase`/`desiredState`/逐源明细/计数/等待原因/下次唤醒 + 01 的预算快照）。
     * 无已保存查询时 `configured=false` 且 `state=PAUSED`；**绝不**用旧 task 记录的最新一条冒充当前流水线。
     */
    @GetMapping("/pipeline")
    fun getPipelineStatus(): Map<String, Any?> {
        val pipeline = continuousPipeline()
            ?: return mapOf(
                "mode" to LEGACY_MODE,
                "configured" to false,
                "state" to null,
                "stateText" to null,
                "waitTexts" to emptyList<String>(),
                "status" to null
            )
        val status = pipeline.status()
        return mapOf(
            "mode" to CONTINUOUS_MODE,
            "configured" to (status.queryHash != null),
            "state" to status.state,
            "stateText" to pipelineStateText(status.state),
            "waitTexts" to waitReasonTexts(status),
            "status" to status
        )
    }

    /**
     * I-3（c3）：恢复是**显式用户操作**，只恢复已保存的条件（无配置 → 409）。
     * 关闭弹窗、重启、日切都不会走这里。
     */
    @PostMapping("/pipeline/resume")
    fun resumePipeline(): ResponseEntity<Any> {
        val pipeline = continuousPipeline()
            ?: return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("mode" to LEGACY_MODE, "message" to "当前为旧同步模式，无需恢复深度发现"))
        val result = try {
            pipeline.resume()
        } catch (ex: Exception) {
            log.warn("深度发现恢复未持久化: {}", ex.message)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("message" to "深度发现恢复未保存成功，请稍后重试"))
        }
        return continuousLaunchResponse(result)
    }

    @GetMapping("/enrich/stats")
    fun getEnrichmentStats(): EnrichmentStats {
        return discoveryService.getEnrichmentStats()
    }

    @PostMapping("/enrich")
    fun enrichExperts(
        @RequestParam(required = false) scope: EnrichmentScope? = null
    ): ResponseEntity<Any> {
        val taskType = "EXPERT_ENRICHMENT"
        val (started, pendingToken) = progressStore.tryStartWithToken(taskType, TaskProgress(
            taskType = taskType, status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        val enrichmentScope = scope ?: EnrichmentScope.DEFAULT

        try {
            enrichmentExecutor.execute {
                var executionId: Long? = null
                try {
                    taskExecutionService.runAndRecordWithResult(
                        taskType, "MANUAL", emptyMap<String, Any>(),
                        onStarted = { id ->
                            executionId = id
                            progressStore.bindExecutionId(taskType, pendingToken, id)
                        }
                    ) {
                        discoveryService.enrichExistingExperts(enrichmentScope)
                    }
                } catch (ex: Exception) {
                    progressStore.update(taskType, TaskProgress(
                        taskType = taskType, status = "FAILED",
                        batchNumber = 0, processedCount = 0, totalCount = 0,
                        message = ex.message ?: "初始化失败",
                        executionId = executionId
                    ), executionId)
                } finally {
                    val execId = executionId
                    if (execId != null) {
                        progressStore.clearExecutionContext(taskType, execId)
                    } else {
                        progressStore.clearExecutionContext(taskType, pendingToken)
                    }
                    val remaining = progressStore.get(taskType)
                    if (remaining?.status in setOf("RUNNING", "CANCELLING")) {
                        progressStore.clear(taskType)
                    }
                }
            }
        } catch (reEx: RejectedExecutionException) {
            progressStore.clear(taskType)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务启动失败，请稍后重试"))
        }

        return ResponseEntity.accepted().body(mapOf("message" to "任务已启动"))
    }

    // ------------------------------------------------------------------
    // I-1/I-2/I-3/I-5（c3）：新模式入口的共享接缝（controller 只调用 02 的 launch/pause/resume/status）
    // ------------------------------------------------------------------

    /**
     * I-1/I-4（c3）：新旧模式的**唯一**判定点。
     * - `pipeline-enabled=false`（默认）→ null，两个 POST 与原 cron 的同步语义逐字保留；
     * - `pipeline-enabled=true` → 02 协调者，入口只调用它的持久化控制接口；
     * - 开关已打开却拿不到协调者 → [DiscoveryPipelineUnavailableException]（503），绝不静默回退。
     */
    private fun continuousPipeline(): DiscoveryPipelineService? {
        val service = pipelineService
        if (service == null) {
            if (discoveryProperties.pipelineEnabled) {
                throw DiscoveryPipelineUnavailableException(
                    "深度发现新模式不可用：pipeline-enabled=true 但 DiscoveryPipelineService 未装配"
                )
            }
            return null
        }
        return service.takeIf { it.enabled }
    }

    /**
     * I-1（c3）：两个手动入口的**同一**规范化 —— 固定 `scope=RND_TARGET`（与定时发现、队列同一学科范围）、
     * 清空临时游标。来源由 02 按同一套「已启用 + 未被学科范围排除」规则解析，因此两个 POST 的有效条件相同。
     */
    private fun normalizeDiscoveryCriteria(criteria: PaperSearchCriteria?): PaperSearchCriteria =
        (criteria ?: PaperSearchCriteria(excludeCountries = listOf("CN"), openAccessOnly = true))
            .copy(subjectScope = SubjectScopeCatalog.RND_TARGET, cursor = null)

    /** I-2（c3）：HTTP 受理与实际完成严格分开 —— 只有配置真正持久化后才回 202。 */
    private fun launchContinuousPipeline(
        pipeline: DiscoveryPipelineService,
        criteria: PaperSearchCriteria,
        includeRawScan: Boolean
    ): ResponseEntity<Any> {
        val result = try {
            pipeline.launch(criteria, MANUAL_TRIGGER, includeRawScan)
        } catch (ex: Exception) {
            log.warn("深度发现配置未持久化: {}", ex.message)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("message" to "深度发现配置未保存成功，请稍后重试"))
        }
        return continuousLaunchResponse(result)
    }

    /**
     * I-2：`applied=false` 一律 409（不同查询仍有在手工作 / 没有已保存查询）；
     * 成功一律 202 + `mode=CONTINUOUS`、`pipelineId`、`phase`、可空 `executionId`。
     * 202 **不是**完成，前端不得据此显示“专家发现完成”。
     */
    private fun continuousLaunchResponse(result: PipelineLaunchResult): ResponseEntity<Any> {
        if (!result.applied) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "mode" to CONTINUOUS_MODE,
                    "reason" to (result.rejection?.reason ?: PipelineRejectionReason.QUERY_CONFLICT),
                    "message" to (result.rejection?.message ?: "已有不同的深度发现查询在运行"),
                    "pipelineId" to result.pipelineId,
                    "phase" to result.phase,
                    "desiredState" to result.desiredState,
                    "executionId" to result.executionId,
                    "resumed" to result.resumed
                )
            )
        }
        return ResponseEntity.accepted().body(
            mapOf(
                "mode" to CONTINUOUS_MODE,
                "message" to pipelineStateText(result.state),
                "pipelineId" to result.pipelineId,
                "state" to result.state,
                "phase" to result.phase,
                "desiredState" to result.desiredState,
                "queryHash" to result.queryHash,
                "executionId" to result.executionId,
                "resumed" to result.resumed
            )
        )
    }

    /** I-5：状态文案（状态名与原因文字同时可见，不只靠颜色）。未知状态不猜测、不丢弃。 */
    private fun pipelineStateText(state: String): String = when (state) {
        PipelinePhase.QUEUED -> "已受理，等待执行"
        PipelinePhase.RUNNING -> "正在采集和处理"
        PipelinePhase.WAITING -> "等待下一轮调度"
        PipelinePhase.DRAINED -> "已排空，没有待处理工作"
        PipelinePhase.FAULTED -> "运行失败，请查看等待原因与来源明细"
        PipelineDesiredState.PAUSED -> "已暂停，需手动恢复"
        else -> "持续运行"
    }

    /**
     * I-5：等待原因文案 —— 七个固定原因各有专门文案，多个同时出现；
     * 来源级原始错误串（02 会原样并入 `waitReasons`）**原样透出**，由前端纯文本显示，绝不放大为整体失败。
     * `DAILY_BUDGET` 的真实重置时刻取自 01 的预算快照，不写死“下一天”。
     */
    private fun waitReasonTexts(status: DiscoveryPipelineStatus): List<String> {
        val resetAt = status.budget.resetAt ?: status.budget.retryAt
        return status.waitReasons.map { reason ->
            when (reason) {
                PipelineWaitReason.DAILY_BUDGET -> "OpenAlex 官方额度已用尽，等待" +
                    (resetAt?.let { formatBeijing(it) + " " } ?: "额度重置后") + "重置"
                PipelineWaitReason.QUEUE_FULL -> "队列已满，正在处理已采集论文"
                PipelineWaitReason.RATE_LIMIT -> "来源限流，稍后自动重试"
                PipelineWaitReason.ENRICHMENT_RESERVE -> "为学术补全保留额度，暂缓消耗"
                PipelineWaitReason.BUDGET_SYNC -> "预算数据待同步，暂不能消耗官方额度"
                PipelineWaitReason.OWNER_RECOVERY -> "上一个窗口正在收尾，等待释放后再开新窗口"
                PipelineWaitReason.SOURCE_ERROR -> "部分来源报错，其余来源继续（详见来源明细）"
                PipelineWaitReason.MANUAL_PAUSE -> "已暂停，需手动恢复"
                PipelineWaitReason.WINDOW_END -> "本轮窗口已结束，等待下一轮续跑"
                PipelineWaitReason.SOURCE_EXHAUSTED -> "所有来源已穷尽"
                else -> reason
            }
        }.distinct()
    }

    private fun formatBeijing(instant: Instant): String =
        BEIJING_FORMATTER.format(LocalDateTime.ofInstant(instant, BEIJING_ZONE))

    /**
     * I-1（c3）：`pipeline-enabled=true` 却没有 02 协调者 → 503（操作端可重试），
     * 而不是悄悄跑旧同步发现。
     */
    @ExceptionHandler(DiscoveryPipelineUnavailableException::class)
    fun handlePipelineUnavailable(ex: DiscoveryPipelineUnavailableException): ResponseEntity<Map<String, String>> {
        log.error("深度发现新模式不可用: {}", ex.message)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(mapOf("mode" to CONTINUOUS_MODE, "message" to (ex.message ?: "深度发现新模式不可用")))
    }

    companion object {
        private const val CONTINUOUS_MODE = "CONTINUOUS"
        private const val LEGACY_MODE = "LEGACY"
        private const val MANUAL_TRIGGER = "MANUAL"
        private val BEIJING_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        private val BEIJING_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("北京时间 yyyy-MM-dd HH:mm")
    }
}
