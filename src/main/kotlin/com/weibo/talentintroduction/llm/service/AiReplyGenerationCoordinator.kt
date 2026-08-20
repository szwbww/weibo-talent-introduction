package com.weibo.talentintroduction.llm.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

typealias AiReplyGenerationOperation = (
    AiReplyCancellationToken,
    AiReplyProgressReporter,
    () -> Boolean
) -> Any

@Service
class AiReplyGenerationCoordinator(
    @Qualifier("aiReplyStreamExecutor") private val executor: ExecutorService,
    @Qualifier("aiReplyStreamScheduler") private val scheduler: ScheduledExecutorService
) {
    private val generations = ConcurrentHashMap<String, GenerationControl>()
    private val logger = LoggerFactory.getLogger(AiReplyGenerationCoordinator::class.java)

    fun start(
        scopeKey: String,
        generationId: String,
        policy: AiReplyTimeoutPolicy,
        operation: AiReplyGenerationOperation
    ): SseEmitter {
        require(isCanonicalUuid(generationId)) { "generationId must be a canonical UUID" }
        val emitter = SseEmitter(policy.totalTimeoutSeconds * 1000L + 30_000L)
        val token = AiReplyCancellationToken()
        val control = GenerationControl(scopeKey, generationId, token, emitter, scheduler)
        synchronized(generations) {
            if (generations.putIfAbsent(generationId, control) != null) {
                throw TrustReplyWorkbenchException(HttpStatus.CONFLICT, "TRUST_REPLY_GENERATION_ID_ACTIVE")
            }
            if (generations.size > MAX_ACTIVE_GENERATIONS) {
                generations.remove(generationId, control)
                throw TrustReplyWorkbenchException(HttpStatus.TOO_MANY_REQUESTS, "TRUST_REPLY_GENERATION_QUEUE_FULL")
            }
        }

        val tracker = AiReplyProgressTracker(
            generationId = generationId,
            attemptTimeoutSeconds = policy.attemptTimeoutSeconds,
            totalTimeoutSeconds = policy.totalTimeoutSeconds,
            sink = control::publishProgress
        )
        control.onCleanup = { generations.remove(generationId, control) }
        emitter.onTimeout { control.disconnect() }
        emitter.onCompletion { control.disconnect() }
        emitter.onError { control.disconnect() }
        control.sendEvent(
            "ready",
            mapOf(
                "generationId" to generationId,
                "appliedLlmAttemptTimeoutSeconds" to policy.attemptTimeoutSeconds,
                "appliedLlmTotalTimeoutSeconds" to policy.totalTimeoutSeconds,
                "progress" to queuedProgress(generationId, policy)
            )
        )
        control.heartbeatFuture = scheduler.scheduleAtFixedRate({
            control.sendHeartbeat(tracker.snapshotNow())
        }, 10L, 10L, TimeUnit.SECONDS)
        try {
            control.workerFuture = executor.submit {
                control.markRunning()
                try {
                    val response = operation(token, tracker, control::tryBeginCommit)
                    control.sendTerminal("result", response)
                } catch (_: AiReplyGenerationCancelledException) {
                    control.sendTerminal("cancelled", mapOf("generationId" to generationId))
                } catch (ex: Exception) {
                    // I-1: 只对已知业务异常透出真实 code；其余固定码，不泄露异常原文。
                    val code = (ex as? TrustReplyWorkbenchException)?.code ?: CODE_GENERATION_FAILED
                    // I-2: 响应与日志必须同时有原因；这里是本链路唯一的诊断点。
                    logger.warn("AI reply generation failed: generationId={}, code={}", generationId, code, ex)
                    control.sendTerminal("error", mapOf("generationId" to generationId, "code" to code, "message" to "AI generation failed"))
                } finally {
                    control.cleanup()
                }
            }
        } catch (_: RejectedExecutionException) {
            control.sendTerminal(
                "error",
                mapOf("generationId" to generationId, "message" to "AI generation queue is full")
            )
            control.cleanup()
        }
        return emitter
    }

    fun cancel(scopeKey: String, generationId: String): String {
        val control = generations[generationId]
        return if (control == null || control.scopeKey != scopeKey) {
            "NOT_ACTIVE"
        } else {
            control.requestCancel()
        }
    }

    internal fun activeGenerationCount(): Int = generations.size

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

    private fun queuedProgress(
        generationId: String,
        policy: AiReplyTimeoutPolicy
    ): Map<String, Any> = mapOf(
        "generationId" to generationId,
        "progressSeq" to 0,
        "phase" to AiReplyProgressPhase.QUEUED.name,
        "providerActivity" to AiReplyProviderActivity.IDLE.name,
        "providerCallIndex" to 0,
        "attemptElapsedSeconds" to 0,
        "attemptTimeoutSeconds" to policy.attemptTimeoutSeconds,
        "totalElapsedSeconds" to 0,
        "totalTimeoutSeconds" to policy.totalTimeoutSeconds,
        "providerEventCount" to 0,
        "contentChars" to 0,
        "secondsSinceProviderActivity" to 0
    )

    companion object {
        private const val MAX_ACTIVE_GENERATIONS = 40
        const val CODE_GENERATION_FAILED = "AI_REPLY_GENERATION_FAILED"
    }
}

private enum class GenerationStatus { REGISTERED, RUNNING, COMMITTING, CANCEL_REQUESTED, FINISHED }

private class GenerationControl(
    val scopeKey: String,
    private val generationId: String,
    private val token: AiReplyCancellationToken,
    private val emitter: SseEmitter,
    private val progressScheduler: ScheduledExecutorService
) {
    @Volatile
    var workerFuture: Future<*>? = null
    @Volatile
    var heartbeatFuture: ScheduledFuture<*>? = null
    @Volatile
    var onCleanup: (() -> Unit)? = null
    @Volatile
    private var status = GenerationStatus.REGISTERED
    private val terminalSent = AtomicBoolean(false)
    private val cleanupDone = AtomicBoolean(false)
    private val sendLock = Any()
    private val progressStateLock = Any()
    private var pendingProgress: AiReplyProgressSnapshot? = null
    private var progressFlushFuture: ScheduledFuture<*>? = null
    private var progressSendInFlight = false
    private var lastProgressSentAt = Long.MIN_VALUE
    private var lastProgressPhase: AiReplyProgressPhase? = null

    fun markRunning() {
        synchronized(sendLock) {
            if (status == GenerationStatus.REGISTERED) status = GenerationStatus.RUNNING
        }
    }

    fun tryBeginCommit(): Boolean = synchronized(sendLock) {
        if (status != GenerationStatus.RUNNING) return@synchronized false
        status = GenerationStatus.COMMITTING
        true
    }

    fun publishProgress(snapshot: AiReplyProgressSnapshot) {
        synchronized(progressStateLock) {
            if (!isProgressActive()) return
            pendingProgress = snapshot
            if (progressSendInFlight) return
            val delay = progressDelayNanos(snapshot)
            if (progressFlushFuture != null && progressFlushFuture?.isDone != true) {
                if (delay != 0L) return
                progressFlushFuture?.cancel(false)
            }
            scheduleProgressFlushLocked(delay)
        }
    }

    private fun flushProgress() {
        val snapshot = synchronized(progressStateLock) {
            progressFlushFuture = null
            if (!isProgressActive()) {
                pendingProgress = null
                null
            } else {
                pendingProgress.also {
                    pendingProgress = null
                    progressSendInFlight = it != null
                }
            }
        } ?: return
        val sent = synchronized(sendLock) {
            isProgressActive() && sendLocked("progress", progressMap(snapshot))
        }
        synchronized(progressStateLock) {
            progressSendInFlight = false
            if (sent) {
                lastProgressSentAt = System.nanoTime()
                lastProgressPhase = snapshot.phase
            }
            pendingProgress?.takeIf { sent && isProgressActive() }?.let {
                scheduleProgressFlushLocked(progressDelayNanos(it))
            }
        }
    }

    private fun progressDelayNanos(snapshot: AiReplyProgressSnapshot): Long {
        if (lastProgressPhase != snapshot.phase || lastProgressSentAt == Long.MIN_VALUE) return 0L
        return (1_000_000_000L - (System.nanoTime() - lastProgressSentAt)).coerceAtLeast(0L)
    }

    private fun scheduleProgressFlushLocked(delayNanos: Long) {
        progressFlushFuture = progressScheduler.schedule({ flushProgress() }, delayNanos, TimeUnit.NANOSECONDS)
    }

    private fun isProgressActive(): Boolean =
        status == GenerationStatus.RUNNING || status == GenerationStatus.REGISTERED

    fun sendHeartbeat(snapshot: AiReplyProgressSnapshot?) {
        if (snapshot == null) return
        synchronized(sendLock) {
            if (status != GenerationStatus.RUNNING) return
            sendLocked("heartbeat", mapOf("generationId" to generationId, "progress" to progressMap(snapshot)))
        }
    }

    fun sendEvent(name: String, data: Any) {
        synchronized(sendLock) { sendLocked(name, data) }
    }

    fun sendTerminal(name: String, data: Any) {
        if (!terminalSent.compareAndSet(false, true)) return
        synchronized(sendLock) {
            status = GenerationStatus.FINISHED
            sendLocked(name, data)
            runCatching { emitter.complete() }
        }
    }

    fun requestCancel(): String {
        val active = synchronized(sendLock) {
            when (status) {
                GenerationStatus.REGISTERED, GenerationStatus.RUNNING -> {
                    status = GenerationStatus.CANCEL_REQUESTED
                    true
                }
                GenerationStatus.COMMITTING -> return "TOO_LATE"
                GenerationStatus.CANCEL_REQUESTED, GenerationStatus.FINISHED -> return "NOT_ACTIVE"
            }
        }
        if (active) {
            token.cancel()
            workerFuture?.cancel(true)
            heartbeatFuture?.cancel(false)
            sendTerminal("cancelled", mapOf("generationId" to generationId))
            cleanup()
            return "CANCEL_REQUESTED"
        }
        return "NOT_ACTIVE"
    }

    fun disconnect() {
        synchronized(sendLock) {
            status = GenerationStatus.FINISHED
            terminalSent.set(true)
        }
        token.cancel()
        workerFuture?.cancel(true)
        heartbeatFuture?.cancel(false)
        cleanup()
    }

    fun cleanup() {
        if (!cleanupDone.compareAndSet(false, true)) return
        heartbeatFuture?.cancel(false)
        synchronized(progressStateLock) {
            progressFlushFuture?.cancel(false)
            progressFlushFuture = null
            pendingProgress = null
            progressSendInFlight = false
        }
        onCleanup?.invoke()
    }

    private fun sendLocked(name: String, data: Any): Boolean {
        if (terminalSent.get() && name != "result" && name != "cancelled" && name != "error") return false
        return try {
            emitter.send(SseEmitter.event().name(name).data(data))
            true
        } catch (_: Exception) {
            status = GenerationStatus.FINISHED
            terminalSent.set(true)
            token.cancel()
            workerFuture?.cancel(true)
            heartbeatFuture?.cancel(false)
            cleanup()
            false
        }
    }

    private fun progressMap(snapshot: AiReplyProgressSnapshot): Map<String, Any> = mapOf(
        "generationId" to snapshot.generationId,
        "progressSeq" to snapshot.progressSeq,
        "phase" to snapshot.phase.name,
        "providerActivity" to snapshot.providerActivity.name,
        "providerCallIndex" to snapshot.providerCallIndex,
        "attemptElapsedSeconds" to snapshot.attemptElapsedSeconds,
        "attemptTimeoutSeconds" to snapshot.attemptTimeoutSeconds,
        "totalElapsedSeconds" to snapshot.totalElapsedSeconds,
        "totalTimeoutSeconds" to snapshot.totalTimeoutSeconds,
        "providerEventCount" to snapshot.providerEventCount,
        "contentChars" to snapshot.contentChars,
        "secondsSinceProviderActivity" to snapshot.secondsSinceProviderActivity
    )
}
