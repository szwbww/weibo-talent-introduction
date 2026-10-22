package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ExpertSearchCursorTest {
    private val mapper = ObjectMapper()
    private val shards = mapOf("total" to 1, "successful" to 1, "failed" to 0)
    private fun json(value: Any): JsonNode = mapper.valueToTree(value)
    private fun hit(id: Int) = mapOf("_id" to "doc-$id", "_source" to mapOf("orcidId" to "same"),
        "sort" to listOf(1720000000000L, id.toLong()))
    private fun response(hits: List<Any>, pit: String = "rotated") = json(mapOf(
        "_shards" to shards, "timed_out" to false, "pit_id" to pit, "hits" to mapOf("hits" to hits)))

    private inner class Fixture(val levels: List<ExpertIndexLevel> = listOf(ExpertIndexLevel.CANDIDATE), size: Int = 2) {
        val requests = mutableListOf<Triple<String, HttpMethod, Map<String, Any>?>>()
        val scheduler = Mockito.mock(ScheduledExecutorService::class.java)
        val future = Mockito.mock(ScheduledFuture::class.java)
        val renewals = mutableListOf<Runnable>()
        val responses = java.util.ArrayDeque<JsonNode>()
        var searchFailure: Exception? = null
        var beforeSearch: (() -> Unit)? = null
        var closed = 0
        init {
            Mockito.`when`(scheduler.scheduleAtFixedRate(any(Runnable::class.java), eq(60L), eq(60L), eq(TimeUnit.SECONDS)))
                .thenAnswer { invocation -> renewals.add(invocation.getArgument(0)); future }
        }
        val cursor = ExpertSearchCursor(levels, size, { it.name.lowercase() }, {
            mapOf("query" to mapOf("match_all" to emptyMap<String, Any>()),
                "sort" to listOf(mapOf("candidateValidatedAt" to "desc"), mapOf("_shard_doc" to "asc")))
        }, { path, method, body ->
            requests.add(Triple(path, method, body))
            when {
                path.contains("/_pit?") -> json(mapOf("id" to "pit-${requests.size}"))
                path == "/_pit" -> json(mapOf("succeeded" to true))
                else -> {
                    beforeSearch?.invoke()
                    searchFailure?.let { throw it }
                    if (body?.get("size") == 0) response(emptyList(), "renewed-${requests.size}")
                    else responses.removeFirst()
                }
            }
        }, { node -> ExpertProfile(node.path("_id").asText(), "same", null, null, null, null, null, null) },
            scheduler, { closed++ })
        fun searches() = requests.filter { it.first == "/_search" && it.third?.get("size") != 0 }
        fun deletes() = requests.filter { it.second == HttpMethod.DELETE }
    }

    @Test
    fun `raw sort tail advances even when caller filters every profile and only empty raw response exhausts`() {
        val f = Fixture()
        f.responses.add(response(listOf(hit(1), hit(2))))
        f.responses.add(response(listOf(hit(3))))
        f.responses.add(response(emptyList()))
        assertTrue(f.requests.isEmpty(), "PIT is lazy")
        val first = f.cursor.nextPage()
        assertTrue(first.profiles.filter { false }.isEmpty())
        assertFalse(first.exhausted)
        assertEquals("doc-3", f.cursor.nextPage().profiles.single().esDocId)
        assertEquals(json(listOf(1720000000000L, 2L)), f.searches()[1].third!!["search_after"])
        assertTrue((f.searches()[1].third!!["search_after"] as JsonNode)[1].isIntegralNumber)
        assertTrue(f.cursor.nextPage().exhausted)
        f.cursor.close()
        assertEquals(1, f.closed)
        assertEquals(1, f.deletes().size)
        assertEquals("rotated", f.deletes().single().third!!["id"])
        f.requests.filter { it.first == "/_search" }.forEach {
            assertEquals(false, it.third!!["track_total_hits"])
            assertFalse(it.third!!.containsKey("from"))
            assertEquals(mapOf("id" to (it.third!!["pit"] as Map<*, *>)["id"], "keep_alive" to "5m"), it.third!!["pit"])
        }
    }

    @Test
    fun `six minutes of renewals update PIT without consuming search after or prefetching`() {
        val f = Fixture()
        f.responses.add(response(listOf(hit(1))))
        f.responses.add(response(listOf(hit(2))))
        f.cursor.nextPage()
        repeat(6) { f.renewals.single().run() }
        assertEquals(1, f.searches().size)
        val renewalBodies = f.requests.filter { it.third?.get("size") == 0 }.map { it.third!! }
        assertEquals(6, renewalBodies.size)
        renewalBodies.forEach {
            assertEquals(mapOf("match_none" to emptyMap<String, Any>()), it["query"])
            assertEquals(false, it["track_total_hits"])
            assertFalse(it.containsKey("search_after"))
        }
        f.cursor.nextPage()
        assertEquals("renewed-8", (f.searches().last().third!!["pit"] as Map<*, *>)["id"])
        assertEquals(json(listOf(1720000000000L, 1L)), f.searches().last().third!!["search_after"])
        f.cursor.close()
        val count = f.requests.size
        f.renewals.single().run()
        f.cursor.close()
        assertEquals(count, f.requests.size)
        Mockito.verify(f.future).cancel(false)
        assertThrows(IllegalStateException::class.java) { f.cursor.nextPage() }
    }

    @Test
    fun `renewal failure stays visible on consumption and closes newest PIT`() {
        val f = Fixture()
        f.responses.add(response(listOf(hit(1))))
        f.cursor.nextPage()
        f.searchFailure = org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND)
        f.renewals.single().run()
        assertThrows(ExpertSearchUnavailableException::class.java) { f.cursor.nextPage() }
        assertEquals("rotated", f.deletes().single().third!!["id"])
        val count = f.requests.size
        f.renewals.single().run()
        f.cursor.close()
        assertEquals(count, f.requests.size)
        assertEquals(1, f.closed)
    }

    @Test
    fun `partial timeout malformed and nonadvancing pages fail closed and release PIT`() {
        val invalid = listOf(
            json(mapOf("_shards" to mapOf("total" to 2, "successful" to 1, "failed" to 1), "pit_id" to "partial")),
            json(mapOf("_shards" to shards, "timed_out" to true)),
            json(mapOf("_shards" to shards)),
            response(listOf(mapOf("_id" to "missing-sort", "_source" to emptyMap<String, Any>()))),
            response(listOf(hit(1), hit(1)))
        )
        for (body in invalid) {
            val f = Fixture()
            f.responses.add(body)
            assertThrows(ExpertSearchUnavailableException::class.java) { f.cursor.nextPage() }
            assertEquals(1, f.deletes().size)
            assertEquals(body.path("pit_id").asText("pit-1"), f.deletes().single().third!!["id"])
            f.cursor.close()
        }
        val f = Fixture()
        f.responses.add(response(listOf(hit(1))))
        f.responses.add(response(listOf(hit(1))))
        f.cursor.nextPage()
        assertThrows(ExpertSearchUnavailableException::class.java) { f.cursor.nextPage() }
        assertEquals(1, f.deletes().size)
    }

    @Test
    fun `layers close before opening next layer and independent cursors never share ids`() {
        val f = Fixture(listOf(ExpertIndexLevel.APPLICATION, ExpertIndexLevel.CANDIDATE))
        f.responses.add(response(listOf(hit(1)), "application-pit"))
        f.responses.add(response(emptyList(), "application-last"))
        f.responses.add(response(listOf(hit(2)), "candidate-pit"))
        f.cursor.nextPage()
        assertEquals(1, f.requests.count { it.first.contains("/_pit?") })
        f.cursor.nextPage()
        assertEquals("application-last", f.requests[3].third!!["id"])
        assertTrue(f.requests[4].first.startsWith("/candidate/_pit?"))
        assertFalse(f.searches().last().third!!.containsKey("search_after"))
        val other = Fixture()
        other.responses.add(response(listOf(hit(3)), "statistics-pit"))
        other.cursor.nextPage()
        other.cursor.close()
        f.cursor.close()
        assertEquals("statistics-pit", other.deletes().last().third!!["id"])
        assertEquals("candidate-pit", f.deletes().last().third!!["id"])
    }

    @Test
    fun `closing unused cursor makes no HTTP requests and bounds reject invalid sizes`() {
        val f = Fixture()
        f.cursor.close()
        f.cursor.close()
        assertTrue(f.requests.isEmpty())
        assertThrows(IllegalStateException::class.java) { f.cursor.nextPage() }
        assertThrows(IllegalArgumentException::class.java) { Fixture(size = 0) }
        assertThrows(IllegalArgumentException::class.java) { Fixture(size = 501) }
    }

    @Test
    fun `page renewal and close serialize on one lock and cancellation use releases context`() {
        val f = Fixture()
        f.responses.add(response(listOf(hit(1))))
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        f.beforeSearch = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            val page = pool.submit<ExpertSearchCursor.Page> { f.cursor.nextPage() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val renewal = pool.submit { f.renewals.single().run() }
            val close = pool.submit { f.cursor.close() }
            assertFalse(renewal.isDone)
            assertFalse(close.isDone)
            release.countDown()
            assertEquals(1, page.get(5, TimeUnit.SECONDS).profiles.size)
            renewal.get(5, TimeUnit.SECONDS)
            close.get(5, TimeUnit.SECONDS)
            val count = f.requests.size
            f.renewals.single().run()
            f.cursor.close()
            assertEquals(count, f.requests.size)
            assertEquals(1, f.deletes().size)
        } finally {
            release.countDown()
            pool.shutdownNow()
            f.cursor.close()
        }
        val cancelled = Fixture()
        cancelled.responses.add(response(listOf(hit(1))))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            cancelled.cursor.use { cursor ->
                cursor.nextPage()
                throw java.util.concurrent.CancellationException("operator cancelled")
            }
        }
        assertEquals(1, cancelled.deletes().size)
    }

    private class EsContainer : GenericContainer<EsContainer>(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.14.2"))

    @Test
    @EnabledIfSystemProperty(named = "esIt", matches = "true")
    fun `real ES 7_14 cursor traverses 11001 documents and early stop frees independent contexts`() {
        check(DockerClientFactory.instance().isDockerAvailable) { "Docker is required for real ES cursor smoke" }
        val es = EsContainer().withExposedPorts(9200)
            .withEnv("discovery.type", "single-node").withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/").forStatusCode(200)).withStartupTimeout(Duration.ofMinutes(3))
        es.start()
        try {
            val url = "http://${es.host}:${es.getMappedPort(9200)}"
            val index = "cursor-${UUID.randomUUID()}"
            val client = RestTemplate()
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            client.put("$url/$index", HttpEntity(mapOf("settings" to mapOf("number_of_shards" to 2),
                "mappings" to mapOf("properties" to mapOf("candidateValidatedAt" to mapOf("type" to "date"),
                    "orcidId" to mapOf("type" to "keyword")))), headers))
            val properties = ElasticsearchProperties(baseUrl = url, username = "test", password = "test",
                rawIndexName = index, candidateIndexName = index, applicationIndexName = index)
            val requests = mutableListOf<Pair<String, JsonNode?>>()
            client.interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { request, body, execution ->
                requests.add(request.uri.path to if (body.isEmpty()) null else mapper.readTree(body))
                execution.execute(request, body)
            })
            val service = ExpertSearchService(client, properties, ExpertIndexService(properties, client, mapper))
            try {
                val bulk = buildString {
                    for (id in 1..11001) {
                        append(mapper.writeValueAsString(mapOf("index" to mapOf("_id" to "doc-$id")))).append('\n')
                        append(mapper.writeValueAsString(if (id % 3 == 0) mapOf("orcidId" to "same")
                            else mapOf("orcidId" to "same", "candidateValidatedAt" to "2026-10-22T00:00:00Z"))).append('\n')
                    }
                }
                val bulkResult = client.postForObject("$url/$index/_bulk?refresh=true", HttpEntity(bulk, headers), JsonNode::class.java)!!
                assertFalse(bulkResult.path("errors").asBoolean())
                requests.clear()
                val ids = mutableSetOf<String>()
                var pages = 0
                service.openCandidateCursor(ExpertIndexLevel.CANDIDATE).use { cursor ->
                    while (true) {
                        val page = cursor.nextPage()
                        pages++
                        if (page.exhausted) break
                        assertTrue(page.profiles.size <= 500)
                        page.profiles.forEach { assertTrue(ids.add(it.esDocId!!), "No duplicate document") }
                        if (pages == 1) {
                            // Live writes shrink the current query, but must not change the PIT enumeration.
                            client.delete("$url/$index/_doc/${page.profiles.first().esDocId}?refresh=true")
                        }
                    }
                }
                assertEquals(11001, ids.size)
                assertEquals(24, pages)
                val searches = requests.filter { it.first == "/_search" }.map { it.second!! }
                assertEquals(24, searches.size)
                searches.forEach {
                    assertFalse(it.has("from"))
                    assertFalse(it.path("track_total_hits").asBoolean(true))
                    assertEquals("5m", it.path("pit").path("keep_alive").asText())
                    if (it.has("search_after")) assertTrue(it.path("search_after").last().isIntegralNumber)
                }
                assertFalse(requests.any { it.first.endsWith("/_count") })
                assertEquals(1, requests.count { it.first == "/_pit" })
                println("CURSOR_SMOKE ES=7.14.2 documents=${ids.size} pages=$pages searchRequests=${searches.size} countRequests=0")
                requests.clear()
                val sending = service.openCandidateCursor(ExpertIndexLevel.CANDIDATE, pageSize = 10)
                val statistics = service.openCandidateCursor(ExpertIndexLevel.CANDIDATE, pageSize = 7)
                assertEquals(10, sending.nextPage().profiles.size)
                assertEquals(7, statistics.nextPage().profiles.size)
                sending.close()
                sending.close()
                assertEquals(7, statistics.nextPage().profiles.size)
                service.closeCandidateCursors()
                assertEquals(3, requests.count { it.first == "/_search" }, "No close-time prefetch")
                assertEquals(2, requests.count { it.first == "/_pit" })
                val freed = client.getForObject("$url/$index/_stats/search", JsonNode::class.java)!!
                assertEquals(0, freed.path("_all").path("total").path("search").path("open_contexts").asInt(-1))
                assertThrows(IllegalStateException::class.java) { statistics.nextPage() }
                assertThrows(IllegalStateException::class.java) { service.openCandidateCursor(ExpertIndexLevel.CANDIDATE) }
                println("CURSOR_SMOKE earlyStopSearchRequests=3 releasedPits=2 openContexts=0 SMTP=0")
            } finally {
                service.closeCandidateCursors()
                client.delete("$url/$index")
            }
        } finally { es.stop() }
    }
}
