package com.weibo.talentintroduction.task.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class TaskProgressStoreTest {

    @Test
    fun `tryStart succeeds when no task is running`() {
        val store = TaskProgressStore()
        val progress = runningProgress("TEST")
        assertTrue(store.tryStart("TEST", progress))
        assertEquals("RUNNING", store.get("TEST")?.status)
    }

    @Test
    fun `tryStart fails when task is already running`() {
        val store = TaskProgressStore()
        store.update("TEST", runningProgress("TEST"))
        val progress = runningProgress("TEST")
        assertFalse(store.tryStart("TEST", progress))
    }

    @Test
    fun `tryStart succeeds after task completes`() {
        val store = TaskProgressStore()
        store.update("TEST", runningProgress("TEST"))
        store.update("TEST", completedProgress("TEST"))
        val progress = runningProgress("TEST")
        assertTrue(store.tryStart("TEST", progress))
    }

    @Test
    fun `concurrent tryStart only one succeeds`() {
        val store = TaskProgressStore()
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

    private fun runningProgress(taskType: String) = TaskProgress(
        taskType = taskType, status = "RUNNING",
        batchNumber = 0, processedCount = 0, totalCount = 0
    )

    private fun completedProgress(taskType: String) = TaskProgress(
        taskType = taskType, status = "COMPLETED",
        batchNumber = -1, processedCount = 1, totalCount = 1
    )
}
