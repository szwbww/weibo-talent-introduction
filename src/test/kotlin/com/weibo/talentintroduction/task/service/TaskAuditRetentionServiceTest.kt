package com.weibo.talentintroduction.task.service

import com.weibo.talentintroduction.config.TaskRetentionProperties
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.InOrder
import org.mockito.Mockito
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class TaskAuditRetentionServiceTest {

    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val executionRepository = Mockito.mock(TaskExecutionRepository::class.java)
    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)

    private fun service(props: TaskRetentionProperties = TaskRetentionProperties()) =
        TaskAuditRetentionService(progressLogRepository, executionRepository, props)

    // ---- I3-3: cutoff ----

    @Test
    fun `cutoff is 90 days ago in Asia-Shanghai when retentionDays is 90`() {
        val cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime::class.java)
        service(TaskRetentionProperties(retentionDays = 90)).purge()

        Mockito.verify(progressLogRepository).deleteOlderThan(
            captureValue(cutoffCaptor, LocalDateTime.MIN),
            Mockito.anyInt()
        )
        val cutoff = cutoffCaptor.value
        val expected = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(90)
        val skewMinutes = Math.abs(Duration.between(cutoff, expected).toMinutes())
        assertTrue(
            skewMinutes < 1,
            "cutoff $cutoff must be ~90 days before now in Asia/Shanghai (skew ${skewMinutes}min)"
        )
    }

    // ---- I3-2: batching ----

    @Test
    fun `purge deletes in batches until a batch returns zero`() {
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
            .thenReturn(2000, 2000, 137, 0)

        val result = service().purge()

        assertEquals(4137, result.progressLogDeleted, "cumulative deleted rows across 4 batches")
        assertEquals(0, result.executionDeleted)
        assertEquals(0, result.failedTables)
        assertEquals(4137, result.taskSuccessCount)
        assertEquals(0, result.taskFailureCount)
        assertEquals("SUCCESS", result.taskFinalStatus)
        Mockito.verify(progressLogRepository, Mockito.times(4))
            .deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt())
    }

    @Test
    fun `purge stops early when maxRowsPerRun cap is reached`() {
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.eq(2000)))
            .thenReturn(2000)
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.eq(1000)))
            .thenReturn(1000)

        val result = service(TaskRetentionProperties(maxRowsPerRun = 3000)).purge()

        assertEquals(3000, result.progressLogDeleted, "per-run cap must not be exceeded")
        assertEquals(0, result.failedTables)
        assertEquals("SUCCESS", result.taskFinalStatus)
        val limits = ArgumentCaptor.forClass(Int::class.java)
        Mockito.verify(progressLogRepository, Mockito.times(2))
            .deleteOlderThan(anyValue(LocalDateTime.now()), captureValue(limits, 0))
        assertEquals(
            listOf(2000, 1000),
            limits.allValues,
            "delete limits must be the remaining capacity: 2000 then 1000, no third call"
        )
    }

    // ---- I3-4: deletion order ----

    @Test
    fun `progress log table is purged before execution table`() {
        service().purge()

        val inOrder: InOrder = Mockito.inOrder(progressLogRepository, executionRepository)
        inOrder.verify(progressLogRepository).deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt())
        inOrder.verify(executionRepository).deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt())
    }

    // ---- I3-6: partial failure / total failure ----

    @Test
    fun `failure of one table still purges the other and yields PARTIAL_SUCCESS`() {
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
            .thenThrow(RuntimeException("progress log purge boom"))

        val result = service().purge()

        assertEquals(0, result.progressLogDeleted)
        assertEquals(0, result.executionDeleted)
        assertEquals(1, result.failedTables)
        assertEquals("PARTIAL_SUCCESS", result.taskFinalStatus)
        Mockito.verify(executionRepository).deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt())
    }

    @Test
    fun `failure of both tables yields FAILED`() {
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
            .thenThrow(RuntimeException("progress log purge boom"))
        Mockito.`when`(executionRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
            .thenThrow(RuntimeException("execution purge boom"))

        val result = service().purge()

        assertEquals(2, result.failedTables)
        assertEquals(0, result.taskSuccessCount)
        assertEquals(2, result.taskFailureCount)
        assertEquals("FAILED", result.taskFinalStatus)
    }

    // ---- I3-5: no self-exemption ----

    @Test
    fun `delete condition is only cutoff plus batch size with no task type filter`() {
        Mockito.`when`(progressLogRepository.deleteOlderThan(anyValue(LocalDateTime.now()), Mockito.anyInt()))
            .thenReturn(1, 0)
        val cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime::class.java)
        val batchCaptor = ArgumentCaptor.forClass(Int::class.java)
        val props = TaskRetentionProperties(batchSize = 2000)

        service(props).purge()

        Mockito.verify(progressLogRepository, Mockito.times(2))
            .deleteOlderThan(
                captureValue(cutoffCaptor, LocalDateTime.MIN),
                captureValue(batchCaptor, 0)
            )
        assertEquals(listOf(2000, 2000), batchCaptor.allValues, "every batch must use the configured batch size")
        val cutoffs = cutoffCaptor.allValues
        assertEquals(1, cutoffs.distinct().size, "one shared cutoff for both tables (I3-3)")
        val skewMinutes = Math.abs(
            Duration.between(cutoffs.first(), LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(90)).toMinutes()
        )
        assertTrue(skewMinutes < 1, "cutoff must be the 90-day retention boundary in Asia/Shanghai")
    }

    // ---- N3-5: disabled scheduler is a no-op ----

    @Test
    fun `scheduler does nothing when retention is disabled`() {
        val scheduler = TaskAuditRetentionScheduler(
            TaskRetentionProperties(enabled = false),
            taskExecutionService,
            service()
        )

        scheduler.scheduleRetention()

        Mockito.verify(taskExecutionService, Mockito.never()).runAndRecordWithResult<Unit>(
            eqValue("TASK_AUDIT_RETENTION"), eqValue("SCHEDULED"), eqValue("task-audit-retention"),
            anyValue(null), Mockito.isNull(), anyValue {}
        )
    }

    @Test
    fun `scheduler records retention run when enabled`() {
        val scheduler = TaskAuditRetentionScheduler(
            TaskRetentionProperties(enabled = true),
            taskExecutionService,
            service()
        )

        scheduler.scheduleRetention()

        Mockito.verify(taskExecutionService).runAndRecordWithResult<Unit>(
            eqValue("TASK_AUDIT_RETENTION"), eqValue("SCHEDULED"), eqValue("task-audit-retention"),
            anyValue(null), Mockito.isNull(), anyValue {}
        )
    }

    private fun eqValue(value: String): String =
        Mockito.eq(value) ?: value

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    /** 沿用仓库既有惯例（如 ManualOutreachTxHelperTest / BatchSendControlServiceTest）：
     * `captor.capture()` 作语句调用登记匹配器（返回值被丢弃，不会触发 Kotlin 的平台类型空检查），
     * 实参传回非空 default；真实值由 captor 事后读取。 */
    private fun <T> captureValue(captor: ArgumentCaptor<T>, defaultValue: T): T {
        captor.capture()
        return defaultValue
    }
}
