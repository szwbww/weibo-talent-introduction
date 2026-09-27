package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.ElasticsearchProperties
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
    private val executions = mock(TaskExecutionRepository::class.java)
    private val logs = mock(TaskProgressLogRepository::class.java)
    private val rest = RestTemplate()
    private val server = MockRestServiceServer.createServer(rest)
    private val mapper = jacksonObjectMapper()
    private val service = DiscoveryPromotionProgressService(jobs, executions, logs, rest,
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
        verifyNoInteractions(jobs, executions, logs)
    }
}
