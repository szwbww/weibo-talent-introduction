package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.UnpaywallProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate
import com.weibo.talentintroduction.config.SlowHttpServer

class UnpaywallClientTest {
    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val mapper = ObjectMapper()

    @Test
    fun `findPdfUrl returns null when email not configured`() {
        val properties = UnpaywallProperties(email = "")
        val client = UnpaywallClient(restTemplate, properties)
        assertNull(client.findPdfUrl("10.1234/test"))
    }

    @Test
    fun `findPdfUrl returns best_oa_location url_for_pdf`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "http://example.com/paper.pdf")
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertEquals("http://example.com/paper.pdf", result)
    }

    @Test
    fun `findPdfUrl falls back to oa_locations`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to null,
            "oa_locations" to listOf(
                mapOf("url_for_pdf" to "http://example.com/fallback.pdf")
            )
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertEquals("http://example.com/fallback.pdf", result)
    }

    @Test
    fun `findPdfUrl returns null on API error`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        Mockito.doThrow(RuntimeException("API error"))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val result = client.findPdfUrl("10.1234/test")
        assertNull(result)
    }

    @Test
    fun `findPdfUrls returns every open location deduplicated with best first (I-1)`() {
        // c10（I-1）：首选地址失效后回退链要能接着尝试其他开放位置，因此客户端要给出全部去重地址。
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "https://repo.example/best.pdf"),
            "oa_locations" to listOf(
                mapOf("url_for_pdf" to "https://repo.example/best.pdf"),
                mapOf("url_for_pdf" to "https://repo.example/second.pdf"),
                mapOf("url_for_pdf" to "https://repo.example/third.pdf")
            )
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        assertEquals(
            listOf(
                "https://repo.example/best.pdf",
                "https://repo.example/second.pdf",
                "https://repo.example/third.pdf"
            ),
            client.findPdfUrls("10.1234/test")
        )
        assertEquals("https://repo.example/best.pdf", client.findPdfUrl("10.1234/test"))
    }

    @Test
    fun `findPdfUrls keeps only public http(s) links (I-1)`() {
        // c10（I-1）：非公开协议与空值不成其为可下载的开放全文地址，绝不能下发给下载器。
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)

        val response = mapOf(
            "best_oa_location" to mapOf("url_for_pdf" to "ftp://repo.example/best.pdf"),
            "oa_locations" to listOf(
                mapOf("url_for_pdf" to "https://repo.example/ok.pdf"),
                mapOf("url_for_pdf" to "")
            )
        )
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        assertEquals(listOf("https://repo.example/ok.pdf"), client.findPdfUrls("10.1234/test"))
    }

    @Test
    fun `findPdfUrls skips delay and request once the shared deadline expired (R-4, V-4)`() {
        // V-4：Unpaywall 阶段过去不在共享总时限内 —— 时限已过时连礼貌延迟与查询都不发。
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 5_000)
        val client = UnpaywallClient(restTemplate, properties)
        var requested = false
        Mockito.doAnswer { _: org.mockito.invocation.InvocationOnMock ->
            requested = true
            mapper.readTree("{}")
        }.`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        val startedAt = System.nanoTime()
        val urls = client.findPdfUrls("10.1234/test", java.time.Instant.now().minusSeconds(1))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(emptyList<String>(), urls)
        assertFalse(requested, "过期的共享时限下不得发出 Unpaywall 查询")
        assertTrue(elapsedMs < 5_000, "过期时不得再睡礼貌延迟（实际 ${elapsedMs}ms）")
    }

    @Test
    fun `findPdfUrls with no shared deadline keeps the previous behaviour (R-4 compatibility)`() {
        val properties = UnpaywallProperties(email = "test@example.com", requestDelayMs = 0)
        val client = UnpaywallClient(restTemplate, properties)
        val response = mapOf("best_oa_location" to mapOf("url_for_pdf" to "https://repo.example/ok.pdf"))
        Mockito.doReturn(mapper.readTree(mapper.writeValueAsString(response)))
            .`when`(restTemplate).getForObject(Mockito.anyString(), Mockito.eq(com.fasterxml.jackson.databind.JsonNode::class.java))

        assertEquals(listOf("https://repo.example/ok.pdf"), client.findPdfUrls("10.1234/test", null))
        assertEquals(listOf("https://repo.example/ok.pdf"), client.findPdfUrls("10.1234/test"))
    }

@Test
    fun `a slow lookup is cut off by the shared budget and yields no candidates (R-1, V-4)`() {
        // V-4：Unpaywall 走的是没有配置超时的通用 client，此前查询可以无限等待；现在受单篇共享预算约束。
        SlowHttpServer(SlowHttpServer.Mode.ACCEPT_ONLY).use { server ->
            val properties = UnpaywallProperties(
                baseUrl = "http://127.0.0.1:${server.port}", email = "test@example.com", requestDelayMs = 0
            )
            val client = UnpaywallClient(RestTemplate(), properties)
            val startedAt = System.nanoTime()

            val urls = client.findPdfUrls("10.1234/test", java.time.Instant.now().plusMillis(400))

            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
            assertEquals(emptyList<String>(), urls)
            assertTrue(elapsedMs < 5_000, "剩余预算 400ms 内必须结束（实际 ${elapsedMs}ms）")
            assertEquals(1, server.acceptedCount, "只发一次查询，不留孤儿重试")
        }
    }
}
