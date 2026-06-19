package com.weibo.talentintroduction.mail.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class AccountRateLimiter {
    private data class RateState(
        val currentIntervalMs: Long,
        val backoffLevel: Int,
        val consecutiveSuccesses: Int
    )

    private val states = ConcurrentHashMap<String, RateState>()

    companion object {
        const val MAX_BACKOFF_LEVEL = 5
        const val RECOVERY_THRESHOLD = 10
        const val MAX_INTERVAL_MS = 60_000L
    }

    fun getIntervalMs(accountCode: String, baseIntervalMs: Long): Long {
        val state = states[accountCode] ?: return baseIntervalMs
        return maxOf(state.currentIntervalMs, baseIntervalMs)
    }

    fun recordSuccess(accountCode: String, baseIntervalMs: Long) {
        states.compute(accountCode) { _, existing ->
            if (existing == null || existing.backoffLevel == 0) return@compute null
            val newSuccesses = existing.consecutiveSuccesses + 1
            if (newSuccesses >= RECOVERY_THRESHOLD && existing.backoffLevel > 0) {
                val newLevel = existing.backoffLevel - 1
                val newInterval = if (newLevel == 0) baseIntervalMs
                else baseIntervalMs * (1L shl newLevel)
                RateState(minOf(newInterval, MAX_INTERVAL_MS), newLevel, 0)
            } else {
                existing.copy(consecutiveSuccesses = newSuccesses)
            }
        }
    }

    fun recordThrottled(accountCode: String, baseIntervalMs: Long) {
        states.compute(accountCode) { _, existing ->
            val currentLevel = existing?.backoffLevel ?: 0
            val newLevel = minOf(currentLevel + 1, MAX_BACKOFF_LEVEL)
            val newInterval = baseIntervalMs * (1L shl newLevel)
            RateState(minOf(newInterval, MAX_INTERVAL_MS), newLevel, 0)
        }
    }

    fun getSnapshot(): Map<String, Long> =
        states.mapValues { it.value.currentIntervalMs }

    fun clear() = states.clear()
}
