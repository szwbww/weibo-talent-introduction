package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import org.springframework.scheduling.TriggerContext
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.ScheduledFuture
import javax.annotation.PostConstruct

/**
 * Dynamic-cron scheduler for the batch send flow (I-2: AUTO mode, triggerType=SCHEDULED).
 *
 * Unlike [MailAutomationScheduler] (which is gated by `talent-introduction.scheduling.enabled`
 * and uses fixed `@Scheduled` cron from application.yml), this bean is always present and reads
 * its cron from the DB-backed [BatchSendSettingService] on every trigger evaluation, so operators
 * can change the schedule at runtime without a restart. The `autoEnabled` flag is checked inside
 * the trigger body (not via a conditional bean) so the schedule itself stays registered.
 *
 * Cron changes trigger an immediate reschedule via [BatchSendCronChangedEvent], cancelling any
 * pending fire computed from a superseded cron (I-1) without interrupting an in-flight send (I-2).
 *
 * Mutual exclusion (I-1) is delegated to [BatchSendControlService.startAuto] which uses
 * [TaskProgressStore.tryStartWithToken] + the single-thread manualOutreachExecutor.
 */
@Component
class BatchSendScheduler(
    private val batchSendSettingService: BatchSendSettingService,
    private val batchSendControlService: BatchSendControlService,
    @Qualifier("batchSendTaskScheduler") private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(BatchSendScheduler::class.java)

    @Volatile
    private var scheduledFuture: ScheduledFuture<*>? = null

    @PostConstruct
    fun scheduleInitial() {
        reschedule()
    }

    @EventListener
    fun onCronChanged(event: BatchSendCronChangedEvent) {
        log.info("Batch send cron changed from {} to {}, rescheduling", event.oldCron, event.newCron)
        reschedule()
    }

    private fun reschedule() {
        synchronized(this) {
            scheduledFuture?.cancel(false)
            scheduledFuture = taskScheduler.schedule(
                Runnable { triggerBatchSend() },
                DynamicCronTrigger(batchSendSettingService)
            )
        }
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

@Configuration
class BatchSendSchedulerConfiguration {
    @Bean("batchSendTaskScheduler")
    fun batchSendTaskScheduler(): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("batch-send-")
            initialize()
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
