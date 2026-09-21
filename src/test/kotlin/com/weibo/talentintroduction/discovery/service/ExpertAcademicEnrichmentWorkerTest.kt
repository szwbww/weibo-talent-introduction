package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.config.RequestKind
import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgress
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * c8（08）：自动补全 worker 的调度/互斥/记账契约。
 * 断言落在持久化与请求边界上（任务锁、任务记录、领取上限、释放时机），不是内部调用顺序。
 */
class ExpertAcademicEnrichmentWorkerTest {
    private lateinit var discoveryService: ExpertDiscoveryService
    private lateinit var progressStore: TaskProgressStore
    private lateinit var autoEnrichmentExecutor: Executor
    private lateinit var repository: TaskExecutionRepository
    private val objectMapper = ObjectMapper()

    private val enabledProperties = ExpertDiscoveryProperties(
        enabled = true, autoEnrichmentEnabled = true, autoEnrichmentBatchSize = 100
    )

    @BeforeEach
    fun setUp() {
        discoveryService = Mockito.mock(ExpertDiscoveryService::class.java)
        progressStore = Mockito.mock(TaskProgressStore::class.java)
        repository = Mockito.mock(TaskExecutionRepository::class.java)
        autoEnrichmentExecutor = Mockito.mock(Executor::class.java)
        Mockito.doAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }.`when`(autoEnrichmentExecutor).execute(Mockito.any(Runnable::class.java))
        // R-3（V-3）：默认「有到期任务」，进入取锁/领取流程；空闲探针的用例自行改写为 false。
        Mockito.doReturn(true).`when`(discoveryService).hasDueEnrichmentJobs()
    }

    private fun worker(props: ExpertDiscoveryProperties = enabledProperties) = ExpertAcademicEnrichmentWorker(
        props, discoveryService,
        TaskExecutionService(repository, objectMapper, MailSchedulingProperties(autoReplyAllCron = "-")),
        progressStore, autoEnrichmentExecutor
    )

    private fun anyTaskProgress(): TaskProgress =
        Mockito.any(TaskProgress::class.java) ?: TaskProgress("", "", 0, 0, 0)

    private fun anyRequestKind(): RequestKind =
        Mockito.any(RequestKind::class.java) ?: RequestKind.NEW_ENRICHMENT

    private fun <T : Any> eqValue(value: T): T = Mockito.eq(value) ?: value

    private fun startedToken(): Pair<Boolean, Long> = Pair(true, -11L)

    private fun job(id: Long, docId: String, source: String, token: String) = ExpertAcademicEnrichmentJob(
        id = id, expertDocId = docId, source = source, status = ExpertAcademicEnrichmentJob.STATUS_RUNNING,
        attempts = 0, nextAttemptAt = LocalDateTime.now(), leaseToken = token,
        leaseUntil = LocalDateTime.now().plusMinutes(10)
    )

    private fun savedExecutions(): List<TaskExecution> {
        val captor = ArgumentCaptor.forClass(TaskExecution::class.java)
        Mockito.verify(repository, Mockito.atLeastOnce()).save(captor.capture())
        return captor.allValues
    }

    @Test
    fun `开关关闭时一次检查什么都不做（不领取、不建任务记录）`() {
        worker(ExpertDiscoveryProperties(autoEnrichmentEnabled = false)).processDueEnrichmentJobs()

        Mockito.verify(progressStore, Mockito.never())
            .tryStartWithToken(Mockito.anyString(), anyTaskProgress())
        Mockito.verify(discoveryService, Mockito.never()).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.verify(autoEnrichmentExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `自动批次在 EXPERT_ENRICHMENT 任务记录下按 NEW_ENRICHMENT 处理并释放任务锁`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 7L)
            }
        val claimed = listOf(job(1L, "0000-0001", "EUROPE_PMC", "token-1"))
        Mockito.doReturn(claimed).`when`(discoveryService).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.doReturn(
            AutoEnrichmentBatchResult(
                claimed = 1, succeeded = 1,
                bySource = mapOf("EUROPE_PMC" to AutoEnrichmentSourceCounts(enqueued = 1, succeeded = 1))
            )
        ).`when`(discoveryService).processClaimedEnrichmentJobBatch(Mockito.anyList(), anyRequestKind(), Mockito.anyString())

        worker().processDueEnrichmentJobs()

        // 与人工补全入口同一把锁、同一任务类型
        Mockito.verify(progressStore).tryStartWithToken(eqValue("EXPERT_ENRICHMENT"), anyTaskProgress())
        // 领取上限来自配置；自动调用一律 NEW_ENRICHMENT
        Mockito.verify(discoveryService).claimDueEnrichmentJobs(eqValue(100))
        Mockito.verify(discoveryService).processClaimedEnrichmentJobBatch(
            eqValue(claimed), eqValue(RequestKind.NEW_ENRICHMENT), eqValue("EXPERT_ENRICHMENT")
        )
        // I-4：自动补全有独立 EXPERT_ENRICHMENT 任务记录，details 带逐源计数
        val execution = savedExecutions().last()
        assertEquals("EXPERT_ENRICHMENT", execution.taskType)
        assertEquals("SCHEDULED", execution.triggerType)
        assertEquals("SUCCESS", execution.status)
        assertEquals(1, execution.successCount)
        assertTrue(execution.resultSummary!!.contains("\"bySource\""))
        assertTrue(execution.resultSummary!!.contains("EUROPE_PMC"))
        // 批次结束后释放锁，后续 tick（或人工入口）可再次获取
        Mockito.verify(progressStore).clearExecutionContext("EXPERT_ENRICHMENT", 7L)
    }

    @Test
    fun `空闲检查（没有到期任务）不建任务记录也不写进度日志`() {
        // R-3（V-3）：空转的一次检查只做只读探针 —— 不取任务锁（tryStartWithToken 会落孤儿进度行）、
        // 不建 task_execution、不进专用线程。
        Mockito.doReturn(false).`when`(discoveryService).hasDueEnrichmentJobs()

        worker().processDueEnrichmentJobs()

        Mockito.verify(discoveryService).hasDueEnrichmentJobs()
        Mockito.verify(progressStore, Mockito.never())
            .tryStartWithToken(Mockito.anyString(), anyTaskProgress())
        Mockito.verify(progressStore, Mockito.never())
            .update(Mockito.anyString(), anyTaskProgress(), Mockito.any())
        Mockito.verify(progressStore, Mockito.never()).clear(Mockito.anyString())
        Mockito.verify(discoveryService, Mockito.never()).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.verify(autoEnrichmentExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `人工补全持锁时自动检查直接跳过（自动与手动互斥）`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(Pair(false, -11L))

        worker().processDueEnrichmentJobs()

        Mockito.verify(discoveryService, Mockito.never()).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.verify(autoEnrichmentExecutor, Mockito.never()).execute(Mockito.any(Runnable::class.java))
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `没有到期任务时只在专用线程上检查一次，不产生任务记录`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.doReturn(emptyList<ExpertAcademicEnrichmentJob>())
            .`when`(discoveryService).claimDueEnrichmentJobs(Mockito.anyInt())

        worker().processDueEnrichmentJobs()

        Mockito.verify(autoEnrichmentExecutor).execute(Mockito.any(Runnable::class.java))
        Mockito.verify(discoveryService, Mockito.never())
            .processClaimedEnrichmentJobBatch(Mockito.anyList(), anyRequestKind(), Mockito.anyString())
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
        Mockito.verify(progressStore).clearExecutionContext("EXPERT_ENRICHMENT", -11L)
    }

    @Test
    fun `批次失败时如实记 FAILED 并释放任务锁，未完成状态留给租约恢复`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 9L)
            }
        val claimed = listOf(job(1L, "0000-0001", "EUROPE_PMC", "token-1"))
        Mockito.doReturn(claimed).`when`(discoveryService).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.doThrow(IllegalStateException("RAW 文档读取失败"))
            .`when`(discoveryService)
            .processClaimedEnrichmentJobBatch(Mockito.anyList(), anyRequestKind(), Mockito.anyString())

        val captured = mutableListOf<TaskProgress>()
        Mockito.doAnswer { invocation ->
            captured.add(invocation.getArgument(1) as TaskProgress)
            null
        }.`when`(progressStore).update(Mockito.anyString(), anyTaskProgress(), Mockito.any())

        worker().processDueEnrichmentJobs()

        assertEquals("FAILED", savedExecutions().last().status)
        assertEquals("FAILED", captured.single().status)
        assertTrue(captured.single().message!!.contains("RAW 文档读取失败"))
        Mockito.verify(progressStore).clearExecutionContext("EXPERT_ENRICHMENT", 9L)
    }

    @Test
    fun `专用线程拒绝提交时释放任务锁并跳过，不写任何任务记录`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.doThrow(RejectedExecutionException("busy"))
            .`when`(autoEnrichmentExecutor).execute(Mockito.any(Runnable::class.java))

        worker().processDueEnrichmentJobs()

        Mockito.verify(progressStore).clear("EXPERT_ENRICHMENT")
        Mockito.verify(discoveryService, Mockito.never()).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.verify(repository, Mockito.never()).save(Mockito.any(TaskExecution::class.java))
    }

    @Test
    fun `取消发生在领取之前时不领取新任务`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.doReturn(true).`when`(progressStore).isCancelled("EXPERT_ENRICHMENT")

        worker().processDueEnrichmentJobs()

        Mockito.verify(discoveryService, Mockito.never()).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.verify(progressStore).clearExecutionContext("EXPERT_ENRICHMENT", -11L)
    }

    @Test
    fun `批次仍在专用线程上执行时新的检查不排队且不丢锁`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.doNothing()
            .doThrow(RejectedExecutionException("busy"))
            .`when`(autoEnrichmentExecutor).execute(Mockito.any(Runnable::class.java))
        Mockito.doReturn(emptyList<ExpertAcademicEnrichmentJob>())
            .`when`(discoveryService).claimDueEnrichmentJobs(Mockito.anyInt())

        val worker = worker()
        worker.processDueEnrichmentJobs()
        worker.processDueEnrichmentJobs()

        Mockito.verify(autoEnrichmentExecutor, Mockito.times(2)).execute(Mockito.any(Runnable::class.java))
        Mockito.verify(progressStore).clear("EXPERT_ENRICHMENT")
    }

    @Test
    fun `领取上限使用配置的批次大小（尾批不足 100 也照常执行）`() {
        Mockito.`when`(progressStore.tryStartWithToken(Mockito.anyString(), anyTaskProgress()))
            .thenReturn(startedToken())
        Mockito.`when`(repository.save(Mockito.any(TaskExecution::class.java)))
            .thenAnswer { invocation ->
                val execution = invocation.arguments[0] as TaskExecution
                execution.copy(id = execution.id ?: 3L)
            }
        val claimed = listOf(job(1L, "0000-0001", "ORCID", "token-1"))
        Mockito.doReturn(claimed).`when`(discoveryService).claimDueEnrichmentJobs(Mockito.anyInt())
        Mockito.doReturn(AutoEnrichmentBatchResult(claimed = 1, succeeded = 1))
            .`when`(discoveryService).processClaimedEnrichmentJobBatch(Mockito.anyList(), anyRequestKind(), Mockito.anyString())

        worker(enabledProperties.copy(autoEnrichmentBatchSize = 7)).processDueEnrichmentJobs()

        Mockito.verify(discoveryService).claimDueEnrichmentJobs(eqValue(7))
    }
}
