package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.core")
data class CoreProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.core.ac.uk/v3",
    val apiKey: String = "",
    val requestDelayMs: Long = 600,
    val maxPapersPerSource: Int = 300
)
