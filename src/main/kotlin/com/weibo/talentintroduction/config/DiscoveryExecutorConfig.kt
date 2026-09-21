package com.weibo.talentintroduction.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class DiscoveryExecutorConfig(
    private val discoveryProperties: ExpertDiscoveryProperties
) {

    @Bean("discoveryFetchExecutor")
    fun discoveryFetchExecutor(): Executor {
        val concurrency = discoveryProperties.fetchConcurrency.coerceAtLeast(1)
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = concurrency
        executor.maxPoolSize = concurrency
        executor.setQueueCapacity(discoveryProperties.maxPapersPerRun.coerceAtLeast(100))
        executor.setThreadNamePrefix("discovery-fetch-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean("enrichmentExecutor")
    fun enrichmentExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("enrichment-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    /**
     * I-2（08）：自动补全 worker 的专用单线程 executor。`@Scheduled` 默认只有一个调度线程、
     * 与邮件定时任务共用，补全批次含外部 HTTP，绝不能占用它；队列容量 0 →
     * 上一批仍在执行时新的提交被拒绝（worker 记为一次跳过，并释放任务互斥）。
     */
    @Bean("autoEnrichmentExecutor")
    fun autoEnrichmentExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("auto-enrichment-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    /**
     * 专家分类回填专用单线程 executor（I2-5：同一时刻最多一个分类任务）。
     * 队列容量 0 → 任务已在执行时新的 submit 立即抛 RejectedExecutionException，
     * 由 ExpertClassificationAdminController 转为 409 并清理 pending context。
     */
    @Bean("expertClassificationExecutor")
    fun expertClassificationExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("expert-classification-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}
