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

    /**
     * I-6/I-7（c2）：持久化队列的**协调者** executor —— 单线程、队列容量 0。
     *
     * `tick()` 是短事务：判定 `PAUSED`/`nextWake`/owner 之后把窗口循环派发到这里并立即返回。
     * 容量 0 意味着「上一个窗口还在跑」时新的 tick 派发被拒绝，此时服务**保留 `QUEUED`**
     * 交给下一次 tick（绝不并发开第二个窗口）。窗口循环本身只做协调：采集与提取都在各自的池里，
     * 它绝不 `join` 整页。
     */
    @Bean("pipelineCoordinatorExecutor")
    fun pipelineCoordinatorExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("discovery-pipeline-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    /**
     * I-6（c2）：采集 executor —— 4 线程、队列 8，因此最多 4 个来源页同时在飞，其余待派发的页排队。
     * 同一来源同时最多一个未完成页（由 stream 租约保证），所以源 A 卡住不会阻止源 B 继续。
     */
    @Bean("pipelineCollectionExecutor")
    fun pipelineCollectionExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = PIPELINE_COLLECTION_CONCURRENCY
        executor.maxPoolSize = PIPELINE_COLLECTION_CONCURRENCY
        executor.setQueueCapacity(PIPELINE_COLLECTION_QUEUE_CAPACITY)
        executor.setThreadNamePrefix("discovery-collect-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    /**
     * I-6（c2）：全文提取 executor —— 线程数即「全局最多同时 8 个提取任务」的唯一实现点
     * （旧 `discoveryFetchExecutor` 的并发语义逐字不变，两者互不影响）。队列容量 0：
     * 协调者只在有空闲槽位时才提交，真的被拒绝时把刚领取的 job 退回 `PENDING`（容量等待，
     * 不消耗 attempts），绝不排队堆积。
     */
    @Bean("pipelineFetchExecutor")
    fun pipelineFetchExecutor(): Executor {
        val concurrency = discoveryProperties.pipelineFetchConcurrency.coerceAtLeast(1)
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = concurrency
        executor.maxPoolSize = concurrency
        executor.setQueueCapacity(0)
        executor.setThreadNamePrefix("discovery-pipeline-fetch-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    private companion object {
        /** I-6（c2）：采集池固定 4 线程，避免窗口把并发都花在取数上。 */
        const val PIPELINE_COLLECTION_CONCURRENCY = 4

        /** I-6（c2）：采集池队列容量 8 —— 已就绪来源可继续，不会被在飞页完全堵死。 */
        const val PIPELINE_COLLECTION_QUEUE_CAPACITY = 8
    }
}
