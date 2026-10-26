package com.weibo.talentintroduction.discovery.sample

import com.weibo.talentintroduction.config.DiscoverySampleProperties
import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.nio.file.*
import java.nio.channels.FileChannel
import java.time.*

class DiscoverySampleStoreTest {
    @TempDir lateinit var temp: Path
    private fun canonical(path: Path): Path {
        Files.createDirectories(path.parent)
        return path.parent.toRealPath().resolve(path.fileName)
    }
    private val now = Instant.parse("2026-10-18T00:00:00Z")
    private fun root(name: String): Path = System.getProperty("sample.acceptanceDir")?.let { Paths.get(it, "store", name) } ?: temp.resolve(name)
    private fun properties(path: Path) = DiscoverySampleProperties(enabled = true, directory = canonical(path).toString(), maxBodyBytes = 1024,
        maxMetadataBytes = 16384, maxTotalBytes = 20000, maxSamplesPerBucketPerRun = 2, maxSuccessControlsPerRun = 1)
    private fun paper(title: String) = PaperMetadata("PMC1", "1", "doi", title, 2026, "journal", listOf(
        PaperAuthor("Given", "Family", "orcid", "affiliation", true, "a@example.org", "education", "A1", "University", "GB", "OPENALEX")),
        "OPENALEX", "original text", "https://user:secret@example.org/paper?token=secret#fragment", listOf("https://example.org/second?secret"))
    private fun sample(title: String, success: Boolean = false): DiscoverySampleManifest {
        val p = DiscoverySampleJson.paper(paper(title)); val sha = DiscoverySampleJson.hash(DiscoverySampleJson.canonical(p))
        return DiscoverySampleManifest(sampleId = DiscoverySampleJson.hash("1\nOPENALEX\n$sha"), inputSha256 = sha, capturedAt = now.toString(),
            context = DiscoverySampleContext(1, "OPENALEX", DiscoverySampleEntry.SYNC, 1, null), paper = p,
            urlFingerprints = listOf(DiscoverySampleJson.hash(paper(title).downloadUrl!!), DiscoverySampleJson.hash(paper(title).candidateDownloadUrls[0])),
            bucket = if (success) DiscoverySampleBucket.SUCCESS_CONTROL else DiscoverySampleBucket.NO_EMAIL,
            unresolvedAuthorEmailCount = 0, outcome = EmailExtractionOutcome(emptyList(), "PDF", "NO_EMAIL_IN_TEXT"),
            extractionExceptionClass = null, attempts = emptyList(), attemptsTruncated = false, bodyBytesRetained = 0)
    }
    private fun packages(path: Path): List<Path> = if (!Files.exists(path)) emptyList() else Files.walk(path).use { stream ->
        stream.filter { it.fileName.toString() == "sample.json" }.toArray().map { it as Path } }
    private fun result(cases: Map<String, Any>) {
        System.getProperty("sample.acceptanceDir")?.let { val path = Paths.get(it, "store", "cases.json"); Files.createDirectories(path.parent)
            Files.write(path, DiscoverySampleJson.mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(cases)) }
    }
    @Test fun `URL scrubbing preserves raw reserved escapes and canonical input`() {
        val raw = "https://user:p%40ss@exa%6Dple.org:443/paper%2Fpart%3Fname%23section%25.pdf?token=secret#fragment"
        val expected = "https://exa%6Dple.org:443/paper%2Fpart%3Fname%23section%25.pdf"
        assertEquals(expected, DiscoverySampleJson.scrub(raw))
        assertEquals(expected, DiscoverySampleJson.scrub(expected))
        val original = paper("escaped").copy(downloadUrl = raw, candidateDownloadUrls = listOf(raw))
        val retained = DiscoverySampleJson.paper(original)
        assertEquals(expected, retained.downloadUrl)
        assertEquals(listOf(expected), retained.candidateDownloadUrls)
        assertArrayEquals(DiscoverySampleJson.canonical(original.copy(downloadUrl = expected,
            candidateDownloadUrls = listOf(expected))), DiscoverySampleJson.canonical(retained))
        assertArrayEquals(DiscoverySampleJson.canonical(retained),
            DiscoverySampleJson.canonical(DiscoverySampleJson.paper(retained)))
    }
    @Test fun `distinct execution expiry eviction and restart do not accumulate empty parents`() {
        fun execution(id: Long, at: Instant): DiscoverySampleManifest {
            val manifest = sample("retention")
            return manifest.copy(context = manifest.context.copy(executionId = id), capturedAt = at.toString(),
                sampleId = DiscoverySampleJson.hash("$id\nOPENALEX\n${manifest.inputSha256}"))
        }
        for (expire in listOf(false, true)) {
            val path = canonical(temp.resolve(if (expire) "repeated-expiry" else "repeated-eviction"))
            val p = properties(path).copy(maxTotalSamples = 1)
            // More distinct executions than the unchanged 1028-entry scan ceiling.
            for (id in 1L..1030L) {
                val at = now.plusSeconds(if (expire) id * 8 * 86400 else id)
                val store = DiscoverySampleStore(p, Clock.fixed(at, ZoneOffset.UTC))
                store.startup()
                assertEquals(DiscoverySampleDecision.SAVED, store.commit(execution(id, at), emptyMap()))
                Files.newDirectoryStream(path).use { entries ->
                    assertEquals(setOf(".writer.lock", id.toString()), entries.map { it.fileName.toString() }.toSet())
                }
            }
            val restarted = DiscoverySampleStore(p, Clock.fixed(now.plusSeconds(1031L * 8 * 86400), ZoneOffset.UTC))
            restarted.cleanup()
            assertTrue(packages(path).isEmpty())
            assertFalse(Files.exists(path.resolve("1030")))
        }
    }
    @Test fun `empty execution recovery is bounded and never deletes unknown or nonempty directories`() {
        val path = canonical(temp.resolve("empty-recovery")); val p = properties(path).copy(maxTotalSamples = 1)
        Files.createDirectory(path)
        for (id in 1..100) Files.createDirectory(path.resolve(id.toString()))
        DiscoverySampleStore(p).startup()
        Files.newDirectoryStream(path).use { assertEquals(listOf(".writer.lock"), it.map { entry -> entry.fileName.toString() }) }
        val failed = DiscoverySampleStore(p)
        failed.atomicMove = { _, _ -> throw java.io.IOException("injected") }
        for (id in 1L..20L) {
            val manifest = sample("failed")
            assertEquals(DiscoverySampleDecision.IO_ERROR, failed.commit(manifest.copy(
                context = manifest.context.copy(executionId = id),
                sampleId = DiscoverySampleJson.hash("$id\nOPENALEX\n${manifest.inputSha256}")), emptyMap()))
            assertFalse(Files.exists(path.resolve(id.toString())))
        }
        assertEquals(DiscoverySampleDecision.SAVED, DiscoverySampleStore(p).commit(sample("restart"), emptyMap()))
        val unknown = path.resolve("unknown"); Files.createDirectory(unknown)
        assertEquals(DiscoverySampleDecision.STORE_INVALID, DiscoverySampleStore(p).commit(sample("unknown"), emptyMap()))
        assertTrue(Files.isDirectory(unknown))
        Files.delete(unknown)
        val nonempty = path.resolve("2"); Files.createDirectory(nonempty); Files.writeString(nonempty.resolve("untouched"), "keep")
        assertEquals(DiscoverySampleDecision.STORE_INVALID, DiscoverySampleStore(p).commit(sample("nonempty"), emptyMap()))
        assertEquals("keep", Files.readString(nonempty.resolve("untouched")))
        assertEquals(1, packages(path).size)
    }
    @Test fun `store acceptance cases`() {
        listOf("saved", "missing-body", "quota", "expiry", "capacity", "atomic-failure", "unwritable", "io-error").forEach { name ->
            val path = root(name)
            if (Files.exists(path)) Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
        val cases = linkedMapOf<String, Any>()
        val saved = root("saved"); val store = DiscoverySampleStore(properties(saved), Clock.fixed(now, ZoneOffset.UTC))
        val body = "original\r\nbytes  作者".toByteArray()
        val original = sample("roundtrip")
        val manifest = original.copy(attempts = listOf(DiscoverySampleAttempt(1, DiscoverySampleAttemptKind.PDF_URL,
            "https://example.org/paper", DiscoverySampleJson.hash("original"), 1, 200, "application/pdf", null,
            DiscoverySampleBodyStatus.SAVED, "body-01.bin", DiscoverySampleJson.hash(body), body.size.toLong(), null)), bodyBytesRetained = body.size.toLong())
        assertEquals(DiscoverySampleDecision.SAVED, store.commit(manifest, mapOf("body-01.bin" to body)))
        val file = packages(saved).single()
        val roundtrip = DiscoverySampleJson.mapper.readValue(Files.readAllBytes(file), DiscoverySampleManifest::class.java)
        assertEquals(paper("roundtrip").authors, roundtrip.paper.authors)
        assertArrayEquals(body, Files.readAllBytes(file.parent.resolve("body-01.bin")))
        assertFalse(Files.readString(file).contains("secret"))
        assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(saved)))
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
        System.getProperty("sample.acceptanceDir")?.let { Files.copy(file, Paths.get(it, "store", "saved", "sample.json"), StandardCopyOption.REPLACE_EXISTING)
            Files.copy(file.parent.resolve("body-01.bin"), Paths.get(it, "store", "saved", "body-01.bin"), StandardCopyOption.REPLACE_EXISTING) }
        cases["roundtrip"] = mapOf("stage" to roundtrip.stage, "source" to roundtrip.context.source, "authorRoundtripEqual" to true, "bodyHashEqual" to true)
        val missing = root("missing-body"); val missingManifest = sample("missing").copy(attempts = listOf(manifest.attempts[0].copy(
            bodyStatus = DiscoverySampleBodyStatus.NOT_AVAILABLE, bodyFile = null, bodyBytes = null, bodySha256 = null)))
        assertEquals(DiscoverySampleDecision.SAVED, DiscoverySampleStore(properties(missing), Clock.fixed(now, ZoneOffset.UTC)).commit(missingManifest, emptyMap()))
        cases["missing-body"] = mapOf("bodyStatus" to "NOT_AVAILABLE", "bodyFile" to null)
        val quota = root("quota"); val qp = properties(quota)
        val qs = DiscoverySampleStore(qp, Clock.fixed(now, ZoneOffset.UTC))
        for (i in 1..3) assertEquals(if (i <= 2) DiscoverySampleDecision.SAVED else DiscoverySampleDecision.QUOTA, qs.commit(sample("failure$i"), emptyMap()))
        assertEquals(DiscoverySampleDecision.SAVED, qs.commit(sample("success1", true), emptyMap()))
        assertEquals(DiscoverySampleDecision.QUOTA, DiscoverySampleStore(qp, Clock.fixed(now, ZoneOffset.UTC)).commit(sample("success2", true), emptyMap()))
        cases["quota"] = mapOf("failurePackages" to 2, "successPackages" to 1)
        val expiry = root("expiry"); val ep = properties(expiry)
        DiscoverySampleStore(ep, Clock.fixed(now, ZoneOffset.UTC)).commit(sample("expired"), emptyMap())
        DiscoverySampleStore(ep, Clock.fixed(now.plusSeconds(8 * 86400), ZoneOffset.UTC)).cleanup()
        assertTrue(packages(expiry).isEmpty()); cases["expiry"] = mapOf("packages" to 0)
        val capacity = root("capacity"); val cp = properties(capacity).copy(maxTotalSamples = 1)
        val cs = DiscoverySampleStore(cp, Clock.fixed(now, ZoneOffset.UTC))
        cs.commit(sample("old"), emptyMap()); cs.commit(sample("new"), emptyMap())
        assertEquals(1, packages(capacity).size)
        val size = Files.walk(capacity).use { paths -> paths.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum() }
        assertTrue(size <= cp.maxTotalBytes); cases["capacity"] = mapOf("withinLimit" to true, "logicalBytes" to size)
        val atomic = root("atomic-failure"); val broken = DiscoverySampleStore(properties(atomic), Clock.fixed(now, ZoneOffset.UTC))
        broken.atomicMove = { _, _ -> throw AtomicMoveNotSupportedException("test", "test", "injected") }
        assertEquals(DiscoverySampleDecision.IO_ERROR, broken.commit(sample("atomic"), emptyMap()))
        assertTrue(packages(atomic).isEmpty()); cases["atomic-failure"] = mapOf("visiblePackages" to 0)
        val unwritable = root("unwritable"); Files.createDirectories(unwritable.parent); Files.write(unwritable, byteArrayOf(1))
        val decision = DiscoverySampleStore(properties(unwritable), Clock.fixed(now, ZoneOffset.UTC)).commit(sample("unwritable"), emptyMap())
        assertEquals(DiscoverySampleDecision.STORE_INVALID, decision)
        val ioRoot = root("io-error"); val ioStore = DiscoverySampleStore(properties(ioRoot), Clock.fixed(now, ZoneOffset.UTC)); ioStore.atomicMove = { _, _ -> throw java.io.IOException("injected readonly filesystem") }
        assertEquals(DiscoverySampleDecision.IO_ERROR, ioStore.commit(sample("io"), emptyMap()))
        cases["unwritable"] = mapOf("decision" to "IO_ERROR", "simulation" to "injected filesystem write failure")
        result(cases)
    }
    @Test fun `locks bad packages symlinks and crashed temporary files`() {
        val path = temp.resolve("locked"); val p = properties(path); val store = DiscoverySampleStore(p)
        store.cleanup()
        FileChannel.open(path.resolve(".writer.lock"), StandardOpenOption.WRITE).use { channel -> channel.lock().use {
            assertEquals(DiscoverySampleDecision.LOCK_BUSY, store.commit(sample("lock"), emptyMap())) } }
        val crashed = path.resolve(".tmp-00000000-0000-0000-0000-000000000000"); Files.createDirectory(crashed); Files.write(crashed.resolve("partial"), byteArrayOf(1))
        DiscoverySampleStore(p).cleanup(); assertFalse(Files.exists(crashed))
        val outside = temp.resolve("outside"); Files.createDirectory(outside); Files.write(outside.resolve("untouched"), byteArrayOf(1))
        Files.createSymbolicLink(path.resolve("2"), outside)
        assertEquals(DiscoverySampleDecision.STORE_INVALID, store.commit(sample("symlink"), emptyMap()))
        assertTrue(Files.exists(outside.resolve("untouched")))
        val bad = temp.resolve("bad"); Files.createDirectories(bad.resolve("1").resolve("a".repeat(64)))
        Files.writeString(bad.resolve("1").resolve("a".repeat(64)).resolve("sample.json"), "broken")
        assertNotEquals(DiscoverySampleDecision.SAVED, DiscoverySampleStore(properties(bad)).commit(sample("bad"), emptyMap()))
    }
    @Test fun `metadata is skipped whole and disabled configuration performs no IO`() {
        val path = temp.resolve("metadata")
        val store = DiscoverySampleStore(properties(path).copy(maxMetadataBytes = 1024))
        assertEquals(DiscoverySampleDecision.METADATA_LIMIT, store.commit(sample("large".repeat(1000)), emptyMap()))
        assertTrue(packages(path).isEmpty())
        val disabled = temp.resolve("disabled")
        assertEquals(DiscoverySampleDecision.DISABLED, DiscoverySampleStore(properties(disabled).copy(enabled = false))
            .commit(sample("disabled"), emptyMap()))
        assertFalse(Files.exists(disabled))
        val invalid = temp.resolve("invalid")
        assertEquals(DiscoverySampleDecision.CONFIG_ERROR, DiscoverySampleStore(properties(invalid).copy(maxBodyBytes = 0))
            .commit(sample("invalid"), emptyMap()))
        assertFalse(Files.exists(invalid))
    }
    @Test fun `configuration binding and hard limits`() {
        val bound = Binder(MapConfigurationPropertySource(mapOf("talent-introduction.expert-discovery.samples.enabled" to "true",
            "talent-introduction.expert-discovery.samples.max-body-bytes" to "1024")))
            .bind("talent-introduction.expert-discovery.samples", DiscoverySampleProperties::class.java).get()
        assertTrue(bound.enabled); assertEquals(1024L, bound.maxBodyBytes); assertTrue(bound.valid())
        assertFalse(bound.copy(maxInFlightCaptures = 3).valid()); assertFalse(bound.copy(maxAttemptsPerSample = 17).valid())
        assertFalse(bound.copy(maxBodyBytes = 10485761).valid()); assertFalse(bound.copy(maxMetadataBytes = 1048577).valid())
        assertFalse(bound.copy(maxBodiesPerSample = 3).valid()); assertFalse(bound.copy(maxTotalBytes = 1).valid())
        assertFalse(bound.copy(directory = "relative").valid()); assertFalse(bound.copy(retentionDays = 0).valid())
    }
}
