package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ScheduledThreadPoolExecutor

class SchedulingConfigTest {
    private val config = SchedulingConfig()

    @Test
    fun `taskScheduler is ThreadPoolTaskScheduler with app-sched prefix`() {
        val scheduler = config.taskScheduler()

        assertTrue(scheduler is ThreadPoolTaskScheduler)
        assertEquals("app-sched-", scheduler.threadNamePrefix)
        assertEquals(
            4,
            (scheduler.scheduledExecutor as ScheduledThreadPoolExecutor).corePoolSize
        )

        scheduler.shutdown()
    }
}
