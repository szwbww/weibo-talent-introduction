package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.llm")
data class LlmProperties(
    val enabled: Boolean = false,
    val apiUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val timeoutMs: Int = 30_000
)
