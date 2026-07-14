package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.domain.BatchSendTaskConfig
import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import java.util.concurrent.ScheduledFuture

class BatchSendSchedulerTest {
    private val batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val batchSendControlService = Mockito.mock(BatchSendControlService::class.java)
    private val taskScheduler = Mockito.mock(TaskScheduler::class.java)
    private val scheduledFuture = Mockito.mock(ScheduledFuture::class.java)

    private fun scheduler(): BatchSendScheduler =
        BatchSendScheduler(batchSendTaskConfigRepository, batchSendControlService, taskScheduler)

    private fun enabledConfig(id: Long = 1L, cron: String = "0 0 0 * * ?"): BatchSendTaskConfig =
        BatchSendTaskConfig(
            id = id,
            configName = "test-$id",
            mailType = "INTRODUCTION",
            autoEnabled = true,
            cron = cron,
            dailyCap = 1000,
            roundSize = 50,
            perMailIntervalMs = 1000,
            perRoundIntervalMs = 60000,
            selfCheckTtlMinutes = 30
        )

    @Test
    fun `onCronChanged cancels old future without interrupt and reschedules`() {
        Mockito.`when`(batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig()))
            .thenReturn(listOf(enabledConfig(cron = "0 0 8 * * ?")))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)

        val scheduler = scheduler()
        scheduler.scheduleInitial()

        scheduler.onCronChanged(BatchSendCronChangedEvent("0 0 0 * * ?", "0 0 8 * * ?"))

        Mockito.verify(scheduledFuture).cancel(false)
        Mockito.verify(taskScheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `scheduleInitial registers trigger task per enabled config`() {
        Mockito.`when`(batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig(1L), enabledConfig(2L, "0 0 8 * * ?")))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)

        scheduler().scheduleInitial()

        Mockito.verify(taskScheduler, Mockito.times(2))
            .schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `triggerBatchSend skips startScheduled when config disabled`() {
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.`when`(taskScheduler.schedule(runnableCaptor.capture(), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)
        Mockito.`when`(batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig()))
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(enabledConfig().copy(autoEnabled = false))

        val scheduler = scheduler()
        scheduler.scheduleInitial()
        runnableCaptor.value.run()

        Mockito.verify(batchSendControlService, Mockito.never()).startScheduled(Mockito.anyLong())
    }

    @Test
    fun `triggerBatchSend calls startScheduled when config enabled`() {
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.`when`(taskScheduler.schedule(runnableCaptor.capture(), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)
        Mockito.`when`(batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig()))
        Mockito.`when`(batchSendTaskConfigRepository.findByIdAndDeletedAtIsNull(1L))
            .thenReturn(enabledConfig())
        Mockito.`when`(batchSendControlService.startScheduled(1L))
            .thenReturn(ResponseEntity.ok(mapOf("message" to "started")))

        val scheduler = scheduler()
        scheduler.scheduleInitial()
        runnableCaptor.value.run()

        Mockito.verify(batchSendControlService).startScheduled(1L)
    }

    @Test
    fun `scheduleInitial leaves a non-null scheduled future`() {
        Mockito.`when`(batchSendTaskConfigRepository.findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc())
            .thenReturn(listOf(enabledConfig()))
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)

        scheduler().scheduleInitial()

        assertNotNull(scheduledFuture)
    }
}
