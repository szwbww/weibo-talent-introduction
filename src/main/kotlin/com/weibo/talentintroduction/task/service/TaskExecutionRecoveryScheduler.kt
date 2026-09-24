package com.weibo.talentintroduction.task.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

@Component
class TaskExecutionRecoveryScheduler(private val service: TaskExecutionService) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val started = AtomicBoolean(false)
    // 与业务 cron 隔离：长任务占满共享调度池时，租约心跳仍须继续。
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "task-execution-recovery").apply { isDaemon = true }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!started.compareAndSet(false, true)) return
        reconcile()
        executor.scheduleWithFixedDelay({ reconcile() }, 30, 30, TimeUnit.SECONDS)
    }

    fun reconcile() {
        try {
            val recovered = service.reconcileInterruptedExecutions()
            if (recovered > 0) log.warn("Marked {} expired task executions INTERRUPTED", recovered)
        } catch (ex: Exception) {
            log.warn("Task execution heartbeat/recovery failed; will retry", ex)
        }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }
}
