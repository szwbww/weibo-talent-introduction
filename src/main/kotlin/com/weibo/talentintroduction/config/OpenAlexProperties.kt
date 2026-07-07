package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.openalex")
data class OpenAlexProperties(
    val enabled: Boolean = false,
    val politeEmail: String = "",
    val baseUrl: String = "https://api.openalex.org",
    val requestDelayMs: Long = 100,
    val maxPapersPerSource: Int = 500,
    val connectTimeoutMs: Int = 5000,
    val readTimeoutMs: Int = 15000,
    val enrichmentDelayMs: Long = 300,
    val enrichmentBatchSize: Int = 50,
    val fetchWorksEnabled: Boolean = false,
    val fetchPatentsEnabled: Boolean = false
)
