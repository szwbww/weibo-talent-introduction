package com.weibo.talentintroduction.expert.service

import com.weibo.talentintroduction.config.ExpertClassificationProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertType
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.function.Supplier

/**
 * 子计划 04 增量调度测试（I4-1 ~ I4-4）。
 *
 * 使用固定 properties 与同步 fake executor：请求构造/互斥跳过/token 绑定/异常终态/executor 拒绝
 * 全部在调用线程内完成断言；context 测试覆盖默认 disabled 无 bean。
 */
class ExpertClassificationSchedulerTest {

    private lateinit var properties: ExpertClassificationProperties
    private lateinit var backfillService: ExpertClassificationBackfillService
    private lateinit var taskExecutionService: TaskExecutionService
    private lateinit var progressStore: TaskProgressStore

    private val synchronousExecutor = Executor { it.run() }
    private val rejectingExecutor = Executor { throw RejectedExecutionException("pool full") }

    private var capturedRequest: ExpertClassificationBackfillRequest? = null
    private var capturedInitialProgress: TaskProgress? = null

    @BeforeEach
    fun setUp() {
        properties = ExpertClassificationProperties()
        backfillService = Mockito.mock(ExpertClassificationBackfillService::class.java)
        taskExecutionService = Mockito.mock(TaskExecutionService::class.java)
        progressStore = Mockito.mock(TaskProgressStore::class.java)
    }

    private fun scheduler(executor: Executor = synchronousExecutor): ExpertClassificationScheduler =
        ExpertClassificationScheduler(properties, backfillService, taskExecutionService, progressStore, executor)

    private fun <T> anyValue(defaultValue: T): T =
        Mockito.any<T>() ?: defaultValue

    private fun <T : Any> eqValue(value: T): T =
        Mockito.eq(value) ?: value

    private fun stubTryStart(started: Boolean, token: Long = -12345L) {
        Mockito.`when`(
            progressStore.tryStartWithToken(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                anyValue(TaskProgress("", "", 0, 0, 0))
            )
        ).thenAnswer { invocation ->
            capturedInitialProgress = invocation.getArgument<TaskProgress>(1)
            Pair(started, token)
        }
    }

    private fun stubBackfillRun(blockResult: ExpertClassificationBackfillResult) {
        Mockito.`when`(
            backfillService.run(
                anyValue(ExpertClassificationBackfillRequest()),
                anyValue(0L)
            )
        ).thenReturn(blockResult)
    }

    /** 同步执行：调用 onStarted 后执行 block，并捕获传给 runAndRecordWithResult 的请求。 */
    private fun stubRunAndRecordInline(blockResult: ExpertClassificationBackfillResult) {
        Mockito.`when`(
            taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                eqValue("SCHEDULED"),
                anyValue(Any()),
                anyValue<(Long) -> Unit> { },
                Mockito.isNull(),
                anyValue { blockResult }
            )
        ).thenAnswer { invocation ->
            capturedRequest = invocation.getArgument<ExpertClassificationBackfillRequest>(2)
            val onStarted = invocation.getArgument<((Long) -> Unit)?>(3)
            onStarted?.invoke(100L)
            val block = invocation.getArgument<() -> ExpertClassificationBackfillResult>(5)
            Pair(taskExecution(), block())
        }
    }

    private fun result() = ExpertClassificationBackfillResult(
        level = ExpertIndexLevel.CANDIDATE,
        mode = BackfillMode.EXECUTE,
        policyVersion = "rnd-v2-2026",
        scanned = 5,
        byType = mapOf(ExpertType.PRODUCTION_RND.name to 1L),
        writeSuccess = 5,
        writeNoop = 0,
        writeFailure = 0,
        skippedMissingDocId = 0,
        reasonCounts = emptyMap(),
        wasCancelled = false
    )

    private fun taskExecution() = TaskExecution(
        id = 100L,
        taskType = ExpertClassificationBackfillService.TASK_TYPE,
        triggerType = "SCHEDULED",
        status = "RUNNING",
        requestPayload = "{}",
        resultSummary = null,
        startedAt = LocalDateTime.now(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Test
    fun `fixed request CANDIDATE EXECUTE rnd-v2-2026 onlyPending with property bounds (I4-2 I4-4)`() {
        properties = ExpertClassificationProperties(batchSize = 1000, delayMs = 5000, maxDocsPerRun = 12345L)
        stubTryStart(started = true, token = -12345L)
        stubBackfillRun(result())
        stubRunAndRecordInline(result())

        scheduler().scheduleIncremental()

        // I4-2：请求逐字段断言——只 CANDIDATE + EXECUTE + onlyPending=true，
        // 模型无 force 字段；level 固定 CANDIDATE，不可能扫描 RAW/APPLICATION。
        val request = capturedRequest!!
        assertThat(request.level).isEqualTo(ExpertIndexLevel.CANDIDATE)
        assertThat(request.mode).isEqualTo(BackfillMode.EXECUTE)
        assertThat(request.version).isEqualTo("rnd-v2-2026")
        assertThat(request.onlyPending).isTrue()
        assertThat(request.confirmation).isEqualTo("EXECUTE_CANDIDATE:rnd-v2-2026")
        // I4-4：batch/delay/maxDocs 取自 properties（此处为边界值）
        assertThat(request.batchSize).isEqualTo(1000)
        assertThat(request.delayMs).isEqualTo(5000)
        assertThat(request.maxDocs).isEqualTo(12345L)

        // 初始进度 details 同样固定为 CANDIDATE/EXECUTE/onlyPending=true
        val initial = capturedInitialProgress!!
        assertThat(initial.details).containsEntry("level", "CANDIDATE")
        assertThat(initial.details).containsEntry("mode", "EXECUTE")
        assertThat(initial.details).containsEntry("onlyPending", true)
        assertThat(initial.details).containsEntry("policyVersion", "rnd-v2-2026")

        // I4-3：token 绑定 + 结束后清理（与人工路径共用 taskType）
        Mockito.verify(progressStore)
            .bindExecutionId(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(-12345L), eqValue(100L))
        Mockito.verify(progressStore)
            .clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(100L))
        Mockito.verify(taskExecutionService)
            .runAndRecordWithResult<ExpertClassificationBackfillResult>(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                eqValue("SCHEDULED"),
                anyValue(Any()),
                anyValue<(Long) -> Unit> { },
                Mockito.isNull(),
                anyValue { result() }
            )
    }

    @Test
    fun `skip when lock held by manual backfill, no executor submission (I4-3)`() {
        stubTryStart(started = false, token = -42L)

        scheduler().scheduleIncremental()

        // 抢锁失败：不提交 executor、不建第二个 execution、不写 progress/不清理
        Mockito.verify(taskExecutionService, Mockito.never())
            .runAndRecordWithResult<ExpertClassificationBackfillResult>(
                anyString(), anyString(), anyValue(Any()), anyValue<(Long) -> Unit> { }, Mockito.isNull(), anyValue { result() }
            )
        Mockito.verify(progressStore, Mockito.never()).bindExecutionId(anyString(), anyLong(), anyLong())
        Mockito.verify(progressStore, Mockito.never()).update(anyString(), anyValue(TaskProgress("", "", 0, 0, 0)), anyValue(0L))
        Mockito.verify(progressStore, Mockito.never()).clearExecutionContext(anyString(), anyLong())
    }

    @Test
    fun `execution exception records FAILED and clears bound context (I4-3)`() {
        stubTryStart(started = true, token = -12345L)
        Mockito.`when`(
            taskExecutionService.runAndRecordWithResult<ExpertClassificationBackfillResult>(
                eqValue(ExpertClassificationBackfillService.TASK_TYPE),
                eqValue("SCHEDULED"),
                anyValue(Any()),
                anyValue<(Long) -> Unit> { },
                Mockito.isNull(),
                anyValue { result() }
            )
        ).thenAnswer { invocation ->
            invocation.getArgument<((Long) -> Unit)?>(3)?.invoke(100L)
            throw RuntimeException("boom")
        }

        scheduler().scheduleIncremental()

        val expected = TaskProgress(
            taskType = ExpertClassificationBackfillService.TASK_TYPE,
            status = "FAILED",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 50000,
            message = "boom"
        )
        Mockito.verify(progressStore)
            .update(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(expected), eqValue(100L))
        Mockito.verify(progressStore)
            .clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(100L))
    }

    @Test
    fun `executor rejection clears pending token and records FAILED (I4-3)`() {
        stubTryStart(started = true, token = -12345L)

        scheduler(rejectingExecutor).scheduleIncremental()

        val expected = TaskProgress(
            taskType = ExpertClassificationBackfillService.TASK_TYPE,
            status = "FAILED",
            batchNumber = 0,
            processedCount = 0,
            totalCount = 0,
            message = "启动失败: pool full"
        )
        Mockito.verify(progressStore)
            .update(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(expected), eqValue(-12345L))
        Mockito.verify(progressStore)
            .clearExecutionContext(eqValue(ExpertClassificationBackfillService.TASK_TYPE), eqValue(-12345L))
        Mockito.verify(taskExecutionService, Mockito.never())
            .runAndRecordWithResult<ExpertClassificationBackfillResult>(
                anyString(), anyString(), anyValue(Any()), anyValue<(Long) -> Unit> { }, Mockito.isNull(), anyValue { result() }
            )
    }

    @Test
    fun `properties accept boundary values and reject out-of-range (I4-4)`() {
        val min = ExpertClassificationProperties(batchSize = 100, delayMs = 0, maxDocsPerRun = 1L)
        assertThat(min.batchSize).isEqualTo(100)
        assertThat(min.delayMs).isEqualTo(0)
        assertThat(min.maxDocsPerRun).isEqualTo(1L)

        val max = ExpertClassificationProperties(batchSize = 1000, delayMs = 5000, maxDocsPerRun = 200000L)
        assertThat(max.batchSize).isEqualTo(1000)
        assertThat(max.delayMs).isEqualTo(5000)
        assertThat(max.maxDocsPerRun).isEqualTo(200000L)

        assertThatThrownBy { ExpertClassificationProperties(batchSize = 99) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ExpertClassificationProperties(batchSize = 1001) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ExpertClassificationProperties(delayMs = -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ExpertClassificationProperties(delayMs = 5001) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ExpertClassificationProperties(maxDocsPerRun = 0L) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ExpertClassificationProperties(maxDocsPerRun = 200001L) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `scheduler bean absent by default and when disabled, present when enabled (I4-1)`() {
        val runner = ApplicationContextRunner()
            .withUserConfiguration(ExpertClassificationScheduler::class.java)
            .withBean(ExpertClassificationProperties::class.java, Supplier { ExpertClassificationProperties() })
            .withBean(ExpertClassificationBackfillService::class.java, Supplier {
                Mockito.mock(ExpertClassificationBackfillService::class.java)
            })
            .withBean(TaskExecutionService::class.java, Supplier {
                Mockito.mock(TaskExecutionService::class.java)
            })
            .withBean(TaskProgressStore::class.java, Supplier {
                Mockito.mock(TaskProgressStore::class.java)
            })
            .withBean("expertClassificationExecutor", Executor::class.java, Supplier { Executor { it.run() } })

        // 默认（属性缺失）与显式 false：bean 不创建 → 启动零副作用（I4-1）
        runner.run { ctx ->
            assertThat(ctx).hasNotFailed()
            assertThat(ctx.getBeansOfType(ExpertClassificationScheduler::class.java)).isEmpty()
        }
        runner.withPropertyValues("talent-introduction.expert-classification.incremental-enabled=false")
            .run { ctx ->
                assertThat(ctx.getBeansOfType(ExpertClassificationScheduler::class.java)).isEmpty()
            }
        // 显式 true：bean 创建
        runner.withPropertyValues("talent-introduction.expert-classification.incremental-enabled=true")
            .run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx.getBeansOfType(ExpertClassificationScheduler::class.java)).hasSize(1)
            }
    }
}
