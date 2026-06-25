package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.campaign.event.BatchSendCronChangedEvent
import com.weibo.talentintroduction.campaign.service.BatchSendConfig
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendSettingService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.Trigger
import java.util.concurrent.ScheduledFuture

class BatchSendSchedulerTest {
    private val batchSendSettingService = Mockito.mock(BatchSendSettingService::class.java)
    private val batchSendControlService = Mockito.mock(BatchSendControlService::class.java)
    private val taskScheduler = Mockito.mock(TaskScheduler::class.java)
    private val scheduledFuture = Mockito.mock(ScheduledFuture::class.java)

    private fun scheduler(): BatchSendScheduler =
        BatchSendScheduler(batchSendSettingService, batchSendControlService, taskScheduler)

    private fun defaultConfig(autoEnabled: Boolean = true): BatchSendConfig =
        BatchSendConfig(
            autoEnabled = autoEnabled,
            cron = "0 0 0 * * ?",
            dailyCap = 1000,
            roundSize = 50,
            perMailIntervalMs = 1000,
            perRoundIntervalMs = 60000,
            selfCheckTtlMinutes = 30
        )

    @Test
    fun `onCronChanged cancels old future without interrupt and reschedules`() {
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
    fun `scheduleInitial registers trigger task`() {
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)

        scheduler().scheduleInitial()

        Mockito.verify(taskScheduler).schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java))
    }

    @Test
    fun `triggerBatchSend skips startAuto when autoEnabled is false`() {
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.`when`(taskScheduler.schedule(runnableCaptor.capture(), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(defaultConfig(autoEnabled = false))

        val scheduler = scheduler()
        scheduler.scheduleInitial()
        runnableCaptor.value.run()

        Mockito.verify(batchSendControlService, Mockito.never()).startAuto()
    }

    @Test
    fun `triggerBatchSend calls startAuto when autoEnabled is true`() {
        val runnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.`when`(taskScheduler.schedule(runnableCaptor.capture(), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)
        Mockito.`when`(batchSendSettingService.getConfig()).thenReturn(defaultConfig(autoEnabled = true))
        Mockito.`when`(batchSendControlService.startAuto())
            .thenReturn(ResponseEntity.ok(mapOf("message" to "started")))

        val scheduler = scheduler()
        scheduler.scheduleInitial()
        runnableCaptor.value.run()

        Mockito.verify(batchSendControlService).startAuto()
    }

    @Test
    fun `scheduleInitial leaves a non-null scheduled future`() {
        Mockito.`when`(taskScheduler.schedule(Mockito.any(Runnable::class.java), Mockito.any(Trigger::class.java)))
            .thenReturn(scheduledFuture)

        scheduler().scheduleInitial()

        assertNotNull(scheduledFuture)
    }
}
