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
import java.time.LocalDateTime

class MailAutomationControllerTest {
    private val initialOutreachService = Mockito.mock(InitialOutreachService::class.java)
    private val autoMailReplyService = Mockito.mock(AutoMailReplyService::class.java)
    private val batchAutoMailReplyService = Mockito.mock(BatchAutoMailReplyService::class.java)
    @Suppress("UNCHECKED_CAST")
    private val mailQueuePublisherProvider = Mockito.mock(ObjectProvider::class.java) as ObjectProvider<MailQueuePublisher>
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private var capturedTriggerType: String? = null
    private var capturedRequest: Any? = null

    private val controller = MailAutomationController(
        initialOutreachService,
        autoMailReplyService,
        batchAutoMailReplyService,
        mailQueuePublisherProvider,
        taskExecutionService
    )

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    @BeforeEach
    fun setUp() {
        capturedTriggerType = null
        capturedRequest = null

        Mockito.`when`(taskExecutionService.runAndRecord(
            anyValue(""), anyValue(""), anyValue(Any()), anyValue { null }
        )).thenAnswer { invocation ->
            capturedTriggerType = invocation.getArgument(1)
            capturedRequest = invocation.getArgument(2)
            @Suppress("UNCHECKED_CAST")
            val block = invocation.getArgument<() -> Any?>(3)
            try { block() } catch (_: Exception) {}
            TaskExecution(
                id = 1L,
                taskType = invocation.getArgument(0),
                triggerType = invocation.getArgument(1),
                status = "SUCCESS",
                requestPayload = objectMapper.writeValueAsString(invocation.getArgument(2)),
                resultSummary = null,
                startedAt = LocalDateTime.now(),
                finishedAt = LocalDateTime.now()
            )
        }
    }

    // 1. contactIds = null -> MANUAL_ALL
    @Test
    fun `null contactIds triggers MANUAL_ALL`() {
        controller.checkReplies(CheckRepliesRequest(contactIds = null))

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(20)
    }

    // 2. contactIds = [] -> MANUAL_ALL
    @Test
    fun `empty contactIds triggers MANUAL_ALL`() {
        controller.checkReplies(CheckRepliesRequest(contactIds = emptyList()))

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(20)
    }

    // 3. contactIds = [1,2] -> MANUAL_SELECTIVE
    @Test
    fun `specific contactIds triggers MANUAL_SELECTIVE`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0)
        )).thenReturn(emptyBatchResult())

        controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 2L)))

        assertEquals("MANUAL_SELECTIVE", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(listOf(1L, 2L), 20)
    }

    // 4. Duplicate id normalization
    @Test
    fun `duplicate contactIds are deduplicated`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0)
        )).thenReturn(emptyBatchResult())

        controller.checkReplies(CheckRepliesRequest(contactIds = listOf(1L, 1L, 2L)))

        assertEquals("MANUAL_SELECTIVE", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(listOf(1L, 2L), 20)
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
        controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 1))

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(1)
    }

    @Test
    fun `maxMessagesPerAccount 100 accepted`() {
        controller.checkReplies(CheckRepliesRequest(maxMessagesPerAccount = 100))

        assertEquals("MANUAL_ALL", capturedTriggerType)
        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(100)
    }

    // 9. Full-mode calls receiveAndAutoReplyAll
    @Test
    fun `full mode calls receiveAndAutoReplyAll not selective`() {
        controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = 15))

        verify(batchAutoMailReplyService).receiveAndAutoReplyAll(15)
        verify(batchAutoMailReplyService, never()).receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0)
        )
    }

    // 10. Selective calls receiveAndAutoReplyForContacts
    @Test
    fun `selective mode calls receiveAndAutoReplyForContacts not all`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0)
        )).thenReturn(emptyBatchResult())

        controller.checkReplies(CheckRepliesRequest(contactIds = listOf(5L), maxMessagesPerAccount = 10))

        verify(batchAutoMailReplyService).receiveAndAutoReplyForContacts(listOf(5L), 10)
        verify(batchAutoMailReplyService, never()).receiveAndAutoReplyAll(anyValue(0))
    }

    // 11. Task requestPayload uses normalized parameters
    @Test
    fun `task records normalized request with deduplicated ids and default maxMessages`() {
        Mockito.`when`(batchAutoMailReplyService.receiveAndAutoReplyForContacts(
            anyValue(emptyList()), anyValue(0)
        )).thenReturn(emptyBatchResult())

        val result = controller.checkReplies(
            CheckRepliesRequest(contactIds = listOf(1L, 1L, 2L), maxMessagesPerAccount = null)
        )

        val normalized = capturedRequest as CheckRepliesRequest
        assertEquals(listOf(1L, 2L), normalized.contactIds)
        assertEquals(20, normalized.maxMessagesPerAccount)
    }

    @Test
    fun `null maxMessagesPerAccount defaults to 20 in normalized request`() {
        controller.checkReplies(CheckRepliesRequest(contactIds = null, maxMessagesPerAccount = null))

        val normalized = capturedRequest as CheckRepliesRequest
        assertNull(normalized.contactIds)
        assertEquals(20, normalized.maxMessagesPerAccount)
    }

    private fun emptyBatchResult() = BatchAutoMailReplyResult(
        accountCount = 0,
        fetched = 0,
        recorded = 0,
        replied = 0,
        manualReview = 0,
        accounts = emptyList()
    )
}
