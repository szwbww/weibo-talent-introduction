package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.arxiv")
data class ArxivProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "http://export.arxiv.org/api",
    val requestDelayMs: Long = 3000,
    val maxPapersPerSource: Int = 100
)
