package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(TaskExecutionController::class)
class TaskExecutionControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @Test
    fun `recent-polls route is not captured by id path variable`() {
        Mockito.`when`(taskExecutionService.listRecentPolls(10))
            .thenReturn(listOf(
                TaskExecution(
                    id = 1L,
                    taskType = "AUTO_REPLY_ALL",
                    triggerType = "SCHEDULED",
                    status = "SUCCESS",
                    requestPayload = "{}",
                    resultSummary = null,
                    startedAt = LocalDateTime.now(),
                    finishedAt = LocalDateTime.now()
                )
            ))
        Mockito.`when`(taskExecutionService.nextPollTime()).thenReturn(null)

        mockMvc.perform(get("/api/task-executions/recent-polls").param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$[0].triggerType").value("SCHEDULED"))
    }

    @Test
    fun `id route still works for numeric ids`() {
        Mockito.`when`(taskExecutionService.getExecution(42L))
            .thenReturn(
                TaskExecution(
                    id = 42L,
                    taskType = "AUTO_REPLY_ALL",
                    triggerType = "SCHEDULED",
                    status = "SUCCESS",
                    requestPayload = "{}",
                    resultSummary = null,
                    startedAt = LocalDateTime.now(),
                    finishedAt = LocalDateTime.now()
                )
            )

        mockMvc.perform(get("/api/task-executions/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
    }
}
