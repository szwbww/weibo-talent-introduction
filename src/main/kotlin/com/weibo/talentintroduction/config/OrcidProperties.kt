package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.orcid")
data class OrcidProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://pub.orcid.org/v3.0",
    val requestDelayMs: Long = 100,
    val maxRecordsPerRun: Int = 1000
)
