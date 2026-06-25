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
}
