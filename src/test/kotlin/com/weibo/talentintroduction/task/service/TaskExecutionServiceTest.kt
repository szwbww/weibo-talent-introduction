package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class TaskExecutionServiceTest {
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val service = TaskExecutionService(repository, ObjectMapper(), schedulingProperties)

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

    @Test
    fun `nextPollTime returns null for dash cron`() {
        val result = service.nextPollTime()
        assertEquals(null, result)
    }

    @Test
    fun `nextPollTime returns future time for valid cron`() {
        val validCronService = TaskExecutionService(
            repository, ObjectMapper(),
            MailSchedulingProperties(autoReplyAllCron = "0 */5 * * * *")
        )
        val result = validCronService.nextPollTime()
        assertNotNull(result)
        assertTrue(result!!.isAfter(LocalDateTime.now()))
    }

    @Test
    fun `nextPollTime returns null for invalid cron`() {
        val invalidCronService = TaskExecutionService(
            repository, ObjectMapper(),
            MailSchedulingProperties(autoReplyAllCron = "not-a-cron")
        )
        val result = invalidCronService.nextPollTime()
        assertEquals(null, result)
    }

    @Test
    fun `listRecentPolls accepts limit 1`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 1))
            .thenReturn(listOf(execution("SUCCESS")))

        val result = service.listRecentPolls(1)

        assertEquals(1, result.size)
    }

    @Test
    fun `listRecentPolls accepts limit 100`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 100))
            .thenReturn(emptyList())

        val result = service.listRecentPolls(100)

        assertEquals(0, result.size)
    }

    @Test
    fun `listRecentPolls rejects limit 0`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.listRecentPolls(0)
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    @Test
    fun `listRecentPolls rejects limit 101`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.listRecentPolls(101)
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    @Test
    fun `runAndRecordWithResult returns business result on success`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val (recorded, result) = service.runAndRecordWithResult(
            taskType = "EXPERT_REVALIDATION",
            triggerType = "MANUAL",
            request = "test"
        ) {
            RevalidationTestResult(passed = 42, demoted = 0, demotionFailed = 0, total = 42)
        }

        assertEquals("SUCCESS", recorded.status)
        assertEquals(42, result.passed)
        assertEquals(42, recorded.successCount)
        assertEquals(0, recorded.failureCount)
    }

    @Test
    fun `runAndRecordWithResult saves FAILED and rethrows exception`() {
        val saveInvocations = mutableListOf<TaskExecution>()
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                saveInvocations.add(execution)
                execution.copy(id = execution.id ?: 1L)
            }

        val ex = assertThrows(RuntimeException::class.java) {
            service.runAndRecordWithResult<Any>(
                taskType = "EXPERT_REVALIDATION",
                triggerType = "MANUAL",
                request = "test"
            ) {
                error("ES connection refused")
            }
        }

        assertTrue(ex.message!!.contains("ES connection refused"))
        assertEquals(2, saveInvocations.size)
        assertEquals("RUNNING", saveInvocations[0].status)
        assertEquals("FAILED", saveInvocations[1].status)
        assertEquals(1, saveInvocations[1].failureCount)
        assertTrue(saveInvocations[1].errorMessage!!.contains("ES connection refused"))
    }

    @Test
    fun `runAndRecordWithResult records PARTIAL_SUCCESS from provider`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val (recorded, result) = service.runAndRecordWithResult(
            taskType = "EXPERT_REVALIDATION",
            triggerType = "MANUAL",
            request = "test"
        ) {
            RevalidationTestResult(passed = 5, demoted = 3, demotionFailed = 2, total = 10)
        }

        assertEquals("PARTIAL_SUCCESS", recorded.status)
        assertEquals(5, recorded.successCount)
        assertEquals(5, recorded.failureCount)
        assertEquals(5, result.passed)
    }

    @Test
    fun `records DiscoveryResult with summaryText and bySource in resultSummary`() {
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 1L)
            }

        val stats = com.weibo.talentintroduction.discovery.domain.DiscoveryStats()
        val sourceStats = stats.getOrCreateSourceStats("EUROPE_PMC", "FULLTEXT_XML")
        sourceStats.papersSearched = 5
        sourceStats.indexed = 3
        sourceStats.promoted = 2
        stats.refreshGlobalCounts()

        val discoveryResult = com.weibo.talentintroduction.discovery.domain.DiscoveryResult(
            triggeredBy = "MANUAL",
            stats = stats,
            summaryText = "完成: 论文 5, 收录 3, 晋升 2"
        )

        val recorded = service.runAndRecord(
            taskType = "EXPERT_DISCOVERY",
            triggerType = "MANUAL",
            request = com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria()
        ) {
            discoveryResult
        }

        assertEquals("SUCCESS", recorded.status)
        assertNotNull(recorded.resultSummary)
        val summary = ObjectMapper().readTree(recorded.resultSummary)
        assertTrue(summary.has("summaryText"))
        assertEquals("完成: 论文 5, 收录 3, 晋升 2", summary.get("summaryText").asText())
        assertTrue(summary.has("stats"))
        assertTrue(summary.get("stats").has("bySource"))
        val bySource = summary.get("stats").get("bySource")
        assertTrue(bySource.has("EUROPE_PMC"))
        assertEquals(5, bySource.get("EUROPE_PMC").get("papersSearched").asInt())
        assertEquals(3, bySource.get("EUROPE_PMC").get("indexed").asInt())
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

private data class RevalidationTestResult(
    val passed: Int,
    val demoted: Int,
    val demotionFailed: Int,
    val total: Int
) : TaskExecutionSummaryProvider {
    override val taskSuccessCount: Int get() = passed
    override val taskFailureCount: Int get() = demoted + demotionFailed
    override val taskFinalStatus: String?
        get() = when {
            total == 0 -> "SUCCESS"
            taskFailureCount == 0 -> "SUCCESS"
            taskSuccessCount == 0 -> "FAILED"
            else -> "PARTIAL_SUCCESS"
        }
}
