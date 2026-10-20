package com.weibo.talentintroduction.config

import com.weibo.talentintroduction.discovery.domain.VerificationMode
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.net.URI

@ConstructorBinding
@ConfigurationProperties(prefix = "talent-introduction.discovery-verification")
class DiscoveryVerificationProperties(
    val enabled: Boolean = false,
    val workerEnabled: Boolean = false,
    val mode: VerificationMode = VerificationMode.AUDIT,
    val apolloDefault: Boolean = true,
    val apolloApiKey: String = "",
    /** Legacy compatibility only: Apollo uses provider limits and Retry-After. */
    val requestsPerMinute: Int = 10,
    val requestsPerDay: Int = 500,
    /** ORCID evidence-read budget only; does not limit Apollo. */
    val maxRequestsPerItem: Int = 12,
    val maxPagesPerQuery: Int = 5,
    val maxTransientAttempts: Int = 3,
    val leaseSeconds: Long = 120,
    val pollMs: Long = 5000,
    val orcidPublicBaseUrl: String = "https://pub.orcid.org/v3.0",
    val orcidAccessToken: String = "",
    /** Single deployment switch for the frozen-registration institution country supplement. */
    val countrySupplementEnabled: Boolean = false,
    val httpConnectTimeout: Duration = Duration.ofSeconds(3),
    val httpReadTimeout: Duration = Duration.ofSeconds(15)
) {
    init {
        require(requestsPerMinute in 1..100000 && requestsPerDay in 1..10000000)
        require(maxRequestsPerItem in 1..10000 && maxPagesPerQuery in 1..500)
        require(maxTransientAttempts in 1..3)
        require(leaseSeconds in 31..86400 && pollMs in 100..3600000)
        require(httpConnectTimeout > Duration.ZERO && httpConnectTimeout <= Duration.ofMinutes(1))
        require(httpReadTimeout > Duration.ZERO && httpReadTimeout <= Duration.ofMinutes(5))
        require(URI(orcidPublicBaseUrl).let { it.scheme == "https" && it.host != null && it.userInfo == null })
    }
    override fun toString(): String = "DiscoveryVerificationProperties(enabled=$enabled, workerEnabled=$workerEnabled, mode=$mode)"
}

@Configuration
@EnableConfigurationProperties(DiscoveryVerificationProperties::class)
class DiscoveryVerificationPropertiesConfig
