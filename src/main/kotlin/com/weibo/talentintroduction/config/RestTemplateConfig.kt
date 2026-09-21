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
import org.springframework.http.HttpMethod
import org.springframework.web.client.ResponseExtractor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.net.URI
import java.time.Duration
import java.time.Instant

/** R-1（V-4）：PDF 下载客户端的连接超时（既有的既有值，抽出为单一来源，供有界 client 复用）。 */
const val PDF_DOWNLOAD_CONNECT_TIMEOUT_MS: Long = 10_000L

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
            .setConnectTimeout(Duration.ofMillis(PDF_DOWNLOAD_CONNECT_TIMEOUT_MS))
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

    /**
     * R-1（V-4）：全文阶段（XML / OA URL / Unpaywall）共用的「剩余预算」执行器。
     * 唯一实现是 [BoundedFulltextHttp]；单独暴露为 bean 是为了让调用方可以注入并在测试中替换。
     */
    @Bean
    fun boundedHttpExecutor(): BoundedHttpExecutor = BoundedFulltextHttp

    /** I-2: one shared quota/rate authority per JVM, usable from injected and hand-built call sites alike. */
    @Bean
    fun openAlexRequestPolicy(openAlexProperties: OpenAlexProperties): OpenAlexRequestPolicy =
        OpenAlexRequestPolicy(openAlexProperties)
}

/**
 * R-1（V-4）：整条全文链的「剩余预算」HTTP 执行器。
 *
 * 单篇论文只有一个共享总时限，XML / 开放 OA URL / Unpaywall 三个阶段都必须在这**一个**预算内完成，
 * 包括已经在飞的连接、响应头读取与响应体读取：连接超时与读取超时都取
 * `min(客户端既有配置, 剩余预算)` —— 只会更短，因此正常（未过期 / 没有共享时限）调用方的行为逐字不变。
 *
 * 预算被压缩时不再挂 [RetryingClientHttpRequestInterceptor]：这时候连一次尝试的预算都不够，
 * 再叠内层重试就会让调用方越过总时限（外层的 `FetchRetry` 也会在每个尝试前重新判断 deadline）。
 * 消息转换器、错误处理与其余拦截器（例如 OpenAlex 认证）原样继承，认证/解析语义不变。
 */
interface BoundedHttpExecutor {
    /**
     * 在剩余预算内执行**一次** HTTP GET。连接与读取超时都取 `min(既有配置上限, remainingMs)`，
     * 因此只会更短；`remainingMs >=` 两项上限时必须原样使用 [base]（未过期 / 无共享时限的调用方行为不变）。
     */
    fun <T> execute(
        base: RestTemplate,
        uri: URI,
        connectCapMs: Long,
        readCapMs: Long,
        remainingMs: Long,
        responseExtractor: ResponseExtractor<T>
    ): T?
}

object BoundedFulltextHttp : BoundedHttpExecutor {

    /** 没有共享时限（deadline 为 null）时使用的「不限」预算：此时 [bounded] 直接返回原 client。 */
    const val UNBOUNDED_REMAINING_MS: Long = Long.MAX_VALUE

    /** 0 毫秒在 JDK 客户端里表示「无限等待」，因此生效超时至少 1 毫秒。 */
    private const val MIN_TIMEOUT_MS = 1L

    /** deadline 为 null → [UNBOUNDED_REMAINING_MS]；已过期 → 0。 */
    fun remainingMsOrUnbounded(deadline: Instant?): Long =
        if (deadline == null) UNBOUNDED_REMAINING_MS
        else Duration.between(Instant.now(), deadline).toMillis().coerceAtLeast(0L)

    /** 实际生效的超时：不大于客户端既有配置，也不小于 [MIN_TIMEOUT_MS]。 */
    fun effectiveTimeoutMs(configuredMs: Long, remainingMs: Long): Int =
        minOf(configuredMs, remainingMs).coerceAtLeast(MIN_TIMEOUT_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * 剩余预算不紧于任何一项配置时返回**同一个** client 实例（零分配、零行为变化）；
     * 否则返回一个一次性的有界 client：连接/读取超时都不超过剩余预算。
     */
    fun bounded(base: RestTemplate, connectCapMs: Long, readCapMs: Long, remainingMs: Long): RestTemplate {
        if (remainingMs >= connectCapMs && remainingMs >= readCapMs) return base
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(effectiveTimeoutMs(connectCapMs, remainingMs))
            setReadTimeout(effectiveTimeoutMs(readCapMs, remainingMs))
        }
        val bounded = RestTemplate(factory)
        bounded.messageConverters.clear()
        bounded.messageConverters.addAll(base.messageConverters)
        bounded.errorHandler = base.errorHandler
        bounded.interceptors.addAll(base.interceptors.filterNot { it is RetryingClientHttpRequestInterceptor })
        return bounded
    }

    fun <T : Any> getForObject(
        base: RestTemplate,
        url: String,
        responseType: Class<T>,
        connectCapMs: Long,
        readCapMs: Long,
        remainingMs: Long
    ): T? = bounded(base, connectCapMs, readCapMs, remainingMs).getForObject(url, responseType)

    override fun <T> execute(
        base: RestTemplate,
        uri: URI,
        connectCapMs: Long,
        readCapMs: Long,
        remainingMs: Long,
        responseExtractor: ResponseExtractor<T>
    ): T? = bounded(base, connectCapMs, readCapMs, remainingMs)
        .execute(uri, HttpMethod.GET, null, responseExtractor)
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
