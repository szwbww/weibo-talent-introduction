package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

data class AttachmentMetaResponse(
    val id: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long
)

data class MailboxAttachmentDownload(
    val fileName: String,
    val contentType: String,
    val path: Path,
    val fileSize: Long
)

@Service
class MailboxAttachmentService(
    private val properties: MailAttachmentStorageProperties,
    private val mailboxService: MailboxService,
    private val mailAttachmentRepository: MailAttachmentRepository
) {
    fun listAttachments(source: String, id: Long): List<AttachmentMetaResponse> =
        mailboxService.resolveAttachments(source, id).map { it.toMetaResponse() }

    fun download(attachmentId: Long): MailboxAttachmentDownload {
        val attachment = mailAttachmentRepository.findById(attachmentId)
            .orElseThrow { NoSuchElementException("Attachment not found: $attachmentId") }
        val resolvedPath = validateStoragePath(attachment)
        return MailboxAttachmentDownload(
            fileName = attachment.fileName,
            contentType = resolveContentType(attachment),
            path = resolvedPath,
            fileSize = attachment.fileSize
        )
    }

    private fun validateStoragePath(attachment: MailAttachment): Path {
        val storagePath = Path.of(attachment.storagePath).toAbsolutePath().normalize()
        require(Files.exists(storagePath)) { "File not found: ${attachment.fileName}" }
        require(Files.isRegularFile(storagePath)) { "Not a regular file: ${attachment.fileName}" }

        val realBasePath = Path.of(properties.basePath).toRealPath()
        val realStoragePath = storagePath.toRealPath()
        require(realStoragePath.startsWith(realBasePath)) {
            "Attachment path is outside configured base path"
        }
        return realStoragePath
    }

    private fun MailAttachment.toMetaResponse(): AttachmentMetaResponse =
        AttachmentMetaResponse(
            id = id ?: error("Attachment id is required"),
            fileName = fileName,
            contentType = contentType,
            fileSize = fileSize
        )

    private fun resolveContentType(attachment: MailAttachment): String {
        if (!attachment.contentType.isNullOrBlank()) {
            return attachment.contentType
        }
        val name = attachment.fileName.lowercase()
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".log") || name.endsWith(".md") -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
