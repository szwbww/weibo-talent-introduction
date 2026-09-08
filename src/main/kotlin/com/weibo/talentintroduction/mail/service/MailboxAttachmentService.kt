package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.document.service.ExpertMaterialService
import com.weibo.talentintroduction.mail.domain.MailAttachment
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

data class AttachmentMetaResponse(
    val id: Long,
    val fileName: String,
    val contentType: String?,
    val fileSize: Long?
)

data class MailboxAttachmentDownload(
    val fileName: String,
    val contentType: String,
    val path: Path,
    val fileSize: Long
)

/**
 * 收发件箱附件列表/下载（旧 mailbox 入口）。下载就绪与 realpath/基目录校验
 * 统一委托 [ExpertMaterialService.resolveReadyFileUnscoped]（I-3）：未就绪抛
 * [com.weibo.talentintroduction.document.service.MaterialNotReadyException]
 * （HTTP 409），不再自行维护路径旁路。消息级归属仍由调用链
 * （MailboxService.resolveAttachments → 07 委托 resolveMessageAttachments）保证。
 */
@Service
class MailboxAttachmentService(
    private val mailboxService: MailboxService,
    private val materialService: ExpertMaterialService
) {
    fun listAttachments(source: String, id: Long): List<AttachmentMetaResponse> =
        mailboxService.resolveAttachments(source, id).map { it.toMetaResponse() }

    fun download(attachmentId: Long): MailboxAttachmentDownload {
        val ready = materialService.resolveReadyFileUnscoped(attachmentId)
        val path = ready.path
        return MailboxAttachmentDownload(
            fileName = ready.attachment.fileName,
            contentType = resolveContentType(ready.attachment),
            path = path,
            fileSize = Files.size(path)
        )
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
