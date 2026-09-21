package com.weibo.talentintroduction.config

import org.springframework.http.HttpHeaders
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

/** Result of asking the shared policy for permission to call OpenAlex. */
sealed class Permit {
    object Allowed : Permit()

    /** The daily budget (or the whole UTC day) is unavailable until [resetAt]. */
    data class Deferred(val resetAt: Instant) : Permit()
}

/**
 * Raised instead of calling OpenAlex when [OpenAlexRequestPolicy.beforeRequest] returned [Permit.Deferred].
 * Extends [IllegalStateException] so the repository-wide exception-to-HTTP mapping stays predictable.
 */
class OpenAlexBudgetDeferredException(val resetAt: Instant) :
    IllegalStateException("OpenAlex daily quota deferred until $resetAt")

/**
 * Clock/sleep seam so the rate limiting and the day rollover can be driven deterministically in tests.
 */
interface PolicyTimeSource {
    fun now(): Instant
    fun nanoTime(): Long
    fun sleep(ms: Long)

    companion object {
        val SYSTEM: PolicyTimeSource = object : PolicyTimeSource {
            override fun now(): Instant = Instant.now()
            override fun nanoTime(): Long = System.nanoTime()
            override fun sleep(ms: Long) = Thread.sleep(ms)
        }
    }
}

/**
 * I-2/I-3: the single shared quota and rate authority for every OpenAlex call in this JVM. Discovery and enrichment
 * draw from the same budget; the account's real quota headers correct the in-process accounting, and a response-less
 * budget (fresh process, no header seen yet) is throttled against the configured free budget instead of being treated
 * as unlimited.
 *
 * Budget unit: 1 credit = $0.0001 (the provider prices 1,000 list+filter calls at $0.10 and reports the per-request
 * cost in `X-RateLimit-Credits-Used`). List calls cost [LIST_REQUEST_CREDITS]; a fulltext content download costs
 * [FULLTEXT_DOWNLOAD_CREDITS] and is counted separately.
 */
class OpenAlexRequestPolicy(
    private val properties: OpenAlexProperties,
    private val time: PolicyTimeSource = PolicyTimeSource.SYSTEM
) {
    private val lock = Any()

    private var day: LocalDate = utcDay(time.now())
    private var spentCredits: Long = 0
    private var inFlightCredits: Long = 0
    private var providerRemainingCredits: Long? = null
    private var providerLimitCredits: Long? = null
    private var providerResetAt: Instant? = null
    private var deferredUntil: Instant? = null
    private var rateLimitedUntil: Instant = Instant.EPOCH
    private var rateLimitBackoffMs: Long = 0
    private var nextSlotNanos: Long = 0
    private var listRequestsToday: Long = 0
    private var fulltextDownloadsToday: Long = 0

    /** I-3: configured request rate, clamped so it can never exceed the official 100/s nor mean "unlimited". */
    val effectiveMaxRequestsPerSecond: Double
        get() = properties.maxRequestsPerSecond.coerceIn(MIN_REQUESTS_PER_SECOND, OFFICIAL_MAX_REQUESTS_PER_SECOND)

    /** Credits still spendable today (local accounting capped by the last provider header). */
    fun remainingCredits(): Long = synchronized(lock) {
        rolloverIfNeeded(time.now())
        availableCredits()
    }

    /** I-3: list/search calls confirmed by the provider today. */
    fun listRequestCount(): Long = synchronized(lock) { listRequestsToday }

    /** I-3: fulltext content downloads confirmed by the provider today, counted apart from list calls. */
    fun fulltextDownloadCount(): Long = synchronized(lock) { fulltextDownloadsToday }

    /**
     * Reserves one request slot and its estimated cost. Returns [Permit.Deferred] when the day budget would drop
     * below the share reserved for [RequestKind.NEW_ENRICHMENT]; callers must not issue the HTTP call then.
     */
    fun beforeRequest(kind: RequestKind): Permit {
        val estimated = LIST_REQUEST_CREDITS
        val sleepMs: Long
        synchronized(lock) {
            val now = time.now()
            rolloverIfNeeded(now)
            deferredUntil?.let { if (!now.isBefore(it)) { deferredUntil = null; providerRemainingCredits = null } }
            deferredUntil?.let { return Permit.Deferred(it) }

            val available = availableCredits() - inFlightCredits
            val floor = if (kind.mayUseReservedBudget) 0L else reservedCredits()
            if (available - estimated < floor) {
                return Permit.Deferred(providerResetAt ?: nextUtcMidnight(now))
            }
            inFlightCredits += estimated

            val nowNanos = time.nanoTime()
            var slotNanos = maxOf(nowNanos, nextSlotNanos)
            // A bounded request-rate 429 backoff simply delays the next slot; it never blocks the day budget.
            if (now.isBefore(rateLimitedUntil)) {
                slotNanos = maxOf(slotNanos, nowNanos + (rateLimitedUntil.toEpochMilli() - now.toEpochMilli()) * 1_000_000L)
            }
            nextSlotNanos = slotNanos + minIntervalNanos()
            sleepMs = (slotNanos - nowNanos) / 1_000_000L
        }
        if (sleepMs > 0) time.sleep(sleepMs)
        return Permit.Allowed
    }

    /**
     * Reconciles one reserved request with the provider's real quota headers: the actual cost replaces the estimate,
     * the remaining budget and the UTC reset are refreshed, a day-budget 429 defers every consumer, and a
     * request-rate 429 only causes bounded backoff. Passing empty headers (failed request, no response) releases the
     * reservation and backs off conservatively without pretending the call happened.
     */
    fun recordResponse(headers: HttpHeaders) {
        synchronized(lock) {
            val now = time.now()
            rolloverIfNeeded(now)

            val creditsUsed = headers.creditHeader(CREDITS_USED_HEADER)
            val remaining = headers.creditHeader(REMAINING_HEADER)
            val limit = headers.creditHeader(LIMIT_HEADER)
            val retryAfterSeconds = headers.getFirst(RETRY_AFTER_HEADER)?.trim()?.toLongOrNull()
            val resetSeconds = headers.getFirst(RESET_HEADER)?.trim()?.toLongOrNull()

            inFlightCredits = (inFlightCredits - LIST_REQUEST_CREDITS).coerceAtLeast(0)
            spentCredits += creditsUsed ?: LIST_REQUEST_CREDITS
            if (creditsUsed != null) {
                if (creditsUsed >= FULLTEXT_DOWNLOAD_CREDITS) fulltextDownloadsToday++ else listRequestsToday++
            }

            if (remaining != null) providerRemainingCredits = remaining
            if (limit != null) providerLimitCredits = limit
            resetSeconds?.let { providerResetAt = now.plusSeconds(it) }

            when {
                // Day budget exhausted: the provider will keep answering 429 until the UTC reset. Never retry densely.
                remaining != null && remaining <= 0 -> {
                    deferredUntil = providerResetAt ?: nextUtcMidnight(now)
                    rateLimitBackoffMs = 0
                    rateLimitedUntil = Instant.EPOCH
                }
                // Request-rate 429 (budget still has room): bounded exponential backoff, capped by configuration.
                retryAfterSeconds != null || (creditsUsed == null && remaining != null && remaining > 0) -> {
                    rateLimitBackoffMs = nextRateLimitBackoffMs()
                    val cap = properties.rateLimitBackoffMaxMs.coerceAtLeast(0)
                    val retryAfterMs = retryAfterSeconds?.times(1_000L) ?: 0
                    val delayMs = maxOf(retryAfterMs, rateLimitBackoffMs).coerceAtMost(cap)
                    rateLimitedUntil = if (delayMs > 0) now.plusMillis(delayMs) else Instant.EPOCH
                }
                creditsUsed != null -> {
                    rateLimitBackoffMs = 0
                    rateLimitedUntil = Instant.EPOCH
                }
                // No provider signal at all (network error, empty body): stay conservative and back off briefly.
                else -> {
                    rateLimitBackoffMs = nextRateLimitBackoffMs()
                    val cap = properties.rateLimitBackoffMaxMs.coerceAtLeast(0)
                    val delayMs = rateLimitBackoffMs.coerceAtMost(cap)
                    rateLimitedUntil = if (delayMs > 0) now.plusMillis(delayMs) else Instant.EPOCH
                }
            }
        }
    }

    private fun nextRateLimitBackoffMs(): Long {
        val cap = properties.rateLimitBackoffMaxMs.coerceAtLeast(0)
        rateLimitBackoffMs = if (rateLimitBackoffMs <= 0) INITIAL_BACKOFF_MS else rateLimitBackoffMs * 2
        return rateLimitBackoffMs.coerceAtMost(cap)
    }

    private fun minIntervalNanos(): Long =
        (1_000_000_000.0 / effectiveMaxRequestsPerSecond).toLong().coerceAtLeast(1L)

    /** I-3: effective limit = min(configured cap, provider limit) and never more than what the provider has left. */
    private fun availableCredits(): Long {
        val local = (effectiveCapCredits() - spentCredits).coerceAtLeast(0)
        val provider = providerRemainingCredits ?: return local
        return minOf(local, provider)
    }

    private fun effectiveCapCredits(): Long {
        val configured = dailyCapCredits()
        val providerLimit = providerLimitCredits ?: return configured
        return minOf(configured, providerLimit)
    }

    private fun dailyCapCredits(): Long {
        val cap = properties.dailyBudgetUsd
        val usd = if (cap > 0.0) {
            cap
        } else if (properties.apiKey.isNotBlank()) {
            KEYED_FREE_BUDGET_USD
        } else {
            KEYLESS_FREE_BUDGET_USD
        }
        return (usd * CREDITS_PER_USD).toLong().coerceAtLeast(0)
    }

    private fun reservedCredits(): Long =
        (effectiveCapCredits() * properties.newEnrichmentReserveRatio.coerceIn(0.0, 1.0)).toLong()

    /** In-process state is not an audit source: on a new UTC day the budget must be rebuilt from headers again. */
    private fun rolloverIfNeeded(now: Instant) {
        val today = utcDay(now)
        if (today == day) return
        day = today
        spentCredits = 0
        inFlightCredits = 0
        providerRemainingCredits = null
        providerLimitCredits = null
        providerResetAt = null
        deferredUntil = null
        rateLimitedUntil = Instant.EPOCH
        rateLimitBackoffMs = 0
        nextSlotNanos = 0
        listRequestsToday = 0
        fulltextDownloadsToday = 0
    }

    private fun nextUtcMidnight(now: Instant): Instant =
        utcDay(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun utcDay(now: Instant): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate()

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

        const val LIMIT_HEADER = "X-RateLimit-Limit"
        const val REMAINING_HEADER = "X-RateLimit-Remaining"
        const val CREDITS_USED_HEADER = "X-RateLimit-Credits-Used"
        const val RESET_HEADER = "X-RateLimit-Reset"
        const val RETRY_AFTER_HEADER = "Retry-After"
    }
}
