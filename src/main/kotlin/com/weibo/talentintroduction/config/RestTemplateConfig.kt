package com.weibo.talentintroduction.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Duration

@Configuration
@EnableConfigurationProperties(
    ElasticsearchProperties::class,
    CandidateFilterProperties::class,
    MailQueueProperties::class,
    MailSchedulingProperties::class,
    MailAttachmentStorageProperties::class,
    EmailValidationProperties::class,
    AcademicFilterProperties::class,
    EuropePmcProperties::class,
    ExpertDiscoveryProperties::class,
    OpenAlexProperties::class,
    CrossrefProperties::class,
    ArxivProperties::class,
    UnpaywallProperties::class,
    PdfExtractionProperties::class,
    PmcOaProperties::class,
    OrcidProperties::class,
    CoreProperties::class,
    ManualOutreachProperties::class,
    UnsubscribeProperties::class,
    WarmupProperties::class,
    LlmProperties::class,
    TranslationProperties::class,
    PostmasterProperties::class,
    ExpertClassificationProperties::class
)
class RestTemplateConfig {

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()

    @Bean
    @Qualifier("europePmcRestTemplate")
    fun europePmcRestTemplate(
        europePmcProperties: EuropePmcProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(europePmcProperties.connectTimeoutMs.toLong()))
            .setReadTimeout(Duration.ofMillis(europePmcProperties.readTimeoutMs.toLong()))
            .additionalInterceptors(
                RetryingClientHttpRequestInterceptor(
                    maxRetries = europePmcProperties.maxRetries,
                    initialBackoffMs = europePmcProperties.retryBackoffMs
                )
            )
            .build()

    @Bean
    @Qualifier("pdfDownloadRestTemplate")
    fun pdfDownloadRestTemplate(
        pdfExtractionProperties: PdfExtractionProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(10_000L))
            .setReadTimeout(Duration.ofMillis(pdfExtractionProperties.downloadTimeoutMs))
            .additionalInterceptors(
                RetryingClientHttpRequestInterceptor(
                    maxRetries = pdfExtractionProperties.maxRetries,
                    initialBackoffMs = pdfExtractionProperties.retryBackoffMs
                )
            )
            .build()

    @Bean
    @Qualifier("translationRestTemplate")
    fun translationRestTemplate(
        translationProperties: TranslationProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(translationProperties.timeoutMs.toLong()))
            .setReadTimeout(Duration.ofMillis(translationProperties.timeoutMs.toLong()))
            .build()

    @Bean
    @Qualifier("openAlexRestTemplate")
    fun openAlexRestTemplate(
        openAlexProperties: OpenAlexProperties,
        builder: RestTemplateBuilder
    ): RestTemplate =
        builder
            .setConnectTimeout(Duration.ofMillis(openAlexProperties.connectTimeoutMs.toLong()))
            .setReadTimeout(Duration.ofMillis(openAlexProperties.readTimeoutMs.toLong()))
            .additionalInterceptors(
                OpenAlexAuthInterceptor(
                    apiKey = openAlexProperties.apiKey,
                    baseUrl = openAlexProperties.baseUrl
                )
            )
            .build()

    /** I-2: one shared quota/rate authority per JVM, usable from injected and hand-built call sites alike. */
    @Bean
    fun openAlexRequestPolicy(openAlexProperties: OpenAlexProperties): OpenAlexRequestPolicy =
        OpenAlexRequestPolicy(openAlexProperties)
}

/**
 * I-1: the OpenAlex bearer token is attached only to the configured OpenAlex API HTTPS origin. Any other origin
 * (external fulltext hosts, a cross-origin redirect target) has its credential stripped, so the key cannot leave the
 * configured origin. The key is never put into a URL and never logged.
 */
class OpenAlexAuthInterceptor(apiKey: String, baseUrl: String) : ClientHttpRequestInterceptor {

    private val apiKey = apiKey.trim()
    private val trustedScheme: String
    private val trustedHost: String
    private val trustedPort: Int

    init {
        val base = try {
            URI.create(baseUrl.trim().trimEnd('/'))
        } catch (e: IllegalArgumentException) {
            null
        }
        val scheme = base?.scheme?.lowercase().orEmpty()
        trustedScheme = scheme
        trustedHost = base?.host?.lowercase().orEmpty()
        trustedPort = normalisePort(scheme, base?.port ?: -1)
    }

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        if (apiKey.isNotBlank() && isTrustedOrigin(request.uri)) {
            request.headers.setBearerAuth(apiKey)
        } else {
            request.headers.remove(HttpHeaders.AUTHORIZATION)
        }
        return execution.execute(request, body)
    }

    private fun isTrustedOrigin(uri: URI): Boolean {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        if (trustedHost.isEmpty() || trustedScheme != HTTPS_SCHEME || scheme != HTTPS_SCHEME) return false
        return host == trustedHost && normalisePort(scheme, uri.port) == trustedPort
    }

    companion object {
        private const val HTTPS_SCHEME = "https"

        private fun normalisePort(scheme: String, port: Int): Int = when {
            port != -1 -> port
            scheme == HTTPS_SCHEME -> 443
            else -> 80
        }
    }
}
