package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component

@Component
@Configuration
class DailyCountResetScheduler(
    private val mailSenderAccountService: MailSenderAccountService,
    private val taskExecutionService: TaskExecutionService
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(DailyCountResetScheduler::class.java)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.addCronTask(
            Runnable { runScheduledReset() },
            "0 0 0 * * ?"
        )
    }

    fun runScheduledReset() {
        try {
            taskExecutionService.runAndRecord("DAILY_COUNT_RESET", "SCHEDULED", "daily-count-reset") {
                val result = mailSenderAccountService.resetDailyCounts()
                log.info(
                    "Daily count reset complete: {} counts reset, {} pauses resumed",
                    result.countReset,
                    result.pauseResumed
                )
            }
        } catch (e: Exception) {
            log.error("Daily count reset failed", e)
        }
    }
}
