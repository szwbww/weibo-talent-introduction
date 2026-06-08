package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.discovery.domain.DiscoveryResult
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.task.service.TaskExecutionService
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
    private val taskExecutionService: TaskExecutionService
) {
    @PostMapping("/run")
    fun triggerDiscovery(@RequestBody(required = false) criteria: PaperSearchCriteria?): ResponseEntity<Any> {
        val searchCriteria = criteria ?: PaperSearchCriteria(
            excludeCountries = listOf("CN"),
            openAccessOnly = true
        )
        val execution = taskExecutionService.runAndRecord(
            "EXPERT_DISCOVERY", "MANUAL", searchCriteria
        ) {
            discoveryService.discover(searchCriteria, "MANUAL")
        }
        return ResponseEntity.ok(execution)
    }

    @PostMapping("/run/by-keyword")
    fun triggerDiscoveryByKeyword(
        @RequestParam keywords: List<String>,
        @RequestParam(defaultValue = "2020") yearFrom: Int,
        @RequestParam(defaultValue = "2026") yearTo: Int
    ): ResponseEntity<Any> {
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
        return ResponseEntity.ok(execution)
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
