package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.config.BudgetReserveRequest
import com.weibo.talentintroduction.config.BudgetReserveResult
import com.weibo.talentintroduction.config.DeferredReason
import com.weibo.talentintroduction.config.Operation
import com.weibo.talentintroduction.config.RequestKind
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
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 共享预算账本的真实 MySQL 集成测试（`-DmysqlIt=true`，testcontainers MySQL 8.0.36）。
 *
 * 表由真实 V132 迁移建立（不是手写 DDL），因此同时验证迁移契约；H2 不能替代行锁、唯一约束、
 * 并发预占与跨周期结算语义。
 *
 * 覆盖子计划 01 的验收：
 * - **I-6**：account/day/reservation 的唯一约束与非负 CHECK；UTC 时间往返；状态只走
 *   RESERVED→SETTLED|UNKNOWN；账本聚合等于 permit 明细；活跃 UNKNOWN 不被清理、已关闭周期按批归档。
 * - **I-3**：最后 10 credits 的双连接并发只能批准总成本 ≤ 10；重复 settle 不重复扣；超时预占不退还；
 *   高成本立即追扣。
 * - **I-4**：同账号只允许一个校准者；0 成本操作不写账本行但遵守限速槽；日切后旧周期 permit 只结算旧行。
 * - **I-5**：只读现有补全任务的未完成计数与 NEW_ENRICHMENT 成本样本。
 */
@EnabledIfSystemProperty(named = "mysqlIt", matches = "true")
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 并发预占必须用真实独立连接/事务：测试级事务会让另一个线程看不到未提交行。
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
    properties = [
        "spring.flyway.placeholder-replacement=false"
    ]
)
@Import(OpenAlexBudgetRepository::class)
class OpenAlexBudgetRepositoryIT {

    companion object {
        private const val SCOPE = "primary"
        private val NOW: Instant = Instant.parse("2026-09-22T02:41:17.123Z")
        private val NEXT_RESET: Instant = Instant.parse("2026-09-23T00:00:00Z")

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
                "Docker is required for the OpenAlex budget ledger tests"
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
    private lateinit var repository: OpenAlexBudgetRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanLedger() {
        jdbcTemplate.update("DELETE FROM openalex_budget_reservation")
        jdbcTemplate.update("DELETE FROM openalex_budget_day")
        jdbcTemplate.update("DELETE FROM openalex_budget_account")
        jdbcTemplate.update("DELETE FROM expert_academic_enrichment_job")
    }

    // ------------------------------------------------------------------
    // I-3 / I-6：预占、结算、幂等与聚合一致
    // ------------------------------------------------------------------

    @Test
    fun `a permit is reserved before the call and settles exactly once (I-3, I-6)`() {
        reconcile(limit = 10_000, remaining = 10_000)
        val permit = reserve(RequestKind.DISCOVERY, Operation.LIST, estimated = 1)

        val reservation = reservationRow(permit)
        assertEquals("RESERVED", reservation["status"])
        assertEquals(1L, reservation["amount_reserved"])
        assertNull(reservation["amount_actual"])
        assertNull(reservation["settled_at"])
        assertEquals(1L, dayColumn("outstanding_reserved"))
        assertEquals(0L, dayColumn("confirmed_spent"))

        assertTrue(repository.settle(SCOPE, permit, 1L, 9_999L, 10_000L, NOW.plusSeconds(1)))
        assertEquals("SETTLED", reservationRow(permit)["status"])
        assertEquals(1L, reservationRow(permit)["amount_actual"])
        assertEquals(1L, dayColumn("confirmed_spent"))
        assertEquals(0L, dayColumn("outstanding_reserved"))
        assertEquals(9_999L, dayColumn("provider_ceiling"))

        // 重复结算不得重复扣减。
        assertFalse(repository.settle(SCOPE, permit, 1L, 9_999L, 10_000L, NOW.plusSeconds(2)))
        assertEquals(1L, dayColumn("confirmed_spent"))
        assertEquals(1L, reservationCount())
    }

    @Test
    fun `a higher actual cost is charged immediately and a missing response keeps its reservation (I-3)`() {
        reconcile(limit = 20, remaining = 20)
        val search = reserve(RequestKind.DISCOVERY, Operation.SEARCH, estimated = 10)
        // 真实成本 11 > 估算 10：立即追扣。
        repository.settle(SCOPE, search, 11L, 9L, 20L, NOW.plusSeconds(1))
        assertEquals(11L, dayColumn("confirmed_spent"))

        // 超时（无证据）：UNKNOWN，仍计入未结算预占，绝不退还。
        val timedOut = reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, estimated = 1)
        assertTrue(repository.markUnknown(SCOPE, timedOut, NOW.plusSeconds(2)))
        assertEquals("UNKNOWN", reservationRow(timedOut)["status"])
        assertNull(reservationRow(timedOut)["amount_actual"])
        assertEquals(1L, dayColumn("outstanding_reserved"))
        // UNKNOWN 不能再次被标为 UNKNOWN（状态迁移只有 RESERVED→UNKNOWN）。
        assertFalse(repository.markUnknown(SCOPE, timedOut, NOW.plusSeconds(3)))
    }

    @Test
    fun `the ledger aggregate always equals the permit detail (I-6)`() {
        reconcile(limit = 1_000, remaining = 1_000)
        val settled = reserve(RequestKind.DISCOVERY, Operation.LIST, estimated = 1)
        repository.settle(SCOPE, settled, 1L, 999L, 1_000L, NOW.plusSeconds(1))
        val unknown = reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, estimated = 1)
        repository.markUnknown(SCOPE, unknown, NOW.plusSeconds(2))
        val open = reserve(RequestKind.HISTORY_ENRICHMENT, Operation.LIST, estimated = 1)

        assertEquals(
            jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_actual), 0) FROM openalex_budget_reservation WHERE status = 'SETTLED'",
                Long::class.java
            ),
            dayColumn("confirmed_spent"),
            "confirmed_spent 必须等于已结算 permit 的 amount_actual 之和"
        )
        assertEquals(
            jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount_reserved), 0) FROM openalex_budget_reservation
                 WHERE status IN ('RESERVED', 'UNKNOWN')
                """,
                Long::class.java
            ),
            dayColumn("outstanding_reserved"),
            "outstanding_reserved 必须等于未结算 permit 的 amount_reserved 之和"
        )
        assertEquals(3L, reservationCount())
        assertNotNull(open)
    }

    @Test
    fun `two connections racing for the last ten credits approve at most ten credits in total (I-3)`() {
        reconcile(limit = 10, remaining = 10)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(11)
        try {
            val tasks = (1..10).map {
                pool.submit<Long> {
                    start.await()
                    val permitId = UUID.randomUUID().toString()
                    val permit = repository.reserve(request(permitId, RequestKind.DISCOVERY, Operation.LIST, 1L))
                    if (permit is BudgetReserveResult.Approved) {
                        repository.settle(SCOPE, permitId, 1L, null, null, NOW.plusSeconds(1))
                        1L
                    } else {
                        0L
                    }
                }
            } + pool.submit<Long> {
                start.await()
                val permitId = UUID.randomUUID().toString()
                val permit = repository.reserve(request(permitId, RequestKind.DISCOVERY, Operation.SEARCH, 10L))
                if (permit is BudgetReserveResult.Approved) {
                    repository.settle(SCOPE, permitId, 10L, null, null, NOW.plusSeconds(1))
                    10L
                } else {
                    0L
                }
            }
            start.countDown()
            val approved = tasks.sumOf { it.get(30, TimeUnit.SECONDS) }
            assertTrue(approved <= 10L, "批准的预占总成本必须 ≤ 10（实际 $approved）")
            assertTrue(dayColumn("confirmed_spent") <= 10L)
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // I-4 / I-6：周期、校准租约、限速与归档
    // ------------------------------------------------------------------

    @Test
    fun `only one calibrator can hold the account lease (I-4)`() {
        val first = "lease-first"
        val second = "lease-second"
        assertTrue(repository.claimSyncLease(SCOPE, first, NOW, NOW.plusSeconds(60)))
        assertFalse(repository.claimSyncLease(SCOPE, second, NOW.plusSeconds(1), NOW.plusSeconds(120)))

        // 租约到期后另一个校准者可以接管；旧持有者释放不会影响新持有者。
        assertTrue(repository.claimSyncLease(SCOPE, second, NOW.plusSeconds(61), NOW.plusSeconds(120)))
        repository.releaseSyncLease(SCOPE, first)
        assertFalse(repository.claimSyncLease(SCOPE, first, NOW.plusSeconds(62), NOW.plusSeconds(120)))
    }

    @Test
    fun `zero cost operations write no ledger row but still respect the account rate slot (I-4)`() {
        reconcile(limit = 10_000, remaining = 10_000)

        val singleton = repository.reserve(
            request(UUID.randomUUID().toString(), RequestKind.NEW_ENRICHMENT, Operation.SINGLETON, 0L, rateIntervalMs = 200L)
        )
        assertTrue(singleton is BudgetReserveResult.Approved)
        assertEquals(0L, reservationCount(), "0 成本操作不产生账本行")

        // 同一毫秒内的第二次调用落在账号级限速槽之前 → RATE_LIMIT（不睡在数据库锁上）。
        val next = repository.reserve(
            request(UUID.randomUUID().toString(), RequestKind.NEW_ENRICHMENT, Operation.LIST, 1L)
        )
        assertEquals(DeferredReason.RATE_LIMIT, (next as BudgetReserveResult.Rejected).reason)
        assertTrue(next.retryAt.isAfter(NOW))
    }

    @Test
    fun `a reservation from before the reset only settles its own cycle row (I-4, I-6)`() {
        reconcile(limit = 10_000, remaining = 5_000, resetAt = NEXT_RESET)
        val oldPermit = reserve(RequestKind.DISCOVERY, Operation.LIST, estimated = 1)
        val oldDayId = reservationDayId(oldPermit)

        val nextReset = NEXT_RESET.plusSeconds(86_400)
        reconcile(limit = 10_000, remaining = 10_000, resetAt = nextReset, now = NEXT_RESET.plusSeconds(1))
        assertEquals(nextReset, repository.ledger(SCOPE, 10_000, NEXT_RESET.plusSeconds(1)).cycleResetAt)
        assertEquals(10_000L, dayColumn("provider_ceiling"), "新周期 ceiling 来自新快照")
        assertEquals(0L, dayColumn("confirmed_spent"))

        // 旧周期的迟到响应：只结算旧行，绝不污染新周期。
        assertTrue(repository.settle(SCOPE, oldPermit, 1L, 5L, 10_000L, NEXT_RESET.plusSeconds(2)))
        assertEquals(0L, dayColumn("confirmed_spent"), "新周期不受旧响应影响")
        assertEquals(10_000L, dayColumn("provider_ceiling"))
        assertEquals(1L, columnForDay(oldDayId, "confirmed_spent"))
        assertEquals(5L, columnForDay(oldDayId, "provider_ceiling"))
    }

    @Test
    fun `an active unknown is never cleaned up while closed cycles are archived in batches (I-6)`() {
        reconcile(limit = 1_000, remaining = 1_000)
        val active = reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, estimated = 1)
        repository.markUnknown(SCOPE, active, NOW.plusSeconds(1))

        assertEquals(0, repository.cleanupClosedCycles(NOW.plusSeconds(86_400 * 30), 1_000))
        assertEquals(1L, reservationCount(), "活跃周期与未解决 permit 不按短 TTL 释放")

        // 官方确认新周期 → 旧周期关闭；再过期后按批归档。
        val nextReset = NEXT_RESET.plusSeconds(86_400)
        reconcile(limit = 1_000, remaining = 1_000, resetAt = nextReset, now = NEXT_RESET.plusSeconds(1))
        jdbcTemplate.update(
            "UPDATE openalex_budget_day SET closed_at = ? WHERE closed_at IS NOT NULL",
            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        )
        assertEquals(0, repository.cleanupClosedCycles(NOW.minusSeconds(60), 1_000), "保留期内不清理")
        assertEquals(1, repository.cleanupClosedCycles(NOW.plusSeconds(86_400), 1_000))
        assertEquals(0L, reservationCount(), "已关闭周期的 permit 随周期归档")
        assertEquals(1L, dayCount(), "活跃周期保留")
    }

    @Test
    fun `unique constraints non negative checks and state values are enforced by the migration (I-6)`() {
        reconcile(limit = 1_000, remaining = 1_000)
        val dayId = dayColumn("id")

        // day 行唯一 (account_scope, reset_at)
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                """
                INSERT INTO openalex_budget_day
                    (account_scope, reset_at, free_limit, confirmed_spent, outstanding_reserved, created_at, updated_at)
                VALUES (?, ?, 1000, 0, 0, ?, ?)
                """,
                SCOPE, db(NEXT_RESET), db(NOW), db(NOW)
            )
        }
        // 额度字段非负（MySQL 8 把 CHECK 违例报成 error 3819 / SQLState HY000，Spring 归为 DataAccessException）
        assertThrows(DataAccessException::class.java) {
            jdbcTemplate.update("UPDATE openalex_budget_day SET free_limit = -1 WHERE id = ?", dayId)
        }
        assertThrows(DataAccessException::class.java) {
            jdbcTemplate.update("UPDATE openalex_budget_day SET outstanding_reserved = -1 WHERE id = ?", dayId)
        }
        // permit_id 唯一 + 状态取值受 CHECK 约束
        val permit = reserve(RequestKind.DISCOVERY, Operation.LIST, estimated = 1)
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbcTemplate.update(
                """
                INSERT INTO openalex_budget_reservation
                    (permit_id, day_id, request_kind, operation, amount_reserved, status, created_at)
                VALUES (?, ?, 'DISCOVERY', 'LIST', 1, 'RESERVED', ?)
                """,
                permit, dayId, db(NOW)
            )
        }
        assertThrows(DataAccessException::class.java) {
            jdbcTemplate.update(
                """
                INSERT INTO openalex_budget_reservation
                    (permit_id, day_id, request_kind, operation, amount_reserved, status, created_at)
                VALUES (?, ?, 'DISCOVERY', 'LIST', 1, 'REFUNDED', ?)
                """,
                UUID.randomUUID().toString(), dayId, db(NOW)
            )
        }
        // account 行主键唯一：重复 ensure 只是一次空更新。
        repository.reconcile(SCOPE, NEXT_RESET, 1_000, 1_000, 1_000, NOW.plusSeconds(1))
        assertEquals(
            1L,
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_budget_account", Long::class.java)!!
        )
    }

    @Test
    fun `utc timestamps survive the round trip through the ledger (I-6)`() {
        val syncedAt = Instant.parse("2026-09-22T02:41:17.123Z")
        repository.reconcile(SCOPE, NEXT_RESET, 10_000, 9_773, 10_000, syncedAt)

        val ledger = repository.ledger(SCOPE, 10_000, syncedAt)
        assertEquals(NEXT_RESET, ledger.cycleResetAt)
        assertEquals(syncedAt, ledger.lastSyncedAt)
        assertEquals(9_773L, ledger.providerCeilingCredits)
        assertEquals(9_773L, ledger.effectiveRemainingCredits)
        assertEquals(
            LocalDateTime.ofInstant(syncedAt, ZoneOffset.UTC),
            jdbcTemplate.queryForObject(
                "SELECT last_synced_at FROM openalex_budget_account WHERE account_scope = ?",
                LocalDateTime::class.java,
                SCOPE
            )
        )
    }

    @Test
    fun `an expired cycle is not a trusted cycle and a stale snapshot is ignored (I-4)`() {
        assertNull(repository.ledger(SCOPE, 10_000, NOW).cycleResetAt, "尚未校准时没有可信周期")
        assertTrue(
            repository.reserve(request(UUID.randomUUID().toString(), RequestKind.DISCOVERY, Operation.LIST, 1L))
                is BudgetReserveResult.Rejected
        )

        // 已过期的官方快照（reset 早于现在）不被接受。
        assertFalse(repository.reconcile(SCOPE, NOW.minusSeconds(1), 10_000, 10_000, 10_000, NOW))
        assertTrue(repository.reconcile(SCOPE, NEXT_RESET, 10_000, 10_000, 10_000, NOW))
        assertTrue(
            repository.reserve(request(UUID.randomUUID().toString(), RequestKind.DISCOVERY, Operation.LIST, 1L))
                is BudgetReserveResult.Approved
        )
    }

    // ------------------------------------------------------------------
    // I-5：补全任务只读统计与成本样本
    // ------------------------------------------------------------------

    @Test
    fun `unfinished enrichment jobs are counted by status and costs sampled from settled permits (I-5)`() {
        seedEnrichmentJob("DOC-PENDING", "PENDING")
        seedEnrichmentJob("DOC-RUNNING", "RUNNING")
        seedEnrichmentJob("DOC-RETRY", "RETRY_WAIT")
        seedEnrichmentJob("DOC-SUCCEEDED", "SUCCEEDED")
        seedEnrichmentJob("DOC-FAILED", "FAILED")
        seedEnrichmentJob("DOC-UNMATCHED", "UNMATCHED")

        assertEquals(3L, repository.countUnfinishedEnrichmentJobs())

        reconcile(limit = 1_000, remaining = 1_000)
        val first = reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, estimated = 1)
        repository.settle(SCOPE, first, 10L, null, null, NOW.plusSeconds(1))
        val second = reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, estimated = 1)
        repository.settle(SCOPE, second, 30L, null, null, NOW.plusSeconds(2))
        val discovery = reserve(RequestKind.DISCOVERY, Operation.LIST, estimated = 1)
        repository.settle(SCOPE, discovery, 1L, null, null, NOW.plusSeconds(3))

        assertEquals(listOf(30L, 10L), repository.recentNewEnrichmentCosts(SCOPE, 100))
        assertEquals(listOf(30L), repository.recentNewEnrichmentCosts(SCOPE, 1))
        repository.recordNewEnrichmentRequest(SCOPE, NOW.plusSeconds(4))
        assertEquals(NOW.plusSeconds(4), repository.ledger(SCOPE, 1_000, NOW.plusSeconds(5)).lastNewEnrichmentAt)
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun reconcile(
        limit: Long,
        remaining: Long,
        resetAt: Instant = NEXT_RESET,
        now: Instant = NOW
    ) {
        assertTrue(repository.reconcile(SCOPE, resetAt, limit, remaining, limit, now))
    }

    private fun request(
        permitId: String,
        kind: RequestKind,
        operation: Operation,
        estimated: Long,
        rateIntervalMs: Long = 0L
    ): BudgetReserveRequest =
        BudgetReserveRequest(
            accountScope = SCOPE,
            permitId = permitId,
            kind = kind,
            operation = operation,
            estimatedCredits = estimated,
            reservedFloorCredits = 0L,
            rateIntervalMs = rateIntervalMs,
            configuredFreeLimitCredits = 10_000L,
            now = NOW
        )

    /** 预占一个 permit 并返回它的 id（Rate slot 为 0 时同一时刻可连续预占）。 */
    private fun reserve(kind: RequestKind, operation: Operation, estimated: Long): String {
        val permitId = UUID.randomUUID().toString()
        val result = repository.reserve(request(permitId, kind, operation, estimated))
        assertTrue(result is BudgetReserveResult.Approved, "expected an approved reservation, got $result")
        return permitId
    }

    private fun reservationRow(permitId: String): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM openalex_budget_reservation WHERE permit_id = ?", permitId)

    private fun reservationDayId(permitId: String): Long =
        jdbcTemplate.queryForObject(
            "SELECT day_id FROM openalex_budget_reservation WHERE permit_id = ?",
            Long::class.java,
            permitId
        )!!

    private fun reservationCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_budget_reservation", Long::class.java)!!

    private fun dayCount(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM openalex_budget_day", Long::class.java)!!

    private fun dayColumn(column: String): Long =
        jdbcTemplate.queryForObject(
            "SELECT $column FROM openalex_budget_day WHERE closed_at IS NULL ORDER BY reset_at ASC LIMIT 1",
            Long::class.java
        )!!

    private fun columnForDay(dayId: Long, column: String): Long =
        jdbcTemplate.queryForObject("SELECT $column FROM openalex_budget_day WHERE id = ?", Long::class.java, dayId)!!

    private fun seedEnrichmentJob(docId: String, status: String) {
        jdbcTemplate.update(
            """
            INSERT INTO expert_academic_enrichment_job
                (expert_doc_id, source, status, attempts, next_attempt_at, created_at, updated_at)
            VALUES (?, 'openalex', ?, 0, ?, ?, ?)
            """,
            docId, status, db(NOW), db(NOW), db(NOW)
        )
    }

    private fun db(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
}
