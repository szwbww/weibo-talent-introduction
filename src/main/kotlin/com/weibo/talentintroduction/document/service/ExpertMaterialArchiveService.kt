package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Read-only export: prepare the complete archive before committing any HTTP response. */
@Service
class ExpertMaterialArchiveService(
    private val materialService: ExpertMaterialService,
    private val attachmentRepository: MailAttachmentRepository
) {
    fun withArchive(contactId: Long, attachmentIds: List<Long>, consume: (MaterialArchive) -> Unit) {
        require(contactId > 0 && attachmentIds.all { it > 0 }) { "专家及附件 ID 必须为正整数" }
        val ids = attachmentIds.distinct()
        require(ids.size in 1..MAX_FILES) { "一次请选择 1–500 份资料下载" }
        var archivePath: Path? = null
        try {
            val archive = try {
                val files = ids.map { id ->
                    try {
                        materialService.resolveReadyFile(contactId, id)
                    } catch (ex: MaterialNotReadyException) {
                        val name = attachmentRepository.findById(id).orElse(null)?.fileName?.take(256)
                            ?: "附件 $id"
                        throw MaterialNotReadyException(id, ex.state, "资料尚未就绪，请先获取到服务器：$name")
                    }
                }
                var total = 0L
                val sizes = files.map { file ->
                    Files.size(file.path).also { size ->
                        if (size > MAX_BYTES - total) throw MaterialArchiveException.tooLarge()
                        total += size
                    }
                }
                val target = createArchiveFile()
                archivePath = target
                val names = mutableSetOf<String>()
                var copiedTotal = 0L
                ZipOutputStream(Files.newOutputStream(target).buffered(), UTF_8).use { zip ->
                    val buffer = ByteArray(64 * 1024)
                    files.forEachIndexed { index, file ->
                        zip.putNextEntry(ZipEntry(uniqueName(file.attachment.fileName, ids[index], names)))
                        var copiedFile = 0L
                        Files.newInputStream(file.path).use { input ->
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count.toLong() > MAX_BYTES - copiedTotal) throw MaterialArchiveException.tooLarge()
                                zip.write(buffer, 0, count)
                                copiedTotal += count
                                copiedFile += count
                            }
                        }
                        // A changing source must not be delivered as a complete snapshot.
                        if (copiedFile != sizes[index]) throw MaterialArchiveException.failed()
                        zip.closeEntry()
                    }
                }
                MaterialArchive(target, "materials-$contactId.zip", Files.size(target))
            } catch (ex: IOException) {
                throw MaterialArchiveException.failed(ex)
            }
            // Consumer/network failures propagate separately; the controller must not append JSON to a ZIP.
            consume(archive)
        } finally {
            archivePath?.let { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (ex: IOException) {
                    logger.warn("Unable to remove temporary material archive", ex)
                }
            }
        }
    }

    internal fun createArchiveFile(): Path = Files.createTempFile("expert-materials-", ".zip")

    private fun uniqueName(original: String, id: Long, used: MutableSet<String>): String {
        var name = original.replace('\\', '/').substringAfterLast('/')
            .replace(Regex("[\\p{Cc}<>:\"|?*]"), "_").trim().trimEnd('.', ' ')
        if (name.isBlank()) name = "attachment-$id"
        if (Regex("(?i)^(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\\.|$)").containsMatchIn(name)) {
            name = "_$name"
        }
        val dot = name.lastIndexOf('.')
        val extension = if (dot > 0 && name.substring(dot).toByteArray(UTF_8).size <= 32) name.substring(dot) else ""
        val stem = if (extension.isEmpty()) name else name.substring(0, dot)
        var ordinal = 1
        while (true) {
            val suffix = if (ordinal == 1) "" else " ($ordinal)"
            val budget = MAX_NAME_BYTES - extension.toByteArray(UTF_8).size - suffix.toByteArray(UTF_8).size
            val candidate = truncateUtf8(stem, budget).trimEnd('.', ' ') + suffix + extension
            if (used.add(candidate.lowercase(Locale.ROOT))) return candidate
            ordinal++
        }
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        var end = 0
        var bytes = 0
        while (end < value.length) {
            val count = Character.charCount(value.codePointAt(end))
            val nextBytes = value.substring(end, end + count).toByteArray(UTF_8).size
            if (bytes + nextBytes > maxBytes) break
            bytes += nextBytes
            end += count
        }
        return value.substring(0, end)
    }

    companion object {
        const val MAX_FILES = 500
        const val MAX_BYTES = 500L * 1024 * 1024
        private const val MAX_NAME_BYTES = 240
        private val logger = LoggerFactory.getLogger(ExpertMaterialArchiveService::class.java)
    }
}

data class MaterialArchive(val path: Path, val fileName: String, val fileSize: Long)

class MaterialArchiveException(val status: HttpStatus, val code: String, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    companion object {
        fun tooLarge() = MaterialArchiveException(HttpStatus.PAYLOAD_TOO_LARGE, "MATERIAL_ARCHIVE_TOO_LARGE", "所选资料超过 500 MiB，请分批下载")
        fun failed(cause: Throwable? = null) = MaterialArchiveException(HttpStatus.INTERNAL_SERVER_ERROR, "MATERIAL_ARCHIVE_FAILED", "压缩包生成失败，请重试", cause)
    }
}
