package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.ArgumentCaptor
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TaskProgressStoreTest {

    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val objectMapper = ObjectMapper()

    @Test
    fun `tryStart succeeds when no task is running`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val progress = runningProgress("TEST")
        assertTrue(store.tryStart("TEST", progress))
        assertEquals("RUNNING", store.get("TEST")?.status)
    }

    @Test
    fun `tryStart fails when task is already running`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("TEST", runningProgress("TEST"))
        val progress = runningProgress("TEST")
        assertFalse(store.tryStart("TEST", progress))
    }

    @Test
    fun `tryStart fails when task is cancelling`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.requestCancel("TEST")
        assertFalse(store.tryStart("TEST", runningProgress("TEST")))
        assertEquals("CANCELLING", store.get("TEST")?.status)
    }

    @Test
    fun `tryStart succeeds after task completes`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("TEST", runningProgress("TEST"))
        store.update("TEST", completedProgress("TEST"))
        val progress = runningProgress("TEST")
        assertTrue(store.tryStart("TEST", progress))
    }

    @Test
    fun `concurrent tryStart only one succeeds`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        var successCount = 0
        val lock = Any()

        repeat(threadCount) {
            executor.submit {
                try {
                    val progress = runningProgress("RACE")
                    val ok = store.tryStart("RACE", progress)
                    if (ok) {
                        synchronized(lock) { successCount++ }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        assertEquals(1, successCount)
        assertEquals("RUNNING", store.get("RACE")?.status)
    }

    @Test
    fun `requestCancel requires executionId`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("TEST", runningProgress("TEST"))
        // 没有设置 executionId，requestCancel 应该无操作
        assertFalse(store.requestCancel("TEST"))
        assertFalse(store.isCancelled("TEST"))
        assertEquals("RUNNING", store.get("TEST")?.status)
    }

    @Test
    fun `requestCancel with executionId sets CANCELLING`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 42L)
        assertTrue(store.requestCancel("TEST"))
        assertTrue(store.isCancelled("TEST"))
        assertEquals("CANCELLING", store.get("TEST")?.status)
    }

    @Test
    fun `requestCancel on non-running does nothing`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("TEST", completedProgress("TEST"))
        store.setCurrentExecutionId("TEST", 42L)
        assertFalse(store.requestCancel("TEST"))
        assertFalse(store.isCancelled("TEST"))
        assertEquals("COMPLETED", store.get("TEST")?.status)
    }

    @Test
    fun `cancelled task cannot be restarted until completed`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.requestCancel("TEST")
        // CANCELLING 期间不能启动
        assertFalse(store.tryStart("TEST", runningProgress("TEST")))
        // 任务完成 CANCELLED 后可以启动
        store.update("TEST", cancelledProgress("TEST"))
        assertTrue(store.tryStart("TEST", runningProgress("TEST")))
    }

    @Test
    fun `isRunning returns true for RUNNING and CANCELLING`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("A", runningProgress("A"))
        store.update("B", completedProgress("B"))
        store.update("C", TaskProgress("C", "CANCELLING", 0, 0, 0))
        assertTrue(store.isRunning("A"))
        assertFalse(store.isRunning("B"))
        assertTrue(store.isRunning("C"))
    }

    @Test
    fun `persistProgressLog writes DB with executionId`() {
        Mockito.reset(progressLogRepository)
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.setCurrentExecutionId("TEST", 99L)
        store.update("TEST", runningProgress("TEST"))

        val captor = ArgumentCaptor.forClass(TaskProgressLog::class.java)
        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
        val saved = captor.value
        assertEquals("TEST", saved.taskType)
        assertEquals(99L, saved.taskExecutionId)
        assertEquals("RUNNING", saved.status)
    }

    @Test
    fun `restore from log converts RUNNING to INTERRUPTED`() {
        val log = TaskProgressLog(
            id = 1L,
            taskType = "TEST",
            taskExecutionId = 10L,
            batchNumber = 3,
            status = "RUNNING",
            processedCount = 50,
            totalCount = 100,
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
            .thenReturn(log)

        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val restored = store.get("TEST")
        assertNotNull(restored)
        assertEquals("INTERRUPTED", restored?.status)
        assertEquals(50, restored?.processedCount)
    }

    @Test
    fun `restore from log converts CANCELLING to INTERRUPTED`() {
        val log = TaskProgressLog(
            id = 1L,
            taskType = "TEST",
            status = "CANCELLING",
            batchNumber = 2,
            processedCount = 10,
            totalCount = 20,
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
            .thenReturn(log)

        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val restored = store.get("TEST")
        assertEquals("INTERRUPTED", restored?.status)
    }

    @Test
    fun `restore from log preserves COMPLETED status`() {
        val log = TaskProgressLog(
            id = 1L,
            taskType = "TEST",
            status = "COMPLETED",
            batchNumber = -1,
            processedCount = 10,
            totalCount = 10,
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
            .thenReturn(log)

        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val restored = store.get("TEST")
        assertEquals("COMPLETED", restored?.status)
    }

    @Test
    fun `clearExecutionContext removes cancellation flag`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.update("TEST", runningProgress("TEST"))
        store.requestCancel("TEST")
        assertTrue(store.isCancelled("TEST"))

        store.clearExecutionContext("TEST", 1L)
        assertFalse(store.isCancelled("TEST"))
        assertNull(store.getCurrentExecutionId("TEST"))
    }

    @Test
    fun `new execution is isolated from old cancellation flag`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        // 旧任务
        val (started1, token1) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started1)
        store.bindExecutionId("TEST", token1, 1L)
        store.update("TEST", runningProgress("TEST"))
        store.requestCancel("TEST")
        assertTrue(store.isCancelled("TEST"))
        // 旧任务结束
        store.update("TEST", cancelledProgress("TEST"))
        // 新任务启动
        val (started2, token2) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started2)
        store.bindExecutionId("TEST", token2, 2L)
        // 新任务不应受旧 flag 影响
        assertFalse(store.isCancelled("TEST"))
    }

    @Test
    fun `update with stale executionId is rejected`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.update("TEST", runningProgress("TEST"))

        // Simulate new execution
        store.update("TEST", completedProgress("TEST"))
        val (started2, token2) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started2)
        store.bindExecutionId("TEST", token2, 2L)

        val accepted = store.update("TEST", runningProgress("TEST"), expectedExecutionId = 1L)
        assertFalse(accepted)
        val current = store.get("TEST")
        assertNotNull(current)
        assertEquals(2L, current?.executionId)
    }

    @Test
    fun `clearExecutionContext with stale executionId is rejected`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.update("TEST", runningProgress("TEST"))

        // Another task takes over
        store.update("TEST", completedProgress("TEST"))
        val (started2, token2) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started2)
        store.bindExecutionId("TEST", token2, 2L)

        val cleared = store.clearExecutionContext("TEST", 1L)
        assertFalse(cleared)
        assertEquals(2L, store.getCurrentExecutionId("TEST"))
        assertTrue(store.isRunning("TEST"))
    }

    @Test
    fun `clearExecutionContext with matching executionId succeeds`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        store.bindExecutionId("TEST", token, 1L)
        store.update("TEST", runningProgress("TEST"))
        store.update("TEST", completedProgress("TEST"))

        val cleared = store.clearExecutionContext("TEST", 1L)
        assertTrue(cleared)
        assertNull(store.getCurrentExecutionId("TEST"))
        assertFalse(store.isCancelled("TEST"))
    }

    @Test
    fun `bindExecutionId transfers cancellation flag`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("TEST", runningProgress("TEST"))
        assertTrue(started)
        // Cancel while pending - should succeed and set flag on pending token
        assertTrue(store.requestCancel("TEST"))
        assertTrue(store.isCancelled("TEST"))
        store.bindExecutionId("TEST", token, 42L)
        // After binding, cancellation flag should be transferred to real executionId
        assertTrue(store.isCancelled("TEST"))
        assertEquals("CANCELLING", store.get("TEST")?.status)
    }

    @Test
    fun `concurrent stale update rejected by CAS`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("RACE", runningProgress("RACE"))
        assertTrue(started)
        store.bindExecutionId("RACE", token, 1L)

        val updateRejected = AtomicBoolean(false)
        val latch = CountDownLatch(2)

        // Thread A: legitimate update with correct executionId
        Thread {
            store.update("RACE", TaskProgress("RACE", "RUNNING", 1, 10, 100), expectedExecutionId = 1L)
            latch.countDown()
        }.start()

        // Thread B: stale update with wrong executionId
        Thread {
            Thread.sleep(5) // small delay to increase chance of interleaving
            val accepted = store.update("RACE", TaskProgress("RACE", "RUNNING", 1, 99, 100), expectedExecutionId = 999L)
            updateRejected.set(!accepted)
            latch.countDown()
        }.start()

        latch.await()
        assertTrue(updateRejected.get(), "Stale update with wrong executionId should be rejected")
    }

    @Test
    fun `concurrent stale clear rejected by CAS`() {
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val (started, token) = store.tryStartWithToken("RACE", runningProgress("RACE"))
        assertTrue(started)
        store.bindExecutionId("RACE", token, 1L)

        val clearRejected = AtomicBoolean(false)
        val latch = CountDownLatch(2)

        // Thread A: legitimate clear with correct executionId
        Thread {
            store.clearExecutionContext("RACE", 1L)
            latch.countDown()
        }.start()

        // Thread B: stale clear with wrong executionId
        Thread {
            Thread.sleep(5)
            val accepted = store.clearExecutionContext("RACE", 999L)
            clearRejected.set(!accepted)
            latch.countDown()
        }.start()

        latch.await()
        assertTrue(clearRejected.get(), "Stale clear with wrong executionId should be rejected")
    }

    @Test
    fun `persistProgressLog writes batchRejectReasonsJson`() {
        Mockito.reset(progressLogRepository)
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val reasons = mapOf("NO_EMAIL_IN_FULLTEXT" to 2, "FULLTEXT_FETCH_FAILED" to 1)
        store.update("TEST", TaskProgress(
            taskType = "TEST", status = "RUNNING",
            batchNumber = 1, processedCount = 3, totalCount = 100,
            batchProcessed = 3, batchPassed = 0, batchRejected = 3,
            batchRejectReasons = reasons
        ))

        val captor = ArgumentCaptor.forClass(TaskProgressLog::class.java)
        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
        val saved = captor.value
        assertNotNull(saved.batchRejectReasonsJson)
        val parsed = objectMapper.readValue<Map<String, Int>>(saved.batchRejectReasonsJson!!)
        assertEquals(2, parsed["NO_EMAIL_IN_FULLTEXT"])
        assertEquals(1, parsed["FULLTEXT_FETCH_FAILED"])
    }

    @Test
    fun `persistProgressLog writes null batchRejectReasonsJson when absent`() {
        Mockito.reset(progressLogRepository)
        val store = TaskProgressStore(progressLogRepository, objectMapper)
        store.update("TEST", runningProgress("TEST"))

        val captor = ArgumentCaptor.forClass(TaskProgressLog::class.java)
        Mockito.verify(progressLogRepository, Mockito.times(1)).save(captor.capture())
        assertNull(captor.value.batchRejectReasonsJson)
    }

    @Test
    fun `restore from log round-trips batchRejectReasons`() {
        val log = TaskProgressLog(
            id = 1L,
            taskType = "TEST",
            status = "COMPLETED",
            batchNumber = 2,
            processedCount = 10,
            totalCount = 100,
            batchProcessed = 5,
            batchPassed = 2,
            batchRejected = 3,
            batchRejectReasonsJson = """{"DUPLICATE":1,"NO_EMAIL_IN_FULLTEXT":2}""",
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
            .thenReturn(log)

        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val restored = store.get("TEST")
        assertNotNull(restored)
        assertEquals(mapOf("DUPLICATE" to 1, "NO_EMAIL_IN_FULLTEXT" to 2), restored?.batchRejectReasons)
    }

    @Test
    fun `restore from log ignores invalid batchRejectReasonsJson`() {
        val log = TaskProgressLog(
            id = 1L,
            taskType = "TEST",
            status = "COMPLETED",
            batchNumber = 1,
            processedCount = 5,
            totalCount = 10,
            batchRejectReasonsJson = "not-json",
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("TEST"))
            .thenReturn(log)

        val store = TaskProgressStore(progressLogRepository, objectMapper)
        val restored = store.get("TEST")
        assertNotNull(restored)
        assertEquals("COMPLETED", restored?.status)
        assertEquals(5, restored?.processedCount)
        assertEquals(10, restored?.totalCount)
        assertNull(restored?.batchRejectReasons)
    }

    private fun runningProgress(taskType: String) = TaskProgress(
        taskType = taskType, status = "RUNNING",
        batchNumber = 0, processedCount = 0, totalCount = 0
    )

    private fun completedProgress(taskType: String) = TaskProgress(
        taskType = taskType, status = "COMPLETED",
        batchNumber = -1, processedCount = 1, totalCount = 1
    )

    private fun cancelledProgress(taskType: String) = TaskProgress(
        taskType = taskType, status = "CANCELLED",
        batchNumber = -1, processedCount = 1, totalCount = 1
    )
}
