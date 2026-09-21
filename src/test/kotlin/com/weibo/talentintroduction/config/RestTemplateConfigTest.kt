package com.weibo.talentintroduction.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.anything
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.http.client.ClientHttpResponse

class RestTemplateConfigTest {
    private val config = RestTemplateConfig()

    @Test
    fun `bounded client narrows connect and read timeouts to the remaining budget and never widens them (R-1, V-4)`() {
        val base = config.restTemplate()

        val bounded = BoundedFulltextHttp.bounded(
            base, connectCapMs = 5_000, readCapMs = 30_000, deadline = java.time.Instant.now().plusMillis(400)
        )

        assertNotSame(base, bounded, "剩余预算更紧时必须换用有界 client")
        val factory = bounded.requestFactory as SimpleClientHttpRequestFactory
        // 剩余预算是从绝对 deadline 现算的，毫秒取整可能少 1ms；这里断言「不超过预算且两项一致」。
        val connectTimeout = timeoutField(factory, "connectTimeout")
        assertEquals(connectTimeout, timeoutField(factory, "readTimeout"))
        assertTrue(connectTimeout in 300..400, "生效超时必须落在剩余预算内（实际 ${connectTimeout}ms）")
        // 只可能收紧：配置上限小于剩余预算时按配置值，0 也不退化成「无限等待」
        assertEquals(400, BoundedFulltextHttp.effectiveTimeoutMs(5_000, 400))
        assertEquals(5_000, BoundedFulltextHttp.effectiveTimeoutMs(5_000, 600_000))
        assertEquals(1, BoundedFulltextHttp.effectiveTimeoutMs(5_000, 0))
    }

    @Test
    fun `an unbounded budget returns the original client untouched (R-1 compatibility)`() {
        val base = config.restTemplate()

        assertSame(base, BoundedFulltextHttp.bounded(base, 5_000, 30_000, null))
        assertSame(base, BoundedFulltextHttp.bounded(base, Long.MAX_VALUE, Long.MAX_VALUE, null))
        assertSame(
            base,
            BoundedFulltextHttp.bounded(base, 5_000, 30_000, java.time.Instant.now().plusSeconds(3_600))
        )
        assertTrue(BoundedFulltextHttp.remainingMsOrUnbounded(null) == BoundedFulltextHttp.UNBOUNDED_REMAINING_MS)
    }

    @Test
    fun `the bounded client keeps converters, the error handler and non-retry interceptors (R-1 compatibility)`() {
        val base = config.openAlexRestTemplate(OpenAlexProperties(apiKey = "k"), RestTemplateBuilder())

        val bounded = BoundedFulltextHttp.bounded(
            base, connectCapMs = 5_000, readCapMs = 30_000, deadline = java.time.Instant.now().plusMillis(250)
        )

        assertEquals(base.messageConverters.size, bounded.messageConverters.size)
        assertSame(base.errorHandler, bounded.errorHandler)
        assertTrue(bounded.interceptors.any { it is OpenAlexAuthInterceptor }, "认证拦截器必须保留")
        assertFalse(
            bounded.interceptors.any { it is RetryingClientHttpRequestInterceptor },
            "预算被压缩时不得再叠内层重试，否则一次尝试的重试会把调用方拖过总时限"
        )
    }

    @Test
    fun `a response header that never arrives is aborted by the remaining budget (R-1, V-4)`() {
        SlowHttpServer(SlowHttpServer.Mode.ACCEPT_ONLY).use { server ->
            val base = config.restTemplate()
            val startedAt = System.nanoTime()

            assertThrows(ResourceAccessException::class.java) {
                BoundedFulltextHttp.getForObject(
                    base, "http://127.0.0.1:${server.port}/slow", ByteArray::class.java,
                    connectCapMs = 5_000, readCapMs = 30_000, deadline = java.time.Instant.now().plusMillis(400)
                )
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue(elapsedMs < 5_000, "剩余预算 400ms 内必须结束（实际 ${elapsedMs}ms）")
            assertEquals(1, server.acceptedCount, "有界 client 只发一次请求，不留孤儿重试")
        }
    }

    private fun timeoutField(factory: SimpleClientHttpRequestFactory, name: String): Int {
        val field = SimpleClientHttpRequestFactory::class.java.getDeclaredField(name)
        field.isAccessible = true
        return (field.get(factory) as Number).toInt()
    }

    @Test
    fun `shared restTemplate has no interceptors`() {
        assertTrue(config.restTemplate().interceptors.isEmpty())
    }

    @Test
    fun `europePmcRestTemplate includes retry interceptor`() {
        val restTemplate = config.europePmcRestTemplate(EuropePmcProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is RetryingClientHttpRequestInterceptor)
    }

    @Test
    fun `pdfDownloadRestTemplate includes retry interceptor`() {
        val restTemplate = config.pdfDownloadRestTemplate(PdfExtractionProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is RetryingClientHttpRequestInterceptor)
    }

    @Test
    fun `openAlexRestTemplate carries only the authentication interceptor`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(), RestTemplateBuilder())

        assertEquals(1, restTemplate.interceptors.size)
        assertTrue(restTemplate.interceptors[0] is OpenAlexAuthInterceptor)
    }

    @Test
    fun `openAlexRestTemplate sends bearer to the configured https origin (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(apiKey = "test-key"), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        val urls = listOf(
            "https://api.openalex.org/works?per_page=1",
            "https://api.openalex.org:443/authors/A1"
        )
        for (url in urls) {
            server.expect(anything())
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andRespond(withStatus(HttpStatus.OK))
        }
        for (url in urls) {
            restTemplate.getForEntity(url, String::class.java)
        }
        server.verify()
    }

    @Test
    fun `openAlexRestTemplate keeps the key off every other origin (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(apiKey = "test-key"), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        val urls = listOf(
            "https://api.openalex.org:8443/works",
            "https://api.openalex.org.evil.com/works",
            "https://www.example.org/paper.pdf",
            "http://api.openalex.org/works"
        )
        for (url in urls) {
            server.expect(anything())
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withStatus(HttpStatus.OK))
        }
        for (url in urls) {
            restTemplate.getForEntity(url, String::class.java)
        }
        server.verify()
    }

    @Test
    fun `openAlexRestTemplate strips a credential from an untrusted origin (I-1, V-1)`() {
        // A cross-origin redirect target or an external fulltext host must never carry the key.
        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(
            ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.set(HttpHeaders.AUTHORIZATION, "Bearer test-key")
                execution.execute(request, body)
            }
        )
        restTemplate.interceptors.add(OpenAlexAuthInterceptor("test-key", "https://api.openalex.org"))
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(anything())
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.OK))
        restTemplate.getForEntity("https://www.example.org/paper.pdf", String::class.java)
        server.verify()
    }

    @Test
    fun `anonymous configuration never sends a credential (I-1, V-1)`() {
        val restTemplate = config.openAlexRestTemplate(OpenAlexProperties(), RestTemplateBuilder())
        val server = MockRestServiceServer.bindTo(restTemplate).build()

        server.expect(anything())
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.OK))
        restTemplate.getForEntity("https://api.openalex.org/works", String::class.java)
        server.verify()
    }

    @Test
    fun `configuration never prints a live key (I-1)`() {
        val properties = OpenAlexProperties(apiKey = "super-secret-key")

        assertTrue(properties.toString().contains("apiKey=\"***\""))
        assertTrue(!properties.toString().contains("super-secret-key"))
    }

    @Test
    fun `openAlexRequestPolicy bean uses the configured budget and key (I-2)`() {
        assertEquals(1_000, config.openAlexRequestPolicy(OpenAlexProperties()).remainingCredits())
        assertEquals(10_000, config.openAlexRequestPolicy(OpenAlexProperties(apiKey = "k")).remainingCredits())
    }

@Test
    fun `a trickling response body is cut off at the absolute deadline, not per read (R-1, V-4)`() {
        // V-4 的残余形态：单次 socket 读超时挡不住「每次都在超时前吐一点」的服务端。
        SlowHttpServer(SlowHttpServer.Mode.TRICKLE_BODY, trickleIntervalMs = 20).use { server ->
            val base = config.restTemplate()
            val startedAt = System.nanoTime()

            val thrown = assertThrows(Exception::class.java) {
                BoundedFulltextHttp.getForObject(
                    base, "http://127.0.0.1:${server.port}/trickle", ByteArray::class.java,
                    connectCapMs = 5_000, readCapMs = 30_000, deadline = java.time.Instant.now().plusMillis(500)
                )
            }

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertTrue(
                generateSequence(thrown as Throwable?) { it.cause }
                    .any { it is BoundedFulltextHttp.FulltextBodyDeadlineExceededException },
                "细水长流的响应体必须按绝对 deadline 截断（实际异常：${thrown}）"
            )
            assertTrue(elapsedMs < 5_000, "500ms 预算内必须结束（实际 ${elapsedMs}ms）")
            assertEquals(1, server.acceptedCount)
        }
    }

    @Test
    fun `no request is dispatched when no positive budget remains (R-1, V-4)`() {
        // preflight 判定与真正 dispatch 之间的原子补位：0 预算绝不发出请求（本地服务端 0 次连接）。
        SlowHttpServer(SlowHttpServer.Mode.ACCEPT_ONLY).use { server ->
            val base = config.restTemplate()

            assertThrows(BoundedFulltextHttp.NoRemainingBudgetException::class.java) {
                BoundedFulltextHttp.getForObject(
                    base, "http://127.0.0.1:${server.port}/never", ByteArray::class.java,
                    connectCapMs = 5_000, readCapMs = 30_000, deadline = java.time.Instant.now()
                )
            }

            assertEquals(0, server.acceptedCount, "预算为 0 时不得 dispatch")
        }
    }

    @Test
    fun `the bounded body stream stops a chunk stream at the deadline (R-1, V-4)`() {
        val deadline = java.time.Instant.now().plusMillis(600)
        val chunks = AtomicInteger(0)
        val delegate = object : java.io.InputStream() {
            override fun read(): Int = throw UnsupportedOperationException()
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                chunks.incrementAndGet()
                Thread.sleep(20)
                java.util.Arrays.fill(b, off, off + 32, 'x'.code.toByte())
                return 32
            }
        }
        val body = BoundedFulltextHttp.deadlineBoundedStream(delegate, deadline)
        val startedAt = System.nanoTime()

        assertThrows(BoundedFulltextHttp.FulltextBodyDeadlineExceededException::class.java) {
            while (true) {
                if (body.read(ByteArray(32)) == -1) break
            }
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(chunks.get() >= 3, "应该在 deadline 之前读到若干分片（实际 ${chunks.get()}）")
        assertTrue(elapsedMs < 5_000, "600ms 预算内必须结束（实际 ${elapsedMs}ms）")
    }
}

/**
 * R-1（V-4）：受控慢响应服务器 —— 证明**已经在飞**的连接、响应头与响应体读取都受剩余预算约束。
 *
 * 端口由系统分配；[acceptedCount] 用来断言「预算耗尽后不再发出后续请求 / 不留孤儿重试」。
 * 线程都是 daemon，关闭后不影响 JVM 退出。
 */
internal class SlowHttpServer(
    private val mode: Mode = Mode.ACCEPT_ONLY,
    private val bodyPrefix: ByteArray = ByteArray(0),
    private val trickleIntervalMs: Long = 30
) : AutoCloseable {

    enum class Mode {
        /** 接受连接但一个字节都不回：连接成功、响应头永不返回。 */
        ACCEPT_ONLY,

        /** 回 200 与响应头（声明较大 Content-Length）后停住：响应体读取中途挂起。 */
        HEADERS_THEN_STALL,

        /**
         * 回头部后每 [trickleIntervalMs] 毫秒吐一小段数据：每次 socket 读都能及时返回（永远不触发
         * 单次读超时），但整个响应体可以无限期地「细水长流」下去。
         */
        TRICKLE_BODY
    }

    private val server = ServerSocket(0)
    private val closed = AtomicBoolean(false)
    private val accepted = AtomicInteger(0)

    val port: Int get() = server.localPort
    val acceptedCount: Int get() = accepted.get()

    init {
        val acceptor = Thread {
            while (!closed.get()) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    return@Thread
                }
                accepted.incrementAndGet()
                val worker = Thread { serve(socket) }
                worker.isDaemon = true
                worker.start()
            }
        }
        acceptor.isDaemon = true
        acceptor.start()
    }

    private fun serve(socket: Socket) {
        try {
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) line = reader.readLine()
                if (mode == Mode.TRICKLE_BODY) {
                    val out = s.getOutputStream()
                    out.write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\nContent-Length: 1048576\r\n\r\n"
                            .toByteArray(Charsets.ISO_8859_1)
                    )
                    out.flush()
                    val payload = ByteArray(32) { 'x'.code.toByte() }
                    while (!closed.get()) {
                        out.write(payload)
                        out.flush()
                        Thread.sleep(trickleIntervalMs)
                    }
                }
                if (mode == Mode.HEADERS_THEN_STALL) {
                    val out = s.getOutputStream()
                    out.write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/pdf\r\nContent-Length: 1048576\r\n\r\n"
                            .toByteArray(Charsets.ISO_8859_1)
                    )
                    if (bodyPrefix.isNotEmpty()) out.write(bodyPrefix)
                    out.flush()
                }
                while (!closed.get()) Thread.sleep(20)
            }
        } catch (e: Exception) {
            // 客户端超时/断开：服务端线程结束即可
        }
    }

    override fun close() {
        closed.set(true)
        try {
            server.close()
        } catch (e: Exception) {
            // 已经关闭
        }
    }

}
