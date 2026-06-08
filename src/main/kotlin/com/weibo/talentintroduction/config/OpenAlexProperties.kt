package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.openalex")
data class OpenAlexProperties(
    val enabled: Boolean = false,
    val politeEmail: String = "",
    val baseUrl: String = "https://api.openalex.org",
    val requestDelayMs: Long = 100
)
