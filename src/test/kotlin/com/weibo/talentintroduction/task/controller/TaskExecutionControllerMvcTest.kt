package com.weibo.talentintroduction.task.controller

import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor
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

    @MockBean
    private lateinit var taskExecutionRepository: TaskExecutionRepository

    @MockBean
    private lateinit var taskExecutionSummaryExtractor: TaskExecutionSummaryExtractor

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

    @Test
    fun `poll detail returns accounts and experts`() {
        val summary = """{"accountCount":2,"accounts":[{"accountCode":"a1","status":"SUCCESS","fetched":3,"recorded":3,"replied":2,"manualReview":0,"repliedExperts":[{"expertContactId":1,"expertEmail":"e@test.com","expertName":"Name","outcome":"QA_REPLIED"}]},{"accountCode":"a2","status":"FAILED","fetched":0,"recorded":0,"replied":0,"manualReview":0,"errorMessage":"timeout","repliedExperts":[]}]}"""
        Mockito.`when`(taskExecutionService.getExecution(1L))
            .thenReturn(TaskExecution(
                id = 1L, taskType = "AUTO_REPLY_ALL", triggerType = "SCHEDULED",
                status = "SUCCESS", requestPayload = "{}", resultSummary = summary,
                startedAt = LocalDateTime.now(), finishedAt = LocalDateTime.now()
            ))

        mockMvc.perform(get("/api/task-executions/recent-polls/1/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.accounts[0].accountCode").value("a1"))
            .andExpect(jsonPath("$.accounts[0].repliedExperts[0].expertEmail").value("e@test.com"))
            .andExpect(jsonPath("$.accounts[1].status").value("FAILED"))
            .andExpect(jsonPath("$.accounts[1].errorMessage").value("timeout"))
    }

    @Test
    fun `poll detail rejects non poll execution`() {
        Mockito.`when`(taskExecutionService.getExecution(1L))
            .thenReturn(TaskExecution(
                id = 1L, taskType = "INITIAL_OUTREACH", triggerType = "MANUAL",
                status = "SUCCESS", requestPayload = "{}", resultSummary = null,
                startedAt = LocalDateTime.now()
            ))

        mockMvc.perform(get("/api/task-executions/recent-polls/1/detail"))
            .andExpect(status().is4xxClientError)
    }

    @Test
    fun `poll detail null summary returns empty accounts`() {
        Mockito.`when`(taskExecutionService.getExecution(1L))
            .thenReturn(TaskExecution(
                id = 1L, taskType = "AUTO_REPLY_ALL", triggerType = "SCHEDULED",
                status = "SUCCESS", requestPayload = "{}", resultSummary = null,
                startedAt = LocalDateTime.now()
            ))

        mockMvc.perform(get("/api/task-executions/recent-polls/1/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accounts").isEmpty)
    }

    @Test
    fun `poll detail corrupt JSON returns error`() {
        Mockito.`when`(taskExecutionService.getExecution(1L))
            .thenReturn(TaskExecution(
                id = 1L, taskType = "AUTO_REPLY_ALL", triggerType = "SCHEDULED",
                status = "SUCCESS", requestPayload = "{}", resultSummary = "{{{bad",
                startedAt = LocalDateTime.now()
            ))

        mockMvc.perform(get("/api/task-executions/recent-polls/1/detail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accounts").isEmpty)
            .andExpect(jsonPath("$.error").isNotEmpty)
    }
}
