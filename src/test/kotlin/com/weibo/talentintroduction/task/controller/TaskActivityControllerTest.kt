package com.weibo.talentintroduction.task.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskExecutionListItem
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryExtractor
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * T-5.1：任务活动端点（I-2/I-3/I-4/I-8）。
 *
 * 三组断言，刻意不互相替代：
 * 1. 真实 controller 逻辑 + **真实** [TaskProgressStore]（只 mock 日志仓库）——进度匹配规则
 *    与 peek 纯读行为只能这样证；只 mock progress 再断言自己的映射是自证。
 * 2. 与旧 [TaskExecutionController] **一同**注册到同一个 MockMvc——`/active` 是字面量段，
 *    必须赢过 `/{id}`；用 `verifyNoInteractions(taskExecutionService)` 证明它没有绕进数字 id 路由。
 * 3. `@Query` 注解文本——有界（LIMIT/OFFSET）与不读三个 TEXT 列是编译期不可见的约定。
 */
class TaskActivityControllerTest {

    private val repository = Mockito.mock(TaskExecutionRepository::class.java)
    private val progressLogRepository = Mockito.mock(TaskProgressLogRepository::class.java)
    private val store = TaskProgressStore(progressLogRepository, ObjectMapper())
    private val activityController = TaskActivityController(repository, store)

    private val taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
    private val extractor = Mockito.mock(TaskExecutionSummaryExtractor::class.java)
    private val legacyController =
        TaskExecutionController(taskExecutionService, repository, extractor, ObjectMapper())
    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(legacyController, activityController).build()

    private fun activeItem(
        id: Long,
        taskType: String = "EXPERT_ENRICHMENT",
        triggerType: String = "SCHEDULED",
        status: String = "RUNNING",
        startedAt: LocalDateTime = LocalDateTime.now().minusSeconds(30),
        successCount: Int = 0,
        failureCount: Int = 0
    ) = TaskExecutionListItem(
        id = id,
        taskType = taskType,
        triggerType = triggerType,
        status = status,
        successCount = successCount,
        failureCount = failureCount,
        errorMessage = null,
        startedAt = startedAt,
        finishedAt = null
    )

    private fun stubActive(items: List<TaskExecutionListItem>, total: Long = items.size.toLong()) {
        Mockito.`when`(repository.findActivePage(Mockito.anyInt(), Mockito.anyLong())).thenReturn(items)
        Mockito.`when`(repository.countActive()).thenReturn(total)
    }

    private fun progress(
        taskType: String,
        processedCount: Long,
        totalCount: Long,
        status: String = "RUNNING",
        message: String? = null
    ) = TaskProgress(
        taskType = taskType,
        status = status,
        batchNumber = 1,
        processedCount = processedCount,
        totalCount = totalCount,
        message = message
    )

    // ---- route contract: /active must not be captured by /{id} ----

    @Test
    fun `active route is not captured by the numeric id route`() {
        stubActive(listOf(activeItem(id = 12845L)))

        mockMvc.perform(get("/api/task-executions/active"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(6))
            .andExpect(jsonPath("$.items[0].id").value(12845))
            .andExpect(jsonPath("$.items[0].taskTypeLabel").value("学术数据补全"))
            .andExpect(jsonPath("$.items[0].triggerType").value("SCHEDULED"))

        // 走 /{id} 就会调用 service.getExecution；没有交互即证明路由落在新控制器上。
        Mockito.verifyNoInteractions(taskExecutionService)
    }

    @Test
    fun `numeric id route still reaches the legacy controller`() {
        Mockito.`when`(taskExecutionService.getExecution(42L)).thenReturn(
            TaskExecution(
                id = 42L,
                taskType = "DAILY_COUNT_RESET",
                triggerType = "SCHEDULED",
                status = "SUCCESS",
                requestPayload = "{}",
                resultSummary = null,
                startedAt = LocalDateTime.now(),
                finishedAt = null
            )
        )

        mockMvc.perform(get("/api/task-executions/42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.requestPayload").value("{}"))

        Mockito.verify(repository, Mockito.never())
            .findActivePage(Mockito.anyInt(), Mockito.anyLong())
    }

    // ---- pagination + total (I-2) ----

    @Test
    fun `page and size are clamped and offset is computed in long`() {
        stubActive(emptyList(), total = 13L)

        val clamped = activityController.activeExecutions(page = -3, size = 0)
        assertEquals(0, clamped.page)
        assertEquals(1, clamped.size)
        assertEquals(13L, clamped.total)
        assertTrue(clamped.items.isEmpty())
        Mockito.verify(repository).findActivePage(1, 0L)

        activityController.activeExecutions(page = 2, size = 999)
        Mockito.verify(repository).findActivePage(50, 100L)
    }

    @Test
    fun `total comes from an independent count and is not the page size`() {
        stubActive(listOf(activeItem(id = 1L), activeItem(id = 2L)), total = 13L)

        val response = activityController.activeExecutions(page = 1, size = 2)

        assertEquals(13L, response.total)
        assertEquals(2, response.items.size)
        Mockito.verify(repository).countActive()
    }

    @Test
    fun `same task type with different execution ids stays as two items`() {
        stubActive(
            listOf(
                activeItem(id = 11L, taskType = "PREVIEW_UNKNOWN_TASK", triggerType = "QUEUE"),
                activeItem(id = 12L, taskType = "PREVIEW_UNKNOWN_TASK", triggerType = "QUEUE")
            )
        )

        val response = activityController.activeExecutions(page = 0, size = 6)

        assertEquals(listOf(11L, 12L), response.items.map { it.id })
    }

    @Test
    fun `unknown catalog type keeps the code and exposes no metric or progress ui`() {
        stubActive(listOf(activeItem(id = 11L, taskType = "PREVIEW_UNKNOWN_TASK", triggerType = "QUEUE")))

        val item = activityController.activeExecutions(page = 0, size = 6).items.single()

        assertEquals("PREVIEW_UNKNOWN_TASK", item.taskTypeLabel)
        assertNull(item.metricLabel)
        assertFalse(item.hasProgressUi)
        // triggerType 原样展示；catalog.group 只描述类型分类，不能替代每次执行的触发方式。
        assertEquals("QUEUE", item.triggerType)
    }

    @Test
    fun `elapsed seconds derive from started_at and never go negative`() {
        val now = LocalDateTime.now()
        stubActive(listOf(activeItem(id = 1L, startedAt = now.minusSeconds(226))))
        assertTrue(activityController.activeExecutions(0, 6).items.single().elapsedSeconds >= 226)

        stubActive(listOf(activeItem(id = 2L, startedAt = now.plusHours(2))))
        assertEquals(0L, activityController.activeExecutions(0, 6).items.single().elapsedSeconds)
    }

    // ---- progress matching (I-3), real store ----

    @Test
    fun `progress only matches the same execution id and a running status`() {
        stubActive(listOf(activeItem(id = 500L, taskType = "EXPERT_ENRICHMENT")))
        val (started, pendingToken) = store.tryStartWithToken(
            "EXPERT_ENRICHMENT",
            progress("EXPERT_ENRICHMENT", processedCount = 126, totalCount = 300, message = "正在补全专家学术信息")
        )
        assertTrue(started)

        // 未绑定执行 id：内存里是负 pendingToken，不是任何执行的实时状态。
        assertNull(activityController.activeExecutions(0, 6).items.single().progress)
        val pending = store.peek("EXPERT_ENRICHMENT")!!.executionId
        assertNotNull(pending)
        assertTrue(pending!! < 0, "未绑定时内存执行身份是负 pendingToken")

        assertTrue(store.bindExecutionId("EXPERT_ENRICHMENT", pendingToken, 500L))
        val matched = activityController.activeExecutions(0, 6).items.single().progress
        assertNotNull(matched)
        assertEquals(126L, matched!!.processedCount)
        assertEquals(300L, matched.totalCount)
        assertEquals(42, matched.percentage)
        assertEquals("正在补全专家学术信息", matched.message)

        // 同类型的另一次执行（同页或下一页）绝不能共用这次进度。
        stubActive(listOf(activeItem(id = 501L, taskType = "EXPERT_ENRICHMENT")))
        assertNull(activityController.activeExecutions(0, 6).items.single().progress)

        // 内存终态与 DB 终态的短时错位：按"无实时进度"处理，不推断完成、不补写。
        store.update("EXPERT_ENRICHMENT", progress("EXPERT_ENRICHMENT", 300, 300, status = "COMPLETED"))
        stubActive(listOf(activeItem(id = 500L, taskType = "EXPERT_ENRICHMENT")))
        assertNull(activityController.activeExecutions(0, 6).items.single().progress)
    }

    @Test
    fun `unknown total yields a null percentage and long messages are truncated`() {
        stubActive(listOf(activeItem(id = 700L, taskType = "EXPERT_ENRICHMENT")))
        store.setCurrentExecutionId("EXPERT_ENRICHMENT", 700L)
        store.update(
            "EXPERT_ENRICHMENT",
            progress("EXPERT_ENRICHMENT", processedCount = 5, totalCount = 0, message = "长".repeat(900))
        )

        val matched = activityController.activeExecutions(0, 6).items.single().progress

        assertNotNull(matched)
        assertNull(matched!!.percentage)
        assertEquals(5L, matched.processedCount)
        assertEquals(0L, matched.totalCount)
        assertEquals(500, matched.message!!.length)
    }

    @Test
    fun `a cleared execution context stops reporting live progress`() {
        stubActive(listOf(activeItem(id = 500L, taskType = "EXPERT_ENRICHMENT")))
        store.setCurrentExecutionId("EXPERT_ENRICHMENT", 500L)
        store.update("EXPERT_ENRICHMENT", progress("EXPERT_ENRICHMENT", 10, 40))
        assertNotNull(activityController.activeExecutions(0, 6).items.single().progress)

        assertTrue(store.clearExecutionContext("EXPERT_ENRICHMENT", 500L))

        assertNull(activityController.activeExecutions(0, 6).items.single().progress)
    }

    @Test
    fun `cancelling memory status is reported while the db row keeps its own status`() {
        stubActive(listOf(activeItem(id = 500L, taskType = "EXPERT_ENRICHMENT", status = "RUNNING")))
        store.setCurrentExecutionId("EXPERT_ENRICHMENT", 500L)
        store.update("EXPERT_ENRICHMENT", progress("EXPERT_ENRICHMENT", 10, 40))
        assertTrue(store.requestCancel("EXPERT_ENRICHMENT"))

        val item = activityController.activeExecutions(0, 6).items.single()

        assertEquals("RUNNING", item.status)
        assertEquals("CANCELLING", item.progress!!.status)
    }

    // ---- peek purity (I-3/I-4) ----

    @Test
    fun `peek is a pure in-memory read that never touches the log repository`() {
        stubActive(listOf(activeItem(id = 800L, taskType = "EXPERT_ENRICHMENT")))
        store.setCurrentExecutionId("EXPERT_ENRICHMENT", 800L)
        Mockito.clearInvocations(progressLogRepository)

        assertNotNull(activityController.activeExecutions(0, 6).items.single().progress)

        Mockito.verifyNoInteractions(progressLogRepository)
    }

    @Test
    fun `absent in-memory progress is not restored from the progress log`() {
        // 日志里有一条历史 RUNNING：get() 会把它恢复成 INTERRUPTED，peek() 必须完全不看它。
        Mockito.`when`(progressLogRepository.findTopByTaskTypeOrderByIdDesc("EXPERT_ENRICHMENT"))
            .thenReturn(
                TaskProgressLog(
                    id = 1L,
                    taskType = "EXPERT_ENRICHMENT",
                    taskExecutionId = 900L,
                    batchNumber = 1,
                    status = "RUNNING",
                    processedCount = 77,
                    totalCount = 100,
                    createdAt = LocalDateTime.now()
                )
            )
        stubActive(listOf(activeItem(id = 900L, taskType = "EXPERT_ENRICHMENT")))

        assertNull(activityController.activeExecutions(0, 6).items.single().progress)
        Mockito.verify(progressLogRepository, Mockito.never())
            .findTopByTaskTypeOrderByIdDesc(Mockito.anyString())
    }

    @Test
    fun `one active request performs exactly one page select plus one count and nothing else`() {
        stubActive(listOf(activeItem(id = 1L)), total = 1L)

        activityController.activeExecutions(page = 0, size = 6)

        Mockito.verify(repository).findActivePage(6, 0L)
        Mockito.verify(repository).countActive()
        Mockito.verifyNoMoreInteractions(repository)
    }

    // ---- SQL contract (I-4) ----

    @Test
    fun `active page query is bounded and skips every text column`() {
        val sql = querySql("findActivePage", Int::class.java, Long::class.java)

        assertTrue(sql.contains("status IN ('RUNNING', 'CANCELLING')"), sql)
        assertTrue(sql.contains("ORDER BY started_at DESC, id DESC"), sql)
        assertTrue(sql.contains("LIMIT :size OFFSET :offset"), sql)
        assertFalse(sql.contains("SELECT *"), sql)
        assertFalse(sql.contains("request_payload"), sql)
        assertFalse(sql.contains("result_summary"), sql)
        assertTrue(sql.contains("NULL AS error_message"), sql)
    }

    @Test
    fun `active count query is an independent count over the same status set`() {
        assertEquals(
            "SELECT COUNT(*) FROM task_execution WHERE status IN ('RUNNING', 'CANCELLING')",
            querySql("countActive")
        )
    }

    private fun querySql(name: String, vararg parameterTypes: Class<*>): String {
        val method = TaskExecutionRepository::class.java.getMethod(name, *parameterTypes)
        val annotation = method.getAnnotation(Query::class.java)
        assertNotNull(annotation, "@Query must be present on $name")
        return annotation.value.replace(Regex("\\s+"), " ").trim()
    }

    // ---- auth surface (I-8) ----

    @Test
    fun `the new endpoint stays behind the auth interceptor`() {
        val config = Files.readString(
            Path.of("src/main/kotlin/com/weibo/talentintroduction/auth/config/AuthWebConfig.kt")
        )

        assertTrue(config.contains("""excludePathPatterns("/api/auth/login", "/api/auth/me")"""), config)
        assertFalse(config.contains("task-executions"), "新端点不得加入鉴权豁免清单")
    }
}
