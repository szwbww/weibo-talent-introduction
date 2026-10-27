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
    @Test fun `idle coordinator does not renew abandoned preparation tasks`() {
        service().reconcile()
        assertFalse(Mockito.mockingDetails(tasks).invocations.any { it.method.name.startsWith("heartbeat") })
    }

    @Test fun `fatal preparation failure records failure instead of leaving running task`() {
        val policy: DiscoveryAdmissionPolicy = mock()
        val tx: TransactionTemplate = mock()
        Mockito.doAnswer { invocation ->
            invocation.getArgument<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>(0)
                .accept(mock())
            null
        }.`when`(tx).executeWithoutResult(Mockito.any())
        val service = DiscoveryRecheckService(repository, admissions, writer, mock(), policy, mock(),
            DiscoveryVerificationProperties(), tasks, tx, queries, commits, Executor { it.run() },
            DefaultListableBeanFactory().getBeanProvider(DiscoveryVerificationWorker::class.java), papers)
        val run = VerificationRun(42, VerificationMode.AUDIT, VerificationMode.AUDIT.policyVersion, false, "{}", false,
            7, Instant.EPOCH, Instant.EPOCH, "{}")
        val recheck = RecheckRun(RecheckSpec("operator", key, "{}", RecheckEvidenceMode.REUSE, Instant.EPOCH, "v", "v"),
            RecheckControl(RecheckPhase.PREPARING))
        Mockito.`when`(repository.findRun(42)).thenReturn(run)
        Mockito.`when`(repository.recheck(run)).thenReturn(recheck)
        Mockito.`when`(repository.recheckCounts(42)).thenReturn(RecheckCounts(0,0,0,0,0,0,0,0,0,0))
        Mockito.`when`(tasks.findById(42)).thenReturn(java.util.Optional.of(
            com.weibo.talentintroduction.task.domain.TaskExecution(id=42, taskType="DISCOVERY_RECHECK_PREPARE",
                triggerType="MANUAL", status="RUNNING", requestPayload=null, resultSummary=null, startedAt=java.time.LocalDateTime.now())))
        Mockito.`when`(policy.currentConfigSnapshot()).thenThrow(OutOfMemoryError("synthetic bounded failure"))
        val freeze = DiscoveryRecheckService::class.java.getDeclaredMethod("freeze", VerificationRun::class.java, RecheckPrepareRequest::class.java)
        freeze.isAccessible = true
        val error = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            freeze.invoke(service, run, RecheckPrepareRequest(key, docIds=listOf("a")))
        }
        assertTrue(error.cause is OutOfMemoryError)
        val terminal = Mockito.mockingDetails(tasks).invocations.filter { it.method.name == "finishOwned" }
        assertEquals(1, terminal.size, "Fatal error must persist a terminal task before being rethrown")
        assertEquals("FAILED", terminal.single().arguments[2])
        assertEquals("PREPARE_FATAL:OutOfMemoryError", terminal.single().arguments[6])
    }

    @Test fun `heartbeat targets only live preparation execution and stops after removal`() {
        val service = service()
        val field = DiscoveryRecheckService::class.java.getDeclaredField("preparingThreads").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val active = field.get(service) as java.util.concurrent.ConcurrentHashMap<Long, Thread>
        active[42]=Thread.currentThread()
        active[43]=Thread { }
        service.reconcile()
        val heartbeats = Mockito.mockingDetails(tasks).invocations.filter { it.method.name.startsWith("heartbeat") }
        assertEquals(1,heartbeats.size)
        assertEquals("heartbeatExecution",heartbeats.single().method.name)
        assertEquals(42L,heartbeats.single().arguments[0])
        active.clear()
        Mockito.clearInvocations(tasks)
        service.reconcile()
        assertFalse(Mockito.mockingDetails(tasks).invocations.any { it.method.name.startsWith("heartbeat") })
    }

}
