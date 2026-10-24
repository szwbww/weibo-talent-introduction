package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.weibo.talentintroduction.config.DiscoveryExecutorConfig
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.config.ExpertDiscoveryProperties
import com.weibo.talentintroduction.config.MailSchedulingProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.DiscoveryReviewRepository
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import com.weibo.talentintroduction.expert.service.ExpertIndexService
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.service.TaskExecutionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class DiscoveryReviewCountsServiceTest {
    @Test
    fun `15501 enriched rows produce six decisions and four cards in one 32 batch scan`() {
        val f = CountsFixture()
        val accepted = f.service.refresh("operator")
        assertEquals(DiscoveryReviewCountsState.PREPARING, accepted.state)
        assertNull(accepted.executionId)
        f.work.single().run()
        val status = f.service.status(accepted.requestKey)
        assertEquals(DiscoveryReviewCountsState.SUCCESS, status.state)
        assertEquals(1L, status.executionId)
        assertEquals(15501, status.scanned)
        assertEquals(15140, status.result!!.uninitialized)
        assertEquals(mapOf("autoPassed" to 100, "needsReview" to 200, "manualApproved" to 50, "held" to 11), status.result!!.counts)
        assertEquals(1, f.searches)
        assertEquals(32, f.pages)
        assertEquals(1, f.clears)
        assertEquals(32, f.admissionReads)
        val oracle = mutableMapOf<String, Int>()
        f.scan.scanAll(DiscoveryReviewScanService.DiscoveryReviewFilter(ExpertIndexLevel.RAW, "discovered", null, null, null)) { batch ->
            batch.filter { it.row.initialized }.forEach { oracle.merge(it.row.decision, 1, Int::plus) }
            true
        }
        assertEquals(oracle, status.result!!.decisions)
        assertTrue(f.businessCalls.all { it in setOf("findAdmissions", "findItemsByIds") }, "no business writes")
        assertTrue(f.writerCalls.all { it == "discoveryProfile" }, "no ES writes")
        assertTrue(f.searchBodies.all { !it.containsKey("from") }, "listPage must not be called")
        assertTrue(f.searchBodies.all { it["size"] == 500 })
    }

    @Test
    fun `page eleven 503 fails without complete result and preserves persisted previous success`() {
        val f = CountsFixture()
        val first = f.service.refresh("op")
        f.work.removeAt(0).run()
        val old = f.service.status(first.requestKey).result!!
        f.failPage = 11
        val next = f.service.refresh("op")
        f.work.removeAt(0).run()
        val failed = f.service.status(next.requestKey)
        assertEquals(DiscoveryReviewCountsState.FAILED, failed.state)
        assertNull(failed.result)
        assertEquals(5000, failed.scanned)
        assertEquals("REVIEW_SCAN_FAILED", failed.error!!.code)
        assertEquals("HTTP_503", failed.error!!.causeCategory)
        assertEquals(old, failed.lastSuccess)
        assertEquals(2, f.clears)
        val restarted = DiscoveryReviewCountsService(f.scan, f.tasks, f.mapper, Executor { error("GET scheduled work") })
        assertEquals(failed, restarted.status(next.requestKey))
    }

    @Test
    fun `twenty concurrent refreshes reuse one preparing request and GET never schedules`() {
        val f = CountsFixture()
        val pool = Executors.newFixedThreadPool(20)
        try {
            val replies = pool.invokeAll((1..20).map { Callable { f.service.refresh("op") } }).map { it.get() }
            assertEquals(1, replies.map { it.requestKey }.distinct().size)
            assertEquals(1, f.work.size)
            assertEquals(0, f.searches)
            assertEquals(DiscoveryReviewCountsState.PREPARING, f.service.status(replies.first().requestKey).state)
            assertEquals(1, f.work.size)
            assertThrows(NoSuchElementException::class.java) { f.service.status(UUID.randomUUID().toString()) }
            assertEquals(1, f.work.size)
        } finally { pool.shutdownNow() }
    }

    @Test
    fun `scroll integrity failures remain distinct and clear every available scroll`() {
        for ((mode, code) in mapOf("timeout" to "REVIEW_SCAN_TIMED_OUT", "shards" to "REVIEW_SCAN_SHARDS_FAILED",
            "null" to "REVIEW_SCAN_EMPTY_BODY", "scroll" to "REVIEW_SCAN_SCROLL_LOST", "hits" to "REVIEW_SCAN_INVALID_RESPONSE")) {
            val f = CountsFixture()
            f.invalid = mode
            val accepted = f.service.refresh("op")
            f.work.single().run()
            val status = f.service.status(accepted.requestKey)
            assertEquals(DiscoveryReviewCountsState.FAILED, status.state, mode)
            assertEquals(code, status.error!!.code, mode)
            assertNull(status.result)
            assertEquals(if (mode == "scroll" || mode == "null") 0 else 1, f.clears, mode)
        }
    }

    @Test
    fun `a later null response and scroll loss cannot finish partially successful`() {
        for (mode in listOf("null", "scroll")) {
            val f = CountsFixture()
            f.invalid = mode
            f.invalidPage = 2
            val accepted = f.service.refresh("op")
            f.work.single().run()
            assertEquals(DiscoveryReviewCountsState.FAILED, f.service.status(accepted.requestKey).state)
            assertEquals(1, f.clears)
        }
    }

    @Test
    fun `database failure is visible including before task creation`() {
        val f = CountsFixture()
        f.failAdmissions = true
        val accepted = f.service.refresh("op")
        f.work.single().run()
        assertEquals("COUNTS_DATABASE_FAILED", f.service.status(accepted.requestKey).error!!.code)
        assertEquals(1, f.clears)
        val g = CountsFixture()
        g.failSave = true
        val next = g.service.refresh("op")
        g.work.single().run()
        val status = g.service.status(next.requestKey)
        assertNull(status.executionId)
        assertEquals(DiscoveryReviewCountsState.FAILED, status.state)
        assertEquals("COUNTS_DATABASE_FAILED", status.error!!.code)
    }

    @Test
    fun `recovery interrupted row is read without restarting and a new refresh has a new key`() {
        val f = CountsFixture()
        val accepted = f.service.refresh("op")
        f.work.single().run()
        f.rows[1L] = f.rows.getValue(1L).copy(status = "INTERRUPTED", resultSummary = null, errorMessage = "EXECUTION_LOST")
        val restarted = DiscoveryReviewCountsService(f.scan, f.tasks, f.mapper, Executor { f.work.add(it) })
        assertEquals(DiscoveryReviewCountsState.INTERRUPTED, restarted.status(accepted.requestKey).state)
        assertEquals("COUNTS_INTERRUPTED", restarted.status(accepted.requestKey).error!!.code)
        assertEquals(1, f.searches)
        assertNotEquals(accepted.requestKey, restarted.refresh("op").requestKey)
    }

    @Test
    fun `each batch stops when execution loses ownership and finally clears scroll`() {
        val f = CountsFixture()
        f.interruptPage = 2
        val accepted = f.service.refresh("op")
        f.work.single().run()
        val status = f.service.status(accepted.requestKey)
        assertEquals(DiscoveryReviewCountsState.INTERRUPTED, status.state)
        assertNull(status.result)
        assertEquals(2, f.pages)
        assertEquals(1, f.clears)
    }

    @Test
    fun `rejected submission has no fictitious task and dedicated executor has no queue`() {
        val f = CountsFixture()
        val rejected = DiscoveryReviewCountsService(f.scan, f.tasks, f.mapper, Executor { throw RejectedExecutionException() })
        assertThrows(DiscoveryReviewCountsBusyException::class.java) { rejected.refresh("op") }
        assertTrue(f.rows.isEmpty())
        val executor = DiscoveryExecutorConfig(ExpertDiscoveryProperties()).discoveryReviewCountsExecutor() as ThreadPoolTaskExecutor
        try {
            assertEquals(1, executor.corePoolSize)
            assertEquals(1, executor.maxPoolSize)
            assertEquals(0, executor.threadPoolExecutor.queue.remainingCapacity())
            assertEquals("discovery-review-counts-", executor.threadNamePrefix)
        } finally { executor.shutdown() }
    }
}

/** Real counts, scroll/enrich, and task-execution code; only external storage/HTTP boundaries are fake. */
class CountsFixture {
    val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())
    val rows = java.util.concurrent.ConcurrentHashMap<Long, TaskExecution>()
    val work = java.util.Collections.synchronizedList(mutableListOf<Runnable>())
    var searches = 0
    var pages = 0
    var clears = 0
    var admissionReads = 0
    var failPage = 0
    var invalid: String? = null
    var invalidPage = 1
    var failAdmissions = false
    var failSave = false
    var interruptPage = 0
    val businessCalls = mutableListOf<String>()
    val writerCalls = mutableListOf<String>()
    val searchBodies = mutableListOf<Map<*, *>>()
    private val now = LocalDateTime.now()
    private fun profile(id: String) = ExpertProfile(esDocId = id, orcidId = id, email = "$id@example.org", givenNames = "Ada",
        familyNames = "Lovelace", country = "US", keyword = null, employment = null, institution = "University", tags = listOf("discovered"))
    private fun decision(id: String): String? = when (id.toInt()) {
        in 0..99 -> "AUTO_PASSED"
        in 100..299 -> "NEEDS_REVIEW"
        in 300..329 -> "MANUAL_APPROVED"
        in 330..349 -> "LEGACY_APPROVED"
        in 350..354 -> "HOLD"
        in 355..360 -> "REJECTED"
        else -> null
    }
    private val taskRepository = Mockito.mock(TaskExecutionRepository::class.java) { call ->
        when (call.method.name) {
            "save" -> {
                if (failSave) throw DataAccessResourceFailureException("task database unavailable")
                val row = call.getArgument<TaskExecution>(0).copy(id = (rows.size + 1).toLong())
                rows[row.id!!] = row
                row
            }
            "findById" -> Optional.ofNullable(rows[call.getArgument<Long>(0)])
            "findRecentByTaskType" -> rows.values.sortedByDescending { it.id }.take(call.getArgument(1))
            "updateProgressCounts" -> {
                val id = call.getArgument<Long>(0)
                rows.computeIfPresent(id) { _, row -> row.copy(successCount = call.getArgument(1), failureCount = call.getArgument(2)) }
                1
            }
            "finishOwned" -> {
                val id = call.getArgument<Long>(0)
                if (rows.getValue(id).status != "RUNNING") 0 else {
                    rows[id] = rows.getValue(id).copy(status = call.getArgument(2), resultSummary = call.getArgument(3),
                        successCount = call.getArgument(4), failureCount = call.getArgument(5), errorMessage = call.getArgument(6), finishedAt = call.getArgument(7))
                    1
                }
            }
            else -> Mockito.RETURNS_DEFAULTS.answer(call)
        }
    }
    val tasks = TaskExecutionService(taskRepository, mapper, MailSchedulingProperties())
    private val repository = Mockito.mock(DiscoveryReviewRepository::class.java) { call ->
        businessCalls.add(call.method.name)
        when (call.method.name) {
            "findAdmissions" -> {
                admissionReads++
                if (failAdmissions) throw DataAccessResourceFailureException("admission database unavailable")
                call.getArgument<Collection<String>>(0).mapNotNull { id -> decision(id)?.let { d ->
                    ExpertDiscoveryAdmission(id, DiscoveryReviewIdentity.hash(id, "$id@example.org", "Ada", "Lovelace"), d, 1, null, null, null, now)
                } }
            }
            "findItemsByIds" -> emptyList<ExpertDiscoveryReviewItem>()
            else -> Mockito.RETURNS_DEFAULTS.answer(call)
        }
    }
    private val writer = Mockito.mock(ExpertIndexWriterService::class.java) { call ->
        writerCalls.add(call.method.name)
        if (call.method.name == "discoveryProfile") profile(call.getArgument(0)) else Mockito.RETURNS_DEFAULTS.answer(call)
    }
    private val policy = Mockito.mock(DiscoveryAdmissionPolicy::class.java) { call ->
        if (call.method.name == "evaluate") AutomaticAdmissionResult(DiscoveryAdmissionStatus.NEEDS_REVIEW, emptyList(), emptyList(), "counts-test",
            Instant.EPOCH, AdmissionConfigSnapshot(true, true, false, false, false, 70, false, 5, false, 50, false, 5))
        else Mockito.RETURNS_DEFAULTS.answer(call)
    }
    private val indices = Mockito.mock(ExpertIndexService::class.java) { call ->
        if (call.method.name == "indexName") "raw" else Mockito.RETURNS_DEFAULTS.answer(call)
    }
    private val rest = Mockito.mock(RestTemplate::class.java) { call ->
        if (call.method.name != "exchange") Mockito.RETURNS_DEFAULTS.answer(call) else {
            val method = call.getArgument<HttpMethod>(1)
            if (method == HttpMethod.DELETE) {
                clears++
                ResponseEntity.ok(mapper.createObjectNode())
            } else {
                val url = call.getArgument<String>(0)
                if (!url.contains("_search/scroll")) {
                    searches++
                    pages = 0
                    searchBodies.add(call.getArgument<HttpEntity<*>>(2).body as Map<*, *>)
                }
                pages++
                if (interruptPage == pages) rows.computeIfPresent(1L) { _, row -> row.copy(status = "INTERRUPTED") }
                if (failPage == pages) throw HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "ES fixture unavailable")
                if (invalid == "null" && invalidPage == pages) ResponseEntity.ok<JsonNode>(null) else {
                    val start = (pages - 1) * 500
                    val hits = (start until minOf(start + 500, 15501)).map { id -> mapOf("_id" to id.toString(), "_source" to mapOf("email" to "$id@example.org")) }
                    val node = mapper.valueToTree<JsonNode>(mapOf("_scroll_id" to "fixture-scroll", "hits" to mapOf("hits" to hits)))
                    if (invalidPage == pages) when (invalid) {
                        "timeout" -> (node as com.fasterxml.jackson.databind.node.ObjectNode).put("timed_out", true)
                        "shards" -> (node as com.fasterxml.jackson.databind.node.ObjectNode).set<JsonNode>("_shards", mapper.readTree("{\"failed\":1}"))
                        "scroll" -> (node as com.fasterxml.jackson.databind.node.ObjectNode).remove("_scroll_id")
                        "hits" -> (node as com.fasterxml.jackson.databind.node.ObjectNode).remove("hits")
                    }
                    ResponseEntity.ok(node)
                }
            }
        }
    }
    val scan = DiscoveryReviewScanService(repository, policy, writer, rest, ElasticsearchProperties("http://es", "u", "p", "raw", "cand", "app"), indices, mapper)
    val service = DiscoveryReviewCountsService(scan, tasks, mapper, Executor { work.add(it) })
}
