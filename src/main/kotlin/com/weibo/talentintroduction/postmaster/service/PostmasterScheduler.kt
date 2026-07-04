package com.weibo.talentintroduction.postmaster.service

import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.postmaster", name = ["enabled"], havingValue = "true")
class PostmasterScheduler(
    private val collector: PostmasterDataCollector,
    private val autoPauseService: ReputationAutoPauseService,
    private val taskExecutionService: TaskExecutionService
) {
    @Scheduled(cron = "\${talent-introduction.postmaster.cron}")
    fun runDaily() {
        taskExecutionService.runAndRecord("POSTMASTER_REPUTATION", "SCHEDULED", "postmaster-daily") {
            collector.collect()
            autoPauseService.checkAndAct()
        }
    }
}
