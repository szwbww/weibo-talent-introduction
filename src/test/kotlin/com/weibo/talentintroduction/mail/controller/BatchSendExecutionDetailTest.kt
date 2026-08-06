package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendTaskConfigService
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

/**
 * I-1/I-2/I-3: execution detail must expose the full process timeline
 * (INIT/ROUND/FINAL rows, id order), structured stopReason, and live
 * running-state metrics from the latest progress log.
 */
class BatchSendExecutionDetailTest {

    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val objectMapper = ObjectMapper()

    private fun controller() = BatchSendConfigController(
        batchSendTaskConfigService = Mockito.mock(BatchSendTaskConfigService::class.java),
        templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java),
        batchSendControlService = Mockito.mock(BatchSendControlService::class.java),
        manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java),
        taskExecutionService = taskExecutionService,
        progressLogRepository = progressLogRepository,
        objectMapper = objectMapper
    )

    private fun execution(
        id: Long = 10L,
        batchConfigId: Long? = 1L,
        status: String = "SUCCESS",
        resultSummary: String? = null,
        successCount: Int = 0,
        failureCount: Int = 0
    ) = TaskExecution(
        id = id,
        taskType = "MANUAL_INITIAL_OUTREACH",
        triggerType = "MANUAL",
        status = status,
        requestPayload = null,
        resultSummary = resultSummary,
        successCount = successCount,
        failureCount = failureCount,
        startedAt = LocalDateTime.of(2026, 8, 6, 10, 0, 0),
        finishedAt = if (status == "RUNNING") null else LocalDateTime.of(2026, 8, 6, 10, 5, 0),
        batchConfigId = batchConfigId
    )

    private fun logRow(
        id: Long,
        batchNumber: Int,
        status: String = "RUNNING",
        message: String? = null,
        detailsJson: String? = null,
        errorsJson: String? = null,
        totalCount: Long = 0,
        processedCount: Long = 0
    ) = TaskProgressLog(
        id = id,
        taskType = "MANUAL_INITIAL_OUTREACH",
        taskExecutionId = 10L,
        batchNumber = batchNumber,
        status = status,
        processedCount = processedCount,
        totalCount = totalCount,
        message = message,
        detailsJson = detailsJson,
        errorsJson = errorsJson,
        createdAt = LocalDateTime.of(2026, 8, 6, 10, 0, id.toInt())
    )

    private fun stubLogs(rows: List<TaskProgressLog>) {
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(10L)).thenReturn(rows)
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(null)
    }

    @Test
    fun `INIT and FINAL rows are kept with correct classification`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubLogs(
            listOf(
                logRow(1, 0, message = "正在初始化发送队列..."),
                logRow(2, 1, message = "第1轮完成"),
                logRow(3, 1, message = "第1轮完成"),
                logRow(4, 2, message = "第2轮完成"),
                logRow(5, 0, message = "发送任务已完成")
            )
        )

        val rows = controller().getConfigExecutionDetail(1L, 10L).body!!.progressRows

        assertEquals(4, rows.size, "zero-batch rows must not be dropped")
        assertEquals(listOf("INIT", "ROUND", "ROUND", "FINAL"), rows.map { it.kind })
        assertEquals(listOf("正在初始化发送队列...", "第1轮完成", "第2轮完成", "发送任务已完成"), rows.map { it.message })
        assertEquals(listOf(1, 3, 4, 5), rows.map { it.createdAt.second }, "rows must be in id ascending order")
    }

    @Test
    fun `same batchNumber keeps only the row with the largest id`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubLogs(
            listOf(
                logRow(1, 0),
                logRow(2, 1, message = "第1轮旧进度"),
                logRow(3, 1, message = "第1轮新进度"),
                logRow(4, 0)
            )
        )

        val rows = controller().getConfigExecutionDetail(1L, 10L).body!!.progressRows

        assertEquals(listOf("INIT", "ROUND", "FINAL"), rows.map { it.kind })
        assertEquals(listOf("第1轮新进度"), rows.filter { it.kind == "ROUND" }.map { it.message })
    }

    @Test
    fun `output is ordered by id not by batchNumber`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubLogs(
            listOf(
                logRow(1, 0),
                logRow(7, 3),
                logRow(8, 1),
                logRow(9, 0)
            )
        )

        val rows = controller().getConfigExecutionDetail(1L, 10L).body!!.progressRows

        assertEquals(listOf(1, 7, 8, 9), rows.map { it.createdAt.second }, "id ascending")
        assertEquals(listOf(0, 3, 1, 0), rows.map { it.batchNumber }, "batch 3 precedes batch 1 by id order")
    }

    @Test
    fun `stopReason is read from detailsJson not message text`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubLogs(
            listOf(
                logRow(1, 0),
                logRow(2, 1, message = "第3轮完成", detailsJson = """{"stopReason":"DAILY_CAP_REACHED"}"""),
                logRow(3, 0, message = "已达到今日发送上限", detailsJson = """{}""")
            )
        )

        val rows = controller().getConfigExecutionDetail(1L, 10L).body!!.progressRows

        assertEquals("DAILY_CAP_REACHED", rows[1].stopReason)
        assertNull(rows[2].stopReason, "message text must not be reverse-parsed")
    }

    @Test
    fun `unparseable errorsJson degrades to empty list without throwing`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubLogs(
            listOf(
                logRow(1, 0),
                logRow(2, 1, errorsJson = "not-json")
            )
        )

        val rows = controller().getConfigExecutionDetail(1L, 10L).body!!.progressRows

        assertTrue(rows[1].errors.isEmpty())
    }

    @Test
    fun `RUNNING without resultSummary reads target and reasons from latest progress log`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(status = "RUNNING", successCount = 2, failureCount = 0)
        )
        val latest = logRow(
            id = 9, batchNumber = 2, totalCount = 18, processedCount = 7,
            detailsJson = """
                {"sentTotal":5,"failedTotal":2,"skippedTotal":1,"pending":10,
                 "failureReasons":{"SMTP_TIMEOUT":{"label":"SMTP 超时","count":2}},
                 "skippedReasons":{"NO_ACCOUNT":{"label":"无可用账号","count":1}}}
            """.trimIndent(),
            errorsJson = """["发送失败 (a@b.com): TIMEOUT"]"""
        )
        Mockito.`when`(progressLogRepository.findAllByTaskExecutionIdOrderByIdAsc(10L))
            .thenReturn(listOf(logRow(1, 0), logRow(5, 1), latest))
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(latest)

        val detail = controller().getConfigExecutionDetail(1L, 10L).body!!

        assertEquals(18, detail.target)
        assertEquals(5, detail.success)
        assertEquals(2, detail.failure)
        assertEquals(1, detail.skipped)
        assertEquals(10, detail.remaining)
        assertEquals("SMTP 超时", detail.failureReasons["SMTP_TIMEOUT"]?.label)
        assertEquals(2, detail.failureReasons["SMTP_TIMEOUT"]?.count)
        assertEquals("无可用账号", detail.skippedReasons["NO_ACCOUNT"]?.label)
        assertEquals(listOf("发送失败 (a@b.com): TIMEOUT"), detail.errorSamples)
    }

    @Test
    fun `resultSummary takes precedence over progress log rows`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(
                status = "SUCCESS",
                resultSummary = """
                    {"outcome":{"target":30,"success":25,"failure":2,"skipped":1,"remaining":2,
                     "failureReasons":{},"skippedReasons":{},"errorSamples":[]}}
                """.trimIndent()
            )
        )
        stubLogs(
            listOf(
                logRow(1, 0, detailsJson = """{"sentTotal":3,"failedTotal":0,"pending":99}"""),
                logRow(2, 0)
            )
        )

        val detail = controller().getConfigExecutionDetail(1L, 10L).body!!

        assertEquals(30, detail.target)
        assertEquals(25, detail.success)
        Mockito.verify(progressLogRepository, Mockito.never())
            .findTopByTaskExecutionIdOrderByIdDesc(Mockito.anyLong())
    }

    @Test
    fun `execution of another config still returns 404`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(batchConfigId = 2L))

        val response = controller().getConfigExecutionDetail(1L, 10L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        Mockito.verify(progressLogRepository, Mockito.never())
            .findAllByTaskExecutionIdOrderByIdAsc(Mockito.anyLong())
    }
}
