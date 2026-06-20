package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class TaskProgressControllerExecutionsTest {

    private val progressStore = Mockito.mock(TaskProgressStore::class.java)
    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val taskExecutionRepository = Mockito.mock(TaskExecutionRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val controller = TaskProgressController(
        progressStore, progressLogRepository, taskExecutionRepository, objectMapper
    )

    private fun execution(
        id: Long,
        taskType: String,
        status: String,
        resultSummary: String?,
        triggerType: String = "MANUAL",
        startedAt: LocalDateTime = LocalDateTime.of(2026, 6, 10, 10, 0),
        finishedAt: LocalDateTime? = LocalDateTime.of(2026, 6, 10, 10, 5),
        errorMessage: String? = null
    ) = TaskExecution(
        id = id, taskType = taskType, triggerType = triggerType, status = status,
        resultSummary = resultSummary, requestPayload = null, startedAt = startedAt, finishedAt = finishedAt,
        errorMessage = errorMessage
    )

    @Test
    fun `revalidation resultSummary maps total passed demoted correctly`() {
        val summary = """{"stats":{"total":100,"passed":80,"demoted":15,"demotionFailed":5},"wasCancelled":false}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(1, "EXPERT_REVALIDATION", "SUCCESS", summary)))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        assertEquals(HttpStatus.OK, response.statusCode)
        val list = response.body!!
        assertEquals(1, list.size)
        assertEquals(100L, list[0].totalProcessed)
        assertEquals(80L, list[0].totalPassed)
        assertEquals(15L, list[0].totalRejected)
        assertEquals("SUCCESS", list[0].status)
    }

    @Test
    fun `wasCancelled true maps status to CANCELLED`() {
        val summary = """{"stats":{"total":50,"passed":30,"demoted":10},"wasCancelled":true}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(2, "EXPERT_REVALIDATION", "SUCCESS", summary)))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals("CANCELLED", list[0].status)
    }

    @Test
    fun `resultSummary null falls back to latest progress log`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(3, "EXPERT_REVALIDATION", "RUNNING", null)))

        val log = TaskProgressLog(
            id = 10L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 3L,
            batchNumber = 5, status = "RUNNING", processedCount = 60, totalCount = 100,
            detailsJson = """{"passed":50,"demoted":10}"""
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(3L))
            .thenReturn(log)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(60L, list[0].totalProcessed)
        assertEquals(50L, list[0].totalPassed)
        assertEquals(10L, list[0].totalRejected)
    }

    @Test
    fun `resultSummary null and no progress log returns zeros`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(4, "EXPERT_REVALIDATION", "FAILED", null)))

        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(4L))
            .thenReturn(null)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(0L, list[0].totalProcessed)
        assertEquals(0L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }

    @Test
    fun `malformed JSON resultSummary returns zero totals without log fallback`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(5, "EXPERT_REVALIDATION", "SUCCESS", "{bad json")))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(0L, list[0].totalProcessed)
        assertEquals(0L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
        // Verify progressLogRepository was never queried for this executionId
        Mockito.verify(progressLogRepository, Mockito.never())
            .findTopByTaskExecutionIdOrderByIdDesc(Mockito.anyLong())
    }

    @Test
    fun `empty JSON object resultSummary returns zero totals`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(15, "EXPERT_REVALIDATION", "SUCCESS", "{}")))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(0L, list[0].totalProcessed)
        assertEquals(0L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }

    @Test
    fun `resultSummary with empty stats returns zero totals`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(16, "EXPERT_REVALIDATION", "SUCCESS", """{"stats":{}}""")))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(0L, list[0].totalProcessed)
        assertEquals(0L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }

    @Test
    fun `null resultSummary falls back to log`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(17, "EXPERT_REVALIDATION", "SUCCESS", null)))

        val log = TaskProgressLog(
            id = 20L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 17L,
            batchNumber = -1, status = "COMPLETED", processedCount = 100, totalCount = 100,
            detailsJson = null
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(17L))
            .thenReturn(log)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(100L, list[0].totalProcessed)
    }

    @Test
    fun `blank resultSummary falls back to log`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(18, "EXPERT_REVALIDATION", "SUCCESS", "")))

        val log = TaskProgressLog(
            id = 21L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 18L,
            batchNumber = -1, status = "COMPLETED", processedCount = 50, totalCount = 50,
            detailsJson = null
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(18L))
            .thenReturn(log)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(50L, list[0].totalProcessed)
    }

    @Test
    fun `illegal JSON with wasCancelled keeps taskExecution original status`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(19, "EXPERT_REVALIDATION", "SUCCESS", "{bad json")))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals("SUCCESS", list[0].status)
    }

    @Test
    fun `limit 0 clamped to 1`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 1))
            .thenReturn(emptyList())

        val response = controller.getExecutions("EXPERT_REVALIDATION", 0)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `limit 51 clamped to 50`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 50))
            .thenReturn(emptyList())

        val response = controller.getExecutions("EXPERT_REVALIDATION", 51)
        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `taskType not in whitelist returns 400`() {
        val response = controller.getExecutions("AUTO_REPLY_ALL", 10)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `promotion scan resultSummary maps total promoted filtered emailRejected`() {
        val summary = """{"stats":{"total":200,"promoted":150,"filtered":30,"emailRejected":10,"promotionFailed":5},"wasCancelled":false}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("RAW_PROMOTION_SCAN", 10))
            .thenReturn(listOf(execution(6, "RAW_PROMOTION_SCAN", "SUCCESS", summary)))

        val response = controller.getExecutions("RAW_PROMOTION_SCAN", 10)
        val list = response.body!!
        assertEquals(200L, list[0].totalProcessed)
        assertEquals(150L, list[0].totalPassed)
        assertEquals(40L, list[0].totalRejected) // filtered + emailRejected
    }

    @Test
    fun `discovery resultSummary maps totalPapers indexed summaryText`() {
        val summary = """{"stats":{"totalPapers":500,"indexed":400,"promoted":350},"summaryText":"完成: 论文 500, 收录 400, 晋升 350","wasCancelled":false}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_DISCOVERY", 10))
            .thenReturn(listOf(execution(7, "EXPERT_DISCOVERY", "SUCCESS", summary)))

        val response = controller.getExecutions("EXPERT_DISCOVERY", 10)
        val list = response.body!!
        assertEquals(500L, list[0].totalProcessed)
        assertEquals(400L, list[0].totalPassed)
        assertEquals(100L, list[0].totalRejected)
        assertEquals("完成: 论文 500, 收录 400, 晋升 350", list[0].summaryText)
    }

    @Test
    fun `discovery fallback from log parses indexed from detailsJson`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_DISCOVERY", 10))
            .thenReturn(listOf(execution(8, "EXPERT_DISCOVERY", "RUNNING", null)))

        val log = TaskProgressLog(
            id = 12L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 8L,
            batchNumber = 3, status = "RUNNING", processedCount = 300, totalCount = 500,
            detailsJson = """{"indexed":200,"promoted":180}"""
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(8L))
            .thenReturn(log)

        val response = controller.getExecutions("EXPERT_DISCOVERY", 10)
        val list = response.body!!
        assertEquals(300L, list[0].totalProcessed)
        assertEquals(200L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected) // discovery rejected = 0 from fallback
    }

    @Test
    fun `RUNNING execution has null durationSeconds`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(9, "EXPERT_REVALIDATION", "RUNNING", null, finishedAt = null)))

        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(9L))
            .thenReturn(null)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(null, list[0].durationSeconds)
        assertEquals(null, list[0].finishedAt)
    }

    @Test
    fun `batchOnly filters out batchNumber zero and negative`() {
        val logs = listOf(
            TaskProgressLog(id = 1L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = 0, status = "RUNNING", processedCount = 0, totalCount = 100),
            TaskProgressLog(id = 2L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = 1, status = "RUNNING", processedCount = 10, totalCount = 100, batchProcessed = 10),
            TaskProgressLog(id = 3L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = -1, status = "COMPLETED", processedCount = 100, totalCount = 100),
            TaskProgressLog(id = 4L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = 2, status = "RUNNING", processedCount = 20, totalCount = 100, batchProcessed = 10)
        )
        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_REVALIDATION"))
            .thenReturn(null)
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_REVALIDATION"))
            .thenReturn(logs.last())
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(1L))
            .thenReturn(logs)

        val result = controller.getProgressLogs("EXPERT_REVALIDATION", null, batchOnly = true)
        assertEquals(2, result.size)
        assertTrue(result.all { it.batchNumber > 0 })
        assertEquals(1, result[0].batchNumber)
        assertEquals(2, result[1].batchNumber)
    }

    @Test
    fun `batchOnly false returns all logs including batchNumber zero and negative`() {
        val logs = listOf(
            TaskProgressLog(id = 1L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = 0, status = "RUNNING", processedCount = 0, totalCount = 100),
            TaskProgressLog(id = 2L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 1L,
                batchNumber = 1, status = "RUNNING", processedCount = 10, totalCount = 100)
        )
        Mockito.`when`(progressStore.getCurrentExecutionId("EXPERT_REVALIDATION"))
            .thenReturn(null)
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_REVALIDATION"))
            .thenReturn(logs.last())
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(1L))
            .thenReturn(logs)

        val result = controller.getProgressLogs("EXPERT_REVALIDATION", null, batchOnly = false)
        assertEquals(2, result.size)
    }

    @Test
    fun `durationSeconds computed correctly`() {
        val summary = """{"stats":{"total":100,"passed":80,"demoted":20},"wasCancelled":false}"""
        val start = LocalDateTime.of(2026, 6, 10, 10, 0, 0)
        val finish = LocalDateTime.of(2026, 6, 10, 10, 5, 30)
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(10, "EXPERT_REVALIDATION", "SUCCESS", summary, startedAt = start, finishedAt = finish)))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        val list = response.body!!
        assertEquals(330L, list[0].durationSeconds)
    }

    @Test
    fun `triggerType is passed through`() {
        val summary = """{"stats":{"total":1,"passed":1,"demoted":0},"wasCancelled":false}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(11, "EXPERT_REVALIDATION", "SUCCESS", summary, triggerType = "SCHEDULED")))

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        assertEquals("SCHEDULED", response.body!![0].triggerType)
    }

    @Test
    fun `errorMessage is passed through`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("EXPERT_REVALIDATION", 10))
            .thenReturn(listOf(execution(12, "EXPERT_REVALIDATION", "FAILED", null, errorMessage = "Connection refused")))

        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(12L))
            .thenReturn(null)

        val response = controller.getExecutions("EXPERT_REVALIDATION", 10)
        assertEquals("Connection refused", response.body!![0].errorMessage)
    }

    @Test
    fun `outreach resultSummary maps sent failed processed from flat fields`() {
        val summary =
            """{"total":84079,"sent":2,"failed":0,"skippedNoAccount":0,"wasCancelled":false,"finalStatus":"COMPLETED","stopReason":null,"remaining":84077}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("MANUAL_INITIAL_OUTREACH", 10))
            .thenReturn(listOf(execution(20, "MANUAL_INITIAL_OUTREACH", "SUCCESS", summary)))

        val response = controller.getExecutions("MANUAL_INITIAL_OUTREACH", 10)
        val list = response.body!!
        assertEquals(2L, list[0].totalProcessed)
        assertEquals(2L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }

    @Test
    fun `outreach with failures maps rejected`() {
        val summary =
            """{"total":10,"sent":3,"failed":2,"skippedNoAccount":0,"wasCancelled":false,"finalStatus":"COMPLETED","stopReason":null,"remaining":5}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("MANUAL_INITIAL_OUTREACH", 10))
            .thenReturn(listOf(execution(21, "MANUAL_INITIAL_OUTREACH", "SUCCESS", summary)))

        val response = controller.getExecutions("MANUAL_INITIAL_OUTREACH", 10)
        val list = response.body!!
        assertEquals(5L, list[0].totalProcessed)
        assertEquals(3L, list[0].totalPassed)
        assertEquals(2L, list[0].totalRejected)
    }

    @Test
    fun `outreach empty snapshot yields zeros without negative`() {
        val summary = """{"total":0,"sent":0,"failed":0,"remaining":0,"finalStatus":"COMPLETED"}"""
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("MANUAL_INITIAL_OUTREACH", 10))
            .thenReturn(listOf(execution(22, "MANUAL_INITIAL_OUTREACH", "SUCCESS", summary)))

        val response = controller.getExecutions("MANUAL_INITIAL_OUTREACH", 10)
        val list = response.body!!
        assertEquals(0L, list[0].totalProcessed)
        assertEquals(0L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }

    @Test
    fun `outreach blank resultSummary falls back to log`() {
        Mockito.`when`(taskExecutionRepository.findRecentByTaskType("MANUAL_INITIAL_OUTREACH", 10))
            .thenReturn(listOf(execution(23, "MANUAL_INITIAL_OUTREACH", "SUCCESS", "")))

        val log = TaskProgressLog(
            id = 30L, taskType = "MANUAL_INITIAL_OUTREACH", taskExecutionId = 23L,
            batchNumber = 1, status = "COMPLETED", processedCount = 1, totalCount = 100,
            detailsJson = """{"sent":1,"failed":0}"""
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(23L))
            .thenReturn(log)

        val response = controller.getExecutions("MANUAL_INITIAL_OUTREACH", 10)
        val list = response.body!!
        assertEquals(1L, list[0].totalProcessed)
        assertEquals(1L, list[0].totalPassed)
        assertEquals(0L, list[0].totalRejected)
    }
}
