package com.weibo.talentintroduction.expert.service

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.expert.domain.ExpertIndexLevel
import com.weibo.talentintroduction.expert.domain.ExpertProfile
import org.springframework.http.HttpMethod
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** PIT only enumerates a snapshot; callers must revalidate current send authorization. */
class ExpertSearchCursor internal constructor(
    levels: List<ExpertIndexLevel>,
    private val pageSize: Int,
    private val indexName: (ExpertIndexLevel) -> String,
    private val searchBody: (ExpertIndexLevel) -> Map<String, Any>,
    private val request: (String, HttpMethod, Map<String, Any>?) -> JsonNode,
    private val profile: (JsonNode) -> ExpertProfile,
    private val scheduler: ScheduledExecutorService,
    private val onClosed: (ExpertSearchCursor) -> Unit
) : AutoCloseable {
    data class Page(val profiles: List<ExpertProfile>, val exhausted: Boolean)

    private val levels = levels.toList()
    private val lock = Any()
    private var levelIndex = 0
    private var pitId: String? = null
    private var searchAfter: JsonNode? = null
    private var renewal: ScheduledFuture<*>? = null
    private var closed = false
    private var exhausted = false
    private var failure: ExpertSearchUnavailableException? = null

    init {
        require(pageSize in 1..500) { "pageSize must be between 1 and 500" }
        require(this.levels.distinct().size == this.levels.size) { "levels must not repeat" }
    }

    fun nextPage(): Page = nextPage(pageSize)

    fun nextPage(pageSize: Int): Page = synchronized(lock) {
        require(pageSize in 1..500) { "pageSize must be between 1 and 500" }
        failure?.let { throw it }
        if (exhausted) return@synchronized Page(emptyList(), true)
        check(!closed) { "Candidate cursor is closed" }
        try {
            while (levelIndex < levels.size) {
                if (pitId == null) openLevel()
                val body = searchBody(levels[levelIndex]).toMutableMap()
                body["size"] = pageSize
                body["track_total_hits"] = false
                body["pit"] = mapOf("id" to pitId!!, "keep_alive" to KEEP_ALIVE)
                searchAfter?.let { body["search_after"] = it }
                val response = search(body)
                val hits = response.path("hits").path("hits")
                requireResponse(hits.isArray && hits.size() <= pageSize, "Invalid candidate hits")
                if (hits.isEmpty) {
                    closeLevel()
                    levelIndex++
                    continue
                }
                val expectedSortSize = (body["sort"] as List<*>).size
                val seenSorts = HashSet<JsonNode>(hits.size())
                for (hit in hits) {
                    val sort = hit.path("sort")
                    requireResponse(sort.isArray && sort.size() == expectedSortSize &&
                        sort.all { it.isValueNode && !it.isNull } && sort.last().isIntegralNumber,
                        "Missing or invalid candidate sort")
                    requireResponse(sort != searchAfter && seenSorts.add(sort), "Candidate cursor did not advance")
                    requireResponse(hit.path("_source").isObject && hit.path("_id").asText().isNotBlank(),
                        "Invalid candidate document")
                }
                // Advance with the raw last hit, never with caller-filtered profiles.
                val profiles = hits.map(profile)
                searchAfter = hits.last().path("sort").deepCopy<JsonNode>()
                return@synchronized Page(profiles, false)
            }
            exhausted = true
            close()
            Page(emptyList(), true)
        } catch (ex: Exception) {
            throw fail(ex)
        }
    }

    private fun openLevel() {
        val response = request("/${indexName(levels[levelIndex])}/_pit?keep_alive=$KEEP_ALIVE", HttpMethod.POST, null)
        val id = response.path("id").asText()
        requireResponse(id.isNotBlank(), "Missing PIT id")
        pitId = id
        if (response.has("_shards") || response.has("error")) validateComplete(response)
        renewal = scheduler.scheduleAtFixedRate({ renew() }, 60, 60, TimeUnit.SECONDS)
    }

    private fun search(body: Map<String, Any>): JsonNode {
        val response = request("/_search", HttpMethod.POST, body)
        // ES may rotate the id even on a partial response: always release the latest id.
        response.path("pit_id").takeIf { it.isTextual && it.asText().isNotBlank() }?.let { pitId = it.asText() }
        validateComplete(response)
        return response
    }

    private fun renew() = synchronized(lock) {
        if (closed || pitId == null) return@synchronized
        try {
            search(mapOf(
                "size" to 0,
                "query" to mapOf("match_none" to emptyMap<String, Any>()),
                "track_total_hits" to false,
                "pit" to mapOf("id" to pitId!!, "keep_alive" to KEEP_ALIVE)
            ))
        } catch (ex: Exception) {
            fail(ex)
        }
    }

    private fun validateComplete(response: JsonNode) {
        val shards = response.path("_shards")
        requireResponse(!response.has("error") && response.path("timed_out").asBoolean(false).not() &&
            shards.path("failed").isIntegralNumber && shards.path("failed").asInt() == 0 &&
            shards.path("total").isIntegralNumber && shards.path("successful").isIntegralNumber &&
            shards.path("successful").asInt() == shards.path("total").asInt(), "Incomplete PIT response")
    }

    private fun requireResponse(valid: Boolean, message: String) {
        if (!valid) throw ExpertSearchUnavailableException(message)
    }

    private fun fail(cause: Exception): ExpertSearchUnavailableException {
        val controlled = if (cause is ExpertSearchUnavailableException) cause else
            ExpertSearchUnavailableException("Candidate cursor request failed").apply { initCause(cause) }
        failure = controlled
        try { close() } catch (cleanup: Exception) { controlled.addSuppressed(cleanup) }
        return controlled
    }

    private fun closeLevel() {
        renewal?.cancel(false)
        renewal = null
        val id = pitId
        pitId = null
        searchAfter = null
        if (id != null) request("/_pit", HttpMethod.DELETE, mapOf("id" to id))
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        try {
            closeLevel()
        } catch (ex: Exception) {
            throw ExpertSearchUnavailableException("Candidate PIT close failed").apply { initCause(ex) }
        } finally {
            onClosed(this)
        }
    }

    private companion object { const val KEEP_ALIVE = "5m" }
}
