package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository
import com.weibo.talentintroduction.discovery.repository.DiscoveryVerificationRepository.PromotionDocuments
import com.weibo.talentintroduction.discovery.repository.DiscoveryJobDocument
import com.weibo.talentintroduction.discovery.repository.ExpertAcademicEnrichmentJobRepository
import com.weibo.talentintroduction.task.domain.TaskExecution
import com.weibo.talentintroduction.task.domain.TaskProgressLog
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import com.weibo.talentintroduction.task.repository.TaskProgressLogRepository
import com.weibo.talentintroduction.task.service.TaskProgress
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.util.Optional

class DiscoveryPromotionProgressServiceTest {
    private val jobs = mock(ExpertAcademicEnrichmentJobRepository::class.java)
    private val verification = mock(DiscoveryVerificationRepository::class.java)
    private val executions = mock(TaskExecutionRepository::class.java)
    private val logs = mock(TaskProgressLogRepository::class.java)
    private val rest = RestTemplate()
    private val server = MockRestServiceServer.createServer(rest)
    private val mapper = jacksonObjectMapper()
    private val service = DiscoveryPromotionProgressService(jobs, verification, executions, logs, rest,
        ElasticsearchProperties("http://es", "user", "pass", "raw", "candidate", "application"), mapper)
    private val start = LocalDateTime.of(2026, 9, 27, 20, 3, 19, 200000000)
    private val execution = TaskExecution(42L, "EXPERT_DISCOVERY", "MANUAL", "SUCCESS", null, null,
        startedAt = start, finishedAt = start.plusMinutes(20))

    private fun progress() = TaskProgress("EXPERT_DISCOVERY", "RUNNING", 3, 300, 10000,
        message = "[OPENALEX] 批次 3: 收录 8, 晋升 1", executionId = 42L,
        details = mapOf("indexed" to 8, "promoted" to 1, "currentSource" to "OPENALEX",
            "bySource" to mapOf("OPENALEX" to mapOf("indexed" to 8, "promoted" to 1),
                "CROSSREF" to mapOf("indexed" to 0, "promoted" to 0))))

    private fun fixture() {
        `when`(verification.reportingPromotionDocuments(42L)).thenReturn(PromotionDocuments(emptySet(), emptyMap()))
        `when`(executions.findById(42L)).thenReturn(Optional.of(execution))
        `when`(jobs.findDiscoveryDocuments(42L)).thenReturn(listOf(
            DiscoveryJobDocument("new1", "OPENALEX"), DiscoveryJobDocument("new2", "OPENALEX"),
            DiscoveryJobDocument("old", "OPENALEX"), DiscoveryJobDocument("raw-only", "OPENALEX")))
    }

    private fun found(id: String, date: String = "2026-09-27 20:08:00") =
        mapOf("_id" to id, "found" to true, "_source" to mapOf("discoveredAt" to date, "tags" to listOf("discovered")))
    private fun missing(id: String) = mapOf("_id" to id, "found" to false)
    private fun response() = mapper.writeValueAsString(mapOf("docs" to listOf(
        found("new1"), found("new2"), found("old", "2026-08-02 10:00:00"), missing("raw-only"),
        found("new1"), missing("new2"), missing("old"), missing("raw-only"))))

    @Test
    fun `live count includes async admissions but deduplicates layers and excludes old encounters`() {
        fixture()
        server.expect(requestTo("http://es/_mget")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.docs.length()").value(8))
            .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))
        val original = progress()
        val result = service.refresh(original)
        assertEquals(2, result.details!!["promoted"])
        assertTrue(result.message!!.endsWith("晋升 2"))
        assertEquals(1, original.details!!["promoted"], "read projection must not mutate the writer snapshot")
        val sources = mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(result.details).path("bySource")
        assertEquals(2, sources.path("OPENALEX").path("promoted").asInt())
        assertEquals(0, sources.path("CROSSREF").path("promoted").asInt())
        server.verify()
    }

    @Test
    fun `completed history refreshes without changing persisted summary`() {
        fixture()
        server.expect(requestTo("http://es/_mget")).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))
        val original = execution.copy(resultSummary = mapper.writeValueAsString(mapOf("stats" to progress().details)))
        val result = service.refresh(original)
        assertEquals(2, mapper.readTree(result.resultSummary).path("stats").path("promoted").asInt())
        assertEquals(1, mapper.readTree(original.resultSummary).path("stats").path("promoted").asInt())
    }

    @Test
    fun `cleared execution context uses persisted task identity`() {
        fixture()
        `when`(logs.findTopByTaskTypeOrderByIdDesc("EXPERT_DISCOVERY")).thenReturn(
            TaskProgressLog(taskType = "EXPERT_DISCOVERY", taskExecutionId = 42L, status = "SUCCESS",
                batchNumber = 3, processedCount = 300, totalCount = 10000))
        server.expect(requestTo("http://es/_mget")).andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))
        assertEquals(2, service.refresh(progress().copy(executionId = null)).details!!["promoted"])
    }

    @Test
    fun `ES failure preserves recorded progress instead of replacing it with zero`() {
        fixture()
        server.expect(requestTo("http://es/_mget")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        val original = progress()
        assertSame(original, service.refresh(original))
    }

    @Test
    fun `unrelated tasks never query discovery data`() {
        val original = progress().copy(taskType = "EXPERT_ENRICHMENT")
        assertSame(original, service.refresh(original))
        verifyNoInteractions(jobs, verification, executions, logs)
    }

    private fun managed(managedIds: Set<String>, projected: Map<String, String>) {
        `when`(executions.findById(42L)).thenReturn(Optional.of(execution))
        `when`(verification.reportingPromotionDocuments(42L)).thenReturn(PromotionDocuments(managedIds, projected))
    }

    private fun zeroProgress() = progress().copy(
        details = mapOf("indexed" to 8, "promoted" to 0, "currentSource" to "OPENALEX",
            "autoAdmitted" to 2,
            "bySource" to mapOf("OPENALEX" to mapOf("indexed" to 8, "promoted" to 0),
                "CROSSREF" to mapOf("indexed" to 0, "promoted" to 0))))

    @Test
    fun `projected ledger counts without this run job or ES dates after collection finishes`() {
        managed(setOf("old-job"), mapOf("old-job" to "OPENALEX"))
        // The unique enrichment job still belongs to an earlier run, so this lookup is empty.
        `when`(jobs.findDiscoveryDocuments(42L)).thenReturn(emptyList())
        assertEquals(mapOf("OPENALEX" to 1), service.counts(execution))
        assertEquals(mapOf("OPENALEX" to 1), service.counts(execution.copy(finishedAt = null)))
        server.verify() // No ES lookup or timestamp parsing is needed for a persisted PROJECTED event.
    }

    @Test
    fun `legacy and managed overlap counts once with ledger source and disjoint source still counts`() {
        managed(setOf("overlap"), mapOf("overlap" to "CROSSREF"))
        `when`(jobs.findDiscoveryDocuments(42L)).thenReturn(listOf(
            DiscoveryJobDocument("overlap", "OPENALEX"), DiscoveryJobDocument("legacy", "OPENALEX")))
        server.expect(requestTo("http://es/_mget"))
            .andExpect(jsonPath("$.docs.length()").value(2))
            .andExpect(jsonPath("$.docs[0]._id").value("legacy"))
            .andRespond(withSuccess(mapper.writeValueAsString(mapOf("docs" to listOf(found("legacy"), found("legacy")))),
                MediaType.APPLICATION_JSON))
        assertEquals(mapOf("OPENALEX" to 1, "CROSSREF" to 1), service.counts(execution))
        server.verify()
    }

    @Test
    fun `managed non projected states never gain legacy existence credit`() {
        val ids = listOf("NOT_REQUIRED", "PENDING", "ALREADY_PRESENT", "APPLICATION_PRESENT", "ERROR")
        managed(ids.toSet(), emptyMap())
        `when`(jobs.findDiscoveryDocuments(42L)).thenReturn(ids.map { DiscoveryJobDocument(it, "OPENALEX") })
        assertTrue(service.counts(execution).isEmpty())
        assertEquals(0, service.refresh(zeroProgress()).details!!["promoted"])
        server.verify()
    }

    @Test
    fun `both refresh readers show one promotion for two admissions without any persistence writes`() {
        managed(setOf("projected", "existing"), mapOf("projected" to "OPENALEX"))
        val original = zeroProgress()
        val stored = execution.copy(resultSummary = mapper.writeValueAsString(mapOf("stats" to original.details)))
        repeat(2) {
            val live = service.refresh(original)
            val completed = mapper.readTree(service.refresh(stored).resultSummary).path("stats")
            assertEquals(1, live.details!!["promoted"])
            assertEquals(2, live.details!!["autoAdmitted"])
            assertEquals(1, completed.path("promoted").asInt())
            assertEquals(2, completed.path("autoAdmitted").asInt())
        }
        assertEquals(0, original.details!!["promoted"])
        assertEquals(0, mapper.readTree(stored.resultSummary).path("stats").path("promoted").asInt())
        assertTrue(mockingDetails(verification).invocations.all { it.method.name == "reportingPromotionDocuments" })
        assertTrue(mockingDetails(jobs).invocations.all { it.method.name == "findDiscoveryDocuments" })
        assertTrue(mockingDetails(executions).invocations.all { it.method.name == "findById" })
        verifyNoInteractions(logs)
        server.verify()
    }

    @Test
    fun `legacy aggregate floor is retained not added to the verified union`() {
        managed(setOf("projected"), mapOf("projected" to "OPENALEX"))
        val original = zeroProgress().copy(details = mapOf("bySource" to
            mapOf("OPENALEX" to mapOf("promoted" to 5))))
        assertEquals(5, service.refresh(original).details!!["promoted"], "LEGACY_AGGREGATE_FLOOR")
    }

    @Test
    fun `ledger unavailable preserves both original reader snapshots`() {
        `when`(executions.findById(42L)).thenReturn(Optional.of(execution))
        `when`(verification.reportingPromotionDocuments(42L)).thenThrow(IllegalStateException("ledger unavailable"))
        val original = zeroProgress()
        val stored = execution.copy(resultSummary = mapper.writeValueAsString(mapOf("stats" to original.details)))
        assertSame(original, service.refresh(original))
        assertSame(stored, service.refresh(stored))
        server.verify()
    }

    @Test
    fun `ES failure preserves completed summary even when ledger has a promotion`() {
        managed(setOf("projected"), mapOf("projected" to "OPENALEX"))
        `when`(jobs.findDiscoveryDocuments(42L)).thenReturn(listOf(DiscoveryJobDocument("legacy", "OPENALEX")))
        server.expect(requestTo("http://es/_mget")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        val stored = execution.copy(resultSummary = mapper.writeValueAsString(mapOf("stats" to zeroProgress().details)))
        assertSame(stored, service.refresh(stored))
        server.verify()
    }
}
