package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.service.ArxivDataSource
import com.weibo.talentintroduction.discovery.service.CoreDataSource
import com.weibo.talentintroduction.discovery.service.CrossrefDataSource
import com.weibo.talentintroduction.discovery.service.EnrichmentStats
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.discovery.service.OpenAlexDataSource
import com.weibo.talentintroduction.discovery.service.OrcidDataSource
import com.weibo.talentintroduction.discovery.service.PmcOaDataSource
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskLaunchResponse
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

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
    private val europePmcProperties: EuropePmcProperties
) {
    @GetMapping("/sources")
    fun getAvailableSources(): List<Map<String, Any>> {
        val all = listOf(
            Triple("EUROPE_PMC", europePmcProperties.enabled, "FULLTEXT_XML"),
            Triple("PMC_OA", pmcOaProvider.getIfAvailable() != null, "FULLTEXT_XML"),
            Triple("OPENALEX", openAlexProvider.getIfAvailable() != null, "FULLTEXT_XML"),
            Triple("CROSSREF", crossrefProvider.getIfAvailable() != null, "PDF_PARSE"),
            Triple("CORE", coreProvider.getIfAvailable() != null, "FULLTEXT_TEXT"),
            Triple("ARXIV", arxivProvider.getIfAvailable() != null, "PDF_PARSE"),
            Triple("ORCID", orcidProvider.getIfAvailable() != null, "API_FIELD")
        )
        return all.map { (name, enabled, method) ->
            mapOf("sourceName" to name, "enabled" to enabled, "extractionMethod" to method)
        }
    }

    @PostMapping("/run")
    fun triggerDiscovery(
        @RequestBody(required = false) criteria: PaperSearchCriteria?,
        @RequestParam(required = false) includeRawScan: Boolean? = null
    ): ResponseEntity<Any> {
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
            execution = taskExecutionService.runAndRecord(
                "EXPERT_DISCOVERY", "MANUAL", criteria ?: PaperSearchCriteria(
                    excludeCountries = listOf("CN"),
                    openAccessOnly = true
                ),
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
                }
            ) {
                discoveryService.discover(
                    criteria ?: PaperSearchCriteria(
                        excludeCountries = listOf("CN"),
                        openAccessOnly = true
                    ),
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
                sources = sources ?: emptyList()
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

    @GetMapping("/enrich/stats")
    fun getEnrichmentStats(): EnrichmentStats {
        return discoveryService.getEnrichmentStats()
    }

    @PostMapping("/enrich")
    fun enrichExperts(): ResponseEntity<Any> {
        val taskType = "EXPERT_ENRICHMENT"
        val (started, pendingToken) = progressStore.tryStartWithToken(taskType, TaskProgress(
            taskType = taskType, status = "RUNNING",
            batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
        ))
        if (!started) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }

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
                        discoveryService.enrichExistingExperts()
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
}
