package com.weibo.talentintroduction.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.expert-discovery.openalex")
data class OpenAlexProperties(
    val enabled: Boolean = false,
    val politeEmail: String = "",
    val baseUrl: String = "https://api.openalex.org",
    val requestDelayMs: Long = 100,
    /**
     * I-1（09）：OpenAlex 的单次运行上限 = 上线目标 10000（与 application.yml 的
     * `OPENALEX_MAX_PAPERS` 同值）。它是**本源**额度，受全局论文上限与后来源保留份额约束。
     */
    val maxPapersPerSource: Int = 10000,
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
    val rateLimitBackoffMaxMs: Long = 60_000,
    /**
     * I-2：账号的稳定**非秘密**标识。同一 API Key 的全部本系统实例必须使用同一个 scope，
     * 预算才真正共享；换 Key 不换 scope（额度不会重置），换成另一个真实账号才换 scope。
     * 它绝不能由 Key 明文派生。
     */
    val accountScope: String = "primary",
    /**
     * I-2：免费消费保护上限（credits）。有效免费上限 = min(官方日免费额度, 本值, 旧 dailyBudgetUsd 折算)，
     * 预付余额永不加入。默认 10000 = 当前官方免费账号的日额度。
     */
    val freeBudgetCredits: Long = 10000,
    /** I-4：官方余额校准周期（启动/日切/额度不足/异常恢复之外，每满该间隔重新校准一次）。 */
    val budgetSyncInterval: Duration = Duration.ofSeconds(300)
) {
    init {
        require(dailyBudgetUsd >= 0.0) {
            "talent-introduction.expert-discovery.openalex.daily-budget-usd must not be negative (was $dailyBudgetUsd)"
        }
        require(freeBudgetCredits >= 0L) {
            "talent-introduction.expert-discovery.openalex.free-budget-credits must not be negative (was $freeBudgetCredits)"
        }
        require(!budgetSyncInterval.isNegative && !budgetSyncInterval.isZero) {
            "talent-introduction.expert-discovery.openalex.budget-sync-interval must be positive (was $budgetSyncInterval)"
        }
        require(accountScope.isNotBlank()) {
            "talent-introduction.expert-discovery.openalex.account-scope must not be blank"
        }
    }

    /** I-1: never log the configuration object with a live key in it. */
    override fun toString(): String =
        "OpenAlexProperties(enabled=$enabled, politeEmail=$politeEmail, baseUrl=$baseUrl, " +
            "requestDelayMs=$requestDelayMs, maxPapersPerSource=$maxPapersPerSource, " +
            "connectTimeoutMs=$connectTimeoutMs, readTimeoutMs=$readTimeoutMs, " +
            "enrichmentDelayMs=$enrichmentDelayMs, enrichmentBatchSize=$enrichmentBatchSize, " +
            "enrichmentRateLimitMode=$enrichmentRateLimitMode, enrichmentMaxBackoffMs=$enrichmentMaxBackoffMs, " +
            "fetchWorksEnabled=$fetchWorksEnabled, fetchPatentsEnabled=$fetchPatentsEnabled, " +
            "maxRequestsPerSecond=$maxRequestsPerSecond, newEnrichmentReserveRatio=$newEnrichmentReserveRatio, " +
            "dailyBudgetUsd=$dailyBudgetUsd, rateLimitBackoffMaxMs=$rateLimitBackoffMaxMs, " +
            "accountScope=$accountScope, freeBudgetCredits=$freeBudgetCredits, " +
            "budgetSyncInterval=$budgetSyncInterval, apiKey=" +
            (if (apiKey.isBlank()) "\"\"" else "\"***\"") + ")"
}
