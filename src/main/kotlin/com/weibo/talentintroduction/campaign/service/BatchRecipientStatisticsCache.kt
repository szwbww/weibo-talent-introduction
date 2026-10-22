package com.weibo.talentintroduction.campaign.service

import com.fasterxml.jackson.databind.ObjectMapper
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
    private val lock = Any()
    private val completed = LinkedHashMap<String, Entry>(64, 0.75f, true)
    private val inFlight = mutableMapOf<String, CompletableFuture<BatchRecipientStatisticsEnvelope>>()

    fun statistics(snapshot: BatchExecutionSnapshot, cacheOnly: Boolean = false, refresh: Boolean = false): BatchRecipientStatisticsEnvelope {
        require(!(cacheOnly && refresh)) { "cacheOnly and refresh cannot both be true" }
        require(snapshot.mailType == BatchSendType.INTRODUCTION.name || snapshot.mailType == BatchSendType.MATERIAL_REMINDER.name) { "Unsupported mailType" }
        val fingerprint = templates.statisticsFingerprint(snapshot.templateId, snapshot.mailType)
        val context = outreach.statisticsContext(snapshot.copy(templateId = fingerprint.templateId))
        val key = cacheKey(context, fingerprint.fingerprint)
        var owner = false
        lateinit var future: CompletableFuture<BatchRecipientStatisticsEnvelope>
        synchronized(lock) {
            if (cacheOnly) return envelope(key, completed[key], inFlight.containsKey(key))
            inFlight[key]?.let { futureToJoin ->
                // Wait outside the monitor: lookup must never wait for a scan.
                future = futureToJoin
                return@synchronized
            }
            val existing = completed[key]
            if (!refresh && existing?.summary != null) return envelope(key, existing, false)
            if (inFlight.size >= MAX_IN_FLIGHT) return envelope(key, existing, false).copy(
                state = BatchRecipientStatisticsState.ERROR, errorCode = "STATISTICS_BUSY"
            )
            future = CompletableFuture()
            inFlight[key] = future
            owner = true
        }
        // Only the thread that installed the future owns computation.
        if (!owner) return future.join()
        val result = try {
            val summary = outreach.countStrictStatistics(context)
            val after = templates.statisticsFingerprint(snapshot.templateId, snapshot.mailType)
            if (after != fingerprint) finish(key, future, null, "TEMPLATE_CHANGED_DURING_STATISTICS")
            else finish(key, future, summary, null)
        } catch (_: Exception) {
            finish(key, future, null, "STATISTICS_UNAVAILABLE")
        }
        return result
    }


    private fun finish(key: String, future: CompletableFuture<BatchRecipientStatisticsEnvelope>, summary: PendingOutreachSummary?, error: String?): BatchRecipientStatisticsEnvelope = synchronized(lock) {
        val old = completed[key]
        val entry = if (summary != null) Entry(summary, LocalDateTime.now(clock.withZone(SHANGHAI)))
        else Entry(old?.summary, old?.cachedAt, error)
        completed[key] = entry
        inFlight.remove(key)
        while (completed.size > MAX_COMPLETED) {
            // A refresh retains its previous success even while unrelated keys churn the LRU.
            completed.remove(completed.keys.first { it !in inFlight })
        }
        val result = envelope(key, entry, false)
        future.complete(result)
        result
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
