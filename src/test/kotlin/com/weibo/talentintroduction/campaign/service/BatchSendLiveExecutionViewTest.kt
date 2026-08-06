package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.repository.BatchSendTaskConfigRepository
import com.weibo.talentintroduction.mail.service.MailSenderAccountService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.util.concurrent.Executor

/**
 * I-1/I-2/I-5: live view presence is decided by the in-memory single slot
 * (getCurrentExecutionId), never by restoreFromLog; cancellation is
 * executionId-scoped; percentage comes from TaskProgress without
 * recomputation.
 */
class BatchSendLiveExecutionViewTest {

    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
    private val batchSendSettingService = Mockito.mock(BatchSendSettingService::class.java)
    private val batchSendTaskConfigRepository = Mockito.mock(BatchSendTaskConfigRepository::class.java)
    private val mailSenderAccountService = Mockito.mock(MailSenderAccountService::class.java)
    private val mailComposeTemplateService = Mockito.mock(com.weibo.talentintroduction.template.service.MailComposeTemplateService::class.java)
    private val objectMapper = ObjectMapper()
    private val manualOutreachExecutor = Mockito.mock(Executor::class.java)

    private val service = BatchSendControlService(
        progressStore = progressStore,
        taskExecutionService = taskExecutionService,
        manualInitialOutreachService = manualInitialOutreachService,
        batchSendSettingService = batchSendSettingService,
        batchSendTaskConfigRepository = batchSendTaskConfigRepository,
        mailSenderAccountService = mailSenderAccountService,
        mailComposeTemplateService = mailComposeTemplateService,
        objectMapper = objectMapper,
        manualOutreachExecutor = manualOutreachExecutor
    )

    private fun progress(
        status: String = "RUNNING",
        processedCount: Long = 0,
        totalCount: Long = 0,
        message: String? = null,
        details: Map<String, Any>? = null,
        executionId: Long? = 101L
    ) = TaskProgress(
        taskType = BatchSendControlService.TASK_TYPE,
        status = status,
        batchNumber = 1,
        processedCount = processedCount,
        totalCount = totalCount,
        message = message,
        details = details,
        executionId = executionId
    )

    @Test
    fun `live is null when slot holds a different execution`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(999L)
        // get() must not be consulted on the mismatch branch (I-1)
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE))
            .thenThrow(RuntimeException("get must not be called on mismatch"))

        assertNull(service.getLiveExecutionView(101L))
    }

    @Test
    fun `live is null when slot is empty and does not restore from log`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(null)

        assertNull(service.getLiveExecutionView(101L))
        Mockito.verify(progressStore, Mockito.never()).get(Mockito.anyString())
    }

    @Test
    fun `live is populated when slot is bound to the queried execution`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
            progress(
                status = "RUNNING",
                processedCount = 7,
                totalCount = 120,
                message = "正在发送：a@b.edu",
                details = mapOf("roundNumber" to 2, "accounts" to emptyList<Any>())
            )
        )

        val live = service.getLiveExecutionView(101L)

        assertNotNull(live)
        assertEquals("RUNNING", live!!.status)
        assertEquals("正在发送：a@b.edu", live.message)
        assertEquals(2, live.roundNumber)
        assertEquals(7, live.processedCount)
        assertEquals(120, live.totalCount)
        assertEquals(5, live.percentage)
        assertTrue(live.cancellable)
    }

    @Test
    fun `percentage is 0 when totalCount is 0`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
            progress(processedCount = 7, totalCount = 0)
        )

        val live = service.getLiveExecutionView(101L)

        assertEquals(0, live!!.percentage)
        assertEquals(0, live.totalCount)
    }

    @Test
    fun `CANCELLING status is not cancellable`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
            progress(status = "CANCELLING")
        )

        val live = service.getLiveExecutionView(101L)

        assertEquals("CANCELLING", live!!.status)
        assertFalse(live.cancellable)
    }

    @Test
    fun `accounts reuse extractAccountStats parsing`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.get(BatchSendControlService.TASK_TYPE)).thenReturn(
            progress(
                details = mapOf(
                    "roundNumber" to 1,
                    "accounts" to listOf(
                        mapOf(
                            "accountCode" to "a@weibo.com",
                            "todaySent" to 5,
                            "dailyLimit" to 50,
                            "success" to 3,
                            "failed" to 0,
                            "paused" to false
                        ),
                        mapOf(
                            "accountCode" to "b@weibo.com",
                            "todaySent" to 2,
                            "dailyLimit" to 50,
                            "success" to 1,
                            "failed" to 2,
                            "paused" to false
                        )
                    )
                )
            )
        )

        val live = service.getLiveExecutionView(101L)

        assertEquals(2, live!!.accounts.size)
        assertEquals("a@weibo.com", live.accounts[0].accountCode)
        assertEquals(3, live.accounts[0].success)
        assertEquals(0, live.accounts[0].failed)
        assertEquals("b@weibo.com", live.accounts[1].accountCode)
        assertEquals(2, live.accounts[1].failed)
    }

    @Test
    fun `cancel returns 409 and skips requestCancel on executionId mismatch`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)

        val response = service.cancelExecution(102L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("该执行已结束或不是当前正在运行的执行", response.body!!["message"])
        Mockito.verify(progressStore, Mockito.never()).requestCancel(Mockito.anyString())
    }

    @Test
    fun `cancel returns 409 when requestCancel is rejected`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(false)

        val response = service.cancelExecution(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("取消请求已在处理中", response.body!!["message"])
    }

    @Test
    fun `cancel returns 200 when requestCancel is accepted`() {
        Mockito.`when`(progressStore.getCurrentExecutionId(BatchSendControlService.TASK_TYPE)).thenReturn(101L)
        Mockito.`when`(progressStore.requestCancel(BatchSendControlService.TASK_TYPE)).thenReturn(true)

        val response = service.cancelExecution(101L)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("已发送取消请求，将在当前批次结束后停止", response.body!!["message"])
    }
}
