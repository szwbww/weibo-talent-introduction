package com.weibo.talentintroduction.discovery.service

import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.web.client.RestTemplate
import java.nio.file.Files
import java.time.Instant

internal val recheckTime: Instant = Instant.parse("2026-10-24T00:00:00Z")
internal fun evidenceSnapshot(name: String = "Alice Smith", email: String = "alice@fixture.edu", authorId: String? = "A123",
    orcid: String? = null, verified: Boolean = true, suffix: String = name): VerificationSnapshot {
    val p = ExpertProfile(orcidId = "EMAIL-$suffix",email=email,givenNames=name.substringBefore(' '),familyNames=name.substringAfter(' '),
        country=null,keyword=null,employment=null,tags=listOf("discovered"),
        identityVerification=if (verified) DiscoveryIdentity.verified(email,name.substringBefore(' '),name.substringAfter(' '),
            "SOURCE_SHA256:${"a".repeat(64)}",orcid,authorId) else null)
    return VerificationSnapshot(DiscoveryIdentity.hash("identity:$suffix"),DiscoveryIdentity.hash("facts:$suffix"),p)
}
internal class PaperBridgeFixture(val f: ArchiveFixture) {
    val network = Mockito.mock(RestTemplate::class.java)
    val bounded = Mockito.mock(BoundedHttpExecutor::class.java)
    val pdf = PdfEmailExtractor(network,PlainTextEmailExtractor(),PdfExtractionProperties(),bounded)
    val analyzer = Mockito.spy(PaperBodyAnalyzer(pdf,PaperQualificationEvidenceExtractor(),f.mapper))
    val bridge = PaperVerificationEvidenceService(f.repository,f.service,analyzer,f.mapper)
    val authors = listOf(PaperAuthor("Alice","Smith",null,null,openAlexAuthorId="A123"),
        PaperAuthor("Bob","Jones",null,null,openAlexAuthorId="A456"),PaperAuthor("Carol","White",null,null,openAlexAuthorId="A789"))
    val text = "Alice Smith: alice@fixture.edu\n\nBob Jones: bob@fixture.edu\n\nCarol White: carol@fixture.edu\n\n" +
        "Biographies\n\nAlice Smith received her PhD in 2020. Alice Smith was born on 1980-01-02. Alice Smith is an Assistant Professor.\n\n" +
        "Bob Jones is a Professor.\n\nCarol White is a PhD student."
    fun paper(key: String = "W1", execution: Long = 1, value: String = text, people: List<PaperAuthor> = authors,
        bytes: ByteArray = value.toByteArray(), kind: ArchiveAttemptKind = ArchiveAttemptKind.EMBEDDED_TEXT): PaperSourceRef {
        val r = f.service.beginRecord(execution,"OPENALEX",key,f.mapper.valueToTree(mapOf("schemaVersion" to 1,
            "doi" to "10.1234/example","pubYear" to 2023,"authors" to people)))
        val a = f.body(r,bytes,kind,if (kind == ArchiveAttemptKind.PDF_URL) "application/pdf" else "text/plain")
        f.service.finishExtraction(r.id,f.mapper.readTree("""{"schemaVersion":1,"first":{"capturedAt":"2023-01-01T00:00:00Z","attemptIds":[${a.id}],"outcome":{"emails":[]}}}"""))
        return PaperSourceRef(r.id,a.id,a.blobSha256!!,execution,"OPENALEX",key)
    }
    fun collect(snapshot: VerificationSnapshot, refs: List<PaperSourceRef>, mode: RecheckEvidenceMode = RecheckEvidenceMode.REUSE) =
        bridge.collect(snapshot,refs,mode,recheckTime,"https://admin.fixture.invalid")
}

class PaperVerificationEvidenceServiceTest {
    @BeforeEach fun clean() = ArchiveMysqlFixture.clean()
    private fun parseCalls(analyzer: PaperBodyAnalyzer) = Mockito.mockingDetails(analyzer).invocations.count { it.method.name == "parse" }
    @Test fun `shared body across people computes once while binding and diagnostics stay individual`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper()
            val alice = evidenceSnapshot()
            val bob = evidenceSnapshot("Bob Jones","bob@fixture.edu","A456")
            val cold = b.collect(alice,listOf(ref))
            val samePersonWarm = b.collect(alice,listOf(ref))
            val warm = b.collect(bob,listOf(ref))
            assertEquals(cold,samePersonWarm)
            assertEquals(1,cold.parsedBodies); assertEquals(1,warm.parsedBodies)
            assertEquals(3,cold.sources.single().evidence.size)
            assertEquals(1,warm.sources.single().evidence.size)
            assertEquals(alice.identityHash,cold.sources.single().evidence.first().identityHash)
            assertEquals(bob.identityHash,warm.sources.single().evidence.first().identityHash)
            assertEquals(1,parseCalls(b.analyzer))
        }
    }
    @Test fun `same bytes with changed authors year kind or version recompute`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f)
            val base = b.paper("base")
            val authors = b.paper("authors",people=b.authors.reversed())
            val year = b.paper("year")
            ArchiveMysqlFixture.jdbc.update("UPDATE discovery_paper_archive_record SET metadata_json=JSON_SET(metadata_json,'$.pubYear',2024) WHERE id=?",year.archiveRecordId)
            val kind = b.paper("kind",kind=ArchiveAttemptKind.PMC_XML)
            listOf(base,authors,year,kind).forEach { b.collect(evidenceSnapshot(),listOf(it)) }
            assertEquals(4,parseCalls(b.analyzer))
            Mockito.doReturn(mapOf("format" to 999)).`when`(b.analyzer).versions()
            b.collect(evidenceSnapshot(),listOf(base))
            assertEquals(5,parseCalls(b.analyzer))
        }
    }
    @Test fun `archive busy retains safe cause and retryability without confusing a missing body`() {
        ArchiveFixture().use { f ->
            f.enable(); val bridge = PaperBridgeFixture(f); val ref = bridge.paper()
            java.nio.channels.FileChannel.open(f.root.resolve(".archive.lock"), java.nio.file.StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    val source = bridge.collect(evidenceSnapshot(), listOf(ref)).sources.single()
                    assertEquals(listOf("SOURCE_UNAVAILABLE"), source.reasonCodes)
                    assertEquals("ARCHIVE_BUSY", source.failure!!.code)
                    assertEquals("OPEN_ARCHIVE", source.failure!!.stage)
                    assertTrue(source.failure!!.retryable)
                    assertTrue(source.evidence.isEmpty())
                }
            }
            val recovered = bridge.collect(evidenceSnapshot(), listOf(ref)).sources.single()
            assertNull(recovered.failure)
            assertTrue(recovered.evidence.isNotEmpty())
        }
    }
    @Test fun `unique current body binding maps only own SELF historical facts with exact provenance and zero writes`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper()
            val before = f.repository.record(ref.archiveRecordId)!!
            val s = evidenceSnapshot(verified=false)
            val read = b.collect(s,listOf(ref)); val source = read.sources.single()
            assertEquals(1,read.parsedBodies); assertNotNull(source.binding)
            assertEquals(setOf("education","birthDate","employment"),source.evidence.map { it.field }.toSet())
            val education = f.mapper.readTree(source.evidence.single { it.field == "education" }.value)
            assertEquals("PhD completed",education.path("role").asText()); assertTrue(education.path("completed").asBoolean())
            assertEquals("1980-01-02",source.evidence.single { it.field == "birthDate" }.value)
            source.evidence.forEach {
                assertEquals("PAPER",it.provider); assertEquals(AssertionSource.SELF,it.assertionSource)
                assertEquals(TemporalScope.HISTORICAL,it.temporalScope); assertEquals("https://doi.org/10.1234/example",it.sourceUrl)
                assertEquals(ref.bodySha256,it.responseSha256); assertEquals(s.identityHash,it.identityHash); assertEquals(s.factFingerprint,it.factFingerprint)
                assertTrue(it.jsonPointer!!.contains("/attempt/${ref.attemptId}/")); assertTrue(it.jsonPointer!!.endsWith("/year/2023"))
                assertTrue(it.excerpt!!.contains("Alice Smith")); assertFalse(it.excerpt!!.contains("Bob Jones"))
            }
            assertEquals(before,f.repository.record(ref.archiveRecordId)); assertFalse(before.protected)
            Mockito.verifyNoInteractions(b.network,b.bounded)
        }
    }
    @Test fun `bound author ID works without new mailbox proof while adjacent author and student never acquire completed doctorate`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper(value=b.text.substringAfter("Biographies"))
            assertEquals(3,b.collect(evidenceSnapshot(),listOf(ref)).sources.single().evidence.size)
            val bob = b.collect(evidenceSnapshot("Bob Jones","bob@fixture.edu","A456"),listOf(ref)).sources.single().evidence
            assertEquals(1,bob.size); assertEquals("employment",bob.single().field); assertFalse(bob.single().value.contains("Assistant"))
            val carol = b.collect(evidenceSnapshot("Carol White","carol@fixture.edu","A789"),listOf(ref)).sources.single().evidence
            assertEquals("PhD student",f.mapper.readTree(carol.single().value).path("role").asText())
            assertFalse(f.mapper.readTree(carol.single().value).path("completed").asBoolean())
            assertTrue(b.collect(evidenceSnapshot(verified=false),listOf(ref)).sources.single().evidence.isEmpty())
        }
    }
    @Test fun `same name shared mailbox and non owner biography produce no positive facts and distinct diagnostics`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f)
            val shared = b.paper("shared",value=b.text.replace("bob@fixture.edu","alice@fixture.edu"),
                people=b.authors.map { if (it.familyNames in setOf("Smith","Jones")) it.copy(email="alice@fixture.edu") else it })
            val sharedRead = b.collect(evidenceSnapshot(),listOf(shared)).sources.single()
            assertTrue(sharedRead.evidence.isEmpty()); assertEquals(listOf("AMBIGUOUS_IDENTITY"),sharedRead.reasonCodes)
            val duplicate = b.paper("same-name",people=listOf(b.authors[0],b.authors[0]))
            val duplicateRead = b.collect(evidenceSnapshot(),listOf(duplicate)).sources.single()
            assertTrue(duplicateRead.evidence.isEmpty()); assertTrue("AMBIGUOUS_IDENTITY" in duplicateRead.reasonCodes)
            val adjacent = b.paper("adjacent",value="Biographies\n\nBob Jones received his PhD. Bob Jones is a Professor.")
            assertTrue(b.collect(evidenceSnapshot(),listOf(adjacent)).sources.single().evidence.isEmpty())
        }
    }
    @Test fun `cross execution source lookup normalizes IDs validates contradictions and parses duplicate SHA once`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val one = b.paper(); val two = b.paper(execution=2)
            val exact = PaperArchiveLookup(1,"OPENALEX","W1","https://doi.org/10.1234/EXAMPLE")
            assertEquals(listOf(one),b.bridge.locate(listOf(exact)).getValue(exact))
            val old = exact.copy(executionId=99)
            assertEquals(listOf(one,two),b.bridge.locate(listOf(old)).getValue(old))
            val wrong = exact.copy(doi="10.1234/other")
            assertTrue(b.bridge.locate(listOf(wrong)).getValue(wrong).isEmpty())
            val before = listOf(one,two).map { f.repository.record(it.archiveRecordId) }
            val read = b.collect(evidenceSnapshot(),listOf(one,two))
            assertEquals(1,read.parsedBodies); assertEquals(listOf(1L,2L),read.sources.map { it.source.sourceExecutionId })
            assertTrue(read.sources.all { it.evidence.size == 3 }); assertEquals(before,listOf(one,two).map { f.repository.record(it.archiveRecordId) })
            Mockito.verifyNoInteractions(b.network,b.bounded)
        }
    }
    @Test fun `fixed missing changed mismatched refs never select replacement and preserve reliable identity`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper(); b.paper(execution=2)
            for (bad in listOf(ref.copy(attemptId=999999),ref.copy(bodySha256="b".repeat(64)),ref.copy(sourceExecutionId=99))) {
                val failed = b.collect(evidenceSnapshot(),listOf(bad),RecheckEvidenceMode.REFRESH)
                assertEquals(listOf("SOURCE_UNAVAILABLE"),failed.reasonCodes)
                assertNotNull(failed.sources.single().failure)
                assertFalse(failed.sources.single().failure!!.retryable)
                assertEquals("VALIDATE_REFERENCE", failed.sources.single().failure!!.stage)
            }
            val path = f.root.resolve("blobs").resolve(ref.bodySha256.take(2)).resolve("${ref.bodySha256}.bin")
            assertNull(b.collect(evidenceSnapshot(),listOf(ref)).sources.single().failure)
            Files.write(path,"changed".toByteArray())
            val s = evidenceSnapshot(); assertTrue(s.identityValid)
            assertEquals(listOf("SOURCE_UNAVAILABLE"),b.collect(s,listOf(ref)).reasonCodes)
            assertTrue(s.identityValid); assertEquals("A123",s.profile.identityVerification!!.openAlexAuthorId)
            Mockito.verifyNoInteractions(b.network,b.bounded)
        }
    }
    @Test fun `physical deletion remains unavailable and bounded PDF coverage is partial with real pages`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f)
            val ref = b.paper(bytes=QualificationFixtures.pdf(101,emptyList()),kind=ArchiveAttemptKind.PDF_URL)
            val source = b.collect(evidenceSnapshot(),listOf(ref)).sources.single()
            assertEquals(PaperEvidenceStatus.PARTIAL,source.coverage!!.status)
            assertTrue("PAGE_LIMIT" in source.coverage!!.reasons); assertEquals(100,source.coverage!!.scannedPages)
            assertEquals(101,source.coverage!!.totalPages); assertTrue(source.evidence.isEmpty())
            val preview = f.service.deletePreview(listOf(ref.archiveRecordId))
            assertEquals(1,f.service.delete(ArchiveDeleteRequest(preview.ids,preview.snapshotHash)).deletedFiles)
            assertEquals(listOf("SOURCE_UNAVAILABLE"),b.collect(evidenceSnapshot(),listOf(ref)).reasonCodes)
        }
    }
    @Test fun `PDF facts preserve page and malformed or partial birth dates are never facts`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f)
            val ref = b.paper(bytes=QualificationFixtures.pdf(lines=listOf("Biographies","Alice Smith received her PhD.","Alice Smith was born in 1980.","Alice Smith was born on 1980-02-30.")),kind=ArchiveAttemptKind.PDF_URL)
            val source = b.collect(evidenceSnapshot(),listOf(ref)).sources.single()
            assertEquals(1,source.evidence.size); assertTrue(source.evidence.single().jsonPointer!!.contains("/page/1/"))
            assertEquals("education",source.evidence.single().field)
        }
    }
    @Test fun `truthful same domain fallback URL and frozen source ID contradiction are explicit`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper()
            ArchiveMysqlFixture.jdbc.update("UPDATE discovery_paper_archive_record SET metadata_json=JSON_REMOVE(metadata_json,'$.doi') WHERE id=?",ref.archiveRecordId)
            ArchiveMysqlFixture.jdbc.update("UPDATE discovery_paper_archive_attempt SET source_url=NULL WHERE id=?",ref.attemptId)
            val result = b.collect(evidenceSnapshot(),listOf(ref)).sources.single()
            assertTrue(result.evidence.all { it.sourceUrl == "https://admin.fixture.invalid/api/expert-discovery/papers/${ref.archiveRecordId}" })
            val noOrigin = b.bridge.collect(evidenceSnapshot(),listOf(ref),RecheckEvidenceMode.REUSE,recheckTime).sources.single()
            assertTrue(noOrigin.evidence.isEmpty()); assertTrue("SOURCE_URL_UNAVAILABLE" in noOrigin.reasonCodes)
            val frozen = evidenceSnapshot().let { it.copy(profile=it.profile.copy(externalIds="""{"doi":"10.1234/other"}""")) }
            assertEquals(listOf("SOURCE_UNAVAILABLE"),b.collect(frozen,listOf(ref)).reasonCodes)
        }
    }
    @Test fun `historical PAPER professor alone cannot satisfy CURRENT qualification or repair identity`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f); val ref = b.paper(value="Biographies\n\nAlice Smith is a Professor.")
            val s = evidenceSnapshot(); val evidence = b.collect(s,listOf(ref)).sources.single().evidence
            assertEquals(1,evidence.size)
            assertNotEquals(VerificationQualification.MET,DiscoveryVerificationRules().evaluate(s,evidence,ruleVersion=3,evaluatedAt=recheckTime).qualification)
            val unverified = evidenceSnapshot(verified=false)
            assertFalse(unverified.identityValid)
            assertNotEquals(VerificationQualification.MET,DiscoveryVerificationRules().evaluate(unverified,evidence,ruleVersion=3,evaluatedAt=recheckTime).qualification)
        }
    }
    @Test fun `verified author ID identifies its own biography despite name order without issuing a new receipt`() {
        ArchiveFixture().use { f ->
            f.enable(); val b = PaperBridgeFixture(f)
            val ref = b.paper(value="Biographies\n\nSmith Alice received her PhD.",
                people=listOf(b.authors.first().copy(givenNames="Smith",familyNames="Alice")))
            val s = evidenceSnapshot(); val before = s.profile
            val source = b.collect(s,listOf(ref)).sources.single()
            assertEquals(1,source.evidence.size); assertEquals(0,source.boundAuthorIndex)
            assertEquals("VERIFIED_AUTHOR_ID",source.bindingMethod); assertEquals(before,s.profile)
            assertTrue(b.collect(evidenceSnapshot(verified=false),listOf(ref)).sources.single().evidence.isEmpty())
        }
    }
}

/** Standalone disposable runtime, not a JUnit invocation and never a production application. */
object RecheckEvidenceRuntimeLauncher {
    @JvmStatic fun main(args: Array<String>) {
        val httpCount = java.util.concurrent.atomic.AtomicInteger()
        val unexpected = java.util.concurrent.atomic.AtomicInteger()
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { exchange ->
            httpCount.incrementAndGet()
            val path = exchange.requestURI.toString()
            val response = when (path) {
                "/authors?filter=openalex:A123&per_page=1" -> AcademicClientFixture.success
                "/authors?filter=orcid:0000-0002-1825-0097&per_page=200" -> AcademicClientFixture.ambiguous
                else -> { unexpected.incrementAndGet(); """{"error":"unexpected fixture request"}""" }
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type","application/json")
            exchange.sendResponseHeaders(if (unexpected.get()==0) 200 else 500,response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            ArchiveMysqlFixture.clean()
            ArchiveFixture(realDisk=true).use { f ->
                f.enable(); val b = PaperBridgeFixture(f); val first = b.paper(); val duplicate = b.paper(execution=2)
                val frozen = evidenceSnapshot()
                val before = listOf(first,duplicate).map { f.repository.record(it.archiveRecordId)!! }
                val refs = b.bridge.locate(listOf(PaperArchiveLookup(99,"OPENALEX","W1","https://doi.org/10.1234/EXAMPLE"))).values.single()
                check(refs == listOf(first,duplicate))
                val read = b.collect(frozen,refs)
                check(read.parsedBodies==1 && read.sources.all { it.evidence.size==3 })
                check(read.sources.flatMap { it.evidence }.all { it.provider=="PAPER" && it.assertionSource==AssertionSource.SELF &&
                    it.temporalScope==TemporalScope.HISTORICAL && it.responseSha256==first.bodySha256 &&
                    it.jsonPointer!!.contains("/paragraph/") && it.jsonPointer!!.endsWith("/year/2023") &&
                    !it.excerpt!!.contains("Bob Jones") })
                check(before==listOf(first,duplicate).map { f.repository.record(it.archiveRecordId)!! })
                val pure = f.service.analysisLease(first.archiveRecordId).use { lease ->
                    b.analyzer.compute(lease.bodies.map { PaperBodyInput(it.hash,it.attempt.kind,it.input.readBytes()) },
                        f.mapper.readTree(lease.record.metadataJson),f.mapper.readTree(lease.record.outcomeJson).path("first"),
                        lease.fileSet.map { it.first }.toSet(),recheckTime)
                }
                check(before==listOf(first,duplicate).map { f.repository.record(it.archiveRecordId)!! })
                val explicit = DiscoveryPaperAnalysisService(f.service,b.pdf,PaperQualificationEvidenceExtractor(),f.mapper)
                    .analyze(first.archiveRecordId)
                val comparable = explicit.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("analyzedAt",recheckTime.toString())
                check(f.mapper.valueToTree<com.fasterxml.jackson.databind.JsonNode>(pure)==comparable)
                val saved = f.repository.record(first.archiveRecordId)!!
                check(saved.revision==before.first().revision+1 && saved.outcomeJson==before.first().outcomeJson &&
                    f.repository.attempt(first.attemptId)!!.blobSha256==first.bodySha256 && !saved.protected)
                val shared = b.paper("shared",value=b.text.replace("bob@fixture.edu","alice@fixture.edu"),
                    people=b.authors.map { it.copy(email="alice@fixture.edu") })
                val sharedRead = b.collect(frozen,listOf(shared)).sources.single()
                check(sharedRead.evidence.isEmpty() && "AMBIGUOUS_IDENTITY" in sharedRead.reasonCodes)
                val adjacent = b.paper("adjacent",value="Biographies\n\nBob Jones is a Professor.")
                check(b.collect(frozen,listOf(adjacent)).sources.single().evidence.isEmpty())
                val student = b.collect(evidenceSnapshot("Carol White","carol@fixture.edu","A789"),listOf(first)).sources.single().evidence.single()
                check(f.mapper.readTree(student.value).path("completed").asBoolean()==false)
                val orderedName = b.paper("ordered-name",value="Biographies\n\nSmith Alice received her PhD.",
                    people=listOf(b.authors.first().copy(givenNames="Smith",familyNames="Alice")))
                check(b.collect(frozen,listOf(orderedName)).sources.single().let {
                    it.evidence.size==1 && it.bindingMethod=="VERIFIED_AUTHOR_ID" && it.boundAuthorIndex==0 })
                val partial = b.paper("partial",bytes=QualificationFixtures.pdf(101,emptyList()),kind=ArchiveAttemptKind.PDF_URL)
                check(b.collect(frozen,listOf(partial)).sources.single().coverage!!.let {
                    it.status==PaperEvidenceStatus.PARTIAL && it.scannedPages==100 && it.totalPages==101 && "PAGE_LIMIT" in it.reasons })
                val invalid = first.copy(bodySha256="b".repeat(64))
                check(b.collect(frozen,listOf(invalid)).reasonCodes==listOf("SOURCE_UNAVAILABLE"))
                val driftPath = f.root.resolve("blobs").resolve(adjacent.bodySha256.take(2)).resolve("${adjacent.bodySha256}.bin")
                Files.write(driftPath,"changed source".toByteArray())
                check(b.collect(frozen,listOf(adjacent)).reasonCodes==listOf("SOURCE_UNAVAILABLE"))
                val deletion = f.service.deletePreview(listOf(first.archiveRecordId,duplicate.archiveRecordId))
                check(f.service.delete(ArchiveDeleteRequest(deletion.ids,deletion.snapshotHash)).deletedFiles==1)
                check(b.collect(frozen,refs).reasonCodes==listOf("SOURCE_UNAVAILABLE"))
                check(f.repository.record(first.archiveRecordId)!!.analysisJson==saved.analysisJson)
                Mockito.verifyNoInteractions(b.network,b.bounded)
                val academic = AcademicClientFixture("http://127.0.0.1:${server.address.port}")
                val reuse = academic.service.collect(listOf(frozen),RecheckEvidenceMode.REUSE,recheckTime)
                check(reuse.getValue(frozen.identityHash).reasonCodes==listOf("EVIDENCE_NOT_RETAINED"))
                check(httpCount.get()==0 && academic.kinds.isEmpty())
                val orcid = evidenceSnapshot(authorId=null,orcid="0000-0002-1825-0097",suffix="orcid")
                val noId = evidenceSnapshot(authorId=null,suffix="email-only")
                val snapshotJson = f.mapper.writeValueAsString(listOf(frozen,orcid,noId))
                val refreshed = academic.service.collect(listOf(frozen,orcid,noId),RecheckEvidenceMode.REFRESH,recheckTime)
                check(refreshed.getValue(frozen.identityHash).data!!.let { it.hIndex==12 && it.citationCount==345 && it.researchFieldIds==listOf("3100") })
                check(refreshed.getValue(orcid.identityHash).reasonCodes==listOf("AMBIGUOUS_IDENTITY"))
                check(refreshed.getValue(noId.identityHash).reasonCodes==listOf("NO_TRUSTED_ACADEMIC_ID"))
                check(httpCount.get()==2 && unexpected.get()==0 && academic.kinds==List(3) { RequestKind.HISTORY_ENRICHMENT })
                check(f.mapper.writeValueAsString(listOf(frozen,orcid,noId))==snapshotJson)
                academic.service.collect(listOf(frozen),RecheckEvidenceMode.REUSE,recheckTime,refreshed)
                check(httpCount.get()==2)
                Mockito.verifyNoInteractions(academic.europePmc,academic.pdf,academic.unpaywall)
                println("CHILD06_RUNTIME PASS realMySQL=true duplicateSHAParses=1 pureWrites=0 explicitAnalysisWrites=1 first/bodySHA=unchanged PAPER=SELF/HISTORICAL ownFacts=3 shared/adjacent=0 fixedMissing/changed=SOURCE_UNAVAILABLE partialPages=100/101 REUSE_HTTP=0 REFRESH_HTTP=2 unexpectedHTTP=0 trustedID=A123 ORCID=AMBIGUOUS EMAIL=NO_TRUSTED_ACADEMIC_ID profileMutations=0")
            }
        } finally { server.stop(0); ArchiveMysqlFixture.stop() }
    }
}
