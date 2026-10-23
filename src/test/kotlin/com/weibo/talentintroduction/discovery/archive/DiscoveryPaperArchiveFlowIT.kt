package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.discovery.repository.*
import com.weibo.talentintroduction.discovery.sample.*
import com.weibo.talentintroduction.discovery.service.*
import com.weibo.talentintroduction.expert.service.ExpertIndexWriterService
import com.weibo.talentintroduction.task.service.TaskExecutionService
import com.weibo.talentintroduction.task.service.TaskProgressStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

@Configuration(proxyBeanMethods=false)
@EnableTransactionManagement
internal class ArchiveFlowTransactions

/** Loopback-only HTTP, disposable SQL and real files. Never starts the production Boot context. */
internal class ArchiveFlowFixture(val realDisk: Boolean = false) : AutoCloseable {
    val archive=ArchiveFixture(realDisk)
    val mapper=archive.mapper
    val requests=ConcurrentHashMap<String,AtomicInteger>()
    val responses=ConcurrentHashMap<String,ByteArray>()
    val statuses=ConcurrentHashMap<String,Int>()
    var onRequest: (String) -> Unit = {}
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
    val httpPool=Executors.newCachedThreadPool()
    val pool=Executors.newFixedThreadPool(4)
    val context=AnnotationConfigApplicationContext()
    val progress=Mockito.mock(TaskProgressStore::class.java)
    val props=ExpertDiscoveryProperties(includeRawScan=false,fetchConcurrency=4,maxPapersPerRun=100)
    val europeProps=EuropePmcProperties(baseUrl=url("/xml"),requestDelayMs=0,maxRetries=1,retryBackoffMs=1)
    val openProps=OpenAlexProperties(enabled=true,requestDelayMs=0)
    val sources: Map<String,AcademicDataSource>
    val discovery: ExpertDiscoveryService
    val queue: DiscoveryPaperQueueStore
    val now=Instant.parse("2026-10-23T12:00:00Z")
    init {
        server.executor=httpPool
        server.createContext("/") { exchange ->
            val path=exchange.requestURI.path
            requests.computeIfAbsent(path) { AtomicInteger() }.incrementAndGet()
            onRequest(path)
            val body=responses[path] ?: "not found".toByteArray()
            val code=statuses[path] ?: if (responses.containsKey(path)) 200 else 404
            exchange.responseHeaders.set("Content-Type",if(path.endsWith("pdf")) "application/pdf" else "application/xml; charset=UTF-8")
            exchange.sendResponseHeaders(code,body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        fun local(uri: java.net.URI): java.net.URI {
            // Arxiv's URL is fixed in production. Route synthetic IDs before the bounded transport is created.
            val target=if(uri.host=="arxiv.org") {
                val key=uri.path.substringAfterLast('/')
                java.net.URI(url(if(key in setOf("one","two")) "/$key.pdf" else "/same.pdf"))
            } else uri
            check(target.host=="127.0.0.1") { "External data-service traffic is forbidden in archive fixtures" }
            return target
        }
        val rest=RestTemplate()
        rest.interceptors.add(org.springframework.http.client.ClientHttpRequestInterceptor { request,bytes,execution ->
            check(request.uri.host=="127.0.0.1") { "External data-service traffic is forbidden in archive fixtures" }
            execution.execute(request,bytes)
        })
        val transport=object: BoundedHttpExecutor {
            override fun <T: Any> getForObject(base: RestTemplate,url: String,responseType: Class<T>,connectCapMs: Long,readCapMs: Long,deadline: Instant?): T? =
                BoundedFulltextHttp.getForObject(base,local(java.net.URI(url)).toString(),responseType,connectCapMs,readCapMs,deadline)
            override fun <T> execute(base: RestTemplate,uri: java.net.URI,connectCapMs: Long,readCapMs: Long,deadline: Instant?,
                responseExtractor: org.springframework.web.client.ResponseExtractor<T>): T? =
                BoundedFulltextHttp.execute(base,local(uri),connectCapMs,readCapMs,deadline,responseExtractor)
        }
        val pdf=PdfEmailExtractor(rest,PlainTextEmailExtractor(),PdfExtractionProperties(maxPdfSizeBytes=2048),transport)
        val europe=Mockito.spy(EuropePmcDataSource(rest,europeProps))
        val unpaywall=Mockito.mock(UnpaywallClient::class.java)
        Mockito.`when`(unpaywall.isConfigured()).thenReturn(true)
        Mockito.`when`(unpaywall.findPdfUrl(Mockito.anyString())).thenReturn(url("/same.pdf"))
        val open=Mockito.spy(OpenAlexDataSource(rest,openProps,europe,pdf,unpaywall))
        val pmc=Mockito.spy(PmcOaDataSource(rest,PmcOaProperties(baseUrl=url("/pmc"),requestDelayMs=0)))
        val crossref=Mockito.spy(CrossrefDataSource(rest,CrossrefProperties(requestDelayMs=0),unpaywall,pdf))
        val core=Mockito.spy(CoreDataSource(rest,CoreProperties(apiKey="synthetic",requestDelayMs=0),PlainTextEmailExtractor(),pdf))
        val arxiv=Mockito.spy(ArxivDataSource(rest,ArxivProperties(requestDelayMs=0),pdf))
        sources=listOf(europe,pmc,open,crossref,core,arxiv).associateBy { it.sourceName }
        fun register(name: String,value: Any) { context.beanFactory.registerSingleton(name,value) }
        register("archiveService",archive.service)
        register("objectMapper",mapper)
        register("expertDiscoveryProperties",props)
        register("elasticsearchProperties",ElasticsearchProperties(url("/never-es"),"synthetic","synthetic","raw","candidate","application"))
        register("openAlexProperties",openProps)
        register("europePmcProperties",europeProps)
        register("restTemplate",rest)
        register("progressStore",progress)
        register("discoveryFetchExecutor",pool)
        register("jdbcTemplate",ArchiveMysqlFixture.jdbc)
        register("transactionManager",DataSourceTransactionManager(ArchiveMysqlFixture.dataSource))
        sources.forEach { (name,source) -> register(name,source) }
        ExpertDiscoveryService::class.primaryConstructor!!.parameters.forEach { parameter ->
            val type=parameter.type.jvmErasure.java
            if (!parameter.isOptional && type != org.springframework.beans.factory.ObjectProvider::class.java &&
                context.beanFactory.getBeanNamesForType(type).isEmpty()) register(type.simpleName,Mockito.mock(type))
        }
        context.register(ArchiveFlowTransactions::class.java,DiscoveryPaperArchiveCapture::class.java,
            ExpertDiscoveryService::class.java,DiscoveryPaperQueueRepository::class.java)
        context.refresh()
        discovery=context.getBean(ExpertDiscoveryService::class.java)
        queue=context.getBean(DiscoveryPaperQueueStore::class.java)
        // Production component registration and constructor injection, not a null capture bypass.
        check(context.getBean(DiscoveryPaperArchiveCapture::class.java) ===
            ExpertDiscoveryService::class.java.getDeclaredField("archiveCapture").also { it.isAccessible=true }.get(discovery))
    }
    fun url(path: String)="http://127.0.0.1:${server.address.port}$path"
    fun paper(source: String,key: String=source,download: String?=null,pmc: String?=null,text: String?=null)=
        PaperMetadata(pmc,null,"10.fixture/$key",key,2026,null,emptyList(),source,fullText=text,downloadUrl=download)
    fun count(path: String)=requests[path]?.get() ?: 0
    fun totalRequests()=requests.values.sumOf { it.get() }
    fun envelope(paper: PaperMetadata): QueuedItemEnvelope {
        val method=ExpertDiscoveryService::class.java.getDeclaredMethod("envelopeForPaper",String::class.java,PaperMetadata::class.java,Int::class.javaPrimitiveType)
        method.isAccessible=true
        return method.invoke(discovery,paper.source,paper,Int.MAX_VALUE) as QueuedItemEnvelope
    }
    fun extract(paper: PaperMetadata,execution: Long=1): QueuedItemExtraction = discovery.extractQueuedItem(envelope(paper),
        PaperSearchCriteria(sources=listOf(paper.source)),2,65536,DiscoverySampleContext(execution,paper.source,DiscoverySampleEntry.QUEUE,null,execution))
    fun sync(papers: List<PaperMetadata>,execution: Long=1): DiscoveryResult {
        val source=sources.getValue(papers.first().source)
        val page=PaperSearchResult(papers,null,papers.size.toLong())
        if(source is CoreDataSource) Mockito.doReturn(CoreDataSource.CoreSearchPage(page,false,0)).`when`(source)
            .searchCorePage(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        else Mockito.doReturn(page).`when`(source).searchPapers(Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria())
        return discovery.discover(PaperSearchCriteria(sources=listOf(source.sourceName),pageSize=papers.size),"fixture",execution,false)
    }
    fun body(attempt: ArchiveAttempt): ByteArray {
        val bytes=ByteArrayOutputStream()
        archive.service.withContent(attempt.recordId,attempt.id) { _,input -> input.copyTo(bytes) }
        return bytes.toByteArray()
    }
    fun pipeline(): DiscoveryPipelineService = DiscoveryPipelineService(queue,discovery,Mockito.mock(TaskExecutionService::class.java),progress,
        props,mapper,OpenAlexRequestPolicy(openProps),java.util.concurrent.Executor { it.run() },java.util.concurrent.Executor { it.run() },pool,
        object: PipelineTimeSource { override fun now()=this@ArchiveFlowFixture.now })
    fun seedQueue(paper: PaperMetadata,cache: String?=null): JobRow {
        val jdbc=ArchiveMysqlFixture.jdbc
        queue.ensurePipeline(now)
        jdbc.update("UPDATE discovery_pipeline SET desired_state='RUNNING',phase='RUNNING',generation=1,active_count=1,payload_bytes=1000 WHERE id=1")
        jdbc.update("INSERT INTO discovery_collection_stream (id,pipeline_id,query_hash,source,epoch,cursor_value,cursor_state) VALUES (1,1,?,?,0,'unchanged','ACTIVE')", "a".repeat(64),paper.source)
        val e=envelope(paper)
        jdbc.update("INSERT INTO discovery_paper_job (id,stream_id,item_key,identity_quality,unit,payload_version,priority,metadata_json,extraction_json,payload_bytes,status,attempts,next_attempt_at,lease_token,lease_until,generation) VALUES (1,1,?,?,?,1,1,?,?,1000,'RUNNING',2,?,'lease',?,1)",
            e.itemKey,e.identityQuality,e.unit,e.payloadJson,cache,LocalDateTime.ofInstant(now,ZoneOffset.UTC),LocalDateTime.ofInstant(now.plusSeconds(300),ZoneOffset.UTC))
        return job()
    }
    fun job(): JobRow {
        // The repository mapper remains the sole job reader; nextDueJobs intentionally excludes RUNNING.
        val method=DiscoveryPaperQueueRepository::class.java.getDeclaredMethod("jobRow",java.sql.ResultSet::class.java)
        method.isAccessible=true
        val target=DiscoveryPaperQueueRepository(ArchiveMysqlFixture.jdbc)
        return ArchiveMysqlFixture.jdbc.query("SELECT * FROM discovery_paper_job WHERE id=1",org.springframework.jdbc.core.RowMapper { rs,_ -> method.invoke(target,rs) as JobRow }).single()
    }
    fun process(job: JobRow): Any {
        val type=Class.forName("com.weibo.talentintroduction.discovery.service.DiscoveryPipelineService\$WindowState")
        val state=type.declaredConstructors.single().also { it.isAccessible=true }.newInstance(
            PaperSearchCriteria(sources=listOf(sources.keys.first { it==queue.findStreamById(job.streamId)!!.source })),"owner",1L,now.plusSeconds(300),now)
        type.getDeclaredField("executionId").also { it.isAccessible=true }.get(state).let { (it as AtomicLong).set(99) }
        type.getDeclaredField("verification").also { it.isAccessible=true }.set(state,DiscoveryVerificationExecution(99))
        val method=DiscoveryPipelineService::class.java.getDeclaredMethod("processJob",type,JobRow::class.java,String::class.java)
        method.isAccessible=true
        method.invoke(pipeline(),state,job,"lease")
        return state
    }
    override fun close() {
        context.close(); server.stop(0); httpPool.shutdownNow(); pool.shutdownNow(); archive.close()
    }
    companion object {
        private var queueSchema=false
        fun clean() {
            ArchiveMysqlFixture.clean()
            if (!queueSchema) {
                ResourceDatabasePopulator(ClassPathResource("db/migration/V133__create_discovery_paper_queue.sql")).execute(ArchiveMysqlFixture.dataSource)
                queueSchema=true
            }
            ArchiveMysqlFixture.jdbc.update("DELETE FROM discovery_paper_job")
            ArchiveMysqlFixture.jdbc.update("DELETE FROM discovery_collection_stream")
            ArchiveMysqlFixture.jdbc.update("DELETE FROM discovery_pipeline")
        }
    }
}

class DiscoveryPaperArchiveFlowIT {
    @BeforeEach fun clean()=ArchiveFlowFixture.clean()
    @Test fun `all six registered sources archive exact bytes independently of samples`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable()
            val pdf=f.archive.pdf(1024)
            val xml="<article><front><article-meta/></front><body>synthetic XML</body></article>".toByteArray()
            f.responses["/same.pdf"]=pdf; f.responses["/xml/PMC1/fullTextXML"]=xml; f.responses["/pmc/efetch.fcgi"]=xml
            val papers=listOf(f.paper("OPENALEX",download=f.url("/same.pdf")),f.paper("EUROPE_PMC",pmc="PMC1"),
                f.paper("PMC_OA",pmc="1"),f.paper("CROSSREF"),f.paper("CORE",text="synthetic CORE original"),
                f.paper("ARXIV",download=f.url("/same.pdf")))
            papers.forEachIndexed { index,paper ->
                if (paper.source in setOf("EUROPE_PMC","PMC_OA")) f.sync(listOf(paper),index+1L)
                else assertTrue(f.extract(paper,index+1L) is QueuedItemExtraction.Extracted)
            }
            assertEquals(papers.map { it.source }.toSet(),f.archive.repository.records().map { it.sourceName }.toSet())
            val attempts=f.archive.repository.records().flatMap { f.archive.repository.attempts(it.id) }
            assertEquals(6,attempts.size); assertTrue(attempts.all { it.status==ArchiveAttemptStatus.SAVED })
            attempts.forEach { a ->
                val expected=when(a.kind) { ArchiveAttemptKind.PMC_XML,ArchiveAttemptKind.PMC_OA_XML -> xml
                    ArchiveAttemptKind.EMBEDDED_TEXT -> "synthetic CORE original".toByteArray(); else -> pdf }
                assertArrayEquals(expected,f.body(a)); assertEquals(hash(expected),a.blobSha256)
            }
            assertEquals(5,f.totalRequests())
            assertFalse(Files.exists(f.archive.directory.resolve("legacy-samples")))
            assertEquals(3L,f.archive.service.stats().storage.fileCount)
        }
    }
    @Test fun `CORE embedded text then PDF fallback and no URL record`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable(); f.responses["/same.pdf"]=f.archive.pdf()
            f.sync(listOf(f.paper("CORE",text="no addresses",download=f.url("/same.pdf"))))
            val record=f.archive.repository.records().single()
            assertEquals(listOf(ArchiveAttemptKind.EMBEDDED_TEXT,ArchiveAttemptKind.PDF_URL),f.archive.repository.attempts(record.id).map { it.kind })
            f.extract(f.paper("OPENALEX","no-url"),2)
            val noUrl=f.archive.repository.records().last()
            assertTrue(f.archive.repository.attempts(noUrl.id).isEmpty())
            assertEquals("NO_PMC_ID",f.mapper.readTree(noUrl.outcomeJson).path("first").path("outcome").path("failureReason").asText())
            assertEquals(1,f.totalRequests())
        }
    }
    @Test fun `XML retry fallback failures oversize and original HTML response are retained correctly`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable()
            f.responses["/xml/RETRY/fullTextXML"]="<article><body>retry success</body></article>".toByteArray()
            f.onRequest={ path -> if(path=="/xml/RETRY/fullTextXML") f.statuses[path]=if(f.count(path)==1) 503 else 200 }
            f.sync(listOf(f.paper("EUROPE_PMC",pmc="RETRY")))
            assertEquals(2,f.count("/xml/RETRY/fullTextXML"))
            val retry=f.archive.repository.records().single()
            assertEquals(listOf(ArchiveAttemptStatus.DOWNLOAD_FAILED,ArchiveAttemptStatus.SAVED),f.archive.repository.attempts(retry.id).map { it.status })
            f.responses["/same.pdf"]=f.archive.pdf()
            val p=f.paper("OPENALEX","fallback",download=f.url("/missing")).copy(candidateDownloadUrls=listOf(f.url("/missing2"),f.url("/same.pdf")))
            f.extract(p,2)
            val attempts=f.archive.repository.attempts(f.archive.repository.records().last().id)
            assertEquals(listOf(ArchiveAttemptStatus.DOWNLOAD_FAILED,ArchiveAttemptStatus.DOWNLOAD_FAILED,ArchiveAttemptStatus.SAVED),attempts.map { it.status })
            f.responses["/large.pdf"]=f.archive.pdf(4096)
            f.extract(f.paper("CORE","large",download=f.url("/large.pdf")),3)
            assertEquals(ArchiveAttemptStatus.TOO_LARGE,f.archive.repository.attempts(f.archive.repository.records().last().id).single().status)
            val html="<html><body>original layout untouched</body></html>".toByteArray()
            f.responses["/html"]=html
            f.extract(f.paper("CORE","html",download=f.url("/html")),4)
            assertArrayEquals(html,f.body(f.archive.repository.attempts(f.archive.repository.records().last().id).single()))
        }
    }
    @Test fun `ten full downloads share blobs but keep every saved attempt and task identity`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable()
            repeat(10) { index ->
                val variant=if(index<3) 0 else index-2
                f.responses["/body$index.pdf"]=f.archive.pdf(fill=(65+variant).toByte())
            }
            val executor=Executors.newFixedThreadPool(2)
            try {
                val calls=(0..9).map { index -> executor.submit {
                    f.extract(f.paper("CORE","task$index",download=f.url("/body$index.pdf")),index+1L)
                } }
                calls.forEach { it.get(30,TimeUnit.SECONDS) }
            } finally { executor.shutdownNow() }
            val counts=f.archive.service.stats().counts
            assertEquals(10L,counts.savedAttempts); assertEquals(8L,counts.newFiles); assertEquals(2L,counts.reusedAttempts)
            assertEquals((1L..10L).toSet(),f.archive.repository.records().map { it.executionId }.toSet())
        }
    }
    @Test fun `SYNC blocked page awaits all workers consumes zero and keeps checkpoint`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable(); f.responses["/one.pdf"]=f.archive.pdf(); f.responses["/two.pdf"]=f.archive.pdf(fill=66)
            val downloads=CountDownLatch(2); val slowDone=AtomicBoolean(false)
            f.onRequest={ path -> if(path in listOf("/one.pdf","/two.pdf")) { downloads.countDown(); check(downloads.await(5,TimeUnit.SECONDS)); if(path=="/one.pdf") { Thread.sleep(200); slowDone.set(true) } } }
            f.archive.service.failpoint={ point -> if(point=="BEFORE_RENAME" && !slowDone.get()) throw java.io.IOException("synthetic storage failure") }
            val cursor=f.context.getBean(DiscoverySourceCursorRepository::class.java)
            val writer=f.context.getBean(ExpertIndexWriterService::class.java)
            val error=assertThrows(PaperArchiveBlockedException::class.java) {
                f.sync(listOf(f.paper("ARXIV","one",f.url("/one.pdf")),f.paper("ARXIV","two",f.url("/two.pdf"))))
            }
            assertTrue(error.message!!.startsWith("论文原文留存受阻，本页尚未消费"))
            assertTrue(slowDone.get()); assertEquals(2,f.totalRequests())
            assertTrue(Mockito.mockingDetails(writer).invocations.isEmpty())
            assertTrue(Mockito.mockingDetails(cursor).invocations.none { it.method.name=="save" })
            val progress=Mockito.mockingDetails(f.progress).invocations.filter { it.method.name=="update" }.last().arguments[1] as com.weibo.talentintroduction.task.service.TaskProgress
            assertEquals(0L,progress.processedCount)
            f.archive.service.failpoint={}
            val result=f.sync(listOf(f.paper("ARXIV","one",f.url("/one.pdf")),f.paper("ARXIV","two",f.url("/two.pdf"))),2)
            assertEquals(2,result.stats.totalPapers)
        }
    }
    @Test fun `QUEUE storage gate uses real CAS PENDING plus sixty without attempts or cache loss then recovers`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable(); f.responses["/same.pdf"]=f.archive.pdf()
            val original=f.seedQueue(f.paper("CORE",download=f.url("/same.pdf")))
            f.archive.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,0,0) }
            f.process(original)
            val pending=f.job()
            assertEquals(QueueJobStatus.PENDING,pending.status); assertEquals(2,pending.attempts)
            assertEquals(f.now.plusSeconds(60),pending.nextAttemptAt); assertNull(pending.extractionJson)
            assertEquals("ARCHIVE_STORAGE_BLOCKED",pending.lastError); assertEquals(0,f.totalRequests())
            assertFalse(f.queue.saveExtraction(1,"stale-lease",1,"{}",2,f.now))
            f.archive.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,80*ARCHIVE_GIB,70*ARCHIVE_GIB) }
            assertTrue(f.queue.claimJob(1,"lease",f.now.plusSeconds(500),1,f.now.plusSeconds(60)))
            f.process(f.job())
            assertEquals(QueueJobStatus.SUCCEEDED,f.job().status); assertEquals(2,f.job().attempts)
            assertEquals(1,f.totalRequests()); assertEquals(1L,f.archive.service.stats().counts.savedAttempts)
            assertEquals("unchanged",f.queue.findStreamById(1)!!.cursorValue)
        }
    }
    @Test fun `cached queue consumption never downloads or forges archive and cancellation retains bytes`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable()
            val cache=f.mapper.writeValueAsString(EmailExtractionOutcome(emptyList(),"PDF_PARSE",identityRuleVersion=com.weibo.talentintroduction.expert.domain.DiscoveryIdentity.EXTRACTION_VERSION))
            f.seedQueue(f.paper("CORE",download=f.url("/not-needed.pdf")),cache)
            f.archive.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,0,0) }
            f.process(f.job())
            assertEquals(QueueJobStatus.SUCCEEDED,f.job().status); assertEquals(cache,f.job().extractionJson)
            assertEquals(0,f.totalRequests()); assertTrue(f.archive.repository.records().isEmpty())
            f.archive.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,80*ARCHIVE_GIB,70*ARCHIVE_GIB) }
            f.responses["/same.pdf"]=f.archive.pdf()
            f.onRequest={ Mockito.`when`(f.progress.isCancelled("EXPERT_DISCOVERY")).thenReturn(true) }
            val result=f.sync(listOf(f.paper("ARXIV",download=f.url("/same.pdf"))),100)
            assertTrue(result.wasCancelled); assertEquals(0,result.stats.totalPapers)
            val a=f.archive.repository.attempts(f.archive.repository.records().single().id).single()
            assertEquals(ArchiveAttemptStatus.SAVED,a.status); assertArrayEquals(f.archive.pdf(),f.body(a))
            assertTrue(Mockito.mockingDetails(f.context.getBean(ExpertIndexWriterService::class.java)).invocations.isEmpty())
        }
    }
    @Test fun `toggle off during HTTP still stores response freezes first and updates latest`() {
        ArchiveFlowFixture().use { f ->
            f.archive.enable(); f.responses["/same.pdf"]=f.archive.pdf()
            f.onRequest={ val s=f.archive.service.settings(); f.archive.service.updateSettings(ArchiveSettingsRequest(s.revision,false,s.maxBytes,s.minFreeBytes)) }
            val p=f.paper("CORE",download=f.url("/same.pdf"))
            f.extract(p)
            val record=f.archive.repository.records().single(); val first=f.mapper.readTree(record.outcomeJson).path("first")
            f.extract(p,2); assertEquals(1,f.archive.repository.records().size)
            f.onRequest={}; f.archive.enable(); f.extract(p)
            val json=f.mapper.readTree(f.archive.repository.record(record.id)!!.outcomeJson)
            assertEquals(first,json.path("first")); assertNotEquals(first.path("attemptIds"),json.path("latest").path("attemptIds"))
            assertEquals(2,f.archive.repository.attempts(record.id).size)
        }
    }
    companion object {
        fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

/** A separate executable smoke, not a relabelled JUnit run. */
object ArchiveCaptureSmokeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        try {
            ArchiveFlowFixture.clean()
            ArchiveFlowFixture(realDisk=true).use { f ->
                f.archive.enable()
                val bytes=f.archive.pdf(); f.responses["/smoke.pdf"]=bytes
                val paper=f.paper("CORE",download=f.url("/smoke.pdf"))
                f.extract(paper)
                val r=f.archive.repository.records().single()
                val first=f.mapper.readTree(r.outcomeJson).path("first")
                f.extract(paper)
                val latest=f.mapper.readTree(f.archive.repository.record(r.id)!!.outcomeJson)
                check(latest.path("first")==first)
                check(latest.path("latest").path("attemptIds")!=first.path("attemptIds"))
                val a=f.archive.repository.attempts(r.id).last()
                check(f.body(a).contentEquals(bytes)); check(a.blobSha256==DiscoveryPaperArchiveFlowIT.hash(bytes))
                f.seedQueue(paper)
                val before=f.totalRequests()
                f.archive.service.diskProbe={ ArchiveDisk(100*ARCHIVE_GIB,0,0) }
                f.process(f.job())
                check(f.job().status==QueueJobStatus.PENDING && f.job().attempts==2 && f.job().nextAttemptAt==f.now.plusSeconds(60))
                check(f.totalRequests()==before && f.queue.findStreamById(1)!!.cursorValue=="unchanged")
                println("CAPTURE_SMOKE bytes=${bytes.size} sha256=${a.blobSha256} first=frozen latest=updated savedAttempts=2 sharedFiles=1 blockedHTTP=0 queue=PENDING attempts=2 delay=60s cursor=unchanged")
            }
        } finally { ArchiveMysqlFixture.stop() }
    }
}
