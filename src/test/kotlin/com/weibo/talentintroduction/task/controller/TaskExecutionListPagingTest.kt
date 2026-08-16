package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionPage
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * B1 (I0-1 / I0-3 / I0-5 / I0-2): paged listExecutions contract.
 * Controller layer: size/page clamping (I0-5) + response shape {items, total}
 * without TEXT columns (I0-1). Service layer: each filter combo reads the
 * matching paged + count query, total from the same combo (I0-3).
 * Migration: V100 index text assertion (I0-2).
 */
class TaskExecutionListPagingTest {

    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val service = TaskExecutionService(repository, ObjectMapper(), com.weibo.talentintroduction.config.MailSchedulingProperties(autoReplyAllCron = "-"))
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val controller = TaskExecutionController(service, objectMapper)

    private fun item(
        id: Long = 1L,
        taskType: String = "AUTO_REPLY_ALL",
        status: String = "SUCCESS"
    ) = TaskExecutionListItem(
        id = id,
        taskType = taskType,
        triggerType = "QUEUE",
        status = status,
        successCount = 4,
        failureCount = 0,
        errorMessage = null,
        startedAt = LocalDateTime.of(2026, 8, 16, 10, 0),
        finishedAt = LocalDateTime.of(2026, 8, 16, 10, 5)
    )

    // ---- I0-5: size/page clamping in the controller ----

    @Test
    fun `size zero is clamped to 1 without throwing`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(0L)

        val response = controller.listExecutions(null, null, 0, 0)

        assertEquals(0L, response.total)
        Mockito.verify(repository).findPage(1, 0L)
    }

    @Test
    fun `negative size is clamped to 1 without throwing`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(0L)

        val response = controller.listExecutions(null, null, 0, -1)

        assertEquals(0L, response.total)
        Mockito.verify(repository).findPage(1, 0L)
    }

    @Test
    fun `huge size is clamped to 200 without throwing`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(0L)

        val response = controller.listExecutions(null, null, 0, 100000)

        assertEquals(0L, response.total)
        Mockito.verify(repository).findPage(200, 0L)
    }

    @Test
    fun `negative page is clamped to 0`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(0L)

        val response = controller.listExecutions(null, null, -3, 50)

        assertEquals(0L, response.total)
        Mockito.verify(repository).findPage(50, 0L)
    }

    // ---- I0-1: response shape is {items, total} without TEXT columns ----

    @Test
    fun `response shape has items and total and items carry no TEXT columns`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong()))
            .thenReturn(listOf(item(id = 42L)))
        Mockito.`when`(repository.countAll()).thenReturn(137L)

        val response = controller.listExecutions(null, null, 0, 50)

        assertEquals(137L, response.total)
        assertEquals(1, response.items.size)
        assertEquals(42L, response.items[0].id)
        assertEquals("AUTO_REPLY_ALL", response.items[0].taskType)
        assertEquals("QUEUE", response.items[0].triggerType)
        assertEquals("SUCCESS", response.items[0].status)
        assertEquals(4, response.items[0].successCount)
        assertEquals(0, response.items[0].failureCount)
        assertEquals("2026-08-16T10:00", response.items[0].startedAt)
        assertEquals("2026-08-16T10:05", response.items[0].finishedAt)

        val json = objectMapper.writeValueAsString(response)
        assertTrue(json.startsWith("{\"items\":"), "response must be a {items, total} object, got: $json")
        assertTrue(json.contains("\"total\":137"), "response must carry total, got: $json")
        assertTrue(!json.contains("requestPayload"), "list items must not carry requestPayload")
        assertTrue(!json.contains("resultSummary"), "list items must not carry resultSummary")
    }

    // ---- I0-3: each filter combo uses the matching paged + count query ----

    @Test
    fun `no filter uses findPage and countAll`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong()))
            .thenReturn(listOf(item()))
        Mockito.`when`(repository.countAll()).thenReturn(137L)

        val page = service.listExecutions(null, null, 0, 50)

        assertEquals(137L, page.total)
        Mockito.verify(repository).findPage(50, 0L)
        Mockito.verify(repository).countAll()
        Mockito.verify(repository, Mockito.never()).countByTaskType(Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).countByStatus(Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).countByTaskTypeAndStatus(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `task type filter uses findPageByTaskType and countByTaskType`() {
        Mockito.`when`(repository.findPageByTaskType(Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong()))
            .thenReturn(listOf(item(taskType = "AUTO_REPLY_ALL")))
        Mockito.`when`(repository.countByTaskType("AUTO_REPLY_ALL")).thenReturn(80L)

        val page = service.listExecutions("AUTO_REPLY_ALL", null, 0, 50)

        assertEquals(80L, page.total)
        Mockito.verify(repository).findPageByTaskType("AUTO_REPLY_ALL", 50, 0L)
        Mockito.verify(repository).countByTaskType("AUTO_REPLY_ALL")
        Mockito.verify(repository, Mockito.never()).countAll()
        Mockito.verify(repository, Mockito.never()).countByStatus(Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).countByTaskTypeAndStatus(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `status filter uses findPageByStatus and countByStatus`() {
        Mockito.`when`(repository.findPageByStatus(Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong()))
            .thenReturn(listOf(item(status = "FAILED")))
        Mockito.`when`(repository.countByStatus("FAILED")).thenReturn(12L)

        val page = service.listExecutions(null, "FAILED", 0, 50)

        assertEquals(12L, page.total)
        Mockito.verify(repository).findPageByStatus("FAILED", 50, 0L)
        Mockito.verify(repository).countByStatus("FAILED")
        Mockito.verify(repository, Mockito.never()).countAll()
        Mockito.verify(repository, Mockito.never()).countByTaskType(Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).countByTaskTypeAndStatus(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun `task type and status filter uses findPageByTaskTypeAndStatus and countByTaskTypeAndStatus`() {
        Mockito.`when`(
            repository.findPageByTaskTypeAndStatus(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyLong())
        ).thenReturn(listOf(item(taskType = "AUTO_REPLY_ACCOUNT", status = "FAILED")))
        Mockito.`when`(repository.countByTaskTypeAndStatus("AUTO_REPLY_ACCOUNT", "FAILED")).thenReturn(3L)

        val page = service.listExecutions("AUTO_REPLY_ACCOUNT", "FAILED", 0, 50)

        assertEquals(3L, page.total)
        Mockito.verify(repository).findPageByTaskTypeAndStatus("AUTO_REPLY_ACCOUNT", "FAILED", 50, 0L)
        Mockito.verify(repository).countByTaskTypeAndStatus("AUTO_REPLY_ACCOUNT", "FAILED")
        Mockito.verify(repository, Mockito.never()).countAll()
        Mockito.verify(repository, Mockito.never()).countByTaskType(Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).countByStatus(Mockito.anyString())
    }

    @Test
    fun `offset is page times size`() {
        Mockito.`when`(repository.findPage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(emptyList())
        Mockito.`when`(repository.countAll()).thenReturn(200L)

        val page = service.listExecutions(null, null, 2, 50)

        assertEquals(200L, page.total)
        Mockito.verify(repository).findPage(50, 100L)
    }

    // ---- I0-2: V100 migration text ----

    @Test
    fun `V100 adds the three list indexes without placeholder syntax`() {
        val sql = Files.readString(
            Path.of("src/main/resources/db/migration/V100__add_task_execution_indexes.sql")
        )

        assertTrue(sql.contains("CREATE INDEX idx_te_started ON task_execution (started_at)"))
        assertTrue(sql.contains("CREATE INDEX idx_te_type_started ON task_execution (task_type, started_at)"))
        assertTrue(sql.contains("CREATE INDEX idx_te_status_started ON task_execution (status, started_at)"))
        assertTrue(!sql.contains("${'$'}{"), "V100 must not contain Flyway placeholder syntax")
    }
}
