package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.document.repository.ExpertDocumentRepository
import com.weibo.talentintroduction.mail.domain.MailAttachmentTransfer
import com.weibo.talentintroduction.mail.repository.MailAttachmentRepository
import com.weibo.talentintroduction.mail.repository.MailAttachmentTransferRepository
import com.weibo.talentintroduction.mail.repository.MailRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.relational.core.conversion.DbActionExecutionException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

/**
 * 附件传输的登记与请求入口（worker 领取/下载在 [AttachmentTransferWorker]）。
 *
 * - 登记（[register]）：按源唯一身份幂等建 METADATA_ONLY 行；校验 purpose 与
 *   attachment_id 组合（MATERIAL 必须 attachmentId，DMARC 必须无 attachment /
 *   无 processing 假来信）。登记不受排队上限影响。
 * - 入队（[enqueueMaterial] / [enqueueTransferByIds]）：显式请求才把
 *   METADATA_ONLY|FAILED|SOURCE_UNAVAILABLE 翻成 QUEUED；重复请求
 *   QUEUED/DOWNLOADING/STORED 不新建、不改写；全局排队上限 5000，超额不排队
 *   （不影响元数据登记）；错误脱敏。
 *
 * 本阶段不开放 HTTP：06 提供控制器并做联系人与附件归属的完整校验；本服务按
 * 契约在事务内再次核验归属后入队。
 */
@Service
class AttachmentTransferService(
    private val transferRepository: MailAttachmentTransferRepository,
    private val attachmentRepository: MailAttachmentRepository,
    private val expertDocumentRepository: ExpertDocumentRepository,
    private val mailRecordRepository: MailRecordRepository,
    /** 生产/IT 注入；单元测试可不传（null 时回退同事务查询）。 */
    private val transactionTemplate: TransactionTemplate? = null
) {
    private val log = LoggerFactory.getLogger(AttachmentTransferService::class.java)

    // ------------------------------------------------------------------
    // 登记
    // ------------------------------------------------------------------

    data class RegisterTransferRequest(
        val purpose: String,
        /** MATERIAL 必填；DMARC 必须为空。 */
        val attachmentId: Long? = null,
        /** 真实收信账号，禁止客户端指定凭据。 */
        val accountCode: String,
        val folder: String,
        val uidValidity: Long,
        val imapUid: Long,
        /** 点分 1-based part 路径（如 2、2.1），ASCII；不能按文件名定位。 */
        val partPath: String,
        /** 可空；仅下载复核，空不补猜。 */
        val messageId: String? = null,
        val fileName: String,
        val contentType: String? = null,
        /** BODYSTRUCTURE 编码大小，未知 null。 */
        val encodedSize: Long? = null,
        val disposition: String? = null,
        /** MATERIAL 由 04 终结登记桥接；DMARC 必须为空。 */
        val inboundProcessingId: Long? = null
    )

    data class RegistrationResult(
        val transfer: MailAttachmentTransfer,
        val created: Boolean
    )

    /**
     * 幂等登记（单 INSERT，天然原子）。不加 @Transactional：并发同源时后到线程的
     * INSERT 冲突会把所在事务标 rollback-only，即便捕获也会在提交时抛
     * UnexpectedRollbackException。无注解时 save 自身事务提交/回滚，冲突捕获后
     * 走新事务回查；若调用方（04 登记桥接）已开事务，repo 调用仍自动加入。
     */
    fun register(request: RegisterTransferRequest): RegistrationResult {
        validateRegistration(request)
        if (request.purpose == MailAttachmentTransfer.PURPOSE_MATERIAL) {
            requireAttachmentExists(request.attachmentId!!)
        }
        val existing = transferRepository
            .findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                request.accountCode,
                request.folder,
                request.uidValidity,
                request.imapUid,
                request.partPath
            )
        if (existing != null) {
            require(existing.purpose == request.purpose) {
                "source identity already registered with purpose ${existing.purpose}"
            }
            require(existing.attachmentId == request.attachmentId) {
                "source identity already registered for a different attachment"
            }
            return RegistrationResult(existing, created = false)
        }
        val now = LocalDateTime.now()
        val transfer = MailAttachmentTransfer(
            attachmentId = request.attachmentId,
            purpose = request.purpose,
            accountCode = request.accountCode,
            folder = request.folder,
            uidValidity = request.uidValidity,
            imapUid = request.imapUid,
            partPath = request.partPath,
            messageId = request.messageId,
            fileName = request.fileName,
            contentType = request.contentType,
            encodedSize = request.encodedSize,
            disposition = request.disposition,
            inboundProcessingId = request.inboundProcessingId,
            state = MailAttachmentTransfer.STATE_METADATA_ONLY,
            requestedBy = null,
            createdAt = now,
            updatedAt = now
        )
        return try {
            RegistrationResult(transferRepository.save(transfer), created = true)
        } catch (e: DataAccessException) {
            conflictFallback(request, e)
        } catch (e: DbActionExecutionException) {
            // Spring Data JDBC 把 INSERT 的约束冲突包成 DbActionExecutionException
            // （非 DataAccessException 子类）。
            conflictFallback(request, e)
        }
    }

    private fun conflictFallback(
        request: RegisterTransferRequest,
        conflict: RuntimeException
    ): RegistrationResult {
        // 并发登记同源：唯一键/死锁/锁等待等瞬时竞争都走同源回查。注意本事务的
        // REPEATABLE READ 快照看不到赢家（未提交→提交）的行，回查必须在新事务里做。
        val raced = findRegisteredSourceFresh(
            request.accountCode,
            request.folder,
            request.uidValidity,
            request.imapUid,
            request.partPath
        )
        if (raced != null) {
            return RegistrationResult(raced, created = false)
        }
        throw conflict
    }

    private fun findRegisteredSourceFresh(
        accountCode: String,
        folder: String,
        uidValidity: Long,
        imapUid: Long,
        partPath: String
    ): MailAttachmentTransfer? {
        val find = {
            transferRepository.findByAccountCodeAndFolderAndUidValidityAndImapUidAndPartPath(
                accountCode, folder, uidValidity, imapUid, partPath
            )
        }
        val tx = transactionTemplate
        return if (tx != null) {
            // 赢家行在本事务可见性之外；轮询新事务最多 ~1s，覆盖提交竞态窗口。
            var last: MailAttachmentTransfer? = null
            repeat(10) {
                last = tx.execute { find() }
                if (last != null) return last
                try {
                    Thread.sleep(50)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            last
        } else {
            find()
        }
    }

    private fun validateRegistration(request: RegisterTransferRequest) {
        require(request.purpose == MailAttachmentTransfer.PURPOSE_MATERIAL ||
            request.purpose == MailAttachmentTransfer.PURPOSE_DMARC) {
            "purpose must be MATERIAL or DMARC"
        }
        when (request.purpose) {
            MailAttachmentTransfer.PURPOSE_MATERIAL -> {
                require(request.attachmentId != null) {
                    "MATERIAL transfer requires an attachmentId"
                }
                require(request.attachmentId!! > 0) { "attachmentId must be positive" }
            }
            MailAttachmentTransfer.PURPOSE_DMARC -> {
                require(request.attachmentId == null) {
                    "DMARC transfer must not reference an expert attachment"
                }
                require(request.inboundProcessingId == null) {
                    "DMARC transfer must not be linked to an inbound processing row"
                }
            }
        }
        require(request.accountCode.isNotBlank()) { "accountCode is required" }
        require(request.folder.isNotBlank()) { "folder is required" }
        require(request.uidValidity > 0) { "uidValidity must be positive" }
        require(request.imapUid > 0) { "imapUid must be positive" }
        // ASCII 点分 1-based part 路径；客户端文件名永不参与定位。
        ImapAttachmentContentFetcher.parsePartPath(request.partPath)
        require(request.fileName.isNotBlank()) { "fileName is required" }
        require(request.encodedSize == null || request.encodedSize!! >= 0) {
            "encodedSize must not be negative"
        }
    }

    private fun requireAttachmentExists(attachmentId: Long) {
        require(attachmentRepository.findById(attachmentId).isPresent) {
            "attachment not found: $attachmentId"
        }
    }

    // ------------------------------------------------------------------
    // 入队（显式请求下载）
    // ------------------------------------------------------------------

    data class EnqueueItemResult(
        val attachmentId: Long,
        val transferId: Long? = null,
        /** 操作后的行状态（已就绪且无行时为 null）。 */
        val transferState: String? = null,
        val alreadyReady: Boolean = false,
        val errorCode: String? = null
    )

    data class EnqueueBatchResult(
        val items: List<EnqueueItemResult>,
        val acceptedCount: Int,
        val alreadyReadyCount: Int,
        val queueFull: Boolean
    )

    data class TransferEnqueueResult(
        val transferId: Long,
        val transferState: String? = null,
        val errorCode: String? = null
    )

    data class TransferEnqueueBatch(
        val items: List<TransferEnqueueResult>,
        val acceptedCount: Int,
        val queueFull: Boolean
    )

    /** 同专家附件批量入队（1..500 去重）；全部归属核验通过后才翻转状态。 */
    @Transactional
    fun enqueueMaterial(
        contactId: Long,
        attachmentIds: List<Long>,
        requestedBy: String
    ): EnqueueBatchResult {
        require(requestedBy.isNotBlank()) { "requestedBy is required" }
        val distinct = attachmentIds.distinct()
        require(distinct.isNotEmpty() && distinct.size <= MAX_ATTACHMENTS_PER_REQUEST) {
            "attachmentIds must contain 1..$MAX_ATTACHMENTS_PER_REQUEST distinct ids"
        }
        val now = LocalDateTime.now()
        var queuedCount = transferRepository.countQueued()
        var accepted = 0
        var alreadyReady = 0
        var queueFull = false
        val items = distinct.map { attachmentId ->
            val attachment = attachmentRepository.findById(attachmentId).orElse(null)
            if (attachment == null) {
                return@map EnqueueItemResult(attachmentId, errorCode = "ATTACHMENT_NOT_FOUND")
            }
            val ownerContactId = resolveOwnerContactId(attachmentId, attachment.mailRecordId)
            if (ownerContactId == null) {
                return@map EnqueueItemResult(attachmentId, errorCode = "ATTACHMENT_UNBOUND")
            }
            if (ownerContactId != contactId) {
                return@map EnqueueItemResult(attachmentId, errorCode = "NOT_OWNED_BY_EXPERT")
            }
            if (isFileReady(attachment.storagePath)) {
                alreadyReady += 1
                val row = transferRepository.findByAttachmentId(attachmentId)
                return@map EnqueueItemResult(
                    attachmentId,
                    transferId = row?.id,
                    transferState = row?.state,
                    alreadyReady = true
                )
            }
            val row = transferRepository.findByAttachmentId(attachmentId)
            if (row == null) {
                // 无本地文件且无已登记可靠来源：不能伪造 UID/来源。
                return@map EnqueueItemResult(
                    attachmentId,
                    errorCode = "NO_RELIABLE_SOURCE"
                )
            }
            if (row.state == MailAttachmentTransfer.STATE_QUEUED ||
                row.state == MailAttachmentTransfer.STATE_DOWNLOADING ||
                row.state == MailAttachmentTransfer.STATE_STORED
            ) {
                return@map EnqueueItemResult(
                    attachmentId,
                    transferId = row.id,
                    transferState = row.state,
                    alreadyReady = false
                )
            }
            if (queuedCount >= MAX_QUEUED_ROWS) {
                queueFull = true
                return@map EnqueueItemResult(
                    attachmentId,
                    transferId = row.id,
                    transferState = row.state,
                    errorCode = "QUEUE_FULL"
                )
            }
            val updated = transferRepository.markRequested(row.id ?: -1, requestedBy, now)
            if (updated == 1) {
                queuedCount += 1
                accepted += 1
                EnqueueItemResult(attachmentId, transferId = row.id, transferState = MailAttachmentTransfer.STATE_QUEUED)
            } else {
                // 竞态：行刚被其他请求翻转
                val current = transferRepository.findById(row.id ?: -1).orElse(row)
                EnqueueItemResult(
                    attachmentId,
                    transferId = current.id,
                    transferState = current.state,
                    alreadyReady = false
                )
            }
        }
        return EnqueueBatchResult(items, accepted, alreadyReady, queueFull)
    }

    /** 直接按 transfer id 入队（DMARC 固定 SYSTEM 请求者；也供通用重试）。 */
    @Transactional
    fun enqueueTransferByIds(
        transferIds: List<Long>,
        requestedBy: String
    ): TransferEnqueueBatch {
        require(requestedBy.isNotBlank()) { "requestedBy is required" }
        val distinct = transferIds.distinct()
        require(distinct.isNotEmpty() && distinct.size <= MAX_ATTACHMENTS_PER_REQUEST) {
            "transferIds must contain 1..$MAX_ATTACHMENTS_PER_REQUEST distinct ids"
        }
        val now = LocalDateTime.now()
        var queuedCount = transferRepository.countQueued()
        var accepted = 0
        var queueFull = false
        val items = distinct.map { transferId ->
            val row = transferRepository.findById(transferId).orElse(null)
            if (row == null) {
                return@map TransferEnqueueResult(transferId, null, "TRANSFER_NOT_FOUND")
            }
            if (row.purpose == MailAttachmentTransfer.PURPOSE_DMARC) {
                require(requestedBy == SYSTEM_REQUESTER) {
                    "DMARC transfers can only be requested by SYSTEM"
                }
            }
            if (row.state == MailAttachmentTransfer.STATE_QUEUED ||
                row.state == MailAttachmentTransfer.STATE_DOWNLOADING ||
                row.state == MailAttachmentTransfer.STATE_STORED
            ) {
                return@map TransferEnqueueResult(transferId, row.state)
            }
            if (queuedCount >= MAX_QUEUED_ROWS) {
                queueFull = true
                return@map TransferEnqueueResult(transferId, row.state, "QUEUE_FULL")
            }
            val updated = transferRepository.markRequested(row.id ?: -1, requestedBy, now)
            if (updated == 1) {
                queuedCount += 1
                accepted += 1
                TransferEnqueueResult(transferId, MailAttachmentTransfer.STATE_QUEUED)
            } else {
                // 竞态：行刚被其他请求翻转
                val current = transferRepository.findById(row.id ?: -1).orElse(row)
                TransferEnqueueResult(transferId, current.state)
            }
        }
        return TransferEnqueueBatch(items, accepted, queueFull)
    }

    private fun resolveOwnerContactId(attachmentId: Long, mailRecordId: Long?): Long? {
        val document = expertDocumentRepository.findFirstByMailAttachmentId(attachmentId)
        if (document != null) return document.expertContactId
        if (mailRecordId != null) {
            return mailRecordRepository.findByIdOrNull(mailRecordId)?.expertContactId
        }
        return null
    }

    private fun isFileReady(storagePath: String?): Boolean {
        if (storagePath.isNullOrBlank()) return false
        return try {
            val path = Path.of(storagePath)
            Files.isRegularFile(path)
        } catch (e: Exception) {
            log.warn("unreadable storage path for readiness check", e)
            false
        }
    }

    companion object {
        const val MAX_ATTACHMENTS_PER_REQUEST = 500
        const val MAX_QUEUED_ROWS = 5000L
        const val SYSTEM_REQUESTER = "SYSTEM"
    }
}
