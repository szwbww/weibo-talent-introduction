package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * I-6: bindExecutionId must rewrite pending-token rows to the real execution id,
 * fail soft on rebind errors, and never touch rows when the bind is rejected.
 */
class TaskProgressStoreRebindTest {

    private val repository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val store = TaskProgressStore(repository, ObjectMapper())

    private fun startWithToken(): Long {
        val (started, token) = store.tryStartWithToken(
            "MANUAL_INITIAL_OUTREACH",
            TaskProgress(
                taskType = "MANUAL_INITIAL_OUTREACH",
                status = "RUNNING",
                batchNumber = 0,
                processedCount = 0,
                totalCount = 0
            )
        )
        assertTrue(started)
        return token
    }

    @Test
    fun `bindExecutionId success rebinds pending token rows to real execution id`() {
        val token = startWithToken()

        assertTrue(store.bindExecutionId("MANUAL_INITIAL_OUTREACH", token, 42L))

        Mockito.verify(repository).rebindPendingExecutionId(token, 42L)
    }

    @Test
    fun `rebind failure is logged not thrown and bind still succeeds`() {
        val token = startWithToken()
        Mockito.`when`(repository.rebindPendingExecutionId(Mockito.anyLong(), Mockito.anyLong()))
            .thenThrow(RuntimeException("db down"))

        assertTrue(store.bindExecutionId("MANUAL_INITIAL_OUTREACH", token, 42L))
    }

    @Test
    fun `rejected bind does not call rebind`() {
        val token = startWithToken()

        assertFalse(store.bindExecutionId("MANUAL_INITIAL_OUTREACH", token + 1, 42L))

        Mockito.verify(repository, Mockito.never())
            .rebindPendingExecutionId(Mockito.anyLong(), Mockito.anyLong())
    }
}
