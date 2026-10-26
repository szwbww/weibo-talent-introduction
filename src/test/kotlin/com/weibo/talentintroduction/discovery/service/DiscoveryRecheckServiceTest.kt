package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryVerificationProperties
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.repository.TaskExecutionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

class DiscoveryRecheckServiceTest {
    private inline fun <reified T> mock(): T = Mockito.mock(T::class.java)
    private val repository: DiscoveryVerificationRepository = mock()
    private val queries: DiscoveryVerificationQueryService = mock()
    private val writer: ExpertIndexWriterService = mock()
    private val admissions: DiscoveryReviewRepository = mock()
    private val tasks: TaskExecutionRepository = mock()
    private val commits: DiscoveryVerificationCommitService = mock()
    private val papers: PaperVerificationEvidenceService = mock()
    private val key = UUID.randomUUID().toString()
    private fun service(properties: DiscoveryVerificationProperties = DiscoveryVerificationProperties()) = DiscoveryRecheckService(
        repository, admissions, writer, mock(), mock(), mock(), properties, tasks, TransactionTemplate(), queries,
        commits, Executor { it.run() }, DefaultListableBeanFactory().getBeanProvider(DiscoveryVerificationWorker::class.java), papers)

    @Test fun `canonical spec sorts deduplicates real ids and does not contain changing time`() {
        val service = service()
        val first = service.canonicalSpec(RecheckPrepareRequest(key, docIds = listOf("b", "a", "b")), "operator")
        assertEquals(first, service.canonicalSpec(RecheckPrepareRequest(key, docIds = listOf("a", "b")), "operator"))
        val tree = jacksonObjectMapper().readTree(first)
        assertEquals("AUDIT", tree.path("mode").asText()); assertEquals("REUSE", tree.path("evidenceMode").asText())
        assertEquals(listOf("a", "b"), tree.path("docIds").map { it.asText() })
        assertNotEquals(first, service.canonicalSpec(RecheckPrepareRequest(key, docIds = listOf("a", "b"), mode = VerificationMode.ENFORCE), "operator"))
        assertNotEquals(first, service.canonicalSpec(RecheckPrepareRequest(key, docIds = listOf("a", "b")), "other"))
        Mockito.verifyNoInteractions(repository, writer, admissions, tasks, commits, papers)
    }

    @Test fun `all matching preserves filter without imposing selected id limit`() {
        val text = service().canonicalSpec(RecheckPrepareRequest(key, RecheckScope.ALL_MATCHING,
            filter = RecheckFilter(" quantum ", "ANY", "HOLD")), "operator")
        val tree = jacksonObjectMapper().readTree(text)
        assertEquals("quantum", tree.path("filter").path("q").asText())
        assertEquals("HOLD", tree.path("filter").path("decision").asText())
        assertTrue(tree.path("docIds").isEmpty)
    }

    @Test fun `all pending overrides page search and status filters on server`() {
        val text = service().canonicalSpec(RecheckPrepareRequest(key, RecheckScope.ALL_PENDING,
            filter = RecheckFilter("unrelated", "ANY", "AUTO_PASSED")), "operator")
        val tree = jacksonObjectMapper().readTree(text)
        assertEquals("NEEDS_REVIEW", tree.path("filter").path("decision").asText())
        assertTrue(tree.path("filter").path("q").isNull)
        assertTrue(tree.path("filter").path("issue").isNull)
        assertTrue(tree.path("docIds").isEmpty)
    }

    @Test fun `invalid request parameters fail before registration or source reads`() {
        val invalid = listOf(RecheckPrepareRequest("not-uuid", docIds = listOf("a")),
            RecheckPrepareRequest(key), RecheckPrepareRequest(key, docIds = (1..101).map { "d$it" }),
            RecheckPrepareRequest(key, RecheckScope.ALL_MATCHING, listOf("a")),
            RecheckPrepareRequest(key, docIds = listOf("../wrong")),
            RecheckPrepareRequest(key, RecheckScope.ALL_MATCHING, filter = RecheckFilter(decision = "NOT_A_DECISION")))
        invalid.forEach { request -> assertThrows(IllegalArgumentException::class.java) { service().prepare(request, "operator") } }
        Mockito.verifyNoInteractions(repository, writer, admissions, tasks, commits, papers)
    }

    @Test fun `disabled start retains ready without activation`() {
        val recheck = RecheckRun(RecheckSpec("operator", key, "{}", RecheckEvidenceMode.REUSE, Instant.EPOCH, "v", "v"),
            RecheckControl(RecheckPhase.READY, "a".repeat(64)))
        val run = VerificationRun(42, VerificationMode.AUDIT, VerificationMode.AUDIT.policyVersion, false, "{}", true,
            7, Instant.EPOCH, Instant.EPOCH, "{}")
        Mockito.`when`(queries.requireRecheck(42)).thenReturn(run)
        Mockito.`when`(repository.findRun(42)).thenReturn(run)
        Mockito.`when`(repository.recheck(run)).thenReturn(recheck)
        val error = assertThrows(ResponseStatusException::class.java) { service().start(42, RecheckStartRequest("a".repeat(64), 7)) }
        assertEquals(HttpStatus.CONFLICT, error.status); assertEquals("VERIFICATION_DISABLED", error.reason)
        assertFalse(Mockito.mockingDetails(repository).invocations.any { it.method.name == "startRecheck" })
        Mockito.verifyNoInteractions(commits, writer, papers)
    }

    @Test fun `sync validates all ownership before any projection work`() {
        Mockito.`when`(queries.requireRecheckItem(42, 7)).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "RECHECK_ITEM_NOT_FOUND"))
        val error = assertThrows(ResponseStatusException::class.java) { service().syncRetry(42, RecheckSyncRequest(listOf(7))) }
        assertEquals(HttpStatus.NOT_FOUND, error.status)
        Mockito.verifyNoInteractions(commits, writer, papers)
    }
}
