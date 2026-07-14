package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendStatusView
import com.weibo.talentintroduction.campaign.service.InitialOutreachService
import com.weibo.talentintroduction.campaign.service.AccountStatRow
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
import org.springframework.http.ResponseEntity
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
    private val batchSendControlService = Mockito.mock(BatchSendControlService::class.java)
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
        manualOutreachExecutor = manualOutreachExecutor,
        batchSendControlService = batchSendControlService
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
            anyValue(""), anyValue(""), anyValue(Any()), anyValue { }, Mockito.isNull(), anyValue { }
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
    fun `startManualOutreach delegates to control service and returns 202 when accepted`() {
        Mockito.`when`(batchSendControlService.startManual()).thenReturn(
            ResponseEntity.accepted().body(mapOf("message" to "已启动"))
        )

        val response = controller.startManualOutreach()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        assertEquals("已启动", response.body?.get("message"))
        verify(batchSendControlService).startManual()
    }

    @Test
    fun `startManualOutreach returns 409 when control service rejects`() {
        Mockito.`when`(batchSendControlService.startManual()).thenReturn(
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to "流程当前状态为 RUNNING，无法开始（需 IDLE）"))
        )

        val response = controller.startManualOutreach()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("IDLE"))
        verify(batchSendControlService).startManual()
    }

    // ──── Phase 03: batch send control endpoints ────

    @Test
    fun `pauseBatchSend delegates to control service pause with OPERATOR reason`() {
        Mockito.`when`(batchSendControlService.pause("OPERATOR")).thenReturn(
            ResponseEntity.ok(mapOf("message" to "已暂停: OPERATOR"))
        )

        val response = controller.pauseBatchSend()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("已暂停: OPERATOR", response.body?.get("message"))
        verify(batchSendControlService).pause("OPERATOR")
    }

    @Test
    fun `pauseBatchSend returns 409 when control service rejects pause`() {
        Mockito.`when`(batchSendControlService.pause("OPERATOR")).thenReturn(
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to "流程当前状态为 IDLE，无法暂停（需 RUNNING）"))
        )

        val response = controller.pauseBatchSend()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        verify(batchSendControlService).pause("OPERATOR")
    }

    @Test
    fun `runManualOnce delegates to control service and returns 202 when allowed`() {
        Mockito.`when`(batchSendControlService.runManualOnce()).thenReturn(
            ResponseEntity.accepted().body(mapOf("message" to "已启动"))
        )

        val response = controller.runManualOnce()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify(batchSendControlService).runManualOnce()
    }

    @Test
    fun `runManualOnce returns 409 when control service rejects running state`() {
        Mockito.`when`(batchSendControlService.runManualOnce()).thenReturn(
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to "流程当前状态为 RUNNING，手动执行仅在 IDLE 或 PAUSED 时可用"))
        )

        val response = controller.runManualOnce()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body?.get("message")!!.contains("PAUSED"))
        verify(batchSendControlService).runManualOnce()
    }

    @Test
    fun `startAutoBatchSend delegates to control service startAuto`() {
        Mockito.`when`(batchSendControlService.startAuto()).thenReturn(
            ResponseEntity.accepted().body(mapOf("message" to "已启动"))
        )

        val response = controller.startAutoBatchSend()

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify(batchSendControlService).startAuto()
    }

    @Test
    fun `startAutoBatchSend returns 409 when autoEnabled is false`() {
        Mockito.`when`(batchSendControlService.startAuto()).thenReturn(
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to "自动定时发送未启用"))
        )

        val response = controller.startAutoBatchSend()

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        verify(batchSendControlService).startAuto()
    }

    @Test
    fun `resumeBatchSendSchedule delegates to control service without immediate execution`() {
        Mockito.`when`(batchSendControlService.resumeSchedule()).thenReturn(
            ResponseEntity.ok(mapOf("message" to "已恢复定时发送"))
        )

        val response = controller.resumeBatchSendSchedule()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("已恢复定时发送", response.body?.get("message"))
        verify(batchSendControlService).resumeSchedule()
    }

    @Test
    fun `pauseBatchSendSchedule delegates to control service without cancelling execution`() {
        Mockito.`when`(batchSendControlService.pauseSchedule()).thenReturn(
            ResponseEntity.ok(mapOf("message" to "已暂停定时发送"))
        )

        val response = controller.pauseBatchSendSchedule()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("已暂停定时发送", response.body?.get("message"))
        verify(batchSendControlService).pauseSchedule()
    }

    @Test
    fun `getBatchSendStatus returns status view from control service`() {
        val statusView = BatchSendStatusView(
            status = "PAUSED",
            mode = "AUTO",
            autoEnabled = false,
            pauseReason = "NO_AVAILABLE_ACCOUNT",
            roundNumber = 3,
            dailyCap = 1000,
            dailySentTotal = 42,
            sentTotal = 42,
            failedTotal = 2,
            accounts = listOf(
                AccountStatRow("chen", todaySent = 42, dailyLimit = 100, success = 40, failed = 2, paused = false, pauseReason = null)
            ),
            executionId = 99L,
            message = "流程已暂停: NO_AVAILABLE_ACCOUNT"
        )
        Mockito.`when`(batchSendControlService.getStatus()).thenReturn(statusView)

        val result = controller.getBatchSendStatus()

        assertEquals("PAUSED", result.status)
        assertEquals("AUTO", result.mode)
        assertEquals("NO_AVAILABLE_ACCOUNT", result.pauseReason)
        assertEquals(3, result.roundNumber)
        assertEquals(1000, result.dailyCap)
        assertEquals(42, result.dailySentTotal)
        assertEquals(1, result.accounts.size)
        assertEquals("chen", result.accounts[0].accountCode)
        assertEquals(40, result.accounts[0].success)
        verify(batchSendControlService).getStatus()
    }

    @Test
    fun `getBatchSendStatus returns IDLE status when flow not started`() {
        val statusView = BatchSendStatusView(
            status = "IDLE", mode = "NONE", autoEnabled = false, pauseReason = "",
            roundNumber = 0, dailyCap = 0, dailySentTotal = 0,
            sentTotal = 0, failedTotal = 0,
            accounts = emptyList(), executionId = null, message = null
        )
        Mockito.`when`(batchSendControlService.getStatus()).thenReturn(statusView)

        val result = controller.getBatchSendStatus()

        assertEquals("IDLE", result.status)
        assertNull(result.executionId)
        assertTrue(result.accounts.isEmpty())
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
