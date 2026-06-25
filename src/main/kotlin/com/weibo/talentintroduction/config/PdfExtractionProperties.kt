package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.pdf-extraction")
data class PdfExtractionProperties(
    val maxPdfSizeBytes: Long = 10_485_760,
    val downloadTimeoutMs: Long = 60_000,
    val maxPages: Int = 2,
    val blacklistPrefixes: List<String> = listOf(
        "support", "info", "journals", "permissions", "editorial", "office", "help", "admin"
    ),
    val maxRetries: Int = 2,
    val retryBackoffMs: Long = 500,
    val htmlFallbackEnabled: Boolean = true
)
