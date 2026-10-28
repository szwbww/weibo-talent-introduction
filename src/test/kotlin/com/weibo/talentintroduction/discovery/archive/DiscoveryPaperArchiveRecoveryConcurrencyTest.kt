package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.weibo.talentintroduction.config.DiscoveryPaperArchiveProperties
import com.weibo.talentintroduction.config.DiscoverySampleProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real files and service; only JDBC is replaced so latch tests need no external database. */
class DiscoveryPaperArchiveRecoveryConcurrencyTest {
    @TempDir lateinit var directory: Path
    private val pool = Executors.newFixedThreadPool(3)
    private val release = CountDownLatch(1)
    private val now = Instant.now()
    private val blobs = ConcurrentHashMap<String, ArchiveBlob>()
    private val records = ConcurrentHashMap<Long, ArchiveRecord>()
    private val attempts = ConcurrentHashMap<Long, ArchiveAttempt>()
    private val repository = Mockito.mock(DiscoveryPaperArchiveRepository::class.java)
    private lateinit var root: Path
    private lateinit var service: DiscoveryPaperArchiveService

    @AfterEach fun cleanup() {
        release.countDown()
        pool.shutdownNow()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
    }

    private fun fixture() {
        root = directory.toRealPath().resolve("archive")
        Files.createDirectories(root.resolve("blobs"))
        Files.createDirectories(root.resolve(".tmp"))
        Mockito.`when`(repository.blobs()).thenAnswer { blobs.values.sortedByDescending { it.sha256 } }
        Mockito.`when`(repository.records()).thenAnswer { records.values.toList() }
        Mockito.`when`(repository.blob(Mockito.anyString())).thenAnswer { blobs[it.getArgument(0)] }
        Mockito.`when`(repository.record(Mockito.anyLong())).thenAnswer { records[it.getArgument(0)] }
        Mockito.`when`(repository.attempt(Mockito.anyLong())).thenAnswer { attempts[it.getArgument(0)] }
        Mockito.`when`(repository.attempts(Mockito.anyLong())).thenAnswer { call ->
            attempts.values.filter { it.recordId == call.getArgument<Long>(0) }.sortedBy { it.id }
        }
        Mockito.`when`(repository.references(Mockito.anyString())).thenAnswer { call ->
            attempts.values.filter { it.blobSha256 == call.getArgument<String>(0) && it.status == ArchiveAttemptStatus.SAVED }
        }
        Mockito.doAnswer { call ->
            val hash = call.getArgument<String>(0)
            blobs.computeIfPresent(hash) { _, blob -> blob.copy(state = call.getArgument(1)) }
            null
        }.`when`(repository).blobState(Mockito.anyString(), any())
        service = DiscoveryPaperArchiveService(repository, DiscoveryPaperArchiveProperties(root.toString()),
            DiscoverySampleProperties(directory = directory.toRealPath().resolve("samples").toString()), jacksonObjectMapper().findAndRegisterModules())
    }

    private fun body(id: Long, text: String): ArchiveBlob {
        val bytes = text.toByteArray()
        val hash = DiscoveryPaperArchiveService.sha(bytes)
        val blob = ArchiveBlob(hash, bytes.size.toLong(), "text/plain", ArchiveBlobState.AVAILABLE, now)
        blobs[hash] = blob
        records[id] = ArchiveRecord(id, 1, "PUBMED", "paper-$id", "{\"schemaVersion\":1}", null, null, false, 1, now, now)
        attempts[id] = ArchiveAttempt(id, id, "attempt-$id", ArchiveAttemptKind.EMBEDDED_TEXT, null,
            ArchiveAttemptStatus.SAVED, hash, false, null, null, blob.sizeBytes, now, now)
        val path = path(blob)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return blob
    }

    private fun path(blob: ArchiveBlob) = root.resolve("blobs/${blob.sha256.take(2)}/${blob.sha256}.bin")
    private fun pauseRecovery(event: String = "RECOVERY_BEFORE_HASH"): CountDownLatch {
        val entered = CountDownLatch(1)
        service.failpoint = { point ->
            if (point == event) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "recovery barrier timed out" }
            }
        }
        return entered
    }

    @Test fun `another blob analysis finishes before slow recovery hash is released`() {
        fixture()
        body(1, "slow recovery")
        body(2, "analysis still progresses")
        val entered = pauseRecovery()
        val recovery = pool.submit { service.recover() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val analysis = pool.submit<String> { service.analysisLease(2).use { it.bodies.single().input.bufferedReader().readText() } }
        try {
            assertEquals("analysis still progresses", completes(analysis))
            assertEquals(1L, release.count, "analysis must finish with recovery still paused")
        } finally { release.countDown() }
        recovery.get(5, TimeUnit.SECONDS)
    }

    @Test fun `overlapping recovery returns without running another scan`() {
        fixture()
        body(1, "single flight")
        val entered = pauseRecovery()
        val scans = AtomicInteger()
        val barrier = service.failpoint
        service.failpoint = { point -> if (point == "RECOVERY_BEFORE_HASH") scans.incrementAndGet(); barrier(point) }
        val first = pool.submit { service.recover() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            completes(pool.submit { service.recover() })
            assertEquals(1, scans.get())
        } finally { release.countDown() }
        first.get(5, TimeUnit.SECONDS)
        assertEquals(1, scans.get())
    }

    @Test fun `recovery hash reader prevents deletion until hashing completes`() {
        fixture()
        val blob = body(1, "pinned hash")
        val entered = pauseRecovery()
        val recovery = pool.submit { service.recover() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            val preview = completes(pool.submit<ArchiveDeletePreview> { service.deletePreview(listOf(1)) })
            assertEquals(listOf(blob.sha256), preview.busyFiles)
            assertEquals(listOf("BUSY"), preview.items.single().reasonCodes)
            val result = service.delete(ArchiveDeleteRequest(preview.ids, preview.snapshotHash))
            assertEquals(0, result.deletedFiles)
            assertTrue(Files.exists(path(blob)))
        } finally { release.countDown() }
        recovery.get(5, TimeUnit.SECONDS)
        assertEquals(1, service.deletePreview(listOf(1)).deletableFiles, "recovery pin must be released")
    }

    @Test fun `analysis reader prevents recovery deleting a blob after its references disappear`() {
        fixture()
        val blob = body(1, "analysis pin")
        service.analysisLease(1).use { lease ->
            attempts.clear()
            service.recover()
            assertTrue(Files.exists(path(blob)))
            assertEquals("analysis pin", lease.bodies.single().input.bufferedReader().readText())
        }
        service.recover()
        assertFalse(Files.exists(path(blob)))
    }

    @Test fun `corrupt content becomes missing and hash failure releases single flight and reader pin`() {
        fixture()
        val blob = body(1, "correct content")
        Files.write(path(blob), "corrupt content".toByteArray())
        service.failpoint = { if (it == "RECOVERY_BEFORE_HASH") throw java.io.IOException("hash fixture failure") }
        assertThrows(ArchiveException::class.java) { service.recover() }
        service.failpoint = {}
        assertEquals(1, service.deletePreview(listOf(1)).deletableFiles, "failed recovery must release its pin")
        service.recover()
        assertEquals(ArchiveBlobState.MISSING, blobs[blob.sha256]!!.state)
    }

    @Test fun `stale corrupt hash cannot mark an atomically replaced valid file missing`() {
        fixture()
        val blob = body(1, "correct content")
        Files.write(path(blob), "corrupt content".toByteArray())
        val entered = pauseRecovery("RECOVERY_AFTER_HASH")
        val recovery = pool.submit { service.recover() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            val replacement = root.resolve(".tmp/replacement")
            Files.write(replacement, "correct content".toByteArray())
            Files.move(replacement, path(blob), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { release.countDown() }
        recovery.get(5, TimeUnit.SECONDS)
        assertEquals(ArchiveBlobState.AVAILABLE, blobs[blob.sha256]!!.state)
        service.analysisLease(1).use { assertEquals("correct content", it.bodies.single().input.bufferedReader().readText()) }
    }

    @Test fun `filesystem without file keys rechecks repaired content before marking missing`() {
        fixture()
        val blob = body(1, "correct content")
        Files.write(path(blob), "corrupt content".toByteArray())
        val original = Files.readAttributes(path(blob), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        // Some providers have no file key and coarse timestamps: replacement can look identical.
        service.recoveryAttributes = { path ->
            val current = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            object : BasicFileAttributes by current {
                override fun fileKey(): Any? = null
                override fun lastModifiedTime() = original.lastModifiedTime()
                override fun creationTime() = original.creationTime()
            }
        }
        val entered = pauseRecovery("RECOVERY_AFTER_HASH")
        val recovery = pool.submit { service.recover() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            Files.write(path(blob), "correct content".toByteArray())
        } finally { release.countDown() }
        recovery.get(5, TimeUnit.SECONDS)
        assertEquals(ArchiveBlobState.AVAILABLE, blobs[blob.sha256]!!.state)
        service.failpoint = {}
        Files.write(path(blob), "corrupt content".toByteArray())
        service.recover()
        assertEquals(ArchiveBlobState.MISSING, blobs[blob.sha256]!!.state)
    }

    private fun <T> completes(future: Future<T>): T = try {
        future.get(2, TimeUnit.SECONDS)
    } catch (_: TimeoutException) {
        fail("operation blocked while recovery hash remained paused")
    }

    private fun <T> any(): T = Mockito.any<T>()
}
