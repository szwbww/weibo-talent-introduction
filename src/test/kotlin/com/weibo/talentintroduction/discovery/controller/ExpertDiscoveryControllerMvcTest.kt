package com.weibo.talentintroduction.discovery.controller

import com.weibo.talentintroduction.config.EuropePmcProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.OpenAlexBudgetSnapshot
import com.weibo.talentintroduction.discovery.domain.PaperSearchCriteria
import com.weibo.talentintroduction.discovery.domain.SubjectScopeCatalog
import com.weibo.talentintroduction.discovery.repository.PipelineDesiredState
import com.weibo.talentintroduction.discovery.repository.PipelinePhase
import com.weibo.talentintroduction.discovery.repository.PipelineWaitReason
import com.weibo.talentintroduction.discovery.service.ArxivDataSource
import com.weibo.talentintroduction.discovery.service.CoreDataSource
import com.weibo.talentintroduction.discovery.service.CrossrefDataSource
import com.weibo.talentintroduction.discovery.service.DiscoveryPipelineService
import com.weibo.talentintroduction.discovery.service.DiscoveryPipelineStatus
import com.weibo.talentintroduction.discovery.service.DiscoverySourceStatus
import com.weibo.talentintroduction.discovery.service.ExpertDiscoveryService
import com.weibo.talentintroduction.discovery.service.OpenAlexDataSource
import com.weibo.talentintroduction.discovery.service.OrcidDataSource
import com.weibo.talentintroduction.discovery.service.PipelineLaunchResult
import com.weibo.talentintroduction.discovery.service.PipelineRejection
import com.weibo.talentintroduction.discovery.service.PipelineRejectionReason
import com.weibo.talentintroduction.discovery.service.PmcOaDataSource
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.concurrent.Executor

@WebMvcTest(ExpertDiscoveryController::class)
@EnableConfigurationProperties(EuropePmcProperties::class, ExpertDiscoveryProperties::class)
class ExpertDiscoveryControllerMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var discoveryService: ExpertDiscoveryService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean(name = "enrichmentExecutor")
    private lateinit var enrichmentExecutor: Executor

    @MockBean
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>

    @MockBean
    private lateinit var crossrefProvider: ObjectProvider<CrossrefDataSource>

    @MockBean
    private lateinit var arxivProvider: ObjectProvider<ArxivDataSource>

    @MockBean
    private lateinit var pmcOaProvider: ObjectProvider<PmcOaDataSource>

    @MockBean
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>

    @MockBean
    private lateinit var coreProvider: ObjectProvider<CoreDataSource>

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    @Test
    fun `triggerDiscovery returns JSON contract on success`() {
        val token = 5555L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 100L,
            taskType = "EXPERT_DISCOVERY",
            triggerType = "MANUAL",
            status = "SUCCESS",
            requestPayload = null,
            resultSummary = "{\"indexed\": 5}",
            startedAt = LocalDateTime.now()
        )

        Mockito.`when`(taskExecutionService.runAndRecord<Any>(
            eqValue("EXPERT_DISCOVERY"),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue<(Long) -> Unit> { },
            Mockito.isNull(),
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(100L)
            taskExecution
        }

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionId").value(100))
            .andExpect(jsonPath("$.result.taskType").value("EXPERT_DISCOVERY"))
            .andExpect(jsonPath("$.result.resultSummary").value("{\"indexed\": 5}"))
    }

    @Test
    fun `triggerDiscovery returns 409 when task is running`() {
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(false, -1L))

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("任务正在执行中，请等待完成"))
    }

    @Test
    fun `triggerDiscovery returns 500 when execution fails`() {
        val token = 5555L
        Mockito.`when`(progressStore.tryStartWithToken(eqValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0))))
            .thenReturn(Pair(true, token))

        val taskExecution = TaskExecution(
            id = 101L,
            taskType = "EXPERT_DISCOVERY",
            triggerType = "MANUAL",
            status = "FAILED",
            requestPayload = null,
            resultSummary = null,
            errorMessage = "Europe PMC is down",
            startedAt = LocalDateTime.now()
        )

        Mockito.`when`(taskExecutionService.runAndRecord<Any>(
            eqValue("EXPERT_DISCOVERY"),
            eqValue("MANUAL"),
            anyValue(Any()),
            anyValue<(Long) -> Unit> { },
            Mockito.isNull(),
            anyValue { Any() }
        )).thenAnswer { invocation ->
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(101L)
            taskExecution
        }

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("Europe PMC is down"))
    }

    @Test
    fun `pipeline endpoints report LEGACY while the feature switch is off`() {
        mockMvc.perform(get("/api/expert-discovery/pipeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("LEGACY"))
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.status").doesNotExist())

        mockMvc.perform(post("/api/expert-discovery/pipeline/resume"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.mode").value("LEGACY"))
    }
}

/**
 * c3（I-1/I-2/I-3/I-5）：`pipeline-enabled=true` 时的连续模式协议。
 *
 * 与上面同一文件里的旧模式用例分开成两个上下文，是为了让「开关关闭时旧同步路径逐字保留」与
 * 「开关打开时只走 02 的持久化控制」各自可断言 —— 模式由 `ExpertDiscoveryProperties.pipelineEnabled`
 * 单点决定，而不是由请求决定。
 */
@WebMvcTest(
    controllers = [ExpertDiscoveryController::class],
    properties = ["talent-introduction.expert-discovery.pipeline-enabled=true"]
)
@EnableConfigurationProperties(EuropePmcProperties::class, ExpertDiscoveryProperties::class)
class ExpertDiscoveryContinuousMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var discoveryService: ExpertDiscoveryService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean(name = "enrichmentExecutor")
    private lateinit var enrichmentExecutor: Executor

    @MockBean
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>

    @MockBean
    private lateinit var crossrefProvider: ObjectProvider<CrossrefDataSource>

    @MockBean
    private lateinit var arxivProvider: ObjectProvider<ArxivDataSource>

    @MockBean
    private lateinit var pmcOaProvider: ObjectProvider<PmcOaDataSource>

    @MockBean
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>

    @MockBean
    private lateinit var coreProvider: ObjectProvider<CoreDataSource>

    @MockBean
    private lateinit var pipeline: DiscoveryPipelineService

    /** 与文件顶部同一惯例：Matcher 返回值不得为 null，必须回落到真实默认值再传给非空 Kotlin 参数。 */
    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun anyCriteria(): PaperSearchCriteria = anyValue(PaperSearchCriteria())

    private fun usePipeline() {
        Mockito.`when`(pipeline.enabled).thenReturn(true)
    }

    private fun appliedResult(executionId: Long? = null): PipelineLaunchResult = PipelineLaunchResult(
        applied = true,
        pipelineId = 1L,
        state = PipelinePhase.QUEUED,
        phase = PipelinePhase.QUEUED,
        desiredState = PipelineDesiredState.RUNNING,
        queryHash = "hash-1",
        executionId = executionId,
        resumed = false
    )

    private fun status(
        state: String,
        queryHash: String?,
        currentExecutionId: Long?,
        waitReasons: List<String>,
        phase: String = state
    ): DiscoveryPipelineStatus = DiscoveryPipelineStatus(
        pipelineId = 1L,
        state = state,
        phase = phase,
        desiredState = if (state == PipelineDesiredState.PAUSED) PipelineDesiredState.PAUSED else PipelineDesiredState.RUNNING,
        currentExecutionId = currentExecutionId,
        queryHash = queryHash,
        criteriaVersion = 1,
        sources = listOf(
            DiscoverySourceStatus(
                source = "OPENALEX", cursorState = "ACTIVE", cursorValue = "c1", nextAttemptAt = null,
                sourceError = null, activeJobs = 2, failedJobs = 1, queuedItems = 30,
                processedItems = 12, indexedExperts = 5, duplicateExperts = 4
            )
        ),
        queuedPapers = 20,
        queuedRecords = 10,
        processedPapers = 8,
        processedRecords = 4,
        indexedExperts = 5,
        duplicateExperts = 4,
        failedItems = 1,
        queueDepth = 30,
        runningJobs = 2,
        payloadBytes = 1024,
        reservedResultBytes = 512,
        oldestActiveAgeSeconds = 7,
        waitReasons = waitReasons,
        waitReason = waitReasons.firstOrNull(),
        nextWakeAt = null,
        windowUntil = null,
        ownerActive = true,
        capacityPaused = false,
        rawScanDone = true,
        budget = OpenAlexBudgetSnapshot(
            accountScope = "acc",
            resetAt = java.time.Instant.parse("2026-09-23T00:00:00Z"),
            officialLimitCredits = 10_000,
            officialRemainingCredits = 9_773,
            confirmedSpentCredits = 227,
            reservedCredits = 2,
            effectiveRemainingCredits = 9_771,
            enrichmentReserveCredits = 500,
            lastSyncedAt = java.time.Instant.parse("2026-09-22T23:00:00Z"),
            deferredReason = null,
            retryAt = null
        )
    )

    @Test
    fun `both manual entries are accepted with the same normalized RND_TARGET query and never run legacy sync (I-1, I-2)`() {
        usePipeline()
        val captured = mutableListOf<PaperSearchCriteria>()
        Mockito.`when`(pipeline.launch(anyCriteria(), eqValue("MANUAL"), Mockito.anyBoolean()))
            .thenAnswer { invocation ->
                captured.add(invocation.getArgument(0))
                appliedResult()
            }

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"keywords\":[\"ai\"],\"sources\":[\"OPENALEX\"]}"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.mode").value("CONTINUOUS"))
            .andExpect(jsonPath("$.pipelineId").value(1))
            .andExpect(jsonPath("$.phase").value("QUEUED"))
            .andExpect(jsonPath("$.executionId").doesNotExist())

        mockMvc.perform(post("/api/expert-discovery/run/by-keyword")
            .param("keywords", "ai")
            .param("sources", "OPENALEX"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.mode").value("CONTINUOUS"))
            .andExpect(jsonPath("$.message").value("已受理，等待执行"))

        assertEquals(2, captured.size)
        assertEquals(SubjectScopeCatalog.RND_TARGET, captured[0].subjectScope)
        assertEquals(captured[0], captured[1])
        Mockito.verify(discoveryService, Mockito.never())
            .discover(anyCriteria(), anyValue("MANUAL"), Mockito.anyBoolean())
        Mockito.verify(progressStore, Mockito.never())
            .tryStartWithToken(anyValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0)))
    }

    @Test
    fun `a different query is 409 while a repeated identical query is idempotent (I-2)`() {
        usePipeline()
        Mockito.`when`(pipeline.launch(anyCriteria(), anyValue("MANUAL"), Mockito.anyBoolean()))
            .thenReturn(
                PipelineLaunchResult(
                    applied = false, pipelineId = 1L, state = PipelineDesiredState.PAUSED, phase = PipelinePhase.QUEUED,
                    desiredState = PipelineDesiredState.PAUSED, queryHash = null, executionId = null, resumed = false,
                    rejection = PipelineRejection(PipelineRejectionReason.QUERY_CONFLICT, "已有不同的查询仍在推进")
                )
            )

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"keywords\":[\"other\"]}"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.reason").value("QUERY_CONFLICT"))
            .andExpect(jsonPath("$.message").value("已有不同的查询仍在推进"))

        Mockito.`when`(pipeline.launch(anyCriteria(), anyValue("MANUAL"), Mockito.anyBoolean()))
            .thenReturn(appliedResult().copy(resumed = true))
        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"keywords\":[\"other\"]}"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.resumed").value(true))
    }

    @Test
    fun `a configuration that cannot be persisted is 503 and the screen stays retryable (I-2)`() {
        usePipeline()
        Mockito.`when`(pipeline.launch(anyCriteria(), anyValue("MANUAL"), Mockito.anyBoolean()))
            .thenThrow(RuntimeException("mysql down"))

        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("深度发现配置未保存成功，请稍后重试"))
    }

    @Test
    fun `pipeline status exposes the 02 snapshot with honest texts and no legacy binding (I-2, I-5)`() {
        usePipeline()
        Mockito.`when`(pipeline.status()).thenReturn(
            status(
                state = PipelinePhase.WAITING,
                queryHash = "hash-1",
                currentExecutionId = null,
                waitReasons = listOf(PipelineWaitReason.DAILY_BUDGET, "SEARCH_FAILED", PipelineWaitReason.QUEUE_FULL)
            )
        )

        mockMvc.perform(get("/api/expert-discovery/pipeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").value("CONTINUOUS"))
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.state").value("WAITING"))
            .andExpect(jsonPath("$.stateText").value("等待下一轮调度"))
            .andExpect(jsonPath("$.waitTexts[0]").value("OpenAlex 官方额度已用尽，等待北京时间 2026-09-23 08:00 重置"))
            .andExpect(jsonPath("$.waitTexts[1]").value("SEARCH_FAILED"))
            .andExpect(jsonPath("$.waitTexts[2]").value("队列已满，正在处理已采集论文"))
            .andExpect(jsonPath("$.status.pipelineId").value(1))
            .andExpect(jsonPath("$.status.queueDepth").value(30))
            .andExpect(jsonPath("$.status.currentExecutionId").doesNotExist())
            .andExpect(jsonPath("$.status.sources[0].source").value("OPENALEX"))
            .andExpect(jsonPath("$.status.budget.officialLimitCredits").value(10000))
            .andExpect(jsonPath("$.status.budget.confirmedSpentCredits").value(227))
            .andExpect(jsonPath("$.status.budget.effectiveRemainingCredits").value(9771))
            .andExpect(jsonPath("$.status.budget.accountScope").value("acc"))
            .andExpect(jsonPath("$.status.budget.apiKey").doesNotExist())
    }

    @Test
    fun `pipeline status without a saved query is PAUSED and never impersonates a historical task (I-2, I-5)`() {
        usePipeline()
        Mockito.`when`(pipeline.status()).thenReturn(
            status(state = PipelineDesiredState.PAUSED, queryHash = null, currentExecutionId = null, waitReasons = emptyList())
        )

        mockMvc.perform(get("/api/expert-discovery/pipeline"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.state").value("PAUSED"))
            .andExpect(jsonPath("$.stateText").value("已暂停，需手动恢复"))
            .andExpect(jsonPath("$.status.queryHash").doesNotExist())
            .andExpect(jsonPath("$.status.currentExecutionId").doesNotExist())
    }

    @Test
    fun `explicit resume continues only a saved query (I-3)`() {
        usePipeline()
        Mockito.`when`(pipeline.resume()).thenReturn(
            PipelineLaunchResult(
                applied = false, pipelineId = 1L, state = PipelineDesiredState.PAUSED, phase = PipelinePhase.QUEUED,
                desiredState = PipelineDesiredState.PAUSED, queryHash = null, executionId = null, resumed = false,
                rejection = PipelineRejection(PipelineRejectionReason.NOT_CONFIGURED, "尚未配置任何深度发现查询，无法恢复")
            )
        )

        mockMvc.perform(post("/api/expert-discovery/pipeline/resume"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.reason").value("NOT_CONFIGURED"))
            .andExpect(jsonPath("$.message").value("尚未配置任何深度发现查询，无法恢复"))

        Mockito.`when`(pipeline.resume()).thenReturn(appliedResult(executionId = 88L).copy(resumed = true))
        mockMvc.perform(post("/api/expert-discovery/pipeline/resume"))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.resumed").value(true))
            .andExpect(jsonPath("$.executionId").value(88))
    }
}

/**
 * c3（I-1）：`pipeline-enabled=true` 却没有 02 协调者（装配缺失）时，入口必须**明确失败**（503），
 * 绝不在操作端不知情的情况下回退到旧同步发现。
 */
@WebMvcTest(
    controllers = [ExpertDiscoveryController::class],
    properties = ["talent-introduction.expert-discovery.pipeline-enabled=true"]
)
@EnableConfigurationProperties(EuropePmcProperties::class, ExpertDiscoveryProperties::class)
class ExpertDiscoveryPipelineUnavailableMvcTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun <T> anyValue(defaultValue: T): T = Mockito.any<T>() ?: defaultValue

    @MockBean
    private lateinit var discoveryService: ExpertDiscoveryService

    @MockBean
    private lateinit var taskExecutionService: TaskExecutionService

    @MockBean
    private lateinit var progressStore: TaskProgressStore

    @MockBean(name = "enrichmentExecutor")
    private lateinit var enrichmentExecutor: Executor

    @MockBean
    private lateinit var openAlexProvider: ObjectProvider<OpenAlexDataSource>

    @MockBean
    private lateinit var crossrefProvider: ObjectProvider<CrossrefDataSource>

    @MockBean
    private lateinit var arxivProvider: ObjectProvider<ArxivDataSource>

    @MockBean
    private lateinit var pmcOaProvider: ObjectProvider<PmcOaDataSource>

    @MockBean
    private lateinit var orcidProvider: ObjectProvider<OrcidDataSource>

    @MockBean
    private lateinit var coreProvider: ObjectProvider<CoreDataSource>

    @Test
    fun `an enabled switch without the coordinator fails loudly instead of falling back (I-1)`() {
        mockMvc.perform(post("/api/expert-discovery/run")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.mode").value("CONTINUOUS"))
            .andExpect(jsonPath("$.message").value(
                "深度发现新模式不可用：pipeline-enabled=true 但 DiscoveryPipelineService 未装配"
            ))

        mockMvc.perform(get("/api/expert-discovery/pipeline"))
            .andExpect(status().isServiceUnavailable)

        Mockito.verify(discoveryService, Mockito.never())
            .discover(anyValue(PaperSearchCriteria()), anyValue("MANUAL"), Mockito.anyBoolean())
        Mockito.verify(progressStore, Mockito.never())
            .tryStartWithToken(anyValue("EXPERT_DISCOVERY"), anyValue(TaskProgress("", "", 0, 0, 0)))
    }
}
