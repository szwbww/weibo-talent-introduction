package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery", name = ["enabled"], havingValue = "true")
class ExpertDiscoveryScheduler(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val discoveryProperties: ExpertDiscoveryProperties
) {
    @Scheduled(cron = "\${talent-introduction.expert-discovery.cron:-}")
    fun scheduleDiscovery() {
        val criteria = PaperSearchCriteria(
            excludeCountries = listOf("CN"),
            openAccessOnly = true
        )
        taskExecutionService.runAndRecord("EXPERT_DISCOVERY", "SCHEDULED", criteria) {
            discoveryService.discover(criteria, "SCHEDULED")
        }
    }
}
