package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.campaign.domain.normalizeExcludeTags
import com.weibo.talentintroduction.campaign.domain.BatchExecutionSnapshot
import com.weibo.talentintroduction.campaign.domain.EmailVerificationAllowedStates
import com.weibo.talentintroduction.campaign.domain.RecipientScope
import com.weibo.talentintroduction.template.service.MailComposeTemplateService
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.annotation.PreDestroy
import org.slf4j.LoggerFactory

/** Display-only, process-local cache. Sending never consults this store. */
@Service
class BatchRecipientStatisticsCache(
    private val outreach: ManualInitialOutreachService,
    private val templates: MailComposeTemplateService,
    private val objectMapper: ObjectMapper
) {
    internal var clock: Clock = Clock.system(SHANGHAI)
    private data class Entry(
        val summary: PendingOutreachSummary? = null,
        val cachedAt: LocalDateTime? = null,
        val errorCode: String? = null
    )
    internal var timeoutMillis: Long = 120_000
    private val log = LoggerFactory.getLogger(javaClass)
    private class Refresh(timeoutMillis: Long) {
        val result = CompletableFuture<BatchRecipientStatisticsEnvelope>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        @Volatile var cancelled = false
        var worker: Thread? = null
    }
    // A timed-out blocking read retains its worker slot until it actually exits.
    private val workerSlots = Semaphore(MAX_IN_FLIGHT)
    private val workers = Executors.newFixedThreadPool(MAX_IN_FLIGHT) { task ->
        Thread(task, "recipient-statistics").apply { isDaemon = true }
    }
    private val lock = Any()
    private val completed = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val inFlight = mutableMapOf<String, Refresh>()

    fun statistics(snapshot: BatchExecutionSnapshot, cacheOnly: Boolean = false, refresh: Boolean = false): BatchRecipientStatisticsEnvelope {
        require(!(cacheOnly && refresh)) { "cacheOnly and refresh cannot both be true" }
        require(snapshot.mailType == BatchSendType.INTRODUCTION.name || snapshot.mailType == BatchSendType.MATERIAL_REMINDER.name) { "Unsupported mailType" }
        val fingerprint = templates.statisticsFingerprint(snapshot.templateId, snapshot.mailType)
        val context = outreach.statisticsContext(snapshot.copy(templateId = fingerprint.templateId))
        val key = cacheKey(context, fingerprint.fingerprint)
        var owner = false
        lateinit var job: Refresh
        synchronized(lock) {
            if (cacheOnly) return envelope(key, completed[key], inFlight.containsKey(key))
            inFlight[key]?.let { futureToJoin ->
                // Wait outside the monitor: lookup must never wait for a scan.
                job = futureToJoin
                return@synchronized
            }
            val existing = completed[key]
            if (!refresh && existing?.summary != null) return envelope(key, existing, false)
            if (inFlight.size >= MAX_IN_FLIGHT || !workerSlots.tryAcquire()) return envelope(key, existing, false).copy(
                state = BatchRecipientStatisticsState.ERROR, errorCode = "STATISTICS_BUSY"
            )
            job = Refresh(timeoutMillis)
            inFlight[key] = job
            owner = true
        }
        if (owner) {
            try {
                workers.execute {
                    var summary: PendingOutreachSummary? = null
                    var error: String? = null
                    try {
                        synchronized(lock) {
                            if (job.cancelled) throw InterruptedException("Statistics cancelled")
                            job.worker = Thread.currentThread()
                        }
                        summary = RecipientStatisticsCancellation.withDeadline(
                            { job.cancelled || System.nanoTime() >= job.deadline }
                        ) { outreach.countStrictStatistics(context) }
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Statistics cancelled")
                        val after = templates.statisticsFingerprint(snapshot.templateId, snapshot.mailType)
                        if (after != fingerprint) {
                            summary = null
                            error = "TEMPLATE_CHANGED_DURING_STATISTICS"
                        }
                    } catch (ex: Exception) {
                        summary = null
                        error = if (ex is TimeoutException) "STATISTICS_TIMEOUT" else "STATISTICS_UNAVAILABLE"
                        if (ex !is InterruptedException && ex !is TimeoutException) log.warn("Recipient statistics failed", ex)
                    } finally {
                        synchronized(lock) { job.worker = null }
                        workerSlots.release()
                    }
                    finish(key, job, summary, error)
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                workerSlots.release()
                return finish(key, job, null, "STATISTICS_BUSY")
            }
        }
        return try {
            job.result.get(maxOf(0L, job.deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        } catch (_: TimeoutException) {
            finish(key, job, null, "STATISTICS_TIMEOUT", cancel = true)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            finish(key, job, null, "STATISTICS_CANCELLED", cancel = true)
        }
    }

    private fun finish(
        key: String, job: Refresh, summary: PendingOutreachSummary?, error: String?, cancel: Boolean = false
    ): BatchRecipientStatisticsEnvelope = synchronized(lock) {
        // A timeout or replacement owns publication: late workers may never overwrite it.
        if (inFlight[key] !== job) return@synchronized job.result.getNow(envelope(key, completed[key], false))
        if (cancel) {
            job.cancelled = true
            job.worker?.interrupt()
        }
        val old = completed[key]
        val completionError = if (System.nanoTime() >= job.deadline) "STATISTICS_TIMEOUT" else error
        val entry = if (summary != null && completionError == null) Entry(summary, LocalDateTime.now(clock.withZone(SHANGHAI)))
        else Entry(old?.summary, old?.cachedAt, completionError)
        completed[key] = entry
        inFlight.remove(key)
        while (completed.size > MAX_COMPLETED) {
            completed.remove(completed.keys.first { it !in inFlight })
        }
        val result = envelope(key, entry, false)
        job.result.complete(result)
        result
    }

    @PreDestroy
    fun close() {
        synchronized(lock) {
            inFlight.toMap().forEach { (key, job) ->
                finish(key, job, null, "STATISTICS_CANCELLED", cancel = true)
            }
        }
        workers.shutdownNow()
    }

    private fun envelope(key: String, entry: Entry?, refreshing: Boolean): BatchRecipientStatisticsEnvelope = BatchRecipientStatisticsEnvelope(
        state = when {
            refreshing -> BatchRecipientStatisticsState.REFRESHING
            entry?.errorCode != null -> BatchRecipientStatisticsState.ERROR
            entry?.summary != null -> BatchRecipientStatisticsState.READY
            else -> BatchRecipientStatisticsState.MISS
        },
        cacheKey = key,
        cachedAt = entry?.cachedAt,
        stale = entry?.cachedAt?.let { Duration.between(it, LocalDateTime.now(clock.withZone(SHANGHAI))) >= Duration.ofHours(24) } ?: false,
        summary = entry?.summary,
        errorCode = entry?.errorCode
    )

    internal fun cacheKey(context: BatchRecipientStatisticsContext, fingerprint: String): String {
        val scope = context.scope
        val canonical = linkedMapOf<String, Any?>(
            "rulesVersion" to "v1", "mailType" to scope.mailType,
            "templateId" to context.snapshot.templateId, "templateFingerprint" to fingerprint,
            "funnelLevels" to scope.funnelLevels.sorted(), "tags" to scope.tags.distinct().sorted(),
            "excludeTags" to normalizeExcludeTags(scope.excludeTags).sorted(),
            "regions" to scope.regions.distinct().sorted(), "emailDomains" to scope.emailDomains.distinct().sorted(),
            "discipline" to scope.discipline, "operatorStatuses" to scope.operatorStatuses.distinct().sorted(),
            "expertTypes" to scope.expertTypes.distinct().sorted(), "hasFields" to scope.gateEsFields.distinct().sorted(),
            "researchDirectionFilter" to scope.researchDirectionFilter,
            "excludeVerifiedUnavailableEmails" to scope.excludeVerifiedUnavailableEmails,
            "gateFilterEnabled" to context.snapshot.gateFilterEnabled,
            "emailVerificationEnabled" to context.snapshot.emailVerificationEnabled,
            "emailVerificationAllowedStates" to EmailVerificationAllowedStates.allowedFor(context.snapshot.emailVerificationAllowedStates).sorted()
        )
        return MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(canonical))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val SHANGHAI = ZoneId.of("Asia/Shanghai")
        private const val MAX_COMPLETED = 64
        private const val MAX_IN_FLIGHT = 2
    }
}

/** Effective scope is resolved once and reused by both the key and the strict scan. */
data class BatchRecipientStatisticsContext(val snapshot: BatchExecutionSnapshot, val scope: RecipientScope)
enum class BatchRecipientStatisticsState { MISS, READY, REFRESHING, ERROR }
data class BatchRecipientStatisticsEnvelope(
    val state: BatchRecipientStatisticsState,
    val cacheKey: String? = null,
    val cachedAt: LocalDateTime? = null,
    val stale: Boolean = false,
    val summary: PendingOutreachSummary? = null,
    val errorCode: String? = null,
    /** Applies only to baseline diagnostics, never to the complete totalSendable reference count. */
    val diagnosticLimit: Int = 20_000
)

/** Scoped to a statistics worker; deadline survives drivers that clear the interrupt flag. */
internal object RecipientStatisticsCancellation {
    private val expired = ThreadLocal<() -> Boolean>()

    fun checkActive() {
        if (expired.get()?.invoke() == true) throw TimeoutException("Statistics deadline exceeded")
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Statistics cancelled")
    }

    fun <T> withDeadline(isExpired: () -> Boolean, action: () -> T): T {
        expired.set(isExpired)
        return try {
            checkActive()
            action().also { checkActive() }
        } finally {
            expired.remove()
        }
    }
}
