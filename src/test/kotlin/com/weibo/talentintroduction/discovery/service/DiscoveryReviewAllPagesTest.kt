package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.discovery.domain.AdmissionConfigSnapshot
import com.weibo.talentintroduction.discovery.domain.AutomaticAdmissionResult
import com.weibo.talentintroduction.discovery.domain.DiscoveryAdmissionStatus
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewIdentity
import com.weibo.talentintroduction.discovery.domain.DiscoveryReviewPrepareRequest
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryAdmission
import com.weibo.talentintroduction.discovery.domain.ExpertDiscoveryReviewItem
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewApplyOutcome
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskExecutionSummaryProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 03（I-1～I-4）：审核所有页的服务层契约。
 *
 * 大样本替代说明（如实记录）：本机没有可用的隔离 ES（`localhost:9200` 不可达），因此
 * **10005 人 fixture 走可注入的 scan 层**：mock 掉 [RestTemplate] 的 ES HTTP 边界，
 * 按真实 scroll 协议（初始 `_search?scroll` + `_search/scroll` + finally DELETE）返回 21 批
 * （20×500 + 5）真实 `_id`/`_source`/`_seq_no`；[DiscoveryReviewScanService] 的真实 scroll/筛选/
 * admission 合并代码逐行走过。持久层用与 SQL 语义一一对应的内存假仓库（批次 CAS、逐 500 领取、
 * 取消只碰未应用项）；真实 MySQL 上的行锁/CAS 由
 * [com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepositoryIT] 覆盖。
 */
class DiscoveryReviewAllPagesTest {

    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
    private val repository = FakeReviewRepository()
    private val policy = Mockito.mock(DiscoveryAdmissionPolicy::class.java)
    private val writer = Mockito.mock(ExpertIndexWriterService::class.java)
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val expertIndexService = Mockito.mock(ExpertIndexService::class.java)
    private val properties = ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app")
    private val clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC)
    private val tasks = FakeTaskExecutions(mapper)
    private val fixture = EsFixture()
    private val scan = DiscoveryReviewScanService(
        repository, policy, writer, restTemplate, properties, expertIndexService, mapper
    )

    private val directExecutor = Executor { it.run() }

    private fun newService(executor: Executor = directExecutor): DiscoveryReviewService = DiscoveryReviewService(
        repository, policy, writer, restTemplate, properties, expertIndexService, mapper,
        clock, tasks, executor, scan
    )

    private val service: DiscoveryReviewService get() = newService()

    @BeforeEach
    fun setUp() {
        Mockito.`when`(expertIndexService.indexName(Mockito.any(ExpertIndexLevel::class.java) ?: ExpertIndexLevel.RAW))
            .thenReturn("raw-index")
        Mockito.`when`(writer.discoveryProfile(anyString(), Mockito.any<Map<String, Any?>>() ?: emptyMap())).thenAnswer { inv ->
            val docId = inv.getArgument<String>(0)
            val source = inv.getArgument<Map<String, Any?>>(1)
            profileOf(docId, source["email"] as? String)
        }
        Mockito.`when`(policy.evaluate(Mockito.any(ExpertProfile::class.java) ?: profileOf("doc", null)))
            .thenReturn(needsReview())
        Mockito.`when`(
            writer.readDiscoveryDocument(Mockito.any(ExpertIndexLevel::class.java) ?: ExpertIndexLevel.RAW, anyString())
        ).thenAnswer { inv ->
                val docId = inv.getArgument<String>(1)
                ExpertIndexWriterService.DiscoverySnapshot(mapOf("email" to fixture.emailOf(docId)), 1L, 1L)
            }
        Mockito.`when`(
            restTemplate.exchange(
                anyString(),
                Mockito.any(HttpMethod::class.java) ?: HttpMethod.POST,
                Mockito.any(),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenAnswer { inv ->
            val url = inv.getArgument<String>(0)
            val method = inv.getArgument<HttpMethod>(1)
            when {
                method == HttpMethod.DELETE -> {
                    fixture.scrollDeleteCount++
                    ResponseEntity.ok(mapper.createObjectNode())
                }
                url.contains("_search/scroll") -> {
                    fixture.scrollCalls++
                    if (fixture.failOnScrollCall == fixture.scrollCalls) {
                        throw IllegalStateException("ES scroll 第二批失败")
                    }
                    ResponseEntity.ok(fixture.nextBatchNode())
                }
                else -> {
                    fixture.searchCalls++
                    ResponseEntity.ok(fixture.nextBatchNode())
                }
            }
        }
    }

    private fun needsReview(): AutomaticAdmissionResult = AutomaticAdmissionResult(
        DiscoveryAdmissionStatus.NEEDS_REVIEW, emptyList(), emptyList(), "2026-10-04.1",
        Instant.parse("2026-10-04T00:00:00Z"),
        AdmissionConfigSnapshot(
            requireOrcid = true, requireValidEmail = true, requireDoctoralDegree = false,
            excludeChineseNationality = false, enableAgeFilter = false, maxAgeExclusive = 70,
            enableHIndexFilter = false, minHIndex = 5, enableCitationFilter = false, minCitationCount = 50,
            enableActivityFilter = false, recentYearsThreshold = 5
        )
    )

    private fun profileOf(docId: String, email: String?): ExpertProfile = ExpertProfile(
        esDocId = docId, orcidId = "0000-$docId", email = email, givenNames = "Ada", familyNames = "Lovelace",
        country = "US", keyword = null, employment = null, institution = "X Univ",
        tags = listOf("discovered"), institutionEvidence = "SOURCE_SHA256:x", filterResult = "PASSED"
    )

    private fun identityOf(docId: String): String =
        DiscoveryReviewIdentity.hash(docId, fixture.emailOf(docId), "Ada", "Lovelace")

    private fun approveRequest() = DiscoveryReviewPrepareRequest(scope = "ALL_MATCHING", action = "APPROVE")

    private fun prepareAll(docs: Int, offset: Int = 1, requestKey: String? = null): DiscoveryReviewBatchStatus {
        fixture.reset((offset until offset + docs).map { "doc-%05d".format(it) })
        return service.prepareAllMatching(approveRequest(), null, null, null, requestKey, "op1")
    }

    // ── I-1：10005 全量完整扫描 ──────────────────────────────────────────────

    @Test
    fun `all-page prepare captures every one of 10005 matches and only then becomes READY`() {
        val status = prepareAll(10005)

        assertEquals(DiscoveryReviewBatchPhase.READY, status.phase)
        assertEquals(10005, status.total)
        assertEquals(10005, status.pending)
        assertEquals(0, status.applied)
        assertNotNull(status.batchHash)
        // 真实 scroll：21 批（20×500 + 5）→ 21 次 admission 批量读取，绝无逐人查库。
        assertEquals(21, fixture.scrollBatches)
        assertEquals(21, repository.admissionBatchReads)
        // finally 一定清理 scroll 上下文。
        assertEquals(1, fixture.scrollDeleteCount)

        // request_payload 只存 batchKey/action/筛选/actor，绝不塞 10005 个 docId。
        val payload = tasks.rows.first { it.taskType == DiscoveryReviewService.TASK_PREPARE }.requestPayload!!
        assertTrue(payload.contains(status.batchKey))
        assertFalse(payload.contains("doc-"))
        assertFalse(payload.contains("docIds"))
    }

    @Test
    fun `a failing second ES batch leaves a PREPARE_FAILED snapshot that cannot be confirmed`() {
        fixture.reset((1..10005).map { "doc-%05d".format(it) })
        fixture.failOnScrollCall = 1 // 第一批成功（500 条），第二批 scroll 抛错

        val status = service.prepareAllMatching(approveRequest(), null, null, null, null, "op1")

        assertEquals(DiscoveryReviewBatchPhase.PREPARE_FAILED, status.phase)
        assertNull(status.batchHash)
        assertEquals(0, status.applied)
        assertEquals(1, fixture.scrollDeleteCount, "失败路径也必须清掉 scroll 上下文")
        assertThrows(DiscoveryReviewConflictException::class.java) {
            service.confirm(status.batchKey, "whatever", "op1")
        }
    }

    @Test
    fun `confirm consumes exactly the stored snapshot and never expands to new discoveries (I-2)`() {
        val status = prepareAll(3)
        val hash = status.batchHash!!
        val before = fixture.searchCalls + fixture.scrollCalls

        val result = service.confirm(status.batchKey, hash, "op1")

        assertEquals(3, result.total)
        assertEquals(3, result.applied)
        assertEquals(before, fixture.searchCalls + fixture.scrollCalls)
        assertEquals(3, service.batchStatus(status.batchKey).total)
    }

    // ── I-3：持久状态决定进度、失败与重试 ────────────────────────────────────

    @Test
    fun `10002 applied and 3 failed survive a restart and retry only re-claims the 3 failures`() {
        val status = prepareAll(10005)
        val hash = status.batchHash!!
        repository.failDocIds = setOf("doc-00001", "doc-00002", "doc-00003")

        val first = service.confirm(status.batchKey, hash, "op1")
        assertEquals(10002, first.applied)
        assertEquals(3, first.failed)

        // “重启”：新的服务实例只从持久明细读状态，计数不清零。
        val restarted = newService()
        val afterRestart = restarted.batchStatus(status.batchKey)
        assertEquals(10002, afterRestart.applied)
        assertEquals(3, afterRestart.failed)
        assertEquals(10005, afterRestart.total)
        assertEquals(
            10005,
            afterRestart.applied + afterRestart.stale + afterRestart.failed + afterRestart.cancelled + afterRestart.pending
        )

        // 明确重试只重领 3 个 FAILED，绝不重复已应用项。
        repository.failDocIds = emptySet()
        restarted.retryBatch(status.batchKey, "op1")
        val finalCounts = restarted.batchStatus(status.batchKey)
        assertEquals(0, finalCounts.failed)
        assertEquals(10005, finalCounts.applied)
        assertEquals(10005, finalCounts.total)
        // 每个身份在 admission 上只被推进一次（绝不重复授权）。
        assertTrue(
            repository.admissions.values.all { it.revision == 1L },
            "每个专家只应被应用一次：${repository.admissions.filterValues { it.revision != 1L }.keys}"
        )

        // 重复确认不新建执行、不新增审核项。
        val applyTasks = tasks.rows.count { it.taskType == DiscoveryReviewService.TASK_APPLY }
        val again = restarted.confirm(status.batchKey, hash, "op1")
        assertEquals(10005, again.applied)
        assertEquals(applyTasks, tasks.rows.count { it.taskType == DiscoveryReviewService.TASK_APPLY })
    }

    @Test
    fun `task success counts come from the persistent details not from the 202 acknowledgement`() {
        val status = prepareAll(4)
        val hash = status.batchHash!!
        repository.failDocIds = setOf("doc-00001")

        service.confirm(status.batchKey, hash, "op1")

        val applyTask = tasks.rows.last { it.taskType == DiscoveryReviewService.TASK_APPLY }
        assertEquals(3, applyTask.successCount)
        assertEquals(1, applyTask.failureCount)
        assertEquals("PARTIAL_SUCCESS", applyTask.status)
    }

    // ── I-4：并发、过期、取消 ────────────────────────────────────────────────

    @Test
    fun `concurrent confirm never double-applies an item`() {
        val status = prepareAll(1000)
        val hash = status.batchHash!!
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures = (1..2).map {
                pool.submit(
                    Callable {
                        start.await(5, TimeUnit.SECONDS)
                        newService().confirm(status.batchKey, hash, "op$it")
                    }
                )
            }
            start.countDown()
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val counts = repository.batchStateCounts(status.batchKey)
        assertEquals(1000, counts["APPLIED"])
        assertTrue(repository.admissions.values.all { it.revision == 1L })
    }

    @Test
    fun `concurrent claims never hand the same item to two executions`() {
        fixture.reset((1..1000).map { "doc-%05d".format(it) })
        val status = service.prepareAllMatching(approveRequest(), null, null, null, null, "op1")
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val futures = listOf(90001L, 90002L).map { exec ->
                pool.submit(
                    Callable {
                        start.await(5, TimeUnit.SECONDS)
                        val claimed = mutableListOf<Long>()
                        while (true) {
                            val batch = repository.claimBatchItems(status.batchKey, exec, 100, includeFailed = false)
                            if (batch.isEmpty()) break
                            claimed += batch.map { it.id }
                        }
                        claimed
                    }
                )
            }
            start.countDown()
            val all = futures.flatMap { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1000, all.size)
            assertEquals(1000, all.toSet().size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a snapshot older than 24 hours is refused on confirm (I-4)`() {
        val status = prepareAll(3)
        val hash = status.batchHash!!
        repository.ageBatch(status.batchKey, LocalDateTime.now(clock).minusHours(25))

        val ex = assertThrows(DiscoveryReviewConflictException::class.java) {
            service.confirm(status.batchKey, hash, "op1")
        }
        assertTrue(ex.message!!.contains("24"))
    }

    @Test
    fun `cancel only affects unapplied items and never regresses applied results (I-4)`() {
        val appliedBatch = prepareAll(4)
        service.confirm(appliedBatch.batchKey, appliedBatch.batchHash!!, "op1")
        assertEquals(4, service.batchStatus(appliedBatch.batchKey).applied)

        val cancelled = service.cancelBatch(appliedBatch.batchKey, "op1")
        assertEquals(4, cancelled.applied)
        assertEquals(0, cancelled.cancelled)

        val pendingBatch = prepareAll(3, offset = 100)
        val cancelledPending = service.cancelBatch(pendingBatch.batchKey, "op1")
        assertEquals(DiscoveryReviewBatchPhase.CANCELLED, cancelledPending.phase)
        assertEquals(3, cancelledPending.cancelled)
        assertEquals(0, cancelledPending.applied)
    }

    @Test
    fun `all-page only collects NEEDS_REVIEW and never overwrites HOLD or approved experts (I-2)`() {
        val docs = listOf("doc-00001", "doc-00002", "doc-00003", "doc-00004")
        fixture.reset(docs)
        repository.seedAdmission("doc-00002", identityOf("doc-00002"), "HOLD")
        repository.seedAdmission("doc-00003", identityOf("doc-00003"), "MANUAL_APPROVED")

        val status = service.prepareAllMatching(approveRequest(), null, null, null, null, "op1")

        assertEquals(2, status.total)
        val collected = repository.findItemsByBatch(status.batchKey).map { it.expertDocId }.toSet()
        assertEquals(setOf("doc-00001", "doc-00004"), collected)
        assertEquals("HOLD", repository.findAdmission("doc-00002")!!.decision)
        assertEquals("MANUAL_APPROVED", repository.findAdmission("doc-00003")!!.decision)
    }

    @Test
    fun `the same request key with the same filter is idempotent and a different filter conflicts`() {
        fixture.reset(listOf("doc-00001", "doc-00002"))
        val first = service.prepareAllMatching(approveRequest(), null, null, null, "window-1", "op1")

        val second = service.prepareAllMatching(approveRequest(), null, null, null, "window-1", "op1")
        assertEquals(first.batchKey, second.batchKey)
        assertEquals(1, tasks.rows.count { it.taskType == DiscoveryReviewService.TASK_PREPARE })

        assertThrows(DiscoveryReviewConflictException::class.java) {
            service.prepareAllMatching(approveRequest(), "other-tag", null, null, "window-1", "op1")
        }
    }

    // ── 测试替身：真实 scroll 协议 fixture ───────────────────────────────────

    private inner class EsFixture {
        val docs = mutableListOf<String>()
        var cursor = 0
        var failOnScrollCall: Int? = null
        var scrollCalls = 0
        var searchCalls = 0
        var scrollDeleteCount = 0
        var scrollBatches = 0

        fun reset(docIds: List<String>) {
            docs.clear()
            docs += docIds
            cursor = 0
            failOnScrollCall = null
            scrollCalls = 0
            searchCalls = 0
            scrollDeleteCount = 0
            scrollBatches = 0
        }

        fun emailOf(docId: String): String = "ada-$docId@example.org"

        fun nextBatchNode(): JsonNode {
            val from = cursor
            val to = minOf(from + 500, docs.size)
            if (to > from) scrollBatches++
            val arr = mapper.createArrayNode()
            for (i in from until to) {
                val docId = docs[i]
                val hit = mapper.createObjectNode()
                hit.put("_id", docId)
                hit.put("_seq_no", 1L)
                hit.put("_primary_term", 1L)
                val source = mapper.createObjectNode()
                source.put("email", emailOf(docId))
                source.putArray("tags").add("discovered")
                hit.set<JsonNode>("_source", source)
                arr.add(hit)
            }
            cursor = to
            val root = mapper.createObjectNode()
            root.put("_scroll_id", "scroll-$cursor")
            val hits = root.putObject("hits")
            hits.putObject("total").put("value", docs.size.toLong())
            hits.set<JsonNode>("hits", arr)
            return root
        }
    }

    // ── 测试替身：与 SQL 语义一一对应的内存仓库 ─────────────────────────────

    private inner class FakeReviewRepository : DiscoveryReviewRepository(JdbcTemplate()) {
        val admissions = mutableMapOf<String, ExpertDiscoveryAdmission>()
        val items = linkedMapOf<Long, ExpertDiscoveryReviewItem>()
        var admissionBatchReads = 0
        var failDocIds: Set<String> = emptySet()
        private var itemSeq = 0L

        fun seedAdmission(docId: String, identityHash: String, decision: String) {
            admissions[docId] = ExpertDiscoveryAdmission(
                docId, identityHash, decision, 0L, null, "v", null, LocalDateTime.now(clock)
            )
        }

        fun ageBatch(batchKey: String, createdAt: LocalDateTime) {
            items.replaceAll { _, item -> if (item.batchKey == batchKey) item.copy(createdAt = createdAt) else item }
        }

        @Synchronized
        override fun initializeAdmission(
            expertDocId: String, identityHash: String, decision: String, policyVersion: String?, now: LocalDateTime
        ) {
            admissions.putIfAbsent(
                expertDocId,
                ExpertDiscoveryAdmission(expertDocId, identityHash, decision, 0L, null, policyVersion, now, now)
            )
        }

        override fun findAdmission(expertDocId: String): ExpertDiscoveryAdmission? = admissions[expertDocId]

        @Synchronized
        override fun findAdmissions(expertDocIds: Collection<String>): List<ExpertDiscoveryAdmission> {
            admissionBatchReads++
            return expertDocIds.distinct().mapNotNull { admissions[it] }
        }

        override fun findItemsByIds(ids: Collection<Long>): List<ExpertDiscoveryReviewItem> =
            ids.distinct().mapNotNull { items[it] }

        @Synchronized
        override fun insertItem(
            batchKey: String, expertDocId: String, sourceLevel: String, identityHash: String,
            snapshotHash: String, expectedRevision: Long, action: String, state: String,
            snapshotJson: String, reasonSnapshotJson: String?, actor: String, note: String?,
            previousItemId: Long?, executionId: Long?, now: LocalDateTime
        ): Long {
            val id = ++itemSeq
            items[id] = ExpertDiscoveryReviewItem(
                id, batchKey, expertDocId, sourceLevel, identityHash, snapshotHash, expectedRevision,
                action, state, snapshotJson, reasonSnapshotJson, actor, note, previousItemId,
                executionId, null, now, null, null
            )
            return id
        }

        override fun findItem(id: Long): ExpertDiscoveryReviewItem? = items[id]

        override fun findItemsByBatch(batchKey: String): List<ExpertDiscoveryReviewItem> =
            items.values.filter { it.batchKey == batchKey }.sortedBy { it.id }

        override fun findItemsByBatchPage(batchKey: String, afterId: Long, limit: Int): List<ExpertDiscoveryReviewItem> =
            findItemsByBatch(batchKey).filter { it.id > afterId }.take(limit)

        override fun batchStateCounts(batchKey: String): Map<String, Int> =
            findItemsByBatch(batchKey).groupingBy { it.state }.eachCount()

        override fun findHistory(expertDocId: String, limit: Int): List<ExpertDiscoveryReviewItem> =
            items.values.filter { it.expertDocId == expertDocId }.sortedByDescending { it.id }.take(limit)

        @Synchronized
        override fun markBatchReady(batchKey: String): Int {
            var changed = 0
            items.replaceAll { _, item ->
                if (item.batchKey == batchKey && item.state == "STAGED") {
                    changed++
                    item.copy(state = "READY")
                } else item
            }
            return changed
        }

        override fun findBatchCreatedAt(batchKey: String): LocalDateTime? =
            findItemsByBatch(batchKey).minOfOrNull { it.createdAt }

        @Synchronized
        override fun claimBatchItems(
            batchKey: String, executionId: Long, limit: Int, includeFailed: Boolean
        ): List<ExpertDiscoveryReviewItem> {
            val allowed = if (includeFailed) setOf("STAGED", "READY", "APPLYING", "FAILED") else setOf("STAGED", "READY")
            val claimed = mutableListOf<ExpertDiscoveryReviewItem>()
            for (item in findItemsByBatch(batchKey)) {
                if (item.state !in allowed) continue
                val updated = item.copy(state = "APPLYING", executionId = executionId)
                items[item.id] = updated
                claimed += updated
                if (claimed.size >= limit) break
            }
            return claimed
        }

        @Synchronized
        override fun cancelUnappliedItems(batchKey: String): Int {
            var changed = 0
            items.replaceAll { _, item ->
                if (item.batchKey == batchKey && item.state in setOf("STAGED", "READY", "APPLYING")) {
                    changed++
                    item.copy(state = "CANCELLED")
                } else item
            }
            return changed
        }

        @Synchronized
        override fun applyItem(
            itemId: Long, currentIdentityHash: String, decision: String, policyVersion: String?, now: LocalDateTime
        ): DiscoveryReviewApplyOutcome {
            val item = items[itemId] ?: return DiscoveryReviewApplyOutcome.NOT_FOUND
            if (item.state == "APPLIED") return DiscoveryReviewApplyOutcome.ALREADY_APPLIED
            if (item.state !in setOf("STAGED", "READY", "APPLYING")) return DiscoveryReviewApplyOutcome.SKIPPED
            if (item.expertDocId in failDocIds) throw IllegalStateException("injected apply failure for ${item.expertDocId}")
            val admission = admissions[item.expertDocId]
                ?: run {
                    items[itemId] = item.copy(state = "STALE", errorCode = "ADMISSION_MISSING")
                    return DiscoveryReviewApplyOutcome.STALE
                }
            if (admission.revision != item.expectedRevision || admission.identityHash != currentIdentityHash) {
                items[itemId] = item.copy(state = "STALE", errorCode = "REVISION_OR_IDENTITY_CHANGED")
                return DiscoveryReviewApplyOutcome.STALE
            }
            admissions[item.expertDocId] = admission.copy(
                identityHash = currentIdentityHash, decision = decision,
                revision = admission.revision + 1, decisionItemId = itemId, updatedAt = now
            )
            items[itemId] = item.copy(state = "APPLIED", confirmedAt = now, appliedAt = now)
            return DiscoveryReviewApplyOutcome.APPLIED
        }

        @Synchronized
        override fun markItemFailed(itemId: Long, errorCode: String, now: LocalDateTime) {
            items[itemId] = items[itemId]!!.copy(state = "FAILED", errorCode = errorCode, confirmedAt = now)
        }
    }

    // ── 测试替身：持久 task_execution 的内存实现 ────────────────────────────

    private class FakeTaskExecutions(private val mapper: ObjectMapper) : TaskExecutionService(
        Mockito.mock(TaskExecutionRepository::class.java), mapper, MailSchedulingProperties()
    ) {
        val rows = mutableListOf<TaskExecution>()
        private var seq = 0L

        override fun <T : Any?> runAndRecordWithResult(
            taskType: String,
            triggerType: String,
            request: Any,
            onStarted: ((executionId: Long) -> Unit)?,
            batchConfigId: Long?,
            block: () -> T
        ): Pair<TaskExecution, T> {
            val running = newRunning(taskType, triggerType, mapper.writeValueAsString(request))
            onStarted?.invoke(running.id!!)
            return try {
                val result = block()
                val (status, success, failure) = terminalOf(result)
                val finished = running.copy(
                    status = status, resultSummary = mapper.writeValueAsString(result),
                    successCount = success, failureCount = failure, finishedAt = LocalDateTime.now()
                )
                replace(finished)
                finished to result
            } catch (ex: Exception) {
                replace(running.copy(status = "FAILED", failureCount = 1, errorMessage = ex.message, finishedAt = LocalDateTime.now()))
                throw ex
            }
        }

        override fun listRecentByTaskType(taskType: String, limit: Int): List<TaskExecution> =
            synchronized(this) {
                rows.filter { it.taskType == taskType }.sortedByDescending { it.id }.take(limit)
            }

        @Synchronized
        private fun newRunning(taskType: String, triggerType: String, payload: String): TaskExecution {
            val id = ++seq
            val now = LocalDateTime.now()
            return TaskExecution(
                id = id, taskType = taskType, triggerType = triggerType, status = "RUNNING",
                requestPayload = payload, resultSummary = null,
                startedAt = now, createdAt = now, updatedAt = now
            ).also { rows += it }
        }

        @Synchronized
        private fun replace(next: TaskExecution) {
            val index = rows.indexOfFirst { it.id == next.id }
            if (index >= 0) rows[index] = next
        }

        private fun terminalOf(result: Any?): Triple<String, Int, Int> = when (result) {
            is TaskExecutionSummaryProvider -> Triple(
                result.taskFinalStatus
                    ?: if (result.taskFailureCount > 0 && result.taskSuccessCount > 0) "PARTIAL_SUCCESS"
                    else if (result.taskFailureCount > 0) "FAILED" else "SUCCESS",
                result.taskSuccessCount, result.taskFailureCount
            )
            else -> Triple("SUCCESS", 0, 0)
        }
    }
}
