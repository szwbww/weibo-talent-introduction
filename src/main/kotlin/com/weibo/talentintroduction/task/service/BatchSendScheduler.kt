package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
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
 * Per-config dynamic cron scheduler (I-2). Each enabled [batch_send_task_config] row gets its own
 * [ScheduledFuture] keyed by config id. Reload diffs cancel removed/changed futures with
 * [ScheduledFuture.cancel](false) without interrupting an in-flight send.
 */
@Component
class BatchSendScheduler(
    private val batchSendTaskConfigRepository: BatchSendTaskConfigRepository,
    private val batchSendControlService: BatchSendControlService,
    @Qualifier("batchSendTaskScheduler") private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(BatchSendScheduler::class.java)

    private val scheduledFutures = mutableMapOf<Long, ScheduledFuture<*>>()
    private val scheduledCrons = mutableMapOf<Long, String>()

    @PostConstruct
    fun scheduleInitial() {
        rescheduleAll()
    }

    @EventListener
    fun onCronChanged(event: BatchSendCronChangedEvent) {
        log.info("Batch send config changed (oldCron={}, newCron={}), rescheduling all configs", event.oldCron, event.newCron)
        rescheduleAll()
    }

    private fun rescheduleAll() {
        synchronized(this) {
            val enabled = batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc()
            val enabledIds = enabled.mapNotNull { it.id }.toSet()

            val toRemove = scheduledFutures.keys.filter { it !in enabledIds }
            for (configId in toRemove) {
                scheduledFutures.remove(configId)?.cancel(false)
                scheduledCrons.remove(configId)
                log.debug("Cancelled batch send schedule for removed/disabled configId={}", configId)
            }

            for (config in enabled) {
                val configId = config.id ?: continue
                val cron = config.cron
                if (scheduledFutures.containsKey(configId) && scheduledCrons[configId] == cron) {
                    continue
                }
                scheduledFutures.remove(configId)?.cancel(false)
                try {
                    scheduledFutures[configId] = taskScheduler.schedule(
                        Runnable { triggerBatchSend(configId) },
                        ConfigCronTrigger(configId, cron)
                    )
                    scheduledCrons[configId] = cron
                    log.debug("Scheduled batch send for configId={}, cron={}", configId, cron)
                } catch (e: Exception) {
                    log.warn("Failed to reschedule batch send for configId={}: {}", configId, e.message)
                }
            }
        }
    }

    private fun triggerBatchSend(configId: Long) {
        val config = batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(configId)
        if (config == null || !config.autoEnabled) {
            log.debug("Config {} deleted or disabled, skipping scheduled trigger", configId)
            return
        }
        log.info("Scheduled batch send trigger firing: configId={}, cron={}", configId, config.cron)
        val response = batchSendControlService.startScheduled(configId)
        if (response.statusCode.is2xxSuccessful) {
            log.info("Auto batch send started for configId={}: {}", configId, response.body?.get("message"))
        } else {
            log.warn("Auto batch send start rejected for configId={}: status={}, message={}",
                configId, response.statusCode, response.body?.get("message"))
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

private class ConfigCronTrigger(
    private val configId: Long,
    private val cron: String
) : Trigger {
    override fun nextExecutionTime(context: TriggerContext): Date? =
        try {
            CronTrigger(cron).nextExecutionTime(context)
        } catch (_: Exception) {
            null
        }
}
