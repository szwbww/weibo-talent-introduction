package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.mail.queue.MailQueuePublisher
import com.weibo.talentintroduction.mail.service.AutoMailReplyService
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyResult
import com.weibo.talentintroduction.mail.service.BatchAutoMailReplyService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import com.weibo.talentintroduction.task.service.TaskProgress
import java.util.concurrent.Executor

class MailAutomationControllerTest {
    private val initialOutreachService = Mockito.mock(InitialOutreachService::class.java)
    private val autoMailReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val batchAutoMailReplyService = Mockito.mock(BatchAutoMailReplyService::class.java)
    @Suppress("UNCHECKED_CAST")
    private val mailQueuePublisherProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<MailQueuePublisher>
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java)
    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val manualOutreachExecutor = Mockito.mock(Executor::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private var capturedTriggerType: String? = null
    private var capturedRequest: Any? = null
    private var lastRecordedResult: Any? = null

    private val controller = MailAutomationController(
        initialOutreachService = initialOutreachService,
        autoMailReplyService = autoMailReplyService,
        batchAutoMailReplyService = batchAutoMailReplyService,
        mailQueuePublisherProvider = mailQueuePublisherProvider,
        taskExecutionService = taskExecutionService,
        manualInitialOutreachService = manualInitialOutreachService,
        progressStore = progressStore,
        manualOutreachExecutor = manualOutreachExecutor
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun <T> captureValue(captor: org.mockito.ArgumentCaptor<T>, defaultValue: T): T =
        captor.capture() ?: defaultValue

    @BeforeEach
    fun setUp() {
        capturedTriggerType = null
        capturedRequest = null

        Mockito.doAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }.`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))

        Mockito.`when`(taskExecutionService.runAndRecordWithResult<Any>(
            anyValue(""), anyValue(""), anyValue(Any()), anyValue { }, anyValue { }
        )).thenAnswer { invocation ->
            capturedTriggerType = invocation.getArgument(1)
            capturedRequest = invocation.getArgument(2)
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(1L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> Any?>(4)
            val result = try { block() } catch (_: Exception) { null }
            lastRecordedResult = result
            val execution = TaskExecution(
                id = 1L,
                taskType = invocation.getArgument(0),
                triggerType = invocation.getArgument(1),
                status = "SUCCESS",
                requestPayload = objectMapper.writeValueAsString(invocation.getArgument(2)),
                resultSummary = null,
                startedAt = LocalDateTime.now(),
                finishedAt = LocalDateTime.now()
            )
            Pair(execution, result)
        }

        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(emptyBatchResult())

        Mockito.doReturn(Pair(true, 12345L))
            .`when`(progressStore).tryStartWithToken(eqValue("CHECK_REPLIES"), anyValue(TaskProgress("CHECK_REPLIES", "RUNNING", 0, 0, 0)))
    }

    // 1. contactIds = null -> MANUAL_ALL
    @Test
    fun `null contactIds triggers MANUAL_ALL`() {
        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(eqValue(20), anyValue(null), anyValue(null))
    }

    // 2. contactIds = [] -> MANUAL_ALL
    @Test
    fun `empty contactIds triggers MANUAL_ALL`() {
        val response = controller.checkReplies(CheckRepliesRequest(contactIds = emptyList()))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(eqValue(20), anyValue(null), anyValue(null))
    }

    // 3. contactIds = [1,2] -> MANUAL_SELECTIVE
    @Test
    fun `specific contactIds triggers MANUAL_SELECTIVE`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0), anyValue(null), anyValue(null)
        )).thenReturn(emptyBatchResult())

        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 2L)))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_SELECTIVE", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(eqValue(listOf(1L, 2L)), eqValue(20), anyValue(null), anyValue(null))
    }

    // 4. Duplicate id normalization
    @Test
    fun `duplicate contactIds are deduplicated`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0), anyValue(null), anyValue(null)
        )).thenReturn(emptyBatchResult())

        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 1L, 2L)))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_SELECTIVE", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(eqValue(listOf(1L, 2L)), eqValue(20), anyValue(null), anyValue(null))
    }

    // 5. id 0, negative rejected
    @Test
    fun `contactId 0 is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.checkReplies(CheckRepliesRequest(contactIds = listOf(0L, 1L)))
        }
        assertTrue(ex.message!!.contains("positive"))
    }

    @Test
    fun `negative contactId is rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.checkReplies(CheckRepliesRequest(contactIds = listOf(-1L)))
        }
        assertTrue(ex.message!!.contains("positive"))
    }

    // 6. Over 500 ids rejected
    @Test
    fun `more than 500 contactIds rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.checkReplies(CheckRepliesRequest(contactIds = (1L..501L).toList()))
        }
        assertTrue(ex.message!!.contains("500"))
    }

    // 7. maxMessages 0, 101 rejected
    @Test
    fun `maxMessagesPerAccount 0 rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 0))
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    @Test
    fun `maxMessagesPerAccount 101 rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 101))
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    // 8. maxMessages 1, 100 accepted
    @Test
    fun `maxMessagesPerAccount 1 accepted`() {
        val response = controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 1))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(eqValue(1), anyValue(null), anyValue(null))
    }

    @Test
    fun `maxMessagesPerAccount 100 accepted`() {
        val response = controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 100))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(eqValue(100), anyValue(null), anyValue(null))
    }

    // 9. Full-mode calls receiveAndAutoReplyAll
    @Test
    fun `full mode calls receiveAndAutoReplyAll not selective`() {
        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = 15))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(eqValue(15), anyValue(null), anyValue(null))
        verify(batchAutoMailReplyService, never()).receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0), anyValue(null), anyValue(null)
        )
    }

    // 10. Selective calls receiveAndAutoReplyForContacts
    @Test
    fun `selective mode calls receiveAndAutoReplyForContacts not all`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0), anyValue(null), anyValue(null)
        )).thenReturn(emptyBatchResult())

        val response = controller.checkReplies(CheckRepliesRequest(contactIds = listOf(5L), maxMessagesPerAccount = 10))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(eqValue(listOf(5L)), eqValue(10), anyValue(null), anyValue(null))
        verify(batchAutoMailReplyService, never()).receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null))
    }

    // 11. Task requestPayload uses normalized parameters
    @Test
    fun `task records normalized request with deduplicated ids and default maxMessages`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0), anyValue(null), anyValue(null)
        )).thenReturn(emptyBatchResult())

        val response = controller.checkReplies(
            CheckRepliesRequest(contactIds = listOf(1L, 1L, 2L), maxMessagesPerAccount = null)
        )
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        val normalized = capturedRequest as CheckRepliesRequest
        assertEquals(listOf(1L, 2L), normalized.contactIds)
        assertEquals(20, normalized.maxMessagesPerAccount)
    }

    @Test
    fun `null maxMessagesPerAccount defaults to 20 in normalized request`() {
        val response = controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = null))
        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        val normalized = capturedRequest as CheckRepliesRequest
        assertNull(normalized.contactIds)
        assertEquals(20, normalized.maxMessagesPerAccount)
    }

    @Test
    fun `startManualOutreach returns 202 and runs bulk outreach successfully`() {
        Mockito.doReturn(Pair(true, 12345L))
            .`when`(progressStore).tryStartWithToken(eqValue("MANUAL_INITIAL_OUTREACH"), anyValue(TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)))

        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onStarted = invocation.getArgument<(Long) -> Unit>(3)
            onStarted(99L)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> Any?>(4)
            block()
        }.`when`(taskExecutionService).runAndRecordWithResult<Any>(
            eqValue("MANUAL_INITIAL_OUTREACH"), eqValue("MANUAL"), eqValue("manual-outreach"),
            anyValue<(Long) -> Unit> { }, anyValue<() -> Any> { Any() }
        )

        val response = controller.startManualOutreach()
        assertEquals(org.springframework.http.HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("已启动", response.body?.get("message"))

        Mockito.verify(progressStore).bindExecutionId("MANUAL_INITIAL_OUTREACH", 12345L, 99L)
        Mockito.verify(manualInitialOutreachService).runBulkOutreach(99L)
        Mockito.verify(progressStore).clearExecutionContext("MANUAL_INITIAL_OUTREACH", 99L)
    }

    @Test
    fun `startManualOutreach returns 409 when task is already running`() {
        Mockito.doReturn(Pair(false, 0L))
            .`when`(progressStore).tryStartWithToken(eqValue("MANUAL_INITIAL_OUTREACH"), anyValue(TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)))

        val response = controller.startManualOutreach()
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, response.statusCode)
        assertEquals("任务正在执行中", response.body?.get("message"))
    }

    @Test
    fun `startManualOutreach handles RejectedExecutionException, returns 500, updates progress and clears token`() {
        Mockito.doReturn(Pair(true, 12345L))
            .`when`(progressStore).tryStartWithToken(eqValue("MANUAL_INITIAL_OUTREACH"), anyValue(TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)))

        Mockito.doThrow(java.util.concurrent.RejectedExecutionException("Queue full"))
            .`when`(manualOutreachExecutor).execute(Mockito.any(Runnable::class.java))

        val response = controller.startManualOutreach()
        assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("启动失败"))

        Mockito.verify(progressStore).update(eqValue("MANUAL_INITIAL_OUTREACH"), anyValue(TaskProgress("MANUAL_INITIAL_OUTREACH", "RUNNING", 0, 0, 0)), eqValue(12345L))
        Mockito.verify(progressStore).clearExecutionContext("MANUAL_INITIAL_OUTREACH", 12345L)
    }

    private fun emptyBatchResult() = BatchAutoMailReplyResult(
        accountCount = 0,
        fetched = 0,
        recorded = 0,
        replied = 0,
        manualReview = 0,
        accounts = emptyList()
    )

    @Test
    fun `checkReplies records COMPLETED state in progress store`() {
        val result = BatchAutoMailReplyResult(
            accountCount = 2,
            successAccountCount = 2,
            failedAccountCount = 0,
            fetched = 10,
            recorded = 5,
            replied = 2,
            manualReview = 1,
            accounts = emptyList(),
            totalAccountsToPoll = 2,
            accountsPolled = 2,
            taskFinalStatus = "COMPLETED"
        )
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(result)

        controller.checkReplies(CheckRepliesRequest(emptyList(), null))

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(eqValue("CHECK_REPLIES"), captureValue(progressCaptor, TaskProgress("CHECK_REPLIES", "RUNNING", 0, 0, 0)), eqValue(1L))
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("COMPLETED", finalProgress.status)
        assertEquals(2L, finalProgress.processedCount)
        assertEquals(2L, finalProgress.totalCount)
        assertEquals("检查回复完成：共检查 2/2 个邮箱账号，获取 10 封邮件，自动回复 2 封，转人工 1 封", finalProgress.message)
        assertEquals(result, lastRecordedResult)
    }

    @Test
    fun `checkReplies records PARTIAL_SUCCESS state in progress store`() {
        val result = BatchAutoMailReplyResult(
            accountCount = 2,
            successAccountCount = 1,
            failedAccountCount = 1,
            fetched = 10,
            recorded = 5,
            replied = 2,
            manualReview = 1,
            accounts = emptyList(),
            totalAccountsToPoll = 2,
            accountsPolled = 2,
            taskFinalStatus = "PARTIAL_SUCCESS"
        )
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(result)

        controller.checkReplies(CheckRepliesRequest(emptyList(), null))

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(eqValue("CHECK_REPLIES"), captureValue(progressCaptor, TaskProgress("CHECK_REPLIES", "RUNNING", 0, 0, 0)), eqValue(1L))
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("PARTIAL_SUCCESS", finalProgress.status)
        assertEquals(2L, finalProgress.processedCount)
        assertEquals(2L, finalProgress.totalCount)
        assertEquals("检查回复部分成功：共检查 2/2 个邮箱账号，成功 1 个，失败 1 个", finalProgress.message)
        assertEquals(result, lastRecordedResult)
    }

    @Test
    fun `checkReplies records FAILED state in progress store`() {
        val result = BatchAutoMailReplyResult(
            accountCount = 2,
            successAccountCount = 0,
            failedAccountCount = 2,
            fetched = 0,
            recorded = 0,
            replied = 0,
            manualReview = 0,
            accounts = emptyList(),
            totalAccountsToPoll = 2,
            accountsPolled = 2,
            taskFinalStatus = "FAILED"
        )
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(result)

        controller.checkReplies(CheckRepliesRequest(emptyList(), null))

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(eqValue("CHECK_REPLIES"), captureValue(progressCaptor, TaskProgress("CHECK_REPLIES", "RUNNING", 0, 0, 0)), eqValue(1L))
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("FAILED", finalProgress.status)
        assertEquals(2L, finalProgress.processedCount)
        assertEquals(2L, finalProgress.totalCount)
        assertEquals("检查回复失败：共检查 2/2 个邮箱账号均失败，错误信息请查看日志", finalProgress.message)
        assertEquals(result, lastRecordedResult)
    }

    @Test
    fun `checkReplies records CANCELLED state in progress store`() {
        val result = BatchAutoMailReplyResult(
            accountCount = 2,
            successAccountCount = 1,
            failedAccountCount = 0,
            fetched = 5,
            recorded = 2,
            replied = 1,
            manualReview = 0,
            accounts = emptyList(),
            totalAccountsToPoll = 2,
            accountsPolled = 1,
            taskFinalStatus = "CANCELLED"
        )
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyAll(anyValue(0), anyValue(null), anyValue(null)))
            .thenReturn(result)

        controller.checkReplies(CheckRepliesRequest(emptyList(), null))

        val progressCaptor = org.mockito.ArgumentCaptor.forClass(TaskProgress::class.java)
        Mockito.verify(progressStore, Mockito.atLeastOnce()).update(eqValue("CHECK_REPLIES"), captureValue(progressCaptor, TaskProgress("CHECK_REPLIES", "RUNNING", 0, 0, 0)), eqValue(1L))
        val finalProgress = progressCaptor.allValues.last()
        assertEquals("CANCELLED", finalProgress.status)
        assertEquals(1L, finalProgress.processedCount)
        assertEquals(2L, finalProgress.totalCount)
        assertEquals("检查回复已被取消：共检查 1/2 个邮箱账号，获取 5 封邮件，自动回复 1 封，转人工 0 封", finalProgress.message)
        assertEquals(result, lastRecordedResult)
    }
}
