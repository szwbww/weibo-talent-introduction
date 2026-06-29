package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.translation")
data class TranslationProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "http://127.0.0.1:5000",
    val source: String = "en",
    val target: String = "zh-Hans",
    val timeoutMs: Int = 5000,
    val maxChars: Int = 5000,
    val apiKey: String? = null
)
