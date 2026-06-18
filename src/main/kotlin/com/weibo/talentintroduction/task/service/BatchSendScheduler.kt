package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.TriggerContext
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Dynamic-cron scheduler for the batch send flow (I-2: AUTO mode, triggerType=SCHEDULED).
 *
 * Unlike [MailAutomationScheduler] (which is gated by `talent-introduction.scheduling.enabled`
 * and uses fixed `@Scheduled` cron from application.yml), this bean is always present and reads
 * its cron from the DB-backed [BatchSendSettingService] on every trigger evaluation, so operators
 * can change the schedule at runtime without a restart. The `autoEnabled` flag is checked inside
 * the trigger body (not via a conditional bean) so the schedule itself stays registered.
 *
 * Mutual exclusion (I-1) is delegated to [BatchSendControlService.startAuto] which uses
 * [TaskProgressStore.tryStartWithToken] + the single-thread manualOutreachExecutor.
 */
@Component
@Configuration
class BatchSendScheduler(
    private val batchSendSettingService: BatchSendSettingService,
    private val batchSendControlService: BatchSendControlService
) : SchedulingConfigurer {

    private val log = LoggerFactory.getLogger(BatchSendScheduler::class.java)

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        // AddTriggerTask evaluates the trigger after each execution to schedule the next one.
        taskRegistrar.addTriggerTask(
            Runnable { triggerBatchSend() },
            DynamicCronTrigger(batchSendSettingService)
        )
    }

    private fun triggerBatchSend() {
        val config = try {
            batchSendSettingService.getConfig()
        } catch (e: Exception) {
            log.warn("Failed to read batch send config, skipping trigger: {}", e.message)
            return
        }
        if (!config.autoEnabled) {
            log.debug("Auto batch send disabled (autoEnabled=false), skipping trigger")
            return
        }
        log.info("Scheduled batch send trigger firing; cron={}", config.cron)
        val response = batchSendControlService.startAuto()
        if (response.statusCode.is2xxSuccessful) {
            log.info("Auto batch send started: {}", response.body?.get("message"))
        } else {
            log.warn("Auto batch send start rejected: status={}, message={}",
                response.statusCode, response.body?.get("message"))
        }
    }
}

/**
 * Trigger that reads the cron expression from [BatchSendSettingService] on each
 * [nextExecution] call. If the cron is invalid, [BatchSendSettingService.getConfig] already
 * falls back to the default ("0 0 0 * * ?"), so this trigger always returns a valid next time.
 * Returns null only if cron parsing fails, which pauses scheduling until the config is fixed.
 */
private class DynamicCronTrigger(private val configService: BatchSendSettingService) : Trigger {
    override fun nextExecutionTime(context: TriggerContext): Date? {
        val cron = try {
            configService.getConfig().cron
        } catch (e: Exception) {
            return null
        }
        return try {
            CronTrigger(cron).nextExecutionTime(context)
        } catch (e: Exception) {
            null
        }
    }
}
