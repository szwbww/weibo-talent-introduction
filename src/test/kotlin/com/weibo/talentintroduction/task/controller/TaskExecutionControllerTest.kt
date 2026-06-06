package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

class TaskExecutionControllerTest {
    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val schedulingProperties = MailSchedulingProperties(autoReplyAllCron = "-")
    private val objMapper = ObjectMapper().registerKotlinModule()
    private val service = TaskExecutionService(repository, objMapper, schedulingProperties)
    private val controller = TaskExecutionController(service, objMapper)

    @Test
    fun `recent polls default limit 10`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 10))
            .thenReturn(listOf(exec("SUCCESS")))

        val result = controller.recentPolls(10)

        assertEquals(1, result.size)
    }

    @Test
    fun `recent polls limit 0 rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.recentPolls(0)
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    @Test
    fun `recent polls limit 101 rejected`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.recentPolls(101)
        }
        assertTrue(ex.message!!.contains("between 1 and 100"))
    }

    @Test
    fun `recent polls with new result summary fields`() {
        val json = """{"totalAccountsToPoll":5,"accountsPolled":5,"totalExpertsToCheck":0,"expertsChecked":0,"expertsWithReply":["a@test.com"]}"""
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 1))
            .thenReturn(listOf(TaskExecution(
                id = 1L,
                taskType = "AUTO_REPLY_ALL",
                triggerType = "SCHEDULED",
                status = "SUCCESS",
                requestPayload = "{}",
                resultSummary = json,
                startedAt = LocalDateTime.now(),
                finishedAt = LocalDateTime.now()
            )))

        val result = controller.recentPolls(1)

        assertEquals(1, result.size)
        assertEquals(5, result[0].totalAccountsToPoll)
        assertEquals(5, result[0].accountsPolled)
        assertEquals(1, result[0].expertsWithReply.size)
    }

    @Test
    fun `corrupt JSON returns zero values`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 1))
            .thenReturn(listOf(TaskExecution(
                id = 1L,
                taskType = "AUTO_REPLY_ALL",
                triggerType = "SCHEDULED",
                status = "SUCCESS",
                requestPayload = "{}",
                resultSummary = "not valid json {{{",
                startedAt = LocalDateTime.now(),
                finishedAt = LocalDateTime.now()
            )))

        val result = controller.recentPolls(1)

        assertEquals(1, result.size)
        assertEquals(0, result[0].totalAccountsToPoll)
        assertEquals(0, result[0].accountsPolled)
    }

    @Test
    fun `manual trigger correctly marked`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 3))
            .thenReturn(listOf(
                exec("MANUAL_ALL"),
                exec("MANUAL_SELECTIVE"),
                exec("SCHEDULED")
            ))

        val result = controller.recentPolls(3)

        assertEquals(3, result.size)
        assertEquals(true, result[0].isManualTrigger)
        assertEquals(true, result[1].isManualTrigger)
        assertEquals(false, result[2].isManualTrigger)
    }

    @Test
    fun `running task does not crash`() {
        Mockito.`when`(repository.findRecentByTaskType("AUTO_REPLY_ALL", 1))
            .thenReturn(listOf(TaskExecution(
                id = 1L,
                taskType = "AUTO_REPLY_ALL",
                triggerType = "SCHEDULED",
                status = "RUNNING",
                requestPayload = "{}",
                resultSummary = null,
                startedAt = LocalDateTime.now()
            )))

        val result = controller.recentPolls(1)

        assertEquals(1, result.size)
        assertEquals("RUNNING", result[0].status)
        assertEquals(0, result[0].totalAccountsToPoll)
    }

    private fun exec(triggerType: String): TaskExecution =
        TaskExecution(
            id = 1L,
            taskType = "AUTO_REPLY_ALL",
            triggerType = triggerType,
            status = "SUCCESS",
            requestPayload = "{}",
            resultSummary = null,
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now()
        )
}
