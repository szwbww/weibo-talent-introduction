package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.mail.domain.MailAttachment
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

/**
 * AI 文本提取器。归属 + 就绪 + realpath 统一委托
 * [ExpertMaterialService.resolveReadyFile]（I-3）：任何所选文件未就绪即抛
 * [MaterialNotReadyException]（HTTP 409），并且**先全部解析就绪、再读任何
 * 文件内容**——不会出现读到一半才发现缺件、也不会部分分析冒充完成。
 * 只有归属有效（expert_document → attachment → 唯一 owner → 该 contact）的
 * 文档可被 AI 读取；匿名附件不能借 attachmentId 越权。
 */
@Service
class DocumentTextExtractor(
    private val materialService: ExpertMaterialService
) {
    fun extract(contactId: Long, attachmentIds: List<Long>): Map<Long, ExtractedText> {
        require(attachmentIds.isNotEmpty()) { "attachmentIds must not be empty" }

        // 阶段 1：全部所选先解析并核验就绪（resolveReadyFile 内含归属/路径校验），
        // 任一失败即抛，未读任何文件字节。
        val readyFiles = attachmentIds.associateWith { attachmentId ->
            materialService.resolveReadyFile(contactId, attachmentId)
        }
        // 阶段 2：全部就绪后才逐件读取内容。
        return readyFiles.mapValues { (attachmentId, ready) ->
            val attachment = ready.attachment
            val contentType = resolveContentType(attachment)
            extractFromFile(attachmentId, attachment, ready.path, contentType)
        }
    }

    fun validateAttachmentBelongsToContact(contactId: Long, attachmentId: Long) {
        materialService.resolveReadyFile(contactId, attachmentId)
    }

    private fun extractFromFile(
        attachmentId: Long,
        attachment: MailAttachment,
        path: Path,
        contentType: String
    ): ExtractedText {
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
