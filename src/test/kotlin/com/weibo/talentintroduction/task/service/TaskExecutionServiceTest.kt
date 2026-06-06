package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class TaskExecutionServiceTest {
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val service = TaskExecutionService(repository, ObjectMapper())

    @Test
    fun `lists executions by task type and status`() {
        Mockito.`when`(
            repository.findAllByTaskTypeAndStatusOrderByStartedAtDesc("AUTO_REPLY_ACCOUNT", "FAILED")
        ).thenReturn(listOf(execution(status = "FAILED")))

        val result = service.listExecutions("AUTO_REPLY_ACCOUNT", "FAILED")

        assertEquals(1, result.size)
        Mockito.verify(repository).findAllByTaskTypeAndStatusOrderByStartedAtDesc(
            "AUTO_REPLY_ACCOUNT",
            "FAILED"
        )
    }

    @Test
    fun `records successful task execution`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            QueueFanOutResultForTest(dispatched = 3)
        }

        assertEquals("SUCCESS", recorded.status)
        assertEquals(3, recorded.successCount)
        assertEquals(recorded.startedAt, recorded.createdAt)
        assertEquals(recorded.finishedAt, recorded.updatedAt)
    }

    @Test
    fun `records failed task execution`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            error("RabbitMQ unavailable")
        }

        assertEquals("FAILED", recorded.status)
        assertEquals(1, recorded.failureCount)
        assertEquals("RabbitMQ unavailable", recorded.errorMessage)
        assertEquals(recorded.startedAt, recorded.createdAt)
        assertEquals(recorded.finishedAt, recorded.updatedAt)
    }

    @Test
    fun `records partial success from TaskExecutionSummaryProvider`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            PartialSuccessResult(taskSuccessCount = 2, taskFailureCount = 1)
        }

        assertEquals("PARTIAL_SUCCESS", recorded.status)
        assertEquals(2, recorded.successCount)
        assertEquals(1, recorded.failureCount)
        assertNotNull(recorded.resultSummary)
    }

    @Test
    fun `records FAILED when all accounts fail via provider`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            AllFailedResult()
        }

        assertEquals("FAILED", recorded.status)
        assertEquals(0, recorded.successCount)
        assertEquals(3, recorded.failureCount)
    }

    @Test
    fun `records SUCCESS when all accounts succeed via provider`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            AllSuccessResult()
        }

        assertEquals("SUCCESS", recorded.status)
        assertEquals(3, recorded.successCount)
        assertEquals(0, recorded.failureCount)
    }

    @Test
    fun `uses account counts not mail counts for summary provider`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val recorded = service.runAndRecord(
            taskType = "AUTO_REPLY_ALL",
            triggerType = "SCHEDULED",
            request = mapOf("maxMessagesPerAccount" to 20)
        ) {
            BatchResultWithMailCounts()
        }

        assertEquals("SUCCESS", recorded.status)
        assertEquals(1, recorded.successCount)
        assertEquals(0, recorded.failureCount)
    }

    private fun execution(status: String): TaskExecution =
        TaskExecution(
            id = 1L,
            taskType = "AUTO_REPLY_ACCOUNT",
            triggerType = "QUEUE",
            status = status,
            requestPayload = "{}",
            resultSummary = "{}",
            startedAt = LocalDateTime.now()
        )
}

private data class QueueFanOutResultForTest(
    val dispatched: Int
)

private data class PartialSuccessResult(
    override val taskSuccessCount: Int,
    override val taskFailureCount: Int,
    override val taskFinalStatus: String = "PARTIAL_SUCCESS"
) : TaskExecutionSummaryProvider

private class AllFailedResult : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int = 0
    override val taskFailureCount: Int = 3
    override val taskFinalStatus: String? = "FAILED"
}

private class AllSuccessResult : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int = 3
    override val taskFailureCount: Int = 0
    override val taskFinalStatus: String? = null
}

private class BatchResultWithMailCounts : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int = 1
    override val taskFailureCount: Int = 0
    override val taskFinalStatus: String? = null
}
