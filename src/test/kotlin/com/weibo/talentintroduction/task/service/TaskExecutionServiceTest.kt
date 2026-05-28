package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
