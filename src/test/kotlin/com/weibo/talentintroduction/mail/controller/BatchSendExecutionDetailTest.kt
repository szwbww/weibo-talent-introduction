package com.weibo.talentintroduction.mail.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.BatchExecutionMetadata
import com.weibo.talentintroduction.campaign.service.ManualOutreachResult
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationAggregate
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationPage
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRepository
import com.weibo.talentintroduction.campaign.repository.BatchEmailVerificationRow
import com.weibo.talentintroduction.campaign.service.BatchSendControlService
import com.weibo.talentintroduction.campaign.service.BatchSendTaskConfigService
import com.weibo.talentintroduction.campaign.service.ExecutionLiveView
import com.weibo.talentintroduction.campaign.service.ManualInitialOutreachService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.template.repository.MailComposeTemplateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
    private val batchSendControlService = Mockito.mock(BatchSendControlService::class.java)
    private val emailVerificationRepository = Mockito.mock(BatchEmailVerificationRepository::class.java)
    private val objectMapper = ObjectMapper()

    /** 序列化断言用：与 Spring Boot 运行时一致地注册 JavaTimeModule（LocalDateTime 列）。 */
    private val jsonMapper = ObjectMapper()
        .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())

    private fun controller() = BatchSendConfigController(
        batchSendTaskConfigService = Mockito.mock(BatchSendTaskConfigService::class.java),
        templateRepository = Mockito.mock(MailComposeTemplateRepository::class.java),
        batchSendControlService = batchSendControlService,
        manualInitialOutreachService = Mockito.mock(ManualInitialOutreachService::class.java),
        taskExecutionService = taskExecutionService,
        progressLogRepository = progressLogRepository,
        objectMapper = objectMapper,
        batchEmailVerificationRepository = emailVerificationRepository
    )

    private fun execution(
        id: Long = 10L,
        batchConfigId: Long? = 1L,
        status: String = "SUCCESS",
        resultSummary: String? = null,
        successCount: Int = 0,
        failureCount: Int = 0,
        taskType: String = "MANUAL_INITIAL_OUTREACH",
        requestPayload: String? = null
    ) = TaskExecution(
        id = id,
        taskType = taskType,
        triggerType = "MANUAL",
        status = status,
        requestPayload = requestPayload,
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

    @Test
    fun `execution level endpoint returns detail without config ownership check`() {
        // batchConfigId belongs to config 2 while the path has no config id at all
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(batchConfigId = 2L))
        stubLogs(
            listOf(
                logRow(1, 0, message = "正在初始化发送队列..."),
                logRow(2, 1, message = "第1轮完成"),
                logRow(3, 0, message = "发送任务已完成")
            )
        )

        val detail = controller().getExecutionDetail(10L).body!!

        assertEquals(10L, detail.executionId)
        assertEquals(listOf("INIT", "ROUND", "FINAL"), detail.progressRows.map { it.kind })
    }

    @Test
    fun `execution level endpoint accepts batchConfigId null independent run`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(batchConfigId = null))
        stubLogs(emptyList())

        val response = controller().getExecutionDetail(10L)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `execution level endpoint returns 404 for non manual task type`() {
        Mockito.`when`(taskExecutionService.getExecution(10L))
            .thenReturn(execution(taskType = "EXPERT_DISCOVERY"))

        val response = controller().getExecutionDetail(10L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        Mockito.verify(progressLogRepository, Mockito.never())
            .findAllByTaskExecutionIdOrderByIdAsc(Mockito.anyLong())
    }

    @Test
    fun `live is populated only when service reports the current execution`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(status = "RUNNING"))
        stubLogs(listOf(logRow(1, 0)))
        Mockito.`when`(batchSendControlService.getLiveExecutionView(10L)).thenReturn(
            ExecutionLiveView(
                status = "RUNNING",
                message = "正在发送：a@b.edu",
                roundNumber = 1,
                processedCount = 7,
                totalCount = 120,
                percentage = 5,
                accounts = emptyList(),
                cancellable = true
            )
        )

        val detail = controller().getExecutionDetail(10L).body!!

        assertEquals("RUNNING", detail.live!!.status)
        assertEquals(7, detail.live!!.processedCount)
        assertEquals(120, detail.live!!.totalCount)
        assertEquals(5, detail.live!!.percentage)
    }

    @Test
    fun `RUNNING status without live view leaves live null`() {
        // execution.status == "RUNNING" but getCurrentExecutionId does not match
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(status = "RUNNING"))
        stubLogs(listOf(logRow(1, 0)))
        Mockito.`when`(batchSendControlService.getLiveExecutionView(10L)).thenReturn(null)

        val detail = controller().getConfigExecutionDetail(1L, 10L).body!!

        assertNull(detail.live, "stale RUNNING row must not produce a live block")
    }

    @Test
    fun `execution level cancel delegates to service`() {
        Mockito.`when`(batchSendControlService.cancelExecution(10L)).thenReturn(
            org.springframework.http.ResponseEntity.ok(mapOf("message" to "已发送取消请求，将在当前批次结束后停止"))
        )

        val response = controller().cancelExecution(10L)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("已发送取消请求，将在当前批次结束后停止", response.body!!["message"])
        Mockito.verify(batchSendControlService).cancelExecution(10L)
    }

    @Test
    fun `list all executions returns config and independent rows with echo batchConfigId`() {
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 50))
            .thenReturn(
                listOf(
                    execution(id = 10L, batchConfigId = 1L),
                    execution(id = 11L, batchConfigId = null)
                )
            )

        val body = controller().listAllExecutions(50).body!!

        assertEquals(2, body.size)
        assertEquals(1L, body[0].batchConfigId, "config-sourced row keeps its config id")
        assertNull(body[1].batchConfigId, "independent run must appear with null batchConfigId")
        Mockito.verify(taskExecutionService).listRecentByTaskType(BatchSendControlService.TASK_TYPE, 50)
    }

    @Test
    fun `list all executions clamps limit 0 to 1`() {
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 1))
            .thenReturn(emptyList())

        val response = controller().listAllExecutions(0)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList<Any>(), response.body)
        Mockito.verify(taskExecutionService).listRecentByTaskType(BatchSendControlService.TASK_TYPE, 1)
    }

    @Test
    fun `list all executions clamps limit 500 to 200`() {
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 200))
            .thenReturn(emptyList())

        val response = controller().listAllExecutions(500)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList<Any>(), response.body)
        Mockito.verify(taskExecutionService).listRecentByTaskType(BatchSendControlService.TASK_TYPE, 200)
    }

    private fun demandExecutionJson(
        planned: Long = 30,
        quota: Long = 10,
        scanned: Long = 50,
        exhausted: Boolean = true
    ) = """{"version":2,"plannedSuccess":$planned,"remainingSuccessQuota":$quota,"scanned":$scanned,"sourceExhausted":$exhausted}"""

    private fun resultWithExecution(metadata: String, success: Int = 20, skipped: Int = 3) =
        """{"execution":$metadata,"outcome":{"target":${success + 2 + skipped},"success":$success,"failure":2,"skipped":$skipped,"remaining":0,
           "failureReasons":{"SMTP_TIMEOUT":{"label":"SMTP 超时","count":2}},
           "skippedReasons":{"FILTERED":{"label":"条件排除","count":$skipped}},"errorSamples":["fixture timeout"]}}"""

    @Test
    fun `v2 latest progress and final result expose identical counters through both detail endpoints`() {
        val metadata = demandExecutionJson()
        val latest = logRow(9, 1, totalCount = 0, processedCount = 25,
            detailsJson = """{"execution":$metadata,"sentTotal":20,"failedTotal":2,"skippedTotal":3,"pending":999,
                "failureReasons":{"SMTP_TIMEOUT":{"label":"SMTP 超时","count":2}},
                "skippedReasons":{"FILTERED":{"label":"条件排除","count":3}}}""",
            errorsJson = """["fixture timeout"]""")
        stubLogs(listOf(logRow(1, 0), latest))
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(latest)
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(status = "RUNNING"))
        val c = controller()
        val running = c.getConfigExecutionDetail(1L, 10L).body!!
        assertEquals(25, running.target, "unknown generic totalCount must not become the v2 target")
        assertEquals(0, running.remaining, "quota and legacy population remaining must not be mixed")
        assertEquals(BatchExecutionMetadata(30, 10, 50, true), running.execution)
        assertEquals(running.execution, c.getExecutionDetail(10L).body!!.execution)
        assertEquals(listOf("INIT", "ROUND"), running.progressRows.map { it.kind })

        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(resultSummary = resultWithExecution(metadata)))
        val completed = c.getExecutionDetail(10L).body!!
        assertEquals(running.target, completed.target)
        assertEquals(running.success, completed.success)
        assertEquals(running.failure, completed.failure)
        assertEquals(running.skipped, completed.skipped)
        assertEquals(running.remaining, completed.remaining)
        assertEquals(running.execution, completed.execution)
        assertEquals(running.failureReasons, completed.failureReasons)
        assertEquals(running.skippedReasons, completed.skippedReasons)
        assertEquals(running.errorSamples, completed.errorSamples)
        assertEquals(completed.execution, c.getConfigExecutionDetail(1L, 10L).body!!.execution)
    }

    @Test
    fun `v2 completed summary exposes actual49 and zero quota without reading progress`() {
        val row = execution(resultSummary = resultWithExecution(demandExecutionJson(quota = 0, scanned = 80, exhausted = false),
            success = 30, skipped = 17))
        Mockito.`when`(taskExecutionService.listRecentByBatchConfigId(1L, 50)).thenReturn(listOf(row))
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 50)).thenReturn(listOf(row))
        val c = controller()
        val summary = c.listConfigExecutions(1L, 50).body!!.single()
        assertEquals(49, summary.target)
        assertEquals(30, summary.success)
        assertEquals(2, summary.failure)
        assertEquals(17, summary.skipped)
        assertEquals(0, summary.remaining)
        assertEquals(0L, summary.execution!!.remainingSuccessQuota)
        assertEquals(summary.execution, c.listAllExecutions(50).body!!.single().execution)
        Mockito.verifyNoInteractions(progressLogRepository)
    }

    @Test
    fun `RUNNING list does not perform per execution progress lookups`() {
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 50))
            .thenReturn(listOf(execution(status = "RUNNING")))
        val summary = controller().listAllExecutions(50).body!!.single()
        assertNull(summary.execution)
        Mockito.verifyNoInteractions(progressLogRepository)
    }

    @Test
    fun `historical1757 remains unchanged in summary and both detail readers`() {
        val row = execution(resultSummary = """{"outcome":{"target":1757,"success":30,"failure":0,"skipped":17,"remaining":1710}}""")
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(row)
        Mockito.`when`(taskExecutionService.listRecentByTaskType(BatchSendControlService.TASK_TYPE, 50)).thenReturn(listOf(row))
        stubLogs(emptyList())
        val c = controller()
        val summary = c.listAllExecutions(50).body!!.single()
        assertEquals(listOf(1757, 30, 0, 17, 1710),
            listOf(summary.target, summary.success, summary.failure, summary.skipped, summary.remaining))
        assertNull(summary.execution)
        listOf(c.getExecutionDetail(10L).body!!, c.getConfigExecutionDetail(1L, 10L).body!!).forEach {
            assertEquals(listOf(1757, 30, 0, 17, 1710), listOf(it.target, it.success, it.failure, it.skipped, it.remaining))
            assertNull(it.execution)
        }
    }

    @Test
    fun `unknown incomplete negative coercible and overflow protocols preserve existing outcome in both readers`() {
        val valid = demandExecutionJson()
        val invalid = listOf(
            "null", "{}", "[]",
            valid.replace("\"version\":2", "\"version\":3"),
            valid.replace("\"version\":2", "\"version\":2.0"),
            valid.replace("\"version\":2", "\"version\":\"2\""),
            valid.replace("\"scanned\":50,", ""),
            valid.replace("\"scanned\":50", "\"scanned\":-1"),
            valid.replace("\"plannedSuccess\":30", "\"plannedSuccess\":30.5"),
            valid.replace("\"plannedSuccess\":30", "\"plannedSuccess\":\"30\""),
            valid.replace("\"remainingSuccessQuota\":10", "\"remainingSuccessQuota\":-1"),
            valid.replace("\"plannedSuccess\":30", "\"plannedSuccess\":9223372036854775808"),
            valid.replace("\"sourceExhausted\":true", "\"sourceExhausted\":\"true\"")
        )
        val c = controller()
        invalid.forEach { metadata ->
            Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
                execution(resultSummary = resultWithExecution(metadata)))
            stubLogs(emptyList())
            val final = c.getExecutionDetail(10L).body!!
            assertNull(final.execution, metadata)
            assertEquals(25, final.target, metadata)
            assertEquals(20, final.success, metadata)
            assertEquals("SMTP 超时", final.failureReasons["SMTP_TIMEOUT"]?.label)
            assertEquals(listOf("fixture timeout"), final.errorSamples)

            val latest = logRow(9, 1, totalCount = 1757,
                detailsJson = """{"execution":$metadata,"sentTotal":30,"failedTotal":0,"skippedTotal":17,"pending":1710}""")
            Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(status = "RUNNING"))
            Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(latest)
            val running = c.getExecutionDetail(10L).body!!
            assertNull(running.execution, metadata)
            assertEquals(1757, running.target, metadata)
            assertEquals(1710, running.remaining, metadata)
        }
    }

    @Test
    fun `bad JSON result and progress degrade without manufacturing protocol`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(status = "RUNNING", resultSummary = "{", successCount = 7, failureCount = 2))
        stubLogs(emptyList())
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L))
            .thenReturn(logRow(9, 1, detailsJson = "{"))
        val detail = controller().getExecutionDetail(10L).body!!
        assertNull(detail.execution)
        assertEquals(7, detail.success)
        assertEquals(2, detail.failure)
    }

    @Test
    fun `metadata uses Long and adds only one nested result or progress field while legacy result omits it`() {
        val legacy = ManualOutreachResult(0, 0, 0, 0, false)
        val legacyNode = objectMapper.readTree(objectMapper.writeValueAsString(legacy))
        assertFalse(legacyNode.has("execution"), "existing producers must not write even a null protocol")
        val planned = 50_000L * 50_000L
        val metadata = BatchExecutionMetadata(planned, planned - 20, 50, true)
        val result = objectMapper.readTree(objectMapper.writeValueAsString(legacy.copy(execution = metadata)))
        assertEquals(legacyNode.fieldNames().asSequence().toSet() + "execution", result.fieldNames().asSequence().toSet())
        assertEquals(setOf("version", "plannedSuccess", "remainingSuccessQuota", "scanned", "sourceExhausted"),
            result.path("execution").fieldNames().asSequence().toSet())
        assertEquals(2_500_000_000L, result.path("execution").path("plannedSuccess").longValue())
        val details = objectMapper.readTree(objectMapper.writeValueAsString(mapOf("sentTotal" to 20)))
        val progress = objectMapper.readTree(objectMapper.writeValueAsString(mapOf("sentTotal" to 20, "execution" to metadata)))
        assertEquals(details.fieldNames().asSequence().toSet() + "execution", progress.fieldNames().asSequence().toSet())
        assertEquals(result.path("execution"), progress.path("execution"))
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(
            resultSummary = resultWithExecution(objectMapper.writeValueAsString(metadata))))
        stubLogs(emptyList())
        assertEquals(planned, controller().getExecutionDetail(10L).body!!.execution!!.plannedSuccess)
    }

    // ── 03 T1: per-email verification detail (read-only) ───────────────────────────

    private fun verificationRow(
        id: Long,
        decision: String = "PASS",
        providerState: String? = "deliverable",
        providerReason: String? = null,
        errorCode: String? = null,
        sendStatus: String = "SENT",
        sendReason: String? = null,
        tagStatus: String = "NOT_REQUIRED",
        tagError: String? = null,
        requestCount: Int = 1,
        checkedAt: LocalDateTime? = LocalDateTime.of(2026, 8, 6, 10, 0, 5)
    ) = BatchEmailVerificationRow(
        id = id,
        taskExecutionId = 10L,
        expertDocId = "doc-$id",
        orcidId = "0000-0002-1825-009$id",
        expertName = "专家$id",
        email = "expert$id@university.edu",
        decision = decision,
        providerState = providerState,
        providerReason = providerReason,
        errorCode = errorCode,
        requestCount = requestCount,
        checkedAt = checkedAt,
        sendStatus = sendStatus,
        sendReason = sendReason,
        tagStatus = tagStatus,
        tagError = tagError,
        createdAt = LocalDateTime.of(2026, 8, 6, 10, 0, 0),
        updatedAt = LocalDateTime.of(2026, 8, 6, 10, 0, 6)
    )

    private fun verificationPage(
        rows: List<BatchEmailVerificationRow>,
        hasMore: Boolean = false,
        aggregate: BatchEmailVerificationAggregate = BatchEmailVerificationAggregate.EMPTY
    ) = BatchEmailVerificationPage(rows = rows, hasMore = hasMore, aggregate = aggregate)

    private fun stubAnyVerificationPage(page: BatchEmailVerificationPage) {
        Mockito.`when`(
            emailVerificationRepository.readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
        ).thenReturn(page)
    }

    @Test
    fun `verification detail is 404 for a non manual-initial-outreach execution`() {
        Mockito.`when`(taskExecutionService.getExecution(10L))
            .thenReturn(execution(taskType = "EXPERT_DISCOVERY"))

        val response = controller().getExecutionEmailVerifications(10L, 0L, 50, null)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        Mockito.verify(emailVerificationRepository, Mockito.never())
            .readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test
    fun `verification detail is 404 for an unknown execution`() {
        Mockito.`when`(taskExecutionService.getExecution(99L))
            .thenThrow(IllegalStateException("Task execution not found: 99"))

        val response = controller().getExecutionEmailVerifications(99L, 0L, 50, null)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        Mockito.verify(emailVerificationRepository, Mockito.never())
            .readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test
    fun `verification detail is 404 when configId does not own the execution`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(batchConfigId = 1L))

        val response = controller().getExecutionEmailVerifications(10L, 0L, 50, 2L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        Mockito.verify(emailVerificationRepository, Mockito.never())
            .readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test
    fun `verification detail accepts the matching configId and reads that page`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution(batchConfigId = 1L))
        Mockito.`when`(emailVerificationRepository.readPage(10L, 0L, 50))
            .thenReturn(verificationPage(listOf(verificationRow(1L))))

        val body = controller().getExecutionEmailVerifications(10L, 0L, 50, 1L).body!!

        assertEquals(10L, body.executionId)
        assertEquals(1, body.items.size)
        Mockito.verify(emailVerificationRepository).readPage(10L, 0L, 50)
    }

    @Test
    fun `verification detail rejects a negative cursor and an out-of-range limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            controller().getExecutionEmailVerifications(10L, -1L, 50, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller().getExecutionEmailVerifications(10L, 0L, 101, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            controller().getExecutionEmailVerifications(10L, 0L, 0, null)
        }
        Mockito.verify(emailVerificationRepository, Mockito.never())
            .readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test
    fun `enabled comes from the saved request snapshot not the current config`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(
                requestPayload = """
                    {"sourceConfigId":1,"sourceUpdatedAt":"2026-08-06T10:00:00",
                     "snapshot":{"mailType":"INTRODUCTION","emailVerificationEnabled":true,"roundSize":5}}
                """.trimIndent()
            )
        )
        stubAnyVerificationPage(verificationPage(emptyList()))

        val enabled = controller().getExecutionEmailVerifications(10L, 0L, 50, null).body!!.enabled

        assertTrue(enabled, "the switch must follow the execution snapshot")
        Mockito.verify(taskExecutionService).getExecution(10L)
    }

    @Test
    fun `legacy payload without the field and a missing payload both read as disabled`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(
            execution(
                requestPayload = """{"legacySendType":"INTRODUCTION","snapshot":{"mailType":"INTRODUCTION"}}"""
            )
        )
        Mockito.`when`(taskExecutionService.getExecution(11L))
            .thenReturn(execution(id = 11L, requestPayload = null))
        stubAnyVerificationPage(verificationPage(emptyList()))

        assertFalse(controller().getExecutionEmailVerifications(10L, 0L, 50, null).body!!.enabled)
        assertFalse(controller().getExecutionEmailVerifications(11L, 0L, 50, null).body!!.enabled)
    }

    @Test
    fun `unreadable request snapshot fails loudly instead of reporting disabled`() {
        Mockito.`when`(taskExecutionService.getExecution(10L))
            .thenReturn(execution(requestPayload = "{not json"))

        val failure = assertThrows(IllegalStateException::class.java) {
            controller().getExecutionEmailVerifications(10L, 0L, 50, null)
        }

        assertTrue(
            failure.message!!.contains("快照"),
            "a broken historical snapshot must be named, never shown as 'not enabled'"
        )
        Mockito.verify(emailVerificationRepository, Mockito.never())
            .readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
    }

    @Test
    fun `paging cursor is the last id of the page and is null on the last page`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        Mockito.`when`(emailVerificationRepository.readPage(10L, 100L, 2))
            .thenReturn(
                verificationPage(rows = listOf(verificationRow(101L), verificationRow(102L)), hasMore = true)
            )
        Mockito.`when`(emailVerificationRepository.readPage(10L, 102L, 2))
            .thenReturn(verificationPage(rows = listOf(verificationRow(103L)), hasMore = false))

        val firstPage = controller().getExecutionEmailVerifications(10L, 100L, 2, null).body!!
        assertEquals(102L, firstPage.nextAfterId)
        assertTrue(firstPage.hasMore)

        val lastPage = controller().getExecutionEmailVerifications(10L, 102L, 2, null).body!!
        assertNull(lastPage.nextAfterId, "no cursor may be emitted for the final page")
        assertFalse(lastPage.hasMore)
    }

    @Test
    fun `summary is the whole execution aggregate with errors mapped from serviceError`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        Mockito.`when`(emailVerificationRepository.readPage(10L, 0L, 50)).thenReturn(
            verificationPage(
                rows = listOf(verificationRow(1L)),
                aggregate = BatchEmailVerificationAggregate(
                    total = 5, pending = 0, passed = 2, rejected = 3, serviceError = 0,
                    notSent = 4, sending = 0, sent = 1, sendFailed = 0, sendSkipped = 3,
                    tagNotRequired = 2, tagPending = 0, tagApplied = 0, tagFailed = 1
                )
            )
        )

        val summary = controller().getExecutionEmailVerifications(10L, 0L, 50, null).body!!.summary

        assertEquals(5, summary.total)
        assertEquals(0, summary.pending)
        assertEquals(2, summary.passed)
        assertEquals(3, summary.rejected)
        assertEquals(0, summary.errors)
        assertEquals(1, summary.tagFailed, "tag failures are an extra dimension of the same execution")
        assertEquals(
            summary.total,
            summary.passed + summary.rejected + summary.errors + summary.pending,
            "passed/rejected/errors/pending must partition the total"
        )
    }

    @Test
    fun `items expose only the display whitelist`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        Mockito.`when`(emailVerificationRepository.readPage(10L, 0L, 50)).thenReturn(
            verificationPage(
                listOf(
                    verificationRow(
                        id = 1L, decision = "SKIP", providerState = "undeliverable",
                        providerReason = "rejected_email", tagStatus = "FAILED",
                        tagError = "EMAIL_VERIFY_TAG_FAILED", sendStatus = "SKIPPED",
                        sendReason = "EMAIL_VERIFICATION_REJECTED"
                    )
                )
            )
        )

        val json = jsonMapper.writeValueAsString(
            controller().getExecutionEmailVerifications(10L, 0L, 50, null).body!!
        )
        val keys = jsonMapper.readTree(json).path("items").get(0).fieldNames().asSequence().toSet()

        assertEquals(
            setOf(
                "id", "expertDocId", "orcidId", "expertName", "email", "decision",
                "providerState", "providerReason", "errorCode", "checkedAt", "requestCount",
                "sendStatus", "sendReason", "tagStatus", "tagError", "reusedFromId"
            ),
            keys
        )
        assertFalse(json.contains("apiKey"), "no provider secret may reach the console")
        assertFalse(json.contains("updatedAt"), "internal timestamps stay out of the response")
    }

    @Test
    fun `reused verification exposes source without replacing current send status`() {
        val originalTime = LocalDateTime.of(2026, 1, 1, 9, 0)
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        Mockito.`when`(emailVerificationRepository.readPage(10L, 0L, 50)).thenReturn(
            verificationPage(listOf(verificationRow(50L, checkedAt = originalTime, requestCount = 0,
                sendStatus = "NOT_SENT").copy(reusedFromId = 42L))))
        val row = controller().getExecutionEmailVerifications(10L, 0L, 50, null).body!!.items.single()
        assertEquals(42L, row.reusedFromId)
        assertEquals(0, row.requestCount)
        assertEquals(originalTime, row.checkedAt)
        assertEquals("NOT_SENT", row.sendStatus)
    }

    @Test
    fun `repository failure propagates instead of returning an empty page`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        Mockito.`when`(
            emailVerificationRepository.readPage(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt())
        ).thenThrow(RuntimeException("batch_email_verification unavailable"))

        assertThrows(RuntimeException::class.java) {
            controller().getExecutionEmailVerifications(10L, 0L, 50, null)
        }
    }

    @Test
    fun `verification detail performs a single read and never writes`() {
        Mockito.`when`(taskExecutionService.getExecution(10L)).thenReturn(execution())
        stubAnyVerificationPage(verificationPage(listOf(verificationRow(1L))))

        controller().getExecutionEmailVerifications(10L, 0L, 50, null)

        Mockito.verify(emailVerificationRepository).readPage(10L, 0L, 50)
        Mockito.verifyNoMoreInteractions(emailVerificationRepository)
    }
}
