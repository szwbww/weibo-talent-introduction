package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

data class ExtractedText(
    val attachmentId: Long,
    val fileName: String,
    val text: String,
    val supported: Boolean,
    val unsupportedReason: String? = null
)

@Service
class DocumentTextExtractor(
    private val properties: MailAttachmentStorageProperties,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val mailRecordRepository: MailRecordRepository
) {
    fun extract(contactId: Long, attachmentIds: List<Long>): Map<Long, ExtractedText> {
        require(attachmentIds.isNotEmpty()) { "attachmentIds must not be empty" }

        return attachmentIds.associateWith { attachmentId ->
            val attachment = resolveAttachment(contactId, attachmentId)
            val contentType = resolveContentType(attachment)
            extractFromFile(attachmentId, attachment, contentType)
        }
    }

    fun validateAttachmentBelongsToContact(contactId: Long, attachmentId: Long) {
        resolveAttachment(contactId, attachmentId)
    }

    private fun resolveAttachment(contactId: Long, attachmentId: Long): MailAttachment {
        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
            ?: throw IllegalArgumentException("Document not found for attachment $attachmentId")
        require(document.expertContactId == contactId) {
            "Document $attachmentId does not belong to expert contact $contactId"
        }

        val attachment = mailAttachmentRepository.findById(attachmentId)
            .orElseThrow { IllegalArgumentException("Attachment not found: $attachmentId") }

        val mailRecordId = requireNotNull(attachment.mailRecordId) {
            "Expert document attachment must have mail_record_id"
        }
        val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
            ?: throw IllegalArgumentException("Mail record not found: $mailRecordId")
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

        return attachment
    }

    private fun extractFromFile(attachmentId: Long, attachment: MailAttachment, contentType: String): ExtractedText {
        val path = Path.of(attachment.storagePath).toAbsolutePath().normalize()
        return when {
            contentType == "application/pdf" -> {
                val text = PDDocument.load(path.toFile()).use { document ->
                    PDFTextStripper().getText(document)
                }
                ExtractedText(attachmentId, attachment.fileName, text, supported = true)
            }
            contentType.startsWith("text/") -> {
                val text = Files.readString(path, StandardCharsets.UTF_8)
                ExtractedText(attachmentId, attachment.fileName, text, supported = true)
            }
            else -> ExtractedText(
                attachmentId = attachmentId,
                fileName = attachment.fileName,
                text = "",
                supported = false,
                unsupportedReason = "不支持的文件类型: $contentType"
            )
        }
    }

    private fun resolveContentType(attachment: MailAttachment): String {
        if (!attachment.contentType.isNullOrBlank()) {
            return attachment.contentType
        }
        val name = attachment.fileName.lowercase()
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") || name.endsWith(".csv") || name.endsWith(".log") || name.endsWith(".md") -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
