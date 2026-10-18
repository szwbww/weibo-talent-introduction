package com.weibo.talentintroduction.discovery.sample

import com.weibo.talentintroduction.config.DiscoverySampleProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled
import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.file.attribute.PosixFilePermissions
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.annotation.PostConstruct

@Component
class DiscoverySampleStore(val properties: DiscoverySampleProperties, private val clock: Clock = Clock.systemUTC()) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val counts = ConcurrentHashMap<DiscoverySampleDecision, AtomicLong>()
    internal var atomicMove: (Path, Path) -> Unit = { from, to ->
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
    }
    private val lastDiagnostic = AtomicLong(Long.MIN_VALUE)
    private val root get() = Paths.get(properties.directory).normalize()
    private data class Package(val path: Path, val manifest: DiscoverySampleManifest, val bytes: Long)
    private class InvalidStore : Exception()
    companion object { private val locks = ConcurrentHashMap<Path, ReentrantLock>() }

    fun decision(value: DiscoverySampleDecision): DiscoverySampleDecision {
        if (value == DiscoverySampleDecision.DISABLED) return value
        counts.computeIfAbsent(value) { AtomicLong() }.incrementAndGet()
        if (value != DiscoverySampleDecision.SAVED) {
            val now = clock.millis()
            val previous = lastDiagnostic.get()
            if ((previous == Long.MIN_VALUE || now - previous >= 60000) && lastDiagnostic.compareAndSet(previous, now))
                logger.warn("event=DISCOVERY_SAMPLE_DIAGNOSTIC decision={} processCounts={}", value, counts.mapValues { it.value.get() })
        }
        return value
    }
    fun active(): Boolean {
        if (!properties.enabled) return false
        if (!properties.valid()) { decision(DiscoverySampleDecision.CONFIG_ERROR); return false }
        return true
    }
    private fun privateDirectory(path: Path) {
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    }
    private fun prepareRoot() {
        var current = root.root
        for (part in root) {
            current = current.resolve(part)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw InvalidStore()
            } else privateDirectory(current)
        }
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"))
    }
    private fun write(path: Path, bytes: ByteArray) {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.write(path, bytes, StandardOpenOption.WRITE)
    }
    private fun remove(path: Path, depth: Int = 0, budget: IntArray = intArrayOf(0)) {
        if (depth > 3 || ++budget[0] > 64 || Files.isSymbolicLink(path)) throw InvalidStore()
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) Files.newDirectoryStream(path).use { stream ->
            stream.forEach { remove(it, depth + 1, budget) }
        }
        Files.delete(path)
    }
    private fun scan(): MutableList<Package> {
        val result = mutableListOf<Package>()
        var entries = 0
        Files.newDirectoryStream(root).use { stream -> for (execution in stream) {
            if (++entries > properties.maxTotalSamples * 4L + 1024) throw InvalidStore()
            val name = execution.fileName.toString()
            if (name == ".writer.lock") continue
            if (Files.isSymbolicLink(execution)) throw InvalidStore()
            if (name.startsWith(".tmp-") && name.removePrefix(".tmp-").matches(Regex("[0-9a-f-]{36}"))) { remove(execution); continue }
            if (!name.matches(Regex("[1-9][0-9]*")) || !Files.isDirectory(execution, LinkOption.NOFOLLOW_LINKS)) throw InvalidStore()
            Files.newDirectoryStream(execution).use { packages -> for (path in packages) {
                if (++entries > properties.maxTotalSamples * 4L + 1024) throw InvalidStore()
                if (Files.isSymbolicLink(path) || !path.fileName.toString().matches(Regex("[0-9a-f]{64}"))) throw InvalidStore()
                val json = path.resolve("sample.json")
                if (Files.isSymbolicLink(json) || Files.size(json) > 1048576) throw InvalidStore()
                val manifest = try {
                    DiscoverySampleJson.mapper.readValue(Files.readAllBytes(json), DiscoverySampleManifest::class.java)
                } catch (_: Exception) { throw InvalidStore() }
                if (manifest.schemaVersion != 1 || manifest.stage != "EXTRACTION" || manifest.sampleId != path.fileName.toString() ||
                    manifest.context.executionId.toString() != name || !manifest.context.valid()) throw InvalidStore()
                var bytes = Files.size(json)
                val expected = mutableSetOf("sample.json")
                for (attempt in manifest.attempts) if (attempt.bodyStatus == DiscoverySampleBodyStatus.SAVED) {
                    val file = attempt.bodyFile ?: throw InvalidStore()
                    if (!file.matches(Regex("body-0[12]\\.bin")) || !expected.add(file)) throw InvalidStore()
                    val body = path.resolve(file)
                    if (Files.isSymbolicLink(body) || Files.size(body) > 10485760 || Files.size(body) != attempt.bodyBytes) throw InvalidStore()
                    if (DiscoverySampleJson.hash(Files.readAllBytes(body)) != attempt.bodySha256) throw InvalidStore()
                    bytes += Files.size(body)
                }
                Files.newDirectoryStream(path).use { files -> files.forEach { if (it.fileName.toString() !in expected) throw InvalidStore() } }
                result.add(Package(path, manifest, bytes))
            } }
        } }
        return result
    }
    private fun locked(action: () -> DiscoverySampleDecision): DiscoverySampleDecision {
        if (!properties.enabled) return DiscoverySampleDecision.DISABLED
        if (!properties.valid()) return decision(DiscoverySampleDecision.CONFIG_ERROR)
        val lock = locks.computeIfAbsent(root) { ReentrantLock() }
        if (!lock.tryLock()) return decision(DiscoverySampleDecision.LOCK_BUSY)
        try {
            prepareRoot()
            val lockFile = root.resolve(".writer.lock")
            if (Files.isSymbolicLink(lockFile)) throw InvalidStore()
            if (!Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS)) write(lockFile, byteArrayOf())
            FileChannel.open(lockFile, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val fileLock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                if (fileLock == null) return decision(DiscoverySampleDecision.LOCK_BUSY)
                fileLock.use { return decision(action()) }
            }
        } catch (_: InvalidStore) { return decision(DiscoverySampleDecision.STORE_INVALID) }
        catch (_: Exception) { return decision(DiscoverySampleDecision.IO_ERROR) }
        finally { lock.unlock() }
    }
    private fun retained(): MutableList<Package> {
        val packages = scan()
        val cutoff = clock.instant().minus(properties.retentionDays.toLong(), ChronoUnit.DAYS)
        val expired = packages.filter { Instant.parse(it.manifest.capturedAt).isBefore(cutoff) }
        expired.forEach { remove(it.path) }; packages.removeAll(expired.toSet())
        return packages
    }
    @PostConstruct
    fun startup() { cleanup() }
    @Scheduled(fixedDelay = 3600000)
    fun cleanup() { if (properties.enabled) locked { retained(); DiscoverySampleDecision.SAVED } }

    fun commit(manifest: DiscoverySampleManifest, bodies: Map<String, ByteArray>): DiscoverySampleDecision = locked {
        if (!manifest.context.valid()) return@locked DiscoverySampleDecision.INVALID_CONTEXT
        val json = DiscoverySampleJson.bounded(manifest, properties.maxMetadataBytes)
            ?: return@locked DiscoverySampleDecision.METADATA_LIMIT
        if (manifest.schemaVersion != 1 || manifest.stage != "EXTRACTION" ||
            manifest.inputSha256 != DiscoverySampleJson.hash(DiscoverySampleJson.canonical(manifest.paper)) ||
            manifest.sampleId != DiscoverySampleJson.hash("${manifest.context.executionId}\n${manifest.context.source}\n${manifest.inputSha256}"))
            return@locked DiscoverySampleDecision.STORE_INVALID
        val expected = manifest.attempts.filter { it.bodyStatus == DiscoverySampleBodyStatus.SAVED }
        if (expected.size > properties.maxBodiesPerSample || manifest.attempts.size > properties.maxAttemptsPerSample ||
            expected.map { it.bodyFile }.toSet() != bodies.keys || manifest.bodyBytesRetained != bodies.values.sumOf { it.size.toLong() })
            return@locked DiscoverySampleDecision.STORE_INVALID
        if (manifest.attempts.map { it.sequence } != (1..manifest.attempts.size).toList() ||
            manifest.attempts.any { attempt ->
                attempt.elapsedMs < 0 || attempt.url != DiscoverySampleJson.scrub(attempt.url) ||
                    when (attempt.bodyStatus) {
                        DiscoverySampleBodyStatus.SAVED -> attempt.bodyFile == null || attempt.bodySha256 == null || attempt.bodyBytes == null
                        DiscoverySampleBodyStatus.NOT_AVAILABLE -> attempt.bodyFile != null || attempt.bodySha256 != null || attempt.bodyBytes != null
                        DiscoverySampleBodyStatus.BODY_LIMIT -> attempt.bodyFile != null || attempt.bodySha256 != null || attempt.bodyBytes == null || attempt.bodyBytes < 0
                    }
            } || manifest.paper != DiscoverySampleJson.paper(manifest.paper))
            return@locked DiscoverySampleDecision.STORE_INVALID
        for (attempt in expected) {
            val body = bodies[attempt.bodyFile] ?: return@locked DiscoverySampleDecision.STORE_INVALID
            if (!attempt.bodyFile.orEmpty().matches(Regex("body-0[12]\\.bin")) || body.size > properties.maxBodyBytes ||
                body.size.toLong() != attempt.bodyBytes || DiscoverySampleJson.hash(body) != attempt.bodySha256)
                return@locked DiscoverySampleDecision.STORE_INVALID
        }
        val packages = retained()
        if (packages.any { it.manifest.sampleId == manifest.sampleId }) return@locked DiscoverySampleDecision.DUPLICATE
        val limit = if (manifest.bucket == DiscoverySampleBucket.SUCCESS_CONTROL) properties.maxSuccessControlsPerRun else properties.maxSamplesPerBucketPerRun
        if (packages.count { it.manifest.context.executionId == manifest.context.executionId && it.manifest.bucket == manifest.bucket } >= limit)
            return@locked DiscoverySampleDecision.QUOTA
        val needed = json.size + manifest.bodyBytesRetained
        for (old in packages.sortedBy { Instant.parse(it.manifest.capturedAt) }) {
            if (packages.size < properties.maxTotalSamples && packages.sumOf { it.bytes } + needed <= properties.maxTotalBytes) break
            remove(old.path); packages.remove(old)
        }
        if (Files.getFileStore(root).usableSpace < needed) return@locked DiscoverySampleDecision.IO_ERROR
        val execution = root.resolve(manifest.context.executionId.toString())
        if (!Files.exists(execution)) privateDirectory(execution)
        val temporary = root.resolve(".tmp-${UUID.randomUUID()}")
        privateDirectory(temporary)
        try {
            bodies.forEach { (name, bytes) -> write(temporary.resolve(name), bytes) }
            write(temporary.resolve("sample.json"), json)
            bodies.forEach { (name, bytes) -> if (DiscoverySampleJson.hash(Files.readAllBytes(temporary.resolve(name))) != DiscoverySampleJson.hash(bytes)) throw InvalidStore() }
            atomicMove(temporary, execution.resolve(manifest.sampleId))
            logger.info("event=DISCOVERY_SAMPLE_CAPTURE decision=SAVED executionId={} sampleId={} bucket={}", manifest.context.executionId, manifest.sampleId, manifest.bucket)
            DiscoverySampleDecision.SAVED
        } finally { if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) remove(temporary) }
    }
}
