package com.weibo.talentintroduction.task.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TaskProgressStore(
    private val progressLogRepository: TaskProgressLogRepository,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(TaskProgressStore::class.java)

    private val store = ConcurrentHashMap<String, TaskProgress>()
    private val cancellationFlags = ConcurrentHashMap<String, Boolean>()

    fun update(taskType: String, progress: TaskProgress, expectedExecutionId: Long? = null): Boolean {
        var accepted = false
        val result = store.compute(taskType) { _, current ->
            if (current == null) {
                if (expectedExecutionId != null) return@compute null
                accepted = true
                return@compute progress.copy(executionId = null)
            }
            if (expectedExecutionId != null && current.executionId != expectedExecutionId) {
                log.warn("Stale progress update rejected for {}: expected execId={}, current={}", taskType, expectedExecutionId, current.executionId)
                accepted = false
                return@compute current
            }
            accepted = true
            progress.copy(executionId = current.executionId)
        }
        if (accepted && result != null) {
            persistProgressLog(taskType, result)
        }
        return accepted
    }

    fun get(taskType: String): TaskProgress? {
        val cached = store[taskType]
        if (cached != null) return cached
        return restoreFromLog(taskType)
    }

    fun clear(taskType: String) {
        val removed = store.remove(taskType)
        if (removed?.executionId != null) {
            cancellationFlags.remove("$taskType:${removed.executionId}")
        }
    }

    fun clearExecutionContext(taskType: String, expectedExecutionId: Long): Boolean {
        var accepted = false
        store.compute(taskType) { _, current ->
            if (current == null) {
                accepted = false
                return@compute null
            }
            if (current.executionId != expectedExecutionId) {
                log.warn("Stale clear rejected for {}: expected execId={}, current={}", taskType, expectedExecutionId, current.executionId)
                accepted = false
                return@compute current
            }
            accepted = true
            current.copy(executionId = null)
        }
        cancellationFlags.remove("$taskType:$expectedExecutionId")
        return accepted
    }

    fun isRunning(taskType: String): Boolean {
        val current = get(taskType) ?: return false
        return current.status in setOf("RUNNING", "CANCELLING")
    }

    fun requestCancel(taskType: String): Boolean {
        var accepted = false
        val result = store.compute(taskType) { _, current ->
            if (current == null || current.status != "RUNNING") {
                accepted = false
                return@compute current
            }
            val executionId = current.executionId
            if (executionId == null) {
                accepted = false
                return@compute current
            }
            cancellationFlags["$taskType:$executionId"] = true
            accepted = true
            current.copy(status = "CANCELLING")
        }
        if (accepted && result != null) {
            persistProgressLog(taskType, result)
        }
        return accepted
    }

    fun isCancelled(taskType: String): Boolean {
        val executionId = store[taskType]?.executionId ?: return false
        return cancellationFlags["$taskType:$executionId"] == true
    }

    fun isCancelled(taskType: String, executionId: Long): Boolean {
        return cancellationFlags["$taskType:$executionId"] == true
    }

    fun setCurrentExecutionId(taskType: String, executionId: Long): Boolean {
        var accepted = false
        store.compute(taskType) { _, current ->
            if (current == null) {
                accepted = true
                TaskProgress(taskType = taskType, status = "RUNNING", batchNumber = 0, processedCount = 0, totalCount = 0, executionId = executionId)
            } else {
                val currentExecId = current.executionId
                if (currentExecId != null && currentExecId > 0 && currentExecId != executionId) {
                    log.warn("ExecutionId already set for {}, rejecting overwrite with {}", taskType, executionId)
                    accepted = false
                    return@compute current
                }
                accepted = true
                current.copy(executionId = executionId)
            }
        }
        return accepted
    }

    fun getCurrentExecutionId(taskType: String): Long? = store[taskType]?.executionId

    fun tryStart(taskType: String, initial: TaskProgress): Boolean {
        val result = store.compute(taskType) { _, current ->
            if (current?.status in setOf("RUNNING", "CANCELLING")) current else initial
        }
        val started = result === initial
        if (started) {
            persistProgressLog(taskType, result!!)
        }
        return started
    }

    fun tryStartWithToken(taskType: String, initial: TaskProgress): Pair<Boolean, Long> {
        val pendingToken = -System.nanoTime()
        val toInsert = initial.copy(executionId = pendingToken)
        val result = store.compute(taskType) { _, current ->
            if (current?.status in setOf("RUNNING", "CANCELLING")) current else toInsert
        }
        val started = result === toInsert
        if (started && result != null) {
            persistProgressLog(taskType, result)
        }
        return Pair(started, pendingToken)
    }

    fun bindExecutionId(taskType: String, pendingToken: Long, executionId: Long): Boolean {
        var accepted = false
        store.compute(taskType) { _, current ->
            if (current == null) {
                accepted = false
                return@compute null
            }
            if (current.executionId != pendingToken) {
                log.warn("Stale bind rejected for {}: expected token={}, current={}", taskType, pendingToken, current.executionId)
                accepted = false
                return@compute current
            }
            accepted = true
            current.copy(executionId = executionId)
        }
        if (accepted) {
            if (cancellationFlags.remove("$taskType:$pendingToken") == true) {
                cancellationFlags["$taskType:$executionId"] = true
            }
            try {
                progressLogRepository.rebindPendingExecutionId(pendingToken, executionId)
            } catch (e: Exception) {
                log.warn("Failed to rebind pending progress logs from token {} to execution {}: {}", pendingToken, executionId, e.message, e)
            }
        }
        return accepted
    }

    private fun persistProgressLog(taskType: String, progress: TaskProgress) {
        try {
            val logEntry = TaskProgressLog(
                taskType = taskType,
                taskExecutionId = progress.executionId,
                batchNumber = progress.batchNumber,
                status = progress.status,
                processedCount = progress.processedCount,
                totalCount = progress.totalCount,
                batchProcessed = progress.batchProcessed,
                batchPassed = progress.batchPassed,
                batchRejected = progress.batchRejected,
                batchRejectReasonsJson = progress.batchRejectReasons?.let { objectMapper.writeValueAsString(it) },
                message = progress.message,
                detailsJson = progress.details?.let { objectMapper.writeValueAsString(it) },
                errorsJson = progress.errors?.let { objectMapper.writeValueAsString(it) }
            )
            progressLogRepository.save(logEntry)
        } catch (e: Exception) {
            log.warn("Failed to persist progress log for {}: {}", taskType, e.message, e)
        }
    }

    private fun restoreFromLog(taskType: String): TaskProgress? {
        try {
            val latestLog = progressLogRepository.findTopByTaskTypeOrderByIdDesc(taskType)
                ?: return null

            val status = when (latestLog.status) {
                "RUNNING", "CANCELLING" -> "INTERRUPTED"
                else -> latestLog.status
            }

            val batchRejectReasons = latestLog.batchRejectReasonsJson?.let { json ->
                try {
                    objectMapper.readValue<Map<String, Int>>(json)
                } catch (e: Exception) {
                    log.warn("Failed to parse batchRejectReasonsJson for {}: {}", taskType, e.message)
                    null
                }
            }

            return TaskProgress(
                taskType = taskType,
                status = status,
                batchNumber = latestLog.batchNumber,
                processedCount = latestLog.processedCount,
                totalCount = latestLog.totalCount,
                message = latestLog.message,
                details = latestLog.detailsJson?.let {
                    objectMapper.readValue<Map<String, Any>>(it)
                },
                errors = latestLog.errorsJson?.let {
                    objectMapper.readValue<List<String>>(it)
                },
                batchProcessed = latestLog.batchProcessed,
                batchPassed = latestLog.batchPassed,
                batchRejected = latestLog.batchRejected,
                batchRejectReasons = batchRejectReasons,
                executionId = latestLog.taskExecutionId
            )
        } catch (e: Exception) {
            log.warn("Failed to restore progress for {} from log: {}", taskType, e.message, e)
            return null
        }
    }
}

data class TaskProgress(
    val taskType: String,
    val status: String,
    val batchNumber: Int,
    val processedCount: Long,
    val totalCount: Long,
    val message: String? = null,
    val details: Map<String, Any>? = null,
    val errors: List<String>? = null,
    val batchProcessed: Int = 0,
    val batchPassed: Int = 0,
    val batchRejected: Int = 0,
    val batchRejectReasons: Map<String, Int>? = null,
    val executionId: Long? = null
) {
    val percentage: Int
        get() = if (totalCount > 0) ((processedCount * 100) / totalCount).toInt().coerceIn(0, 100) else 0
}
