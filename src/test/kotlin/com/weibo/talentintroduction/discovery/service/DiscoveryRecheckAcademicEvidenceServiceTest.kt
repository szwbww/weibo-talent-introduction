package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.RecheckEvidenceMode
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
import org.springframework.web.client.RestTemplate
import java.time.Instant

internal fun academicService(client: OpenAlexDataSource, properties: OpenAlexProperties = OpenAlexProperties()): DiscoveryRecheckAcademicEvidenceService =
    DiscoveryRecheckAcademicEvidenceService(StaticListableBeanFactory(mapOf("openAlex" to client)).getBeanProvider(OpenAlexDataSource::class.java),properties)

internal class AcademicClientFixture(baseUrl: String = "https://api.openalex.org") {
    val transport = RestTemplate()
    val europePmc = Mockito.mock(EuropePmcDataSource::class.java)
    val pdf = Mockito.mock(PdfEmailExtractor::class.java)
    val unpaywall = Mockito.mock(UnpaywallClient::class.java)
    val properties = OpenAlexProperties(enabled=true,baseUrl=baseUrl,requestDelayMs=0,enrichmentDelayMs=0,maxRequestsPerSecond=100.0)
    private val time = object : PolicyTimeSource {
        var value = recheckTime
        override fun now() = value
        override fun sleep(ms: Long) { value = value.plusMillis(ms) }
    }
    private val store = InMemoryOpenAlexBudgetStore().also {
        it.reconcile(properties.accountScope,recheckTime.plusSeconds(86400),10000,10000,10000,recheckTime)
    }
    val kinds = mutableListOf<RequestKind>()
    private val audited = object : OpenAlexBudgetStore by store {
        override fun reserve(request: BudgetReserveRequest): BudgetReserveResult {
            kinds += request.kind; return store.reserve(request)
        }
    }
    val client = OpenAlexDataSource(transport,properties,europePmc,pdf,unpaywall,OpenAlexRequestPolicy(properties,time,audited))
    val service = academicService(client,properties)
    companion object {
        const val AUTHOR = """{"id":"https://openalex.org/A123","orcid":"https://orcid.org/0000-0002-1825-0097","summary_stats":{"h_index":12},"cited_by_count":345,"works_count":67,"topics":[{"display_name":"Physics","field":{"id":"https://openalex.org/fields/3100","display_name":"Physics"}}],"last_known_institutions":[],"counts_by_year":[{"year":2025}]}"""
        val success = """{"meta":{"count":1},"results":[$AUTHOR]}"""
        val ambiguous = """{"meta":{"count":2},"results":[$AUTHOR,${AUTHOR.replace("A123","A456")}]}"""
    }
}

class DiscoveryRecheckAcademicEvidenceServiceTest {
    @Test fun `real author response cancellation prevents works patents and next identity dispatch`() {
        assertTrue(academicDispatchCancellation(false).contains("wire=1"))
    }
    @Test fun `academic rate limit uses live retry clock without changing frozen evaluation time`() {
        val client = Mockito.mock(OpenAlexDataSource::class.java)
        val live = recheckTime.plusSeconds(86400)
        val service = DiscoveryRecheckAcademicEvidenceService(
            StaticListableBeanFactory(mapOf("openAlex" to client)).getBeanProvider(OpenAlexDataSource::class.java),
            OpenAlexProperties(), java.time.Clock.fixed(live, java.time.ZoneOffset.UTC))
        Mockito.`when`(client.batchEnrichByAuthorIds(listOf("A123"), RequestKind.HISTORY_ENRICHMENT))
            .thenReturn(mapOf("A123" to EnrichmentOutcome.RateLimited(120000)))
        val snapshot = evidenceSnapshot()
        val result = service.collect(listOf(snapshot), RecheckEvidenceMode.REFRESH, recheckTime).getValue(snapshot.identityHash)
        assertEquals(recheckTime, result.evaluatedAt)
        assertEquals(live.plusSeconds(120), result.retryAt)
        assertEquals(listOf("RATE_LIMITED"), result.reasonCodes)
        assertNull(result.httpStatus)
        assertNull(result.data)
    }
    @Test fun `REUSE never resolves provider and missing evidence stays insufficient`() {
        @Suppress("UNCHECKED_CAST")
        val providers = Mockito.mock(org.springframework.beans.factory.ObjectProvider::class.java) as org.springframework.beans.factory.ObjectProvider<OpenAlexDataSource>
        val service = DiscoveryRecheckAcademicEvidenceService(providers)
        val s = evidenceSnapshot()
        assertEquals(listOf("EVIDENCE_NOT_RETAINED"),service.collect(listOf(s),RecheckEvidenceMode.REUSE,recheckTime).getValue(s.identityHash).reasonCodes)
        val frozen = s.copy(profile=s.profile.copy(hIndex=12,researchFields="Physics",researchFieldIds=listOf("3100")))
        val result = service.collect(listOf(frozen),RecheckEvidenceMode.REUSE,recheckTime).getValue(s.identityHash)
        assertEquals(12,result.data!!.hIndex); assertEquals("Physics",result.profileResearchFields); assertTrue(result.retained)
        val prior = result.copy(data=result.data!!.copy(hIndex=20),sourceUrls=listOf("https://openalex.org/A123"))
        assertEquals(20,service.collect(listOf(s),RecheckEvidenceMode.REUSE,recheckTime,mapOf(s.identityHash to prior)).getValue(s.identityHash).data!!.hIndex)
        assertEquals(listOf("EVIDENCE_NOT_RETAINED"),service.collect(listOf(s),RecheckEvidenceMode.REUSE,recheckTime,
            mapOf(s.identityHash to prior.copy(factFingerprint="c".repeat(64)))).getValue(s.identityHash).reasonCodes)
        Mockito.verifyNoInteractions(providers)
    }
    @Test fun `trusted identity input matrix rejects email work IDs unbound authors stale proof and guesses`() {
        val client = Mockito.mock(OpenAlexDataSource::class.java); val service = academicService(client)
        val base = evidenceSnapshot()
        val invalid = listOf(
            evidenceSnapshot(authorId=null),evidenceSnapshot(authorId="W123"),evidenceSnapshot(authorId="EMAIL-123"),
            evidenceSnapshot(verified=false),base.copy(profile=base.profile.copy(email="changed@fixture.edu")),
            base.copy(profile=base.profile.copy(identityVerification=base.profile.identityVerification!!.copy(version=1))),
            evidenceSnapshot(authorId=null,orcid="EMAIL-123"),
            base.copy(profile=base.profile.copy(identityVerification=null,tags=null,externalIds="""{"openAlexAuthorId":"A123"}""")),
            base.copy(profile=base.profile.copy(identityVerification=base.profile.identityVerification!!.copy(openAlexAuthorId=null),orcidId="0000-0002-1825-0097"))
        )
        invalid.forEach { s ->
            assertNull(service.trustedId(s)); val r = service.collect(listOf(s),RecheckEvidenceMode.REFRESH,recheckTime).getValue(s.identityHash)
            assertEquals(listOf("NO_TRUSTED_ACADEMIC_ID"),r.reasonCodes); assertNull(r.data)
        }
        assertEquals("A123",service.trustedId(base))
        assertEquals("A123",service.trustedId(base.copy(profile=base.profile.copy(identityVerification=base.profile.identityVerification!!.copy(openAlexAuthorId="https://openalex.org/A123")))))
        assertEquals("0000-0002-1825-0097",service.trustedId(evidenceSnapshot(authorId=null,orcid="0000-0002-1825-0097")))
        Mockito.verifyNoInteractions(client)
    }
    @Test fun `REFRESH uses existing batch clients history priority and never writer shell`() {
        val client = Mockito.mock(OpenAlexDataSource::class.java); val service = academicService(client)
        val a = evidenceSnapshot(); val o = evidenceSnapshot(authorId=null,orcid="0000-0002-1825-0097",suffix="orcid")
        Mockito.`when`(client.batchEnrichByAuthorIds(listOf("A123"),RequestKind.HISTORY_ENRICHMENT))
            .thenReturn(mapOf("A123" to EnrichmentOutcome.Success(AuthorEnrichment(12,345,67))))
        Mockito.`when`(client.batchEnrichByOrcids(listOf("0000-0002-1825-0097"),RequestKind.HISTORY_ENRICHMENT))
            .thenReturn(mapOf("0000-0002-1825-0097" to EnrichmentOutcome.AmbiguousIdentity))
        val result = service.collect(listOf(a,o),RecheckEvidenceMode.REFRESH,recheckTime)
        assertEquals(12,result.getValue(a.identityHash).data!!.hIndex)
        assertEquals(listOf("AMBIGUOUS_IDENTITY"),result.getValue(o.identityHash).reasonCodes)
        assertNull(result.getValue(o.identityHash).data)
        Mockito.verify(client).batchEnrichByAuthorIds(listOf("A123"),RequestKind.HISTORY_ENRICHMENT)
        Mockito.verify(client).batchEnrichByOrcids(listOf("0000-0002-1825-0097"),RequestKind.HISTORY_ENRICHMENT)
        Mockito.verifyNoMoreInteractions(client)
    }
    @Test fun `actual OpenAlex transport returns metrics research fields and refuses multiple authors for same ORCID`() {
        val f = AcademicClientFixture(); val server = MockRestServiceServer.createServer(f.transport)
        server.expect(requestTo("https://api.openalex.org/authors?filter=openalex:A123&per_page=1"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(AcademicClientFixture.success,MediaType.APPLICATION_JSON))
        server.expect(requestTo("https://api.openalex.org/authors?filter=orcid:0000-0002-1825-0097&per_page=200"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(AcademicClientFixture.ambiguous,MediaType.APPLICATION_JSON))
        val s = evidenceSnapshot(); val o = evidenceSnapshot(authorId=null,orcid="0000-0002-1825-0097",suffix="orcid")
        val result = f.service.collect(listOf(s,o),RecheckEvidenceMode.REFRESH,recheckTime)
        assertEquals(12,result.getValue(s.identityHash).data!!.hIndex)
        assertEquals(listOf("3100"),result.getValue(s.identityHash).data!!.researchFieldIds)
        assertEquals(listOf("AMBIGUOUS_IDENTITY"),result.getValue(o.identityHash).reasonCodes)
        // Two HTTP requests, plus one denied rate-slot reservation before the clock advances.
        assertEquals(List(3) { RequestKind.HISTORY_ENRICHMENT },f.kinds)
        server.verify(); Mockito.verifyNoInteractions(f.europePmc,f.pdf,f.unpaywall)
    }
    @Test fun `frozen mismatched ORCID never binds and provider absence is diagnostic not qualification failure`() {
        val s = evidenceSnapshot(authorId=null,orcid="0000-0002-1825-0097")
        val service = DiscoveryRecheckAcademicEvidenceService(StaticListableBeanFactory().getBeanProvider(OpenAlexDataSource::class.java))
        assertEquals(listOf("PROVIDER_UNAVAILABLE"),service.collect(listOf(s),RecheckEvidenceMode.REFRESH,recheckTime).getValue(s.identityHash).reasonCodes)
        assertNull(service.trustedId(s.copy(profile=s.profile.copy(externalIds="""{"orcid":"0000-0001-5109-3700"}"""))))
    }
    @Test fun `bounded inputs and budget deferral return diagnostics without more provider work`() {
        val client = Mockito.mock(OpenAlexDataSource::class.java); val service = academicService(client)
        val s = evidenceSnapshot()
        Mockito.`when`(client.batchEnrichByAuthorIds(listOf("A123"),RequestKind.HISTORY_ENRICHMENT))
            .thenThrow(OpenAlexBudgetDeferredException(recheckTime.plusSeconds(60)))
        val r = service.collect(listOf(s),RecheckEvidenceMode.REFRESH,recheckTime).getValue(s.identityHash)
        assertEquals(listOf("BUDGET_DEFERRED"),r.reasonCodes); assertEquals(recheckTime.plusSeconds(60),r.retryAt); assertNull(r.data)
        assertThrows(IllegalArgumentException::class.java) { service.collect(List(101) { s },RecheckEvidenceMode.REUSE,recheckTime) }
        assertThrows(IllegalArgumentException::class.java) { service.collect(listOf(s,s),RecheckEvidenceMode.REUSE,recheckTime) }
        Mockito.verify(client).batchEnrichByAuthorIds(listOf("A123"),RequestKind.HISTORY_ENRICHMENT)
        Mockito.verifyNoMoreInteractions(client)
    }
}

/** Strict loopback transport used by both regression tests and the independent child07 launcher. */
internal fun academicDispatchCancellation(cancelDuringWait: Boolean): String {
    val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    val cancelled = java.util.concurrent.atomic.AtomicBoolean()
    var cancelAfterAuthor = !cancelDuringWait
    var cancelOnSleep = false
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    val base = "http://127.0.0.1:${server.address.port}"
    val mapper = jacksonObjectMapper()
    val author = mapper.readTree(AcademicClientFixture.AUTHOR) as com.fasterxml.jackson.databind.node.ObjectNode
    author.put("works_api_url", "$base/works")
    server.createContext("/") { exchange ->
        calls += exchange.requestURI.toString()
        val response = if (exchange.requestURI.path == "/authors") {
            if (cancelAfterAuthor) cancelled.set(true)
            """{"meta":{"count":1},"results":[$author]}"""
        } else {
            check(exchange.requestURI.path == "/works")
            """{"results":[{"title":"fixture title"}]}"""
        }
        val bytes = response.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }; exchange.close()
    }
    server.start()
    try {
        val properties = OpenAlexProperties(enabled = true, baseUrl = base, requestDelayMs = 0,
            enrichmentDelayMs = 0, maxRequestsPerSecond = 100.0, fetchWorksEnabled = true, fetchPatentsEnabled = true)
        val time = object : PolicyTimeSource {
            var current = recheckTime
            override fun now() = current
            override fun sleep(ms: Long) { current = current.plusMillis(ms); if (cancelOnSleep) cancelled.set(true) }
        }
        val store = InMemoryOpenAlexBudgetStore().also {
            it.reconcile(properties.accountScope, recheckTime.plusSeconds(86400), 10000, 10000, 10000, recheckTime)
        }
        val kinds = mutableListOf<RequestKind>()
        val observed = object : OpenAlexBudgetStore by store {
            override fun reserve(request: BudgetReserveRequest): BudgetReserveResult {
                kinds += request.kind; return store.reserve(request)
            }
        }
        val policy = OpenAlexRequestPolicy(properties, time, observed)
        fun client(p: OpenAlexProperties) = OpenAlexDataSource(RestTemplate(), p,
            Mockito.mock(EuropePmcDataSource::class.java), Mockito.mock(PdfEmailExtractor::class.java),
            Mockito.mock(UnpaywallClient::class.java), policy)
        if (cancelDuringWait) {
            client(properties.copy(fetchWorksEnabled = false, fetchPatentsEnabled = false))
                .batchEnrichByAuthorIds(listOf("A123"), RequestKind.HISTORY_ENRICHMENT)
            cancelOnSleep = true
        }
        val service = academicService(client(properties), properties)
        var refused = false
        try {
            service.collect(listOf(evidenceSnapshot(), evidenceSnapshot(authorId = null, orcid = "0000-0002-1825-0097", suffix = "second")),
                RecheckEvidenceMode.REFRESH, recheckTime, beforeDispatch = {
                    if (cancelled.get()) throw java.util.concurrent.CancellationException("fixture run cancelled")
                })
        } catch (_: java.util.concurrent.CancellationException) { refused = true }
        check(refused && calls.size == 1 && calls.single().startsWith("/authors"))
        val attemptsBefore = kinds.size
        try {
            service.collect(listOf(evidenceSnapshot()), RecheckEvidenceMode.REFRESH, recheckTime,
                beforeDispatch = { throw java.util.concurrent.CancellationException("still cancelled") })
            error("Cancelled retry must propagate")
        } catch (_: java.util.concurrent.CancellationException) { }
        check(kinds.size == attemptsBefore && calls.size == 1)
        check(kinds.all { it == RequestKind.HISTORY_ENRICHMENT })
        cancelAfterAuthor = false; cancelOnSleep = false; cancelled.set(false)
        val ordinary = client(properties).batchEnrichByAuthorIds(listOf("A123"), RequestKind.HISTORY_ENRICHMENT)
        check((ordinary.getValue("A123") as EnrichmentOutcome.Success).data.let {
            it.recentWorkTitles == listOf("fixture title") && it.patentTitles == listOf("fixture title") })
        check(calls.size == 4 && calls.drop(2).all { it.startsWith("/works") })
        return "cancelDuringWait=$cancelDuringWait wire=1 cancelledRetryWire=0 ordinaryWire=3 requestKind=HISTORY_ENRICHMENT"
    } finally { server.stop(0) }
}
