package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery")
data class ExpertDiscoveryProperties(
    val enabled: Boolean = false,
    val cron: String = "-",
    val maxPapersPerRun: Int = 500,
    val maxAuthorsPerRun: Int = 2000
)
