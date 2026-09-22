package com.weibo.talentintroduction.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpMethod
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.ResponseExtractor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
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
            .requestFactory { NoRedirectRequestFactory() }
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

    /**
     * I-2/I-4：官方余额校准的取数接缝 —— 唯一实现 [HttpOpenAlexBudgetSyncSource] 走认证 client，
     * 只读 `/rate-limit` 的额度字段。取数失败一律视为「未获得可信余额」。
     */
    @Bean
    fun openAlexBudgetSyncSource(
        @Qualifier("openAlexRestTemplate") openAlexRestTemplate: RestTemplate,
        openAlexProperties: OpenAlexProperties
    ): OpenAlexBudgetSyncSource = HttpOpenAlexBudgetSyncSource(openAlexRestTemplate, openAlexProperties)

    /**
     * I-2/I-3/I-4：生产预算策略 Bean —— **强制注入共享 JDBC 账本**（[OpenAlexBudgetStore] 的 MySQL 实现）
     * 与官方校准接缝；两者都是必需依赖，缺失时 Spring 启动直接失败，绝不退回内存额度。
     */
    @Bean
    fun sharedOpenAlexRequestPolicy(
        openAlexProperties: OpenAlexProperties,
        budgetStore: OpenAlexBudgetStore,
        budgetSyncSource: OpenAlexBudgetSyncSource
    ): OpenAlexRequestPolicy =
        OpenAlexRequestPolicy(openAlexProperties, PolicyTimeSource.SYSTEM, budgetStore, budgetSyncSource)

    /**
     * 非 Bean 兼容入口：旧单元测试直接调用它（内存账本、无官方校准）。生产装配一律走
     * [sharedOpenAlexRequestPolicy]，本方法不参与任何生产装配。
     */
    fun openAlexRequestPolicy(openAlexProperties: OpenAlexProperties): OpenAlexRequestPolicy =
        OpenAlexRequestPolicy(openAlexProperties)
}

/**
 * I-1/I-2：OpenAlex API client **禁止自动重定向**。
 *
 * API 端点正常不重定向；一旦出现 3xx，JDK 客户端默认会静默跟随并把认证拦截器已写入的凭证带到新 origin。
 * 关闭自动跟随让 3xx 变成普通非 2xx 响应（由调用方处理），跨 origin 重定向绝不可能被自动执行。
 */
private class NoRedirectRequestFactory : SimpleClientHttpRequestFactory() {
    /** JDK 客户端对 GET 默认自动跟随重定向（见父类 `prepareConnection`），这里逐请求关掉它。 */
    override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
        super.prepareConnection(connection, httpMethod)
        connection.instanceFollowRedirects = false
    }
}

/**
 * I-4：官方 `/rate-limit` 校准取数。
 *
 * 分类为 [Operation.RATE_LIMIT]（0 credits，不产生账本行），由校准租约 + 同步间隔节流，不做任何自动重试；
 * 凭证只由既有认证拦截器发给配置的 OpenAlex origin，响应体只解析额度字段，绝不记录 Key。
 */
class HttpOpenAlexBudgetSyncSource(
    private val restTemplate: RestTemplate,
    private val properties: OpenAlexProperties
) : OpenAlexBudgetSyncSource {

    private val log = LoggerFactory.getLogger(HttpOpenAlexBudgetSyncSource::class.java)

    override fun fetchOfficialBalance(): OpenAlexOfficialBalance? {
        val url = "${properties.baseUrl.trimEnd('/')}/rate-limit"
        val response = try {
            restTemplate.exchange(url, HttpMethod.GET, null, com.fasterxml.jackson.databind.JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            log.warn("OpenAlex /rate-limit returned HTTP {}: {}", e.statusCode.value(), e.message)
            return null
        } catch (e: Exception) {
            log.warn("OpenAlex /rate-limit call failed: {}", e.message)
            return null
        }
        val body = response?.body ?: return null
        return parseOpenAlexOfficialBalance(body)
    }
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
     * 在单篇共享预算内执行**一次** GET：连接/读取超时取 `min(既有配置上限, 剩余预算)`（只会更短），
     * 响应体也在同一绝对 [deadline] 内读完。预算不是正数时**绝不 dispatch**。
     * [deadline] 为 `null`（没有共享时限）或剩余预算不紧于配置时，行为与直接使用 [base] 完全一致。
     */
    fun <T : Any> getForObject(
        base: RestTemplate,
        url: String,
        responseType: Class<T>,
        connectCapMs: Long,
        readCapMs: Long,
        deadline: Instant?
    ): T?

    /** 同上，但由调用方自己决定如何消费响应（流式读取等）。 */
    fun <T> execute(
        base: RestTemplate,
        uri: URI,
        connectCapMs: Long,
        readCapMs: Long,
        deadline: Instant?,
        responseExtractor: ResponseExtractor<T>
    ): T?
}

object BoundedFulltextHttp : BoundedHttpExecutor {

    /** 没有共享时限（deadline 为 null）时使用的「不限」预算：此时 [bounded] 直接返回原 client。 */
    const val UNBOUNDED_REMAINING_MS: Long = Long.MAX_VALUE

    /**
     * R-1（V-4）：preflight 与「真正发请求」之间的原子补位 —— 剩余预算不是正数时**绝不 dispatch**，
     * 因此不可能出现「刚判定已过期、却又用一个 1 毫秒 client 把请求发出去」的越界请求。
     */
    class NoRemainingBudgetException : IllegalStateException("fulltext budget exhausted before dispatch")

    /** R-1（V-4）：响应体读取越过绝对时限（服务端细水长流式响应也会被截断）。 */
    class FulltextBodyDeadlineExceededException : IllegalStateException("fulltext body exceeded the shared deadline")

    /** I-1：重定向落点是 OpenAlex Content/API 计量目的地 —— 绝不跟随（否则会消耗账号额度）。 */
    class MeteredRedirectException(target: String) :
        IllegalStateException("refusing to follow a redirect to an OpenAlex metered destination: $target")

    /** I-1：重定向跳数超过 [MAX_REDIRECTS] —— 停止而不是无限跟随。 */
    class TooManyRedirectsException(target: String) :
        IllegalStateException("too many redirects while fetching fulltext (last target: $target)")

    /** I-1：手工逐跳跟随的上限。 */
    const val MAX_REDIRECTS: Int = 5

    /** 0 毫秒在 JDK 客户端里表示「无限等待」，因此生效超时至少 1 毫秒。 */
    private const val MIN_TIMEOUT_MS = 1L

    /** 关闭时排空响应体用的缓冲区大小（排空同样受绝对 deadline 约束）。 */
    private const val DRAIN_BUFFER_BYTES = 8192

    /** deadline 为 null → [UNBOUNDED_REMAINING_MS]；已过期 → 0。 */
    fun remainingMsOrUnbounded(deadline: Instant?): Long =
        if (deadline == null) UNBOUNDED_REMAINING_MS
        else Duration.between(Instant.now(), deadline).toMillis().coerceAtLeast(0L)

    /** 实际生效的超时：不大于客户端既有配置，也不小于 [MIN_TIMEOUT_MS]。 */
    fun effectiveTimeoutMs(configuredMs: Long, remainingMs: Long): Int =
        minOf(configuredMs, remainingMs).coerceAtLeast(MIN_TIMEOUT_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * 剩余预算不紧于任何一项配置时返回**同一个** client 实例（零分配、零行为变化）；
     * 否则返回一个一次性的有界 client。
     *
     * 有界 client 的连接/读取超时都不超过剩余预算，并且它包装了响应体流：**每一次分片读取前后**都对照
     * 绝对 [deadline]（见 [DeadlineBoundedInputStream]）。单次读超时只能约束「一次阻塞读取」，
     * 服务端只要在每次读超时前吐一点数据就能让转换器（`getForObject(..., Xxx::class.java)`）永远读不完；
     * 包装流把这条细水长流也截断在总时限上。预算被压缩时不再挂 [RetryingClientHttpRequestInterceptor]：
     * 这时候连一次尝试的预算都不够，再叠内层重试就会越过总时限（外层 `FetchRetry` 也会逐次重判 deadline）。
     */
    fun bounded(base: RestTemplate, connectCapMs: Long, readCapMs: Long, deadline: Instant?): RestTemplate {
        // R-1（V-4）：只有「没有共享时限」的调用方才拿原 client。**任何非空 deadline 都必须包住响应体** ——
        // 早先的「剩余预算不紧于配置就返回原 client」会让正常的 90 秒预算绕过 body 包装，
        // 于是 Europe PMC（5s/30s 上限）那种「deadline 比 socket 上限宽」的正常情形下，
        // 细水长流的响应体仍可越过绝对 deadline。
        if (deadline == null) return base
        val remainingMs = remainingMsOrUnbounded(deadline)
        val tightened = remainingMs < connectCapMs || remainingMs < readCapMs
        val factory = DeadlineBoundedRequestFactory(
            connectTimeoutMs = effectiveTimeoutMs(connectCapMs, remainingMs),
            readTimeoutMs = effectiveTimeoutMs(readCapMs, remainingMs),
            deadline = deadline
        )
        val bounded = RestTemplate(factory)
        bounded.messageConverters.clear()
        bounded.messageConverters.addAll(base.messageConverters)
        bounded.errorHandler = base.errorHandler
        // 预算宽于配置时超时仍是原配置值，重试语义也照旧；只有预算真的被压缩时才不叠内层重试。
        bounded.interceptors.addAll(
            base.interceptors.filterNot { tightened && it is RetryingClientHttpRequestInterceptor }
        )
        return bounded
    }

    override fun <T : Any> getForObject(
        base: RestTemplate,
        url: String,
        responseType: Class<T>,
        connectCapMs: Long,
        readCapMs: Long,
        deadline: Instant?
    ): T? {
        requirePositiveBudget(deadline)
        return bounded(base, connectCapMs, readCapMs, deadline).getForObject(url, responseType)
    }

    override fun <T> execute(
        base: RestTemplate,
        uri: URI,
        connectCapMs: Long,
        readCapMs: Long,
        deadline: Instant?,
        responseExtractor: ResponseExtractor<T>
    ): T? {
        requirePositiveBudget(deadline)
        return bounded(base, connectCapMs, readCapMs, deadline)
            .execute(uri, HttpMethod.GET, null, responseExtractor)
    }

    /** R-1（V-4）：把任意响应体流包成「绝对 [deadline] 内可读」的流（有界 client 内部使用，测试直接验证它）。 */
    fun deadlineBoundedStream(delegate: java.io.InputStream, deadline: Instant?): java.io.InputStream =
        DeadlineBoundedInputStream(delegate, deadline)

    /** R-1（V-4）：dispatch 前的最后一次判定，0 或负预算一律不发请求。 */
    private fun requirePositiveBudget(deadline: Instant?) {
        if (remainingMsOrUnbounded(deadline) <= 0L) throw NoRemainingBudgetException()
    }

    /** 只把「响应体流」包上绝对 deadline，其余客户端行为（超时、转换器、错误处理）保持不变。 */
    private class DeadlineBoundedRequestFactory(
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        private val deadline: Instant?
    ) : SimpleClientHttpRequestFactory() {

        init {
            setConnectTimeout(connectTimeoutMs)
            setReadTimeout(readTimeoutMs)
        }

        /** I-1：重定向必须**逐跳**检查，因此关闭 JDK 客户端的自动跟随（GET 默认是跟随），由本工厂手工跟随。 */
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            super.prepareConnection(connection, httpMethod)
            connection.instanceFollowRedirects = false
        }

        /** 一次「原始」请求（不进入重定向循环），用于跟随下一跳。 */
        private fun rawRequest(uri: URI, httpMethod: HttpMethod): ClientHttpRequest =
            super.createRequest(uri, httpMethod)

        override fun createRequest(uri: URI, httpMethod: HttpMethod): ClientHttpRequest {
            val delegate = super.createRequest(uri, httpMethod)
            return object : ClientHttpRequest by delegate {
                /**
                 * I-1：公开全文地址的重定向逐跳检查 —— 每一跳的 Location 都必须是公开 http(s) 地址，
                 * 指向 OpenAlex Content/API 计量目的地的跳转一律拒绝（绝不为了拿全文而消耗账号额度）。
                 * 只校验初始 URL 是不够的：计量目标常常正是通过 302 才出现的。
                 */
                override fun execute(): ClientHttpResponse {
                    var current = delegate
                    var hops = 0
                    while (true) {
                        val response = current.execute()
                        val location = response.headers[HttpHeaders.LOCATION]?.firstOrNull()
                        if (location.isNullOrBlank()) return boundedBody(response)
                        val target = current.uri.resolve(location)
                        if (hops++ >= MAX_REDIRECTS) {
                            drainAndClose(response)
                            throw TooManyRedirectsException(target.toString())
                        }
                        if (OpenAlexMeteredDestinations.isMetered(target.toString())) {
                            drainAndClose(response)
                            throw MeteredRedirectException(target.toString())
                        }
                        drainAndClose(response)
                        current = rawRequest(target, HttpMethod.GET)
                    }
                }
            }
        }

        private fun boundedBody(response: ClientHttpResponse): ClientHttpResponse =
            object : ClientHttpResponse by response {
                override fun getBody(): java.io.InputStream =
                    DeadlineBoundedInputStream(response.body, deadline)

                /**
                 * R-1（V-4）：Spring 的 `SimpleClientHttpResponse.close()` 会用**无界的原流**把剩余响应体
                 * 排空，好把连接还给连接池 —— 遇到细水长流的服务端，这一步同样等于无限等待（调用方已经
                 * 拿到结果也回不去）。这里改成同样受绝对 [deadline] 约束的排空：能在时限内排空就照旧复用
                 * 连接，排不空就直接关掉原流、放弃本次复用。
                 */
                override fun close() {
                    drainAndClose(response)
                }
            }

        /** 中间跳（3xx）的响应体同样按绝对 deadline 排空，绝不在这里无限等待。 */
        private fun drainAndClose(response: ClientHttpResponse) {
            try {
                val bounded = DeadlineBoundedInputStream(response.body, deadline)
                val sink = ByteArray(DRAIN_BUFFER_BYTES)
                while (bounded.read(sink) != -1) {
                    // 排空剩余响应体（丢弃），只是为了让连接可复用
                }
                response.close()
            } catch (e: Exception) {
                runCatching { response.body.close() }
            }
        }
    }

    /** R-1（V-4）：每次读取前后对照绝对 deadline —— 细水长流也读不过总时限。 */
    private class DeadlineBoundedInputStream(
        private val delegate: java.io.InputStream,
        private val deadline: Instant?
    ) : java.io.InputStream() {

        override fun read(): Int {
            checkDeadline()
            val value = delegate.read()
            checkDeadline()
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            checkDeadline()
            val count = delegate.read(b, off, len)
            checkDeadline()
            return count
        }

        override fun available(): Int = delegate.available()

        override fun close() = delegate.close()

        private fun checkDeadline() {
            if (deadline != null && !Instant.now().isBefore(deadline)) {
                throw FulltextBodyDeadlineExceededException()
            }
        }
    }
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
