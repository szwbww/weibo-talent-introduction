package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.europe-pmc")
data class EuropePmcProperties(
    val baseUrl: String = "https://www.ebi.ac.uk/europepmc/webservices/rest",
    val requestDelayMs: Long = 100,
    val enabled: Boolean = true,
    val connectTimeoutMs: Int = 5000,
    val readTimeoutMs: Int = 30000,
    val maxPapersPerSource: Int = 500
)
