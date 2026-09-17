package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.campaign.repository.ExpertContactRepository
import com.weibo.talentintroduction.config.MailAttachmentStorageProperties
import com.weibo.talentintroduction.mail.domain.OutboundMailAttachment
import com.weibo.talentintroduction.mail.repository.OutboundMailAttachmentRepository
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID

/**
 * 04：人工回复通用附件的有界上传、归属校验与原文件读取。
 *
 * 存储布局（I-4）：`MailAttachmentStorageProperties.basePath/outbound/<UUID>`；用户原始
 * 文件名永不参与拼路径，也不落库第二份 storage_key。写入顺序固定为「写临时文件 →
 * 流式累计尺寸/SHA → 原子移动到最终 UUID 路径 → 最后写元数据」，因此崩溃遗留文件不会
 * 被当成可用附件（元数据才是权威），写库失败会删掉本次刚落的文件。
 *
 * 归属（I-2）：`contactId` 必须是已存在专家，上传者身份只取会话登录名；草稿下载与
 * 首次发送解析都要求「同专家 + 同上传者」，不匹配一律 404（不区分不存在与越权）。
 *
 * 读取校验（I-4）：候选路径必须 realpath 落在 outbound 根内且是常规文件，尺寸与
 * SHA-256 必须与元数据一致；文件缺失 404，越界/非普通文件/尺寸或 hash 不符 409，
 * 绝不静默忽略后继续发信。
 *
 * 本服务不写 `mail_attachment` / `expert_document`，不调用 SMTP、不建排期、不变更
 * 专家状态，也不占用任何发送 attempt（I-1）。
 */
@Service
class OutboundAttachmentService(
    private val properties: MailAttachmentStorageProperties,
    private val repository: OutboundMailAttachmentRepository,
    private val expertContactRepository: ExpertContactRepository
) {

    /**
     * 上传一个原件（每次一个文件，multipart 单字段）。
     *
     * [authenticatedUsername] 只能是会话身份；[stream] 由调用方负责关闭（controller
     * 用 use 包裹 multipart 的输入流）。超过 [MAX_FILE_BYTES] 立即 413 并删除本次
     * 临时文件，不落元数据、不留半成品。
     */
    fun upload(
        contactId: Long,
        authenticatedUsername: String,
        originalFilename: String?,
        declaredContentType: String?,
        stream: InputStream
    ): OutboundAttachmentUploadResponse {
        val operator = normalizeOperator(authenticatedUsername)
        if (!expertContactRepository.existsById(contactId)) {
            throw OutboundAttachmentException.notFound("专家不存在")
        }
        val fileName = normalizeOutboundFileName(originalFilename)
        val contentType = normalizeOutboundContentType(declaredContentType)

        val root = outboundRoot()
        Files.createDirectories(root)
        val id = UUID.randomUUID().toString()
        val finalPath = root.resolve(id)
        val tempPath = root.resolve("$TEMP_FILE_PREFIX$id")

        val digest = MessageDigest.getInstance("SHA-256")
        var byteLength = 0L
        try {
            Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(STREAM_BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        break
                    }
                    if (read == 0) {
                        continue
                    }
                    byteLength += read
                    if (byteLength > MAX_FILE_BYTES) {
                        throw OutboundAttachmentException.payloadTooLarge(
                            "单个附件不能超过 $MAX_FILE_BYTES 字节"
                        )
                    }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
            // 完整校验通过后才进入最终 UUID 路径；同一目录内保证原子性。
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tempPath)
        }

        val attachment = OutboundMailAttachment(
            id = id,
            expertContactId = contactId,
            createdBy = operator,
            fileName = fileName,
            contentType = contentType,
            byteLength = byteLength,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            createdAt = LocalDateTime.now()
        )
        try {
            repository.insert(attachment)
        } catch (ex: Exception) {
            // 写库失败不得留下「可用附件」：删掉本次刚落地的文件再上抛。
            Files.deleteIfExists(finalPath)
            throw ex
        }
        return OutboundAttachmentUploadResponse(
            id = attachment.id,
            filename = attachment.fileName,
            contentType = attachment.contentType,
            byteLength = attachment.byteLength,
            sha256 = attachment.sha256,
            downloadUrl = draftDownloadUrl(contactId, attachment.id)
        )
    }

    /**
     * 草稿下载（I-2）：同专家 + 同上传用户；不匹配一律 404，不泄露附件是否存在。
     */
    fun resolveDraftDownload(
        contactId: Long,
        attachmentId: String,
        authenticatedUsername: String
    ): OutboundMailFile {
        val operator = normalizeOperator(authenticatedUsername)
        val attachment = loadOwned(contactId, attachmentId)
        if (attachment.createdBy != operator) {
            throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
        }
        return readVerifiedFile(attachment)
    }

    /**
     * 首次发送解析（I-2/I-3）：按用户选择顺序返回不可变文件集合与有序快照。
     *
     * 校验顺序：个数上限（400）→ 重复 id（400）→ 归属（同专家 + 同上传者，404）→
     * 总字节上限（413）→ 逐个原件尺寸/hash（404/409）。[attachmentIds] 为空表示
     * 没有通用附件，返回空集合（05/06 无需特判）。
     */
    fun resolveForSend(
        contactId: Long,
        attachmentIds: List<String>,
        authenticatedUsername: String
    ): OutboundAttachmentFileSet {
        if (attachmentIds.isEmpty()) {
            return OutboundAttachmentFileSet(emptyList(), emptyList())
        }
        val operator = normalizeOperator(authenticatedUsername)
        val attachments = loadOrdered(contactId, attachmentIds)
        attachments.firstOrNull { it.createdBy != operator }?.let {
            throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
        }
        val files = attachments.map { readVerifiedFile(it) }
        return OutboundAttachmentFileSet(files, files.map { it.snapshot })
    }

    /**
     * 元数据读取（06 的已发送比较/展示用）：只读元数据，**不读原件、不要求原件仍在线**；
     * 归属仍要求同专家 + 同上传者，边界与 [resolveForSend] 完全一致。
     */
    fun loadSnapshots(
        contactId: Long,
        attachmentIds: List<String>,
        authenticatedUsername: String
    ): List<OutboundAttachmentSnapshot> {
        if (attachmentIds.isEmpty()) {
            return emptyList()
        }
        val operator = normalizeOperator(authenticatedUsername)
        val attachments = loadOrdered(contactId, attachmentIds)
        attachments.firstOrNull { it.createdBy != operator }?.let {
            throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)
        }
        return attachments.map { snapshotOf(it) }
    }

    /**
     * 已发送原件读取（06 的已发消息下载）：只按专家范围取行——「该 id 是否在这封已发
     * 消息的快照成员里」由调用方先判，因此这里不做上传者校验（跨操作员可下载已发件）。
     */
    fun resolveForMessageDownload(contactId: Long, attachmentId: String): OutboundMailFile =
        readVerifiedFile(loadOwned(contactId, attachmentId))

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private fun loadOwned(contactId: Long, attachmentId: String): OutboundMailAttachment =
        repository.findAllByExpertContactIdAndIdIn(contactId, listOf(attachmentId)).singleOrNull()
            ?: throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE)

    /** 批量取元数据并恢复请求顺序，同时执行数量/总量边界（I-3）。 */
    private fun loadOrdered(contactId: Long, attachmentIds: List<String>): List<OutboundMailAttachment> {
        if (attachmentIds.size > MAX_FILES) {
            throw OutboundAttachmentException.badRequest("最多 $MAX_FILES 个通用附件")
        }
        if (attachmentIds.distinct().size != attachmentIds.size) {
            throw OutboundAttachmentException.badRequest("通用附件 id 不能重复")
        }
        val byId = repository.findAllByExpertContactIdAndIdIn(contactId, attachmentIds).associateBy { it.id }
        val ordered = attachmentIds.map { byId[it] ?: throw OutboundAttachmentException.notFound(NOT_AVAILABLE_MESSAGE) }
        if (ordered.sumOf { it.byteLength } > MAX_TOTAL_BYTES) {
            throw OutboundAttachmentException.payloadTooLarge("通用附件总计不能超过 $MAX_TOTAL_BYTES 字节")
        }
        return ordered
    }

    /** 按元数据重算快照并校验（含文件名/类型/摘要形态），损坏即 409。 */
    private fun snapshotOf(attachment: OutboundMailAttachment): OutboundAttachmentSnapshot {
        val snapshot = OutboundAttachmentSnapshot(
            schemaVersion = OUTBOUND_ATTACHMENT_SCHEMA_VERSION,
            id = attachment.id,
            filename = attachment.fileName,
            contentType = attachment.contentType,
            byteLength = attachment.byteLength,
            sha256 = attachment.sha256
        )
        OutboundAttachmentSnapshotCodec.validateSnapshot(snapshot)
        return snapshot
    }

    /** 读原件：realpath 越界/非普通文件/尺寸或 hash 不符都不放行（I-4）。 */
    private fun readVerifiedFile(attachment: OutboundMailAttachment): OutboundMailFile {
        val snapshot = snapshotOf(attachment)
        val root = outboundRoot()
        val candidate = root.resolve(snapshot.id)
        if (!Files.exists(candidate)) {
            throw OutboundAttachmentException.notFound("附件原件缺失（id=${snapshot.id}）")
        }
        if (!Files.isRegularFile(candidate)) {
            throw OutboundAttachmentException.conflict("附件原件不是常规文件（id=${snapshot.id}）")
        }
        val realFile = candidate.toRealPath()
        if (!realFile.startsWith(root.toRealPath())) {
            throw OutboundAttachmentException.conflict("附件原件路径越界（id=${snapshot.id}）")
        }
        val bytes = Files.readAllBytes(realFile)
        if (bytes.size.toLong() != snapshot.byteLength) {
            throw OutboundAttachmentException.conflict("附件原件尺寸与元数据不一致（id=${snapshot.id}）")
        }
        if (outboundSha256Hex(bytes) != snapshot.sha256) {
            throw OutboundAttachmentException.conflict("附件原件摘要与元数据不一致（id=${snapshot.id}）")
        }
        return OutboundMailFile(snapshot, bytes)
    }

    /** 上传者身份：空身份拒绝写入（不伪造），超列宽报配置错误（绝不截断成另一个用户）。 */
    private fun normalizeOperator(authenticatedUsername: String?): String {
        val operator = authenticatedUsername?.trim().orEmpty()
        if (operator.isEmpty()) {
            throw OutboundAttachmentException.badRequest("上传身份缺失")
        }
        if (operator.length > MAX_CREATED_BY_CHARS) {
            throw IllegalStateException(
                "登录用户名超过 created_by 列宽（$MAX_CREATED_BY_CHARS 字符），拒绝写入"
            )
        }
        return operator
    }

    private fun outboundRoot(): Path =
        Path.of(properties.basePath).toAbsolutePath().normalize().resolve(OUTBOUND_DIR)

    /** 草稿下载地址：context 相对，只含专家 id 与附件 id（不含任何服务器路径）。 */
    private fun draftDownloadUrl(contactId: Long, attachmentId: String): String =
        "/api/mail/conversations/$contactId/outbound-attachments/$attachmentId/download"

    private companion object {
        const val OUTBOUND_DIR = "outbound"
        const val TEMP_FILE_PREFIX = ".tmp-"
        const val STREAM_BUFFER_BYTES = 64 * 1024
        const val NOT_AVAILABLE_MESSAGE = "附件不存在或不属于当前会话"
    }
}
