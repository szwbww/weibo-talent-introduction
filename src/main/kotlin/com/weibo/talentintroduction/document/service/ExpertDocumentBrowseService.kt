package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

data class ExpertDocumentFile(
    val documentId: Long,
    val attachmentId: Long,
    val mailRecordId: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long,
    val documentType: String,
    val documentStatus: String,
    val createdAt: LocalDateTime?,
    val previewable: Boolean,
    val downloadUrl: String,
    val previewUrl: String
)

data class DocumentFileResource(
    val fileName: String,
    val contentType: String,
    val path: Path,
    val fileSize: Long
)

@Service
class ExpertDocumentBrowseService(
    private val properties: MailAttachmentStorageProperties,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    fun listDocuments(contactId: Long): List<ExpertDocumentFile> {
        val documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)

        return documents.map { doc ->
            val attachment = mailAttachmentRepository.findById(doc.mailAttachmentId)
                .orElseThrow { error("Attachment not found: ${doc.mailAttachmentId}") }

            val mailRecordId = requireNotNull(attachment.mailRecordId) {
                "Expert document attachment must have mail_record_id"
            }
            val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
                ?: error("Mail record not found: $mailRecordId")
            require(mailRecord.expertContactId == contactId) {
                "Mail record $mailRecordId does not belong to expert contact $contactId"
            }

            val contentType = resolveContentType(attachment)
            val previewable = isPreviewable(contentType)

            ExpertDocumentFile(
                documentId = doc.id ?: error("Document id is required"),
                attachmentId = attachment.id ?: error("Attachment id is required"),
                mailRecordId = mailRecordId,
                fileName = attachment.fileName,
                contentType = contentType,
                fileSize = attachment.fileSize,
                documentType = doc.documentType,
                documentStatus = doc.documentStatus,
                createdAt = doc.createdAt,
                previewable = previewable,
                downloadUrl = "/api/expert-contacts/$contactId/attachments/${attachment.id}/download",
                previewUrl = "/api/expert-contacts/$contactId/attachments/${attachment.id}/preview"
            )
        }
    }

    fun resolveForDownload(contactId: Long, attachmentId: Long): DocumentFileResource {
        val validation = validateAndResolve(contactId, attachmentId)
        return DocumentFileResource(
            fileName = validation.attachment.fileName,
            contentType = resolveContentType(validation.attachment),
            path = validation.resolvedPath,
            fileSize = validation.attachment.fileSize
        )
    }

    fun resolveForPreview(contactId: Long, attachmentId: Long): DocumentFileResource {
        val resource = resolveForDownload(contactId, attachmentId)
        val previewable = isPreviewable(resource.contentType)
        require(previewable) { "File type '${resource.contentType}' is not previewable" }
        return resource
    }

    private data class ValidationResult(
        val attachment: MailAttachment,
        val resolvedPath: Path
    )

    private fun validateAndResolve(contactId: Long, attachmentId: Long): ValidationResult {
        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
            ?: error("Document not found for attachment $attachmentId")
        require(document.expertContactId == contactId) {
            "Document $attachmentId does not belong to expert contact $contactId"
        }

        val attachment = mailAttachmentRepository.findById(attachmentId)
            .orElseThrow { error("Attachment not found: $attachmentId") }

        val mailRecordId = requireNotNull(attachment.mailRecordId) {
            "Expert document attachment must have mail_record_id"
        }
        val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
            ?: error("Mail record not found: $mailRecordId")
        require(mailRecord.expertContactId == contactId) {
            "Mail record $mailRecordId does not belong to expert contact $contactId"
        }

        val storagePath = Path.of(attachment.storagePath).toAbsolutePath().normalize()
        require(Files.exists(storagePath)) { "File not found: ${attachment.fileName}" }
        require(Files.isRegularFile(storagePath)) { "Not a regular file: ${attachment.fileName}" }

        val realBasePath = Path.of(properties.basePath).toRealPath()
        val realStoragePath = storagePath.toRealPath()
        require(realStoragePath.startsWith(realBasePath)) {
            "Attachment path is outside configured base path"
        }

        return ValidationResult(attachment, realStoragePath)
    }

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

    private fun isPreviewable(contentType: String): Boolean =
        contentType == "application/pdf" ||
        contentType.startsWith("image/") ||
        contentType.startsWith("text/")
}
