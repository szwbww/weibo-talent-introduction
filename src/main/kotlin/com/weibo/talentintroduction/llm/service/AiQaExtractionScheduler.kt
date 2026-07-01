package com.weibo.talentintroduction.llm.service

import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "talent-introduction.scheduling", name = ["enabled"], havingValue = "true")
class AiQaExtractionScheduler(
    private val properties: MailSchedulingProperties,
    private val aiQaExtractionService: AiQaExtractionService,
    private val taskExecutionService: TaskExecutionService
) {
    @Scheduled(cron = "\${talent-introduction.scheduling.ai-qa-extraction-cron:-}")
    fun scheduleExtraction() {
        val request = mapOf("maxContacts" to properties.aiQaExtractionMaxContacts)
        taskExecutionService.runAndRecord("AI_QA_EXTRACTION", "SCHEDULED", request) {
            aiQaExtractionService.extractBatch(properties.aiQaExtractionMaxContacts)
        }
    }
}
