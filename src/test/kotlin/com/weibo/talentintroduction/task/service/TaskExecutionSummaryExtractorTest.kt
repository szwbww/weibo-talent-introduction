package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskTypeCatalog
import com.weibo.talentintroduction.task.domain.TaskTypeMeta
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.LocalDateTime

/**
 * B2 (I1-2 / I1-3 / I1-4 / N1-2)：TaskExecutionSummaryExtractor 三级取数
 * （迁移自 TaskProgressController.parseResultSummary / fallbackFromLog，输出须与
 * 迁移前逐字一致）+ TaskTypeCatalog 断言（原 TaskTypeCatalogTest 合并于此文件）。
 */
class TaskExecutionSummaryExtractorTest {

    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val extractor = TaskExecutionSummaryExtractor(progressLogRepository, objectMapper)

    private fun execution(
        id: Long,
        taskType: String,
        resultSummary: String?,
        status: String = "SUCCESS",
        successCount: Int = 0,
        failureCount: Int = 0
    ) = TaskExecution(
        id = id, taskType = taskType, triggerType = "SCHEDULED", status = status,
        requestPayload = null, resultSummary = resultSummary,
        successCount = successCount, failureCount = failureCount,
        startedAt = LocalDateTime.of(2026, 6, 10, 10, 0),
        finishedAt = LocalDateTime.of(2026, 6, 10, 10, 5)
    )

    // ---- I1-3 第 ① 级：resultSummary 解析（6 种 summaryRule，输出与迁移前 parseResultSummary 逐字一致） ----

    @Test
    fun `EXPERT_REVALIDATION resultSummary maps total passed demoted`() {
        val summary = """{"stats":{"total":100,"passed":80,"demoted":15,"demotionFailed":5},"wasCancelled":false}"""
        val totals = extractor.extract("EXPERT_REVALIDATION", execution(1, "EXPERT_REVALIDATION", summary))
        assertEquals(ExecutionTotals(totalProcessed = 100, totalPassed = 80, totalRejected = 15), totals)
    }

    @Test
    fun `RAW_PROMOTION_SCAN resultSummary maps total promoted filtered plus emailRejected`() {
        val summary = """{"stats":{"total":200,"promoted":150,"filtered":30,"emailRejected":10,"promotionFailed":5},"wasCancelled":false}"""
        val totals = extractor.extract("RAW_PROMOTION_SCAN", execution(2, "RAW_PROMOTION_SCAN", summary))
        assertEquals(ExecutionTotals(totalProcessed = 200, totalPassed = 150, totalRejected = 40), totals)
    }

    @Test
    fun `EXPERT_DISCOVERY resultSummary maps totalPapers indexed rejected and summaryText`() {
        val summary = """{"stats":{"totalPapers":500,"indexed":400,"promoted":350},"summaryText":"完成: 论文 500, 收录 400, 晋升 350","wasCancelled":false}"""
        val totals = extractor.extract("EXPERT_DISCOVERY", execution(3, "EXPERT_DISCOVERY", summary))
        assertEquals(
            ExecutionTotals(
                totalProcessed = 500, totalPassed = 400, totalRejected = 100,
                summaryText = "完成: 论文 500, 收录 400, 晋升 350"
            ),
            totals
        )
    }

    @Test
    fun `MANUAL_INITIAL_OUTREACH resultSummary maps sent failed and total minus remaining floor zero`() {
        val summary =
            """{"total":84079,"sent":2,"failed":0,"skippedNoAccount":0,"wasCancelled":false,"finalStatus":"COMPLETED","stopReason":null,"remaining":84077}"""
        val totals = extractor.extract("MANUAL_INITIAL_OUTREACH", execution(4, "MANUAL_INITIAL_OUTREACH", summary))
        assertEquals(ExecutionTotals(totalProcessed = 2, totalPassed = 2, totalRejected = 0), totals)
    }

    @Test
    fun `CHECK_REPLIES resultSummary maps totalAccountsToPoll success and failed accounts`() {
        val summary = """{"totalAccountsToPoll":100,"successAccountCount":90,"failedAccountCount":10}"""
        val totals = extractor.extract("CHECK_REPLIES", execution(5, "CHECK_REPLIES", summary))
        assertEquals(ExecutionTotals(totalProcessed = 100, totalPassed = 90, totalRejected = 10), totals)
    }

    @Test
    fun `EXPERT_ENRICHMENT resultSummary maps enriched plus failed`() {
        val summary = """{"enriched":12,"failed":1}"""
        val totals = extractor.extract("EXPERT_ENRICHMENT", execution(6, "EXPERT_ENRICHMENT", summary))
        assertEquals(ExecutionTotals(totalProcessed = 13, totalPassed = 12, totalRejected = 1), totals)
    }

    // ---- I1-3 第 ② 级：RUNNING（resultSummary=null）走最新 progress_log.detailsJson ----

    @Test
    fun `RUNNING with progress log detailsJson yields non-zero level 2 totals`() {
        val running = execution(7, "EXPERT_REVALIDATION", null, status = "RUNNING")
        val log = com.weibo.talentintroduction.task.domain.TaskProgressLog(
            id = 10L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 7L,
            batchNumber = 5, status = "RUNNING", processedCount = 60, totalCount = 100,
            detailsJson = """{"passed":50,"demoted":10}"""
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(7L)).thenReturn(log)

        val totals = extractor.extract("EXPERT_REVALIDATION", running)
        assertEquals(ExecutionTotals(totalProcessed = 60, totalPassed = 50, totalRejected = 10), totals)
    }

    @Test
    fun `discovery fallback from log parses indexed with rejected zero`() {
        val running = execution(8, "EXPERT_DISCOVERY", null, status = "RUNNING")
        val log = com.weibo.talentintroduction.task.domain.TaskProgressLog(
            id = 12L, taskType = "EXPERT_DISCOVERY", taskExecutionId = 8L,
            batchNumber = 3, status = "RUNNING", processedCount = 300, totalCount = 500,
            detailsJson = """{"indexed":200,"promoted":180}"""
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(8L)).thenReturn(log)

        val totals = extractor.extract("EXPERT_DISCOVERY", running)
        assertEquals(ExecutionTotals(totalProcessed = 300, totalPassed = 200, totalRejected = 0), totals)
    }

    @Test
    fun `fallback from log without detailsJson still reports processedCount`() {
        val execution9 = execution(9, "EXPERT_REVALIDATION", null)
        val log = com.weibo.talentintroduction.task.domain.TaskProgressLog(
            id = 20L, taskType = "EXPERT_REVALIDATION", taskExecutionId = 9L,
            batchNumber = -1, status = "COMPLETED", processedCount = 100, totalCount = 100,
            detailsJson = null
        )
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(9L)).thenReturn(log)

        val totals = extractor.extract("EXPERT_REVALIDATION", execution9)
        assertEquals(100L, totals.totalProcessed)
    }

    // ---- I1-3 第 ③ 级：三级全空时回落存量 success_count/failure_count；全空为全 0 ----

    @Test
    fun `no resultSummary no log falls back to stored success and failure counts`() {
        val execution10 = execution(10, "AUTO_REPLY_ALL", null, successCount = 7, failureCount = 3)
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(10L)).thenReturn(null)

        val totals = extractor.extract("AUTO_REPLY_ALL", execution10)
        assertEquals(ExecutionTotals(totalProcessed = 10, totalPassed = 7, totalRejected = 3), totals)
    }

    @Test
    fun `all three levels empty returns zero totals and null summaryText`() {
        val execution11 = execution(11, "EXPERT_REVALIDATION", null)
        Mockito.`when`(progressLogRepository.findTopByTaskExecutionIdOrderByIdDesc(11L)).thenReturn(null)

        val totals = extractor.extract("EXPERT_REVALIDATION", execution11)
        assertEquals(ExecutionTotals(), totals)
        assertNull(totals.summaryText)
    }

    @Test
    fun `malformed resultSummary returns zero totals without progress log fallback`() {
        val execution12 = execution(12, "EXPERT_REVALIDATION", "{bad json")
        val totals = extractor.extract("EXPERT_REVALIDATION", execution12)
        assertEquals(ExecutionTotals(), totals)
        // 终态权威：解析异常不回退到日志查询
        Mockito.verify(progressLogRepository, Mockito.never())
            .findTopByTaskExecutionIdOrderByIdDesc(Mockito.anyLong())
    }

    // ---- detectWasCancelled（终态改判 CANCELLED） ----

    @Test
    fun `wasCancelled true maps to cancelled detection`() {
        assertTrue(extractor.detectWasCancelled("""{"stats":{"total":50,"passed":30,"demoted":10},"wasCancelled":true}"""))
    }

    @Test
    fun `wasCancelled absent or malformed returns false`() {
        assertFalse(extractor.detectWasCancelled("""{"stats":{"total":1,"passed":1,"demoted":0},"wasCancelled":false}"""))
        assertFalse(extractor.detectWasCancelled(null))
        assertFalse(extractor.detectWasCancelled("{bad json"))
    }

    // ---- TaskTypeCatalog 断言（原 TaskTypeCatalogTest 合并于此） ----

    /** N1-2 锁定：hasProgressUi 的集合恰好等于既有 allowedTaskTypes 的 6 项。 */
    @Test
    fun `catalog hasProgressUi set equals the six-item whitelist`() {
        val expected = setOf(
            "EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY",
            "EXPERT_ENRICHMENT", "MANUAL_INITIAL_OUTREACH", "CHECK_REPLIES"
        )
        val actual = TaskTypeCatalog.entries.filter { it.value.hasProgressUi }.keys
        assertEquals(expected, actual)
    }

    /** 反向断言（防 catalog 与 extractor 漂移）：每个非 null summaryRule 都有对应分支。 */
    @Test
    fun `catalog summaryRule keys match the extractor rule set`() {
        val ruleKeys = TaskTypeCatalog.entries.values.filter { it.summaryRule != null }.map { it.summaryRule }.toSet()
        assertEquals(
            setOf(
                "EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY",
                "MANUAL_INITIAL_OUTREACH", "CHECK_REPLIES", "EXPERT_ENRICHMENT"
            ),
            ruleKeys
        )
    }

    /** 现状审计的 16 种 taskType 全集逐条声明（字面量断言）。 */
    @Test
    fun `catalog covers the sixteen audited task types`() {
        val auditedCodes = setOf(
            "AI_QA_EXTRACTION", "AUTO_REPLY_ACCOUNT", "AUTO_REPLY_ALL",
            "AUTO_REPLY_ALL_DISPATCH", "BOUNCE_COLLECTION", "CANDIDATE_OPERATOR_STATUS_SYNC",
            "CHECK_REPLIES", "DAILY_COUNT_RESET", "EXPERT_DISCOVERY", "EXPERT_ENRICHMENT",
            "EXPERT_REVALIDATION", "INITIAL_OUTREACH", "MANUAL_INITIAL_OUTREACH",
            "OPERATOR_STATUS_RECONCILE", "POSTMASTER_REPUTATION", "RAW_PROMOTION_SCAN"
        )
        assertEquals(auditedCodes, TaskTypeCatalog.entries.keys)
    }

    /** I1-2 口径锁定：metricLabel 只在该类型存量计数可信时非 null；已核实的 6 项逐一断言。 */
    @Test
    fun `catalog metricLabel decisions are locked`() {
        val meta: (String) -> TaskTypeMeta = { code -> TaskTypeCatalog.byCode(code)!! }
        assertEquals("已发送/失败", meta("MANUAL_INITIAL_OUTREACH").metricLabel)
        assertEquals("已回复/转人工", meta("AUTO_REPLY_ACCOUNT").metricLabel)
        assertEquals("派发账号数/—", meta("AUTO_REPLY_ALL_DISPATCH").metricLabel)
        assertEquals("轮询账号/失败账号", meta("AUTO_REPLY_ALL").metricLabel)
        assertEquals("一致/异常", meta("OPERATOR_STATUS_RECONCILE").metricLabel)
        assertNull(meta("INITIAL_OUTREACH").metricLabel)
        assertNull(meta("BOUNCE_COLLECTION").metricLabel)
        assertNull(meta("EXPERT_ENRICHMENT").metricLabel)
        assertNull(meta("DAILY_COUNT_RESET").metricLabel)
        assertEquals(16, TaskTypeCatalog.entries.size)
    }
}
