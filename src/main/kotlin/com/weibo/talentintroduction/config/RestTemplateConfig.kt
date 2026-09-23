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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * I-6（c2）：**仅在队列提取作用域内生效**的全文请求网关。
 *
 * 背景：旧发现流程在整页 join 前不落地任何工作，整页全文下载会把窗口时间吃光；新队列把「取数」与
 * 「处理」解耦后，同一时刻可能有多个提取线程指向同一目标站点。I-6 要求「owner 协调全局最多 8 个提取
 * 任务、**同一目标域最多 2 个 HTTP 请求（含重定向的实际落点域名）**」，并拒绝把 OpenAlex 计量
 * Content 目标当作公开 PDF 下载。
 *
 * 设计约束：
 * - 作用域由**提取线程自己**进入（[inQueueExtractionScope]，finally 退出），作用域是 ThreadLocal；
 * - 没有作用域的旧调用（Europe PMC / PMC OA / Unpaywall / PDF 的既有调用点）行为**逐字不变**：
 *   [acquireHop] 返回 null，既不限流也不检查 90 秒预算，也不触碰任何共享计数；
 * - 域名许可按「每一次实际 HTTP 跳」领取（初始地址与每一个重定向落点各算一跳），
 *   响应流关闭/读尽后释放，因此并发上限约束的是**真正在飞的请求**；
 * - 拿不到许可时置 [HOST_BUSY] 标志**并**抛出 [HostBusyException]：即使来源适配器把异常吞成空结果，
 *   外层仍能依据标志把 job 延期（既不记永久失败，也不消耗 attempts）；
 * - 每一跳都重新检查单篇 90 秒总时限（[PAPER_DEADLINE_MS]，与
 *   `OpenAlexDataSource.FULLTEXT_PER_PAPER_DEADLINE_MS` 同值），到点即 `NoRemainingBudgetException`；
 * - 跨 origin 的重定向必须剥离认证头（凭证只允许发给初始 origin）。
 *
 * 全局「最多 8 个提取任务」由 `pipelineFetchExecutor` 的线程数保证（见 `DiscoveryExecutorConfig`），
 * 本网关只负责**每目标域最多 2 个**这一层。
 */
object FulltextRequestGate {

    /** I-6：单篇论文全文（含全部回退地址与重定向）的总时限。 */
    const val PAPER_DEADLINE_MS: Long = 90_000L

    /** I-6：作用域内的「域名许可暂不可得」标志名（外层据此把 job 延期而不是记失败）。 */
    const val HOST_BUSY: String = "HOST_BUSY"

    /** I-6：域名许可不可得 —— 立即终止本次提取，外层按 [HOST_BUSY] 延期该 job。 */
    class HostBusyException(host: String) :
        IllegalStateException("$HOST_BUSY: no fulltext host permit available for $host")

    /** I-6：初始地址或某一跳落在 OpenAlex 计量目的地 —— 绝不把它当公开全文下载（会消耗账号额度）。 */
    class MeteredDestinationException(target: String) :
        IllegalStateException("refusing to fetch a metered OpenAlex destination as public fulltext: $target")

    /** I-6：一次「跳」的域名许可；[close] 幂等，响应流用尽或关闭时释放。 */
    class HopPermit internal constructor(private val host: String?) : AutoCloseable {
        private val released = AtomicBoolean(false)

        override fun close() {
            val target = host ?: return
            if (released.compareAndSet(false, true)) decrement(target)
        }
    }

    /** I-6：队列提取作用域（ThreadLocal；每域并发上限与单篇总时限）。 */
    class Scope internal constructor(val perHostLimit: Int, val deadline: Instant) {
        internal val hostBusy = AtomicBoolean(false)
    }

    private val scope = ThreadLocal<Scope?>()
    private val inFlight = ConcurrentHashMap<String, AtomicInteger>()

    /** 当前线程的作用域；null = 旧调用，网关完全惰性。 */
    fun activeScope(): Scope? = scope.get()

    /** I-6：进入队列提取作用域；嵌套调用保留外层作用域（内层退出不会清掉外层标志）。 */
    fun <T> inQueueExtractionScope(perHostLimit: Int, deadline: Instant, block: () -> T): T {
        val previous = scope.get()
        scope.set(Scope(perHostLimit.coerceAtLeast(1), deadline))
        try {
            return block()
        } finally {
            if (previous == null) scope.remove() else scope.set(previous)
        }
    }

    /** I-6：作用域内是否出现过 [HOST_BUSY] —— 适配器吞掉异常时外层唯一的判据。 */
    fun hostBusyInScope(): Boolean = scope.get()?.hostBusy?.get() ?: false

    /**
     * I-6：领取某一跳的域名许可。无作用域返回 null（旧调用不受限）；
     * 有作用域时先检查单篇剩余时限，再原子领取域名许可，失败即置 [HOST_BUSY] 并抛出。
     */
    fun acquireHop(uri: URI): HopPermit? {
        val current = scope.get() ?: return null
        checkRemainingBudget()
        val host = uri.host?.lowercase() ?: return HopPermit(null)
        if (!tryIncrement(host, current.perHostLimit)) {
            current.hostBusy.set(true)
            throw HostBusyException(host)
        }
        return HopPermit(host)
    }

    /** I-6：每一跳都重新检查单篇 90 秒总时限；到点不再发下一跳。 */
    fun checkRemainingBudget() {
        val current = scope.get() ?: return
        if (!Instant.now().isBefore(current.deadline)) throw BoundedFulltextHttp.NoRemainingBudgetException()
    }

    /** I-6：单篇剩余预算（毫秒）；无作用域返回 null。 */
    fun remainingMs(): Long? = scope.get()?.let { Duration.between(Instant.now(), it.deadline).toMillis() }

    /** 诊断/测试用：某域名当前在飞的许可数。 */
    fun inFlightForHost(host: String): Int = inFlight[host.lowercase()]?.get() ?: 0

    /** I-6：跨 origin 的跳必须剥离认证头（凭证只允许出现在初始 origin）。 */
    fun stripCrossOriginCredential(request: HttpRequest, initialOrigin: String) {
        if (originKey(request.uri) != initialOrigin) {
            request.headers.remove(HttpHeaders.AUTHORIZATION)
        }
    }

    /** I-6：origin 的稳定表示（scheme://host:port），用于判断是否跨 origin。 */
    fun originKey(uri: URI): String {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "https" -> 443
            scheme == "http" -> 80
            else -> -1
        }
        return "$scheme://$host:$port"
    }

    private fun tryIncrement(host: String, limit: Int): Boolean {
        val counter = inFlight.computeIfAbsent(host) { AtomicInteger(0) }
        while (true) {
            val current = counter.get()
            if (current >= limit) return false
            if (counter.compareAndSet(current, current + 1)) return true
        }
    }

    private fun decrement(host: String) {
        val counter = inFlight[host] ?: return
        while (true) {
            val current = counter.get()
            if (current <= 0) return
            if (counter.compareAndSet(current, current - 1)) return
        }
    }
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
            connectCapMs = connectCapMs,
            readCapMs = readCapMs,
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
        private val connectCapMs: Long,
        private val readCapMs: Long,
        private val deadline: Instant?
    ) : SimpleClientHttpRequestFactory() {

        init {
            refreshTimeouts()
        }

        /** I-6（c2）：每一跳都按**当时的**剩余预算重新收紧超时，绝不把初始预算一路带到后续跳。 */
        private fun refreshTimeouts() {
            val remaining = BoundedFulltextHttp.remainingMsOrUnbounded(deadline)
            setConnectTimeout(BoundedFulltextHttp.effectiveTimeoutMs(connectCapMs, remaining))
            setReadTimeout(BoundedFulltextHttp.effectiveTimeoutMs(readCapMs, remaining))
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
                 * I-1/I-6（c2）：公开全文地址的重定向**逐跳**检查 —— 每一跳的 Location 都必须是公开
                 * http(s) 地址，指向 OpenAlex Content/API 计量目的地的跳转一律拒绝（绝不为了拿全文而
                 * 消耗账号额度）；只校验初始 URL 是不够的，计量目标常常正是通过 302 才出现的。
                 *
                 * 队列提取作用域内还要：逐跳重取目标域名许可（每域最多 [FulltextRequestGate] 条的
                 * 在飞请求）、逐跳重新检查单篇总时限、跨 origin 剥离认证头。没有作用域的旧调用
                 * 完全不走这一段，行为逐字不变。
                 */
                override fun execute(): ClientHttpResponse {
                    var current = delegate
                    var hops = 0
                    val initialOrigin = FulltextRequestGate.originKey(delegate.uri)
                    while (true) {
                        // I-6（c2）：只有队列提取作用域内才逐跳重新收紧超时；没有作用域的旧调用
                        // 沿用构造时（即 bounded() 时）算出的超时，行为逐字不变。
                        if (FulltextRequestGate.activeScope() != null) {
                            refreshTimeouts()
                            FulltextRequestGate.checkRemainingBudget()
                            if (OpenAlexMeteredDestinations.isMetered(current.uri.toString())) {
                                throw FulltextRequestGate.MeteredDestinationException(current.uri.toString())
                            }
                            FulltextRequestGate.stripCrossOriginCredential(current, initialOrigin)
                        }
                        val permit = FulltextRequestGate.acquireHop(current.uri)
                        val response = try {
                            current.execute()
                        } catch (e: Exception) {
                            permit?.close()
                            throw e
                        }
                        val location = response.headers[HttpHeaders.LOCATION]?.firstOrNull()
                        if (location.isNullOrBlank()) return boundedBody(response, permit)
                        val target = current.uri.resolve(location)
                        if (hops++ >= MAX_REDIRECTS) {
                            drainAndClose(response)
                            permit?.close()
                            throw TooManyRedirectsException(target.toString())
                        }
                        if (OpenAlexMeteredDestinations.isMetered(target.toString())) {
                            drainAndClose(response)
                            permit?.close()
                            throw MeteredRedirectException(target.toString())
                        }
                        drainAndClose(response)
                        permit?.close()
                        current = rawRequest(target, HttpMethod.GET)
                    }
                }
            }
        }

        private fun boundedBody(
            response: ClientHttpResponse,
            permit: FulltextRequestGate.HopPermit?
        ): ClientHttpResponse =
            object : ClientHttpResponse by response {
                override fun getBody(): java.io.InputStream =
                    PermitReleasingInputStream(DeadlineBoundedInputStream(response.body, deadline), permit)

                /**
                 * R-1（V-4）：Spring 的 `SimpleClientHttpResponse.close()` 会用**无界的原流**把剩余响应体
                 * 排空，好把连接还给连接池 —— 遇到细水长流的服务端，这一步同样等于无限等待（调用方已经
                 * 拿到结果也回不去）。这里改成同样受绝对 [deadline] 约束的排空：能在时限内排空就照旧复用
                 * 连接，排不空就直接关掉原流、放弃本次复用。
                 *
                 * I-6（c2）：关闭即释放该跳的域名许可（幂等），因此「拿到结果就 close」不会泄漏并发额度。
                 */
                override fun close() {
                    try {
                        drainAndClose(response)
                    } finally {
                        permit?.close()
                    }
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

    /**
     * I-6（c2）：响应体读尽或关闭即释放该跳的域名许可 —— 即使调用方只读到 EOF 也不 close，
     * 并发额度也不会泄漏；[FulltextRequestGate.HopPermit.close] 本身幂等。
     */
    private class PermitReleasingInputStream(
        private val delegate: java.io.InputStream,
        private val permit: FulltextRequestGate.HopPermit?
    ) : java.io.InputStream() {

        override fun read(): Int = releaseOnEof(delegate.read())

        override fun read(b: ByteArray, off: Int, len: Int): Int = releaseOnEof(delegate.read(b, off, len))

        override fun available(): Int = delegate.available()

        override fun close() {
            try {
                delegate.close()
            } finally {
                permit?.close()
            }
        }

        private fun releaseOnEof(value: Int): Int {
            if (value == -1) permit?.close()
            return value
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
