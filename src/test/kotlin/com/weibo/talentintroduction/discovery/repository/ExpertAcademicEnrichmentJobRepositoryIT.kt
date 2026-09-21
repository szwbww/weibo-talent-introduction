package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.ExpertAcademicEnrichmentJob
import com.weibo.talentintroduction.discovery.service.ExpertAcademicEnrichmentJobService
import com.weibo.talentintroduction.discovery.service.LayerUpdateResult
import com.weibo.talentintroduction.discovery.service.LayerUpdateStatus
import com.weibo.talentintroduction.discovery.service.ProfileEnrichmentOutcome
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 可恢复补全任务存储的真实 MySQL 集成测试（`-DmigrationIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由真实 V131 迁移建立（不是手写 DDL），因此同时验证迁移契约；H2 不能替代持久化/并发恢复语义。
 *
 * 覆盖计划验收：
 * - **V-1/I-1**：重复 enqueue 合并成唯一 1 行；30 天内成功不重入、超过 30 天重开；
 *   FAILED 只被人工显式重开；UNMATCHED 由再次入队重开。
 * - **V-1/I-2**：两个事务同时 claim 只有一行成功；过期租约（含到期边界）重新可领；
 *   旧 token 既不能续租也不能完成；领取按到期时间排序且不超过 limit。
 * - **V-2/I-3**：PENDING→RUNNING→SUCCEEDED；部分写入落 RETRY_WAIT（不伪造成功）；
 *   网络故障退避 1m/5m/30m/2h 且第 5 次落 FAILED；429/日额度延期不消耗故障尝试数并按
 *   reset 重排；NO_ID / NOT_FOUND 落 UNMATCHED。
 */
@EnabledIfSystemProperty(named = "migrationIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 并发领取必须用真实独立连接/事务：测试级事务会让另一个线程看不到未提交行。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(ExpertAcademicEnrichmentJobService::class)
class ExpertAcademicEnrichmentJobRepositoryIT {

    companion object {
        /** 真实 ES `_id` 形状（ORCID），不是姓名/邮箱派生的键。 */
        private const val DOC_ID = "0000-0002-1825-0097"

        private class KotlinMySqlContainer(image: String) :
            MySQLContainer<KotlinMySqlContainer>(image)

        private val mysql = KotlinMySqlContainer("mysql:8.0.36")
            .withDatabaseName("talent_introduction")
            .withUsername("test")
            .withPassword("test")

        @JvmStatic
        @BeforeAll
        fun startMysql() {
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker is required for expert academic enrichment job repository tests"
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
    private lateinit var jobService: ExpertAcademicEnrichmentJobService

    @Autowired
    private lateinit var jobRepository: ExpertAcademicEnrichmentJobRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanJobs() {
        jdbcTemplate.update("DELETE FROM expert_academic_enrichment_job")
    }

    // ------------------------------------------------------------------
    // V-1 / I-1：入队合并与重开
    // ------------------------------------------------------------------

    @Test
    fun `enqueue merges repeated enqueues for one expert into a single pending row (I-1)`() {
        jobService.enqueue(DOC_ID, "openalex", 11L)
        jobService.enqueue(DOC_ID, "orcid", 12L)
        jobService.enqueue(DOC_ID, "openalex", null)

        assertEquals(1L, jobCount(), "同专家多次入队必须合并成一行")
        val row = jobRepository.findByExpertDocId(DOC_ID)!!
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_PENDING, row.status)
        assertEquals(0, row.attempts)
        assertNotNull(row.nextAttemptAt)
        assertNotNull(row.createdAt)
        assertNull(row.leaseToken)
        assertNull(row.leaseUntil)
        assertEquals(11L, row.discoveryExecutionId, "合并保留首次入队的审计归属，不产生第二行")
    }

    @Test
    fun `enqueue keeps a success younger than thirty days and reopens a stale success (I-1)`() {
        val now = LocalDateTime.now().withNano(0)
        val fresh = seedJob(
            "DOC-FRESH-SUCCESS",
            ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED,
            nextAttemptAt = now.minusDays(1),
            attempts = 1,
            updatedAt = now.minusDays(1)
        )
        val stale = seedJob(
            "DOC-STALE-SUCCESS",
            ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED,
            nextAttemptAt = now.minusDays(40),
            attempts = 2,
            updatedAt = now.minusDays(40)
        )

        jobService.enqueue("DOC-FRESH-SUCCESS", "openalex", 21L)
        jobService.enqueue("DOC-STALE-SUCCESS", "openalex", 22L)

        val freshRow = jobRepository.findById(fresh).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED, freshRow.status)
        assertEquals(1, freshRow.attempts, "30 天内的成功不得重入")

        val staleRow = jobRepository.findById(stale).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_PENDING, staleRow.status)
        assertEquals(0, staleRow.attempts)
        assertNull(staleRow.leaseToken)
        assertEquals(
            listOf("DOC-STALE-SUCCESS"),
            jobService.claimDue(10, now.plusSeconds(30)).map { it.expertDocId },
            "重开后的行立即可领"
        )
    }

    @Test
    fun `enqueue merges FAILED until an explicit manual reopen and reopens UNMATCHED (I-1, I-3)`() {
        val now = LocalDateTime.now().withNano(0)
        val failed = seedJob(
            "DOC-FAILED",
            ExpertAcademicEnrichmentJob.STATUS_FAILED,
            nextAttemptAt = now.minusMinutes(1),
            attempts = 5,
            lastError = "TRANSIENT_ERROR"
        )
        seedJob(
            "DOC-UNMATCHED",
            ExpertAcademicEnrichmentJob.STATUS_UNMATCHED,
            nextAttemptAt = now.minusMinutes(1),
            lastError = "NO_TRUSTED_IDENTITY"
        )

        jobService.enqueue("DOC-FAILED", "openalex", 31L)

        val failedRow = jobRepository.findById(failed).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_FAILED, failedRow.status, "FAILED 不被自动重试")
        assertEquals(5, failedRow.attempts)
        assertEquals(
            emptyList<String>(),
            jobService.claimDue(10, now.plusDays(1)).map { it.expertDocId },
            "FAILED 与 UNMATCHED 都不是可领取状态"
        )

        assertTrue(jobService.reopenFailed(failed, now), "人工重试是重开 FAILED 的唯一路径")
        val reopened = jobRepository.findById(failed).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_PENDING, reopened.status)
        assertEquals(0, reopened.attempts)
        assertNull(reopened.lastError)
        assertEquals(listOf("DOC-FAILED"), jobService.claimDue(10, now).map { it.expertDocId })
        assertFalse(jobService.reopenFailed(failed, now), "非 FAILED 行不能被人工重开")

        jobService.enqueue("DOC-UNMATCHED", "openalex", 32L)
        val unmatchedRow = jobRepository.findByExpertDocId("DOC-UNMATCHED")!!
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_PENDING, unmatchedRow.status)
        assertEquals(0, unmatchedRow.attempts)
        assertNull(unmatchedRow.lastError)
        assertNull(unmatchedRow.leaseToken)
    }

    // ------------------------------------------------------------------
    // V-1 / I-2：领取、排序、limit、并发与租约恢复
    // ------------------------------------------------------------------

    @Test
    fun `claimDue takes only due rows and skips not-yet-due, leased and terminal rows (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val pendingDue = seedJob("DOC-PENDING-DUE", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now)
        val retryDue = seedJob(
            "DOC-RETRY-DUE",
            ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT,
            nextAttemptAt = now.minusMinutes(1),
            attempts = 2
        )
        seedJob("DOC-PENDING-FUTURE", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now.plusMinutes(5))
        seedJob(
            "DOC-RETRY-FUTURE",
            ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT,
            nextAttemptAt = now.plusMinutes(5),
            attempts = 1
        )
        seedJob(
            "DOC-LEASED",
            ExpertAcademicEnrichmentJob.STATUS_RUNNING,
            nextAttemptAt = now,
            leaseToken = "live-worker",
            leaseUntil = now.plusMinutes(30)
        )
        seedJob("DOC-SUCCEEDED", ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED, nextAttemptAt = now.minusDays(1))
        seedJob("DOC-UNMATCHED", ExpertAcademicEnrichmentJob.STATUS_UNMATCHED, nextAttemptAt = now.minusDays(1))
        seedJob(
            "DOC-FAILED",
            ExpertAcademicEnrichmentJob.STATUS_FAILED,
            nextAttemptAt = now.minusDays(1),
            attempts = 5
        )

        val claimed = jobService.claimDue(10, now)

        assertEquals(
            listOf("DOC-RETRY-DUE", "DOC-PENDING-DUE"),
            claimed.map { it.expertDocId },
            "只领取到期行，并按 next_attempt_at 排序"
        )
        claimed.forEach { row ->
            assertEquals(ExpertAcademicEnrichmentJob.STATUS_RUNNING, row.status)
            assertEquals(now.plusMinutes(10), row.leaseUntil, "租期 10 分钟")
            assertNotNull(row.leaseToken)
        }
        // attempts 是故障尝试口径：领取既不增加也不重置。
        assertEquals(2, jobRepository.findById(retryDue).orElseThrow().attempts)
        assertEquals(0, jobRepository.findById(pendingDue).orElseThrow().attempts)
        // 未到期行保持原状，到点后才可领。
        assertEquals(
            listOf("DOC-PENDING-FUTURE", "DOC-RETRY-FUTURE"),
            jobService.claimDue(10, now.plusMinutes(5)).map { it.expertDocId }
        )
    }

    @Test
    fun `a lease becomes claimable exactly when it expires (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val id = seedJob(
            "DOC-LEASED",
            ExpertAcademicEnrichmentJob.STATUS_RUNNING,
            nextAttemptAt = now,
            leaseToken = "live-worker",
            leaseUntil = now.plusMinutes(30)
        )

        assertEquals(
            emptyList<String>(),
            jobService.claimDue(10, now.plusMinutes(29)).map { it.expertDocId },
            "租约未到期不可领取"
        )

        val reclaimed = jobService.claimDue(10, now.plusMinutes(30)).single()
        assertEquals(id, reclaimed.id)
        assertNotNull(reclaimed.leaseToken)
        assertTrue(reclaimed.leaseToken != "live-worker", "重新领取必须换发新 token")
    }

    @Test
    fun `claimDue never claims more than the requested limit (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        seedJob("DOC-L1", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now.minusMinutes(3))
        seedJob("DOC-L2", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now.minusMinutes(2))
        seedJob("DOC-L3", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now.minusMinutes(1))

        val claimed = jobService.claimDue(2, now)

        assertEquals(listOf("DOC-L1", "DOC-L2"), claimed.map { it.expertDocId })
        assertEquals(
            ExpertAcademicEnrichmentJob.STATUS_PENDING,
            jobRepository.findByExpertDocId("DOC-L3")!!.status,
            "超出 limit 的行必须保持 PENDING 交给下一轮"
        )
    }

    @Test
    fun `two concurrent transactions claim the same due row only once (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val id = seedJob("DOC-RACE", ExpertAcademicEnrichmentJob.STATUS_PENDING, nextAttemptAt = now)

        val start = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val results = CopyOnWriteArrayList<List<ExpertAcademicEnrichmentJob>>()
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(2) {
                pool.submit {
                    try {
                        start.await()
                        results += jobService.claimDue(1, now)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue(finished.await(60, TimeUnit.SECONDS), "并发领取未在时限内结束")
        } finally {
            pool.shutdownNow()
        }

        assertEquals(2, results.size)
        assertEquals(1, results.count { it.size == 1 }, "同一行只能被一个事务领取")
        val winner = results.single { it.isNotEmpty() }.single()
        assertEquals(id, winner.id)
        val row = jobRepository.findById(id).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RUNNING, row.status)
        assertEquals(winner.leaseToken, row.leaseToken, "持久化 token 必须就是获胜事务的 token")
        assertEquals(1L, jobCount(), "领取不得复制任务行")
    }

    @Test
    fun `an expired lease is reclaimable and the previous token can neither renew nor complete (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val id = seedJob(
            "DOC-CRASH",
            ExpertAcademicEnrichmentJob.STATUS_RUNNING,
            nextAttemptAt = now.minusMinutes(10),
            attempts = 1,
            leaseToken = "crashed-worker",
            leaseUntil = now.minusSeconds(1)
        )

        val reclaimed = jobService.claimDue(1, now).single()
        val newToken = reclaimed.leaseToken!!
        assertEquals(id, reclaimed.id)

        assertFalse(jobService.renewLease(id, "crashed-worker", now), "旧 token 不能续租")
        assertFalse(
            jobService.complete(id, "crashed-worker", success()),
            "旧 token 不能完成（否则会覆盖新领取者的结果）"
        )
        val afterStaleAttempts = jobRepository.findById(id).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RUNNING, afterStaleAttempts.status)
        assertEquals(newToken, afterStaleAttempts.leaseToken)
        assertNull(afterStaleAttempts.resultJson, "旧 token 的完成不得改写任何列")

        assertTrue(jobService.renewLease(id, newToken, now))
        assertEquals(now.plusMinutes(10), jobRepository.findById(id).orElseThrow().leaseUntil)
        assertTrue(jobService.complete(id, newToken, success()))
        assertEquals(
            ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED,
            jobRepository.findById(id).orElseThrow().status
        )
    }

    @Test
    fun `renewLease extends only the current holder's lease (I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val id = seedJob(
            "DOC-RENEW",
            ExpertAcademicEnrichmentJob.STATUS_RUNNING,
            nextAttemptAt = now,
            leaseToken = "worker-a",
            leaseUntil = now.plusMinutes(1)
        )

        assertFalse(jobService.renewLease(id, "worker-b", now))
        assertEquals(now.plusMinutes(1), jobRepository.findById(id).orElseThrow().leaseUntil)

        assertTrue(jobService.renewLease(id, "worker-a", now))
        assertEquals(now.plusMinutes(10), jobRepository.findById(id).orElseThrow().leaseUntil)
    }

    // ------------------------------------------------------------------
    // V-2 / I-3：结果持久化与重试分类
    // ------------------------------------------------------------------

    @Test
    fun `complete persists SUCCEEDED with the layer detail and clears the lease (I-1, I-2)`() {
        val now = LocalDateTime.now().withNano(0)
        val id = seedAndClaim("DOC-SUCCESS", now)
        val token = currentToken(id)

        assertTrue(
            jobService.complete(
                id,
                token,
                ProfileEnrichmentOutcome.Success(
                    LayerUpdateResult(
                        LayerUpdateStatus.UPDATED,
                        LayerUpdateStatus.ABSENT,
                        LayerUpdateStatus.UPDATED
                    )
                )
            )
        )

        val row = jobRepository.findById(id).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED, row.status)
        assertEquals(0, row.attempts)
        assertNull(row.leaseToken)
        assertNull(row.leaseUntil)
        assertNull(row.lastError)
        val resultJson = row.resultJson!!
        assertTrue(resultJson.contains("\"outcome\":\"SUCCESS\""), resultJson)
        assertTrue(resultJson.contains("\"raw\":\"UPDATED\""), resultJson)
        assertTrue(resultJson.contains("\"candidate\":\"ABSENT\""), resultJson)
        assertTrue(resultJson.contains("\"application\":\"UPDATED\""), resultJson)

        assertEquals(
            emptyList<String>(),
            jobService.claimDue(10, now.plusYears(1)).map { it.expertDocId },
            "SUCCEEDED 不再可领"
        )
        assertFalse(
            jobService.complete(id, token, ProfileEnrichmentOutcome.RetryableError()),
            "完成后的行不能被旧 worker 再写一次"
        )
        assertEquals(
            ExpertAcademicEnrichmentJob.STATUS_SUCCEEDED,
            jobRepository.findById(id).orElseThrow().status
        )
    }

    @Test
    fun `a partially written enrichment waits for retry instead of reporting success (I-3)`() {
        val now = LocalDateTime.now().withNano(0)
        val layerFailure = seedAndClaim("DOC-PARTIAL-LAYER", now)

        val before = LocalDateTime.now()
        assertTrue(
            jobService.complete(
                layerFailure,
                currentToken(layerFailure),
                ProfileEnrichmentOutcome.Partial(
                    LayerUpdateResult(LayerUpdateStatus.UPDATED, LayerUpdateStatus.FAILED, LayerUpdateStatus.ABSENT),
                    recentWorksFailed = false
                )
            )
        )
        val after = LocalDateTime.now()

        val layerRow = jobRepository.findById(layerFailure).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, layerRow.status)
        assertEquals(1, layerRow.attempts)
        assertEquals("LAYER_UPDATE_FAILED", layerRow.lastError)
        assertTrue(layerRow.resultJson!!.contains("\"candidate\":\"FAILED\""), layerRow.resultJson!!)
        assertScheduledAfterDelay(layerRow.nextAttemptAt, 60, before, after, "层写失败按 1 分钟退避")

        // 只有开关控制的最近论文子请求失败：同样是可重试等待，但原因码区分得开。
        val titlesFailure = seedAndClaim("DOC-PARTIAL-TITLES", now)
        assertTrue(
            jobService.complete(
                titlesFailure,
                currentToken(titlesFailure),
                ProfileEnrichmentOutcome.Partial(
                    LayerUpdateResult(LayerUpdateStatus.UPDATED, LayerUpdateStatus.ABSENT, LayerUpdateStatus.ABSENT),
                    recentWorksFailed = true
                )
            )
        )
        val titlesRow = jobRepository.findById(titlesFailure).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, titlesRow.status)
        assertEquals("RECENT_WORKS_FAILED", titlesRow.lastError)
        assertTrue(titlesRow.resultJson!!.contains("\"recentWorksFailed\":true"), titlesRow.resultJson!!)
    }

    @Test
    fun `transient failures back off one five thirty and one hundred twenty minutes then fail (I-3)`() {
        val id = seedJob(
            "DOC-LADDER",
            ExpertAcademicEnrichmentJob.STATUS_PENDING,
            nextAttemptAt = LocalDateTime.now().withNano(0)
        )
        var claimNow = LocalDateTime.now().withNano(0)

        listOf(1L, 5L, 30L, 120L).forEachIndexed { index, delayMinutes ->
            val claimed = jobService.claimDue(1, claimNow).single()
            val before = LocalDateTime.now()
            assertTrue(jobService.complete(id, claimed.leaseToken!!, ProfileEnrichmentOutcome.RetryableError()))
            val after = LocalDateTime.now()

            val row = jobRepository.findById(id).orElseThrow()
            assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, row.status)
            assertEquals(index + 1, row.attempts)
            assertEquals("TRANSIENT_ERROR", row.lastError)
            assertScheduledAfterDelay(
                row.nextAttemptAt,
                delayMinutes * 60,
                before,
                after,
                "第 ${index + 1} 次故障退避 $delayMinutes 分钟"
            )
            assertNull(row.leaseToken, "完成必须清空租约")
            claimNow = row.nextAttemptAt
        }

        val fifthAttempt = jobService.claimDue(1, claimNow).single()
        assertTrue(jobService.complete(id, fifthAttempt.leaseToken!!, ProfileEnrichmentOutcome.RetryableError()))
        val failedRow = jobRepository.findById(id).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_FAILED, failedRow.status, "第 5 次故障后不再自动重试")
        assertEquals(5, failedRow.attempts)
        assertEquals(
            emptyList<String>(),
            jobService.claimDue(1, claimNow.plusDays(30)).map { it.expertDocId },
            "FAILED 只有人工重试能重开"
        )
    }

    @Test
    fun `rate limit and daily budget deferral wait on the reset without consuming attempts (I-3)`() {
        val now = LocalDateTime.now().withNano(0)

        // 日额度延期：按真实重置时刻重排，attempts 不变，reset 前不可领。
        val resetAt = Instant.ofEpochMilli(Instant.now().toEpochMilli() + 3_600_000L)
        val budgetId = seedAndClaim("DOC-BUDGET", now, attempts = 3)
        assertTrue(
            jobService.complete(budgetId, currentToken(budgetId), ProfileEnrichmentOutcome.Deferred(resetAt))
        )
        val budgetRow = jobRepository.findById(budgetId).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, budgetRow.status)
        assertEquals(3, budgetRow.attempts, "额度延期不消耗故障尝试")
        assertEquals("BUDGET_DEFERRED", budgetRow.lastError)
        assertEquals(
            LocalDateTime.ofInstant(resetAt, ZoneId.systemDefault()),
            budgetRow.nextAttemptAt,
            "按真实重置时刻续跑（与 worker 本地时钟同口径）"
        )
        assertEquals(
            emptyList<String>(),
            jobService.claimDue(10, budgetRow.nextAttemptAt.minusSeconds(1)).map { it.expertDocId }
        )
        assertEquals(
            listOf("DOC-BUDGET"),
            jobService.claimDue(1, budgetRow.nextAttemptAt).map { it.expertDocId }
        )

        // 供应商限流：按 retry-after 重排，同样不消耗故障尝试（高 attempts 也不会被 429 推到 FAILED）。
        val rateId = seedAndClaim("DOC-RATE", now, attempts = 7)
        val before = LocalDateTime.now()
        assertTrue(
            jobService.complete(
                rateId,
                currentToken(rateId),
                ProfileEnrichmentOutcome.RetryableError(retryAfterMs = 90_000, rateLimited = true)
            )
        )
        val after = LocalDateTime.now()
        val rateRow = jobRepository.findById(rateId).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, rateRow.status)
        assertEquals(7, rateRow.attempts)
        assertEquals("RATE_LIMITED", rateRow.lastError)
        assertScheduledAfterDelay(rateRow.nextAttemptAt, 90, before, after, "限流按 retry-after 重排")

        // 限流未给 retry-after：按最小退避 1 分钟重排。
        val noHintId = seedAndClaim("DOC-RATE-NO-HINT", now)
        val noHintBefore = LocalDateTime.now()
        assertTrue(
            jobService.complete(
                noHintId,
                currentToken(noHintId),
                ProfileEnrichmentOutcome.RetryableError(rateLimited = true)
            )
        )
        val noHintAfter = LocalDateTime.now()
        val noHintRow = jobRepository.findById(noHintId).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_RETRY_WAIT, noHintRow.status)
        assertEquals(0, noHintRow.attempts)
        assertScheduledAfterDelay(
            noHintRow.nextAttemptAt,
            60,
            noHintBefore,
            noHintAfter,
            "限流无 retry-after 时按最小退避 1 分钟"
        )
    }

    @Test
    fun `NoId and NotFound persist as UNMATCHED and never as SUCCEEDED (I-3)`() {
        val now = LocalDateTime.now().withNano(0)
        val noId = seedAndClaim("DOC-NO-ID", now, attempts = 2)
        val notFound = seedAndClaim("DOC-NOT-FOUND", now)

        assertTrue(jobService.complete(noId, currentToken(noId), ProfileEnrichmentOutcome.NoId))
        assertTrue(jobService.complete(notFound, currentToken(notFound), ProfileEnrichmentOutcome.NotFound))

        val noIdRow = jobRepository.findById(noId).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_UNMATCHED, noIdRow.status, "无可靠身份绝不伪造 SUCCEEDED")
        assertEquals(2, noIdRow.attempts, "未匹配不是故障尝试")
        assertEquals("NO_TRUSTED_IDENTITY", noIdRow.lastError)
        assertNull(noIdRow.leaseToken)
        assertTrue(noIdRow.resultJson!!.contains("\"outcome\":\"NO_ID\""), noIdRow.resultJson!!)

        val notFoundRow = jobRepository.findById(notFound).orElseThrow()
        assertEquals(ExpertAcademicEnrichmentJob.STATUS_UNMATCHED, notFoundRow.status)
        assertEquals("AUTHOR_NOT_FOUND", notFoundRow.lastError)

        assertEquals(
            emptyList<String>(),
            jobService.claimDue(10, now.plusDays(1)).map { it.expertDocId },
            "UNMATCHED 只有再次入队（可靠身份变更）才能重开"
        )
        jobService.enqueue("DOC-NO-ID", "openalex", 41L)
        assertEquals(
            ExpertAcademicEnrichmentJob.STATUS_PENDING,
            jobRepository.findById(noId).orElseThrow().status
        )
    }

    // ------------------------------------------------------------------

    /** 种一行到期 `PENDING` 任务并立即领取：断言确实领到了这一行，返回带真实租约 token 的 id。 */
    private fun seedAndClaim(docId: String, now: LocalDateTime, attempts: Int = 0): Long {
        seedJob(
            docId,
            ExpertAcademicEnrichmentJob.STATUS_PENDING,
            nextAttemptAt = now,
            attempts = attempts
        )
        val claimed = jobService.claimDue(1, now)
        assertEquals(listOf(docId), claimed.map { it.expertDocId }, "到期 PENDING 行必须可领：$docId @ $now")
        return claimed.single().id!!
    }

    private fun currentToken(id: Long): String = jobRepository.findById(id).orElseThrow().leaseToken!!

    /**
     * 退避/延期时刻断言：DATETIME(3) 舍入与服务内部取时都会带来亚毫秒偏差，因此按
     * [delaySeconds] 的 ±2 秒窗口断言；相邻退避档位相隔 ≥4 分钟，不会把档位判错。
     */
    private fun assertScheduledAfterDelay(
        actual: LocalDateTime,
        delaySeconds: Long,
        before: LocalDateTime,
        after: LocalDateTime,
        message: String
    ) {
        val lower = before.plusSeconds(delaySeconds).minusSeconds(2)
        val upper = after.plusSeconds(delaySeconds).plusSeconds(2)
        assertFalse(actual.isBefore(lower), "$message：$actual 早于 $lower")
        assertFalse(actual.isAfter(upper), "$message：$actual 晚于 $upper")
    }

    private fun success() = ProfileEnrichmentOutcome.Success(
        LayerUpdateResult(LayerUpdateStatus.UPDATED, LayerUpdateStatus.ABSENT, LayerUpdateStatus.ABSENT)
    )

    private fun jobCount(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM expert_academic_enrichment_job",
            Long::class.java
        ) ?: error("count must not be null")

    private fun seedJob(
        docId: String,
        status: String,
        nextAttemptAt: LocalDateTime,
        attempts: Int = 0,
        leaseToken: String? = null,
        leaseUntil: LocalDateTime? = null,
        lastError: String? = null,
        updatedAt: LocalDateTime = LocalDateTime.now().withNano(0)
    ): Long {
        jdbcTemplate.update(
            """
            INSERT INTO expert_academic_enrichment_job
                (expert_doc_id, source, discovery_execution_id, status, attempts, next_attempt_at,
                 lease_token, lease_until, last_error, result_json, created_at, updated_at)
            VALUES (?, 'openalex', 7, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
            """.trimIndent(),
            docId,
            status,
            attempts,
            Timestamp.valueOf(nextAttemptAt),
            leaseToken,
            leaseUntil?.let { Timestamp.valueOf(it) },
            lastError,
            Timestamp.valueOf(updatedAt),
            Timestamp.valueOf(updatedAt)
        )
        return jdbcTemplate.queryForObject(
            "SELECT id FROM expert_academic_enrichment_job WHERE expert_doc_id = ?",
            Long::class.java,
            docId
        ) ?: error("seeded job must exist")
    }
}
