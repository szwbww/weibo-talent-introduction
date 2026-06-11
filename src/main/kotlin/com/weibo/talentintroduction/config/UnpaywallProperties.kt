package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.unpaywall")
data class UnpaywallProperties(
    val baseUrl: String = "https://api.unpaywall.org/v2",
    val email: String = "",
    val requestDelayMs: Long = 200
)
