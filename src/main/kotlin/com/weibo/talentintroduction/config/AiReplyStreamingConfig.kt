package com.weibo.talentintroduction.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Configuration
class AiReplyStreamingConfig {
    @Bean(destroyMethod = "shutdown")
    fun aiReplyStreamExecutor(): ExecutorService =
        ThreadPoolExecutor(
            2,
            8,
            30L,
            TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(32),
            { runnable -> Thread(runnable, "ai-reply-stream-").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy()
        ).apply { allowCoreThreadTimeOut(false) }

    @Bean(destroyMethod = "shutdown")
    fun aiReplyStreamScheduler(): ScheduledExecutorService =
        Executors.newScheduledThreadPool(2) { runnable ->
            Thread(runnable, "ai-reply-heartbeat-").apply { isDaemon = true }
        }

    @Bean
    fun aiReplySseHttpClient(): HttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build()
}
