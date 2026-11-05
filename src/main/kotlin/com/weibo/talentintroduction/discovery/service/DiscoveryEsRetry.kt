package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.PolicyTimeSource
import com.weibo.talentintroduction.discovery.domain.PaperReprocessFailure
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpStatusCodeException

/** Explicit ES-only call sites. Never wrap a business transaction or a provider HTTP client. */
object DiscoveryEsRetry {
    private val log = LoggerFactory.getLogger(DiscoveryEsRetry::class.java)
    private val delays = longArrayOf(2_000, 5_000, 10_000)
    private data class Context(val time: PolicyTimeSource, val stopped: () -> Boolean)
    // Lexical context shares cancellation with synchronous writer calls; never inherited by pool threads.
    private val context = ThreadLocal<Context>()

    fun <T> withContext(time: PolicyTimeSource, stopped: () -> Boolean, action: () -> T): T {
        val previous = context.get()
        context.set(Context(time, stopped))
        return try { action() } finally {
            if (previous == null) context.remove() else context.set(previous)
        }
    }

    fun isForbidden(error: Exception): Boolean = error is HttpStatusCodeException && error.rawStatusCode == 403

    fun <T> execute(operation: String, action: () -> T): T {
        for (attempt in 1..4) {
            checkStopped()
            try {
                val result = action()
                if (attempt > 1) log.info("event=DISCOVERY_ES_RETRY_RECOVERED operation={} attempts={}", operation, attempt)
                return result
            } catch (e: Exception) {
                if (!isForbidden(e)) throw e
                PaperReprocessFailure.log("DISCOVERY_ES_FORBIDDEN_$operation", e)
                if (attempt == 4) {
                    log.warn("event=DISCOVERY_ES_RETRY_EXHAUSTED operation={} attempts=4 status=403", operation)
                    throw e
                }
                waitAfterForbidden(operation, attempt)
            }
        }
        error("Unreachable ES retry state")
    }

    /** Also used by dedup's existing bounded loop, so retry loops never nest. */
    fun waitAfterForbidden(operation: String, failedAttempt: Int,
        time: PolicyTimeSource = context.get()?.time ?: PolicyTimeSource.SYSTEM,
        stopped: () -> Boolean = { false }) {
        require(failedAttempt in 1..3)
        var remaining = delays[failedAttempt - 1]
        log.warn("event=DISCOVERY_ES_RETRY operation={} status=403 retry={} maxRetries=3 delayMs={}",
            operation, failedAttempt, remaining)
        while (remaining > 0) {
            checkStopped(stopped)
            val slice = minOf(remaining, 250L)
            try { time.sleep(slice) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw DiscoveryEsRetryStoppedException()
            }
            remaining -= slice
        }
        checkStopped(stopped)
    }

    private fun checkStopped(extra: () -> Boolean = { false }) {
        if (Thread.currentThread().isInterrupted || context.get()?.stopped?.invoke() == true || extra()) {
            log.info("event=DISCOVERY_ES_RETRY_STOPPED")
            throw DiscoveryEsRetryStoppedException()
        }
    }
}

/** A cancelled/deadline-expired retry is a failed operation, never a missing document. */
class DiscoveryEsRetryStoppedException : RuntimeException("ES retry stopped by cancellation or deadline")
