package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.openalex")
data class OpenAlexProperties(
    val enabled: Boolean = false,
    val politeEmail: String = "",
    val baseUrl: String = "https://api.openalex.org",
    val requestDelayMs: Long = 100,
    val maxPapersPerSource: Int = 500,
    val connectTimeoutMs: Int = 5000,
    val readTimeoutMs: Int = 15000,
    val enrichmentDelayMs: Long = 300,
    val enrichmentBatchSize: Int = 50,
    val enrichmentRateLimitMode: String = "WAIT",
    val enrichmentMaxBackoffMs: Long = 1_800_000,
    val fetchWorksEnabled: Boolean = false,
    val fetchPatentsEnabled: Boolean = false,
    /** I-1: API key from the environment only; blank keeps the anonymous (keyless) behaviour. */
    val apiKey: String = "",
    /** I-3: suggested 5/s, never above the official 100/s (see [OpenAlexRequestPolicy]). */
    val maxRequestsPerSecond: Double = 5.0,
    /** I-3: share of the daily budget held back for new-expert enrichment (0.2 = 20%). */
    val newEnrichmentReserveRatio: Double = 0.2,
    /** I-3: optional daily spend cap in USD; 0 (default) uses the provider free budget (keyed $1, keyless $0.10). */
    val dailyBudgetUsd: Double = 0.0,
    /** I-2: upper bound for the request-rate 429 backoff. */
    val rateLimitBackoffMaxMs: Long = 60_000
) {
    /** I-1: never log the configuration object with a live key in it. */
    override fun toString(): String =
        "OpenAlexProperties(enabled=$enabled, politeEmail=$politeEmail, baseUrl=$baseUrl, " +
            "requestDelayMs=$requestDelayMs, maxPapersPerSource=$maxPapersPerSource, " +
            "connectTimeoutMs=$connectTimeoutMs, readTimeoutMs=$readTimeoutMs, " +
            "enrichmentDelayMs=$enrichmentDelayMs, enrichmentBatchSize=$enrichmentBatchSize, " +
            "enrichmentRateLimitMode=$enrichmentRateLimitMode, enrichmentMaxBackoffMs=$enrichmentMaxBackoffMs, " +
            "fetchWorksEnabled=$fetchWorksEnabled, fetchPatentsEnabled=$fetchPatentsEnabled, " +
            "maxRequestsPerSecond=$maxRequestsPerSecond, newEnrichmentReserveRatio=$newEnrichmentReserveRatio, " +
            "dailyBudgetUsd=$dailyBudgetUsd, rateLimitBackoffMaxMs=$rateLimitBackoffMaxMs, apiKey=" +
            (if (apiKey.isBlank()) "\"\"" else "\"***\"") + ")"
}
