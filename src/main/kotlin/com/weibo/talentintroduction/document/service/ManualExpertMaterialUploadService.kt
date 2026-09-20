package com.weibo.talentintroduction.document.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.DocumentStatus
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ManualExpertMaterialUpload
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.document.repository.ManualExpertMaterialUploadRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.service.MAX_CREATED_BY_CHARS
import com.weibo.talentintroduction.mail.service.MailAttachmentService
import com.weibo.talentintroduction.mail.service.OutboundAttachmentException
import com.weibo.talentintroduction.mail.service.normalizeOutboundContentType
import com.weibo.talentintroduction.mail.service.normalizeOutboundFileName
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.UUID

/**
 * 手动材料上传成功响应（201）。只含业务元数据：绝不含 storagePath、绝对根路径或
 * 临时文件名，文件名只回显清洗后的展示名。
 */
data class ManualExpertMaterialUploadResponse(
    val attachmentId: Long,
    val documentId: Long,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val documentType: String,
    val documentStatus: String,
    val storageState: String
)

/**
 * 专家材料手动上传（fast-p manual-expert-material-upload 阶段 2）：有界、流式、原子。
 *
 * 顺序固定（I-2）：
 * 1. 校验会话身份与专家存在 → 清洗文件名/MIME → 推断材料类型（复用 04/收信同一套规则）；
 * 2. 流式写 `${basePath}/manual/.tmp-<UUID>` 并按**实际读入字节**计数，首次超过
 *    [MAX_MANUAL_MATERIAL_BYTES] 立即 413（不信 `MultipartFile.size`）；
 * 3. 完整通过后同目录原子移动到 `${basePath}/manual/<UUID>`——原始文件名永不参与拼路径；
 * 4. 在**一个** [TransactionTemplate] 事务里依次写 `manual_expert_material_upload` →
 *    `mail_attachment`（三选一 owner 中的 manual、真实大小、final 路径）→
 *    `expert_document`（PENDING_REVIEW、沿用推断类型）。
 *
 * 失败语义：文件写入/移动失败零数据库写；事务体或提交失败删除本次 final 文件；
 * 临时文件在所有分支删除。任何分支都不留下「有文件无元数据」或「有元数据无文件」。
 *
 * 手动材料不是邮件事件（I-7）：`mail_record_id`/`inbound_processing_id` 恒为 null，
 * 不建 `mail_attachment_transfer` 行，不推进 `MATERIALS_RECEIVED`，不触网。
 */
@Service
class ManualExpertMaterialUploadService(
    private val properties: MailAttachmentStorageProperties,
    private val expertContactRepository: ExpertContactRepository,
    private val manualUploadRepository: ManualExpertMaterialUploadRepository,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailAttachmentService: MailAttachmentService,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * 上传一个本地文件（每次一个文件，multipart 单字段 `file`）。
     *
     * [authenticatedUsername] 只能是会话身份，客户端不可传；[stream] 由调用方负责关闭
     * （controller 用 use 包裹 multipart 输入流）。允许 0 字节（写 `file_size = 0`）。
     */
    fun upload(
        contactId: Long,
        authenticatedUsername: String,
        originalFilename: String?,
        declaredContentType: String?,
        stream: InputStream
    ): ManualExpertMaterialUploadResponse {
        val operator = normalizeOperator(authenticatedUsername)
        if (!expertContactRepository.existsById(contactId)) {
            throw OutboundAttachmentException.notFound("专家不存在")
        }
        val fileName = normalizeOutboundFileName(originalFilename)
        val contentType = normalizeOutboundContentType(declaredContentType)
        val documentType = mailAttachmentService.inferDocumentType(fileName).name

        val root = manualRoot()
        Files.createDirectories(root)
        val uuid = UUID.randomUUID().toString()
        val finalPath = root.resolve(uuid)
        val tempPath = root.resolve(TEMP_FILE_PREFIX + uuid)
        val fileSize = writeStreamedFile(stream, tempPath, finalPath)

        val now = LocalDateTime.now()
        val written = try {
            transactionTemplate.execute {
                val source = manualUploadRepository.save(
                    ManualExpertMaterialUpload(
                        expertContactId = contactId,
                        uploadedBy = operator,
                        createdAt = now
                    )
                )
                val uploadId = source.id
                    ?: error("manual expert material upload id is required")
                val attachment = mailAttachmentRepository.save(
                    MailAttachment(
                        mailRecordId = null,
                        inboundProcessingId = null,
                        fileName = fileName,
                        contentType = contentType,
                        fileSize = fileSize,
                        storagePath = finalPath.toString(),
                        createdAt = now,
                        manualUploadId = uploadId
                    )
                )
                val attachmentId = attachment.id
                    ?: error("mail attachment id is required")
                val document = expertDocumentRepository.save(
                    ExpertDocument(
                        expertContactId = contactId,
                        mailAttachmentId = attachmentId,
                        documentType = documentType,
                        documentStatus = DocumentStatus.PENDING_REVIEW.name,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                WrittenIds(
                    attachmentId = attachmentId,
                    documentId = document.id ?: error("expert document id is required")
                )
            }
        } catch (ex: Exception) {
            // 事务体/提交失败不得留下「有文件无元数据」：删掉本次刚落地的 final 再上抛。
            Files.deleteIfExists(finalPath)
            throw ex
        }
        if (written == null) {
            Files.deleteIfExists(finalPath)
            error("manual expert material upload transaction returned no result")
        }
        return ManualExpertMaterialUploadResponse(
            attachmentId = written.attachmentId,
            documentId = written.documentId,
            fileName = fileName,
            contentType = contentType,
            fileSize = fileSize,
            documentType = documentType,
            documentStatus = DocumentStatus.PENDING_REVIEW.name,
            storageState = MailAttachmentTransfer.STATE_STORED
        )
    }

    /**
     * 流式落盘 + 上限计数 + 同目录原子移动；临时文件在所有分支删除。
     * 超过上限立即抛 413（[OutboundAttachmentException.payloadTooLarge]），
     * 此时 final 尚不存在、临时文件在 finally 里被删掉，因此零数据库写。
     */
    private fun writeStreamedFile(stream: InputStream, tempPath: Path, finalPath: Path): Long {
        var fileSize = 0L
        try {
            Files.newOutputStream(
                tempPath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { output ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    fileSize += read
                    if (fileSize > MAX_MANUAL_MATERIAL_BYTES) {
                        throw OutboundAttachmentException.payloadTooLarge(
                            "单个材料不能超过 $MAX_MANUAL_MATERIAL_BYTES 字节"
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tempPath)
        }
        return fileSize
    }

    /**
     * 上传者身份：只取会话用户名，trim 后必须非空；超列宽报错，绝不截断成另一个用户。
     * 列宽与 04 的 `outbound_mail_attachment.created_by` 同为 100 字符，复用同一常量。
     */
    private fun normalizeOperator(authenticatedUsername: String?): String {
        val operator = authenticatedUsername?.trim().orEmpty()
        if (operator.isEmpty()) {
            throw OutboundAttachmentException.badRequest("上传身份缺失")
        }
        if (operator.length > MAX_CREATED_BY_CHARS) {
            throw IllegalStateException(
                "登录用户名超过 uploaded_by 列宽（$MAX_CREATED_BY_CHARS 字符），拒绝写入"
            )
        }
        return operator
    }

    /** 手动材料根：`basePath/manual`；只有 UUID 会成为路径段。 */
    private fun manualRoot(): Path =
        Path.of(properties.basePath).toAbsolutePath().normalize().resolve(MANUAL_DIR)

    private data class WrittenIds(val attachmentId: Long, val documentId: Long)

    companion object {
        /** 手动材料单文件业务上限（精确字节）：`0..104857600` 接受，`104857601` 起 413。 */
        const val MAX_MANUAL_MATERIAL_BYTES = 100L * 1024 * 1024

        private const val MANUAL_DIR = "manual"
        private const val TEMP_FILE_PREFIX = ".tmp-"
        private const val STREAM_BUFFER_BYTES = 64 * 1024
    }
}
