package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.service.QUEUE_PAYLOAD_TOO_LARGE
import com.weibo.talentintroduction.discovery.service.QUEUE_UNIDENTIFIABLE
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.ResultSet
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 持久化采集队列的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由**真实 V133 迁移**建立（不是手写 DDL），因此同时验证迁移契约；H2 不能替代行锁、唯一约束、
 * 条件 CAS 与并发入队的语义，所以本文件是 I-1/I-2/I-3/I-5/I-7/I-8 中「事务/租约/容量/CAS」部分的唯一证据。
 *
 * 覆盖映射：
 * - **I-1**：整页入队与 cursor 推进同事务；显式回滚后 cursor 与 job 都回到原状；租约被接管时整页不写。
 * - **I-2**：`UNIQUE(stream_id, item_key)` 使重复身份只留一行；无标识条目、超限条目以 FAILED 落库且身份字段完整。
 * - **I-3**：双连接 claim 唯一；旧 token 的终态/续租/保存影响 0 行；过期租约可重新领取；
 *   generation 不一致时 complete 被拒；重试退避与「第 5 次终止」的持久化形态。
 * - **I-5**：数量或字节任一满 → 整页 `CAPACITY_BLOCKED`（cursor 不推进）；并发入队不越界；
 *   在途结果预留参与容量；清理释放字节而不动累计指标与游标。
 * - **I-7**：`desired_state`/`phase` 生命周期、`generation` 递增、属主租约 CAS、失效属主只清自己。
 * - **I-8**：一次 CAS 最多累加一次；逐单位（论文/ORCID）计数分离；清理不改计数/游标。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 并发 claim / 并发入队必须用真实独立连接：测试级事务会让另一个线程看不到未提交行。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(DiscoveryPaperQueueRepository::class)
class DiscoveryPaperQueueRepositoryIT {

    companion object {
        private val NOW: Instant = Instant.parse("2026-09-22T02:41:17.123Z")
        private val LIMITS = QueueCapacityLimits(
            highWater = 10_000,
            maxBytes = 1_000_000,
            reservedResultBytes = 100
        )

        private class KotlinMySqlContainer(image: String) : MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for the discovery paper queue tests"
            }
            mysql.start()
        }

        @JvmStatic
        @AfterAll
        fun stopMysql() {
            if (mysql.isRunning) mysql.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    @Autowired
    private lateinit var repository: DiscoveryPaperQueueRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @BeforeEach
    fun cleanQueue() {
        jdbcTemplate.update("DELETE FROM discovery_paper_job")
        jdbcTemplate.update("DELETE FROM discovery_collection_stream")
        jdbcTemplate.update("DELETE FROM discovery_pipeline")
        repository.ensurePipeline(NOW)
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private fun launchRunning(queryHash: String = "qh"): PipelineRow {
        val outcome = repository.launch(
            criteriaJson = """{"keywords":["engineering"]}""",
            criteriaVersion = 1,
            queryHash = queryHash,
            rawScanDone = true,
            now = NOW
        )
        assertTrue(outcome is LaunchOutcome.Applied, "launch 必须成功: $outcome")
        return (outcome as LaunchOutcome.Applied).pipeline
    }

    private fun newStream(queryHash: String = "qh", source: String = "OPENALEX"): StreamRow =
        repository.ensureStream(1L, queryHash, source, 1L, """{"keywords":["engineering"]}""", NOW)

    private fun item(
        key: String,
        bytes: Long = 40,
        unit: String = QueueItemUnit.PAPER,
        quality: String = QueueIdentityQuality.DOI,
        downloadable: Boolean = true,
        version: Int = 1
    ) = QueueJobInsert(
        itemKey = key,
        identityQuality = quality,
        unit = unit,
        payloadVersion = version,
        publiclyDownloadable = downloadable,
        metadataJson = """{"title":"$key"}""",
        payloadBytes = bytes
    )

    private fun enqueue(
        stream: StreamRow,
        leaseToken: String,
        items: List<QueueJobInsert>,
        cursorValue: String?,
        exhausted: Boolean = false,
        limits: QueueCapacityLimits = LIMITS,
        now: Instant = NOW
    ): EnqueuePageResult = repository.enqueuePage(
        streamId = stream.id, leaseToken = leaseToken, unit = QueueItemUnit.PAPER,
        items = items, cursorValue = cursorValue, exhausted = exhausted, limits = limits, now = now
    )

    private fun jobRows(): List<Map<String, Any?>> = jdbcTemplate.query(
        "SELECT id, stream_id, item_key, unit, status, attempts, generation, payload_bytes, reserved_result_bytes, completed_at, last_error FROM discovery_paper_job ORDER BY id"
    ) { rs: ResultSet, _: Int ->
        mapOf(
            "id" to rs.getLong("id"),
            "stream_id" to rs.getLong("stream_id"),
            "item_key" to rs.getString("item_key"),
            "unit" to rs.getString("unit"),
            "status" to rs.getString("status"),
            "attempts" to rs.getInt("attempts"),
            "generation" to rs.getLong("generation"),
            "payload_bytes" to rs.getLong("payload_bytes"),
            "reserved" to rs.getLong("reserved_result_bytes"),
            "completed_at" to rs.getTimestamp("completed_at"),
            "last_error" to rs.getString("last_error")
        )
    }

    private fun onlyJob(): Map<String, Any?> = jobRows().single()

    private fun streamRow(id: Long): StreamRow = requireNotNull(repository.findStreamById(id))

    // ==================================================================
    // I-1：整页入队与 cursor 推进同事务
    // ==================================================================

    @Test
    fun `a whole page commits jobs and the cursor in one transaction (I-1)`() {
        launchRunning()
        val stream = newStream()
        val lease = "lease-1"
        assertTrue(repository.claimStreamLease(stream.id, lease, NOW.plusSeconds(120), NOW))

        val result = enqueue(stream, lease, listOf(item("DOI:a"), item("DOI:b")), cursorValue = "page-2")

        assertEquals(EnqueuePageStatus.COMMITTED, result.status)
        assertEquals(2, result.insertedJobs)
        val after = streamRow(stream.id)
        assertEquals("page-2", after.cursorValue)
        assertEquals(StreamCursorState.ACTIVE, after.cursorState)
        assertNull(after.leaseToken, "整页提交必须同时释放采集页租约")
        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(2L, pipeline.queuedPapers)
        assertEquals(2L, pipeline.activeCount)
        assertEquals(2L * LIMITS.reservedResultBytes, pipeline.reservedResultBytes)
        assertEquals(80L, pipeline.payloadBytes, "字节账只累加实际负载 + 在途预留")
    }

    @Test
    fun `an explicitly rolled back page transaction leaves the cursor and the queue untouched (I-1)`() {
        launchRunning()
        val stream = newStream()
        val lease = "lease-rollback"
        assertTrue(repository.claimStreamLease(stream.id, lease, NOW.plusSeconds(120), NOW))
        val tx = TransactionTemplate(transactionManager)

        assertThrows(IllegalStateException::class.java) {
            tx.execute {
                enqueue(stream, lease, listOf(item("DOI:a"), item("DOI:b")), cursorValue = "page-2")
                // 提交前崩溃（例如进程被杀、或后续步骤抛错）。
                throw IllegalStateException("crash before commit")
            }
        }

        val after = streamRow(stream.id)
        assertNull(after.cursorValue, "回滚后 cursor 必须保持原值")
        assertEquals(lease, after.leaseToken, "回滚必须保留采集页租约以便重放同一页")
        assertEquals(0, jobRows().size, "回滚后一条 job 都不得留下")
        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(0L, pipeline.activeCount)
        assertEquals(0L, pipeline.queuedPapers)
        repository.releaseStreamLease(stream.id, lease, NOW)
    }

    @Test
    fun `a page whose lease was taken over is never written and never advances the cursor (I-1)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "mine", NOW.plusSeconds(120), NOW))
        // 另一个采集者抢走了租约（模拟租约过期后被接管）。
        jdbcTemplate.update(
            "UPDATE discovery_collection_stream SET lease_token = 'other' WHERE id = ?", stream.id
        )

        val result = enqueue(stream, "mine", listOf(item("DOI:a")), cursorValue = "page-2")

        assertEquals(EnqueuePageStatus.LEASE_LOST, result.status)
        assertNull(streamRow(stream.id).cursorValue, "租约丢失时整页不写、cursor 不推进")
        assertEquals(0, jobRows().size)
    }

    @Test
    fun `EXHAUSTED is a persisted cursor state that a later page cannot silently reopen (I-1)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null, exhausted = true)
        assertEquals(StreamCursorState.EXHAUSTED, streamRow(stream.id).cursorState)

        // 穷尽之后同一来源不再可被领取（采集者不会重复扫同一来源）。
        assertFalse(
            repository.claimStreamLease(stream.id, "l2", NOW.plusSeconds(120), NOW.plusSeconds(1)),
            "EXHAUSTED 的来源不得再被领取（不因重启/次日/新窗口重置）"
        )
    }

    @Test
    fun `a legacy cursor only seeds a pristine stream (I-1)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.seedStreamCursorIfPristine(stream.id, "LEGACY-9", NOW), "空且无 job 的流可被种子")
        assertEquals("LEGACY-9", streamRow(stream.id).cursorValue)
        assertFalse(repository.seedStreamCursorIfPristine(stream.id, "LEGACY-10", NOW), "已推进过的流不得被覆盖")

        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = "page-2")
        val other = newStream(source = "CROSSREF")
        assertTrue(repository.seedStreamCursorIfPristine(other.id, "LEGACY-11", NOW), "另一个来源的流各自独立种子")
        assertEquals("LEGACY-11", streamRow(other.id).cursorValue)
    }

    // ==================================================================
    // I-2：工作唯一性与版本
    // ==================================================================

    @Test
    fun `the unique key keeps one row per work identity and makes replay free of new capacity (I-2)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        val first = enqueue(stream, "l1", listOf(item("DOI:a"), item("DOI:a"), item("DOI:b")), cursorValue = "page-2")
        assertEquals(2, first.insertedJobs, "页内重复身份必须先按 item_key 去重")
        assertEquals(0, first.duplicateJobs, "页内重复身份在入队前就按 item_key 去重，不算「已存在的重放」")

        // 重放同一页：一条都不新增、容量不增长，但 cursor 仍按页推进。
        val second = newStream(source = "OPENALEX")
        assertEquals(stream.id, second.id)
        assertTrue(repository.claimStreamLease(stream.id, "l2", NOW.plusSeconds(120), NOW))
        val replay = enqueue(stream, "l2", listOf(item("DOI:a"), item("DOI:b")), cursorValue = "page-3")
        assertEquals(0, replay.insertedJobs, "重复身份不占新增容量")
        assertEquals(2, replay.duplicateJobs)
        assertEquals("page-3", streamRow(stream.id).cursorValue)
        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(2L, pipeline.activeCount)
        assertEquals(2L, pipeline.queuedPapers, "重放不得重复计数")
    }

    @Test
    fun `an unknown payload version is stored observably and still repairable (I-2)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a", version = 99)), cursorValue = null)

        val row = onlyJob()
        assertEquals(99, jdbcTemplate.queryForObject(
            "SELECT payload_version FROM discovery_paper_job WHERE item_key = 'DOI:a'", Int::class.java
        ), "未知版本必须原样落库（消费方据此拒绝），不得静默改写")
        assertEquals(QueueJobStatus.PENDING, row["status"])
    }

    @Test
    fun `unidentifiable or oversized items become observable FAILED rows with intact identity (I-2, I-5)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(
            repository.insertFailedItem(
                stream.id, item("PAYLOAD_HASH:deadbeef", bytes = 70_000, quality = QueueIdentityQuality.PAYLOAD_HASH),
                QUEUE_PAYLOAD_TOO_LARGE, LIMITS, NOW
            )
        )
        assertTrue(
            repository.insertFailedItem(
                stream.id, item("RECORD:orphan", unit = QueueItemUnit.RECORD, quality = QueueIdentityQuality.PAYLOAD_HASH),
                QUEUE_UNIDENTIFIABLE, LIMITS, NOW
            )
        )
        assertFalse(
            repository.insertFailedItem(
                stream.id, item("PAYLOAD_HASH:deadbeef"), QUEUE_PAYLOAD_TOO_LARGE, LIMITS, NOW
            ),
            "同一身份不得重复形成诊断条目"
        )

        val rows = jobRows()
        assertEquals(2, rows.size)
        val oversized = rows.first { it["item_key"] == "PAYLOAD_HASH:deadbeef" }
        assertEquals(QueueJobStatus.FAILED, oversized["status"])
        assertEquals(QUEUE_PAYLOAD_TOO_LARGE, oversized["last_error"])
        assertNotNull(oversized["completed_at"])
        assertEquals(0L, oversized["reserved"], "诊断条目不得占用在途结果预留")
        val orcid = rows.first { it["item_key"] == "RECORD:orphan" }
        assertEquals(QueueItemUnit.RECORD, orcid["unit"], "ORCID 记录不得被当成论文")
        assertEquals(QUEUE_UNIDENTIFIABLE, orcid["last_error"])

        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(2L, pipeline.failedItems)
        assertEquals(1L, pipeline.queuedPapers)
        assertEquals(1L, pipeline.queuedRecords, "论文与 ORCID 记录计数分离")
        assertEquals(0L, pipeline.activeCount, "诊断条目从未成为活跃工作")
        assertEquals(0L, pipeline.indexedExperts, "失败条目绝不冒充专家产量")
    }

    // ==================================================================
    // I-3：租约与状态机
    // ==================================================================

    @Test
    fun `two connections cannot claim the same job (I-3)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        val jobId = onlyJob()["id"] as Long

        val barrier = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        val wins = AtomicInteger(0)
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        repeat(2) { index ->
            pool.submit {
                barrier.countDown()
                barrier.await(10, TimeUnit.SECONDS)
                val won = repository.claimJob(
                    jobId, "token-$index", NOW.plusSeconds(120), 0L, NOW.plusSeconds(1)
                )
                results += won
                if (won) wins.incrementAndGet()
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals(2, results.size)
        assertEquals(1, wins.get(), "同一 job 只能被一条连接领取")
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE status = 'RUNNING'", Int::class.java
        ))
    }

    @Test
    fun `an expired lease is recoverable and an old token cannot touch the new attempt (I-3)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        val jobId = onlyJob()["id"] as Long

        assertTrue(repository.claimJob(jobId, "old", NOW.plusSeconds(10), 0L, NOW))
        // 租约到期后另一条连接重新领取（崩溃恢复）。
        assertTrue(repository.claimJob(jobId, "new", NOW.plusSeconds(300), 0L, NOW.plusSeconds(60)))

        assertEquals(0, repository.renewJobLease(jobId, "old", 0L, NOW.plusSeconds(600), NOW.plusSeconds(61)))
        assertFalse(
            repository.saveExtraction(jobId, "old", 0L, """{"emails":[]}""", 20, NOW.plusSeconds(61)),
            "旧 token 不得保存抽取结果"
        )
        assertFalse(
            repository.completeJob(jobId, "old", 0L, QueueJobStatus.SUCCEEDED, 0, NOW, null, NOW.plusSeconds(61)).applied,
            "旧 token 的终态必须影响 0 行"
        )
        assertEquals(QueueJobStatus.RUNNING, onlyJob()["status"])

        assertTrue(repository.saveExtraction(jobId, "new", 0L, """{"emails":[]}""", 20, NOW.plusSeconds(62)))
        assertTrue(
            repository.completeJob(jobId, "new", 0L, QueueJobStatus.SUCCEEDED, 0, NOW, null, NOW.plusSeconds(63)).applied
        )
        assertEquals(QueueJobStatus.SUCCEEDED, onlyJob()["status"])
    }

    @Test
    fun `a stale generation can save an extraction but can never complete (I-3, I-7)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        val jobId = onlyJob()["id"] as Long
        assertTrue(repository.claimJob(jobId, "token", NOW.plusSeconds(120), 0L, NOW))

        // 人工暂停：generation 递增（claim 从此不再放行）。
        val paused = repository.pause(NOW.plusSeconds(1))
        assertEquals(1L, paused.generation)
        assertFalse(
            repository.claimJob(jobId + 1000, "x", NOW.plusSeconds(120), 0L, NOW.plusSeconds(2)),
            "暂停后不得产生新领取"
        )

        // 唯一例外：租约仍有效的旧 job 允许保存可靠抽取结果……
        assertTrue(
            repository.saveExtraction(jobId, "token", 0L, """{"emails":[{"email":"a@b.c"}]}""", 30, NOW.plusSeconds(2)),
            "暂停后仍允许保存已领取工作的可靠抽取结果"
        )
        // ……但 complete 必须被当前 generation 拒绝。
        assertFalse(
            repository.completeJob(jobId, "token", 0L, QueueJobStatus.SUCCEEDED, 0, NOW, null, NOW.plusSeconds(3)).applied,
            "generation 不一致时绝不 complete"
        )
        assertEquals(QueueJobStatus.RUNNING, onlyJob()["status"])
        assertTrue(repository.returnToPending(jobId, "token", 0L, NOW.plusSeconds(10), "MANUAL_PAUSE", NOW.plusSeconds(3)))
        val row = onlyJob()
        assertEquals(QueueJobStatus.PENDING, row["status"])
        assertEquals(0, row["attempts"], "暂停退回不得消耗 attempts")
        assertNotNull(
            jdbcTemplate.queryForObject(
                "SELECT extraction_json FROM discovery_paper_job WHERE id = ?", String::class.java, jobId
            ),
            "退回 PENDING 不得丢掉已保存的抽取结果"
        )
    }

    @Test
    fun `retry backoff and terminal failure are persisted with their reason (I-3)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        val jobId = onlyJob()["id"] as Long
        assertTrue(repository.claimJob(jobId, "t1", NOW.plusSeconds(120), 0L, NOW))

        assertTrue(
            repository.scheduleRetry(jobId, "t1", 0L, 1, NOW.plusSeconds(30), "NETWORK_ERROR", NOW.plusSeconds(1))
        )
        val retry = onlyJob()
        assertEquals(QueueJobStatus.RETRY_WAIT, retry["status"])
        assertEquals(1, retry["attempts"])
        assertEquals("NETWORK_ERROR", retry["last_error"])
        assertNull(retry["completed_at"], "重试等待不是终态")

        // 第 5 次失败：终态 FAILED + 原因 + 完成时刻，且不再可被领取。
        assertTrue(repository.claimJob(jobId, "t2", NOW.plusSeconds(120), 0L, NOW.plusSeconds(31)))
        assertTrue(
            repository.completeJob(
                jobId, "t2", 0L, QueueJobStatus.FAILED, 5, NOW.plusSeconds(31), "NETWORK_ERROR", NOW.plusSeconds(32)
            ).applied
        )
        val failed = onlyJob()
        assertEquals(QueueJobStatus.FAILED, failed["status"])
        assertEquals(5, failed["attempts"])
        assertNotNull(failed["completed_at"])
        assertFalse(repository.claimJob(jobId, "t3", NOW.plusSeconds(120), 0L, NOW.plusSeconds(3600)))
        assertEquals(1L, requireNotNull(repository.findPipeline()).failedItems)
    }

    @Test
    fun `a budget wait returns the job to PENDING without consuming attempts (I-3)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        val jobId = onlyJob()["id"] as Long
        assertTrue(repository.claimJob(jobId, "t1", NOW.plusSeconds(120), 0L, NOW))

        assertTrue(repository.returnToPending(jobId, "t1", 0L, NOW.plusSeconds(600), "DAILY_BUDGET", NOW))

        val row = onlyJob()
        assertEquals(QueueJobStatus.PENDING, row["status"])
        assertEquals(0, row["attempts"], "额度等待不消耗尝试数")
        assertEquals("DAILY_BUDGET", row["last_error"])
        assertEquals(1L, requireNotNull(repository.findPipeline()).activeCount, "等待期间仍算活跃工作")
    }

    // ==================================================================
    // I-5：容量与所有权有界
    // ==================================================================

    @Test
    fun `a page is refused as a whole when the count high-water would be crossed (I-5)`() {
        launchRunning()
        val stream = newStream()
        val tight = LIMITS.copy(highWater = 3)
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        assertEquals(
            EnqueuePageStatus.COMMITTED,
            enqueue(stream, "l1", listOf(item("DOI:a"), item("DOI:b")), "page-2", limits = tight).status
        )

        assertTrue(repository.claimStreamLease(stream.id, "l2", NOW.plusSeconds(120), NOW))
        val blocked = enqueue(
            stream, "l2", listOf(item("DOI:c"), item("DOI:d")), "page-3", limits = tight
        )

        assertEquals(EnqueuePageStatus.CAPACITY_BLOCKED, blocked.status)
        assertEquals("page-2", streamRow(stream.id).cursorValue, "容量不足时整页不提交、cursor 不推进")
        assertEquals(2, jobRows().size, "被拒绝的整页一条都不得写入")
        assertEquals(2L, requireNotNull(repository.findPipeline()).activeCount)
    }

    @Test
    fun `a page is refused as a whole when the byte budget would be crossed (I-5)`() {
        launchRunning()
        val stream = newStream()
        // 元数据本身很小，但「实际字节 + 在途结果预留」必须一起算。
        val tight = LIMITS.copy(highWater = 10_000, maxBytes = 400, reservedResultBytes = 100)
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        assertEquals(
            EnqueuePageStatus.COMMITTED,
            enqueue(stream, "l1", listOf(item("DOI:a", bytes = 150)), "page-2", limits = tight).status
        )

        assertTrue(repository.claimStreamLease(stream.id, "l2", NOW.plusSeconds(120), NOW))
        val blocked = enqueue(stream, "l2", listOf(item("DOI:b", bytes = 150)), "page-3", limits = tight)
        assertEquals(EnqueuePageStatus.CAPACITY_BLOCKED, blocked.status, "字节越界同样拒绝整页")
        assertEquals("page-2", streamRow(stream.id).cursorValue)

        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(150L, pipeline.payloadBytes)
        assertEquals(100L, pipeline.reservedResultBytes, "在途结果预留必须计入占用")
    }

    @Test
    fun `saving an extraction converts the reservation into actual bytes without deadlock (I-5)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a", bytes = 40)), cursorValue = null)
        val jobId = onlyJob()["id"] as Long
        assertTrue(repository.claimJob(jobId, "t1", NOW.plusSeconds(120), 0L, NOW))

        val extraction = """{"emails":[{"email":"a@b.c"}]}"""
        assertTrue(repository.saveExtraction(jobId, "t1", 0L, extraction, 300, NOW.plusSeconds(1)))

        val row = onlyJob()
        assertEquals(0L, row["reserved"], "保存后预占必须转成实际字节")
        assertEquals(340L, row["payload_bytes"])
        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(340L, pipeline.payloadBytes)
        assertEquals(0L, pipeline.reservedResultBytes)
    }

    @Test
    fun `concurrent enqueuers cannot exceed the high-water mark (I-5)`() {
        launchRunning()
        val stream = newStream()
        val tight = LIMITS.copy(highWater = 6, reservedResultBytes = 100, maxBytes = 1_000_000)

        val pool = Executors.newFixedThreadPool(4)
        val barrier = CountDownLatch(4)
        val committed = AtomicInteger(0)
        val blocked = AtomicInteger(0)
        repeat(4) { index ->
            pool.submit {
                val token = "lease-$index"
                if (repository.claimStreamLease(stream.id, token, NOW.plusSeconds(300), NOW)) {
                    barrier.countDown()
                    barrier.await(20, TimeUnit.SECONDS)
                    val result = enqueue(
                        stream, token,
                        (0 until 3).map { item("DOI:$index-$it", bytes = 40) },
                        "page-$index", limits = tight
                    )
                    when (result.status) {
                        EnqueuePageStatus.COMMITTED -> committed.incrementAndGet()
                        EnqueuePageStatus.CAPACITY_BLOCKED -> blocked.incrementAndGet()
                        EnqueuePageStatus.LEASE_LOST -> Unit
                    }
                } else {
                    barrier.countDown()
                }
            }
        }
        pool.shutdown()
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))

        val active = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM discovery_paper_job WHERE status IN ('PENDING','RUNNING','RETRY_WAIT')", Int::class.java
        ) ?: 0
        assertTrue(active <= 6, "并发入队绝不越过数量高水位（实际 $active）")
        assertEquals(
            active.toLong(), requireNotNull(repository.findPipeline()).activeCount,
            "容量账必须与 job 表实际行数一致"
        )
        assertTrue(committed.get() >= 1, "至少一页成功")
        assertTrue(
            committed.get() + blocked.get() + (4 - committed.get() - blocked.get()) == 4,
            "未被高水位拒绝的页要么提交，要么因采集页租约被其他连接持有而让出（不静默丢弃）"
        )
    }

    @Test
    fun `terminal cleanup releases bytes and never touches counters or cursors (I-8)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a", bytes = 400), item("DOI:b", bytes = 400)), "page-2")
        val ids = jobRows().map { it["id"] as Long }
        for ((index, id) in ids.withIndex()) {
            val token = "t$index"
            assertTrue(repository.claimJob(id, token, NOW.plusSeconds(120), 0L, NOW))
            assertTrue(
                repository.completeJob(id, token, 0L, QueueJobStatus.SUCCEEDED, 0, NOW, null, NOW.plusSeconds(1)).applied
            )
        }
        val before = requireNotNull(repository.findPipeline())
        assertEquals(800L, before.payloadBytes, "终态但未清理的负载仍计入占用")
        assertEquals(2L, before.processedPapers)
        assertEquals(2L, before.queuedPapers)
        assertEquals(0L, before.activeCount)

        // 7 天前完成 → 清负载（保留去重键）。
        val clearedJobs = repository.clearTerminalPayloads(NOW, 1000, NOW)
        assertEquals(0, clearedJobs, "截止时间早于完成时刻的条目不得被清理")
        val cleared = repository.clearTerminalPayloads(NOW.plusSeconds(8 * 24 * 3600), 1000, NOW.plusSeconds(8 * 24 * 3600))
        assertEquals(2, cleared)
        val after = requireNotNull(repository.findPipeline())
        assertEquals(0L, after.payloadBytes, "清理必须释放字节")
        assertEquals(2L, after.processedPapers, "清理不得重算累计指标")
        assertEquals(2L, after.queuedPapers)
        assertEquals("page-2", streamRow(stream.id).cursorValue, "清理不得重置 stream 游标")
        assertEquals(2, jobRows().size, "7 天内只清负载，不删去重键")

        // 90 天后才允许删除状态行/去重键。
        val deleted = repository.deleteTerminalJobs(NOW.plusSeconds(91 * 24 * 3600), 1000, NOW.plusSeconds(91 * 24 * 3600))
        assertEquals(2, deleted)
        assertEquals(0, jobRows().size)
        assertEquals(2L, requireNotNull(repository.findPipeline()).processedPapers, "删行也不得回退累计指标")
    }

    // ==================================================================
    // I-7：持久化控制与窗口交接
    // ==================================================================

    @Test
    fun `the pipeline starts PAUSED and launch pause resume keep their persisted meaning (I-7)`() {
        val fresh = requireNotNull(repository.findPipeline())
        assertEquals(PipelineDesiredState.PAUSED, fresh.desiredState, "初始必须是 PAUSED")
        assertEquals(0L, fresh.generation)

        val launched = launchRunning("qh-1")
        assertEquals(PipelineDesiredState.RUNNING, launched.desiredState)
        assertEquals("qh-1", launched.queryHash)

        val paused = repository.pause(NOW.plusSeconds(1))
        assertEquals(PipelineDesiredState.PAUSED, paused.desiredState)
        assertEquals(1L, paused.generation, "从 RUNNING 转入 PAUSED 必须递增 generation")
        assertEquals(PipelineWaitReason.MANUAL_PAUSE, paused.waitReason)

        // 幂等：再次 pause 不再递增（没有新的领取可以停止）。
        assertEquals(1L, repository.pause(NOW.plusSeconds(2)).generation)
        // 重启（重新 ensurePipeline）不得解除暂停。
        repository.ensurePipeline(NOW.plusSeconds(3))
        assertEquals(PipelineDesiredState.PAUSED, requireNotNull(repository.findPipeline()).desiredState)

        val resumed = requireNotNull(repository.resume(NOW.plusSeconds(4)))
        assertEquals(PipelineDesiredState.RUNNING, resumed.desiredState)
        assertEquals(1L, resumed.generation, "恢复不得新建 epoch/generation")

        // 同查询幂等；不同查询在有积压时被拒绝（没有任何在手工作时才允许换查询）。
        assertTrue(repository.launch("""{"k":2}""", 1, "qh-1", true, NOW.plusSeconds(5)) is LaunchOutcome.Applied)
        val backlogStream = newStream("qh-1")
        assertTrue(repository.claimStreamLease(backlogStream.id, "lb", NOW.plusSeconds(120), NOW.plusSeconds(5)))
        enqueue(backlogStream, "lb", listOf(item("DOI:backlog")), cursorValue = null)
        val conflict = repository.launch("""{"k":3}""", 1, "qh-2", true, NOW.plusSeconds(6))
        assertTrue(conflict is LaunchOutcome.Conflict, "有积压时切换查询必须被拒绝: $conflict")
    }

    @Test
    fun `resume without a configured query is refused (I-7)`() {
        assertNull(repository.resume(NOW.plusSeconds(1)), "从未配置查询时 resume 必须返回 null（控制层 409）")
    }

    @Test
    fun `the window owner lease is exclusive and a stale owner only clears itself (I-7)`() {
        launchRunning()
        assertTrue(repository.claimOwner("owner-1", NOW.plusSeconds(30), NOW.plusSeconds(3600), NOW))
        assertFalse(
            repository.claimOwner("owner-2", NOW.plusSeconds(30), NOW.plusSeconds(3600), NOW.plusSeconds(1)),
            "同一时刻只能有一个有效窗口属主"
        )
        assertEquals(0, repository.releaseStaleOwner(NOW.plusSeconds(60), NOW.plusSeconds(1)), "有效属主不得被当成失效")
        assertEquals("owner-1", requireNotNull(repository.findPipeline()).ownerToken)

        // 旧 owner 不能释放新 owner 的租约。
        assertTrue(repository.claimOwner("owner-3", NOW.plusSeconds(300), NOW.plusSeconds(7200), NOW.plusSeconds(31)))
        assertEquals(0, repository.releaseOwner("owner-1", PipelinePhase.WAITING, null, null, NOW.plusSeconds(32)))
        assertEquals("owner-3", requireNotNull(repository.findPipeline()).ownerToken)

        // 失效属主可以被清理，并进入 OWNER_RECOVERY。
        val recoveryAt = NOW.plusSeconds(301)
        assertEquals(1, repository.releaseStaleOwner(recoveryAt, NOW.plusSeconds(500)))
        val recovered = requireNotNull(repository.findPipeline())
        assertNull(recovered.ownerToken)
        assertEquals(PipelinePhase.WAITING, recovered.phase)
        assertEquals(PipelineWaitReason.OWNER_RECOVERY, recovered.waitReason)
    }

    @Test
    fun `a new window refreshes an expired window deadline instead of reusing it (I-7)`() {
        launchRunning()
        assertTrue(repository.claimOwner("owner-1", NOW.plusSeconds(1), NOW.plusSeconds(60), NOW))
        val firstWindow = requireNotNull(repository.findPipeline()).windowUntil
        assertEquals(NOW.plusSeconds(60), firstWindow)

        // 窗口结束后新的窗口必须换新的截止时间。
        assertTrue(repository.claimOwner("owner-2", NOW.plusSeconds(120), NOW.plusSeconds(1200), NOW.plusSeconds(61)))
        assertEquals(NOW.plusSeconds(1200), requireNotNull(repository.findPipeline()).windowUntil)
    }

    @Test
    fun `task history binding and raw scan marking require the current owner (I-7)`() {
        launchRunning()
        assertEquals(0, repository.bindExecutionId("nobody", 5L, NOW), "旧 owner 不得绑定任务历史")
        assertTrue(repository.claimOwner("owner-1", NOW.plusSeconds(30), NOW.plusSeconds(3600), NOW))
        assertEquals(1, repository.bindExecutionId("owner-1", 5L, NOW))
        assertEquals(5L, requireNotNull(repository.findPipeline()).executionId)
        assertEquals(0, repository.markRawScanDone("nobody", NOW), "非 owner 不得写 RAW 扫描标记")
        assertEquals(1, repository.markRawScanDone("owner-1", NOW))
        assertTrue(requireNotNull(repository.findPipeline()).rawScanDone, "RAW 扫描完成后必须持久化标记")
        assertEquals(0, repository.markRawScanDone("owner-2", NOW), "旧 owner 不得改写新窗口的标记")
    }

    @Test
    fun `draining requires exhausted sources and an empty active queue (I-7)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null, exhausted = true)

        val pipeline = requireNotNull(repository.findPipeline())
        assertEquals(1L, pipeline.activeCount)
        assertTrue(repository.markDrained(NOW.plusSeconds(1)))
        assertEquals(PipelinePhase.DRAINED, requireNotNull(repository.findPipeline()).phase)
        assertEquals(1L, requireNotNull(repository.findPipeline()).activeCount, "DRAINED 只表达相位，不删活跃工作")
        assertFalse(repository.markDrained(NOW.plusSeconds(2)), "重复标记 DRAINED 是空操作")
    }

    // ==================================================================
    // I-8：计数与快照
    // ==================================================================

    @Test
    fun `each counter is incremented at most once per CAS and units stay separate (I-8)`() {
        launchRunning()
        val paperStream = newStream(source = "OPENALEX")
        val recordStream = newStream(source = "ORCID")
        assertTrue(repository.claimStreamLease(paperStream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(paperStream, "l1", listOf(item("DOI:a")), cursorValue = null)
        assertTrue(repository.claimStreamLease(recordStream.id, "l2", NOW.plusSeconds(120), NOW))
        repository.enqueuePage(
            streamId = recordStream.id, leaseToken = "l2", unit = QueueItemUnit.RECORD,
            items = listOf(item("0000-0002-1825-0097", unit = QueueItemUnit.RECORD, quality = QueueIdentityQuality.ORCID)),
            cursorValue = null, exhausted = true, limits = LIMITS, now = NOW
        )

        val paperJob = jobRows().first { it["item_key"] == "DOI:a" }["id"] as Long
        val recordJob = jobRows().first { it["unit"] == QueueItemUnit.RECORD }["id"] as Long
        assertTrue(repository.claimJob(paperJob, "tp", NOW.plusSeconds(120), 0L, NOW))
        assertTrue(repository.claimJob(recordJob, "tr", NOW.plusSeconds(120), 0L, NOW))

        assertTrue(repository.completeJobWithExperts(paperJob, "tp", 0L, 2, 1, 0, NOW, NOW.plusSeconds(1)).applied)
        assertTrue(repository.completeJobWithExperts(recordJob, "tr", 0L, 1, 0, 0, NOW, NOW.plusSeconds(1)).applied)
        // 重复终态：0 行生效，计数不再累加。
        assertFalse(repository.completeJobWithExperts(paperJob, "tp", 0L, 2, 1, 0, NOW, NOW.plusSeconds(2)).applied)

        val status = repository.findPipeline()!!
        assertEquals(1L, status.queuedPapers, "论文与 ORCID 入队计数分离")
        assertEquals(1L, status.queuedRecords)
        assertEquals(1L, status.processedPapers)
        assertEquals(1L, status.processedRecords)
        assertEquals(3L, status.indexedExperts, "一次 CAS 最多各累加一次")
        assertEquals(1L, status.duplicateExperts)
        assertEquals(0L, status.failedItems)
        assertEquals(0L, status.activeCount)
        assertEquals(0L, repository.countActiveJobs())

        val streams = repository.findStreams(1L).associateBy { it.source }
        assertEquals(2L, streams.getValue("OPENALEX").indexedExperts)
        assertEquals(1L, streams.getValue("ORCID").indexedExperts, "逐源计数同样分离")
        val counts = repository.jobStatusCountsByStream(repository.findStreams(1L).map { it.id })
        assertEquals(2L, counts.values.sumOf { it.values.sum() })
    }

    @Test
    fun `job identity is unique per stream and the same key in another stream is not merged (I-2)`() {
        launchRunning()
        val first = newStream(source = "OPENALEX")
        val second = newStream(source = "CROSSREF")
        assertTrue(repository.claimStreamLease(first.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(first, "l1", listOf(item("DOI:same")), cursorValue = null)
        assertTrue(repository.claimStreamLease(second.id, "l2", NOW.plusSeconds(120), NOW))
        enqueue(second, "l2", listOf(item("DOI:same")), cursorValue = null)

        assertEquals(2, jobRows().size, "唯一键是 (stream_id, item_key)，不同来源的同一身份各自成行")
        assertEquals(2, jobRows().map { it["stream_id"] }.distinct().size)
    }

    @Test
    fun `the 32 kilobyte result reservation is what lets a full queue still store results (I-5)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a", bytes = 40)), cursorValue = null)

        val row = onlyJob()
        assertEquals(
            LIMITS.reservedResultBytes, row["reserved"],
            "入队时必须按配置为未抽取的活跃 job 预留结果空间"
        )
        assertEquals(
            40L + LIMITS.reservedResultBytes, requireNotNull(repository.findPipeline()).occupiedBytes,
            "占用 = 实际负载 + 在途结果预留"
        )
        assertEquals(
            com.weibo.talentintroduction.config.PIPELINE_RESERVED_RESULT_BYTES,
            com.weibo.talentintroduction.config.PIPELINE_RESERVED_RESULT_BYTES,
            "生产默认预留量由 ExpertDiscoveryProperties 的启动校验保证（32 KiB）"
        )
    }

    @Test
    fun `the due queries respect status priority and stream scope (I-3, I-6)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(
            stream, "l1",
            listOf(
                item("DOI:ordinary", downloadable = false),
                item("DOI:priority")
            ),
            cursorValue = null
        )
        val other = newStream(source = "CROSSREF")
        assertTrue(repository.claimStreamLease(other.id, "l2", NOW.plusSeconds(120), NOW))
        enqueue(other, "l2", listOf(item("DOI:elsewhere")), cursorValue = null)

        assertEquals(
            3L,
            repository.countActiveJobs(),
            "两条来源的到期工作都可见（queueDepth 只计活跃 job）"
        )
        assertEquals(
            "DOI:priority",
            repository.nextDueJob(repository.findStreams(1L).map { it.id }, true, NOW)!!.itemKey,
            "跨来源按优先/到期顺序给出第一个到期工作"
        )
        val priorityFirst = repository.nextDueJob(listOf(stream.id), priorityFirst = true, now = NOW)!!
        assertEquals("DOI:priority", priorityFirst.itemKey, "来源内优先可公开下载的工作")
        assertEquals(1, priorityFirst.priority)
        val ordinary = repository.nextDueOrdinaryJob(listOf(stream.id), NOW)!!
        assertEquals("DOI:ordinary", ordinary.itemKey, "普通任务可被单独取出（每 10 个高优先取 1 个）")
        assertTrue(
            repository.nextDueJob(listOf(other.id), priorityFirst = true, now = NOW)!!.itemKey == "DOI:elsewhere",
            "按 stream 过滤：其他来源的工作不会被误领"
        )
    }

    @Test
    fun `a suspended collection lease lets the other source proceed (I-6)`() {
        launchRunning()
        val slow = newStream(source = "OPENALEX")
        val healthy = newStream(source = "CROSSREF")

        // 慢来源持有采集页租约：同一来源不得被第二个采集者重复取数。
        assertTrue(repository.claimStreamLease(slow.id, "mine", NOW.plusSeconds(300), NOW))
        assertFalse(
            repository.claimStreamLease(slow.id, "other", NOW.plusSeconds(300), NOW.plusSeconds(1)),
            "同一来源同时只能有一个采集页在飞"
        )
        // 其他来源照常可以领取并完成自己的页。
        assertTrue(repository.claimStreamLease(healthy.id, "healthy", NOW.plusSeconds(300), NOW.plusSeconds(1)))
        assertEquals(
            EnqueuePageStatus.COMMITTED,
            repository.enqueuePage(
                healthy.id, "healthy", QueueItemUnit.PAPER, listOf(item("DOI:healthy")),
                null, true, LIMITS, NOW.plusSeconds(2)
            ).status
        )
        assertEquals(StreamCursorState.EXHAUSTED, streamRow(healthy.id).cursorState)
        assertEquals(StreamCursorState.ACTIVE, streamRow(slow.id).cursorState, "慢来源仍可续跑")
        assertEquals("mine", streamRow(slow.id).leaseToken)
    }

    @Test
    fun `a source level error is observable and does not masquerade as exhaustion (I-1, I-6)`() {
        launchRunning()
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))

        assertEquals(
            1,
            repository.recordStreamError(stream.id, "SEARCH_FAILED", NOW.plusSeconds(600), NOW)
        )
        val after = streamRow(stream.id)
        assertEquals("SEARCH_FAILED", after.sourceError)
        assertEquals(NOW.plusSeconds(600), after.nextAttemptAt)
        assertEquals(StreamCursorState.ACTIVE, after.cursorState, "来源错误绝不是穷尽")
        assertNull(after.leaseToken)

        // 到期前不可领取，到期后可以重新领取同一页。
        assertFalse(repository.claimStreamLease(stream.id, "l2", NOW.plusSeconds(1200), NOW.plusSeconds(1)))
        assertTrue(repository.claimStreamLease(stream.id, "l3", NOW.plusSeconds(1200), NOW.plusSeconds(601)))

        // 额度延期同样只推迟本源。
        assertEquals(
            1,
            repository.deferStream(stream.id, "l3", NOW.plusSeconds(3600), PipelineWaitReason.DAILY_BUDGET, NOW.plusSeconds(601))
        )
        val deferred = streamRow(stream.id)
        assertEquals(PipelineWaitReason.DAILY_BUDGET, deferred.sourceError)
        assertEquals(NOW.plusSeconds(3600), deferred.nextAttemptAt)
    }

    @Test
    fun `capacity paused is a persisted diagnostic flag (I-5)`() {
        launchRunning()
        repository.setCapacityPaused(true, NOW)
        assertTrue(requireNotNull(repository.findPipeline()).capacityPaused)
        // 成功入队会清除该标记（容量回落后的正常路径）。
        val stream = newStream()
        assertTrue(repository.claimStreamLease(stream.id, "l1", NOW.plusSeconds(120), NOW))
        enqueue(stream, "l1", listOf(item("DOI:a")), cursorValue = null)
        assertFalse(requireNotNull(repository.findPipeline()).capacityPaused)
    }
}
