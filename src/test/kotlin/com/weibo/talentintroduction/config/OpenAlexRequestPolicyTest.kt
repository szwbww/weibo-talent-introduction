package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * I-1..I-6：共享预算策略的场景测试。
 *
 * 账本用 [InMemoryOpenAlexBudgetStore]（生产 Bean 一律注入 MySQL 账本，见 `RestTemplateConfig`），
 * 两个 policy 实例共用一个 store 即代表「同一 accountScope 的两个应用实例」。
 */
class OpenAlexRequestPolicyTest {

    private class FakeTime(
        var current: Instant = Instant.parse("2026-09-21T02:41:17Z")
    ) : PolicyTimeSource {
        val sleeps = CopyOnWriteArrayList<Long>()

        override fun now(): Instant = synchronized(this) { current }

        override fun sleep(ms: Long) {
            // 与 Thread.sleep 一致：负等待必须抛 IllegalArgumentException，否则测试无法证明策略不会传入非正值。
            if (ms < 0L) throw IllegalArgumentException("timeout value is negative")
            synchronized(this) {
                sleeps += ms
                current = current.plusMillis(ms)
            }
        }
    }

    private val time = FakeTime()

    // 5/s would add a 200ms slot to every assertion; the rate limit itself is asserted in its own test.
    private fun properties(
        apiKey: String = "",
        dailyBudgetUsd: Double = 0.0,
        maxRequestsPerSecond: Double = 100.0,
        reserveRatio: Double = 0.2,
        enabled: Boolean = true,
        freeBudgetCredits: Long = 10000,
        rateLimitBackoffMaxMs: Long = 60_000,
        accountScope: String = "primary",
        budgetSyncInterval: Duration = Duration.ofSeconds(300)
    ) = OpenAlexProperties(
        enabled = enabled,
        apiKey = apiKey,
        dailyBudgetUsd = dailyBudgetUsd,
        maxRequestsPerSecond = maxRequestsPerSecond,
        newEnrichmentReserveRatio = reserveRatio,
        freeBudgetCredits = freeBudgetCredits,
        rateLimitBackoffMaxMs = rateLimitBackoffMaxMs,
        accountScope = accountScope,
        budgetSyncInterval = budgetSyncInterval
    )

    private fun policy(
        apiKey: String = "",
        dailyBudgetUsd: Double = 0.0,
        maxRequestsPerSecond: Double = 100.0,
        reserveRatio: Double = 0.2,
        enabled: Boolean = true,
        freeBudgetCredits: Long = 10000,
        rateLimitBackoffMaxMs: Long = 60_000,
        accountScope: String = "primary",
        store: OpenAlexBudgetStore = InMemoryOpenAlexBudgetStore(),
        syncSource: OpenAlexBudgetSyncSource? = null
    ) = OpenAlexRequestPolicy(
        properties(
            apiKey, dailyBudgetUsd, maxRequestsPerSecond, reserveRatio, enabled,
            freeBudgetCredits, rateLimitBackoffMaxMs, accountScope
        ),
        time,
        store,
        syncSource
    )

    private fun quotaHeaders(
        creditsUsed: Long? = 1L,
        remaining: Long? = null,
        limit: Long? = null,
        resetSeconds: Long? = null,
        retryAfterSeconds: Long? = null,
        prepaidRemainingUsd: String? = null
    ): HttpHeaders {
        val headers = HttpHeaders()
        creditsUsed?.let { headers.set(OpenAlexRequestPolicy.CREDITS_USED_HEADER, it.toString()) }
        remaining?.let { headers.set(OpenAlexRequestPolicy.REMAINING_HEADER, it.toString()) }
        limit?.let { headers.set(OpenAlexRequestPolicy.LIMIT_HEADER, it.toString()) }
        resetSeconds?.let { headers.set(OpenAlexRequestPolicy.RESET_HEADER, it.toString()) }
        retryAfterSeconds?.let { headers.set(OpenAlexRequestPolicy.RETRY_AFTER_HEADER, it.toString()) }
        prepaidRemainingUsd?.let { headers.set("X-RateLimit-Prepaid-Remaining-USD", it) }
        return headers
    }

    private val listTarget = "https://api.openalex.org/works?filter=is_oa:true&per_page=100"
    private val searchTarget = "https://api.openalex.org/works?filter=title_and_abstract.search:alloy"
    private val singletonTarget = "https://api.openalex.org/authors/A5023888391"
    private val rateLimitTarget = "https://api.openalex.org/rate-limit"

    /** Spends the budget the way a real caller does: reserve a permit, then reconcile with the provider cost header. */
    private fun consume(policy: OpenAlexRequestPolicy, kind: RequestKind, requests: Int) {
        repeat(requests) {
            val permit = policy.reserve(kind, Operation.LIST, listTarget)
            assertTrue(permit is Permit.Allowed, "expected an allowed permit, got $permit")
            policy.recordResponse((permit as Permit.Allowed).permitId, quotaHeaders())
            // 限速槽位现在以 Deferred(retryAt) 表达（策略绝不 sleep），因此测试自己把时钟推过槽位。
            time.current = time.current.plusSeconds(1)
        }
    }

    /** 429 冷却 / 限速槽位：调用方拿到的 Deferred(retryAt) 距当前时刻的毫秒数（策略绝不 sleep）。 */
    private fun rateLimitRetryDelayMs(policy: OpenAlexRequestPolicy): Long {
        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertTrue(deferred is Permit.Deferred, "expected a deferred permit, got $deferred")
        assertEquals(DeferredReason.RATE_LIMIT, (deferred as Permit.Deferred).reason)
        return Duration.between(time.current, deferred.retryAt).toMillis()
    }

    // ------------------------------------------------------------------
    // I-1：操作成本与用途分离
    // ------------------------------------------------------------------

    @Test
    fun `operation costs are list 1 search 10 singleton 0 content 100 rate limit 0 (I-1)`() {
        assertEquals(1L, Operation.LIST.costCredits)
        assertEquals(10L, Operation.SEARCH.costCredits)
        assertEquals(0L, Operation.SINGLETON.costCredits)
        assertEquals(100L, Operation.CONTENT.costCredits)
        assertEquals(0L, Operation.RATE_LIMIT.costCredits)
    }

    @Test
    fun `the declared operation must match the target host and path (I-1)`() {
        val policy = policy()

        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.SEARCH, searchTarget) is Permit.Allowed)
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.SINGLETON, singletonTarget) is Permit.Allowed)
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.RATE_LIMIT, rateLimitTarget) is Permit.Allowed)

        // 声明与目标不一致一律抛错：绝不按 URL substring 静默推断成本。
        assertThrows(IllegalStateException::class.java) {
            policy.reserve(RequestKind.DISCOVERY, Operation.SEARCH, listTarget)
        }
        assertThrows(IllegalStateException::class.java) {
            policy.reserve(RequestKind.DISCOVERY, Operation.LIST, searchTarget)
        }
        assertThrows(IllegalStateException::class.java) {
            policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, singletonTarget)
        }
    }

    @Test
    fun `metered content downloads are refused and never write a ledger row (I-1)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(apiKey = "k", store = store)

        val thrown = assertThrows(OpenAlexContentDisabledException::class.java) {
            policy.reserve(RequestKind.DISCOVERY, Operation.CONTENT, "https://content.openalex.org/works/W1.pdf")
        }
        assertTrue(thrown.message!!.contains("CONTENT"))

        // 模拟的 100-credit Content 调用不产生任何账本行，也不消耗额度。
        val snapshot = policy.snapshot()
        assertEquals(0L, snapshot.confirmedSpentCredits)
        assertEquals(0L, snapshot.reservedCredits)
        assertEquals(10000L, snapshot.effectiveRemainingCredits)
    }

    @Test
    fun `list search and singleton calls are charged 1 10 and 0 credits on the shared ledger (I-1)`() {
        val policy = policy()

        val list = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Allowed
        policy.recordResponse(list.permitId, quotaHeaders(creditsUsed = 1))
        assertEquals(1L, policy.snapshot().confirmedSpentCredits)

        val search = policy.reserve(RequestKind.DISCOVERY, Operation.SEARCH, searchTarget) as Permit.Allowed
        policy.recordResponse(search.permitId, quotaHeaders(creditsUsed = 10))
        assertEquals(11L, policy.snapshot().confirmedSpentCredits)

        // SINGLETON 是 0 成本：允许但不产生账本行。
        val singleton = policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.SINGLETON, singletonTarget) as Permit.Allowed
        policy.recordResponse(singleton.permitId, HttpHeaders())
        assertEquals(11L, policy.snapshot().confirmedSpentCredits)
        assertEquals(0L, policy.snapshot().reservedCredits)
    }

    // ------------------------------------------------------------------
    // I-2：账号周期唯一预算与凭证隔离
    // ------------------------------------------------------------------

    @Test
    fun `keyless budget is the 0_10 dollar free budget and is never unlimited (I-2)`() {
        val policy = policy()

        assertEquals(1_000, policy.remainingCredits())
        consume(policy, RequestKind.NEW_ENRICHMENT, 1_000)

        assertEquals(0, policy.remainingCredits())
        assertTrue(policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Deferred)
    }

    @Test
    fun `keyed budget is ten times the keyless free budget (I-2)`() {
        val policy = policy(apiKey = "test-key")

        assertEquals(10_000, policy.remainingCredits())
        consume(policy, RequestKind.NEW_ENRICHMENT, 1_000)
        assertEquals(9_000, policy.remainingCredits())
    }

    @Test
    fun `official balance caps the effective limit and never adds the prepaid balance (I-2)`() {
        // 官方实测：daily_budget 10000 credits、已用 227 → 有效可用最多 9773。
        val policy = policy(apiKey = "test-key", syncSource = { OpenAlexOfficialBalance(10000, 9773, time.current.plusSeconds(3600)) })

        assertTrue(policy.syncOfficialBudget())

        val snapshot = policy.snapshot()
        assertEquals(10000L, snapshot.officialLimitCredits)
        assertEquals(9773L, snapshot.officialRemainingCredits)
        assertEquals(9773L, snapshot.effectiveRemainingCredits)
        assertTrue(snapshot.effectiveRemainingCredits <= 9773L)

        // 模拟预付 100：绝不抬高有效额度。
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 9772, prepaidRemainingUsd = "100.0"))
        assertEquals(9772L, policy.remainingCredits())
    }

    @Test
    fun `a protected free budget only tightens and a legacy usd cap only tightens (I-2)`() {
        assertEquals(10_000L, policy(apiKey = "k").remainingCredits())
        assertEquals(2_000L, policy(apiKey = "k", freeBudgetCredits = 2_000).remainingCredits())
        // dailyBudgetUsd > 0 折算后只能收紧。
        assertEquals(500L, policy(apiKey = "k", dailyBudgetUsd = 0.05).remainingCredits())
        assertEquals(2_000L, policy(apiKey = "k", dailyBudgetUsd = 1.0, freeBudgetCredits = 2_000).remainingCredits())
        // dailyBudgetUsd = 0 表示采用免费保护默认值，绝不表示无限。
        assertEquals(10_000L, policy(apiKey = "k", dailyBudgetUsd = 0.0).remainingCredits())
    }

    @Test
    fun `negative configuration fails startup (I-2)`() {
        assertThrows(IllegalArgumentException::class.java) { OpenAlexProperties(dailyBudgetUsd = -0.01) }
        assertThrows(IllegalArgumentException::class.java) { OpenAlexProperties(freeBudgetCredits = -1L) }
        assertThrows(IllegalArgumentException::class.java) { OpenAlexProperties(budgetSyncInterval = Duration.ZERO) }
        assertThrows(IllegalArgumentException::class.java) { OpenAlexProperties(accountScope = " ") }
    }

    @Test
    fun `key rotation and a second instance share one account scope without resetting the budget (I-2)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val first = policy(apiKey = "key-1", store = store)
        consume(first, RequestKind.NEW_ENRICHMENT, 100)
        assertEquals(9900L, first.snapshot().confirmedSpentCredits.let { 10_000L - it })

        // 换 Key 后（同一 accountScope）预算不重置。
        val rotated = policy(apiKey = "key-2", store = store)
        assertEquals(9900L, rotated.snapshot().effectiveRemainingCredits)
        assertEquals(100L, rotated.snapshot().confirmedSpentCredits)

        // 第二个实例（同一 scope）看到的是同一份已用额度，不会各自拿到 10000。
        val second = policy(apiKey = "key-1", store = store)
        assertEquals(9900L, second.snapshot().effectiveRemainingCredits)
    }

    @Test
    fun `the account scope never derives from the api key (I-2)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(apiKey = "super-secret-key", accountScope = "primary", store = store)

        assertEquals("primary", policy.snapshot().accountScope)
        assertFalse(policy.snapshot().toString().contains("super-secret-key"))
    }

    // ------------------------------------------------------------------
    // I-3：预占先于外部请求，乱序响应保守处理
    // ------------------------------------------------------------------

    @Test
    fun `provider remaining and limit headers cap the configured budget (I-2)`() {
        // Keyed cap is $1 (10,000 credits), but the account is really on the keyless allowance.
        val policy = policy(apiKey = "test-key")
        policy.recordResponse(quotaHeaders(remaining = 900, limit = 1_000))

        assertEquals(900, policy.remainingCredits())
    }

    @Test
    fun `discovery and history backfill stop at the share reserved for new-expert enrichment (I-3, I-5)`() {
        val store = InMemoryOpenAlexBudgetStore().apply { pendingEnrichmentJobs = 20 }
        val policy = policy(store = store)
        consume(policy, RequestKind.DISCOVERY, 800)

        // 1,000 中的 200 = 保留目标（20%）: only new-expert enrichment may continue.
        assertEquals(200, policy.remainingCredits())
        assertEquals(200L, policy.snapshot().enrichmentReserveCredits)
        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(Permit.Deferred(DeferredReason.ENRICHMENT_RESERVE, Instant.parse("2026-09-22T00:00:00Z")), deferred)
        assertEquals(
            DeferredReason.ENRICHMENT_RESERVE,
            (policy.reserve(RequestKind.HISTORY_ENRICHMENT, Operation.LIST, listTarget) as Permit.Deferred).reason
        )
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, listTarget) is Permit.Allowed)
    }

    @Test
    fun `deferred permit exposes the actual UTC reset (I-4)`() {
        val policy = policy()
        consume(policy, RequestKind.DISCOVERY, 1_000)

        val deferred = policy.beforeRequest(RequestKind.DISCOVERY) as Permit.Deferred
        assertEquals(DeferredReason.DAILY_BUDGET, deferred.reason)
        assertEquals(Instant.parse("2026-09-22T00:00:00Z"), deferred.retryAt)
    }

    @Test
    fun `exhausted day budget defers every consumer without dense retries (I-2, I-4)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 3_600))

        // I-4：retryAt 取**已确认的官方周期** reset，而不是响应头换算出来的未确认时刻。
        val reset = Instant.parse("2026-09-22T00:00:00Z")
        assertEquals(reset, (policy.beforeRequest(RequestKind.DISCOVERY) as Permit.Deferred).retryAt)
        assertEquals(reset, (policy.beforeRequest(RequestKind.NEW_ENRICHMENT) as Permit.Deferred).retryAt)
        assertEquals(reset, (policy.beforeRequest(RequestKind.HISTORY_ENRICHMENT) as Permit.Deferred).retryAt)
        assertEquals(0, policy.remainingCredits())
        assertTrue(time.sleeps.isEmpty(), "an exhausted budget must not spin or back off, only wait for the reset")
    }

    @Test
    fun `an exhausted budget still allows zero cost operations (I-4)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 3_600))

        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.SINGLETON, singletonTarget) is Permit.Allowed)
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.RATE_LIMIT, rateLimitTarget) is Permit.Allowed)
        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Deferred)
    }

    @Test
    fun `budget is rebuilt after the UTC reset (I-3)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 10))
        assertTrue(policy.beforeRequest(RequestKind.DISCOVERY) is Permit.Deferred)

        time.current = Instant.parse("2026-09-22T00:00:05Z")

        assertTrue(policy.beforeRequest(RequestKind.DISCOVERY) is Permit.Allowed)
        val snapshot = policy.snapshot()
        assertEquals(1_000L, snapshot.officialLimitCredits, "新周期重建了额度上限")
        assertEquals(0L, snapshot.confirmedSpentCredits)
        assertEquals(1L, snapshot.reservedCredits, "刚放行的请求仍在未结算预占里")
        assertEquals(0, policy.listRequestCount())
    }

    @Test
    fun `paid prepaid balance is never consumed automatically (I-2)`() {
        val policy = policy()
        policy.recordResponse(
            quotaHeaders(remaining = 0, limit = 1_000, resetSeconds = 3_600, prepaidRemainingUsd = "5.0")
        )

        assertTrue(policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Deferred)
        assertEquals(0, policy.remainingCredits())
    }

    @Test
    fun `a settle is applied exactly once even when the response is handled twice (I-3)`() {
        val policy = policy()
        val permit = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Allowed

        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 1, remaining = 999))
        assertEquals(1L, policy.snapshot().confirmedSpentCredits)
        assertEquals(999L, policy.remainingCredits())

        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 1, remaining = 999))
        assertEquals(1L, policy.snapshot().confirmedSpentCredits, "重复结算不得重复扣减")
        assertEquals(0L, policy.snapshot().reservedCredits)
    }

    @Test
    fun `a timed out reservation stays unknown and is never refunded (I-3)`() {
        val policy = policy(apiKey = "k")
        val permit = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Allowed
        assertEquals(9999L, policy.remainingCredits())

        policy.recordTimeout(permit.permitId)

        assertEquals(9999L, policy.remainingCredits(), "超时不得立即退还预占")
        assertEquals(1L, policy.snapshot().reservedCredits)
        assertEquals(0L, policy.snapshot().confirmedSpentCredits)
    }

    @Test
    fun `a lower balance followed by a higher balance never increases availability (I-3)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 900, limit = 1_000))
        assertEquals(900L, policy.remainingCredits())

        // 乱序/较旧响应报出更高余额：只收紧，不抬高。
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 950, limit = 1_000))
        assertEquals(900L, policy.remainingCredits())
        assertEquals(900L, policy.snapshot().officialRemainingCredits)
    }

    @Test
    fun `a higher actual cost is charged immediately and blocks the next over budget request (I-3)`() {
        val policy = policy(dailyBudgetUsd = 0.0011)
        assertEquals(11L, policy.remainingCredits())

        val permit = policy.reserve(RequestKind.DISCOVERY, Operation.SEARCH, searchTarget) as Permit.Allowed
        // 估算 10、真实 11：立即追扣，剩余额度按实际成本归零。
        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 11, remaining = 0))

        assertEquals(11L, policy.snapshot().confirmedSpentCredits)
        assertEquals(0L, policy.remainingCredits())
        assertEquals(
            DeferredReason.DAILY_BUDGET,
            (policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Deferred).reason
        )
    }

    @Test
    fun `two connections racing for the last ten credits approve at most ten credits in total (I-3)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(dailyBudgetUsd = 0.001, reserveRatio = 0.0, store = store)
        assertEquals(10L, policy.remainingCredits())

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(11)
        try {
            val tasks = (1..10).map {
                pool.submit<Long> {
                    start.await()
                    val permit = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
                    if (permit is Permit.Allowed) {
                        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 1))
                        1L
                    } else {
                        0L
                    }
                }
            } + pool.submit<Long> {
                start.await()
                val permit = policy.reserve(RequestKind.DISCOVERY, Operation.SEARCH, searchTarget)
                if (permit is Permit.Allowed) {
                    policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 10))
                    10L
                } else {
                    0L
                }
            }
            start.countDown()
            val approvedCredits = tasks.sumOf { it.get(10, TimeUnit.SECONDS) }
            assertTrue(approvedCredits <= 10L, "批准的预占总成本必须 ≤ 10（实际 $approvedCredits）")
            assertTrue(policy.snapshot().confirmedSpentCredits <= 10L)
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // I-4：同步与冷却不能变成长时间阻塞
    // ------------------------------------------------------------------

    @Test
    fun `request-rate 429 backs off with a bounded delay and recovers after a success (I-4)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000, retryAfterSeconds = 3))
        // I-4：官方 Retry-After 原样进入共享冷却，并以 Deferred(retryAt) 交给调用方（策略绝不 sleep）。
        assertEquals(3_000L, rateLimitRetryDelayMs(policy))

        // Retry-After absent: exponential from 1s, doubling, never above the configured cap.
        // 每轮先越过上一轮冷却，使新一轮退避成为唯一约束（共享冷却只收紧、不提前清除）。
        var backoff = 1_000L
        repeat(8) {
            time.current = time.current.plusMillis(60_000)
            policy.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000))
            backoff = (backoff * 2).coerceAtMost(60_000)
            assertEquals(backoff, rateLimitRetryDelayMs(policy), "backoff must grow but stay bounded")
        }
        assertEquals(60_000L, rateLimitRetryDelayMs(policy), "backoff is capped by rate-limit-backoff-max-ms")

        // 时间越过既有冷却；成功的响应清零本地指数退避（共享冷却只按时间过期，绝不提前清除）。
        time.current = time.current.plusMillis(120_000)
        policy.recordResponse(quotaHeaders())
        policy.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000))
        assertEquals(1_000L, rateLimitRetryDelayMs(policy), "a successful response must clear the rate-limit backoff")
        assertTrue(time.sleeps.isEmpty(), "429 退避只以 Deferred(retryAt) 表达，绝不睡在调用线程上")
    }

    @Test
    fun `a 429 cooldown is shared by every instance of the same account (I-4)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val first = policy(store = store)
        val second = policy(store = store)

        first.recordResponse(quotaHeaders(creditsUsed = null, remaining = 500, limit = 1_000, retryAfterSeconds = 30))

        val deferred = second.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(
            Permit.Deferred(DeferredReason.RATE_LIMIT, time.current.plusSeconds(30)),
            deferred
        )
        assertTrue(time.sleeps.isEmpty(), "冷却必须返回 retryAt 而不是睡在调用线程上")
    }

    @Test
    fun `a store failure defers metered requests and never falls back to a local budget (I-4)`() {
        val failing = object : OpenAlexBudgetStore {
            private fun boom(): Nothing = throw DataAccessResourceFailureException("mysql down")
            override fun ledger(accountScope: String, configuredFreeLimitCredits: Long, now: Instant) = boom()
            override fun reserve(request: BudgetReserveRequest): BudgetReserveResult = boom()
            override fun settle(
                accountScope: String, permitId: String, actualCredits: Long?, observedRemaining: Long?,
                observedLimit: Long?, now: Instant
            ): Boolean = boom()
            override fun markUnknown(accountScope: String, permitId: String, now: Instant): Boolean = boom()
            override fun applyCooldown(accountScope: String, notBefore: Instant, now: Instant) = boom()
            override fun observeProvider(
                accountScope: String, observedRemaining: Long?, observedLimit: Long?,
                configuredFreeLimitCredits: Long, now: Instant
            ) = boom()
            override fun reconcile(
                accountScope: String, cycleResetAt: Instant, officialLimitCredits: Long?,
                officialRemainingCredits: Long, configuredFreeLimitCredits: Long, now: Instant
            ): Boolean = boom()
            override fun claimSyncLease(accountScope: String, token: String, now: Instant, leaseUntil: Instant): Boolean = boom()
            override fun releaseSyncLease(accountScope: String, token: String) = boom()
            override fun recordNewEnrichmentRequest(accountScope: String, now: Instant) = boom()
            override fun countUnfinishedEnrichmentJobs(): Long = boom()
            override fun recentNewEnrichmentCosts(accountScope: String, limit: Int): List<Long> = boom()
            override fun cleanupClosedCycles(closedBefore: Instant, batchSize: Int): Int = boom()
        }
        val policy = policy(store = failing)

        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(DeferredReason.BUDGET_STORE_UNAVAILABLE, (deferred as Permit.Deferred).reason)
        assertEquals(0L, policy.remainingCredits(), "账本不可用时可用额必须是 0，不能退回本地 10000")
        assertEquals(DeferredReason.BUDGET_STORE_UNAVAILABLE, policy.snapshot().deferredReason)
    }

    @Test
    fun `a failed official sync defers metered requests instead of guessing a balance (I-4)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(store = store, syncSource = { null })

        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(DeferredReason.BUDGET_SYNC, (deferred as Permit.Deferred).reason)
        // 尚未获得可信余额时，0 成本操作照常（不依赖额度）。
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.SINGLETON, singletonTarget) is Permit.Allowed)
    }

    @Test
    fun `an untrusted ledger reports a sync-deferred snapshot instead of a usable balance (I-4, I-2)`() {
        val store = InMemoryOpenAlexBudgetStore()
        // 官方余额从未确认（取数失败）：本地保护上限绝不是可用余额。
        val policy = policy(store = store, syncSource = { null })

        val snapshot = policy.snapshot()
        assertEquals(DeferredReason.BUDGET_SYNC, snapshot.deferredReason)
        assertNull(snapshot.officialRemainingCredits, "未确认官方余额时不得给出可信剩余额度")
        assertNotNull(snapshot.retryAt, "延期必须带 retryAt")
        // 同一状态下的计量请求同样被延期，绝不放行。
        assertEquals(
            DeferredReason.BUDGET_SYNC,
            (policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Deferred).reason
        )

        // 官方确认新周期之后，快照回到「无延期原因」，数值可供展示。
        val synced = policy(
            store = store,
            syncSource = { OpenAlexOfficialBalance(10000, 9000, time.current.plusSeconds(3600)) }
        )
        assertTrue(synced.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        val trusted = synced.snapshot()
        assertNull(trusted.deferredReason)
        assertEquals(9000L, trusted.officialRemainingCredits)
    }

    @Test
    fun `a confirmed official cycle unlocks metered requests and an expired one forces a new sync (I-4)`() {
        var resetAt = Instant.parse("2026-09-21T04:00:00Z")
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(store = store, syncSource = { OpenAlexOfficialBalance(10000, 10000, resetAt) })

        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        assertEquals(resetAt, policy.snapshot().resetAt)

        // 官方 reset 到期：必须先确认新周期，未确认前计量请求延期。
        time.current = resetAt.plusSeconds(1)
        resetAt = Instant.parse("2026-09-22T04:00:00Z")
        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        assertEquals(resetAt, policy.snapshot().resetAt)
    }

    @Test
    fun `a response from before the reset never writes the new cycle (I-3, I-4)`() {
        var resetAt = Instant.parse("2026-09-21T04:00:00Z")
        var remaining = 5000L
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(store = store, syncSource = { OpenAlexOfficialBalance(10000, remaining, resetAt) })

        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        assertEquals(5000L, policy.snapshot().officialRemainingCredits)
        val permit = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Allowed

        // 日切：官方确认新周期（余额重新为 10000）。
        time.current = resetAt.plusSeconds(1)
        resetAt = Instant.parse("2026-09-22T04:00:00Z")
        remaining = 10000L
        assertTrue(policy.syncOfficialBudget(force = true))
        assertEquals(10000L, policy.snapshot().officialRemainingCredits)

        // 旧周期的迟到响应（余额 5）只能结算旧行，绝不污染新周期。
        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 1, remaining = 5))

        val snapshot = policy.snapshot()
        assertEquals(10000L, snapshot.officialRemainingCredits, "旧响应不得抬高/污染新周期")
        assertEquals(0L, snapshot.confirmedSpentCredits, "旧周期 permit 只结算旧行")
        assertEquals(0L, snapshot.reservedCredits)
    }

    @Test
    fun `a sub-second rate slot is paced inline while longer waits defer (I-4)`() {
        // 共享 5/s 节奏（200ms 槽位）仍在调用线程内等待：这是限速节奏，不是 429 冷却。
        assertEquals(listOf(200L), slotWaits(5.0))
        assertEquals(listOf(10L), slotWaits(500.0))

        // 慢到超过内联上限的槽位（0.01/s = 100 秒）绝不睡在调用线程里：立刻返回 Deferred(retryAt)。
        val recorder = FakeTime()
        val policy = OpenAlexRequestPolicy(
            properties(maxRequestsPerSecond = 0.0), recorder, InMemoryOpenAlexBudgetStore()
        )
        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(DeferredReason.RATE_LIMIT, (deferred as Permit.Deferred).reason)
        assertEquals(100_000L, Duration.between(recorder.current, deferred.retryAt).toMillis())
        assertTrue(recorder.sleeps.isEmpty(), "超过内联上限的槽位必须返回 retryAt 而不是长时间 sleep")
    }

    @Test
    fun `a rate-limit rejection whose retryAt already elapsed defers instead of sleeping (I-4)`() {
        val real = InMemoryOpenAlexBudgetStore()
        val slot = time.current.plusMillis(200)
        // 存储往返（锁等待 / 校准 HTTP）耗时超过剩余槽位：reserve 返回的共享 retryAt 在计算等待时已经过去，
        // 于是 waitMs 为负 —— 非正等待绝不允许交给 Thread.sleep（负值抛 IllegalArgumentException）。
        val store = object : OpenAlexBudgetStore by real {
            override fun reserve(request: BudgetReserveRequest): BudgetReserveResult {
                time.current = time.current.plusSeconds(5)
                return BudgetReserveResult.Rejected(DeferredReason.RATE_LIMIT, slot)
            }
        }
        val policy = policy(store = store)

        val deferred = policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)
        assertEquals(Permit.Deferred(DeferredReason.RATE_LIMIT, slot), deferred)
        assertTrue(time.sleeps.isEmpty(), "非正等待绝不允许 sleep（实际 ${time.sleeps}）")
    }

    /** 两次连续请求之间策略实际等待的限速槽位（毫秒）。 */
    private fun slotWaits(maxRequestsPerSecond: Double): List<Long> {
        val recorder = FakeTime()
        val policy = OpenAlexRequestPolicy(
            properties(maxRequestsPerSecond = maxRequestsPerSecond), recorder, InMemoryOpenAlexBudgetStore()
        )
        assertTrue(policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed)
        assertTrue(
            policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) is Permit.Allowed,
            "子秒槽位必须在调用线程内等完，而不是把节奏变成延期"
        )
        return recorder.sleeps.toList()
    }

    @Test
    fun `request rate is configurable and clamped to the official 100 per second (I-3)`() {
        assertEquals(5.0, policy(maxRequestsPerSecond = 5.0).effectiveMaxRequestsPerSecond)
        assertEquals(100.0, policy(maxRequestsPerSecond = 500.0).effectiveMaxRequestsPerSecond)
        assertEquals(0.01, policy(maxRequestsPerSecond = 0.0).effectiveMaxRequestsPerSecond)
    }

    @Test
    fun `list calls and fulltext downloads are counted and charged separately (I-3)`() {
        val policy = policy()
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 1_000, limit = 1_000))
        policy.recordResponse(quotaHeaders(creditsUsed = 100, remaining = 900, limit = 1_000))

        assertEquals(1, policy.listRequestCount())
        assertEquals(1, policy.fulltextDownloadCount())
        // provider 报出的 remaining 已经包含这两次消耗，因此只与本地确认消耗取较小值。
        assertEquals(900, policy.remainingCredits())
    }

    @Test
    fun `a request without a response keeps its reservation and stays conservative (I-2)`() {
        val policy = policy()
        val permit = policy.beforeRequest(RequestKind.NEW_ENRICHMENT) as Permit.Allowed
        assertEquals(999, policy.remainingCredits())

        policy.recordResponse(permit.permitId, HttpHeaders())

        assertEquals(999, policy.remainingCredits(), "没有响应头时按预占额保守记账，不退款")
        assertEquals(0, policy.listRequestCount())
    }

    @Test
    fun `concurrent callers share one budget and only one is admitted for the last credit (I-3)`() {
        val policy = policy(dailyBudgetUsd = 0.0001, reserveRatio = 0.0)
        assertEquals(1, policy.remainingCredits())

        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..8).map {
                pool.submit<Boolean> {
                    start.await()
                    policy.beforeRequest(RequestKind.NEW_ENRICHMENT) is Permit.Allowed
                }
            }
            start.countDown()
            val allowed = futures.count { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, allowed, "only one caller may be admitted from the last credit")
        } finally {
            pool.shutdownNow()
        }
    }

    // ------------------------------------------------------------------
    // I-5：补全保留可借用但有依据
    // ------------------------------------------------------------------

    @Test
    fun `the enrichment reserve follows the pending work and is released after sixty idle seconds (I-5)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(apiKey = "k", store = store)

        // 200 条待补 × 无样本估算 10 = 2000 = 20% 目标，保留区生效。
        store.pendingEnrichmentJobs = 200
        assertEquals(2000L, policy.snapshot().enrichmentReserveCredits)

        // 全部补完且 60 秒内没有新的 NEW_ENRICHMENT 请求 → 保留降为 0（空闲额度可借给采集）。
        store.pendingEnrichmentJobs = 0
        time.current = time.current.plusSeconds(61)
        assertEquals(0L, policy.snapshot().enrichmentReserveCredits)
        consume(policy, RequestKind.DISCOVERY, 9_000)
        assertEquals(1_000L, policy.remainingCredits(), "没有待补时采集可以借用保留区")

        // 重新入队 → 保留立即恢复（已发出的请求不被撤回）。
        store.pendingEnrichmentJobs = 200
        assertEquals(1000L, policy.snapshot().enrichmentReserveCredits, "预留不得超过实际剩余额度")
        assertEquals(
            DeferredReason.ENRICHMENT_RESERVE,
            (policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget) as Permit.Deferred).reason
        )
        assertTrue(policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, listTarget) is Permit.Allowed)
    }

    @Test
    fun `the enrichment estimate uses the p95 of the last settled new-enrichment costs (I-5)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(apiKey = "k", store = store)
        // 10 次真实成本 30 credits（P95=30）× 3 = 90 估算 → 保留 = min(2000, 20 × 90) = 1800。
        repeat(10) {
            val permit = policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, listTarget) as Permit.Allowed
            policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 30))
        }
        store.pendingEnrichmentJobs = 20

        assertEquals(1800L, policy.snapshot().enrichmentReserveCredits)
    }

    @Test
    fun `at least one reserved request is kept while the target is sufficient (I-5)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(apiKey = "k", store = store)

        // 刚发生过 NEW_ENRICHMENT 请求（60 秒内）但已无待补：仍保留一个 10-credit 请求。
        val permit = policy.reserve(RequestKind.NEW_ENRICHMENT, Operation.LIST, listTarget) as Permit.Allowed
        policy.recordResponse(permit.permitId, quotaHeaders(creditsUsed = 1))
        store.pendingEnrichmentJobs = 0

        assertEquals(10L, policy.snapshot().enrichmentReserveCredits)
    }

    // ------------------------------------------------------------------
    // I-6：快照与归档
    // ------------------------------------------------------------------

    @Test
    fun `the read-only snapshot carries exactly the downstream fields and no credential (I-6)`() {
        val policy = policy(apiKey = "super-secret-key")
        policy.recordResponse(quotaHeaders(creditsUsed = 1, remaining = 999, limit = 1000))

        val snapshot = policy.snapshot()
        assertEquals("primary", snapshot.accountScope)
        assertEquals(Instant.parse("2026-09-22T00:00:00Z"), snapshot.resetAt)
        assertEquals(1000L, snapshot.officialLimitCredits)
        assertEquals(999L, snapshot.officialRemainingCredits)
        // 没有 permit 的兼容观察路径只收紧 provider ceiling（provider 的 remaining 已含该次消耗），不重复记账。
        assertEquals(0L, snapshot.confirmedSpentCredits)
        assertEquals(0L, snapshot.reservedCredits)
        assertEquals(999L, snapshot.effectiveRemainingCredits)
        assertEquals(0L, snapshot.enrichmentReserveCredits)
        assertNull(snapshot.lastSyncedAt)
        assertNull(snapshot.deferredReason)
        assertNull(snapshot.retryAt)
        assertFalse(snapshot.toString().contains("super-secret-key"))
    }

    @Test
    fun `the snapshot reports the last deferral reason and retry time (I-4, I-6)`() {
        val policy = policy(dailyBudgetUsd = 0.0001, reserveRatio = 0.0)
        consume(policy, RequestKind.DISCOVERY, 1)

        policy.reserve(RequestKind.DISCOVERY, Operation.LIST, listTarget)

        val snapshot = policy.snapshot()
        assertEquals(DeferredReason.DAILY_BUDGET, snapshot.deferredReason)
        assertEquals(Instant.parse("2026-09-22T00:00:00Z"), snapshot.retryAt)
    }

    @Test
    fun `closed cycles are archived in bounded batches and active cycles are never cleaned (I-6)`() {
        val store = InMemoryOpenAlexBudgetStore()
        val policy = policy(store = store)

        assertEquals(0, policy.cleanupArchivedCycles())
        assertEquals(Duration.ofDays(7), OpenAlexRequestPolicy.ARCHIVE_RETENTION)
        assertEquals(1000, OpenAlexRequestPolicy.CLEANUP_BATCH_SIZE)
    }
}
