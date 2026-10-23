package com.weibo.talentintroduction.discovery.archive

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

const val ARCHIVE_GIB: Long = 1073741824

enum class ArchiveBlobState { AVAILABLE, DELETING, MISSING }
enum class ArchiveAttemptStatus { STARTED, SAVED, DOWNLOAD_FAILED, TOO_LARGE, SAVE_ERROR, DELETED }
enum class ArchiveAttemptKind { PDF_URL, PMC_XML, PMC_OA_XML, EMBEDDED_TEXT }
enum class ArchiveRecordState { SAVE_ERROR, STORED, PROCESSING, DELETED, NOT_STORED }
enum class ArchiveGate { READY, BLOCKED, DISABLED, UNAVAILABLE }

class ArchiveException(val code: String, val httpStatus: Int, override val message: String = code,
                       val preview: ArchiveDeletePreview? = null) : IllegalStateException(message)

data class ArchiveSetting(val revision: Long = 0, val enabled: Boolean = false,
    val maxBytes: Long = 20 * ARCHIVE_GIB, val minFreeBytes: Long = 15 * ARCHIVE_GIB, val updatedAt: Instant? = null) {
    fun validate() {
        if (revision < 0 || listOf(maxBytes, minFreeBytes).any { it !in ARCHIVE_GIB..1024 * ARCHIVE_GIB || it % ARCHIVE_GIB != 0L })
            throw ArchiveException("INVALID_SETTINGS", 400)
    }
}
data class ArchiveSettingsRequest(val revision: Long, val enabled: Boolean, val maxBytes: Long, val minFreeBytes: Long)
data class ArchiveSettingsResponse(val revision: Long, val enabled: Boolean, val maxBytes: Long, val minFreeBytes: Long,
    val effectiveEnabled: Boolean, val gate: ArchiveGate, val reasonCode: String?, val updatedAt: Instant?)
data class ArchiveRecord(val id: Long, val executionId: Long, val sourceName: String, val paperKey: String,
    val metadataJson: String, val outcomeJson: String?, val analysisJson: String?, val protected: Boolean,
    val revision: Long, val createdAt: Instant, val updatedAt: Instant)
data class ArchiveBlob(val sha256: String, val sizeBytes: Long, val mediaType: String, val state: ArchiveBlobState, val createdAt: Instant)
data class ArchiveAttempt(val id: Long, val recordId: Long, val attemptKey: String, val kind: ArchiveAttemptKind,
    val sourceUrl: String?, val status: ArchiveAttemptStatus, val blobSha256: String?, val reused: Boolean,
    val httpStatus: Int?, val reasonCode: String?, val completedBytes: Long, val createdAt: Instant, val updatedAt: Instant)
data class ArchiveExtractionSummary(val emails: Int, val resolved: Int, val unresolved: Int)
data class ArchiveRow(val id: Long, val revision: Long, val executionId: Long, val sourceName: String, val paperKey: String,
    val title: String?, val doi: String?, val pubYear: Int?, val state: ArchiveRecordState, val protected: Boolean,
    val storedBytes: Long, val attemptCount: Int, val readableAttemptIds: List<Long>, val firstCapturedAt: Instant,
    val updatedAt: Instant, val extractionSummary: ArchiveExtractionSummary?, val analysisSummary: JsonNode?)
data class ArchiveAttemptDetail(val id: Long, val kind: ArchiveAttemptKind, val status: ArchiveAttemptStatus,
    val sourceUrl: String?, val bodySha256: String?, val mediaType: String?, val bytes: Long, val reused: Boolean,
    val httpStatus: Int?, val reasonCode: String?, val readable: Boolean)
data class ArchiveDetail(val record: ArchiveRow, val paper: JsonNode, val attempts: List<ArchiveAttemptDetail>,
    val outcome: JsonNode?, val analysis: JsonNode?)
data class ArchivePage(val items: List<ArchiveRow>, val total: Long, val page: Int, val size: Int)
data class ArchiveQuery(val executionId: Long? = null, val q: String? = null, val status: ArchiveRecordState? = null,
    val protected: Boolean? = null, val hasGain: Boolean? = null, val page: Int = 0, val size: Int = 20)
data class ArchiveCounts(val papers: Long, val attempts: Long, val savedAttempts: Long, val newFiles: Long,
    val reusedAttempts: Long, val failedAttempts: Long, val notStoredPapers: Long)
data class ArchiveStorage(val directoryAvailable: Boolean, val archiveUsedBytes: Long?, val blobBytes: Long?, val tempBytes: Long?,
    val fileCount: Long?, val protectedPaperCount: Long, val diskTotalBytes: Long?, val diskUsedBytes: Long?,
    val diskAvailableBytes: Long?, val diskReservedBytes: Long?, val maxBytes: Long, val minFreeBytes: Long,
    val enabled: Boolean, val gate: ArchiveGate, val reasonCode: String?, val measuredAt: Instant)
data class ArchiveStats(val executionId: Long?, val storage: ArchiveStorage, val counts: ArchiveCounts)
data class ArchiveRevisionItem(val id: Long, val revision: Long)
data class ArchiveProtectionRequest(val items: List<ArchiveRevisionItem>, val protected: Boolean)
data class ArchiveConflict(val id: Long, val reasonCode: String)
data class ArchiveProtectedItem(val id: Long, val protected: Boolean, val revision: Long)
data class ArchiveProtectionResult(val changed: Int, val unchanged: Int, val conflicts: List<ArchiveConflict>, val items: List<ArchiveProtectedItem>)
data class ArchiveIdsRequest(val ids: List<Long>)
data class ArchiveDeleteRequest(val ids: List<Long>, val snapshotHash: String)
data class ArchiveDeleteItem(val id: Long, val reasonCodes: List<String>)
data class ArchiveDeletePreview(val ids: List<Long>, val snapshotHash: String, val deletableFiles: Int, val deletableBytes: Long,
    val protectedRecords: List<Long>, val sharedFiles: List<String>, val busyFiles: List<String>, val notStored: List<Long>,
    val items: List<ArchiveDeleteItem>)
data class ArchiveDeleteResult(val deletedFiles: Int, val deletedBytes: Long, val skipped: List<ArchiveConflict>,
    val failed: List<ArchiveConflict>, val remainingFiles: Long)

/** The descriptor is never serialized. The controller streams while the service holds the mutation/file lock. */
data class ArchiveContent(val mediaType: String, val length: Long, val filename: String)
