package com.weibo.talentintroduction.config

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Which consumer is spending the shared OpenAlex budget. Callers name the context explicitly; it is never inferred
 * from the thread that happens to run the request. Priority order (highest first):
 * [NEW_ENRICHMENT] > [DISCOVERY] > [HISTORY_ENRICHMENT].
 */
enum class RequestKind(val mayUseReservedBudget: Boolean) {
    /** Academic enrichment of newly discovered experts; the only consumer allowed inside the reserved share. */
    NEW_ENRICHMENT(true),

    /** Paper discovery (list calls against `/works`). */
    DISCOVERY(false),

    /** Backfill of experts already stored; lowest priority, never allowed inside the reserved share. */
    HISTORY_ENRICHMENT(false)
}

/**
 * I-1：一次 OpenAlex 调用的**成本类别**，与用途（[RequestKind]）严格分离。
 *
 * 费率依据官方 example-costs：列表/过滤 1 credit、关键词或语义搜索 10 credits、单实体 0、
 * Content 下载 100 credits、`/rate-limit` 自身 0。费用常量集中在本枚举一处，官方费率变动必须显式更新，
 * 不自动启用付费。调用方在构造请求时**明确传入**本操作，随后由 [OpenAlexRequestPolicy.reserve]
 * 校验目标主机与路径是否与声明的操作一致 —— 绝不按 URL 任意 substring 猜测。
 */
enum class Operation(val costCredits: Long) {
    /** 列表/过滤查询（`/works`、`/authors?filter=` 等），1 credit。 */
    LIST(1L),

    /** 关键词/语义搜索（`search=` 或 `*.search:` 过滤），10 credits。 */
    SEARCH(10L),

    /** 单实体读取（`/authors/{id}`），0 credit。 */
    SINGLETON(0L),

    /** 计量全文下载（Content API），100 credits。本阶段一律拒绝新增调用。 */
    CONTENT(100L),

    /** 官方 `/rate-limit` 校准调用，0 credit。 */
    RATE_LIMIT(0L)
}

/**
 * I-4：延期（[Permit.Deferred]）的机器可读原因，供调用方与操作端区分「额度不足 / 限速 / 尚未校准 / 账本不可用 / 补全保留区」。
 */
enum class DeferredReason {
    /** 本周期免费额度已耗尽或不足以覆盖本次预占。 */
    DAILY_BUDGET,

    /** 账号级 429 冷却或限速槽位未到（共享 notBefore）。 */
    RATE_LIMIT,

    /** 尚未获得可信的官方余额（未校准成功），或官方 reset 已到期但新周期未确认。 */
    BUDGET_SYNC,

    /** 共享账本（MySQL）不可用：绝不退回本地内存额度。 */
    BUDGET_STORE_UNAVAILABLE,

    /** 只属于新专家补全的保留份额挡住了本次采集/历史补全。 */
    ENRICHMENT_RESERVE
}

/** Result of asking the shared policy for permission to call OpenAlex. */
sealed class Permit {
    /** I-3：permit 唯一，结算恰好一次（settle 或 markUnknown）。 */
    data class Allowed(val permitId: String) : Permit()

    /** The call must not be issued. [reason] is machine readable; [retryAt] is the earliest sensible retry. */
    data class Deferred(val reason: DeferredReason, val retryAt: Instant) : Permit()
}

/**
 * Raised instead of calling OpenAlex when [OpenAlexRequestPolicy.reserve] returned [Permit.Deferred].
 * Extends [IllegalStateException] so the repository-wide exception-to-HTTP mapping stays predictable.
 *
 * [resetAt] keeps its historical meaning ("when the budget is expected back"); [retryAt] is the same instant and
 * [reason] says why. The single-argument constructor keeps existing callers/tests source compatible.
 */
class OpenAlexBudgetDeferredException(
    val reason: DeferredReason,
    val retryAt: Instant,
    val resetAt: Instant = retryAt
) : IllegalStateException("OpenAlex budget deferred ($reason) until $retryAt") {
    constructor(resetAt: Instant) : this(DeferredReason.DAILY_BUDGET, resetAt, resetAt)
}

/**
 * I-1：本阶段拒绝任何**新增**的计量 Content 下载（100 credits/次）。公开出版社/PMC 全文不属于 OpenAlex 计量范围，
 * 仍走原有公开链接路径。
 */
class OpenAlexContentDisabledException(message: String) : IllegalStateException(message)

/**
 * I-1/I-2：OpenAlex 的计量目标主机。Content API 与 API 本体的下载路径一律不作为公开全文候选，
 * 也不作为重定向的落点 —— 两者都会消耗账号额度并可能带上凭证。
 */
object OpenAlexMeteredDestinations {
    const val API_HOST = "api.openalex.org"
    const val CONTENT_HOST = "content.openalex.org"

    /** 目标主机是否是 OpenAlex 计量主机（Content API 或 API 本体）。无法解析的 URL 视为非计量。 */
    fun isMetered(url: String?): Boolean {
        val host = hostOf(url) ?: return false
        return host == API_HOST || host == CONTENT_HOST
    }

    private fun hostOf(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            URI.create(trimmed).host?.lowercase()
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Clock/sleep seam so the rate limiting, the day rollover and the shared-ledger timestamps can be driven
 * deterministically in tests. `now()` is the single clock: the ledger stores UTC instants, so a sleeping caller
 * must see time advance (no separate monotonic source).
 */
interface PolicyTimeSource {
    fun now(): Instant
    fun sleep(ms: Long)

    companion object {
        val SYSTEM: PolicyTimeSource = object : PolicyTimeSource {
            override fun now(): Instant = Instant.now()
            override fun sleep(ms: Long) = Thread.sleep(ms)
        }
    }
}

// ---------------------------------------------------------------------------
// I-2/I-3/I-6：共享账本（生产实现 = MySQL，见 discovery/repository/OpenAlexBudgetRepository）
// ---------------------------------------------------------------------------

/**
 * I-6：账号级账本状态 —— 当前已确认的官方周期、免费上限、确认消耗、未结算预占与 provider ceiling
 * 分别读取，调用方不得把「可用」值再当原始余额扣减。
 *
 * [cycleResetAt] 为 null 表示**尚无已确认的官方周期**：此时计量请求必须延期（[DeferredReason.BUDGET_SYNC]）。
 */
data class OpenAlexBudgetLedger(
    val accountScope: String,
    val cycleResetAt: Instant?,
    val freeLimitCredits: Long,
    val providerCeilingCredits: Long?,
    val confirmedSpentCredits: Long,
    val outstandingReservedCredits: Long,
    val cooldownUntil: Instant?,
    val rateNextAt: Instant,
    val lastSyncedAt: Instant?,
    val lastNewEnrichmentAt: Instant?
) {
    /**
     * I-3：可用额 = min(免费上限 − 确认消耗, provider ceiling) − 全部未结算预占。
     * provider ceiling 已经包含 provider 侧的同一次消耗，因此只与「本地确认消耗」取较小值，不重复相减。
     */
    val effectiveRemainingCredits: Long
        get() {
            val localBound = (freeLimitCredits - confirmedSpentCredits).coerceAtLeast(0)
            val bounded = providerCeilingCredits?.let { minOf(localBound, it) } ?: localBound
            return (bounded - outstandingReservedCredits).coerceAtLeast(0)
        }
}

/** I-3：一次预占请求。事务内按固定锁序 account → day → reservation 执行，网络调用一律在事务之外。 */
data class BudgetReserveRequest(
    val accountScope: String,
    val permitId: String,
    val kind: RequestKind,
    val operation: Operation,
    val estimatedCredits: Long,
    /** 不可借用补全保留区的用途（采集/历史补全）必须守住的保留下限；[RequestKind.NEW_ENRICHMENT] 恒为 0。 */
    val reservedFloorCredits: Long,
    val rateIntervalMs: Long,
    val configuredFreeLimitCredits: Long,
    val now: Instant
)

/** I-3：预占结果。Rejected 不产生任何账本行。 */
sealed class BudgetReserveResult {
    data class Approved(
        /** 0 成本操作在没有已确认周期时也可以是 null（不消耗额度，不需要周期）。 */
        val cycleResetAt: Instant?,
        val rateNextAt: Instant,
        val effectiveRemainingCredits: Long
    ) : BudgetReserveResult()

    data class Rejected(val reason: DeferredReason, val retryAt: Instant) : BudgetReserveResult()
}

/**
 * I-2/I-3/I-4/I-6：共享预算账本的存储契约。生产 Bean 必须注入 JDBC 实现；单元测试可注入内存实现。
 * 实现必须保证每个方法自成一个事务/临界区，且 reserve 内部先锁账号行再锁周期行。
 */
interface OpenAlexBudgetStore {

    /** 只读当前状态；[configuredFreeLimitCredits] 用于「尚未同步」时给出配置保护上限。 */
    fun ledger(accountScope: String, configuredFreeLimitCredits: Long, now: Instant): OpenAlexBudgetLedger

    fun reserve(request: BudgetReserveRequest): BudgetReserveResult

    /**
     * I-3：结算恰好一次。`actualCredits = null` 表示没有 provider 证据（失败/超时）—— 按预占额保守记账，
     * 不退款；较小的真实成本只退还该 permit 有证据的差额；较大的真实成本立即追扣。
     * [observedRemaining] 只用于收紧本 permit 所在周期的 provider ceiling。
     */
    fun settle(
        accountScope: String,
        permitId: String,
        actualCredits: Long?,
        observedRemaining: Long?,
        observedLimit: Long?,
        now: Instant
    ): Boolean

    /** I-3：超时/响应丢失 → UNKNOWN，**不退还**预占（仍计入未结算）。 */
    fun markUnknown(accountScope: String, permitId: String, now: Instant): Boolean

    /** I-4：429 冷却写入共享 notBefore（跨实例可见），只收紧不提前清除。 */
    fun applyCooldown(accountScope: String, notBefore: Instant, now: Instant)

    /** 没有 permit 的兼容路径：只观察 provider 数值（ceiling/limit 只收紧），不产生账本行。 */
    fun observeProvider(
        accountScope: String,
        observedRemaining: Long?,
        observedLimit: Long?,
        configuredFreeLimitCredits: Long,
        now: Instant
    )

    /**
     * I-2/I-4：官方校准。只接受**本周期**（[cycleResetAt] 晚于 [now]）的非陈旧快照；确认新 reset 时开启新周期
     * 并关闭旧周期（旧周期未完成 permit 只结算旧行）。返回是否被接受。
     */
    fun reconcile(
        accountScope: String,
        cycleResetAt: Instant,
        officialLimitCredits: Long?,
        officialRemainingCredits: Long,
        configuredFreeLimitCredits: Long,
        now: Instant
    ): Boolean

    /** I-4：同账号只允许一个校准者（租约）。 */
    fun claimSyncLease(accountScope: String, token: String, now: Instant, leaseUntil: Instant): Boolean

    fun releaseSyncLease(accountScope: String, token: String)

    /** I-5：记录最近一次 NEW_ENRICHMENT 请求时刻（保留区的空闲释放依据）。 */
    fun recordNewEnrichmentRequest(accountScope: String, now: Instant)

    /** I-5：只读现有补全任务，PENDING/RUNNING/RETRY_WAIT 均算未完成。 */
    fun countUnfinishedEnrichmentJobs(): Long

    /** I-5：最近 [limit] 次已结算 NEW_ENRICHMENT 请求的真实成本样本。 */
    fun recentNewEnrichmentCosts(accountScope: String, limit: Int): List<Long>

    /** I-6：已关闭周期超过保留期后按批归档/删除；活跃周期与未解决 permit 不清理。返回删除的周期数。 */
    fun cleanupClosedCycles(closedBefore: Instant, batchSize: Int): Int
}

/** 官方 `GET /rate-limit` 的余额快照（不含任何凭证）。 */
data class OpenAlexOfficialBalance(
    val limitCredits: Long?,
    val remainingCredits: Long,
    val resetAt: Instant?
)

/**
 * I-2/I-4：官方余额的取数接缝。生产实现由 `RestTemplateConfig` 用带认证拦截器的 OpenAlex client 构造；
 * 单元测试注入可控假实现。取数失败（null / 抛异常）一律视为「未获得可信余额」。
 */
fun interface OpenAlexBudgetSyncSource {
    fun fetchOfficialBalance(): OpenAlexOfficialBalance?
}

/**
 * 官方 `/rate-limit` 响应 → [OpenAlexOfficialBalance]。字段名按官方文档（`credits_limit` / `credits_remaining` /
 * `daily_budget_usd` / `daily_remaining_usd`），同时接受 `rate_limit` 子对象或根对象；credits 缺失时按
 * 1 credit = $0.0001 从美元字段换算。**任何必需字段都缺失时返回 null**（宁可延期，绝不猜一个额度）。
 */
fun parseOpenAlexOfficialBalance(root: JsonNode): OpenAlexOfficialBalance? {
    val node = root.path("rate_limit").takeIf { it.isObject } ?: root
    val limit = creditField(node, "credits_limit") ?: usdField(node, "daily_budget_usd")
    val remaining = creditField(node, "credits_remaining") ?: usdField(node, "daily_remaining_usd")
    if (remaining == null) return null
    val resetAt = resetField(node)
    return OpenAlexOfficialBalance(limitCredits = limit, remainingCredits = remaining, resetAt = resetAt)
}

private fun creditField(node: JsonNode, name: String): Long? {
    val value = node.path(name)
    return if (value.isNumber) value.asDouble().toLong().coerceAtLeast(0L) else null
}

private fun usdField(node: JsonNode, name: String): Long? {
    val value = node.path(name)
    if (!value.isNumber) return null
    return (value.asDouble() * OpenAlexRequestPolicy.CREDITS_PER_USD).toLong().coerceAtLeast(0L)
}

private fun resetField(node: JsonNode): Instant? {
    for (name in listOf("resets_at", "reset_at", "reset")) {
        val text = node.path(name).asText(null) ?: continue
        val parsed = try {
            Instant.parse(text)
        } catch (e: Exception) {
            null
        }
        if (parsed != null) return parsed
    }
    return null
}

// ---------------------------------------------------------------------------
// 测试用内存账本（生产永不使用：见 OpenAlexRequestPolicy 的构造注释）
// ---------------------------------------------------------------------------

/**
 * 单元测试用的内存账本，逐字复刻旧的内存策略语义（本地 UTC 日切、单进程锁），并提供可注入的
 * 补全任务数/成本样本。**生产 Bean 一律注入 JDBC 账本**，本类不参与任何生产装配。
 */
internal class InMemoryOpenAlexBudgetStore : OpenAlexBudgetStore {

    private class Reservation(val dayResetAt: Instant, val kind: RequestKind, val reserved: Long) {
        var status: String = STATUS_RESERVED
        var actual: Long? = null
    }

    private val lock = Any()
    private val reservations = LinkedHashMap<String, Reservation>()
    private var cycleResetAt: Instant? = null
    private var freeLimit: Long = 0
    private var confirmedSpent: Long = 0
    private var outstanding: Long = 0
    private var providerCeiling: Long? = null
    private var cooldownUntil: Instant? = null
    private var rateNextAt: Instant = Instant.EPOCH
    private var lastSyncedAt: Instant? = null
    private var lastNewEnrichmentAt: Instant? = null
    private var syncLeaseToken: String? = null
    private var syncLeaseUntil: Instant? = null
    private var settledNewEnrichmentCosts = mutableListOf<Long>()

    /** I-5：测试可控的待补全任务数（生产由 expert_academic_enrichment_job 统计）。 */
    var pendingEnrichmentJobs: Long = 0

    override fun ledger(accountScope: String, configuredFreeLimitCredits: Long, now: Instant): OpenAlexBudgetLedger =
        synchronized(lock) {
            rollover(now, configuredFreeLimitCredits)
            OpenAlexBudgetLedger(
                accountScope = accountScope,
                cycleResetAt = cycleResetAt,
                freeLimitCredits = freeLimit,
                providerCeilingCredits = providerCeiling,
                confirmedSpentCredits = confirmedSpent,
                outstandingReservedCredits = outstanding,
                cooldownUntil = cooldownUntil,
                rateNextAt = rateNextAt,
                lastSyncedAt = lastSyncedAt,
                lastNewEnrichmentAt = lastNewEnrichmentAt
            )
        }

    override fun reserve(request: BudgetReserveRequest): BudgetReserveResult = synchronized(lock) {
        rollover(request.now, request.configuredFreeLimitCredits)
        val cycle = cycleResetAt ?: return BudgetReserveResult.Rejected(
            DeferredReason.BUDGET_SYNC, request.now.plusMillis(SYNC_RETRY_MS)
        )
        cooldownUntil?.let {
            if (request.now.isBefore(it)) return BudgetReserveResult.Rejected(DeferredReason.RATE_LIMIT, it)
        }
        if (request.now.isBefore(rateNextAt)) return BudgetReserveResult.Rejected(DeferredReason.RATE_LIMIT, rateNextAt)
        // I-4：只有真正放行的请求才占用限速槽（被延期的请求一个字节都没发出去）。
        if (request.estimatedCredits <= 0) {
            rateNextAt = request.now.plusMillis(request.rateIntervalMs)
            return BudgetReserveResult.Approved(cycle, rateNextAt, effectiveRemaining())
        }
        val available = effectiveRemaining()
        val after = available - request.estimatedCredits
        if (after < request.reservedFloorCredits) {
            val reason = if (after < 0) DeferredReason.DAILY_BUDGET else DeferredReason.ENRICHMENT_RESERVE
            return BudgetReserveResult.Rejected(reason, cycle)
        }
        rateNextAt = request.now.plusMillis(request.rateIntervalMs)
        reservations[request.permitId] = Reservation(cycle, request.kind, request.estimatedCredits)
        outstanding += request.estimatedCredits
        BudgetReserveResult.Approved(cycle, rateNextAt, after)
    }

    override fun settle(
        accountScope: String,
        permitId: String,
        actualCredits: Long?,
        observedRemaining: Long?,
        observedLimit: Long?,
        now: Instant
    ): Boolean = synchronized(lock) {
        val reservation = reservations[permitId] ?: return false
        if (reservation.status == STATUS_SETTLED) return false
        val amount = (actualCredits ?: reservation.reserved).coerceAtLeast(0)
        reservation.status = STATUS_SETTLED
        reservation.actual = amount
        // I-6：旧周期 permit 只结算旧行 —— 内存账本不保留已关闭周期，因此迟到响应不影响新周期。
        val sameCycle = reservation.dayResetAt == cycleResetAt
        if (sameCycle) {
            confirmedSpent += amount
            outstanding = (outstanding - reservation.reserved).coerceAtLeast(0)
            if (reservation.kind == RequestKind.NEW_ENRICHMENT) settledNewEnrichmentCosts += amount
            observedRemaining?.let { providerCeiling = providerCeiling?.let { current -> minOf(current, it) } ?: it }
            observedLimit?.let { freeLimit = minOf(freeLimit, it) }
        }
        true
    }

    override fun markUnknown(accountScope: String, permitId: String, now: Instant): Boolean = synchronized(lock) {
        val reservation = reservations[permitId] ?: return false
        if (reservation.status != STATUS_RESERVED) return false
        reservation.status = STATUS_UNKNOWN
        true
    }

    override fun applyCooldown(accountScope: String, notBefore: Instant, now: Instant) {
        synchronized(lock) {
            if (notBefore.isAfter(now)) cooldownUntil = maxOf(cooldownUntil ?: notBefore, notBefore)
        }
    }

    override fun observeProvider(
        accountScope: String,
        observedRemaining: Long?,
        observedLimit: Long?,
        configuredFreeLimitCredits: Long,
        now: Instant
    ) {
        synchronized(lock) {
            rollover(now, configuredFreeLimitCredits)
            observedRemaining?.let { providerCeiling = providerCeiling?.let { current -> minOf(current, it) } ?: it }
            observedLimit?.let { freeLimit = minOf(freeLimit, it) }
        }
    }

    override fun reconcile(
        accountScope: String,
        cycleResetAt: Instant,
        officialLimitCredits: Long?,
        officialRemainingCredits: Long,
        configuredFreeLimitCredits: Long,
        now: Instant
    ): Boolean = synchronized(lock) {
        if (!cycleResetAt.isAfter(now)) return false
        val official = minOf(officialLimitCredits ?: configuredFreeLimitCredits, configuredFreeLimitCredits)
        if (this.cycleResetAt != cycleResetAt) {
            this.cycleResetAt = cycleResetAt
            freeLimit = official
            confirmedSpent = 0
            outstanding = 0
            providerCeiling = officialRemainingCredits
        } else {
            freeLimit = minOf(freeLimit, official)
            providerCeiling = minOf(providerCeiling ?: officialRemainingCredits, officialRemainingCredits)
        }
        lastSyncedAt = now
        true
    }

    override fun claimSyncLease(accountScope: String, token: String, now: Instant, leaseUntil: Instant): Boolean =
        synchronized(lock) {
            val held = syncLeaseToken != null && syncLeaseUntil != null && now.isBefore(syncLeaseUntil!!)
            if (held && syncLeaseToken != token) return false
            syncLeaseToken = token
            syncLeaseUntil = leaseUntil
            true
        }

    override fun releaseSyncLease(accountScope: String, token: String) = synchronized(lock) {
        if (syncLeaseToken == token) {
            syncLeaseToken = null
            syncLeaseUntil = null
        }
    }

    override fun recordNewEnrichmentRequest(accountScope: String, now: Instant) {
        synchronized(lock) { lastNewEnrichmentAt = now }
    }

    override fun countUnfinishedEnrichmentJobs(): Long = synchronized(lock) { pendingEnrichmentJobs }

    override fun recentNewEnrichmentCosts(accountScope: String, limit: Int): List<Long> = synchronized(lock) {
        settledNewEnrichmentCosts.takeLast(limit)
    }

    override fun cleanupClosedCycles(closedBefore: Instant, batchSize: Int): Int = 0

    private fun effectiveRemaining(): Long {
        val localBound = (freeLimit - confirmedSpent).coerceAtLeast(0)
        val bounded = providerCeiling?.let { minOf(localBound, it) } ?: localBound
        return (bounded - outstanding).coerceAtLeast(0)
    }

    /** 本地 UTC 日切：新的一天 = 新周期（没有官方校准时按 UTC 午夜重建，与旧策略一致）。 */
    private fun rollover(now: Instant, configuredFreeLimitCredits: Long) {
        val current = cycleResetAt
        if (current != null && now.isBefore(current)) return
        cycleResetAt = utcDay(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        freeLimit = configuredFreeLimitCredits
        // 第一次建立周期（还没有旧周期）不能清掉刚写入的 429 冷却与最近补全时刻。
        if (current == null) return
        confirmedSpent = 0
        outstanding = 0
        providerCeiling = null
        cooldownUntil = null
        settledNewEnrichmentCosts = mutableListOf()
    }

    private companion object {
        const val STATUS_RESERVED = "RESERVED"
        const val STATUS_SETTLED = "SETTLED"
        const val STATUS_UNKNOWN = "UNKNOWN"
        const val SYNC_RETRY_MS = 60_000L
    }
}

private fun utcDay(now: Instant): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate()

/**
 * 对外只读预算快照（后两个计划直接读取；字段固定，不含 Key / 认证 URL）。
 */
data class OpenAlexBudgetSnapshot(
    val accountScope: String,
    val resetAt: Instant?,
    val officialLimitCredits: Long?,
    val officialRemainingCredits: Long?,
    val confirmedSpentCredits: Long,
    val reservedCredits: Long,
    val effectiveRemainingCredits: Long,
    val enrichmentReserveCredits: Long,
    val lastSyncedAt: Instant?,
    val deferredReason: DeferredReason?,
    val retryAt: Instant?
)

/**
 * I-1..I-6：全部 OpenAlex 计量请求的唯一预算与限速权威。
 *
 * - I-1 每次调用显式声明 [Operation]（成本）与 [RequestKind]（用途），并校验目标主机/路径；
 * - I-2 有效免费上限 = min(官方日免费额度, 配置保护上限)，预付余额永不加入；账号用稳定的非秘密
 *   [OpenAlexProperties.accountScope] 标识，换 Key 不清空已用预算；
 * - I-3 预占先于外部请求，permit 唯一且只结算一次，UNKNOWN 不退还，乱序响应只能收紧本周期 ceiling；
 * - I-4 未获得可信余额 / 账本不可用时计量请求延期（绝不退回本地 10000）；429 冷却写共享 notBefore；
 *   任何等待都在数据库锁之外，且超过上限就返回 retryAt 而不是睡下去；
 * - I-5 新专家补全保留区可借用但有依据；
 * - I-6 状态由 [OpenAlexBudgetStore] 持久化，重启/多实例共享。
 *
 * **生产装配必须注入 JDBC 账本**（[OpenAlexBudgetStore] 的 MySQL 实现）与官方余额取数接缝；默认参数
 * 只是单元测试/兼容路径的内存账本，生产 Bean 永远显式传入两者（见 `RestTemplateConfig`）。
 */
class OpenAlexRequestPolicy(
    private val properties: OpenAlexProperties,
    private val time: PolicyTimeSource = PolicyTimeSource.SYSTEM,
    private val store: OpenAlexBudgetStore = InMemoryOpenAlexBudgetStore(),
    private val syncSource: OpenAlexBudgetSyncSource? = null
) {
    private val log = LoggerFactory.getLogger(OpenAlexRequestPolicy::class.java)
    private val legacyPermit = ThreadLocal<String?>()

    @Volatile
    private var lastDeferral: Permit.Deferred? = null

    @Volatile
    private var forceSyncBeforeNextReserve: Boolean = false

    private var rateLimitBackoffMs: Long = 0
    private var listRequestsToday: Long = 0
    private var fulltextDownloadsToday: Long = 0
    private var countedDay: LocalDate? = null

    /** I-3：配置的请求速率，clamp 到官方 100/s 硬上限，且永不表示「无限」。 */
    val effectiveMaxRequestsPerSecond: Double
        get() = properties.maxRequestsPerSecond.coerceIn(MIN_REQUESTS_PER_SECOND, OFFICIAL_MAX_REQUESTS_PER_SECOND)

    /** I-3：当前可用额（= 免费上限 − 确认消耗，受 provider ceiling 约束，再减去全部未结算预占）。 */
    fun remainingCredits(): Long = ledgerOrNull()?.effectiveRemainingCredits ?: 0L

    /** I-3：本进程观察到的列表/搜索请求数（仅遥测，权威口径是账本）。 */
    fun listRequestCount(): Long = synchronized(this) {
        rolloverCounters(time.now())
        listRequestsToday
    }

    /** I-3：本进程观察到的计量 Content 下载数（本阶段恒为 0：计量主机一律被拒绝）。 */
    fun fulltextDownloadCount(): Long = synchronized(this) {
        rolloverCounters(time.now())
        fulltextDownloadsToday
    }

    /** 后两个计划读取的只读快照（字段固定）。账本不可用时返回延期原因而不是抛异常。 */
    fun snapshot(): OpenAlexBudgetSnapshot {
        val now = time.now()
        val deferral = lastDeferral
        val ledger = try {
            store.ledger(properties.accountScope, configuredFreeLimitCredits(), now)
        } catch (e: DataAccessException) {
            log.warn("OpenAlex budget store unavailable while reading the snapshot: {}", e.message)
            return OpenAlexBudgetSnapshot(
                accountScope = properties.accountScope,
                resetAt = null,
                officialLimitCredits = configuredFreeLimitCredits(),
                officialRemainingCredits = null,
                confirmedSpentCredits = 0,
                reservedCredits = 0,
                effectiveRemainingCredits = 0,
                enrichmentReserveCredits = 0,
                lastSyncedAt = null,
                deferredReason = DeferredReason.BUDGET_STORE_UNAVAILABLE,
                retryAt = now.plusMillis(STORE_RETRY_MS)
            )
        }
        val reserve = try {
            enrichmentReserveCredits(ledger, now)
        } catch (e: DataAccessException) {
            0L
        }
        return OpenAlexBudgetSnapshot(
            accountScope = properties.accountScope,
            resetAt = ledger.cycleResetAt,
            officialLimitCredits = ledger.freeLimitCredits,
            officialRemainingCredits = ledger.providerCeilingCredits,
            confirmedSpentCredits = ledger.confirmedSpentCredits,
            reservedCredits = ledger.outstandingReservedCredits,
            effectiveRemainingCredits = ledger.effectiveRemainingCredits,
            enrichmentReserveCredits = reserve,
            lastSyncedAt = ledger.lastSyncedAt,
            deferredReason = deferral?.reason,
            retryAt = deferral?.retryAt
        )
    }

    /**
     * I-1/I-3：显式操作 + 用途的预占入口。返回 [Permit.Allowed]（带唯一 permitId）或
     * [Permit.Deferred]（带机器可读原因与 retryAt）。调用方在 Deferred 时**绝不发请求**。
     */
    fun reserve(kind: RequestKind, operation: Operation, targetUrl: String?): Permit {
        if (targetUrl != null) validateOperationTarget(operation, targetUrl)
        if (operation == Operation.CONTENT) {
            throw OpenAlexContentDisabledException(
                "OpenAlex metered Content downloads are refused in this phase (operation=CONTENT, cost=${operation.costCredits})"
            )
        }
        return reserveInternal(kind, operation, targetUrl)
    }

    /**
     * 兼容入口：等价于 [reserve] 的 LIST 操作（旧调用方只有列表/搜索请求，且不再自行推断成本）。
     * 新代码一律使用带 [Operation] 的 [reserve]。
     */
    fun beforeRequest(kind: RequestKind): Permit = reserveInternal(kind, Operation.LIST, null)

    /**
     * I-3：按 permit 结算。`headers` 来自 provider 响应；没有响应头（网络失败）时按预占额保守记账。
     */
    fun recordResponse(permitId: String, headers: HttpHeaders) {
        if (legacyPermit.get() == permitId) legacyPermit.remove()
        val now = time.now()
        val creditsUsed = headers.creditHeader(CREDITS_USED_HEADER)
        val remaining = headers.creditHeader(REMAINING_HEADER)
        val limit = headers.creditHeader(LIMIT_HEADER)
        val retryAfterSeconds = headers.getFirst(RETRY_AFTER_HEADER)?.trim()?.toLongOrNull()

        // I-4：请求速率 429（额度仍有剩余，或带 Retry-After）只写共享冷却，绝不 dense retry。
        if (retryAfterSeconds != null || (creditsUsed == null && remaining != null && remaining > 0)) {
            val delayMs = rateLimitDelayMs(retryAfterSeconds)
            if (delayMs > 0) {
                storeOrDefer { store.applyCooldown(properties.accountScope, now.plusMillis(delayMs), now) }
            }
        } else if (creditsUsed != null) {
            rateLimitBackoffMs = 0
        }

        val settled = storeOrDefer {
            store.settle(
                properties.accountScope,
                permitId,
                actualCredits = creditsUsed,
                observedRemaining = remaining,
                observedLimit = limit,
                now = now
            )
        }
        if (settled == true && creditsUsed != null) countObservedRequest(creditsUsed, now)
        // 响应里带 0 余额（额度耗尽）→ 之后的预占自然被账本按 DAILY_BUDGET 拒绝。
    }

    /**
     * 兼容入口：结算当前线程最近一次 [beforeRequest]/[reserve] 的 permit。没有 permit 时只观察 provider
     * 数值（旧测试与诊断路径），不产生账本行。
     */
    fun recordResponse(headers: HttpHeaders) {
        val permitId = legacyPermit.get()
        if (permitId != null) {
            recordResponse(permitId, headers)
            return
        }
        val now = time.now()
        val creditsUsed = headers.creditHeader(CREDITS_USED_HEADER)
        val remaining = headers.creditHeader(REMAINING_HEADER)
        val limit = headers.creditHeader(LIMIT_HEADER)
        val retryAfterSeconds = headers.getFirst(RETRY_AFTER_HEADER)?.trim()?.toLongOrNull()
        if (retryAfterSeconds != null || (creditsUsed == null && remaining != null && remaining > 0)) {
            val delayMs = rateLimitDelayMs(retryAfterSeconds)
            if (delayMs > 0) {
                storeOrDefer { store.applyCooldown(properties.accountScope, now.plusMillis(delayMs), now) }
            }
        } else if (creditsUsed != null) {
            rateLimitBackoffMs = 0
        }
        storeOrDefer {
            store.observeProvider(properties.accountScope, remaining, limit, configuredFreeLimitCredits(), now)
        }
        if (creditsUsed != null) countObservedRequest(creditsUsed, now)
    }

    /** I-3：超时/响应丢失 → UNKNOWN，不退还预占；并让下一次预占前强制重新校准（异常恢复）。 */
    fun recordTimeout(permitId: String) {
        if (legacyPermit.get() == permitId) legacyPermit.remove()
        storeOrDefer { store.markUnknown(properties.accountScope, permitId, time.now()) }
        forceSyncBeforeNextReserve = true
    }

    /**
     * I-4：官方校准。同账号只允许一个校准者（租约）；只接受本周期、非陈旧快照。
     * 返回是否在本次调用中获得了可信余额。c3 的调度 tick 可直接调用它。
     */
    fun syncOfficialBudget(force: Boolean = false): Boolean {
        val source = syncSource ?: return false
        if (!properties.enabled) return false
        val now = time.now()
        val token = UUID.randomUUID().toString()
        val leaseUntil = now.plusMillis(SYNC_LEASE_MS)
        val acquired = try {
            store.claimSyncLease(properties.accountScope, token, now, leaseUntil)
        } catch (e: DataAccessException) {
            log.warn("OpenAlex budget store unavailable while claiming the calibration lease: {}", e.message)
            return false
        }
        if (!acquired) return false
        try {
            val current = store.ledger(properties.accountScope, configuredFreeLimitCredits(), time.now())
            if (!force && current.lastSyncedAt != null &&
                Duration.between(current.lastSyncedAt, now).toMillis() < properties.budgetSyncInterval.toMillis()
            ) {
                return current.providerCeilingCredits != null
            }
            val observed = try {
                source.fetchOfficialBalance()
            } catch (e: Exception) {
                log.warn("OpenAlex official budget sync failed: {}", e.message)
                null
            } ?: return false
            val resetAt = observed.resetAt ?: nextUtcMidnight(now)
            val accepted = store.reconcile(
                properties.accountScope,
                resetAt,
                observed.limitCredits,
                observed.remainingCredits,
                configuredFreeLimitCredits(),
                time.now()
            )
            if (!accepted) log.debug("OpenAlex official budget snapshot ignored (cycle {} already ended)", resetAt)
            return accepted
        } catch (e: DataAccessException) {
            log.warn("OpenAlex budget store unavailable while reconciling the official balance: {}", e.message)
            return false
        } finally {
            try {
                store.releaseSyncLease(properties.accountScope, token)
            } catch (e: DataAccessException) {
                log.warn("OpenAlex budget store unavailable while releasing the calibration lease: {}", e.message)
            }
        }
    }

    /** I-6：已关闭周期超过 [retention] 后按批归档；活跃周期与未解决 permit 不清理。 */
    fun cleanupArchivedCycles(retention: Duration = ARCHIVE_RETENTION, batchSize: Int = CLEANUP_BATCH_SIZE): Int =
        storeOrDefer { store.cleanupClosedCycles(time.now().minus(retention), batchSize) } ?: 0

    // ------------------------------------------------------------------
    // 内部：预占流程
    // ------------------------------------------------------------------

    private fun reserveInternal(kind: RequestKind, operation: Operation, targetUrl: String?): Permit {
        var attempt = 0
        var syncRetried = false
        while (true) {
            val now = time.now()
            val ledger = try {
                // I-4：0 成本操作（SINGLETON/RATE_LIMIT）不依赖额度，但账本不可用时仍然延期（冷却/限速是共享的）。
                if (operation.costCredits == 0L) store.ledger(properties.accountScope, configuredFreeLimitCredits(), now)
                else ensureTrustedLedger(now)
            } catch (e: DataAccessException) {
                log.warn("OpenAlex budget store unavailable; deferring metered request: {}", e.message)
                return defer(DeferredReason.BUDGET_STORE_UNAVAILABLE, now.plusMillis(STORE_RETRY_MS))
            } ?: return defer(DeferredReason.BUDGET_SYNC, now.plusMillis(SYNC_RETRY_MS))

            val permitId = UUID.randomUUID().toString()
            val floor = try {
                if (kind.mayUseReservedBudget) 0L else enrichmentReserveCredits(ledger, now)
            } catch (e: DataAccessException) {
                log.warn("OpenAlex budget store unavailable while reading the enrichment reserve: {}", e.message)
                return defer(DeferredReason.BUDGET_STORE_UNAVAILABLE, now.plusMillis(STORE_RETRY_MS))
            }
            val result = try {
                store.reserve(
                    BudgetReserveRequest(
                        accountScope = properties.accountScope,
                        permitId = permitId,
                        kind = kind,
                        operation = operation,
                        estimatedCredits = operation.costCredits,
                        reservedFloorCredits = floor,
                        rateIntervalMs = rateIntervalMs(),
                        configuredFreeLimitCredits = configuredFreeLimitCredits(),
                        now = now
                    )
                )
            } catch (e: DataAccessException) {
                log.warn("OpenAlex budget store unavailable; deferring metered request: {}", e.message)
                return defer(DeferredReason.BUDGET_STORE_UNAVAILABLE, now.plusMillis(STORE_RETRY_MS))
            }

            when (result) {
                is BudgetReserveResult.Approved -> {
                    if (kind == RequestKind.NEW_ENRICHMENT) {
                        storeOrDefer { store.recordNewEnrichmentRequest(properties.accountScope, now) }
                    }
                    legacyPermit.set(permitId)
                    lastDeferral = null
                    return Permit.Allowed(permitId)
                }

                is BudgetReserveResult.Rejected -> {
                    if (result.reason == DeferredReason.RATE_LIMIT) {
                        val waitMs = Duration.between(time.now(), result.retryAt).toMillis()
                        if (waitMs <= inlineWaitCapMs() && attempt < MAX_RATE_WAIT_RETRIES) {
                            time.sleep(waitMs)
                            attempt++
                            continue
                        }
                        return defer(DeferredReason.RATE_LIMIT, result.retryAt)
                    }
                    // I-4：额度不足 → 先按官方余额校准一次；官方确认仍不足才延期。
                    if (result.reason == DeferredReason.DAILY_BUDGET && !syncRetried && syncSource != null) {
                        syncRetried = true
                        if (syncOfficialBudget(force = true)) continue
                    }
                    return defer(result.reason, result.retryAt)
                }
            }
        }
    }

    /**
     * I-4：只有「已确认的官方周期 + 可信 provider ceiling」才允许发出计量请求；否则先校准，
     * 校准失败即延期（绝不退回本地额度）。内存账本（无 syncSource）沿用旧的本地 UTC 日切语义。
     */
    private fun ensureTrustedLedger(now: Instant): OpenAlexBudgetLedger? {
        if (syncSource == null) {
            return store.ledger(properties.accountScope, configuredFreeLimitCredits(), now)
        }
        val ledger = store.ledger(properties.accountScope, configuredFreeLimitCredits(), now)
        val cycleEnded = ledger.cycleResetAt == null || !ledger.cycleResetAt.isAfter(now)
        val stale = ledger.lastSyncedAt == null ||
            Duration.between(ledger.lastSyncedAt, now).toMillis() >= properties.budgetSyncInterval.toMillis()
        if (forceSyncBeforeNextReserve || cycleEnded || stale || ledger.providerCeilingCredits == null) {
            forceSyncBeforeNextReserve = false
            syncOfficialBudget(force = cycleEnded || ledger.providerCeilingCredits == null)
        }
        val refreshed = store.ledger(properties.accountScope, configuredFreeLimitCredits(), now)
        val trustedCycle = refreshed.cycleResetAt?.isAfter(now) == true
        return if (trustedCycle && refreshed.providerCeilingCredits != null) refreshed else null
    }

    /** I-5：保留区 = min(目标, 待补需求)，无待补且 60 秒无新补全请求时降为 0，且永不超过实际剩余。 */
    private fun enrichmentReserveCredits(ledger: OpenAlexBudgetLedger, now: Instant): Long {
        val ratio = properties.newEnrichmentReserveRatio.coerceIn(0.0, 1.0)
        val target = (ledger.freeLimitCredits * ratio).toLong().coerceAtLeast(0)
        if (target <= 0L) return 0L
        val pending = store.countUnfinishedEnrichmentJobs()
        val idleSince = ledger.lastNewEnrichmentAt?.let { Duration.between(it, now).seconds } ?: Long.MAX_VALUE
        val wanted = if (pending > 0L) {
            val estimate = enrichmentEstimateCredits()
            maxOf(pending * estimate, MIN_RESERVED_REQUEST_CREDITS)
        } else if (idleSince >= IDLE_RESERVE_RELEASE_SECONDS) {
            0L
        } else {
            MIN_RESERVED_REQUEST_CREDITS
        }
        return minOf(target, wanted, ledger.effectiveRemainingCredits).coerceAtLeast(0L)
    }

    /** I-5：无样本按每任务 10 credits；有样本取 max(10, 最近 100 次真实成本 P95 × 3)。 */
    private fun enrichmentEstimateCredits(): Long {
        val samples = store.recentNewEnrichmentCosts(properties.accountScope, ENRICHMENT_ESTIMATE_SAMPLE_SIZE)
        if (samples.isEmpty()) return DEFAULT_ENRICHMENT_ESTIMATE_CREDITS
        val sorted = samples.sorted()
        val index = Math.ceil(sorted.size * P95_RANK).toInt().coerceIn(1, sorted.size) - 1
        return maxOf(DEFAULT_ENRICHMENT_ESTIMATE_CREDITS, sorted[index] * ENRICHMENT_ESTIMATE_MULTIPLIER)
    }

    private fun defer(reason: DeferredReason, retryAt: Instant): Permit.Deferred {
        val deferred = Permit.Deferred(reason, retryAt)
        lastDeferral = deferred
        return deferred
    }

    private fun countObservedRequest(creditsUsed: Long?, now: Instant) {
        synchronized(this) {
            rolloverCounters(now)
            when {
                creditsUsed == null -> listRequestsToday++
                creditsUsed >= FULLTEXT_DOWNLOAD_CREDITS -> fulltextDownloadsToday++
                else -> listRequestsToday++
            }
        }
    }

    /** 遥测计数按 UTC 日切；账本口径不受影响。 */
    private fun rolloverCounters(now: Instant) {
        val today = utcDay(now)
        if (countedDay != today) {
            countedDay = today
            listRequestsToday = 0
            fulltextDownloadsToday = 0
        }
    }

    private fun ledgerOrNull(): OpenAlexBudgetLedger? = try {
        store.ledger(properties.accountScope, configuredFreeLimitCredits(), time.now())
    } catch (e: DataAccessException) {
        log.warn("OpenAlex budget store unavailable: {}", e.message)
        null
    }

    private inline fun <T> storeOrDefer(block: () -> T): T? = try {
        block()
    } catch (e: DataAccessException) {
        log.warn("OpenAlex budget store unavailable: {}", e.message)
        null
    }

    /** I-1：声明的操作必须与目标主机/路径一致；不一致一律抛错（绝不按 substring 静默推断）。 */
    fun validateOperationTarget(operation: Operation, targetUrl: String) {
        val derived = deriveOperation(targetUrl)
        check(derived == operation) {
            "OpenAlex operation $operation does not match its target host/path (derived $derived): $targetUrl"
        }
    }

    /**
     * 目标主机 + 路径 + 查询 → 唯一允许的操作。
     *
     * 这里**不做 URI 解析**：真实查询串含空格等未转义字符（`title_and_abstract.search:deep learning`），
     * `URI.create` 会直接拒绝。按结构拆出 host / path / query 已经足够校验，且绝不依赖任意 substring 猜测。
     */
    private fun deriveOperation(targetUrl: String): Operation {
        val schemeEnd = targetUrl.indexOf("://")
        val rest = if (schemeEnd >= 0) targetUrl.substring(schemeEnd + 3) else targetUrl
        val hostEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (hostEnd >= 0) rest.substring(0, hostEnd) else rest
        val host = authority.substringBefore(':').lowercase()
        val afterHost = if (hostEnd >= 0) rest.substring(hostEnd) else ""
        val path = afterHost.substringBefore('?').substringBefore('#')
        val query = afterHost.substringAfter('?', "").substringBefore('#')
        if (host.isEmpty()) throw IllegalStateException("OpenAlex request target has no host: $targetUrl")
        if (host == OpenAlexMeteredDestinations.CONTENT_HOST) return Operation.CONTENT
        return when {
            path == "/rate-limit" -> Operation.RATE_LIMIT
            SINGLETON_AUTHOR_PATH.matches(path) -> Operation.SINGLETON
            path.startsWith("/works") || path.startsWith("/authors") ->
                if (query.contains("search=") || query.contains("search:")) Operation.SEARCH else Operation.LIST
            host == OpenAlexMeteredDestinations.API_HOST -> Operation.SINGLETON
            else -> throw IllegalStateException("Unknown OpenAlex request target: $targetUrl")
        }
    }

    private fun rateIntervalMs(): Long =
        (1_000.0 / effectiveMaxRequestsPerSecond).toLong().coerceAtLeast(1L)

    /** I-4：允许在调用线程内等待的上限（= 配置的 429 退避上限）；超过就返回 retryAt。 */
    private fun inlineWaitCapMs(): Long = properties.rateLimitBackoffMaxMs.coerceAtLeast(0)

    /**
     * I-4：429 的共享冷却时长。官方 `Retry-After` 一律照做（不受退避上限影响），指数退避部分才受
     * `rate-limit-backoff-max-ms` 约束。
     */
    private fun rateLimitDelayMs(retryAfterSeconds: Long?): Long {
        val retryAfterMs = (retryAfterSeconds ?: 0L) * 1_000L
        val backoffMs = nextRateLimitBackoffMs().coerceAtMost(properties.rateLimitBackoffMaxMs.coerceAtLeast(0))
        return maxOf(retryAfterMs, backoffMs)
    }

    private fun nextRateLimitBackoffMs(): Long {
        val cap = properties.rateLimitBackoffMaxMs.coerceAtLeast(0)
        rateLimitBackoffMs = if (rateLimitBackoffMs <= 0) INITIAL_BACKOFF_MS else rateLimitBackoffMs * 2
        return rateLimitBackoffMs.coerceAtMost(cap)
    }

    /** I-2：官方日免费额度（静态估计：带 Key 10000 credits = $1，匿名 1000 credits = $0.10）。 */
    private fun officialFreeLimitCredits(): Long =
        if (properties.apiKey.isNotBlank()) {
            (KEYED_FREE_BUDGET_USD * CREDITS_PER_USD).toLong()
        } else {
            (KEYLESS_FREE_BUDGET_USD * CREDITS_PER_USD).toLong()
        }

    /** I-2：配置保护上限 = min(静态官方免费额度, freeBudgetCredits, 旧 dailyBudgetUsd 折算)。预付余额不参与。 */
    private fun configuredFreeLimitCredits(): Long {
        val protection = properties.freeBudgetCredits.coerceAtLeast(0L)
        val legacyUsd = properties.dailyBudgetUsd
        val legacyCredits = if (legacyUsd > 0.0) (legacyUsd * CREDITS_PER_USD).toLong() else Long.MAX_VALUE
        return minOf(officialFreeLimitCredits(), protection, legacyCredits).coerceAtLeast(0L)
    }

    private fun nextUtcMidnight(now: Instant): Instant =
        utcDay(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun HttpHeaders.creditHeader(name: String): Long? =
        getFirst(name)?.trim()?.toDoubleOrNull()?.toLong()

    companion object {
        /** 1 credit = $0.0001: the provider prices 1,000 list+filter calls at $0.10. */
        const val CREDITS_PER_USD = 10_000.0
        const val LIST_REQUEST_CREDITS = 1L
        const val FULLTEXT_DOWNLOAD_CREDITS = 100L
        const val KEYED_FREE_BUDGET_USD = 1.0
        const val KEYLESS_FREE_BUDGET_USD = 0.10
        const val OFFICIAL_MAX_REQUESTS_PER_SECOND = 100.0
        const val MIN_REQUESTS_PER_SECOND = 0.01
        const val INITIAL_BACKOFF_MS = 1_000L

        /** I-5：保留区空闲释放窗口与最小保留粒度。 */
        const val IDLE_RESERVE_RELEASE_SECONDS = 60L
        const val MIN_RESERVED_REQUEST_CREDITS = 10L
        const val DEFAULT_ENRICHMENT_ESTIMATE_CREDITS = 10L
        const val ENRICHMENT_ESTIMATE_SAMPLE_SIZE = 100
        const val ENRICHMENT_ESTIMATE_MULTIPLIER = 3L
        const val P95_RANK = 0.95

        /** I-4：校准租约与各类延期的重试窗口。 */
        const val SYNC_LEASE_MS = 60_000L
        const val STORE_RETRY_MS = 30_000L
        const val SYNC_RETRY_MS = 60_000L
        const val MAX_RATE_WAIT_RETRIES = 3

        /** I-6：已关闭周期账本/permit 的保留期与单批清理上限。 */
        val ARCHIVE_RETENTION: Duration = Duration.ofDays(7)
        const val CLEANUP_BATCH_SIZE = 1000

        const val LIMIT_HEADER = "X-RateLimit-Limit"
        const val REMAINING_HEADER = "X-RateLimit-Remaining"
        const val CREDITS_USED_HEADER = "X-RateLimit-Credits-Used"
        const val RESET_HEADER = "X-RateLimit-Reset"
        const val RETRY_AFTER_HEADER = "Retry-After"

        private val SINGLETON_AUTHOR_PATH = Regex("^/authors/[^/]+$")
    }
}
