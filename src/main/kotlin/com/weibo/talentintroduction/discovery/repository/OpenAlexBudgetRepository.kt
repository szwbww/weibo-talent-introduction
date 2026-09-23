package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.config.BudgetReserveRequest
import com.weibo.talentintroduction.config.BudgetReserveResult
import com.weibo.talentintroduction.config.DeferredReason
import com.weibo.talentintroduction.config.OpenAlexBudgetLedger
import com.weibo.talentintroduction.config.OpenAlexBudgetStore
import com.weibo.talentintroduction.config.OpenAlexRequestPolicy
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * I-2/I-3/I-4/I-6：OpenAlex 共享预算账本的 MySQL 实现（V132 三张表）。
 *
 * 生产装配（`RestTemplateConfig.sharedOpenAlexRequestPolicy`）**强制注入**本类；单元测试可注入内存账本，
 * 但生产 Bean 绝不会退回内存额度。
 *
 * 事务与锁序（I-3/I-6）：
 * - 每个方法自成一个事务；[reserve]/[settle]/[markUnknown]/[reconcile] 一律按**固定锁序**
 *   `openalex_budget_account` → `openalex_budget_day` → `openalex_budget_reservation` 加行锁（`FOR UPDATE`），
 *   因此多实例并发不会交叉死锁；
 * - 网络调用永远在事务之外：本类只做数据库操作，等待（限速槽/429 冷却）由 policy 在事务外完成；
 * - 时间列一律以 UTC 墙钟写入 `DATETIME(3)`（[toDb]/[toInstant]），与 JVM 默认时区无关。
 *
 * 语义要点：
 * - [reserve] 0 成本的 SINGLETON/RATE_LIMIT **不写账本行**（不消耗额度），但仍遵守 429 冷却与账号限速；
 * - `amount_reserved` 与 `amount_actual` 分列；[settle] 恰好一次（已结算返回 false），
 *   `actualCredits = null`（无 provider 证据）按预占额保守记账，绝不退款；
 * - `provider_ceiling` 与 `free_limit` 只收紧（`LEAST`），旧响应/旧周期不可能抬高本周期余额；
 * - UNKNOWN 仍计入 `outstanding_reserved`，只由有依据的结算或周期关闭归档（[cleanupClosedCycles] 只删已关闭周期）。
 */
@Repository
class OpenAlexBudgetRepository(private val jdbcTemplate: JdbcTemplate) : OpenAlexBudgetStore {

    private data class AccountRow(
        val rateNextAt: Instant,
        val cooldownUntil: Instant?,
        val lastSyncedAt: Instant?,
        val lastNewEnrichmentAt: Instant?
    )

    private data class CycleRow(
        val id: Long,
        val resetAt: Instant,
        val freeLimit: Long,
        val confirmedSpent: Long,
        val outstandingReserved: Long,
        val providerCeiling: Long?
    ) {
        /** I-3：可用额 = min(免费上限 − 确认消耗, provider ceiling) − 全部未结算预占。 */
        val effectiveRemaining: Long
            get() {
                val localBound = (freeLimit - confirmedSpent).coerceAtLeast(0)
                val bounded = providerCeiling?.let { minOf(localBound, it) } ?: localBound
                return (bounded - outstandingReserved).coerceAtLeast(0)
            }
    }

    private data class ReservationRow(
        val permitId: String,
        val dayId: Long,
        val amountReserved: Long,
        val status: String
    )

    override fun ledger(accountScope: String, configuredFreeLimitCredits: Long, now: Instant): OpenAlexBudgetLedger {
        val account = findAccount(accountScope)
        val cycle = findCurrentCycle(accountScope, now)
        return OpenAlexBudgetLedger(
            accountScope = accountScope,
            cycleResetAt = cycle?.resetAt,
            freeLimitCredits = cycle?.freeLimit ?: configuredFreeLimitCredits,
            providerCeilingCredits = cycle?.providerCeiling,
            confirmedSpentCredits = cycle?.confirmedSpent ?: 0L,
            outstandingReservedCredits = cycle?.outstandingReserved ?: 0L,
            cooldownUntil = account?.cooldownUntil,
            rateNextAt = account?.rateNextAt ?: now,
            lastSyncedAt = account?.lastSyncedAt,
            lastNewEnrichmentAt = account?.lastNewEnrichmentAt
        )
    }

    @Transactional
    override fun reserve(request: BudgetReserveRequest): BudgetReserveResult {
        ensureAccountRow(request.accountScope, request.now)
        val account = lockAccount(request.accountScope)
            ?: throw IllegalStateException("openalex_budget_account row disappeared right after ensure for scope ${request.accountScope}")
        account.cooldownUntil?.let {
            if (request.now.isBefore(it)) return BudgetReserveResult.Rejected(DeferredReason.RATE_LIMIT, it)
        }
        if (request.now.isBefore(account.rateNextAt)) {
            return BudgetReserveResult.Rejected(DeferredReason.RATE_LIMIT, account.rateNextAt)
        }
        // I-4：只有真正放行的请求才占用限速槽（被延期的请求一个字节都没发出去）。
        val slot = request.now.plusMillis(request.rateIntervalMs)

        // I-4：0 成本操作（SINGLETON/RATE_LIMIT）不消耗额度、不写账本行，但已遵守冷却与账号限速。
        if (request.estimatedCredits <= 0L) {
            advanceRateSlot(request.accountScope, slot, request.now)
            val cycle = findCurrentCycle(request.accountScope, request.now)
            return BudgetReserveResult.Approved(cycle?.resetAt, slot, cycle?.effectiveRemaining ?: 0L)
        }

        val cycle = lockCurrentCycle(request.accountScope, request.now)
            ?: return BudgetReserveResult.Rejected(
                DeferredReason.BUDGET_SYNC, request.now.plusMillis(OpenAlexRequestPolicy.SYNC_RETRY_MS)
            )
        val after = cycle.effectiveRemaining - request.estimatedCredits
        if (after < request.reservedFloorCredits) {
            val reason = if (after < 0L) DeferredReason.DAILY_BUDGET else DeferredReason.ENRICHMENT_RESERVE
            return BudgetReserveResult.Rejected(reason, cycle.resetAt)
        }
        advanceRateSlot(request.accountScope, slot, request.now)
        jdbcTemplate.update(
            """
            INSERT INTO openalex_budget_reservation
                (permit_id, day_id, request_kind, operation, amount_reserved, amount_actual, status, created_at, settled_at)
            VALUES (?, ?, ?, ?, ?, NULL, 'RESERVED', ?, NULL)
            """,
            request.permitId, cycle.id, request.kind.name, request.operation.name,
            request.estimatedCredits, toDb(request.now)
        )
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_day
               SET outstanding_reserved = outstanding_reserved + ?, updated_at = ?
             WHERE id = ?
            """,
            request.estimatedCredits, toDb(request.now), cycle.id
        )
        return BudgetReserveResult.Approved(cycle.resetAt, slot, after)
    }

    @Transactional
    override fun settle(
        accountScope: String,
        permitId: String,
        actualCredits: Long?,
        observedRemaining: Long?,
        observedLimit: Long?,
        now: Instant
    ): Boolean {
        lockAccount(accountScope)
        val reservation = lockReservation(permitId) ?: return false
        // I-3：结算恰好一次；重复结算不再扣减。
        if (reservation.status == STATUS_SETTLED) return false
        val amount = (actualCredits ?: reservation.amountReserved).coerceAtLeast(0L)
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_reservation
               SET status = 'SETTLED', amount_actual = ?, settled_at = ?
             WHERE permit_id = ? AND status <> 'SETTLED'
            """,
            amount, toDb(now), permitId
        )
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_day
               SET confirmed_spent = confirmed_spent + ?,
                   outstanding_reserved = GREATEST(outstanding_reserved - ?, 0),
                   provider_ceiling = COALESCE(LEAST(COALESCE(provider_ceiling, ?), ?), provider_ceiling),
                   free_limit = LEAST(free_limit, COALESCE(?, free_limit)),
                   updated_at = ?
             WHERE id = ?
            """,
            amount, reservation.amountReserved,
            observedRemaining, observedRemaining,
            observedLimit,
            toDb(now), reservation.dayId
        )
        return true
    }

    @Transactional
    override fun markUnknown(accountScope: String, permitId: String, now: Instant): Boolean {
        lockAccount(accountScope)
        val reservation = lockReservation(permitId) ?: return false
        if (reservation.status != STATUS_RESERVED) return false
        // I-3：UNKNOWN 不退还预占 —— outstanding_reserved 保持不变，只由有依据的结算或周期关闭归档。
        val updated = jdbcTemplate.update(
            "UPDATE openalex_budget_reservation SET status = 'UNKNOWN' WHERE permit_id = ? AND status = 'RESERVED'",
            permitId
        )
        return updated > 0
    }

    override fun applyCooldown(accountScope: String, notBefore: Instant, now: Instant) {
        ensureAccountRow(accountScope, now)
        // I-4：只收紧，不提前清除；跨实例可见（写账号行而不是进程内存）。
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_account
               SET cooldown_until = GREATEST(COALESCE(cooldown_until, ?), ?), updated_at = ?
             WHERE account_scope = ?
            """,
            toDb(notBefore), toDb(notBefore), toDb(now), accountScope
        )
    }

    override fun observeProvider(
        accountScope: String,
        observedRemaining: Long?,
        observedLimit: Long?,
        configuredFreeLimitCredits: Long,
        now: Instant
    ) {
        val cycle = findCurrentCycle(accountScope, now) ?: return
        if (observedRemaining == null && observedLimit == null) return
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_day
               SET provider_ceiling = COALESCE(LEAST(COALESCE(provider_ceiling, ?), ?), provider_ceiling),
                   free_limit = LEAST(free_limit, COALESCE(?, free_limit)),
                   updated_at = ?
             WHERE id = ?
            """,
            observedRemaining, observedRemaining,
            observedLimit,
            toDb(now), cycle.id
        )
    }

    @Transactional
    override fun reconcile(
        accountScope: String,
        cycleResetAt: Instant,
        officialLimitCredits: Long?,
        officialRemainingCredits: Long,
        configuredFreeLimitCredits: Long,
        now: Instant
    ): Boolean {
        // I-3/I-4：只接受本周期（reset 尚未到期）的非陈旧快照。
        if (!cycleResetAt.isAfter(now)) return false
        ensureAccountRow(accountScope, now)
        lockAccount(accountScope)
        val official = minOf(officialLimitCredits ?: configuredFreeLimitCredits, configuredFreeLimitCredits)
            .coerceAtLeast(0L)
        // 官方确认了新的 reset：旧周期在此关闭（旧周期未完成 permit 仍只结算旧行）。
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_day
               SET closed_at = ?, updated_at = ?
             WHERE account_scope = ? AND closed_at IS NULL AND reset_at <= ?
            """,
            toDb(now), toDb(now), accountScope, toDb(now)
        )
        val existing = lockCycle(accountScope, cycleResetAt)
        if (existing == null) {
            jdbcTemplate.update(
                """
                INSERT INTO openalex_budget_day
                    (account_scope, reset_at, free_limit, confirmed_spent, outstanding_reserved, provider_ceiling, created_at, updated_at)
                VALUES (?, ?, ?, 0, 0, ?, ?, ?)
                """,
                accountScope, toDb(cycleResetAt), official, officialRemainingCredits, toDb(now), toDb(now)
            )
        } else {
            jdbcTemplate.update(
                """
                UPDATE openalex_budget_day
                   SET free_limit = LEAST(free_limit, ?),
                       provider_ceiling = LEAST(COALESCE(provider_ceiling, ?), ?),
                       updated_at = ?
                 WHERE id = ?
                """,
                official, officialRemainingCredits, officialRemainingCredits, toDb(now), existing.id
            )
        }
        jdbcTemplate.update(
            "UPDATE openalex_budget_account SET last_synced_at = ?, updated_at = ? WHERE account_scope = ?",
            toDb(now), toDb(now), accountScope
        )
        return true
    }

    @Transactional
    override fun claimSyncLease(accountScope: String, token: String, now: Instant, leaseUntil: Instant): Boolean {
        ensureAccountRow(accountScope, now)
        val updated = jdbcTemplate.update(
            """
            UPDATE openalex_budget_account
               SET sync_lease_token = ?, sync_lease_until = ?, updated_at = ?
             WHERE account_scope = ?
               AND (sync_lease_token IS NULL OR sync_lease_until IS NULL OR sync_lease_until <= ? OR sync_lease_token = ?)
            """,
            token, toDb(leaseUntil), toDb(now), accountScope, toDb(now), token
        )
        return updated > 0
    }

    override fun releaseSyncLease(accountScope: String, token: String) {
        jdbcTemplate.update(
            """
            UPDATE openalex_budget_account
               SET sync_lease_token = NULL, sync_lease_until = NULL
             WHERE account_scope = ? AND sync_lease_token = ?
            """,
            accountScope, token
        )
    }

    override fun recordNewEnrichmentRequest(accountScope: String, now: Instant) {
        ensureAccountRow(accountScope, now)
        jdbcTemplate.update(
            "UPDATE openalex_budget_account SET last_new_enrichment_at = ? WHERE account_scope = ?",
            toDb(now), accountScope
        )
    }

    /** I-5：只读现有补全任务表；PENDING/RUNNING/RETRY_WAIT 都是未完成（含 next_attempt_at 在未来的待补）。 */
    override fun countUnfinishedEnrichmentJobs(): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM expert_academic_enrichment_job
             WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT')
            """,
            Long::class.java
        ) ?: 0L

    /** I-5：最近 [limit] 次**已结算** NEW_ENRICHMENT 请求的真实成本样本（同一账号、跨实例共享）。 */
    override fun recentNewEnrichmentCosts(accountScope: String, limit: Int): List<Long> =
        jdbcTemplate.queryForList(
            """
            SELECT r.amount_actual
              FROM openalex_budget_reservation r
              JOIN openalex_budget_day d ON d.id = r.day_id
             WHERE d.account_scope = ?
               AND r.request_kind = 'NEW_ENRICHMENT'
               AND r.status = 'SETTLED'
               AND r.amount_actual IS NOT NULL
             ORDER BY r.settled_at DESC, r.permit_id DESC
             LIMIT ?
            """,
            Long::class.java,
            accountScope, limit
        ).filterNotNull()

    /**
     * I-6：只清理**已关闭**周期（`closed_at` 早于保留期）的账本与 permit；活跃周期与未解决 permit 永不按
     * 短 TTL 释放。UNKNOWN 由「周期关闭」归档（有依据），不会在活跃周期里被当成未花费而清掉。
     */
    @Transactional
    override fun cleanupClosedCycles(closedBefore: Instant, batchSize: Int): Int {
        if (batchSize <= 0) return 0
        val ids = jdbcTemplate.queryForList(
            """
            SELECT id FROM openalex_budget_day
             WHERE closed_at IS NOT NULL AND closed_at < ?
             ORDER BY id
             LIMIT ?
            """,
            Long::class.java,
            toDb(closedBefore), batchSize
        )
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        jdbcTemplate.update(
            "DELETE FROM openalex_budget_reservation WHERE day_id IN ($placeholders)",
            *ids.toTypedArray()
        )
        jdbcTemplate.update(
            "DELETE FROM openalex_budget_day WHERE id IN ($placeholders)",
            *ids.toTypedArray()
        )
        return ids.size
    }

    // ------------------------------------------------------------------
    // 内部读写
    // ------------------------------------------------------------------

    private fun advanceRateSlot(accountScope: String, slot: Instant, now: Instant) {
        jdbcTemplate.update(
            "UPDATE openalex_budget_account SET rate_next_at = ?, updated_at = ? WHERE account_scope = ?",
            toDb(slot), toDb(now), accountScope
        )
    }

    private fun ensureAccountRow(accountScope: String, now: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO openalex_budget_account (account_scope, rate_next_at, created_at, updated_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE account_scope = account_scope
            """,
            accountScope, toDb(now), toDb(now), toDb(now)
        )
    }

    private fun findAccount(accountScope: String): AccountRow? =
        jdbcTemplate.query(
            "SELECT rate_next_at, cooldown_until, last_synced_at, last_new_enrichment_at FROM openalex_budget_account WHERE account_scope = ?",
            { rs, _ -> accountRow(rs) },
            accountScope
        ).firstOrNull()

    private fun lockAccount(accountScope: String): AccountRow? =
        jdbcTemplate.query(
            "SELECT rate_next_at, cooldown_until, last_synced_at, last_new_enrichment_at FROM openalex_budget_account WHERE account_scope = ? FOR UPDATE",
            { rs, _ -> accountRow(rs) },
            accountScope
        ).firstOrNull()

    private fun accountRow(rs: ResultSet) = AccountRow(
        rateNextAt = toInstant(rs.getObject("rate_next_at", LocalDateTime::class.java)) ?: Instant.EPOCH,
        cooldownUntil = toInstant(rs.getObject("cooldown_until", LocalDateTime::class.java)),
        lastSyncedAt = toInstant(rs.getObject("last_synced_at", LocalDateTime::class.java)),
        lastNewEnrichmentAt = toInstant(rs.getObject("last_new_enrichment_at", LocalDateTime::class.java))
    )

    private fun findCurrentCycle(accountScope: String, now: Instant): CycleRow? =
        jdbcTemplate.query(
            """
            SELECT id, reset_at, free_limit, confirmed_spent, outstanding_reserved, provider_ceiling
              FROM openalex_budget_day
             WHERE account_scope = ? AND closed_at IS NULL AND reset_at > ?
             ORDER BY reset_at ASC
             LIMIT 1
            """,
            { rs, _ -> cycleRow(rs) },
            accountScope, toDb(now)
        ).firstOrNull()

    private fun lockCurrentCycle(accountScope: String, now: Instant): CycleRow? =
        jdbcTemplate.query(
            """
            SELECT id, reset_at, free_limit, confirmed_spent, outstanding_reserved, provider_ceiling
              FROM openalex_budget_day
             WHERE account_scope = ? AND closed_at IS NULL AND reset_at > ?
             ORDER BY reset_at ASC
             LIMIT 1
             FOR UPDATE
            """,
            { rs, _ -> cycleRow(rs) },
            accountScope, toDb(now)
        ).firstOrNull()

    private fun lockCycle(accountScope: String, resetAt: Instant): CycleRow? =
        jdbcTemplate.query(
            """
            SELECT id, reset_at, free_limit, confirmed_spent, outstanding_reserved, provider_ceiling
              FROM openalex_budget_day
             WHERE account_scope = ? AND reset_at = ?
             FOR UPDATE
            """,
            { rs, _ -> cycleRow(rs) },
            accountScope, toDb(resetAt)
        ).firstOrNull()

    private fun cycleRow(rs: ResultSet) = CycleRow(
        id = rs.getLong("id"),
        resetAt = toInstant(rs.getObject("reset_at", LocalDateTime::class.java)) ?: Instant.EPOCH,
        freeLimit = rs.getLong("free_limit"),
        confirmedSpent = rs.getLong("confirmed_spent"),
        outstandingReserved = rs.getLong("outstanding_reserved"),
        providerCeiling = rs.getLong("provider_ceiling").takeUnless { rs.wasNull() }
    )

    private fun lockReservation(permitId: String): ReservationRow? =
        jdbcTemplate.query(
            "SELECT permit_id, day_id, amount_reserved, status FROM openalex_budget_reservation WHERE permit_id = ? FOR UPDATE",
            { rs, _ -> ReservationRow(rs.getString("permit_id"), rs.getLong("day_id"), rs.getLong("amount_reserved"), rs.getString("status")) },
            permitId
        ).firstOrNull()

    private companion object {
        const val STATUS_RESERVED = "RESERVED"
        const val STATUS_SETTLED = "SETTLED"

        fun toDb(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

        fun toInstant(value: LocalDateTime?): Instant? = value?.toInstant(ZoneOffset.UTC)
    }
}
