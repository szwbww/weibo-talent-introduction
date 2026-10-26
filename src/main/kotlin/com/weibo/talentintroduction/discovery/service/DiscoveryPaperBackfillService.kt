package com.weibo.talentintroduction.discovery.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.weibo.talentintroduction.discovery.archive.*
import com.weibo.talentintroduction.discovery.domain.*
import com.weibo.talentintroduction.expert.domain.DiscoveryIdentity
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

/** A non-serializable parser permit, not a second identity or admission policy. */
class ParsedArchivedPaper private constructor(private val paper: DiscoveryHistoricalReplayTool.Paper,
    emails: List<AuthorEmail>, val analysis: PaperBodyComputation) {
    val emails: List<AuthorEmail> = java.util.Collections.unmodifiableList(emails.toList())
    fun requireMatches(metadata: PaperMetadata, authors: List<AuthorEmail>, source: PaperSourceRef) {
        require(metadata == paper.metadata && authors.distinct() == authors && authors.all { it in emails } &&
            source == paper.source) { "ARCHIVED_PARSER_INPUT_CHANGED" }
        DiscoveryHistoricalReplayTool.readBody(paper) // Recheck retained bytes immediately before the existing write seam.
        require(emails.all { !it.givenNames.isNullOrBlank() && !it.familyNames.isNullOrBlank() && DiscoveryIdentity.validEvidence(it.identityEvidence) })
    }
    companion object {
        fun parse(paper: DiscoveryHistoricalReplayTool.Paper, analyzer: PaperBodyAnalyzer): ParsedArchivedPaper {
            val parsed = analyzer.parse(PaperBodyInput(paper.source.bodySha256, paper.kind,
                DiscoveryHistoricalReplayTool.readBody(paper)), DiscoveryHistoricalReplayTool.mapper.valueToTree(paper.metadata))
            require(parsed.reasons.isEmpty() && parsed.qualification.coverage.status !in setOf(PaperEvidenceStatus.ERROR, PaperEvidenceStatus.PARTIAL)) { "SOURCE_PARSE_PARTIAL" }
            val bound = parsed.emails.filter { !it.givenNames.isNullOrBlank() && !it.familyNames.isNullOrBlank() && DiscoveryIdentity.validEvidence(it.identityEvidence) }
            val conflicts = bound.groupBy { DiscoveryIdentity.normalizedEmail(it.email) }.filterValues { group ->
                group.map { it.givenNames?.lowercase() to it.familyNames?.lowercase() }.distinct().size != 1 }.keys
            return ParsedArchivedPaper(paper, bound.filterNot { DiscoveryIdentity.normalizedEmail(it.email) in conflicts }
                .distinctBy { DiscoveryIdentity.normalizedEmail(it.email) }, parsed)
        }
    }
}
data class ArchivedPaperExecution(val source: PaperSourceRef, val evaluationAt: Instant, val parsed: ParsedArchivedPaper)

/** The adapter reads current identity/revision and creates NEW tasks/runs; no historical item reset API. */
interface HistoricalBindingsTarget {
    fun validate(frozen: DiscoveryHistoricalReplayTool.Frozen)
    fun begin(manifestHash: String): DiscoveryVerificationExecution
    fun resume(executionId: Long): DiscoveryVerificationExecution
    fun validatePaper(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>)
    fun recheckExisting(frozen: DiscoveryHistoricalReplayTool.Frozen, paper: DiscoveryHistoricalReplayTool.Paper, emails: List<AuthorEmail>): List<Long>
    fun finish(context: DiscoveryVerificationExecution, cancelled: Boolean, failed: Boolean)
}

/** Fixed manifest, cross-process paper lease and immutable attempt receipts. No source discovery on retry. */
class DiscoveryPaperBackfillService(private val discovery: ExpertDiscoveryService, private val analyzer: PaperBodyAnalyzer,
    private val target: HistoricalBindingsTarget) {
    data class Receipt(val source: PaperSourceRef, val executionId: Long, val status: String, val retryable: Boolean,
        val result: ConsumeOutcomeResult? = null, val recheckRunIds: List<Long> = emptyList(), val reason: String? = null)
    data class Result(val completed: Int, val skippedCompleted: Int, val failed: Int, val newRaw: Int,
        val duplicates: Int, val cancelled: Boolean, val receipts: List<Receipt>)

    fun apply(frozen: DiscoveryHistoricalReplayTool.Frozen, expectedManifestHash: String,
        cancelled: () -> Boolean = { Files.exists(frozen.directory.resolve("cancel"), LinkOption.NOFOLLOW_LINKS) }): Result {
        require(expectedManifestHash == frozen.hash)
        // Reject changed manifests/implementations even when a caller retains an old in-memory object.
        val loaded = DiscoveryHistoricalReplayTool.load(frozen.directory.resolve("manifest.json"), expectedManifestHash)
        require(loaded.manifest == frozen.manifest)
        val directory = frozen.directory.resolve("bindings")
        DiscoveryHistoricalReplayTool.secureDirectory(directory)
        val lock = directory.resolve(".owner.lock")
        return locked(lock) {
            target.validate(frozen)
            val executionPath = directory.resolve("execution.json")
            val context = if (Files.exists(executionPath)) {
                DiscoveryHistoricalReplayTool.privateFile(executionPath)
                target.resume(DiscoveryHistoricalReplayTool.mapper.readTree(Files.readAllBytes(executionPath)).path("executionId").longValue())
            } else {
                target.begin(frozen.hash).also { c ->
                    require(c.executionId != null && c.run != null && frozen.manifest.scope.papers.none { it.source.sourceExecutionId == c.executionId })
                    DiscoveryVerificationReplayTool.atomicWrite(executionPath, DiscoveryHistoricalReplayTool.mapper.writeValueAsBytes(mapOf("executionId" to c.executionId)))
                }
            }
            val receipts = mutableListOf<Receipt>(); var skipped = 0; var stopped = false
            for (paper in frozen.manifest.scope.papers) {
                if (cancelled()) { stopped = true; break }
                val key = "${paper.source.archiveRecordId}-${paper.source.attemptId}"
                val paperDir = directory.resolve(key); DiscoveryHistoricalReplayTool.secureDirectory(paperDir)
                locked(paperDir.resolve(".lease")) {
                    val previous = Files.list(paperDir).use { paths -> paths.filter { it.fileName.toString().endsWith(".json") }.sorted().toArray().map { it as Path } }
                    val done = previous.any { path -> DiscoveryHistoricalReplayTool.privateFile(path)
                        val r: Receipt = DiscoveryHistoricalReplayTool.mapper.readValue(Files.readAllBytes(path))
                        require(r.source == paper.source && r.executionId == context.executionId)
                        !r.retryable }
                    if (done) { skipped++; return@locked }
                    val receipt = try {
                        val parsed = ParsedArchivedPaper.parse(paper, analyzer)
                        target.validatePaper(frozen, paper, parsed.emails)
                        if (cancelled()) { stopped = true; return@locked }
                        val runs = target.recheckExisting(frozen, paper, parsed.emails)
                        val existingEmails = frozen.manifest.scope.experts.mapNotNull {
                            it.profile.email?.let(DiscoveryIdentity::normalizedEmail) }.toSet()
                        val newEmails = parsed.emails.filterNot { DiscoveryIdentity.normalizedEmail(it.email) in existingEmails }
                        val outcome = discovery.consumeArchivedPaper(paper.metadata, newEmails, paper.source.sourceName,
                            paper.source.sourceItemKey, context.copy(archivedPaper = ArchivedPaperExecution(paper.source, frozen.manifest.scope.evaluationAt, parsed)))
                        val retry = outcome.rawWriteFailed || outcome.enqueueFailed || outcome.dedupFailed || outcome.identityRepairFailed || outcome.registrationFailed
                        Receipt(paper.source, context.executionId!!, if (retry) "RETRYABLE" else "COMPLETED", retry, outcome, runs,
                            if (retry) "CONSUMER_WRITE_INCOMPLETE" else null)
                    } catch (e: java.util.concurrent.CancellationException) { stopped = true; return@locked }
                    catch (e: InterruptedException) { Thread.currentThread().interrupt(); stopped = true; return@locked }
                    catch (e: Exception) {
                        val reason = when {
                            !Files.isRegularFile(Paths.get(paper.path), LinkOption.NOFOLLOW_LINKS) -> "SOURCE_MISSING"
                            e is IllegalArgumentException && e.message in setOf("SOURCE_HASH_CHANGED", "CURRENT_IDENTITY_OR_REVISION_CHANGED", "SOURCE_PARSE_PARTIAL", "CROSS_PAPER_IDENTITY_CONFLICT") -> e.message!!
                            else -> "BACKFILL_INPUT_OR_WRITE_FAILED"
                        }
                        Receipt(paper.source, context.executionId!!, "RETRYABLE", true, reason = reason)
                    }
                    DiscoveryVerificationReplayTool.atomicWrite(paperDir.resolve("%08d.json".format(previous.size + 1)),
                        DiscoveryHistoricalReplayTool.mapper.writeValueAsBytes(receipt))
                    receipts += receipt
                }
                if (stopped) break
            }
            target.finish(context, stopped, receipts.any { it.retryable })
            Result(receipts.count { !it.retryable }, skipped, receipts.count { it.retryable }, receipts.sumOf { it.result?.indexedExperts ?: 0 },
                receipts.sumOf { it.result?.duplicateExperts ?: 0 }, stopped, receipts)
        }
    }
    private fun <T> locked(path: Path, action: () -> T): T {
        DiscoveryHistoricalReplayTool.noLinks(path)
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            val lock = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
            requireNotNull(lock) { "BACKFILL_BUSY" }
            lock.use { return action() }
        }
    }
}
