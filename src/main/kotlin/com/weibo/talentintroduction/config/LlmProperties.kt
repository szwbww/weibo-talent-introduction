package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm")
data class LlmProperties(
    val enabled: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val replyFlashModel: String = "deepseek-v4-flash",
    val replyProModel: String = "deepseek-v4-pro",
    val timeoutMs: Int = 30_000,
    val temperature: Double = 0.3,
    val freeFormTemperature: Double = 0.6,
    val shadowScoringEnabled: Boolean = false
)
