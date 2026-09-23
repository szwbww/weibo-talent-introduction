package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.BoundedFulltextHttp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestTemplate
import java.net.ServerSocket
import java.net.URI
import java.time.Instant
import kotlin.concurrent.thread

class DiscoveryTrafficMeterTest {
    @Test
    fun `counts bytes drained after a partial fulltext read`() {
        ServerSocket(0).use { server ->
            val responder = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    val payload = "0123456789".toByteArray()
                    socket.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: 10\r\nContent-Type: application/pdf\r\nConnection: close\r\n\r\n".toByteArray()
                    )
                    socket.getOutputStream().write(payload)
                    socket.getOutputStream().flush()
                }
            }
            val client = RestTemplate().apply { interceptors.add(DiscoveryTrafficMeter.interceptor) }
            val session = DiscoveryTrafficMeter.newSession()
            val result = DiscoveryTrafficMeter.measure(session, "OPENALEX", DiscoveryTrafficMeter.Category.FULLTEXT) {
                BoundedFulltextHttp.execute(
                    client, URI.create("http://127.0.0.1:${server.localPort}/paper.pdf"),
                    1_000, 1_000, Instant.now().plusSeconds(5)
                ) { response -> String(response.body.readNBytes(2)) }
            }
            responder.join(2_000)
            assertEquals("01", result)
            assertEquals(10L, session.snapshot().totalBytes)
            assertEquals(8L, session.snapshot().discardedBytes)
        }
    }

    @Test
    fun `counts response bytes per run and category including HTTP failures`() {
        val client = RestTemplate().apply { interceptors.add(DiscoveryTrafficMeter.interceptor) }
        val server = MockRestServiceServer.bindTo(client).build()
        server.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_PLAIN).body("metadata"))
        server.expect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.TEXT_PLAIN).body("blocked"))
        val first = DiscoveryTrafficMeter.newSession()
        DiscoveryTrafficMeter.measure(first, "OPENALEX", DiscoveryTrafficMeter.Category.METADATA) {
            assertEquals("metadata", client.getForObject("https://api.openalex.org/works", String::class.java))
        }
        val second = DiscoveryTrafficMeter.newSession()
        DiscoveryTrafficMeter.measure(second, "CROSSREF", DiscoveryTrafficMeter.Category.FULLTEXT) {
            runCatching { client.getForObject("https://example.org/paper.pdf", String::class.java) }
        }
        server.verify()
        assertEquals(8L, first.snapshot().totalBytes)
        assertEquals(8L, first.snapshot().metadataBytes)
        assertEquals(8L, first.snapshot().bySource["OPENALEX"])
        assertEquals(7L, second.snapshot().fulltextBytes)
        assertEquals(7L, second.snapshot().byHost["example.org"])
        assertNull(DiscoveryTrafficMeter.currentSession())
    }
}
