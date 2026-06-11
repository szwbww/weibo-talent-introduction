package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.crossref")
data class CrossrefProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://api.crossref.org",
    val politeEmail: String = "",
    val requestDelayMs: Long = 200,
    val maxPapersPerSource: Int = 300
)
