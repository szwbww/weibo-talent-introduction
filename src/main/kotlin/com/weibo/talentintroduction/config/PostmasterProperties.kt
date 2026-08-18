package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.postmaster")
data class PostmasterProperties(
    val enabled: Boolean = false,
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val oauthRedirectUri: String = "",
    val oauthTokenFile: String = "/etc/talent/secrets/postmaster-oauth-token.json",
    val domains: List<String> = emptyList(),
    val cron: String = "0 0 8 * * *",
    val pauseThresholdSpamRate: Double = 0.003,
    val resumeThresholdSpamRate: Double = 0.001,
    val resumeConsecutiveDays: Int = 3
)
