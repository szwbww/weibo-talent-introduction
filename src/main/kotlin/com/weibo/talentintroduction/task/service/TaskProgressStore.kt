package com.weibo.talentintroduction.task.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class TaskProgressStore {

    private val store = ConcurrentHashMap<String, TaskProgress>()

    fun update(taskType: String, progress: TaskProgress) {
        store[taskType] = progress
    }

    fun get(taskType: String): TaskProgress? = store[taskType]

    fun clear(taskType: String) {
        store.remove(taskType)
    }

    fun isRunning(taskType: String): Boolean {
        val current = store[taskType] ?: return false
        return current.status == "RUNNING"
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
    val errors: List<String>? = null
) {
    val percentage: Int
        get() = if (totalCount > 0) ((processedCount * 100) / totalCount).toInt().coerceIn(0, 100) else 0
}
