package com.weibo.talentintroduction.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class ManualOutreachConfig {

    @Bean("manualOutreachExecutor")
    fun manualOutreachExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("manual-outreach-")
        executor.initialize()
        return executor
    }
}
