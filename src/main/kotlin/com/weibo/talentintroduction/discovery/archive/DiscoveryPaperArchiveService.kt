package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import com.weibo.talentintroduction.config.DiscoveryPaperArchiveProperties
import com.weibo.talentintroduction.config.DiscoverySampleProperties
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal data class ArchiveDisk(val total: Long, val unallocated: Long, val usable: Long)

@Service
class DiscoveryPaperArchiveService(
    private val repository: DiscoveryPaperArchiveRepository,
    private val properties: DiscoveryPaperArchiveProperties,
    private val sampleProperties: DiscoverySampleProperties,
    private val mapper: ObjectMapper
) {
    private val lock = ReentrantLock()
    private val activeWriters = mutableSetOf<Path>()
    internal var failpoint: (String) -> Unit = {}
    internal var diskProbe: (Path) -> ArchiveDisk = { path -> Files.getFileStore(path).let { ArchiveDisk(it.totalSpace, it.unallocatedSpace, it.usableSpace) } }
    private val directoryPermissions = PosixFilePermissions.fromString("rwx------")
    private val filePermissions = PosixFilePermissions.fromString("rw-------")

    private fun root(): Path {
        val path = Paths.get(properties.directory)
        val old = Paths.get(sampleProperties.directory).toAbsolutePath().normalize()
        if (!path.isAbsolute || path != path.normalize() || path.startsWith(old) || old.startsWith(path)) throw ArchiveException("INVALID_DIRECTORY", 503)
        checkPath(path)
        return path
    }
    private fun checkPath(path: Path) {
        var current: Path? = path
        while (current != null) {
            if (Files.isSymbolicLink(current)) throw ArchiveException("UNSAFE_PATH", 503)
            current = current.parent
        }
    }
    private fun directory(path: Path) {
        checkPath(path)
        Files.createDirectories(path, PosixFilePermissions.asFileAttribute(directoryPermissions))
        Files.setPosixFilePermissions(path, directoryPermissions)
    }
    private fun prepareRoot(): Path = root().also { directory(it); directory(it.resolve("blobs")); directory(it.resolve(".tmp")) }
    private fun blobPath(hash: String): Path {
        if (!hash.matches(Regex("[a-f0-9]{64}"))) throw ArchiveException("INVALID_HASH", 400)
        return root().resolve("blobs").resolve(hash.substring(0,2)).resolve("$hash.bin").also { checkPath(it) }
    }
    private fun <T> mutation(create: Boolean = false, action: () -> T): T = lock.withLock {
        try {
            if (lock.holdCount > 1) return@withLock action()
            val base = if (create) prepareRoot() else root()
            if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) return@withLock action()
            val lockPath = base.resolve(".archive.lock"); checkPath(lockPath)
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                Files.setPosixFilePermissions(lockPath, filePermissions)
                val fileLock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                if (fileLock == null) throw ArchiveException("ARCHIVE_BUSY", 503)
                fileLock.use { action() }
            }
        } catch (e: DataAccessException) { throw ArchiveException("DB_UNAVAILABLE", 503) }
        catch (e: java.io.IOException) { throw ArchiveException("STORAGE_UNAVAILABLE", 503) }
    }
    private fun id(value: Long) { if (value <= 0) throw ArchiveException("INVALID_ID", 400) }
    private fun record(id: Long): ArchiveRecord { id(id); return repository.record(id) ?: throw ArchiveException("NOT_FOUND", 404) }
    private fun attempt(recordId: Long, attemptId: Long): ArchiveAttempt {
        record(recordId); id(attemptId)
        return repository.attempt(attemptId)?.takeIf { it.recordId == recordId } ?: throw ArchiveException("NOT_FOUND", 404)
    }
    private fun safeUrl(value: String?): String? = value?.let {
        try { URI(it).takeIf { u -> u.scheme in listOf("https", "http") && u.host != null }?.let { u -> URI(u.scheme, null, u.host, u.port, u.path, null, null).toASCIIString() } }
        catch (_: Exception) { null }
    }
    private fun clean(node: JsonNode, metadata: Boolean = false): JsonNode {
        if (node.isObject) {
            val result = mapper.createObjectNode()
            node.fields().forEachRemaining { (key, value) ->
                val sensitive = key.replace("_", "").replace("-", "").lowercase() in SENSITIVE_FIELDS
                if (!(metadata && key.equals("fullText", true)) && !sensitive) {
                    result.set<JsonNode>(key, clean(value, metadata))
                }
            }
            return result
        }
        if (node.isArray) return mapper.createArrayNode().also { array -> node.forEach { array.add(clean(it, metadata)) } }
        if (node.isTextual) {
            val value = node.asText()
            if (value.startsWith("/")) return mapper.nullNode()
            if (value.matches(Regex("(?is)^https?://.*"))) return safeUrl(value)?.let { mapper.nodeFactory.textNode(it) } ?: mapper.nullNode()
        }
        return node.deepCopy<JsonNode>()
    }
    private fun json(node: JsonNode, max: Int, metadata: Boolean = false): String {
        if (!node.isObject || node.path("schemaVersion").asInt() != 1) throw ArchiveException("INVALID_SCHEMA", 400)
        val value = mapper.writeValueAsString(clean(node, metadata))
        if (value.toByteArray(Charsets.UTF_8).size > max) throw ArchiveException("METADATA_TOO_LARGE", 413)
        return value
    }
    fun beginRecord(executionId: Long, sourceName: String, paperKey: String, metadata: JsonNode): ArchiveRecord = mutation {
        id(executionId)
        if (!sourceName.matches(Regex("[A-Z0-9_]{1,32}")) || paperKey.isBlank() || paperKey.length > 160) throw ArchiveException("INVALID_RECORD", 400)
        repository.beginRecord(executionId, sourceName, paperKey, json(metadata, 512 * 1024, true), Instant.now())
    }
    fun beginAttempt(recordId: Long, extractionId: UUID, sequence: Int, kind: ArchiveAttemptKind, sourceUrl: String? = null): ArchiveAttempt = mutation {
        record(recordId)
        if (sequence < 0) throw ArchiveException("INVALID_ATTEMPT", 400)
        repository.beginAttempt(recordId, sha("$recordId:$extractionId:$sequence".toByteArray()), kind, safeUrl(sourceUrl), Instant.now())
    }
    fun finishAttempt(attemptId: Long, status: ArchiveAttemptStatus, reasonCode: String? = null, httpStatus: Int? = null, completedBytes: Long = 0): ArchiveAttempt = mutation {
        id(attemptId)
        if (status !in listOf(ArchiveAttemptStatus.DOWNLOAD_FAILED, ArchiveAttemptStatus.TOO_LARGE, ArchiveAttemptStatus.SAVE_ERROR) || completedBytes < 0 || (reasonCode != null && !reasonCode.matches(Regex("[A-Z0-9_]{1,64}")))) throw ArchiveException("INVALID_ATTEMPT", 400)
        repository.finishAttempt(attemptId, status, reasonCode, httpStatus, completedBytes, Instant.now())
    }
    fun finishExtraction(recordId: Long, outcome: JsonNode): ArchiveRecord = mutation {
        record(recordId); repository.finishExtraction(recordId, json(outcome, 4 * 1024 * 1024), Instant.now())
    }
    fun saveAnalysis(recordId: Long, revision: Long, analysis: JsonNode): ArchiveRecord = mutation {
        record(recordId); repository.saveAnalysis(recordId, revision, json(analysis, 4 * 1024 * 1024), Instant.now())
    }
    fun saveBody(attemptId: Long, bytes: ByteArray, mediaType: String): ArchiveAttempt = mutation(create = true) {
        id(attemptId)
        val a = repository.attempt(attemptId) ?: throw ArchiveException("NOT_FOUND", 404)
        if (a.status == ArchiveAttemptStatus.SAVED) {
            val b = a.blobSha256?.let { repository.blob(it) } ?: throw ArchiveException("SOURCE_MISSING", 410)
            if (!validBlob(b, true) || sha(bytes) != b.sha256) throw ArchiveException("ATTEMPT_IDENTITY_CONFLICT", 409)
            return@mutation a
        }
        if (a.status != ArchiveAttemptStatus.STARTED) throw ArchiveException("ATTEMPT_FINISHED", 409)
        val settings = repository.setting()
        if (!properties.allowed || !settings.enabled) throw ArchiveException("ARCHIVE_DISABLED", 409)
        val hash = sha(bytes); val existing = repository.blob(hash)
        if (existing?.state == ArchiveBlobState.DELETING) throw ArchiveException("DELETE_IN_PROGRESS", 409)
        if (existing?.state == ArchiveBlobState.AVAILABLE && validBlob(existing, true)) {
            repository.saveBody(attemptId, hash, existing.sizeBytes, existing.mediaType, true, Instant.now())
            return@mutation repository.attempt(attemptId)!!
        }
        val size = sizes(); val disk = diskProbe(root())
        if (bytes.size.toLong() > settings.maxBytes - size.first - size.second || disk.usable - bytes.size < settings.minFreeBytes) throw ArchiveException("ARCHIVE_STORAGE_BLOCKED", 409)
        val media = if (mediaType.substringBefore(';').lowercase() == "application/pdf" && bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray())) "application/pdf" else "text/plain"
        val temp = root().resolve(".tmp").resolve(UUID.randomUUID().toString())
        activeWriters.add(temp)
        try {
            FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
                Files.setPosixFilePermissions(temp, filePermissions)
                val buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            failpoint("BEFORE_RENAME")
            if (Files.size(temp) != bytes.size.toLong() || hashFile(temp) != hash) throw ArchiveException("SAVE_ERROR", 503)
            val final = blobPath(hash); directory(final.parent)
            Files.move(temp, final, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Files.setPosixFilePermissions(final, filePermissions)
            FileChannel.open(final.parent, StandardOpenOption.READ).use { it.force(true) }
            failpoint("AFTER_RENAME")
            val after = sizes(); val afterDisk = diskProbe(root())
            if (after.first + after.second > settings.maxBytes || afterDisk.usable < settings.minFreeBytes) throw ArchiveException("ARCHIVE_STORAGE_BLOCKED", 409)
            repository.saveBody(attemptId, hash, bytes.size.toLong(), media, false, Instant.now())
            repository.attempt(attemptId)!!
        } catch (e: Exception) {
            // Never remove a final file here: it may be an older committed/shared blob.
            try { repository.finishAttempt(attemptId, ArchiveAttemptStatus.SAVE_ERROR, "SAVE_ERROR", null, 0, Instant.now()) } catch (_: Exception) { }
            if (e is ArchiveException) throw e
            if (e is DataAccessException) throw ArchiveException("DB_UNAVAILABLE", 503)
            throw ArchiveException("SAVE_ERROR", 503)
        } finally { activeWriters.remove(temp); Files.deleteIfExists(temp) }
    }
    private fun hashFile(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return hex(digest.digest())
    }
    private fun validBlob(blob: ArchiveBlob, hash: Boolean = false): Boolean {
        if (blob.state != ArchiveBlobState.AVAILABLE) return false
        val path = blobPath(blob.sha256)
        val valid = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == blob.sizeBytes && (!hash || hashFile(path) == blob.sha256)
        if (!valid) repository.blobState(blob.sha256, ArchiveBlobState.MISSING)
        return valid
    }
    private fun sizes(): Triple<Long, Long, Long> {
        val base = root(); if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) return Triple(0, 0, 0)
        var blobs = 0L; var temp = 0L; var files = 0L
        Files.walk(base).use { stream -> stream.forEach { path ->
            checkPath(path)
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                if (path.startsWith(base.resolve("blobs"))) { blobs += Files.size(path); files++ }
                else if (path.startsWith(base.resolve(".tmp"))) temp += Files.size(path)
            }
        } }
        return Triple(blobs, temp, files)
    }
    private fun nearestParent(path: Path): Path {
        var p = path
        while (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) p = p.parent ?: throw ArchiveException("STORAGE_UNAVAILABLE", 503)
        checkPath(p); return p
    }
    private fun storage(s: ArchiveSetting): ArchiveStorage {
        val available = try { Files.isDirectory(root(), LinkOption.NOFOLLOW_LINKS) } catch (_: Exception) { false }
        val measured = Instant.now()
        val size = try { sizes() } catch (_: Exception) { null }
        val disk = try { diskProbe(nearestParent(root())) } catch (_: Exception) { null }
        val enabled = s.enabled && properties.allowed
        val gate = when {
            size == null || disk == null -> ArchiveGate.UNAVAILABLE
            !enabled -> ArchiveGate.DISABLED
            !available || size.first + size.second > s.maxBytes || disk.usable < s.minFreeBytes -> ArchiveGate.BLOCKED
            else -> ArchiveGate.READY
        }
        val reason = when (gate) { ArchiveGate.READY -> null; ArchiveGate.DISABLED -> "ARCHIVE_DISABLED"; ArchiveGate.UNAVAILABLE -> "STORAGE_UNAVAILABLE"; else -> "ARCHIVE_STORAGE_BLOCKED" }
        return ArchiveStorage(available, size?.let { it.first + it.second }, size?.first, size?.second, size?.third,
            repository.records().count { it.protected }.toLong(), disk?.total, disk?.let { it.total - it.unallocated }, disk?.usable,
            disk?.let { maxOf(0, it.unallocated - it.usable) }, s.maxBytes, s.minFreeBytes, enabled, gate, reason, measured)
    }
    fun settings(): ArchiveSettingsResponse = mutation {
        val s = repository.setting(); val storage = storage(s)
        ArchiveSettingsResponse(s.revision, s.enabled, s.maxBytes, s.minFreeBytes, s.enabled && properties.allowed && storage.directoryAvailable,
            storage.gate, storage.reasonCode, s.updatedAt)
    }
    fun updateSettings(request: ArchiveSettingsRequest): ArchiveSettingsResponse = mutation(create = request.enabled && properties.allowed) {
        ArchiveSetting(request.revision, request.enabled, request.maxBytes, request.minFreeBytes).validate()
        repository.saveSetting(request, Instant.now()); settings()
    }
    fun stats(executionId: Long? = null): ArchiveStats = mutation {
        executionId?.let { id(it) }
        repository.blobs().filter { it.state == ArchiveBlobState.AVAILABLE }.forEach { validBlob(it) }
        ArchiveStats(executionId, storage(repository.setting()), repository.counts(executionId))
    }
    private fun row(r: ArchiveRecord): ArchiveRow {
        val paper = mapper.readTree(r.metadataJson); val attempts = repository.attempts(r.id)
        val readable = attempts.filter { it.status == ArchiveAttemptStatus.SAVED && it.blobSha256?.let { hash -> repository.blob(hash)?.let { validBlob(it) } } == true }
        val state = when {
            attempts.any { it.status == ArchiveAttemptStatus.SAVE_ERROR } -> ArchiveRecordState.SAVE_ERROR
            readable.isNotEmpty() -> ArchiveRecordState.STORED
            attempts.any { it.status == ArchiveAttemptStatus.STARTED } -> ArchiveRecordState.PROCESSING
            attempts.isNotEmpty() && attempts.all { it.status == ArchiveAttemptStatus.DELETED } -> ArchiveRecordState.DELETED
            else -> ArchiveRecordState.NOT_STORED
        }
        val outcome = r.outcomeJson?.let { mapper.readTree(it) }
        val summary = outcome?.let { o ->
            fun count(key: String): Int = o.path(key).let { if (it.isArray) it.size() else it.asInt(0) }
            ArchiveExtractionSummary(count("emails"), count("resolved"), count("unresolved"))
        }
        val analysis = r.analysisJson?.let { mapper.readTree(it) }
        return ArchiveRow(r.id,r.revision,r.executionId,r.sourceName,r.paperKey,paper.path("title").takeUnless { it.isMissingNode || it.isNull }?.asText(),paper.path("doi").takeUnless { it.isMissingNode || it.isNull }?.asText(),
            (paper.get("pubYear") ?: paper.get("year"))?.takeIf { it.isIntegralNumber }?.asInt(),state,r.protected,
            readable.mapNotNull { it.blobSha256 }.distinct().sumOf { repository.blob(it)!!.sizeBytes },attempts.size,readable.map { it.id },r.createdAt,r.updatedAt,summary,analysis?.get("summary") ?: analysis)
    }
    fun list(query: ArchiveQuery): ArchivePage = mutation {
        if (query.page < 0 || query.size !in listOf(20,50,100) || (query.q?.length ?: 0) > 200) throw ArchiveException("INVALID_QUERY", 400)
        query.executionId?.let { id(it) }
        repository.blobs().filter { it.state == ArchiveBlobState.AVAILABLE }.forEach { validBlob(it) }
        val (records, total) = repository.list(query)
        ArchivePage(records.map { row(it) }, total, query.page, query.size)
    }
    fun detail(id: Long): ArchiveDetail = mutation {
        val r = record(id); val row = row(r)
        ArchiveDetail(row, clean(mapper.readTree(r.metadataJson)), repository.attempts(id).map { a ->
            ArchiveAttemptDetail(a.id,a.kind,a.status,safeUrl(a.sourceUrl),a.blobSha256,a.blobSha256?.let { repository.blob(it)?.mediaType },a.completedBytes,a.reused,a.httpStatus,a.reasonCode,a.id in row.readableAttemptIds)
        }, r.outcomeJson?.let { clean(mapper.readTree(it)) },r.analysisJson?.let { clean(mapper.readTree(it)) })
    }
    /** Streams under the same lock as deletion, with no absolute path in the public descriptor. */
    fun <T> withContent(recordId: Long, attemptId: Long, action: (ArchiveContent, InputStream) -> T): T = mutation {
        val a = attempt(recordId, attemptId)
        if (a.status == ArchiveAttemptStatus.DELETED) throw ArchiveException("SOURCE_DELETED", 410)
        val b = a.blobSha256?.let { repository.blob(it) } ?: throw ArchiveException("SOURCE_MISSING", 410)
        if (a.status != ArchiveAttemptStatus.SAVED || !validBlob(b)) throw ArchiveException("SOURCE_MISSING", 410)
        Files.newInputStream(blobPath(b.sha256), LinkOption.NOFOLLOW_LINKS).use { input ->
            val magic = ByteArray(5); val count = input.read(magic)
            val pdf = b.mediaType == "application/pdf" && count == 5 && magic.contentEquals("%PDF-".toByteArray())
            Files.newInputStream(blobPath(b.sha256), LinkOption.NOFOLLOW_LINKS).use { body -> action(ArchiveContent(if (pdf) "application/pdf" else "text/plain;charset=UTF-8",b.sizeBytes,"paper-$recordId-$attemptId.${if (pdf) "pdf" else "txt"}"), body) }
        }
    }
    private fun ids(ids: List<Long>): List<Long> {
        if (ids.isEmpty() || ids.size > 200 || ids.any { it <= 0 } || ids.distinct().size != ids.size) throw ArchiveException("INVALID_IDS", 400)
        return ids.sorted()
    }
    fun protection(request: ArchiveProtectionRequest): ArchiveProtectionResult = mutation {
        ids(request.items.map { it.id })
        var changed = 0; var unchanged = 0; val conflicts = mutableListOf<ArchiveConflict>(); val items = mutableListOf<ArchiveProtectedItem>()
        request.items.forEach { item ->
            try {
                repository.transaction {
                    val r = record(item.id)
                    if (repository.attempts(r.id).any { a -> a.blobSha256?.let { repository.blob(it)?.state == ArchiveBlobState.DELETING } == true }) throw ArchiveException("DELETE_IN_PROGRESS", 409)
                    if (item.revision != r.revision) throw ArchiveException("REVISION_CONFLICT", 409)
                    val updated = if (r.protected == request.protected) { unchanged++; r } else repository.protect(r.id,r.revision,request.protected,Instant.now()).also { changed++ }
                    items += ArchiveProtectedItem(updated.id,updated.protected,updated.revision)
                    true
                }
            } catch (e: ArchiveException) { conflicts += ArchiveConflict(item.id,e.code) }
        }
        ArchiveProtectionResult(changed,unchanged,conflicts,items)
    }
    private data class Deletion(val preview: ArchiveDeletePreview, val blobs: List<ArchiveBlob>)
    private fun deletion(selected: List<Long>): Deletion {
        val ids = ids(selected); val records = ids.associateWith { repository.record(it) }
        val hashes = records.values.filterNotNull().flatMap { repository.attempts(it.id) }.filter { it.status == ArchiveAttemptStatus.SAVED }.mapNotNull { it.blobSha256 }.distinct().sorted()
        val shared = mutableListOf<String>(); val busy = mutableListOf<String>(); val deletable = mutableListOf<ArchiveBlob>()
        val canonical = mapper.createObjectNode(); val recordSnapshot = mapper.createArrayNode(); val blobSnapshot = mapper.createArrayNode()
        ids.forEach { id -> recordSnapshot.add(mapper.valueToTree<JsonNode>(mapOf("id" to id,"revision" to records[id]?.revision,"protected" to records[id]?.protected,"attempts" to repository.attempts(id)))) }
        hashes.forEach { hash ->
            val blob = repository.blob(hash); val refs = repository.references(hash)
            val references = refs.map { a -> listOf(a.id,a.recordId,repository.record(a.recordId)?.revision,repository.record(a.recordId)?.protected) }
            blobSnapshot.add(mapper.valueToTree<JsonNode>(listOf(hash,blob?.state?.name,references)))
            when {
                blob == null || blob.state == ArchiveBlobState.MISSING -> {}
                blob.state == ArchiveBlobState.DELETING -> busy += hash
                refs.any { it.recordId !in ids || repository.record(it.recordId)?.protected == true } -> shared += hash
                validBlob(blob) -> deletable += blob
            }
        }
        canonical.set<JsonNode>("records",recordSnapshot); canonical.set<JsonNode>("blobs",blobSnapshot)
        val notStored = ids.filter { id -> repository.attempts(id).none { it.status == ArchiveAttemptStatus.SAVED && it.blobSha256?.let { h -> repository.blob(h)?.let { validBlob(it) } } == true } }
        val protected = ids.filter { records[it]?.protected == true }
        val items = ids.map { id ->
            val a = repository.attempts(id); val reasons = mutableListOf<String>()
            if (records[id] == null) reasons += "NOT_FOUND"
            if (id in protected) reasons += "PROTECTED"
            if (id in notStored) reasons += "NOT_STORED"
            if (a.any { it.blobSha256 in shared }) reasons += "SHARED_FILE"
            if (a.any { it.blobSha256 in busy }) reasons += "DELETE_IN_PROGRESS"
            ArchiveDeleteItem(id,reasons)
        }
        return Deletion(ArchiveDeletePreview(ids,sha(mapper.writeValueAsBytes(canonical)),deletable.size,deletable.sumOf { it.sizeBytes },protected,shared,busy,notStored,items),deletable)
    }
    fun deletePreview(ids: List<Long>): ArchiveDeletePreview = mutation { deletion(ids).preview }
    fun delete(request: ArchiveDeleteRequest): ArchiveDeleteResult = mutation {
        val d = deletion(request.ids)
        if (request.snapshotHash != d.preview.snapshotHash) throw ArchiveException("DELETE_SNAPSHOT_CHANGED",409,preview=d.preview)
        var files = 0; var bytes = 0L; val failed = mutableListOf<ArchiveConflict>()
        d.blobs.forEach { blob ->
            val refs = repository.references(blob.sha256)
            repository.transaction { repository.blobState(blob.sha256,ArchiveBlobState.DELETING); true }
            failpoint("AFTER_DELETING")
            try {
                Files.deleteIfExists(blobPath(blob.sha256))
                failpoint("AFTER_UNLINK")
                repository.finishDelete(blob.sha256,Instant.now())
                files++; bytes += blob.sizeBytes
            } catch (_: Exception) { refs.map { it.recordId }.distinct().forEach { failed += ArchiveConflict(it,"DELETE_IO_ERROR") } }
        }
        ArchiveDeleteResult(files,bytes,d.preview.items.flatMap { item -> item.reasonCodes.map { ArchiveConflict(item.id,it) } },failed,sizes().third)
    }
    @EventListener(ApplicationReadyEvent::class)
    fun startupRecovery() {
        try { recover() } catch (e: Exception) {
            LOG.warn("event=DISCOVERY_PAPER_RECOVERY_UNAVAILABLE reasonCode={}", (e as? ArchiveException)?.code ?: "RECOVERY_FAILED")
        }
    }
    @Scheduled(fixedDelay = 300000)
    fun scheduledRecovery() {
        try { recover() } catch (e: Exception) {
            LOG.warn("event=DISCOVERY_PAPER_RECOVERY_UNAVAILABLE reasonCode={}", (e as? ArchiveException)?.code ?: "RECOVERY_FAILED")
        }
    }
    fun recover() = mutation {
        val base = root()
        if (!Files.exists(base,LinkOption.NOFOLLOW_LINKS)) return@mutation
        val now = Instant.now()
        repository.blobs().forEach { blob ->
            when {
                blob.state == ArchiveBlobState.DELETING -> {
                    // Once DELETING commits, new references/protection are forbidden until this finishes.
                    Files.deleteIfExists(blobPath(blob.sha256)); repository.finishDelete(blob.sha256,now)
                }
                repository.references(blob.sha256).isEmpty() -> {
                    Files.deleteIfExists(blobPath(blob.sha256)); repository.deleteUnreferencedBlob(blob.sha256)
                }
                blob.state == ArchiveBlobState.AVAILABLE -> validBlob(blob,true)
            }
        }
        val blobs = base.resolve("blobs")
        if (Files.exists(blobs)) Files.walk(blobs).use { paths -> paths.forEach { path ->
            checkPath(path)
            if (Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {
                val hash = path.fileName.toString().removeSuffix(".bin")
                if (repository.blob(hash) == null) Files.delete(path)
            }
        } }
        val temp = base.resolve(".tmp")
        if (Files.exists(temp)) Files.list(temp).use { paths -> paths.forEach { path ->
            checkPath(path)
            if (path !in activeWriters && Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(now.minusSeconds(3600))) Files.delete(path)
        } }
        repository.records().forEach { r -> repository.attempts(r.id).filter { it.status == ArchiveAttemptStatus.STARTED && it.updatedAt.isBefore(now.minusSeconds(3600)) }.forEach {
            repository.finishAttempt(it.id,ArchiveAttemptStatus.SAVE_ERROR,"INTERRUPTED_WRITE",null,0,now)
        } }
    }
    companion object {
        private val LOG = LoggerFactory.getLogger(DiscoveryPaperArchiveService::class.java)
        private val SENSITIVE_FIELDS = setOf("password", "credentials", "credential", "apikey", "token",
            "accesstoken", "refreshtoken", "authorization", "localpath", "filepath", "absolutepath", "directory")
        fun sha(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
