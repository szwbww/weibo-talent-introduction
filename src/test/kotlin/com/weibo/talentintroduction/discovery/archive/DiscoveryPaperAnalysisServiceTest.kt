package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import com.weibo.talentintroduction.config.BoundedHttpExecutor
import com.weibo.talentintroduction.config.PdfExtractionProperties
import com.weibo.talentintroduction.discovery.controller.DiscoveryPaperArchiveController
import com.weibo.talentintroduction.discovery.domain.PaperAuthor
import com.weibo.talentintroduction.discovery.service.PdfEmailExtractor
import com.weibo.talentintroduction.discovery.service.PlainTextEmailExtractor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class AnalysisFixture(val f: ArchiveFixture) {
    val network = Mockito.mock(RestTemplate::class.java)
    val bounded = Mockito.mock(BoundedHttpExecutor::class.java)
    val extractor = PaperQualificationEvidenceExtractor()
    val analysis = DiscoveryPaperAnalysisService(f.service,PdfEmailExtractor(network,PlainTextEmailExtractor(),PdfExtractionProperties(),bounded),extractor,f.mapper)
    val controller = DiscoveryPaperArchiveController(f.service,analysis)
    fun paper(key: String): ArchiveRecord = f.service.beginRecord(1,"OPENALEX",key,f.mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(
        mapOf("schemaVersion" to 1,"title" to key,"pubYear" to 2023,"authors" to QualificationFixtures.authors)))
    fun body(p: ArchiveRecord, value: String = QualificationFixtures.biography) = f.body(p,value.toByteArray(),ArchiveAttemptKind.EMBEDDED_TEXT,"text/plain")
    fun baseline(p: ArchiveRecord, attempts: List<Long>, emails: String = "[]") {
        f.service.finishExtraction(p.id,f.mapper.readTree("""{"schemaVersion":1,"first":{"capturedAt":"2023-01-01T00:00:00Z","attemptIds":$attempts,"outcome":{"emails":$emails}},"latest":{"outcome":{"emails":[]}}}"""))
    }
    fun post(p: ArchiveRecord, force: Boolean = false) = controller.analysis(p.id,f.mapper.readTree("""{"force":$force}"""))
}

class DiscoveryPaperAnalysisServiceTest {
    @BeforeEach fun clean() = ArchiveMysqlFixture.clean()
    @Test fun `pure kernel equals explicit saved result and never changes baseline or body`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("pure"); val b = a.body(p)
            a.baseline(p, listOf(b.id))
            val before = f.repository.record(p.id)!!
            val analyzer = PaperBodyAnalyzer(PdfEmailExtractor(a.network,PlainTextEmailExtractor(),PdfExtractionProperties(),a.bounded),a.extractor,f.mapper)
            val time = java.time.Instant.parse("2026-10-24T00:00:00Z")
            val pure = f.service.analysisLease(p.id).use { lease ->
                analyzer.compute(lease.bodies.map { PaperBodyInput(it.hash,it.attempt.kind,it.input.readBytes()) },
                    f.mapper.readTree(before.metadataJson),f.mapper.readTree(before.outcomeJson).path("first"),
                    lease.fileSet.map { it.first }.toSet(),time)
            }
            assertEquals(before,f.repository.record(p.id))
            val explicit = a.post(p).deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("analyzedAt",time.toString())
            assertEquals(f.mapper.valueToTree<JsonNode>(pure),explicit)
            assertEquals(before.revision+1,f.repository.record(p.id)!!.revision)
            assertEquals(before.outcomeJson,f.repository.record(p.id)!!.outcomeJson)
            assertEquals(b.blobSha256,f.repository.attempt(b.id)!!.blobSha256)
            Mockito.verifyNoInteractions(a.network,a.bounded)
        }
    }
    @Test fun `byte format and implementation versions invalidate old cache without rewriting first`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("old-pdf")
            val b = f.body(p,QualificationFixtures.pdf(),ArchiveAttemptKind.PDF_URL,"text/plain")
            a.baseline(p,listOf(b.id))
            val before = f.repository.record(p.id)!!
            val result = a.post(p)
            assertEquals(2,result.path("analysisVersion").asInt())
            assertEquals(1,result.path("coverage").path("scannedPages").asInt())
            assertEquals(PaperContentFormat.VERSION,result.path("parserVersion").path("format").asInt())
            assertEquals(PaperQualificationEvidenceExtractor.VERSION,result.path("parserVersion").path("qualification").asInt())
            val old = result.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("analysisVersion",1)
            f.service.saveAnalysis(p.id,f.repository.record(p.id)!!.revision,old)
            var parsed = false
            a.analysis.beforeParse = { parsed = true }
            assertEquals(2,a.post(p).path("analysisVersion").asInt()); assertTrue(parsed)
            for (key in listOf("format", "qualification")) {
                val stale = result.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
                (stale.path("parserVersion") as com.fasterxml.jackson.databind.node.ObjectNode).put(key,0)
                f.service.saveAnalysis(p.id,f.repository.record(p.id)!!.revision,stale)
                parsed = false
                a.post(p); assertTrue(parsed,key)
            }
            assertEquals(before.outcomeJson,f.repository.record(p.id)!!.outcomeJson)
            assertEquals(b.blobSha256,f.repository.attempt(b.id)!!.blobSha256)
            val html = "<html>not PDF</html>".toByteArray()
            assertEquals("HTML",PaperContentFormat.detect(html,ArchiveAttemptKind.PDF_URL))
        }
    }
    @Test fun `actual files SQL controller first immutable cache force and deletion retention`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("lifecycle"); val b = a.body(p); a.baseline(p,listOf(b.id))
            val before = f.repository.record(p.id)!!
            val result = a.post(p)
            assertEquals(3,result.path("qualificationFacts").size()); assertEquals("COMPLETE",result.path("baselineCoverage").asText())
            assertEquals(0,result.path("deltaAddedBindings").asInt()); assertFalse(result.path("hasGain").asBoolean())
            val after = f.repository.record(p.id)!!
            assertEquals(before,after.copy(analysisJson=before.analysisJson,revision=before.revision,updatedAt=before.updatedAt))
            a.analysis.beforeParse = { error("cache must not parse") }
            assertEquals(result,a.post(p)); assertEquals(after,f.repository.record(p.id))
            a.analysis.beforeParse = {}; a.post(p,true); assertEquals(after.revision+1,f.repository.record(p.id)!!.revision)
            val preview = f.service.deletePreview(listOf(p.id)); f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash))
            assertNotNull(a.controller.detail(p.id).analysis)
            assertEquals(410,assertThrows(ArchiveException::class.java) { a.post(p) }.httpStatus)
            Mockito.verifyNoInteractions(a.network,a.bounded)
        }
    }
    @Test fun `missing baseline and partial anchored inputs have null delta and cannot pass gain query`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("no-baseline"); a.body(p)
            assertTrue(a.post(p).path("delta").isNull)
            val q = a.paper("partial"); val body = a.body(q); a.baseline(q,listOf(body.id,99999L))
            val result = a.post(q)
            assertEquals("PARTIAL",result.path("baselineCoverage").asText()); assertEquals(99999L,result.path("missingBodies")[0].asLong())
            assertTrue(result.path("delta").isNull)
            assertEquals(0L,f.service.list(ArchiveQuery(hasGain=true)).total)
            assertEquals(0L,f.service.list(ArchiveQuery(hasGain=false)).total)
        }
    }
    @Test fun `first exact attempt ids exclude later sources and complete positive negative projections`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("gain")
            val body = a.body(p,"Alice Smith: alice@fixture.edu"); a.baseline(p,listOf(body.id))
            a.body(p,"Bob Jones: bob@fixture.edu")
            val result = a.post(p)
            assertEquals(listOf(body.blobSha256),result.path("inputBodyHashes").map { it.asText() })
            assertEquals(1,result.path("after").size()); assertEquals(1,result.path("deltaAddedBindings").asInt())
            assertEquals(1L,f.service.list(ArchiveQuery(hasGain=true)).total)
            val summary = f.service.list(ArchiveQuery()).items.single().analysisSummary!!
            assertFalse(summary.has("qualificationFacts")); assertFalse(summary.has("after")); assertEquals(1,summary.path("deltaAddedBindings").asInt())
            val q = a.paper("no-gain"); val qb = a.body(q); a.baseline(q,listOf(qb.id)); a.post(q)
            assertEquals(1L,f.service.list(ArchiveQuery(hasGain=false)).total)
            assertEquals(1L,f.service.list(ArchiveQuery(hasGain=true)).total)
        }
    }
    @Test fun `concurrent analysis is 409 and deletion reports busy during parse lease`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("concurrent"); val b = a.body(p); a.baseline(p,listOf(b.id))
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val pool = Executors.newSingleThreadExecutor()
            a.analysis.beforeParse = { entered.countDown(); check(release.await(20,TimeUnit.SECONDS)) }
            try {
                val future = pool.submit<JsonNode> { a.post(p) }; assertTrue(entered.await(20,TimeUnit.SECONDS))
                assertEquals("ANALYSIS_RUNNING",assertThrows(ArchiveException::class.java) { a.post(p) }.code)
                val preview = f.service.deletePreview(listOf(p.id)); assertEquals(0,preview.deletableFiles)
                assertTrue("BUSY" in preview.items.single().reasonCodes)
                assertEquals(0,f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash)).deletedFiles)
                release.countDown(); assertEquals(3,future.get(20,TimeUnit.SECONDS).path("qualificationFacts").size())
            } finally { release.countDown(); pool.shutdownNow() }
        }
    }
    @Test fun `revision new source and physical loss refuse final CAS without modifying first`() {
        for (change in listOf("revision","source","physical")) {
            ArchiveMysqlFixture.clean()
            ArchiveFixture().use { f ->
                f.enable(); val a = AnalysisFixture(f); val p = a.paper(change); val b = a.body(p); a.baseline(p,listOf(b.id))
                val first = f.repository.record(p.id)!!.outcomeJson
                a.analysis.beforeParse = {
                    when (change) {
                        "revision" -> f.protect(p.id,true)
                        "source" -> a.body(p,"new source")
                        else -> Files.delete(f.root.resolve("blobs").resolve(b.blobSha256!!.take(2)).resolve("${b.blobSha256}.bin"))
                    }
                }
                assertEquals("REVISION_CONFLICT",assertThrows(ArchiveException::class.java) { a.post(p) }.code)
                assertNull(f.repository.record(p.id)!!.analysisJson); assertEquals(first,f.repository.record(p.id)!!.outcomeJson)
            }
        }
    }
    @Test fun `source conflict merges to unknown and invalid request has no side effects`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("conflict")
            val b = a.body(p,"Alice Smith: shared@fixture.edu"); val c = a.body(p,"Bob Jones: shared@fixture.edu"); a.baseline(p,listOf(b.id,c.id))
            val result = a.post(p)
            assertTrue(result.path("after")[0].path("authorName").isNull); assertEquals(2,result.path("conflicts").size())
            for (request in listOf("{\"force\":1}","{\"url\":\"https://fixture.invalid\"}","[]")) {
                assertEquals(400,assertThrows(ArchiveException::class.java) { a.controller.analysis(p.id,f.mapper.readTree(request)) }.httpStatus)
            }
            Mockito.verifyNoInteractions(a.network,a.bounded)
        }
    }
    @Test fun `PDF analysis uses real byte parser while scan without text is not not-met`() {
        ArchiveFixture().use { f ->
            f.enable(); val a = AnalysisFixture(f); val p = a.paper("pdf")
            val b = f.body(p,QualificationFixtures.pdf()); a.baseline(p,listOf(b.id))
            assertEquals(3,a.post(p).path("qualificationFacts").size())
            val q = a.paper("scan"); val c = f.body(q,QualificationFixtures.pdf(lines=emptyList())); a.baseline(q,listOf(c.id))
            assertEquals("NOT_FOUND",a.post(q).path("coverage").path("status").asText())
            Mockito.verifyNoInteractions(a.network,a.bounded)
        }
    }
}

/** Separate runtime entry: real disposable SQL, actual files and controller calls, never a Spring application. */
object PaperAnalysisSmokeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        try {
            ArchiveMysqlFixture.clean()
            ArchiveFixture(realDisk=true).use { f ->
                f.enable(); val a = AnalysisFixture(f); val p = a.paper("analysis-smoke"); val b = a.body(p); a.baseline(p,listOf(b.id))
                val first = f.repository.record(p.id)!!.outcomeJson
                val result = a.post(p)
                val facts = result.path("qualificationFacts")
                check(facts.map { it.path("kind").asText() } == listOf("ASSISTANT_PROFESSOR","COMPLETED_DOCTORATE","DOCTORAL_STUDENT"))
                check(facts.all { it.path("paperYear").asInt()==2023 && it.path("bodySha256").asText()==b.blobSha256 && it.path("paragraph").asInt()>0 && it.path("page").isNull && it.path("temporalScope").asText()=="PUBLICATION_TIME" })
                val revision = f.repository.record(p.id)!!.revision
                a.analysis.beforeParse = { error("cache parsed") }; check(a.post(p)==result); check(f.repository.record(p.id)!!.revision==revision)
                a.analysis.beforeParse = {}
                check(f.repository.record(p.id)!!.outcomeJson==first)
                val missing = a.paper("missing-baseline-smoke"); a.body(missing, QualificationFixtures.biography + "\nDistinct missing-baseline source."); check(a.post(missing).path("delta").isNull)
                val preview = f.service.deletePreview(listOf(p.id)); check(f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash)).deletedFiles==1)
                check(a.controller.detail(p.id).analysis==result)
                val error = try { a.post(p); null } catch (e: ArchiveException) { e }
                check(error?.httpStatus==410)
                Mockito.verifyNoInteractions(a.network,a.bounded)
                println("ANALYSIS_SMOKE facts=3 kinds=assistant/completed/student year=2023 hash/paragraph=verified scope=PUBLICATION_TIME first=unchanged cache=no-write missingBaselineDelta=null deletedAnalysis=retained reanalysis=410 network=0 businessWrites=0")
            }
        } finally { ArchiveMysqlFixture.stop() }
    }
}

/** Private exact-corpus pure entry. Prints aggregate evidence only, never names, mailboxes or quotes. */
object PaperQualificationPrivateAuditLauncher {
    @JvmStatic fun main(args: Array<String>) {
        val root = Paths.get(args.single())
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false)
        val extractor = PaperQualificationEvidenceExtractor()
        var papers = 0; var bodies = 0; var missing = 0; var facts = 0; var errors = 0
        val kinds = mutableMapOf<String,Int>()
        Files.list(root).use { paths -> paths.filter { it.fileName.toString().matches(Regex("[0-9a-f]{64}\\.json")) }.sorted().forEach { path ->
            val node = mapper.readTree(Files.readAllBytes(path).toString(Charsets.UTF_8)); papers++
            val paper = node.path("input").path("paper")
            val authors = paper.path("authors").map { mapper.treeToValue(it,PaperAuthor::class.java) }
            node.path("bodies").forEach { body ->
                val filename = Paths.get(body.path("path").asText()).fileName
                val candidate = root.resolve(filename)
                if (!Files.isRegularFile(candidate)) { missing++ } else {
                    val bytes = Files.readAllBytes(candidate); bodies++
                    val result = extractor.extract(bytes,"PDF",authors,paper.path("pubYear").asInt(),DiscoveryPaperArchiveService.sha(bytes))
                    facts += result.facts.size
                    if (result.coverage.status==PaperEvidenceStatus.ERROR) errors++
                    result.facts.forEach { kinds[it.kind.name]=(kinds[it.kind.name] ?: 0)+1 }
                }
            }
        } }
        println("PRIVATE_QUALIFICATION_AUDIT papers=$papers bodies=$bodies missingBodies=$missing facts=$facts kinds=$kinds parseErrors=$errors personLabels=UNAVAILABLE exact1plus2Coverage=NOT_VERIFIED externalRequests=0 businessWrites=0")
    }
}
