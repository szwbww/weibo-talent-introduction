package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import com.weibo.talentintroduction.campaign.service.BatchSendType
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
 * the trigger body so the schedule itself stays registered.
 *
 * Cron changes or autoEnabled toggling on any type trigger an immediate full reschedule via
 * [BatchSendCronChangedEvent], cancelling pending fires computed from superseded config (I-1).
 *
 * I-8: INTRODUCTION and MATERIAL_REMINDER have independent ScheduledFutures managed via
 * [scheduledFutures]. INTRODUCTION is always scheduled; MATERIAL_REMINDER is only scheduled
 * when its autoEnabled=true (cancelled when disabled). Both dispatch to the same
 * [BatchSendControlService.startAuto] which enforces shared execution mutex via
 * [TaskProgressStore.tryStartWithToken] — any running type returns 409 for the other (I-8).
 */
@Component
class BatchSendScheduler(
    private val batchSendSettingService: BatchSendSettingService,
    private val batchSendControlService: BatchSendControlService,
    @Qualifier("batchSendTaskScheduler") private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(BatchSendScheduler::class.java)

    private val scheduledFutures = mutableMapOf<BatchSendType, ScheduledFuture<*>>()

    @PostConstruct
    fun scheduleInitial() {
        rescheduleAll()
    }

    @EventListener
    fun onCronChanged(event: BatchSendCronChangedEvent) {
        log.info("Batch send cron/enabled changed (oldCron={}, newCron={}), rescheduling all types", event.oldCron, event.newCron)
        rescheduleAll()
    }

    private fun rescheduleAll() {
        synchronized(this) {
            rescheduleOne(BatchSendType.INTRODUCTION)
            rescheduleOne(BatchSendType.MATERIAL_REMINDER)
        }
    }

    private fun rescheduleOne(sendType: BatchSendType) {
        scheduledFutures.remove(sendType)?.cancel(false)
        try {
            val config = if (sendType == BatchSendType.INTRODUCTION) {
                batchSendSettingService.getConfig()
            } else {
                batchSendSettingService.getConfig(sendType)
            }
            if (sendType == BatchSendType.MATERIAL_REMINDER && !config.autoEnabled) {
                log.debug("MATERIAL_REMINDER auto send disabled (autoEnabled=false), not scheduling")
                return
            }
            scheduledFutures[sendType] = taskScheduler.schedule(
                Runnable { triggerBatchSend(sendType) },
                DynamicCronTrigger(batchSendSettingService, sendType)
            )
            log.debug("Scheduled {} batch send trigger, cron={}", sendType, config.cron)
        } catch (e: Exception) {
            log.warn("Failed to reschedule {} batch send: {}", sendType, e.message)
        }
    }

    private fun triggerBatchSend(sendType: BatchSendType) {
        val config = try {
            if (sendType == BatchSendType.INTRODUCTION) batchSendSettingService.getConfig()
            else batchSendSettingService.getConfig(sendType)
        } catch (e: Exception) {
            log.warn("Failed to read batch send config for {}, skipping trigger: {}", sendType, e.message)
            return
        }
        if (!config.autoEnabled) {
            log.debug("{} auto batch send disabled (autoEnabled=false), skipping trigger", sendType)
            return
        }
        log.info("Scheduled batch send trigger firing: sendType={}, cron={}", sendType, config.cron)
        val response = if (sendType == BatchSendType.INTRODUCTION) {
            batchSendControlService.startAuto()
        } else {
            batchSendControlService.startAuto(sendType)
        }
        if (response.statusCode.is2xxSuccessful) {
            log.info("{} auto batch send started: {}", sendType, response.body?.get("message"))
        } else {
            log.warn("{} auto batch send start rejected: status={}, message={}",
                sendType, response.statusCode, response.body?.get("message"))
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
 * Trigger that reads the cron expression for [sendType] from [BatchSendSettingService] on each
 * [nextExecution] call. Falls back to the service default if cron is invalid.
 * Returns null only if cron parsing fails, which pauses scheduling until the config is fixed.
 */
private class DynamicCronTrigger(
    private val configService: BatchSendSettingService,
    private val sendType: BatchSendType
) : Trigger {
    override fun nextExecutionTime(context: TriggerContext): Date? {
        val cron = try {
            configService.getConfig(sendType).cron
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
