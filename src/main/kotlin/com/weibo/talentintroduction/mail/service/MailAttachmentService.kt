package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.document.domain.ExpertDocument
import com.weibo.talentintroduction.document.domain.ExpertDocumentType
import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachment
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

/**
 * 附件两套保存入口（I-2/I-3），按每个附件的 content 分流：
 *
 * - content 非空（旧模式）：保留旧写法——mkdir + Files.write + 真实 file_size/
 *   storage_path；已匹配分支以 mail_record 为 owner，未匹配以 processing 为 owner。
 * - content 为 null（metadataOnly 模式）：不 mkdir、不 Files.write；在 transfer
 *   源唯一身份（account/folder/uid_validity/imap_uid/part_path，V119
 *   uk_mail_attachment_transfer_source）约束内创建 mail_attachment（已匹配时
 *   同时幂等补 ExpertDocument）与 METADATA_ONLY transfer 行；真实字节与文件
 *   之后由传输 worker 按 transfer 行补齐，下载状态绝不改写 document_status。
 *
 * 桥接（04 终结登记）：已匹配（record owner）分支的 transfer 行在 confirm 建好
 * processing 后由 [bridgeInboundProcessing] 补齐 inbound_processing_id；任何
 * metadata 附件缺登记行都会使本信确认失败（I-2：缺一个附件登记失败则本信不能
 * 被确认）。未匹配/无首信在 confirm 拿到 processing.id 后直接以 processing 为
 * owner 登记（transfer 行创建即带 inbound_processing_id）。
 */
@Service
class MailAttachmentService(
    private val properties: MailAttachmentStorageProperties,
    private val mailAttachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val attachmentTransferRepository: MailAttachmentTransferRepository
) {
    // ------------------------------------------------------------------
    // 已匹配分支：record owner
    // ------------------------------------------------------------------

    fun saveInboundAttachments(
        expertContactId: Long,
        mailRecordId: Long,
        attachments: List<ReceivedMailAttachment>
    ): List<ExpertDocument> {
        if (attachments.isEmpty()) {
            return emptyList()
        }

        val now = LocalDateTime.now()
        return attachments.map { received ->
            val content = received.content
            if (content != null) {
                saveLegacyRecordAttachment(expertContactId, mailRecordId, received, content, now)
            } else {
                val source = received.source
                    ?: throw MetadataContentUnavailableException(
                        "metadata attachment without remote source (file=${received.fileName})"
                    )
                val attachment = registerMetadataAttachment(
                    recordOwnerId = mailRecordId,
                    processingOwnerId = null,
                    received = received,
                    source = source,
                    now = now
                )
                ensureExpertDocument(expertContactId, attachment, received.fileName, now)
            }
        }
    }

    // ------------------------------------------------------------------
    // 未匹配 / 无首信 / 来源存疑分支：processing owner
    // ------------------------------------------------------------------

    /**
     * @param expertContactId 已知专家（无首信/来源存疑等）时非空：附件登记后立即
     * 幂等补 ExpertDocument（同 attachmentId）；未匹配（null）不建文档，等后台
     * 绑定时再由 [ensureDocumentsForProcessingAttachments] 补齐。
     */
    fun saveUnmatchedAttachments(
        inboundProcessingId: Long,
        attachments: List<ReceivedMailAttachment>,
        expertContactId: Long? = null
    ): List<MailAttachment> {
        if (attachments.isEmpty()) {
            return emptyList()
        }

        val now = LocalDateTime.now()
        return attachments.map { received ->
            val attachment = if (received.content != null) {
                saveLegacyProcessingAttachment(inboundProcessingId, received, received.content, now)
            } else {
                val source = received.source
                    ?: throw MetadataContentUnavailableException(
                        "metadata attachment without remote source (file=${received.fileName})"
                    )
                registerMetadataAttachment(
                    recordOwnerId = null,
                    processingOwnerId = inboundProcessingId,
                    received = received,
                    source = source,
                    now = now
                )
            }
            if (expertContactId != null) {
                ensureExpertDocument(expertContactId, attachment, received.fileName, now)
            }
            attachment
        }
    }

    /**
     * 后台绑定（UnmatchedInboundMailService.bindToContact）：开关启用时，按已有
     * processing-owner 附件幂等补 ExpertDocument——只建文档，不搬迁 owner、不重写
     * 路径、不改任何 review/document 状态、不触网不落盘（零文件 I/O）。
     */
    fun ensureDocumentsForProcessingAttachments(inboundProcessingId: Long, expertContactId: Long) {
        if (!properties.metadataOnly) {
            return
        }
        val now = LocalDateTime.now()
        mailAttachmentRepository.findAllByInboundProcessingIdOrderByCreatedAtAsc(inboundProcessingId)
            .forEach { attachment ->
                val attachmentId = attachment.id ?: return@forEach
                if (expertDocumentRepository.findFirstByMailAttachmentId(attachmentId) == null) {
                    expertDocumentRepository.save(
                        ExpertDocument(
                            expertContactId = expertContactId,
                            mailAttachmentId = attachmentId,
                            documentType = inferDocumentType(attachment.fileName).name,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }
    }

    // ------------------------------------------------------------------
    // 04 终结登记桥接
    // ------------------------------------------------------------------

    /**
     * confirm 建好 processing 行后调用：把本信（metadata 附件）已登记的 transfer
     * 行桥接到 processing.id。任一 metadata 附件找不到登记行即抛错使本信确认失败
     * （I-2 完整性）；重复调用幂等（已桥接的行不再改写）。
     */
    fun bridgeInboundProcessing(
        processingId: Long,
        attachments: List<ReceivedMailAttachment>
    ) {
        val now = LocalDateTime.now()
        attachments.forEach { received ->
            val source = received.source ?: return@forEach
            val row = attachmentTransferRepository
                .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                    source.accountCode,
                    source.folder,
                    source.uidValidity,
                    source.uid,
                    source.partPath
                )
                ?: throw IllegalStateException(
                    "attachment transfer registration missing for ${source.accountCode}/" +
                        "${source.folder}/${source.uidValidity}/${source.uid}/${source.partPath}; " +
                        "inbound message must not be confirmed without a complete attachment index"
                )
            require(row.purpose == MailAttachmentTransfer.PURPOSE_MATERIAL) {
                "transfer ${row.id} for part ${source.partPath} is ${row.purpose}, expected MATERIAL"
            }
            if (row.inboundProcessingId == null) {
                val rowId = row.id ?: error("Registered transfer row id is required")
                attachmentTransferRepository.save(
                    row.copy(inboundProcessingId = processingId, updatedAt = now)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 登记内部实现
    // ------------------------------------------------------------------

    /**
     * metadata 附件登记：在 transfer 源唯一身份内幂等创建 mail_attachment +
     * METADATA_ONLY transfer 行（不写文件）。同源重复到达直接复用已登记附件，
     * 不产生第二个 attachment/transfer/document；并发同源由
     * uk_mail_attachment_transfer_source 兜底（冲突即本信失败回滚，重试收敛为
     * 恰好一行）。
     */
    private fun registerMetadataAttachment(
        recordOwnerId: Long?,
        processingOwnerId: Long?,
        received: ReceivedMailAttachment,
        source: ImapAttachmentSource,
        now: LocalDateTime
    ): MailAttachment {
        require(source.uidValidity > 0) { "metadata registration requires positive uidValidity" }
        require(source.uid > 0) { "metadata registration requires positive imap uid" }
        require(source.partPath.isNotBlank()) { "metadata registration requires partPath" }
        require(source.accountCode.isNotBlank()) { "metadata registration requires accountCode" }
        require(source.folder.isNotBlank()) { "metadata registration requires folder" }
        require(recordOwnerId != null || processingOwnerId != null) {
            "metadata attachment requires exactly one owner (record or processing)"
        }

        val existing = attachmentTransferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                source.accountCode,
                source.folder,
                source.uidValidity,
                source.uid,
                source.partPath
            )
        if (existing != null) {
            require(existing.purpose == MailAttachmentTransfer.PURPOSE_MATERIAL) {
                "source identity already registered with purpose ${existing.purpose}"
            }
            val existingAttachmentId = existing.attachmentId
                ?: error("MATERIAL transfer ${existing.id} has no attachment")
            return mailAttachmentRepository.findById(existingAttachmentId)
                .orElseThrow { error("registered attachment missing: $existingAttachmentId") }
        }

        val attachment = mailAttachmentRepository.save(
            MailAttachment(
                mailRecordId = recordOwnerId,
                inboundProcessingId = processingOwnerId,
                fileName = received.fileName,
                contentType = received.contentType,
                fileSize = null,
                storagePath = null,
                createdAt = now
            )
        )
        val attachmentId = attachment.id ?: error("Mail attachment id is required")
        attachmentTransferRepository.save(
            MailAttachmentTransfer(
                attachmentId = attachmentId,
                purpose = MailAttachmentTransfer.PURPOSE_MATERIAL,
                accountCode = source.accountCode,
                folder = source.folder,
                uidValidity = source.uidValidity,
                imapUid = source.uid,
                partPath = source.partPath,
                messageId = source.messageId,
                fileName = received.fileName,
                contentType = received.contentType,
                encodedSize = source.encodedSize,
                disposition = source.disposition,
                inboundProcessingId = processingOwnerId,
                state = MailAttachmentTransfer.STATE_METADATA_ONLY,
                createdAt = now,
                updatedAt = now
            )
        )
        return attachment
    }

    /** 幂等补 ExpertDocument（同 attachmentId 至多一个文档；不触碰已存在文档）。 */
    private fun ensureExpertDocument(
        expertContactId: Long,
        attachment: MailAttachment,
        fileName: String,
        now: LocalDateTime
    ): ExpertDocument {
        val attachmentId = attachment.id ?: error("Mail attachment id is required")
        val existing = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
        if (existing != null) {
            return existing
        }
        return expertDocumentRepository.save(
            ExpertDocument(
                expertContactId = expertContactId,
                mailAttachmentId = attachmentId,
                documentType = inferDocumentType(fileName).name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    // ------------------------------------------------------------------
    // 旧（content）路径——保持原行为
    // ------------------------------------------------------------------

    private fun saveLegacyRecordAttachment(
        expertContactId: Long,
        mailRecordId: Long,
        received: ReceivedMailAttachment,
        content: ByteArray,
        now: LocalDateTime
    ): ExpertDocument {
        val directory = Path.of(properties.basePath, expertContactId.toString(), mailRecordId.toString())
        Files.createDirectories(directory)
        val safeFileName = received.fileName.toSafeFileName()
        val storagePath = directory.resolve("${UUID.randomUUID()}-$safeFileName")
        Files.write(storagePath, content)

        val mailAttachment = mailAttachmentRepository.save(
            MailAttachment(
                mailRecordId = mailRecordId,
                inboundProcessingId = null,
                fileName = received.fileName,
                contentType = received.contentType,
                fileSize = content.size.toLong(),
                storagePath = storagePath.toString(),
                createdAt = now
            )
        )
        return expertDocumentRepository.save(
            ExpertDocument(
                expertContactId = expertContactId,
                mailAttachmentId = mailAttachment.id ?: error("Mail attachment id is required"),
                documentType = inferDocumentType(received.fileName).name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun saveLegacyProcessingAttachment(
        inboundProcessingId: Long,
        received: ReceivedMailAttachment,
        content: ByteArray,
        now: LocalDateTime
    ): MailAttachment {
        val directory = Path.of(properties.basePath, "unmatched", inboundProcessingId.toString())
        Files.createDirectories(directory)
        val safeFileName = received.fileName.toSafeFileName()
        val storagePath = directory.resolve("${UUID.randomUUID()}-$safeFileName")
        Files.write(storagePath, content)

        return mailAttachmentRepository.save(
            MailAttachment(
                mailRecordId = null,
                inboundProcessingId = inboundProcessingId,
                fileName = received.fileName,
                contentType = received.contentType,
                fileSize = content.size.toLong(),
                storagePath = storagePath.toString(),
                createdAt = now
            )
        )
    }

    fun inferPrimaryIntentFromAttachments(attachments: List<ReceivedMailAttachment>): InboundIntentCode? {
        if (attachments.isEmpty()) {
            return null
        }
        val types = attachments.map { inferDocumentType(it.fileName) }.toSet()
        return if (types == setOf(ExpertDocumentType.CV) || ExpertDocumentType.CV in types) {
            InboundIntentCode.CV_ATTACHED
        } else {
            InboundIntentCode.DOCS_ATTACHED
        }
    }

    fun inferDocumentType(fileName: String): ExpertDocumentType {
        val normalized = fileName.lowercase(Locale.ROOT)
        return when {
            normalized.contains("cv") || normalized.contains("resume") -> ExpertDocumentType.CV
            normalized.contains("passport") -> ExpertDocumentType.PASSPORT
            normalized.contains("phd") || normalized.contains("doctor") -> ExpertDocumentType.PHD_DEGREE
            normalized.contains("master") -> ExpertDocumentType.MASTER_DEGREE
            normalized.contains("bachelor") -> ExpertDocumentType.BACHELOR_DEGREE
            normalized.contains("employment") || normalized.contains("work") -> ExpertDocumentType.EMPLOYMENT_PROOF
            normalized.contains("patent") -> ExpertDocumentType.PATENT_PROOF
            normalized.contains("award") || normalized.contains("honor") -> ExpertDocumentType.AWARD_PROOF
            normalized.contains("publication") || normalized.contains("paper") -> ExpertDocumentType.PUBLICATION_LIST
            normalized.endsWith(".ppt") || normalized.endsWith(".pptx") || normalized.contains("powerpoint") -> ExpertDocumentType.PPT
            normalized.endsWith(".mp4") || normalized.endsWith(".mov") || normalized.contains("video") || normalized.contains("vcr") -> ExpertDocumentType.VIDEO
            normalized.contains("commitment") || normalized.contains("statement") -> ExpertDocumentType.COMMITMENT
            else -> ExpertDocumentType.OTHER
        }
    }

    private fun String.toSafeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "attachment" }
            .take(180)
}
