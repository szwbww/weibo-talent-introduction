package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/expert-discovery")
class ExpertDiscoveryController(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val progressStore: TaskProgressStore
) {
    @PostMapping("/run")
    fun triggerDiscovery(@RequestBody(required = false) criteria: PaperSearchCriteria?): ResponseEntity<Any> {
        if (!progressStore.tryStart("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
            ))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        try {
            val execution = taskExecutionService.runAndRecord(
                "EXPERT_DISCOVERY", "MANUAL", criteria ?: PaperSearchCriteria(
                    excludeCountries = listOf("CN"),
                    openAccessOnly = true
                )
            ) {
                discoveryService.discover(criteria ?: PaperSearchCriteria(
                    excludeCountries = listOf("CN"),
                    openAccessOnly = true
                ), "MANUAL")
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
                ))
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("message" to (execution.errorMessage ?: "任务执行失败")))
            }
            return ResponseEntity.ok(execution)
        } catch (ex: Exception) {
            val existing = progressStore.get("EXPERT_DISCOVERY")
            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                status = "FAILED",
                message = ex.message ?: "初始化失败"
            ) ?: TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ))
            throw ex
        }
    }

    @PostMapping("/run/by-keyword")
    fun triggerDiscoveryByKeyword(
        @RequestParam keywords: List<String>,
        @RequestParam(defaultValue = "2020") yearFrom: Int,
        @RequestParam(defaultValue = "2026") yearTo: Int
    ): ResponseEntity<Any> {
        if (!progressStore.tryStart("EXPERT_DISCOVERY", TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = 0, processedCount = 0, totalCount = 0, message = "初始化中..."
            ))) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("message" to "任务正在执行中，请等待完成"))
        }
        try {
            val criteria = PaperSearchCriteria(
                keywords = keywords,
                publicationYearFrom = yearFrom,
                publicationYearTo = yearTo,
                excludeCountries = listOf("CN"),
                openAccessOnly = true
            )
            val execution = taskExecutionService.runAndRecord(
                "EXPERT_DISCOVERY", "MANUAL", criteria
            ) {
                discoveryService.discover(criteria, "MANUAL")
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
                ))
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("message" to (execution.errorMessage ?: "任务执行失败")))
            }
            return ResponseEntity.ok(execution)
        } catch (ex: Exception) {
            val existing = progressStore.get("EXPERT_DISCOVERY")
            progressStore.update("EXPERT_DISCOVERY", existing?.copy(
                status = "FAILED",
                message = ex.message ?: "初始化失败"
            ) ?: TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "FAILED",
                batchNumber = 0, processedCount = 0, totalCount = 0,
                message = ex.message ?: "初始化失败"
            ))
            throw ex
        }
    }

    @PostMapping("/enrich")
    fun enrichExperts(
        @RequestParam(defaultValue = "500") maxExperts: Int
    ): ResponseEntity<Any> {
        val execution = taskExecutionService.runAndRecord(
            "EXPERT_ENRICHMENT", "MANUAL", mapOf("maxExperts" to maxExperts)
        ) {
            discoveryService.enrichExistingExperts(maxExperts)
        }
        return ResponseEntity.ok(execution)
    }
}
