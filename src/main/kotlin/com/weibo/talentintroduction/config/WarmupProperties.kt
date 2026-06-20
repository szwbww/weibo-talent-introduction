package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "talent-introduction.warmup")
data class WarmupProperties(
    val enabled: Boolean = false,
    val steps: List<WarmupStep> = listOf(
        WarmupStep(1, 20),
        WarmupStep(3, 40),
        WarmupStep(5, 80),
        WarmupStep(8, 160),
        WarmupStep(12, 320)
    )
)

data class WarmupStep(
    val dayFrom: Int,
    val limit: Int
)
