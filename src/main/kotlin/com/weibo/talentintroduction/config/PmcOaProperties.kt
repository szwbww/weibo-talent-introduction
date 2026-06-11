package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.pmc-oa")
data class PmcOaProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
    val apiKey: String = "",
    val requestDelayMs: Long = 350,
    val maxPapersPerSource: Int = 500
)
