package com.weibo.talentintroduction.document.service

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
    val mailRecordId: Long?,
    val fileName: String,
    val contentType: String,
    val fileSize: Long?,
    val documentType: String,
    val documentStatus: String,
    val createdAt: LocalDateTime?,
    val previewable: Boolean,
    val downloadUrl: String?,
    val previewUrl: String?
)

data class DocumentFileResource(
    val fileName: String,
    val contentType: String,
    val path: Path,
    val fileSize: Long
)

/**
 * 旧专家资料浏览接口（URL/契约不变）。下载/预览的就绪与归属判定统一委托
 * [ExpertMaterialService.resolveReadyFile]（I-1：双 owner、realpath、409
 * MATERIAL_NOT_READY），本服务不再各自维护路径校验，杜绝旁路。
 */
@Service
class ExpertDocumentBrowseService(
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val mailRecordRepository: MailRecordRepository,
    private val materialService: ExpertMaterialService
) {
    fun listDocuments(contactId: Long): List<ExpertDocumentFile> {
        val documents = expertDocumentRepository.findAllByExpertContactIdOrderByCreatedAtAsc(contactId)

        return documents.map { doc ->
            val attachment = mailAttachmentRepository.findById(doc.mailAttachmentId)
                .orElseThrow { error("Attachment not found: ${doc.mailAttachmentId}") }

            // 双 owner 兼容：mail_record owner（历史）沿用原有归属校验；
            // processing owner（04 已绑定专家附件）以 expert_document 为准。
            val mailRecordId = attachment.mailRecordId
            if (mailRecordId != null) {
                val mailRecord = mailRecordRepository.findByIdOrNull(mailRecordId)
                    ?: error("Mail record not found: $mailRecordId")
                require(mailRecord.expertContactId == contactId) {
                    "Mail record $mailRecordId does not belong to expert contact $contactId"
                }
            }

            val contentType = resolveContentType(attachment)
            val hasLocalFile = attachment.storagePath != null

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
                previewable = hasLocalFile && isPreviewable(contentType),
                downloadUrl = if (hasLocalFile) {
                    "/api/expert-contacts/$contactId/attachments/${attachment.id}/download"
                } else {
                    null
                },
                previewUrl = if (hasLocalFile) {
                    "/api/expert-contacts/$contactId/attachments/${attachment.id}/preview"
                } else {
                    null
                }
            )
        }
    }

    /**
     * 未就绪抛 [MaterialNotReadyException]（HTTP 409，附件仍就绪前不创建任务）；
     * 越权/路径越界抛 IllegalArgumentException（HTTP 400）。
     */
    fun resolveForDownload(contactId: Long, attachmentId: Long): DocumentFileResource {
        val ready = materialService.resolveReadyFile(contactId, attachmentId)
        return DocumentFileResource(
            fileName = ready.attachment.fileName,
            contentType = resolveContentType(ready.attachment),
            path = ready.path,
            fileSize = Files.size(ready.path)
        )
    }

    fun resolveForPreview(contactId: Long, attachmentId: Long): DocumentFileResource {
        val resource = resolveForDownload(contactId, attachmentId)
        val previewable = isPreviewable(resource.contentType)
        require(previewable) { "File type '${resource.contentType}' is not previewable" }
        return resource
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
