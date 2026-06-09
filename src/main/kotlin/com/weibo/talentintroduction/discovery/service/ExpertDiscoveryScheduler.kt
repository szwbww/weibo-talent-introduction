package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.expert-discovery", name = ["enabled"], havingValue = "true")
class ExpertDiscoveryScheduler(
    private val discoveryService: ExpertDiscoveryService,
    private val taskExecutionService: TaskExecutionService,
    private val discoveryProperties: ExpertDiscoveryProperties,
    private val progressStore: TaskProgressStore
) {
    @Scheduled(cron = "\${talent-introduction.expert-discovery.cron:-}")
    fun scheduleDiscovery() {
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
                openAccessOnly = true
            )
            taskExecutionService.runAndRecord("EXPERT_DISCOVERY", "SCHEDULED", criteria,
                onStarted = { id ->
                    executionId = id
                    progressStore.bindExecutionId("EXPERT_DISCOVERY", token, id)
                }
            ) {
                discoveryService.discover(criteria, "SCHEDULED")
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
}
