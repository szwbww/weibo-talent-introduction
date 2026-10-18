package com.weibo.talentintroduction.discovery.sample

import com.weibo.talentintroduction.config.*
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.util.concurrent.*

class DiscoverySampleCaptureTest {
    @TempDir lateinit var temp: Path
    private fun context(id: Long = 1) = DiscoverySampleContext(id, "OPENALEX", DiscoverySampleEntry.SYNC, 1, null)
    private fun paper(name: String = "paper") = PaperMetadata(null, null, null, name, 2026, null, emptyList(), "OPENALEX")
    private val outcome = EmailExtractionOutcome(emptyList(), "PDF", "NO_EMAIL_IN_TEXT")
    private fun canonical(path: Path): String {
        Files.createDirectories(path.parent)
        return path.parent.toRealPath().resolve(path.fileName).toString()
    }
    private fun manifests(root: Path): List<DiscoverySampleManifest> = if (!Files.exists(root)) emptyList() else Files.walk(root).use { paths ->
        paths.filter { it.fileName.toString() == "sample.json" }.toArray().map {
            DiscoverySampleJson.mapper.readValue(Files.readAllBytes(it as Path), DiscoverySampleManifest::class.java) } }
    @Test fun `capture identity bounds scope and acceptance`() {
        val base = System.getProperty("sample.acceptanceDir")?.let { Paths.get(it, "capture-scope") } ?: temp
        if (Files.exists(base)) Files.walk(base).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        val disabled = base.resolve("disabled")
        val disabledCapture = DiscoverySampleCapture(DiscoverySampleStore(DiscoverySampleProperties(directory = disabled.toString())))
        var calls = 0
        val startDisabled = System.nanoTime()
        repeat(20) { assertSame(outcome, disabledCapture.capture(context(), paper()) { calls++; outcome }) }
        val disabledElapsed = System.nanoTime() - startDisabled
        assertEquals(20, calls); assertFalse(Files.exists(disabled))
        val root = base.resolve("enabled")
        val properties = DiscoverySampleProperties(enabled = true, directory = canonical(root), maxBodyBytes = 1024,
            maxMetadataBytes = 16384, maxTotalBytes = 1000000)
        val capture = DiscoverySampleCapture(DiscoverySampleStore(properties))
        val original = IllegalStateException("secret exception")
        calls = 0
        val thrown = assertThrows(IllegalStateException::class.java) { capture.capture(context(), paper("error")) { calls++; throw original } }
        assertSame(original, thrown); assertEquals(1, calls)
        assertEquals(original.javaClass.name, manifests(root).single().extractionExceptionClass)
        val startEnabled = System.nanoTime()
        repeat(20) { index -> assertSame(outcome, capture.capture(context(), paper("enabled$index")) {
            val token = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PDF_URL, "https://user:password@example.org/p?secret#x", PdfExtractionProperties())
            DiscoverySampleCapture.headers(token, 200, "application/pdf")
            DiscoverySampleCapture.fullBody(token, ByteArray(1025))
            DiscoverySampleCapture.endAttempt(token)
            outcome
        }) }
        val enabledElapsed = System.nanoTime() - startEnabled
        val bodyLimit = manifests(root).first { it.paper.title == "enabled0" }.attempts.single()
        assertEquals(DiscoverySampleBodyStatus.BODY_LIMIT, bodyLimit.bodyStatus); assertNull(bodyLimit.bodyFile)
        assertNull(bodyLimit.bodySha256); assertEquals(1025L, bodyLimit.bodyBytes)
        assertFalse(bodyLimit.url.contains("secret")); assertFalse(bodyLimit.url.contains("password"))
        val pool = Executors.newFixedThreadPool(2)
        val bothStarted = CountDownLatch(2)
        val firstCommitted = CountDownLatch(1)
        try {
            val futures = (2L..3L).map { id -> pool.submit<EmailExtractionOutcome> {
                capture.capture(context(id), paper("parallel$id")) {
                    bothStarted.countDown()
                    check(bothStarted.await(5, TimeUnit.SECONDS))
                    if (id == 3L) check(firstCommitted.await(5, TimeUnit.SECONDS))
                    val token = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/$id")
                    DiscoverySampleCapture.fullBody(token, "$id".toByteArray()); DiscoverySampleCapture.endAttempt(token); outcome
                }.also { if (id == 2L) firstCommitted.countDown() }
            } }
            futures.forEach { assertSame(outcome, it.get(10, TimeUnit.SECONDS)) }
        } finally { pool.shutdownNow() }
        val parallel = manifests(root).filter { it.context.executionId in 2L..3L }
        assertEquals(setOf(2L, 3L), parallel.map { it.context.executionId }.toSet())
        parallel.forEach { assertEquals("https://example.org/${it.context.executionId}", it.attempts.single().url) }
        assertNull(DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "outside"))
        capture.capture(context(4), paper("reuse")) { outcome }
        assertTrue(manifests(root).single { it.context.executionId == 4L }.attempts.isEmpty())
        val cases = mapOf("disabled" to mapOf("createdFiles" to 0, "extraNetworkCalls" to 0, "elapsedNanos" to disabledElapsed),
            "delegate-error" to mapOf("sameException" to true, "delegateCalls" to 1),
            "scope-reuse" to mapOf("crossExecutionMixups" to 0, "executionIds" to parallel.map { it.context.executionId }),
            "resources" to mapOf("enabledElapsedNanos" to enabledElapsed, "disabledElapsedNanos" to disabledElapsed,
                "iterations" to 20, "networkCalls" to 0, "dbWrites" to 0, "esWrites" to 0,
                "configuredMaxBodyBytes" to properties.maxBodyBytes, "configuredMaxInFlightCaptures" to properties.maxInFlightCaptures,
                "retainedBodyBytes" to manifests(root).sumOf { it.bodyBytesRetained }))
        System.getProperty("sample.acceptanceDir")?.let { Files.createDirectories(base); Files.write(base.resolve("cases.json"),
            DiscoverySampleJson.mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(cases)) }
    }
    @Test fun `busy host limits and nested restoration`() {
        val root = temp.resolve("scope")
        val capture = DiscoverySampleCapture(DiscoverySampleStore(DiscoverySampleProperties(enabled = true, directory = canonical(root), maxInFlightCaptures = 1)))
        val busy = FulltextRequestGate.HostBusyException("example.org")
        assertSame(busy, assertThrows(FulltextRequestGate.HostBusyException::class.java) {
            capture.capture(context(), paper()) { throw busy } })
        assertTrue(manifests(root).isEmpty())
        FulltextRequestGate.inQueueExtractionScope(1, java.time.Instant.now().plusSeconds(60)) {
            capture.capture(context(), paper("swallowed-host-busy")) {
                FulltextRequestGate.activeScope()!!.hostBusy.set(true)
                outcome
            }
        }
        assertTrue(manifests(root).isEmpty())
        capture.capture(context(), paper("outer")) {
            val token = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/outer")
            assertSame(outcome, capture.capture(context(2), paper("busy")) { outcome })
            DiscoverySampleCapture.fullBody(token, "outer".toByteArray()); DiscoverySampleCapture.endAttempt(token)
            repeat(20) { DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/$it") }
            outcome
        }
        val manifest = manifests(root).single()
        assertEquals(16, manifest.attempts.size); assertTrue(manifest.attemptsTruncated)
        assertEquals(DiscoverySampleBodyStatus.SAVED, manifest.attempts.first().bodyStatus)
        assertEquals(5L, manifest.bodyBytesRetained)
        assertSame(outcome, capture.capture(context(0), paper("invalid")) { outcome })
        assertEquals(1, manifests(root).size)
        val nested = DiscoverySampleCapture(DiscoverySampleStore(DiscoverySampleProperties(enabled = true, directory = canonical(temp.resolve("nested")))))
        nested.capture(context(), paper("outer")) {
            val outer = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/outer")
            nested.capture(context(2), paper("inner")) {
                val inner = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/inner")
                DiscoverySampleCapture.endAttempt(inner); outcome
            }
            DiscoverySampleCapture.fullBody(outer, byteArrayOf(1)); DiscoverySampleCapture.endAttempt(outer); outcome
        }
    }
    @Test fun `complete bodies retained in completion order without partial bytes`() {
        val root = temp.resolve("bodies")
        val capture = DiscoverySampleCapture(DiscoverySampleStore(DiscoverySampleProperties(enabled = true, directory = canonical(root))))
        capture.capture(context(), paper()) {
            val first = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/1")
            val second = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/2")
            val third = DiscoverySampleCapture.beginAttempt(DiscoverySampleAttemptKind.PMC_XML, "https://example.org/3")
            DiscoverySampleCapture.fullBody(second, byteArrayOf(2))
            DiscoverySampleCapture.fullBody(first, byteArrayOf(1))
            DiscoverySampleCapture.fullBody(third, byteArrayOf(3))
            listOf(first, second, third).forEach { DiscoverySampleCapture.endAttempt(it) }
            outcome
        }
        val manifest = manifests(root).single()
        assertEquals(listOf(DiscoverySampleBodyStatus.SAVED, DiscoverySampleBodyStatus.SAVED, DiscoverySampleBodyStatus.BODY_LIMIT),
            manifest.attempts.map { it.bodyStatus })
        assertEquals(2L, manifest.bodyBytesRetained)
        assertNull(manifest.attempts[2].bodyFile)
    }
    @Test fun `every bucket follows ordered precedence`() {
        fun bucket(reason: String?) = DiscoverySampleJson.bucket(EmailExtractionOutcome(emptyList(), null, reason), null)
        assertEquals(DiscoverySampleBucket.EXTRACTION_EXCEPTION, DiscoverySampleJson.bucket(null, IllegalStateException()))
        mapOf("NO_PMC_ID" to DiscoverySampleBucket.NO_FULLTEXT_ADDRESS, "NO_EMAIL_IN_HTML" to DiscoverySampleBucket.NO_EMAIL,
            "PDF_TOO_LARGE" to DiscoverySampleBucket.BODY_TOO_LARGE, "PDF_DOWNLOAD_FAILED" to DiscoverySampleBucket.DOWNLOAD_FAILED,
            "FULLTEXT_FETCH_FAILED" to DiscoverySampleBucket.DOWNLOAD_FAILED, "PDF_PARSE_FAILED" to DiscoverySampleBucket.PARSE_FAILED,
            "XML_PARSE_FAILED" to DiscoverySampleBucket.PARSE_FAILED, "UNKNOWN" to DiscoverySampleBucket.OTHER_FAILURE)
            .forEach { (reason, expected) -> assertEquals(expected, bucket(reason)) }
        assertEquals(DiscoverySampleBucket.OTHER_FAILURE, bucket(null))
        val complete = AuthorEmail("a@example.org", "Given", "Family", true, null, null)
        assertEquals(DiscoverySampleBucket.SUCCESS_CONTROL, DiscoverySampleJson.bucket(EmailExtractionOutcome(listOf(complete), null), null))
        assertEquals(DiscoverySampleBucket.IDENTITY_UNRESOLVED, DiscoverySampleJson.bucket(
            EmailExtractionOutcome(listOf(complete.copy(givenNames = " ")), null, "PDF_DOWNLOAD_FAILED"), null))
    }
}
