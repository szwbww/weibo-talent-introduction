package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(TaskProgressController::class)
class TaskProgressControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean
    private lateinit var progressLogRepository: TaskProgressLogRepository

    @MockBean
    private lateinit var taskExecutionRepository: TaskExecutionRepository

    @Test
    fun `getProgress returns progress when present`() {
        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
            .thenReturn(TaskProgress(
                taskType = "EXPERT_DISCOVERY", status = "RUNNING",
                batchNumber = 3, processedCount = 50, totalCount = 100,
                message = "批次 3", executionId = 42L
            ))

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.batchNumber").value(3))
            .andExpect(jsonPath("$.executionId").value(42))
    }

    @Test
    fun `getProgress returns 204 when absent`() {
        Mockito.`when`(progressStore.get("EXPERT_DISCOVERY"))
            .thenReturn(null)

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `cancelTask returns 200 when cancel succeeds`() {
        Mockito.`when`(progressStore.requestCancel("EXPERT_DISCOVERY"))
            .thenReturn(true)

        mockMvc.perform(post("/api/task-progress/EXPERT_DISCOVERY/cancel"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("已发送取消请求，任务将在当前批次结束后停止"))
    }

    @Test
    fun `cancelTask returns 409 when no running task`() {
        Mockito.`when`(progressStore.requestCancel("EXPERT_DISCOVERY"))
            .thenReturn(false)

        mockMvc.perform(post("/api/task-progress/EXPERT_DISCOVERY/cancel"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("没有正在运行的任务或取消请求已处理"))
    }

    @Test
    fun `getProgressLogs with executionId param returns logs`() {
        val logs = listOf(
            TaskProgressLog(
                id = 1L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 7L,
                batchNumber = 1, status = "RUNNING", processedCount = 10, totalCount = 100,
                createdAt = LocalDateTime.now()
            ),
            TaskProgressLog(
                id = 2L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 7L,
                batchNumber = 2, status = "RUNNING", processedCount = 20, totalCount = 100,
                createdAt = LocalDateTime.now()
            )
        )
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(7L))
            .thenReturn(logs)

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY/logs").param("executionId", "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].batchNumber").value(1))
            .andExpect(jsonPath("$[1].batchNumber").value(2))
    }

    @Test
    fun `getProgressLogs without executionId falls back to current executionId`() {
        val logs = listOf(
            TaskProgressLog(
                id = 1L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 5L,
                batchNumber = 1, status = "RUNNING", processedCount = 10, totalCount = 100,
                createdAt = LocalDateTime.now()
            )
        )
        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
            .thenReturn(5L)
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(5L))
            .thenReturn(logs)

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY/logs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].batchNumber").value(1))
    }

    @Test
    fun `getProgressLogs without executionId falls back to latest log executionId`() {
        val latestLog = TaskProgressLog(
            id = 10L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 8L,
            batchNumber = 3, status = "COMPLETED", processedCount = 30, totalCount = 30,
            createdAt = LocalDateTime.now()
        )
        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
            .thenReturn(null)
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_DISCOVERY"))
            .thenReturn(latestLog)
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(8L))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY/logs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `getProgressLogs returns empty when no executionId found`() {
        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_DISCOVERY"))
            .thenReturn(null)
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_DISCOVERY"))
            .thenReturn(null)

        mockMvc.perform(get("/api/task-progress/EXPERT_DISCOVERY/logs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)
    }
}
