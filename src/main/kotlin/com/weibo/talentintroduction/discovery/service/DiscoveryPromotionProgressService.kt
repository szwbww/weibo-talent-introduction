package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Read projection only: asynchronous admission must not overwrite discovery's progress writer. */
@Service
class DiscoveryPromotionProgressService(
    private val jobs: ExpertAcademicEnrichmentJobRepository,
    private val executions: TaskExecutionRepository,
    private val logs: TaskProgressLogRepository,
    private val restTemplate: RestTemplate,
    private val properties: ElasticsearchProperties,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun refresh(progress: TaskProgress): TaskProgress {
        if (progress.taskType != "EXPERT_DISCOVERY" || progress.details?.get("bySource") == null) return progress
        return safely(progress) {
            val id = progress.executionId ?: logs.findTopByTaskTypeOrderByIdDesc(progress.taskType)?.taskExecutionId
                ?: return@safely progress
            val execution = executions.findById(id).orElse(null) ?: return@safely progress
            val details = mapper.valueToTree<ObjectNode>(progress.details)
            val counts = counts(execution)
            merge(details, counts)
            @Suppress("UNCHECKED_CAST")
            progress.copy(
                details = mapper.convertValue(details, Map::class.java) as Map<String, Any>,
                message = progress.message?.let { message ->
                    val currentSource = details.path("currentSource").asText()
                    val promoted = if (currentSource.isBlank()) details.path("promoted")
                    else details.path("bySource").path(currentSource).path("promoted")
                    if (promoted.isNumber) message.replace(Regex("晋升 \\d+"), "晋升 ${promoted.asInt()}") else message
                }
            )
        }
    }

    /** Completed tasks also gain asynchronous promotions on subsequent reads, including after restart. */
    fun refresh(execution: TaskExecution): TaskExecution {
        if (execution.taskType != "EXPERT_DISCOVERY" || execution.resultSummary.isNullOrBlank()) return execution
        return safely(execution) {
            val root = mapper.readTree(execution.resultSummary) as? ObjectNode ?: return@safely execution
            val stats = (root.get("stats") as? ObjectNode) ?: root
            if (!stats.path("bySource").isObject) return@safely execution
            if (!stats.has("summaryText") && root.has("summaryText")) stats.set<JsonNode>("summaryText", root.get("summaryText"))
            merge(stats, counts(execution))
            root.put("summaryText", stats.path("summaryText").asText())
            execution.copy(resultSummary = mapper.writeValueAsString(root))
        }
    }

    internal fun counts(execution: TaskExecution): Map<String, Int> {
        val id = execution.id ?: return emptyMap()
        val documents = jobs.findDiscoveryDocuments(id).associate { it.expertDocId to it.source }
        val admitted = mutableSetOf<String>()
        val start = execution.startedAt.truncatedTo(ChronoUnit.SECONDS)
        val end = execution.finishedAt
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBasicAuth(properties.username, properties.password)
        }
        for (chunk in documents.keys.chunked(200)) {
            val requests = listOf(properties.candidateIndexName, properties.applicationIndexName).flatMap { index ->
                chunk.map { docId -> mapOf("_index" to index, "_id" to docId,
                    "_source" to listOf("discoveredAt", "tags")) }
            }
            val response = restTemplate.exchange("${properties.baseUrl}/_mget", HttpMethod.POST,
                HttpEntity(mapOf("docs" to requests), headers), JsonNode::class.java).body
                ?: error("Missing promotion lookup response")
            val docs = response.path("docs")
            check(docs.isArray && docs.size() == requests.size) { "Incomplete promotion lookup response" }
            for (doc in docs) {
                check(!doc.has("error")) { "Promotion document lookup failed" }
                if (!doc.path("found").asBoolean()) continue
                val source = doc.path("_source")
                if (source.path("tags").none { it.asText() == "discovered" }) continue
                val discovered = parseDate(source.path("discoveredAt").asText()) ?: continue
                // Duplicate encounters also enqueue old experts. They are not this run's new admissions.
                if (discovered.isBefore(start) || (end != null && discovered.isAfter(end))) continue
                val docId = doc.path("_id").asText()
                if (docId in documents) admitted.add(docId)
            }
        }
        return admitted.groupingBy { documents.getValue(it) }.eachCount()
    }

    private fun merge(details: ObjectNode, counts: Map<String, Int>) {
        val sources = details.path("bySource") as? ObjectNode ?: return
        var total = 0
        var summary = details.path("summaryText").asText()
        sources.fields().forEach { (source, value) ->
            if (value is ObjectNode) {
                // Actual layer membership already includes synchronous admissions: never add the two.
                val promoted = maxOf(value.path("promoted").asInt(), counts[source] ?: 0)
                value.put("promoted", promoted)
                total += promoted
                summary = summary.replace(Regex("(${Regex.escape(source)} 收录 \\d+/晋升 )\\d+")) { "${it.groupValues[1]}$promoted" }
            }
        }
        details.put("promoted", total)
        summary = summary.replace(Regex("(合计:.*?晋升 )\\d+")) { "${it.groupValues[1]}$total" }
        details.put("summaryText", summary.ifBlank { "收录 ${details.path("indexed").asInt()}，晋升 $total（含异步补全，按专家去重）" })
    }

    private fun parseDate(value: String): LocalDateTime? = runCatching {
        LocalDateTime.parse(value.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrNull()

    private fun <T> safely(original: T, action: () -> T): T = try {
        action()
    } catch (e: Exception) {
        log.warn("Discovery promotion statistics unavailable: {}", e.javaClass.simpleName)
        original
    }
}
