package com.weibo.talentintroduction.discovery.archive

import com.weibo.talentintroduction.discovery.domain.EmailExtractionOutcome
import com.weibo.talentintroduction.discovery.domain.PaperMetadata
import com.weibo.talentintroduction.discovery.sample.DiscoverySampleContext
import com.weibo.talentintroduction.discovery.sample.DiscoverySampleEntry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiscoveryPaperArchiveCaptureTest {
    @BeforeEach fun clean() = ArchiveMysqlFixture.clean()
    private fun context(execution: Long = 1, source: String = "OPENALEX") =
        DiscoverySampleContext(execution, source, DiscoverySampleEntry.SYNC, 1, null)
    private fun paper(source: String = "OPENALEX") = PaperMetadata(null, null, "10.fixture/capture", "Synthetic", 2026, null, emptyList(), source)
    private fun outcome(reason: String = "NO_EMAIL_IN_FULLTEXT") = EmailExtractionOutcome(emptyList(), "PDF_PARSE", reason)
    private fun body(bytes: ByteArray) {
        val token = DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL, "https://user:secret@fixture.invalid/full?token=secret#fragment")
        DiscoveryPaperArchiveCapture.headers(token, 200, "application/pdf")
        DiscoveryPaperArchiveCapture.fullBody(token, bytes)
        DiscoveryPaperArchiveCapture.endAttempt(token)
    }
    @Test fun `disabled capture delegates unchanged and makes no archive writes`() {
        ArchiveFixture().use { f ->
            val capture = DiscoveryPaperArchiveCapture(f.service, f.mapper)
            val expected = outcome()
            assertSame(expected, capture.capture(context(), "key", paper()) { body(f.pdf()); expected })
            assertEquals(0, f.repository.records().size)
            assertFalse(Files.exists(f.root))
            f.enable()
            capture.capture(null, "key", paper()) { body(f.pdf()); expected }
            capture.capture(context(source="ORCID"), "key", paper()) { body(f.pdf()); expected }
            assertEquals(0, f.repository.records().size)
        }
    }
    @Test fun `scope permit survives enabled toggle but new scopes do not write`() {
        ArchiveFixture().use { f ->
            f.enable(); val capture = DiscoveryPaperArchiveCapture(f.service, f.mapper)
            capture.capture(context(), "key", paper()) {
                val token = DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL, null)
                val s = f.service.settings()
                f.service.updateSettings(ArchiveSettingsRequest(s.revision, false, s.maxBytes, s.minFreeBytes))
                DiscoveryPaperArchiveCapture.fullBody(token, f.pdf())
                outcome()
            }
            assertEquals(1L, f.service.stats().counts.savedAttempts)
            capture.capture(context(), "disabled", paper()) { body(f.pdf()); outcome() }
            assertEquals(1, f.repository.records().size)
            val direct = f.service.beginAttempt(f.repository.records().single().id, java.util.UUID.randomUUID(), 1, ArchiveAttemptKind.PDF_URL)
            assertEquals("ARCHIVE_DISABLED", assertThrows(ArchiveException::class.java) { f.service.saveBody(direct.id, f.pdf(), "application/pdf") }.code)
            assertFalse(DiscoveryPaperArchiveCapture.hasScope())
        }
    }
    @Test fun `live capacity and deployment permission still block issued permits`() {
        ArchiveFixture().use { f ->
            f.enable(); val capture = DiscoveryPaperArchiveCapture(f.service, f.mapper)
            val error = assertThrows(PaperArchiveBlockedException::class.java) {
                capture.capture(context(), "capacity", paper()) {
                    val token = DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL, null)
                    f.service.diskProbe = { ArchiveDisk(100*ARCHIVE_GIB, 0, 0) }
                    runCatching { DiscoveryPaperArchiveCapture.fullBody(token, f.pdf()) }
                    outcome()
                }
            }
            assertEquals("ARCHIVE_STORAGE_BLOCKED", error.code)
            assertEquals(ArchiveAttemptStatus.SAVE_ERROR, f.repository.attempts(f.repository.records().single().id).single().status)
            f.service.diskProbe = { ArchiveDisk(100*ARCHIVE_GIB,80*ARCHIVE_GIB,70*ARCHIVE_GIB) }
            assertThrows(PaperArchiveBlockedException::class.java) {
                capture.capture(context(), "deployment", paper()) {
                    val token = DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL, null)
                    f.props.allowed = false
                    DiscoveryPaperArchiveCapture.fullBody(token, f.pdf())
                    outcome()
                }
            }
            assertFalse(DiscoveryPaperArchiveCapture.hasScope())
        }
    }
    @Test fun `scope preflight rejects low space before delegate`() {
        ArchiveFixture().use { f ->
            f.enable(); f.service.diskProbe = { ArchiveDisk(100*ARCHIVE_GIB,0,0) }
            var called = false
            assertThrows(PaperArchiveBlockedException::class.java) {
                DiscoveryPaperArchiveCapture(f.service,f.mapper).capture(context(), "blocked", paper()) { called=true; outcome() }
            }
            assertFalse(called); assertEquals(0,f.repository.records().size)
        }
    }
    @Test fun `first snapshot frozen latest updates and metadata has no full text or credentials`() {
        ArchiveFixture().use { f ->
            f.enable(); val capture = DiscoveryPaperArchiveCapture(f.service,f.mapper)
            val p = paper().copy(fullText="private full text", downloadUrl="https://user:pass@fixture.invalid/a?token=secret#x")
            capture.capture(context(),"same",p) { body(f.pdf()); outcome("FIRST") }
            val record = f.repository.records().single()
            val first = f.mapper.readTree(record.outcomeJson).get("first")
            capture.capture(context(),"same",p) { body(f.pdf()); outcome("LATEST") }
            val latest = f.mapper.readTree(f.repository.record(record.id)!!.outcomeJson)
            assertEquals(first,latest.get("first"))
            assertEquals("LATEST",latest.path("latest").path("outcome").path("failureReason").asText())
            assertEquals(20261004,first.path("parserVersion").path("identityExtraction").asInt())
            assertEquals(0,first.path("parserVersion").path("pdfLayout").asInt())
            assertEquals(1,first.path("attemptIds").size())
            assertFalse(record.metadataJson.contains("fullText")); assertFalse(record.metadataJson.contains("secret"))
            assertEquals("https://fixture.invalid/a",f.mapper.readTree(record.metadataJson).path("downloadUrl").asText())
            assertEquals("https://fixture.invalid/full", f.repository.attempts(record.id).first().sourceUrl)
        }
    }
    @Test fun `concurrent repositories preserve first and analysis under latest writes`() {
        ArchiveFixture().use { f ->
            val record=f.paper("atomic")
            fun snapshot(value: String) = f.mapper.readTree("""{"schemaVersion":1,"first":{"tag":"$value"},"latest":{"tag":"$value"}}""")
            f.service.finishExtraction(record.id,snapshot("baseline"))
            val old=f.repository.record(record.id)!!
            f.service.saveAnalysis(record.id,old.revision,f.mapper.readTree("""{"schemaVersion":1,"tag":"analysis"}"""))
            val barrier=CountDownLatch(1); val pool=Executors.newFixedThreadPool(2)
            try {
                val calls=(1..2).map { n -> pool.submit { barrier.await(); ArchiveMysqlFixture.repository().finishExtraction(record.id,snapshot("update$n").toString(),Instant.now()) } }
                barrier.countDown(); calls.forEach { it.get(15,TimeUnit.SECONDS) }
            } finally { pool.shutdownNow() }
            val final=f.repository.record(record.id)!!
            val json=f.mapper.readTree(final.outcomeJson)
            assertEquals("baseline",json.path("first").path("tag").asText())
            assertTrue(json.path("latest").path("tag").asText() in listOf("update1","update2"))
            assertEquals("analysis",f.mapper.readTree(final.analysisJson).path("tag").asText())
            val legacy=f.paper("legacy")
            f.service.finishExtraction(legacy.id,f.mapper.readTree("""{"schemaVersion":1,"emails":[]}"""))
            f.service.finishExtraction(legacy.id,snapshot("new"))
            assertFalse(f.mapper.readTree(f.repository.record(legacy.id)!!.outcomeJson).has("first"))
        }
    }
    @Test fun `nested scopes restore thread identity without body accumulation or old sample quotas`() {
        ArchiveFixture().use { f ->
            f.enable(); val capture=DiscoveryPaperArchiveCapture(f.service,f.mapper)
            capture.capture(context(1),"outer",paper()) {
                body(f.pdf())
                capture.capture(context(2,"CORE"),"inner",paper("CORE").copy(fullText="CORE original text")) { outcome() }
                repeat(24) { body(f.pdf(fill=(it+1).toByte())) }
                outcome()
            }
            assertFalse(DiscoveryPaperArchiveCapture.hasScope())
            val records=f.repository.records().associateBy { it.executionId }
            assertEquals(25,f.repository.attempts(records.getValue(1).id).size)
            assertEquals(ArchiveAttemptKind.EMBEDDED_TEXT,f.repository.attempts(records.getValue(2).id).single().kind)
        }
    }
    @Test fun `download failure too large and unfinished attempts terminate without fake blobs`() {
        ArchiveFixture().use { f ->
            f.enable(); val capture=DiscoveryPaperArchiveCapture(f.service,f.mapper)
            capture.capture(context(),"failures",paper()) {
                val missing=DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL,null)
                DiscoveryPaperArchiveCapture.headers(missing,404,"text/html")
                DiscoveryPaperArchiveCapture.endAttempt(missing,IllegalStateException("404"))
                val large=DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PDF_URL,null)
                DiscoveryPaperArchiveCapture.endAttempt(large,null,true)
                DiscoveryPaperArchiveCapture.beginAttempt(ArchiveAttemptKind.PMC_XML,null)
                outcome()
            }
            assertEquals(listOf(ArchiveAttemptStatus.DOWNLOAD_FAILED,ArchiveAttemptStatus.TOO_LARGE,ArchiveAttemptStatus.DOWNLOAD_FAILED),
                f.repository.attempts(f.repository.records().single().id).map { it.status })
            assertEquals(0,f.repository.blobs().size)
        }
    }
}
